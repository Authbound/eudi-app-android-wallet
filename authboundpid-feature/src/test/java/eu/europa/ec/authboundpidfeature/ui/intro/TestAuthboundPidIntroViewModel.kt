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

package eu.europa.ec.authboundpidfeature.ui.intro

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import eu.europa.ec.authboundpidfeature.interactor.AuthboundPidIntroInteractor
import eu.europa.ec.authboundpidfeature.interactor.AuthboundPidIntroPartialState
import eu.europa.ec.authboundpidfeature.interactor.AuthboundPidVerificationPartialState
import eu.europa.ec.authboundpidfeature.model.AuthboundPidResultType
import eu.europa.ec.commonfeature.config.OfferUiConfig
import eu.europa.ec.corelogic.controller.ResolveDocumentOfferPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.eudi.wallet.issue.openid4vci.Offer
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.serializer.UiSerializer
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TestAuthboundPidIntroViewModel {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @get:Rule
    val coroutineRule = CoroutineTestRule(testDispatcher, testScope)

    @Mock
    private lateinit var interactor: AuthboundPidIntroInteractor

    @Mock
    private lateinit var walletCoreDocumentsController: WalletCoreDocumentsController

    @Mock
    private lateinit var uiSerializer: UiSerializer

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        whenever(resourceProvider.getString(any<Int>())).thenAnswer { invocation ->
            when (invocation.arguments[0] as Int) {
                R.string.authboundpid_intro_launching -> "Opening secure verification…"
                R.string.authboundpid_processing_status -> "Processing verification…"
                R.string.authboundpid_processing_issuing -> "Issuing credential…"
                else -> ""
            }
        }
    }

    @After
    fun after() {
        Dispatchers.resetMain()
        closeable.close()
    }

    @Test
    fun `Given session creation succeeds, When StartVerification is dispatched, Then SDK launch effect is emitted and completion state is persisted`() =
        coroutineRule.runTest {
            whenever(interactor.createSession()).thenReturn(
                flow {
                    emit(AuthboundPidIntroPartialState.CreatingSession)
                    emit(
                        AuthboundPidIntroPartialState.SessionCreated(
                            sessionId = "session-1",
                            candourSessionId = "candour-1",
                            candourApiEndpoint = "https://test-rest.candour.fi"
                        )
                    )
                }
            )
            val savedStateHandle = SavedStateHandle()
            val viewModel = createViewModel(savedStateHandle)

            viewModel.effect.test {
                viewModel.setEvent(Event.StartVerification)
                testScope.advanceUntilIdle()

                val effect = awaitItem() as Effect.LaunchCandourSdk
                assertEquals("candour-1", effect.candourSessionId)
                assertEquals("https://test-rest.candour.fi", effect.candourApiEndpoint)
                assertTrue(viewModel.viewState.value.isCompleting)
                assertEquals("session-1", savedStateHandle.get<String>("authboundpid_active_session_id"))
                assertEquals(true, savedStateHandle.get<Boolean>("authboundpid_awaiting_sdk_result"))
            }
        }

    @Test
    fun `Given awaiting sdk result, When cancel status is received twice, Then only the first result is handled`() =
        coroutineRule.runTest {
            val savedStateHandle = SavedStateHandle(
                mapOf(
                    "authboundpid_active_session_id" to "session-1",
                    "authboundpid_awaiting_sdk_result" to true
                )
            )
            val viewModel = createViewModel(savedStateHandle)

            viewModel.effect.test {
                viewModel.setEvent(Event.CandourSdkResult("CANCELLED_SESSION"))
                testScope.advanceUntilIdle()
                assertEquals(
                    Effect.Navigation.NavigateToResult(AuthboundPidResultType.CANCELED),
                    awaitItem()
                )

                viewModel.setEvent(Event.CandourSdkResult("CANCELLED_SESSION"))
                testScope.advanceUntilIdle()
                expectNoEvents()
                assertFalse(savedStateHandle.get<Boolean>("authboundpid_awaiting_sdk_result") ?: true)
            }
        }

    @Test
    fun `Given recovered active session, When success status is received, Then verification is fetched and issuance navigation is emitted`() =
        coroutineRule.runTest {
            whenever(interactor.getVerificationResult("session-1")).thenReturn(
                flow {
                    emit(AuthboundPidVerificationPartialState.Loading)
                    emit(AuthboundPidVerificationPartialState.Verified("openid-credential-offer://offer"))
                }
            )
            whenever(walletCoreDocumentsController.resolveDocumentOffer("openid-credential-offer://offer")).thenReturn(
                flow {
                    emit(ResolveDocumentOfferPartialState.Success(mock<Offer>()))
                }
            )
            whenever(uiSerializer.toBase64(any<OfferUiConfig>(), eq(OfferUiConfig.Parser))).thenReturn("encoded-offer")
            val savedStateHandle = SavedStateHandle(
                mapOf(
                    "authboundpid_active_session_id" to "session-1",
                    "authboundpid_awaiting_sdk_result" to true
                )
            )
            val viewModel = createViewModel(savedStateHandle)

            viewModel.effect.test {
                viewModel.setEvent(Event.CandourSdkResult("SUCCESS_SESSION"))
                testScope.advanceUntilIdle()

                val effect = awaitItem() as Effect.Navigation.NavigateToIssuance
                assertTrue(effect.screenRoute.contains("ISSUANCE_DOCUMENT_OFFER"))
                assertTrue(effect.screenRoute.contains("encoded-offer"))
                verify(interactor).getVerificationResult("session-1")
                assertFalse(viewModel.viewState.value.isCompleting)
                assertFalse(savedStateHandle.get<Boolean>("authboundpid_awaiting_sdk_result") ?: true)
            }
        }

    @Test
    fun `Given awaiting sdk result, When sdk launch fails, Then unavailable result is emitted`() =
        coroutineRule.runTest {
            val savedStateHandle = SavedStateHandle(
                mapOf(
                    "authboundpid_active_session_id" to "session-1",
                    "authboundpid_awaiting_sdk_result" to true
                )
            )
            val viewModel = createViewModel(savedStateHandle)

            viewModel.effect.test {
                viewModel.setEvent(Event.CandourSdkLaunchFailed)
                testScope.advanceUntilIdle()

                assertEquals(
                    Effect.Navigation.NavigateToResult(AuthboundPidResultType.UNAVAILABLE),
                    awaitItem()
                )
                assertFalse(savedStateHandle.get<Boolean>("authboundpid_awaiting_sdk_result") ?: true)
            }
        }

    @Test
    fun `Given awaiting sdk result, When lost session status is received, Then unavailable result is emitted`() =
        coroutineRule.runTest {
            val savedStateHandle = SavedStateHandle(
                mapOf(
                    "authboundpid_active_session_id" to "session-1",
                    "authboundpid_awaiting_sdk_result" to true
                )
            )
            val viewModel = createViewModel(savedStateHandle)

            viewModel.effect.test {
                viewModel.setEvent(Event.CandourSdkResult("LOST_SESSION"))
                testScope.advanceUntilIdle()

                assertEquals(
                    Effect.Navigation.NavigateToResult(AuthboundPidResultType.UNAVAILABLE),
                    awaitItem()
                )
                assertFalse(savedStateHandle.get<Boolean>("authboundpid_awaiting_sdk_result") ?: true)
            }
        }

    @Test
    fun `Given offer serialization fails, When verification succeeds, Then unknown result is emitted`() =
        coroutineRule.runTest {
            whenever(interactor.getVerificationResult("session-1")).thenReturn(
                flow {
                    emit(AuthboundPidVerificationPartialState.Loading)
                    emit(AuthboundPidVerificationPartialState.Verified("openid-credential-offer://offer"))
                }
            )
            whenever(walletCoreDocumentsController.resolveDocumentOffer("openid-credential-offer://offer")).thenReturn(
                flow {
                    emit(ResolveDocumentOfferPartialState.Success(mock<Offer>()))
                }
            )
            whenever(uiSerializer.toBase64(any<OfferUiConfig>(), eq(OfferUiConfig.Parser))).thenReturn(null)
            val savedStateHandle = SavedStateHandle(
                mapOf(
                    "authboundpid_active_session_id" to "session-1",
                    "authboundpid_awaiting_sdk_result" to true
                )
            )
            val viewModel = createViewModel(savedStateHandle)

            viewModel.effect.test {
                viewModel.setEvent(Event.CandourSdkResult("SUCCESS_SESSION_DATA_SAVED"))
                testScope.advanceUntilIdle()

                assertEquals(
                    Effect.Navigation.NavigateToResult(AuthboundPidResultType.UNKNOWN),
                    awaitItem()
                )
            }
        }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): AuthboundPidIntroViewModel {
        return AuthboundPidIntroViewModel(
            savedStateHandle = savedStateHandle,
            interactor = interactor,
            walletCoreDocumentsController = walletCoreDocumentsController,
            uiSerializer = uiSerializer,
            resourceProvider = resourceProvider
        )
    }
}
