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
import eu.europa.ec.businesslogic.model.DeviceInfo
import eu.europa.ec.notificationlogic.controller.PushNotificationController
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.walletactivationlogic.usecase.CreateWalletAttestationUseCase
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

// State: Focused only on activation
data class WalletSetupState(
    val isActivating: Boolean = false,
    val activationError: String? = null,
) : ViewState

// Events: Actions the user can take on this screen
sealed class WalletSetupEvent : ViewEvent {
    data object ActivateWallet : WalletSetupEvent()
    data object Retry : WalletSetupEvent()
    data object SignOut : WalletSetupEvent() // If user wants to cancel
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
    private val logController: LogController
) : MviViewModel<WalletSetupEvent, WalletSetupState, WalletSetupEffect>() {

    override fun setInitialState(): WalletSetupState {
        // EUDI-ARF: Check if wallet is already activated for this user to avoid re-attestation
        if (prefKeys.isWalletActivated()) {
            logController.i("WalletSetupViewModel", ) {"Wallet already activated for this user, navigating to home."}
            // Use a coroutine to navigate after the view is ready
            viewModelScope.launch {
                setEffect { WalletSetupEffect.NavigateToHome }
            }
        }
        return WalletSetupState()
    }

    override fun handleEvents(event: WalletSetupEvent) {
        when (event) {
            is WalletSetupEvent.ActivateWallet -> activateWallet()
            is WalletSetupEvent.Retry -> activateWallet() // Same logic for retry
            is WalletSetupEvent.SignOut -> signOut()
        }
    }

    private fun activateWallet() {
        // Prevent duplicate activations if already in progress
        if (viewState.value.isActivating) {
            logController.w("WalletSetupViewModel") { "Activation already in progress, ignoring." }
            return
        }

        viewModelScope.launch {
            setState { copy(isActivating = true, activationError = null) }
            try {
                val pushToken =
                    pushNotificationController.registerForPushNotifications().getOrThrow()
                val deviceInfo = getCombinedDeviceInfo()

                logController.d("WalletSetupViewModel", "Push Token: $pushToken")
                logController.d("WalletSetupViewModel", "Device Info: $deviceInfo")

                createWalletAttestationUseCase(deviceInfo, pushToken).getOrThrow()
                
                prefKeys.setWalletActivated(true)
                setState { copy(isActivating = false) }
                setEffect { WalletSetupEffect.NavigateToHome }

            } catch (e: Exception) {
                logController.e("WalletSetupViewModel", e)
                val errorMessage = e.message ?: "Wallet activation failed"
                setState { copy(isActivating = false, activationError = errorMessage) }
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
                prefKeys.setWalletActivated(false)
                logController.d("WalletSetupViewModel", "Wallet activation status cleared - EUDI-ARF compliant")
                
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
} 