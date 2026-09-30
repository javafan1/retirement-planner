# Monte Carlo Phase 4D — PDF Export

## Starting state and read-only audit

Starting HEAD: `35d0782 Add Monte Carlo strategy comparison UI`.
Initial `git status --short`: empty. `git diff --cached --name-only`: empty.
Phase 4C is committed; no pull/reset/stash was performed.

Existing PDFBox infrastructure inspected before implementation:
`IntegratedAnalyzerPdfExporter` and its deterministic/weighted frozen report adapters,
`ProjectionPdfExporter` / `ProjectionPdfCharts`, `BreakEvenPdfExporter` /
`BreakEvenPdfChart`, `PdfReportSupport`, PDF content/preview tests, and the Results,
Break-Even and Social Security save-dialog flows. These use structured text/tables,
vector drawing, Helvetica, Letter landscape pages, and page-number footers. The
integrated analyzer writes a temporary sibling before replacing the chosen file
and exports on a background task. Existing charts are rendered from prepared data,
not whole-dialog screenshots. PDF tests use PDFTextStripper and PDFRenderer.

Monte Carlo audit: the single-strategy Run contains a frozen result, FanModel,
people metadata, reporting years and reference status. FanModel contains cached
percentiles/reference values plus configured SS markers and Roth/RMD periods.
Longevity intentionally omits the configured-lifetime deterministic reference and
annotations. Its annual rows distinguish living, funded, failed-living and deceased
populations. Terminal outcomes use each world's own balance date immediately before
second death. Fixed results expose immutable terminal/annual percentiles; their
failure-statistics getter is an existing lazy summary of immutable failures.
Comparison Run contains frozen details and the Phase 4B cached summary. Paired
terminal metrics condition on BOTH_COMPLETED; annual rows retain different,
explicit living/comparable populations. No reducer or generator is needed for PDF.
Both sessions invalidate stale results and publish only fully completed analyses.

Reuse decision: reuse PdfReportSupport drawing, wrapping, safe glyph handling and
page-number primitives without modifying them or existing exporters. Add a small
Monte Carlo report composer, immutable report input, and vector fan renderer.
Use Letter portrait with a full-width vector chart and readable paginated tables.
Single-strategy completion will additionally capture plan-derived display metadata
that was absent from Run; this is reporting plumbing, not analysis behavior.

## Implemented architecture and behavior

`MonteCarloPdfReportAdapter` formats a completed Run on the FX thread, reusing
the existing Monte Carlo money, probability, tax-direction and inflation wording.
Its immutable `MonteCarloPdfReport` contains only copied lists, strings, cached
quantiles, deterministic reference values and marker/period metadata. It exposes
no plan, JavaFX control, analyzer, generator or execution callback. The exporter
only accepts this report. Repeated export cannot schedule another analysis.
The fixed result's existing failure-statistics getter summarizes frozen failures;
no financial percentile is calculated from projections during report construction.
Longevity and paired failure statistics are already cached result aggregates.

`MonteCarloRun.planDetails` captures exact projection start, configured horizon,
general inflation, deterministic healthcare inflation and configured reference
return from the isolated run plan at completion. Existing construction overloads
remain compatible; runs without that metadata explicitly say it was not captured,
rather than reading a live plan or inventing defaults. Comparison already has
frozen result-time labels and details; no candidate plan is reopened for export.

Both Monte Carlo views now offer Export PDF next to Run/Cancel. Only COMPLETED,
non-stale, non-null results enable the action. Running, cancelling, cancelled,
failed, idle and closed states reject report capture. Existing source-revision
and input invalidation remain authoritative. Export does not clear or mutate the
completed result, including when writing fails.

The shared Monte Carlo action uses the native PDF Save chooser and useful filenames:
`monte-carlo-fixed.pdf`, `monte-carlo-longevity.pdf`, and
`monte-carlo-strategy-comparison.pdf`. Cancellation is a no-op. Readiness is checked
again after modal dialogs; extension-appended existing paths receive explicit
replacement confirmation. Normal typed PDF paths use native overwrite confirmation.
Formatting is on FX; a separate virtual-thread Task writes the immutable report.
Alerts and button state changes stay on FX. Existing-file contents survive renderer
failure because PDFBox saves to a temporary sibling before replacement; temporary
files are cleaned up. No chart snapshot can fail: charts are vector report drawing.

