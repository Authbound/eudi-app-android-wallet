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
import eu.europa.ec.authenticationlogic.model.OnboardingError
import eu.europa.ec.authenticationlogic.model.OnboardingRequest
import eu.europa.ec.authenticationlogic.usecase.CheckHandleAvailabilityUseCase
import eu.europa.ec.authenticationlogic.usecase.CompleteOnboardingUseCase
import eu.europa.ec.authenticationlogic.usecase.GetCurrentUserUseCase
import eu.europa.ec.authenticationlogic.usecase.GetMyProfileUseCase
import eu.europa.ec.authenticationlogic.usecase.SignOutMode
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.businesslogic.controller.device.DeviceController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.notificationlogic.controller.UserScopedPushNotificationController
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

/**
 * Simplified ProfileCompletion ViewModel using atomic onboarding.
 * 
 * This replaces the complex two-phase onboarding with a single atomic operation
 * that eliminates partial state problems and simplifies error handling.
 */

data class ProfileCompletionStateV2(
    val handle: String = "",
    val displayName: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isHandleAvailable: Boolean? = null,
    val handleValidationJob: Job? = null,
    val onboardingStep: String = "",
    val canRetry: Boolean = false
) : ViewState

sealed class ProfileCompletionEventV2 : ViewEvent {
    data class OnHandleChanged(val handle: String) : ProfileCompletionEventV2()
    data class OnDisplayNameChanged(val displayName: String) : ProfileCompletionEventV2()
    data object CompleteOnboarding : ProfileCompletionEventV2()
    data object RetryOnboarding : ProfileCompletionEventV2()
    data object SignOut : ProfileCompletionEventV2()
}

sealed class ProfileCompletionEffectV2 : ViewSideEffect {
    data object NavigateToHome : ProfileCompletionEffectV2()
    data object NavigateToLogin : ProfileCompletionEffectV2()
    data class ShowError(val message: String) : ProfileCompletionEffectV2()
}

