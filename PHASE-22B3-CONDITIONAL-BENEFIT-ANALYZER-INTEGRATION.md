# Phase 22B3 — Conditional Benefit Analyzer Integration

## Checkpoint A — execution and compatibility contract (before public admission changes)

Starting branch main; HEAD 1bf90ad5c9d594015d75b5dbcc070e50d294954a. The index is empty. Accepted B1/B2 changes are present. The starting status and SHA-256 inventory are retained in target/phase22b3/initial-status.txt and initial-hashes.json. No real retirement plans are used.

### Selected compatible extension

Reuse the B2 evaluator exclusively through ProjectionEngine. Preserve January 1 deaths, inclusive payment months, original DOB age triggers, DECIMAL128 arithmetic, annual HALF_UP cents, fully taxable non-pension source classification, and retained nonqualified cash settlement. No RNG or tax-law changes.

An immutable analyzer capability identifies ANALYZER_ANNUAL_CONDITIONAL_BENEFITS_V1, analysis family, exact scenario protocol, ordered candidate roles and original frozen input documents/digests. Each document retains original definitions and IDs. A definition digest authenticates the exact collection. At least one candidate must contain benefits; disabled candidates remain explicitly bound independently. At most two bounded original documents are retained, never scenario receipt histories or random draws. Existing document limits apply. The capability is integrity/ownership provenance under the application's existing digest architecture, not a cryptographic signature.

| Result | Payload | Envelope | Stored fingerprint | Capability / algorithm |
|---|---|---|---|---|
| Legacy MC | V1 | V1 | V1 | Unchanged; temporary numeric V2 remains read-only compatibility |
| Standalone stochastic expenses | V2 | V1 | V3 | Existing ExpenseExecution unchanged |
| Paired stochastic expenses | V3 | V1 | V4 | Existing PairedExpenseExecution unchanged |
| Benefit-enabled standalone MC | **V4** | V1 | **V5** | New analyzer capability; optional existing standalone ExpenseExecution |
| Benefit-enabled paired MC | **V5** | V1 | **V6** | New analyzer capability with independent Current/Baseline; optional existing PairedExpenseExecution |
| Benefit-enabled Quick/exhaustive/individual deterministic/weighted | Existing family payload | V1 | Existing V1 financial-input contract | Known optional envelope capability plus conditional-benefits=analyzer-annual-v1; digest includes capability |
| Complete deterministic projection | Existing V1 | Existing contract | Unchanged | B2 deterministic-annual-v1 remains distinct, not an analyzer capability |

New combinations are exact and disjoint. A benefit-capable MC payload requires its capability, generated worlds and its selected fingerprint; expense capabilities must match each frozen candidate. Non-MC enabled artifacts require the new tag/capability together, verified original financial/context fingerprints and retained source binding where annual evidence exists. Legacy wire output omits the optional property. A narrow streaming reader defaults only that property; all original fields remain required and unknown/duplicate fields remain rejected. No global algorithm-map bump, schema migration, historical rewrite or promotion occurs. B0 pension-selection=death-selected-v2 remains separate.

### Execution / provenance integration matrix

All current guards are ConditionalBenefitAdmission.requireSupported. Verified execution will use a distinct supported-analyzer admission method preserving domain and supported-state validation. Unsupported callers retain explicit guards.

