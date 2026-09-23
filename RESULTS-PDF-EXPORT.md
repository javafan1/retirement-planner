# Results PDF export update

## Audit and preserved behavior

ResultsSummaryView's existing Export action offers CSV or PDF. The PDF-only path is exportProjectionPdf -> ProjectionPdfExporter.export. CSV has its own ProjectionCsvExporter, unchanged columns/formatting/filename/entry point. No shared export code needed refactoring.

The prior PDF used PDFBox directly: portrait US Letter, Helvetica/Helvetica Bold, blue/purple/orange/green summary accents, generated date, key results, key assumptions and a ten-column annual table. It had no charts, no JavaFX screenshots, no Baseline section, no page numbers, and no detailed account/income/expense inventories beyond summary/table values. Its 570-point annual table exceeded the 540-point portrait content width.

All existing useful content remains: ending/peak investable assets, non-investable assets, net worth, estate values, final-year effective tax rate; investment/general/healthcare inflation, SS COLA, death/survivor settings, Roth configuration; and every existing annual-table column (Year, Age, Beginning, Growth, Income, Expenses, Tax, Ending, Non-Invest., Net Worth). The former investable-estate result is present in the After-Tax Estate chart; the executive heir-value card now matches the Results UI's broader heir value including non-investable assets. This distinction is explicitly labeled.

## Report structure

1. Executive Results Summary: six readable cards, including the existing UI average effective tax rate and heir value; final-year rate retained. Plan filename (or Unsaved retirement plan), actual period and generation date.
2. Existing valid Baseline Comparison, when supplied by the Results view: Investment Growth, Total Income, Total Taxes, Peak Annual Tax, Investable Assets, Total Net Worth and Investable After-Tax Estate; Baseline/Current/Change values. Consumes the already-displayed immutable ProjectionComparison, never invokes compareProjections or Break-Even analysis for export.
3. Projection Charts: one large vector chart per selector metric.
4. Projection Summary: original annual columns and formatting, fitted to the page, with repeated headers on continuation pages.
5. Key Assumptions: original entries plus household names/DOBs, independent SS claiming ages, source full-retirement monthly benefit, exact saved start date/benefit valuation year, filing status, tax growth/rates/adjustments and Roth stop rule. Unscheduled tax changes say not scheduled.

All pages are landscape US Letter (792 x 612 points). Existing PDF font/color conventions remain; page numbers are added. The representative report has 17 pages. No PDF feature was added to Break-Even Analysis.

## Shared data and exact chart order

ResultsSummaryView retains the exact immutable ProjectionChartModel it already prepares for the interactive chart and hands it to ProjectionPdfReport. The existing computed average tax rate and heir value are captured without changing their calculations. Neither renderer queries live JavaFX nodes. The PDF enumerates ProjectionChartMetric.values(), maintaining the existing outcome-first order and automatically including every available metric:

| Order | Exact metric title | Existing authoritative source in ProjectionChartModel |
|---|---|---|
| 1 | Investable Assets | ProjectionYear.getEndingInvestableAssets |
| 2 | Total Net Worth | Prepared ending investable + matching NonInvestableAssetProjection total |
| 3 | After-Tax Estate | getAfterTaxEstateValue |
| 4 | Total Income | getGuaranteedIncome |
| 5 | Expenses (Projection Summary) | getAnnualExpenses |
| 6 | Total Income Taxes | getTotalIncomeTax |
| 7 | Social Security Benefits | getSocialSecurityResult().householdBenefit() |
| 8 | Investment Growth | getInvestmentGrowth |
| 9 | Gross Portfolio Withdrawals | getPortfolioWithdrawal |
| 10 | Non-Investable Assets | Matching NonInvestableAssetProjection.getTotalValue |
| 11 | Medicare Premiums | getAnnualMedicarePremium |
| 12 | Actual Roth Conversions | getRothConversion |
| 13 | RMD Distributed During Projection | getRmdDistributedInProjection |

