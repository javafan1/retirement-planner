# Phase 19A — Social Security Benefit Reduction Architecture Audit

## Executive summary

The natural plan-level home for this assumption is `EconomicAssumptions`, alongside Social Security COLA. The main projection obtains its authoritative annual Social Security result from `SocialSecurityProjectionIncomeProvider`; `ProjectionEngine` then passes that result through cash flow, taxes, Medicare-related income/tax inputs, Roth bracket-fill, reporting, and retained projection data. A reduction applied to payable benefits at that boundary can propagate through the normal financial model without adding downstream benefit calculations.

That one change would not cover every analyzer. Social Security-only deterministic and mortality-weighted analyzers call the Social Security strategy calculators directly. Individual mortality Social Security analysis also uses a direct monthly own-benefit API. These paths need the same explicit reduction policy carried into their immutable calculation inputs. Integrated deterministic and longevity-weighted analyzers, Quick Comparison, and Monte Carlo use full-plan projection paths and would inherit the assumption when their frozen plans contain it.

No production code, tests, plan schemas, or application settings were changed in this audit. Existing Phase 17B/18A working-tree work was left intact.

## Current domain model and persistence

`RetirementPlan` owns `PlanningAssumptions`; `PlanningAssumptions` owns an immutable `EconomicAssumptions`. The latter currently stores investment return, general inflation, healthcare inflation, and Social Security COLA as `BigDecimal` inputs. It has Jackson constructor compatibility for previous economic-assumption JSON forms. `PlanningAssumptions` has additional legacy-flat JSON handling.

Recommended additions to `EconomicAssumptions`:

| Proposed property | Type | Default | Validation |
|---|---|---:|---|
| Social Security benefit reduction percentage | `BigDecimal` | `0` | Inclusive 0 through 100; reject negative and above 100 |
| Benefit reduction effective year | `int` (or nullable during deserialization only) | `2032` | Four-digit calendar year; accept years outside a particular plan's projection horizon |

Keep percentage in percent units (`17` means 17%), consistent with the UI labels; convert to a fraction only in the calculation policy. Decimal percentages should retain `BigDecimal` precision. A pure immutable policy/value object is also reasonable if it prevents duplicated validation or conversion logic, but it must remain part of the serialized economic plan inputs.

Backward-compatible Jackson behavior should map absent legacy properties to 0% and 2032. Recommend treating an absent or null reduction as 0 and an absent or null effective year as 2032; reject malformed explicit non-null values. Zero reduction makes the year calculation-inert, but persisting the selected year supports later editing. No outer plan schema migration appears necessary: the fields can be added to the existing nested economic-assumptions JSON object. Existing files load through the current constructor-based Jackson path.

`EconomicAssumptions` is immutable but does not currently define value equality. New constructor overloads/getters and `toString` need deliberate compatibility review. Existing constructor call sites that rebuild economic assumptions must carry the two new values forward; otherwise an unrelated edit can reset them. Identified examples include `AssumptionsView`, `ResultsSummaryView.applyEconomicAssumptions`, and `MonteCarloStrategyComparisonRunService`; implementation should search all `new EconomicAssumptions(...)` sites, including wizard and tests. The Summary path currently reconstructs the object while preserving COLA, so it is a concrete reset risk if not updated.

`RetirementPlanSnapshot.fromRetirementPlan` freezes a baseline using JSON round-trip; baseline snapshots therefore carry newly serialized economic inputs automatically. `CompleteProjectionSnapshot.freeze(plan)` stores serialized configured inputs and an input digest, so a changed value will make a previous accepted projection stale without a new outer projection artifact version. Plan save/load/swap likewise use the serialized plan model. No mutation of historical snapshots should occur.

