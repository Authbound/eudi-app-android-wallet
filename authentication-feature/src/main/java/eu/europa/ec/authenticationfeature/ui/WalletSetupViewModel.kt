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

import androidx.lifecycle.viewModelScope
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricAuthenticationController
import eu.europa.ec.businesslogic.model.error.WalletActivationError
import eu.europa.ec.businesslogic.model.error.getErrorTitle
import eu.europa.ec.businesslogic.model.error.getRetryDelaySeconds
import eu.europa.ec.businesslogic.model.error.getUserFriendlyMessage
import eu.europa.ec.businesslogic.model.error.isPermanent
import eu.europa.ec.businesslogic.model.error.isRetryable
import eu.europa.ec.businesslogic.model.error.shouldAutoRetry
import eu.europa.ec.businesslogic.model.error.toWalletActivationError
import eu.europa.ec.authenticationlogic.usecase.SignOutMode
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.businesslogic.controller.device.DeviceController
import eu.europa.ec.businesslogic.controller.device.DeviceSecurityState
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.businesslogic.model.DeviceInfo
import eu.europa.ec.notificationlogic.controller.PushNotificationController
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.walletactivationlogic.usecase.CreateWalletAttestationUseCase
import eu.europa.ec.walletactivationlogic.usecase.DeleteWalletActivationUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

/**
 * Represents the current step in the wallet activation process.
 */
enum class ActivationStep {
    IDLE,
    FETCHING_CHALLENGE,
    GENERATING_KEYS,
    ACTIVATING_WALLET
}

/**
 * State for the wallet setup screen with enhanced error handling.
 */
data class WalletSetupState(
    val isActivating: Boolean = false,
    val activationError: WalletActivationError? = null,
    val canNavigateBack: Boolean = true,
    val backButtonText: String = "Cancel Setup",
    val showConfirmationDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val showDeleteConfirmationDialog: Boolean = false,
    val isWalletAlreadyActivated: Boolean = false,
    // Enhanced error UX fields
    val currentStep: ActivationStep = ActivationStep.IDLE,
    val retryCountdown: Int? = null,
    val autoRetrying: Boolean = false,
    val autoRetryAttempt: Int = 0,
) : ViewState {
    companion object {
        const val MAX_AUTO_RETRY_ATTEMPTS = 3
    }
}

// Events: Actions the user can take on this screen
sealed class WalletSetupEvent : ViewEvent {
    data object ActivateWallet : WalletSetupEvent()
    data object Retry : WalletSetupEvent()
    data object SignOut : WalletSetupEvent() // If user wants to cancel from error state
    data object CancelSetup : WalletSetupEvent() // For back button during loading
    data object BackToLogin : WalletSetupEvent() // For back button during error
    data object ConfirmCancelSetup : WalletSetupEvent() // Confirm cancellation
    data object DismissConfirmationDialog : WalletSetupEvent() // Dismiss confirmation
    data object DeleteWallet : WalletSetupEvent() // Delete wallet activation
    data object ConfirmDeleteWallet : WalletSetupEvent() // Confirm wallet deletion
    data object DismissDeleteConfirmationDialog : WalletSetupEvent() // Dismiss delete confirmation
}

// Effects: Navigation or one-off actions
sealed class WalletSetupEffect : ViewSideEffect {
    data object NavigateToHome : WalletSetupEffect()
    data object NavigateToLogin : WalletSetupEffect()
    data object NavigateToDeviceSecurity : WalletSetupEffect()
    data class ShowError(val message: String) : WalletSetupEffect()
}

