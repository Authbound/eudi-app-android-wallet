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
import eu.europa.ec.networklogic.model.request.CompleteProfileRequest
import eu.europa.ec.authenticationlogic.usecase.CheckHandleAvailabilityUseCase
import eu.europa.ec.authenticationlogic.usecase.CompleteProfileUseCase
import eu.europa.ec.authenticationlogic.usecase.GetCurrentUserUseCase
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.device.DeviceController
import eu.europa.ec.walletactivationlogic.usecase.CreateWalletAttestationUseCase
import eu.europa.ec.notificationlogic.controller.UserScopedPushNotificationController
import eu.europa.ec.businesslogic.model.error.WalletActivationError
import eu.europa.ec.businesslogic.model.error.toWalletActivationError
import eu.europa.ec.businesslogic.model.error.getUserFriendlyMessage
import eu.europa.ec.businesslogic.model.error.isRetryable
import eu.europa.ec.authenticationlogic.usecase.SignOutMode
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.koin.android.annotation.KoinViewModel
import kotlin.math.pow

data class ProfileCompletionState(
    val handle: String = "",
    val displayName: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isHandleAvailable: Boolean? = null,
    val handleValidationJob: Job? = null,
    val isCreatingWUA: Boolean = false,
    val wuaCreationStep: String = "",
    val lastError: WalletActivationError? = null,
    val canRetry: Boolean = false,
    val retryCount: Int = 0
) : ViewState

sealed class ProfileCompletionEvent : ViewEvent {
    data class OnHandleChanged(val handle: String) : ProfileCompletionEvent()
    data class OnDisplayNameChanged(val displayName: String) : ProfileCompletionEvent()
    data object CompleteProfile : ProfileCompletionEvent()
    data object RetryWalletActivation : ProfileCompletionEvent()
    data object SignOut : ProfileCompletionEvent()
}

sealed class ProfileCompletionEffect : ViewSideEffect {
    data object NavigateToWalletSetup : ProfileCompletionEffect()
    data object NavigateToLogin : ProfileCompletionEffect()
    data class ShowError(val message: String) : ProfileCompletionEffect()
}

