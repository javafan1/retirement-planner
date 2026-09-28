# Monte Carlo Phase 3 V1 — stochastic general spending inflation

## Starting revision and audit

Starting HEAD: `f8ac27f7d803f63867d293973a0aeeebeb7ad01e`, `main`, subject
`Add longevity-adjusted Monte Carlo analysis` (Commit E). The initial worktree and
index were clean. `git status --branch --short` reported `## main...origin/main`;
this describes the locally known remote-tracking reference, without a fetch.
Prior commits were `5a64a95` aggregation, `d30b3f4` execution, `d8c96db` worlds,
and `a6c6305` random streams. No staging, commits, or history operations were used.

The read-only audit was completed and summarized before production changes. A
minimal spending-only integration was available; no engine redesign was needed.

| Concept | Existing authoritative source/use | Phase 3 treatment |
|---|---|---|
| General CPI-style spending | `EconomicAssumptions.generalInflationRate`; `PlanningAssumptions` delegates | Optional annual path for non-healthcare expenses only |
| Healthcare spending | `EconomicAssumptions.healthcareInflationRate` | Deterministic |
| Social Security COLA | `EconomicAssumptions.socialSecurityColaRate`; advanced income provider and legacy/ROTH tax inputs | Separate and unchanged |
| Federal brackets and standard deduction | Separate growth rates in `TaxAssumptions`, consumed by `FederalTaxRuleProjectionService` | Deterministic |
| Michigan exemptions/deductions | `MichiganTaxRuleProjectionService` reads general inflation through `getExpectedAnnualInflationRate()` | Deterministic |
| IRMAA thresholds | `IrmaaRuleProjectionService` reads the same general-inflation alias | Deterministic |
| Medicare Part B/D premium schedule | `IrmaaRuleProjectionService` uses healthcare inflation | Deterministic schedule; existing income-dependent tier selection unchanged |
| Pension COLAs | Each `Pension.annualColaRate`, compounded from pension start | Unchanged; no general-inflation reference |
| Non-investable assets | Each asset's `annualGrowthRate` via `NonInvestableAssetProjectionService` | Unchanged |
| Weighted estate present value | General inflation plus real discount rate in `EstatePresentValueCalculator` | Unchanged; separate weighted analyzer, not Monte Carlo |

`EconomicAssumptions` supports legacy JSON and constructors that initialize both
healthcare and SS COLA from general inflation when separate values are absent.
Those are stored separate assumptions, not a dynamic linkage. No stochastic path
is substituted into the plan-wide object. This avoids silently randomizing tax
indexation, IRMAA thresholds, Medicare premiums or Social Security.

Before Phase 3, `ProjectionEvaluationContext` carried claiming/lifetime/survivor
and horizon overrides only. `ProjectionEconomicPath` already supported annual
investment returns, but there was no annual general-inflation path. Government
rules were projected for each calendar year using fixed plan assumptions, not
year-specific economic overrides. Plan building, copying and persistence continue
unchanged. `MonteCarloRunService` freezes the plan and settings for worker execution.

### Inventory of existing general-inflation readers

The audit searched `getGeneralInflationRate`, `getExpectedAnnualInflationRate`,
`getInflationRate`, `generalInflationRate` and inflation/COLA/growth consumers.
The existing direct readers (excluding getter declarations) were:

- `domain/projection/ProjectionEngine.getExpenseGrowthRate`: all non-healthcare
  expenses, including recurring, delayed-start and one-time expenses.
- `domain/tax/state/michigan/MichiganTaxRuleProjectionService`: two rule-growth uses.
- `domain/medicare/IrmaaRuleProjectionService`: income-threshold growth.
- `app/socialsecurity/LongevityWeightedContinuationEvaluator` and
  `LongevityWeightedIntegratedStrategyEvaluator`: estate PV conversion.
