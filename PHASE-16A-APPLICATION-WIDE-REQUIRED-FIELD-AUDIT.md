# Phase 16A — Application-Wide Required Field Indicator Audit

## Executive summary

The JavaFX application has no application-wide required-field indicator system. Cues are scattered across validation messages, prompt text, tooltips, wizard copy, and a few isolated asterisks. The household wizard marks Birth Date and Mortality category; Assumptions marks State of Residence and several labels in its guided/basic editor. Most ordinary editor and analyzer labels have no marker. WizardFieldRow defines a requiredness vocabulary but current steps do not use it as a shared form mechanism.

Requiredness must follow actual Apply/Run validation, not visual prominence or a non-null default. Many controls are optional; others become required only after enabling a feature or selecting a mode. A static star on every form row would mislead users.

Recommendation: add shared accessible required-label support with explicit, bindable state. Keep validation authoritative and separate from rendering. Preserve label text, control IDs, tooltips, label associations, focus order and behavior. This phase made no production changes.

## Scope and inventory method

Inspected the JavaFX tree under src/main/java/com/daviddunn/retirementplanner/ui (164 source files), including main views, Summary Key Assumptions, all seven New Plan wizard steps, shared editors, Application Settings, Social Security analyzer inputs, Monte Carlo and paired comparison, Break-Even, history/filter dialogs and utilities.

The inventory counts semantic field roles once per template: a spouse row, repeated per-account RMD fields, or an editor instantiated per record counts once. Runtime control count varies by plan. Table columns, output labels, navigation, actions, filters, confirmations and export file choosers are noted but excluded from financial/configuration input totals. Dynamic requirements not uniformly declared by current code are identified rather than inferred.

Status: A = always needs a valid value at submit/run; C = conditionally required; O = optional; D = defaulted and valid without user entry; R = derived/read-only. A default may satisfy a validator; the marker should not imply that users need to type over it.

## Complete logical field inventory

### Main workspace and Results Summary

