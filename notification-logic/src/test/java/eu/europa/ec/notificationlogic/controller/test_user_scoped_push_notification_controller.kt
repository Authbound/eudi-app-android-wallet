/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 */

package eu.europa.ec.notificationlogic.controller

import com.google.firebase.messaging.FirebaseMessaging
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.ApiResponse
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun before(): Unit {
        closeable = MockitoAnnotations.openMocks(this)
    }

    @After
    fun after(): Unit {
        closeable.close()
    }

    @Test
    fun `Given a process restart, When an opaque wallet refresh arrives, Then a refresh wake is emitted`(): Unit = runTest {
        val controller: UserScopedPushNotificationController = createController()
        val notificationData: Map<String, String> = mapOf(
            "type" to "wallet_refresh",
            "created_at" to "2026-07-10T00:00:00Z"
        )

        controller.handleIncomingNotification(notificationData)

        val notification: WalletNotification? = withTimeoutOrNull(100) {
            controller.observeGeneralNotifications().first()
        }
        assertNotNull("Opaque refresh must work before user scope is restored", notification)
        assertEquals(NotificationType.ACTION_REQUEST, notification?.type)
        assertEquals(notificationData, notification?.data)
    }

    @Test
    fun `Given replayed notifications, When user state is cleared, Then a later observer receives nothing`(): Unit = runTest {
        val controller: UserScopedPushNotificationController = createController()
        controller.handleIncomingNotification(mapOf("type" to "wallet_refresh"))

        controller.clearUserNotifications("user-1")

        val replayed: WalletNotification? = withTimeoutOrNull(100) {
            controller.observeGeneralNotifications().first()
        }
        assertNull("Cleared user state must not replay notifications", replayed)
    }

    @Test
    fun `Given HTTP 404 responses, When classifying token sync, Then only the producer device error is benign`(): Unit {
        val missingDevice: ApiResponse<Unit> = ApiResponse.Error(
            code = 404,
            message = "Not Found",
            errorBody = """{"success":false,"error":{"code":"DEVICE_NOT_FOUND"}}"""
        )
        val missingRoute: ApiResponse<Unit> = ApiResponse.Error(
            code = 404,
            message = "Not Found",
            errorBody = """{"message":"Route not found"}"""
        )

        assertTrue(missingDevice.isUnlinkedDeviceResponse())
        assertFalse(missingRoute.isUnlinkedDeviceResponse())
    }

    private fun createController(): UserScopedPushNotificationController =
        UserScopedPushNotificationControllerImpl(
            firebaseMessaging = firebaseMessaging,
            logController = logController,
            apiClient = apiClient,
            supabaseClient = supabaseClient
        )
}
