# Monte Carlo Phase 4A: paired comparison foundation

## Starting state and boundary

Starting HEAD: `8249c503d25d4baecdfe8579221a49898383e63f`
(`8249c50 Add stochastic inflation to Monte Carlo analysis`). Phase 3 is committed.
Initial `git status --short` produced no output: both worktree and index were clean.
No pull, reset, stash, staging, commit, push, or dependency/build changes were made.

This phase adds a headless two-strategy execution API. There are no changes to
existing production files, financial formulas, RNGs, UI, persistence, or existing
single-strategy APIs. No aggregation, ranking, search, heat map, or strategy
enumeration was added.

## Read-only architecture audit and choice

The audit preceded implementation and covered the following existing paths.

| Area | Finding |
|---|---|
| Fixed entry point | `MonteCarloAnalyzer.analyze` / `analyzeWithReferenceOutcome`: one deep copy per analysis, configured horizon and death assumptions, indexed `MonteCarloScenarioGenerator`, one deterministic reference, immediate reduction of each simulation. |
| Longevity entry point | `MonteCarloAnalyzer.analyzeMortality`: one deep copy, mortality preparation validation, indexed `MonteCarloWorldGenerator`, exact second-death-minus-one horizon, opening-date estate special case. |
| Inflation | Fixed execution invokes `MonteCarloInflationGenerator` by index; mortality world generation owns the optional `ProjectionInflationPath`. Engine receives the path through `ProjectionEvaluationContext`. |
| World ownership | Immutable `ProjectionEconomicPath` owns market inputs; `HouseholdLifetimeScenario` owns annual death timing; immutable `ProjectionInflationPath` owns spending inflation. `MonteCarloWorld` combines these but requires both deaths, so it cannot represent existing fixed-mode survival scenarios. |
| RNG ownership | `MonteCarloScenarioGenerator` retains legacy market generation. `MonteCarloRandomStreams` owns the established dimension-separated protocol for mortality and inflation. No changes were needed. |
| Fixed results | `MonteCarloAnalysisResult.RunOutcome` retains metrics or structured funding failure; annual observations are reduced into year distributions. This is insufficient for retaining each paired failed prefix. |
| Mortality results | `MonteCarloMortalityAnalysisResult.WorldOutcome` retains lifetime, annual investable observations, optional terminal metrics, and independent failure. `TerminalOutcome` already has all four requested terminal metrics plus date. |
| Failure ownership | `ProjectionExecutionResult.InsufficientFunds` owns completed projection rows and `FundingFailure`. Unexpected exceptions are separate from financial failures. |
| Plan isolation | `RetirementPlanScenarioCopyService` performs a complete Jackson/Java-time round trip. Existing analyzers copy once per run, not per world. UI session capture adds its own earlier snapshot. Engine constructs `ProjectedPortfolio` and does not mutate real account balances. New observer tests verify complete plan JSON remains unchanged across executions. |
| Baselines | Factory-created `RetirementPlanSnapshot` now deep-copies (more recent than the caution in AGENTS), but getters still expose mutable domain objects. Baseline projection and comparison operate on deterministic projections, not shared stochastic worlds. |
| Break-Even | Uses captured projection snapshots and plan summaries; its context factory reuses authoritative joint mortality for survival annotations, not paired financial execution. |
| Integrated SS | Evaluator deep-copies a plan and supplies a claiming override to the same engine. Candidate/override types are specific to Social Security. They are not a general retirement-strategy input. |

Chosen representation: **two complete privately frozen plans**, each with a stable
display label (`MonteCarloStrategyCandidate`). This supports expense, Roth and
Social Security changes without introducing a second strategy language. Nominal
plan horizons do not determine comparison exposure. A sealed assumptions value
composes either `Fixed` or `Longevity` inputs without nullable mode fields.

The private candidate capture uses the existing copy service. The request exposes
no mutable plan. Each run obtains exactly one further isolated working copy per
side; those two copies are reused for every world. This mirrors existing
session-capture plus analyzer-copy behavior. No per-world copying is performed.

`MonteCarloComparisonWorld` is a small adapter, rather than a relaxation of the
existing mortality-world validation. Its `from(MonteCarloWorld)` factory retains
the original path and lifetime object references. It also supports fixed-mode
survival/one-death timing without fictitious sampled deaths.

## Execution and horizon contract