| Family | Authoritative execution and deaths | Isolation / output | Reuse policy | New provenance / history / reporting |
|---|---|---|---|---|
| Couple integrated individual deterministic | IntegratedSocialSecurityStrategyEvaluator → ProjectionEngine; configured deaths | Existing scenario JSON copy retains definitions; retained annual receipts/cash/tax | Independent strategy projections | Analyzer capability at accepted family artifact; frozen assumptions/receipts |
| Couple Quick Comparison | Comparison service → same evaluator, baseline plus candidates | Same original definitions in each isolated claiming context | Exact strategy de-duplication only | Quick envelope capability and original request fingerprint; original report assumptions |
| Couple exhaustive search | Complete search → same evaluator | Compact metrics plus bounded retained annual details | Existing strategy enumeration/order | Exhaustive envelope capability; original documents and retained receipts |
| Claiming grids / heat maps | Grid → same evaluator | Existing isolated inputs; complete projections / derived displays | No separate benefit arithmetic | Completed values and frozen report context; persisted exhaustive heat map uses its family capability |
| Single-person deterministic | Individual search → same evaluator, optional explicit primary lifetime | Date/age PRIMARY benefits retained | Existing nine strategies | Individual envelope capability; original override in fingerprint |
| Couple weighted individual | Weighted evaluator → exact scenario ProjectionEngine | Shared mortality mapper; prior-Dec-31 estate; same evaluator | Full independent scenario evaluation | Weighted analyzer capability, original probabilities/valuation and frozen definitions |
| Single-person mortality integrated | SinglePersonMortalityAnalysis INTEGRATED → exact lifetime ProjectionEngine | Same primary mortality, no fabricated spouse | Independent scenarios | Individual weighted capability; SS_ONLY remains unaffected |
| Weighted comparisons | Comparison → reference weighted evaluator | Original occurrence identities/probabilities and retained aggregates | Disable equivalence, prefix and parallel representative reuse only when benefits exist | Weighted family capability and original baseline/elections |
| Direct continuation evaluator | Reference weighted evaluator fallback for benefits | Same scenario deaths and original definitions | No speculative carrier/prefix reuse for benefits | Same weighted semantic; no legacy fast-path change |
| Standalone MC fixed, couple/single | MonteCarloAnalyzer → ProjectionEngine; configured deaths | Original frozen plan; existing financial reducer | Existing worlds and transient expense paths | Payload V4 / fingerprint V5; original settings/model/report |
| Standalone MC longevity, couple/single | Same engine; existing sampled lifetime | Exact last-death-minus-one horizon; original plan | No new mortality draws | V4/V5 contract with sampled protocol and original mortality inputs |
| Paired MC fixed, couple/single | Comparison analyzer → engine for each candidate; shared fixed lifetime | Independently frozen Current/Baseline | Existing shared economic worlds and expense correspondence | Payload V5 / fingerprint V6, ordered candidate capability/disclosures |
| Paired MC longevity, couple/single | Same paired engine; existing shared sampled lifetime | Candidate-specific definitions and paths; same terminal horizon | No new sampling or reduction | V5/V6 sampled contract; original independent candidate reports |
| Opening last/second-death shortcut | EstateAtSecondDeathCalculator opening snapshot | No financial year exists; both modeled recipients deceased | No projection/evaluator needed | Bound capability records prior-Dec-31/opening metric; no terminal payment injected |

All B1 definitions require a living modeled recipient. At January 1 last death no such recipient remains; post-last-death guarantees/external settlements are not expressible in the approved domain. First-death payments while a recipient survives are evaluated within the existing financial horizon. No horizon extension, terminal-estate deposit or metric reinterpretation is selected.

Supported-state scope remains Michigan, Illinois and existing no-general-income-tax models. Pennsylvania/Iowa conditional classifications remain blocked. Supplied Monte Carlo worlds/paths with benefits remain explicitly unsupported; generated worlds are the supported authenticated subset. SS-only rankings, fingerprints and objectives do not consume benefits.

### Checkpoint sequence

Implement strict capability/codec/fingerprint compatibility and tests before public admission. Then enable integrated and weighted reference paths, generated standalone/paired MC, current/history disclosure and immutable association. Preserve no-benefit arithmetic, legacy wire/fingerprints, random protocols and goldens. Run focused suites, complete normal Maven, actual JavaFX/PDF inspection and final preservation review. Any incompatible historical interpretation or material metric conflict stops implementation for approval.

Implementation and verification results will be recorded below. This checkpoint does not claim completion or tested support.

## 1. Executive summary

Implemented B3 analyzer execution using the accepted B2 financial engine and evaluator. Integrated deterministic, single-person integrated, couple/single-person mortality-weighted, and all eight generated Monte Carlo household/mode combinations now preserve and execute approved conditional benefits. Zero and positive recurring-expense volatility are supported together with benefits. Current and Baseline retain independent definitions and expense capabilities.

