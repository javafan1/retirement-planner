# Monte Carlo Phase 1 — annual-return foundation

## Architecture audit

`EconomicAssumptions.expectedAnnualInvestmentReturn` is persisted. `PlanningAssumptions.getExpectedAnnualInvestmentReturn()` delegates to it. The assumption is used as a constant nominal annual compound return, not a stochastic calibration or explicitly defined arithmetic/geometric expectation. No volatility or asset-class covariance assumptions exist.

Before this change, the sole investment-return read in `ProjectionEngine` was in `calculateInvestmentGrowth`: beginning investable assets multiplied by the plan return. The first projection year's growth is prorated by inclusive active calendar months / 12 and rounded HALF_UP to cents; every year's total growth is rounded to cents. The annual calculation may run a second time to include authoritative Medicare premiums.

All investable account types share this return. Account growth is allocated proportionally using existing 12-decimal shares. Projection-generated retained non-qualified assets receive the same return with their established allocation/rounding. There is no asset-class return model. Non-investable assets have their own deterministic annual growth rates in `NonInvestableAssetProjectionService`; these are not randomized.

Annual order remains: opening balances and projected rules; growth calculation; guaranteed income/expenses; prior-December-31 RMD (opening RMD special handling in year one); apply growth; distribute RMDs; allocate additional spending withdrawal; determine and execute owner-specific Roth transfers; calculate/fund income taxes; authoritative Medicare rerun if necessary; settle and retain surplus; estate values; ending account snapshots. Growth is applied before withdrawals/conversions, and these affect the following year's opening balances. A return path does not alter any of this ordering.

Ordinary callers use `ProjectionEngine.project(plan)` or `(plan, ProjectionEvaluationContext)`. `RetirementPlan` and its accounts are mutable. Each engine run builds a separate `ProjectedPortfolio`; it does not write actual account balances. `Projection` permits adding years; `getYears()` defensively copies the list. Annual results retain account metadata references, so retaining arbitrary complete projections would not be a sufficient isolation strategy.

The analyzer copies the plan once through the existing `RetirementPlanScenarioCopyService`, then sequentially reuses that isolated plan and one engine. Government JSON is loaded at engine construction, not inside the simulation loop. There are no JavaFX calls or mortality loads in this path. Social Security schedules and projected government rules are still rebuilt per run/year. Tax-funding feedback, Medicare's second annual pass and bracket-fill conversions can be expensive. No new static/global random state or caches were introduced. This API is sequential, not advertised as thread-safe, and callers must not concurrently edit the source while the initial copy is being made.

## Integration and files

Only existing production class changed in this phase:

- `domain/projection/ProjectionEngine.java`: 23 added / 6 removed lines, adding an overload and passing one applicable return through the existing annual calculation and Medicare rerun.

New production classes:

- `domain/projection/ProjectionEconomicPath.java`: immutable constant or calendar-year map; required coverage validation; annual map values cannot be below -100%; no persistence.
- `app/montecarlo/MonteCarloSettings.java`: count, seed, explicit arithmetic expected return and explicit return standard deviation. `forPlan` defaults mean to the existing plan return. Counts 1–100,000 are accepted; callers should budget count × horizon memory.
- `MonteCarloScenarioGenerator.java`: V1 reproducible indexed scenario generation and injectable Gaussian source.
- `MonteCarloPercentiles.java`: empirical exact-decimal type-7 interpolation.
- `MonteCarloAnalysisResult.java`: immutable compact outcomes, conditional distributions, annual percentiles, observed zero-balance statistics and deterministic reference.
- `MonteCarloAnalyzer.java`: isolated sequential execution of the authoritative engine, reduction and aggregation.

New tests: `app/montecarlo/MonteCarloFixtures.java`, `MonteCarloFoundationTest.java`, `MonteCarloBenchmarkTest.java`.

The existing two engine entry points now supply a constant path equal to the plan assumption. No plan model/JSON fields, existing test expected values, UI, CSV or PDF code changed in this phase. Existing context overrides, including exact horizons and death/claiming strategies, continue to compose with the new three-argument engine overload.

## Return distribution and semantics

V1 uses independent annual **lognormal gross returns**, moment-matched to the requested annual simple-return arithmetic mean `m` and standard deviation `s`:

```
sigmaLog² = log(1 + s² / (1 + m)²)
muLog = log(1 + m) - sigmaLog² / 2
R = exp(muLog + sigmaLog * Z) - 1; Z ~ N(0,1)
```

This preserves the requested arithmetic moments without clipping normal returns below -100%. Mean must exceed -100%; volatility must be nonnegative; inputs and generated values must be finite. Extremely negative log returns may numerically become exactly -100%, but never less. Non-finite draws/overflow are explicit failures, not silently clipped. Zero volatility bypasses random sampling and uses the exact original BigDecimal mean.

