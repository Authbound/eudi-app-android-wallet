/*
 * Copyright (c) 2024 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon as they will be approved by the European
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
package eu.europa.ec.notificationlogic.di

import com.google.firebase.messaging.FirebaseMessaging
import eu.europa.ec.notificationlogic.controller.PushNotificationController
import eu.europa.ec.notificationlogic.controller.PushNotificationControllerImpl
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.dsl.module


@Module
@ComponentScan("eu.europa.ec.notificationlogic")
class LogicNotificationModule

@Single
fun provideFirebaseMessaging(): FirebaseMessaging {
    return FirebaseMessaging.getInstance()
}

@Single
fun providePushNotificationController(firebaseMessaging: FirebaseMessaging): PushNotificationController {
    return PushNotificationControllerImpl(firebaseMessaging)
}