No benefit mathematics, tax rules, mortality tables, random generators, expense sampling/correspondence, projection financial arithmetic, insurance products or editors changed. No-benefit paths retain their previous algorithms, wire formats and fingerprints. B3 adds explicit result capabilities rather than borrowing B2's deterministic stamp. Historical validation and export do not rerun payments or projections.

The complete normal regression passed: 4,387 tests, 4,341 passed, zero failures/errors and 46 existing skips. Native Windows operator acceptance remains required; this report does not authorize B4.

## 2. Supported analyzer matrix

The Checkpoint A table identifies actual execution boundaries, not merely potential support in ProjectionEngine. These boundaries are connected and covered by focused tests.

| Analyzer | Couple | Single person | B3 policy |
|---|---|---|---|
| Individual strategy integrated deterministic evaluation | Supported | Supported | Same original benefits, isolated claiming context |
| Quick Comparison | Supported | Not a separate existing single-person workflow | Original SS candidate ordering/objective retained |
| Complete integrated search / claiming grid / heat map | Supported | Existing nine-age search supported | Completed financial values, no separate benefit arithmetic |
| Longevity-weighted integrated individual evaluation | Supported | Supported through individual mortality integration | Existing probabilities, discounting and terminal metric |
| Longevity-weighted strategy comparison | Supported | Existing individual nine-strategy comparison supported | Reference scenario execution for benefit-bearing inputs |
| Standalone MC fixed horizon | Supported | Supported | Generated worlds; zero or positive expense volatility |
| Standalone MC longevity-adjusted | Supported | Supported | Existing sampled mortality and exact terminal horizon |
| Paired MC fixed horizon | Supported | Supported | Independent Current/Baseline, shared existing worlds |
| Paired MC longevity-adjusted | Supported | Supported | Independent benefits/expenses, same sampled lives |
| SS-only | Unchanged | Unchanged | Benefits excluded from objective and financial fingerprint |

All eight MC combinations support Current-only benefits, Baseline-only benefits, identical definitions and differing financial definitions. Existing demographic restrictions remain. The supported tax subset is still Michigan, Illinois and existing no-general-income-tax models. PA/IA conditional-income classification remains explicitly unavailable. Existing no-benefit state support is unchanged.

## 3. Admission changes

ConditionalBenefitAdmission.requireAnalyzerSupported delegates to the accepted B2 domain/household/state validation. Explicit approved analyzer roots call it before financial work or shortcut publication. The original unsupported-feature guard remains available rather than becoming an unconditional no-op.

Generated MC roots reject benefit-bearing supplied worlds, supplied paths and internal expense overrides before invoking those suppliers. Existing supplied-input behavior for no-benefit plans remains unchanged. Snapshot interpretation requires the correct enabled capability; a legacy artifact with benefit configuration alone cannot establish execution. Pure input assessment remains separate from financial admission: capability candidate construction validates domain identity, while executable result validation checks supported taxation.

Controller association admission now permits verified B3 families. Association publication additionally requires a conditional analyzer capability when the active plan contains benefits, alongside all original plan/revision/projection/input-fingerprint/run-ticket checks. SS-only retains its distinct path.

## 4. Scenario mortality integration

No new death selection exists. Deterministic integration uses configured timing or the existing explicit single-person override. Weighted and longevity MC use the existing lifetime mapper/context. Fixed MC keeps its existing fixed/configured death semantics. Death remains January 1 of the chosen year; Social Security's separate monthly rules are unchanged.

Direct lifetime oracles cover primary-first, spouse-first, death outside lump coverage, simultaneous last death and opening last death. A missing spouse remains absent, not deceased. Single-person date/age payments require a living PRIMARY; no household death payout to a fabricated recipient is introduced.

## 5. Evaluator reuse and new-completion verification

ProjectionEngine and ConditionalBenefitEvaluator retain their accepted B2 source and financial arithmetic unchanged. B3 adds ConditionalBenefitExecutionVerification at completed analyzer projection boundaries. It compares recorded sources with the same authoritative pure evaluator under the exact scenario context before reducing/publishing a new result, including completed prefixes after funding failure and the fixed deterministic reference.

This is acceptance verification, not another cash-flow calculator: it does not add money, withdraw assets, fund taxes, sample, mutate plans or commit deposits. Empty plans return immediately. An injected engine that strips definitions is rejected rather than publishing a seemingly supported result. Historical codec/report code never invokes this verifier or the evaluator.

