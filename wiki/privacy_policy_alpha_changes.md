# Privacy Policy Changes For Wallet Alpha

## Purpose

This document captures the wallet-alpha privacy updates that should be reflected on the public privacy page at `https://www.authbound.io/privacy`.

## Planned Privacy Version

- Version string exposed by backend: `privacy-2026-04-08`
- Updated date shown in-app: `April 8, 2026`

## Required Updates

The public privacy policy should explicitly cover the Android wallet alpha and describe:

1. Scope
   Closed alpha wallet users, invited testers, and related support interactions.
2. Data categories
   Account identifiers, profile data, wallet activation data, legal acceptance records, diagnostics, and support metadata actually collected by Authbound services.
3. On-device vs server-side data
   Clarify which secrets, local unlock material, and document artifacts remain on-device versus which account/profile records reach Authbound backend services.
4. Purpose of processing
   Authentication, profile setup, wallet activation, legal compliance, diagnostics, abuse prevention, and support.
5. Retention
   How long Authbound retains account records, diagnostics, and deletion/audit records for alpha users.
6. Account deletion
   Explain the in-app deletion path plus the public deletion page at `https://www.authbound.io/delete-account`.
7. User rights
   Contact and process for deletion, access, correction, or privacy requests.
8. International transfers and subprocessors
   If applicable, describe them accurately.

## Android App Alignment

This app release now exposes:

- legal/privacy review links from login
- a mandatory post-auth legal gate
- a `Privacy & Data` settings screen
- an in-app delete-account initiation flow

The published privacy policy should stay aligned with those shipped user-facing behaviors and with the Play Console Data safety answers.

## Publication Notes

- Final wording requires legal review.
- The public page must remain stable and unauthenticated.
- If the privacy version changes, backend `requiredPrivacyVersion` must be updated to trigger re-acknowledgement in the app.
