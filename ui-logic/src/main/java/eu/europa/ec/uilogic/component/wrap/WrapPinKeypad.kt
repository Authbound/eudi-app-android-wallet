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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE

@Composable
fun WrapPinKeypad(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyShape: Shape = MaterialTheme.shapes.extraLarge,
    keySpacing: Dp = 12.dp,
    maxKeySize: Dp = 82.dp,
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
                    WrapButton(
                        modifier = Modifier.size(keySize),
                        buttonConfig = ButtonConfig(
                            type = ButtonType.SECONDARY,
                            enabled = enabled,
                            onClick = onLeadingPressed,
                            shape = keyShape,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            WrapIcon(iconData = leadingIconData)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(keySize))
                }

                KeyButton(
                    modifier = Modifier.size(keySize),
                    keyShape = keyShape,
                    enabled = enabled,
                    label = "0",
                    onClick = { onDigitPressed(0) }
                )

                WrapButton(
                    modifier = Modifier.size(keySize),
                    buttonConfig = ButtonConfig(
                        type = ButtonType.SECONDARY,
                        enabled = enabled,
                        onClick = onBackspacePressed,
                        shape = keyShape,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        WrapIcon(iconData = AppIcons.ArrowBack)
                    }
                }
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
            KeyButton(
                modifier = Modifier
                    .size(keySize),
                keyShape = keyShape,
                enabled = enabled,
                label = digit.toString(),
                onClick = { onDigitPressed(digit) }
            )
        }
    }
}

@Composable
private fun KeyButton(
    modifier: Modifier,
    keyShape: Shape,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    WrapButton(
        modifier = modifier,
        buttonConfig = ButtonConfig(
            type = ButtonType.SECONDARY,
            enabled = enabled,
            onClick = onClick,
            shape = keyShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
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


