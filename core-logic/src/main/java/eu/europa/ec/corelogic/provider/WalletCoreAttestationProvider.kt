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

import android.util.Log
import android.util.Base64
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.impl.ECDSA
import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepository
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.crypto.WuaSigningResult
import eu.europa.ec.corelogic.config.AuthboundWalletProviderConfig
import eu.europa.ec.corelogic.config.EuReferenceWalletProviderConfig
import eu.europa.ec.corelogic.config.WalletProviderConfig
import eu.europa.ec.eudi.openid4vci.Nonce
import eu.europa.ec.eudi.wallet.provider.WalletAttestationsProvider
import eu.europa.ec.networklogic.repository.WalletAttestationHttpHeaders
import eu.europa.ec.networklogic.repository.WalletAttestationRepository
import eu.europa.ec.networklogic.repository.WalletAttestationRequest
import eu.europa.ec.networklogic.repository.WalletProofChallengeRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.multipaz.crypto.X509CertChain
import org.multipaz.securearea.KeyInfo
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

class WuaProofUserAuthRequiredException : IllegalStateException(
    "Wallet unit attestation proof requires user authentication"
)

class MissingWuaKeyException : IllegalStateException(
    "Wallet unit attestation key is not available"
)

class MissingKeyAttestationChainException(
    keyAlias: String,
) : IllegalStateException(
    "Authbound issuance requires hardware-backed key attestation for alias=$keyAlias"
)

interface WalletCoreAttestationProviderFactory {
    fun create(walletProviderConfig: WalletProviderConfig): WalletAttestationsProvider
}

class WalletCoreAttestationProviderFactoryImpl(
    private val walletAttestationRepository: WalletAttestationRepository,
    private val supabaseAuthRepository: SupabaseAuthRepository,
    private val cryptoController: CryptoController,
) : WalletCoreAttestationProviderFactory {

    override fun create(walletProviderConfig: WalletProviderConfig): WalletAttestationsProvider =
        when (walletProviderConfig) {
            is EuReferenceWalletProviderConfig -> EuReferenceWalletAttestationProvider(
                walletProviderConfig = walletProviderConfig,
                walletAttestationRepository = walletAttestationRepository,
            )

            is AuthboundWalletProviderConfig -> AuthboundWalletAttestationProvider(
                walletProviderConfig = walletProviderConfig,
                walletAttestationRepository = walletAttestationRepository,
                supabaseAuthRepository = supabaseAuthRepository,
                cryptoController = cryptoController,
            )
        }
}

private class EuReferenceWalletAttestationProvider(
    private val walletProviderConfig: EuReferenceWalletProviderConfig,
    private val walletAttestationRepository: WalletAttestationRepository,
) : WalletAttestationsProvider {

    override suspend fun getWalletAttestation(
        keyInfo: KeyInfo
    ): Result<String> = walletAttestationRepository.getWalletAttestation(
        baseUrl = walletProviderConfig.baseUrl,
        request = WalletAttestationRequest(
            body = buildJsonObject {
                put("jwk", keyInfo.publicKey.toJwk())
            }
        )
    )

    override suspend fun getKeyAttestation(
        keys: List<KeyInfo>,
        nonce: Nonce?
    ): Result<String> = walletAttestationRepository.getKeyAttestation(
        baseUrl = walletProviderConfig.baseUrl,
        request = WalletAttestationRequest(
            body = buildJsonObject {
                put("nonce", JsonPrimitive(nonce?.value.orEmpty()))
                putJsonObject("jwkSet") {
                    putJsonArray("keys") {
                        keys.forEach { keyInfo ->
                            add(keyInfo.publicKey.toJwk())
                        }
                    }
                }
            }
        )
    )
}

