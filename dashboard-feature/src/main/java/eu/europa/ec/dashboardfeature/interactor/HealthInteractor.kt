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

package eu.europa.ec.dashboardfeature.interactor

import eu.europa.ec.dashboardfeature.ui.health.ConnectionStatus
import eu.europa.ec.dashboardfeature.ui.health.HealthRecordUi
import eu.europa.ec.dashboardfeature.ui.health.HealthService
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.businesslogic.util.formatInstant
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.extension.localizedIssuerMetadata
import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.corelogic.model.toDocumentCategory
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.dashboardfeature.repository.MaisaRepository
import eu.europa.ec.dashboardfeature.ui.health.MedicationStatus
import eu.europa.ec.dashboardfeature.ui.health.MedicationUi
import eu.europa.ec.dashboardfeature.ui.health.PrescriptionUi
import eu.europa.ec.dashboardfeature.ui.health.VaccinationUi
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant

/**
 * Partial state for health data loading.
 */
sealed class HealthInteractorPartialState {
    data class Success(
        val medications: List<MedicationUi>,
        val prescriptions: List<PrescriptionUi>,
        val healthRecords: List<HealthRecordUi>,
        val vaccinations: List<VaccinationUi>
    ) : HealthInteractorPartialState()

    data class Failure(val error: String) : HealthInteractorPartialState()
}

/**
 * Partial state for health service connection status.
 */
sealed class HealthConnectionPartialState {
    data class Success(
        val kantaStatus: ConnectionStatus,
        val maisaStatus: ConnectionStatus,
        val lastSyncTime: String?
    ) : HealthConnectionPartialState()

    data class Failure(val error: String) : HealthConnectionPartialState()
}

/**
 * Interactor interface for health-related business logic.
 */
interface HealthInteractor {
    /**
     * Gets all health data from connected services.
     */
    fun getHealthData(): Flow<HealthInteractorPartialState>

    /**
     * Gets the connection status for health services.
     */
    fun getConnectionStatus(): Flow<HealthConnectionPartialState>

    /**
     * Initiates connection to a health service.
     */
    suspend fun connectToService(service: HealthService): Result<Unit>

    /**
     * Starts Maisa authorization and returns the auth URL.
     */
    suspend fun startMaisaAuth(): Result<String>

    /**
     * Completes Maisa callback flow and returns credential offer URI.
     */
    suspend fun handleMaisaCallback(code: String, state: String, redirectUri: String): Result<String>

    /**
     * Disconnects from a health service.
     */
    suspend fun disconnectFromService(service: HealthService): Result<Unit>

    /**
     * Refreshes health data from connected services.
     */
    suspend fun refreshHealthData(): Result<Unit>

    /**
     * Checks if any health data is available.
     */
    fun hasHealthData(): Boolean
}

/**
 * Implementation of HealthInteractor with mock data for development.
 * Real API integration will be added when issuer services are available.
 */
