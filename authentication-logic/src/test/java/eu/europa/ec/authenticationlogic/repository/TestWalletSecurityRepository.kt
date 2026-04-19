/*
 * Copyright (c) 2026 European Commission
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

package eu.europa.ec.authenticationlogic.repository

import eu.europa.ec.authenticationlogic.controller.storage.WalletRecoveryChallengeController
import eu.europa.ec.authenticationlogic.model.WalletSecurityEventType
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.ApiResponse
import eu.europa.ec.networklogic.model.request.WalletRecoveryPrepareRequest
import eu.europa.ec.networklogic.model.request.WalletSecurityIncidentRequest
import eu.europa.ec.networklogic.model.response.AttestationChallengeResponse
import eu.europa.ec.networklogic.model.response.WalletRecoveryPrepareResponse
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import io.github.jan.supabase.SupabaseClient
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TestWalletSecurityRepository {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var apiClient: ApiClient

    @Mock
    private lateinit var supabaseClient: SupabaseClient

    @Mock
    private lateinit var walletRecoveryChallengeController: WalletRecoveryChallengeController

    @Mock
    private lateinit var logController: LogController

    private lateinit var repository: WalletSecurityRepositoryImpl
    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        repository = TestableWalletSecurityRepository(
            apiClient = apiClient,
            supabaseClient = supabaseClient,
            walletRecoveryChallengeController = walletRecoveryChallengeController,
            logController = logController,
            mockAuthToken = MOCK_AUTH_TOKEN
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `Given incident reporting succeeds, When reportIncident is called, Then backend request contains the security signals`() =
        coroutineRule.runTest {
            whenever(apiClient.reportWalletSecurityIncident(any(), eq(MOCK_AUTH_TOKEN)))
                .thenReturn(ApiResponse.Success(Unit))

            val result = repository.reportIncident(
                eventType = WalletSecurityEventType.LocalAuthTamperDetected,
                signals = listOf(
                    "local_auth_integrity_failure",
                    "unexpected_missing_auth_material"
                )
            )

            assertTrue(result.isSuccess)

            val requestCaptor = argumentCaptor<WalletSecurityIncidentRequest>()
            verify(apiClient).reportWalletSecurityIncident(
                requestCaptor.capture(),
                eq(MOCK_AUTH_TOKEN)
            )
            val capturedRequest: WalletSecurityIncidentRequest = requestCaptor.firstValue
            assertEquals(
                WalletSecurityEventType.LocalAuthTamperDetected.wireName,
                capturedRequest.eventType
            )
            assertEquals(2, capturedRequest.clientStateVersion)
            assertNotNull(capturedRequest.detectedAt)
            val signals = capturedRequest.details
                ?.get("signals")
                ?.jsonArray
                ?.map { it.jsonPrimitive.content }
            assertEquals(
                listOf(
                    "local_auth_integrity_failure",
                    "unexpected_missing_auth_material"
                ),
                signals
            )
        }

    @Test
    fun `Given recovery preparation succeeds, When prepareWalletRecovery is called, Then attestation challenge is cached for reactivation`() =
        coroutineRule.runTest {
            val response = WalletRecoveryPrepareResponse(
                success = true,
                challengeId = "prepared-challenge-id",
                challenge = "aabbccdd",
                expiresAt = "2026-04-17T10:20:00Z",
                ttlSeconds = 300,
                revokedWuaCount = 1,
                nextAction = "wallet_reactivation"
            )
            whenever(apiClient.prepareWalletRecovery(any(), eq(MOCK_AUTH_TOKEN)))
                .thenReturn(ApiResponse.Success(response))

            val result = repository.prepareWalletRecovery(
                evidence = listOf("offline_recovery_unavailable")
            )

            assertTrue(result.isSuccess)

            val requestCaptor = argumentCaptor<WalletRecoveryPrepareRequest>()
            verify(apiClient).prepareWalletRecovery(
                requestCaptor.capture(),
                eq(MOCK_AUTH_TOKEN)
            )
            assertEquals(
                listOf("offline_recovery_unavailable"),
                requestCaptor.firstValue.evidence
            )
            verify(walletRecoveryChallengeController).cachePreparedChallenge(
                AttestationChallengeResponse(
                    challengeId = "prepared-challenge-id",
                    challenge = "aabbccdd",
                    expiresAt = "2026-04-17T10:20:00Z",
                    ttlSeconds = 300
                )
            )
        }

    @Test
    fun `Given valid cached recovery challenge exists, When prepareWalletRecovery is called, Then backend preparation is skipped`() =
        coroutineRule.runTest {
            whenever(walletRecoveryChallengeController.peekPreparedChallenge())
                .thenReturn(
                    AttestationChallengeResponse(
                        challengeId = "cached-challenge-id",
                        challenge = "deadbeef",
                        expiresAt = "2026-04-17T10:20:00Z",
                        ttlSeconds = 300
                    )
                )

            val result = repository.prepareWalletRecovery(
                evidence = listOf("retry_after_push_failure")
            )

            assertTrue(result.isSuccess)
            verify(walletRecoveryChallengeController).peekPreparedChallenge()
            verify(apiClient, never()).prepareWalletRecovery(any(), any())
            verify(walletRecoveryChallengeController, never()).cachePreparedChallenge(any())
        }

    private class TestableWalletSecurityRepository(
        apiClient: ApiClient,
        supabaseClient: SupabaseClient,
        walletRecoveryChallengeController: WalletRecoveryChallengeController,
        logController: LogController,
        private val mockAuthToken: String?
    ) : WalletSecurityRepositoryImpl(
        apiClient = apiClient,
        supabaseClient = supabaseClient,
        walletRecoveryChallengeController = walletRecoveryChallengeController,
        logController = logController
    ) {
        override suspend fun getAuthToken(): String? = mockAuthToken
    }

    private companion object {
        private const val MOCK_AUTH_TOKEN = "test-access-token"
    }
}
