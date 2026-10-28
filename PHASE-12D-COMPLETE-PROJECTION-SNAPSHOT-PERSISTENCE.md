# Phase 12D — Complete Projection Snapshot Persistence

## Executive summary

Implemented versioned, immutable standard-projection components and a current/baseline role manifest. Newly captured baselines retain the exact accepted annual results and detached effective inputs. Ordinary Save/Save As persist the components before atomically publishing the plan manifest. Reopening restores original results asynchronously without invoking the projection engine or income-summary calculations. Baseline Details uses original results and suppresses legacy rebuilt charts.

This implementation stops at Phase 12D. Analyzer pinning/visualization extensions (12E), directional swap preparation and the swap operation/UI (12F) are not implemented. Native Windows acceptance is pending.

## Baseline and existing work

Branch `main`; starting/current HEAD `e620bec4a0011ab12d15fce670bff5011093d9da`. The starting index was empty. Sixteen tracked modifications and 26 untracked files existed before 12D, including all Phase 11 work, Phase 12B and the Phase 12C audit. They were preserved. The AGENTS baseline shallow-copy warning was checked against current source: `fromRetirementPlan` already deep-copies, but the snapshot constructor/getters expose mutable domain objects. The new complete component therefore stores immutable DTOs and a frozen JSON input document rather than retaining that mutable graph.

## Schema and component inventory

The optional plan root `projectionState` is a `ProjectionRoleManifest` version 1:

```json
{
  "version": 1,
  "owner": "<plan UUID>",
  "current": {"id": "<snapshot UUID>", "digest": "<SHA-256>"},
  "baseline": {"id": "<snapshot UUID>", "digest": "<SHA-256>"}
}
```

Either role can be absent. Two roles may legitimately reference the same accepted result immediately after baseline capture; a later projection creates a different current identity. `ProjectionBaseline` adds optional `projectionSnapshotId`; its original inputs, capture date and description remain intact. No analyzer snapshot schema/version changes.

Immutable gzip JSON components live beside the plan under `.retirement-planner-projections/<owner UUID>/<snapshot UUID>.json.gz`. Keep this directory with the plan. The owner directory has a verified `.binding` hash of the original normalized plan path; copying a plan filename alone does not establish a new valid component binding. Use Save As for independent ownership. Backup recovery resolves the binding to the original primary path.

`CompleteProjectionSnapshot` v1 preserves:

- Snapshot UUID, current owning plan UUID, original source identity, acceptance Instant, explicit schema and provenance.
- Exact detached five-root configured-input JSON (household/persons, accounts/ownership, planning/economic/tax/withdrawal/death/horizon settings, Roth request and non-investable inputs), with its own SHA-256. No baseline recursion, analyzer histories, caches or JavaFX objects.
- Existing `RetainedProjectionSnapshot` v1: every supported annual field, account registry and references, authoritative Social Security data, cash settlement, income attribution, state/federal tax details, Medicare, RMDs, conversions and estate data. Original decimal scales remain intact.
- Original non-investable annual asset rows and totals, preserving their year associations.
- The completed dashboard income summary and projection statistics. Restoration constructs these values directly; it does not call `IncomeSummaryService` or a Social Security benefit calculator.
- Frozen participant/election metadata used by the existing chart and Break-Even adapters.

Start/end years and completeness follow from the validated ordered nonempty annual series. Calculation provenance explicitly says that the original build version was not recorded; no unavailable algorithm/build version is invented. The accepted timestamp identifies completion/publication; an independent historical execution-start timestamp was not previously available.

All exposed DTO collections are copied. `restoreInputs`, annual restoration and non-investable restoration produce detached mutable domain graphs; changing them cannot mutate the accepted immutable component. Distinct account identities remain distinct through the existing indexed account registry. Orphan references are rejected by the retained codec, never repaired by name.

## Accepted projection lifecycle

`ApplicationController.getCurrentProjection` remains the normal acceptance path. It freezes inputs before execution and reconstructs a detached metadata plan from that document. The unchanged synchronous engine retains its established live-account identity contract. Summary/non-investable results and annotations use the frozen metadata plan. The full immutable result and chart are staged before publication.

