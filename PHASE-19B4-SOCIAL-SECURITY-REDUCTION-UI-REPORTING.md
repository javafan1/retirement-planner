# Phase 19B4 — Social Security Benefit Reduction UI and Reporting

## Summary

Phase 19B4 exposes the existing Social Security benefit reduction assumption in the Assumptions screen and adds conditional disclosure to the Summary, Yearly Projection Details, analyzer assumption summaries, and applicable PDFs. A zero reduction adds no Summary/report disclosure. No calculation, plan schema, fingerprint, navigation, PDF workflow, or PDF destination behavior was changed.

The reduction continues to be computed by the Phase 19B2 policy and propagated by the Phase 19B3 projection/analyzer paths. Phase 19B4 reads and presents the configured or frozen values only.

## Assumptions controls and validation

The Assumptions screen now has a compact **Future Social Security Benefit Reduction** card below Social Security assumptions. It contains:

- **Social Security Benefit Reduction (%)** — defaults to `0`; accepts decimal percentages from 0 through 100 inclusive.
- **Reduction Effective Year** — defaults to `2032`; requires four ASCII digits and a year from 1000 through 9999. It is required even when the percentage is zero and is independent of the projection horizon.

Both fields use the existing required-field indicator infrastructure, the existing Apply lifecycle and field-specific validation. Apply reconstructs immutable `EconomicAssumptions` with the two entered values while retaining the other assumption values. Invalid values keep the form open, show the existing validation feedback, and retain the draft text. Before Apply, edits remain view drafts; Apply follows the ordinary plan-modified/revision/projection invalidation path. Closing through the existing workspace Close action preserves drafts under the existing view-instance lifecycle.

The explanatory tooltip is the approved text:

> Allows you to model a potential future reduction in Social Security retirement and survivor benefits. Enter the percentage by which benefits would decrease and the calendar year when the reduction would begin. The reduction is assumed to remain in effect for all subsequent years. This is a planning scenario, not an enacted change to Social Security law. A value of 0% uses currently scheduled benefits.

The tooltip is available on both controls. The guided Assumptions editor also includes the fields in its advanced section and draft summary; no separate calculation or persistence route was introduced.

## Summary and projection details

The right-hand Key Assumptions column shows `Social Security reduction: R% beginning Y` only when the committed plan value is nonzero. It is read-only and uses the plan values loaded by the Summary; it does not read Assumptions screen draft text. It is a wrapped note inside the existing Planning Horizon card and does not alter the card order, column location, width, Apply behavior, or chart layout.

Yearly Projection Details receives an optional disclosure from the accepted `CompleteProjectionSnapshot` input document. This keeps the wording tied to the projection being inspected rather than to later plan edits. The disclosure is omitted for zero. `ProjectionYearDetailsRequest` retains its previous three-argument constructor for source compatibility.

## Analyzer presentation and historical provenance

The display policy is conditional: no extra line for 0%; otherwise `Social Security benefit reduction: R% beginning Y`.

| Surface | Source used |
|---|---|
| Social Security-only deterministic and mortality-weighted strategy analysis | Reduction policy carried by the frozen strategy request used for that analysis |
| Integrated deterministic, weighted, and Quick Comparison input summaries | Frozen plan/context passed to `SocialSecurityAnalyzerInputSummary` |
| Individual Social Security analysis and its PDF | `IndividualReportContext` captured from result-time plan assumptions; historical report adapter retains the captured line |
| Monte Carlo standalone PDF | Frozen run's plan-details summary |
| Monte Carlo strategy comparison PDF | Captured Current Plan and Saved Baseline details, each disclosed independently only when nonzero |
| Projection PDF | Captured plan/report input supplied to `ProjectionPdfExporter` |
| Yearly Projection Details | Accepted projection snapshot inputs |

The implementation does not substitute current settings into retained analyzer report contexts or projection snapshots. Existing frozen result identity and export workflows remain unchanged.

### Reporting limitation

Break-Even report metadata (`BreakEvenPlanSummary` and the resulting PDF report context) does not contain a frozen `EconomicAssumptions` reduction value. Reading the current plan at export time would risk describing a different assumption than the selected comparison used. Therefore the Break-Even report does not display this new assumption in Phase 19B4. This limitation is documented rather than presenting potentially incorrect provenance. Likewise, no separate historical projection PDF entry point was found; ordinary projection export uses its accepted captured projection/plan input.

The new disclosure is not repeated in yearly data rows. Existing charts, heat maps, tables, PDF colors, titles, page layouts, and report values are not recalculated or redesigned. At 0%, existing report presentation remains unchanged.

## PDF coverage

