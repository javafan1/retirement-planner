# Phase 12E — Immutable Analyzer Associations and Visualization

## Executive summary and authorized scope

Implemented Option 2: all 15 analyzer identities can record an explicit association with the exact admitted, accepted projection UUID. Quick Comparison, Individual Deterministic, and Individual Social Security Only now have durable last-success result lifecycles. The earlier scope-decision gate in this report is superseded by the user's explicit Option 2 authorization. No legacy visible/memory-only result is inferred to belong to a projection.

Phase 12F has not begun. There is no swap action, save-before-swap dialog, new directional Break-Even computation, financial algorithm change, dependency change, or storage-limit increase. User-confirmed Phase 12D native acceptance is background evidence; Phase 12E native acceptance remains pending.

Final verification and readiness are recorded in the acceptance section at the end of this report.

## Preparation and Git baseline

Read AGENTS.md and the Phase 12C/12D/12B, Phase 9 persistence, Phase 10 parameter, Phase 11 grid/navigation/PDF and Monte Carlo codec/lifecycle reports; inspected actual execution, repository, codec, report and UI paths. Branch `main`; HEAD `e620bec4a0011ab12d15fce670bff5011093d9da`. Index empty at start. The Phase 12D report recorded 29 modified tracked paths and 31 untracked status entries, including accumulated Phase 11 and 12 work. Those changes were preserved. The expanded final file inventory below distinguishes files rather than grouped directory status entries.

Pre-implementation focused baseline: 97 passed, zero failures/errors/skips, 32.169 seconds (`target/phase12e-preflight.log`). That is baseline evidence, not proof of the new implementation.

## Schema and component architecture

The plan's existing nonfinancial `projectionState` is extended with role-manifest version 2. Version 1 remains readable, and its empty new collections are omitted when written. Unsupported manifest versions are rejected. CompleteProjectionSnapshot remains version 1.

Version 2 retains current/baseline component references and adds:

- `associations`: bounded, typed AnalyzerAssociation version-1 records: owner UUID, exact projection UUID, original projection input-document SHA-256, allowlisted AnalysisKind, analyzer run UUID, exact original input/context/payload digests, immutable compressed-component SHA-256, and original completion timestamp.
- `pinnedProjections`: bounded references to original projection components whose recorded analyzer associations must survive recalculation or removal of a visible baseline role. These are references, not embedded annual graphs.

References are validated for supported version, ownership, unique projection/kind keys, valid digest syntax, consistent same-ID role/pin digests, and non-orphan pinned projections. A reference must identify a visible role or a recorded pinned projection. An analyzer envelope must exactly match owner, kind, run, fingerprints, payload digest and timestamp. Loading additionally verifies the referenced original projection, exact input digest and household shape. No association is established by matching names, dates or approximate financial values.

Immutable analyzer files share the existing component directory and path binding:

`.retirement-planner-projections/<plan-uuid>/analysis-<run-uuid>.json.gz`

Projection components retain their existing names. Plan JSON contains small references, never duplicated Monte Carlo histories or financial result graphs. Multiple roles reuse one projection/component identity. Replaceable last-success slot files remain separate from immutable historical artifacts: a mutable slot path is never used as a permanent reference.

### New result families

- QuickComparisonSnapshot v1 stores the original baseline graph, every deduplicated success/failure in caller order, all retained candidate projections, exact 14 metrics/differences, complete supplied candidate list, original SS ranks/expected values, prerequisite fingerprint/run, requested and supplied counts, and original baseline description. First-seen deduplication is validated against the original supplied list; missing candidates, inconsistent references and invalid versions are rejected.
- IndividualDeterministicSnapshot v1 stores the primary-only baseline, nine original candidate entries, competition ranks/ties and ranked occurrence ordering, exact metrics/differences, retained projections, original person/horizon/death-year context, and the already completed immutable Break-Even results. Restoration does not calculate new Break-Even results. These are the existing individual comparisons, not the deferred Phase 12F directional feature.
- Individual SS Only reuses the existing WeightedSnapshots individual family and explicit kind dispatch. No replacement financial representation was introduced.

