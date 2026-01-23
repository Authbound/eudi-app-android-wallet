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
package eu.europa.ec.dashboardfeature.repository

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.request.MaisaExchangeRequest
import eu.europa.ec.networklogic.model.request.MaisaIssueRequest
import eu.europa.ec.networklogic.model.response.MaisaAuthorizeResponse
import eu.europa.ec.networklogic.model.response.MaisaIssueResponse
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth

interface MaisaRepository {
    suspend fun startAuth(): Result<MaisaAuthorizeResponse>
    suspend fun exchangeAndIssue(code: String, state: String, redirectUri: String): Result<MaisaIssueResponse>
}

class MaisaRepositoryImpl(
    private val apiClient: ApiClient,
    private val supabaseClient: SupabaseClient,
    private val prefsController: PrefsControllerV2,
    private val logController: LogController
) : MaisaRepository {

    companion object {
        private const val MAISA_STATE_KEY = "maisa_oauth_state"
        private const val MAISA_SUBJECT_REF_KEY = "maisa_subject_ref"
        private const val MAISA_CONNECTED_KEY = "maisa_connected"
        private const val MAISA_LAST_SYNC_KEY = "maisa_last_sync"
    }

    override suspend fun startAuth(): Result<MaisaAuthorizeResponse> {
        return try {
            val session = supabaseClient.auth.currentSessionOrNull()
            val token = session?.accessToken
            val subjectRef = session?.user?.id
            val response = apiClient.startMaisaAuth(token)
            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
            val body = response.body()
                ?: return Result.failure(Exception("Empty authorize response"))
            prefsController.setString(MAISA_STATE_KEY, body.state)
            if (!subjectRef.isNullOrBlank()) {
                prefsController.setString(MAISA_SUBJECT_REF_KEY, subjectRef)
            }
            Result.success(body)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exchangeAndIssue(
        code: String,
        state: String,
        redirectUri: String
    ): Result<MaisaIssueResponse> {
        return try {
            val session = supabaseClient.auth.currentSessionOrNull()
            val token = session?.accessToken
            val subjectRef = session?.user?.id
                ?: prefsController.getString(MAISA_SUBJECT_REF_KEY, "")
            if (subjectRef.isBlank()) {
                return Result.failure(IllegalStateException("Missing user context"))
            }
            val storedState = prefsController.getString(MAISA_STATE_KEY, "")
            if (storedState.isBlank() || storedState != state) {
                return Result.failure(IllegalStateException("Invalid state"))
            }
            val exchangeResponse = apiClient.exchangeMaisaCode(
                MaisaExchangeRequest(code = code, state = state, redirectUri = redirectUri),
                token
            )
            if (!exchangeResponse.isSuccessful) {
                return Result.failure(Exception("HTTP ${exchangeResponse.code()}: ${exchangeResponse.message()}"))
            }
            val exchangeBody = exchangeResponse.body()
                ?: return Result.failure(Exception("Empty exchange response"))
            val issueResponse = apiClient.issueMaisaCredential(
                MaisaIssueRequest(
                    subjectRef = subjectRef,
                    accessToken = exchangeBody.accessToken,
                    patientId = exchangeBody.patientId,
                    userPin = "1234"
                ),
                token
            )
            if (!issueResponse.isSuccessful) {
                return Result.failure(Exception("HTTP ${issueResponse.code()}: ${issueResponse.message()}"))
            }
            val issueBody = issueResponse.body()
                ?: return Result.failure(Exception("Empty issue response"))
            prefsController.setBool(MAISA_CONNECTED_KEY, true)
            prefsController.setString(MAISA_LAST_SYNC_KEY, "Just now")
            prefsController.clear(MAISA_STATE_KEY)
            prefsController.clear(MAISA_SUBJECT_REF_KEY)
            Result.success(issueBody)
        } catch (e: Exception) {
            logController.e("MaisaRepository", e)
            Result.failure(e)
        }
    }
}
