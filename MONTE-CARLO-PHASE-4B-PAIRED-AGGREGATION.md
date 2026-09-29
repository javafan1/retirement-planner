# Monte Carlo Phase 4B � paired aggregation

## Starting state and scope

Starting HEAD: `29e3b58 Add paired Monte Carlo strategy comparison foundation`.
`git status --short` and `git diff --cached --name-only` both produced no output.
Phase 4A was committed, the worktree was clean, and the index was empty before changes.
No pull, reset, stash, staging, commit, or push was performed.

Headless A-vs-B reduction only. No financial formulas, projection execution logic,
generators, random-stream protocol, persistence/schema, dependencies, build configuration,
or UI files changed. No rankings, composite score, claiming grid, charts, or exports added.

## Phase 4A architecture audit

- `MonteCarloStrategyCandidate` privately freezes a complete plan through the existing
  scenario copy service and supplies one isolated working copy per side per run.
- `MonteCarloStrategyComparisonRequest` captures candidates and sealed Fixed/Longevity
  assumptions. Its indexed world source depends only on assumptions, not candidates.
- `MonteCarloComparisonWorld` holds the shared market path, lifetime scenario, and optional
  inflation path. Mortality-world adaptation retains the exact input objects.
- `MonteCarloStrategyComparisonAnalyzer` validates demographics and coverage, then executes
  A and B sequentially in each world. It reduces each execution to annual completed-prefix
  balances plus a terminal outcome or authoritative funding failure. Longevity uses exact
  second-death-minus-one endings; opening death bypasses the engine. Cancellation publishes
  no partial result; unexpected exceptions remain execution errors, not funding observations.
- `MonteCarloStrategyOutcome` copies annual balances into an unmodifiable sorted map and
  requires exactly one terminal result or funding failure.
- `MonteCarloPairedOutcome` retains scenario order identity and shared mortality, derives
  the four funding states, rejects unequal completed terminal dates, and exposes A-minus-B
  terminal differences only for both-completed worlds.
- The original comparison result was a record validating count and order. It is now a final
  immutable value class retaining the existing three-argument constructor and accessors,
  with value equality/hashCode and one cached summary. There are no record-pattern callers.

Reusable infrastructure inspected: `MonteCarloPercentiles`, `FundingFailureStatistics`,
`MonteCarloMortalityAccumulator`, `MonteCarloMortalityAnnualResult`, fixed and mortality
analysis results and terminal distributions, `BreakEvenMetricResult`, and the integrated
Social Security comparison result. None is changed. Existing break-even and SS models
have different populations and are not repurposed as Monte Carlo monetary distributions.

## Reducer and immutable results

`MonteCarloStrategyComparisonAccumulator.reduce(assumptions, outcomes)` traverses the ordered
pairs once, validates horizons and complete annual prefixes, collects terminal differences,
annual differences, state counts, and independent failures, then freezes the summaries.
It cannot access candidate plans, ProjectionEngine, world generators, or random streams.
`MonteCarloStrategyComparisonResult` invokes it once at construction and returns the same
summary on repeated access. Original outcomes remain available and unchanged.

The summary, paired-state summary, paired-metric summary, and annual result are value records.
Maps are defensively copied, sorted, and unmodifiable. No sample lists or mutable accumulator
buckets escape. Percentiles and means are calculated once. Small count-derived probability
accessors perform only bounded arithmetic, never revisit outcomes or recompute distributions.

Internal validation enforces requested/state totals, metric sample/relation totals,
per-side failure counts, annual population identities, contiguous reporting years,
contiguous side prefixes, failures within living horizons, and expected terminal dates.
Phase 4A's equal-date check remains in force. Opening-date death uses the actual opening date.

## Funding states, probabilities, and rounding

For N requested pairs:

| State | Definition |
|---|---|
| BOTH_COMPLETED | Both sides have authoritative terminal outcomes |
| A_COMPLETED_B_FAILED | A terminal outcome; B actual funding failure |
| A_FAILED_B_COMPLETED | A actual funding failure; B terminal outcome |
| BOTH_FAILED | Both sides have actual funding failures |

