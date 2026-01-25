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

package eu.europa.ec.dashboardfeature.ui.dashboard.sidemenu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.dashboardfeature.ui.dashboard.Event
import eu.europa.ec.dashboardfeature.ui.dashboard.State
import eu.europa.ec.dashboardfeature.ui.dashboard.model.SideMenuItemUi
import eu.europa.ec.dashboardfeature.ui.dashboard.model.SideMenuTypeUi
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import eu.europa.ec.uilogic.component.SectionTitle
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.MenuFooter
import eu.europa.ec.uilogic.component.wrap.MenuItemCard
import eu.europa.ec.uilogic.component.wrap.MenuSectionHeader
import eu.europa.ec.uilogic.component.wrap.ProfileHeader
import eu.europa.ec.uilogic.component.wrap.ProfileHeaderConfig
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapListItem
import kotlinx.coroutines.delay

@Composable
internal fun SideMenuScreen(
    state: State,
    onEventSent: (Event) -> Unit,
) {
    Content(
        state = state,
        onEventSent = onEventSent,
        onClose = { onEventSent(Event.SideMenu.Close) }
    )
}

@Composable
private fun Content(
    state: State,
    onEventSent: (Event) -> Unit,
    onClose: () -> Unit = {}
) {
    // Staggered animation states
    var showProfile by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var showFooter by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showProfile = true
        delay(100)
        showActions = true
        delay(100)
        showAccount = true
        delay(100)
        showFooter = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        // Profile Header with floating close button
        Box(modifier = Modifier.fillMaxWidth()) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showProfile,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { -it / 4 }
            ) {
                ProfileHeader(
                    config = ProfileHeaderConfig(
                        displayName = state.userProfile?.displayName ?: "User",
                        email = state.userProfile?.email,
                        avatarUrl = state.userProfile?.avatarUrl,
                        onEditClick = {
                            onEventSent(Event.SideMenu.ItemClicked(SideMenuTypeUi.PROFILE))
                        }
                    ),
                    onProfileClick = {
                        onEventSent(Event.SideMenu.ItemClicked(SideMenuTypeUi.PROFILE))
                    }
                )
            }

            // Floating close button in top-right corner - elevated above profile
            Surface(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(SPACING_MEDIUM.dp)
                    .zIndex(10f)
                    .size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val icon = AppIcons.Close
                    val imageVector = icon.imageVector
                    val resourceId = icon.resourceId
                    
                    if (imageVector != null) {
                        androidx.compose.material3.Icon(
                            imageVector = imageVector,
                            contentDescription = stringResource(icon.contentDescriptionId),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    } else if (resourceId != null) {
                        androidx.compose.material3.Icon(
                            painter = androidx.compose.ui.res.painterResource(resourceId),
                            contentDescription = stringResource(icon.contentDescriptionId),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Scrollable content area
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = SPACING_LARGE.dp)
        ) {
            // Group menu options by section
            val quickActions = state.sideMenuOptions.filterQuickActions()
            val accountOptions = state.sideMenuOptions.filterAccountOptions()

            // Quick Actions Section
            if (quickActions.isNotEmpty()) {
                AnimatedVisibility(
                    visible = showActions,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }
                ) {
                    MenuSection(
                        title = stringResource(R.string.dashboard_side_menu_quick_actions_header),
                        items = quickActions,
                        onEventSent = onEventSent
                    )
                }
            }

            VSpacer.Large()

            // Account Section
            if (accountOptions.isNotEmpty()) {
                AnimatedVisibility(
                    visible = showAccount,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }
                ) {
                    MenuSection(
                        title = stringResource(R.string.dashboard_side_menu_account_header),
                        items = accountOptions,
                        onEventSent = onEventSent
                    )
                }
            }
        }

        // Footer
        AnimatedVisibility(
            visible = showFooter,
            enter = fadeIn(tween(300))
        ) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = SPACING_MEDIUM.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            MenuFooter(
                appVersion = "1.0.0", // TODO: Get from BuildConfig
                copyrightText = stringResource(R.string.app_copyright)
            )
        }
    }
}

/**
 * A section of menu items with a header and grouped cards.
 */
