# Single-person Stage 2 — core projection audit and financial-semantics decision

## Starting state

Starting HEAD: `4e8741e Add single-person household domain support`.
Initial `git status --short`: empty. Index empty.
Read `SINGLE-PERSON-PLAN-SUPPORT.md` and `SINGLE-PERSON-STAGE-1-DOMAIN.md`.
Both remain unchanged.

**Stage 2 implementation is complete; final verification is recorded below.**
The original audit found an IRMAA scope conflict. The user subsequently explicitly
authorized preserving same-year AGI behavior. The audit and decision rationale
below are retained as implementation context, not an outstanding approval request.

## Financial-semantics conflict requiring resolution

The request requires IRMAA lookback/prior-year behavior to work and also requires
unchanged two-person financial results and no unrelated model redesign.

Actual code:

1. `ProjectionEngine.calculateProjectionYear` obtains the current calculation's
   `FederalTaxCalculation` from the tax-funding result.
2. It passes that calculation, the year's configured/applicable filing status,
   projected rules and covered participant count to `MedicarePremiumCalculator`.
3. `MedicarePremiumCalculator.calculate` assigns
   `modifiedAdjustedGrossIncome = federalTaxCalculation.getAdjustedGrossIncome()`
   and selects the IRMAA bracket with that value. It receives no income year or
   historical MAGI input.
4. Projection reruns the same annual calculation with the resulting Medicare
   premium as authoritative cash-flow expense. This is annual cash-funding
   feedback, not an income lookback.

Repository-wide searches for lookback, look-back, historical/prior MAGI and
two-year income found no historical-income implementation. The existing Medicare
tests verify supplied AGI, participant multipliers and same-year projection cash
funding. They do not establish a lookback model.

Implementing a historical-income lookback would change the financial model for
couples as well as singles. It would need explicit pre-projection income data or
an approved missing-history policy, year-indexed income history, filing-status
rules for lookback, and updated premium/cash-flow integration. It cannot honestly
be described as a membership-only adaptation or certified by unchanged tests.

The repository AGENTS.md financial safeguard says to stop before an additional
financial rule change outside the approved scope, describe the issue/behavior,
and recommend an implementation. It also prohibits changing financial behavior
merely to satisfy a test. This is the reason for pausing before production edits.

**Recommended decision:** proceed with Stage 2 using the existing same-year
AGI-based IRMAA model for both household sizes; explicitly defer historical
lookback to a separate approved financial milestone. In Stage 2, test configured
filing status, current modeled AGI, rule-selected tier and one-person premiums.
Do not add or claim a lookback test that the engine cannot satisfy.

## Stage 1 guarantees to preserve

Household requires primary and exposes optional spouse/present members. Required
spouse access throws for absent spouse. EffectiveHouseholdDeathView distinguishes
ABSENT/ALIVE/DECEASED and requires membership. Single-person JSON preserves absence;
SPOUSE-owned references fail validation. Persisted couple-specific death scenarios
are not yet admitted for singles. No spouse-removal UI is enabled.

## Read-only Stage 2 trace and classification

