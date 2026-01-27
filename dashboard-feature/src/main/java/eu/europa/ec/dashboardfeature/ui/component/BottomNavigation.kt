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

import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
qqqimport androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import eu.europa.ec.dashboardfeature.util.TestTag
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.activeHighlight
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.wrap.WrapIcon

sealed class BottomNavigationItem(
    val route: String,
    @param:StringRes val titleRes: Int,
    val icon: IconDataUi,
    val iconSize: Dp = 24.dp,
) {
    data object Home : BottomNavigationItem(
        route = "HOME",
        titleRes = R.string.home_screen_title,
        icon = AppIcons.Home
    )

    data object Wallet : BottomNavigationItem(
        route = "WALLET",
        titleRes = R.string.wallet_screen_title,
        icon = AppIcons.WalletOutline,
    )

    data object Verify : BottomNavigationItem(
        route = "VERIFY",
        titleRes = R.string.verification_quick_action_title,
        icon = AppIcons.Verified
    )

    data object Settings: BottomNavigationItem(
        route = "SETTINGS",
        titleRes = R.string.content_description_user_icon,
        icon = AppIcons.UserIcon,
    )
}

@Composable
fun BottomNavigationBar(
    navController: NavController,
    onQrScanClick: () -> Unit = {}
) {
    val navItems = listOf(
        BottomNavigationItem.Home,
        BottomNavigationItem.Wallet,
        BottomNavigationItem.Verify,
        BottomNavigationItem.Settings
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val view = LocalView.current

    // Create a floating navigation bar with center QR FAB
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 22.dp)
            .padding(horizontal = 25.dp),
        contentAlignment = Alignment.Center
    ) {
        // Blur background layer for glass effect
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .blur(20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(36.dp)
                )
        )

        // The floating navigation bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.90f),
            shadowElevation = 12.dp,
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side nav items (Home, Wallet) - takes equal weight
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navItems.take(2).forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route?.substringBefore("?") == screen.route
                        } == true

                        FloatingNavItem(
                            modifier = Modifier.testTag(
                                TestTag.DashboardScreen.bottomNavigationItem(
                                    navItem = screen.route.lowercase()
                                )
                            ),
                            icon = screen.icon,
                            label = stringResource(screen.titleRes),
                            selected = selected,
                            iconSize = screen.iconSize,
                            onItemClick = {
                                if (!selected) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }

                // Center spacer for FAB - fixed width
                Spacer(modifier = Modifier.width(64.dp))

                // Right side nav items (Verify, Settings) - takes equal weight
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navItems.drop(2).forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route?.substringBefore("?") == screen.route
                        } == true

                        FloatingNavItem(
                            modifier = Modifier.testTag(
                                TestTag.DashboardScreen.bottomNavigationItem(
                                    navItem = screen.route.lowercase()
                                )
                            ),
                            icon = screen.icon,
                            label = stringResource(screen.titleRes),
                            selected = selected,
                            iconSize = screen.iconSize,
                            onItemClick = {
                                if (!selected) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Center QR Scan FAB with glow effect - overlaps the nav bar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-20).dp),
            contentAlignment = Alignment.Center
        ) {
            // Glow effect behind FAB
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            radius = 56f
                        ),
                        shape = CircleShape
                    )
            )

            // FAB
            FloatingActionButton(
                modifier = Modifier.size(56.dp),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onQrScanClick()
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 12.dp
                )
            ) {
                WrapIcon(
                    iconData = AppIcons.QrScanner,
                    customTint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun FloatingNavItem(
    modifier: Modifier = Modifier,
    icon: IconDataUi,
    label: String,
    selected: Boolean,
    iconSize: Dp = 24.dp,
    onItemClick: () -> Unit
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }

    // Smooth scale animation with spring
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    // Animated background alpha
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (selected) 0.12f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "backgroundAlpha"
    )

    // Animated icon tint - uses activeHighlight for proper dark theme contrast
    val iconTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.activeHighlight else MaterialTheme.colorScheme.outline,
        animationSpec = tween(durationMillis = 200),
        label = "iconTint"
    )

    // Trigger haptic feedback when selected
    LaunchedEffect(selected) {
        if (selected) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onItemClick()
            }
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        // Icon container with fixed size - requiredSize ensures it won't be constrained
        Box(
            modifier = Modifier
                .requiredSize(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.activeHighlight.copy(alpha = backgroundAlpha),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Scaled icon
            WrapIcon(
                iconData = icon,
                enabled = true,
                customTint = iconTint,
                modifier = Modifier
                    .size(iconSize)
                    .scale(scale)
            )
        }

        // Premium pill-shaped indicator with glow
        Box(
            modifier = Modifier.padding(top = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            // Glow effect behind indicator (only when selected)
            if (selected) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(8.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.activeHighlight.copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                radius = 32f
                            ),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }

            // Indicator pill
            Box(
                modifier = Modifier
                    .width(if (selected) 16.dp else 4.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (selected) {
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.activeHighlight,
                                    MaterialTheme.colorScheme.activeHighlight.copy(alpha = 0.7f)
                                )
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.Transparent)
                            )
                        }
                    )
                    .alpha(if (selected) 1f else 0f)
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