class HealthInteractorImpl(
    private val resourceProvider: ResourceProvider,
    private val prefsController: PrefsControllerV2,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val maisaRepository: MaisaRepository
) : HealthInteractor {

    companion object {
        private const val MAISA_CONNECTED_KEY = "maisa_connected"
        private const val MAISA_LAST_SYNC_KEY = "maisa_last_sync"
    }

    private var kantaConnected = false
    private var lastSyncTime: String? = null



    private val mockPrescriptions = listOf(
        PrescriptionUi(
            id = "presc_1",
            name = "Metformin 500mg",
            prescribedBy = "Dr. Virtanen",
            prescribedDate = "10.01.2026",
            status = MedicationStatus.ACTIVE,
            instructions = "Take with meals",
            renewalDate = "10.07.2026"
        ),
        PrescriptionUi(
            id = "presc_2",
            name = "Lisinopril 10mg",
            prescribedBy = "Dr. Korhonen",
            prescribedDate = "15.12.2025",
            status = MedicationStatus.ACTIVE,
            instructions = "Take in the morning",
            renewalDate = "15.06.2026"
        )
    )

    private val mockHealthRecords = listOf(
        HealthRecordUi(
            id = "record_1",
            title = "Annual Health Checkup",
            category = "General",
            date = "05.01.2026",
            provider = "Helsinki University Hospital",
            summary = "Regular annual examination, all results normal"
        ),
        HealthRecordUi(
            id = "record_2",
            title = "Blood Test Results",
            category = "Laboratory",
            date = "05.01.2026",
            provider = "HUSLAB",
            summary = "Complete blood count, cholesterol panel"
        ),
        HealthRecordUi(
            id = "record_3",
            title = "Dental Examination",
            category = "Dental",
            date = "20.12.2025",
            provider = "Oral Hammaslääkärit",
            summary = "Routine checkup, cleaning performed"
        ),
        HealthRecordUi(
            id = "record_4",
            title = "Eye Examination",
            category = "Ophthalmology",
            date = "15.11.2025",
            provider = "Silmäasema",
            summary = "Vision test, prescription updated"
        ),
        HealthRecordUi(
            id = "record_5",
            title = "Physical Therapy Session",
            category = "Rehabilitation",
            date = "08.01.2026",
            provider = "Fysios Helsinki",
            summary = "Back pain treatment, exercises prescribed"
        )
    )

    private val mockVaccinations = listOf(
        VaccinationUi(
            id = "vacc_1",
            name = "COVID-19 Vaccine (Comirnaty)",
            date = "15.09.2025",
            provider = "Helsinki Vaccination Center",
            nextDoseDate = "15.09.2026",
            lotNumber = "FN1234",
            isBooster = true
        ),
        VaccinationUi(
            id = "vacc_2",
            name = "Influenza Vaccine",
            date = "01.10.2025",
            provider = "Terveystalo",
            nextDoseDate = "01.10.2026",
            lotNumber = "FL5678",
            isBooster = false
        ),
        VaccinationUi(
            id = "vacc_3",
            name = "Tetanus-Diphtheria (Td)",
            date = "20.03.2023",
            provider = "Helsinki Health Center",
            nextDoseDate = "20.03.2033",
            lotNumber = "TD9012",
            isBooster = true
        ),
        VaccinationUi(
            id = "vacc_4",
            name = "Hepatitis B Vaccine",
            date = "15.06.2022",
            provider = "Mehiläinen",
            nextDoseDate = null,
            lotNumber = "HB3456",
            isBooster = false
        )
    )

    override fun getHealthData(): Flow<HealthInteractorPartialState> = flow {
        try {
            delay(300)
            val isMaisaConnected = prefsController.safeBool(MAISA_CONNECTED_KEY, false)
            val documents = walletCoreDocumentsController.getAllIssuedDocuments()
            val documentCategories = walletCoreDocumentsController.getAllDocumentCategories()
            val userLocale = resourceProvider.getLocale()

            val healthDocuments = documents.filter { document ->
                val category = document.toDocumentIdentifier().toDocumentCategory(documentCategories)
                category == DocumentCategory.Health
            }

            if (!isMaisaConnected || healthDocuments.isEmpty()) {
                emit(
                    HealthInteractorPartialState.Success(
                        medications = emptyList(),
                        prescriptions = emptyList(),
                        healthRecords = emptyList(),
                        vaccinations = emptyList()
                    )
                )
                return@flow
            }

            val medications = healthDocuments.map { document ->
                val issuerName = document.localizedIssuerMetadata(userLocale)?.name
                val validUntil = document.getValidUntil().getOrNull()
                val status = resolveStatus(validUntil)
                MedicationUi(
                    id = document.id,
                    name = document.name,
                    dosage = issuerName?.let { "Issued by $it" } ?: "Health credential",
                    status = status,
                    validUntil = validUntil?.formatInstant(),
                    prescribedBy = issuerName,
                    pharmacy = null
                )
            }

            emit(
                HealthInteractorPartialState.Success(
                    medications = medications,
                    prescriptions = emptyList(),
                    healthRecords = emptyList(),
                    vaccinations = emptyList()
                )
            )
        } catch (e: Exception) {
            emit(HealthInteractorPartialState.Failure(e.localizedMessage ?: "Unknown error"))
        }
    }

    override fun getConnectionStatus(): Flow<HealthConnectionPartialState> = flow {
        try {
            val isMaisaConnected = prefsController.safeBool(MAISA_CONNECTED_KEY, false)
            val savedLastSync = prefsController.safeString(MAISA_LAST_SYNC_KEY, lastSyncTime ?: "")
            emit(
                HealthConnectionPartialState.Success(
                    kantaStatus = if (kantaConnected) ConnectionStatus.CONNECTED else ConnectionStatus.NOT_CONNECTED,
                    maisaStatus = if (isMaisaConnected) ConnectionStatus.CONNECTED else ConnectionStatus.NOT_CONNECTED,
                    lastSyncTime = savedLastSync.ifEmpty { lastSyncTime }
                )
            )
        } catch (e: Exception) {
            emit(HealthConnectionPartialState.Failure(e.localizedMessage ?: "Unknown error"))
        }
    }

    override suspend fun connectToService(service: HealthService): Result<Unit> {
        return try {
            delay(1000)
            when (service) {
                HealthService.KANTA -> kantaConnected = true
                HealthService.MAISA -> return Result.failure(IllegalStateException("Use Maisa flow"))
            }
            lastSyncTime = "Just now"
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun startMaisaAuth(): Result<String> {
        return maisaRepository.startAuth().map { response -> response.authorizationUrl }
    }

    override suspend fun handleMaisaCallback(
        code: String,
        state: String,
        redirectUri: String
    ): Result<String> {
        return maisaRepository.exchangeAndIssue(code, state, redirectUri)
            .map { response -> response.credentialOfferUri }
    }

    override suspend fun disconnectFromService(service: HealthService): Result<Unit> {
        return try {
            when (service) {
                HealthService.KANTA -> kantaConnected = false
                HealthService.MAISA -> {
                    prefsController.setBool(MAISA_CONNECTED_KEY, false)
                    prefsController.setString(MAISA_LAST_SYNC_KEY, "")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshHealthData(): Result<Unit> {
        return try {
            delay(1000)
            lastSyncTime = "Just now"
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun hasHealthData(): Boolean {
        val isMaisaConnected = prefsController.safeBool(MAISA_CONNECTED_KEY, false)
        if (!isMaisaConnected) {
            return kantaConnected
        }
        val documentCategories = walletCoreDocumentsController.getAllDocumentCategories()
        val hasHealthDocument = walletCoreDocumentsController.getAllIssuedDocuments().any { document ->
            document.toDocumentIdentifier().toDocumentCategory(documentCategories) == DocumentCategory.Health
        }
        return kantaConnected || hasHealthDocument
    }

    private fun resolveStatus(validUntil: Instant?): MedicationStatus {
        if (validUntil == null) {
            return MedicationStatus.ACTIVE
        }
        if (validUntil.isBefore(Instant.now())) {
            return MedicationStatus.EXPIRED
        }
        val daysRemaining = Duration.between(Instant.now(), validUntil).toDays()
        return if (daysRemaining <= 30) {
            MedicationStatus.EXPIRING_SOON
        } else {
            MedicationStatus.ACTIVE
        }
    }
}
