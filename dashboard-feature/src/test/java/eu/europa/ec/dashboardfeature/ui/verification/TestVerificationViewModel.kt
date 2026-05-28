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

import eu.europa.ec.dashboardfeature.model.verification.VerificationTemplate
import eu.europa.ec.dashboardfeature.model.verification.VerificationTemplateType
import eu.europa.ec.dashboardfeature.repository.VerificationRepository
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
class TestVerificationViewModel {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @get:Rule
    val coroutineRule = CoroutineTestRule(testDispatcher, testScope)

    @Mock
    private lateinit var verificationRepository: VerificationRepository

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        whenever(resourceProvider.getString(any())).thenReturn("Attribute")
        runBlocking {
            whenever(verificationRepository.getVerificationTemplates()).thenReturn(templates)
        }
    }

    @After
    fun after() {
        Dispatchers.resetMain()
        closeable.close()
    }

    @Test
    fun `Given custom invitation creation, When initialized, Then only backend supported attributes are available`() =
        coroutineRule.runTest {
            val viewModel = createViewModel()

            viewModel.setEvent(Event.Init)
            testScope.advanceUntilIdle()
            viewModel.handleEvents(Event.InitializeTemplate(VerificationTemplateType.CUSTOM))

            assertEquals(
                listOf("age_over_18", "full_name", "date_of_birth", "nationality"),
                viewModel.viewState.value.attributes.map { it.key }
            )
        }

    @Test
    fun `Given identity attributes are selected, When age is selected, Then identity attributes are cleared`() =
        coroutineRule.runTest {
            val viewModel = createInitializedCustomViewModel()

            viewModel.handleEvents(Event.ToggleAttribute("full_name", true))
            viewModel.handleEvents(Event.UpdateExpectedValue("full_name", "Alice Example"))
            viewModel.handleEvents(Event.ToggleAttribute("date_of_birth", true))
            viewModel.handleEvents(Event.UpdateExpectedValue("date_of_birth", "1990-01-01"))
            viewModel.handleEvents(Event.ToggleAttribute("age_over_18", true))

            val attributes = viewModel.viewState.value.attributes.associateBy { it.key }
            assertTrue(attributes.getValue("age_over_18").selected)
            assertFalse(attributes.getValue("full_name").selected)
            assertFalse(attributes.getValue("date_of_birth").selected)
            assertEquals("", attributes.getValue("full_name").expectedValue)
            assertEquals("", attributes.getValue("date_of_birth").expectedValue)
        }

    @Test
    fun `Given nationality is selected, When identity attribute is selected, Then nationality is cleared`() =
        coroutineRule.runTest {
            val viewModel = createInitializedCustomViewModel()

            viewModel.handleEvents(Event.ToggleAttribute("nationality", true))
            viewModel.handleEvents(Event.ToggleAttribute("full_name", true))

            val attributes = viewModel.viewState.value.attributes.associateBy { it.key }
            assertFalse(attributes.getValue("nationality").selected)
            assertTrue(attributes.getValue("full_name").selected)
        }

    private suspend fun createInitializedCustomViewModel(): VerificationViewModel {
        val viewModel = createViewModel()
        viewModel.setEvent(Event.Init)
        testScope.advanceUntilIdle()
        viewModel.handleEvents(Event.InitializeTemplate(VerificationTemplateType.CUSTOM))
        return viewModel
    }

    private fun createViewModel(): VerificationViewModel {
        return VerificationViewModel(
            verificationRepository = verificationRepository,
            resourceProvider = resourceProvider
        )
    }

    private companion object {
        val templates = listOf(
            VerificationTemplate(
                type = VerificationTemplateType.AGE_VERIFICATION,
                title = "Age",
                description = "Age",
                purpose = "Age",
                attributes = listOf("age_over_18")
            ),
            VerificationTemplate(
                type = VerificationTemplateType.IDENTITY_VERIFICATION,
                title = "Identity",
                description = "Identity",
                purpose = "Identity",
                attributes = listOf("full_name", "date_of_birth")
            ),
            VerificationTemplate(
                type = VerificationTemplateType.NATIONALITY_VERIFICATION,
                title = "Nationality",
                description = "Nationality",
                purpose = "Nationality",
                attributes = listOf("nationality")
            ),
            VerificationTemplate(
                type = VerificationTemplateType.CUSTOM,
                title = "Custom",
                description = "Custom",
                purpose = "Peer-to-peer attribute verification",
                attributes = emptyList()
            )
        )
    }
}
