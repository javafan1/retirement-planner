# Phase 6E-N3 — Alabama Pension UI Corrections

## Pension editor

The standard editor placed the required-field legend in the same GridPane row as the legal classification label and ComboBox. The legend spanned both columns, so its text could draw across the selected classification. The editor had one classification label, an empty ComboBox prompt and the standard JavaFX selection cell. The guided pension dialog removed the shared legend when it installed its own, which explained the difference between the two entry points. Font size and Windows scaling made the shared-row defect more visible.

The label, full-width ComboBox and legend now have separate rows. The regular and guided editors share the same layout and use the display text **Unknown**, **Defined Benefit** and **Defined Contribution**. The longer Unknown / Not Specified caption was clipped in the narrow, enlarged-font guided editor. Field help identifies the IRC §414(j) requirement for Defined Benefit. The existing scrollable form accommodates smaller viewports. The Cancel button sizes to its label at larger font sizes.

## Preflight and navigation

- The nonmodal notice appears beneath State of Residence when the Alabama draft has a pension that may require review. It uses the draft timeline, filing status and configured deaths. While a form input is temporarily invalid, the preview uses the applied timeline and the residence selected in the editor.
- The reusable preflight looks for a positive, in-horizon payment from an UNKNOWN pension to an admitted Single or Married Filing Jointly household recipient. It uses the existing pension receipt selection and checks both ordinary and direct survivor payments. It excludes other states, classified pensions, zero payments and pensions outside the projection years. IRAs, brokerage accounts and other non-pension sources do not enter the check.
- The main window also evaluates the active plan immediately before a deterministic projection refresh. A freshly created plan with a candidate shows the notice first and can proceed through **Recalculate Projection**. A legacy file with no saved results shows the same guidance on open. Accepted saved results reopen through the existing restoration path.
- **Review Pensions** opens the existing Income workspace and selects and scrolls to the first affected pension. **Choose a Pension** lists owner and name for each affected record when there is more than one. Guided creation uses the same actions to return to its Pensions step. Navigation preserves drafts and does not start a projection.
- **Close** dismisses the notice for the current candidate list. It clears when the list is empty and refreshes when classifications or the applicable pension list change. The current projection's confirmed pension-classification failure uses the inline status and notice. Other state-tax admission failures and cases outside preflight coverage retain the State Tax Unavailable dialog.

## Compatibility

Missing legacy classifications still load as UNKNOWN. Explicit DB/DC choices use the existing serialized enum values. Apply, Cancel, Save As and retained projection/baseline restoration follow the existing persistence paths. All notices are session-local. The preflight only reads the selected plan or draft.

## Files changed

Paths in this list are relative to the repository root.

| Path | Change |
| --- | --- |
| `src/main/java/com/daviddunn/retirementplanner/domain/income/HouseholdPensionIncomeCalculator.java` | Shares the annual pension receipt selection. |
| `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/AlabamaPensionPreflight.java` | Adds the advisory check for potentially relevant pensions. |
| `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/AlabamaProductionTaxCalculator.java` | Shares the supported filer check, first supported year and existing classification failure text. |
| `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java` | Wires the deterministic projection notice and Income navigation. |
| `src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java` | Checks preflight before a new startup projection. |
| `src/main/java/com/daviddunn/retirementplanner/ui/controls/AlabamaPensionNotice.java` | Renders and dismisses the notice and provides pension links. |
| `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/PensionDialog.java` | Separates the classification and legend and sizes the control and Cancel label. |
| `src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java` | Shows the notice for the selected assumptions draft. |
| `src/main/java/com/daviddunn/retirementplanner/ui/views/IncomeSourcesView.java` | Selects and scrolls to the requested pension. |
| `src/main/java/com/daviddunn/retirementplanner/ui/wizard/AssumptionsWizardStep.java` | Connects the guided Assumptions notice to existing wizard navigation. |
| `src/main/java/com/daviddunn/retirementplanner/ui/wizard/IncomeWizardStep.java` | Selects the requested pension in the guided editor. |
| `src/main/java/com/daviddunn/retirementplanner/ui/wizard/NewRetirementPlanWizard.java` | Navigates from Assumptions to the visited Pensions step. |
| `src/main/resources/css/application.css` | Styles the notice using shared Light and Dark theme colors. |
| `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/AlabamaPensionPreflightTest.java` | Covers state, dates, ownership, survivor receipts and the annual admission safeguard. |
| `src/test/java/com/daviddunn/retirementplanner/ui/dialogs/PensionLayoutAndRequiredNameTest.java` | Inspects actual JavaFX skins and captures the narrow dialog in both themes. |
| `src/test/java/com/daviddunn/retirementplanner/ui/views/AlabamaPensionNoticeTest.java` | Covers dismissal, navigation, classification edits, legacy open, Save As, baseline residence and fallback. |
| `src/test/java/com/daviddunn/retirementplanner/ui/wizard/AlabamaUiAcceptanceTest.java` | Covers guided navigation back to Pensions with Alabama still an unapplied draft. |
| `PHASE-6E-N3-ALABAMA-PENSION-UI-CORRECTIONS.md` | Records verification and acceptance steps. |

