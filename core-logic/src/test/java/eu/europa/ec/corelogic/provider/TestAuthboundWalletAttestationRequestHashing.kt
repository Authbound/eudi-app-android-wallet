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

package eu.europa.ec.corelogic.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TestAuthboundWalletAttestationRequestHashing {

    @Test
    fun canonicalHash_isStableForEquivalentProofFreeWalletInstanceRequests() {
        val keyMaterial = AuthboundAttestedKeyMaterial(
            keyAlias = "wallet-instance-key",
            jwk = JsonObject(
                linkedMapOf(
                    "kty" to JsonPrimitive("EC"),
                    "crv" to JsonPrimitive("P-256"),
                    "x" to JsonPrimitive("instance-x"),
                    "y" to JsonPrimitive("instance-y"),
                )
            ),
            x5c = JsonArray(
                listOf(
                    JsonPrimitive("cert-1"),
                    JsonPrimitive("cert-2"),
                )
            ),
        )

        val canonicalBody = buildWalletInstanceProofFreeRequest(keyMaterial)
        val reorderedBody = buildJsonObject {
            putJsonObject("key") {
                put("x5c", keyMaterial.x5c)
                put(
                    "jwk",
                    JsonObject(
                        linkedMapOf(
                            "y" to JsonPrimitive("instance-y"),
                            "x" to JsonPrimitive("instance-x"),
                            "crv" to JsonPrimitive("P-256"),
                            "kty" to JsonPrimitive("EC"),
                        )
                    )
                )
            }
        }

        assertEquals(canonicalJsonString(canonicalBody), canonicalJsonString(reorderedBody))
        assertEquals(hashCanonicalJsonObject(canonicalBody), hashCanonicalJsonObject(reorderedBody))
    }

    @Test
    fun walletInstanceSubmittedBody_matchesProofChallengeHashOnceProofIsRemoved() {
        val keyMaterial = AuthboundAttestedKeyMaterial(
            keyAlias = "wallet-instance-key",
            jwk = JsonObject(
                linkedMapOf(
                    "kty" to JsonPrimitive("EC"),
                    "crv" to JsonPrimitive("P-256"),
                    "x" to JsonPrimitive("instance-x"),
                    "y" to JsonPrimitive("instance-y"),
                )
            ),
            x5c = JsonArray(
                listOf(
                    JsonPrimitive("cert-1"),
                    JsonPrimitive("cert-2"),
                )
            ),
        )
        val proofFreeRequest = buildWalletInstanceProofFreeRequest(keyMaterial)
        val submittedRequest = buildWalletInstanceRequest(
            keyMaterial = keyMaterial,
            proof = "wallet-instance-proof",
        )
        val strippedRequest = buildJsonObject {
            putJsonObject("key") {
                val submittedKey = submittedRequest["key"]!!.jsonObject
                put("jwk", submittedKey["jwk"]!!)
                put("x5c", submittedKey["x5c"]!!)
            }
        }

        assertEquals(
            proofFreeRequest["key"]!!.jsonObject["jwk"],
            strippedRequest["key"]!!.jsonObject["jwk"],
        )
        assertEquals(
            proofFreeRequest["key"]!!.jsonObject["x5c"],
            strippedRequest["key"]!!.jsonObject["x5c"],
        )
        assertEquals(
            hashCanonicalJsonObject(proofFreeRequest),
            hashCanonicalJsonObject(strippedRequest),
        )
    }

    @Test
    fun walletUnitSubmittedBody_matchesProofChallengeHashOnceProofsAreRemoved() {
        val firstKey = AuthboundAttestedKeyMaterial(
            keyAlias = "wallet-unit-key-1",
            jwk = JsonObject(
                linkedMapOf(
                    "kty" to JsonPrimitive("EC"),
                    "crv" to JsonPrimitive("P-256"),
                    "x" to JsonPrimitive("unit-x-1"),
                    "y" to JsonPrimitive("unit-y-1"),
                )
            ),
            x5c = JsonArray(
                listOf(
                    JsonPrimitive("cert-1a"),
                    JsonPrimitive("cert-1b"),
                )
            ),
        )
        val secondKey = AuthboundAttestedKeyMaterial(
            keyAlias = "wallet-unit-key-2",
            jwk = JsonObject(
                linkedMapOf(
                    "kty" to JsonPrimitive("EC"),
                    "crv" to JsonPrimitive("P-256"),
                    "x" to JsonPrimitive("unit-x-2"),
                    "y" to JsonPrimitive("unit-y-2"),
                )
            ),
            x5c = JsonArray(
                listOf(
                    JsonPrimitive("cert-2a"),
                    JsonPrimitive("cert-2b"),
                )
            ),
        )
        val proofFreeRequest = buildWalletUnitProofFreeRequest(
            nonce = "nonce-123",
            keys = listOf(firstKey, secondKey),
        )
        val submittedRequest = buildWalletUnitRequest(
            nonce = "nonce-123",
            keys = listOf(firstKey, secondKey),
            proofsByKeyAlias = mapOf(
                firstKey.keyAlias to "proof-1",
                secondKey.keyAlias to "proof-2",
            ),
        )
        val strippedRequest = buildJsonObject {
            put("nonce", submittedRequest["nonce"]!!)
            putJsonArray("keys") {
                submittedRequest["keys"]!!.jsonArray.forEach { submittedKey ->
                    add(
                        buildJsonObject {
                            val keyObject = submittedKey.jsonObject
                            put("jwk", keyObject["jwk"]!!)
                            put("x5c", keyObject["x5c"]!!)
                        }
                    )
                }
            }
        }

        assertEquals(proofFreeRequest["keys"], strippedRequest["keys"])
        assertEquals(
            hashCanonicalJsonObject(proofFreeRequest),
            hashCanonicalJsonObject(strippedRequest),
        )
    }
}
