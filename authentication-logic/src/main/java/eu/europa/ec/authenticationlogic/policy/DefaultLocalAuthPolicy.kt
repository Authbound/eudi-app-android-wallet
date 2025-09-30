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

package eu.europa.ec.authenticationlogic.policy



import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationController
import eu.europa.ec.authenticationlogic.controller.storage.BiometryStorageController
import eu.europa.ec.authenticationlogic.controller.storage.PinStorageController
import eu.europa.ec.authenticationlogic.gate.KeyGate


class DefaultLocalAuthPolicy(
    private val deviceAuth: DeviceAuthenticationController,
    private val biometryStorage: BiometryStorageController,
    private val pinStorage: PinStorageController,
    private val keyGate: KeyGate
) : LocalAuthPolicy {

    override suspend fun needsLocalUnlock(): Boolean {
        return keyGate.isKeyLocked()
    }

    override suspend fun isPinSet(): Boolean {
        return pinStorage.isPinCreated()
    }

    override suspend fun isBiometricsEnabledByUser(): Boolean {
        return biometryStorage.getUseBiometricsAuth()
    }

    override suspend fun isBiometricHardwareAvailable(): Boolean {
        return deviceAuth.canAuthenticateNow()
    }
}
