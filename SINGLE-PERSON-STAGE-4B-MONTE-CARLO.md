# Single-person Stage 4B: Monte Carlo architecture review

**Update:** The user approved the migration below. The original audit is preserved
as historical design input. Implementation and verification are documented in the
appended implementation section; the original stop status is no longer active.

## Status and starting point

**Implementation paused at the requested architecture stop gate. Stage 4B is not
implemented or ready for acceptance.** This report records the read-only audit and
a proposed implementation boundary. No production code, tests, financial values,
golden files, persistence formats or random-stream protocols were changed.

Starting HEAD: `6f3b27f Add single-person plan UI support`.
Initial `git status --short`: empty; index empty.

The Stage 1, Stage 2, Stage 3, Stage 4A and Stage 4A.1 reports were consulted.
Their findings are retained. The domain and deterministic engine already support
an absent spouse. The issue below is in Monte Carlo's separate frozen contracts,
not a defect invalidating those completed stages.

## Stop condition and concrete problem

The request says to stop before a significant architectural change that could
alter existing couple Monte Carlo results. Extending the shared Monte Carlo
lifetime and population contracts is such a change:

1. `HouseholdLifetimeScenario` intentionally stores timing, not membership.
   An empty spouse death year means survival for a present spouse; the same
   timing object can accompany a single-person household. Stage 1 resolves this
   safely in `EffectiveHouseholdDeathView` by supplying actual Household membership.
2. `MonteCarloWorld` and `MonteCarloMortalityAnalysisResult.WorldOutcome` carry
   only that timing object, without a household-composition discriminator.
   Their constructors currently require **both** death years.
3. Execution and reducers independently derive the terminal year using the maximum
   of two death years. The annual reducer classifies every world into both alive,
   primary only, spouse only or both deceased.
4. The paired execution and reduction contracts repeat this assumption. Frozen
   presentation/PDF adapters also assume two people and second-death terminology.

Removing `requireSpouse`, relaxing the two-death validation, or treating an empty
spouse death year as deceased would not be a safe implementation. It could accept
an incomplete couple world as a complete single lifetime, shorten its projection,
change its terminal assets/funding outcome, and alter annual sample populations.
The reducers must receive explicit composition independently of missing dates.

This requires a coordinated request/world/outcome/population migration rather
than local nullable-spouse patches. The design below aims to preserve couple
values exactly; no numerical change is proposed or authorized. Preservation still
needs proof after the contract migration. Approval of that concrete boundary is
the next step before production edits.

## Audit inventory

