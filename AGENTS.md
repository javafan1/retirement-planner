# Project overview

Retirement Planner is a Java 25 Maven application for modeling multi-year retirement outcomes. Its primary desktop interface is JavaFX; Jackson provides JSON persistence; PDFBox supports PDF export.

- Maven coordinates: `com.daviddunn.retirementplanner:retirement-planner:1.0-SNAPSHOT`.
- Java: Maven compiler release 25; IntelliJ is configured for `temurin-25` / `JDK_25`.
- Main JavaFX class: `com.daviddunn.retirementplanner.ui.MainApplication`.
- Main dependencies: JavaFX Controls 25, Jackson 2.20.0 (including JSR-310), JUnit Jupiter 5.12.2, Ikonli 12.4.0, and PDFBox 3.0.8.
- The project uses Git and UTF-8 source/resource encoding.

# Architecture

The application is a layered, domain-oriented Java application.

- `ui`: JavaFX window, views, dialogs, charts, controls, and `ApplicationController`.
- `domain`: business model and financial logic. Important subpackages include `model`, `financial`, `income`, `projection`, `withdrawal`, `tax`, `rmd`, `medicare`, `roth`, `estate`, `baseline`, `rules`, and `noninvestable`.
- `persistence`: JSON plan/settings repositories and government-rule loading.
- `app`: console-oriented application path plus CSV/PDF exporters.
- `data`: demo-plan factories.
- `application.settings`: application-settings abstraction.
- `util`: money and presentation utilities.

Keep financial and projection rules in the domain layer. Keep persistence code in repositories. Keep UI classes focused on collecting input, invoking controller behavior, and rendering results.

# UI/domain interaction

`MainWindow` composes JavaFX views and coordinates through `ApplicationController`. The controller owns the current `RetirementPlan`, repositories, projection engine, summaries, baseline services, current file, and projection caches.

The usual update path is:

1. A view updates the current plan or related domain object.
2. The controller is marked modified.
3. Cached projection data is invalidated.
4. The projection and dependent views are refreshed.

Existing views can directly update domain objects before raising their plan-changed callback. Preserve that pattern unless a requested architectural change explicitly replaces it. Do not put financial calculations in JavaFX event handlers or views.

# Projection engine

`ProjectionEngine` is the core yearly simulation orchestrator.

It creates a separate `ProjectedPortfolio` from the actual account portfolio, then iterates through each projection year. It projects government rules, investment growth, income, expenses, RMDs, withdrawals, taxes, tax-funding withdrawals, Medicare premiums, Roth conversions, and estate values. It produces a `ProjectionYear` for each modeled calendar year.

The ending projected portfolio becomes the next year's opening portfolio. Its modeled December 31 balances are captured as an `RmdBalanceSnapshot` for the following year's RMD calculation.

Never mutate real `Account` balances while running a projection. Use `ProjectedPortfolio`, projected account balances, and snapshots to model changing financial state.

# Coding conventions

- Use the `com.daviddunn.retirementplanner...` package hierarchy.
- Prefer small, focused domain services with clear responsibilities.
- Use `BigDecimal` for financial calculations.
- Never use `double` or `float` for monetary calculations.
- Validate required inputs with `Objects.requireNonNull` and reject invalid financial values explicitly.
- Preserve the existing explicit, vertically formatted Java style.
- Prefer immutable/value-style domain objects where practical; do not expose mutable internal collections.
- Use constructor-based Jackson deserialization (`@JsonCreator`, `@JsonProperty`) where persisted types need it.
- Reuse existing domain services and value objects instead of adding special-case financial rules directly to UI classes or `ProjectionEngine`.

# Persistence rules

- `JsonRetirementPlanRepository` serializes and deserializes the complete `RetirementPlan` as formatted JSON, with Java time support.
- `JsonApplicationSettingsRepository` stores settings, including the last opened plan, under the user's home directory in `.retirement-planner/settings.json`.
- `GovernmentRulesRepository` loads versioned government rules from classpath JSON resources, currently `rules/government-rules-2026.json`.
- Do not hard-code tax, RMD, or Medicare rule values in projection logic.
- Preserve backward compatibility for persisted plans where reasonably possible, especially when adding optional fields.

# Financial-calculation rules

- Use `BigDecimal` for financial calculations; never use `double` or `float` for monetary calculations.
- Never change financial calculation behavior solely to make a test pass.
- If an existing test conflicts with the intended financial behavior, stop and explain the conflict rather than changing the financial behavior just to satisfy the test.
- Do not change financial behavior merely to satisfy UI requirements.
- Add or update domain tests before changing financial calculation behavior.

Projection-specific rules:

