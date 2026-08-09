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
import androidx.activity.ComponentActivity
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.config.toDomainConfig
import eu.europa.ec.commonfeature.interactor.ScopedPresentationInteractorDelegate
import eu.europa.ec.commonfeature.util.CARD_DISPLAY_DATE_PATTERN
import eu.europa.ec.commonfeature.util.DocumentJsonKeys
import eu.europa.ec.commonfeature.util.extractIdentityCardData

import eu.europa.ec.commonfeature.util.documentHasExpired
import eu.europa.ec.commonfeature.util.extractValueFromDocumentOrEmpty
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.controller.TransferEventPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onSubscription
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
        val heroCredentials: List<HeroCredentialUi>
    ) : HomeInteractorGetHeroCredentialPartialState()

    data class Failure(
        val error: String
    ) : HomeInteractorGetHeroCredentialPartialState()
}

sealed class HomeInteractorPresentIdPartialState {
    data class QrReady(val qrCode: String) : HomeInteractorPresentIdPartialState()
    data class Error(val error: String) : HomeInteractorPresentIdPartialState()
    data object Connected : HomeInteractorPresentIdPartialState()
    data object Disconnected : HomeInteractorPresentIdPartialState()
}

interface HomeInteractor {
    fun isBleAvailable(): Boolean
    fun isBleCentralClientModeEnabled(): Boolean
    fun getUserNameViaMainPidDocument(): Flow<HomeInteractorGetUserNameViaMainPidDocumentPartialState>
    fun getCredentials(): Flow<HomeInteractorGetCredentialsPartialState>
    fun getHeroCredential(): Flow<HomeInteractorGetHeroCredentialPartialState>
    fun setPresentIdConfig(config: RequestUriConfig)
    fun startPresentIdEngagement(): Flow<HomeInteractorPresentIdPartialState>
    fun togglePresentIdNfcEngagement(
        componentActivity: ComponentActivity,
        toggle: Boolean
    )
    fun cancelPresentIdPresentation()
    fun releasePresentIdPresentationController()
}

