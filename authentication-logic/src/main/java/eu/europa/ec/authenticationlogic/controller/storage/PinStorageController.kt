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

package eu.europa.ec.authenticationlogic.controller.storage

import eu.europa.ec.authenticationlogic.config.StorageConfig
import eu.europa.ec.authenticationlogic.model.LocalUnlockStatus
import eu.europa.ec.authenticationlogic.model.PinValidationResult

interface PinStorageController {
    suspend fun retrievePin(): String
    suspend fun setPin(pin: String)
    suspend fun isPinValid(pin: String): Boolean
    suspend fun getLocalUnlockStatus(): LocalUnlockStatus = if (retrievePin().isBlank()) {
        LocalUnlockStatus.NotProvisioned
    } else {
        LocalUnlockStatus.ReadyForPin
    }
    suspend fun verifyPin(pin: String): PinValidationResult = if (isPinValid(pin)) {
        PinValidationResult.Success
    } else {
        PinValidationResult.Failed(remainingAttempts = Int.MAX_VALUE)
    }
    suspend fun prepareRecovery(): LocalUnlockStatus = LocalUnlockStatus.RecoveryRequired
    suspend fun clearPinData(userId: String? = null) {
        setPin("")
    }
    suspend fun clearEphemeralSecrets() = Unit

}

class PinStorageControllerImpl(private val storageConfig: StorageConfig) : PinStorageController {
    override suspend fun retrievePin(): String = storageConfig.pinStorageProvider.retrievePin()

    override suspend fun setPin(pin: String) {
        storageConfig.pinStorageProvider.setPin(pin)
    }

    override suspend fun isPinValid(pin: String): Boolean =
        storageConfig.pinStorageProvider.isPinValid(pin)

    override suspend fun getLocalUnlockStatus(): LocalUnlockStatus =
        storageConfig.pinStorageProvider.getLocalUnlockStatus()

    override suspend fun verifyPin(pin: String): PinValidationResult =
        storageConfig.pinStorageProvider.verifyPin(pin)

    override suspend fun prepareRecovery(): LocalUnlockStatus =
        storageConfig.pinStorageProvider.prepareRecovery()

    override suspend fun clearPinData(userId: String?) {
        storageConfig.pinStorageProvider.clearPinData(userId)
    }

    override suspend fun clearEphemeralSecrets() {
        storageConfig.pinStorageProvider.clearEphemeralSecrets()
    }
}
