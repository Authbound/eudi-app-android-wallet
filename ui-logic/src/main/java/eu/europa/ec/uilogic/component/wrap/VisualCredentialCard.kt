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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import kotlinx.coroutines.delay

/**
 * Visual type for credential cards - determines styling.
 */
enum class CredentialVisualType {
    /** Personal Identification Data - EU Blue/Gold */
    PID,
    /** Mobile Driving License - Green */
    MDL,
    /** Education/Diploma credentials - Purple */
    DIPLOMA,
    /** Health-related credentials - Red */
    HEALTH,
    /** Generic/Other credentials - Gray */
    GENERIC
}

/**
 * Status of a credential.
 */
enum class CredentialStatus {
    ISSUED,
    PENDING,
    EXPIRED,
    REVOKED
}

/**
 * Configuration for visual credential card.
 */
data class VisualCredentialConfig(
    val id: String,
    val visualType: CredentialVisualType,
    val title: String,
    val subtitle: String?,
    val holderName: String?,
    val issuerName: String?,
    val primaryField: String?,
    val secondaryField: String?,
    val status: CredentialStatus,
    val expiryDate: String?,
    val hasPhoto: Boolean = false
)

/**
 * Color scheme for credential types.
 */
data class CredentialColorScheme(
    val gradientStart: Color,
    val gradientEnd: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

/**
 * Get color scheme for credential type.
 */
private fun getCredentialColors(type: CredentialVisualType): CredentialColorScheme {
    return when (type) {
        CredentialVisualType.PID -> CredentialColorScheme(
            gradientStart = Color(0xFF003399),
            gradientEnd = Color(0xFF001F5C),
            accent = Color(0xFFFFCC00),
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.8f)
        )
        CredentialVisualType.MDL -> CredentialColorScheme(
            gradientStart = Color(0xFF2E7D32),
            gradientEnd = Color(0xFF1B5E20),
            accent = Color(0xFFA5D6A7),
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.8f)
        )
        CredentialVisualType.DIPLOMA -> CredentialColorScheme(
            gradientStart = Color(0xFF7B1FA2),
            gradientEnd = Color(0xFF4A148C),
            accent = Color(0xFFCE93D8),
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.8f)
        )
        CredentialVisualType.HEALTH -> CredentialColorScheme(
            gradientStart = Color(0xFFC62828),
            gradientEnd = Color(0xFFB71C1C),
            accent = Color(0xFFEF9A9A),
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.8f)
        )
        CredentialVisualType.GENERIC -> CredentialColorScheme(
            gradientStart = Color(0xFF455A64),
            gradientEnd = Color(0xFF263238),
            accent = Color(0xFF90A4AE),
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.8f)
        )
    }
}

/**
 * A premium visual credential card inspired by Apple Wallet.
 * Displays documents with beautiful gradients and visual hierarchy.
 */
