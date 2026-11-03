# Phase 17B3 — Social Security Analyzer PDF View/Save Integration

## Summary

Social Security analyzer PDF exports now use the shared Phase 17B1 View PDF / Save PDF / Cancel workflow. The integration covers couple deterministic integrated, longevity-weighted integrated, Social Security Only, and Quick Comparison reports; single-person deterministic, Social Security Only, and longevity-weighted reports; and associated historical Social Security reports. The Monte Carlo history export remains on its existing path.

The existing `IntegratedAnalyzerPdfExporter` and frozen report representations remain the source of PDF content. Analyzer and projection calculations are not rerun. The View and Save choices render from one captured report, and View publishes the complete PDF to the managed preview store before asking the injected opener to open it.

## Social Security PDF export inventory

| Entry point | Report | Previous path | Phase 17B3 behavior |
|---|---|---|---|
| `SocialSecurityStrategyAnalyzerDialog.exportPdf(boolean)` | Couple Deterministic Integrated | Direct Save chooser and existing integrated PDF exporter | Shared View/Save/Cancel; freezes selected integrated result and report context once |
| `SocialSecurityStrategyAnalyzerDialog.exportPdf(boolean)` | Couple Longevity-Weighted Integrated | Direct Save chooser and existing integrated PDF exporter | Shared workflow; preserves weighted result, selected metric/strategy, assumptions, and heat-map data |
| `SocialSecurityStrategyAnalyzerDialog.exportAdditionalPdf(boolean)` | Couple Social Security Only | Direct Save chooser | Shared workflow; preserves existing report and filename/extension behavior |
| `SocialSecurityStrategyAnalyzerDialog.exportAdditionalPdf(boolean)` | Couple Quick Comparison | Direct Save chooser | Shared workflow; preserves selected comparison result and report content |
| `IndividualPdfExportAction` | Single-person Deterministic Integrated | Direct Save chooser | Shared workflow; captures the selected immutable report and does not rerun analysis |
| `IndividualPdfExportAction` | Single-person Social Security Only | Direct Save chooser | Shared workflow; preserves original chooser and save semantics |
| `IndividualPdfExportAction` | Single-person Longevity-Weighted Integrated | Direct Save chooser | Shared workflow; preserves original report identity and assumptions |
| `ProjectionAnalyzerHistoryDialog` (Social Security associated result) | Historical associated Social Security result | Existing history export action | Shared workflow captures the selected historical snapshot report on FX thread |
| `ProjectionAnalyzerHistoryDialog` (Monte Carlo associated result) | Historical Monte Carlo result | Existing Monte Carlo history export | **Unchanged and deferred**; the new optional Social Security report supplier is absent for Monte Carlo content |

Other PDF workflows, including Monte Carlo standalone and paired reports, are unchanged and deferred to later phases. CSV exports are unchanged.

## Implementation and report capture

The couple and single-person actions use `PdfExportWorkflowCoordinator<R>` through `PdfExportWorkflowFactory`. The coordinator owns the common choice dialog, cancellation-before-capture behavior, Save chooser timing, busy/reentrancy guard, worker dispatch, managed preview publication, opener invocation, and FX-thread completion/error presentation.

Each accepted View or Save action captures the report once on the JavaFX thread. The report holds the selected completed analysis result and its presentation context, including strategy/survivor-election details, mortality and valuation assumptions, selected metric, ranked results, and heat-map data where present. Existing analysis/projection identity and historical/current association information are retained by the existing report adapters and history snapshot.

No live controls are read by the worker. The existing exporter renders the captured report off the JavaFX thread. The implementation does not rerun deterministic projection, longevity-weighted analysis, claiming strategy analysis, mortality calculations, or other analysis work.

Completion callbacks are guarded against stale/closed views so they do not announce an obsolete result as current. Failure reporting remains visible even if the originating result is no longer current. A successfully rendered historical PDF remains valid for the accepted capture; closing or switching the view does not replace its contents.

## Heat maps and report content

