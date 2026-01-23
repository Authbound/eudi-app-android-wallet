# Component Patterns Reference

## QuickActionCard

Premium action card with gradient background and visual polish.

### Structure
```
┌─────────────────────────┐
│  ╔═══╗    ○ ○           │ ← Decorative circles
│  ║ ◎ ║  Title           │ ← Icon with glow ring
│  ╚═══╝                  │
│  Description       [→]  │ ← Arrow indicator
└─────────────────────────┘
```

### Implementation Pattern
```kotlin
QuickActionCard(
    modifier = Modifier.height(140.dp),
    config = QuickActionConfig(
        id = "action_id",
        title = "Action Title",
        description = "Action description text",
        icon = AppIcons.IconName,
        gradientStart = Color(0xFF0A1A36),
        gradientEnd = Color(0xFF1E3A5F),
        accentColor = Color(0xFF3B82F6)
    ),
    onClick = { /* handle click */ }
)
```

### Key Visual Elements
- **Background**: 135° diagonal gradient
- **Corners**: 20dp rounded
- **Shadow**: 8dp elevation
- **Icon container**: 48dp circle, 15% accent glow
- **Arrow**: 20dp, 60% white opacity

---

## Premium Empty State

For sections with no content. Creates premium feel instead of boring empty state.

### Structure
```
┌─────────────────────────────────────┐
│  ○ ○                     ○          │ ← Decorative circles
│                                     │
│     ╔═══╗                           │
│     ║ ◎ ║  Title text               │ ← Icon + Text
│     ╚═══╝  Description              │
│                                     │
│         [ + Add Action ]            │ ← CTA Button
│                                     │
└─────────────────────────────────────┘
```

### Implementation Pattern
```kotlin
@Composable
fun PremiumEmptyState(
    icon: IconDataUi,
    title: String,
    description: String,
    actionText: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
        ) {
            // Decorative circles
            DecorativeCircles(
                modifier = Modifier.align(Alignment.TopEnd),
                color = MaterialTheme.colorScheme.tertiary
            )

            // Content column
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon with glow
                IconWithGlow(icon = icon)

                // Text content
                Text(title, style = typography.titleLarge, color = onPrimary)
                Text(description, color = onPrimary.copy(alpha = 0.7f))

                // CTA Button
                WrapButton(text = actionText, onClick = onActionClick)
            }
        }
    }
}
```

---

## Section Headers

Use gradient text for premium section titles.

### Implementation
```kotlin
@Composable
fun GradientSectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.onSurface,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            )
        ),
        modifier = modifier
    )
}
```

---

## Guide Carousel Topic Card

Large hero card with gradient background and navigation rail.

### Visual Elements
- **Background**: Navy diagonal gradient
- **Decorative**: Giant icon at 5% opacity (background), circles at 8-12%
- **Navigation**: Vertical rail with capsule indicators
- **Typography**: Display title (32sp), body description

---

## Bottom Navigation

Floating navigation bar with center FAB.

### FAB with Glow
```kotlin
Box(contentAlignment = Alignment.Center) {
    // Glow layer
    Box(
        modifier = Modifier
            .size(72.dp)
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

    // FAB
    FloatingActionButton(
        modifier = Modifier.size(56.dp),
        containerColor = primary
    ) { /* icon */ }
}
```

### Selected Indicator with Glow
```kotlin
Box(contentAlignment = Alignment.Center) {
    // Glow behind
    if (selected) {
        Box(
            modifier = Modifier
                .size(24.dp, 8.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(tertiary.copy(0.35f), Transparent),
                        radius = 32f
                    ),
                    shape = RoundedCornerShape(4.dp)
                )
        )
    }

    // Pill indicator
    Box(
        modifier = Modifier
            .width(if (selected) 16.dp else 4.dp)
            .height(4.dp)
            .background(gradientBrush)
    )
}
```

---

## Credential List Item

Standard list item for credentials with status indicators.

### Status Colors
| State | Color |
|-------|-------|
| Issued (valid) | Default (onSurface) |
| Pending | `warning` |
| Expired | `error` |
| Revoked | `error` |
| Failed | `error` |
