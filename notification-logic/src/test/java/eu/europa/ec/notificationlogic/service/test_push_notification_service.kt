/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 */

package eu.europa.ec.notificationlogic.service

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.notificationlogic.controller.UserScopedPushNotificationController
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class TestPushNotificationService {

    private lateinit var controller: UserScopedPushNotificationController

    @Before
    fun before(): Unit {
        controller = mock(UserScopedPushNotificationController::class.java)
        runBlocking {
            whenever(controller.syncCurrentDeviceToken("rotated-token"))
                .thenReturn(Result.success(Unit))
        }
        startKoin {
            modules(
                module {
                    single<UserScopedPushNotificationController> { controller }
                    single<LogController> { mock(LogController::class.java) }
                }
            )
        }
    }

    @After
    fun after(): Unit {
        stopKoin()
    }

    @Test
    fun `Given Firebase rotates its token, When the callback fires, Then the authenticated device is synchronized`(): Unit {
        val service: PushNotificationService = Robolectric
            .buildService(PushNotificationService::class.java)
            .create()
            .get()

        service.onNewToken("rotated-token")

        runBlocking {
            verify(controller, timeout(2_000)).syncCurrentDeviceToken("rotated-token")
        }
    }
}
