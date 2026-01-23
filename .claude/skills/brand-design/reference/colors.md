# Brand Colors Reference

## Primary Navy Spectrum

The core brand palette is a navy gradient spectrum that creates depth and premium feel.

| Token | Hex | OKLCH | Usage |
|-------|-----|-------|-------|
| Navy Deep | `#0A1A36` | `oklch(0.30 0.085 250)` | Primary backgrounds, hero cards |
| Navy Medium | `#1E3A5F` | `oklch(0.35 0.08 250)` | Gradient endpoints |
| Navy Light | `#2A4A6F` | `oklch(0.40 0.075 250)` | Tertiary backgrounds |

## Accent Colors

| Token | Hex | Usage |
|-------|-----|-------|
| Blue Accent | `#3B82F6` | Primary CTAs, icons, interactive elements |
| Teal Accent | `#2A8A9A` | Highlights, decorations, secondary accent |
| Slate | `#5F6A85` | Secondary text, borders, muted elements |

## Quick Action Gradients

Each quick action has a unique gradient personality:

### Authenticate (Navy)
```kotlin
gradientStart = Color(0xFF0A1A36)  // Deep navy
gradientEnd = Color(0xFF1E3A5F)    // Medium navy
accentColor = Color(0xFF3B82F6)    // Blue accent
```

### Add Credentials (Amber)
```kotlin
gradientStart = Color(0xFFB45309)  // Amber dark
gradientEnd = Color(0xFFD97706)    // Amber light
accentColor = Color(0xFFFBBF24)    // Yellow accent
```

### Verify (Emerald)
```kotlin
gradientStart = Color(0xFF047857)  // Emerald dark
gradientEnd = Color(0xFF059669)    // Emerald light
accentColor = Color(0xFF34D399)    // Green accent
```

### Sign (Purple)
```kotlin
gradientStart = Color(0xFF5B21B6)  // Purple dark
gradientEnd = Color(0xFF7C3AED)    // Purple light
accentColor = Color(0xFFA78BFA)    // Violet accent
```

## Theme Integration

### Using ThemeColors
```kotlin
import eu.europa.ec.resourceslogic.theme.values.ThemeColors

// Direct access (non-composable)
val navy = ThemeColors.primary

// Premium effects
val shadow = ThemeColors.shadowNavy
val glow = ThemeColors.glowAccent
```

### Using ColorScheme Extensions
```kotlin
import eu.europa.ec.resourceslogic.theme.values.*

@Composable
fun MyComponent() {
    val shadow = MaterialTheme.colorScheme.shadowNavy
    val glow = MaterialTheme.colorScheme.glowAccent
}
```

## Dark Mode Mapping

| Light Mode | Dark Mode | Notes |
|------------|-----------|-------|
| `#0A1A36` (navy) | `#3B82F6` (blue) | Primary inverts to accent |
| `#1E3A5F` (medium) | `#2A4A6F` (light) | Gradients stay similar |
| `#FFFCF6` (cream bg) | `#0A1A36` (navy bg) | Background swap |

## Anti-Patterns

**❌ DON'T: Hardcode brand colors**
```kotlin
// Bad
color = Color(0xFF0A1A36)
```

**✅ DO: Use tokens or theme**
```kotlin
// Good - via theme
color = MaterialTheme.colorScheme.primary

// Good - via direct access for non-composable
color = ThemeColors.primary

// Good - for premium effects
color = MaterialTheme.colorScheme.shadowNavy
```
