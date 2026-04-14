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

package eu.europa.ec.authboundpidfeature.interactor

import app.cash.turbine.test
import eu.europa.ec.authboundpidlogic.repository.AuthboundPidRepository
import eu.europa.ec.networklogic.model.response.AuthboundPidSessionStatus
import eu.europa.ec.networklogic.model.response.CreateAuthboundPidSessionResponse
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TestAuthboundPidIntroInteractor {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @get:Rule
    val coroutineRule = CoroutineTestRule(testDispatcher, testScope)

    @Mock
    private lateinit var repository: AuthboundPidRepository

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.genericErrorMessage()).thenReturn("generic error")
        whenever(resourceProvider.genericNetworkErrorMessage()).thenReturn("network error")
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `Given sdk session creation succeeds, When createSession is collected, Then session metadata is exposed`() =
        coroutineRule.runTest {
            whenever(repository.createSession()).thenReturn(
                Result.success(
                    CreateAuthboundPidSessionResponse(
                        sessionId = "session-1",
                        candourSessionId = "candour-1",
                        candourApiEndpoint = "https://api-sandbox.candour.fi/api/mobile/sdk/v1/",
                        expiresAt = "2026-04-10T12:00:00.000Z"
                    )
                )
            )

            createInteractor().createSession().test {
                assertEquals(AuthboundPidIntroPartialState.CreatingSession, awaitItem())
                assertEquals(
                    AuthboundPidIntroPartialState.SessionCreated(
                        sessionId = "session-1",
                        candourSessionId = "candour-1",
                        candourApiEndpoint = "https://api-sandbox.candour.fi/api/mobile/sdk/v1/"
                    ),
                    awaitItem()
                )
                awaitComplete()
            }
        }

    @Test
    fun `Given backend returns processing before verified, When fetching verification result, Then bounded retry reaches verified`() =
        coroutineRule.runTest {
            val processing = AuthboundPidSessionStatus(
                sessionId = "session-1",
                status = AuthboundPidSessionStatus.STATUS_PROCESSING,
                expiresAt = "2026-04-10T12:00:00.000Z"
            )
            val verified = AuthboundPidSessionStatus(
                sessionId = "session-1",
                status = AuthboundPidSessionStatus.STATUS_VERIFIED,
                credentialOfferUri = "openid-credential-offer://offer",
                expiresAt = "2026-04-10T12:00:00.000Z"
            )
            whenever(repository.resolveSession("session-1")).thenReturn(Result.success(processing))
            whenever(repository.getSessionStatus("session-1")).thenReturn(
                Result.success(processing),
                Result.success(verified)
            )

            createInteractor().getVerificationResult("session-1").test {
                assertEquals(AuthboundPidVerificationPartialState.Loading, awaitItem())
                testScope.advanceUntilIdle()
                assertEquals(
                    AuthboundPidVerificationPartialState.Verified("openid-credential-offer://offer"),
                    awaitItem()
                )
                awaitComplete()
            }

            verify(repository).resolveSession("session-1")
            verify(repository, times(2)).getSessionStatus("session-1")
        }

    @Test
    fun `Given backend remains processing, When fetching verification result, Then timeout is emitted after bounded retry`() =
        coroutineRule.runTest {
            val processing = AuthboundPidSessionStatus(
                sessionId = "session-1",
                status = AuthboundPidSessionStatus.STATUS_PROCESSING,
                expiresAt = "2026-04-10T12:00:00.000Z"
            )
            whenever(repository.resolveSession("session-1")).thenReturn(Result.success(processing))
            whenever(repository.getSessionStatus("session-1")).thenReturn(
                Result.success(processing),
                Result.success(processing),
                Result.success(processing)
            )

            createInteractor().getVerificationResult("session-1").test {
                assertEquals(AuthboundPidVerificationPartialState.Loading, awaitItem())
                testScope.advanceUntilIdle()
                assertEquals(AuthboundPidVerificationPartialState.Timeout, awaitItem())
                awaitComplete()
            }

            verify(repository).resolveSession("session-1")
            verify(repository, times(3)).getSessionStatus("session-1")
        }

    @Test
    fun `Given issuance is still retryable, When fetching verification result, Then resolve is retried until verified`() =
        coroutineRule.runTest {
            val issuancePending = AuthboundPidSessionStatus(
                sessionId = "session-1",
                status = AuthboundPidSessionStatus.STATUS_PROCESSING,
                identityVerified = true,
                expiresAt = "2026-04-10T12:00:00.000Z"
            )
            val verified = AuthboundPidSessionStatus(
                sessionId = "session-1",
                status = AuthboundPidSessionStatus.STATUS_VERIFIED,
                identityVerified = true,
                credentialOfferUri = "openid-credential-offer://offer",
                expiresAt = "2026-04-10T12:00:00.000Z"
            )
            whenever(repository.resolveSession("session-1")).thenReturn(
                Result.success(issuancePending),
                Result.success(verified)
            )

            createInteractor().getVerificationResult("session-1").test {
                assertEquals(AuthboundPidVerificationPartialState.Loading, awaitItem())
                testScope.advanceUntilIdle()
                assertEquals(
                    AuthboundPidVerificationPartialState.Verified("openid-credential-offer://offer"),
                    awaitItem()
                )
                awaitComplete()
            }

            verify(repository, times(2)).resolveSession("session-1")
            verify(repository, times(0)).getSessionStatus("session-1")
        }

    private fun createInteractor(): AuthboundPidIntroInteractor {
        return AuthboundPidIntroInteractorImpl(
            authboundPidRepository = repository,
            resourceProvider = resourceProvider
        )
    }
}
