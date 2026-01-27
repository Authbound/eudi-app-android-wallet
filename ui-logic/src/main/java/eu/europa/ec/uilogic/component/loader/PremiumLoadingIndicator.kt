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

package eu.europa.ec.uilogic.component.loader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.ANIMATION_DURATION_ENTRANCE
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.Z_LOADING
import eu.europa.ec.uilogic.extension.clickableNoRipple

// Brand colors for premium loading indicator
private val NavyBackdrop = Color(0xFF0A1A36).copy(alpha = 0.85f)
private val CardBackground = Color(0xFF1E3A5F)
private val SpinnerPrimary = Color(0xFF3B82F6)
private val SpinnerSecondary = Color(0xFF2A8A9A)
private val GlowColor = Color(0xFF3B82F6).copy(alpha = 0.30f)

// Animation constants
private const val SPINNER_ROTATION_DURATION = 1200
private const val GLOW_PULSE_DURATION = 1500
private const val GLOW_SCALE_MIN = 1.0f
private const val GLOW_SCALE_MAX = 1.05f

/**
 * Premium full-screen loading indicator with Authbound brand styling.
 *
 * Features:
 * - Navy gradient backdrop at 85% opacity
 * - Centered card with elevation and rounded corners
 * - Custom dual-arc spinner with blue (#3B82F6) and teal (#2A8A9A) colors
 * - Pulsing glow effect around the spinner
 * - Optional message and progress bar
 * - Smooth 300ms fade-in animation with spring physics for rotation
 *
 * @param visible Whether the loading indicator is visible
 * @param config Loading configuration with message and progress options
 */
@Composable
fun PremiumLoadingIndicator(
    visible: Boolean,
    config: LoadingConfig = LoadingConfig.fullScreen()
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION_ENTRANCE,
                easing = androidx.compose.animation.core.EaseOut
            )
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION_ENTRANCE,
                easing = androidx.compose.animation.core.EaseIn
            )
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(Z_LOADING)
                .background(NavyBackdrop)
                .clickableNoRipple { /* Block interactions */ }
        ) {
            LoadingCard(config = config)
        }
    }
}

@Composable
private fun LoadingCard(config: LoadingConfig) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(SPACING_MEDIUM.dp),
                ambientColor = SpinnerPrimary.copy(alpha = 0.2f),
                spotColor = SpinnerPrimary.copy(alpha = 0.2f)
            )
            .clip(RoundedCornerShape(SPACING_MEDIUM.dp))
            .background(CardBackground)
            .padding(SPACING_LARGE.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
        ) {
            // Spinner with glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp)
            ) {
                GlowEffect()
                DualArcSpinner()
            }

            // Optional message
            config.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            // Optional progress bar
            if (config.showProgress) {
                ProgressBar(progress = config.progress)
            }
        }
    }
}

@Composable
private fun GlowEffect() {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")

    val glowScale by infiniteTransition.animateFloat(
        initialValue = GLOW_SCALE_MIN,
        targetValue = GLOW_SCALE_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(GLOW_PULSE_DURATION, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    Canvas(
        modifier = Modifier.size((64 * glowScale).dp)
    ) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    GlowColor,
                    GlowColor.copy(alpha = 0.1f),
                    Color.Transparent
                )
            ),
            radius = size.minDimension / 2
        )
    }
}

@Composable
private fun DualArcSpinner() {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = SPINNER_ROTATION_DURATION,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Secondary arc rotates in opposite direction for visual interest
    val secondaryRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (SPINNER_ROTATION_DURATION * 1.5).toInt(),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "secondaryRotation"
    )

    Canvas(modifier = Modifier.size(48.dp)) {
        val strokeWidth = 4.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)

        // Primary arc (blue)
        rotate(rotation, center) {
            drawArc(
                color = SpinnerPrimary,
                startAngle = 0f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Secondary arc (teal)
        rotate(secondaryRotation, center) {
            drawArc(
                color = SpinnerSecondary,
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    val clampedProgress = progress.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_SMALL.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedProgress)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(SpinnerPrimary, SpinnerSecondary)
                    )
                )
        )
    }

    // Progress percentage text
    Text(
        text = "${(clampedProgress * 100).toInt()}%",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.7f)
    )
}

/**
 * Inline loading indicator for buttons and form fields.
 *
 * A smaller, simpler spinner that fits within compact UI elements.
 * Uses the same brand colors but without the backdrop or card.
 */
@Composable
fun InlineLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Int = 24
) {
    val infiniteTransition = rememberInfiniteTransition(label = "inlineSpinner")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = SPINNER_ROTATION_DURATION,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "inlineRotation"
    )

    Canvas(modifier = modifier.size(size.dp)) {
        val strokeWidth = 2.dp.toPx()
        val radius = (this.size.minDimension - strokeWidth) / 2
        val center = Offset(this.size.width / 2, this.size.height / 2)

        rotate(rotation, center) {
            drawArc(
                color = SpinnerPrimary,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

// Preview section
@ThemeModePreviews
@Composable
private fun PremiumLoadingIndicatorPreview() {
    PreviewTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            PremiumLoadingIndicator(
                visible = true,
                config = LoadingConfig.fullScreen()
            )
        }
    }
}

@ThemeModePreviews
@Composable
private fun PremiumLoadingWithMessagePreview() {
    PreviewTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            PremiumLoadingIndicator(
                visible = true,
                config = LoadingConfig.fullScreenWithMessage("Issuing credential...")
            )
        }
    }
}

@ThemeModePreviews
@Composable
private fun PremiumLoadingWithProgressPreview() {
    PreviewTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            PremiumLoadingIndicator(
                visible = true,
                config = LoadingConfig.fullScreenWithProgress(
                    message = "Downloading...",
                    progress = 0.65f
                )
            )
        }
    }
}

@ThemeModePreviews
@Composable
private fun InlineLoadingIndicatorPreview() {
    PreviewTheme {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp)
        ) {
            InlineLoadingIndicator()
        }
    }
}
