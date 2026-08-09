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

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope

import eu.europa.ec.commonfeature.config.IssuanceFlowType
import eu.europa.ec.commonfeature.config.IssuanceUiConfig
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.QrScanFlow
import eu.europa.ec.commonfeature.config.QrScanUiConfig
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.dashboardfeature.interactor.AuthboundPidEntryInteractor
import eu.europa.ec.dashboardfeature.interactor.AuthboundPidEntryState
import eu.europa.ec.dashboardfeature.interactor.HomeInteractor
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorGetCredentialsPartialState
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorGetHeroCredentialPartialState
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorGetUserNameViaMainPidDocumentPartialState
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorPresentIdPartialState
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
import eu.europa.ec.uilogic.navigation.AuthboundPidScreens
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.ProximityScreens

import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

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

    // Hero credentials for the top of the home screen
    val heroCredentials: List<HeroCredentialUi> = emptyList(),
    val isLoadingHeroCredential: Boolean = false,
    val selectedHeroCredentialDocumentId: DocumentId? = null,
    val presentIdQrCode: String = "",
    val presentIdPresentationScopeId: String = "",
    val presentIdDocumentId: DocumentId? = null,
    val isPresentIdHandoffInProgress: Boolean = false,

    // Credentials list for the home screen (deprecated - moved to hero card)
    val isLoadingCredentials: Boolean = false,
    val credentials: List<Pair<DocumentCategory, List<DocumentUi>>> = emptyList(),
    val showEmptyCredentialsMessage: Boolean = false,
    val shouldShowAuthboundPidEntry: Boolean = false,
    val shouldShowAuthboundPidHomePrompt: Boolean = false,
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object StartProximityFlow : Event()
    data object GetCredentials : Event()
    data class HeroCredentialPressed(val documentId: DocumentId) : Event()
    data class PresentIdPressed(val documentId: DocumentId) : Event()
    data class PresentIdNfcEngagement(
        val componentActivity: ComponentActivity,
        val enable: Boolean
    ) : Event()
    data object PresentIdLifecycleStopped : Event()
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

    // Authbound PID events
    data object GetAuthboundIdPressed : Event()
    data object AuthboundPidPromoNotNowPressed : Event()

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
            data object AuthboundPid : AddDocument()
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
    data object PresentId : HomeScreenBottomSheetContent()

    data class Bluetooth(val availability: BleAvailability) : HomeScreenBottomSheetContent()
}

