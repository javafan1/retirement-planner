> Implementation update: the approved migration is implemented. The original audit below is preserved verbatim as historical context; see **Approved Stage 4C implementation** for current status and verification.

# Single-Person Stage 4C — Reporting Audit and Architecture Gate

## Status

Audit completed; implementation paused at the user's explicit shared heat-map/PDF
architecture gate. **Stage 4C is not implemented.** No production or test source
has been changed. This document proposes the coordinated report-model change for
review; it does not claim single-person PDF acceptance.

Starting HEAD: `77cd784 Add single-person Monte Carlo support`.
Initial `git status --short`: empty. Index empty.

## Blocking report contract

The integrated analyzer report is structurally two-dimensional, rather than a
generic report with an optional spouse section:

- `app/export/IntegratedAnalyzerReport.java` requires a non-null `HeatMap`.
  Its cell-count invariant is primary-age count multiplied by spouse-age count.
  Each `Cell` requires an integer spouse age. Empty spouse ages therefore require
  zero cells; nine genuine individual cells cannot be represented honestly.
- `ui/socialsecurity/IntegratedReportContext.java` directly dereferences the
  spouse while capturing result-time person names. It has no composition field.
- `IntegratedAnalyzerReportAdapter` requires `ClaimingStrategyHeatMapModel`,
  retrieves spouse elections and survivor candidates, creates couple election
  tables, and uses couple mortality metadata for weighted reports.
- `app/export/IntegratedAnalyzerPdfExporter.java` places the grid and selected
  summary on the first landscape page. Its grid renderer always draws a spouse
  axis and positions cells by spouse age. Simply hiding the axis does not create
  a valid individual visualization or a valid frozen report.
- `SinglePersonIntegratedView` and `SinglePersonMortalityView` are separate
  completed-result UI paths. They do not use the couple dialog's PDF context and
  export lifecycle. The deterministic frozen presentation carries results,
  primary name, horizon, and death year, but not a complete result-time assumptions
  report. The mortality result carries individual mortality and valuation values,
  but report-wide plan assumptions still require capture at run admission.

This crosses the shared immutable report contract, adapters, first-page layout,
and two UI completion paths. It is not a safe guard-removal-only change. Changing
the common cell shape or coordinate rules in place risks couple selection,
81-cell validation, layout, and existing report consumers. Per the requested
gate, no such change has been made.

## Reporting-path audit

| Path | Current finding | Proposed Stage 4C treatment |
| --- | --- | --- |
| Deterministic integrated PDF | Explicit rejection plus required couple grid/context/election rows | Individual frozen adapter and nine-age visualization; preserve couple path |
| Longevity-weighted integrated PDF | Couple weighted aggregate/context types, spouse mortality and second-death wording | Adapt actual `SinglePersonMortalityAnalysis.Result`; do not manufacture couple aggregates |
| Social Security Only | Single-person view has no export action; existing integrated exporter has a fixed integrated-analysis title | Explicit report title/type for expected-PV SS-only output; never label it deterministic integrated analysis |
| Monte Carlo fixed PDF | Adapter and UI eligibility explicitly reject individuals; chart already has primary-only markers | Remove guards after composition-aware report tests; retain frozen completed-run source |
| Monte Carlo longevity PDF | Adapter calls couple longevity getter, survivor age and couple annual breakdown | Read individual request, neutral living/funded/failed/deceased counts, person's-death terminal wording |
| Paired Monte Carlo PDF | Guard plus nominal second-death explanation | Use existing structural request composition and cached summary; preserve paired denominators and signed deltas |
| Projection PDF | `ProjectionPdfExporter` requires spouse; person-detail loop uses primary/spouse pair | Use actual household members and inspect every emitted assumption/income row before enabling |
| Projection CSV | Explicit rejection; fixed spouse own/survivor/selected-benefit and Roth columns | Preserve couple columns; define an individual schema omitting inapplicable columns, with explicit header tests |
| Results Summary | Composition-aware death/survivor controls already hidden; export button disabled for individual | Preserve existing calculated summaries, enable only after exporters are supported |
| Results table | Uses primary age and household financial values; no direct spouse dependency found in `ResultsView` | Regression verification, not a calculation rewrite |
| Break-even PDF | Dialog and exporter guards; person caption dereferences spouse | Composition-aware frozen plan caption and neutral individual survival wording |
| Break-even chart | Text says probability at least one spouse alive | Use actual context/composition; do not invent a missing survival series |
| Single-person deterministic break-even UI | Supplies empty survival overlay with explicit deferred explanation | Preserve financial break-even math; decide/report availability of actual individual overlay separately |
| Console reporting | Separate report path discovered; not the PDF export framework | Include in final wording/ownership review; no changes at audit gate |

