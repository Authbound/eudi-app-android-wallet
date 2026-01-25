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

package eu.europa.ec.resourceslogic.theme.values

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import eu.europa.ec.resourceslogic.theme.ThemeManager
import eu.europa.ec.resourceslogic.theme.templates.ThemeColorsTemplate

private val isInDarkMode: Boolean
    get() {
        return ThemeManager.instance.set.isInDarkMode
    }

class ThemeColors {
    companion object {
        private const val white: Long = 0xFFFFFFFF
        private const val black: Long = 0xFF000000

        // Light theme base colors palette - Updated to match Authbound global.css colors
        // HSL: --primary: 220 86% 11% -> #0A1A36
        private const val eudiw_theme_light_primary: Long = 0xFF0A1A36
        private const val eudiw_theme_light_onPrimary: Long = white
        // HSL: --primary-foreground: 210 36% 96% -> #F1F5FB
        private const val eudiw_theme_light_primaryContainer: Long = 0xFFF1F5FB
        private const val eudiw_theme_light_onPrimaryContainer: Long = 0xFF0A1A36
        
        // HSL: --secondary: 222 17% 44% -> #5F6A85
        private const val eudiw_theme_light_secondary: Long = 0xFF5F6A85
        // HSL: --secondary-foreground: 210 36% 96% -> #F1F5FB
        private const val eudiw_theme_light_onSecondary: Long = 0xFFF1F5FB
        private const val eudiw_theme_light_secondaryContainer: Long = 0xFFEBEFF7
        private const val eudiw_theme_light_onSecondaryContainer: Long = 0xFF5F6A85
        
        // HSL: --accent: 217 91% 60% -> #3B82F6
        private const val eudiw_theme_light_tertiary: Long = 0xFF3B82F6
        // HSL: --accent-foreground: 210 36% 96% -> #F1F5FB
        private const val eudiw_theme_light_onTertiary: Long = 0xFFF1F5FB
        private const val eudiw_theme_light_tertiaryContainer: Long = 0xFFDCEAFF
        private const val eudiw_theme_light_onTertiaryContainer: Long = 0xFF0A1A36
        
        // HSL: --destructive: 0 79% 50% -> #F03030
        private const val eudiw_theme_light_error: Long = 0xFFF03030
        private const val eudiw_theme_light_onError: Long = white
        private const val eudiw_theme_light_errorContainer: Long = 0xFFFFDADA
        // HSL: --destructive-foreground: 210 36% 96% -> #F1F5FB
        private const val eudiw_theme_light_onErrorContainer: Long = 0xFF7A0000
        
        // HSL: --background: 48 100% 99% -> #FFFCF5
        private const val eudiw_theme_light_surface: Long = 0xFFFFFCF6
        // HSL: --foreground: 220 86% 11% -> #0A1A36
        private const val eudiw_theme_light_onSurface: Long = 0xFF0A1A36
        private const val eudiw_theme_light_background: Long = eudiw_theme_light_surface
        private const val eudiw_theme_light_onBackground: Long = eudiw_theme_light_onSurface
        
        // HSL: --card: 48 100% 99% -> #FFFCF5
        private const val eudiw_theme_light_surfaceVariant: Long = 0xFFFFFCF5
        // HSL: --card-foreground: 220 86% 11% -> #0A1A36
        private const val eudiw_theme_light_onSurfaceVariant: Long = 0xFF0A1A36
        
        // HSL: --border: 222 17% 44% -> #5F6A85
        private const val eudiw_theme_light_outline: Long = 0xFF5F6A85
        // HSL: --input: 222 17% 44% -> #5F6A85
        private const val eudiw_theme_light_outlineVariant: Long = 0xFF5F6A85
        
        private const val eudiw_theme_light_scrim: Long = black
        private const val eudiw_theme_light_inverseSurface: Long = 0xFF0A1A36
        private const val eudiw_theme_light_inverseOnSurface: Long = 0xFFFFFCF5
        // HSL: --ring: 217 91% 60% -> #3B82F6
        private const val eudiw_theme_light_inversePrimary: Long = 0xFF3B82F6
        
        private const val eudiw_theme_light_surfaceDim: Long = 0xFFF5F2EB
        private const val eudiw_theme_light_surfaceBright: Long = 0xFFFFFCF5
        private const val eudiw_theme_light_surfaceContainerLowest: Long = white
        private const val eudiw_theme_light_surfaceContainerLow: Long = 0xFFFFFCF5
        private const val eudiw_theme_light_surfaceContainer: Long = 0xFFFAF7F0
        private const val eudiw_theme_light_surfaceContainerHigh: Long = 0xFFF5F2EB
        private const val eudiw_theme_light_surfaceContainerHighest: Long = 0xFFF0EDE6
        private const val eudiw_theme_light_surfaceTint: Long = eudiw_theme_light_surface

        // Light theme fixed accent roles (identical in dark as well).
        private const val eudiw_theme_light_primaryFixed: Long = 0xFFD4DEF7
        private const val eudiw_theme_light_primaryFixedDim: Long = 0xFFA8BEF0
        private const val eudiw_theme_light_onPrimaryFixed: Long = 0xFF08122B
        private const val eudiw_theme_light_onPrimaryFixedVariant: Long = 0xFF173782

        private const val eudiw_theme_light_secondaryFixed: Long = 0xFFD3D6F8
        private const val eudiw_theme_light_secondaryFixedDim: Long = 0xFFA6ADF2
        private const val eudiw_theme_light_onSecondaryFixed: Long = 0xFF070A2C
        private const val eudiw_theme_light_onSecondaryFixedVariant: Long = 0xFF141D85

        private const val eudiw_theme_light_tertiaryFixed: Long = 0xFFE0EBE3
        private const val eudiw_theme_light_tertiaryFixedDim: Long = 0xFFC0D8C7
        private const val eudiw_theme_light_onTertiaryFixed: Long = 0xFF141F17
        private const val eudiw_theme_light_onTertiaryFixedVariant: Long = 0xFF3B5E46

        // Light theme extra colors palette.
        // Using a green color for success
        internal const val eudiw_theme_light_success: Long = 0xFF22C55E
        // HSL: --muted: 222 17% 44% -> #5F6A85
        internal const val eudiw_theme_light_warning: Long = 0xFFF59E0B
        // HSL: --accent: 217 91% 60% -> #3B82F6
        internal const val eudiw_theme_light_pending: Long = 0xFF3B82F6
        // HSL: --border: 222 17% 44% -> #5F6A85
        internal const val eudiw_theme_light_divider: Long = 0xFF5F6A85

        // Active/selection highlight color - separate from primary for dark theme contrast
        // Light: Navy (matches primary) | Dark: Off-white (high contrast)
        internal const val eudiw_theme_light_activeHighlight: Long = 0xFF0A1A36
        internal const val eudiw_theme_light_onActiveHighlight: Long = 0xFFFFFFFF

        // Premium effect colors (light mode) - Authbound brand
        internal const val eudiw_theme_light_shadow_navy: Long = 0x2E0A1A36      // 18% opacity navy shadow
        internal const val eudiw_theme_light_glow_accent: Long = 0x403B82F6      // 25% opacity accent glow
        internal const val eudiw_theme_light_glow_primary: Long = 0x330A1A36     // 20% opacity primary glow

        // Dark theme base colors palette - Updated to match Authbound global.css dark mode colors
        // HSL: --primary: 217 91% 60% -> #3B82F6 (dark mode primary)
        private const val eudiw_theme_dark_primary: Long = 0xFF3B82F6
        // HSL: --primary-foreground: 210 36% 96% -> #F1F5FB
        private const val eudiw_theme_dark_onPrimary: Long = 0xFFF1F5FB
        private const val eudiw_theme_dark_primaryContainer: Long = 0xFF1D4ED8
        private const val eudiw_theme_dark_onPrimaryContainer: Long = 0xFFDCEAFF
        
        // HSL: --secondary: 222 17% 44% -> #5F6A85
        private const val eudiw_theme_dark_secondary: Long = 0xFF5F6A85
        // HSL: --secondary-foreground: 0 0% 98% -> #FAFAFA
        private const val eudiw_theme_dark_onSecondary: Long = 0xFFFAFAFA
        private const val eudiw_theme_dark_secondaryContainer: Long = 0xFF4A5573
        private const val eudiw_theme_dark_onSecondaryContainer: Long = 0xFFDCE0EB
        
        // HSL: --accent: 217 91% 60% -> #3B82F6
        private const val eudiw_theme_dark_tertiary: Long = 0xFF3B82F6
        // HSL: --accent-foreground: 0 0% 98% -> #FAFAFA
        private const val eudiw_theme_dark_onTertiary: Long = 0xFFFAFAFA
        private const val eudiw_theme_dark_tertiaryContainer: Long = 0xFF1D4ED8
        private const val eudiw_theme_dark_onTertiaryContainer: Long = 0xFFDCEAFF
        
        // HSL: --destructive: 0 62.8% 30.6% -> #9D1C1C
        private const val eudiw_theme_dark_error: Long = 0xFF9D1C1C
        // HSL: --destructive-foreground: 0 85.7% 97.3% -> #FEE2E2
        private const val eudiw_theme_dark_onError: Long = 0xFFFEE2E2
        private const val eudiw_theme_dark_errorContainer: Long = 0xFF7A0000
        private const val eudiw_theme_dark_onErrorContainer: Long = 0xFFFFDADA
        
        // HSL: --background: 220 86% 11% -> #0A1A36
        private const val eudiw_theme_dark_surface: Long = 0xFF0A1A36
        // HSL: --foreground: 0 0% 98% -> #FAFAFA
        private const val eudiw_theme_dark_onSurface: Long = 0xFFFAFAFA
        private const val eudiw_theme_dark_background: Long = eudiw_theme_dark_surface
        private const val eudiw_theme_dark_onBackground: Long = eudiw_theme_dark_onSurface
        
        // HSL: --card: 220 86% 11% -> #0A1A36
        private const val eudiw_theme_dark_surfaceVariant: Long = 0xFF0A1A36
        // HSL: --card-foreground: 0 0% 98% -> #FAFAFA
        private const val eudiw_theme_dark_onSurfaceVariant: Long = 0xFFFAFAFA
        
        // HSL: --border: 222 17% 44% -> #5F6A85
        private const val eudiw_theme_dark_outline: Long = 0xFF5F6A85
        // HSL: --input: 222 17% 44% -> #5F6A85
        private const val eudiw_theme_dark_outlineVariant: Long = 0xFF5F6A85
        
        private const val eudiw_theme_dark_scrim: Long = black
        private const val eudiw_theme_dark_inverseSurface: Long = 0xFFFFFCF5
        private const val eudiw_theme_dark_inverseOnSurface: Long = 0xFF0A1A36
        // HSL: --ring: 217 91% 60% -> #3B82F6
        private const val eudiw_theme_dark_inversePrimary: Long = 0xFF3B82F6
        
        private const val eudiw_theme_dark_surfaceDim: Long = 0xFF081429
        private const val eudiw_theme_dark_surfaceBright: Long = 0xFF0F2142
        private const val eudiw_theme_dark_surfaceContainerLowest: Long = 0xFF050E1C
        private const val eudiw_theme_dark_surfaceContainerLow: Long = 0xFF081429
        private const val eudiw_theme_dark_surfaceContainer: Long = 0xFF0A1A36
        private const val eudiw_theme_dark_surfaceContainerHigh: Long = 0xFF0F2142
        private const val eudiw_theme_dark_surfaceContainerHighest: Long = 0xFF14294F
        private const val eudiw_theme_dark_surfaceTint: Long = eudiw_theme_dark_surface

        // Dark theme fixed accent roles (same values as light).
        private const val eudiw_theme_dark_primaryFixed: Long = 0xFFD4DEF7
        private const val eudiw_theme_dark_primaryFixedDim: Long = 0xFFA8BEF0
        private const val eudiw_theme_dark_onPrimaryFixed: Long = 0xFF08122B
        private const val eudiw_theme_dark_onPrimaryFixedVariant: Long = 0xFF173782

        private const val eudiw_theme_dark_secondaryFixed: Long = 0xFFD3D6F8
        private const val eudiw_theme_dark_secondaryFixedDim: Long = 0xFFA6ADF2
        private const val eudiw_theme_dark_onSecondaryFixed: Long = 0xFF070A2C
        private const val eudiw_theme_dark_onSecondaryFixedVariant: Long = 0xFF141D85

        private const val eudiw_theme_dark_tertiaryFixed: Long = 0xFFE0EBE3
        private const val eudiw_theme_dark_tertiaryFixedDim: Long = 0xFFC0D8C7
        private const val eudiw_theme_dark_onTertiaryFixed: Long = 0xFF141F17
        private const val eudiw_theme_dark_onTertiaryFixedVariant: Long = 0xFF3B5E46

        // Dark theme extra colors palette.
        internal const val eudiw_theme_dark_success: Long = 0xFF4ADE80
        // HSL: --muted: 222 17% 44% -> #5F6A85
        internal const val eudiw_theme_dark_warning: Long = 0xFFFBBF24
        // HSL: --accent: 217 91% 60% -> #3B82F6
        internal const val eudiw_theme_dark_pending: Long = 0xFF3B82F6
        // HSL: --border: 222 17% 44% -> #5F6A85
        internal const val eudiw_theme_dark_divider: Long = 0xFF5F6A85

        // Active/selection highlight color - separate from primary for dark theme contrast
        // Light: Navy (matches primary) | Dark: Off-white (high contrast)
        internal const val eudiw_theme_dark_activeHighlight: Long = 0xFFFAFAFA
        internal const val eudiw_theme_dark_onActiveHighlight: Long = 0xFF0A1A36

        // Premium effect colors (dark mode) - Authbound brand
        internal const val eudiw_theme_dark_shadow_navy: Long = 0x1A000000       // 10% opacity black shadow
        internal const val eudiw_theme_dark_glow_accent: Long = 0x4D3B82F6       // 30% opacity accent glow
        internal const val eudiw_theme_dark_glow_primary: Long = 0x263B82F6      // 15% opacity primary glow

        const val eudiw_theme_light_background_preview: Long =
            eudiw_theme_light_surface
        const val eudiw_theme_dark_background_preview: Long =
            eudiw_theme_dark_surface

        internal val lightColors = ThemeColorsTemplate(
            primary = eudiw_theme_light_primary,
            onPrimary = eudiw_theme_light_onPrimary,
            primaryContainer = eudiw_theme_light_primaryContainer,
            onPrimaryContainer = eudiw_theme_light_onPrimaryContainer,
            secondary = eudiw_theme_light_secondary,
            onSecondary = eudiw_theme_light_onSecondary,
            secondaryContainer = eudiw_theme_light_secondaryContainer,
            onSecondaryContainer = eudiw_theme_light_onSecondaryContainer,
            tertiary = eudiw_theme_light_tertiary,
            onTertiary = eudiw_theme_light_onTertiary,
            tertiaryContainer = eudiw_theme_light_tertiaryContainer,
            onTertiaryContainer = eudiw_theme_light_onTertiaryContainer,
            error = eudiw_theme_light_error,
            errorContainer = eudiw_theme_light_errorContainer,
            onError = eudiw_theme_light_onError,
            onErrorContainer = eudiw_theme_light_onErrorContainer,
            background = eudiw_theme_light_background,
            onBackground = eudiw_theme_light_onBackground,
            surface = eudiw_theme_light_surface,
            onSurface = eudiw_theme_light_onSurface,
            surfaceVariant = eudiw_theme_light_surfaceVariant,
            onSurfaceVariant = eudiw_theme_light_onSurfaceVariant,
            outline = eudiw_theme_light_outline,
            inverseOnSurface = eudiw_theme_light_inverseOnSurface,
            inverseSurface = eudiw_theme_light_inverseSurface,
            inversePrimary = eudiw_theme_light_inversePrimary,
            surfaceTint = eudiw_theme_light_surfaceTint,
            outlineVariant = eudiw_theme_light_outlineVariant,
            scrim = eudiw_theme_light_scrim,
            surfaceBright = eudiw_theme_light_surfaceBright,
            surfaceDim = eudiw_theme_light_surfaceDim,
            surfaceContainer = eudiw_theme_light_surfaceContainer,
            surfaceContainerHigh = eudiw_theme_light_surfaceContainerHigh,
            surfaceContainerHighest = eudiw_theme_light_surfaceContainerHighest,
            surfaceContainerLow = eudiw_theme_light_surfaceContainerLow,
            surfaceContainerLowest = eudiw_theme_light_surfaceContainerLowest,
            primaryFixed = eudiw_theme_light_primaryFixed,
            primaryFixedDim = eudiw_theme_light_primaryFixedDim,
            onPrimaryFixed = eudiw_theme_light_onPrimaryFixed,
            onPrimaryFixedVariant = eudiw_theme_light_onPrimaryFixedVariant,
            secondaryFixed = eudiw_theme_light_secondaryFixed,
            secondaryFixedDim = eudiw_theme_light_secondaryFixedDim,
            onSecondaryFixed = eudiw_theme_light_onSecondaryFixed,
            onSecondaryFixedVariant = eudiw_theme_light_onSecondaryFixedVariant,
            tertiaryFixed = eudiw_theme_light_tertiaryFixed,
            tertiaryFixedDim = eudiw_theme_light_tertiaryFixedDim,
            onTertiaryFixed = eudiw_theme_light_onTertiaryFixed,
            onTertiaryFixedVariant = eudiw_theme_light_onTertiaryFixedVariant,
        )

        internal val darkColors = ThemeColorsTemplate(
            primary = eudiw_theme_dark_primary,
            onPrimary = eudiw_theme_dark_onPrimary,
            primaryContainer = eudiw_theme_dark_primaryContainer,
            onPrimaryContainer = eudiw_theme_dark_onPrimaryContainer,
            secondary = eudiw_theme_dark_secondary,
            onSecondary = eudiw_theme_dark_onSecondary,
            secondaryContainer = eudiw_theme_dark_secondaryContainer,
            onSecondaryContainer = eudiw_theme_dark_onSecondaryContainer,
            tertiary = eudiw_theme_dark_tertiary,
            onTertiary = eudiw_theme_dark_onTertiary,
            tertiaryContainer = eudiw_theme_dark_tertiaryContainer,
            onTertiaryContainer = eudiw_theme_dark_onTertiaryContainer,
            error = eudiw_theme_dark_error,
            errorContainer = eudiw_theme_dark_errorContainer,
            onError = eudiw_theme_dark_onError,
            onErrorContainer = eudiw_theme_dark_onErrorContainer,
            background = eudiw_theme_dark_background,
            onBackground = eudiw_theme_dark_onBackground,
            surface = eudiw_theme_dark_surface,
            onSurface = eudiw_theme_dark_onSurface,
            surfaceVariant = eudiw_theme_dark_surfaceVariant,
            onSurfaceVariant = eudiw_theme_dark_onSurfaceVariant,
            outline = eudiw_theme_dark_outline,
            inverseOnSurface = eudiw_theme_dark_inverseOnSurface,
            inverseSurface = eudiw_theme_dark_inverseSurface,
            inversePrimary = eudiw_theme_dark_inversePrimary,
            surfaceTint = eudiw_theme_dark_surfaceTint,
            outlineVariant = eudiw_theme_dark_outlineVariant,
            scrim = eudiw_theme_dark_scrim,
            surfaceBright = eudiw_theme_dark_surfaceBright,
            surfaceDim = eudiw_theme_dark_surfaceDim,
            surfaceContainer = eudiw_theme_dark_surfaceContainer,
            surfaceContainerHigh = eudiw_theme_dark_surfaceContainerHigh,
            surfaceContainerHighest = eudiw_theme_dark_surfaceContainerHighest,
            surfaceContainerLow = eudiw_theme_dark_surfaceContainerLow,
            surfaceContainerLowest = eudiw_theme_dark_surfaceContainerLowest,
            primaryFixed = eudiw_theme_dark_primaryFixed,
            primaryFixedDim = eudiw_theme_dark_primaryFixedDim,
            onPrimaryFixed = eudiw_theme_dark_onPrimaryFixed,
            onPrimaryFixedVariant = eudiw_theme_dark_onPrimaryFixedVariant,
            secondaryFixed = eudiw_theme_dark_secondaryFixed,
            secondaryFixedDim = eudiw_theme_dark_secondaryFixedDim,
            onSecondaryFixed = eudiw_theme_dark_onSecondaryFixed,
            onSecondaryFixedVariant = eudiw_theme_dark_onSecondaryFixedVariant,
            tertiaryFixed = eudiw_theme_dark_tertiaryFixed,
            tertiaryFixedDim = eudiw_theme_dark_tertiaryFixedDim,
            onTertiaryFixed = eudiw_theme_dark_onTertiaryFixed,
            onTertiaryFixedVariant = eudiw_theme_dark_onTertiaryFixedVariant,
        )

        val primary: Color
            get() = if (isInDarkMode) {
                Color(eudiw_theme_dark_primary)
            } else {
                Color(eudiw_theme_light_primary)
            }

        val success: Color
            get() = if (isInDarkMode) {
                Color(eudiw_theme_dark_success)
            } else {
                Color(eudiw_theme_light_success)
            }

        val warning: Color
            get() = if (isInDarkMode) {
                Color(eudiw_theme_dark_warning)
            } else {
                Color(eudiw_theme_light_warning)
            }

        val pending: Color
            get() = if (isInDarkMode) {
                Color(eudiw_theme_dark_pending)
            } else {
                Color(eudiw_theme_light_pending)
            }

        val error: Color
            get() = if (isInDarkMode) {
                Color(eudiw_theme_dark_error)
            } else {
                Color(eudiw_theme_light_error)
            }

        val divider: Color
            get() = if (isInDarkMode) {
                Color(eudiw_theme_dark_divider)
            } else {
                Color(eudiw_theme_light_divider)
            }

        // Premium effect colors - Authbound brand
        val shadowNavy: Color
            get() = if (isInDarkMode) {
                Color(eudiw_theme_dark_shadow_navy)
            } else {
                Color(eudiw_theme_light_shadow_navy)
            }

        val glowAccent: Color
            get() = if (isInDarkMode) {
                Color(eudiw_theme_dark_glow_accent)
            } else {
                Color(eudiw_theme_light_glow_accent)
            }

        val glowPrimary: Color
            get() = if (isInDarkMode) {
                Color(eudiw_theme_dark_glow_primary)
            } else {
                Color(eudiw_theme_light_glow_primary)
            }

        // Semantic color for selection/active states (nav icons, checkboxes, radio buttons)
        // Separate from primary to allow different colors in dark mode
        val activeHighlight: Color
            get() = if (isInDarkMode) {
                Color(eudiw_theme_dark_activeHighlight)
            } else {
                Color(eudiw_theme_light_activeHighlight)
            }

        val onActiveHighlight: Color
            get() = if (isInDarkMode) {
                Color(eudiw_theme_dark_onActiveHighlight)
            } else {
                Color(eudiw_theme_light_onActiveHighlight)
            }
    }
}

