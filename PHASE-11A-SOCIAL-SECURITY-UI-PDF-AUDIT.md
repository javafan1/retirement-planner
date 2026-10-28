# Phase 11A — Social Security UI and PDF enhancement audit

## 1. Scope, baseline and recommendation

Audit only. No production, test, financial, persistence or CSS changes are authorized or made. Recommendations below are proposed designs, not implemented behavior.

Initial repository: `main`, HEAD `e620bec4a0011ab12d15fce670bff5011093d9da`, clean working tree and index. Reviewed AGENTS.md, Phase 8C contextual layout, Phase 9C restoration, Phase 9D-1 SS persistence, Phase 9D-2 Gate 4 historical PDF fidelity and Phase 10D parameter implementation reports, and the current sources/tests. Earlier reports describe earlier boundaries; current source takes precedence.

Recommend four small implementation stages: shared grid rendering; guarded result-navigation policy; couple SS PDF; Quick PDF. Preserve financial adapters, accepted-result gates, all fifteen parameter configurations and existing snapshot formats. Do not add Quick result persistence.

Two important findings govern the design:

- SS Only grid cells represent **Stage 1 expected Social Security present value**. Integrated heat maps represent the best successful complete strategy for each retirement-age pair under their own objective. Matching colors must not change those meanings.
- Existing PDF infrastructure is reusable, but its visualization contract currently requires either an integrated heat map or an individual nine-age view. Quick needs an explicit table-only report variant, not a fabricated heat map.

## 2. Current implementation inventory

Java paths below are relative to `src/main/java/com/daviddunn/retirementplanner/`; line locations are audit-time anchors.

| Source | Verified responsibility and relevant location |
|---|---|
| `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java` | Couple workspaces; SS tabs in `socialSecurityContent()` (~567); `runAnalysis()` (~736); `render()` (~811), `renderGrid()` (~828), `gridDetail()` (~865); Quick preparation/acceptance `runIntegratedAnalysis()` (~874); existing PDF readiness/capture/export (~1655–1740) |
| `ui/socialsecurity/SocialSecurityStrategyAnalyzerPresentation.java` | SS complete-strategy ranks and retirement-grid result access; retain its ordering/tie semantics |
| `ui/socialsecurity/ClaimingStrategyHeatMapView.java` | Integrated metric selector, toggle-cell grid, legend, tooltips, selection/detail navigation and responsive layout |
| `ui/socialsecurity/ClaimingStrategyHeatMapModel.java`, `ClaimingStrategyHeatMapCell.java`, `ClaimingStrategyHeatMapMetric.java`, `ClaimingStrategyHeatMapProfile.java`, `ClaimingHeatMapFormatting.java` | Pure integrated heat-map data and formatting contracts; cell strategies require complete survivor elections |
| `ui/socialsecurity/DeterministicHeatMapAdapter.java`, `LongevityWeightedHeatMapAdapter.java` | Existing financial-objective-specific pure adapters; must remain semantically unchanged |
| `util/ClaimingHeatMapPalette.java` | Shared UI/PDF thresholds and colors |
| `src/main/resources/css/claiming-strategy-heat-map.css` | Cell, hover, selected, optimal, focus and container styles |
| `ui/socialsecurity/IntegratedAnalysisResultTabs.java` | Ranked-first constructor, natural-height layout and deferred outer-scroll reveal on heat-map selection |
| `ui/socialsecurity/LongevityWeightedIntegratedView.java` | Weighted ranked/heat-map tabs and expandable sections; `render()` (~134); detail action selects Ranked Strategies (~215) |
| `ui/socialsecurity/SocialSecurityAnalyzerJobController.java` | Eligibility, generation/revision, cancellation, terminal hooks; `finish()` (~171) rechecks eligibility after state observers before success publication |
| `ui/socialsecurity/WeightedResultPersistence.java` | Reused asynchronous historical load, binding/generation guards, currency, durability/retry and historical PDF notice; `exportable()` (~219), `report()` (~263) |
| `ui/socialsecurity/CoupleSocialSecuritySnapshotMapper.java`, `ExhaustiveResultSnapshots.java`, `AnalysisSnapshotPresentationMapper.java` | Existing pure restoration and frozen context/preferences mapping |
| `ui/socialsecurity/IntegratedReportContext.java`, `IntegratedAnalyzerReportAdapter.java` | Frozen report-time names/assumptions and detached report construction for existing couple integrated exports |
| `app/export/IntegratedAnalyzerReport.java`, `IntegratedAnalyzerPdfExporter.java`, `PdfReportSupport.java` | Immutable report values; PDFBox layout, tables, pagination, heat maps and file output |
| `ui/socialsecurity/IndividualAnalyzerReportAdapter.java`, `IndividualPdfExportAction.java`, `SinglePersonIntegratedView.java`, `SinglePersonMortalityView.java` | Existing individual exports; reuse patterns without adding duplicate exports or spouse UI |
| `ui/socialsecurity/IntegratedSocialSecurityComparisonPresentation.java` | Quick rows in original analyzer/caller order, original SS rank, structured success/failure |
| `app/socialsecurity/IntegratedSocialSecurityStrategyComparisonResult.java`, `IntegratedSocialSecurityStrategyComparisonEntry.java` | Quick baseline; supplied/unique candidate counts; exact strategies, optional SS values, integrated results/differences or failures |

