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
 * Response from creating an AuthboundPID Identity verification session.
 */
@Serializable
data class CreateAuthboundPidSessionResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("candour_session_id") val candourSessionId: String,
    @SerialName("candour_api_endpoint") val candourApiEndpoint: String,
    @SerialName("expires_at") val expiresAt: String
)

/**
 * Response from polling an AuthboundPID session status.
 *
 * Terminal statuses (stop polling): verified, failed, expired
 * Non-terminal statuses (keep polling): pending, processing
 */
@Serializable
data class AuthboundPidSessionStatus(
    @SerialName("session_id") val sessionId: String,
    val status: String,
    @SerialName("identity_verified") val identityVerified: Boolean? = null,
    @SerialName("credential_offer_uri") val credentialOfferUri: String? = null,
    @SerialName("expires_at") val expiresAt: String
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_PROCESSING = "processing"
        const val STATUS_VERIFIED = "verified"
        const val STATUS_FAILED = "failed"
        const val STATUS_EXPIRED = "expired"

        val TERMINAL_STATUSES = setOf(STATUS_VERIFIED, STATUS_FAILED, STATUS_EXPIRED)
    }

    val isTerminal: Boolean get() = status in TERMINAL_STATUSES
}