Projection report charts and integrated report tables already have dedicated
layout code. `PdfReportSupport`, PDFBox fonts, page numbering and existing save
workflows can be reused. No dependency, schema, financial formula, mortality or
random-stream change is needed for the proposed reporting work.

The implementation audit must complete row-level checks for pensions, owner
labels, tax/RMD/Roth tables, assumptions and footnotes once the report contract
is approved. The table above identifies entry points and structural dependencies;
it is not a claim that every rendered string has already been visually verified.

## Proposed architecture for approval

1. Add an explicit immutable claiming-visualization variant to the integrated
   report boundary: existing couple grid versus individual claiming-age table.
   Keep the existing couple `HeatMap` and `Cell` payloads and drawing algorithm.
   Individual entries contain only primary claiming age, displayed metric value,
   percent-of-optimal/tier, and selected/current/optimal markers. No spouse
   coordinate, sentinel or single dummy row is allowed.
2. Dispatch the first-page visualization by its explicit variant. Draw a compact
   nine-row or nine-cell individual visualization using the existing palette and
   typography; keep the selected-strategy summary adjacent. Preserve the couple
   first-page geometry and legend exactly. Reuse existing table pagination and
   page header/footer implementation; do not introduce another PDF framework.
3. Capture individual report identity and assumptions from the isolated plan at
   analysis admission. Pair that immutable context with the completed result;
   never read later live-plan fields during export. Add single-result adapters
   for deterministic integrated and the two individual mortality analysis modes.
   Keep existing couple adapters on their existing numerical result types.
4. Add single-view export actions using the existing chooser/error convention.
   Enable only for a current completed result; edits, running work, cancellation
   and failures must not permit mixed-state export. Row/metric selection and
   repeated export must not execute projections or mortality analysis.
5. Adapt other report adapters using established `hasSpouse`, actual member
   collections, `MonteCarloLifetime`, and optional couple population data.
   Remove guards only after the associated report is tested. Preserve all couple
   text, ordering, column widths, titles, filenames and values where possible.

This is a coordinated **report-contract migration**, not a financial-domain or
general PDF-framework rewrite. Approval is requested because the shared
visualization/report contract and first-page dispatch must change. The intended
implementation isolates that risk rather than weakening the existing grid's
invariants or forcing individual results through couple cells.

## Expected implementation boundary

Core shared contract/layout: `IntegratedAnalyzerReport`,
`IntegratedAnalyzerPdfExporter`, and composition-aware frozen context/adapters.
Single-view export integration: `SinglePersonIntegratedView`,
`SinglePersonMortalityView`, with their run-admission context capture.
Localized exporters: `MonteCarloPdfReportAdapter`, `ProjectionPdfExporter`,
`ProjectionCsvExporter`, `BreakEvenPdfExporter`, `BreakEvenPdfChart`.
Eligibility/actions: both Monte Carlo views, `ResultsSummaryView`,
`BreakEvenAnalysisDialog`. Supporting report models only if required to carry
explicit composition. Existing couple UI heat-map classes should remain intact.

Tests would cover immutable report capture, nine-age visualization, every
individual export mode, actual financial values, absence of spouse/survivor rows,
stale-state and no-rerun behavior, persistence before export, and couple report
model/image regressions. Exact final file count remains to be established during
implementation; no speculative production files have been created.

## Regression and visual verification plan

Capture couple deterministic/weighted report models and rendered pages before
the contract migration. Compare monetary strings, all 81 cells, selected and
optimal markers, tiers, tables, page counts and page images after the change.
Ignore generated timestamps only through a fixed test clock/context, not by
discarding financial fields. Preserve existing expected values.

