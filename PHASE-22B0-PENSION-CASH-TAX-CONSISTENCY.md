# Phase 22B0 — Pension Cash/Tax Consistency Correction

## 1. Executive summary

**The financial correction and approved compatibility extension are implemented and automated verification is complete.** The final normal Maven suite passed **4,233 tests: 4,187 passed, 0 failures, 0 errors and 46 unchanged existing skips**, in **13:06**. Phase 22B0 is ready for native Windows acceptance; Phase 22B1 is not started.

Ordinary projections now use the existing HouseholdPensionIncomeCalculator.calculateWithSources result for pension cash, federal/state income facts, Roth bracket filling and tax funding. The same year-specific total and immutable per-source attribution already used by lifetime projections now serve both paths. Unrelated lifetime-only RMD/Roth behavior remains unchanged.

The permanent regressions reproduce the original discrepancy in both death directions before correction. They now pass: $0 owner / $2,500 monthly survivor produces $30,000 payable pension cash and $30,000 pension AGI after the owner's death. Positive owner payments, zero survivor elections, multiple pensions, dates, COLA, single-person deaths and downstream financial oracles are covered.

Existing snapshot schemas, frozen documents, input fingerprints, projection UUID associations and MC payload/fingerprint versions are unchanged. New ordinary-calculation completions record a separate corrected pension algorithm semantic. Old potentially affected results remain historical/outdated and readable/exportable; provably unaffected results retain existing applicability.

## 2. Original reproduction and root cause

Synthetic fixtures contain two people, zero Social Security records, zero investment/inflation rates, controlled expenses/assets, pension commencement January 1, 2030, no COLA, owner death January 1, 2031, and the other person alive throughout the horizon. No user plan files are accessed.

| Year | Before ordinary cash / AGI | Existing lifetime cash / AGI | Corrected ordinary cash / AGI |
|---|---|---|---|
| 2030 | $0 / $0 | $0 / $0 | $0 / $0 |
| 2031 | $30,000 / $0 | $30,000 / $30,000 | $30,000 / $30,000 |
| 2032–2034 | $30,000 / $0 annually | $30,000 / $30,000 annually | $30,000 / $30,000 annually |

This is independently checked as 12 × $2,500, in both PRIMARY_DIES and SPOUSE_DIES directions. Separate lifetime control cases check attributed pension gross as well as cash and AGI.

Root cause:

- ProjectionEngine.executeInternal identifies explicit lifetime overrides with lifetimeRun.
- Previously, calculateProjectionYear obtained the authoritative PensionIncomeResult only for lifetimeRun.
- Ordinary calculateTotalIncome suppressed deceased-owner income and added independently selected survivor cash.
- Without the pension override, TaxIncomeCalculator.calculate independently read original-owner pension payments. Those payments could be zero, larger, smaller, or positive after a zero survivor election.
- Tax funding and Roth bracket fill therefore received different pension tax facts from the pension cash selection.

The initial permanent run failed 2 of 2 tests before production edits. The expanded Checkpoint A run was 46 tests: 44 passed, 2 reproduced-defect failures, 0 errors/skips. Logs remain under target/phase22b0.

The user explicitly approved the bounded algorithm/applicability extension after reviewing Checkpoint A. Its selected matrix was recorded in this report before financial engine edits; the compatibility foundation then passed 21 tests before the engine change.

## 3. Authoritative year-specific selection

ProjectionEngine now always captures calculateWithSources(household, projectionDate, effectiveDeathView) as a present immutable result, including an authoritative zero. This has three consequences:

1. Cash calculation skips separate original/survivor pension derivation and uses the selected total.
2. TaxIncomeCalculator's existing authoritative-pension parameter suppresses independent original-owner pension reads.
3. The same source list accompanies pre-conversion tax facts and final/iterative tax funding.

TaxHouseholdContext has a compatible overload accepting the already-selected result. The engine uses it instead of calculating another pension selection while capturing state context. Existing callers of the original overload retain their established behavior.