`IntegratedAnalyzerPdfExporter`, the heat-map calculations, palette, legend, axes, percentage-of-optimal display, optimal markers, report layout, and PDF metadata conventions were not changed. The report adapters provide immutable heat-map values; these PDF paths do not snapshot JavaFX chart nodes. Thus no JavaFX chart rendering is moved to the background worker and no chart is substituted or simplified.

Existing PDF golden/content tests remain in place. New tests inspect generated report content and selected strategy/heat-map data without changing financial expectations.

## Save, View, and Cancel behavior

- **View PDF:** no Save chooser. The captured report renders to a unique managed preview; only after completion does the coordinator call the injected opener. A viewer-launch failure retains the finished preview and reports its location.
- **Save PDF:** uses each old export's chooser title, suggested filename, extension policy, overwrite confirmation, and destination behavior. Integrated exports that previously wrote the exact selected path continue to do so. Social Security Only and Quick Comparison retain their prior `.pdf` normalization and confirmation behavior when the normalized destination exists. Single-person export retains its prior chooser and extension handling.
- **Cancel / Escape / window close:** returns before report capture, rendering, chooser invocation, or preview creation.

The shared busy guard rejects duplicate activation while rendering. Existing progress/busy behavior is retained; no percentage progress or unsupported cancellation semantics were added. External PDF viewers are never launched by automated tests.

## Historical result handling

The historical analyzer dialog supplies a report capture callback only for associated Social Security content. It captures the exact selected historical payload and its original context instead of substituting a current result. Monte Carlo history remains on its old export implementation. Existing freshness and export-readiness checks remain authoritative. A result/view change during rendering can suppress a misleading success notification but does not mutate or reinterpret the already captured report.

## Files changed for Phase 17B3

- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualPdfExportAction.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ProjectionAnalyzerHistoryDialog.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/IntegratedAnalyzerPdfStateTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12EAssociationTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityPdfWorkflowIntegrationTest.java` (new)
- `src/test/java/com/daviddunn/retirementplanner/ui/pdf/PdfExportChoiceDialogPreviewTest.java` (extended)
- `PHASE-17B3-SOCIAL-SECURITY-PDF-VIEW-SAVE.md`

The shared PDF coordinator, preview store, opener, choice dialog, and Phase 17B2 integrations were existing Phase 17B1/B2 work and were reused without creating a competing implementation.

## Tests and visual verification

Focused command covered the Social Security export integration, shared coordinator/store, analyzer PDF, history association, and preview tests:

```text
59 tests; 0 failures; 0 errors; 0 skipped
```

The complete Maven regression suite completed successfully:

```text
Tests run: 3976, Failures: 0, Errors: 0, Skipped: 46
BUILD SUCCESS
```

The 46 skips are the suite's existing skipped tests; no assertions or golden values were weakened. Fault-injection persistence tests emitted expected warning traces while passing.

Actual JavaFX choice-dialog previews were generated and inspected in Light and Dark themes for:

- Deterministic Integrated
- Longevity-Weighted Integrated
- Social Security Only
- Quick Comparison

Preview files are under `target/phase17b3-social-security-pdf-choice/` (`*-light.png` and `*-dark.png`). The shared dialog showed all three actions without clipping, used View as the default action, and followed the active theme. Automated rendering used fake openers. Native Windows scaling and launching a real external PDF viewer were not exercised in this environment.

Final `git diff --check` passed.

## Deferred exports and limitations

Monte Carlo standalone/paired PDF export and the Monte Carlo branch of analyzer history are unchanged. Other non-Social-Security PDF workflows and CSV exports are unchanged. The operating-system PDF association and real viewer launch still require native Windows acceptance; automated tests intentionally do not invoke external applications.

## Git status and acceptance

The existing Phase 17B1/B2 modified and untracked files were preserved. No files were staged, committed, or pushed. The final working tree also contains the Phase 17B3 files listed above and pre-existing changes in `MainApplication`, `BreakEvenAnalysisDialog`, `ResultsSummaryView`, the application stylesheet, and Projection PDF tests, plus the B1/B2 reports and shared PDF infrastructure.

**Stop gate:** Phase 17B3 is ready for native Windows acceptance. Verify real default-viewer opening, Windows Save chooser behavior/overwrite confirmation, and the shared dialog in both themes. Phase 17B4 has not begun.