Use existing PDFBox text extraction and `PDFRenderer` preview infrastructure.
Generate individual projection, deterministic integrated, SS-only expected-PV,
weighted integrated, fixed/longevity Monte Carlo, fixed/longevity paired Monte
Carlo and applicable break-even reports from a saved/reloaded realistic plan.
Inspect every page, not merely first-page thumbnails. Verify no blank spouse
sections, readable nine-age visualization, pagination, totals matching frozen
UI results and unchanged couple pages. Include repeated-export invocation tests.

## Verification performed at this gate

Bundled IntelliJ Maven with the mandatory workspace-local repository ran:

```text
IntegratedAnalyzerPdfReportTest
IntegratedAnalyzerPdfStateTest
MonteCarloPdfExportTest
ProjectionPdfExportTest
BreakEvenPdfExportTest
```

**22 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, 16.715 seconds.**
Log: `target/stage4c-reporting-audit.log`.

These are existing-report audit regressions, not Stage 4C implementation tests.
No full/non-benchmark suite was rerun because production/tests are unchanged and
implementation is stopped at the gate. The supplied Stage 4B baseline remains
1,735 tests, zero failures/errors, 13 skipped; it is not a new Stage 4C result.
No couple golden, deterministic, seeded Monte Carlo, or single-person analyzer
values were modified. No new numerical-regression claim is made from this audit.

PDFs/previews visually inspected for Stage 4C: **0**. End-to-end single-person
export acceptance: **not performed; current guards remain in place**.

`git diff --check`: passed. Since this report is untracked, it was additionally
checked for trailing whitespace. Exact final file boundary: **one new report**.

Exact final `git status --short`:

```text
?? SINGLE-PERSON-STAGE-4C-REPORTING.md
```

Nothing was staged, committed or pushed. No new-plan UI redesign was started.
Implementation and all single-person export acceptance remain pending approval
of the report-contract migration above. No completion marker is appropriate.

---

# Approved Stage 4C implementation

The audit above is retained as historical design input. The user approved its coordinated report-model migration. This section supersedes its paused/not-implemented status; final verification totals and inventory follow below.

## Starting state and boundary

Implementation started at `77cd784 Add single-person Monte Carlo support`. The only initial working-tree entry was the untracked audit document `SINGLE-PERSON-STAGE-4C-REPORTING.md`; the index was empty. No financial, mortality, random-generation, projection, persistence, or analysis-service implementation was changed. Changes are confined to report models, adapters, renderers, export availability, and tests.

## Report architecture and frozen context

`IntegratedAnalyzerReport.Visualization` explicitly permits two representations. Existing `HeatMap`/`Cell` retain their two-dimensional invariant and spouse coordinate. New immutable `Individual` contains exactly nine ordered `IndividualAge` records, ages 62–70. An individual record contains only age, formatted value, percent of optimal, tier, and optimal/selected/current flags. It has no spouse coordinate or survivor data. The original couple grid rendering method remains unchanged.

`IndividualAnalyzerReportAdapter` consumes completed deterministic or mortality-weighted results. It formats existing metrics and computes only presentation percentages/tier classification, using unrounded values before display rounding. It does not execute projections or recalculate financial results. Deterministic reports retain the existing After-Tax Estate metric; SS-only retains expected Social Security PV; weighted integrated retains expected PV After-Tax Estate. These objectives are not substituted for one another. Selection changes choose an existing result, without rerunning analysis.

`IndividualReportContext` captures immutable display rows from the isolated plan at analysis admission. It retains Primary identity, plan/economic/tax assumptions, actual income, ownership/account amounts, expenses and Roth configuration. Custom taxable-income targets are reported as targets rather than incorrectly presenting their unused fixed-amount field. Completed mortality results supply mortality-table metadata, conditioning/valuation dates and factors. SS-only PDFs omit unrelated full-plan assumptions. No mutable plan or JavaFX control survives context capture.

Single-person views retain the context with the successful result. Export is enabled only for a current completed result with no conflicting job. Input changes disable export; later edits cannot change an already captured report. Native save cancellation, extension/overwrite handling, background writing and error alerts follow the existing exporter pattern. The export control uses shared InputHelp. No analysis service is called by export.

## Individual PDF presentation

The first landscape page contains a nine-row Primary claiming-age visualization, the existing heat/tier palette and legend, exact-optimum stars, selected-row outline, current designation, formatted monetary value and percentage. A selected-strategy summary sits alongside it. There is no spouse axis. Remaining assumptions, rankings, current/selected metrics and methodology flow below on subsequent pages with repeated table headers. Individual pagination uses natural continuation; couple page breaks remain unchanged. Existing titles/styles are retained, with a distinct Social Security Only title.

