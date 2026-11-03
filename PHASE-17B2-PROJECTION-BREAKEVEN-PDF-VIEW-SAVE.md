# Phase 17B2 — Projection and Break-Even PDF View/Save Integration

## Executive summary

Projection PDF export from Results Summary and Break-Even PDF export now use the accepted Phase 17B1 `PdfExportWorkflowCoordinator<R>`. Each offers View PDF, Save PDF, and Cancel. View writes a verified complete PDF to the managed preview store and invokes the existing desktop opener; Save retains the report-specific native file chooser, suggested name, extension handling, and exporter; Cancel performs no export. Rendering and file I/O run on virtual worker threads, while capture and UI state remain on the JavaFX thread.

The Projection renderer receives detached copies of the selected plan and accepted annual projection, plus immutable presentation inputs. The Break-Even report is captured lazily from the already completed comparison/context/insight and reused for rendering. Neither path runs a projection or Break-Even analysis. The PDF generators and their content were not changed.

## Preliminary export inventory

| Export surface | Entry point | Generator and input | Old Save behavior | Phase 17B2 behavior |
|---|---|---|---|---|
| Projection | Results Summary → Export → PDF → `ResultsSummaryView.exportProjectionPdf()` | `ProjectionPdfExporter`; active accepted projection, plan assumptions, non-investible asset projections, prepared `ProjectionPdfReport` | `Export Retirement Projection`, `RetirementProjection.pdf`, `PDF Files (*.pdf)` filter, `.pdf` appended when omitted, direct-path renderer | Shared View/Save/Cancel choice; same Save chooser behavior; frozen detached inputs rendered off-thread |
| Break-Even | Break-Even Analysis → Export PDF → `BreakEvenAnalysisDialog.choosePdf()` | `BreakEvenPdfExporter`; completed `BreakEvenPdfReport` with analysis, comparison context, and insight | `Export Break-Even Analysis PDF`, `Break-Even-Analysis.pdf`, `PDF report` filter, `.pdf` appended when omitted, direct-path renderer | Shared View/Save/Cancel choice; same Save chooser behavior; immutable report rendered off-thread |

The Results Summary CSV/PDF format prompt remains unchanged. Choosing CSV still follows the existing CSV export path; choosing PDF proceeds to the shared choice dialog. Social Security, Monte Carlo, and all other export surfaces remain unchanged.

## Shared coordinator integration

Both workflows use `PdfExportWorkflowFactory`, which configures `PdfExportDialogs.choicePrompt()`, the managed `PdfPreviewStore`, `DesktopPdfOpener`, a per-request virtual-thread worker, and `Platform.runLater` for completion. The factory is newly added and does not replace or alter the Phase 17B1 coordinator.

The common coordinator enforces these behaviors:

- Readiness is checked before showing the choice and again before destination selection/capture.
- Cancel exits before report capture, chooser invocation, rendering, or preview creation.
- View bypasses the Save chooser; Save invokes the export-specific chooser adapter.
- One captured report is passed to the worker renderer.
- The renderer creates a new exporter instance for the job and writes off the FX thread.
- The View path passes the preview store's validated, published PDF to the opener only after rendering finishes. Completed previews are retained.
- Busy state rejects repeat starts and each export action is disabled with an indeterminate progress graphic while the job runs.
- Viewer-launch failures are distinct from render/save failures and report the retained preview path.

No external viewer is launched in automated tests. The production `DesktopPdfOpener` remains responsible for OS launch capability checks and does not use a shell fallback.

## Projection identity and frozen report inputs

The Projection request is bound to the exact `currentPlan` and `currentProjection` references that the Summary displayed at activation. Its existing availability requirement remains a nonempty selected projection; the request also verifies that the Summary still points at the controller's active plan after the choice and Save chooser return.

On the FX thread, capture:

1. A detached plan copy via the existing `RetirementPlanScenarioCopyService`.
2. A detached annual result via `RetainedProjectionSnapshot.capture(...).restoreProjection()`, which validates exact reconstruction.
3. An immutable copy of the non-investible projection list.
4. The already prepared `ProjectionPdfReport` containing chart, tax, estate, and baseline-comparison presentation values.

The background exporter receives only this frozen capture. It uses a fresh `ProjectionPdfExporter` instance, so its mutable internal PDF document state is confined to that worker. A test compares text and page counts from ordinary and detached-input exports; both are identical. This verifies that the detached capture retains the existing report content without another projection run.

If the active Summary selection changes while rendering, the generated file still represents the projection selected when the user accepted the export. The worker is not redirected to a newer result. The Save success alert is shown only if the same plan/projection remains selected and the controller confirms that the current projection inputs still match; stale success messaging is suppressed. Render and viewer failures remain reportable because their error still applies to the captured export, even if the view selection changed.

The Projection annual-result model itself does not carry a public UUID; the active result object identity is therefore used as the view-selection guard. This does not alter controller projection UUIDs, historical associations, or baseline identity.

## Break-Even identity and capture

`BreakEvenAnalysisDialog` retains the completed analysis, context, and insight supplied when it is opened. These values are immutable records with copied collections. `BreakEvenPdfReport` is now prepared lazily by `preparePdfReport()` when the accepted choice reaches capture (or when existing direct callers request it). Thus Cancel does not construct the report. Both View and Save pass that same frozen report to `BreakEvenPdfExporter`; no comparison, crossover, sustained break-even, metric, or horizon calculation is repeated.

The dialog’s PDF continues to use the exporter’s existing report metric/content policy, independent of interactive chart selection. The dialog owner is passed to the choice dialog, Save chooser, and error presentation. Closing the dialog after capture does not invalidate or delete an otherwise successful preview; failures remain visible to the application owner. Current/Baseline swapping cannot change the already captured Break-Even result.

## Save compatibility

Projection Save retains the title, initial name, extension filter, extension normalization, and native `FileChooser` overwrite behavior. Its chooser still has no explicitly selected initial directory, matching the old path. Break-Even Save retains its own title, suggested name, `PDF report` filter, extension normalization, and native chooser behavior. The selected path is not replaced or written until the user confirms Save and the coordinator has captured the report.

For either export, cancelling the native chooser exits before capture/rendering and does not create a preview. The existing PDF exporters still write directly to their selected destination; no previously safe-write strategy was changed because these two exporters used direct-path writing before integration. Report generators, titles, page layout, charts, tables, financial values, and metadata conventions are unchanged.

## Files changed

### Production

- `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java` — Projection choice workflow, frozen capture, busy feedback, completion/error handling, and legacy Save chooser adapter.
- `src/main/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenAnalysisDialog.java` — Break-Even choice workflow, lazy immutable report capture, busy feedback, and legacy Save chooser adapter.
- `src/main/java/com/daviddunn/retirementplanner/ui/pdf/PdfExportWorkflowFactory.java` — New production-adapter factory for per-surface coordinators.

### Tests and visual previews

- `src/test/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenPdfWorkflowIntegrationTest.java` — New UI-handler tests using a fake opener and temporary store for Cancel, View, and Save.
- `src/test/java/com/daviddunn/retirementplanner/ui/charts/ProjectionPdfExportTest.java` — Detached Projection capture content-equivalence regression.
- `src/test/java/com/daviddunn/retirementplanner/ui/pdf/PdfExportChoiceDialogPreviewTest.java` — Projection and Break-Even owner-context previews and viewer-error previews in both themes.

The pre-existing Phase 17B1 changes in `MainApplication.java`, `application.css`, the shared PDF infrastructure/tests, and the Phase 17A/17B1 reports were preserved. The B2 implementation did not modify financial model code, PDF exporters, application navigation, persistence, or analysis execution.

## Visual verification

Actual JavaFX snapshots were generated and inspected:

- `target/phase17b2-pdf-export-choice/projection-light.png`
- `target/phase17b2-pdf-export-choice/projection-dark.png`
- `target/phase17b2-pdf-export-choice/break-even-light.png`
- `target/phase17b2-pdf-export-choice/break-even-dark.png`
- `target/phase17b2-pdf-export-choice/projection-save-focused-light.png`
- `target/phase17b2-pdf-export-choice/projection-save-focused-dark.png`
- `target/phase17b2-pdf-export-choice/break-even-save-focused-light.png`
- `target/phase17b2-pdf-export-choice/break-even-save-focused-dark.png`
- Matching `projection-cancel-focused-*` and `break-even-cancel-focused-*` previews.
- `target/phase17b2-pdf-export-choice/viewer-error-light.png`
- `target/phase17b2-pdf-export-choice/viewer-error-dark.png`

The Projection and Break-Even owner contexts share the same compact choice dialog, with View as the default action and all three buttons visible. Save-focused and Cancel-focused snapshots show visible focus outlines in both themes. The viewer-error alert clearly distinguishes a failed OS launch and displays the retained preview path. Existing coordinator tests cover Cancel, chooser cancellation, capture count, View without chooser, Save, busy reentrancy, worker dispatch, viewer failure, and retained preview behavior. Automated tests used only a fake opener.

The progress state is an indeterminate inline `ProgressIndicator` on the existing Export button. No percentage or new cancellation policy was added. No native Windows DPI scale test or real PDF-viewer launch was performed in this environment.

## Test results

Focused verification used IntelliJ’s bundled Maven and the repository-local `.codex-m2\repository` as required:

- Broad focused run before adding the dedicated viewer-error screenshot: **18 tests, 0 failures, 0 errors, 0 skipped**.
- Final choice-dialog visual test rerun after adding focused Save/Cancel states: **3 tests, 0 failures, 0 errors, 0 skipped**.
- Existing Projection and Break-Even PDF generator suites, Phase 17B1 coordinator/store tests, and JavaFX choice/error previews all passed.
- `git diff --check`: passed.

Complete Maven regression on the final production and test code: **3,970 tests, 0 failures, 0 errors, 46 skipped; BUILD SUCCESS** (11m 06s). The 46 skips are existing opt-in benchmark/visual cases. The suite includes existing projection, historical-result, analyzer, financial-golden, and PDF-content regressions. No PDF or financial golden expectations were changed.

## Deferred exports and limitations

Social Security Integrated Analyzer, Monte Carlo, Monte Carlo comparison, CSV export, and all other PDF workflows remain on their prior behavior for a later authorized phase. The application does not embed or wait for the external viewer. `Desktop.open` confirms only a launch request; it does not prove that the viewer has fully loaded the document.

Native Windows acceptance should verify the real default PDF association, missing-association messaging, Save chooser filename/extension and overwrite behavior, and closing or changing projections while an export is rendering. Windows scaling at 100%, 125%, and 150% was not tested here.

## Git status and preservation

The branch remains `main`. Before B2 edits, the working tree contained the uncommitted Phase 17B1 changes and untracked Phase 17A/17B1 reports and shared PDF source/tests. Those files remain present and were not reset, cleaned, staged, or committed. B2 added the files listed above. No changes were staged, committed, or pushed.
