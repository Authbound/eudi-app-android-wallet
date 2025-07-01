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

import eu.europa.ec.authenticationlogic.usecase.SignUpWithEmailPasswordUseCase
import eu.europa.ec.businesslogic.controller.device.DeviceController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.notificationlogic.controller.PushNotificationController
import eu.europa.ec.walletactivationlogic.usecase.CreateWalletAttestationUseCase
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import eu.europa.ec.authenticationfeature.ui.AuthenticationViewModel
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricAuthenticationController
import eu.europa.ec.authenticationlogic.usecase.ObserveAuthStateUseCase
import eu.europa.ec.authenticationlogic.usecase.SignInWithEmailPasswordUseCase
import eu.europa.ec.authenticationlogic.usecase.SignInWithOAuthUseCase

@Module
@ComponentScan("eu.europa.ec.authenticationfeature")
class FeatureAuthenticationModule

@Factory
fun provideAuthenticationViewModel(
    signInWithEmailPasswordUseCase: SignInWithEmailPasswordUseCase,
    signUpWithEmailPasswordUseCase: SignUpWithEmailPasswordUseCase,
    signInWithOAuthUseCase: SignInWithOAuthUseCase,
    observeAuthStateUseCase: ObserveAuthStateUseCase,
    createWalletAttestationUseCase: CreateWalletAttestationUseCase,
    deviceController: DeviceController,
    biometricAuthenticationController: BiometricAuthenticationController,
    pushNotificationController: PushNotificationController,
    prefKeys: PrefKeys,
    logController: LogController
): AuthenticationViewModel = AuthenticationViewModel(
    signInWithEmailPasswordUseCase,
    signUpWithEmailPasswordUseCase,
    signInWithOAuthUseCase,
    observeAuthStateUseCase,
    createWalletAttestationUseCase,
    deviceController,
    biometricAuthenticationController,
    pushNotificationController,
    prefKeys,
    logController,
)
