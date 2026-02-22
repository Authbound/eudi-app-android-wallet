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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import kotlinx.coroutines.delay

/**
 * Illustration type for empty states.
 */
enum class EmptyStateIllustration {
    VERIFICATION,
    DOCUMENTS,
    HISTORY,
    SEARCH_NO_RESULTS,
    INBOX,
    HEALTH
}

/**
 * Configuration for a feature highlight card.
 */
data class FeatureHighlight(
    val icon: IconDataUi,
    val title: String,
    val description: String
)

/**
 * A premium empty state component with animated illustration.
 * Used when lists have no items to display.
 *
 * Features:
 * - Staggered entrance animations
 * - Bouncy illustration animation
 * - Optional feature highlight cards
 * - Premium action button
 */
@Composable
fun PremiumEmptyState(
    illustration: EmptyStateIllustration,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    features: List<FeatureHighlight>? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    enableAnimations: Boolean = true
) {
    val shouldAnimate: Boolean = enableAnimations
    var showIllustration by remember { mutableStateOf(!shouldAnimate) }
    var showTitle by remember { mutableStateOf(!shouldAnimate) }
    var showDescription by remember { mutableStateOf(!shouldAnimate) }
    var showFeatures by remember { mutableStateOf(!shouldAnimate) }
    var showAction by remember { mutableStateOf(!shouldAnimate) }

    LaunchedEffect(shouldAnimate) {
        if (shouldAnimate) {
            delay(100)
            showIllustration = true
            delay(150)
            showTitle = true
            delay(100)
            showDescription = true
            delay(150)
            showFeatures = true
            delay(150)
            showAction = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (shouldAnimate) {
            AnimatedVisibility(
                visible = showIllustration,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -it / 4 }
            ) {
                EmptyStateIllustrationComponent(illustration = illustration, enableAnimations = true)
            }
        } else {
            EmptyStateIllustrationComponent(illustration = illustration, enableAnimations = false)
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (shouldAnimate) {
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (shouldAnimate) {
            AnimatedVisibility(
                visible = showDescription,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Feature highlights
        if (!features.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))

            if (shouldAnimate) {
                AnimatedVisibility(
                    visible = showFeatures,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        features.forEach { feature ->
                            FeatureHighlightCard(
                                feature = feature,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    features.forEach { feature ->
                        FeatureHighlightCard(
                            feature = feature,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Action button
        if (actionLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(32.dp))

            if (shouldAnimate) {
                AnimatedVisibility(
                    visible = showAction,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }
                ) {
                    PremiumActionButton(
                        text = actionLabel,
                        onClick = onActionClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                PremiumActionButton(
                    text = actionLabel,
                    onClick = onActionClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun EmptyStateIllustrationComponent(
    illustration: EmptyStateIllustration,
    modifier: Modifier = Modifier,
    enableAnimations: Boolean = true
) {
    val pulseScale: Float = if (enableAnimations) {
        val infiniteTransition = rememberInfiniteTransition(label = "illustrationPulse")
        val animatedScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1500,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "illustrationPulseScale"
        )
        animatedScale
    } else {
        1f
    }

    val (mainIcon, accentIcon, primaryColor, secondaryColor) = when (illustration) {
        EmptyStateIllustration.VERIFICATION -> IllustrationColors(
            mainIcon = AppIcons.Verified,
            accentIcon = AppIcons.Add,
            primaryColor = MaterialTheme.colorScheme.tertiary,
            secondaryColor = MaterialTheme.colorScheme.tertiaryContainer
        )
        EmptyStateIllustration.DOCUMENTS -> IllustrationColors(
            mainIcon = AppIcons.IdCards,
            accentIcon = AppIcons.Add,
            primaryColor = MaterialTheme.colorScheme.primary,
            secondaryColor = MaterialTheme.colorScheme.primaryContainer
        )
        EmptyStateIllustration.HISTORY -> IllustrationColors(
            mainIcon = AppIcons.ClockTimer,
            accentIcon = null,
            primaryColor = MaterialTheme.colorScheme.secondary,
            secondaryColor = MaterialTheme.colorScheme.secondaryContainer
        )
        EmptyStateIllustration.SEARCH_NO_RESULTS -> IllustrationColors(
            mainIcon = AppIcons.Search,
            accentIcon = null,
            primaryColor = MaterialTheme.colorScheme.outline,
            secondaryColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
        EmptyStateIllustration.INBOX -> IllustrationColors(
            mainIcon = AppIcons.Inbox,
            accentIcon = null,
            primaryColor = MaterialTheme.colorScheme.secondary,
            secondaryColor = MaterialTheme.colorScheme.secondaryContainer
        )
        EmptyStateIllustration.HEALTH -> IllustrationColors(
            mainIcon = AppIcons.Health,
            accentIcon = AppIcons.Add,
            primaryColor = MaterialTheme.colorScheme.error,
            secondaryColor = MaterialTheme.colorScheme.errorContainer
        )
    }

    Box(
        modifier = modifier
            .size(120.dp)
            .scale(pulseScale),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            primaryColor.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Inner circle
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = secondaryColor.copy(alpha = 0.4f),
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                WrapIcon(
                    iconData = mainIcon,
                    customTint = primaryColor,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Floating accent badge (optional)
        if (accentIcon != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = accentIcon,
                    customTint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FeatureHighlightCard(
    feature: FeatureHighlight,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = feature.icon,
                    customTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PremiumActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconDataUi? = null
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(100),
        label = "buttonScale"
    )

    Button(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        },
        modifier = modifier
            .height(56.dp)
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        interactionSource = interactionSource,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        if (icon != null) {
            WrapIcon(
                iconData = icon,
                customTint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Data class to hold illustration colors and icons.
 */
private data class IllustrationColors(
    val mainIcon: IconDataUi,
    val accentIcon: IconDataUi?,
    val primaryColor: Color,
    val secondaryColor: Color
)

@ThemeModePreviews
@Composable
private fun PremiumEmptyStateVerificationPreview() {
    PreviewTheme {
        PremiumEmptyState(
            illustration = EmptyStateIllustration.VERIFICATION,
            title = "No Active Verifications",
            description = "Create a verification request to start verifying credentials from others.",
            features = listOf(
                FeatureHighlight(
                    icon = AppIcons.QrScanner,
                    title = "QR Code",
                    description = "Share via QR"
                ),
                FeatureHighlight(
                    icon = AppIcons.OpenNew,
                    title = "Deep Link",
                    description = "Send a link"
                )
            ),
            actionLabel = "Create Verification",
            onActionClick = {}
        )
    }
}

@ThemeModePreviews
@Composable
private fun PremiumEmptyStateHistoryPreview() {
    PreviewTheme {
        PremiumEmptyState(
            illustration = EmptyStateIllustration.HISTORY,
            title = "No History Yet",
            description = "Your completed verifications will appear here."
        )
    }
}
