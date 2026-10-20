# Phase 9D-2 Gate 4 — Historical PDF fidelity

## Result and boundary

Verified original, cross-session-restored Current, and restored Outdated Deterministic Exhaustive exports. All four supported heat-map metrics preserve financial source values, ranks/ties, baseline, selection, frozen assumptions and colors. Original and restored Current PDFs have identical extracted text and **pixel-identical pages** when their export timestamps are held equal for comparison. Outdated exports preserve the same financial report data while adding the established historical notice and original completion time.

Two narrowly scoped presentation defects were corrected: omitted retained projection warnings and overflowing unusually large heat-map amounts. No financial calculation, snapshot representation/limit, persistence lifecycle, ranking, dependency, golden or unrelated analysis mode changed.

The user confirmed native Gate 3B-2 acceptance: the original 44.69 MB save failure is resolved and real Exhaustive results restore across application restarts. This report records that confirmation; the Gate 4 automated fixtures use synthetic data, not the user's plan or snapshot. Gate 5 has not been started.

## Preparation and exact files changed

Read AGENTS.md and all current Phase 9D-2 reports. Inspected the actual dialog capture/readiness path, `WeightedResultPersistence.report`, `ExhaustiveResultSnapshots`, detached projection reconstruction, immutable report/context, deterministic report adapter, PDFBox exporter, palette and existing PDF/state/persistence tests.

Initial branch `main`, HEAD `4fddd4ee1eaebc8354b009b77a381459495d665c`. Initial tree contained five modified tracked files and twenty-two untracked files from previous gates, with an empty index. Preserved all of them.

This gate changes only:

- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IntegratedAnalyzerReportAdapter.java`: include baseline warnings and retained selected/highest-strategy warnings in deterministic results sections.
- `src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerPdfExporter.java`: fit unusually long cell values inside the existing heat-map geometry.
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveHistoricalPdfFidelityTest.java` (new): four focused historical/visual/export tests.
- `src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustivePdfFidelitySupport.java` (new): small bridge to reuse the independent Gate 1 domain-field/getter/account-identity oracle across test packages.
- This report.

## Original versus restored comparison

The principal fixture runs a real production deterministic search through the dialog's admitted worker/completion path, saves it using the production application store and optimized repository, closes that dialog/store, constructs new controller/store/view instances and reopens the saved plan. It calculates **324 complete strategies**: all 81 retirement-age pairs with two supported survivor candidates per owner, retaining the normal twenty detailed projections. It includes real exact ties and competition ranks. This is a bounded Gate 4 fixture, not another 5,184-candidate capacity run.

The search is invoked once in that test. All subsequent restore, selection and export operations reuse its accepted result. The restored view's calculation factories throw if called, and financial job generation remains zero. The explicit original calculation invocation count remains one after every restored and Outdated export.

For each metric, tests select the same second retained outcome through the existing ranked table, synchronize the corresponding heat-map age pair and capture the existing PDF report:

| Metric | Fresh vs restored Current | Restored Outdated |
|---|---|---|
| Percent of Optimal | Exact report data, text and all-page raster match | Same financial data, historical notice |
| Difference from Optimal | Exact report data, text and all-page raster match | Same financial data, historical notice |
| Difference from Current Plan | Exact report data, text and all-page raster match | Same financial data, historical notice |
| Future-Dollar Estate | Exact report data, text and all-page raster match | Same financial data, historical notice |

Expected PV After-Tax Estate is a weighted metric, not a fifth deterministic metric. No unsupported metric was introduced.

The immutable report's `exportedAt` is normalized to one fixed instant **only in the comparison test**, avoiding expected differences in PDF header text. Actual run-start and completion times remain original. Production export time remains the actual export time. PDF bytes are not required to match because PDF document IDs/metadata may differ; visible page pixels and extracted text are compared instead.

## Exact financial-data fidelity

Before comparing formatted PDF values, the independent oracle compares original and restored calculation data:

- Every generated occurrence, complete election, generation order, success/failure, competition rank and successful ranking order.
- All fourteen `ProjectionMetrics` fields and candidate-minus-baseline fields using scale-sensitive `BigDecimal.equals`, without tolerances.
- Original baseline and every retained detailed projection, including nested annual data, nullable fields, account ownership/identity, source attribution, taxes, Social Security results, flags and warnings.
- All annual public zero-argument financial getters, using the pre-existing Gate 1 field/getter oracle.
- Preserved detached account registries. Original and restored accounts are different mutable objects; their within-projection references remain correct. No live-plan account matching by name is used.

The PDF report DTO intentionally contains formatted strings, not exact monetary BigDecimals. Its existing whole-dollar/display formatting is preserved. Exact scale/value fidelity is proven at the source graph; complete formatted report data is then compared separately. This avoids claiming that a displayed rounded monetary string itself carries the original calculation scale.

