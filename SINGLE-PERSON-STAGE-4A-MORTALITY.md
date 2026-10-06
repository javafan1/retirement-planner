# Stage 4A - single-person mortality and weighted analyzers

Starting HEAD: `00bbc51 Add single-person Social Security support`.
Initial `git status --short`: empty; index and worktree clean.

## Read-only audit and implementation boundary

The Stage 1-3 reports were read before production changes. The existing individual
`SocialSecurityMortalityDistributionProvider` is already cardinality-independent.
It conditions survival to 1, starts with the next complete birthday interval,
multiplies remaining survival by adjusted qx using DECIMAL128, and assigns all
remaining mass to terminal age 120. That final residual is an existing explicit
terminal convention, not a new normalization. Adjustment is
`1 - (1 - qx)^factor`: above 1 increases mortality; below 1 decreases it.

`AnalyzerLongevityAssumptions`, `HouseholdLongevityScenarios` and its factory are
couple contracts. They require two marginal distributions and construct an exact
Cartesian product. They should remain intact for couple consumers. A separate
individual prepared value will reuse the same provider and expose real death-date
scenarios and survival, without any spouse component.

SS-only uses birthday death dates, excludes the death month, starts remaining
benefits at conditioning, deflates by SS COLA in calendar years, then discounts
monthly at the real rate. Integrated weighting coarsens deaths to January 1 of
the death year and takes the preceding December 31 investable estate (or matching
opening estate). Its current ranked metric is expected PV after-tax investable
estate, excluding non-investable property; it is not a weighted version of every
deterministic metric. It uses general inflation and ACT/365.25 real discounting.
These distinct timing conventions must remain explicit.

Stage 4A will reuse authoritative monthly own-benefit calculation, PV helpers,
ProjectionEngine, and estate snapshot/PV calculations. Single results need no
couple survivor fields or second-death labels. The single view will retain the
Stage 3 deterministic analysis and add mortality-weighted nine-row results using
the existing background job controller. Conditioning and valuation remain
independent. Person remains mortality-category authority.

Couple continuation/prefix/equivalence caches encode paired death dates and
survivor elections. They remain untouched; single evaluation can execute one
projection per positive individual scenario without a Cartesian product. Existing
weighted request/result/report types remain explicitly couple-shaped; a dedicated
single input/result path avoids fake spouse data and changes to those caches.

Monte Carlo is audit-only: its existing SHA-256 protocol samples one joint outcome
using HOUSEHOLD_MORTALITY dimension 1, not two independent person draws. Reserved
PRIMARY_MORTALITY dimension 2 can support a later versioned single-person draw.
No random protocol, generator, worker, percentile or paired reducer changes belong
here. Single Monte Carlo admission guards and single PDF guards remain.

## Implemented foundation and mathematical relationships

`IndividualLongevityScenarios` wraps the existing immutable individual provider
result and its captured request/table metadata. There is no spouse field, joint
probability, survivor election or fabricated Person. The household factory rejects
couples and reads the primary Person category; absent category fails clearly.
Ordered death ages become actual birthday death dates. Invalid ordering or death
on/before conditioning is rejected. Individual probabilities must already total
exactly one through the existing distribution constructor.

For successive modeled intervals, `d_x = S_x * adjusted_qx`, then
`S_(x+1) = S_x - d_x`. `S_start = 1`. At the terminal age the provider assigns
`1 - sum(nonterminal d_x)`. Thus all death masses total exactly one as BigDecimal;
there is no new rescaling or lost residual. `survivalAt(date)` sums death outcomes
strictly after that date. SS receipt probability instead sums outcomes whose death
MONTH follows the payment month, preserving death-month exclusion.

The provider, table data, adjustment formula, partial-year convention, joint
factory and couple probability calculation were not modified. Factor 1 takes the
provider's exact identity branch. Existing fractional hazard exponentiation alone
uses StrictMath; all distribution accumulation and monetary weighting remain
BigDecimal. Tests establish bounds, monotonicity, exact conservation, factor
sensitivity, birthday/terminal behavior and repeated equivalent scenario lists.
The terminal-age 119 conditioning case produces one death-at-120 outcome of mass 1;
conditioning at 120 is rejected as before.

## Social Security Only

`SinglePersonMortalityAnalysis` exposes separate SS-only and integrated modes.
The SS-only mode generates the existing nine primary-only elections, ages 62-70.
It calls the new own-monthly stream entry point on the existing authoritative
`SocialSecurityStrategyCalculator`; preparation, claiming adjustment, cent rounding
and economic COLA all reuse existing private calculation methods. No couple path
or survivor formula was edited. The public entry point performs no optimization.

