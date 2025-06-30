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
import io.github.jan.supabase.auth.user.UserInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import android.util.Base64
import eu.europa.ec.businesslogic.controller.log.LogController

import java.security.cert.Certificate

@Serializable
data class WalletActivationRequest(
    @SerialName("wua_public_key")
    val wuaPublicKey: String,
    @SerialName("device_info")
    val deviceInfo: String,
    @SerialName("push_notification_token")
    val pushNotificationToken: String
)

interface WalletActivationRepository {
    suspend fun activateWallet(
        publicKey: Certificate,
        attestationChain: Array<Certificate>,
        deviceInfo: String,
        pushToken: String,
    ): Result<UserInfo>
}

class WalletActivationRepositoryImpl(
    private val supabaseClient: SupabaseClient,
    private val httpClient: HttpClient,
    private val logController: LogController
) : WalletActivationRepository {
    override suspend fun activateWallet(
        publicKey: Certificate,
        attestationChain: Array<Certificate>,
        deviceInfo: String,
        pushToken: String,
    ): Result<UserInfo> {
        return try {
            val token = supabaseClient.auth.currentSessionOrNull()?.accessToken
                ?: return Result.failure(Exception("User not authenticated"))

            val request = WalletActivationRequest(
                wuaPublicKey = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP),
                deviceInfo = deviceInfo,
                pushNotificationToken = pushToken
            )
            
            logController.d("WalletActivation", "Request Body: $request")

            val response = httpClient.post("/api/mobile/wallet-activation") {
                contentType(ContentType.Application.Json)
                setBody(request)
                headers.append("Authorization", "Bearer $token")
            }.body<UserInfo>()

            Result.success(response)
        } catch (e: Exception) {
            logController.e("WalletActivation", e)
            Result.failure(e)
        }
    }
} 