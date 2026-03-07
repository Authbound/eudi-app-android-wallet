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
import androidx.lifecycle.viewModelScope
import eu.europa.ec.authenticationlogic.usecase.SignOutMode
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.businesslogic.validator.Form
import eu.europa.ec.businesslogic.validator.FormValidationResult
import eu.europa.ec.businesslogic.validator.Rule
import eu.europa.ec.commonfeature.config.SuccessUIConfig
import eu.europa.ec.commonfeature.interactor.BiometricInteractor
import eu.europa.ec.commonfeature.interactor.QuickPinInteractor
import eu.europa.ec.commonfeature.interactor.QuickPinInteractorPinValidPartialState
import eu.europa.ec.commonfeature.interactor.QuickPinInteractorSetPinPartialState
import eu.europa.ec.commonfeature.model.PinFlow
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.AppIcons
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
import org.koin.android.annotation.KoinViewModel
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
        val validationResult: FormValidationResult = FormValidationResult(false),
        val subtitle: String = "",
        val title: String = "",
        val pin: String = "",
        val enteredPin: String = "",
        val buttonText: String = "",
        val resetPin: Boolean = false,
        val pinState: PinValidationState,
        val isBottomSheetOpen: Boolean = false,
        val quickPinSize: Int = 6,
        val showOverflowMenu: Boolean = false,
        val showResetConfirmation: Boolean = false,
        val pinSuccess: Boolean = false,
        val isTransitioning: Boolean = false,
) : ViewState {

    val isVerifyFlow: Boolean
        get() = pinFlow == PinFlow.VERIFY
    val action: ScreenNavigateAction
        get() {
            return when (pinFlow) {
                PinFlow.CREATE -> ScreenNavigateAction.NONE
                PinFlow.UPDATE -> ScreenNavigateAction.CANCELABLE
                PinFlow.VERIFY -> ScreenNavigateAction.NONE
            }
        }

    val shouldShowBiometricLoginButton: Boolean
        get() = isVerifyFlow && isBiometricsEnabled

    val onBackEvent: Event
        get() {
            return when (pinFlow) {
                PinFlow.CREATE -> Event.Finish
                PinFlow.UPDATE -> Event.CancelPressed
                PinFlow.VERIFY -> Event.Finish
            }
        }
}

sealed class Event : ViewEvent {
    data class NextButtonPressed(val pin: String) : Event()
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
            data object ConfirmPressed : Reset()
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
    }

    data object ShowBottomSheet : Effect()
    data object CloseBottomSheet : Effect()
}

