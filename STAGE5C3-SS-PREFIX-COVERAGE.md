# Stage 5C3: guarded Social Security prefix coverage

## Scope and resumption

Stage 5C3 optimizes only the proof of longevity-weighted strategy equivalence.
It does not change Social Security arithmetic, ProjectionEngine, Stage 4,
Stage 5A/5B, or Stage 5C2 financial continuation behavior. There is no UI,
persistence, parallelism, mortality sampling, general checkpoint, or inherited
account change.

On resumption, the three new production classes, provider validated-key adapter,
comparison integration, four test classes, and three verification logs existed.
The code compiled. Characterization had passed 14 cases; focused Stage 5C3 had
passed 46 cases; Stage 5C1/provider/Stage 5C2 references had passed 69 cases with
two opt-in skips. No truncated edits or temporary production debug code were
found. The resumed work preserves that implementation and finishes full-suite
verification, the full-universe oracle/benchmark, and this document.

## Reference relationship

`LongevityWeightedStrategyEquivalencePlanner.plan` retains its original Stage
5C1 processing loop. Its result record adds optional Stage 5C3 work metadata;
the original constructor remains available and supplies an empty optional.

`LongevityWeightedPrefixEquivalencePlanner` is the separate optimized planner.
Normal comparison uses it and then the unchanged Stage 5C2 evaluator.
`compareWithEquivalenceOnly` still uses the Stage 5C1 planner and independent
Stage 4 representative evaluations. `compareExact` remains the Stage 5A/5B
independent strategy oracle. No financial calculation path has been replaced.

## Why a longer-lived cache was rejected

Inspection enumerated all 13,478,400 older-fixture logical requests using the
existing exact schedule key. There were 210,600 distinct keys and zero additional
cross-scenario hits. Keys include the effective deaths and finite horizon; an
out-of-horizon later death is still distinguished by the required end year.
Keeping every schedule would retain 7,270,155 annual rows without avoiding
calculations. Stage 5C3 instead reduces redundant proofs and keeps caches local
to the current scenario.

## Carrier selection and conservative boundary

`LongevityEquivalenceCoverage` builds immutable planner-local index assignments.
It includes only actual positive-probability scenarios whose second death is
after the configured final projection year. Paths distinguish primary-first
plus first-death year, spouse-first plus first-death year, and simultaneous
deaths. Each path chooses the latest actual second-death member. Ties retain
the first original occurrence. All assignments retain original scenario indices.

The simultaneous path uses its latest actual simultaneous member. No synthetic
both-alive scenario or synthetic terminal death is introduced. All mortality
dates continue through the existing mapper to January 1 of their modeled year.

If second death is within the configured horizon, the member is independent for
this conservative Social Security proof planner (not Stage 5G financial execution).
Its full schedule includes post-second-death years; Stage 5C3 does not discard
those years or infer their values from a longer-lived carrier.

## Validation before coverage

The provider's `validatedKeyForEquivalence` executes the same existing source
selection, election construction, override validation, effective death clipping,
survivor-election applicability, and `SocialSecurityStrategyRequest` constructor
as ordinary authoritative generation. It returns a key only after successful
finite-request validation. It does not calculate benefits.

For a potentially covered member, the planner validates both that member and
its carrier for every participating distinct candidate. Carrier validation is
additional work, never a replacement for member validation. As in Stage 5C1,
already-singleton partitions need no further equivalence proof and identical
candidate inputs can share successful request work. Invalid complete candidates
remain separate singleton positions.

The original provider calculation and `validateForContinuation` branches retain
their behavior. There is no planner-specific annual-only output path.

## Exact prefix proof

Both keys must originate in successful provider validation. The guard requires:

1. The same January 1 start and complete December 31 ends, with carrier coverage
   at least as long as the member.
2. Exactly equal owner elections, benefit values, valuation years, claim dates,
   and COLA, including BigDecimal scale.
3. Equal effective owner deaths over the member interval. A carrier death after
   the member end is absent in the member's finite request.
4. Equal survivor entitlement months whenever entitlement can pay in the member
   interval. A null/inapplicable entitlement is ignored only when the other
   request's entitlement also cannot pay during that interval. An entitlement
   before the opening year retains its original month, because that month can
   affect the reduction factor.

These conditions preserve each month's alive state, own/spousal eligibility,
worker history, and survivor entitlement inputs. The same authoritative monthly
arithmetic therefore produces identical monthly values. The same ordered annual
aggregation produces identical annual records and exact BigDecimal scales.
The provider still returns January-December SS results when the financial plan
opens partway through a year.

No schedule prefix map is constructed in production. For a compatible member,
its refinement is deferred to the actual carrier's original scenario position.
The carrier is itself a required positive-probability scenario, so any difference
in its complete schedule is a legitimate distinction. Equality of that complete
schedule proves equality of all compatible member prefixes. If another required
scenario already isolates a candidate, no additional proof is necessary.