- The first projection year can be partial: investment growth and active recurring expenses are prorated from the projection start date. Later years use full-year calculations.
- Government rules are projected per calendar year using the plan's economic assumptions.
- Income and expenses must respect death-scenario behavior, including survivor income and recurring-expense adjustments.
- Non-investable assets are projected separately. Combine them with investable assets only when calculating net worth or comparisons.
- RMDs use prior December 31 *projected* balances, never current live account balances.
- Apply RMD withdrawals from eligible projected accounts first. An RMD can satisfy all or part of the household cash-flow need.
- Use the configured withdrawal strategy only for the remaining portfolio withdrawal after RMD allocation.
- Calculate tax-funding withdrawals after calculating taxes; include them in total portfolio withdrawals.
- Projection-generated retained non-qualified assets form one household-level,
  non-persisted balance. Annual excess RMD is source-attribution flow data, not
  a separate balance-sheet pool.
- No modeled household cash may disappear: guaranteed income and
  projection-period RMD cash fund ordinary expenses, Medicare, and taxes;
  genuinely unspent post-tax cash is deposited once as retained
  non-qualified assets.
- Retained non-qualified assets receive the configured investment return,
  enter investable assets/net worth/after-tax estate without a tax-deferred
  haircut, and fund later spending and taxes before persisted accounts. They
  are excluded from RMD calculations and Roth conversions.
- Use the filing status applicable to each projection year. The configured status applies through the death year; the surviving filer becomes `SINGLE` in later years.
- Medicare premiums depend on the federal tax result, filing status, projected rules, and number of covered individuals.
- Medicare coverage in a projection year requires both modeled alive status
  and the existing Medicare age eligibility. A configured death is effective
  January 1 of the death year, so the deceased person is excluded for that
  entire year and all later years; Medicare remains annual rather than partial-year.

# Roth conversions

Roth conversion behavior is implemented in `domain.roth` and invoked within the yearly projection flow.

- `RothConversionRequest` records enablement, start year, annual amount, frequency, stop rule, and strategy.
- `ScheduledRothConversionPolicy` controls whether the conversion runs in a year. It supports one-time and annual schedules and can stop at the first household RMD.
- Fixed conversions use the requested amount.
- Bracket-fill strategies target the 12%, 22%, or 24% federal bracket through `RothConversionTargetBracketResolver`.
- `RothConversionBracketFillCalculator` must account for tax-funding withdrawals because they can increase taxable income. Do not replace this with a one-pass bracket-room calculation.
- Include the conversion in tax calculations before funding taxes.
- Roth strategies determine a household-level requested conversion amount. Execution is owner-specific: `PRIMARY` sources are considered first, then `SPOUSE`; within each owner, persisted portfolio/account-list order determines source and destination priority.
- All otherwise-eligible primary- and spouse-owned sources may participate. Each conversion transfers only to a Roth account owned by the same owner as its source; cross-owner conversion is not allowed.
- Source exhaustion continues through additional eligible accounts and then to the other owner. Actual conversion is limited by valid same-owner source/destination capacity; shortfall is `max(requested - actual, 0)`.
- `ProjectionYear` records actual household, primary, and spouse conversions. Taxes use the actual executed household conversion, never an unexecuted requested amount.
- RMD processing occurs before Roth conversion. Annual statutory RMD, RMD distributed before projection, and RMD distributed during projection remain distinct. RMD dollars are not converted; a fully pre-satisfied opening RMD does not prevent conversion of remaining eligible assets.
- A conversion must not mutate persisted account balances.
- The projection year's `HouseholdSocialSecurityResult` is the authoritative
  gross Social Security input for guaranteed income, Roth bracket-fill tax
  calculations, and final annual tax calculations. When that result is
  available, bracket fill must not independently use a legacy Social Security
  calculation.
- Modeled Medicare premiums are annual household cash-flow expenses. The same
  authoritative annual `MedicarePremiumCalculation` total feeds reporting,
  lifetime Medicare metrics, and the established portfolio/RMD cash-funding
  mechanics; it must not be deducted separately from Social Security or ending
  assets.

# Baselines

Baselines are stored on the plan as `ProjectionBaseline` instances. `ProjectionBaselineFactory` creates a `RetirementPlanSnapshot`, and `BaselineProjectionService` rebuilds a plan from that snapshot and runs the same `ProjectionEngine` used for the current plan.

`ProjectionComparisonService` compares a requested year across baseline and current projections, including investable/non-investable assets, net worth, after-tax estate value, effective tax rate, and peak investable assets.

Important caveat: the current `RetirementPlanSnapshot` stores references to existing domain objects rather than making a deep copy. Do not assume it provides immutable historical isolation. Treat any work that changes baseline semantics as an explicit architectural/financial behavior change and test it carefully.

# Testing requirements

