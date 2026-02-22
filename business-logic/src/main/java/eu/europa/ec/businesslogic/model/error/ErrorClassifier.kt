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

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Regex that matches HTTP status codes in context — requires a preceding keyword like
 * "HTTP", "status", or "code" to avoid false positives on arbitrary 3-digit numbers.
 * Examples matched: "HTTP 401", "status 503", "code: 429", "HTTP/1.1 500"
 */
private val HTTP_CODE_PATTERN = Regex(
    "(?:HTTP/?\\d?\\.?\\d?\\s*|status\\s*:?\\s*|code\\s*:?\\s*)(\\d{3})",
    RegexOption.IGNORE_CASE
)

/**
 * Extension functions for classifying raw exceptions into typed [CommonError] instances.
 *
 * Classification uses exception type first (most reliable), then message heuristics
 * as a fallback. Feature-specific classifiers (Phase 2) will check domain-specific
 * patterns first, then delegate to [toCommonError] for infrastructure errors.
 */
fun Throwable.toCommonError(): CommonError {
    return when {
        this is SocketTimeoutException ->
            CommonError.Timeout(cause = this)

        this is UnknownHostException ->
            CommonError.NetworkUnavailable(cause = this)

        this is SSLException ->
            CommonError.NetworkUnavailable(cause = this)

        this is IOException ->
            CommonError.NetworkUnavailable(cause = this)

        // Message heuristics as fallback for wrapped exceptions.
        // "timeout" is safe as a plain keyword — it's unambiguous.
        message?.contains("timeout", ignoreCase = true) == true ->
            CommonError.Timeout(cause = this)

        // For HTTP codes, require context keywords to avoid matching arbitrary numbers.
        message?.contains("unauthorized", ignoreCase = true) == true ||
        message?.contains("not authenticated", ignoreCase = true) == true ||
        message?.let { extractHttpCode(it) } == 401 ||
        message?.let { extractHttpCode(it) } == 403 ->
            CommonError.Unauthorized(reason = message)

        message?.contains("rate limit", ignoreCase = true) == true ||
        message?.let { extractHttpCode(it) } == 429 ->
            CommonError.RateLimited()

        message?.contains("server unavailable", ignoreCase = true) == true ||
        message?.let { extractHttpCode(it) }?.let { it in 502..503 } == true ->
            CommonError.ServerUnavailable(httpCode = message?.let { extractHttpCode(it) } ?: 503)

        message?.contains("internal server error", ignoreCase = true) == true ||
        message?.let { extractHttpCode(it) } == 500 ->
            CommonError.ServerUnavailable(httpCode = 500)

        else ->
            CommonError.Unknown(cause = this)
    }
}

/**
 * Extracts an HTTP status code from a message string, requiring contextual keywords
 * (HTTP, status, code) to avoid false positives on arbitrary numbers.
 */
private fun extractHttpCode(msg: String): Int? {
    return HTTP_CODE_PATTERN.find(msg)?.groupValues?.get(1)?.toIntOrNull()
}
