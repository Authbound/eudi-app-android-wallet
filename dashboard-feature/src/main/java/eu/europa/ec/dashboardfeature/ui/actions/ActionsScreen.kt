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

import android.content.Context
import android.view.HapticFeedbackConstants
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionCategoryUi
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionType
import eu.europa.ec.dashboardfeature.ui.actions.model.ActionUi
import eu.europa.ec.dashboardfeature.ui.actions.model.DeviceLinkStatus
import eu.europa.ec.dashboardfeature.ui.actions.model.LinkedDeviceInfo
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.activeHighlight
import eu.europa.ec.resourceslogic.theme.values.success
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.content.ToolbarActionUi
import eu.europa.ec.uilogic.component.content.ToolbarConfig
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ActionCard
import eu.europa.ec.uilogic.component.wrap.ActionSectionHeader
import eu.europa.ec.uilogic.component.wrap.InboxActionCardConfig
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import eu.europa.ec.uilogic.component.wrap.ActionStatus as WrapActionStatus
import eu.europa.ec.uilogic.component.wrap.ActionType as WrapActionType
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionsScreen(
    navController: NavController,
    viewModel: ActionsViewModel,
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()

    // Toolbar config changes based on device link status
    val toolbarConfig = if (state.deviceLinkStatus == DeviceLinkStatus.LINKED) {
        ToolbarConfig(
            title = stringResource(R.string.actions_screen_title),
            actions = listOf(
                ToolbarActionUi(
                    icon = AppIcons.Settings,
                    onClick = { viewModel.setEvent(Event.ManageDevice) }
                )
            )
        )
    } else {
        ToolbarConfig(
            title = stringResource(R.string.actions_screen_title)
        )
    }
    ContentScreen(
        isLoading = state.isLoading || state.deviceLinkStatus == DeviceLinkStatus.CHECKING,
        navigatableAction = ScreenNavigateAction.NONE,
        toolBarConfig = toolbarConfig,
        contentErrorConfig = state.error,
        onBack = { viewModel.setEvent(Event.Pop) }
    ) { paddingValues ->
        ActionsTabContent(
            navController = navController,
            viewModel = viewModel,
            state = state,
            paddingValues = paddingValues,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionsTabContent(
    navController: NavController,
    viewModel: ActionsViewModel,
    state: State,
    paddingValues: PaddingValues,
) {
    val context: Context = LocalContext.current
    val bottomSheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

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

    ActionsContent(
        state = state,
        paddingValues = paddingValues,
        onEventSent = { viewModel.setEvent(it) }
    )

    if (state.showDeviceManagementSheet) {
        WrapModalBottomSheet(
            onDismissRequest = { viewModel.setEvent(Event.DismissDeviceManagement) },
            sheetState = bottomSheetState
        ) {
            DeviceManagementSheetContent(
                deviceInfo = state.linkedDeviceInfo,
                onUnlink = { viewModel.setEvent(Event.UnlinkDevice) }
            )
        }
    }

    if (state.showUnlinkConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.setEvent(Event.DismissUnlinkConfirmation) },
            title = { Text(stringResource(R.string.actions_device_unlink_confirm_title)) },
            text = {
                Text(
                    text = stringResource(R.string.actions_device_unlink_confirm_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setEvent(Event.ConfirmUnlinkDevice) }) {
                    Text(
                        text = stringResource(R.string.actions_device_unlink),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setEvent(Event.DismissUnlinkConfirmation) }) {
                    Text(text = stringResource(R.string.generic_cancel))
                }
            }
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
    // Route to appropriate content based on device link status
    when (state.deviceLinkStatus) {
        DeviceLinkStatus.CHECKING -> {
            // Loading state is handled by ContentScreen
        }
        DeviceLinkStatus.NOT_LINKED -> {
            DeviceNotLinkedContent(
                paddingValues = paddingValues,
                onLinkDevice = { onEventSent(Event.LinkDevice) }
            )
        }
        DeviceLinkStatus.LINKED -> {
            LinkedDeviceContent(
                state = state,
                paddingValues = paddingValues,
                onEventSent = onEventSent
            )
        }
    }
}

// ============================================================================
// UNLINKED STATE - Warm, friendly onboarding
// ============================================================================

@Composable
private fun DeviceNotLinkedContent(
    paddingValues: PaddingValues,
    onLinkDevice: () -> Unit
) {
    val view = LocalView.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = SPACING_LARGE.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        DeviceLinkIllustration()

        VSpacer.Large()

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.actions_device_not_linked_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            VSpacer.Medium()
            Text(
                text = stringResource(R.string.actions_device_not_linked_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }

        VSpacer.ExtraLarge()

        val featureIconColor = MaterialTheme.colorScheme.activeHighlight
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureHighlightCard(
                modifier = Modifier.weight(1f),
                icon = AppIcons.Verified,
                title = stringResource(R.string.actions_device_feature_verify_title),
                accentColor = featureIconColor
            )
            FeatureHighlightCard(
                modifier = Modifier.weight(1f),
                icon = AppIcons.Sign,
                title = stringResource(R.string.actions_device_feature_sign_title),
                accentColor = featureIconColor
            )
            FeatureHighlightCard(
                modifier = Modifier.weight(1f),
                icon = AppIcons.WalletSecured,
                title = stringResource(R.string.actions_device_feature_share_title),
                accentColor = featureIconColor
            )
        }

        VSpacer.ExtraLarge()

        LinkDeviceButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onLinkDevice()
            }
        )
    }
}

