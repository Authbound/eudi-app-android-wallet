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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.model.verification.VerificationSession
import eu.europa.ec.dashboardfeature.ui.component.NotificationIconButton
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.success
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.EmptyStateIllustration
import eu.europa.ec.uilogic.component.wrap.FeatureHighlight
import eu.europa.ec.uilogic.component.wrap.PremiumEmptyState
import eu.europa.ec.uilogic.component.wrap.PremiumTab
import eu.europa.ec.uilogic.component.wrap.PremiumTabRow
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.extension.paddingFrom
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
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
) {
    val state: VerificationHomeState by viewModel.viewState.collectAsStateWithLifecycle()
    var selectedTab: VerificationHomeTab by remember {
        mutableStateOf(VerificationHomeTab.Active)
    }
    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.NONE,
        topBar = {
            VerificationTopBar(
                notificationCount = notificationCount,
                onNotificationsClick = onNotificationsClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .paddingFrom(paddingValues, bottom = false)
        ) {
            // Premium tab row with sliding indicator
            PremiumTabRow(
                tabs = listOf(
                    PremiumTab(
                        label = stringResource(R.string.verification_home_tab_active),
                        badge = state.activeSessions.size.takeIf { it > 0 }
                    ),
                    PremiumTab(
                        label = stringResource(R.string.verification_home_tab_history)
                    )
                ),
                selectedTabIndex = selectedTab.ordinal,
                onTabSelected = { selectedTab = VerificationHomeTab.entries[it] },
                enableAnimations = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SPACING_MEDIUM.dp, vertical = SPACING_SMALL.dp)
            )

            // Content based on selected tab
            when (selectedTab) {
                VerificationHomeTab.Active -> ActiveSessionsContent(
                    sessions = state.activeSessions,
                    onCreateClick = { viewModel.setEvent(VerificationHomeEvent.CreateVerification) }
                )
                VerificationHomeTab.History -> HistorySessionsContent(
                    sessions = state.historySessions
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is VerificationHomeEffect.Navigation.SwitchScreen -> {
                    navController.navigate(effect.screenRoute) {
                        popUpTo(effect.popUpToScreenRoute) {
                            inclusive = effect.inclusive
                        }
                    }
                }
            }
        }.collect()
    }
    LaunchedEffect(Unit) {
        viewModel.setEvent(VerificationHomeEvent.Init)
    }
}

@Composable
private fun VerificationTopBar(
    notificationCount: Int,
    onNotificationsClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SPACING_SMALL.dp,
                vertical = 4.dp
            )
    ) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
            text = stringResource(R.string.verification_quick_action_title)
        )
        NotificationIconButton(
            modifier = Modifier.align(Alignment.CenterEnd),
            badgeCount = notificationCount,
            onClick = onNotificationsClick,
        )
    }
}

@Composable
private fun ActiveSessionsContent(
    sessions: List<VerificationSession>,
    onCreateClick: () -> Unit
) {
    if (sessions.isEmpty()) {
        // Premium empty state with feature highlights
        PremiumEmptyState(
            illustration = EmptyStateIllustration.VERIFICATION,
            title = stringResource(R.string.verification_home_empty_active),
            description = stringResource(R.string.verification_empty_description),
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
            enableAnimations = false
        )
    } else {
        // Sessions list with create button at top
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = SPACING_MEDIUM.dp,
                vertical = SPACING_MEDIUM.dp
            ),
            verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
        ) {
            // Create button at top
            item {
                CreateVerificationButton(onClick = onCreateClick)
            }

            // Session cards
            itemsIndexed(
                items = sessions,
                key = { _, session -> session.id }
            ) { _, session ->
                VerificationSessionCard(
                    session = session
                )
            }
        }
    }
}

@Composable
private fun HistorySessionsContent(
    sessions: List<VerificationSession>
) {
    if (sessions.isEmpty()) {
        // Premium empty state for history
        PremiumEmptyState(
            illustration = EmptyStateIllustration.HISTORY,
            title = stringResource(R.string.verification_home_empty_history),
            description = stringResource(R.string.verification_history_empty_description),
            enableAnimations = false
        )
    } else {
        // History sessions list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = SPACING_MEDIUM.dp,
                vertical = SPACING_MEDIUM.dp
            ),
            verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
        ) {
            itemsIndexed(
                items = sessions,
                key = { _, session -> session.id }
            ) { _, session ->
                VerificationSessionCard(
                    session = session,
                    isHistoryItem = true
                )
            }
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WrapIcon(
                iconData = AppIcons.Add,
                customTint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.verification_home_create_button),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun VerificationSessionCard(
    session: VerificationSession,
    isHistoryItem: Boolean = false,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }

    val statusColor = if (isHistoryItem) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.success
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true)
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column {
            // Status header strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(statusColor.copy(alpha = 0.08f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.verification_home_session_code,
                            session.sessionCode
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
                // Status pill
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isHistoryItem) {
                            stringResource(R.string.verification_status_completed)
                        } else {
                            stringResource(R.string.verification_status_active)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            // Content
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (session.description.isNotBlank()) {
                    Text(
                        text = session.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Meta info row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MetaChip(
                        icon = AppIcons.ClockTimer,
                        text = formatTimestamp(session.createdAt)
                    )
                }
                // Action buttons (only for active sessions)
                if (!isHistoryItem) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SessionActionButton(
                            modifier = Modifier.weight(1f),
                            icon = AppIcons.QrScanner,
                            text = stringResource(R.string.verification_action_show_qr),
                            onClick = { }
                        )
                        SessionActionButton(
                            modifier = Modifier.weight(1f),
                            icon = AppIcons.OpenNew,
                            text = stringResource(R.string.verification_action_copy_link),
                            onClick = { }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(
    icon: eu.europa.ec.uilogic.component.IconDataUi,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        WrapIcon(
            iconData = icon,
            customTint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SessionActionButton(
    icon: eu.europa.ec.uilogic.component.IconDataUi,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true)
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WrapIcon(
                iconData = icon,
                customTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
