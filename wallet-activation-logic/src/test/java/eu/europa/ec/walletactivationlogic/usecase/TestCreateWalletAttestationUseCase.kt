/*
 * Copyright (c) 2025 European Commission
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

package eu.europa.ec.walletactivationlogic.usecase

import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.model.DeviceInfo
import eu.europa.ec.businesslogic.model.error.WalletActivationError
import eu.europa.ec.networklogic.model.response.AttestationChallengeResponse
import eu.europa.ec.networklogic.model.response.WalletActivationResponse
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.walletactivationlogic.repository.WalletActivationRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.cert.Certificate

/**
 * Unit tests for [CreateWalletAttestationUseCaseImpl].
 *
 * These tests verify the WUA (Wallet Unit Attestation) creation flow including:
 * - Challenge fetching and hex-to-bytes conversion
 * - Key pair generation with attestation
 * - Cleanup on failure (critical for ARF compliance)
 * - Error propagation and typing
 *
 * SECURITY-CRITICAL: Tests verify that WUA keys are cleaned up on any failure
 * to ensure fresh attestation challenges are always used (prevents replay attacks).
 */
class TestCreateWalletAttestationUseCase {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var cryptoController: CryptoController

    @Mock
    private lateinit var walletActivationRepository: WalletActivationRepository

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var mockCertificate: Certificate

