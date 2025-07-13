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
package eu.europa.ec.authenticationlogic.model

/**
 * Result model for successful atomic onboarding operation.
 * 
 * Contains confirmation that all onboarding steps completed successfully:
 * - Profile was created/updated
 * - Wallet attestation was created
 * - Device was registered
 * - Push notifications were configured
 */
data class OnboardingResult(
    /**
     * The created/updated user profile
     */
    val profile: Profile,
    
    /**
     * Wallet attestation ID for reference
     */
    val walletAttestationId: String,
    
    /**
     * Timestamp when onboarding completed
     */
    val completedAt: String
)

/**
 * Specific error types for onboarding failures.
 * These map to backend error codes for proper error handling.
 */
sealed class OnboardingError : Exception() {
    /**
     * The requested handle is already taken by another user.
     * User should try a different handle.
     */
    data class HandleAlreadyTaken(
        override val message: String = "This handle is already taken. Please choose a different one."
    ) : OnboardingError()
    
    /**
     * The handle format is invalid (too short, invalid characters, etc.).
     * User should correct the handle format.
     */
    data class HandleInvalidFormat(
        override val message: String = "Handle must be at least 3 characters and contain only letters and numbers."
    ) : OnboardingError()
    
    /**
     * The user's device does not meet security requirements for wallet creation.
     * This is typically not retryable.
     */
    data class DeviceNotSupported(
        override val message: String = "Your device does not meet the security requirements for wallet creation."
    ) : OnboardingError()
    
    /**
     * Internal server error or infrastructure issue.
     * This is retryable after a delay.
     */
    data class InternalError(
        override val message: String = "An internal error occurred. Please try again later."
    ) : OnboardingError()
    
    /**
     * Rate limiting - too many onboarding attempts.
     * Retryable after a delay.
     */
    data class RateLimited(
        override val message: String = "Too many attempts. Please wait a moment and try again."
    ) : OnboardingError()
    
    /**
     * Network or connectivity error.
     * Retryable immediately.
     */
    data class NetworkError(
        override val message: String = "Network error. Please check your connection and try again."
    ) : OnboardingError()
}