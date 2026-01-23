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
 * Response from the attestation challenge endpoint.
 *
 * The challenge is used for EUDI ARF-compliant WUA (Wallet Unit Attestation) key generation.
 * The challenge is embedded in the Android Keystore key attestation extension and verified
 * by the backend to prevent replay attacks.
 */
@Serializable
data class AttestationChallengeResponse(
    @SerialName("challenge_id")
    val challengeId: String,

    @SerialName("challenge")
    val challenge: String,  // Hex-encoded 32 bytes

    @SerialName("expires_at")
    val expiresAt: String,

    @SerialName("ttl_seconds")
    val ttlSeconds: Int
)
