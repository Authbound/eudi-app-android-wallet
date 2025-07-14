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

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.walletactivationlogic.repository.WalletActivationRepository

interface DeleteWalletActivationUseCase {
    suspend operator fun invoke(): Result<Unit>
}

class DeleteWalletActivationUseCaseImpl(
    private val walletActivationRepository: WalletActivationRepository,
    private val prefKeys: PrefKeys,
    private val logController: LogController
) : DeleteWalletActivationUseCase {
    
    override suspend fun invoke(): Result<Unit> {
        return try {
            logController.d("DeleteWalletActivation", "Starting wallet activation deletion...")
            
            // Delete from backend
            val deleteResult = walletActivationRepository.deleteWalletActivation()
            
            if (deleteResult.isSuccess) {
                // Clear local wallet activation flag
                try {
                    prefKeys.setWalletActivated(false)
                    logController.d("DeleteWalletActivation", "Local wallet activation flag cleared")
                } catch (e: SecurityException) {
                    logController.w("DeleteWalletActivation") { 
                        "Failed to clear local wallet activation flag due to security error: ${e.message}" 
                    }
                    // Continue - backend deletion succeeded even if local flag couldn't be cleared
                }
                
                logController.d("DeleteWalletActivation", "Wallet activation deleted successfully")
                Result.success(Unit)
            } else {
                val error = deleteResult.exceptionOrNull() ?: Exception("Unknown error")
                logController.e("DeleteWalletActivation", error)
                Result.failure(error)
            }
        } catch (e: Exception) {
            logController.e("DeleteWalletActivation", e)
            Result.failure(e)
        }
    }
}