Expected PV is the sum over payment months of the existing real/PV monthly value
times that month's individual receipt probability. This is the linear expectation
of the same mortality-weighted lifetime benefit concept used for couples, not a
configured-horizon substitute. Nominal expected benefits also remain available.
Benefits begin at the conditioning month, independent of valuation date. SS COLA
deflates calendar-year nominal amounts; monthly real discounting uses the existing
high-precision helper. Display formatting alone rounds dollars.

The existing per-scenario SS valuation horizon guard is also preserved: a
valuation month after any positive-probability lifetime horizon is rejected rather
than silently allowing a request rejected by the couple valuation path. Integrated
estate valuation retains its separate existing date conventions.

An independent FRA fixture sums each possible lifetime's monthly benefit stream,
then weights those scenario totals. It computes the known FRA $3,000 amount,
annual COLA/cent rounding and calendar-year deflation directly, without calling
the production valuation helper. At zero real discount its expected PV agrees
within 1E-20 dollars. Separate nonzero-discount tests verify discount sensitivity,
longevity sensitivity, stable ranking and current/maximum identification. No
particular claiming age is assumed to be universally optimal.

## Longevity-Weighted Integrated

The same individual distribution feeds nine deterministic full-plan election
evaluations. Each positive-probability death birthday maps to January 1 of its
calendar year, exactly as the couple weighted analyzer maps death years. The
projection context ends EXACTLY at death year minus one, independently of the
configured plan horizon. The Stage 3 primary-only lifetime context is used.
Opening-date death uses the existing opening-estate validation/calculation with
zero projection executions. A death before available opening balances fails
clearly; no estate is imputed. Failed evaluation does not publish a partial ranking.

The existing estate snapshot helper has a historical `EstateAtSecondDeath` name,
but its implementation is cardinality-independent: it selects opening or prior
December 31 balances and applies the existing heir haircut. Reusing that helper
does not create second-death state in a single result. Single results call the
event the person's death. Their compact outcomes contain only death year, weight,
investable assets, nominal after-tax estate and PV estate.

Actual weighted metrics retained are expected PV After-Tax Estate (ranking),
expected nominal After-Tax Estate, expected Investable Assets, and min/max nominal
scenario estate. Non-investable assets are excluded, consistent with the existing
weighted methodology. General inflation and real discount use the unchanged
ACT/365.25 estate discount helper. Deterministic full-plan taxes, withdrawals,
RMD/Roth, Medicare, income and expenses are computed only by ProjectionEngine.
Same-year-AGI IRMAA remains unchanged.

The reference test independently projects two lifetimes for every age and sums
their actual ending estates times 0.25/0.75. It agrees without rounding, preserves
probability mass, and shows claiming-age changes affecting the resulting finances.
The inherited realistic Stage 3 fixture contains taxable, traditional and Roth
accounts, pension, Social Security, expenses and bracket-target conversions.
Stage 3's own death-cessation and downstream financial-interaction tests remain
unchanged and pass in the regression selection.

## Strategy counts, performance and retention

Single SS-only executes nine elections and zero ProjectionEngine calls. The
representative production table fixture has 58 death scenarios, giving exactly
522 integrated executions (9 x 58), not 81 strategy combinations. Its standalone
JUnit execution including setup/assertions took about 4.13 seconds in the final
focused preview run. This is a test timing, not a benchmark guarantee. The two-
scenario reference uses 18 projections; the opening-only fixture uses zero.
No full projections are retained in completed mortality results. Retained compact
outcomes scale as O(9 x individual scenarios). Plan copies occur per scenario,
not per annual engine step. Existing couple continuation/equivalence caches and
bounded worker coordinator were not changed or repurposed for single scenarios.

Couple retirement-age grids remain 81 cells. Existing complete survivor searches
can contain more than 81 complete elections; their universe is unchanged.

## UI, state, guards and accessibility

The single-person analyzer now has Deterministic Integrated, Social Security Only,
and Longevity-Weighted Integrated tabs. The former Stage 3 deferred placeholders
are replaced with primary-only mortality views. They show category read-only from
Person, primary factor, separate conditioning/valuation dates and real discount
percentage. There is no spouse field or mortality-category override.

Both new views use the existing job admission controller, progress component,
cooperative cancellation and stale-publication prevention. Inputs disable while
work runs. Relevant edits and plan revisions mark results stale. Completed labels
and results capture run-time metadata. Row selection and table sorting read cached
results and start no jobs. The nine-row tables show exact-tie rank, claiming age,
current and maximum markers, expected PV, differences from current/maximum and
selected nominal details. No second axis or new heat-map implementation exists.
Controls use InputHelp, linked labels and accessible names; result accessible help
indicates stale/completed state. No calculation runs on the FX thread.

