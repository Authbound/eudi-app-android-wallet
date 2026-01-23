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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Enhanced device information for WUA (Wallet Unit Attestation) security assessment.
 *
 * This comprehensive device profile enables proper security evaluation and trust
 * establishment as required by EUDI wallet specifications for LoA High compliance.
 */
@Serializable
data class EnhancedDeviceInfo(
    // Basic device identification
    @SerialName("deviceModel")
    val deviceModel: String,

    @SerialName("osVersion")
    val osVersion: String,

    @SerialName("deviceOsApiLevel")
    val deviceOsApiLevel: String,

    @SerialName("securityPatchLevel")
    val securityPatchLevel: String,

    // Hardware security capabilities - Critical for WUA assessment
    @SerialName("hasSecureElement")
    val hasSecureElement: Boolean,

    @SerialName("hasHardwareKeystore")
    val hasHardwareKeystore: Boolean,

    @SerialName("hasStrongBox")
    val hasStrongBox: Boolean,

    @SerialName("attestationSupported")
    val attestationSupported: Boolean,

    @SerialName("hasBiometricHardware")
    val hasBiometricHardware: Boolean,

    // Device integrity and verification
    @SerialName("deviceVerifiedBoot")
    val deviceVerifiedBoot: Boolean,

    @SerialName("playProtectVerified")
    val playProtectVerified: Boolean,

    // Derived security assessment
    @SerialName("securityLevel")
    val securityLevel: String, // "HIGH", "MEDIUM", "LOW"

    @SerialName("isHardwareBacked")
    val isHardwareBacked: Boolean,

    @SerialName("meetsLoAHighRequirements")
    val meetsLoAHighRequirements: Boolean
)

@Serializable
data class WalletActivationRequest(
    @SerialName("wuaPublicKey")
    val wuaPublicKey: String,
    @SerialName("attestationChain")
    val attestationChain: List<String>,
    @SerialName("challengeId")
    val challengeId: String,
    @SerialName("deviceInfo")
    val deviceInfo: EnhancedDeviceInfo,
    @SerialName("pushNotificationToken")
    val pushNotificationToken: String?,
    @SerialName("pushNotificationProvider")
    val pushNotificationProvider: String? = "fcm"
)