private class AuthboundWalletAttestationProvider(
    private val walletProviderConfig: AuthboundWalletProviderConfig,
    private val walletAttestationRepository: WalletAttestationRepository,
    private val supabaseAuthRepository: SupabaseAuthRepository,
    private val cryptoController: CryptoController,
) : WalletAttestationsProvider {

    private companion object {
        const val TAG = "AuthboundAttestation"
        const val WALLET_INSTANCE_ATTESTATION_PATH = "/wallet-instance-attestation/jwk"
        const val WALLET_UNIT_ATTESTATION_PATH = "/wallet-unit-attestation/jwk-set"
        const val WALLET_INSTANCE_ATTESTATION_PURPOSE = "wallet_instance_attestation"
        const val WALLET_UNIT_ATTESTATION_PURPOSE = "wallet_unit_attestation"
    }

    override suspend fun getWalletAttestation(
        keyInfo: KeyInfo
    ): Result<String> = runCatching {
        val attestationChain = keyInfo.requireAttestationChain()
        val keyMaterial = keyInfo.toAuthboundAttestedKeyMaterial(attestationChain)
        val baseRequestBody = buildWalletInstanceProofFreeRequest(keyMaterial)
        val authboundContext = buildAuthboundRequestContext(
            requestBody = baseRequestBody,
            requestPath = WALLET_INSTANCE_ATTESTATION_PATH,
            purpose = WALLET_INSTANCE_ATTESTATION_PURPOSE,
        )
        val requestBody = buildWalletInstanceRequest(
            keyMaterial = keyMaterial,
            proof = createAttestedKeyProof(
                keyAlias = keyMaterial.keyAlias,
                attestationNonce = authboundContext.proofChallenge.attestationNonce,
                requestHash = authboundContext.requestHash,
                requestPath = WALLET_INSTANCE_ATTESTATION_PATH,
            ),
        )
        Log.d(
            TAG,
            "Submitting Authbound attestation purpose=$WALLET_INSTANCE_ATTESTATION_PURPOSE path=$WALLET_INSTANCE_ATTESTATION_PATH challengeId=${authboundContext.proofChallenge.challengeId} requestHash=${authboundContext.requestHash.shortForLog()}"
        )

        walletAttestationRepository.getWalletAttestation(
            baseUrl = walletProviderConfig.baseUrl,
            request = WalletAttestationRequest(
                body = requestBody,
                headers = authboundContext.headers
            )
        ).getOrThrow()
    }

    override suspend fun getKeyAttestation(
        keys: List<KeyInfo>,
        nonce: Nonce?
    ): Result<String> = runCatching {
        val nonceValue = nonce?.value.orEmpty()
        val attestedKeys = keys.map { keyInfo ->
            keyInfo.toAuthboundAttestedKeyMaterial(keyInfo.requireAttestationChain())
        }
        val baseRequestBody = buildWalletUnitProofFreeRequest(
            nonce = nonceValue,
            keys = attestedKeys,
        )
        val authboundContext = buildAuthboundRequestContext(
            requestBody = baseRequestBody,
            requestPath = WALLET_UNIT_ATTESTATION_PATH,
            purpose = WALLET_UNIT_ATTESTATION_PURPOSE,
        )
        val proofsByKeyAlias = attestedKeys.associate { keyMaterial ->
            keyMaterial.keyAlias to createAttestedKeyProof(
                keyAlias = keyMaterial.keyAlias,
                attestationNonce = authboundContext.proofChallenge.attestationNonce,
                requestHash = authboundContext.requestHash,
                requestPath = WALLET_UNIT_ATTESTATION_PATH,
            )
        }
        val requestBody = buildWalletUnitRequest(
            nonce = nonceValue,
            keys = attestedKeys,
            proofsByKeyAlias = proofsByKeyAlias,
        )
        Log.d(
            TAG,
            "Submitting Authbound attestation purpose=$WALLET_UNIT_ATTESTATION_PURPOSE path=$WALLET_UNIT_ATTESTATION_PATH challengeId=${authboundContext.proofChallenge.challengeId} requestHash=${authboundContext.requestHash.shortForLog()}"
        )

        walletAttestationRepository.getKeyAttestation(
            baseUrl = walletProviderConfig.baseUrl,
            request = WalletAttestationRequest(
                body = requestBody,
                headers = authboundContext.headers
            )
        ).getOrThrow()
    }

    private suspend fun buildAuthboundRequestContext(
        requestBody: JsonObject,
        requestPath: String,
        purpose: String,
    ): AuthboundRequestContext {
        val bearerToken = requireNotNull(supabaseAuthRepository.getAccessToken()) {
            "Authbound wallet attestation requires an authenticated user session"
        }
        val requestHash = requestBody.toCanonicalHash()
        Log.d(
            TAG,
            "Creating Authbound proof challenge purpose=$purpose path=$requestPath requestHash=${requestHash.shortForLog()}"
        )
        val proofChallenge = walletAttestationRepository.createProofChallenge(
            baseUrl = walletProviderConfig.baseUrl,
            bearerToken = bearerToken,
            request = WalletProofChallengeRequest(
                purpose = purpose,
                requestHash = requestHash,
            )
        ).getOrThrow()
        Log.d(
            TAG,
            "Created Authbound proof challenge purpose=$purpose path=$requestPath challengeId=${proofChallenge.challengeId} requestHash=${requestHash.shortForLog()}"
        )

        return AuthboundRequestContext(
            requestHash = requestHash,
            proofChallenge = proofChallenge,
            headers = WalletAttestationHttpHeaders(
                bearerToken = bearerToken,
                wuaChallengeId = proofChallenge.challengeId,
                wuaProof = createWuaProof(
                    challengeId = proofChallenge.challengeId,
                    challenge = proofChallenge.challenge,
                    requestHash = requestHash,
                    requestPath = requestPath,
                )
            )
        )
    }

    private fun createWuaProof(
        challengeId: String,
        challenge: String,
        requestHash: String,
        requestPath: String,
    ): String {
        val header = buildJsonObject {
            put("alg", JsonPrimitive("ES256"))
            put("typ", JsonPrimitive("JWT"))
        }

        val issuedAt = Instant.now().epochSecond
        val payload = buildJsonObject {
            put("challengeId", JsonPrimitive(challengeId))
            put("challenge", JsonPrimitive(challenge))
            put("requestHash", JsonPrimitive(requestHash))
            put("method", JsonPrimitive("POST"))
            put("path", JsonPrimitive(requestPath))
            put("iat", JsonPrimitive(issuedAt))
            put("exp", JsonPrimitive(issuedAt + 300))
        }

        android.util.Log.d("WuaProof", "header=$header")
        android.util.Log.d("WuaProof", "payload=$payload")

        val signingInput = "${header.toBase64Url()}.${payload.toBase64Url()}"
        val signature = when (val result = cryptoController.signWuaPayload(signingInput.toByteArray(StandardCharsets.UTF_8))) {
            is WuaSigningResult.Signed -> result.signature
            WuaSigningResult.MissingKey -> throw MissingWuaKeyException()
            WuaSigningResult.UserAuthRequired -> throw WuaProofUserAuthRequiredException()
            is WuaSigningResult.Failure -> throw result.cause
        }

        val jwt = signingInput + "." + signature.toJoseBase64Url()
        android.util.Log.d("WuaProof", "jwt=$jwt")
        return jwt
    }

    private fun createAttestedKeyProof(
        keyAlias: String,
        attestationNonce: String,
        requestHash: String,
        requestPath: String,
    ): String {
        val header = buildJsonObject {
            put("alg", JsonPrimitive("ES256"))
            put("typ", JsonPrimitive("JWT"))
        }

        val issuedAt = Instant.now().epochSecond
        val payload = buildJsonObject {
            put("attestationNonce", JsonPrimitive(attestationNonce))
            put("requestHash", JsonPrimitive(requestHash))
            put("method", JsonPrimitive("POST"))
            put("path", JsonPrimitive(requestPath))
            put("iat", JsonPrimitive(issuedAt))
            put("exp", JsonPrimitive(issuedAt + 300))
        }

        val signingInput = "${header.toBase64Url()}.${payload.toBase64Url()}"
        val signature = when (val result = cryptoController.signPayload(keyAlias, signingInput.toByteArray(StandardCharsets.UTF_8))) {
            is WuaSigningResult.Signed -> result.signature
            WuaSigningResult.MissingKey -> throw IllegalStateException(
                "Attested key is not available for alias=$keyAlias"
            )

            WuaSigningResult.UserAuthRequired -> throw WuaProofUserAuthRequiredException()
            is WuaSigningResult.Failure -> throw result.cause
        }

        return signingInput + "." + signature.toJoseBase64Url()
    }

    private data class AuthboundRequestContext(
        val requestHash: String,
        val proofChallenge: eu.europa.ec.networklogic.repository.WalletProofChallenge,
        val headers: WalletAttestationHttpHeaders,
    )
}

