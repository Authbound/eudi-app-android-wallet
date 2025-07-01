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
package eu.europa.ec.businesslogic.model

import com.google.gson.annotations.SerializedName

data class DeviceInfo(
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("device_name")
    val deviceName: String,
    @SerializedName("device_model")
    val deviceModel: String,
    @SerializedName("os_version")
    val deviceOs: String,
    @SerializedName("device_os_version")
    val deviceOsVersion: String,
    @SerializedName("security_patch_level")
    val securityPatchLevel: String,
    @SerializedName("has_secure_element")
    val hasSecureElement: Boolean,
    @SerializedName("has_hardware_keystore")
    val hasHardwareKeystore: Boolean,
    @SerializedName("has_strongbox")
    val hasStrongBox: Boolean,
    @SerializedName("attestation_supported")
    val attestationSupported: Boolean,
    @SerializedName("device_verified_boot")
    val deviceVerifiedBoot: Boolean,
    @SerializedName("play_protect_verified")
    val playProtectVerified: Boolean,
    @SerializedName("has_biometric_hardware")
    val hasBiometricHardware: Boolean
)