No pension arithmetic was rewritten. The existing selection calculator retains commencement/end months, COLA, independent survivor dollars, actual household membership and January 1 death-year behavior. It returns zero when the household is deceased and requires a living survivor for survivor payments.

RMD owner eligibility, opening RMD treatment, Roth execution eligibility, stop rules, withdrawal ordering, investment growth, Social Security, taxes and Medicare algorithms were not changed. The lifetime-only active-owner override remains conditional.

## 4. Source attribution and state treatment

IncomeSourceAttribution.pension already freezes:

- Actual recipient separately from the original pension owner.
- Annual gross and modeled taxable amounts.
- Original monthly/survivor dollars, COLA and schedule.
- Original RetirementParticipantFacts.

These sources are preserved, not replaced with a household-income aggregate. TaxIncomeAttribution attaches the source records to the pension bucket and checks attribution limits. Pensions retain the existing fully federally taxable model; no annuity basis/exclusion rules are introduced.

Tests verify multiple deceased-owner pensions, a separate living spouse pension, recipient SPOUSE with originalOwner PRIMARY, and preservation of distinct original participant facts. The reverse death direction is independently tested.

State handling remains exactly within the existing supported framework:

- MichiganTaxCalculator already knows how to consume selected pension totals/sources from context. Federal cash/tax facts now agree with that selection.
- MichiganRetirementEligibility still returns UNKNOWN / PENSION_SOURCE_QUALIFICATION_UNAVAILABLE for pension qualification. B0 does not infer an exemption from either person's birth date or replace original participant facts with the survivor's facts.
- Illinois retains its conservative pension treatment and qualification warning.
- Full financial oracles exercise Michigan and Illinois. Other implemented state suites run in the normal full regression.
- No survivor-specific state exemption or new jurisdiction is claimed.

## 5. Financial oracles and ordinary/lifetime agreement

PensionCashTaxConsistencyTest covers:

| Case | Independent expectation / check |
|---|---|
| Zero owner / $2,500 survivor, both death orders | 0 before death, $30,000/year after death; cash, AGI and sources agree |
| $1,000 owner / $500 survivor, 10% COLA, end June 2033 | $12,000; $6,600; $7,260; $3,993; $0 |
| Owner dies before July 2032 commencement; end June 2033 | $0; $0; $3,000; $3,000; $0 |
| Positive owner / zero survivor | $12,000 before death and zero afterward |
| Multiple pensions with different owners | $13,200 before death and $37,200 after death; three independent sources |
| Both survive | Original $12,000/year owner payment; no survivor substitution |
| Single-person own pension | Scheduled $12,000 / $6,000 / $0 plus existing SS treatment |
| Single-person explicit death | No pension cash/tax source after death; no absent-spouse payment |

For downstream verification, four synthetic full plans combine a survivor pension, a living-owner $2 million traditional IRA, Roth destination, optional taxable assets, statutory opening RMD data, $250,000 spending, and 22% bracket-fill Roth conversions. They exercise both Michigan and Illinois and monthly survivor amounts of $2,500 and $15,000.

The independent oracle replaces the survivor arrangement with an ordinary pension paid to the living recipient for the same years/amounts. Every annual BigDecimal financial getter is compared against that oracle and against the equivalent explicit lifetime scenario. This checks taxes, withdrawals, conversions, RMDs, retained assets, balances and estate values without prescribing their direction.

Assertions additionally establish positive RMDs, tax-funding withdrawals, a positive bracket-fill case, IRMAA surcharge cases, one pension source, original participant facts, unchanged input JSON and exact recipient ownership.

## 6. Cash settlement, RMD, Roth and IRMAA

Pension cash enters guaranteed income once. No separate pension deposit, synthetic withdrawal, or retained-cash adjustment was introduced. Each rich financial case independently reconciles:

guaranteed income + projection-period RMD cash + spending withdrawal + tax-funding withdrawal

against:

ordinary expenses + Medicare premiums + taxes + retained surplus

within the existing two-cent settlement tolerance. All monetary annual results agree with the ordinary living-recipient oracle.

