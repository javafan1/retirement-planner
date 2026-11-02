# Phase 16B2C — Pension Layout and Required-Name Validation

## Outcome

Phase 16B2C corrects the Income-view pension dialog's clipped actions, and applies the approved nonblank-name requirement at the person, account, and pension submission boundaries. The change preserves the Phase 16B2 `RequiredFieldSupport` indicators and inline wrapping behavior. Legacy blank names remain readable and are not rewritten during deserialization or ordinary plan serialization.

## Audit findings and root cause

`PensionDialog` originally placed the complete form grid directly in `DialogPane.content`. Expanding the optional Participant Facts `TitledPane` increased the content's preferred height. The dialog sized itself to that expanded preferred height, so on a smaller usable display the DialogPane's action area could fall below the visible screen. The content had no vertical viewport to absorb the extra height, and the action area was part of the same dialog sizing calculation.

The account editor already provided a project-local pattern for a scrollable form with native DialogPane actions. The pension editor lacked that boundary. Other editors inspected did not share the same dynamically expanding facts section, so no unrelated dialog was resized as part of this correction.

## Pension layout correction

The pension form now sits in a fit-to-width `ScrollPane` with vertical scrolling as needed and horizontal scrolling disabled. A small outer content box holds validation messages above the viewport; the native DialogPane action bar remains outside the scrolling content. This keeps Save/Cancel visible while the form grows, and lets error messages occupy visible layout space. Validation focuses the affected field and scrolls it into view.

The dialog retains its owner, modality, `ButtonType.OK`/`CANCEL` semantics, and keyboard behavior. Its preferred width is capped at 560 logical pixels, while maximum dimensions are derived from the owning screen's visual bounds with margins for window decorations and the action footer. On constrained screens, the form viewport shrinks and scrolls instead of pushing the actions off-screen. Participant Facts expansion recalculates the dialog size without imposing a fixed taller dialog.

## Required-name policy and validation boundaries

The approved rule is that names submitted by user edits contain at least one non-whitespace character. `RequiredNameValidation.normalize` rejects null, empty, or Unicode-whitespace-only values and trims leading/trailing whitespace with `String.strip()`. It preserves internal spaces, punctuation, accents, and non-Latin characters; no capitalization, alphabet, or minimum-length rule was added.

| Record | Required name | Enforcement point | Failure behavior |
|---|---|---|---|
| Primary person | First and last name | `PersonCard.readValidated()` on dirty Household Apply; `NewPlanDraft.complete()` on wizard final acceptance | Rejects before either included person's values are committed; identifies and focuses the invalid field |
| Included spouse | First and last name | Same Household Apply and wizard final acceptance paths, active only while included | No mutation or clearing on failure; retained spouse values remain untouched while spouse inclusion is off |
| Account | Account Name for all user-editable account types | `AccountDialog` submit filter, used by standard Accounts and wizard editors | Keeps editor open, preserves text, shows inline field-specific feedback, focuses the name field; successful result is normalized |
| Pension | Pension Name for all user-editable pension records | `PensionDialog` submit validation, used by Income and wizard editors | Keeps editor open, preserves text, shows inline field-specific feedback, focuses the name field; successful result is normalized |

The account and pension domain constructors remain permissive. They are also used by JSON decoding, historical data, tests, and non-UI factories, so constructor invariants would have broken the legacy compatibility requirement. No global mapper or repository validation was added.

### Save paths and legacy compatibility

- Household Apply validates both names on a dirty edit, validates both included people before applying either one, then commits the edit. A clean legacy household still runs the pre-existing birth-date/mortality checks but does not require migration of blank names.
- A new plan's `NewPlanDraft.complete()` independently validates included names, so accepting a plan cannot bypass the UI card path.
- Standard and guided account creation/editing share `AccountDialog`; its submit filter runs before result conversion, so invalid names cannot produce an `Account` result.
- Standard and guided pension creation/editing share `PensionDialog`; invalid names cannot produce a `Pension` result.
- Plan JSON loading and serialization continue to accept blank historical names. Save/Save As of an otherwise unchanged legacy plan is not blocked by a whole-plan name scan. Editing the affected record requires a valid replacement name.
- No placeholder names, eager data migration, persistence schema changes, historical result rewrites, or projection changes were introduced.

## Required-field indicator reconciliation

The existing shared `RequiredFieldSupport` implementation supplies the visual star without changing `Label.getText()`. `PersonCard` uses its existing person-required observable state: primary first/last names are always marked, while spouse first/last names are marked only while spouse inclusion is active. The wizard's older label-string star path detects attached helper indicators and does not add a duplicate. Account and pension editor names now use the same helper; guided editor presentation uses that same attached marker.

