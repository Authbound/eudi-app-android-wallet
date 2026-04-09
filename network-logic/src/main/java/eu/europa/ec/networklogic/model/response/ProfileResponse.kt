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

package eu.europa.ec.networklogic.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileResponse(
    @SerialName("id")
    val id: String,
    @SerialName("handle")
    val handle: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("legal_acceptance")
    val legalAcceptance: LegalAcceptanceResponse? = null,
    @SerialName("account_deletion")
    val accountDeletion: AccountDeletionResponse? = null
)

@Serializable
data class LegalAcceptanceResponse(
    @SerialName("required_terms_version")
    val requiredTermsVersion: String? = null,
    @SerialName("accepted_terms_version")
    val acceptedTermsVersion: String? = null,
    @SerialName("accepted_terms_at")
    val acceptedTermsAt: String? = null,
    @SerialName("required_privacy_version")
    val requiredPrivacyVersion: String? = null,
    @SerialName("acknowledged_privacy_version")
    val acknowledgedPrivacyVersion: String? = null,
    @SerialName("acknowledged_privacy_at")
    val acknowledgedPrivacyAt: String? = null,
)

@Serializable
data class AccountDeletionResponse(
    @SerialName("status")
    val status: String = "none",
    @SerialName("requested_at")
    val requestedAt: String? = null,
    @SerialName("scheduled_for")
    val scheduledFor: String? = null,
    @SerialName("can_cancel")
    val canCancel: Boolean = false,
)

@Serializable
data class LegalAcceptanceEnvelopeResponse(
    @SerialName("legal_acceptance")
    val legalAcceptance: LegalAcceptanceResponse
)

@Serializable
data class AccountDeletionEnvelopeResponse(
    @SerialName("account_deletion")
    val accountDeletion: AccountDeletionResponse
)