@Composable
fun VisualCredentialCard(
    config: VisualCredentialConfig,
    modifier: Modifier = Modifier,
    animationDelay: Int = 0,
    onClick: () -> Unit = {}
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val colors = getCredentialColors(config.visualType)

    // Entrance animation
    var isVisible by remember { mutableStateOf(animationDelay == 0) }
    LaunchedEffect(Unit) {
        if (animationDelay > 0) {
            delay(animationDelay.toLong())
            isVisible = true
        }
    }

    // Scale animation
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "credential_card_scale"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(400)) + slideInVertically(
            animationSpec = tween(400),
            initialOffsetY = { it / 3 }
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .scale(scale)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    }
                ),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(colors.gradientStart, colors.gradientEnd)
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header row: Type icon/flag + Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // Type badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (config.visualType == CredentialVisualType.PID) {
                                    // EU Flag indicator
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(colors.accent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "EU",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.gradientStart,
                                            fontSize = 8.sp
                                        )
                                    }
                                }

                                Text(
                                    text = getTypeLabel(config.visualType),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textSecondary,
                                    letterSpacing = 1.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Title
                            Text(
                                text = config.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Subtitle
                            if (!config.subtitle.isNullOrBlank()) {
                                Text(
                                    text = config.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Status badge
                        CredentialStatusBadge(
                            status = config.status,
                            colors = colors
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Content area: Photo placeholder + Details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Photo placeholder (if applicable)
                        if (config.hasPhoto) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.textPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                WrapIcon(
                                    iconData = AppIcons.User,
                                    customTint = colors.textPrimary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Details column
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Holder name
                            if (!config.holderName.isNullOrBlank()) {
                                Text(
                                    text = config.holderName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                            }

                            // Primary field
                            if (!config.primaryField.isNullOrBlank()) {
                                Text(
                                    text = config.primaryField,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }

                            // Secondary field / Expiry
                            if (!config.expiryDate.isNullOrBlank()) {
                                Text(
                                    text = "Valid until: ${config.expiryDate}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }

                    // Footer: Issuer info
                    if (!config.issuerName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WrapIcon(
                                iconData = AppIcons.Verified,
                                customTint = colors.accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = config.issuerName,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Status badge for credential cards.
 */
@Composable
private fun CredentialStatusBadge(
    status: CredentialStatus,
    colors: CredentialColorScheme,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, label) = when (status) {
        CredentialStatus.ISSUED -> colors.accent.copy(alpha = 0.2f) to "Issued"
        CredentialStatus.PENDING -> Color(0xFFFFA726).copy(alpha = 0.3f) to "Pending"
        CredentialStatus.EXPIRED -> Color(0xFFEF5350).copy(alpha = 0.3f) to "Expired"
        CredentialStatus.REVOKED -> Color(0xFFEF5350).copy(alpha = 0.3f) to "Revoked"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = backgroundColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * Get display label for credential type.
 */
private fun getTypeLabel(type: CredentialVisualType): String {
    return when (type) {
        CredentialVisualType.PID -> "PERSONAL ID"
        CredentialVisualType.MDL -> "DRIVING LICENSE"
        CredentialVisualType.DIPLOMA -> "EDUCATION"
        CredentialVisualType.HEALTH -> "HEALTH"
        CredentialVisualType.GENERIC -> "CREDENTIAL"
    }
}

@ThemeModePreviews
@Composable
private fun VisualCredentialCardPreview() {
    PreviewTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VisualCredentialCard(
                config = VisualCredentialConfig(
                    id = "1",
                    visualType = CredentialVisualType.PID,
                    title = "National ID Card",
                    subtitle = "Personal Identification Data",
                    holderName = "John Michael Doe",
                    issuerName = "Finland",
                    primaryField = "Born: 15.03.1990",
                    secondaryField = null,
                    status = CredentialStatus.ISSUED,
                    expiryDate = "01.01.2030",
                    hasPhoto = true
                )
            )

            VisualCredentialCard(
                config = VisualCredentialConfig(
                    id = "2",
                    visualType = CredentialVisualType.MDL,
                    title = "Driving License",
                    subtitle = "Mobile Driving License (mDL)",
                    holderName = "John Doe",
                    issuerName = "Transport Agency",
                    primaryField = "Categories: B, BE",
                    secondaryField = null,
                    status = CredentialStatus.PENDING,
                    expiryDate = "15.06.2028",
                    hasPhoto = true
                )
            )

            VisualCredentialCard(
                config = VisualCredentialConfig(
                    id = "3",
                    visualType = CredentialVisualType.DIPLOMA,
                    title = "Master's Degree",
                    subtitle = "Computer Science",
                    holderName = "John Doe",
                    issuerName = "University of Helsinki",
                    primaryField = "Graduated: 2020",
                    secondaryField = null,
                    status = CredentialStatus.ISSUED,
                    expiryDate = null,
                    hasPhoto = false
                )
            )
        }
    }
}