`StrictMath` and double are used only in stochastic generation. `BigDecimal.valueOf(rate)` is the explicit boundary to the financial engine; no engine monetary arithmetic became floating-point. Generated rates are full-year nominal rates; first-year proration stays in the engine.

Volatility is required rather than given a permanent implicit default. The development fixture uses **12% annual standard deviation** as an explicit test/benchmark setting, not a calibrated portfolio assumption or recommendation.

**Volatility drag:** a 4.5% arithmetic mean with 12% volatility does not have a 4.5% median compound growth path. The model's log-growth-equivalent rate is about 3.82% before cash flows. Retirement results additionally depend on cash flows, taxes, conversions and return sequence. Existing deterministic 4.5% is not changed or automatically converted. Future UI labels should say “Expected annual nominal return (arithmetic mean)” and “Annual return volatility (standard deviation)”, with an explanation that median compound growth differs from arithmetic mean.

## Reproducibility and common random numbers

Model identifier: `LOGNORMAL_ARITHMETIC_MOMENTS_V1`.

Scenario index `i` initializes `java.util.Random` with `seed + 0x9E3779B97F4A7C15L * i` using intentional long wraparound. Annual Gaussian draws occur in increasing calendar-year order. The seed/index/horizon/settings define the path independently of mutable plan contents, iteration order, previous scenario errors and requested count. Different counts preserve the same indexed scenario. Identical horizons and settings can regenerate or reuse the same immutable paths across strategies.

The analyzer also accepts an indexed immutable-path provider for caller-owned common scenarios. Such runs are labeled `CALLER_SUPPLIED_PATHS`, rather than falsely claiming the built-in generator produced them. For horizons with different start years, callers should explicitly reuse a shared calendar-year path; the V1 sequential stream is anchored at the supplied first year.

## Funding-success limitation — deliberate, not a success metric

`Projection.depletedPortfolio()` checks only final investable assets. It does not establish whether required spending was funded throughout the horizon. The withdrawal allocator throws generic `IllegalStateException` on insufficient assets/RMD capacity; tax funding can also throw for convergence/other problems. The failed projection does not return its partial years or a typed, year-tagged unfunded amount. Negative account balances are not a supported continuation model.

Consequently **no plan-success probability is reported**. Every requested index is retained as either completed metrics or an explicit exception-type/message record. Errors are not automatically classified as insolvency; no ending assets, taxes or failure year are manufactured for them. A failed deterministic reference is likewise explicitly represented.

Available balance observations are narrowly named:

- first year-end with reported investable assets <= 0, among completed runs;
- count/probability of such observations among completed runs;
- probability balances remain strictly positive at every year-end, among completed runs;
- earliest/median/latest first observed nonpositive year (minimum/P50/maximum of the first-year distribution).

A temporary zero followed by later income/retained cash is an observation, not permanent exhaustion or a failed plan. Intrayear exhaustion and unfunded-spending dates cannot be inferred from these annual observations.

**All annual and ending percentiles are conditional on complete projections.** Each distribution exposes sampleCount; result exposes completed and computation-failure counts. With zero complete paths, distributions and probabilities are empty, not zero. In the presence of errors these distributions must not be presented as unconditional Monte Carlo outcomes; excluding failed paths may bias them upward. Partial years from aborted runs are unavailable and are not included.

Before a funding-probability UI, recommend a separately reviewed, small engine outcome contract: typed insufficient-funds vs computational failures, failing calendar year, unfunded requirement, and completed annual prefix. Preserve default deterministic exceptions/values. Define any post-exhaustion continuation explicitly rather than assuming zero wealth or permitting debt. This limitation was reported during the audit; Phase 1 does not invent those semantics.

## Collected outputs

Every completed run uses existing `ProjectionMetricsCalculator`:

- ending investable assets;
- ending net worth (investable assets + independently projected non-investable assets);
- after-tax investable estate (the existing metric excludes non-investable assets);
- lifetime federal + Michigan income taxes;
- existing compact totals for growth, guaranteed income, withdrawals, Roth conversions, RMDs, Medicare and Social Security.

Annual investable assets come directly from `ProjectionYear.getEndingInvestableAssets()`. No balance is reconstructed. Metrics are unweighted by mortality. Inflation, COLA, pensions, death years, tax assumptions and non-investable growth assumptions are unchanged; resulting taxes/withdrawals/estate can naturally vary with returns through the existing engine.

A caller may supply a valid deterministic Projection for the exact same plan revision; its full horizon is validated and it is reduced to immutable annual asset values and metrics without rerunning it. Bare Projection has no revision provenance, so exact-plan validity is the caller's documented responsibility. If absent, one normal projection is run, never one per simulation.

