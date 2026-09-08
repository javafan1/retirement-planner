# Stage 5C1: proven longevity-weighted strategy equivalence

Implemented September 7, 2026. No commit, persistence/UI change, engine checkpoint,
parallel execution, or exhaustive weighted financial search.

## API and financial reference

`LongevityWeightedIntegratedStrategyComparisonService.compare(request)` now plans
job-local equivalence before financial evaluation. `compareExact(request)` retains
the Stage 5A/5B independent evaluation path, including evaluation of duplicate
candidate occurrences. Both use the unchanged Stage 4 evaluator.

The request still freezes the source plan with `RetirementPlanScenarioCopyService`.
The planner and financial service obtain independent copies of the same frozen
inputs. No live balance, horizon, JSON, candidate, mortality probability, or baseline
policy is changed. Baselines remain separately evaluated, with the existing explicit
complete survivor-policy requirement and no invented age-60 election.

## Exact proof

`LongevityWeightedStrategyEquivalencePlanner` starts with individually validated
candidates, then refines partitions against every positive-probability mortality
scenario. For each scenario it uses the existing mapper and provider with coverage:

    first = projection start calendar year
    last = max(configured last year, second death year - 1)

The compared object is the complete calendar-year-indexed map of
`HouseholdSocialSecurityResult`. Every field participates:

- Primary and spouse own benefits.
- Primary and spouse spousal excess benefits.
- Primary and spouse survivor candidates.
- Primary and spouse benefit selections.
- Household benefit.

Java map/record equality compares all fields and resolves hash collisions with full
equality. BigDecimal equality remains exact, including scale; there is no rounded
metric key, tolerance, digest-only proof, or household-total shortcut. Strict scale
equality can miss an optimization but cannot introduce an approximate match.

Coverage includes the opening calendar year's authoritative annual SS result, even
when the financial opening date is partial-year. This is exactly what the current
ProjectionEngine consumes. The proof does not stop at the configured horizon or
group mortality outcomes by second death. Zero-probability outcomes are ignored;
original weights are never changed.

Singleton partitions need no further comparisons because no result will be shared
with another strategy. They still undergo normal Stage 4 validation and evaluation.

## Bounded authoritative schedule cache

A fresh cache exists for one mortality scenario at a time. The provider constructs
and validates each finite SocialSecurityStrategyRequest before consulting it. Exact
duplicate strategies can reuse that already-validated request's result.

The authoritative monthly calculator exposes an exact input key retaining analysis
start/end, both complete retirement elections (ownership, birthday, FRA amount,
benefit valuation year, exact claim date), both death dates and COLA. Its survivor
component is the resolved entitlement month. This uses the same extracted helper as
the existing monthly calculation: the later of intended claim month and worker death
month, or absent when there is no applicable claim/death.

The extraction leaves the existing calculation unchanged. It does not decide that
an election is irrelevant because of age, benefit dominance, or equal estate values.
Each original request still receives its existing validation. Annual schedules from
different input keys may group only after complete result equality. The ordinary
projection path uses no cache. Neither ProjectionEngine nor Stage 4 was modified.

The cache key is a maintenance boundary: future changes to authoritative SS inputs
or survivor entitlement semantics must update this key and its uncached-reference
tests together. Reuse is safe because the provider is currently the only path by
which a strategy changes ProjectionEngine's financial inputs.

## Identity, errors, retention and progress

The earliest one-based original input position represents each partition. Every
original occurrence remains a separate comparison entry with its exact original
strategy instance and input position. All original entries are ranked, and exact
ties keep input order and the existing competition ranks. Baseline deltas use the
unchanged arithmetic.

Proof/validation failures become singleton partitions and run through the reference
path. Financial failures are NEVER shared, even for a proven group: every member is
evaluated independently and receives its own failure identity/message. Cancellation
propagates as AnalysisCancelledException, with no completed partial comparison or
incomplete-probability aggregate.

Stage 5 bounded retention remains in force. A representative temporarily retains
scenario outcomes only if its group contains a requested detail position. Immutable
outcomes have no embedded strategy identity; they are attached to each selected
original entry, whose strategy supplies that identity. Unselected entries have no
details. No detail re-evaluations are required. No full Projection is retained.

A separate sequential `LONGEVITY_STRATEGY_EQUIVALENCE` phase reports checked mortality
outcomes. The following comparison phase counts every original candidate and the
optional baseline, including shared successes and failures. Cancellation is checked
throughout planning, at strategy boundaries, and through Stage 4's existing scenario
boundaries. There are no competing nested progress streams.

The result exposes the immutable equivalence plan, original-to-representative mapping,
potential and actual avoided evaluations, and existing actual work counters. A
strategy aggregate's projection count still describes that strategy's Stage 4 result;
job `work()` counts actual executions. Baseline work is included there but is outside
candidate equivalence groups. `detailReevaluations()` is zero.

## Measurements

