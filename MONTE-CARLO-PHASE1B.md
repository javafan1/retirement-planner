# Monte Carlo Phase 1B - final headless foundation

This report supersedes the Phase 1 report's failure-handling and annual-percentile limitations. Phase 1B is headless; no Monte Carlo UI or persistence fields were added.

## Structured projection and deterministic compatibility

All deterministic and structured entry points share ProjectionEngine.executeInternal. There is only one authoritative annual financial calculation path. Investment growth, income, RMDs, withdrawals, Roth conversions, taxes, Medicare, and estate ordering/rules remain unchanged.

projectWithOutcome(plan) and projectWithOutcome(plan, context, economicPath) return sealed ProjectionExecutionResult:

- Completed: immutable completed-year list, with a normal Projection available through projection().
- InsufficientFunds: immutable completed-year prefix and FundingFailure. No unfinished failure-year record, fabricated terminal balance, or completed tax total is supplied.

Known allocator sites attach the package-private typed FundingConstraint cause to their original exception classes/messages. Only structured execution captures this cause. Deterministic project callers still throw:

- IRA pool: IllegalStateException("Insufficient projected IRA balance to satisfy RMD.").
- Additional withdrawal/allocation/estimate: IllegalStateException("Insufficient projected assets to satisfy withdrawal.").
- Account-specific employer RMD: IllegalArgumentException("Withdrawal cannot exceed projected account balance.").

Diagnostic cause detail is intentionally added. Classification never matches exception text or treats arbitrary IllegalStateException as a funding failure. The employer RMD preflight repeats the existing account-balance check.

The list containers are immutable; account metadata retains ordinary ProjectionYear reference semantics. Monte Carlo reduces complete/partial projections immediately and retains scalar values rather than account references or full projections.

## Unexpected errors abort

Expected structured funding failures continue to the next scenario.

Unexpected RuntimeException during path generation, engine execution, horizon validation, or per-scenario aggregation aborts analysis. MonteCarloExecutionException preserves:

- phase: SIMULATION or DETERMINISTIC_REFERENCE;
- zero-based scenario index, absent for reference failures;
- configured seed;
- return-model identifier: LOGNORMAL_ARITHMETIC_MOMENTS_V1 or CALLER_SUPPLIED_PATHS;
- original exception type/message in the diagnostic message;
- the original exception object as getCause().

For caller-supplied paths, the configured seed is metadata, not a claim that it generated the path. Unexpected reference execution/aggregation failures abort before scenarios. Invalid initial arguments, copying/setup failures, and invalid supplied-reference horizons also propagate; no scenario index is invented before execution. JVM Errors are not caught.

No partial analysis is returned. ERROR status, error outcome records, and computationFailureCount were removed. A deterministic reference is either completed or structurally underfunded. An underfunded reference retains its scalar annual prefix and does not itself invalidate the stochastic analysis.

## Accounting and funding probabilities

Each returned result validates complete, unique zero-based scenario-index coverage. Every RunOutcome has exactly one completed metric record or funding failure.

```
completedSimulationCount + fundingFailureCount = requestedSimulationCount
fundingProbability = completedSimulationCount / requestedSimulationCount
fundingFailureProbability = fundingFailureCount / requestedSimulationCount
```

Both probabilities are always available BigDecimal values, calculated with DECIMAL128 division. They sum to one at DECIMAL128 precision. Recurring ratios are rounded independently to 34 significant digits: use DECIMAL128 addition to verify the sum, since unrestricted-precision addition can expose last-place rounding residue.

These are Funding Probability and Funding Failure Probability, not broader retirement-success probabilities. They describe execution of the full configured horizon without an unsatisfied modeled allocation request, including RMD restrictions and strategy/tax-funding requirements.

## FundingFailure fields and stages

Each failure records the first terminating calendar year, zero-based projection-year index, optional primary/spouse ages, stage, optional RMD owner, and exact required/available/shortfall BigDecimal amounts.

Ages use Person.getAge at December 31 of the failure year. Missing people/birth dates yield unavailable ages. These reporting ages do not assert modeled alive status or an intrayear failure date.

| Stage | Meaning |
|---|---|
| WITHDRAWAL_ALLOCATION | Actual additional withdrawal request cannot be allocated |
| WITHDRAWAL_ESTIMATE | Withdrawal-breakdown estimate cannot be allocated, potentially within tax/bracket iteration |
| IRA_RMD | Owner-specific eligible IRA pool cannot satisfy its request |
| ACCOUNT_RMD | Specific employer account cannot satisfy its RMD |

Required amount is the original request at that allocator invocation. Available amount is allocatable funds under its owner/account/strategy rules, or the specific account balance for employer RMDs. Shortfall is exactly required minus available.