The four exact counts sum to N. A completed = bothCompleted + aCompletedBFailed;
B completed = bothCompleted + aFailedBCompleted. A failed = aFailedBCompleted + bothFailed;
B failed = aCompletedBFailed + bothFailed.

State probabilities use count/N with BigDecimal DECIMAL128 division. Finite decimal division
cannot exactly represent all rational probabilities: independently rounded thirds, for example,
do not sum to one. `MonteCarloPairedProbabilities` assigns the arithmetic residual to the largest
category (category order breaks ties). Zero-count categories stay zero. This explicit rounding
reconciliation meets exact partition identities without binary floating point. Exact counts are
authoritative; this new paired presentation of fractions can differ in the last decimal places
from independently dividing each existing single-strategy fraction. Existing funding statistics
and existing analyses retain their original division semantics unchanged.

A and B funding probabilities are the sums of their applicable reconciled states. Funding
probability difference is A minus B. Net asymmetric funding advantage is
P(A completes/B fails) minus P(A fails/B completes). Exact BigDecimal subtraction makes these
numerically equal; scale is not a numerical distinction. Tests cover repeating fractions and
A=0.984, B=0.979, difference=0.005. No percentage-point formatting is encoded.

## Monetary statistics and populations

All four terminal summaries have BOTH_COMPLETED as denominator:
terminal investable assets, total net worth, after-tax estate, and lifetime modeled income taxes.
Each observation is the exact nominal A-minus-B value within one paired world at its common
terminal date. Longevity worlds can have different terminal dates from each other; no common
valuation date, discounting, or inflation adjustment is introduced.

`MonteCarloPercentiles.of` supplies existing Type-7 quantiles, interpolating sorted observations
at (n-1)*p. Exposed points are Min/P10/P25/P50 (Median)/P75/P90/Max plus sample count.
No duplicate percentile implementation exists. Arithmetic means sum BigDecimals exactly,
then divide by sample count with DECIMAL128. Failed worlds never supply zero terminal values.

Explicit paired-percentile regression: A=[0,100,101], B=[0,1,100], paired differences=[0,99,1].
Median(A)-Median(B)=99, whereas Median(A-B)=1. The test verifies the latter plus every Type-7
point: 0, 0.2, 0.5, 1, 50, 79.4, 99. Mean is 100/3 under DECIMAL128.

Greater/equal/less counts compare exact differences to zero without rounding, including ties.
Their sum is sampleCount. Conditional relation probabilities use only comparable samples,
with the same residual reconciliation. No observations means zero counts and absent percentiles,
mean, and all three probabilities. No NaN, infinity, invented zero probabilities, or division by zero.

Tax subtraction is also A minus B: positive means A paid more modeled income tax; negative means
A paid less. The model encodes no preference direction or composite result.

## Annual and mortality semantics

Annual reporting spans the start year through the fixed common end, or the latest sampled
final living year for longevity. All-opening-death results have an empty annual map. There is
no configured-horizon cap on sampled longevity years.

Shared mortality gives identical living/applicable populations for both strategies. Each row
exposes requested, living, deceased, both-comparable, A-completed/B-failed, A-failed/B-completed,
and both-failed counts; per-side available counts are derived. Requested = living + deceased;
living = comparable + the three failure availability categories. Thus comparable <= living <= requested.

Each comparable observation is A-minus-B annual investable assets. A completed prefix remains
eligible even when that side fails later. A failure in or before the year makes that side unavailable.
Post-second-death years are deceased observations, never failures, even if funding failed earlier.
No missing balance is imputed. Only annual investable assets are aggregated.

## Failure detail

Independent A and B `FundingFailureStatistics.from` calls reuse the existing failure-year counts,
all-requested year probabilities, failure-conditional fractions, first-shortfall-year Type-7
percentiles, and shortfall-amount Type-7 percentiles. No failure means Optional.empty(). The existing
summary does not aggregate stage/owner/requested/available dimensions. Those exact original
`FundingFailure` fields remain available on each side's retained paired outcome, unchanged.
No new taxonomy or cross-side monetary interpretation is introduced.

