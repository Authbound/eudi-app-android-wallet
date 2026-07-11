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

package eu.europa.ec.notificationlogic.controller

import com.google.firebase.messaging.FirebaseMessaging
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.networklogic.api.ApiClient
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class TestUserScopedPushNotificationController {

    @Mock
    private lateinit var firebaseMessaging: FirebaseMessaging

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var apiClient: ApiClient

    @Mock
    private lateinit var supabaseClient: SupabaseClient

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `Given process restart, When wallet refresh arrives, Then generic wake is emitted`() = runTest {
        // Given
        val controller = UserScopedPushNotificationControllerImpl(
            firebaseMessaging = firebaseMessaging,
            logController = logController,
            apiClient = apiClient,
            supabaseClient = supabaseClient
        )
        val notificationData = mapOf(
            "type" to "wallet_refresh",
            "created_at" to "2026-07-10T00:00:00Z"
        )

        // When
        controller.handleIncomingNotification(notificationData)

        // Then
        val notification = withTimeoutOrNull(100) {
            controller.observeGeneralNotifications().first()
        }
        assertNotNull("Generic wallet refresh should survive process restart", notification)
        assertEquals(NotificationType.ACTION_REQUEST, notification?.type)
        assertEquals(notificationData, notification?.data)
    }

    @Test
    fun `Given process restart, When user scoped notification arrives, Then it is ignored`() = runTest {
        // Given
        val controller = UserScopedPushNotificationControllerImpl(
            firebaseMessaging = firebaseMessaging,
            logController = logController,
            apiClient = apiClient,
            supabaseClient = supabaseClient
        )

        // When
        controller.handleIncomingNotification(
            mapOf(
                "type" to "action_request",
                "user_id" to "user-123"
            )
        )

        // Then
        val notification = withTimeoutOrNull(100) {
            controller.observeActionRequests().first()
        }
        assertNull("User-scoped notifications still require the active user", notification)
    }

    @Test
    fun `Given replayed notifications, When user state is cleared, Then a later observer receives nothing`() = runTest {
        val controller = UserScopedPushNotificationControllerImpl(
            firebaseMessaging = firebaseMessaging,
            logController = logController,
            apiClient = apiClient,
            supabaseClient = supabaseClient
        )
        controller.handleIncomingNotification(
            mapOf("type" to "wallet_refresh", "created_at" to "2026-07-10T00:00:00Z")
        )

        controller.clearUserNotifications("user-1")

        val replayed = withTimeoutOrNull(100) {
            controller.observeGeneralNotifications().first()
        }
        assertNull("Cleared user state must not replay notifications", replayed)
    }
}
