# AGENTS.md

This repository contains Authbound's EUDI Android Wallet implementation.
Use this guide when operating as an agentic coding assistant.

## Where to find documentation
- Project docs are available at `~/dev/authbound-portal/docs`.
  These contain design docs for architecture and development.
- Internal repo docs: `wiki/how_to_build.md`, `wiki/configuration.md`.
- Cursor rules live in `.cursor/rules/*.mdc` and must be followed.

## Build and run
- Default debug build: `./gradlew assembleDebug`
- Dev flavor builds:
  - `./gradlew assembleDevDebug`
  - `./gradlew assembleDevRelease`
- Demo flavor builds:
  - `./gradlew assembleDemoDebug`
  - `./gradlew assembleDemoRelease`
- Install dev debug on device/emulator: `./gradlew installDevDebug`
- Clean: `./gradlew clean`

## Tests
- Run all unit tests: `./gradlew test`
- Run module tests:
  - `./gradlew :business-logic:test`
  - `./gradlew :authentication-logic:test`
- Run a single test class (JUnit/JUnit5):
  - `./gradlew :module-name:test --tests "com.example.ClassName"`
- Run a single test method:
  - `./gradlew :module-name:test --tests "com.example.ClassName.methodName"`
- Run instrumentation tests: `./gradlew connectedAndroidTest`
- Coverage report: `./gradlew koverHtmlReport`

## Lint and quality
- Android lint: `./gradlew lint`
- Debug lint report: `./gradlew lintDebug`
- Dependency vulnerability scan: `./gradlew dependencyCheckAnalyze`
- Baseline profiles: `./gradlew generateBaselineProfile`

## Build environment requirements
- JDK 17 required (JDK 21+ often fails).
- Gradle via wrapper `./gradlew` (AGP 8.13, Kotlin 2.2.21).
- Android SDK 34+.
- If needed, set `JAVA_HOME` to JDK 17 or Android Studio JBR.

## Architecture and module rules (from Cursor rules)
- Clean Architecture with presentation, domain, and data layers.
- Feature modules depend on logic modules; logic modules depend on core.
- No circular dependencies; core should have minimal dependencies.
- MVI pattern in ViewModels: `State`, `Event`, `Effect`.
- Use Interactors (feature), Use Cases (logic), Controllers (system-level).
- DI uses Koin with `@Module`, `@Single`, `@Factory` annotations.

## Kotlin code style (from Cursor rules)
- Use English for all code and docs.
- Always declare types for variables, params, and return values.
- Prefer immutability (`val`), data classes for data.
- Functions: short, single-purpose (<20 lines), start with verbs.
- Boolean naming: `isX`, `hasX`, `canX`.
- Avoid deep nesting; prefer early returns and small helpers.
- Prefer composition over inheritance; follow SOLID.
- Avoid `any` types; create explicit types instead.
- No blank lines inside functions.

## Naming conventions
- Classes: PascalCase.
- Functions/variables: camelCase.
- Files/directories: underscore_case (per Cursor rules).
- Constants/environment variables: UPPERCASE.
- Use complete words; avoid non-standard abbreviations.

## Imports and formatting
- Keep imports minimal and grouped by package.
- Remove unused imports.
- Prefer Kotlin idioms and standard library operators.
- Keep formatting consistent with existing files; don’t reformat unrelated code.

## Error handling
- Use exceptions only for unexpected errors.
- When catching exceptions, either:
  - Fix an expected problem,
  - Add context,
  - Otherwise let global handlers handle it.
- Prefer sealed error types or `Result` for domain failures.
- Never log sensitive data (security requirement).

## Security and compliance (Cursor rules)
- Follow EUDI-ARF and GDPR requirements.
- Use hardware-backed keystore for crypto.
- Use BIOMETRIC_STRONG where required.
- Enforce certificate pinning for network traffic.
- Audit log security-critical events.

## UI/UX (Cursor rules)
- Material 3 components.
- Compose-first UI (migration from XML in progress).
- Accessibility: WCAG 2.1 AA, 48dp touch targets, proper semantics.
- Smooth animations; use lazy lists/grids for performance.

## Backend integration
- Backend is a separate repository.
- Ask the developer for API contracts, endpoints, and data models.
- Don’t assume network behavior; consult existing network-logic patterns.

## Testing guidelines
- Arrange-Act-Assert for unit tests.
- Given-When-Then for acceptance tests.
- Use MockK + Truth; coroutines test tools for Flow/suspend.
- Prefer test doubles for expensive dependencies.

## Additional references
- `.cursor/rules/android-development-guidelines.mdc`
- `.cursor/rules/android-technical-stack.mdc`
- `.cursor/rules/project-architecture.mdc`
- `.cursor/rules/security-compliance.mdc`
- `.cursor/rules/ui-ux-patterns.mdc`
- `.cursor/rules/eu-identity-wallet-context.mdc`
