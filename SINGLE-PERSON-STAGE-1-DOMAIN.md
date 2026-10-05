# Single-person Stage 1: domain and persistence

Starting HEAD: `7057825 Add app-wide input tooltips`. Initial status and index empty.
The existing `SINGLE-PERSON-PLAN-SUPPORT.md` audit is retained unchanged.

## Boundary decided before production edits

Household owns explicit primary/spouse roles, not list positions. Person and Account
have no persisted ID field; do not invent IDs or promise preservation of a nonexistent
identifier. Preserve names, birth dates, mortality categories, ownership and data.

Dependency chain: Household representation -> RetirementPlan / baseline snapshot
ownership validation -> JSON repository boundary. Household membership also feeds
EffectiveHouseholdDeathView; update its callers mechanically to supply membership.
ProjectionEngine and SocialSecurityProjectionIncomeProvider remain unsupported for
single-person financial execution and reject at entry. PersonMortalityCategories
and MonteCarloMortalityRequest retain explicit two-person boundaries. Legacy
getSpouse consumers use a checked compatibility accessor, not a nullable return.
No UI, generation, optimizer, reporting, tax, RMD or Roth formulas are adapted.

Design: primary required; spouse nullable only in the existing serialized property,
exposed to new domain callers as Optional plus hasSpouse. Legacy required-spouse
access fails intentionally on absence. The death view requires a Household and
exposes ABSENT/ALIVE/DECEASED; absent death dates remain survival only for present
members. No implicit two-person death-view factory remains. Single-person ownership
validation rejects SPOUSE references rather than reassigning them; Joint ownership
policy remains deferred, not silently reinterpreted. Persisted couple-only death
scenarios in single-person plans are rejected until Stage 2 defines those inputs.

This stage does not change default new-plan UI/factory behavior, spouse-removal
workflow, filing status, mortality distributions or export behavior.

## Implemented household and death-state contracts

- `new Household(primary)` constructs a single-person household. The existing
  two-argument constructor accepts an absent spouse and still requires primary.
  The same Person object cannot fill both roles. More than one spouse is structurally
  impossible; `members()` returns an immutable ordered list.
- `spouse()` returns Optional, `hasSpouse()` is explicit, and `getPrimaryPerson()`
  retains its existing identity. No dates, categories, people or deaths are fabricated.
- Existing `getSpouse()` is deliberately a **required-spouse compatibility accessor**.
  It never returns null; single-person calls throw UnsupportedOperationException
  with an explicit unsupported message. `requireSpouse(component)` identifies
  specific guarded services. Unadapted UI/reporting consumers therefore cannot
  obtain a null spouse and accidentally interpret it as alive.
- JSON alone uses a private annotated nullable getter. The serialized property is
  still `spouse`; Optional, membership helpers and validation are not serialized.
- `EffectiveHouseholdDeathView.resolve` now requires Household. All production
  callers and four existing test call sites supply it; there is no old implicit
  two-person overload. The view freezes membership as a boolean, not a mutable Person
  reference. `state(owner, year)` distinguishes ABSENT, ALIVE and DECEASED.
- For an absent spouse, `isAlive` is false and `deathDate(SPOUSE)` rejects the query
  rather than returning an empty Optional that might be interpreted as survival.
  A supplied spouse death year with no spouse is invalid. Present members with no
  death year retain existing survival semantics. Death remains effective January 1.
- `isHouseholdDeceased` works over present members. Legacy `areBothDeceased` rejects
  single-person use, preserving its literal two-person meaning. JOINT alive status
  means some household member is alive; JOINT cannot be queried as a person's state.
  This is a membership foundation, not authorization to project a single plan yet.

## Persistence and reference validation

No schema version or migration is added. `spouse: null` and an omitted spouse property
both load as absence. Existing two-person JSON retains primaryPerson/spouse/expenses
and all nested data. A missing primary fails. Person defaults remain valid for
unfinished editing as before; a present incomplete spouse is not converted to absence,
and existing mortality validation still rejects its unresolved category.

Household checks primary-attached accounts/income for nonexistent SPOUSE ownership.
RetirementPlan and RetirementPlanSnapshot also validate the portfolio and reject
couple-specific persisted death scenarios on single households. The repository
rechecks before writing and after loading, including baseline snapshots, because
the existing account/income collections are mutable. A rejected save does not
overwrite the destination. Records are never reassigned or deleted.

This is boundary validation, not a new collection ownership framework: a caller can
still mutate the existing lists into an invalid intermediate state, but explicit
validation and repository admission reject it. Existing two-person validation is
not broadened. Joint ownership remains the existing account-type concept; Stage 1
does not claim to define or execute single-person Joint-account financial semantics.

Identity preservation means the same Java objects within a constructed household,
and equivalent person/ownership/account data across JSON. Neither Person nor Account
has a persisted ID, and the repository does not preserve Java reference aliasing
between duplicate serialized person-account and portfolio-account lists. This is
existing behavior, not a new identity scheme. Baseline reference sharing is also
unchanged; composition editing must address it in a later stage.

