/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.dashboardfeature.ui.home

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.QrScanFlow
import eu.europa.ec.commonfeature.config.QrScanUiConfig
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.corelogic.di.getOrCreatePresentationScope
import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.dashboardfeature.interactor.HomeInteractor
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorGetCredentialsPartialState
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorGetUserNameViaMainPidDocumentPartialState
import eu.europa.ec.dashboardfeature.model.DocumentUi
import eu.europa.ec.dashboardfeature.ui.BottomNavigationItem
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.wrap.ActionCardConfig
import eu.europa.ec.uilogic.component.wrap.QuickActionConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.ProximityScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

enum class BleAvailability {
    AVAILABLE,
    NO_PERMISSION,
    DISABLED,
    UNKNOWN
}

data class State(
    val isLoading: Boolean = false,
    val isBottomSheetOpen: Boolean = false,
    val sheetContent: HomeScreenBottomSheetContent = HomeScreenBottomSheetContent.Authenticate,
    val welcomeUserMessage: String,
    val authenticateCardConfig: ActionCardConfig,
    val signCardConfig: ActionCardConfig,

    // New quick actions list for the grid layout
    val quickActions: List<QuickActionConfig> = emptyList(),
    val bleAvailability: BleAvailability = BleAvailability.UNKNOWN,
    val isBleCentralClientModeEnabled: Boolean = false,
    
    // New credentials list for the home screen
    val isLoadingCredentials: Boolean = false,
    val credentials: List<Pair<DocumentCategory, List<DocumentUi>>> = emptyList(),
    val showEmptyCredentialsMessage: Boolean = false
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object StartProximityFlow : Event()
    data object GetCredentials : Event()

    sealed class AuthenticateCard : Event() {
        data object AuthenticatePressed : Event()
        data object LearnMorePressed : Event()
    }

    sealed class SignDocumentCard : Event() {
        data object SignDocumentPressed : Event()
        data object LearnMorePressed : Event()
    }

    // New event for handling quick action clicks
    data class QuickActionPressed(val actionId: String) : Event()
    
    // New event for handling credential item clicks
    data class CredentialPressed(val docId: DocumentId) : Event()
    data object ViewAllCredentialsPressed : Event()
    data object AddCredentialPressed : Event()
    
    // Verification events
    data object VerificationPressed : Event()
    
    sealed class BottomSheet : Event() {
        data class UpdateBottomSheetState(val isOpen: Boolean) : BottomSheet()
        data object Close : BottomSheet()

        sealed class Authenticate : BottomSheet() {
            data object OpenAuthenticateInPerson : Authenticate()
            data object OpenAuthenticateOnLine : Authenticate()
        }

        sealed class Bluetooth : BottomSheet() {
            data class PrimaryButtonPressed(val availability: BleAvailability) : Bluetooth()
            data object SecondaryButtonPressed : Bluetooth()
        }
        
        sealed class AddDocument : BottomSheet() {
            data object FromList : AddDocument()
            data object ScanQr : AddDocument()
        }
        
        sealed class Verification : BottomSheet() {
            data object UseTemplate : Verification()
            data object CreateCustom : Verification()
        }
    }

    data object OnShowPermissionsRational : Event()
    data class OnPermissionStateChanged(val availability: BleAvailability) : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Dashboard.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()

        data class SwitchTab(
            val tabRoute: String

        ) : Navigation()

        data object OnAppSettings : Navigation()
        data object OnSystemSettings : Navigation()
    }

    data object ShowBottomSheet : Effect()
    data class CloseBottomSheet(val hasNextBottomSheet: Boolean) : Effect()
}

sealed class HomeScreenBottomSheetContent {
    data object Authenticate : HomeScreenBottomSheetContent()
    data object LearnMoreAboutAuthenticate : HomeScreenBottomSheetContent()
    data object LearnMoreAboutSignDocument : HomeScreenBottomSheetContent()
    data object AddDocument : HomeScreenBottomSheetContent()
    data object Verification : HomeScreenBottomSheetContent()

    data class Bluetooth(val availability: BleAvailability) : HomeScreenBottomSheetContent()
}

