/*
 * Copyright (c) 2023 European Commission
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

package eu.europa.ec.networklogic.api

import eu.europa.ec.networklogic.model.ApiResponse
import eu.europa.ec.networklogic.model.request.ActionRespondRequest
import eu.europa.ec.networklogic.model.request.CompletePairingRequest
import eu.europa.ec.networklogic.model.request.CompleteProfileRequest
import eu.europa.ec.networklogic.model.request.RecordLegalAcceptanceRequest
import eu.europa.ec.networklogic.model.request.CreateVerificationSessionRequest
import eu.europa.ec.networklogic.model.request.CreateAuthboundPidSessionRequest
import eu.europa.ec.networklogic.model.request.DummyRequest
import eu.europa.ec.networklogic.model.request.MaisaExchangeRequest
import eu.europa.ec.networklogic.model.request.MaisaIssueRequest
import eu.europa.ec.networklogic.model.request.WalletActivationRequest
import eu.europa.ec.networklogic.model.response.AttestationChallengeResponse

import eu.europa.ec.networklogic.model.response.AccountDeletionEnvelopeResponse
import eu.europa.ec.networklogic.model.response.AuthboundPidSessionStatus
import eu.europa.ec.networklogic.model.response.CheckHandleResponse
import eu.europa.ec.networklogic.model.response.CreateAuthboundPidSessionResponse
import eu.europa.ec.networklogic.model.response.CreateVerificationSessionResponse
import eu.europa.ec.networklogic.model.response.DummyResponse

import eu.europa.ec.networklogic.model.response.LegalAcceptanceEnvelopeResponse
import eu.europa.ec.networklogic.model.response.MaisaAuthorizeResponse
import eu.europa.ec.networklogic.model.response.MaisaExchangeResponse
import eu.europa.ec.networklogic.model.response.MaisaIssueResponse
import eu.europa.ec.networklogic.model.response.ProfileResponse

import eu.europa.ec.networklogic.model.response.ActionRespondResponse
import eu.europa.ec.networklogic.model.response.ActionsListResponse
import eu.europa.ec.networklogic.model.response.DeviceStatusResponse
import eu.europa.ec.networklogic.model.response.PairingCompleteResponse
import eu.europa.ec.networklogic.model.response.VerificationPublicSessionResponse
import eu.europa.ec.networklogic.model.response.VerificationSessionDetailResponse
import eu.europa.ec.networklogic.model.response.VerificationSessionStatusEventDto
import eu.europa.ec.networklogic.model.response.VerificationSessionsListResponse
import eu.europa.ec.networklogic.model.response.WalletActivationResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * API client interface for Authbound REST endpoints.
 *
 * Provides a unified interface for all backend API calls, maintaining
 * API compatibility while using Ktor HttpClient internally.
 */
interface ApiClient {
    suspend fun test(body: DummyRequest): ApiResponse<DummyResponse>

    suspend fun getAttestationChallenge(bearerToken: String): ApiResponse<AttestationChallengeResponse>
    suspend fun activateWallet(body: WalletActivationRequest, bearerToken: String): ApiResponse<WalletActivationResponse>
    suspend fun deleteWalletActivation(bearerToken: String): ApiResponse<Unit>

    // Profile API methods
    suspend fun completeProfile(body: CompleteProfileRequest, bearerToken: String): ApiResponse<Unit>
    suspend fun checkHandleAvailability(handle: String, bearerToken: String): ApiResponse<CheckHandleResponse>
    suspend fun getMyProfile(bearerToken: String): ApiResponse<ProfileResponse>
    suspend fun recordLegalAcceptance(body: RecordLegalAcceptanceRequest, bearerToken: String): ApiResponse<LegalAcceptanceEnvelopeResponse>
    suspend fun requestAccountDeletion(bearerToken: String): ApiResponse<AccountDeletionEnvelopeResponse>
    suspend fun cancelAccountDeletion(bearerToken: String): ApiResponse<AccountDeletionEnvelopeResponse>

    // Maisa mobile endpoints
    suspend fun startMaisaAuth(bearerToken: String?): ApiResponse<MaisaAuthorizeResponse>
    suspend fun exchangeMaisaCode(body: MaisaExchangeRequest, bearerToken: String?): ApiResponse<MaisaExchangeResponse>
    suspend fun issueMaisaCredential(body: MaisaIssueRequest, bearerToken: String?): ApiResponse<MaisaIssueResponse>

