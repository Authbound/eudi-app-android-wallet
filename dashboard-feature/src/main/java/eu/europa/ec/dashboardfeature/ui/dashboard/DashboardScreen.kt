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

package eu.europa.ec.dashboardfeature.ui.dashboard

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import eu.europa.ec.dashboardfeature.ui.BottomNavigationBar
import eu.europa.ec.dashboardfeature.ui.BottomNavigationItem
import eu.europa.ec.dashboardfeature.ui.add_credentials.AddCredentialsScreen
import eu.europa.ec.dashboardfeature.ui.add_credentials.AddCredentialsViewModel
import eu.europa.ec.dashboardfeature.ui.documents.DocumentsScreen
import eu.europa.ec.dashboardfeature.ui.documents.DocumentsViewModel
import eu.europa.ec.dashboardfeature.ui.home.HomeScreen
import eu.europa.ec.dashboardfeature.ui.home.HomeViewModel
import eu.europa.ec.dashboardfeature.ui.settings.SettingsScreen
import eu.europa.ec.dashboardfeature.ui.settings.SettingsViewModel
import eu.europa.ec.dashboardfeature.ui.sidemenu.SideMenuScreen
import eu.europa.ec.dashboardfeature.ui.transactions.TransactionsScreen
import eu.europa.ec.dashboardfeature.ui.transactions.TransactionsViewModel
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.extension.finish
import eu.europa.ec.uilogic.extension.getPendingDeepLink
import eu.europa.ec.uilogic.extension.openAppSettings
import eu.europa.ec.uilogic.extension.openBleSettings
import eu.europa.ec.uilogic.extension.openIntentChooser
import eu.europa.ec.uilogic.extension.openUrl
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.helper.handleDeepLinkAction
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.compose.koinViewModel

@Composable
fun DashboardScreen(
    hostNavController: NavController,
    viewModel: DashboardViewModel,
    documentsViewModel: DocumentsViewModel,
    homeViewModel: HomeViewModel,
    transactionsViewModel: TransactionsViewModel,
    settingsViewModel: SettingsViewModel,
    addCredentialsViewModel: AddCredentialsViewModel
) {
    val context = LocalContext.current
    val bottomNavigationController = rememberNavController()
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    // Remove the excessive bottom padding that's creating the gap
    // val extraBottomPadding = 80.dp

    // Handle navigation effects from child screens
    LaunchedEffect(Unit) {
        homeViewModel.effect.collect { effect ->
            if (effect is eu.europa.ec.dashboardfeature.ui.home.Effect.Navigation.SwitchTab) {
                // Navigate to the specified tab
                bottomNavigationController.navigate(effect.tabRoute) {
                    popUpTo(bottomNavigationController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    Scaffold(
        // The floating bottom bar is added as a regular bottom bar
        bottomBar = { BottomNavigationBar(bottomNavigationController) },
        // Set windowInsets to zero to properly handle the floating bar
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        NavHost(
            modifier = Modifier
                .fillMaxSize(),
                // Apply padding from Scaffold directly without extra padding
                //.padding(padding),
            navController = bottomNavigationController,
            startDestination = BottomNavigationItem.Home.route
        ) {
            composable(BottomNavigationItem.Home.route) {
                HomeScreen(
                    hostNavController,
                    homeViewModel,
                    bottomNavigationController,
                    onDashboardEventSent = { event ->
                        viewModel.setEvent(event)
                    }
                )
            }
            composable(BottomNavigationItem.Documents.route) {
                DocumentsScreen(
                    hostNavController,
                    documentsViewModel,
                    onDashboardEventSent = { event ->
                        viewModel.setEvent(event)
                    }
                )
            }
            composable(BottomNavigationItem.Transactions.route) {
                TransactionsScreen(hostNavController, transactionsViewModel)
            }
            composable(route = BottomNavigationItem.AddCredential.route) {
                AddCredentialsScreen(
                    hostNavController, addCredentialsViewModel,
                )
            }


            composable(BottomNavigationItem.Settings.route) {
                SettingsScreen(
                    hostNavController,
                    settingsViewModel
                )
            }

        }
    }

    AnimatedVisibility(
        visible = state.isSideMenuVisible,
        modifier = Modifier.fillMaxSize(),
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = when (state.sideMenuAnimation) {
            SideMenuAnimation.SLIDE -> slideOutHorizontally(targetOffsetX = { it })
            SideMenuAnimation.FADE -> fadeOut(animationSpec = tween(state.menuAnimationDuration))
        }
    ) {
        SideMenuScreen(
            state = state,
            onEventSent = { event -> viewModel.setEvent(event) }
        )
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        viewModel.setEvent(
            Event.Init(
                deepLinkUri = context.getPendingDeepLink()
            )
        )
    }

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> handleNavigationEffect(effect, hostNavController, context)

                is Effect.ShareLogFile -> {
                    context.openIntentChooser(
                        effect.intent,
                        effect.chooserTitle
                    )
                }
            }
        }.collect()
    }
}

private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
    context: Context
) {
    when (navigationEffect) {
        is Effect.Navigation.Pop -> context.finish()
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute) {
                popUpTo(navigationEffect.popUpToScreenRoute) {
                    inclusive = navigationEffect.inclusive
                }
            }
        }

        is Effect.Navigation.OpenDeepLinkAction -> {
            handleDeepLinkAction(
                navController,
                navigationEffect.deepLinkUri,
                navigationEffect.arguments
            )
        }

        is Effect.Navigation.OnAppSettings -> context.openAppSettings()
        is Effect.Navigation.OnSystemSettings -> context.openBleSettings()
        is Effect.Navigation.OpenUrlExternally -> context.openUrl(uri = navigationEffect.url)
    }
}