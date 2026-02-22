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

import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.model.error.WalletActivationError
import eu.europa.ec.businesslogic.extension.hexToByteArray
import eu.europa.ec.businesslogic.model.DeviceInfo
import eu.europa.ec.networklogic.model.response.WalletActivationResponse
import eu.europa.ec.walletactivationlogic.repository.WalletActivationRepository

interface CreateWalletAttestationUseCase {
    suspend operator fun invoke(
        deviceInfo: DeviceInfo,
        pushToken: String,
    ): Result<WalletActivationResponse>
}

class CreateWalletAttestationUseCaseImpl(
    private val cryptoController: CryptoController,
    private val walletActivationRepository: WalletActivationRepository,
    private val logController: LogController,
) : CreateWalletAttestationUseCase {

    override suspend fun invoke(
        deviceInfo: DeviceInfo,
        pushToken: String,
    ): Result<WalletActivationResponse> {
        return invokeInternal(deviceInfo, pushToken, isConflictRetry = false)
    }

    private suspend fun invokeInternal(
        deviceInfo: DeviceInfo,
        pushToken: String,
        isConflictRetry: Boolean,
    ): Result<WalletActivationResponse> {
        // Step 1: Get attestation challenge from backend
        val challengeResponse = walletActivationRepository.getAttestationChallenge()
            .getOrElse { error ->
                return Result.failure(
                    if (error is WalletActivationError) error
                    else WalletActivationError.UnexpectedError(error)
                )
            }

        // Step 2: Convert hex-encoded challenge to bytes
        val challengeBytes = try {
            challengeResponse.challenge.hexToByteArray()
        } catch (e: Exception) {
            return Result.failure(
                WalletActivationError.ServerRejection(
                    httpCode = 400,
                    serverMessage = "Invalid challenge format: ${e.message}"
                )
            )
        }

        // Step 3: Generate WUA key pair with the challenge embedded in attestation
        val certificateChain = try {
            cryptoController.generateWuaKeyPair(challengeBytes) ?: run {
                // Clean up any partial key state on failure
                cryptoController.deleteWuaKey()
                return Result.failure(
                    WalletActivationError.CryptographicFailure(
                        operation = "WUA key pair generation",
                        cause = Exception("KeyStore returned null certificate chain")
                    )
                )
            }
        } catch (e: Exception) {
            // Clean up any partial key state on failure
            cryptoController.deleteWuaKey()
            return Result.failure(
                WalletActivationError.CryptographicFailure(
                    operation = "WUA key pair generation",
                    cause = e
                )
            )
        }

        val publicKey = certificateChain.first()

        // Step 4: Activate wallet with attestation chain and challenge ID
        val activationResult = walletActivationRepository.activateWallet(
            publicKey = publicKey,
            attestationChain = certificateChain,
            challengeId = challengeResponse.challengeId,
            deviceInfo = deviceInfo,
            pushToken = pushToken,
        )

        if (activationResult.isFailure) {
            val error = activationResult.exceptionOrNull()

            // Auto-recover from 409 Conflict: delete old WUA from server and retry once
            if (error is WalletActivationError.WalletAlreadyExists && !isConflictRetry) {
                logController.i("CreateWalletAttestation") {
                    "409 Conflict: WUA already exists on server. Deleting old WUA and retrying..."
                }
                cryptoController.deleteWuaKey()

                val deleteResult = walletActivationRepository.deleteWalletActivation()
                if (deleteResult.isFailure) {
                    logController.e("CreateWalletAttestation") {
                        "Failed to delete old WUA: ${deleteResult.exceptionOrNull()?.message}"
                    }
                    return Result.failure(error)
                }

                logController.i("CreateWalletAttestation") {
                    "Old WUA deleted successfully, retrying activation with fresh key..."
                }
                return invokeInternal(deviceInfo, pushToken, isConflictRetry = true)
            }

            // For all other errors (or failed conflict retry): clean up key and propagate
            cryptoController.deleteWuaKey()
        }

        return activationResult
    }
} 