All three use the existing AnalysisSnapshot envelope, digest rules, allowlisted codec, repository, result-store worker, Retry Save, backup/recovery and Save As machinery. Quick and Individual Deterministic raw encoding and digest streaming are bounded by the existing decoded-document ceiling. Original weighted, SS, Exhaustive v2 interning and Monte Carlo shared-prefix formats/digests are unchanged.

## Association lifecycle and acceptance

1. Request preparation captures an immutable AnalyzerAdmission: plan identity, exact accepted projection UUID and input digest, source revision, projection generation, analyzer kind, fresh run ticket, and prepared effective-request fingerprint. A projection must exist and match the current financial inputs; household shape must match.
2. The existing job/session eligibility and AnalysisSuccessPolicy remain authoritative. The ticket is the accepted analyzer run UUID. The final callback also checks existing UI edit/parameter ownership guards. Completion input fingerprint must equal the prepared fingerprint. For Monte Carlo the original V1 stored fingerprint is used for provenance; V2 numerical compatibility remains unchanged for currency assessment.
3. Only eligible successful completion records a pending immutable association. Canceled, failed, all-unsuccessful, obsolete, wrong-owner, superseded-projection, wrong-run and mismatched-request completions cannot replace it. A newly accepted last-success result can still be kept when no valid projection admission exists, without claiming a historical association.
4. The last-success slot saves asynchronously through its original lifecycle. Historical pin durability is separate: ordinary plan Save stages and verifies immutable projection/analyzer components, enforces the shared budget, commits the root manifest atomically, then publishes materialized references and clears pending handoffs. Pending all-zero digest placeholders are accepted only with the exact in-memory completion; they cannot resolve a missing on-disk artifact.
5. The history UI and exported context explicitly distinguish accepted-in-memory/pending ordinary Save from a verified committed historical component. Closing a dialog does not cancel an accepted result-store write. Configuration-only auto-save does not silently commit the projection manifest or unrelated financial edits.

The manifest holds the latest accepted association per projection/kind, independently of the latest analyzer slot. A newer successful run for the same projection/kind can publish a replacement reference; the original immutable artifact is never modified or garbage-collected. Merely opening an analyzer or loading a legacy slot cannot create a pin.

Recalculation archives references for recorded projection identities; it does not relabel results as belonging to the new current projection. Baseline capture/clearing/replacement changes role visibility while retaining recorded artifacts/projection references. Earlier associated projections are available as explicitly labeled earlier-projection choices. There is no unbounded history of every run.

## Analyzer coverage matrix

| Identity | Durable result | Historical presentation |
|---|---|---|
| COUPLE_SOCIAL_SECURITY | Existing SS payload | Original Stage 1 shared grid/markers; complete Stage 2 rankings/elections; original assumptions/PDF |
| COUPLE_QUICK | New Quick v1 | Original ordered candidates, baseline, differences, failures/provenance; retained annual tables; table-only PDF |
| COUPLE_EXHAUSTIVE | Existing Exhaustive v2 | Existing objective/metric heat map, ranking groups, frozen SS reference, retained original annual tables/PDF |
| COUPLE_WEIGHTED | Existing weighted | Existing weighted heat map, competition ranks, original optional baseline states and frozen comparisons/PDF |
| INDIVIDUAL_DETERMINISTIC | New individual deterministic v1 | Primary-only nine-age values/ranks, original metrics and retained annual results; original PDF; stored existing comparisons retained |
| INDIVIDUAL_SOCIAL_SECURITY | Existing individual payload, new lifecycle | Primary-only nine-age expected SS values/ranks and original assumptions/PDF |
| INDIVIDUAL_WEIGHTED | Existing individual payload | Primary-only nine-age weighted values/ranks and original assumptions/PDF |
| COUPLE_MONTE_CARLO_FIXED | Existing MC v1 | Original fan chart, percentile tables, seeds/settings, PDF |
| COUPLE_MONTE_CARLO_LONGEVITY | Existing MC v1 | Original longevity fan/population/statistics and PDF |
| COUPLE_MONTE_CARLO_PAIRED_FIXED | Existing MC v1 | Original matched A/B outcomes/differences/chart and PDF |
| COUPLE_MONTE_CARLO_PAIRED_LONGEVITY | Existing MC v1 | Original matched market/inflation/lifetime results and PDF |
| INDIVIDUAL_MONTE_CARLO_FIXED | Existing MC v1 | Primary-only fan/results/PDF |
| INDIVIDUAL_MONTE_CARLO_LONGEVITY | Existing MC v1 | Primary-only longevity fan/results/PDF |
| INDIVIDUAL_MONTE_CARLO_PAIRED_FIXED | Existing MC v1 | Primary-only matched comparison/results/PDF |
| INDIVIDUAL_MONTE_CARLO_PAIRED_LONGEVITY | Existing MC v1 | Primary-only matched longevity comparison/results/PDF |

