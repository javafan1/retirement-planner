# Phase 17A — PDF View/Save Workflow Architecture Audit

## Executive summary

Aledrix has four report-generation services and several distinct JavaFX export paths. Every user-facing PDF action currently leads to a Save File chooser; none opens a PDF in the system viewer. Projection and Break-Even exports render synchronously on the JavaFX thread. Monte Carlo and Social Security exports use background tasks, while analyzer-history export uses the analysis-results background executor. The latter paths also have different readiness, stale-result, progress, error, filename-extension, and overwrite behavior.

The report generators accept structured report data and a `Path`; they do not own UI prompts or open files. Monte Carlo and Integrated Analyzer use temporary sibling files for safe final-destination replacement and delete those intermediates immediately. Those files are not viewer previews and are unsuitable for the requested View PDF action.

Recommend one JavaFX workflow coordinator with injected report capture, Save chooser, renderer, and OS opener; a separate app-managed preview-file service; and preservation of each current report adapter and PDF exporter. A choice must precede report capture/rendering. Cancel then performs no PDF rendering, chooser, or file creation. Save preserves each workflow's current filename and chooser semantics. View writes a unique complete PDF to an application-owned per-user directory and opens it with the OS default handler. Because Aledrix cannot know when an external viewer releases a file, preview files should remain after viewer launch and be removed only by conservative later cleanup.

No production code was changed in this audit. The initial working tree was clean. Focused existing PDF tests passed, and `git diff --check` passed after adding this report.

## Inventory of PDF export entry points

The inventory below separates UI entry points from reusable generators. Filenames are the code's suggested names; a user can choose another filename through the native Save dialog.

| UI entry point and owner | Report / generator | Current Save chooser and suggested filename | Generation and lifecycle | Cancellation, errors, overwrite, tests |
|---|---|---|---|---|
| Results Summary → Export → PDF; `ResultsSummaryView.exportProjectionPdf()` | Current accepted retirement projection, non-investible asset values, and prepared Summary report; `ProjectionPdfExporter` | `FileChooser`, title “Export Retirement Projection”; `RetirementProjection.pdf` | Synchronous on FX thread after chooser; writes directly to selected path. `.pdf` appended when omitted. | Chooser cancel returns without export. Empty projection gets an existing UI error. `IOException` becomes a generic export error (and is printed). No explicit second overwrite check after extension append. `ProjectionPdfExportTest` plus state-tax PDF tests cover generator output. |
| Break-Even Analysis dialog → Export PDF; `BreakEvenAnalysisDialog.choosePdf()` | Completed comparison, context, and insight; `BreakEvenPdfExporter` | `FileChooser`, title “Export Break-Even Analysis PDF”; `Break-Even-Analysis.pdf` | Synchronous on FX thread. The exporter writes directly to the selected destination. `.pdf` is appended if omitted. | Chooser cancel returns. `IOException` shows an error alert. No success alert or explicit confirmation if appending `.pdf` collides with an existing file. Covered by `BreakEvenPdfExportTest` and report tests in `IndividualReportTest`. |
| Monte Carlo Retirement Analysis → Export PDF; `MonteCarloAnalysisView` with shared `MonteCarloPdfExportAction` | Frozen completed fixed-horizon or longevity result; `MonteCarloPdfReportAdapter`, `MonteCarloPdfExporter` | `FileChooser`, title “Export Monte Carlo PDF”; filename from captured report | Report capture occurs before chooser for filename/readiness and again after choice; PDF rendering runs in a JavaFX `Task` on a virtual thread. Button is busy-disabled; success/error alerts are shown. | Cancel or stale/unready result returns without rendering. Appends `.pdf`; if that appended target exists, asks before replacing. Native chooser handles its normal overwrite case. Covered by `MonteCarloPdfExportTest`, numerical/historical fidelity tests and report adapters. |
| Monte Carlo Strategy Comparison → Export PDF; `MonteCarloStrategyComparisonView` with `MonteCarloPdfExportAction` | Frozen paired comparison; same Monte Carlo report generator | Same chooser and filename behavior as standalone Monte Carlo | Same background task and button busy state | Same behavior; comparison section of `MonteCarloPdfExportTest`, `MonteCarloPairedWorldTest`, and historical fidelity tests. |
| Social Security Strategy Analyzer → Deterministic integrated and Longevity-Weighted integrated result actions; `SocialSecurityStrategyAnalyzerDialog.exportPdf(boolean)` | Completed deterministic or weighted integrated analysis with selected presentation state and frozen report context; `IntegratedAnalyzerPdfExporter` | `FileChooser`, title “Export Integrated Analysis PDF”; mode-specific `Social-Security-Integrated-Deterministic-Analysis.pdf` or `Social-Security-Integrated-Longevity-Weighted-Analysis.pdf` | Report is captured on FX thread; PDF rendering runs in a `Task` on a virtual thread. Existing `exportBusy` state disables export actions. | Cancel returns. Rechecks analysis readiness after chooser. Uses chooser-selected path directly (no extension append in this method). Failure gets a shared PDF error alert; success alert includes path. Covered by `IntegratedAnalyzerPdfStateTest`, `IntegratedAnalyzerPdfReportTest`, `IntegratedAnalyzerPdfStateTest`, integrated PDF tests, and history fidelity tests. |
| Social Security Strategy Analyzer → Social Security Only and Quick Comparison result actions; `exportAdditionalPdf(boolean)` | Frozen mortality-weighted Social Security-only report or deterministic integrated quick-comparison report; `SocialSecurityOnlyReportAdapter` / `QuickComparisonReportAdapter`, then `IntegratedAnalyzerPdfExporter` | `FileChooser`; `Social-Security-Only-Analysis.pdf` or `Social-Security-Quick-Comparison.pdf` | Small frozen inputs are captured on FX; report preparation/rendering runs in a background `Task` on a virtual thread. Ownership/result identity is rechecked before completion UI. | Cancel/readiness change returns. Appends `.pdf`; explicit confirmation if the appended path exists. Errors use the common PDF presentation. Covered by `SocialSecurityAndQuickPdfTest`, report tests and analyzer persistence/freshness tests. |
| Single-person Integrated Retirement Plan deterministic claiming analysis; `SinglePersonIntegratedView` with `IndividualPdfExportAction` | Frozen completed deterministic strategy search; `IndividualAnalyzerReportAdapter`, `IntegratedAnalyzerPdfExporter` | `FileChooser`, title “Export individual claiming PDF”; `social-security-deterministic.pdf` | Captures a report on FX, then renders on a virtual-thread `Task`. Export is disabled unless the current completed result is exportable and analysis jobs are idle. | Cancel returns; appends `.pdf`, confirms collision at appended destination, reports success/error. Covered by `IndividualReportTest` and Social Security PDF report tests. |
| Single-person Social Security Only; `SinglePersonMortalityView` with `IndividualPdfExportAction` | Frozen Social Security-only mortality-weighted report; same Integrated Analyzer exporter | Shared chooser; `social-security-only.pdf` | Same single-person background path | Same behavior. `IndividualReportTest`, `IntegratedAnalyzerPdfReportTest`, and `SocialSecurityAndQuickPdfTest` cover report generation. |
| Single-person Longevity-Weighted Integrated analysis; `SinglePersonMortalityView` with `IndividualPdfExportAction` | Frozen longevity-weighted integrated result; same Integrated Analyzer exporter | Shared chooser; `social-security-longevity-weighted.pdf` | Same single-person background path | Same behavior. Covered by `IndividualReportTest`, `IntegratedAnalyzerPdfReportTest`, and weighted restoration/report tests. |
| Projection Analyzer History dialog → Export PDF; `ProjectionAnalyzerHistoryDialog.export()` | Selected original associated analysis. Supports deterministic/weighted Social Security analyzer snapshots and single/couple Monte Carlo run or paired-comparison snapshots; historical adapters restore the original context. Uses `IntegratedAnalyzerPdfExporter` or `MonteCarloPdfExporter`. | `FileChooser`, title “Export Original Analyzer PDF”; fixed `historical-analyzer.pdf` regardless of selected report type | Captures selected association/report work on FX, then executes on `source.getAnalysisResults().background(...)`. The original result reference is guarded; export does not recalculate. | Cancel returns. Failure updates the dialog notice; no success alert. No explicit extension append or collision confirmation beyond chooser behavior. Covered by `ExhaustiveHistoricalPdfFidelityTest`, `MonteCarloHistoricalPdfFidelityTest`, `ExhaustivePersistenceTest`, and restoration/report tests. |

