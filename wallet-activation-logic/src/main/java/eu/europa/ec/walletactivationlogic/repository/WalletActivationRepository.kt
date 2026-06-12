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

import eu.europa.ec.authenticationlogic.controller.storage.WalletRecoveryChallengeController
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.model.DeviceInfo
import eu.europa.ec.businesslogic.model.error.HttpErrorResponse
import eu.europa.ec.businesslogic.model.error.WalletActivationError
import eu.europa.ec.businesslogic.model.error.toWalletActivationError
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.ApiResponse
import eu.europa.ec.networklogic.model.request.EnhancedDeviceInfo
import eu.europa.ec.networklogic.model.request.WalletActivationRequest
import eu.europa.ec.networklogic.model.response.AttestationChallengeResponse
import eu.europa.ec.networklogic.model.response.WalletActivationResponse
import android.util.Base64
import java.security.cert.Certificate

interface WalletActivationRepository {
    suspend fun getAttestationChallenge(): Result<AttestationChallengeResponse>

    suspend fun activateWallet(
        attestationChain: Array<Certificate>,
        challengeId: String,
        deviceInfo: DeviceInfo,
        pushToken: String,
    ): Result<WalletActivationResponse>

    suspend fun deleteWalletActivation(): Result<Unit>
}

open class WalletActivationRepositoryImpl(
    private val supabaseClient: SupabaseClient,
    private val api: ApiClient,
    private val logController: LogController,
    private val walletRecoveryChallengeController: WalletRecoveryChallengeController
) : WalletActivationRepository {

    /**
     * Returns the current Supabase auth token.
     * Protected and open to allow overriding in tests.
     */
    protected open suspend fun getAuthToken(): String? {
        return supabaseClient.auth.currentSessionOrNull()?.accessToken
    }

    override suspend fun getAttestationChallenge(): Result<AttestationChallengeResponse> {
        return try {
            val preparedChallenge: AttestationChallengeResponse? =
                walletRecoveryChallengeController.consumePreparedChallenge()
            if (preparedChallenge != null) {
                logController.d("WalletActivation", "Using prepared recovery challenge")
                return Result.success(preparedChallenge)
            }
            val token = getAuthToken()
                ?: return Result.failure(
                    WalletActivationError.AuthenticationFailure("User not authenticated")
                )

            logController.d("WalletActivation", "Fetching attestation challenge...")
            val response = api.getAttestationChallenge(token)

            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    logController.d("WalletActivation", "Challenge received")
                    Result.success(responseBody)
                } else {
                    logController.e("WalletActivation", Exception("Challenge response body is null"))
                    Result.failure(
                        WalletActivationError.ServerRejection(
                            httpCode = response.code(),
                            serverMessage = "Empty response body"
                        )
                    )
                }
            } else {
                val error = parseHttpError(response)
                logController.e("WalletActivation", Exception("Challenge fetch failed: ${error.message}"))
                Result.failure(error)
            }
        } catch (e: WalletActivationError) {
            logController.e("WalletActivation", e)
            Result.failure(e)
        } catch (e: Exception) {
            logController.e("WalletActivation", e)
            Result.failure(e.toWalletActivationError())
        }
    }

    override suspend fun activateWallet(
        attestationChain: Array<Certificate>,
        challengeId: String,
        deviceInfo: DeviceInfo,
        pushToken: String,
    ): Result<WalletActivationResponse> {
        return try {
            val token = getAuthToken()
                ?: return Result.failure(
                    WalletActivationError.AuthenticationFailure("User not authenticated")
                )

            // Create comprehensive device security assessment for WUA
            val enhancedDeviceInfo = createEnhancedDeviceInfo(deviceInfo)

            // Convert certificate chain to Base64-encoded strings
            val base64AttestationChain = attestationChain.map { cert ->
                Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
            }

            val request = WalletActivationRequest(
                attestationChain = base64AttestationChain,
                challengeId = challengeId,
                deviceInfo = enhancedDeviceInfo,
                pushNotificationToken = pushToken,
                pushNotificationProvider = "fcm"
            )

            logController.d("WalletActivation", "Device security assessment prepared")
            val response = api.activateWallet(request, token)

            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    logController.d("WalletActivation", "WUA activation succeeded")
                    Result.success(responseBody)
                } else {
                    logController.e("WalletActivation", Exception("WUA failed. Error body is null"))
                    Result.failure(
                        WalletActivationError.ServerRejection(
                            httpCode = response.code(),
                            serverMessage = "Empty response body"
                        )
                    )
                }
            } else {
                logController.e("WalletActivation") { "WUA HTTP ${response.code()}" }
                val error = parseHttpError(response, challengeId)
                logController.e("WalletActivation", Exception("WUA activation failed"))
                Result.failure(error)
            }
        } catch (e: WalletActivationError) {
            logController.e("WalletActivation", e)
            Result.failure(e)
        } catch (e: Exception) {
            logController.e("WalletActivation", e)
            Result.failure(e.toWalletActivationError())
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
            logController.w("WalletActivation") { "Could not parse security patch level: $securityPatchLevel" }
            false
        }
    }

    override suspend fun deleteWalletActivation(): Result<Unit> {
        return try {
            val token = getAuthToken()
                ?: return Result.failure(
                    WalletActivationError.AuthenticationFailure("User not authenticated")
                )

            logController.d("WalletActivation", "Deleting wallet activation...")
            val response = api.deleteWalletActivation(token)

            if (response.isSuccessful) {
                logController.d("WalletActivation", "Wallet activation deleted successfully")
                Result.success(Unit)
            } else {
                val error = parseHttpError(response)
                logController.e("WalletActivation", Exception("Delete failed: ${error.message}"))
                Result.failure(error)
            }
        } catch (e: WalletActivationError) {
            logController.e("WalletActivation", e)
            Result.failure(e)
        } catch (e: Exception) {
            logController.e("WalletActivation", e)
            Result.failure(e.toWalletActivationError())
        }
    }

    /**
     * Parses HTTP error response into a typed WalletActivationError.
     * Extracts error body for context from the ApiResponse.
     */
    private fun <T> parseHttpError(
        response: ApiResponse<T>,
        challengeId: String? = null
    ): WalletActivationError {
        val errorBody = response.errorBody()

        val httpError = HttpErrorResponse(
            code = response.code(),
            message = response.message(),
            errorBody = errorBody,
            retryAfterSeconds = null
        )

        // For challenge-related errors, inject the challenge ID if available
        return when {
            response.code() == 409 -> {
                WalletActivationError.WalletAlreadyExists(httpCode = 409)
            }
            response.code() == 410 && challengeId != null -> {
                WalletActivationError.ChallengeExpired(challengeId)
            }
            response.code() == 404 && challengeId != null &&
                    errorBody?.contains("challenge", ignoreCase = true) == true -> {
                WalletActivationError.ChallengeNotFound(challengeId)
            }
            else -> httpError.toWalletActivationError()
        }
    }
}
