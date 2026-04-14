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

@Serializable
data class VerificationRequestedAttributeDto(
    @SerialName("type")
    val type: String,

    @SerialName("expectedValue")
    val expectedValue: String? = null
)

@Serializable
data class VerificationRecipientDto(
    @SerialName("contactType")
    val contactType: String,

    @SerialName("value")
    val value: String,

    @SerialName("status")
    val status: String? = null,

    @SerialName("resolvedUserId")
    val resolvedUserId: String? = null,

    @SerialName("invitedAt")
    val invitedAt: String? = null,

    @SerialName("openedAt")
    val openedAt: String? = null,

    @SerialName("verifiedAt")
    val verifiedAt: String? = null,

    @SerialName("failedAt")
    val failedAt: String? = null,

    @SerialName("createdAt")
    val createdAt: String? = null,

    @SerialName("updatedAt")
    val updatedAt: String? = null
)

@Serializable
data class VerificationSessionDto(
    @SerialName("id")
    val id: String,

    @SerialName("status")
    val status: String,

    @SerialName("purpose")
    val purpose: String,

    @SerialName("requestedAttributes")
    val requestedAttributes: Map<String, VerificationRequestedAttributeDto> = emptyMap(),

    @SerialName("createdAt")
    val createdAt: String,

    @SerialName("updatedAt")
    val updatedAt: String,

    @SerialName("expiresAt")
    val expiresAt: String? = null,

    @SerialName("completedAt")
    val completedAt: String? = null,

    @SerialName("failedAt")
    val failedAt: String? = null,

    @SerialName("creatorId")
    val creatorId: String? = null
)

@Serializable
data class VerificationPublicSessionDto(
    @SerialName("id")
    val id: String,

    @SerialName("status")
    val status: String,

    @SerialName("purpose")
    val purpose: String,

    @SerialName("requestedAttributes")
    val requestedAttributes: Map<String, VerificationRequestedAttributeDto> = emptyMap(),

    @SerialName("createdAt")
    val createdAt: String,

    @SerialName("expiresAt")
    val expiresAt: String? = null
)

@Serializable
data class VerificationRequesterDto(
    @SerialName("id")
    val id: String? = null,

    @SerialName("displayName")
    val displayName: String? = null,

    @SerialName("handle")
    val handle: String? = null
)

@Serializable
data class VerificationSessionStatusEventDto(
    @SerialName("status")
    val status: String,

    @SerialName("updatedAt")
    val updatedAt: String? = null
)

@Serializable
data class VerificationClientActionDto(
    @SerialName("kind")
    val kind: String,

    @SerialName("data")
    val data: String,

    @SerialName("expiresAt")
    val expiresAt: String
)

@Serializable
data class VerificationAccessMethodDto(
    @SerialName("id")
    val id: String,

    @SerialName("type")
    val type: String,

    @SerialName("token")
    val token: String,

    @SerialName("createdAt")
    val createdAt: String? = null
)

@Serializable
data class VerificationRequestDto(
    @SerialName("id")
    val id: String,

    @SerialName("verificationType")
    val verificationType: String,

    @SerialName("expectedValue")
    val expectedValue: String,

    @SerialName("status")
    val status: String
)

@Serializable
data class VerificationNotificationResultDto(
    @SerialName("recipient")
    val recipient: String,

    @SerialName("channel")
    val channel: String,

    @SerialName("success")
    val success: Boolean,

    @SerialName("error")
    val error: String? = null
)

@Serializable
data class VerificationNotificationSummaryDto(
    @SerialName("sent")
    val sent: Int,

    @SerialName("failed")
    val failed: Int,

    @SerialName("results")
    val results: List<VerificationNotificationResultDto> = emptyList()
)

@Serializable
data class CreateVerificationSessionResponse(
    @SerialName("sessionId")
    val sessionId: String,

    @SerialName("status")
    val status: String,

    @SerialName("createdAt")
    val createdAt: String? = null,

    @SerialName("updatedAt")
    val updatedAt: String? = null,

    @SerialName("expiresAt")
    val expiresAt: String,

    @SerialName("accessToken")
    val accessToken: String,

    @SerialName("publicUrl")
    val publicUrl: String? = null,

    @SerialName("recipients")
    val recipients: List<VerificationRecipientDto> = emptyList(),

    @SerialName("gatewaySessionId")
    val gatewaySessionId: String? = null,

    @SerialName("clientAction")
    val clientAction: VerificationClientActionDto? = null,

    @SerialName("sseToken")
    val sseToken: String? = null,

    @SerialName("statusStreamUrl")
    val statusStreamUrl: String? = null,

    @SerialName("creditsDeducted")
    val creditsDeducted: Int? = null,

    @SerialName("creditsRemaining")
    val creditsRemaining: Int? = null,

    @SerialName("notifications")
    val notifications: VerificationNotificationSummaryDto? = null
)

@Serializable
data class VerificationSessionsListResponse(
    @SerialName("sessions")
    val sessions: List<VerificationSessionListItemDto> = emptyList()
)

@Serializable
data class VerificationSessionListItemDto(
    @SerialName("id")
    val id: String,

    @SerialName("status")
    val status: String,

    @SerialName("purpose")
    val purpose: String,

    @SerialName("requestedAttributes")
    val requestedAttributes: Map<String, VerificationRequestedAttributeDto> = emptyMap(),

    @SerialName("expectedValues")
    val expectedValues: Map<String, String?> = emptyMap(),

    @SerialName("recipients")
    val recipients: List<VerificationRecipientDto> = emptyList(),

    @SerialName("createdAt")
    val createdAt: String,

    @SerialName("updatedAt")
    val updatedAt: String,

    @SerialName("expiresAt")
    val expiresAt: String? = null,

    @SerialName("completedAt")
    val completedAt: String? = null,

    @SerialName("failedAt")
    val failedAt: String? = null
)

@Serializable
data class VerificationSessionDetailResponse(
    @SerialName("session")
    val session: VerificationSessionDto,

    @SerialName("accessMethods")
    val accessMethods: List<VerificationAccessMethodDto> = emptyList(),

    @SerialName("accessToken")
    val accessToken: String? = null,

    @SerialName("publicUrl")
    val publicUrl: String? = null,

    @SerialName("verificationRequests")
    val verificationRequests: List<VerificationRequestDto> = emptyList(),

    @SerialName("recipients")
    val recipients: List<VerificationRecipientDto> = emptyList(),

    @SerialName("expectedValues")
    val expectedValues: Map<String, String?> = emptyMap(),

    @SerialName("gatewaySessionId")
    val gatewaySessionId: String? = null,

    @SerialName("clientAction")
    val clientAction: VerificationClientActionDto? = null,

    @SerialName("sseToken")
    val sseToken: String? = null,

    @SerialName("statusStreamUrl")
    val statusStreamUrl: String? = null
)

@Serializable
data class VerificationPublicSessionResponse(
    @SerialName("session")
    val session: VerificationPublicSessionDto,

    @SerialName("publicUrl")
    val publicUrl: String? = null,

    @SerialName("requester")
    val requester: VerificationRequesterDto? = null,

    @SerialName("expectedValues")
    val expectedValues: Map<String, String?> = emptyMap(),

    @SerialName("gatewaySessionId")
    val gatewaySessionId: String? = null,

    @SerialName("clientAction")
    val clientAction: VerificationClientActionDto? = null,

    @SerialName("sseToken")
    val sseToken: String? = null,

    @SerialName("statusStreamUrl")
    val statusStreamUrl: String? = null
)
