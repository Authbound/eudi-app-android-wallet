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

package eu.europa.ec.dashboardfeature.repository

import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.ApiResponse
import eu.europa.ec.networklogic.model.request.ActionRespondRequest
import eu.europa.ec.networklogic.model.request.DeviceLinkRequest
import eu.europa.ec.networklogic.model.response.ActionRespondResponse
import eu.europa.ec.networklogic.model.response.DeviceLinkResponse
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ActionsRepositoryImpl].
 *
 * These tests verify the WUA signature generation and validation logic,
 * ensuring that:
 * - Signatures include timestamps that are sent to the backend
 * - Action responses fail properly when WUA key is missing
 * - Device linking fails when wallet is not activated
 *
 * Uses a TestableActionsRepository subclass that overrides getAuthToken()
 * to bypass Supabase extension property mocking difficulties.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class TestActionsRepository {

    @Mock
    private lateinit var apiClient: ApiClient

    @Mock
    private lateinit var supabaseClient: SupabaseClient

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var cryptoController: CryptoController

    private lateinit var closeable: AutoCloseable

    private val testAccessToken = "test-access-token"
    private val testActionId = "action-123"

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
    }

    @After
    fun after() {
        closeable.close()
    }

    // region Accept Action Tests

    @Test
    fun `Given WUA key exists, When acceptAction is called, Then request includes signature and timestamp`() = runTest {
        // Given
        val signatureBytes = "test-signature".toByteArray()
        whenever(cryptoController.signData(any())).thenReturn(signatureBytes)

        val successResponse = ApiResponse.Success(
            body = ActionRespondResponse(success = true, actionId = testActionId),
            code = 200
        )
        whenever(apiClient.respondToAction(eq(testActionId), any(), eq(testAccessToken)))
            .thenReturn(successResponse)

        val testableRepository = createTestableRepository(testAccessToken)

        // When
        val result = testableRepository.acceptAction(testActionId, null, null)

        // Then
        assertTrue("Result should be success", result.isSuccess)

        // Verify the request includes both signature and timestamp
        val requestCaptor = argumentCaptor<ActionRespondRequest>()
        verify(apiClient).respondToAction(eq(testActionId), requestCaptor.capture(), eq(testAccessToken))

        val capturedRequest = requestCaptor.firstValue
        assertNotNull("wuaSignature should not be null", capturedRequest.wuaSignature)
        assertNotNull("signatureTimestamp should not be null", capturedRequest.signatureTimestamp)
        assertTrue("signatureTimestamp should be recent",
            capturedRequest.signatureTimestamp!! > System.currentTimeMillis() - 5000)
        assertEquals("accept", capturedRequest.decision)
    }

    @Test
    fun `Given WUA key does not exist, When acceptAction is called, Then returns failure`() = runTest {
        // Given
        whenever(cryptoController.signData(any())).thenReturn(null)

        val testableRepository = createTestableRepository(testAccessToken)

        // When
        val result = testableRepository.acceptAction(testActionId, null, null)

        // Then
        assertTrue("Result should be failure", result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull("Exception should not be null", exception)
        assertTrue("Error message should mention signing failure",
            exception!!.message?.contains("sign") == true)
    }

    // endregion

    // region Decline Action Tests

    @Test
    fun `Given WUA key exists, When declineAction is called, Then request includes signature and timestamp`() = runTest {
        // Given
        val signatureBytes = "test-signature".toByteArray()
        whenever(cryptoController.signData(any())).thenReturn(signatureBytes)

        val successResponse = ApiResponse.Success(
            body = ActionRespondResponse(success = true, actionId = testActionId),
            code = 200
        )
        whenever(apiClient.respondToAction(eq(testActionId), any(), eq(testAccessToken)))
            .thenReturn(successResponse)

        val testableRepository = createTestableRepository(testAccessToken)

        // When
        val result = testableRepository.declineAction(testActionId, "user_cancelled")

        // Then
        assertTrue("Result should be success", result.isSuccess)

        val requestCaptor = argumentCaptor<ActionRespondRequest>()
        verify(apiClient).respondToAction(eq(testActionId), requestCaptor.capture(), eq(testAccessToken))

        val capturedRequest = requestCaptor.firstValue
        assertNotNull("wuaSignature should not be null", capturedRequest.wuaSignature)
        assertNotNull("signatureTimestamp should not be null", capturedRequest.signatureTimestamp)
        assertEquals("decline", capturedRequest.decision)
        assertEquals("user_cancelled", capturedRequest.declineReason)
    }

    @Test
    fun `Given WUA key does not exist, When declineAction is called, Then returns failure`() = runTest {
        // Given
        whenever(cryptoController.signData(any())).thenReturn(null)

        val testableRepository = createTestableRepository(testAccessToken)

        // When
        val result = testableRepository.declineAction(testActionId, "user_cancelled")

        // Then
        assertTrue("Result should be failure", result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull("Exception should not be null", exception)
        assertTrue("Error message should mention signing failure",
            exception!!.message?.contains("sign") == true)
    }

    // endregion

    // region Device Linking Tests

    @Test
    fun `Given WUA key exists, When linkDevice is called, Then request includes public key`() = runTest {
        // Given
        val testPublicKey = "base64-encoded-public-key"
        whenever(cryptoController.getWuaPublicKey()).thenReturn(testPublicKey)

        val successResponse = ApiResponse.Success(
            body = DeviceLinkResponse(
                success = true,
                deviceId = "device-123",
                linkedAt = "2025-01-15T10:00:00Z"
            ),
            code = 200
        )
        whenever(apiClient.linkDevice(any(), eq(testAccessToken))).thenReturn(successResponse)

        val testableRepository = createTestableRepository(testAccessToken)

        // When
        val result = testableRepository.linkDevice("LINK-CODE", "fcm-token")

        // Then
        assertTrue("Result should be success", result.isSuccess)

        val requestCaptor = argumentCaptor<DeviceLinkRequest>()
        verify(apiClient).linkDevice(requestCaptor.capture(), eq(testAccessToken))

        val capturedRequest = requestCaptor.firstValue
        assertEquals("LINK-CODE", capturedRequest.linkingCode)
        assertEquals(testPublicKey, capturedRequest.wuaPublicKey)
        assertEquals("fcm-token", capturedRequest.fcmToken)
    }

    @Test
    fun `Given WUA key does not exist, When linkDevice is called, Then returns failure`() = runTest {
        // Given
        whenever(cryptoController.getWuaPublicKey()).thenReturn(null)

        val testableRepository = createTestableRepository(testAccessToken)

        // When
        val result = testableRepository.linkDevice("LINK-CODE", "fcm-token")

        // Then
        assertTrue("Result should be failure", result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull("Exception should not be null", exception)
        assertTrue("Error message should mention wallet activation",
            exception!!.message?.contains("activated") == true)
    }

    @Test
    fun `Given not authenticated, When linkDevice is called, Then returns failure`() = runTest {
        // Given - null token simulates unauthenticated state
        val testableRepository = createTestableRepository(null)

        // When
        val result = testableRepository.linkDevice("LINK-CODE", "fcm-token")

        // Then
        assertTrue("Result should be failure", result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull("Exception should not be null", exception)
        assertTrue("Error message should mention authentication",
            exception!!.message?.contains("authenticated") == true)
    }

    // endregion

    // region Signature Payload Format Tests

    @Test
    fun `Given valid input, When signing action, Then payload format is correct`() = runTest {
        // Given
        var capturedPayload: ByteArray? = null
        whenever(cryptoController.signData(any())).thenAnswer { invocation ->
            capturedPayload = invocation.getArgument(0)
            "signature".toByteArray()
        }

        val successResponse = ApiResponse.Success(
            body = ActionRespondResponse(success = true, actionId = testActionId),
            code = 200
        )
        whenever(apiClient.respondToAction(eq(testActionId), any(), eq(testAccessToken)))
            .thenReturn(successResponse)

        val testableRepository = createTestableRepository(testAccessToken)

        // When
        testableRepository.acceptAction(testActionId, null, null)

        // Then
        assertNotNull("Payload should be captured", capturedPayload)
        val payloadString = String(capturedPayload!!)

        // Verify JSON format: {"actionId":"...","decision":"...","timestamp":...}
        val json = org.json.JSONObject(payloadString)
        assertEquals("actionId should match", testActionId, json.getString("actionId"))
        assertEquals("decision should be accept", "accept", json.getString("decision"))

        val timestamp = json.getLong("timestamp")
        assertTrue("Timestamp should be recent",
            timestamp > System.currentTimeMillis() - 5000)
    }

    // endregion

    // region Helper Methods

    /**
     * Creates a testable repository that injects a mock auth token,
     * bypassing the need to mock Supabase extension properties.
     */
    private fun createTestableRepository(mockAuthToken: String?): TestableActionsRepository {
        return TestableActionsRepository(
            apiClient = apiClient,
            supabaseClient = supabaseClient,
            resourceProvider = resourceProvider,
            logController = logController,
            cryptoController = cryptoController,
            mockAuthToken = mockAuthToken
        )
    }

    // endregion

    /**
     * Testable subclass that allows injecting a mock auth token
     * without needing to mock Supabase extension properties.
     */
    private class TestableActionsRepository(
        apiClient: ApiClient,
        supabaseClient: SupabaseClient,
        resourceProvider: ResourceProvider,
        logController: LogController,
        cryptoController: CryptoController,
        private val mockAuthToken: String?
    ) : ActionsRepositoryImpl(
        apiClient = apiClient,
        supabaseClient = supabaseClient,
        resourceProvider = resourceProvider,
        logController = logController,
        cryptoController = cryptoController
    ) {
        override suspend fun getAuthToken(): String? = mockAuthToken
    }
}
