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

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.config.ReIssuanceRule
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.controller.IssueDocumentsPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.util.CoreActions
import eu.europa.ec.corelogic.util.CoreActions.RE_ISSUANCE_IDS_DETAILS_EXTRA
import eu.europa.ec.eudi.wallet.document.CreateDocumentSettings.CredentialPolicy
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.Instant

class ReIssuanceWorkManager(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val walletCoreDocumentsController: WalletCoreDocumentsController by inject()
    private val walletCoreConfig: WalletCoreConfig by inject()
    private val logController: LogController by inject()

    companion object {
        private const val TAG = "ReIssuanceWorker"
        const val RE_ISSUANCE_WORK_NAME = "reIssuanceWorker"

        internal suspend fun shouldReIssueDocument(
            document: IssuedDocument,
            rule: ReIssuanceRule,
            now: Instant
        ): Boolean {
            val expiresBefore: Instant = now.plus(Duration.ofHours(rule.minExpirationHours.toLong()))
            val hasLowOneTimeUseCount: Boolean =
                document.credentialPolicy == CredentialPolicy.OneTimeUse &&
                        document.credentialsCount() <= rule.minNumberOfCredentials
            val expiresSoon: Boolean = document.getValidUntil()
                .map { validUntil -> validUntil.isBefore(expiresBefore) }
                .getOrDefault(false)
            return hasLowOneTimeUseCount || expiresSoon
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val failedIds = mutableListOf<String>()
            val replacedIds = mutableListOf<String>()
            val rule: ReIssuanceRule = walletCoreConfig.documentIssuanceConfig.reissuanceRule
            val now: Instant = Instant.now()
            walletCoreDocumentsController.getAllIssuedDocuments()
                .filter { shouldReIssueDocument(it, rule, now) }
                .forEach { document ->
                    val state: IssueDocumentsPartialState = walletCoreDocumentsController.reIssueDocument(
                        documentId = document.id,
                        issuerId = document.issuerMetadata?.credentialIssuerIdentifier.orEmpty(),
                        allowAuthorizationFallback = false
                    ).first()
                    when (state) {
                        is IssueDocumentsPartialState.DeferredSuccess,
                        is IssueDocumentsPartialState.PartialSuccess,
                        is IssueDocumentsPartialState.Success -> replacedIds.add(document.id)

                        is IssueDocumentsPartialState.UserAuthRequired -> {
                            state.resultHandler.onAuthenticationFailure()
                            failedIds.add(document.id)
                        }

                        is IssueDocumentsPartialState.Failure,
                        IssueDocumentsPartialState.UserAuthCancelled -> failedIds.add(document.id)
                    }
                }
            walletCoreDocumentsController.replaceFailedReIssuedDocumentIds(failedIds)
            if (replacedIds.isNotEmpty()) {
                notifyDocumentsList()
                notifyDocumentDetails(replacedIds)
            }
            Result.success()
        } catch (e: Exception) {
            logController.e(TAG) { "Reissuance worker failed: ${e::class.java.simpleName}: ${e.message}" }
            Result.failure()
        }
    }

    private fun notifyDocumentDetails(replacedIds: List<String>) {
        val detailsIntent = Intent(CoreActions.RE_ISSUANCE_WORK_REFRESH_DETAILS_ACTION).apply {
            setPackage(applicationContext.packageName)
            putStringArrayListExtra(RE_ISSUANCE_IDS_DETAILS_EXTRA, ArrayList(replacedIds))
        }
        applicationContext.sendBroadcast(detailsIntent)
    }

    private fun notifyDocumentsList() {
        val refreshIntent = Intent(CoreActions.RE_ISSUANCE_WORK_REFRESH_ACTION).apply {
            setPackage(applicationContext.packageName)
        }
        applicationContext.sendBroadcast(refreshIntent)
    }
}
