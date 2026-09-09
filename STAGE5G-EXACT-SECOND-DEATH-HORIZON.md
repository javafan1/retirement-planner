# Stage 5G: exact second-death horizon

## Corrected weighted financial contract

The longevity-weighted objective is EXPECTED_PV_AFTER_TAX_ESTATE: after-tax
investable estate at household second death, in valuation-date dollars.
A modeled January 1 death in year D selects December 31 D - 1 balances.
The required financial projection now ends exactly in D - 1, whether the
persisted plan ends earlier or later. This is an explicit semantic correction.

Stage 4 originally used an extension-only override: max(configuredLast, D - 1).
Stage 5C2 preserved that complete-run failure contract. It therefore evaluated
every scenario with second death inside the configured horizon independently:
post-death one-time expenses could fail after an otherwise available snapshot.
Those unused years are not necessary for the weighted objective. The engine
carries portfolios, RMD snapshots, and RMD history forward; later rows never
revise earlier rows or their estate values. Social Security monthly inputs are
also prefix-local after finite request validation.

All years through D - 1 remain, including the complete survivor interval:
Social Security, pensions, RMDs, conversions, taxes, Medicare/IRMAA, spending,
growth, withdrawals, and retained cash. No financial formula was changed.

## Explicit evaluation context

ProjectionEvaluationContext.HorizonPolicy has three values:

| Policy | Effective end |
|---|---|
| CONFIGURED | Persisted configured end |
| EXTEND_TO_REQUESTED | Maximum of configured and requested end |
| EXACT_REQUESTED | Requested end exactly |

withEndingYear(int) and all existing constructors retain their prior behavior.
withExactEndingYear(int) opts into the new policy. ProjectionEngine.project
resolves the end once using resolveEndingYear and uses that same end for Social
Security preparation and annual iteration. A requested end before the opening
year is rejected; the opening-estate case bypasses projection entirely.
The context is run-only and is not stored in RetirementPlan JSON.

## Opening snapshot and failures

EstateAtSecondDeathCalculator.calculateOpening uses the existing opening
portfolio composition and heir-tax arithmetic. It creates no financial year
and invokes no ProjectionEngine. The existing deceased-owner/already-distributed
opening RMD consistency check was extracted unchanged from OpeningRmdCalculator
so that it still applies without an annual simulation. No government-rule load
or future-year validation is needed for a valid opening-only estate.

Second death before available opening balances is still unsupported. January 1
death before a July opening date is not treated as an opening snapshot.

An expense in 2034 no longer invalidates a scenario whose second death is
January 1, 2031, provided its required 2030 calculations succeed. The same
expense in 2030 remains a failure. Invalid requests, inconsistent opening data,
unsupported required calculations, or funding failures through D - 1 still
invalidate the scenario. Cancellation is still cancellation, not a partial value.
This can turn previously failed weighted candidates into successes and thereby
affect the available ranking; it does not change successful prefix arithmetic
or the ranking comparator.

## Independent reference and continuation

LongevityWeightedIntegratedStrategyEvaluator remains independent and sequential:
one exact projection per positive non-opening mortality scenario, with direct
opening snapshots. It remains the reference for financial continuation.

LongevityScenarioContinuationPlanner now groups every supported positive
non-opening scenario, without a configured-end exclusion. Paths are primary-first
plus first-death year, spouse-first plus first-death year, or simultaneous death.
Each carrier is the latest actual member in that path, with stable tie selection.
LongevityContinuationSession validates the member and carrier finite Social
Security keys and applies the existing exact LongevityEquivalenceCoverage.covers
guard before reusing scalar estate snapshots. No heuristic strategy grouping or
synthetic survival date was introduced. Only immutable keys and scalar snapshots
remain in the session; projected account graphs stay local to each engine call.

A failed longest carrier discards its speculative snapshots. Members run
independently in original order through their own exact horizons. A short
member can succeed even when a later required year makes the carrier fail.
Unavailable finite-request coverage also falls back independently.

The Stage 5C1/5C3 strategy planners and their configured/extended proof horizons
are unchanged. Full successful annual-schedule equality is a conservative proof
of equal required prefixes for supported complete strategies. Invalid/unproven
inputs remain isolated. Stage 5G does not target the observed 1,782 groups.
Reducing conservative proof work or representatives is a possible later stage.

Stage 5D remains default 4 workers, hard cap 8, bounded submission, exclusive
copies, ordered collection, and cooperative cleanup. No coordinator redesign.

## Probability and accounting

Original scenario order, positive probability, zero-mass skipping, multiplication
order, summation order, and BigDecimal scales are unchanged. Reused snapshots
still produce one outcome per original positive scenario. There is no dropping
of failed probability or renormalization.

The independent evaluator counts actual engine invocations rather than deriving
them from outcome count. Opening snapshots count as scenarios/outcomes and zero
engine calls. Continuation counts carrier attempts, independent fallbacks,
started/completed engine calls, and completed annual rows separately. Successful
reused carrier/member snapshots count as reused outcomes; opening snapshots do
not. independentEarlyHorizonRuns is retained for diagnostic compatibility and
is always zero under Stage 5G. Existing comparison aggregation avoids counting
continuation subtotals twice.

For completed successful work, engine starts = carrier attempts + independent
fallbacks. A failing projection increments starts but not completed projections.
An opening-only run has outcomes but no carriers, fallbacks, or engine starts.

## Exactness tests and performance evidence

Eight characterization cases were added and passed before production changes.
They compare the SAME deaths under the old configured/extended projection and
a test-only copied exact horizon. The permanent test also compares the new exact
context to that oracle. Cases cover both death orders, simultaneous death,
multi-year survivor intervals, inside/boundary/beyond configured end, partial
opening, fixed conversions, and bracket fill. Complete rows, raw BigDecimal
accessors, account balances, Social Security records/year keys, estate, and PV
are compared exactly; source JSON and deterministic projections are unchanged.
Existing prefix tests additionally exercise RMD stop history, conversion
shortfall, tax funding, and retained cash.

