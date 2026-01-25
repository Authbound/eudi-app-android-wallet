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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE

@Composable
fun WrapPinKeypad(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyShape: Shape = CircleShape,
    keySpacing: Dp = 16.dp,
    maxKeySize: Dp = 76.dp,
    leadingIconData: IconDataUi? = null,
    onLeadingPressed: (() -> Unit)? = null,
    onDigitPressed: (Int) -> Unit,
    onBackspacePressed: () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Compute a real key size from available width (full-width keypad).
        val computedKeySize = (maxWidth - (keySpacing * 2)) / 3
        val keySize = if (computedKeySize < maxKeySize) computedKeySize else maxKeySize

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = SPACING_LARGE.dp),
            verticalArrangement = Arrangement.spacedBy(keySpacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            KeypadRow(
                keySize = keySize,
                keyShape = keyShape,
                keySpacing = keySpacing,
                enabled = enabled,
                keys = listOf(1, 2, 3),
                onDigitPressed = onDigitPressed
            )
            KeypadRow(
                keySize = keySize,
                keyShape = keyShape,
                keySpacing = keySpacing,
                enabled = enabled,
                keys = listOf(4, 5, 6),
                onDigitPressed = onDigitPressed
            )
            KeypadRow(
                keySize = keySize,
                keyShape = keyShape,
                keySpacing = keySpacing,
                enabled = enabled,
                keys = listOf(7, 8, 9),
                onDigitPressed = onDigitPressed
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(keySpacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIconData != null && onLeadingPressed != null) {
                    ModernKeyButton(
                        modifier = Modifier.size(keySize),
                        keyShape = keyShape,
                        enabled = enabled,
                        onClick = onLeadingPressed,
                        content = {
                            WrapIcon(iconData = leadingIconData)
                        }
                    )
                } else {
                    Spacer(modifier = Modifier.size(keySize))
                }

                ModernKeyButton(
                    modifier = Modifier.size(keySize),
                    keyShape = keyShape,
                    enabled = enabled,
                    label = "0",
                    onClick = { onDigitPressed(0) }
                )

                ModernKeyButton(
                    modifier = Modifier.size(keySize),
                    keyShape = keyShape,
                    enabled = enabled,
                    onClick = onBackspacePressed,
                    content = {
                        WrapIcon(
                            iconData = AppIcons.ArrowBack,
                            customTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun KeypadRow(
    keySize: Dp,
    keyShape: Shape,
    keySpacing: Dp,
    enabled: Boolean,
    keys: List<Int>,
    onDigitPressed: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(keySpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { digit ->
            ModernKeyButton(
                modifier = Modifier.size(keySize),
                keyShape = keyShape,
                enabled = enabled,
                label = digit.toString(),
                onClick = { onDigitPressed(digit) }
            )
        }
    }
}

@Composable
private fun ModernKeyButton(
    modifier: Modifier,
    keyShape: Shape,
    enabled: Boolean,
    label: String? = null,
    onClick: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Subtle scale animation on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "keyScale"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .clip(keyShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
            indication = ripple(
                    bounded = true,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (content != null) {
            content()
        } else if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@ThemeModePreviews
@Composable
private fun PreviewWrapPinKeypad() {
    PreviewTheme {
        WrapPinKeypad(
            onDigitPressed = {},
            onBackspacePressed = {}
        )
    }
}
