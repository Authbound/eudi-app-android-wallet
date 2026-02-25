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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.ANIMATION_DURATION_ENTRANCE
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.Z_LOADING
import eu.europa.ec.uilogic.extension.clickableNoRipple

// ── Authbound brand palette ──────────────────────────────────────────
private val Navy = Color(0xFF0A1A36)
private val DeepNavy = Color(0xFF060F20)
private val AccentBlue = Color(0xFF3B82F6)
private val LightBlue = Color(0xFF60A5FA)
private val SkyBlue = Color(0xFF93C5FD)
private val Teal = Color(0xFF2DD4BF)

// ── Ring animation timing ────────────────────────────────────────────
// Each ring has a DIFFERENT duration so they never sync up and "merge".
// The visual trick: short arcs (60-80°) on rings of different radii
// at non-harmonic speeds means they drift independently forever.
private const val INNER_RING_DURATION = 1800
private const val OUTER_RING_DURATION = 2800
private const val PARTICLE_RING_DURATION = 4200

// Logo breathing
private const val BREATHE_DURATION = 2400

// Smooth deceleration curve — feels like the arc is "sweeping" then coasting
private val SwingEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

/**
 * Premium loading indicator featuring the Authbound shield logo.
 *
 * Three concentric orbital rings sweep around the logo at different
 * speeds and radii. The rings never overlap because each is a short
 * gradient arc (60-80°) on its own track. The logo breathes with a
 * warm glow underneath.
 */
@Composable
fun PremiumLoadingIndicator(
    visible: Boolean,
    config: LoadingConfig = LoadingConfig.fullScreen()
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(ANIMATION_DURATION_ENTRANCE, easing = EaseOut)),
        exit = fadeOut(animationSpec = tween(ANIMATION_DURATION_ENTRANCE, easing = EaseIn))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(Z_LOADING)
                .background(DeepNavy.copy(alpha = 0.92f))
                .clickableNoRipple { }
        ) {
            LoadingCard(config = config)
        }
    }
}

@Composable
private fun LoadingCard(config: LoadingConfig) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // The entire orbital system — compact 120dp box
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(120.dp)
        ) {
            // Layer 1: Large ambient glow radiating from center
            AmbientGlow()

            // Layer 2: Outer ring — slowest, most transparent
            OrbitalArc(
                ringSize = 110f,
                strokeWidth = 1.5f,
                sweep = 120f,
                duration = OUTER_RING_DURATION,
                headColor = SkyBlue.copy(alpha = 0.5f),
                clockwise = true
            )

            // Layer 3: Inner ring — fastest, most visible
            OrbitalArc(
                ringSize = 76f,
                strokeWidth = 2.5f,
                sweep = 140f,
                duration = INNER_RING_DURATION,
                headColor = AccentBlue,
                clockwise = true
            )

            // Layer 4: Middle ring — counter-rotation for depth
            OrbitalArc(
                ringSize = 92f,
                strokeWidth = 2f,
                sweep = 100f,
                duration = PARTICLE_RING_DURATION,
                headColor = Teal.copy(alpha = 0.7f),
                clockwise = false
            )

            // Layer 5: Logo with breathing effect
            ShieldLogo()
        }

        // Optional message
        config.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }

        // Optional progress bar
        if (config.showProgress) {
            ProgressBar(progress = config.progress)
        }
    }
}

// ── Shield Logo ──────────────────────────────────────────────────────

@Composable
private fun ShieldLogo() {
    val transition = rememberInfiniteTransition(label = "breathe")

    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATHE_DURATION, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    val glowAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATHE_DURATION, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoGlow"
    )

    Box(contentAlignment = Alignment.Center) {
        // Wide radial glow behind the logo — fills most of the orbital area
        Canvas(modifier = Modifier.size(140.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = glowAlpha * 0.18f),
                        0.1f to Color.White.copy(alpha = glowAlpha * 0.12f),
                        0.25f to AccentBlue.copy(alpha = glowAlpha * 0.28f),
                        0.45f to AccentBlue.copy(alpha = glowAlpha * 0.12f),
                        0.7f to Navy.copy(alpha = glowAlpha * 0.05f),
                        1f to Color.Transparent
                    )
                ),
                radius = size.minDimension / 2
            )
        }

        Image(
            painter = painterResource(id = R.drawable.authbound_logo_bold),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier
                .size((44 * scale).dp)
                .alpha(0.95f)
        )
    }
}

