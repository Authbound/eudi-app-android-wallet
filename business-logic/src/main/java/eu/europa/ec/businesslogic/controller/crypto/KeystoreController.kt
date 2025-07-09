/*
 * Copyright (c) 2023 European Commission
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

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.businesslogic.provider.UuidProvider
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

interface KeystoreController {
    fun retrieveOrGenerateBiometricSecretKey(): SecretKey?
    fun generateWuaKeyPair(): Array<Certificate>?
    fun deleteBiometricSecretKey(alias: String)
}

class KeystoreControllerImpl(
    private val prefKeys: PrefKeys,
    private val logController: LogController,
    private val uuidProvider: UuidProvider
) : KeystoreController {

    companion object {
        private const val STORE_TYPE = "AndroidKeyStore"
        private const val WUA_KEY_ALIAS = "wua_key_alias"
    }

    private var androidKeyStore: KeyStore? = null

    init {
        loadKeyStore()
    }

    /**
     * Load/Init KeyStore
     */
    private fun loadKeyStore() {
        try {
            androidKeyStore = KeyStore.getInstance(STORE_TYPE)
            androidKeyStore?.load(null)
        } catch (e: Exception) {
            logController.e(this.javaClass.simpleName, e)
        }
    }

    /**
     * Retrieves the existing biometric secret key if exists or generates a new one if it is the
     * first time.
     */
    override fun retrieveOrGenerateBiometricSecretKey(): SecretKey? {
        return androidKeyStore?.let {
            val alias = prefKeys.getBiometricAlias()
            if (alias.isEmpty()) {
                val newAlias = createPublicKey()
                generateBiometricSecretKey(newAlias)
                prefKeys.setBiometricAlias(newAlias)
                getBiometricSecretKey(it, newAlias)
            } else {
                getBiometricSecretKey(it, alias)
            }
        }
    }

    override fun generateWuaKeyPair(): Array<Certificate>? {
        return androidKeyStore?.let {
            if (!it.containsAlias(WUA_KEY_ALIAS)) {
                val keyPairGenerator =
                    KeyPairGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_EC,
                        STORE_TYPE
                    )

                val parameterSpec = KeyGenParameterSpec.Builder(
                    WUA_KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).run {
                    setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    setDigests(
                        KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA512
                    )
                    setUserAuthenticationRequired(false)
                    setAttestationChallenge("authbound".toByteArray())
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
    private fun generateBiometricSecretKey(alias: String) {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE_TYPE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_DEVICE_CREDENTIAL or KeyProperties.AUTH_BIOMETRIC_STRONG
                )
                .build()
        )
        keyGenerator.generateKey()
    }

    private fun getBiometricSecretKey(keyStore: KeyStore, alias: String): SecretKey {
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