class HomeInteractorImpl(
    private val resourceProvider: ResourceProvider,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val walletCoreConfig: WalletCoreConfig,
    walletCorePresentationController: WalletCorePresentationController? = null
) : HomeInteractor,
    ScopedPresentationInteractorDelegate(walletCorePresentationController) {
    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun isBleAvailable(): Boolean {
        val bluetoothManager: BluetoothManager? = resourceProvider.provideContext()
            .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter?.isEnabled == true
    }

    override fun isBleCentralClientModeEnabled(): Boolean =
        walletCoreConfig.config.enableBleCentralMode

    override fun setPresentIdConfig(config: RequestUriConfig) {
        setScopeId(config.presentationScopeId)
        walletCorePresentationController.setConfig(config.toDomainConfig())
    }

    override fun startPresentIdEngagement(): Flow<HomeInteractorPresentIdPartialState> = flow {
        walletCorePresentationController.events
            .onSubscription {
                walletCorePresentationController.startQrEngagement()
            }.mapNotNull {
                when (it) {
                    is TransferEventPartialState.Connected -> {
                        HomeInteractorPresentIdPartialState.Connected
                    }

                    is TransferEventPartialState.Error -> {
                        HomeInteractorPresentIdPartialState.Error(error = it.error)
                    }

                    is TransferEventPartialState.QrEngagementReady -> {
                        HomeInteractorPresentIdPartialState.QrReady(qrCode = it.qrCode)
                    }

                    is TransferEventPartialState.Disconnected -> {
                        HomeInteractorPresentIdPartialState.Disconnected
                    }

                    else -> null
                }
            }.collect {
                emit(it)
            }
    }.safeAsync {
        HomeInteractorPresentIdPartialState.Error(error = it.localizedMessage ?: genericErrorMsg)
    }

    override fun togglePresentIdNfcEngagement(
        componentActivity: ComponentActivity,
        toggle: Boolean
    ) {
        walletCorePresentationController.toggleNfcEngagement(componentActivity, toggle)
    }

    override fun cancelPresentIdPresentation() {
        walletCorePresentationController.stopPresentation()
        closeScopedPresentationScope()
    }

    override fun releasePresentIdPresentationController() {
        releaseScopedPresentationController()
    }

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
                        val dateFormatter = SimpleDateFormat(CARD_DISPLAY_DATE_PATTERN, userLocale)
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

                // Group by category and sort: Government (PID) first, Health second, then by order
                val groupedByCategory = documents.groupBy { it.documentCategory }
                    .map { (category, docs) -> category to docs }
                    .sortedWith(compareBy { (category, _) ->
                        when (category) {
                            DocumentCategory.Government -> 0
                            DocumentCategory.Health -> 1
                            else -> 2 + category.order
                        }
                    })

                // Limit to 3 documents per category for home screen
                val limitedDocuments = groupedByCategory.map { (category, docs) ->
                    category to docs.take(3)
                }

                emit(HomeInteractorGetCredentialsPartialState.Success(limitedDocuments))
            } catch (e: Exception) {
                emit(HomeInteractorGetCredentialsPartialState.Failure(e.localizedMessage ?: genericErrorMsg))
            }
        }.flowOn(Dispatchers.IO)

    override fun getHeroCredential(): Flow<HomeInteractorGetHeroCredentialPartialState> =
        flow {
            try {
                val userLocale = resourceProvider.getLocale()
                val mainPid = walletCoreDocumentsController.getMainPidDocument()
                val issuedDocuments = walletCoreDocumentsController.getAllIssuedDocuments()
                val heroCredentials = mutableListOf<HeroCredentialUi>()
                for (document in issuedDocuments) {
                    val heroCredential = buildHeroCredential(document, userLocale)
                    if (heroCredential != null && heroCredential.isHeroCandidate()) {
                        heroCredentials.add(heroCredential)
                    }
                }
                val sortedHeroCredentials = heroCredentials.sortedWith(
                    compareByDescending<HeroCredentialUi> { heroCredential ->
                        heroCredential.documentId == mainPid?.id
                    }.thenBy { heroCredential ->
                        heroCredential.title.lowercase(Locale.getDefault())
                    }
                )
                emit(HomeInteractorGetHeroCredentialPartialState.Success(sortedHeroCredentials))
            } catch (e: Exception) {
                emit(
                    HomeInteractorGetHeroCredentialPartialState.Failure(
                        e.localizedMessage ?: genericErrorMsg
                    )
                )
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun buildHeroCredential(
        document: IssuedDocument,
        userLocale: Locale
    ): HeroCredentialUi? {
        val localizedIssuerMetadata = document.localizedIssuerMetadata(userLocale)
        val issuerName = localizedIssuerMetadata?.name
        val documentIdentifier = document.toDocumentIdentifier()
        val heroValidUntil = document.getValidUntil().getOrNull()
        val documentHasExpired = heroValidUntil?.let { documentHasExpired(it) } ?: false
        val documentIssuanceState = if (documentHasExpired) {
            DocumentIssuanceStateUi.Expired
        } else {
            DocumentIssuanceStateUi.Issued
        }
        val identityCardData = extractIdentityCardData(
            document = document,
            resourceProvider = resourceProvider
        )
        return HeroCredentialUi(
            documentId = document.id,
            documentIdentifier = documentIdentifier,
            title = document.name,
            subtitle = documentIdentifier.getSubtitle(),
            holderName = identityCardData.holderName,
            issuerName = issuerName,
            expiryDate = identityCardData.expiryDate,
            status = documentIssuanceState,
            hasPhoto = !identityCardData.portraitBase64.isNullOrBlank(),
            portraitBase64 = identityCardData.portraitBase64,
            nationality = identityCardData.nationality,
            birthDate = identityCardData.birthDate
        )
    }

    private fun HeroCredentialUi.isHeroCandidate(): Boolean {
        val formatType = documentIdentifier.formatType.lowercase(Locale.getDefault())
        val isPid = documentIdentifier == DocumentIdentifier.MdocPid
            || documentIdentifier == DocumentIdentifier.SdJwtPid
        val isMdoc = formatType.contains("mdoc")
            || formatType.contains("mdl")
        val isAuthbound = issuerName?.contains("authbound", ignoreCase = true) == true
        return isPid || isMdoc || isAuthbound
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