@Composable
private fun MenuSection(
    title: String,
    items: List<SideMenuItemUi.ActionItem>,
    onEventSent: (Event) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MenuSectionHeader(title = title)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SPACING_MEDIUM.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    val title = when (val content = item.data.mainContentData) {
                        is ListItemMainContentDataUi.Text -> content.text
                        is ListItemMainContentDataUi.Image -> ""
                    }

                    val icon = when (val leading = item.data.leadingContentData) {
                        is ListItemLeadingContentDataUi.Icon -> leading.iconData
                        else -> AppIcons.IdCards
                    }

                    MenuItemCard(
                        title = title,
                        icon = icon,
                        onClick = {
                            onEventSent(Event.SideMenu.ItemClicked(itemType = item.type))
                        },
                        isEnabled = item.isEnabled,
                        badge = if (!item.isEnabled) "Coming" else null,
                        showDivider = index < items.lastIndex
                    )
                }
            }
        }
    }
}

/**
 * Extension function to filter quick action items from side menu.
 */
private fun List<SideMenuItemUi>.filterQuickActions(): List<SideMenuItemUi.ActionItem> {
    return filterIsInstance<SideMenuItemUi.ActionItem>().filter { item ->
        item.type in listOf(
            SideMenuTypeUi.AUTHENTICATE,
            SideMenuTypeUi.ADD_DOCUMENT,
            SideMenuTypeUi.VERIFY,
            SideMenuTypeUi.SIGN
        )
    }
}

/**
 * Extension function to filter account-related items from side menu.
 */
private fun List<SideMenuItemUi>.filterAccountOptions(): List<SideMenuItemUi.ActionItem> {
    return filterIsInstance<SideMenuItemUi.ActionItem>().filter { item ->
        item.type in listOf(
            SideMenuTypeUi.PROFILE,
            SideMenuTypeUi.CHANGE_PIN,
            SideMenuTypeUi.SETTINGS,
            SideMenuTypeUi.EU_GUIDE,
            SideMenuTypeUi.SHARE_LOGS
        )
    }
}

@Composable
private fun SideMenuOptions(
    sideMenuOptions: List<SideMenuItemUi>,
    onEventSent: (Event) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(sideMenuOptions) { index, item ->
            when (item) {
                is SideMenuItemUi.Header -> {
                    SectionTitle(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SPACING_SMALL.dp, vertical = SPACING_SMALL.dp),
                        text = item.title,
                        textConfig = TextConfig(
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    )
                }

                is SideMenuItemUi.ActionItem -> {
                    WrapListItem(
                        modifier = Modifier.fillMaxWidth(),
                        mainContentVerticalPadding = SPACING_MEDIUM.dp,
                        item = item.data,
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        onItemClick = {
                            onEventSent(
                                Event.SideMenu.ItemClicked(itemType = item.type)
                            )
                        }
                    )

                    if (index != sideMenuOptions.lastIndex && sideMenuOptions[index + 1] is SideMenuItemUi.ActionItem) {
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = SPACING_SMALL.dp)
                        )
                    }
                }
            }
        }
    }
}

@ThemeModePreviews
@Composable
private fun SideMenuContentPreview() {
    PreviewTheme {
        Content(
            state = State(
                isSideMenuVisible = true,
                sideMenuTitle = "",
                sideMenuOptions = listOf(
                    SideMenuItemUi.Header("Actions"),
                    SideMenuItemUi.ActionItem(
                        type = SideMenuTypeUi.PROFILE,
                        data = ListItemDataUi(
                            itemId = stringResource(R.string.dashboard_side_menu_option_profile_id),
                            mainContentData = ListItemMainContentDataUi.Text(
                                text = stringResource(R.string.dashboard_side_menu_option_profile)
                            ),
                            leadingContentData = ListItemLeadingContentDataUi.Icon(
                                iconData = AppIcons.UserIcon
                            ),
                            trailingContentData = ListItemTrailingContentDataUi.Icon(
                                iconData = AppIcons.KeyboardArrowRight
                            )
                        )
                    ),
                ),
            ),
            onEventSent = {},
            onClose = {}
        )
    }
}