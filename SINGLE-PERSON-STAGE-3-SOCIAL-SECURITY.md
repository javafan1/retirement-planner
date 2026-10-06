# Single-person Stage 3: Social Security architecture audit and scope gate

## Status and starting state

Starting HEAD: `4950195 Add single-person core projection support`.
Initial `git status --short`: empty. Index empty.
Read the original architecture audit and Stage 1/Stage 2 reports; all remain
unchanged.

**Historical audit stop (superseded by the implementation section below).** This was the requested read-only audit and scope-gate
report. No production files, tests, financial assertions or persisted data were
changed. The stopping condition is the mismatch between the requested deterministic
SS-only analyzer and the existing user-facing mortality-weighted SS-only analyzer.
It is not a problem with representing an absent spouse in the Stage 1 domain.

## Initial scope decision (now resolved)

The existing Social Security Only UI is not a deterministic fixed-lifetime
analyzer. Its authoritative ranking is mortality-weighted expected Social Security
present value. This applies even though evaluation of a probability distribution
is deterministic arithmetic rather than random sampling.

Concrete dependency chain:

1. `SocialSecurityStrategyAnalyzerDialog` runs
   `SocialSecuritySurvivorClaimingOptimizationCalculator` for Social Security Only.
   Its methodology explicitly states mortality-weighted expected PV ranking.
2. `SocialSecurityStrategyAnalysisRequestFactory.create` always resolves two
   Person mortality categories, two adjustments, conditioning date and a mortality
   table. It constructs `AnalyzerLongevityAssumptions` and calls
   `HouseholdLongevityScenarioFactory.create(primaryDOB, spouseDOB, longevity)`.
3. The resulting two marginal distributions feed
   `SocialSecurityMortalityWeightedClaimingGridRequest`.
4. `SocialSecurityMortalityWeightedClaimingGridCalculator.calculate` combines the
   distributions into `SocialSecurityJointMortalityScenario` values and computes
   expected nominal, expected real and expected present values.
5. Survivor optimization expands complete paired retirement/survivor elections.
   Integrated Quick Comparison consumes that analyzer ordering and expected-PV
   columns even though its subsequent full-plan projections are deterministic.

Preserving that SS-only metric for singles requires a genuine single-life
probability/evaluation contract. An absent spouse distribution cannot be replaced
with a zero-valued or immortal spouse. This crosses the requested Stage 4 mortality
boundary. Deferring only the Longevity-Weighted Integrated tab does not remove
this dependency from Social Security Only or Quick Comparison.

There IS a separate headless deterministic path:
`SocialSecurityClaimingGridCalculator` -> `SocialSecurityStrategyCalculator` ->
`SocialSecurityStrategyValuationCalculator`. It evaluates nominal/real/PV totals
over an explicit finite scenario. It is not the calculator backing the current
SS-only UI. Adapting it is technically feasible without mortality changes, but
requires an explicit horizon convention and truthful fixed-horizon metric wording.
Silently substituting its result under "Expected PV" would change the financial
interpretation and potentially the strategy ranked optimal.

The Stage 3 review gate says to stop before changing mortality contracts or another
out-of-scope financial rule. AGENTS.md also requires a stop before an additional
financial-model change. These instructions are the reason for this audit-only
stop; no tool or automatic approval review rejected an action.

### Original proposal — rejected by the subsequent user scope clarification

Authorize a single-person **deterministic, fixed-horizon Social Security Only**
mode for Stage 3:

- Reuse the existing own-benefit and deterministic valuation formulas.
- Use the configured plan projection range as the displayed finite benefit
  horizon, with an explicitly modeled primary death ending receipt on January 1
  of that year where supplied; no mortality weights.
- Label ranking "Social Security present value through [end year]", not expected
  lifetime PV. Preserve the valuation date/real discount assumptions explicitly.
- Present nine actual ages in one table, with current/maximum/tie indicators.
- Evaluate the nine deterministic integrated strategies through ProjectionEngine;
  reuse those completed results for selection and break-even.
- Keep the couple SS-only expected-PV path and heat maps unchanged. Defer all
  single-person mortality-weighted SS and integrated analysis until Stage 4.

This proposed horizon/metric convention has not been implemented or assumed
approved. Alternative: explicitly bring single-person marginal mortality-weighted
SS evaluation into scope now while continuing to defer Monte Carlo. That is a
larger boundary and requires mortality-contract tests, not a nullable spouse patch.

