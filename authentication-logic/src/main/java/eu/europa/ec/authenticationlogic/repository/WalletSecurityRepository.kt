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

package eu.europa.ec.authenticationlogic.repository

import eu.europa.ec.authenticationlogic.controller.storage.WalletRecoveryChallengeController
import eu.europa.ec.authenticationlogic.model.WalletSecurityEventType
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.request.WalletRecoveryPrepareRequest
import eu.europa.ec.networklogic.model.request.WalletSecurityIncidentRequest
import eu.europa.ec.networklogic.model.response.WalletRecoveryPrepareResponse
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

interface WalletSecurityRepository {
    suspend fun reportIncident(
        eventType: WalletSecurityEventType,
        signals: List<String>
    ): Result<Unit>

    suspend fun prepareWalletRecovery(
        evidence: List<String>
    ): Result<Unit>
}

open class WalletSecurityRepositoryImpl(
    private val apiClient: ApiClient,
    private val supabaseClient: SupabaseClient,
    private val walletRecoveryChallengeController: WalletRecoveryChallengeController,
    private val logController: LogController
) : WalletSecurityRepository {

    protected open suspend fun getAuthToken(): String? {
        return supabaseClient.auth.currentSessionOrNull()?.accessToken
    }

    override suspend fun reportIncident(
        eventType: WalletSecurityEventType,
        signals: List<String>
    ): Result<Unit> {
        return try {
            val authToken: String = getAuthToken()
                ?: return Result.failure(IllegalStateException("User not authenticated"))
            val request: WalletSecurityIncidentRequest = WalletSecurityIncidentRequest(
                eventType = eventType.wireName,
                details = buildDetails(signals),
                detectedAt = Instant.now().toString()
            )
            val response = apiClient.reportWalletSecurityIncident(request, authToken)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException("Wallet security incident failed: HTTP ${response.code()}")
                )
            }
        } catch (e: Exception) {
            logController.w("WalletSecurityRepository") {
                "Failed to report ${eventType.wireName}: ${e.message}"
            }
            Result.failure(e)
        }
    }

    override suspend fun prepareWalletRecovery(
        evidence: List<String>
    ): Result<Unit> {
        return try {
            if (walletRecoveryChallengeController.peekPreparedChallenge() != null) {
                return Result.success(Unit)
            }
            val authToken: String = getAuthToken()
                ?: return Result.failure(IllegalStateException("User not authenticated"))
            val request: WalletRecoveryPrepareRequest = WalletRecoveryPrepareRequest(
                evidence = evidence,
                clientDetectedAt = Instant.now().toString()
            )
            val response = apiClient.prepareWalletRecovery(request, authToken)
            if (!response.isSuccessful) {
                return Result.failure(IllegalStateException(mapRecoveryError(response.code())))
            }
            val body: WalletRecoveryPrepareResponse = response.body()
                ?: return Result.failure(
                    IllegalStateException("Wallet recovery preparation returned an empty response")
                )
            walletRecoveryChallengeController.cachePreparedChallenge(body.toAttestationChallengeResponse())
            Result.success(Unit)
        } catch (e: Exception) {
            logController.w("WalletSecurityRepository") {
                "Failed to prepare wallet recovery: ${e.message}"
            }
            Result.failure(e)
        }
    }

    private fun buildDetails(signals: List<String>): JsonObject? {
        if (signals.isEmpty()) {
            return null
        }
        return JsonObject(mapOf("signals" to JsonArray(signals.map(::JsonPrimitive))))
    }

    private fun mapRecoveryError(httpCode: Int): String {
        return when (httpCode) {
            429 -> "Wallet recovery is temporarily rate limited. Try again later."
            else -> "Online wallet recovery is unavailable right now."
        }
    }
}
