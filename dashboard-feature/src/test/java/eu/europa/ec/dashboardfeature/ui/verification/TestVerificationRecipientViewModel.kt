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

package eu.europa.ec.dashboardfeature.ui.verification

import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipientSession
import eu.europa.ec.dashboardfeature.repository.VerificationRepository
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.util.StringResourceProviderMocker
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.serializer.UiSerializer
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TestVerificationRecipientViewModel {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @get:Rule
    val coroutineRule = CoroutineTestRule(testDispatcher, testScope)

    @Mock
    private lateinit var verificationRepository: VerificationRepository

    @Mock
    private lateinit var uiSerializer: UiSerializer

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        StringResourceProviderMocker.mockResourceProviderStrings(
            resourceProvider = resourceProvider,
            pairs = listOf(
                R.string.verification_recipient_refresh_error_empty to "Verification request unavailable",
                R.string.verification_recipient_refresh_error_cached to "Unable to refresh verification request",
                R.string.verification_recipient_consent_required to "Consent required",
                R.string.verification_recipient_request_unavailable to "Verification request unavailable"
            )
        )
    }

    @After
    fun after() {
        Dispatchers.resetMain()
        closeable.close()
    }

    @Test
    fun `Given loaded session, When open in browser, Then backend public url is emitted`() =
        coroutineRule.runTest {
            val sessionId = "550e8400-e29b-41d4-a716-446655440000"
            val accessToken = "123e4567-e89b-12d3-a456-426614174000"
            val publicUrl = "https://app.authbound.io/verify/$sessionId#token=$accessToken"
            whenever(
                verificationRepository.getPublicVerificationSession(sessionId, accessToken)
            ).thenReturn(
                Result.success(
                    createRecipientSession(
                        id = sessionId,
                        publicUrl = publicUrl
                    )
                )
            )

            val viewModel = createViewModel()
            viewModel.handleEvents(
                VerificationRecipientEvent.Init(
                    sessionId = sessionId,
                    accessToken = accessToken,
                    verificationUrl = "https://app.authbound.io/verify/$sessionId#token=other"
                )
            )
            testScope.advanceUntilIdle()

            viewModel.effect.runFlowTest {
                viewModel.handleEvents(VerificationRecipientEvent.OpenInBrowser)
                testScope.advanceUntilIdle()

                val effect = awaitItem() as VerificationRecipientEffect.OpenUrlInBrowser
                assertEquals(publicUrl, effect.url)
            }
        }

    @Test
    fun `Given initial load failed, When open in browser, Then original incoming verification url is emitted`() =
        coroutineRule.runTest {
            val sessionId = "550e8400-e29b-41d4-a716-446655440000"
            val accessToken = "123e4567-e89b-12d3-a456-426614174000"
            val incomingUrl = "https://app.authbound.io/verify/$sessionId#token=$accessToken"
            whenever(
                verificationRepository.getPublicVerificationSession(sessionId, accessToken)
            ).thenReturn(Result.failure(Exception("boom")))

            val viewModel = createViewModel()
            viewModel.handleEvents(
                VerificationRecipientEvent.Init(
                    sessionId = sessionId,
                    accessToken = accessToken,
                    verificationUrl = incomingUrl
                )
            )
            testScope.advanceUntilIdle()
            assertNull(viewModel.viewState.value.session)

            viewModel.effect.runFlowTest {
                viewModel.handleEvents(VerificationRecipientEvent.OpenInBrowser)
                testScope.advanceUntilIdle()

                val effect = awaitItem() as VerificationRecipientEffect.OpenUrlInBrowser
                assertEquals(incomingUrl, effect.url)
            }
        }

    @Test
    fun `Given loaded session with consent, When start succeeds, Then verification id is retained and wallet flow opens`() =
        coroutineRule.runTest {
            val sessionId = "550e8400-e29b-41d4-a716-446655440000"
            val accessToken = "123e4567-e89b-12d3-a456-426614174000"
            val requestUri = "openid4vp://verify?request_uri=https%3A%2F%2Fapi.authbound.io"
            whenever(
                verificationRepository.getPublicVerificationSession(sessionId, accessToken)
            ).thenReturn(
                Result.success(
                    createRecipientSession(
                        id = sessionId,
                        publicUrl = "https://app.authbound.io/verify/$sessionId#token=$accessToken",
                        status = "verified"
                    )
                )
            )
            whenever(
                verificationRepository.startPublicVerificationSession(sessionId, accessToken)
            ).thenReturn(
                Result.success(
                    createRecipientSession(
                        id = sessionId,
                        publicUrl = "https://app.authbound.io/verify/$sessionId#token=$accessToken",
                        status = "verification_started",
                        verificationId = "verification-1",
                        requestUri = requestUri
                    )
                )
            )
            whenever(uiSerializer.toBase64(any<RequestUriConfig>(), eq(RequestUriConfig.Parser)))
                .thenReturn("serialized-request-uri")

            val viewModel = createViewModel()
            viewModel.handleEvents(
                VerificationRecipientEvent.Init(
                    sessionId = sessionId,
                    accessToken = accessToken
                )
            )
            testScope.runCurrent()
            viewModel.handleEvents(VerificationRecipientEvent.ConsentChanged(true))

            viewModel.effect.runFlowTest {
                viewModel.handleEvents(VerificationRecipientEvent.StartVerification)
                testScope.runCurrent()

                val effect = awaitItem() as VerificationRecipientEffect.Navigation.SwitchScreen
                assertEquals(
                    "verification-1",
                    viewModel.viewState.value.session?.verificationId
                )
                assertEquals(
                    "verification_started",
                    viewModel.viewState.value.session?.status
                )
                assertEquals(requestUri, viewModel.viewState.value.session?.requestUri)
                assertTrue(effect.screenRoute.contains("serialized-request-uri"))
            }
        }

    @Test
    fun `Given route payload key, When start succeeds, Then initiator route does not expose recipient token`() =
        coroutineRule.runTest {
            val sessionId = "550e8400-e29b-41d4-a716-446655440000"
            val accessToken = "123e4567-e89b-12d3-a456-426614174000"
            val incomingUrl = "https://app.authbound.io/verify/$sessionId#token=$accessToken"
            val requestUri = "openid4vp://verify?request_uri=https%3A%2F%2Fapi.authbound.io"
            whenever(
                verificationRepository.getPublicVerificationSession(sessionId, accessToken)
            ).thenReturn(
                Result.success(
                    createRecipientSession(
                        id = sessionId,
                        publicUrl = incomingUrl,
                        status = "verified"
                    )
                )
            )
            whenever(
                verificationRepository.startPublicVerificationSession(sessionId, accessToken)
            ).thenReturn(
                Result.success(
                    createRecipientSession(
                        id = sessionId,
                        publicUrl = incomingUrl,
                        status = "verification_started",
                        verificationId = "verification-1",
                        requestUri = requestUri
                    )
                )
            )
            val configCaptor = argumentCaptor<RequestUriConfig>()
            whenever(uiSerializer.toBase64(configCaptor.capture(), eq(RequestUriConfig.Parser)))
                .thenReturn("serialized-request-uri")

            val viewModel = createViewModel()
            viewModel.handleEvents(
                VerificationRecipientEvent.Init(
                    sessionId = sessionId,
                    accessToken = accessToken,
                    verificationUrl = incomingUrl,
                    routePayloadKey = "payload-key"
                )
            )
            testScope.runCurrent()
            viewModel.handleEvents(VerificationRecipientEvent.ConsentChanged(true))

            viewModel.effect.runFlowTest {
                viewModel.handleEvents(VerificationRecipientEvent.StartVerification)
                testScope.runCurrent()

                awaitItem() as VerificationRecipientEffect.Navigation.SwitchScreen
                val mode = configCaptor.firstValue.mode as PresentationMode.OpenId4Vp
                assertTrue(mode.initiatorRoute.contains("payloadKey=payload-key"))
                assertFalse(mode.initiatorRoute.contains(accessToken))
                assertFalse(mode.initiatorRoute.contains("verificationUrl"))
                assertFalse(mode.initiatorRoute.contains(incomingUrl))
            }
        }

    @Test
    fun `Given loaded session without consent, When start requested, Then consent toast is shown`() =
        coroutineRule.runTest {
            val sessionId = "550e8400-e29b-41d4-a716-446655440000"
            val accessToken = "123e4567-e89b-12d3-a456-426614174000"
            whenever(
                verificationRepository.getPublicVerificationSession(sessionId, accessToken)
            ).thenReturn(
                Result.success(
                    createRecipientSession(
                        id = sessionId,
                        publicUrl = "https://app.authbound.io/verify/$sessionId#token=$accessToken",
                        status = "verified"
                    )
                )
            )

            val viewModel = createViewModel()
            viewModel.handleEvents(
                VerificationRecipientEvent.Init(
                    sessionId = sessionId,
                    accessToken = accessToken
                )
            )
            testScope.runCurrent()

            viewModel.effect.runFlowTest {
                viewModel.handleEvents(VerificationRecipientEvent.StartVerification)
                testScope.runCurrent()

                val effect = awaitItem() as VerificationRecipientEffect.ShowToast
                assertEquals("Consent required", effect.message)
            }
        }

    private fun createViewModel(): VerificationRecipientViewModel {
        return VerificationRecipientViewModel(
            verificationRepository = verificationRepository,
            uiSerializer = uiSerializer,
            resourceProvider = resourceProvider
        )
    }

    private fun createRecipientSession(
        id: String,
        publicUrl: String,
        status: String = "verified",
        verificationId: String? = null,
        requestUri: String? = null
    ): VerificationRecipientSession {
        return VerificationRecipientSession(
            id = id,
            status = status,
            verificationId = verificationId,
            purpose = "Verify employment eligibility",
            createdAt = 1_713_090_000_000,
            expiresAt = 1_713_093_600_000,
            requester = null,
            requestedAttributes = emptyList(),
            publicUrl = publicUrl,
            requestUri = requestUri,
            requestUriExpiresAt = null
        )
    }
}