@KoinViewModel
class ProfileCompletionViewModel(
    private val completeProfileUseCase: CompleteProfileUseCase,
    private val checkHandleAvailabilityUseCase: CheckHandleAvailabilityUseCase,
    private val createWalletAttestationUseCase: CreateWalletAttestationUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val userScopedPushNotificationController: UserScopedPushNotificationController,
    private val deviceController: DeviceController,
    private val signOutUseCase: SignOutUseCase,
    private val logController: LogController
) : MviViewModel<ProfileCompletionEvent, ProfileCompletionState, ProfileCompletionEffect>() {

    override fun setInitialState() = ProfileCompletionState()

    override fun handleEvents(event: ProfileCompletionEvent) {
        when (event) {
            is ProfileCompletionEvent.OnHandleChanged -> {
                val handle = event.handle.filter { it.isLetterOrDigit() }
                setState { copy(handle = handle, isHandleAvailable = null) }
                viewState.value.handleValidationJob?.cancel()
                val job = viewModelScope.launch {
                    delay(300) // Debounce
                    checkHandleAvailability(handle)
                }
                setState { copy(handleValidationJob = job) }
            }
            is ProfileCompletionEvent.OnDisplayNameChanged -> {
                setState { copy(displayName = event.displayName) }
            }
            is ProfileCompletionEvent.CompleteProfile -> {
                completeProfile()
            }
            is ProfileCompletionEvent.RetryWalletActivation -> {
                retryWalletActivation()
            }
            is ProfileCompletionEvent.SignOut -> {
                signOut()
            }
        }
    }

    private fun checkHandleAvailability(handle: String) {
        if (handle.length < 3) {
            setState { copy(isHandleAvailable = false) }
            return
        }
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            checkHandleAvailabilityUseCase(handle)
                .onSuccess { isAvailable ->
                    setState { copy(isLoading = false, isHandleAvailable = isAvailable) }
                }
                .onFailure {
                    setState { copy(isLoading = false, error = it.message) }
                }
        }
    }

    private fun completeProfile() {
        val state = viewState.value
        if (state.handle.isBlank() || state.displayName.isBlank() || state.isHandleAvailable != true) {
            setEffect { ProfileCompletionEffect.ShowError("Please fill all fields and ensure handle is available.") }
            return
        }

        viewModelScope.launch {
            setState { copy(isLoading = true, wuaCreationStep = "Completing profile...") }
            
            // Step 1: Complete profile
            val request = CompleteProfileRequest(state.handle, state.displayName)
            completeProfileUseCase(request)
                .onSuccess {
                    logController.d("ProfileCompletion", "Profile completed successfully")
                    setState { copy(wuaCreationStep = "Creating wallet attestation...") }
                    createWalletAttestation()
                }
                .onFailure { error ->
                    logController.e("ProfileCompletion", error)
                    val walletError = error.toWalletActivationError()
                    setState { 
                        copy(
                            isLoading = false, 
                            isCreatingWUA = false, 
                            error = error.message,
                            lastError = walletError,
                            canRetry = false // Profile completion errors are not retryable
                        ) 
                    }
                    setEffect { ProfileCompletionEffect.ShowError(walletError.getUserFriendlyMessage()) }
                }
        }
    }

    private fun retryWalletActivation() {
        val currentState = viewState.value
        if (!currentState.canRetry || currentState.retryCount >= 3) {
            setEffect { ProfileCompletionEffect.ShowError("Maximum retry attempts reached. Please try again later.") }
            return
        }
        
        logController.d("ProfileCompletion", "Retrying wallet activation (attempt ${currentState.retryCount + 1})")
        setState { 
            copy(
                lastError = null, 
                canRetry = false, 
                retryCount = currentState.retryCount + 1,
                error = null
            ) 
        }
        
        viewModelScope.launch {
            // Exponential backoff: 1s, 2s, 4s for retries 1, 2, 3
            val backoffSeconds = 2.0.pow(currentState.retryCount.toDouble()).toLong()
            logController.d("ProfileCompletion", "Applying exponential backoff: ${backoffSeconds}s")
            
            setState { copy(wuaCreationStep = "Retrying in ${backoffSeconds}s...") }
            delay(backoffSeconds * 1000)
            
            createWalletAttestation()
        }
    }

    private suspend fun createWalletAttestation() {
        try {
            setState { copy(isCreatingWUA = true, wuaCreationStep = "Getting user information...") }
            
            // Get current user with timeout - returns UserInfo? directly
            val currentUser = withTimeout(10_000) { // 10 second timeout
                getCurrentUserUseCase()
            }
            
            if (currentUser != null) {
                val userId = currentUser.id
                logController.d("ProfileCompletion", "Got current user: ${userId.take(8)}...")
                
                setState { copy(wuaCreationStep = "Gathering device information...") }
                
                // Get device information with timeout - returns DeviceInfo directly
                val deviceInfo = withTimeout(5_000) { // 5 second timeout
                    deviceController.getDeviceInfo()
                }
                
                setState { copy(wuaCreationStep = "Registering for notifications...") }
                
                // Register for user-scoped push notifications with timeout
                val pushTokenResult = withTimeout(15_000) { // 15 second timeout for FCM
                    userScopedPushNotificationController.registerForPushNotifications(userId)
                }
                
                pushTokenResult
                    .onSuccess { pushToken ->
                        logController.d("ProfileCompletion", "Push token obtained: ${pushToken.take(16)}...")
                        setState { copy(wuaCreationStep = "Creating wallet unit attestation...") }
                        
                        // Create WUA with device info and push token with timeout
                        val wuaResult = withTimeout(30_000) { // 30 second timeout for WUA creation
                            createWalletAttestationUseCase(deviceInfo, pushToken)
                        }
                        
                        wuaResult
                            .onSuccess { wuaResponse ->
                                logController.d("ProfileCompletion", "WUA created successfully")
                                setState { 
                                    copy(
                                        isLoading = false, 
                                        isCreatingWUA = false, 
                                        wuaCreationStep = "Wallet activated successfully!"
                                    ) 
                                }
                                setEffect { ProfileCompletionEffect.NavigateToWalletSetup }
                            }
                            .onFailure { error ->
                                logController.e("ProfileCompletion", error)
                                val walletError = error.toWalletActivationError()
                                handleWalletActivationError(walletError, "Failed to create wallet attestation")
                            }
                    }
                    .onFailure { error ->
                        logController.e("ProfileCompletion", error)
                        val walletError = WalletActivationError.NotificationRegistrationFailure(error)
                        handleWalletActivationError(walletError, "Failed to register for notifications")
                    }
            } else {
                logController.e("ProfileCompletion") { "Failed to get current user - user is null" }
                val walletError = WalletActivationError.AuthenticationFailure("Failed to get current user")
                handleWalletActivationError(walletError, "Failed to get user information")
            }
                
        } catch (e: TimeoutCancellationException) {
            logController.e("ProfileCompletion") { "Wallet activation timed out" }
            val walletError = WalletActivationError.TimeoutError("wallet activation", 30)
            handleWalletActivationError(walletError, "Wallet activation timed out")
        } catch (e: Exception) {
            logController.e("ProfileCompletion", e)
            val walletError = WalletActivationError.UnexpectedError(e)
            handleWalletActivationError(walletError, "An unexpected error occurred")
        }
    }

    private fun handleWalletActivationError(error: WalletActivationError, step: String) {
        val currentRetryCount = viewState.value.retryCount
        
        // Check if this is a "wallet already exists" error from backend
        val errorMessage = error.message ?: ""
        val isAlreadyExistsError = errorMessage.contains("already exists", ignoreCase = true) ||
                errorMessage.contains("already activated", ignoreCase = true) ||
                errorMessage.contains("duplicate", ignoreCase = true) ||
                (error is WalletActivationError.ServerRejection && error.httpCode == 409)
        
        if (isAlreadyExistsError) {
            logController.i("ProfileCompletion") { "Wallet already exists on backend, treating as successful activation." }
            setState { 
                copy(
                    isLoading = false, 
                    isCreatingWUA = false, 
                    wuaCreationStep = "Wallet already activated!"
                ) 
            }
            setEffect { ProfileCompletionEffect.NavigateToWalletSetup }
            return
        }
        
        // Different retry limits for different error types
        val maxRetries = when (error) {
            is WalletActivationError.NetworkFailure -> 3
            is WalletActivationError.TimeoutError -> 2
            is WalletActivationError.NotificationRegistrationFailure -> 2
            is WalletActivationError.ServerRejection -> if (error.httpCode in 500..599) 2 else 0
            else -> 0 // Non-retryable errors
        }
        
        val isRetryable = error.isRetryable() && currentRetryCount < maxRetries
        
        logController.w("ProfileCompletion") { 
            "Wallet activation error: ${error.message} (attempt ${currentRetryCount + 1}/${maxRetries + 1})" 
        }
        
        setState { 
            copy(
                isLoading = false, 
                isCreatingWUA = false, 
                error = error.message,
                wuaCreationStep = step,
                lastError = error,
                canRetry = isRetryable
            ) 
        }
        
        if (isRetryable) {
            setEffect { ProfileCompletionEffect.ShowError("${error.getUserFriendlyMessage()} (Attempt ${currentRetryCount + 1}/${maxRetries + 1})") }
        } else {
            setEffect { ProfileCompletionEffect.ShowError(error.getUserFriendlyMessage()) }
        }
    }
    
    private fun signOut() {
        viewModelScope.launch {
            try {
                logController.d("ProfileCompletionViewModel", "Signing out user...")
                setState { copy(isLoading = true) }
                
                signOutUseCase(SignOutMode.Soft)
                logController.d("ProfileCompletionViewModel", "User signed out successfully, navigating to login.")
                setEffect { ProfileCompletionEffect.NavigateToLogin }
            } catch (e: Exception) {
                logController.e("ProfileCompletionViewModel", e)
                setState { copy(isLoading = false, error = e.message) }
                setEffect { ProfileCompletionEffect.ShowError(e.message ?: "Sign out failed") }
            }
        }
    }
} 