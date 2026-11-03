# Phase 17B4 — Monte Carlo PDF View/Save Integration

## Summary

The remaining Monte Carlo PDF export actions now use the shared Phase 17B1 View PDF / Save PDF / Cancel coordinator:

- Standalone fixed-horizon and longevity-adjusted Monte Carlo reports.
- Monte Carlo Strategy Comparison reports.
- Historical standalone and paired Monte Carlo reports in Projection Analyzer History.

The existing `MonteCarloPdfReportAdapter` and `MonteCarloPdfExporter` remain the report and rendering implementation. Exports capture completed results only; they do not rerun simulations or regenerate random values. PDF layout, content, report metadata conventions, and safe sibling-temporary-file publication remain unchanged.

## Complete Monte Carlo PDF export inventory

| Entry point | Report variants | Capture and rendering | Phase 17B4 behavior |
|---|---|---|---|
| `MonteCarloAnalysisView` → `MonteCarloPdfExportAction` (`mc-export-pdf`) | Fixed Lifespan; Longevity-Adjusted | `preparePdfReport()` formats the currently completed session result through `MonteCarloPdfReportAdapter`, then `MonteCarloPdfExporter` renders it | View/Save/Cancel; captures once after choice (and Save chooser where applicable); ready/result identity guarded |
| `MonteCarloStrategyComparisonView` → `MonteCarloPdfExportAction` (`mcc-export-pdf`) | Fixed and longevity paired comparisons | Same report adapter/exporter, using the exact accepted `MonteCarloStrategyComparisonRun` | View/Save/Cancel; preserves strategy A/B pair, paired summary, assumptions, and comparison chart data |
| `ProjectionAnalyzerHistoryDialog` — Monte Carlo snapshot branch | Single/couple fixed, longevity, paired fixed, paired longevity | `MonteCarloSnapshotPresentationMapper` restores the selected immutable saved payload; `MonteCarloPdfReportAdapter` formats that restored result; the existing exporter writes it | Shared history workflow captures only after choice and preserves selected association/content identity |

The Social Security history branch remains integrated through the same generic historical workflow helper added in Phase 17B3. Other Social Security actions remain unchanged in this phase. No additional Monte Carlo PDF action was found in production source. CSV export paths are unchanged.

## Shared workflow integration

`MonteCarloPdfExportAction` now delegates choice, Save destination selection, worker dispatch, preview creation, opening, busy/reentrancy handling, and failure presentation to `PdfExportWorkflowCoordinator<MonteCarloPdfReport>` created with `PdfExportWorkflowFactory`.

The Save chooser preserves its title (`Export Monte Carlo PDF`), `PDF report` filter, filename, extension normalization, native overwrite behavior, and the explicit confirmation used when appending `.pdf` would collide with an existing file. The standalone fixed/longevity filename is selected from the completed run's mode; the paired filename remains `monte-carlo-strategy-comparison.pdf`. These names match the existing report adapter output without formatting or capturing a report before the choice.

- **View:** no Save chooser; the one captured report renders off the FX thread to a completed managed preview before the injected production opener is called.
- **Save:** the native chooser runs first; the report is captured once only after a destination is accepted, then the existing exporter writes to that destination.
- **Cancel / chooser cancellation:** no report capture, PDF render, preview creation, or simulation-state change.

The existing `MonteCarloPdfExporter` safe-write behavior is unchanged: it writes to a sibling temporary PDF and replaces the destination only after a complete document is produced. View uses the preview store's managed path as that destination. Completed previews are retained under the existing B1 retention policy, including if viewer launch fails.

The busy state disables repeat export activation. The worker receives only a frozen `MonteCarloPdfReport`, not JavaFX controls, a session, a plan, or a simulation engine. The captured report remains valid if the view closes or the selected result changes; stale success notification is suppressed by comparing the selected completed-result object identity, while failures still report. No success message claims the OS viewer finished loading the file.

## Immutable capture, identity, and memory

Live standalone and paired actions record the selected completed-run object before showing the choice. Readiness and identity are checked again after the choice and Save chooser. The report supplier is called once only after the user accepts View or Save. If the selected result changed while a chooser was open, the request is rejected before capture. If it changes while rendering, the already accepted report is not redirected to another result.

Historical export binds to the selected `Content` and exact `AnalyzerAssociation` object. The history coordinator rechecks that the association is still attached to the selected projection role before capture. Its `MonteCarloPdfReport` supplier closes over the restored run from the selected historical snapshot; it does not query a last-success slot or current plan result. Historical snapshot timestamps and existing durability context are preserved. The existing Monte Carlo history report adapter and renderer are reused.

The adapter creates presentation data from the completed aggregate result: annual percentile points, report tables, context strings, and summaries. It does not copy every simulation path into a second cache. One report instance is retained by the export task until rendering and opening/saving finish; the live session or history content continues to own its original completed result. The report's copied data grows with annual/report rows, not with the number of individual trials. No separate simulation cache or deep copy of the simulation result was added.

The exporter is vector PDFBox rendering from report data; it does not snapshot a JavaFX chart node. Export rendering and filesystem I/O run on the worker. There is no FX-thread wait, report duplication, or new simulation executor use. Existing shared busy feedback is retained; no percentage progress or new cancellation behavior was introduced. PDF preview cleanup and concurrent-process safety continue to be handled by `PdfPreviewStore`.

## Historical result and comparison preservation

The paired report is created from the one selected `MonteCarloStrategyComparisonRun`, preserving both strategy labels, paired-world comparison values, original assumptions, seed and run count. It does not compute comparison metrics again.

