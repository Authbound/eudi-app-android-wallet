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

package eu.europa.ec.storagelogic.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import eu.europa.ec.storagelogic.model.UserDocumentMapping

@Dao
interface UserDocumentMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun store(mapping: UserDocumentMapping)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun storeAll(mappings: List<UserDocumentMapping>)

    @Query("SELECT documentId FROM user_document_mappings WHERE userId = :userId")
    suspend fun getDocumentIdsForUser(userId: String): List<String>

    @Query("SELECT * FROM user_document_mappings WHERE documentId = :documentId AND userId = :userId")
    suspend fun getMapping(documentId: String, userId: String): UserDocumentMapping?

    @Query("DELETE FROM user_document_mappings WHERE documentId = :documentId")
    suspend fun deleteByDocumentId(documentId: String)

    @Query("DELETE FROM user_document_mappings WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT documentId FROM user_document_mappings")
    suspend fun getAllMappedDocumentIds(): List<String>

    @Query("DELETE FROM user_document_mappings")
    suspend fun deleteAll()
}
