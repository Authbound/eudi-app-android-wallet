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

package eu.europa.ec.authenticationlogic.di

import android.content.Context
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
import eu.europa.ec.authenticationlogic.controller.storage.RecoveryCheckpointController
import eu.europa.ec.authenticationlogic.controller.storage.RecoveryCheckpointControllerImpl
import eu.europa.ec.authenticationlogic.controller.storage.WalletRecoveryChallengeController
import eu.europa.ec.authenticationlogic.controller.storage.WalletRecoveryChallengeControllerImpl
import eu.europa.ec.authenticationlogic.gate.AppLockLifecycleObserver
import eu.europa.ec.authenticationlogic.gate.KeyGate
import eu.europa.ec.authenticationlogic.gate.KeyGateV2Impl
import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.authenticationlogic.policy.DefaultLocalAuthPolicy
import eu.europa.ec.authenticationlogic.policy.LocalAuthPolicy

import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepository
import eu.europa.ec.authenticationlogic.repository.SupabaseAuthRepositoryImpl
import eu.europa.ec.authenticationlogic.repository.ProfileRepository
import eu.europa.ec.authenticationlogic.repository.ProfileRepositoryImpl
import eu.europa.ec.authenticationlogic.repository.WalletSecurityRepository
import eu.europa.ec.authenticationlogic.repository.WalletSecurityRepositoryImpl
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.authenticationlogic.storage.PrefsBiometryStorageProvider
import eu.europa.ec.authenticationlogic.storage.PrefsPinStorageProvider
import eu.europa.ec.authenticationlogic.usecase.*
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCaseImpl
import eu.europa.ec.authenticationlogic.usecase.IsProfileCompletedUseCaseV2Impl
import eu.europa.ec.authenticationlogic.usecase.IsWalletActivatedUseCaseImpl
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.notificationlogic.controller.UserScopedPushNotificationController
import eu.europa.ec.businesslogic.controller.crypto.KeystoreController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import io.github.jan.supabase.SupabaseClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * V2 DI Module with fixed dependency injection and race condition prevention.
 *
 * Key Fixes:
 * 1. KeyGate and LocalUnlockTracker share same singleton instance
 * 2. Uses PrefsControllerV2 for automatic user context
 * 3. Proper scoping for all dependencies
 */
@Module
@ComponentScan("eu.europa.ec.authenticationlogic")
class LogicAuthenticationModule

// ============================================================
// Storage Configuration
// ============================================================

@Single
fun provideStorageConfig(
    prefsController: PrefsControllerV2,
    cryptoController: CryptoController
): StorageConfig = StorageConfigImpl(
    pinImpl = PrefsPinStorageProvider(prefsController, cryptoController),
    biometryImpl = PrefsBiometryStorageProvider(prefsController)
)

// ============================================================
// Authentication Controllers
// ============================================================

@Factory
fun provideBiometricAuthenticationController(
    cryptoController: CryptoController,
    biometryStorageController: BiometryStorageController,
    resourceProvider: ResourceProvider
): BiometricAuthenticationController =
    BiometricAuthenticationControllerImpl(
        resourceProvider,
        cryptoController,
        biometryStorageController,
    )

@Factory
fun provideDeviceAuthenticationController(
    resourceProvider: ResourceProvider,
    biometricAuthenticationController: BiometricAuthenticationController,
    localUnlockTracker: LocalUnlockTracker
): DeviceAuthenticationController =
    DeviceAuthenticationControllerImpl(
        resourceProvider,
        biometricAuthenticationController,
        localUnlockTracker
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
fun provideWalletRecoveryChallengeController(
    prefsController: PrefsControllerV2
): WalletRecoveryChallengeController = WalletRecoveryChallengeControllerImpl(prefsController)

@Factory
fun provideRecoveryCheckpointController(
    prefsController: PrefsControllerV2
): RecoveryCheckpointController = RecoveryCheckpointControllerImpl(prefsController)

// ============================================================
// Repositories
// ============================================================

@Factory
fun provideSupabaseAuthRepository(
    context: Context,
    supabaseClient: SupabaseClient
): SupabaseAuthRepository = SupabaseAuthRepositoryImpl(context, supabaseClient)

@Factory
fun provideProfileRepository(
    apiClient: ApiClient,
    supabaseClient: SupabaseClient,
    logController: LogController
): ProfileRepository = ProfileRepositoryImpl(apiClient, supabaseClient, logController)

@Factory
fun provideWalletSecurityRepository(
    apiClient: ApiClient,
    supabaseClient: SupabaseClient,
    walletRecoveryChallengeController: WalletRecoveryChallengeController,
    logController: LogController
): WalletSecurityRepository = WalletSecurityRepositoryImpl(
    apiClient,
    supabaseClient,
    walletRecoveryChallengeController,
    logController
)

// ============================================================
// Use Cases
// ============================================================

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
    supabaseAuthRepository: SupabaseAuthRepository,
    prefsController: PrefsControllerV2,
    keystoreController: KeystoreController,
    prefKeys: PrefKeysV2,
    pinStorageController: PinStorageController,
    localUnlockTracker: LocalUnlockTracker,
    logController: LogController,
    pushNotificationController: UserScopedPushNotificationController
): SignOutUseCase = SignOutUseCaseImpl(
    supabaseAuthRepository,
    prefsController,
    keystoreController,
    prefKeys,
    pinStorageController,
    localUnlockTracker,
    logController,
    pushNotificationController
)

@Factory
fun provideCompleteProfileUseCase(
    profileRepository: ProfileRepository
): CompleteProfileUseCase = CompleteProfileUseCaseImpl(profileRepository)

