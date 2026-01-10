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

package eu.europa.ec.dashboardfeature.ui.actions

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionCategoryUi
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionFilterChipUi
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionSortOrder
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionType
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionUi
import eu.europa.ec.dashboardfeature.ui.dashboard.Event as DashboardEvent
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.content.ToolbarConfig
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ActionCard
import eu.europa.ec.uilogic.component.wrap.InboxActionCardConfig
import eu.europa.ec.uilogic.component.wrap.ActionSectionHeader
import eu.europa.ec.uilogic.component.wrap.ActionStatus as WrapActionStatus
import eu.europa.ec.uilogic.component.wrap.ActionType as WrapActionType
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@Composable
internal fun ActionsScreen(
    navController: NavController,
    viewModel: ActionsViewModel,
    onDashboardEventSent: (DashboardEvent) -> Unit = {}
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_CREATE
    ) {
        viewModel.setEvent(Event.Init)
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        viewModel.setEvent(Event.OnResume)
    }

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.NONE,
        toolBarConfig = ToolbarConfig(
            title = stringResource(R.string.actions_screen_title),
        ),
        contentErrorConfig = state.error,
        onBack = { viewModel.setEvent(Event.Pop) }
    ) { paddingValues ->
        ActionsContent(
            state = state,
            paddingValues = paddingValues,
            onEventSent = { viewModel.setEvent(it) }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.Navigation.Pop -> navController.popBackStack()
                is Effect.Navigation.SwitchScreen -> {
                    navController.navigate(effect.screenRoute) {
                        popUpTo(effect.popUpToScreenRoute) {
                            inclusive = effect.inclusive
                        }
                    }
                }
                is Effect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is Effect.RefreshBadgeCount -> {
                    // Notify parent to update badge
                }
            }
        }.collect()
    }
}

