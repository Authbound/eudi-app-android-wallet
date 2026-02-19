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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for WalletActivationError classification and behavior logic.
 *
 * Tests focus on critical decision points:
 * - HTTP response → error type mapping
 * - Retry behavior determination (isRetryable, shouldAutoRetry, isPermanent)
 * - Exception → error type mapping
 */
class TestWalletActivationError {

    // region HttpErrorResponse.toWalletActivationError()

    @Test
    fun `Given 429 response with retry-after, When toWalletActivationError, Then returns ChallengeRateLimited with correct seconds`() {
        val response = HttpErrorResponse(
            code = 429,
            message = "Too Many Requests",
            errorBody = null,
            retryAfterSeconds = 45
        )

        val error = response.toWalletActivationError()

        assertTrue(error is WalletActivationError.ChallengeRateLimited)
        assertEquals(45, (error as WalletActivationError.ChallengeRateLimited).retryAfterSeconds)
    }

    @Test
    fun `Given 429 response without retry-after, When toWalletActivationError, Then defaults to 60 seconds`() {
        val response = HttpErrorResponse(
            code = 429,
            message = "Too Many Requests",
            errorBody = null,
            retryAfterSeconds = null
        )

        val error = response.toWalletActivationError()

        assertTrue(error is WalletActivationError.ChallengeRateLimited)
        assertEquals(60, (error as WalletActivationError.ChallengeRateLimited).retryAfterSeconds)
    }

    @Test
    fun `Given 410 response, When toWalletActivationError, Then returns ChallengeExpired`() {
        val response = HttpErrorResponse(
            code = 410,
            message = "Gone",
            errorBody = """{"challengeId": "abc123"}""",
            retryAfterSeconds = null
        )

        val error = response.toWalletActivationError()

        assertTrue(error is WalletActivationError.ChallengeExpired)
        assertEquals("abc123", (error as WalletActivationError.ChallengeExpired).challengeId)
    }

    @Test
    fun `Given 404 with challenge in body, When toWalletActivationError, Then returns ChallengeNotFound`() {
        val response = HttpErrorResponse(
            code = 404,
            message = "Not Found",
            errorBody = """{"error": "challenge not found", "challengeId": "xyz789"}""",
            retryAfterSeconds = null
        )

        val error = response.toWalletActivationError()

        assertTrue(error is WalletActivationError.ChallengeNotFound)
        assertEquals("xyz789", (error as WalletActivationError.ChallengeNotFound).challengeId)
    }

    @Test
    fun `Given 404 without challenge mention, When toWalletActivationError, Then returns ServerRejection`() {
        val response = HttpErrorResponse(
            code = 404,
            message = "Not Found",
            errorBody = """{"error": "resource not found"}""",
            retryAfterSeconds = null
        )

        val error = response.toWalletActivationError()

        assertTrue(error is WalletActivationError.ServerRejection)
        assertEquals(404, (error as WalletActivationError.ServerRejection).httpCode)
    }

    @Test
    fun `Given 400 with attestation error, When toWalletActivationError, Then returns AttestationRejected`() {
        val response = HttpErrorResponse(
            code = 400,
            message = "Bad Request",
            errorBody = """{"error": "attestation verification failed"}""",
            retryAfterSeconds = null
        )

        val error = response.toWalletActivationError()

        assertTrue(error is WalletActivationError.AttestationRejected)
        assertFalse((error as WalletActivationError.AttestationRejected).isDeviceIssue)
    }

    @Test
    fun `Given 400 with device security error, When toWalletActivationError, Then returns AttestationRejected with isDeviceIssue true`() {
        val response = HttpErrorResponse(
            code = 400,
            message = "Bad Request",
            errorBody = """{"error": "device attestation failed due to security level"}""",
            retryAfterSeconds = null
        )

        val error = response.toWalletActivationError()

        assertTrue(error is WalletActivationError.AttestationRejected)
        assertTrue((error as WalletActivationError.AttestationRejected).isDeviceIssue)
    }

    @Test
    fun `Given 422 response, When toWalletActivationError, Then returns DeviceSecurityInsufficient`() {
        val response = HttpErrorResponse(
            code = 422,
            message = "Unprocessable Entity",
            errorBody = """{"error": "device lacks hardware security and verified boot"}""",
            retryAfterSeconds = null
        )

        val error = response.toWalletActivationError()

        assertTrue(error is WalletActivationError.DeviceSecurityInsufficient)
        val deviceError = error as WalletActivationError.DeviceSecurityInsufficient
        assertTrue(deviceError.missingRequirements.contains("Hardware security module"))
        assertTrue(deviceError.missingRequirements.contains("Verified boot"))
    }

