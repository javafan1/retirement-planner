# Phase 21B2 — Supported Standalone Stochastic Expense Execution

## Compatibility checkpoint A — selected contract before execution changes

This checkpoint was recorded before modifying supported execution. Phase 21B1's uncommitted files and the Phase 21A/B1 reports were present at the start and have been preserved.

The current snapshot is a concrete `MonteCarloSnapshot` V1 record. Its provenance creator has seven existing properties; the strict codec requires original creator properties. `AnalysisSnapshot` envelope schema 1 currently accepts fingerprint version 1 only. `TEMPORARY_NUMERIC_V2` is read-only numeric compatibility, not a stored expense capability. Existing algorithm maps are kind-based V1 maps. Internal expense execution is isolated behind package-private wrappers.

Selected matrix:

| Result | Payload | Envelope schema | Stored fingerprint | Compatibility / algorithms |
|---|---:|---:|---:|---|
| Legacy standalone or comparison, expense-disabled | MonteCarloSnapshot 1 | 1 | 1 | Original EXACT_V1 verification and TEMPORARY_NUMERIC_V2 assessment; unchanged algorithm map |
| Generated standalone with positive configured volatility | MonteCarloSnapshot 2 with mandatory immutable expenseExecution capability | 1 | 3 | Explicit EXPENSE_MODEL_V3 contract; existing algorithm tags plus a payload-specific recurring-expenses capability tag |
| Comparison with positive volatility | Not admitted in B2 | — | — | Existing execution guard retained |
| Enabled caller-supplied paths/worlds | Not admitted in B2 | — | — | Explicit unsupported admission retained |

The existing concrete snapshot type will support V2 through one narrowly scoped optional field. Its V1 constructor remains source-compatible. The new field is omitted entirely for V1 serialization, preserving original wire/digest behavior. A focused streaming snapshot decoder will permit only this field to be absent for V1 while retaining strict validation of every original property, unknown fields and explicit versions. The existing `Provenance` creator remains unchanged, avoiding another missing-creator compatibility problem like Phase 19 S3.

`expenseExecution` will identify the frozen V1 expense model, dimension 6/version 1, correspondence version 1, original plan-document digest, and bounded enabled occurrence bindings. It will verify these against the original frozen request document. The request retains the sole full frozen plan document; no per-scenario paths/draws or second complete budget are persisted. Runtime standalone results carry the same immutable capability so capture cannot silently label an enabled calculation as legacy or describe it using a different plan.

Envelope validation will accept only payload-1/fingerprint-1 or standalone payload-2/fingerprint-3 combinations. Capability-specific algorithm expectations will not globally change legacy maps. The original V1 and temporary numeric V2 fingerprint representations remain unchanged; expense V3 reuses only the established numeric-setting equivalence rules and explicitly fingerprints the expense capability. Legacy V1 artifacts with positive configuration but no expense provenance will remain preserved and explicitly unavailable for supported financial interpretation, rather than being upgraded or recalculated.

Affected paths: accepted completion envelope construction; strict snapshot codec; MC input fingerprint/compatibility; lifecycle restoration and acceptance; snapshot mapping; historical result restoration/report adapters; standalone run service/UI admission. Projection association records continue to carry the original input/context digests and UUIDs with no schema change. Save/Load/Save As/Swap continue through their existing repositories and atomic component rules.

Checkpoint sequencing: finish and test this foundation with public positive execution still gated; then enable generated standalone execution through the existing B2 engine; then verify original-context history/PDF disclosures and full Level 3 regression. No incompatible legacy interpretation has been identified in this selected strategy.

## 1. Executive summary

Generated standalone Monte Carlo now supports configured positive recurring-expense volatility for **all four combinations: couple/fixed, couple/longevity-adjusted, single-person/fixed and single-person/longevity-adjusted**. The existing Phase 14 expense sampler and projection integration are reused. The supported adapter adds immutable execution provenance, a distinct fingerprint, strict restoration, and original-input disclosures before public admission is enabled.

Implementation and automated verification are complete. The final complete Maven suite passed **4,101 tests: 4,055 passed, 46 skipped, zero failures and errors**. Phase 21B2 is ready for native Windows acceptance; Phase 21B3 remains unimplemented.

Zero-volatility runs retain the original execution path, V1 wire shape and fingerprint storage. Comparison positive volatility and caller-supplied enabled paths/worlds remain explicitly unsupported. No calculation formulas, random generators, projection/tax/Roth code, plan schemas, PDF exporter, export-directory settings or navigation were changed.