The existing deterministic PDF reports aggregate financial metrics, election tables, selection details, warnings, assumptions and whether projection details were retained. **It does not print annual account/projection tables.** No annual appendix was added in this gate. The complete retained annual graphs remain available and are verified exactly before report construction; no information is discarded from the snapshot or restored model merely because the existing PDF does not display every annual getter.

Exporters consume immutable captured report data. Source inspection confirms they do not call `ProjectionEngine`, the SS optimizer, search, current projection retrieval or account-name matching. Existing gap/percentage/display computations operate only on frozen result metrics, as before; they do not rerun financial analysis.

## Current, Outdated and incompatible behavior

Current restored results export normally. Tests then change investment return, general inflation, healthcare inflation, plan SS COLA and the primary person's name. The result becomes Outdated; all four reports retain original names, economic assumptions, source values, baseline, rankings, selection and heat map. Only status/context identifies the historical result. The original completion timestamp remains visible, separately from run start and export time.

A separate available-reference case preserves the exact frozen SS cross-reference record, including its `123456.0012300` test PV scale, original matching strategy, estate/rank/gap and stored provenance. Editing the live SS estimate to `7777.7777` with valuation year 2044 does not replace that reference or the report assumptions. The principal fresh-job case separately verifies the explicit original-unavailable SS state is retained, rather than substituted with a later recommendation. No SS analysis is automatically executed.

An unsupported saved projection algorithm version is classified Incompatible or Unavailable. Its PDF button is disabled, report capture is refused and saved bytes remain intact. The test does not export unsafe financial data to make a report appear complete.

The primary test compares plan-file and snapshot-file bytes before/after restored exports and Outdated exports; both remain unchanged. Candidate selection uses retained data and does not start a job. The intentional current-plan edits are in memory and are not silently saved by exporting.

## Defects and corrections

### 1. Missing projection warnings

The adapter previously omitted `IntegratedSocialSecurityStrategyResult.warnings()` from deterministic PDFs. A test using the actual engine's baseline warning failed before correction:

> Current plan survivor behavior was evaluated using its configured deterministic survivor policy.

The adapter now adds a Current Plan Projection Warnings section when nonempty, and includes retained detail warnings in existing highest/selected-strategy detail text. Warnings are neither inferred nor rewritten. Existing section rendering and pagination are reused. Financial values and rankings are unchanged.

### 2. Large heat-map amounts overflowed cells

Rendering a deliberate large-display fixture (`$123,456,789,012,345.67`) showed amounts wrapping into three lines and spilling below their cell backgrounds. The renderer now reduces value text from 7.5 points in 0.25-point steps, down to 5.5 points if needed, until its wrapped line block fits the existing vertical budget. Ordinary values retain exactly their previous size/baseline. Grid dimensions, palette, stars, borders, metrics, formatting and content are unchanged.

The fix is in the existing shared couple heat-map renderer; normal weighted output remains unchanged and weighted PDF/restoration regression tests were included. This is text fitting, not a redesign or change to weighted report contents. Pathological amounts longer than the minimum-font budget are not certified by this bounded large-value fixture; broader stress acceptance remains Gate 5.

## Rendered PDF evidence and inspection

Actual PDFs were exported through `IntegratedAnalyzerPdfExporter`, rendered with PDFBox at 96 DPI and inspected as images. Original-versus-restored Current pages are additionally compared pixel-by-pixel at 72 DPI, for every page and all four metrics. Every palette color is checked in rendered first pages. No screenshot substitution for vector report content is introduced.

Artifacts are ignored under `target/gate4-exhaustive-pdf/`:

- `original-<metric>.pdf`, `restored-<metric>.pdf`, `outdated-<metric>.pdf`: twelve PDFs, nine pages each for the actual calculated fixture.
- `<state>-<metric>-page-<n>.png`: first/assumptions/ranking/last-page captures; every page is captured for Future-Dollar Estate.
- `large-values-long-tables.pdf`: twenty-one-page rendering-only stress report, with 81 separately displayed election/outcome rows and synthetic large amounts.
- `large-values-long-tables-page-{1,3,5,11,21}.png`: heat map, repeated election headers, financial table continuation and interpretation/last page.

Inspected representative Current and Outdated heat maps, frozen assumptions, competition-rank/group tables, all fourteen baseline metrics, warning section, selected-strategy details, interpretation notes, long table continuation headers, page breaks and fitted large values. Representative files include `restored-future_dollar_estate-page-{1,2,5,6,7,9}.png`, `outdated-future_dollar_estate-page-1.png`, `restored-difference_from_current-page-3.png` and the large-value captures above.

Findings after correction: complete 9-by-9 maps; original colors/optimal and selection markers; readable original assumptions and Outdated identification; preserved ranks/group counts; repeated table headers; no observed clipping or footer overlap in inspected pages. The large-value table wraps within the existing columns and grows page count naturally. No native print/printer, Windows DPI, Narrator or high-contrast certification is claimed.

