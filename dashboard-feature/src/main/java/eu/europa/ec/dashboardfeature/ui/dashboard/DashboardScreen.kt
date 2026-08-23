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

package eu.europa.ec.dashboardfeature.ui.dashboard

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import eu.europa.ec.businesslogic.extension.getParcelableArrayListExtra
import eu.europa.ec.corelogic.model.RevokedDocumentDataDomain
import eu.europa.ec.commonfeature.ui.qr_scan.DEVICE_LINKING_PAIRING_PAYLOAD_RESULT_KEY
import eu.europa.ec.corelogic.util.CoreActions
import eu.europa.ec.dashboardfeature.ui.component.BottomNavigationBar
import eu.europa.ec.dashboardfeature.ui.component.BottomNavigationItem
import eu.europa.ec.dashboardfeature.ui.dashboard.sidemenu.SideMenuScreen
import eu.europa.ec.dashboardfeature.ui.documents.detail.DocumentDetailsViewModel
import eu.europa.ec.dashboardfeature.ui.documents.list.DocumentsViewModel
import eu.europa.ec.dashboardfeature.ui.home.HomeScreen
import eu.europa.ec.dashboardfeature.ui.home.HomeViewModel
import eu.europa.ec.dashboardfeature.ui.settings.SettingsScreen
import eu.europa.ec.dashboardfeature.ui.settings.SettingsViewModel
import eu.europa.ec.dashboardfeature.ui.wallet.WalletScreen
import eu.europa.ec.dashboardfeature.ui.wallet.WalletTab

