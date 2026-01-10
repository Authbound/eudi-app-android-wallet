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

import android.bluetooth.BluetoothManager
import android.content.Context
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.commonfeature.util.DocumentJsonKeys

import eu.europa.ec.commonfeature.util.documentHasExpired
import eu.europa.ec.commonfeature.util.extractValueFromDocumentOrEmpty
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.extension.localizedIssuerMetadata
import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.toDocumentCategory
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.dashboardfeature.ui.documents.detail.model.DocumentIssuanceStateUi

import eu.europa.ec.dashboardfeature.ui.documents.list.model.DocumentUi
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.dashboardfeature.ui.home.model.HeroCredentialUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class HomeInteractorGetUserNameViaMainPidDocumentPartialState {
    data class Success(
        val userFirstName: String,
    ) : HomeInteractorGetUserNameViaMainPidDocumentPartialState()

    data class Failure(
        val error: String
    ) : HomeInteractorGetUserNameViaMainPidDocumentPartialState()
}

sealed class HomeInteractorGetCredentialsPartialState {
    data class Success(
        val credentials: List<Pair<DocumentCategory, List<DocumentUi>>>
    ) : HomeInteractorGetCredentialsPartialState()

    data class Failure(
        val error: String
    ) : HomeInteractorGetCredentialsPartialState()
}

sealed class HomeInteractorGetHeroCredentialPartialState {
    data class Success(
        val heroCredential: HeroCredentialUi?
    ) : HomeInteractorGetHeroCredentialPartialState()

    data class Failure(
        val error: String
    ) : HomeInteractorGetHeroCredentialPartialState()
}

interface HomeInteractor {
    fun isBleAvailable(): Boolean
    fun isBleCentralClientModeEnabled(): Boolean
    fun getUserNameViaMainPidDocument(): Flow<HomeInteractorGetUserNameViaMainPidDocumentPartialState>
    fun getCredentials(): Flow<HomeInteractorGetCredentialsPartialState>
    fun getHeroCredential(): Flow<HomeInteractorGetHeroCredentialPartialState>
}

