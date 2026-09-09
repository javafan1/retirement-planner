Stage 5D: bounded parallel longevity-weighted representative evaluation

Inspection and experimental measurements, September 8, 2026. No production implementation is included. Recommendation: proceed with a separately testable bounded representative coordinator, default limit 4, internal maximum 8, additionally limited by Runtime.availableProcessors() and available work. Keep Stage 5C3 planning, each representative's Stage 5C2 continuation evaluation, and final assembly sequential.

1. Recovery and scope

The resumed inspection found only the pre-existing `.gitignore` addition `*.log`. There were no untracked Stage 5D files, Stage 5D logs, partial benchmark files, or inconsistent source edits. The earlier tool results and in-memory JShell harness definitions survived in the conversation. Completed work comprised the financial-path inspection, ten measurements with a 2 GiB heap limit, and four measurements with a 512 MiB limit. The unfinished work was a focused graph-isolation/concurrent-job check and the final report. Those are now complete.

No production, test, build, UI, persistence, or existing stage-document files were changed. This inspection report is the only added file. No Git history operation was performed. Existing `.gitignore` work was preserved.

The existing `stage5c3-full.log` independently confirms 952 tests, zero failures/errors, six skipped, BUILD SUCCESS, completed at 12:30:06 EDT. The subsequent Stage 5C3 benchmark log also records BUILD SUCCESS. Maven was not rerun for this inspection: the experiments used existing compiled classes, and no Java files were changed. Production and relevant benchmark/helper source timestamps were checked against existing classes; unrelated test-class discrepancies did not affect the harness.

2. Measured workload and methodology

The fixture is the existing `Stage4TestPlans.plan()`, complete universe from `LongevityWeightedEquivalenceBenchmarkTest.universe(plan)`, and its `productionScenarios(plan)`: 5,184 original strategies, 81 Stage 5C3 representatives, 2,600 positive mortality scenarios per representative, no comparison baseline, aggregate-only retention. Valuation date is 2029-07-01 and real discount rate is 0.03, matching the existing benchmark setup.

JShell received its harness through standard input, with `--execution local`, existing `target/classes` and `target/test-classes`, and Jackson/JUnit support jars from `.codex-m2/repository`. No harness source/class/report file was written. Each JVM performed an untimed full sequential job before measuring the unchanged full sequential comparison service. The financial experiment then reused its immutable representative mapping, supplied a distinct coordinator-created plan copy per representative, and invoked the unchanged private representative `evaluate` method through reflection. Each task had its own service instance and WorkCounter; all Stage 5C2 internals remained sequential. Results were retrieved by submitted representative position. The experimental executor had fixed worker count and an explicitly bounded 81-slot queue; the recommended implementation below bounds in-flight work more tightly.

The harness passed the comparison request to the existing private method for access to immutable inputs; its frozen plan was not read by workers. Production task descriptors should omit that containing request entirely, avoiding even a reachable reference to the coordinator's mutable plan graph.

Measurements include task setup, plan copying, evaluation, result collection, checking, and executor termination. They exclude Stage 5C3 planning. `System.gc()` ran before each timed financial sample, outside its reported GC interval. Heap usage was sampled every 20 ms. CPU is JVM process CPU time, including GC/JIT and harness overhead. Allocation is coordinator plus worker thread allocated bytes. No financial arithmetic used floating point; floating point was used only for benchmark units and ratios.

The first financial sweep was 1, 2, 4, 6, 8 workers; the return sweep was 8, 6, 4, 2, 1. This reduces ordering bias but is not a multi-machine or statistically precise scaling study. Worker startup/JIT, thermal conditions, adaptive heap sizing, and reflection/JShell overhead remain relevant. No other CPU-heavy experiment ran concurrently with the timed jobs.

3. Sequential references

| Heap limit | Planning seconds | Financial and assembly seconds | Total wall seconds | Process CPU seconds | GC milliseconds |
| --- | ---: | ---: | ---: | ---: | ---: |
| 2 GiB | 14.457 | 23.400 | 37.859 | 41.391 | 673 |
| 512 MiB | 15.077 | 24.348 | 39.425 | 41.438 | 1,260 |

These reproduce the supplied approximately 23.374-second financial baseline. Planning was slower than the supplied 12.4-12.8 seconds in these harness runs. The total estimates below explicitly use the requested 12.5-second planning assumption; they are not measured parallel end-to-end jobs.

