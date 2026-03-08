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

package eu.europa.ec.corelogic.controller

import android.util.Log
import androidx.core.net.toUri
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationResult
import eu.europa.ec.authenticationlogic.model.BiometricCrypto
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.extension.documentIdentifier
import eu.europa.ec.corelogic.extension.getLocalizedDisplayName
import eu.europa.ec.corelogic.extension.parseTransactionLog
import eu.europa.ec.corelogic.extension.toCoreTransactionLog
import eu.europa.ec.corelogic.extension.toTransactionLogData
import eu.europa.ec.corelogic.model.DeferredDocumentDataDomain
import eu.europa.ec.corelogic.model.DocumentCategories
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.FormatType
import eu.europa.ec.corelogic.model.ScopedDocumentDomain
import eu.europa.ec.corelogic.model.TransactionLogDataDomain
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.eudi.openid4vci.CredentialIssuerMetadata
import eu.europa.ec.eudi.openid4vci.MsoMdocCredential
import eu.europa.ec.eudi.openid4vci.SdJwtVcCredential
import eu.europa.ec.eudi.statium.Status
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.CreateDocumentSettings
import eu.europa.ec.eudi.wallet.document.DeferredDocument
import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.eudi.wallet.document.DocumentExtensions.getDefaultCreateDocumentSettings
import eu.europa.ec.eudi.wallet.document.DocumentExtensions.getDefaultKeyUnlockData
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.eudi.wallet.issue.openid4vci.DeferredIssueResult
import eu.europa.ec.eudi.wallet.issue.openid4vci.IssueEvent
import eu.europa.ec.eudi.wallet.issue.openid4vci.Offer
import eu.europa.ec.eudi.wallet.issue.openid4vci.OfferResult
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.businesslogic.controller.wallet.UserDocumentOwnershipController
import eu.europa.ec.storagelogic.dao.BookmarkDao
import eu.europa.ec.storagelogic.dao.RevokedDocumentDao
import eu.europa.ec.storagelogic.dao.TransactionLogDao
import eu.europa.ec.storagelogic.model.Bookmark
import eu.europa.ec.storagelogic.model.RevokedDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Locale

enum class IssuanceMethod {
    OPENID4VCI
}

sealed class IssueDocumentsPartialState {
    data class Success(val documentIds: List<DocumentId>) : IssueDocumentsPartialState()
    data class DeferredSuccess(val deferredDocuments: Map<DocumentId, FormatType>) :
        IssueDocumentsPartialState()

    data class PartialSuccess(
        val documentIds: List<DocumentId>,
        val nonIssuedDocuments: Map<String, String>,
    ) : IssueDocumentsPartialState()

    data class Failure(val errorMessage: String) : IssueDocumentsPartialState()
    data class UserAuthRequired(
        val crypto: BiometricCrypto,
        val resultHandler: DeviceAuthenticationResult,
    ) : IssueDocumentsPartialState()
}

sealed class DeleteDocumentPartialState {
    data object Success : DeleteDocumentPartialState()
    data class Failure(val errorMessage: String) : DeleteDocumentPartialState()
}

sealed class DeleteAllDocumentsPartialState {
    data object Success : DeleteAllDocumentsPartialState()
    data class Failure(val errorMessage: String) : DeleteAllDocumentsPartialState()
}

sealed class ResolveDocumentOfferPartialState {
    data class Success(val offer: Offer) : ResolveDocumentOfferPartialState()
    data class Failure(val errorMessage: String) : ResolveDocumentOfferPartialState()
}

sealed class FetchScopedDocumentsPartialState {
    data class Success(val documents: List<ScopedDocumentDomain>) :
        FetchScopedDocumentsPartialState()

    data class Failure(val errorMessage: String) : FetchScopedDocumentsPartialState()
}

sealed class IssueDeferredDocumentPartialState {
    data class Issued(
        val deferredDocumentData: DeferredDocumentDataDomain,
    ) : IssueDeferredDocumentPartialState()

