/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 */
package eu.europa.ec.notificationlogic.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class TestPushNotificationPrivacy {

    @Test
    fun `push service never logs payload contents or notification titles`() {
        val serviceSource = File(
            "src/main/java/eu/europa/ec/notificationlogic/service/PushNotificationService.kt"
        ).readText()
        val controllerSource = File(
            "src/main/java/eu/europa/ec/notificationlogic/controller/UserScopedPushNotificationController.kt"
        ).readText()

        assertFalse(serviceSource.contains("Message data payload"))
        assertFalse(serviceSource.contains("notification.title"))
        assertFalse(serviceSource.contains("remoteMessage.from"))
        assertFalse(controllerSource.contains("userId.take"))
    }
}