## Percentiles

Type-7 empirical interpolation: sort unrounded observations; index `(n - 1) * p`; linearly interpolate adjacent values using BigDecimal. Report P10/P25/P50/P75/P90, minimum, maximum and sampleCount. A singleton returns its value at every percentile; no samples return Optional.empty. No display rounding enters statistics.

## Memory and performance

One plan copy and one complete stochastic projection are live at a time. During aggregation, annual asset samples require O(simulations × years) BigDecimals; after percentiles are formed those samples are released. Returned data retain O(simulations) compact outcomes plus O(years × percentile count), not thousands of complete projections or generated paths.

Representative benchmark: 2027–2056, two modern-cohort people, Social Security, escalating pension, five accounts (joint brokerage, two owner-specific Traditional IRAs and two Roth IRAs), fixed $75,000 conversions through first household RMD, federal/Michigan taxes, $80,000.01 expenses, $500,000 home equity. Opening investable assets $5 million. Mean 4.5%, volatility 12%, seed 417. One supplied reference and 20 warmup simulations. Sequential Java 25 execution, no parallelism:

| Simulations | Elapsed | Simulations/sec | Completed / errors | Approx retained heap delta | Sum of heap-pool peaks |
|---:|---:|---:|---:|---:|---:|
| 100 | 0.597 s | 167.60 | 100 / 0 | 0.15 MiB | 175.43 MiB |
| 1,000 | 3.185 s | 313.96 | 1,000 / 0 | 1.10 MiB | 473.40 MiB |
| 5,000 | 15.230 s | 328.29 | 5,000 / 0 | 5.41 MiB | 451.84 MiB |

Measurements use MemoryMXBean after explicit GC; peak sums are approximate JVM heap-pool figures, not simultaneous resident memory or allocation totals. Expect JIT/GC/host variation. For 10,000 comparable runs, estimate about 11 MiB retained results plus roughly 15–30 MiB temporary annual-sample storage. Engine temporary allocations can still drive heap use into hundreds of MiB; do not size heap solely from final result size. Projected elapsed time at the 5,000-run rate is about 30.5 seconds, not a measured 10,000-run result. Bracket-fill strategies, larger portfolios and different horizons may be slower.

No JSON round-trip inside the simulation loop; no repeated engine construction/rule parsing; no UI conversion or mortality loading. Per-run SS schedule preparation, annual rule construction, Medicare reruns, immutable portfolio allocations and tax/bracket iteration remain optimization candidates only after profiling. No optimization changed financial behavior.

## Validation

Eight foundation tests cover:

- constant-path equality of every serialized annual result and unchanged plan JSON;
- constant paths with both survivor directions and exact ending-year context;
- +10%, -10%, +5%, 0% paths across a partial first year, growth, Roth, RMD and tax funding;
- reproducible paths and complete aggregate equality, different seed/index, count-independent scenario identity;
- zero-volatility collapse across all annual percentiles and ending metrics, and exact engine execution counts when reference supplied;
- explicit failures, conditional sample counts and empty all-failed distributions;
- temporary year-end zero followed by recovery, without calling it plan failure;
- percentile boundaries, singleton/empty/negative values, immutable maps and missing-year validation;
- seeded 100,000-return moment checks, impossible-return boundary and non-finite source rejection.

Focused projection/Roth/RMD/reporting/Monte Carlo run: **326 tests, zero failures/errors/skips**. No existing deterministic expected values updated. Benchmark test passes separately and is excluded from the non-benchmark suite.

Full non-benchmark suite: **1,384 tests, zero failures/errors, three skipped; BUILD SUCCESS**, 1 minute 26 seconds. Existing investment-growth, opening-RMD, multi-owner Roth, Social Security/survivor, deterministic/longevity analyzer, persistence, CSV, Results and Break-Even PDF tests passed unchanged. No deterministic regression discovered.

Logs: `target/monte-carlo-focused.log`, `target/monte-carlo-regression-focused.log`, `target/monte-carlo-benchmark.log`, `target/monte-carlo-benchmark.txt`, `target/monte-carlo-full.log`.

## Next phases / limitations

First address the structured funding-outcome contract above before presenting success/exhaustion probabilities. Extend the immutable economic path with named annual inflation/COLA inputs only when each engine consumer has an explicitly tested binding; add correlated generation outside the engine. Keep mortality scenarios separate and reuse existing mortality infrastructure/context, not an independent model. Future strategy comparison should freeze plan revisions and share indexed economic paths. Add cancellation/progress/admission budgets before a long-running UI. None of these future capabilities, menu items or persistence fields are introduced now.

No commits made.