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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.model.verification.VerificationSession
import eu.europa.ec.dashboardfeature.model.verification.humanizeVerificationStatus
import eu.europa.ec.dashboardfeature.model.verification.lastUpdatedAt
import eu.europa.ec.dashboardfeature.ui.component.NotificationIconButton
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.success
import eu.europa.ec.resourceslogic.theme.values.warning
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.InlineSnackbar
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.DocumentStatus
import eu.europa.ec.uilogic.component.wrap.PremiumTab
import eu.europa.ec.uilogic.component.wrap.PremiumTabRow
import eu.europa.ec.uilogic.component.wrap.QuickActionCard
import eu.europa.ec.uilogic.component.wrap.QuickActionConfig
import eu.europa.ec.uilogic.component.wrap.StatusBadge
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CreateVerificationButton(onClick = onCreateClick)
            }
            item {
                VerificationEmptyPanel(
                    title = emptyTitle,
                    description = emptyDescription
                )
            }
            item {
                VerificationSharingMethodsPanel()
            }
            item {
                Spacer(modifier = Modifier.height(112.dp))
            }
        }
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

        itemsIndexed(sessions, key = { _, session -> session.id }) { index, session ->
            VerificationSessionCard(
                session = session,
                timelineText = sessionTimelineText(session = session, tab = tab),
                animationDelay = index * 60,
                onClick = { onSessionClick(session.id) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun VerificationEmptyPanel(
    title: String,
    description: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VerificationIconTile(icon = AppIcons.Verified)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VerificationSharingMethodsPanel() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VerificationMethodRow(
                icon = AppIcons.QrScanner,
                title = stringResource(R.string.verification_feature_qr_title),
                description = stringResource(R.string.verification_feature_qr_description)
            )
            VerificationMethodRow(
                icon = AppIcons.OpenNew,
                title = stringResource(R.string.verification_feature_link_title),
                description = stringResource(R.string.verification_feature_link_description)
            )
        }
    }
}

@Composable
private fun VerificationMethodRow(
    icon: eu.europa.ec.uilogic.component.IconDataUi,
    title: String,
    description: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VerificationIconTile(icon = icon)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VerificationIconTile(icon: eu.europa.ec.uilogic.component.IconDataUi) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        WrapIcon(
            iconData = icon,
            customTint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun CreateVerificationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    QuickActionCard(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp),
        config = QuickActionConfig(
            id = "create_verification",
            title = stringResource(R.string.verification_home_create_button),
            description = stringResource(R.string.verification_home_create_description),
            icon = AppIcons.Add,
            gradientStart = MaterialTheme.colorScheme.primary,
            gradientEnd = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
            accentColor = Color.White
        ),
        onClick = onClick
    )
}

@Composable
private fun VerificationSessionCard(
    session: VerificationSession,
    timelineText: String,
    animationDelay: Int = 0,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(100),
        label = "card_scale"
    )

    var isVisible by remember { mutableStateOf(animationDelay == 0) }
    LaunchedEffect(Unit) {
        if (animationDelay > 0) {
            kotlinx.coroutines.delay(animationDelay.toLong())
            isVisible = true
        }
    }

    val successColor = MaterialTheme.colorScheme.success
    val errorColor = MaterialTheme.colorScheme.error
    val warningColor = MaterialTheme.colorScheme.warning
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = sessionAccentColor(session, successColor, errorColor, warningColor, primaryColor)
    val docStatus = sessionToDocumentStatus(session.status)

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    }
                ),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            shadowElevation = 4.dp
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Left accent bar
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(accentColor)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = humanizeVerificationStatus(session.status).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = session.purpose,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(
                            status = docStatus,
                            label = humanizeVerificationStatus(session.status)
                        )
                    }
                    if (session.requestedAttributes.isNotEmpty()) {
                        Text(
                            text = session.requestedAttributes.joinToString { it.label },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = timelineText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (session.publicUrl != null || session.qrPayload != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SessionActionChip(
                                label = stringResource(R.string.verification_feature_qr_title),
                                icon = AppIcons.QrScanner
                            )
                            SessionActionChip(
                                label = stringResource(R.string.verification_feature_link_title),
                                icon = AppIcons.OpenNew
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionActionChip(label: String, icon: eu.europa.ec.uilogic.component.IconDataUi) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WrapIcon(
                iconData = icon,
                customTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun sessionAccentColor(
    session: VerificationSession,
    success: Color,
    error: Color,
    warning: Color,
    primary: Color,
): Color {
    val expiresAt = session.expiresAt
    val isExpiringSoon = expiresAt != null &&
            expiresAt - System.currentTimeMillis() < 24 * 60 * 60 * 1000L
    return when {
        session.status.lowercase() in listOf("completed", "verified") -> success
        session.status.lowercase() in listOf("expired", "failed", "canceled", "rejected", "invalid") -> error
        isExpiringSoon -> warning
        else -> primary
    }
}

private fun sessionToDocumentStatus(status: String): DocumentStatus = when (status.lowercase()) {
    "completed", "verified" -> DocumentStatus.ISSUED
    "expired", "failed", "canceled", "rejected", "invalid" -> DocumentStatus.EXPIRED
    else -> DocumentStatus.PENDING
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
