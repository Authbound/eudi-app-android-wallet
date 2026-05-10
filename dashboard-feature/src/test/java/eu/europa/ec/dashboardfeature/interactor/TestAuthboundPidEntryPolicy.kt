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

import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

class TestAuthboundPidEntryPolicy {

    @Test
    fun `Given Authbound custom PID format from Authbound issuer, When checking credential, Then it is Authbound PID`() {
        val result: Boolean = isAuthboundPidCredential(
            documentIdentifier = DocumentIdentifier.OTHER("urn:vc:authbound:pid:1.0"),
            issuerName = "Authbound"
        )

        assertTrue(result)
    }

    @Test
    fun `Given standard PID from non Authbound issuer, When checking credential, Then it is not Authbound PID`() {
        val result: Boolean = isAuthboundPidCredential(
            documentIdentifier = DocumentIdentifier.MdocPid,
            issuerName = "Government issuer"
        )

        assertFalse(result)
    }

    @Test
    fun `Given standard PID from configured Authbound issuer, When checking credential, Then it is Authbound PID`() {
        val result: Boolean = isAuthboundPidCredential(
            documentIdentifier = DocumentIdentifier.MdocPid,
            issuerName = "Identity provider",
            credentialIssuerIdentifier = "https://issuer.example.com/api/v1/openid4vci",
            authboundCredentialIssuerIdentifiers = setOf("https://issuer.example.com/api/v1/openid4vci")
        )

        assertTrue(result)
    }

    @Test
    fun `Given standard PID from Authbound issuer source, When display name is missing, Then it is Authbound PID`() {
        val result: Boolean = isAuthboundPidCredential(
            documentIdentifier = DocumentIdentifier.SdJwtPid,
            issuerName = null,
            credentialIssuerIdentifier = "https://issuer.authbound.io/api/v1/openid4vci",
            authboundCredentialIssuerIdentifiers = setOf("https://issuer.authbound.io/api/v1/openid4vci")
        )

        assertTrue(result)
    }

    @Test
    fun `Given standard PID from unconfigured issuer containing Authbound, When checking credential, Then it is not Authbound PID`() {
        val result: Boolean = isAuthboundPidCredential(
            documentIdentifier = DocumentIdentifier.MdocPid,
            issuerName = null,
            credentialIssuerIdentifier = "https://not-authbound.example",
            authboundCredentialIssuerIdentifiers = setOf("https://issuer.authbound.io/api/v1/openid4vci")
        )

        assertFalse(result)
    }

    @Test
    fun `Given standard PID from parent issuer path, When configured Authbound issuer is a child path, Then it is not Authbound PID`() {
        val result: Boolean = isAuthboundPidCredential(
            documentIdentifier = DocumentIdentifier.MdocPid,
            issuerName = null,
            credentialIssuerIdentifier = "https://issuer.example.com",
            authboundCredentialIssuerIdentifiers = setOf("https://issuer.example.com/api/v1/openid4vci")
        )

        assertFalse(result)
    }

    @Test
    fun `Given PID from Authbound display name with mismatched issuer source, When checking credential, Then it is not Authbound PID`() {
        val result: Boolean = isAuthboundPidCredential(
            documentIdentifier = DocumentIdentifier.MdocPid,
            issuerName = "Authbound issuer",
            credentialIssuerIdentifier = "https://government.example.com/openid4vci",
            authboundCredentialIssuerIdentifiers = setOf("https://issuer.authbound.io/api/v1/openid4vci")
        )

        assertFalse(result)
    }

    @Test
    fun `Given unrelated Authbound credential, When checking credential, Then it is not Authbound PID`() {
        val result: Boolean = isAuthboundPidCredential(
            documentIdentifier = DocumentIdentifier.OTHER("urn:iso:std:iso:18013:5:1:mDL"),
            issuerName = "Authbound"
        )

        assertFalse(result)
    }

    @Test
    fun `Given future Home prompt snooze, When resolving entry state, Then stable entry stays visible`() {
        val result: AuthboundPidEntryState = resolveAuthboundPidEntryState(
            hasAuthboundPid = false,
            snoozeUntilEpochMillis = 2_000L,
            nowEpochMillis = 1_000L
        )

        assertTrue(result.shouldShowEntry)
        assertFalse(result.shouldShowHomePrompt)
    }

    @Test
    fun `Given Authbound PID exists, When resolving entry state, Then every entry point is hidden`() {
        val result: AuthboundPidEntryState = resolveAuthboundPidEntryState(
            hasAuthboundPid = true,
            snoozeUntilEpochMillis = 0L,
            nowEpochMillis = 1_000L
        )

        assertFalse(result.shouldShowEntry)
        assertFalse(result.shouldShowHomePrompt)
    }