`AnalysisFingerprintService` has an explicit `socialSecurity()` fingerprint section that currently records COLA, DOBs, FRA amounts, claiming ages/dates, and valuation years. It must include both reduction inputs for every analyzer whose values or ranks use Social Security payments. Integrated/general plan analysis fingerprints also serialize relevant plan assumptions; still include the explicit Social Security inputs for Social Security-only kinds and verify weighted kinds, context fingerprints, Monte Carlo effective-input assessment, and old snapshot compatibility. Existing saved analysis records should remain loadable, but their old fingerprints must not be silently rewritten; an assumption change should make prior analysis stale under the established compatibility policy.

## UI integration

`AssumptionsView` has the existing Social Security COLA control in its economic assumptions area and already owns Apply parsing, validation, dirty-state notification, and required-field infrastructure. Place a compact two-field group directly beneath the explanatory text **Future Social Security Benefit Reduction**. Use the existing percentage parser, year-only field conventions, tooltip helper, and `RequiredFieldSupport`; do not use claim-age widgets or add a new Summary editing lifecycle.

The tooltip should use the supplied complete explanatory copy, including the statement that the assumption is a planning scenario and not enacted law. Recommended display: percentage text in percent units, and a four-digit calendar-year field initialized to 2032. The reduction value should be required/validated on Apply even at zero. Recommend requiring a valid year only while percentage is greater than zero, while retaining the displayed 2032 default; this conditional marker/validation policy should be approved before implementation. Alternatively, keep the year always valid because it has a default. In either policy, a blank/malformed active year must produce field-specific feedback and must not silently substitute another date. These UI requiredness details are approval decisions, not changes made in this audit.

Results Summary Key Assumptions is a separate editable surface and must remain in its current right column. Prefer a small conditional read-only disclosure in that column (and in detailed assumption summaries) only when reduction is greater than zero, rather than introducing a second pair of editable fields or widening the panel. A disclosure should read along the lines of “Social Security reduction: 17% beginning 2032.” At zero, omit it to avoid clutter. All displayed values should come from the same committed plan assumptions; no projection recalculation is caused by opening the screen.

## Projection calculation entry point and treatment

### Current calculation flow

`ProjectionEngine.project(...)` calls `SocialSecurityProjectionIncomeProvider.calculate(plan, firstYear, lastYear, context)`. For supported modern-cohort households, that provider creates a `SocialSecurityStrategyRequest` and invokes `SocialSecurityStrategyCalculator`, whose monthly results select own, spousal-excess, and survivor benefits and are aggregated by calendar year. For unsupported legacy household shapes it uses `HouseholdSocialSecurityIncomeCalculator`. The provider also has a single-person own-retirement path using `calculateOwnRetirement`.

The monthly strategy calculator applies claiming-age adjustments and Social Security COLA before producing payment amounts. The annual legacy calculator uses the same plan COLA and separately computes own and survivor benefits. `ProjectionYear` stores `HouseholdSocialSecurityResult`; projection, tax, metrics, and reports consume those stored selected amounts.

### Recommended exactly-once policy

Introduce one immutable reduction policy/value object with a single `isEffective(year)` / `reduce(amount, year)` contract. Pass it through projection and analyzer calculation inputs. Apply it to the final payable selected benefit for each calendar-year payment, after the own/spousal/survivor selection has been determined, not to PIA, FRA, initial claim amount, survivor worker basis, or COLA rate. For the advanced monthly strategy engine, apply the policy to the selected monthly owner payable in each month whose calendar year is at or after the effective year. The reduced selected amounts then aggregate into owner/household annual fields. For 100%, payable benefits become zero.

This ordering ensures a survivor benefit is reduced as a payable benefit regardless of whether its worker basis began before the effective date. It does not compound the percentage over time: each payment is scheduled benefit times one constant factor, while the existing COLA projection continues unchanged. Before the effective calendar year, output is bit-for-bit unchanged. At 0%, bypass the transform entirely to preserve current outputs and goldens.