## Read-only audit and dependency classification

| Area | Actual contract | Classification / required adaptation |
| --- | --- | --- |
| SocialSecurityIncome / SocialSecurityDialog | Stores claiming age and start date; UI derives read-only start date from person/age. Legacy COLA remains a JSON compatibility property. | Stage 3: preserve age-derived UI authority. Do not rewrite legacy persisted dates without auditing inconsistencies. |
| SocialSecurityProjectionIncomeProvider | Stage 2 supports zero/one primary record through calculateOwnRetirement, but rejects lifetime/strategy contexts for singles. | Stage 3: admit genuine primary-only strategy and death inputs after validation. |
| SocialSecurityStrategyCalculator | Own monthly calculation is reusable; full request prepares two elections and computes spouse/survivor candidates every month. | Stage 3: generalize presence while retaining own and survivor formulas. |
| SocialSecurityStrategyRequest | Spouse election required; finite end resolved from explicit horizon or later of two deaths; survivor validation refers to opposite death. | Stage 3: optional spouse with absence invariants; one-person finite horizon/death policy required. |
| HouseholdSocialSecurityResult | Stage 2 already validates absent spouse/spousal/survivor components and exposes hasSpouse. | Naturally supports single core projection; retain detailed couple audit. |
| Monthly/annual/lifetime SS results | Full analyzer graph has mandatory paired numeric components and couple life states; lifetime reconciliation sums spouse/spousal/survivor values. | Stage 3: absence-aware results, not manufactured zeros. |
| SocialSecurityHouseholdClaimingStrategy | Primitive spouse age, required spouse date and two survivor elections; shared value identity used by integrated/weighted services. | Stage 3: explicit optional election, preserving couple identity/order/equality. |
| Deterministic claiming grid | Nested primary/spouse age loops and Cartesian result cells; nominal/real/PV rankings already exist. | Stage 3 candidate for nine-age deterministic table after metric/horizon decision. |
| SS-only UI request and ranking | Mortality categories/distributions/joint scenarios/survivor optimization are mandatory. | Blocking Stage 3/4 boundary described above. |
| Complete deterministic integrated search | Standard universe multiplies two age lists and two survivor candidate lists. | Stage 3: single universe must be exactly nine, without placeholder candidates. Couple complete search has more than 81 strategies; only its retirement-age grid has 81 cells. |
| IntegratedSocialSecurityStrategyEvaluator | Requires advanced paired path; validates both ages/dates, extracts both survivor elections, deep-copies plan and calls engine with strategy override. | Stage 3: retain isolation and projection authority, admit single strategy only when supported. |
| ProjectionMetricsDifferenceCalculator and consumers | Shared metrics include spouse lifetime income. | Stage 3: preserve absent spouse while comparing real metrics; do not subtract null or manufacture spouse observations. |
| EffectiveHouseholdDeathView | Stage 1 freezes membership; ABSENT/ALIVE/DECEASED explicit; January-1 death date; primary death with absent spouse is distinguishable. | Naturally supports membership semantics. Do not infer presence from missing death data. |
| HouseholdLifetimeScenario | Two Optional death years; absence means survival for a present member. | Stage 3 must validate with membership; do not label empty spouse timing as living spouse. |
| RetirementPlan / baseline snapshot validation | Single household currently rejects couple persisted death scenarios. | Stage 3 must define explicit single primary-death input before relaxing guard; no spouse scenario UI. |
| ProjectionEngine | Single lifetime/survivor/strategy overrides guarded; Stage 2 generic owner processing is ready. | Stage 3: targeted context admission only; retain tax/IRMAA/RMD/Roth algorithms. |
| BreakEvenAnalyzer | Amount mathematics compares completed projections and sums authoritative household SS. Annual metadata still dereferences spouse age. | Stage 3: optional metadata; keep crossover/shared-horizon math unchanged. |
| BreakEvenPlanSummary | Always obtains required spouse; null-person helper fabricates metadata placeholder. | Stage 3: explicit absence, no placeholder person summary. |
| BreakEvenContextFactory | Events loop both owners; survival overlay always constructs joint longevity scenarios. | Stage 3 events can use actual people; single survival overlay must remain explicitly unavailable until Stage 4. |
| ClaimingStrategyHeatMapModel / View / adapters | Two-axis cell lookup, Cartesian coordinates, metric selection over completed results. | Preserve couple 9x9 behavior; use nine-row single table rather than fake spouse axis. |
| Analyzer dialog/session/jobs | Shared mortality inputs, frozen metadata, progress/cancellation and stale scopes; row selection uses retained results. | Stage 3 targeted presentation/session integration after scope decision; preserve no-rerun selection. |
| Longevity-weighted integrated | Joint probabilities, exact second-death endings and continuation/equivalence machinery. | Stage 4: choose requested option B, explicitly unavailable for singles. |
| Monte Carlo | Existing two-person admission and seeded contracts remain. | Stage 4, untouched. |
| Analyzer/report exports | Frozen report metadata and heat-map report shapes are paired; BreakEven PDF carries spouse/joint survival context. | Stage 5; retain or add explicit single export guards at newly enabled entry points. |

