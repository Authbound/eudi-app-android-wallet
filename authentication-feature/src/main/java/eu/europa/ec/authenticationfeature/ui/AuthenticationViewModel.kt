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
import eu.europa.ec.authenticationlogic.controller.storage.PinStorageController
import eu.europa.ec.authenticationlogic.model.EmailPasswordRequest
import eu.europa.ec.authenticationlogic.model.OAuthProvider
import eu.europa.ec.authenticationlogic.usecase.ObserveAuthStateUseCase
import eu.europa.ec.authenticationlogic.usecase.SignInWithEmailPasswordUseCase
import eu.europa.ec.authenticationlogic.usecase.SignInWithOAuthUseCase
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.authenticationlogic.usecase.SignUpWithEmailPasswordUseCase
import eu.europa.ec.authenticationlogic.usecase.IsProfileCompletedUseCase
import eu.europa.ec.authenticationlogic.usecase.SignOutMode
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.ExperimentalTime

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
    data object ResetViewModel : Event()
}

sealed class Effect : ViewSideEffect {
    data object NavigateToHome : Effect()
    data class ShowError(val message: String) : Effect()
    data class ShowInfo(val message: String) : Effect()
    
    sealed class Navigation : Effect() {
        data object NavigateToWalletSetup : Navigation()
        data object NavigateToProfileCompletion : Navigation()
        data object NavigateToPinCreate : Navigation()
        data object NavigateToPinVerify : Navigation()
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
    private val isProfileCompletedUseCase: IsProfileCompletedUseCase,
    private val prefKeys: PrefKeysV2,
    private val logController: LogController,
    private val pinStorageController: PinStorageController
) : MviViewModel<Event, State, Effect>() {
    
    private var isInitialized: Boolean = false
    private var backNavigationInProgress: Boolean = false
    private var shouldHandleAuthenticatedNavigation: Boolean = false
    
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
                    logController.d("AuthViewModel", "ViewModel initialized")
                } else {
                    logController.d("AuthViewModel", "Initialize called but already initialized - ignoring")
                }
            }
            is Event.SignOut -> signOut()
            is Event.ResetViewModel -> resetViewModel()
        }
    }

    private fun signInWithEmailPassword() {
        val currentState = viewState.value
        
        logController.d("AuthViewModel", "Sign in requested - Email: ${currentState.email}, ViewModel State: isInitialized=$isInitialized, backNavigationInProgress=$backNavigationInProgress")
        
        // Validate input fields
        if (currentState.email.isBlank()) {
            val message = "Please enter your email address"
            setState { copy(error = message) }
            setEffect { Effect.ShowError(message) }
            logController.w("AuthViewModel", ){"Sign in failed: Email is blank"}
            return
        }
        
        if (currentState.password.isBlank()) {
            val message = "Please enter your password"
            setState { copy(error = message) }
            setEffect { Effect.ShowError(message) }
            logController.w("AuthViewModel", ){"Sign in failed: Password is blank"}
            return
        }
        
        viewModelScope.launch {
            try {
                shouldHandleAuthenticatedNavigation = true
                setState { copy(isLoading = true, error = null) }
                val request = EmailPasswordRequest(currentState.email, currentState.password)
                logController.d("AuthViewModel", "Attempting sign in with email: ${request.email} (Instance: ${this@AuthenticationViewModel.hashCode()})")
                signInWithEmailPasswordUseCase(request)
                logController.d("AuthViewModel", "Sign in use case completed, waiting for auth state observer...")
                // Note: Don't set loading to false here - let the auth state observer handle it
            } catch (e: Exception) {
                logController.e("AuthViewModel", ) {"Sign in failed with exception: ${e.message}"}
                shouldHandleAuthenticatedNavigation = false
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
                shouldHandleAuthenticatedNavigation = true
                setState { copy(isLoading = true, error = null) }
                val request = EmailPasswordRequest(viewState.value.email, viewState.value.password)
                signUpWithEmailPasswordUseCase(request)
                setEffect { Effect.ShowInfo("Account created. Setting up your profile...") }
                // Keep loading = true - auth observer will navigate and dismiss loading
                // Only clear form fields, don't reset isSignUpMode to avoid flash
                setState {
                    copy(
                        email = "",
                        password = "",
                        confirmPassword = ""
                    )
                }
            } catch (e: Exception) {
                shouldHandleAuthenticatedNavigation = false
                setState { copy(isLoading = false, error = e.message) }
                setEffect { Effect.ShowError(e.message ?: "An unknown error occurred") }
            }
        }
    }

    private fun signInWithOAuth(provider: OAuthProvider, context: Context) {
        viewModelScope.launch {
            try {
                shouldHandleAuthenticatedNavigation = true
                setState { copy(isLoading = true, error = null) }
                signInWithOAuthUseCase(provider, context)
            } catch (e: Exception) {
                shouldHandleAuthenticatedNavigation = false
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
        logController.d("AuthViewModel", "Navigate back requested (Instance: ${this.hashCode()})")

        setState { copy(error = null) }

        // Back from WalletSetup means user doesn't want to complete setup - sign out
        logController.d("AuthViewModel", "Back navigation - signing out for clear UX")
        signOutAndNavigateToLogin()

        // Reset flags immediately - no artificial delays needed
        isInitialized = false
        backNavigationInProgress = false
    }

    private fun signOut() {
        viewModelScope.launch {
            try {
                logController.d("AuthViewModel", "Signing out user...")
                setState { copy(isLoading = true, error = null) }
                signOutUseCase(SignOutMode.Soft)
                shouldHandleAuthenticatedNavigation = false
                resetViewModel() // Reset ViewModel state after successful sign out
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
                signOutUseCase(SignOutMode.Soft)
                shouldHandleAuthenticatedNavigation = false
                resetViewModel() // Reset ViewModel state after successful sign out
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

    @OptIn(ExperimentalTime::class)
    private fun observeAuthState() {
        viewModelScope.launch {
            observeAuthStateUseCase()
                .catch {
                    setState { copy(isLoading = false, error = it.message) }
                    setEffect { Effect.ShowError(it.message ?: "An unknown error occurred") }
                }
                .collect { status ->
                    logController.d("AuthViewModel", "Auth state changed: ${status::class.simpleName} (Instance: ${this@AuthenticationViewModel.hashCode()})")
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            val user = status.session.user
                            logController.d("AuthViewModel", "User authenticated: ${user?.id?.take(8)}...")

                            if (user === null) {
                                logController.e("AuthViewModel" ){"Authenticated but user is null"}
                                setState { copy(isLoading = false) }
                                setEffect { Effect.ShowError("User is null") }
                                shouldHandleAuthenticatedNavigation = false
                                return@collect
                            }

                            // V2: User context is automatically derived from Supabase session
                            logController.d("AuthViewModel", "User authenticated: ${user.id.take(8)}... (context auto-managed)")

                            if (!shouldHandleAuthenticatedNavigation) {
                                // User is already authenticated but didn't initiate this auth flow
                                // SplashInteractor handles routing for pre-authenticated users
                                setState { copy(isLoading = false) }
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
                                shouldHandleAuthenticatedNavigation = false
                            } else {
                                // Keep loading visible until navigation is determined
                                // to prevent the login form from flashing during async checks
                                // EUDI-ARF: Wallet is device-bound, check local activation status
                                try {
                                    if (prefKeys.isWalletActivatedSafe()) {
                                        val hasPin = try {
                                            pinStorageController.retrievePin().isNotBlank()
                                        } catch (e: Exception) {
                                            logController.w("AuthViewModel") { "Failed to check PIN status: ${e.message}" }
                                            false
                                        }
                                        if (hasPin) {
                                            logController.d("AuthViewModel", "User authenticated with wallet + PIN - navigating to PIN verify")
                                            setEffect { Effect.Navigation.NavigateToPinVerify }
                                        } else {
                                            logController.d("AuthViewModel", "User authenticated with wallet but no PIN - navigating to PIN create")
                                            setEffect { Effect.Navigation.NavigateToPinCreate }
                                        }
                                    } else {
                                        // Check if profile is complete
                                        val isProfileCompleted = isProfileCompletedUseCase()
                                        if (isProfileCompleted) {
                                            logController.d("AuthViewModel", "User authenticated but wallet not activated - navigating to setup")
                                            setEffect { Effect.Navigation.NavigateToWalletSetup }
                                        } else {
                                            logController.d("AuthViewModel", "User authenticated but profile not complete - navigating to profile completion")
                                            setEffect { Effect.Navigation.NavigateToProfileCompletion }
                                        }
                                    }
                                } catch (e: SecurityException) {
                                    logController.w("AuthViewModel") {
                                        "Security error checking wallet activation: ${e.message}. Navigating to wallet setup."
                                    }
                                    setEffect { Effect.Navigation.NavigateToWalletSetup }
                                } catch (e: Exception) {
                                    logController.e("AuthViewModel", e)
                                    setState { copy(isLoading = false) }
                                    setEffect { Effect.ShowError("Navigation error. Please restart the app.") }
                                }
                                shouldHandleAuthenticatedNavigation = false
                            }
                        }

                        is SessionStatus.NotAuthenticated -> {
                            logController.d("AuthViewModel", "Session status: NotAuthenticated (Instance: ${this@AuthenticationViewModel.hashCode()})")

                            // Check if this NotAuthenticated occurred during a user-initiated auth attempt
                            // This indicates the auth was canceled (e.g., user closed OAuth browser) or failed
                            val wasAuthAttemptInProgress = shouldHandleAuthenticatedNavigation

                            // Always reset flags and loading state to ensure UI doesn't get stuck
                            shouldHandleAuthenticatedNavigation = false

                            if (wasAuthAttemptInProgress) {
                                logController.w("AuthViewModel") {
                                    "Authentication attempt ended without success - auth may have been canceled or failed"
                                }
                                setState {
                                    copy(
                                        isLoading = false,
                                        error = "Authentication was canceled or failed. Please try again."
                                    )
                                }
                                setEffect { Effect.ShowError("Authentication was canceled or failed. Please try again.") }
                            } else {
                                // Normal NotAuthenticated state (no auth attempt was in progress)
                                // V2: User context is automatically cleared when session ends
                                logController.d("AuthViewModel", "State updated to NotAuthenticated (context auto-cleared)")
                                setState {
                                    copy(
                                        isLoading = false,
                                        error = null
                                    )
                                }
                            }
                        }

                        is SessionStatus.Initializing -> {
                            logController.d("AuthViewModel", "Session status: Initializing (Instance: ${this@AuthenticationViewModel.hashCode()})")
                            setState { copy(isLoading = true) }
                        }

                        is SessionStatus.RefreshFailure -> {
                            logController.e("AuthViewModel" ){"Session refresh failed"}
                            setState { copy(isLoading = false) }
                            setEffect { Effect.ShowError("An unknown error occurred while refreshing the session.") }
                            shouldHandleAuthenticatedNavigation = false
                        }
                    }
                }
        }
    }
    
    private fun resetViewModel() {
        logController.d("AuthViewModel", "Resetting ViewModel state (Instance: ${this.hashCode()})")
        isInitialized = false
        backNavigationInProgress = false
        shouldHandleAuthenticatedNavigation = false
        setState {
            copy(
                email = "",
                password = "",
                confirmPassword = "",
                isSignUpMode = false,
                isLoading = false,
                error = null
            )
        }
        logController.d("AuthViewModel", "ViewModel state reset completed")
    }
} 
