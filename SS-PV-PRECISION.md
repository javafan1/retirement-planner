# Social Security present-value precision

## Rounding map (inspected before implementation)

| Location | Existing operation | Classification / treatment |
| --- | --- | --- |
| `SocialSecurityPresentValueCalculator.toBaseDateRealAmount` | DECIMAL128 division followed by `setScale(2, HALF_UP)` | Decimal division precision is necessary; monthly cent rounding is unnecessary intermediate rounding. Remove the latter. |
| `SocialSecurityPresentValueCalculator.presentValue` | `doubleValue`, `StrictMath.pow`, DECIMAL128 division, then `setScale(2, HALF_UP)` | Historical binary approximation and unnecessary monthly cent rounding. Use decimal effective-annual discounting and retain DECIMAL128 output. |
| Same calculator, `integerGrowthFactor` | DECIMAL128 power/reciprocal | Necessary finite decimal precision; keep calendar-year COLA convention. |
| `SocialSecurityStrategyValuationCalculator` (all three paths) | Exact BigDecimal sums of monthly values | No additional rounding; preserve reconciliation and streaming/audit equivalence. |
| `SocialSecurityMortalityWeightedStrategyCalculator` | Exact probability multiplication/sums, then `money(real)` / `money(presentValue)` | Historical cents contract, but also a ranking boundary. Remove real/PV cent rounding to avoid valuation-origin-dependent artificial ties. |
| Same calculator | `money(primary)`, `money(spouse)` and their sum | Historical public nominal reconciliation contract. Preserve. |
| `SocialSecurityMortalityWeightedComparisonCalculator` | Exact weighted sums, then cents rounding of nominal, real and PV totals; differences from those totals | Preserve nominal contract. Real/PV totals and differences must retain precision for comparison and reference agreement. |
| Strategy/own/spousal/survivor benefit calculators | Monetary benefit rounding, including nominal COLA-adjusted benefits | Existing financial benefit rules; outside this correction, unchanged. |
| Mortality distribution and joint probability calculators | Probability MathContexts; exact joint products | Existing mortality semantics; unchanged. |
| `UIFormatters.money` | Currency NumberFormat with zero fractional digits | Presentation-only; unchanged and never fed into ranking. |

## Precision and result contracts

Monthly real/PV arithmetic returns DECIMAL128 (34 significant digits), with
40-digit guard precision for the fractional effective-annual discount factor.
Discounting remains `(1 + realRate)^(months / 12)`; real-dollar conversion remains
the existing calendar-year COLA conversion. No financial binary floating point.
Monthly amounts are summed and multiplied by probabilities without cent rounding.

The stronger real/PV representation is necessary through the public analysis
records because these same fields are authoritative ranking inputs. Even rounding
only the final expected PV to cents can merge/unmerge near ties when the valuation
origin scales every strategy. These transient records now retain precision rather
than an incidental cents scale. Nominal benefit/owner totals retain their existing
contracts. Record signatures, reconciliation checks and persistence are unchanged.
The final currency-rounding boundary is presentation (`UIFormatters.money`, whole
dollars in the analyzer). Consumers needing cents may round a display copy only.

Ranking uses `presentValue` / `expectedPresentValue` BigDecimals with `compareTo`,
including SS grid/survivor search, analyzer competition ranks and Quick cutoff ties.
Quick can select different near-tie candidates because the SS ranking is more
accurate; its deterministic projection calculations are unchanged.

With fixed cash flows, mortality and rates, moving base month from b to b' scales
PV by `(1 + COLA)^(year(b') - year(b)) * (1 + realRate)^(months(b,b') / 12)`.
Tests check this relation at a precision justified by DECIMAL128, not loose cents
tolerances. Finite precision cannot promise distinct ordering for arbitrarily
close mathematical values below its resolution. Identical cash-flow ties remain
exact. Changing the SS analysis start date or mortality conditioning is not a
valuation-only change.

