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

package eu.europa.ec.networklogic.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class WalletAttestationHttpHeaders(
    val bearerToken: String? = null,
    val wuaChallengeId: String? = null,
    val wuaProof: String? = null,
)

data class WalletAttestationRequest(
    val body: JsonObject,
    val headers: WalletAttestationHttpHeaders = WalletAttestationHttpHeaders(),
)

data class WalletProofChallengeRequest(
    val purpose: String,
    val requestHash: String,
)

data class WalletProofChallenge(
    val challengeId: String,
    val challenge: String,
    val expiresAt: String,
    val algorithm: String,
    val attestationNonce: String,
    val attestationNonceExpiresAt: String,
)

interface WalletAttestationRepository {

    suspend fun createProofChallenge(
        baseUrl: String,
        bearerToken: String,
        request: WalletProofChallengeRequest,
    ): Result<WalletProofChallenge>

    suspend fun getWalletAttestation(
        baseUrl: String,
        request: WalletAttestationRequest,
    ): Result<String>

    suspend fun getKeyAttestation(
        baseUrl: String,
        request: WalletAttestationRequest,
    ): Result<String>
}

class WalletAttestationRepositoryImpl(
    private val httpClient: HttpClient
) : WalletAttestationRepository {

    private companion object {
        const val PROOF_CHALLENGE_PATH = "/proof-challenges"
        const val WALLET_INSTANCE_ATTESTATION_PATH = "/wallet-instance-attestation/jwk"
        const val WALLET_UNIT_ATTESTATION_PATH = "/wallet-unit-attestation/jwk-set"
        const val WUA_CHALLENGE_ID_HEADER = "X-WUA-Challenge-Id"
        const val WUA_PROOF_HEADER = "X-WUA-Proof"
    }

    override suspend fun createProofChallenge(
        baseUrl: String,
        bearerToken: String,
        request: WalletProofChallengeRequest,
    ): Result<WalletProofChallenge> = runCatching {
        val response = httpClient.post(baseUrl + PROOF_CHALLENGE_PATH) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $bearerToken")
            setBody(
                buildJsonObject {
                    put("purpose", JsonPrimitive(request.purpose))
                    put("requestHash", JsonPrimitive(request.requestHash))
                }
            )
        }
        response.requireSuccess()
        response.bodyAsText()
            .let { Json.decodeFromString<JsonObject>(it) }
            .let { payload ->
                WalletProofChallenge(
                    challengeId = payload.jsonObject["challengeId"]?.jsonPrimitive?.content
                        ?: throw IllegalStateException("Missing challengeId"),
                    challenge = payload.jsonObject["challenge"]?.jsonPrimitive?.content
                        ?: throw IllegalStateException("Missing challenge"),
                    expiresAt = payload.jsonObject["expiresAt"]?.jsonPrimitive?.content
                        ?: throw IllegalStateException("Missing expiresAt"),
                    algorithm = payload.jsonObject["algorithm"]?.jsonPrimitive?.content
                        ?: throw IllegalStateException("Missing algorithm"),
                    attestationNonce = payload.jsonObject["attestationNonce"]?.jsonPrimitive?.content
                        ?: throw IllegalStateException("Missing attestationNonce"),
                    attestationNonceExpiresAt = payload.jsonObject["attestationNonceExpiresAt"]?.jsonPrimitive?.content
                        ?: throw IllegalStateException("Missing attestationNonceExpiresAt"),
                )
            }
    }

    override suspend fun getWalletAttestation(
        baseUrl: String,
        request: WalletAttestationRequest,
    ): Result<String> = runCatching {
        val response = httpClient.post(baseUrl + WALLET_INSTANCE_ATTESTATION_PATH) {
            contentType(ContentType.Application.Json)
            applyHeaders(request.headers)
            setBody(request.body)
        }
        response.requireSuccess()
        response.bodyAsText()
            .let { Json.decodeFromString<JsonObject>(it) }
            .let { it.jsonObject["walletInstanceAttestation"]?.jsonPrimitive?.content }
            ?: throw IllegalStateException("No attestation response")
    }

    override suspend fun getKeyAttestation(
        baseUrl: String,
        request: WalletAttestationRequest,
    ): Result<String> = runCatching {
        val response = httpClient.post(baseUrl + WALLET_UNIT_ATTESTATION_PATH) {
            contentType(ContentType.Application.Json)
            applyHeaders(request.headers)
            setBody(request.body)
        }
        response.requireSuccess()
        response.bodyAsText()
            .let { Json.decodeFromString<JsonObject>(it) }
            .let { it.jsonObject["walletUnitAttestation"]?.jsonPrimitive?.content }
            ?: throw IllegalStateException("No attestation response")
    }

    private fun HttpRequestBuilder.applyHeaders(headers: WalletAttestationHttpHeaders) {
        headers.bearerToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        headers.wuaChallengeId?.let { header(WUA_CHALLENGE_ID_HEADER, it) }
        headers.wuaProof?.let { header(WUA_PROOF_HEADER, it) }
    }

    private fun HttpResponse.requireSuccess() {
        if (!status.isSuccess()) {
            throw IllegalStateException("Wallet attestation request failed: HTTP ${status.value}")
        }
    }
}
