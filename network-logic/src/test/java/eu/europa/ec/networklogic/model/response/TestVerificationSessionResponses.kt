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

package eu.europa.ec.networklogic.model.response

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TestVerificationSessionResponses {
    private val json: Json = Json { ignoreUnknownKeys = true }

    @Test
    fun `Given invitation recipient payload, When decoding, Then verification metadata is retained`() {
        val recipient = json.decodeFromString(
            VerificationRecipientDto.serializer(),
            """
                {
                  "id": "recipient-1",
                  "contactType": "link",
                  "status": "verification_started",
                  "verificationId": "verification-1",
                  "verificationStartedAt": "2026-05-26T08:05:00Z"
                }
            """.trimIndent()
        )

        assertEquals("recipient-1", recipient.id)
        assertEquals("verification-1", recipient.verificationId)
        assertEquals("2026-05-26T08:05:00Z", recipient.verificationStartedAt)
    }

    @Test
    fun `Given notification delivery payload, When decoding, Then disabled provider and error code are retained`() {
        val result = json.decodeFromString(
            VerificationNotificationResultDto.serializer(),
            """
                {
                  "recipient": "alice@example.com",
                  "channel": "email",
                  "success": false,
                  "providerDisabled": true,
                  "errorCode": "EMAIL_NOT_CONFIGURED",
                  "error": "Email provider is disabled"
                }
            """.trimIndent()
        )

        assertEquals(true, result.providerDisabled)
        assertEquals("EMAIL_NOT_CONFIGURED", result.errorCode)
    }
}
