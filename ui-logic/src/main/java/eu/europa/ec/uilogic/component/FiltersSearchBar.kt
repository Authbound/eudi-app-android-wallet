/*
 * Copyright (c) 2023 European Commission
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

package eu.europa.ec.uilogic.component

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapIconButton

@Composable
fun FiltersSearchBar(
    text: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    onClearClick: () -> Unit,
    isFilteringActive: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val hairlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = text,
            maxLines = 1,
            singleLine = true,
            onValueChange = {
                onValueChange(it)
            },
            placeholder = {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            },
            leadingIcon = {
                WrapIcon(
                    iconData = AppIcons.Search,
                    customTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            },
            trailingIcon = if (text.isNotEmpty()) {
                {
                    WrapIconButton(
                        iconData = AppIcons.Close,
                        customTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            onClearClick()
                            focusManager.clearFocus()
                        }
                    )
                }
            } else null,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = hairlineColor,
                focusedBorderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )

        FilterButton(
            isFilteringActive = isFilteringActive,
            onClick = onFilterClick,
            modifier = Modifier.padding(start = SPACING_SMALL.dp)
        )
    }
}

@Composable
private fun FilterButton(
    isFilteringActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "filter_button_scale"
    )
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .scale(scale)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(18.dp)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple()
                ) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            WrapIcon(
                iconData = AppIcons.Filters,
                customTint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isFilteringActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(7.dp)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape)
            )
        }
    }
}

@Composable
@ThemeModePreviews
private fun FiltersSearchBarPreview() {
    PreviewTheme {
        FiltersSearchBar(
            text = "",
            placeholder = "Search documents",
            onValueChange = { },
            onFilterClick = {},
            onClearClick = {},
            isFilteringActive = true,
        )
    }
}