| Area | Finding | Classification / proposed boundary |
| --- | --- | --- |
| Projection admission | `executeInternal` currently requires spouse | Stage 2: replace only after single financial path and validation are ready |
| Horizon | Uses configured start/length and explicit evaluation-context ending | Preserve configured deterministic horizon; single lifetime/death overrides remain later-stage guarded |
| Failure | FundingFailure already uses Optional ages; engine currently dereferences spouse to populate it | Stage 2: absent spouse age is Optional.empty, unchanged failure taxonomy |
| Owner eligibility | Engine streams PRIMARY/SPOUSE even for ordinary projection | Stage 2: derive present owners, retaining existing ordering/lifetime filters for couples |
| Income | Engine explicitly sums primary and spouse and tests opposite-owner survivor pension | Stage 2: present-member aggregation; spouse-dependent rule only for a real spouse |
| Pension | HouseholdPensionIncomeCalculator uses List.of(primary, spouse) and both-deceased logic | Stage 2: membership-aware aggregation; reject positive spouse-survivor elections without spouse |
| Expenses | Household-level model; recurring amounts prorated in opening year, inflation by category; cessation calls areBothDeceased | Stage 2: household-deceased semantics; preserve partial year, dates and one-time handling. No person-owned expense feature found |
| Portfolio growth / net worth | Based on accounts, retained assets and non-investable assets | Naturally size-independent; verify sums rather than invent spouse balances |
| Withdrawals | Type/ordering-based portfolio allocation; RMD allocator explicitly invokes both owner results | Ordinary priority naturally reusable; Stage 2 must make RMD application iterate actual owner results |
| RMD | HouseholdRmdCalculator unconditionally reads spouse; OpeningRmdCalculator creates paired remaining results | Stage 2: present-owner calculation and explicit absent spouse result; retain prior-Dec-31/opening distribution rules |
| Roth | Existing converter supports an eligible owner set and fixed primary-first ordering | Stage 2: pass present owners, preserve couple continuation and tax-funding feedback |
| Federal tax | Stored TaxAssumptions filing status feeds current-year calculation; no composition-derived Single in ordinary horizon | Preserve explicit status; no automatic conversion from no spouse to Single |
| Michigan tax | Receives retirement income, filing status and rules; no person/DOB parameter in MichiganTaxCalculator | Naturally reusable; verify no extra-person deduction, retain current state model |
| Medicare | Engine counts primary and spouse by age/alive status | Stage 2: count actual eligible members only; preserve existing age/date convention |
| IRMAA | Uses supplied current-year AGI and projected filing-status tiers | Decision above required; historical lookback is absent |
| Own Social Security | Provider rejects single household; authoritative strategy calculator has isolated prepare/ownBenefit primitives | Stage 2 can expose a bounded own-only annual schedule reusing monthly rounding/COLA/claim timing; do not fabricate paired requests |
| SS results | HouseholdSocialSecurityResult requires all spouse/spousal/survivor fields | Stage 2 needs explicit absent audit data with compatibility for existing couple results; zero spouse is not acceptable |
| Projection results | Spouse Roth conversion is required and participates in sum validation | Stage 2 needs explicit absence, preserving existing couple getters/data and downstream guards |
| Estate | Annual after-tax estate uses ending snapshots, heir rate and ending assets | Naturally size-independent; configured terminal year stays configured, no invented second death |
| SS analyzers / survivor overrides | Two-person elections and survivor strategy contracts | Stage 3, retain guards |
| Mortality / longevity and paired Monte Carlo | Two-person sampling and scenario contracts | Stage 4, retain guards; core support must not implicitly enable these services |
| UI / PDF / CSV | Required-spouse consumers and fixed spouse fields | Stage 5; no broad adaptations, do not silently export fabricated data |

## Planned implementation sequence after the decision

Capture a representative two-person multi-year regression fixture **before**
production changes, including tax, Medicare, RMD, Roth, estate and ending assets.
Then add single-person integration tests before adapting the shared financial
path. Refactor present-owner/member iteration, add absent result representations,
reuse own-benefit math and retain explicit unsupported analysis guards. Define
Joint-account admission explicitly without reassignment. Keep persisted death/
survivor semantics deferred rather than inventing a temporary policy.

Use current tables/calculators as test authorities for tax and premium components;
assert cash/asset reconciliation, RMD before conversion, no duplicate opening
distribution, actual conversions in taxes, and no spouse records. Run focused
tests incrementally and the full non-benchmark suite on completed implementation.

## Original audit verification (before implementation)

At the original audit stop, no integration fixture or production changes existed.
The supplied 1,660-test baseline was not presented as a fresh suite result.

Existing Medicare regression checks:
`MedicarePremiumCalculatorTest,MedicareCashFlowProjectionTest,MedicareDeathCoverageProjectionTest`
passed: **13 tests, 0 failures/errors/skips**, BUILD SUCCESS. Log:
`target/single-stage2-irmaa-audit.log`. Used IntelliJ bundled Maven and the
repository-local `.codex-m2/repository` required by AGENTS.md. These tests confirm
the existing behavior remains passing; they do not prove historical lookback.

At that audit stop, `git diff --check` passed and status was:

