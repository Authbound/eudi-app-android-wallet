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

package eu.europa.ec.dashboardfeature.ui.actions

import eu.europa.ec.dashboardfeature.interactor.ActionsInteractor
import eu.europa.ec.dashboardfeature.interactor.ActionsInteractorPartialState
import eu.europa.ec.notificationlogic.controller.NotificationType
import eu.europa.ec.notificationlogic.controller.UserScopedPushNotificationController
import eu.europa.ec.notificationlogic.controller.WalletNotification
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
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
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TestActionsViewModel {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val notifications = MutableSharedFlow<WalletNotification>(extraBufferCapacity = 1)

    @get:Rule
    val coroutineRule = CoroutineTestRule(testDispatcher, testScope)

    @Mock
    private lateinit var interactor: ActionsInteractor

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var uiSerializer: UiSerializer

    @Mock
    private lateinit var pushNotificationController: UserScopedPushNotificationController

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        whenever(pushNotificationController.observeGeneralNotifications()).thenReturn(notifications)
        whenever(resourceProvider.getString(any())).thenReturn("")
        whenever(interactor.getActions()).thenReturn(
            flowOf(
                ActionsInteractorPartialState.Success(
                    pendingCount = 0,
                    allActions = emptyList(),
                    groupedActions = emptyList()
                )
            )
        )
    }

    @After
    fun after() {
        Dispatchers.resetMain()
        closeable.close()
    }

    @Test
    fun `Given wallet refresh wake, When received, Then actions are fetched with current auth`() =
        coroutineRule.runTest {
            createViewModel()
            testScope.advanceUntilIdle()

            notifications.emit(
                WalletNotification(
                    id = "2026-07-10T00:00:00Z",
                    type = NotificationType.ACTION_REQUEST,
                    title = "Authbound update available",
                    message = "Open Authbound to refresh",
                    data = mapOf(
                        "type" to "wallet_refresh",
                        "created_at" to "2026-07-10T00:00:00Z"
                    )
                )
            )
            testScope.advanceUntilIdle()

            verify(interactor, times(1)).getActions()
        }

    private fun createViewModel() = ActionsViewModel(
        interactor = interactor,
        resourceProvider = resourceProvider,
        uiSerializer = uiSerializer,
        pushNotificationController = pushNotificationController
    )
}
