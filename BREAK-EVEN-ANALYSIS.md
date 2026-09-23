# Baseline vs Current Break-Even Analysis — V1

## Audit and baseline handling

- `ResultsSummaryView` owns the existing Baseline Comparison presentation; `ResultsView` is the separate annual results table. Both now expose Break-Even Analysis.
- `ApplicationController` holds Current and Baseline projection caches. Existing normal comparison refresh prepares these caches and the separate non-investable asset projections. The analysis action never calls lazy projection getters.
- Persisted Baseline contains a `RetirementPlanSnapshot`, not serialized `ProjectionYear` results. The existing baseline workflow prepares its projection from that saved snapshot. V1 consumes that already-prepared projection as-is; it never extends, extrapolates, or recalculates it.
- The audit found that `RetirementPlanSnapshot.fromRetirementPlan` shared mutable household and account objects with Current. New saves now deep-copy the snapshot using its existing Jackson representation. No JSON fields were added. This isolates saved elections and other assumptions from later Current edits.
- The baseline cache is now associated with the actual `ProjectionBaseline` identity. Switching plans/baselines cannot reuse another baseline's results.
- Names, birth dates, and retirement claiming ages are captured as immutable metadata when their corresponding projection is prepared. Baseline metadata comes only from its saved snapshot; Current metadata comes from Current. Missing or ambiguous Social Security records display “Unavailable”.
- Previously overwritten historical baseline data cannot be reconstructed. This change does not attempt a migration or infer old elections from Current.

## Architecture

`BreakEvenProjectionSnapshot` holds supplied annual results, non-investable asset results, and immutable household metadata. `BreakEvenAnalyzer` produces `BreakEvenAnalysisResult`, containing horizon metadata and a `BreakEvenMetricResult` for each metric. Each metric provides ordered `BreakEvenYearResult` points, status, first crossover, sustained break-even, all upward/downward crossings, and final difference.

The analyzer has no JavaFX, controller, projection-engine, repository, or plan-writing dependencies. Results are immutable. The controller's `getCachedBreakEvenAnalysis` reads populated caches only; missing caches return no available analysis. Views receive an already-calculated result. The dialog can change the displayed metric without any financial analysis or projection calls.

## Exact metric definitions

All differences are **Current minus Baseline**, using full `BigDecimal` precision.

| Metric | Value compared |
| --- | --- |
| Cumulative Social Security Benefits | Sum of each shared year's `ProjectionYear.getSocialSecurityResult().householdBenefit()`, independently for each plan. Accumulation begins at the first shared year and excludes non-shared years. |
| Investable Assets | `ProjectionYear.getEndingInvestableAssets()` for that year; never summed across years. |
| Total Net Worth | Ending investable assets plus the same year's separate projected non-investable asset total, matching existing Baseline Comparison. Missing non-investable totals use the existing zero convention. |
| After-Tax Estate | `ProjectionYear.getAfterTaxEstateValue()`; never summed across years and no additional non-investable value added, matching existing comparison semantics. |

No tax, Social Security, investment, Roth, RMD, liability, or estate calculation is recreated or changed by this analysis. Values are nominal projection dollars; V1 adds no discounting.

## Comparable period and outcomes

The analyzer sorts and intersects actual calendar years. It does not match by index, fill gaps, or fabricate values. Duplicate calendar years are rejected explicitly. Empty/disjoint projections return `NO_COMPARABLE_YEARS` and no points.

The result exposes both plans' actual start/end years, comparison start/end years, comparable count, and whether their available year sets differ. The chart stops at comparison end. Different starts, ends, and missing years are explained above the chart. If one plan ends earlier, the message states that later break-even cannot be determined from the available results.

