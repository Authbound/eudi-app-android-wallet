/*
 * Copyright (c) 2025 European Commission
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

package eu.europa.ec.dashboardfeature.repository

import android.util.Base64
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.dashboardfeature.BuildConfig
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionRequest
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionStatus
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionType
import eu.europa.ec.dashboardfeature.ui.actions.model.DeviceLinkStatus
import eu.europa.ec.dashboardfeature.ui.actions.model.LinkedDeviceInfo
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.request.ActionRespondRequest
import eu.europa.ec.networklogic.model.request.DeviceInfoDto
import eu.europa.ec.networklogic.model.request.DeviceLinkRequest
import eu.europa.ec.networklogic.model.request.PresentationSubmissionDto
import eu.europa.ec.networklogic.model.response.ActionDto
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Repository for Actions feature backend integration.
 *
 * Handles fetching actions, responding to them, and device linking.
 */
interface ActionsRepository {
    /**
     * Fetches actions from the backend with optional filters.
     */
    suspend fun fetchActions(
        status: ActionStatus? = null,
        type: ActionType? = null
    ): Result<List<ActionRequest>>

    /**
     * Accepts an action with optional VP token for verification requests.
     */
    suspend fun acceptAction(
        actionId: String,
        vpToken: String? = null,
        presentationSubmission: PresentationSubmissionDto? = null
    ): Result<Unit>

    /**
     * Declines an action with optional reason.
     */
    suspend fun declineAction(
        actionId: String,
        reason: String = "user_cancelled"
    ): Result<Unit>

    /**
     * Links this device to the Authbound portal using a linking code.
     */
    suspend fun linkDevice(
        linkingCode: String,
        fcmToken: String?
    ): Result<LinkedDeviceInfo>

    /**
     * Unlinks the current device from the portal.
     */
    suspend fun unlinkDevice(deviceId: String): Result<Unit>

    /**
     * Gets the current device linking status.
     */
    suspend fun getDeviceStatus(): Result<DeviceLinkStatusResult>
}

/**
 * Result containing device link status and info.
 */
data class DeviceLinkStatusResult(
    val status: DeviceLinkStatus,
    val deviceInfo: LinkedDeviceInfo?
)