// ── Ambient Glow ─────────────────────────────────────────────────────

@Composable
private fun AmbientGlow() {
    val transition = rememberInfiniteTransition(label = "ambient")

    val pulse by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientPulse"
    )

    Canvas(modifier = Modifier.size(120.dp)) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to AccentBlue.copy(alpha = pulse * 1.2f),
                    0.3f to AccentBlue.copy(alpha = pulse * 0.5f),
                    0.6f to Navy.copy(alpha = pulse * 0.2f),
                    1f to Color.Transparent
                ),
                radius = size.minDimension / 2
            ),
            radius = size.minDimension / 2
        )
    }
}

// ── Single Orbital Arc ───────────────────────────────────────────────
//
// The leading edge ("head") must always be the brightest point.
// For clockwise rotation the head is at the END of the sweep angle,
// so the gradient runs: transparent (tail @ 0°) → opaque (head @ sweep°).
// For counter-clockwise the head is at the START, so it's reversed.

@Composable
private fun OrbitalArc(
    ringSize: Float,
    strokeWidth: Float,
    sweep: Float,
    duration: Int,
    headColor: Color,
    clockwise: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "ring_${duration}")

    val rotation by transition.animateFloat(
        initialValue = if (clockwise) 0f else 360f,
        targetValue = if (clockwise) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot_$duration"
    )

    val sweepFraction = sweep / 360f

    // Build gradient so bright end = leading edge of rotation.
    // Multiple intermediate stops create a long, smooth tail fade.
    val colorStops = if (clockwise) {
        // Clockwise: head is at sweep end → tail fades in gradually from 0°
        arrayOf(
            0f to Color.Transparent,
            sweepFraction * 0.15f to headColor.copy(alpha = 0.03f),
            sweepFraction * 0.35f to headColor.copy(alpha = 0.10f),
            sweepFraction * 0.55f to headColor.copy(alpha = 0.25f),
            sweepFraction * 0.75f to headColor.copy(alpha = 0.50f),
            sweepFraction * 0.90f to headColor.copy(alpha = 0.80f),
            sweepFraction to headColor,
            sweepFraction + 0.001f to Color.Transparent,
            1f to Color.Transparent
        )
    } else {
        // Counter-clockwise: head is at sweep start → tail fades out gradually
        arrayOf(
            0f to headColor,
            sweepFraction * 0.10f to headColor.copy(alpha = 0.80f),
            sweepFraction * 0.25f to headColor.copy(alpha = 0.50f),
            sweepFraction * 0.45f to headColor.copy(alpha = 0.25f),
            sweepFraction * 0.65f to headColor.copy(alpha = 0.10f),
            sweepFraction * 0.85f to headColor.copy(alpha = 0.03f),
            sweepFraction to Color.Transparent,
            sweepFraction + 0.001f to Color.Transparent,
            1f to Color.Transparent
        )
    }

    Canvas(modifier = Modifier.size(ringSize.dp)) {
        val sw = strokeWidth.dp.toPx()
        val radius = (size.minDimension - sw) / 2
        val center = Offset(size.width / 2, size.height / 2)

        rotate(rotation, center) {
            drawArc(
                brush = Brush.sweepGradient(colorStops = colorStops),
                startAngle = 0f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
    }
}

// ── Progress Bar ─────────────────────────────────────────────────────

@Composable
private fun ProgressBar(progress: Float) {
    val clamped = progress.coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 40.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(AccentBlue, Teal)
                        )
                    )
            )
        }
        Text(
            text = "${(clamped * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

// ── Inline Loading (for buttons / fields) ────────────────────────────

@Composable
fun InlineLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Int = 24
) {
    val transition = rememberInfiniteTransition(label = "inlineSpinner")

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(INNER_RING_DURATION, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inlineRotation"
    )

    Canvas(modifier = modifier.size(size.dp)) {
        val sw = 2.dp.toPx()
        val radius = (this.size.minDimension - sw) / 2
        val center = Offset(this.size.width / 2, this.size.height / 2)

        rotate(rotation, center) {
            drawArc(
                brush = Brush.sweepGradient(
                    colorStops = arrayOf(
                        0f to AccentBlue,
                        0.22f to LightBlue,
                        0.22f to Color.Transparent,
                        1f to Color.Transparent
                    )
                ),
                startAngle = 0f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────

@ThemeModePreviews
@Composable
private fun PremiumLoadingIndicatorPreview() {
    PreviewTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            PremiumLoadingIndicator(visible = true)
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