- `app/socialsecurity/LongevityWeightedIntegratedStrategyComparisonRequest`:
  rate validation; `LongevityWeightedIntegratedStrategyComparisonService`: metadata.
- `ui/views/AssumptionsView`: edit/display and assumption reconstruction.
- `ui/views/ResultsSummaryView`: sensitivity input/display; reconstructed economic
  assumptions preserve separate healthcare and COLA values.
- `ui/socialsecurity/SocialSecurityAnalyzerInputSummary`: presentation.
- `app/export/ProjectionPdfExporter` and `ui/console/ConsoleReportPrinter`: reporting.
- `domain/model/PlanningAssumptions`: delegation/legacy aliases. Its `getInflationRate`
  has no external production callers. `EconomicAssumptions` resolves old JSON inputs.

Indirect general-rate value consumers include `EstatePresentValueCalculator`,
weighted strategy result/metadata records, `IntegratedAnalyzerReportAdapter`, and
`LongevityWeightedIntegratedView`. None changed. The added UI read initializes the
session mean; the original plan remains authoritative when stochastic inflation is off.

## Model, stream protocol and ownership

For each scenario and calendar year:

```text
Z[y] ~ independent standard Normal
i[y] = max(floor, mean + volatility * Z[y])
M[firstYear] = 1
M[y] = M[y-1] * (1 + i[y]), y > firstYear
generalExpense[y] = openingBaseExpense * M[y]
```

The floor clamps draws, rather than resampling a truncated normal. Mean and
volatility describe the underlying normal distribution: flooring changes its
realized moments, with no normalization. Floor must exceed -100%, volatility must
be nonnegative, and mean must be at least the floor. Zero volatility returns the
exact `BigDecimal` mean without consuming random bytes. Invalid/non-finite generator
parameters fail explicitly. UI inputs have explicit finite percentage bounds.

No existing stochastic inflation defaults were found. UI defaults are off, mean
from the current plan, volatility 1.75%, floor -2%. These are editable session-only
modeling defaults: moderate annual variability and allowance for mild deflation,
not empirical calibration or a financial recommendation. New-plan loading resets
these controls. Disabling inflation ignores the inactive stochastic fields.

- `MonteCarloSettings` owns optional `MonteCarloInflationSettings`. Empty means the
  exact previous deterministic behavior; existing four-argument callers remain valid.
- `MonteCarloRandomStreams` owns derivation: existing reserved `GENERAL_INFLATION=4`,
  new dimension-local `GENERAL_INFLATION_V1=1`. Household/primary/spouse/healthcare
  IDs remain 1/2/3/5. Protocol version and SHA-256 encoding are unchanged.
- `MonteCarloInflationGenerator` owns model `FLOORED_NORMAL_GENERAL_INFLATION_V1`.
  Each draw consumes two big-endian 64-bit words. Their high 52 bits yield open-unit
  uniforms `(bits + 0.5) * 2^-52`. Box-Muller uses StrictMath log/sqrt/cos, discarding
  the sine partner. There is no spare-draw cache or shared mutable RNG.
- Double arithmetic is confined to random generation/parameter representability
  validation. `BigDecimal.valueOf` crosses into projection arithmetic.
- `MonteCarloWorldGenerator` composes existing market and lifetime outcomes with
  the optional inflation path. `MonteCarloWorld` remains immutable exogenous input,
  with no projection results. Its old constructor remains supported.
- `MonteCarloAnalyzer` injects paths into immutable evaluation contexts. Fixed mode
  retains its existing execution shape; longevity mode uses the world path.

Seed, scenario index, calendar origin, model/stream version and settings determine
inflation draws. Count, execution order, market draws and mortality draws do not.
Extending coverage appends to the prefix. Mortality may change the required path
length, but overlapping years retain identical inflation draws. Market generation
and mortality selection code and stream consumption remain unchanged. All worlds
retain empirical weight 1/N, with sequential execution and existing cancellation.

## Projection integration and exact scope

