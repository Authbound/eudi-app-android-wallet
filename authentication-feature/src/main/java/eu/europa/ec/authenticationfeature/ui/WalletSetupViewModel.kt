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
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.businesslogic.controller.device.DeviceController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import eu.europa.ec.businesslogic.model.DeviceInfo
import eu.europa.ec.notificationlogic.controller.PushNotificationController
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.walletactivationlogic.usecase.CreateWalletAttestationUseCase
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

// State: Enhanced with navigation control
data class WalletSetupState(
    val isActivating: Boolean = false,
    val activationError: String? = null,
    val canNavigateBack: Boolean = true,
    val backButtonText: String = "Cancel Setup",
    val showConfirmationDialog: Boolean = false,
) : ViewState

// Events: Actions the user can take on this screen
sealed class WalletSetupEvent : ViewEvent {
    data object ActivateWallet : WalletSetupEvent()
    data object Retry : WalletSetupEvent()
    data object SignOut : WalletSetupEvent() // If user wants to cancel from error state
    data object CancelSetup : WalletSetupEvent() // For back button during loading
    data object BackToLogin : WalletSetupEvent() // For back button during error
    data object ConfirmCancelSetup : WalletSetupEvent() // Confirm cancellation
    data object DismissConfirmationDialog : WalletSetupEvent() // Dismiss confirmation
}

// Effects: Navigation or one-off actions
sealed class WalletSetupEffect : ViewSideEffect {
    data object NavigateToHome : WalletSetupEffect()
    data object NavigateToLogin : WalletSetupEffect()
    data class ShowError(val message: String) : WalletSetupEffect()
}

@KoinViewModel
class WalletSetupViewModel(
    private val createWalletAttestationUseCase: CreateWalletAttestationUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val deviceController: DeviceController,
    private val biometricAuthenticationController: BiometricAuthenticationController,
    private val pushNotificationController: PushNotificationController,
    private val prefKeys: PrefKeys,
    private val prefsController: PrefsController,
    private val logController: LogController
) : MviViewModel<WalletSetupEvent, WalletSetupState, WalletSetupEffect>() {

    override fun setInitialState(): WalletSetupState {
        // EUDI-ARF: Check if wallet is already activated for this user to avoid re-attestation
        try {
            if (prefKeys.isWalletActivatedSafe()) {
                logController.i("WalletSetupViewModel", ) {"Wallet already activated for this user, navigating to home."}
                // Use a coroutine to navigate after the view is ready
                viewModelScope.launch {
                    setEffect { WalletSetupEffect.NavigateToHome }
                }
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
        return WalletSetupState()
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
        }
    }

    private fun activateWallet() {
        // Prevent duplicate activations if already in progress
        if (viewState.value.isActivating) {
            logController.w("WalletSetupViewModel") { "Activation already in progress, ignoring." }
            return
        }

        viewModelScope.launch {
            setState { 
                copy(
                    isActivating = true, 
                    activationError = null,
                    backButtonText = "Cancel Setup",
                    canNavigateBack = true
                ) 
            }
            try {
                val pushToken =
                    pushNotificationController.registerForPushNotifications().getOrThrow()
                val deviceInfo = getCombinedDeviceInfo()

                logController.d("WalletSetupViewModel", "Push Token: $pushToken")
                logController.d("WalletSetupViewModel", "Device Info: $deviceInfo")

                createWalletAttestationUseCase(deviceInfo, pushToken).getOrThrow()
                
                try {
                    prefKeys.setWalletActivated(true)
                } catch (e: SecurityException) {
                    logController.w("WalletSetupViewModel") { 
                        "Failed to save wallet activation status due to security error: ${e.message}" 
                    }
                    // Still proceed to home - the wallet is activated even if we can't save the flag
                }
                
                setState { 
                    copy(
                        isActivating = false,
                        canNavigateBack = false // No back button on success
                    ) 
                }
                setEffect { WalletSetupEffect.NavigateToHome }

            } catch (e: Exception) {
                logController.e("WalletSetupViewModel", e)
                val errorMessage = e.message ?: "Wallet activation failed"
                setState { 
                    copy(
                        isActivating = false, 
                        activationError = errorMessage,
                        backButtonText = "Back to Login",
                        canNavigateBack = true
                    ) 
                }
                setEffect { WalletSetupEffect.ShowError(errorMessage) }
            }
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
                
                signOutUseCase()
                logController.d("WalletSetupViewModel", "User signed out successfully, navigating to login.")
                setEffect { WalletSetupEffect.NavigateToLogin }
            } catch (e: Exception) {
                logController.e("WalletSetupViewModel", e)
                setState { copy(isActivating = false, activationError = e.message) }
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

    private fun performSecureSignOut() {
        viewModelScope.launch {
            try {
                logController.d("WalletSetupViewModel", "Performing secure sign out...")
                setState { copy(isActivating = true, canNavigateBack = false) } // Prevent double-tap
                
                // Clear any partial wallet state - using safe method
                try {
                    if (prefsController.hasCurrentUser()) {
                        prefKeys.setWalletActivated(false)
                        logController.d("WalletSetupViewModel", "Wallet activation status cleared")
                    } else {
                        logController.d("WalletSetupViewModel", "No user context for clearing wallet activation status")
                    }
                } catch (e: SecurityException) {
                    logController.w("WalletSetupViewModel") { 
                        "Failed to clear wallet activation status due to security error: ${e.message}. Proceeding with sign out." 
                    }
                    // Continue with sign out even if we can't clear the preference
                }
                
                // Secure sign out with complete session cleanup
                signOutUseCase()
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