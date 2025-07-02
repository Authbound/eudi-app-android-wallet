/*
 * Copyright (c) 2023 European Commission
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

package eu.europa.ec.networklogic.model.request

import com.google.gson.annotations.SerializedName

// Simplified DeviceInfo that matches backend schema
data class SimpleDeviceInfo(
    @SerializedName("osVersion")
    val osVersion: String,
    @SerializedName("deviceModel") 
    val deviceModel: String,
    @SerializedName("isHardwareBacked")
    val isHardwareBacked: Boolean
)

data class WalletActivationRequest(
    @SerializedName("wuaPublicKey")
    val wuaPublicKey: String,
    @SerializedName("deviceInfo")
    val deviceInfo: SimpleDeviceInfo,
    @SerializedName("pushNotificationToken")
    val pushNotificationToken: String?,
    @SerializedName("pushNotificationProvider")
    val pushNotificationProvider: String? = "fcm"
)
