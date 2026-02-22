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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Unit tests for the AppError foundation: CommonError, ErrorClassifier,
 * StructuredErrorResponse, and WalletActivationError.toAppError() bridge.
 */
class TestAppError {

    // ============================================================
    // region CommonError subtypes — behavior verification
    // ============================================================

    @Test
    fun `Given NetworkUnavailable, Then severity is ERROR and is retryable`() {
        val error = CommonError.NetworkUnavailable(cause = IOException("No connection"))

        assertEquals(ErrorSeverity.ERROR, error.severity)
        assertTrue(error.isRetryable())
        assertFalse(error.isPermanent())
        assertFalse(error.shouldAutoRetry())
        assertEquals("NET-000", error.getErrorCode())
        assertEquals("No Connection", error.getErrorTitle())
        assertNotNull(error.getUserFriendlyMessage())
        assertNull(error.getRetryDelaySeconds())
    }

    @Test
    fun `Given ServerUnavailable, Then severity is ERROR with HTTP code in error code`() {
        val error = CommonError.ServerUnavailable(httpCode = 503, serverMessage = "Overloaded")

        assertEquals(ErrorSeverity.ERROR, error.severity)
        assertTrue(error.isRetryable())
        assertFalse(error.isPermanent())
        assertEquals("NET-503", error.getErrorCode())
        assertEquals("Server Unavailable", error.getErrorTitle())
    }

    @Test
    fun `Given Unauthorized, Then is permanent and not retryable`() {
        val error = CommonError.Unauthorized(reason = "Token expired")

        assertEquals(ErrorSeverity.ERROR, error.severity)
        assertFalse(error.isRetryable())
        assertTrue(error.isPermanent())
        assertEquals("AUTH-401", error.getErrorCode())
        assertEquals("Authentication Error", error.getErrorTitle())
    }

    @Test
    fun `Given Timeout, Then severity is ERROR and is retryable`() {
        val error = CommonError.Timeout(operation = "fetchDocuments")

        assertEquals(ErrorSeverity.ERROR, error.severity)
        assertTrue(error.isRetryable())
        assertFalse(error.isPermanent())
        assertEquals("NET-TMO", error.getErrorCode())
        assertEquals("Request Timeout", error.getErrorTitle())
    }

    @Test
    fun `Given RateLimited with delay, Then severity is WARNING with retry delay`() {
        val error = CommonError.RateLimited(retryAfterSeconds = 30)

        assertEquals(ErrorSeverity.WARNING, error.severity)
        assertTrue(error.isRetryable())
        assertFalse(error.isPermanent())
        assertEquals(30, error.getRetryDelaySeconds())
        assertEquals("NET-429", error.getErrorCode())
        assertEquals("Too Many Requests", error.getErrorTitle())
        assertTrue(error.getUserFriendlyMessage().contains("30"))
    }

    @Test
    fun `Given RateLimited without delay, Then retry delay is null`() {
        val error = CommonError.RateLimited(retryAfterSeconds = null)

        assertTrue(error.isRetryable())
        assertNull(error.getRetryDelaySeconds())
    }

    @Test
    fun `Given Unknown, Then severity is ERROR and is retryable`() {
        val cause = RuntimeException("Something broke")
        val error = CommonError.Unknown(cause = cause)

        assertEquals(ErrorSeverity.ERROR, error.severity)
        assertTrue(error.isRetryable())
        assertFalse(error.isPermanent())
        assertEquals("APP-UNK", error.getErrorCode())
        assertEquals("Something Went Wrong", error.getErrorTitle())
        assertEquals(cause, error.cause)
    }

    // endregion

    // ============================================================
    // region Throwable.toCommonError() — exception classification
    // ============================================================

    @Test
    fun `Given SocketTimeoutException, When toCommonError, Then returns Timeout`() {
        val exception = SocketTimeoutException("Connection timed out")

        val error = exception.toCommonError()

        assertTrue(error is CommonError.Timeout)
        assertEquals(exception, error.cause)
    }