Phase 10 `SocialSecurityParameterBinding`, mapping, coordinator and configuration-only transactions remain independent from report/navigation state. Quick accepted parameter persistence does not persist its financial results.

## 3. Heat-map gap analysis

| Concern | SS Only today | Exhaustive/shared integrated today | Proposed SS treatment |
|---|---|---|---|
| Dimensions | Actual request axes, normally 9×9 ages 62–70 | Standard 9×9 | Use actual SS axes; do not manufacture missing cells |
| Orientation | Primary rows, spouse columns | Primary columns, spouse rows | Recommend aligning SS to primary horizontal; explicit axis labels and coordinate tests required |
| Geometry | Ordinary Buttons, 6-pixel gaps, content-based sizing | ToggleButtons, 4-pixel gaps, fluid columns, 40-pixel minimum/preferred cell height | Share grid layout and sizing |
| Values | Stage 1 expected PV currency | Selected integrated metric, default percent of optimum | Retain SS PV as default cell text; tier background from Stage 1 percentage |
| Colors/legend | No financial tier colors or legend | Five exact palette tiers, unavailable gray | Reuse palette, legend and CSS; identify denominator as highest Stage 1 SS expected PV |
| Optimal | Star for exact Stage 1 highest cells | Star, strong border and accessible optimal text | Preserve all exact SS ties, independent of formatted percentage |
| Advanced candidates | Triangle for retained retirement cells, except star takes precedence | Complete strategy and rank represented in details | Preserve advanced-to-Stage-2 information independently of optimal marker |
| Selection | Click updates text; no durable selected styling | Toggle group, blue selection border; selects first optimum/default | Shared selected styling; pair identity, not child/index position |
| Hover/help | Generic tooltip | Age/metric-specific tooltip, hover underline | Rich SS-specific tooltip; retain PV/real/nominal and Stage 2 availability distinctions |
| Detail click | Updates gridDetails only | Opens selected cell details; separate action opens ranked full strategy | Preserve SS details; best expanded strategy only when actually retained/available |
| Accessibility | Standard button behavior, limited semantic help | Focus styling and age/metric/optimal accessible descriptions | Equivalent age, Stage 1 value, retained, selected and optimum descriptions |
| Responsive | Grid nested in its own fit-to-width ScrollPane | Stacked below width 1200, grid/details side-by-side above; outer natural-height scrolling | Reuse logical-size layout; avoid nested scroll traps and clipped labels |
| Historical | Rebuilds grid from snapshot; restores complete-strategy table occurrence only | Restores available metric, selected strategy and some tab preferences | Rebuild from saved SS output without calculations; default missing grid selection deterministically |