private fun KeyInfo.requireAttestationChain(): X509CertChain =
    attestation.certChain ?: throw MissingKeyAttestationChainException(alias)

internal data class AuthboundAttestedKeyMaterial(
    val keyAlias: String,
    val jwk: JsonElement,
    val x5c: JsonElement,
)

internal fun buildWalletInstanceProofFreeRequest(
    keyMaterial: AuthboundAttestedKeyMaterial,
): JsonObject = buildJsonObject {
    putJsonObject("key") {
        put("jwk", keyMaterial.jwk)
        put("x5c", keyMaterial.x5c)
    }
}

internal fun buildWalletInstanceRequest(
    keyMaterial: AuthboundAttestedKeyMaterial,
    proof: String,
): JsonObject = buildJsonObject {
    putJsonObject("key") {
        put("jwk", keyMaterial.jwk)
        put("x5c", keyMaterial.x5c)
        put("proof", JsonPrimitive(proof))
    }
}

internal fun buildWalletUnitProofFreeRequest(
    nonce: String,
    keys: List<AuthboundAttestedKeyMaterial>,
): JsonObject = buildJsonObject {
    put("nonce", JsonPrimitive(nonce))
    putJsonArray("keys") {
        keys.forEach { keyMaterial ->
            add(keyMaterial.toJsonObject())
        }
    }
}

