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
import eu.europa.ec.corelogic.util.CoreActions.RE_ISSUANCE_FAILURE_STATUS_IDS_DETAILS_EXTRA
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

    internal enum class ReIssuanceOutcome {
        Replaced,
        Pending,
        Failed,
        UserAuthRequired
    }

    override suspend fun doWork(): Result {
        return try {
            val failedIds = mutableListOf<String>()
            val replacedIds = mutableListOf<String>()
            val pendingIds = mutableListOf<String>()
            val rule: ReIssuanceRule = walletCoreConfig.documentIssuanceConfig.reissuanceRule
            val now: Instant = Instant.now()
            val previousFailedIds: Set<String> =
                walletCoreDocumentsController.getFailedReIssuedDocumentIds().toSet()
            val documentsToReIssue: List<IssuedDocument> = walletCoreDocumentsController
                .getAllIssuedDocuments()
                .filter { shouldReIssueDocument(it, rule, now) }
            if (documentsToReIssue.isEmpty() && previousFailedIds.isEmpty()) {
                return Result.success()
            }
            documentsToReIssue.forEach { document ->
                val state: IssueDocumentsPartialState = walletCoreDocumentsController.reIssueDocument(
                    documentId = document.id,
                    issuerId = document.issuerMetadata?.credentialIssuerIdentifier.orEmpty(),
                    allowAuthorizationFallback = false
                ).first()
                when (classifyReIssuanceState(state)) {
                    ReIssuanceOutcome.Replaced -> replacedIds.add(document.id)
                    ReIssuanceOutcome.Pending -> pendingIds.add(document.id)
                    ReIssuanceOutcome.Failed -> failedIds.add(document.id)
                    ReIssuanceOutcome.UserAuthRequired -> {
                        val userAuthRequiredState: IssueDocumentsPartialState.UserAuthRequired =
                            state as IssueDocumentsPartialState.UserAuthRequired
                        userAuthRequiredState.resultHandler.onAuthenticationFailure()
                        failedIds.add(document.id)
                    }
                }
            }
            val failedStatusChangedIds: List<String> =
                getChangedFailedReIssuanceIds(previousFailedIds, failedIds)
            val failedStatusDetailIds: List<String> =
                getFailedReIssuanceDetailRefreshIds(failedStatusChangedIds, replacedIds)
            walletCoreDocumentsController.replaceFailedReIssuedDocumentIds(failedIds)
            if (
                replacedIds.isNotEmpty() ||
                pendingIds.isNotEmpty() ||
                failedStatusChangedIds.isNotEmpty()
            ) {
                notifyDocumentsList()
            }
            if (replacedIds.isNotEmpty()) {
                notifyDocumentDetails(replacedIds)
            }
            if (failedStatusDetailIds.isNotEmpty()) {
                notifyFailedDocumentDetails(failedStatusDetailIds)
            }
            Result.success()
        } catch (e: Exception) {
            logController.e(TAG) { "Reissuance worker failed: ${e::class.java.simpleName}: ${e.message}" }
            Result.failure()
        }
    }

    private fun notifyFailedDocumentDetails(failedStatusChangedIds: List<String>) {
        val detailsIntent = Intent(CoreActions.RE_ISSUANCE_WORK_REFRESH_FAILURE_STATUS_DETAILS_ACTION).apply {
            setPackage(applicationContext.packageName)
            putStringArrayListExtra(
                RE_ISSUANCE_FAILURE_STATUS_IDS_DETAILS_EXTRA,
                ArrayList(failedStatusChangedIds)
            )
        }
        applicationContext.sendBroadcast(detailsIntent)
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

    companion object {
        private const val TAG = "ReIssuanceWorker"
        const val RE_ISSUANCE_WORK_NAME = "reIssuanceWorker"

        internal fun classifyReIssuanceState(state: IssueDocumentsPartialState): ReIssuanceOutcome {
            return when (state) {
                is IssueDocumentsPartialState.DeferredSuccess -> ReIssuanceOutcome.Pending
                is IssueDocumentsPartialState.PartialSuccess,
                is IssueDocumentsPartialState.Success -> ReIssuanceOutcome.Replaced

                is IssueDocumentsPartialState.UserAuthRequired -> ReIssuanceOutcome.UserAuthRequired
                is IssueDocumentsPartialState.Failure,
                IssueDocumentsPartialState.UserAuthCancelled -> ReIssuanceOutcome.Failed
            }
        }

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

        internal fun getChangedFailedReIssuanceIds(
            previousFailedIds: Set<String>,
            failedIds: List<String>
        ): List<String> {
            val currentFailedIds: Set<String> = failedIds.toSet()
            val changedIds = linkedSetOf<String>()
            previousFailedIds.forEach { id ->
                if (id !in currentFailedIds) changedIds.add(id)
            }
            currentFailedIds.forEach { id ->
                if (id !in previousFailedIds) changedIds.add(id)
            }
            return changedIds.toList()
        }

        internal fun getFailedReIssuanceDetailRefreshIds(
            failedStatusChangedIds: List<String>,
            replacedIds: List<String>
        ): List<String> {
            val replacedIdSet: Set<String> = replacedIds.toSet()
            return failedStatusChangedIds.filterNot { id -> id in replacedIdSet }
        }
    }
}