These are **funding-constraint shortfall amounts**, not finalized annual unmet spending or a retirement-plan failure amount. An intermediate tax estimate may terminate before convergence. RMD restrictions may bind while other household wealth remains. No new financial calculation infers a different deficit or attributes the failure to Roth taxes.

## Failure-year and shortfall statistics

FundingFailureStatistics exposes:

- count by first-shortfall year;
- probabilityByYear = year count / all requested simulations;
- fractionOfFailuresByYear = year count / funding failures;
- minimum, maximum, P10/P25/P50/P75/P90 of first-shortfall year;
- minimum, maximum, P10/P25/P50/P75/P90 of funding-constraint shortfall amount.

Rates are decimal fractions. Quantiles use exact-decimal type-7 interpolation. For years [2030, 2030, 2033, 2035], median is 2031.5: a statistical coordinate, not an invented occurrence. No-failure statistics are Optional.empty.

Shortfall aggregation intentionally combines different allocator stages. Numeric aggregation is unchanged. Consumers must preserve the stage context and must not label the combined amounts as unmet household spending.

## Terminal and lifetime-tax percentiles

Ending Investable Assets, ending Net Worth, ending After-Tax Estate, and lifetime taxes include only full-horizon completed runs. Funding failures contribute no synthetic terminal values. All-failed distributions are absent.

**Lifetime-tax percentiles are conditional on simulations that successfully funded the complete projection horizon.**

MonteCarloAnalysisResult.lifetimeTaxes() aggregates ProjectionMetrics.totalTaxes: cumulative federal plus Michigan income taxes over the projection. It exposes minimum, P10/P25/P50/P75/P90, maximum, and sampleCount, using the existing type-7 convention. Taxes from failed prefixes are not substituted for lifetime totals.

## Annual percentile semantics

For calendar year Y, annual Investable Assets includes every simulation that successfully completed Y. This includes valid prefixes of simulations that later encounter a funding failure. The unfinished failure year and all later years are excluded; no post-failure values are invented. Valid completed zero balances remain included.

The result exposes annualPercentileBasis = SIMULATIONS_COMPLETING_EACH_YEAR and per-year sampleCount. This deliberately differs from terminal distributions. Later-year samples may shrink and exhibit survivor selection; this is not an unconditional post-failure wealth fan.

## Reproducibility and isolation

Immutable ProjectionEconomicPath and indexed, plan-independent V1 random streams are unchanged. Seed, scenario index, horizon, and settings determine generated paths. Requested simulation count and earlier funding failures cannot change subsequent draws. Caller-supplied common paths remain supported. Execution is sequential.

Constant-return paths, both survivor directions, exact context horizons, zero volatility, partial first years, and plan JSON non-mutation remain tested. The analyzer uses an isolated plan. No plan JSON schema, CSV export, Results PDF, or Break-Even PDF behavior was changed.

## Test user.home

Surefire in pom.xml now supplies:

```xml
<systemPropertyVariables>
    <user.home>${project.build.directory}/test-user-home</user.home>
</systemPropertyVariables>
```

The property applies only to test JVMs. The unchanged settings repository safely creates target/test-user-home/.retirement-planner through Files.createDirectories when saving. No David-specific path is configured. The direct RothConversionMainWindowTest run verified both the redirected user.home in Surefire XML and the workspace settings file.

IDE test execution should delegate to Maven or supply the same test user.home property. Normal application settings behavior is unchanged.

## Tests and final verification

Existing eight foundation tests preserve serialized constant-path equivalence, both survivor directions/context horizons, zero volatility, seeded reproducibility, indexed/common paths, partial-year cash-flow ordering, JSON non-mutation, zero-balance recovery, quantile boundaries, and generation moments.

Eight funding tests cover legacy exceptions, structured success/failure, exact year/ages/amounts, mixed 7/3 counts, annual prefixes, all-complete/all-failed runs, year distributions, tax-estimate semantics, fail-fast engine diagnostics, and IRA/employer-account constraints.

Seven added finalization tests cover:

1. Known lifetime taxes [100, 200, 300, 400, 500], every quantile, multi-year totals, and exclusion of failed-prefix taxes.
2. All-failed empty tax distribution and underfunded-reference prefix.
3. Caller path error after expected funding failure, preserved cause/index/seed/model, and stopped execution.
4. Reference failure aborting before any scenario.
5. Counts/probabilities for every split at simulation counts 1, 3, 7, 10, and 19.
6. Unavailable ages and deterministic shortfall quantiles [10, 20, 30, 40, 50].
7. Rejection of missing, duplicate, negative, and out-of-range scenario indices.