## 2. Provenance foundation and compatibility

`ExpenseExecution` is an immutable capability carrying model `INDEPENDENT_LOGNORMAL_EXPENSE_LEVEL_V1`, dimension/version 6/1, correspondence version 1, SHA-256 of the exact original frozen request plan document, and ordered enabled occurrence bindings (expense list index, normalized definition hash, duplicate ordinal). Validation reconstructs the manifest from the saved original input document and requires exact equality. The full budget remains solely in the existing request plan document; the manifest contains no amounts or descriptions and no scenario draws.

The original `MonteCarloSnapshot.Provenance` fields continue to carry return, mortality, inflation, stream and aggregation versions and generated/supplied-world source. Original settings carry seed/trial count. Payload V2 requires the generated standalone model/protocol combinations and its own validated expense capability. Enabled comparisons and supplied sources cannot masquerade as supported V2.

The narrow streaming deserializer allows the new root field to be absent for V1, preserving every original required creator property under strict decoding. Unknown/duplicate fields, unsupported capabilities, wrong binding/digest, family/version combinations, numeric bounds and malformed artifacts remain rejected. Raw/compressed limits (32/16 MiB), document depth/token/array/number constraints and Phase 19 S3 Social Security compatibility are retained. No existing artifact is upgraded on load.

A legacy V1 artifact with positive expense configuration but no validated supported capability remains codec-readable and preserved, but restoration/financial interpretation is explicitly unavailable. Its configuration alone is not evidence of stochastic execution. No historical data is discarded, rewritten or recomputed.

## 3. Supported execution and admission

The existing `MonteCarloAnalyzer` common fixed and mortality loops detect configured positive volatility on their isolated plan. Only generated standalone sources can construct the supported capability and `InternalExpenseRun.configured` adapter. That adapter builds the already tested sampling specifications and immutable per-scenario bound expense paths. Public methods returning supported results attach the capability; package-private internal experimental wrappers remain separate and cannot be captured as supported results without it.

`ExpenseVolatilityAdmission.requireStandaloneSupported` performs bounded preparation with the existing validations and no random draws. UI and worker service use this standalone gate. The original comparison gate remains in place. Supplied positive sources fail before any caller world/path is read. Invalid counts, dates, negative enabled expenses, invalid rates, incompatible bindings and unsupported coordinates continue to fail explicitly.

The Phase 21B1 Current run footer, persistent validation/failure feedback, progress, cancellation and duplicate-run guard are preserved. Supported positive input no longer receives the obsolete public-execution warning. Actual invalid expense preparation receives field/resource feedback without mutating the plan.

## 4. Expense mathematics and random streams

The Phase 14 model is unchanged: annual relative standard deviation v, log variance ln(1+v²), log mean minus half that variance, and exp(mu+sigma*Z) level multipliers. It remains mean-preserving before financial rounding, independent across expenses/years, without shock compounding or tail caps.

The existing keyed SHA-256 protocol, normalized financial definition, duplicate ordinal, absolute calendar year and expense dimension 6/version 1 are unchanged. Reordering unique entries or inserting an unrelated entry cannot shift existing draws. No durable expense IDs were added. No new draws enter the market, mortality or inflation generators. At zero volatility no expense path or multiply-by-one operation is introduced.

## 5. Financial integration

The scenario-local immutable path enters `ProjectionEvaluationContext.withExpensePath`, using the existing engine's authoritative expense calculation. That retains General/Healthcare/None escalation, first-year proration, active dates, death adjustments, one-time exclusion and zero-budget behavior. One bound multiplier is reused for repeated evaluations of the same year, including Medicare/tax-funding retries.

The existing fixed-budget oracle suite verifies annual spending, withdrawals, RMD usage/prior-December balances, retained assets, tax funding and Roth bracket fill, Medicare reevaluation, funding failure, ending assets/net worth/estate. Public enabled results match the existing internal engine exactly in each household/mode combination. No financial direction is imposed on taxes or conversions; existing full-engine reconciliation is retained.

## 6. Fingerprints, freshness and association

