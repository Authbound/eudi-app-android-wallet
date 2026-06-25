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
import eu.europa.ec.businesslogic.controller.wallet.UserDocumentOwnershipController
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.provider.IssuerOpenId4VciManagerFactory
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.storagelogic.dao.BookmarkDao
import eu.europa.ec.storagelogic.dao.FailedReIssuedDocumentDao
import eu.europa.ec.storagelogic.dao.RevokedDocumentDao
import eu.europa.ec.storagelogic.dao.TransactionLogDao
import eu.europa.ec.storagelogic.model.Bookmark
import eu.europa.ec.storagelogic.model.FailedReIssuedDocument
import eu.europa.ec.storagelogic.model.RevokedDocument
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests the user-scoping behavior of WalletCoreDocumentsControllerImpl.
 *
 * Focuses on verifying that document access, bookmarks, revoked documents,
 * and transaction logs are properly scoped to the current user.
 */
class TestWalletCoreDocumentsControllerUserScoping {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var eudiWallet: EudiWallet

    @Mock
    private lateinit var walletCoreConfig: WalletCoreConfig

    @Mock
    private lateinit var bookmarkDao: BookmarkDao

    @Mock
    private lateinit var issuerOpenId4VciManagerFactory: IssuerOpenId4VciManagerFactory

    @Mock
    private lateinit var transactionLogDao: TransactionLogDao

    @Mock
    private lateinit var revokedDocumentDao: RevokedDocumentDao

    @Mock
    private lateinit var failedReIssuedDocumentDao: FailedReIssuedDocumentDao

    @Mock
    private lateinit var ownershipController: UserDocumentOwnershipController

    @Mock
    private lateinit var logController: LogController

    private lateinit var controller: WalletCoreDocumentsControllerImpl
    private lateinit var closeable: AutoCloseable