| Screen / class | Editable field roles | Status, condition and validation evidence | Existing marker / recommendation |
|---|---|---|---|
| Household — HouseholdView / PersonCard | Primary First Name and Last Name | O; PersonCard explicitly preserves optional names | None; leave unmarked |
| Household | Primary Birth Date; Mortality category | A; PersonInformationValidation rejects missing/invalid values on Apply | No ordinary marker; mark both |
| Household | Include spouse toggle | O; Primary-only is valid | No star |
| Household | Spouse First Name and Last Name | O when spouse exists | No star |
| Household | Spouse Birth Date; Mortality category | C when Include spouse selected; same person validation | Guided view marks; bind elsewhere to spouse selection |
| Accounts — AccountDialog | Name | A/ambiguous; trimmed and passed to constructors but blank-name rule is not uniform/documented | No marker; resolve policy |
| Accounts | Type; Owner | A; missing owner explicitly rejected for individually owned accounts; type controls valid construction | No marker; mark |
| Accounts | Current Balance | A; parsed and domain-validated | No marker; mark |
| Accounts | Original Owner DOB; Date of Death; Beneficiary Relationship | C for inherited IRA types; otherwise inapplicable | No marker; conditional |
| Accounts | Separation status/date; retirement-conditions status/date | O/C; shared editor validates status/date consistency but facts are not universal requirements | Conditional only for date-required status |
| Accounts | Explicit employer availability action/status/first eligible year | C for Traditional 401(k)/403(b); new account requires explicit state, legacy UNKNOWN can be retained; year optional for indefinite unavailability | Dynamic by type, status and new/edit |
| AccountsView | Selected row for Edit/Remove | O action state; buttons disabled until selection | Not a data field |
| Income — PensionDialog | Name | O/ambiguous; no consistent nonblank rule established | Resolve before marking |
| Income | Owner; Start Date; Monthly Benefit | A; needed to construct/project pension income | Mark |
| Income | End Date | O; blank means no scheduled end | No star |
| Income | Survivor Monthly Benefit | O; blank means no survivor benefit | No star |
| Income | Annual COLA Rate | D; zero/no increase is valid and defaulted | No star |
| Income | Participant separation/retirement statuses and dates | O/C by shared participant-facts rules | Conditional |
| Income — SocialSecurityDialog | Name | O/D; defaults to Social Security; no blank guard | No star |
| Income | Owner; Gross FRA Monthly Benefit; Claiming Age | A; required to build source | Mark |
| Income | Benefit Start Date | R; derived from DOB and claiming age | Never mark |
| Income | Use today’s dollar convention | O/C; only shown for legacy valuation-year mismatch | No star |
| Income wizard | Add/edit Social Security source | O at step level; no source is valid | No star |
| Expenses — ExpenseDialog | Description; Expense Type; Annual Amount; Effective Date | A for valid record; description blank explicitly rejected; type may be defaulted | Mark active required values |
| Expenses | Escalation | C/D; recurring only, defaulted; one-time does not use it | Conditional |
| Expenses | End Date | O; if supplied it is validated; hidden for one-time | No star |
| Expenses | Annual Expense Volatility (%) | D/C; zero default; only nonnegative recurring supported | No star |
| Non-Investible Assets — NonInvestableAssetDialog | Asset Name; Current Value; Growth Rate | A for valid submission; blank name and negative value rejected; numeric parse required | Mark |
| Assumptions — AssumptionsView | Projection Start Date; projection length / assumed second-death horizon | A; valid date/horizon required; label changes for single/couple | Mark |
| Assumptions | Household survival scenario | D; existing default valid | Mark only if cleared selection can block Apply |
| Assumptions | Death Year | C when selected scenario configures death | Conditional |
| Assumptions | Survivor Benefit Claiming Age | C when survivor policy requires it and spouse exists | Conditional |
| Assumptions | Post-Death Expense Factor (%) | D/C; 100% default, relevant under death scenario | Defaulted; conditional applicability |
| Assumptions | Investment Return; General Inflation; Healthcare Inflation; Social Security COLA | D; loaded/defaulted and range-validated | No star while valid defaults exist |
| Assumptions | Filing Status | D/A; default satisfies required enum on Apply | Clarify marker meaning if unset can block Apply |
| Assumptions | State of Residence | A; explicitly displays State of Residence * and * Required | Existing marker; standardize |
| Assumptions | Federal Bracket Growth; Standard Deduction Growth | D; valid defaults and numeric validation | No star while defaults valid |
| Assumptions | Future Federal Tax Rate Change; Effective Year | D/C; zero-change default; year associated with nonzero adjustment | Conditional; verify parser branch |
| Assumptions | Legacy State Income Tax Rate; Local Income Tax Rate; Estimated Heir Tax Rate | D/O; defaults exist, zero local valid, jurisdiction may supersede legacy rate | No star solely because present |
| Assumptions | Primary/Spouse Social Security Claiming Age | C only for owner with source | Conditional |
| Assumptions | Monthly benefit preview | R; derived using shared presentation logic | Never mark |
| RothConversionView | Enable Roth conversion | O feature toggle; disabled valid | Never mark |
| RothConversionView | Strategy; Conversion Year; Frequency; Stop Rule | D/C; schedule semantics apply when enabled | Conditional |
| RothConversionView | Conversion Amount | C for enabled fixed-amount strategy | Conditional |
| RothConversionView | Target Taxable Income | C for enabled custom-target strategy instead of amount | Conditional |
| Summary Key Assumptions | Investment Return; General Inflation | D; mirrors Assumptions validation | Same state as source fields |
| Summary Key Assumptions | Roth enabled/strategy/start year/amount/target/frequency/stop rule | O/C/D; shared RothConversionConfiguration transition validator | Mirror same conditional rules |
| Summary Key Assumptions | Planning horizon | A; same Assumptions contract | Mark |
| Summary Key Assumptions | Death scenario/year/survivor age | D/C; same household/scenario policy | Conditional |
| Summary Key Assumptions | Primary/Spouse claiming age | C when source configured | Conditional |

### New Retirement Plan wizard

