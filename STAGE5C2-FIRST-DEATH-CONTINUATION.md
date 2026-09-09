# Stage 5C2: guarded first-death continuation reuse

**Stage 5G update:** This report records the original Stage 5C2 contract and measurements.
Weighted financial runs now stop exactly at second-death year minus one; opening
snapshots require no engine call. All supported non-opening positive scenarios can
join financial continuation paths, including deaths inside the configured horizon.
Post-snapshot failures no longer invalidate the member. Carrier failures still
fall back independently. The early-horizon restrictions below are historical;
see [Stage 5G](STAGE5G-EXACT-SECOND-DEATH-HORIZON.md) for the current contract.

Completed implementation resumed September 8, 2026. Sequential, headless, exact
financial prefix reuse; no UI, persistence, mortality approximation, candidate
pruning, inherited-account rules, or general projection checkpoint changes.

## Reference paths and integration

`LongevityWeightedIntegratedStrategyEvaluator` remains the unchanged Stage 4
single-strategy oracle. `compareExact` retains the Stage 5A/5B independent
multi-strategy behavior. The package-level `compareWithEquivalenceOnly` preserves
Stage 5C1's original proven groups followed by independent Stage 4 representative
evaluation. Existing Stage 5C1 tests explicitly use that path and retain their
original financial and work-count assertions.

The normal comparison `compare` runs the unchanged Stage 5C1 planner, then uses
`LongevityWeightedContinuationEvaluator` for representative and baseline work.
No failed representative is shared with other strategies. Every original
comparison entry, ranking, baseline delta, and requested detail retains its own
identity. Actual work counts intentionally differ from the reference counts.

The new evaluator preserves Stage 4's ordered probability/PV accumulation and
exception wrapper. It delegates projection and estate/discount-factor calculations
to existing domain services. No new estate or financial projection formula exists.
Its accumulation and methodology metadata intentionally mirror the untouched
oracle; exact full-result tests guard against drift between the two paths.

## Financial-prefix proof

For a fixed isolated plan and SS strategy, two scenarios with the same first
decedent and first-death year have equal inputs before the earlier second death:

- SS own/spousal benefits depend on current-month alive status and fixed elections.
  Survivor benefits depend on first-death worker history, survivor election, and
  payment month. Future second death cannot change earlier payable benefits.
- Pension survivor cash uses current alive status, source commencement/end dates,
  survivor amount, and source COLA.
- RMD eligibility uses current living owners and the preceding projected balance
  snapshot. The lifetime first-household-RMD boolean accumulates only prior/current
  requirements; it never resets at an owner's death.
- Roth execution uses current balances, living-owner eligibility, schedule, RMD
  stop state, and current-year tax/bracket-fill feedback.
- Medicare, filing status, taxes, expenses, withdrawals, retained household cash,
  investment growth, and heir-tax valuation use current-year inputs and prior
  projected state, not remaining lifespan.

Equal opening portfolios and annual inputs therefore produce equal complete
annual rows and equal next-year state. The first year retains its original partial
opening-date behavior. Permanent characterization compares serialized complete
`ProjectionYear` rows, including account snapshots, tax funding, Medicare,
conversions, and retained cash, rather than only estate totals.

This proves financial prefixes, not unconditional scenario success. SS finite
request construction can omit out-of-horizon deaths or inapplicable survivor
elections. `validateForContinuation` executes the same provider request construction
and validation for each member, without generating another schedule. Ordinary and
equivalence provider calls take the unchanged calculation branch. If validation
cannot prove a member, that member runs independently so its original exception
contract remains authoritative.

## Grouping and carrier selection

Only positive-probability scenarios whose second death is after the configured
last projection year enter carrier groups. Groups distinguish primary-first plus
first-death year, spouse-first plus first-death year, and simultaneous deaths.

Each group selects its latest actual required second-death member; ties retain
the first original occurrence. No synthetic death date is introduced. Simultaneous
cases use the latest actual simultaneous member, whose earlier both-alive history
supplies earlier simultaneous snapshots. They do not use a synthetic both-survive
projection extended to the younger household member's terminal year.

Carriers are attempted lazily when an eligible original member first needs the
group. A successful carrier supplies scalar immutable estate snapshots using the
existing `EstateAtSecondDeathCalculator`: January 1 death in year D uses December
31 of D-1. Complete projections are discarded after extraction. Original scenario
iteration supplies each outcome's death years, probability, date, and PV; carrier
identity never substitutes for member identity.

