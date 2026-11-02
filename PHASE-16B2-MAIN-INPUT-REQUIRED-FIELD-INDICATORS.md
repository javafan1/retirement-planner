# Phase 16B2 — Main Input Required-Field Indicators

## Result

Phase 16B2 main-view and editor integration is implemented. Required-field indicators use the accepted `RequiredFieldSupport` attachment API, and the confirmed wrapped-label defect has been corrected within that shared helper. The indicators reflect current form requirements and do not perform validation or change form submission behavior.

The Results Summary's right-hand Key Assumptions column remains in place. No financial rules, persistence formats, analyzer behavior, or application navigation were changed. Phase 16B3 has not begun.

## Audit trail and approved helper correction

The initial Phase 16B2 checkpoint stopped at visual verification after showing that the Phase 16B1 `Label` graphic with `ContentDisplay.RIGHT` aligned the asterisk to the edge of a wrapped `Label`'s layout box. On the long “Assumed second death / end of household projection” label, the asterisk was separated from the final word. The same issue appeared in the narrow Summary Key Assumptions column.

The approved correction changes only the helper's rendering strategy. It now places the unchanged label text, a separating space, and the themed indicator `Text` in one `TextFlow`, displayed as the label's graphic. JavaFX wraps the text and marker together. A layout test checks the marker's caret geometry against the end of the final text line, rather than only checking that a marker node exists. At very narrow widths the marker flows onto its own following line instead of clipping or moving to the far edge.

The first `TextFlow` prototype bound its maximum width to `Label.width`. That produced a zero-width feedback cycle in the Summary sidebar. The final implementation leaves the flow's maximum width unconstrained and lets the Label skin size the graphic; a resize test confirms that the Label and flow retain the available column width and reflow correctly. No label-specific widths or manual coordinates were introduced.

`RequiredFieldSupport.attach(...)`, `Label.getText()`, control IDs, label/control association, tooltip, dynamic observable state, attachment disposal, existing accessible names, and required-state accessible-help composition remain intact. Existing non-null label graphics continue to fail clearly rather than being silently discarded. The helper stays non-focusable and uses the existing `.required-field-indicator` CSS class and `-app-error` token. When an older guided-editor label text ends in a literal marker, the helper preserves `Label.getText()` and renders the marker only once in its inline flow. The guided editor wrapper omits a redundant helper legend where it already supplies its legacy legend.

## Requiredness policy and field reconciliation

The asterisk means a valid value is required when the applicable existing form submits, even when a valid default is already present. The helper only renders the caller-provided current requirement; it does not inspect values, control types, validation errors, or disabled state. Existing validation and default behavior were retained.

| UI surface | Always-required field roles marked | Conditional roles and condition | Intentionally unmarked / derived | Validation source |
|---|---|---|---|---|
| Household — Primary | First Name; Last Name; Birth Date; Mortality category | — | — | Household Apply and New Plan final acceptance require nonblank names; existing person-information validation checks date/category |
| Household — Spouse | — | First Name; Last Name; Birth Date and Mortality category while spouse is included | Retained spouse values while excluded | Include-spouse state controls indicators; household Apply validates included spouse; no clearing on exclusion |
| Account editor | Account Name; Account Type; Owner; Current Balance | Inherited original-owner DOB, date of death and beneficiary relationship for inherited account types | First Eligible Year is optional | `AccountDialog` blocks submission with a field-specific name error; AccountFactory/domain checks enforce other selected fields |
| Account editor — participant facts | — | Separation Status and Plan Retirement Conditions when the participant-facts panel is visible, managed and expanded | Separation Date; Conditions Met Date | Existing `RetirementParticipantFactsEditor.value()` non-null status requirements; dates remain optional |
| Account editor — employer availability | — | Availability Status for visible new employer accounts or after explicit replacement of legacy UNKNOWN | First eligible calendar year is optional; legacy UNKNOWN is not newly marked | Existing employer-availability editor validation and Phase 13I-B policy |
| Pension editor | Pension Name; Owner; Start Date; Monthly Benefit; Annual COLA Rate (the valid displayed default still satisfies the submit parser) | Participant-fact statuses when their section is expanded and shown | End Date; Survivor Monthly Benefit; participant dates | `PensionDialog` blocks submission with field-specific errors; participant facts are validated by `RetirementParticipantFactsEditor` |
| Social Security editor | Owner; Gross FRA Monthly Benefit; Claiming Age | — | Name; Benefit Start Date is derived/read-only | Existing `SocialSecurityDialog` result validation |
| Expense editor | Description; Expense Type; Annual Amount | Purchase / Effective Date for One Time only | Recurring Effective Date; End Date; Annual Expense Volatility (0% is optional/defaulted); escalation selection follows its existing default/validation behavior and was not made newly mandatory | Existing `ExpenseDialog.validateAndBuildExpense()` |
| Non-Investible Asset editor | Asset Name; Current Value; Growth Rate | — | — | Existing asset editor parsing/result validation |
| Assumptions | Projection Start Date; Planning Horizon; Post-Death Expense Factor; Investment Return; General Inflation; Healthcare Inflation; Social Security COLA; Filing Status; State of Residence; Federal Bracket Growth; Standard Deduction Growth; Legacy State Income Tax Rate; Local Income Tax Rate; Estimated Heir Tax Rate | Future federal rate adjustment and effective year when either is entered (both then required); Death Scenario when the plan has a spouse; Death Year and Survivor Benefit Claiming Age for an active non-“Both Survive” scenario; Social Security claiming age for each configured source | Calculated monthly benefit rows are read-only and have no indicator | Existing `AssumptionsView.applyChanges()` parsing/validation and current household/source state |
| Roth Conversions | — | Strategy, Conversion Year, Frequency and Stop Rule while enabled; Conversion Amount only for FIXED_AMOUNT; Target Taxable Income only for CUSTOM_TAXABLE_INCOME_TARGET | All inactive conversion fields while disabled; fields for the other strategy | Existing `RothConversionConfiguration` validation and shared transition rules |
| Results Summary — Key Assumptions | Investment Return; General Inflation; Planning Horizon | Roth Strategy, Start Year and Frequency while enabled; Amount/Target only for matching strategy; Death Scenario for couple plans; Death Year and Survivor Benefit Claiming Age for active death scenario; Social Security age for each configured source | Monthly benefit previews remain derived/read-only; inactive controls remain unmarked | Same Summary Apply handlers and configuration validation; conditions are shared with the Summary's mode state |
| Opening RMD Information dialog | Deferred outside Phase 16B2 main-view rollout | Per-account prior December 31 balance and already-distributed RMD values are validated by the RMD workflow when used | This auxiliary RMD dialog is not part of the Accounts editor or Results Summary controls changed here | Assign to a later approved remaining-dialog rollout; do not infer new validation policy in this phase |