@KoinViewModel
class ProfileCompletionViewModelV2(
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val checkHandleAvailabilityUseCase: CheckHandleAvailabilityUseCase,
    private val getMyProfileUseCase: GetMyProfileUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val userScopedPushNotificationController: UserScopedPushNotificationController,
    private val deviceController: DeviceController,
    private val signOutUseCase: SignOutUseCase,
    private val logController: LogController
) : MviViewModel<ProfileCompletionEventV2, ProfileCompletionStateV2, ProfileCompletionEffectV2>() {

    override fun setInitialState(): ProfileCompletionStateV2 {
        // Load existing profile if available
        loadExistingProfile()
        return ProfileCompletionStateV2()
    }

    override fun handleEvents(event: ProfileCompletionEventV2) {
        when (event) {
            is ProfileCompletionEventV2.OnHandleChanged -> {
                val handle = event.handle.filter { it.isLetterOrDigit() }
                setState { copy(handle = handle, isHandleAvailable = null, error = null) }
                viewState.value.handleValidationJob?.cancel()
                val job = viewModelScope.launch {
                    delay(300) // Debounce
                    checkHandleAvailability(handle)
                }
                setState { copy(handleValidationJob = job) }
            }
            is ProfileCompletionEventV2.OnDisplayNameChanged -> {
                setState { copy(displayName = event.displayName, error = null) }
            }
            is ProfileCompletionEventV2.CompleteOnboarding -> {
                completeOnboarding()
            }
            is ProfileCompletionEventV2.RetryOnboarding -> {
                completeOnboarding()
            }
            is ProfileCompletionEventV2.SignOut -> {
                signOut()
            }
        }
    }

    private fun loadExistingProfile() {
        viewModelScope.launch {
            try {
                logController.d("ProfileCompletionV2", "Loading existing profile...")
                val profileResult = getMyProfileUseCase()
                if (profileResult.isSuccess) {
                    val profile = profileResult.getOrNull()
                    if (profile != null) {
                        logController.d("ProfileCompletionV2", "Found existing profile: ${profile.handle}")
                        setState { 
                            copy(
                                handle = profile.handle,
                                displayName = profile.displayName,
                                isHandleAvailable = true // If profile exists, handle was already validated
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logController.w("ProfileCompletionV2") { "Failed to load existing profile: ${e.message}"}
                // Continue with empty state - user will input fresh data
            }
        }
    }

    private fun checkHandleAvailability(handle: String) {
        if (handle.length < 3) {
            setState { copy(isHandleAvailable = false) }
            return
        }
        
        viewModelScope.launch {
            try {
                setState { copy(isLoading = true) }
                checkHandleAvailabilityUseCase(handle)
                    .onSuccess { isAvailable ->
                        setState { copy(isLoading = false, isHandleAvailable = isAvailable) }
                    }
                    .onFailure { error ->
                        setState { copy(isLoading = false, error = error.message) }
                    }
            } catch (e: Exception) {
                logController.e("ProfileCompletionV2", ) {"Handle availability check failed"}
                setState { copy(isLoading = false, error = "Failed to check handle availability") }
            }
        }
    }

    private fun completeOnboarding() {
        val state = viewState.value
        
        // Validate input
        if (state.handle.isBlank() || state.displayName.isBlank()) {
            setEffect { ProfileCompletionEffectV2.ShowError("Please fill in all fields.") }
            return
        }
        
        if (state.isHandleAvailable != true) {
            setEffect { ProfileCompletionEffectV2.ShowError("Please choose an available handle.") }
            return
        }

        viewModelScope.launch {
            try {
                setState { 
                    copy(
                        isLoading = true, 
                        error = null, 
                        canRetry = false,
                        onboardingStep = "Getting device information..."
                    ) 
                }

                // Get device info
                val deviceInfo = deviceController.getDeviceInfo()
                setState { copy(onboardingStep = "Registering for notifications...") }

                // Get current user
                val currentUser = getCurrentUserUseCase()
                if (currentUser == null) {
                    throw Exception("User not authenticated")
                }

                // Get push token
                val pushTokenResult = userScopedPushNotificationController.registerForPushNotifications(currentUser.id)
                val pushToken = pushTokenResult.getOrThrow()
                
                setState { copy(onboardingStep = "Completing onboarding...") }

                // Single atomic onboarding operation
                val onboardingRequest = OnboardingRequest(
                    handle = state.handle,
                    displayName = state.displayName,
                    deviceInfo = deviceInfo,
                    pushToken = pushToken
                )

                completeOnboardingUseCase(onboardingRequest)
                    .onSuccess { result ->
                        logController.i("ProfileCompletionV2", ) {"Onboarding completed successfully"}
                        setState { 
                            copy(
                                isLoading = false,
                                onboardingStep = "Onboarding completed!"
                            ) 
                        }
                        setEffect { ProfileCompletionEffectV2.NavigateToHome }
                    }
                    .onFailure { error ->
                        handleOnboardingError(error)
                    }

            } catch (e: Exception) {
                logController.e("ProfileCompletionV2" ) {"Onboarding failed"}
                handleOnboardingError(e)
            }
        }
    }

    private fun handleOnboardingError(error: Throwable) {
        logController.e("ProfileCompletionV2", ) {"Onboarding error: ${error.message}"}
        
        val (errorMessage, canRetry) = when (error) {
            is OnboardingError.HandleAlreadyTaken -> {
                setState { copy(isHandleAvailable = false) }
                error.message to false // User needs to choose different handle
            }
            is OnboardingError.HandleInvalidFormat -> {
                setState { copy(isHandleAvailable = false) }
                error.message to false // User needs to fix handle format
            }
            is OnboardingError.DeviceNotSupported -> {
                error.message to false // Not retryable
            }
            is OnboardingError.NetworkError,
            is OnboardingError.InternalError,
            is OnboardingError.RateLimited -> {
                error.message to true // Retryable
            }
            else -> {
                "An unexpected error occurred. Please try again." to true
            }
        }

        setState { 
            copy(
                isLoading = false,
                error = errorMessage,
                canRetry = canRetry,
                onboardingStep = ""
            ) 
        }
        setEffect { ProfileCompletionEffectV2.ShowError(errorMessage ?: "An unexpected error occurred. Please try again.") }
    }

    private fun signOut() {
        viewModelScope.launch {
            try {
                logController.d("ProfileCompletionV2", "Signing out user...")
                setState { copy(isLoading = true, error = null) }
                
                signOutUseCase(SignOutMode.Soft)
                logController.d("ProfileCompletionV2", "User signed out successfully")
                setEffect { ProfileCompletionEffectV2.NavigateToLogin }
            } catch (e: Exception) {
                logController.e("ProfileCompletionV2", ) {"Sign out failed"}
                setState { copy(isLoading = false, error = e.message) }
                setEffect { ProfileCompletionEffectV2.ShowError(e.message ?: "Sign out failed") }
            }
        }
    }
}