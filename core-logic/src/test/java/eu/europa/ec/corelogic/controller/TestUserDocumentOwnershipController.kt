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
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.storagelogic.dao.BookmarkDao
import eu.europa.ec.storagelogic.dao.RevokedDocumentDao
import eu.europa.ec.storagelogic.dao.TransactionLogDao
import eu.europa.ec.storagelogic.dao.UserDocumentMappingDao
import eu.europa.ec.storagelogic.model.UserDocumentMapping
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TestUserDocumentOwnershipController {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var userDocumentMappingDao: UserDocumentMappingDao

    @Mock
    private lateinit var eudiWallet: EudiWallet

    @Mock
    private lateinit var supabaseAuthRepository: SupabaseAuthRepository

    @Mock
    private lateinit var prefsControllerV2: PrefsControllerV2

    @Mock
    private lateinit var bookmarkDao: BookmarkDao

    @Mock
    private lateinit var revokedDocumentDao: RevokedDocumentDao

    @Mock
    private lateinit var transactionLogDao: TransactionLogDao

    @Mock
    private lateinit var logController: LogController

    private lateinit var controller: UserDocumentOwnershipControllerImpl
    private lateinit var closeable: AutoCloseable

    @Before
    fun setup() {
        closeable = MockitoAnnotations.openMocks(this)
        controller = UserDocumentOwnershipControllerImpl(
            userDocumentMappingDao = userDocumentMappingDao,
            eudiWallet = eudiWallet,
            supabaseAuthRepository = supabaseAuthRepository,
            prefsControllerV2 = prefsControllerV2,
            bookmarkDao = bookmarkDao,
            revokedDocumentDao = revokedDocumentDao,
            transactionLogDao = transactionLogDao,
            logController = logController
        )
    }

    //region getCurrentUserId

    @Test
    fun `getCurrentUserId returns userId when user is authenticated`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(USER_A)

            val result = controller.getCurrentUserId()

            assertEquals(USER_A, result)
        }

    @Test
    fun `getCurrentUserId returns null when no user session`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(null)

            val result = controller.getCurrentUserId()

            assertEquals(null, result)
        }

    //endregion

    //region requireCurrentUserId

    @Test
    fun `requireCurrentUserId returns userId when user is authenticated`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(USER_A)

            val result = controller.requireCurrentUserId()

            assertEquals(USER_A, result)
        }

    @Test(expected = SecurityException::class)
    fun `requireCurrentUserId throws SecurityException when no user session`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(null)

            controller.requireCurrentUserId()
        }

    //endregion

    //region bindDocumentToCurrentUser

    @Test
    fun `bindDocumentToCurrentUser stores mapping with correct userId and documentId`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(USER_A)

            controller.bindDocumentToCurrentUser(DOC_1)

            verify(userDocumentMappingDao).store(argThat { mapping ->
                mapping.documentId == DOC_1 && mapping.userId == USER_A
            })
        }

    @Test(expected = SecurityException::class)
    fun `bindDocumentToCurrentUser throws when no user session`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(null)

            controller.bindDocumentToCurrentUser(DOC_1)
        }

    //endregion

    //region bindDocumentsToCurrentUser

    @Test
    fun `bindDocumentsToCurrentUser stores all mappings`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(USER_A)

            controller.bindDocumentsToCurrentUser(listOf(DOC_1, DOC_2, DOC_3))

            verify(userDocumentMappingDao).storeAll(argThat { mappings ->
                mappings.size == 3 &&
                        mappings.map { it.documentId } == listOf(DOC_1, DOC_2, DOC_3) &&
                        mappings.all { it.userId == USER_A }
            })
        }

    @Test
    fun `bindDocumentsToCurrentUser does nothing for empty list`() =
        coroutineRule.runTest {
            controller.bindDocumentsToCurrentUser(emptyList())

            verify(userDocumentMappingDao, never()).storeAll(any())
            verify(supabaseAuthRepository, never()).getCurrentUserId()
        }

    //endregion

    //region isDocumentOwnedByCurrentUser

    @Test
    fun `isDocumentOwnedByCurrentUser returns true when mapping exists`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(USER_A)
            whenever(userDocumentMappingDao.getMapping(DOC_1, USER_A))
                .thenReturn(UserDocumentMapping(documentId = DOC_1, userId = USER_A))

            val result = controller.isDocumentOwnedByCurrentUser(DOC_1)

            assertTrue(result)
        }

    @Test
    fun `isDocumentOwnedByCurrentUser returns false when no mapping exists`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(USER_A)
            whenever(userDocumentMappingDao.getMapping(DOC_1, USER_A)).thenReturn(null)

            val result = controller.isDocumentOwnedByCurrentUser(DOC_1)

            assertFalse(result)
        }

    @Test
    fun `isDocumentOwnedByCurrentUser returns false when no user session`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(null)

            val result = controller.isDocumentOwnedByCurrentUser(DOC_1)

            assertFalse(result)
        }

    //endregion

    //region getCurrentUserDocumentIds

    @Test
    fun `getCurrentUserDocumentIds returns set of document IDs for current user`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(USER_A)
            whenever(userDocumentMappingDao.getDocumentIdsForUser(USER_A))
                .thenReturn(listOf(DOC_1, DOC_2))

            val result = controller.getCurrentUserDocumentIds()

            assertEquals(setOf(DOC_1, DOC_2), result)
        }

    @Test
    fun `getCurrentUserDocumentIds returns empty set when no user session`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(null)

            val result = controller.getCurrentUserDocumentIds()

            assertTrue(result.isEmpty())
        }

    @Test
    fun `getCurrentUserDocumentIds returns empty set when user has no documents`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(USER_A)
            whenever(userDocumentMappingDao.getDocumentIdsForUser(USER_A))
                .thenReturn(emptyList())

            val result = controller.getCurrentUserDocumentIds()

            assertTrue(result.isEmpty())
        }

    //endregion

    //region unbindDocument

    @Test
    fun `unbindDocument deletes mapping by document ID`() =
        coroutineRule.runTest {
            controller.unbindDocument(DOC_1)

            verify(userDocumentMappingDao).deleteByDocumentId(DOC_1)
        }

    //endregion

    //region unbindAllDocumentsForCurrentUser

    @Test
    fun `unbindAllDocumentsForCurrentUser deletes all mappings for current user`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(USER_A)

            controller.unbindAllDocumentsForCurrentUser()

            verify(userDocumentMappingDao).deleteAllForUser(USER_A)
        }

    @Test(expected = SecurityException::class)
    fun `unbindAllDocumentsForCurrentUser throws when no user session`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(null)

            controller.unbindAllDocumentsForCurrentUser()
        }

    //endregion

    //region migrateOrphanedDocumentsToCurrentUser

    @Test
    fun `migrateOrphanedDocumentsToCurrentUser binds unmapped SDK documents`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(USER_A)

            // SDK has 3 documents, 1 is already mapped
            val doc1 = mockDocument(DOC_1)
            val doc2 = mockDocument(DOC_2)
            val doc3 = mockDocument(DOC_3)
            whenever(eudiWallet.getDocuments()).thenReturn(listOf(doc1, doc2, doc3))
            whenever(userDocumentMappingDao.getAllMappedDocumentIds()).thenReturn(listOf(DOC_1))

            val count = controller.migrateOrphanedDocumentsToCurrentUser()

            assertEquals(2, count)
            verify(userDocumentMappingDao).storeAll(argThat { mappings ->
                mappings.size == 2 &&
                        mappings.map { it.documentId }.toSet() == setOf(DOC_2, DOC_3) &&
                        mappings.all { it.userId == USER_A }
            })
        }

    @Test
    fun `migrateOrphanedDocumentsToCurrentUser returns zero when all documents already mapped`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(USER_A)
            val doc1 = mockDocument(DOC_1)
            whenever(eudiWallet.getDocuments()).thenReturn(listOf(doc1))
            whenever(userDocumentMappingDao.getAllMappedDocumentIds()).thenReturn(listOf(DOC_1))

            val count = controller.migrateOrphanedDocumentsToCurrentUser()

            assertEquals(0, count)
            verify(userDocumentMappingDao, never()).storeAll(any())
        }

    @Test
    fun `migrateOrphanedDocumentsToCurrentUser migrates orphaned Room records`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(USER_A)
            whenever(eudiWallet.getDocuments()).thenReturn(emptyList())
            whenever(userDocumentMappingDao.getAllMappedDocumentIds()).thenReturn(emptyList())

            controller.migrateOrphanedDocumentsToCurrentUser()

            verify(bookmarkDao).migrateOrphanedToUser(USER_A)
            verify(revokedDocumentDao).migrateOrphanedToUser(USER_A)
            verify(transactionLogDao).migrateOrphanedToUser(USER_A)
        }

    @Test
    fun `migrateOrphanedDocumentsToCurrentUser continues if Room migration fails`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(USER_A)
            whenever(eudiWallet.getDocuments()).thenReturn(emptyList())
            whenever(userDocumentMappingDao.getAllMappedDocumentIds()).thenReturn(emptyList())
            whenever(bookmarkDao.migrateOrphanedToUser(USER_A))
                .thenThrow(RuntimeException("DB error"))

            // Should not throw — runCatching swallows the error
            val count = controller.migrateOrphanedDocumentsToCurrentUser()

            assertEquals(0, count)
            // Other DAOs should still be called despite bookmark migration failing
            verify(revokedDocumentDao).migrateOrphanedToUser(USER_A)
            verify(transactionLogDao).migrateOrphanedToUser(USER_A)
        }

    @Test
    fun `migrateOrphanedDocumentsToCurrentUser returns zero when SDK has no documents`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(USER_A)
            whenever(eudiWallet.getDocuments()).thenReturn(emptyList())
            whenever(userDocumentMappingDao.getAllMappedDocumentIds()).thenReturn(emptyList())

            val count = controller.migrateOrphanedDocumentsToCurrentUser()

            assertEquals(0, count)
        }

    @Test(expected = SecurityException::class)
    fun `migrateOrphanedDocumentsToCurrentUser throws when no user session`() =
        coroutineRule.runTest {
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(null)

            controller.migrateOrphanedDocumentsToCurrentUser()
        }

    //endregion

    //region isMigrationCompleted / setMigrationCompleted

    @Test
    fun `isMigrationCompleted returns false by default`() =
        coroutineRule.runTest {
            whenever(prefsControllerV2.getBool("user_document_migration_completed", false))
                .thenReturn(false)

            val result = controller.isMigrationCompleted()

            assertFalse(result)
        }

    @Test
    fun `isMigrationCompleted returns true after migration`() =
        coroutineRule.runTest {
            whenever(prefsControllerV2.getBool("user_document_migration_completed", false))
                .thenReturn(true)

            val result = controller.isMigrationCompleted()

            assertTrue(result)
        }

    @Test
    fun `setMigrationCompleted persists flag`() =
        coroutineRule.runTest {
            controller.setMigrationCompleted()

            verify(prefsControllerV2).setBool("user_document_migration_completed", true)
        }

    //endregion

    //region Multi-user isolation

    @Test
    fun `documents bound by User A are not visible to User B`() =
        coroutineRule.runTest {
            // User A binds a document
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(USER_A)
            whenever(userDocumentMappingDao.getMapping(DOC_1, USER_B)).thenReturn(null)

            // Check from User B's perspective
            whenever(supabaseAuthRepository.getCurrentUserId()).thenReturn(USER_B)
            val result = controller.isDocumentOwnedByCurrentUser(DOC_1)

            assertFalse(result)
        }

    //endregion

    //region Helpers

    private fun mockDocument(id: String): IssuedDocument {
        return mock<IssuedDocument> {
            whenever(it.id).thenReturn(id)
        }
    }

    //endregion

    companion object {
        private const val USER_A = "user-a-uuid-1234"
        private const val USER_B = "user-b-uuid-5678"
        private const val DOC_1 = "doc-id-001"
        private const val DOC_2 = "doc-id-002"
        private const val DOC_3 = "doc-id-003"
    }
}
