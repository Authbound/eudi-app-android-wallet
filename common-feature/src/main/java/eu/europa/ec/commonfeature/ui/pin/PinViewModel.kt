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

package eu.europa.ec.commonfeature.ui.pin

import android.content.Context
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAuthenticate
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationResult
import eu.europa.ec.authenticationlogic.model.BiometricCrypto
import eu.europa.ec.authenticationlogic.model.LocalRecoveryResetResult
import eu.europa.ec.authenticationlogic.model.LocalUnlockStatus
import eu.europa.ec.authenticationlogic.secure.SecurePin
import eu.europa.ec.authenticationlogic.secure.SecurePinImpl
import androidx.lifecycle.viewModelScope
import eu.europa.ec.businesslogic.validator.Form
import eu.europa.ec.businesslogic.validator.FormValidationResult
import eu.europa.ec.businesslogic.validator.Rule
import eu.europa.ec.commonfeature.config.SuccessUIConfig
import eu.europa.ec.commonfeature.interactor.BiometricInteractor
import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractor
import eu.europa.ec.commonfeature.interactor.QuickPinInteractor
import eu.europa.ec.commonfeature.interactor.QuickPinInteractorPinValidPartialState
import eu.europa.ec.commonfeature.interactor.QuickPinInteractorSetPinPartialState
import eu.europa.ec.commonfeature.model.PinFlow
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.ModuleRoute
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam

enum class PinValidationState {
    ENTER,
    REENTER,
    VALIDATE
}

data class State(
        private val pinFlow: PinFlow,
        val isLoading: Boolean = false,
        val isButtonEnabled: Boolean = false,
        val quickPinError: String? = null,
        val isBiometricsEnabled: Boolean = false,
        val showBiometricsPreferencePrompt: Boolean = false,
        val pendingNavigationRoute: String? = null,
        val validationResult: FormValidationResult = FormValidationResult(false),
        val subtitle: String = "",
        val title: String = "",
        val pinLength: Int = 0,
        val buttonText: String = "",
        val resetPin: Boolean = false,
        val pinState: PinValidationState,
        val isBottomSheetOpen: Boolean = false,
        val quickPinSize: Int = 6,
        val showOverflowMenu: Boolean = false,
        val showResetConfirmation: Boolean = false,
        val pinSuccess: Boolean = false,
        val isTransitioning: Boolean = false,
        val isRecoveringPin: Boolean = false,
) : ViewState {

    val isVerifyFlow: Boolean
        get() = pinFlow == PinFlow.VERIFY
    val action: ScreenNavigateAction
        get() {
            return when (pinFlow) {
                PinFlow.CREATE_WITH_ACTIVATION, PinFlow.CREATE_WITHOUT_ACTIVATION -> ScreenNavigateAction.NONE
                PinFlow.UPDATE -> ScreenNavigateAction.CANCELABLE
                PinFlow.VERIFY -> ScreenNavigateAction.NONE
            }
        }

    val shouldShowBiometricLoginButton: Boolean
        get() = isVerifyFlow && isBiometricsEnabled

    val onBackEvent: Event
        get() {
            return when (pinFlow) {
                PinFlow.CREATE_WITH_ACTIVATION, PinFlow.CREATE_WITHOUT_ACTIVATION -> Event.Finish
                PinFlow.UPDATE -> Event.CancelPressed
                PinFlow.VERIFY -> Event.Finish
            }
        }
}

sealed class Event : ViewEvent {
    data object NextButtonPressed : Event()
    data class OnQuickPinDigitPressed(val digit: Int) : Event()
    data object OnQuickPinBackspacePressed : Event()
    data class OnQuickPinEntered(val quickPin: String) : Event()
    data class OnBiometricLoginPressed(val context: Context) : Event()
    data class OnBiometricsPreferenceSelected(val shouldUseBiometrics: Boolean) : Event()
    data object CancelPressed : Event()
    data object Finish : Event()
    data class OverflowMenuToggled(val isOpen: Boolean) : Event()
    data object ForgotPinPressed : Event()
    sealed class BottomSheet : Event() {
        data class UpdateBottomSheetState(val isOpen: Boolean) : BottomSheet()

        sealed class Cancel : BottomSheet() {
            data object PrimaryButtonPressed : Cancel()
            data object SecondaryButtonPressed : Cancel()
        }

