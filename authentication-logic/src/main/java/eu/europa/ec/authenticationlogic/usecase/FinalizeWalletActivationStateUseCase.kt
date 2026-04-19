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

package eu.europa.ec.authenticationlogic.usecase

import eu.europa.ec.authenticationlogic.controller.storage.RecoveryCheckpointController
import eu.europa.ec.authenticationlogic.storage.LocalAuthKeys
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2

interface FinalizeWalletActivationStateUseCase {
    suspend operator fun invoke(): Result<Unit>
}

class FinalizeWalletActivationStateUseCaseImpl(
    private val prefKeys: PrefKeysV2,
    private val prefsController: PrefsControllerV2,
    private val recoveryCheckpointController: RecoveryCheckpointController
) : FinalizeWalletActivationStateUseCase {

    override suspend fun invoke(): Result<Unit> {
        return runCatching {
            prefKeys.setWalletActivated(true)
            prefsController.setBool(LocalAuthKeys.ENROLLMENT_REQUIRED, true)
            recoveryCheckpointController.clearCheckpoint()
        }
    }
}
