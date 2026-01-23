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

package eu.europa.ec.dashboardfeature.ui.component

import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.wrap.WrapIconButton

@Composable
fun NotificationIconButton(
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val badgeText: String? = when {
        badgeCount > 99 -> "99+"
        badgeCount > 0 -> badgeCount.toString()
        else -> null
    }
    BadgedBox(
        modifier = modifier,
        badge = {
            badgeText?.let { safeBadgeText ->
                Badge(
                    modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
                ) {
                    Text(
                        text = safeBadgeText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    ) {
        WrapIconButton(
            iconData = AppIcons.Notifications,
            customTint = MaterialTheme.colorScheme.onSurface,
            onClick = onClick,
        )
    }
}
