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

import android.net.Uri
import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.businesslogic.controller.device.DeviceController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionRequest
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionStatus
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionType
import eu.europa.ec.dashboardfeature.ui.actions.model.DeviceLinkStatus
import eu.europa.ec.dashboardfeature.ui.actions.model.LinkedDeviceInfo
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.request.ActionRespondRequest
import eu.europa.ec.networklogic.model.request.CompletePairingRequest
import eu.europa.ec.networklogic.model.request.PresentationSubmissionDto
import eu.europa.ec.networklogic.model.response.ActionDto
import eu.europa.ec.notificationlogic.controller.UserScopedPushNotificationController
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

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
     * Completes device pairing after scanning the portal QR code.
     */
    suspend fun linkDevice(pairingPayload: String): Result<LinkedDeviceInfo>

    /**
     * Unlinks the current device from the portal.
     */
    suspend fun unlinkDevice(): Result<Unit>

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
    private val logController: LogController,
    private val deviceController: DeviceController,
    private val localUnlockTracker: LocalUnlockTracker,
    private val userScopedPushNotificationController: UserScopedPushNotificationController
) : ActionsRepository {

    companion object {
        private const val TAG = "ActionsRepository"
    }

    @Serializable
    private data class PairingQrPayload(
        @SerialName("t")
        val type: String,

        @SerialName("v")
        val version: Int,

        @SerialName("sid")
        val sessionId: String,

        @SerialName("cr")
        val challengeResponse: String,

        @SerialName("url")
        val completionUrl: String,

        @SerialName("exp")
        val expiresAtEpochSeconds: Long
    )

    private val pairingJson = Json {
        ignoreUnknownKeys = true
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

    /**
     * Gets the current authenticated user ID.
     * Protected and open to allow overriding in tests.
     */
    protected open suspend fun getCurrentUserId(): String? {
        return supabaseClient.auth.currentSessionOrNull()?.user?.id
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
                status = when (status) {
                    null, ActionStatus.PENDING -> "pending"
                    else -> "all"
                },
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

            val actions = body.data.map { dto -> mapActionDtoToRequest(dto) }
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

            val responseContext = getActionResponseContext()
                .getOrElse { return Result.failure(it) }

            val request = ActionRespondRequest(
                response = "accept",
                deviceId = responseContext.deviceId,
                biometricVerified = true,
                payload = buildAcceptPayload(vpToken, presentationSubmission)
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

            val responseContext = getActionResponseContext()
                .getOrElse { return Result.failure(it) }

            val request = ActionRespondRequest(
                response = "decline",
                deviceId = responseContext.deviceId,
                biometricVerified = true,
                payload = buildDeclinePayload(reason)
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

    override suspend fun linkDevice(pairingPayload: String): Result<LinkedDeviceInfo> {
        return try {
            val token = getAuthToken()
                ?: return Result.failure(Exception("Not authenticated"))
            val userId = getCurrentUserId()
                ?: return Result.failure(Exception("Not authenticated"))
            val parsedPayload = parsePairingPayload(pairingPayload)
                .getOrElse { return Result.failure(it) }
            val pushToken = userScopedPushNotificationController
                .registerForPushNotifications(userId)
                .getOrElse { error ->
                    logController.w(TAG) { "Failed to register push notifications for pairing: ${error.message}" }
                    return Result.failure(Exception("Unable to register this device for notifications", error))
                }

            val deviceInfo = deviceController.getDeviceInfo()
            val deviceName = deviceInfo.deviceName.ifBlank { android.os.Build.MODEL }
            val deviceModel = deviceInfo.deviceModel.ifBlank { null }

            val request = CompletePairingRequest(
                deviceName = deviceName,
                deviceModel = deviceModel,
                fcmToken = pushToken,
                challengeResponse = parsedPayload.challengeResponse
            )

            val response = apiClient.completePairing(
                completionUrl = parsedPayload.completionUrl,
                body = request,
                bearerToken = token
            )

            if (!response.isSuccessful) {
                logController.w(TAG) { "Failed to link device: ${response.code()} ${response.message()}" }
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

            val pairingResult = response.body()
                ?: return Result.failure(Exception("Pairing completed but the server response was empty"))
            if (!pairingResult.success) {
                return Result.failure(Exception("Pairing could not be completed"))
            }

            val linkedDevice = getDeviceStatus().getOrNull()?.deviceInfo
                ?: LinkedDeviceInfo(
                    deviceId = pairingResult.deviceId,
                    deviceName = deviceName,
                    deviceModel = deviceModel ?: deviceName,
                    linkedAt = Instant.now()
                ).also {
                    logController.w(TAG) {
                        "Pairing succeeded but device status refresh was unavailable; using completion response as source of truth"
                    }
                }

            logController.d(TAG, "Successfully linked device: ${linkedDevice.deviceId}")
            Result.success(linkedDevice)
        } catch (e: Exception) {
            logController.e(TAG, e)
            Result.failure(e)
        }
    }

    override suspend fun unlinkDevice(): Result<Unit> {
        return try {
            val token = getAuthToken()
                ?: return Result.failure(Exception("Not authenticated"))

            val response = apiClient.unlinkCurrentDevice(bearerToken = token)

            if (!response.isSuccessful) {
                logController.w(TAG) { "Failed to unlink device: ${response.code()} ${response.message()}" }
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

            logController.d(TAG, "Successfully unlinked current device")
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

            val status = if (body.hasLinkedDevice) DeviceLinkStatus.LINKED else DeviceLinkStatus.NOT_LINKED
            val deviceInfo = body.device?.let { dto ->
                LinkedDeviceInfo(
                    deviceId = dto.deviceId,
                    deviceName = dto.deviceName,
                    deviceModel = dto.deviceModel ?: dto.deviceName,
                    linkedAt = dto.linkedAt.parseInstantOrNull() ?: Instant.now()
                )
            }

            Result.success(DeviceLinkStatusResult(status, deviceInfo))
        } catch (e: Exception) {
            logController.e(TAG, e)
            Result.failure(e)
        }
    }

    private data class ActionResponseContext(
        val deviceId: String
    )

    private fun getActionResponseContext(): Result<ActionResponseContext> {
        if (!localUnlockTracker.isUnlocked()) {
            logController.w(TAG) { "Cannot respond to action - wallet is locked" }
            return Result.failure(Exception("Unlock the wallet before responding to actions"))
        }

        val deviceId = deviceController.getDeviceInfo().deviceId
        if (deviceId.isBlank()) {
            logController.w(TAG) { "Cannot respond to action - device id missing" }
            return Result.failure(Exception("Unable to determine this device"))
        }

        return Result.success(ActionResponseContext(deviceId = deviceId))
    }

    private fun parsePairingPayload(pairingPayload: String): Result<PairingQrPayload> {
        return try {
            val payload = pairingJson.decodeFromString(PairingQrPayload.serializer(), pairingPayload)

            if (payload.type != "authbound_pair") {
                return Result.failure(IllegalArgumentException("Unsupported pairing QR type"))
            }
            if (payload.version != 1) {
                return Result.failure(IllegalArgumentException("Unsupported pairing QR version"))
            }

            UUID.fromString(payload.sessionId)

            if (payload.challengeResponse.isBlank()) {
                return Result.failure(IllegalArgumentException("Pairing QR challenge response is missing"))
            }

            val completionUri = Uri.parse(payload.completionUrl)
            if (completionUri.scheme.isNullOrBlank() || completionUri.host.isNullOrBlank()) {
                return Result.failure(IllegalArgumentException("Pairing QR completion URL is invalid"))
            }

            val nowEpochSeconds = Instant.now().epochSecond
            if (payload.expiresAtEpochSeconds <= nowEpochSeconds) {
                return Result.failure(IllegalArgumentException("Pairing session has expired"))
            }

            Result.success(payload)
        } catch (e: Exception) {
            logController.w(TAG) { "Invalid pairing QR payload: ${e.message}" }
            Result.failure(IllegalArgumentException("Invalid pairing QR code", e))
        }
    }

    private fun buildAcceptPayload(
        vpToken: String?,
        presentationSubmission: PresentationSubmissionDto?
    ): JsonObject? {
        if (vpToken == null && presentationSubmission == null) {
            return null
        }

        return buildJsonObject {
            vpToken?.let { put("vp_token", JsonPrimitive(it)) }
            presentationSubmission?.let {
                put(
                    "presentation_submission",
                    Json.encodeToJsonElement(PresentationSubmissionDto.serializer(), it)
                )
            }
        }
    }

    private fun buildDeclinePayload(reason: String): JsonObject? {
        if (reason.isBlank()) {
            return null
        }

        return buildJsonObject {
            put("reason", JsonPrimitive(reason))
        }
    }

    private fun mapActionDtoToRequest(dto: ActionDto): ActionRequest {
        val type = when (dto.type.uppercase()) {
            "VERIFY_REQUEST" -> ActionType.VERIFY_REQUEST
            "SIGN_REQUEST" -> ActionType.SIGN_REQUEST
            "DATA_REQUEST" -> ActionType.DATA_REQUEST
            "CREDENTIAL_OFFER" -> ActionType.DATA_REQUEST
            else -> {
                logController.w(TAG) { "Unknown action type from backend: ${dto.type}" }
                ActionType.VERIFY_REQUEST
            }
        }

        return ActionRequest(
            id = dto.id,
            type = type,
            title = dto.title,
            requesterName = dto.requester.name,
            requesterLogoUrl = dto.requester.logoUrl,
            description = dto.description,
            timestamp = dto.createdAt.parseInstantOrNull() ?: Instant.now(),
            expiresAt = dto.expiresAt?.let { it.parseInstantOrNull() },
            status = ActionStatus.PENDING,
            metadata = buildMap {
                dto.priority?.let { put("priority", it) }
                dto.payload.forEach { (key, value) ->
                    put(
                        key,
                        when (value) {
                            is JsonPrimitive -> value.contentOrNull ?: value.toString()
                            else -> value.toString()
                        }
                    )
                }
            }
        )
    }
}