| Area / classes | Current behavior and required boundary |
| --- | --- |
| `MonteCarloAnalyzer` fixed execution | Explicit two-person admission guard. Remaining loop uses the shared ProjectionEngine, fixed market paths, inflation paths, completed prefixes and existing metrics. No separate financial formulas are needed. |
| `MonteCarloMortalityRequest` | Captures both DOBs, mandatory couple `AnalyzerLongevityAssumptions`, table and optional survivor election. Requires a spouse. Conditions at projection start, deliberately overriding the session conditioning date. |
| `AnalyzerLongevityAssumptions`, `LongevitySessionSettings` | Require spouse category/adjustment or spouse adjustment. They must not be populated with fake spouse inputs for a single request. Prefer a Monte Carlo-specific individual input variant over changing existing couple analyzer assumptions globally. |
| `Person` | Stores mortality category, **not a longevity factor**. Adjustments currently come from analysis session settings. Preserve that actual architecture; adding a persisted Person factor would be a separate change. |
| `IndividualLongevityScenarios` | Stage 4A already provides the actual individual distribution. Reuse its mortality provider, adjustment and timing conventions; do not introduce another mortality formula. |
| `HouseholdLongevityScenarioFactory`, `HouseholdLongevityScenarios` | Couple path constructs an ordered joint distribution from two individual distributions. Keep this path and its ordering unchanged. |
| `MonteCarloWorldGenerator` | Samples one index from that ordered joint distribution using `DiscreteProbabilitySampler`, maps both death dates to years, then generates economic coverage through last living year. It does not currently draw each partner independently. |
| `HouseholdLifetimeScenarioMapper` | Accepts only joint scenarios; annualizes birthday death to January 1 of the same year. A direct individual mapping is needed without a spouse date. |
| `MonteCarloWorld` | Rejects missing either death year; no composition field. Needs explicit membership in its complete sampled-lifetime contract. |
| `MonteCarloAnalyzer.analyzeMortality` | Requires advanced SS, survivor age, matching two-person demographics; terminates immediately before second death. Opening-date death uses opening estate and no projection. Preserve analogous actual-person terminal timing. |
| `MonteCarloMortalityAnalysisResult.WorldOutcome` | Requires both death years, exposes `secondDeathYear`, validates failure before that year. Needs composition-aware complete-lifetime validation and neutral terminal-year access. |
| `MonteCarloMortalityAccumulator`, `MonteCarloMortalityAnnualResult` | Couple population fields and reconciliation. Need a genuine individual population representation without labeling an individual as a couple survivor. |
| `MonteCarloStrategyComparisonRequest`, `MonteCarloComparisonWorld`, `MonteCarloPairedOutcome` | Freeze shared timing and ordered A/B outcomes; need shared composition with demographic compatibility checks. Single-vs-couple comparison must fail clearly. |
| `MonteCarloStrategyComparisonAnalyzer` | Guards both plans, accesses both people, requires survivor elections, takes maximum death year. Generalize admission and terminal timing, not financial evaluation. |
| `MonteCarloStrategyComparisonAccumulator` | Repeats two-death terminal calculation. Its A-minus-B statistics and paired-state formulas are otherwise cardinality-independent. |
| `MonteCarloPercentiles`, paired metric/state summaries, `FundingFailureStatistics` | Reusable unchanged. Type-7 BigDecimal interpolation, empty Optional populations, exact observed funding failures and conditional paired differences remain authoritative. |
| `MonteCarloRunService`, `MonteCarloStrategyComparisonRunService` | Shared engine adapters; comparison capture unconditionally visits spouse and freezes two survivor elections. Require composition-aware capture before enabling actions. |
| `MonteCarloAnalysisView`, `MonteCarloStrategyComparisonView` | Mandatory spouse adjustment/survivor controls and result-time spouse dereferences. Hide/omit inapplicable controls and validation, retaining existing stale/cancel/background behavior. |
| `MonteCarloFanModel`, `MonteCarloPresentation`, `MonteCarloMortalityPresentation`, comparison presentation | Spouse names, elections, both-alive population text and second-death disclosure. Need frozen composition-aware wording and individual annual population display. Charts and percentile values themselves remain reusable. |
| `MainWindow` | Stage 4A.1 intentionally disables single-person Monte Carlo and comparison actions. Keep until execution and presentation are both supported. |
| `MonteCarloPdfReportAdapter`, `MonteCarloPdfExportAction` | Report adapter dereferences spouse metadata and renders couple populations. Add explicit single-person export guard if broad report adaptation remains deferred; preserve frozen-result/no-rerun design. |
| Existing Social Security analyzers | Stage 4A's nine-strategy single-person analyses already work; no claiming ranking changes belong here. Monte Carlo exposes distributions, not a strategy optimizer. |

## Financial execution findings

The existing deterministic path supplies the needed financial rules. No new
calculation is proposed for taxes, pensions, Social Security, RMDs or Roth:

- `EffectiveHouseholdDeathView` freezes composition and distinguishes ABSENT,
  ALIVE and DECEASED. `isHouseholdDeceased` works for one actual person.
- `SocialSecurityProjectionIncomeProvider` already produces primary-only annual
  results, with explicit spouse/survivor absence and own-benefit cessation.
- Projection owner eligibility, income and Medicare processing use actual members
  or actual owner maps. Retain primary-only RMD/Roth processing and existing
  withdrawal ordering, opening RMD and tax-funding mechanics.
- Existing single-person validation rejects invalid spouse/joint ownership and
  spouse-dependent pension elections. Do not relax those rules for Monte Carlo.
- Expenses remain household expenses and use existing death/inflation semantics.
- Filing status remains explicit. Same-year modeled AGI IRMAA is intentionally
  unchanged; historical lookback remains a separate financial-rules milestone.