| Step / class | Editable field roles | Status / actual gate | Current cue |
|---|---|---|---|
| Household — HouseholdWizardStep / PersonCard(true) | Just me/couple selection | D; Just me valid | No star |
| Household | Primary First/Last Name | O; wizard says optional | No star |
| Household | Primary Birth Date; Mortality category | A | Existing stars and “* Required” legend |
| Household | Spouse Birth Date; Mortality category | C when couple selected | Guided markers; spouse card conditional |
| Accounts — AccountsWizardStep | AccountDialog fields above | O at step level; no accounts valid | Empty state says optional |
| Income — IncomeWizardStep | Pension/SocialSecurity fields above | O at step level; no income valid | Empty list permitted |
| Expenses — ExpensesWizardStep | ExpenseDialog fields above | O at step level; no expenses valid | Empty state says optional |
| Assumptions — AssumptionsWizardStep | Basic assumption controls | D; defaults accepted; review encouraged, not re-entry | Do not mark every default |
| Social Security — SocialSecurityWizardStep | Add/edit per-person source | O at step level | Not-entered state says optional |
| Review — ReviewWizardStep | Prior Dec. 31 balance; current-year RMD already distributed, generated per applicable account | C when opening RMD applies; nonnegative, zero valid | Conditional prompts |
| Wizard shell | Previously visited-step selector and progress | O navigation state; accessible copy says progress is not completeness | Never mark |

### Application Settings and preferences

| Screen | Field/action | Status and evidence | Indicator |
|---|---|---|---|
| ApplicationSettingsDialog | Theme | D; default/loaded value exists | No star |
| Same | Default retirement-plan directory | O; blank accepted, path validated if provided | No star |
| Same | Show All Analyzer Introductions Again | Action, not field | No star |
| AnalyzerIntroductionDialog | Don’t show this introduction again | O preference | No star |

### Analysis interfaces

| Screen / class | Editable roles | Status and condition | Indicator recommendation |
|---|---|---|---|
| MonteCarloAnalysisView | Simulations; Analysis Mode; Inflation Mode | D/A for Run; defaults satisfy, mode chooses active parameters | Mark only if cleared selection blocks Run |
| Same | Expected return; Return volatility; Random seed | Valid values needed for run, defaults or plan-derived values supplied | Distinguish valid-default from manual entry |
| Same | Expected inflation; inflation volatility; floor | C when stochastic inflation selected | Conditional |
| Same | Primary/Spouse mortality adjustment | C/D in longevity mode for present household members | By mode/person |
| Same | Survivor Social Security Claiming Age | C if longevity survivor scenario needs it | Conditional |
| MonteCarloStrategyComparisonView | Simulation count; mode; inflation choice; expected return; volatility; seed | D/A; run validates parameters; defaults commonly satisfy | Mirror run validator |
| Same | Inflation mean/volatility/floor | C for stochastic inflation | Conditional |
| Same | Primary/Spouse retirement ages; survivor ages for candidates A/B | C by candidate completeness, household type and death scenario | Per candidate |
| SocialSecurityStrategyAnalyzerDialog | Primary/Spouse mortality adjustment | D/C; initialized 1.00; mode/person-specific | Conditional |
| Same | Mortality conditioning date; valuation date; real discount rate | D/A in applicable modes; dates plan-initialized, rate 1% default | Valid defaults generally satisfy |
| Same | Integrated candidate count (1–20) | D/C for Quick Comparison only; default 10 | Conditional |
| Same | Current Strategy Baseline primary/spouse survivor ages | O/C; blank omits explicit baseline | Required only if baseline requested |
| SinglePersonMortalityView | Mortality factor; discount rate; conditioning date; valuation date | D/A for applicable analysis; initialized/restored and validated | Valid defaults |
| SinglePersonIntegratedView | Death Year | C when explicit death-year scenario selected | Conditional |
| Analyzer chooser/history | Choice, role/kind selectors | O navigation/filter inputs | No star |
| ClaimingStrategyHeatMapView | Metric selector | D presentation selection | No star |
| BreakEvenAnalysisView | Metric selector | D selection from completed result | No star |
| Projection/analyzer history | Show Changed Values Only and role/kind filters | O presentation filters | No star |
| Break-Even launch | Compatible projection/result availability | C controller prerequisite, not free-form field | Disabled/explanatory state |

