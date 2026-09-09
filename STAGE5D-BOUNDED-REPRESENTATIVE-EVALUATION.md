# Stage 5D: bounded representative evaluation

Stage 5D changes execution scheduling, not financial calculations. Stage 5C3 proof planning remains sequential. The comparison evaluates its optional baseline in its established position, then delegates independent Stage 5C2 representatives to `LongevityWeightedRepresentativeEvaluationCoordinator`. Each representative's continuation groups, carriers and fallbacks remain sequential. **Nested analysis executors are prohibited.** Stage 4, Stage 5C1 and Stage 5C2 algorithms, persisted models and UI are unchanged.

## Ownership and bounds

The internal default limit is 4; the hard cap is 8. Effective workers are `min(configuredLimit, 8, Runtime.availableProcessors(), representativeCount)`. The package-private constructor supports testing/internal configuration. Zero work creates no executor; one effective worker executes directly on the coordinator thread.

For multiple workers, a fixed platform-thread pool uses an explicit `ArrayBlockingQueue`. At most twice the effective worker count of tasks are outstanding, normally one worker-count active and one queued. Physical queue capacity is twice the worker count to tolerate the brief interval between a future becoming complete and its worker becoming available. Submission is incremental; all 81 plan graphs are never prepared at once. A failed member retry occupies the slot released by its representative's consumed result.

Only the comparison/coordinator thread prepares tasks. Each gets `request.newPlanCopy()`, preserving the existing frozen JSON plan-copy semantics, and its own evaluator, work counter and complete aggregate computation. Accounts, projected portfolios, RMD snapshots, retained cash, Roth state and scenario plan copies stay inside that representative. Workers receive immutable strategy/scenario/valuation values and a controlled cancellation adapter, not mutable comparison collections or planner caches. RMD and same-owner Roth account identity remain local to the copied graph.

The task result contains an immutable entry and work snapshot. Numeric financial evaluation must not call presentation formatters: in particular, `ProjectionYear.getIrmaaBracketDisplay()` reaches shared mutable formatting state. Do not introduce projection serialization or presentation formatting on these workers without a separate safety review.

## Ordering, failures and retained detail

Representative work is ordered by original representative position. Futures are consumed by that position, not completion order. Original occurrences are expanded sequentially in original input order; ranking and baseline deltas are assembled sequentially after the executor closes. Complete representative aggregates, including BigDecimal scales and summation order, are computed entirely inside each task. Competition ranks and original-order ties use the existing comparator.

A structured failed representative stays failed. Each later equivalent original occurrence independently retries through the same bounded coordinator, exactly as in the sequential path. A successful retry never replaces/promotes the failed representative. Ordinary task infrastructure exceptions propagate as failures rather than masquerading as cancellation. Baseline failure continues to suppress baseline deltas without discarding candidate results.

Detail selection is fixed before submission. A representative retains detail if any selected original member needs it; expansion attaches the original candidate identity and strips unselected detail. Baseline plus selected original entries remain capped at 20. Existing Stage 5C2 compact outcome retention is unchanged; no carrier projections are retained by this coordinator. Consumed futures are removed promptly.

Final work accounting uses representative-local counters. Immutable work snapshots are added sequentially in input traversal order, including independent retries. Continuation counts already included in public totals are not counted twice. No shared financial accounting atomics or concurrent BigDecimal reductions are used.

## Cancellation and progress

Only the coordinator thread invokes the external cancellation token or progress listener. While awaiting a future it polls at 25 ms intervals and latches cancellation in an `AtomicBoolean`; workers see only that sticky flag at existing Stage 5C2 cooperative boundaries. Direct one-worker execution polls the external token at those same boundaries on the owner thread.

Cancellation stops submission. Scope cleanup sets the sticky flag, cancels outstanding futures with `cancel(false)`, clears queued tasks, shuts down the executor and waits for termination before returning/throwing. Active engine calls finish their current non-interruptible call, then stop at existing boundaries. No `Thread.stop`, `shutdownNow`, or arbitrary worker interruption is used. An externally interrupted coordinator requests cooperative cleanup, restores its interrupt status and reports an infrastructure exception. Cleanup also runs on callback/preparation failures and ordinary exceptions.

The comparison checks cancellation after task consumption, after executor closure and immediately before returning the assembled result. A cancelled job publishes no partial/completed comparison. As with any cooperative API, an event occurring after the final token check cannot be observed retroactively; a future UI adapter must also guard result installation against its current job/cancellation identity.

Stage 5C3 keeps its separate proof progress phase. Financial progress counts finalized original occurrences, plus optional baseline. It advances only as the coordinator consumes/expands the contiguous original-input prefix. Failures finalize one occurrence; retries/fallbacks never double-count it. Completion order therefore cannot change the published count sequence or listener thread. This intentionally favors deterministic progress over displaying whichever worker finishes first; a slow leading representative can temporarily hold the visible count steady.

