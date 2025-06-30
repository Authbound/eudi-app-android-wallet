/*
 * Copyright (c) 2024 European Commission
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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface PushNotificationController {
    suspend fun registerForPushNotifications(): Result<String>
}

class PushNotificationControllerImpl(
    private val firebaseMessaging: FirebaseMessaging
) : PushNotificationController {
    override suspend fun registerForPushNotifications(): Result<String> = suspendCoroutine { continuation ->
        firebaseMessaging.token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                continuation.resume(Result.failure(task.exception!!))
                return@addOnCompleteListener
            }

            val token = task.result
            continuation.resume(Result.success(token))
        }
    }
} 