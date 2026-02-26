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

package eu.europa.ec.businesslogic.controller.wallet

/**
 * Controls the mapping between EUDI SDK documents and Supabase users.
 *
 * The EUDI SDK stores credentials at the device level with no user scoping.
 * This controller maintains a Room-backed mapping table that records which
 * documents belong to which user, enabling multi-user credential isolation
 * on shared devices.
 *
 * Interface in business-logic for dependency inversion; implementation in core-logic.
 */
interface UserDocumentOwnershipController {

    suspend fun getCurrentUserId(): String?

    @Throws(SecurityException::class)
    suspend fun requireCurrentUserId(): String

    suspend fun bindDocumentToCurrentUser(documentId: String)

    suspend fun bindDocumentsToCurrentUser(documentIds: List<String>)

    suspend fun isDocumentOwnedByCurrentUser(documentId: String): Boolean

    suspend fun getCurrentUserDocumentIds(): Set<String>

    suspend fun unbindDocument(documentId: String)

    suspend fun unbindAllDocumentsForCurrentUser()

    /**
     * Migrates orphaned documents (those in the EUDI SDK but not mapped to any user)
     * and orphaned Room records (those with empty userId) to the current user.
     *
     * @return the number of documents migrated
     */
    suspend fun migrateOrphanedDocumentsToCurrentUser(): Int

    suspend fun isMigrationCompleted(): Boolean

    suspend fun setMigrationCompleted()
}
