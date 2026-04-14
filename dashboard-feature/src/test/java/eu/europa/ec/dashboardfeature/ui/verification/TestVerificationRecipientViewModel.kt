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
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
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
                R.string.verification_recipient_refresh_error_cached to "Unable to refresh verification request"
            )
        )
        whenever(verificationRepository.observePublicVerificationSessionStatus(any())).thenReturn(emptyFlow())
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
            val publicUrl = "https://app.authbound.io/verify/$sessionId?token=$accessToken"
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
                    verificationUrl = "https://app.authbound.io/verify/$sessionId?token=other"
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
            val incomingUrl = "https://app.authbound.io/verify/$sessionId?token=$accessToken"
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

    private fun createViewModel(): VerificationRecipientViewModel {
        return VerificationRecipientViewModel(
            verificationRepository = verificationRepository,
            uiSerializer = uiSerializer,
            resourceProvider = resourceProvider
        )
    }

    private fun createRecipientSession(
        id: String,
        publicUrl: String
    ): VerificationRecipientSession {
        return VerificationRecipientSession(
            id = id,
            status = "verified",
            purpose = "Verify employment eligibility",
            createdAt = 1_713_090_000_000,
            expiresAt = 1_713_093_600_000,
            requester = null,
            requestedAttributes = emptyList(),
            publicUrl = publicUrl,
            gatewaySessionId = null,
            requestUri = null,
            requestUriExpiresAt = null,
            sseToken = null,
            statusStreamUrl = null
        )
    }
}
