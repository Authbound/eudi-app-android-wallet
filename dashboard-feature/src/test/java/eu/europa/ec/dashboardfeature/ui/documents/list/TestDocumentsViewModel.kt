/*
 * Copyright (c) 2025 European Commission
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

package eu.europa.ec.dashboardfeature.ui.documents.list

import eu.europa.ec.businesslogic.validator.model.FilterableAttributes
import eu.europa.ec.businesslogic.validator.model.FilterableItem
import eu.europa.ec.businesslogic.validator.model.FilterableList
import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.dashboardfeature.interactor.AuthboundPidEntryInteractor
import eu.europa.ec.dashboardfeature.interactor.AuthboundPidEntryState
import eu.europa.ec.dashboardfeature.interactor.DocumentInteractorGetDocumentsPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentsInteractor
import eu.europa.ec.dashboardfeature.ui.documents.detail.model.DocumentIssuanceStateUi
import eu.europa.ec.dashboardfeature.ui.documents.list.model.DocumentUi
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.DualSelectorButton
import eu.europa.ec.uilogic.component.DualSelectorButtonDataUi
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.navigation.AuthboundPidScreens
import eu.europa.ec.uilogic.serializer.UiSerializer
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
class TestDocumentsViewModel {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @get:Rule
    val coroutineRule = CoroutineTestRule(testDispatcher, testScope)

    @Mock
    private lateinit var interactor: DocumentsInteractor

    @Mock
    private lateinit var authboundPidEntryInteractor: AuthboundPidEntryInteractor

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var uiSerializer: UiSerializer

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        whenever(resourceProvider.getString(any<Int>())).thenReturn("")
        runBlocking {
            whenever(authboundPidEntryInteractor.getEntryState()).thenReturn(
                AuthboundPidEntryState(
                    shouldShowEntry = false,
                    shouldShowHomePrompt = false
                )
            )
        }
    }

    @After
    fun after() {
        Dispatchers.resetMain()
        closeable.close()
    }

    @Test
    fun `Given Authbound PID is missing, When documents are fetched, Then Authbound entry is visible`() =
        coroutineRule.runTest {
            whenever(interactor.getDocuments()).thenReturn(
                flow {
                    emit(
                        DocumentInteractorGetDocumentsPartialState.Success(
                            allDocuments = FilterableList(emptyList()),
                            shouldAllowUserInteraction = true
                        )
                    )
                }
            )
            whenever(authboundPidEntryInteractor.getEntryState()).thenReturn(
                AuthboundPidEntryState(
                    shouldShowEntry = true,
                    shouldShowHomePrompt = true
                )
            )
            val viewModel = createViewModel()
            viewModel.setEvent(Event.GetDocuments)
            testScope.advanceUntilIdle()
            assertTrue(viewModel.viewState.value.shouldShowAuthboundPidEntry)
        }

    @Test
    fun `When Authbound PID add option is selected, Then navigates to Authbound PID intro`() =
        coroutineRule.runTest {
            whenever(authboundPidEntryInteractor.getEntryState()).thenReturn(
                AuthboundPidEntryState(
                    shouldShowEntry = true,
                    shouldShowHomePrompt = true
                )
            )
            val effects: MutableList<Effect> = mutableListOf()
            val viewModel = createViewModel()
            val collectJob = launch {
                viewModel.effect.take(2).toList(effects)
            }
            viewModel.setEvent(Event.BottomSheet.AddDocument.AuthboundPid)
            testScope.advanceUntilIdle()
            val navigationEffect = effects.filterIsInstance<Effect.Navigation.SwitchScreen>().first()
            assertEquals(AuthboundPidScreens.Intro.screenRoute, navigationEffect.screenRoute)
            collectJob.cancel()
        }

    @Test
    fun `Given Authbound PID state becomes hidden, When Authbound add option is selected, Then navigation is blocked`() =
        coroutineRule.runTest {
            whenever(interactor.getDocuments()).thenReturn(
                flow {
                    emit(
                        DocumentInteractorGetDocumentsPartialState.Success(
                            allDocuments = FilterableList(emptyList()),
                            shouldAllowUserInteraction = true
                        )
                    )
                }
            )
            whenever(authboundPidEntryInteractor.getEntryState()).thenReturn(
                AuthboundPidEntryState(
                    shouldShowEntry = true,
                    shouldShowHomePrompt = true
                ),
                AuthboundPidEntryState(
                    shouldShowEntry = false,
                    shouldShowHomePrompt = false
                )
            )
            val viewModel = createViewModel()
            viewModel.setEvent(Event.GetDocuments)
            testScope.advanceUntilIdle()
            val effects: MutableList<Effect> = mutableListOf()
            val collectJob = launch {
                viewModel.effect.toList(effects)
            }

            viewModel.setEvent(Event.BottomSheet.AddDocument.AuthboundPid)
            testScope.advanceUntilIdle()

            val hasAuthboundNavigation: Boolean =
                effects.filterIsInstance<Effect.Navigation.SwitchScreen>().any {
                    it.screenRoute == AuthboundPidScreens.Intro.screenRoute
                }
            assertFalse(viewModel.viewState.value.shouldShowAuthboundPidEntry)
            assertFalse(hasAuthboundNavigation)
            collectJob.cancel()
        }

    @Test
    fun `Given wallet has no documents, When GetDocuments succeeds, Then totalDocumentsCount is zero`() =
        coroutineRule.runTest {
            whenever(interactor.getDocuments()).thenReturn(
                flow {
                    emit(
                        DocumentInteractorGetDocumentsPartialState.Success(
                            allDocuments = FilterableList(emptyList()),
                            shouldAllowUserInteraction = true
                        )
                    )
                }
            )
            val viewModel = createViewModel()
            viewModel.setEvent(Event.GetDocuments)
            testScope.advanceUntilIdle()
            assertEquals(0, viewModel.viewState.value.totalDocumentsCount)
        }

    @Test
    fun `Given wallet has documents, When GetDocuments succeeds, Then totalDocumentsCount matches`() =
        coroutineRule.runTest {
            whenever(interactor.getDocuments()).thenReturn(
                flow {
                    emit(
                        DocumentInteractorGetDocumentsPartialState.Success(
                            allDocuments = FilterableList(
                                listOf(
                                    mockFilterableDocument(itemId = "doc1"),
                                    mockFilterableDocument(itemId = "doc2")
                                )
                            ),
                            shouldAllowUserInteraction = true
                        )
                    )
                }
            )
            val viewModel = createViewModel()
            viewModel.setEvent(Event.GetDocuments)
            testScope.advanceUntilIdle()
            assertEquals(2, viewModel.viewState.value.totalDocumentsCount)
        }

    @Test
    fun `Given no results and zero total documents, Then state resolves to empty wallet`() {
        val state = stateWith(showNoResultsFound = true, totalDocumentsCount = 0)
        assertTrue(state.isWalletEmpty)
        assertFalse(state.showNoMatches)
    }

    @Test
    fun `Given no results but existing documents, Then state resolves to no matches`() {
        val state = stateWith(showNoResultsFound = true, totalDocumentsCount = 2)
        assertFalse(state.isWalletEmpty)
        assertTrue(state.showNoMatches)
    }

    @Test
    fun `Given results are shown, Then neither empty wallet nor no matches resolves`() {
        val state = stateWith(showNoResultsFound = false, totalDocumentsCount = 2)
        assertFalse(state.isWalletEmpty)
        assertFalse(state.showNoMatches)
    }

    @Test
    fun `When EntranceAnimationCompleted, Then hasPlayedEntranceAnimation is true`() =
        coroutineRule.runTest {
            val viewModel = createViewModel()
            assertFalse(viewModel.viewState.value.hasPlayedEntranceAnimation)
            viewModel.setEvent(Event.EntranceAnimationCompleted)
            testScope.advanceUntilIdle()
            assertTrue(viewModel.viewState.value.hasPlayedEntranceAnimation)
        }

    private fun stateWith(
        showNoResultsFound: Boolean,
        totalDocumentsCount: Int,
    ): State {
        return State(
            isLoading = false,
            sortOrder = DualSelectorButtonDataUi(
                first = "",
                second = "",
                selectedButton = DualSelectorButton.FIRST
            ),
            isFilteringActive = false,
            showNoResultsFound = showNoResultsFound,
            totalDocumentsCount = totalDocumentsCount,
        )
    }

    private fun mockFilterableDocument(itemId: String): FilterableItem {
        return FilterableItem(
            payload = DocumentUi(
                documentIssuanceState = DocumentIssuanceStateUi.Issued,
                uiData = ListItemDataUi(
                    itemId = itemId,
                    mainContentData = ListItemMainContentDataUi.Text("test"),
                    overlineText = null,
                    supportingText = null,
                    leadingContentData = null,
                    trailingContentData = null
                ),
                documentIdentifier = DocumentIdentifier.MdocPid,
                documentCategory = DocumentCategory.Government,
            ),
            attributes = object : FilterableAttributes {
                override val searchTags: List<String>
                    get() = emptyList()
            }
        )
    }

    private fun createViewModel(): DocumentsViewModel {
        return DocumentsViewModel(
            interactor = interactor,
            authboundPidEntryInteractor = authboundPidEntryInteractor,
            resourceProvider = resourceProvider,
            uiSerializer = uiSerializer
        )
    }
}
