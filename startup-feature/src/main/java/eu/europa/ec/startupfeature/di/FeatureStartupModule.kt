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

package eu.europa.ec.startupfeature.di

import eu.europa.ec.authenticationlogic.policy.LocalAuthPolicy
import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepository
import eu.europa.ec.authenticationlogic.usecase.IsProfileCompletedUseCase
import eu.europa.ec.authenticationlogic.usecase.IsWalletActivatedUseCase
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.commonfeature.interactor.QuickPinInteractor
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.startupfeature.interactor.SplashInteractor
import eu.europa.ec.startupfeature.interactor.SplashInteractorImpl
import eu.europa.ec.uilogic.serializer.UiSerializer
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module

@Module
@ComponentScan("eu.europa.ec.startupfeature")
class FeatureStartupModule

@Factory
fun provideSplashInteractor(
    supabaseAuthRepository: SupabaseAuthRepository,
    uiSerializer: UiSerializer,
    resourceProvider: ResourceProvider,
    prefsController: PrefsControllerV2,
    prefKeys: PrefKeysV2,
    logController: LogController,
    localAuthPolicy: LocalAuthPolicy,
    isWalletActivatedUseCase: IsWalletActivatedUseCase,
    isProfileCompletedUseCase: IsProfileCompletedUseCase,
    quickPinInteractor: QuickPinInteractor
): SplashInteractor = SplashInteractorImpl(
    supabaseAuthRepository,
    uiSerializer,
    resourceProvider,
    prefsController,
    prefKeys,
    logController,
    localAuthPolicy,
    isWalletActivatedUseCase,
    isProfileCompletedUseCase,
    quickPinInteractor
)