The legacy household fallback and standalone single-person own-retirement path must be covered by the same policy. Prefer adding year-aware policy parameters to their calculation boundary, with compatibility overloads that default to no reduction. Do not scale both a selected monthly/annual payment and its household aggregate. For legacy annual records, implementation must settle the rounding point so that output remains consistent with its existing cents conventions. The advanced engine has monthly results, so specify whether each reduced monthly payable is rounded to cents before annual aggregation; maintain component/selected/household reconciliation by deriving the selected total from consistently reduced components or applying the factor to the selected owner total and rebuilding component attribution consistently. This is a focused financial rounding decision to validate against zero-rate exactness and annual cent invariants.

## Downstream financial dependency map

The projection path stores annual Social Security in `ProjectionYear`. `ProjectionEngine` and `TaxIncomeCalculator` use the projected result rather than independently recalculating benefits. That means a correctly reduced result naturally feeds:

- guaranteed household cash flow and any resulting portfolio withdrawals;
- taxable Social Security and federal/state tax calculations;
- Roth bracket-fill trial taxable income and final conversion tax funding;
- Medicare/IRMAA inputs that depend on annual income/tax;
- opening/ending portfolio balances, investment growth, net worth, estate, and projection metrics;
- Results Summary charts/metrics, Yearly Projection Details, and projection PDF inputs;
- deterministic and Monte Carlo projections, which use `ProjectionEngine`.

This propagation is conditional on avoiding direct scheduled-benefit calculations downstream. Audit confirmed the core ProjectionEngine consumes the provider result. Search and tests must still cover standalone reports and analysis adapters that may use calculator outputs directly. Do not reduce benefits again in taxes, charts, or PDF formatters.

## Analyzer compatibility matrix

| Analysis path | Current benefit path | Reduction coverage required |
|---|---|---|
| Couple Social Security Only deterministic grid | `SocialSecurityStrategyAnalysisRequestFactory` builds a `SocialSecurityStrategyRequest`; `SocialSecurityClaimingGridCalculator` evaluates through `SocialSecurityStrategyCalculator` | Carry reduction on the base request and through every candidate request; update strategy cache key |
| Couple Social Security Only mortality-weighted grid | Same base strategy; `SocialSecurityMortalityWeightedClaimingGridCalculator` derives mortality-specific requests and uses the strategy valuation calculator | Preserve reduction in derived strategies; rankings and expected values include it |
| Couple survivor claiming optimization / break-even comparison | Strategy calculator and comparison/valuation services | Preserve policy through request derivation and ranking/break-even values |
| Single-person Social Security-only mortality analysis | `SinglePersonMortalityAnalysis` calls `calculateOwnRetirementMonthly` directly | Add policy to monthly own-retirement path; ensure valuation and break-even use reduced monthly amounts |
| Individual/couple integrated deterministic analysis | Deep-copied plan projected by `ProjectionEngine` with strategy override | Inherited from frozen plan assumption and provider; do not mutate the plan for candidate strategies |
| Integrated quick comparison | Evaluates selected strategies using projection engine; candidate selection also depends on Social Security-only ranks | Integrated values inherit projection path; upstream SS-only candidate ranking must also use reduction |
| Deterministic exhaustive search and claiming grid | Complete plan projections per candidate | Inherited from projection path; fingerprints/result metadata include reduction |
| Longevity-weighted integrated analyzer | Frozen plan plus repeated exact-horizon `ProjectionEngine` outcomes | Inherited for annual financial metrics; Social Security-only expected-value/ranking subinputs need explicit policy propagation where used |
| Monte Carlo standalone/comparison | `MonteCarloAnalyzer` / strategy comparison call `ProjectionEngine` using frozen plan and effective settings | Inherited from plan; include in frozen input identity and prove random streams unchanged at 0% |
| Projection and analyzer history / PDFs | Frozen annual projection or retained analyzer result and captured assumptions | Do not recalculate historical values; show the captured reduction assumption when nonzero and retain original provenance |