## 6. Integrated deterministic analysis

IntegratedSocialSecurityStrategyEvaluator preserves definitions through its existing JSON isolation and invokes the authoritative engine for baseline/candidate projections. Quick, claiming grid and complete search share that evaluator. The single-person nine-age result and its existing Break-Even outputs consume completed projections.

Tests retain exact receipt lists across claiming strategies, compare candidate metrics with direct integrated evaluations, verify bounded grids/searches and Quick, and check original plan JSON is unchanged. SS-only values and rankings remain identical after adding benefits; no conditional value is independently added to SS present value.

## 7. Longevity-weighted analysis

Each positive mortality scenario uses its existing effective lifetime and exact last-death-minus-one ending year. Completed engine estate values feed the original probability-weighted/discounted reduction in original order. A one-scenario probability-one oracle independently checks the literal expected payments: $400,000 once plus two years of $30,000 survivor income equals $460,000 gross while the spouse survives. Reverse death order produces the corresponding PRIMARY receipts. Death in 2028 outside the one-day 2027 lump coverage yields only $30,000 recurring income before terminal death.

Simultaneous deaths produce no living-recipient payment. Opening last-death cases preserve opening estate with zero projection calls. No after-last-death promise is introduced or silently added to prior December 31 assets. Such guarantees/estate recipients are outside the stored B1 domain.

## 8. Prefix, continuation and equivalence

For nonempty benefit inputs, weighted comparison disables representative equivalence, prefix continuation and parallel representative reuse and executes the independent reference path. Direct continuation evaluation also falls back to the reference evaluator and reports actual work counts. Direct unverified proof/session entry points reject benefit inputs explicitly.

No-benefit optimization and coordinator behavior is unchanged. Tests compare reference versus fallback outcomes/ordered entries, preserve scenario probability, confirm zero avoided evaluations/continuation comparisons, and test direct proof rejection. This is a deliberate performance limitation, not a financial approximation.

## 9. Eight Monte Carlo configurations

Generated standalone fixed/longevity and paired fixed/longevity execute for couples and single people. The accepted Phase 21 internal expense adapter is reused without modification. Each scenario's completed projection is verified and reduced by the existing metrics/statistics code. No receipt amount is independently added in a reducer.

The execution matrix includes zero and positive expense volatility. Standalone tests compare original generated worlds with direct engine financial outcomes and repeat seeds. Paired tests compare each candidate's annual assets with its own direct projection under the same world, and preserve success/funding/terminal evidence. Identical candidates produce identical outcomes and zero estate deltas.

## 10. Current/Baseline independence

Ordered capability candidates contain distinct roles, frozen documents, exact document/definition digests and explicit enabled states. Baseline-only and Current-only cases retain the disabled side independently. No plan, benefit IDs, paths, percentages or trigger definitions are copied from one candidate to the other.

Snapshot validation rejects a missing capability, swapped roles/documents, wrong family/protocol, manipulated document or definition digest and incomplete expense capability. Material benefit edits alter financial identity; labels and opaque UUIDs do not. Existing independent expense manifests remain bound to their own exact inputs.

## 11. Phase 21 stream preservation

No market, mortality, inflation or expense generator changed. There is no conditional-benefit random dimension. Expense dimension/version 6/1, keyed definitions, duplicate ordinals, absolute-year identity and exact-definition correspondence remain the Phase 21 protocols. Benefits are deterministic given original inputs, year and the already selected lifetime.

Tests compare generated market paths and lifetimes before/after adding benefits. Existing Phase 21 seed/vector/golden suites run unchanged in the complete regression. At zero benefits no new draws, multiply-by-one financial operations or enabled capability are introduced.

## 12. New capability and exact version rules

The selected matrix above is implemented by ConditionalBenefitAnalysisExecution, MonteCarloSnapshot, its streaming reader/mapping, AnalysisSnapshot/AnalysisSnapshotReader, AnalysisVersions, both fingerprint/compatibility paths and the codec.

