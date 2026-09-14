# Integrated result asset and heir metrics

The deterministic and longevity-weighted results expose the underlying investable
assets and heir value alongside their unchanged ranking objectives. This change
does not alter projections, mortality, claiming calculations, persistence, or
Stage 5D worker defaults.

## Deterministic results

Both Quick Comparison and Deterministic Exhaustive Search already contained
Ending Investable Assets and After-Tax Estate. After-Tax Estate is now labeled
**After-Tax Heir Value**, without adding a duplicate metric or calculation.

| Display | Authoritative source |
| --- | --- |
| Ending Investable Assets | `ProjectionMetrics.endingInvestableAssets`, from the final `ProjectionYear.getEndingInvestableAssets()` |
| After-Tax Heir Value | `ProjectionMetrics.afterTaxEstate`, from the same final `ProjectionYear.getAfterTaxEstateValue()` |

Both are nominal future dollars at the configured deterministic projection
horizon. The existing simplified heir-tax calculation estimates tax on deferred
accounts and subtracts that estimate from investable assets. This relabeling does
not include non-investable assets or introduce inherited-IRA distribution rules.
The deterministic `AFTER_TAX_ESTATE` ranking, grouping, ties, and current position
remain unchanged. Quick Comparison retains SS-selected candidate order.

The two monetary columns use BigDecimal cell values and numeric comparators.
User sorting changes only table order, never backend rank or stored result order.
Summary and selected-detail labels use the same heir-value terminology, and the
exhaustive summary now includes current and highest ending investable assets.

## Longevity-weighted results

| Display | Source and units |
| --- | --- |
| Expected Investable Assets at Second Death | Sum of original positive scenario probability times `EstateAtSecondDeathSnapshot.nominalInvestableAssets`; nominal future-dollar expectation |
| Expected After-Tax Heir Value | Existing `expectedNominalEstateAtSecondDeath`, which sums probability times `nominalAfterTaxEstate`; nominal future-dollar expectation |
| Expected PV After-Tax Estate | Existing `expectedPvAfterTaxEstate`; valuation-date dollars and unchanged ranking objective |

`LongevityWeightedIntegratedStrategyResult.expectedInvestableAssetsAtSecondDeath()`
traverses existing scenario outcomes in their original order using exact
BigDecimal multiplication and addition, with no MathContext truncation, cent
rounding, or probability renormalization. Zero-probability outcomes are excluded.
`LongevityWeightedStrategyAggregate` retains this one additional compact amount
before detailed outcomes are discarded. Independent and continuation evaluators
share this accessor; no second snapshot, projection, or heir-tax path was added.

Snapshot timing remains Stage 5G: January 1 second death in year D uses December
31 of D-1, or the opening snapshot when second death is on the opening date.
Configured projection-end assets are not substituted. The nominal expectations
combine amounts from different possible second-death dates; they are not PV and
are not ranking objectives.

Ranking remains `EXPECTED_PV_AFTER_TAX_ESTATE`. **Difference vs Current** remains
candidate expected PV minus current expected PV. A missing/incomplete or failed
baseline still yields an unavailable comparison. The current card's difference
from itself is zero when its evaluation is available.

## Presentation and limitations

The weighted table, Highest/Current cards, and selected details show all three
metrics. Cards/details visibly separate nominal expectations and valuation-date
PV; wrapped table headers have corresponding explanatory tooltips. Minimum and
maximum nominal scenario estate, probability coverage, methodology, and inherited
account limitations remain visible.

Compare Analysis Outcomes uses separate deterministic and weighted columns, with
metrics stacked within each. Deterministic values are labeled configured-horizon
future dollars; weighted nominal and PV values have separate labels. No
deterministic-minus-weighted monetary difference is calculated. Existing source
revision and election compatibility checks still control deterministic joins.

Weighted metrics exclude homes and other non-investable assets. The existing
simplified heir-tax haircut remains; inherited-account retitling, inherited-IRA
distribution requirements, and estate-settlement mechanics are not modeled.

Weighted columns now use unconstrained resizing so horizontal scrolling is
available at narrow widths. All requested metrics remain table columns. Table
height settings are unchanged: minHeight 110, computed preferred/max height,
and VBox growth NEVER. Row count does not increase the height. Natural-height
expandable content continues to use the shared outer analyzer ScrollPane.

## Verification

Tests cover authoritative deterministic final-year values, typed sorting, distinct
nominal/PV mappings, PV differences, opening and later-death snapshots, zero mass,
sub-cent arithmetic, no renormalization, continuation equality, and layout at
1180x820, 900x650, and 1366x768. All Maven runs use the repository-local cache,
software JavaFX rendering, and `-XX:ActiveProcessorCount=2`, sequentially.
Benchmark classes and full 5,184-strategy weighted evaluation are excluded.

Focused results: deterministic presentation 2 passed; weighted aggregation and
continuation/comparison checks 50 passed; presentation/layout/state 32 passed;
Stage 5C3/D/F/G references 91 passed; SS precision references 31 passed. These
groups overlap with the complete suite; they are not additional unique tests.

The single stabilized complete non-benchmark run on 2026-09-10 finished with
1,098 tests, zero failures, zero errors, and three skipped. `git diff --check`
passed. Test logs remain in ignored `target/`; no commit was made.

## Files changed for this enhancement

Added: `INTEGRATED-RESULT-ASSET-METRICS.md`.

Production Java files (under the existing application/UI packages):

- `LongevityWeightedIntegratedStrategyResult.java`
- `LongevityWeightedStrategyAggregate.java`
- `IntegratedAnalysisComparisonPresentation.java`
- `LongevityWeightedIntegratedView.java`
- `SocialSecurityStrategyAnalyzerDialog.java`

Tests:

- `LongevityContinuationTest.java`
- `LongevityWeightedIntegratedStrategyEvaluatorTest.java`
- `Stage5DComparisonTest.java` (preserves the new amount in its synthetic tie fixture)
- `IntegratedAnalysisComparisonPresentationTest.java`
- `LongevityWeightedIntegratedPresentationTest.java` (typed aggregate fixture)
- `LongevityWeightedIntegratedLayoutTest.java`
- `SocialSecurityStrategyAnalyzerDialogStateTest.java`

Also updated the table-column-policy note in `ANALYZER-INPUTS-AND-DATES.md`.
Earlier uncommitted input/date, sizing, and SS precision work remains intact.
No persistence or financial-model changes beyond the new derived expectation.
