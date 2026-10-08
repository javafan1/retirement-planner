# Phase 5A — Guided New Retirement Plan Wizard

## Existing architecture audit

The audit preceded production edits. The desktop shell is `MainApplication` →
`MainWindow`; there is no separate MainView or PersonView. `HouseholdView` uses
two `PersonCard` editors, with the spouse editor hidden for a genuine one-person
household. The File → New menu was the sole user-facing New entry point.

Previously, `MainWindow.onNew()` called `confirmPlanDeparture()`, then
`ApplicationController.newPlan()`, then loaded the tabs. The controller immediately
activated `RetirementPlanFactory.createSinglePersonPlan()`, incremented the source
revision, cleared the file association and modified flag, and invalidated projection
caches. Startup also uses the controller's no-argument `newPlan()` before optionally
opening the last saved plan. That internal route remains compatible; it is not a
second user-facing creation workflow.

The factory supplies:

| Input | Existing default, preserved |
| --- | --- |
| Household | Primary with empty names, no birth date or mortality category; no spouse |
| Projection | Today's start date; 40 calendar years |
| Investment return | 8% |
| General/healthcare inflation and Social Security COLA | 3% each |
| Federal bracket and deduction growth | 2.5% each |
| State/local tax | 0% |
| Filing status | Existing `MARRIED_FILING_JOINTLY` default, configured independently of membership |
| Estimated heir tax rate | 25% |
| Future federal rate change | Unconfigured |
| Withdrawal ordering | `TAXABLE_FIRST` |
| Death scenario | `BOTH_SURVIVE`, no death year or survivor age; expense factor 100% |
| Financial records | Empty accounts, incomes, expenses, and non-investable assets |
| Roth request/baseline | None |

This phase does not infer filing status from household membership or modify factory
defaults. Users continue to review tax assumptions in the existing Assumptions tab.

`PersonInformationValidation` is the authoritative completed-editor validator:
birth date and mortality category are required; both names remain optional.
`PersonCard.readValidated()` already parses typed dates, invokes that validator,
focuses the invalid control, and returns an immutable `Edit` before any Person
mutation. No new age, future-date, or name restrictions were added. Legacy/draft
Person data may still have missing dates/categories. Projection readiness needs
each household member's birth date and the plan start date; it does not establish
whether the portfolio can fund the plan.

`RetirementPlan.setSpouse()` is the existing membership API. It validates ownership
and dependencies without deleting financial records. `Household.members()` returns
one or two actual Persons; an absent spouse is represented by null/Optional.empty.

`InputHelp.install()` attaches input tooltips, using `HelpIcon.createTooltip()` for
popup configuration. There is no centralized required-field decoration framework;
existing forms report validation locally. The wizard reuses the Person editor and
validator, with opt-in presentation rather than a parallel validation definition.

`JsonRetirementPlanRepository` validates household references and saves/loads the
complete existing JSON shape. `ApplicationController.open()` activates loaded plans,
sets their file association, clears modified state, updates last-opened settings,
and invalidates projections. Save/Save As still explicitly write files. Tab editors
may have unapplied dirty state independently of the controller's modified flag.
The existing departure guard handles Save/Discard/Cancel across these states.

UI tests use JUnit 5, the actual JavaFX toolkit, FX-thread FutureTasks, reflection
for existing private controls, and scheduled dialog responses. Existing opt-in
preview tests generate PNG snapshots without adding dependencies.

### Architecture gate finding

No substantial lifecycle, ownership, persistence, or validation refactor is required.
A factory-created plan is already an appropriate isolated creation draft. A small
controller overload activates a completed draft with the same lifecycle as New.
No domain or JSON schema changes are needed.

## Wizard framework and transaction

`NewRetirementPlanWizard` is a resizable JavaFX Dialog returning a RetirementPlan.
It owns `NewPlanDraft` and an immutable ordered list of implemented
`NewPlanWizardStep` pages. Pages supply a title, content, and validation/apply action.
The shell provides current-step text, a progress bar, Back/Next, Create Plan, Cancel,
and scroll/focus handling for invalid controls. Next validates its page; Create
revalidates all registered pages before the draft's final domain/reference check.
Back preserves controls. Only implemented pages contribute to progress/navigation.

