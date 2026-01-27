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

/**
 * Configuration for the premium loading system.
 *
 * Provides flexible options for customizing loading indicators including
 * type selection, optional messages, and progress display.
 *
 * @param type The style of loading indicator to display
 * @param message Optional message to show below the spinner (e.g., "Issuing credential...")
 * @param showProgress Whether to display a progress bar
 * @param progress Current progress value between 0f and 1f (only used when showProgress is true)
 */
data class LoadingConfig(
    val type: LoadingType = LoadingType.FULL_SCREEN,
    val message: String? = null,
    val showProgress: Boolean = false,
    val progress: Float = 0f
) {
    companion object {
        /**
         * Creates a simple full-screen loading config without message.
         */
        fun fullScreen(): LoadingConfig = LoadingConfig(type = LoadingType.FULL_SCREEN)

        /**
         * Creates a full-screen loading config with a message.
         */
        fun fullScreenWithMessage(message: String): LoadingConfig = LoadingConfig(
            type = LoadingType.FULL_SCREEN,
            message = message
        )

        /**
         * Creates a full-screen loading config with progress indicator.
         */
        fun fullScreenWithProgress(
            message: String? = null,
            progress: Float = 0f
        ): LoadingConfig = LoadingConfig(
            type = LoadingType.FULL_SCREEN,
            message = message,
            showProgress = true,
            progress = progress.coerceIn(0f, 1f)
        )

        /**
         * Creates an inline loading config for buttons/fields.
         */
        fun inline(): LoadingConfig = LoadingConfig(type = LoadingType.INLINE)
    }
}