## Test evidence

Identical strategies are checked in fixed and longevity modes with stochastic inflation and
seed 417: matching per-world outcomes, no asymmetric states, equal funding probabilities,
zero funding difference, exact-zero terminal/annual percentile points and means, and all
both-completed relations classified equal with conditional probability one.

Controlled expense case: zero market return and deterministic inflation, 2027�2029,
A spends 100/year and B spends 150/year from equal 10000 balances. Independent engine oracles
validate both sides; terminal paired asset delta is +150 for every world, with probability greater=1.
Annual means also match subtraction of engine-produced annual balances.

Controlled SS case: valid primary retirement elections differ while both engine invocations
receive identical market path, inflation path, and mortality objects. Both sides are checked
against independent engine oracles under the same exact death horizon and survivor election.
The nonzero summary difference equals the authoritative paired difference; repeated reduction
adds no engine calls.

Controlled tax case: A has an additional taxable pension. Both sides match engine oracles;
A-minus-B lifetime modeled tax is positive and classified greater, never sign-reversed.

Funding tests use a readable four-state synthetic set plus engine-produced complete/failing
outcomes and their reversals. Counts, independent failure summaries, and annual asymmetric
availability reconcile. Engine-produced state aggregation intentionally tests the reducer with
known observations; it is not represented as a new stochastic execution protocol.

Edges cover zero/one/all both-completed, all both-failed, either all-asymmetric direction,
ties, mixed greater/equal/less, opening second death, year 2149 final living observations,
zero/one annual comparable samples, 1E1000 magnitudes, 1E-1000 unrounded signs,
negative and zero differences, invalid prefixes/horizons/populations, defensive collection
copies, unmodifiable results, cached identity, and repeated value-equal reduction.

Initial focused run found one test-only scale-sensitive zero comparison (0 versus 0E-34).
It was corrected to BigDecimal numerical comparison; no calculation was changed to satisfy it.

## Verification commands and results

All Maven commands use IntelliJ's bundled Maven at:
`C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd`
and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.

Final counts, benchmark results, and the exact Git boundary are recorded below.

## Performance and future Phase 4C

Reduction stores four O(worlds) terminal difference lists and O(worlds � years) annual difference
samples temporarily, because pairs are consumed in one traversal. Sorting uses the existing
percentile helper's temporary O(worlds) reference copy per distribution. Only differences are
new BigDecimals; underlying A/B balances are not duplicated. Failure lists retain references.
Final retained summary size is O(reporting years + distinct failure years), independent of world
count, and includes no raw sample arrays or projections. Exact retained-byte estimates from
heap-before/after-GC measurements are approximate and can be noisy.

The paired metric factory is independent of execution and can be reused inside a future
analyzer without introducing N-way comparison infrastructure now.

Phase 4C recommendations: display funding reliability separately from conditional monetary
magnitude; show both-completed denominators prominently; label nominal A-minus-B values and
higher-tax direction explicitly; show annual changing populations and deceased/failure counts;
display missing populations as unavailable; preserve ties. Show actual terminal-date context
for longevity and avoid implying a common valuation date. Do not infer a recommendation from
these summaries. No UI implementation is included here.

## Measured 5,000-world performance

Existing `MonteCarloPairedBenchmarkTest` was rerun after extending it to capture its paired
result and separately time reduction of those already-produced outcomes. Reduction does not
rerun projections. Warmups use 30 worlds; seed 417, 12% market volatility, stochastic inflation,
and the existing Phase 4A household are unchanged.

| Mode | Single execution | Paired execution including cached summary | Standalone reduction | Approx. retained summary | Annual rows |
|---|---:|---:|---:|---:|---:|
| Fixed | 14.490 s | 29.539 s | 0.017136 s | 0.0077 MiB | 30 |
| Longevity | 11.905 s | 22.722 s | 0.015292 s | 0.0109 MiB | 45 |

