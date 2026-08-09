# Document Details Credential Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Document details shows a real identity card like Home, a shared Present ID bar, no eye-toggle privacy empty-card, and full claims collapsed under an advanced expand control.

**Architecture:** Extract `PresentIdBar` to `ui-logic`. Strip `hideSensitiveContent` from details MVI/UI. Always map `IdentityCardData` into the passport card. Claims expand via `areDocumentClaimsExpanded`. Fix passport field layout to two rows so dates do not truncate.

**Tech Stack:** Kotlin, Jetpack Compose, MVI ViewModels, JUnit + MockK + Truth (existing test patterns).

**Spec:** `docs/superpowers/specs/2026-08-09-document-details-credential-card-design.md`

## Global Constraints

- Always-real credential card on details (never strip identity fields for privacy).
- Remove eye / `hideSensitiveContent` from document details only.
- Claims default collapsed; expand control required.
- Shared Present ID bar for Home and Details.
- Passport fields: row1 nationality + DOB; row2 valid until full width.
- Out of scope: share/QR pass redesign, biometrics gate, mask mode.

---

### Task 1: Extract shared PresentIdBar

**Files:**
- Create: `ui-logic/src/main/java/eu/europa/ec/uilogic/component/wrap/PresentIdBar.kt`
- Modify: `dashboard-feature/.../ui/home/HomeScreen.kt` (replace private PresentIdBar with import)
- Modify: (later task uses it on details)

**Interfaces:**
- Produces: `@Composable fun PresentIdBar(onClick: () -> Unit, modifier: Modifier = Modifier)`

- [ ] Copy Home’s `PresentIdBar` into ui-logic with same visuals (navy surface, glow border, haptic, semantics).
- [ ] Replace Home private composable with the shared one.
- [ ] Compile ui-logic + dashboard-feature.

---

### Task 2: Passport field layout (two rows)

**Files:**
- Modify: `ui-logic/.../wrap/VisualCredentialCard.kt` (`PassportCardContent` field row)

- [ ] Change field layout to nationality + DOB on row 1; valid until full-width on row 2.
- [ ] Prefer full date display (no mid-date ellipsis when space allows — maxLines 1 OK if full width).
- [ ] Compile ui-logic.

---

### Task 3: Details ViewModel state (remove hide, add claims expand)

**Files:**
- Modify: `dashboard-feature/.../documents/detail/DocumentDetailsViewModel.kt`
- Modify: `dashboard-feature/src/test/.../TestDocumentDetailsViewModel.kt` (if present)

- [ ] Remove `hideSensitiveContent` and `ChangeContentVisibility`.
- [ ] Add `areDocumentClaimsExpanded: Boolean = false` and `ToggleDocumentClaimsExpanded`.
- [ ] Update unit tests; run `:dashboard-feature:testDevDebugUnitTest --tests "*DocumentDetails*"`.

---

### Task 4: Details screen UI

**Files:**
- Modify: `dashboard-feature/.../documents/detail/DocumentDetailsScreen.kt`
- Modify: `resources-logic/src/main/res/values/strings.xml` (show/hide claims strings)

- [ ] Remove eye toolbar action.
- [ ] Always map identity card data (no hide gating).
- [ ] Use shared `PresentIdBar` instead of primary WrapButton.
- [ ] Collapse claims behind expand toggle; expanded shows `WrapListItems` without blur.
- [ ] Compile + run details tests.

---

### Task 5: Verify and commit

- [ ] Compile dashboard + ui-logic + proximity (smoke).
- [ ] Run relevant unit tests.
- [ ] Commit implementation (not ConfigLogicImpl local env change).
