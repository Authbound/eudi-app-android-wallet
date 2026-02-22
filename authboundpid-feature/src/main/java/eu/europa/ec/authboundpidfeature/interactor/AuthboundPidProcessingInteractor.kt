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

package eu.europa.ec.authboundpidfeature.interactor

import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.authboundpidlogic.repository.AuthboundPidRepository
import eu.europa.ec.networklogic.model.response.AuthboundPidSessionStatus
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class AuthboundPidProcessingPartialState {
    data object Polling : AuthboundPidProcessingPartialState()

    data class Verified(val credentialOfferUri: String) : AuthboundPidProcessingPartialState()

    data class VerifiedNoOffer(val sessionId: String) : AuthboundPidProcessingPartialState()

    data class Failed(val errorMessage: String) : AuthboundPidProcessingPartialState()

    data object Expired : AuthboundPidProcessingPartialState()

    data object Canceled : AuthboundPidProcessingPartialState()

    data object Timeout : AuthboundPidProcessingPartialState()
}

interface AuthboundPidProcessingInteractor {
    /**
     * Polls the backend for the AuthboundPID session result with exponential backoff.
     * Emits [AuthboundPidProcessingPartialState.Polling] during polling, then a terminal state.
     * Aborts after [MAX_CONSECUTIVE_FAILURES] consecutive network failures.
     */
    fun pollForResult(sessionId: String): Flow<AuthboundPidProcessingPartialState>

    /**
     * Returns the pending session ID from the repository, or null if none exists.
     */
    suspend fun getPendingSessionId(): String?

    /**
     * Returns the pending callback nonce from the repository, or null if none exists.
     */
    suspend fun getPendingNonce(): String?

    /**
     * Clears the pending session state in the repository.
     */
    suspend fun clearPendingSession()
}

class AuthboundPidProcessingInteractorImpl(
    private val authboundPidRepository: AuthboundPidRepository,
    private val resourceProvider: ResourceProvider
) : AuthboundPidProcessingInteractor {

    companion object {
        private val BACKOFF_DELAYS = listOf(1000L, 2000L, 4000L, 8000L)
        private const val MAX_POLLING_DURATION_MS = 120_000L
        private const val MAX_CONSECUTIVE_FAILURES = 5
    }

    private val genericErrorMsg: String
        get() = resourceProvider.genericErrorMessage()

    override fun pollForResult(sessionId: String): Flow<AuthboundPidProcessingPartialState> = flow {
        emit(AuthboundPidProcessingPartialState.Polling)

        val startTime = System.currentTimeMillis()
        var attemptIndex = 0
        var consecutiveFailures = 0

        while (System.currentTimeMillis() - startTime < MAX_POLLING_DURATION_MS) {
            authboundPidRepository.getSessionStatus(sessionId).fold(
                onSuccess = { status ->
                    consecutiveFailures = 0
                    val terminalState = mapTerminalStatus(status)
                    if (terminalState != null) {
                        emit(terminalState)
                        return@flow
                    }
                },
                onFailure = { error ->
                    consecutiveFailures++
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        emit(
                            AuthboundPidProcessingPartialState.Failed(
                                resourceProvider.getString(R.string.authboundpid_network_error)
                            )
                        )
                        return@flow
                    }
                }
            )

            val delayMs = BACKOFF_DELAYS.getOrElse(attemptIndex) { BACKOFF_DELAYS.last() }
            delay(delayMs)
            attemptIndex++
        }

        emit(AuthboundPidProcessingPartialState.Timeout)
    }.safeAsync {
        AuthboundPidProcessingPartialState.Failed(it.localizedMessage ?: genericErrorMsg)
    }

    override suspend fun getPendingSessionId(): String? =
        authboundPidRepository.getPendingSessionId()

    override suspend fun getPendingNonce(): String? =
        authboundPidRepository.getPendingNonce()

    override suspend fun clearPendingSession() =
        authboundPidRepository.clearPendingSession()

    private fun mapTerminalStatus(status: AuthboundPidSessionStatus): AuthboundPidProcessingPartialState? {
        return when (status.status) {
            AuthboundPidSessionStatus.STATUS_VERIFIED -> {
                val offerUri = status.credentialOfferUri
                if (offerUri != null) {
                    AuthboundPidProcessingPartialState.Verified(offerUri)
                } else {
                    AuthboundPidProcessingPartialState.VerifiedNoOffer(status.sessionId)
                }
            }
            AuthboundPidSessionStatus.STATUS_FAILED ->
                AuthboundPidProcessingPartialState.Failed(
                    resourceProvider.getString(R.string.authboundpid_verification_failed)
                )
            AuthboundPidSessionStatus.STATUS_EXPIRED ->
                AuthboundPidProcessingPartialState.Expired
            AuthboundPidSessionStatus.STATUS_CANCELED ->
                AuthboundPidProcessingPartialState.Canceled
            else -> null
        }
    }
}
