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

package eu.europa.ec.quickidlogic.interactor

import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.quickidlogic.model.QuickIdSession
import eu.europa.ec.quickidlogic.repository.QuickIdRepository
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Partial states for QuickID session operations.
 */
sealed class QuickIdSessionPartialState {
    /**
     * Session created successfully.
     */
    data class SessionCreated(
        val sessionId: String
    ) : QuickIdSessionPartialState()

    /**
     * Liveness session created successfully.
     */
    data class LivenessSessionCreated(
        val livenessSessionId: String,
        val region: String
    ) : QuickIdSessionPartialState()

    /**
     * Session operation failed.
     */
    data class Failure(val errorMessage: String) : QuickIdSessionPartialState()
}

/**
 * Interactor for managing QuickID verification sessions.
 *
 * Handles session creation and lifecycle management.
 */
interface QuickIdSessionInteractor {
    /**
     * Creates a new QuickID verification session.
     *
     * @return Flow emitting the session creation result
     */
    fun createSession(): Flow<QuickIdSessionPartialState>

    /**
     * Creates an AWS Liveness session for the current QuickID session.
     *
     * @return Flow emitting the liveness session creation result
     */
    fun createLivenessSession(): Flow<QuickIdSessionPartialState>

    /**
     * Gets the current session ID.
     *
     * @return The session ID or null if no active session
     */
    fun getSessionId(): String?

    /**
     * Gets the current session's liveness session ID.
     *
     * @return The liveness session ID or null if not created
     */
    fun getLivenessSessionId(): String?

    /**
     * Clears the current session.
     */
    fun clearSession()

    /**
     * Checks if there is an active session.
     *
     * @return True if there is an active, non-expired session
     */
    fun hasActiveSession(): Boolean
}

class QuickIdSessionInteractorImpl(
    private val quickIdRepository: QuickIdRepository,
    private val resourceProvider: ResourceProvider
) : QuickIdSessionInteractor {

    private val genericErrorMsg: String
        get() = resourceProvider.genericErrorMessage()

    override fun createSession(): Flow<QuickIdSessionPartialState> = flow {
        quickIdRepository.createSession()
            .onSuccess { session ->
                emit(QuickIdSessionPartialState.SessionCreated(
                    sessionId = session.sessionId
                ))
            }
            .onFailure { error ->
                emit(QuickIdSessionPartialState.Failure(
                    errorMessage = error.message ?: genericErrorMsg
                ))
            }
    }.safeAsync {
        QuickIdSessionPartialState.Failure(
            errorMessage = it.localizedMessage ?: genericErrorMsg
        )
    }

    override fun createLivenessSession(): Flow<QuickIdSessionPartialState> = flow {
        quickIdRepository.createLivenessSession()
            .onSuccess { livenessSession ->
                emit(QuickIdSessionPartialState.LivenessSessionCreated(
                    livenessSessionId = livenessSession.livenessSessionId,
                    region = livenessSession.region
                ))
            }
            .onFailure { error ->
                emit(QuickIdSessionPartialState.Failure(
                    errorMessage = error.message ?: genericErrorMsg
                ))
            }
    }.safeAsync {
        QuickIdSessionPartialState.Failure(
            errorMessage = it.localizedMessage ?: genericErrorMsg
        )
    }

    override fun getSessionId(): String? {
        return quickIdRepository.getCurrentSession()?.sessionId
    }

    override fun getLivenessSessionId(): String? {
        return quickIdRepository.getCurrentSession()?.livenessSessionId
    }

    override fun clearSession() {
        quickIdRepository.clearSession()
    }

    override fun hasActiveSession(): Boolean {
        return quickIdRepository.getCurrentSession() != null
    }
}
