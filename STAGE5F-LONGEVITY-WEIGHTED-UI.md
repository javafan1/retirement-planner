# Stage 5F: Longevity-weighted integrated analyzer UI

## Navigation and objectives

The two top-level tabs remain Social Security Only and Integrated Retirement Plan.
Social Security Only retains Recommendation, Claiming Grid, Top Strategies and Assumptions.
Integrated Retirement Plan contains Deterministic and Longevity-Weighted.
Deterministic retains Quick Comparison and Deterministic Exhaustive Search.

The three objectives remain distinct:

- SS-only ranks expected present value of Social Security benefits under longevity assumptions.
- Deterministic exhaustive ranks after-tax estate at the configured plan horizon/death scenario.
- Longevity-weighted exhaustive ranks `EXPECTED_PV_AFTER_TAX_ESTATE`: after-tax investable estate
  at second death, converted to valuation-date present value and probability weighted.

Quick Comparison still evaluates an SS-only-selected set with deterministic full-plan financial outcomes.
It does not select an integrated winner. No strategies are applied or persisted by this analyzer.

## Request construction

`LongevityWeightedAnalysisRequestFactory` captures an isolated plan copy and immutable longevity categories,
adjustments, valuation date, real discount rate and dialog-local source/assumptions revision stamps on FX.
The household identities and persisted elections are captured within that plan copy. No live controls or
mutable active-plan references pass to workers.

Under Stage 5E admission, the factory prepares mortality scenarios and enumerates the complete Cartesian
product of the existing standard retirement-age lists and authoritative survivor candidates, in deterministic
generation order. Retirement dates use the existing authoritative date calculator. Candidate generation
and baseline extraction use only the captured plan. Neither requires an SS-only result or its cutoff.
The existing weighted comparison request makes its own isolated plan copy and validates supported shapes.

An explicit persisted survivor policy yields `explicitCurrentStrategy(plan)`. A null survivor policy yields
`Optional.empty()`; candidate analysis continues and known retirement elections remain visible. The UI says
that current comparison requires a complete survivor policy and that no age has been assumed. Other
validation failures are not converted into missing-policy outcomes.

## Presentation and metric units

`LongevityWeightedIntegratedPresentation` consumes the existing Stage 5 result records. It does not rank
again. Original occurrence identities and backend competition ranks (1, 1, 3) remain unchanged.
An exact baseline candidate match reuses that rank; otherwise the current metric position is one plus the
number of successful candidates strictly above baseline. It is labeled position, not backend rank.
Percentage improvement uses BigDecimal and is unavailable for a nonpositive baseline.

All original rows remain available. Exact unrounded PV equality determines ties. Proven equivalence is
reported only from the returned equivalence-plan membership; rounded dollar equality is not a proof.
The UI does not collapse weighted rows using deterministic financial grouping.

The seven primary columns are Weighted Rank, Primary Retirement, Spouse Retirement, Primary Survivor,
Spouse Survivor, Expected PV After-Tax Estate, and Difference vs Current. Numeric values are sort keys;
formatting is only for cells. Highest/Current text markers supplement rank. Failed rows have no financial
values or rank. The default sort is ascending weighted rank. Sort changes never alter stored ranks.

Highest/current summary cards disclose all four elections, valuation date, PV, rank/position and ties.
Highest improvement versus current uses the backend PV difference and presentation-only percentage.

`IntegratedAnalysisComparisonPresentation` joins exact complete elections only when both results are
current for the same source revision and have identical candidate-universe occurrence counts. The comparison
combines identical role strategies while preserving Deterministic highest, Longevity-weighted highest and
Current labels. A highest row represents the first tied strategy, with ties disclosed separately. No
deterministic run is triggered automatically. A missing/stale/incompatible deterministic result yields
unavailable deterministic values without clearing the weighted result. Selected-row details can reference
any compatible deterministic candidate, not just the summary rows.

Deterministic estate is labeled Future Dollars; Expected PV After-Tax Estate is labeled Valuation-Date
Dollars. These values are never subtracted from each other. The UI explains the configured-horizon versus
probability-weighted second-death objectives. Non-investable assets are excluded from the weighted metric.

## Methodology and limitations

Stage 5G corrects the weighted horizon: each mortality scenario requires only the
December 31 snapshot before January 1 second death, or its matching opening snapshot.
Financial execution ends exactly at that snapshot regardless of the configured
plan horizon. Post-snapshot failures are irrelevant; earlier failures remain errors.
The weighted UI exposes this as methodology text, not an editable horizon option.
See [Stage 5G](STAGE5G-EXACT-SECOND-DEATH-HORIZON.md).

The view explains independent spouse mortality, combinations of death years, and full retirement-plan
projection including taxes, RMDs, Roth conversions, pensions, Medicare, withdrawals and investment growth.
Estate is measured at household second death, deflated by general inflation, discounted at the real rate
to the analyzer valuation date and probability weighted.

Expanded methodology discloses January 1 modeled deaths, preceding December 31 or matching opening
snapshot measurement, NEXT_COMPLETE_BIRTHDAY_INTERVAL mortality conditioning, actual days / 365.25
discount timing, original probabilities not renormalized, and result-time rates/dates/mortality metadata.