```text
?? SINGLE-PERSON-STAGE-2-CORE-PROJECTION.md
```

The full suite was not rerun for that documentation-only stop. Implementation
verification follows and supersedes this historical status.

## Stage 2 implementation and review boundary

The user resolved the audit conflict by authorizing the existing same-year AGI
IRMAA model. Implementation is limited to deterministic configured-horizon
projection, the result data it needs, and guards preventing accidental entry into
still-unsupported downstream features. Stage 1 household/death/persistence
invariants remain intact. No UI controls, tooltip wording, persistence schema,
government tables, stochastic generators, or comparison statistics changed.

### Core projection, accounts, withdrawals and expenses

`Household.peopleByOwner()` exposes an immutable, primary-first map of actual
members. The engine aggregates actual members and derives eligible owners from
that map. Funding failure carries an absent spouse age. Medicare counts actual
living, age-eligible members. No dummy person or spouse age is created.

Stage 1 reference validation still runs. Single-person projection rejects JOINT
portfolio accounts rather than reassigning ownership, and rejects non-primary
income ownership. Primary brokerage, checking, traditional IRA, 401(k) and Roth
accounts are exercised. Ordinary withdrawal ordering, retained household cash,
growth and tax-funding algorithms are unchanged; RMD allocation iterates only
actual owner results. Persisted account balances remain untouched.

Expenses remain household-level (no person-specific expense model was found).
Existing recurring/one-time dates, opening partial-year proration, general and
healthcare inflation are unchanged. Cessation uses explicit household-deceased
semantics instead of assuming two deaths.

Configured deterministic horizon behavior is unchanged. Single-person lifetime,
survivor and strategy overrides remain rejected. This stage does not introduce a
single-person configured death scenario or approximate future death semantics.

### Pension and own Social Security

Pension and taxable-income aggregation iterate actual members. Positive pension
survivor elections are rejected for a single household; no survivor is invented.
Existing single-life amount, start date, COLA and taxation remain authoritative.

`SocialSecurityStrategyCalculator.calculateOwnRetirement` exposes a bounded annual
schedule built from its existing monthly preparation/own-benefit calculation.
The projection provider supports zero or one primary Social Security record,
using its configured election and the existing COLA/monthly rounding. This does
not run claiming optimization or build a synthetic couple request. Own benefits
at ages 62, 67 and 70 are checked against the identical own-benefit component of
a genuine two-person authoritative calculation.

Single-person `HouseholdSocialSecurityResult` has explicit absent spouse, spousal
and survivor audit components, validated together; `hasSpouse()` and an Optional
accessor distinguish absence from zero. Couple construction and selection remain
unchanged. Projection years and summary metrics similarly expose absent spouse
Roth/SS data. Required-spouse legacy access fails clearly rather than inventing
an observation. These result changes are not persisted plan-schema changes.

### Taxes, Medicare and the approved IRMAA limitation

Federal filing status continues to come from configured/applicable tax
assumptions. The same single-person fixture with SINGLE versus
MARRIED_FILING_JOINTLY verifies differing deductions, federal tax and IRMAA tier,
while both have exactly one covered participant. No composition-based status
inference was introduced. Michigan receives the existing income/status/rules;
its formulas and deductions are unchanged. Actual executed conversion and the
authoritative own Social Security result feed tax calculations.

> The existing Retirement Planner IRMAA implementation uses same-year modeled AGI rather than the historical MAGI lookback generally used for Medicare IRMAA determination. Stage 2 intentionally preserves this existing behavior to keep single-person support financially behavior-neutral for existing plans. Historical IRMAA lookback support is deferred to a separate financial-rules milestone.

Implementation location: `ProjectionEngine.calculateProjectionYear` supplies its
current-year federal calculation to `MedicarePremiumCalculator.calculate`, which
uses `getAdjustedGrossIncome()` as modeled MAGI. Existing annual Medicare
cash-funding feedback remains unchanged. No historical storage, threshold change,
or year-sequencing change was introduced. This is not historically accurate
lookback modeling.

