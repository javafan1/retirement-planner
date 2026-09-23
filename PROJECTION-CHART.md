# Interactive Projection Chart

## Layout audit and preserved components

The user's main Results screen is `ResultsSummaryView`, hosted in MainWindow's **Summary** tab. The separate `ResultsView` is hosted in **Yearly Detail** and does not contain the two small charts or Key Assumptions. It is intentionally unchanged.

ResultsSummaryView originally had:

- Top Results header, projection range, Recalculate Projection and Export actions.
- Center VBox: existing metric cards, optional Baseline Comparison section, Projection Summary section/table, then an HBox containing two small charts.
- Right: existing Key Assumptions VBox and its internal scroller, including Planning Horizon, Social Security, Economic Assumptions and Roth Conversions.

The two replaced widgets were `assetChart` (LineChart, **Total Investable Assets**) and `compositionChart` (StackedAreaChart, **Investable Asset Composition**). Their private builders/population code were removed. The new chart occupies the center content width directly before the unchanged Projection Summary section. The center content now has a fit-to-width vertical scroller so the table remains accessible without squeezing it. Header, cards, Baseline Comparison controls, Key Assumptions location, table columns/calculations/actions, MainWindow tabs and the separate Charts tab are preserved.

## Architecture and reuse

`ProjectionChartModel` is an immutable JavaFX-independent presentation snapshot of the already loaded projection, non-investable projections, and frozen person/election metadata. It has no projection-engine/controller/persistence dependency. Selection is local to `ProjectionChartView`; it chooses among prepared values, with Investable Assets the initial default. No plan preferences are persisted.

`TimelineClaimMarkers` extracts the existing Break-Even plot-node rendering, tooltips and collision layout verbatim into a shared helper. `chart-timeline.css` contains the previous darker dashed strokes and bold labels. BreakEvenChart delegates marker rendering to this helper; its financial series, sustained marker, probabilities and probability-row layout remain unchanged. Compatibility style classes are retained for existing tests. No Break-Even calculation code changed.

Claim events use `BreakEvenPlanSummary`'s existing immutable `retirementClaimDate` metadata, which captures the exact SocialSecurityIncome start date used by the projection provider. The chart does not derive DOB-plus-age or assume January 1. Labels contain year, actual person name and claiming age, without Baseline/Current/Both-plans identities. Events outside the loaded projection's calendar horizon are excluded.

## Dropdown metrics and sources

| Choice | Authoritative source |
| --- | --- |
| Investable Assets (default) | `ProjectionYear.getEndingInvestableAssets()` |
| Total Net Worth | Ending investable assets + same-year `NonInvestableAssetProjection.getTotalValue()`, matching existing table semantics |
| After-Tax Estate | `getAfterTaxEstateValue()` |
| Total Income | `getGuaranteedIncome()` |
| Expenses (Projection Summary) | `getAnnualExpenses()`; not silently redefined to include tax/Medicare |
| Total Income Taxes | `getTotalIncomeTax()` (federal + state income taxes) |
| Social Security Benefits | `getSocialSecurityResult().householdBenefit()`; annual, not cumulative |
| Investment Growth | `getInvestmentGrowth()` |
| Gross Portfolio Withdrawals | `getPortfolioWithdrawal()` |
| Non-Investable Assets | Same-year `NonInvestableAssetProjection.getTotalValue()` |
| Medicare Premiums | `getAnnualMedicarePremium()` |
| Actual Roth Conversions | `getRothConversion()` |
| RMD Distributed During Projection | `getRmdDistributedInProjection()` |

Dollar basis: exactly the loaded projection values, with no inflation adjustment. The existing table caption says "today's dollars", but its bindings directly display projected values without deflation. That pre-existing caption is unchanged under this task's table-preservation constraint; the chart explicitly says it uses the same values as the table.

## Composition and interaction

Composition uses each ending account snapshot's existing `Account.getProjectionAssetType()`:

- TAXABLE → Taxable / Cash (includes brokerage, checking/savings and other accounts already classified taxable).
- TAX_DEFERRED → Tax-Deferred (existing traditional classifications).
- ROTH → Roth.

Retained non-qualified assets are included once in Taxable / Cash, matching the old chart. No new account classification is introduced. The stacked area shows these components; an orange path and per-year points independently show the authoritative total. The compact legend identifies categories and total.