4. Financial measurements with a 2 GiB heap limit

Every row completed all 81 representatives without failure and performed exactly 9,396 ProjectionEngine starts and completions. All representative aggregates, including BigDecimal scales, original strategy identity, and every continuation-work component matched the unchanged sequential reference.

| Workers | Sample | Wall seconds | CPU seconds | Average CPU cores used | GC ms | Sampled peak used heap MiB | Allocated GiB | Allocation GiB/s |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | outward | 22.746 | 25.531 | 1.12 | 495 | 233.6 | 87.462 | 3.85 |
| 2 | outward | 13.450 | 27.813 | 2.07 | 427 | 353.5 | 87.461 | 6.50 |
| 4 | outward | 8.926 | 34.594 | 3.88 | 447 | 511.1 | 87.460 | 9.80 |
| 6 | outward | 5.453 | 31.578 | 5.79 | 399 | 764.5 | 87.460 | 16.04 |
| 8 | outward | 4.516 | 32.172 | 7.12 | 395 | 1,043.7 | 87.460 | 19.37 |
| 8 | return | 4.576 | 32.391 | 7.08 | 402 | 1,043.3 | 87.460 | 19.11 |
| 6 | return | 5.805 | 32.922 | 5.67 | 431 | 740.1 | 87.460 | 15.07 |
| 4 | return | 8.359 | 33.203 | 3.97 | 416 | 568.4 | 87.460 | 10.46 |
| 2 | return | 12.562 | 25.453 | 2.03 | 439 | 347.5 | 87.460 | 6.96 |
| 1 | return | 23.715 | 23.797 | 1.00 | 487 | 276.8 | 87.460 | 3.69 |

Using the mean of the two one-worker samples (23.231 seconds) and the mean for each other worker count:

| Workers | Financial range seconds | Approximate financial speedup | Estimated total with 12.5 s planning |
| ---: | ---: | ---: | ---: |
| 1 | 22.75-23.72 | 1.00x | 35.25-36.22 s |
| 2 | 12.56-13.45 | 1.79x | 25.06-25.95 s |
| 4 | 8.36-8.93 | 2.69x | 20.86-21.43 s |
| 6 | 5.45-5.81 | 4.13x | 17.95-18.31 s |
| 8 | 4.52-4.58 | 5.11x | 17.02-17.08 s |

Using this inspection's actual 14.457-second planning reference instead, four workers suggest 22.82-23.38 seconds overall. Thus the 20-25-second target is supported by measurements at four workers even allowing for slower planning. These are estimates, not deployment promises.

5. Smaller-heap confirmation and output equality

The second JVM used a 512 MiB maximum heap and the worker order 4, 8, 8, 4. It additionally expanded each parallel representative result to all original occurrences, applied the existing ranking comparator and ranked-entry method with the existing competition-rank algorithm, and asserted exact equality of all 5,184 ordered entries and the complete ranked-entry list against the unchanged sequential service. Original strategy object identities also matched. Elapsed timings are intentionally outside semantic equality. The unchanged proof mapping was supplied by the sequential reference, not recomputed in parallel.

| Workers | Sample | Wall seconds | CPU seconds | Average cores | GC ms | Sampled peak MiB | Allocated GiB | Allocation GiB/s |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 4 | 1 | 8.478 | 35.250 | 4.16 | 759 | 427.3 | 86.909 | 10.25 |
| 8 | 1 | 4.946 | 35.469 | 7.17 | 800 | 420.0 | 86.907 | 17.57 |
| 8 | 2 | 5.020 | 36.031 | 7.18 | 820 | 409.9 | 86.907 | 17.31 |
| 4 | 2 | 9.159 | 37.500 | 4.09 | 789 | 411.3 | 86.908 | 9.49 |

All four runs passed the full entry/ranking checks and exact continuation accounting, with zero failures and 9,396 engine runs. In the 2 GiB sweep, equality checked complete representative aggregates and accounting; complete expanded entry/ranking equality was explicitly executed in the 512 MiB sweep for four and eight workers. This distinction matters: there is no implemented production parallel comparison result yet. Baseline and selected detail were exercised separately by the focused concurrent-job check below, not by the 81-representative aggregate-only benchmark. Failure/cancellation race cases remain permanent implementation tests, not claimed benchmark coverage.