    private lateinit var useCase: CreateWalletAttestationUseCaseImpl
    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        useCase = CreateWalletAttestationUseCaseImpl(
            cryptoController = cryptoController,
            walletActivationRepository = walletActivationRepository,
            logController = logController
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    // region Challenge Fetch Failures

    @Test
    fun `Given challenge fetch fails with WalletActivationError, When invoke is called, Then error type is preserved`() =
        coroutineRule.runTest {
            // Given
            val expectedError = WalletActivationError.NetworkFailure(
                httpCode = 503,
                cause = Exception("Service unavailable")
            )
            whenever(walletActivationRepository.getAttestationChallenge())
                .thenReturn(Result.failure(expectedError))

            // When
            val result = useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then
            assertTrue("Result should be failure", result.isFailure)
            assertEquals(expectedError, result.exceptionOrNull())
            verify(cryptoController, never()).generateWuaKeyPair(any())
        }

    @Test
    fun `Given challenge fetch fails with generic exception, When invoke is called, Then wrapped to UnexpectedError`() =
        coroutineRule.runTest {
            // Given
            val genericException = RuntimeException("Database error")
            whenever(walletActivationRepository.getAttestationChallenge())
                .thenReturn(Result.failure(genericException))

            // When
            val result = useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then
            assertTrue("Result should be failure", result.isFailure)
            val error = result.exceptionOrNull()
            assertTrue("Should be UnexpectedError", error is WalletActivationError.UnexpectedError)
            assertEquals(genericException, (error as WalletActivationError.UnexpectedError).cause)
        }

    // endregion

    // region Challenge Format Validation

    @Test
    fun `Given invalid hex challenge format, When invoke is called, Then returns ServerRejection with 400`() =
        coroutineRule.runTest {
            // Given - "ZZ" is not valid hex
            val invalidHexChallenge = AttestationChallengeResponse(
                challengeId = "test-id",
                challenge = "ZZ1234",
                expiresAt = "2025-12-31T23:59:59Z",
                ttlSeconds = 300
            )
            whenever(walletActivationRepository.getAttestationChallenge())
                .thenReturn(Result.success(invalidHexChallenge))

            // When
            val result = useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then
            assertTrue("Result should be failure", result.isFailure)
            val error = result.exceptionOrNull()
            assertTrue("Should be ServerRejection", error is WalletActivationError.ServerRejection)
            assertEquals(400, (error as WalletActivationError.ServerRejection).httpCode)
            verify(cryptoController, never()).generateWuaKeyPair(any())
        }

    @Test
    fun `Given odd-length hex challenge, When invoke is called, Then returns ServerRejection`() =
        coroutineRule.runTest {
            // Given - Odd length hex string is invalid
            val oddLengthChallenge = AttestationChallengeResponse(
                challengeId = "test-id",
                challenge = "abc", // 3 characters - invalid
                expiresAt = "2025-12-31T23:59:59Z",
                ttlSeconds = 300
            )
            whenever(walletActivationRepository.getAttestationChallenge())
                .thenReturn(Result.success(oddLengthChallenge))

            // When
            val result = useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then
            assertTrue("Result should be failure", result.isFailure)
            val error = result.exceptionOrNull()
            assertTrue("Should be ServerRejection", error is WalletActivationError.ServerRejection)
            assertEquals(400, (error as WalletActivationError.ServerRejection).httpCode)
        }

    // endregion

    // region Key Generation Failures with Cleanup

    @Test
    fun `Given key generation returns null, When invoke is called, Then cleanup is performed and CryptographicFailure returned`() =
        coroutineRule.runTest {
            // Given
            setupValidChallengeResponse()
            whenever(cryptoController.generateWuaKeyPair(any())).thenReturn(null)
            whenever(cryptoController.deleteWuaKey()).thenReturn(true)

            // When
            val result = useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then
            assertTrue("Result should be failure", result.isFailure)
            val error = result.exceptionOrNull()
            assertTrue("Should be CryptographicFailure", error is WalletActivationError.CryptographicFailure)

            // CRITICAL: Verify cleanup was called
            verify(cryptoController).deleteWuaKey()
        }

    @Test
    fun `Given key generation throws exception, When invoke is called, Then cleanup is performed and CryptographicFailure returned`() =
        coroutineRule.runTest {
            // Given
            setupValidChallengeResponse()
            val keyGenException = SecurityException("Keystore unavailable")
            whenever(cryptoController.generateWuaKeyPair(any())).thenThrow(keyGenException)
            whenever(cryptoController.deleteWuaKey()).thenReturn(true)

            // When
            val result = useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then
            assertTrue("Result should be failure", result.isFailure)
            val error = result.exceptionOrNull()
            assertTrue("Should be CryptographicFailure", error is WalletActivationError.CryptographicFailure)
            assertEquals(keyGenException, (error as WalletActivationError.CryptographicFailure).cause)

            // CRITICAL: Verify cleanup was called
            verify(cryptoController).deleteWuaKey()
        }

    // endregion

    // region Activation Failure with Cleanup (CRITICAL BUG FIX TEST)

    @Test
    fun `Given activation fails after key generation, When invoke is called, Then WUA key is cleaned up`() =
        coroutineRule.runTest {
            // Given - This is the critical test for the bug we fixed
            setupValidChallengeResponse()
            val certChain = arrayOf(mockCertificate)
            whenever(cryptoController.generateWuaKeyPair(any())).thenReturn(certChain)

            val activationError = WalletActivationError.ChallengeExpired("test-challenge-id")
            whenever(walletActivationRepository.activateWallet(
                any(), any(), any(), any(), any()
            )).thenReturn(Result.failure(activationError))

            whenever(cryptoController.deleteWuaKey()).thenReturn(true)

            // When
            val result = useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then
            assertTrue("Result should be failure", result.isFailure)

            // CRITICAL: Verify cleanup on activation failure
            // This ensures next retry gets a fresh attestation with new challenge
            verify(cryptoController).deleteWuaKey()
        }

    @Test
    fun `Given auto-retry errors occur, When activation fails, Then cleanup still happens`() =
        coroutineRule.runTest {
            // Given
            setupValidChallengeResponse()
            val certChain = arrayOf(mockCertificate)
            whenever(cryptoController.generateWuaKeyPair(any())).thenReturn(certChain)

            // ChallengeNotFound should trigger auto-retry, but we still need cleanup
            val challengeNotFound = WalletActivationError.ChallengeNotFound("test-id")
            whenever(walletActivationRepository.activateWallet(
                any(), any(), any(), any(), any()
            )).thenReturn(Result.failure(challengeNotFound))

            whenever(cryptoController.deleteWuaKey()).thenReturn(true)

            // When
            val result = useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then
            assertTrue("Result should be failure", result.isFailure)
            verify(cryptoController).deleteWuaKey()
        }

    @Test
    fun `Given deleteWuaKey fails during cleanup, When error occurs, Then continues gracefully`() =
        coroutineRule.runTest {
            // Given
            setupValidChallengeResponse()
            whenever(cryptoController.generateWuaKeyPair(any())).thenReturn(null)
            // Cleanup fails but should not throw
            whenever(cryptoController.deleteWuaKey()).thenReturn(false)

            // When - Should not throw even if cleanup fails
            val result = useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then
            assertTrue("Result should be failure", result.isFailure)
            verify(cryptoController).deleteWuaKey()
        }

    // endregion

    // region Happy Path

    @Test
    fun `Given full happy path, When invoke is called, Then returns success and key is kept`() =
        coroutineRule.runTest {
            // Given
            setupValidChallengeResponse()
            val certChain = arrayOf(mockCertificate)
            whenever(cryptoController.generateWuaKeyPair(any())).thenReturn(certChain)

            val successResponse = WalletActivationResponse(wua = "wua-token-123")
            whenever(walletActivationRepository.activateWallet(
                any(), any(), any(), any(), any()
            )).thenReturn(Result.success(successResponse))

            // When
            val result = useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then
            assertTrue("Result should be success", result.isSuccess)
            assertEquals(successResponse, result.getOrNull())

            // Key should NOT be deleted on success
            verify(cryptoController, never()).deleteWuaKey()
        }

    // endregion

    // region Correct Parameter Passing

    @Test
    fun `Given valid hex challenge, When invoke is called, Then challenge bytes are passed correctly`() =
        coroutineRule.runTest {
            // Given - "48656c6c6f" = [72, 101, 108, 108, 111] = "Hello" in ASCII
            val hexChallenge = AttestationChallengeResponse(
                challengeId = "test-id",
                challenge = "48656c6c6f",
                expiresAt = "2025-12-31T23:59:59Z",
                ttlSeconds = 300
            )
            whenever(walletActivationRepository.getAttestationChallenge())
                .thenReturn(Result.success(hexChallenge))

            val certChain = arrayOf(mockCertificate)
            whenever(cryptoController.generateWuaKeyPair(any())).thenReturn(certChain)

            val successResponse = WalletActivationResponse(wua = "wua-token-123")
            whenever(walletActivationRepository.activateWallet(
                any(), any(), any(), any(), any()
            )).thenReturn(Result.success(successResponse))

            // When
            useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then - Verify correct bytes were passed: [72, 101, 108, 108, 111]
            val expectedBytes = byteArrayOf(72, 101, 108, 108, 111)
            verify(cryptoController).generateWuaKeyPair(eq(expectedBytes))
        }

    @Test
    fun `Given challenge response, When activate is called, Then challengeId is passed correctly`() =
        coroutineRule.runTest {
            // Given
            val challengeId = "unique-challenge-id-123"
            val challenge = AttestationChallengeResponse(
                challengeId = challengeId,
                challenge = "aabbccdd",
                expiresAt = "2025-12-31T23:59:59Z",
                ttlSeconds = 300
            )
            whenever(walletActivationRepository.getAttestationChallenge())
                .thenReturn(Result.success(challenge))

            val certChain = arrayOf(mockCertificate)
            whenever(cryptoController.generateWuaKeyPair(any())).thenReturn(certChain)

            val successResponse = WalletActivationResponse(wua = "wua-token-123")
            whenever(walletActivationRepository.activateWallet(
                any(), any(), eq(challengeId), any(), any()
            )).thenReturn(Result.success(successResponse))

            // When
            useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then
            verify(walletActivationRepository).activateWallet(
                any(), any(), eq(challengeId), any(), any()
            )
        }

    @Test
    fun `Given device info and push token, When invoke is called, Then they are propagated correctly`() =
        coroutineRule.runTest {
            // Given
            setupValidChallengeResponse()
            val certChain = arrayOf(mockCertificate)
            whenever(cryptoController.generateWuaKeyPair(any())).thenReturn(certChain)

            val successResponse = WalletActivationResponse(wua = "wua-token-123")
            whenever(walletActivationRepository.activateWallet(
                any(), any(), any(), eq(MOCK_DEVICE_INFO), eq(MOCK_PUSH_TOKEN)
            )).thenReturn(Result.success(successResponse))

            // When
            useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then
            verify(walletActivationRepository).activateWallet(
                any(), any(), any(), eq(MOCK_DEVICE_INFO), eq(MOCK_PUSH_TOKEN)
            )
        }

    // endregion

    // region Hex Conversion Verification

    @Test
    fun `Given hex string 48656c6c6f, When converted, Then produces correct byte array for Hello`() =
        coroutineRule.runTest {
            // Given - "48656c6c6f" = "Hello" in ASCII
            val hexChallenge = AttestationChallengeResponse(
                challengeId = "test-id",
                challenge = "48656c6c6f",
                expiresAt = "2025-12-31T23:59:59Z",
                ttlSeconds = 300
            )
            whenever(walletActivationRepository.getAttestationChallenge())
                .thenReturn(Result.success(hexChallenge))

            val certChain = arrayOf(mockCertificate)
            whenever(cryptoController.generateWuaKeyPair(any())).thenReturn(certChain)

            whenever(walletActivationRepository.activateWallet(
                any(), any(), any(), any(), any()
            )).thenReturn(Result.success(WalletActivationResponse(wua = "wua-token")))

            // When
            useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then - Verify [72, 101, 108, 108, 111] was passed (H=72, e=101, l=108, l=108, o=111)
            verify(cryptoController).generateWuaKeyPair(eq(byteArrayOf(72, 101, 108, 108, 111)))
        }

    // endregion

    // region 409 Conflict Auto-Recovery

    @Test
    fun `Given activation returns 409, When invoke is called, Then deletes old WUA and retries successfully`() =
        coroutineRule.runTest {
            // Given
            val challenge1 = AttestationChallengeResponse(
                challengeId = "challenge-1",
                challenge = "aabbccdd",
                expiresAt = "2025-12-31T23:59:59Z",
                ttlSeconds = 300
            )
            val challenge2 = AttestationChallengeResponse(
                challengeId = "challenge-2",
                challenge = "eeff0011",
                expiresAt = "2025-12-31T23:59:59Z",
                ttlSeconds = 300
            )
            whenever(walletActivationRepository.getAttestationChallenge())
                .thenReturn(Result.success(challenge1))
                .thenReturn(Result.success(challenge2))

            val certChain = arrayOf(mockCertificate)
            whenever(cryptoController.generateWuaKeyPair(any())).thenReturn(certChain)
            whenever(cryptoController.deleteWuaKey()).thenReturn(true)

            val conflictError = WalletActivationError.WalletAlreadyExists()
            val successResponse = WalletActivationResponse(wua = "wua-token-123")
            whenever(walletActivationRepository.activateWallet(any(), any(), any(), any(), any()))
                .thenReturn(Result.failure(conflictError))
                .thenReturn(Result.success(successResponse))

            whenever(walletActivationRepository.deleteWalletActivation())
                .thenReturn(Result.success(Unit))

            // When
            val result = useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then
            assertTrue("Result should be success after conflict recovery", result.isSuccess)
            assertEquals(successResponse, result.getOrNull())
            verify(walletActivationRepository).deleteWalletActivation()
            verify(walletActivationRepository, times(2)).activateWallet(any(), any(), any(), any(), any())
        }

    @Test
    fun `Given activation returns 409 and delete fails, When invoke is called, Then propagates original error`() =
        coroutineRule.runTest {
            // Given
            setupValidChallengeResponse()
            val certChain = arrayOf(mockCertificate)
            whenever(cryptoController.generateWuaKeyPair(any())).thenReturn(certChain)
            whenever(cryptoController.deleteWuaKey()).thenReturn(true)

            val conflictError = WalletActivationError.WalletAlreadyExists()
            whenever(walletActivationRepository.activateWallet(any(), any(), any(), any(), any()))
                .thenReturn(Result.failure(conflictError))

            whenever(walletActivationRepository.deleteWalletActivation())
                .thenReturn(Result.failure(WalletActivationError.ServerError(500, "Internal error")))

            // When
            val result = useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then
            assertTrue("Result should be failure", result.isFailure)
            assertTrue("Should be WalletAlreadyExists", result.exceptionOrNull() is WalletActivationError.WalletAlreadyExists)
            // Should not retry activation since delete failed
            verify(walletActivationRepository, times(1)).activateWallet(any(), any(), any(), any(), any())
        }

    @Test
    fun `Given activation returns 409 twice, When invoke is called, Then does not retry more than once`() =
        coroutineRule.runTest {
            // Given
            val challenge1 = AttestationChallengeResponse(
                challengeId = "challenge-1",
                challenge = "aabbccdd",
                expiresAt = "2025-12-31T23:59:59Z",
                ttlSeconds = 300
            )
            val challenge2 = AttestationChallengeResponse(
                challengeId = "challenge-2",
                challenge = "eeff0011",
                expiresAt = "2025-12-31T23:59:59Z",
                ttlSeconds = 300
            )
            whenever(walletActivationRepository.getAttestationChallenge())
                .thenReturn(Result.success(challenge1))
                .thenReturn(Result.success(challenge2))

            val certChain = arrayOf(mockCertificate)
            whenever(cryptoController.generateWuaKeyPair(any())).thenReturn(certChain)
            whenever(cryptoController.deleteWuaKey()).thenReturn(true)

            val conflictError = WalletActivationError.WalletAlreadyExists()
            // Both activate calls return 409
            whenever(walletActivationRepository.activateWallet(any(), any(), any(), any(), any()))
                .thenReturn(Result.failure(conflictError))

            whenever(walletActivationRepository.deleteWalletActivation())
                .thenReturn(Result.success(Unit))

            // When
            val result = useCase.invoke(MOCK_DEVICE_INFO, MOCK_PUSH_TOKEN)

            // Then
            assertTrue("Result should be failure", result.isFailure)
            assertTrue("Should be WalletAlreadyExists", result.exceptionOrNull() is WalletActivationError.WalletAlreadyExists)
            // Should have tried activation exactly twice (initial + one retry)
            verify(walletActivationRepository, times(2)).activateWallet(any(), any(), any(), any(), any())
            // Should have deleted old WUA only once (on first 409)
            verify(walletActivationRepository, times(1)).deleteWalletActivation()
        }

    // endregion

    // region Helper Methods

    private suspend fun setupValidChallengeResponse() {
        val validChallenge = AttestationChallengeResponse(
            challengeId = MOCK_CHALLENGE_ID,
            challenge = MOCK_HEX_CHALLENGE,
            expiresAt = "2025-12-31T23:59:59Z",
            ttlSeconds = 300
        )
        whenever(walletActivationRepository.getAttestationChallenge())
            .thenReturn(Result.success(validChallenge))
    }

    // endregion

    companion object {
        private const val MOCK_CHALLENGE_ID = "test-challenge-id"
        private const val MOCK_HEX_CHALLENGE = "aabbccdd" // Valid hex
        private const val MOCK_PUSH_TOKEN = "fcm-token-123"

        private val MOCK_DEVICE_INFO = DeviceInfo(
            deviceId = "device-123",
            deviceName = "Test Device",
            deviceModel = "Pixel 7",
            deviceOs = "Android 14",
            deviceOsVersion = "34",
            securityPatchLevel = "2025-01-01",
            hasSecureElement = true,
            hasHardwareKeystore = true,
            hasStrongBox = true,
            attestationSupported = true,
            deviceVerifiedBoot = true,
            playProtectVerified = true,
            hasBiometricHardware = true
        )
    }
}
