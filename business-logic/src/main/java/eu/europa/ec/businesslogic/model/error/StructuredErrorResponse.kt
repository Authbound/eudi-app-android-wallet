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

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

/**
 * Represents a structured error response from the backend.
 *
 * Expected JSON format:
 * ```json
 * {
 *   "error": {
 *     "code": "CREDENTIAL_OUT_OF_USAGES",
 *     "message": "The credential has reached its maximum number of usages",
 *     "domain": "issuance",
 *     "severity": "error",
 *     "retryable": false,
 *     "details": { "documentType": "eu.europa.ec.eudi.pid.1" },
 *     "retryAfterSeconds": null
 *   }
 * }
 * ```
 *
 * This is designed for forward compatibility — when the backend starts returning
 * structured errors, this parser will map them to typed [AppError] instances.
 */
data class StructuredErrorResponse(
    val code: String,
    val message: String,
    val domain: String? = null,
    val severity: String? = null,
    val retryable: Boolean = false,
    val details: Map<String, String> = emptyMap(),
    val retryAfterSeconds: Int? = null,
) {

    companion object {
        /**
         * Parses a JSON string into a [StructuredErrorResponse].
         * Returns null if the JSON is malformed or missing the required `error.code` field.
         */
        fun parse(jsonBody: String?): StructuredErrorResponse? {
            if (jsonBody.isNullOrBlank()) return null
            return try {
                val root = JsonParser.parseString(jsonBody).asJsonObject
                val error = root.getAsJsonObject("error") ?: return null

                val code = error.get("code")?.asString?.ifBlank { null } ?: return null
                val message = error.get("message")?.asString ?: "Unknown error"
                val domain = error.get("domain")?.asString?.ifBlank { null }
                val severity = error.get("severity")?.asString?.ifBlank { null }
                val retryable = error.get("retryable")?.asBoolean ?: false

                val retryAfterSeconds = error.get("retryAfterSeconds")?.let { element ->
                    if (element.isJsonNull) null
                    else element.asInt.takeIf { it > 0 }
                }

                val details = mutableMapOf<String, String>()
                error.getAsJsonObject("details")?.entrySet()?.forEach { (key, value) ->
                    details[key] = if (value.isJsonPrimitive) value.asString else value.toString()
                }

                StructuredErrorResponse(
                    code = code,
                    message = message,
                    domain = domain,
                    severity = severity,
                    retryable = retryable,
                    details = details,
                    retryAfterSeconds = retryAfterSeconds,
                )
            } catch (_: JsonSyntaxException) {
                null
            } catch (_: IllegalStateException) {
                null
            } catch (_: UnsupportedOperationException) {
                null
            }
        }
    }

    /**
     * Converts this structured response to a typed [AppError], using the HTTP status code
     * for additional context.
     *
     * Known error codes are mapped to specific [CommonError] subtypes. Feature-specific
     * classifiers (Phase 2) will extend this to map domain codes (e.g., "CREDENTIAL_OUT_OF_USAGES")
     * to their own typed errors.
     */
    fun toAppError(httpCode: Int? = null): AppError {
        return when {
            code.equals("RATE_LIMITED", ignoreCase = true) ->
                CommonError.RateLimited(retryAfterSeconds)

            code.equals("UNAUTHORIZED", ignoreCase = true) ||
            code.equals("SESSION_EXPIRED", ignoreCase = true) ->
                CommonError.Unauthorized(reason = message)

            code.equals("SERVER_ERROR", ignoreCase = true) ->
                CommonError.ServerUnavailable(
                    httpCode = httpCode ?: 500,
                    serverMessage = message
                )

            code.equals("TIMEOUT", ignoreCase = true) ->
                CommonError.Timeout(operation = domain)

            // Default: create an AppError with the structured message preserved
            else -> object : AppError {
                override val technicalMessage: String = "Structured error [$code]: $message"
                override val severity: ErrorSeverity = parseSeverity()
                override fun getErrorTitle(): String = domain?.replaceFirstChar { it.uppercase() }
                    ?: "Error"
                override fun getUserFriendlyMessage(): String = message
                override fun isRetryable(): Boolean = retryable
                override fun getRetryDelaySeconds(): Int? = retryAfterSeconds
                override fun getErrorCode(): String = code
            }
        }
    }

    private fun parseSeverity(): ErrorSeverity {
        return when (severity?.lowercase()) {
            "info" -> ErrorSeverity.INFO
            "warning" -> ErrorSeverity.WARNING
            "critical" -> ErrorSeverity.CRITICAL
            else -> ErrorSeverity.ERROR
        }
    }
}
