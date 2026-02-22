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
 * Common contract for all typed application errors.
 *
 * This is an interface (not sealed class) because some error types like [WalletActivationError]
 * already extend [Exception]. Feature-specific error sealed classes implement this directly.
 *
 * Every error provides:
 * - A user-facing title and message (for UI display)
 * - A technical message (for logging/debugging)
 * - Severity level (drives UI treatment: colors, icons, layout)
 * - Recovery hints (retryable, auto-retry, permanent)
 * - An optional error code in "DOMAIN-NNN" format (for user support)
 */
interface AppError {
    val technicalMessage: String
    val cause: Throwable? get() = null
    val severity: ErrorSeverity get() = ErrorSeverity.ERROR

    fun getErrorTitle(): String
    fun getUserFriendlyMessage(): String

    fun isRetryable(): Boolean = false
    fun shouldAutoRetry(): Boolean = false
    fun isPermanent(): Boolean = false
    fun getRetryDelaySeconds(): Int? = null

    /** Error code in "DOMAIN-NNN" format for user-facing support references. */
    fun getErrorCode(): String? = null
}
