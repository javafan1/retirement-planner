# Break-Even compact layout

## Measured outcome

With the same synthetic 2027–2056 comparison, the previous layout placed the chart 679 px below the content top. The compact layout measured 366 px at the original 1100 px dialog width: approximately **313 px recovered by layout changes**. The new screen-bounded 1200 x 920 preferred dialog gives 349 px above the chart, approximately **330 px total savings**, including reduced wrapping at the wider default.

The chart remains **400 px high** and its plotting region **300 px high**, exactly matching the before measurement. The default dialog previously preferred 1100 x 850; the modest new preferred size remains capped to available screen space. Close stays in the existing bottom action area.

At the reviewed desktop size, the complete chart, calendar-year X-axis, probability values, mortality heading and ahead/behind labels fit without scrolling. The mortality heading ends at scene Y=837; the viewport ends at Y=865. The annual comparison table still follows below and remains scrollable. Expanded details and genuinely smaller windows may require scrolling.

## Presentation changes

- Replaced two padded plan cards and the introductory sentence with a compact wrapping plan-elections row. Baseline and Current, both names and both retirement claiming ages remain directly visible.
- Kept four summary cards at full available width. Reduced padding/gaps, placed the prominent year beside the existing survival percentage, and joined ages/sustained status with separators. First-crossover and all non-recovery/status text remain. The 32 px year and existing marker styling are unchanged.
- Retained the compact Break-Even Summary heading.
- Default Insight now has a heading with Show details at the right, the existing dynamic headline, and a single wrapping snapshot line with the same signed, cent-precision values.
- Moved the gross-withdrawal narrative into Show details. All prior expanded content remains: metric values, withdrawal/tax/growth observations, interpretation caveats and survival context. The disclosure is a keyboard-accessible Hyperlink and a managed/visible details VBox, collapsed by default; toggling only changes layout.
- Comparison period and Chart selector share a responsive GridPane row; below 720 px of available control width they stack. The selector prefers 360 px, capped at 420 px, and can shrink.
- Selected metric name, outcome and ending difference occupy one wrapping banner label, using the original presentation formatter and cents.
- Ahead/behind labels remain adjacent to the chart, with reduced surrounding gaps. The probability row, its tick alignment and explanatory tooltip are untouched.
- Plans and Insight wrap; summary cards retain the existing four/two/one-column responsive behavior.

## Files changed in this task

Production:
- src/main/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenAnalysisView.java
- src/main/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenAnalysisDialog.java
- src/main/resources/css/break-even.css

Tests:
- src/test/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenAnalysisViewTest.java (updated presentation/disclosure assertions)
- src/test/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenCompactLayoutTest.java (new real-projection desktop/layout regression and preview)

Documentation: BREAK-EVEN-COMPACT-LAYOUT.md.

No domain, financial, mortality, Insight formatter/calculator, event detection, chart-data or chart-marker implementation files were changed. No Results UI, PDF or CSV behavior changed in this task.

## Regression scenario and previews

A synthetic household uses David retirement age 70 in both plans and Lisa 62 versus 65, with the same birth dates, financial inputs and 2027–2056 horizon. Existing ProjectionEngine/BreakEvenAnalyzer/Insight/Context services prepare the test data. This is not the unavailable original user plan; outputs are calculated, never hard-coded to match illustrative reference dates.

Its actual results remain: cumulative Social Security 2041; Investable Assets, Net Worth and After-Tax Estate 2045. The reviewed default is Total Net Worth. Claim markers remain Lisa 2027/Baseline, Lisa 2030/Current and David 2033/Both plans. The 2045 marker, full X-axis and original mortality series are visible. Tests retain result and survival-map identities across layout/disclosure changes.

- target/break-even-compact/after-desktop.png — current collapsed default, on a 1920 x 1080 canvas.
- target/break-even-compact/before-desktop.png — prior layout reference.
- target/break-even-compact/before-measurements.txt
- target/break-even-compact/after-measurements.txt

Focused regression command: -Dtest=BreakEven*Test,*Mortality*Test. **95 tests, zero failures/errors, 1 skipped.** Covers existing analyzer, Insight, survival/mortality, UI state and claiming-marker/tick alignment tests. New checks cover default visibility without scrolling, unchanged chart height, shared comparison/selector row, collapsed/expanded details, unchanged chart values and responsive bounds.

Full non-benchmark Maven suite: **1,374 tests, zero failures/errors, 3 skipped**, BUILD SUCCESS. Log: target/break-even-layout-full.log.

No commits made.