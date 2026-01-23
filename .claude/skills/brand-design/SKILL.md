---
name: brand-design
version: 1.0.0
description: Authbound brand design system for Android UI development. Provides color tokens, gradient patterns, animation standards, and premium visual effects aligned with the web dashboard.
---

# Brand Design System - Authbound Android

This skill provides the Authbound brand design system for implementing premium, consistent UI across the Android wallet app.

## Instructions

**When to use this skill**: Reference this skill when implementing new UI components, reviewing design consistency, or creating premium visual effects.

### Step 1: Understand the Design Context

Determine what type of component you're building:

1. **Action Cards** (QuickActionCard, ActionCard) → Use gradient backgrounds + decorative circles
2. **Empty States** → Use premium empty state pattern with navy gradients
3. **Section Headers** → Use gradient text effect
4. **Navigation Elements** → Use glow effects for active states
5. **Lists & Cards** → Use proper spacing and shadow tokens

### Step 2: Load Relevant Reference Material

Based on component type, read the appropriate reference file:

- **Colors & Gradients** → `reference/colors.md` (brand palette, gradient patterns)
- **Animation Standards** → `reference/animations.md` (entrance, interaction, spring)
- **Component Patterns** → `reference/components.md` (card types, empty states, navigation)
- **Premium Effects** → `reference/effects.md` (shadows, glows, decorative elements)

### Step 3: Apply Brand Tokens

When implementing UI:

1. **Never hardcode colors** - Use brand tokens or MaterialTheme
2. **Use 135° diagonal gradients** for action-oriented components
3. **Apply decorative circles** on dark backgrounds only
4. **Include haptic feedback** on all interactive elements
5. **Maintain 300ms entrance animation standard**

### Step 4: Verify Consistency

Check implementation against:

- [ ] Colors match brand palette (navy spectrum + accents)
- [ ] Gradients use correct direction (135° diagonal)
- [ ] Shadows use `shadowNavy` token where applicable
- [ ] Glows use `glowAccent` or `glowPrimary` tokens
- [ ] Animations follow 300ms standard
- [ ] Touch targets are minimum 48dp
- [ ] Both light and dark modes look correct

## Quick Reference

### Core Colors
| Token | Light | Dark |
|-------|-------|------|
| Navy Primary | `#0A1A36` | `#3B82F6` |
| Navy Medium | `#1E3A5F` | - |
| Blue Accent | `#3B82F6` | `#3B82F6` |
| Teal Accent | `#2A8A9A` | `#2A8A9A` |

### Gradient Pattern
```kotlin
Brush.linearGradient(
    colors = listOf(gradientStart, gradientEnd),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)
```

### Animation Defaults
- Entrance: `fadeIn(tween(300)) + slideInVertically(tween(300))`
- Press scale: `0.97f` (cards), `0.95f` (buttons)
- Haptic: `HapticFeedbackConstants.VIRTUAL_KEY`

## Core Principles

- **Premium feel**: Use gradients over flat colors
- **Brand coherence**: Match web dashboard visual language
- **Subtle animation**: Enhance without distraction
- **Consistent tokens**: Never hardcode brand colors
- **Dark mode first**: Test dark mode thoroughly
