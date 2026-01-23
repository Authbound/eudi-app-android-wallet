# Premium Effects Reference

## Decorative Circles

Circular patterns add visual interest to premium cards.

### Standard Pattern (120dp canvas)
```kotlin
@Composable
fun DecorativeCircles(
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(
        modifier = modifier
            .size(120.dp)
            .padding(12.dp)
    ) {
        // Large circle (outline)
        drawCircle(
            color = color.copy(alpha = 0.08f),
            radius = 45.dp.toPx(),
            center = Offset(size.width * 0.7f, size.height * 0.3f),
            style = Stroke(width = 2.dp.toPx())
        )
        // Medium circle (filled)
        drawCircle(
            color = color.copy(alpha = 0.12f),
            radius = 20.dp.toPx(),
            center = Offset(size.width * 0.4f, size.height * 0.5f)
        )
        // Small circle (filled)
        drawCircle(
            color = color.copy(alpha = 0.10f),
            radius = 8.dp.toPx(),
            center = Offset(size.width * 0.85f, size.height * 0.65f)
        )
    }
}
```

### Usage Guidelines
- **Position**: Top-right corner of dark cards
- **Color**: Use accent color (tertiary or custom)
- **Opacity range**: 8-12%
- **Only on dark backgrounds**: Circles look best against navy/dark gradients

### Compact Pattern (80dp canvas)
For smaller cards like QuickActionCard.

```kotlin
Canvas(modifier.size(80.dp).padding(8.dp)) {
    drawCircle(color.copy(0.10f), 28.dp.toPx(), style = Stroke(1.5.dp.toPx()))
    drawCircle(color.copy(0.08f), 12.dp.toPx())
    drawCircle(color.copy(0.12f), 5.dp.toPx())
}
```

---

## Gradient Backgrounds

### Diagonal Gradient (135°)
Standard for action cards and premium sections.

```kotlin
Box(
    modifier = Modifier.background(
        brush = Brush.linearGradient(
            colors = listOf(gradientStart, gradientEnd),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    )
)
```

### Vertical Gradient (180°)
For hero sections and top-to-bottom flows.

```kotlin
Brush.linearGradient(
    colors = listOf(topColor, bottomColor),
    start = Offset(0f, 0f),
    end = Offset(0f, Float.POSITIVE_INFINITY)
)
```

### Radial Gradient
For glow effects behind circular elements.

```kotlin
Brush.radialGradient(
    colors = listOf(
        centerColor.copy(alpha = 0.25f),
        middleColor.copy(alpha = 0.08f),
        Color.Transparent
    ),
    radius = 56f
)
```

---

## Glow Effects

### Icon Container Glow
Subtle glow ring around icon containers.

```kotlin
Box(
    modifier = Modifier
        .size(48.dp)
        .clip(CircleShape)
        .background(accentColor.copy(alpha = 0.15f)),
    contentAlignment = Alignment.Center
) {
    WrapIcon(iconData = icon, customTint = Color.White)
}
```

### FAB Glow
Radial glow behind floating action buttons.

```kotlin
// Glow layer (behind FAB)
Box(
    modifier = Modifier
        .size(72.dp)  // Larger than FAB
        .background(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.25f),
                    primary.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                radius = 56f
            ),
            shape = CircleShape
        )
)
```

### Selected Indicator Glow
Glow behind selected navigation indicators.

```kotlin
if (selected) {
    Box(
        modifier = Modifier
            .width(24.dp)
            .height(8.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tertiary.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    radius = 32f
                ),
                shape = RoundedCornerShape(4.dp)
            )
    )
}
```

---

## Shadow Tokens

### Premium Shadow (Navy-tinted)
Use `shadowNavy` token for depth with brand color.

```kotlin
// Via ColorScheme extension
val shadowColor = MaterialTheme.colorScheme.shadowNavy

// Direct access
val shadowColor = ThemeColors.shadowNavy
```

### Elevation Guidelines
| Component | Elevation |
|-----------|-----------|
| Cards | 4-8dp |
| FAB | 8-12dp |
| Navigation bar | 12dp |
| Modals | 16dp |

---

## Gradient Text

For section headers with premium feel.

```kotlin
Text(
    text = "Section Title",
    style = MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.Bold,
        brush = Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.onSurface,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        )
    )
)
```

---

## Background Decoration (Giant Icon)

Large decorative icon in card backgrounds.

```kotlin
WrapIcon(
    iconData = topic.icon,
    customTint = Color.White.copy(alpha = 0.05f),
    modifier = Modifier
        .size(350.dp)
        .align(Alignment.TopEnd)
        .offset(x = 100.dp, y = -80.dp)
        .rotate(-15f)
)
```

### Guidelines
- **Size**: 300-400dp (much larger than container)
- **Opacity**: 5% white
- **Position**: Offset outside container bounds
- **Rotation**: Slight angle (-15°) adds interest
