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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.theme.values.success
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Transaction type categories for visual styling.
 */
enum class TransactionType {
    SHARE,      // Sharing credentials/attributes
    SIGN,       // Document signing
    VERIFY      // Identity verification
}

/**
 * Transaction status for the timeline item.
 */
enum class TransactionStatus {
    COMPLETED,
    FAILED
}

/**
 * Configuration data for a transaction card.
 */
data class TransactionCardConfig(
    val id: String,
    val title: String,
    val verifierName: String,
    val timestamp: String,
    val description: String?,
    val type: TransactionType,
    val status: TransactionStatus,
    val statusLabel: String,
    val isExpanded: Boolean = false
)

/**
 * A timeline item that combines the timeline visualization with a transaction card.
 * Features a dot connected to a vertical line, with the card content beside it.
 */
@Composable
fun TimelineItem(
    modifier: Modifier = Modifier,
    config: TransactionCardConfig,
    showTimelineLine: Boolean = true,
    isLastItem: Boolean = false,
    animationDelay: Int = 0,
    onClick: () -> Unit,
    onExpandToggle: (() -> Unit)? = null
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Entrance animation state
    var isVisible by remember { mutableStateOf(animationDelay == 0) }
    LaunchedEffect(Unit) {
        if (animationDelay > 0) {
            delay(animationDelay.toLong())
            isVisible = true
        }
    }

    // Scale animation for press effect
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "transaction_card_scale"
    )

    // Accent color based on transaction type
    val accentColor = when (config.type) {
        TransactionType.SHARE -> MaterialTheme.colorScheme.primary
        TransactionType.SIGN -> MaterialTheme.colorScheme.tertiary
        TransactionType.VERIFY -> MaterialTheme.colorScheme.secondary
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300)) + slideInVertically(
            animationSpec = tween(300),
            initialOffsetY = { it / 4 }
        )
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Timeline column (dot + line)
            if (showTimelineLine) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(32.dp)
                ) {
                    // Timeline dot with glow effect
                    Box(
                        modifier = Modifier.size(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Glow background
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.3f))
                        )
                        // Dot
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                    }

                    // Vertical line (not shown for last item)
                    if (!isLastItem) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .weight(1f, fill = false)
                                .height(80.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))
            }

            // Transaction card
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .scale(scale)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true),
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onClick()
                        }
                    ),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Left accent border
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(if (config.isExpanded) 120.dp else 80.dp)
                            .background(accentColor)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Header row: Type icon + Title + Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Type icon
                                val typeIcon = when (config.type) {
                                    TransactionType.SHARE -> AppIcons.IdCards
                                    TransactionType.SIGN -> AppIcons.Sign
                                    TransactionType.VERIFY -> AppIcons.Verified
                                }
                                WrapIcon(
                                    iconData = typeIcon,
                                    customTint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )

                                Text(
                                    text = config.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Status indicator
                            TransactionStatusIndicator(
                                status = config.status,
                                label = config.statusLabel
                            )
                        }

                        // Verifier + Timestamp
                        Text(
                            text = "${config.verifierName} • ${config.timestamp}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Description (if any)
                        if (!config.description.isNullOrBlank()) {
                            Text(
                                text = config.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (config.isExpanded) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A compact status indicator for transaction cards.
 */
@Composable
fun TransactionStatusIndicator(
    status: TransactionStatus,
    label: String,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, icon) = when (status) {
        TransactionStatus.COMPLETED -> Triple(
            MaterialTheme.colorScheme.success.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.success,
            AppIcons.Check
        )
        TransactionStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.error,
            AppIcons.Error
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        WrapIcon(
            iconData = icon,
            customTint = textColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

/**
 * A time group header for separating transactions by date.
 */
@Composable
fun TimeGroupHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Gradient line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@ThemeModePreviews
@Composable
private fun TimelineItemPreview() {
    PreviewTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            TimeGroupHeader(title = "Today")

            Spacer(modifier = Modifier.height(8.dp))

            TimelineItem(
                config = TransactionCardConfig(
                    id = "1",
                    title = "Identity Verification",
                    verifierName = "Nordic Bank",
                    timestamp = "14:32",
                    description = "3 attributes shared",
                    type = TransactionType.SHARE,
                    status = TransactionStatus.COMPLETED,
                    statusLabel = "Completed"
                ),
                showTimelineLine = true,
                isLastItem = false,
                onClick = {}
            )

            TimelineItem(
                config = TransactionCardConfig(
                    id = "2",
                    title = "Tax Declaration",
                    verifierName = "Tax Authority",
                    timestamp = "11:15",
                    description = "Digital signature",
                    type = TransactionType.SIGN,
                    status = TransactionStatus.COMPLETED,
                    statusLabel = "Completed"
                ),
                showTimelineLine = true,
                isLastItem = false,
                onClick = {}
            )

            TimelineItem(
                config = TransactionCardConfig(
                    id = "3",
                    title = "Age Check",
                    verifierName = "Online Store",
                    timestamp = "09:45",
                    description = "1 attribute shared",
                    type = TransactionType.VERIFY,
                    status = TransactionStatus.FAILED,
                    statusLabel = "Failed"
                ),
                showTimelineLine = true,
                isLastItem = true,
                onClick = {}
            )
        }
    }
}

@ThemeModePreviews
@Composable
private fun TimeGroupHeaderPreview() {
    PreviewTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TimeGroupHeader(title = "Today")
            TimeGroupHeader(title = "This Week")
            TimeGroupHeader(title = "January 2025")
        }
    }
}