Exact palette thresholds: >=99.5% `#85c8bc`; >=99% `#b7ddd1`; >=97% `#e5e4b1`; >=95% `#f0c98a`; below95% `#e9a58d`; unavailable `#e5e8ec`. Optimal border `#174d43`; selected border `#173f78`. Palette comparisons use unrounded BigDecimal percentages. Preserve that behavior; displayed rounded 100% alone must not imply exact optimum. For zero/nonpositive optimum use explicit unavailable percentage semantics rather than inventing a division rule.

SS Stage 1 and Stage 2 must remain visibly separate: a grid's highest retirement pair need not identify the highest expanded complete survivor strategy. `gridDetail()` already distinguishes Stage 1 values from `bestForRetirement()` Stage 2 values. Do not replace the grid with the latter.

### Proposed shared component

Extract a small UI-only grid/legend renderer from `ClaimingStrategyHeatMapView`, with age-pair cell display values, tier, optimal/retained markers, accessible text and a selection callback. Keep integrated metric/strategy detail wrappers and adapters. Add a pure SS Stage 1 adapter and SS wrapper for its details.

Proposed new classes: `ClaimingAgeGridView`, `ClaimingAgeGridPresentation` and `SocialSecurityClaimingGridAdapter` in `ui/socialsecurity` (names subject to implementation review). Reuse CSS/palette. Avoid a general UI framework.

Simply adding an SS profile to the existing integrated model is unsafe: it requires nonnull survivor elections, contains estate-specific metric vocabulary, and explains cells as complete-strategy outcomes. Do not supply fake elections or place SS PV into estate fields. Sharing only copied CSS would leave duplicate grid behavior; the small renderer extraction gives a single implementation while preserving separate semantics.

No new saved presentation-preference schema is needed for these enhancements. Existing SS snapshot preferences record a complete strategy occurrence, not a grid cell. New grid selection can be session-local with deterministic fallback; persisting a new selection contract would require separate approval.

## 4. Result-tab lifecycle design

### SS Only

There is no literal Results tab today: the workspace has a Completed Results section containing Recommendation, Claiming Grid, Top Strategies and Assumptions. `socialSecurityTabs` is currently local, so a retained reference or small workspace-owned selection method is needed.

Recommend selecting **Claiming Grid** after an eligible, accepted fresh SS result and revealing the Completed Results section. This interpretation needs approval; selecting Recommendation instead is equally small and may better match the user's meaning of “results tab.” Do not add another navigation layer.

Hook selection after successful `render()` inside `runAnalysis()`'s existing eligible success callback, and after the existing success policy confirms an accepted result. Do not attach selection to worker completion, state IDLE, parameter save completion or snapshot write completion. Leave the job controller's acceptance gates unchanged.

Capture plan/view/result identity and a navigation epoch at admission. If the user changes analysis workspace or result tabs after admission, completion may update retained data but must not override that later manual selection. No requestFocus. Rejected, canceled, failed, all-unsuccessful or obsolete results do not navigate. Any deferred reveal must recheck the same ownership/selection guard.

For historical SS loads, recommend retaining current opening behavior (Recommendation on a new workspace; no forced selection on restoration). Passive restoration must not imitate a new financial completion or invalidate Quick. A later phase can change that explicit default if desired.

### Exhaustive and Weighted

Current `IntegratedAnalysisResultTabs(ranked, heatMap)` starts Ranked Strategies. Fresh render does not select Heat Map. Historical attachment selects Heat Map only for a saved `tab == "heat-map"`; otherwise it retains the current selection. Saved preferences are not a reliable continuous record of manual tab intent: fresh Exhaustive preparation captures the tab before the run.

Proposed policy:

| Event | Selection/reveal rule |
|---|---|
| First display of a fresh accepted result | Heat Map, unless a later manual navigation epoch superseded the run's request |
| First display of compatible historical result, including Outdated | Heat Map, unless a later manual choice superseded the load request |
| Switching to a different restored result identity | Apply initial Heat Map rule once for that identity |
| Revisit same result/workspace in same dialog | Preserve user's current tab, metric, cell and ranked selection |
| Reopen dialog or switch plan | New view/binding starts with Heat Map when its completed integrated result attaches |
| Start/cancel/fail replacement | Keep prior result and tab; no premature reset |
| Input edits, currency reassessment, parameter writes, retry/save metadata | Never select a tab or scroll |
| Full-strategy detail action | Preserve existing intentional transition to Ranked Strategies |