    @Test
    fun `Given UnknownHostException, When toCommonError, Then returns NetworkUnavailable`() {
        val exception = UnknownHostException("api.example.com")

        val error = exception.toCommonError()

        assertTrue(error is CommonError.NetworkUnavailable)
    }

    @Test
    fun `Given IOException, When toCommonError, Then returns NetworkUnavailable`() {
        val exception = IOException("Connection reset")

        val error = exception.toCommonError()

        assertTrue(error is CommonError.NetworkUnavailable)
    }

    @Test
    fun `Given SSLException, When toCommonError, Then returns NetworkUnavailable`() {
        val exception = SSLException("Certificate validation failed")

        val error = exception.toCommonError()

        assertTrue(error is CommonError.NetworkUnavailable)
    }

    @Test
    fun `Given exception with timeout in message, When toCommonError, Then returns Timeout`() {
        val exception = RuntimeException("Request timeout after 30s")

        val error = exception.toCommonError()

        assertTrue(error is CommonError.Timeout)
    }

    @Test
    fun `Given exception with HTTP 401 in message, When toCommonError, Then returns Unauthorized`() {
        val exception = RuntimeException("HTTP 401 Unauthorized")

        val error = exception.toCommonError()

        assertTrue(error is CommonError.Unauthorized)
    }

    @Test
    fun `Given exception with unauthorized keyword, When toCommonError, Then returns Unauthorized`() {
        val exception = RuntimeException("User is unauthorized")

        val error = exception.toCommonError()

        assertTrue(error is CommonError.Unauthorized)
    }

    @Test
    fun `Given exception with status 403, When toCommonError, Then returns Unauthorized`() {
        val exception = RuntimeException("Request failed with status 403")

        val error = exception.toCommonError()

        assertTrue(error is CommonError.Unauthorized)
    }

    @Test
    fun `Given exception with HTTP 429 in message, When toCommonError, Then returns RateLimited`() {
        val exception = RuntimeException("HTTP 429 Too Many Requests")

        val error = exception.toCommonError()

        assertTrue(error is CommonError.RateLimited)
    }

    @Test
    fun `Given exception with rate limit keyword, When toCommonError, Then returns RateLimited`() {
        val exception = RuntimeException("Rate limit exceeded")

        val error = exception.toCommonError()

        assertTrue(error is CommonError.RateLimited)
    }

    @Test
    fun `Given exception with HTTP 503, When toCommonError, Then returns ServerUnavailable`() {
        val exception = RuntimeException("HTTP 503 Service Unavailable")

        val error = exception.toCommonError()

        assertTrue(error is CommonError.ServerUnavailable)
    }

    @Test
    fun `Given exception with server unavailable keyword, When toCommonError, Then returns ServerUnavailable`() {
        val exception = RuntimeException("The server unavailable, please retry")

        val error = exception.toCommonError()

        assertTrue(error is CommonError.ServerUnavailable)
    }

    @Test
    fun `Given exception with HTTP 500, When toCommonError, Then returns ServerUnavailable with code 500`() {
        val exception = RuntimeException("HTTP/1.1 500 Internal Server Error")

        val error = exception.toCommonError()

        assertTrue(error is CommonError.ServerUnavailable)
        assertEquals(500, (error as CommonError.ServerUnavailable).httpCode)
    }

    @Test
    fun `Given generic exception, When toCommonError, Then returns Unknown`() {
        val exception = RuntimeException("Something completely unexpected")

        val error = exception.toCommonError()

        assertTrue(error is CommonError.Unknown)
        assertEquals(exception, error.cause)
    }

    @Test
    fun `Given exception with bare 401 without HTTP context, When toCommonError, Then returns Unknown`() {
        val exception = RuntimeException("Transaction #14012 failed")

        val error = exception.toCommonError()

        assertTrue(error is CommonError.Unknown)
    }

    @Test
    fun `Given exception with bare 503 without HTTP context, When toCommonError, Then returns Unknown`() {
        val exception = RuntimeException("Row 503 not found in spreadsheet")

        val error = exception.toCommonError()

        assertTrue(error is CommonError.Unknown)
    }