Allocation volume was roughly 87 GiB per financial job, largely transient. Extra workers mainly increased allocation rate and simultaneously live intermediate state. The sampled peak grew from roughly 234-277 MiB with one worker to about 1,044 MiB with eight under the 2 GiB cap. The successful 512 MiB trials show that this observed growth is not a minimum live-heap requirement. A tighter heap increases GC and reduced eight-worker performance modestly. GC collection counts, exact instantaneous peak heap, native/RSS memory, and allocation by class were not recorded. Sampled used heap includes uncollected garbage and JShell overhead; it is not retained-live-set measurement. No OOM occurred, but larger plan graphs, retained detail, or simultaneous jobs require further memory testing.

6. Machine information

`Runtime.availableProcessors()` returned 8. Java was Temurin OpenJDK 25.0.4.1. The Windows processor registry identifies `Intel(R) Core(TM) Ultra 7 355`. `Get-CimInstance Win32_Processor` was denied, so physical-core topology and an independently queried OS logical-processor total were not established. Do not infer physical cores from the Java count.

Average process CPU usage was approximately 13-14% of eight processors at one worker, 25-26% at two, 48-50% at four, 71-72% at six, and 88-89% at eight. Eight workers approached machine saturation; fewer workers primarily saturated their allocated worker capacity. These process-wide averages do not establish which physical cores or core types were used.

7. Thread-safety classification

The inspection followed the comparison service, both weighted evaluators, continuation session/planner, ProjectionEngine, evaluation context, Social Security provider and monthly calculators, pension/income calculators, tax and tax-funding calculators, government-rule projection and loading, RMD services, withdrawals, Roth scheduling/bracket fill/execution, Medicare/IRMAA, estate snapshots/discounting, copy utilities, callbacks, and result/retention structures. Static-state searches covered production domain, application, persistence, and presentation code. Library internals were not exhaustively audited; the recommended design avoids sharing mutable application service instances across workers.

| Reachable object or state family | Classification and required ownership |
| --- | --- |
| BigDecimal values, dates, MathContext constants, enums, strings, strategies, survivor elections, lifetime scenario and ProjectionEvaluationContext | Immutable; safely share value inputs. Horizon overrides create new contexts. |
| Prepared mortality distributions, probabilities, assumptions, scenario list | Immutable value graphs with defensive list copies; safely share. No mutable mortality table/provider is needed during financial evaluation. |
| Comparison request's private frozen RetirementPlan | Job-local mutable graph, encapsulated without external exposure. It is frozen by ownership, not by an immutable Java type. Keep it out of task descriptors. |
| RetirementPlan, Household, Person, AccountPortfolio, Account, person account/income lists, household expense list, non-investable assets, persisted baseline snapshot references | Job-local before dispatch; independently copied representative-local and scenario-local graphs afterward. Unmodifiable collection views do not make their mutable elements immutable. |
| Comparison entries list, representative map, detail-selection working set, rank map, WorkCounter, avoided/completed counters | Job-local and unsafe for concurrent writes. Coordinator owns these; tasks get local counters. |
| Stage 5C3 partitions, duplicate maps, CountingCache, coverage bookkeeping, Counter, temporary provider schedules | Planning/job-local, mutable, not thread-safe. None is transferred to financial workers. Returned Plan defensively freezes its partition/mapping lists. |
| Stage 5C2 attempted set, snapshots map, temporary extraction maps, session counters | Representative-local mutable state. Never share a LongevityContinuationSession or execute its methods concurrently. |
| Continuation grouping lists/maps during construction | Representative-local mutable builders; returned Path/Group records and copied maps/lists are immutable. |
| Discount-factor map, scenario-outcomes list, expected-value/min/max accumulator variables | Representative-local. Traverse mortality in its existing order and produce one complete aggregate. |
| RetirementPlanScenarioCopyService ObjectMapper and serialization/deserialization buffers | Mutable mapper/cache internals; use representative-local instances, with per-copy buffers. Do not reconfigure or introduce shared mapper state. |
| ProjectionEngine and its dependency graph | Representative-local engine instance. Financial collaborators are stateless or hold only fixed collaborator references/constants; keep them local anyway. Each engine loads its own immutable rules graph. |
| GovernmentRulesRepository mapper, resource stream, Jackson builders | Local to engine construction. Stream closes after load. Rules and rule tables use immutable values/defensive lists. |
| Projection and its growing year list | Scenario/carrier-local mutable state. Does not escape continuation evaluation. |
| ProjectedPortfolio, projected account balances, RMD snapshots, account-RMD results, Roth allocations, ending account snapshots | Value-style containers referencing mutable Account objects; therefore scenario-local, not globally immutable. Account references all belong to that scenario's portfolio. |
| Retained non-qualified balance, tax-funding/bracket-fill iteration values, pension and Medicare calculations | Scenario-local immutable values/local variables. No shared cash balance, tax context, pension accumulator, or iteration cache. |
| SocialSecurityProjectionIncomeProvider financial schedule map; monthly/annual lists, by-year map and temporary EnumSets | Scenario-local builders with immutable output maps/lists/sets. Provider owns no schedule cache. Financial calls pass no equivalence cache. |
| EstateAtSecondDeathSnapshot, weighted outcomes, aggregates, final entries, work snapshots | Immutable values, including account-free retained estate detail. Safe to publish after task completion. |
| Progress listener and supplied cancellation token | Unknown unless the implementation is known; interfaces impose no thread-safety contract. Existing test callbacks include mutable lists/counters. Never call arbitrary external listeners concurrently. |
| Proposed sticky cancellation flag and executor/futures/completion queue | Shared thread-safe coordination state, using defined concurrency primitives. Not financial state. |
| Static currency/percentage NumberFormat instances | Shared unsafe presentation state; described below. They are not invoked by the representative financial path. |