Recommend new initial Heat Map default take precedence over old saved tab preferences, while retaining valid metric/strategy preferences. This requires explicit approval. Never rewrite old snapshot bytes/preferences just to establish a UI default. Alternative: honor explicit saved Ranked tab, but then historical opening will not consistently satisfy the requested default.

Implement an explicit per-result initialization method rather than calling select(heatMap) on every render/status refresh. Guard `IntegratedAnalysisResultTabs`' existing deferred reveal: currently the queued callback does not recheck whether the user switched back before it executes. Avoid delayed scrolling after manual navigation, closure or plan change. Preserve natural-height outer scrolling and existing result tables.

## 5. PDF design and reuse

### Existing reusable infrastructure and constraints

Existing PDFBox exporter uses landscape 792×612-point pages, 36-point margins, shared palette, wrapped/paginated tables and continuation sections. It writes through a temporary sibling file, then replaces the destination; do not describe this report-output path as the snapshot repository's stronger transactional protocol. Existing title handling already supports individual “Social Security Only.”

`IntegratedAnalyzerReport` contains immutable report-facing values and a sealed Visualization with HeatMap and Individual alternatives. Exporter currently branches Individual versus heat map. Add explicitly typed SS-grid and table-only variants (or an equally explicit mode-neutral renderer contract) with exhaustive dispatch. Preserve existing report output through regression tests. Do not misuse `heatMap()` casts for Quick or use null visualization. No new dependency or PDF framework is warranted.

Use pure report adapters and shared pagination/drawing helpers. Capture immutable source context at request preparation, accepted completion time at eligibility acceptance, and user selection plus export time at export. UI collection/selection access belongs on FX; PDF rendering/file I/O remains background work. Large pure row formatting can be offloaded after a small detached capture. Never read live plan fields later on the export worker.

Exact BigDecimal values/scales must be checked at source-model boundaries; formatted report strings are intentionally rounded for display and are insufficient as the financial fidelity oracle.

### A. Couple Social Security Only

Proposed `SocialSecurityOnlyReportAdapter` and a small immutable completed SS report session retain the original context/result, accepted start/completion timestamps and prerequisite provenance. Current `RunResult` carries context only through the callback; retaining the rendered assumption string alone is inadequate for a new exact report adapter. Historical context/result already exists in `CoupleSocialSecuritySnapshot` and `CoupleSocialSecuritySnapshotMapper`; reconstruct pure report context without optimizer/mortality work or a snapshot-format change.

Proposed pages:

1. SS claiming-analysis title; household; Current/Outdated; original run/completion and export times; Stage 1 grid, exact tier legend, optimal/retained and selected pair explanation.
2. Recommendation and selected details, explicitly separating Stage 1 retirement grid from Stage 2 complete-strategy expected PV/real/nominal values and survivor elections.
3. Paginated complete-strategy rankings in original rank/tie order; original elections and existing expected-value columns; original assumptions, mortality/valuation context, limitations and explanatory results.

Reuse color/selection source from the shared pure grid presentation for UI/PDF parity. Include original axes, all cells and all original ranked entries; do not export only visible table rows. Record unavailable selections honestly. No estate, tax or full-plan projection metric is introduced.

Compatible restored Current and Outdated results should export. Reuse historical notice/completion conventions of `WeightedResultPersistence.report()` through a narrow report decorator if needed. Incompatible data is not exportable. Invalid next-run inputs do not invalidate the frozen report's safety; distinguish Inputs Need Attention from the completed result. Pending/failed snapshot storage must not falsely imply durable saving, although a valid in-memory accepted result can still export.

### B. Couple Deterministic Quick Comparison

Quick remains memory-only across dialog/application lifetime; parameter configurations contain candidate count/prerequisite hashes, not restorable Quick results. PDF is available only while the accepted in-memory comparison and frozen report context remain owned by the same plan/view. Restart/reopen cannot manufacture a Quick PDF. Show that limitation explicitly.

