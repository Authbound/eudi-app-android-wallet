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

package eu.europa.ec.dashboardfeature.model.verification

enum class VerificationTemplateType {
    AGE_VERIFICATION,
    IDENTITY_VERIFICATION,
    NATIONALITY_VERIFICATION,
    CUSTOM
}

enum class VerificationRecipientContactType(val apiValue: String) {
    HANDLE("handle"),
    EMAIL("email"),
    PHONE("phone"),
    LINK("link");

    companion object {
        fun fromApiValue(value: String): VerificationRecipientContactType = entries.firstOrNull {
            it.apiValue.equals(value, ignoreCase = true)
        } ?: HANDLE
    }
}

data class VerificationAttributeDefinition(
    val key: String,
    val label: String,
    val description: String,
    val requiresExpectedValue: Boolean = false
)

data class VerificationTemplate(
    val type: VerificationTemplateType,
    val title: String,
    val description: String,
    val purpose: String,
    val attributes: List<String>
)

data class VerificationDraftAttribute(
    val key: String,
    val label: String,
    val description: String,
    val requiresExpectedValue: Boolean,
    val selected: Boolean,
    val expectedValue: String = ""
)

data class VerificationRecipient(
    val contactType: VerificationRecipientContactType,
    val value: String,
    val status: String? = null,
    val resolvedUserId: String? = null,
    val invitedAt: Long? = null,
    val openedAt: Long? = null,
    val verifiedAt: Long? = null,
    val failedAt: Long? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

data class VerificationRequester(
    val id: String? = null,
    val displayName: String? = null,
    val handle: String? = null
)

data class VerificationRequestedAttribute(
    val key: String,
    val label: String,
    val description: String,
    val expectedValue: String? = null
)

data class VerificationSession(
    val id: String,
    val status: String,
    val purpose: String,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long?,
    val completedAt: Long? = null,
    val failedAt: Long? = null,
    val requestedAttributes: List<VerificationDraftAttribute>,
    val recipients: List<VerificationRecipient> = emptyList(),
    val accessToken: String? = null,
    val publicUrl: String? = null,
    val qrPayload: String? = null,
    val qrPayloadExpiresAt: Long? = null,
    val creditsDeducted: Int? = null,
    val creditsRemaining: Int? = null
)

data class VerificationRecipientSession(
    val id: String,
    val status: String,
    val verificationId: String? = null,
    val purpose: String,
    val createdAt: Long,
    val expiresAt: Long?,
    val requester: VerificationRequester?,
    val requestedAttributes: List<VerificationRequestedAttribute>,
    val publicUrl: String,
    val requestUri: String? = null,
    val requestUriExpiresAt: Long? = null
)

private val ExpectedValueAttributes = setOf("full_name", "personal_id", "date_of_birth")

fun requiresExpectedValue(attributeKey: String): Boolean = attributeKey in ExpectedValueAttributes

fun VerificationRecipient.lastStatusChangedAt(): Long? = when (status?.lowercase()) {
    "verified" -> verifiedAt ?: updatedAt
    "failed", "rejected", "invalid", "canceled" -> failedAt ?: updatedAt
    "expired" -> updatedAt
    "opened" -> openedAt ?: updatedAt
    null, "", "pending", "invited" -> invitedAt
    else -> listOfNotNull(verifiedAt, failedAt, openedAt, invitedAt, updatedAt).maxOrNull()
}

fun VerificationRecipient.lastSessionActivityAt(): Long? = when (status?.lowercase()) {
    "verified" -> verifiedAt ?: updatedAt
    "failed", "rejected", "invalid", "canceled" -> failedAt ?: updatedAt
    "expired" -> updatedAt
    "opened" -> openedAt ?: updatedAt
    else -> null
}

fun VerificationSession.lastUpdatedAt(): Long {
    val recipientUpdatedAt = recipients.maxOfOrNull { it.lastSessionActivityAt() ?: Long.MIN_VALUE }
        ?.takeIf { it != Long.MIN_VALUE }

    return listOfNotNull(
        updatedAt,
        completedAt,
        failedAt,
        recipientUpdatedAt,
        createdAt,
    ).maxOrNull() ?: createdAt
}

fun isVerificationHistoryStatus(status: String): Boolean = status in setOf(
    "verified",
    "failed",
    "rejected",
    "expired",
    "invalid",
    "canceled",
)

fun humanizeVerificationStatus(status: String): String = when (status.lowercase()) {
    "pending" -> "Pending"
    "active" -> "Active"
    "verified" -> "Verified"
    "failed" -> "Failed"
    "rejected" -> "Rejected"
    "expired" -> "Expired"
    "invalid" -> "Invalid"
    "canceled" -> "Canceled"
    else -> status.replaceFirstChar { it.uppercase() }
}