Public entry points are `MonteCarloStrategyComparisonAnalyzer.analyzeComparison`:
request only, request with progress/cancellation, and request with an indexed
`IntFunction<MonteCarloComparisonWorld>` plus progress/cancellation.
`request.worldSource()` can be retained and reused across later comparisons.

Execution is sequential:

```text
capture private candidates -> copy A once, copy B once -> validate inputs
for index i:
    world = worlds.apply(i)                // exactly one call
    validate index, common timing and path coverage before either side
    A = evaluate(workingPlanA, world)
    B = evaluate(workingPlanB, world)       // same world, same path objects
    retain compact pair(i, lifetime, A, B)
```

Generation reads only request assumptions, never a candidate. Evaluation has no
RNG or world-generator access. The same architecture leaves future dimensions
owned by the world; a future dimension must be added to the adapter/context once,
not generated separately per strategy.

**Fixed horizon:** the request explicitly owns start date, inclusive ending year,
and configured lifetime timing. Each plan must match the start and death timing;
both must describe the same household demographics. Different nominal plan
lengths are permitted and ignored for horizon selection. Both evaluations use
`empty().withExactEndingYear(request.endingYear())`, keeping the existing
configured-death engine path rather than enabling mortality execution. The world
source generates exactly the request's common interval. Fixed timing supports
the existing both-survive/one-death model; it does not invent a two-death fixed mode.

**Longevity horizon:** the existing mortality request freezes birth dates,
categories, table, adjustments and projection-start conditioning. Both candidates
must match these inputs and support the existing advanced two-person SS path.
Every world ends at `max(primaryDeathYear, spouseDeathYear) - 1`, independent of
either plan's nominal length. Evaluation uses the shared lifetime object and
`withExactEndingYear`. Each candidate's persisted survivor claiming age supplies
its own `withSurvivorClaimingAge`; the generation request's optional shared
survivor age is deliberately not used as a strategy election. Each candidate
must contain that election, including when its configured scenario is both-survive.
The request captures those elections through its frozen candidates.

Opening-date second death uses the existing `EstateAtSecondDeathCalculator`,
opening non-investable value, and zero modeled taxes, with zero engine calls.
Earlier second death is rejected. Partial first financial years retain engine
proration. Complete projection dates and contiguous prefixes are checked.

**Inflation:** the optional world path is passed by reference to both contexts.
No resampling, independent RNG, or inflation copy occurs. When absent, each plan
retains the authoritative deterministic assumptions. Caller-supplied worlds are
explicitly marked; the caller must retain its source for reproduction.

## Compact results, deltas and execution semantics

`MonteCarloStrategyComparisonResult` retains the request, generated/caller-supplied
source marker, requested count and immutable ordered pairs. Each pair retains
scenario index and shared lifetime timing, but no market/inflation path.
Each `MonteCarloStrategyOutcome` retains an immutable map of completed annual
investable observations and exactly one of terminal result or `FundingFailure`.
The existing `MonteCarloMortalityAnalysisResult.TerminalOutcome` is reused for:

- balance date;
- terminal investable assets;
- terminal total net worth;
- terminal after-tax estate;
- total modeled lifetime income taxes.

A separate neutral compact carrier is necessary because the existing mortality
`WorldOutcome` embeds mandatory two-person death years in each side. Existing
single-strategy result types were left source-compatible. No full `Projection`,
`ProjectionExecutionResult`, projection rows, or duplicated paths are retained.

Derived status preserves all four states: `BOTH_COMPLETED`,
`A_COMPLETED_B_FAILED`, `A_FAILED_B_COMPLETED`, `BOTH_FAILED`. Both actual failures
remain independent. Failed sides have no fabricated terminal balances or taxes.

`deltas()` returns optional nominal A-minus-B investable assets, net worth,
after-tax estate and lifetime taxes only when both complete. Constructing a pair
with unequal successful terminal dates is rejected. There is no success-versus-
failure ordering, probability, distribution, score, or reducer.

Unexpected runtime errors abort with zero-based scenario index, `WORLD`,
`STRATEGY_A` or `STRATEGY_B`, seed and original cause. World generation/validation
errors occur before either evaluation; A errors prevent B execution. Financial
funding failures remain ordinary observations. Preparation validation errors
abort before simulations. Cancellation preserves `AnalysisCancelledException`.

