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

package eu.europa.ec.commonfeature.logic.qr

import eu.europa.ec.commonfeature.config.IssuanceFlowType
import eu.europa.ec.testlogic.base.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class QrPayloadRoutingClassifierTest {

    @Test
    fun `openid-credential-offer scheme maps to issuance`() {
        val uri = "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example%2Foffer"
        val route = QrPayloadRoutingClassifier.classify(uri)
        assertTrue(route is UniversalScanRoute.Issuance)
    }

    @Test
    fun `eudi-openid4vp scheme maps to presentation`() {
        val uri =
            "eudi-openid4vp://verifier.example?client_id=verifier.example&request_uri=https%3A%2F%2Fverifier.example%2Frequest.jwt"
        assertEquals(UniversalScanRoute.Presentation, QrPayloadRoutingClassifier.classify(uri))
    }

    @Test
    fun `https URL with credential_offer query maps to issuance`() {
        val uri = "https://issuer.example/path?credential_offer=%7B%22credential_issuer%22%3A%22https%3A%2F%2Fi%22%7D"
        val route = QrPayloadRoutingClassifier.classify(uri)
        assertTrue(route is UniversalScanRoute.Issuance)
    }

    @Test
    fun `https URL with request_uri maps to presentation`() {
        val uri = "https://verifier.example/wallet?request_uri=https%3A%2F%2Fverifier.example%2Fjwt"
        assertEquals(UniversalScanRoute.Presentation, QrPayloadRoutingClassifier.classify(uri))
    }

    @Test
    fun `https URL without strong signals defaults to presentation`() {
        val uri = "https://verifier.example/landing"
        assertEquals(UniversalScanRoute.Presentation, QrPayloadRoutingClassifier.classify(uri))
    }

    @Test
    fun `authbound pid callback is rejected for universal scanner`() {
        val uri = "authbound://authboundpid/callback?nonce=abc&status=success"
        assertNull(QrPayloadRoutingClassifier.classify(uri))
    }

    @Test
    fun `issuance uses ExtraDocument flow for dashboard parity`() {
        val uri = "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fx"
        val route = QrPayloadRoutingClassifier.classify(uri) as UniversalScanRoute.Issuance
        assertTrue(route.flowType is IssuanceFlowType.ExtraDocument)
    }
}
