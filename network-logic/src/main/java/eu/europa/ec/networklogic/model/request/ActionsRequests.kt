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

package eu.europa.ec.networklogic.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Request to respond to an action (accept or decline).
 */
@Serializable
data class ActionRespondRequest(
    @SerialName("response")
    val response: String, // "accept" or "decline"

    @SerialName("device_id")
    val deviceId: String,

    @SerialName("biometric_verified")
    val biometricVerified: Boolean,

    @SerialName("payload")
    val payload: JsonObject? = null
)

/**
 * OpenID4VP Presentation Submission as per spec.
 */
@Serializable
data class PresentationSubmissionDto(
    @SerialName("id")
    val id: String,

    @SerialName("definition_id")
    val definitionId: String,

    @SerialName("descriptor_map")
    val descriptorMap: List<DescriptorMapEntryDto>
)

/**
 * Entry in the descriptor map for presentation submission.
 */
@Serializable
data class DescriptorMapEntryDto(
    @SerialName("id")
    val id: String,

    @SerialName("format")
    val format: String,

    @SerialName("path")
    val path: String,

    @SerialName("path_nested")
    val pathNested: DescriptorMapEntryDto? = null
)

/**
 * Request to complete a pairing session after scanning the portal QR code.
 */
@Serializable
data class CompletePairingRequest(
    @SerialName("deviceName")
    val deviceName: String,

    @SerialName("deviceModel")
    val deviceModel: String? = null,

    @SerialName("fcmToken")
    val fcmToken: String,

    @SerialName("challengeResponse")
    val challengeResponse: String
)

@Serializable
data class UpdateDeviceTokenRequest(
    @SerialName("fcmToken")
    val fcmToken: String
)