`runIntegratedAnalysis()` captures isolated inputs and selected candidates, but currently stores only `integratedPresentation` after success. Add immutable report context at preparation: original financial assumptions, run start, current-plan baseline description, original compatible SS prerequisite context/fingerprint, candidate count, selected identities/original SS ranks and exact SS expected values. Capture completion time only after accepted success. Keep it with the accepted comparison, retaining it through canceled/failed reruns. Do not read today's prerequisite, baseline policy labels or live settings when exporting an older comparison.

Report content: title and frozen settings/provenance; persisted-current-plan baseline from the result; original ordered compared strategies and complete elections; supplied/unique/success/failure counts; original SS ranks/expected PV; integrated metrics and candidate-minus-baseline differences already available in result/details; structured failures/warnings and existing limitations. Current Quick UI includes lifetime SS, taxes, withdrawals, ending investable assets, after-tax heir value and estate difference, plus additional metrics in detail. Paginate wide data into readable election and financial tables rather than shrinking every column onto one page.

Quick is **SS-ranked candidate comparison**, not an integrated optimization ranking or new recommendation. Preserve caller order/ties, do not sort by estate or invent competition ranks. No heat map. Original SS candidate precision and first-seen deduplication remain unchanged. Include full available existing detail metrics without calculating new ones.

Recommend allow Outdated in-memory Quick PDF with a historical notice and original assumptions. Export eligibility is separate from readiness to run again: a stale prerequisite must still prevent a new Quick run, but need not destroy safe historical export. Since Quick has no durable result metadata, implement a small in-memory currency/context assessment using existing captured input/prerequisite dependencies; do not claim persistence or add a new fingerprint contract. If those dependencies cannot establish safety, disable export with a precise reason instead of using current inputs.

## 6. Exact proposed change inventory

No files in this table are changed by this audit.

| Action | Files/resources |
|---|---|
| Modify UI composition, frozen accepted report sessions, export actions and guarded selection | `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java` |
| Extract shared grid/legend renderer; keep integrated wrapper semantics | `ui/socialsecurity/ClaimingStrategyHeatMapView.java`; proposed `ClaimingAgeGridView.java`, `ClaimingAgeGridPresentation.java`, `SocialSecurityClaimingGridAdapter.java` |
| Reuse/minimally extend styles and formatting | `src/main/resources/css/claiming-strategy-heat-map.css`, `ui/socialsecurity/ClaimingHeatMapFormatting.java`; palette thresholds unchanged |
| Add guarded per-result initial selection | `ui/socialsecurity/IntegratedAnalysisResultTabs.java`, `LongevityWeightedIntegratedView.java`; dialog historical attachment paths |
| Explicit report variants/rendering | `app/export/IntegratedAnalyzerReport.java`, `IntegratedAnalyzerPdfExporter.java`; `PdfReportSupport.java` only if shared pagination requires it |
| Pure new adapters and frozen session/context | proposed `ui/socialsecurity/SocialSecurityOnlyReportAdapter.java`, `QuickComparisonReportAdapter.java`, `QuickComparisonReportContext.java`; retain SS completed context in a small typed session |
| Historical decoration reuse only if necessary | `ui/socialsecurity/WeightedResultPersistence.java` or a scoped extracted report decorator, preserving compatibility/status semantics |

No expected changes to domain engines, request semantics, repository/codec DTOs, accepted parameter schema/coordinator, AnalysisFingerprintService, Monte Carlo code, dependencies or financial goldens. Existing integrated/individual adapters should remain unchanged unless an explicit report-contract compilation adjustment is required and regression-tested.

## 7. Testing and regression design

Extend existing tests rather than duplicate financial suites:

- `ClaimingStrategyHeatMapModelTest`, `ClaimingStrategyHeatMapViewTest`, `DeterministicHeatMapAdapterTest`: shared renderer preserves existing integrated metrics, unrounded thresholds, exact ties, nonpositive/unavailable handling, age orientation, selected/focus styling, tooltips and accessibility. Add SS adapter tests comparing every Stage 1 cell/axis to original outputs; use cases where Stage 1 and Stage 2 optima differ.
- `SocialSecurityStrategyAnalyzerDialogStateTest`, `SocialSecurityAnalyzerJobControllerTest`, `AnalyzerDirectNavigationTest`: successful policy-accepted SS selects intended tab; rejected/canceled/fatal/obsolete/all-unsuccessful do not; manual selection after admission wins. Delay FX reveal/load callbacks deliberately; test closure, plan switching and user navigation during work.
- `CoupleSocialSecurityPersistenceTest`, `WeightedRestorationTest`, `ExhaustivePersistenceTest`: original/restored values and frozen context unchanged; new defaults once per result; repeated metadata callbacks do not reset manual tabs/metric/cell. Preserve passive SS prerequisite publication without Quick execution.
- New focused `SocialSecurityOnlyPdfReportTest` and `QuickComparisonPdfReportTest`: exact raw result/context equality, then immutable report/text/image comparisons; all ranks/ties/cells, optional/failure data, large values, long names, pagination and original timestamps. Throwing financial factories prove capture/restore/export/selection never calculate. Quick export must use original prerequisite when a newer SS result exists.
- Extend `IntegratedAnalyzerPdfStateTest`, `IntegratedAnalyzerPdfReportTest`, `ExhaustiveHistoricalPdfFidelityTest`, `IndividualReportTest`: existing page geometry, heat-map colors, historical assumptions, warnings, large-value wrapping and individual output remain unchanged after renderer dispatch extension.
- `SocialSecurityParameterPersistenceTest` and existing Monte Carlo parameter/fingerprint/lifecycle tests: no accepted configuration writes from tab/cell selection or export, no dirty/revision/cache changes, all fifteen configurations and numeric compatibility intact. Test Save As/binding guards without altering schemas.
- Layout at actual JavaFX logical 1920×1080, 1366×768, 1180×820, 900×650 and narrower feasible preview. Assert visible age labels/cells/legend/actions, no configuration clipping, natural outer scrolling, bounded tables and keyboard traversal. Render PDFs and inspect real page images. Do not claim Windows DPI/Narrator/high-contrast verification from automated logical-size tests.

Existing tests inspected are evidence of current contracts, not proof of the proposed enhancements. New tests above are recommendations only.

## 8. Recommended implementation stages and review gates

1. **Grid-only consistency:** extract renderer, add pure SS Stage 1 adapter, preserve all objective-specific adapters. Approve orientation/default cell metric first. Gate: exact-value/selection/layout tests and UI previews.
2. **Navigation:** retain SS tab reference; add small per-result/epoch selection policy to both integrated workspaces and SS callback. Approve SS target tab and saved-tab precedence first. Gate: canceled/stale/manual-choice/restore races pass.
3. **SS PDF:** frozen accepted report session, SS-grid report variant, background exporter and Current/Outdated readiness. Gate: fresh/restored/historical financial, text, raster and no-recalculation tests.
4. **Quick PDF:** immutable prerequisite/financial context capture and explicit table-only report variant; no result persistence. Gate: original caller order/partial failure/provenance/historical report fidelity and prerequisite safety.
5. **Acceptance:** focused tests then full Maven suite, rendered JavaFX/PDF inspection and native checklist. Existing fifteen parameter configurations, Phase 9 snapshots, all individual exporters and Monte Carlo regressions remain release gates. No automatic staging/commit/push.

## 9. Open decisions requiring design approval

1. Should fresh SS completion select **Claiming Grid** (recommended) or Recommendation? No Results tab exists today.
2. Approve rotating SS axes to match primary-horizontal integrated maps? Labels and tests must make this intentional.
3. Keep SS currency cell text by default (recommended), with tier percentages in tooltip/details, or add a PV/percentage display selector? No new financial metric is needed.
4. Approve Heat Map as the first-display default even when an older snapshot recorded Ranked? Recommendation: yes, preserving manual choices made in the current view after admission/load began and retaining valid metric/strategy preferences.
5. Approve Outdated Quick PDF while its frozen result/context remain safely available in memory, with an explicit memory-only notice? Recommendation: yes; no Quick result persistence.

