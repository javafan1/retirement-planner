# Single-Person Plan Support — architecture review gate

## Status and starting state

Starting HEAD: `c42efa4 Add app-wide input tooltips`.
Initial `git status --short`: empty; index and worktree clean.

**Implementation is not complete. Production changes have not started.** The
read-only audit established the broad architectural change described in section
32 of the request. This document records the blocking contracts, proposed design,
migration risks and estimated boundary for review before a broad rewrite. It is
not a certification that every consumer has been exhaustively traced or tested.

## Confirmed architectural blockers

| Area / source | Actual assumption and consequence |
| --- | --- |
| `domain/model/Household` | Constructor requires non-null primary AND spouse. Income/liability aggregation dereferences spouse. JSON cannot currently represent the requested absent spouse. |
| `domain/factory/RetirementPlanFactory` | Creates two blank Persons for every new plan. |
| `domain/model/RetirementPlan` | Holds a final Household; composition replacement needs a deliberate update path, not an uncoordinated null assignment. |
| `ui/views/HouseholdView` | Always loads, validates, compares and applies both PersonCards. Both panes are permanently present. |
| `domain/projection/HouseholdLifetimeScenario` | Two optional death years; empty means survival through the horizon, NOT absent person. |
| `domain/projection/EffectiveHouseholdDeathView` | Missing death date means alive. JOINT is alive unless both owners are deceased; terminal/death semantics have no absent-member state. Merely leaving spouse death empty would create a phantom survivor. |
| `domain/projection/ProjectionEngine` | Spouse reporting age, owner eligibility, income, Medicare coverage and filing-status transitions consume the paired household/death model. Must adapt membership centrally, not just prevent null dereferences. |
| `domain/projection/SocialSecurityProjectionIncomeProvider` | Advanced monthly path is for two modern-cohort people. Explicit lifetime scenarios reject the legacy compatibility fallback. Single-person longevity cannot simply reuse the current fallback. |
| `domain/socialsecurity/analysis/SocialSecurityStrategyRequest` | Requires both retirement elections; validates both birth/death/survivor dates and resolves the later death. |
| `SocialSecurityHouseholdClaimingStrategy` | Requires both retirement dates and both survivor elections; primitive spouse age has no absent representation. Used as strategy identity beyond the calculator. |
| `SocialSecurityJointMortalityScenario` | Requires both death dates and both marginal probabilities plus joint probability. |
| `HouseholdLongevityScenarios` / factory | Always builds two marginal distributions and their joint scenarios. Weighted integrated evaluation consumes those scenarios. |
| `app/montecarlo/MonteCarloMortalityRequest` | Explicitly requires two people, two birth dates/categories/adjustments; execution election is survivor-oriented. |
| `MonteCarloWorldGenerator` | Samples a joint scenario and ends at max(primary death, spouse death) minus one. Not a one-person sampler with an optional second draw. |
| `MonteCarloMortalityAnnualResult` | Reconciliation is expressed as both alive + primary only + spouse only, with both deceased outside living population. Needs explicit household membership in result semantics. |
| `domain/income/HouseholdPensionIncomeCalculator` | Iterates `List.of(primary, spouse)` (rejects null); survivor eligibility is the opposite owner and relies on death-view presence semantics. |
| `domain/rmd/HouseholdRmdCalculator` | Retrieves both persons and invokes owner-specific processing; default eligible owner set includes both. Membership must determine eligibility without changing RMD formulas. |
| `domain/roth/ProjectedPortfolioRothConverter` | Primary-then-spouse order and default owner set. Same-owner transfer behavior can remain; membership validation must prevent nonexistent-owner sources. |
| `ui/dialogs/AccountDialog`, `PensionDialog` | Spouse is offered as an owner; pension excludes Joint but does not model optional household membership. Account Joint treatment differs by account type. |
| `TaxAssumptions` / `FilingStatus` | Status is explicitly stored; convenience constructor defaults to MFJ. Enum also has Single, MFS, HOH and qualifying surviving spouse. Composition must not silently overwrite filing status. Enum presence alone does not prove every rules dataset supports every status. |
| `domain/baseline/RetirementPlanSnapshot` | Retains Household/portfolio/assumption references. Composition edits must not mutate an existing baseline through shared references. |
| SS analyzer request factory / complete integrated search request | Explicitly rejects absent primary or spouse. Search/grid/result identities use paired retirement and survivor elections. |
| `ui/breakeven` presentation/view/chart | Dereferences spouse metadata and presents probability at least one spouse alive. One-person survival must replace joint survival, not be fabricated. |
| `app/export/IntegratedAnalyzerReport` / exporter | Heat map validates Cartesian primary-age × spouse-age cells; export renders a spouse axis. Needs a one-dimensional table path. |
| `ui/montecarlo/MonteCarloPdfReportAdapter` | Frozen metadata includes spouse categories/adjustments; annual tables show joint-life states and second-death wording. Must retain frozen/no-rerun architecture. |
| `app/export/ProjectionCsvExporter` | Stable spouse own/survivor/selection and Roth columns. Absence needs an explicit output policy without casual column removal. |
| Projection/Break-even PDF | Projection PDF already filters absent persons in one reporting loop, but death context remains paired. Break-even PDF dereferences spouse and uses joint-survival chart labels. Local tolerance does not establish end-to-end support. |

