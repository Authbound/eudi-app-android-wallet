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

package eu.europa.ec.dashboardfeature.ui.verification

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.model.verification.VerificationSession
import eu.europa.ec.dashboardfeature.model.verification.humanizeVerificationStatus
import eu.europa.ec.dashboardfeature.model.verification.lastUpdatedAt
import eu.europa.ec.dashboardfeature.ui.component.NotificationIconButton
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.InlineSnackbar
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.EmptyStateIllustration
import eu.europa.ec.uilogic.component.wrap.FeatureHighlight
import eu.europa.ec.uilogic.component.wrap.PremiumEmptyState
import eu.europa.ec.uilogic.component.wrap.PremiumTab
import eu.europa.ec.uilogic.component.wrap.PremiumTabRow
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
import eu.europa.ec.uilogic.extension.paddingFrom
import kotlinx.coroutines.flow.collectLatest
import eu.europa.ec.dashboardfeature.ui.dashboard.Event as DashboardEvent
import eu.europa.ec.dashboardfeature.ui.dashboard.Event.SideMenu.Open as OpenSideMenuEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VerificationHomeTab {
    Active,
    History,
}

private fun formatTimestamp(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

@Composable
fun VerificationHomeScreen(
    navController: NavController,
    viewModel: VerificationHomeViewModel,
    notificationCount: Int,
    onNotificationsClick: () -> Unit,
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    val state: VerificationHomeState by viewModel.viewState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(VerificationHomeTab.Active) }

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.NONE,
        topBar = {
            VerificationTopBar(
                notificationCount = notificationCount,
                onNotificationsClick = onNotificationsClick,
                onDashboardEventSent = onDashboardEventSent,
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .paddingFrom(paddingValues, bottom = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
            ) {
                PremiumTabRow(
                    tabs = listOf(
                        PremiumTab(
                            label = stringResource(R.string.verification_home_tab_active),
                            badge = state.activeSessions.size.takeIf { it > 0 }
                        ),
                        PremiumTab(
                            label = stringResource(R.string.verification_home_tab_history),
                            badge = state.historySessions.size.takeIf { it > 0 }
                        )
                    ),
                    selectedTabIndex = selectedTab.ordinal,
                    onTabSelected = { selectedTab = VerificationHomeTab.entries[it] },
                    enableAnimations = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SPACING_MEDIUM.dp, vertical = SPACING_SMALL.dp)
                )

                when (selectedTab) {
                    VerificationHomeTab.Active -> VerificationSessionsContent(
                        tab = VerificationHomeTab.Active,
                        sessions = state.activeSessions,
                        emptyTitle = stringResource(R.string.verification_home_empty_active),
                        emptyDescription = stringResource(R.string.verification_empty_description),
                        onCreateClick = { viewModel.setEvent(VerificationHomeEvent.CreateVerification) },
                        onSessionClick = { sessionId ->
                            viewModel.setEvent(VerificationHomeEvent.OpenSession(sessionId))
                        }
                    )
                    VerificationHomeTab.History -> VerificationSessionsContent(
                        tab = VerificationHomeTab.History,
                        sessions = state.historySessions,
                        emptyTitle = stringResource(R.string.verification_home_empty_history),
                        emptyDescription = stringResource(R.string.verification_history_empty_description),
                        onCreateClick = { viewModel.setEvent(VerificationHomeEvent.CreateVerification) },
                        onSessionClick = { sessionId ->
                            viewModel.setEvent(VerificationHomeEvent.OpenSession(sessionId))
                        }
                    )
                }
            }

            state.error?.let { error ->
                InlineSnackbar(
                    message = error,
                    onRetry = { viewModel.setEvent(VerificationHomeEvent.Refresh) },
                    onDismiss = { viewModel.setEvent(VerificationHomeEvent.DismissError) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = SPACING_EXTRA_SMALL.dp)
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is VerificationHomeEffect.Navigation.SwitchScreen -> {
                    navController.navigate(effect.screenRoute) {
                        popUpTo(effect.popUpToScreenRoute) {
                            inclusive = effect.inclusive
                        }
                    }
                }
            }
        }
    }

    OneTimeLaunchedEffect {
        viewModel.setEvent(VerificationHomeEvent.Init)
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        viewModel.setEvent(VerificationHomeEvent.Refresh)
    }
}

@Composable
private fun VerificationTopBar(
    notificationCount: Int,
    onNotificationsClick: () -> Unit,
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_SMALL.dp, vertical = 4.dp)
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
            text = stringResource(R.string.verification_quick_action_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        NotificationIconButton(
            modifier = Modifier.align(Alignment.CenterEnd),
            badgeCount = notificationCount,
            onClick = onNotificationsClick,
        )
    }
}

@Composable
private fun VerificationSessionsContent(
    tab: VerificationHomeTab,
    sessions: List<VerificationSession>,
    emptyTitle: String,
    emptyDescription: String,
    onCreateClick: () -> Unit,
    onSessionClick: (String) -> Unit,
) {
    if (sessions.isEmpty()) {
        PremiumEmptyState(
            illustration = EmptyStateIllustration.VERIFICATION,
            title = emptyTitle,
            description = emptyDescription,
            features = listOf(
                FeatureHighlight(
                    icon = AppIcons.QrScanner,
                    title = stringResource(R.string.verification_feature_qr_title),
                    description = stringResource(R.string.verification_feature_qr_description)
                ),
                FeatureHighlight(
                    icon = AppIcons.OpenNew,
                    title = stringResource(R.string.verification_feature_link_title),
                    description = stringResource(R.string.verification_feature_link_description)
                )
            ),
            actionLabel = stringResource(R.string.verification_home_create_button),
            onActionClick = onCreateClick,
            enableAnimations = false,
            useActionsStyle = true
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CreateVerificationButton(onClick = onCreateClick)
        }

        items(sessions, key = { it.id }) { session ->
            VerificationSessionCard(
                session = session,
                timelineText = sessionTimelineText(session = session, tab = tab),
                onClick = { onSessionClick(session.id) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun CreateVerificationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true)
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WrapIcon(
                iconData = AppIcons.Add,
                customTint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = stringResource(R.string.verification_home_create_button),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun VerificationSessionCard(
    session: VerificationSession,
    timelineText: String,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true)
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = session.purpose,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = humanizeVerificationStatus(session.status),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = session.requestedAttributes.joinToString { it.label },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = timelineText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun sessionTimelineText(
    session: VerificationSession,
    tab: VerificationHomeTab,
): String = when (tab) {
    VerificationHomeTab.Active -> {
        val expiresAt = session.expiresAt
        if (expiresAt != null) {
            stringResource(
                R.string.verification_home_timeline_expires,
                formatTimestamp(expiresAt)
            )
        } else {
            stringResource(
                R.string.verification_home_timeline_created,
                formatTimestamp(session.createdAt)
            )
        }
    }

    VerificationHomeTab.History -> stringResource(
        R.string.verification_home_timeline_updated,
        formatTimestamp(session.lastUpdatedAt())
    )
}
