# Phase 5B — Complete New Retirement Plan Wizard

## Architecture audit and gate

The audit preceded production changes. Phase 5A already provides the correct
transaction boundary: File → New opens `NewRetirementPlanWizard`, which owns a
fresh `RetirementPlanFactory.createSinglePersonPlan()` draft. The controller is
not involved in wizard editing. Only a completed dialog result reaches the
existing departure guard and `ApplicationController.newPlan(plan)` activation.
Cancel also preserves unapplied edits in the existing tabs. Opening an existing
file still uses `ApplicationController.open()` and bypasses creation.

No financial-model, JSON, ownership, calculation-engine, or active-plan lifecycle
refactor was required. No architecture gate was triggered. The existing model
and object builders can serve every implemented page directly.

### Accounts

`AccountFactory.getSupportedTypes()` is the authoritative list of eleven account
types. `AccountDialog` already uses this source, the account ownership rules, and
the factory to build concrete account subclasses. Tax treatment, RMD eligibility,
and Roth conversion eligibility are determined by those subclasses; the wizard
does not expose or duplicate those rules. Retirement accounts are individually
owned. Brokerage/checking/savings support joint ownership for a couple; a genuine
single-person household has Primary ownership only. Inherited accounts require
the existing original-owner birth/death dates and beneficiary relationship.

Accounts are stored in `AccountPortfolio`, with existing add/replace/remove APIs.
The wizard uses the existing balance parser and currency formatter. Names are
optional under existing semantics. A zero-account plan is legal.

Opening-year RMD history is a separate established workflow. Current balance is
not automatically substituted for prior December 31 history. Review offers the
existing `OpeningRmdDialog` when applicable and reminds users about missing/stale
facts. Ordinary edits retaining account type and owner retain entered history.

### Income

The supported persisted income subtypes are `SocialSecurityIncome` and `Pension`.
There is no generic employment/rental/other guaranteed-income subtype to expose.
Sources live on actual Persons and carry Primary/Spouse ownership. Explicit
ownership edits move the replacement source through the existing Person APIs.

Social Security uses an FRA monthly benefit, benefit valuation year, and claiming
age. The existing dialog/calculator derives the start date from the owner's birth
date and claiming age; it remains read-only. The initial interface offers at most
one Social Security record per actual person, consistent with ordinary analysis
inputs. A backward birth-date edit refreshes this same derived date, preserving
the original benefit valuation year and every other source value. Changing plan
start does not silently rebase an entered FRA amount. No Social Security engine,
survivor policy, benefit calculation, or legacy-COLA behavior changed.

Pensions use monthly amount, required start date, optional end date, decimal COLA
rate, and optional survivor monthly amount. The existing pension dialog/builders
are reused. Survivor inputs are absent for a single-person household. Income is
optional; spending may be funded by accounts. No missing-pension warning appears.

### Expenses

Expenses belong to the household; the model has no owner or frequency property.
Recurring amounts are annual and use General or Healthcare growth. Their start
and end dates may be unrestricted. One-time purchases require the existing
purchase date, use General growth, and end on that date. `ExpenseDialog` remains
the authoritative builder/validator. Zero expenses are legal but warrant a
non-blocking spending reminder.

### Assumptions

`PlanningAssumptions` contains the projection start/length and existing economic,
tax, withdrawal, and death-scenario assumptions. The factory supplies today's
start, 40 years, 8% return, and 3% general/healthcare inflation and Social Security
COLA. Filing status remains the existing independently configured MFJ default;
the wizard makes it visible and editable, including for a single-person plan.

Other preserved defaults include 2.5% bracket/deduction growth, zero state/local
rate, 25% heir rate, taxable-first withdrawal order, BOTH_SURVIVE with no death
year/survivor election, no Roth request, and no financial records. Longevity
conditioning and present-value dates belong to analyzer sessions, not plan JSON.

The compact guided presentation reuses the exact `AssumptionsView` controls,
tooltips, parser, validation, and atomic apply path. Advanced inputs stay hidden
with their loaded values so the same parser preserves them. The normal tab uses
the original presentation. Editing wizard assumptions replaces the actual draft
assumptions through the existing apply path, without a parallel assumptions model.

### Validation, persistence, and UI infrastructure