All measurements used IntelliJ bundled Maven and the Codex-local Maven repository.
The older household uses the production SSA distribution: 2,600 positive-probability
outcomes. Timings vary with JIT warmup and other test activity.

| Measurement | Inputs | Groups | Stage 4 evaluations | ProjectionEngine runs | Time |
| --- | ---: | ---: | ---: | ---: | --- |
| Existing three-candidate benchmark | 3 | 3 | 3 | 7,800 | 14.636 s total; first completion 5.466 s; planning 0.005 s |
| Intentional survivor-election redundancy | 24 | 3 | 3 | 7,800 | 4.024 s planning + 13.746 s financial = 17.770 s |
| Complete universe, planning ONLY | 5,184 | 81 | 0 | 0 | 101.321 s |

The 24-candidate run actually avoided 21 Stage 4 evaluations, retained 24 aggregates
and only 2,600 selected scenario outcomes. No detail re-evaluations occurred.

Full-universe planning examined 13,478,400 strategy/scenario requests, using 210,600
successful authoritative SS calculations through its bounded cache. It proves 5,103
potentially avoidable Stage 4 evaluations (98.4375%) for THIS fixture. It does not
prove 81 groups for other households or distributions.

Using the measured 13.746 seconds for three representative evaluations gives an
estimate of about 472 seconds (7.8 minutes) for planning plus 81 financial evaluations.
The corresponding unoptimized extrapolation is about 6.6 hours. This is an estimate,
not a measured exhaustive weighted search. Financial run count would fall from
13,478,400 to 210,600 if all representatives succeed and no baseline is requested.
Failures intentionally cause extra independent evaluations.

## Verification and counterexamples

Before changing existing behavior, 39 characterization/reference tests passed.
The same 39 tests passed after implementation. Existing Stage 5 exact-work-count and
progress assertions now explicitly call `compareExact`; their assertions were not
weakened.

Focused tests compare every original ordered entry, ranked entry, aggregate,
probability, baseline delta, failure, detail and shared metadata with the exact path.
Fixtures include older and younger households, early death in either direction,
asymmetric benefits, simultaneous death, partial opening years, bracket-fill Roth
conversions, extended horizons, explicit duplicate identities and zero mortality mass.

Counterexamples prevent grouping when retirement dates change benefits, early death
makes survivor timing relevant, one strategy pays own benefits and another survivor
benefits, later coverage exposes differences, or owner components differ despite
identical household totals. Invalid policies and opening-RMD financial failures stay
individual. Planning, strategy-boundary and inside-Stage-4 cancellation are covered.

Another 4,608 annual schedule sequences across retirement/survivor grids were compared
between cached and uncached authoritative provider calls, including younger/older,
primary-first, spouse-first, simultaneous and late mortality cases. Source JSON,
balances, frozen inputs, ordinary projections and deterministic search ranks are
checked for isolation.

The full-universe benchmark is opt-in with `-Dstage5c1.fullPlanningBenchmark=true`.
It passed separately (one test, no failures/errors/skips). Normal verification skips
this long benchmark in addition to the three pre-existing skipped tests.

Final verification: characterization/reference 39 tests, 0 failures, 0 errors, 0 skipped;
focused 27 tests, 0 failures, 0 errors, 1 skipped; full suite 866 tests, 0 failures,
0 errors, 4 skipped. All BUILD SUCCESS. The opt-in benchmark passed separately.

Logs:

- `stage5c1-characterization.log` and `stage5c1-characterization-after.log`
- `stage5c1-focused.log`
- `stage5c1-planning-benchmark.log`
- `stage5c1-full.log`

## Remaining limitations and Stage 5C2

All Stage 4 financial limitations remain: deceased-owner assets stay household assets
without inherited-account distributions/retitling, January 1 annual death timing,
estimated heir-tax haircut, investable-estate scope, independent mortality, and no
invented living-owner RMD factor beyond the supported age.

Equivalence makes this older fixture feasible as background work, not an interactive
exhaustive search. First-death continuation reuse remains worth investigating for a
later approved stage, with explicit capture of all account/RMD, Roth-stop, retained
cash, tax and Medicare state and exact reference tests. It is not implemented here.
The 101-second proof cost is also a material part of exhaustive-job latency and should
be profiled before committing to a larger engine refactor.

## Files changed in Stage 5C1

Files already untracked from previous stages are not new Stage 5C1 files.

Added:

- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedStrategyEquivalencePlanner.java`
- `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedStrategyEquivalenceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedEquivalenceBenchmarkTest.java`
- `STAGE5C1-STRATEGY-EQUIVALENCE.md`

Modified:

- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedIntegratedStrategyComparisonService.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedIntegratedStrategyComparisonResult.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/projection/SocialSecurityProjectionIncomeProvider.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityStrategyCalculator.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/analysis/AnalysisPhase.java`
- `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedIntegratedStrategyComparisonTest.java`
- `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedComparisonRequestTest.java`
- `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedComparisonBenchmarkTest.java`
