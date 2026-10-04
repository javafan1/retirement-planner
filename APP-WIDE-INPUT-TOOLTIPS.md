# App-Wide Input Tooltips

## Starting state and read-only inventory

Starting HEAD: `55ac3fa Add Monte Carlo PDF export`. Initial `git status --short`
was empty. No staging, reset, stash or pull.

Existing mechanism: `HelpIcon.createTooltip` wraps at 375px, uses 12px text,
250ms show delay, five-minute duration and JavaFX's default hide delay.
Economic Assumptions uses separate help icons, not input/label tooltips.
Social Security longevity/valuation help uses that factory on controls only.
SurvivorBenefitClaimingControls already shares dynamically bound help with its label.
Monte Carlo uses plain Tooltip constructors, with help on most inputs but no
label hover; chart/result tooltips are separate presentation behavior.

Pre-implementation inventory (logical controls, excluding skin editor children):

| View/domain | Editable inputs reviewed | Existing coverage / intended work |
| --- | --- | --- |
| PersonCard (used twice) | 4: names, DOB, mortality category | Category covered; add DOB; names obvious, excluded |
| AccountDialog | 7: name, type, owner, balance, inherited DOB/death/relationship | Add six substantive explanations; name excluded |
| PensionDialog | 7: name, owner, dates, monthly/survivor amounts, COLA | Add six; name excluded; COLA is a decimal, not percentage |
| ExpenseDialog | 6: description, type, amount, growth, dates | Add five; description excluded |
| NonInvestableAssetDialog | 3: name, value, growth | Add two; name excluded |
| SocialSecurityDialog | 5 editable: name, owner, FRA amount, claiming age, legacy convention | Add four; derived start date is output-only |
| OpeningRmdDialog | 2 per applicable account | Historical balance/distribution help plus copy action |
| AssumptionsView | 17: start/horizon, four death settings, four economic, seven tax | Extend icon help to label/control; add remaining help |
| RothConversionView | 7: enable, strategy, year, amount, target, frequency, stop | Add all seven |
| ResultsSummaryView | 15 plus claiming age per SS source | Reuse matching economic/planning/Roth help; preserve dynamic survivor help |
| SS analyzer | 6: mortality factors, discount, two dates, top-N spinner | Retain five existing explanations; add label hover, units and top-N |
| Weighted Current Strategy baseline | 2 survivor ages | Retain contextual help; add label hover |
| Monte Carlo single | 12: count/mode/return/volatility/seed, four inflation, three longevity | Retain semantics; standardize factory and label hover |
| Monte Carlo comparison | 13: same plus separate A/B survivor ages | Retain paired semantics; standardize factory and label hover |
| Break-even / projection chart / SS heat map | 1 metric selector each | Explain display-only selection; retain heat-map metric help |

No editable table cells, sliders, radio buttons or ChoiceBox inputs were found.
Tables are generated output; row selection, chart hover, year navigation and
percentile labels retain their existing help. Household uses PersonCard; account,
income, expense and asset list views open the audited dialogs. The income-source
ChoiceDialog chooses Pension versus Social Security and is self-explanatory.
Save/Cancel/Add/Edit/Delete and file chooser navigation are excluded. No separate
editable Medicare, filing-status, contribution, account-return or retirement-date
forms exist in this UI. Mortality categories/conditioning dates in Monte Carlo
are captured from the plan and displayed as context, not editable controls.

Semantic tracing: dialog converters/validators and load/apply paths; Person;
AccountFactory/ownership/inherited metadata; Pension/IncomeSource active months;
Expense and ProjectionEngine expense activation, inflation and survivor factor;
SocialSecurityIncome and monthly strategy valuation-year COLA; tax assumption
parsing/projected rules; ScheduledRothConversionPolicy and bracket-fill targets;
OpeningRmdWorkflowService; mortality hazard adjustment; analyzer request factories;
MonteCarloInputs and result/session isolation were inspected before wording.

