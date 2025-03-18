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

package eu.europa.ec.dashboardfeature.interactor

import android.bluetooth.BluetoothManager
import android.content.Context
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.businesslogic.extension.isExpired
import eu.europa.ec.commonfeature.model.DocumentUiIssuanceState
import eu.europa.ec.commonfeature.ui.document_details.model.DocumentJsonKeys
import eu.europa.ec.commonfeature.util.documentHasExpired
import eu.europa.ec.commonfeature.util.extractValueFromDocumentOrEmpty
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.extension.localizedIssuerMetadata
import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.corelogic.model.toDocumentCategory
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.dashboardfeature.model.DocumentUi
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemLeadingContentData
import eu.europa.ec.uilogic.component.ListItemMainContentData
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

interface HomeInteractor {
    fun isBleAvailable(): Boolean
    fun isBleCentralClientModeEnabled(): Boolean
    fun getUserNameViaMainPidDocument(): Flow<HomeInteractorGetUserNameViaMainPidDocumentPartialState>
    fun getCredentials(): Flow<HomeInteractorGetCredentialsPartialState>
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
                        
                        val documentHasExpired = documentHasExpired(document.validUntil)
                        val documentIssuanceState = if (documentHasExpired) {
                            DocumentUiIssuanceState.Expired
                        } else {
                            DocumentUiIssuanceState.Issued
                        }
                        
                        // Format date for display
                        val dateFormatter = SimpleDateFormat("dd/MM/yyyy", userLocale)
                        val validUntilText = document.validUntil?.let {
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
                            uiData = ListItemData(
                                itemId = document.id,
                                mainContentData = ListItemMainContentData.Text(text = document.name),
                                overlineText = issuerName,
                                supportingText = validUntilText,
                                leadingContentData = ListItemLeadingContentData.Icon(
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
}