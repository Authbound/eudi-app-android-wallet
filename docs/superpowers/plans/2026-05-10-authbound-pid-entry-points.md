# Authbound PID Entry Points Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep Authbound PID creation discoverable until the wallet already contains an Authbound-issued PID, while allowing the Home promotional prompt to be snoozed without removing stable entry points.

**Architecture:** Add one dashboard-feature eligibility interactor for Authbound PID presence and Home promo snooze state. Home, Dashboard side menu, and Documents list consume that interactor, so every entry point uses the same "hide only when Authbound PID exists" rule. Home moves the promo out of the hero carousel, adds carousel page dots for real hero credentials, and keeps Add Credential / side menu options available during promo snooze.

**Tech Stack:** Kotlin, Jetpack Compose, MVI ViewModels, Koin annotations, WalletCoreDocumentsController, PrefsControllerV2, MockK/Truth unit tests.

---

### Task 1: Shared Eligibility Unit

**Files:**
- Create: `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/interactor/AuthboundPidEntryInteractor.kt`
- Test: `dashboard-feature/src/test/java/eu/europa/ec/dashboardfeature/interactor/TestAuthboundPidEntryPolicy.kt`

- [x] **Step 1: Write failing tests for PID detection and snooze resolution**

```kotlin
@Test
fun `Authbound custom PID format from Authbound issuer is an Authbound PID`() {
    val result = isAuthboundPidCredential(
        documentIdentifier = DocumentIdentifier.OTHER("urn:vc:authbound:pid:1.0"),
        issuerName = "Authbound"
    )
    assertThat(result).isTrue()
}

@Test
fun `non Authbound PID does not hide entry points`() {
    val result = isAuthboundPidCredential(
        documentIdentifier = DocumentIdentifier.MdocPid,
        issuerName = "Government issuer"
    )
    assertThat(result).isFalse()
}

@Test
fun `future snooze hides only the home prompt`() {
    val result = resolveAuthboundPidEntryState(
        hasAuthboundPid = false,
        snoozeUntilEpochMillis = 2_000L,
        nowEpochMillis = 1_000L
    )
    assertThat(result.shouldShowEntry).isTrue()
    assertThat(result.shouldShowHomePrompt).isFalse()
}

@Test
fun `Authbound PID hides every entry point`() {
    val result = resolveAuthboundPidEntryState(
        hasAuthboundPid = true,
        snoozeUntilEpochMillis = 0L,
        nowEpochMillis = 1_000L
    )
    assertThat(result.shouldShowEntry).isFalse()
    assertThat(result.shouldShowHomePrompt).isFalse()
}
```

- [x] **Step 2: Run the new test and confirm it fails**

Run: `./gradlew :dashboard-feature:test --tests "eu.europa.ec.dashboardfeature.interactor.TestAuthboundPidEntryPolicy"`
Expected: FAIL because `isAuthboundPidCredential` and `resolveAuthboundPidEntryState` do not exist.

- [x] **Step 3: Implement the pure policy and interactor shell**

```kotlin
internal const val AUTHBOUND_PID_FORMAT_TYPE: String = "urn:vc:authbound:pid:1.0"
internal const val AUTHBOUND_PID_HOME_PROMPT_SNOOZE_UNTIL_KEY: String =
    "authbound_pid_home_prompt_snooze_until"
internal const val AUTHBOUND_PID_HOME_PROMPT_SNOOZE_DAYS: Long = 30L

data class AuthboundPidEntryState(
    val shouldShowEntry: Boolean,
    val shouldShowHomePrompt: Boolean,
)

internal fun isAuthboundPidCredential(
    documentIdentifier: DocumentIdentifier,
    issuerName: String?,
): Boolean {
    val isPidLike: Boolean = documentIdentifier == DocumentIdentifier.MdocPid
        || documentIdentifier == DocumentIdentifier.SdJwtPid
        || (documentIdentifier is DocumentIdentifier.OTHER
        && documentIdentifier.formatType.equals(AUTHBOUND_PID_FORMAT_TYPE, ignoreCase = true))
    val isAuthboundIssued: Boolean = issuerName?.contains("authbound", ignoreCase = true) == true
    return isPidLike && isAuthboundIssued
}

internal fun resolveAuthboundPidEntryState(
    hasAuthboundPid: Boolean,
    snoozeUntilEpochMillis: Long,
    nowEpochMillis: Long,
): AuthboundPidEntryState {
    val shouldShowEntry: Boolean = !hasAuthboundPid
    val isHomePromptSnoozed: Boolean = snoozeUntilEpochMillis > nowEpochMillis
    return AuthboundPidEntryState(
        shouldShowEntry = shouldShowEntry,
        shouldShowHomePrompt = shouldShowEntry && !isHomePromptSnoozed
    )
}
```

- [x] **Step 4: Run the test and confirm it passes**

Run: `./gradlew :dashboard-feature:test --tests "eu.europa.ec.dashboardfeature.interactor.TestAuthboundPidEntryPolicy"`
Expected: PASS.

### Task 2: Wire Eligibility Into View Models

**Files:**
- Modify: `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/di/FeatureDashboardModule.kt`
- Modify: `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/home/HomeViewModel.kt`
- Modify: `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/dashboard/DashboardViewModel.kt`
- Modify: `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/interactor/DashboardInteractor.kt`
- Modify: `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/dashboard/model/SideMenuItemUi.kt`
- Modify: `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/documents/list/DocumentsViewModel.kt`
- Test: `dashboard-feature/src/test/java/eu/europa/ec/dashboardfeature/ui/home/TestHomeViewModel.kt`

