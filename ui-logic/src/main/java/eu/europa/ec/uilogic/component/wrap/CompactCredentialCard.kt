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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ImageOrPlaceholder
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import kotlinx.coroutines.delay

/** Configuration for a compact credential card on the homescreen. */
data class CompactCredentialConfig(
        val id: String,
        val visualType: CredentialVisualType,
        val title: String,
        val issuerName: String?,
        val status: CredentialStatus,
        val expiryDate: String?,
        val hasPhoto: Boolean = false,
        val portraitBase64: String? = null
)

/**
 * Compact credential card for the homescreen "Your Credentials" section.
 *
 * Uses the same gradient color system as VisualCredentialCard but in a tight, 72dp-tall horizontal
 * layout. Left side shows a type-colored accent strip with an icon, the body has title + issuer,
 * and the right edge shows a status dot and optional chevron.
 */
@Composable
fun CompactCredentialCard(
        config: CompactCredentialConfig,
        modifier: Modifier = Modifier,
        animationDelay: Int = 0,
        onClick: () -> Unit = {}
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val colors = getCompactColors(config.visualType)

    val scale by
            animateFloatAsState(
                    targetValue = if (isPressed) 0.975f else 1f,
                    animationSpec = tween(durationMillis = 120),
                    label = "compact_card_scale"
            )

    var isVisible by remember { mutableStateOf(animationDelay == 0) }
    LaunchedEffect(Unit) {
        if (animationDelay > 0) {
            delay(animationDelay.toLong())
            isVisible = true
        }
    }

    val cardShape = RoundedCornerShape(16.dp)

    AnimatedVisibility(
            visible = isVisible,
            enter =
                    fadeIn(tween(250)) +
                            slideInVertically(
                                    animationSpec = tween(250),
                                    initialOffsetY = { it / 6 }
                            )
    ) {
        Surface(
                modifier =
                        modifier.fillMaxWidth()
                                .height(72.dp)
                                .scale(scale)
                                .clickable(
                                        interactionSource = interactionSource,
                                        indication = ripple(bounded = true),
                                        onClick = {
                                            view.performHapticFeedback(
                                                    HapticFeedbackConstants.VIRTUAL_KEY
                                            )
                                            onClick()
                                        }
                                ),
                shape = cardShape,
                shadowElevation = 6.dp,
                color = Color.Transparent
        ) {
            Box(
                    modifier =
                            Modifier.fillMaxSize()
                                    .background(
                                            Brush.linearGradient(
                                                    colors =
                                                            listOf(
                                                                    colors.gradientStart,
                                                                    colors.gradientEnd
                                                            ),
                                                    start = Offset(0f, 0f),
                                                    end =
                                                            Offset(
                                                                    Float.POSITIVE_INFINITY,
                                                                    Float.POSITIVE_INFINITY
                                                            )
                                            )
                                    )
            ) {
                // Subtle accent arc in top-right
                CompactDecorativeArc(
                        modifier = Modifier.align(Alignment.TopEnd).size(72.dp),
                        color = colors.accent
                )

                // Top edge highlight (simulates light reflection)
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .align(Alignment.TopCenter)
                )

                Row(
                        modifier = Modifier.fillMaxSize().padding(start = 0.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left accent strip with type icon
                    Box(
                            modifier = Modifier.width(52.dp).height(72.dp),
                            contentAlignment = Alignment.Center
                    ) {
                        // Accent glow behind icon
                        Box(
                                modifier =
                                        Modifier.size(36.dp)
                                                .clip(CircleShape)
                                                .background(colors.accent.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                        ) {
                            if (config.hasPhoto && !config.portraitBase64.isNullOrBlank()) {
                                ImageOrPlaceholder(
                                        modifier = Modifier.size(32.dp).clip(CircleShape),
                                        base64Image = config.portraitBase64.orEmpty(),
                                        contentScale = ContentScale.Crop,
                                        fallbackIcon = AppIcons.User
                                )
                            } else {
                                WrapIcon(
                                        iconData = getCompactTypeIcon(config.visualType),
                                        customTint = colors.accent,
                                        modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Title + issuer
                    Column(
                            modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                            verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                                text = config.title,
                                style =
                                        MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = (-0.2).sp
                                        ),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )

                        if (!config.issuerName.isNullOrBlank()) {
                            Text(
                                    text = config.issuerName,
                                    style =
                                            MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 11.sp
                                            ),
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Status dot + type label column
                    Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.Center
                    ) {
                        CompactStatusIndicator(status = config.status, accent = colors.accent)

                        if (!config.expiryDate.isNullOrBlank()) {
                            Text(
                                    text = config.expiryDate,
                                    style =
                                            MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    letterSpacing = 0.3.sp
                                            ),
                                    color = colors.textSecondary.copy(alpha = 0.7f),
                                    maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Tiny status indicator — a colored dot with a label. */
@Composable
private fun CompactStatusIndicator(
        status: CredentialStatus,
        accent: Color,
        modifier: Modifier = Modifier
) {
    val (dotColor, label) =
            when (status) {
                CredentialStatus.ISSUED -> Pair(Color(0xFF4ADE80), "Active")
                CredentialStatus.PENDING -> Pair(Color(0xFFFBBF24), "Pending")
                CredentialStatus.EXPIRED -> Pair(Color(0xFFEF4444), "Expired")
                CredentialStatus.REVOKED -> Pair(Color(0xFFEF4444), "Revoked")
            }

    Row(
            modifier = modifier.padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Glowing dot
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        Text(
                text = label,
                style =
                        MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                        ),
                color = dotColor
        )
    }
}

/** Decorative arc for the compact card — single subtle sweep in the top-right. */
@Composable
private fun CompactDecorativeArc(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        drawArc(
                color = color.copy(alpha = 0.07f),
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(size.width * 0.2f, -size.height * 0.4f),
                size = androidx.compose.ui.geometry.Size(size.width, size.height),
                style = Stroke(width = 1.5.dp.toPx())
        )
        // Small filled circle accent
        drawCircle(
                color = color.copy(alpha = 0.05f),
                radius = 8.dp.toPx(),
                center = Offset(size.width * 0.7f, size.height * 0.6f)
        )
    }
}

/**
 * Color scheme for compact cards — same brand palette as VisualCredentialCard but optimized for the
 * condensed format.
 */
private fun getCompactColors(type: CredentialVisualType): CredentialColorScheme {
    return when (type) {
        CredentialVisualType.PID ->
                CredentialColorScheme(
                        gradientStart = Color(0xFF0A1A36), // Navy Deep (matches full card)
                        gradientEnd = Color(0xFF1E3A5F), // Navy Medium
                        accent = Color(0xFFD4A84B),
                        textPrimary = Color.White,
                        textSecondary = Color.White.copy(alpha = 0.70f)
                )
        CredentialVisualType.MDL ->
                CredentialColorScheme(
                        gradientStart = Color(0xFF0C1E3D),
                        gradientEnd = Color(0xFF152D52),
                        accent = Color(0xFF2DD4BF),
                        textPrimary = Color.White,
                        textSecondary = Color.White.copy(alpha = 0.65f)
                )
        CredentialVisualType.DIPLOMA ->
                CredentialColorScheme(
                        gradientStart = Color(0xFF121A30),
                        gradientEnd = Color(0xFF1C2B4A),
                        accent = Color(0xFFA78BFA),
                        textPrimary = Color.White,
                        textSecondary = Color.White.copy(alpha = 0.65f)
                )
        CredentialVisualType.HEALTH ->
                CredentialColorScheme(
                        gradientStart = Color(0xFF1C1522),
                        gradientEnd = Color(0xFF2C2238),
                        accent = Color(0xFFF472B6),
                        textPrimary = Color.White,
                        textSecondary = Color.White.copy(alpha = 0.65f)
                )
        CredentialVisualType.GENERIC ->
                CredentialColorScheme(
                        gradientStart = Color(0xFF0C1E3D),
                        gradientEnd = Color(0xFF1A3358),
                        accent = Color(0xFF60A5FA),
                        textPrimary = Color.White,
                        textSecondary = Color.White.copy(alpha = 0.65f)
                )
        CredentialVisualType.AUTHBOUND ->
                CredentialColorScheme(
                        gradientStart = Color(0xFF0A1A36),
                        gradientEnd = Color(0xFF1A3060),
                        accent = Color(0xFF3B82F6),
                        textPrimary = Color.White,
                        textSecondary = Color.White.copy(alpha = 0.70f)
                )
    }
}

/** Icon per credential type for the compact left badge. */
private fun getCompactTypeIcon(type: CredentialVisualType) =
        when (type) {
            CredentialVisualType.PID -> AppIcons.IdCards
            CredentialVisualType.MDL -> AppIcons.IdCards
            CredentialVisualType.DIPLOMA -> AppIcons.Education
            CredentialVisualType.HEALTH -> AppIcons.Health
            CredentialVisualType.GENERIC -> AppIcons.Documents
            CredentialVisualType.AUTHBOUND -> AppIcons.Verified
        }

@ThemeModePreviews
@Composable
private fun CompactCredentialCardPreview() {
    PreviewTheme {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CompactCredentialCard(
                    config =
                            CompactCredentialConfig(
                                    id = "1",
                                    visualType = CredentialVisualType.PID,
                                    title = "National ID Card",
                                    issuerName = "Government Authority",
                                    status = CredentialStatus.ISSUED,
                                    expiryDate = "01/2030",
                                    hasPhoto = false
                            )
            )
            CompactCredentialCard(
                    config =
                            CompactCredentialConfig(
                                    id = "2",
                                    visualType = CredentialVisualType.MDL,
                                    title = "Driving License",
                                    issuerName = "Transport Agency",
                                    status = CredentialStatus.PENDING,
                                    expiryDate = "06/2028",
                                    hasPhoto = false
                            )
            )
            CompactCredentialCard(
                    config =
                            CompactCredentialConfig(
                                    id = "3",
                                    visualType = CredentialVisualType.DIPLOMA,
                                    title = "Master's Degree",
                                    issuerName = "University of Helsinki",
                                    status = CredentialStatus.ISSUED,
                                    expiryDate = null,
                                    hasPhoto = false
                            )
            )
            CompactCredentialCard(
                    config =
                            CompactCredentialConfig(
                                    id = "4",
                                    visualType = CredentialVisualType.HEALTH,
                                    title = "Health Insurance",
                                    issuerName = "Kela",
                                    status = CredentialStatus.EXPIRED,
                                    expiryDate = "01/2024",
                                    hasPhoto = false
                            )
            )
        }
    }
}
