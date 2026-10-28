# Phase 12G — Bounded analyzer freshness correction

## Executive summary

Implemented the approved correction following the Phase 12G audit. Fresh Quick Comparison UI and PDF now use the same typed compatibility assessment, including change/revert recovery. Asynchronous assessment is distinguished from a proven mismatch. Matching legacy inputs remain usable without inventing an association. Recorded associations have a separate projection-identity assessment and an explicit **View Associated Result** action using existing read-only History navigation.

No financial algorithms, fingerprints, persisted schema versions, historical payloads, result values, execution admission/generation guards, cross-analysis join rules, dependencies or storage limits changed. No automatic historical-session promotion, directional Break-Even persistence/reuse, or subsequent phase is implemented. Native Windows acceptance remains pending.

**READY FOR REVIEW AND NATIVE WINDOWS ACCEPTANCE.** Final focused: 345 total / 344 passed / 1 skipped. Final full: 3,575 total / 3,531 passed / 44 skipped / zero failures or errors. Do not infer native acceptance from automated previews.

## Baseline and preserved work

Branch `main`; HEAD `e620bec4a0011ab12d15fce670bff5011093d9da`; staging index empty. Starting inventory: 39 modified tracked files and 50 expanded untracked files, including the Phase 12G audit and all accumulated Phase 11–12 work. Nothing was staged, committed, pushed, reset, cleaned or discarded. Only the bounded files listed below were changed in this implementation; the complete accumulated inventory is appended for review.

Read AGENTS.md, the complete Phase 12G audit and current lifecycle, snapshot, association, history, report, and test implementations. The approved legacy compatibility and explicit read-only historical navigation policies govern this correction.

## Reproduced defect and exact correction

Added `SocialSecurityAndQuickPdfTest.freshQuickChangeAndRevertSynchronizesUiAndPdfCurrency` before production changes. The initial run failed exactly as expected: after restoring the candidate count and observing Current compatibility, the Quick label still contained `Candidate count changed - run integrated analysis again.` One test failed; no errors/skips. Log: ignored `target/phase12g-reproducer.log`.

The root cause was a guard that synchronized the Quick label only for `quickWasRestored` results. Fresh results used the same persistence assessment but retained the old label, and PDF export inferred currency from label emptiness. The changed callback now synchronizes every accepted Quick report from `quickPersistence.warning()`. Export captures `quickPersistence.freshness()` on FX and passes that immutable typed assessment to the pure report adapter. Display text is never an input to compatibility.

Numeric candidate-count changes still produce Outdated. Reverting restores matching compatibility without calculation. When the current Social Security prerequisite is unavailable, new Quick admission remains blocked; the prior frozen comparison remains exportable with Inputs Need Attention rather than a fabricated current/prerequisite match. Original financial tables, candidate order, complete elections, original prerequisite fingerprint/run, warnings and timestamps remain unchanged. Fatal/canceled/all-unsuccessful replacements preserve the previous accepted result under the existing policy.

## Minimal assessment model

`AnalysisResultFreshness` is an immutable presentation record in the existing analysis-results package. It wraps the existing `AnalysisCompatibilityPolicy.Assessment` and a separate association classification. It has no serializer registration, schema version, calculation factory, storage or mutation API.

| Dimension | Representation and behavior |
|---|---|
| Effective applicability | Existing CURRENT, OUTDATED, INPUTS_NEED_ATTENTION, INCOMPATIBLE_OR_UNAVAILABLE, NO_SAVED_RESULTS; a missing assessment means pending, not mismatch |
| Association | CURRENT_PROJECTION, HISTORICAL_PROJECTION, INPUTS_EDITED, UNRECORDED |
| Artifact integrity | Existing repository/codec/envelope/digest validation; failed historical verification remains unavailable |
| Execution authorization | Existing job/session revision, generation, admission, cancellation and ownership guards; unchanged |
| Durability | Existing Pending/Saved/Save Failed/Memory Only information and retry; independent of compatibility |

The long-standing `current()` compatibility predicate is preserved for existing prerequisites and interactive behavior. The notice explicitly distinguishes **Current Results — compatible but unassociated; financial/request compatibility only, not verified projection-associated Current** from **Current Results — verified projection association and matching analysis inputs**. Thus the familiar legacy input-compatibility heading remains, but does not claim a projection pin. A result with a recorded association to another projection is labeled Compatible Results with historical association; matching hashes do not move it. Recorded Current associations whose configured financial inputs no longer match the accepted projection are identified separately.