Completed Person validation remains `PersonInformationValidation`: DOB and
mortality category required, names optional. Existing item constructors, dialog
builders, plan reference validation, and assumptions parser remain authoritative.
The new opt-in dialog decorator validates input presence/number/date syntax and
renders builder errors inline; it adds no financial ranges or calculation rules.

The existing JSON repository serializes these same domain objects and validates
household references. No wizard-only persisted properties, schema changes, or
migrations were added. Existing JavaFX FutureTask/dialog-response test infrastructure
and opt-in PNG fixtures provide UI verification without new dependencies.

## Final flow and selected fields

Household → Accounts → Income → Expenses → Assumptions → Review → Create Plan.
The shell supplies Back, Next, Cancel, current-step text, a subdued breadcrumb and
progress bar. Create Plan is visible/default only on Review and cannot be invoked
early. `onEntering()` refreshes dependent presentations and Review. Section Edit
buttons navigate directly back from Review. Input state survives Back/Next.

| Step | Initial fields | Required/default/optional decisions |
| --- | --- | --- |
| Household | First/last name, DOB, mortality category; optional Add/Remove Spouse | Names optional; DOB/category required for each actual person; starts Primary-only |
| Accounts | Name, authoritative type, owner, balance; inherited metadata when applicable | Name optional; type/owner/balance required per item; inherited dates/relationship required when relevant; collection optional |
| Income: Social Security | Name, owner, FRA monthly amount, claim age, derived read-only start | Name optional; owner/amount/claim age required per record; existing claim-age choices/defaults; whole source optional |
| Income: Pension | Name, owner, monthly benefit, start/end, decimal COLA, couple-only survivor amount | Owner/amount/start/COLA required with existing defaults; name/end/survivor optional; whole source optional |
| Expenses | Description, recurring/one-time, annual/purchase amount, growth, start/end | Description/type/amount/growth required per item; recurring dates optional; one-time purchase date required; collection optional |
| Assumptions | Projection start, length, investment return, general inflation, healthcare inflation, SS COLA, filing status | Required controls show authoritative loaded defaults; existing parser/constraints apply |
| Review | Live summaries of all five sections, Edit actions, readiness reminders | Full validation before Create; legal omissions remain non-blocking |

Advanced tax details/future tax changes, death/survivor scenario elections,
withdrawal strategy, Roth configuration, non-investable assets, baselines,
advanced analyzer settings, and reporting remain in the existing tabs/tools.
Unsupported other-income and expense-ownership/frequency fields are not invented.

## Required fields, help, and validation

Required labels use `*` with a visible `* Required` explanation before validation.
Errors appear after Next/Create or item OK, identify a field, mark its input, and
focus it. Edits clear the affected presentation error. Missing/invalid entries
do not commit an item. The existing assumptions atomic apply path blocks invalid
Next. Create revalidates all registered pages and the complete plan references.

Existing input tooltip texts remain attached directly to controls. No duplicate
label-side help icons were added. The guided presentation is opt-in; existing
item editors and the normal Assumptions tab retain their behavior.

## Review, warnings, and spouse dependencies

Review is rendered directly from draft objects on entry. It shows actual member
names/DOB/categories; account type/owner/balance; FRA benefit basis, claim age and
derived dates; pension amounts/dates/COLA/survivor amounts; expense amounts/growth/
timing; horizon/economic rates/filing status. It does not calculate or cache a
second financial model or projection.

Blocking errors represent existing Person/reference validation failures. Separate
non-blocking reminders cover no accounts, no retirement spending, and applicable
missing/stale opening RMD facts. Optional Social Security/pensions are not treated
as invalid or automatically alarming. Legal empty plans can still be created.

Remove Spouse uses `RetirementPlan.setSpouse(null)` and catches its existing
dependency rejection. Spouse/joint accounts, spouse incomes, survivor pensions,
and existing couple-only assumptions are preserved and explained. Nothing is
silently deleted, transferred, or reassigned. The user can return to the relevant
page and explicitly remove/reassign those items. Household-wide expenses are
not fabricated as spouse dependencies.

## Draft transaction and post-create integration

Every page and nested editor targets the one isolated factory-created plan.
Unfinished control values are session state, not active-plan writes. Cancel at
any step discards the draft without touching current plan identity, JSON, file,
revision, dirty flag, or unapplied tab edits. The normal departure guard still
runs only after a valid creation result.

