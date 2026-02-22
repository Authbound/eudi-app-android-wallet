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

package eu.europa.ec.uilogic.component.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.businesslogic.model.error.AppError
import eu.europa.ec.businesslogic.model.error.ErrorSeverity
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton

@Composable
internal fun ContentError(
    config: ContentErrorConfig,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ContentTitle(
            title = config.errorTitle ?: stringResource(
                id = R.string.generic_error_message
            ),
            subtitle = config.errorSubTitle ?: stringResource(
                id = R.string.generic_error_retry
            ),
            subTitleMaxLines = 10
        )

        config.errorCode?.let { code ->
            if (config.severity == ErrorSeverity.ERROR || config.severity == ErrorSeverity.CRITICAL) {
                Text(
                    text = "Error code: $code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = SPACING_EXTRA_SMALL.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        config.onSecondaryAction?.let { secondaryCallback ->
            WrapButton(
                buttonConfig = ButtonConfig(
                    type = ButtonType.SECONDARY,
                    onClick = { secondaryCallback() },
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = config.secondaryActionLabel
                        ?: stringResource(id = R.string.generic_error_button_retry)
                )
            }
        }

        config.onRetry?.let { callback ->
            WrapButton(
                buttonConfig = ButtonConfig(
                    type = ButtonType.PRIMARY,
                    onClick = {
                        callback()
                    },
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(id = R.string.generic_error_button_retry)
                )
            }
        }
    }
}

data class ContentErrorConfig(
    val errorTitle: String? = null,
    val errorSubTitle: String? = null,
    val onCancel: () -> Unit,
    val onRetry: (() -> Unit)? = null,
    // Severity-based fields (backward compatible defaults)
    val severity: ErrorSeverity = ErrorSeverity.ERROR,
    val errorCode: String? = null,
    val onSecondaryAction: (() -> Unit)? = null,
    val secondaryActionLabel: String? = null,
    val retryCountdownSeconds: Int? = null,
) {
    companion object {
        /**
         * Creates a [ContentErrorConfig] from a typed [AppError].
         * Maps error severity, error code, retry behavior, and messages automatically.
         */
        fun fromAppError(
            error: AppError,
            onCancel: () -> Unit,
            onRetry: (() -> Unit)? = null,
            onSecondaryAction: (() -> Unit)? = null,
            secondaryActionLabel: String? = null,
        ): ContentErrorConfig {
            return ContentErrorConfig(
                errorTitle = error.getErrorTitle(),
                errorSubTitle = error.getUserFriendlyMessage(),
                onCancel = onCancel,
                onRetry = if (error.isRetryable()) onRetry else null,
                severity = error.severity,
                errorCode = error.getErrorCode(),
                onSecondaryAction = onSecondaryAction,
                secondaryActionLabel = secondaryActionLabel,
                retryCountdownSeconds = error.getRetryDelaySeconds(),
            )
        }
    }
}

@ThemeModePreviews
@Composable
private fun PreviewContentErrorWithRetry() {
    PreviewTheme {
        ContentError(
            config = ContentErrorConfig(
                onCancel = {},
                onRetry = {},
            ),
            modifier = Modifier.padding(SIZE_MEDIUM.dp)
        )
    }
}

@ThemeModePreviews
@Composable
private fun PreviewContentErrorWithoutRetry() {
    PreviewTheme {
        ContentError(
            config = ContentErrorConfig(
                onCancel = {},
                onRetry = null,
            ),
            modifier = Modifier.padding(SIZE_MEDIUM.dp)
        )
    }
}