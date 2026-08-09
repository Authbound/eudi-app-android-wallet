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

import androidx.activity.ComponentActivity
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.config.toDomainConfig
import eu.europa.ec.commonfeature.interactor.ScopedPresentationInteractor
import eu.europa.ec.commonfeature.interactor.ScopedPresentationInteractorDelegate
import eu.europa.ec.commonfeature.util.DocumentJsonKeys
import eu.europa.ec.commonfeature.util.extractIdentityCardData
import eu.europa.ec.commonfeature.util.extractValueFromDocumentOrEmpty
import eu.europa.ec.corelogic.controller.TransferEventPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.eudi.wallet.document.IssuedDocument
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
    val isIdentityDocument: Boolean = true,
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
            val identityCardData = extractIdentityCardData(
                document = document,
                resourceProvider = resourceProvider
            )
            val sex = getSex(document)
            val documentCode = getDocumentCode(document)
            ProximityPresentingDocumentUi(
                holderName = identityCardData.holderName,
                documentName = document.name,
                documentCode = documentCode,
                countryCode = identityCardData.nationality,
                birthDate = identityCardData.birthDate,
                sex = sex,
                validUntil = identityCardData.expiryDate,
                portraitBase64 = identityCardData.portraitBase64,
                // Same rule as the credential cards: a portrait window belongs to any
                // credential that carries a portrait or is an identity document type;
                // attribute-only credentials (e.g. age attestations) get neither.
                isIdentityDocument = identityCardData.portraitBase64 != null ||
                        documentCode != DOCUMENT_CODE_AGE
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

    private fun getSex(document: IssuedDocument): String? {
        val rawSex = DocumentJsonKeys.GENDER_KEYS.firstNotNullOfOrNull { key ->
            extractValueFromDocumentOrEmpty(document, key).takeIf { it.isNotBlank() }
        } ?: return null
        return normalizeSex(rawSex)
    }

    private fun getDocumentCode(document: IssuedDocument): String {
        return when (val documentIdentifier = document.toDocumentIdentifier()) {
            DocumentIdentifier.MdocPid,
            DocumentIdentifier.SdJwtPid -> DOCUMENT_CODE_PID

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
        // Match on whole words so e.g. "language" or "agency" in a document name can
        // never classify an identity document as an age attestation.
        val sourceWords = "$formatType $documentName"
            .lowercase(Locale.ROOT)
            .split(WORD_SEPARATOR_REGEX)
            .toSet()
        return when {
            "mdl" in sourceWords || "driving" in sourceWords -> DOCUMENT_CODE_MDL
            "photo" in sourceWords -> DOCUMENT_CODE_PHOTO_ID
            "age" in sourceWords -> DOCUMENT_CODE_AGE
            else -> DOCUMENT_CODE_GENERIC
        }
    }

    private fun normalizeSex(raw: String): String? {
        return when (raw.trim().lowercase(Locale.ROOT)) {
            "1", "m", "male" -> "M"
            "2", "f", "female" -> "F"
            "0", "9", "not known", "not applicable", "unknown", "unset" -> null
            else -> raw.trim().uppercase(Locale.ROOT).takeIf { it.isNotBlank() }
        }
    }

    private companion object {
        const val DOCUMENT_CODE_PID = "PID"
        const val DOCUMENT_CODE_MDL = "MDL"
        const val DOCUMENT_CODE_PHOTO_ID = "PHOTO ID"
        const val DOCUMENT_CODE_AGE = "AGE"
        const val DOCUMENT_CODE_GENERIC = "ID"

        val WORD_SEPARATOR_REGEX = Regex("[^a-z0-9]+")
    }
}