`createIndividual` is the explicit primary-only request-factory path. The old
factory overloads and weighted joint request types return structurally couple
contexts, so they retain composition guards but now direct callers to the supported
individual path instead of claiming all single weighted analysis is deferred.
The old coupled input contracts were not filled with null/sentinel spouse values.

Monte Carlo guards, joint PersonMortalityCategories consumers, and PDF guards
remain intact. The single views expose no PDF action. Deterministic break-even
still works; its survival overlay is a separate presentation integration and
remains deferred. Two short explanations were corrected so they no longer claim
the individual mortality model itself is missing. No break-even mathematics or
PDF output changed. Broad single-household creation/removal and reporting cleanup
remain outside this milestone.

## Couple regression evidence and tests

Before production edits, `SinglePersonStage4ACoupleGoldenTest` captured exact text
for production-table primary/spouse marginals, every joint scenario, 81 expected-
PV age-pair cells under a small two-point mortality fixture, maximum-PV cells,
and weighted integrated estate outcomes. Capture mode was removed before editing
production. The committed-to-worktree expected resource was not regenerated after
implementation. All comparisons remain exact, unrounded and passing. Existing
Stage 2/3 couple financial goldens and survivor tests are also retained.

New tests: eight individual mortality/analysis tests, one couple golden test and
three JavaFX tests. They cover mathematical invariants, nine elections, independent
PV/estate references, production-table execution counts, opening death, invalid
inputs, cancellation, stable cached selection, stale/frozen metadata, tooltips,
geometry and real 81-cell couple weighted rendering. The Stage 3 placeholder UI
assertion was updated to assert the newly supported tabs instead; no financial
assertion or expected financial value was removed or weakened.

Verification so far, using IntelliJ Maven and `.codex-m2/repository`:

- Pre-change golden capture: 1 test passed (`target/stage4a-golden.log`).
- Intermediate core/Stage 3 UI group: 12 passed
  (`target/stage4a-core-full-distribution.log`).
- Broad mortality, SS, survivor, heat-map, single-person and Monte Carlo group:
  882 tests, 0 failures/errors, 6 skipped (`target/stage4a-focused.log`).
- Final focused core/UI preview run: 11 tests, 0 failures/errors/skips
  (`target/stage4a-preview-final.log`).
- Full non-benchmark result is recorded in the final verification section below.

The first new test incorrectly compared two wrapper objects whose existing nested
distribution uses identity equality; it was corrected to compare their immutable
scenario values. The obsolete Stage 3 placeholder-label assertion was updated for
enabled tabs. Neither correction changed financial behavior. There have been no
unexplained numerical changes or relaxed couple expected values.

## Visual inspection

Opt-in previews: `-Dsingle.stage4a.preview=true`,
`SinglePersonMortalityViewTest`; files in `target/single-stage4a-preview/`.
Generated images were opened and visually inspected:

- `SOCIAL_SECURITY_ONLY.png`, 1180 x 900: nine readable rows, primary-only inputs,
  current/maximum markers, selected details and denominator/scenario explanation.
- `INTEGRATED.png`, 1180 x 900: nine rows, nominal/expected assets, death-date and
  property-exclusion explanation; selected details fit.
- `SOCIAL_SECURITY_ONLY-stale.png` and `INTEGRATED-stale.png`, 1180 x 900:
  edited factor 0.8 visibly invalidates results while frozen summary retains 1.0.
- `primary-person.png`, 650 x 500: original PersonCard displays actual Female
  category with no duplicate analyzer category control.
- `couple-weighted-heat-map.png`, 1400 x 1040: all 81 actual evaluated age-pair
  cells, both axes, tier legend, selected current/maximum comparisons and survivor
  elections remain readable. Uses a controlled certain-death fixture and fixed
  genuine survivor elections, not a claim to benchmark the entire survivor search.
- `couple-ss-analyzer.png`, 1884 x 1001 client (1900 x 1040 window), and
  `couple-ss-grid.png`: existing couple assumptions, survivor strategy details,
  highest-PV summary and 81-cell grid are unchanged. This controlled preview uses
  supplied certain-death distributions; its values are not production-table
  actuarial examples. The existing couple UI wording/layout was not redesigned.

No clipping/overlap was observed in the new single views. Tests assert table right
edge and selected-details bottom within the primary viewport. Couple screens use
their existing vertical scrolling. Existing heat-map tests cover selection,
navigation and display-metric changes without analysis reruns. No screen-reader
speech testing was performed; accessible names/state are checked programmatically.

## Existing conventions intentionally preserved

The next-complete-birthday approximation, terminal residual at age 120, birthday
SS deaths versus January 1 full-plan deaths, deterministic healthcare inflation,
estimated heir haircut, lack of legal estate settlement and same-year AGI IRMAA
are existing model choices. They were not corrected or characterized as exact
real-world actuarial/tax treatment. Partial opening coverage remains required;
invalid prior-opening death scenarios fail rather than being approximated.