- Investable assets, net worth and after-tax estate come from authoritative engine
  metrics. `EstateAtSecondDeathCalculator` has couple-oriented names but its
  opening-balance calculation does not intrinsically require a spouse. Avoid a
  broad rename across unrelated analyzers; use a neutral Monte Carlo adapter.
- Longevity projections currently end at the December 31 before the modeled
  January 1 terminal death. Do not introduce post-death financial rows merely to
  test cessation. Test the underlying death context separately against the engine.
- Funding failure remains the existing structured outcome. Deceased households
  are not failed households; failed worlds supply no fabricated terminal values.
- Sampled worlds each contribute one empirical observation. Do not apply the
  original mortality probability a second time during reduction.

## Proposed architecture for review

1. Add a small immutable Monte Carlo household-lifetime value with explicit
   single/couple composition plus the existing timing object. A sampled lifetime
   requires a death for every present person and forbids dates for absent people.
   Expose neutral `terminalDeathYear`, actual-person alive state and projection
   timing. Do not change the persisted household schema or reinterpret empty dates.
2. Give frozen mortality inputs explicit individual/couple variants. Individual
   inputs contain primary DOB/category/adjustment, conditioning and table only;
   no spouse adjustment or survivor election. Keep existing couple construction
   as a compatibility path with the same validation and exact values.
3. Preserve the current couple generator branch verbatim in mathematical order.
   Add an individual branch using the Stage 4A individual distribution directly.
   Share only market/inflation generation and final immutable world construction.
4. Carry composition through generated and caller-supplied worlds, outcomes,
   paired comparisons and cached results. Validate it against captured plans at
   admission, and reject mismatched caller-supplied worlds before projection.
5. Use neutral annual populations (requested, living, funded living, failed
   living, deceased), with an optional couple-only breakdown. Preserve existing
   couple breakdown values; represent its absence explicitly for individuals.
   Keep percentile and monetary-difference implementations unchanged.
6. Route both cardinalities through the same ProjectionEngine. Apply survivor
   override only to real couples. Reuse the existing exact terminal-year and
   opening-estate behavior without inventing a second death.
7. Adapt frozen UI metadata, conditional controls, fan-model population wording
   and comparison capture; then remove the corresponding MainWindow guards.
   Keep single-person PDF export explicitly deferred unless separately approved.

Expected implementation boundary: approximately 25-35 production files across
Monte Carlo application models/services and UI adapters, plus focused domain,
integration, UI/preview and regression tests. Exact files should be finalized
against the chosen lifetime/input representation. No new mortality mathematics,
financial rules, persisted schema, new-plan wizard or analyzer-ranking framework
is needed. Prefer additive compatibility constructors over forcing unrelated
couple analyzer contracts to accept missing spouse data.

## Random-number compatibility

The existing frozen stream protocol derives bytes from SHA-256 of seed, scenario
index, dimension and version. Couple mortality uses `HOUSEHOLD_MORTALITY = 1`,
version 1, and the ordered joint probability sampler. Market returns retain their
legacy generator; general inflation has its independent dimension.

**Do not replace the couple joint draw with independent primary/spouse draws.**
Even if distributionally equivalent, it changes the death years associated with
each existing seed/index, financial horizons, annual populations and outcomes.

Recommendation: activate the already reserved `PRIMARY_MORTALITY = 2` for the
new single-person path with an explicitly documented version 1. Never create or
consume the reserved spouse stream for a single person. Leave all existing
dimension numbers, byte protocol, joint ordering and couple call sequence intact.
Add independent golden bytes/death outcomes for the new single-person path.

Required tests after migration: seed/index repeatability, count/order independence,
market/inflation prefix identity, no spouse draw, malformed/mismatched lifetimes,
opening death, late death, living+deceased=requested, funded+failed=living,
terminal sample counts, direct-engine financial oracles, paired identical-strategy
zero differences, and unchanged existing couple fingerprint/golden resources.

## Verification and acceptance status

No Stage 4B feature tests were added because production implementation stopped
at the architecture gate. Existing regression verification is recorded below.
It validates the starting baseline, not single-person Monte Carlo support.