`MainWindow` has no PDF menu action. Its File choosers are for plans, and the Results Summary's Export action is the sole projection-PDF UI entry point. The adjacent CSV export is a separate path and is outside the requested PDF choice workflow. `LongevityWeightedIntegratedView` contains its export button, wired by `SocialSecurityStrategyAnalyzerDialog` to the weighted integrated path above.

### Generator inventory and write behavior

| Generator | Input form | Current output handling | Notes |
|---|---|---|---|
| `ProjectionPdfExporter` | `RetirementPlan`, `Projection`, non-investible projection list, immutable `ProjectionPdfReport`, destination | Writes the PDF directly to destination | Existing output is not published through a temporary sibling. Uses PDFBox and shared `PdfReportSupport`/chart rendering. |
| `BreakEvenPdfExporter` | `BreakEvenPdfReport`, destination | Writes directly to destination | Report contains completed analysis/context/insight; chart rendering can produce multiple pages. |
| `IntegratedAnalyzerPdfExporter` | `IntegratedAnalyzerReport`, destination | Writes a sibling temp PDF, then moves it over destination; deletes the intermediate in `finally` | The temporary is an atomic-publication intermediate, not a durable preview. |
| `MonteCarloPdfExporter` | `MonteCarloPdfReport`, destination | Writes a sibling temp PDF, then moves it over destination; deletes the intermediate in `finally` | Frozen report-only input; no Monte Carlo run occurs for export. |

The history dialog selects between Integrated Analyzer and Monte Carlo exporters based on the saved analysis payload. `ProjectionCsvExporter` is not a PDF generator. Tests also generate PDFs directly for visual, numerical, regional-tax, and historical-fidelity assertions; these are test artifacts, not application entry points.

## Existing shared architecture

There is no common PDF workflow service. Shared pieces are at narrower levels:

- `PdfReportSupport` shares PDF layout primitives, not export orchestration.
- `MonteCarloPdfExportAction` centralizes only standalone and paired Monte Carlo chooser/task/error handling.
- `IndividualPdfExportAction` centralizes the three single-person Social Security PDF actions.
- `SocialSecurityStrategyAnalyzerDialog` owns distinct integrated, Social Security-only, and Quick Comparison paths with stronger result-identity checks.
- `ProjectionAnalyzerHistoryDialog` owns original-context restoration and asynchronous export.
- Exporters are report generators and generally do not depend on JavaFX. `IntegratedAnalyzerPdfExporter` imports some UI presentation helpers for report wording; it does not display UI or open a viewer.

Search found no `Desktop.open`, `Desktop.Action.OPEN`, JavaFX `HostServices.showDocument`, `ProcessBuilder`, or other operating-system document-opening implementation in production source. The existing temporary file usages are `AtomicFileWriter` internals and the two exporter sibling-file transactions. The latter are deliberately deleted after final save and cannot remain available to a PDF viewer.

## Proposed View / Save / Cancel interaction

