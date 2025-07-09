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
import java.time.Instant

/**
 * User identity model for EUDI wallet users.
 * Supports P2P verification and credential claiming flows.
 */
data class UserIdentity(
    @SerializedName("user_id")
    val userId: String,
    
    @SerializedName("handle")
    val handle: String, // @username format for easy sharing
    
    @SerializedName("display_name")
    val displayName: String?,
    
    @SerializedName("email")
    val email: String,
    
    @SerializedName("verification_status")
    val verificationStatus: UserVerificationStatus,
    
    @SerializedName("public_key")
    val publicKey: String?, // For P2P verification
    
    @SerializedName("created_at")
    val createdAt: Instant,
    
    @SerializedName("last_active")
    val lastActive: Instant,
    
    @SerializedName("wallet_activated")
    val walletActivated: Boolean = false
)

/**
 * User verification status for trust establishment in P2P interactions.
 */
enum class UserVerificationStatus {
    @SerializedName("unverified")
    UNVERIFIED,
    
    @SerializedName("email_verified")
    EMAIL_VERIFIED,
    
    @SerializedName("identity_verified")
    IDENTITY_VERIFIED, // Has valid PID
    
    @SerializedName("organization_verified")
    ORGANIZATION_VERIFIED // Verified by organization
}

/**
 * Credential claim notification for users.
 */
data class CredentialClaim(
    @SerializedName("claim_id")
    val claimId: String,
    
    @SerializedName("target_user_handle")
    val targetUserHandle: String,
    
    @SerializedName("credential_type")
    val credentialType: String,
    
    @SerializedName("issuer_name")
    val issuerName: String,
    
    @SerializedName("issuer_did")
    val issuerDid: String,
    
    @SerializedName("claim_message")
    val claimMessage: String?,
    
    @SerializedName("expires_at")
    val expiresAt: Instant?,
    
    @SerializedName("created_at")
    val createdAt: Instant,
    
    @SerializedName("status")
    val status: CredentialClaimStatus
)

enum class CredentialClaimStatus {
    @SerializedName("pending")
    PENDING,
    
    @SerializedName("claimed")
    CLAIMED,
    
    @SerializedName("expired")
    EXPIRED,
    
    @SerializedName("rejected")
    REJECTED
}

/**
 * P2P verification request between users.
 */
data class VerificationRequest(
    @SerializedName("request_id")
    val requestId: String,
    
    @SerializedName("requester_handle")
    val requesterHandle: String,
    
    @SerializedName("target_handle")
    val targetHandle: String,
    
    @SerializedName("verification_type")
    val verificationType: VerificationType,
    
    @SerializedName("requested_attributes")
    val requestedAttributes: List<String>,
    
    @SerializedName("message")
    val message: String?,
    
    @SerializedName("qr_code")
    val qrCode: String?, // For QR-based verification
    
    @SerializedName("created_at")
    val createdAt: Instant,
    
    @SerializedName("expires_at")
    val expiresAt: Instant,
    
    @SerializedName("status")
    val status: VerificationRequestStatus
)

enum class VerificationType {
    @SerializedName("identity_check")
    IDENTITY_CHECK,
    
    @SerializedName("age_verification")
    AGE_VERIFICATION,
    
    @SerializedName("professional_credential")
    PROFESSIONAL_CREDENTIAL,
    
    @SerializedName("custom")
    CUSTOM
}

enum class VerificationRequestStatus {
    @SerializedName("pending")
    PENDING,
    
    @SerializedName("accepted")
    ACCEPTED,
    
    @SerializedName("rejected")
    REJECTED,
    
    @SerializedName("expired")
    EXPIRED
} 