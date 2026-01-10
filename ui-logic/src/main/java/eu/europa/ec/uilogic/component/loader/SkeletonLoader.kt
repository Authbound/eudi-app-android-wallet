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

package eu.europa.ec.uilogic.component.loader

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews

/**
 * A shimmer effect brush that creates a loading animation.
 * The shimmer moves from left to right continuously.
 *
 * @param shimmerColors The colors used for the shimmer gradient
 * @param durationMillis The duration of one complete shimmer cycle
 */
@Composable
fun shimmerBrush(
    shimmerColors: List<Color> = listOf(
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
    ),
    durationMillis: Int = 1000
): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = durationMillis,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnimation - 200f, 0f),
        end = Offset(translateAnimation + 200f, 0f)
    )
}

/**
 * A skeleton placeholder box with shimmer animation.
 *
 * @param modifier The modifier for the skeleton box
 * @param height The height of the skeleton
 * @param cornerRadius The corner radius of the skeleton
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    cornerRadius: Dp = 4.dp
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(shimmerBrush())
    )
}

/**
 * A circular skeleton placeholder with shimmer animation.
 *
 * @param modifier The modifier for the skeleton circle
 * @param size The diameter of the circle
 */
@Composable
fun SkeletonCircle(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(shimmerBrush())
    )
}

/**
 * A skeleton card that mimics the DocumentCard loading state.
 * Shows a placeholder with icon, title, subtitle, and status badge areas.
 */
@Composable
fun SkeletonDocumentCard(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon placeholder
            SkeletonCircle(size = 48.dp)

            Spacer(modifier = Modifier.width(16.dp))

            // Text content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Issuer name (overline)
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.4f),
                    height = 12.dp
                )
                // Document title
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.7f),
                    height = 18.dp
                )
                // Validity info
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.5f),
                    height = 14.dp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Status badge placeholder
            SkeletonBox(
                modifier = Modifier.width(60.dp),
                height = 24.dp,
                cornerRadius = 12.dp
            )
        }
    }
}

/**
 * A skeleton card that mimics the TransactionCard loading state.
 * Shows a placeholder with timeline dot, title, timestamp, and card content.
 */
@Composable
fun SkeletonTransactionCard(
    modifier: Modifier = Modifier,
    showTimelineDot: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        if (showTimelineDot) {
            // Timeline dot placeholder
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SkeletonCircle(size = 12.dp)
                Spacer(modifier = Modifier.height(4.dp))
                // Timeline line
                SkeletonBox(
                    modifier = Modifier.width(2.dp),
                    height = 80.dp,
                    cornerRadius = 1.dp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))
        }

        // Transaction card content
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Transaction title
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.6f),
                    height = 16.dp
                )
                // Verifier/timestamp
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.4f),
                    height = 12.dp
                )
                // Description
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.8f),
                    height = 14.dp
                )
            }
        }
    }
}

/**
 * A list of skeleton document cards for loading states.
 *
 * @param count The number of skeleton cards to display
 * @param modifier The modifier for the column container
 */
@Composable
fun SkeletonDocumentList(
    count: Int = 3,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(count) {
            SkeletonDocumentCard()
        }
    }
}

/**
 * A list of skeleton transaction cards for loading states.
 *
 * @param count The number of skeleton cards to display
 * @param modifier The modifier for the column container
 */
@Composable
fun SkeletonTransactionList(
    count: Int = 3,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(count) { index ->
            SkeletonTransactionCard(
                showTimelineDot = true
            )
        }
    }
}

@ThemeModePreviews
@Composable
private fun SkeletonDocumentCardPreview() {
    PreviewTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SkeletonDocumentCard()
            SkeletonDocumentCard()
        }
    }
}

@ThemeModePreviews
@Composable
private fun SkeletonTransactionCardPreview() {
    PreviewTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonTransactionCard()
            SkeletonTransactionCard()
        }
    }
}
