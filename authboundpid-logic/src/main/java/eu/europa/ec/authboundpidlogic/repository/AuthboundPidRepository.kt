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

package eu.europa.ec.authboundpidlogic.repository

import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.ApiResponse
import eu.europa.ec.networklogic.model.request.CreateAuthboundPidSessionRequest
import eu.europa.ec.networklogic.model.response.AuthboundPidSessionStatus
import eu.europa.ec.networklogic.model.response.CreateAuthboundPidSessionResponse
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Repository for AuthboundPID Identity verification operations.
 *
 * Manages API calls and holds the pending session ID across the Chrome Custom Tab
 * redirect flow. The @Single Koin scope ensures this survives the CCT redirect
 * (the process stays alive since CCT runs in the same task).
 */
interface AuthboundPidRepository {
    /**
     * Creates a new AuthboundPID verification session.
     * Stores the session ID and nonce for use after the callback returns.
     * @param callbackBaseUrl The base callback URL (nonce will be appended).
     */
    suspend fun createSession(callbackBaseUrl: String): Result<CreateAuthboundPidSessionResponse>

    /**
     * Polls the backend for the current session status.
     */
    suspend fun getSessionStatus(sessionId: String): Result<AuthboundPidSessionStatus>

    /**
     * The session ID from the most recent [createSession] call.
     * Survives the Chrome Custom Tab redirect since this is a @Single scoped repository.
     */
    suspend fun getPendingSessionId(): String?

    /**
     * The nonce generated for the most recent callback URL.
     * Used to validate that the deep link callback is authentic.
     */
    suspend fun getPendingNonce(): String?

    /**
     * Clears the pending session state after it's been consumed.
     */
    suspend fun clearPendingSession()
}

class AuthboundPidRepositoryImpl(
    private val apiClient: ApiClient,
    private val supabaseClient: SupabaseClient
) : AuthboundPidRepository {

    private val sessionMutex = Mutex()
    private var _pendingSessionId: String? = null
    private var _pendingNonce: String? = null

    override suspend fun getPendingSessionId(): String? = sessionMutex.withLock {
        _pendingSessionId
    }

    override suspend fun getPendingNonce(): String? = sessionMutex.withLock {
        _pendingNonce
    }

    override suspend fun createSession(callbackBaseUrl: String): Result<CreateAuthboundPidSessionResponse> {
        val accessToken = getAccessToken()
            ?: return Result.failure(IllegalStateException("User not authenticated"))

        val nonce = UUID.randomUUID().toString()
        val callbackUrl = "$callbackBaseUrl?nonce=$nonce"
        val request = CreateAuthboundPidSessionRequest(callbackUrl = callbackUrl)

        return when (val response = apiClient.createAuthboundPidSession(request, accessToken)) {
            is ApiResponse.Success -> {
                sessionMutex.withLock {
                    _pendingSessionId = response.body.sessionId
                    _pendingNonce = nonce
                }
                Result.success(response.body)
            }
            is ApiResponse.Error -> Result.failure(Exception(response.message))
        }
    }

    override suspend fun getSessionStatus(sessionId: String): Result<AuthboundPidSessionStatus> {
        val accessToken = getAccessToken()
            ?: return Result.failure(IllegalStateException("User not authenticated"))

        return when (val response = apiClient.getAuthboundPidSessionStatus(sessionId, accessToken)) {
            is ApiResponse.Success -> Result.success(response.body)
            is ApiResponse.Error -> Result.failure(Exception(response.message))
        }
    }

    override suspend fun clearPendingSession() {
        sessionMutex.withLock {
            _pendingSessionId = null
            _pendingNonce = null
        }
    }

    private suspend fun getAccessToken(): String? {
        return supabaseClient.auth.currentAccessTokenOrNull()
    }
}