Evidence: `genuineSingleProjectionUsesOnlyPresentOwnerAndSameYearIrmaa` checks
same-year AGI and rule-selected Part B/D amounts over four years;
`medicareBeginsForOneEligiblePersonOnly` checks the eligibility transition;
`firstHouseholdRmdStopsPrimaryConversionAndFilingStatusRemainsExplicit` checks
filing-status independence. `representativeCoupleRemainsExactlyUnchanged` checks
exact two-person Medicare/MAGI outputs captured before production changes.
Existing Medicare calculator, death-coverage and cash-flow tests also pass.
Changing lookback was rejected because it independently changes the financial
model and existing couple results, outside this membership-support milestone.

### RMD and Roth conversion

Household RMD results carry actual owners only, in stable primary-first order.
Annual and opening calculations preserve prior-December-31 balance semantics.
Opening distributions remain taxable but are not withdrawn twice; remaining RMD
is allocated before conversion. FIRST_HOUSEHOLD_RMD works with the primary alone.

The Roth converter, bracket-fill feedback and target rules were not rewritten.
They receive the actual eligible owner set. Single-owner source exhaustion stops
at primary capacity; no spouse or cross-owner destination is created. Taxes use
executed conversion. A focused custom taxable-income-target test compares ordinary
and fully pre-satisfied larger opening RMDs: greater taxable RMD reduces conversion
room by the corresponding amount, both hit the same target, and the pre-satisfied
RMD produces no second distribution. The expected annual RMD comes from the
existing calculator, not duplicated law tables.

### Net worth, estate and funding failure

Existing investable-assets and after-tax-estate formulas are unchanged. Integration
assertions reconcile opening assets, growth, withdrawals and retained surplus;
authoritative non-investable home projection adds to net worth. Estate equals
ending investable assets less the existing heir-tax estimate. No nonexistent
second-death date is needed for configured-horizon deterministic estate values.
Funding shortfalls retain the existing failure stage/amount taxonomy, with no
spouse age or assumed spouse resources.

### Downstream guards and deferred work

Fixed and paired Monte Carlo entry points explicitly retain their two-person
restriction; existing longevity/mortality and specialized Social Security guards
remain. Projection CSV/PDF export rejects single-person input before writing,
pending intentional report adaptation. Other required-spouse UI/analyzer paths
are not silently enabled. Legacy standalone tax-income paths without an
authoritative SS projection result are not newly advertised as single-person
capable; the core engine supplies that result.

Stage 3 should adapt explicit death/survivor and specialized Social Security
contracts, then later stages can adapt mortality, Monte Carlo and presentation.
Consumers must recognize absent result components instead of assuming numeric
spouse values. A future UI must resolve JOINT ownership and survivor elections
explicitly before admitting a one-person projection. No spouse-removal workflow
was added.

## Integration and two-person regression evidence

New `SinglePersonCoreProjectionTest` has 12 executions, including the three
claiming-age cases. It exercises four projection years, partial opening year,
pension, own SS, recurring/health/one-time expenses, growth, taxable/cash/IRA/401(k)/
Roth assets, federal/Michigan tax, Medicare/IRMAA, opening and later RMDs, fixed and
target conversions, source exhaustion, non-investable property, estate, failure,
JSON round-trip and deferred-service guards. It checks actual financial
relationships and immutable live balances, not just successful construction.

The new golden resource was captured on the pre-change engine (log
`target/stage2-capture.log`) and then locked into an exact string comparison of
unrounded BigDecimal values across four years. Columns include income, expenses,
RMD, conversion, federal/Michigan tax, Medicare, modeled MAGI, withdrawals,
investable assets and estate. Existing two-person financial expected values were
not edited. No observed representative couple output changed.

`SinglePersonHouseholdTest` now tests rejection of deferred lifetime/strategy
contexts instead of blanket rejection of ordinary deterministic projection.
Its Stage 1 absence, persistence, reference and mortality guarantees remain.

## Verification, performance and manual scope

All Maven runs use IntelliJ bundled Maven and the workspace `.codex-m2/repository`.
Focused progression:

- Initial Medicare audit: 13 tests, no failures/errors/skips.
- Pre-change golden capture: 1 test passed.
- Early core groups: 15, then 25 tests passed.
- Financial/RMD/Roth/Medicare group: 203 tests, 0 failures/errors, 1 skipped;
  `target/stage2-financial.log`.
