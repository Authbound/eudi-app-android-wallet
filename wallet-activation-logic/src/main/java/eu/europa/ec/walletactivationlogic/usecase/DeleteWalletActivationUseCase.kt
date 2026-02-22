/*
 * Copyright (c) 2024 European Commission
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
package eu.europa.ec.walletactivationlogic.usecase

import eu.europa.ec.authenticationlogic.usecase.SignOutMode
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.businesslogic.controller.wallet.LocalWalletCleanupController
import eu.europa.ec.walletactivationlogic.repository.WalletActivationRepository

interface DeleteWalletActivationUseCase {
    suspend operator fun invoke(): Result<Unit>
}

class DeleteWalletActivationUseCaseImpl(
    private val walletActivationRepository: WalletActivationRepository,
    private val prefKeys: PrefKeys,
    private val signOutUseCase: SignOutUseCase,
    private val logController: LogController,
    private val cryptoController: CryptoController,
    private val localWalletCleanupController: LocalWalletCleanupController
) : DeleteWalletActivationUseCase {

    override suspend fun invoke(): Result<Unit> {
        return try {
            logController.d(TAG, "Starting wallet activation deletion...")

            // Step 1: Backend deletion (CRITICAL gate — abort if fails)
            val deleteResult = walletActivationRepository.deleteWalletActivation()
            if (deleteResult.isFailure) {
                val error = deleteResult.exceptionOrNull() ?: Exception("Unknown error")
                logController.e(TAG, error)
                return Result.failure(error)
            }
            logController.d(TAG, "Backend deletion succeeded")

            // Step 2: Delete WUA key from Android Keystore (SECURITY-CRITICAL, best-effort)
            runCatching {
                val deleted = cryptoController.deleteWuaKey()
                if (deleted) {
                    logController.d(TAG, "WUA key deleted from Keystore")
                } else {
                    logController.w(TAG) { "WUA key deletion returned false" }
                }
            }.onFailure { e ->
                logController.w(TAG) { "Failed to delete WUA key: ${e.message}" }
            }

            // Step 3: Clean local wallet data — documents + DB (best-effort)
            runCatching {
                val failures = localWalletCleanupController.cleanupLocalWalletData()
                if (failures.isNotEmpty()) {
                    logController.w(TAG) { "Local cleanup had failures: $failures" }
                }
            }.onFailure { e ->
                logController.w(TAG) { "Local wallet cleanup threw: ${e.message}" }
            }

            // Step 4: Clear activation flag (best-effort)
            runCatching {
                prefKeys.setWalletActivated(false)
                logController.d(TAG, "Local wallet activation flag cleared")
            }.onFailure { e ->
                logController.w(TAG) { "Failed to clear activation flag: ${e.message}" }
            }

            // Step 5: Hard sign-out — session, prefs, biometric keys (best-effort)
            runCatching {
                signOutUseCase(SignOutMode.Hard)
                logController.d(TAG, "User signed out after wallet deletion")
            }.onFailure { e ->
                logController.w(TAG) { "Failed to sign out: ${e.message}" }
            }

            logController.d(TAG, "Wallet activation deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            logController.e(TAG, e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "DeleteWalletActivation"
    }
}