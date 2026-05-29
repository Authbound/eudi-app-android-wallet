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
import eu.europa.ec.dashboardfeature.model.verification.VerificationTemplateType
import eu.europa.ec.dashboardfeature.model.verification.VerificationDraftAttribute
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipient
import eu.europa.ec.dashboardfeature.model.verification.VerificationRecipientContactType
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.ApiResponse
import eu.europa.ec.networklogic.model.request.CreateVerificationSessionRequest
import eu.europa.ec.networklogic.model.response.CreateVerificationSessionResponse
import eu.europa.ec.networklogic.model.response.StartVerificationInvitationResponse
import eu.europa.ec.networklogic.model.response.VerificationClientActionDto
import eu.europa.ec.networklogic.model.response.VerificationPublicSessionDto
import eu.europa.ec.networklogic.model.response.VerificationPublicSessionResponse
import eu.europa.ec.networklogic.model.response.VerificationRecipientDto
import eu.europa.ec.networklogic.model.response.VerificationRequestedAttributeDto
import eu.europa.ec.networklogic.model.response.VerificationSessionDetailResponse
import eu.europa.ec.networklogic.model.response.VerificationSessionDto
import eu.europa.ec.networklogic.model.response.VerificationSessionListItemDto
import eu.europa.ec.networklogic.model.response.VerificationSessionsListResponse
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import io.github.jan.supabase.SupabaseClient
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

class TestVerificationRepository {

    @Mock
    private lateinit var apiClient: ApiClient

    @Mock
    private lateinit var supabaseClient: SupabaseClient

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    private val authToken = "requester-token"