@KoinViewModel
class PinViewModel(
        private val interactor: QuickPinInteractor,
        private val biometricInteractor: BiometricInteractor,
        private val resourceProvider: ResourceProvider,
        private val uiSerializer: UiSerializer,
        private val signOutUseCase: SignOutUseCase,
        @InjectedParam private val pinFlow: PinFlow
) : MviViewModel<Event, State, Effect>() {

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
        val shouldPromptForBiometricsPreference: Boolean =
                (pinFlow == PinFlow.CREATE || pinFlow == PinFlow.VERIFY) &&
                        !hasBiometricsPreferenceBeenDecided

        when (pinFlow) {
            PinFlow.CREATE -> {
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
                pinFlow = pinFlow
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
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
                val state = viewState.value

                when (state.pinState) {
                    PinValidationState.ENTER -> {
                        // Set state for re-enter phase
                        setupReenterPhase(enteredPin = event.pin)
                    }
                    PinValidationState.REENTER -> {
                        // Save the new pin
                        saveNewPin(newPin = state.pin, enteredPin = state.enteredPin)
                    }
                    PinValidationState.VALIDATE -> {
                        validatePin(currentPin = state.pin)
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
                resetPinAndSignOut()
            }
            is Event.BottomSheet.Reset.CancelPressed -> {
                setState { copy(showResetConfirmation = false) }
            }
            is Event.Finish -> setEffect { Effect.Navigation.Finish }
        }
    }

    private fun validatePin(currentPin: String) {
        viewModelScope.launch {
            interactor.isCurrentPinValid(pin = currentPin).collect {
                when (it) {
                    is QuickPinInteractorPinValidPartialState.Failed -> {
                        setState {
                            copy(quickPinError = it.errorMessage, pin = "", resetPin = true)
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
            setState { copy(isTransitioning = true) }
            delay(75L)
            when (pinFlow) {
                PinFlow.VERIFY -> {
                    setEffect {
                        Effect.Navigation.SwitchScreen(
                                DashboardScreens.Dashboard.screenRoute
                        )
                    }
                }
                PinFlow.UPDATE -> {
                    setState { copy(pinSuccess = false, isTransitioning = false) }
                    setupEnterPhase()
                }
                PinFlow.CREATE -> {
                    setState { copy(pinSuccess = false, isTransitioning = false) }
                    setupEnterPhase()
                }
            }
        }
    }

    private fun setupEnterPhase() {
        val newPinState = PinValidationState.ENTER

        setState {
            copy(
                    quickPinError = null,
                    enteredPin = "",
                    pinState = newPinState,
                    buttonText = calculateButtonText(newPinState),
                    pin = "",
                    resetPin = true,
                    subtitle = calculateSubtitle(newPinState)
            )
        }
    }

    private fun setupReenterPhase(enteredPin: String) {
        val newPinState = PinValidationState.REENTER

        setState {
            copy(
                    quickPinError = null,
                    enteredPin = enteredPin,
                    pinState = PinValidationState.REENTER,
                    buttonText = calculateButtonText(newPinState),
                    pin = "",
                    resetPin = true,
                    subtitle = calculateSubtitle(newPinState)
            )
        }
    }

    private fun saveNewPin(newPin: String, enteredPin: String) {
        viewModelScope.launch {
            interactor.setPin(newPin = newPin, initialPin = enteredPin).collect {
                when (it) {
                    is QuickPinInteractorSetPinPartialState.Failed -> {
                        setState { copy(quickPinError = it.errorMessage) }
                    }
                    is QuickPinInteractorSetPinPartialState.Success -> {
                        setState { copy(pinSuccess = true) }
                        delay(250L)
                        setState { copy(isTransitioning = true) }
                        delay(75L)
                        setEffect { Effect.Navigation.SwitchScreen(getNextScreenRoute()) }
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
            setState {
                copy(
                        validationResult = validationResult,
                        isButtonEnabled = validationResult.isValid,
                        quickPinError = validationResult.message,
                        pin = pin,
                        resetPin = false
                )
            }
        }
    }

    private fun storeBiometricsPreference(shouldUseBiometrics: Boolean) {
        viewModelScope.launch {
            biometricInteractor.storeBiometricsUsageDecision(shouldUseBiometrics)
            biometricInteractor.storeBiometricsPreferenceDecided(true)
            setState {
                copy(
                        isBiometricsEnabled = shouldUseBiometrics,
                        showBiometricsPreferencePrompt = false,
                        quickPinError = null
                )
            }
        }
    }

    private fun authenticateWithBiometrics(context: Context) {
        setState { copy(quickPinError = null) }
        biometricInteractor.getBiometricsAvailability { availability ->
            when (availability) {
                BiometricsAvailability.CanAuthenticate -> {
                    biometricInteractor.authenticateWithBiometrics(
                            context = context,
                            notifyOnAuthenticationFailure = true
                    ) { authResult ->
                        when (authResult) {
                            is BiometricsAuthenticate.Success -> {
                                handlePinValidationSuccess()
                            }
                            is BiometricsAuthenticate.Failed -> {
                                setState {
                                    copy(
                                            quickPinError = authResult.errorMessage,
                                            pin = "",
                                            resetPin = true
                                    )
                                }
                            }
                            BiometricsAuthenticate.Cancelled -> Unit
                        }
                    }
                }
                BiometricsAvailability.NonEnrolled -> {
                    setState {
                        copy(
                                quickPinError =
                                        resourceProvider.getString(
                                                R.string.quick_pin_biometric_not_enrolled_error
                                        ),
                                pin = "",
                                resetPin = true
                        )
                    }
                }
                is BiometricsAvailability.Failure -> {
                    setState {
                        copy(
                                quickPinError = availability.errorMessage,
                                pin = "",
                                resetPin = true
                        )
                    }
                }
            }
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
            PinFlow.CREATE -> {
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
            PinFlow.CREATE ->
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

        // After PIN creation → Push to Dashboard (Dashboard not in nav stack yet)
        val navigationAfterCreate =
                ConfigNavigation(
                        navigationType =
                                NavigationType.PushScreen(
                                        screen = DashboardScreens.Dashboard,
                                        popUpToScreen = CommonScreens.QuickPin
                                ),
                )

        // After PIN update (change) → Pop back to Dashboard (already in nav stack)
        val navigationAfterUpdate =
                ConfigNavigation(
                        navigationType = NavigationType.PopTo(DashboardScreens.Dashboard),
                )

        val navigation =
                when (pinFlow) {
                    PinFlow.CREATE -> navigationAfterCreate
                    PinFlow.UPDATE -> navigationAfterUpdate
                    PinFlow.VERIFY -> error("Unreachable - checked above")
                }

        val (successText, successDescription, successButton) =
                when (pinFlow) {
                    PinFlow.CREATE ->
                            Triple(
                                    resourceProvider.getString(
                                            R.string.quick_pin_create_success_text
                                    ),
                                    resourceProvider.getString(
                                            R.string.quick_pin_create_success_description
                                    ),
                                    resourceProvider.getString(
                                            R.string.quick_pin_create_success_btn
                                    )
                            )
                    PinFlow.UPDATE ->
                            Triple(
                                    resourceProvider.getString(
                                            R.string.quick_pin_change_success_text
                                    ),
                                    resourceProvider.getString(
                                            R.string.quick_pin_change_success_description
                                    ),
                                    resourceProvider.getString(
                                            R.string.quick_pin_change_success_btn
                                    )
                            )
                    PinFlow.VERIFY -> error("Unreachable - checked above")
                }

        val imageConfig =
                when (pinFlow) {
                    PinFlow.CREATE ->
                            SuccessUIConfig.ImageConfig(
                                    type =
                                            SuccessUIConfig.ImageConfig.Type.Drawable(
                                                    icon = AppIcons.WalletSecured
                                            ),
                                    tint = null,
                            )
                    PinFlow.UPDATE -> SuccessUIConfig.ImageConfig()
                    PinFlow.VERIFY -> error("Unreachable - checked above")
                }

        return generateComposableNavigationLink(
                screen = CommonScreens.Success,
                arguments =
                        generateComposableArguments(
                                mapOf(
                                        SuccessUIConfig.serializedKeyName to
                                                uiSerializer
                                                        .toBase64(
                                                                SuccessUIConfig(
                                                                        textElementsConfig =
                                                                                SuccessUIConfig
                                                                                        .TextElementsConfig(
                                                                                                text =
                                                                                                        successText,
                                                                                                description =
                                                                                                        successDescription
                                                                                        ),
                                                                        imageConfig = imageConfig,
                                                                        buttonConfig =
                                                                                listOf(
                                                                                        SuccessUIConfig
                                                                                                .ButtonConfig(
                                                                                                        text =
                                                                                                                successButton,
                                                                                                        style =
                                                                                                                SuccessUIConfig
                                                                                                                        .ButtonConfig
                                                                                                                        .Style
                                                                                                                        .PRIMARY,
                                                                                                        navigation =
                                                                                                                navigation
                                                                                                )
                                                                                ),
                                                                        onBackScreenToNavigate =
                                                                                navigation,
                                                                ),
                                                                SuccessUIConfig.Parser
                                                        )
                                                        .orEmpty()
                                )
                        )
        )
    }

    private fun resetPinAndSignOut() {
        setState { copy(isLoading = true, showResetConfirmation = false) }
        viewModelScope.launch {
            try {
                interactor.resetPin()
                signOutUseCase(SignOutMode.Soft)
                setEffect { Effect.Navigation.GoToLogin }
            } catch (_: Exception) {
                setState { copy(isLoading = false) }
            }
        }
    }

    private fun showBottomSheet() {
        setEffect { Effect.ShowBottomSheet }
    }

    private fun hideBottomSheet() {
        setEffect { Effect.CloseBottomSheet }
    }
}
