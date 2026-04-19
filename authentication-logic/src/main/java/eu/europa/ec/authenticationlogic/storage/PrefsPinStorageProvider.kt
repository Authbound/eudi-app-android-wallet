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

package eu.europa.ec.authenticationlogic.storage

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import com.google.gson.Gson
import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.authenticationlogic.model.LocalUnlockStatus
import eu.europa.ec.authenticationlogic.model.PinValidationResult
import eu.europa.ec.authenticationlogic.provider.PinStorageProvider
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.businesslogic.extension.decodeFromBase64
import eu.europa.ec.businesslogic.extension.encodeToBase64String
import eu.europa.ec.businesslogic.extension.parseFromJson
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
private const val RECOVERY_AUTH_WINDOW_SECONDS = 30
private const val PIN_CONFIGURED_SENTINEL = "__pin_configured__"

private sealed interface LocalAuthMaterialLoadState {
    data object Missing : LocalAuthMaterialLoadState
    data object Tampered : LocalAuthMaterialLoadState
    data class Valid(val material: LocalAuthMaterialV2) : LocalAuthMaterialLoadState
}

private data class LocalAuthMaterialV2(
    val v: Int = 2,
    val pinSalt: String,
    val pinIterations: Int,
    val pinWrappedUnlockKey: String,
    val pinWrapIv: String,
    val recoveryWrappedUnlockKey: String,
    val recoveryWrapIv: String,
    val failedAttempts: Int,
    val lockedUntilMs: Long,
    val stateMac: String,
)

