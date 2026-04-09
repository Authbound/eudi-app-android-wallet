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

package eu.europa.ec.authenticationfeature.di

import eu.europa.ec.authenticationfeature.ui.LegalAcceptanceViewModel
import eu.europa.ec.authenticationfeature.ui.AccountDeletionScheduledViewModel
import eu.europa.ec.authenticationlogic.usecase.CancelAccountDeletionUseCase
import eu.europa.ec.authenticationlogic.usecase.GetMyProfileUseCase
import eu.europa.ec.authenticationlogic.usecase.SignUpWithEmailPasswordUseCase
import eu.europa.ec.businesslogic.controller.device.DeviceController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.notificationlogic.controller.PushNotificationController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.walletactivationlogic.usecase.CreateWalletAttestationUseCase
import eu.europa.ec.walletactivationlogic.usecase.DeleteWalletActivationUseCase
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import eu.europa.ec.authenticationfeature.ui.AuthenticationViewModel
import eu.europa.ec.authenticationfeature.ui.DeviceSecurityRequiredViewModel
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricAuthenticationController
import eu.europa.ec.authenticationlogic.controller.storage.PinStorageController
import eu.europa.ec.authenticationlogic.usecase.CheckHandleAvailabilityUseCase
import eu.europa.ec.authenticationlogic.usecase.CompleteProfileUseCase
import eu.europa.ec.authenticationlogic.usecase.GetCurrentUserUseCase
import eu.europa.ec.authenticationlogic.usecase.GetLegalAcceptanceStateUseCase
import eu.europa.ec.authenticationlogic.usecase.ObserveAuthStateUseCase
import eu.europa.ec.authenticationlogic.usecase.RecordLegalAcceptanceUseCase
import eu.europa.ec.authenticationlogic.usecase.SignInWithEmailPasswordUseCase
import eu.europa.ec.authenticationlogic.usecase.SignInWithOAuthUseCase
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.authenticationfeature.ui.ProfileCompletionViewModel
import eu.europa.ec.authenticationfeature.ui.WalletSetupViewModel
import eu.europa.ec.notificationlogic.controller.UserScopedPushNotificationController

@Module
@ComponentScan("eu.europa.ec.authenticationfeature")
class FeatureAuthenticationModule

@Factory
fun provideAuthenticationViewModel(
    signInWithEmailPasswordUseCase: SignInWithEmailPasswordUseCase,
    signUpWithEmailPasswordUseCase: SignUpWithEmailPasswordUseCase,
    signInWithOAuthUseCase: SignInWithOAuthUseCase,
    signOutUseCase: SignOutUseCase,
    observeAuthStateUseCase: ObserveAuthStateUseCase,
    logController: LogController,
): AuthenticationViewModel = AuthenticationViewModel(
    signInWithEmailPasswordUseCase,
    signUpWithEmailPasswordUseCase,
    signInWithOAuthUseCase,
    signOutUseCase,
    observeAuthStateUseCase,
    logController
)

@Factory
fun provideLegalAcceptanceViewModel(
    getLegalAcceptanceStateUseCase: GetLegalAcceptanceStateUseCase,
    recordLegalAcceptanceUseCase: RecordLegalAcceptanceUseCase,
    signOutUseCase: SignOutUseCase,
    resourceProvider: ResourceProvider,
): LegalAcceptanceViewModel = LegalAcceptanceViewModel(
    getLegalAcceptanceStateUseCase,
    recordLegalAcceptanceUseCase,
    signOutUseCase,
    resourceProvider
)

@Factory
fun provideAccountDeletionScheduledViewModel(
    getMyProfileUseCase: GetMyProfileUseCase,
    cancelAccountDeletionUseCase: CancelAccountDeletionUseCase,
    signOutUseCase: SignOutUseCase,
    resourceProvider: ResourceProvider,
): AccountDeletionScheduledViewModel = AccountDeletionScheduledViewModel(
    getMyProfileUseCase,
    cancelAccountDeletionUseCase,
    signOutUseCase,
    resourceProvider
)

@Factory
fun provideDeviceSecurityRequiredViewModel(
    deviceController: DeviceController,
    getCurrentUserUseCase: GetCurrentUserUseCase,
    logController: LogController
): DeviceSecurityRequiredViewModel = DeviceSecurityRequiredViewModel(
    deviceController,
    getCurrentUserUseCase,
    logController
)

@Factory
fun provideWalletSetupViewModel(
    createWalletAttestationUseCase: CreateWalletAttestationUseCase,
    deleteWalletActivationUseCase: DeleteWalletActivationUseCase,
    signOutUseCase: SignOutUseCase,
    deviceController: DeviceController,
    biometricAuthenticationController: BiometricAuthenticationController,
    pushNotificationController: PushNotificationController,
    prefKeys: PrefKeysV2,
    prefsController: PrefsControllerV2,
    logController: LogController,
    pinStorageController: PinStorageController
): WalletSetupViewModel = WalletSetupViewModel(
    createWalletAttestationUseCase,
    deleteWalletActivationUseCase,
    signOutUseCase,
    deviceController,
    biometricAuthenticationController,
    pushNotificationController,
    prefKeys,
    prefsController,
    logController,
    pinStorageController
)

@Factory
fun provideProfileCompletionViewModel(
    completeProfileUseCase: CompleteProfileUseCase,
    checkHandleAvailabilityUseCase: CheckHandleAvailabilityUseCase,
    createWalletAttestationUseCase: CreateWalletAttestationUseCase,
    getCurrentUserUseCase: GetCurrentUserUseCase,
    userScopedPushNotificationController: UserScopedPushNotificationController,
    deviceController: DeviceController,
    signOutUseCase: SignOutUseCase,
    prefKeys: PrefKeysV2,
    logController: LogController
): ProfileCompletionViewModel = ProfileCompletionViewModel(
    completeProfileUseCase,
    checkHandleAvailabilityUseCase,
    createWalletAttestationUseCase,
    getCurrentUserUseCase,
    userScopedPushNotificationController,
    deviceController,
    signOutUseCase,
    prefKeys,
    logController
)