## Stage 4B migration recommendations

1. Sample `IndividualLongevityScenarios.scenarios()` directly with the existing
   exact discrete probability sampler. Define/version-test PRIMARY_MORTALITY (2)
   for single households. Consume no SPOUSE_MORTALITY (3) draw.
2. Preserve current couple HOUSEHOLD_MORTALITY (1) joint order, rejection protocol
   and SHA-256 stream keys byte-for-byte. Market legacy Random and independent
   inflation dimension 4 remain unchanged. Add new single fingerprints, do not
   regenerate couple fingerprints.
3. Make world/request membership explicit. Single terminal timing is primary
   death year minus one; opening death uses opening estate and no projection.
   Keep fixed-horizon and longevity admissions separate.
4. Annual counts should reconcile living + deceased = requested and funded living
   + failed living = living. Couple composition details remain conditional on
   two real members. Never count normal post-death absence as funding failure.
5. For paired analysis validate matching membership/identity before drawing. A/B
   must share the same one-person realization, market path and inflation path.
   Reuse the existing paired reducer, percentile convention, comparable-world
   denominators and ordered outcomes; do not impute failed/post-death balances.
6. Audit worker-local generator state and any world/continuation caches for actual
   household shape in their keys. Avoid sharing mutable streams across workers.
   Do not apply the couple survivor-prefix cache to a primary-only strategy.
7. Keep report inputs frozen. Stage 5 must replace joint/second-death wording and
   table shapes honestly rather than adding empty spouse rows. Preserve explicit
   guards until each output adapter is adapted and tested.

No Stage 4B execution, random generation, worker protocol or percentile changes
were made here.

## Seeded regression evidence

The broad selection and first full suite passed the unchanged
`MonteCarloFoundationTest.seed417MarketPathsRemainIdenticalToPreInfrastructureFixtures`,
`MonteCarloWorldGeneratorTest.seed417WorldPathsExactlyMatchLegacyForScenariosZeroOneAnd224`,
`MonteCarloInflationRegressionTest.seed417FinancialFingerprints`, frozen SHA-256
known-answer stream tests, ordered joint-sampler boundary/frequency tests, and
paired-world/common-path tests. No expected fingerprints were edited. There are
no Monte Carlo production-file changes.

## Final verification

- First complete non-benchmark suite: **1,695 tests, 0 failures, 0 errors,
  9 skipped**, BUILD SUCCESS in 1:56 (`target/stage4a-full.log`).
- Focused verification after the final valuation-horizon guard: **12 tests,
  0 failures/errors/skips**, BUILD SUCCESS (`target/stage4a-final-focused.log`).
- Final complete non-benchmark rerun after all code/test edits: **1,695 tests, 0 failures, 0 errors, 9 skipped**, BUILD SUCCESS in 1:53 (`target/stage4a-full-final.log`).

The unchanged nine skipped tests are existing opt-in/disabled cases; no test was
newly skipped to complete this milestone. Diff audit found no synthesized person,
sentinel spouse age, fake spouse mortality mass or changed tax/IRMAA rule.

## Final boundary and review gate

Implementation, automated verification and visual inspection are complete for
Stage 4A. Stage 4B has not been started. Full-suite results include unchanged
couple probability, survivor, weighted financial, heat-map and seeded Monte Carlo
regressions. No unexplained couple numerical changes were observed.

Final boundary: **15 files**: 9 production Java files (6 modified, 3 new),
4 test Java files (1 modified, 3 new), 1 exact golden resource and this report.
Earlier stage reports are unchanged. The source changes outside the new individual
path are an additive own-benefit monthly API, single-view tab integration,
request-factory entry point and corrected guard/deferral wording.

`git diff --check`: exit 0. Git's LF-to-CRLF notices are not whitespace errors.
HEAD remains `00bbc51 Add single-person Social Security support`. Index is empty.
Nothing was staged, committed or pushed.

Exact final `git status --short`:

```text
 M src/main/java/com/daviddunn/retirementplanner/app/breakeven/BreakEvenContextFactory.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedIntegratedStrategyComparisonRequest.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedIntegratedStrategyRequest.java
 M src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityStrategyCalculator.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalysisRequestFactory.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedViewTest.java
?? SINGLE-PERSON-STAGE-4A-MORTALITY.md
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/SinglePersonMortalityAnalysis.java
?? src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/IndividualLongevityScenarios.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonMortalityView.java
?? src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/SinglePersonMortalityAnalysisTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/SinglePersonStage4ACoupleGoldenTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonMortalityViewTest.java
?? src/test/resources/single-person-stage4a-couple-golden.txt
```
