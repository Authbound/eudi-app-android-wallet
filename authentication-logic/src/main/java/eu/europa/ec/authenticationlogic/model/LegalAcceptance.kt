/*
 * Copyright (c) 2026 European Commission
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
package eu.europa.ec.authenticationlogic.model

import com.google.gson.annotations.SerializedName

data class LegalAcceptance(
    @SerializedName("required_terms_version")
    val requiredTermsVersion: String? = null,
    @SerializedName("accepted_terms_version")
    val acceptedTermsVersion: String? = null,
    @SerializedName("accepted_terms_at")
    val acceptedTermsAt: String? = null,
    @SerializedName("required_privacy_version")
    val requiredPrivacyVersion: String? = null,
    @SerializedName("acknowledged_privacy_version")
    val acknowledgedPrivacyVersion: String? = null,
    @SerializedName("acknowledged_privacy_at")
    val acknowledgedPrivacyAt: String? = null,
)

data class LegalAcceptanceSnapshot(
    val requiredTermsVersion: String = "",
    val acceptedTermsVersion: String? = null,
    val acceptedTermsAt: String? = null,
    val requiredPrivacyVersion: String = "",
    val acknowledgedPrivacyVersion: String? = null,
    val acknowledgedPrivacyAt: String? = null,
) {
    val isRequirementConfigured: Boolean
        get() = requiredTermsVersion.isNotBlank() && requiredPrivacyVersion.isNotBlank()
    val isTermsAccepted: Boolean
        get() = requiredTermsVersion.isNotBlank() && acceptedTermsVersion == requiredTermsVersion
    val isPrivacyAcknowledged: Boolean
        get() = requiredPrivacyVersion.isNotBlank() && acknowledgedPrivacyVersion == requiredPrivacyVersion
    val isAccepted: Boolean
        get() = isRequirementConfigured && isTermsAccepted && isPrivacyAcknowledged
}