## Historical UI and navigation

Projection Analyzer History is available from Results Summary and from Baseline Details. The read-only resizable modal selects the exact current, baseline or earlier pinned projection UUID and analyzer identity. Missing association displays an explicit not-captured/legacy-unknown explanation. It never substitutes a newer last-success slot or starts an analyzer.

Only the selected artifact is decoded on the existing bounded AnalysisResultStore worker. Algorithm/reference compatibility is checked against the original saved envelope before rendering. Original input/context digests are not compared to unrelated live editor fields to manufacture Current status. Quick's history-specific PDF uses neutral original-projection labeling; ordinary interactive Quick export keeps its existing Current/Outdated rules.

The viewer reuses the existing SS grid/palette, integrated heat-map models/rendering, MC fan/difference charts, pure report adapters and PDFBox exporters. It shows original assumptions/request/comparison sections. Tables preserve source order and are read-only/non-sortable, with tooltips for complete text. Individual nine-age selections and integrated metric/strategy selection only format original values. Available original retained projections are selectable in a collapsed annual-results section; unretained details remain unavailable.

Plan/view ownership, selected reference and load-generation checks prevent delayed callbacks from replacing a later selection or attaching to another plan/closed dialog. UI controls are created only on FX; heavy reconstruction and PDF writing use the existing background worker. Export captures selection on FX and serializes one export at a time. A completed export cannot overwrite another selection's status. No JavaFX nodes are serialized or passed to PDF rendering.

Ordinary analyzer dialogs retain Phase 11B navigation and existing workflows. Restored Quick still requires a compatible SS prerequisite before a new run. Historical Quick can export without satisfying a new-run prerequisite. A newer SS result cannot rewrite its frozen original prerequisite. Quick compatibility is refreshed after prerequisite restoration and candidate-count edits; changing/reverting restored count returns Outdated/Current through the original fingerprint service.

## Persistence, ownership and recovery

Ordinary Save stages components before the authoritative plan manifest replacement, under the existing cooperative save coordination/path binding and durable-file checks. Reused components are read/verified rather than overwritten. Failure preserves the prior root manifest/artifacts and pending accepted memory state for retry. Failed saves may leave unreferenced staged immutable files; no automatic garbage collection was introduced.

Save As clones under an independent owner UUID and path binding, retaining original financial payload/context digests, run IDs, timestamps and input identity. Envelope/component digests are recomputed for the new owner. Ordinary same-owner Save does not invent copy provenance. Original files are unaffected. The existing last-success Save As copy process remains independent.

Version-1 plans/manifests and existing analyzer snapshots continue to load. Legacy memory-only results are not imported or reconstructed from parameter configurations. Legacy unassociated persisted results remain usable through their existing last-success UI but do not acquire projection provenance. Missing/corrupt/unsupported/oversized components are unavailable; their manifest references are retained and unsafe replacement is refused.

## Storage and allocation bounds

Unchanged per-component/document limits: 33,554,432 decoded bytes; 16,777,216 compressed bytes; 2,000,000 tokens; 100,000 array containers; depth 80; strings 256,000 characters; numeric tokens 4,096 characters. Existing retained-projection and MC expansion validation remain active. No unsafe polymorphic deserialization or expanded unbounded JSON document was added.

New manifest tables are limited to 30 associations and 30 pinned projection references. At capacity a further distinct association is visibly refused, retaining previous pins and the independently accepted last-success result. No association is silently discarded to fit. Only current/baseline ordinary projection graphs are eagerly restored; archived projection/analyzer components load individually. Pending pin graphs are shared immutable references, not copied financial graphs.