class PrefsPinStorageProvider(
    private val prefsController: PrefsControllerV2,
    private val cryptoController: CryptoController,
    private val keyStoreProvider: () -> KeyStore = {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }
) : PinStorageProvider {

    private val gson: Gson = Gson()
    private val secureRandom: SecureRandom = SecureRandom()

    @Volatile
    private var cachedVaultUnlockKey: ByteArray? = null

    @Volatile
    private var cachedVaultUnlockKeyExpiresAtMs: Long = 0L

    override suspend fun retrievePin(): String = when (getLocalUnlockStatus()) {
        LocalUnlockStatus.NotProvisioned -> ""
        else -> PIN_CONFIGURED_SENTINEL
    }

    override suspend fun setPin(pin: String) {
        require(pin.isNotBlank()) { "PIN must not be blank" }

        val currentState: LocalAuthMaterialLoadState = loadMaterialState(migrateLegacy = false)
        val vaultUnlockKey: ByteArray = when (currentState) {
            LocalAuthMaterialLoadState.Missing -> generateRandomBytes(LocalAuthKeys.VAULT_UNLOCK_KEY_SIZE_BYTES)
            LocalAuthMaterialLoadState.Tampered ->
                throw SecurityException("Local auth material is tampered")
            is LocalAuthMaterialLoadState.Valid ->
                requireTrustedVaultUnlockKey()
        }

        persistMaterial(
            createMaterial(
                pin = pin,
                vaultUnlockKey = vaultUnlockKey
            )
        )
        deleteLegacyPinPrefs()
        cacheVaultUnlockKey(vaultUnlockKey)
    }

    override suspend fun isPinValid(pin: String): Boolean = verifyPin(pin) is PinValidationResult.Success

    override suspend fun getLocalUnlockStatus(): LocalUnlockStatus {
        return when (val state = loadMaterialState(migrateLegacy = true)) {
            LocalAuthMaterialLoadState.Missing -> LocalUnlockStatus.NotProvisioned
            LocalAuthMaterialLoadState.Tampered -> LocalUnlockStatus.TamperDetected
            is LocalAuthMaterialLoadState.Valid -> resolveStatus(state.material)
        }
    }

    override suspend fun verifyPin(pin: String): PinValidationResult {
        return when (val state = loadMaterialState(migrateLegacy = true)) {
            LocalAuthMaterialLoadState.Missing -> PinValidationResult.RecoveryRequired
            LocalAuthMaterialLoadState.Tampered -> PinValidationResult.TamperDetected
            is LocalAuthMaterialLoadState.Valid -> verifyAgainstMaterial(pin, state.material)
        }
    }

    override suspend fun prepareRecovery(): LocalUnlockStatus {
        val state: LocalAuthMaterialLoadState = loadMaterialState(migrateLegacy = true)
        if (state !is LocalAuthMaterialLoadState.Valid) {
            return when (state) {
                LocalAuthMaterialLoadState.Missing -> LocalUnlockStatus.RecoveryRequired
                LocalAuthMaterialLoadState.Tampered -> LocalUnlockStatus.TamperDetected
                else -> LocalUnlockStatus.RecoveryRequired
            }
        }

        return try {
            val vaultUnlockKey: ByteArray = unwrapWithRecoveryKey(
                wrappedUnlockKey = decodeFromBase64(state.material.recoveryWrappedUnlockKey)
            )
            cacheVaultUnlockKey(vaultUnlockKey)
            persistMaterial(
                state.material.copy(
                    failedAttempts = 0,
                    lockedUntilMs = 0,
                    stateMac = ""
                )
            )
            LocalUnlockStatus.ReadyForPin
        } catch (_: UserNotAuthenticatedException) {
            LocalUnlockStatus.RecoveryRequired
        } catch (_: KeyPermanentlyInvalidatedException) {
            LocalUnlockStatus.RecoveryRequired
        } catch (_: Exception) {
            LocalUnlockStatus.RecoveryRequired
        }
    }

    override suspend fun clearPinData(userId: String?) {
        val failures: MutableList<String> = mutableListOf()
        clearEphemeralSecrets()
        runCatching { clearLocalAuthAliases() }
            .onFailure { failures += it.message ?: "Delete local auth aliases" }
        if (userId != null) {
            runCatching { clearScopedKey(LocalAuthKeys.AUTH_STATE, userId) }
                .onFailure { failures += "Clear local auth state" }
            runCatching { clearScopedKey(LocalAuthKeys.ENROLLMENT_REQUIRED, userId) }
                .onFailure { failures += "Clear enrollment requirement flag" }
            runCatching { deleteLegacyPinPrefs(userId) }
                .onFailure { failures += "Clear legacy PIN preferences" }
        }
        if (failures.isNotEmpty()) {
            throw SecurityException(
                "Failed to clear local auth material: ${failures.joinToString(", ")}"
            )
        }
    }

    override suspend fun clearEphemeralSecrets() {
        clearCachedVaultUnlockKey()
    }

    private fun clearCachedVaultUnlockKey() {
        cachedVaultUnlockKey?.fill(0)
        cachedVaultUnlockKey = null
        cachedVaultUnlockKeyExpiresAtMs = 0L
    }

    private suspend fun verifyAgainstMaterial(
        pin: String,
        material: LocalAuthMaterialV2
    ): PinValidationResult {
        val status: LocalUnlockStatus = resolveStatus(material)
        if (status is LocalUnlockStatus.TemporarilyLocked) {
            return PinValidationResult.Failed(
                remainingAttempts = remainingAttempts(material.failedAttempts),
                lockedUntilMs = status.lockedUntilMs
            )
        }
        if (status is LocalUnlockStatus.RecoveryRequired) {
            return PinValidationResult.RecoveryRequired
        }

        return try {
            val vaultUnlockKey: ByteArray = unwrapWithPin(
                pin = pin,
                material = material
            )
            cacheVaultUnlockKey(vaultUnlockKey)
            persistMaterial(
                material.copy(
                    failedAttempts = 0,
                    lockedUntilMs = 0,
                    stateMac = ""
                )
            )
            PinValidationResult.Success
        } catch (_: AEADBadTagException) {
            registerFailedAttempt(material)
        } catch (_: IllegalArgumentException) {
            registerFailedAttempt(material)
        }
    }

    private suspend fun registerFailedAttempt(material: LocalAuthMaterialV2): PinValidationResult {
        val failedAttempts: Int = material.failedAttempts + 1
        if (failedAttempts >= LocalAuthKeys.MAX_FAILED_ATTEMPTS) {
            persistMaterial(
                material.copy(
                    failedAttempts = LocalAuthKeys.MAX_FAILED_ATTEMPTS,
                    lockedUntilMs = 0,
                    stateMac = ""
                )
            )
            clearEphemeralSecrets()
            return PinValidationResult.RecoveryRequired
        }

        val lockedUntilMs: Long = calculateLockUntil(failedAttempts)
        persistMaterial(
            material.copy(
                failedAttempts = failedAttempts,
                lockedUntilMs = lockedUntilMs,
                stateMac = ""
            )
        )
        clearEphemeralSecrets()
        return PinValidationResult.Failed(
            remainingAttempts = remainingAttempts(failedAttempts),
            lockedUntilMs = lockedUntilMs.takeIf { it > 0L }
        )
    }

    private fun resolveStatus(material: LocalAuthMaterialV2): LocalUnlockStatus {
        val now: Long = System.currentTimeMillis()
        return when {
            material.failedAttempts >= LocalAuthKeys.MAX_FAILED_ATTEMPTS -> LocalUnlockStatus.RecoveryRequired
            material.lockedUntilMs > now -> LocalUnlockStatus.TemporarilyLocked(material.lockedUntilMs)
            else -> LocalUnlockStatus.ReadyForPin
        }
    }

    private suspend fun loadMaterialState(migrateLegacy: Boolean): LocalAuthMaterialLoadState {
        val rawMaterial: String = prefsController.getString(LocalAuthKeys.AUTH_STATE, "")
        if (rawMaterial.isBlank()) {
            if (migrateLegacy) {
                migrateLegacyMaterial()?.let { return LocalAuthMaterialLoadState.Valid(it) }
            }
            return LocalAuthMaterialLoadState.Missing
        }

        val material: LocalAuthMaterialV2 = rawMaterial.parseFromJson<LocalAuthMaterialV2>()
            ?: return LocalAuthMaterialLoadState.Tampered

        if (!material.isStructurallyValid()) {
            return LocalAuthMaterialLoadState.Tampered
        }

        val expectedMac: String = signMaterial(material.copy(stateMac = ""))
        return if (expectedMac == material.stateMac) {
            LocalAuthMaterialLoadState.Valid(material)
        } else {
            LocalAuthMaterialLoadState.Tampered
        }
    }

    private suspend fun migrateLegacyMaterial(): LocalAuthMaterialV2? {
        val encryptedBase64: String = prefsController.getString(LocalAuthKeys.LEGACY_PIN_ENC, "")
        val ivBase64: String = prefsController.getString(LocalAuthKeys.LEGACY_PIN_IV, "")
        if (encryptedBase64.isBlank() || ivBase64.isBlank()) {
            return null
        }

        val legacyPin: String = decryptLegacyPin(
            encryptedBase64 = encryptedBase64,
            ivBase64 = ivBase64
        )
        if (legacyPin.isBlank()) {
            return null
        }

        val migratedMaterial: LocalAuthMaterialV2 = createMaterial(
            pin = legacyPin,
            vaultUnlockKey = generateRandomBytes(LocalAuthKeys.VAULT_UNLOCK_KEY_SIZE_BYTES)
        )
        persistMaterial(migratedMaterial)
        deleteLegacyPinPrefs()
        return migratedMaterial
    }

    private suspend fun persistMaterial(material: LocalAuthMaterialV2) {
        val materialWithMac: LocalAuthMaterialV2 = material.copy(
            stateMac = signMaterial(material.copy(stateMac = ""))
        )
        prefsController.setString(LocalAuthKeys.AUTH_STATE, gson.toJson(materialWithMac))
    }

    private fun createMaterial(
        pin: String,
        vaultUnlockKey: ByteArray
    ): LocalAuthMaterialV2 {
        val pinSalt: ByteArray = generateRandomBytes(LocalAuthKeys.PIN_SALT_SIZE_BYTES)
        val pinWrap: WrappedPayload = wrapWithPin(
            pin = pin,
            vaultUnlockKey = vaultUnlockKey,
            salt = pinSalt
        )
        val recoveryWrappedUnlockKey: ByteArray = wrapWithRecoveryKey(vaultUnlockKey)
        return LocalAuthMaterialV2(
            pinSalt = pinSalt.encodeToBase64String(),
            pinIterations = LocalAuthKeys.PBKDF2_ITERATIONS,
            pinWrappedUnlockKey = pinWrap.cipherText.encodeToBase64String(),
            pinWrapIv = pinWrap.iv.encodeToBase64String(),
            recoveryWrappedUnlockKey = recoveryWrappedUnlockKey.encodeToBase64String(),
            recoveryWrapIv = "",
            failedAttempts = 0,
            lockedUntilMs = 0,
            stateMac = ""
        )
    }

    private fun wrapWithPin(
        pin: String,
        vaultUnlockKey: ByteArray,
        salt: ByteArray
    ): WrappedPayload {
        val cipher: Cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, derivePinKey(pin, salt))
        return WrappedPayload(
            cipherText = cipher.doFinal(vaultUnlockKey),
            iv = cipher.iv
        )
    }

    private fun unwrapWithPin(
        pin: String,
        material: LocalAuthMaterialV2
    ): ByteArray {
        val cipher: Cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            derivePinKey(pin, decodeFromBase64(material.pinSalt)),
            GCMParameterSpec(128, decodeFromBase64(material.pinWrapIv))
        )
        return cipher.doFinal(decodeFromBase64(material.pinWrappedUnlockKey))
    }

    private fun derivePinKey(pin: String, salt: ByteArray): SecretKey {
        val pepperedPin: String = computeHmac(
            alias = LocalAuthKeys.PIN_PEPPER_ALIAS,
            data = pin.toByteArray(StandardCharsets.UTF_8)
        ).encodeToBase64String()
        val spec = PBEKeySpec(
            pepperedPin.toCharArray(),
            salt,
            LocalAuthKeys.PBKDF2_ITERATIONS,
            256
        )
        val factory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun wrapWithRecoveryKey(data: ByteArray): ByteArray {
        val keyPair: KeyPair = retrieveOrGenerateRecoveryKeyPair()
        val cipher: Cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.public)
        return cipher.doFinal(data)
    }

    private fun unwrapWithRecoveryKey(wrappedUnlockKey: ByteArray): ByteArray {
        val keyPair: KeyPair = retrieveOrGenerateRecoveryKeyPair()
        val cipher: Cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyPair.private)
        return cipher.doFinal(wrappedUnlockKey)
    }

    private fun retrieveOrGenerateRecoveryKeyPair(): KeyPair {
        val keyStore: KeyStore = loadKeyStore()
        val certificate = keyStore.getCertificate(LocalAuthKeys.RECOVERY_KEY_ALIAS)
        val privateKey = keyStore.getKey(LocalAuthKeys.RECOVERY_KEY_ALIAS, null)
        if (certificate != null && privateKey != null) {
            return KeyPair(certificate.publicKey, privateKey as java.security.PrivateKey)
        }

        val generator: KeyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE
        )
        val builder = KeyGenParameterSpec.Builder(
            LocalAuthKeys.RECOVERY_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setDigests(
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA512
            )
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            setKeySize(3072)
            setUserAuthenticationRequired(true)
            setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setUserAuthenticationParameters(
                    RECOVERY_AUTH_WINDOW_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
            } else {
                @Suppress("DEPRECATION")
                setUserAuthenticationValidityDurationSeconds(RECOVERY_AUTH_WINDOW_SECONDS)
            }
        }
        generator.initialize(builder.build())
        val keyPair: KeyPair = generator.generateKeyPair()
        return keyPair
    }

    private fun computeHmac(alias: String, data: ByteArray): ByteArray {
        val mac: Mac = Mac.getInstance("HmacSHA256")
        mac.init(retrieveOrGenerateHmacKey(alias))
        return mac.doFinal(data)
    }

    private fun retrieveOrGenerateHmacKey(alias: String): SecretKey {
        val keyStore: KeyStore = loadKeyStore()
        val existing: SecretKey? = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) {
            return existing
        }

        val generator: KeyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).setKeySize(256).build()
        )
        return generator.generateKey()
    }

    private fun signMaterial(material: LocalAuthMaterialV2): String {
        return computeHmac(
            alias = LocalAuthKeys.AUTH_STATE_MAC_ALIAS,
            data = material.macPayload().toByteArray(StandardCharsets.UTF_8)
        ).encodeToBase64String()
    }

    private fun decryptLegacyPin(
        encryptedBase64: String,
        ivBase64: String
    ): String {
        val cipher = cryptoController.getCipher(
            encrypt = false,
            ivBytes = decodeFromBase64(ivBase64),
            userAuthenticationRequired = false
        )
        val decryptedBytes = cryptoController.encryptDecrypt(
            cipher = cipher,
            byteArray = decodeFromBase64(encryptedBase64)
        )
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private suspend fun deleteLegacyPinPrefs(userId: String? = null) {
        clearScopedKey(LocalAuthKeys.LEGACY_PIN_ENC, userId)
        clearScopedKey(LocalAuthKeys.LEGACY_PIN_IV, userId)
    }

    private suspend fun clearScopedKey(key: String, userId: String?) {
        if (userId == null) {
            prefsController.clear(key)
        } else {
            prefsController.clearForUser(userId, key)
        }
    }

    private fun clearLocalAuthAliases() {
        val keyStore: KeyStore = loadKeyStore()
        val failedAliases: MutableList<String> = mutableListOf()
        listOf(
            LocalAuthKeys.PIN_PEPPER_ALIAS,
            LocalAuthKeys.AUTH_STATE_MAC_ALIAS,
            LocalAuthKeys.RECOVERY_KEY_ALIAS
        ).forEach { alias ->
            runCatching {
                if (keyStore.containsAlias(alias)) {
                    keyStore.deleteEntry(alias)
                }
            }
            .onFailure { failedAliases += alias }
        }
        if (failedAliases.isNotEmpty()) {
            throw SecurityException(
                "Failed to clear local auth aliases: ${failedAliases.joinToString(", ")}"
            )
        }
    }

    private fun cacheVaultUnlockKey(vaultUnlockKey: ByteArray) {
        cachedVaultUnlockKey = vaultUnlockKey.copyOf()
        cachedVaultUnlockKeyExpiresAtMs = System.currentTimeMillis() + LocalUnlockTracker.DEFAULT_TTL_MS
    }

    private fun requireTrustedVaultUnlockKey(): ByteArray {
        if (!isCachedVaultUnlockKeyValid()) {
            clearCachedVaultUnlockKey()
            throw SecurityException("PIN update requires a prior trusted unlock")
        }
        return cachedVaultUnlockKey?.copyOf()
            ?: throw SecurityException("PIN update requires a prior trusted unlock")
    }

    private fun isCachedVaultUnlockKeyValid(): Boolean {
        return cachedVaultUnlockKey != null &&
            cachedVaultUnlockKeyExpiresAtMs > System.currentTimeMillis()
    }

    private fun loadKeyStore(): KeyStore {
        return keyStoreProvider()
    }

    private fun remainingAttempts(failedAttempts: Int): Int {
        return (LocalAuthKeys.MAX_FAILED_ATTEMPTS - failedAttempts).coerceAtLeast(0)
    }

    private fun calculateLockUntil(failedAttempts: Int): Long {
        val durationMs: Long = when (failedAttempts) {
            6 -> 30_000L
            7 -> 60_000L
            8 -> 5 * 60_000L
            9 -> 15 * 60_000L
            else -> 0L
        }
        return if (durationMs > 0L) {
            System.currentTimeMillis() + durationMs
        } else {
            0L
        }
    }

    private fun generateRandomBytes(size: Int): ByteArray {
        return ByteArray(size).also(secureRandom::nextBytes)
    }

    private fun LocalAuthMaterialV2.isStructurallyValid(): Boolean {
        return v == 2 &&
            pinSalt.isNotBlank() &&
            pinIterations >= LocalAuthKeys.PBKDF2_ITERATIONS &&
            pinWrappedUnlockKey.isNotBlank() &&
            pinWrapIv.isNotBlank() &&
            recoveryWrappedUnlockKey.isNotBlank()
    }

    private fun LocalAuthMaterialV2.macPayload(): String {
        return listOf(
            v.toString(),
            pinSalt,
            pinIterations.toString(),
            pinWrappedUnlockKey,
            pinWrapIv,
            recoveryWrappedUnlockKey,
            recoveryWrapIv,
            failedAttempts.toString(),
            lockedUntilMs.toString()
        ).joinToString("|")
    }
}

private data class WrappedPayload(
    val cipherText: ByteArray,
    val iv: ByteArray
)