No blocking shared mutable financial state was found. This conclusion applies to the inspected production path and ownership design, not arbitrary injected collaborators or future extensions.

8. Unsafe static state and latent presentation coupling

`util/CurrencyFormatter.FORMATTER`, `ui/util/Formatters.MONEY`, and `ui/util/UIFormatters.MONEY/PERCENT` are mutable static NumberFormat instances and must not be used concurrently. There is a domain-to-presentation access path: `ProjectionYear.getIrmaaBracketDisplay()` calls `IrmaaBracket.getDisplayRange()`, which uses UIFormatters. Representative evaluation calculates numeric premiums and extracts numeric estate snapshots; it does not call those display getters or serialize full Projection objects.

Consequently this does not block Stage 5D's proposed boundary. Keep formatting, full-projection serialization, and UI callbacks out of workers. Concurrent JSON serialization of full projections is not covered by the safety finding. Do not repair this unrelated presentation coupling in Stage 5D without an explicit scope decision.

No production financial static mutable map/list/set, static work counter/cache, shared Random, shared StringBuilder, SimpleDateFormat, mutable financial singleton, or mutable static test hook was found. Static age lists use immutable lists; static date formatters are DateTimeFormatter values. Stage 5C3's static nested CountingCache class creates local cache instances; it is not a static cache instance.

9. Exact plan/account isolation

Isolation occurs at three existing boundaries: the comparison-request constructor JSON-copies the caller's complete RetirementPlan; `newPlanCopy()` JSON-copies that private frozen graph; and each weighted evaluator copies its source plan before its session. Continuation carrier/independent runs additionally call `copier.copy(plan)` before projecting. The standalone weighted strategy request itself holds a plan reference and does not freeze it; it must be supplied an owned copy.

For Stage 5D, create a separate `newPlanCopy()` on the coordinator immediately before each submission. Hand exclusive ownership to one task and retain the existing evaluator/scenario copies. Copying the containing graph, rather than copying account maps independently, preserves the identity relationships required by each financial run: ProjectedPortfolio starts from that scenario's AccountPortfolio; RmdBalanceSnapshot keeps those exact Account references; account lookups use `==`; Roth source and same-owner destination selection use the same projected account list in its existing order.

JSON round trips should not be described as preserving every alias in the caller's graph. An Account referenced both by a Person and by AccountPortfolio can deserialize as separate objects. This is an existing copying characteristic; the financial path obtains account identity from the scenario's AccountPortfolio. Do not replace identity matching with names or merge account objects across copies. RetirementPlanSnapshot also contains reference-based members in the original plan; the enclosing JSON round trip isolates its nested graph. Do not substitute that shallow snapshot for a deep copy.