- Focused baseline regressions: **33 tests, 0 failures, 0 errors, 0 skipped**;
  BUILD SUCCESS, 13.967 seconds. Log: `target/stage4b-audit-regressions.log`.
  Tests: `MonteCarloRandomStreamsTest`, `MonteCarloWorldGeneratorTest`,
  `MonteCarloInflationRegressionTest`, `MonteCarloPairedWorldTest`,
  `SinglePersonStage3CoupleGoldenTest`, `SinglePersonStage4ACoupleGoldenTest`.
- Existing couple golden and seeded assertions passed without expected-value edits.
- Single-person seeded repeatability is **not yet implemented or verified**.
- No manual single-person Monte Carlo acceptance or new UI preview is claimed.
  The current UI deliberately disables this path, as verified in Stage 4A.1.
- Full non-benchmark baseline run: **1,704 tests, 0 failures, 0 errors, 9 skipped**;
  BUILD SUCCESS, 2:04. Log: `target/stage4b-audit-nonbenchmark.log`. This excludes
  benchmark classes, explaining the difference from the prior complete-suite
  total of 1,725 tests and 13 skips. No tests or assertions were changed or skipped
  by this audit.
- Complete benchmark-inclusive suite is not required for this documentation-only
  architecture stop; it has not been rerun in this audit.

## Final file boundary

Only this new report is changed. No files were staged, committed or pushed.
All production, test and golden-resource files remain at starting HEAD.

`git diff --check`: exit 0. The new report was separately checked for trailing
whitespace; none was found. Index remains empty.

Exact final `git status --short` after baseline verification:

```text
?? SINGLE-PERSON-STAGE-4B-MONTE-CARLO.md
```

The next review should approve or revise the explicit frozen lifetime/input and
population migration above before Stage 4B implementation resumes.

---

## Approved Stage 4B implementation

Implementation started at the same HEAD, `6f3b27f`. The only pre-existing worktree
change was this untracked audit report. The user explicitly approved the
request/world/outcome/population migration and required checkpoint regressions.
No new financial rule, persistence migration or new-plan UI was authorized or added.

### Final domain model and compatibility

`MonteCarloLifetime` is a sealed immutable value with two structural variants:

- `Individual`: exactly one `Life`, Primary.
- `Couple`: exactly two `Life` values, Primary and Spouse.

A Life's optional death year describes **that present person's** timing: an empty
year means survival through a fixed horizon. It never describes an absent person.
Sampled mortality worlds require a death for every actual life. There is no spouse
Life, sentinel death, fake mortality mass or fabricated Person in an Individual.
`terminalDeathYear()` uses only the actual lives. An incomplete Couple cannot be
accepted as an Individual or as a complete mortality world.

The lifetime retains one immutable `HouseholdLifetimeScenario` as an adapter to
the existing projection API. That API still receives the actual frozen plan,
whose `EffectiveHouseholdDeathView` supplies membership. The adapter's empty
spouse date is not the Monte Carlo membership representation. Its consistency
with the structural lives is validated at construction. Retaining the same
adapter object also preserves the existing paired execution identity guarantee.

Legacy constructors accepting a bare `HouseholdLifetimeScenario` explicitly mean
Couple, retaining their previous contract; they never guess composition from an
empty date. New single callers use an Individual. Compatibility accessors remain
for existing couple consumers. `WorldOutcome.secondDeathYear()` rejects an
Individual; neutral financial terminal logic uses `lifetime().terminalDeathYear()`.

### Request → world → outcome → population migration

- `MonteCarloMortalityRequest.individual` freezes primary DOB, Person mortality
  category, primary adjustment, plan-start conditioning and table. There are no
  spouse assumptions or survivor elections. Existing constructors preserve couple
  construction; their common primary inputs use the same individual request type.
  Couple-only metadata is explicitly optional and required-spouse access fails
  clearly on individual requests. No mutable Person/plan is retained.
- The individual generator reuses Stage 4A `IndividualLongevityScenarios` and the
  existing distribution provider. It samples the ordered individual death outcomes
  directly, maps the actual person's birthday death to the existing January 1
  annual convention, and generates economic coverage through the last living year.