A persistent wrapped callout above results states that inherited-account retitling/distribution rules are
not modeled, deceased-owner balances remain household assets, this can materially affect estate estimates,
and non-investable assets are excluded. Expanded details retain backend limitations including unchanged
account tax classification and the simplified estimated heir-tax haircut. This is not a runtime failure.

The shared assumptions caption distinguishes SS-only and weighted use from Quick candidate selection
and deterministic outcomes. It explicitly says the single valuation-date control also sets mortality
conditioning, which is a distinct concept. It does not change the plan projection start date.

## Lifecycle, invalidation and threading

The weighted action calls the existing `SocialSecurityAnalyzerJobController` with `Mode.WEIGHTED` and
the default `LongevityWeightedIntegratedStrategyComparisonService.compare` production path:
Stage 5C3 proof, Stage 5C2 continuation reuse, Stage 5D bounded representative execution.
No controller, executor or financial engine is added. Stage 5D production default remains **4**, cap **8**.

All analyzer Run actions and inputs are disabled during work/cleanup. Common Cancel/progress/status is
visible above the mode tabs. Phase counts describe only the current phase, never an invented overall percent:
Preparing longevity scenarios; Proving equivalent claiming strategies; Evaluating retirement outcomes; Complete.
Cancellation remains cooperative. Admission is released only after cleanup. Closing is nonblocking and
uses the existing controller close/dispose and stale-publication guards.

| Change | SS-only | Quick | Deterministic exhaustive | Weighted |
|---|---|---|---|---|
| Source revision | Stale | Stale | Stale | Stale |
| Mortality categories/adjustments | Stale | Stale | Unchanged | Stale |
| Valuation date/real discount | Stale | Stale | Unchanged | Stale |
| Quick count | Unchanged | Stale | Unchanged | Unchanged |
| New SS-only result | Updated | Stale | Financial result unchanged; SS reference refreshed | Unchanged |
| Weighted settings (future controls) | Unchanged | Unchanged | Unchanged | Stale |

Weighted invalidation does not depend on any previous SS-only run. Old results retain their result-time
labels and show Inputs changed — rerun. Rerunning preserves the previous result; valid success replaces it.
Cancellation, fatal failure and obsolete publication preserve it. Structured failures publish completed
results with separate candidate and baseline failure reporting. All-candidate failure has no highest strategy.

`LongevityWeightedIntegratedView` formats and renders only on FX, through guarded controller publication
or FX input/selection handlers. Financial workers do not access controls or shared money formatters.

## Detail retention and layout

The production UI request is aggregate-only. Selected rows immediately show exact elections, expected PV,
expected/minimum/maximum nominal estate, probability coverage, scenario count, baseline delta, compatible
deterministic rank, methodology and limitations. Reported work counts and timings are secondary technical
details, never inferred from strategy counts.

**On-demand scenario loading is deferred.** There is no fake or disabled loading action, no retained exhaustive
scenario matrix and no second lifecycle. A future bounded detail adapter must reuse the same frozen
result-time inputs, original occurrence identity, existing service, Stage 5E admission and 20-strategy cap.
It must not replace the exhaustive ranking. The UI explicitly identifies aggregate-only detail availability.

The seven-column constrained table targets roughly 810 pixels. Wrapped headers and collapsible summaries,
comparison, methodology and details preserve table space. The weighted section itself scrolls vertically at
small sizes. Synthetic JavaFX layout tests cover 1180x820, 900x650 and 1366x768 with software rendering.
Keyboard-focusable Run/Cancel controls and textual status markers avoid color-only meaning.

## Verification policy and follow-up

Use IntelliJ bundled Maven and the repository `.codex-m2/repository`. Verification runs one Maven process
at a time, with software JavaFX rendering and a workspace-local JavaFX cache. `-XX:ActiveProcessorCount=2`
bounds normal financial worker execution during verification without altering production defaults.
Benchmark-named tests are explicitly excluded per this session's machine-stability requirement.

Remaining acceptance work: interactive readability/keyboard review on the user's display and representative
plan acceptance. There are no changes to financial formulas, Stage 4/5A–5E services, persistence or build
configuration. The inherited-account and supported-model boundaries remain existing financial limitations.

### Verification results (2026-09-09)

All final groups passed with zero failures and zero errors:

| Group | Tests | Skipped |
|---|---:|---:|
| Weighted request / presentation / cross-reference | 15 | 0 |
| Dialog state / input | 14 | 0 |
| Stage 5E controller / progress / weighted adapter | 35 | 0 |
| New Stage 5F real weighted cancellation / close / queued-publication tests | 5 | 0 |
| Application Social Security financial/reference group, excluding benchmarks and Stage 5F lifecycle | 215 | 2 |
| Entire non-benchmark Maven suite | 1,037 | 3 |

The full verification invocation was:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' `
  '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' `
  '-Dtest=!*BenchmarkTest' `
  '-DargLine=-XX:ActiveProcessorCount=2 -Dprism.order=sw -Djavafx.cachedir=C:\Users\david\IdeaProjects\retirement-planner\target\javafx-cache' test
```

BUILD SUCCESS. Benchmark exclusion is intentional, so this is not an unfiltered comparison to the supplied
Stage 5E total. Reference tests detected no deterministic or weighted financial-output changes. No real-plan
performance run was performed. A first JavaFX test attempt could not write its default native cache outside
the workspace; using the local cache resolved that environment issue. Subsequent FX tests passed.