`ProjectionInflationPath` stores explicit year/rate entries, rejects nulls and
duplicates, exposes an immutable map, and throws for every missing requested year.
An empty path is permitted for a world ending at opening; it cannot cover an actual
projection year. No missing-year deterministic fallback exists.

`ProjectionEvaluationContext.withInflationPath` composes with claiming, lifetime,
survivor age and both horizon policies. Existing constructors remain source compatible.
`ProjectionEngine` validates coverage for the resolved horizon before calculation,
then accumulates one unrounded general-spending multiplier per year and passes it
through the existing annual calculation, including Medicare's existing recalculation.
Only the non-healthcare branch of expense growth consumes it. No projection logic
is duplicated in Monte Carlo, and no persisted plan field or account is mutated.

Existing expense values are opening-projection-year base amounts, not values rebased
to individual expense start dates. Opening-year inflation is recorded but does not
inflate the opening base. Subsequent years use their destination-year rate. Delayed
and one-time general expenses use the same cumulative multiplier even when inactive
in earlier years. Existing activation, partial-first-year month proration, rounding,
post-death recurring-expense adjustment and one-time exclusions remain intact.
Without a path, the old `CompoundGrowthService` call remains unchanged.

Directly affected items: every active non-healthcare recurring household expense,
including later-start recurring expenses, and non-healthcare one-time expenses
already tied to general inflation. Their changed cash needs naturally flow through
authoritative withdrawals, taxes, funding outcomes, ending assets and estate metrics.

Not overridden: healthcare spending inflation; SS COLA or claiming; mortality math,
conditioning and death mapping; pension COLAs; federal bracket/deduction growth;
Michigan rule indexation; IRMAA threshold growth; Medicare premium growth; investment
return generation; home/non-investable appreciation; RMD formulas; Roth policies;
tax formulas; estate PV assumptions; deterministic reference projections. Tax amounts,
RMD amounts and Medicare tiers can still respond normally to changed financial state.

**Healthcare inflation remains deterministic in Phase 3 V1.** Although expenses have
a clean healthcare category, the healthcare assumption also grows Medicare premiums.
Keeping both untouched avoids broadening scope or silently splitting that assumption.
Reserved dimension 5 remains unused; a later independent path can be added alongside
the general path without replacing this API.

## UI and methodology

The existing dialog adds a compact inflation selector and three inline percentage
fields in its heading. Both lifespan modes support both inflation modes. No second
chart or change to fan-chart structure, selected-year mechanics, terminal table,
funding populations or aggregation was introduced. Controls have labels, tooltips
and accessible text and are disabled during work. Every inflation edit invalidates
the session. Chart selection does not rerun work. Run metadata and Analysis Details
use immutable completed-run settings, including after controls are edited.

Analysis Details includes:

> Stochastic inflation varies annual general spending inflation independently across
> simulations. It does not change the Social Security COLA, mortality tables,
> investment return generator, or specialized inflation assumptions.

It also discloses the normal distribution, configured floor, independent reproducible
streams, unchanged percentile interpretation, and model limitations.

## Verification

All Maven runs use IntelliJ's bundled executable:
`C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd`
and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.
Java: Temurin 25.0.4.1. Dependencies and build configuration are unchanged.

- Pre-change benchmark/foundation/world suite: 20 tests, zero failures/errors/skips
  (`target/inflation-before.log`). Benchmark harness was added before production changes.
- Pre-engine-change financial fingerprint capture: one test passed
  (`target/inflation-fingerprint-before.log`).
- Initial projection integration: 5 tests passed (`target/inflation-integration.log`).
- Focused final selector `-Dtest=MonteCarlo*Test,ProjectionInflationPathTest,!*BenchmarkTest`:
  154 tests, zero failures/errors, three opt-in preview entries skipped
  (`target/inflation-focused.log`).