        sealed class Reset : BottomSheet() {
            data class ConfirmPressed(val context: Context) : Reset()
            data object CancelPressed : Reset()
        }
    }
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchModule(val moduleRoute: ModuleRoute) : Navigation()
        data class SwitchScreen(val screen: String) : Navigation()

        data object Pop : Navigation()
        data object Finish : Navigation()
        data object GoToLogin : Navigation()
        data object GoToWalletSetup : Navigation()
    }

    data object ShowBottomSheet : Effect()
    data object CloseBottomSheet : Effect()
    data object TriggerBiometricAuth : Effect()
}

@KoinViewModel
class PinViewModel(
        private val interactor: QuickPinInteractor,
        private val biometricInteractor: BiometricInteractor,
        private val deviceAuthenticationInteractor: DeviceAuthenticationInteractor,
        private val resourceProvider: ResourceProvider,
        private val uiSerializer: UiSerializer,
        @InjectedParam private val pinFlow: PinFlow
) : MviViewModel<Event, State, Effect>() {

    // Cached at init time so saveNewPin() can decide whether to show the prompt post-creation.
    private var biometricsPreferenceNotYetDecided: Boolean = false
    private val pinBuffer: CharArray = CharArray(DEFAULT_PIN_SIZE)
    private var pinBufferLength: Int = 0
    private var pendingEnteredPin: SecurePin? = null

    override fun setInitialState(): State {
        val title: String
        val subtitle: String
        val pinState: PinValidationState
        val buttonText: String
        val isBiometricsEnabled: Boolean = runBlocking {
            biometricInteractor.getBiometricUserSelection()
        }
        val hasBiometricsPreferenceBeenDecided: Boolean = runBlocking {
            biometricInteractor.getBiometricsPreferenceDecided()
        }
        val localUnlockStatus: LocalUnlockStatus = runBlocking {
            interactor.getLocalUnlockStatus()
        }
        biometricsPreferenceNotYetDecided = !hasBiometricsPreferenceBeenDecided
        // Prompt is always shown AFTER PIN is validated — never at screen start.
        val shouldPromptForBiometricsPreference: Boolean = false

        when (pinFlow) {
            PinFlow.CREATE_WITH_ACTIVATION, PinFlow.CREATE_WITHOUT_ACTIVATION -> {
                title = resourceProvider.getString(R.string.quick_pin_create_title)
                subtitle = resourceProvider.getString(R.string.quick_pin_create_enter_subtitle)
                pinState = PinValidationState.ENTER
                buttonText = calculateButtonText(pinState)
            }
            PinFlow.UPDATE -> {
                title = resourceProvider.getString(R.string.quick_pin_change_title)
                subtitle =
                        resourceProvider.getString(
                                R.string.quick_pin_change_validate_current_subtitle
                        )
                pinState = PinValidationState.VALIDATE
                buttonText = calculateButtonText(pinState)
            }
            PinFlow.VERIFY -> {
                title = resourceProvider.getString(R.string.quick_pin_verify_title)
                subtitle = resourceProvider.getString(R.string.quick_pin_verify_subtitle)
                pinState = PinValidationState.VALIDATE
                buttonText = resourceProvider.getString(R.string.generic_verify_capitalized)
            }
        }

        return State(
                isLoading = false,
                title = title,
                subtitle = subtitle,
                pinState = pinState,
                buttonText = buttonText,
                isBiometricsEnabled = isBiometricsEnabled,
                showBiometricsPreferencePrompt = shouldPromptForBiometricsPreference,
                pinFlow = pinFlow,
                quickPinError = initialErrorFor(localUnlockStatus)
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.OnQuickPinDigitPressed -> {
                appendPinDigit(event.digit)
            }
            Event.OnQuickPinBackspacePressed -> {
                removePinDigit()
            }
            is Event.OnQuickPinEntered -> {
                validateForm(event.quickPin)
            }
            is Event.OnBiometricLoginPressed -> {
                authenticateWithBiometrics(event.context)
            }
            is Event.OnBiometricsPreferenceSelected -> {
                storeBiometricsPreference(event.shouldUseBiometrics)
            }
            is Event.NextButtonPressed -> {
                if (pinBufferLength != viewState.value.quickPinSize) {
                    return
                }
                when (viewState.value.pinState) {
                    PinValidationState.ENTER -> {
                        setupReenterPhase(enteredPin = consumePinBuffer())
                    }
                    PinValidationState.REENTER -> {
                        saveNewPin(newPin = consumePinBuffer())
                    }
                    PinValidationState.VALIDATE -> {
                        validatePin(currentPin = consumePinBuffer())
                    }
                }
            }
            is Event.CancelPressed -> {
                showBottomSheet()
            }
            is Event.BottomSheet.UpdateBottomSheetState -> {
                setState { copy(isBottomSheetOpen = event.isOpen) }
            }
            is Event.BottomSheet.Cancel.PrimaryButtonPressed -> {
                hideBottomSheet()
            }
            is Event.BottomSheet.Cancel.SecondaryButtonPressed -> {
                viewModelScope.launch {
                    clearPendingPin()
                    hideBottomSheet()
                    delay(200L)
                    setEffect { Effect.Navigation.Pop }
                }
            }
            is Event.OverflowMenuToggled -> {
                setState { copy(showOverflowMenu = event.isOpen) }
            }
            is Event.ForgotPinPressed -> {
                setState { copy(showOverflowMenu = false, showResetConfirmation = true) }
            }
            is Event.BottomSheet.Reset.ConfirmPressed -> {
                recoverPinOrReset(context = event.context)
            }
            is Event.BottomSheet.Reset.CancelPressed -> {
                setState { copy(showResetConfirmation = false) }
            }
            is Event.Finish -> {
                clearPendingPin()
                clearPinBuffer()
                setEffect { Effect.Navigation.Finish }
            }
        }
    }

    private fun validatePin(currentPin: SecurePin) {
        viewModelScope.launch {
            interactor.isCurrentPinValid(pin = currentPin).collect {
                when (it) {
                    is QuickPinInteractorPinValidPartialState.Failed -> {
                        clearPinBuffer()
                        setState {
                            copy(
                                quickPinError = it.errorMessage,
                                isButtonEnabled = false,
                                pinLength = 0,
                                resetPin = true,
                                showResetConfirmation = it.requiresRecovery
                            )
                        }
                    }
                    QuickPinInteractorPinValidPartialState.Success -> {
                        handlePinValidationSuccess()
                    }
                }
            }
        }
    }

    private fun handlePinValidationSuccess() {
        viewModelScope.launch {
            setState { copy(pinSuccess = true) }
            delay(250L)
            if (viewState.value.isRecoveringPin) {
                setState { copy(pinSuccess = false, isTransitioning = true) }
                delay(75L)
                setEffect { Effect.Navigation.SwitchScreen(DashboardScreens.Dashboard.screenRoute) }
                return@launch
            }
            when (pinFlow) {
                PinFlow.VERIFY -> {
                    if (biometricsPreferenceNotYetDecided) {
                        // PIN verified — now ask about biometrics before going to the dashboard.
                        setState {
                            copy(
                                pinSuccess = false,
                                pendingNavigationRoute = DashboardScreens.Dashboard.screenRoute,
                                showBiometricsPreferencePrompt = true
                            )
                        }
                    } else {
                        setState { copy(isTransitioning = true) }
                        delay(75L)
                        setEffect { Effect.Navigation.SwitchScreen(DashboardScreens.Dashboard.screenRoute) }
                    }
                }
                PinFlow.UPDATE -> {
                    setState { copy(pinSuccess = false, isTransitioning = false) }
                    setupEnterPhase()
                }
                PinFlow.CREATE_WITH_ACTIVATION, PinFlow.CREATE_WITHOUT_ACTIVATION -> {
                    setState { copy(pinSuccess = false, isTransitioning = false) }
                    setupEnterPhase()
                }
            }
        }
    }

    private fun setupEnterPhase() {
        val newPinState = PinValidationState.ENTER

        clearPendingPin()
        clearPinBuffer()
        setState {
            copy(
                    quickPinError = null,
                    isButtonEnabled = false,
                    pinState = newPinState,
                    buttonText = calculateButtonText(newPinState),
                    pinLength = 0,
                    resetPin = true,
                    subtitle = calculateSubtitle(newPinState),
                    isRecoveringPin = false
            )
        }
    }

    private fun setupRecoveryPhase() {
        val newPinState = PinValidationState.ENTER
        clearPendingPin()
        clearPinBuffer()
        setState {
            copy(
                quickPinError = null,
                isButtonEnabled = false,
                pinState = newPinState,
                buttonText = calculateButtonText(newPinState),
                pinLength = 0,
                resetPin = true,
                subtitle = resourceProvider.getString(R.string.quick_pin_create_enter_subtitle),
                title = resourceProvider.getString(R.string.quick_pin_create_title),
                showResetConfirmation = false,
                isRecoveringPin = true
            )
        }
    }

    private fun setupReenterPhase(enteredPin: SecurePin) {
        val newPinState = PinValidationState.REENTER
        clearPendingPin()
        pendingEnteredPin = enteredPin
        clearPinBuffer()

        setState {
            copy(
                    quickPinError = null,
                    isButtonEnabled = false,
                    pinState = PinValidationState.REENTER,
                    buttonText = calculateButtonText(newPinState),
                    pinLength = 0,
                    resetPin = true,
                    subtitle = calculateSubtitle(newPinState)
            )
        }
    }

    private fun saveNewPin(newPin: SecurePin) {
        val enteredPin: SecurePin = pendingEnteredPin ?: run {
            newPin.close()
            return
        }
        pendingEnteredPin = null
        viewModelScope.launch {
            interactor.setPin(newPin = newPin, initialPin = enteredPin).collect {
                when (it) {
                    is QuickPinInteractorSetPinPartialState.Failed -> {
                        clearPinBuffer()
                        setState {
                            copy(
                                quickPinError = it.errorMessage,
                                isButtonEnabled = false,
                                pinLength = 0
                            )
                        }
                    }
                    is QuickPinInteractorSetPinPartialState.Success -> {
                        if (viewState.value.isRecoveringPin) {
                            interactor.reportRecoveryCompleted(signals = listOf("pin_re_enrolled"))
                        }
                        val nextRoute = if (viewState.value.isRecoveringPin) {
                            DashboardScreens.Dashboard.screenRoute
                        } else {
                            getNextScreenRoute()
                        }
                        clearPinBuffer()
                        setState {
                            copy(
                                isButtonEnabled = false,
                                pinSuccess = true,
                                pinLength = 0
                            )
                        }
                        delay(250L)
                        if (biometricsPreferenceNotYetDecided && !viewState.value.isRecoveringPin) {
                            // Ask about biometrics now that the PIN is set, before proceeding.
                            setState {
                                copy(
                                    pinSuccess = false,
                                    pendingNavigationRoute = nextRoute,
                                    showBiometricsPreferencePrompt = true
                                )
                            }
                        } else {
                            setState { copy(isTransitioning = true) }
                            delay(75L)
                            setEffect { Effect.Navigation.SwitchScreen(nextRoute) }
                        }
                    }
                }
            }
        }
    }

    private fun getListOfRules(pin: String): Form {
        return Form(
                mapOf(
                        listOf(
                                Rule.ValidateStringRange(
                                        viewState.value.quickPinSize..viewState.value.quickPinSize,
                                        ""
                                ),
                                Rule.ValidateRegex(
                                        "-?\\d+(\\.\\d+)?".toRegex(),
                                        resourceProvider.getString(
                                                R.string
                                                        .quick_pin_numerical_rule_invalid_error_message
                                        )
                                )
                        ) to pin
                )
        )
    }

    private fun validateForm(pin: String) {
        viewModelScope.launch {
            val validationResult = interactor.validateForm(getListOfRules(pin))
            if (validationResult.isValid || pin.length < viewState.value.quickPinSize) {
                replacePinBuffer(pin)
            } else {
                clearPinBuffer()
            }
            setState {
                copy(
                        validationResult = validationResult,
                        isButtonEnabled = validationResult.isValid,
                        quickPinError = validationResult.message,
                        pinLength = pinBufferLength,
                        resetPin = false
                )
            }
        }
    }

    private fun storeBiometricsPreference(shouldUseBiometrics: Boolean) {
        viewModelScope.launch {
            biometricInteractor.storeBiometricsUsageDecision(shouldUseBiometrics)
            biometricInteractor.storeBiometricsPreferenceDecided(true)
            val pendingRoute = viewState.value.pendingNavigationRoute
            setState {
                copy(
                        isBiometricsEnabled = shouldUseBiometrics,
                        showBiometricsPreferencePrompt = false,
                        // Keep pendingNavigationRoute if biometrics enabled — authenticateWithBiometrics
                        // needs it to decide what to do on completion. Cleared there.
                        pendingNavigationRoute = if (shouldUseBiometrics) pendingNavigationRoute else null,
                        quickPinError = null
                )
            }
            when {
                shouldUseBiometrics -> {
                    // Both CREATE and VERIFY: trigger biometric scan immediately after enabling.
                    setEffect { Effect.TriggerBiometricAuth }
                }
                pendingRoute != null -> {
                    // Biometrics declined after PIN verified — navigate forward without a scan.
                    setState { copy(isTransitioning = true) }
                    delay(75L)
                    setEffect { Effect.Navigation.SwitchScreen(pendingRoute) }
                }
            }
        }
    }

    private fun authenticateWithBiometrics(context: Context) {
        setState { copy(quickPinError = null) }
        // Capture now — used inside the async callback to decide post-auth behaviour.
        val pendingRoute = viewState.value.pendingNavigationRoute
        biometricInteractor.getBiometricsAvailability { availability ->
            when (availability) {
                BiometricsAvailability.CanAuthenticate -> {
                    biometricInteractor.authenticateWithBiometrics(
                            context = context,
                            notifyOnAuthenticationFailure = pendingRoute == null
                    ) { authResult ->
                        when (authResult) {
                            is BiometricsAuthenticate.Success -> {
                                if (pendingRoute != null) {
                                    // CREATE flow: scan confirmed — proceed to success screen.
                                    navigateAfterBiometricActivation(pendingRoute)
                                } else {
                                    // VERIFY flow: authenticated — go to dashboard.
                                    handlePinValidationSuccess()
                                }
                            }
                            is BiometricsAuthenticate.Failed -> {
                                if (pendingRoute != null) {
                                    // CREATE flow: scan failed but preference is saved — proceed anyway.
                                    navigateAfterBiometricActivation(pendingRoute)
                                } else {
                                    clearPinBuffer()
                                    setState {
                                        copy(
                                            quickPinError = authResult.errorMessage,
                                            isButtonEnabled = false,
                                            pinLength = 0,
                                            resetPin = true
                                        )
                                    }
                                }
                            }
                            BiometricsAuthenticate.Cancelled -> {
                                if (pendingRoute != null) {
                                    // CREATE flow: scan cancelled — proceed anyway.
                                    navigateAfterBiometricActivation(pendingRoute)
                                }
                                // VERIFY flow cancelled: nothing — user can use PIN instead.
                            }
                        }
                    }
                }
                BiometricsAvailability.NonEnrolled -> {
                    if (pendingRoute != null) {
                        navigateAfterBiometricActivation(pendingRoute)
                    } else {
                        clearPinBuffer()
                        setState {
                            copy(
                                    quickPinError = resourceProvider.getString(
                                            R.string.quick_pin_biometric_not_enrolled_error
                                    ),
                                    isButtonEnabled = false,
                                    pinLength = 0,
                                    resetPin = true
                            )
                        }
                    }
                }
                is BiometricsAvailability.Failure -> {
                    if (pendingRoute != null) {
                        navigateAfterBiometricActivation(pendingRoute)
                    } else {
                        clearPinBuffer()
                        setState {
                            copy(
                                quickPinError = availability.errorMessage,
                                isButtonEnabled = false,
                                pinLength = 0,
                                resetPin = true
                            )
                        }
                    }
                }
            }
        }
    }

    private fun navigateAfterBiometricActivation(route: String) {
        viewModelScope.launch {
            setState { copy(pendingNavigationRoute = null, isTransitioning = true) }
            delay(75L)
            setEffect { Effect.Navigation.SwitchScreen(route) }
        }
    }

    private fun initialErrorFor(status: LocalUnlockStatus): String? {
        return when (status) {
            is LocalUnlockStatus.TemporarilyLocked ->
                resourceProvider.getString(R.string.quick_pin_locked_error)
            LocalUnlockStatus.RecoveryRequired ->
                resourceProvider.getString(R.string.quick_pin_recovery_required_error)
            LocalUnlockStatus.TamperDetected ->
                resourceProvider.getString(R.string.quick_pin_security_error)
            else -> null
        }
    }

    private fun calculateSubtitle(pinState: PinValidationState): String {
        return when (pinFlow) {
            PinFlow.UPDATE -> {
                when (pinState) {
                    PinValidationState.ENTER ->
                            resourceProvider.getString(R.string.quick_pin_change_enter_new_subtitle)
                    PinValidationState.REENTER ->
                            resourceProvider.getString(
                                    R.string.quick_pin_change_reenter_new_subtitle
                            )
                    PinValidationState.VALIDATE ->
                            resourceProvider.getString(
                                    R.string.quick_pin_change_validate_current_subtitle
                            )
                }
            }
            PinFlow.CREATE_WITH_ACTIVATION, PinFlow.CREATE_WITHOUT_ACTIVATION -> {
                when (pinState) {
                    PinValidationState.ENTER ->
                            resourceProvider.getString(R.string.quick_pin_create_enter_subtitle)
                    PinValidationState.REENTER ->
                            resourceProvider.getString(R.string.quick_pin_create_reenter_subtitle)
                    PinValidationState.VALIDATE -> viewState.value.subtitle
                }
            }
            PinFlow.VERIFY -> {
                // VERIFY flow only uses VALIDATE state, always show the same subtitle
                resourceProvider.getString(R.string.quick_pin_verify_subtitle)
            }
        }
    }

    private fun calculateButtonText(pinState: PinValidationState): String {
        return when (pinFlow) {
            PinFlow.CREATE_WITH_ACTIVATION, PinFlow.CREATE_WITHOUT_ACTIVATION ->
                    when (pinState) {
                        PinValidationState.ENTER ->
                                resourceProvider.getString(
                                        R.string.quick_pin_create_button_continue
                                )
                        PinValidationState.REENTER ->
                                resourceProvider.getString(R.string.quick_pin_create_button_confirm)
                        PinValidationState.VALIDATE ->
                                resourceProvider.getString(R.string.generic_next_capitalized)
                    }
            PinFlow.VERIFY -> resourceProvider.getString(R.string.quick_pin_verify_button_unlock)
            PinFlow.UPDATE ->
                    when (pinState) {
                        PinValidationState.ENTER ->
                                resourceProvider.getString(R.string.generic_next_capitalized)
                        PinValidationState.REENTER ->
                                resourceProvider.getString(R.string.generic_confirm_capitalized)
                        PinValidationState.VALIDATE ->
                                resourceProvider.getString(R.string.generic_next_capitalized)
                    }
        }
    }

    private fun getNextScreenRoute(): String {
        // Fail fast if called for VERIFY flow (programming error)
        // VERIFY flow navigates directly to Dashboard from validatePin() - no success screen
        check(pinFlow != PinFlow.VERIFY) {
            "getNextScreenRoute() should not be called for VERIFY flow"
        }

        // CREATE flows skip the intermediate "wallet secured" confirmation screen and
        // navigate straight to the Dashboard. That screen was purely cosmetic — biometric
        // preference is already resolved before this point, and device security is
        // enforced earlier in the authentication graph (DeviceSecurityRequired). The
        // QuickPin route is popped by handleNavigationEffect on Effect.Navigation.SwitchScreen.
        if (pinFlow == PinFlow.CREATE_WITH_ACTIVATION || pinFlow == PinFlow.CREATE_WITHOUT_ACTIVATION) {
            return DashboardScreens.Dashboard.screenRoute
        }

        // UPDATE flow: show "PIN changed" confirmation, then pop back to Dashboard
        // (which is already in the nav stack).
        val navigationAfterUpdate =
                ConfigNavigation(
                        navigationType = NavigationType.PopTo(DashboardScreens.Dashboard),
                )

        return generateComposableNavigationLink(
            screen = CommonScreens.Success,
            arguments = generateComposableArguments(
                mapOf(
                    SuccessUIConfig.serializedKeyName to uiSerializer.toBase64(
                        SuccessUIConfig(
                            textElementsConfig = SuccessUIConfig.TextElementsConfig(
                                text = resourceProvider.getString(R.string.quick_pin_change_success_text),
                                description = resourceProvider.getString(R.string.quick_pin_change_success_description),
                            ),
                            imageConfig = SuccessUIConfig.ImageConfig(),
                            buttonConfig = listOf(
                                SuccessUIConfig.ButtonConfig(
                                    text = resourceProvider.getString(R.string.quick_pin_change_success_btn),
                                    style = SuccessUIConfig.ButtonConfig.Style.PRIMARY,
                                    navigation = navigationAfterUpdate,
                                )
                            ),
                            onBackScreenToNavigate = navigationAfterUpdate,
                        ),
                        SuccessUIConfig.Parser
                    ).orEmpty()
                )
            )
        )
    }

    private fun recoverPinOrReset(context: Context) {
        setState { copy(isLoading = true, showResetConfirmation = false) }
        deviceAuthenticationInteractor.authenticateWithBiometrics(
            context = context,
            crypto = BiometricCrypto(null),
            notifyOnAuthenticationFailure = true,
            resultHandler = DeviceAuthenticationResult(
                onAuthenticationSuccess = {
                    interactor.reportRecoveryStarted(signals = listOf("device_auth_verified"))
                    when (interactor.beginPinRecovery()) {
                        LocalUnlockStatus.ReadyForPin -> {
                            setState { copy(isLoading = false) }
                            setupRecoveryPhase()
                        }
                        else -> {
                            when (val result = interactor.performLocalRecoveryReset()) {
                                LocalRecoveryResetResult.LocalResetComplete -> {
                                    setState { copy(isLoading = false) }
                                    setEffect { Effect.Navigation.GoToWalletSetup }
                                }
                                is LocalRecoveryResetResult.LocalResetBlocked -> {
                                    setState {
                                        copy(
                                            isLoading = false,
                                            quickPinError = result.errorMessage
                                        )
                                    }
                                }
                                is LocalRecoveryResetResult.SecurityFailure -> {
                                    setState {
                                        copy(
                                            isLoading = false,
                                            quickPinError = result.errorMessage
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                onAuthenticationError = {
                    viewModelScope.launch {
                        setState {
                            copy(
                                isLoading = false,
                                quickPinError = resourceProvider.getString(
                                    R.string.quick_pin_recovery_required_error
                                )
                            )
                        }
                    }
                },
                onAuthenticationFailure = {
                    viewModelScope.launch {
                        setState { copy(isLoading = false) }
                    }
                }
            )
        )
    }

    private fun showBottomSheet() {
        setEffect { Effect.ShowBottomSheet }
    }

    private fun hideBottomSheet() {
        setEffect { Effect.CloseBottomSheet }
    }

    override fun onCleared() {
        clearPendingPin()
        clearPinBuffer()
        super.onCleared()
    }

    private fun appendPinDigit(digit: Int) {
        if (digit !in 0..9) {
            return
        }
        if (!viewState.value.quickPinError.isNullOrEmpty()) {
            clearPinBuffer()
        }
        if (pinBufferLength >= viewState.value.quickPinSize) {
            return
        }
        pinBuffer[pinBufferLength] = ('0'.code + digit).toChar()
        pinBufferLength += 1
        setState {
            copy(
                quickPinError = null,
                isButtonEnabled = pinBufferLength == viewState.value.quickPinSize,
                pinLength = pinBufferLength,
                resetPin = false
            )
        }
        if (pinBufferLength == viewState.value.quickPinSize) {
            viewModelScope.launch {
                delay(150L)
                if (pinBufferLength == viewState.value.quickPinSize) {
                    handleEvents(Event.NextButtonPressed)
                }
            }
        }
    }

    private fun removePinDigit() {
        if (pinBufferLength <= 0) {
            if (!viewState.value.quickPinError.isNullOrEmpty()) {
                setState {
                    copy(
                        quickPinError = null,
                        isButtonEnabled = false,
                        pinLength = 0
                    )
                }
            }
            return
        }
        pinBufferLength -= 1
        pinBuffer[pinBufferLength] = '\u0000'
        setState {
            copy(
                quickPinError = null,
                isButtonEnabled = false,
                pinLength = pinBufferLength,
                resetPin = false
            )
        }
    }

    private fun replacePinBuffer(pin: String) {
        clearPinBuffer()
        pin.take(viewState.value.quickPinSize).forEach { char ->
            pinBuffer[pinBufferLength] = char
            pinBufferLength += 1
        }
    }

    private fun consumePinBuffer(): SecurePin {
        val securePin: SecurePin = SecurePinImpl.from(pinBuffer, pinBufferLength)
        clearPinBuffer()
        setState { copy(isButtonEnabled = false, pinLength = 0, resetPin = true) }
        return securePin
    }

    private fun clearPinBuffer() {
        pinBuffer.fill('\u0000')
        pinBufferLength = 0
    }

    private fun clearPendingPin() {
        pendingEnteredPin?.close()
        pendingEnteredPin = null
    }

    private companion object {
        const val DEFAULT_PIN_SIZE = 6
    }
}