The original tax-funding and Roth iterative calculators consume the selected pension total and sources. They remain responsible for tax-funding feedback. Statutory RMD calculation and portfolio sequencing are unchanged.

Medicare premiums, covered lives, surcharge values and recorded Medicare MAGI agree with the independent oracle. Tests do not assume recorded Medicare reevaluation MAGI must exactly equal the final tax-funding AGI: the existing same-year reevaluation/iteration behavior can leave those values different in both the survivor case and the pre-existing living-recipient control. That behavior is outside B0 and was not changed. No IRMAA lookback redesign is included.

## 7. Selected version and applicability matrix

| Family | Supported legacy contract | Newly calculated contract | Legacy applicability |
|---|---|---|---|
| Complete normal projection | CompleteProjectionSnapshot schema 1; original frozen inputs/digest and annual graph | Same schema, existing provenance string prefixed with exact pension-selection=death-selected-v2; token | Potentially affected ordinary result is outdated. Frozen inputs proving no relevant active pension/death divergence remain applicable. Unreadable or unknown explicit semantic is conservatively not current. |
| Couple Quick / Exhaustive | Envelope 1 / stored fingerprint 1 / original projection=v1 map | Original map plus pension-selection=death-selected-v2 | Current effective-input assessment must establish unaffected status after fingerprint/context equality; affected or unavailable evidence means outdated. |
| Fixed standalone MC | Payload 1, envelope 1, fingerprint 1; existing numeric V2 read-only compatibility | Same payload/fingerprint; additive pension-selection algorithm key | Inspect original frozen request and its actual analysis horizon. |
| Expense-enabled standalone fixed MC | Payload 2 / envelope 1 / fingerprint 3 / ExpenseExecution | Same versions/capability plus additive algorithm key | Exact legacy map remains supported; affected old result is outdated. |
| Fixed paired MC, legacy or enabled | Legacy payload 1 / fingerprint 1, or paired payload 3 / fingerprint 4; envelope 1 | Same versions/capabilities plus additive algorithm key | Either candidate can make the result potentially affected. Each uses its own frozen plan and the shared actual comparison horizon. |
| SS-only, individual deterministic/weighted, couple weighted, longevity MC | Original versions and algorithm maps | Unchanged; SS-only does not project and existing explicit death/lifetime paths already select pension income correctly | Original applicability rules remain; no blanket pension invalidation. |

Important implementation distinctions:

- AnalysisVersions.algorithms remains the **legacy** algorithm map. It was not globally bumped.
- AnalysisVersions.calculatedAlgorithms is used only at new ordinary-calculation completion producers.
- AnalysisSnapshot strictly accepts the exact legacy or exact corrected capability map for expense-enabled MC. Unknown tags/values and unsupported family combinations are not accepted.
- AnalysisCompatibilityPolicy recognizes the new semantic separately from input fingerprint equality. Missing old evidence results in OUTDATED, not failed decoding or empty successful results.
- Runtime Assessment.legacyPensionAffected is applicability evidence only, not part of a serialized financial input digest. Existing three-argument construction remains supported and means unavailable evidence.
- MC checks the actual requested fixed/comparison horizon, including horizons longer than the frozen plan's configured length. A dedicated paired regression covers that boundary.
- CompleteProjectionSnapshot.matches and matchesDeterministicInputs retain exact/configured input comparison semantics. calculationSemanticsCurrent is separate.
- New-result stamps never modify an old snapshot. Existing projection and analyzer schema, digest and ownership checks are retained.

No version identifier was repurposed. In particular, temporary numeric V2 remains its original contract and is not a pension or stochastic-expense model version.

## 8. Historical restoration and immutable associations

ApplicationController now combines input matching with calculation-semantic applicability when assessing current deterministic results, authorizing new accepted-projection associations and capturing a new baseline. Historical inspection and exact-input swap behavior remain separate.

Recorded association classification remains based on its original owner, projection UUID, digests and configured-input identity. An outdated algorithm does not move or relabel that recorded link; separate applicability produces the historical/outdated status. A dedicated assertion verifies both facts together.

