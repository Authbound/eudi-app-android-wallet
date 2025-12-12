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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.dashboardfeature.ui.dashboard.Event
import eu.europa.ec.dashboardfeature.ui.dashboard.State
import eu.europa.ec.dashboardfeature.ui.dashboard.model.SideMenuItemUi
import eu.europa.ec.dashboardfeature.ui.dashboard.model.SideMenuTypeUi
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import eu.europa.ec.uilogic.component.SectionTitle
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.content.ToolbarActionUi
import eu.europa.ec.uilogic.component.content.ToolbarConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapListItem

@Composable
internal fun SideMenuScreen(
    state: State,
    onEventSent: (Event) -> Unit,
) {
    ContentScreen(
        navigatableAction = ScreenNavigateAction.NONE,
        toolBarConfig = ToolbarConfig(
            title = state.sideMenuTitle,
            actions = listOf(
                ToolbarActionUi(
                    icon = AppIcons.Close,
                    onClick = {
                        onEventSent(
                            Event.SideMenu.Close
                        )
                    }
                )
            )
        ),
        isLoading = false,
        onBack = {
            onEventSent(
                Event.SideMenu.Close
            )
        }
    ) { paddingValues ->
        Content(
            state = state,
            paddingValues = paddingValues,
            onEventSent = onEventSent
        )
    }
}

@Composable
private fun Content(
    state: State,
    onEventSent: (Event) -> Unit,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SideMenuOptions(
                sideMenuOptions = state.sideMenuOptions,
                onEventSent = onEventSent,
            )
        }
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
            paddingValues = PaddingValues(SPACING_MEDIUM.dp)
        )
    }
}