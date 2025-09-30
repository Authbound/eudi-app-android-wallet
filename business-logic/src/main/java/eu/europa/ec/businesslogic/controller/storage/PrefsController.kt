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

package eu.europa.ec.businesslogic.controller.storage

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.edit
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.resourceslogic.provider.ResourceProvider

/**
 * User-scoped preferences controller that ensures proper data isolation between users.
 *
 * CRITICAL SECURITY FEATURE: Prevents cross-user data leakage by scoping all
 * preferences to the authenticated user's ID. All preference operations are now
 * implicitly user-scoped.
 */
interface PrefsController {
    fun setCurrentUser(userId: String?)
    fun clearCurrentUserData()

    fun getString(key: String, defaultValue: String): String
    fun setString(key: String, value: String)
    fun getBool(key: String, defaultValue: Boolean): Boolean
    fun setBool(key: String, value: Boolean)
    fun getLong(key: String, defaultValue: Long): Long
    fun setLong(key: String, value: Long)
    fun getInt(key: String, defaultValue: Int): Int
    fun setInt(key: String, value: Int)
    fun contains(key: String): Boolean
    fun clear(key: String)
    fun clearAll() // Clears all data for the current user

    // Safe methods that don't throw exceptions when no user context is set
    fun safeBool(key: String, defaultValue: Boolean): Boolean
    fun safeString(key: String, defaultValue: String): String
    fun hasCurrentUser(): Boolean
}

class PrefsControllerImpl(
    private val resourceProvider: ResourceProvider,
    private val logController: LogController
) : PrefsController {

    private var currentUserId: String? = null

    companion object {
        private const val USER_PREFS_PREFIX = "authbound-wallet-user-"
    }

    override fun setCurrentUser(userId: String?) {
        currentUserId = userId
        if (userId != null) {
            logController.d("PrefsController", "Current user context set to: ${userId.take(8)}...")
        } else {
            logController.d("PrefsController", "User context cleared.")
        }
    }

    override fun clearCurrentUserData() {
        currentUserId?.let { userId ->
            getUserPrefs(userId).edit { clear() }
            logController.i("PrefsController") { "Cleared all data for user: ${userId.take(8)}..." }
        }
    }

    private fun getUserPrefs(userId: String): SharedPreferences {
        return resourceProvider.provideContext()
            .getSharedPreferences("$USER_PREFS_PREFIX$userId", MODE_PRIVATE)
    }

    private fun getCurrentUserPrefs(): SharedPreferences {
        val userId = currentUserId ?: throw SecurityException(
            "CRITICAL SECURITY ERROR: Attempting to access user preferences without an authenticated user context. Ensure setCurrentUser() is called after login."
        )
        return getUserPrefs(userId)
    }

    // Shuffling has been removed for simplicity and to avoid security-by-obscurity.
    // Encryption should be handled by the Android Keystore for sensitive data.
    override fun setString(key: String, value: String) {
        getCurrentUserPrefs().edit { putString(key, value) }
    }

    override fun getString(key: String, defaultValue: String): String {
        return getCurrentUserPrefs().getString(key, defaultValue) ?: defaultValue
    }
    
    override fun setLong(key: String, value: Long) {
        getCurrentUserPrefs().edit { putLong(key, value) }
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return getCurrentUserPrefs().getLong(key, defaultValue)
    }

    override fun setBool(key: String, value: Boolean) {
        getCurrentUserPrefs().edit { putBoolean(key, value) }
    }

    override fun getBool(key: String, defaultValue: Boolean): Boolean {
        return getCurrentUserPrefs().getBoolean(key, defaultValue)
    }
    
    override fun getInt(key: String, defaultValue: Int): Int {
        return getCurrentUserPrefs().getInt(key, defaultValue)
    }
    
    override fun setInt(key: String, value: Int) {
        getCurrentUserPrefs().edit { putInt(key, value) }
    }

    override fun contains(key: String): Boolean {
        return getCurrentUserPrefs().contains(key)
    }

    override fun clear(key: String) {
        getCurrentUserPrefs().edit { remove(key) }
    }

    override fun clearAll() {
        clearCurrentUserData()
    }

    override fun safeBool(key: String, defaultValue: Boolean): Boolean {
        return if (currentUserId != null) {
            try {
                getCurrentUserPrefs().getBoolean(key, defaultValue)
            } catch (e: SecurityException) {
                logController.w("PrefsController") { "Failed to access preferences safely: ${e.message}" }
                defaultValue
            }
        } else {
            logController.d("PrefsController",  "No user context for safe access to key: $key, returning default: $defaultValue")
            defaultValue
        }
    }

    override fun safeString(key: String, defaultValue: String): String {
        return if (currentUserId != null) {
            try {
                getCurrentUserPrefs().getString(key, defaultValue) ?: defaultValue
            } catch (e: SecurityException) {
                logController.w("PrefsController") { "Failed to access string preference safely: ${e.message}" }
                defaultValue
            }
        } else {
            logController.d("PrefsController", "No user context for safe string access: $key, returning default: $defaultValue")
            defaultValue
        }
    }

    override fun hasCurrentUser(): Boolean {
        return currentUserId != null
    }
}