The resumed in-memory check traversed application fields and collections for two copies and the source, confirming no shared mutable plan/person/household/portfolio/account/collection nodes for the fixture. It verified local RMD account lookup, rejection of a foreign copy's account, mutation isolation between copies, and unchanged comparison results after mutating the original source after request creation. Broader owner/account-shape and nested-baseline fixtures belong in permanent tests.

10. Recommended boundary and architecture options

| Option | Correctness / determinism risk | Complexity / cancellation | Speedup evidence | Memory |
| --- | --- | --- | --- | --- |
| B: separate bounded coordinator around unchanged representative evaluation | Lowest when financial evaluation and ordered assembly remain unchanged | Moderate; independently testable scheduler and cancellation state | Measured representative-level gains above | Bounded active representative state |
| A: executor directly inside comparison service | Also viable, but scheduling mixes with equivalence, retry, ranking and retention rules | Fewer classes, more coupled tests and control flow | Same potential as B | Same bound if implemented carefully |
| C: parallel continuation groups inside a representative | Higher risk to attempted/snapshot state, first failure, scenario order and carrier reuse | High; requires new internal ownership/cancellation design | Unmeasured | Multiple carriers per representative |
| D: nested representative and continuation pools | Highest risk; difficult accounting and failure/cancellation ownership | Highest; oversubscription/deadlock risk | Unmeasured | Multiplicative active state |

Prefer B, then A. Defer C and D. There is no measured reason to change Stage 5C2 internals now. No nested financial executors or parallel streams.

11. Executor and ownership design

Plan sequentially, determine ordered representative groups and detail flags, and evaluate the optional baseline once on the coordinator before dispatch. Construct immutable descriptors containing original order/strategy/value inputs plus an exclusively owned plan graph. Descriptors must not expose the whole comparison request or its frozen plan. Use a fixed ThreadPoolExecutor with an explicit bounded queue; at most W active and W queued tasks is sufficient. Submit new work as capacity becomes available. Do not pre-copy or queue all original strategies.

Each task invokes an unchanged sequential Stage 5C2 evaluator and returns an immutable complete entry plus its immutable work snapshot. Store results by representative/input index, even if a completion queue is used to detect readiness. The coordinator alone updates final maps/lists, reports public progress, sums work, expands original entries, and ranks. Close and await the per-job executor on every success, failure, and cancellation exit. Do not use the common pool or an unbounded fixed-thread-pool queue.

12. Failure semantics that must survive

The comparison loop currently reuses only a successful representative. When a representative fails, every later original member is independently evaluated. A later successful member does not replace the failed representative or authorize reuse for remaining members. This is stricter than copying one task failure to an entire group.

Preserve the existing ordered assembly loop: fetch the representative's stored result and request each required failed-representative member retry independently in original input order, using the same evaluation helper and its own counter. Submit each required retry through the same bounded coordinator/pool and consume it before advancing that original entry. Continue servicing completion events and cancellation while awaiting the retry. Prioritize the required retry when a submission slot becomes available. Do not evaluate retries as an additional financial worker on the coordinator while W workers are active; total financial concurrency must remain at W. Retain original occurrence identity and never promote a retry to representative status.

Preserve the existing RuntimeException classification/message/cause handling. AnalysisCancelledException remains a separate signal. Errors and executor infrastructure failures should retain job-level failure behavior, not become fabricated financial candidate failures. A failed baseline remains order zero, does not prevent candidate evaluation, produces no baseline deltas, and is first in externally visible failures. Candidate failures remain in original order, have no aggregate/detail/rank/deltas, and do not suppress successful candidates.

13. Cooperative cancellation

Current Stage 5C2 checks cancellation before scenario processing, validation/reuse, carrier attempts, extraction/assignment, independent fallback, engine entry/exit, and final return. ProjectionEngine itself accepts no cancellation token and does not check interruption in its yearly loop or the monthly Social Security calculation. A running carrier or fallback must be allowed to finish its current engine call before stopping at the existing boundary.

Use one sticky, thread-safe job cancellation state. The coordinator can poll the external token while waiting and mirror cancellation into an AtomicBoolean; workers see only this safe token. This avoids concurrently invoking arbitrary supplied token implementations. Keep a final external-token check as well as the shared-state check before publishing. If the external token is already explicitly thread-safe, a documented adapter may read it directly. The API must not assume that every lambda closes over safely published state.