Generation guarantees contiguous indices, but the result constructor is public and externally callable, so existing validation merits these tests.

| Verification | Run | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Direct RothConversionMainWindowTest | 13 | 13 | 0 | 0 | 0 |
| Focused Monte Carlo/projection/RMD/Roth/tax/reporting/PDF selection | 344 | 344 | 0 | 0 | 0 |
| Full non-benchmark suite | 1,399 | 1,396 | 0 | 0 | 3 |

The seven new finalization tests explain the increase from the historical 1,392 tests. The three pre-existing skips remain unchanged. No deterministic expected financial values were changed. A new scalar tax fixture initially omitted its Medicare value; only that fixture was corrected before the final passing runs.

All commands used IntelliJ bundled Maven and the repository-local .codex-m2/repository. Full-suite selector: -Dtest=*,!*BenchmarkTest.

Logs under target:

- monte-carlo-1b-final-home.log
- monte-carlo-1b-final-focused.log
- monte-carlo-1b-final-full.log
- monte-carlo-1b-final-benchmark.log

## Final benchmark and memory

Sequential 30-year, five-account, two-person fixture; expected arithmetic return 4.5%, volatility 12%, seed 417, supplied deterministic reference, 20 warmups. Final benchmark follows all production changes. Prior artifacts are preserved; final output is target/monte-carlo-benchmark-1b-final.txt.

| Simulations | Phase 1 | Earlier Phase 1B | Final elapsed | Simulations/sec | Completed / funding failures | Retained heap delta | Summed pool peak |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 | 0.597 s | 0.509 s | 0.474 s | 210.90 | 100 / 0 | 0.15 MiB | 171.47 MiB |
| 1,000 | 3.185 s | 3.110 s | 3.065 s | 326.26 | 1,000 / 0 | 1.10 MiB | 441.30 MiB |
| 5,000 | 15.230 s | 14.168 s | 14.727 s | 339.52 | 5,000 / 0 | 5.41 MiB | 411.57 MiB |

Benchmark JUnit result: one test passed, zero failures/errors/skips. All 6,100 measured simulations completed; no funding failures or unexpected errors. The final 5,000-run measurement is about 3.9% slower than the earlier Phase 1B run and 3.3% faster than Phase 1. These single-run differences are consistent with host/JIT variation, not evidence of a proven speed change or a material throughput regression.

Retained heap estimates use explicit GC. Summed heap-pool peaks are approximate and do not represent simultaneous resident memory or allocation totals. Returned storage is compact per-simulation metrics/failures plus annual quantiles, not full projections. Temporary annual sample storage is O(simulations x horizon); one projection is live at a time. Lifetime-tax samples add O(completed simulations) references and sorting; tax values already exist in compact metrics.

## Exact file inventory

Production paths relative to src/main/java/com/daviddunn/retirementplanner:

- domain/projection/ProjectionEngine.java
- domain/projection/ProjectedWithdrawalAllocator.java
- domain/projection/ProjectionEconomicPath.java
- domain/projection/ProjectionExecutionResult.java
- domain/projection/FundingConstraint.java
- domain/projection/FundingFailure.java
- app/montecarlo/MonteCarloSettings.java
- app/montecarlo/MonteCarloScenarioGenerator.java
- app/montecarlo/MonteCarloPercentiles.java
- app/montecarlo/MonteCarloAnalyzer.java
- app/montecarlo/MonteCarloAnalysisResult.java
- app/montecarlo/FundingFailureStatistics.java
- app/montecarlo/MonteCarloExecutionException.java

Test paths relative to src/test/java/com/daviddunn/retirementplanner/app/montecarlo:

- MonteCarloFixtures.java
- MonteCarloFoundationTest.java
- MonteCarloFundingTest.java
- MonteCarloFinalizationTest.java
- MonteCarloBenchmarkTest.java

Also changed: pom.xml (test home only) and MONTE-CARLO-PHASE1B.md (this report). MONTE-CARLO-PHASE1.md remains the historical Phase 1 report.

Formatting was limited to Monte Carlo/structured-outcome files and edited engine/allocator sections. No unrelated pre-existing debug code was removed.

## Remaining limitations

No intrayear failure date, unfinished-year outcome, post-failure continuation, or alternative strategy rescue is modeled. Annual/terminal distributions retain their stated conditioning. Funding-constraint amounts have heterogeneous stage semantics. Year quantiles may be fractional. A supplied reference requires caller assurance of the same plan revision; only horizon is checked.

No stochastic inflation/mortality, strategy comparison workflow, parallelism, progress/cancellation, Monte Carlo UI, or Monte Carlo PDF was added. Nothing has been committed.