- Social Security/core/Stage 1 group: 391 tests, 0 failures/errors, 1 skipped;
  `target/stage2-ss.log`.
- First complete non-benchmark run: 1,672 tests, 1 failure, 0 errors, 9 skipped.
  The existing `Stage5DComparisonTest.callbackFailureCleansUpOutstandingWorkers`
  worker-liveness timing assertion failed. Its isolated class rerun passed all
  23 tests (`target/stage2-isolated.log`). No related production/test code was
  changed. Full-suite rerun is recorded below.

The new source-capacity test is included in the complete runs. No existing
financial assertions were weakened, disabled or removed.

Interactive single-person creation/results remain intentionally deferred; no
manual GUI completion is claimed. Core behavior was exercised through the
headless integration fixtures above. Existing UI tests remain in the full suite.
No new controls means no tooltip changes. No projection benchmark was performed;
there is no added per-year JSON copying or analyzer execution. Own SS is indexed
once per projection run and member iteration is bounded to one or two people.

## Deferred roadmap: IRMAA Historical MAGI Lookback

Treat this as a separate financial-model enhancement with its own review gate:

- Audit applicable historical MAGI and lookback-year semantics and filing-status
  interactions, including death/survivor transitions.
- Define unavailable pre-projection history, optional user-provided historical
  MAGI and first-projection-year policies explicitly.
- Audit threshold inflation/indexing separately from historical-income selection.
- Introduce a year-indexed historical income/status input and calculation boundary,
  then propagate it through projection cash funding and Roth/MAGI-producing events.
- Cover deterministic and Monte Carlo projections without changing random streams.
- Design required persistence/backward compatibility and UI/input support.
- Run explicit before/after financial regression analysis and obtain review of
  the changed model; do not hide that change within household-size support.

## Final verification and exact file boundary

18 production files, 2 test files, 1 golden resource and this report: 22 files.
The exact final status below identifies every path. The original Stage 1 and
architecture audit reports are unchanged. Nothing was staged, committed or pushed.

Full non-benchmark rerun: **1,672 tests, 0 failures, 0 errors, 9 skipped**,
BUILD SUCCESS, 1:56 elapsed (`target/stage2-full-rerun.log`). Command selection:
`-Dtest=*Test,!*BenchmarkTest`. This includes existing Monte Carlo, mortality,
seeded, survivor, financial, persistence, UI and export regressions.

`git diff --check`: passed. Git emits existing LF-to-CRLF normalization warnings;
there are no whitespace errors. Index is empty (`git diff --cached --name-only`
has no output). HEAD remains `4e8741e`. No stage/commit/push operations performed.

Exact final `git status --short`:

```text
 M src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionCsvExporter.java
 M src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloAnalyzer.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonAnalyzer.java
 M src/main/java/com/daviddunn/retirementplanner/domain/income/HouseholdPensionIncomeCalculator.java
 M src/main/java/com/daviddunn/retirementplanner/domain/income/HouseholdSocialSecurityResult.java
 M src/main/java/com/daviddunn/retirementplanner/domain/model/Household.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectedWithdrawalAllocator.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionYear.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/SocialSecurityProjectionIncomeProvider.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/summary/ProjectionMetrics.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/summary/ProjectionMetricsCalculator.java
 M src/main/java/com/daviddunn/retirementplanner/domain/rmd/HouseholdRmdCalculator.java
 M src/main/java/com/daviddunn/retirementplanner/domain/rmd/HouseholdRmdResult.java
 M src/main/java/com/daviddunn/retirementplanner/domain/rmd/OpeningRmdCalculator.java
 M src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityStrategyCalculator.java
 M src/main/java/com/daviddunn/retirementplanner/domain/tax/TaxIncomeCalculator.java
 M src/test/java/com/daviddunn/retirementplanner/domain/model/SinglePersonHouseholdTest.java
?? SINGLE-PERSON-STAGE-2-CORE-PROJECTION.md
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/SinglePersonCoreProjectionTest.java
?? src/test/resources/single-person-stage2-couple-golden.txt
```