    private lateinit var closeable: AutoCloseable
    private lateinit var repository: TestableVerificationRepository

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.getString(any<Int>())).thenReturn("Attribute")
        repository = TestableVerificationRepository(
            apiClient = apiClient,
            supabaseClient = supabaseClient,
            resourceProvider = resourceProvider,
            authToken = authToken
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
    fun `Given newly created invitation, When sharing reloads by id, Then create-time public URL is retained`() =
        runTest {
            val sessionId = "550e8400-e29b-41d4-a716-446655440000"
            val publicUrl = "https://app.authbound.test/verify/$sessionId#token=recipient-token"

            whenever(apiClient.createVerificationSession(any<CreateVerificationSessionRequest>(), eq(authToken)))
                .thenReturn(
                    ApiResponse.Success(
                        createSessionResponse(
                            sessionId = sessionId,
                            publicUrl = publicUrl
                        )
                    )
                )
            whenever(apiClient.getVerificationSession(sessionId, authToken)).thenReturn(
                ApiResponse.Success(detailResponse(sessionId = sessionId, publicUrl = null))
            )
            whenever(apiClient.getVerificationSessions(authToken)).thenReturn(
                ApiResponse.Success(
                    VerificationSessionsListResponse(
                        invitations = listOf(listItem(sessionId = sessionId, publicUrl = null))
                    )
                )
            )

            val created = repository.createVerificationSession(
                purpose = "Verify age",
                attributes = listOf(selectedAgeAttribute()),
                recipients = listOf(linkRecipient())
            ).getOrThrow()
            val reloaded = repository.getVerificationSession(sessionId).getOrThrow()

            assertEquals(publicUrl, created.publicUrl)
            assertEquals(publicUrl, reloaded.publicUrl)
        }

    @Test
    fun `Given invitation templates, When loaded, Then only backend supported attributes are offered`() =
        runTest {
            val templates = repository.getVerificationTemplates()
            val identityTemplate = templates.single {
                it.type == VerificationTemplateType.IDENTITY_VERIFICATION
            }
            val offeredAttributes = templates.flatMap { it.attributes }.toSet()

            assertEquals(listOf("full_name", "date_of_birth"), identityTemplate.attributes)
            assertFalse(offeredAttributes.contains("personal_id"))
            assertFalse(offeredAttributes.contains("address"))
            assertFalse(offeredAttributes.contains("phone"))
        }

    @Test
    fun `Given requester invitation recipient metadata, When sessions refresh, Then metadata is retained`() =
        runTest {
            val sessionId = "550e8400-e29b-41d4-a716-446655440000"
            whenever(apiClient.getVerificationSessions(authToken)).thenReturn(
                ApiResponse.Success(
                    VerificationSessionsListResponse(
                        invitations = listOf(
                            listItem(sessionId = sessionId, publicUrl = null).copy(
                                recipients = listOf(
                                    VerificationRecipientDto(
                                        id = "recipient-1",
                                        contactType = "link",
                                        status = "verification_started",
                                        verificationId = "verification-1",
                                        verificationStartedAt = "2026-05-26T08:05:00Z",
                                        updatedAt = "2026-05-26T08:06:00Z"
                                    )
                                )
                            )
                        )
                    )
                )
            )

            repository.refreshVerificationSessions().getOrThrow()

            val recipient = repository.getVerificationSessions().first().single().recipients.single()
            assertEquals("recipient-1", recipient.id)
            assertEquals("verification-1", recipient.verificationId)
            assertEquals(1779782700000L, recipient.verificationStartedAt)
        }

    @Test
    fun `Given multi-recipient invitation creation, When mapped, Then recipient public URLs stay per recipient`() =
        runTest {
            val sessionId = "550e8400-e29b-41d4-a716-446655440000"
            val firstUrl = "https://app.authbound.test/verify/$sessionId#token=first-recipient-token"
            val secondUrl = "https://app.authbound.test/verify/$sessionId#token=second-recipient-token"
            whenever(apiClient.createVerificationSession(any<CreateVerificationSessionRequest>(), eq(authToken)))
                .thenReturn(
                    ApiResponse.Success(
                        CreateVerificationSessionResponse(
                            invitationId = sessionId,
                            status = "created",
                            createdAt = "2026-05-26T08:00:00Z",
                            updatedAt = "2026-05-26T08:00:00Z",
                            expiresAt = "2026-05-27T08:00:00Z",
                            recipients = listOf(
                                VerificationRecipientDto(
                                    id = "recipient-1",
                                    contactType = "email",
                                    value = "first@example.com",
                                    status = "pending",
                                    publicUrl = firstUrl
                                ),
                                VerificationRecipientDto(
                                    id = "recipient-2",
                                    contactType = "email",
                                    value = "second@example.com",
                                    status = "pending",
                                    publicUrl = secondUrl
                                )
                            ),
                            creditsReserved = 2,
                            creditsRemaining = 8
                        )
                    )
                )

            val created = repository.createVerificationSession(
                purpose = "Verify age",
                attributes = listOf(selectedAgeAttribute()),
                recipients = listOf(
                    VerificationRecipient(
                        contactType = VerificationRecipientContactType.EMAIL,
                        value = "first@example.com"
                    ),
                    VerificationRecipient(
                        contactType = VerificationRecipientContactType.EMAIL,
                        value = "second@example.com"
                    )
                )
            ).getOrThrow()

            assertNull(created.publicUrl)
            assertEquals(firstUrl, created.recipients[0].publicUrl)
            assertEquals(secondUrl, created.recipients[1].publicUrl)
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
            assertEquals("", result.publicUrl)
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
            )
        )
    }

    private fun createSessionResponse(
        sessionId: String,
        publicUrl: String?
    ): CreateVerificationSessionResponse {
        return CreateVerificationSessionResponse(
            invitationId = sessionId,
            status = "created",
            createdAt = "2026-05-26T08:00:00Z",
            updatedAt = "2026-05-26T08:00:00Z",
            expiresAt = "2026-05-27T08:00:00Z",
            recipients = listOf(
                VerificationRecipientDto(
                    contactType = "link",
                    status = "pending",
                    publicUrl = publicUrl
                )
            ),
            creditsReserved = 1,
            creditsRemaining = 9
        )
    }

    private fun detailResponse(
        sessionId: String,
        publicUrl: String?
    ): VerificationSessionDetailResponse {
        return VerificationSessionDetailResponse(
            session = VerificationSessionDto(
                id = sessionId,
                status = "created",
                purpose = "Verify age",
                requestedAttributes = mapOf(
                    "age_over_18" to VerificationRequestedAttributeDto(type = "age_over_18")
                ),
                recipients = listOf(
                    VerificationRecipientDto(
                        contactType = "link",
                        status = "pending",
                        publicUrl = publicUrl
                    )
                ),
                createdAt = "2026-05-26T08:00:00Z",
                updatedAt = "2026-05-26T08:00:00Z",
                expiresAt = "2026-05-27T08:00:00Z"
            )
        )
    }

    private fun listItem(
        sessionId: String,
        publicUrl: String?
    ): VerificationSessionListItemDto {
        return VerificationSessionListItemDto(
            id = sessionId,
            status = "created",
            purpose = "Verify age",
            requestedAttributes = mapOf(
                "age_over_18" to VerificationRequestedAttributeDto(type = "age_over_18")
            ),
            recipients = listOf(
                VerificationRecipientDto(
                    contactType = "link",
                    status = "pending",
                    publicUrl = publicUrl
                )
            ),
            createdAt = "2026-05-26T08:00:00Z",
            updatedAt = "2026-05-26T08:00:00Z",
            expiresAt = "2026-05-27T08:00:00Z"
        )
    }

    private fun selectedAgeAttribute(): VerificationDraftAttribute {
        return VerificationDraftAttribute(
            key = "age_over_18",
            label = "Age over 18",
            description = "Age proof",
            requiresExpectedValue = false,
            selected = true
        )
    }

    private fun linkRecipient(): VerificationRecipient {
        return VerificationRecipient(
            contactType = VerificationRecipientContactType.LINK,
            value = ""
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

    private class TestableVerificationRepository(
        apiClient: ApiClient,
        supabaseClient: SupabaseClient,
        resourceProvider: ResourceProvider,
        private val authToken: String?
    ) : VerificationRepositoryImpl(
        apiClient = apiClient,
        supabaseClient = supabaseClient,
        resourceProvider = resourceProvider
    ) {
        override suspend fun getAuthToken(): String? = authToken
    }
}
