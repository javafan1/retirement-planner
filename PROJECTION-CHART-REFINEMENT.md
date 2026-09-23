# Projection Chart refinement audit

## Current visualization policy (supersedes the original fallback policy below)

Following review, detailed composition is display-reconciled when its unrounded sum differs from the authoritative total by **at most $0.01, inclusive, in either direction**. Two independent cent-rounding steps (base withdrawal and ending aggregate) can each contribute half a cent. This fixed currency-unit allowance does not scale with assets, account count, years or display formatting. A residual just above one cent fails, as does a dollar. Any larger accumulated numerical artifact remains conservatively unavailable rather than receiving an expanded tolerance.

Before numeric acceptance, each year's snapshots must match the supplied plan account roster exactly once by source object identity (the existing projection preserves these references). Duplicate roster entries, duplicate snapshots, omitted accounts including zero-balance accounts, unexpected accounts, unsupported classifications, invalid ownership and invalid balances fail. Distinct accounts with the same name remain distinct. BigDecimal has no NaN/infinity values. Retained non-qualified assets are validated and included once.

The roster is supplied by ResultsSummaryView; it is not inferred from the year's snapshot list. UI test fixtures use shared account identities to represent the same portfolio across years.

The orange line retains reported Investable Assets. Stack values remain the actual unrounded categories, with no residual redistribution or invented category. Ordinary currency formatting and the existing material-failure message remain unchanged. Projection-domain calculations were not modified; the historical rounding finding below remains valid.

**Validation:** focused suite 61 tests, no failures/errors; full non-benchmark suite 1,371 tests, no failures/errors, 3 skipped. No commits made.

**Result:** all 30 synthetic 2027–2056 years now display composition; no years fail. The exact 2030 residual remains $0.00592995119494. Tests verify original component sum and authoritative total independently, as well as zero/exact, demonstrated sub-cent, positive/negative one-cent boundary, just-outside boundary, dollar discrepancies, missing zero-balance accounts, duplicates, unexpected accounts, all 11 account types and permitted owners.

Files changed in this follow-up: new `ui/charts/CompositionDisplayReconciliation.java`; `ProjectionChartModel.java`; `ui/views/ResultsSummaryView.java` (supplies roster); chart fixtures/model/reconciliation/view tests; this report. No renderer, period/claiming styles, chart sizing or financial model changes.

Preview: `target/projection-chart-rounding-preview.png` shows the exact synthetic 30-year rounding fixture. It intentionally has pension income rather than Social Security; the existing `target/projection-chart-preview.png` continues to exercise claiming markers and overlapping Roth/RMD periods.

The reported screenshot used RetirementPlan0917.json (2027–2056). That file is not present; workspace plan.json is a different scenario. Findings below are independently reproduced with a valid synthetic 2027–2056 projection, not attributed to the unavailable file.

## Reconciliation finding

All account categories are already included. Reported ending assets are calculated in ProjectionEngine from beginning assets + cent-rounded investment growth - reported portfolio withdrawals + retained surplus, followed by cent rounding. The reported base withdrawal is rounded to cents, while the account allocator uses the unrounded additional cash-flow withdrawal. Pension COLA can introduce fractional cents. Account snapshots therefore retain a different, full-precision balance. Proportional growth allocation also uses finite-precision account shares.

Synthetic 2030:

| Value | Dollars |
|---|---:|
| Reported Investable Assets | 2,211,334.91 |
| Taxable/Cash | 339,561.20378730022085 |
| Tax-Deferred | 1,436,303.04393498502088 |
| Roth | 435,470.65634776356333 |
| Composition sum | 2,211,334.90407004880506 |
| Raw residual | 0.00592995119494 |

The unrounded withdrawal path differs by 0.003273125; final aggregate rounding contributes another 0.00265682619494. This exceeds the existing half-cent reconciliation precision. It is a projection rounding inconsistency, not missing detail or a chart mapping defect. No projection/domain calculations were changed.

Representative synthetic audit (long decimals abbreviated here only):

| Year | Reported | Composition sum | Residual | Context |
|---|---:|---:|---:|---|
| 2027 | 2,129,865.64 | 2,129,865.635500 | +0.004500 | First year, Roth conversion |
| 2030 | 2,211,334.91 | 2,211,334.904070 | +0.005930 | Roth conversion, rejected |
| 2038 | 2,404,124.11 | 2,404,124.108021 | +0.001979 | First RMD |
| 2056 | 2,786,610.12 | 2,786,610.125488 | -0.005488 | Final year, rejected |

Nine of this fixture's 30 years fail reconciliation. The separate desktop-preview fixture reconciles in all 25 years. Stacked composition remains conditional; it is not correct to claim every valid projection now reconciles. Raw residuals are tested without display rounding, with a conservative strict half-cent bound; tolerance was not widened. Fallback text: “Detailed asset composition is unavailable for this projection.”

A separate authorized projection fix should align reported and allocated withdrawal precision, and verify the ending aggregate against the actual ending portfolio. No new account category or additional result detail is necessary. That financial correction is deferred.

## Authoritative categories

Uses AccountType.getProjectionAssetType via Account.getProjectionAssetType:

- Taxable/Cash: Brokerage, Checking, Savings, plus ending retained non-qualified household assets exactly once.
- Tax-Deferred: Traditional IRA, Rollover IRA, Traditional 401(k), Traditional 403(b), Inherited Traditional IRA.
- Roth: Roth IRA, Roth 401(k), Inherited Roth IRA.

All permitted Primary, Spouse and Joint ownership is included. No separate Other category exists or was added. Annual excess RMD is a flow, not another balance; it is not added again.

## Presentation changes

Direct Roth Conversion(s) and RMDs labels show actual period years. Full-year shading runs from first year minus 0.5 to last year plus 0.5 on a year-centered axis. Both subtle tints remain; independent edge stripes appear for overlapping periods. No top/bottom-stripe instructional text remains. Labels avoid claiming labels and earlier period labels. The shared Break-Even claiming CSS is reused; Projection moves dashed strokes above area fills so their contrast is preserved. The shared default layout behavior for Break-Even is unchanged. Actual Roth/RMD amounts appear in tooltips only when positive.

Cards, tabs, right assumptions panel, table, chart height/location and all 13 metrics are preserved. No calculations, account classifications, plan state, Baseline or Break-Even behavior changed.

## Files changed in this refinement

- ui/charts/ProjectionChartModel.java
- ui/charts/ProjectionChartPresentation.java
- ui/charts/ProjectionChartView.java
- ui/charts/ProjectionPlotDecorations.java
- ui/charts/TimelineClaimMarkers.java
- resources/css/projection-chart.css
- test ui/charts/ProjectionChartReconciliationTest.java (new)
- test ui/charts/ProjectionChartViewTest.java
- PROJECTION-CHART-REFINEMENT.md (this report)

Tests cover every account type/permitted owner, raw precision boundaries, missing balances, a real-engine fractional cash-flow regression, all years' source reconciliation, direct period labels, existing period gaps/overlap, 13 metrics and unchanged Results layout. Preview: target/projection-chart-preview.png, generated by the JavaFX integration test using synthetic Alex/Sam data, not the unavailable user's plan.
Validation: focused chart/Break-Even/Results tests: 63 tests, zero failures/errors. Full non-benchmark Maven suite: see target/projection-refinement-full.log. No commits made.
