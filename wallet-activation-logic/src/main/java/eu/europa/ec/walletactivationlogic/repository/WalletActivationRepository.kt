/*
 * Copyright (c) 2024 European Commission
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
package eu.europa.ec.walletactivationlogic.repository


import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth

import android.util.Base64
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.model.DeviceInfo

import eu.europa.ec.networklogic.api.ApiClient

import eu.europa.ec.networklogic.model.request.WalletActivationRequest
import eu.europa.ec.networklogic.model.response.WalletActivationResponse

import java.security.cert.Certificate



interface WalletActivationRepository {
    suspend fun activateWallet(
        publicKey: Certificate,
        attestationChain: Array<Certificate>,
        deviceInfo: DeviceInfo,
        pushToken: String,
    ): Result<WalletActivationResponse>
}

class WalletActivationRepositoryImpl(
    private val supabaseClient: SupabaseClient,
    private val api: ApiClient,
    private val logController: LogController
) : WalletActivationRepository {
    override suspend fun activateWallet(
        publicKey: Certificate,
        attestationChain: Array<Certificate>,
        deviceInfo: DeviceInfo,
        pushToken: String,
    ): Result<WalletActivationResponse> {
        return try {
            val token = supabaseClient.auth.currentSessionOrNull()?.accessToken
                ?: return Result.failure(Exception("User not authenticated"))

            val request = WalletActivationRequest(
                pushNotificationToken = pushToken,
                wuaPublicKey = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP),
                deviceInfo = deviceInfo)


            logController.d("WalletActivation", "Request Body: $request")
            val response = api.activateWallet(request, token)
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    logController.d("WalletActivation", "Success: $responseBody")
                    Result.success(responseBody)
                } else {
                    logController.e("WalletActivation", Exception("WalletActivation failed. Error body is null"))
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                logController.e("WalletActivation", Exception(errorMsg))
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            logController.e("WalletActivation", e)
            Result.failure(e)
        }
    }
} 