The Phase 16B2 inventory is reconciled in [PHASE-16B2-MAIN-INPUT-REQUIRED-FIELD-INDICATORS.md](PHASE-16B2-MAIN-INPUT-REQUIRED-FIELD-INDICATORS.md). Relative to that report, four always-required roles (primary names, account name, pension name) and two conditional roles (spouse names) were added. The current main-view reconciliation is 39 always-required roles, 36 conditional roles, and 10 optional/ambiguous or intentionally unmarked roles. Optional fields, including account first-eligible year, pension end/survivor values, recurring expense volatility, and Social Security display name, remain unmarked.

## Field-by-field validation consistency review

| Marked field group | Existing enforcement checked | Indicator/validation alignment |
|---|---|---|
| Household names, birth dates, mortality categories | Dirty Apply validates names plus existing person facts; clean legacy Apply preserves the old facts-only behavior | Names are required only for changed/included records; spouse requirement follows inclusion |
| Account type, owner, balance, inherited-account facts | Account submission uses selected type/owner and existing account factory/domain checks; inherited fields remain conditional | Existing required markers remain tied to type/editor mode; name now has explicit nonblank feedback |
| Employer conversion availability | New traditional employer account submission requires explicit supported status; legacy UNKNOWN remains preserved on unrelated edit | Existing state and year conditional behavior is unchanged; year remains optional per policy |
| Participant facts | Shared editor requires statuses when the section is active; valid `Unknown` defaults satisfy the requirement; dates remain optional | Status stars remain tied to the shown/managed editor; no new date requirement |
| Pension | Submit validation checks name, owner, start date, monthly benefit, annual COLA and parses optional end/survivor values; annual COLA's valid default is accepted | Name is newly enforced; other markers continue to match existing parser/conditional behavior |
| Social Security | Submit validation requires owner, gross FRA benefit, and claiming age; benefit start date is derived | Existing required markers match; no formula or claiming behavior changed |
| Expense | Existing editor validates description, type, amount, and one-time effective date; recurring dates and zero-volatility default remain optional | Existing markers and one-time condition match; volatility was not made mandatory and positive-volatility execution remains gated |
| Non-investible asset | Existing editor requires name, current value, and growth rate during submission | Existing markers match; parser and growth calculation unchanged |
| Assumptions | Apply parses core numeric/date inputs; scenario/source-dependent fields use the current plan/mode checks | Existing markers remain tied to current assumptions; defaults count as valid values where Apply accepts them |
| Roth Conversion | Shared configuration validation requires enabled schedule fields and strategy-specific amount/target fields | Dynamic markers follow enabled state and strategy; disabled saved requests are not modified |
| Results Summary Key Assumptions | Same Apply/configuration validators and mode conditions as the full inputs | Requiredness remains synchronized; right-hand panel and Apply/Cancel behavior unchanged |

The full Phase 16A inventory still assigns analyzer-only and Application Settings controls to later phases. No missing or inconsistent non-name requirement was “fixed” by relaxing or broadening validation here. The newly approved names are the only new validation rule. Existing account/pension blank-name acceptance for directly constructed legacy objects is intentional and distinct from editor submission.

## Changed files

Production changes for this correction:

- `src/main/java/com/daviddunn/retirementplanner/domain/model/PersonInformationValidation.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/model/RequiredNameValidation.java` (new)
- `src/main/java/com/daviddunn/retirementplanner/ui/components/PersonCard.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/AccountDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/PensionDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/HouseholdView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanDraft.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/wizard/WizardDialogSupport.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/wizard/AccountsWizardStep.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/wizard/IncomeWizardStep.java`

Tests and preview support:

- `src/test/java/com/daviddunn/retirementplanner/ui/dialogs/PensionLayoutAndRequiredNameTest.java` (new)
- `src/test/java/com/daviddunn/retirementplanner/ui/dialogs/RequiredFieldMainEditorTest.java` (new/extended from Phase 16B2 work)
- `src/test/java/com/daviddunn/retirementplanner/ui/views/RequiredFieldMainViewsPreviewTest.java` (new/extended)
- `src/test/java/com/daviddunn/retirementplanner/ui/views/HouseholdViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsMainWindowTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/RothAvailabilityUiTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/RothConversionMainWindowTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/PlanningHorizonLayoutTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/ResultsSummarySocialSecurityPreviewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/RothConversionViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewRetirementPlanWizardTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/AccountsWizardStepTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/ExpensesWizardStepTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/IncomeWizardStepTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanFundingFailureTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/WizardFinalAcceptanceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/CompleteWizardTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/GuidedWizardShellTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/SevenStepWizardTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/WizardClarityTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/WizardFinancialEquivalenceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/InputTooltipTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/controls/RequiredFieldSupportTest.java`

