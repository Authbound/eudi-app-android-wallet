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

package eu.europa.ec.dashboardfeature.interactor

import eu.europa.ec.dashboardfeature.ui.dashboard.model.SideMenuItemUi
import eu.europa.ec.dashboardfeature.ui.dashboard.model.SideMenuTypeUi
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi

interface DashboardInteractor {
    fun getSideMenuOptions(): List<SideMenuItemUi>
}

class DashboardInteractorImpl(
    private val resourceProvider: ResourceProvider,
) : DashboardInteractor {

    override fun getSideMenuOptions(): List<SideMenuItemUi> {
        return buildList {
            add(SideMenuItemUi.Header(resourceProvider.getString(R.string.home_screen_quick_actions)))

            add(
                SideMenuItemUi.ActionItem(
                    type = SideMenuTypeUi.AUTHENTICATE,
                    data = ListItemDataUi(
                        itemId = "authenticate",
                        mainContentData = ListItemMainContentDataUi.Text(
                            text = resourceProvider.getString(R.string.home_screen_authenticate)
                        ),
                        leadingContentData = ListItemLeadingContentDataUi.Icon(
                            iconData = AppIcons.TouchId
                        ),
                        trailingContentData = ListItemTrailingContentDataUi.Icon(
                            iconData = AppIcons.KeyboardArrowRight
                        )
                    )
                )
            )
            add(
                SideMenuItemUi.ActionItem(
                    type = SideMenuTypeUi.ADD_DOCUMENT,
                    data = ListItemDataUi(
                        itemId = "add_document",
                        mainContentData = ListItemMainContentDataUi.Text(
                            text = resourceProvider.getString(R.string.dashboard_quick_action_add_credential)
                        ),
                        leadingContentData = ListItemLeadingContentDataUi.Icon(
                            iconData = AppIcons.Id
                        ),
                        trailingContentData = ListItemTrailingContentDataUi.Icon(
                            iconData = AppIcons.KeyboardArrowRight
                        )
                    )
                )
            )
            add(
                SideMenuItemUi.ActionItem(
                    type = SideMenuTypeUi.VERIFY,
                    data = ListItemDataUi(
                        itemId = "verify",
                        mainContentData = ListItemMainContentDataUi.Text(
                            text = resourceProvider.getString(R.string.verification_quick_action_title)
                        ),
                        leadingContentData = ListItemLeadingContentDataUi.Icon(
                            iconData = AppIcons.Verified
                        ),
                        trailingContentData = ListItemTrailingContentDataUi.Icon(
                            iconData = AppIcons.KeyboardArrowRight
                        )
                    )
                )
            )
            add(
                SideMenuItemUi.ActionItem(
                    type = SideMenuTypeUi.SIGN_DOCUMENT,
                    data = ListItemDataUi(
                        itemId = "sign_document",
                        mainContentData = ListItemMainContentDataUi.Text(
                            text = resourceProvider.getString(R.string.home_screen_sign)
                        ),
                        leadingContentData = ListItemLeadingContentDataUi.Icon(
                            iconData = AppIcons.Sign
                        ),
                        trailingContentData = ListItemTrailingContentDataUi.TextWithIcon(
                            text = resourceProvider.getString(R.string.coming_soon_badge),
                            iconData = AppIcons.KeyboardArrowRight
                        )
                    )
                )
            )

            add(SideMenuItemUi.Header(resourceProvider.getString(R.string.dashboard_side_menu_option_profile)))

            add(
                SideMenuItemUi.ActionItem(
                    type = SideMenuTypeUi.PROFILE,
                    data = ListItemDataUi(
                        itemId = resourceProvider.getString(R.string.dashboard_side_menu_option_profile_id),
                        mainContentData = ListItemMainContentDataUi.Text(
                            text = resourceProvider.getString(R.string.dashboard_side_menu_option_profile)
                        ),
                        leadingContentData = ListItemLeadingContentDataUi.Icon(
                            iconData = AppIcons.UserIcon
                        ),
                        trailingContentData = ListItemTrailingContentDataUi.Icon(
                            iconData = AppIcons.KeyboardArrowRight
                        )
                    )
                )
            )
        }
    }
}