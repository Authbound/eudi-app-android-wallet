/*
 * Copyright (c) 2024 European Commission
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
package eu.europa.ec.authenticationfeature.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import eu.europa.ec.authenticationlogic.model.EmailPasswordRequest
import eu.europa.ec.authenticationlogic.model.OAuthProvider
import eu.europa.ec.authenticationlogic.usecase.ObserveAuthStateUseCase
import eu.europa.ec.authenticationlogic.usecase.SignInWithEmailPasswordUseCase
import eu.europa.ec.authenticationlogic.usecase.SignInWithOAuthUseCase
import eu.europa.ec.authenticationlogic.usecase.SignUpWithEmailPasswordUseCase
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricAuthenticationController
import eu.europa.ec.businesslogic.controller.device.DeviceController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.model.DeviceInfo
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.notificationlogic.controller.PushNotificationController
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.walletactivationlogic.usecase.CreateWalletAttestationUseCase
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class State(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isSignUpMode: Boolean = false,
    val isLoading: Boolean = false,
    val isActivating: Boolean = false,
    val isWalletActivated: Boolean = false,
    val error: String? = null,
) : ViewState

sealed class Event : ViewEvent {
    data class OnEmailChanged(val email: String) : Event()
    data class OnPasswordChanged(val password: String) : Event()
    data class OnConfirmPasswordChanged(val password: String) : Event()
    data object ToggleMode : Event()
    data object SignInWithEmailAndPassword : Event()
    data object SignUpWithEmailAndPassword : Event()
    data class SignInWithOAuth(val provider: OAuthProvider, val context: Context) : Event()
    data object DismissLoading : Event()
    data object ActivateWallet : Event()
}

sealed class Effect : ViewSideEffect {
    data object NavigateToHome : Effect()
    data class ShowError(val message: String) : Effect()
    data class ShowInfo(val message: String) : Effect()
}

@KoinViewModel
class AuthenticationViewModel(
    private val signInWithEmailPasswordUseCase: SignInWithEmailPasswordUseCase,
    private val signUpWithEmailPasswordUseCase: SignUpWithEmailPasswordUseCase,
    private val signInWithOAuthUseCase: SignInWithOAuthUseCase,
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val createWalletAttestationUseCase: CreateWalletAttestationUseCase,
    private val deviceController: DeviceController,
    private val biometricAuthenticationController: BiometricAuthenticationController,
    private val pushNotificationController: PushNotificationController,
    private val prefKeys: PrefKeys,
    private val logController: LogController
) : MviViewModel<Event, State, Effect>() {
    override fun setInitialState(): State {
        observeAuthState()
        return State()
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.OnEmailChanged -> setState { copy(email = event.email) }
            is Event.OnPasswordChanged -> setState { copy(password = event.password) }
            is Event.OnConfirmPasswordChanged -> setState { copy(confirmPassword = event.password) }
            is Event.ToggleMode -> setState {
                copy(
                    isSignUpMode = !viewState.value.isSignUpMode,
                    error = null
                )
            }
            is Event.SignInWithEmailAndPassword -> signInWithEmailPassword()
            is Event.SignUpWithEmailAndPassword -> signUpWithEmailPassword()
            is Event.SignInWithOAuth -> signInWithOAuth(event.provider, event.context)
            is Event.DismissLoading -> setState { copy(isLoading = false) }
            is Event.ActivateWallet -> activateWallet()
        }
    }

    private fun signInWithEmailPassword() {
        viewModelScope.launch {
            try {
                setState { copy(isLoading = true, error = null) }
                val request = EmailPasswordRequest(viewState.value.email, viewState.value.password)
                signInWithEmailPasswordUseCase(request)
            } catch (e: Exception) {
                setState { copy(isLoading = false, error = e.message) }
                setEffect { Effect.ShowError(e.message ?: "An unknown error occurred") }
            }
        }
    }

    private fun signUpWithEmailPassword() {
        if (viewState.value.password != viewState.value.confirmPassword) {
            val message = "Passwords do not match"
            setState { copy(error = message) }
            setEffect { Effect.ShowError(message) }
            return
        }
        viewModelScope.launch {
            try {
                setState { copy(isLoading = true, error = null) }
                val request = EmailPasswordRequest(viewState.value.email, viewState.value.password)
                signUpWithEmailPasswordUseCase(request)
                setEffect { Effect.ShowInfo("Confirmation email sent. Please verify your email.") }
                setState {
                    copy(
                        isLoading = false,
                        isSignUpMode = false,
                        email = "",
                        password = "",
                        confirmPassword = ""
                    )
                }
            } catch (e: Exception) {
                setState { copy(isLoading = false, error = e.message) }
                setEffect { Effect.ShowError(e.message ?: "An unknown error occurred") }
            }
        }
    }

    private fun signInWithOAuth(provider: OAuthProvider, context: Context) {
        viewModelScope.launch {
            try {
                setState { copy(isLoading = true, error = null) }
                signInWithOAuthUseCase(provider, context)
            } catch (e: Exception) {
                setState { copy(isLoading = false, error = e.message) }
                setEffect { Effect.ShowError(e.message ?: "An unknown error occurred") }
            }
        }
    }

    private fun activateWallet() {
        viewModelScope.launch {
            setState { copy(isActivating = true, isLoading = false) }
            try {
                val pushToken =
                    pushNotificationController.registerForPushNotifications().getOrThrow()
                val deviceInfo = getCombinedDeviceInfo()

                logController.d("WalletActivation", "Push Token: $pushToken")
                logController.d("WalletActivation", "Device Info: $deviceInfo")

                createWalletAttestationUseCase(deviceInfo, pushToken).getOrThrow()
                setState { copy(isActivating = false, isWalletActivated = true) }
                setEffect { Effect.NavigateToHome }
            } catch (e: Exception) {
                logController.e("WalletActivation", e)
                setState { copy(isActivating = false, error = e.message) }
                setEffect { Effect.ShowError(e.message ?: "An unknown error occurred") }
            }
        }
    }

    /**
     * Combines basic device info from business-logic with biometric capability from authentication-logic
     */
    private fun getCombinedDeviceInfo(): DeviceInfo {
        val basicDeviceInfo = deviceController.getDeviceInfo()
        val hasBiometricHardware = biometricAuthenticationController.hasBiometricHardware()
        
        return basicDeviceInfo.copy(
            hasBiometricHardware = hasBiometricHardware
        )
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            observeAuthStateUseCase()
                .onStart { setState { copy(isLoading = true) } }
                .catch {
                    setState { copy(isLoading = false, error = it.message) }
                    setEffect { Effect.ShowError(it.message ?: "An unknown error occurred") }
                }
                .collect { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            val user = status.session.user

                            if (user === null) {
                                setState { copy(isLoading = false) }
                                setEffect { Effect.ShowError("User is null") }
                                return@collect
                            }

                            val isEmailOnlyProvider =
                                user.identities?.size == 1 && user.identities?.first()?.provider == "email"

                            if (isEmailOnlyProvider && user.emailConfirmedAt == null) {
                                setState { copy(isLoading = false) }
                                setEffect { Effect.ShowInfo("Please verify your email to continue.") }
                                if (viewState.value.isSignUpMode) {
                                    setState {
                                        copy(
                                            isSignUpMode = false,
                                            email = "",
                                            password = "",
                                            confirmPassword = ""
                                        )
                                    }
                                }
                            } else {
                                if (prefKeys.isWalletActivated()) {
                                    setEffect { Effect.NavigateToHome }
                                } else {
                                    setEvent(Event.ActivateWallet)
                                }
                            }
                        }

                        is SessionStatus.NotAuthenticated -> {
                            setState { copy(isLoading = false) }
                        }

                        else -> {
                            // No-op for Loading and Initial, as onStart handles the initial loading state.
                        }
                    }
                }
        }
    }
} 