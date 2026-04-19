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

package eu.europa.ec.authenticationlogic.controller.storage

import eu.europa.ec.authenticationlogic.storage.LocalAuthKeys
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.networklogic.model.response.AttestationChallengeResponse
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TestWalletRecoveryChallengeController {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var prefsController: PrefsControllerV2

    private lateinit var controller: WalletRecoveryChallengeControllerImpl
    private lateinit var closeable: AutoCloseable

    private val json: Json = Json { ignoreUnknownKeys = true }

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        controller = WalletRecoveryChallengeControllerImpl(prefsController)
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `Given valid prepared challenge, When peekPreparedChallenge is called, Then it is returned without clearing cache`() =
        coroutineRule.runTest {
            val cachedChallenge = AttestationChallengeResponse(
                challengeId = "prepared-challenge-id",
                challenge = "aabbccdd",
                expiresAt = "2099-04-17T10:20:00Z",
                ttlSeconds = 300
            )
            whenever(prefsController.contains(LocalAuthKeys.PREPARED_WALLET_RECOVERY_CHALLENGE))
                .thenReturn(true)
            whenever(
                prefsController.getString(
                    LocalAuthKeys.PREPARED_WALLET_RECOVERY_CHALLENGE,
                    ""
                )
            ).thenReturn(json.encodeToString(cachedChallenge))

            val result = controller.peekPreparedChallenge()

            assertEquals(cachedChallenge, result)
            verify(prefsController, never()).clear(LocalAuthKeys.PREPARED_WALLET_RECOVERY_CHALLENGE)
        }

    @Test
    fun `Given expired prepared challenge, When peekPreparedChallenge is called, Then cache is cleared and null is returned`() =
        coroutineRule.runTest {
            val expiredChallenge = AttestationChallengeResponse(
                challengeId = "expired-challenge-id",
                challenge = "deadbeef",
                expiresAt = "2020-04-17T10:20:00Z",
                ttlSeconds = 300
            )
            whenever(prefsController.contains(LocalAuthKeys.PREPARED_WALLET_RECOVERY_CHALLENGE))
                .thenReturn(true)
            whenever(
                prefsController.getString(
                    LocalAuthKeys.PREPARED_WALLET_RECOVERY_CHALLENGE,
                    ""
                )
            ).thenReturn(json.encodeToString(expiredChallenge))

            val result = controller.peekPreparedChallenge()

            assertNull(result)
            verify(prefsController).clear(LocalAuthKeys.PREPARED_WALLET_RECOVERY_CHALLENGE)
        }
}
