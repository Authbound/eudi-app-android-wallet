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
package eu.europa.ec.businesslogic.model.error

/**
 * Comprehensive error types for wallet activation (WUA creation) failures.
 * Provides specific error classification for better user experience and debugging.
 *
 * Includes challenge-specific errors for EUDI ARF dynamic attestation compliance.
 */
sealed class WalletActivationError(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    // ============================================================
    // Challenge-specific errors for EUDI ARF compliance
    // ============================================================

    /**
     * Rate limit exceeded when requesting attestation challenge.
     * User should wait before retrying.
     */
    data class ChallengeRateLimited(
        val retryAfterSeconds: Int
    ) : WalletActivationError(
        "Rate limit exceeded. Please wait $retryAfterSeconds seconds."
    )

    /**
     * Challenge has expired before activation could complete.
     * Should auto-retry with a new challenge.
     */
    data class ChallengeExpired(
        val challengeId: String
    ) : WalletActivationError(
        "Security challenge expired."
    )

    /**
     * Challenge ID not found on server (already used or invalid).
     * Should auto-retry with a new challenge.
     */
    data class ChallengeNotFound(
        val challengeId: String
    ) : WalletActivationError(
        "Security challenge not found."
    )

    /**
     * Device attestation was rejected by the backend.
     * This could be a device security issue or certificate chain problem.
     */
    data class AttestationRejected(
        val reason: String,
        val isDeviceIssue: Boolean = false
    ) : WalletActivationError(
        "Device attestation rejected: $reason"
    )

    // ============================================================
    // Device and security errors
    // ============================================================

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

    // ============================================================
    // Network and server errors
    // ============================================================

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
     * Server error (5xx) - backend issue that may be temporary.
     */
    data class ServerError(
        val httpCode: Int,
        val serverMessage: String?
    ) : WalletActivationError(
        "Server error during wallet activation (HTTP $httpCode)"
    )

    // ============================================================
    // Other errors
    // ============================================================

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

        message?.contains("timeout", ignoreCase = true) == true -> {
            WalletActivationError.TimeoutError("unknown", 30)
        }

        message?.contains("authentication", ignoreCase = true) == true ||
        message?.contains("not authenticated", ignoreCase = true) == true -> {
            WalletActivationError.AuthenticationFailure(message ?: "Unknown authentication error")
        }

        message?.contains("crypto", ignoreCase = true) == true ||
        message?.contains("key", ignoreCase = true) == true ||
        message?.contains("keystore", ignoreCase = true) == true -> {
            WalletActivationError.CryptographicFailure("key generation", this)
        }

        message?.contains("HTTP") == true -> {
            val httpCode = message?.let { msg ->
                Regex("HTTP (\\d+)").find(msg)?.groupValues?.get(1)?.toIntOrNull()
            }
            when (httpCode) {
                in 500..599 -> WalletActivationError.ServerError(httpCode!!, message)
                else -> WalletActivationError.NetworkFailure(httpCode, this)
            }
        }

        else -> WalletActivationError.UnexpectedError(this)
    }
}

/**
 * HTTP error response data for typed error parsing.
 */
data class HttpErrorResponse(
    val code: Int,
    val message: String?,
    val errorBody: String?,
    val retryAfterSeconds: Int? = null
)

/**
 * Maps HTTP error response to specific WalletActivationError based on status code and error body.
 * This provides more accurate error classification than parsing exception messages.
 */
fun HttpErrorResponse.toWalletActivationError(): WalletActivationError {
    return when (code) {
        429 -> WalletActivationError.ChallengeRateLimited(
            retryAfterSeconds = retryAfterSeconds ?: 60
        )

        410 -> WalletActivationError.ChallengeExpired(
            challengeId = parseErrorField("challengeId") ?: "unknown"
        )

        404 -> {
            // Check if this is a challenge not found error
            if (errorBody?.contains("challenge", ignoreCase = true) == true) {
                WalletActivationError.ChallengeNotFound(
                    challengeId = parseErrorField("challengeId") ?: "unknown"
                )
            } else {
                WalletActivationError.ServerRejection(code, message)
            }
        }

        400 -> {
            // Check for attestation-specific errors
            when {
                errorBody?.contains("attestation", ignoreCase = true) == true ||
                errorBody?.contains("certificate", ignoreCase = true) == true -> {
                    val isDeviceIssue = errorBody.contains("device", ignoreCase = true) ||
                            errorBody.contains("security", ignoreCase = true)
                    WalletActivationError.AttestationRejected(
                        reason = parseErrorField("error") ?: message ?: "Unknown",
                        isDeviceIssue = isDeviceIssue
                    )
                }
                else -> WalletActivationError.ServerRejection(code, message)
            }
        }

        422 -> {
            // Unprocessable entity - often device security issues
            WalletActivationError.DeviceSecurityInsufficient(
                missingRequirements = parseRequirements(),
                deviceSecurityLevel = "UNKNOWN"
            )
        }

        401, 403 -> WalletActivationError.AuthenticationFailure(
            reason = message ?: "Unauthorized"
        )

        in 500..599 -> WalletActivationError.ServerError(code, message)

        else -> WalletActivationError.ServerRejection(code, message)
    }
}

/**
 * Parse a specific field from the error body JSON.
 */
