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

package eu.europa.ec.proximityfeature.interactor

import android.util.Base64
import androidx.activity.ComponentActivity
import eu.europa.ec.businesslogic.extension.encodeToBase64String
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.config.toDomainConfig
import eu.europa.ec.commonfeature.interactor.ScopedPresentationInteractor
import eu.europa.ec.commonfeature.interactor.ScopedPresentationInteractorDelegate
import eu.europa.ec.commonfeature.util.DocumentJsonKeys
import eu.europa.ec.commonfeature.util.extractValueFromDocumentOrEmpty
import eu.europa.ec.corelogic.controller.TransferEventPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onSubscription

sealed class ProximityQRPartialState {
    data class QrReady(val qrCode: String) : ProximityQRPartialState()
    data class Error(val error: String) : ProximityQRPartialState()
    data object Connected : ProximityQRPartialState()
    data object Disconnected : ProximityQRPartialState()
}

/** Summary of the document about to be presented, shown alongside the engagement QR. */
data class ProximityPresentingDocumentUi(
    val holderName: String?,
    val documentName: String,
    val documentCode: String,
    val countryCode: String?,
    val birthDate: String?,
    val sex: String?,
    val validUntil: String?,
    val portraitBase64: String?,
)

interface ProximityQRInteractor : ScopedPresentationInteractor {
    fun startQrEngagement(): Flow<ProximityQRPartialState>
    fun toggleNfcEngagement(
        componentActivity: ComponentActivity,
        toggle: Boolean
    )

    fun cancelTransfer()
    fun setConfig(config: RequestUriConfig)
    suspend fun getPresentingDocument(): ProximityPresentingDocumentUi?
}

