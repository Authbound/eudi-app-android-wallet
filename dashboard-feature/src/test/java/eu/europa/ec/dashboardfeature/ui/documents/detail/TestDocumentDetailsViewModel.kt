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

package eu.europa.ec.dashboardfeature.ui.documents.detail

import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractor
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TestDocumentDetailsViewModel {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @get:Rule
    val coroutineRule = CoroutineTestRule(testDispatcher, testScope)

    @Mock
    private lateinit var interactor: DocumentDetailsInteractor

    @Mock
    private lateinit var uiSerializer: UiSerializer

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    private lateinit var closeable: AutoCloseable
    private lateinit var viewModel: DocumentDetailsViewModel

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        whenever(resourceProvider.getString(any<Int>())).thenReturn("")
        whenever(interactor.getDocumentDetails(DOCUMENT_ID)).thenReturn(emptyFlow())
        viewModel = DocumentDetailsViewModel(
            documentDetailsInteractor = interactor,
            uiSerializer = uiSerializer,
            resourceProvider = resourceProvider,
            documentId = DOCUMENT_ID
        )
    }

    @After
    fun after() {
        Dispatchers.resetMain()
        closeable.close()
    }

    @Test
    fun `Given reissuance failure status changed for current document, When event is handled, Then details are reloaded`() =
        coroutineRule.runTest {
            viewModel.handleEvents(Event.OnReIssuanceFailureStatusChanged(listOf(DOCUMENT_ID)))
            testScope.advanceUntilIdle()

            verify(interactor).getDocumentDetails(DOCUMENT_ID)
        }

    @Test
    fun `Given reissuance failure status changed for another document, When event is handled, Then details are not reloaded`() =
        coroutineRule.runTest {
            viewModel.handleEvents(Event.OnReIssuanceFailureStatusChanged(listOf("other-document-id")))
            testScope.advanceUntilIdle()

            verify(interactor, never()).getDocumentDetails(DOCUMENT_ID)
        }

    @Test
    fun `Given reissuance succeeded for current document, When event is handled, Then screen is popped`() =
        coroutineRule.runTest {
            viewModel.effect.runFlowTest {
                viewModel.handleEvents(Event.OnReIssuanceStatusChanged(listOf(DOCUMENT_ID)))
                testScope.advanceUntilIdle()

                assertEquals(Effect.Navigation.Pop, awaitItem())
            }
            verify(interactor, never()).getDocumentDetails(DOCUMENT_ID)
        }

    @Test
    fun `Given present id is pressed, When event is handled, Then proximity QR opens for current document`() =
        coroutineRule.runTest {
            val configCaptor = argumentCaptor<RequestUriConfig>()
            whenever(
                uiSerializer.toBase64(
                    model = configCaptor.capture(),
                    parser = eq(RequestUriConfig.Parser)
                )
            ).thenReturn("present-id-config")

            viewModel.effect.runFlowTest {
                viewModel.handleEvents(Event.PresentIdPressed)
                testScope.advanceUntilIdle()

                val effect = awaitItem() as Effect.Navigation.SwitchScreen
                assertTrue(effect.screenRoute.contains("PROXIMITY_QR"))
                assertTrue(effect.screenRoute.contains("requestUriConfig=present-id-config"))
                assertEquals(DOCUMENT_ID, configCaptor.firstValue.presentingDocumentId)
                assertTrue(configCaptor.firstValue.mode is PresentationMode.Ble)
            }
        }

    private companion object {
        const val DOCUMENT_ID: String = "document-id"
    }
}