Paired retained results measured 32.76 MiB fixed and 29.58 MiB longevity. The separate summary
heap deltas are approximate GC measurements for this identical-strategy fixture, not guarantees
for arbitrary values. Standalone reduction was about 0.06�0.07% of paired execution time.
Compared with recorded Phase 4A paired timings of 30.436 s / 23.908 s, no material execution
slowdown was observed. These are single-run observations, not statistical performance bounds.
Benchmark: 1 test passed, 0 failures/errors/skips, 80.93 s test time.
Logs: `target/phase4b-benchmark.log`, `target/phase4a-benchmark.txt` (existing benchmark output),
and `target/phase4b-reduction-benchmark.txt`.

## Final verification results

- New Phase 4B focused tests: **18 passed**, 0 failures/errors/skips, using
  `-Dtest=MonteCarloPairedAggregationTest,MonteCarloPairedAggregationIntegrationTest test`.
  Log: `target/phase4b-focused-final.log`.
- Phase 4A regression tests: **28 passed** (13 StrategyComparison + 15 PairedWorld),
  also included in the final full suite. Original execution tests were not modified.
- Dedicated Monte Carlo group: **195 tests**, 0 failures/errors, 3 opt-in preview skips,
  using `-Dtest=MonteCarlo*Test,!*BenchmarkTest test`. This preceded the final additional
  second-death/failure test; that new test passed focused and in the final full suite.
  Log: `target/phase4b-montecarlo.log`.
- Final full non-benchmark suite: **1,600 tests, 0 failures, 0 errors, 6 skipped**,
  BUILD SUCCESS, 2:00 minutes, using `-Dtest=*Test,!*BenchmarkTest test`.
  Log: `target/phase4b-full-final.log`. The six skips are two opt-in personal-plan audits,
  three opt-in Monte Carlo previews, and one explicitly disabled multi-minute production
  mortality performance test. No unrelated intermittent failure occurred.
- Paired benchmark: **1 passed**, with execution and separately measured reduction as above,
  using `-Dtest=MonteCarloPairedBenchmarkTest test`.

The full suite includes projection regressions, mortality, stochastic inflation, funding-failure,
Social Security integration/survivor, persistence round-trip, and UI/fan-chart tests. Seed-417
checks passed in `MonteCarloFoundationTest`, `MonteCarloWorldGeneratorTest`,
`MonteCarloPairedWorldTest`, `MonteCarloRandomStreamsTest`, and
`MonteCarloInflationRegressionTest`; existing mortality/fixed result fingerprints also passed.
Existing small-sample threshold tests in `MonteCarloMortalityViewTest` passed unchanged.
No deterministic financial, fixed Monte Carlo, longevity Monte Carlo, market/mortality/inflation
stream, funding-failure, survivor, fan-chart, small-sample warning, or persisted-schema behavior
was altered. Production file inspection independently confirms that those implementations
are outside the final diff.

## Final review boundary

`git diff --check`: passed. All 11 changed/new files also checked with
`git -c core.autocrlf=false diff --no-index --check -- /dev/null <file>` to include untracked
content: no whitespace diagnostics. The no-index difference status for new content is expected.
HEAD remains `29e3b58`. `git diff --cached --name-only` is empty.

Exact boundary: **11 files: 7 production (6 new, 1 modified), 3 tests (2 new, 1 modified),
1 new report**. No deletions. No UI files. No files staged, committed, or pushed.

Production changes are the cached result, reducer, overall summary, paired state summary,
metric summary, annual result, and shared probability-reconciliation helper. Test changes are
the two focused aggregation test classes and the existing paired benchmark instrumentation.

Exact final `git status --short`:

```text
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonResult.java
 M src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPairedBenchmarkTest.java
?? MONTE-CARLO-PHASE-4B-PAIRED-AGGREGATION.md
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPairedAnnualResult.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPairedMetricSummary.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPairedProbabilities.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPairedStateSummary.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonAccumulator.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonSummary.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPairedAggregationIntegrationTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPairedAggregationTest.java
```