At each existing Export PDF action, keep current result-availability gates and show one owner-modal, keyboard-accessible choice with **View PDF**, **Save PDF**, and **Cancel**. The outer Results Summary format choice (CSV/PDF/Cancel) remains in place; choosing CSV is unchanged, and choosing PDF then invokes the shared PDF choice. Cancel at either prompt must do no PDF render, no Save chooser, and no preview-file creation.

- **View PDF:** capture the currently exportable result once, generate exactly the same report through its current adapter/exporter, write to a unique app-managed `.pdf`, and request opening that file in the OS default viewer. Do not show the destination chooser. If launching fails after generation, tell the user the PDF exists and show its retained path; do not delete it or silently substitute Save.
- **Save PDF:** invoke the same native Save chooser with the same report-specific suggested name and extension filter, then pass the chosen destination to the existing exporter. Retain current success/error behavior, extension handling, and overwrite confirmation per entry point. Existing inconsistencies are identified below and should not be silently reinterpreted as a content change.
- **Cancel:** return immediately. Do not call the PDF exporter or create a file. Report readiness/capability checks may run before the dialog, but no report preparation that renders PDF bytes should run until the user selects View or Save.

For Save, show the chooser after the user's Save selection; chooser cancellation ends without PDF generation. For View, do not create a second save copy or call the Save chooser. Keep the existing export button names and locations.

## Recommended shared design

Implement a small JavaFX-facing `PdfExportWorkflowCoordinator` (name illustrative) plus a focused `PdfPreviewFileStore`:

1. The coordinator receives the owning `Window`, export readiness predicate, suggested filename, a report-capture supplier, a writer callback `(report, destination)`, and injected choice/Save-dialog and viewer services.
2. It presents the shared three-choice dialog. On Cancel it returns without invoking the report supplier or writer. On Save it obtains the Save destination and only then captures/writes. On View it captures once, obtains a unique preview path, writes, then opens it.
3. Report capture that reads JavaFX-bound state happens on the FX thread. The immutable report object is passed to a worker for PDFBox rendering and file I/O. Recheck that the owning view/result binding is still valid before publishing a success/failure alert, following the stronger guards already used by Social Security exports.
4. Use existing JavaFX `Task`/worker conventions and per-action busy state. Do not put PDF generation in event handlers, do not call projection/analyzer engines, and do not share the analysis-computation executor unless its shutdown and admission semantics are verified.
5. Keep the existing four PDF renderers and report adapters. The workflow layer handles only choice, file destination, dispatch, external launch, and user feedback. Inject a `PdfOpener` interface so automated tests use a fake and never start an external process.

`java.awt.Desktop` with `isDesktopSupported`, `isSupported(Desktop.Action.OPEN)`, and `open(File)` is the smallest platform-default-handler adapter on the current Java desktop runtime. Convert unsupported operation, security restrictions, missing associations, and I/O errors into a UI error that preserves the generated preview path. Do not fall back to a shell command or concatenate a user-controlled path into a process command. Run potentially blocking OS launch calls off the FX thread, then post UI feedback on FX.

## Temporary-file lifecycle and privacy

Recommended preview location: an Aledrix-specific directory under the current user's application data/configuration root (the application already stores settings under `~/.retirement-planner`). Use random unique names such as `preview-<random>.pdf`, create files with restrictive current-user permissions where the platform exposes them, and rely on the user's profile ACL inheritance on Windows. Do not put a stable plan name or financial details in the temporary filename.

Do not delete a preview immediately after `Desktop.open`; the API does not report when the external application has finished reading. Do not use `deleteOnExit`, close-time deletion, or a process handle as proof the PDF viewer released the file. Keep successfully opened and launch-failed complete PDFs, and perform best-effort stale cleanup on a later application startup using a conservative age threshold (recommended 30 days) and a strict application-owned filename/prefix filter. On Windows, locked files may fail deletion; skip them and retry at a later startup. Cleanup failure must not block launch. Incomplete files from render failures should be removed immediately when possible; if locked, include them in stale cleanup. Document the retained-file location and provide a way for users to remove them manually.