Existing owner, financial revision and projection-generation checks plus an exact post-run configured-input document check guard publication. Canceled, failed, insufficient-funding, superseded and wrong-plan executions cannot replace the accepted component. A failed rerun can leave the active projection display unavailable under existing failure policy while retaining its last accepted immutable component. Later edits cannot change frozen inputs/results. Capture itself does not mark the plan modified or increment a revision.

Summary and non-investable lazy getters reuse values populated by acceptance instead of immediately recomputing them from live edited inputs.

## Exact baseline capture

`saveCurrentAsBaseline` requires an accepted available result whose frozen input document matches the current configured inputs. The UI reports the mismatch and asks for an explicit successful projection; capturing does not run one. It creates a detached baseline input object from the accepted document and assigns the same snapshot ID to the baseline role. No financial engine or baseline rebuild participates.

Replacement preserves existing confirmation behavior. Clearing the baseline removes its role on the next Save while preserving the current role, including when both previously referenced the same object. Original immutable files remain retained for backups. Baseline capture retains its established dirty/revision behavior; snapshot creation alone does not dirty the plan.

## Persistence, atomicity and recovery

`JsonRetirementPlanRepository` coordinates component staging under the existing save/cooperative locks. Each bounded component is written and verified before the atomic plan replacement. The plan manifest is the commit point. Plan UUID and in-memory role publication occur only after that commit. Failed verification/replacement leaves the previous durable plan and its component references valid. A retry uses accepted in-memory values and needs no projection.

Save As stages independently owned components under the new UUID/path before committing the new plan. Original snapshot IDs, source identity, accepted timestamps and financial values remain unchanged; compressed component digests change as necessary because ownership changes. The old plan and artifacts remain independent. Saving while an asynchronous restoration is pending can load the captured source components, stage them and restart restoration against the committed manifest.

This is ordinary explicit plan persistence. There is no automatic full-plan save, no saving unrelated financial edits merely to persist a result, and no new configuration-only write path. A newly accepted projection/captured baseline is in memory until ordinary Save/Save As. Capture's UI notice explicitly requests Save for durability.

Restoration uses the existing bounded application-owned background queue. It validates and reconstructs current/baseline graphs, summaries, input comparisons and charts on the worker. Only a fully prepared set is published under captured owner/manifest/baseline/generation guards. Plan switches, new runs and replaced manifests reject older callbacks. MainWindow renders the restored caches without invalidation or an implicit projection. Missing/corrupt components retain saved references and show an unavailable notice; no rebuilt cache replaces them.

## Bounds and validation

Existing per-component limits remain 32 MiB decoded, 16 MiB compressed, 2,000,000 tokens, 100,000 array items, depth 80, string length 256,000 and numeric-token length 4,096. Standard-projection output uses an additional bounded streaming encoder before allocating an oversized raw document; existing analyzer encodings are unchanged. There is no interned expansion step.

Complete components additionally require at most 1,000 annual rows, 1,000 account identities and a 256,000-character configured-input document. Years must be consecutive and match non-investable year rows. Only two role components are attached. Per-owner component storage is bounded to 128 MiB compressed and 1,000 directory entries. Unsupported versions, wrong ownership, malformed references, missing files, checksum mismatches, inconsistent baseline input/result associations, unknown JSON fields and trailing data are rejected. Paths and binding markers reject symbolic/foreign storage paths under the existing atomic-write conventions.

Files written before a failed root commit can remain unreferenced. No automatic garbage collection is implemented: older artifacts may be required by backups. Hitting a storage budget fails conservatively rather than deleting historical files. File hashes provide corruption detection, not signatures against an attacker who can rewrite the plan and recompute every checksum.

## Legacy compatibility and fingerprints

Plans lacking the new root/optional baseline ID continue loading. Legacy baseline inputs/descriptions/timestamps are preserved on Save/Save As. They do not acquire original annual results merely by loading or saving. Opening such a plan does not implicitly run a projection. An explicit ordinary projection workflow can still rebuild a legacy baseline for existing comparisons; Baseline Details never labels that rebuilt chart original and suppresses it. Without sufficient accepted comparison caches, the existing details enablement remains unavailable.