V1 stored fingerprints and the existing read-only `TEMPORARY_NUMERIC_V2` compatibility contract are unchanged. Enabled V3 includes the effective original financial plan plus the explicit model/protocol/binding capability. Its financial fingerprint excludes the exact document digest, because the digest also covers names, plan IDs and baseline data; those remain integrity/provenance inputs rather than accidental new financial dependencies. Existing financial decimal-scale behavior is preserved; analyzer numeric-setting equivalence follows V2 rules.

The codec verifies enabled financial/context fingerprints, exact request binding, envelope timestamp consistency and content digest. Enabled acceptance uses V3 throughout the worker, completion envelope and revision guard. No revision/freshness gate was relaxed. Volatility changes make results outdated without altering the saved artifact. Original UUID/input digest associations remain attached to the original projection across both swap directions and Save As identity cloning.

## 7. Historical restoration and storage

All four supported modes were exercised through the real view/controller/result store and isolated filesystem repositories: accepted run, ordinary Save, Save As, restart, associated result restoration, current-plan volatility edit, both projection-swap directions and historical PDF export. Restored worker callbacks deliberately throw if simulation is attempted. Restoration uses frozen aggregates and original settings without recalculation.

Storage failures retain the completed report as unsaved; Retry Save submits the same completion without running the simulation again. Fault-injected filesystem verification preserves previous bytes, and a successful retry creates a readable backup. Ownership mismatch, corruption and version mismatch remain rejected. Existing atomic writer/component assembly and immutable associations are unchanged.

## 8. Current/history/PDF disclosures

A compact read-only result label identifies active stochastic recurring expenses. Analysis Details and PDFs explain annual relative standard deviation, mean-preserving independent lognormal levels, original configured percentages by expense index, model/dimension/correspondence versions, independence, and one-time/deterministic exclusions. Existing report settings disclose original trial count, seed, market return, inflation and mortality assumptions.

The disclosures are derived from the result's frozen request, never current live percentages. Restoration verifies them against the saved capability. Tests change live volatility from 25% to 17% and confirm historical PDFs still disclose 25%. The existing adapter already includes original plan details in report context, so no new PDF renderer or report layout was introduced. View/Save use the same existing coordinator and exporter; fake openers are used in tests. Zero-volatility report context remains unchanged.

## 9. Modified/new files

Production paths below are relative to `src/main/java/com/daviddunn/retirementplanner/`:

- New `app/montecarlo/ExpenseExecution.java`, `MonteCarloSnapshotDeserializer.java`.
- `app/montecarlo/ExpenseVolatilityAdmission.java`, `InternalExpenseRun.java`, `MonteCarloAnalyzer.java`: supported adapter/admission only.
- `app/montecarlo/MonteCarloAnalysisResult.java`, `MonteCarloMortalityAnalysisResult.java`: optional immutable capability and compatible constructors.
- `app/montecarlo/MonteCarloSnapshot.java`, `MonteCarloSnapshotMapping.java`, `MonteCarloFingerprintService.java`: versioned capture, decoding, identity and restoration.
- `app/socialsecurity/results/AcceptedAnalysisCompletion.java`, `AnalysisSnapshot.java`, `AnalysisVersions.java`, `AnalysisCompatibilityPolicy.java`: payload-specific enabled envelope support; legacy maps unchanged.
- `persistence/AnalysisSnapshotCodec.java`, `JsonAnalysisSnapshotRepository.java`: strict enabled verification and compatible prior-artifact assessment.
- `ui/montecarlo/MonteCarloAnalysisView.java`, `MonteCarloRunService.java`, `MonteCarloResultLifecycle.java`, `MonteCarloSnapshotPresentationMapper.java`: supported admission, original-context disclosure and accepted-result lifecycle.
- `ui/socialsecurity/ProjectionAnalyzerHistoryDialog.java`: payload-specific algorithm expectations only.

New tests: `StandaloneExpenseCapabilityTest`, `SupportedStandaloneExpenseTest`, `StandaloneExpenseHistoryTest`, `StandaloneExpensePreviewTest`. Extended `ExpenseVolatilityAdmissionTest`, persistence `ExpenseVolatilityCompatibilityTest`, `MonteCarloPdfWorkflowIntegrationTest` and the existing untracked Phase 21B1 `MonteCarloRunFeedbackTest` for newly supported admission. `ui/introduction/AnalyzerIntroductionMenuTest` now explicitly verifies the accepted B1 BorderPane/scroll/fixed-footer structure rather than casting the dialog root to ScrollPane. Its menu, no-pre-Continue-work, plan, revision and projection-preservation assertions remain. Existing comparison assertions were retained.

