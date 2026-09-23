# Longevity-weighted Current Strategy baseline

The Longevity-Weighted tab contains an expanded, compact **Current Strategy
Baseline** section above its run action. Its two retirement ages are read-only;
its two survivor ages are optional analyzer inputs. This section controls the
comparison reference, not the exhaustive candidate universe.

| Election | Initial source |
| --- | --- |
| Primary retirement | Primary-owned SocialSecurityIncome claiming age and start date |
| Spouse retirement | Spouse-owned SocialSecurityIncome claiming age and start date |
| Primary survivor | Plan survivor age only when Spouse Dies |
| Spouse survivor | Plan survivor age only when Primary Dies |

Both Survive supplies neither survivor election, even if a dormant survivor age
remains persisted. Death year is never an election in this baseline. Longevity
scenarios continue to supply their own deaths.

The analyzer supplies the missing opposite-death survivor policy. No age 60,
FRA, or other default is invented. Either survivor input may be edited, including
a plan-derived value. Compact source labels distinguish Plan, Plan death scenario,
Analyzer, and Analyzer override. Returning to the plan-derived value shows its
plan source again.

## Validation and session lifetime

A baseline requires both correctly owned retirement records with supported ages
62–70 and internally consistent claim dates, plus both valid survivor elections.
The survivor inputs accept whole years 60–70: the advanced survivor engine's
earliest age through the existing plan's supported late-election range. Survivor
dates use each person's exact birthday, matching persisted age-policy semantics;
the existing earliest-survivor-date validation also applies. The candidate universe
still uses its authoritative whole-year and exact survivor-FRA candidates.

Missing or invalid fields receive specific messages. They omit the optional
baseline rather than preventing an otherwise valid candidate analysis. An
unsupported household still follows the existing overall analysis validation.

Edits are held only while this analyzer dialog is open, consistent with its other
session inputs. Refreshing the same plan retains edited survivor fields and
rederives unedited fields. Switching to a different plan resets all survivor
inputs and derives the new plan's applicable election. Closing and reopening
creates a fresh dialog and rederives initial values. No analyzer-specific
persistence or cross-dialog cache was added.

## Results

Difference vs Current remains:

`candidate expected PV after-tax estate - baseline expected PV after-tax estate`

The backend uses BigDecimal subtraction without intermediate currency rounding.
Positive differences mean greater PV than current; negative differences mean
lower PV. Currency formatting is display-only. The current card shows all four
elections and sources, Expected Investable Assets at Second Death, Expected
After-Tax Heir Value, Expected PV After-Tax Estate, rank/position, and Difference
from Highest Strategy (current PV minus highest candidate PV).

An exact candidate match uses its existing backend competition rank. The backend
continues its established separate baseline evaluation; it does not deduplicate
the baseline against candidates. Identical successful elections reconcile to zero
PV difference. A noncandidate baseline is not inserted into candidate rankings:
its labeled **Metric position among tested strategies** is one plus the number
of successful candidates with strictly greater PV. This is presentation-only,
not a manufactured backend rank.

Incomplete baselines display known elections and specific missing/invalid fields,
without financial values. A separately failed baseline displays its failure and
retains successful candidate results; deltas remain unavailable. No zero value
is substituted for an unavailable baseline.

## Revisions and scope

Starting weighted work freezes the isolated plan, four baseline elections,
completeness/validation and source labels, source revisions, mortality assumptions,
valuation date and rate. The complete universe is generated from the frozen plan
under admission control. Subsequent edits cannot alter that request.

Survivor baseline edits invalidate only WEIGHTED_SETTINGS. The previous weighted
result and result-time baseline summary remain visible and marked stale, while
Stage 5E publication guards reject the old in-flight result. The whole weighted
request reruns; no partial comparison refresh was introduced. SS-only, Quick
Comparison, and deterministic results are unaffected by these edits.

The shared input matrix distinguishes candidate survivor elections from these
weighted-only baseline inputs and explains which owner survives each deterministic
scenario. The input summary includes four current elections, sources and baseline
completeness.

Deterministic plan death behavior, death year, retirement records, financial
formulas, PV precision, candidate generation, ranking, Stage 5C equivalence,
Stage 5D defaults and Stage 5G horizons are unchanged. Existing headless explicit
shared-policy APIs retain their contract; the UI supplies its own complete
baseline instead of opting into a shared-policy assumption.

## Verification (2026-09-11)

- Focused baseline/request/presentation/input/state/layout/controller classes:
  97 tests passed (14 baseline, 7 request factory, 7 weighted presentation,
  4 input presentation, 26 dialog state, 36 job controller, 3 weighted layout).
- Stage 5F/G references: 27 tests passed. All named Stage 5 non-benchmark
  reference classes in the complete run: 65 tests passed.
- Complete non-benchmark verification: 1,214 tests, 1,211 passed, 3 skipped,
  zero assertion failures or test errors in the final reports, across 187 classes.
  Skips are two opt-in personal-plan audits and the pre-existing disabled full
  SS mortality benchmark.

All Maven commands used IntelliJ's bundled Maven and the required repository-local
`.codex-m2/repository`. JavaFX used software rendering and a cache under `target`;
settings tests used `target/baseline-test-home`. Financial weighted comparisons
used at most two effective workers via `-XX:ActiveProcessorCount=2`; existing
synthetic coordinator tests retain their explicit concurrency test seams.

The suite selection was `*Test,!*BenchmarkTest,!LongevityWeightedProductionDistributionTest,
!IntegratedSocialSecurityCompleteStrategySearchCalculatorTest#fullSearchEvaluatesIndependentUniverseRanksAndRetainsOnlyTopDetails`.
The latter two exclusions contain embedded performance measurements. No complete
5,184-strategy weighted financial evaluation was run.

The first suite process exceeded its 768MB heap in the existing production-mortality
audit-trail class after 784 tests. Only unfinished classes were resumed, one JVM at
a time; that same class also exceeded 2GB. After checking available physical memory,
the isolated class passed with a 4GB heap ceiling (3 tests passed, 1 disabled).
Completed classes were not rerun to obtain the aggregate suite totals above.
No build configuration or production memory/worker defaults were changed.

Final status/diff review found no changes to financial or persistence code in this
task, no plan writes from analyzer inputs, and no new untracked logs or debug files.
Existing staged work and the preceding tooltip enhancement were preserved.
