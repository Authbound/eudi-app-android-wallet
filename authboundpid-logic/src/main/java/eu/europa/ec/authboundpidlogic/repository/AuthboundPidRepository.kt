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

/**
 * Repository for AuthboundPID Identity verification operations.
 */
interface AuthboundPidRepository {
    /**
     * Creates a new AuthboundPID verification session.
     */
    suspend fun createSession(): Result<CreateAuthboundPidSessionResponse>

    /**
     * Polls the backend for the current session status.
     */
    suspend fun getSessionStatus(sessionId: String): Result<AuthboundPidSessionStatus>

    /**
     * Resolves the session after Candour SDK completion.
     */
    suspend fun resolveSession(sessionId: String): Result<AuthboundPidSessionStatus>
}

class AuthboundPidRepositoryImpl(
    private val apiClient: ApiClient,
    private val supabaseClient: SupabaseClient
) : AuthboundPidRepository {

    override suspend fun createSession(): Result<CreateAuthboundPidSessionResponse> {
        val accessToken = getAccessToken()
            ?: return Result.failure(IllegalStateException("User not authenticated"))

        val request: CreateAuthboundPidSessionRequest = CreateAuthboundPidSessionRequest(
            launchType = "sdk"
        )

        return when (val response = apiClient.createAuthboundPidSession(request, accessToken)) {
            is ApiResponse.Success -> Result.success(response.body)
            is ApiResponse.Error -> Result.failure(Exception(response.errorBody.orErrorMessage(response.message)))
        }
    }

    override suspend fun getSessionStatus(sessionId: String): Result<AuthboundPidSessionStatus> {
        val accessToken = getAccessToken()
            ?: return Result.failure(IllegalStateException("User not authenticated"))

        return when (val response = apiClient.getAuthboundPidSessionStatus(sessionId, accessToken)) {
            is ApiResponse.Success -> Result.success(response.body)
            is ApiResponse.Error -> Result.failure(Exception(response.errorBody.orErrorMessage(response.message)))
        }
    }

    override suspend fun resolveSession(sessionId: String): Result<AuthboundPidSessionStatus> {
        val accessToken = getAccessToken()
            ?: return Result.failure(IllegalStateException("User not authenticated"))

        return when (val response = apiClient.resolveAuthboundPidSession(sessionId, accessToken)) {
            is ApiResponse.Success -> Result.success(response.body)
            is ApiResponse.Error -> Result.failure(Exception(response.errorBody.orErrorMessage(response.message)))
        }
    }

    private suspend fun getAccessToken(): String? {
        return supabaseClient.auth.currentAccessTokenOrNull()
    }

    private fun String?.orErrorMessage(fallback: String): String {
        return this?.takeIf { it.isNotBlank() } ?: fallback
    }
}