- New focused coverage: 9 generator/execution tests, 4 projection/path tests,
  1 financial fingerprint test and 3 UI test cases. This covers 100,000-draw normal
  moment sanity, floor behavior, exact zero volatility, stream isolation, scenario
  and coverage prefixes, immutable paths, year boundaries/missing coverage, varying
  compounded expenses, partial years, specialized separation, no mutation, exact
  second-death horizon, opening-death zero-engine branch, direct-engine annual and
  terminal oracles, funding classification, repeatability, stale/frozen UI behavior,
  accessibility and no chart-triggered rerun.
- Full non-benchmark selector `-Dtest=*Test,!*BenchmarkTest`: **1,554 tests,
  zero failures, zero errors, six opt-in skips**, BUILD SUCCESS, native exit 0,
  1:46 (`target/inflation-full-final.log`, final rerun after disclosure updates;
  prior successful run: `target/inflation-full.log`, 1:38). This includes all Phase 1/2, projection,
  mortality, tax, RMD, Roth, persistence, baseline and UI regression tests.
- Initial preview selector `-Dtest=MonteCarloPreviewTest -Dmontecarlo.preview=true`:
  **6 tests passed**, zero failures/errors/skips (`target/inflation-previews.log`).
  Includes the three existing fixed/longevity previews and three Phase 3 cases.
- Settled expansion preview selector `-Dtest=MonteCarloPreviewTest#phaseThreePreviews
  -Dmontecarlo.preview=true`: **3 tests passed**, zero failures/errors/skips
  (`target/inflation-previews-final.log`). Final wording-corrected images were
  regenerated by the same three successful preview cases in `target/inflation-ui-final.log`.
- Final affected UI selector `-Dtest=MonteCarloInflationViewTest,MonteCarloMortalityViewTest`:
  **40 tests passed**, zero failures/errors/skips (`target/inflation-ui-verified.log`).
  The preceding combined UI/preview run retained three failures in an old disclosure
  assertion requiring the obsolete text "No stochastic inflation". That assertion was
  updated to the Phase 3 disclosure; the 40-case rerun and final full suite pass.

Existing seed-417 market and mortality fingerprints pass unchanged. The prior
225-world full fixed-result fingerprints (funded and mixed-failure populations,
all percentiles and deterministic reference) pass unchanged. The settings `toString`
preserves legacy text when inflation is off, so metadata does not disturb that check.
The newly captured 25-world financial fingerprints are:

```text
fixed:     01ed3b0ad6eb20a31d0c5b0dc4736eff5e8946ffd9913fe4d5703be22fb74bab
longevity: f7bc8ef768c25b2de93e9be750ca448bdf31561bd76a6b1ff94823c805811296
```

Constant-path projection tests compare every serialized annual field with the old
deterministic projection. Zero-volatility runs match fixed and longevity outcomes
and annual aggregates exactly. Financial behavior was never changed to satisfy a test.
During development, an incorrect test type name was fixed; the first broad run caught
settings text and UI-structure assumptions. Legacy settings text and Label details
were preserved; only the UI selector-count expectation changed from two to three.
The failed development log is retained in `target/inflation-focused-initial-failures.log`.

## Performance

Same machine, seed 417, 5,000 sequential simulations per mode, same 30-year starting
plan, 30-run warmup, 4.5% mean market return, 12% volatility. Longevity uses sampled
exact second-death horizons. Inflation on: 2% mean, 1.75% volatility, -2% floor.

| Mode | Pre-Phase-3 deterministic inflation | Phase 3 stochastic inflation | Retained heap before / after |
|---|---:|---:|---:|
| Fixed lifespan | 14.772 s | 14.467 s | 5.41 / 5.42 MiB |
| Longevity-adjusted | 12.281 s | 11.469 s | 14.85 / 14.85 MiB |