import eu.europa.ec.dashboardfeature.ui.actions.ActionsViewModel
import eu.europa.ec.dashboardfeature.ui.health.HealthViewModel
import eu.europa.ec.dashboardfeature.ui.verification.VerificationHomeScreen
import eu.europa.ec.dashboardfeature.ui.verification.VerificationHomeViewModel
import org.koin.androidx.compose.koinViewModel
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.SystemBroadcastReceiver
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.wrap.BottomSheetTextDataUi
import eu.europa.ec.uilogic.component.wrap.BottomSheetWithOptionsList
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import eu.europa.ec.uilogic.extension.applyTestTag
import eu.europa.ec.uilogic.extension.finish
import eu.europa.ec.uilogic.extension.getPendingDeepLink
import eu.europa.ec.uilogic.extension.getPendingIntent
import eu.europa.ec.uilogic.extension.openAppSettings
import eu.europa.ec.uilogic.extension.openBleSettings
import eu.europa.ec.uilogic.extension.openIntentChooser
import eu.europa.ec.uilogic.extension.openUrl
import eu.europa.ec.uilogic.navigation.helper.handleDeepLinkAction
import eu.europa.ec.uilogic.navigation.helper.handleIntentAction
import eu.europa.ec.uilogic.test.DashboardTestTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
internal fun DashboardScreen(
    hostNavController: NavController,
    viewModel: DashboardViewModel,
    documentsViewModel: DocumentsViewModel,
    homeViewModel: HomeViewModel,
    actionsViewModel: ActionsViewModel,
    healthViewModel: HealthViewModel,
    settingsViewModel: SettingsViewModel,
) {
    val context: Context = LocalContext.current
    val featureComingSoonMessage: String = stringResource(R.string.feature_coming_soon)
    val bottomNavigationController: NavHostController = rememberNavController()
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val actionsState: eu.europa.ec.dashboardfeature.ui.actions.State by actionsViewModel.viewState.collectAsStateWithLifecycle()
    val scope: CoroutineScope = rememberCoroutineScope()
    val bottomSheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val notificationCount: Int = actionsState.pendingCount
    val onNotificationsClick: () -> Unit = {
        bottomNavigationController.navigate(
            "${BottomNavigationItem.Wallet.route}?tab=${WalletTab.Actions.routeValue}"
        ) {
            popUpTo(bottomNavigationController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    Scaffold(
        modifier = Modifier.applyTestTag(DashboardTestTags.Screen.ROOT),
        bottomBar = {
            BottomNavigationBar(
                navController = bottomNavigationController,
                onQrScanClick = {
                    viewModel.setEvent(Event.QrScanPressed)
                }
            )
        }
    ) { _ ->
        NavHost(
            modifier = Modifier
                .fillMaxSize(),
            navController = bottomNavigationController,
            startDestination = BottomNavigationItem.Home.route
        ) {
            composable(BottomNavigationItem.Home.route) {
                HomeScreen(
                    navHostController = hostNavController,
                    viewModel = homeViewModel,
                    bottomNavHostController = bottomNavigationController,
                    notificationCount = notificationCount,
                    onNotificationsClick = onNotificationsClick,
                    onDashboardEventSent = { event ->
                        viewModel.setEvent(event)
                    }
                )
            }
            composable(
                route = "${BottomNavigationItem.Wallet.route}?tab={tab}",
                arguments = listOf(
                    navArgument("tab") {
                        defaultValue = WalletTab.Documents.routeValue
                    }
                )
            ) { backStackEntry ->
                val selectedTab = WalletTab.fromRouteValue(
                    backStackEntry.arguments?.getString("tab")
                )
                WalletScreen(
                    navController = hostNavController,
                    documentsViewModel = documentsViewModel,
                    actionsViewModel = actionsViewModel,
                    healthViewModel = healthViewModel,
                    selectedTab = selectedTab,
                    notificationCount = notificationCount,
                    onNotificationsClick = onNotificationsClick,
                    onDashboardEventSent = { event ->
                        viewModel.setEvent(event)
                    }
                )
            }
            composable(BottomNavigationItem.Verify.route) {
                val verificationHomeViewModel: VerificationHomeViewModel = koinViewModel()
                VerificationHomeScreen(
                    navController = hostNavController,
                    viewModel = verificationHomeViewModel,
                    notificationCount = notificationCount,
                    onNotificationsClick = onNotificationsClick,
                    onDashboardEventSent = { event ->
                        viewModel.setEvent(event)
                    }
                )
            }
            composable(BottomNavigationItem.Settings.route) {
                SettingsScreen(
                    navController = hostNavController,
                    viewModel = settingsViewModel,
                    notificationCount = notificationCount,
                    onNotificationsClick = onNotificationsClick,
                )
            }
        }
        if (state.isBottomSheetOpen) {
            WrapModalBottomSheet(
                onDismissRequest = {
                    viewModel.setEvent(
                        Event.BottomSheet.UpdateBottomSheetState(
                            isOpen = false
                        )
                    )
                },
                sheetState = bottomSheetState
            ) {
                DashboardSheetContent(
                    sheetContent = state.sheetContent,
                    onEventSent = {
                        viewModel.setEvent(it)
                    }
                )
            }
        }
    }

    AnimatedVisibility(
        visible = state.isSideMenuVisible,
        modifier = Modifier.fillMaxSize(),
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = when (state.sideMenuAnimation) {
            SideMenuAnimation.SLIDE -> slideOutHorizontally(targetOffsetX = { -it })
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
        val shouldOpenActions: Boolean = hostNavController
            .currentBackStackEntry
            ?.savedStateHandle
            ?.remove<Boolean>("openActions") == true
        val pairingPayload: String? = hostNavController
            .currentBackStackEntry
            ?.savedStateHandle
            ?.remove<String>(DEVICE_LINKING_PAIRING_PAYLOAD_RESULT_KEY)
        val documentDeleted: Boolean = hostNavController
            .currentBackStackEntry
            ?.savedStateHandle
            ?.remove<Boolean>(DocumentDetailsViewModel.DOCUMENT_DELETED_RESULT_KEY) == true

        if (shouldOpenActions || pairingPayload != null) {
            bottomNavigationController.navigate(
                "${BottomNavigationItem.Wallet.route}?tab=${WalletTab.Actions.routeValue}"
            ) {
                popUpTo(bottomNavigationController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        pairingPayload?.let {
            actionsViewModel.setEvent(
                eu.europa.ec.dashboardfeature.ui.actions.Event.CompleteDeviceLinking(it)
            )
        }

        actionsViewModel.setEvent(eu.europa.ec.dashboardfeature.ui.actions.Event.OnResume)
        homeViewModel.setEvent(
            eu.europa.ec.dashboardfeature.ui.home.Event.GetCredentials
        )
        if (documentDeleted) {
            documentsViewModel.setEvent(
                eu.europa.ec.dashboardfeature.ui.documents.list.Event.GetDocuments
            )
        }
        viewModel.setEvent(
            Event.Init(
                intent = context.getPendingIntent(alsoClearIt = true),
                deepLinkUri = context.getPendingDeepLink()
            )
        )
    }

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> handleNavigationEffect(effect, hostNavController, context)

                is Effect.CloseBottomSheet -> {
                    scope.launch {
                        bottomSheetState.hide()
                    }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) {
                            viewModel.setEvent(Event.BottomSheet.UpdateBottomSheetState(isOpen = false))
                        }
                    }
                }

                is Effect.ShowBottomSheet -> {
                    viewModel.setEvent(Event.BottomSheet.UpdateBottomSheetState(isOpen = true))
                }

                is Effect.ShareLogFile -> {
                    context.openIntentChooser(
                        effect.intent,
                        effect.chooserTitle
                    )
                }

                is Effect.TriggerQuickAction -> {
                    if (bottomNavigationController.currentDestination?.route != BottomNavigationItem.Home.route) {
                        bottomNavigationController.navigate(BottomNavigationItem.Home.route) {
                            popUpTo(bottomNavigationController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    homeViewModel.setEvent(
                        eu.europa.ec.dashboardfeature.ui.home.Event.QuickActionPressed(
                            effect.actionId
                        )
                    )
                }

                is Effect.SwitchBottomTab -> {
                    if (bottomNavigationController.currentDestination?.route != effect.route) {
                        bottomNavigationController.navigate(effect.route) {
                            popUpTo(bottomNavigationController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                is Effect.ShowComingSoon -> {
                    android.widget.Toast.makeText(
                        context,
                        featureComingSoonMessage,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.collect()
    }

    SystemBroadcastReceiver(
        intentFilters = listOf(
            CoreActions.REVOCATION_WORK_MESSAGE_ACTION
        )
    ) { intent ->
        intent.getParcelableArrayListExtra<RevokedDocumentDataDomain>(
            action = CoreActions.REVOCATION_IDS_EXTRA
        )?.let {
            viewModel.setEvent(
                Event.DocumentRevocationNotificationReceived(it)
            )
        }
    }
}

private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
    context: Context,
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

        is Effect.Navigation.OpenIntentAction -> {
            handleIntentAction(
                navController,
                navigationEffect.intentAction,
                navigationEffect.arguments
            )
        }

        is Effect.Navigation.OnAppSettings -> context.openAppSettings()
        is Effect.Navigation.OnSystemSettings -> context.openBleSettings()
        is Effect.Navigation.OpenUrlExternally -> context.openUrl(uri = navigationEffect.url)
    }
}

@Composable
private fun DashboardSheetContent(
    sheetContent: DashboardBottomSheetContent,
    onEventSent: (even: Event) -> Unit,
) {
    when (sheetContent) {
        is DashboardBottomSheetContent.DocumentRevocation -> {
            BottomSheetWithOptionsList(
                textData = BottomSheetTextDataUi(
                    title = stringResource(
                        id = R.string.dashboard_bottom_sheet_revoked_document_dialog_title
                    ),
                    message = stringResource(
                        id = R.string.dashboard_bottom_sheet_revoked_document_dialog_subtitle
                    ),
                ),
                options = sheetContent.options,
                onEventSent = onEventSent,
            )
        }
    }
}