    @Test
    fun `Given document lookup fails, When getting entry state, Then Authbound entry remains available`() = runTest {
        val prefsController: PrefsControllerV2 = mock()
        val documentsController: WalletCoreDocumentsController = mock()
        val interactor = createInteractor(
            prefsController = prefsController,
            documentsController = documentsController
        )
        whenever(prefsController.safeLong(any(), any())).thenReturn(0L)
        whenever(documentsController.getAllIssuedDocuments()).thenThrow(IllegalStateException("storage"))

        val result: AuthboundPidEntryState = interactor.getEntryState()

        assertEquals(AuthboundPidEntryState(shouldShowEntry = true, shouldShowHomePrompt = true), result)
    }

    @Test
    fun `Given preference read fails, When getting entry state, Then Authbound entry remains available`() = runTest {
        val prefsController: PrefsControllerV2 = mock()
        val documentsController: WalletCoreDocumentsController = mock()
        val interactor = createInteractor(
            prefsController = prefsController,
            documentsController = documentsController
        )
        whenever(prefsController.safeLong(any(), any())).thenThrow(IllegalStateException("prefs"))
        whenever(documentsController.getAllIssuedDocuments()).thenReturn(emptyList())

        val result: AuthboundPidEntryState = interactor.getEntryState()

        assertEquals(AuthboundPidEntryState(shouldShowEntry = true, shouldShowHomePrompt = true), result)
    }

    @Test
    fun `Given preference read is cancelled, When getting entry state, Then cancellation propagates`() =
        runTest {
            val prefsController: PrefsControllerV2 = mock()
            val interactor = createInteractor(prefsController = prefsController)
            whenever(prefsController.safeLong(any(), any()))
                .thenThrow(CancellationException("cancelled"))

            try {
                interactor.getEntryState()
                fail("Expected CancellationException")
            } catch (e: CancellationException) {
                assertEquals("cancelled", e.message)
            }
        }

    @Test
    fun `Given preference write fails, When snoozing Home prompt, Then no exception is thrown`() = runTest {
        val prefsController: PrefsControllerV2 = mock()
        val interactor = createInteractor(prefsController = prefsController)
        whenever(prefsController.setLong(any(), any())).thenThrow(IllegalStateException("prefs"))

        interactor.snoozeHomePrompt()

        assertTrue(true)
    }

    @Test
    fun `Given document lookup is cancelled, When getting entry state, Then cancellation propagates`() =
        runTest {
            val prefsController: PrefsControllerV2 = mock()
            val documentsController: WalletCoreDocumentsController = mock()
            val interactor = createInteractor(
                prefsController = prefsController,
                documentsController = documentsController
            )
            whenever(prefsController.safeLong(any(), any())).thenReturn(0L)
            whenever(documentsController.getAllIssuedDocuments())
                .thenThrow(CancellationException("cancelled"))

            try {
                interactor.getEntryState()
                fail("Expected CancellationException")
            } catch (e: CancellationException) {
                assertEquals("cancelled", e.message)
            }
        }

    @Test
    fun `Given preference write is cancelled, When snoozing Home prompt, Then cancellation propagates`() =
        runTest {
            val prefsController: PrefsControllerV2 = mock()
            val interactor = createInteractor(prefsController = prefsController)
            whenever(prefsController.setLong(any(), any()))
                .thenThrow(CancellationException("cancelled"))

            try {
                interactor.snoozeHomePrompt()
                fail("Expected CancellationException")
            } catch (e: CancellationException) {
                assertEquals("cancelled", e.message)
            }
        }

    private fun createInteractor(
        prefsController: PrefsControllerV2 = mock(),
        documentsController: WalletCoreDocumentsController = mock(),
    ): AuthboundPidEntryInteractor {
        val resourceProvider: ResourceProvider = mock()
        val walletCoreConfig: WalletCoreConfig = mock()
        whenever(resourceProvider.getLocale()).thenReturn(Locale.ENGLISH)
        whenever(walletCoreConfig.issuersConfig).thenReturn(emptyList())
        return AuthboundPidEntryInteractorImpl(
            resourceProvider = resourceProvider,
            walletCoreDocumentsController = documentsController,
            walletCoreConfig = walletCoreConfig,
            prefsController = prefsController,
            currentTimeMillis = { 1_000L }
        )
    }
}