| Report family | Nonzero disclosure | Zero behavior | Provenance |
|---|---|---|---|
| Retirement Projection PDF | Yes | Omitted | Captured plan passed to exporter |
| Social Security-only deterministic / mortality-weighted PDFs | Yes, through captured strategy request/context | Omitted | Frozen analysis request |
| Individual Social Security PDFs | Yes | Omitted | Result-time `IndividualReportContext` |
| Integrated deterministic / longevity-weighted / Quick Comparison PDFs | Yes | Omitted | Frozen analyzer input summary |
| Standalone Monte Carlo PDFs | Yes | Omitted | Frozen run plan details |
| Monte Carlo comparison PDFs | Yes, separately for Current and Baseline where applicable | Omitted for each zero side | Captured comparison details |
| Break-Even PDFs | Not added | Existing presentation retained | Report context lacks the frozen economic assumption; see limitation above |
| Historical projection PDF | No separate entry point identified | N/A | No separate historical projection PDF workflow identified |

PDF View/Save/Cancel coordination, preview storage, chooser defaults, PDF export directory settings, and Save behavior are unchanged.

## Files changed for Phase 19B4

Production:

- `src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/summary/ProjectionYearDetailsRequest.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/summary/ProjectionYearDetailsPane.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/ProjectionYearDetailsDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAnalyzerInputSummary.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualReportContext.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualAnalyzerReportAdapter.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerConfigurationLayout.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRunService.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonRunService.java`

Tests:

- `src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsLayoutTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/ResultsSummarySocialSecurityPreviewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAnalyzerInputPresentationTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/charts/ProjectionPdfExportTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloComparisonPresentationTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/IntegratedAnalyzerPdfReportTest.java`

Earlier Phase 19A–19B3 modified and untracked work remains in the working tree and was preserved.

## Visual verification

JavaFX preview tests rendered actual controls in Light and Dark themes at 900×650 and 1180×820. The files are written under `target/phase19b4/previews/`:

- `assumptions-reduction-LIGHT-900.png`
- `assumptions-reduction-DARK-900.png`
- `assumptions-reduction-LIGHT-1180.png`
- `assumptions-reduction-DARK-1180.png`
- `summary-reduction-LIGHT-900.png`
- `summary-reduction-DARK-900.png`
- `summary-reduction-LIGHT-1180.png`
- `summary-reduction-DARK-1180.png`

The Assumptions card crop showed readable labels, red required markers, aligned controls, and correct theme styling. The Summary previews showed the nonzero disclosure visible and wrapped within the existing right-hand Key Assumptions panel in both themes and sizes. The Summary at 1180px retains existing chart/metric horizontal scrolling; no change to that behavior was made. These are JavaFX previews on the available environment; native Windows display scaling and acceptance remain outstanding.

## Tests and verification

Focused tests run:

- Assumptions, Summary, Projection PDF, analyzer input presentation, integrated report, Monte Carlo comparison, and Yearly Details navigation: **84 tests, 84 passed, 0 skipped, 0 failures, 0 errors**.
- Assumptions layout, Planning Horizon layout, Assumptions Apply/validation, and Summary after final layout adjustments: **75 tests, 75 passed, 0 skipped, 0 failures, 0 errors**.
- Actual Light/Dark previews at 900×650 and 1180×820: **2 preview tests passed**; the full Assumptions/Summary rendering set was regenerated after the final Summary placement change.

Full Maven regression results: **4,026 tests run, 3,980 passed, 46 skipped, 0 failures, 0 errors**. The skipped tests are existing opt-in/preview/benchmark cases. No financial golden values were regenerated.

`git diff --check`: **passed** (only Git line-ending conversion warnings were emitted).

## Financial, schema, and compatibility scope

Phase 19B4 does not alter Social Security reduction mathematics, COLA, survivor eligibility, mortality, taxes, Roth conversion behavior, Monte Carlo mathematics, projection snapshot schemas, navigation, Phase 17B settings/Close behavior, or Phase 18A export-directory behavior. It adds no independent calculation path and does not mutate historical results. Zero-reduction behavior and presentation remain unchanged.

## Native Windows acceptance checklist

- Verify the new card and tooltip in Assumptions in Light and Dark mode.
- Confirm defaults `0` and `2032`, decimal entry, required asterisks, invalid-value feedback, Apply, and Cancel/draft retention.
- Verify the Summary disclosure appears only for nonzero committed values and stays in the right-hand Key Assumptions column at 900×650 and 1180×820.
- Confirm Yearly Details uses the accepted projection's frozen assumption after changing current plan assumptions.
- Verify deterministic, integrated, individual, and Monte Carlo analyzer summaries/PDFs disclose captured nonzero assumptions, and 0% output stays uncluttered.
- Confirm the Break-Even PDF limitation is understood; its frozen context currently does not carry this assumption.
- Exercise existing PDF View/Save/Cancel and configured export-directory behavior unchanged.
- Confirm Phase 17B Close navigation retains the Assumptions view draft.

## Git status

The branch remains `main`. Existing Phase 19A–19B3 modified and untracked work was preserved; Phase 19B4 changes remain unstaged. No files were staged, committed, or pushed. Native Windows acceptance is still required before committing the complete Phase 19 feature.