    @Test
    fun `Given 401 response, When toWalletActivationError, Then returns AuthenticationFailure`() {
        val response = HttpErrorResponse(
            code = 401,
            message = "Unauthorized",
            errorBody = null,
            retryAfterSeconds = null
        )

        val error = response.toWalletActivationError()

        assertTrue(error is WalletActivationError.AuthenticationFailure)
    }

    @Test
    fun `Given 403 response, When toWalletActivationError, Then returns AuthenticationFailure`() {
        val response = HttpErrorResponse(
            code = 403,
            message = "Forbidden",
            errorBody = null,
            retryAfterSeconds = null
        )

        val error = response.toWalletActivationError()

        assertTrue(error is WalletActivationError.AuthenticationFailure)
    }

    @Test
    fun `Given 500 response, When toWalletActivationError, Then returns ServerError`() {
        val response = HttpErrorResponse(
            code = 500,
            message = "Internal Server Error",
            errorBody = null,
            retryAfterSeconds = null
        )

        val error = response.toWalletActivationError()

        assertTrue(error is WalletActivationError.ServerError)
        assertEquals(500, (error as WalletActivationError.ServerError).httpCode)
    }

    @Test
    fun `Given 503 response, When toWalletActivationError, Then returns ServerError`() {
        val response = HttpErrorResponse(
            code = 503,
            message = "Service Unavailable",
            errorBody = null,
            retryAfterSeconds = null
        )

        val error = response.toWalletActivationError()

        assertTrue(error is WalletActivationError.ServerError)
        assertEquals(503, (error as WalletActivationError.ServerError).httpCode)
    }

    @Test
    fun `Given 409 response, When toWalletActivationError, Then returns WalletAlreadyExists`() {
        val response = HttpErrorResponse(
            code = 409,
            message = "Conflict",
            errorBody = null,
            retryAfterSeconds = null
        )

        val error = response.toWalletActivationError()

        assertTrue(error is WalletActivationError.WalletAlreadyExists)
        assertEquals(409, (error as WalletActivationError.WalletAlreadyExists).httpCode)
    }

    // endregion

    // region isRetryable()

    @Test
    fun `Given NetworkFailure, When isRetryable, Then returns true`() {
        val error = WalletActivationError.NetworkFailure(cause = Exception("Connection failed"))
        assertTrue(error.isRetryable())
    }

    @Test
    fun `Given TimeoutError, When isRetryable, Then returns true`() {
        val error = WalletActivationError.TimeoutError("activation", 30)
        assertTrue(error.isRetryable())
    }

    @Test
    fun `Given ServerError, When isRetryable, Then returns true`() {
        val error = WalletActivationError.ServerError(500, "Internal error")
        assertTrue(error.isRetryable())
    }

    @Test
    fun `Given ChallengeRateLimited, When isRetryable, Then returns true`() {
        val error = WalletActivationError.ChallengeRateLimited(60)
        assertTrue(error.isRetryable())
    }

    @Test
    fun `Given CryptographicFailure, When isRetryable, Then returns true`() {
        val error = WalletActivationError.CryptographicFailure("key gen", Exception())
        assertTrue(error.isRetryable())
    }

    @Test
    fun `Given WalletAlreadyExists, When isRetryable, Then returns true`() {
        val error = WalletActivationError.WalletAlreadyExists()
        assertTrue(error.isRetryable())
    }

    @Test
    fun `Given DeviceSecurityInsufficient, When isRetryable, Then returns false`() {
        val error = WalletActivationError.DeviceSecurityInsufficient(listOf("HSM"), "LOW")
        assertFalse(error.isRetryable())
    }

    @Test
    fun `Given AuthenticationFailure, When isRetryable, Then returns false`() {
        val error = WalletActivationError.AuthenticationFailure("Token expired")
        assertFalse(error.isRetryable())
    }

    @Test
    fun `Given ChallengeExpired, When isRetryable, Then returns false`() {
        val error = WalletActivationError.ChallengeExpired("abc")
        assertFalse(error.isRetryable())
    }

    @Test
    fun `Given ServerRejection with 5xx, When isRetryable, Then returns true`() {
        val error = WalletActivationError.ServerRejection(502, "Bad Gateway")
        assertTrue(error.isRetryable())
    }

    @Test
    fun `Given ServerRejection with 4xx, When isRetryable, Then returns false`() {
        val error = WalletActivationError.ServerRejection(400, "Bad Request")
        assertFalse(error.isRetryable())
    }

    // endregion

    // region shouldAutoRetry()

    @Test
    fun `Given ChallengeExpired, When shouldAutoRetry, Then returns true`() {
        val error = WalletActivationError.ChallengeExpired("abc123")
        assertTrue(error.shouldAutoRetry())
    }

    @Test
    fun `Given ChallengeNotFound, When shouldAutoRetry, Then returns true`() {
        val error = WalletActivationError.ChallengeNotFound("xyz789")
        assertTrue(error.shouldAutoRetry())
    }