For a countable reconciliation, “role” means one field on one UI surface; repeated fields in the Summary and full Assumptions form count as distinct display roles. Following Phase 16B2C, the implementation covers **39 always-required roles** and **36 conditional roles** across the listed surfaces (**75 covered roles**). The two primary name roles and Account Name and Pension Name add four always-required roles; the two spouse name roles add two conditional roles. The conditional rules that can create/remove markers at runtime are connected to the same mode/source properties that already drive visibility or validation. The reusable Social Security row attaches one age role per present source, so single-person plans do not receive a spouse marker.

There are **10 optional, ambiguous, or intentionally unmarked editable roles** called out above: optional first-eligible year, two optional participant dates, pension end date and survivor amount, Social Security name, recurring expense dates, expense volatility, and escalation selection behavior. The count treats the two recurring date controls as separate roles and excludes read-only values. Two read-only Social Security benefit previews and the derived benefit start date were inspected and correctly receive no marker. Existing manual markers standardized: the two `PersonCard` field labels and State of Residence now use the shared graphical indicator instead of embedded label asterisks.

Phase 16B2C resolved the Account Name, Pension Name, and household-name policy as required nonblank names. Participant-fact statuses were an omitted Phase 16A inventory item discovered during implementation. The editor's submit path requires non-null statuses while its valid default is `Unknown`, so the status fields receive asterisks when shown. Their dates stay optional.

## Implementation and lifecycle

The main views and editors keep `RequiredFieldSupport.Attachment` handles and close them when a dialog is hidden or its owner view is disposed. Dynamic rows in Assumptions and Summary close old attachments before rebuilding them, preventing listener accumulation across plan loads. `PersonCard` exposes a requirement property controlled by the existing spouse-inclusion state. The Roth Conversion legend follows enabled state; it is hidden and unmanaged when conversions are inactive.

The Summary implementation adds indicators to editable Key Assumptions controls without moving or resizing the right-hand column. Roth strategy-specific markers and death-scenario markers follow the same conditions as their controls. Existing Apply/Cancel callbacks and dirty/freshness behavior were left unchanged. Merely changing required state does not write plan values or trigger projection work.

Shared input editors also appear in guided plan creation. Their existing field IDs, validation and wizard flow remain unchanged. The shared helper keeps the public label text unchanged, avoids duplicating a legacy trailing marker when rendering, and preserves the wizard wrapper's single existing required legend. The step structure and wizard-specific validation were not redesigned.

## Files changed

Production files:

- `src/main/java/com/daviddunn/retirementplanner/ui/controls/RequiredFieldSupport.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/controls/RetirementParticipantFactsEditor.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/controls/EmployerRothAvailabilityEditor.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/components/PersonCard.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/AccountDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/ExpenseDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/NonInvestableAssetDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/PensionDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/SocialSecurityDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/wizard/WizardDialogSupport.java` (only to avoid a duplicate legend from a shared editor)
- `src/main/java/com/daviddunn/retirementplanner/ui/views/HouseholdView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/RothConversionView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java`

Test files:

