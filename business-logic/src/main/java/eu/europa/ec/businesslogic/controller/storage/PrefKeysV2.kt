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

/**
 * Type-safe preference keys with automatic user context handling.
 * 
 * This interface works with PrefsControllerV2 which automatically
 * derives user context from Supabase session.
 */
interface PrefKeysV2 {
    // Crypto keys
    suspend fun getCryptoAlias(): String
    suspend fun setCryptoAlias(value: String)
    suspend fun getBiometricAlias(): String
    suspend fun setBiometricAlias(value: String)

    // App settings
    suspend fun getShowBatchIssuanceCounter(): Boolean
    suspend fun setShowBatchIssuanceCounter(value: Boolean)

    // Wallet state
    suspend fun isWalletActivated(): Boolean
    suspend fun setWalletActivated(isActivated: Boolean)
    suspend fun isProfileCompleted(): Boolean
    suspend fun setProfileCompleted(value: Boolean)

    // Safe methods (return defaults if no session)
    fun isWalletActivatedSafe(): Boolean
    fun getBiometricAliasSafe(): String
    fun isProfileCompletedSafe(): Boolean
}

class PrefKeysV2Impl(
    private val prefsController: PrefsControllerV2
) : PrefKeysV2 {

    override suspend fun getCryptoAlias(): String {
        return prefsController.getString("CryptoAlias", "")
    }

    override suspend fun setCryptoAlias(value: String) {
        prefsController.setString("CryptoAlias", value)
    }

    override suspend fun getBiometricAlias(): String {
        return prefsController.getString("BiometricAlias", "")
    }

    override suspend fun setBiometricAlias(value: String) {
        prefsController.setString("BiometricAlias", value)
    }

    override suspend fun getShowBatchIssuanceCounter(): Boolean {
        return prefsController.getBool("show_batch_issuance_counter", false)
    }

    override suspend fun setShowBatchIssuanceCounter(value: Boolean) {
        prefsController.setBool("show_batch_issuance_counter", value)
    }

    override suspend fun isWalletActivated(): Boolean {
        return prefsController.getBool("is_wallet_activated", false)
    }

    override suspend fun setWalletActivated(isActivated: Boolean) {
        prefsController.setBool("is_wallet_activated", isActivated)
    }

    override suspend fun isProfileCompleted(): Boolean {
        return prefsController.getBool("profile_completed", false)
    }

    override suspend fun setProfileCompleted(value: Boolean) {
        prefsController.setBool("profile_completed", value)
    }

    // ============================================================
    // Safe Methods (Return Defaults If No Session)
    // ============================================================

    override fun isWalletActivatedSafe(): Boolean {
        return prefsController.safeBool("is_wallet_activated", false)
    }

    override fun getBiometricAliasSafe(): String {
        return prefsController.safeString("BiometricAlias", "")
    }

    override fun isProfileCompletedSafe(): Boolean {
        return prefsController.safeBool("profile_completed", false)
    }
}
