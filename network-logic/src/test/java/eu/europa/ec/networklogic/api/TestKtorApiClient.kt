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

package eu.europa.ec.networklogic.api

import eu.europa.ec.networklogic.model.request.CompletePairingRequest
import eu.europa.ec.networklogic.model.request.UpdateDeviceTokenRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class TestKtorApiClient {

    @Test
    fun `Given refreshed FCM token, When updating current device, Then request uses canonical authenticated route`() = runTest {
        var requestedUrl: String? = null
        var authorizationHeader: String? = null
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            authorizationHeader = request.headers[HttpHeaders.Authorization]
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val apiClient = KtorApiClient(httpClient, "https://mobile.authbound.test")

        val result = apiClient.updateCurrentDeviceToken(
            body = UpdateDeviceTokenRequest(fcmToken = "new-fcm-token"),
            bearerToken = "access-token"
        )

        assertTrue(result.isSuccessful)
        assertEquals(
            "https://mobile.authbound.test/v1/mobile/pairing/devices/current/token",
            requestedUrl
        )
        assertEquals("Bearer access-token", authorizationHeader)
        httpClient.close()
    }

    @Test
    fun `Given pairing session id, When completing pairing, Then request uses configured mobile backend`() = runTest {
        // Given
        val sessionId = "550e8400-e29b-41d4-a716-446655440000"
        var requestedUrl: String? = null
        var authorizationHeader: String? = null
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            authorizationHeader = request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"success":true,"deviceId":"device-123","previousDeviceReplaced":false}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val apiClient = KtorApiClient(
            httpClient = httpClient,
            baseUrl = "https://mobile.authbound.test"
        )

        // When
        val result = apiClient.completePairing(
            sessionId = sessionId,
            body = CompletePairingRequest(
                deviceName = "Pixel Test",
                fcmToken = "fcm-token",
                challengeResponse = "challenge-response"
            ),
            bearerToken = "access-token"
        )

        // Then
        assertTrue(result.isSuccessful)
        assertEquals(
            "https://mobile.authbound.test/v1/mobile/pairing/complete/$sessionId",
            requestedUrl
        )
        assertEquals("Bearer access-token", authorizationHeader)
        httpClient.close()
    }
}
