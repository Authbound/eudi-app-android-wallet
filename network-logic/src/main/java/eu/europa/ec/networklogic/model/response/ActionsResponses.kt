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
 * Response containing list of actions from the backend.
 */
@Serializable
data class ActionsListResponse(
    @SerialName("actions")
    val actions: List<ActionDto>,

    @SerialName("pagination")
    val pagination: PaginationDto? = null
)

/**
 * Pagination metadata.
 */
@Serializable
data class PaginationDto(
    @SerialName("total")
    val total: Int,

    @SerialName("hasMore")
    val hasMore: Boolean
)

/**
 * DTO representing an action request from the backend.
 */
@Serializable
data class ActionDto(
    @SerialName("id")
    val id: String,

    @SerialName("type")
    val type: String, // VERIFY_REQUEST, SIGN_REQUEST, DATA_REQUEST

    @SerialName("status")
    val status: String, // PENDING, ACCEPTED, DECLINED, EXPIRED

    @SerialName("requester")
    val requester: RequesterDto,

    @SerialName("description")
    val description: String? = null,

    @SerialName("openId4VpRequestUri")
    val openId4VpRequestUri: String? = null,

    @SerialName("requestedCredentials")
    val requestedCredentials: List<String>? = null,

    @SerialName("requestedClaims")
    val requestedClaims: List<String>? = null,

    @SerialName("createdAt")
    val createdAt: String, // ISO 8601 timestamp

    @SerialName("expiresAt")
    val expiresAt: String? = null // ISO 8601 timestamp
)

/**
 * Information about the entity requesting an action.
 */
@Serializable
data class RequesterDto(
    @SerialName("name")
    val name: String,

    @SerialName("logoUrl")
    val logoUrl: String? = null,

    @SerialName("verifierDid")
    val verifierDid: String? = null
)

/**
 * Response after responding to an action.
 */
@Serializable
data class ActionRespondResponse(
    @SerialName("success")
    val success: Boolean,

    @SerialName("message")
    val message: String? = null,

    @SerialName("actionId")
    val actionId: String
)

/**
 * Response after linking a device.
 */
@Serializable
data class DeviceLinkResponse(
    @SerialName("success")
    val success: Boolean,

    @SerialName("deviceId")
    val deviceId: String,

    @SerialName("linkedAt")
    val linkedAt: String // ISO 8601 timestamp
)

/**
 * Response containing device linking status.
 */
@Serializable
data class DeviceStatusResponse(
    @SerialName("isLinked")
    val isLinked: Boolean,

    @SerialName("deviceInfo")
    val deviceInfo: LinkedDeviceInfoDto? = null
)

/**
 * Information about a linked device from the backend.
 */
@Serializable
data class LinkedDeviceInfoDto(
    @SerialName("deviceId")
    val deviceId: String,

    @SerialName("deviceName")
    val deviceName: String,

    @SerialName("deviceModel")
    val deviceModel: String,

    @SerialName("linkedAt")
    val linkedAt: String // ISO 8601 timestamp
)
