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

import eu.europa.ec.authenticationlogic.controller.storage.BiometryStorageController
import eu.europa.ec.authenticationlogic.controller.storage.PinStorageController
import eu.europa.ec.authenticationlogic.controller.storage.RecoveryCheckpointController
import eu.europa.ec.authenticationlogic.controller.storage.WalletRecoveryChallengeController
import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.authenticationlogic.model.LocalRecoveryResetResult
import eu.europa.ec.authenticationlogic.model.RecoveryCheckpoint
import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepository
import eu.europa.ec.authenticationlogic.storage.LocalAuthKeys
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.crypto.KeystoreController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.businesslogic.controller.wallet.LocalWalletCleanupController

interface ResetLocalWalletForRecoveryUseCase {
    suspend operator fun invoke(): LocalRecoveryResetResult
}

class ResetLocalWalletForRecoveryUseCaseImpl(
    private val supabaseAuthRepository: SupabaseAuthRepository,
    private val pinStorageController: PinStorageController,
    private val localUnlockTracker: LocalUnlockTracker,
    private val biometryStorageController: BiometryStorageController,
    private val prefsController: PrefsControllerV2,
    private val prefKeys: PrefKeysV2,
    private val cryptoController: CryptoController,
    private val keystoreController: KeystoreController,
    private val localWalletCleanupController: LocalWalletCleanupController,
    private val recoveryCheckpointController: RecoveryCheckpointController,
    private val walletRecoveryChallengeController: WalletRecoveryChallengeController,
    private val logController: LogController
) : ResetLocalWalletForRecoveryUseCase {

    override suspend fun invoke(): LocalRecoveryResetResult {
        var destructiveCleanupStarted: Boolean = false
        return try {
            recoveryCheckpointController.setCheckpoint(RecoveryCheckpoint.LOCAL_RESET_IN_PROGRESS)
            runCatching { localUnlockTracker.lockNow() }
            runCatching { walletRecoveryChallengeController.clearPreparedChallenge() }
            val userId: String = supabaseAuthRepository.getCurrentUser()?.id
                ?: return rollbackCheckpointIfSafe(
                    destructiveCleanupStarted = destructiveCleanupStarted,
                    result = LocalRecoveryResetResult.SecurityFailure(
                        "Missing authenticated user for local wallet recovery reset."
                    )
                )
            destructiveCleanupStarted = true
            pinStorageController.clearPinData(userId)

            val failures: MutableList<String> = mutableListOf()
            failures += localWalletCleanupController.cleanupLocalWalletData()

            if (!cryptoController.deleteWuaKey()) {
                failures += "Delete wallet attestation key"
            }

            val biometricAlias: String = runCatching { prefKeys.getBiometricAliasSafe() }.getOrDefault("")
            runCatching { biometryStorageController.setBiometricAuthentication(null) }
                .onFailure { failures += "Clear biometric auth blob" }
            runCatching { biometryStorageController.setUseBiometricsAuth(false) }
                .onFailure { failures += "Disable biometric preference" }
            runCatching { biometryStorageController.setBiometricsPreferenceDecided(false) }
                .onFailure { failures += "Reset biometric preference state" }
            if (biometricAlias.isNotBlank()) {
                runCatching { keystoreController.deleteBiometricSecretKey(biometricAlias) }
                    .onFailure { failures += "Delete biometric key" }
            }
            runCatching { prefKeys.setBiometricAlias("") }
                .onFailure { failures += "Clear biometric alias" }
            runCatching { prefKeys.setWalletActivated(false) }
                .onFailure { failures += "Clear wallet activation flag" }
            runCatching { prefsController.setBool(LocalAuthKeys.ENROLLMENT_REQUIRED, false) }
                .onFailure { failures += "Clear enrollment flag" }

            if (failures.isNotEmpty()) {
                logController.w("ResetLocalWalletForRecovery") {
                    "Local recovery reset blocked by failures: $failures"
                }
                LocalRecoveryResetResult.LocalResetBlocked(
                    "Failed to complete local wallet reset for recovery."
                )
            } else {
                recoveryCheckpointController.setCheckpoint(RecoveryCheckpoint.ONLINE_REACTIVATION_REQUIRED)
                LocalRecoveryResetResult.LocalResetComplete
            }
        } catch (e: SecurityException) {
            logController.w("ResetLocalWalletForRecovery") { "Security failure: ${e.message}" }
            rollbackCheckpointIfSafe(
                destructiveCleanupStarted = destructiveCleanupStarted,
                result = LocalRecoveryResetResult.SecurityFailure(
                    e.message ?: "Local recovery reset failed due to a security error."
                )
            )
        } catch (e: Exception) {
            logController.w("ResetLocalWalletForRecovery") { "Reset blocked: ${e.message}" }
            rollbackCheckpointIfSafe(
                destructiveCleanupStarted = destructiveCleanupStarted,
                result = LocalRecoveryResetResult.LocalResetBlocked(
                    e.message ?: "Failed to complete local wallet reset for recovery."
                )
            )
        }
    }

    private suspend fun rollbackCheckpointIfSafe(
        destructiveCleanupStarted: Boolean,
        result: LocalRecoveryResetResult
    ): LocalRecoveryResetResult {
        if (!destructiveCleanupStarted) {
            runCatching { recoveryCheckpointController.clearCheckpoint() }
                .onFailure {
                    logController.w("ResetLocalWalletForRecovery") {
                        "Failed to roll back recovery checkpoint: ${it.message}"
                    }
                }
        }
        return result
    }
}