Historical/outdated pension feedback is returned after restoration and swaps. Saving the plan does not make an old result newly applicable. Existing frozen original results remain inspectable/exportable and are never recalculated just to assess, save, restore or report them.

New tests exercise real temporary filesystem/controller workflows:

- Save and Save As of a synthetic legacy-provenance projection with baseline.
- Restart/open with an engine that throws if called.
- Original annual graph, projection ID and input digest retained.
- Exact configured-input matching remains true while financial applicability is false.
- New association/baseline promotion cannot relabel old semantics as current.
- An explicit corrected calculation can coexist with the immutable old baseline.
- Swapping both directions with a throwing engine retains each original result and its applicability.
- Legacy and corrected MC standalone/paired artifacts round-trip with exact payloads and fingerprints.
- Unknown enabled algorithm tags are rejected.
- Unknown old integrated provenance is conservatively outdated; explicit unaffected evidence is accepted.
- Correct lifetime artifacts remain applicable even with potentially affected configured pension inputs.

Existing association, corruption, ownership, storage bounds, Save/Load/Swap and failure-preservation suites remain enabled in the full regression. No user plan, backup, sidecar or historical artifact was accessed or rewritten during development.

## 9. Monte Carlo, longevity and random streams

Both fixed standalone and fixed strategy-comparison engines use ordinary projection contexts, so newly computed affected cases receive the correction. Longevity-adjusted scenarios already use explicit lifetime contexts and keep their selected-pension behavior. Single-person integrated death overrides likewise use the existing explicit lifetime path.

No market, mortality, inflation or expense random generator, seed, dimension, expense correspondence protocol or stochastic distribution changed. The Phase 21 expense engine and repeated-year multiplier reuse remain authoritative.

SupportedStandaloneExpenseTest and SupportedPairedExpenseTest passed in the 185-test financial/workflow run, including internal-engine agreement, all four household/mode combinations, seeded reproducibility and paired expense correspondence. Monte Carlo numeric/codec/history/association suites also passed focused verification. The complete normal suite is the final seeded/golden compatibility milestone.

## 10. Reporting and visual inspection

No UI/editor, PDF renderer, report layout, chart layout, navigation, settings or export-directory code changed.

PensionProjectionReportingTest generates corrected-pension.pdf and frozen-pension.pdf under target/phase22b0/reporting. The second export uses the original frozen plan/results after live pension edits. Extracted PDF text and page counts are identical; both reports contain 16 pages. Chart TOTAL_INCOME and TOTAL_TAXES values reconcile directly with completed ProjectionYear values. Export performs no simulation or projection.

Actual PDFBox-rendered annual-table.png and assumptions.png were inspected. The table shows 30K survivor income from 2031 onward, corrected annual taxes, and the original state-tax breakdown. Labels, rows, columns and assumptions are readable without clipping/overlap. Existing compact formatting and unrelated disclosure limitations remain unchanged.

Native Windows Yearly Details/Light/Dark review is still required. No new bespoke theme layout was introduced. Existing JavaFX/PDF/export tests remain part of the full suite.

## 11. Modified files

Production:

1. app/projection/PensionSelectionSemantics.java — new bounded applicability predicate and semantic constants.
2. app/projection/CompleteProjectionSnapshot.java — separate algorithm applicability.
3. app/socialsecurity/results/AnalysisVersions.java — distinct legacy/new calculation maps.
4. app/socialsecurity/results/AnalysisSnapshot.java — exact supported legacy/corrected enabled maps.
5. app/socialsecurity/results/AnalysisCompatibilityPolicy.java — readable historical vs current semantics.
6. app/socialsecurity/results/AnalysisFingerprintService.java — runtime applicability evidence; digest computation unchanged.
7. app/montecarlo/MonteCarloFingerprintService.java — original candidate/horizon applicability evidence.
8. domain/income/HouseholdPensionIncomeCalculator.java — documentation of shared authoritative use.
9. domain/projection/ProjectionEngine.java — selected pension handoff for both paths.
10. domain/tax/state/TaxHouseholdContext.java — reuse already-selected pension facts.
11. ui/controller/ApplicationController.java — new projection provenance and applicability feedback/guards.
12. ui/montecarlo/MonteCarloResultLifecycle.java — stamp fresh fixed MC completions.
13. ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java — stamp fresh Quick/Exhaustive completions.

