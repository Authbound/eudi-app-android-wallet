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

package eu.europa.ec.presentationfeature.ui.loading

import android.content.Context
import app.cash.turbine.test
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationResult
import eu.europa.ec.authenticationlogic.model.BiometricCrypto
import eu.europa.ec.commonfeature.ui.loading.Effect
import eu.europa.ec.corelogic.model.AuthenticationData
import eu.europa.ec.presentationfeature.interactor.PresentationLoadingInteractor
import eu.europa.ec.presentationfeature.interactor.PresentationLoadingObserveResponsePartialState
import eu.europa.ec.presentationfeature.interactor.PresentationLoadingSendRequestedDocumentPartialState
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.navigation.PresentationScreens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TestPresentationLoadingViewModel {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @get:Rule
    val coroutineRule = CoroutineTestRule(testDispatcher, testScope)

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var interactor: PresentationLoadingInteractor

    @Mock
    private lateinit var context: Context

    private lateinit var closeable: AutoCloseable
    private var authenticationSuccesses = 0

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        whenever(resourceProvider.getString(any<Int>())).thenReturn("Loading")
        whenever(interactor.observeResponse()).thenReturn(
            flowOf(
                PresentationLoadingObserveResponsePartialState.UserAuthenticationRequired(
                    authenticationData = listOf(
                        AuthenticationData(
                            crypto = BiometricCrypto(cryptoObject = null),
                            onAuthenticationSuccess = { authenticationSuccesses += 1 },
                        )
                    )
                )
            )
        )
        whenever(interactor.sendRequestedDocuments()).thenReturn(
            PresentationLoadingSendRequestedDocumentPartialState.Success
        )
    }

    @After
    fun after() {
        Dispatchers.resetMain()
        closeable.close()
    }

    @Test
    fun `successful presentation authentication sends all prepared documents once`() =
        coroutineRule.runTest {
            val viewModel = createViewModel()
            val resultHandler = authenticationResultHandler(viewModel)

            resultHandler.onAuthenticationSuccess()
            testScope.advanceUntilIdle()

            assertEquals(1, authenticationSuccesses)
            verify(interactor, times(1)).sendRequestedDocuments()
        }

    @Test
    fun `cancelled presentation authentication returns to request without sending`() =
        coroutineRule.runTest {
            val viewModel = createViewModel()
            val resultHandler = authenticationResultHandler(viewModel)

            viewModel.effect.test {
                resultHandler.onAuthenticationError()

                assertEquals(
                    Effect.Navigation.PopBackStackUpTo(
                        screenRoute = PresentationScreens.PresentationRequest.screenRoute,
                        inclusive = false,
                    ),
                    awaitItem(),
                )
            }
            assertEquals(0, authenticationSuccesses)
            verify(interactor, never()).sendRequestedDocuments()
        }

    @Test
    fun `failed presentation authentication does not prepare or send documents`() =
        coroutineRule.runTest {
            val viewModel = createViewModel()
            val resultHandler = authenticationResultHandler(viewModel)

            resultHandler.onAuthenticationFailure()
            testScope.advanceUntilIdle()

            assertEquals(0, authenticationSuccesses)
            verify(interactor, never()).sendRequestedDocuments()
        }

    private suspend fun authenticationResultHandler(
        viewModel: PresentationLoadingViewModel,
    ): DeviceAuthenticationResult {
        viewModel.doWork(context)
        testScope.advanceUntilIdle()
        val captor = argumentCaptor<DeviceAuthenticationResult>()
        verify(interactor).handleUserAuthentication(
            context = any(),
            crypto = any(),
            notifyOnAuthenticationFailure = any(),
            resultHandler = captor.capture(),
        )
        return captor.firstValue
    }

    private fun createViewModel() = PresentationLoadingViewModel(
        resourceProvider = resourceProvider,
        interactor = interactor,
        presentationScopeId = "presentation-session",
    )
}