- [x] **Step 1: Write failing ViewModel tests for promo state and snooze**

```kotlin
@Test
fun `GetCredentials shows Authbound entry when Authbound PID is missing`() = coroutineRule.runTest {
    whenever(authboundPidEntryInteractor.getEntryState()).thenReturn(
        AuthboundPidEntryState(shouldShowEntry = true, shouldShowHomePrompt = true)
    )
    viewModel.setEvent(Event.GetCredentials)
    assertThat(viewModel.viewState.value.shouldShowAuthboundPidEntry).isTrue()
    assertThat(viewModel.viewState.value.shouldShowAuthboundPidHomePrompt).isTrue()
}

@Test
fun `Not now hides only Home Authbound prompt`() = coroutineRule.runTest {
    whenever(authboundPidEntryInteractor.snoozeHomePrompt()).thenReturn(Unit)
    viewModel.setEvent(Event.AuthboundPidPromoNotNowPressed)
    assertThat(viewModel.viewState.value.shouldShowAuthboundPidEntry).isTrue()
    assertThat(viewModel.viewState.value.shouldShowAuthboundPidHomePrompt).isFalse()
}
```

- [x] **Step 2: Run the affected Home ViewModel test and confirm it fails**

Run: `./gradlew :dashboard-feature:test --tests "eu.europa.ec.dashboardfeature.ui.home.TestHomeViewModel"`
Expected: FAIL because new state/event/interactor dependency does not exist.

- [x] **Step 3: Add interactor API and ViewModel state**

Add `AuthboundPidEntryInteractor` methods:

```kotlin
interface AuthboundPidEntryInteractor {
    suspend fun getEntryState(): AuthboundPidEntryState
    suspend fun snoozeHomePrompt()
}
```

Add Home state fields:

```kotlin
val shouldShowAuthboundPidEntry: Boolean = false,
val shouldShowAuthboundPidHomePrompt: Boolean = false,
```

Add Home event:

```kotlin
data object AuthboundPidPromoNotNowPressed : Event()
data object AuthboundPidAddOptionPressed : Event()
```

Add Dashboard side menu type:

```kotlin
AUTHBOUND_PID,
```

Add Documents state field and bottom-sheet event:

```kotlin
val shouldShowAuthboundPidEntry: Boolean = false
data object AuthboundPid : AddDocument()
```

- [x] **Step 4: Run Home ViewModel test and compile to catch constructor wiring**

Run: `./gradlew :dashboard-feature:test --tests "eu.europa.ec.dashboardfeature.ui.home.TestHomeViewModel"`
Expected: PASS after test updates and minimal implementation.

### Task 3: Compose Entry Points

**Files:**
- Modify: `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/home/HomeScreen.kt`
- Modify: `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/documents/list/DocumentsScreen.kt`
- Modify: `dashboard-feature/src/main/java/eu/europa/ec/dashboardfeature/ui/dashboard/SideMenuScreen.kt`
- Modify: `resources-logic/src/main/res/values/strings.xml`

- [x] **Step 1: Write or update UI-facing tests where available**

Run: `rg -n "HomeScreen|DocumentsScreen|SideMenuScreen" dashboard-feature/src/test`
Expected: If no Compose UI test harness exists, cover view model state and routing in Task 2 and verify compilation in Task 4.

- [x] **Step 2: Replace hero promo item with compact Home prompt**

In `Content`, render `AuthboundIdHomePrompt` below `HeroCredentialSection` only when `state.shouldShowAuthboundPidHomePrompt`.

```kotlin
if (state.shouldShowAuthboundPidHomePrompt) {
    AuthboundIdHomePrompt(
        onGetAuthboundIdClick = { onEventSend(Event.GetAuthboundIdPressed) },
        onNotNowClick = { onEventSend(Event.AuthboundPidPromoNotNowPressed) }
    )
}
```

- [x] **Step 3: Add hero carousel page dots**

Use `rememberLazyListState()` in `HeroCredentialSection`, remove the promo item from the `LazyRow`, and render dots only when `heroCredentials.size > 1`.

```kotlin
val selectedPage: Int by remember {
    derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, heroCredentials.lastIndex) }
}
if (heroCredentials.size > 1) {
    HeroCredentialPageIndicator(
        pageCount = heroCredentials.size,
        selectedPage = selectedPage
    )
}
```

- [x] **Step 4: Add Authbound PID option to Add Credential sheets**

Append the option when the state field is true:

```kotlin
if (state.shouldShowAuthboundPidEntry) {
    add(
        ModalOptionUi(
            title = stringResource(R.string.authboundpid_add_credential_option),
            leadingIcon = AppIcons.Verified,
            event = Event.BottomSheet.AddDocument.AuthboundPid
        )
    )
}
```

- [x] **Step 5: Add side menu quick action**

When `shouldShowAuthboundPidEntry` is true, `DashboardInteractor.getSideMenuOptions` inserts an enabled `AUTHBOUND_PID` action with item id `authboundpid`; `DashboardViewModel` maps it to `Effect.TriggerQuickAction("authboundpid")`.

### Task 4: Verification

**Files:**
- All changed source and test files.

- [x] **Step 1: Run focused dashboard tests**

Run: `./gradlew :dashboard-feature:test`
Expected: PASS.

- [x] **Step 2: Compile dashboard feature**

Run: `./gradlew :dashboard-feature:compileDevDebugKotlin`
Expected: PASS.

- [x] **Step 3: Inspect git diff**

Run: `git diff --stat && git diff -- dashboard-feature resources-logic docs/superpowers/plans`
Expected: Only Authbound PID entry point implementation, tests, strings, and this plan changed.