Thus replacing covered refinements with carrier refinements preserves the final
partition in both directions. The planner still visits scenarios in original
order, and final groups and representatives retain original input positions.

## Failures, cancellation, and isolation

If any member/carrier validation or compatibility check cannot establish the
proof, the entire member goes through original Stage 5C1 schedule generation
and exact refinement. A partially proven member never supplies partial grouping.
The test-only guard seam can reject additional coverage but cannot authorize a
proof rejected by the production guard.

Carriers are calculated only at their own original positions, not speculatively.
A carrier calculation failure isolates its candidate at that required scenario,
as the reference does. It is not assigned as another member's financial failure.
No failed schedule enters a cache. The comparison service retains its original
independent treatment of failed financial representatives.

Cancellation is checked during coverage construction, between partitions and
candidates, before calculation/fallback, at progress boundaries, and while
building final representative assignments. No partial equivalence result is
published. The request supplies a frozen plan copy before progress callbacks;
the source plan, candidate identities, mortality inputs, and persisted horizon
remain unchanged.

Both-dead rows come from the authoritative provider on independent paths. No
generic zero objects or scale normalization are introduced. Map and record
equality still use BigDecimal.equals. No cached hashes, canonical IDs, annual-only
output, or year-specific memoization are implemented in this pass.

## Work accounting and memory

`LongevityEquivalencePlanningWork` is separate from financial continuation work:

- Logical schedule requests count participating candidate requests, with
  duplicate reuse on successful paths; deferred refinements can leave partitions
  non-singleton longer than in Stage 5C1.
- Member validations count actual provider validation attempts, including a
  second attempt when an unproven member is sent through reference processing.
- Carrier compatibility validations count additional finite carrier validations.
- Carrier and independent calculations count authoritative starts at validated
  cache misses. Failed request counts include reference-path validation or
  calculation failures. Annual rows count successfully cached schedules only.
- Covered scenarios and avoided calculations count accepted member proofs and
  distinct member schedule keys whose calculation was omitted. They are local
  work counters, not a universal prediction of the reference's singleton pruning.
- Coverage groups count eligible mortality paths; fallback scenarios count members
  whose proof was unavailable and which re-entered reference refinement.

The counting cache relies on the provider's post-validation `containsKey` miss
immediately preceding calculation; this relationship is a maintenance contract.
Caches and validation-key sets are discarded at each scenario boundary. There
is no job-wide cache of annual schedules or monthly results.

## Permanent tests and benchmark methodology

Characterization compares every annual record and both derived selected-owner
benefits, including scales, for primary-first, spouse-first and simultaneous
prefixes, younger/older households, January/partial openings, retirement ages
62/67/70, survivor ages 60/67, and multiple second-death years. Independent
early-horizon suffixes explicitly demonstrate why generic zero rows are unsafe.

Validation tests compare exception type/message with the authoritative provider
for missing policy, invalid finite range, death before birth, invalid elections,
and unsupported owner/source shape. They cover omitted invalid elections which
are valid in a shorter request but fail in the carrier. Guard tests reject
different starts, insufficient ends, death changes, COLA scales, elections, and
active entitlement months.

Partition tests compare complete groups and representative arrays, with duplicate
positions, across older, younger, early, survivor-sensitive, simultaneous,
partial-opening, extended-horizon, and reversed-order fixtures. They also cover
forced unavailable coverage, invalid candidate singletons, zero-probability
carriers, tie ordering, empty input, cancellation, immutability and live edits.

The opt-in `LongevityPrefixEquivalenceBenchmarkTest` warms both complete planners,
measures the reference, then runs the optimized planner three times. Every run
compares the complete 5,184-position partition, not only its group count. The
normal comparison service then runs one further optimized proof and financial
evaluation of the resulting representatives, checking every original entry's
identity, order, aggregate sharing and rank. It never evaluates 5,184 independent
weighted financial strategies.

Allocation uses ThreadMXBean cumulative allocation for the executing thread.
Baseline annual rows are derived from this fixture's independently enumerated
uniform effective-key count; optimized rows are counted at successful cache puts.
Financial time from the service includes result assembly and small setup overhead.

## Results

Verification completed September 8, 2026, using Java 25 and IntelliJ bundled
Maven. The benchmark used a 1 GB maximum heap and full-job warmups for both paths.

| Older full-universe planner | Stage 5C1 | Stage 5C3 |
| --- | ---: | ---: |
| Input strategies | 5,184 | 5,184 |
| Exact equivalence groups | 81 | 81 |
| Logical schedule requests | 13,478,400 | 13,478,400 |
| Original member validations | Provider on every logical request | 13,478,400 |
| Additional carrier compatibility validations | 0 | 12,877,056 |
| Authoritative calculations | 210,600 | 9,396 |
| Carrier calculations | N/A | 8,100 |
| Independent calculations | 210,600 | 1,296 |
| Annual rows | 7,270,155 (fixture-derived) | 419,580 (counted) |
| Planning runtime | 107.012 s | 12.702 / 12.421 / 12.811 s |
| Cumulative allocated bytes | 508,876,385,384 | 71,139,708,392 / 71,133,699,552 / 71,133,699,088 |