`ApplicationController.analysisAssociation` provides a cache-only read of recorded provenance for the displayed verified snapshot. It uses the accepted projection UUID/input digest and `currentProjectionInputsMatch`, not the dirty flag or source revision. It checks owner, run, kind, input/context fingerprints, payload digest and completion time through `AnalyzerAssociation.validate`. A matching financial digest with a different projection UUID never creates or moves an association. A wrong owner or inconsistent envelope cannot qualify. Pending association placeholders are not silently promoted into verified committed association status; their ordinary Save requirement remains visible.

No old fingerprint bytes are normalized, rewritten or migrated. Monte Carlo V1 verification and V2 temporary-numeric compatibility remain unchanged. Financial plan/baseline decimal scales retain their existing significance.

## Pending restoration and UI/PDF consistency

Generic SS/Weighted/Exhaustive/individual persistence notices use the shared presentation assessment. SS and Exhaustive status labels no longer map every false `current()` value to Outdated; pending assessment says Assessing completed result inputs. The same assessment is used in generic integrated PDF provenance and SS-only PDF capture. Existing Outdated notices retain their familiar heading for a proven mismatch.

All eight Monte Carlo variants retain their session-stale execution signal, but the visible warning comes from the independent result assessment. Pending assessment therefore says Assessing rather than STALE/Outdated. The standalone plan label says Analyzed Plan, with Assessing or Not Current where appropriate; it does not claim current applicability merely because a session completed. The accepted-result metadata-pending status is explicit.

Monte Carlo PDF provenance now distinguishes UNASSESSED and INPUTS_NEED_ATTENTION from OUTDATED. Previously both had a blanket Outdated first line followed by a qualifying explanation. The original frozen results, seeds/trial counts, annual histories, matched pairing, financial tables and timestamps are unchanged. The UI and PDF share the status derived from the typed assessment.

## Explicit associated-result navigation

`AssociatedAnalyzerAction` is a small shared JavaFX button, installed in the existing result persistence controls for all seven SS/integrated identities and the two Monte Carlo view classes covering all eight MC identities. It uses the selected kind and accepted Current projection's recorded UUID/input digest. Missing/legacy association means disabled, with an explanatory tooltip. It does not infer history from a visible result or equal hash.

Clicking rechecks ownership/availability and opens `ProjectionAnalyzerHistoryDialog` with the Current projection UUID and requested kind. A small constructor overload preselects the kind; the existing role selector, read-only rendering, artifact verification, generation checks and PDF capture paths are reused. The button is a lookup action, not an artifact-integrity certification: a recorded reference is checked fully by History when opened. Missing/corrupt/unsupported artifacts produce its existing unavailable state with the reference preserved. Pending accepted pins can be viewed in memory and retain their ordinary-Save durability notice.

No analyzer controls are restored or replaced by this action. Last-success slots, accepted parameter configurations and financial inputs are unchanged. Existing History generation, captured plan object/UUID, selected reference and membership checks prevent late loads or export completions from binding to another plan/selection. No new lifecycle contract, executor or background store was added. History selection is explicit; no automatic session promotion occurs on swap, restart or navigation.

## Swap, persistence and concurrency

The association follows original projection UUID, not Current/Baseline labels. The all-15 swap matrix now additionally asserts historical association before role exchange, Current association after exchange and restart, independent cloned ownership after Save As, rejection of the original owner's snapshot in the clone, and historical classification after a second swap. Exact saved results and original last-success slot assertions remain intact. Throwing projection factories remain in the swap/history tests.

No changes were made to `SocialSecurityAnalyzerJobController`, MC session completion gates, controller swap preparation/commit, analyzer admission, association acceptance, repositories, manifest schema, payload codecs, atomic writes, parameter saves or financial fingerprints. An obsolete completion still cannot publish after swap. A valid accepted result may be reassessed separately without pretending its old execution revision authorizes a new completion.

Deterministic/Weighted cross-analysis join restrictions remain unchanged, including session revision, exact universe/election and current-flag requirements. Break-Even ordered-pair invalidation and explicit calculation behavior are unchanged.

## Tests and actual verification

New tests:

- Fresh Quick candidate-count change/revert: typed notice, result label and PDF agree; financial tables remain identical; PDF rendered.
- Fifteen analyzer identities: compatible legacy unknown association, verified exact UUID, equal input digest/different UUID, configured-input mismatch, wrong owner and mismatched payload digest.
- Explicit read-only History entry selects the recorded analyzer kind, does not promote a last-success slot, change accepted configurations/inputs/dirty state or call the projection engine.
- Controlled delayed SS restoration: pending rather than Outdated, then Current compatibility without a job.
- Eight controlled MC reassessments: pending UI and PDF are not Outdated; release restores compatible-but-unassociated status.
- Existing all-15 swap/save/restart/Save As tests extended with association classifications and second swap. Existing late-completion, corrupt artifact, wrong binding and preservation assertions remain.

Existing tests with exact old presentation assertions were updated to the approved semantics, not weakened: MC edit-time assertions now require Assessing and prohibit Outdated before fingerprint assessment; restored legacy PDF checks require the full unassociated disclosure. The existing Quick prerequisite test now waits for initial metadata assessment and requires Inputs Need Attention when the prerequisite is unavailable. Numerical, ranking, financial golden, seeded simulation and payload expectations were not changed.

Initial correction run: **53 passed, zero failures/errors/skips** (`target/phase12g-correction.log`). Broader exploratory runs exposed old immediate-stale/old-PDF-text expectations and a new test fixture that used a Saved-only helper on a never-saved plan. These were corrected before final acceptance; the new delayed-load fixture explicitly saves its plan. A test-only fully qualified class-name compile error was corrected. The intentionally failing reproducer is preserved in the source and now passes.

Final focused command uses IntelliJ bundled Maven, project-local `.codex-m2/repository`, `-DargLine=-Xmx4g`, and `-Dmc.lifecycle.preview=true`:

```text
-Dtest=Phase12GFreshnessTest,SocialSecurityAndQuickPdfTest,MonteCarloResultLifecycleTest,WeightedRestorationTest,CoupleSocialSecurityPersistenceTest,ExhaustivePersistenceTest,MonteCarloComparisonViewTest,MonteCarloViewTest,MonteCarloRestorationViewTest,MonteCarloHistoricalPdfFidelityTest,MonteCarloPdfExportTest,Phase12FAnalyzerSwapTest,ProjectionSwapTest,Phase12EAssociationTest,Phase12EIndividualLifecycleTest,MonteCarloNumericCompatibilityTest,AnalysisFingerprintTest,SocialSecurityParameterPersistenceTest,MonteCarloParameterPersistenceTest,ResultNavigationTest test
```

Final focused: **345 total, 344 passed, 1 skipped, 0 failures, 0 errors; BUILD SUCCESS; 01:39; finished 2026-10-27 15:35:07 -04:00.** Log: `target/phase12g-focused-release.log`. The skip is the existing `mc.pdf.historical.large=true` opt-in 10,000-trial historical PDF case. Final full: **3,575 total, 3,531 passed, 44 skipped, 0 failures, 0 errors; BUILD SUCCESS; 12:25; finished 2026-10-27 15:47:43 -04:00.** Log: `target/phase12g-full-release.log`. No excluded tests or test-disable flags were added. Existing opt-in previews/capacity cases retain their established skip conditions.

## Visual inspection

Generated representative Quick UI previews using the actual dialog at 1180×820 light and 900×650 dark logical scene dimensions, and Quick PDF with rendered pages under ignored `target/phase12g-previews/` and `target/phase11c-pdf/phase12g-quick-reverted*`. Existing MC restoration previews cover each of the eight variants and original/current/outdated data with `mc.lifecycle.preview=true`. Existing Phase 12E tests render read-only History and original PDFs for all 15 variants.

Actual inspected images and final observations are recorded in the acceptance addendum below. Automated PNG rendering does not establish native Windows DPI, keyboard/screen-reader, high-contrast or complete dark-theme certification. No global palette or CSS changes.

## Exact Phase 12G implementation inventory

Production paths under `src/main/java/com/daviddunn/retirementplanner/`:

1. **Added** `app/socialsecurity/results/AnalysisResultFreshness.java` — immutable presentation assessment and recorded identity classification.
2. **Added** `ui/socialsecurity/AssociatedAnalyzerAction.java` — explicit read-only History action.
3. `ui/controller/ApplicationController.java` — cache-only association presentation API.
4. `ui/socialsecurity/WeightedResultPersistence.java` — typed status, warning, provenance and associated action.
5. `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java` — fresh Quick synchronization, shared UI/PDF assessment and pending labels.
6. `ui/socialsecurity/QuickComparisonReportAdapter.java` — typed immutable export assessment overload.
7. `ui/socialsecurity/ProjectionAnalyzerHistoryDialog.java` — preselected-kind constructor overload only.
8. `ui/montecarlo/MonteCarloResultLifecycle.java` — shared freshness/pending and association resolver.
9. `ui/montecarlo/MonteCarloHistoricalPdf.java` — pending/invalid-input provenance separated from proven mismatch.
10. `ui/montecarlo/MonteCarloAnalysisView.java` — pending status and associated-result action.
11. `ui/montecarlo/MonteCarloStrategyComparisonView.java` — same for paired views.