## Semantics to preserve during implementation

Claiming formulas, FRA calculations, economic Social Security COLA, monthly
rounding and two-person survivor basis frozen at death remain authoritative.
Projection year-only death maps to January 1; no single survivor stream should
exist. Stage 2 same-year AGI IRMAA, filing status, Michigan tax, RMD, Roth,
withdrawal and estate formulas must remain unchanged. Legacy JSON COLA must not
become a second active source.

A noteworthy compatibility issue: the UI derives dates from claiming age, but
persisted `SocialSecurityIncome` stores both and the projection provider reads
`getStartDate()`. Domain construction can therefore hold inconsistent values.
This audit has not proven that any user plan is inconsistent. Do not globally
normalize existing couple JSON as a side effect of Stage 3; validate strategy
age/date consistency and protect existing financial fixtures.

The separate deterministic full-plan search ranks After-Tax Estate; the quick
comparison keeps SS analyzer order rather than selecting an integrated winner.
Those are different semantics and must not be flattened into one ranking.

## Planned implementation/test boundary after resolution

1. Capture additional pre-change couple own/survivor, grid, integrated and
   heat-map values before shared code edits; retain the Stage 2 financial golden.
2. Generalize required strategy/request/result shapes with explicit absence;
   reject single survivor/spouse inputs at admission.
3. Reuse monthly own-benefit math with primary death cessation. Test ages 62/FRA/70,
   derived dates, economic COLA, and frozen couple survivor basis/COLA.
4. Generate nine single age strategies and preserve couple grid/search universes.
   Adapt deterministic evaluator/context/metric differences, leaving mortality
   and weighted paths guarded.
5. Build single ranked presentation using completed data and existing job/stale
   controls. Adapt deterministic break-even metadata/events and omit unavailable
   mortality overlays. Keep unsupported PDFs disabled.
6. Test execution counts (nine candidates plus separately documented current-plan
   evaluation, where needed), current/optimal indicators, metric/selection no-rerun,
   realistic tax/Roth/withdrawal/estate flow, couple heat-map preservation, and
   visual previews. Run complete non-benchmark suite before completion.

No single-person strategy execution count, ranking, preview or performance result
is claimed yet. The acceptance gate remains unmet because implementation has not
started. No partial implementation was introduced while the ranking semantics
were unresolved.

## Verification performed at the audit gate

Focused existing regressions: **80 tests, 0 failures, 0 errors, 0 skipped**,
BUILD SUCCESS in 12.620 seconds. Log: `target/single-stage3-audit-tests.log`.
Used IntelliJ bundled Maven and repository-local `.codex-m2/repository`.

Selection:
`SinglePersonCoreProjectionTest,SocialSecurityClaimingGridCalculatorTest,SocialSecuritySurvivorStrategyCalculatorTest,SocialSecurityColaSourceOfTruthTest,SocialSecurityLegacyColaIsolationTest,SocialSecurityStrategyAnalysisRequestFactoryTest,IntegratedRetirementClaimingGridCalculatorTest,DeterministicHeatMapAdapterTest,ClaimingStrategyHeatMapModelTest,BreakEvenAnalyzerTest,BreakEvenContextFactoryTest`.

This includes the existing exact Stage 2 couple golden and existing survivor,
COLA, claiming-grid, integrated, heat-map and break-even assertions. No new golden
or single-person Stage 3 tests were added, and no expected values were edited.
The full 1,672-test suite was not rerun for this documentation-only change; that
number remains the supplied Stage 2 baseline. No UI changes or manual visual
inspection were performed. No calculation defect was fixed.

