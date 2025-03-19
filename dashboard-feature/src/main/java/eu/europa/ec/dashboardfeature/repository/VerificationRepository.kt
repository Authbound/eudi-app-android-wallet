/*
 * Copyright (c) 2023 European Commission
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

import eu.europa.ec.dashboardfeature.model.verification.ParameterType
import eu.europa.ec.dashboardfeature.model.verification.ValidationCriteria
import eu.europa.ec.dashboardfeature.model.verification.VerificationParameter
import eu.europa.ec.dashboardfeature.model.verification.VerificationSession
import eu.europa.ec.dashboardfeature.model.verification.VerificationTemplate
import eu.europa.ec.dashboardfeature.model.verification.VerificationTemplateType
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.koin.android.annotation.KoinViewModel
import java.util.Calendar
import java.util.UUID

interface VerificationRepository {
    suspend fun getVerificationTemplates(): List<VerificationTemplate>
    suspend fun createVerificationSession(session: VerificationSession): Result<VerificationSession>
    suspend fun saveVerificationSession(session: VerificationSession): VerificationSession
    suspend fun getVerificationSession(sessionId: String): Result<VerificationSession>
    suspend fun getVerificationSessions(): Flow<List<VerificationSession>>
    suspend fun generateVerificationSessionDeepLink(sessionId: String): Result<String>
    suspend fun deleteVerificationSession(sessionId: String): Result<Boolean>
}

class VerificationRepositoryImpl(
    private val resourceProvider: ResourceProvider
) : VerificationRepository {
    
    // In-memory storage for sessions, would be replaced with a proper database in production
    private val sessionsFlow = MutableStateFlow<List<VerificationSession>>(emptyList())
    
    // Predefined templates
    private val verificationTemplates by lazy {
        listOf(
            VerificationTemplate(
                type = VerificationTemplateType.AGE_VERIFICATION,
                title = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_template_age_title),
                description = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_template_age_description),
                parameters = listOf(
                    VerificationParameter(
                        type = ParameterType.AGE,
                        label = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_parameter_age),
                        description = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_parameter_age_description),
                        validationCriteria = ValidationCriteria.AgeValidation(minAge = 18)
                    )
                )
            ),
            VerificationTemplate(
                type = VerificationTemplateType.IDENTITY_VERIFICATION,
                title = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_template_identity_title),
                description = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_template_identity_description),
                parameters = listOf(
                    VerificationParameter(
                        type = ParameterType.NAME,
                        label = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_parameter_name),
                        description = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_parameter_name_description)
                    ),
                    VerificationParameter(
                        type = ParameterType.DATE_OF_BIRTH,
                        label = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_parameter_date_of_birth),
                        description = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_parameter_date_of_birth_description)
                    ),
                    VerificationParameter(
                        type = ParameterType.DOCUMENT_TYPE,
                        label = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_parameter_document_type),
                        description = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_parameter_document_type_description)
                    )
                )
            ),
            VerificationTemplate(
                type = VerificationTemplateType.NATIONALITY_VERIFICATION,
                title = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_template_nationality_title),
                description = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_template_nationality_description),
                parameters = listOf(
                    VerificationParameter(
                        type = ParameterType.NATIONALITY,
                        label = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_parameter_nationality),
                        description = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_parameter_nationality_description)
                    )
                )
            ),
            VerificationTemplate(
                type = VerificationTemplateType.CUSTOM,
                title = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_template_custom_title),
                description = resourceProvider.getString(eu.europa.ec.resourceslogic.R.string.verification_template_custom_description),
                parameters = emptyList()
            )
        )
    }
    
    override suspend fun getVerificationTemplates(): List<VerificationTemplate> {
        return verificationTemplates
    }
    
    override suspend fun createVerificationSession(session: VerificationSession): Result<VerificationSession> {
        return try {
            val newSession = session.copy(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                sessionCode = UUID.randomUUID().toString().take(8).uppercase()
            )
            
            sessionsFlow.update { currentSessions ->
                currentSessions + newSession
            }
            
            Result.success(newSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun saveVerificationSession(session: VerificationSession): VerificationSession {
        val newSession = session.copy(
            id = session.id.ifBlank { UUID.randomUUID().toString() },
            createdAt = System.currentTimeMillis(),
            sessionCode = session.sessionCode.ifBlank { UUID.randomUUID().toString().take(8).uppercase() }
        )
        
        sessionsFlow.update { currentSessions ->
            currentSessions + newSession
        }
        
        return newSession
    }
    
    override suspend fun getVerificationSession(sessionId: String): Result<VerificationSession> {
        return try {
            val session = sessionsFlow.value.find { it.id == sessionId }
                ?: return Result.failure(NoSuchElementException("Verification session not found"))
                
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getVerificationSessions(): Flow<List<VerificationSession>> {
        return sessionsFlow.asStateFlow().map { sessions ->
            // Filter out expired sessions
            sessions.filter { session ->
                session.expiresAt?.let { expiryTime ->
                    System.currentTimeMillis() < expiryTime
                } ?: true
            }
        }
    }
    
    override suspend fun generateVerificationSessionDeepLink(sessionId: String): Result<String> {
        return try {
            val session = sessionsFlow.value.find { it.id == sessionId }
                ?: return Result.failure(NoSuchElementException("Verification session not found"))
                
            val deepLink = "authbound://verification?sessionId=${session.id}&code=${session.sessionCode}"
            
            // Update session with deep link
            val updatedSession = session.copy(deepLink = deepLink)
            sessionsFlow.update { currentSessions ->
                currentSessions.map { s ->
                    if (s.id == sessionId) updatedSession else s
                }
            }
            
            Result.success(deepLink)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteVerificationSession(sessionId: String): Result<Boolean> {
        return try {
            val initialSize = sessionsFlow.value.size
            sessionsFlow.update { currentSessions ->
                currentSessions.filter { it.id != sessionId }
            }
            
            Result.success(initialSize != sessionsFlow.value.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 