- Use JUnit 5 tests under `src/test/java`, mirroring the production package structure.
- Add or update focused domain tests for every financial or projection behavior change.
- Cover boundary ages and years, partial first years, RMD eligibility and prior-year snapshots, withdrawal ordering, tax-bracket boundaries, tax-funding feedback, Roth schedules/stop rules/bracket fill, persistence round trips, and baseline comparisons when applicable.
- Use the existing builders and `TestDataFactory` helpers where appropriate.
- Run relevant targeted tests first, then the full Maven test suite: `mvn test`.
- Do not consider a task complete if tests fail.

# Autonomy and command execution

Codex is authorized to work autonomously within this repository for normal software-development tasks that are within the scope of the user's requested task.

## Routine commands and actions

Codex should execute routine read-only, inspection, build, and test commands without asking the user for confirmation whenever the execution environment permits it.

This includes, but is not limited to:

- `Get-Content`, `Get-ChildItem`, `Select-String`, `Test-Path`, and `Resolve-Path`
- `rg` and `find`
- `git status`, `git diff`, `git log`, `git show`, `git branch`, `git ls-files`, `git check-ignore`, and `git grep`
- Java, JDK, and Maven version inspection
- Maven compile commands, targeted JUnit tests, and the complete Maven test suite
- IntelliJ bundled Maven commands
- Other non-destructive commands used to inspect the repository, compile the application, or verify behavior

Do not ask the user for permission merely to inspect source files, tests, resources, configuration files, Git status/diffs, or build output.

For requests to inspect, diagnose, review, or plan:

- Inspect all relevant project files and run read-only commands as needed.
- Do not modify files unless implementation or fixing was explicitly requested.

For requests to implement, change, or fix:

- Make in-scope local project changes without requesting approval for each file.
- Compile and run relevant non-destructive tests automatically.
- Fix ordinary in-scope compilation errors caused by the requested change.
- Inspect git diff and git status as needed.

## Implementation autonomy

Once the user has authorized a specific implementation task, Codex may autonomously:

- Inspect all files relevant to that task.
- Modify files necessary to implement that task.
- Add or modify focused tests.
- Run targeted tests and the full Maven test suite.
- Fix compilation errors directly caused by the requested implementation.
- Fix test failures directly caused by the requested implementation when the fix is clearly within the approved behavior.
- Make small supporting changes necessary to complete the requested task.
- Repeat the inspect/edit/compile/test cycle without requesting approval at each step.

Do not interrupt the user for routine intermediate approvals when the work remains within the scope of the task they already approved.

## IntelliJ bundled Maven

The project may use IntelliJ IDEA's bundled Maven when `mvn` is not available on PATH.

The known Maven executable is:

```text
C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd
```

Codex is authorized to use this executable for Maven inspection, compilation, targeted tests, and full test-suite execution without requesting additional user approval whenever the execution environment permits it.

## Actions that still require explicit user authorization

Stop and request approval before:

- materially changing financial calculation semantics
- performing a significant architectural refactor
- changing dependencies, Java version, or build configuration
- deleting significant files
- committing, resetting, reverting, rebasing, or otherwise changing Git history
- accessing or modifying files outside the project workspace
- expanding the task beyond the requested scope

Do not perform the following unless the user explicitly authorizes them:

- `git commit`
- `git push`
- `git reset --hard`
- `git clean`
- Force Git operations or rewriting Git history
- Deleting project files unless deletion is explicitly part of the requested task
- Modifying files outside this repository
- Installing or uninstalling system software
- Changing operating-system configuration
- Transmitting or exposing personal financial information
- Making broad architectural changes outside the requested task
- Intentionally changing financial-model behavior outside the requested task

## Financial-model safeguard

Codex may autonomously inspect financial calculations, identify defects, add tests, and implement financial behavior that the user has explicitly approved.

If fixing an approved task reveals that an additional financial rule must be changed outside the approved scope, stop before making that additional financial-model change and report:

1. The issue discovered.
2. Why the additional change appears necessary.
3. The financial behavior that would change.
4. The recommended implementation.

Ordinary compilation fixes, plumbing changes, parameter propagation, and test updates that do not independently change financial semantics do not require additional approval.

Never change financial behavior solely to make a test pass.

## Completion behavior

For an authorized implementation task, Codex should normally continue until:

1. The requested implementation is complete.
2. Relevant focused tests pass.
3. The full Maven test suite passes, when practical.
4. The results and files changed have been reported.

Do not commit changes unless the user separately requests a commit.

# Development workflow

- Make one logical change at a time.
- Do not refactor unrelated code while implementing a requested feature.
- Preserve existing behavior unless the requested change explicitly requires otherwise.
- Before making significant architectural changes, explain the proposed approach.
- Inspect the relevant domain code and tests before changing calculation behavior.
- After implementation, run the relevant tests and then the full Maven test suite.
- Report exactly which files were changed and which tests were run.
- Do not consider a task complete if tests fail.
- If Maven is unavailable in the environment, report that clearly; do not claim Maven tests were run.

