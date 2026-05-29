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

package eu.europa.ec.uilogic.navigation.helper

import android.net.Uri
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.uilogic.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class DeepLinkHelperTest {

    @Test
    fun `canonical verification portal link maps to verification session deep link`() {
        val sessionId = "550e8400-e29b-41d4-a716-446655440000"
        val accessToken = "123e4567-e89b-12d3-a456-426614174000"
        val uri = Uri.parse(
            "https://${BuildConfig.VERIFICATION_PORTAL_HOST}/verify/$sessionId#token=$accessToken"
        )

        val action = hasDeepLink(uri)
        val parsed = parseVerificationSessionDeepLink(uri)

        assertEquals(DeepLinkType.VERIFICATION_SESSION, action?.type)
        assertNotNull(parsed)
        assertEquals(sessionId, parsed?.sessionId)
        assertEquals(accessToken, parsed?.accessToken)
    }

    @Test
    fun `verification portal link accepts opaque hex access tokens`() {
        val sessionId = "550e8400-e29b-41d4-a716-446655440000"
        val accessToken = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val uri = Uri.parse(
            "https://${BuildConfig.VERIFICATION_PORTAL_HOST}/verify/$sessionId#token=$accessToken"
        )

        val parsed = parseVerificationSessionDeepLink(uri)

        assertNotNull(parsed)
        assertEquals(sessionId, parsed?.sessionId)
        assertEquals(accessToken, parsed?.accessToken)
    }

    @Test
    fun `verification portal link without token is treated as external`() {
        val uri = Uri.parse(
            "https://${BuildConfig.VERIFICATION_PORTAL_HOST}/verify/550e8400-e29b-41d4-a716-446655440000"
        )

        assertNull(parseVerificationSessionDeepLink(uri))
        assertEquals(DeepLinkType.EXTERNAL, hasDeepLink(uri)?.type)
    }

    @Test
    fun `verification portal link with query token is rejected instead of opened externally`() {
        val sessionId = "550e8400-e29b-41d4-a716-446655440000"

        listOf(
            "token",
            "access_token",
            "verification_token",
            "AUTHBOUND_VERIFICATION_TOKEN",
            "x-authbound-verification-token",
            "public_token"
        ).forEach { tokenKey ->
            val uri = Uri.parse(
                "https://${BuildConfig.VERIFICATION_PORTAL_HOST}/verify/$sessionId?$tokenKey=recipient-secret"
            )

            assertNull(parseVerificationSessionDeepLink(uri))
            assertNull(hasDeepLink(uri))
        }
    }

    @Test
    fun `verification link on another host is treated as external`() {
        val uri = Uri.parse(
            "https://example.com/verify/550e8400-e29b-41d4-a716-446655440000#token=123e4567-e89b-12d3-a456-426614174000"
        )

        assertNull(parseVerificationSessionDeepLink(uri))
        assertEquals(DeepLinkType.EXTERNAL, hasDeepLink(uri)?.type)
    }
}