## Historical audit-only file boundary

Only this report was created. Prior reports remain intact. Nothing staged,
committed or pushed. `git diff --check` passed; index empty.
Exact final `git status --short`:

```text
?? SINGLE-PERSON-STAGE-3-SOCIAL-SECURITY.md
```

## Scope clarification accepted / implementation boundary

The user rejected fixed-horizon substitution for SS-only expected PV. Stage 3
will preserve both mortality-weighted objectives and explicitly defer their
single-person entry points. The implementation boundary is optional household
claiming strategy -> deterministic provider/context -> existing integrated
evaluator/search and metric differences -> single nine-row view using the
existing job controller. Break-even adapts absent-person metadata and skips the
mortality overlay for singles. Single death timing is a run-only primary death
input, with membership-aware January-1 cessation, not a persisted couple scenario.
Couple calculators/heat-map/weighted formulas remain unchanged. Reports remain
guarded. A pre-edit couple grid/survivor golden is captured before shared edits.
This supersedes the earlier audit-only stop; final results will be appended.

## Completed Stage 3 implementation

The clarified Stage 3 scope is implemented. The audit's proposed deterministic
fixed-horizon substitute for Social Security Only was **rejected**, not installed.
Couple Social Security Only still ranks mortality-weighted expected PV. A single
household sees an explicit implementation-boundary explanation instead of results
with a different objective. Single-person Longevity-Weighted Integrated analysis
is likewise deferred. Both depend on Stage 4's shared single-life probability
foundation; neither is approximated here.

Implementation resumed at the same HEAD, `4950195`. Its initial worktree contained
only the untracked audit report already created in the preceding turn. The index
was empty. Earlier Stage 1/2 reports remain unchanged.

### Domain, own-benefit execution and death timing

`SocialSecurityHouseholdClaimingStrategy` now represents absent spouse age as a
nullable Integer with `hasSpouse()` and `spouseRetirementAgeIfPresent()`. The
`primaryOnly` factory has no spouse date or survivor elections. The constructor
rejects mixed absent-spouse/present-survivor shapes and validates the single
claiming age range. Existing couple values, record equality/hash semantics and
generation ordering retain the same numeric/election meaning; no sentinel ages
or fabricated elections were introduced.

Stage 2's `HouseholdSocialSecurityResult.primaryOnly` already supplies honest absent
spouse/spousal/survivor components, so no further result-shape rewrite was needed.
Single own benefit equals the household received amount. Own retirement still
uses the same authoritative monthly preparation, claiming adjustment, rounding
and COLA code. An overload admits a real primary death date and invokes the
existing monthly alive convention. No survivor calculation is called for singles.

For singles, the projection provider derives the claim date from primary DOB and
the authoritative claiming age, including strategy overrides. It rejects an
override whose date disagrees. Couple persisted-date behavior is untouched.
EconomicAssumptions remains the sole active Social Security COLA source; the test
uses a conflicting legacy source COLA of 0.99 and proves it is inert.

`HouseholdLifetimeScenario.primaryOnly` is explicitly a timing factory; Household
membership still determines absence. The engine admits primary-only deterministic
strategy/lifetime contexts but rejects spouse death, spouse election and survivor
age inputs. Death takes effect January 1 of the supplied year. Own benefits stop
for that entire year and later years, including death before claiming. No spouse
or survivor stream begins. The configured projection horizon is retained.

The analyzer offers an optional **session-only Primary death year**. Blank means
no primary death within the configured analysis; it does not create a mortality
record or alter the saved plan. This input does not reuse the persisted couple
PRIMARY_DIES/SPOUSE_DIES choices. Existing single-plan validation of those coupled
persisted scenarios remains intact. No JSON schema or migration was added.

The existing surviving-filer transition is gated to households with an actual
spouse. For singles, configured filing status remains authoritative after the
primary death; a test preserves the configured standard deduction instead of
creating a fictitious surviving Single filer. Couple transitions are unchanged.
No tax brackets, tax formulas, Michigan logic, same-year-AGI IRMAA, Medicare tables,
RMD/Roth algorithms or investment/expense formulas changed.

### Deterministic strategy generation and evaluation

The existing complete integrated search request generates real strategies:

- Standard single household: exactly nine primary ages, 62 through 70, and empty
  spouse/survivor candidate lists. Candidate evaluation executes nine times.
- Couple retirement-age grid: still 81 age pairs. The existing **complete** couple
  exhaustive search also crosses its survivor candidates, so its total can exceed
  81. That existing larger universe was preserved, not silently reduced to 81.

The calculator iterates this ordered strategy list using its existing evaluator,
metric-difference calculation, failure retention, rank/tie logic, progress and
bounded projection-detail retention. Current plan is evaluated separately once:
standard single execution is **one baseline plus nine candidate projections**.
No 81-cell single run is performed and discarded.

`IntegratedSocialSecurityStrategyEvaluator` continues to copy each plan and call
ProjectionEngine with run-only overrides. Each candidate changes only primary
claiming age/date; the same optional primary death timing applies to baseline and
all candidates. No active plan mutation occurs. Metrics remain Investment Growth,
Total Income, Total Taxes, Peak Annual Tax, Investable Assets, Net Worth,
After-Tax Estate and the established lifetime detail metrics. Ranking remains
**deterministic After-Tax Estate**, not SS expected PV. Spouse metric differences
are absent for two single results; mismatched presence is rejected.

`SinglePersonIntegratedAnalysis` packages the completed result, frozen metadata and
break-even comparisons built from retained projections. It uses the existing
non-investable asset service for the net-worth component. It adds no claiming,
tax, estate or break-even formula. The presentation retains no mutable live plan
as completed-result metadata.

### Single-person presentation and state management

The existing SocialSecurityStrategyAnalyzerDialog routes single households to a
compact one-dimensional presentation. It shows all nine rows, claiming age,
deterministic rank, After-Tax Estate, differences from current and maximum,
Current and Maximum-estate markers, and selected strategy financial details.
Ties retain the existing rank semantics. There is no spouse axis or single-person
heat map. Selection reads completed data and does not start any job/projection.

The existing SocialSecurityAnalyzerJobController supplies application admission,
background execution, progress, cancellation and stale-publication prevention.
Input and source-plan revision changes invalidate the single result. Closing the
dialog closes the job session. Inputs/actions disable while running, cancellation
publishes no partial result, and break-even is disabled for stale/failed/cancelled
runs. Frozen result labels remain unchanged when live inputs are edited.

The mortality-dependent tabs explain their implementation deferral; their metrics
are not renamed or replaced. Quick Comparison is not exposed for singles because
its candidate order depends on the deferred mortality-weighted SS-only result.
The deterministic nine-strategy analysis is independently available.

SocialSecurityDialog now offers only actual owners. For singles it offers PRIMARY
only, retains the derived/read-only start date and existing tooltip infrastructure,
and rejects an existing non-primary source rather than reassigning it. Broad
household creation/removal and unrelated Results UI remain later-stage work.

New controls use InputHelp, linked labels and descriptive accessible names. The
strategy table has an explicit accessible name; details remain readable text.
No new charts, CSS framework or preference coloring were introduced.

### Break-even and export boundaries

BreakEvenPlanSummary represents absent spouse explicitly; the analyzer omits spouse
age metadata. The original shared-year indexing, cumulative household SS,
crossover, sustained/no-recovery/identical logic and metric definitions are
unchanged. Single household SS naturally uses the received own benefit.

The single integrated run computes its per-age comparisons from completed
projections. Opening or selecting a comparison does not rerun ProjectionEngine.
The dialog identifies selected claiming age as Current and the frozen current-plan
age as Baseline. No spouse row/age is shown. Deterministic break-even remains
available while single survival probabilities are explicitly deferred.

Single Break-Even PDF is disabled in the dialog and rejected by the exporter
before writing. The integrated report adapter rejects single strategies; the new
single view exposes no PDF export. Existing couple exporters remain unchanged
apart from these admission guards. Stage 2 projection CSV/PDF and Monte Carlo
guards remain. No frozen-result/no-rerun export architecture was weakened.

### Single-person tests and regression evidence

New tests:

- `SinglePersonDeterministicStrategyTest` (5): nine unique actual strategies,
  derived dates, own benefit at ages 62/67/70, legacy-COLA isolation, death cessation,
  spouse/survivor absence, direct engine/evaluator metric equality, current rank,
  maximum rank, nine candidate progress units, immutable plan data, income/tax/
  withdrawal/Roth/estate differences, configured filing status, completed break-even
  including non-investable net worth, explicit deferred-service guards, and invalid
  strategy rejection. No particular claiming age is assumed optimal.
