# Android Wallet Implementation Plan

## Overview

This document outlines the implementation plan for refactoring the Android wallet app to incorporate UI elements and features from the existing mobile app. The plan focuses on preserving the authenticate and sign buttons while adapting them to the Android platform's design patterns and guidelines.

## Current State Analysis

### Existing Mobile App (Expo/React Native)
- Modern, visually appealing UI with a floating tab bar
- Home screen with "Quick Actions" cards in a grid layout
- "Authenticate" and "Sign" actions are prominent on the home screen
- Clean, user-friendly interface with clear visual hierarchy

### Android Wallet App (Native Android/Kotlin)
- Follows Material Design principles with a bottom navigation bar
- Home screen with "Authenticate" and "Sign" action cards in a vertical layout
- Bottom sheets for additional options and information
- Structured around EU Digital Identity Wallet standards

## Implementation Goals

1. Preserve the core functionality of the Android wallet app
2. Incorporate the visual appeal and user experience of the mobile app
3. Adapt the "Authenticate" and "Sign" buttons to a more modern, card-based design
4. Maintain compliance with EU Digital Identity Wallet standards
5. Ensure accessibility and performance on Android devices

## Implementation Phases

### Phase 1: UI Component Redesign (HIGH PRIORITY)

:white_check_mark: **Create Quick Action Component**
- Develop a new `QuickActionCard` component based on the mobile app design
- Implement animations and visual feedback on interaction
- Support customization of colors, icons, and text
- Files created:
  - `ui-logic/src/main/java/eu/europa/ec/uilogic/component/wrap/QuickActionCard.kt`

:white_check_mark: **Redesign Action Cards**
- Transform the current vertical action cards into a grid-based "Quick Actions" layout
- Implement visually appealing card designs with gradients and icons
- Ensure proper spacing and visual hierarchy
- Fixed card sizing to ensure consistent dimensions across all quick actions
- Files modified:
  - `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/home/HomeScreen.kt`
  - `ui-logic/src/main/java/eu/europa/ec/uilogic/component/wrap/QuickActionCard.kt`

:white_check_mark: **Update Bottom Navigation**
- Enhance the current bottom navigation with animations and visual improvements
- Implement haptic feedback similar to the mobile app
- Ensure proper spacing and visual hierarchy
- Files modified:
  - `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/BottomNavigation.kt`

### Phase 2: Home Screen Refactoring (HIGH PRIORITY)

:white_check_mark: **Update ViewModel and State**
- Modify the HomeViewModel to support the new quick actions
- Update the state to include quick action configurations
- Ensure proper event handling for all actions
- Files modified:
  - `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/home/HomeViewModel.kt`

:white_check_mark: **Implement Quick Actions Grid**
- Create a 2x2 grid layout for quick actions
- Include "Authenticate" and "Sign" as primary actions
- Add additional quick actions as needed (e.g., "View Credentials", "Settings")
- Files modified:
  - `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/home/HomeScreen.kt`
  - `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/home/HomeViewModel.kt`

:white_check_mark: **Redesign Home Screen Layout**
- Implement a grid-based layout for quick actions
- Update welcome message and header styling
- Add proper spacing and visual hierarchy
- Files modified:
  - `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/home/HomeScreen.kt`

### Phase 3: Bottom Sheet Enhancements (MEDIUM PRIORITY)

:white_check_mark: **Redesign Bottom Sheets**
- Update bottom sheet designs to match the mobile app's aesthetic
- Implement smooth animations for sheet transitions
- Ensure proper spacing and visual hierarchy
- Files modified:
  - `ui-logic/src/main/java/eu/europa/ec/uilogic/component/wrap/WrapModalBottomSheet.kt`
  - `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/home/HomeScreen.kt`

:white_check_mark: **Enhance Authentication Options**
- Update the authentication bottom sheet with improved visuals
- Add clear visual distinction between in-person and online options
- Implement smooth animations and transitions
- Files modified:
  - `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/home/HomeScreen.kt`

:white_check_mark: **Implement Sign Document Options**
- Fully implement the sign document bottom sheet (previously commented out)
- Add clear visual distinction between device and QR options
- Ensure proper event handling for all options
- Files modified:
  - `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/home/HomeScreen.kt`
  - `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/home/HomeViewModel.kt`

### Phase 4: Visual Enhancements (MEDIUM PRIORITY)

:white_large_square: **Implement Custom Theming**
- Update color schemes to match the mobile app's aesthetic
- Implement custom typography and spacing
- Ensure proper dark mode support
- Files to modify:
  - `ui-logic/src/main/java/eu/europa/ec/uilogic/theme/Theme.kt`
  - `resources-logic/src/main/res/values/colors.xml`

