# Phase 13I-C — Automatic Modeled Roth IRA Destinations

## Executive summary

Enabled Roth conversion settings now create a zero-balance modeled Roth IRA only when the user explicitly applies the settings and confirms the required owner or owners. Both Roth Conversion surfaces use the same MainWindow transaction. Existing same-owner Roth IRAs are reused; Roth 401(k), inherited Roth IRA, and another owner's Roth IRA do not satisfy the destination requirement.

The creation and request update are prepared and applied as one in-memory transaction. Primary is processed before Spouse. A failed account addition rolls back prior additions and restores the original conversion request. Successful changes produce one normal dirty/revision notification and refresh both configuration views and Accounts. Ordinary Save does not create a destination: if changed enabled settings need one, the user is told to use Apply first.

No account IDs, origin fields, plan or retained-snapshot schema changes, historical rewrites, or calculation-rule changes were introduced.

## Architecture findings and transaction boundary

The account portfolio is the authoritative persisted account collection. `AccountPortfolio.addAccount` and `removeAccount` are its existing mutation operations, and `AccountsView.load` reflects that collection. A modeled destination can therefore use an ordinary `RothIRA` with its established JSON and projection behavior.

`RothConversionView` previously wrote the request before its callback, while `ResultsSummaryView` delegated to a separate request callback. The shared boundary is now `MainWindow.applyRothConversionRequest`: it validates through `RothConversionConfiguration`, determines all missing destinations through `RothConversionDestinationService`, asks for one confirmation, commits accounts and request, and only then marks the plan modified. This keeps the user confirmation before the first mutation and avoids a broad controller lifecycle change.

`RothConversionDestinationService.apply` prepares all required accounts before portfolio mutation and restores the request/removes any accounts already added if a runtime failure occurs. A test portfolio that fails on the Spouse addition verifies that the Primary addition is rolled back too.

## Owner, availability, and destination rules

The service evaluates only present `PRIMARY` and `SPOUSE` owners, in that order. An owner needs a destination only when that owner has a supported pretax source usable during the enabled schedule and within the modeled projection horizon. Account balance does not determine this eligibility, so a zero-balance supported source can still be configured for future use.

Traditional and rollover IRAs are usable source types under the Phase 13I-A/B policy. Traditional 401(k)/403(b) accounts follow Phase 13I-B availability: legacy UNKNOWN retains its legacy execution assumption; AVAILABLE is usable; NOT_YET_AVAILABLE with a year can justify a destination if the schedule overlaps that year and the modeled horizon; unavailable throughout the horizon does not. If another usable source exists for the owner, it remains eligible. Owners absent from the household are skipped.

An existing ordinary Roth IRA owned by the same person is reused without modification or reordering. When multiple ordinary same-owner Roth IRAs exist, no account is added and the converter's existing first-in-portfolio selection continues unchanged. If none exists, the service creates exactly one `RothIRA` with opening balance `BigDecimal.ZERO` and name `Primary Roth IRA (Modeled)` or `Spouse Roth IRA (Modeled)`. Name collisions are resolved by appending a deterministic numeric suffix, considering all account names without regard to case. No cross-owner transfer or hidden balance is introduced.

## Confirmation, cancellation, and save behavior

When one or both destinations are needed, an owner-modal JavaFX dialog names the affected owner or owners and states that the account starts at $0, exists only in the retirement model, does not open a real financial account, and can later be edited or removed in Accounts. One dialog covers both owners. Cancel, Escape, or window close returns without changing the portfolio, request, dirty state, projection state, or the view's draft text. When no account is needed, Apply proceeds without confirmation.

Both the Roth Conversion tab and Results Summary use the same transactional handler. On success, MainWindow calls `markModified` once and refreshes the application views. During an ordinary plan Save, the Roth view validates and preserves an unchanged legacy schedule. If a changed enabled draft requires a new destination, Save reports that the user must apply the settings first; it neither prompts nor creates an account. Applying a disabled request never creates a destination. Opening the screen, checking its enable box, loading, Save As, projection, analyzer, PDF, history, and baseline/swap operations have no creation hook.

## Persistence, fingerprints, and financial invariants