### Additional dialogs and utilities

| Dialog / class | Editable roles | Status |
|---|---|---|
| OpeningRmdDialog | Prior Dec. 31 balance; current-year RMD already distributed per applicable account | C; only applicable rows shown; nonnegative values, zero allowed |
| EmployerRothAvailabilityEditor | Availability status and optional first eligible year | C by account type/new-vs-legacy/status |
| RothDestinationCreationDialog | Confirm/cancel | Action; cancel valid and nonmutating |
| BaselineDetailsView | Show Changed Values Only | O display filter |
| ProjectionYearDetailsDialog | Year selection if exposed | D/read-only browsing |
| Export dialogs | Destination path and overwrite confirmation | Export workflow action, not financial input |
| About/splash/introduction | No financial input; introduction suppression checkbox optional | Excluded |

## Inventory totals

Counts use unique semantic roles, not rendered nodes. Read-only previews and presentation-only selectors/filters are listed but excluded from the financial/configuration field count.

| Measure | Count |
|---|---:|
| JavaFX UI source files inspected | 164 |
| Distinct input-bearing screen/dialog workflows | 31 |
| Editable financial/configuration roles | 96 |
| Always required | 23 |
| Conditionally required | 31 |
| Optional | 25 |
| Defaulted and valid without user entry | 17 |
| Derived/read-only values adjacent to inputs | 3 |
| Existing visible required-star roles | 5 |
| Required roles without consistent field marker | 49 |
| Ambiguous requirements before static marking | 8 |

Each repeated spouse/account/record template counts once. Existing markers occur in guided person fields and Assumptions State/basic guided labels. Counts are inventory bookkeeping, not a runtime schema.

## Validation architecture and findings

- Main views own parsing, draft state and Apply validation. Editors validate through dialog result conversion and domain constructors.
- Wizard steps reuse editors; Accounts, Income, Expenses and Social Security permit empty lists.
- Analyzer values validate at Run/request creation. Dormant mode controls may be irrelevant or blank.
- Roth conversion uses shared RothConversionConfiguration validation across RothConversionView and Summary.
- Settings defaults Theme and permits blank default directory.
- InputHelp handles tooltips/label linkage, not requiredness. WizardFieldRow.Requiredness is not the shared validation authority.

### Ambiguities and potential pre-existing issues

1. Account Name blank acceptance is not uniform/documented across account constructors.
2. Pension Name blank acceptance is unclear.
3. Numeric assumptions must parse at Apply but normally have a valid default; “valid required” differs from “user must enter.”
4. Future Federal Tax Rate Change Effective Year appears conditional on nonzero change; verify exact parser branch.
5. Survivor requirements depend on spouse, selected death scenario and survivor policy.
6. Social Security analyzer dates/rates can be required for Run yet already defaulted.
7. A future Roth schedule can be valid with zero current source balance; capacity warnings are not required text fields.
8. Employer availability state is required for new 401(k)/403(b), while legacy UNKNOWN may be retained; year is optional for indefinite unavailability.

These are classification/policy ambiguities, not authorization to alter validation.

## Required-indicator design

Use shared label support or RequiredFieldRow with explicit state (REQUIRED, CONDITIONAL, OPTIONAL, DEFAULTED, READ_ONLY) and an observable current-required property. The state must come from existing validation/configuration policy, not control type or disabled status.

Render a literal asterisk immediately after the label via a non-focusable Label graphic or equivalent. Preserve base label text, control ID, tooltip and label association. Accessible help/description should announce “Required” while active; color alone is insufficient. Keep existing accessible names where feasible.