The existing shared per-owner compressed budget remains 128 MiB and 1,000 directory entries, including immutable artifacts. The existing projection staging count check was tightened at its off-by-one boundary to refuse a new entry when 1,000 already exist. No limit was increased. Save failures preserve existing data; larger plans or repeated histories can reach these limits and require an explicit future storage decision. No claim is made that every possible cutoff-tied Quick universe fits one document.

### Capacity evidence

Newly calculated production-engine Quick fixture: requested 20; cutoff ties produced 64 unique supplied complete strategies; 40-year horizon; three accounts with both owners/account types. Every candidate was independently evaluated through the actual comparison service. No reused synthetic outcome was substituted. Exact candidate/result/difference/annual graph comparisons pass after decoding.

Measurements are in `target/phase12e-previews/quick-capacity.txt`; final values are recorded in the acceptance section. Sizes include complete baseline and retained candidate projections. Timings are individual local measurements, not a benchmark average. No isolated peak-memory measurement was made for this new fixture; the tests run under the stated 4 GiB heap and the structural/document bounds remain enforced.

Existing MC 10,000-trial capacity evidence is reused, not a newly calculated 10,000-trial simulation: Phase 2B reports 20,695,338 raw bytes, 7,613,054 gzip bytes, 1,732,788 tokens, 303-character maximum numeric token; final replay encode/decode/reconstruction 856/479/96 ms. Its original measured peak/used-heap caveats remain applicable. Phase 12E newly calculates small independent three-trial histories for all eight MC association/rendering cases. The original real-world Exhaustive capacity acceptance is prior user-confirmed evidence, not a newly measured native payload here.

## Financial, fingerprint and compatibility invariants

No projection, SS, tax, RMD, Roth, mortality, ranking or seeded Monte Carlo calculation source was changed by Phase 12E. BigDecimal values/scales are retained; rounding is display-only. Account registries reconstruct detached graphs with original reference relationships; equal-looking distinct accounts are not merged. Quick does not introduce an integrated optimizer or reorder SS-ranked input candidates.

The existing exclusion of the recognized nonfinancial `projectionState` root property already covers this manifest extension. No financial fingerprint contract, V1 stored MC fingerprint, V2 numeric compatibility, historical digest/schema or Phase 10 parameter schema was changed. No new root exclusion was added in this phase. Historical restoration and report generation have no financial calculation factories; throwing-engine lifecycle tests and zero-job-generation checks prove no execution during restore.

All 15 accepted-parameter configurations, automatic configuration-only persistence, unsaved financial edit isolation, dirty-state handling, Retry Parameters Save and Save As behavior remain independent. Creating an association does not dirty financial inputs, increment a financial revision or run a projection. Committing accepted pins requires ordinary Save; it is not falsely reported as an automatic parameter save.

## Verification and visual inspection

New tests cover all 15 component save/restart/Save As cases; exact payload/context/digest/timestamp preservation; request/run/generation/owner rejection; failed/canceled replacement retention; baseline clearing/recalculation; missing/corrupt/unsupported/oversized artifacts; manifest/component write failure; backup recovery; budget/capacity refusal; and historical view load/navigation/closure races.

Actual eligible callback tests cover the newly persisted individual modes, including failure/cancellation and restart using throwing calculation factories. All eight actual MC view completion paths verify original request fingerprint/run and accepted projection UUID binding. Quick's existing callback/PDF test now requires actual durable restoration instead of the superseded memory-only expectation, preserves original prerequisite across newer SS results, and verifies restored candidate-count change/revert. Existing version-rejection tests reject manifest version 3 now that version 2 is explicitly supported; assertions were not disabled or weakened.

Generated fixtures/previews are under ignored `target/phase12e-previews/`: all 15 historical presentation PNGs and PDFs plus first-page PDF PNGs. Couple previews use 1180×820 light; individual previews use 900×650 dark. Actual images inspected include SS grid/combined markers/ranking table, individual deterministic age colors/selection, MC paired chart bands, Quick PDF, and representative integrated/individual/SS PDF pages. Initial preview inspection exposed missing style-container ancestry in the new viewer; reusing the existing claiming-grid and MC stylesheet classes fixed the color rendering. No global stylesheet/palette was changed.

