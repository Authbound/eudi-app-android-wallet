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

package eu.europa.ec.dashboardfeature.ui.mydata.model

import eu.europa.ec.uilogic.component.IconDataUi

/**
 * Categories for organizing personal data.
 */
enum class DataCategory {
    IDENTITY,
    CONTACT,
    ADDRESS,
    DRIVING,
    EDUCATION
}

/**
 * Verification status of a data field.
 */
enum class VerificationStatus {
    /** Data is verified by a trusted issuer */
    VERIFIED,
    /** Data is self-declared by the user */
    SELF_DECLARED,
    /** Data verification is pending */
    PENDING
}

/**
 * A single personal data field.
 */
data class PersonalDataField(
    val id: String,
    val label: String,
    val value: String,
    val status: VerificationStatus,
    val sourceCredential: String?,
    val isEditable: Boolean = false,
    val isSensitive: Boolean = false
)

/**
 * A category of personal data fields.
 */
data class DataCategoryUi(
    val category: DataCategory,
    val title: String,
    val icon: IconDataUi,
    val fields: List<PersonalDataField>
)

/**
 * UI model for the My Data screen.
 */
data class MyDataUi(
    val categories: List<DataCategoryUi>,
    val lastUpdated: String?
)
