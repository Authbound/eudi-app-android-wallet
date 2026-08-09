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

package eu.europa.ec.dashboardfeature.ui.home

import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.dashboardfeature.interactor.AuthboundPidEntryInteractor
import eu.europa.ec.dashboardfeature.interactor.AuthboundPidEntryState
import eu.europa.ec.dashboardfeature.interactor.HomeInteractor
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorGetCredentialsPartialState
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorGetHeroCredentialPartialState
import eu.europa.ec.dashboardfeature.ui.documents.detail.model.DocumentIssuanceStateUi
import eu.europa.ec.dashboardfeature.ui.documents.list.model.DocumentUi
import eu.europa.ec.dashboardfeature.ui.home.model.HeroCredentialUi
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.navigation.AuthboundPidScreens
import eu.europa.ec.uilogic.serializer.UiSerializer
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TestHomeViewModel {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @get:Rule
    val coroutineRule = CoroutineTestRule(testDispatcher, testScope)

    @Mock
    private lateinit var homeInteractor: HomeInteractor

    @Mock
    private lateinit var authboundPidEntryInteractor: AuthboundPidEntryInteractor

    @Mock
    private lateinit var uiSerializer: UiSerializer

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        whenever(resourceProvider.getString(any<Int>())).thenReturn("")
        whenever(homeInteractor.isBleCentralClientModeEnabled()).thenReturn(false)
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

    // Test 1: GetCredentials event fetches and updates credentials in state
    @Test
    fun `Given verification is incomplete, When initial state is created, Then verify quick action is not shown`() {
        val viewModel = createViewModel()

        val hasVerifyQuickAction: Boolean = viewModel.viewState.value.quickActions.any { action ->
            action.id == "verify"
        }

        assertFalse(hasVerifyQuickAction)
    }

    @Test
    fun `Given verification is incomplete, When verify quick action event is received, Then verification sheet is not opened`() {
        val viewModel = createViewModel()

        viewModel.setEvent(Event.QuickActionPressed("verify"))

        assertFalse(viewModel.viewState.value.isBottomSheetOpen)
    }

    @Test
    fun `Given credentials available, When GetCredentials event, Then state contains credentials`() =
        coroutineRule.runTest {
            // Given
            val mockCredentials = listOf(
                DocumentCategory.Government to listOf(createDocumentUi("doc-1", "PID Document"))
            )
            whenever(homeInteractor.getCredentials()).thenReturn(
                flow {
                    emit(HomeInteractorGetCredentialsPartialState.Success(mockCredentials))
                }
            )
            whenever(homeInteractor.getHeroCredential()).thenReturn(
                flow {
                    emit(HomeInteractorGetHeroCredentialPartialState.Success(emptyList()))
                }
            )

            // When
            val viewModel = createViewModel()
            viewModel.setEvent(Event.GetCredentials)
            testScope.advanceUntilIdle()

            // Then
            assertEquals(mockCredentials, viewModel.viewState.value.credentials)
            assertFalse(viewModel.viewState.value.isLoadingCredentials)
        }

    // Test 2: GetCredentials refreshes with new data (simulates post-issuance refresh)
    @Test
    fun `Given new credential issued, When GetCredentials dispatched again, Then state updates with new credentials`() =
        coroutineRule.runTest {
            // Given - initial credentials
            val oldCredentials = listOf(
                DocumentCategory.Government to listOf(createDocumentUi("doc-1", "PID Document"))
            )
            whenever(homeInteractor.getCredentials()).thenReturn(
                flow {
                    emit(HomeInteractorGetCredentialsPartialState.Success(oldCredentials))
                }
            )
            whenever(homeInteractor.getHeroCredential()).thenReturn(
                flow {
                    emit(HomeInteractorGetHeroCredentialPartialState.Success(emptyList()))
                }
            )

            val viewModel = createViewModel()
            viewModel.setEvent(Event.GetCredentials)
            testScope.advanceUntilIdle()
            assertEquals(oldCredentials, viewModel.viewState.value.credentials)

            // When - new credential issued, GetCredentials dispatched again
            val newCredentials = listOf(
                DocumentCategory.Government to listOf(
                    createDocumentUi("doc-1", "PID Document"),
                    createDocumentUi("doc-2", "mDL Document")
                )
            )
            whenever(homeInteractor.getCredentials()).thenReturn(
                flow {
                    emit(HomeInteractorGetCredentialsPartialState.Success(newCredentials))
                }
            )

            viewModel.setEvent(Event.GetCredentials)
            testScope.advanceUntilIdle()

            // Then
            assertEquals(newCredentials, viewModel.viewState.value.credentials)
        }

    // Test 3: GetCredentials failure shows empty message
    @Test
    fun `Given interactor fails, When GetCredentials event, Then showEmptyCredentialsMessage is true`() =
        coroutineRule.runTest {
            // Given
            whenever(homeInteractor.getCredentials()).thenReturn(
                flow {
                    emit(HomeInteractorGetCredentialsPartialState.Failure("error"))
                }
            )
            whenever(homeInteractor.getHeroCredential()).thenReturn(
                flow {
                    emit(HomeInteractorGetHeroCredentialPartialState.Success(emptyList()))
                }
            )

            // When
            val viewModel = createViewModel()
            viewModel.setEvent(Event.GetCredentials)
            testScope.advanceUntilIdle()

            // Then
            assertTrue(viewModel.viewState.value.showEmptyCredentialsMessage)
            assertFalse(viewModel.viewState.value.isLoadingCredentials)
        }

    // Test 4: Authbound PID entry points are visible when no Authbound PID exists
    @Test
    fun `Given Authbound PID is missing, When GetCredentials event, Then Authbound entry state is visible`() =
        coroutineRule.runTest {
            // Given
            whenever(homeInteractor.getCredentials()).thenReturn(
                flow {
                    emit(HomeInteractorGetCredentialsPartialState.Success(emptyList()))
                }
            )
            whenever(homeInteractor.getHeroCredential()).thenReturn(
                flow {
                    emit(HomeInteractorGetHeroCredentialPartialState.Success(emptyList()))
                }
            )
            whenever(authboundPidEntryInteractor.getEntryState()).thenReturn(
                AuthboundPidEntryState(
                    shouldShowEntry = true,
                    shouldShowHomePrompt = true
                )
            )

            // When
            val viewModel = createViewModel()
            viewModel.setEvent(Event.GetCredentials)
            testScope.advanceUntilIdle()

            // Then
            assertTrue(viewModel.viewState.value.shouldShowAuthboundPidEntry)
            assertTrue(viewModel.viewState.value.shouldShowAuthboundPidHomePrompt)
        }

    // Test 5: Home promo dismissal does not hide stable Authbound PID entry points
    @Test
    fun `Given Authbound PID prompt is visible, When Not now pressed, Then only Home prompt is hidden`() =
        coroutineRule.runTest {
            // Given
            whenever(authboundPidEntryInteractor.snoozeHomePrompt()).thenReturn(Unit)
            whenever(homeInteractor.getCredentials()).thenReturn(
                flow {
                    emit(HomeInteractorGetCredentialsPartialState.Success(emptyList()))
                }
            )
            whenever(homeInteractor.getHeroCredential()).thenReturn(
                flow {
                    emit(HomeInteractorGetHeroCredentialPartialState.Success(emptyList()))
                }
            )
            whenever(authboundPidEntryInteractor.getEntryState()).thenReturn(
                AuthboundPidEntryState(
                    shouldShowEntry = true,
                    shouldShowHomePrompt = true
                )
            )
            val viewModel = createViewModel()
            viewModel.setEvent(Event.GetCredentials)
            testScope.advanceUntilIdle()

            // When
            viewModel.setEvent(Event.AuthboundPidPromoNotNowPressed)
            testScope.advanceUntilIdle()

            // Then
            assertTrue(viewModel.viewState.value.shouldShowAuthboundPidEntry)
            assertFalse(viewModel.viewState.value.shouldShowAuthboundPidHomePrompt)
        }

    @Test
    fun `Given Authbound PID state becomes hidden, When Home promo is pressed, Then navigation is blocked`() =
        coroutineRule.runTest {
            whenever(homeInteractor.getCredentials()).thenReturn(
                flow {
                    emit(HomeInteractorGetCredentialsPartialState.Success(emptyList()))
                }
            )
            whenever(homeInteractor.getHeroCredential()).thenReturn(
                flow {
                    emit(HomeInteractorGetHeroCredentialPartialState.Success(emptyList()))
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
            viewModel.setEvent(Event.GetCredentials)
            testScope.advanceUntilIdle()
            val effects: MutableList<Effect> = mutableListOf()
            val collectJob = launch {
                viewModel.effect.toList(effects)
            }

            viewModel.setEvent(Event.GetAuthboundIdPressed)
            testScope.advanceUntilIdle()

            val hasAuthboundNavigation: Boolean =
                effects.filterIsInstance<Effect.Navigation.SwitchScreen>().any {
                    it.screenRoute == AuthboundPidScreens.Intro.screenRoute
                }
            assertFalse(viewModel.viewState.value.shouldShowAuthboundPidEntry)
            assertFalse(viewModel.viewState.value.shouldShowAuthboundPidHomePrompt)
            assertFalse(hasAuthboundNavigation)
            collectJob.cancel()
        }

    @Test
    fun `Given Authbound PID state becomes hidden, When Add Credential Authbound option is pressed, Then navigation is blocked`() =
        coroutineRule.runTest {
            whenever(homeInteractor.getCredentials()).thenReturn(
                flow {
                    emit(HomeInteractorGetCredentialsPartialState.Success(emptyList()))
                }
            )
            whenever(homeInteractor.getHeroCredential()).thenReturn(
                flow {
                    emit(HomeInteractorGetHeroCredentialPartialState.Success(emptyList()))
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
            viewModel.setEvent(Event.GetCredentials)
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
            assertFalse(viewModel.viewState.value.shouldShowAuthboundPidHomePrompt)
            assertFalse(hasAuthboundNavigation)
            collectJob.cancel()
        }

    @Test
    fun `Given hero credential is pressed, When credential is known, Then document details opens`() =
        coroutineRule.runTest {
            val selectedDocumentId = "hero-document-id"
            val heroCredentials = listOf(createHeroCredential(selectedDocumentId))
            whenever(homeInteractor.getCredentials()).thenReturn(
                flow {
                    emit(HomeInteractorGetCredentialsPartialState.Success(emptyList()))
                }
            )
            whenever(homeInteractor.getHeroCredential()).thenReturn(
                flow {
                    emit(HomeInteractorGetHeroCredentialPartialState.Success(heroCredentials))
                }
            )
            val viewModel = createViewModel()
            val effects: MutableList<Effect> = mutableListOf()
            val collectJob = launch {
                viewModel.effect.toList(effects)
            }
            viewModel.setEvent(Event.GetCredentials)
            testScope.advanceUntilIdle()

            viewModel.setEvent(Event.HeroCredentialPressed(selectedDocumentId))
            testScope.advanceUntilIdle()

            val navigationEffect = effects.filterIsInstance<Effect.Navigation.SwitchScreen>().last()
            assertTrue(navigationEffect.screenRoute.contains("DOCUMENT_DETAILS"))
            assertTrue(navigationEffect.screenRoute.contains("documentId=$selectedDocumentId"))
            assertFalse(viewModel.viewState.value.isBottomSheetOpen)
            collectJob.cancel()
        }

    @Test
    fun `Given present id is pressed, When proximity flow starts, Then present id sheet opens with selected document`() =
        coroutineRule.runTest {
            val selectedDocumentId = "hero-document-id"
            val heroCredentials = listOf(createHeroCredential(selectedDocumentId))
            whenever(homeInteractor.isBleAvailable()).thenReturn(true)
            whenever(homeInteractor.getCredentials()).thenReturn(
                flow {
                    emit(HomeInteractorGetCredentialsPartialState.Success(emptyList()))
                }
            )
            whenever(homeInteractor.getHeroCredential()).thenReturn(
                flow {
                    emit(HomeInteractorGetHeroCredentialPartialState.Success(heroCredentials))
                }
            )
            whenever(homeInteractor.startPresentIdEngagement()).thenReturn(emptyFlow())
            val viewModel = createViewModel()
            viewModel.setEvent(Event.GetCredentials)
            testScope.advanceUntilIdle()

            viewModel.setEvent(Event.PresentIdPressed(selectedDocumentId))
            testScope.advanceUntilIdle()
            assertEquals(selectedDocumentId, viewModel.viewState.value.selectedHeroCredentialDocumentId)
            viewModel.setEvent(Event.StartProximityFlow)
            testScope.advanceUntilIdle()

            val requestConfigCaptor = argumentCaptor<RequestUriConfig>()
            verify(homeInteractor).setPresentIdConfig(requestConfigCaptor.capture())
            assertEquals(selectedDocumentId, requestConfigCaptor.firstValue.presentingDocumentId)
            assertTrue(requestConfigCaptor.firstValue.mode is PresentationMode.Ble)
            assertEquals(null, viewModel.viewState.value.selectedHeroCredentialDocumentId)
            assertEquals(selectedDocumentId, viewModel.viewState.value.presentIdDocumentId)
            assertEquals(
                requestConfigCaptor.firstValue.presentationScopeId,
                viewModel.viewState.value.presentIdPresentationScopeId
            )
            assertTrue(viewModel.viewState.value.sheetContent is HomeScreenBottomSheetContent.PresentId)
        }

    //region Helper Methods

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            homeInteractor = homeInteractor,
            authboundPidEntryInteractor = authboundPidEntryInteractor,
            uiSerializer = uiSerializer,
            resourceProvider = resourceProvider
        )
    }

    private fun createDocumentUi(id: String, name: String): DocumentUi {
        return DocumentUi(
            documentIssuanceState = DocumentIssuanceStateUi.Issued,
            uiData = ListItemDataUi(
                itemId = id,
                mainContentData = ListItemMainContentDataUi.Text(text = name),
                overlineText = null,
                supportingText = null,
                leadingContentData = ListItemLeadingContentDataUi.Icon(
                    iconData = AppIcons.Documents
                )
            ),
            documentIdentifier = DocumentIdentifier.MdocPid,
            documentCategory = DocumentCategory.Government
        )
    }

    private fun createHeroCredential(documentId: String): HeroCredentialUi {
        return HeroCredentialUi(
            documentId = documentId,
            documentIdentifier = DocumentIdentifier.MdocPid,
            title = "PID Document",
            subtitle = null,
            holderName = "JAN ANDERSSON",
            issuerName = "Authbound",
            expiryDate = "11/07/2026",
            status = DocumentIssuanceStateUi.Issued,
            hasPhoto = false,
            portraitBase64 = null,
            nationality = "FIN"
        )
    }

    //endregion
}
