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
package eu.europa.ec.notificationlogic.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import eu.europa.ec.notificationlogic.controller.UserScopedPushNotificationController
import eu.europa.ec.businesslogic.controller.log.LogController
import org.koin.android.ext.android.inject

class PushNotificationService : FirebaseMessagingService() {

    private val userScopedController: UserScopedPushNotificationController by inject()
    private val logController: LogController by inject()

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        try {
            logController.d("PushNotificationService", "Received message from: ${remoteMessage.from}")
            
            // Extract data payload
            val data = remoteMessage.data
            
            if (data.isNotEmpty()) {
                logController.d("PushNotificationService", "Message data payload: $data")
                // Route message to user-scoped controller for processing
                userScopedController.handleIncomingNotification(data)
            } else {
                logController.w("PushNotificationService") { "Received message with empty data payload" }
            }
            
            // Handle notification payload if present
            remoteMessage.notification?.let { notification ->
                logController.d("PushNotificationService", "Message notification: ${notification.title}")
                // For display notifications, we could show system notifications here
                // but for EUDI wallet, we primarily use data messages for security
            }
            
        } catch (e: Exception) {
            logController.e("PushNotificationService", e)
        }
    }

    override fun onNewToken(token: String) {
        try {
            logController.d("PushNotificationService", "New FCM token received: ${token.take(16)}...")
            
            // Store the new token for future registration
            // The actual registration with backend happens when user authenticates
            // and the UserScopedPushNotificationController.registerForPushNotifications is called
            
            // TODO: If user is already authenticated, we should update the token on the backend
            // This would require access to authentication state and backend API
            
        } catch (e: Exception) {
            logController.e("PushNotificationService", e)
        }
    }
} 