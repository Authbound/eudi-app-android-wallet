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

package eu.europa.ec.authenticationlogic.controller.storage

import eu.europa.ec.authenticationlogic.model.RecoveryCheckpoint
import eu.europa.ec.authenticationlogic.storage.LocalAuthKeys
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2

interface RecoveryCheckpointController {
    suspend fun getCheckpoint(): RecoveryCheckpoint
    suspend fun setCheckpoint(checkpoint: RecoveryCheckpoint)
    suspend fun clearCheckpoint()
}

class RecoveryCheckpointControllerImpl(
    private val prefsController: PrefsControllerV2
) : RecoveryCheckpointController {

    override suspend fun getCheckpoint(): RecoveryCheckpoint {
        val persistedValue: String = prefsController.getString(
            LocalAuthKeys.RECOVERY_CHECKPOINT,
            RecoveryCheckpoint.NONE.name
        )
        return RecoveryCheckpoint.fromPersistedValue(persistedValue)
    }

    override suspend fun setCheckpoint(checkpoint: RecoveryCheckpoint) {
        prefsController.setString(LocalAuthKeys.RECOVERY_CHECKPOINT, checkpoint.name)
    }

    override suspend fun clearCheckpoint() {
        prefsController.clear(LocalAuthKeys.RECOVERY_CHECKPOINT)
    }
}
