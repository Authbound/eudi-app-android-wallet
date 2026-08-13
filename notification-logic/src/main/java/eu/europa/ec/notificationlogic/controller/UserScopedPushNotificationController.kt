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
package eu.europa.ec.notificationlogic.controller

import com.google.firebase.messaging.FirebaseMessaging
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.model.CredentialClaim
import eu.europa.ec.businesslogic.model.VerificationRequest
import eu.europa.ec.businesslogic.model.CredentialClaimStatus
import eu.europa.ec.businesslogic.model.VerificationRequestStatus
import eu.europa.ec.businesslogic.model.VerificationType
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.JsonParser
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.ApiResponse
import eu.europa.ec.networklogic.model.request.UpdateDeviceTokenRequest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Enhanced push notification controller with user-scoped messaging.
 * Handles credential claims, verification requests, action requests, and other wallet notifications.
 */
interface UserScopedPushNotificationController {
    suspend fun registerForPushNotifications(userId: String): Result<String>
    suspend fun unregisterPushNotifications(userId: String): Result<Unit>
    suspend fun syncCurrentDeviceToken(token: String? = null): Result<Unit>

    // Notification flows
    fun observeCredentialClaims(): Flow<CredentialClaim>
    fun observeVerificationRequests(): Flow<VerificationRequest>
    fun observeActionRequests(): Flow<ActionNotification>
    fun observeGeneralNotifications(): Flow<WalletNotification>

    // Handle incoming notifications
    fun handleIncomingNotification(data: Map<String, String>)

    // Clear notifications for user
    fun clearUserNotifications(userId: String)
}

/**
 * Notification for an action request received via FCM.
 */