- Worlds, comparison worlds and paired/outcome records carry `MonteCarloLifetime`.
  Admission checks composition against the captured request and strategies before
  projection. Single/couple comparisons and caller-supplied mismatched worlds fail
  explicitly rather than fabricating or ignoring a person.
- Mortality execution uses the same ProjectionEngine and exact ending-year API.
  Only couples receive survivor-election overrides. Individual terminal assets
  use the December 31 before the person's death year, or the opening balance when
  death is on the projection opening date. Opening outcomes have no fabricated
  annual row. Dates remain nominal, world-specific terminal dates.
- Fixed execution no longer has the obsolete single-person guard. It uses the same
  configured horizon, reference, generators and reducer as before.
- Single plans without Social Security also work: they use the existing core
  primary-only, zero-own-benefit path. A pension-only plan does not need a fake
  Social Security record or claiming election. Existing couple admission rules
  remain intact.
- Annual results expose `deceasedCount` and the existing common living/funded/
  failed-living counts. `Optional<CouplePopulation>` contains the three couple
  living states only for real couples. It is absent for an individual, who is not
  classified as a surviving spouse. Existing couple accessors retain their values
  and reject individual use where the question is inapplicable.
- Conservation remains requested = living + deceased, and living = funded annual
  samples + failed living. Percentiles are present exactly when samples exist.
  Terminal samples contain completed lifetimes only. No deceased or failed balance
  is imputed as zero. Funding probabilities remain empirical DECIMAL128 fractions.
- Paired comparisons share the same structural lifetime and economic path on both
  sides. A-minus-B, both-completed terminal denominators, annual comparable samples,
  ties, failure statistics, means and Type-7 percentiles retain their existing
  implementations and interpretation. Summary reduction remains cached.

### Random streams and checkpoint results

The couple branch still prepares the same ordered joint distribution, draws from
`HOUSEHOLD_MORTALITY` dimension 1/version 1, and uses the same rejection sampler.
No couple draw order, probabilities, seed derivation or market/inflation generator
changed. Single sampling activates the reserved `PRIMARY_MORTALITY` dimension 2
with version 1. It creates no spouse stream and no joint distribution.

| Checkpoint | Result | Log |
| --- | --- | --- |
| Request/world plus individual sampling | 35 tests; 0 failures/errors/skips | `target/stage4b-checkpoint-world.log` |
| Outcomes | 58 tests; 0 failures/errors/skips | `target/stage4b-checkpoint-outcome.log` |
| Population aggregation | 67 tests; 0 failures/errors/skips | `target/stage4b-checkpoint-population.log` |
| Paired migration | 50 tests; 0 failures/errors/skips | `target/stage4b-paired.log` |
| Final focused financial/domain/Monte Carlo/UI/golden verification | 270 tests; 0 failures/errors; 5 existing skips | `target/stage4b-focused-final.log` |

Checkpoint issues were investigated rather than changing expected values:

1. An overload initially made an existing null-input test ambiguous. The individual
   entry point is now a named factory. Missing-category exception compatibility
   was also restored.
2. The outcome record migration changed its automatic diagnostic string. An existing
   seed-417 financial fingerprint hashes that string. Couple `toString()` now
   preserves the old format and every financial field. The original hash passes
   unchanged, proving the discrepancy was representational, not numerical.
3. Paired regression tests require the **same** timing object, not merely equal
   timing. Caching the validated projection adapter restored that identity. Both
   original assertions pass without modification.
4. Three previous-stage guard tests expected Monte Carlo to remain disabled/rejected.
   Those specific availability assertions now verify successful execution. Their
   financial and deferred-report assertions remain intact.

No couple golden/seeded expected values or resources were regenerated. Existing
Stage 2 deterministic couple financial golden rows, Stage 3 and Stage 4A analyzer
goldens, inflation financial fingerprints, mortality/market streams and paired
regressions all pass in the focused verification.

### UI integration and reporting boundary