Tests under `src/test/java/com/daviddunn/retirementplanner/`:

1. **Added** `ui/socialsecurity/Phase12GFreshnessTest.java`.
2. `ui/socialsecurity/SocialSecurityAndQuickPdfTest.java`.
3. `ui/socialsecurity/Phase12FAnalyzerSwapTest.java`.
4. `app/montecarlo/MonteCarloResultLifecycleTest.java`.
5. `app/montecarlo/MonteCarloRestorationViewTest.java`.
6. `ui/montecarlo/MonteCarloComparisonViewTest.java`.
7. `ui/montecarlo/MonteCarloViewTest.java`.
8. `ui/montecarlo/MonteCarloPdfExportTest.java` — immediate pending export status, retaining exact financial/no-engine assertions.
9. `ui/views/SinglePersonHouseholdUiTest.java` — same explicit pending-state requirement in the single-person end-to-end flow.

Report: `PHASE-12G-ANALYZER-FRESHNESS-IMPLEMENTATION.md`. The earlier audit remains unchanged. Some listed paths were already untracked/modified by earlier phases; those prior contents were preserved. Exclude ignored `target/` PDFs, PNGs, logs, compiled outputs and local Maven cache from a future commit. No staging performed.

## Limitations and explicit deferrals

- No deterministic/weighted cross-analysis join relaxation; valid historical results may still be unsuitable for a new live cross-analysis join.
- No persisted directional Break-Even artifact or automatic pair reuse.
- No automatic promotion of a historical association into a current analyzer session or parameter controls.
- No new persistence formats or legacy association inference.
- View Associated Result lookup is enabled for recorded accepted-Current references; artifact integrity/availability is checked by existing History on opening. A missing component remains unavailable; no recalculation/fallback occurs. Baseline/earlier projections remain selectable inside History.
- Legacy-compatible headings retain `Current Results` for input compatibility but explicitly state compatible-but-unassociated and deny verified projection-associated Current. Full verification requires the matching recorded envelope/UUID/input digest and effective request. This distinction does not alter existing prerequisite contracts.
- Pending pin placeholder digests do not qualify for verified committed association presentation; ordinary Save materializes and validates them. Save/Retry status remains independent.
- Native acceptance remains required for the new action and status wording. No financial model or new native acceptance is claimed.

## Native Windows acceptance checklist

1. Run fresh Quick with a compatible SS prerequisite. Change candidate count, verify Outdated after assessment; revert and confirm notice, result warning and exported PDF agree with no rerun.
2. Change SS assumptions so the next-run Quick prerequisite is unavailable. Verify a new Quick run is blocked while its original result/PDF remain available with appropriate input-attention status and original data.
3. Open legacy last-success results with no association. Confirm explicit compatible-but-unassociated disclosure, no invented pin and no falsely enabled associated action.
4. Accept and ordinarily Save analyses against distinct A/B projections. Swap and double swap; use View Associated Result to open the exact Current UUID/kind. Verify last-success session, editable controls, accepted configurations and financial inputs remain unchanged by viewing.
5. Test Current/Baseline/earlier History choices, original assumptions/heat maps/rankings/PDF timestamps, unavailable references and missing/corrupt artifact behavior. No analysis should run.
6. Check all eight MC modes through restart and format-only change/revert. Pending assessment must not show a proven mismatch; actual seed/parameter changes must become Outdated; original values restore compatibility.
7. Repeat single-person/couple plans, Save/restart, Save As and plan switch while a load is pending. No cross-owner attachment or stale completion may overwrite a newer session.
8. Check tab/keyboard behavior, action tooltip, light theme 1180×820 and dark 900×650, scrolling and PDF notices. Confirm parameter Retry Save, dirty-plan isolation and existing cross-analysis/Break-Even restrictions remain intact.

Stop after Phase 12G for review and native Windows acceptance. No subsequent phase is authorized by this implementation.

## Visual acceptance addendum

Inspected the actual rendered files:

- `target/phase12g-previews/quick-reverted-light.png` (1180×820): actual Quick workspace, saved parameter status, compatible-but-unassociated result notice and disabled associated-result action for a legacy/no-admission result. No remaining candidate-count rerun warning. Text and controls fit with existing outer scrolling.
- `target/phase12g-previews/quick-reverted-dark.png` (900×650): responsive workspace selector, readable status and controls, vertical scrolling at reduced height; associated action/completed details can lie below the fold rather than being horizontally clipped. The test applies existing Modena dark base variables; native Windows theme certification is not claimed.
- `target/phase11c-pdf/phase12g-quick-reverted-page-1.png`: original run/completion/export timestamps and prerequisite identity, matching compatible-but-unassociated notice, original baseline summary, and readable pagination.
- `target/phase11c-pdf/phase12g-quick-reverted-page-72.png`: original final candidate warnings and limitations, legible footer/Page 72 of 72, no clipped explanatory text. The intentionally broad existing fixture retains every original row; no data is omitted to shorten it.
- `target/mc-2c-couple_monte_carlo_paired_longevity-current.png`: original pairing, seed/trial count, both survivor ages, compatible-but-unassociated notice and History lookup action; financial tables and chart area remain intact.
- `target/mc-2c-individual_monte_carlo_fixed-current.png`: primary-only standalone original trajectory, annotations, original percentiles and compatible legacy notice; no stale banner after format-only reassessment.

The read-only History selection path reuses Phase 12E views. Its action is separately tested enabled for a recorded pin and disabled without one, with no slot/control promotion. Full platform-native action/modal behavior remains on the native checklist.

## Full-suite follow-up record

The first complete run finished with 3,575 total / 3,526 passed / 44 skipped / 0 failures / 5 errors in 10:29 (2026-10-27 15:33:06 -04:00). All five errors were UI-thread assertion wrappers expecting Historical Analysis immediately after an edit: four Fixed/Longevity standalone/paired PDF cases and one single-person end-to-end case. They now require the precise pending assessment text and explicitly prohibit Outdated before assessment; all financial graph/table/value, original-context and no-recalculation assertions are retained. No financial result failure was found. A duplicate historical prefix in the legacy boolean Quick report overload was also removed; the typed production report path already used the correct assessment text. The final focused and complete runs below supersede this exploratory run.

## Final acceptance and integrity checks

- Complete Maven regression log reports BUILD SUCCESS. All **406 current-run Surefire XML suites** independently total **3,575 tests / 3,531 passed / 44 skipped / zero failures/errors**. One pre-existing October 19 `WeightedRestorationTest-phase9c-compatibility.xml` report contains one old test and was excluded from current-run accounting, without deleting it. Maven and fresh XML agree exactly.
- Source/test hashes for all **20 Phase 12G code/test files** match the hashes captured at the start of the final full run. No source or test changed during or after that run. The report alone was finalized afterward.
- The captured PowerShell 5.1 host session returned 1 despite Maven BUILD SUCCESS and clean current-run XML. Its redirection records Java/Jansi native-access warnings as NativeCommandError. A separate bundled-Maven version probe with explicit native `$LASTEXITCODE` propagation returned 0 with the same warning pattern. Test success here is established by Maven's completed summary and current-run XML, not an assumed shell exit code. No build/JVM configuration was changed to suppress warnings.
- `git diff --check` passes. Branch/HEAD remain main / e620bec4a0011ab12d15fce670bff5011093d9da. Index empty. Current accumulated tree: **45 modified tracked files and 54 untracked files**, including all preserved earlier work.
- The report is validated UTF-8. Existing financial golden resources, financial fingerprints, snapshot formats, algorithms and cross-analysis join restrictions were not modified by Phase 12G.
- Additional actual images inspected: `target/phase12e-previews/COUPLE_QUICK.png` (original read-only candidate order/elections/failures and financial tables) and `target/phase12e-previews/INDIVIDUAL_SOCIAL_SECURITY-pdf.png` (original saved primary-only result, selection/optimum markers, original completion/projection-association notice and pagination). These remain historical presentations, not promoted current sessions.

Phase 12G is ready for review and native Windows acceptance. The explicit deferrals above remain deferred. No later phase was started.

## Final accumulated Git inventory

Tracked modifications (45), including preserved prior work:

```text
src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerPdfExporter.java
src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerReport.java
src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloFingerprintService.java
src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPlanDocument.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/RetirementPlanScenarioCopyService.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AcceptedAnalysisCompletion.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisFingerprintService.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ExhaustiveSnapshot.java
src/main/java/com/daviddunn/retirementplanner/domain/baseline/ProjectionBaseline.java
src/main/java/com/daviddunn/retirementplanner/domain/model/RetirementPlan.java
src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java
src/main/java/com/daviddunn/retirementplanner/persistence/JsonRetirementPlanRepository.java
src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java
src/main/java/com/daviddunn/retirementplanner/ui/charts/ProjectionChartView.java
src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloHistoricalPdf.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloParameterControls.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloResultLifecycle.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerNavigationView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ClaimingStrategyHeatMapView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveResultSnapshots.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualCompletionPersistence.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IntegratedAnalysisResultTabs.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/LongevityWeightedIntegratedView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonMortalityView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityParameterBinding.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedResultPersistence.java
src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java
src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloRestorationViewTest.java
src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloResultLifecycleTest.java
src/test/java/com/daviddunn/retirementplanner/ui/controller/ApplicationControllerTest.java
src/test/java/com/daviddunn/retirementplanner/ui/controller/BreakEvenControllerTest.java
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloComparisonViewTest.java
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPdfExportTest.java
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloViewTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/CoupleSocialSecurityPersistenceTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedRestorationTest.java
src/test/java/com/daviddunn/retirementplanner/ui/views/SinglePersonHouseholdUiTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/CompleteWizardEndToEndTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/StateTaxWizardIntegrationTest.java
```

Untracked files (54), including preserved prior work:

```text
PHASE-11A-SOCIAL-SECURITY-UI-PDF-AUDIT.md
PHASE-11B-SHARED-GRID-AND-NAVIGATION.md
PHASE-11C-SOCIAL-SECURITY-AND-QUICK-PDF-EXPORT.md
PHASE-11D-FINAL-INTEGRATION-AND-REGRESSION-AUDIT.md
PHASE-12A-BASELINE-DETAILS-ARCHITECTURE-AUDIT.md
PHASE-12B-BASELINE-DETAILS-IMPLEMENTATION.md
PHASE-12C-PROJECTION-SNAPSHOT-AND-SWAP-ARCHITECTURE-AUDIT.md
PHASE-12D-COMPLETE-PROJECTION-SNAPSHOT-PERSISTENCE.md
PHASE-12E-IMMUTABLE-ANALYZER-ASSOCIATIONS.md
PHASE-12F-CURRENT-BASELINE-SWAP.md
PHASE-12G-ANALYZER-FRESHNESS-AUDIT.md
PHASE-12G-ANALYZER-FRESHNESS-IMPLEMENTATION.md
src/main/java/com/daviddunn/retirementplanner/app/baseline/BaselineDetailsSession.java
src/main/java/com/daviddunn/retirementplanner/app/baseline/BaselineParameterComparison.java
src/main/java/com/daviddunn/retirementplanner/app/baseline/ProjectionInputs.java
src/main/java/com/daviddunn/retirementplanner/app/projection/AnalyzerAdmission.java
src/main/java/com/daviddunn/retirementplanner/app/projection/AnalyzerAssociation.java
src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java
src/main/java/com/daviddunn/retirementplanner/app/projection/ProjectionRoleManifest.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisResultFreshness.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/IndividualDeterministicSnapshot.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/QuickComparisonSnapshot.java
src/main/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotComponents.java
src/main/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsDialog.java
src/main/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisPdfData.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AssociatedAnalyzerAction.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ClaimingAgeGridView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/HistoricalAnalyzerPresentation.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ProjectionAnalyzerHistoryDialog.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/QuickComparisonReportAdapter.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/QuickReportSession.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/QuickSnapshotAdapter.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationGuard.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityClaimingGridAdapter.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityOnlyReportAdapter.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityReportSession.java
src/main/resources/css/baseline-details.css
src/test/java/com/daviddunn/retirementplanner/app/baseline/BaselineDetailsTest.java
src/test/java/com/daviddunn/retirementplanner/app/montecarlo/Phase12EHistoryFixtures.java
src/test/java/com/daviddunn/retirementplanner/persistence/AnalyzerAssociationStorageTest.java
src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotPersistenceTest.java
src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSwapTest.java
src/test/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsViewTest.java
src/test/java/com/daviddunn/retirementplanner/ui/baseline/ProjectionSwapViewTest.java
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloProjectionAssociationTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12EAssociationTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12ECodecTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12EIndividualLifecycleTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12FAnalyzerSwapTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12GFreshnessTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SharedClaimingAgeGridTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAndQuickPdfTest.java
```

Staged paths: 0. Nothing staged, committed, pushed, reset, cleaned or discarded.