The analyzer semantic is ANALYZER_ANNUAL_CONDITIONAL_BENEFITS_V1, with algorithm key conditional-benefits=analyzer-annual-v1. Its scenario protocol distinguishes integrated deterministic, independent weighted Jan-1/prior-Dec-31 and generated fixed/longevity MC. It is separate from B2's deterministic-annual-v1 string and B0's death-selected-v2 pension semantic.

Non-MC family payloads and fingerprint V1 remain intact: B1 already classified the optional financial inputs, while the new capability/algorithm distinguishes actual B3 execution. The envelope's known optional field is omitted for old results. Content digest includes it when present. The streaming reader requires every original envelope property and defaults only this new optional field.

MC payload V4/fingerprint V5 is standalone benefits; payload V5/fingerprint V6 is paired benefits. Existing ExpenseExecution or PairedExpenseExecution composes with that capability when configured; missing/mismatched expense provenance is rejected. Correct generated return/inflation/stream/mortality contracts are mandatory. New ordinary fixed results require the corrected B0 tag too. V1, temporary numeric V2, standalone expense V2/V3 and paired expense V3/V4 retain their prior meanings. Unknown tag/capability combinations are rejected; no legacy algorithm map is globally bumped.

Enabled fingerprints include normalized financial benefit inputs, model/protocol and world source, with independent ordered candidate groups and existing expense financial manifests. Original document/definition digests authenticate exact lineage separately; they do not turn labels, IDs or names into financial dependencies. AnalysisFingerprintService adds runtime applicability evidence only, not a redefined financial digest.

## 13. Frozen history, storage and restoration

New integrated completions attach capability only at eligible acceptance. MC results carry capability through core result objects, payload mapping, restoration and lifecycle storage. AcceptedAnalysisCompletion preserves it through association/run changes; cloned AnalysisSnapshot keeps original capability/documents. The codec verifies capability, original input fingerprints, content digest and existing family/numeric/ownership contracts.

B2's retained structural cash/tax/source validation is shared with analyzer history. Historical validation checks stored facts and definitions without evaluating payments. Missing capability cannot authenticate receipt-bearing retained graphs. Legacy configured-but-unproven MC artifacts remain preserved with explicit incompatibility; they are not upgraded, emptied or recalculated. All original no-benefit history remains supported.

Actual eight-family UI tests exercise accepted results, configuration saving, Save As, restart and associated historical loads. Separate paired longevity tests swap both projection directions with a throwing projection engine and preserve both original result payloads. Tests reacquire materialized manifest references after ordinary Save: pending zero-digest placeholders are intentionally replaced by durable digests, not treated as immutable old reference objects.

Replacement cancellation/failure retains the previous successful result. Injected storage failure preserves the prior durable artifact and the new unsaved completion; Retry Save retains the same payload/run identity without simulation or restoration. Closing the lifecycle does not cancel accepted application-owned storage.

## 14. UI and PDF disclosures

ConditionalBenefitDisclosures formats original configured definitions only; it does not evaluate benefits or label future events as current income. It includes monthly versus one-time amount, trigger date/person/age, living recipient, coverage/not-before/end, escalation, fully taxable non-pension classification and household retained nonqualified cash destination.

Integrated/individual report contexts capture these rows before execution. Existing selected-details/weighted methodology sections use the frozen context. Historical report adapters reuse saved contexts, never the current plan. MC result-time detail metadata includes the validated capability's original per-candidate disclosure; standalone Analysis Details now displays it even without stochastic expenses. Comparison discloses Current and Baseline separately, including explicit absence on a disabled side.

Existing PDF adapters consume those original contexts/details. No PDF renderer, chart, ranking/table calculation, View/Save/Cancel workflow or export-directory implementation was changed. Reports distinguish configuration from completed receipt facts and retain original trial count, seed, mortality, inflation and investment assumptions. Actual PDFs were generated for current/history across all eight MC kinds and for Quick, exhaustive, weighted and individual deterministic/weighted results. Quick current/history report objects and extracted text agree after live benefit edits. Charts/tables remain completed original data.

## 15. Financial correctness and reconciliation evidence

