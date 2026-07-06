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

package eu.europa.ec.proximityfeature.ui.request

import eu.europa.ec.proximityfeature.interactor.ProximityRequestInteractor
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.navigation.CommonScreens
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TestProximityRequestViewModel {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
    private val testScope: TestScope = TestScope(testDispatcher)

    @get:Rule
    val coroutineRule = CoroutineTestRule(testDispatcher, testScope)

    @Mock
    private lateinit var interactor: ProximityRequestInteractor

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        whenever(resourceProvider.getString(any<Int>())).thenReturn("text")
    }

    @After
    fun after() {
        Dispatchers.resetMain()
        closeable.close()
    }

    @Test
    fun `Given approved proximity request, When resolving next screen, Then loading route is returned without biometric screen`() {
        val viewModel: ProximityRequestViewModel = createViewModel()

        val nextScreen: String = viewModel.getNextScreen()

        assertEquals("PROXIMITY_LOADING?scopeId=scope-id", nextScreen)
        assertFalse(nextScreen.contains(CommonScreens.Biometric.screenName))
    }

    @Test
    fun `Given selected document id, When doing work, Then interactor receives selected document id`() {
        // Given
        whenever(interactor.getRequestDocuments()).thenReturn(emptyFlow())
        val viewModel: ProximityRequestViewModel = createViewModel(
            presentingDocumentId = "selected-doc-id"
        )

        // When
        viewModel.doWork()
        testScope.advanceUntilIdle()

        // Then
        verify(interactor).setSelectedDocumentId("selected-doc-id")
    }

    private fun createViewModel(
        presentingDocumentId: String? = null
    ): ProximityRequestViewModel {
        return ProximityRequestViewModel(
            interactor = interactor,
            resourceProvider = resourceProvider,
            presentationScopeId = "scope-id",
            presentingDocumentId = presentingDocumentId
        )
    }
}
