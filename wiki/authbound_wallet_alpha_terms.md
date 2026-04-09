# Authbound Wallet Alpha Terms

## Purpose

This document defines the wallet-specific terms that should be published for the closed alpha release. It is separate from the main website terms at `https://www.authbound.io/terms`, which remain website-wide and are not sufficient as the wallet contract.

## Planned Public URL

- `https://www.authbound.io/terms`

## Versioning

- Initial version for this app release: `wallet-alpha-2026-04-08`
- Published/updated date shown in-app: `April 8, 2026`
- Backend `legalAcceptance.requiredTermsVersion` should match the published version string exactly.

## Required Clauses

The public terms page should include at least the following sections:

1. Eligibility
   Invite-only closed alpha access. Authbound may revoke access at any time.
2. Pre-release status
   The wallet is experimental, incomplete, and may contain bugs, interruptions, or breaking changes.
3. No service commitment
   No uptime, availability, support, or compatibility commitment for the alpha.
4. Changes and removal
   Features, flows, APIs, and stored alpha data may change or be removed without notice.
5. No critical reliance
   Users must not rely on the alpha for emergency, recovery, production, legal, or regulatory workflows.
6. Security and device responsibility
   Users are responsible for using a supported device, device lock, and keeping access credentials private.
7. Suspension and termination
   Authbound may suspend or terminate access for security, abuse, compliance, or product reasons.
8. Feedback license
   Feedback may be used by Authbound without restriction or compensation.
9. Warranty disclaimer
   Provide the alpha on an "as is" and "as available" basis.
10. Liability limitation
    Limit indirect, consequential, incidental, and production-reliance damages to the fullest extent permitted by law.

## In-App Acceptance Contract

The Android app now requires acceptance before the user can proceed past authenticated startup when:

- no terms acceptance has been recorded, or
- `acceptedTermsVersion != requiredTermsVersion`, or
- `acknowledgedPrivacyVersion != requiredPrivacyVersion`

The app records acceptance through the authenticated backend endpoint and stores the last known accepted snapshot locally as a fallback cache.

## Publication Notes

- Final wording requires counsel review.
- The website page must be public and reachable without authentication.
- If the version changes, the backend must return the new required version so the app can force re-acceptance.
