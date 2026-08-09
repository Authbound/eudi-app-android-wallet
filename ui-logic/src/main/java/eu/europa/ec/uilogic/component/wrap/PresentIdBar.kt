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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.brandNavyMedium
import eu.europa.ec.resourceslogic.theme.values.glowAccent
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL

/**
 * Shared Present ID affordance used under the hero credential card on Home and Document Details.
 * QR + NFC labels communicate that both channels open together.
 */
@Composable
fun PresentIdBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val presentIdLabel: String = stringResource(R.string.home_hero_present_id)
    val methodsLabel: String = stringResource(R.string.home_hero_present_id_methods)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "present_id_bar_scale"
    )
    val barShape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = SPACING_SMALL.dp)
            .scale(scale)
            .clip(barShape)
            .background(MaterialTheme.colorScheme.brandNavyMedium.copy(alpha = 0.30f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.glowAccent,
                shape = barShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClickLabel = presentIdLabel,
                role = Role.Button
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .semantics {
                contentDescription = "$presentIdLabel, $methodsLabel"
                role = Role.Button
            }
            .heightIn(min = 52.dp)
            .padding(horizontal = SPACING_MEDIUM.dp, vertical = SPACING_SMALL.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        WrapIcon(
            iconData = AppIcons.QR,
            customTint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = presentIdLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = methodsLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
        )
        WrapIcon(
            iconData = AppIcons.KeyboardArrowRight,
            customTint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
