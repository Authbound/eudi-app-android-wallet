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

package eu.europa.ec.dashboardfeature.ui.wallet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.ui.actions.ActionsTabContent
import eu.europa.ec.dashboardfeature.ui.actions.ActionsViewModel
import eu.europa.ec.dashboardfeature.ui.actions.State as ActionsState
import eu.europa.ec.dashboardfeature.ui.actions.model.DeviceLinkStatus
import eu.europa.ec.dashboardfeature.ui.component.NotificationIconButton
import eu.europa.ec.dashboardfeature.ui.documents.list.DocumentsTabContent
import eu.europa.ec.dashboardfeature.ui.documents.list.DocumentsViewModel
import eu.europa.ec.dashboardfeature.ui.documents.list.State as DocumentsState
import eu.europa.ec.dashboardfeature.ui.health.HealthTabContent
import eu.europa.ec.dashboardfeature.ui.health.HealthViewModel
import eu.europa.ec.dashboardfeature.ui.health.State as HealthState
import eu.europa.ec.dashboardfeature.ui.dashboard.Event as DashboardEvent
import eu.europa.ec.dashboardfeature.ui.dashboard.Event.SideMenu.Open as OpenSideMenuEvent
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.PremiumTab
import eu.europa.ec.uilogic.component.wrap.PremiumTabRow
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
import eu.europa.ec.uilogic.extension.paddingFrom

enum class WalletTab(val routeValue: String, val titleRes: Int) {
    Documents(routeValue = "documents", titleRes = R.string.documents_screen_title),
    Actions(routeValue = "actions", titleRes = R.string.actions_screen_title),
    Health(routeValue = "health", titleRes = R.string.health_screen_title),
    ;

    companion object {
        fun fromRouteValue(value: String?): WalletTab {
            return entries.firstOrNull { it.routeValue == value } ?: Documents
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    navController: NavController,
    documentsViewModel: DocumentsViewModel,
    actionsViewModel: ActionsViewModel,
    healthViewModel: HealthViewModel,
    selectedTab: WalletTab,
    notificationCount: Int,
    onNotificationsClick: () -> Unit,
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    val documentsState: DocumentsState by documentsViewModel.viewState.collectAsStateWithLifecycle()
    val actionsState: ActionsState by actionsViewModel.viewState.collectAsStateWithLifecycle()
    val healthState: HealthState by healthViewModel.viewState.collectAsStateWithLifecycle()
    var currentTab: WalletTab by remember(selectedTab) { mutableStateOf(selectedTab) }
    val isLoading: Boolean = when (currentTab) {
        WalletTab.Documents -> documentsState.isLoading
        WalletTab.Actions -> actionsState.isLoading || actionsState.deviceLinkStatus == DeviceLinkStatus.CHECKING
        WalletTab.Health -> healthState.isLoading
    }
    val contentErrorConfig: ContentErrorConfig? = when (currentTab) {
        WalletTab.Documents -> documentsState.error
        WalletTab.Actions -> actionsState.error
        WalletTab.Health -> healthState.error
    }

    ContentScreen(
        isLoading = isLoading,
        contentErrorConfig = contentErrorConfig,
        navigatableAction = ScreenNavigateAction.NONE,
        topBar = {
            WalletTopBar(
                notificationCount = notificationCount,
                onNotificationsClick = onNotificationsClick,
                onDashboardEventSent = onDashboardEventSent,
            )
        }
    ) { paddingValues ->
        val layoutDirection: LayoutDirection = LocalLayoutDirection.current
        val contentPadding: PaddingValues = PaddingValues(
            start = paddingValues.calculateStartPadding(layoutDirection),
            end = paddingValues.calculateEndPadding(layoutDirection),
            bottom = paddingValues.calculateBottomPadding(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .paddingFrom(
                    pv = paddingValues,
                    start = false,
                    end = false,
                    bottom = false,
                )
        ) {
            // Premium tab row with sliding indicator
            PremiumTabRow(
                tabs = listOf(
                    PremiumTab(
                        label = stringResource(WalletTab.Documents.titleRes),
                        icon = AppIcons.IdCards
                    ),
                    PremiumTab(
                        label = stringResource(WalletTab.Actions.titleRes),
                        icon = AppIcons.Inbox,
                        badge = actionsState.pendingCount.takeIf { it > 0 }
                    ),
                    PremiumTab(
                        label = stringResource(WalletTab.Health.titleRes),
                        icon = AppIcons.Health
                    )
                ),
                selectedTabIndex = currentTab.ordinal,
                onTabSelected = { currentTab = WalletTab.entries[it] },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SPACING_MEDIUM.dp, vertical = SPACING_SMALL.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = SPACING_SMALL.dp)
            ) {
                when (currentTab) {
                    WalletTab.Documents -> DocumentsTabContent(
                        navHostController = navController,
                        viewModel = documentsViewModel,
                        paddingValues = contentPadding,
                    )
                    WalletTab.Actions -> ActionsTabContent(
                        navController = navController,
                        viewModel = actionsViewModel,
                        state = actionsState,
                        paddingValues = contentPadding,
                    )
                    WalletTab.Health -> HealthTabContent(
                        navController = navController,
                        viewModel = healthViewModel,
                        paddingValues = contentPadding,
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletTopBar(
    notificationCount: Int,
    onNotificationsClick: () -> Unit,
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SPACING_SMALL.dp,
                vertical = 4.dp
            )
    ) {
        WrapIconButton(
            modifier = Modifier.align(Alignment.CenterStart),
            iconData = AppIcons.Menu,
            customTint = MaterialTheme.colorScheme.onSurface,
        ) {
            onDashboardEventSent(OpenSideMenuEvent)
        }
        Text(
            modifier = Modifier.align(Alignment.Center),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
            text = stringResource(R.string.wallet_screen_title)
        )
        NotificationIconButton(
            modifier = Modifier.align(Alignment.CenterEnd),
            badgeCount = notificationCount,
            onClick = onNotificationsClick,
        )
    }
}