@Composable
private fun DeviceLinkIllustration() {
    val highlightColor = MaterialTheme.colorScheme.activeHighlight
    Box(
        modifier = Modifier
            .size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            highlightColor.copy(alpha = 0.15f),
                            highlightColor.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Inner circle with gradient
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            highlightColor.copy(alpha = 0.2f),
                            highlightColor.copy(alpha = 0.1f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Device + Link icon composition
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = AppIcons.Id,
                    customTint = highlightColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Floating plus badge - keeps primary color as it's a CTA indicator
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            WrapIcon(
                iconData = AppIcons.Add,
                customTint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FeatureHighlightCard(
    modifier: Modifier = Modifier,
    icon: IconDataUi,
    title: String,
    accentColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = icon,
                    customTint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            VSpacer.Small()
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun LinkDeviceButton(
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        WrapIcon(
            iconData = AppIcons.Add,
            customTint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.actions_device_link_button),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ============================================================================
// LINKED STATE - Events view with polished animations
// ============================================================================

@Composable
private fun LinkedDeviceContent(
    state: State,
    paddingValues: PaddingValues,
    onEventSent: (Event) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // Device status bar
        if (state.linkedDeviceInfo != null) {
            DeviceStatusBar(
                deviceName = state.linkedDeviceInfo.deviceName,
                isConnected = true
            )
        }

        if (state.showEmptyState) {
            EmptyActionsState()
        } else {
            Column {
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
                    EventsList(
                        groupedActions = state.filteredGroupedActions,
                        processingActionId = state.isProcessingAction,
                        onEventSent = onEventSent
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceStatusBar(
    deviceName: String,
    isConnected: Boolean
) {
    val successColor = MaterialTheme.colorScheme.success
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_MEDIUM.dp)
            .padding(top = SPACING_SMALL.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isConnected) {
            successColor.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnected) successColor else MaterialTheme.colorScheme.error
                    )
            )

            Text(
                text = deviceName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "·",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.actions_device_linked_status),
                style = MaterialTheme.typography.labelMedium,
                color = if (isConnected) successColor else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun EventsList(
    groupedActions: List<Pair<ActionCategoryUi, List<ActionUi>>>,
    processingActionId: String?,
    onEventSent: (Event) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = SPACING_MEDIUM.dp,
            vertical = SPACING_SMALL.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        groupedActions.forEachIndexed { categoryIndex, (category, actions) ->
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
            ) { _, action ->
                Column {
                    ActionCard(
                        config = action.toActionCardConfig(),
                        enableAnimations = false,
                        onAccept = { onEventSent(Event.AcceptAction(action.id)) },
                        onDecline = { onEventSent(Event.DeclineAction(action.id)) },
                        onClick = { onEventSent(Event.ActionItemClicked(action.id)) }
                    )
                    // Show loading indicator if this action is being processed
                    if (processingActionId == action.id) {
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

        item {
            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}

// ============================================================================
// MANAGEMENT BAR
// ============================================================================

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

// ============================================================================
// EMPTY & NO RESULTS STATES
// ============================================================================

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
private fun EmptyActionsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
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

// ============================================================================
// DEVICE MANAGEMENT BOTTOM SHEET
// ============================================================================

@Composable
private fun DeviceManagementSheetContent(
    deviceInfo: LinkedDeviceInfo?,
    onUnlink: () -> Unit
) {
    val view = LocalView.current
    val successColor = MaterialTheme.colorScheme.success
    val highlightColor = MaterialTheme.colorScheme.activeHighlight
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SPACING_LARGE.dp)
    ) {
        Text(
            text = stringResource(R.string.actions_device_manage),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        VSpacer.Large()
        if (deviceInfo != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(highlightColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        WrapIcon(
                            iconData = AppIcons.Id,
                            customTint = highlightColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = deviceInfo.deviceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = deviceInfo.deviceModel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(successColor)
                        )
                        Text(
                            text = stringResource(R.string.actions_device_linked_status),
                            style = MaterialTheme.typography.labelSmall,
                            color = successColor
                        )
                    }
                }
            }
        }
        VSpacer.ExtraLarge()
        Button(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onUnlink()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            WrapIcon(
                iconData = AppIcons.Close,
                customTint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.actions_device_unlink),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        VSpacer.Medium()
    }
}

// ============================================================================
// HELPERS
// ============================================================================

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