Visual review corrected literal absent dates (now projection start/no configured end/not scheduled), unnecessary individual section page breaks, and missing custom Roth-target wording. No font reduction or financial change was used to solve layout issues.

## Other reporting paths

- Projection PDF iterates real household members, includes actual Primary pension/account information, and omits couple survivor assumptions for an individual.
- Results Summary enables the existing completed-projection PDF and CSV workflow.
- Individual CSV omits spouse own benefit, both survivor candidate columns, spouse selected benefit and spouse Roth conversion. Remaining household/Primary columns retain calculated values. Couple CSV retains its original header and row order. Consumers should use CSV column names because individual output intentionally has fewer columns.
- Console household output stops after Primary when no spouse exists.
- Break-even PDF uses Primary-only claiming labels and individual survival wording. Existing crossover calculations are untouched. The preview intentionally has no mortality overlay inputs and clearly reports survival probability unavailable; no probabilities are fabricated.
- Monte Carlo fixed, longevity and both paired modes enable export from completed frozen runs. Individual mortality assumptions use only Primary; annual tables show requested/living/funded/failed-living/deceased populations. Terminal descriptions refer to the person's death, not second death. Paired denominators, signed differences, taxes and percentiles still come from cached authoritative summaries. Couple table/wording branches remain intact.

## Tests and checkpoint evidence

No existing couple PDF test expectations or financial golden resources were changed. Old tests that explicitly expected single-person export rejection were updated to assert the newly authorized successful export. No financial assertion was weakened.

| Checkpoint | Tests | Failures/errors | Skipped | Log |
|---|---:|---:|---:|---|
| Original read-only PDF audit | 22 | 0/0 | 0 | target/stage4c-reporting-audit.log |
| 1: individual model | 23 | 0/0 | 0 | target/stage4c-checkpoint1.log |
| 2: frozen context | 24 | 0/0 | 0 | target/stage4c-checkpoint2.log |
| 3: rendering | 25 | 0/0 | 0 | target/stage4c-checkpoint3.log |
| 4: export integration | 96 | 0/0 | 0 | target/stage4c-checkpoint4.log |
| Broader focused regressions | 317 | 0/0 | 5 | target/stage4c-focused.log |
| Initial non-benchmark suite | 1,719 | 0/0 | 9 | target/stage4c-nonbenchmark.log |

`IndividualReportTest` covers the exact nine-age invariant, immutable collections, no spouse coordinate, percent/tier rounding boundaries, exact optimum, current/selection distinction, frozen account values, actual analysis-to-PDF text, all three individual analyzers, and stale export eligibility. `Stage4cPdfChecks` extracts PDF text and optionally renders every page. `SinglePersonHouseholdUiTest` now exports the reloaded UI-created plan, projection PDF/CSV, fixed/longevity Monte Carlo and both paired modes. Repeated exports retain analysis invocation/generation counts. Existing single-person financial fixtures and all couple golden/seed tests remain authoritative.

## Acceptance method and reproduction

The acceptance workflow is automated against actual JavaFX views/controllers and domain-backed dialog converters, not a mocked report-only pipeline: construct Primary through Household UI, save, open a fresh controller, reload/edit/resave/reload, project, run each of the three single-person analyzers, select results and prepare/export completed reports. Monte Carlo fixed/longevity and paired modes are similarly exercised. The views' stale-state eligibility is asserted after input edits. PDFs are then manually inspected through rendered page images.

The native OS file chooser itself was not manually clicked in this environment; tests call the same completed-report capture/export boundary after exercising the real views. No claim of a human-driven end-to-end desktop session is made.

To repeat interactively: create Primary only in Household; use DOB 1965-02-01 and a mortality category, Primary monthly FRA Social Security $3,000/claim age 67, a $1,000 monthly pension, Primary brokerage $400,000, traditional IRA $700,000, Roth IRA $100,000, recurring spending $40,000/year, start 2027-01-01, horizon 20 years and Single filing status. Save/reopen. Run projection and export from Results Summary. Run each individual analyzer, select an age and Export PDF. Run fixed and longevity Monte Carlo and export. Save a baseline to enable paired comparison; export both modes. Verify the nine ages, selected/current/optimal markers and Primary-only assumptions. Edit an assumption and verify export is disabled until rerun.