    @Test
    fun `Given NetworkFailure, When shouldAutoRetry, Then returns false`() {
        val error = WalletActivationError.NetworkFailure(cause = Exception())
        assertFalse(error.shouldAutoRetry())
    }

    @Test
    fun `Given ChallengeRateLimited, When shouldAutoRetry, Then returns false`() {
        val error = WalletActivationError.ChallengeRateLimited(60)
        assertFalse(error.shouldAutoRetry())
    }

    @Test
    fun `Given ServerError, When shouldAutoRetry, Then returns false`() {
        val error = WalletActivationError.ServerError(500, "Error")
        assertFalse(error.shouldAutoRetry())
    }

    @Test
    fun `Given WalletAlreadyExists, When shouldAutoRetry, Then returns false`() {
        val error = WalletActivationError.WalletAlreadyExists()
        assertFalse(error.shouldAutoRetry())
    }

    // endregion

    // region isPermanent()

    @Test
    fun `Given DeviceSecurityInsufficient, When isPermanent, Then returns true`() {
        val error = WalletActivationError.DeviceSecurityInsufficient(listOf("HSM"), "LOW")
        assertTrue(error.isPermanent())
    }

    @Test
    fun `Given AttestationRejected with device issue, When isPermanent, Then returns true`() {
        val error = WalletActivationError.AttestationRejected("Security level too low", isDeviceIssue = true)
        assertTrue(error.isPermanent())
    }

    @Test
    fun `Given AttestationRejected without device issue, When isPermanent, Then returns false`() {
        val error = WalletActivationError.AttestationRejected("Certificate chain invalid", isDeviceIssue = false)
        assertFalse(error.isPermanent())
    }

    @Test
    fun `Given AuthenticationFailure, When isPermanent, Then returns true`() {
        val error = WalletActivationError.AuthenticationFailure("Token revoked")
        assertTrue(error.isPermanent())
    }

    @Test
    fun `Given NetworkFailure, When isPermanent, Then returns false`() {
        val error = WalletActivationError.NetworkFailure(cause = Exception())
        assertFalse(error.isPermanent())
    }

    @Test
    fun `Given ServerError, When isPermanent, Then returns false`() {
        val error = WalletActivationError.ServerError(500, "Error")
        assertFalse(error.isPermanent())
    }

    @Test
    fun `Given WalletAlreadyExists, When isPermanent, Then returns false`() {
        val error = WalletActivationError.WalletAlreadyExists()
        assertFalse(error.isPermanent())
    }

    // endregion

    // region Throwable.toWalletActivationError()

    @Test
    fun `Given WalletActivationError, When toWalletActivationError, Then returns same instance`() {
        val original = WalletActivationError.NetworkFailure(cause = Exception("test"))

        val result = original.toWalletActivationError()

        assertSame(original, result)
    }

    @Test
    fun `Given exception with timeout in message, When toWalletActivationError, Then returns TimeoutError`() {
        val exception = Exception("Connection timeout after 30 seconds")

        val error = exception.toWalletActivationError()

        assertTrue(error is WalletActivationError.TimeoutError)
    }

    @Test
    fun `Given exception with authentication in message, When toWalletActivationError, Then returns AuthenticationFailure`() {
        val exception = Exception("User not authenticated")

        val error = exception.toWalletActivationError()

        assertTrue(error is WalletActivationError.AuthenticationFailure)
    }

    @Test
    fun `Given exception with keystore in message, When toWalletActivationError, Then returns CryptographicFailure`() {
        val exception = Exception("Android keystore unavailable")

        val error = exception.toWalletActivationError()

        assertTrue(error is WalletActivationError.CryptographicFailure)
    }

    @Test
    fun `Given exception with HTTP 500 in message, When toWalletActivationError, Then returns ServerError`() {
        val exception = Exception("Request failed: HTTP 500")

        val error = exception.toWalletActivationError()

        assertTrue(error is WalletActivationError.ServerError)
        assertEquals(500, (error as WalletActivationError.ServerError).httpCode)
    }

    @Test
    fun `Given generic exception, When toWalletActivationError, Then returns UnexpectedError`() {
        val exception = Exception("Something random happened")

        val error = exception.toWalletActivationError()

        assertTrue(error is WalletActivationError.UnexpectedError)
        assertSame(exception, error.cause)
    }

    // endregion

    // region getRetryDelaySeconds()

    @Test
    fun `Given ChallengeRateLimited, When getRetryDelaySeconds, Then returns retry seconds`() {
        val error = WalletActivationError.ChallengeRateLimited(45)

        val delay = error.getRetryDelaySeconds()

        assertEquals(45, delay)
    }

    @Test
    fun `Given other error, When getRetryDelaySeconds, Then returns null`() {
        val error = WalletActivationError.NetworkFailure(cause = Exception())

        val delay = error.getRetryDelaySeconds()

        assertEquals(null, delay)
    }

    // endregion
}