open class ActionsRepositoryImpl(
    private val apiClient: ApiClient,
    private val supabaseClient: SupabaseClient,
    private val resourceProvider: ResourceProvider,
    private val logController: LogController,
    private val cryptoController: CryptoController
) : ActionsRepository {

    companion object {
        private const val TAG = "ActionsRepository"
    }

    /**
     * Safely parses an ISO timestamp string to Instant.
     * Returns null if parsing fails, allowing callers to provide a fallback.
     */
    private fun String.parseInstantOrNull(): Instant? = try {
        Instant.parse(this)
    } catch (e: DateTimeParseException) {
        logController.w(TAG) { "Failed to parse timestamp: $this - ${e.message}" }
        null
    }

    /**
     * Gets the current authentication token.
     * Protected and open to allow overriding in tests.
     */
    protected open suspend fun getAuthToken(): String? {
        return supabaseClient.auth.currentSessionOrNull()?.accessToken
    }

    override suspend fun fetchActions(
        status: ActionStatus?,
        type: ActionType?
    ): Result<List<ActionRequest>> {
        return try {
            val token = getAuthToken()
                ?: return Result.failure(Exception("Not authenticated"))

            val response = apiClient.getActions(
                bearerToken = token,
                status = status?.name,
                type = type?.name
            )

            if (!response.isSuccessful) {
                if (response.code() == 404) {
                    logController.d(TAG, "Actions endpoint not available (404), returning empty list")
                    return Result.success(emptyList())
                }
                logController.w(TAG) { "Failed to fetch actions: ${response.code()} ${response.message()}" }
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

            val body = response.body()
                ?: return Result.failure(Exception("Empty response"))

            val actions = body.actions.map { dto -> mapActionDtoToRequest(dto) }
            logController.d(TAG, "Fetched ${actions.size} actions")
            Result.success(actions)
        } catch (e: Exception) {
            logController.e(TAG, e)
            Result.failure(e)
        }
    }

    override suspend fun acceptAction(
        actionId: String,
        vpToken: String?,
        presentationSubmission: PresentationSubmissionDto?
    ): Result<Unit> {
        return try {
            val token = getAuthToken()
                ?: return Result.failure(Exception("Not authenticated"))

            val signatureResult = createWuaSignature(actionId, "accept")
                ?: return Result.failure(Exception("Failed to sign action - wallet may not be activated"))

            val request = ActionRespondRequest(
                decision = "accept",
                vpToken = vpToken,
                presentationSubmission = presentationSubmission,
                wuaSignature = signatureResult.signature,
                signatureTimestamp = signatureResult.timestamp
            )

            val response = apiClient.respondToAction(
                actionId = actionId,
                body = request,
                bearerToken = token
            )

            if (!response.isSuccessful) {
                logController.w(TAG) { "Failed to accept action: ${response.code()} ${response.message()}" }
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

            logController.d(TAG, "Successfully accepted action: $actionId")
            Result.success(Unit)
        } catch (e: Exception) {
            logController.e(TAG, e)
            Result.failure(e)
        }
    }

    override suspend fun declineAction(
        actionId: String,
        reason: String
    ): Result<Unit> {
        return try {
            val token = getAuthToken()
                ?: return Result.failure(Exception("Not authenticated"))

            val signatureResult = createWuaSignature(actionId, "decline")
                ?: return Result.failure(Exception("Failed to sign action - wallet may not be activated"))

            val request = ActionRespondRequest(
                decision = "decline",
                declineReason = reason,
                wuaSignature = signatureResult.signature,
                signatureTimestamp = signatureResult.timestamp
            )

            val response = apiClient.respondToAction(
                actionId = actionId,
                body = request,
                bearerToken = token
            )

            if (!response.isSuccessful) {
                logController.w(TAG) { "Failed to decline action: ${response.code()} ${response.message()}" }
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

            logController.d(TAG, "Successfully declined action: $actionId")
            Result.success(Unit)
        } catch (e: Exception) {
            logController.e(TAG, e)
            Result.failure(e)
        }
    }

    override suspend fun linkDevice(
        linkingCode: String,
        fcmToken: String?
    ): Result<LinkedDeviceInfo> {
        return try {
            val token = getAuthToken()
                ?: return Result.failure(Exception("Not authenticated"))

            val wuaPublicKey = cryptoController.getWuaPublicKey()
            if (wuaPublicKey == null) {
                logController.w(TAG) { "Cannot link device - WUA key not found. Wallet must be activated first." }
                return Result.failure(Exception("Wallet not activated - please complete wallet activation before linking device"))
            }

            val deviceInfo = DeviceInfoDto(
                deviceName = android.os.Build.MODEL,
                deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                osVersion = "Android ${android.os.Build.VERSION.RELEASE}",
                appVersion = BuildConfig.APP_VERSION
            )

            val request = DeviceLinkRequest(
                linkingCode = linkingCode,
                deviceInfo = deviceInfo,
                fcmToken = fcmToken,
                wuaPublicKey = wuaPublicKey
            )

            val response = apiClient.linkDevice(
                body = request,
                bearerToken = token
            )

            if (!response.isSuccessful) {
                logController.w(TAG) { "Failed to link device: ${response.code()} ${response.message()}" }
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

            val body = response.body()
                ?: return Result.failure(Exception("Empty response"))

            val linkedDevice = LinkedDeviceInfo(
                deviceId = body.deviceId,
                deviceName = deviceInfo.deviceName,
                deviceModel = deviceInfo.deviceModel,
                linkedAt = body.linkedAt.parseInstantOrNull() ?: Instant.now()
            )

            logController.d(TAG, "Successfully linked device: ${body.deviceId}")
            Result.success(linkedDevice)
        } catch (e: Exception) {
            logController.e(TAG, e)
            Result.failure(e)
        }
    }

    override suspend fun unlinkDevice(deviceId: String): Result<Unit> {
        return try {
            val token = getAuthToken()
                ?: return Result.failure(Exception("Not authenticated"))

            val response = apiClient.unlinkDevice(
                deviceId = deviceId,
                bearerToken = token
            )

            if (!response.isSuccessful) {
                logController.w(TAG) { "Failed to unlink device: ${response.code()} ${response.message()}" }
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

            logController.d(TAG, "Successfully unlinked device: $deviceId")
            Result.success(Unit)
        } catch (e: Exception) {
            logController.e(TAG, e)
            Result.failure(e)
        }
    }

    override suspend fun getDeviceStatus(): Result<DeviceLinkStatusResult> {
        return try {
            val token = getAuthToken()
                ?: return Result.failure(Exception("Not authenticated"))

            val response = apiClient.getDeviceStatus(bearerToken = token)

            if (!response.isSuccessful) {
                if (response.code() == 404) {
                    logController.d(TAG, "Device status endpoint not available (404), defaulting to NOT_LINKED")
                    return Result.success(DeviceLinkStatusResult(DeviceLinkStatus.NOT_LINKED, null))
                }
                logController.w(TAG) { "Failed to get device status: ${response.code()} ${response.message()}" }
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

            val body = response.body()
                ?: return Result.failure(Exception("Empty response"))

            val status = if (body.isLinked) DeviceLinkStatus.LINKED else DeviceLinkStatus.NOT_LINKED
            val deviceInfo = body.deviceInfo?.let { dto ->
                LinkedDeviceInfo(
                    deviceId = dto.deviceId,
                    deviceName = dto.deviceName,
                    deviceModel = dto.deviceModel,
                    linkedAt = dto.linkedAt.parseInstantOrNull() ?: Instant.now()
                )
            }

            Result.success(DeviceLinkStatusResult(status, deviceInfo))
        } catch (e: Exception) {
            logController.e(TAG, e)
            Result.failure(e)
        }
    }

    /**
     * Result of creating a WUA signature containing both the signature and timestamp.
     */
    private data class WuaSignatureResult(
        val signature: String,
        val timestamp: Long
    )

    /**
     * Creates a WUA signature for action responses.
     *
     * The signature payload is a JSON object containing actionId, decision, and timestamp.
     * Using JSON encoding ensures special characters in actionId (like colons) don't
     * corrupt the payload structure.
     *
     * @param actionId The action being responded to
     * @param decision Either "accept" or "decline"
     * @return WuaSignatureResult containing Base64-encoded signature and timestamp, or null if signing fails
     */
    private fun createWuaSignature(actionId: String, decision: String): WuaSignatureResult? {
        val timestamp = System.currentTimeMillis()
        val signaturePayload = JSONObject().apply {
            put("actionId", actionId)
            put("decision", decision)
            put("timestamp", timestamp)
        }.toString()
        val signatureBytes = cryptoController.signData(signaturePayload.toByteArray())
        if (signatureBytes == null) {
            logController.w(TAG) { "WUA signing failed - key may not exist (wallet not activated)" }
            return null
        }
        val signature = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
        return WuaSignatureResult(signature, timestamp)
    }

    private fun mapActionDtoToRequest(dto: ActionDto): ActionRequest {
        val type = when (dto.type.uppercase()) {
            "VERIFY_REQUEST" -> ActionType.VERIFY_REQUEST
            "SIGN_REQUEST" -> ActionType.SIGN_REQUEST
            "DATA_REQUEST" -> ActionType.DATA_REQUEST
            else -> ActionType.VERIFY_REQUEST
        }

        val status = when (dto.status.uppercase()) {
            "PENDING" -> ActionStatus.PENDING
            "ACCEPTED" -> ActionStatus.ACCEPTED
            "DECLINED" -> ActionStatus.DECLINED
            "EXPIRED" -> ActionStatus.EXPIRED
            else -> ActionStatus.PENDING
        }

        val title = when (type) {
            ActionType.SIGN_REQUEST -> resourceProvider.getString(R.string.actions_type_sign_request)
            ActionType.VERIFY_REQUEST -> resourceProvider.getString(R.string.actions_type_verify_request)
            ActionType.DATA_REQUEST -> resourceProvider.getString(R.string.actions_type_data_request)
        }

        return ActionRequest(
            id = dto.id,
            type = type,
            title = title,
            requesterName = dto.requester.name,
            requesterLogoUrl = dto.requester.logoUrl,
            description = dto.description,
            timestamp = dto.createdAt.parseInstantOrNull() ?: Instant.now(),
            expiresAt = dto.expiresAt?.let { it.parseInstantOrNull() },
            status = status,
            metadata = buildMap {
                dto.openId4VpRequestUri?.let { put("openId4VpRequestUri", it) }
                dto.requestedCredentials?.let { put("requestedCredentials", it.joinToString(",")) }
                dto.requestedClaims?.let { put("requestedClaims", it.joinToString(",")) }
                dto.requester.verifierDid?.let { put("verifierDid", it) }
            }
        )
    }
}
