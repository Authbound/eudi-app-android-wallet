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

package eu.europa.ec.authboundpidfeature.ui.result

import androidx.lifecycle.SavedStateHandle
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class TestAuthboundPidResultViewModel {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @get:Rule
    val coroutineRule = CoroutineTestRule(testDispatcher, testScope)

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        whenever(resourceProvider.getString(any<Int>())).thenAnswer { invocation ->
            when (invocation.arguments[0] as Int) {
                R.string.authboundpid_try_again -> "Try Again"
                R.string.authboundpid_start_over -> "Start Over"
                R.string.authboundpid_result_no_internet_title -> "No Internet Connection"
                R.string.authboundpid_result_no_internet_message -> "Reconnect and try again."
                R.string.authboundpid_result_unavailable_title -> "Verification Unavailable"
                R.string.authboundpid_result_unavailable_message -> "Start a new verification."
                R.string.authboundpid_result_unknown_title -> "Unknown Error"
                R.string.authboundpid_result_unknown_message -> "Something went wrong."
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
    fun `Given NO_INTERNET result type, When initialized, Then no internet copy is shown`() =
        coroutineRule.runTest {
            val viewModel = createViewModel("NO_INTERNET")

            viewModel.setEvent(Event.Init)
            testScope.advanceUntilIdle()

            assertEquals("No Internet Connection", viewModel.viewState.value.title)
            assertEquals("Reconnect and try again.", viewModel.viewState.value.message)
            assertTrue(viewModel.viewState.value.showRetryButton)
        }

    @Test
    fun `Given UNAVAILABLE result type, When initialized, Then unavailable copy is shown`() =
        coroutineRule.runTest {
            val viewModel = createViewModel("UNAVAILABLE")

            viewModel.setEvent(Event.Init)
            testScope.advanceUntilIdle()

            assertEquals("Verification Unavailable", viewModel.viewState.value.title)
            assertEquals("Start a new verification.", viewModel.viewState.value.message)
            assertEquals("Start Over", viewModel.viewState.value.retryButtonText)
        }

    @Test
    fun `Given invalid result type, When initialized, Then unknown fallback is shown`() =
        coroutineRule.runTest {
            val viewModel = createViewModel("NOT_A_REAL_RESULT")

            viewModel.setEvent(Event.Init)
            testScope.advanceUntilIdle()

            assertEquals("Unknown Error", viewModel.viewState.value.title)
            assertEquals("Something went wrong.", viewModel.viewState.value.message)
        }

    private fun createViewModel(resultType: String): AuthboundPidResultViewModel {
        return AuthboundPidResultViewModel(
            savedStateHandle = SavedStateHandle(mapOf("resultType" to resultType)),
            resourceProvider = resourceProvider
        )
    }
}