On cancellation: stop submission; set the sticky flag; cancel/remove pending queued futures with `cancel(false)`; let active tasks observe the flag at existing boundaries; wait for actual executor termination; discard assembled/buffered values and throw AnalysisCancelledException. A queue/start race is handled by the task's initial cancellation check. Do not mistake `Future.isDone()` after cancellation for proof that running code has stopped. Avoid cancelling running futures just to mark them done; maintain completion/accounting ownership until workers exit.

Use orderly executor shutdown after queue cancellation. `shutdownNow()` and `cancel(true)` add interruption without an interruption-aware financial engine and are not needed. Never use Thread.stop. The coordinator should use bounded waits/polls so it can notice cancellation rather than block indefinitely on the earliest unfinished input. Unwrap ExecutionException: only the actual cancellation signal or confirmed job cancellation means cancellation. An unrelated worker RuntimeException must not be relabeled merely because it came through a Future. If cancellation and failure coincide, cancellation prevents result publication while failure diagnostics can still be retained internally.

The current contract uses boundary checks, including a final check before return. A later UI must also guard publication against a canceled/stale task; progress reaching 100% is not publication authority. Do not return a partial ranking or overwrite a previous successful UI result.

14. Progress recommendation

Preserve existing phases `LONGEVITY_STRATEGY_EQUIVALENCE` and `LONGEVITY_INTEGRATED_COMPARISON`. For the initial parallel implementation, retain the comparison's existing logical unit: original input occurrences finalized, plus the optional baseline. The coordinator advances the contiguous finalized input prefix in original order, emitting the existing 0..N counts. This is deterministic, monotonic, bounded, handles failures naturally, and never double-counts carrier/fallback attempts. It can pause behind an early unfinished representative; completion diagnostics can separately indicate activity.

Representative completion counts are useful diagnostics but should not silently replace the existing total of original occurrences. Engine starts/completions and estimated weighted work have uncertain denominators because fallbacks can add work. Successful logical mortality outcomes can be reported as diagnostics from per-task snapshots, but a failed representative does not finalize all its scenarios; using those alone as a percent denominator would stall completed-with-failures jobs below 100%. Do not fabricate outcomes to fill the bar.

Only the coordinator invokes the supplied progress listener. Worker observers update their own state; optional live immutable snapshots can use one atomic slot per task and coalescing rather than a contended atomic for every engine event. Per-phase fractions never decrease. A future single overall progress bar must use a fixed phase-to-overall mapping established at job start, or show separate phase bars; blindly displaying the next phase's zero as overall progress would go backward. Stage 5D needs no new financial progress API or UI changes.

15. Exact work accounting

Keep the existing observer/latest-snapshot/finally pattern, but one WorkCounter and latest-snapshot holder per evaluation task/retry. Return the last immutable work snapshot even on a failed candidate. Sum completed task snapshots once, sequentially by original evaluation position. Do not sum cumulative observer snapshots repeatedly. Keep baseline and every failed-representative member retry in accounting; only genuinely reused successful members increase comparison evaluationsAvoided.

Measured complete fixture totals, identical in all financial runs:

| Counter | Total |
| --- | ---: |
| Continuation evaluations | 81 |
| Stage 4 evaluations | 0 |
| Positive scenarios / scenarios started / outcomes produced | 210,600 each |
| Carrier attempts / successful carriers | 8,100 each |
| Failed carriers / fallback independent runs | 0 each |
| Independent early-horizon runs | 1,296 |
| Engine starts / completions | 9,396 each |
| Reused mortality outcomes | 209,304 |
| Completed annual rows | 419,580 |
| Original candidate evaluations avoided by equivalence | 5,103 |

Canceled jobs can have scheduling-dependent partial diagnostics because multiple tasks were in flight; they return no completed comparison. Exact sequential final accounting is required for completed jobs, including completed-with-failures jobs.

16. Deterministic assembly and ranking

Each representative retains its existing sequential mortality loop, BigDecimal operations/scales, probability sum, min/max, expected nominal estate, and expected PV estate. There is no shared expected-value reduction. Expand successful representative values using the unchanged immutable proof mapping while constructing each entry with the original candidate object and inputOrder. Preserve baseline order zero, selected details, and failed-member retries.

Use the existing descending expected-PV comparator with inputOrder secondary ordering. Competition ties use BigDecimal.compareTo, with rank equal to the first tied position; successful ties retain original order and rank gaps. Failed entries never enter the ranked list. Compute baseline differences and assemble the failures list sequentially. Never sort by completion time, worker number, strategy hash, or an unordered map traversal. The same complete immutable results imply identical ranking without introducing a new objective.