The working-tree diff also includes **pre-existing Phase 21B1** changes to `MonteCarloAnalysisDialog`, `MonteCarloViewTest`, `css/monte-carlo.css`, `MonteCarloRunDiagnostics` and its tests; these were preserved, not independently redesigned in B2.

## 10. Verification record

Testing classification: **Level 3**, because supported financial execution, artifact capability and fingerprints change. All financial golden expectations and permanent test configuration remain unchanged.

Completed checkpoint runs:

| Run | Tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Foundation before public enablement | 42 | 40 | 0 | 0 | 2 existing opt-in |
| Execution, engine oracles, B1 feedback | 66 | 66 | 0 | 0 | 0 |
| Real history/controller/storage integration | 45 | 45 | 0 | 0 | 0 |
| Defensive capability, atomic fault, PDF workflow, enabled UI/resource tests | 29 | 29 | 0 | 0 | 0 |

Logs: `target/phase21b2-foundation-final.log`, `phase21b2-execution.log`, `phase21b2-history.log`, `phase21b2-defensive-preview-final.log`. Counts overlap and must not be summed as unique tests. An initial new-test compile error and preview-fixture failures were corrected; they were fixture issues (decimal-scale loss in a generic test tree and use of an unbound controller for durable preview results), not reasons to relax production safeguards.

The broader initial focused run (`phase21b2-focused-final.log`) ran 597 tests: 583 passed, 2 failed, 0 errors, 12 existing opt-in skips. One failure was the obsolete standalone-rejected assertion (updated to supported execution while preserving supplied-source rejection); the other was the record's automatic diagnostic `toString` including the newly added null capability. The legacy result text was restored for disabled results, retaining both original complete numerical golden hashes **without changing their expected values**. Enabled mortality equality includes capability; its legacy hash/text remain unchanged when absent.

A subsequent 21-test compatibility/preview run passed with zero failures/errors/skips, including the opt-in cached PDF preview (`montecarlo.pdf.preview=true`). Visual review then caught that the new compact UI label pointed to details that the old details renderer did not actually display. The renderer now adds the original worker/restored plan details only for enabled results; current/history tests assert that the original percentage remains present after live edits. An in-progress full run was stopped for this correction, not counted as successful, and restarted on the final source. Preview expansion animation is disabled only in the test fixture to capture the settled scroll position reliably.

Final focused verification (`target/phase21b2-focused-verified.log`) passed: **592 tests, 582 passed, 0 failures, 0 errors, 10 existing opt-in skips**, 1:59. It includes enabled capabilities/execution/history/previews, existing Monte Carlo/RNG/expense tests, snapshot/repository/association tests, Phase 19 compatibility and save tests. Expensive benchmark classes were excluded only from this focused command; they remain included in the unfiltered full command. No permanent test configuration was changed.

Verification uses Java 25.0.4, the required IntelliJ bundled Maven, local `.codex-m2/repository`, and `-DargLine=-Xmx4g`. Command shapes:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' `
  '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' `
  '-DargLine=-Xmx4g' `
  '-Dtest=StandaloneExpense*Test,SupportedStandaloneExpenseTest,*MonteCarlo*Test,*Expense*Test,*AnalysisSnapshot*Test,*AnalyzerAssociation*Test,*AssociationTest,*WeightedSnapshot*Test,*Save*Test,*SocialSecurityReduction*Test,!*BenchmarkTest' test

# Complete normal regression, without test exclusions:
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' `
  '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-DargLine=-Xmx4g' test
