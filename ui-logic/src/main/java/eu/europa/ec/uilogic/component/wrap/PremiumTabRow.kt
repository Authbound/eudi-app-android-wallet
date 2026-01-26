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

package eu.europa.ec.uilogic.component.wrap

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews

/**
 * Configuration for a premium tab item.
 */
data class PremiumTab(
    val label: String,
    val icon: IconDataUi? = null,
    val badge: Int? = null
)

/**
 * A premium custom tab component with a sliding pill indicator.
 * Inspired by iOS Segmented Control and Stripe's tab design.
 *
 * Features:
 * - Pill-shaped sliding indicator with spring animation
 * - Optional badge count support
 * - Haptic feedback on selection
 * - Press scale animation
 * - Smooth color transitions
 */
@Composable
fun PremiumTabRow(
    tabs: List<PremiumTab>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    indicatorColor: Color = MaterialTheme.colorScheme.surface,
    selectedTextColor: Color = MaterialTheme.colorScheme.onSurface,
    unselectedTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enableAnimations: Boolean = true
) {
    val density = LocalDensity.current
    val view = LocalView.current

    // Track tab positions and widths for indicator animation
    val tabWidths = remember { mutableStateListOf<Int>() }
    val tabOffsets = remember { mutableStateListOf<Int>() }
    var containerWidth by remember { mutableIntStateOf(0) }

    // Initialize lists if needed
    if (tabWidths.size != tabs.size) {
        tabWidths.clear()
        tabOffsets.clear()
        repeat(tabs.size) {
            tabWidths.add(0)
            tabOffsets.add(0)
        }
    }

    // Calculate indicator position and width
    val indicatorOffset: Dp = if (enableAnimations) {
        val animatedOffset by animateDpAsState(
            targetValue = with(density) {
                if (selectedTabIndex < tabOffsets.size && tabOffsets[selectedTabIndex] > 0) {
                    tabOffsets[selectedTabIndex].toDp()
                } else {
                    0.dp
                }
            },
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = 400f
            ),
            label = "indicatorOffset"
        )
        animatedOffset
    } else {
        with(density) {
            if (selectedTabIndex < tabOffsets.size && tabOffsets[selectedTabIndex] > 0) {
                tabOffsets[selectedTabIndex].toDp()
            } else {
                0.dp
            }
        }
    }
    val indicatorWidth: Dp = if (enableAnimations) {
        val animatedWidth by animateDpAsState(
            targetValue = with(density) {
                if (selectedTabIndex < tabWidths.size && tabWidths[selectedTabIndex] > 0) {
                    tabWidths[selectedTabIndex].toDp()
                } else {
                    0.dp
                }
            },
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = 400f
            ),
            label = "indicatorWidth"
        )
        animatedWidth
    } else {
        with(density) {
            if (selectedTabIndex < tabWidths.size && tabWidths[selectedTabIndex] > 0) {
                tabWidths[selectedTabIndex].toDp()
            } else {
                0.dp
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .onGloballyPositioned { containerWidth = it.size.width },
        shape = RoundedCornerShape(26.dp),
        color = containerColor,
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 0.5.dp,
            color = Color.White.copy(alpha = 0.12f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            // Sliding indicator
            if (indicatorWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                        .width(indicatorWidth)
                        .fillMaxHeight()
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(22.dp),
                            clip = false
                        )
                        .clip(RoundedCornerShape(22.dp))
                        .background(indicatorColor)
                )
            }

            // Tab row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    // Dynamic weight: give more space to longer tabs (Documents) and less to shorter ones (Health)
                    // Adding a base value (e.g. 6) ensures reasonable minimum width for icon/padding
                    val weight = (tab.label.length + 6).toFloat()

                    PremiumTabItem(
                        tab = tab,
                        isSelected = index == selectedTabIndex,
                        selectedTextColor = selectedTextColor,
                        unselectedTextColor = unselectedTextColor,
                        enableAnimations = enableAnimations,
                        onClick = {
                            if (index != selectedTabIndex) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onTabSelected(index)
                            }
                        },
                        onMeasured = { width, offset ->
                            if (index < tabWidths.size) {
                                tabWidths[index] = width
                                tabOffsets[index] = offset
                            }
                        },
                        modifier = Modifier.weight(weight)
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumTabItem(
    tab: PremiumTab,
    isSelected: Boolean,
    selectedTextColor: Color,
    unselectedTextColor: Color,
    enableAnimations: Boolean,
    onClick: () -> Unit,
    onMeasured: (width: Int, offset: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale: Float = if (enableAnimations) {
        val animatedScale by animateFloatAsState(
            targetValue = if (isPressed) 0.95f else 1f,
            animationSpec = tween(100),
            label = "tabScale"
        )
        animatedScale
    } else {
        1f
    }
    val textColor: Color = if (enableAnimations) {
        val animatedColor by animateColorAsState(
            targetValue = if (isSelected) selectedTextColor else unselectedTextColor,
            animationSpec = tween(200),
            label = "tabTextColor"
        )
        animatedColor
    } else {
        if (isSelected) selectedTextColor else unselectedTextColor
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .onGloballyPositioned { coordinates ->
                onMeasured(coordinates.size.width, coordinates.parentCoordinates?.let {
                    coordinates.positionInParent().x.toInt()
                } ?: 0)
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon (optional)
            if (tab.icon != null) {
                WrapIcon(
                    iconData = tab.icon,
                    customTint = textColor,
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .size(17.dp)
                )
            }

            // Label
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Badge (optional)
            if (tab.badge != null && tab.badge > 0) {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (tab.badge > 99) "99+" else tab.badge.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@ThemeModePreviews
@Composable
private fun PremiumTabRowPreview() {
    PreviewTheme {
        var selectedTab by remember { mutableIntStateOf(0) }

        PremiumTabRow(
            tabs = listOf(
                PremiumTab(label = "Documents"),
                PremiumTab(label = "Actions", badge = 3)
            ),
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.padding(16.dp)
        )
    }
}

@ThemeModePreviews
@Composable
private fun PremiumTabRowThreeTabsPreview() {
    PreviewTheme {
        var selectedTab by remember { mutableIntStateOf(1) }

        PremiumTabRow(
            tabs = listOf(
                PremiumTab(label = "Active", badge = 2),
                PremiumTab(label = "Pending"),
                PremiumTab(label = "History")
            ),
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.padding(16.dp)
        )
    }
}
