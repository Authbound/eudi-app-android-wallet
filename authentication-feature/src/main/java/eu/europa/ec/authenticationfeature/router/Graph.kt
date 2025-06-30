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
import eu.europa.ec.authenticationfeature.ui.LoginScreen
import eu.europa.ec.authenticationfeature.ui.WalletSetupScreen
import eu.europa.ec.uilogic.navigation.AuthenticationScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import org.koin.androidx.compose.koinViewModel

fun NavGraphBuilder.featureAuthenticationGraph(navController: NavController) {
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
            }
        )
    }

    composable(
        route = AuthenticationScreens.WalletSetup.screenRoute,
    ) {
        WalletSetupScreen(
            viewModel = koinViewModel()
        )
    }
} 