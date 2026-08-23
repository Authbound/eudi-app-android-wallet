/*
 * Copyright (c) 2026 European Commission
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

import androidx.biometric.BiometricPrompt
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mock
import org.multipaz.securearea.KeyUnlockData

class TestWalletCorePresentationControllerAuthentication {

    @Test
    fun `multiple disclosed documents require one authentication and all receive their own unlock data`() {
        val firstDocument = disclosedDocument("document-1")
        val secondDocument = disclosedDocument("document-2")
        val disclosedDocuments = mutableListOf(firstDocument, secondDocument)
        val firstUnlockData = mock(KeyUnlockData::class.java)
        val secondUnlockData = mock(KeyUnlockData::class.java)

        val authenticationData = presentationAuthenticationData(
            disclosedDocuments = disclosedDocuments,
            documentUnlockData = listOf(
                PresentationDocumentUnlockData(firstDocument, firstUnlockData, null),
                PresentationDocumentUnlockData(secondDocument, secondUnlockData, null),
            ),
        )

        assertNull(authenticationData.crypto.cryptoObject)
        authenticationData.onAuthenticationSuccess()
        assertEquals(2, disclosedDocuments.size)
        assertSame(firstUnlockData, disclosedDocuments[0].keyUnlockData)
        assertSame(secondUnlockData, disclosedDocuments[1].keyUnlockData)
    }

    @Test
    fun `single disclosed document still requires one authentication`() {
        val document = disclosedDocument("document-1")
        val disclosedDocuments = mutableListOf(document)
        val unlockData = mock(KeyUnlockData::class.java)

        val authenticationData = presentationAuthenticationData(
            disclosedDocuments = disclosedDocuments,
            documentUnlockData = listOf(
                PresentationDocumentUnlockData(document, unlockData, null),
            ),
        )

        assertNull(authenticationData.crypto.cryptoObject)
        authenticationData.onAuthenticationSuccess()
        assertSame(unlockData, disclosedDocuments.single().keyUnlockData)
    }

    @Test
    fun `auth per operation key fails closed without changing disclosed documents`() {
        val document = disclosedDocument("document-1")
        val disclosedDocuments = mutableListOf(document)
        val unlockData = mock(KeyUnlockData::class.java)
        val cryptoObject = mock(BiometricPrompt.CryptoObject::class.java)

        assertThrows(IllegalStateException::class.java) {
            presentationAuthenticationData(
                disclosedDocuments = disclosedDocuments,
                documentUnlockData = listOf(
                    PresentationDocumentUnlockData(document, unlockData, cryptoObject),
                ),
            )
        }
        assertNull(disclosedDocuments.single().keyUnlockData)
    }

    private fun disclosedDocument(documentId: String) = DisclosedDocument(
        documentId = documentId,
        disclosedItems = emptyList(),
    )
}