- `src/test/java/com/daviddunn/retirementplanner/ui/controls/RequiredFieldSupportTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/dialogs/RequiredFieldMainEditorTest.java` (new)
- `src/test/java/com/daviddunn/retirementplanner/ui/views/RequiredFieldMainViewsPreviewTest.java` (new)
- `src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/HouseholdViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/PlanningHorizonLayoutTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/ResultsSummarySocialSecurityPreviewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/RothConversionViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/ExpensesWizardStepTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewRetirementPlanWizardTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanFundingFailureTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/WizardFinalAcceptanceTest.java`

`PlanningHorizonLayoutTest` and wizard tests that previously inspected JavaFX's internal label skin or a marker embedded in `Label.getText()` now inspect the graphic marker while retaining assertions for the unchanged base label text and layout. The funding recovery test locates the balance editor by its existing tooltip rather than depending on accessible-help string ordering after the shared helper appends “Required”.

## JavaFX visual verification

The actual JavaFX preview test regenerated Light and Dark images under `target/phase16b2/previews/` for household single/couple, employer account, pension (including expanded participant facts), Social Security, one-time and recurring expenses, non-investible asset, Assumptions, Roth Conversions, Results Summary Key Assumptions, and a compact wrapped-label fixture.

The required wrapped-label checks were inspected at 1180×820 for Assumptions and Summary, and at multiple widths in the standalone 340×620 fixture (280, 180, 115, 75 and 60 logical pixels). At 60 pixels the asterisk moves to its own following line. In the Summary, the Key Assumptions column remains on the right with the same width and existing scrolling; its horizon label now ends with an inline red marker on the final text line. No special width or manual position is applied. Both themes show legible red markers against the existing surfaces. Representative paths:

- `target/phase16b2/previews/light-assumptions-1180x820.png`
- `target/phase16b2/previews/dark-assumptions-1180x820.png`
- `target/phase16b2/previews/light-summary-key-assumptions-1180x820.png`
- `target/phase16b2/previews/dark-summary-key-assumptions-1180x820.png`
- `target/phase16b2/previews/light-wrapped-label-fixture-340x620.png`
- `target/phase16b2/previews/dark-wrapped-label-fixture-340x620.png`

The actual household, account, pension, Social Security, expense, asset and Roth views were also inspected in Light and Dark modes. The single-person Household preview has no spouse marker; the couple preview shows both spouse requirements. Required markers remain beside their labels in disabled controls where the field is still required. No native Windows 125%/150% DPI exercise was performed; these are JavaFX logical-pixel renderings on the current Windows test environment.

## Verification

Focused test results after the helper correction and integration:

- Shared helper, editor/view integration, layout and guided-editor focused group: **56 tests, 0 failures, 0 errors, 1 skipped**. The skip is the existing opt-in `NewPlanFundingFailureTest` visual preview.
- A separate focused integration run before the final helper edge-case test: **116 tests, 0 failures, 0 errors, 0 skipped**.
- JavaFX preview test: **1 passed**; actual Light/Dark screen previews regenerated and inspected.
- Complete Maven regression suite (final code): **3,948 tests, 0 failures, 0 errors, 46 skipped**. Maven reported `BUILD SUCCESS`. Skips are existing opt-in/visual tests guarded by environment properties; no assertions were weakened to obtain this result.
- `git diff --check`: **passed**; Git emitted only line-ending normalization warnings for the modified Windows working copies.

The first complete-suite attempt (before updating test assertions for the new inline graphic and before changing the AccountDialog test's help lookup to use its tooltip) executed 3,947 tests and exposed four test errors: two looked only for the old skin `.text` node, one looked only for a prior accessible-help prefix, and one expected a literal `*` in a wizard `Label.getText()`. The affected tests now inspect the preserved base label text and inline marker, or locate the existing tooltip. The final full-suite run passed.

## Preserved behavior and limitations

- No Apply/Save validation, value default, error, or field requirement was changed to make the indicators fit.
- No financial calculation, plan JSON, projection fingerprint, historical snapshot, analyzer, expense volatility execution, or navigation behavior was changed.
- Account Name and Pension Name blank-value behavior remains as found; the two are not newly marked required.
- Expense volatility remains optional/defaulted and its Monte Carlo admission restriction is unchanged.
- Key Assumptions remains at the right; no Summary redesign or width change was made.
- Wizard steps and validation remain unchanged; shared dialog labels use the common visual marker without duplicate stars/legends.
- Analyzer and Application Settings indicator rollout remains deferred to later approved phases.
- The auxiliary Opening RMD Information dialog's account-specific balance/distribution fields are deferred with remaining dialogs; no new RMD validation or indicator policy was introduced.

## Native Windows acceptance checklist

- Review light/dark required markers on the Assumptions screen and Summary Key Assumptions at compact and standard sizes.
- Confirm long wrapped horizon labels put the star directly after the final line in both locations.
- Confirm employer-account status, inherited-account, participant-fact, expense type and Roth-strategy transitions show/hide markers as expected.
- Confirm accessibility announcements identify Required without losing existing accessible help; test with the target screen reader.
- Verify keyboard traversal and dialogs at Windows 100%, 125% and 150% display scaling.
- Confirm single-person versus couple Household behavior and editor/wizard compatibility.

No files were staged, committed, or pushed. Phase 16B3 has not started.