@KoinViewModel
class WalletSetupViewModel(
    private val createWalletAttestationUseCase: CreateWalletAttestationUseCase,
    private val deleteWalletActivationUseCase: DeleteWalletActivationUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val deviceController: DeviceController,
    private val biometricAuthenticationController: BiometricAuthenticationController,
    private val pushNotificationController: PushNotificationController,
    private val prefKeys: PrefKeysV2,
    private val prefsController: PrefsControllerV2,
    private val logController: LogController
) : MviViewModel<WalletSetupEvent, WalletSetupState, WalletSetupEffect>() {

    private var countdownJob: Job? = null

    override fun setInitialState(): WalletSetupState {
        // EUDI-ARF: Check if wallet is already activated for this user to avoid re-attestation
        try {
            if (prefKeys.isWalletActivatedSafe()) {
                logController.i("WalletSetupViewModel", ) {"Wallet already activated for this user, navigating to home."}
                // Use a coroutine to navigate after the view is ready
                viewModelScope.launch {
                    setEffect { WalletSetupEffect.NavigateToHome }
                }
                return WalletSetupState(isWalletAlreadyActivated = true)
            }
        } catch (e: SecurityException) {
            logController.w("WalletSetupViewModel") { 
                "Security error accessing wallet activation status: ${e.message}. Proceeding with setup." 
            }
            // Continue with normal setup flow if we can't access preferences
        } catch (e: Exception) {
            logController.e("WalletSetupViewModel", e)
            // Continue with normal setup flow for any other error
        }
        return WalletSetupState(isWalletAlreadyActivated = false)
    }

    override fun handleEvents(event: WalletSetupEvent) {
        when (event) {
            is WalletSetupEvent.ActivateWallet -> activateWallet()
            is WalletSetupEvent.Retry -> activateWallet() // Same logic for retry
            is WalletSetupEvent.SignOut -> signOut()
            is WalletSetupEvent.CancelSetup -> handleCancelSetup()
            is WalletSetupEvent.BackToLogin -> handleBackToLogin()
            is WalletSetupEvent.ConfirmCancelSetup -> confirmCancelSetup()
            is WalletSetupEvent.DismissConfirmationDialog -> dismissConfirmationDialog()
            is WalletSetupEvent.DeleteWallet -> handleDeleteWallet()
            is WalletSetupEvent.ConfirmDeleteWallet -> confirmDeleteWallet()
            is WalletSetupEvent.DismissDeleteConfirmationDialog -> dismissDeleteConfirmationDialog()
        }
    }

    private fun activateWallet(isAutoRetry: Boolean = false) {
        // Prevent duplicate activations if already in progress or already activated
        val currentState = viewState.value
        if (currentState.isActivating && !isAutoRetry) {
            logController.w("WalletSetupViewModel") { "Activation already in progress, ignoring." }
            return
        }

        // Cancel any running countdown
        countdownJob?.cancel()

        // Double-check wallet activation status before proceeding
        try {
            if (prefKeys.isWalletActivatedSafe()) {
                logController.i("WalletSetupViewModel") { "Wallet already activated, navigating to home instead." }
                viewModelScope.launch {
                    setEffect { WalletSetupEffect.NavigateToHome }
                }
                return
            }
        } catch (e: Exception) {
            logController.w("WalletSetupViewModel") { "Failed to check wallet status: ${e.message}. Proceeding with activation." }
        }

        viewModelScope.launch {
            if (!ensureDeviceSecurityReady()) {
                return@launch
            }
            setState {
                copy(
                    isActivating = true,
                    activationError = null,
                    backButtonText = "Cancel Setup",
                    canNavigateBack = true,
                    currentStep = ActivationStep.FETCHING_CHALLENGE,
                    retryCountdown = null,
                    autoRetrying = isAutoRetry
                )
            }
            try {
                val pushToken =
                    pushNotificationController.registerForPushNotifications().getOrThrow()
                val deviceInfo = getCombinedDeviceInfo()

                logController.d("WalletSetupViewModel", "Push Token: $pushToken")
                logController.d("WalletSetupViewModel", "Device Info: $deviceInfo")

                // Step 2-4: Create wallet attestation (challenge → keys → activate)
                setState { copy(currentStep = ActivationStep.GENERATING_KEYS) }
                createWalletAttestationUseCase(deviceInfo, pushToken).getOrThrow()

                try {
                    prefKeys.setWalletActivated(true)
                } catch (e: SecurityException) {
                    logController.w("WalletSetupViewModel") {
                        "Failed to save wallet activation status due to security error: ${e.message}"
                    }
                }

                setState {
                    copy(
                        isActivating = false,
                        canNavigateBack = false,
                        currentStep = ActivationStep.IDLE,
                        autoRetrying = false,
                        autoRetryAttempt = 0
                    )
                }
                setEffect { WalletSetupEffect.NavigateToHome }

            } catch (e: Exception) {
                logController.e("WalletSetupViewModel", e)
                handleActivationError(e)
            }
        }
    }

    private suspend fun handleActivationError(exception: Exception) {
        val error = if (exception is WalletActivationError) {
            exception
        } else {
            exception.toWalletActivationError()
        }

        val errorMessage = exception.message ?: "Wallet activation failed"

        // Check if this is a "wallet already exists" error from backend
        val isAlreadyExistsError = errorMessage.contains("already exists", ignoreCase = true) ||
                errorMessage.contains("already activated", ignoreCase = true) ||
                errorMessage.contains("duplicate", ignoreCase = true) ||
                errorMessage.contains("409", ignoreCase = true)

        when {
            isAlreadyExistsError -> {
                logController.i("WalletSetupViewModel") {
                    "Wallet already exists on backend, marking as activated and navigating to home."
                }
                try {
                    prefKeys.setWalletActivated(true)
                } catch (securityException: SecurityException) {
                    logController.w("WalletSetupViewModel") {
                        "Failed to save wallet activation status: ${securityException.message}"
                    }
                }
                setState {
                    copy(
                        isActivating = false,
                        canNavigateBack = false,
                        autoRetrying = false
                    )
                }
                setEffect { WalletSetupEffect.NavigateToHome }
            }

            // Auto-retry for transient errors (expired/not found challenges)
            error.shouldAutoRetry() && viewState.value.autoRetryAttempt < WalletSetupState.MAX_AUTO_RETRY_ATTEMPTS -> {
                val attempt = viewState.value.autoRetryAttempt + 1
                logController.i("WalletSetupViewModel") {
                    "Auto-retrying activation (attempt $attempt of ${WalletSetupState.MAX_AUTO_RETRY_ATTEMPTS})"
                }
                setState {
                    copy(
                        autoRetryAttempt = attempt,
                        autoRetrying = true
                    )
                }
                // Small delay before auto-retry
                viewModelScope.launch {
                    delay(500)
                    activateWallet(isAutoRetry = true)
                }
            }

            // Rate limited - start countdown
            error is WalletActivationError.ChallengeRateLimited -> {
                startRateLimitCountdown(error.retryAfterSeconds)
                setState {
                    copy(
                        isActivating = false,
                        activationError = error,
                        backButtonText = "Cancel Setup",
                        canNavigateBack = true,
                        currentStep = ActivationStep.IDLE,
                        autoRetrying = false
                    )
                }
            }

            // Other errors - show error UI
            else -> {
                setState {
                    copy(
                        isActivating = false,
                        activationError = error,
                        backButtonText = "Back to Login",
                        canNavigateBack = true,
                        currentStep = ActivationStep.IDLE,
                        autoRetrying = false,
                        autoRetryAttempt = 0
                    )
                }
                setEffect { WalletSetupEffect.ShowError(error.getUserFriendlyMessage()) }
            }
        }
    }

    private fun startRateLimitCountdown(seconds: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                setState { copy(retryCountdown = remaining) }
                delay(1000)
                remaining--
            }
            setState { copy(retryCountdown = null) }
            // Auto-retry after countdown completes
            logController.i("WalletSetupViewModel") { "Rate limit countdown complete, auto-retrying" }
            activateWallet(isAutoRetry = true)
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            try {
                logController.d("WalletSetupViewModel", "Signing out user...")
                setState { copy(isActivating = true) } // Show loading indicator
                
                // Clear wallet activation status - EUDI-ARF compliant approach
                // Wallet is device-bound and should be re-established each session
                try {
                    prefKeys.setWalletActivated(false)
                    logController.d("WalletSetupViewModel", "Wallet activation status cleared - EUDI-ARF compliant")
                } catch (e: SecurityException) {
                    logController.w("WalletSetupViewModel") { 
                        "Failed to clear wallet activation status due to security error: ${e.message}. Proceeding with sign out." 
                    }
                    // Continue with sign out even if we can't clear the preference
                }
                
                signOutUseCase(SignOutMode.Soft)
                logController.d("WalletSetupViewModel", "User signed out successfully, navigating to login.")
                setEffect { WalletSetupEffect.NavigateToLogin }
            } catch (e: Exception) {
                logController.e("WalletSetupViewModel", e)
                val signOutError = e.toWalletActivationError()
                setState { copy(isActivating = false, activationError = signOutError) }
                setEffect { WalletSetupEffect.ShowError(e.message ?: "Sign out failed") }
            }
        }
    }

    private fun getCombinedDeviceInfo(): DeviceInfo {
        val basicDeviceInfo = deviceController.getDeviceInfo()
        val hasBiometricHardware = biometricAuthenticationController.hasBiometricHardware()
        return basicDeviceInfo.copy(
            hasBiometricHardware = hasBiometricHardware
        )
    }

    private suspend fun ensureDeviceSecurityReady(): Boolean {
        val securityState: DeviceSecurityState = deviceController.getDeviceSecurityState()
        if (securityState.isReady) {
            return true
        }
        logController.w("WalletSetupViewModel") { "Device security missing, redirecting to setup" }
        setState {
            copy(
                isActivating = false,
                activationError = null,
                currentStep = ActivationStep.IDLE,
                autoRetrying = false,
                autoRetryAttempt = 0
            )
        }
        setEffect { WalletSetupEffect.NavigateToDeviceSecurity }
        return false
    }

    private fun handleCancelSetup() {
        logController.d("WalletSetupViewModel", "Cancel setup requested")
        
        // Show confirmation dialog only if wallet activation is in progress
        if (viewState.value.isActivating) {
            setState { copy(showConfirmationDialog = true) }
        } else {
            // If not activating, go directly to login (no confirmation needed)
            handleBackToLogin()
        }
    }

    private fun handleBackToLogin() {
        logController.d("WalletSetupViewModel", "Back to login requested")
        performSecureSignOut()
    }

    private fun confirmCancelSetup() {
        logController.d("WalletSetupViewModel", "Cancel setup confirmed")
        setState { copy(showConfirmationDialog = false) }
        performSecureSignOut()
    }

    private fun dismissConfirmationDialog() {
        logController.d("WalletSetupViewModel", "Confirmation dialog dismissed")
        setState { copy(showConfirmationDialog = false) }
    }

    private fun handleDeleteWallet() {
        logController.d("WalletSetupViewModel", "Delete wallet requested")
        setState { copy(showDeleteConfirmationDialog = true) }
    }

    private fun confirmDeleteWallet() {
        logController.d("WalletSetupViewModel", "Delete wallet confirmed")
        setState { copy(showDeleteConfirmationDialog = false) }
        deleteWalletActivation()
    }

    private fun dismissDeleteConfirmationDialog() {
        logController.d("WalletSetupViewModel", "Delete confirmation dialog dismissed")
        setState { copy(showDeleteConfirmationDialog = false) }
    }

    private fun deleteWalletActivation() {
        viewModelScope.launch {
            setState { 
                copy(
                    isDeleting = true, 
                    canNavigateBack = false
                ) 
            }
            try {
                logController.d("WalletSetupViewModel", "Deleting wallet activation...")
                
                deleteWalletActivationUseCase().getOrThrow()
                
                logController.d("WalletSetupViewModel", "Wallet activation deleted successfully")
                // Don't navigate manually - let the authentication state observer handle navigation
                // after the user is signed out by the DeleteWalletActivationUseCase
                setState { 
                    copy(
                        isDeleting = false,
                        activationError = null,
                        canNavigateBack = true
                    ) 
                }
                // Note: Navigation will be handled by AuthenticationViewModel.observeAuthState() 
                // when it detects SessionStatus.NotAuthenticated after SignOutUseCase completes
                
            } catch (e: Exception) {
                logController.e("WalletSetupViewModel", e)
                val deleteError = e.toWalletActivationError()
                setState {
                    copy(
                        isDeleting = false,
                        activationError = deleteError,
                        canNavigateBack = true
                    )
                }
                setEffect { WalletSetupEffect.ShowError(deleteError.getUserFriendlyMessage()) }
            }
        }
    }

    private fun performSecureSignOut() {
        viewModelScope.launch {
            try {
                logController.d("WalletSetupViewModel", "Performing secure sign out...")
                setState { copy(isActivating = true, canNavigateBack = false) } // Prevent double-tap
                
                // Clear any partial wallet state
                // In V2: PrefsControllerV2 auto-derives user context from Supabase session
                // If no session exists, this will throw SecurityException (caught below)
                try {
                    prefKeys.setWalletActivated(false)
                    logController.d("WalletSetupViewModel", "Wallet activation status cleared")
                } catch (e: SecurityException) {
                    logController.w("WalletSetupViewModel") { 
                        "No authenticated session to clear wallet activation status: ${e.message}. Proceeding with sign out." 
                    }
                    // Continue with sign out even if we can't clear the preference
                }
                
                // Secure sign out with complete session cleanup
                signOutUseCase(SignOutMode.Soft)
                logController.d("WalletSetupViewModel", "Secure sign out completed, navigating to login.")
                setEffect { WalletSetupEffect.NavigateToLogin }
                
            } catch (e: Exception) {
                logController.e("WalletSetupViewModel", e)
                // Even if cleanup fails, force navigation to login for security
                logController.w("WalletSetupViewModel") { "Sign out failed, but forcing navigation to login for security" }
                setEffect { WalletSetupEffect.NavigateToLogin }
            } finally {
                setState { copy(isActivating = false, canNavigateBack = true) }
            }
        }
    }
} 