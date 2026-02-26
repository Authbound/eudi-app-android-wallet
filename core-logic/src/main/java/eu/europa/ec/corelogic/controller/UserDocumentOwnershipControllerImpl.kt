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

import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepository
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.businesslogic.controller.wallet.UserDocumentOwnershipController
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.storagelogic.dao.BookmarkDao
import eu.europa.ec.storagelogic.dao.RevokedDocumentDao
import eu.europa.ec.storagelogic.dao.TransactionLogDao
import eu.europa.ec.storagelogic.dao.UserDocumentMappingDao
import eu.europa.ec.storagelogic.model.UserDocumentMapping

class UserDocumentOwnershipControllerImpl(
    private val userDocumentMappingDao: UserDocumentMappingDao,
    private val eudiWallet: EudiWallet,
    private val supabaseAuthRepository: SupabaseAuthRepository,
    private val prefsControllerV2: PrefsControllerV2,
    private val bookmarkDao: BookmarkDao,
    private val revokedDocumentDao: RevokedDocumentDao,
    private val transactionLogDao: TransactionLogDao,
    private val logController: LogController
) : UserDocumentOwnershipController {

    override suspend fun getCurrentUserId(): String? {
        return supabaseAuthRepository.getCurrentUserId()
    }

    override suspend fun requireCurrentUserId(): String {
        return getCurrentUserId()
            ?: throw SecurityException("No authenticated user session — cannot determine document ownership")
    }

    override suspend fun bindDocumentToCurrentUser(documentId: String) {
        val userId = requireCurrentUserId()
        val mapping = UserDocumentMapping(
            documentId = documentId,
            userId = userId
        )
        userDocumentMappingDao.store(mapping)
        logController.d(TAG, "Bound document $documentId to user $userId")
    }

    override suspend fun bindDocumentsToCurrentUser(documentIds: List<String>) {
        if (documentIds.isEmpty()) return
        val userId = requireCurrentUserId()
        val mappings = documentIds.map { docId ->
            UserDocumentMapping(documentId = docId, userId = userId)
        }
        userDocumentMappingDao.storeAll(mappings)
        logController.d(TAG, "Bound ${documentIds.size} documents to user $userId")
    }

    override suspend fun isDocumentOwnedByCurrentUser(documentId: String): Boolean {
        val userId = getCurrentUserId() ?: return false
        return userDocumentMappingDao.getMapping(documentId, userId) != null
    }

    override suspend fun getCurrentUserDocumentIds(): Set<String> {
        val userId = getCurrentUserId() ?: return emptySet()
        return userDocumentMappingDao.getDocumentIdsForUser(userId).toSet()
    }

    override suspend fun unbindDocument(documentId: String) {
        userDocumentMappingDao.deleteByDocumentId(documentId)
        logController.d(TAG, "Unbound document $documentId from all users")
    }

    override suspend fun unbindAllDocumentsForCurrentUser() {
        val userId = requireCurrentUserId()
        userDocumentMappingDao.deleteAllForUser(userId)
        logController.d(TAG, "Unbound all documents for user $userId")
    }

    override suspend fun migrateOrphanedDocumentsToCurrentUser(): Int {
        val userId = requireCurrentUserId()

        // Find SDK documents not mapped to any user
        val allSdkDocIds = eudiWallet.getDocuments().map { it.id }.toSet()
        val allMappedDocIds = userDocumentMappingDao.getAllMappedDocumentIds().toSet()
        val orphanedDocIds = allSdkDocIds - allMappedDocIds

        if (orphanedDocIds.isNotEmpty()) {
            val mappings = orphanedDocIds.map { docId ->
                UserDocumentMapping(documentId = docId, userId = userId)
            }
            userDocumentMappingDao.storeAll(mappings)
            logController.d(TAG, "Migrated ${orphanedDocIds.size} orphaned SDK documents to user $userId")
        }

        // Migrate Room records with empty userId
        runCatching { bookmarkDao.migrateOrphanedToUser(userId) }
        runCatching { revokedDocumentDao.migrateOrphanedToUser(userId) }
        runCatching { transactionLogDao.migrateOrphanedToUser(userId) }

        return orphanedDocIds.size
    }

    override suspend fun isMigrationCompleted(): Boolean {
        return prefsControllerV2.getBool(PREF_KEY_MIGRATION_COMPLETED, false)
    }

    override suspend fun setMigrationCompleted() {
        prefsControllerV2.setBool(PREF_KEY_MIGRATION_COMPLETED, true)
    }

    companion object {
        private const val TAG = "UserDocOwnership"
        private const val PREF_KEY_MIGRATION_COMPLETED = "user_document_migration_completed"
    }
}
