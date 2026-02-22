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

import eu.europa.ec.resourceslogic.theme.ThemeManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.AppIcons.AuthboundLogoAndText
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapImage

data class AppIconAndTextData(
    val appIcon: IconDataUi = AuthboundLogoAndText,
    val appText: IconDataUi = AppIcons.AuthboundText,
)

@Composable
fun AppIconAndText(
    modifier: Modifier = Modifier,
    appIconAndTextData: AppIconAndTextData,
    iconModifier: Modifier = Modifier,
    useDarkModeAwareTint: Boolean = true
) {
    val isDarkTheme = ThemeManager.instance.set.isInDarkMode

    // Apply white tint in dark mode for better visibility on dark backgrounds
    // Using BlendMode.SrcIn to replace all non-transparent pixels with white
    val colorFilter = if (useDarkModeAwareTint && isDarkTheme) {
        ColorFilter.tint(Color.White, BlendMode.SrcIn)
    } else {
        null
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            space = SPACING_SMALL.dp,
            alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.Top
    ) {
        WrapImage(
            iconData = appIconAndTextData.appIcon,
            modifier = iconModifier,
            colorFilter = colorFilter
        )
        //WrapImage(iconData = appIconAndTextData.appText)
    }
}

@ThemeModePreviews
@Composable
private fun AppIconAndTextPreview() {
    PreviewTheme {
        AppIconAndText(
            appIconAndTextData = AppIconAndTextData(AuthboundLogoAndText, appText = AppIcons.AuthboundText)
        )
    }
}