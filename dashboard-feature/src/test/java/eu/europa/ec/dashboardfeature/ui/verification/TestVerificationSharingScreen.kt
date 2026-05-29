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

package eu.europa.ec.dashboardfeature.ui.verification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestVerificationSharingScreen {

    @Test
    fun `display public verification url strips recipient token fragment`() {
        val displayUrl = displayPublicVerificationUrl(
            "https://app.authbound.io/verify/550e8400-e29b-41d4-a716-446655440000#token=recipient-secret"
        )

        assertEquals(
            "https://app.authbound.io/verify/550e8400-e29b-41d4-a716-446655440000",
            displayUrl
        )
    }

    @Test
    fun `display public verification url strips token query aliases`() {
        val displayUrl = displayPublicVerificationUrl(
            "https://app.authbound.io/verify/550e8400-e29b-41d4-a716-446655440000?verification_token=recipient-secret"
        )

        assertEquals(
            "https://app.authbound.io/verify/550e8400-e29b-41d4-a716-446655440000",
            displayUrl
        )
    }

    @Test
    fun `display public verification url treats blank urls as missing`() {
        assertNull(displayPublicVerificationUrl(" "))
    }
}