interface PrefKeys {
    fun getCryptoAlias(): String
    fun setCryptoAlias(value: String)

    fun getBiometricAlias(): String
    fun setBiometricAlias(value: String)

    fun getShowBatchIssuanceCounter(): Boolean
    fun setShowBatchIssuanceCounter(value: Boolean)

    fun isWalletActivated(): Boolean
    fun setWalletActivated(isActivated: Boolean)

    // Safe methods that don't throw when no user context is set
    fun isWalletActivatedSafe(): Boolean
    fun getBiometricAliasSafe(): String

    fun getIsProfileCompletedSafe(): Boolean
    fun setProfileCompleted(value: Boolean)
}

class PrefKeysImpl(
    private val prefsController: PrefsController
) : PrefKeys {


    /**
     * Retrieves the alias used for cryptographic operations from SharedPreferences.
     * This alias is typically used to identify a specific key or set of keys
     * stored in the Android Keystore system.
     *
     * @return The crypto alias string. Returns an empty string if the alias is not found
     *         or has not been set.
     */
    override fun getCryptoAlias(): String {
        return prefsController.getString("CryptoAlias", "")
    }


    /**
     * Stores the crypto alias used for the secret key in android keystore.
     * This is used for cryptographic operations not related to biometrics.
     *
     * @param value the crypto alias value.
     */
    override fun setCryptoAlias(value: String) {
        prefsController.setString("CryptoAlias", value)
    }

    override fun getBiometricAlias(): String {
        return prefsController.getString("BiometricAlias", "")
    }

    override fun setBiometricAlias(value: String) {
        prefsController.setString("BiometricAlias", value)
    }

    override fun getShowBatchIssuanceCounter(): Boolean {
        return prefsController.getBool("show_batch_issuance_counter", false)
    }

    override fun setShowBatchIssuanceCounter(value: Boolean) {
        prefsController.setBool("show_batch_issuance_counter", value)
    }

    override fun isWalletActivated(): Boolean = prefsController.getBool("is_wallet_activated", false)

    override fun setWalletActivated(isActivated: Boolean) {
        prefsController.setBool("is_wallet_activated", isActivated)
    }

    override fun isWalletActivatedSafe(): Boolean = prefsController.safeBool("is_wallet_activated", false)

    override fun getBiometricAliasSafe(): String = prefsController.safeString("BiometricAlias", "")

    override fun getIsProfileCompletedSafe(): Boolean {
        return prefsController.safeBool("profile_completed", false)
    }

    override fun setProfileCompleted(value: Boolean) {
        prefsController.setBool("profile_completed", value)
    }
}