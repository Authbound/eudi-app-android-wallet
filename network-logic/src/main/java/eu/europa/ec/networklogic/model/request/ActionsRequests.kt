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

/**
 * Request to respond to an action (accept or decline).
 *
 * For VERIFY_REQUEST actions, the vpToken and presentationSubmission are required when accepting.
 * The wuaSignature provides device attestation for security.
 */
@Serializable
data class ActionRespondRequest(
    @SerialName("decision")
    val decision: String, // "accept" or "decline"

    @SerialName("vpToken")
    val vpToken: String? = null,

    @SerialName("presentationSubmission")
    val presentationSubmission: PresentationSubmissionDto? = null,

    @SerialName("declineReason")
    val declineReason: String? = null,

    @SerialName("wuaSignature")
    val wuaSignature: String? = null,

    @SerialName("signatureTimestamp")
    val signatureTimestamp: Long? = null
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
 * Request to link a device to the Authbound portal.
 */
@Serializable
data class DeviceLinkRequest(
    @SerialName("linkingCode")
    val linkingCode: String,

    @SerialName("deviceInfo")
    val deviceInfo: DeviceInfoDto,

    @SerialName("fcmToken")
    val fcmToken: String?,

    @SerialName("wuaPublicKey")
    val wuaPublicKey: String
)

/**
 * Basic device info for linking.
 */
@Serializable
data class DeviceInfoDto(
    @SerialName("deviceName")
    val deviceName: String,

    @SerialName("deviceModel")
    val deviceModel: String,

    @SerialName("osVersion")
    val osVersion: String,

    @SerialName("appVersion")
    val appVersion: String
)
