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

package eu.europa.ec.commonfeature.interactor

import android.content.Context
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricAuthenticationController
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAuthenticate
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.authenticationlogic.controller.storage.BiometryStorageController
import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.authenticationlogic.secure.SecurePinImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

interface BiometricInteractor {
    fun getBiometricsAvailability(): BiometricsAvailability
    fun getBiometricsAvailability(listener: (BiometricsAvailability) -> Unit) {
        listener(getBiometricsAvailability())
    }
    suspend fun getBiometricUserSelection(): Boolean
    suspend fun storeBiometricsUsageDecision(shouldUseBiometrics: Boolean)
    suspend fun getBiometricsPreferenceDecided(): Boolean
    suspend fun storeBiometricsPreferenceDecided(value: Boolean)
    fun authenticateWithBiometrics(
        context: Context,
        notifyOnAuthenticationFailure: Boolean,
        listener: (BiometricsAuthenticate) -> Unit
    )

    fun launchBiometricSystemScreen()
    fun isPinValid(pin: String): Flow<QuickPinInteractorPinValidPartialState>
}

class BiometricInteractorImpl(
    private val biometryStorageController: BiometryStorageController,
    private val biometricAuthenticationController: BiometricAuthenticationController,
    private val quickPinInteractor: QuickPinInteractor,
    private val localUnlockTracker: LocalUnlockTracker,
    private val coroutineScope: CoroutineScope,
) : BiometricInteractor {

    override fun isPinValid(pin: String): Flow<QuickPinInteractorPinValidPartialState> =
        quickPinInteractor.isCurrentPinValid(SecurePinImpl(pin))

    override suspend fun storeBiometricsUsageDecision(shouldUseBiometrics: Boolean) {
        biometryStorageController.setUseBiometricsAuth(shouldUseBiometrics)
    }

    override suspend fun getBiometricUserSelection(): Boolean {
        return biometryStorageController.getUseBiometricsAuth()
    }

    override suspend fun getBiometricsPreferenceDecided(): Boolean {
        return biometryStorageController.getBiometricsPreferenceDecided()
    }

    override suspend fun storeBiometricsPreferenceDecided(value: Boolean) {
        biometryStorageController.setBiometricsPreferenceDecided(value)
    }

    override fun getBiometricsAvailability(): BiometricsAvailability {
        return biometricAuthenticationController.getBiometricsAvailability()
    }

    override fun authenticateWithBiometrics(
        context: Context,
        notifyOnAuthenticationFailure: Boolean,
        listener: (BiometricsAuthenticate) -> Unit
    ) {
        biometricAuthenticationController.authenticate(
            context,
            notifyOnAuthenticationFailure
        ) { result ->
            // Mark as unlocked on successful biometric authentication
            if (result is BiometricsAuthenticate.Success) {
                coroutineScope.launch(Dispatchers.IO) {
                    localUnlockTracker.markUnlocked()
                }
            }
            // Forward result to caller
            listener(result)
        }
    }

    override fun launchBiometricSystemScreen() {
        biometricAuthenticationController.launchBiometricSystemScreen()
    }
}
