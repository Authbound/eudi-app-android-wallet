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

package eu.europa.ec.networklogic.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from creating a QuickID verification session.
 */
@Serializable
data class QuickIdSessionResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("client_token") val clientToken: String,
    @SerialName("expires_at") val expiresAt: String
)

/**
 * Response from creating an AWS Liveness session.
 */
@Serializable
data class LivenessSessionResponse(
    @SerialName("liveness_session_id") val livenessSessionId: String,
    @SerialName("region") val region: String
)

/**
 * Response from the verification endpoint after submitting NFC data and liveness results.
 */
@Serializable
data class VerificationResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("status") val status: String,
    @SerialName("document_data") val documentData: DocumentData? = null,
    @SerialName("biometrics") val biometrics: BiometricResult? = null,
    @SerialName("last_error") val lastError: VerificationError? = null
) {
    companion object {
        const val STATUS_VERIFIED = "VERIFIED"
        const val STATUS_REJECTED = "REJECTED"
    }
}

/**
 * Document data extracted from passport NFC chip.
 */
@Serializable
data class DocumentData(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    @SerialName("nationality") val nationality: String? = null,
    @SerialName("document_number") val documentNumber: String? = null,
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("gender") val gender: String? = null
)

/**
 * Biometric verification result.
 */
@Serializable
data class BiometricResult(
    @SerialName("face_match") val faceMatch: Boolean,
    @SerialName("confidence") val confidence: Float? = null,
    @SerialName("liveness_result") val livenessResult: String? = null
)

/**
 * Verification error details.
 */
@Serializable
data class VerificationError(
    @SerialName("code") val code: String,
    @SerialName("message") val message: String,
    @SerialName("recoverable") val recoverable: Boolean = true
)

/**
 * Response containing the credential offer URI for issuing the Authbound ID credential.
 */
@Serializable
data class AuthboundIdCredentialResponse(
    @SerialName("credential_offer_uri") val credentialOfferUri: String,
    @SerialName("expires_at") val expiresAt: String
)

/**
 * Response from MRZ OCR endpoint after scanning passport data page.
 */
@Serializable
data class MrzOcrResponse(
    @SerialName("document_number") val documentNumber: String,
    @SerialName("date_of_birth") val dateOfBirth: String,
    @SerialName("date_of_expiry") val dateOfExpiry: String,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("issuing_country") val issuingCountry: String? = null,
    @SerialName("passport_photo_base64") val passportPhotoBase64: String? = null
)

/**
 * Error response from MRZ OCR when document cannot be read.
 */
@Serializable
data class MrzOcrError(
    @SerialName("code") val code: String,
    @SerialName("message") val message: String
)
