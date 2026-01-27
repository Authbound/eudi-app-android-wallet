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

package eu.europa.ec.uilogic.component.loader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import eu.europa.ec.uilogic.component.utils.Z_LOADING
import eu.europa.ec.uilogic.extension.clickableNoRipple

/**
 * Basic loading indicator with Material Design CircularProgressIndicator.
 *
 * @deprecated Use [PremiumLoadingIndicator] for brand-aligned loading UX,
 * or use [LoadingConfig] with [ContentScreen] for coordinated loading.
 *
 * Migration:
 * ```kotlin
 * // Old:
 * if (isLoading) LoadingIndicator()
 *
 * // New (in ContentScreen):
 * ContentScreen(
 *     isLoading = isLoading,
 *     loadingConfig = LoadingConfig.fullScreen()
 * ) { ... }
 *
 * // Or standalone:
 * PremiumLoadingIndicator(visible = isLoading)
 * ```
 */
@Deprecated(
    message = "Use PremiumLoadingIndicator for brand-aligned loading UX",
    replaceWith = ReplaceWith(
        "PremiumLoadingIndicator(visible = true)",
        "eu.europa.ec.uilogic.component.loader.PremiumLoadingIndicator"
    )
)
@Composable
fun LoadingIndicator() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(Z_LOADING)
            .clickableNoRipple { }
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}