## Tests executed

Used IntelliJ bundled Maven with the required project-local repository:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-DargLine=-Xmx4g' '-Dtest=ExhaustiveHistoricalPdfFidelityTest,IntegratedAnalyzerPdfReportTest,IntegratedAnalyzerPdfStateTest,ExhaustivePersistenceTest,LegacyWeightedSnapshotCompatibilityTest,ProductionExhaustiveInterningTest,RetainedProjectionCodecTest,IndividualReportTest,WeightedRestorationTest' test
```

| Run | Result |
|---|---|
| Pre-change PDF/state/Exhaustive lifecycle/frozen weighted baseline | 23 passed; zero failures/errors/skips |
| Warning omission proof, before correction | One expected failing regression assertion; actual baseline warning missing |
| Expanded focused regression before text fitting | 71 passed; zero failures/errors/skips |
| Final focused regression after both production corrections | **90 passed; zero failures/errors/skips**, BUILD SUCCESS, 36.071 seconds |
| Final regenerated PDF evidence, PDF/state and frozen weighted checks | **16 passed; zero failures/errors/skips**, BUILD SUCCESS, 19.422 seconds |

Counts overlap and are not summed as distinct tests. The four new tests cover full historical report/raster comparison, actual baseline warnings, frozen available SS/incompatible blocking, and long-table/large-value fitting. Existing production interning tests include legacy Exhaustive reading; both frozen weighted snapshots pass unchanged. Individual/weighted tests check that the shared renderer correction does not disrupt existing PDF contracts.

An initial test oracle compared the whole application result record using its identity-sensitive `Projection` equality. It was corrected to invoke the independent field/getter oracle for baseline/retained projections and explicit entry/rank/metric comparisons, matching Gate 1's established pattern. No financial assertion was weakened. A test-only call to a nonexistent heat-map DTO accessor was corrected during compilation. No production financial behavior changed to satisfy either issue.

Logs: ignored `target/9d2-gate4-baseline.log`, `target/9d2-gate4-warning-proof.log`, `target/9d2-gate4-verified.log`, `target/9d2-gate4-rendered-final.log`. No golden was changed or failing test disabled. No full Maven suite was run in Gate 4; Gate 5 final regression acceptance remains separately authorized work.

## Remaining Gate 5 work

- Run final established full regression suite after review of these presentation corrections.
- Final native PDF inspection with the user's restored full-universe result, Current and Outdated, including the desired retained selection and available historical SS reference if present.
- Broader extreme-magnitude/long-name/content stress checks, native print handling and final acceptance inventory as appropriate.
- Preserve prior real snapshot/capacity acceptance evidence; no need to repeat an expensive analysis merely to export a PDF.

Stop after Gate 4. No Quick persistence, remaining individual persistence, storage-limit change, interning/schema change, financial algorithm, mortality/tax/SS calculation, golden update or dependency change was undertaken.

## Git verification

`git diff --check` passed; only line-ending notices were printed. Seven modified tracked files and twenty-five untracked files in the final tree include preserved prior-gate work. Index remains empty. Generated PDFs/PNGs/logs are under ignored `target/`. No staging, commits, pushes, resets or discarded changes.

Complete final working-tree inventory follows.

```text
 M src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerPdfExporter.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java
 M src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java
 M src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IntegratedAnalyzerReportAdapter.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedResultPersistence.java
?? PHASE-9D-2-EXHAUSTIVE-PROJECTION-CODEC.md
?? PHASE-9D-2-GATE-2-EXHAUSTIVE-SNAPSHOT.md
?? PHASE-9D-2-GATE-3-EXHAUSTIVE-INTEGRATION.md
?? PHASE-9D-2-GATE-3A-CAPACITY-INVESTIGATION.md
?? PHASE-9D-2-GATE-3B-LOSSLESS-INTERING.md
?? PHASE-9D-2-GATE-3B1-REALISTIC-CAPACITY.md
?? PHASE-9D-2-GATE-3B2-PRODUCTION-INTERNING.md
?? PHASE-9D-2-GATE-4-PDF-FIDELITY.md
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ExhaustiveSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotSizeDiagnostics.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/ExhaustiveSnapshotInterning.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/RetainedProjectionCodec.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveResultSnapshots.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveCapacityInvestigationTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveInterningAuditTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustivePdfFidelitySupport.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveSnapshotTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ProductionExhaustiveInterningTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/RealisticExhaustiveCapacityProofTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/RetainedProjectionCodecTest.java
?? src/test/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotSizeDiagnosticsTest.java
?? src/test/java/com/daviddunn/retirementplanner/persistence/ExhaustiveSnapshotInterningTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveHistoricalPdfFidelityTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java
```
