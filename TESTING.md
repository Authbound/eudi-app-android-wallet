# UI Testing

## Scope
The auth-gate smoke suite lives in [`/Users/lassi/dev/eudi-app-android-wallet/app/src/androidTest/java/eu/europa/ec/euidi/test/AuthGateSmokeTest.kt`](/Users/lassi/dev/eudi-app-android-wallet/app/src/androidTest/java/eu/europa/ec/euidi/test/AuthGateSmokeTest.kt). It boots the real app with a test `Application`, overrides Koin edges with scenario-driven fakes, and verifies startup routing plus the PIN gate journeys.

Key test infrastructure:
- `AuthTestRunner` swaps in `AuthTestApplication` only when `auth_test_application=true` is passed as an instrumentation runner argument.
- `AuthTestApplication` enables Koin overrides and disables reporting/background work that would add noise to instrumentation.
- `AuthScenarioDriver` controls auth, onboarding, wallet activation, PIN, and local-unlock state without live services.
- `AuthScenarioState.walletSetupAttemptOutcomes` scripts per-attempt wallet setup results so smoke tests can exercise retry, destructive recovery, and stuck-loading cases.

## Prerequisites
- JDK 17
- Android SDK 34+
- A working emulator or Gradle managed devices

## Local Commands
- Compile androidTest sources: `./gradlew :app:compileDevDebugAndroidTestKotlin`
- Run the fast `MainActivity` support regressions: `./gradlew :assembly-logic:testDevDebugUnitTest --tests "eu.europa.ec.assemblylogic.ui.TestMainActivity"`
- Run the blocking smoke device group: `./gradlew :app:smokeGroupDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.auth_test_application=true`
- Run the expanded nightly device group: `./gradlew :app:nightlyGroupDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.auth_test_application=true`

If you run managed devices in CI or on headless Linux, use:

```bash
./gradlew :app:smokeGroupDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.auth_test_application=true \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

## Managed Device Groups
- `smoke`: one phone emulator for PR-blocking auth smoke coverage
- `nightly`: phone + tablet + latest repo-standard phone coverage
- `ci`: compatibility alias that currently matches `smoke`

## Current Smoke Coverage
- Cold start unauthenticated -> login
- Login with valid credentials -> auth observer -> profile completion
- Authenticated but profile incomplete -> profile completion
- Authenticated but device security missing -> device security required
- Device security fixed in-session -> retry -> wallet setup
- Authenticated and wallet not activated -> wallet setup
- Wallet setup pending -> loading state stays visible and blocks forward navigation
- Wallet setup success -> PIN create
- Wallet setup retryable failure -> retry -> PIN create
- Wallet setup permanent failure -> delete wallet -> login
- Wallet setup pending -> back dismiss -> remain on setup
- Wallet setup pending -> back confirm -> login
- Wallet setup pending -> activity recreate -> remain on setup
- Activated without PIN -> create PIN -> success -> dashboard
- Returning locked user -> verify PIN -> dashboard
- Wrong PIN -> error shown, no dashboard navigation
- Background/resume after local unlock expiry -> PIN required again
- Cold start with auth callback deep link -> no auth bypass
- Cold start with malformed deep link -> no auth bypass
- Returning locked user with auth callback deep link -> PIN required before any dashboard access

## Current Fast Support Coverage
- `MainActivity` does not re-check lock state on a fresh start
- `MainActivity` does not restart when returning from background inside the local unlock TTL
- `MainActivity` restarts cleanly and preserves the pending deep link when returning from background after local unlock expiry
