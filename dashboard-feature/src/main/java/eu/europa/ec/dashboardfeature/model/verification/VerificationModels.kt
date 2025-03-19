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

package eu.europa.ec.dashboardfeature.model.verification

import java.util.UUID

/**
 * Represents a verification parameter with its type, label, and validation criteria
 */
data class VerificationParameter(
    val id: String = UUID.randomUUID().toString(),
    val type: ParameterType,
    val label: String,
    val description: String,
    val isRequired: Boolean = true,
    val validationCriteria: ValidationCriteria? = null
)

/**
 * Types of parameters that can be verified
 */
enum class ParameterType {
    AGE,
    NAME,
    DATE_OF_BIRTH,
    NATIONALITY,
    DOCUMENT_TYPE,
    DOCUMENT_NUMBER,
    EXPIRY_DATE,
    CUSTOM
}

/**
 * Validation criteria for parameters
 */
sealed class ValidationCriteria {
    data class AgeValidation(val minAge: Int) : ValidationCriteria()
    data class DateValidation(val minDate: Long, val maxDate: Long? = null) : ValidationCriteria()
    data class TextValidation(val regex: String? = null, val minLength: Int = 0, val maxLength: Int = Int.MAX_VALUE) : ValidationCriteria()
    data class ValueInList(val allowedValues: List<String>) : ValidationCriteria()
}

/**
 * Predefined verification templates for common use cases
 */
enum class VerificationTemplateType {
    AGE_VERIFICATION,
    IDENTITY_VERIFICATION,
    NATIONALITY_VERIFICATION,
    CUSTOM
}

/**
 * Template for verification session with predefined parameters
 */
data class VerificationTemplate(
    val type: VerificationTemplateType,
    val title: String,
    val description: String,
    val parameters: List<VerificationParameter>
)

/**
 * Complete verification session with all details
 */
data class VerificationSession(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val title: String,
    val description: String,
    val creatorId: String,
    val parameters: List<VerificationParameter>,
    val expiresAt: Long? = null,
    val sessionCode: String = UUID.randomUUID().toString().take(8).uppercase(),
    val deepLink: String? = null
) 