The PDF reads these prepared values; it does not implement these calculations. Dollars retain exactly the basis used by the interactive chart and source projection. Axis scaling and currency formatting are presentation only.

## Rendering

ProjectionPdfCharts draws PDFBox vector paths, filled polygons, text and axes. No chart is rasterized or screen-captured. Tests verify the report has no image XObjects. PNG files are review previews rendered FROM the finished PDF.

Investable Assets uses actual prepared Taxable/Cash, Tax-Deferred and Roth component balances. The orange series uses the authoritative reported total. The shared compositionComplete flag governs the stack, including the finalized inclusive $0.01 display-only allowance and account-completeness checks. No PDF tolerance or classification implementation was added. Material failures show the authoritative total and “Detailed asset composition is unavailable for this projection.”

Prepared claiming events use dark dashed vertical strokes and readable labels. Roth and RMD periods use the model's actual executed-transaction periods, calendar-year bands, translucent purple/green shading, direct labels and independent edge indicators so overlap remains visible over stacked fills. Annotations occupy collision-managed lanes above the plot; they do not cover the financial series. Legends identify the total/categories and period treatments. No break-even markers, difference series or mortality row is included.

## Files changed in this task

New:
- src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfReport.java
- src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfCharts.java
- src/test/java/com/daviddunn/retirementplanner/ui/charts/ProjectionPdfExportTest.java
- RESULTS-PDF-EXPORT.md

Modified:
- src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java
- src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java (PDF input capture and PDF call only; no calculation or interactive behavior change)

No CSV source, CSV test, financial-domain, chart-model, chart-renderer/CSS, Break-Even or mortality file was changed in this task.

## Validation and visual review

Two new PDF tests cover: all 13 charts; exact UI/prepared chart-model equality; valid composition and authoritative total; claiming events; Roth/RMD overlap; exact 2027–2056 bounds; executive summary/assumptions/table text; vector output; nonblank landscape pages; material-composition fallback; preserved Baseline values; plan JSON unchanged; and equivalent PDF text/content when exporting after selecting Investable Assets versus Social Security Benefits.

Focused suite: 26 tests, zero failures/errors. Includes ProjectionPdfExportTest (2), ProjectionChartModelTest (5), ProjectionChartReconciliationTest (4), ProjectionChartViewTest (4), RetirementPlannerMvpIntegrationTest (1), ProjectionYearReportingAgeTest (10). The latter two are all existing test classes referencing ProjectionCsvExporter. Neither was edited. An additional PDF test verifies byte-for-byte identical CSV output before/after both PDF exports. CSV source remains unchanged.

Full non-benchmark Maven suite: **1,373 tests, zero failures/errors, 3 skipped**, BUILD SUCCESS. Log: target/projection-pdf-full.log.

Every page of the representative 17-page PDF was rendered and visually inspected, including the entire annual table, assumptions, all 13 charts, labels/legends and page breaks. The separate Baseline/fallback summary was also inspected. The sample uses synthetic Alex/Sam, 2027–2056, Social Security, pension, expenses, taxes, taxable/traditional/Roth accounts, modeled home equity and overlapping Roth/RMD years. It is not the unavailable RetirementPlan0917.json.

Review artifacts:
- target/projection-pdf-preview/retirement-projection.pdf
- target/projection-pdf-preview/page-01.png through page-17.png
- target/projection-pdf-preview/contact-0.png (pages 1–6)
- target/projection-pdf-preview/contact-6.png (pages 7–12)
- target/projection-pdf-preview/contact-12.png (pages 13–17)
- target/projection-pdf-preview/fallback-baseline.pdf (separate test fixture)
- target/projection-pdf-preview/baseline-summary.png

No commits made. No financial calculations, projection runs per chart, persistence, CSV or interactive chart behavior changed. Existing standard Helvetica PDF font coverage is retained; glyphs outside that font's encoding use a question-mark fallback rather than aborting export.