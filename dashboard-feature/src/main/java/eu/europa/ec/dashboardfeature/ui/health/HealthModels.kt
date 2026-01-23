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

package eu.europa.ec.dashboardfeature.ui.health

import java.time.Instant

/**
 * Status of medication or prescription.
 */
enum class MedicationStatus {
    ACTIVE,
    EXPIRING_SOON,
    EXPIRED,
    DISCONTINUED
}

/**
 * Health service providers for importing health data.
 */
enum class HealthService {
    KANTA,
    MAISA
}

/**
 * Connection status for health service providers.
 */
enum class ConnectionStatus {
    NOT_CONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * UI model for displaying medication items.
 */
data class MedicationUi(
    val id: String,
    val name: String,
    val dosage: String,
    val status: MedicationStatus,
    val validUntil: String?,
    val prescribedBy: String?,
    val pharmacy: String?
)

/**
 * UI model for displaying prescription items.
 */
data class PrescriptionUi(
    val id: String,
    val name: String,
    val prescribedBy: String,
    val prescribedDate: String,
    val status: MedicationStatus,
    val instructions: String?,
    val renewalDate: String?
)

/**
 * UI model for displaying health record items.
 */
data class HealthRecordUi(
    val id: String,
    val title: String,
    val category: String,
    val date: String,
    val provider: String,
    val summary: String?
)

/**
 * UI model for displaying vaccination items.
 */
data class VaccinationUi(
    val id: String,
    val name: String,
    val date: String,
    val provider: String,
    val nextDoseDate: String?,
    val lotNumber: String?,
    val isBooster: Boolean = false
)

/**
 * Category for grouping health data in the UI.
 */
sealed class HealthCategoryUi(val displayName: String) {
    data object Medications : HealthCategoryUi("Active Medications")
    data object Prescriptions : HealthCategoryUi("Prescriptions")
    data object HealthRecords : HealthCategoryUi("Health Records")
    data object Vaccinations : HealthCategoryUi("Vaccinations")
}

/**
 * Bottom sheet content types for the health screen.
 */
sealed class HealthSheetContent {
    data object None : HealthSheetContent()
    data class MedicationDetail(val medication: MedicationUi) : HealthSheetContent()
    data class ShareOptions(val credentialId: String) : HealthSheetContent()
    data class ServiceInfo(val service: HealthService) : HealthSheetContent()
}