The main architectural gap is that `SocialSecurityStrategyRequest` currently carries dates, elections, deaths, and COLA but no reduction assumption. `SocialSecurityStrategyCalculator.scheduleKey()` contains calculation inputs including COLA; if reduction becomes a request input it must enter the key, otherwise schedules computed with different reduction assumptions can alias in the equivalence caches. Grid and mortality request derivation methods must copy the new policy. Compatibility constructors should retain the existing zero-reduction behavior.

## Reporting and presentation

When reduction is zero, preserve existing result text and PDF content. For a nonzero assumption, include a concise “Social Security benefit reduction: R% beginning Y” disclosure in the projection assumptions summary and any full-plan analyzer/PDF assumption block. Social Security-only reports should capture the reduction in their immutable request/report context and disclose it. Historical reports must describe the assumption frozen with the selected result, not current plan settings. Yearly detail values should naturally show payable reduced income; optionally identify the reduction in a report-level assumption note rather than add a repeated per-year column.

Audit report adapters including `ProjectionPdfReportAdapter`, `IndividualAnalyzerReportAdapter`, `IntegratedAnalyzerReportAdapter`, Monte Carlo PDF adapters, Summary assumption presentations, and retained historical report adapters during implementation. Do not alter report titles/layouts or emit new disclosure for 0%.

## Fingerprints, revision, snapshots, and stale-result behavior

The two inputs affect financial results and therefore belong in configured input identity. `CompleteProjectionSnapshot.freeze(plan)` serializes the plan and computes an input digest; adding the values to the plan model makes accepted projections compare stale after edits. The application controller's normal plan-modified path should increment revision and invalidate projection freshness when Assumptions applies the change.

Explicit Social Security analyzer fingerprints must add the fields to their canonical social-security section. Integrated and weighted frozen plan snapshots carry them in plan JSON, but verify the exact kind-specific exclusions; only assumptions overridden by the analyzer's timing model should be removed, never benefit reduction. Monte Carlo effective-input capture must include the same plan values. Old fingerprints remain historical identifiers and should not be rewritten. Projection baselines created before the change deserialize with 0%/2032; those unchanged inputs should preserve results.

## Validation and edge cases

Recommended model rules: percentage must be non-null after defaulting, finite decimal in [0,100]; year is an integer four-digit calendar year. Do not restrict year to within the current projection horizon; an out-of-horizon setting is harmless and future plan horizons can change. A 0% reduction ignores the year for calculations. Invalid explicit non-null persisted values should fail strict domain construction rather than silently clamp; legacy missing/null values should default compatibly. UI parsing should reject malformed decimal input, malformed/noninteger/non-four-digit year when active, and preserve draft text on failure.

No Social Security law or claiming rules change. The feature represents a uniform user-specified reduction to retirement and survivor payments. It should apply only once to payments after selection and should be independent of mortality weighting. The planner's January 1 death-year projection convention remains unchanged; the reduction uses the payment calendar year, not death year or claiming age.

## Regression test plan

