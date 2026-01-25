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

package eu.europa.ec.quickidfeature.di

import eu.europa.ec.quickidfeature.interactor.QuickIdIntroInteractor
import eu.europa.ec.quickidfeature.interactor.QuickIdIntroInteractorImpl
import eu.europa.ec.quickidlogic.controller.PassportNfcController
import eu.europa.ec.quickidlogic.interactor.QuickIdSessionInteractor
import eu.europa.ec.quickidlogic.interactor.VerificationInteractor
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.serializer.UiSerializer
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module

@Module
@ComponentScan("eu.europa.ec.quickidfeature")
class FeatureQuickIdModule

@Factory
fun provideQuickIdIntroInteractor(
    quickIdSessionInteractor: QuickIdSessionInteractor,
    resourceProvider: ResourceProvider
): QuickIdIntroInteractor = QuickIdIntroInteractorImpl(
    quickIdSessionInteractor,
    resourceProvider
)