    @Test
    fun `Given exception with null message, When toCommonError, Then returns Unknown`() {
        val exception = RuntimeException(null as String?)

        val error = exception.toCommonError()

        assertTrue(error is CommonError.Unknown)
    }

    // endregion

    // ============================================================
    // region StructuredErrorResponse.parse() — JSON parsing
    // ============================================================

    @Test
    fun `Given valid JSON, When parse, Then returns StructuredErrorResponse`() {
        val json = """
        {
            "error": {
                "code": "CREDENTIAL_OUT_OF_USAGES",
                "message": "The credential has reached its maximum number of usages",
                "domain": "issuance",
                "severity": "error",
                "retryable": false,
                "details": { "documentType": "eu.europa.ec.eudi.pid.1" },
                "retryAfterSeconds": null
            }
        }
        """.trimIndent()

        val result = StructuredErrorResponse.parse(json)

        assertNotNull(result)
        assertEquals("CREDENTIAL_OUT_OF_USAGES", result!!.code)
        assertEquals("The credential has reached its maximum number of usages", result.message)
        assertEquals("issuance", result.domain)
        assertEquals("error", result.severity)
        assertFalse(result.retryable)
        assertEquals("eu.europa.ec.eudi.pid.1", result.details["documentType"])
        assertNull(result.retryAfterSeconds)
    }

    @Test
    fun `Given JSON with retryAfterSeconds, When parse, Then retryAfterSeconds is set`() {
        val json = """
        {
            "error": {
                "code": "RATE_LIMITED",
                "message": "Too many requests",
                "retryable": true,
                "retryAfterSeconds": 45
            }
        }
        """.trimIndent()

        val result = StructuredErrorResponse.parse(json)

        assertNotNull(result)
        assertEquals(45, result!!.retryAfterSeconds)
        assertTrue(result.retryable)
    }

    @Test
    fun `Given JSON missing error object, When parse, Then returns null`() {
        val json = """{"status": "error", "message": "Something went wrong"}"""

        val result = StructuredErrorResponse.parse(json)

        assertNull(result)
    }

    @Test
    fun `Given JSON with empty code, When parse, Then returns null`() {
        val json = """{"error": {"code": "", "message": "Some error"}}"""

        val result = StructuredErrorResponse.parse(json)

        assertNull(result)
    }

    @Test
    fun `Given invalid JSON, When parse, Then returns null`() {
        val json = "not a json string at all"

        val result = StructuredErrorResponse.parse(json)

        assertNull(result)
    }

    @Test
    fun `Given null input, When parse, Then returns null`() {
        val result = StructuredErrorResponse.parse(null)

        assertNull(result)
    }

    @Test
    fun `Given blank input, When parse, Then returns null`() {
        val result = StructuredErrorResponse.parse("   ")

        assertNull(result)
    }

    @Test
    fun `Given minimal valid JSON, When parse, Then uses defaults for optional fields`() {
        val json = """{"error": {"code": "SOME_ERROR", "message": "Oops"}}"""

        val result = StructuredErrorResponse.parse(json)

        assertNotNull(result)
        assertEquals("SOME_ERROR", result!!.code)
        assertEquals("Oops", result.message)
        assertNull(result.domain)
        assertNull(result.severity)
        assertFalse(result.retryable)
        assertTrue(result.details.isEmpty())
        assertNull(result.retryAfterSeconds)
    }

    @Test
    fun `Given JSON with non-string detail values, When parse, Then converts them to strings`() {
        val json = """
        {
            "error": {
                "code": "SOME_ERROR",
                "message": "Error with mixed details",
                "details": {
                    "stringVal": "hello",
                    "numericVal": 42,
                    "boolVal": true
                }
            }
        }
        """.trimIndent()

        val result = StructuredErrorResponse.parse(json)

        assertNotNull(result)
        assertEquals("hello", result!!.details["stringVal"])
        assertEquals("42", result.details["numericVal"])
        assertEquals("true", result.details["boolVal"])
    }

    // endregion

    // ============================================================
    // region StructuredErrorResponse.toAppError() — error mapping
    // ============================================================