Historical MC capture uses the selected restored snapshot and its saved result context. It does not substitute current results, rerun the Monte Carlo generator, mutate analyzer persistence, or alter freshness checks. Closing or changing the history selection after capture cannot change the report contents. No simulation/result schema or identity contract changed.

## Save and View error behavior

The same `MonteCarloPdfExporter` renders both View and Save reports. Save extension behavior and the appended-extension collision prompt remain specific to the existing MC chooser. Historical MC retains the history chooser title, `historical-analyzer.pdf` suggestion, PDF filter, and no-extension-normalization behavior. The historical Social Security and MC branches now share one generic coordinator-start helper, with separate typed report suppliers and the same selected-association validity checks.

Renderer failures use the shared export error presentation. Viewer-launch errors are distinct and include the retained preview path. A successfully completed preview is not removed because the source view closed. Historical reports retain their prior no-success-alert behavior; current Save reports retain their success message only while the accepted result remains current.

## Performance and visual verification

The opt-in existing `MonteCarloPdfPreviewTest` was run against its already cached completed 5,000-simulation report fixtures (no Monte Carlo simulations were rerun for this measurement). It passed and rendered 24 pages across five reports:

| Cached report | Pages | PDF render time |
|---|---:|---:|
| Fixed standalone | 3 | 260.7 ms |
| Longevity standalone | 7 | 28.4 ms |
| Fixed comparison | 5 | 22.6 ms |
| Longevity comparison | 6 | 12.2 ms |
| Longevity late-life appendix variant | 7 | 8.5 ms |

The fixed report was the first render in the JVM; later timings benefit from PDFBox/JVM warm-up. These are report-generation times only and exclude analysis and page-image rendering. PDF sizes ranged from 8.8 KB to 17.3 KB. The opt-in test also checked text bounds, footer/page numbering, and rasterized all pages. Peak process heap was not separately instrumented. The measured report model contains aggregate annual and table data, not trial-by-trial simulation paths.

Actual JavaFX previews were generated and inspected for standalone, comparison, and historical Monte Carlo choice-dialog owner contexts in Light and Dark themes. View is the default; View, Save, and Cancel remain visible. Save-focused previews show a visible keyboard focus outline. The in-flight state was also rendered in both themes; the existing Export PDF button is disabled during the worker render (the previous MC action did not have a separate progress indicator). Render-failure and viewer-launch-failure alerts were captured in both themes; viewer failure clearly states that the completed preview remains available at its path.

Preview files: `target/phase17b4-monte-carlo-pdf-choice/` (`standalone-*`, `standalone-busy-*`, `comparison-*`, `historical-*`, `*-save-focused-*`, `*-cancel-focused-*`, `render-failure-*`, and `viewer-failure-*`). The real Windows default viewer, native Save chooser behavior, and DPI scaling require native acceptance.

## Tests

Focused command covered the Monte Carlo export action, existing Monte Carlo PDF content/fidelity tests, history association, shared coordinator/store, and JavaFX previews:

```text
47 tests; 0 failures; 0 errors; 1 skipped
```

The one skip is the existing opt-in `MonteCarloPdfPreviewTest` in the ordinary focused run. It was then separately run with `-Dmontecarlo.pdf.preview=true` against cached reports:

```text
1 test; 0 failures; 0 errors; 0 skipped
```

The full Maven regression suite after the final production changes completed:

```text
Tests run: 3980, Failures: 0, Errors: 0, Skipped: 46
BUILD SUCCESS
```

The 46 skips are existing skipped/opt-in tests. Existing financial and PDF golden expectations were not changed. Existing fault-injection tests emitted their expected persistence warnings while passing. `git diff --check` passed after the code changes; it will be run again after this report is added.

After that full run, the integration test was extended only to capture the existing disabled-while-rendering button state in Light/Dark mode. The final `MonteCarloPdfWorkflowIntegrationTest` rerun passed **3 tests, 0 failures, 0 errors, 0 skips**. No production source changed after the full regression run.

## Files changed for Phase 17B4

- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPdfExportAction.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ProjectionAnalyzerHistoryDialog.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPdfWorkflowIntegrationTest.java` (new)
- `src/test/java/com/daviddunn/retirementplanner/ui/pdf/PdfExportChoiceDialogPreviewTest.java` (extended)
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12EAssociationTest.java` (extended)
- `PHASE-17B4-MONTE-CARLO-PDF-VIEW-SAVE.md`

The modified Phase 17B1–B3 source, tests, reports, and other pre-existing working-tree changes were preserved.

## Remaining exports, limitations, and acceptance

No Monte Carlo PDF entry point identified in the Phase 17A inventory remains outside the shared View/Save/Cancel workflow. Social Security exports, Projection, and Break-Even are already integrated in prior phases. CSV exports are unaffected.

Native Windows acceptance should verify:

- View opens completed standalone, paired, and historical MC reports through the configured default PDF viewer.
- Missing viewer association shows the retained preview path.
- Save chooser title, suggestion, `.pdf` normalization, and appended-extension overwrite confirmation match prior behavior.
- Cancel and chooser cancellation leave no PDF or preview behind.
- Changing assumptions/results or closing the view while rendering does not substitute report content or show a stale success message.
- Light/Dark appearance, keyboard focus, and 100%, 125%, and 150% display scaling.

The automated environment did not invoke a real external viewer or exercise native Windows DPI scaling. No files were staged, committed, or pushed. Phase 17B5 has not begun.