Broad search covered structural access, spouse elections/birth/death dates,
paired life states, second-death and joint-mortality terms, not only UI labels.
172 production files matched the broad discovery expression. A narrower expression
for spouse accessors/elections/deaths and joint-life states matched 75 files.
These are candidate-review counts, not 172 proven defects or a final edit count.
The discovered `people.get(1)` in comparison run service belongs to a comparison
compatibility check; positional matches require semantic review rather than
mechanical replacement. No owner/person references were found in Expense's
financial model by the ownership search; household expense/death adjustments
still require integration testing.

## Proposed coherent design (not implemented)

1. Keep Household's existing explicit primary/spouse JSON shape. Require primary;
   allow an explicitly absent spouse. Add `hasSpouse()` and ordered present-person /
   present-owner accessors. Keep absent versus invalid-present spouse distinct.
   Missing/null spouse JSON can represent absence without a versioned schema
   migration; existing spouse objects, IDs and ownership must remain untouched.
2. Introduce membership-aware lifetime state. Presence and death timing are separate
   facts: no death date continues to mean alive through the horizon only for a
   PRESENT person. Expose household-deceased and last-living-date semantics shared
   by pensions, Medicare, expenses, taxes, projection and analysis. Preserve existing
   two-person constructor behavior through compatibility factories/adapters.
3. Generalize strategy identity to one required retirement election and an explicitly
   optional spouse election. Survivor elections are inapplicable without a spouse.
   Reuse the authoritative own-retirement calculation; do not fabricate a zero
   spouse or silently route all single analyses through legacy approximations.
   Produce nine one-dimensional 62–70 strategies with a ranked/table UI. Adapt
   integrated evaluation, strategy equality, prefix/equivalence caches and progress
   totals together, not only the request validator.
4. Add a true single-life scenario path using the existing individual mortality
   distribution. Generalize weighted scenario consumers around present members
   and household terminal date; preserve joint probabilities/order for couples.
   Opening-date death still uses opening assets; other deaths retain January 1 /
   preceding December 31 financial timing.
5. Keep paired Monte Carlo execution/reduction conventions. A single-person paired
   world carries one person's shared mortality realization. Require compatible
   household identities/composition for longevity comparison; do not invent a
   mapping between a single current plan and a two-person baseline.
6. Annual results expose requested/living/funded/failed-living/deceased independently
   of optional couple-specific composition detail. Enforce living + deceased =
   requested and funded + failed-living = living. Paired comparable/asymmetric
   funding and A-minus-B monetary denominators remain unchanged.
7. Add household composition controls with shared InputHelp. Removal first inventories
   spouse income, pension/SS sources, liabilities, portfolio ownership, Joint accounts,
   survivor settings and baseline implications. Prefer blocking removal until the
   user explicitly resolves financial records; never auto-delete or reassign.
   A composed replacement must preserve household expenses and invalidate caches
   and analyzer sessions without modifying a saved baseline.
8. Expose filing status independently with validation against supported tax/IRMAA
   rule tables. A newly created normal single plan can explicitly initialize Single;
   changing existing composition must not silently change stored tax status.
9. Validate absent-owner accounts and survivor pensions at plan admission and edit
   boundaries. Proposed Joint policy: disallow creation of Joint ownership without
   a spouse and require explicit resolution of preexisting Joint records before
   removal. Do not reinterpret Joint as Primary behind the user's back.