    @Test
    fun `Given RATE_LIMITED code, When toAppError, Then returns RateLimited`() {
        val response = StructuredErrorResponse(
            code = "RATE_LIMITED",
            message = "Too many requests",
            retryAfterSeconds = 30,
        )

        val error = response.toAppError()

        assertTrue(error is CommonError.RateLimited)
        assertEquals(30, (error as CommonError.RateLimited).retryAfterSeconds)
    }

    @Test
    fun `Given UNAUTHORIZED code, When toAppError, Then returns Unauthorized`() {
        val response = StructuredErrorResponse(
            code = "UNAUTHORIZED",
            message = "Invalid token",
        )

        val error = response.toAppError()

        assertTrue(error is CommonError.Unauthorized)
    }

    @Test
    fun `Given SERVER_ERROR code, When toAppError, Then returns ServerUnavailable with httpCode`() {
        val response = StructuredErrorResponse(
            code = "SERVER_ERROR",
            message = "Internal error",
        )

        val error = response.toAppError(httpCode = 502)

        assertTrue(error is CommonError.ServerUnavailable)
        assertEquals(502, (error as CommonError.ServerUnavailable).httpCode)
    }

    @Test
    fun `Given unknown code, When toAppError, Then returns AppError with structured data`() {
        val response = StructuredErrorResponse(
            code = "CREDENTIAL_OUT_OF_USAGES",
            message = "Credential limit reached",
            domain = "issuance",
            severity = "warning",
            retryable = false,
        )

        val error = response.toAppError()

        assertEquals("CREDENTIAL_OUT_OF_USAGES", error.getErrorCode())
        assertEquals("Credential limit reached", error.getUserFriendlyMessage())
        assertEquals(ErrorSeverity.WARNING, error.severity)
        assertFalse(error.isRetryable())
    }

    @Test
    fun `Given severity info, When toAppError for unknown code, Then severity is INFO`() {
        val response = StructuredErrorResponse(
            code = "SOME_INFO",
            message = "FYI",
            severity = "info",
        )

        val error = response.toAppError()

        assertEquals(ErrorSeverity.INFO, error.severity)
    }

    @Test
    fun `Given severity critical, When toAppError for unknown code, Then severity is CRITICAL`() {
        val response = StructuredErrorResponse(
            code = "SYSTEM_FAILURE",
            message = "Critical system failure",
            severity = "critical",
        )

        val error = response.toAppError()

        assertEquals(ErrorSeverity.CRITICAL, error.severity)
    }

    // endregion

    // ============================================================
    // region WalletActivationError.toAppError() — bridge verification
    // ============================================================

    @Test
    fun `Given ChallengeRateLimited, When toAppError, Then bridges all fields correctly`() {
        val source = WalletActivationError.ChallengeRateLimited(retryAfterSeconds = 45)

        val error = source.toAppError()

        assertEquals(source.getErrorTitle(), error.getErrorTitle())
        assertEquals(source.getUserFriendlyMessage(), error.getUserFriendlyMessage())
        assertEquals(source.isRetryable(), error.isRetryable())
        assertEquals(source.shouldAutoRetry(), error.shouldAutoRetry())
        assertEquals(source.isPermanent(), error.isPermanent())
        assertEquals(source.getRetryDelaySeconds(), error.getRetryDelaySeconds())
        assertEquals(ErrorSeverity.WARNING, error.severity)
        assertEquals("WUA-429", error.getErrorCode())
    }

    @Test
    fun `Given ChallengeExpired, When toAppError, Then severity is WARNING`() {
        val error = WalletActivationError.ChallengeExpired("abc").toAppError()

        assertEquals(ErrorSeverity.WARNING, error.severity)
        assertEquals("WUA-EXP", error.getErrorCode())
        assertTrue(error.shouldAutoRetry())
    }

    @Test
    fun `Given ChallengeNotFound, When toAppError, Then severity is WARNING`() {
        val error = WalletActivationError.ChallengeNotFound("xyz").toAppError()

        assertEquals(ErrorSeverity.WARNING, error.severity)
        assertEquals("WUA-CNF", error.getErrorCode())
        assertTrue(error.shouldAutoRetry())
    }

