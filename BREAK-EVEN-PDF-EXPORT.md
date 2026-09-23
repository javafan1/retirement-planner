# Break-Even PDF export

The Break-Even Analysis dialog now has **Export PDF** beside **Close**, outside its scrolling content. It saves the completed result, Insight and survival/event context; it does not read chart nodes, change the selected metric/disclosure/scroll, run analysis, or modify plans. The default filename is `Break-Even-Analysis.pdf`: this dialog's existing completed metadata has household names/elections but no plan-file name.

## Report

The representative report has six landscape pages (792 × 612 points):

1. Plan comparison, comparison period, four summary cards, survival at sustained recovery, Insight headline and financial snapshot.
2. Cumulative Social Security Benefits Difference.
3. Investable Assets Difference.
4. Total Net Worth Difference.
5. After-Tax Estate Difference.
6. Complete Insight snapshot/driver totals and mortality convention, conditioning date, factors and table metadata.

Long text flows onto additional pages rather than being clipped. Charts always include all four enum metrics, regardless of UI selection. Every chart consumes the exact existing annual differences, actual shared years, claiming events and survival series. No financial difference or mortality probability is recalculated by the renderer. Graph coordinate scaling is presentation-only.

Charts are PDFBox vectors, not JavaFX screenshots. Orange financial series, prominent zero lines, dashed claiming markers (Baseline/Current/Both plans), collision-staggered labels, green sustained-recovery annotations with ages/probability, and tick-aligned whole-percent survival rows are rendered directly. First crossover is reported separately when needed. No recovery, temporary crossover, already-ahead, identical and no-shared-year results use existing UI status formatting and do not receive invented markers. Different horizons use the existing result's shared-year limits and explanation.

The fixed report Insight headline uses Total Net Worth, the UI default, so report content is independent of selector state. All other results and all available driver observations remain included. Driver totals are explicitly observational, not causal attribution; financial amounts are never mortality-weighted.

## Shared infrastructure and files

New:
- `app/export/BreakEvenPdfReport.java`: immutable completed report inputs; all four metrics.
- `app/export/BreakEvenPdfExporter.java`: report-specific composition and flowing summary/details.
- `app/export/BreakEvenPdfChart.java`: vector renderer of prepared metric results/context.
- `app/export/PdfReportSupport.java`: generic fonts, colors, text wrapping, shapes and page numbering extracted from the existing Results PDF renderer. Unicode minus is normalized to printable Helvetica hyphen.
- `ui/breakeven/BreakEvenPdfExportTest.java` (test source): export, state independence, chart/report content, special states and preview generation.

Modified:
- `ui/breakeven/BreakEvenAnalysisDialog.java`: button, file chooser, captured report and export workflow.
- `ui/breakeven/BreakEvenPresentation.java` and `BreakEvenInsightPresentation.java`: existing pure formatting APIs made public for reuse, bodies unchanged.
- `app/export/ProjectionPdfCharts.java` and `ProjectionPdfExporter.java`: delegate generic rendering primitives/page numbering to shared support; existing Results report composition retained.
- `ui/breakeven/BreakEvenCompactLayoutTest.java` (test source): existing synthetic snapshot fixture made package-visible for reuse.

No CSV source, expected CSV output, financial model, projection engine, analysis, mortality or event-detection logic was changed.

## Validation and artifacts

Focused run: 60 tests, zero failures/errors/skips. Includes new PDF tests, existing Results PDF tests (2), Break-Even analyzer/Insight/context/UI/controller tests and CSV regression coverage (`RetirementPlannerMvpIntegrationTest`, `ProjectionYearReportingAgeTest`). Existing Results PDF tests also compare CSV before/after export. CSV exporter SHA-256 remains `C4881F590B05E1BFDA9E47D99587C3D81E9C8DC4035ADB465B4FFD46F0916F21`.

Tests export after selecting Social Security, Net Worth and Estate, assert identical report text, preserve selected metric/disclosure/scroll and original input identities, inspect all metric titles/markers/ages/probabilities/events, and verify vector output. Seven state scenarios cover normal, no recovery, already ahead, identical, multiple crossings, temporary recovery and empty overlap, with differing horizons.

Review PDF: `target/break-even-pdf-preview/break-even-analysis.pdf`.
Preview pages: `target/break-even-pdf-preview/page-01.png` through `page-06.png` (110 DPI).
All six representative pages visually inspected.

The synthetic household is David 70 / Lisa 62 versus David 70 / Lisa 65, 2027–2056. Its actual results are SS 2041 and the three wealth metrics 2045; these are not hard-coded and are not claimed to reproduce the unavailable reviewed plan file. Survival, event and result objects are reused unchanged.

Focused log: `target/break-even-pdf-focused.log`.
Full non-benchmark suite: 1,376 tests, zero failures/errors, three skipped; BUILD SUCCESS (1 minute 33 seconds).

Full non-benchmark log: `target/break-even-pdf-full.log`.

No commit made.