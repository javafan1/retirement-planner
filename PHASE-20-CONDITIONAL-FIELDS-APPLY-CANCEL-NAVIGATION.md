# Phase 20 — Conditional Fields and Apply/Cancel Navigation

## Executive summary

Implemented paired optional entry for the Social Security benefit reduction assumption and consistent Results Summary routing for successful Apply and Cancel actions on workspace screens that already have reversible screen-level drafts. The existing `EconomicAssumptions` defaults remain 0% / 2032. No domain, calculation, persistence, projection identity, analyzer, PDF, or Summary layout behavior was changed.

The seven View-menu input screens do not share one editing lifecycle. Household, Assumptions, and Roth Conversions provide reversible screen-level drafts and Apply/Cancel controls. Accounts, Income, Expenses, and Non-Investible Assets commit accepted changes through their editor dialogs; adding a screen-level Cancel or rollback there would misrepresent their existing transaction semantics, so those screens retain their existing Close navigation behavior.

## Social Security reduction pair

In `AssumptionsView`, both controls display blank when the committed value is the default pair (0%, 2032). Other configurations remain visible, including 0% with a nondefault effective year. When both fields are blank, Apply normalizes the draft to the existing 0% / 2032 domain defaults. No UI-only blank-state property was added to the domain model.

Conditional required markers use the existing `RequiredFieldSupport` attachments and the fields' text properties as their observable condition. If only a percentage is present, the effective year is marked required. If only a year is present, the percentage is marked required. Both blank and both populated show no missing-field marker. A literal zero percentage counts as populated. Input parsing continues to enforce the existing inclusive 0–100 decimal percentage range and four ASCII digit year range of 1000–9999.

A field-specific validation error leaves the draft text intact, does not commit either value, and does not navigate away. The optional-help copy explains that both fields may be left blank, but become a required pair if either is entered. The existing tooltip was retained and extended with the pair behavior.

## Workspace lifecycle audit and implementation

| Workspace | Existing editing semantics | Phase 20 Apply/Cancel result |
|---|---|---|
| Household | Reversible screen-level draft | Successful Apply and Cancel return to Results Summary; failed Apply stays in Household. |
| Accounts | Accepted account editor operations commit immediately | No screen-level rollback or new Cancel was introduced; existing Close behavior remains. |
| Income | Accepted income/pension/social security editor operations commit immediately | No screen-level rollback or new Cancel was introduced; existing Close behavior remains. |
| Expenses | Accepted expense editor operations commit immediately | No screen-level rollback or new Cancel was introduced; existing Close behavior remains. |
| Non-Investible Assets | Accepted asset editor operations commit immediately | No screen-level rollback or new Cancel was introduced; existing Close behavior remains. |
| Assumptions | Reversible screen-level draft | Successful Apply and Cancel return to Results Summary; invalid Apply stays on Assumptions with draft text preserved. |
| Roth Conversions | Reversible screen-level draft, including existing transactional handler | Successful Apply and Cancel return to Results Summary; failed transactional Apply stays on the view. |

`MainWindow` supplies one shared completion callback that invokes its existing `navigateToResultsSummary()` destination. The callback is wired to the UI action handlers, not to programmatic `applyChanges()` calls, so callers such as the wizard do not acquire workspace navigation side effects. Navigation occurs only after synchronous Apply success or Cancel completes. Existing disabled/busy guards remain in force.

The Phase 17B Close action remains distinct: Close retains the workspace's existing node and draft lifecycle. Cancel discards only reversible uncommitted drafts. No modal editor, analyzer, wizard, settings, or Yearly Details lifecycle was changed.

## Files changed for Phase 20

- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/HouseholdView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/RothConversionView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/wizard/WizardFieldRow.java` — exposes the existing guided row label so the shared required-field helper can attach to it without a duplicate indicator.
- `src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/HouseholdViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/RothConversionViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/MainWindowViewNavigationTest.java`

No stylesheet change remains for Phase 20. An optional-help color override was removed after it produced unresolved theme-token warnings in isolated scenes; the help text now uses the normal theme-aware label color.

## Verification

### Selected regression level

Level 2 — UI and workflow integration, based on the changed JavaFX views, button handlers, navigation callback wiring, and input validation. Financial computation, persistence, serialization, snapshot, and analyzer identity paths were not modified. Per the risk-based policy, unrelated Monte Carlo simulations and exhaustive financial/analyzer suites were not included in the final scoped run.

### Final-source focused tests

Command used the repository-mandated IntelliJ Maven executable and local `.codex-m2` repository. The focused set included Assumptions view/layout/MainWindow tests, the navigation integration test, Household and Roth Conversion view tests, and relevant wizard/shared-view regression tests.

- Tests run: 176
- Passed: 176
- Failures: 0
- Errors: 0
- Skipped: 0

A complete Maven suite had previously passed before the final removal of the optional-help CSS color override: 4,044 run, 0 failures, 0 errors, 46 skipped. A later full-suite attempt was interrupted when the risk-based regression policy was supplied; it is not counted as a completed run. The final source was verified by the Level 2 focused run above. The final stylesheet cleanup was presentation-only and did not affect behavior.

### JavaFX previews

Actual JavaFX previews were generated and inspected, not inferred from CSS. The Assumptions view was inspected in Light and Dark themes at compact and standard sizes, including blank, conditional-marker, and validation states. The Results Summary was inspected at 900×650 and 1180×820; its right-hand Key Assumptions column and layout remain intact.

Preview artifacts are under `target/phase20/previews/` (`assumptions-reduction-{blank,conditional,validation}-{LIGHT,DARK}-{900,1180}.png`). Summary renderings are under `target/phase15c/previews/`. Visual checks found the required marker follows the missing paired field label, the optional explanation is readable, and the existing Summary layout is unchanged. Native Windows DPI scaling and acceptance remain outstanding.

### Diff check

`git diff --check` completed with no whitespace errors. Git emitted only the repository's existing LF-to-CRLF conversion notices.

## Scope confirmation and limitations

- The Results Summary and Key Assumptions layout were not modified.
- No projections or analyzers are launched by the new navigation callback.
- No financial calculations, plan serialization, snapshots, fingerprints, or history were changed.
- The four editor-driven workspace screens intentionally retain immediate-commit semantics and do not gain a false screen-level Cancel transaction.
- Native Windows acceptance, including DPI scaling and interactive Apply/Cancel navigation, remains required.

## Git status and preservation

Pre-existing Phase 19C Break-Even disclosure changes and its report/tests remain in the working tree and were preserved. Other pre-existing working-tree modifications were also left untouched. Phase 20 files are unstaged. Nothing was staged, committed, pushed, reset, cleaned, or reverted.

## Native Windows acceptance checklist

- Verify pair blank/default display, conditional asterisks, pair validation, and preservation of entered text after invalid Apply.
- Verify valid Apply commits and returns to Results Summary; Cancel discards only reversible drafts and returns to Summary.
- Verify Close remains distinct from Cancel and retains the existing workspace draft lifecycle.
- Verify Accounts, Income, Expenses, and Non-Investible Assets keep their established editor commit semantics.
- Inspect Light/Dark themes at 900×650 and 1180×820 and at 100%, 125%, and 150% Windows scaling where available.
- Confirm the Results Summary and right-hand Key Assumptions column remain intact.
