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

package eu.europa.ec.dashboardfeature.router

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import eu.europa.ec.dashboardfeature.BuildConfig
import eu.europa.ec.dashboardfeature.ui.actions.ActionsViewModel
import eu.europa.ec.dashboardfeature.ui.dashboard.DashboardScreen
import eu.europa.ec.dashboardfeature.ui.document_sign.DocumentSignScreen
import eu.europa.ec.dashboardfeature.ui.documents.detail.DocumentDetailsScreen
import eu.europa.ec.dashboardfeature.ui.mydata.MyDataScreen
import eu.europa.ec.dashboardfeature.ui.settings.SettingsScreen
import eu.europa.ec.dashboardfeature.ui.settings.AccountDetailsScreen
import eu.europa.ec.dashboardfeature.ui.transactions.detail.TransactionDetailsScreen
import eu.europa.ec.dashboardfeature.ui.verification.VerificationCustomCreationScreen
import eu.europa.ec.dashboardfeature.ui.verification.VerificationSharingScreen
import eu.europa.ec.dashboardfeature.ui.verification.VerificationTemplateSelectionScreen
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.ModuleRoute
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

fun NavGraphBuilder.featureDashboardGraph(navController: NavController) {
    navigation(
        startDestination = DashboardScreens.Dashboard.screenRoute,
        route = ModuleRoute.DashboardModule.route
    ) {
        composable(
            route = DashboardScreens.Dashboard.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK + DashboardScreens.Dashboard.screenRoute
                }
            )
        ) {
            DashboardScreen(
                hostNavController = navController,
                viewModel = koinViewModel(),
                documentsViewModel = koinViewModel(),
                homeViewModel = koinViewModel(),
                actionsViewModel = koinViewModel(),
                healthViewModel = koinViewModel(),
                settingsViewModel = koinViewModel(),
            )
        }

        composable(
            route = DashboardScreens.Settings.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK + DashboardScreens.Settings.screenRoute
                }
            ),
        ) {
            val actionsViewModel: ActionsViewModel = koinViewModel()
            val actionsState: eu.europa.ec.dashboardfeature.ui.actions.State by actionsViewModel
                .viewState
                .collectAsStateWithLifecycle()
            val onNotificationsClick: () -> Unit = {
                val previousEntry: androidx.navigation.NavBackStackEntry? =
                    navController.previousBackStackEntry
                if (previousEntry != null) {
                    previousEntry.savedStateHandle.set("openActions", true)
                    navController.popBackStack(DashboardScreens.Dashboard.screenRoute, false)
                } else {
                    navController.navigate(DashboardScreens.Dashboard.screenRoute)
                }
            }
            LaunchedEffect(Unit) {
                actionsViewModel.setEvent(eu.europa.ec.dashboardfeature.ui.actions.Event.OnResume)
            }
            SettingsScreen(
                navController = navController,
                viewModel = koinViewModel(),
                notificationCount = actionsState.pendingCount,
                onNotificationsClick = onNotificationsClick,
            )
        }

        composable(
            route = DashboardScreens.VerificationTemplateSelection.screenRoute,
        ) {
            val actionsViewModel: ActionsViewModel = koinViewModel()
            val actionsState: eu.europa.ec.dashboardfeature.ui.actions.State by actionsViewModel
                .viewState
                .collectAsStateWithLifecycle()
            val onNotificationsClick: () -> Unit = {
                val previousEntry: androidx.navigation.NavBackStackEntry? =
                    navController.previousBackStackEntry
                if (previousEntry != null) {
                    previousEntry.savedStateHandle.set("openActions", true)
                    navController.popBackStack(DashboardScreens.Dashboard.screenRoute, false)
                } else {
                    navController.navigate(DashboardScreens.Dashboard.screenRoute)
                }
            }
            LaunchedEffect(Unit) {
                actionsViewModel.setEvent(eu.europa.ec.dashboardfeature.ui.actions.Event.OnResume)
            }
            VerificationTemplateSelectionScreen(
                navController = navController,
                viewModel = koinViewModel(),
                notificationCount = actionsState.pendingCount,
                onNotificationsClick = onNotificationsClick,
            )
        }

        composable(
            route = DashboardScreens.VerificationCustomCreation.screenRoute,
        ) {
            val actionsViewModel: ActionsViewModel = koinViewModel()
            val actionsState: eu.europa.ec.dashboardfeature.ui.actions.State by actionsViewModel
                .viewState
                .collectAsStateWithLifecycle()
            val onNotificationsClick: () -> Unit = {
                val previousEntry: androidx.navigation.NavBackStackEntry? =
                    navController.previousBackStackEntry
                if (previousEntry != null) {
                    previousEntry.savedStateHandle.set("openActions", true)
                    navController.popBackStack(DashboardScreens.Dashboard.screenRoute, false)
                } else {
                    navController.navigate(DashboardScreens.Dashboard.screenRoute)
                }
            }
            LaunchedEffect(Unit) {
                actionsViewModel.setEvent(eu.europa.ec.dashboardfeature.ui.actions.Event.OnResume)
            }
            VerificationCustomCreationScreen(
                navController = navController,
                viewModel = koinViewModel(),
                notificationCount = actionsState.pendingCount,
                onNotificationsClick = onNotificationsClick,
            )
        }
        composable(
            route = DashboardScreens.VerificationSharing.screenRoute,
        ) {
            val actionsViewModel: ActionsViewModel = koinViewModel()
            val actionsState: eu.europa.ec.dashboardfeature.ui.actions.State by actionsViewModel
                .viewState
                .collectAsStateWithLifecycle()
            val onNotificationsClick: () -> Unit = {
                val previousEntry: androidx.navigation.NavBackStackEntry? =
                    navController.previousBackStackEntry
                if (previousEntry != null) {
                    previousEntry.savedStateHandle.set("openActions", true)
                    navController.popBackStack(DashboardScreens.Dashboard.screenRoute, false)
                } else {
                    navController.navigate(DashboardScreens.Dashboard.screenRoute)
                }
            }
            LaunchedEffect(Unit) {
                actionsViewModel.setEvent(eu.europa.ec.dashboardfeature.ui.actions.Event.OnResume)
            }
            VerificationSharingScreen(
                navController = navController,
                viewModel = koinViewModel(),
                notificationCount = actionsState.pendingCount,
                onNotificationsClick = onNotificationsClick,
            )
        }

        composable(
            route = DashboardScreens.AccountDetails.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK + DashboardScreens.AccountDetails.screenRoute
                }
            ),
        ) {
            AccountDetailsScreen(
                navController = navController,
                viewModel = koinViewModel()
            )
        }

        composable(
            route = DashboardScreens.DocumentSign.screenRoute
        ) {
            DocumentSignScreen(navController, koinViewModel())
        }

        composable(
            route = DashboardScreens.DocumentDetails.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK + DashboardScreens.DocumentDetails.screenRoute
                }
            ),
            arguments = listOf(
                navArgument("documentId") {
                    type = NavType.StringType
                },
            )
        ) {
            DocumentDetailsScreen(
                navController,
                koinViewModel(
                    parameters = {
                        parametersOf(
                            it.arguments?.getString("documentId").orEmpty(),
                        )
                    }
                )
            )
        }

        composable(
            route = DashboardScreens.TransactionDetails.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK + DashboardScreens.TransactionDetails.screenRoute
                }
            ),
            arguments = listOf(
                navArgument("transactionId") {
                    type = NavType.StringType
                },
            )
        ) {
            TransactionDetailsScreen(
                navController,
                koinViewModel(
                    parameters = {
                        parametersOf(
                            it.arguments?.getString("transactionId").orEmpty(),
                        )
                    }
                )
            )
        }

        composable(
            route = DashboardScreens.MyData.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK + DashboardScreens.MyData.screenRoute
                }
            )
        ) {
            MyDataScreen(
                navController = navController,
                viewModel = koinViewModel()
            )
        }
    }
}