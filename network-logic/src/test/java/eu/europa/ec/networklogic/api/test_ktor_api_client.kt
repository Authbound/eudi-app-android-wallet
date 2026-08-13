/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 */

package eu.europa.ec.networklogic.api

import eu.europa.ec.networklogic.model.request.CompletePairingRequest
import eu.europa.ec.networklogic.model.request.UpdateDeviceTokenRequest
import eu.europa.ec.networklogic.model.ApiResponse
import eu.europa.ec.networklogic.model.response.PairingCompleteResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class TestKtorApiClient {

    private lateinit var httpClient: HttpClient
    private var requested: HttpRequestData? = null
    private var responseStatus: HttpStatusCode = HttpStatusCode.OK

    @Before
    fun before(): Unit {
        val engine: MockEngine = MockEngine { request: HttpRequestData ->
            requested = request
            respond(
                content = """{"success":true,"deviceId":"device-123","previousDeviceReplaced":false}""",
                status = responseStatus,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }

    @After
    fun after(): Unit {
        httpClient.close()
    }

    @Test
    fun `Given a pairing session, When completing it, Then the trusted mobile contract is used`(): Unit = runTest {
        val sessionId: String = "550e8400-e29b-41d4-a716-446655440000"
        val apiClient: ApiClient = KtorApiClient(httpClient, "https://trusted-mobile.authbound.test")
        val result: ApiResponse<PairingCompleteResponse> = apiClient.completePairing(
            sessionId = sessionId,
            body = CompletePairingRequest(
                deviceName = "Pixel Test",
                fcmToken = "fcm-token",
                challengeResponse = "challenge-response"
            ),
            bearerToken = "access-token"
        )
        val request: HttpRequestData = checkNotNull(requested)
        assertTrue(result.isSuccessful)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(
            "https://trusted-mobile.authbound.test/v1/mobile/pairing/complete/$sessionId",
            request.url.toString()
        )
        assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
        assertEquals("v1", request.headers[AUTHBOUND_API_VERSION_HEADER])
        assertEquals("v1.2026-07-10.1", request.headers[AUTHBOUND_CONTRACT_REVISION_HEADER])
    }

    @Test
    fun `Given a rotated FCM token, When updating the current device, Then the authenticated mobile contract is used`(): Unit =
        runTest {
            val apiClient: ApiClient = KtorApiClient(httpClient, "https://trusted-mobile.authbound.test")

            val result: ApiResponse<Unit> = apiClient.updateCurrentDeviceToken(
                body = UpdateDeviceTokenRequest(fcmToken = "rotated-fcm-token"),
                bearerToken = "access-token"
            )

            val request: HttpRequestData = checkNotNull(requested)
            assertTrue(result.isSuccessful)
            assertEquals(HttpMethod.Put, request.method)
            assertEquals(
                "https://trusted-mobile.authbound.test/v1/mobile/pairing/devices/current/token",
                request.url.toString()
            )
            assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
            assertEquals("v1", request.headers[AUTHBOUND_API_VERSION_HEADER])
            assertEquals("v1.2026-07-10.1", request.headers[AUTHBOUND_CONTRACT_REVISION_HEADER])
            val requestBody: String = (request.body as TextContent).text
            assertEquals(
                "rotated-fcm-token",
                Json.parseToJsonElement(requestBody).jsonObject["fcmToken"]?.jsonPrimitive?.content
            )
        }

    @Test
    fun `Given the token is already bound elsewhere, When updating the current device, Then conflict is preserved`(): Unit =
        runTest {
            responseStatus = HttpStatusCode.Conflict
            val apiClient: ApiClient = KtorApiClient(httpClient, "https://trusted-mobile.authbound.test")

            val result: ApiResponse<Unit> = apiClient.updateCurrentDeviceToken(
                body = UpdateDeviceTokenRequest(fcmToken = "conflicting-fcm-token"),
                bearerToken = "access-token"
            )

            assertFalse(result.isSuccessful)
            assertEquals(HttpStatusCode.Conflict.value, result.code())
        }
}