The new account is an ordinary serialized `RothIRA`, so Save, Load, and Save As use current account serialization and configured-input digests. The account uses no new metadata and does not require a retained-account schema change. Existing history and accepted results remain as stored; this feature does not rewrite them. Baseline/swap identity checks continue to compare the complete configured documents.

Financial execution was not modified. Once explicitly added, the account behaves like a manually configured ordinary Roth IRA with the same owner, name, and zero balance. A deterministic projection comparison and seeded Monte Carlo comparison verify equal outcomes for automatically and manually created equivalent destinations. Existing projection and retained snapshot tests continue to verify restoration and swap behavior.

## Files changed for Phase 13I-C

New production files:

- `src/main/java/com/daviddunn/retirementplanner/domain/roth/RothConversionDestinationService.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/RothDestinationCreationDialog.java`

Modified production files:

- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/RothConversionView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java`

New test file:

- `src/test/java/com/daviddunn/retirementplanner/domain/roth/RothConversionDestinationServiceTest.java`

Modified test files:

- `src/test/java/com/daviddunn/retirementplanner/ui/views/RothAvailabilityUiTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/RothConversionMainWindowTest.java`

These files are part of an existing uncommitted Phase 13I-A/B and Phase 14 working tree. Their prior changes were preserved; the list above identifies only the files extended for Phase 13I-C.

## Test and visual verification

Focused verification used the IntelliJ-bundled Maven, the project-local `.codex-m2\repository`, and a 4 GiB test heap. The selected classes ran **124 tests: 124 passed, 0 failures, 0 errors, 0 skips**. Coverage includes primary/spouse and couple creation, single-person plans, existing and unsupported destinations, collision-safe names, zero balances, legacy/future/unavailable employer sources, validation rejection, disabled requests, rollback on partial addition, idempotence, confirmation cancel, both Apply surfaces, no creation during ordinary Save, Save/Save As, retained snapshot schema, projection swap regressions, and matching deterministic/Monte Carlo results against a manual destination.

The complete Maven suite passed: **3,908 tests, 3,862 passed, 0 failures, 0 errors, 46 skipped**. The existing property-gated skips were retained. The full log is `target/phase13ic/full.log`.

Actual JavaFX confirmation-dialog previews were generated and inspected in both themes:

- `target/roth-availability/previews/LIGHT-modeled-destination-confirmation.png`
- `target/roth-availability/previews/DARK-modeled-destination-confirmation.png`

The owner names, $0 opening balance, model-only disclosure, edit/remove note, and both actions are visible and readable. Automated renders are not native Windows acceptance.

`git diff --check` passed. Existing line-ending conversion notices remain. No files were staged or committed; the branch and HEAD were not changed.

## Native Windows acceptance checklist

1. With a supported source and no same-owner Roth IRA, apply settings from each Roth configuration surface. Confirm that one modal names the owner, is centered on the main window, and offers Create Modeled Account and Cancel.
2. Cancel using the button, Escape, and window close. Verify the plan, request, title/dirty marker, accounts list, projection, and typed draft remain unchanged.
3. Confirm creation for Primary, Spouse, and both owners. Verify $0 opening balances, deterministic names, correct ownership, one dirty-state transition, and refresh of both Roth surfaces and Accounts.
4. Confirm an existing ordinary same-owner Roth IRA suppresses the dialog and is reused. Check multiple Roth IRAs, wrong-owner Roth, Roth 401(k), and inherited Roth IRA cases.
5. Verify zero-balance IRA sources and future-eligible employer sources; verify an employer source unavailable throughout the horizon does not alone create a destination.
6. Attempt ordinary Save/Save As with a changed enabled draft requiring a destination. Verify it requests Apply first and creates no account. Verify unchanged legacy schedules and disabled requests remain saveable without creating accounts.
7. Save, reopen, Save As, and swap plans. Confirm account owner/name/balance and configured-input identity survive, while previously accepted historical results remain unchanged.
8. Check Light and Dark themes, supported window sizes, Windows display scaling, keyboard focus order, modal ownership, and focus restoration after confirmation.

## Limitations and phase boundary

The modeled Roth IRA is a planning account, not a custodial account. Account creation does not establish legal distribution eligibility, Roth distribution qualification, tax basis, or Roth five-year rules. Those existing model limitations remain.

Phase 13I-C is complete for review and native Windows acceptance. No Phase 13I-D work has begun.
