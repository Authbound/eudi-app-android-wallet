# Home Credentials List Refresh After Issuance — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix the home screen credentials list not refreshing after a new credential is issued.

**Architecture:** The `DashboardScreen`'s `LifecycleEffect(ON_RESUME)` already fires when popping back from issuance. It refreshes `DashboardViewModel` and `ActionsViewModel` but skips `HomeViewModel`. We add one line to also refresh home credentials.

**Tech Stack:** Kotlin, Jetpack Compose, Compose Navigation, MVI (ViewModel events)

---

## Root Cause

`HomeScreen` uses `LifecycleEffect(ON_RESUME)` to dispatch `Event.GetCredentials`, but this never fires after in-Activity `popBackStack()` navigation. The inner NavHost's lifecycle doesn't transition through PAUSED→RESUMED — only the outer NavBackStackEntry does.

`DashboardScreen.kt:226-251` has a working `LifecycleEffect(ON_RESUME)` that fires correctly on resume, but only refreshes `actionsViewModel` and `viewModel` (DashboardViewModel) — not `homeViewModel`.

---

### Task 1: Add home credentials refresh to DashboardScreen ON_RESUME

**Files:**
- Modify: `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/dashboard/DashboardScreen.kt:245`

**Step 1: Add the refresh call**

In `DashboardScreen.kt`, inside the `LifecycleEffect(ON_RESUME)` block (line 226-251), add `homeViewModel.setEvent(...)` after the existing `actionsViewModel.setEvent(...)` call at line 245.

The block currently ends with:
```kotlin
        actionsViewModel.setEvent(eu.europa.ec.dashboardfeature.ui.actions.Event.OnResume)
        viewModel.setEvent(
            Event.Init(
                deepLinkUri = context.getPendingDeepLink()
            )
        )
```

Change to:
```kotlin
        actionsViewModel.setEvent(eu.europa.ec.dashboardfeature.ui.actions.Event.OnResume)
        homeViewModel.setEvent(
            eu.europa.ec.dashboardfeature.ui.home.Event.GetCredentials
        )
        viewModel.setEvent(
            Event.Init(
                deepLinkUri = context.getPendingDeepLink()
            )
        )
```

No new imports needed — `HomeViewModel` is already imported at line 56, and `Event` is referenced via fully-qualified name to avoid ambiguity with `DashboardScreen`'s own `Event`.

**Step 2: Verify compilation**

Run: `JAVA_HOME="/Users/lassi/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :dashboard-feature:compileDevDebugKotlin`

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/dashboard/DashboardScreen.kt
git commit -m "fix: refresh home credentials list when returning from issuance"
```

---

### Task 2: Manual verification

**Test on device/emulator:**
1. Open the app, observe credentials on home screen
2. Navigate to Add Document and issue a new credential
3. After issuance success, tap Continue to return to home
4. Verify the new credential appears in the home credentials list immediately

**Edge cases to check:**
- Return to home via back button (should also work since it pops the back stack)
- Switch to another bottom nav tab and back (should still work via existing `HomeScreen` lifecycle effect)