## Verification and maintenance

`compareSequential` retains the no-coordinator Stage 5C3/5C2 path as a package-private oracle. Stage 5A/B `compareExact` and Stage 5C1 `compareWithEquivalenceOnly` remain sequential. Four characterization tests were added and passed before changing execution semantics.

Permanent Stage 5D tests cover complete record equality (excluding elapsed durations only), original identity, exact decimal scales, duplicates, competition ties, structured and simultaneous failures, baseline failure, independent successful retries without promotion, genuine younger-household carrier/fallback failures, retained detail and cap, reversed completion using latches, bounded submission, cooperative cancellation with real carrier/fallback calls, final publication cancellation, callback/preparation failure cleanup, plan/account/RMD isolation and concurrent weighted/deterministic work. There are no sleep-based correctness assertions.

The opt-in `Stage5DRepresentativeBenchmarkTest` warms the original sequential service, then runs sequential/1/2/4/6/8 followed by the reverse order. Every measured run compares the complete externally meaningful result against the sequential reference, including all 5,184 entries/ranks, mappings, aggregates and work. It also checks source JSON and original candidate identity. Enable with `-Dstage5d.fullBenchmark=true -Dtest=Stage5DRepresentativeBenchmarkTest -DargLine=-Xmx2g`; use the repository's mandated IntelliJ Maven and local repository argument.

Each comparison owns its pool. Concurrent jobs can therefore oversubscribe CPU and multiply peak memory even though each individual job is bounded. Before Stage 5E UI integration, define application-level admission/cancellation ownership, marshal coordinator callbacks to JavaFX, guard stale result installation and keep planning/financial progress distinct. This stage does not publish provisional best/rank results; only the complete deterministic result has authoritative ranks. Runtime/accounting diagnostics are available without changing UI or persistence.

The earlier inspection report remains a separate historical record.

## Production benchmark: September 8, 2026

The opt-in benchmark passed in 379.9 seconds (Maven BUILD SUCCESS). Runtime reported 8 processors and a 2,048 MiB maximum heap. The inspection identified an Intel Core Ultra 7 355; physical core topology was unavailable through the permitted CIM query. These results describe this machine/JVM, not a hard-coded production assumption.

After one full sequential warm-up, each mode ran twice in forward/reverse order. Financial time includes ordered assembly and executor cleanup; proof planning is excluded. Speedups below compare the two-sample financial means with the sequential mean (23.856 s). Two samples establish useful local evidence, not precise portable scaling.

| Mode | Financial samples (s) | Mean speedup | Observed total range (s) | Total estimate with 12.5 s planning (s, mean) |
| --- | --- | --- | --- | --- |
| Sequential oracle | 24.107 / 23.604 | 1.00x | 38.436–38.838 | 36.36 |
| 1 worker, direct coordinator | 24.177 / 24.224 | 0.99x | 38.883–39.094 | 36.70 |
| 2 workers | 13.759 / 12.316 | 1.83x | 27.194–28.295 | 25.54 |
| **4 workers (default)** | **7.698 / 7.616** | **3.12x** | **22.176–22.258** | **20.16** |
| 6 workers | 5.440 / 5.340 | 4.43x | 19.973–20.080 | 17.89 |
| 8 workers | 5.134 / 4.460 | 4.97x | 19.387–19.829 | 17.30 |

Observed planning was 14.478–14.927 s. Its unchanged sequential cost explains the difference between observed totals and the estimate using the earlier 12.5 s baseline.

Every measured run had **5,184 successes, zero failures, 81 representatives and 9,396 engine runs**, with exact complete-result equality to the warmed sequential oracle. Ordered/ranked entries, identity, representative mapping, decimal values/scales, coverage, aggregate fields and final work accounting matched. Only elapsed durations are excluded from equality. No weighted-output differences were found. Deterministic reference tests and concurrent deterministic projection checks also passed; financial formulas were not changed.

| Mode | Sampled peak used heap (MiB) | Financial GC time (ms) | GC collections | Mean active CPU cores, range |
| --- | --- | --- | --- | --- |
| Sequential | 213–253 | 436–475 | 363–438 | 1.01–1.06 |
| 1 | 234–295 | 478–572 | 357–396 | 0.99–1.05 |
| 2 | 358–406 | 440–482 | 230–259 | 1.95–2.09 |
| 4 | 519–527 | 418–430 | 178–179 | 3.88–4.00 |
| 6 | 751–776 | 392–403 | 131–132 | 5.69–5.70 |
| 8 | 946–1,028 | 389–407 | 104–112 | 6.12–6.86 |

Heap was sampled every 20 ms, so this is not an allocation profile or guaranteed instantaneous peak; native memory is excluded. A full GC occurred before each sample outside the measured phase. GC and CPU counters are process-wide during financial execution. CPU demand tracks the bounded pool through four workers; six/eight do not continuously saturate all eight reported processors. Allocation rate was not measured. No OOM or excessive GC growth occurred with this 2 GiB heap, but additional workers clearly amplify live/used heap and concurrent-job cost.