@KoinViewModel
class HomeViewModel(
    private val homeInteractor: HomeInteractor,
    private val uiSerializer: UiSerializer,
    private val resourceProvider: ResourceProvider
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State {
        // Define colors for quick actions
        val authenticateColor = Color(0xFF1E40AF) // Deep blue
        val signColor = Color(0xFF7C3AED) // Purple
        val viewCredentialsColor = Color(0xFF16A34A) // Green
        val settingsColor = Color(0xFFD97706) // Amber
        val verificationColor = Color(0xFF16A34A) // Red

        // Create quick actions list
        val quickActionsList =
            listOf(
                QuickActionConfig(
                    id = "authenticate",
                    title =
                        resourceProvider.getString(
                            R.string.home_screen_authenticate
                        ),
                    description =
                        resourceProvider.getString(
                            R.string.home_screen_authentication_card_title
                        ),
                    icon = AppIcons.IdCards,
                    backgroundColor = authenticateColor,
                    borderColor = authenticateColor.copy(alpha = 0.7f)
                ),
                QuickActionConfig(
                    id = "add_credentials",
                    title =
                        resourceProvider.getString(
                            R.string.dashboard_quick_action_add_credential
                        ),
                    description =
                        resourceProvider.getString(
                            R.string
                                .dashboard_quick_action_add_credential_description
                        ),
                    icon = AppIcons.Id,
                    backgroundColor = settingsColor,
                    borderColor = settingsColor.copy(alpha = 0.7f)
                ),
                QuickActionConfig(
                    id = "verify",
                    title = resourceProvider.getString(R.string.verification_quick_action_title),
                    description =
                        resourceProvider.getString(
                            R.string.verification_quick_action_description
                        ),
                    icon = AppIcons.Certified,
                    backgroundColor = verificationColor,
                    borderColor = verificationColor.copy(alpha = 0.7f)
                ),
                QuickActionConfig(
                    id = "sign",
                    title = resourceProvider.getString(R.string.home_screen_sign),
                    description =
                        resourceProvider.getString(
                            R.string.home_screen_sign_card_title
                        ),
                    icon = AppIcons.Contract,
                    backgroundColor = signColor,
                    borderColor = signColor.copy(alpha = 0.7f)
                ),
            )

        return State(
            welcomeUserMessage = resourceProvider.getString(R.string.home_screen_welcome),
            authenticateCardConfig =
                ActionCardConfig(
                    title =
                        resourceProvider.getString(
                            R.string.home_screen_authentication_card_title
                        ),
                    icon = AppIcons.IdCards,
                    primaryButtonText =
                        resourceProvider.getString(
                            R.string.home_screen_authenticate
                        ),
                    secondaryButtonText =
                        resourceProvider.getString(R.string.home_screen_learn_more)
                ),
            signCardConfig =
                ActionCardConfig(
                    title =
                        resourceProvider.getString(
                            R.string.home_screen_sign_card_title
                        ),
                    icon = AppIcons.Contract,
                    primaryButtonText =
                        resourceProvider.getString(
                            R.string.home_screen_sign
                        ),
                    secondaryButtonText =
                        resourceProvider.getString(R.string.home_screen_learn_more)
                ),
            quickActions = quickActionsList,
            isBleCentralClientModeEnabled = homeInteractor.isBleCentralClientModeEnabled(),
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> {
                getUserNameViaMainPidDocument()
                getCredentials()
            }

            is Event.GetCredentials -> {
                getCredentials()
            }

            is Event.AuthenticateCard.AuthenticatePressed ->
                showBottomSheet(sheetContent = HomeScreenBottomSheetContent.Authenticate)

            is Event.AuthenticateCard.LearnMorePressed ->
                showBottomSheet(
                    sheetContent = HomeScreenBottomSheetContent.LearnMoreAboutAuthenticate
                )

            is Event.SignDocumentCard.SignDocumentPressed -> {
                navigateToDocumentSign()
            }

            is Event.SignDocumentCard.LearnMorePressed ->
                showBottomSheet(
                    sheetContent = HomeScreenBottomSheetContent.LearnMoreAboutSignDocument
                )

            is Event.BottomSheet.UpdateBottomSheetState -> {
                setState { copy(isBottomSheetOpen = event.isOpen) }
            }

            is Event.BottomSheet.Close -> {
                hideBottomSheet()
            }

            is Event.BottomSheet.Authenticate.OpenAuthenticateInPerson -> {
                checkIfBluetoothIsEnabled()
            }

            is Event.BottomSheet.Authenticate.OpenAuthenticateOnLine -> {
                hideBottomSheet()
                navigateToQrScan()
            }
            
            is Event.BottomSheet.AddDocument.FromList -> {
                hideBottomSheet()
                navigateToAddDocument()
            }
            
            is Event.BottomSheet.AddDocument.ScanQr -> {
                hideBottomSheet()
                navigateToQrScanForDocument()
            }

            is Event.BottomSheet.Verification.UseTemplate -> {
                hideBottomSheet()
                navigateToVerificationTemplateSelection()
            }
            
            is Event.BottomSheet.Verification.CreateCustom -> {
                hideBottomSheet()
                navigateToCustomVerification()
            }

            is Event.VerificationPressed -> {
                showBottomSheet(HomeScreenBottomSheetContent.Verification)
            }

            is Event.OnPermissionStateChanged -> {
                setState { copy(bleAvailability = event.availability) }
            }

            is Event.OnShowPermissionsRational -> {
                setState { copy(bleAvailability = BleAvailability.UNKNOWN) }
                showBottomSheet(
                    sheetContent =
                        HomeScreenBottomSheetContent.Bluetooth(
                            BleAvailability.NO_PERMISSION
                        )
                )
            }

            is Event.StartProximityFlow -> {
                hideBottomSheet()
                startProximityFlow()
            }

            is Event.BottomSheet.Bluetooth.PrimaryButtonPressed -> {
                hideBottomSheet()
                onBleUserAction(event.availability)
            }

            is Event.BottomSheet.Bluetooth.SecondaryButtonPressed -> {
                hideBottomSheet()
            }

            is Event.QuickActionPressed -> {
                handleQuickAction(event.actionId)
            }

            is Event.CredentialPressed -> {
                navigateToDocumentDetails(event.docId)
            }

            is Event.ViewAllCredentialsPressed -> {
                navigateToDocumentsTab()
            }

            is Event.AddCredentialPressed -> {
                showBottomSheet(HomeScreenBottomSheetContent.AddDocument)
            }
        }
    }

    private fun checkIfBluetoothIsEnabled() {
        if (homeInteractor.isBleAvailable()) {
            setState { copy(bleAvailability = BleAvailability.NO_PERMISSION) }
        } else {
            setState { copy(bleAvailability = BleAvailability.DISABLED) }
            hideAndShowNextBottomSheet()
            showBottomSheet(
                sheetContent = HomeScreenBottomSheetContent.Bluetooth(BleAvailability.DISABLED)
            )
        }
    }

    private fun onBleUserAction(availability: BleAvailability) {
        when (availability) {
            BleAvailability.NO_PERMISSION -> {
                setEffect { Effect.Navigation.OnAppSettings }
            }

            BleAvailability.DISABLED -> {
                setEffect { Effect.Navigation.OnSystemSettings }
            }

            else -> {
                // no implementation
            }
        }
    }

    private fun showBottomSheet(sheetContent: HomeScreenBottomSheetContent) {
        setState { copy(sheetContent = sheetContent) }
        setEffect { Effect.ShowBottomSheet }
    }

    private fun hideBottomSheet() {
        setEffect { Effect.CloseBottomSheet(false) }
    }

    private fun hideAndShowNextBottomSheet() {
        setEffect { Effect.CloseBottomSheet(true) }
    }

    private fun navigateToDocumentSign() {
        setEffect {
            Effect.Navigation.SwitchScreen(screenRoute = DashboardScreens.SignDocument.screenRoute)
        }
    }

    private fun navigateToDocumentDetails(docId: DocumentId) {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = IssuanceScreens.DocumentDetails,
                    arguments = generateComposableArguments(
                        mapOf(
                            "detailsType" to IssuanceFlowUiConfig.EXTRA_DOCUMENT,
                            "documentId" to docId
                        )
                    )
                )
            )
        }
    }

    private fun startProximityFlow() {
        setState { copy(bleAvailability = BleAvailability.AVAILABLE) }
        // Create Koin scope for presentation
        getOrCreatePresentationScope()
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute =
                    generateComposableNavigationLink(
                        screen = ProximityScreens.QR,
                        arguments =
                            generateComposableArguments(
                                mapOf(
                                    RequestUriConfig.serializedKeyName to
                                            uiSerializer.toBase64(
                                                RequestUriConfig(
                                                    PresentationMode
                                                        .Ble(
                                                            DashboardScreens
                                                                .Dashboard
                                                                .screenRoute
                                                        )
                                                ),
                                                RequestUriConfig.Parser
                                            )
                                )
                            )
                    )
            )
        }
    }

    private fun navigateToQrScan() {
        val navigationEffect =
            Effect.Navigation.SwitchScreen(
                screenRoute =
                    generateComposableNavigationLink(
                        screen = CommonScreens.QrScan,
                        arguments =
                            generateComposableArguments(
                                mapOf(
                                    QrScanUiConfig.serializedKeyName to
                                            uiSerializer.toBase64(
                                                QrScanUiConfig(
                                                    title =
                                                        resourceProvider
                                                            .getString(
                                                                R.string
                                                                    .presentation_qr_scan_title
                                                            ),
                                                    subTitle =
                                                        resourceProvider
                                                            .getString(
                                                                R.string
                                                                    .presentation_qr_scan_subtitle
                                                            ),
                                                    qrScanFlow =
                                                        QrScanFlow
                                                            .Presentation
                                                ),
                                                QrScanUiConfig
                                                    .Parser
                                            )
                                )
                            )
                    )
            )

        setEffect { navigationEffect }
    }
    
    private fun navigateToQrScanForDocument() {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = CommonScreens.QrScan,
                    arguments = generateComposableArguments(
                        mapOf(
                            QrScanUiConfig.serializedKeyName to uiSerializer.toBase64(
                                QrScanUiConfig(
                                    title = resourceProvider.getString(R.string.issuance_qr_scan_title),
                                    subTitle = resourceProvider.getString(R.string.issuance_qr_scan_subtitle),
                                    qrScanFlow = QrScanFlow.Issuance(IssuanceFlowUiConfig.EXTRA_DOCUMENT)
                                ),
                                QrScanUiConfig.Parser
                            )
                        )
                    )
                ),
                inclusive = false
            )
        }
    }

    private fun navigateToDocumentsTab() {
        // Navigate to Documents tab
        setEffect {
            Effect.Navigation.SwitchTab(
                tabRoute = BottomNavigationItem.Documents.route
            )
        }
    }

    private fun navigateToAddDocument() {
        // Navigate to add credential flow
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = IssuanceScreens.AddDocument,
                    arguments = generateComposableArguments(
                        mapOf("flowType" to IssuanceFlowUiConfig.EXTRA_DOCUMENT)
                    )
                )
            )
        }
    }

    private fun navigateToVerificationTemplateSelection() {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = DashboardScreens.VerificationTemplateSelection.screenRoute
            )
        }
    }
    
    private fun navigateToCustomVerification() {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = DashboardScreens.VerificationCustomCreation.screenRoute
            )
        }
    }

    private fun getUserNameViaMainPidDocument() {
        setState { copy(isLoading = true) }
        viewModelScope.launch {
            homeInteractor.getUserNameViaMainPidDocument().collect { response ->
                when (response) {
                    is HomeInteractorGetUserNameViaMainPidDocumentPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                            )
                        }
                    }

                    is HomeInteractorGetUserNameViaMainPidDocumentPartialState.Success -> {
                        setState {
                            copy(
                                isLoading = false,
                                welcomeUserMessage =
                                    if (response.userFirstName.isNotBlank()) {
                                        resourceProvider.getString(
                                            R.string.home_screen_welcome_user_message,
                                            response.userFirstName
                                        )
                                    } else
                                        resourceProvider.getString(
                                            R.string.home_screen_welcome
                                        )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun getCredentials() {
        setState { copy(isLoadingCredentials = true) }
        viewModelScope.launch {
            homeInteractor.getCredentials().collect { response ->
                when (response) {
                    is HomeInteractorGetCredentialsPartialState.Failure -> {
                        setState {
                            copy(
                                isLoadingCredentials = false,
                                showEmptyCredentialsMessage = true
                            )
                        }
                    }

                    is HomeInteractorGetCredentialsPartialState.Success -> {
                        setState {
                            copy(
                                isLoadingCredentials = false,
                                credentials = response.credentials,
                                showEmptyCredentialsMessage = response.credentials.isEmpty() 
                                    || response.credentials.all { it.second.isEmpty() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleQuickAction(actionId: String) {
        when (actionId) {
            "authenticate" -> {
                showBottomSheet(sheetContent = HomeScreenBottomSheetContent.Authenticate)
            }

            "sign" -> {
                navigateToDocumentSign()
            }

            "verify" -> {
                showBottomSheet(sheetContent = HomeScreenBottomSheetContent.Verification)
            }

            "add_credentials" -> {
                showBottomSheet(sheetContent = HomeScreenBottomSheetContent.AddDocument)
            }
        }
    }
}
