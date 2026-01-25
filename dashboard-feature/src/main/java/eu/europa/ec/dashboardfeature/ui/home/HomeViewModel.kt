/*
 * Copyright (c) 2025 European Commission
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

import eu.europa.ec.commonfeature.config.IssuanceFlowType
import eu.europa.ec.commonfeature.config.IssuanceUiConfig
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.QrScanFlow
import eu.europa.ec.commonfeature.config.QrScanUiConfig
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.corelogic.di.getOrCreatePresentationScope
import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.dashboardfeature.interactor.HomeInteractor
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorGetCredentialsPartialState
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorGetHeroCredentialPartialState
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorGetUserNameViaMainPidDocumentPartialState
import eu.europa.ec.dashboardfeature.ui.home.model.HeroCredentialUi

import eu.europa.ec.dashboardfeature.ui.component.BottomNavigationItem
import eu.europa.ec.dashboardfeature.ui.documents.list.model.DocumentUi
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.dashboardfeature.ui.home.HomeScreenBottomSheetContent.Bluetooth
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
import eu.europa.ec.uilogic.navigation.QuickIdScreens
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

    // Hero credential for the top of the home screen
    val heroCredential: HeroCredentialUi? = null,
    val isLoadingHeroCredential: Boolean = false,

    // Credentials list for the home screen (deprecated - moved to hero card)
    val isLoadingCredentials: Boolean = false,
    val credentials: List<Pair<DocumentCategory, List<DocumentUi>>> = emptyList(),
    val showEmptyCredentialsMessage: Boolean = false
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object StartProximityFlow : Event()
    data object GetCredentials : Event()
    data object HeroCredentialPressed : Event()
    data object QrScanPressed : Event()

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

    // QuickID / Authbound ID events
    data object GetAuthboundIdPressed : Event()

    sealed class BottomSheet : Event() {
        data class UpdateBottomSheetState(val isOpen: Boolean) : BottomSheet()
        data object Close : BottomSheet()

        sealed class Authenticate : BottomSheet() {
            data object OpenAuthenticateInPerson : Authenticate()
            data object OpenAuthenticateOnLine : Authenticate()
        }

        sealed class SignDocument : BottomSheet() {
            data object OpenFromDevice : Authenticate()
            data object OpenScanQR : Authenticate()
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
    data object Sign : HomeScreenBottomSheetContent()

    data class Bluetooth(val availability: BleAvailability) : HomeScreenBottomSheetContent()
}

@KoinViewModel
class HomeViewModel(
    private val homeInteractor: HomeInteractor,
    private val uiSerializer: UiSerializer,
    private val resourceProvider: ResourceProvider
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State {
        // Premium gradient color definitions - Authbound brand palette
        // Navy spectrum: #0A1A36 (deepest) → #1E3A5F (medium) → #2A4A6F (lighter)
        // Accents: Blue #3B82F6, Teal #2A8A9A, Amber #E0530D, Purple #6D28D9

        // Create quick actions list with premium gradient styling
        val quickActionsList =
            listOf(
                QuickActionConfig(
                    id = "authenticate",
                    title = resourceProvider.getString(R.string.home_screen_authenticate),
                    description = resourceProvider.getString(R.string.home_screen_authentication_card_title),
                    icon = AppIcons.TouchId,
                    gradientStart = Color(0xFF0A1A36),  // Deep navy
                    gradientEnd = Color(0xFF1E3A5F),    // Medium navy
                    accentColor = Color(0xFF3B82F6)     // Blue accent
                ),
                QuickActionConfig(
                    id = "add_credentials",
                    title = resourceProvider.getString(R.string.dashboard_quick_action_add_credential),
                    description = resourceProvider.getString(R.string.dashboard_quick_action_add_credential_description),
                    icon = AppIcons.Id,
                    gradientStart = Color(0xFFB45309),  // Amber dark
                    gradientEnd = Color(0xFFD97706),    // Amber light
                    accentColor = Color(0xFFFBBF24)     // Yellow accent
                ),
                QuickActionConfig(
                    id = "verify",
                    title = resourceProvider.getString(R.string.verification_quick_action_title),
                    description = resourceProvider.getString(R.string.verification_quick_action_description),
                    icon = AppIcons.Verified,
                    gradientStart = Color(0xFF047857),  // Emerald dark
                    gradientEnd = Color(0xFF059669),    // Emerald light
                    accentColor = Color(0xFF34D399)     // Green accent
                ),
                QuickActionConfig(
                    id = "sign",
                    title = resourceProvider.getString(R.string.home_screen_sign),
                    description = resourceProvider.getString(R.string.home_screen_sign_card_title),
                    icon = AppIcons.Sign,
                    gradientStart = Color(0xFF5B21B6),  // Purple dark
                    gradientEnd = Color(0xFF7C3AED),    // Purple light
                    accentColor = Color(0xFFA78BFA)     // Violet accent
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
                getHeroCredential()
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

            is Event.SignDocumentCard.SignDocumentPressed -> showBottomSheet(
                sheetContent = HomeScreenBottomSheetContent.Sign
            )

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

            is Event.BottomSheet.SignDocument.OpenFromDevice -> {
                hideBottomSheet()
                navigateToDocumentSign()
            }

            is Event.BottomSheet.SignDocument.OpenScanQR -> {
                hideBottomSheet()
                navigateToQrSignatureScan()
            }

            is Event.OnPermissionStateChanged -> {
                setState { copy(bleAvailability = event.availability) }
            }

            is Event.OnShowPermissionsRational -> {
                setState { copy(bleAvailability = BleAvailability.UNKNOWN) }
                showBottomSheet(
                    sheetContent =
                        Bluetooth(
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

            is Event.HeroCredentialPressed -> {
                handleHeroCredentialPressed()
            }

            is Event.QrScanPressed -> {
                navigateToQrScan()
            }

            is Event.GetAuthboundIdPressed -> {
                navigateToQuickId()
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
                sheetContent = Bluetooth(BleAvailability.DISABLED)
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
            Effect.Navigation.SwitchScreen(screenRoute = DashboardScreens.DocumentSign.screenRoute)
        }
    }

    private fun navigateToAddDocument() {
        val addDocumentScreenRoute = generateComposableNavigationLink(
            screen = IssuanceScreens.AddDocument,
            arguments = generateComposableArguments(
                mapOf(
                    IssuanceUiConfig.serializedKeyName to uiSerializer.toBase64(
                        model = IssuanceUiConfig(
                            flowType = IssuanceFlowType.ExtraDocument(
                                formatType = null
                            )
                        ),
                        parser = IssuanceUiConfig.Parser
                    )
                )
            )
        )
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = addDocumentScreenRoute
            )
        }
    }

    private fun navigateToDocumentDetails(docId: DocumentId) {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = DashboardScreens.DocumentDetails,
                    arguments = generateComposableArguments(
                        mapOf("documentId" to docId)
                    )
                )
            )

        }
    }

    private fun navigateToDocumentsTab() {
        setEffect {
            Effect.Navigation.SwitchTab(
                "${BottomNavigationItem.Wallet.route}?tab=${eu.europa.ec.dashboardfeature.ui.wallet.WalletTab.Documents.routeValue}"
            )
        }
    }

    private fun startProximityFlow() {
        setState { copy(bleAvailability = BleAvailability.AVAILABLE) }
        // Create Koin scope for presentation
        getOrCreatePresentationScope()
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = ProximityScreens.QR,
                    arguments = generateComposableArguments(
                        mapOf(
                            RequestUriConfig.serializedKeyName to uiSerializer.toBase64(
                                RequestUriConfig(PresentationMode.Ble(DashboardScreens.Dashboard.screenRoute)),
                                RequestUriConfig.Parser
                            )
                        )
                    )
                )
            )
        }
    }

    private fun navigateToQrSignatureScan() {
        val navigationEffect = Effect.Navigation.SwitchScreen(
            screenRoute = generateComposableNavigationLink(
                screen = CommonScreens.QrScan,
                arguments = generateComposableArguments(
                    mapOf(
                        QrScanUiConfig.serializedKeyName to uiSerializer.toBase64(
                            QrScanUiConfig(
                                title = resourceProvider.getString(R.string.signature_qr_scan_title),
                                subTitle = resourceProvider.getString(R.string.signature_qr_scan_subtitle),
                                qrScanFlow = QrScanFlow.Signature
                            ),
                            QrScanUiConfig.Parser
                        )
                    )
                )
            )
        )
        setEffect {
            navigationEffect
        }
    }

    private fun navigateToQrScan() {
        val navigationEffect = Effect.Navigation.SwitchScreen(
            screenRoute = generateComposableNavigationLink(
                screen = CommonScreens.QrScan,
                arguments = generateComposableArguments(
                    mapOf(
                        QrScanUiConfig.serializedKeyName to uiSerializer.toBase64(
                            QrScanUiConfig(
                                title = resourceProvider.getString(R.string.presentation_qr_scan_title),
                                subTitle = resourceProvider.getString(R.string.presentation_qr_scan_subtitle),
                                qrScanFlow = QrScanFlow.Presentation
                            ),
                            QrScanUiConfig.Parser
                        )
                    )
                )
            )
        )
        setEffect {
            navigationEffect
        }
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
                                    qrScanFlow = QrScanFlow.Issuance(
                                        issuanceFlowType = IssuanceFlowType.ExtraDocument(
                                            formatType = null
                                        )
                                    )
                                ),
                                QrScanUiConfig.Parser
                            )
                        )
                    )
                )
            )
        }
    }

//    private fun navigateToQrScan() {
//        val navigationEffect = Effect.Navigation.SwitchScreen(
//            screenRoute = generateComposableNavigationLink(
//                screen = CommonScreens.QrScan,
//                arguments = generateComposableArguments(
//                    mapOf(
//                        QrScanUiConfig.serializedKeyName to uiSerializer.toBase64(
//                            QrScanUiConfig(
//                                title = resourceProvider.getString(R.string.presentation_qr_scan_title),
//                                subTitle = resourceProvider.getString(R.string.presentation_qr_scan_subtitle),
//                                qrScanFlow = QrScanFlow.Presentation
//                            ),
//                            QrScanUiConfig.Parser
//                        )
//                    )
//                )
//            ))
//        }
//    }

    private fun navigateToVerificationTemplateSelection() {

        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = DashboardScreens.DocumentDetails.screenRoute
            )
        }
    }

    private fun navigateToCustomVerification() {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = DashboardScreens.DocumentDetails.screenRoute
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
                                welcomeUserMessage = if (response.userFirstName.isNotBlank()) {
                                    resourceProvider.getString(
                                        R.string.home_screen_welcome_user_message,
                                        response.userFirstName
                                    )
                                } else resourceProvider.getString(R.string.home_screen_welcome)
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

    private fun getHeroCredential() {
        setState { copy(isLoadingHeroCredential = true) }
        viewModelScope.launch {
            homeInteractor.getHeroCredential().collect { response ->
                when (response) {
                    is HomeInteractorGetHeroCredentialPartialState.Failure -> {
                        setState {
                            copy(
                                isLoadingHeroCredential = false,
                                heroCredential = null
                            )
                        }
                    }

                    is HomeInteractorGetHeroCredentialPartialState.Success -> {
                        setState {
                            copy(
                                isLoadingHeroCredential = false,
                                heroCredential = response.heroCredential
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleHeroCredentialPressed() {
        val heroCredential = viewState.value.heroCredential ?: return

        // Navigate to proximity QR screen to share the credential
        getOrCreatePresentationScope()
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = ProximityScreens.QR,
                    arguments = generateComposableArguments(
                        mapOf(
                            RequestUriConfig.serializedKeyName to uiSerializer.toBase64(
                                RequestUriConfig(PresentationMode.Ble(DashboardScreens.Dashboard.screenRoute)),
                                RequestUriConfig.Parser
                            )
                        )
                    )
                )
            )
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

            "authbound_id" -> {
                navigateToQuickId()
            }
        }
    }

    private fun navigateToQuickId() {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = QuickIdScreens.Intro.screenRoute
            )
        }
    }
}
