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
package eu.europa.ec.corelogic.controller

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.wallet.LocalWalletCleanupController
import eu.europa.ec.businesslogic.controller.wallet.UserDocumentOwnershipController
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.storagelogic.dao.BookmarkDao
import eu.europa.ec.storagelogic.dao.RevokedDocumentDao
import eu.europa.ec.storagelogic.dao.TransactionLogDao

class LocalWalletCleanupControllerImpl(
    private val eudiWallet: EudiWallet,
    private val bookmarkDao: BookmarkDao,
    private val transactionLogDao: TransactionLogDao,
    private val revokedDocumentDao: RevokedDocumentDao,
    private val ownershipController: UserDocumentOwnershipController,
    private val logController: LogController
) : LocalWalletCleanupController {

    override suspend fun cleanupLocalWalletData(): List<String> {
        val failures = mutableListOf<String>()

        val userId = ownershipController.getCurrentUserId()

        if (userId != null) {
            // User-scoped cleanup: only delete current user's documents
            runCatching {
                val ownedDocIds = ownershipController.getCurrentUserDocumentIds()
                for (docId in ownedDocIds) {
                    runCatching {
                        eudiWallet.deleteDocumentById(docId)
                    }.onFailure { e ->
                        logController.w(TAG) { "Failed to delete document $docId: ${e.message}" }
                        failures.add("Delete document $docId")
                    }
                }
            }.onFailure { e ->
                logController.w(TAG) { "Failed to get owned document IDs: ${e.message}" }
                failures.add("Get owned documents")
            }

            // Clear user-scoped Room records
            runCatching { bookmarkDao.deleteAllForUser(userId) }.onFailure { e ->
                logController.w(TAG) { "Failed to clear bookmarks: ${e.message}" }
                failures.add("Clear bookmarks")
            }
            runCatching { transactionLogDao.deleteAllForUser(userId) }.onFailure { e ->
                logController.w(TAG) { "Failed to clear transaction logs: ${e.message}" }
                failures.add("Clear transaction logs")
            }
            runCatching { revokedDocumentDao.deleteAllForUser(userId) }.onFailure { e ->
                logController.w(TAG) { "Failed to clear revoked documents: ${e.message}" }
                failures.add("Clear revoked documents")
            }

            // Unbind ownership mappings
            runCatching { ownershipController.unbindAllDocumentsForCurrentUser() }.onFailure { e ->
                logController.w(TAG) { "Failed to unbind document mappings: ${e.message}" }
                failures.add("Unbind document mappings")
            }
        } else {
            // Fallback: no session — delete all documents (nuclear cleanup)
            logController.w(TAG) { "No user session — falling back to unscoped cleanup" }
            runCatching {
                val documents = eudiWallet.getDocuments()
                for (doc in documents) {
                    runCatching {
                        eudiWallet.deleteDocumentById(doc.id)
                    }.onFailure { e ->
                        logController.w(TAG) { "Failed to delete document ${doc.id}: ${e.message}" }
                        failures.add("Delete document ${doc.id}")
                    }
                }
            }.onFailure { e ->
                logController.w(TAG) { "Failed to list documents: ${e.message}" }
                failures.add("List documents")
            }

            runCatching { bookmarkDao.deleteAll() }.onFailure { e ->
                logController.w(TAG) { "Failed to clear bookmarks: ${e.message}" }
                failures.add("Clear bookmarks")
            }
            runCatching { transactionLogDao.deleteAll() }.onFailure { e ->
                logController.w(TAG) { "Failed to clear transaction logs: ${e.message}" }
                failures.add("Clear transaction logs")
            }
            runCatching { revokedDocumentDao.deleteAll() }.onFailure { e ->
                logController.w(TAG) { "Failed to clear revoked documents: ${e.message}" }
                failures.add("Clear revoked documents")
            }
        }

        if (failures.isEmpty()) {
            logController.d(TAG, "Local wallet data cleaned up successfully")
        } else {
            logController.w(TAG) { "Local cleanup completed with ${failures.size} failures: $failures" }
        }

        return failures
    }

    companion object {
        private const val TAG = "LocalWalletCleanup"
    }
}
