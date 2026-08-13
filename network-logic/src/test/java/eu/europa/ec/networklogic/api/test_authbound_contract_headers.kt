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

import io.ktor.client.request.HttpRequestBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class TestAuthboundContractHeaders {

    @Test
    fun `Given a mobile API request, When contract headers are applied, Then the current mobile revision is sent`(): Unit {
        val requestBuilder: HttpRequestBuilder = HttpRequestBuilder()
        requestBuilder.authboundMobileContractHeaders()
        assertEquals("v1", requestBuilder.headers[AUTHBOUND_API_VERSION_HEADER])
        assertEquals(
            "v1.2026-07-10.1",
            requestBuilder.headers[AUTHBOUND_CONTRACT_REVISION_HEADER]
        )
    }
}