    @Test
    fun `Given AttestationRejected with device issue, When toAppError, Then severity is CRITICAL`() {
        val error = WalletActivationError.AttestationRejected("Low security", isDeviceIssue = true).toAppError()

        assertEquals(ErrorSeverity.CRITICAL, error.severity)
        assertEquals("WUA-ATT", error.getErrorCode())
        assertTrue(error.isPermanent())
    }

    @Test
    fun `Given AttestationRejected without device issue, When toAppError, Then severity is ERROR`() {
        val error = WalletActivationError.AttestationRejected("Cert invalid", isDeviceIssue = false).toAppError()

        assertEquals(ErrorSeverity.ERROR, error.severity)
        assertFalse(error.isPermanent())
    }

    @Test
    fun `Given DeviceSecurityInsufficient, When toAppError, Then severity is CRITICAL`() {
        val error = WalletActivationError.DeviceSecurityInsufficient(listOf("HSM"), "LOW").toAppError()

        assertEquals(ErrorSeverity.CRITICAL, error.severity)
        assertEquals("WUA-SEC", error.getErrorCode())
        assertTrue(error.isPermanent())
    }

    @Test
    fun `Given CryptographicFailure, When toAppError, Then is retryable`() {
        val error = WalletActivationError.CryptographicFailure("key gen", Exception()).toAppError()

        assertEquals(ErrorSeverity.ERROR, error.severity)
        assertEquals("WUA-CRY", error.getErrorCode())
        assertTrue(error.isRetryable())
    }

    @Test
    fun `Given WalletAlreadyExists, When toAppError, Then severity is WARNING`() {
        val error = WalletActivationError.WalletAlreadyExists().toAppError()

        assertEquals(ErrorSeverity.WARNING, error.severity)
        assertEquals("WUA-409", error.getErrorCode())
        assertTrue(error.isRetryable())
    }

    @Test
    fun `Given NetworkFailure, When toAppError, Then is retryable`() {
        val error = WalletActivationError.NetworkFailure(cause = IOException()).toAppError()

        assertEquals(ErrorSeverity.ERROR, error.severity)
        assertEquals("WUA-NET", error.getErrorCode())
        assertTrue(error.isRetryable())
    }

    @Test
    fun `Given ServerRejection, When toAppError, Then error code is WUA-REJ`() {
        val error = WalletActivationError.ServerRejection(400, "Bad Request").toAppError()

        assertEquals("WUA-REJ", error.getErrorCode())
    }

    @Test
    fun `Given ServerError, When toAppError, Then error code includes HTTP code`() {
        val error = WalletActivationError.ServerError(500, "Internal Error").toAppError()

        assertEquals("WUA-500", error.getErrorCode())
        assertTrue(error.isRetryable())
    }

    @Test
    fun `Given NotificationRegistrationFailure, When toAppError, Then severity is WARNING`() {
        val error = WalletActivationError.NotificationRegistrationFailure(Exception()).toAppError()

        assertEquals(ErrorSeverity.WARNING, error.severity)
        assertEquals("WUA-NTF", error.getErrorCode())
    }

    @Test
    fun `Given AuthenticationFailure, When toAppError, Then is permanent`() {
        val error = WalletActivationError.AuthenticationFailure("Token revoked").toAppError()

        assertEquals(ErrorSeverity.ERROR, error.severity)
        assertEquals("WUA-AUTH", error.getErrorCode())
        assertTrue(error.isPermanent())
    }

    @Test
    fun `Given TimeoutError, When toAppError, Then is retryable`() {
        val error = WalletActivationError.TimeoutError("activation", 30).toAppError()

        assertEquals(ErrorSeverity.ERROR, error.severity)
        assertEquals("WUA-TMO", error.getErrorCode())
        assertTrue(error.isRetryable())
    }

    @Test
    fun `Given UnexpectedError, When toAppError, Then error code is WUA-UNK`() {
        val error = WalletActivationError.UnexpectedError(RuntimeException("oops")).toAppError()

        assertEquals(ErrorSeverity.ERROR, error.severity)
        assertEquals("WUA-UNK", error.getErrorCode())
    }

    // endregion
}
