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

package eu.europa.ec.businesslogic.controller.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.provider.UuidProvider
import kotlinx.coroutines.runBlocking
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

interface KeystoreController {
    fun retrieveOrGenerateSecretKey(userAuthenticationRequired: Boolean): SecretKey?
    /**
     * Generates a WUA (Wallet Unit Attestation) key pair in the Android Keystore.
     *
     * @param challenge The attestation challenge from the backend server. This should be a
     *                  unique, server-provided nonce for each WUA generation to prevent
     *                  replay attacks. If null, uses a default challenge (NOT recommended
     *                  for production - backend should always provide a fresh challenge).
     * @return The certificate chain for the generated key pair, or null if generation fails.
     */
    fun generateWuaKeyPair(challenge: ByteArray? = null): Array<Certificate>?
    fun deleteBiometricSecretKey(alias: String)
}

class KeystoreControllerImpl(
    private val prefKeys: PrefKeysV2,
    private val logController: LogController,
    private val uuidProvider: UuidProvider
) : KeystoreController {

    companion object {
        private const val STORE_TYPE = "AndroidKeyStore"
        private const val WUA_KEY_ALIAS = "wua_key_alias"
        // Default challenge - should be replaced with server-provided nonce for ARF compliance
        private const val DEFAULT_ATTESTATION_CHALLENGE = "authbound"
    }

    private var androidKeyStore: KeyStore? = null

    init {
        loadKeyStore()
    }

    private fun loadKeyStore() {
        try {
            androidKeyStore = KeyStore.getInstance(STORE_TYPE)
            androidKeyStore?.load(null)
        } catch (e: Exception) {
            logController.e(this.javaClass.simpleName, e)
        }
    }

    /**
     * Retrieves an existing secret key or generates a new one.
     *
     * If an alias for a secret key is found in preferences, this function attempts to retrieve the key
     * associated with that alias from the Android KeyStore.
     *
     * If no alias is found or if the key retrieval fails, a new secret key is generated.
     * The new key is then stored in the Android KeyStore under a newly generated alias,
     * and this new alias is saved in preferences for future use.
     *
     * The generation of the new key can be configured to require user authentication.
     *
     * @param userAuthenticationRequired A boolean indicating whether the newly generated key
     *                                   should require user authentication for its use.
     * @return The retrieved or newly generated [SecretKey], or `null` if the Android KeyStore
     *         is unavailable or if any other error occurs during the process.
     */
    override fun retrieveOrGenerateSecretKey(userAuthenticationRequired: Boolean): SecretKey? {
        return androidKeyStore?.let {
            val alias = runBlocking { prefKeys.getCryptoAlias() }
            if (alias.isEmpty()) {
                val newAlias = createPublicKey()
                generateSecretKey(newAlias, userAuthenticationRequired)
                runBlocking { prefKeys.setCryptoAlias(newAlias) }
                getSecretKey(it, newAlias)
            } else {
                getSecretKey(it, alias)
            }
        }
    }

    override fun generateWuaKeyPair(challenge: ByteArray?): Array<Certificate>? {
        return androidKeyStore?.let {
            if (!it.containsAlias(WUA_KEY_ALIAS)) {
                val keyPairGenerator =
                    KeyPairGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_EC,
                        STORE_TYPE
                    )

                // Use server-provided challenge or fallback to default
                // NOTE: For ARF compliance, backend should always provide a unique challenge
                val attestationChallenge = challenge ?: DEFAULT_ATTESTATION_CHALLENGE.toByteArray()

                val parameterSpec = KeyGenParameterSpec.Builder(
                    WUA_KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).run {
                    setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    setDigests(
                        KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA512
                    )
                    // ARF Compliance: WUA keys must require user authentication
                    setUserAuthenticationRequired(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Auth valid for 5 minutes, requires strong biometric or device credential
                        setUserAuthenticationParameters(
                            300, // 5 minutes
                            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                        )
                    }
                    setAttestationChallenge(attestationChallenge)
                    build()
                }
                keyPairGenerator.initialize(parameterSpec)
                keyPairGenerator.generateKeyPair()
            }
            it.getCertificateChain(WUA_KEY_ALIAS)
        }
    }

    override fun deleteBiometricSecretKey(alias: String) {
        try {
            androidKeyStore?.deleteEntry(alias)
            logController.d(this.javaClass.simpleName, "Biometric key with alias $alias deleted.")
        } catch (e: Exception) {
            logController.e(this.javaClass.simpleName, e)
        }
    }

    @Suppress("DEPRECATION")
    private fun generateSecretKey(alias: String, userAuthenticationRequired: Boolean) {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE_TYPE)

        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setKeySize(256)
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            if (userAuthenticationRequired) {
                setUserAuthenticationRequired(true)
                setInvalidatedByBiometricEnrollment(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(
                        0,
                        KeyProperties.AUTH_DEVICE_CREDENTIAL or KeyProperties.AUTH_BIOMETRIC_STRONG
                    )
                } else {
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
        }

        keyGenerator.init(
            builder.build()
        )

        keyGenerator.generateKey()
    }

    private fun getSecretKey(keyStore: KeyStore, alias: String): SecretKey {
        keyStore.load(null)
        return keyStore.getKey(alias, null) as SecretKey
    }

    /**
     * Get random GUID string
     *
     * @return a string containing 64 characters
     */
    private fun createPublicKey(): GUID =
        (uuidProvider.provideUuid() + uuidProvider.provideUuid())
            .take(CryptoControllerImpl.MAX_GUID_LENGTH)
}