```

The separate cached opt-in report run used `-Dtest=MonteCarloMortalityResultsTest,ExpenseVolatilityCompatibilityTest,StandaloneExpensePreviewTest,MonteCarloPdfPreviewTest -Dmontecarlo.pdf.preview=true`, passing all 21 tests. Existing cached 5,000-world report fixtures were used, not new simulations or external viewers.

The first completed full run (`target/phase21b2-full-regression-final.log`) executed **4,101 tests: 4,053 passed, 0 assertion failures, 2 errors, 46 skips**, in 13:04. Both errors were the same obsolete root-node cast in the menu test for the two household shapes; they were not calculation failures. The test was updated to assert the accepted B1 fixed-footer layout explicitly. A targeted follow-up (`target/phase21b2-menu-regression.log`) passed **36/36**, zero failures/errors/skips, covering that menu test plus run feedback, enabled previews and enabled history. Production source was unchanged by this final correction. The complete unfiltered suite was then rerun with the corrected test included; its result is recorded below.

The normal full-suite skips are existing opt-in tests and four pre-existing Mississippi worksheet assumptions pending authoritative guidance. No tests were disabled or weakened. Full skip details are preserved in `target/phase21b2-skip-inventory.json`. The opt-in PDF preview was also run separately and passed; no real-plan audit was enabled or user data accessed.

### Final complete regression result

`target/phase21b2-full-regression-verified.log`: **BUILD SUCCESS**, completed November 5, 2026, in **12:27**. The complete normal suite ran without test exclusions:

| Tests reported | Passed | Failures | Errors | Skipped |
|---:|---:|---:|---:|---:|
| 4,101 | 4,055 | 0 | 0 | 46 |

Counts were independently reconciled with the current run's Surefire XML (`target/phase21b2-full-totals.json`). One pre-existing, stale XML diagnostic report outside this run was excluded from the count and left untouched. All original financial golden expectations, including both complete seed-417 fixed-result hashes, remain unchanged and passed. Existing market/mortality/inflation vectors, zero-expense normalization, V1 wire/fingerprint compatibility, V2 numeric compatibility, comparison regressions, Phase 19 snapshot compatibility, financial/tax/Roth tests and Phase 20 navigation tests passed.

Skip classification (existing conditions, not new exclusions):

| Count | Reason |
|---:|---|
| 25 | Opt-in visual previews; B2's new Light/Dark previews run normally, and the existing cached Monte Carlo PDF preview was additionally enabled and passed separately. |
| 14 | Opt-in capacity/performance/large-artifact probes requiring their explicit properties; normal full-suite benchmarks did run. B2 also ran its bounded enabled workload. |
| 2 | Private-plan audits requiring `audit.plan`; not enabled because no real user plan files are to be accessed. |
| 1 | Existing explicitly disabled multi-minute production mortality performance benchmark. |
| 4 | Existing Mississippi worksheet assumption aborts pending authoritative guidance (MS11, MS12, MS21, MS27); financial expectations were not changed. |

`git diff --check` passed with exit 0. New B2 files were also scanned for trailing whitespace (none). No final failures or errors remain.

Coverage mapping:

| Contract | Principal executable evidence |
|---|---|
| Known-answer keyed random vectors, moments, ordering, duplicate/insertion correspondence, stream independence | Existing `MonteCarloRandomStreamsTest`, `MonteCarloExpenseGeneratorTest`, `MonteCarloInflationRegressionTest`, `MonteCarloWorldGeneratorTest` |
| Timing, escalation, partial-year/death rules, one-time exclusion, Medicare retries, RMD/withdrawals, tax/Roth funding, estate and funding failure | Existing `StochasticExpenseProjectionTest` (fixed-budget oracles) and `StochasticExpenseMonteCarloTest` (direct full-engine oracles) |
| Generated public routing for all four combinations, repeatability, unchanged reference, blocked supplied sources/comparisons | `SupportedStandaloneExpenseTest`, updated `ExpenseVolatilityAdmissionTest`, `ExpenseVolatilityCompatibilityTest` |
| V1 exact reencoding, V2 strict capability/manifest validation, V3 identity, malformed/mismatched/digest/ownership rejection, original-file/backup safety | `StandaloneExpenseCapabilityTest`, existing `MonteCarloSnapshotCodecTest`, `MonteCarloNumericCompatibilityTest`, repository tests |
| Actual accepted association, Save/Save As/restart, both swap directions, live edits without historical substitution, no restore simulation | `StandaloneExpenseHistoryTest`, existing parameter/projection-association/lifecycle tests |
| Cancellation, revision/closure rejection, worker failure/empty messages, duplicate activation, save failure and Retry Save without rerun | Extended `MonteCarloRunFeedbackTest`, existing session/progress/finalization tests |
| Original-context enabled View/Save reports, unchanged legacy report data/pixels, Light/Dark compact dialog/details | `MonteCarloPdfWorkflowIntegrationTest`, `StandaloneExpensePreviewTest`, `MonteCarloHistoricalPdfFidelityTest`, separate cached `MonteCarloPdfPreviewTest` |

## 11. Visual verification

Actual JavaFX dialogs were rendered in Light and Dark at requested 900×650 and 1180×820 windows, for all four supported household/mode combinations, including busy, completed summary and expanded Analysis Details states. The UI uses ordinary vertical scrolling; the fixed Current run footer and Close remain available. Original chart/legend/outcomes styling and conditional disclosure wrapping were inspected. Screenshots are in `target/phase21b2/previews/` (window decoration means pane-image dimensions differ from requested outer dimensions).

Real original-context PDFs for all four modes were exported and text-checked, with rendered pages in the same directory. The first-page assumptions disclose the original 25% after the live plan changes to 17%; chart/report evidence remains frozen. Representative inspected images include `couple-fixed-LIGHT-900-details.png`, `couple-longevity-DARK-900-details.png`, `single-fixed-DARK-1180-details.png`, `single-longevity-LIGHT-900-details.png`, and the fixed/longevity PDF first pages. Chart/table/last pages of the representative couple-fixed (4 pages) and single-longevity (6 pages) PDFs were additionally rendered and inspected using the existing PDFBox dependency, without an external viewer. No overlapping assumption text or missing chart information was seen. Normal table pagination remains intact. Existing wide fixed-result UI table columns retain their prior scrolling behavior; this phase did not redesign them. Native Windows viewer, DPI and interactive acceptance remain required.

## 12. Performance and resources

One bounded representative public fixed run used 100 trials and 10 expense definitions (9 enabled bindings), completing in 0.587 seconds on this environment. The raw envelope was 125,566 bytes, compressed 25,430 bytes; capability 1,340 bytes. These are observations, not performance guarantees or statistical accuracy claims; elapsed/heap data vary with warmup and machine.

No additional result cache or collection of expense paths was introduced. Each scenario owns its existing O(enabled-expenses × modeled-years) immutable path and releases it after projection reduction. Frozen aggregate/result storage retains the established limits. Preparation/validation reconstruct bounded definitions/manifests but does not create random draws. Existing trial, occurrence, horizon, numeric/document limits, cancellation and progress polling remain unchanged. No production-scale performance claim is made.

## 13. Native Windows acceptance checklist

- Run configured positive volatility without clearing it: couple/single-person, fixed/longevity.
- Verify visible progress, Cancel, duplicate-run prevention and the Phase 21B1 Current run footer at compact size.
- Inspect original percentages, model/protocol, annual-standard-deviation explanation and original seed/settings in current and historical details.
- Save, Save As, restart and restore without recalculation; verify accepted projection association and historical PDFs after editing current volatility.
- Swap both directions and confirm original result association/freshness; positive-only changes must not substitute another result.
- Exercise PDF View/Save/Cancel with the default export directory and native viewer; verify original chart/table content and disclosure readability.
- Check Light/Dark and Windows 100%, 125%, 150% scaling.
- Confirm positive-volatility comparison remains explicitly blocked pending B3; supplied-world APIs remain unsupported.
- Verify a real storage failure retains the unsaved result and Retry Save does not rerun it.

## 14. Remaining Phase 21B3 limitations

Positive expense-volatility strategy comparisons remain gated. Their result capability, pair correspondence, common-random-number provenance and associated reports are deferred to B3. No correlation, alternate distributions, monthly sampling or durable expense identifiers were introduced. No Phase 21B4 reporting expansion beyond the minimum truthful standalone disclosures was undertaken.

## 15. Git and data preservation

Work started on `main` at `81dd97a12a875dad864057a2662a7ac6bcdfefbd`. All existing modified/untracked Phase 21A/B1 work was retained. Completed Phase 19/20/19C behavior is preserved. Synthetic plans and isolated temporary directories were used; no actual user plans were accessed or modified. Nothing was staged, committed, pushed, reset, cleaned or reverted.

Final branch/HEAD are unchanged. The combined working tree has **26 modified tracked files and 12 untracked files** (38 status entries, including pre-existing B1 work); the index is empty. Exact status is recorded in `target/phase21b2-final-git-status.txt`. No simulation paths or generated test artifacts were added to Git.

## 16. Approval gate

Stop after B2 verification/report completion. Request native Windows acceptance before Phase 21B3. Do not declare comparison support or proceed to another phase automatically.

Remaining acceptance risks are native DPI/keyboard interaction, real viewer integration and user-scale workloads beyond the bounded probes. Legacy positive-configured V1 artifacts without validated expense provenance remain preserved but explicitly unavailable for supported interpretation. Supported generated V2 artifacts restore under their original V3 fingerprint and disclosure contract. No new development phase is started.
