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
package eu.europa.ec.walletactivationlogic.di

import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.walletactivationlogic.repository.WalletActivationRepository
import eu.europa.ec.walletactivationlogic.repository.WalletActivationRepositoryImpl
import eu.europa.ec.walletactivationlogic.usecase.CreateWalletAttestationUseCase
import eu.europa.ec.walletactivationlogic.usecase.CreateWalletAttestationUseCaseImpl
import eu.europa.ec.walletactivationlogic.usecase.DeleteWalletActivationUseCase
import eu.europa.ec.walletactivationlogic.usecase.DeleteWalletActivationUseCaseImpl
import io.github.jan.supabase.SupabaseClient

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys

import eu.europa.ec.networklogic.api.ApiClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module

@Module
@ComponentScan("eu.europa.ec.walletactivationlogic")
class FeatureWalletActivationModule

@Factory
fun provideWalletActivationRepository(
    supabaseClient: SupabaseClient,
    api: ApiClient,
    logController: LogController
): WalletActivationRepository = WalletActivationRepositoryImpl(supabaseClient, api, logController)

@Factory
fun provideCreateWalletAttestationUseCase(
    cryptoController: CryptoController,
    walletActivationRepository: WalletActivationRepository
): CreateWalletAttestationUseCase = CreateWalletAttestationUseCaseImpl(
    cryptoController,
    walletActivationRepository
)

@Factory
fun provideDeleteWalletActivationUseCase(
    walletActivationRepository: WalletActivationRepository,
    prefKeys: PrefKeys,
    signOutUseCase: SignOutUseCase,
    logController: LogController
): DeleteWalletActivationUseCase = DeleteWalletActivationUseCaseImpl(
    walletActivationRepository,
    prefKeys,
    signOutUseCase,
    logController
) 