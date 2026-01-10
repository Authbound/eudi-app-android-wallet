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

/**
 * Enhanced device information for WUA (Wallet Unit Attestation) security assessment.
 * 
 * This comprehensive device profile enables proper security evaluation and trust 
 * establishment as required by EUDI wallet specifications for LoA High compliance.
 */
data class EnhancedDeviceInfo(
    // Basic device identification
    @SerializedName("deviceModel")
    val deviceModel: String,
    
    @SerializedName("osVersion")
    val osVersion: String,
    
    @SerializedName("deviceOsApiLevel")
    val deviceOsApiLevel: String,
    
    @SerializedName("securityPatchLevel")
    val securityPatchLevel: String,
    
    // Hardware security capabilities - Critical for WUA assessment
    @SerializedName("hasSecureElement")
    val hasSecureElement: Boolean,
    
    @SerializedName("hasHardwareKeystore")
    val hasHardwareKeystore: Boolean,
    
    @SerializedName("hasStrongBox")
    val hasStrongBox: Boolean,
    
    @SerializedName("attestationSupported")
    val attestationSupported: Boolean,
    
    @SerializedName("hasBiometricHardware")
    val hasBiometricHardware: Boolean,
    
    // Device integrity and verification
    @SerializedName("deviceVerifiedBoot")
    val deviceVerifiedBoot: Boolean,
    
    @SerializedName("playProtectVerified")
    val playProtectVerified: Boolean,
    
    // Derived security assessment
    @SerializedName("securityLevel")
    val securityLevel: String, // "HIGH", "MEDIUM", "LOW"
    
    @SerializedName("isHardwareBacked")
    val isHardwareBacked: Boolean,
    
    @SerializedName("meetsLoAHighRequirements")
    val meetsLoAHighRequirements: Boolean
)

data class WalletActivationRequest(
    @SerializedName("wuaPublicKey")
    val wuaPublicKey: String,
    @SerializedName("attestationChain")
    val attestationChain: List<String>,
    @SerializedName("challengeId")
    val challengeId: String,
    @SerializedName("deviceInfo")
    val deviceInfo: EnhancedDeviceInfo,
    @SerializedName("pushNotificationToken")
    val pushNotificationToken: String?,
    @SerializedName("pushNotificationProvider")
    val pushNotificationProvider: String? = "fcm"
)