Automated previews establish source-value/rendering evidence, not Windows DPI, high-contrast or screen-reader certification. Existing wide report tables retain tooltips/full source rows; reduced-window usability and long values require the native checklist. PDFs reuse existing pagination/layout, without a reporting redesign.

## Known limitations and Phase 12F prerequisites

- Native Phase 12E acceptance has not been performed by Codex. User acceptance of Phase 12D is not acceptance of the new viewer/lifecycles.
- Historical pins are pending until ordinary Save; last-success slot durability and parameter durability are separate statuses.
- One latest association per projection/kind is visible in the manifest. Immutable superseded artifacts remain on disk; no all-run history browser or garbage collection is supplied.
- Capacity is finite. Failure/refusal is explicit and preserves previous durable data; no financial fields are omitted. Companion storage must accompany the plan file when moved/backed up, under the existing path/ownership policy.
- Explicit ordinary Save still stages components through the existing repository path; large multi-component Save latency is not certified as zero UI latency. Lazy history decoding/PDF writing are off FX.
- The historical viewer is read-only. It reuses original charts/ranks/report data and exposes retained annual tables, not editable analysis controls or newly generated missing details. Existing stored individual Break-Even results survive; no new directional calculation is added.
- Original legacy association cannot be inferred and is shown as not recorded. Unsupported financial/reference versions remain unavailable rather than being recalculated.
- Phase 12F can use getAnalyzerAssociations(projection UUID), immutable manifest references and loadAssociatedAnalyzer(reference) to inspect role-following artifacts. It still needs separately authorized atomic editable-plan/result role exchange, unsaved-edit/freshness gates and the approved save-before-swap workflow. No such operation exists here.

## Native Windows acceptance checklist

1. Rebuild/restart normally in IntelliJ. Open a saved couple plan; run and accept the ordinary projection before running an analyzer.
2. Run SS Only, Quick, Exhaustive, Weighted, then each MC identity with distinctive settings. Confirm last-success/parameter save feedback; ordinary Save commits historical pins.
3. Capture baseline, alter inputs and accept a new projection. Open Baseline Details → View Projection Analyzer History; verify the old exact projection's original SS grid, ranks, metrics, assumptions, selections, retained annual results and PDFs. Confirm later SS runs do not rewrite old Quick provenance.
4. Reopen the application. Open Projection Analyzer History from Results Summary, select current/baseline/earlier recorded identities and each available kind. Verify no progress/run is started; unavailable/legacy results are labeled honestly.
5. Repeat with a single-person plan: Individual SS Only, Deterministic and Weighted plus four MC variants. Verify nine-age values/independent parameters, actual restoration, original PDF values/timestamps and retained annual results.
6. Cancel/fail replacement runs. Confirm previous last-success result, settings and historical association remain; newer control edits are not rolled back by obsolete callbacks.
7. Change/revert Quick candidate count and prerequisite inputs. Confirm normal readiness/Current/Outdated behavior, safe historical export, and no bypass of the new-run SS prerequisite.
8. Save As, reopen original and clone independently, switch couple/individual plans while work is pending, and close dialogs during save/load. Confirm no wrong-plan result or late navigation.
9. Verify pending pin versus saved component notices, failed write/retry without calculation, unchanged dirty financial edits during automatic parameter saves, and explicit ordinary Save behavior.
10. Inspect 1180×820 and 900×650, light/dark themes, keyboard selection, tooltips, table scrolling, chart selection and PDFs. Do not attempt swapping; Phase 12F is absent.

## Exact Phase 12E source/test inventory

New production files:

- app/projection/AnalyzerAdmission.java; AnalyzerAssociation.java
- app/socialsecurity/results/QuickComparisonSnapshot.java; IndividualDeterministicSnapshot.java
- ui/socialsecurity/QuickSnapshotAdapter.java; HistoricalAnalyzerPresentation.java; ProjectionAnalyzerHistoryDialog.java

Existing production files extended by this phase (all under src/main/java/com/daviddunn/retirementplanner):