`AnalysisFingerprintService` and `MonteCarloFingerprintService` admit/exclude exactly the recognized nonfinancial `projectionState` root alongside existing metadata. `MonteCarloPlanDocument` and scenario-copy code strip it. No financial root, decimal normalization rule, numeric V2 compatibility, baseline financial contract or analyzer snapshot digest is changed. Projection input identity is a separate exact document digest; it does not replace existing analyzer fingerprints.

## Standard charts and Phase 12B

The existing pure `ProjectionChartModel` renders all 13 standard metrics from original annual/non-investable data: investable assets, net worth, after-tax estate, guaranteed income, expenses, taxes, Social Security, investment growth, withdrawals, non-investable assets, Medicare, Roth conversions and RMDs. Existing participant election markers and actual RMD/conversion periods derive from frozen metadata/results. No screenshot, JavaFX node or redundant chart-series archive is persisted.

Results Summary uses the chart associated with the exact accepted projection instead of live account identities. Baseline Details reuses `ProjectionChartView` with **Original Baseline Projection — Saved Results**. It retains selection, tooltip data, event markers, amber comparisons, filtering and collapsible categories. Legacy original-result absence has an explicit explanation.

### Reconciliation

The automated 31-year fixture ends in 2056. Its baseline chart point is asserted exactly equal (including scale) to the saved original 2056 annual ending investable-assets value. Its current-minus-baseline ending difference is asserted against the unchanged `ProjectionComparisonService` result. Both original snapshots survive independent Save As and restart.

The reported native legacy $18,795,254 versus approximately $17.8M cannot be diagnosed exactly without that original run's data. Existing comparisons use ending projection values and distinguish investable assets, net worth and after-tax estate; selecting a different chart year or comparing another metric can differ. These are possible definition/horizon checks, not a claimed diagnosis of the native case. Rebuilt legacy charts are suppressed. No scaling, offsets, interpolation or authoritative comparison changes were introduced.

## Dirty state, staleness and future swap APIs

These are separate observations:

1. `isModified()` — unsaved editable-plan changes.
2. `currentProjectionInputsMatch()` — available accepted result matches exact configured inputs, independent of dirty state.
3. `peekCurrentProjection()` — currently available accepted display result.
4. `getAcceptedProjectionSnapshot()`/role manifest — validated complete retained input/result identity.

A dirty but numerically unchanged plan can match its projection; direct edits can differ even before a dirty notification. Baseline Details assesses the frozen-input match for new snapshots. Save alone does not make edited inputs match an accepted result.

Phase 12F must stage both directions, resolve dirty edits through **Save Before Swapping Projections?** with Save and Continue/Cancel, require successful Save, and then revalidate ownership and freshness. Saving stale inputs alone must never authorize swapping. No swap command/dialog is present now.

## Verification

Baseline focused run: 139 passed, zero failures/errors/skips; 20.462 s (`target/phase12d-baseline.log`). Intermediate expanded focused run: 75 passed, zero failures/errors/skips; 39.858 s (`target/phase12d-focused-final.log`). Final verification results and capacity measurements are recorded below.

Coverage includes independent financial-getter comparisons, exact decimal scales, detached account identities, both household shapes, summary/chart equality, 31-year horizon reconciliation, no-engine restart/capture/details, failure/cancellation, superseded execution/loading, plan switching, strict metadata dispatch, dirty/fresh distinction, foreign file binding, corrupt components, Save As and injected atomic VERIFY/REPLACE failures. Existing retained-projection tests include long financial numeric tokens and supported nested account/tax/SS variants.

Two restored JavaFX previews exercise 1180×820 light and 900×650 dark controls. Tests verify installed hover tooltips, point-selection detail updates, all original point counts, and unchanged dirty state with a throwing engine. Preview images are ignored under `target/phase12b-previews/phase12d-*`; these are automated JavaFX checks, not native Windows DPI/assistive-technology certification.

## Native Windows acceptance checklist

