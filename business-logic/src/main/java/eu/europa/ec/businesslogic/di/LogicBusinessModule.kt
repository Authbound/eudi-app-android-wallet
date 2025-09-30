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

package eu.europa.ec.businesslogic.di

import android.content.Context
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.config.ConfigLogicImpl
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.crypto.CryptoControllerImpl
import eu.europa.ec.businesslogic.controller.crypto.KeystoreController
import eu.europa.ec.businesslogic.controller.crypto.KeystoreControllerImpl
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.log.LogControllerImpl
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.businesslogic.controller.storage.PrefKeysImpl
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerImpl
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2Impl
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2Impl
import eu.europa.ec.businesslogic.controller.device.DeviceController
import eu.europa.ec.businesslogic.controller.device.DeviceControllerImpl
import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.businesslogic.provider.UuidProviderImpl
import eu.europa.ec.businesslogic.validator.FilterValidator
import eu.europa.ec.businesslogic.validator.FilterValidatorImpl
import eu.europa.ec.businesslogic.validator.FormValidator
import eu.europa.ec.businesslogic.validator.FormValidatorImpl
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import io.github.jan.supabase.SupabaseClient

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("eu.europa.ec.businesslogic")
class LogicBusinessModule

@Single
fun provideConfigLogic(context: Context): ConfigLogic = ConfigLogicImpl(context)

@Single
fun provideLogController(context: Context, configLogic: ConfigLogic): LogController =
    LogControllerImpl(context, configLogic)

// ============================================================
// V1 Controllers (Keep for backwards compatibility during migration)
// ============================================================
@Single
fun providePrefsController(
    resourceProvider: ResourceProvider,
    logController: LogController
): PrefsController = PrefsControllerImpl(resourceProvider, logController)

@Single
fun providePrefKeys(
    prefsController: PrefsController
): PrefKeys = PrefKeysImpl(prefsController)

// ============================================================
// V2 Controllers (New - Race condition free)
// ============================================================
@Single
fun providePrefsControllerV2(
    resourceProvider: ResourceProvider,
    logController: LogController,
    supabaseClient: SupabaseClient
): PrefsControllerV2 = PrefsControllerV2Impl(resourceProvider, logController, supabaseClient)

@Single
fun providePrefKeysV2(
    prefsController: PrefsControllerV2
): PrefKeysV2 = PrefKeysV2Impl(prefsController)

// ============================================================
// Keystore Controller (Updated to use V2)
// ============================================================
@Single
fun provideKeystoreController(
    prefKeys: PrefKeysV2,
    logController: LogController,
    uuidProvider: UuidProvider
): KeystoreController =
    KeystoreControllerImpl(prefKeys, logController, uuidProvider)

@Factory
fun provideCryptoController(keystoreController: KeystoreController): CryptoController =
    CryptoControllerImpl(keystoreController)

@Factory
fun provideFormValidator(logController: LogController): FormValidator =
    FormValidatorImpl(logController)

@Factory
fun provideFiltersValidator(): FilterValidator = FilterValidatorImpl()

@Single
fun provideUuidProvider(): UuidProvider {
    return UuidProviderImpl()
}

@Factory
fun provideDeviceController(resourceProvider: ResourceProvider): DeviceController = DeviceControllerImpl(resourceProvider)