    @Before
    fun setup() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.genericErrorMessage()).thenReturn("Generic error")

        controller = WalletCoreDocumentsControllerImpl(
            resourceProvider = resourceProvider,
            eudiWallet = eudiWallet,
            walletCoreConfig = walletCoreConfig,
            issuerOpenId4VciManagerFactory = issuerOpenId4VciManagerFactory,
            bookmarkDao = bookmarkDao,
            transactionLogDao = transactionLogDao,
            revokedDocumentDao = revokedDocumentDao,
            failedReIssuedDocumentDao = failedReIssuedDocumentDao,
            ownershipController = ownershipController,
            logController = logController,
            dispatcher = Dispatchers.Unconfined
        )
    }

    //region getAllDocuments - ownership filtering

    @Test
    fun `getAllDocuments returns only documents owned by current user`() =
        coroutineRule.runTest {
            // Given: SDK has 3 docs, user owns 2
            val doc1 = mockIssuedDocument("doc-1")
            val doc2 = mockIssuedDocument("doc-2")
            val doc3 = mockIssuedDocument("doc-3")
            whenever(eudiWallet.getDocuments(any())).thenReturn(listOf(doc1, doc2, doc3))
            whenever(ownershipController.getCurrentUserDocumentIds())
                .thenReturn(setOf("doc-1", "doc-3"))

            val result = controller.getAllDocuments()

            assertEquals(2, result.size)
            assertTrue(result.any { it.id == "doc-1" })
            assertTrue(result.any { it.id == "doc-3" })
            assertFalse(result.any { it.id == "doc-2" })
        }

    @Test
    fun `getAllDocuments returns empty when user owns no documents`() =
        coroutineRule.runTest {
            val doc1 = mockIssuedDocument("doc-1")
            whenever(eudiWallet.getDocuments(any())).thenReturn(listOf(doc1))
            whenever(ownershipController.getCurrentUserDocumentIds()).thenReturn(emptySet())

            val result = controller.getAllDocuments()

            assertTrue(result.isEmpty())
        }

    //endregion

    //region getDocumentById - ownership check

    @Test
    fun `getDocumentById returns document when owned by current user`() =
        coroutineRule.runTest {
            val doc = mockIssuedDocument("doc-1")
            whenever(ownershipController.isDocumentOwnedByCurrentUser("doc-1")).thenReturn(true)
            whenever(eudiWallet.getDocumentById("doc-1")).thenReturn(doc)

            val result = controller.getDocumentById("doc-1")

            assertEquals("doc-1", result?.id)
        }

    @Test
    fun `getDocumentById returns null when not owned by current user`() =
        coroutineRule.runTest {
            whenever(ownershipController.isDocumentOwnedByCurrentUser("doc-1")).thenReturn(false)

            val result = controller.getDocumentById("doc-1")

            assertNull(result)
        }

    //endregion

    //region Bookmarks - user-scoped

    @Test
    fun `isDocumentBookmarked queries with userId`() =
        coroutineRule.runTest {
            whenever(ownershipController.requireCurrentUserId()).thenReturn(USER_A)
            whenever(bookmarkDao.retrieve("doc-1", USER_A))
                .thenReturn(Bookmark("doc-1", USER_A))

            val result = controller.isDocumentBookmarked("doc-1")

            assertTrue(result)
            verify(bookmarkDao).retrieve("doc-1", USER_A)
        }

    @Test
    fun `isDocumentBookmarked returns false for other users bookmark`() =
        coroutineRule.runTest {
            whenever(ownershipController.requireCurrentUserId()).thenReturn(USER_A)
            whenever(bookmarkDao.retrieve("doc-1", USER_A)).thenReturn(null)

            val result = controller.isDocumentBookmarked("doc-1")

            assertFalse(result)
        }

    @Test
    fun `storeBookmark includes userId`() =
        coroutineRule.runTest {
            whenever(ownershipController.requireCurrentUserId()).thenReturn(USER_A)

            controller.storeBookmark("doc-1")

            verify(bookmarkDao).store(argThat { bookmark ->
                bookmark.identifier == "doc-1" && bookmark.userId == USER_A
            })
        }

    @Test
    fun `deleteBookmark scopes by userId`() =
        coroutineRule.runTest {
            whenever(ownershipController.requireCurrentUserId()).thenReturn(USER_A)

            controller.deleteBookmark("doc-1")

            verify(bookmarkDao).delete("doc-1", USER_A)
        }

    //endregion

    //region Revoked documents - user-scoped

    @Test
    fun `getRevokedDocumentIds returns only current users revoked docs`() =
        coroutineRule.runTest {
            whenever(ownershipController.getCurrentUserId()).thenReturn(USER_A)
            whenever(revokedDocumentDao.retrieveAllForUser(USER_A))
                .thenReturn(listOf(RevokedDocument("doc-1", USER_A)))

            val result = controller.getRevokedDocumentIds()

            assertEquals(listOf("doc-1"), result)
        }

    @Test
    fun `isDocumentRevoked queries with userId`() =
        coroutineRule.runTest {
            whenever(ownershipController.getCurrentUserId()).thenReturn(USER_A)
            whenever(revokedDocumentDao.retrieve("doc-1", USER_A))
                .thenReturn(RevokedDocument("doc-1", USER_A))

            val result = controller.isDocumentRevoked("doc-1")

            assertTrue(result)
        }

    @Test
    fun `storeRevokedDocuments includes userId`() =
        coroutineRule.runTest {
            whenever(ownershipController.requireCurrentUserId()).thenReturn(USER_A)
            val doc = mockIssuedDocument("doc-1")

            controller.storeRevokedDocuments(listOf(doc))

            verify(revokedDocumentDao).storeAll(argThat { docs ->
                docs.size == 1 && docs[0].identifier == "doc-1" && docs[0].userId == USER_A
            })
        }

    @Test
    fun `removeRevokedDocumentsFromStorage scopes by userId`() =
        coroutineRule.runTest {
            whenever(ownershipController.requireCurrentUserId()).thenReturn(USER_A)

            controller.removeRevokedDocumentsFromStorage(listOf("doc-1", "doc-2"))

            verify(revokedDocumentDao).delete("doc-1", USER_A)
            verify(revokedDocumentDao).delete("doc-2", USER_A)
        }

    //endregion

    //region Failed reissued documents - user-scoped

    @Test
    fun `replaceFailedReIssuedDocumentIds scopes by userId`() =
        coroutineRule.runTest {
            whenever(ownershipController.requireCurrentUserId()).thenReturn(USER_A)

            controller.replaceFailedReIssuedDocumentIds(listOf("doc-1", "doc-2", "doc-1"))

            verify(failedReIssuedDocumentDao).deleteAllForUser(USER_A)
            verify(failedReIssuedDocumentDao).storeAll(argThat { docs ->
                docs == listOf(
                    FailedReIssuedDocument("doc-1", USER_A),
                    FailedReIssuedDocument("doc-2", USER_A)
                )
            })
        }

    @Test
    fun `getFailedReIssuedDocumentIds returns only current users failed docs`() =
        coroutineRule.runTest {
            whenever(ownershipController.getCurrentUserId()).thenReturn(USER_A)
            whenever(failedReIssuedDocumentDao.retrieveAllForUser(USER_A))
                .thenReturn(listOf(FailedReIssuedDocument("doc-1", USER_A)))

            val result = controller.getFailedReIssuedDocumentIds()

            assertEquals(listOf("doc-1"), result)
        }

    //endregion

    //region Transaction logs - user-scoped

    @Test
    fun `getTransactionLogs queries with userId`() =
        coroutineRule.runTest {
            whenever(ownershipController.getCurrentUserId()).thenReturn(USER_A)
            whenever(transactionLogDao.retrieveAllForUser(USER_A)).thenReturn(emptyList())

            controller.getTransactionLogs()

            verify(transactionLogDao).retrieveAllForUser(USER_A)
        }

    @Test
    fun `getTransactionLogs uses empty userId when no session`() =
        coroutineRule.runTest {
            whenever(ownershipController.getCurrentUserId()).thenReturn(null)
            whenever(transactionLogDao.retrieveAllForUser("")).thenReturn(emptyList())

            controller.getTransactionLogs()

            verify(transactionLogDao).retrieveAllForUser("")
        }

    //endregion

    //region Helpers

    private fun mockIssuedDocument(id: String): IssuedDocument {
        return mock<IssuedDocument> {
            whenever(it.id).thenReturn(id)
        }
    }

    //endregion

    companion object {
        private const val USER_A = "user-a-uuid-1234"
    }
}
