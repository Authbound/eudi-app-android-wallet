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
package eu.europa.ec.walletactivationlogic.repository


import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth

import android.util.Base64
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.model.DeviceInfo

import eu.europa.ec.networklogic.api.ApiClient

import eu.europa.ec.networklogic.model.request.WalletActivationRequest
import eu.europa.ec.networklogic.model.request.EnhancedDeviceInfo
import eu.europa.ec.networklogic.model.response.WalletActivationResponse

import java.security.cert.Certificate



interface WalletActivationRepository {
    suspend fun activateWallet(
        publicKey: Certificate,
        attestationChain: Array<Certificate>,
        deviceInfo: DeviceInfo,
        pushToken: String,
    ): Result<WalletActivationResponse>
}

class WalletActivationRepositoryImpl(
    private val supabaseClient: SupabaseClient,
    private val api: ApiClient,
    private val logController: LogController
) : WalletActivationRepository {
    override suspend fun activateWallet(
        publicKey: Certificate,
        attestationChain: Array<Certificate>,
        deviceInfo: DeviceInfo,
        pushToken: String,
    ): Result<WalletActivationResponse> {
        return try {
            val token = supabaseClient.auth.currentSessionOrNull()?.accessToken
                ?: return Result.failure(Exception("User not authenticated"))

            // Create comprehensive device security assessment for WUA
            val enhancedDeviceInfo = createEnhancedDeviceInfo(deviceInfo)

            val request = WalletActivationRequest(
                pushNotificationToken = pushToken,
                wuaPublicKey = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP),
                deviceInfo = enhancedDeviceInfo,
                pushNotificationProvider = "fcm"
            )

            logController.d("WalletActivation", "Enhanced Device Security Assessment: $enhancedDeviceInfo")
            val response = api.activateWallet(request, token)
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    logController.d("WalletActivation", "WUA Success: $responseBody")
                    Result.success(responseBody)
                } else {
                    logController.e("WalletActivation", Exception("WUA failed. Error body is null"))
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                logController.e("WalletActivation", Exception(errorMsg))
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            logController.e("WalletActivation", e)
            Result.failure(e)
        }
    }

    /**
     * Creates enhanced device information with comprehensive security assessment
     * required for WUA (Wallet Unit Attestation) evaluation.
     * 
     * This assessment determines device security capabilities and LoA High compliance
     * as required by EUDI wallet specifications.
     */
    private fun createEnhancedDeviceInfo(deviceInfo: DeviceInfo): EnhancedDeviceInfo {
        val isHardwareBacked = deviceInfo.hasHardwareKeystore || deviceInfo.hasSecureElement
        val hasAdvancedSecurity = deviceInfo.hasStrongBox || 
                                 (deviceInfo.hasSecureElement && deviceInfo.attestationSupported)
        
        // Assess overall device security level for WUA evaluation
        val securityLevel = assessDeviceSecurityLevel(deviceInfo)
        val meetsLoAHigh = evaluateLoAHighCompliance(deviceInfo)
        
        return EnhancedDeviceInfo(
            deviceModel = deviceInfo.deviceModel,
            osVersion = deviceInfo.deviceOs,
            deviceOsApiLevel = deviceInfo.deviceOsVersion,
            securityPatchLevel = deviceInfo.securityPatchLevel,
            hasSecureElement = deviceInfo.hasSecureElement,
            hasHardwareKeystore = deviceInfo.hasHardwareKeystore,
            hasStrongBox = deviceInfo.hasStrongBox,
            attestationSupported = deviceInfo.attestationSupported,
            hasBiometricHardware = deviceInfo.hasBiometricHardware,
            deviceVerifiedBoot = deviceInfo.deviceVerifiedBoot,
            playProtectVerified = deviceInfo.playProtectVerified,
            securityLevel = securityLevel,
            isHardwareBacked = isHardwareBacked,
            meetsLoAHighRequirements = meetsLoAHigh
        )
    }

    /**
     * Assesses overall device security level based on hardware capabilities.
     * Used for WUA risk assessment and trust establishment.
     */
    private fun assessDeviceSecurityLevel(deviceInfo: DeviceInfo): String {
        return when {
            // HIGH: StrongBox + Hardware attestation + Verified boot
            deviceInfo.hasStrongBox && 
            deviceInfo.attestationSupported && 
            deviceInfo.deviceVerifiedBoot -> "HIGH"
            
            // MEDIUM: Hardware security + some verification
            (deviceInfo.hasSecureElement || deviceInfo.hasHardwareKeystore) &&
            (deviceInfo.attestationSupported || deviceInfo.deviceVerifiedBoot) -> "MEDIUM"
            
            // LOW: Limited hardware security
            else -> "LOW"
        }
    }

    /**
     * Evaluates if device meets LoA High requirements for EUDI wallet compliance.
     * 
     * LoA High requires:
     * - Hardware-backed key storage
     * - Device integrity verification
     * - Strong authentication capabilities
     * - Up-to-date security patches
     */
    private fun evaluateLoAHighCompliance(deviceInfo: DeviceInfo): Boolean {
        val hasHardwareSecurity = deviceInfo.hasHardwareKeystore || deviceInfo.hasSecureElement
        val hasIntegrityVerification = deviceInfo.deviceVerifiedBoot && deviceInfo.playProtectVerified
        val hasAuthenticationCapabilities = deviceInfo.hasBiometricHardware || deviceInfo.attestationSupported
        val hasRecentPatches = isSecurityPatchRecent(deviceInfo.securityPatchLevel)
        
        return hasHardwareSecurity && 
               hasIntegrityVerification && 
               hasAuthenticationCapabilities && 
               hasRecentPatches
    }

    /**
     * Checks if security patch level is recent enough for LoA High requirements.
     */
    private fun isSecurityPatchRecent(securityPatchLevel: String): Boolean {
        return try {
            // Basic check - should be within last 6 months for LoA High
            // Format is typically "YYYY-MM-DD"
            val patchParts = securityPatchLevel.split("-")
            if (patchParts.size >= 2) {
                val patchYear = patchParts[0].toInt()
                val patchMonth = patchParts[1].toInt()
                val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
                
                // Calculate months difference
                val monthsDiff = (currentYear - patchYear) * 12 + (currentMonth - patchMonth)
                monthsDiff <= 6 // Within 6 months
            } else {
                false
            }
        } catch (e: Exception) {
            logController.w("WalletActivation",) { "Could not parse security patch level: $securityPatchLevel"}
            false
        }
    }
} 