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

import eu.europa.ec.authenticationlogic.model.LegalAcceptance
import eu.europa.ec.authenticationlogic.model.Profile
import eu.europa.ec.authenticationlogic.repository.ProfileRepository
import eu.europa.ec.businesslogic.controller.log.LogController
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TestGetLegalAcceptanceStateUseCase {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var prefKeys: PrefKeysV2

    @Mock
    private lateinit var profileRepository: ProfileRepository

    @Mock
    private lateinit var logController: LogController

    private lateinit var useCase: GetLegalAcceptanceStateUseCase
    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        useCase = GetLegalAcceptanceStateUseCaseImpl(
            prefKeys = prefKeys,
            profileRepository = profileRepository,
            logController = logController
        )
        whenever(prefKeys.getRequiredTermsVersionSafe()).thenReturn("")
        whenever(prefKeys.getAcceptedTermsVersionSafe()).thenReturn("")
        whenever(prefKeys.getAcceptedTermsAtSafe()).thenReturn("")
        whenever(prefKeys.getRequiredPrivacyVersionSafe()).thenReturn("")
        whenever(prefKeys.getAcknowledgedPrivacyVersionSafe()).thenReturn("")
        whenever(prefKeys.getAcknowledgedPrivacyAtSafe()).thenReturn("")
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `Given backend profile includes legal acceptance, When invoke is called, Then it returns and caches backend versions`() =
        coroutineRule.runTest {
            whenever(profileRepository.getMyProfile()).thenReturn(
                Result.success(
                    Profile(
                        id = "user-1",
                        handle = "lassi",
                        displayName = "Lassi",
                        legalAcceptance = LegalAcceptance(
                            requiredTermsVersion = "wallet-alpha-2026-04-08",
                            acceptedTermsVersion = "wallet-alpha-2026-04-08",
                            acceptedTermsAt = "2026-04-08T10:15:00Z",
                            requiredPrivacyVersion = "privacy-2026-04-08",
                            acknowledgedPrivacyVersion = "privacy-2026-04-08",
                            acknowledgedPrivacyAt = "2026-04-08T10:15:00Z"
                        )
                    )
                )
            )

            val result = useCase()

            assertTrue(result.isSuccess)
            val snapshot = result.getOrThrow()
            assertEquals("wallet-alpha-2026-04-08", snapshot.requiredTermsVersion)
            assertEquals("privacy-2026-04-08", snapshot.requiredPrivacyVersion)
            assertTrue(snapshot.isAccepted)
            verify(prefKeys).setRequiredTermsVersion("wallet-alpha-2026-04-08")
            verify(prefKeys).setAcceptedTermsVersion("wallet-alpha-2026-04-08")
            verify(prefKeys).setRequiredPrivacyVersion("privacy-2026-04-08")
            verify(prefKeys).setAcknowledgedPrivacyVersion("privacy-2026-04-08")
            verify(prefKeys).setProfileCompleted(true)
        }

    @Test
    fun `Given backend fails and cached acceptance is current, When invoke is called, Then it falls back to cached accepted snapshot`() =
        coroutineRule.runTest {
            whenever(prefKeys.getRequiredTermsVersionSafe()).thenReturn("wallet-alpha-2026-04-08")
            whenever(prefKeys.getAcceptedTermsVersionSafe()).thenReturn("wallet-alpha-2026-04-08")
            whenever(prefKeys.getAcceptedTermsAtSafe()).thenReturn("2026-04-08T10:15:00Z")
            whenever(prefKeys.getRequiredPrivacyVersionSafe()).thenReturn("privacy-2026-04-08")
            whenever(prefKeys.getAcknowledgedPrivacyVersionSafe()).thenReturn("privacy-2026-04-08")
            whenever(prefKeys.getAcknowledgedPrivacyAtSafe()).thenReturn("2026-04-08T10:15:00Z")
            whenever(profileRepository.getMyProfile()).thenReturn(
                Result.failure(IllegalStateException("temporary backend failure"))
            )

            val result = useCase()

            assertTrue(result.isFailure)
            verify(prefKeys, never()).setProfileCompleted(true)
        }
}