Tests:

- domain/projection/PensionCashTaxConsistencyTest.java — new permanent reproduction and financial matrix.
- domain/projection/PensionAlgorithmCompatibilityTest.java — new compatibility/immutability coverage.
- domain/projection/PensionProjectionReportingTest.java — new frozen-result PDF coverage.
- domain/projection/SurvivorMechanicsCharacterizationTest.java — independent scheduled-payment oracle and justified corrected expectation.
- persistence/ProjectionSnapshotPersistenceTest.java — controller Save/Save As/restart/swap coverage.
- app/montecarlo/StandaloneExpenseCapabilityTest.java — explicitly construct the legacy envelope when verifying original wire compatibility, separately from fresh completion tags.
- app/socialsecurity/IntegratedSocialSecurityStrategyEvaluatorTest.java and IntegratedSocialSecurityStrategyComparisonServiceTest.java — retain the original no-survivor cases with exact post-death zero-income/tax assertions; add $3,000 survivor-payment variants that still test strategy-dependent tax differences.
- app/socialsecurity/PensionScheduledPaymentOracle.java — independent scheduled owner/living-recipient control for affected grid fixtures.
- app/socialsecurity/SinglePersonStage3CoupleGoldenTest.java and StateTaxPerformanceAuditTest.java — independently verified corrected grid expectations while preserving all original golden resource files.

Paths above are beneath src/main/java or src/test/java/com/daviddunn/retirementplanner respectively. This implementation report is also updated. The pre-existing Phase 22A report remains unchanged.

## 12. Focused verification and development failures

Environment: Java 25; IntelliJ bundled Maven at the repository-mandated path; repository-local .codex-m2/repository; test JVM -Xmx4g. Synthetic/temp repositories only.

Regression classification: **Level 3 — financial calculation and result-provenance integrity**. Focused development checks precede the complete normal suite; no financial/exhaustive class exclusions were added. Existing opt-in conditions remain unchanged.

| Run/log under target/phase22b0 | Tests | Passed | Failures | Errors | Skips | Interpretation |
|---|---:|---:|---:|---:|---:|---|
| before-correction.log | 2 | 0 | 2 | 0 | 0 | Required original failure |
| checkpoint-a-focused.log | 46 | 44 | 2 | 0 | 0 | Both death directions reproduced |
| compatibility-foundation.log | 21 | 21 | 0 | 0 | 0 | Passed before financial engine edit |
| after-correction.log | 50 | 49 | 1 | 0 | 0 | New defect tests passed; old incorrect pension characterization identified |
| downstream.log | 16 | 12 | 0 | 4 | 0 | Synthetic spouse cohort 1953 hit existing advanced-lifetime admission; fixture corrected to 1955 |
| downstream-oracles.log | 17 | 13 | 4 | 0 | 0 | Medicare record contains a bracket without value equality; assertions corrected to compare financial facts |
| focused-integration.log | 134 | 129 | 3 | 0 | 2 | Invalid test assumption that recorded reevaluation MAGI always equals final AGI; replaced with independent control comparison |
| focused-final.log | 137 | 135 | 0 | 0 | 2 | Financial/snapshot/fingerprint/codec verification |
| focused-financial-workflows.log | 185 | 185 | 0 | 0 | 0 | Pension, lifetime, swaps, associations and supported stochastic expense suites |
| final-oracles-and-reporting.log | 36 | 36 | 0 | 0 | 0 | Final financial, compatibility, history and actual PDF evidence |
| verified-golden-oracles.log | 60 | 59 | 0 | 0 | 1 | Every affected grid expectation agrees with the independently scheduled pension control; existing opt-in performance skip |
| final-association-review.log | 69 | 69 | 0 | 0 | 0 | Original association identity remains distinct from outdated calculation applicability; Phase 12 swaps/freshness verified |