- `SinglePersonStage3CoupleGoldenTest` (1): exact unrounded text fixture captured
  **before production edits**, then capture mode removed. It locks current couple
  strategy, annual own/survivor/household received amounts and all 81 integrated
  age-pair metric records. The fixture includes a delayed primary election and a
  primary death with a surviving spouse. Capture log:
  `target/stage3-golden-capture.log` (1 passed).
- `SinglePersonIntegratedViewTest` (5): nine rows and markers, cached selection
  without jobs, stale inputs/frozen metadata, real single dialog routing, explicit
  mortality explanations, cancellation with no partial result, geometry, actual
  owner-only SS input and derived date, plus opt-in preview generation.

The existing SinglePersonHouseholdTest guard cases were updated from unsupported
ordinary lifetime execution to rejection of nonexistent survivor inputs, because
primary deterministic death is now supported. Mortality guard assertions remain.
No existing financial expected value was weakened, removed or changed.

Existing tests cover frozen survivor basis and post-death economic COLA, 81-cell
claiming grids, deterministic search, mortality-weighted expected-PV calculations,
heat-map metric/selection behavior and all break-even statuses. Their assertions
remain unchanged. The Stage 2 exact financial golden also passes. No couple
financial output difference was observed. Weighted calculators, mortality tables,
generators, random streams and seed protocols were not edited.

### Visual verification

Opt-in command property: `-Dsingle.stage3.preview=true` with
`SinglePersonIntegratedViewTest`. Images are in `target/single-stage3-preview/`.
The generated images were opened and inspected, not merely produced.

- `single-social-security-input.png`: actual dialog; PRIMARY only, age 70 and
  derived February 1, 2035 date; labels and help fit.
- `single-integrated-1180.png`: 1180 x 820 content; all nine rows, current/maximum
  indicators, selected metrics, controls and break-even action fit without
  horizontal clipping. There is no spouse coordinate. Geometry assertions check
  table width/right edge and selected details within the viewport.
- `single-break-even.png`: 1180 x 900; one primary age column, four metric summaries
  and signed difference chart. The annual table continues vertically as designed.
  No fabricated spouse row. The standalone preview uses the default unavailable
  survival context; the integrated action supplies the explicit Stage 4 deferral.
- `couple-heat-map.png`: fully populated real 81-cell deterministic result;
  primary/spouse axes, tier legend, optimum marker, metric switch and survivor
  details remain readable. The fixture holds survivor elections fixed, as the
  existing 81-pair grid does; it is not a reduced claim about complete exhaustive
  survivor search.
- `couple-analyzer-inputs.png` and `couple-ranked-strategies.png`: actual existing
  couple dialog at 1900 x 1040 window size; primary/spouse and longevity inputs,
  current strategy and selected outcome remain present. Existing long content uses
  vertical scrolling. A separate actual ranked-table snapshot is recorded below.

These are controlled JavaFX fixtures, not a claim that broad single-person plan
creation/removal or every MainWindow/Results screen is adapted. That remains the
later UI/reporting milestone.

### Performance / limitations

Single search performs nine candidates plus its baseline. Break-even uses those
cached projections; UI row selection starts zero jobs (generation count asserted).
Strategy generation is O(number of real strategies); the shared search now holds
a compact ordered list of election descriptors as well as existing outcomes.
No mortality scenario generation or extra annual projection copying is introduced.
No standalone performance benchmark was run; test durations are verification
runtimes, not projection-cost benchmarks.

The persisted model still contains legacy SS start dates alongside age. This
stage derives the date for single-person execution as required, while preserving
existing couple persisted-date compatibility. It does not rewrite older couple
JSON or resolve inconsistent historical inputs silently.

## Stage 4 dependencies — shared foundation, not two implementations

A. Single-person Social Security Only needs mortality-weighted expected-PV
calculation with the same objective as the couple analyzer.

B. Single-person Longevity-Weighted Integrated Retirement Plan needs single-life
scenario generation/probability weighting and appropriate terminal timing.