@KoinViewModel
class HomeViewModel(
    private val homeInteractor: HomeInteractor,
    private val authboundPidEntryInteractor: AuthboundPidEntryInteractor,
    private val uiSerializer: UiSerializer,
    private val resourceProvider: ResourceProvider
) : MviViewModel<Event, State, Effect>() {

    private var presentIdJob: Job? = null

    override fun setInitialState(): State {
        // Quick actions share the navy card base so the home screen reads as one document
        // system; each action keeps its identity through the accent hue alone (icon, border,
        // motif) instead of a saturated full-color fill that competes with the hero card.
        val quickActionsList =
            listOf(
                QuickActionConfig(
                    id = "authenticate",
                    title = resourceProvider.getString(R.string.home_screen_authenticate),
                    description = resourceProvider.getString(R.string.home_screen_authenticate_quick_action_description),
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
                    gradientStart = Color(0xFF0A1A36),  // Deep navy
                    gradientEnd = Color(0xFF1E3A5F),    // Medium navy
                    accentColor = Color(0xFFFBBF24)     // Amber accent
                ),
                QuickActionConfig(
                    id = "sign",
                    title = resourceProvider.getString(R.string.home_screen_sign),
                    description = resourceProvider.getString(R.string.home_screen_sign_card_title),
                    icon = AppIcons.Sign,
                    gradientStart = Color(0xFF0A1A36),  // Deep navy
                    gradientEnd = Color(0xFF1E3A5F),    // Medium navy
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
                getAuthboundPidEntryState()
            }

            is Event.GetCredentials -> {
                getHeroCredential()
                getCredentials()
                getAuthboundPidEntryState()
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
                val shouldCancelPresentIdPresentation: Boolean = event.isOpen.not()
                        && viewState.value.sheetContent is HomeScreenBottomSheetContent.PresentId
                        && viewState.value.isPresentIdHandoffInProgress.not()
                setState { copy(isBottomSheetOpen = event.isOpen) }
                if (shouldCancelPresentIdPresentation) {
                    cancelPresentIdShare()
                }
            }

            is Event.BottomSheet.Close -> {
                if (viewState.value.sheetContent is HomeScreenBottomSheetContent.PresentId
                    && viewState.value.isPresentIdHandoffInProgress.not()
                ) {
                    cancelPresentIdShare()
                }
                hideBottomSheet()
            }

            is Event.BottomSheet.Authenticate.OpenAuthenticateInPerson -> {
                setState { copy(selectedHeroCredentialDocumentId = null) }
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

            is Event.BottomSheet.AddDocument.AuthboundPid -> {
                hideBottomSheet()
                navigateToAuthboundPidIfEligible()
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

            is Event.PresentIdNfcEngagement -> {
                handlePresentIdNfcEngagement(event)
            }

            is Event.PresentIdLifecycleStopped -> {
                stopPresentIdShareFromLifecycle()
            }

            is Event.StartProximityFlow -> {
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
                handleHeroCredentialPressed(event.documentId)
            }

            is Event.PresentIdPressed -> {
                handlePresentIdPressed(event.documentId)
            }

            is Event.QrScanPressed -> {
                navigateToQrScan()
            }

            is Event.GetAuthboundIdPressed -> {
                navigateToAuthboundPidIfEligible()
            }

            is Event.AuthboundPidPromoNotNowPressed -> {
                snoozeAuthboundPidHomePrompt()
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
        setState {
            copy(
                sheetContent = sheetContent,
                isPresentIdHandoffInProgress = false
            )
        }
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
        val selectedDocumentId: DocumentId? = viewState.value.selectedHeroCredentialDocumentId
        val requestUriConfig = RequestUriConfig(
            mode = PresentationMode.Ble(DashboardScreens.Dashboard.screenRoute),
            presentingDocumentId = selectedDocumentId
        )
        homeInteractor.setPresentIdConfig(requestUriConfig)
        presentIdJob?.cancel()
        setState {
            copy(
                bleAvailability = BleAvailability.AVAILABLE,
                selectedHeroCredentialDocumentId = null,
                presentIdQrCode = "",
                presentIdPresentationScopeId = requestUriConfig.presentationScopeId,
                presentIdDocumentId = selectedDocumentId,
                isPresentIdHandoffInProgress = false,
                sheetContent = HomeScreenBottomSheetContent.PresentId
            )
        }
        setEffect { Effect.ShowBottomSheet }
        presentIdJob = viewModelScope.launch {
            homeInteractor.startPresentIdEngagement().collect { response ->
                when (response) {
                    is HomeInteractorPresentIdPartialState.QrReady -> {
                        setState { copy(presentIdQrCode = response.qrCode) }
                    }

                    is HomeInteractorPresentIdPartialState.Connected -> {
                        navigateFromPresentIdSheetToRequest()
                    }

                    is HomeInteractorPresentIdPartialState.Disconnected -> {
                        cancelPresentIdShare()
                        hideBottomSheet()
                    }

                    is HomeInteractorPresentIdPartialState.Error -> {
                        cancelPresentIdShare()
                        hideBottomSheet()
                    }
                }
            }
        }
    }

    private fun navigateFromPresentIdSheetToRequest() {
        val presentationScopeId: String = viewState.value.presentIdPresentationScopeId
        val presentingDocumentId: DocumentId? = viewState.value.presentIdDocumentId
        val arguments: Map<String, String> = buildMap {
            put("scopeId", presentationScopeId)
            presentingDocumentId
                ?.takeIf { it.isNotBlank() }
                ?.let { put("presentingDocumentId", it) }
        }
        unsubscribePresentIdShare()
        homeInteractor.releasePresentIdPresentationController()
        setState {
            copy(
                isBottomSheetOpen = false,
                isPresentIdHandoffInProgress = true,
                presentIdQrCode = ""
            )
        }
        setEffect { Effect.CloseBottomSheet(false) }
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = ProximityScreens.Request,
                    arguments = generateComposableArguments(arguments)
                )
            )
        }
    }

    private fun handlePresentIdNfcEngagement(event: Event.PresentIdNfcEngagement) {
        val canToggleNfc: Boolean =
            viewState.value.sheetContent is HomeScreenBottomSheetContent.PresentId
                    && viewState.value.presentIdPresentationScopeId.isNotBlank()
        if (canToggleNfc) {
            homeInteractor.togglePresentIdNfcEngagement(
                componentActivity = event.componentActivity,
                toggle = event.enable
            )
        }
    }

    private fun unsubscribePresentIdShare() {
        presentIdJob?.cancel()
        presentIdJob = null
    }

    private fun cancelPresentIdShare() {
        val hasActivePresentation: Boolean = viewState.value.presentIdPresentationScopeId.isNotBlank()
        unsubscribePresentIdShare()
        if (hasActivePresentation) {
            homeInteractor.cancelPresentIdPresentation()
        }
        setState {
            copy(
                presentIdQrCode = "",
                presentIdPresentationScopeId = "",
                presentIdDocumentId = null,
                selectedHeroCredentialDocumentId = null,
                isPresentIdHandoffInProgress = false
            )
        }
    }

    private fun stopPresentIdShareFromLifecycle() {
        val shouldStopPresentIdPresentation: Boolean =
            viewState.value.sheetContent is HomeScreenBottomSheetContent.PresentId
                    && viewState.value.isPresentIdHandoffInProgress.not()
        if (shouldStopPresentIdPresentation.not()) return
        cancelPresentIdShare()
        setState { copy(isBottomSheetOpen = false) }
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
                                heroCredentials = emptyList()
                            )
                        }
                    }

                    is HomeInteractorGetHeroCredentialPartialState.Success -> {
                        setState {
                            copy(
                                isLoadingHeroCredential = false,
                                heroCredentials = response.heroCredentials
                            )
                        }
                    }
                }
            }
        }
    }

    private fun getAuthboundPidEntryState() {
        viewModelScope.launch {
            val entryState = authboundPidEntryInteractor.getEntryState()
            setState {
                copy(
                    shouldShowAuthboundPidEntry = entryState.shouldShowEntry,
                    shouldShowAuthboundPidHomePrompt = entryState.shouldShowHomePrompt
                )
            }
        }
    }

    private fun snoozeAuthboundPidHomePrompt() {
        viewModelScope.launch {
            authboundPidEntryInteractor.snoozeHomePrompt()
            setState { copy(shouldShowAuthboundPidHomePrompt = false) }
        }
    }

    private fun handleHeroCredentialPressed(documentId: DocumentId) {
        val isKnownHeroCredential = viewState.value.heroCredentials.any { heroCredential ->
            heroCredential.documentId == documentId
        }
        if (!isKnownHeroCredential) return

        navigateToDocumentDetails(documentId)
    }

    private fun handlePresentIdPressed(documentId: DocumentId) {
        val isKnownHeroCredential = viewState.value.heroCredentials.any { heroCredential ->
            heroCredential.documentId == documentId
        }
        if (!isKnownHeroCredential) return

        setState { copy(selectedHeroCredentialDocumentId = documentId) }
        checkIfBluetoothIsEnabled()
    }

    private fun handleQuickAction(actionId: String) {
        when (actionId) {
            "authenticate" -> {
                showBottomSheet(sheetContent = HomeScreenBottomSheetContent.Authenticate)
            }

            "sign" -> {
                navigateToDocumentSign()
            }

            "add_credentials" -> {
                showBottomSheet(sheetContent = HomeScreenBottomSheetContent.AddDocument)
            }

            "authboundpid" -> {
                navigateToAuthboundPidIfEligible()
            }
        }
    }

    private fun navigateToAuthboundPidIfEligible() {
        viewModelScope.launch {
            val entryState: AuthboundPidEntryState = authboundPidEntryInteractor.getEntryState()
            setState {
                copy(
                    shouldShowAuthboundPidEntry = entryState.shouldShowEntry,
                    shouldShowAuthboundPidHomePrompt = entryState.shouldShowHomePrompt
                )
            }
            if (entryState.shouldShowEntry) {
                navigateToAuthboundPid()
            }
        }
    }

    private fun navigateToAuthboundPid() {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = AuthboundPidScreens.Intro.screenRoute
            )
        }
    }
}