- **First crossover:** the first transition from a negative difference to a nonnegative difference in consecutive available comparison points.
- **Sustained break-even:** the first nonnegative point after the last negative point, provided it exists. Every remaining comparison point must be nonnegative.
- **Break-even reached:** a deficit exists and sustained recovery is established.
- **No break-even within comparable period:** no recovery crossover and a deficit remains at the end. Final deficit is exposed/displayed.
- **Crossover not sustained:** at least one recovery crossover occurs, but the last point is negative.
- **Current already ahead:** all points are nonnegative, with at least one positive point. No traditional crossover year is assigned.
- **Identical:** all differences are exactly zero. No meaningful break-even is assigned.

An opening equality or advantage does not conceal a later deficit. All direction changes remain available in the result model. “Sustained” always means through the final comparable year, never beyond it.

Comparison tolerance is **exactly zero**. Display rounding is never used for status or crossover calculations, including sub-cent deficits. Currency presentation uses cents. Primary age comes from the Current `ProjectionYear`; spouse age uses the existing December 31 whole-year convention and captured birth date.

## UI

The dialog includes Baseline/Current claiming-age summaries, all four break-even summaries, comparison-period explanation, metric selector, one difference chart, a zero reference series, sustained marker, annual-point tooltips, and a bounded scrollable annual table. Positive/negative symbols reuse the existing comparison palette. No PDF export or future-feature placeholder was added.

Both Results actions are disabled without a calculated result containing at least one shared year. Normal result refresh clears stale analyses. Opening/closing the dialog does not alter the selected annual row, projections, assumptions, modified state, or persistence.

## Files in this change

Added:

- `src/main/java/com/daviddunn/retirementplanner/domain/breakeven/BreakEvenMetric.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/breakeven/BreakEvenStatus.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/breakeven/BreakEvenPlanSummary.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/breakeven/BreakEvenProjectionSnapshot.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/breakeven/BreakEvenYearResult.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/breakeven/BreakEvenMetricResult.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/breakeven/BreakEvenAnalysisResult.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/breakeven/BreakEvenAnalyzer.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenPresentation.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenAnalysisView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenAnalysisDialog.java`
- `src/main/resources/css/break-even.css`
- `src/test/java/com/daviddunn/retirementplanner/domain/breakeven/BreakEvenAnalyzerTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenAnalysisViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/controller/BreakEvenControllerTest.java`
- `BREAK-EVEN-ANALYSIS.md`

Updated:

- `src/main/java/com/daviddunn/retirementplanner/domain/baseline/RetirementPlanSnapshot.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/baseline/RetirementPlanSnapshotTest.java`

Earlier uncommitted survivor-benefit, year-navigation, and analyzer work is separate from this inventory.

## Verification

Focused tests cover normal/absent/temporary recovery, identical/already-ahead results, all crossing directions, precision, both horizon directions, gaps/different starts, SS accumulation, balance metrics, empty intersections, ages, immutable results, frozen claiming metadata and JSON round-trip, baseline cache identity, dialog actions/selection preservation, metric switching, marker/chart/table data, and differing-horizon presentation. Controller tests prohibit projection calls during analysis and verify unchanged plan JSON, modification state, source revision, and cached projection identities.

Test logs: `target/break-even-domain.log`, `target/break-even-ui.log`, `target/break-even-focused.log`, and `target/break-even-full.log`.

Final results:

- Initial analysis/baseline tests: 25 tests, zero failures/errors.
- Expanded focused run (Break-Even, baseline, Results/year-details, and Social Security regression tests): 387 tests, zero failures/errors, 1 skipped.
- Final JavaFX presentation verification: 4 tests, zero failures/errors (`target/break-even-final-ui.log`).
- Full non-benchmark Maven suite on final source: 1,336 tests, zero failures/errors, 3 skipped; BUILD SUCCESS.

Used IntelliJ's bundled Maven and the required repository-local `.codex-m2/repository`, with test user-home redirected into `target/break-even-test-home`. Full-suite selector: `-Dtest=*Test,!*BenchmarkTest`. No commits were made.