1. Legacy JSON omitting both fields loads as 0%/2032; explicit null policy; malformed JSON and invalid percentage/year rejection.
2. New EconomicAssumptions and full plan Save/Load round trip; baseline snapshot, Save As, swap, and plan revision/freshness.
3. Percentage boundaries 0, 0.1, 10, 17, 22, 100; reject below 0 and above 100; year before, at, and after effective year; year outside modeled horizon.
4. Exact 0% results match existing golden projections/analyzer outcomes; arbitrary effective years at zero have no financial effect.
5. COLA interaction: claim before year, benefits first claimed on/after year, no repeated/compounded reduction after effective year.
6. Own, spousal excess, survivor transitions and survivor claims; survivor begins before/at/after effective year; death-year Jan 1 convention.
7. Single person advanced and legacy fallback paths; couple advanced and compatibility fallback.
8. Projection result component reconciliation, annual cents/rounding, household income, taxable Social Security, state tax, withdrawals, Roth capacity/tax, Medicare IRMAA, account balances, net worth, and estate.
9. Deterministic analyzer grids/rankings, mortality-weighted strategy valuation, survivor optimization, break-even, integrated quick/exhaustive, and longevity-weighted projections.
10. Schedule/equivalence caches distinguish different reduction values/effective years.
11. Monte Carlo fixed/longevity and strategy comparison use the frozen assumption; 0% retains current random streams and exact results.
12. Projection and analyzer fingerprints become stale after either field changes; retained historical result values and provenance do not change.
13. Summary, Yearly Detail, projection and analyzer PDFs disclose nonzero assumption and omit disclosure at zero; historical exports use frozen context.
14. Assumptions UI parsing/validation, conditional year state, RequiredFieldSupport, draft Apply/Cancel, theme, compact width, and Summary column preservation.

## Recommended implementation phases

**19B1 — Domain and persistence:** add validated immutable economic assumption values and legacy defaults; test JSON/baseline/fingerprint reconstruction compatibility. Gate on zero-rate byte/financial regressions.

**19B2 — Shared Social Security calculation policy:** apply reduction exactly once in monthly advanced, single-person, and legacy paths; propagate through request derivation, strategy cache keys, deterministic and mortality-weighted calculators. Add schedule, survivor, COLA, and cents-invariant tests.

**19B3 — Projection/analyzer integration:** verify ProjectionEngine downstream propagation; wire Social Security-only, integrated, weighted, individual, break-even, Monte Carlo and frozen-input/fingerprint paths. Add cross-path agreement and stale-result tests.

**19B4 — UI and reporting:** add Assumptions controls, validation, tooltip, nonzero summary/report disclosures, accessibility, and Light/Dark previews. Keep Summary Key Assumptions in its current right column.

Each stage should have review/acceptance before the next because of the reach into financial result identity and historical-analysis freshness.

## Approval decisions required before implementation

1. Approve `EconomicAssumptions` as the persistence/domain home versus a separate Social Security assumption object.
2. Confirm defaults: 0% and effective year 2032, including missing/null legacy handling.
3. Decide whether effective year validation is conditional on reduction > 0 or always required despite being ignored at 0%.
4. Approve four-digit year range policy (recommend 1000–9999, without horizon restriction).
5. Approve payment rounding point for reduced monthly amounts while preserving component reconciliation and legacy annual conventions.
6. Confirm the reduction applies to all Social Security payable components, including survivor and spousal excess benefits, with the same percentage.
7. Approve conditional report disclosure only when reduction > 0, and whether the Key Assumptions panel receives a compact read-only row.
8. Approve explicit staleness for old analyzer results when assumptions change, while retaining historical snapshots unchanged.
9. Approve inclusion in Social Security-only ranking/present-value results as well as full-plan integrated analysis; this is the recommended consistent behavior.

## Verification and working-tree preservation

Audit-only focused verification used IntelliJ Maven with the repository-local Maven cache:

`SocialSecurityProjectionIncomeProviderTest`, `SocialSecurityStrategyCalculatorTest`, `SocialSecurityClaimingGridCalculatorTest`, `SocialSecurityMortalityWeightedClaimingGridCalculatorTest`, `AnalysisFingerprintTest`, `JsonRetirementPlanRepositoryTest`, and `ProjectionSwapTest` — **138 tests, 0 failures, 0 errors, 0 skipped**.

`git diff --check` passed. No visual inspection was performed because this phase changes no UI. Native Windows behavior was not tested.

At audit start, the current branch was `main` tracking `origin/main` and `git status --short --branch` showed a clean working tree. Phase 17B and Phase 18A implementation files/reports were present in the repository and were not modified. The only new working-tree file is this Phase 19A report. Nothing was staged, committed, pushed, reset, cleaned, or reverted.