Findings requiring truthful help, not financial changes: state/local flat-rate
fields are stored but are not read by the current Michigan projection tax path;
expense activation is by calendar-year overlap (opening recurring expenses alone
are prorated); pension COLA uses decimal input; mortality factors above one
increase hazard rather than extend life. Existing economic help contains forecast
ranges and describes COLA as only after claiming; those descriptions need correction.

## Implementation and coverage

The inventory covers 119 logical input/selector positions, counting one PersonCard
and one applicable Opening RMD account. Repeated people, accounts and Social Security
quick-edit rows add instances. Types are TextField, ComboBox, DatePicker, CheckBox
and Spinner. Three display-only metric selectors were reviewed separately from
planning assumptions. The inventory above records which fields received new help
and which retained existing help; exclusions are deliberate, not missing coverage.

`InputHelp` delegates tooltip construction to the unchanged `HelpIcon` factory.
It sets descriptive accessible help only when none exists. Its explicit label
linking shares the same Tooltip and supplies labelFor. Simple audited grids call
a small direct-child label linker; there is no application-wide reflection or
automatic control mutation. `PlanningInputHelp` and `RothInputHelp` keep duplicated
Assumptions/Results wording consistent. Other wording remains beside its owning
view. No event handlers, values, validation, layout constraints or financial
calculations changed.

Economic help remains available through its original icons and now also through
controls/labels. Four economic explanations were shortened, forecast ranges
removed, and actual inflation/COLA scope corrected. Existing dynamic survivor
help and chart help remain intact. Existing SS mortality/valuation descriptions
were retained with units/direction clarified. Monte Carlo help now uses the common
wrapping/delay behavior and label targets; expected-return semantics are preserved.

Representative wording:

- Federal rate change: “Additive change to federal marginal rates in percentage
  points ... Enter 2 for a two-point increase, not a 2% relative increase.”
- Custom Roth target: “Household federal taxable-income target in nominal dollars
  ... This is total taxable income, not the amount to convert.”
- Mortality: “Above 1.00 increases modeled mortality; below 1.00 decreases it.”
- Local tax: “Stored local income-tax percentage assumption. The current
  projection does not apply this field as an additional local tax.”

Visible labels and existing accessible names remain present. Tooltips do not
replace validation messages. Label targets preserve explanatory access for
conditional/disabled fields. No keyboard handlers or focus traversal were changed.

## Automated and visual verification

`InputTooltipTest` adds 11 tests covering representative controls in every major
form, tooltip factory settings, shared label attachment, retained values/disabled
states, validation, unchanged serialized plans/callback counts, and no analysis
execution during help installation. A bounded form traversal checks meaningful
inputs with explicit exclusions and stops at composite controls rather than
inspecting JavaFX skin internals. Break-even's existing view test adds semantic
assertions for its display-only metric help; no existing assertions were weakened.

Focused UI/Monte Carlo command: `-Dtest=*ViewTest,*Dialog*Test,*Input*Test,*SessionTest,MonteCarlo*Test,!*BenchmarkTest`.
Result: **396 tests, 0 failures, 0 errors, 6 skipped**, BUILD SUCCESS.
Log: `target/tooltips-ui-regressions.log`.

Opt-in `InputTooltipPreviewTest` with `-Dinput.tooltips.preview=true`: **1 test,
0 failures/errors/skips**, BUILD SUCCESS. It opens 14 representative forms and
checks actual Tooltip showing after JavaFX hover events, shared label hover where
applicable, wrapping, width, height and unchanged input bounds. Preview artifacts
are in `target/input-tooltip-preview/`; each screen has `<name>.png` and a separate
`<name>-help.png` popup snapshot. `geometry.txt` records every assertion result.

All 28 images were visually inspected. Screens use 1250×940 where applicable;
the SS analyzer uses its window size and dialogs their natural layout.

