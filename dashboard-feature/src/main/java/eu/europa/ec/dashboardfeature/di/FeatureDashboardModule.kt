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

package eu.europa.ec.dashboardfeature.di

import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.authenticationlogic.usecase.GetCurrentUserUseCase
import eu.europa.ec.authenticationlogic.usecase.GetMyProfileUseCase
import eu.europa.ec.authenticationlogic.usecase.IsUserAuthenticatedUseCase
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.businesslogic.validator.FilterValidator
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.dashboardfeature.interactor.ActionsInteractor
import eu.europa.ec.dashboardfeature.interactor.ActionsInteractorImpl
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractor
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractorImpl
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractor
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractorImpl
import eu.europa.ec.dashboardfeature.interactor.DocumentSignInteractor
import eu.europa.ec.dashboardfeature.interactor.DocumentSignInteractorImpl
import eu.europa.ec.dashboardfeature.interactor.DocumentsInteractor
import eu.europa.ec.dashboardfeature.interactor.DocumentsInteractorImpl
import eu.europa.ec.dashboardfeature.interactor.HealthInteractor
import eu.europa.ec.dashboardfeature.interactor.HealthInteractorImpl
import eu.europa.ec.dashboardfeature.interactor.HomeInteractor
import eu.europa.ec.dashboardfeature.interactor.HomeInteractorImpl
import eu.europa.ec.dashboardfeature.interactor.MyDataInteractor
import eu.europa.ec.dashboardfeature.interactor.MyDataInteractorImpl
import eu.europa.ec.dashboardfeature.interactor.SettingsInteractor
import eu.europa.ec.dashboardfeature.interactor.SettingsInteractorImpl
import eu.europa.ec.dashboardfeature.interactor.TransactionDetailsInteractor
import eu.europa.ec.dashboardfeature.interactor.TransactionDetailsInteractorImpl
import eu.europa.ec.dashboardfeature.interactor.TransactionsInteractor
import eu.europa.ec.dashboardfeature.interactor.TransactionsInteractorImpl
import eu.europa.ec.dashboardfeature.repository.ActionsRepository
import eu.europa.ec.dashboardfeature.repository.ActionsRepositoryImpl
import eu.europa.ec.dashboardfeature.repository.MaisaRepository
import eu.europa.ec.dashboardfeature.repository.MaisaRepositoryImpl
import eu.europa.ec.dashboardfeature.repository.VerificationRepository
import eu.europa.ec.dashboardfeature.repository.VerificationRepositoryImpl
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import io.github.jan.supabase.SupabaseClient

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("eu.europa.ec.dashboardfeature")
class FeatureDashboardModule

@Factory
fun provideDashboardInteractor(
    resourceProvider: ResourceProvider,
    configLogic: ConfigLogic,
    getCurrentUserUseCase: GetCurrentUserUseCase,
    getMyProfileUseCase: GetMyProfileUseCase,
    walletCoreDocumentsController: WalletCoreDocumentsController,
): DashboardInteractor = DashboardInteractorImpl(
    resourceProvider,
    configLogic,
    getCurrentUserUseCase,
    getMyProfileUseCase,
    walletCoreDocumentsController,
)

@Single
fun provideActionsRepository(
    apiClient: ApiClient,
    supabaseClient: SupabaseClient,
    resourceProvider: ResourceProvider,
    logController: LogController,
    cryptoController: CryptoController
): ActionsRepository = ActionsRepositoryImpl(
    apiClient,
    supabaseClient,
    resourceProvider,
    logController,
    cryptoController
)

@Factory
fun provideActionsInteractor(
    resourceProvider: ResourceProvider,
    actionsRepository: ActionsRepository,
): ActionsInteractor = ActionsInteractorImpl(
    resourceProvider,
    actionsRepository,
)

@Factory
fun provideMaisaRepository(
    apiClient: ApiClient,
    supabaseClient: SupabaseClient,
    prefsController: PrefsControllerV2,
    logController: LogController
): MaisaRepository = MaisaRepositoryImpl(
    apiClient,
    supabaseClient,
    prefsController,
    logController
)

@Factory
fun provideHealthInteractor(
    resourceProvider: ResourceProvider,
    prefsController: PrefsControllerV2,
    walletCoreDocumentsController: WalletCoreDocumentsController,
    maisaRepository: MaisaRepository
): HealthInteractor = HealthInteractorImpl(
    resourceProvider,
    prefsController,
    walletCoreDocumentsController,
    maisaRepository
)

@Factory
fun provideMyDataInteractor(
    resourceProvider: ResourceProvider,
): MyDataInteractor = MyDataInteractorImpl(
    resourceProvider,
)

@Factory
fun provideSettingsInteractor(
    configLogic: ConfigLogic,
    logController: LogController,
    resourceProvider: ResourceProvider,
    prefKeys: PrefKeys,
    prefsController: PrefsControllerV2,
    walletCoreDocumentsController: WalletCoreDocumentsController,
    getCurrentUserUseCase: GetCurrentUserUseCase,
    signOutUseCase: SignOutUseCase,
    isUserAuthenticatedUseCase: IsUserAuthenticatedUseCase,
    getMyProfileUseCase: GetMyProfileUseCase,
): SettingsInteractor = SettingsInteractorImpl(
    configLogic,
    logController,
    resourceProvider,
    prefKeys,
    prefsController,
    walletCoreDocumentsController,
    getCurrentUserUseCase,
    signOutUseCase,
    isUserAuthenticatedUseCase,
    getMyProfileUseCase
)

@Factory
fun provideHomeInteractor(
    resourceProvider: ResourceProvider,
    walletCoreDocumentsController: WalletCoreDocumentsController,
    walletCoreConfig: WalletCoreConfig,
): HomeInteractor = HomeInteractorImpl(
    resourceProvider,
    walletCoreDocumentsController,
    walletCoreConfig,
)

@Factory
fun provideDocumentsInteractor(
    resourceProvider: ResourceProvider,
    documentsController: WalletCoreDocumentsController,
    filterValidator: FilterValidator,
): DocumentsInteractor =
    DocumentsInteractorImpl(
        resourceProvider,
        documentsController,
        filterValidator,
    )

@Factory
fun provideTransactionInteractor(
    resourceProvider: ResourceProvider,
    filterValidator: FilterValidator,
    walletCoreDocumentsController: WalletCoreDocumentsController,
): TransactionsInteractor = TransactionsInteractorImpl(
    resourceProvider,
    filterValidator,
    walletCoreDocumentsController
)

@Factory
fun provideDocumentSignInteractor(
    resourceProvider: ResourceProvider,
): DocumentSignInteractor = DocumentSignInteractorImpl(
    resourceProvider,
)

@Factory
fun provideDocumentDetailsInteractor(
    walletCoreDocumentsController: WalletCoreDocumentsController,
    resourceProvider: ResourceProvider,
    uuidProvider: UuidProvider,
): DocumentDetailsInteractor =
    DocumentDetailsInteractorImpl(
        walletCoreDocumentsController,
        resourceProvider,
        uuidProvider,
    )

@Factory
fun provideTransactionDetailsInteractor(
    walletCoreDocumentsController: WalletCoreDocumentsController,
    resourceProvider: ResourceProvider,
    uuidProvider: UuidProvider
): TransactionDetailsInteractor =
    TransactionDetailsInteractorImpl(
        walletCoreDocumentsController,
        resourceProvider,
        uuidProvider
    )

@Single
fun provideVerificationRepository(
    resourceProvider: ResourceProvider,
): VerificationRepository = VerificationRepositoryImpl(resourceProvider)