Use semantic -app-error from theme CSS (currently light #a12622 and dark #ffaba5), not hard-coded color. One “* Required field” legend per independent form is preferable. Avoid repeated legends per card and avoid widening Summary. Application Settings needs no current legend.

Show a marker only while current validation requires the field. Hidden/inapplicable fields have none. A disabled control retains the star only if it remains required on submission. Do not duplicate requirement conditions; use shared validator state where practical.

Recommended API concept: RequiredFieldSupport.attach(Label, Control, ObservableBooleanValue required), or a small RequiredFieldRow. Keep rendering separate from validation; do not expand InputHelp, which is descriptive-only.

## Results Summary preservation

Key Assumptions is on Summary’s right side and includes return/inflation, Roth configuration, horizon/death/survivor values and Social Security claiming ages. Preserve location, width, controls, tooltips, synchronization, Apply/Cancel, dirty state and freshness behavior. Use compact markers and one small legend only if needed; no layout changes.

## Settings and wizard observations

Settings Theme is defaulted, plan directory is blank-valid, reset introductions is an action. No setting requires manual entry.

Household wizard primary Birth Date and Mortality category are required; names are optional; spouse equivalents become required only for couple. Accounts, Income, Expenses and Social Security steps are optional. Assumptions defaults are accepted. Review RMD fields are conditionally required. Avoid a global legend that implies optional list steps need entries.

## Alternatives

| Option | Evaluation |
|---|---|
| Manually append asterisk to label strings | Reject: duplication, altered test/accessibility text, no dynamic behavior |
| CSS pseudo-element | Paint only; business state and accessibility remain unsolved |
| Label graphic utility | Good primitive; dynamic and preserves text; needs accessible description |
| Shared bound RequiredFieldRow/label support | Recommended: consistent, testable, adaptable to layouts |
| Expand InputHelp into validation policy | Reject: mixes concerns |

## Proposed implementation stages

1. **16B1 Shared infrastructure:** reusable indicator, theme token, accessibility, focused state tests.
2. **16B2 Main inputs and Summary:** Household, Accounts, Income, Expenses, Assets, Assumptions, Roth and Summary Key Assumptions.
3. **16B3 Wizard and Settings:** wizard context/nested editors; current Settings have no required fields.
4. **16B4 Analyzer and remaining dialogs:** mode- and household-specific requirements.
5. **16B5 Reconciliation and acceptance:** inventory reconciliation, condition transitions, themes, accessibility, keyboard, compact layout and full Maven suite.

Each stage should have its own acceptance gate. Do not change validation, defaults, persistence or financial behavior.

## Risks and compatibility

- Changing label text can break exact-text tests, selectors and accessibility. Preserve text and render an accessible graphic.
- Marker width may affect Summary Key Assumptions and narrow dialogs.
- Static marks become stale after household, mode, account/expense type, Roth or death-scenario changes.
- Disabled is not equivalent to optional; hidden is not proof of nonrequirement.
- Inline style specificity may override theme colors.
- Keep markers out of tab order and preserve label/control association.
- Clarify that required means valid at submit, even if a valid default is already present.
- No validation, dirty tracking, persistence, fingerprint, projection or historical result changes belong in Phase 16A.

## Verification and repository status

Initial git status --short was clean. No production code, tests, CSS, resources or build files were changed. No JavaFX screenshot/native rendering was performed because this phase is audit-only.

Focused existing tests ran with the required IntelliJ Maven and repository-local .codex-m2 repository:
HouseholdViewTest, AssumptionsViewTest, WizardClarityTest, AccountsWizardStepTest, IncomeWizardStepTest, ExpensesWizardStepTest, and an ApplicationSettingsDialogTest selector.
Result: **105 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.** Breakdown: AssumptionsViewTest 40, HouseholdViewTest 16, AccountsWizardStepTest 16, ExpensesWizardStepTest 5, IncomeWizardStepTest 10, WizardClarityTest 18. Maven found no matching ApplicationSettingsDialogTest, so Settings is not represented in the count. Full suite was not run.

## Decisions requiring approval

1. Approve shared component/helper and literal asterisk convention.
2. Confirm the legend means “valid value required when active form/mode is submitted,” even if a valid default already exists.
3. Resolve Account and Pension blank-name behavior before marking.
4. Require implementation to mirror current dynamic validation and report inconsistencies without changing behavior.
5. Approve stages 16B1–16B5, with separate review gates.

**Phase 16A is audit-only and complete. No implementation has begun.**