No financial-model or dependency approval is needed for this design. A broader persistence/UI framework is unnecessary.

## 10. Native Windows acceptance checklist (future implementation)

1. Run couple SS; verify grid colors/legend, exact-tie stars, retained markers, axis orientation, tooltips and detail values; compare Recommendation/Top Strategies without conflating Stage 1/2.
2. Complete SS while remaining in its workspace; verify approved result tab/reveal. Repeat while manually changing result tab/workspace; later manual navigation must win. Cancel/fail a rerun; no unexpected selection.
3. Complete Exhaustive and Weighted; Heat Map should be initial result view. Manually select Ranked, edit inputs, retry parameter save and revisit workspace; manual choice remains.
4. Restart with saved Current and Outdated results; check approved initial Heat Map behavior, selected metrics/strategies and no recalculation. Switch plans during load; no wrong-plan attachment/reveal.
5. Export fresh/restored/Outdated SS PDF; compare frozen assumptions, completion time, grid colors, ranks and election details. Incompatible result cannot export valid financial data.
6. Run Quick with distinctive count/current SS prerequisite; export full comparison and failures where available. Change current settings/SS result; historical PDF must keep original assumptions/provenance, while a new run still enforces prerequisite readiness. Restart and verify no claim that Quick financial results are restored.
7. Inspect print/PDF pagination at long names and large values; existing Exhaustive/Weighted/individual reports unchanged. Check all representative window sizes, keyboard focus and logical scrolling.
8. Verify accepted parameters, unsaved financial edits, Save As independence, historical result status and Monte Carlo behavior remain unchanged. No parameter persistence should occur merely from selecting tabs or exporting.

## 11. Executed verification and final Git state

Executed IntelliJ bundled Maven with the required project-local `.codex-m2/repository` and `-DargLine=-Xmx4g`, selecting these existing test classes:

| Test class | Run | Passed | Skipped |
|---|---:|---:|---:|
| AnalyzerDirectNavigationTest | 12 | 11 | 1 |
| ClaimingStrategyHeatMapModelTest | 10 | 10 | 0 |
| ClaimingStrategyHeatMapViewTest | 28 | 28 | 0 |
| CoupleSocialSecurityPersistenceTest | 10 | 10 | 0 |
| DeterministicHeatMapAdapterTest | 6 | 6 | 0 |
| ExhaustiveHistoricalPdfFidelityTest | 4 | 4 | 0 |
| IntegratedAnalyzerPdfReportTest | 6 | 6 | 0 |
| IntegratedAnalyzerPdfStateTest | 4 | 4 | 0 |
| QuickComparisonPrecisionTest | 1 | 1 | 0 |
| SocialSecurityAnalyzerJobControllerTest | 40 | 40 | 0 |
| SocialSecurityParameterPersistenceTest | 28 | 28 | 0 |
| SocialSecurityStrategyAnalyzerDialogStateTest | 32 | 32 | 0 |
| WeightedRestorationTest | 14 | 14 | 0 |
| **Total** | **195** | **194** | **1** |

Zero failures/errors; BUILD SUCCESS; Maven wall time **51.121 seconds**. The one skip is the existing opt-in navigation preview: `analyzer.navigation.preview` was not set. No tests were disabled or assertions weakened. Expected injected persistence-failure diagnostics appear in the log; they are not test errors. Log: `target/phase11a-focused.log` (generated, ignored, not a release file).

The historical PDF tests regenerate existing comparison PDFs/page PNGs under `target/gate4-exhaustive-pdf/` and perform automated text/raster comparisons. This audit does not claim independent human inspection of those images, new-enhancement previews, native Windows acceptance, DPI or assistive technology checks. No full Maven suite was requested for this audit or executed; Monte Carlo and the remaining broad regressions are proposed implementation acceptance gates, not newly executed evidence.

`git diff --check` passed. Final `git status --short`:

```text
?? PHASE-11A-SOCIAL-SECURITY-UI-PDF-AUDIT.md
```

Tracked `git diff --stat` is empty. The sole added file is this audit report. No production/test/resource changes, staged entries, commits, pushes, resets or discards. Stop here for design approval; no implementation has begun.
