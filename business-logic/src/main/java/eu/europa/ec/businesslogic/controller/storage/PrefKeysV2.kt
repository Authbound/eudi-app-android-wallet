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
    suspend fun getRequiredTermsVersion(): String
    suspend fun setRequiredTermsVersion(value: String)
    suspend fun getAcceptedTermsVersion(): String
    suspend fun setAcceptedTermsVersion(value: String)
    suspend fun getAcceptedTermsAt(): String
    suspend fun setAcceptedTermsAt(value: String)
    suspend fun getRequiredPrivacyVersion(): String
    suspend fun setRequiredPrivacyVersion(value: String)
    suspend fun getAcknowledgedPrivacyVersion(): String
    suspend fun setAcknowledgedPrivacyVersion(value: String)
    suspend fun getAcknowledgedPrivacyAt(): String
    suspend fun setAcknowledgedPrivacyAt(value: String)

    // Safe methods (return defaults if no session)
    fun isWalletActivatedSafe(): Boolean
    fun getBiometricAliasSafe(): String
    fun isProfileCompletedSafe(): Boolean
    fun getRequiredTermsVersionSafe(): String
    fun getAcceptedTermsVersionSafe(): String
    fun getAcceptedTermsAtSafe(): String
    fun getRequiredPrivacyVersionSafe(): String
    fun getAcknowledgedPrivacyVersionSafe(): String
    fun getAcknowledgedPrivacyAtSafe(): String
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

    override suspend fun getRequiredTermsVersion(): String {
        return prefsController.getString("required_terms_version", "")
    }

    override suspend fun setRequiredTermsVersion(value: String) {
        prefsController.setString("required_terms_version", value)
    }

    override suspend fun getAcceptedTermsVersion(): String {
        return prefsController.getString("accepted_terms_version", "")
    }

    override suspend fun setAcceptedTermsVersion(value: String) {
        prefsController.setString("accepted_terms_version", value)
    }

    override suspend fun getAcceptedTermsAt(): String {
        return prefsController.getString("accepted_terms_at", "")
    }

    override suspend fun setAcceptedTermsAt(value: String) {
        prefsController.setString("accepted_terms_at", value)
    }

    override suspend fun getRequiredPrivacyVersion(): String {
        return prefsController.getString("required_privacy_version", "")
    }

    override suspend fun setRequiredPrivacyVersion(value: String) {
        prefsController.setString("required_privacy_version", value)
    }

    override suspend fun getAcknowledgedPrivacyVersion(): String {
        return prefsController.getString("acknowledged_privacy_version", "")
    }

    override suspend fun setAcknowledgedPrivacyVersion(value: String) {
        prefsController.setString("acknowledged_privacy_version", value)
    }

    override suspend fun getAcknowledgedPrivacyAt(): String {
        return prefsController.getString("acknowledged_privacy_at", "")
    }

    override suspend fun setAcknowledgedPrivacyAt(value: String) {
        prefsController.setString("acknowledged_privacy_at", value)
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

    override fun getRequiredTermsVersionSafe(): String {
        return prefsController.safeString("required_terms_version", "")
    }

    override fun getAcceptedTermsVersionSafe(): String {
        return prefsController.safeString("accepted_terms_version", "")
    }

    override fun getAcceptedTermsAtSafe(): String {
        return prefsController.safeString("accepted_terms_at", "")
    }

    override fun getRequiredPrivacyVersionSafe(): String {
        return prefsController.safeString("required_privacy_version", "")
    }

    override fun getAcknowledgedPrivacyVersionSafe(): String {
        return prefsController.safeString("acknowledged_privacy_version", "")
    }

    override fun getAcknowledgedPrivacyAtSafe(): String {
        return prefsController.safeString("acknowledged_privacy_at", "")
    }
}