These are overlapping runs, not unique cumulative test counts. No production calculation was changed to resolve a mistaken test assumption. No test was disabled, deleted or skipped to shorten verification.

The focused financial/workflow selection included PensionCashTaxConsistencyTest, PensionAlgorithmCompatibilityTest, SurvivorMechanicsCharacterizationTest, ProjectionSnapshotPersistenceTest, SurvivorFinancialMechanicsTest, SupportedStandaloneExpenseTest, SupportedPairedExpenseTest, MonteCarloProjectionAssociationTest, ProjectionSwapTest and AnalysisFingerprintTest. The final oracle/reporting selection additionally covered PensionProjectionReportingTest.

## 13. Full Maven regression

**Final complete run: BUILD SUCCESS, exit code 0.** The mandated IntelliJ mvn.cmd used the repository-local Maven repository, -DargLine=-Xmx4g and plain test, with no test-class selection or added exclusions. Final log: target/phase22b0/full-regression-after-recovery.log; exit code: full-regression-after-recovery.exit.

Final result: **4,233 tests, 4,187 passed, 0 failures, 0 errors, 46 existing skips**. Duration **13:06**, completed November 5, 2026 at 21:57:28 Eastern. Fresh Surefire XML totals independently reconcile to the Maven summary; suite-level evidence is retained in target/phase22b0/full-final-results.csv. The skipped-suite inventory exactly matches the first complete run.

The first complete run (full-regression.log) finished in 13:03 with **4,231 tests: 4,179 passed, 6 failures, 0 errors, 46 existing skips**. Failures were the fresh-vs-legacy envelope test, two no-survivor tax-difference assertions, the affected Stage 3 grid golden, and two performance grid-hash assertions. The independent oracle corrections described below address each; no production financial change was made in response to those assertions.

An intermediate rerun (full-regression-final.log) was intentionally interrupted during an in-flight benchmark for the final association/applicability review. Completed suites recorded 147 tests, 142 passed, 0 failures/errors and 5 existing skips; the in-flight suite has no completed total and is not claimed as verified. The recorded association check remains input/UUID/digest based; financial applicability is assessed separately. The 69-test review passed before restarting the final complete run.

The subsequent full-regression-complete.log run was terminated by the Codex server restart. Its completed suites recorded 660 tests, 647 passed, 0 failures/errors and 13 existing skips; the in-flight suite has no completed total. The process/session was gone and no Maven/test JVM remained; this was not claimed as a successful complete run. No source changes were made for recovery. The normal suite was restarted from the beginning.

No unresolved automated test failure remains. The final complete run occurred after the last production/test edit. Subsequent edits are confined to this report.

Existing normal-suite opt-in/disabled inventory (46 skipped cases across 33 suites, confirmed unchanged in the final complete run):

| Suite | Skipped |
|---|---:|
| IowaProductionBenchmarkTest | 1 |
| MonteCarloCapacityProbeTest | 2 |
| MonteCarloExpenseSamplingBenchmarkTest | 1 |
| MonteCarloHistoricalPdfFidelityTest | 1 |
| MonteCarloSnapshotCodecTest | 2 |
| StochasticExpenseFinancialBenchmarkTest | 1 |
| DavidClaimingAuditTest | 1 |
| DavidMedicareDeathAuditTest | 1 |
| LongevityContinuationBenchmarkTest | 1 |
| LongevityPrefixEquivalenceBenchmarkTest | 1 |
| LongevityWeightedEquivalenceBenchmarkTest | 1 |
| Stage5DRepresentativeBenchmarkTest | 1 |
| StateTaxPerformanceAuditTest | 1 |
| RealisticExhaustiveCapacityProofTest | 1 |
| SocialSecurityProductionMortalityIntegrationTest | 1 |
| IowaMississippiAdmissionWorksheetTest | 4 |
| RetirementParticipantVisualPreviewTest | 1 |
| InputTooltipPreviewTest | 1 |
| MonteCarloComparisonPreviewTest | 1 |
| MonteCarloPdfPreviewTest | 1 |
| MonteCarloPreviewTest | 3 |
| AnalyzerDirectNavigationTest | 1 |
| CoupleSocialSecurityPersistencePreviewTest | 1 |
| IowaIntegratedVisualTest | 1 |
| WeightedRestorationPreviewTest | 4 |
| CompleteWizardPreviewTest | 3 |
| GuidedWizardLayoutPreviewTest | 1 |
| IowaProductionVisualTest | 2 |
| NewPlanFundingFailureTest | 1 |
| NewPlanWizardPreviewTest | 1 |
| NoIncomeTaxProductionVisualTest | 1 |
| PennsylvaniaProductionVisualTest | 1 |
| StateTaxVisualPreviewTest | 1 |

