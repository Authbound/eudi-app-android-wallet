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
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class AuthboundPidIntroPartialState {
    data class SessionCreated(
        val sessionId: String,
        val redirectUrl: String
    ) : AuthboundPidIntroPartialState()

    data object CreatingSession : AuthboundPidIntroPartialState()

    data class Failure(val errorMessage: String) : AuthboundPidIntroPartialState()
}

interface AuthboundPidIntroInteractor {
    fun createSession(): Flow<AuthboundPidIntroPartialState>
}

class AuthboundPidIntroInteractorImpl(
    private val authboundPidRepository: AuthboundPidRepository,
    private val resourceProvider: ResourceProvider
) : AuthboundPidIntroInteractor {

    companion object {
        const val AUTHBOUNDPID_CALLBACK_URL = "authbound://authboundpid/callback"
    }

    private val genericErrorMsg: String
        get() = resourceProvider.genericErrorMessage()

    override fun createSession(): Flow<AuthboundPidIntroPartialState> = flow {
        emit(AuthboundPidIntroPartialState.CreatingSession)

        authboundPidRepository.createSession(AUTHBOUNDPID_CALLBACK_URL).fold(
            onSuccess = { response ->
                emit(
                    AuthboundPidIntroPartialState.SessionCreated(
                        sessionId = response.sessionId,
                        redirectUrl = response.redirectUrl
                    )
                )
            },
            onFailure = { error ->
                emit(AuthboundPidIntroPartialState.Failure(error.message ?: genericErrorMsg))
            }
        )
    }.safeAsync {
        AuthboundPidIntroPartialState.Failure(it.localizedMessage ?: genericErrorMsg)
    }
}