Every measured optimized run compared the entire group list and representative
array against the same warmed exact reference. All 5,184 positions matched,
including group member ordering. The end-to-end run also matched that partition
and checked every original candidate identity, position, shared aggregate and rank.
No grouping or deterministic-output differences were found in verification.

The fixture yielded 100 carrier paths and 16 independent early-horizon scenarios.
Coverage omitted 2,484 member calculations per effective strategy, for 201,204
avoided authoritative calculations (95.54%). There were zero fallback scenarios
and zero failed schedule requests. Counts are derived from fixture inputs rather
than hard-coded in production. Median planner speedup was 8.42x. Cumulative
allocation fell approximately 86.02%; these are allocated bytes, not retained heap.
No peak-live-heap measurement is claimed.

The normal service end-to-end run measured:

- Planning: **12.844 s**.
- Stage 5C2 financial execution plus result assembly/setup: **23.374 s**.
- Combined: **36.218 s**.
- 81 representative continuation evaluations, 9,396 successful ProjectionEngine
  runs, 210,600 completed mortality outcomes, and no financial fallbacks.

No 5,184-strategy independent financial search was run. The approximately
23-second non-planning portion includes small assembly/setup overhead and is
not claimed as an isolated engine-only timer.

| Verification | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Initial prefix characterization, before production changes | 14 | 0 | 0 | 0 |
| Focused Stage 5C3, including characterization | 46 | 0 | 0 | 0 |
| Stage 5C1/provider/Stage 5C2 references | 69 | 0 | 0 | 2 |
| Complete suite | 952 | 0 | 0 | 6 |
| Separate full-universe oracle/benchmark | 1 | 0 | 0 | 0 |

Every final verification command finished with BUILD SUCCESS. The complete suite
adds 47 tests to the 905-test baseline; its additional skip is the full-universe
benchmark, executed successfully separately. Two initial validation test fixtures
were corrected because their age metadata was rejected before reaching the
provider; no production rule or oracle assertion was weakened.

The resumed session needed no further production changes. It completed the full
suite, mandatory full-partition benchmark and three warmed measurements,
end-to-end evaluation, documentation, and final diff review. All earlier correct
partial work was preserved. No commits, resets, reverts or discards were made.

## File inventory

Added production files under `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/`:

- `LongevityEquivalenceCoverage.java`: mortality assignments and validated-key prefix guard.
- `LongevityEquivalencePlanningWork.java`: proof-only work counters.
- `LongevityWeightedPrefixEquivalencePlanner.java`: optimized, ordered refinement.

Modified production files:

- `app/socialsecurity/LongevityWeightedIntegratedStrategyComparisonService.java`: select the prefix planner for normal comparison.
- `app/socialsecurity/LongevityWeightedStrategyEquivalencePlanner.java`: optional work metadata on its result; original processing unchanged.
- `domain/projection/SocialSecurityProjectionIncomeProvider.java`: expose a key after existing finite validation.

Added tests under `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/`:

- `LongevityEquivalencePrefixCharacterizationTest.java`
- `LongevityEquivalenceValidationTest.java`
- `LongevityWeightedPrefixEquivalenceTest.java`
- `LongevityPrefixEquivalenceBenchmarkTest.java`

Also added this document and `stage5c3-characterization.log`, `stage5c3-focused.log`,
`stage5c3-reference.log`, `stage5c3-full.log`, and `stage5c3-benchmark.log`.
No pre-existing tests, dependencies, build configuration, UI, persistence, SS
arithmetic, or Stage 5C2 financial implementation files were modified.

Verification uses IntelliJ's bundled Maven and
`-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.
The separate benchmark adds `-DargLine=-Xmx1g -Dstage5c3.fullBenchmark=true
-Dtest=LongevityPrefixEquivalenceBenchmarkTest` to `test`. These are invocation
options, not changes to project build configuration.

## Maintenance risks and next stages

The proof depends on the authoritative calculator remaining prefix-local: future
death/horizon changes must not alter earlier monthly amounts. Any new survivor,
monthly eligibility, rounding, or lifetime-validation behavior needs renewed
prefix and failure characterization. Finite provider clipping and applicability
must stay the validation authority. Moving or replacing the cache lookup requires
updating work instrumentation.

Before UI integration, expose separate proof and financial progress and retain
the current cancellation/frozen-input boundaries. Scenario completion reports
validated/deferred work; it does not publish an intermediate proven partition.
No UI integration is included here. Parallelism and secondary planner optimizations
should be judged against measured end-to-end latency, not assumed necessary.
At 12.4-12.8 seconds of planning, no secondary planner optimization is warranted
for this pass. Stage 5D is now a reasonable separately scoped benchmark of bounded
parallel representative financial evaluations: the financial portion is dominant.
It is optional, not a prerequisite for read-only background UI integration.