@Factory
fun provideCheckHandleAvailabilityUseCase(
    profileRepository: ProfileRepository
): CheckHandleAvailabilityUseCase = CheckHandleAvailabilityUseCaseImpl(profileRepository)

@Factory
fun provideGetMyProfileUseCase(
    profileRepository: ProfileRepository
): GetMyProfileUseCase = GetMyProfileUseCaseImpl(profileRepository)

@Factory
fun provideRequestAccountDeletionUseCase(
    profileRepository: ProfileRepository
): RequestAccountDeletionUseCase = RequestAccountDeletionUseCaseImpl(profileRepository)

@Factory
fun provideCancelAccountDeletionUseCase(
    profileRepository: ProfileRepository
): CancelAccountDeletionUseCase = CancelAccountDeletionUseCaseImpl(profileRepository)

@Factory
fun provideReportWalletSecurityIncidentUseCase(
    walletSecurityRepository: WalletSecurityRepository
): ReportWalletSecurityIncidentUseCase = ReportWalletSecurityIncidentUseCaseImpl(walletSecurityRepository)

@Factory
fun providePrepareWalletRecoveryUseCase(
    walletSecurityRepository: WalletSecurityRepository
): PrepareWalletRecoveryUseCase = PrepareWalletRecoveryUseCaseImpl(walletSecurityRepository)

@Factory
fun provideResolveLocalAuthRouteUseCase(): ResolveLocalAuthRouteUseCase =
    ResolveLocalAuthRouteUseCaseImpl()

@Factory
fun provideFinalizeWalletActivationStateUseCase(
    prefKeys: PrefKeysV2,
    prefsController: PrefsControllerV2,
    recoveryCheckpointController: RecoveryCheckpointController
): FinalizeWalletActivationStateUseCase = FinalizeWalletActivationStateUseCaseImpl(
    prefKeys,
    prefsController,
    recoveryCheckpointController
)

@Factory
fun provideResetLocalWalletForRecoveryUseCase(
    supabaseAuthRepository: SupabaseAuthRepository,
    pinStorageController: PinStorageController,
    localUnlockTracker: LocalUnlockTracker,
    biometryStorageController: BiometryStorageController,
    prefsController: PrefsControllerV2,
    prefKeys: PrefKeysV2,
    cryptoController: CryptoController,
    keystoreController: KeystoreController,
    localWalletCleanupController: eu.europa.ec.businesslogic.controller.wallet.LocalWalletCleanupController,
    recoveryCheckpointController: RecoveryCheckpointController,
    walletRecoveryChallengeController: WalletRecoveryChallengeController,
    logController: LogController
): ResetLocalWalletForRecoveryUseCase = ResetLocalWalletForRecoveryUseCaseImpl(
    supabaseAuthRepository,
    pinStorageController,
    localUnlockTracker,
    biometryStorageController,
    prefsController,
    prefKeys,
    cryptoController,
    keystoreController,
    localWalletCleanupController,
    recoveryCheckpointController,
    walletRecoveryChallengeController,
    logController
)

@Factory
fun provideGetLegalAcceptanceStateUseCase(
    prefKeys: PrefKeysV2,
    profileRepository: ProfileRepository,
    logController: LogController
): GetLegalAcceptanceStateUseCase =
    GetLegalAcceptanceStateUseCaseImpl(prefKeys, profileRepository, logController)

@Factory
fun provideGetCachedLegalAcceptanceUseCase(
    prefKeys: PrefKeysV2
): GetCachedLegalAcceptanceUseCase = GetCachedLegalAcceptanceUseCaseImpl(prefKeys)

@Factory
fun provideRecordLegalAcceptanceUseCase(
    profileRepository: ProfileRepository,
    prefKeys: PrefKeysV2,
    configLogic: eu.europa.ec.businesslogic.config.ConfigLogic
): RecordLegalAcceptanceUseCase =
    RecordLegalAcceptanceUseCaseImpl(profileRepository, prefKeys, configLogic)

@Single
fun provideIsProfileCompletedUseCase(
    prefKeys: PrefKeysV2,
    profileRepository: ProfileRepository,
    logController: LogController
): IsProfileCompletedUseCase =
    IsProfileCompletedUseCaseV2Impl(prefKeys, profileRepository, logController)

@Factory
fun provideIsWalletActivatedUseCase(
    prefKeys: PrefKeysV2,
    keystoreController: KeystoreController,
    logController: LogController
): IsWalletActivatedUseCase =
    IsWalletActivatedUseCaseImpl(prefKeys, keystoreController, logController)

// ============================================================
// Policy & Gate (Fixed DI Bug)
// ============================================================

@Factory
fun provideLocalAuthenticationPolicy(
    deviceAuth: DeviceAuthenticationController,
    biometryStorage: BiometryStorageController,
    keyGate: KeyGate
): LocalAuthPolicy =
    DefaultLocalAuthPolicy(deviceAuth, biometryStorage, keyGate)


@Single
fun provideKeyGateImpl(
    prefs: PrefsControllerV2,
    prefKeys: PrefKeysV2,
    pinStorage: PinStorageController,
    logController: LogController
): KeyGateV2Impl = KeyGateV2Impl(prefs, prefKeys, pinStorage, logController)

@Factory
fun provideKeyGate(impl: KeyGateV2Impl): KeyGate = impl

@Factory
fun provideLocalUnlockTracker(impl: KeyGateV2Impl): LocalUnlockTracker = impl

@Single
fun provideAppLockLifecycleObserver(impl: KeyGateV2Impl): AppLockLifecycleObserver =
    AppLockLifecycleObserver(impl)