## Report content and chart design

All types share title/type, export time in UTC, frozen key assumptions, primary
chart, funding results, financial outcome tables, annual context, details and
interpretation. Letter portrait uses 36pt margins and a 540pt content width.
Chart text is vector (8–9pt), body/table text 9–10pt, titles 20pt; no raster dialog
capture or low-resolution scaling. Preview PNGs are for review only. Existing
Helvetica/safe-glyph conventions replace Unicode minus with an ASCII minus in
PDF text; values, precision and positive signs use the same UI formatting.

Fixed reports include deterministic reference, configured SS markers with labeled
keys, and Roth/RMD shading with separately positioned edge strips. Longevity hides
these configured-lifetime annotations just as the UI does. Both use P10–P90 and
P25–P75 tones, fine percentile edges and a thicker solid median. Missing samples
break paths and bands; singleton samples retain a median mark. Comparison uses
cached paired differences, a symmetric signed axis and heavier dashed zero line.
Deterministic references use a dashed line; SS markers use dotted lines plus SS
identifiers. Tone/stroke/position distinctions support grayscale interpretation.
No selected-year guide is included: the report covers the complete annual series.

Financial tables transpose the existing four metrics into columns, with Min, P10,
P25, Median/P50, P75, P90, Max and sample-count rows. Paired tables also show cached
mean differences and greater/equal/less probabilities. All paired terminal figures
explicitly use both-completed worlds; annual comparable counts have their own
visible denominator. There are no imputed zeros or independently subtracted
percentiles. The comparison note explicitly distinguishes percentile(Current minus
Baseline) from percentile(Current) minus percentile(Baseline). Positive taxes mean
more modeled income tax, negative taxes mean less; no preference color is applied.

Funding uses all requested worlds. Comparison shows independent A/B probabilities,
the A-minus-B difference in percentage points, and the four exact paired states.
Failure sections use existing FundingFailureStatistics: counts, first-failure
year distribution, shortfall summary and yearly probabilities/fractions with their
different denominators identified. Shortfall amounts are funding constraints, not
invented unmet-spending amounts or a new failure taxonomy.

Annual longevity tables include requested, living, funded/sample, both alive,
primary only, spouse only, failed living and deceased counts. Paired tables include
requested, living, comparable, each asymmetric funded count, both failed and deceased.
Both retain every late year and mark the existing 1–100 / at-most-5% small-sample
heuristic. Complete annual P10/median/P90 tables accompany population tables.
Longevity terminal tables disclose each household's own second-death balance date,
nominal values across differing years, and no common-date discounting. Successful
terminal-date range and year counts are retained where the single result supplies
them; opening-date deaths do not acquire fabricated annual rows.

Inflation details retain mean, volatility, floor and model version;
healthcare remains explicitly deterministic. The existing arithmetic expected-return
explanation is reproduced. Tables repeat headers after automatic page breaks;
rows are kept whole, numeric columns right aligned, long text wrapped, and headings
reserve following content. Headers identify Retirement Planner and analysis type;
footers use Page X of Y. Metadata sets Title, Subject and Creator. Existing shared
PDF utilities and existing exporter outputs are not modified.

## Verification and no-rerun evidence