val ColorScheme.success: Color
    @Composable get() = if (isSystemInDarkTheme()) {
        Color(ThemeColors.eudiw_theme_dark_success)
    } else {
        Color(ThemeColors.eudiw_theme_light_success)
    }

val ColorScheme.warning: Color
    @Composable get() = if (isSystemInDarkTheme()) {
        Color(ThemeColors.eudiw_theme_dark_warning)
    } else {
        Color(ThemeColors.eudiw_theme_light_warning)
    }

val ColorScheme.pending: Color
    @Composable get() = if (isSystemInDarkTheme()) {
        Color(ThemeColors.eudiw_theme_dark_pending)
    } else {
        Color(ThemeColors.eudiw_theme_light_pending)
    }

val ColorScheme.divider: Color
    @Composable get() = if (isSystemInDarkTheme()) {
        Color(ThemeColors.eudiw_theme_dark_divider)
    } else {
        Color(ThemeColors.eudiw_theme_light_divider)
    }

// Premium effect colors - Authbound brand
val ColorScheme.shadowNavy: Color
    @Composable get() = if (isSystemInDarkTheme()) {
        Color(ThemeColors.eudiw_theme_dark_shadow_navy)
    } else {
        Color(ThemeColors.eudiw_theme_light_shadow_navy)
    }

val ColorScheme.glowAccent: Color
    @Composable get() = if (isSystemInDarkTheme()) {
        Color(ThemeColors.eudiw_theme_dark_glow_accent)
    } else {
        Color(ThemeColors.eudiw_theme_light_glow_accent)
    }

val ColorScheme.glowPrimary: Color
    @Composable get() = if (isSystemInDarkTheme()) {
        Color(ThemeColors.eudiw_theme_dark_glow_primary)
    } else {
        Color(ThemeColors.eudiw_theme_light_glow_primary)
    }

// Semantic color for selection/active states (nav icons, checkboxes, radio buttons)
// Separate from primary to allow different colors in dark mode
val ColorScheme.activeHighlight: Color
    @Composable get() = if (isSystemInDarkTheme()) {
        Color(ThemeColors.eudiw_theme_dark_activeHighlight)
    } else {
        Color(ThemeColors.eudiw_theme_light_activeHighlight)
    }

val ColorScheme.onActiveHighlight: Color
    @Composable get() = if (isSystemInDarkTheme()) {
        Color(ThemeColors.eudiw_theme_dark_onActiveHighlight)
    } else {
        Color(ThemeColors.eudiw_theme_light_onActiveHighlight)
    }