17. Detail retention

The current policy selects one-based original candidate positions before execution and caps selected candidates plus retained baseline at 20. Map selected original positions to representative positions before dispatch, so an unselected representative still computes detail needed by a selected equivalent member. Attach details only to selected original entries during assembly; successful equivalent entries may share the immutable outcome list. Failed entries retain no detail.

Stage 5C2 already creates every scenario outcome temporarily, even for aggregate-only comparison. Workers must compact unselected results before placing them in completed Futures. Do not queue full LongevityWeightedIntegratedStrategyResult objects for every representative until final assembly. Retained outcomes hold account-free estate snapshots, not full carrier projections. Carrier projections and their account graphs remain local and are released after extraction. The final retention cap does not remove the temporary O(W * scenarios-per-representative) working-set amplification.

18. Focused concurrency checks completed on resume

An in-memory assertion harness verified disjoint mutable copy graphs, local/foreign RMD identity, copy mutation isolation, and frozen-request isolation. It then ran three latch-started rounds with two independent comparison requests using the same service instance and a concurrent deterministic projection on a separate owned plan. The small comparison fixture included duplicate original candidates, a successful baseline, and baseline/candidate detail retention. Every comparison record field matched after excluding elapsedTime fields, including metadata, proof work/mapping, entries, ranks, deltas, details, accounting, and status. Original strategy identities were checked separately. Deterministic ending assets, estate, and withdrawal metrics matched, and the source remained unchanged by evaluation.

These are inspection smoke checks of existing production services, not tests of a production Stage 5D scheduler. They use CountDownLatch rather than sleep assertions. Initial harness-only parsing/reflection issues were corrected in memory before the successful run; no application change was involved. JShell emitted denied Java preferences registry warnings; the successful assertions still ran. No benchmark harness or test file remains incomplete.

19. Permanent test plan before enabling Stage 5D

| Test | Required assertion |
| --- | --- |
| 1. Sequential versus parallel | All externally meaningful result fields equal; explicitly exclude elapsed diagnostics only. |
| 2. Full 81-representative fixture | All 5,184 entries, proof mapping, ranks and exact accounting equal; opt-in long benchmark if needed. |
| 3. Duplicate candidates | Preserve every occurrence and original object identity, including distinct-but-equal strategy objects. |
| 4. Exact ties | Preserve original-order tie stability. |
| 5. Competition ranks | Equal numeric values with differing BigDecimal scales share ranks; retain exact stored scales and rank gaps. |
| 6. Failed representative | Independently evaluate every member; never share its failure or promote a later success. |
| 7. Baseline success/failure | Order zero; candidates continue; correct deltas or absent deltas; correct failure ordering. |
| 8. Carrier fallback | Failed speculative carrier, shorter successful member, original member failure and exact attempt counters. |
| 9. Younger household failures | Real unsupported-age/financial failures preserve the original message/category/cause behavior. |
| 10. Detail retention | Baseline plus candidate limit; selected nonrepresentative; same-group selections; no account-bearing carrier retention. |
| 11. Cancellation before start | No engine starts and no completed result. |
| 12. Queued cancellation | Latch active tasks, cancel queued work, assert unnecessary engine starts do not occur. |
| 13. Active cancellation | Engine/barrier seam around carrier and fallback; stop at next safe boundary; join workers; no publication. |
| 14. Source isolation | Mutate original after request creation; disjoint graphs, owner-specific Roth destinations, RMD identity, retained cash, nested baseline/non-investable graphs. |
| 15. Repeated parallel runs | Exact repeated values/scales/order without timing assertions. |
| 16. Work accounting | Every counter, baseline, retries and fallbacks; no double counting cumulative snapshots. |
| 17. Reverse completion order | Controlled executor/latches force reverse order; ordered entries/ranks/failure order remain equal. |
| 18. Simultaneous failures | Retain correct candidate identity; continue successes; distinguish worker failures from cancellation and infrastructure errors. |
| 19. Concurrent jobs | Same request repeated/concurrent and independent requests; no cache/counter contamination; bounded cleanup. |
| 20. Deterministic plus weighted | Separate owned plans; deterministic financial results and weighted semantic results unchanged. |
| 21. Progress/publication | Serialized callbacks, fixed total, nondecreasing counts, cancellation from final callback, no completed publication after cancellation boundary. |
| 22. Resource bounds | Enforced active/queued limit, executor termination, and compact results; controlled seams rather than fragile heap/timing unit assertions. |