All Maven commands used IntelliJ 2025.2.6.2 bundled Maven and
`-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.

| Verification | Result | Log |
| --- | --- | --- |
| New PDF tests plus enabled previews | 9 tests, 0 failures/errors/skips | `target/phase4d-preview.log` |
| PDF, Monte Carlo, mortality, inflation, funding and survivor focused suite | 368 tests, 0 failures/errors, 6 skipped | `target/phase4d-regressions.log` |
| Full non-benchmark suite (`-Dtest=*Test,!*BenchmarkTest`) | 1,632 tests, 0 failures/errors, 8 skipped; BUILD SUCCESS | `target/phase4d-full.log` |
| Final enabled preview rerun after full suite | 1 test, 0 failures/errors/skips; BUILD SUCCESS | `target/phase4d-preview-final.log` |

The eight new PDF test cases include parameterized fixed/longevity single and
paired exports. Each runs the analysis once, exports three times to two paths,
and asserts the service invocation counter remains exactly one. Tests mutate
live inputs/plan state afterward, verify stale export is disabled/rejected, and
verify direct report construction from the frozen completed Run retains original
assumptions. Frozen report collections reject mutation. Failure/cancellation,
unwritable output, rendering failure with an existing destination, all-failed
populations and opening-death worlds are covered. Failed exports retain the result.
PDFTextStripper assertions cover titles, assumptions, funding, all four metrics,
percentiles, paired states, conditional counts, tax direction, second-death notes,
Creator/Subject and page footers. No NaN or fabricated missing-population money.

Existing PDF regressions passed: BreakEvenPdfExportTest (2),
ProjectionPdfExportTest (2), IntegratedAnalyzerPdfReportTest (6), and
IntegratedAnalyzerPdfStateTest (4). These cover the existing deterministic and
weighted Social Security reports and existing results export paths. Their
production exporters/shared utilities are unchanged.

Monte Carlo regression coverage passed, including inflation seed regression (1),
paired aggregation integration (4), paired aggregation (14), random streams (5),
strategy comparison (13), world generator (10), and mortality view (37, including
the real 5,000-world seed-417 tail case). Existing UI/session/chart, mortality,
inflation, funding, survivor, projection, persistence and CSV tests also passed in
the full suite. No financial assertion was weakened and no unrelated production
fix was needed. Opt-in previews account for skipped tests in ordinary runs.

No changes were made to ProjectionEngine, tax/Medicare/RMD/Roth/Social Security
formulas, failure semantics, market/inflation/mortality generation, stream protocol,
Phase 4A execution, Phase 4B reduction, seed fingerprints, schema, persistence or
CSV export. The only RunService change captures display metadata from its already
isolated plan. PDF classes contain no analysis execution or percentile reduction.

## Final PDF previews and performance

All fixtures use completed 5,000-simulation, seed-417 analyses with stochastic
inflation. Four test-only completed report snapshots under `target` permit layout
iterations without rerunning projections. They are not persisted plan data or an
application schema. The late-life preview reuses the longevity result and repeats
its unchanged small-population rows near the front for inspection.

| PDF path | Pages | Export time |
| --- | ---: | ---: |
| `target/phase4d-pdf-preview/fixed.pdf` | 3 | 267.3 ms |
| `target/phase4d-pdf-preview/longevity.pdf` | 7 | 31.8 ms |
| `target/phase4d-pdf-preview/comparison-fixed.pdf` | 5 | 23.6 ms |
| `target/phase4d-pdf-preview/comparison-longevity.pdf` | 6 | 13.9 ms |
| `target/phase4d-pdf-preview/longevity-late.pdf` | 7 | 16.1 ms |

Times measure PDF generation only, excluding analysis and image rendering; the
first export includes initialization overhead. Final previews were regenerated
from cached completed reports. Every page has a 120-DPI, 1020 x 1320 PNG named
`<report>-page-<n>.png` in the same directory. First-page grayscale previews use
`<report>-grayscale.png`. All 28 pages passed automated text-bound, glyph, nonblank
content and footer assertions and were individually opened and visually inspected.

Page-by-page visual findings:

| Report / page | Inspection finding |
| --- | --- |
| Fixed 1 | Full title/assumptions, readable axes, bands, dashed reference, SS keys and Roth/RMD edge strips; funding fits. |
| Fixed 2 | Terminal denominator and all eight percentile/sample rows fit; annual table starts with a complete header. |
| Fixed 3 | Repeated annual header, full remaining rows, failure note and methodology fit; no almost-empty trailing page. |
| Longevity 1 | Mortality inputs and changing-population explanation visible; chart and legend clear; funding fits. |
| Longevity 2 | Second-death disclosure, terminal table and population introduction remain together; no overlap. |
| Longevity 3 | Repeated population header; full rows through 2070; small-sample flags readable. |
| Longevity 4 | Last population row preserved; terminal dates/table metadata wrap within margins; terminal-year table readable. |
| Longevity 5 | Terminal-year continuation and start of annual percentile table fit with distinct headings. |
| Longevity 6 | Annual continuation header and all rows fit; currency columns aligned. |
| Longevity 7 | Two-household final sample retained; failure/methodology notes fit without clipping. |
| Fixed comparison 1 | Signed axis and heavy dashed zero distinct from median; chart explanation, three funding columns and all four states visible. |
| Fixed comparison 2 | Denominator/tax wording, signed financial values, mean and three relation probabilities readable; population table starts cleanly. |
| Fixed comparison 3 | Population continuation and annual delta table have repeated/complete headers; no split rows. |
| Fixed comparison 4 | Negative and positive annual deltas aligned; both strategy failure sections fit. |
| Fixed comparison 5 | Frozen labels/settings and paired-percentile explanation fit with full inflation disclosure. |
| Longevity comparison 1 | Mortality inputs, zero line, bands, funding summary and four states fit. |
| Longevity comparison 2 | Terminal-date and tax direction notes visible; financial table and annual population explanation readable. |
| Longevity comparison 3 | Full population continuation fits; no clipped columns or rows. |
| Longevity comparison 4 | Late-year comparable/deceased counts and small flags retained; annual delta table begins cleanly. |
| Longevity comparison 5 | Signed deltas through the two-household tail and both failure sections fit. |
| Longevity comparison 6 | Frozen mortality table/conditioning and methodology notes fit without orphaned heading. |
| Late-life 1 | Chart/funding unchanged; late-life caution and first small-population row fit below. |
| Late-life 2 | Repeated focused header and remaining 39/18/9/2-household rows readable; terminal table and next population heading fit. |
| Late-life 3 | Full population continuation fits with repeated header. |
| Late-life 4 | Final small-population rows, metadata and terminal-year table fit. |
| Late-life 5 | Terminal-year continuation fits with repeated header and intact rows. |
| Late-life 6 | Annual percentile table and sample counts fit through 2057. |
| Late-life 7 | Final annual rows and complete interpretation notes fit; no accidental eighth page. |

Across all pages: no clipped text/chart, overlapping legend, broken glyph, blank
accidental page, split row or orphaned heading was observed. Titles/types, page
numbers, signed differences and denominators agree with the frozen report data.
Fixed and longevity-comparison grayscale first pages were also inspected: band
tones, solid median, dashed deterministic/zero lines and positioned period strips
remain distinguishable without color. PDF graphics/text are vector, so preview
resolution does not constrain print or zoom quality.

Layout refinements made during inspection: reserve 260pt for chart plus legend
(plot height 172pt), keep section introductions with their first table row, repeat
continuation titles and table headers, omit an inapplicable deterministic legend
in longevity, and compact redundant inflation notes while preserving model and
interpretation disclosures. Full annual tables intentionally make longevity
reports longer. A one-row continuation is allowed rather than shrinking text or
omitting the final surviving sample. No manual native-chooser or screen-reader
speech certification is claimed; guards/content/error paths are tested directly.

## Final file boundary and review gate

Exactly 12 files: 9 production files (4 modified, 5 new), 2 new test files and this
report. UI integration is confined to the two views, Run/RunService metadata and
the two new UI export helpers; the three new app/export files own PDF data/drawing.
No CSS, dependencies, build configuration or existing PDF utility was changed.

Exact `git status --short`:

```text
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRun.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRunService.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonView.java
?? MONTE-CARLO-PHASE-4D-PDF-EXPORT.md
?? src/main/java/com/daviddunn/retirementplanner/app/export/MonteCarloPdfChart.java
?? src/main/java/com/daviddunn/retirementplanner/app/export/MonteCarloPdfExporter.java
?? src/main/java/com/daviddunn/retirementplanner/app/export/MonteCarloPdfReport.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPdfExportAction.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPdfReportAdapter.java
?? src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPdfExportTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPdfPreviewTest.java
```

`git diff --check` passed; new files were also checked with no-index whitespace
checks. Index remains empty. Nothing was staged, committed or pushed.
Generated PDFs, PNGs, cached test reports and logs stay under ignored `target`.

Deferred improvements: tagged-PDF accessibility and broader embedded-font Unicode
coverage remain separate work; the existing standard-font convention is preserved.
Optional shorter annual appendices could be considered after review, but this
version retains every year and its denominator. No new financial features or
analysis settings were introduced.
