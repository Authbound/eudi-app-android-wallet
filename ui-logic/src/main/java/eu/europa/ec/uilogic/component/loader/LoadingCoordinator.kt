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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Coordinates loading state across the component hierarchy to prevent duplicate loaders.
 *
 * The coordinator ensures only one loading indicator is shown at a time by tracking
 * whether a parent component is already displaying a loader. Child components can
 * check [isParentLoading] before showing their own loaders.
 *
 * Usage:
 * ```kotlin
 * // In parent (ContentScreen):
 * val coordinator = rememberLoadingCoordinator()
 * CompositionLocalProvider(LocalLoadingCoordinator provides coordinator) {
 *     coordinator.setLoading(isLoading)
 *     // ... content
 * }
 *
 * // In child component:
 * val coordinator = LocalLoadingCoordinator.current
 * if (!coordinator.isParentLoading) {
 *     // Show child's own loader
 * }
 * ```
 */
class LoadingCoordinator {
    /**
     * Whether a parent component is currently showing a loading indicator.
     * Child components should check this before showing their own loaders.
     */
    var isParentLoading: Boolean by mutableStateOf(false)
        private set

    /**
     * The current loading configuration from the parent.
     */
    var parentConfig: LoadingConfig? by mutableStateOf(null)
        private set

    /**
     * Updates the parent loading state.
     * Called by the parent component (ContentScreen) when loading state changes.
     */
    fun setLoading(loading: Boolean, config: LoadingConfig? = null) {
        isParentLoading = loading
        parentConfig = if (loading) config else null
    }

    /**
     * Checks if a child component should show its own loader.
     * Returns true only if no parent is loading and the child has loading content.
     */
    fun shouldChildShowLoader(childIsLoading: Boolean): Boolean {
        return childIsLoading && !isParentLoading
    }
}

/**
 * CompositionLocal for accessing the LoadingCoordinator from any child component.
 *
 * Default value is a no-op coordinator that always returns false for isParentLoading,
 * ensuring backward compatibility with screens that don't use the coordinator.
 */
val LocalLoadingCoordinator = compositionLocalOf { LoadingCoordinator() }

/**
 * Creates and remembers a LoadingCoordinator instance.
 */
@Composable
fun rememberLoadingCoordinator(): LoadingCoordinator {
    return remember { LoadingCoordinator() }
}