- app/projection/ProjectionRoleManifest.java (previously untracked Phase 12D work)
- app/socialsecurity/results/AcceptedAnalysisCompletion.java; ExhaustiveSnapshot.java
- domain/model/RetirementPlan.java (transient pin handoffs/cache references only in this phase)
- persistence/AnalysisSnapshotCodec.java; ProjectionSnapshotComponents.java; JsonRetirementPlanRepository.java
- ui/controller/ApplicationController.java; ui/MainWindow.java; ui/views/ResultsSummaryView.java
- ui/baseline/BaselineDetailsDialog.java
- ui/montecarlo/MonteCarloAnalysisView.java; MonteCarloStrategyComparisonView.java; MonteCarloParameterControls.java
- ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java; SocialSecurityParameterBinding.java; WeightedResultPersistence.java; IndividualCompletionPersistence.java; SinglePersonIntegratedView.java; SinglePersonMortalityView.java; QuickComparisonReportAdapter.java; ExhaustiveResultSnapshots.java

New tests (src/test/java/com/daviddunn/retirementplanner):

- app/montecarlo/Phase12EHistoryFixtures.java (small real-engine fixtures; not large-capacity evidence)
- persistence/AnalyzerAssociationStorageTest.java
- ui/socialsecurity/Phase12EAssociationTest.java; Phase12ECodecTest.java; Phase12EIndividualLifecycleTest.java
- ui/montecarlo/MonteCarloProjectionAssociationTest.java

Existing tests extended: persistence/ProjectionSnapshotPersistenceTest.java (supported manifest version dispatch); ui/socialsecurity/SocialSecurityAndQuickPdfTest.java (newly authorized durable Quick lifecycle and restored compatibility). This report is the new/updated Phase 12E document. Other Phase 11/12A–12D files below remain preserved pre-existing work, not new Phase 12E financial changes.

Generated PDFs/PNGs, Maven logs/XML and capacity text under target/, project-local Maven cache and local IDE artifacts are excluded from a future commit. No real user plan/snapshot was added. No staging, commit, push, reset, cleanup or history operation was performed.

## Complete working-tree inventory

Paths below include preserved earlier-phase work; they are not all newly created by Phase 12E.

```text
Tracked modifications against HEAD:
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
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloParameterControls.java
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
src/test/java/com/daviddunn/retirementplanner/ui/controller/ApplicationControllerTest.java
src/test/java/com/daviddunn/retirementplanner/ui/controller/BreakEvenControllerTest.java
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloComparisonViewTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/CoupleSocialSecurityPersistenceTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedRestorationTest.java
src/test/java/com/daviddunn/retirementplanner/ui/views/SinglePersonHouseholdUiTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/CompleteWizardEndToEndTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/StateTaxWizardIntegrationTest.java

Untracked files:
PHASE-11A-SOCIAL-SECURITY-UI-PDF-AUDIT.md
PHASE-11B-SHARED-GRID-AND-NAVIGATION.md
PHASE-11C-SOCIAL-SECURITY-AND-QUICK-PDF-EXPORT.md
PHASE-11D-FINAL-INTEGRATION-AND-REGRESSION-AUDIT.md
PHASE-12A-BASELINE-DETAILS-ARCHITECTURE-AUDIT.md
PHASE-12B-BASELINE-DETAILS-IMPLEMENTATION.md
PHASE-12C-PROJECTION-SNAPSHOT-AND-SWAP-ARCHITECTURE-AUDIT.md
PHASE-12D-COMPLETE-PROJECTION-SNAPSHOT-PERSISTENCE.md
PHASE-12E-IMMUTABLE-ANALYZER-ASSOCIATIONS.md
src/main/java/com/daviddunn/retirementplanner/app/baseline/BaselineDetailsSession.java
src/main/java/com/daviddunn/retirementplanner/app/baseline/BaselineParameterComparison.java
src/main/java/com/daviddunn/retirementplanner/app/baseline/ProjectionInputs.java
src/main/java/com/daviddunn/retirementplanner/app/projection/AnalyzerAdmission.java
src/main/java/com/daviddunn/retirementplanner/app/projection/AnalyzerAssociation.java
src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java
src/main/java/com/daviddunn/retirementplanner/app/projection/ProjectionRoleManifest.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/IndividualDeterministicSnapshot.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/QuickComparisonSnapshot.java
src/main/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotComponents.java
src/main/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsDialog.java
src/main/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisPdfData.java
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
src/test/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsViewTest.java
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloProjectionAssociationTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12EAssociationTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12ECodecTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12EIndividualLifecycleTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SharedClaimingAgeGridTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAndQuickPdfTest.java
```

