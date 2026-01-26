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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import kotlinx.coroutines.delay

/**
 * Types of actionable requests.
 */
enum class ActionType {
    SIGN_REQUEST,
    VERIFY_REQUEST,
    DATA_REQUEST
}

/**
 * Status of an action request.
 */
enum class ActionStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED
}

/**
 * Configuration for an action card.
 */
data class InboxActionCardConfig(
    val id: String,
    val type: ActionType,
    val title: String,
    val requesterName: String,
    val relativeTime: String,
    val description: String?,
    val status: ActionStatus,
    val isActionable: Boolean = true
)

/**
 * A premium action card for the Actions inbox.
 * Shows actionable requests with Accept/Decline buttons.
 */
@Composable
fun ActionCard(
    config: InboxActionCardConfig,
    modifier: Modifier = Modifier,
    animationDelay: Int = 0,
    enableAnimations: Boolean = true,
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale: Float = if (enableAnimations) {
        val animatedScale by animateFloatAsState(
            targetValue = if (isPressed) 0.98f else 1f,
            animationSpec = tween(durationMillis = 100),
            label = "action_card_scale"
        )
        animatedScale
    } else {
        1f
    }
    // Type-based accent color
    val accentColor = when (config.type) {
        ActionType.SIGN_REQUEST -> MaterialTheme.colorScheme.tertiary
        ActionType.VERIFY_REQUEST -> MaterialTheme.colorScheme.primary
        ActionType.DATA_REQUEST -> MaterialTheme.colorScheme.secondary
    }

    // Type icon
    val typeIcon = when (config.type) {
        ActionType.SIGN_REQUEST -> AppIcons.Sign
        ActionType.VERIFY_REQUEST -> AppIcons.Verified
        ActionType.DATA_REQUEST -> AppIcons.IdCards
    }

    val content: @Composable () -> Unit = {
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
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Left accent border
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(if (config.isActionable) 160.dp else 80.dp)
                        .background(accentColor)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header: Type icon + Title + Time
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
                            // Type icon with background
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accentColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                WrapIcon(
                                    iconData = typeIcon,
                                    customTint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = config.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Relative time
                        Text(
                            text = config.relativeTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Requester name
                    Text(
                        text = config.requesterName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Description
                    if (!config.description.isNullOrBlank()) {
                        Text(
                            text = config.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Action buttons (only for pending/actionable)
                    if (config.isActionable && config.status == ActionStatus.PENDING) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Decline button
                            OutlinedButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onDecline()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Decline")
                            }

                            // Accept button
                            Button(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onAccept()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor
                                )
                            ) {
                                Text(
                                    text = when (config.type) {
                                        ActionType.SIGN_REQUEST -> "Sign"
                                        ActionType.VERIFY_REQUEST -> "Verify"
                                        ActionType.DATA_REQUEST -> "Share"
                                    }
                                )
                            }
                        }
                    } else if (config.status != ActionStatus.PENDING) {
                        // Show status for completed actions
                        ActionStatusBadge(status = config.status)
                    }
                }
            }
        }
    }
    if (enableAnimations) {
        var isVisible by remember { mutableStateOf(animationDelay == 0) }
        LaunchedEffect(Unit) {
            if (animationDelay > 0) {
                delay(animationDelay.toLong())
                isVisible = true
            }
        }
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(300)) + slideInVertically(
                animationSpec = tween(300),
                initialOffsetY = { it / 4 }
            )
        ) {
            content()
        }
    } else {
        content()
    }
}

/**
 * Status badge for completed actions.
 */
@Composable
private fun ActionStatusBadge(
    status: ActionStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, label) = when (status) {
        ActionStatus.ACCEPTED -> Triple(
            Color(0xFF4CAF50).copy(alpha = 0.15f),
            Color(0xFF4CAF50),
            "Accepted"
        )
        ActionStatus.DECLINED -> Triple(
            MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.error,
            "Declined"
        )
        ActionStatus.EXPIRED -> Triple(
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Expired"
        )
        ActionStatus.PENDING -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.primary,
            "Pending"
        )
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
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/**
 * Section header for grouping actions.
 */
@Composable
fun ActionSectionHeader(
    title: String,
    count: Int? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (count != null && count > 0) {
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@ThemeModePreviews
@Composable
private fun ActionCardPreview() {
    PreviewTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionSectionHeader(title = "PENDING", count = 2)

            ActionCard(
                config = InboxActionCardConfig(
                    id = "1",
                    type = ActionType.SIGN_REQUEST,
                    title = "Sign Request",
                    requesterName = "Nordic Bank",
                    relativeTime = "2h ago",
                    description = "Request to digitally sign your apartment lease agreement",
                    status = ActionStatus.PENDING,
                    isActionable = true
                )
            )

            ActionCard(
                config = InboxActionCardConfig(
                    id = "2",
                    type = ActionType.VERIFY_REQUEST,
                    title = "Identity Verification",
                    requesterName = "Tax Authority",
                    relativeTime = "5h ago",
                    description = "Verify your identity for annual tax filing",
                    status = ActionStatus.PENDING,
                    isActionable = true
                )
            )

            ActionSectionHeader(title = "COMPLETED")

            ActionCard(
                config = InboxActionCardConfig(
                    id = "3",
                    type = ActionType.DATA_REQUEST,
                    title = "Age Verification",
                    requesterName = "Online Store",
                    relativeTime = "Yesterday",
                    description = null,
                    status = ActionStatus.ACCEPTED,
                    isActionable = false
                )
            )
        }
    }
}
