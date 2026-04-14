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

package eu.europa.ec.dashboardfeature.repository

import eu.europa.ec.dashboardfeature.model.verification.VerificationDraftAttribute
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipientSession
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipient
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipientContactType
import eu.europa.ec.dashboardfeature.model.verification.VerificationRequestedAttribute
import eu.europa.ec.dashboardfeature.model.verification.VerificationRequester
import eu.europa.ec.dashboardfeature.model.verification.VerificationSession
import eu.europa.ec.dashboardfeature.model.verification.VerificationTemplate
import eu.europa.ec.dashboardfeature.model.verification.VerificationTemplateType
import eu.europa.ec.dashboardfeature.model.verification.requiresExpectedValue
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.request.CreateVerificationSessionRequest
import eu.europa.ec.networklogic.model.request.VerificationRecipientRequestDto
import eu.europa.ec.networklogic.model.request.VerificationRequestedAttributeRequestDto
import eu.europa.ec.networklogic.model.response.CreateVerificationSessionResponse
import eu.europa.ec.networklogic.model.response.VerificationPublicSessionResponse
import eu.europa.ec.networklogic.model.response.VerificationRecipientDto
import eu.europa.ec.networklogic.model.response.VerificationSessionDetailResponse
import eu.europa.ec.networklogic.model.response.VerificationSessionListItemDto
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

interface VerificationRepository {
    suspend fun getVerificationTemplates(): List<VerificationTemplate>
    suspend fun createVerificationSession(
        purpose: String,
        attributes: List<VerificationDraftAttribute>,
        recipients: List<VerificationRecipient>
    ): Result<VerificationSession>
    fun observePublicVerificationSessionStatus(statusStreamUrl: String): Flow<String>
    suspend fun getPublicVerificationSession(
        sessionId: String,
        accessToken: String
    ): Result<VerificationRecipientSession>
    suspend fun getVerificationSession(sessionId: String): Result<VerificationSession>
    suspend fun getVerificationSessions(): Flow<List<VerificationSession>>
    suspend fun refreshVerificationSessions(): Result<Unit>
}

