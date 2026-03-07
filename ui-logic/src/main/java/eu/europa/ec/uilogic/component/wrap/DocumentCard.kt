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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.theme.values.pending
import eu.europa.ec.resourceslogic.theme.values.success
import eu.europa.ec.resourceslogic.theme.values.warning
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import kotlinx.coroutines.delay

/** Status types for document cards with associated colors and labels. */
enum class DocumentStatus {
    ISSUED,
    PENDING,
    FAILED,
    EXPIRED,
    REVOKED
}

/** Configuration data for a document card. */
data class DocumentCardConfig(
        val id: String,
        val title: String,
        val issuerName: String?,
        val validityInfo: String?,
        val status: DocumentStatus,
        val statusLabel: String,
        val icon: IconDataUi? = null,
        val isEnabled: Boolean = true
)

/**
 * A premium document card component with status badges, animations, and haptic feedback.
 *
 * Features:
 * - Scale animation on press (0.98x)
 * - Haptic feedback on tap
 * - Status badge with semantic colors
 * - Left accent border based on status
 * - Staggered entrance animation support
 */
@Composable
fun DocumentCard(
        modifier: Modifier = Modifier,
        config: DocumentCardConfig,
        animationDelay: Int = 0,
        onClick: () -> Unit
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
    val scale by
            animateFloatAsState(
                    targetValue = if (isPressed) 0.98f else 1f,
                    animationSpec = tween(durationMillis = 100),
                    label = "document_card_scale"
            )

    // Status-based colors
    val statusColor =
            when (config.status) {
                DocumentStatus.ISSUED -> MaterialTheme.colorScheme.success
                DocumentStatus.PENDING -> MaterialTheme.colorScheme.warning
                DocumentStatus.FAILED -> MaterialTheme.colorScheme.error
                DocumentStatus.EXPIRED -> MaterialTheme.colorScheme.error
                DocumentStatus.REVOKED -> MaterialTheme.colorScheme.error
            }

    val accentColor =
            when (config.status) {
                DocumentStatus.ISSUED -> MaterialTheme.colorScheme.primary
                DocumentStatus.PENDING -> MaterialTheme.colorScheme.pending
                else -> MaterialTheme.colorScheme.error
            }

    AnimatedVisibility(
            visible = isVisible,
            enter =
                    fadeIn(tween(300)) +
                            slideInVertically(
                                    animationSpec = tween(300),
                                    initialOffsetY = { it / 4 }
                            )
    ) {
        Surface(
                modifier =
                        modifier.scale(scale)
                                .clickable(
                                        interactionSource = interactionSource,
                                        indication = ripple(bounded = true),
                                        enabled = config.isEnabled,
                                        onClick = {
                                            view.performHapticFeedback(
                                                    HapticFeedbackConstants.VIRTUAL_KEY
                                            )
                                            onClick()
                                        }
                                ),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                shadowElevation = 4.dp
        ) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                // Left accent bar
                Box(modifier = Modifier.width(4.dp).height(80.dp).background(accentColor))

                Row(
                        modifier = Modifier.weight(1f).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // Document icon
                    if (config.icon != null) {
                        Surface(
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                WrapIcon(
                                        iconData = config.icon,
                                        customTint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    // Text content
                    Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Issuer name (overline)
                        if (!config.issuerName.isNullOrBlank()) {
                            Text(
                                    text = config.issuerName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Document title
                        Text(
                                text = config.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )

                        // Validity info
                        if (!config.validityInfo.isNullOrBlank()) {
                            Text(
                                    text = config.validityInfo,
                                    style = MaterialTheme.typography.bodySmall,
                                    color =
                                            if (config.status == DocumentStatus.ISSUED) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                statusColor
                                            },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Status badge
                    StatusBadge(status = config.status, label = config.statusLabel)
                }
            }
        }
    }
}

/** A status badge pill showing the document's current state. */
@Composable
fun StatusBadge(status: DocumentStatus, label: String, modifier: Modifier = Modifier) {
    val backgroundColor =
            when (status) {
                DocumentStatus.ISSUED -> MaterialTheme.colorScheme.success.copy(alpha = 0.15f)
                DocumentStatus.PENDING -> MaterialTheme.colorScheme.warning.copy(alpha = 0.15f)
                DocumentStatus.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                DocumentStatus.EXPIRED -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                DocumentStatus.REVOKED -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
            }

    val textColor =
            when (status) {
                DocumentStatus.ISSUED -> MaterialTheme.colorScheme.success
                DocumentStatus.PENDING -> MaterialTheme.colorScheme.warning
                DocumentStatus.FAILED -> MaterialTheme.colorScheme.error
                DocumentStatus.EXPIRED -> MaterialTheme.colorScheme.error
                DocumentStatus.REVOKED -> MaterialTheme.colorScheme.error
            }

    val icon =
            when (status) {
                DocumentStatus.ISSUED -> AppIcons.Verified
                DocumentStatus.PENDING -> AppIcons.ClockTimer
                DocumentStatus.FAILED -> AppIcons.Error
                DocumentStatus.EXPIRED -> AppIcons.Error
                DocumentStatus.REVOKED -> AppIcons.Error
            }

    Surface(modifier = modifier, shape = RoundedCornerShape(100.dp), color = backgroundColor) {
        Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WrapIcon(iconData = icon, customTint = textColor, modifier = Modifier.size(14.dp))
            Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = textColor
            )
        }
    }
}

/**
 * A modern category header for grouping documents. Features icon-forward design with sentence case
 * typography.
 */
@Composable
fun DocumentCategoryHeader(
        title: String,
        icon: IconDataUi? = null,
        documentCount: Int? = null,
        modifier: Modifier = Modifier
) {
    Row(
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            WrapIcon(
                    iconData = icon,
                    customTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
            )
        }

        // Title with sentence case (more modern, easier to read)
        Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
        )

        // Optional document count badge
        if (documentCount != null && documentCount > 0) {
            Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                        text = documentCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@ThemeModePreviews
@Composable
private fun DocumentCardPreview() {
    PreviewTheme {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DocumentCategoryHeader(title = "Government", icon = AppIcons.IdCards, documentCount = 3)

            DocumentCard(
                    config =
                            DocumentCardConfig(
                                    id = "1",
                                    title = "National ID Card",
                                    issuerName = "Government Authority",
                                    validityInfo = "Valid until Dec 2028",
                                    status = DocumentStatus.ISSUED,
                                    statusLabel = "Issued",
                                    icon = AppIcons.IdCards
                            ),
                    onClick = {}
            )

            DocumentCard(
                    config =
                            DocumentCardConfig(
                                    id = "2",
                                    title = "Mobile Driving License",
                                    issuerName = "Transport Authority",
                                    validityInfo = "Awaiting verification...",
                                    status = DocumentStatus.PENDING,
                                    statusLabel = "Pending",
                                    icon = AppIcons.IdCards
                            ),
                    onClick = {}
            )

            DocumentCard(
                    config =
                            DocumentCardConfig(
                                    id = "3",
                                    title = "Tax Certificate",
                                    issuerName = "Tax Authority",
                                    validityInfo = "Expired on Jan 2024",
                                    status = DocumentStatus.EXPIRED,
                                    statusLabel = "Expired",
                                    icon = AppIcons.IdCards
                            ),
                    onClick = {}
            )
        }
    }
}

@ThemeModePreviews
@Composable
private fun StatusBadgePreview() {
    PreviewTheme {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusBadge(status = DocumentStatus.ISSUED, label = "Issued")
            StatusBadge(status = DocumentStatus.PENDING, label = "Pending")
            StatusBadge(status = DocumentStatus.FAILED, label = "Failed")
            StatusBadge(status = DocumentStatus.EXPIRED, label = "Expired")
            StatusBadge(status = DocumentStatus.REVOKED, label = "Revoked")
        }
    }
}