MainWindow enables normal Monte Carlo for Primary-only plans. Comparison requires
a saved baseline with matching household composition. The single input panel
shows Primary mortality adjustment only; spouse/survivor controls are hidden and
unmanaged and do not participate in validation. Existing session conditioning
semantics are preserved: Monte Carlo conditions at projection start.

Frozen names, settings and populations drive completed results. The single annual
readout says living, funded, failed living and deceased. Terminal wording refers
to the person's death. No spouse income, survivor state, couple mortality axis or
second-death interpretation is presented for a single-person result. Chart
selection uses cached data, and edits mark results stale without rerunning.

Single-person PDF export is disabled with explanatory help and guarded again in
`MonteCarloPdfReportAdapter`. No report layout or PDF financial semantics changed.
Existing couple PDF export remains supported. Broad single-person CSV/PDF/reporting
remains the later reporting milestone; no new-plan wizard was implemented.

### New tests and actual UI acceptance

`SinglePersonMonteCarloTest` adds nine tests covering structural lifetime
cardinality, incomplete/mismatched worlds, the dedicated Primary stream and
repeatability across generation order/count, outcomes and population conservation,
opening death versus funding failure, fixed and paired identical-strategy results,
and pension-only admission. Its realistic direct-engine oracle checks every annual
asset observation and terminal assets/net worth/estate/taxes, actual pension and
expense cash flows, own Social Security, absence of spouse/survivor results,
RMDs/Roth, Medicare participant count and unchanged persisted balances. A separate
projection beyond the death boundary verifies own Social Security/pension cessation,
no RMD/Roth after death, and no deceased-person Medicare cost.

The final admission test covers an opening-date lifetime that would otherwise
execute no annual rows. It rejects invalid single-person joint ownership before
requesting any world. To reuse rather than duplicate the existing rule,
`ProjectionEngine.validateSinglePersonProjection` is now public static; its body
is unchanged. Monte Carlo invokes it at mortality admission. This visibility and
documentation change is the only ProjectionEngine edit. Tax, withdrawal, income,
expense, RMD/Roth and estate formulas are unchanged. The same admission is used
for individual longevity comparisons.

`SinglePersonHouseholdUiTest` adds an acceptance test using the existing actual
Household editor and account/pension/Social Security dialog result converters.
It creates a Primary-only plan, saves JSON, starts a new plan, reloads the saved
plan, projects it, runs fixed and longevity analyses through the actual worker
services, and renders completed results. Both runs use seed 417 and stochastic
inflation. It then captures a single-person baseline and runs longevity comparison.
It checks absent spouse controls, no spouse synthesized after reload or execution,
frozen result composition, PDF guards, stale edits, and no analysis rerun on
selection/edit. Existing all-three-nine-strategy analyzer tests remain unchanged.

This is automated interaction with real JavaFX controls and real projections,
followed by visual inspection of the rendered images. It is not a claim that a
human manually clicked through native file dialogs.

Inspected previews, each **1900 × 1040**:

- `target/single-stage4b-preview/fixed.png`: Primary-only SS marker, reference and
  RMD/Roth shading; funding/table/details fit without overlap or horizontal scroll.
- `target/single-stage4b-preview/longevity.png`: Primary factor/category only;
  nominal death-date disclosure, terminal table and both annual readout lines fit.
- `target/single-stage4b-preview/longevity-late.png`: year 2069 has 1 living/funded,
  0 failed living, 99 deceased out of 100. The small-sample warning is visible;
  there are no couple-survivor labels. Selection did not rerun analysis.
- `target/single-stage4b-preview/comparison.png`: matched single lifetimes, cached
  zero deltas for identical strategies, neutral signed axis/zero line and complete
  population readout. Details collapsed and single PDF action disabled.

All four images were opened and visually inspected, not merely generated.

### Manual reproduction steps

1. File → New; Household: leave **Include spouse (optional)** unchecked. Enter
   Primary DOB `02/01/1965`, mortality category Female, and optional name.
2. Add a Primary Brokerage account with sufficient test funding (the UI fixture
   uses Brokerage $400,000, Traditional IRA $700,000 and Roth IRA $100,000).
   Apply Household. Configure projection start `01/01/2027`, length 20, and
   explicitly select the appropriate filing status (Single for this fixture).
