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

package eu.europa.ec.corelogic.worker

import eu.europa.ec.corelogic.config.ReIssuanceRule
import eu.europa.ec.eudi.wallet.document.CreateDocumentSettings.CredentialPolicy
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant

class TestReIssuanceWorkManager {

    @Test
    fun `Given one time use document has threshold credentials, When checked, Then it is selected`() = runBlocking {
        val document: IssuedDocument = document(
            credentialsCount = 2,
            credentialPolicy = CredentialPolicy.OneTimeUse,
            validUntil = NOW.plus(Duration.ofDays(30))
        )

        val result: Boolean = ReIssuanceWorkManager.shouldReIssueDocument(document, RULE, NOW)

        assertTrue(result)
    }

    @Test
    fun `Given rotate use document has threshold credentials, When checked, Then it is not selected`() = runBlocking {
        val document: IssuedDocument = document(
            credentialsCount = 2,
            credentialPolicy = CredentialPolicy.RotateUse,
            validUntil = NOW.plus(Duration.ofDays(30))
        )

        val result: Boolean = ReIssuanceWorkManager.shouldReIssueDocument(document, RULE, NOW)

        assertFalse(result)
    }

    @Test
    fun `Given document expires within threshold, When checked, Then it is selected`() = runBlocking {
        val document: IssuedDocument = document(
            credentialsCount = 30,
            credentialPolicy = CredentialPolicy.RotateUse,
            validUntil = NOW.plus(Duration.ofHours(23))
        )

        val result: Boolean = ReIssuanceWorkManager.shouldReIssueDocument(document, RULE, NOW)

        assertTrue(result)
    }

    @Test
    fun `Given new failed ids, When changed failed reissuance ids are requested, Then new ids are returned`() {
        val result: List<String> = ReIssuanceWorkManager.getChangedFailedReIssuanceIds(
            previousFailedIds = setOf("doc-1"),
            failedIds = listOf("doc-1", "doc-2")
        )

        assertEquals(listOf("doc-2"), result)
    }

    @Test
    fun `Given failed ids are cleared, When changed failed reissuance ids are requested, Then cleared ids are returned`() {
        val result: List<String> = ReIssuanceWorkManager.getChangedFailedReIssuanceIds(
            previousFailedIds = setOf("doc-1", "doc-2"),
            failedIds = listOf("doc-2")
        )

        assertEquals(listOf("doc-1"), result)
    }

    @Test
    fun `Given failed ids are unchanged, When changed failed reissuance ids are requested, Then no ids are returned`() {
        val result: List<String> = ReIssuanceWorkManager.getChangedFailedReIssuanceIds(
            previousFailedIds = setOf("doc-1", "doc-2"),
            failedIds = listOf("doc-2", "doc-1")
        )

        assertEquals(emptyList<String>(), result)
    }

    private fun document(
        credentialsCount: Int,
        credentialPolicy: CredentialPolicy,
        validUntil: Instant
    ): IssuedDocument = runBlocking {
        mock<IssuedDocument> {
            whenever(it.credentialsCount()).thenReturn(credentialsCount)
            whenever(it.credentialPolicy).thenReturn(credentialPolicy)
            whenever(it.getValidUntil()).thenReturn(Result.success(validUntil))
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-22T00:00:00Z")
        val RULE: ReIssuanceRule = ReIssuanceRule(
            minNumberOfCredentials = 2,
            minExpirationHours = 24,
            backgroundInterval = Duration.ofMinutes(15)
        )
    }
}