The directory/ACL mechanism and retention threshold are privacy-relevant policy decisions. Avoid broad cleanup of the OS temp directory or deletion of unrelated files. Do not delete previews belonging to a still-running Aledrix instance; a startup cleanup should use age limits, and concurrent processes need unique paths or a safe per-process subdirectory.

## Threading, performance, and compatibility

| Current family | Current export-thread behavior | Phase 17B implication |
|---|---|---|
| Results Summary projection | Report capture and PDF rendering are synchronous on FX | Long projections or charts can block UI. Move byte rendering to background after taking a stable input/report snapshot. Preserve projection identity and never rerun projection. |
| Break-Even | Report object is captured when dialog opens; PDF rendering is synchronous on FX | Multi-page charts can block UI. Background render is advisable; keep dialog-owned snapshot and no recalculation. |
| Monte Carlo standalone/paired | Frozen report capture plus background PDF rendering; button busy state | Coordinator should retain its busy guard, stale/historical eligibility, current report capture, and completion notification. No simulation is run for export. |
| Couple and single Social Security | Background PDF rendering, current/stale/result binding checks; analyzer computations are separate | Preserve `exportBusy`, job-state guards, and original result report capture. No analysis runs on View/Save. |
| Analyzer history | Background export on analysis-result executor; selection/projection ownership guards | Avoid losing the old immutable snapshot context if the user changes the selected row or switches plans during rendering. Do not introduce a current-plan dependency. |

The PDF exporters are not uniformly atomic: Integrated Analyzer and Monte Carlo render to sibling temp files then replace; Projection and Break-Even currently write directly. A shared destination workflow should not alter page content, metadata, titles, or report data. Whether to make all writes atomic is a separate implementation decision because it changes failure/overwrite behavior. Save overwrite behavior is also inconsistent when an extension is appended: Monte Carlo, individual Social Security, and the additional Social Security/Quick paths check an existing appended `.pdf`; Break-Even and Results Summary do not perform the same explicit second check; couple integrated/history paths rely on the chooser. The Phase 17B implementation should preserve the accepted behavior or request approval before standardizing it.

Duplicate requests are prevented by busy state in Monte Carlo/Social Security/history, but Results Summary and Break-Even have no PDF busy state because they run synchronously. A coordinator should disable its owning entry while a task is active and reject reentrant requests; it should not invent progress percentages. Existing analysis progress/cancellation remains unrelated and authoritative. Current PDF generation itself is not cancellable, and the proposed View feature need not add cancellation.

## Compatibility risks

- Extra choice dialog adds a step to existing entry points; retain owners, modality, keyboard/Escape/close cancellation, themes, labels, and accessible names.
- Results Summary currently has a CSV/PDF format prompt, so PDF export will be a second decision; keep CSV unchanged unless separately approved.
- Keep report title/content/metadata/creation timestamps and frozen historical assumptions identical between View and Save. Capture one immutable report per action to avoid current/historical races.
- Per-entry suggested filenames and extension/overwrite rules differ. An abstraction must accept these as input rather than invent one global default.
- Large reports can consume disk and include private financial data. Preview directory ACLs, retention, cleanup, and failure paths need explicit tests and documentation.
- `Desktop.open` may be unsupported in headless environments or on a system with no PDF association. UI tests must inject an opener and must not launch a viewer.
- A viewer may lock a file on Windows. Cleanup must be retryable and best-effort.
- The system viewer might display an existing stale cached tab if names were reused; unique names avoid that.
- Existing PDF generation can fail after touching the target in Projection/Break-Even; keeping exporters unchanged preserves present behavior but View should render to a fresh unique path so no prior preview is damaged.
- Existing reports may be captured from live Summary chart selection or analyzer view selections. Capture on FX once after choice, then render that frozen report off-thread.

## Test plan for Phase 17B

