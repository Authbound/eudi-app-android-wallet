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
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
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
    data object NavigateBack : Event()
    data object Initialize : Event()
    data object SignOut : Event()
}

sealed class Effect : ViewSideEffect {
    data object NavigateToHome : Effect()
    data class ShowError(val message: String) : Effect()
    data class ShowInfo(val message: String) : Effect()
    
    sealed class Navigation : Effect() {
        data object NavigateToWalletSetup : Navigation()
        data object PopBackStack : Navigation()
        data class NavigateToLoginAndClearStack(val replaceCurrentScreen: Boolean = true) : Navigation()
        data object SignOutAndNavigateToLogin : Navigation()
    }
}

@KoinViewModel
class AuthenticationViewModel(
    private val signInWithEmailPasswordUseCase: SignInWithEmailPasswordUseCase,
    private val signUpWithEmailPasswordUseCase: SignUpWithEmailPasswordUseCase,
    private val signInWithOAuthUseCase: SignInWithOAuthUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    
    private val prefKeys: PrefKeys,
    private val logController: LogController
) : MviViewModel<Event, State, Effect>() {
    
    private var navigationSource: NavigationSource = NavigationSource.UNKNOWN
    private var isInitialized: Boolean = false
    private var backNavigationInProgress: Boolean = false
    
    override fun setInitialState(): State {
        logController.d("AuthViewModel", "ViewModel created - Instance: ${this.hashCode()}")
        observeAuthState()
        return State()
    }

    override fun handleEvents(event: Event) {
        logController.d("AuthViewModel",  "Event received: $event (Instance: ${this.hashCode()})" )
        
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
            is Event.NavigateBack -> {
                if (!backNavigationInProgress) {
                    handleNavigateBack()
                } else {
                    logController.w("AuthViewModel") { "Back navigation already in progress - ignoring" }
                }
            }
            is Event.Initialize -> {
                if (!isInitialized) {
                    isInitialized = true
                    // If we haven't set a source yet, it means we came directly (e.g., from Splash)
                    if (navigationSource == NavigationSource.UNKNOWN) {
                        navigationSource = NavigationSource.DIRECT
                        logController.d("AuthViewModel", "WalletSetup initialized - Direct navigation detected" )
                    } else {
                        logController.d("AuthViewModel", "WalletSetup initialized - Source already set to: $navigationSource" )
                    }
                } else {
                    logController.d("AuthViewModel", "Initialize called but already initialized - ignoring")
                }
            }
            is Event.SignOut -> signOut()
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

    private fun handleNavigateBack() {
        if (backNavigationInProgress) {
            logController.w("AuthViewModel") { "Back navigation already in progress - skipping" }
            return
        }
        
        backNavigationInProgress = true
        logController.d("AuthViewModel", "Navigate back requested - Source: $navigationSource (Instance: ${this.hashCode()})")
        
        setState { 
            copy(
                error = null
            ) 
        }
        
        when (navigationSource) {
            NavigationSource.FROM_LOGIN -> {
                // Normal flow: Login -> WalletSetup -> Back to Login
                logController.d("AuthViewModel", "Normal back navigation - using PopBackStack")
                setEffect { Effect.Navigation.PopBackStack }
            }
            NavigationSource.DIRECT -> {
                // Direct flow: Splash -> WalletSetup -> User wants to exit
                // Sign out user since they don't want to complete wallet setup
                logController.d("AuthViewModel", "Direct navigation back - signing out user")
                signOutAndNavigateToLogin()
            }
            NavigationSource.UNKNOWN -> {
                // Fallback: User backing out of wallet setup - sign out for clean state
                logController.d("AuthViewModel", "Unknown navigation source - signing out for clear UX")
                signOutAndNavigateToLogin()
            }
        }
        
        // Reset flags immediately - no artificial delays needed
        navigationSource = NavigationSource.UNKNOWN
        isInitialized = false
        backNavigationInProgress = false
    }

    private fun signOut() {
        viewModelScope.launch {
            try {
                logController.d("AuthViewModel", "Signing out user...")
                setState { copy(isLoading = true, error = null) }
                signOutUseCase()
                setState { copy(isLoading = false) }
                logController.d("AuthViewModel", "User signed out successfully")
                // Don't emit navigation effect here - let the auth state observer handle it
            } catch (e: Exception) {
                logController.e("AuthViewModel", e)
                setState { copy(isLoading = false, error = e.message) }
                setEffect { Effect.ShowError(e.message ?: "Sign out failed") }
            }
        }
    }

    private fun signOutAndNavigateToLogin() {
        viewModelScope.launch {
            try {
                logController.d("AuthViewModel", "Signing out user and navigating to login...")
                setState { copy(isLoading = true, error = null) }
                signOutUseCase()
                setState { copy(isLoading = false) }
                logController.d("AuthViewModel", "User signed out successfully, emitting navigation effect")
                setEffect { Effect.Navigation.SignOutAndNavigateToLogin }
            } catch (e: Exception) {
                logController.e("AuthViewModel", e)
                setState { copy(isLoading = false, error = e.message) }
                setEffect { Effect.ShowError(e.message ?: "Sign out failed") }
            }
        }
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
                                setState { copy(isLoading = false) }
                                if (prefKeys.isWalletActivated()) {
                                    logController.d("AuthViewModel", "User authenticated with wallet activated - navigating to home")
                                    setEffect { Effect.NavigateToHome }
                                } else {
                                    logController.d("AuthViewModel", "User authenticated but wallet not activated - navigating to setup.")
                                    setEffect { Effect.Navigation.NavigateToWalletSetup }
                                }
                            }
                        }

                        is SessionStatus.NotAuthenticated -> {
                            setState { 
                                copy(
                                    isLoading = false,
                                    error = null
                                ) 
                            }
                        }

                        is SessionStatus.Initializing -> {
                            // Keep loading state for these transient states
                        }

                        is SessionStatus.RefreshFailure -> {
                            setState { copy(isLoading = false) }
                            setEffect { Effect.ShowError("An unknown error occurred while refreshing the session.") }
                        }
                    }
                }
        }
    }
    
    private enum class NavigationSource {
        FROM_LOGIN,  // Normal flow: Login -> WalletSetup
        DIRECT,      // Direct flow: Splash -> WalletSetup or Auth state change
        UNKNOWN      // Initial/fallback state
    }
} 