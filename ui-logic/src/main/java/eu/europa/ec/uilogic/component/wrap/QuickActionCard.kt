
package eu.europa.ec.uilogic.component.wrap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.AppIcons

/**
 * Data class for configuring a QuickActionCard with premium gradient styling.
 * Supports diagonal gradients (135°) with accent glow for icon containers.
 */
data class QuickActionConfig(
    val id: String,
    val title: String,
    val description: String,
    val icon: IconDataUi,
    val gradientStart: Color,           // Primary gradient color (darker)
    val gradientEnd: Color,             // Secondary gradient color (lighter)
    val accentColor: Color,             // Accent color for icon glow
    val isEnabled: Boolean = true
) {
    // Legacy constructor for backward compatibility
    constructor(
        id: String,
        title: String,
        description: String,
        icon: IconDataUi,
        backgroundColor: Color,
        borderColor: Color,
        isEnabled: Boolean = true
    ) : this(
        id = id,
        title = title,
        description = description,
        icon = icon,
        gradientStart = backgroundColor,
        gradientEnd = backgroundColor.copy(alpha = 0.85f),
        accentColor = borderColor,
        isEnabled = isEnabled
    )
}

/**
 * Premium QuickActionCard with diagonal gradient background, icon glow container,
 * decorative circles, and arrow indicator. Matches Authbound web dashboard design.
 */
@Composable
fun QuickActionCard(
    modifier: Modifier = Modifier,
    config: QuickActionConfig,
    animationDelay: Int = 0,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animation for card scaling when pressed
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "scale"
    )

    var isVisible by remember { mutableStateOf(animationDelay == 0) }
    LaunchedEffect(Unit) {
        if (animationDelay > 0) {
            delay(animationDelay.toLong())
            isVisible = true
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(280)) + slideInVertically(
            animationSpec = tween(280),
            initialOffsetY = { it / 5 }
        )
    ) {
        Surface(
            modifier = modifier
                .scale(scale)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.2f)),
                    enabled = config.isEnabled,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    }
                ),
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(config.gradientStart, config.gradientEnd),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    )
            ) {
                QuickActionMotif(
                    modifier = Modifier.align(Alignment.TopEnd),
                    id = config.id,
                    color = config.accentColor
                )

                // Main content
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top section: Icon with glow + Title
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Icon with accent glow ring
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(config.accentColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            WrapIcon(
                                iconData = config.icon,
                                customTint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Title
                        Text(
                            text = config.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Bottom section: Description + Arrow indicator
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Description
                        Text(
                            text = config.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Arrow indicator
                        WrapIcon(
                            iconData = AppIcons.KeyboardArrowRight,
                            customTint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionMotif(
    modifier: Modifier = Modifier,
    id: String,
    color: Color
) {
    Canvas(
        modifier = modifier
            .size(80.dp)
            .padding(8.dp)
    ) {
        when (id) {
            "authenticate" -> {
                // Diagonal scan-beam lines (security scanner motif)
                val lineColor = color.copy(alpha = 0.11f)
                val gap = 11.dp.toPx()
                for (i in 0..4) {
                    val o = i * gap
                    drawLine(
                        color = lineColor,
                        start = Offset(size.width - o, 0f),
                        end = Offset(size.width, o),
                        strokeWidth = 6.dp.toPx()
                    )
                }
            }
            "add_credentials" -> {
                // Corner-bracket geometry (card-insertion motif)
                val c = color.copy(alpha = 0.18f)
                val s = 16.dp.toPx()
                val sw = 1.8.dp.toPx()
                val lx = size.width * 0.55f; val ly = size.height * 0.15f
                val rx = size.width * 0.95f; val ry = size.height * 0.72f
                drawLine(c, Offset(lx, ly), Offset(lx + s, ly), sw)
                drawLine(c, Offset(lx, ly), Offset(lx, ly + s), sw)
                drawLine(c, Offset(rx - s, ry), Offset(rx, ry), sw)
                drawLine(c, Offset(rx, ry - s), Offset(rx, ry), sw)
                drawRect(
                    color = color.copy(alpha = 0.07f),
                    topLeft = Offset(lx + 5.dp.toPx(), ly + 5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(
                        rx - lx - 10.dp.toPx(),
                        ry - ly - 10.dp.toPx()
                    )
                )
            }
            "verify" -> {
                // Radiating concentric rings (trust/signal motif)
                val center = Offset(size.width * 0.72f, size.height * 0.28f)
                listOf(6.dp.toPx(), 14.dp.toPx(), 24.dp.toPx()).forEachIndexed { i, r ->
                    drawCircle(
                        color = color.copy(alpha = 0.18f - i * 0.04f),
                        radius = r,
                        center = center,
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }
                drawCircle(color = color.copy(alpha = 0.22f), radius = 3.dp.toPx(), center = center)
            }
            "sign" -> {
                // Sweeping arc (handwritten signature motif)
                drawArc(
                    color = color.copy(alpha = 0.14f),
                    startAngle = 200f, sweepAngle = 130f, useCenter = false,
                    topLeft = Offset(size.width * 0.10f, size.height * 0.05f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.80f, size.height * 0.80f),
                    style = Stroke(
                        width = 2.8.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
                drawArc(
                    color = color.copy(alpha = 0.08f),
                    startAngle = 165f, sweepAngle = 70f, useCenter = false,
                    topLeft = Offset(size.width * 0.30f, size.height * 0.28f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.60f, size.height * 0.55f),
                    style = Stroke(
                        width = 1.6.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
            else -> {
                // Fallback: original three-circle pattern
                drawCircle(color.copy(alpha = 0.10f), 28.dp.toPx(), Offset(size.width * 0.7f, size.height * 0.3f), style = Stroke(1.5.dp.toPx()))
                drawCircle(color.copy(alpha = 0.08f), 12.dp.toPx(), Offset(size.width * 0.35f, size.height * 0.55f))
                drawCircle(color.copy(alpha = 0.12f), 5.dp.toPx(), Offset(size.width * 0.85f, size.height * 0.7f))
            }
        }
    }
}

@ThemeModePreviews
@Composable
private fun QuickActionCardPreview() {
    PreviewTheme {
        QuickActionCard(
            modifier = Modifier.size(width = 180.dp, height = 140.dp),
            config = QuickActionConfig(
                id = "authenticate",
                title = "Authenticate",
                description = "Verify your identity securely",
                icon = AppIcons.IdCards,
                gradientStart = Color(0xFF0A1A36),
                gradientEnd = Color(0xFF1E3A5F),
                accentColor = Color(0xFF3B82F6)
            ),
            onClick = {}
        )
    }
} 