1. Coordinator choice tests: View, Save, Cancel; Escape/window close equals Cancel; readiness false; Save chooser canceled; no report supplier/writer/path call on Cancel.
2. Equivalence: write one immutable report through Save and View to different destinations; compare extracted text, PDF metadata, page count, and rendered page pixels where deterministic timestamps are frozen.
3. Preview-store tests: unique concurrent names, `.pdf` suffix, no sensitive names, complete-file handling, failed write cleanup, stale-only cleanup, lock/deletion failure retry, concurrent process safety, and permissions where supported.
4. Opener tests with a fake: supported/open success, unavailable Desktop, unsupported OPEN, missing association/I/O failure, security denial. Never call `Desktop.open` in tests.
5. Per-workflow UI tests: ensure existing button/action still exists, chooser appears only for Save, View bypasses chooser, each filename and filter is preserved, readiness/stale guards still apply, no financial calculation or analysis is triggered, and task busy state blocks repeated activation.
6. Regression: retain `ProjectionPdfExportTest`, `BreakEvenPdfExportTest`, `MonteCarloPdfExportTest`, `IntegratedAnalyzerPdfReportTest`, `IntegratedAnalyzerPdfStateTest`, `SocialSecurityAndQuickPdfTest`, historical PDF fidelity tests, and PDF content/financial golden assertions. Add Light/Dark dialogs, keyboard accessibility and owner-modality tests.

Focused baseline run for this audit: **33 tests, 0 failures, 0 errors, 0 skipped**. It covered projection, Break-Even, Monte Carlo, integrated Social Security PDF state/content, and Social Security/Quick PDF reports. Tests generate/read PDFs but do not invoke an OS viewer. `git diff --check` passed. No visual previews were required or generated for this audit; the current workflow is not being changed.

## Proposed Phase 17B boundaries

**17B1 — Workflow and preview infrastructure:** introduce injectable choice/Save/opener interfaces, background export dispatch contract, app-managed preview store, stale cleanup policy, focused unit tests. Do not change PDF contents.

**17B2 — Projection Summary and Break-Even:** integrate the coordinator into the synchronous paths, capture immutable report inputs on FX, preserve CSV and current Save behavior, move rendering off FX only after confirming worker-safe report data, add chooser/cancel and error tests.

**17B3 — Monte Carlo and Social Security current-result exports:** replace duplicated choice/chooser plumbing while retaining each family's eligibility, stale-result, owner, busy, historical-context, and report metadata rules.

**17B4 — Projection Analyzer History and final audit:** add View for original saved contexts, preserve association guards, reconcile all UI export buttons against the inventory, run complete report golden tests and full Maven regression, verify both themes and native Windows PDF associations/locked-file cleanup.

Each stage should be reviewed and accepted before the next. No migration changes financial engines, result versions, historical snapshots, reports, or PDF content.

## Decisions requiring approval before Phase 17B

1. Approve **Desktop.open** as the default-viewer bridge (recommended) versus JavaFX `HostServices.showDocument`.
2. Approve the app-managed per-user preview directory and a **30-day startup cleanup threshold**; confirm that user-visible instructions for manually clearing retained financial PDFs are acceptable.
3. Confirm behavior when opening fails after successful generation: recommended alert names the retained file path and leaves the file available for manual opening.
4. Confirm whether all PDF rendering should move to background tasks or whether the migration should preserve current synchronous Projection/Break-Even timing. Recommendation: render all immutable report snapshots in background while keeping existing analyzer calculation progress/cancellation separate.
5. Resolve whether Save extension/overwrite inconsistencies should remain exactly per entry point or be standardized. Recommendation: preserve each current Save behavior for the first migration and handle any standardization separately.
6. Confirm the additional PDF choice prompt after Results Summary's existing CSV/PDF/Cancel format prompt; recommendation: keep the CSV format dialog and show View/Save/Cancel only after PDF is selected.

No Phase 17B implementation was started.