Those opt-in capacity/visual/audit cases are not claimed as newly executed. B0's own financial, provenance, history and PDF tests have no skips. Native Windows acceptance remains separate.

## 14. Financial golden compatibility and justified change ledger

The existing ordinary configured-death characterization and Stage 4 grid expectations encoded the demonstrated incorrect pension-tax selection. Corrected expectations were accepted only after independent scheduled-payment oracles passed.

That oracle splits payments into an owner pension ending December 2030 ($1,000 monthly) and a living spouse pension beginning January 2031 ($550 monthly, 10% annual COLA, ending June 2033). It does not rely on death-selected survivor arithmetic. Explicit annual pension facts are $12,000 / $6,600 / $7,260 / $3,993 / $0. All annual BigDecimal financial getters must agree before the corrected string golden is asserted.

| Year | Original total tax | Corrected total tax | Original ending assets | Corrected ending assets | Original estate | Corrected estate |
|---|---:|---:|---:|---:|---:|---:|
| 2030 | 13.50 | 13.50 | 1,563,616.90 | 1,563,616.90 | 1,312,366.90 | 1,312,366.90 |
| 2031 | 204.00 | 204.00 | 1,610,486.61 | 1,610,486.61 | 1,354,199.11 | 1,354,199.11 |
| 2032 | 2,654.24 | 1,155.90 | 1,656,972.17 | 1,658,470.51 | 1,395,496.05 | 1,396,994.39 |
| 2033 | 1,151.3625 | 482.9025 | 1,698,088.18 | 1,700,299.93 | 1,431,267.77 | 1,433,479.52 |
| 2034 | 174.25 | 174.25 | 1,742,421.78 | 1,744,699.88 | 1,470,096.76 | 1,472,374.86 |

Guaranteed income and $10,000 annual fixed conversions in that characterization remain unchanged. The corrected tax/funding differences carry into subsequent balances through the existing engine. Pre-death results are identical.

No unrelated golden values or seeded random vectors were edited. No golden fixture was regenerated.

### Grid correction evidence

The Stage 4 fixture has a $1,000 owner pension, $500 independent survivor pension, 2% COLA and death in 2032. The independent control schedules $1,000 owner payments through December 2031 and $520.2000/month to the living spouse starting January 2032, with 2% subsequent COLA. All 81 strategy metrics match that control exactly. The original baseline strategy/annual Social Security lines remain exact. Of 81 grid rows, 45 remain byte-for-byte unchanged; 36 have justified downstream tax/funding/asset differences.

For example, primary claiming age 67 / spouse 62 changes total tax from 24.920888 to zero, ending assets from 954,765.99 to 954,790.91, and withdrawals from 6,623.090888 to 6,598.17. Gross pension/Social Security values remain the independently selected stream. Full before/after evidence is saved as target/phase22b0/grid-before.txt and grid-after-verified.txt.

Original resource files single-person-stage3-couple-golden.txt, phase6d1a-financial-fingerprints.txt and phase6d2-illinois-financial-fingerprints.txt remain unchanged. The corrected fixed expectations are separately asserted in test source, after verifying the oracle:

| Golden | Original | Corrected |
|---|---|---|
| Stage 3 full grid text | Original resource retained; original SS prefix still asserted | f000a3d13ae714dd84f6f7a72dc2323642a7d96f906a090326908c3458488e44 |
| Michigan/Florida grid81 workload | a80f3b6e20971ecab2ef37d4beae442005fe988adde051947ba9794ff5c241c9 | e45da6aa1d78844256adc9f6799a2d4ae73afd5d96b59da566cacd3a200807b4 |
| Illinois grid81 workload | 26306d7c4ef3dc77ff85cdea6a9cf8f50b224b28bcb68ecfcd4768f48a309eae | abb25cfee564214e1475f2d9df8a8df9eea276f02cfb6d35c216c040b8f32dac |

All other performance workload hashes remain the accepted originals, including seeded fixed/longevity MC, no-death projections, bracket-fill cases and correctly selected weighted analyses. No financial behavior was changed to satisfy a performance hash.

## 15. Native Windows acceptance checklist

- Open a synthetic couple plan with $0 primary / $2,500 survivor, 2030 commencement and 2031 primary death. Explicitly run a new projection; verify annual survivor cash and pension AGI are $30,000 after death.
- Repeat with spouse pension owner/death; verify actual recipient and original pension participant facts.
- Check positive owner amounts, zero survivor election, owner death before commencement, end-month boundaries, COLA and multiple pensions.
- Check federal/state taxes, Roth bracket fill, RMD use, tax funding, IRMAA, withdrawals, retained assets and estate values in Yearly Details.
- Verify existing state qualification warnings remain; do not assume a new pension exemption.
- Inspect historical affected results without recalculation. Confirm outdated pension-semantic feedback, original numerical values, original frozen inputs and exportability.
- Explicitly recalculate and confirm corrected results are applicable. Save, Save As, restart and swap both directions; confirm each original result retains its identity/applicability.
- Run fixed/longevity standalone and paired Monte Carlo with zero/positive expense volatility; confirm progress/cancel, stored history and original-context PDF exports remain intact.
- Check projection/PDF values, View/Save/Cancel and default export directory; no layout/editor/navigation/settings redesign.
- Check Light/Dark, compact-window status wrapping and Windows DPI. Native acceptance remains required before proceeding to Phase 22B1.

## 16. Git and working-tree preservation

Branch: main. Starting HEAD: e6d8a9cc6c70377b12237d1b25f3f92ca3203b41.

Starting untracked files were the Phase 22A audit, this B0 checkpoint report and PensionCashTaxConsistencyTest.java. The last two are intentionally extended within the approved continuation. The Phase 22A audit SHA-256 remains DAE081E6B37B0DEC5FD59D0F64CEF34E4E563998D9C7B4FA7C88960F11E7A6DD.

No staged changes, commits, pushes, resets, cleaning, reverts or unrelated file overwrites. Production financial plans, backups and historical artifacts were not accessed or modified. Logs, generated synthetic PDFs and images are ignored under target/phase22b0.

Final branch and HEAD remain main / e6d8a9cc6c70377b12237d1b25f3f92ca3203b41. The index is empty. Git status records 19 modified tracked files and 7 untracked files (including the preserved Phase 22A report); exact paths are in target/phase22b0/final-status.txt. The Phase 22A SHA-256 matches its starting preservation record. No golden resource or build-configuration file changed.

git diff --check passed, as did separate trailing-whitespace/EOF checks of all new B0 files. No production/test changes were made after the successful final full run.

## 17. Remaining limitations and stop gate

- Existing state pension qualification/survivor exemption limits are unchanged.
- Existing same-year Medicare reevaluation/final-AGI behavior is unchanged; no lookback redesign.
- Unknown historical ordinary algorithm evidence is conservatively outdated even when it may have happened to produce correct numbers.
- Direct compatibility overloads in TaxIncomeCalculator remain available; projection execution must supply the authoritative selected result, as implemented.
- No new pension election, beneficiary, conditional benefit, insurance or annuity product is introduced.
- Old financial artifacts are not retroactively corrected or relabeled.
- All normal automated verification passed. Native Windows acceptance, including DPI and compact Light/Dark status presentation, remains pending.

**Recommendation: accept Phase 22B0 for native Windows retesting using section 15.** Stop here. Request native Windows acceptance before proceeding to Phase 22B1; do not commit without separate authorization.
