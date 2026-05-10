# Authbound PID Entry Points Design

Date: 2026-05-10

## Goal

Users who already have one or more credentials must still be able to discover and create an Authbound PID. The Authbound PID entry point should disappear only after the wallet contains the Authbound PID itself.

## Current Behavior

The Home screen already has an Authbound ID promotional card, but it is appended as a full-width item in the hero credential `LazyRow`. When a user has existing hero credentials, the promo is effectively a second page and can be missed. The side menu currently has no Authbound PID action, and the Add Credential bottom sheet only offers list and QR options.

## Eligibility Rules

Show Authbound PID entry points until `hasAuthboundPid` is true.

`hasAuthboundPid` is true only when a wallet document is both PID-like and Authbound-issued.

PID-like means one of:

- `DocumentIdentifier.MdocPid`
- `DocumentIdentifier.SdJwtPid`
- known Authbound PID format `urn:vc:authbound:pid:1.0`

Authbound-issued means issuer metadata contains `authbound`, case-insensitive.

This avoids hiding the entry point for unrelated credentials or unrelated Authbound-issued documents.

## UX Behavior

Use three entry points while Authbound PID is eligible:

1. Home shows a compact Authbound PID prompt under the hero section.
2. Side menu shows a neutral `Get Authbound ID` quick action.
3. Add Credential bottom sheets show `Get Authbound ID` as a third option.

The Home prompt includes a `Not now` action. Tapping it snoozes only the Home prompt for 30 days. The side menu and Add Credential entry points remain visible during the snooze. When the Authbound PID exists, all Authbound PID entry points are hidden.

The hero carousel should contain actual credentials only. It should not include the Authbound PID promotional card. Add page dots when there is more than one hero credential so users can tell the hero section is pageable.

## State Model

Add Home/dashboard state derived from wallet documents and user-scoped preferences:

- `hasAuthboundPid`: whether the wallet already contains an Authbound-issued PID-like credential.
- `shouldShowAuthboundPidEntry`: `true` when `hasAuthboundPid` is false.
- `shouldShowHomeAuthboundPidPromo`: `true` when entry is eligible and the Home snooze expiry is absent or expired.

Store the Home snooze expiry in `PrefsControllerV2` as an epoch millis timestamp. On `Not now`, set it to now plus 30 days. Use the existing user-scoped preference isolation instead of adding new persistence infrastructure.

## Implementation Shape

Keep the implementation scoped mostly to `dashboard-feature`.

Extend `HomeInteractor` to compute Authbound PID eligibility from wallet documents and `PrefsControllerV2`. It should expose enough state for `HomeViewModel` to render the Home prompt and to hide all entry points once Authbound PID exists.

Extend `HomeViewModel.State` with Authbound PID entry visibility. Add an event for dismissing the Home prompt and route it to the interactor preference write. Keep `GetAuthboundIdPressed` navigation to `AuthboundPidScreens.Intro`.

Update `HomeScreen` by replacing the full-width `AuthboundIdPromoCard` hero carousel item with a compact prompt rendered under `HeroCredentialSection`. The compact prompt should have a primary action to start Authbound PID creation and a secondary `Not now` action.

Update `HeroCredentialSection` so it renders only real hero credentials. Add page dots when `heroCredentials.size > 1`.

Extend side menu models with an `AUTHBOUND_PID` item. Route it through `DashboardViewModel` to the existing Authbound PID navigation path or an equivalent dashboard effect.

Update dashboard Add Credential bottom sheets to include a third `Get Authbound ID` option when `shouldShowAuthboundPidEntry` is true. This includes the Home surface and the Documents tab surface unless implementation confirms they share one component. Selecting it navigates to `AuthboundPidScreens.Intro`.

## Testing

Add focused unit tests around state and routing:

- Authbound PID entry is visible when the wallet has no credentials.
- Authbound PID entry is visible when the wallet has unrelated credentials.
- Authbound PID entry is hidden when the wallet has an Authbound-issued PID-like credential.
- The Home prompt is hidden during an active snooze.
- The side menu and Add Credential entries remain available during an active Home snooze.
- Snoozing writes an expiry around now plus 30 days.
- Hero page dots are shown only when there are multiple real hero credentials.

Run at least:

- `./gradlew :dashboard-feature:test`
- `./gradlew :dashboard-feature:compileDevDebugKotlin`

## Out Of Scope

- Changing the Authbound PID creation flow itself.
- Changing backend contracts for PID verification or issuance.
- Building a new Add Credentials screen.
- Changing credential issuance list filtering beyond the Add Credential bottom-sheet entry point.

## Risks And Notes

Authbound PID detection depends partly on issuer metadata and the known Authbound PID format. If backend or issuer metadata changes, the detection helper should be updated in one place.

The `.superpowers/` visual companion files generated during planning are not part of this spec and should not be staged.
