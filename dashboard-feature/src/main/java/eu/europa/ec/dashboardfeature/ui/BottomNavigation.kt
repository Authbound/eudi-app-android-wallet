/*
 * Copyright (c) 2023 European Commission
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

package eu.europa.ec.dashboardfeature.ui

import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.wrap.WrapIcon

enum class BottomNavigationItem(
    val route: String,
    val icon: IconData,
    val labelResId: Int
) {
    Home(
        route = "HOME",
        icon = AppIcons.Home,
        labelResId = R.string.home_screen_title
    ),
    Documents(
        route = "DOCUMENTS",
        icon = AppIcons.Documents,
        labelResId = R.string.documents_screen_title
    ),
    Transactions(
        route = "TRANSACTIONS",
        icon = AppIcons.Transactions,
        labelResId = R.string.transactions_screen_title
    ),
    Settings(
        route = "SETTINGS",
        icon = AppIcons.UserIcon,
        labelResId = R.string.settings_screen_title
    ),
    AddCredential(
        route = "ADD_CREDENTIAL",
        icon = AppIcons.Add,
        labelResId = R.string.dashboard_quick_action_add_credential
    )
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val navItems = listOf(
        BottomNavigationItem.Home,
        BottomNavigationItem.Documents,
        BottomNavigationItem.Transactions,
        BottomNavigationItem.Settings
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Create a floating navigation bar
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // The floating navigation bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            shape = RoundedCornerShape(35.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f),
            shadowElevation = 8.dp,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == screen.route
                    } == true
                    
                    FloatingNavItem(
                        icon = screen.icon,
                        label = stringResource(screen.labelResId),
                        selected = selected,
                        onItemClick = {
//                            if (currentDestination?.route != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
//                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingNavItem(
    icon: IconData,
    label: String,
    selected: Boolean,
    onItemClick: () -> Unit
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    
    // Animation for icon scaling
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.2f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "scale"
    )
    
    // Trigger haptic feedback when selected
    LaunchedEffect(selected) {
        if (selected) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onItemClick()
            }
            .padding(8.dp)
    ) {
        // Icon with background highlight when selected
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.scale(scale)
            ) {
                WrapIcon(
                    iconData = icon,
                    enabled = true,
                    customTint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }
        
        // Indicator dot for selected item
        if (selected) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        } else {
            // Empty space to maintain layout
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(4.dp)
                    .alpha(0f)
            )
        }
    }
}

@ThemeModePreviews
@Composable
private fun BottomNavigationBarPreview() {
    PreviewTheme {
        BottomNavigationBar(rememberNavController())
    }
}