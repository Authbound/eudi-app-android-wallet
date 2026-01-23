# Animation Standards Reference

## Entrance Animations

### Standard Entrance (300ms)
Use for cards, sections, and list items entering the screen.

```kotlin
AnimatedVisibility(
    visible = isVisible,
    enter = fadeIn(tween(300)) + slideInVertically(
        animationSpec = tween(300),
        initialOffsetY = { it / 4 }
    )
)
```

### Staggered List Entrance
Use for lists where items should appear sequentially.

```kotlin
AnimatedVisibility(
    visible = true,
    enter = fadeIn(tween(300, delayMillis = index * 50)) +
            slideInVertically(tween(300, delayMillis = index * 50))
)
```

- **Base delay**: 50ms per item
- **Max items to stagger**: First 5-6 items, rest instant

## Interaction Feedback

### Press Scale Animation
```kotlin
val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.97f else 1f,
    animationSpec = tween(durationMillis = 100),
    label = "scale"
)

Surface(
    modifier = Modifier.scale(scale)
) { /* content */ }
```

| Component Type | Scale Value |
|----------------|-------------|
| Cards | `0.97f` |
| Buttons | `0.95f` |
| Small icons | `0.90f` |

### Haptic Feedback
Always include haptic feedback on user interactions.

```kotlin
val view = LocalView.current

Modifier.clickable {
    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    onClick()
}
```

## Spring Animations

Use for bouncy, playful interactions like navigation selection.

```kotlin
val scale by animateFloatAsState(
    targetValue = if (selected) 1.15f else 1f,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    ),
    label = "scale"
)
```

### Spring Presets
| Use Case | Damping | Stiffness |
|----------|---------|-----------|
| Navigation selection | `MediumBouncy` | `Medium` |
| Card expansion | `LowBouncy` | `Low` |
| Quick response | `NoBouncy` | `High` |

## Crossfade Transitions

Use for content that changes (like carousel topics).

```kotlin
Crossfade(
    targetState = currentTopic,
    animationSpec = tween(300),
    label = "content_transition"
) { topic ->
    TopicContent(topic)
}
```

## Color Animations

### Animated Icon Tint
```kotlin
val iconTint by animateColorAsState(
    targetValue = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outline,
    animationSpec = tween(durationMillis = 200),
    label = "iconTint"
)
```

### Animated Background Alpha
```kotlin
val backgroundAlpha by animateFloatAsState(
    targetValue = if (selected) 0.12f else 0f,
    animationSpec = tween(durationMillis = 200),
    label = "backgroundAlpha"
)
```

## Auto-Advance (Carousels)

For auto-advancing content like the Guide carousel.

```kotlin
var selectedIndex by remember { mutableStateOf(0) }
var delayDuration by remember { mutableStateOf(5000L) }

LaunchedEffect(selectedIndex) {
    delay(delayDuration)
    // Reset delay if manually extended
    if (delayDuration > 5000L) {
        delayDuration = 5000L
    }
    selectedIndex = (selectedIndex + 1) % items.size
}

// On manual interaction, extend delay
onClick = {
    selectedIndex = index
    delayDuration = 10000L  // Give user more time
}
```

## Summary Table

| Animation Type | Duration | Easing |
|----------------|----------|--------|
| Entrance | 300ms | `tween` |
| Press scale | 100ms | `tween` |
| Color change | 200ms | `tween` |
| Spring bounce | Variable | `spring(MediumBouncy)` |
| Crossfade | 300ms | `tween` |
| Auto-advance | 5000ms | Linear (delay) |