    // Actions endpoints
    suspend fun getActions(
        bearerToken: String,
        status: String? = null,
        type: String? = null
    ): ApiResponse<ActionsListResponse>

    suspend fun respondToAction(
        actionId: String,
        body: ActionRespondRequest,
        bearerToken: String
    ): ApiResponse<ActionRespondResponse>

    // Verification session endpoints
    suspend fun createVerificationSession(
        body: CreateVerificationSessionRequest,
        bearerToken: String
    ): ApiResponse<CreateVerificationSessionResponse>

    suspend fun getVerificationSessions(
        userId: String,
        bearerToken: String
    ): ApiResponse<VerificationSessionsListResponse>

    suspend fun getVerificationSession(
        sessionId: String,
        bearerToken: String
    ): ApiResponse<VerificationSessionDetailResponse>

    suspend fun getPublicVerificationSession(
        sessionId: String,
        accessToken: String
    ): ApiResponse<VerificationPublicSessionResponse>

    fun observeVerificationSessionStatus(
        statusStreamUrl: String
    ): Flow<VerificationSessionStatusEventDto>

    // Device linking endpoints
    suspend fun completePairing(
        completionUrl: String,
        body: CompletePairingRequest,
        bearerToken: String
    ): ApiResponse<PairingCompleteResponse>

    suspend fun unlinkCurrentDevice(bearerToken: String): ApiResponse<Unit>

    suspend fun getDeviceStatus(
        bearerToken: String
    ): ApiResponse<DeviceStatusResponse>

    // AuthboundPID Identity endpoints
    suspend fun createAuthboundPidSession(body: CreateAuthboundPidSessionRequest, bearerToken: String): ApiResponse<CreateAuthboundPidSessionResponse>
    suspend fun getAuthboundPidSessionStatus(sessionId: String, bearerToken: String): ApiResponse<AuthboundPidSessionStatus>
    suspend fun resolveAuthboundPidSession(sessionId: String, bearerToken: String): ApiResponse<AuthboundPidSessionStatus>
}

/**
 * Ktor-based implementation of [ApiClient].
 *
 * Replaces the previous Retrofit implementation while maintaining
 * the same API surface for consumers.
 */
class KtorApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String
) : ApiClient {

    companion object {
        private const val TAG = "KtorApiClient"
        private val sseJson = Json {
            ignoreUnknownKeys = true
        }
    }

    init {
        android.util.Log.d(TAG, "Initialized with baseUrl: $baseUrl")
    }

    /**
     * Executes an HTTP request and wraps the response in [ApiResponse].
     *
     * Handles both successful responses (2xx) and error responses,
     * converting them to the appropriate [ApiResponse] subtype.
     */
    private suspend inline fun <reified T> executeRequest(
        crossinline block: suspend () -> HttpResponse
    ): ApiResponse<T> {
        return try {
            val response = block()
            if (response.status.isSuccess()) {
                ApiResponse.Success(
                    body = response.body(),
                    code = response.status.value
                )
            } else {
                ApiResponse.Error(
                    code = response.status.value,
                    message = response.status.description,
                    errorBody = response.bodyAsText()
                )
            }
        } catch (e: Exception) {
            ApiResponse.Error(
                code = 0,
                message = e.message ?: "Network error"
            )
        }
    }

    /**
     * Executes an HTTP request that returns Unit (no body expected).
     *
     * Used for endpoints like DELETE that don't return a response body.
     */
    private suspend fun executeUnitRequest(
        block: suspend () -> HttpResponse
    ): ApiResponse<Unit> {
        return try {
            val response = block()
            if (response.status.isSuccess()) {
                ApiResponse.Success(
                    body = Unit,
                    code = response.status.value
                )
            } else {
                ApiResponse.Error(
                    code = response.status.value,
                    message = response.status.description,
                    errorBody = response.bodyAsText()
                )
            }
        } catch (e: Exception) {
            ApiResponse.Error(
                code = 0,
                message = e.message ?: "Network error"
            )
        }
    }

    // ============================================================================
    // Test endpoint
    // ============================================================================

    override suspend fun test(body: DummyRequest): ApiResponse<DummyResponse> {
        return executeRequest {
            httpClient.post("$baseUrl/test/path") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    // ============================================================================
    // Wallet Activation endpoints
    // ============================================================================

    override suspend fun getAttestationChallenge(bearerToken: String): ApiResponse<AttestationChallengeResponse> {
        return executeRequest {
            httpClient.get("$baseUrl/api/mobile/wallet-activation/challenge") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
            }
        }
    }

    override suspend fun activateWallet(
        body: WalletActivationRequest,
        bearerToken: String
    ): ApiResponse<WalletActivationResponse> {
        return executeRequest {
            httpClient.post("$baseUrl/api/mobile/wallet-activation") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                setBody(body)
            }
        }
    }

    override suspend fun deleteWalletActivation(bearerToken: String): ApiResponse<Unit> {
        return executeUnitRequest {
            httpClient.delete("$baseUrl/api/mobile/profile") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
            }
        }
    }

    // ============================================================================
    // Profile endpoints
    // ============================================================================

    override suspend fun completeProfile(
        body: CompleteProfileRequest,
        bearerToken: String
    ): ApiResponse<Unit> {
        return executeUnitRequest {
            httpClient.post("$baseUrl/api/profiles/complete") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                setBody(body)
            }
        }
    }

    override suspend fun checkHandleAvailability(
        handle: String,
        bearerToken: String
    ): ApiResponse<CheckHandleResponse> {
        val url = "$baseUrl/api/profiles/check?handle=$handle"
        android.util.Log.d(TAG, "checkHandleAvailability: GET $url")
        return executeRequest {
            httpClient.get("$baseUrl/api/profiles/check") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                parameter("handle", handle)
            }
        }
    }

    override suspend fun getMyProfile(bearerToken: String): ApiResponse<ProfileResponse> {
        return executeRequest {
            httpClient.get("$baseUrl/api/profiles/me") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
            }
        }
    }

    override suspend fun recordLegalAcceptance(
        body: RecordLegalAcceptanceRequest,
        bearerToken: String
    ): ApiResponse<LegalAcceptanceEnvelopeResponse> {
        return executeRequest {
            httpClient.post("$baseUrl/api/profiles/legal-acceptance") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                setBody(body)
            }
        }
    }

    override suspend fun requestAccountDeletion(
        bearerToken: String
    ): ApiResponse<AccountDeletionEnvelopeResponse> {
        return executeRequest {
            httpClient.delete("$baseUrl/api/profiles/account") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
            }
        }
    }

    override suspend fun cancelAccountDeletion(
        bearerToken: String
    ): ApiResponse<AccountDeletionEnvelopeResponse> {
        return executeRequest {
            httpClient.post("$baseUrl/api/profiles/account/cancel-deletion") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
            }
        }
    }

    // ============================================================================
    // MAISA OAuth endpoints
    // ============================================================================

    override suspend fun startMaisaAuth(bearerToken: String?): ApiResponse<MaisaAuthorizeResponse> {
        return executeRequest {
            httpClient.post("$baseUrl/api/mobile/maisa/authorize") {
                contentType(ContentType.Application.Json)
                bearerToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
        }
    }

    override suspend fun exchangeMaisaCode(
        body: MaisaExchangeRequest,
        bearerToken: String?
    ): ApiResponse<MaisaExchangeResponse> {
        return executeRequest {
            httpClient.post("$baseUrl/api/mobile/maisa/exchange") {
                contentType(ContentType.Application.Json)
                bearerToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                setBody(body)
            }
        }
    }

    override suspend fun issueMaisaCredential(
        body: MaisaIssueRequest,
        bearerToken: String?
    ): ApiResponse<MaisaIssueResponse> {
        return executeRequest {
            httpClient.post("$baseUrl/api/mobile/maisa/issue") {
                contentType(ContentType.Application.Json)
                bearerToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                setBody(body)
            }
        }
    }

    // ============================================================================
    // Actions endpoints
    // ============================================================================

    override suspend fun getActions(
        bearerToken: String,
        status: String?,
        type: String?
    ): ApiResponse<ActionsListResponse> {
        return executeRequest {
            httpClient.get("$baseUrl/v1/actions/me/actions") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                status?.let { parameter("status", it) }
                type?.let { parameter("type", it) }
            }
        }
    }

    override suspend fun respondToAction(
        actionId: String,
        body: ActionRespondRequest,
        bearerToken: String
    ): ApiResponse<ActionRespondResponse> {
        return executeRequest {
            httpClient.post("$baseUrl/v1/actions/me/actions/$actionId/respond") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                setBody(body)
            }
        }
    }

    override suspend fun createVerificationSession(
        body: CreateVerificationSessionRequest,
        bearerToken: String
    ): ApiResponse<CreateVerificationSessionResponse> {
        return executeRequest {
            httpClient.post("$baseUrl/api/verification-sessions") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                setBody(body)
            }
        }
    }

    override suspend fun getVerificationSessions(
        userId: String,
        bearerToken: String
    ): ApiResponse<VerificationSessionsListResponse> {
        return executeRequest {
            httpClient.get("$baseUrl/api/verification-sessions") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                parameter("userId", userId)
            }
        }
    }

    override suspend fun getVerificationSession(
        sessionId: String,
        bearerToken: String
    ): ApiResponse<VerificationSessionDetailResponse> {
        return executeRequest {
            httpClient.get("$baseUrl/api/verification-sessions/$sessionId") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
            }
        }
    }

    override suspend fun getPublicVerificationSession(
        sessionId: String,
        accessToken: String
    ): ApiResponse<VerificationPublicSessionResponse> {
        return executeRequest {
            httpClient.get("$baseUrl/api/verification-sessions/public/$sessionId") {
                parameter("token", accessToken)
            }
        }
    }

    override fun observeVerificationSessionStatus(
        statusStreamUrl: String
    ): Flow<VerificationSessionStatusEventDto> = flow {
        httpClient.prepareGet(statusStreamUrl) {
            header(HttpHeaders.Accept, "text/event-stream")
            timeout {
                requestTimeoutMillis = 0
                socketTimeoutMillis = 0
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw IllegalStateException("HTTP ${response.status.value}: ${response.status.description}")
            }

            val channel = response.bodyAsChannel()
            var eventName: String? = null
            val dataLines = mutableListOf<String>()

            while (true) {
                val line = channel.readLine() ?: break

                when {
                    line.startsWith("event:") -> {
                        eventName = line.substringAfter(':').trim()
                    }

                    line.startsWith("data:") -> {
                        dataLines += line.substringAfter(':').trimStart()
                    }

                    line.isBlank() -> {
                        if (eventName == "status" && dataLines.isNotEmpty()) {
                            val payload = dataLines.joinToString(separator = "\n")
                            runCatching {
                                sseJson.decodeFromString<VerificationSessionStatusEventDto>(payload)
                            }.onSuccess { emit(it) }
                        }

                        eventName = null
                        dataLines.clear()
                    }
                }
            }
        }
    }

    // ============================================================================
    // Device Linking endpoints
    // ============================================================================

    override suspend fun completePairing(
        completionUrl: String,
        body: CompletePairingRequest,
        bearerToken: String
    ): ApiResponse<PairingCompleteResponse> {
        return executeRequest {
            httpClient.post(completionUrl) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                setBody(body)
            }
        }
    }

    override suspend fun unlinkCurrentDevice(bearerToken: String): ApiResponse<Unit> {
        return executeUnitRequest {
            httpClient.delete("$baseUrl/api/pairing/devices/current") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
            }
        }
    }

    override suspend fun getDeviceStatus(
        bearerToken: String
    ): ApiResponse<DeviceStatusResponse> {
        return executeRequest {
            httpClient.get("$baseUrl/api/pairing/devices/current") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
            }
        }
    }

    // ============================================================================
    // AuthboundPID Identity endpoints
    // ============================================================================

    override suspend fun createAuthboundPidSession(
        body: CreateAuthboundPidSessionRequest,
        bearerToken: String
    ): ApiResponse<CreateAuthboundPidSessionResponse> {
        return executeRequest {
            httpClient.post("$baseUrl/api/authboundpid/sessions") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
                setBody(body)
            }
        }
    }

    override suspend fun getAuthboundPidSessionStatus(
        sessionId: String,
        bearerToken: String
    ): ApiResponse<AuthboundPidSessionStatus> {
        return executeRequest {
            httpClient.get("$baseUrl/api/authboundpid/sessions/$sessionId") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
            }
        }
    }

    override suspend fun resolveAuthboundPidSession(
        sessionId: String,
        bearerToken: String
    ): ApiResponse<AuthboundPidSessionStatus> {
        return executeRequest {
            httpClient.post("$baseUrl/api/authboundpid/sessions/$sessionId/resolve") {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
            }
        }
    }
}