- Literal $30,000 survivor years and $400,000 covered one-time payment; reverse death order; no payout outside coverage or after last death.
- Direct explicit-lifetime estate oracle agrees with weighted results and continuation fallback, retaining original probabilities and dates.
- Direct generated-world engine comparisons verify each standalone/paired candidate independently, including stochastic spending and final assets/estate/taxes.
- B2 payment timing, escalation, federal/state source attribution, Roth/RMD/IRMAA, no-growth lump settlement and cash-source/use oracles remain unchanged and execute in the full suite.
- Fresh verification rejects omission rather than fabricating a supported completed result. Zero-benefit legacy document/fingerprint oracles and seeded financial goldens are not regenerated or changed.

## 16. Modified files

Complete B3-specific inventory: target/phase22b3/b3-files.txt and b3-files.csv, compared with starting hashes rather than confusing accepted B1/B2 changes with B3 edits. Paths below are relative to src/main/java/com/daviddunn/retirementplanner.

- app/montecarlo: MonteCarloAnalysisResult, MonteCarloAnalyzer, MonteCarloFingerprintService, MonteCarloMortalityAnalysisResult, MonteCarloPlanDocument, MonteCarloSnapshot, MonteCarloSnapshotDeserializer, MonteCarloSnapshotMapping, MonteCarloStrategyComparisonAnalyzer, MonteCarloStrategyComparisonResult.
- app/projection: new ConditionalBenefitDisclosures; ConditionalBenefitExecutionSemantics (shared structural validation extraction only; B2 semantic unchanged).
- app/socialsecurity: IntegratedRetirementClaimingGridCalculator, IntegratedSocialSecurityCompleteStrategySearchCalculator, IntegratedSocialSecurityStrategyComparisonService, IntegratedSocialSecurityStrategyEvaluator, LongevityContinuationSession, LongevityWeightedContinuationEvaluator, LongevityWeightedIntegratedStrategyComparisonService, LongevityWeightedIntegratedStrategyEvaluator, LongevityWeightedPrefixEquivalencePlanner, LongevityWeightedStrategyEquivalencePlanner, SinglePersonMortalityAnalysis.
- app/socialsecurity/results: AcceptedAnalysisCompletion, AnalysisCompatibilityPolicy, AnalysisFingerprintService, AnalysisResultStore, AnalysisSnapshot, new AnalysisSnapshotReader, AnalysisVersions, new ConditionalBenefitAnalysisExecution, new ConditionalBenefitAnalyzerValidation.
- domain/benefit: ConditionalBenefitAdmission; new ConditionalBenefitExecutionVerification. domain/estate: EstateAtSecondDeathCalculator. persistence: AnalysisSnapshotCodec.
- ui/controller: ApplicationController. ui/montecarlo: MonteCarloAnalysisView, MonteCarloRun, MonteCarloRunService, MonteCarloStrategyComparisonRun.
- ui/socialsecurity: new FrozenConditionalBenefitDetails; IndividualCompletionPersistence, IndividualReportContext, LongevityWeightedAnalysisRequestFactory, LongevityWeightedIntegratedView, SinglePersonIntegratedView, SinglePersonMortalityView, SocialSecurityAnalyzerInputSummary, SocialSecurityStrategyAnalyzerDialog.

New tests: ConditionalBenefitCapabilityTest, ConditionalBenefitMonteCarloExecutionTest, ConditionalBenefitIntegratedExecutionTest, ConditionalBenefitAnalyzerContractTest, ConditionalBenefitAnalyzerReportingTest, ConditionalBenefitMonteCarloHistoryTest and ConditionalBenefitMonteCarloVisualTest. Existing ConditionalBenefitFingerprintAdmissionTest and ProjectionSnapshotPersistenceTest now assert the deliberately enabled B3 contracts instead of B1/B2 rejection, retaining legacy/history checks. This report is new. No build configuration, dependency, financial golden resource or core tax/expense/random mathematics changed.

## 17. Focused results and development corrections

Environment: Java 25.0.4.1, required IntelliJ Maven, local .codex-m2/repository and -DargLine=-Xmx4g. Level 3; no test exclusions added. Counts below overlap and are not cumulative unique tests.