1. Open a representative couple plan; explicitly calculate a complete projection. Capture baseline and Save. Note an original annual value/year and summary value.
2. Change assumptions/accounts, calculate again, Save, close and restart. Verify current and original baseline charts/results restore without a projection run.
3. Open Baseline Details. Confirm the original-results title, original point/2056 values, matching ending investable comparison, claiming annotations, hover and click selection. Check changed-only filtering/categories and reduced window size.
4. Edit inputs without recalculating. Confirm original results/context remain frozen, status indicates differing inputs, and baseline capture is refused. Save alone must not remove the mismatch.
5. Save As; reopen original and clone separately. Confirm independent ownership and original financial values/timestamps. Keep the hidden projection component directory when backing up the plan.
6. Replace/clear the baseline and Save/reopen. Confirm role changes without loss of current results. Repeat with a single-person plan.
7. Open a legacy input-only baseline. Confirm no original historical chart is invented and no automatic rebuild runs on opening; explicitly calculate for existing legacy comparison workflows when needed.
8. Cancel/fail a replacement; confirm no new accepted snapshot. Check existing analyzers, PDFs, all accepted parameter configurations and configuration-only saves.
9. Test a recoverable backup and Save As recovery. A missing/corrupt component should show unavailable original results and preserve the existing file/references.

## Known limitations and Phase 12E prerequisites

- Native Windows acceptance remains pending. No original native financial discrepancy diagnosis is claimed.
- Component storage is a required companion to the plan JSON, not an embedded portable one-file archive. Save As performs ownership-safe copying; arbitrary filename copying is conservatively rejected.
- Standard snapshots persist on ordinary Save/Save As, not every calculation. Serialization/component writes use the established ordinary Save path and may add latency; loads use the background queue. Large-plan native Save latency still needs observation.
- Legacy original annual results remain unavailable. Financial recalculation never upgrades their provenance.
- A complete component's configured input document is capped at 256,000 characters. Larger plans fail capture/persistence rather than omit inputs or increase limits.
- Historical build version and independent execution-start time were not available in the old normal-projection lifecycle; provenance is explicit about this.
- There is no automatic component GC, analyzer pinning, large analyzer payload duplication, stored directional Break-Even context or role swap. Phase 12E must add bounded immutable analyzer associations without turning last-success analyzer slots into permanent historical pins.

## Exact Phase 12D source/test inventory

All Java paths below are relative to `src/main/java/com/daviddunn/retirementplanner/` unless specified.

New:

- `app/projection/CompleteProjectionSnapshot.java`
- `app/projection/ProjectionRoleManifest.java`
- `persistence/ProjectionSnapshotComponents.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotPersistenceTest.java`
- This report.

Modified in Phase 12D:

- `app/montecarlo/MonteCarloFingerprintService.java`
- `app/montecarlo/MonteCarloPlanDocument.java`
- `app/socialsecurity/RetirementPlanScenarioCopyService.java`
- `app/socialsecurity/results/AnalysisFingerprintService.java`
- `app/baseline/BaselineDetailsSession.java` (preexisting untracked Phase 12B file)
- `domain/baseline/ProjectionBaseline.java`
- `domain/model/RetirementPlan.java`
- `persistence/AnalysisSnapshotCodec.java`
- `persistence/JsonRetirementPlanRepository.java`
- `ui/MainWindow.java` (retains earlier work)
- `ui/controller/ApplicationController.java` (retains Phase 12B context/guards)
- `ui/views/ResultsSummaryView.java` (retains Phase 12B action)
- `ui/baseline/BaselineDetailsView.java` (preexisting untracked Phase 12B file)
- Tests under `src/test/java/com/daviddunn/retirementplanner/`: `app/baseline/BaselineDetailsTest.java`, `ui/baseline/BaselineDetailsViewTest.java`, `ui/controller/ApplicationControllerTest.java`, `ui/controller/BreakEvenControllerTest.java`, `ui/montecarlo/MonteCarloComparisonViewTest.java`, `ui/views/SinglePersonHouseholdUiTest.java`, `ui/wizard/CompleteWizardEndToEndTest.java`, `ui/wizard/StateTaxWizardIntegrationTest.java`. Existing fixture calls now explicitly accept projections or explicitly construct legacy baselines and await restoration; no financial golden expectations changed.