10. Render membership-aware result rows, charts and frozen report metadata. Keep
    CSV headers stable where contractual; propose empty cells for inapplicable
    spouse data, with documented distinction from observed zero. PDFs/UI omit
    inapplicable spouse/survivor/second-death sections. No exporter runs analysis.

## Random-stream preservation

`MonteCarloRandomStreams` freezes SHA-256-derived dimension streams. Current couples
sample ONE joint outcome using dimension 1 (`HOUSEHOLD_MORTALITY`), version 1;
they do not sample independent primary and spouse streams. Dimensions 2 and 3
are already reserved for PRIMARY_MORTALITY and SPOUSE_MORTALITY. Market generation
retains its separate legacy Random protocol; general inflation is dimension 4.

Recommended single-life implementation defines/version-tests dimension 2 for its
one marginal draw and consumes no spouse stream. Leave the couple joint sampler,
probability ordering, rejection protocol, market and inflation generators exactly
unchanged. This is a proposal requiring new single-life fingerprints, not a claim
that seed compatibility has already been verified by execution.

## Migration risk and estimated implementation boundary

High-risk boundaries are absence versus survival, strategy/result equality,
continuation/prefix cache correctness, weighted scenario probability mass, terminal
dates, frozen metadata, composition edits against shared baselines, and absent-owner
financial records. Making Household nullable alone would permit invalid plans into
these paths and is therefore not a safe incremental delivery.

Working estimate: roughly **70–110 production files plus 30–50 test files** across
domain, application analysis, JavaFX and reporting. This is a planning range from
the dependency audit, not a promised final count or elapsed-time estimate. It
requires several coherent stages rather than scattered null checks:

1. Domain presence/lifetime contracts, composition validation and JSON round trips.
2. Household editor/removal workflow and primary-only deterministic projection,
   taxes/Medicare, pensions, RMD/Roth, accounts and expense integration.
3. Authoritative single-person Social Security, nine-age SS-only and deterministic
   integrated analysis; protect two-person identities and financial goldens.
4. Single-life weighted analysis and membership-aware terminal/result models.
5. Fixed/longevity/paired Monte Carlo plus stream and population regressions.
6. Results, CSV, all PDFs, tooltips, frozen export tests and visual matrix.

Before changing financial code, add focused single-person tests and capture current
two-person regression evidence. After each stage run its targeted tests; finally
run the full non-benchmark suite against the 1,644-test baseline. Existing assertions
must remain intact. The comprehensive single-person fixture must cover taxable,
traditional and Roth assets, pension, SS, recurring spending, RMD/conversion, JSON
reload/reprojection and exports; analysis tests remain separate and seeded.

## Verification / limitations of this audit delivery

- Production, tests, financial behavior, schema, tooltips and exports: unchanged.
- New integration tests, seeded comparisons and manual visual matrix: not performed;
  implementation has not begun, so no single-person functionality is claimed.
- Maven suites: not rerun for this documentation-only review-gate delivery. The
  1,644-test result is the supplied previous baseline, not a new test result.
- No files staged, committed or pushed.
- Only this report is added. The appendix below is the exact broad-search candidate
  inventory to support the next audit stage; incidental wording/demo matches are
  included and must not be mistaken for required modifications.

## Broad-search candidate file inventory