3. Add own Social Security with FRA monthly benefit $3,000 and claiming age 67.
   A Primary single-life pension and household expense may be added; neither is
   a prerequisite merely to test Monte Carlo admission.
4. Save As a test JSON, reopen it, confirm no spouse, and recalculate projection.
5. Analysis → Monte Carlo Retirement Analysis. Run Fixed Lifespan with seed 417;
   then select Longevity-Adjusted and run with Primary adjustment 1.00. General
   inflation may be switched to Stochastic. No spouse/survivor entry is required.
6. Inspect funding, terminal percentiles and annual populations. Use End to select
   a late year. PDF export intentionally remains disabled for this household.
7. Save a baseline and use Monte Carlo Strategy Comparison. An unchanged plan and
   its baseline should show identical funding and zero paired monetary differences.

### Final verification and exact boundary

Full-suite results and exact status are recorded below after the final runs.

The implementation boundary is **28 files**: 23 production Java files (22 modified,
one new), four test Java files (three modified, one new), and this report. The
exact status block below is also the complete changed-file inventory.

- Final focused verification: 270 tests, 0 failures, 0 errors, 5 existing skips;
  BUILD SUCCESS in 41.206 seconds.
- Final complete non-benchmark verification: **1,714 tests, 0 failures, 0 errors,
  9 existing skips**, BUILD SUCCESS in 1:57. Log: target/stage4b-nonbenchmark.log.
- Benchmark-inclusive verification: **1,735 tests, 0 failures, 0 errors, 13 existing skips**, BUILD SUCCESS in 5:10. Log: target/stage4b-full.log. The only subsequent production change corrected single-person funding-tooltip grammar; the affected JavaFX acceptance test was rerun separately as recorded below.
- Existing deterministic couple financial, couple analyzer golden and seeded
  Monte Carlo expected values are unchanged. No golden resources were edited.
- No new tests were skipped or disabled to pass this milestone.
- git diff --check: exit 0. The new files were also checked separately for
  trailing whitespace, since untracked files are not included in ordinary diff.
- No persistence/schema, generator mathematics, financial formulas or report
  layouts changed. The sole ProjectionEngine edit exposes the unchanged
  admission validator, as described above.
- Nothing was staged, committed or pushed. Index remains empty.

Exact git status --short:

```text
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloAnalyzer.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloComparisonWorld.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityAccumulator.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityAnalysisResult.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityAnnualResult.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityRequest.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPairedOutcome.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloRandomStreams.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonAccumulator.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonAnalyzer.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonRequest.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloWorld.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloWorldGenerator.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java
 M src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloFanModel.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityPresentation.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPdfReportAdapter.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPresentation.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonRunService.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonView.java
 M src/test/java/com/daviddunn/retirementplanner/domain/model/SinglePersonHouseholdTest.java
 M src/test/java/com/daviddunn/retirementplanner/domain/projection/SinglePersonCoreProjectionTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/SinglePersonHouseholdUiTest.java
?? SINGLE-PERSON-STAGE-4B-MONTE-CARLO.md
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloLifetime.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/SinglePersonMonteCarloTest.java
```

### Benchmark and final review notes

The complete suite included the existing 5,000-world paired benchmarks. Fixed execution: single 15.019 seconds, paired 29.658 seconds (1.975 ratio); reduction 0.016909 seconds, approximately 0.0077 MiB retained summary. Longevity execution: single 12.009 seconds, paired 23.488 seconds (1.956 ratio); reduction 0.013163 seconds, approximately 0.0109 MiB retained summary. These are local measurements, not performance guarantees.

Final wording review corrected the single-person funding tooltip to say obligations through the person's death, without the leftover household/second-death phrase. A semantic assertion was added to the existing UI acceptance test. This changed no calculations, layouts or test counts.

Post-wording JavaFX acceptance verification: 10 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS in 19.714 seconds (target/stage4b-tooltip-final.log). The full and non-benchmark suite totals above precede only this tooltip wording correction and its additional assertion. All implementation checks are complete; Stage 4B is ready for review. Single-person reporting/PDF remains guarded and deferred. Nothing was staged, committed or pushed.