private fun HttpErrorResponse.parseErrorField(field: String): String? {
    return errorBody?.let { body ->
        Regex("\"$field\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
    }
}

/**
 * Parse security requirements from the error body.
 */
private fun HttpErrorResponse.parseRequirements(): List<String> {
    val requirements = mutableListOf<String>()
    errorBody?.let { body ->
        if (body.contains("hardware", ignoreCase = true)) {
            requirements.add("Hardware security module")
        }
        if (body.contains("verified", ignoreCase = true) || body.contains("boot", ignoreCase = true)) {
            requirements.add("Verified boot")
        }
        if (body.contains("patch", ignoreCase = true)) {
            requirements.add("Recent security patches")
        }
        if (body.contains("biometric", ignoreCase = true)) {
            requirements.add("Biometric hardware")
        }
    }
    return requirements.ifEmpty { listOf("Unknown requirements") }
}

/**
 * User-friendly error messages for display in UI.
 */
fun WalletActivationError.getUserFriendlyMessage(): String {
    return when (this) {
        // Challenge-specific errors
        is WalletActivationError.ChallengeRateLimited ->
            "Too many attempts. Please wait $retryAfterSeconds seconds before trying again."

        is WalletActivationError.ChallengeExpired ->
            "The security session expired. Retrying automatically..."

        is WalletActivationError.ChallengeNotFound ->
            "The security session was not found. Retrying automatically..."

        is WalletActivationError.AttestationRejected ->
            if (isDeviceIssue) {
                "Your device couldn't be verified. It may not meet security requirements."
            } else {
                "Verification failed. Please try again or contact support."
            }

        // Device and security errors
        is WalletActivationError.DeviceSecurityInsufficient ->
            "Your device doesn't meet the security requirements for this wallet."

        is WalletActivationError.CryptographicFailure ->
            "Failed to create secure keys for your wallet. Please try again."

        // Network and server errors
        is WalletActivationError.NetworkFailure ->
            "Connection lost. Please check your internet and try again."

        is WalletActivationError.ServerRejection ->
            "The server couldn't activate your wallet. Please try again later."

        is WalletActivationError.ServerError ->
            "Our servers are temporarily unavailable. Please try again in a moment."

        // Other errors
        is WalletActivationError.NotificationRegistrationFailure ->
            "Failed to set up notifications. You may not receive credential updates."

        is WalletActivationError.AuthenticationFailure ->
            "Authentication error. Please sign in again."

        is WalletActivationError.TimeoutError ->
            "The operation took too long. Please check your connection and try again."

        is WalletActivationError.UnexpectedError ->
            "Something went wrong. Please try again or contact support."
    }
}

/**
 * User-friendly error titles for display in UI.
 */
fun WalletActivationError.getErrorTitle(): String {
    return when (this) {
        is WalletActivationError.ChallengeRateLimited -> "Too Many Attempts"
        is WalletActivationError.ChallengeExpired -> "Session Expired"
        is WalletActivationError.ChallengeNotFound -> "Session Not Found"
        is WalletActivationError.AttestationRejected -> "Verification Failed"
        is WalletActivationError.DeviceSecurityInsufficient -> "Device Not Supported"
        is WalletActivationError.CryptographicFailure -> "Security Error"
        is WalletActivationError.NetworkFailure -> "Connection Lost"
        is WalletActivationError.ServerRejection -> "Request Rejected"
        is WalletActivationError.ServerError -> "Server Unavailable"
        is WalletActivationError.NotificationRegistrationFailure -> "Notification Setup Failed"
        is WalletActivationError.AuthenticationFailure -> "Authentication Error"
        is WalletActivationError.TimeoutError -> "Request Timeout"
        is WalletActivationError.UnexpectedError -> "Something Went Wrong"
    }
}

/**
 * Determines if the error is recoverable with a user-initiated retry.
 */
fun WalletActivationError.isRetryable(): Boolean {
    return when (this) {
        is WalletActivationError.ChallengeRateLimited -> true // After countdown
        is WalletActivationError.NetworkFailure -> true
        is WalletActivationError.TimeoutError -> true
        is WalletActivationError.NotificationRegistrationFailure -> true
        is WalletActivationError.ServerError -> true // 5xx errors
        is WalletActivationError.ServerRejection -> httpCode in 500..599
        is WalletActivationError.CryptographicFailure -> true
        else -> false
    }
}

/**
 * Determines if the error should trigger an automatic silent retry.
 * These are transient errors that can be resolved without user intervention.
 */
fun WalletActivationError.shouldAutoRetry(): Boolean {
    return when (this) {
        is WalletActivationError.ChallengeExpired -> true
        is WalletActivationError.ChallengeNotFound -> true
        else -> false
    }
}

/**
 * Determines if the error requires user to wait before retrying.
 * Returns the number of seconds to wait, or null if no wait required.
 */
fun WalletActivationError.getRetryDelaySeconds(): Int? {
    return when (this) {
        is WalletActivationError.ChallengeRateLimited -> retryAfterSeconds
        else -> null
    }
}

/**
 * Determines if the error is permanent and cannot be resolved by retrying.
 */
fun WalletActivationError.isPermanent(): Boolean {
    return when (this) {
        is WalletActivationError.DeviceSecurityInsufficient -> true
        is WalletActivationError.AttestationRejected -> isDeviceIssue
        is WalletActivationError.AuthenticationFailure -> true
        else -> false
    }
}
