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

import eu.europa.ec.authboundpidlogic.repository.AuthboundPidRepository
import eu.europa.ec.networklogic.model.response.AuthboundPidSessionStatus
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

sealed class AuthboundPidIntroPartialState {
    data class SessionCreated(
        val sessionId: String,
        val candourSessionId: String,
        val candourApiEndpoint: String
    ) : AuthboundPidIntroPartialState()

    data object CreatingSession : AuthboundPidIntroPartialState()

    data class Failure(val errorMessage: String) : AuthboundPidIntroPartialState()
}

sealed class AuthboundPidVerificationPartialState {
    data object Loading : AuthboundPidVerificationPartialState()

    data class Verified(val credentialOfferUri: String?) : AuthboundPidVerificationPartialState()

    data object Failed : AuthboundPidVerificationPartialState()

    data object Expired : AuthboundPidVerificationPartialState()

    data object Timeout : AuthboundPidVerificationPartialState()

    data class Failure(val errorMessage: String) : AuthboundPidVerificationPartialState()
}

interface AuthboundPidIntroInteractor {
    fun createSession(): Flow<AuthboundPidIntroPartialState>

    fun getVerificationResult(sessionId: String): Flow<AuthboundPidVerificationPartialState>
}

class AuthboundPidIntroInteractorImpl(
    private val authboundPidRepository: AuthboundPidRepository,
    private val resourceProvider: ResourceProvider
) : AuthboundPidIntroInteractor {

    companion object {
        private val RETRY_DELAYS_MS = listOf(2000L, 4000L, 8000L)
    }

    private val genericErrorMsg: String
        get() = resourceProvider.genericErrorMessage()

    private val genericNetworkErrorMsg: String
        get() = resourceProvider.genericNetworkErrorMessage()

    private suspend fun loadVerificationStatus(
        sessionId: String,
        previousStatus: AuthboundPidSessionStatus?
    ): Result<AuthboundPidSessionStatus> {
        return if (
            previousStatus == null ||
            (
                previousStatus.status == AuthboundPidSessionStatus.STATUS_PROCESSING &&
                    previousStatus.identityVerified == true &&
                    previousStatus.credentialOfferUri.isNullOrBlank()
                )
        ) {
            authboundPidRepository.resolveSession(sessionId)
        } else {
            authboundPidRepository.getSessionStatus(sessionId)
        }
    }

    override fun createSession(): Flow<AuthboundPidIntroPartialState> = flow {
        emit(AuthboundPidIntroPartialState.CreatingSession)

        authboundPidRepository.createSession().fold(
            onSuccess = { response ->
                emit(
                    AuthboundPidIntroPartialState.SessionCreated(
                        sessionId = response.sessionId,
                        candourSessionId = response.candourSessionId,
                        candourApiEndpoint = response.candourApiEndpoint
                    )
                )
            },
            onFailure = { error ->
                emit(AuthboundPidIntroPartialState.Failure(error.message ?: genericErrorMsg))
            }
        )
    }.catch { error ->
        emit(AuthboundPidIntroPartialState.Failure(error.localizedMessage ?: genericErrorMsg))
    }

    override fun getVerificationResult(sessionId: String): Flow<AuthboundPidVerificationPartialState> = flow {
        emit(AuthboundPidVerificationPartialState.Loading)

        var latestStatus: AuthboundPidSessionStatus? = null

        repeat(RETRY_DELAYS_MS.size + 1) { attempt ->
            val status = loadVerificationStatus(sessionId, latestStatus).getOrElse { error ->
                emit(AuthboundPidVerificationPartialState.Failure(error.message ?: genericNetworkErrorMsg))
                return@flow
            }
            latestStatus = status

            when (status.status) {
                AuthboundPidSessionStatus.STATUS_VERIFIED -> {
                    emit(AuthboundPidVerificationPartialState.Verified(status.credentialOfferUri))
                    return@flow
                }
                AuthboundPidSessionStatus.STATUS_FAILED -> {
                    emit(AuthboundPidVerificationPartialState.Failed)
                    return@flow
                }
                AuthboundPidSessionStatus.STATUS_EXPIRED -> {
                    emit(AuthboundPidVerificationPartialState.Expired)
                    return@flow
                }
                AuthboundPidSessionStatus.STATUS_PENDING,
                AuthboundPidSessionStatus.STATUS_PROCESSING -> {
                    if (attempt == RETRY_DELAYS_MS.size) {
                        emit(AuthboundPidVerificationPartialState.Timeout)
                        return@flow
                    }

                    delay(RETRY_DELAYS_MS[attempt])
                }
                else -> {
                    emit(AuthboundPidVerificationPartialState.Failure(genericErrorMsg))
                    return@flow
                }
            }
        }
    }.catch { error ->
        emit(AuthboundPidVerificationPartialState.Failure(error.localizedMessage ?: genericErrorMsg))
    }
}
