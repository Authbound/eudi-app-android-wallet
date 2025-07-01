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
) : CreateWalletAttestationUseCase {
    override suspend fun invoke(
        deviceInfo: DeviceInfo,
        pushToken: String,
    ): Result<WalletActivationResponse> {
        val certificateChain = cryptoController.generateWuaKeyPair()
            ?: return Result.failure(Exception("Failed to generate key pair"))

        val publicKey = certificateChain.first()

        return walletActivationRepository.activateWallet(
            publicKey = publicKey,
            attestationChain = certificateChain,
            deviceInfo = deviceInfo,
            pushToken = pushToken,
        )
    }
} 