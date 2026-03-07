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

package eu.europa.ec.authenticationlogic.storage

import com.google.gson.Gson
import eu.europa.ec.authenticationlogic.model.BiometricAuthentication
import eu.europa.ec.authenticationlogic.provider.BiometryStorageProvider
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2

class PrefsBiometryStorageProvider(
    private val prefsController: PrefsControllerV2
) : BiometryStorageProvider {

    companion object {
        private const val BIOMETRIC_AUTHENTICATION_KEY = "BiometricAuthentication"
        private const val USE_BIOMETRICS_AUTH_KEY = "UseBiometricsAuth"
        private const val BIOMETRICS_PREFERENCE_DECIDED_KEY = "BiometricsPreferenceDecided"
    }

    /**
     * Returns the biometric data in order to validate that biometric is not tampered in any way.
     */
    override suspend fun getBiometricAuthentication(): BiometricAuthentication? {
        return try {
            Gson().fromJson(
                prefsController.getString(BIOMETRIC_AUTHENTICATION_KEY, ""),
                BiometricAuthentication::class.java
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Stores the biometric data used to validate that biometric is not tampered in any way.
     *
     * @param value the biometric data.
     */
    override suspend fun setBiometricAuthentication(value: BiometricAuthentication?) {
        if (value == null) prefsController.clear(BIOMETRIC_AUTHENTICATION_KEY)
        else prefsController.setString(BIOMETRIC_AUTHENTICATION_KEY, Gson().toJson(value))
    }

    /**
     * Key to use Biometrics Auth instead of quick pin.
     *
     * Setting an empty value will clear the entry from shared prefs.
     */
    override suspend fun setUseBiometricsAuth(value: Boolean) {
        prefsController.setBool(USE_BIOMETRICS_AUTH_KEY, value)
    }

    /**
     * Key to use Biometrics Auth instead of quick pin.
     *
     * Setting an empty value will clear the entry from shared prefs.
     */
    override suspend fun getUseBiometricsAuth(): Boolean {
        return prefsController.getBool(USE_BIOMETRICS_AUTH_KEY, false)
    }

    override suspend fun setBiometricsPreferenceDecided(value: Boolean) {
        prefsController.setBool(BIOMETRICS_PREFERENCE_DECIDED_KEY, value)
    }

    override suspend fun getBiometricsPreferenceDecided(): Boolean {
        return prefsController.getBool(BIOMETRICS_PREFERENCE_DECIDED_KEY, false)
    }
}