Generate previews with `-Dsingle.stage4c.preview=true -Dtest=IndividualReportTest,SinglePersonHouseholdUiTest test` using the prescribed IntelliJ Maven and repository-local cache. PDFs and every page image are under `target/stage4c-preview`. Each `.pdf.pages` file gives the current authoritative page count; older surplus PNGs from prior layout iterations are not report pages.

## Deferred work and limits

No new-plan wizard, financial-model change, mortality change, random-stream change or analysis optimization was introduced. Existing same-year AGI IRMAA semantics remain unchanged. The individual weighted report displays the aggregate metrics supplied by its existing result model; it does not invent expected tax/RMD/Roth metrics absent from that result. General tagged-PDF accessibility and redesign of legacy report pagination are outside this milestone. Existing absent break-even mortality-context behavior remains explicit. Native save-dialog interaction remains a user acceptance check.

## Optional benchmark-inclusive attempt

The complete benchmark-inclusive suite was attempted in `target/stage4c-full.log`. It was stopped after roughly ten minutes while still executing the existing 5,000-world paired benchmark. A JVM thread dump (`target/stage4c-full-threads.txt`) showed normal projection/tax work, not a reporting deadlock. No completed test had reported a failure/error. This attempt has no successful full-suite total and is not claimed as a pass. The mandatory complete non-benchmark suite was then rerun after the final report-text/help changes, with preview generation enabled. Benchmark tests or their expectations were not disabled or edited in source.

## Exact file boundary

23 files: 18 production files (15 modified, 3 new), 4 test files (2 modified, 2 new), and this report. Production changes are confined to export/presentation layers. There are no production domain, Monte Carlo execution/aggregation, persistence, financial-rule, or random-stream changes.

Exact working-tree inventory (also the final `git status --short`, provided no additional changes are made):

```text
 M src/main/java/com/daviddunn/retirementplanner/app/export/BreakEvenPdfChart.java
 M src/main/java/com/daviddunn/retirementplanner/app/export/BreakEvenPdfExporter.java
 M src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerPdfExporter.java
 M src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerReport.java
 M src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionCsvExporter.java
 M src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java
 M src/main/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenAnalysisDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/console/ConsoleReportPrinter.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPdfReportAdapter.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IntegratedAnalyzerReportAdapter.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonMortalityView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java
 M src/test/java/com/daviddunn/retirementplanner/domain/projection/SinglePersonCoreProjectionTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/SinglePersonHouseholdUiTest.java
?? SINGLE-PERSON-STAGE-4C-REPORTING.md
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualAnalyzerReportAdapter.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualPdfExportAction.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualReportContext.java
?? src/test/java/com/daviddunn/retirementplanner/Stage4cPdfChecks.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualReportTest.java
```


Couple regression interpretation: existing couple PDF test sources/expectations are unchanged, the original 9x9 drawing method is unchanged, and representative generated couple reports were visually inspected. This is content/layout regression evidence, not a claim of byte-identical PDF files with identical generated timestamps. Existing financial and seeded expected values were not updated.

## Final verification and visual acceptance

Final complete non-benchmark run: **1,719 tests, 0 failures, 0 errors, 9 existing skips; BUILD SUCCESS**. Log: `target/stage4c-final-nonbenchmark.log`; elapsed 19:46 on this run. Command used the required IntelliJ Maven, `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`, `-Dtest=*,!**/*Benchmark*`, and `-Dsingle.stage4c.preview=true`. This run includes every final production/test change and regenerated previews. The large timing variation from the earlier 2:01 run is not presented as a reporting performance measurement.

The nine skips are existing opt-in audit/preview tests: DavidClaimingAudit (1), DavidMedicareDeathAudit (1), SocialSecurityProductionMortalityIntegration (1), InputTooltipPreview (1), MonteCarloComparisonPreview (1), MonteCarloPdfPreview (1), MonteCarloPreview (3). No skip was added for Stage 4C. Stage 4C preview rendering was enabled independently and executed.