| Evidence under target/phase22b3 | Tests | Result |
|---|---:|---|
| checkpoint-a.log | 46 | Passed before public admission; legacy and MC capability/codec/fingerprint contracts |
| checkpoint-a-integrated.log | 1 | Passed non-MC capability/old-envelope compatibility before public admission |
| monte-carlo-oracles.log | 41 | All generated household/mode/candidate/expense financial-world cases passed |
| integrated-final.log | 26 | Integrated/weighted/admission/capability/legacy rules passed |
| focused-final.log | 81 | All selected financial, capability, historical, reporting and UI tests passed |
| final-financial-contracts.log | 63 | Final financial verification, injected omission, Retry Save and legacy binding passed |
| swap-final.log | 1 | Both projection-swap directions with no recalculation passed |
| visual-sized.log | 1 | Actual MC dialogs in both themes at exact scene sizes passed |
| weighted-visual-final.log | 1 | Weighted control wrapping/structure passed; settled visual inspection separately below |

Development failures were corrected without changing financial goldens or approved rules: a structural validator refactor initially used RetirementPlan where the frozen type was RetirementPlanSnapshot; fixture construction briefly selected an illegal death/same-recipient intermediate state; result equality assertions initially compared identity-based mortality request/candidate objects instead of their retained numerical/capability facts; birthday-January-1 fixtures required authoritative generated claiming dates for weighted strategy validation; restarted tests initially inspected fixed mode instead of selecting the original longevity mode; a Quick report fixture used a different synthetic completion UUID; a swap test retained pending placeholders after durable materialization; and visual tests initially looked for detached footer status under the old parent and captured resize/expansion before layout settled. These are recorded in intermediate logs, not hidden test exclusions or relaxed production financial rules.

## 18. Complete Maven regression

The complete normal Maven run passed on November 6, 2026 at 13:41:25 Eastern, in 14 minutes 42 seconds, exit code 0. Across 492 suites: **4,387 tests, 4,341 passed, zero failures, zero errors, 46 existing skips**. This is 75 additional tests over accepted B2's 4,312. No production or test source was changed after launching this final run; subsequent work was report completion and ignored preview/fixture tooling.

