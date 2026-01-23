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

import eu.europa.ec.networklogic.model.request.CompleteProfileRequest
import eu.europa.ec.networklogic.model.request.DummyRequest
import eu.europa.ec.networklogic.model.request.MaisaExchangeRequest
import eu.europa.ec.networklogic.model.request.MaisaIssueRequest
import eu.europa.ec.networklogic.model.request.WalletActivationRequest
import eu.europa.ec.networklogic.model.response.AttestationChallengeResponse
import eu.europa.ec.networklogic.model.response.CheckHandleResponse
import eu.europa.ec.networklogic.model.response.DummyResponse
import eu.europa.ec.networklogic.model.response.MaisaAuthorizeResponse
import eu.europa.ec.networklogic.model.response.MaisaExchangeResponse
import eu.europa.ec.networklogic.model.response.MaisaIssueResponse
import eu.europa.ec.networklogic.model.response.ProfileResponse
import eu.europa.ec.networklogic.model.response.WalletActivationResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query


interface Api {
    @POST("test/path")
    suspend fun test(
        @Body body: DummyRequest
    ): Response<DummyResponse>

    @GET("api/mobile/wallet-activation/challenge")
    suspend fun getAttestationChallenge(
        @Header("Authorization") auth: String
    ): Response<AttestationChallengeResponse>

    @POST("api/mobile/wallet-activation")
    suspend fun activateWallet(
        @Body body: WalletActivationRequest,
        @Header("Authorization") auth: String
    ): Response<WalletActivationResponse>

    @DELETE("api/mobile/profile")
    suspend fun deleteWalletActivation(
        @Header("Authorization") auth: String
    ): Response<Unit>

    // Profile endpoints to replace Supabase functions
    @POST("api/profiles/complete")
    suspend fun completeProfile(
        @Body body: CompleteProfileRequest,
        @Header("Authorization") auth: String
    ): Response<Unit>

    @GET("api/profiles/check")
    suspend fun checkHandleAvailability(
        @Query("handle") handle: String,
        @Header("Authorization") auth: String
    ): Response<CheckHandleResponse>

    @GET("api/profiles/me")
    suspend fun getMyProfile(
        @Header("Authorization") auth: String
    ): Response<ProfileResponse>

    @POST("api/mobile/maisa/authorize")
    suspend fun startMaisaAuth(
        @Header("Authorization") auth: String?
    ): Response<MaisaAuthorizeResponse>

    @POST("api/mobile/maisa/exchange")
    suspend fun exchangeMaisaCode(
        @Body body: MaisaExchangeRequest,
        @Header("Authorization") auth: String?
    ): Response<MaisaExchangeResponse>

    @POST("api/mobile/maisa/issue")
    suspend fun issueMaisaCredential(
        @Body body: MaisaIssueRequest,
        @Header("Authorization") auth: String?
    ): Response<MaisaIssueResponse>
}

interface ApiClient {
    suspend fun test(
        body: DummyRequest
    ): Response<DummyResponse>

    suspend fun getAttestationChallenge(bearerToken: String): Response<AttestationChallengeResponse>
    suspend fun activateWallet(body: WalletActivationRequest, bearerToken: String): Response<WalletActivationResponse>
    suspend fun deleteWalletActivation(bearerToken: String): Response<Unit>

    // Profile API methods to replace Supabase functions
    suspend fun completeProfile(body: CompleteProfileRequest, bearerToken: String): Response<Unit>
    suspend fun checkHandleAvailability(handle: String, bearerToken: String): Response<CheckHandleResponse>
    suspend fun getMyProfile(bearerToken: String): Response<ProfileResponse>

    // Maisa mobile endpoints
    suspend fun startMaisaAuth(bearerToken: String?): Response<MaisaAuthorizeResponse>
    suspend fun exchangeMaisaCode(body: MaisaExchangeRequest, bearerToken: String?): Response<MaisaExchangeResponse>
    suspend fun issueMaisaCredential(body: MaisaIssueRequest, bearerToken: String?): Response<MaisaIssueResponse>
}

class ApiClientImpl(private val apiService: Api) : ApiClient {
    override suspend fun test(body: DummyRequest): Response<DummyResponse> {
        return apiService.test(body)
    }

    override suspend fun getAttestationChallenge(bearerToken: String): Response<AttestationChallengeResponse> {
        return apiService.getAttestationChallenge("Bearer $bearerToken")
    }

    override suspend fun activateWallet(body: WalletActivationRequest, bearerToken: String): Response<WalletActivationResponse> {
        return apiService.activateWallet(body, "Bearer $bearerToken")
    }

    override suspend fun deleteWalletActivation(bearerToken: String): Response<Unit> {
        return apiService.deleteWalletActivation("Bearer $bearerToken")
    }

    override suspend fun completeProfile(body: CompleteProfileRequest, bearerToken: String): Response<Unit> {
        return apiService.completeProfile(body, "Bearer $bearerToken")
    }

    override suspend fun checkHandleAvailability(handle: String, bearerToken: String): Response<CheckHandleResponse> {
        return apiService.checkHandleAvailability(handle, "Bearer $bearerToken")
    }

    override suspend fun getMyProfile(bearerToken: String): Response<ProfileResponse> {
        return apiService.getMyProfile("Bearer $bearerToken")
    }

    override suspend fun startMaisaAuth(bearerToken: String?): Response<MaisaAuthorizeResponse> {
        return apiService.startMaisaAuth(bearerToken?.let { "Bearer $it" })
    }

    override suspend fun exchangeMaisaCode(
        body: MaisaExchangeRequest,
        bearerToken: String?
    ): Response<MaisaExchangeResponse> {
        return apiService.exchangeMaisaCode(body, bearerToken?.let { "Bearer $it" })
    }

    override suspend fun issueMaisaCredential(
        body: MaisaIssueRequest,
        bearerToken: String?
    ): Response<MaisaIssueResponse> {
        return apiService.issueMaisaCredential(body, bearerToken?.let { "Bearer $it" })
    }
}