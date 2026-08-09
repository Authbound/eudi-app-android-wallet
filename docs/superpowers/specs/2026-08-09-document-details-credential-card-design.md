# Document Details: Real Credential Card + Advanced Claims

Date: 2026-08-09

## Goal

Document details should open as the **same real identity card as Home**, with technical claim data available on demand. Remove the upstream “hide sensitive content” pattern that empties the passport card by default and uses a different Present CTA.

## Problem

Screenshots and code show three related failures on document details:

1. **Empty identity card** — `hideSensitiveContent` defaults to `true`. When true, `toVisualCredentialConfig` strips holder name, nationality, birth date, expiry, and portrait, then falls back to the document title (e.g. “AUTHBOUND DIGITAL ID”) with no field row.
2. **Inconsistent Present CTA** — Home uses a navy `PresentIdBar`; details uses a flat primary `WrapButton`.
3. **Claim dump always on** — Full claim list (addresses, age attestations, technical attributes) sits below the card with only a blur toggle. The eye icon is not true security; it is a situational privacy aid that currently destroys the primary identity surface.

Home already shows real identity data on the credential card. Details should match that trust model for the card layer.

## Design Principles

- **Two layers of information**
  - Layer 1 — Credential card: human identity surface (always real).
  - Layer 2 — Document claims: technical / full attribute dump (collapsed by default).
- **One Present control** across Home and Details.
- **No fake security** — do not empty the passport card for “privacy.” Real device security remains biometrics / app lock, not an eye icon.

## UX Behavior

### Toolbar

- Keep: back, bookmark.
- Remove: visibility / eye action (`ChangeContentVisibility`).

### Hero (always visible)

1. **Passport credential card** filled from `IdentityCardData` (same source as Home and the presentation pass):
   - Holder name, portrait or ghost silhouette, nationality, date of birth, valid until, status, issuer.
   - Never strip these fields based on a hide-sensitive flag.
2. **Present ID bar** (shared component with Home):
   - QR icon · “Present ID” · “QR + NFC” · chevron.
   - Same navigation to proximity QR presentation for this document.
   - Hidden when document is revoked or not in issued state (same eligibility as today).

### Instances remaining

Keep existing credential-instances row / expandable re-issuance section as-is.

### Document claims (advanced)

- Default: **collapsed**.
- Control: expandable row under the hero (or under instances), e.g. “Show document claims” / “Hide document claims”.
- Expanded: existing claim list UI (`WrapListItems`), **without** blur / `hideSensitiveContent`.
- Collapsed: claim list not shown (no blur of empty structure).

### Issuer and delete

Unchanged: issuer section and delete document button remain below.

### Layout sketch

```
┌ Toolbar: back · bookmark ─────────────────────────┐
│ Title: document name                              │
│                                                   │
│ ┌─ Passport card (always real IdentityCardData) ┐ │
│ │ type · status · portrait · name · fields · iss│ │
│ └───────────────────────────────────────────────┘ │
│ ┌─ Present ID bar (shared with Home) ───────────┐ │
│ │ QR · Present ID · QR + NFC · >                │ │
│ └───────────────────────────────────────────────┘ │
│ · N/M instances remaining          More info      │
│                                                   │
│ ▸ Show document claims                            │
│   (full claim list when expanded)                 │
│                                                   │
│ ISSUER                                            │
│ [issuer card]                                     │
│                                                   │
│ [ Delete document ]                               │
└───────────────────────────────────────────────────┘
```

## Field density (card)

While making the card always real, fix truncation of dates on the passport layout used by Home and Details:

- **Row 1:** Nationality · Date of birth  
- **Row 2:** Valid until (full width; values must not ellipsis-truncate full `dd/MM/yyyy` dates)

Share / proximity pass field density is **out of scope** for this change.

## State Model

### Remove

| Item | Location |
|------|----------|
| `hideSensitiveContent` | `DocumentDetailsViewModel.State` (default `true` today) |
| `Event.ChangeContentVisibility` | ViewModel + screen |
| Toolbar visibility `ToolbarActionUi` | `DocumentDetailsScreen` |
| `hideSensitiveContent` parameters | `DocumentHero`, `DocumentDetails`, `toVisualCredentialConfig` |

### Add

| Item | Behavior |
|------|----------|
| `areDocumentClaimsExpanded: Boolean = false` | Claims section collapsed by default |
| `Event.ToggleDocumentClaimsExpanded` | Flips the expand flag |

No persistence of expand state across visits (session-only UI state is enough).

## Implementation Shape

### Shared Present control

Extract Home’s private `PresentIdBar` into `ui-logic` (e.g. `PresentIdBar.kt` under wrap components) so Home and Document Details use one implementation (navy surface, glow border, haptic, a11y labels).

### Details screen

- Map `identityCardData` into `VisualCredentialConfig` always (no hide gating).
- Replace primary Present button with shared `PresentIdBar`.
- Wrap claim list in expand/collapse UI driven by `areDocumentClaimsExpanded`.
- Pass `hideSensitiveContent = false` nowhere; delete the flag path on this screen.

### Card layout

Update passport field layout in `VisualCredentialCard` / shared identity elements so nationality + DOB sit on one row and valid-until on a second full-width row.

### Tests

- Update `TestDocumentDetailsViewModel` / screen-related tests: remove hide-sensitive cases; cover claims expand toggle and always-visible identity mapping assumptions where tested.
- Home Present ID navigation tests remain valid after extraction if the composable is pure UI.

## Out of Scope

- Biometric gate to reveal claims.
- Mask / `••••` privacy mode on the card.
- Redesign of share / proximity presentation pass.
- Redesign of claim list item chrome beyond expand/collapse.
- Changing issuer, bookmark, revoke banner, or delete flows.

## Success Criteria

1. Opening document details shows a real identity card (holder name and identity fields when claims exist), never the document title as a stand-in for a hidden name.
2. No eye icon on the details toolbar.
3. Full claim list is hidden until the user expands “Show document claims.”
4. Present control matches Home visually and starts the same present-ID navigation for the document.
5. Passport card dates are readable without mid-value ellipsis on normal phone widths.
6. Unit tests for details ViewModel/screen state updated and green.

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Users miss full claims | Clear expand control with “document claims” wording under the hero |
| Shoulder surfing on card PII | Accept parity with Home; optional future mask mode is separate |
| Regression on Present navigation | Keep existing `PresentIdPressed` → proximity QR path; only swap UI chrome |
| Shared `PresentIdBar` placement | Put in `ui-logic` so feature modules do not depend on each other |

## Open Follow-ups (not this work)

- Share/QR pass field density and truncation.
- Optional card-level mask mode for situational privacy (structure kept, values masked).