data class ActionNotification(
    val actionId: String,
    val type: ActionRequestType,
    val requesterName: String,
    val description: String?,
    val expiresAt: Instant?,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Types of action requests that can be received via push notification.
 */
enum class ActionRequestType {
    VERIFY_REQUEST,
    SIGN_REQUEST,
    DATA_REQUEST
}

/**
 * General wallet notification model.
 */
data class WalletNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val data: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

enum class NotificationType {
    CREDENTIAL_CLAIM,
    VERIFICATION_REQUEST,
    ACTION_REQUEST,
    CREDENTIAL_REVOKED,
    WALLET_UPDATE,
    SECURITY_ALERT,
    GENERAL
}

internal fun ApiResponse<Unit>.isUnlinkedDeviceResponse(): Boolean {
    if (code() != 404) {
        return false
    }
    val responseBody: String = errorBody() ?: return false
    return runCatching {
        JsonParser.parseString(responseBody)
            .asJsonObject
            .getAsJsonObject("error")
            .get("code")
            .asString == "DEVICE_NOT_FOUND"
    }.getOrDefault(false)
}

class UserScopedPushNotificationControllerImpl(
    private val firebaseMessaging: FirebaseMessaging,
    private val logController: LogController,
    private val apiClient: ApiClient,
    private val supabaseClient: SupabaseClient
) : UserScopedPushNotificationController {

    companion object {
        private const val TAG = "UserScopedPush"
    }

    private val gson = Gson()

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
    
    private val _credentialClaims = MutableSharedFlow<CredentialClaim>(replay = 10)
    private val _verificationRequests = MutableSharedFlow<VerificationRequest>(replay = 10)
    private val _actionRequests = MutableSharedFlow<ActionNotification>(replay = 10)
    private val _generalNotifications = MutableSharedFlow<WalletNotification>(replay = 20)

    private val userScopeLock = Any()
    private var currentUserId: String? = null
    
    override suspend fun registerForPushNotifications(userId: String): Result<String> = 
        suspendCoroutine { continuation ->
            try {
                switchUserScope(userId)
                logController.d(TAG, "Registering push notifications")
                
                firebaseMessaging.token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        val exception = task.exception ?: Exception("Unknown FCM error")
                        logController.e("UserScopedPush", exception)
                        continuation.resume(Result.failure(exception))
                        return@addOnCompleteListener
                    }

                    val token = task.result
                    logController.d(TAG, "FCM token obtained")
                    continuation.resume(Result.success(token))
                }
            } catch (e: Exception) {
                logController.e("UserScopedPush", e)
                continuation.resume(Result.failure(e))
            }
        }
    
    override suspend fun unregisterPushNotifications(userId: String): Result<Unit> = 
        suspendCoroutine { continuation ->
            try {
                logController.d(TAG, "Unregistering push notifications")
                clearUserNotifications(userId)
                continuation.resume(Result.success(Unit))
                
            } catch (e: Exception) {
                logController.e("UserScopedPush", e)
                continuation.resume(Result.failure(e))
            }
        }

    override suspend fun syncCurrentDeviceToken(token: String?): Result<Unit> = runCatching {
        val session: UserSession = supabaseClient.auth.currentSessionOrNull()
            ?: return Result.success(Unit)
        val userId: String = session.user?.id
            ?: throw SecurityException("Authenticated session has no user")
        switchUserScope(userId)
        val currentToken: String = token ?: suspendCoroutine { continuation ->
            firebaseMessaging.token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    continuation.resume(task.result)
                } else {
                    continuation.resumeWith(
                        Result.failure(task.exception ?: Exception("Unknown FCM error"))
                    )
                }
            }
        }
        val response: ApiResponse<Unit> = apiClient.updateCurrentDeviceToken(
            body = UpdateDeviceTokenRequest(fcmToken = currentToken),
            bearerToken = session.accessToken
        )
        if (!response.isSuccessful && !response.isUnlinkedDeviceResponse()) {
            throw Exception("Failed to update push notification token: HTTP ${response.code()}")
        }
    }
    
    override fun observeCredentialClaims(): Flow<CredentialClaim> = _credentialClaims.asSharedFlow()

    override fun observeVerificationRequests(): Flow<VerificationRequest> = _verificationRequests.asSharedFlow()

    override fun observeActionRequests(): Flow<ActionNotification> = _actionRequests.asSharedFlow()

    override fun observeGeneralNotifications(): Flow<WalletNotification> = _generalNotifications.asSharedFlow()
    
    override fun handleIncomingNotification(data: Map<String, String>) {
        synchronized(userScopeLock) {
            try {
                val notificationType = data["type"] ?: return@synchronized
                if (notificationType == "wallet_refresh") {
                    handleOpaqueWalletRefresh(data)
                    return@synchronized
                }
                val userId = data["user_id"] ?: return@synchronized
                if (currentUserId != userId) {
                    logController.w(TAG) { "Received notification for different user - ignoring" }
                    return@synchronized
                }
                logController.d(TAG, "Processing notification type: $notificationType")
                when (notificationType) {
                    "credential_claim" -> handleCredentialClaimNotification(data)
                    "verification_request" -> handleVerificationRequestNotification(data)
                    "action_request" -> handleActionRequestNotification(data)
                    else -> handleGeneralNotification(data)
                }
            } catch (e: Exception) {
                logController.e(TAG, e)
            }
        }
    }

    private fun handleOpaqueWalletRefresh(data: Map<String, String>) {
        _generalNotifications.tryEmit(
            WalletNotification(
                id = data["created_at"] ?: System.currentTimeMillis().toString(),
                type = NotificationType.ACTION_REQUEST,
                title = "Authbound update available",
                message = "Open Authbound to refresh",
                data = data
            )
        )
    }
    
    private fun handleCredentialClaimNotification(data: Map<String, String>) {
        logController.d("UserScopedPush", "Processing credential claim notification")
        
        try {
            // First, try to parse the full CredentialClaim from JSON payload
            val credentialClaimJson = data["credential_claim_json"]
            if (credentialClaimJson != null) {
                try {
                    val credentialClaim = gson.fromJson(credentialClaimJson, CredentialClaim::class.java)
                    _credentialClaims.tryEmit(credentialClaim)
                    logController.d("UserScopedPush", "Successfully parsed CredentialClaim: ${credentialClaim.claimId}")
                } catch (e: JsonSyntaxException) {
                    logController.w("UserScopedPush") { "Failed to parse CredentialClaim JSON: ${e.message}" }
                    // Fall back to creating from individual fields
                    createCredentialClaimFromFields(data)
                }
            } else {
                // Create CredentialClaim from individual notification fields
                createCredentialClaimFromFields(data)
            }
        } catch (e: Exception) {
            logController.e("UserScopedPush", e)
            // Create a general notification as fallback
            createFallbackCredentialNotification(data)
        }
    }
    
    private fun createCredentialClaimFromFields(data: Map<String, String>) {
        try {
            val credentialClaim = CredentialClaim(
                claimId = data["claim_id"] ?: "",
                targetUserHandle = data["target_user_handle"] ?: "",
                credentialType = data["credential_type"] ?: "",
                issuerName = data["issuer_name"] ?: "",
                issuerDid = data["issuer_did"] ?: "",
                claimMessage = data["claim_message"],
                expiresAt = data["expires_at"]?.let { it.parseInstantOrNull() },
                createdAt = data["created_at"]?.let { it.parseInstantOrNull() } ?: Instant.now(),
                status = data["status"]?.let {
                    CredentialClaimStatus.valueOf(it.uppercase())
                } ?: CredentialClaimStatus.PENDING
            )
            
            _credentialClaims.tryEmit(credentialClaim)
            logController.d("UserScopedPush", "Created CredentialClaim from fields: ${credentialClaim.claimId}")
            
        } catch (e: Exception) {
            logController.w("UserScopedPush") { "Failed to create CredentialClaim from fields: ${e.message}" }
            createFallbackCredentialNotification(data)
        }
    }
    
    private fun createFallbackCredentialNotification(data: Map<String, String>) {
        val notification = WalletNotification(
            id = data["claim_id"] ?: System.currentTimeMillis().toString(),
            type = NotificationType.CREDENTIAL_CLAIM,
            title = "New Credential Available",
            message = "You have a new credential to claim from ${data["issuer_name"] ?: "an organization"}",
            data = data
        )
        
        _generalNotifications.tryEmit(notification)
        logController.d("UserScopedPush", "Created fallback notification for credential claim")
    }
    
    private fun handleVerificationRequestNotification(data: Map<String, String>) {
        logController.d("UserScopedPush", "Processing verification request notification")
        
        try {
            // First, try to parse the full VerificationRequest from JSON payload
            val verificationRequestJson = data["verification_request_json"]
            if (verificationRequestJson != null) {
                try {
                    val verificationRequest = gson.fromJson(verificationRequestJson, VerificationRequest::class.java)
                    _verificationRequests.tryEmit(verificationRequest)
                    logController.d("UserScopedPush", "Successfully parsed VerificationRequest: ${verificationRequest.requestId}")
                } catch (e: JsonSyntaxException) {
                    logController.w("UserScopedPush") { "Failed to parse VerificationRequest JSON: ${e.message}" }
                    // Fall back to creating from individual fields
                    createVerificationRequestFromFields(data)
                }
            } else {
                // Create VerificationRequest from individual notification fields
                createVerificationRequestFromFields(data)
            }
        } catch (e: Exception) {
            logController.e("UserScopedPush", e)
            // Create a general notification as fallback
            createFallbackVerificationNotification(data)
        }
    }
    
    private fun createVerificationRequestFromFields(data: Map<String, String>) {
        try {
            val requestedAttributes = data["requested_attributes"]?.split(",") ?: emptyList()
            
            val verificationRequest = VerificationRequest(
                requestId = data["request_id"] ?: "",
                requesterHandle = data["requester_handle"] ?: "",
                targetHandle = data["target_handle"] ?: "",
                verificationType = data["verification_type"]?.let {
                    VerificationType.valueOf(it.uppercase())
                } ?: VerificationType.IDENTITY_CHECK,
                requestedAttributes = requestedAttributes,
                message = data["message"],
                qrCode = data["qr_code"],
                createdAt = data["created_at"]?.let { it.parseInstantOrNull() } ?: Instant.now(),
                expiresAt = data["expires_at"]?.let { it.parseInstantOrNull() } ?: Instant.now().plusSeconds(3600), // 1 hour default
                status = data["status"]?.let {
                    VerificationRequestStatus.valueOf(it.uppercase())
                } ?: VerificationRequestStatus.PENDING
            )
            
            _verificationRequests.tryEmit(verificationRequest)
            logController.d("UserScopedPush", "Created VerificationRequest from fields: ${verificationRequest.requestId}")
            
        } catch (e: Exception) {
            logController.w("UserScopedPush") { "Failed to create VerificationRequest from fields: ${e.message}" }
            createFallbackVerificationNotification(data)
        }
    }
    
    private fun createFallbackVerificationNotification(data: Map<String, String>) {
        val notification = WalletNotification(
            id = data["request_id"] ?: System.currentTimeMillis().toString(),
            type = NotificationType.VERIFICATION_REQUEST,
            title = "Verification Request",
            message = "${data["requester_handle"] ?: "Someone"} wants to verify your credentials",
            data = data
        )
        
        _generalNotifications.tryEmit(notification)
        logController.d("UserScopedPush", "Created fallback notification for verification request")
    }
    
    private fun handleActionRequestNotification(data: Map<String, String>) {
        logController.d(TAG, "Processing action request notification")

        try {
            // Validate actionId is present and not empty
            val actionId = data["action_id"]
            if (actionId.isNullOrBlank()) {
                logController.w(TAG) { "Received action notification without valid actionId, skipping" }
                return
            }

            val rawActionType = data["action_type"]?.uppercase()
            val actionType = when (rawActionType) {
                "VERIFY_REQUEST" -> ActionRequestType.VERIFY_REQUEST
                "SIGN_REQUEST" -> ActionRequestType.SIGN_REQUEST
                "DATA_REQUEST" -> ActionRequestType.DATA_REQUEST
                else -> {
                    logController.w(TAG) { "Unknown action type: $rawActionType, defaulting to VERIFY_REQUEST" }
                    ActionRequestType.VERIFY_REQUEST
                }
            }

            val actionNotification = ActionNotification(
                actionId = actionId,
                type = actionType,
                requesterName = data["requester_name"] ?: "Unknown",
                description = data["description"],
                expiresAt = data["expires_at"]?.let { it.parseInstantOrNull() }
            )

            _actionRequests.tryEmit(actionNotification)
            logController.d(TAG, "Emitted ActionNotification: ${actionNotification.actionId}")

        } catch (e: Exception) {
            logController.e(TAG, e)
            // Create a general notification as fallback
            val notification = WalletNotification(
                id = data["action_id"] ?: System.currentTimeMillis().toString(),
                type = NotificationType.ACTION_REQUEST,
                title = "New Action Request",
                message = "${data["requester_name"] ?: "Someone"} requests your action",
                data = data
            )
            _generalNotifications.tryEmit(notification)
        }
    }

    private fun handleGeneralNotification(data: Map<String, String>) {
        val notification = WalletNotification(
            id = data["id"] ?: System.currentTimeMillis().toString(),
            type = NotificationType.GENERAL,
            title = data["title"] ?: "Wallet Notification",
            message = data["message"] ?: "",
            data = data
        )

        _generalNotifications.tryEmit(notification)
    }
    
    private fun switchUserScope(userId: String): Unit = synchronized(userScopeLock) {
        if (currentUserId != userId) {
            resetNotificationReplay()
            currentUserId = userId
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun clearUserNotifications(userId: String): Unit = synchronized(userScopeLock) {
        logController.d(TAG, "Clearing user notifications")
        if (currentUserId == null || currentUserId == userId) {
            currentUserId = null
            resetNotificationReplay()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun resetNotificationReplay(): Unit {
        _credentialClaims.resetReplayCache()
        _verificationRequests.resetReplayCache()
        _actionRequests.resetReplayCache()
        _generalNotifications.resetReplayCache()
    }
}