Progress uses existing `MONTE_CARLO_SIMULATIONS` counts from 0 through N, with at
most approximately 100 updates, one unit per completed pair. Cancellation is
checked before generation, before A, between A/B, after B and before result
publication. Cancellation between sides publishes no partial pair/result and
does not advance completed-world progress. No parallel execution was introduced.

## Verification

Verification commands use IntelliJ bundled Maven and always include
`-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.

- Focused selector: `-Dtest=MonteCarloStrategyComparisonTest,MonteCarloPairedWorldTest`.
  Final run: **28 tests, zero failures/errors/skips** (13 financial/isolation,
  15 world/execution tests, including parameterized invocations).
- Broader selector: `-Dtest=*MonteCarlo*Test,*Projection*Test,*Mortality*Test,*Inflation*Test,*IntegratedSocialSecurity*Test,*Funding*Test,!*Benchmark*`.
  **432 tests, zero failures/errors, 4 skipped**, before four additional focused
  boundary tests and the combined-world fingerprint test. Those additional tests
  passed in the final focused run.
- Full non-benchmark selector: `-Dtest=*Test,!*BenchmarkTest`.
  **1,582 tests, zero failures/errors, 6 skipped (1,576 executed successfully)**,
  Maven BUILD SUCCESS, 1 minute 49 seconds. The six skips are the existing two
  opt-in saved-plan diagnostic tests, three opt-in Monte Carlo previews, and one
  explicitly disabled multi-minute production mortality benchmark.
- Benchmark: **1 test, zero failures/errors/skips**, 5,000-world single and paired
  runs in both modes, plus warmups.

Final full-suite coverage includes these overlapping groups (not additive):

| Group | Tests | Skipped | Failures/errors |
|---|---:|---:|---:|
| Monte Carlo domain/application/UI | 178 | 3 | 0 |
| Domain projection package | 199 | 0 | 0 |
| Mortality-named suites | 120 | 1 | 0 |
| Inflation-named suites | 17 | 0 | 0 |
| IntegratedSocialSecurity-named suites | 35 | 0 | 0 |
| Funding-named suites | 11 | 0 | 0 |

Logs: `target/phase4a-focused.log`, `target/phase4a-regression.log`,
`target/phase4a-full.log`. Full-suite results include all final focused additions.
There were no intermittent or unrelated test failures to isolate or rerun.

New tests explicitly establish:

1. Exactly one world-source invocation per index, two sequential engine calls.
2. Reference identity of market/inflation inputs on both sides; lifetime reference
   identity in longevity execution and validated identical configured timing in fixed mode.
3. Changing either candidate leaves all world dimensions and the other outcome unchanged.
4. Count expansion preserves result and world prefixes; direct, sequential and
   reverse indexed generation agree through index 224 in both modes.
5. Longer fixed coverage preserves every existing market and inflation prefix value.
6. Identical strategies produce exactly equal compact outcomes and zero for all
   completed deltas; funding states also match.
7. A-then-B versus B-then-A preserves each labeled strategy's outcome in both modes.
8. Re-executing the same immutable request produces value-equal results.
9. Both original plans and reused working plans remain unchanged; source edits
   after capture cannot change the request's result.
10. A known $50 annual extra expense produces a $150 three-year difference at
    zero return/inflation, verified against direct engine results.
11. SS claiming changes use authoritative persisted elections and direct-engine oracles.
12. Both funding asymmetries and distinct both-failed dates retain real failures,
    completed prefixes and absent terminal deltas.
13. Known longevity and survivor elections, opening death, partial first year,
    unequal nominal horizons and fixed configured deaths match existing behavior.
14. Cancellation, world errors, A/B errors, malformed coverage/index, invalid
    demographics/elections and mismatched terminal dates are handled explicitly.

Existing regression coverage includes seed-417 market fingerprints, random-stream
vectors, mortality mapping and order/count invariants, inflation generation and
isolation, fan-chart models, selected-year small samples, deterministic projections,
and financial outcomes. The seed-417 pre-inflation financial fingerprint tests still
match `01ed3b0ad6eb20a31d0c5b0dc4736eff5e8946ffd9913fe4d5703be22fb74bab`
(fixed) and `f7bc8ef768c25b2de93e9be750ca448bdf31561bd76a6b1ff94823c805811296`
(longevity). Existing fully funded/mixed fixed-result fingerprints also remain
unchanged. No reference fingerprints or existing tests were edited.

The new combined-world SHA-256 test fixes canonical market maps, lifetime timing,
and inflation maps at indexes 0, 1, and 224. Values were captured with all four
generation sources unchanged from starting HEAD (verified by `git diff HEAD --`
on scenario/world/inflation generators and random streams). The test additionally
checks the comparison adapter against those original generator APIs. All six pass:

| Mode/index | Combined world fingerprint |
|---|---|
| Fixed 0 | `ce5a5aaf18e7184d57d6f758b5d752804902dc239a67bddbae9d0661c57f2361` |
| Fixed 1 | `eec8301f4bcd29e757261dad5008e4e4a741037d6610e916ff7dfeb3040c9562` |
| Fixed 224 | `31199beef286f444b6832a0db83232d82ec5d58bf2fa523b3f4ddbe493087521` |
| Longevity 0 | `1811e7ca1691041f222f71a9dba48f101c9d05d54155a396fcd3ac247c8b9231` |
| Longevity 1 | `5417539c75e51d6b8464c5337d2c33b0e26657b523b09b0a8c1b3347927cf220` |
| Longevity 224 | `0b7744f1391085301cbf735088416568b493987dbfcdf3a6808b52887b50bfdc` |

The initial new-test compile found missing `Expense` imports, corrected locally.
There were no financial assertion failures and no unrelated code was changed.

## Performance and retained memory

`MonteCarloPairedBenchmarkTest` measures 5,000 worlds in each mode, sequentially,
after 30-world warmups. It uses the Phase 3 mortality execution fixture, seed 417,
4.5% expected return, 12% volatility, and general inflation with 2% mean, 1.75%
volatility and -2% floor. Fixed mode has 30 years (2027–2056). Longevity mode uses
each sampled lifetime. Identical strategies exercise two real projections per world.

| Mode | Single strategy | Paired | Time ratio | Single retained heap | Paired retained heap |
|---|---:|---:|---:|---:|---:|
| Fixed | 15.756 s | 30.436 s | 1.932x | 5.42 MiB | 32.45 MiB |
| Longevity | 12.565 s | 23.908 s | 1.903x | 14.83 MiB | 29.26 MiB |

The fixed single result retains aggregate annual samples/terminal summaries,
whereas paired output must retain both per-world annual prefixes; therefore its
retained-memory ratio is larger than its execution-time ratio. Longevity single
results already retain per-world prefixes. Timing includes analysis setup and
reduction; single fixed mode also computes its normal deterministic reference.
These are one representative JVM measurement, not a statistical performance claim.
Raw local logs: `target/phase4a-benchmark.txt` and `target/phase4a-benchmark.log`.

Memory is measured with `MemoryMXBean` after requested GC, while keeping the
result reachable. This is an approximate retained-heap delta, not peak allocation
or a guaranteed collection measurement. Pairs retain two annual scalar maps per
world, so retained memory scales with completed financial years. Source worlds
and engine rows become collectible after each iteration; the request retains
only two frozen plans plus assumptions.

## Phase 4B notes

- Consume these ordered compact pairs without substituting zero for failures.
- Keep A-minus-B direction and same-terminal-date requirements explicit.
- Use the same captured assumptions/indexed world source for future 70/70 versus
  70/62, 67/67 versus 70/70, or caller-managed grids. No enumeration was implemented.
- Generated replay uses the captured request and current frozen generation
  protocols. Caller-supplied replay additionally requires the caller's source.
- Keep future scenario detail bounded; these results deliberately do not retain paths or full rows.
- If independent exact survivor-date strategy overrides are later required, add
  them deliberately; Phase 4A uses complete persisted strategy inputs and does
  not invent another claiming abstraction.

## Final review boundary

`git diff --check`: **passed, no output**. Since these additions are deliberately
unstaged/untracked, all 11 files were also checked using
`git -c core.autocrlf=false diff --no-index --check -- /dev/null <file>`:
**zero whitespace diagnostics**. The no-index difference exit status is expected
for new files. `git diff --cached --stat` is empty; HEAD remains `8249c50`.

Exact file boundary: **11 new files** (7 production, 3 tests, 1 report); **0 existing
files modified**, **0 deleted**, **0 staged**. No UI file changed. Exact final
`git status --short`:

```text
?? MONTE-CARLO-PHASE-4A-PAIRED-COMPARISON.md
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloComparisonWorld.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPairedOutcome.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyCandidate.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonAnalyzer.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonRequest.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonResult.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyOutcome.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPairedBenchmarkTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPairedWorldTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonTest.java
```