class VerificationRepositoryImpl(
    private val apiClient: ApiClient,
    private val supabaseClient: SupabaseClient,
    private val resourceProvider: ResourceProvider
) : VerificationRepository {

    private val sessionsFlow = MutableStateFlow<List<VerificationSession>>(emptyList())

    private data class AttributeCatalogItem(
        val key: String,
        val label: String,
        val description: String
    )

    private fun attributeCatalog(): List<AttributeCatalogItem> = listOf(
        AttributeCatalogItem(
            key = "age_over_18",
            label = resourceProvider.getString(R.string.verification_parameter_age),
            description = resourceProvider.getString(R.string.verification_parameter_age_description)
        ),
        AttributeCatalogItem(
            key = "full_name",
            label = resourceProvider.getString(R.string.verification_parameter_name),
            description = resourceProvider.getString(R.string.verification_parameter_name_description)
        ),
        AttributeCatalogItem(
            key = "date_of_birth",
            label = resourceProvider.getString(R.string.verification_parameter_date_of_birth),
            description = resourceProvider.getString(R.string.verification_parameter_date_of_birth_description)
        ),
        AttributeCatalogItem(
            key = "nationality",
            label = resourceProvider.getString(R.string.verification_parameter_nationality),
            description = resourceProvider.getString(R.string.verification_parameter_nationality_description)
        ),
        AttributeCatalogItem(
            key = "personal_id",
            label = resourceProvider.getString(R.string.verification_parameter_document_number),
            description = resourceProvider.getString(R.string.verification_parameter_document_number_description)
        ),
        AttributeCatalogItem(
            key = "address",
            label = "Address",
            description = "Residential address from the holder's credential"
        ),
        AttributeCatalogItem(
            key = "phone",
            label = "Phone",
            description = "Phone number from the holder's credential"
        )
    )

    private fun catalogByKey(): Map<String, AttributeCatalogItem> = attributeCatalog().associateBy { it.key }

    override suspend fun getVerificationTemplates(): List<VerificationTemplate> {
        return listOf(
            VerificationTemplate(
                type = VerificationTemplateType.AGE_VERIFICATION,
                title = resourceProvider.getString(R.string.verification_template_age_title),
                description = resourceProvider.getString(R.string.verification_template_age_description),
                purpose = resourceProvider.getString(R.string.verification_template_age_description),
                attributes = listOf("age_over_18")
            ),
            VerificationTemplate(
                type = VerificationTemplateType.IDENTITY_VERIFICATION,
                title = resourceProvider.getString(R.string.verification_template_identity_title),
                description = resourceProvider.getString(R.string.verification_template_identity_description),
                purpose = resourceProvider.getString(R.string.verification_template_identity_description),
                attributes = listOf("full_name", "date_of_birth", "personal_id")
            ),
            VerificationTemplate(
                type = VerificationTemplateType.NATIONALITY_VERIFICATION,
                title = resourceProvider.getString(R.string.verification_template_nationality_title),
                description = resourceProvider.getString(R.string.verification_template_nationality_description),
                purpose = resourceProvider.getString(R.string.verification_template_nationality_description),
                attributes = listOf("nationality")
            ),
            VerificationTemplate(
                type = VerificationTemplateType.CUSTOM,
                title = resourceProvider.getString(R.string.verification_template_custom_title),
                description = resourceProvider.getString(R.string.verification_template_custom_description),
                purpose = "Peer-to-peer attribute verification",
                attributes = emptyList()
            )
        )
    }

    override suspend fun createVerificationSession(
        purpose: String,
        attributes: List<VerificationDraftAttribute>,
        recipients: List<VerificationRecipient>
    ): Result<VerificationSession> {
        return try {
            val token = getAuthToken() ?: return Result.failure(Exception("Not authenticated"))
            val creatorId = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

            val selectedAttributes = attributes.filter { it.selected }
            if (selectedAttributes.isEmpty()) {
                return Result.failure(IllegalArgumentException("Select at least one attribute"))
            }

            val missingExpectedValue = selectedAttributes.firstOrNull {
                it.requiresExpectedValue && it.expectedValue.trim().isBlank()
            }
            if (missingExpectedValue != null) {
                return Result.failure(
                    IllegalArgumentException("${missingExpectedValue.label} requires an expected value")
                )
            }

            val requestedAttributes = selectedAttributes.associate { attribute ->
                attribute.key to VerificationRequestedAttributeRequestDto(
                    type = attribute.key,
                    expectedValue = attribute.expectedValue.trim().ifBlank { null }
                )
            }
            val expectedValues = selectedAttributes.mapNotNull { attribute ->
                attribute.expectedValue.trim().takeIf { it.isNotBlank() }?.let { attribute.key to it }
            }.toMap()

            val response = apiClient.createVerificationSession(
                body = CreateVerificationSessionRequest(
                    creatorId = creatorId,
                    requestedAttributes = requestedAttributes,
                    expectedValues = expectedValues,
                    recipients = recipients.map {
                        VerificationRecipientRequestDto(
                            contactType = it.contactType.apiValue,
                            value = it.value.trim()
                        )
                    },
                    purpose = purpose.ifBlank { "Peer-to-peer attribute verification" }
                ),
                bearerToken = token
            )

            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

            val body = response.body() ?: return Result.failure(Exception("Empty response"))
            val session = getVerificationSession(body.sessionId).getOrElse {
                mapCreatedSession(body, purpose, selectedAttributes)
            }
            refreshVerificationSessions()
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observePublicVerificationSessionStatus(statusStreamUrl: String): Flow<String> {
        return apiClient.observeVerificationSessionStatus(statusStreamUrl).map { it.status }
    }

    override suspend fun getPublicVerificationSession(
        sessionId: String,
        accessToken: String
    ): Result<VerificationRecipientSession> {
        return try {
            val response = apiClient.getPublicVerificationSession(
                sessionId = sessionId,
                accessToken = accessToken
            )

            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

            val body = response.body() ?: return Result.failure(Exception("Empty response"))
            Result.success(mapPublicSession(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getVerificationSession(sessionId: String): Result<VerificationSession> {
        return try {
            val token = getAuthToken() ?: return Result.failure(Exception("Not authenticated"))
            val response = apiClient.getVerificationSession(
                sessionId = sessionId,
                bearerToken = token
            )

            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

            val body = response.body() ?: return Result.failure(Exception("Empty response"))
            val session = mapSessionDetail(body)
            storeSession(session)
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getVerificationSessions(): Flow<List<VerificationSession>> = sessionsFlow.asStateFlow()

    override suspend fun refreshVerificationSessions(): Result<Unit> {
        return try {
            val token = getAuthToken() ?: return Result.failure(Exception("Not authenticated"))
            val userId = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

            val response = apiClient.getVerificationSessions(
                userId = userId,
                bearerToken = token
            )

            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

            val body = response.body() ?: return Result.failure(Exception("Empty response"))
            sessionsFlow.value = body.sessions
                .map { mapSessionListItem(it) }
                .sortedByDescending { it.createdAt }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getAuthToken(): String? = supabaseClient.auth.currentSessionOrNull()?.accessToken

    private suspend fun getCurrentUserId(): String? = supabaseClient.auth.currentSessionOrNull()?.user?.id

    private fun mapCreatedSession(
        response: CreateVerificationSessionResponse,
        purpose: String,
        selectedAttributes: List<VerificationDraftAttribute>
    ): VerificationSession {
        return VerificationSession(
            id = response.sessionId,
            status = response.status,
            purpose = purpose,
            createdAt = response.createdAt?.let(::parseIsoToMillis) ?: System.currentTimeMillis(),
            updatedAt = response.updatedAt?.let(::parseIsoToMillis) ?: System.currentTimeMillis(),
            expiresAt = parseIsoToMillis(response.expiresAt),
            requestedAttributes = selectedAttributes,
            recipients = response.recipients.map(::mapRecipient),
            accessToken = response.accessToken,
            publicUrl = response.publicUrl,
            gatewaySessionId = response.gatewaySessionId,
            qrPayload = response.clientAction?.data,
            qrPayloadExpiresAt = response.clientAction?.expiresAt?.let(::parseIsoToMillis),
            sseToken = response.sseToken,
            creditsDeducted = response.creditsDeducted,
            creditsRemaining = response.creditsRemaining
        )
    }

    private fun mapSessionDetail(response: VerificationSessionDetailResponse): VerificationSession {
        val expectedValues = response.expectedValues
        val requestedAttributes = response.session.requestedAttributes.map { entry ->
            val definition = catalogByKey()[entry.key]
            VerificationDraftAttribute(
                key = entry.key,
                label = definition?.label ?: humanizeAttributeKey(entry.key),
                description = definition?.description ?: "",
                requiresExpectedValue = requiresExpectedValue(entry.key),
                selected = true,
                expectedValue = expectedValues[entry.key] ?: entry.value.expectedValue.orEmpty()
            )
        }

        return VerificationSession(
            id = response.session.id,
            status = response.session.status,
            purpose = response.session.purpose,
            createdAt = parseIsoToMillis(response.session.createdAt),
            updatedAt = parseIsoToMillis(response.session.updatedAt),
            expiresAt = response.session.expiresAt?.let(::parseIsoToMillis),
            completedAt = response.session.completedAt?.let(::parseIsoToMillis),
            failedAt = response.session.failedAt?.let(::parseIsoToMillis),
            requestedAttributes = requestedAttributes,
            recipients = response.recipients.map(::mapRecipient),
            accessToken = response.accessToken,
            publicUrl = response.publicUrl,
            gatewaySessionId = response.gatewaySessionId,
            qrPayload = response.clientAction?.data,
            qrPayloadExpiresAt = response.clientAction?.expiresAt?.let(::parseIsoToMillis),
            sseToken = response.sseToken
        )
    }

    private fun mapPublicSession(response: VerificationPublicSessionResponse): VerificationRecipientSession {
        val requestedAttributes = response.session.requestedAttributes.map { entry ->
            val definition = catalogByKey()[entry.key]
            VerificationRequestedAttribute(
                key = entry.key,
                label = definition?.label ?: humanizeAttributeKey(entry.key),
                description = definition?.description ?: "",
                expectedValue = response.expectedValues[entry.key] ?: entry.value.expectedValue
            )
        }

        return VerificationRecipientSession(
            id = response.session.id,
            status = response.session.status,
            purpose = response.session.purpose,
            createdAt = parseIsoToMillis(response.session.createdAt),
            expiresAt = response.session.expiresAt?.let(::parseIsoToMillis),
            requester = response.requester?.let {
                VerificationRequester(
                    id = it.id,
                    displayName = it.displayName,
                    handle = it.handle
                )
            },
            requestedAttributes = requestedAttributes,
            publicUrl = response.publicUrl ?: "",
            gatewaySessionId = response.gatewaySessionId,
            requestUri = response.clientAction?.data,
            requestUriExpiresAt = response.clientAction?.expiresAt?.let(::parseIsoToMillis),
            sseToken = response.sseToken,
            statusStreamUrl = response.statusStreamUrl
        )
    }

    private fun mapSessionListItem(dto: VerificationSessionListItemDto): VerificationSession {
        val requestedAttributes = dto.requestedAttributes.map { entry ->
            val definition = catalogByKey()[entry.key]
            VerificationDraftAttribute(
                key = entry.key,
                label = definition?.label ?: humanizeAttributeKey(entry.key),
                description = definition?.description ?: "",
                requiresExpectedValue = requiresExpectedValue(entry.key),
                selected = true,
                expectedValue = dto.expectedValues[entry.key] ?: entry.value.expectedValue.orEmpty()
            )
        }

        return VerificationSession(
            id = dto.id,
            status = dto.status,
            purpose = dto.purpose,
            createdAt = parseIsoToMillis(dto.createdAt),
            updatedAt = parseIsoToMillis(dto.updatedAt),
            expiresAt = dto.expiresAt?.let(::parseIsoToMillis),
            completedAt = dto.completedAt?.let(::parseIsoToMillis),
            failedAt = dto.failedAt?.let(::parseIsoToMillis),
            requestedAttributes = requestedAttributes,
            recipients = dto.recipients.map(::mapRecipient),
        )
    }

    private fun mapRecipient(dto: VerificationRecipientDto): VerificationRecipient {
        return VerificationRecipient(
            contactType = VerificationRecipientContactType.fromApiValue(dto.contactType),
            value = dto.value,
            status = dto.status,
            resolvedUserId = dto.resolvedUserId,
            invitedAt = dto.invitedAt?.let(::parseIsoToMillis),
            openedAt = dto.openedAt?.let(::parseIsoToMillis),
            verifiedAt = dto.verifiedAt?.let(::parseIsoToMillis),
            failedAt = dto.failedAt?.let(::parseIsoToMillis),
            createdAt = dto.createdAt?.let(::parseIsoToMillis),
            updatedAt = dto.updatedAt?.let(::parseIsoToMillis),
        )
    }

    private fun storeSession(session: VerificationSession) {
        sessionsFlow.value = (sessionsFlow.value.filterNot { it.id == session.id } + session)
            .sortedByDescending { it.createdAt }
    }

    private fun parseIsoToMillis(value: String): Long = runCatching {
        Instant.parse(value).toEpochMilli()
    }.getOrElse {
        System.currentTimeMillis()
    }

    private fun humanizeAttributeKey(key: String): String {
        return key
            .split("_")
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
    }
}