internal fun buildWalletUnitRequest(
    nonce: String,
    keys: List<AuthboundAttestedKeyMaterial>,
    proofsByKeyAlias: Map<String, String>,
): JsonObject = buildJsonObject {
    put("nonce", JsonPrimitive(nonce))
    putJsonArray("keys") {
        keys.forEach { keyMaterial ->
            add(
                keyMaterial.toJsonObject(
                    proof = requireNotNull(proofsByKeyAlias[keyMaterial.keyAlias]) {
                        "Missing attested key proof for alias=${keyMaterial.keyAlias}"
                    }
                )
            )
        }
    }
}

internal fun canonicalJsonString(element: JsonElement): String = canonicalizeJsonElement(element).toString()

internal fun hashCanonicalJsonObject(body: JsonObject): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(canonicalJsonString(body).toByteArray(StandardCharsets.UTF_8))
    return digest.toBase64Url()
}

private fun JsonObject.toCanonicalHash(): String {
    return hashCanonicalJsonObject(this)
}

private fun JsonObject.toBase64Url(): String =
    canonicalJsonString(this).toByteArray(StandardCharsets.UTF_8).toBase64Url()

private fun KeyInfo.toAuthboundAttestedKeyMaterial(
    attestationChain: X509CertChain,
): AuthboundAttestedKeyMaterial = AuthboundAttestedKeyMaterial(
    keyAlias = alias,
    jwk = publicKey.toJwk(),
    x5c = attestationChain.toX5c(),
)

private fun AuthboundAttestedKeyMaterial.toJsonObject(proof: String? = null): JsonObject =
    buildJsonObject {
        put("jwk", jwk)
        put("x5c", x5c)
        if (proof != null) {
            put("proof", JsonPrimitive(proof))
        }
    }

private fun canonicalizeJsonElement(element: JsonElement): JsonElement =
    when (element) {
        is JsonObject -> JsonObject(
            element.entries
                .sortedBy { it.key }
                .associate { (key, value) -> key to canonicalizeJsonElement(value) }
        )

        is JsonArray -> JsonArray(element.map(::canonicalizeJsonElement))
        else -> element
    }

private fun ByteArray.toBase64Url(): String =
    Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

private fun ByteArray.toJoseBase64Url(): String {
    val joseBytes = ECDSA.transcodeSignatureToConcat(
        this,
        ECDSA.getSignatureByteArrayLength(JWSAlgorithm.ES256)
    )
    return joseBytes.toBase64Url()
}

private fun String.shortForLog(): String =
    if (length <= 12) this else "${take(12)}..."
