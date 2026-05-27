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

package eu.europa.ec.dashboardfeature.repository

import eu.europa.ec.dashboardfeature.model.verification.VerificationSession
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.ApiResponse
import eu.europa.ec.networklogic.model.response.StartVerificationInvitationResponse
import eu.europa.ec.networklogic.model.response.VerificationClientActionDto
import eu.europa.ec.networklogic.model.response.VerificationPublicSessionDto
import eu.europa.ec.networklogic.model.response.VerificationPublicSessionResponse
import eu.europa.ec.networklogic.model.response.VerificationRecipientDto
import eu.europa.ec.networklogic.model.response.VerificationRequestedAttributeDto
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import io.github.jan.supabase.SupabaseClient
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class TestVerificationRepository {

    @Mock
    private lateinit var apiClient: ApiClient

    @Mock
    private lateinit var supabaseClient: SupabaseClient

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    private lateinit var closeable: AutoCloseable
    private lateinit var repository: VerificationRepository

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.getString(any<Int>())).thenReturn("Attribute")
        repository = VerificationRepositoryImpl(
            apiClient = apiClient,
            supabaseClient = supabaseClient,
            resourceProvider = resourceProvider
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `Given create response public URL and requester detail omits it, When merged, Then sharing keeps link`() {
        val sessionId = "550e8400-e29b-41d4-a716-446655440000"
        val publicUrl = "https://app.authbound.test/verify/$sessionId#token=recipient-token"
        val created = verificationSession(
            sessionId = sessionId,
            status = "created",
            publicUrl = publicUrl,
            creditsDeducted = 1,
            creditsRemaining = 9
        )
        val detail = verificationSession(
            sessionId = sessionId,
            status = "active",
            publicUrl = null
        )

        val result = mergeCreateInvitationShareTarget(detail = detail, created = created)

        assertEquals("active", result.status)
        assertEquals(publicUrl, result.publicUrl)
        assertEquals(1, result.creditsDeducted)
        assertEquals(9, result.creditsRemaining)
    }

    @Test
    fun `Given public invitation response, When mapped, Then recipient status owns display state`() =
        runTest {
            val sessionId = "550e8400-e29b-41d4-a716-446655440000"
            val accessToken = "recipient-token"
            whenever(apiClient.getPublicVerificationSession(sessionId, accessToken)).thenReturn(
                ApiResponse.Success(publicSessionResponse(sessionId = sessionId, recipientStatus = "opened"))
            )

            val result = repository.getPublicVerificationSession(sessionId, accessToken).getOrThrow()

            assertEquals("opened", result.status)
            assertNull(result.verificationId)
            assertNull(result.requestedAttributes.single().expectedValue)
        }

    @Test
    fun `Given start response, When mapped, Then backend verification id and request uri are retained`() =
        runTest {
            val sessionId = "550e8400-e29b-41d4-a716-446655440000"
            val accessToken = "recipient-token"
            whenever(apiClient.getPublicVerificationSession(sessionId, accessToken)).thenReturn(
                ApiResponse.Success(publicSessionResponse(sessionId = sessionId, recipientStatus = "opened"))
            )
            whenever(apiClient.startPublicVerificationSession(sessionId, accessToken)).thenReturn(
                ApiResponse.Success(
                    StartVerificationInvitationResponse(
                        invitationId = sessionId,
                        recipientId = "recipient-1",
                        verificationId = "verification-1",
                        clientAction = VerificationClientActionDto(
                            kind = "redirect",
                            data = "openid4vp://verify?request_uri=https%3A%2F%2Fapi.authbound.test",
                            expiresAt = "2026-05-26T09:00:00Z"
                        )
                    )
                )
            )

            val result = repository.startPublicVerificationSession(sessionId, accessToken).getOrThrow()

            assertEquals("verification_started", result.status)
            assertEquals("verification-1", result.verificationId)
            assertEquals(
                "openid4vp://verify?request_uri=https%3A%2F%2Fapi.authbound.test",
                result.requestUri
            )
        }

    private fun publicSessionResponse(
        sessionId: String,
        recipientStatus: String
    ): VerificationPublicSessionResponse {
        return VerificationPublicSessionResponse(
            session = VerificationPublicSessionDto(
                id = sessionId,
                status = "active",
                purpose = "Verify age",
                requestedAttributes = mapOf(
                    "full_name" to VerificationRequestedAttributeDto(
                        type = "full_name",
                        expectedValue = "Alice Example"
                    )
                ),
                createdAt = "2026-05-26T08:00:00Z",
                expiresAt = "2026-05-27T08:00:00Z"
            ),
            recipient = VerificationRecipientDto(
                contactType = "link",
                status = recipientStatus
            ),
            publicUrl = "https://app.authbound.test/verify/$sessionId#token=recipient-token"
        )
    }

    private fun verificationSession(
        sessionId: String,
        status: String,
        publicUrl: String?,
        creditsDeducted: Int? = null,
        creditsRemaining: Int? = null
    ): VerificationSession {
        return VerificationSession(
            id = sessionId,
            status = status,
            purpose = "Verify age",
            createdAt = 0,
            updatedAt = 0,
            expiresAt = null,
            requestedAttributes = emptyList(),
            publicUrl = publicUrl,
            creditsDeducted = creditsDeducted,
            creditsRemaining = creditsRemaining
        )
    }
}
