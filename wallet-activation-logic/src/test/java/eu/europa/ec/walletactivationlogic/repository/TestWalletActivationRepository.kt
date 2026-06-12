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

package eu.europa.ec.walletactivationlogic.repository

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.authenticationlogic.controller.storage.WalletRecoveryChallengeController
import eu.europa.ec.businesslogic.model.DeviceInfo
import eu.europa.ec.businesslogic.model.error.WalletActivationError
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.ApiResponse
import eu.europa.ec.networklogic.model.response.AttestationChallengeResponse
import eu.europa.ec.networklogic.model.response.WalletActivationResponse
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import io.github.jan.supabase.SupabaseClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.contains
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.cert.Certificate

/**
 * Unit tests for [WalletActivationRepositoryImpl].
 *
 * Tests verify that HTTP error responses from the API are correctly mapped
 * to typed [WalletActivationError] subtypes through the repository's
 * internal [parseHttpError] method.
 *
 * Uses Robolectric because the repository calls [android.util.Base64]
 * when encoding certificate chains.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class TestWalletActivationRepository {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var apiClient: ApiClient

    @Mock
    private lateinit var supabaseClient: SupabaseClient

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var walletRecoveryChallengeController: WalletRecoveryChallengeController

    @Mock
    private lateinit var mockCertificate: Certificate

    private lateinit var repository: WalletActivationRepositoryImpl
    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(mockCertificate.encoded).thenReturn(byteArrayOf(1, 2, 3))
        repository = TestableWalletActivationRepository(
            apiClient = apiClient,
            supabaseClient = supabaseClient,
            logController = logController,
            walletRecoveryChallengeController = walletRecoveryChallengeController,
            mockAuthToken = MOCK_AUTH_TOKEN
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    // region activateWallet error mapping

    @Test
    fun `Given activation succeeds, When activateWallet is called, Then WUA and device details are not logged`() =
        coroutineRule.runTest {
            whenever(apiClient.activateWallet(any(), eq(MOCK_AUTH_TOKEN)))
                .thenReturn(
                    ApiResponse.Success(
                        body = WalletActivationResponse(wua = REDACTION_TEST_VALUE)
                    )
                )

            val result = repository.activateWallet(
                attestationChain = arrayOf(mockCertificate),
                challengeId = "test-challenge-id",
                deviceInfo = MOCK_DEVICE_INFO,
                pushToken = "fcm-redaction-test-value"
            )

            assertTrue("Result should be success", result.isSuccess)
            verify(logController, never()).d(eq("WalletActivation"), contains(REDACTION_TEST_VALUE))
            verify(logController, never()).d(eq("WalletActivation"), contains("device-123"))
        }

    @Test
    fun `Given prepared recovery challenge exists, When getAttestationChallenge is called, Then cached challenge is returned without API call`() =
        coroutineRule.runTest {
            val cachedChallenge = AttestationChallengeResponse(
                challengeId = "prepared-challenge-id",
                challenge = "aabbccdd",
                expiresAt = "2026-04-17T10:20:00Z",
                ttlSeconds = 300
            )
            whenever(walletRecoveryChallengeController.consumePreparedChallenge())
                .thenReturn(cachedChallenge)

            val result = repository.getAttestationChallenge()

            assertTrue("Result should be success", result.isSuccess)
            assertEquals(cachedChallenge, result.getOrThrow())
            verify(walletRecoveryChallengeController).consumePreparedChallenge()
            verify(apiClient, never()).getAttestationChallenge(any())
        }

    @Test
    fun `Given API returns 409, When activateWallet is called, Then result contains WalletAlreadyExists`() =
        coroutineRule.runTest {
            // Given
            whenever(apiClient.activateWallet(any(), eq(MOCK_AUTH_TOKEN)))
                .thenReturn(ApiResponse.Error(code = 409, message = "Conflict"))

            // When
            val result = repository.activateWallet(
                attestationChain = arrayOf(mockCertificate),
                challengeId = "test-challenge-id",
                deviceInfo = MOCK_DEVICE_INFO,
                pushToken = "fcm-redaction-test-value"
            )

            // Then
            assertTrue("Result should be failure", result.isFailure)
            val error = result.exceptionOrNull()
            assertTrue(
                "Error should be WalletAlreadyExists but was ${error?.javaClass?.simpleName}",
                error is WalletActivationError.WalletAlreadyExists
            )
            assertEquals(409, (error as WalletActivationError.WalletAlreadyExists).httpCode)
        }

    // endregion

    // region Testable subclass

    /**
     * Testable subclass that bypasses Supabase auth extension properties.
     * Follows the same pattern as TestableActionsRepository in dashboard-feature.
     */
    private class TestableWalletActivationRepository(
        apiClient: ApiClient,
        supabaseClient: SupabaseClient,
        logController: LogController,
        walletRecoveryChallengeController: WalletRecoveryChallengeController,
        private val mockAuthToken: String?
    ) : WalletActivationRepositoryImpl(
        supabaseClient = supabaseClient,
        api = apiClient,
        logController = logController,
        walletRecoveryChallengeController = walletRecoveryChallengeController
    ) {
        override suspend fun getAuthToken(): String? = mockAuthToken
    }

    // endregion

    companion object {
        private const val MOCK_AUTH_TOKEN = "test-bearer-token"
        private const val REDACTION_TEST_VALUE = "wua-redaction-test-value"

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