```text
src/main/java\com\daviddunn\retirementplanner\data\RothConversionDemoFactory.java
src/main/java\com\daviddunn\retirementplanner\data\DemoDataFactory.java
src/main/java\com\daviddunn\retirementplanner\app\breakeven\BreakEvenContextFactory.java
src/main/java\com\daviddunn\retirementplanner\app\export\BreakEvenPdfChart.java
src/main/java\com\daviddunn\retirementplanner\app\export\BreakEvenPdfExporter.java
src/main/java\com\daviddunn\retirementplanner\app\export\IntegratedAnalyzerPdfExporter.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\IntegratedRetirementClaimingGridCalculator.java
src/main/java\com\daviddunn\retirementplanner\app\export\IntegratedAnalyzerReport.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\HouseholdLifetimeScenarioMapper.java
src/main/java\com\daviddunn\retirementplanner\app\export\ProjectionCsvExporter.java
src/main/java\com\daviddunn\retirementplanner\app\export\ProjectionPdfExporter.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\IntegratedRetirementClaimingGridCell.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\IntegratedSocialSecurityCompleteStrategySearchCalculator.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\IntegratedSocialSecurityCompleteStrategySearchRequest.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\IntegratedRetirementClaimingGridRequest.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\IntegratedRetirementClaimingGridSurvivorPolicy.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\IntegratedRetirementClaimingGridResult.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\IntegratedSocialSecurityCompleteStrategySearchResult.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\IntegratedSocialSecurityStrategyEvaluator.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\LongevityContinuationSession.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\LongevityEquivalenceCoverage.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\LongevityScenarioContinuationPlanner.java
src/main/java\com\daviddunn\retirementplanner\testutil\ProjectionYearBuilder.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\LongevityWeightedContinuationEvaluator.java
src/main/java\com\daviddunn\retirementplanner\testutil\PersonBuilder.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\LongevityWeightedIntegratedScenarioOutcome.java
src/main/java\com\daviddunn\retirementplanner\ui\console\ConsoleReportPrinter.java
src/main/java\com\daviddunn\retirementplanner\ui\help\RothInputHelp.java
src/main/java\com\daviddunn\retirementplanner\ui\summary\ProjectionYearDetailsPane.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\ProjectionMetricsDifferenceCalculator.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\LongevityWeightedStrategyEquivalencePlanner.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\LongevityWeightedStrategyAggregate.java
src/main/java\com\daviddunn\retirementplanner\app\montecarlo\MonteCarloWorldGenerator.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\LongevityWeightedPrefixEquivalencePlanner.java
src/main/java\com\daviddunn\retirementplanner\ui\views\ResultsView.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\LongevityWeightedIntegratedStrategyResult.java
src/main/java\com\daviddunn\retirementplanner\app\montecarlo\MonteCarloWorld.java
src/main/java\com\daviddunn\retirementplanner\ui\views\ResultsSummaryView.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\LongevityWeightedIntegratedStrategyEvaluator.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\LongevityWeightedIntegratedStrategyComparisonService.java
src/main/java\com\daviddunn\retirementplanner\ui\views\IncomeSourcesView.java
src/main/java\com\daviddunn\retirementplanner\app\montecarlo\MonteCarloStrategyComparisonRequest.java
src/main/java\com\daviddunn\retirementplanner\ui\dialogs\SocialSecurityDialog.java
src/main/java\com\daviddunn\retirementplanner\ui\views\HouseholdView.java
src/main/java\com\daviddunn\retirementplanner\app\socialsecurity\LongevityWeightedIntegratedStrategyComparisonRequest.java
src/main/java\com\daviddunn\retirementplanner\ui\dialogs\ProjectionYearDetailsDialog.java
src/main/java\com\daviddunn\retirementplanner\app\montecarlo\MonteCarloStrategyComparisonAnalyzer.java
src/main/java\com\daviddunn\retirementplanner\ui\dialogs\PensionDialog.java
src/main/java\com\daviddunn\retirementplanner\app\montecarlo\MonteCarloStrategyComparisonAccumulator.java
src/main/java\com\daviddunn\retirementplanner\ui\views\DashboardView.java
src/main/java\com\daviddunn\retirementplanner\ui\dialogs\OpeningRmdDialog.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\ClaimingStrategyHeatMapCell.java
src/main/java\com\daviddunn\retirementplanner\ui\charts\ProjectionChartModel.java
src/main/java\com\daviddunn\retirementplanner\ui\views\AssumptionsView.java
src/main/java\com\daviddunn\retirementplanner\app\montecarlo\MonteCarloRandomStreams.java
src/main/java\com\daviddunn\retirementplanner\domain\income\SurvivorBenefitClaimingPolicy.java
src/main/java\com\daviddunn\retirementplanner\ui\dialogs\AccountDialog.java
src/main/java\com\daviddunn\retirementplanner\app\montecarlo\MonteCarloPairedAnnualResult.java
src/main/java\com\daviddunn\retirementplanner\ui\rmd\OpeningRmdWorkflowService.java
src/main/java\com\daviddunn\retirementplanner\app\montecarlo\MonteCarloMortalityRequest.java
src/main/java\com\daviddunn\retirementplanner\app\montecarlo\MonteCarloMortalityAnnualResult.java
src/main/java\com\daviddunn\retirementplanner\app\montecarlo\MonteCarloMortalityAnalysisResult.java
src/main/java\com\daviddunn\retirementplanner\app\montecarlo\MonteCarloMortalityAccumulator.java
src/main/java\com\daviddunn\retirementplanner\ui\breakeven\BreakEvenPresentation.java
src/main/java\com\daviddunn\retirementplanner\ui\breakeven\BreakEvenChart.java
src/main/java\com\daviddunn\retirementplanner\ui\breakeven\BreakEvenAnalysisView.java
src/main/java\com\daviddunn\retirementplanner\ui\controller\ApplicationController.java
src/main/java\com\daviddunn\retirementplanner\domain\income\HouseholdSocialSecurityResult.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\IntegratedReportContext.java
src/main/java\com\daviddunn\retirementplanner\domain\income\HouseholdSocialSecurityIncomeCalculator.java
src/main/java\com\daviddunn\retirementplanner\ui\montecarlo\MonteCarloAnalysisView.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\IntegratedAnalyzerReportAdapter.java
src/main/java\com\daviddunn\retirementplanner\app\montecarlo\MonteCarloAnalyzer.java
src/main/java\com\daviddunn\retirementplanner\domain\income\HouseholdPensionIncomeCalculator.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\IntegratedAnalysisComparisonPresentation.java
src/main/java\com\daviddunn\retirementplanner\ui\montecarlo\MonteCarloFanModel.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\SocialSecurityStrategyAnalyzerPresentation.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\SocialSecurityStrategyAnalyzerDialog.java
src/main/java\com\daviddunn\retirementplanner\domain\estate\EstatePresentValueCalculator.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\DeterministicHeatMapAdapter.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\SocialSecurityStrategyAnalysisRequestFactory.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\CurrentStrategyBaselineView.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\SocialSecurityStrategyAnalysisContext.java
src/main/java\com\daviddunn\retirementplanner\ui\montecarlo\MonteCarloMortalityPresentation.java
src/main/java\com\daviddunn\retirementplanner\domain\estate\EstateAtSecondDeathSnapshot.java
src/main/java\com\daviddunn\retirementplanner\domain\estate\EstateAtSecondDeathCalculator.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\CurrentStrategyBaseline.java
src/main/java\com\daviddunn\retirementplanner\ui\montecarlo\MonteCarloPresentation.java
src/main/java\com\daviddunn\retirementplanner\ui\montecarlo\MonteCarloPdfReportAdapter.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\SocialSecurityAnalyzerInputSummary.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\ClaimingStrategyHeatMapView.java
src/main/java\com\daviddunn\retirementplanner\ui\montecarlo\MonteCarloStrategyComparisonPresentation.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\SocialSecurityAnalyzerInputMatrix.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\ClaimingStrategyHeatMapModel.java
src/main/java\com\daviddunn\retirementplanner\ui\montecarlo\MonteCarloStrategyComparisonRunService.java
src/main/java\com\daviddunn\retirementplanner\domain\tax\TaxIncomeCalculator.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\ClaimingStrategyHeatMapMetric.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\LongevityWeightedAnalysisRequestFactory.java
src/main/java\com\daviddunn\retirementplanner\ui\montecarlo\MonteCarloStrategyComparisonView.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\LongevityWeightedIntegratedPresentation.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\LongevityWeightedIntegratedView.java
src/main/java\com\daviddunn\retirementplanner\ui\socialsecurity\LongevityWeightedHeatMapAdapter.java
src/main/java\com\daviddunn\retirementplanner\domain\breakeven\BreakEvenAnalyzer.java
src/main/java\com\daviddunn\retirementplanner\domain\breakeven\BreakEvenPlanSummary.java
src/main/java\com\daviddunn\retirementplanner\domain\breakeven\BreakEvenSurvivalPoint.java
src/main/java\com\daviddunn\retirementplanner\domain\breakeven\BreakEvenYearResult.java
src/main/java\com\daviddunn\retirementplanner\domain\financial\Account.java
src/main/java\com\daviddunn\retirementplanner\domain\tax\FilingStatus.java
src/main/java\com\daviddunn\retirementplanner\domain\roth\ProjectedPortfolioRothConverter.java
src/main/java\com\daviddunn\retirementplanner\domain\factory\RetirementPlanFactory.java
src/main/java\com\daviddunn\retirementplanner\domain\model\AccountOwnership.java
src/main/java\com\daviddunn\retirementplanner\domain\model\BeneficiaryRelationship.java
src/main/java\com\daviddunn\retirementplanner\domain\model\DeathScenarioAssumptions.java
src/main/java\com\daviddunn\retirementplanner\domain\model\DeathScenario.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\PersonMortalityCategories.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\LongevitySessionSettings.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\HouseholdLongevityScenarios.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\AnalyzerLongevityAssumptions.java
src/main/java\com\daviddunn\retirementplanner\domain\model\Household.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\HouseholdLongevityScenarioFactory.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityAnnualResult.java
src/main/java\com\daviddunn\retirementplanner\domain\rules\FilingStatus.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityClaimingGridCell.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityClaimingGridCalculator.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityClaimingElection.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityClaimingGridResult.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityDeathAgeMatrixCell.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityClaimingGridRequest.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityDeathAgeMatrixCalculator.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityDeathAgeMatrixRequest.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityDeathAgeMatrixResult.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityHouseholdClaimingStrategy.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityHouseholdLifeState.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityIndependentJointMortalityCalculator.java
src/main/java\com\daviddunn\retirementplanner\domain\projection\ProjectedPersonAssetPools.java
src/main/java\com\daviddunn\retirementplanner\domain\rmd\OpeningRmdCalculator.java
src/main/java\com\daviddunn\retirementplanner\domain\projection\ProjectedWithdrawalAllocator.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityLifetimeResult.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityJointMortalityScenario.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityMonthlyResult.java
src/main/java\com\daviddunn\retirementplanner\domain\projection\HouseholdLifetimeScenario.java
src/main/java\com\daviddunn\retirementplanner\domain\rmd\HouseholdRmdResult.java
src/main/java\com\daviddunn\retirementplanner\domain\projection\ProjectionEngine.java
src/main/java\com\daviddunn\retirementplanner\domain\rmd\HouseholdRmdCalculator.java
src/main/java\com\daviddunn\retirementplanner\domain\projection\FundingFailure.java
src/main/java\com\daviddunn\retirementplanner\domain\projection\ProjectionReadiness.java
src/main/java\com\daviddunn\retirementplanner\domain\projection\ProjectionYear.java
src/main/java\com\daviddunn\retirementplanner\domain\projection\SocialSecurityProjectionIncomeProvider.java
src/main/java\com\daviddunn\retirementplanner\domain\projection\EffectiveHouseholdDeathView.java
src/main/java\com\daviddunn\retirementplanner\domain\projection\summary\IncomeSummaryService.java
src/main/java\com\daviddunn\retirementplanner\domain\projection\summary\IncomeSummary.java
src/main/java\com\daviddunn\retirementplanner\domain\projection\summary\ProjectionMetrics.java
src/main/java\com\daviddunn\retirementplanner\domain\projection\summary\ProjectionMetricsCalculator.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityMortalityWeightedClaimingGridCalculator.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityMortalityWeightedClaimingGridCell.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityMortalityWeightedClaimingGridRequest.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityMortalityWeightedClaimingGridResult.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityMortalityWeightedComparisonCalculator.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityMortalityWeightedComparisonRequest.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityMortalityWeightedScenario.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityMortalityWeightedComparisonResult.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityMortalityWeightedStrategyValue.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityMortalityWeightedStrategyCalculator.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecuritySpousalBenefitCalculator.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityStrategyCalculator.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityStrategyComparisonRequest.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityStrategyRequest.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityStrategyValuationSummary.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecuritySurvivorClaimingOptimizationCell.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecurityStrategyValuationCalculator.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecuritySurvivorClaimingOptimizationCalculator.java
src/main/java\com\daviddunn\retirementplanner\domain\socialsecurity\analysis\SocialSecuritySurvivorClaimingOptimizationResult.java
```

## Final repository checks

`git diff --check`: passed. Exact final `git status --short`:

```text
?? SINGLE-PERSON-PLAN-SUPPORT.md
```

Index remains empty. HEAD remains c42efa4.
