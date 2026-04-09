# Google Play Alpha Legal Release Checklist

## Scope

This checklist covers the app-side and release-console work needed for the closed alpha legal/privacy rollout.

## Website Publication

- Publish wallet alpha terms at `https://www.authbound.io/wallet-alpha-terms`
- Update public privacy policy at `https://www.authbound.io/privacy`
- Publish public account deletion page at `https://www.authbound.io/delete-account`
- Confirm all three pages are reachable without authentication

## Backend Contract

- Return `legalAcceptance` in the authenticated profile/bootstrap response
- Populate:
  - `requiredTermsVersion`
  - `acceptedTermsVersion`
  - `acceptedTermsAt`
  - `requiredPrivacyVersion`
  - `acknowledgedPrivacyVersion`
  - `acknowledgedPrivacyAt`
- Implement authenticated write endpoint for legal acceptance recording
- Keep backend as the source of truth for current required versions

## Android App

- Force legal review after authentication and before profile completion, wallet activation, PIN setup, or dashboard access
- Re-prompt accepted users when required terms/privacy versions change
- Keep public legal links visible from login and sign-up
- Expose `Privacy & Data` in settings
- Show accepted legal versions and timestamps in settings
- Support in-app delete-account initiation with confirmation

## Google Play Console

- Add the public deletion page URL in the account deletion field
- Review and update Data safety answers to match the current app behavior
- Verify disclosures for camera, NFC, Bluetooth, location, notifications, and diagnostics
- Confirm store listing text does not overstate production readiness
- Confirm release track is closed alpha / invited testers only

## Final Verification

- New account hits legal gate immediately after successful auth
- Existing account with current accepted versions bypasses legal gate
- Existing account with outdated versions is forced to re-accept
- Legal acceptance write failure blocks progression
- Delete-account entry is discoverable both in-app and via public page

## Notes

- Actual website content publication and Play Console configuration happen outside this repository.
- This repo now contains the app flow, strings, routing, and tests needed for the Android side of the rollout.
