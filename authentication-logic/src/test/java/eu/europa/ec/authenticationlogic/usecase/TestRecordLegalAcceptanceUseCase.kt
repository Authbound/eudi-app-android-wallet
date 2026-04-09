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

package eu.europa.ec.authenticationlogic.usecase

import eu.europa.ec.authenticationlogic.model.LegalAcceptanceSnapshot
import eu.europa.ec.authenticationlogic.repository.ProfileRepository
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TestRecordLegalAcceptanceUseCase {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var profileRepository: ProfileRepository

    @Mock
    private lateinit var prefKeys: PrefKeysV2

    @Mock
    private lateinit var configLogic: ConfigLogic

    private lateinit var useCase: RecordLegalAcceptanceUseCase
    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(configLogic.appVersion).thenReturn("1.0.0-alpha")
        useCase = RecordLegalAcceptanceUseCaseImpl(
            profileRepository = profileRepository,
            prefKeys = prefKeys,
            configLogic = configLogic
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `Given required versions are configured and backend accepts, When invoke is called, Then it records and caches accepted versions`() =
        coroutineRule.runTest {
            val requestCaptor = argumentCaptor<eu.europa.ec.networklogic.model.request.RecordLegalAcceptanceRequest>()
            whenever(profileRepository.recordLegalAcceptance(any())).thenReturn(
                Result.success(
                    LegalAcceptanceSnapshot(
                        requiredTermsVersion = "wallet-alpha-2026-04-08",
                        acceptedTermsVersion = "wallet-alpha-2026-04-08",
                        acceptedTermsAt = "2026-04-08T10:15:00Z",
                        requiredPrivacyVersion = "privacy-2026-04-08",
                        acknowledgedPrivacyVersion = "privacy-2026-04-08",
                        acknowledgedPrivacyAt = "2026-04-08T10:15:00Z"
                    )
                )
            )

            val result = useCase(
                LegalAcceptanceSnapshot(
                    requiredTermsVersion = "wallet-alpha-2026-04-08",
                    requiredPrivacyVersion = "privacy-2026-04-08"
                )
            )

            assertTrue(result.isSuccess)
            val snapshot = result.getOrThrow()
            assertEquals("wallet-alpha-2026-04-08", snapshot.acceptedTermsVersion)
            assertEquals("privacy-2026-04-08", snapshot.acknowledgedPrivacyVersion)
            assertEquals("2026-04-08T10:15:00Z", snapshot.acceptedTermsAt)
            assertEquals("2026-04-08T10:15:00Z", snapshot.acknowledgedPrivacyAt)

            verify(profileRepository).recordLegalAcceptance(requestCaptor.capture())
            assertEquals("wallet-alpha-2026-04-08", requestCaptor.firstValue.acceptedTermsVersion)
            assertEquals("privacy-2026-04-08", requestCaptor.firstValue.acknowledgedPrivacyVersion)
            assertEquals("1.0.0-alpha", requestCaptor.firstValue.appVersion)
            assertEquals("android", requestCaptor.firstValue.platform)

            verify(prefKeys).setAcceptedTermsVersion("wallet-alpha-2026-04-08")
            verify(prefKeys).setAcceptedTermsAt("2026-04-08T10:15:00Z")
            verify(prefKeys).setAcknowledgedPrivacyVersion("privacy-2026-04-08")
            verify(prefKeys).setAcknowledgedPrivacyAt("2026-04-08T10:15:00Z")
        }

    @Test
    fun `Given backend write fails, When invoke is called, Then it does not cache accepted versions`() =
        coroutineRule.runTest {
            whenever(profileRepository.recordLegalAcceptance(any())).thenReturn(
                Result.failure(IllegalStateException("request failed"))
            )

            val result = useCase(
                LegalAcceptanceSnapshot(
                    requiredTermsVersion = "wallet-alpha-2026-04-08",
                    requiredPrivacyVersion = "privacy-2026-04-08"
                )
            )

            assertTrue(result.isFailure)
            verify(prefKeys, never()).setAcceptedTermsVersion(any())
            verify(prefKeys, never()).setAcceptedTermsAt(any())
            verify(prefKeys, never()).setAcknowledgedPrivacyVersion(any())
            verify(prefKeys, never()).setAcknowledgedPrivacyAt(any())
        }
}
