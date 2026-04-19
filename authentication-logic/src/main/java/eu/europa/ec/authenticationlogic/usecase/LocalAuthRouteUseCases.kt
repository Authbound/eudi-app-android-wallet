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

import eu.europa.ec.authenticationlogic.model.LocalAuthRouteDecision
import eu.europa.ec.authenticationlogic.model.LocalUnlockStatus
import eu.europa.ec.authenticationlogic.model.RecoveryCheckpoint

interface ResolveLocalAuthRouteUseCase {
    operator fun invoke(
        localUnlockStatus: LocalUnlockStatus,
        enrollmentRequired: Boolean,
        recoveryCheckpoint: RecoveryCheckpoint,
        isUnlocked: Boolean
    ): LocalAuthRouteDecision
}

class ResolveLocalAuthRouteUseCaseImpl : ResolveLocalAuthRouteUseCase {
    override fun invoke(
        localUnlockStatus: LocalUnlockStatus,
        enrollmentRequired: Boolean,
        recoveryCheckpoint: RecoveryCheckpoint,
        isUnlocked: Boolean
    ): LocalAuthRouteDecision {
        if (recoveryCheckpoint != RecoveryCheckpoint.NONE) {
            return LocalAuthRouteDecision.SecurityError("Wallet recovery is still in progress")
        }
        return when (localUnlockStatus) {
            LocalUnlockStatus.NotProvisioned -> {
                if (enrollmentRequired) {
                    LocalAuthRouteDecision.PinCreate
                } else {
                    LocalAuthRouteDecision.PinRecoveryRequired
                }
            }
            LocalUnlockStatus.RecoveryRequired -> LocalAuthRouteDecision.PinRecoveryRequired
            LocalUnlockStatus.TamperDetected -> LocalAuthRouteDecision.SecurityError(
                "Local auth state failed integrity checks"
            )
            is LocalUnlockStatus.TemporarilyLocked -> LocalAuthRouteDecision.PinVerificationRequired
            LocalUnlockStatus.ReadyForPin -> {
                if (isUnlocked) {
                    LocalAuthRouteDecision.Ready
                } else {
                    LocalAuthRouteDecision.PinVerificationRequired
                }
            }
        }
    }
}
