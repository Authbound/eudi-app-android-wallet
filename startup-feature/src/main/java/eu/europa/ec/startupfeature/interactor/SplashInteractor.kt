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

package eu.europa.ec.startupfeature.interactor

import eu.europa.ec.authenticationlogic.policy.LocalAuthPolicy
import eu.europa.ec.authenticationlogic.usecase.IsProfileCompletedUseCase
import eu.europa.ec.authenticationlogic.usecase.IsUserAuthenticatedUseCase
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.commonfeature.config.BiometricMode
import eu.europa.ec.commonfeature.config.BiometricUiConfig
import eu.europa.ec.commonfeature.config.IssuanceFlowType
import eu.europa.ec.commonfeature.config.IssuanceUiConfig
import eu.europa.ec.commonfeature.config.OnBackNavigationConfig
import eu.europa.ec.commonfeature.interactor.QuickPinInteractor
import eu.europa.ec.commonfeature.model.PinFlow
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.navigation.AuthenticationScreens
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.Screen
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer

interface SplashInteractor {
    suspend fun getAfterSplashRoute(): String
}


class SplashInteractorImpl(
    private val quickPinInteractor: QuickPinInteractor,
    private val uiSerializer: UiSerializer,
    private val resourceProvider: ResourceProvider,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val isUserAuthenticatedUseCase: IsUserAuthenticatedUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val prefKeys: PrefKeys,
    private val logController: LogController,
    private val localAuthPolicy: LocalAuthPolicy,
    private val isProfileCompletedUseCase: IsProfileCompletedUseCase
) : SplashInteractor {

    private val hasDocuments: Boolean
        get() = walletCoreDocumentsController.getAllDocuments().isNotEmpty()

    override suspend fun getAfterSplashRoute(): String {
        return try {
            // 1) Remote session (Supabase)
            val authed = isUserAuthenticatedUseCase()
            logController.i { "Authed: $authed" }
            if (!authed) return AuthenticationScreens.Login.screenRoute

            // 2) Profile completed? (handle + displayName)
            val profileCompleted = isProfileCompletedUseCase()   // see snippet below
            logController.i { "Profile completed: $profileCompleted" }
            if (!profileCompleted) return AuthenticationScreens.ProfileCompletion.screenRoute

            // 3) Wallet activation (WUA) completed?
            val walletActivated = prefKeys.isWalletActivatedSafe()
            logController.i { "Wallet activated: $walletActivated" }
            if (!walletActivated) return AuthenticationScreens.WalletSetup.screenRoute

            // 4) Local unlock policy (PIN/Biometrics). On success -> always Dashboard.
            val needsUnlock = localAuthPolicy.needsLocalUnlock()
            if (needsUnlock) {
                val useBiometric =
                    localAuthPolicy.isBiometricsEnabledByUser() &&
                            localAuthPolicy.isBiometricHardwareAvailable()

                if (useBiometric) {
                    biometricUnlockConfig(onSuccess = DashboardScreens.Dashboard.screenRoute)
                } else {
                    // Optional: if somehow PIN isn't set but walletActivated is true,
                    // you can route to WalletSetup (or a QuickPin CREATE) instead.
                    pinUnlockConfig(onSuccess = DashboardScreens.Dashboard.screenRoute)
                }
            } else {
                DashboardScreens.Dashboard.screenRoute
            }
        } catch (_: SecurityException) {
            try {
                signOutUseCase()
            } catch (_: Exception) {
            }
            AuthenticationScreens.Login.screenRoute
        } catch (_: Exception) {
            try {
                signOutUseCase()
            } catch (_: Exception) {
            }
            AuthenticationScreens.Login.screenRoute
        }
    }


    private fun pinUnlockConfig(onSuccess: String): String {
        return generateComposableNavigationLink(
            screen = CommonScreens.QuickPin,
            arguments = generateComposableArguments(
                mapOf(
                    "pinFlow" to PinFlow.UPDATE,
                    "onSuccessRoute" to onSuccess
                )
            )
        )
    }


    private fun biometricUnlockConfig(onSuccess: String): String {
        return generateComposableNavigationLink(
            screen = CommonScreens.Biometric,
            arguments = generateComposableArguments(
                mapOf(
                    BiometricUiConfig.serializedKeyName to uiSerializer.toBase64(
                        BiometricUiConfig(
                            mode = BiometricMode.Login(
                                title = resourceProvider.getString(R.string.biometric_login_title),
                                subTitleWhenBiometricsEnabled = resourceProvider.getString(
                                    R.string.biometric_login_biometrics_enabled_subtitle
                                ),
                                subTitleWhenBiometricsNotEnabled = resourceProvider.getString(
                                    R.string.biometric_login_biometrics_not_enabled_subtitle
                                ),
                            ),
                            isPreAuthorization = true,
                            shouldInitializeBiometricAuthOnCreate = true,
                            onSuccessNavigation = ConfigNavigation(
                                navigationType = NavigationType.PushScreen(
                                    screen = DashboardScreens.Dashboard
                                )
                            ),
                            onBackNavigationConfig = OnBackNavigationConfig(
                                onBackNavigation = ConfigNavigation(NavigationType.Finish),
                                hasToolbarBackIcon = false
                            )
                        ),
                        BiometricUiConfig.Parser
                    ).orEmpty(),
                    "onSuccessRoute" to onSuccess
                )
            )
        )
    }
}