The required invocation was:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-DargLine=-Xmx4g' test
```

No class selection or additional exclusions were used. Original normal opt-in conditions remain unchanged. Existing financial goldens, Phase 21 seeded/vector contracts, B0 pension correction, B2 financial oracles, legacy snapshot/fingerprint compatibility and the new B3 suites all passed. No financial golden expectation or fixture was regenerated. Evidence: target/phase22b3/full-regression.log, full-regression.exit and full-regression-totals.json. XML totals were reconciled using precisely the 492 classes listed in this run, excluding stale reports from unrelated earlier invocations.

## 19. Existing skips and limitations

The 46 existing skips across 33 suites match accepted B2 exactly by class, case name and skip reason; there are zero added, removed or changed skip entries. The complete inventory is target/phase22b3/normal-skips.json, compared with target/phase22b2/normal-skips.json. Normal existing opt-in/disabled conditions are retained. No real-plan audit, production-scale capacity proof or optional external viewer automation is enabled. Native Windows DPI, keyboard/screen-reader interaction and external PDF viewer/save-dialog acceptance remain operator checks rather than automated guarantees.

Known boundaries: PA/IA conditional tax mapping remains unsupported; benefits require generated MC worlds; weighted benefit runs intentionally use the slower reference path; no post-last-death/external/estate settlement or insurance tax exclusion exists. Old artifacts unable to establish benefit execution cannot claim current B3 semantics. Capability integrity uses existing digests/ownership, not a new signing service.

## 20. Resource and performance observations

There is no new random stream or unbounded cache. Scenario expense paths remain transient; benefit definitions stay bounded at 100. No per-trial whole-plan copy or persisted per-scenario receipt log is introduced. New capability storage has at most two bounded original documents; MC also retains its original request documents, a bounded per-result duplication rather than per-trial growth. Existing 256,000-character input, raw/compressed, structural, numeric, horizon and trial limits remain enforced.

New-completion verification is proportional to completed years × definitions and calls the pure evaluator again for authentication; financial settlement is still performed only by the authoritative engine. The empty branch returns immediately. Bounded MC oracle matrices execute in seconds, including dozens of two/three-trial comparisons. These observations do not establish production-scale benefit-enabled exhaustive/weighted performance. The reference fallback may be substantially slower; no correctness checks are weakened for speed.

## 21. Visual and native Windows acceptance

Actual JavaFX dialogs were inspected in Light/Dark at scene sizes 900×650 and 1180×820. MC fixed current-run and paired longevity footers remain visible outside the scroller. Expanded original details wrap long labels and clearly separate Current/Baseline. The weighted methodology/assumptions pane also wraps; its existing wide financial table still requires horizontal scrolling. Natural-height result tabs can leave existing blank space below content; the inspected assumptions are reached using the established reveal/scroll mechanism, not a layout redesign.

Settled weighted evidence is weighted-settled-<THEME>-<WIDTH>.png with geometry files, generated by the bounded ignored WeightedPreview helper after real layout pulses. Early weighted maximum-scroll screenshots were blank/unsuitable for acceptance and are not the claimed visual evidence. MC geometry assertions verify exact scene dimensions and footer bounds. Representative PDF chart/assumption/table pages were rendered with PDFBox and inspected: no new overlap or missing disclosure; normal pagination wraps the additional assumptions. Evidence is under target/phase22b3/reporting.

Native acceptance should use the isolated synthetic-benefits-comparison.json and synthetic-single-benefits.json in that folder. Creation preserves any existing file. The couple fixture has Current $2,500/month and $400,000 first-death lump, versus Baseline $1,500/month and $300,000 lump, with 15% recurring-expense volatility. These are explicitly fully taxable synthetic receipts, not a tax-free insurance product. The single fixture has scheduled PRIMARY income and a no-benefit baseline.

- Run and save ordinary projections first; confirm existing B2 cash/tax reporting and immutable baseline behavior.
- Exercise integrated individual, Quick, grid/exhaustive/heat map and weighted analyses; SS-only rankings must remain SS-only. Start with bounded requests/lowest offered trial counts.
- Exercise all eight MC modes with zero/positive expense volatility and Current-only/Baseline-only/different benefit assumptions; inspect original Analysis Details.
- Check $400,000 first-death coverage, survivor monthly commencement/cessation, age/date schedules and no post-last-death household payment.
- Cancel, close during work, edit inputs during work and provoke an ordinary failure; previous accepted results remain available and current feedback is visible.
- Save, Save As, restart, swap both projection directions, edit current definitions and open original History; original values/capability/assumptions must remain unchanged.
- Verify native PDF View/Save/Cancel and Phase 18 export directory, charts/tables and historical original assumptions. Retry Save must not recalculate.
- Check Light/Dark, compact/standard windows, native DPI, keyboard navigation and screen-reader feedback. Check accurate PA/IA and supplied-input limitations.

## 22. Git preservation

Branch and HEAD remain unchanged; index empty. All 55 accepted starting paths still exist. SHA-256 comparison identifies 34 byte-identical paths and 21 necessary B3 overlaps; B3's own inventory contains 60 paths including this report. The B1/B2 reports, accepted 22A/B0 documentation and financial golden resources are preserved. The final combined status has 94 paths, comprising accepted work plus B3 changes. Full status/hash evidence remains in target/phase22b3.

No plan owned by the user, existing backup, saved historical artifact or running application was accessed, overwritten or closed. No staging, commit, push, reset, clean or revert occurred. Verification fixtures/logs/images remain under ignored target. Final status, HEAD and preservation hashes are recorded in final-status.txt, final-head.txt and final-preservation.json. git diff --check passed; untracked B3 source/report whitespace was checked separately. HEAD remains 1bf90ad5c9d594015d75b5dbcc070e50d294954a on main, with zero index entries and zero missing starting paths.

## 23. Deferred work and approval gate

B4 editors and subsequent reporting refinements are not started. Insurance premiums/funding, annuity basis/cash value/surrender value, excluded life proceeds, external beneficiaries, estate settlement and post-last-death guarantees remain outside B3. No Social Security-only objective, financial metric, mortality table, state-law scope or Phase 21 stochastic model was expanded.

Stop after B3 verification and this report. Request native Windows acceptance before proceeding to Phase 22B4. Do not commit without separate authorization.