Phase 5A registers Household alone: Step 1 of 1, Create Plan, and Cancel. Back/Next
are hidden and unmanaged. No empty Accounts/Income/Expenses/Assumptions/Review page
is exposed. Later phases can register additional step implementations without
rewriting the navigation shell.

The draft is a fresh `RetirementPlanFactory.createSinglePersonPlan()` instance;
it never reads the active plan, controller, repositories, or settings. Household
controls hold unfinished input, and validated immutable Person edits are applied to
the draft only after all selected people validate. Draft membership changes use
`setSpouse()`. Cancel, Escape, and window close produce no result. MainWindow has no
creation callback to run until a valid dialog result is returned.

The existing unsaved-changes guard now runs **after valid Create**, immediately
before activation. Running it before opening would discard/save old tab edits even
when the user later canceled the wizard. Opening/canceling now preserves the active
plan, file, cached projection, source revision, selected tab, modified flag, and
unapplied tab values. If the departure guard is canceled, the active plan and edits
remain intact and the completed new draft is abandoned. Save/Discard behavior at
actual replacement remains the existing behavior.

On accepted Create, `ApplicationController.newPlan(plan)` activates the draft,
increments the source revision, clears file association/modified state, and clears
caches. MainWindow loads the existing editors and selects Household. Nothing is
automatically saved. The first projection is deferred: a Household-only default
plan has no funding inputs and automatic projection can fail when future Medicare
costs begin. The new-plan load clears results without running the engine; normal
existing edit/open/refresh paths continue to project as before. No readiness or
financial calculation rule was changed.

## Household inputs and validation

| Person field | Required | Behavior |
| --- | --- | --- |
| First Name | No | Existing optional-name semantics |
| Last Name | No | Existing optional-name semantics |
| Birth Date | Yes | Existing DatePicker parsing and Person validator |
| Mortality category | Yes | Explicit Male/Female choice; no inferred category |

The wizard explains `* Required. First and last names are optional.` before data
entry and labels required controls with an asterisk. No errors appear on opening.
Create/Next attempts show concise inline field feedback and a person-specific
message, mark the invalid input, reveal it in the scroller, and focus it. Editing
the affected input clears its previous field marker; the next attempt validates
again. Text plus the asterisk convention makes required/error status understandable
without color. Defaults, parser behavior, and Person validation remain unchanged.

`PersonCard(true)` enables required labels, inline messages, direct name tooltips,
left-aligned labels, and compact spacing only for the wizard. The existing default
constructor preserves tab labels, geometry, and optional names. Birth date and
mortality help reuse the existing editor text and popup settings. There are no
label-side help graphics.

Primary is the only initial form. `+ Add Spouse` explicitly adds a draft Person and
displays the same editor/required indicators. Both people must validate to Create.
Remove Spouse invokes the domain API, removes the form, and discards spouse entries.
Adding again starts blank. No spouse is manufactured in a single-person result.
No dependent financial records exist in Phase 5A; future pages must respect the
existing domain removal guard when such records exist.

## Exact files changed

Production:

- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/components/PersonCard.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanDraft.java` (new)
- `src/main/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanWizardStep.java` (new)
- `src/main/java/com/daviddunn/retirementplanner/ui/wizard/HouseholdWizardStep.java` (new)
- `src/main/java/com/daviddunn/retirementplanner/ui/wizard/NewRetirementPlanWizard.java` (new)
- `src/main/resources/css/new-plan-wizard.css` (new, wizard-scoped)

Tests:

- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanWizardTestSupport.java` (new)
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewRetirementPlanWizardTest.java` (new)
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanWizardLifecycleTest.java` (new)
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanWizardPreviewTest.java` (new)
- `src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsMainWindowTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/HouseholdViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/RothConversionMainWindowTest.java`

Documentation:

- `PHASE-5A-NEW-PLAN-WIZARD.md` (this file)

## Tests and visual acceptance

The new focused behavioral tests cover required labels, quiet initial state, blank
and malformed birth dates, missing mortality, optional names, typed-date creation,
single-person/mortality persistence, explicit spouse addition/validation/removal,
atomic household application, factory defaults, full JSON round trips for both
shapes, input help/no help icons, multi-page Back/Next/progress/final revalidation,
and draft completion checks.

Lifecycle tests exercise the actual File → New menu, clean/dirty cancellation with
unapplied tab edits, active-plan/file/revision/cache preservation, invalid Create,
window-close cancellation, single/couple activation, existing Household view values,
normal save/reload, projection after adding funding, departure Cancel/Discard,
and legacy Open without wizard/required-field migration.

The three affected legacy New tests now enter a valid wizard household before
answering the existing departure prompt. Their original replacement, dirty-state,
and baseline-reset assertions are retained.

Commands use IntelliJ bundled Maven and the required project-local repository:

```powershell
$maven = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd'
$repo = '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository'
& $maven $repo '-Dtest=NewRetirementPlanWizardTest,NewPlanWizardLifecycleTest,NewPlanWizardPreviewTest' '-Dnew.plan.wizard.preview=true' test
& $maven $repo '-Dtest=NewRetirementPlanWizardTest,NewPlanWizardLifecycleTest,NewPlanWizardPreviewTest,HouseholdViewTest,AssumptionsMainWindowTest,RothConversionMainWindowTest,AssumptionsViewTest,SinglePersonHouseholdUiTest,InputTooltipTest,PlanningHorizonLayoutTest,SurvivorBenefitClaimingControlsTest' '-Dnew.plan.wizard.preview=true' test
& $maven $repo '-Dtest=*,!*Benchmark*' test
& $maven $repo test
git diff --check
```

Verification on October 8, 2026:

| Run | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Focused Phase 5A, including enabled preview | 20 | 0 | 0 | 0 |
| Focused plus directly affected UI regression and preview | 125 | 0 | 0 | 0 |
| Complete non-benchmark (`*,!*Benchmark*`) | 1,740 | 0 | 0 | 10 |
| Complete suite, including benchmarks | 1,761 | 0 | 0 | 14 |

The affected UI portion alone is 105 tests. Default suite runs skip the opt-in
wizard preview; it was explicitly enabled and passed in the focused runs. All
suites completed with Maven `BUILD SUCCESS`. The full suite took 5 minutes 25 seconds.

The existing couple golden tests (`SinglePersonStage3CoupleGoldenTest` and
`SinglePersonStage4ACoupleGoldenTest`) passed with their original values. Existing
deterministic and single-person projection checks, seeded Monte Carlo path fixtures
and repeatability (`MonteCarloFoundationTest`), and PDF/report regressions passed
unchanged. No financial expectations were changed to satisfy tests.

`git diff --check` passed. Screenshot-based visual acceptance passed for all six
states and the actual input-hover popup. No independent end-user session was run.

Preview fixtures use synthetic Alex/Sam Example identities. Screenshots and geometry
are generated under `target/phase5a-wizard-preview/`: blank Primary, populated
Primary, validation error, spouse added, populated couple, narrow couple, and a
separate actual input-hover popup. Screenshot inspection verified aligned labels
and inputs, explicit required status, inline errors/focus, readable help, no help
icons, obvious optional Add Spouse, and fixed Create/Cancel placement. The normal
680 × 650 dialog fits both forms without scrolling; the 490 × 570 outer window
uses vertical scrolling with a wrapping header and visible progress/buttons.
This is automated interaction plus manual screenshot inspection, not a claim of
an independent end-user usability session.

## Compatibility and deferred work

No domain, financial, mortality, Monte Carlo, analyzer-ranking, persistence, or
build/dependency source changed. Existing JSON, legacy incomplete Persons,
single-person membership, couple membership, and internal new-plan factory calls
remain compatible. Existing plan opening bypasses the wizard. Editing tabs remain
the full editing interface.

Deferred: Accounts, Income, Expenses, Assumptions, and Review wizard pages; any
additional required inputs from those pages; handling draft financial dependencies
when changing membership; and broader wizard usability testing. No empty future
page or placeholder spouse is exposed in this phase. Nothing is staged, committed,
or pushed.