The integrated longevity-weighted objective uses `EstatePresentValueCalculator`,
not this calculator. Its ACT/365.25 formula, financial scenarios and ranking are
unchanged. No production worker settings are changed.

## Reference changes and financial effect

The two former hard-coded monthly calculator expectations `1071.43` and `956.63`
are now exactly `1200 / 1.12` and `1200 / 1.2544`, respectively, using DECIMAL128.
Their displayed cents are unchanged. The two manually weighted PV expectations
in `SocialSecurityMortalityWeightedComparisonCalculatorTest` now compare exact
weighted sums rather than cent-rounded sums; no tolerance replaces equality.
No other existing fixed expected values changed.

For the original close pair (primary claims 62 versus 70, spouse claims 67), with
fixed mortality and real rate `0.053418731689453125`, final display cents change:

| Origin | A before | A after | B before | B after |
| --- | ---: | ---: | ---: | ---: |
| 2025-01-01 | 589455.99 | 589456.08 | 589456.01 | 589456.07 |
| 2026-01-01 | 633362.99 | 633362.96 | 633362.95 | 633362.95 |

A now ranks above B at both origins. The common year-shift factor is exactly
`1.02 * 1.053418731689453125`, to DECIMAL128 arithmetic resolution.
A second close pair (63 versus 69; real rate `0.05559856779873371124267578125`)
differs by about 0.00024 in 2025 PV. Both round to the same cents, yet retain
distinct SS ranks and a one-candidate Quick cutoff at both tested origins.

Tests also cover exact ties, wider separation, negative/zero/tiny discount rates,
survivor and own-only elections, zero benefits, monthly/annual origin shifts,
short and 60-year horizons, and a partial opening calendar year. The existing SS
convention includes the opening calendar month; no new daily proration is added.
Audit, compact and streaming totals agree exactly with an independently summed
decimal-power reference. Finite common-scaling checks use a `1E-30` relative
bound justified by 34-significant-digit monthly calculations.

This resolves the precision blocker for the separate analyzer input-grid/date
separation task. That task still needs to separate valuation, mortality
conditioning and analysis-start inputs; this correction does not change them.

## Verification (2026-09-10)

- Original invariant: passed before expanding the reference coverage.
- Focused precision and Quick tests: 45 tests, zero failures/errors.
- Final SS domain/analyzer/Quick reference pass: 258 tests, one existing skip,
  zero failures/errors.
- One complete non-benchmark suite after stabilization: 1,079 tests, three
  existing skips, zero failures/errors; Maven BUILD SUCCESS.
- Existing direct weighted-sum assertions were updated to exact unrounded
  equality after the initial reference run exposed the old cents assumption.
- No benchmark or 5,184-strategy integrated weighted exhaustive run was performed.

All Maven runs used IntelliJ's bundled Maven, the repository-local
`.codex-m2/repository`, one process at a time, `-XX:ActiveProcessorCount=2`, and
`-Dprism.order=sw`. JavaFX runs used the existing repository-local native/cache
directories. The complete suite selector was `-Dtest=*Test,!*BenchmarkTest`.

Files for this correction (earlier UI sizing changes remain separate):

- `domain/socialsecurity/analysis/SocialSecurityPresentValueCalculator.java`
- `domain/socialsecurity/analysis/SocialSecurityMortalityWeightedStrategyCalculator.java`
- `domain/socialsecurity/analysis/SocialSecurityMortalityWeightedComparisonCalculator.java`
- `SocialSecurityPresentValueCalculatorTest.java`
- `SocialSecurityStrategyValuationCalculatorTest.java`
- `SocialSecurityMortalityWeightedComparisonCalculatorTest.java`
- `SocialSecurityValuationDateInvarianceTest.java` (existing uncommitted regression expanded)
- `ui/socialsecurity/QuickComparisonPrecisionTest.java` (new)
- This document (new).