Keep Stage 4, Stage 5A/B, Stage 5C1, and the existing Stage 5C2/5C3 references unchanged. Test Stage 5D versus sequential Stage 5C2/5C3 first, then retain their existing financial oracle comparisons. Run focused tests followed by the full Maven suite after implementation, using IntelliJ's bundled Maven and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.

20. Worker default, limits, and simultaneous jobs

Use `W = min(configuredLimit, 8, max(1, Runtime.availableProcessors()), representativeCount)` for nonempty work; skip executor creation for no representative work. Default configuredLimit to 4, with an internal override permitting 1 through 8. A one-worker reference path must remain available. The limit is internal, not a user-facing setting or a machine-model-specific constant.

Four workers already provide roughly 2.7x financial speedup while using about half this machine's processor capacity and materially less heap under a generous cap. Eight is faster on this fixture but approaches CPU saturation and higher allocation pressure; the 512 MiB tests support its optional use, not an eight-worker default. The maximum of eight reflects the tested range, not proof that larger machines cannot benefit from more workers. Revisit only with broader measurements.

Per-job limits do not constitute an application-wide resource limit: multiple analysis jobs can each create W workers, and existing SS-only analysis has its own parallel paths. Before UI integration, preserve a single active expensive job per dialog and consider an application admission/concurrency budget if simultaneous jobs become a supported product behavior. Do not introduce nested pools or an unsolicited global pool in Stage 5D.

21. Exit decision

Implementation is warranted. The existing deep-copy boundaries and focused graph checks support representative isolation; no unsafe shared financial state blocks that boundary; sequential assembly preserves exactness; existing cancellation boundaries support cooperative shutdown; and repeated measurements show useful speedup with acceptable memory behavior for the tested job. Production readiness remains conditional on the permanent scheduler/failure/cancellation tests, rather than being inferred from successful numeric benchmarks.

22. Exact recommended implementation sequence

1. Add characterization assertions for complete semantic equality, failed-representative member retries, baseline behavior, details, and existing progress/cancellation behavior.
2. Introduce an immutable evaluation result plus work envelope and an owned task-input boundary. Preserve the existing financial evaluation and finally-based accounting. Prove one-worker behavior matches the current service before parallel execution.
3. Add a separately testable coordinator with explicit bounded active/queued work, configurable internal limit, position-indexed results, orderly lifecycle, and no nested execution. Keep Stage 5C3 sequential.
4. Add the cooperative cancellation adapter, bounded completion waits, queued-task cancellation, and final publication guard. Test all failure/cancellation races using controlled executors and barriers.
5. Integrate ordered assembly, failed-member retries through the same bounded pool, baseline deltas, competition ranks, and preselected detail retention. Keep callbacks coordinator-only and preserve logical original-occurrence progress.
6. Run the focused equality, isolation, stress, accounting, and resource-bound tests; then the full Maven suite. Resolve only in-scope implementation defects without altering financial rules or reference semantics.
7. Repeat production-like sequential/parallel measurements with full semantic equality and memory/GC diagnostics, including detail-bearing and failure-heavy fixtures. Confirm the four-worker default and optional higher limits under realistic application heap settings.
8. Enable the internal four-worker default only after these gates pass. Leave UI, persistence, financial rules, Stage 5C3 planning parallelism, and nested continuation parallelism out of the change.

23. UI readiness and architectural concerns

Expose the existing proof and financial phases, serialized logical progress, a cooperative cancellation handle, immutable final baseline/current entry and best completed weighted entry, stable weighted ranking, and optional runtime/work diagnostics. Keep deterministic rank/results separate from the weighted objective. No interim task completion should select or publish a final winner. Preserve the last successful result while a job runs or is canceled.

Before UI work, settle the publication/cancellation gate, callback-thread contract, phase-to-overall progress mapping, resource admission for simultaneous analyses, and the prohibition on worker-side formatting/full-projection serialization. Existing static presentation formatters are a latent concern if that boundary is later expanded. None of these require changing financial calculations or the UI during the initial Stage 5D implementation.
