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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Enhanced push notification controller with user-scoped messaging.
 * Handles credential claims, verification requests, and other wallet notifications.
 */
interface UserScopedPushNotificationController {
    suspend fun registerForPushNotifications(userId: String): Result<String>
    suspend fun unregisterPushNotifications(userId: String): Result<Unit>
    
    // Notification flows
    fun observeCredentialClaims(): Flow<CredentialClaim>
    fun observeVerificationRequests(): Flow<VerificationRequest>
    fun observeGeneralNotifications(): Flow<WalletNotification>
    
    // Handle incoming notifications
    fun handleIncomingNotification(data: Map<String, String>)
    
    // Clear notifications for user
    fun clearUserNotifications(userId: String)
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
    CREDENTIAL_REVOKED,
    WALLET_UPDATE,
    SECURITY_ALERT,
    GENERAL
}

class UserScopedPushNotificationControllerImpl(
    private val firebaseMessaging: FirebaseMessaging,
    private val logController: LogController
) : UserScopedPushNotificationController {
    
    private val gson = Gson()
    
    private val _credentialClaims = MutableSharedFlow<CredentialClaim>(replay = 10)
    private val _verificationRequests = MutableSharedFlow<VerificationRequest>(replay = 10)
    private val _generalNotifications = MutableSharedFlow<WalletNotification>(replay = 20)
    
    private var currentUserId: String? = null
    
    override suspend fun registerForPushNotifications(userId: String): Result<String> = 
        suspendCoroutine { continuation ->
            try {
                currentUserId = userId
                logController.d("UserScopedPush", "Registering push notifications for user: ${userId.take(8)}...")
                
                firebaseMessaging.token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        val exception = task.exception ?: Exception("Unknown FCM error")
                        logController.e("UserScopedPush", exception)
                        continuation.resume(Result.failure(exception))
                        return@addOnCompleteListener
                    }

                    val token = task.result
                    logController.d("UserScopedPush", "FCM token obtained for user: ${userId.take(8)}...")
                    
                    // Subscribe to user-specific topics
                    subscribeToUserTopics(userId, token)
                    
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
                logController.d("UserScopedPush", "Unregistering push notifications for user: ${userId.take(8)}...")
                
                // Unsubscribe from user-specific topics
                unsubscribeFromUserTopics(userId)
                
                currentUserId = null
                continuation.resume(Result.success(Unit))
                
            } catch (e: Exception) {
                logController.e("UserScopedPush", e)
                continuation.resume(Result.failure(e))
            }
        }
    
    private fun subscribeToUserTopics(userId: String, token: String) {
        val userTopic = "user_$userId"
        val claimsTopic = "claims_$userId"
        val verificationTopic = "verification_$userId"
        
        firebaseMessaging.subscribeToTopic(userTopic)
            .addOnSuccessListener {
                logController.d("UserScopedPush", "Subscribed to user topic: $userTopic")
            }
            .addOnFailureListener { e ->
                logController.e("UserScopedPush", e)
            }
        
        firebaseMessaging.subscribeToTopic(claimsTopic)
            .addOnSuccessListener {
                logController.d("UserScopedPush", "Subscribed to claims topic: $claimsTopic")
            }
        
        firebaseMessaging.subscribeToTopic(verificationTopic)
            .addOnSuccessListener {
                logController.d("UserScopedPush", "Subscribed to verification topic: $verificationTopic")
            }
    }
    
    private fun unsubscribeFromUserTopics(userId: String) {
        val topics = listOf(
            "user_$userId",
            "claims_$userId",
            "verification_$userId"
        )
        
        topics.forEach { topic ->
            firebaseMessaging.unsubscribeFromTopic(topic)
                .addOnSuccessListener {
                    logController.d("UserScopedPush", "Unsubscribed from topic: $topic")
                }
                .addOnFailureListener { e ->
                    logController.w("UserScopedPush") { "Failed to unsubscribe from $topic: ${e.message}" }
                }
        }
    }
    
    override fun observeCredentialClaims(): Flow<CredentialClaim> = _credentialClaims.asSharedFlow()
    
    override fun observeVerificationRequests(): Flow<VerificationRequest> = _verificationRequests.asSharedFlow()
    
    override fun observeGeneralNotifications(): Flow<WalletNotification> = _generalNotifications.asSharedFlow()
    
    override fun handleIncomingNotification(data: Map<String, String>) {
        try {
            val notificationType = data["type"] ?: return
            val userId = data["user_id"] ?: return
            
            // Verify notification is for current user
            if (currentUserId != userId) {
                logController.w("UserScopedPush") { "Received notification for different user - ignoring" }
                return
            }
            
            logController.d("UserScopedPush", "Processing notification type: $notificationType for user: ${userId.take(8)}...")
            
            when (notificationType) {
                "credential_claim" -> handleCredentialClaimNotification(data)
                "verification_request" -> handleVerificationRequestNotification(data)
                else -> handleGeneralNotification(data)
            }
            
        } catch (e: Exception) {
            logController.e("UserScopedPush", e)
        }
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
                expiresAt = data["expires_at"]?.let { Instant.parse(it) },
                createdAt = data["created_at"]?.let { Instant.parse(it) } ?: Instant.now(),
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
                createdAt = data["created_at"]?.let { Instant.parse(it) } ?: Instant.now(),
                expiresAt = data["expires_at"]?.let { Instant.parse(it) } ?: Instant.now().plusSeconds(3600), // 1 hour default
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
    
    override fun clearUserNotifications(userId: String) {
        logController.d("UserScopedPush", "Clearing notifications for user: ${userId.take(8)}...")
        // Clear any cached notifications for the user
        // In a full implementation, this would clear local notification storage
    }
} 