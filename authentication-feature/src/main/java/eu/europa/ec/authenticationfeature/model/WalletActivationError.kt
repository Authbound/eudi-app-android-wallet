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
package eu.europa.ec.authenticationfeature.model

/**
 * Comprehensive error types for wallet activation (WUA creation) failures.
 * Provides specific error classification for better user experience and debugging.
 */
sealed class WalletActivationError(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * Device does not meet security requirements for LoA High compliance.
     */
    data class DeviceSecurityInsufficient(
        val missingRequirements: List<String>,
        val deviceSecurityLevel: String
    ) : WalletActivationError(
        "Device does not meet security requirements. Missing: ${missingRequirements.joinToString(", ")}"
    )
    
    /**
     * Failed to generate cryptographic keys or certificates.
     */
    data class CryptographicFailure(
        val operation: String,
        override val cause: Throwable
    ) : WalletActivationError(
        "Failed to perform cryptographic operation: $operation",
        cause
    )
    
    /**
     * Network-related failures during WUA creation.
     */
    data class NetworkFailure(
        val httpCode: Int? = null,
        override val cause: Throwable
    ) : WalletActivationError(
        "Network error during wallet activation${httpCode?.let { " (HTTP $it)" } ?: ""}",
        cause
    )
    
    /**
     * Backend rejected the WUA creation request.
     */
    data class ServerRejection(
        val httpCode: Int,
        val serverMessage: String?
    ) : WalletActivationError(
        "Server rejected wallet activation: ${serverMessage ?: "Unknown reason"} (HTTP $httpCode)"
    )
    
    /**
     * Push notification registration failed.
     */
    data class NotificationRegistrationFailure(
        override val cause: Throwable
    ) : WalletActivationError(
        "Failed to register for push notifications",
        cause
    )
    
    /**
     * User authentication state is invalid.
     */
    data class AuthenticationFailure(
        val reason: String
    ) : WalletActivationError(
        "Authentication error: $reason"
    )
    
    /**
     * Timeout during WUA creation process.
     */
    data class TimeoutError(
        val operation: String,
        val timeoutSeconds: Int
    ) : WalletActivationError(
        "Operation '$operation' timed out after $timeoutSeconds seconds"
    )
    
    /**
     * Generic unexpected error.
     */
    data class UnexpectedError(
        override val cause: Throwable
    ) : WalletActivationError(
        "An unexpected error occurred during wallet activation",
        cause
    )
}

/**
 * Helper function to map generic exceptions to specific WalletActivationError types.
 */
fun Throwable.toWalletActivationError(): WalletActivationError {
    return when {
        this is WalletActivationError -> this
        
        message?.contains("HTTP") == true -> {
            val httpCode = message?.let { msg ->
                Regex("HTTP (\\d+)").find(msg)?.groupValues?.get(1)?.toIntOrNull()
            }
            WalletActivationError.NetworkFailure(httpCode, this)
        }
        
        message?.contains("timeout", ignoreCase = true) == true -> {
            WalletActivationError.TimeoutError("unknown", 30)
        }
        
        message?.contains("authentication", ignoreCase = true) == true -> {
            WalletActivationError.AuthenticationFailure(message ?: "Unknown authentication error")
        }
        
        message?.contains("crypto", ignoreCase = true) == true || 
        message?.contains("key", ignoreCase = true) == true -> {
            WalletActivationError.CryptographicFailure("key generation", this)
        }
        
        else -> WalletActivationError.UnexpectedError(this)
    }
}

/**
 * User-friendly error messages for display in UI.
 */
fun WalletActivationError.getUserFriendlyMessage(): String {
    return when (this) {
        is WalletActivationError.DeviceSecurityInsufficient -> 
            "Your device doesn't meet the security requirements for this wallet. Please ensure your device has hardware security features enabled."
        
        is WalletActivationError.CryptographicFailure -> 
            "Failed to create secure keys for your wallet. Please try again."
        
        is WalletActivationError.NetworkFailure -> 
            "Network connection failed. Please check your internet connection and try again."
        
        is WalletActivationError.ServerRejection -> 
            "The server couldn't activate your wallet at this time. Please try again later."
        
        is WalletActivationError.NotificationRegistrationFailure -> 
            "Failed to set up notifications. You may not receive credential updates."
        
        is WalletActivationError.AuthenticationFailure -> 
            "Authentication error. Please sign in again and try activating your wallet."
        
        is WalletActivationError.TimeoutError -> 
            "The operation took too long. Please try again with a better internet connection."
        
        is WalletActivationError.UnexpectedError -> 
            "An unexpected error occurred. Please try again or contact support if the problem persists."
    }
}

/**
 * Determines if the error is recoverable with a retry.
 */
fun WalletActivationError.isRetryable(): Boolean {
    return when (this) {
        is WalletActivationError.NetworkFailure -> true
        is WalletActivationError.TimeoutError -> true
        is WalletActivationError.NotificationRegistrationFailure -> true
        is WalletActivationError.ServerRejection -> httpCode in 500..599 // Server errors, not client errors
        else -> false
    }
}