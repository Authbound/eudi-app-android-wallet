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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ImageOrPlaceholder

/**
 * Shared building blocks for identity-document style cards: the hero credential card, the
 * document details card, and the presentation pass all compose these so the "official
 * document" visual language cannot drift between screens.
 */

/** Identity document types that carry a holder portrait window (real photo or ghost). */
fun CredentialVisualType.isIdentityDocumentType(): Boolean =
        this == CredentialVisualType.PID ||
                this == CredentialVisualType.MDL ||
                this == CredentialVisualType.AUTHBOUND

/**
 * Recessed photo window echoing a physical ID: darker than the card substrate with a quiet
 * neutral border. Without a portrait it shows a faint outlined "ghost image" silhouette (a
 * real-document security cue) instead of a bright placeholder graphic.
 */
@Composable
fun IdentityPortraitFrame(
        portraitBase64: String?,
        modifier: Modifier = Modifier,
        width: Dp = 72.dp,
        height: Dp = 92.dp,
        frameTint: Color = Color.White
) {
        Box(
                modifier =
                        modifier.width(width)
                                .height(height)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.20f))
                                .border(
                                        width = 1.dp,
                                        color = frameTint.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(8.dp)
                                ),
                contentAlignment = Alignment.Center
        ) {
                if (!portraitBase64.isNullOrBlank()) {
                        ImageOrPlaceholder(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(2.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                base64Image = portraitBase64,
                                contentScale = ContentScale.Crop,
                                fallbackIcon = AppIcons.User
                        )
                } else {
                        GhostPortraitSilhouette(
                                modifier = Modifier.fillMaxSize(),
                                color = frameTint.copy(alpha = 0.22f)
                        )
                }
        }
}

/** Faint outlined head-and-shoulders silhouette drawn like the ghost image on physical IDs. */
@Composable
private fun GhostPortraitSilhouette(
        modifier: Modifier = Modifier,
        color: Color
) {
        Canvas(modifier = modifier) {
                val stroke = Stroke(width = 1.2.dp.toPx())
                // Head: outlined circle in the upper half of the window.
                drawCircle(
                        color = color,
                        radius = size.width * 0.20f,
                        center = Offset(size.width * 0.5f, size.height * 0.34f),
                        style = stroke
                )
                // Shoulders: upper half of an ellipse rising from the bottom edge.
                drawArc(
                        color = color,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.12f, size.height * 0.62f),
                        size = Size(size.width * 0.76f, size.height * 0.66f),
                        style = stroke
                )
        }
}

/** Small letterspaced-caps label above its value; hidden entirely when the value is blank. */
@Composable
fun IdentityLabeledField(
        label: String,
        value: String?,
        modifier: Modifier = Modifier,
        labelColor: Color = Color.White.copy(alpha = 0.56f),
        valueColor: Color = Color.White
) {
        if (value.isNullOrBlank()) return
        Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
                Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp,
                        color = labelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
                Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = valueColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
        }
}

/** Uppercase tracked holder-name treatment shared by all identity-document cards. */
@Composable
fun IdentityHolderName(
        name: String,
        modifier: Modifier = Modifier,
        color: Color = Color.White,
        maxLines: Int = 2
) {
        Text(
                text = name.uppercase(),
                style =
                        MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                lineHeight = 23.sp,
                                letterSpacing = 1.sp
                        ),
                color = color,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
        )
}

/** Small round status dot used beside status labels on credential cards and meta rows. */
@Composable
fun IdentityStatusDot(
        color: Color,
        modifier: Modifier = Modifier
) {
        Box(
                modifier =
                        modifier.size(5.dp)
                                .clip(CircleShape)
                                .background(color)
        )
}

/** Hairline rule used between identity fields on document cards. */
@Composable
fun IdentityHairline(
        modifier: Modifier = Modifier,
        color: Color = Color.White.copy(alpha = 0.14f)
) {
        Box(
                modifier =
                        modifier.fillMaxWidth()
                                .height(1.dp)
                                .background(color)
        )
}
