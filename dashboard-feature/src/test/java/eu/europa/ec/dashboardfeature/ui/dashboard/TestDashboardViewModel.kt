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

package eu.europa.ec.dashboardfeature.ui.dashboard

import eu.europa.ec.dashboardfeature.interactor.AuthboundPidEntryInteractor
import eu.europa.ec.dashboardfeature.interactor.AuthboundPidEntryState
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractor
import eu.europa.ec.dashboardfeature.ui.dashboard.model.SideMenuItemUi
import eu.europa.ec.dashboardfeature.ui.dashboard.model.SideMenuTypeUi
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.serializer.UiSerializer
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TestDashboardViewModel {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @get:Rule
    val coroutineRule = CoroutineTestRule(testDispatcher, testScope)

    @Mock
    private lateinit var dashboardInteractor: DashboardInteractor

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
        whenever(dashboardInteractor.getAppVersion()).thenReturn("1.0.0")
        whenever(dashboardInteractor.getSideMenuOptions()).thenReturn(emptyList())
        whenever(dashboardInteractor.getSideMenuOptions(false)).thenReturn(emptyList())
        whenever(dashboardInteractor.getSideMenuOptions(true)).thenReturn(authboundPidMenuOptions)
    }

    @After
    fun after() {
        Dispatchers.resetMain()
        closeable.close()
    }

    @Test
    fun `Given Authbound PID entry is allowed, When side menu opens, Then refreshed menu includes Authbound action`() =
        coroutineRule.runTest {
            whenever(authboundPidEntryInteractor.getEntryState()).thenReturn(
                AuthboundPidEntryState(
                    shouldShowEntry = true,
                    shouldShowHomePrompt = false
                )
            )
            val viewModel = createViewModel()
            viewModel.setEvent(Event.SideMenu.Open)
            testScope.advanceUntilIdle()
            assertTrue(viewModel.viewState.value.isSideMenuVisible)
            assertEquals(authboundPidMenuOptions, viewModel.viewState.value.sideMenuOptions)
        }

    @Test
    fun `Given Authbound PID entry is still allowed, When side menu Authbound item clicked, Then quick action is emitted`() =
        coroutineRule.runTest {
            whenever(authboundPidEntryInteractor.getEntryState()).thenReturn(
                AuthboundPidEntryState(
                    shouldShowEntry = true,
                    shouldShowHomePrompt = false
                )
            )
            val viewModel = createViewModel()
            viewModel.effect.runFlowTest {
                viewModel.setEvent(Event.SideMenu.ItemClicked(SideMenuTypeUi.AUTHBOUND_PID))
                testScope.advanceUntilIdle()
                val effect = awaitItem() as Effect.TriggerQuickAction
                assertEquals("authboundpid", effect.actionId)
            }
        }

    @Test
    fun `Given Authbound PID entry is no longer allowed, When side menu Authbound item clicked, Then no quick action is emitted`() =
        coroutineRule.runTest {
            whenever(authboundPidEntryInteractor.getEntryState()).thenReturn(
                AuthboundPidEntryState(
                    shouldShowEntry = false,
                    shouldShowHomePrompt = false
                )
            )
            val viewModel = createViewModel()
            viewModel.effect.runFlowTest {
                viewModel.setEvent(Event.SideMenu.ItemClicked(SideMenuTypeUi.AUTHBOUND_PID))
                testScope.advanceUntilIdle()
                expectNoEvents()
            }
            assertFalse(viewModel.viewState.value.isSideMenuVisible)
            assertEquals(emptyList<SideMenuItemUi>(), viewModel.viewState.value.sideMenuOptions)
        }

    private fun createViewModel(): DashboardViewModel {
        return DashboardViewModel(
            dashboardInteractor = dashboardInteractor,
            authboundPidEntryInteractor = authboundPidEntryInteractor,
            uiSerializer = uiSerializer,
            resourceProvider = resourceProvider
        )
    }

    private val authboundPidMenuOptions: List<SideMenuItemUi> = listOf(
        SideMenuItemUi.ActionItem(
            type = SideMenuTypeUi.AUTHBOUND_PID,
            data = ListItemDataUi(
                itemId = "authboundpid",
                mainContentData = ListItemMainContentDataUi.Text("Get Authbound ID")
            )
        )
    )
}
