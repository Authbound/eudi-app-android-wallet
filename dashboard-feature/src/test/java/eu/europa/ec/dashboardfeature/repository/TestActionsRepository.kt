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

import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.businesslogic.controller.device.DeviceController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.model.DeviceInfo
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionStatus
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.ApiResponse
import eu.europa.ec.networklogic.model.request.ActionRespondRequest
import eu.europa.ec.networklogic.model.request.CompletePairingRequest
import eu.europa.ec.networklogic.model.request.DescriptorMapEntryDto
import eu.europa.ec.networklogic.model.request.PresentationSubmissionDto
import eu.europa.ec.networklogic.model.response.ActionDto
import eu.europa.ec.networklogic.model.response.ActionRespondResponse
import eu.europa.ec.networklogic.model.response.ActionsListResponse
import eu.europa.ec.networklogic.model.response.DeviceStatusResponse
import eu.europa.ec.networklogic.model.response.LinkedDeviceInfoDto
import eu.europa.ec.networklogic.model.response.PairingCompleteResponse
import eu.europa.ec.networklogic.model.response.RequesterDto
import eu.europa.ec.notificationlogic.controller.UserScopedPushNotificationController
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ActionsRepositoryImpl].
 *
 * These tests verify the current mobile-backend contract:
 * - action responses send `device_id`, `biometric_verified`, and structured payload data
 * - action responses require a recent local unlock
 * - action list mapping preserves backend titles and payload metadata
 * - device pairing completes through the mobile-backend pairing contract
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class TestActionsRepository {

    @Mock
    private lateinit var apiClient: ApiClient

    @Mock
    private lateinit var supabaseClient: SupabaseClient

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var deviceController: DeviceController

    @Mock
    private lateinit var localUnlockTracker: LocalUnlockTracker

    @Mock
    private lateinit var userScopedPushNotificationController: UserScopedPushNotificationController

    private lateinit var closeable: AutoCloseable

    private val testAccessToken = "test-access-token"
    private val testActionId = "action-123"
    private val testDeviceId = "android-device-123"

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(deviceController.getDeviceInfo()).thenReturn(
            DeviceInfo(
                deviceId = testDeviceId,
                deviceName = "Pixel Test",
                deviceModel = "Google Pixel Test",
                deviceOs = "Android 15",
                deviceOsVersion = "35",
                securityPatchLevel = "2026-01-01",
                hasSecureElement = true,
                hasHardwareKeystore = true,
                hasStrongBox = true,
                attestationSupported = true,
                deviceVerifiedBoot = true,
                playProtectVerified = true,
                hasBiometricHardware = true
            )
        )
        whenever(localUnlockTracker.isUnlocked()).thenReturn(true)
    }

    @After
    fun after() {
        closeable.close()
    }

    // region Accept Action Tests

    @Test
    fun `Given wallet is unlocked, When acceptAction is called, Then request includes device proof and verification payload`() = runTest {
        // Given
        val successResponse = ApiResponse.Success(
            body = ActionRespondResponse(
                id = testActionId,
                status = "accepted",
                respondedAt = "2026-03-09T10:00:00Z"
            ),
            code = 200
        )
        whenever(apiClient.respondToAction(eq(testActionId), any(), eq(testAccessToken)))
            .thenReturn(successResponse)

        val presentationSubmission = PresentationSubmissionDto(
            id = "presentation-submission-1",
            definitionId = "presentation-definition-1",
            descriptorMap = listOf(
                DescriptorMapEntryDto(
                    id = "pid",
                    format = "dc+sd-jwt",
                    path = "$.verifiablePresentation[0]"
                )
            )
        )

        val testableRepository = createTestableRepository(testAccessToken, "user-123")

        // When
        val result = testableRepository.acceptAction(
            actionId = testActionId,
            vpToken = "vp-token-123",
            presentationSubmission = presentationSubmission
        )

        // Then
        assertTrue("Result should be success", result.isSuccess)

        val requestCaptor = argumentCaptor<ActionRespondRequest>()
        verify(apiClient).respondToAction(eq(testActionId), requestCaptor.capture(), eq(testAccessToken))

        val capturedRequest = requestCaptor.firstValue
        assertEquals("accept", capturedRequest.response)
        assertEquals(testDeviceId, capturedRequest.deviceId)
        assertTrue("biometricVerified should be true", capturedRequest.biometricVerified)
        assertNotNull("payload should not be null", capturedRequest.payload)
        assertEquals(
            "vp-token-123",
            capturedRequest.payload?.get("vp_token")?.jsonPrimitive?.content
        )
        assertEquals(
            "presentation-submission-1",
            capturedRequest.payload
                ?.get("presentation_submission")
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content
        )
    }

    @Test
    fun `Given wallet is locked, When acceptAction is called, Then returns failure without calling the API`() = runTest {
        // Given
        whenever(localUnlockTracker.isUnlocked()).thenReturn(false)
        val testableRepository = createTestableRepository(testAccessToken, "user-123")

        // When
        val result = testableRepository.acceptAction(testActionId, null, null)

        // Then
        assertTrue("Result should be failure", result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull("Exception should not be null", exception)
        assertTrue(
            "Error message should mention unlocking the wallet",
            exception!!.message?.contains("Unlock", ignoreCase = true) == true
        )
        verify(apiClient, never()).respondToAction(any(), any(), any())
    }

    // endregion

    // region Decline Action Tests

    @Test
    fun `Given wallet is unlocked, When declineAction is called, Then request includes device proof and decline payload`() = runTest {
        // Given
        val successResponse = ApiResponse.Success(
            body = ActionRespondResponse(
                id = testActionId,
                status = "declined",
                respondedAt = "2026-03-09T10:00:00Z"
            ),
            code = 200
        )
        whenever(apiClient.respondToAction(eq(testActionId), any(), eq(testAccessToken)))
            .thenReturn(successResponse)

        val testableRepository = createTestableRepository(testAccessToken, "user-123")

        // When
        val result = testableRepository.declineAction(testActionId, "user_cancelled")

        // Then
        assertTrue("Result should be success", result.isSuccess)

        val requestCaptor = argumentCaptor<ActionRespondRequest>()
        verify(apiClient).respondToAction(eq(testActionId), requestCaptor.capture(), eq(testAccessToken))

        val capturedRequest = requestCaptor.firstValue
        assertEquals("decline", capturedRequest.response)
        assertEquals(testDeviceId, capturedRequest.deviceId)
        assertTrue("biometricVerified should be true", capturedRequest.biometricVerified)
        assertEquals(
            "user_cancelled",
            capturedRequest.payload?.get("reason")?.jsonPrimitive?.content
        )
    }

    // endregion

    // region Fetch Actions Tests

    @Test
    fun `Given backend action list response, When fetchActions is called, Then backend title and payload metadata are preserved`() = runTest {
        // Given
        val successResponse = ApiResponse.Success(
            body = ActionsListResponse(
                data = listOf(
                    ActionDto(
                        id = testActionId,
                        type = "VERIFY_REQUEST",
                        title = "Age Verification Required",
                        requester = RequesterDto(
                            name = "Acme Store",
                            logoUrl = "https://example.com/logo.png"
                        ),
                        description = "Please verify you are over 18",
                        priority = "high",
                        createdAt = "2026-03-09T10:00:00Z",
                        expiresAt = "2026-03-10T10:00:00Z",
                        payload = buildJsonObject {
                            put("policy_id", JsonPrimitive("pol_age_verification"))
                            put("requested_attributes", buildJsonArray {
                                add(JsonPrimitive("age_over_18"))
                            })
                        }
                    )
                ),
                hasMore = false
            ),
            code = 200
        )
        whenever(apiClient.getActions(eq(testAccessToken), eq("pending"), eq(null)))
            .thenReturn(successResponse)

        val testableRepository = createTestableRepository(testAccessToken, "user-123")

        // When
        val result = testableRepository.fetchActions(status = ActionStatus.PENDING)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        val actions = result.getOrNull()
        assertEquals(1, actions?.size)

        val action = actions?.first()
        assertEquals("Age Verification Required", action?.title)
        assertEquals("Acme Store", action?.requesterName)
        assertEquals(ActionStatus.PENDING, action?.status)
        assertEquals("pol_age_verification", action?.metadata?.get("policy_id"))
        assertTrue(
            "requested_attributes metadata should include requested claim",
            action?.metadata?.get("requested_attributes")?.contains("age_over_18") == true
        )
        assertEquals("high", action?.metadata?.get("priority"))
    }

    // endregion

    // region Device Linking Tests

    @Test
    fun `Given pairing QR payload, When linkDevice is called, Then request completes the pairing session`() = runTest {
        // Given
        val sessionId = "550e8400-e29b-41d4-a716-446655440000"
        val pairingPayload = """
            {"t":"authbound_pair","v":1,"sid":"$sessionId","cr":"challenge-response","url":"https://attacker.example/collect","exp":4102444800}
        """.trimIndent()
        whenever(userScopedPushNotificationController.registerForPushNotifications(eq("user-123")))
            .thenReturn(Result.success("fcm-token-1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_=:-fcm-token-1234567890"))

        val successResponse = ApiResponse.Success(
            body = PairingCompleteResponse(
                success = true,
                deviceId = "device-123",
                previousDeviceReplaced = false
            ),
            code = 200
        )
        whenever(apiClient.completePairing(eq(sessionId), any(), eq(testAccessToken)))
            .thenReturn(successResponse)
        whenever(apiClient.getDeviceStatus(eq(testAccessToken))).thenReturn(
            ApiResponse.Success(
                body = DeviceStatusResponse(
                    hasLinkedDevice = true,
                    device = LinkedDeviceInfoDto(
                        deviceId = "device-123",
                        deviceName = "Pixel Test",
                        deviceModel = "Google Pixel Test",
                        linkedAt = "2025-01-15T10:00:00Z",
                        lastActiveAt = null
                    )
                ),
                code = 200
            )
        )

        val testableRepository = createTestableRepository(testAccessToken, "user-123")

        // When
        val result = testableRepository.linkDevice(pairingPayload)

        // Then
        assertTrue("Result should be success", result.isSuccess)

        val requestCaptor = argumentCaptor<CompletePairingRequest>()
        verify(apiClient).completePairing(
            eq(sessionId),
            requestCaptor.capture(),
            eq(testAccessToken)
        )
        verify(apiClient, never()).completePairing(eq("https://attacker.example/collect"), any(), any())

        val capturedRequest = requestCaptor.firstValue
        assertEquals("Pixel Test", capturedRequest.deviceName)
        assertEquals("Google Pixel Test", capturedRequest.deviceModel)
        assertEquals("challenge-response", capturedRequest.challengeResponse)
        assertTrue("FCM token should be propagated", capturedRequest.fcmToken.startsWith("fcm-token-1234567890"))
    }

    @Test
    fun `Given pairing completion succeeds, When device status refresh fails, Then linkDevice still returns success`() = runTest {
        // Given
        val pairingPayload = """
            {"t":"authbound_pair","v":1,"sid":"550e8400-e29b-41d4-a716-446655440000","cr":"challenge-response","url":"https://staging.authbound.io/api/pairing/complete/550e8400-e29b-41d4-a716-446655440000","exp":4102444800}
        """.trimIndent()
        whenever(userScopedPushNotificationController.registerForPushNotifications(eq("user-123")))
            .thenReturn(Result.success("fcm-token-1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_=:-fcm-token-1234567890"))
        whenever(apiClient.completePairing(eq("550e8400-e29b-41d4-a716-446655440000"), any(), eq(testAccessToken)))
            .thenReturn(
                ApiResponse.Success(
                    body = PairingCompleteResponse(
                        success = true,
                        deviceId = "device-123",
                        previousDeviceReplaced = false
                    ),
                    code = 200
                )
            )
        whenever(apiClient.getDeviceStatus(eq(testAccessToken))).thenReturn(
            ApiResponse.Error(
                code = 504,
                message = "Gateway Timeout"
            )
        )

        val testableRepository = createTestableRepository(testAccessToken, "user-123")

        // When
        val result = testableRepository.linkDevice(pairingPayload)

        // Then
        assertTrue("Result should be success", result.isSuccess)
        val linkedDevice = result.getOrNull()
        assertEquals("device-123", linkedDevice?.deviceId)
        assertEquals("Pixel Test", linkedDevice?.deviceName)
        assertEquals("Google Pixel Test", linkedDevice?.deviceModel)
    }

    @Test
    fun `Given pairing QR payload is invalid, When linkDevice is called, Then returns failure`() = runTest {
        // Given
        val testableRepository = createTestableRepository(testAccessToken, "user-123")

        // When
        val result = testableRepository.linkDevice("not-json")

        // Then
        assertTrue("Result should be failure", result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull("Exception should not be null", exception)
        assertTrue(
            "Error message should mention invalid QR code",
            exception!!.message?.contains("Invalid pairing QR code") == true
        )
    }

    @Test
    fun `Given not authenticated, When linkDevice is called, Then returns failure`() = runTest {
        // Given
        val testableRepository = createTestableRepository(null, "user-123")

        // When
        val result = testableRepository.linkDevice("""{"t":"authbound_pair","v":1,"sid":"550e8400-e29b-41d4-a716-446655440000","cr":"challenge-response","url":"https://app.authbound.io/api/pairing/complete/550e8400-e29b-41d4-a716-446655440000","exp":4102444800}""")

        // Then
        assertTrue("Result should be failure", result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull("Exception should not be null", exception)
        assertTrue(
            "Error message should mention authentication",
            exception!!.message?.contains("authenticated") == true
        )
    }

    // endregion

    /**
     * Creates a testable repository that injects a mock auth token,
     * bypassing the need to mock Supabase extension properties.
     */
    private fun createTestableRepository(
        mockAuthToken: String?,
        mockUserId: String?
    ): TestableActionsRepository {
        return TestableActionsRepository(
            apiClient = apiClient,
            supabaseClient = supabaseClient,
            logController = logController,
            deviceController = deviceController,
            localUnlockTracker = localUnlockTracker,
            userScopedPushNotificationController = userScopedPushNotificationController,
            mockAuthToken = mockAuthToken,
            mockUserId = mockUserId
        )
    }

    /**
     * Testable subclass that allows injecting a mock auth token
     * without needing to mock Supabase extension properties.
     */
    private class TestableActionsRepository(
        apiClient: ApiClient,
        supabaseClient: SupabaseClient,
        logController: LogController,
        deviceController: DeviceController,
        localUnlockTracker: LocalUnlockTracker,
        userScopedPushNotificationController: UserScopedPushNotificationController,
        private val mockAuthToken: String?,
        private val mockUserId: String?
    ) : ActionsRepositoryImpl(
        apiClient = apiClient,
        supabaseClient = supabaseClient,
        logController = logController,
        deviceController = deviceController,
        localUnlockTracker = localUnlockTracker,
        userScopedPushNotificationController = userScopedPushNotificationController
    ) {
        override suspend fun getAuthToken(): String? = mockAuthToken

        override suspend fun getCurrentUserId(): String? = mockUserId
    }
}
