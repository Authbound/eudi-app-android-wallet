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
import kotlinx.serialization.json.JsonObject

/**
 * Response containing list of actions from the backend.
 */
@Serializable
data class ActionsListResponse(
    @SerialName("data")
    val data: List<ActionDto>,

    @SerialName("has_more")
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

    @SerialName("title")
    val title: String,

    @SerialName("requester")
    val requester: RequesterDto,

    @SerialName("description")
    val description: String? = null,

    @SerialName("priority")
    val priority: String? = null,

    @SerialName("created_at")
    val createdAt: String, // ISO 8601 timestamp

    @SerialName("expires_at")
    val expiresAt: String? = null, // ISO 8601 timestamp

    @SerialName("payload")
    val payload: JsonObject
)

/**
 * Information about the entity requesting an action.
 */
@Serializable
data class RequesterDto(
    @SerialName("name")
    val name: String,

    @SerialName("logo_url")
    val logoUrl: String? = null
)

/**
 * Response after responding to an action.
 */
@Serializable
data class ActionRespondResponse(
    @SerialName("id")
    val id: String,

    @SerialName("status")
    val status: String,

    @SerialName("responded_at")
    val respondedAt: String? = null
)

/**
 * Response after completing a pairing session.
 */
@Serializable
data class PairingCompleteResponse(
    @SerialName("success")
    val success: Boolean,

    @SerialName("deviceId")
    val deviceId: String,

    @SerialName("previousDeviceReplaced")
    val previousDeviceReplaced: Boolean
)

/**
 * Response containing device linking status.
 */
@Serializable
data class DeviceStatusResponse(
    @SerialName("hasLinkedDevice")
    val hasLinkedDevice: Boolean,

    @SerialName("device")
    val device: LinkedDeviceInfoDto? = null
)

/**
 * Information about a linked device from the backend.
 */
@Serializable
data class LinkedDeviceInfoDto(
    @SerialName("id")
    val deviceId: String,

    @SerialName("name")
    val deviceName: String,

    @SerialName("model")
    val deviceModel: String? = null,

    @SerialName("linkedAt")
    val linkedAt: String, // ISO 8601 timestamp

    @SerialName("lastActiveAt")
    val lastActiveAt: String? = null
)