class HomeInteractorImpl(
    private val resourceProvider: ResourceProvider,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val walletCoreConfig: WalletCoreConfig
) : HomeInteractor {
    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun isBleAvailable(): Boolean {
        val bluetoothManager: BluetoothManager? = resourceProvider.provideContext()
            .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter?.isEnabled == true
    }

    override fun isBleCentralClientModeEnabled(): Boolean =
        walletCoreConfig.config.enableBleCentralMode

    override fun getUserNameViaMainPidDocument(): Flow<HomeInteractorGetUserNameViaMainPidDocumentPartialState> =
        flow {
            val mainPid = walletCoreDocumentsController.getMainPidDocument()
            val userFirstName = mainPid?.let {
                return@let extractValueFromDocumentOrEmpty(
                    document = it,
                    key = DocumentJsonKeys.FIRST_NAME
                )
            }.orEmpty()

            emit(
                HomeInteractorGetUserNameViaMainPidDocumentPartialState.Success(
                    userFirstName = userFirstName
                )
            )
        }.safeAsync {
            HomeInteractorGetUserNameViaMainPidDocumentPartialState.Failure(
                error = it.localizedMessage ?: genericErrorMsg
            )
        }

    override fun getCredentials(): Flow<HomeInteractorGetCredentialsPartialState> =
        flow {
            try {
                val documentCategories = walletCoreDocumentsController.getAllDocumentCategories()
                val userLocale = resourceProvider.getLocale()

                // Get all documents and convert them to UI models
                val documents = walletCoreDocumentsController.getAllDocuments().mapNotNull { document ->
                    if (document is IssuedDocument) {
                        val localizedIssuerMetadata = document.localizedIssuerMetadata(userLocale)
                        val issuerName = localizedIssuerMetadata?.name

                        val documentIdentifier = document.toDocumentIdentifier()
                        val documentCategory = documentIdentifier.toDocumentCategory(documentCategories)

                        val validUntil = document.getValidUntil().getOrNull()
                        val documentHasExpired = validUntil?.let { documentHasExpired(it) } ?: false
                        val documentIssuanceState = if (documentHasExpired) {
                            DocumentIssuanceStateUi.Expired
                        } else {
                            DocumentIssuanceStateUi.Issued
                        }

                        // Format date for display
                        val dateFormatter = SimpleDateFormat("dd/MM/yyyy", userLocale)
                        val validUntilText = validUntil?.let {
                            if (documentHasExpired) {
                                resourceProvider.getString(R.string.dashboard_document_has_expired)
                            } else {
                                resourceProvider.getString(
                                    R.string.dashboard_document_has_not_expired,
                                    dateFormatter.format(Date(it.toEpochMilli()))
                                )
                            }
                        } ?: ""

                        DocumentUi(
                            documentIssuanceState = documentIssuanceState,
                            uiData = ListItemDataUi(
                                itemId = document.id,
                                mainContentData = ListItemMainContentDataUi.Text(text = document.name),
                                overlineText = issuerName,
                                supportingText = validUntilText,
                                leadingContentData = ListItemLeadingContentDataUi.Icon(
                                    iconData = AppIcons.Documents
                                )
                            ),
                            documentIdentifier = documentIdentifier,
                            documentCategory = documentCategory
                        )
                    } else {
                        null
                    }
                }

                // Group by category and sort
                val groupedByCategory = documents.groupBy { it.documentCategory }
                    .map { (category, docs) -> category to docs }
                    .sortedWith { a, b ->
                        a.first.toString().compareTo(b.first.toString())
                    }

                // Limit to 3 documents per category for home screen
                val limitedDocuments = groupedByCategory.map { (category, docs) ->
                    category to docs.take(3)
                }

                emit(HomeInteractorGetCredentialsPartialState.Success(limitedDocuments))
            } catch (e: Exception) {
                emit(HomeInteractorGetCredentialsPartialState.Failure(e.localizedMessage ?: genericErrorMsg))
            }
        }

    override fun getHeroCredential(): Flow<HomeInteractorGetHeroCredentialPartialState> =
        flow {
            try {
                // Try to get the main PID document first (highest priority)
                val mainPid = walletCoreDocumentsController.getMainPidDocument()

                val heroDocument = if (mainPid != null) {
                    mainPid
                } else {
                    // If no PID, try to get any issued document (mDL or other)
                    walletCoreDocumentsController.getAllIssuedDocuments().firstOrNull()
                }

                if (heroDocument != null) {
                    val userLocale = resourceProvider.getLocale()
                    val localizedIssuerMetadata = heroDocument.localizedIssuerMetadata(userLocale)
                    val issuerName = localizedIssuerMetadata?.name

                    val documentIdentifier = heroDocument.toDocumentIdentifier()
                    val heroValidUntil = heroDocument.getValidUntil().getOrNull()
                    val documentHasExpired = heroValidUntil?.let { documentHasExpired(it) } ?: false
                    val documentIssuanceState = if (documentHasExpired) {
                        DocumentIssuanceStateUi.Expired
                    } else {
                        DocumentIssuanceStateUi.Issued
                    }

                    // Format expiry date
                    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", userLocale)
                    val expiryDate = heroValidUntil?.let {
                        dateFormatter.format(Date(it.toEpochMilli()))
                    }

                    // Extract holder name from document claims
                    val firstName = extractValueFromDocumentOrEmpty(
                        document = heroDocument,
                        key = DocumentJsonKeys.FIRST_NAME
                    )
                    val lastName = extractValueFromDocumentOrEmpty(
                        document = heroDocument,
                        key = DocumentJsonKeys.LAST_NAME
                    )
                    val holderName = listOf(firstName, lastName)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .takeIf { it.isNotBlank() }

                    // Check if document has portrait/photo
                    val hasPhoto = extractValueFromDocumentOrEmpty(
                        document = heroDocument,
                        key = DocumentJsonKeys.PORTRAIT
                    ).isNotBlank()

                    val heroCredential = HeroCredentialUi(
                        documentId = heroDocument.id,
                        documentIdentifier = documentIdentifier,
                        title = heroDocument.name,
                        subtitle = documentIdentifier.getSubtitle(),
                        holderName = holderName,
                        issuerName = issuerName,
                        expiryDate = expiryDate,
                        status = documentIssuanceState,
                        hasPhoto = hasPhoto
                    )

                    emit(HomeInteractorGetHeroCredentialPartialState.Success(heroCredential))
                } else {
                    // No documents available
                    emit(HomeInteractorGetHeroCredentialPartialState.Success(null))
                }
            } catch (e: Exception) {
                emit(
                    HomeInteractorGetHeroCredentialPartialState.Failure(
                        e.localizedMessage ?: genericErrorMsg
                    )
                )
            }
        }

    private fun DocumentIdentifier.getSubtitle(): String {
        return when (this) {
            DocumentIdentifier.MdocPid, DocumentIdentifier.SdJwtPid ->
                resourceProvider.getString(R.string.home_hero_pid_subtitle)
            is DocumentIdentifier.OTHER -> {
                when {
                    formatType.contains("mDL", ignoreCase = true) ||
                    formatType.contains("driving", ignoreCase = true) ->
                        resourceProvider.getString(R.string.home_hero_mdl_subtitle)
                    else -> ""
                }
            }
        }
    }
}