Final-run examples: IndividualReportTest 5/0/0/0; SinglePersonHouseholdUiTest 10/0/0/0; IntegratedAnalyzerPdfReportTest 6/0/0/0; MonteCarloPdfExportTest 8/0/0/0; SinglePersonMonteCarloTest 9/0/0/0; SinglePersonMortalityAnalysisTest 8/0/0/0 (tests/failures/errors/skips). The earlier broader focused run was 317 tests, zero failures/errors and 5 existing skips. Existing couple golden, deterministic, survivor, report-model, heat-map and seeded Monte Carlo assertions passed without changing expected values. Stage 4B single-person analysis assertions also passed unchanged.

**Visual inspection: 12 distinct single-person PDFs (62 pages) and 2 couple PDFs (27 pages), all pages inspected.** Files below are relative to `target/stage4c-preview/`; each has corresponding numbered PNG pages rendered at 100 DPI. Duplicate repeated-export files were not counted as separate visual fixtures.

| PDF | Pages inspected | Findings |
|---|---:|---|
| individual-deterministic.pdf | 1–4 | Nine ages, exact optimum/current/selection, correct custom Roth target, current/selected financial metrics, repeated ranking headers; no spouse data. |
| individual-INTEGRATED.pdf | 1–3 | Expected-PV estate metric, nominal/asset columns, actual mortality metadata and lifetime notes readable. |
| individual-SOCIAL_SECURITY_ONLY.pdf | 1–3 | Own-benefit PV/nominal values, relevant SS inputs only, nine tiers, mortality/valuation dates, no spouse/survivor terms. |
| ui-individual-deterministic.pdf | 1–4 | Actual UI-created/reloaded plan, selected age 64 distinguished from current 67 and optimum 65; pension/accounts/RMD/Roth/tax totals readable. |
| ui-individual-INTEGRATED.pdf | 1–3 | Selected age 65, current 67 and optimum 66 visible; table and Primary mortality assumptions fit. |
| ui-individual-SOCIAL_SECURITY_ONLY.pdf | 1–3 | UI-completed expected-PV ranking and own-benefit assumptions agree with captured results. |
| single-projection.pdf | 1–16 | Primary pension/account assumptions, SS markers and household totals; tax/RMD/Roth/Medicare charts and tables readable, no fabricated person rows. |
| single-mc-Fixed Lifespan.pdf | 1–3 | Funding probability, fan chart, terminal distributions and sample/assumption context fit. |
| single-mc-Longevity-Adjusted.pdf | 1–7 | Individual lifetime description, Primary factor/category, living/funded/failed/deceased populations and late small-sample flags retained. |
| single-mc-comparison-fixed.pdf | 1–4 | Paired funding states, zero reference, signed axis, monetary denominator, annual rows and tax-direction note readable. |
| single-mc-comparison.pdf | 1–6 | Individual longevity paired populations, terminal wording and zero identical-strategy differences retained. |
| single-break-even.pdf | 1–6 | Primary-only labels, all four existing crossover charts, clear unavailable mortality-context explanation. |
| couple-deterministic.pdf | 1–19 | Original 9x9 axes/grid, colors, selection, assumptions, election/ranking tables and metrics retained. |
| couple-weighted.pdf | 1–8 | Original couple grid/legend, selected summary, survivor assumptions and weighted table layout retained. |

No clipped text, overlapping table/chart labels, broken glyphs, fabricated spouse sections or accidentally empty PDF pages were observed. Table headers repeat on continuation pages. Partial final pages are retained where content naturally ends: notably the SS-only methodology occupies a short final page. This is an intentional continuation, not a reserved omitted-spouse area. Identical-strategy Monte Carlo charts retain the existing tiny symmetric zero range and rounded dollar ticks; changing general axis formatting is deferred. Report rendering remains vector PDF drawing through the existing framework; PNGs are inspection artifacts only.

Acceptance result: automated real-JavaFX create/save/reload/project/analyze/report workflow passed, followed by manual rendered-page inspection. The native file chooser was not manually exercised. User-facing acceptance steps and this limitation are documented above.

Final `git diff --check`: passed (`git -c core.safecrlf=false diff --check`, avoiding Windows safe-CRLF conversion diagnostics). New files were additionally checked for trailing whitespace. Final index is empty. The exact 23-file status listing above remains current. Nothing was staged, committed or pushed. No new-plan wizard or financial-model enhancement was started.