@Composable
private fun ActionsContent(
    state: State,
    paddingValues: PaddingValues,
    onEventSent: (Event) -> Unit
) {
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            delay(100)
            showContent = true
        }
    }

    if (state.showEmptyState) {
        EmptyActionsState(paddingValues = paddingValues)
    } else {
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(300))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Management bar with filter, mass actions, and history
                ActionsManagementBar(
                    selectedFilter = state.selectedFilter,
                    pendingCount = state.pendingCount,
                    isProcessingBatch = state.isProcessingBatch,
                    onFilterSelected = { onEventSent(Event.OnFilterSelected(it)) },
                    onAcceptAll = { onEventSent(Event.AcceptAllPending) },
                    onDeclineAll = { onEventSent(Event.DeclineAllPending) },
                    onHistoryClick = { onEventSent(Event.ViewHistory) }
                )

                if (state.showNoResultsState) {
                    NoResultsState()
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            horizontal = SPACING_MEDIUM.dp,
                            vertical = SPACING_SMALL.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        state.filteredGroupedActions.forEachIndexed { categoryIndex, (category, actions) ->
                            item(key = "header_${category.displayName}") {
                                val headerTitle = when (category) {
                                    ActionCategoryUi.Pending -> stringResource(R.string.actions_category_pending)
                                    ActionCategoryUi.Today -> stringResource(R.string.actions_category_today)
                                    ActionCategoryUi.ThisWeek -> stringResource(R.string.actions_category_this_week)
                                    ActionCategoryUi.Earlier -> stringResource(R.string.actions_category_earlier)
                                }

                                ActionSectionHeader(
                                    title = headerTitle,
                                    count = if (category == ActionCategoryUi.Pending) actions.size else null,
                                    modifier = Modifier.padding(
                                        top = if (categoryIndex > 0) SPACING_MEDIUM.dp else 0.dp
                                    )
                                )
                            }

                            itemsIndexed(
                                items = actions,
                                key = { _, action -> action.id }
                            ) { index, action ->
                                ActionCard(
                                    config = action.toActionCardConfig(),
                                    animationDelay = index * 50,
                                    onAccept = { onEventSent(Event.AcceptAction(action.id)) },
                                    onDecline = { onEventSent(Event.DeclineAction(action.id)) },
                                    onClick = { onEventSent(Event.ActionItemClicked(action.id)) }
                                )

                                // Show loading indicator if this action is being processed
                                if (state.isProcessingAction == action.id) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Management bar with filter dropdown, mass action badges, and history button.
 */
@Composable
private fun ActionsManagementBar(
    selectedFilter: ActionType?,
    pendingCount: Int,
    isProcessingBatch: Boolean,
    onFilterSelected: (ActionType?) -> Unit,
    onAcceptAll: () -> Unit,
    onDeclineAll: () -> Unit,
    onHistoryClick: () -> Unit
) {
    var showFilterMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_MEDIUM.dp)
            .padding(vertical = SPACING_SMALL.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Filter dropdown
        Box {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showFilterMenu = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = when (selectedFilter) {
                            ActionType.SIGN_REQUEST -> stringResource(R.string.actions_filter_sign)
                            ActionType.VERIFY_REQUEST -> stringResource(R.string.actions_filter_verify)
                            ActionType.DATA_REQUEST -> stringResource(R.string.actions_filter_data)
                            null -> stringResource(R.string.actions_filter_all)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    WrapIcon(
                        iconData = AppIcons.KeyboardArrowDown,
                        customTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = showFilterMenu,
                onDismissRequest = { showFilterMenu = false }
            ) {
                FilterMenuItem(
                    label = stringResource(R.string.actions_filter_all),
                    isSelected = selectedFilter == null,
                    onClick = {
                        onFilterSelected(null)
                        showFilterMenu = false
                    }
                )
                FilterMenuItem(
                    label = stringResource(R.string.actions_filter_sign),
                    isSelected = selectedFilter == ActionType.SIGN_REQUEST,
                    onClick = {
                        onFilterSelected(ActionType.SIGN_REQUEST)
                        showFilterMenu = false
                    }
                )
                FilterMenuItem(
                    label = stringResource(R.string.actions_filter_verify),
                    isSelected = selectedFilter == ActionType.VERIFY_REQUEST,
                    onClick = {
                        onFilterSelected(ActionType.VERIFY_REQUEST)
                        showFilterMenu = false
                    }
                )
                FilterMenuItem(
                    label = stringResource(R.string.actions_filter_data),
                    isSelected = selectedFilter == ActionType.DATA_REQUEST,
                    onClick = {
                        onFilterSelected(ActionType.DATA_REQUEST)
                        showFilterMenu = false
                    }
                )
            }
        }

        // Mass action badges (only show when there are pending actions)
        if (pendingCount > 0 && !isProcessingBatch) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Accept All badge
                AssistChip(
                    onClick = onAcceptAll,
                    label = {
                        Text(
                            text = stringResource(R.string.actions_mass_accept_all),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        WrapIcon(
                            iconData = AppIcons.Check,
                            customTint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        labelColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                )

                // Decline All badge
                AssistChip(
                    onClick = onDeclineAll,
                    label = {
                        Text(
                            text = stringResource(R.string.actions_mass_decline_all),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        WrapIcon(
                            iconData = AppIcons.Close,
                            customTint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        labelColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                )
            }
        } else if (isProcessingBatch) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        // History button
        IconButton(
            onClick = onHistoryClick
        ) {
            WrapIcon(
                iconData = AppIcons.ClockTimer,
                customTint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FilterMenuItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (isSelected) {
                    WrapIcon(
                        iconData = AppIcons.Check,
                        customTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        onClick = onClick
    )
}

@Composable
private fun NoResultsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SPACING_LARGE.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        WrapIcon(
            iconData = AppIcons.Inbox,
            customTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )

        VSpacer.Medium()

        Text(
            text = stringResource(R.string.actions_screen_search_no_results),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyActionsState(paddingValues: PaddingValues) {
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        showContent = true
    }

    AnimatedVisibility(
        visible = showContent,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = SPACING_LARGE.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            WrapIcon(
                iconData = AppIcons.Inbox,
                customTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(80.dp)
            )

            VSpacer.Large()

            Text(
                text = stringResource(R.string.actions_screen_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            VSpacer.Small()

            Text(
                text = stringResource(R.string.actions_screen_empty_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Convert UI model to ActionCard config
 */
private fun ActionUi.toActionCardConfig(): InboxActionCardConfig {
    return InboxActionCardConfig(
        id = id,
        type = when (type) {
            eu.europa.ec.dashboardfeature.ui.actions.model.ActionType.SIGN_REQUEST -> WrapActionType.SIGN_REQUEST
            eu.europa.ec.dashboardfeature.ui.actions.model.ActionType.VERIFY_REQUEST -> WrapActionType.VERIFY_REQUEST
            eu.europa.ec.dashboardfeature.ui.actions.model.ActionType.DATA_REQUEST -> WrapActionType.DATA_REQUEST
        },
        title = title,
        requesterName = requesterName,
        relativeTime = relativeTime,
        description = description,
        status = when (status) {
            eu.europa.ec.dashboardfeature.ui.actions.model.ActionStatus.PENDING -> WrapActionStatus.PENDING
            eu.europa.ec.dashboardfeature.ui.actions.model.ActionStatus.ACCEPTED -> WrapActionStatus.ACCEPTED
            eu.europa.ec.dashboardfeature.ui.actions.model.ActionStatus.DECLINED -> WrapActionStatus.DECLINED
            eu.europa.ec.dashboardfeature.ui.actions.model.ActionStatus.EXPIRED -> WrapActionStatus.EXPIRED
        },
        isActionable = isActionable
    )
}