## Visual verification and automated tests

JavaFX tests opened real pension dialogs through the Windows Glass backend at process-level output scales of 100% and 150%. They asserted the native window output scale, used a 350 JavaFX-pixel dialog pane with an 18 px font, and checked all three selected values in the rendered skin, label and legend separation, prompt rendering and the Cancel text.

I inspected the eight rendered Defined Benefit previews under [target/phase6e-n3/previews](target/phase6e-n3/previews). They cover the regular and guided editors in Light and Dark at both scale settings. The classification was readable in each preview. The automated checks and snapshots leave the full interactive Windows checklist below open.

| Maven verification | Result |
| --- | --- |
| Focused pension, Alabama admission, projection, persistence, Assumptions and wizard tests | 133 tests, zero failures, errors or skips. `target/phase6e-n3-focused.log`. |
| Final UI checks at 100% Windows Glass scale | 12 tests, zero failures, errors or skips. `target/phase6e-n3-scale100.log`. |
| Final UI checks at 150% Windows Glass scale | 12 tests, zero failures, errors or skips. `target/phase6e-n3-scale150.log`. |
| Established bounded suite | 5,362 tests, 5,327 passed, 35 existing skips, zero failures or errors. Maven exit 0. `target/phase6e-n3-bounded.log`. |

Maven used IntelliJ's bundled executable and the repository-local `.codex-m2/repository` cache. The bounded selector is `-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*Performance*,!*Exhaustive*,!*Search*`.

The focused selector was `-Dtest=AlabamaPensionPreflightTest,AlabamaPensionNoticeTest,PensionLayoutAndRequiredNameTest,PensionClassificationTest,AlabamaPensionPersistenceTest,AlabamaProjectionAcceptanceTest,AlabamaProductionTaxCalculatorTest,AlabamaUiAcceptanceTest,StateTaxUnavailableMessageTest,StateTaxUnavailableProjectionTest,AssumptionsMainWindowTest,MainWindowViewNavigationTest,StateTaxWizardIntegrationTest`. Both final scale runs selected `PensionLayoutAndRequiredNameTest,AlabamaPensionNoticeTest,AlabamaUiAcceptanceTest`.

The existing state-tax golden tests passed with their original expected values, including `AlabamaAnnualGoldenTest`, `StateTaxGoldenFixturesTest` and `MichiganAttributedGoldenTest`. Arkansas and Colorado remain disabled.

## Native Windows acceptance checklist

- [ ] In the full desktop app, run at the normal Windows display scale and repeat at 150% or the target enlarged scale. Use Light and Dark themes. In both the regular and guided editors, narrow the dialog and inspect all three selections, the drop-down, the required-field legend and the footer.
- [ ] Select Alabama in a Michigan plan with both primary and spouse UNKNOWN pensions. Dismiss the notice, make an ordinary edit, then switch to Michigan and back to Alabama. Check notice dismissal and reappearance.
- [ ] Use **Review Pensions** and **Choose a Pension**. Confirm the editor selects the intended record, preserve unrelated assumptions drafts, and try both Cancel and saving an explicit classification.
- [ ] Open a legacy Single Alabama file with a relevant UNKNOWN pension. Check the early guidance, run with the unresolved input, correct the pension and recalculate. Repeat with an out-of-horizon pension and with IRA or brokerage income only.
- [ ] Save As, reopen the accepted Alabama projection, and inspect the saved baseline after switching the current plan to Michigan. Swap the accepted current and baseline roles and confirm the classifications and notice match the active plan. Verify a separate unsupported state-tax case still opens State Tax Unavailable.

## Remaining limitations

Potential relevance depends on projected receipts. Exact DB versus DC tax materiality also depends on all annual tax inputs, including withdrawals and deductions, and remains an annual Alabama calculation. A conservatively flagged pension may be tax-equivalent. The notice follows the existing annual pension calendar, including its opening-year treatment. Other analysis workflows keep their existing state-tax admission and failure reporting.

## Repository verification

`git status --short` was reviewed. With untracked files expanded, the combined working tree has 44 modified tracked files and 36 untracked files, including the preserved Phase 6E-M audit and the Phase 6E-N1 and N2 work. The audit hash matches the N1 record. `git diff --check` exited 0, and the 18 files listed above passed a strict UTF-8 and trailing-whitespace check. The staged diff is empty. All work is local.
