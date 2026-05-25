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

@Serializable
data class VerificationRequestedAttributeRequestDto(
    @SerialName("type")
    val type: String,

    @SerialName("expectedValue")
    val expectedValue: String? = null
)

@Serializable
data class VerificationRecipientRequestDto(
    @SerialName("contactType")
    val contactType: String,

    @SerialName("value")
    val value: String
)

@Serializable
data class CreateVerificationSessionRequest(
    @SerialName("requestedAttributes")
    val requestedAttributes: Map<String, VerificationRequestedAttributeRequestDto>,

    @SerialName("recipients")
    val recipients: List<VerificationRecipientRequestDto> = emptyList(),

    @SerialName("purpose")
    val purpose: String
)