Account allocations retain sub-cent precision while ProjectionYear rounds aggregate ending assets to cents. Reconciliation therefore uses that existing cent precision, without changing the raw component values. The representative real projection reconciled for every year. If account detail genuinely fails to reconcile (including incomplete legacy fixtures), the chart explicitly falls back to the reported total line rather than inventing a balancing category. Percentages use unrounded component/total values and BigDecimal division, rounding only for displayed percentages; a zero total yields zero percentages.

Hover a total point for total, composition amounts/percentages, actual Roth conversion and actual projected RMD. For other metrics, hover the annual point for its value plus Roth/RMD amounts. Clicking a point selects it in the chart's local readout, whose tooltip retains full details. This does not change the projection or the table selection.

## Periods and overlaps

Roth periods include only years with actual executed `getRothConversion() > 0`. Requested-but-unexecuted conversion years are excluded.

RMD periods include only actual `getRmdDistributedInProjection() > 0`. The statutory requirement and RMD distributed before the projection start are not substituted; age alone never creates an event.

The pure period helper sorts active calendar years and combines only consecutive integers. Zero-amount years and missing calendar years split periods. A single year remains a single-year period; no end-of-horizon continuation is implied.

Each period fills its full annual column(s), from half a year before the first to half a year after the last, clipped to the displayed horizon. Roth has a subtle purple tint and top stripe; RMD has a subtle green tint and bottom stripe. Overlapping years keep both stripes and both faint tints. Grouped labels directly above the plot identify each period and stripe location. Per-year hover values identify both actual amounts, never implying that RMD dollars were converted.

There is no comparison series, crossover/break-even marker, difference zero line, or mortality row.

## Files

New production:
- `ui/charts/ProjectionChartMetric.java`
- `ui/charts/ProjectionChartModel.java`
- `ui/charts/ProjectionChartPresentation.java`
- `ui/charts/ProjectionChartView.java`
- `ui/charts/ProjectionPlotDecorations.java`
- `ui/charts/TimelineClaimMarkers.java`
- `src/main/resources/css/chart-timeline.css`
- `src/main/resources/css/projection-chart.css`

Modified production:
- `ui/views/ResultsSummaryView.java`
- `ui/breakeven/BreakEvenChart.java`
- `src/main/resources/css/break-even.css` (claim styles extracted into shared stylesheet)

New tests:
- `ui/charts/ProjectionChartFixtures.java`
- `ui/charts/ProjectionChartModelTest.java`
- `ui/charts/ProjectionChartViewTest.java`

This report is new. Other existing uncommitted changes are preserved. No commit was made.

## Validation and visual review

Focused: `ProjectionChart*Test,BreakEven*Test,PlanningHorizonLayoutTest,ResultsSummary*Test` — **60 tests, zero failures/errors/skips** (`target/projection-chart-focused.log`).

Tests cover every metric, composition/categories/percentages/zero/missing detail, exact SS dates and horizon filtering, actual-vs-requested conversions, actual-vs-statutory/preprojection RMD, periods/gaps/overlap, immutable data, selector values, click/hover information, empty/single-year states, no comparison/mortality features, marker collision/width checks at 1400/900/500 chart widths, unchanged cards/header/sidebar/table ordering, and unchanged plan JSON after metric selection. Existing Break-Even, Planning Horizon and Results Summary tests pass without changed expectations.

The UI test also builds one synthetic household projection using the actual engine, verifies composition reconciliation throughout its 25 years, and captures `target/projection-chart-preview.png`. That desktop preview was visually inspected: all five existing cards and the right-hand Key Assumptions remain, the new chart shows stacked categories and total, SS labels are legible, overlapping Roth/RMD stripes are visible, and Projection Summary is immediately below. The screenshot contains synthetic data, not the user's personal plan. It captures the Results content; MainWindow's unchanged tab shell is outside that preview.

Full non-benchmark suite: **1,366 tests, zero failures/errors, 3 skipped; BUILD SUCCESS** (`target/projection-chart-full.log`).

No projection, Social Security, Roth, RMD, mortality, account classification, persistence or Break-Even calculation behavior changed. The only engine invocation added is test-fixture setup, never a chart interaction.