## Early horizons and failures

Stage 4 projects through `max(configuredLastYear, secondDeathYear - 1)`.
If second death is within the configured horizon, the scenario always runs
independently. Its post-death rows may fail even when the earlier estate is
available, for example when a later one-time expense cannot be funded. Matching
January 1 opening snapshots and coverage-start rejection also retain Stage 4's
full validation contract.

If a carrier fails, its entire speculative result is discarded. Each affected
member subsequently runs independently in original scenario order. Carrier
failure does not finalize or fail any member. The public weighted evaluator still
aborts at the original first failed member, with no completed expected value.
Only the test diagnostic continues after individual failures to check every
younger-household scenario against isolated Stage 4 calls.

No RMD factors are extrapolated. Valid prepared mortality ends at age 120, and
actual carrier dates avoid artificial age-121 survival. The fallback test injects
the real unsupported-age RMD calculation into a speculative attempt, then proves
shorter independent success and the zero-balance bypass. It does not manufacture
unsupported mortality inputs. Naturally occurring withdrawal failures separately
exercise the same fallback policy across the full younger distribution.

## Isolation, cancellation, progress, and work

Every projection uses its own complete JSON-copied plan. Account/RMD state never
crosses plan graphs; identity-based account lookup remains within each run.
Only immutable estate snapshots are shared. The evaluator freezes the caller's
plan before progress callbacks, preserving results even if the live plan changes.

Cancellation is checked before/after carrier and independent projections, during
snapshot extraction, at original scenario boundaries, and after final progress.
Cancellation propagates, never becoming a completed aggregate or shared failure.
There is no new intra-year cancellation or checkpoint API. Progress remains
monotonic original-member completion; speculative extraction does not advance it.

`LongevityContinuationWork` separates:

- Positive-probability scenario inputs, started members, and completed outcomes.
- Carrier attempts, successful carriers, and failed carriers.
- Early-horizon and fallback independent runs.
- Actual projection starts and completed runs.
- Reused outcomes and annual rows from completed runs only.

Reused outcomes include the carrier member's own outcome. Net evaluations avoided
are `reusedOutcomes - carrierAttempts`: each reuse saves an independent run and
each speculation costs one. This can be negative on an aborted strategy. Failed
projections' partial rows are neither retained nor included in completed-row
counts. Carrier/copy attempts and engine starts are separately observable.

Successful strategy `actualProjectionRunCount` reports actual starts, not logical
scenarios. Comparison `work()` aggregates actual execution, including failed
representatives and baseline work. `stageFourEvaluations` counts oracle calls;
`continuationEvaluations` counts optimized strategy calls. Stage 5C1's separate
comparison-level `evaluationsAvoided` still counts avoided whole-strategy evaluations.
Do not sum shared entry-level counts to infer actual job work.

## Measurements

All measurements use IntelliJ bundled Maven and the required project-local
`.codex-m2/repository`. Timings vary with warmup and machine activity.

| Older production fixture | Independent Stage 4 | Stage 5C2 |
| --- | ---: | ---: |
| Logical positive scenarios | 2,600 | 2,600 |
| Engine runs | 2,600 | 116 |
| Annual rows | 89,755 | 5,180 |
| Warmed runtime, resumed focused run | 4.404 s | 0.300 s |

The 116 runs comprise 100 successful carriers and 16 early-horizon independent
runs, with no fallbacks. Counts are derived from fixture inputs, not hard-coded
in the implementation or asserted as an unexplained constant. Net savings are
2,484 runs (95.54%); measured speedup is 14.68x. All 2,600 outcome records and all
non-work result fields match Stage 4 exactly, including BigDecimal scale.

The younger diagnostic checks all 3,720 scenarios: 3,638 successes and 82 exact
failures. It attempts 120 carriers (110 successful, 10 failed), 16 early runs,
and 559 fallback runs: 695 starts, 603 completed runs, and 21,350 completed rows.
Net savings are 3,025 runs. The resumed diagnostic measured 14.874 seconds for
individual Stage 4 calls and 1.857 seconds for continuation-session work. These
diagnostic timings include different setup overhead and are not a completed
weighted-result benchmark: both public evaluators abort at primary death 2031,
spouse death 2081. At that point optimized work is 1 failed carrier, 4 early runs,
47 fallbacks, 52 starts, and 50 completed outcomes.