## Guardrails and deliberately deferred components

ProjectionEngine rejects a single household before constructing projected balances.
SocialSecurityProjectionIncomeProvider rejects it before benefit computation (also
on its advanced-path capability boundary). PersonMortalityCategories and
MonteCarloMortalityRequest explicitly reject it. Other required-spouse consumers
receive the checked Household compatibility exception. Pure calculation/value
types that already require two supplied elections or two mortality distributions
remain unchanged; they do not accept Household absence or fabricate inputs.

RMD/Roth/tax/pension formulas, claiming calculations, stochastic streams/generators,
weighted strategies, paired reduction, UI, PDFs and CSV are unchanged. There are
no new controls and no tooltip changes. A single JSON plan is now representable,
not yet runnable or generally viewable through the desktop UI. No unsupported UI
workflow is advertised as enabled; Stage 2 must replace required-spouse access
deliberately as each component becomes capable.

## Tests and regression evidence

Added `SinglePersonHouseholdTest` (9 tests) and `SinglePersonPlanPersistenceTest`
(7 tests). Coverage includes construction, zero-person rejection, role identity,
immutable membership, three-state semantics, missing-death ambiguity prevention,
primary death without a phantom survivor, invalid ownership, mutable validation,
projection/SS/mortality guardrails, invalid-present spouse category, single/couple
JSON, missing properties, stable JSON trees, invalid load, safe save rejection and
single baseline persistence/invalid baseline validation.

Two existing test files only supply Household to the new death-view signature;
their financial and death-state assertions are unchanged. The existing JSON tests
also passed. No assertion was removed, weakened or disabled.

First focused run: 37 tests, 0 failures/errors/skips, BUILD SUCCESS.
Expanded focused run: **274 tests, 0 failures/errors, 5 skipped**, BUILD SUCCESS.
It includes all MonteCarlo* tests except benchmarks, ownership, persistence,
survivor financial mechanics and household lifetime tests. Existing seed-417,
inflation, mortality and paired-comparison assertions passed; no fingerprint was
updated. Logs: `target/single-stage1-focused.log` and
`target/single-stage1-regressions.log`.

Commands use IntelliJ bundled Maven and
`-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.
Expanded selection:
`-Dtest=SinglePerson*Test,JsonRetirementPlanRepositoryTest,HouseholdLifetimeScenarioTest,SurvivorFinancialMechanicsTest,*Ownership*Test,PersonMortalityOwnershipTest,MonteCarlo*Test,!*BenchmarkTest`.

## Stage 2 implications

Do not remove required-spouse guardrails globally. Adapt projection and dependent
financial components using membership-aware state and ordered present members;
retain explicit rejection for analyzers not yet implemented. Define single-person
persisted death/end inputs before relaxing the current couple-scenario rejection.
Tax filing status is still independent of membership and was not changed.
Resolve Joint-account policy and baseline isolation before spouse-removal UI.
The audit report remains intact and later stages remain deferred.

## Final verification and file boundary

Full non-benchmark suite (`-Dtest=*Test,!*BenchmarkTest`): **1,660 tests,
0 failures, 0 errors, 9 skipped**, BUILD SUCCESS in 1:44.
Log: `target/single-stage1-full.log`. This is the prior 1,644-test baseline plus
16 new tests. Existing financial, persistence, PDF, UI, mortality and seeded Monte
Carlo tests all retain their assertions. No benchmark or visual preview was needed
for this domain-only stage; no single-person UI support is claimed.

`git diff --check`: passed. Index empty. Nothing staged, committed or pushed.
Starting HEAD remains unchanged. Exact boundary: **14 files** — 9 production,
4 tests (2 new, 2 mechanically adapted), and this report. The prior audit report
is unchanged. Exact final `git status --short`:

```text
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityRequest.java
 M src/main/java/com/daviddunn/retirementplanner/domain/baseline/RetirementPlanSnapshot.java
 M src/main/java/com/daviddunn/retirementplanner/domain/model/Household.java
 M src/main/java/com/daviddunn/retirementplanner/domain/model/RetirementPlan.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/EffectiveHouseholdDeathView.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/SocialSecurityProjectionIncomeProvider.java
 M src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/PersonMortalityCategories.java
 M src/main/java/com/daviddunn/retirementplanner/persistence/JsonRetirementPlanRepository.java
 M src/test/java/com/daviddunn/retirementplanner/domain/projection/HouseholdLifetimeScenarioTest.java
 M src/test/java/com/daviddunn/retirementplanner/domain/projection/SurvivorFinancialMechanicsTest.java
?? SINGLE-PERSON-STAGE-1-DOMAIN.md
?? src/test/java/com/daviddunn/retirementplanner/domain/model/SinglePersonHouseholdTest.java
?? src/test/java/com/daviddunn/retirementplanner/persistence/SinglePersonPlanPersistenceTest.java
```