A controlled 8 x 8 positive mortality grid entirely inside a 20-year configured
horizon now uses 15 engine calls (7 primary-first + 7 spouse-first + 1 simultaneous)
instead of 64 independent calls. Every outcome exactly matches the independent
reference. There are no early independent runs or fallbacks in this fixture.

A separate geometry-only test enumerates 56 x 58 mortality years with a common
starting year: 3,248 scenarios, a 35 x 35 = 1,225 early region, and 112 total
paths (56 primary-first + 55 spouse-first + 1 simultaneous). It runs no financial
projection. This is a controlled model of the inferred production geometry,
not a replay or verification of the user's captured plan.

Observed production: 2,382,534 engine runs. Holding 1,782 representatives and
assuming 112 successful carrier paths gives 199,584 calls, approximately 91.62%
fewer (11.94 times fewer calls). This is an estimate, not a guaranteed bound or
runtime measurement: carrier failure, validation fallback, proof planning,
longer carrier rows, and aggregation still matter. No full weighted exhaustive
analysis or opt-in benchmark was run for Stage 5G. Historical benchmark logs
were not rewritten. Benchmark source assertions were updated for the corrected
contract but those benchmark classes were not executed.

## UI, persistence, and limitations

Only weighted methodology text and result metadata changed in Stage 5F.
The methodology version is STAGE5G_EXACT_SECOND_DEATH_V1 and links to this report.
There is no new user-selectable horizon option, UI redesign, or deterministic
horizon change. Request capture, baseline availability, occurrence identities,
ranking, and result retention remain unchanged.

Persisted plan horizons, account balances, JSON schema, and dependencies are
unchanged. Non-investable assets remain excluded; retained cash and the existing
heir-tax haircut retain their existing treatment. Inherited-account retitling,
beneficiary distribution rules, and estate settlement remain unmodeled. The
same source-validation and supported-cohort boundaries remain in force.

## Verification

IntelliJ bundled Maven was used with the repository .codex-m2/repository, one
Maven process at a time, -XX:ActiveProcessorCount=2, software JavaFX rendering,
and target/javafx-cache. Production worker limits were not changed.

Before behavior changes: 8 prefix characterization tests passed.
Initial focused reference group: 62 tests passed.
Stage 4/5C2/5D/5E/5F and analyzer reference group: 311 tests, zero failures/errors,
2 skipped. This ordinary reference group includes existing deterministic search
and single-strategy distribution correctness tests that print legacy timing
labels; no 5,184-strategy weighted search or opt-in benchmark was enabled.
Final Stage 5G focused tests: 22 passed. A missing import in the additional
geometry test was corrected before that successful run.

One complete non-benchmark Maven suite passed: **1,059 tests, zero failures,
zero errors, 3 skipped**, BUILD SUCCESS, 50.427 seconds. No code changed after
this run; only this verification report was completed.

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' `
  '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' `
  '-Dtest=!*BenchmarkTest' `
  '-DargLine=-XX:ActiveProcessorCount=2 -Dprism.order=sw -Djavafx.cachedir=C:\Users\david\IdeaProjects\retirement-planner\target\javafx-cache' test
```

Final git status, diff --check, diff --stat, and production diff review completed.
Whitespace checks pass. There are 4 added and 21 modified files listed below;
no logs, debug instrumentation, unrelated changes, commits, or persisted plan
changes. Benchmark sources compile but their corrected run-count assertions
were not benchmarked. Existing extension-only and deterministic tests pass;
successful weighted prefixes retain exact financial values. The intentional
weighted difference is success when only unneeded post-snapshot years would fail,
plus corrected engine-call counts and methodology metadata.

Stage 5G is ready for a controlled user acceptance run. The user's exhaustive
job was not replayed, so production runtime and exact representative/path counts
remain unmeasured. Inspect captured equivalence membership and phase timings
next before choosing any separate representative-equivalence optimization.

## Changed-file inventory

Added: this report and Stage5GPrefixCharacterizationTest.java,
Stage5GExactHorizonTest.java, Stage5GContinuationTest.java under
src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/.

Modified production files under src/main/java/com/daviddunn/retirementplanner/:

- domain/projection/ProjectionEvaluationContext.java
- domain/projection/ProjectionEngine.java
- domain/estate/EstateAtSecondDeathCalculator.java
- domain/rmd/OpeningRmdCalculator.java
- app/socialsecurity/LongevityWeightedIntegratedStrategyEvaluator.java
- app/socialsecurity/LongevityWeightedContinuationEvaluator.java
- app/socialsecurity/LongevityContinuationSession.java
- app/socialsecurity/LongevityScenarioContinuationPlanner.java
- app/socialsecurity/LongevityContinuationWork.java
- app/socialsecurity/LongevityWeightedIntegratedStrategyComparisonService.java
- ui/socialsecurity/LongevityWeightedIntegratedView.java

Modified test files under src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/:

- LongevityContinuationTest.java
- LongevityWeightedIntegratedStrategyEvaluatorTest.java
- LongevityWeightedIntegratedStrategyComparisonTest.java
- LongevityContinuationBenchmarkTest.java (not executed)
- Stage5DRepresentativeBenchmarkTest.java (not executed)

Updated durable rules in AGENTS.md and current-contract annotations in
STAGE4-LONGEVITY-EVALUATION.md, STAGE5C2-FIRST-DEATH-CONTINUATION.md,
STAGE5C3-SS-PREFIX-COVERAGE.md, and STAGE5F-LONGEVITY-WEIGHTED-UI.md.
