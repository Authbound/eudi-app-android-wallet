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

package eu.europa.ec.commonfeature.di

import eu.europa.ec.authenticationlogic.controller.authentication.BiometricAuthenticationController
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationController
import eu.europa.ec.authenticationlogic.controller.storage.BiometryStorageController
import eu.europa.ec.authenticationlogic.controller.storage.PinStorageController
import eu.europa.ec.authenticationlogic.gate.LocalUnlockTracker
import eu.europa.ec.businesslogic.validator.FormValidator
import eu.europa.ec.commonfeature.interactor.BiometricInteractor
import eu.europa.ec.commonfeature.interactor.BiometricInteractorImpl
import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractor
import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractorImpl
import eu.europa.ec.commonfeature.interactor.QrScanInteractor
import eu.europa.ec.commonfeature.interactor.QrScanInteractorImpl
import eu.europa.ec.commonfeature.interactor.QuickPinInteractor
import eu.europa.ec.commonfeature.interactor.QuickPinInteractorImpl
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("eu.europa.ec.commonfeature")
class FeatureCommonModule

@Single
fun provideApplicationScope(): CoroutineScope {
    return CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Factory
fun provideQuickPinInteractor(
    formValidator: FormValidator,
    pinStorageController: PinStorageController,
    resourceProvider: ResourceProvider,
    localUnlockTracker: LocalUnlockTracker
): QuickPinInteractor {
    return QuickPinInteractorImpl(
        formValidator,
        pinStorageController,
        resourceProvider,
        localUnlockTracker
    )
}

@Factory
fun provideBiometricInteractor(
    biometryStorageController: BiometryStorageController,
    biometricAuthenticationController: BiometricAuthenticationController,
    quickPinInteractor: QuickPinInteractor,
    localUnlockTracker: LocalUnlockTracker,
    coroutineScope: CoroutineScope
): BiometricInteractor {
    return BiometricInteractorImpl(
        biometryStorageController,
        biometricAuthenticationController,
        quickPinInteractor,
        localUnlockTracker,
        coroutineScope
    )
}

@Factory
fun provideDeviceAuthenticationInteractor(
    deviceAuthenticationController: DeviceAuthenticationController
): DeviceAuthenticationInteractor {
    return DeviceAuthenticationInteractorImpl(deviceAuthenticationController)
}

@Factory
fun provideQrScanInteractor(
    formValidator: FormValidator
): QrScanInteractor {
    return QrScanInteractorImpl(formValidator)
}