Implement one direct person mortality distribution with P(alive at age/year) and
P(death at age/year), shared by both paths. Do not wrap it in fake joint mortality.
Preserve existing couple probability conservation and seed behavior. Monte Carlo
adaptation, including fixed/longevity/paired modes, remains Stage 4 work. No Stage 4
implementation was started here.

## Final verification results

IntelliJ bundled Maven and `.codex-m2/repository` were used throughout.

- Audit-only existing regression run: 80 tests passed.
- Pre-change couple golden capture: 1 passed.
- Initial implemented core/golden/Stage 2 group: 15 passed.
- Expanded Social Security/survivor/break-even/heat-map/single-person group:
  **534 tests, 0 failures/errors, 1 skipped**, `target/stage3-focused.log`.
- Final focused UI/domain/golden preview group: **20 tests, 0 failures/errors,
  0 skipped**, `target/stage3-ui-final.log`.
- First full non-benchmark suite: **1,683 tests, 0 failures/errors, 9 skipped**,
  BUILD SUCCESS in 1:45, `target/stage3-full.log`.

Final small validation-message/accessibility edits and the ranked-table preview
are verified in the final rerun recorded below. There were no intermittent
financial failures or changed couple expected values in these completed runs.
The preview fixture's initial reflective render needed its presentation field
initialized, matching the real dialog lifecycle; that test setup was corrected
without changing couple production UI.

### Final rerun and review gate

- Final full non-benchmark suite after all production/test edits: **1,683 tests,
  0 failures, 0 errors, 9 skipped**; BUILD SUCCESS, 1:50,
  `target/stage3-full-final.log`.
- Visually inspected `target/single-stage3-preview/couple-ranked-table.png`
  (1846 x 400): actual existing couple ranking table, with readable ranks,
  retirement dates and financial values. Existing long survivor headings/cells
  use ellipses, and the bounded table scrolls vertically. This is an existing
  couple layout limitation, not a new single-person layout or a claim that all
  81 rows fit simultaneously. No unrelated couple layout redesign was made.
- `git diff --check`: exit 0. Git emits only its LF-to-CRLF working-copy notices.
- Final boundary: **31 files**: 25 production Java files (23 modified, 2 new),
  4 test Java files (1 modified, 3 new), 1 golden text resource, 1 report.
- No synthetic spouse, spouse age sentinel or substitute mortality-weighted
  objective was introduced. Couple golden values remain unchanged; mortality,
  random generation, tax/IRMAA formulas and persistence schema were not changed.
- HEAD remains `4950195 Add single-person core projection support`; index empty.
  Nothing was staged, committed or pushed. Stage 3 stops here for review.

### Exact final git status --short

```text
 M src/main/java/com/daviddunn/retirementplanner/app/breakeven/BreakEvenContextFactory.java
 M src/main/java/com/daviddunn/retirementplanner/app/export/BreakEvenPdfExporter.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/IntegratedSocialSecurityCompleteStrategySearchCalculator.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/IntegratedSocialSecurityCompleteStrategySearchRequest.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/IntegratedSocialSecurityCompleteStrategySearchResult.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/IntegratedSocialSecurityStrategyEvaluator.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedIntegratedStrategyComparisonRequest.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedIntegratedStrategyRequest.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/ProjectionMetricsDifferenceCalculator.java
 M src/main/java/com/daviddunn/retirementplanner/domain/breakeven/BreakEvenAnalyzer.java
 M src/main/java/com/daviddunn/retirementplanner/domain/breakeven/BreakEvenPlanSummary.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/HouseholdLifetimeScenario.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/SocialSecurityProjectionIncomeProvider.java
 M src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityHouseholdClaimingStrategy.java
 M src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityStrategyCalculator.java
 M src/main/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenAnalysisDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenAnalysisView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenPresentation.java
 M src/main/java/com/daviddunn/retirementplanner/ui/dialogs/SocialSecurityDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IntegratedAnalyzerReportAdapter.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalysisRequestFactory.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java
 M src/test/java/com/daviddunn/retirementplanner/domain/model/SinglePersonHouseholdTest.java
?? SINGLE-PERSON-STAGE-3-SOCIAL-SECURITY.md
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/SinglePersonIntegratedAnalysis.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedView.java
?? src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/SinglePersonDeterministicStrategyTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/SinglePersonStage3CoupleGoldenTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedViewTest.java
?? src/test/resources/single-person-stage3-couple-golden.txt
```
