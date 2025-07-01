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
import org.koin.compose.koinInject

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
            },
            onNavigateToWalletSetup = {
                navController.navigate(AuthenticationScreens.WalletSetup.screenRoute)
            }
        )
    }

    composable(
        route = AuthenticationScreens.WalletSetup.screenRoute,
    ) {
        val logController = koinInject<eu.europa.ec.businesslogic.controller.log.LogController>()
        
        WalletSetupScreen(
            viewModel = koinViewModel(),
            logController = logController,
            onPopBackStack = {
                logController.d("Graph", "onPopBackStack called" )
                // Handle normal back navigation (when coming from Login)
                val canPop = navController.previousBackStackEntry != null
                logController.d("Graph", "Can pop back: $canPop, previous entry: ${navController.previousBackStackEntry?.destination?.route}")
                
                if (canPop) {
                    logController.d("Graph", "Executing navController.popBackStack()")
                    navController.popBackStack()
                } else {
                    logController.d("Graph", "No back stack available - this should be handled by ViewModel logout")
                    // This case should now be rare since ViewModel handles logout for DIRECT/UNKNOWN sources
                    // But as fallback, navigate to Login with clear stack
                    navController.navigate(AuthenticationScreens.Login.screenRoute) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                logController.d("Graph", "onPopBackStack completed")
            },
            onNavigateToLogin = {
                logController.d("Graph", "onNavigateToLogin called")
                // Handle direct navigation to Login (when coming directly from Splash)
                navController.navigate(AuthenticationScreens.Login.screenRoute) {
                    popUpTo(AuthenticationScreens.WalletSetup.screenRoute) {
                        inclusive = true
                    }
                    // Ensure we don't create multiple Login instances
                    launchSingleTop = true
                }
                logController.d("Graph", "onNavigateToLogin completed")
            },
            onSignOutAndNavigateToLogin = {
                logController.d("Graph", "onSignOutAndNavigateToLogin called - User will be signed out")
                // Handle sign out + navigation to Login (when user backs out of wallet setup)
                // The actual sign out is handled by the ViewModel, we just handle navigation
                navController.navigate(AuthenticationScreens.Login.screenRoute) {
                    // Clear the entire back stack to prevent any navigation confusion
                    popUpTo(0) {
                        inclusive = true
                    }
                    // Ensure we don't create multiple Login instances
                    launchSingleTop = true
                }
                logController.d("Graph", "onSignOutAndNavigateToLogin completed - Navigated to Login with clear stack")
            }
        )
    }
} 