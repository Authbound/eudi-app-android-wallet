/*
 * Copyright (c) 2024 European Commission
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

package eu.europa.ec.authenticationfeature.router

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import eu.europa.ec.authenticationfeature.ui.AccountDeletionScheduledScreen
import eu.europa.ec.authenticationfeature.ui.DeviceSecurityRequiredScreen
import eu.europa.ec.authenticationfeature.ui.LegalAcceptanceScreen
import eu.europa.ec.authenticationfeature.ui.LoginScreen
import eu.europa.ec.authenticationfeature.ui.ProfileCompletionScreen
import eu.europa.ec.authenticationfeature.ui.WalletSetupScreen
import eu.europa.ec.commonfeature.model.PinFlow
import eu.europa.ec.uilogic.navigation.AuthenticationScreens
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.StartupScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

fun NavGraphBuilder.featureAuthenticationGraph(navController: NavController) {
    val navigateToPinCreate: () -> Unit = {
        navController.navigate(
            generateComposableNavigationLink(
                screen = CommonScreens.QuickPin,
                arguments = generateComposableArguments(mapOf("pinFlow" to PinFlow.CREATE_WITH_ACTIVATION))
            )
        ) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    val navigateToPinVerify: () -> Unit = {
        navController.navigate(
            generateComposableNavigationLink(
                screen = CommonScreens.QuickPin,
                arguments = generateComposableArguments(mapOf("pinFlow" to PinFlow.VERIFY))
            )
        ) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    composable(
        route = AuthenticationScreens.Login.screenRoute,
    ) {
        LoginScreen(
            viewModel = koinViewModel(),
            onNavigateToHome = {
                navController.navigate(DashboardScreens.Dashboard.screenRoute) {
                    // Pop up to the start destination of the graph to
                    // avoid building up a large stack of destinations
                    // on the back stack as users select items
                    popUpTo(AuthenticationScreens.Login.screenRoute) {
                        inclusive = true
                    }
                    // Avoid multiple copies of the same destination when
                    // reselecting the same item
                    launchSingleTop = true
                }
            },
            onNavigateToStartup = {
                navController.navigate(StartupScreens.Splash.screenRoute) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onNavigateToWalletSetup = {
                navController.navigate(AuthenticationScreens.DeviceSecurityRequired.screenRoute)
            },
            onNavigateToProfileCompletion = {
                navController.navigate(AuthenticationScreens.ProfileCompletion.screenRoute)
            },
            onNavigateToPinCreate = navigateToPinCreate,
            onNavigateToPinVerify = navigateToPinVerify
        )
    }

    composable(
        route = AuthenticationScreens.AccountDeletionScheduled.screenRoute,
    ) {
        AccountDeletionScheduledScreen(
            viewModel = koinViewModel(),
            onNavigateToStartup = {
                navController.navigate(StartupScreens.Splash.screenRoute) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onNavigateToLogin = {
                navController.navigate(AuthenticationScreens.Login.screenRoute) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )
    }

    composable(
        route = AuthenticationScreens.LegalAcceptance.screenRoute,
    ) {
        LegalAcceptanceScreen(
            viewModel = koinViewModel(),
            onNavigateToStartup = {
                navController.navigate(StartupScreens.Splash.screenRoute) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onNavigateToLogin = {
                navController.navigate(AuthenticationScreens.Login.screenRoute) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )
    }

    composable(
        route = AuthenticationScreens.ProfileCompletion.screenRoute,
    ) {
        ProfileCompletionScreen(
            viewModel = koinViewModel(),
            onNavigateToWalletSetup = {
                navController.navigate(AuthenticationScreens.DeviceSecurityRequired.screenRoute) {
                    popUpTo(AuthenticationScreens.ProfileCompletion.screenRoute) {
                        inclusive = true
                    }
                }
            },
            onNavigateToLogin = {
                navController.navigate(AuthenticationScreens.Login.screenRoute) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )
    }

    composable(
        route = AuthenticationScreens.DeviceSecurityRequired.screenRoute,
    ) {
        val logController = koinInject<eu.europa.ec.businesslogic.controller.log.LogController>()
        DeviceSecurityRequiredScreen(
            logController = logController,
            onNavigateToWalletSetup = {
                navController.navigate(AuthenticationScreens.WalletSetup.screenRoute) {
                    popUpTo(AuthenticationScreens.DeviceSecurityRequired.screenRoute) {
                        inclusive = true
                    }
                }
            },
            onNavigateToLogin = {
                navController.navigate(AuthenticationScreens.Login.screenRoute) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )
    }

    composable(
        route = AuthenticationScreens.WalletSetup.screenRoute,
    ) {
        val logController = koinInject<eu.europa.ec.businesslogic.controller.log.LogController>()
        WalletSetupScreen(
            logController = logController,
            onNavigateToHome = {
                navController.navigate(DashboardScreens.Dashboard.screenRoute) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onNavigateToPinCreate = navigateToPinCreate,
            onNavigateToPinVerify = navigateToPinVerify,
            onNavigateToLogin = {
                navController.navigate(AuthenticationScreens.Login.screenRoute) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onNavigateToDeviceSecurity = {
                navController.navigate(AuthenticationScreens.DeviceSecurityRequired.screenRoute) {
                    popUpTo(AuthenticationScreens.WalletSetup.screenRoute) {
                        inclusive = true
                    }
                }
            }
        )
    }
} 