# Social Security projection architecture

The advanced monthly `SocialSecurityStrategyCalculator` is the authoritative
benefit engine for both the read-only Social Security Strategy Analyzer and
normal two-person, modern-cohort retirement projections. `ProjectionEngine`
uses `SocialSecurityProjectionIncomeProvider` to translate configured plan
elections once per projection run and index annual results for cash flow,
taxes, and reporting. Projection does not run claiming optimization or
mortality weighting. Its year-only death scenario maps death to January 1 of
the configured year, and its persisted survivor claiming age maps to the
survivor's exact birthday. Unsupported legacy plan shapes remain on an
explicit compatibility fallback pending broader single-person/legacy-cohort
support.

The headless Integrated Social Security Strategy Evaluator is distinct from
the Social Security-only analyzer. The analyzer uses mortality-weighted Social
Security values and does not invoke `ProjectionEngine`. Integrated evaluation
deep-copies the complete `RetirementPlan` and supplies one immutable,
projection-run-only strategy override containing exact independent retirement
and survivor dates. `ProjectionEngine.project(plan)` continues to use persisted
retirement elections and the persisted survivor policy; only the explicit
context overload uses the override. Integrated evaluation runs one
deterministic full-plan projection and does not search or rank strategies.

`IntegratedSocialSecurityStrategyComparisonService` consumes a small ordered
set of complete analyzer strategies. It evaluates the persisted current plan
once as a baseline, removes only exact duplicate strategy inputs, and then
performs independent sequential integrated evaluations in caller order.
Candidate-minus-baseline differences are reported without selecting an
integrated winner or objective. Candidate failures are retained structurally
without discarding successful evaluations. The service does not run claiming
searches, mortality-weighted full-plan projections, or JavaFX behavior.

The read-only JavaFX Social Security Strategy Analyzer presents two modes.
`Social Security Only` retains the mortality-weighted claiming analysis and
ranks complete strategies by expected Social Security present value.
`Integrated Retirement Plan` takes a small analyzer-ordered top-N set and runs
the headless comparison service on one background task. It displays the
persisted current-plan baseline and deterministic future-dollar full-plan
outcomes without choosing an integrated objective, applying a strategy, or
persisting analysis results. Analyzer mortality adjustments and discounting
affect SS-only strategy generation and expected-value columns, not the
deterministic full-plan projection metrics.

The headless `IntegratedRetirementClaimingGridCalculator` is separate from
both the mortality-weighted Social Security claiming grid and the top-N
integrated comparison. It evaluates every requested primary/spouse whole-year
retirement age pair (standard range 62 through 70, producing 81 cells) through
the deterministic full `RetirementPlan`. It calculates the persisted current
strategy baseline once and holds a single explicit survivor policy fixed across
all cells: the persisted shared survivor claiming age is converted to each
person's exact birthday at that age. The grid preserves row-major age order,
retains complete projections and candidate-minus-baseline metrics, continues
after structured cell failures, and does not select an integrated objective or
run mortality-weighted full-plan analysis.

The headless complete deterministic integrated Social Security search expands
both retirement and survivor elections without using Social Security-only rank
as a filter. Its standard universe is whole-year retirement ages 62 through 70
for both people crossed with both authoritative survivor candidate lists. Every
strategy is evaluated through an isolated deterministic full-plan projection.
All strategies retain compact integrated metrics and candidate-minus-baseline
differences; only the configured top detail count (default 20) retains complete
projections. `AFTER_TAX_ESTATE` supplies a named deterministic metric ranking
with exact ties and generation-order tie stability. This ranking is not a
recommendation and does not introduce mortality-weighted full-plan analysis.

The JavaFX `Integrated Retirement Plan` mode exposes that backend through two
read-only sub-tabs. `Quick Comparison` preserves the small analyzer-ordered
top-N comparison. `Exhaustive Search` evaluates the full tested complete
strategy universe independently of Social Security-only rank and presents a
deterministic After-Tax Estate Ranking, current-plan position/gap, and grouped
financially identical display rows. Grouping is presentation-only; the search
still evaluates and retains a compact result for every strategy, with bounded
full-projection retention.

Long-running Social Security work reports real computation progress through
neutral `domain.analysis` callbacks rather than JavaFX properties in financial
calculators. Social Security-only analysis reports Stage 1 retirement-grid
cells and Stage 2 complete survivor strategies. Quick Comparison reports its
baseline and unique candidates. Exhaustive Search reports its baseline and
every generated strategy. JavaFX adapts those counts to a phase, percent,
completed/total text, progress bar, and activity indicator. Exhaustive
cancellation is cooperative at strategy boundaries, publishes no partial
ranking, and preserves the previous successful UI result. These analysis paths
do not mutate or persist the active plan.
