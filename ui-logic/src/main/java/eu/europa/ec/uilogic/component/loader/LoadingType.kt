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
 * Defines the type of loading indicator to display.
 *
 * The loading system supports multiple styles to match different UX contexts:
 * - Full-screen overlays for major operations
 * - Inline spinners for button/field-level loading
 * - Skeleton placeholders for content loading
 */
enum class LoadingType {
    /**
     * Premium full-screen overlay with gradient backdrop.
     * Use for major operations like credential issuance, authentication.
     */
    FULL_SCREEN,

    /**
     * Small inline spinner for buttons or fields.
     * Use for form submissions, quick API calls.
     */
    INLINE,

    /**
     * Content placeholder with shimmer effect.
     * Use while loading lists or cards.
     */
    SKELETON,

    /**
     * No parent loading - defer to child components.
     * Use when child components manage their own loading state.
     */
    NONE
}