The preceding Phase 16B2 working tree changes were retained. Test fixtures supply valid names when exercising unrelated wizard, account, or household behavior. The financial-equivalence wizard test still exercises empty financial inputs; it now gives the person valid names because an included person is required in a newly accepted plan. Blank-name cases remain covered by explicit rejection and legacy-compatibility tests.

## Automated and visual verification

- Focused required-field, editor/view, layout, wizard, and related regression group: **87 tests, 0 failures, 0 errors, 0 skipped**.
- MainWindow Roth regression suite after fixture correction: **17 tests, 0 failures, 0 errors, 0 skipped**.
- `WizardFinancialEquivalenceTest` after updating its person-name fixtures: **4 tests, 0 failures, 0 errors, 0 skipped**.
- Full Maven suite on the final code: **3,955 tests, 0 failures, 0 errors, 46 skipped**; Maven reported `BUILD SUCCESS`. Skips are existing opt-in/visual tests guarded by environment properties.
- `git diff --check`: passed after final report updates.

Actual JavaFX screenshots generated in both themes and inspected:

- `target/phase16b2c/previews/pension-collapsed-light.png`
- `target/phase16b2c/previews/pension-collapsed-dark.png`
- `target/phase16b2c/previews/pension-expanded-light.png`
- `target/phase16b2c/previews/pension-expanded-dark.png`
- `target/phase16b2c/previews/pension-name-error-light.png`
- `target/phase16b2c/previews/pension-name-error-dark.png`
- `target/phase16b2c/previews/pension-validation-error-light.png`
- `target/phase16b2c/previews/pension-validation-error-dark.png`
- `target/phase16b2/previews/light-account-employer-availability.png`
- `target/phase16b2/previews/dark-account-employer-availability.png`
- `target/phase16b2/previews/light-household-single-1180x820.png`
- `target/phase16b2/previews/dark-household-single-1180x820.png`
- `target/phase16b2/previews/light-household-couple-1180x820.png`
- `target/phase16b2/previews/dark-household-couple-1180x820.png`
- `target/phase16b2/previews/light-wizard-account-required-name.png`
- `target/phase16b2/previews/dark-wizard-account-required-name.png`
- `target/phase16b2/previews/light-wizard-pension-required-name.png`
- `target/phase16b2/previews/dark-wizard-pension-required-name.png`
- `target/phase16b2/previews/light-new-plan-household-primary-required-names.png`
- `target/phase16b2/previews/dark-new-plan-household-primary-required-names.png`
- `target/phase16b2/previews/light-new-plan-household-couple-required-names.png`
- `target/phase16b2/previews/dark-new-plan-household-couple-required-names.png`

The pension images show a persistent bottom action area in both expanded and collapsed states. Error previews show the message above the scroll viewport; tests also verify that the invalid control remains within the viewport and that Save/Cancel remain visible. Native Windows 125% and 150% display-scaling checks were not available in the automated rendering run and remain acceptance items.

## Remaining limitations and acceptance

- Name validation applies when users submit edits; untouched legacy blanks remain as stored until the user edits those records.
- This does not add a whole-plan migration or block unrelated Save/Save As operations.
- Windows display scaling at 100%, 125%, and 150% and native focus traversal must be accepted on the target Windows setup.
- Analyzer and Application Settings required-field rollout remains deferred. No Phase 16B3 work is included.

### Working-tree status

The repository already contained partial Phase 16B2 changes when this corrective pass began. Those modified and untracked files were preserved. At completion, the Phase 16B2/B2C source, test, and report files remain in the working tree; `git diff --cached --stat` is empty. Nothing was staged, committed, pushed, reset, or cleaned.

Native Windows acceptance checklist:

- Open Pension from Income, expand Participant Facts, and confirm Save/Cancel remain visible; verify each field is reachable by keyboard and validation scrolls the invalid field into view.
- Check pension dialog sizing on a small laptop display at 100%, 125%, and 150% scaling, in both themes.
- Try blank, spaces-only, padded, punctuated, accented, and non-Latin person/account/pension names. Confirm blank inputs are rejected, padding is trimmed on success, and internal text is preserved.
- Confirm spouse indicators appear only while included and no duplicate stars appear in the wizard.
- Load an older plan with blank names, verify calculations and normal save still work, then verify the edited record requires a valid name.
- Confirm no plan is created or updated after a failed household or editor validation.

No changes were staged, committed, or pushed.
