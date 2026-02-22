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
 * Cross-cutting errors shared across all features.
 *
 * These cover infrastructure-level failures (network, auth, rate limiting) that
 * can occur in any feature. Feature-specific error sealed classes can wrap a
 * [CommonError] via their own `Wrapped(commonError)` subtype to delegate behavior.
 */
sealed class CommonError : AppError {

    data class NetworkUnavailable(
        override val cause: Throwable? = null
    ) : CommonError() {
        override val technicalMessage: String =
            "Network unavailable: ${cause?.message ?: "no connection"}"
        override val severity: ErrorSeverity = ErrorSeverity.ERROR
        override fun getErrorTitle(): String = "No Connection"
        override fun getUserFriendlyMessage(): String =
            "Unable to connect. Please check your internet connection and try again."
        override fun isRetryable(): Boolean = true
        override fun getErrorCode(): String = "NET-000"
    }

    data class ServerUnavailable(
        val httpCode: Int,
        val serverMessage: String? = null
    ) : CommonError() {
        override val technicalMessage: String =
            "Server unavailable: HTTP $httpCode${serverMessage?.let { " - $it" } ?: ""}"
        override val severity: ErrorSeverity = ErrorSeverity.ERROR
        override fun getErrorTitle(): String = "Server Unavailable"
        override fun getUserFriendlyMessage(): String =
            "Our servers are temporarily unavailable. Please try again in a moment."
        override fun isRetryable(): Boolean = true
        override fun getErrorCode(): String = "NET-$httpCode"
    }

    data class Unauthorized(
        val reason: String? = null
    ) : CommonError() {
        override val technicalMessage: String =
            "Unauthorized: ${reason ?: "session invalid"}"
        override val severity: ErrorSeverity = ErrorSeverity.ERROR
        override fun getErrorTitle(): String = "Authentication Error"
        override fun getUserFriendlyMessage(): String =
            "Your session has expired. Please sign in again."
        override fun isPermanent(): Boolean = true
        override fun getErrorCode(): String = "AUTH-401"
    }

    data class Timeout(
        val operation: String? = null,
        override val cause: Throwable? = null
    ) : CommonError() {
        override val technicalMessage: String =
            "Timeout${operation?.let { " during $it" } ?: ""}: ${cause?.message ?: "operation timed out"}"
        override val severity: ErrorSeverity = ErrorSeverity.ERROR
        override fun getErrorTitle(): String = "Request Timeout"
        override fun getUserFriendlyMessage(): String =
            "The operation took too long. Please check your connection and try again."
        override fun isRetryable(): Boolean = true
        override fun getErrorCode(): String = "NET-TMO"
    }

    data class RateLimited(
        val retryAfterSeconds: Int? = null
    ) : CommonError() {
        override val technicalMessage: String =
            "Rate limited${retryAfterSeconds?.let { ", retry after $it seconds" } ?: ""}"
        override val severity: ErrorSeverity = ErrorSeverity.WARNING
        override fun getErrorTitle(): String = "Too Many Requests"
        override fun getUserFriendlyMessage(): String =
            retryAfterSeconds?.let {
                "Too many attempts. Please wait $it seconds before trying again."
            } ?: "Too many attempts. Please wait a moment before trying again."
        override fun isRetryable(): Boolean = true
        override fun getRetryDelaySeconds(): Int? = retryAfterSeconds
        override fun getErrorCode(): String = "NET-429"
    }

    data class Unknown(
        override val cause: Throwable? = null
    ) : CommonError() {
        override val technicalMessage: String =
            "Unknown error: ${cause?.message ?: "no details available"}"
        override val severity: ErrorSeverity = ErrorSeverity.ERROR
        override fun getErrorTitle(): String = "Something Went Wrong"
        override fun getUserFriendlyMessage(): String =
            "An unexpected error occurred. Please try again or contact support."
        override fun isRetryable(): Boolean = true
        override fun getErrorCode(): String = "APP-UNK"
    }
}