## Final acceptance record

Final focused run: **277 total; 275 passed; 0 failures; 0 errors; 2 skipped**, Maven **01:15**, completed October 26, 2026 at 17:14:27 America/New_York. Log: `target/phase12e-release-focused.log`. The two skips are existing opt-in calculated-capacity/replay cases in MonteCarloSnapshotCodecTest; new Phase 12E tests have no skips. No exclusions were added to the complete suite.

Two preceding complete-suite runs passed 3,513 total / 3,469 passed / 44 skipped / zero failures or errors, in 10:15 and 10:17. These preceded the final cross-reference/status refinements and the added numerical cross-reference regression test. They are not substituted for the final full-source run.

Final full-source run: **3,514 total; 3,470 passed; 0 failures; 0 errors; 44 skipped**, Maven **10:13**, completed October 26, 2026 at **17:24:42 America/New_York**. Log: `target/phase12e-release-full.log`. Current-run XML for the 402 classes named in this run agrees exactly. An older separately named Phase 9C compatibility XML in target was not counted as newly executed. The 44 skips are existing conditional/opt-in benchmark, visual/manual, large-capacity or deferred worksheet cases; no test was newly disabled. All 43 new Phase 12E tests passed.

Localized findings corrected during implementation: the Quick count is not a hard cap on cutoff ties; arbitrary context preferences cannot be used for new provenance; pending restoration notices require a completion-aware test wait; reused chart/grid styles need their original ancestor classes; original Exhaustive cross-reference numerical values must survive the history adapter; Quick restoration must reassess after SS prerequisite load/count changes; same-owner Save must not invent clone provenance; pin notices must distinguish pending from committed; inconsistent role/pin references and household mismatches must be rejected. These corrections change no financial calculation or seeded/golden result.

The new association tests run all 15 exact component/restart/Save As cases, load-race cases and historical presentation/PDF generation. The eight actual MC completion paths and two newly persisted individual completion paths are tested separately. Repository tests inject VERIFY/BACKUP/REPLACE failures and component obstruction, missing/oversized/unsupported components, shared-storage budget pressure and association-capacity refusal. Existing projection, fingerprint, frozen weighted, analyzer, parameter and PDF regressions run without weakening assertions.

Final newly calculated Quick capacity: **8,791,342 raw bytes; 921,842 gzip bytes; encode 168 ms; decode 145 ms; independent domain reconstruction 7 ms**. Requested 20 / supplied 64 / unique 64, 40 years, three accounts. Exact source DTO, metrics, differences and independently restored annual graphs are verified. These are local single-run measurements; no new 5,000/10,000-trial capacity simulation or isolated peak-memory benchmark is claimed.

Visual inspection included SS PDF page 3 (complete Stage 1 grid, both markers, legend, original assumptions), Quick continued assumptions and page 9 (all-fourteen-metric continuation, explicit failed candidates, warnings/limitations), weighted and individual report pages, integrated grids and Monte Carlo paired bands. The synthetic fixture PDFs have 32 SS pages, 9 Quick pages, 10 Exhaustive pages, 9 Weighted pages, 4 fixed-MC pages and 5 paired-longevity MC pages; these are fixture-specific counts, not promised report lengths. Auxiliary page rendering uses the existing PDFBox dependencies; no library was added.

Final Git: **39 modified tracked files and 45 expanded untracked files**, including preserved earlier-phase work and this report. Branch and HEAD unchanged; index empty. `git diff --check` passed (exit 0; line-ending advisories were suppressed only for that read-only check with `-c core.safecrlf=false`, without changing repository configuration). No golden file, dependency, financial algorithm or persistence safety limit was changed. Generated output/cache files remain ignored. No stage/commit/push/reset/clean/discard operation occurred.

**Readiness: Phase 12E implementation and automated verification passed; ready for review and native Windows acceptance.** Native acceptance is not claimed. Historical pin commitment requires ordinary plan Save; last-success and parameter automatic saves remain separate. Stop here: Phase 12F is not implemented or authorized.