    data class NotReady(
        val deferredDocumentData: DeferredDocumentDataDomain,
    ) : IssueDeferredDocumentPartialState()

    data class Failed(
        val documentId: DocumentId,
        val errorMessage: String,
    ) : IssueDeferredDocumentPartialState()

    data class Expired(
        val documentId: DocumentId,
    ) : IssueDeferredDocumentPartialState()
}

/**
 * Controller for interacting with internal local storage of Core for CRUD operations on documents
 * */
interface WalletCoreDocumentsController {

    /**
     * @return All the documents from the Database.
     * */
    fun getAllDocuments(): List<Document>

    fun getAllIssuedDocuments(): List<IssuedDocument>

    fun getAllDocumentsByType(documentIdentifiers: List<DocumentIdentifier>): List<IssuedDocument>

    fun getDocumentById(documentId: DocumentId): Document?

    fun getMainPidDocument(): IssuedDocument?

    fun issueDocuments(
        issuanceMethod: IssuanceMethod,
        configIds: List<String>,
        issuerId: String,
        prioritizeDeferred: Boolean = false
    ): Flow<IssueDocumentsPartialState>

    fun issueDocumentsByOffer(
        offer: Offer,
        txCode: String? = null,
        prioritizeDeferred: Boolean = true
    ): Flow<IssueDocumentsPartialState>

    fun deleteDocument(
        documentId: String,
    ): Flow<DeleteDocumentPartialState>

    fun deleteAllDocuments(): Flow<DeleteAllDocumentsPartialState>

    fun resolveDocumentOffer(offerUri: String): Flow<ResolveDocumentOfferPartialState>

    fun issueDeferredDocument(docId: DocumentId): Flow<IssueDeferredDocumentPartialState>

    fun resumeOpenId4VciWithAuthorization(uri: String)

    suspend fun getScopedDocuments(locale: Locale): FetchScopedDocumentsPartialState

    fun getAllDocumentCategories(): DocumentCategories

    suspend fun getRevokedDocumentIds(): List<String>

    suspend fun isDocumentRevoked(id: String): Boolean

    suspend fun resolveDocumentStatus(document: IssuedDocument): Result<Status>

    suspend fun getTransactionLogs(): List<TransactionLogDataDomain>

    suspend fun getTransactionLog(id: String): TransactionLogDataDomain?

    suspend fun isDocumentBookmarked(documentId: DocumentId): Boolean

    suspend fun storeBookmark(bookmarkId: DocumentId)

    suspend fun deleteBookmark(bookmarkId: DocumentId)

    suspend fun isDocumentLowOnCredentials(document: IssuedDocument): Boolean

    suspend fun storeRevokedDocuments(revokedDocuments: List<IssuedDocument>)

    suspend fun removeRevokedDocumentsFromStorage(ids: List<String>)
}

