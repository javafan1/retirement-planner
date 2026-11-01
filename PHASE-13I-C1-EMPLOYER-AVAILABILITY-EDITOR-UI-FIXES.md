# Phase 13I-C1 — Employer Availability Editor UI Fixes

## Summary

The employer availability form now remains in a bounded, vertically scrollable content area while the DialogPane's Save and Cancel action bar stays fixed outside that area. The form uses the monitor's JavaFX visual bounds to limit dialog dimensions and permits the form to grow naturally when it fits. First eligible year remains an optional year-only text value and now requires four digits in the range `0001`–`9999`; blank retains the existing indefinitely-unavailable meaning.

No Roth-conversion eligibility, projection, persistence schema, fingerprint, or account lifecycle behavior was changed.

## Findings and correction

`AccountDialog` previously installed its `GridPane` directly as the dialog content and called `sizeToScene()` after optional controls changed. With the expanded employer availability fields, the complete form could make the dialog taller than the available display area. Although JavaFX places the dialog buttons in a separate DialogPane button bar, the unbounded window could put that bar below the visible screen.

The dialog now wraps its form in a `ScrollPane` only after any guided-wizard wrapper has been installed. The dialog action bar therefore remains outside the scroll area. The content viewport uses natural preferred height up to the active display's available visual height, and scrolls only when needed. The dialog window and content width are also constrained to that screen. The owner window is used to select the relevant display, with the dialog's display and primary display as fallbacks.

In the current source, the first eligible year was already represented by a `TextField`, not a calendar `DatePicker`. It accepted general integer text and relied on the domain constructor for range validation, so the UI did not make the year-only contract clear and malformed or non-four-digit values did not have a specific field-level message. The existing text field is retained, with a YYYY prompt, accessible help, and validation for exactly four digits and a supported UI range of 0001 through 9999. A blank value is still represented as `null`. Changing availability choices hides or shows the year control without clearing its draft value.

## Changes

- `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/AccountDialog.java`
  - Adds a scrollable form viewport, leaving the DialogPane action bar fixed.
  - Constrains dialog size using the owning display's visual bounds and recalculates after optional sections change.
  - Keeps the original form controls, validation, and result conversion intact.
- `src/main/java/com/daviddunn/retirementplanner/ui/controls/EmployerRothAvailabilityEditor.java`
  - Defines the year-only YYYY input, accessible help, and clear validation messages.
  - Accepts only four-digit years from 0001 to 9999 when a year is entered; blank remains allowed.
  - Notifies the dialog when the controls expand/collapse, without discarding the entered year.
- `src/test/java/com/daviddunn/retirementplanner/ui/views/RothAvailabilityUiTest.java`
  - Covers Traditional 401(k) and 403(b), standard and guided account dialogs, availability expansion/collapse, fixed action visibility and bounds, screen bounds, valid/invalid/blank years, draft retention, and cancel behavior.
  - Generates Light/Dark previews at 1180×820 and 900×650.
- `PHASE-13I-C1-EMPLOYER-AVAILABILITY-EDITOR-UI-FIXES.md`

The AccountDialog and RothAvailabilityUiTest were already modified/untracked as part of the preserved Phase 13I work before this correction. Their current diffs include earlier work; this report describes only the Phase 13I-C1 additions. The initial working tree also contained extensive uncommitted Phase 13I-A/B/C and Phase 14B3A changes, plus `.idea/encodings.xml`. Those files were preserved. The index was empty at the start; no files were staged, committed, pushed, reset, cleaned, or discarded.

## Verification

Focused command used IntelliJ's bundled Maven, the repository-local `.codex-m2` repository, and a 4 GiB test heap:

```text
mvn -Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository -DargLine=-Xmx4g -Dtest=RothAvailabilityUiTest,EmployerRothAvailabilityTest test
```

Result: **45 tests, 0 failures, 0 errors, 0 skipped**. This includes 17 JavaFX availability UI tests and 28 domain/persistence compatibility tests.

Full regression command:

```text
mvn -Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository -DargLine=-Xmx4g test
```

Result: **3,914 tests, 0 failures, 0 errors, 46 skipped**; Maven reported `BUILD SUCCESS`. The skipped tests are existing opt-in visual/PDF preview tests. Existing Social Security, projection, Roth conversion, Monte Carlo and persistence suites passed without golden-value changes.

`git diff --check` completed without whitespace errors. Git printed only its existing LF-to-CRLF advisories for previously modified files.

## Visual verification

Actual JavaFX DialogPane previews were generated and inspected in both themes. The expanded 401(k) editor shows the year-only field and keeps both action buttons visible at 1180×820 and 900×650 test window sizes. The Light preview has the standard light dialog surface; the Dark preview has readable high-contrast labels and fields. The preview images are under `target/roth-availability/previews/`:

- `LIGHT-1180-account-expanded.png`
- `DARK-1180-account-expanded.png`
- `LIGHT-900-account-expanded.png`
- `DARK-900-account-expanded.png`

These are JavaFX-rendered test previews on the available Windows environment. Automated assertions verify that changing to Available unmanages the year field, changing back restores it, the entered year remains in the draft, and the footer remains within the dialog pane. Native 125% and 150% display scaling was not changed or independently exercised in this run.

## Native Windows acceptance checklist

- Open Add Account and Edit Account for Traditional 401(k) and Traditional 403(b).
- At 100%, 125%, and 150% scaling, select Not Yet Available and confirm the year controls expand while Save and Cancel remain visible and clickable.
- Repeat on a compact display and confirm the form scrolls while the action footer remains fixed.
- On a larger display, confirm the form fits without unnecessary scrolling.
- Enter valid years (for example, 2032), malformed/non-four-digit text, `0000`, and blank; verify the respective validation and blank-year behavior.
- Switch between Available and Not Yet Available and confirm a draft year is retained.
- Cancel an edit and verify the original account remains unchanged.
- Verify focus order, keyboard activation, tooltip/accessibility help, and owner-window focus after close.
- Confirm ordinary Save/Load preserves the unchanged `rothConversionAvailability` JSON contract and existing conversion behavior.

Native scaling acceptance remains for Windows review. No Phase 13I-D work was started.