:white_large_square: **Add Animations and Transitions**
- Implement smooth animations for screen transitions
- Add micro-interactions for better user feedback
- Ensure proper performance on all devices
- Files to modify:
  - `ui-logic/src/main/java/eu/europa/ec/uilogic/component/utils/Animations.kt` (create if needed)

:white_large_square: **Update Icons and Assets**
- Replace or enhance current icons to match the mobile app's style
- Ensure proper resolution and scaling for all devices
- Maintain visual consistency across the app
- Files to modify:
  - `resources-logic/src/main/res/drawable/` (various icon files)

### Phase 5: Testing and Refinement (FINAL PRIORITY)

:white_large_square: **Implement UI Tests**
- Create comprehensive UI tests for all new components
- Ensure proper functionality across different devices and screen sizes
- Test performance and accessibility
- Files to create/modify:
  - `dashboard-feature/src/test/java/eu/europa/ec/dashboardfeature/ui/home/HomeScreenTest.kt`
  - `ui-logic/src/test/java/eu/europa/ec/uilogic/component/wrap/QuickActionCardTest.kt`

:white_large_square: **Perform User Testing**
- Conduct user testing with the new UI
- Gather feedback and make necessary adjustments
- Ensure proper usability and accessibility

:white_large_square: **Finalize Documentation**
- Update all relevant documentation
- Create design guidelines for future development
- Document all new components and their usage

## Technical Implementation Details

### Quick Action Card Component

```kotlin
@Composable
fun QuickActionCard(
    title: String,
    description: String,
    icon: IconData,
    backgroundColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.48f)  // Approximately half width for 2-column grid
            .aspectRatio(1f)      // Square aspect ratio
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon container with circular background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = icon,
                    customTint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Title and description
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}
```

### Home Screen Quick Actions Implementation

```kotlin
@Composable
private fun QuickActions(
    actions: List<QuickActionConfig>,
    onActionClick: (QuickActionConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.quick_actions),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // 2x2 Grid layout for quick actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // First row of actions (2 cards)
            actions.take(2).forEach { action ->
                QuickActionCard(
                    title = action.title,
                    description = action.description,
                    icon = action.icon,
                    backgroundColor = action.backgroundColor,
                    borderColor = action.borderColor,
                    onClick = { onActionClick(action) }
                )
            }
        }
        
        // Second row of actions (if more than 2 actions)
        if (actions.size > 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                actions.drop(2).take(2).forEach { action ->
                    QuickActionCard(
                        title = action.title,
                        description = action.description,
                        icon = action.icon,
                        backgroundColor = action.backgroundColor,
                        borderColor = action.borderColor,
                        onClick = { onActionClick(action) }
                    )
                }
            }
        }
    }
}
```

## ViewModel Configuration

```kotlin
// In HomeViewModel.kt
data class QuickActionConfig(
    val id: String,
    val title: String,
    val description: String,
    val icon: IconData,
    val backgroundColor: Color,
    val borderColor: Color,
    val action: () -> Unit
)

// In setInitialState()
val quickActions = listOf(
    QuickActionConfig(
        id = "authenticate",
        title = resourceProvider.getString(R.string.home_screen_authenticate),
        description = resourceProvider.getString(R.string.home_screen_authentication_card_description),
        icon = AppIcons.IdCards,
        backgroundColor = MaterialTheme.colorScheme.primary,
        borderColor = MaterialTheme.colorScheme.primaryContainer,
        action = { setEvent(Event.AuthenticateCard.AuthenticatePressed) }
    ),
    QuickActionConfig(
        id = "sign",
        title = resourceProvider.getString(R.string.home_screen_sign),
        description = resourceProvider.getString(R.string.home_screen_sign_card_description),
        icon = AppIcons.Contract,
        backgroundColor = MaterialTheme.colorScheme.secondary,
        borderColor = MaterialTheme.colorScheme.secondaryContainer,
        action = { setEvent(Event.SignDocumentCard.SignDocumentPressed) }
    ),
    // Additional quick actions as needed
)
```

## Compatibility Considerations

- The implementation will maintain compatibility with the EU Digital Identity Wallet standards
- All UI changes will adhere to Material Design guidelines for Android
- Accessibility features will be preserved and enhanced
- Performance will be optimized for a wide range of Android devices

## Progress Tracking

- :white_large_square: Not started
- :arrows_counterclockwise: In progress
- :white_check_mark: Completed
- :x: Blocked

## Conclusion

This implementation plan provides a comprehensive approach to refactoring the Android wallet app to incorporate UI elements and features from the existing mobile app. The phased approach ensures a smooth transition with minimal disruption to ongoing development while significantly enhancing the user experience. 