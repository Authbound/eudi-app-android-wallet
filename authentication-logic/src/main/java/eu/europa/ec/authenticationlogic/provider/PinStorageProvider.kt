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

package eu.europa.ec.authenticationlogic.provider

import eu.europa.ec.authenticationlogic.model.LocalUnlockStatus
import eu.europa.ec.authenticationlogic.model.PinValidationResult
import eu.europa.ec.authenticationlogic.secure.SecurePin

interface PinStorageProvider {
    suspend fun retrievePin(): String
    suspend fun setPin(pin: SecurePin)
    suspend fun isPinValid(pin: SecurePin): Boolean
    suspend fun getLocalUnlockStatus(): LocalUnlockStatus = if (retrievePin().isBlank()) {
        LocalUnlockStatus.NotProvisioned
    } else {
        LocalUnlockStatus.ReadyForPin
    }
    suspend fun verifyPin(pin: SecurePin): PinValidationResult = if (isPinValid(pin)) {
        PinValidationResult.Success
    } else {
        PinValidationResult.Failed(remainingAttempts = Int.MAX_VALUE)
    }
    suspend fun prepareRecovery(): LocalUnlockStatus = LocalUnlockStatus.RecoveryRequired
    suspend fun clearPinData(userId: String? = null) {
        clearEphemeralSecrets()
    }
    suspend fun clearEphemeralSecrets() = Unit

}