These are single-run observations, not evidence of a speedup. There is no evident
end-to-end performance or retained-memory regression. Heap deltas use MemoryMXBean
after requested GC with the result kept reachable, and are approximate. Paths are
consumed per world and not retained in aggregate results. No optimization or parallel
execution was introduced. Files: `target/inflation-before.txt`,
`target/inflation-after.txt`, `target/inflation-benchmark.log` (1 test passed).
Benchmark selector: `-Dtest=MonteCarloInflationBenchmarkTest`; after-run flags:
`-Dinflation.benchmark.stochastic=true -Dinflation.benchmark.output=target/inflation-after.txt`.

## V1 limitations and Phase 3B possibilities

No inflation/market correlation, inflation persistence/autocorrelation, regime
switching or fat tails. Healthcare inflation remains deterministic and SS COLA
remains deterministic/separate. Defaults are not calibrated. Nominal outcome and
conditional annual-percentile interpretations remain unchanged. A future phase
could independently specify healthcare spending versus Medicare premium inflation,
calibrate distributions, or add a versioned correlated/persistent model; none is
implicitly enabled here.

## Final verification, previews and file boundary

The three requested 1900x1040 previews, each using 5,000 simulations, are:

- `target/inflation-fixed-deterministic.png`
- `target/inflation-fixed-stochastic.png`
- `target/inflation-longevity-stochastic.png`

Expanded details companions:

- `target/inflation-fixed-deterministic-details.png`
- `target/inflation-fixed-stochastic-details.png`
- `target/inflation-longevity-stochastic-details.png`

Additional small-sample preview: `target/inflation-longevity-stochastic-tail.png`.
Existing Phase 2 previews were regenerated too, including
`target/monte-carlo-longevity-both-survive-2070-preview.png` and
`target/monte-carlo-phase2-preview.png`.

Manual inspection of all three main layouts confirms compact inline controls,
readable labels, no clipping/overlap, preserved chart structure and selected-year
panel, unchanged terminal-table layout and visible Analysis Details. Survivor
claiming-age label and input remain intact. The existing 2070 small-sample warning
remains readable with nine living/funded households. Existing chart guide geometry,
first/middle/final navigation, late-tail values and seed-417 tail fingerprints pass.

The first expanded-details images caught the expansion animation in flight; the
preview harness now disables that animation before expanding and checks the entire
details-content bounds after scrolling. No application animation behavior changed.
Final expanded fixed/deterministic, fixed/stochastic and longevity/stochastic details
were inspected manually: all methodology text is readable and fully reachable in
the outer scroll pane. The stochastic 2070 tail also preserves the small-sample
warning, two-line selected-year panel, survivor control layout and terminal table.

`git diff --check` passed (exit 0); only standard LF-to-CRLF notices appear on stderr.
The index remains empty of changes and HEAD remains the starting revision.

Final file boundary: **22 paths** — 14 production files, seven test files, one report.
No dependencies, build configuration, CSS, persistence or financial-rule files changed.
Production ownership is listed above; tests are the benchmark, financial fingerprint,
generator/execution, projection/path, inflation UI, existing UI selector-count update,
and preview harness. The mortality presentation also updates its obsolete deterministic-only inflation disclosure. All runtime artifacts remain ignored under `target/`.

The settled expanded-details preview identified two old Phase 2 statements claiming
all inflation remained deterministic. Both fixed and longevity disclosures now state
that general spending inflation uses the selected mode, while SS COLA stays
separate. UI assertions prevent recurrence. This correction changed wording only.

Exact final `git status --short` follows (also the complete created/modified path list;
`M` means modified, `??` means newly created):

```text
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloAnalyzer.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloRandomStreams.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSettings.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloWorld.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloWorldGenerator.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEvaluationContext.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloInputs.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityPresentation.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPresentation.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPreviewTest.java
?? MONTE-CARLO-INFLATION-PHASE-3.md
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloInflationGenerator.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloInflationSettings.java
?? src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionInflationPath.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloInflationBenchmarkTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloInflationRegressionTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloInflationTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ProjectionInflationPathTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloInflationViewTest.java
```