Four remains the recommended default: approximately 3.1x financial speedup with half the eight-worker sampled heap. Eight remains an appropriate internal cap, not a default or UI setting. Implementation is warranted by exactness and measured speedup. Nested parallelism remains prohibited.

## Verification results

All commands used IntelliJ bundled Maven and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.

- Original sequential characterization before production edits: 4 tests, no failures/errors/skips.
- Final focused `Stage5D*Test`: 37 tests, no failures/errors, 1 skipped opt-in benchmark (includes the 4 characterization tests).
- `Longevity*Test` references: 149 tests, no failures/errors, 3 skipped opt-in benchmarks.
- Final full suite after the strengthened reverse-order/retry progress assertions: 989 tests, no failures/errors, 7 skipped; BUILD SUCCESS at September 8, 2026, 19:21:47 EDT (`stage5d-full-suite-final.log`).
- Explicit full 81-representative benchmark: 1 test, no failures/errors/skips; BUILD SUCCESS, all six modes twice.

No existing oracle was weakened. The bounded queue design specifically avoids a potential completion-versus-worker-release rejection race; cancellation tests verify active calls are not interrupted and worker threads terminate. No financial concurrency defect required a formula or Stage 5C2/5C3 semantic change.

## Resume audit: September 9, 2026

Recovery inspected the tracked diff, every untracked Stage 5D source/test/report, all eight Stage 5D Maven logs, source timestamps and Surefire reports before editing. Production implementation, permanent tests and every required benchmark mode were **COMPLETED**. Documentation was **PARTIAL** because it still described the final suite as forthcoming. Final working-tree review was **NEEDS RECHECK**. No implementation or verification item remained **NOT STARTED**, and no verification process was interrupted: the last full suite had finished successfully before this resume. The exact point at which the prior conversation stopped cannot be established from filesystem evidence; only documentation/report closeout remained demonstrably unfinished.

| Reused verification | Tests | Failures | Errors | Skipped | Existing evidence (all BUILD SUCCESS) |
| --- | ---: | ---: | ---: | ---: | --- |
| Original sequential characterization | 4 | 0 | 0 | 0 | `stage5d-characterization.log` |
| Characterization after introducing sequential oracle | 4 | 0 | 0 | 0 | `stage5d-characterization-after.log` |
| Final focused Stage5D tests | 37 | 0 | 0 | 1 | `stage5d-focused-final.log` |
| Longevity reference tests | 149 | 0 | 0 | 3 | `stage5d-reference.log`; later final full suite also covers these classes |
| Final complete suite | 989 | 0 | 0 | 7 | `stage5d-full-suite-final.log`; 172 Surefire XML reports independently sum to these totals |
| Full production benchmark, all six modes twice | 1 | 0 | 0 | 0 | `stage5d-production-benchmark.log` |

The earlier successful focused/full runs remain in `stage5d-focused.log` and `stage5d-full-suite.log`, superseded by their final logs. Current Surefire Stage 5D reports contain 23 comparison, 7 coordinator, 2 cancellation and 4 characterization tests, plus the one normally skipped opt-in benchmark. The explicit successful benchmark log is authoritative for its measured execution; the later suite correctly records that benchmark as skipped.

The latest production edit was September 8 at 19:10:33 EDT and the latest test edit at 19:14:44 EDT. Final focused verification finished at 19:19:49 and the final suite at 19:21:47; the production benchmark finished at 19:19:15. The reference-only log predates the final production edit, so its coverage on the final code is supplied by the later successful complete suite. No Java, test, dependency or build files changed during this resume. No tests or benchmark modes were rerun, and no earlier evidence was invalidated. The resumed change only completes this report and its evidence trail.

Review found no truncated files, unfinished production comments, introduced TODO/debug instrumentation, or unrelated production changes. Benchmark printing and heap/GC monitoring are intentional opt-in test harness code. The existing `*.log` ignore rule keeps verification logs untracked; nothing was staged or committed. The historical inspection report remains unchanged. Application-wide job admission, JavaFX callback dispatch, stale-result publication guards and keeping mutable presentation formatters outside financial workers remain the concerns to resolve before UI integration.

## File inventory

Production addition under `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/`:

- `LongevityWeightedRepresentativeEvaluationCoordinator.java`

Production modification in the same package:

- `LongevityWeightedIntegratedStrategyComparisonService.java`

Test additions under `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/`:

- `Stage5DSequentialCharacterizationTest.java`
- `Stage5DCoordinatorTest.java`
- `Stage5DComparisonTest.java`
- `Stage5DCancellationTest.java`
- `Stage5DRepresentativeBenchmarkTest.java`

This implementation report is new. The pre-existing inspection report and `.gitignore` change were preserved. No other production files, UI, persisted models, dependencies or build configuration were modified. No commit was made.
