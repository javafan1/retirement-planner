Stage 5A/5B: exact ordered longevity-weighted comparison
======================================================

LongevityWeightedIntegratedStrategyComparisonRequest deep-copies the source plan at
construction. Its internal plan is never exposed; each execution obtains another
isolated copy. Candidate lists and detail selections are defensively copied. The
complete strategy values, prepared mortality distribution and valuation inputs are
immutable. Live-plan changes after construction cannot change the job or subsequent
executions of the same request. No persisted model or UI state is added.

Every candidate occurrence is evaluated independently through the Stage 4 evaluator.
Duplicate inputs are neither dropped nor grouped. One-based input position identifies
each occurrence, including identical strategy objects; baseline has position zero.
An empty candidate set is valid and performs no candidate work. An optional baseline
is still evaluated even when the candidate set is empty.

The baseline must be a supplied complete strategy. The explicitCurrentStrategy helper
requires the persisted survivor age before calling the deterministic extractor. It
never uses the extractor's representational age-60 fallback. A caller may instead
supply complete independent survivor elections when persisted policy is absent.
Missing baseline means no baseline evaluation or deltas, not an implicit default.
Malformed complete baseline inputs fail request construction; candidate validation
failures remain ordered failure entries. Explicit age 60 itself remains valid when
it is an actual supplied policy.

Successful candidates are ranked by EXPECTED_PV_AFTER_TAX_ESTATE descending, without
rounding. Exact numeric ties preserve input order and receive competition ranks
(1, 1, 3), matching the existing rank convention. Baseline is separate from candidate
ranking. Candidate-minus-baseline PV and nominal-estate differences are available
only when both evaluations succeed. Deterministic AFTER_TAX_ESTATE is unchanged.

Retention
---------
Every successful candidate retains a compact aggregate, including the exact Stage 4
PV, nominal estate, min/max, probability and scenario/run counts. Unselected per-scenario
results are discarded after aggregate extraction. A baseline flag and explicit input
positions may retain full scenario-outcome lists for at most 20 strategies total.
Those lists plus entry aggregates and job metadata preserve Stage 4 financial detail.
No Stage 4 result wrapper or duplicated methodology is stored in each entry.

There is no automatic top-K retention or detail re-evaluation in this stage. Selection
is explicit before execution. During execution Stage 4 still creates one full transient
scenario list; aggregate-only retention bounds the completed comparison, not Stage 4's
temporary allocation. Metadata and financial limitations are stored once per job.

Failures, work counts and cancellation
--------------------------------------
A failed positive-probability scenario fails that strategy. The entry has a failure
message and no aggregate, rank, detail or baseline delta. Other candidates continue.
A baseline financial failure is reported separately and prevents baseline deltas.
No probability is renormalized. COMPLETED_WITH_FAILURES is distinct from COMPLETED.
Ranks in a failed comparison describe successful candidates only.

completedStrategyCount counts successful candidates; failedStrategyCount counts failed
candidates; processedStrategyCount includes both. Baseline is reported separately.
Work counts include baseline, actual Stage 4 invocations, attempted/completed scenarios,
and attempted/completed ProjectionEngine calls, including failures. Stage 4's internal
work observer is accounting only and does not alter its financial behavior.

Public progress has one LONGEVITY_INTEGRATED_COMPARISON phase, completing one unit per
candidate or requested baseline. Failed strategies count as processed progress. Stage
4's mortality progress is suppressed here, while its scenario-boundary cancellation
checks remain active. Cancellation is checked before/after strategies and after final
progress. It throws AnalysisCancelledException; no cancelled or partial comparison is
returned or marked complete. Partial work counts are not published on cancellation.

This is a sequential reference implementation. No equivalence reuse, first-death
continuation, parallelism, exhaustive weighted search or financial approximation is
implemented. The Stage 3.5A household-asset and Stage 4 estate/discounting limitations
remain unchanged. Stage 5C should first prove strategy equivalence against this exact
reference, then address first-death continuation and its failure-preservation contract.