Create activates the same completed draft through the existing controller path,
loads existing tabs, and selects Household. Financially populated plans request
the normal projection/results refresh. Completely empty legal plans retain
Phase 5A's deferred projection. No file is saved automatically. Native Save/Save
As and existing-plan Open retain their established semantics.

## Tests and internal checkpoints

New focused tests cover every authoritative account type and inherited metadata,
ownership, CRUD, Social Security claiming/basis, pensions and survivor inputs,
expense timing/growth/one-time rules, default/edited/invalid assumptions, input
help/no icons, six-step navigation/progress, Review edits/warnings/errors,
whole-draft creation validation, dependency rejection, cancellation, persistence,
existing-view integration, projection/results and applicable analyzer smoke tests.

The Phase 5A Household-only component tests retain their assertions through an
explicit single-step fixture. Default File → New lifecycle tests exercise the
complete flow; their response helper advances to Review. No financial expected
values or regression assertions were weakened or changed.

| Checkpoint | Result |
| --- | --- |
| A Accounts | 77 tests; zero failures/errors |
| B Income | 124 tests, 1 optional preview skipped; zero failures/errors |
| C Expenses | 98 tests, 1 optional preview skipped; zero failures/errors |
| D Assumptions | 167 tests, 1 optional preview skipped; zero failures/errors |
| E Review | 106 tests, 1 optional preview skipped; zero failures/errors |
| F initial full-workflow integration | 110 tests, 1 optional preview skipped; zero failures/errors |
| Final focused behavior | 77 discovered; 75 passed, 2 opt-in previews skipped; zero failures/errors |
| Isolated Household preview | 1 passed; all six Phase 5A states and direct DOB hover |
| Isolated complete preview | 1 passed; nineteen Phase 5B states and direct return hover |
| Affected UI/domain regression | 652 tests, 1 optional preview skipped; zero failures/errors |
| Complete non-benchmark suite | 1,797 tests, 11 skipped; zero failures/errors; 2:07 |
| Complete benchmark-inclusive suite | 1,818 tests, 15 skipped; zero failures/errors; 5:15 |

The preview tests are intentionally run in separate JavaFX JVMs: combining desktop
hover fixtures exposed shared tooltip/popup state interference. Both actual input
hover checks pass in isolation. During development, a fixture assertion also needed
to capture a dialog Window before JavaFX detached its scene on successful close.

