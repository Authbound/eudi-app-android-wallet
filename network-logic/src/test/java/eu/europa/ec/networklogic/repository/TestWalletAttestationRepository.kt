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

package eu.europa.ec.networklogic.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class TestWalletAttestationRepository {

    @Test
    fun `Given a stale WUA response, When requesting an attestation, Then reactivation is required`() =
        runTest {
            val engine = MockEngine {
                respond(
                    content = """{"error":"Wallet reactivation required","code":"WUA_REACTIVATION_REQUIRED"}""",
                    status = HttpStatusCode.Conflict,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Json.toString()
                    )
                )
            }
            val httpClient = HttpClient(engine) {
                install(ContentNegotiation) { json(Json) }
            }
            val repository = WalletAttestationRepositoryImpl(httpClient)

            val result = repository.getWalletAttestation(
                baseUrl = "https://mobile.authbound.test/v1/mobile/wallet-provider",
                request = WalletAttestationRequest(body = buildJsonObject {})
            )

            assertTrue(
                result.exceptionOrNull().toString(),
                result.exceptionOrNull() is WalletReactivationRequiredException
            )
            httpClient.close()
        }
}