Other preexisting Phase 11/12 files remain intact and are separately inventoried in their reports. A future commit must account for these dependencies rather than assuming the entire working tree was created by 12D. Exclude ignored `target/` logs/previews/generated reports, `.codex-m2/`, runtime plan files, component directories and lock files. No dependencies, CSS, financial engine, government rules, simulation algorithm, analyzer payload version or golden files were changed by 12D. No Git index/history operations were performed.

## Stabilization findings and final verification record

The first full run completed 3,470 tests: 3,401 passed, 44 skipped, 25 errors and zero assertion failures, in 10:07. It exposed an account-reference regression in detached normal execution, two wizard comparisons affected by newly committed metadata, and 22 state-wizard cases assuming synchronous reload. Normal engine execution now retains its original live-account identities; immutable metadata is captured before execution and an additional post-run input-document guard refuses changed input state. Restored historical account identities remain detached. The wizard tests now await restoration, compare all financial input fields separately from the new manifest, verify the accepted ID/committed digest, and still require exact full-plan equality across analyzer execution. Financial golden expectations were not changed.

Final focused stabilization run: 157 total, 156 passed, one existing opt-in skip, zero failures/errors; 01:06 (`target/phase12d-final-focused.log`). This includes all formerly failing wizard/funding cases, exact snapshot fidelity, 31-year reconciliation, backup restoration, saved Outdated context, atomic failure recovery and chart interaction tests.

Measured calculated standard-projection component (31 independently calculated annual years, one account; deliberately a small capacity check, not a worst-case plan): 81,444 raw JSON bytes, 7,120 gzip bytes; encoding 6 ms, decoding 3 ms, independent reconstruction/financial-getter checks 2 ms in the focused run. Measurements are single-run diagnostics, not a performance guarantee. Peak component memory was not separately instrumented. The full suite uses the established `-DargLine=-Xmx4g` configuration. No storage limit was increased.

Inspected actual rendered PNGs: `phase12d-restored-1180-light.png` and `phase12d-restored-900-dark-chart.png`, plus the reduced dark comparison-table preview. Original-results heading, axes, original dollar values, total line, claiming annotation and selected-year detail are visible. The narrow chart uses natural outer scrolling; comparison controls/categories continue below it. Existing saturated blue chart legend colors have weaker contrast in the simulated dark theme; no chart palette/CSS refactor was introduced. Tooltip installation and click-to-select are automated; physical mouse hover, DPI behavior and native Windows acceptance remain manual checks.

No new production logging of financial values was added. No changes to financial engine/tax/RMD/Roth/SS/mortality/Monte Carlo algorithms, build configuration, dependencies or financial golden resources were found in the diff. Analyzer codec dispatch is unchanged; the additional bounded projection-document encoder is separate from existing wire encoding.

## Complete working-tree inventory (including preserved earlier phases)

Index empty; no staged changes. These paths include earlier work and must not all be attributed to Phase 12D.

~~~text
 M src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerPdfExporter.java
 M src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerReport.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloFingerprintService.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPlanDocument.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/RetirementPlanScenarioCopyService.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisFingerprintService.java
 M src/main/java/com/daviddunn/retirementplanner/domain/baseline/ProjectionBaseline.java
 M src/main/java/com/daviddunn/retirementplanner/domain/model/RetirementPlan.java
 M src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java
 M src/main/java/com/daviddunn/retirementplanner/persistence/JsonRetirementPlanRepository.java
 M src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java
 M src/main/java/com/daviddunn/retirementplanner/ui/charts/ProjectionChartView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerNavigationView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ClaimingStrategyHeatMapView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IntegratedAnalysisResultTabs.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/LongevityWeightedIntegratedView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedResultPersistence.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java
 M src/test/java/com/daviddunn/retirementplanner/ui/controller/ApplicationControllerTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/controller/BreakEvenControllerTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloComparisonViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/CoupleSocialSecurityPersistenceTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedRestorationTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/SinglePersonHouseholdUiTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/wizard/CompleteWizardEndToEndTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/wizard/StateTaxWizardIntegrationTest.java