The complete non-benchmark suite passed: 1,797 tests, 11 skipped, zero failures
and errors. All runs use IntelliJ's bundled Maven with
`-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.
Focused selection is `-Dtest=*Wizard*Test`; non-benchmark selection is
`-Dtest=*,!*Benchmark*`; the complete run uses plain `test`. Each preview class
is selected separately with its corresponding `new.plan.wizard.preview=true`
or `complete.wizard.preview=true` property. Test settings already use the
repository's `target/test-user-home` through existing Surefire configuration.

Verification logs are `target/phase5b-focused.log`,
`target/phase5b-household-preview.log`, `target/phase5b-complete-preview.log`,
`target/phase5b-affected.log`, `target/phase5b-non-benchmark.log`, and
`target/phase5b-full.log`. Checkpoint logs are `target/phase5b-checkpoint-a.log`
through `target/phase5b-checkpoint-f.log`.

## Visual and end-to-end acceptance

Visually inspected nineteen Phase 5B page states: Household single/couple;
Accounts empty/single/couple; Income single/couple; Expenses empty/populated;
Assumptions defaults/error; Review single/couple/warnings/errors; and narrow
Household/Accounts/Assumptions/Review. Also inspected Review bottom-scroll states
and the return tooltip. Six existing Household preview states and DOB tooltip
are regenerated. PNGs/contact sheets/geometry are under
`target/phase5b-wizard-preview` and `target/phase5a-wizard-preview`.

At 760×700 all data-entry pages fit without outer scrolling. Populated Review
requires a modest scroll (536/604 pixels of content in a 453-pixel viewport).
At a 490×570 outer window, page content scrolls vertically while navigation stays
visible. Labels and controls align; required text is clear; no help icons or
horizontal input clipping appeared. Input layout bounds, rather than validation
border bounds, are used for alignment assertions.

Actual JavaFX File → New/menu and nested Add editors were exercised with synthetic
Alex/Sam fixtures. Single: IRA $800k, Roth $200k, brokerage $500k; couple adds spouse
IRA $500k and uses joint brokerage $750k. Sources include FRA $3,000/$2,400 at 67,
pensions $1,000/$900 with couple-only survivor amounts, household spending
$60k/$85k plus $6k healthcare, and a 2026 start/30-year horizon/6.5% return.

Both workflows passed Create → existing tabs → Save → reload → projection →
30 Results rows → integrated current-strategy and three-path seeded Monte Carlo
smoke checks. Complete JSON trees match after reload; projection/analyzers preserve
persisted values. The normal Save/Save As controller paths were used; the native
file chooser was bypassed by an explicit temporary test path.

A substantial couple draft canceled at Review preserves an existing dirty plan's
file/revision/content and unapplied assumption edits. Parameterized actual menu
tests also verify active-plan/dirty preservation at every step. Backward spouse
removal after full entry displays account/SS/pension dependencies and leaves the
complete draft JSON unchanged.

These are automated real-JavaFX acceptance workflows plus visual screenshot
inspection, not a separate human mouse/keyboard session. Native file chooser
interaction was not manually exercised.

## Compatibility and remaining limitations

No domain, persistence, tax, RMD/Roth, projection, Social Security, mortality,
Monte Carlo, analyzer ranking, reporting, or PDF algorithms were modified. No
expected financial values were edited. Existing JSON shapes, one/two-person
semantics, and existing detailed editing interfaces are preserved.

The complete passing regression suite confirms unchanged couple golden values,
deterministic results, longevity-weighted results, seeded Monte Carlo values,
PDF/report expectations, and legacy JSON compatibility. Phase 5A Household and
transaction guarantees remain covered alongside the complete navigation flow.
`git diff --check` passes; newly added files were also checked for trailing
whitespace. The exact-file manifest covers all 31 changed files. No staging,
commit, push, dependency, Java-version, or build-configuration changes occurred.

The wizard deliberately collects an initial plan rather than all advanced
settings. Optional empty/underfunded plans remain legal; normal projection funding
errors still apply. Applicable opening-year RMD facts must be supplied for a
ready projection. Very long item names may be elided in compact tables and remain
available in Edit/Review. Narrow pages and larger Review summaries scroll.

## Exact files changed

Production/UI:

```text
src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java
src/main/java/com/daviddunn/retirementplanner/ui/dialogs/AccountDialog.java
src/main/java/com/daviddunn/retirementplanner/ui/dialogs/ExpenseDialog.java
src/main/java/com/daviddunn/retirementplanner/ui/dialogs/PensionDialog.java
src/main/java/com/daviddunn/retirementplanner/ui/dialogs/SocialSecurityDialog.java
src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java
src/main/java/com/daviddunn/retirementplanner/ui/wizard/HouseholdWizardStep.java
src/main/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanDraft.java
src/main/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanWizardStep.java
src/main/java/com/daviddunn/retirementplanner/ui/wizard/NewRetirementPlanWizard.java
src/main/java/com/daviddunn/retirementplanner/ui/wizard/AccountsWizardStep.java
src/main/java/com/daviddunn/retirementplanner/ui/wizard/AssumptionsWizardStep.java
src/main/java/com/daviddunn/retirementplanner/ui/wizard/ExpensesWizardStep.java
src/main/java/com/daviddunn/retirementplanner/ui/wizard/IncomeWizardStep.java
src/main/java/com/daviddunn/retirementplanner/ui/wizard/ReviewWizardStep.java
src/main/java/com/daviddunn/retirementplanner/ui/wizard/WizardDialogSupport.java
src/main/java/com/daviddunn/retirementplanner/ui/wizard/WizardListStep.java
src/main/resources/css/new-plan-wizard.css
```

Tests/fixtures:

```text
src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanWizardLifecycleTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanWizardPreviewTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanWizardTestSupport.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewRetirementPlanWizardTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/AccountsWizardStepTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/AssumptionsWizardStepTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/CompleteWizardEndToEndTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/CompleteWizardFixtures.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/CompleteWizardPreviewTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/CompleteWizardTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/ExpensesWizardStepTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/IncomeWizardStepTest.java
```

Documentation: `PHASE-5B-COMPLETE-NEW-PLAN-WIZARD.md`.
Nothing staged, committed, or pushed.
