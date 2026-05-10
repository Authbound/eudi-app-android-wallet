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
import eu.europa.ec.corelogic.config.AuthboundWalletProviderConfig
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.extension.localizedIssuerMetadata
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException

internal const val AUTHBOUND_PID_FORMAT_TYPE: String = "urn:vc:authbound:pid:1.0"
internal const val AUTHBOUND_PID_HOME_PROMPT_SNOOZE_UNTIL_KEY: String =
    "authbound_pid_home_prompt_snooze_until"
internal const val AUTHBOUND_PID_HOME_PROMPT_SNOOZE_DAYS: Long = 30L

data class AuthboundPidEntryState(
    val shouldShowEntry: Boolean,
    val shouldShowHomePrompt: Boolean,
)

interface AuthboundPidEntryInteractor {
    suspend fun getEntryState(): AuthboundPidEntryState
    suspend fun snoozeHomePrompt()
}

class AuthboundPidEntryInteractorImpl(
    private val resourceProvider: ResourceProvider,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val walletCoreConfig: WalletCoreConfig,
    private val prefsController: PrefsControllerV2,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : AuthboundPidEntryInteractor {

    override suspend fun getEntryState(): AuthboundPidEntryState {
        val snoozeUntilEpochMillis: Long = getSnoozeUntilEpochMillis()
        val hasAuthboundPid: Boolean = getHasAuthboundPid()
        return resolveAuthboundPidEntryState(
            hasAuthboundPid = hasAuthboundPid,
            snoozeUntilEpochMillis = snoozeUntilEpochMillis,
            nowEpochMillis = currentTimeMillis()
        )
    }

    override suspend fun snoozeHomePrompt() {
        val snoozeUntilEpochMillis: Long = currentTimeMillis() +
            Duration.ofDays(AUTHBOUND_PID_HOME_PROMPT_SNOOZE_DAYS).toMillis()
        try {
            prefsController.setLong(
                key = AUTHBOUND_PID_HOME_PROMPT_SNOOZE_UNTIL_KEY,
                value = snoozeUntilEpochMillis
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
    }

    private fun getSnoozeUntilEpochMillis(): Long {
        return try {
            prefsController.safeLong(
                key = AUTHBOUND_PID_HOME_PROMPT_SNOOZE_UNTIL_KEY,
                defaultValue = 0L
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            0L
        }
    }

    private suspend fun getHasAuthboundPid(): Boolean {
        return try {
            hasAuthboundPidCredential()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun hasAuthboundPidCredential(): Boolean {
        val userLocale = resourceProvider.getLocale()
        val authboundCredentialIssuerIdentifiers: Set<String> = walletCoreConfig.issuersConfig
            .filter { it.walletProviderConfig is AuthboundWalletProviderConfig }
            .map { it.config.issuerUrl }
            .toSet()
        return walletCoreDocumentsController.getAllIssuedDocuments().any { document ->
            val issuerMetadata = document.issuerMetadata
            isAuthboundPidCredential(
                documentIdentifier = document.toDocumentIdentifier(),
                issuerName = document.localizedIssuerMetadata(userLocale)?.name,
                credentialIssuerIdentifier = issuerMetadata?.credentialIssuerIdentifier,
                authboundCredentialIssuerIdentifiers = authboundCredentialIssuerIdentifiers
            )
        }
    }
}

internal fun isAuthboundPidCredential(
    documentIdentifier: DocumentIdentifier,
    issuerName: String?,
    credentialIssuerIdentifier: String? = null,
    authboundCredentialIssuerIdentifiers: Set<String> = emptySet(),
): Boolean {
    val isPidLike: Boolean = documentIdentifier == DocumentIdentifier.MdocPid
        || documentIdentifier == DocumentIdentifier.SdJwtPid
        || (documentIdentifier is DocumentIdentifier.OTHER
        && documentIdentifier.formatType.equals(AUTHBOUND_PID_FORMAT_TYPE, ignoreCase = true))
    val isAuthboundIssued: Boolean = isAuthboundCredentialIssuerIdentifier(
        credentialIssuerIdentifier = credentialIssuerIdentifier,
        authboundCredentialIssuerIdentifiers = authboundCredentialIssuerIdentifiers
    ) || hasLegacyAuthboundPidMarker(
        documentIdentifier = documentIdentifier,
        issuerName = issuerName,
        credentialIssuerIdentifier = credentialIssuerIdentifier
    )
    return isPidLike && isAuthboundIssued
}

private fun hasLegacyAuthboundPidMarker(
    documentIdentifier: DocumentIdentifier,
    issuerName: String?,
    credentialIssuerIdentifier: String?,
): Boolean {
    return documentIdentifier is DocumentIdentifier.OTHER
        && documentIdentifier.formatType.equals(AUTHBOUND_PID_FORMAT_TYPE, ignoreCase = true)
        && credentialIssuerIdentifier.isNullOrBlank()
        && issuerName?.contains("authbound", ignoreCase = true) == true
}

private fun isAuthboundCredentialIssuerIdentifier(
    credentialIssuerIdentifier: String?,
    authboundCredentialIssuerIdentifiers: Set<String>,
): Boolean {
    val normalizedCredentialIssuerIdentifier: String =
        credentialIssuerIdentifier.normalizedIssuerIdentifier() ?: return false
    val normalizedAuthboundCredentialIssuerIdentifiers: Set<String> =
        authboundCredentialIssuerIdentifiers.mapNotNull { it.normalizedIssuerIdentifier() }.toSet()
    return normalizedCredentialIssuerIdentifier in normalizedAuthboundCredentialIssuerIdentifiers
}

private fun String?.normalizedIssuerIdentifier(): String? {
    return this
        ?.trim()
        ?.trimEnd('/')
        ?.lowercase()
        ?.takeIf { it.isNotBlank() }
}

internal fun resolveAuthboundPidEntryState(
    hasAuthboundPid: Boolean,
    snoozeUntilEpochMillis: Long,
    nowEpochMillis: Long,
): AuthboundPidEntryState {
    val shouldShowEntry: Boolean = !hasAuthboundPid
    val isHomePromptSnoozed: Boolean = snoozeUntilEpochMillis > nowEpochMillis
    return AuthboundPidEntryState(
        shouldShowEntry = shouldShowEntry,
        shouldShowHomePrompt = shouldShowEntry && !isHomePromptSnoozed
    )
}