?? PHASE-11A-SOCIAL-SECURITY-UI-PDF-AUDIT.md
?? PHASE-11B-SHARED-GRID-AND-NAVIGATION.md
?? PHASE-11C-SOCIAL-SECURITY-AND-QUICK-PDF-EXPORT.md
?? PHASE-11D-FINAL-INTEGRATION-AND-REGRESSION-AUDIT.md
?? PHASE-12A-BASELINE-DETAILS-ARCHITECTURE-AUDIT.md
?? PHASE-12B-BASELINE-DETAILS-IMPLEMENTATION.md
?? PHASE-12C-PROJECTION-SNAPSHOT-AND-SWAP-ARCHITECTURE-AUDIT.md
?? PHASE-12D-COMPLETE-PROJECTION-SNAPSHOT-PERSISTENCE.md
?? src/main/java/com/daviddunn/retirementplanner/app/baseline/BaselineDetailsSession.java
?? src/main/java/com/daviddunn/retirementplanner/app/baseline/BaselineParameterComparison.java
?? src/main/java/com/daviddunn/retirementplanner/app/baseline/ProjectionInputs.java
?? src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/app/projection/ProjectionRoleManifest.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotComponents.java
?? src/main/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsDialog.java
?? src/main/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsView.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisPdfData.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ClaimingAgeGridView.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/QuickComparisonReportAdapter.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/QuickReportSession.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationGuard.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityClaimingGridAdapter.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityOnlyReportAdapter.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityReportSession.java
?? src/main/resources/css/baseline-details.css
?? src/test/java/com/daviddunn/retirementplanner/app/baseline/BaselineDetailsTest.java
?? src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotPersistenceTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsViewTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SharedClaimingAgeGridTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAndQuickPdfTest.java
~~~

## Final acceptance evidence

**Automated verification passed; ready for review and native Windows acceptance.** Phase 12E/12F have not begun.

| Run | Total | Passed | Failed | Errors | Skipped | Maven time |
|---|---:|---:|---:|---:|---:|---|
| Final focused | 157 | 156 | 0 | 0 | 1 | 01:06 |
| Final complete suite | 3,471 | 3,427 | 0 | 0 | 44 | 09:59 |

Full run: `target/phase12d-final-full-suite.log`, completed October 26, 2026 at 08:57:46 America/New_York. Used IntelliJ bundled Maven, the required project-local `.codex-m2/repository` and `-DargLine=-Xmx4g`; no test selector/exclusions were added to the full run. All 44 skips belong to existing conditional/opt-in benchmark, visual/manual, large-capacity or deferred worksheet coverage. No tests were disabled or assertions/goldens relaxed. The current-run XML reports agree with Maven's 3,471 total. An older, separately named `WeightedRestorationTest(phase9c-compatibility)` XML remains in target and was excluded from the current-run XML count; it was not deleted or counted as newly executed.

Selected full-suite evidence, all zero failures/errors/skips: new ProjectionSnapshotPersistenceTest 13; RetainedProjectionCodecTest 27; AnalysisFingerprintTest 71; MonteCarloNumericCompatibilityTest 3; LegacyWeightedSnapshotCompatibilityTest 2; CoupleSocialSecurityPersistenceTest 10; ExhaustivePersistenceTest 11; WeightedRestorationTest 14; AnalysisParameterCoordinatorTest 24; MonteCarloParameterPersistenceTest 20; SocialSecurityParameterPersistenceTest 28; SocialSecurityAndQuickPdfTest 9. Financial engine/state-tax/RMD/Roth and seeded Monte Carlo regressions ran as part of the complete suite.

Final `git diff --check`: exit 0. Branch/HEAD unchanged. Index empty. Final inventory: 29 modified tracked files and 31 untracked files, including all preserved earlier work and this report. No staging, commits, history changes, cleanup, file discards or unrelated edits were performed. The complete path list above and the phase-specific inventory distinguish accumulated work from this phase.

Acceptance remains limited by the explicit native checklist, legacy absence of original results, companion-file portability requirements, existing dark-chart legend contrast and unmeasured worst-case Save latency/peak memory. No native acceptance is claimed. Stop here for review.