class WalletCoreDocumentsControllerImpl(
    private val resourceProvider: ResourceProvider,
    private val eudiWallet: EudiWallet,
    private val walletCoreConfig: WalletCoreConfig,
    private val bookmarkDao: BookmarkDao,
    private val transactionLogDao: TransactionLogDao,
    private val revokedDocumentDao: RevokedDocumentDao,
    private val ownershipController: UserDocumentOwnershipController,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : WalletCoreDocumentsController {

    private val bindingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val genericErrorMessage
        get() = resourceProvider.genericErrorMessage()

    private val documentErrorMessage
        get() = resourceProvider.getString(R.string.issuance_generic_error)

    private val openId4VciManagers by lazy {
        walletCoreConfig.vciConfig.associate { config ->
            config.issuerUrl to eudiWallet.createOpenId4VciManager(config = config)
        }
    }

    override fun getAllDocuments(): List<Document> {
        val ownedIds = runBlocking(dispatcher) { ownershipController.getCurrentUserDocumentIds() }
        return eudiWallet.getDocuments { it is IssuedDocument || it is DeferredDocument }
            .filter { it.id in ownedIds }
    }

    override fun getAllIssuedDocuments(): List<IssuedDocument> =
        getAllDocuments().filterIsInstance<IssuedDocument>()

    override suspend fun getScopedDocuments(locale: Locale): FetchScopedDocumentsPartialState {
        return withContext(dispatcher) {
            runCatching {

                // Fetch metadata from each issuer, gracefully handling failures
                val metadata: Map<String, CredentialIssuerMetadata> =
                    openId4VciManagers.mapNotNull { (issuerUrl, manager) ->
                        manager.getIssuerMetadata()
                            .onFailure { error ->
                                Log.w(
                                    "WalletCoreDocumentsController",
                                    "Failed to fetch metadata from issuer: $issuerUrl - ${error.message}"
                                )
                            }
                            .getOrNull()
                            ?.let { issuerUrl to it }
                    }.toMap()

                val documents: List<ScopedDocumentDomain> =
                    metadata.flatMap { (issuer, meta) ->
                        meta.credentialConfigurationsSupported.map { (id, config) ->

                            val name: String = config.credentialMetadata.getLocalizedDisplayName(
                                userLocale = locale,
                                fallback = id.value
                            )

                            val isPid = when (config) {
                                is MsoMdocCredential -> config.docType.toDocumentIdentifier() == DocumentIdentifier.MdocPid
                                is SdJwtVcCredential -> config.type.toDocumentIdentifier() == DocumentIdentifier.SdJwtPid
                                else -> false
                            }

                            val formatType = when (config) {
                                is MsoMdocCredential -> config.docType
                                is SdJwtVcCredential -> config.type
                                else -> null
                            }

                            ScopedDocumentDomain(
                                name = name,
                                configurationId = id.value,
                                credentialIssuerId = issuer,
                                formatType = formatType,
                                isPid = isPid
                            )
                        }
                    }

                if (documents.isNotEmpty()) {
                    FetchScopedDocumentsPartialState.Success(documents = documents)
                } else {
                    FetchScopedDocumentsPartialState.Failure(errorMessage = genericErrorMessage)
                }
            }
        }.getOrElse {
            FetchScopedDocumentsPartialState.Failure(
                errorMessage = it.localizedMessage ?: genericErrorMessage
            )
        }
    }

    override fun getAllDocumentsByType(documentIdentifiers: List<DocumentIdentifier>): List<IssuedDocument> =
        getAllDocuments()
            .filterIsInstance<IssuedDocument>()
            .filter {
                when (it.format) {
                    is MsoMdocFormat -> documentIdentifiers.any { id ->
                        id.formatType == (it.format as MsoMdocFormat).docType
                    }

                    is SdJwtVcFormat -> documentIdentifiers.any { id ->
                        id.formatType == (it.format as SdJwtVcFormat).vct
                    }
                }
            }

    override fun getDocumentById(documentId: DocumentId): Document? {
        val isOwned = runBlocking(dispatcher) {
            ownershipController.isDocumentOwnedByCurrentUser(documentId)
        }
        if (!isOwned) return null
        return eudiWallet.getDocumentById(documentId = documentId)
    }

    override fun getMainPidDocument(): IssuedDocument? =
        getAllDocumentsByType(
            documentIdentifiers = listOf(
                DocumentIdentifier.MdocPid,
                DocumentIdentifier.SdJwtPid
            )
        ).minByOrNull { it.createdAt }

    override fun issueDocuments(
        issuanceMethod: IssuanceMethod,
        configIds: List<String>,
        issuerId: String,
        prioritizeDeferred: Boolean
    ): Flow<IssueDocumentsPartialState> = flow {
        when (issuanceMethod) {
            IssuanceMethod.OPENID4VCI -> {
                issueDocumentsWithOpenId4VCI(
                    configIds,
                    issuerId,
                    prioritizeDeferred
                ).collect { response ->
                    when (response) {
                        is IssueDocumentsPartialState.Failure -> emit(
                            IssueDocumentsPartialState.Failure(
                                errorMessage = documentErrorMessage
                            )
                        )

                        is IssueDocumentsPartialState.Success -> emit(
                            IssueDocumentsPartialState.Success(
                                response.documentIds
                            )
                        )

                        is IssueDocumentsPartialState.UserAuthRequired -> emit(
                            IssueDocumentsPartialState.UserAuthRequired(
                                crypto = response.crypto,
                                resultHandler = response.resultHandler
                            )
                        )

                        is IssueDocumentsPartialState.PartialSuccess -> emit(
                            IssueDocumentsPartialState.Success(
                                response.documentIds
                            )
                        )

                        is IssueDocumentsPartialState.DeferredSuccess -> emit(
                            IssueDocumentsPartialState.DeferredSuccess(
                                response.deferredDocuments
                            )
                        )
                    }
                }
            }
        }
    }.safeAsync {
        IssueDocumentsPartialState.Failure(errorMessage = documentErrorMessage)
    }

    override fun issueDocumentsByOffer(
        offer: Offer,
        txCode: String?,
        prioritizeDeferred: Boolean
    ): Flow<IssueDocumentsPartialState> =
        callbackFlow {
            val issuerId = offer
                .credentialOffer
                .credentialIssuerIdentifier
                .toString()

            val manager = openId4VciManagers[issuerId]
                ?: openId4VciManagers.values.firstOrNull()

            require(manager != null) { documentErrorMessage }

            manager.issueDocumentByOffer(
                offer = offer,
                onIssueEvent = issuanceCallback(prioritizeDeferred),
                txCode = txCode,
            )

            awaitClose()
        }.safeAsync {
            IssueDocumentsPartialState.Failure(
                errorMessage = documentErrorMessage
            )
        }

    override fun deleteDocument(documentId: String): Flow<DeleteDocumentPartialState> = flow {
        val isOwned = ownershipController.isDocumentOwnedByCurrentUser(documentId)
        if (!isOwned) {
            emit(DeleteDocumentPartialState.Failure(errorMessage = genericErrorMessage))
            return@flow
        }
        val userId = ownershipController.requireCurrentUserId()
        eudiWallet.deleteDocumentById(documentId = documentId)
            .kotlinResult
            .onSuccess {
                revokedDocumentDao.delete(documentId, userId)
                ownershipController.unbindDocument(documentId)
                emit(DeleteDocumentPartialState.Success)
            }
            .onFailure {
                emit(
                    DeleteDocumentPartialState.Failure(
                        errorMessage = it.localizedMessage
                            ?: genericErrorMessage
                    )
                )
            }
    }.safeAsync {
        DeleteDocumentPartialState.Failure(
            errorMessage = it.localizedMessage ?: genericErrorMessage
        )
    }

    override fun deleteAllDocuments(): Flow<DeleteAllDocumentsPartialState> =
        flow {

            val allDocuments = getAllDocuments()
            val mainPidDocument = getMainPidDocument()

            mainPidDocument?.let { safeMainPidDocument ->

                val restOfDocuments = allDocuments.filterNot { doc ->
                    doc.id == safeMainPidDocument.id
                }

                var restOfAllDocsDeleted = true
                var restOfAllDocsDeletedFailureReason = ""

                restOfDocuments.forEach { document ->

                    deleteDocument(
                        documentId = document.id
                    ).collect { deleteDocumentPartialState ->
                        when (deleteDocumentPartialState) {
                            is DeleteDocumentPartialState.Failure -> {
                                restOfAllDocsDeleted = false
                                restOfAllDocsDeletedFailureReason =
                                    deleteDocumentPartialState.errorMessage
                            }

                            is DeleteDocumentPartialState.Success -> {}
                        }
                    }
                }

                if (restOfAllDocsDeleted) {
                    deleteDocument(
                        documentId = safeMainPidDocument.id
                    ).collect { deleteMainPidDocumentPartialState ->
                        when (deleteMainPidDocumentPartialState) {
                            is DeleteDocumentPartialState.Failure -> emit(
                                DeleteAllDocumentsPartialState.Failure(
                                    errorMessage = deleteMainPidDocumentPartialState.errorMessage
                                )
                            )

                            is DeleteDocumentPartialState.Success -> emit(
                                DeleteAllDocumentsPartialState.Success
                            )
                        }
                    }
                } else {
                    emit(DeleteAllDocumentsPartialState.Failure(errorMessage = restOfAllDocsDeletedFailureReason))
                }
            } ?: emit(
                DeleteAllDocumentsPartialState.Failure(
                    errorMessage = genericErrorMessage
                )
            )
        }.safeAsync {
            DeleteAllDocumentsPartialState.Failure(
                errorMessage = it.localizedMessage ?: genericErrorMessage
            )
        }

    override fun resolveDocumentOffer(offerUri: String): Flow<ResolveDocumentOfferPartialState> =
        callbackFlow {
            val issuerId = extractCredentialIssuerFromOfferUri(offerUri).getOrNull()
            val managerEntries = if (issuerId != null) {
                val preferred = openId4VciManagers[issuerId]?.let { manager -> issuerId to manager }
                listOfNotNull(preferred)
            } else {
                openId4VciManagers.map { entry -> entry.key to entry.value }
            }
            Log.d(
                "WalletCoreDocumentsController",
                "resolveDocumentOffer offerUri=$offerUri issuerId=$issuerId managers=${managerEntries.size}"
            )
            if (issuerId != null && managerEntries.isEmpty()) {
                Log.e(
                    "WalletCoreDocumentsController",
                    "resolveDocumentOffer missing manager for issuerId=$issuerId"
                )
            }
            require(managerEntries.isNotEmpty()) { genericErrorMessage }
            fun resolveWithManager(index: Int) {
                val (managerId, manager) = managerEntries[index]
                Log.d(
                    "WalletCoreDocumentsController",
                    "resolveDocumentOffer using manager=$managerId index=$index"
                )
                manager.resolveDocumentOffer(offerUri) { result ->
                    when (result) {
                        is OfferResult.Failure -> {
                            Log.e(
                                "WalletCoreDocumentsController",
                                "resolveDocumentOffer failure manager=$managerId cause=${result.cause::class.java.name} message=${result.cause.message}",
                                result.cause
                            )
                            val nextIndex = index + 1
                            if (nextIndex < managerEntries.size) {
                                Log.w(
                                    "WalletCoreDocumentsController",
                                    "resolveDocumentOffer retry with fallback manager index=$nextIndex"
                                )
                                resolveWithManager(nextIndex)
                            } else {
                                trySendBlocking(
                                    ResolveDocumentOfferPartialState.Failure(
                                        result.cause.localizedMessage ?: genericErrorMessage
                                    )
                                )
                                close()
                            }
                        }
                        is OfferResult.Success -> {
                            Log.d(
                                "WalletCoreDocumentsController",
                                "resolveDocumentOffer success manager=$managerId offerIssuer=${result.offer.credentialOffer.credentialIssuerIdentifier}"
                            )
                            trySendBlocking(
                                ResolveDocumentOfferPartialState.Success(
                                    offer = result.offer
                                )
                            )
                            close()
                        }
                    }
                }
            }
            resolveWithManager(0)
            awaitClose()
        }.safeAsync {
            Log.e(
                "WalletCoreDocumentsController",
                "resolveDocumentOffer exception: ${it.message}"
            )
            ResolveDocumentOfferPartialState.Failure(
                errorMessage = it.localizedMessage ?: genericErrorMessage
            )
        }

    override fun issueDeferredDocument(docId: DocumentId): Flow<IssueDeferredDocumentPartialState> =
        callbackFlow {
            (getDocumentById(docId) as? DeferredDocument)?.let { deferredDoc ->

                val manager = deferredDoc.issuerMetadata?.credentialIssuerIdentifier
                    ?.let(openId4VciManagers::get)
                    ?: openId4VciManagers.values.firstOrNull()

                require(manager != null) { documentErrorMessage }

                manager.issueDeferredDocument(
                    deferredDocument = deferredDoc,
                    executor = null,
                    onIssueResult = { deferredIssuanceResult ->
                        when (deferredIssuanceResult) {
                            is DeferredIssueResult.DocumentFailed -> {
                                trySendBlocking(
                                    IssueDeferredDocumentPartialState.Failed(
                                        documentId = deferredIssuanceResult.documentId,
                                        errorMessage = deferredIssuanceResult.cause.localizedMessage
                                            ?: documentErrorMessage
                                    )
                                )
                            }

                            is DeferredIssueResult.DocumentIssued -> {
                                bindingScope.launch {
                                    try {
                                        ownershipController.bindDocumentToCurrentUser(deferredIssuanceResult.documentId)
                                    } catch (e: Exception) {
                                        Log.e("WalletCoreDocs", "Failed to bind deferred document ${deferredIssuanceResult.documentId}: ${e.message}")
                                    }
                                }
                                trySendBlocking(
                                    IssueDeferredDocumentPartialState.Issued(
                                        DeferredDocumentDataDomain(
                                            documentId = deferredIssuanceResult.documentId,
                                            formatType = deferredIssuanceResult.docType,
                                            docName = deferredIssuanceResult.name
                                        )
                                    )
                                )
                            }

                            is DeferredIssueResult.DocumentNotReady -> {
                                trySendBlocking(
                                    IssueDeferredDocumentPartialState.NotReady(
                                        DeferredDocumentDataDomain(
                                            documentId = deferredIssuanceResult.documentId,
                                            formatType = deferredIssuanceResult.docType,
                                            docName = deferredIssuanceResult.name
                                        )
                                    )
                                )
                            }

                            is DeferredIssueResult.DocumentExpired -> {
                                trySendBlocking(
                                    IssueDeferredDocumentPartialState.Expired(
                                        documentId = deferredIssuanceResult.documentId
                                    )
                                )
                            }
                        }
                    }
                )
            } ?: trySendBlocking(
                IssueDeferredDocumentPartialState.Failed(
                    documentId = docId,
                    errorMessage = documentErrorMessage
                )
            )

            awaitClose()
        }.safeAsync {
            IssueDeferredDocumentPartialState.Failed(
                documentId = docId,
                errorMessage = it.localizedMessage ?: genericErrorMessage
            )
        }

    override fun resumeOpenId4VciWithAuthorization(uri: String) {
        for (manager in openId4VciManagers.values) {
            try {
                manager.resumeWithAuthorization(uri)
                break
            } catch (_: Exception) {
            }
        }
    }

    override fun getAllDocumentCategories(): DocumentCategories {
        return walletCoreConfig.documentCategories
    }

    override suspend fun getTransactionLogs(): List<TransactionLogDataDomain> =
        withContext(dispatcher) {
            val userId = ownershipController.getCurrentUserId() ?: ""
            transactionLogDao.retrieveAllForUser(userId)
                .mapNotNull { transactionLog ->
                    transactionLog
                        .toCoreTransactionLog()
                        ?.parseTransactionLog()
                        ?.toTransactionLogData(transactionLog.identifier)
                }
        }

    override suspend fun getTransactionLog(id: String): TransactionLogDataDomain? =
        withContext(dispatcher) {
            val userId = ownershipController.getCurrentUserId() ?: ""
            transactionLogDao.retrieve(id, userId)
                ?.toCoreTransactionLog()
                ?.parseTransactionLog()
                ?.toTransactionLogData(id)
        }

    override suspend fun isDocumentBookmarked(documentId: DocumentId): Boolean {
        val userId = ownershipController.requireCurrentUserId()
        return bookmarkDao.retrieve(documentId, userId) != null
    }

    override suspend fun storeBookmark(bookmarkId: DocumentId) {
        val userId = ownershipController.requireCurrentUserId()
        bookmarkDao.store(Bookmark(bookmarkId, userId))
    }

    override suspend fun deleteBookmark(bookmarkId: DocumentId) {
        val userId = ownershipController.requireCurrentUserId()
        bookmarkDao.delete(bookmarkId, userId)
    }

    override suspend fun isDocumentLowOnCredentials(document: IssuedDocument): Boolean {
        val documentRemainingCredentials = document.credentialsCount()

        return document.credentialPolicy == CreateDocumentSettings.CredentialPolicy.OneTimeUse
                && documentRemainingCredentials <= 1
    }

    override suspend fun getRevokedDocumentIds(): List<String> {
        val userId = ownershipController.getCurrentUserId() ?: ""
        return revokedDocumentDao.retrieveAllForUser(userId).map { it.identifier }
    }

    override suspend fun isDocumentRevoked(id: String): Boolean {
        val userId = ownershipController.getCurrentUserId() ?: ""
        return revokedDocumentDao.retrieve(id, userId) != null
    }

    override suspend fun storeRevokedDocuments(revokedDocuments: List<IssuedDocument>) {
        val userId = ownershipController.requireCurrentUserId()
        revokedDocumentDao.storeAll(
            revokedDocuments.map { RevokedDocument(identifier = it.id, userId = userId) }
        )
    }

    override suspend fun removeRevokedDocumentsFromStorage(ids: List<String>) {
        val userId = ownershipController.requireCurrentUserId()
        ids.forEach { revokedDocumentDao.delete(it, userId) }
    }

    override suspend fun resolveDocumentStatus(document: IssuedDocument): Result<Status> =
        eudiWallet.resolveStatus(document)

    private fun issueDocumentsWithOpenId4VCI(
        configIds: List<String>,
        issuerId: String,
        prioritizeDeferred: Boolean
    ): Flow<IssueDocumentsPartialState> =
        callbackFlow {

            val manager = openId4VciManagers[issuerId]
            require(manager != null) { documentErrorMessage }

            manager.issueDocumentByConfigurationIdentifiers(
                credentialConfigurationIds = configIds,
                onIssueEvent = issuanceCallback(prioritizeDeferred)
            )

            awaitClose()

        }.safeAsync {
            IssueDocumentsPartialState.Failure(
                errorMessage = documentErrorMessage
            )
        }

    private fun ProducerScope<IssueDocumentsPartialState>.issuanceCallback(
        prioritizeDeferred: Boolean = true
    ): OpenId4VciManager.OnIssueEvent {

        var totalDocumentsToBeIssued = 0
        val nonIssuedDocuments: MutableMap<FormatType, String> = mutableMapOf()
        val deferredDocuments: MutableMap<DocumentId, FormatType> = mutableMapOf()
        val issuedDocuments: MutableMap<DocumentId, FormatType> = mutableMapOf()

        val listener = OpenId4VciManager.OnIssueEvent { event ->
            when (event) {
                is IssueEvent.DocumentFailed -> {
                    nonIssuedDocuments[event.docType] = event.name
                }

                is IssueEvent.DocumentRequiresCreateSettings -> {
                    launch {
                        val offeredDocIdentifier = event.offeredDocument.documentIdentifier

                        val documentIssuanceRule = walletCoreConfig
                            .documentIssuanceConfig
                            .getRuleForDocument(documentIdentifier = offeredDocIdentifier)

                        event.resume(
                            eudiWallet.getDefaultCreateDocumentSettings(
                                offeredDocument = event.offeredDocument,
                                credentialPolicy = documentIssuanceRule.policy,
                                numberOfCredentials = documentIssuanceRule.numberOfCredentials,
                            )
                        )
                    }
                }

                is IssueEvent.DocumentRequiresUserAuth -> {
                    launch {
                        val keyUnlockDataMap =
                            event.keysRequireAuth.mapValues { (keyAlias, secureArea) ->
                                getDefaultKeyUnlockData(secureArea, keyAlias)
                            }

                        val keyUnlockData =
                            keyUnlockDataMap.values.first() //TODO: Revisit this once Core adds support.

                        val cryptoObject = keyUnlockData?.getCryptoObjectForSigning()

                        trySendBlocking(
                            IssueDocumentsPartialState.UserAuthRequired(
                                crypto = BiometricCrypto(cryptoObject),
                                resultHandler = DeviceAuthenticationResult(
                                    onAuthenticationSuccess = { event.resume(keyUnlockDataMap) },
                                    onAuthenticationError = { event.cancel(null) }
                                )
                            )
                        )
                    }
                }

                is IssueEvent.Failure -> {
                    trySendBlocking(
                        IssueDocumentsPartialState.Failure(
                            errorMessage = documentErrorMessage
                        )
                    )
                }

                is IssueEvent.Finished -> {

                    if (deferredDocuments.isNotEmpty() && (prioritizeDeferred || (issuedDocuments.isEmpty()))) {
                        trySendBlocking(IssueDocumentsPartialState.DeferredSuccess(deferredDocuments))
                        return@OnIssueEvent
                    }

                    if (event.issuedDocuments.isEmpty()) {
                        trySendBlocking(
                            IssueDocumentsPartialState.Failure(
                                errorMessage = documentErrorMessage
                            )
                        )
                        return@OnIssueEvent
                    }

                    if (event.issuedDocuments.size == totalDocumentsToBeIssued) {
                        trySendBlocking(
                            IssueDocumentsPartialState.Success(
                                documentIds = event.issuedDocuments
                            )
                        )
                        return@OnIssueEvent
                    }

                    trySendBlocking(
                        IssueDocumentsPartialState.PartialSuccess(
                            documentIds = event.issuedDocuments,
                            nonIssuedDocuments = nonIssuedDocuments
                        )
                    )
                }

                is IssueEvent.DocumentIssued -> {
                    issuedDocuments[event.documentId] = event.docType
                    bindingScope.launch {
                        try {
                            ownershipController.bindDocumentToCurrentUser(event.documentId)
                        } catch (e: Exception) {
                            Log.e("WalletCoreDocs", "Failed to bind document ${event.documentId}: ${e.message}")
                        }
                    }
                }

                is IssueEvent.Started -> {
                    totalDocumentsToBeIssued = event.total
                }

                is IssueEvent.DocumentDeferred -> {
                    deferredDocuments[event.documentId] = event.docType
                    bindingScope.launch {
                        try {
                            ownershipController.bindDocumentToCurrentUser(event.documentId)
                        } catch (e: Exception) {
                            Log.e("WalletCoreDocs", "Failed to bind document ${event.documentId}: ${e.message}")
                        }
                    }
                }
            }
        }

        return listener
    }

    private fun extractCredentialIssuerFromOfferUri(offerUri: String): Result<String> =
        runCatching {
            val uri = offerUri.toUri()
            val credentialOffer = uri.getQueryParameter("credential_offer")
            if (!credentialOffer.isNullOrBlank()) {
                val decoded = URLDecoder.decode(credentialOffer, "UTF-8")
                val json = JSONObject(decoded)
                return@runCatching json.getString("credential_issuer")
            }
            val credentialOfferUri = uri.getQueryParameter("credential_offer_uri")
            if (!credentialOfferUri.isNullOrBlank()) {
                return@runCatching deriveIssuerFromCredentialOfferUri(credentialOfferUri)
            }
            throw IllegalArgumentException("Missing credential offer parameters")
        }

    private fun deriveIssuerFromCredentialOfferUri(offerUri: String): String {
        val uri = offerUri.toUri()
        val path = uri.path.orEmpty()
        val basePath = when {
            path.contains("/credential-offer/") -> path.substringBefore("/credential-offer/")
            path.contains("/credential-offer") -> path.substringBefore("/credential-offer")
            else -> path.substringBeforeLast("/")
        }
        val issuerPath = if (basePath.endsWith("/service")) {
            "$basePath/draft-13"
        } else {
            basePath
        }
        val issuer = uri.buildUpon().path(issuerPath).build().toString()
        Log.d("WalletCoreDocumentsController", "deriveIssuerFromCredentialOfferUri offerUri=$offerUri issuer=$issuer")
        return issuer
    }
}