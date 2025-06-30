/*
 * Copyright (c) 2023 European Commission
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

package eu.europa.ec.authenticationlogic.di

import eu.europa.ec.authenticationlogic.config.StorageConfig
import eu.europa.ec.authenticationlogic.config.StorageConfigImpl
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricAuthenticationController
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricAuthenticationControllerImpl
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationController
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationControllerImpl
import eu.europa.ec.authenticationlogic.controller.storage.BiometryStorageController
import eu.europa.ec.authenticationlogic.controller.storage.BiometryStorageControllerImpl
import eu.europa.ec.authenticationlogic.controller.storage.PinStorageController
import eu.europa.ec.authenticationlogic.controller.storage.PinStorageControllerImpl
import eu.europa.ec.authenticationlogic.provider.BiometryStorageProvider
import eu.europa.ec.authenticationlogic.provider.PinStorageProvider
import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepository
import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepositoryImpl
import eu.europa.ec.authenticationlogic.storage.PrefsBiometryStorageProvider
import eu.europa.ec.authenticationlogic.storage.PrefsPinStorageProvider
import eu.europa.ec.authenticationlogic.usecase.IsUserAuthenticatedUseCase
import eu.europa.ec.authenticationlogic.usecase.IsUserAuthenticatedUseCaseImpl
import eu.europa.ec.authenticationlogic.usecase.ObserveAuthStateUseCase
import eu.europa.ec.authenticationlogic.usecase.ObserveAuthStateUseCaseImpl
import eu.europa.ec.authenticationlogic.usecase.SignInWithEmailPasswordUseCase
import eu.europa.ec.authenticationlogic.usecase.SignInWithEmailPasswordUseCaseImpl
import eu.europa.ec.authenticationlogic.usecase.SignInWithOAuthUseCase
import eu.europa.ec.authenticationlogic.usecase.SignInWithOAuthUseCaseImpl
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCaseImpl
import eu.europa.ec.authenticationlogic.usecase.SignUpWithEmailPasswordUseCase
import eu.europa.ec.authenticationlogic.usecase.SignUpWithEmailPasswordUseCaseImpl
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.authenticationlogic.usecase.GetCurrentUserUseCase
import eu.europa.ec.authenticationlogic.usecase.GetCurrentUserUseCaseImpl
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("eu.europa.ec.authenticationlogic")
class LogicAuthenticationModule

@Single
fun provideStorageConfig(
    prefsController: PrefsController
): StorageConfig = StorageConfigImpl(
    pinImpl = PrefsPinStorageProvider(prefsController),
    biometryImpl = PrefsBiometryStorageProvider(prefsController)
)

@Factory
fun provideBiometricAuthenticationController(
    cryptoController: CryptoController,
    biometryStorageController: BiometryStorageController,
    resourceProvider: ResourceProvider
): BiometricAuthenticationController =
    BiometricAuthenticationControllerImpl(
        resourceProvider,
        cryptoController,
        biometryStorageController
    )

@Factory
fun provideDeviceAuthenticationController(
    resourceProvider: ResourceProvider,
    biometricAuthenticationController: BiometricAuthenticationController
): DeviceAuthenticationController =
    DeviceAuthenticationControllerImpl(
        resourceProvider,
        biometricAuthenticationController
    )

@Factory
fun providePinStorageController(
    storageConfig: StorageConfig
): PinStorageController = PinStorageControllerImpl(storageConfig)

@Factory
fun provideBiometryStorageController(
    storageConfig: StorageConfig
): BiometryStorageController = BiometryStorageControllerImpl(storageConfig)

@Factory
fun provideSupabaseAuthRepository(
    supabaseClient: io.github.jan.supabase.SupabaseClient
): SupabaseAuthRepository = SupabaseAuthRepositoryImpl(supabaseClient)

@Factory
fun provideIsUserAuthenticatedUseCase(
    supabaseAuthRepository: SupabaseAuthRepository
): IsUserAuthenticatedUseCase = IsUserAuthenticatedUseCaseImpl(supabaseAuthRepository)

@Factory
fun provideObserveAuthStateUseCase(
    supabaseAuthRepository: SupabaseAuthRepository
): ObserveAuthStateUseCase = ObserveAuthStateUseCaseImpl(supabaseAuthRepository)

@Factory
fun provideGetCurrentUserUseCase(
    supabaseAuthRepository: SupabaseAuthRepository
): GetCurrentUserUseCase = GetCurrentUserUseCaseImpl(supabaseAuthRepository)

@Factory
fun provideSignInWithEmailPasswordUseCase(
    supabaseAuthRepository: SupabaseAuthRepository
): SignInWithEmailPasswordUseCase = SignInWithEmailPasswordUseCaseImpl(supabaseAuthRepository)

@Factory
fun provideSignUpWithEmailPasswordUseCase(
    supabaseAuthRepository: SupabaseAuthRepository
): SignUpWithEmailPasswordUseCase = SignUpWithEmailPasswordUseCaseImpl(supabaseAuthRepository)

@Factory
fun provideSignInWithOAuthUseCase(
    supabaseAuthRepository: SupabaseAuthRepository
): SignInWithOAuthUseCase = SignInWithOAuthUseCaseImpl(supabaseAuthRepository)

@Factory
fun provideSignOutUseCase(
    supabaseAuthRepository: SupabaseAuthRepository
): SignOutUseCase = SignOutUseCaseImpl(supabaseAuthRepository)