| Preview name | Visual review |
| --- | --- |
| household | DOB explanation readable; name/category layout retained |
| accounts | Inherited fields and relationship help readable; no overlap |
| pension | Decimal COLA units explicit; label hover works |
| expenses | Opening-year dollar basis and future purchase meaning clear |
| assumptions | Existing icons retained; input/label help wraps cleanly |
| social-security | Derived disabled date remains distinct from editable values |
| roth | Target/strategy explanation readable; conditional fields retained |
| opening-rmd | Historical context readable; validation remains separately visible |
| non-investable | Value/appreciation form unchanged; concise help |
| ss-deterministic | Valuation help readable above original analyzer layout |
| ss-weighted | Conditioning explanation readable; baseline controls retained |
| mc-fixed | Arithmetic-return explanation fits without clipping |
| mc-longevity | Mortality direction/range clear; longest popup remains compact |
| mc-comparison | Inflation-floor explanation clear; A/B controls unchanged |

Popup outer widths were 393px including shadow, with content width capped at
375px. Heights were 85–139px. All tested input bounds were identical before/after
hover. The configured delay is 250ms; popups were checked after an 800ms wait.
No popup text clipping or overlap within the captured forms was observed.

Native desktop capture returned black and native pointer hover was unreliable in
this Windows session. The final harness therefore uses JavaFX hover events and
JavaFX scene/popup snapshots. Initial harness failures (native hover and a modal
showAndWait timeout) were resolved in test infrastructure only. Physical mouse
timing, screen-reader speech and manual keyboard operation are not certified;
existing keyboard regression tests remain part of the suite.

## Product-review observations and boundaries

The state/local flat-rate fields deserve future product review because their
stored values do not drive the current Michigan tax path. Pension COLA decimal
entry differs from percentage-entry conventions elsewhere; help explains the
existing behavior without changing it. Expense calendar-year activation and
opening-year proration are documented as implemented. No financial defect was
fixed in this milestone.

Production changes are confined to UI classes. ProjectionEngine, tax/Medicare/RMD,
Roth/SS/pension calculations, inflation, stochastic generation, mortality, random
streams, paired reduction, persistence schema, CSV and PDF semantics are unchanged.
The final boundary is 24 files: 20 production UI files, 3 test files and this report.
Nothing was staged, committed or pushed.

## Final verification and exact file boundary

Full non-benchmark command: `-Dtest=*Test,!*BenchmarkTest` using IntelliJ bundled
Maven and the repository-local `.codex-m2/repository` required by AGENTS.md.
Result: **1,644 tests, 0 failures, 0 errors, 9 skipped**, BUILD SUCCESS in 1:41.
Log: `target/tooltips-full.log`. Relative to the 1,632-test baseline, this adds
11 focused tests and one opt-in preview test; the latter is skipped in the normal
suite and passed separately with previews enabled. Existing financial, Monte Carlo,
PDF, persistence and keyboard tests were included without weakening assertions.

`git diff --check`: passed (no whitespace errors). `git diff --cached --name-only`:
empty. Starting HEAD remains unchanged. Exact final `git status --short` follows;
this is also the complete 24-file boundary, including untracked new files.

```text
 M src/main/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenAnalysisView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/charts/ProjectionChartView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/components/PersonCard.java
 M src/main/java/com/daviddunn/retirementplanner/ui/dialogs/AccountDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/dialogs/ExpenseDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/dialogs/NonInvestableAssetDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/dialogs/OpeningRmdDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/dialogs/PensionDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/dialogs/SocialSecurityDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/help/HelpText.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/CurrentStrategyBaselineView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/RothConversionView.java
 M src/test/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenAnalysisViewTest.java
?? APP-WIDE-INPUT-TOOLTIPS.md
?? src/main/java/com/daviddunn/retirementplanner/ui/controls/InputHelp.java
?? src/main/java/com/daviddunn/retirementplanner/ui/help/PlanningInputHelp.java
?? src/main/java/com/daviddunn/retirementplanner/ui/help/RothInputHelp.java
?? src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/InputTooltipPreviewTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/InputTooltipTest.java
```