class ProximityQRInteractorImpl(
    private val resourceProvider: ResourceProvider,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    walletCorePresentationController: WalletCorePresentationController? = null
) : ProximityQRInteractor,
    ScopedPresentationInteractorDelegate(walletCorePresentationController) {

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    private var presentingDocumentId: String? = null

    override fun setConfig(config: RequestUriConfig) {
        presentingDocumentId = config.presentingDocumentId
        setScopeId(config.presentationScopeId)
        walletCorePresentationController.setConfig(config.toDomainConfig())
    }

    override fun startQrEngagement(): Flow<ProximityQRPartialState> = flow {
        walletCorePresentationController.events
            .onSubscription {
                walletCorePresentationController.startQrEngagement()
            }.mapNotNull {
                when (it) {
                    is TransferEventPartialState.Connected -> {
                        ProximityQRPartialState.Connected
                    }

                    is TransferEventPartialState.Error -> {
                        ProximityQRPartialState.Error(error = it.error)
                    }

                    is TransferEventPartialState.QrEngagementReady -> {
                        ProximityQRPartialState.QrReady(qrCode = it.qrCode)
                    }

                    is TransferEventPartialState.Disconnected -> {
                        ProximityQRPartialState.Disconnected
                    }

                    else -> null
                }
            }.collect {
                emit(it)
            }
    }.safeAsync {
        ProximityQRPartialState.Error(error = it.localizedMessage ?: genericErrorMsg)
    }

    override fun toggleNfcEngagement(
        componentActivity: ComponentActivity,
        toggle: Boolean
    ) {
        walletCorePresentationController.toggleNfcEngagement(componentActivity, toggle)
    }

    override fun cancelTransfer() {
        walletCorePresentationController.stopPresentation()
    }

    override suspend fun getPresentingDocument(): ProximityPresentingDocumentUi? {
        return runCatching {
            val document = getConfiguredPresentingDocument()
                ?: return null
            val firstName = extractValueFromDocumentOrEmpty(
                document = document,
                key = DocumentJsonKeys.FIRST_NAME
            )
            val lastName = extractValueFromDocumentOrEmpty(
                document = document,
                key = DocumentJsonKeys.LAST_NAME
            )
            val holderName = listOf(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .takeIf { it.isNotBlank() }
            val portraitClaimValue: Any? = document.data.claims
                .firstOrNull { it.identifier == DocumentJsonKeys.PORTRAIT }
                ?.value
            val portraitBase64: String? = when (portraitClaimValue) {
                is ByteArray -> portraitClaimValue.encodeToBase64String(Base64.URL_SAFE)
                is String -> portraitClaimValue
                else -> null
            }
            val validUntil = document.getValidUntil().getOrNull()?.let { instant ->
                SimpleDateFormat("dd/MM/yyyy", resourceProvider.getLocale())
                    .format(Date(instant.toEpochMilli()))
            }
            val countryCode = getCountryCode(document)
            val birthDate = getBirthDate(document)
            val sex = getSex(document)
            ProximityPresentingDocumentUi(
                holderName = holderName,
                documentName = document.name,
                documentCode = getDocumentCode(document),
                countryCode = countryCode,
                birthDate = birthDate,
                sex = sex,
                validUntil = validUntil,
                portraitBase64 = portraitBase64
            )
        }.getOrNull()
    }

    private suspend fun getConfiguredPresentingDocument(): IssuedDocument? {
        val selectedDocumentId = presentingDocumentId?.takeIf { it.isNotBlank() }
        if (selectedDocumentId != null) {
            return walletCoreDocumentsController
                .getAllIssuedDocuments()
                .firstOrNull { document -> document.id == selectedDocumentId }
        }
        // Main PID lookup only matches the standard EU PID doc types; fall back to the
        // first issued document so custom-issued IDs (e.g. Authbound PID) are covered too.
        return walletCoreDocumentsController.getMainPidDocument()
            ?: walletCoreDocumentsController.getAllIssuedDocuments().firstOrNull()
    }

    private fun getCountryCode(document: IssuedDocument): String? {
        return listOf(
            DocumentJsonKeys.NATIONALITY,
            DocumentJsonKeys.NATIONALITIES,
            DocumentJsonKeys.ISSUING_COUNTRY
        ).firstNotNullOfOrNull { key ->
            normalizeCountryCode(extractValueFromDocumentOrEmpty(document, key))
        }
    }

    private fun getBirthDate(document: IssuedDocument): String? {
        val rawBirthDate = extractValueFromDocumentOrEmpty(
            document = document,
            key = DocumentJsonKeys.BIRTH_DATE
        ).takeIf { it.isNotBlank() } ?: return null
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val outputFormat = SimpleDateFormat("dd/MM/yyyy", resourceProvider.getLocale())
        return runCatching {
            val parsedBirthDate = inputFormat.parse(rawBirthDate) ?: return@runCatching rawBirthDate
            outputFormat.format(parsedBirthDate)
        }.getOrDefault(rawBirthDate)
    }

    private fun getSex(document: IssuedDocument): String? {
        val rawSex = DocumentJsonKeys.GENDER_KEYS.firstNotNullOfOrNull { key ->
            extractValueFromDocumentOrEmpty(document, key).takeIf { it.isNotBlank() }
        } ?: return null
        return normalizeSex(rawSex)
    }

    private fun getDocumentCode(document: IssuedDocument): String {
        return when (val documentIdentifier = document.toDocumentIdentifier()) {
            DocumentIdentifier.MdocPid,
            DocumentIdentifier.SdJwtPid -> "PID"

            is DocumentIdentifier.OTHER -> getOtherDocumentCode(
                formatType = documentIdentifier.formatType,
                documentName = document.name
            )
        }
    }

    private fun getOtherDocumentCode(
        formatType: String,
        documentName: String
    ): String {
        val source = "$formatType $documentName".lowercase(Locale.ROOT)
        return when {
            source.contains("mdl") || source.contains("driving") -> "MDL"
            source.contains("photo") -> "PHOTO ID"
            source.contains("age") -> "AGE"
            else -> "ID"
        }
    }

    private fun normalizeCountryCode(raw: String): String? {
        val countryCode = raw
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .firstOrNull()
            ?.trim()
            ?.trim('"')
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        if (countryCode.length == 3) return countryCode
        if (countryCode.length != 2) return countryCode
        return runCatching {
            Locale.Builder()
                .setRegion(countryCode)
                .build()
                .isO3Country
                .uppercase(Locale.ROOT)
        }.getOrDefault(countryCode)
    }

    private fun normalizeSex(raw: String): String? {
        return when (raw.trim().lowercase(Locale.ROOT)) {
            "1", "m", "male" -> "M"
            "2", "f", "female" -> "F"
            "0", "9", "not known", "not applicable", "unknown", "unset" -> null
            else -> raw.trim().uppercase(Locale.ROOT).takeIf { it.isNotBlank() }
        }
    }
}