The opt-in 81-retirement-pair financial benchmark completed sequentially in
24.131 seconds: 8,100 successful carriers, 1,296 early independent runs, 9,396
completed projections, 419,580 annual rows, and 210,600 outcomes. No fallbacks
occurred. It uses the older fixture's 81 previously proven retirement-pair groups
without rerunning planning or 5,184 independent strategies. This is a measured
financial workload, not an end-to-end full-universe equivalence comparison.

With unchanged planning at approximately 101-126 seconds, planning is now the
dominant measured cost. Planning plus this measured financial workload is an
estimated 125-150 seconds before remaining job overhead. Planner optimization is
a separate next step, not part of Stage 5C2.

## Verification and resumption

On resumption, all four production components, integration, three test classes,
and three successful benchmark/focused logs already existed. The last prefix
assertion strengthening and zero-probability test had not been verified together.
The resumed work preserved them, verified compilation and characterization, added
partial-opening optimized evaluation and logical-progress/live-edit coverage,
and completed this documentation and final verification.

Verification logs:

- `stage5c2-characterization-resumed.log`: 8 characterization cases, success.
- `stage5c2-focused-resumed.log`: production diagnostics and focused checks.
- `stage5c2-focused-final-additions.log`: final focused additions.
- `stage5c2-reference-resumed.log`: Stage 4/5 and lifetime reference checks.
- `stage5c2-full-resumed.log`: complete-suite verification.
- `stage5c2-representatives.log`: opt-in 81-representative benchmark, one test passed.

Final verification on September 8, 2026:

| Verification | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Prefix characterization | 8 | 0 | 0 | 0 |
| Focused optimization and benchmark tests, final versions | 31 | 0 | 0 | 1 |
| Stage 4/5 and lifetime reference tests | 105 | 0 | 0 | 1 |
| Complete Maven suite | 905 | 0 | 0 | 5 |
| Separate 81-representative benchmark | 1 | 0 | 0 | 0 |

Every run finished with BUILD SUCCESS. The final focused total combines the 20
focused cases and 11 benchmark/integration cases; all also passed in the complete
suite. The complete suite adds 39 cases to the 866-test baseline. Its extra skip
is the opt-in 81-representative benchmark already run separately. No deterministic
or Stage 4 financial-output differences were found. Actual work counts differ
intentionally. `git diff --check` also passed.

The complete-suite benchmark repeated the older measurement at 4.674 seconds
independent versus 0.305 seconds optimized (15.32x), with identical work counts.
The younger diagnostic repeated at 14.946 seconds independent versus 1.939
seconds optimized, with the same success, failure, carrier, and fallback counts.

## File inventory

Added production files under `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/`:

- `LongevityWeightedContinuationEvaluator.java`
- `LongevityContinuationSession.java`
- `LongevityScenarioContinuationPlanner.java`
- `LongevityContinuationWork.java`

Modified production files:

- `app/socialsecurity/LongevityWeightedIntegratedStrategyComparisonService.java`
- `app/socialsecurity/LongevityWeightedIntegratedStrategyComparisonResult.java`
- `domain/projection/SocialSecurityProjectionIncomeProvider.java`

Added tests under `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/`:

- `LongevityContinuationPrefixTest.java`
- `LongevityContinuationTest.java`
- `LongevityContinuationBenchmarkTest.java`

Modified existing tests in that same directory only to pin the preserved Stage 5C1 path:

- `LongevityWeightedStrategyEquivalenceTest.java`
- `LongevityWeightedEquivalenceBenchmarkTest.java`
- `LongevityWeightedComparisonBenchmarkTest.java`

This document and eight `stage5c2-*.log` verification/benchmark logs are added.
The pre-existing one-line whitespace change in `AGENTS.md` is unrelated and was
left untouched. No files were reset, reverted, discarded, deleted, or committed.

## Remaining boundaries

The model still treats deceased-owner assets as invested household assets without
inheritance, retitling, inherited-account distributions, beneficiary rules, or
estate settlement. Estate includes investable assets with the existing estimated
tax-deferred heir-tax haircut; non-investable assets are excluded. Mortality
weights remain independent and unmodified, and deaths remain annual January 1.

Before another stage, retain exact provider-validation and complete-prefix tests
whenever death-sensitive mechanics change. Failed carriers intentionally trade
performance for exactness. No general resumable state is needed for this design.
