# Monte Carlo Phase 4C — Current Plan / Saved Baseline UI

## Final screenshot-driven visual refinement

This section supersedes the earlier layout dimensions below. The refinement starts
from the existing uncommitted 15-file Phase 4C implementation, explicitly authorized
by the final refinement request; HEAD remains `bd2e4e2` and the index remains empty.
The reported real-screen issues were dense upper content, weak funding hierarchy,
ambiguous probability header, understated zero reference, an awkward rotated axis
title, and a population line falling below the dialog viewport.

Changes are confined to presentation and layout:

- Funding Reliability now has three compact neutral columns: Current Plan, Saved
  Baseline, and Difference / Current minus Baseline. Existing probability formatting
  and percentage-point formatting are unchanged. Values remain 20px bold with no
  preference coloring. Columns have 28px separation and 18px right padding.
- The financial header is `P(Current > Baseline)` with a 180px minimum column width.
  Table accessibility explicitly says “Probability Current Plan exceeds Saved Baseline”.
  Tax detail still says positive means more modeled tax and negative means less.
- Thin section-top rules and 4px/2px heading padding establish hierarchy. Removed
  the duplicate strategy-identification row; frozen strategy labels remain immediately
  above results. Context shares the action row, funding-denominator text shares the
  paired-population row, percentile help shares the quantile row, and direction text
  shares the relation row. FlowPanes wrap these groups on smaller windows.
- View spacing is 3px (was 4), result spacing 2px (was 3). Table height is 130px
  (was 138), with unchanged 26px rows and unchanged font sizes. All four rows fit.
- Chart min/preferred/max height is 205px (was 245): exactly a 40px reduction.
  Percentile paths, bands, data, axis bounds, selection and accessible text are unchanged.
- Zero reference is 2.8px (was 2), with a 12px/5px dash pattern (was 10px/4px).
  This is heavier than 1px percentile lines and geometrically distinct from the solid
  3px median. Its zero value and pixel mapping are unchanged.
- The complete Y-axis title is now `Current − Baseline ($)`. The full annual
  conditional-population explanation remains above the chart.
- Both selected-year lines retain every original population field and the original
  font size. Small-sample warning and collapsed Analysis Details remain below them.

The first real-dialog geometry check reproduced the problem: selected-year bottom
913px exceeded viewport bottom 907px. After compacting redundant rows, selected-year
bottom is 850px and collapsed Details bottom is 878px in the actual 1856×962 dialog
scene (1836×897 viewport, ending at y=907). The 1900×1040 preview population-line
bottom is 841px and Details bottom is 869px; the late-year warning adds 19px.

The preview fixture now exercises the actual production Dialog layout via a
package-private constructor accepting the existing completed view. It reuses the
same completed 5,000-world result when moving from the large preview into the actual
dialog; changing selection does not execute another analysis.

Geometry assertions check individual funding columns and non-overlap, financial
table and terminal notice, full untruncated axis-title bounds inside the chart,
zero line at the authoritative zero-axis pixel, dash geometry and stroke weight,
both selected-year text lines including the population-text bottom, warning when
visible, collapsed Details, and every required section inside the actual ScrollPane
viewport. They also reject visible horizontal scrollbars and verify the explicit
table header/accessibility wording. Routine view tests retain keyboard, click/focus,
accessibility, stale state, cancellation, tax direction and no-rerun assertions.

Preview artifacts (all stochastic inflation, 5,000 paired simulations, seed 417):

- `target/phase4c-fixed.png` — 1900×1040, fixed lifespan, selected 2042.
- `target/phase4c-longevity.png` — 1900×1040, longevity, selected 2042.
- `target/phase4c-longevity-late.png` — 1900×1040, selected 2067, 63 comparable.
- `target/phase4c-longevity-2034.png` — 1900×1040, selected 2034, 4,948 comparable.
- `target/phase4c-real-dialog-2034.png` — actual dialog, 1856×962, selected 2034.
- `target/phase4c-real-dialog-late.png` — actual dialog, 1856×962, selected 2067.

Each has a corresponding `-geometry.txt` record. The 2034 fixture matches the
reported mode, stochastic setting, simulation count, seed and selected year; it
uses the existing synthetic plan fixture rather than the user's personal plan.

Visual inspection: all six images were opened and inspected, not merely generated.
The fixed preview has readable positive/negative bands, distinct zero/median lines,
all four table rows, and both selected-year lines. The longevity preview retains
the complete nominal terminal-date explanation and explicit population counts.
The 2067 preview shows 63 comparable / 5,000 requested and 4,937 deceased, with
the warning and collapsed Details visible. Both 2034 previews show 4,948 comparable
and 52 deceased; the actual dialog has 57px clearance from population-line bottom
to viewport bottom. The actual-dialog late preview has population bottom 850px,
warning bottom 869px, and Details bottom 897px, all within viewport bottom 907px.
All six show the three funding columns and explicit Current > Baseline header;
axis titles are complete, there is no overlap, table truncation or horizontal
scrolling, and Analysis Details is collapsed. The full terminal explanation is
visible in every longevity preview. No font reduction was needed.

Verification for the final refinement:

- Focused presentation/session/view tests plus enabled comparison previews:
  **24 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS.
  `target/phase4c-refinement-focused.log`.
- All non-benchmark `MonteCarlo*Test` tests with comparison previews enabled:
  **220 tests, 0 failures, 0 errors, 3 skipped**, BUILD SUCCESS.
  `target/phase4c-refinement-montecarlo.log`. This rerun includes the final added
  real-dialog late-year, non-overlap, untruncated-axis and no-horizontal-scroll
  geometry assertions. All passed. The skipped tests are separate opt-in
  single-strategy preview tests. Seed-417 world paths/random-stream regressions,
  paired execution/aggregation and existing fan-chart behavior passed unchanged.
- Latest 5,000-world preview worker times: fixed **32.2016436s**, longevity
  **26.0002687s** (projection plus cached reduction, same existing backend).
  Selection and moving the completed view into the real dialog reuse the result.
  These are observational timings, not a change to the execution protocol.
- Final full non-benchmark suite: **1,623 tests, 0 failures, 0 errors, 7 skipped**,
  BUILD SUCCESS in 1:47, using `-Dtest=*Test,!*BenchmarkTest test`.
  Log: `target/phase4c-refinement-full.log`. This includes the existing financial,
  persistence/export, baseline, Social Security, projection and Monte Carlo
  regressions. No unrelated production fix or financial-assertion change was needed.
- Final `git diff --check` passed. Untracked files were separately checked with
  `git -c core.autocrlf=false diff --no-index --check -- /dev/null <file>`;
  no whitespace diagnostics. Final status exactly matches the 15-file listing
  at the end of this report. `git diff --cached --name-only` remains empty.

The exact 15-file boundary listed at the end of this report is
unchanged: this pass edits only ComparisonView, ComparisonPresentation,
DifferenceChart, ComparisonDialog, comparison CSS, ComparisonViewTest,
ComparisonPreviewTest and this report. No additional file was introduced.

No financial assertions were changed. Phase 4A execution, Phase 4B aggregation,
random generation, seed-417 inputs, mortality, inflation, taxes, RMDs, Roth,
Social Security, persistence/schema and all financial calculations are unchanged.
No staging, commit or push is part of this refinement.

## Starting state and architecture audit

Starting HEAD: `bd2e4e2 Add paired Monte Carlo strategy comparison statistics`.
Initial `git status --short` and `git diff --cached --name-only`: empty. Phase 4B was
committed and the worktree/index were clean. No pull/reset/stash was used.

Phase 4A's candidate freezes a complete plan; request assumptions own indexed shared worlds;
the analyzer executes both sides sequentially and reports one progress unit per complete pair.
Paired outcomes retain annual prefixes, terminal values or authoritative failures. Phase 4B's
result caches one immutable summary using its reducer, Type-7 metric summaries, reconciled
BigDecimal probabilities, and explicit annual populations. These implementations were read
and are unchanged. The UI consumes `result.summary()`; it does not run reducers or statistics.

Existing UI inspected: MonteCarloAnalysisDialog/View, MonteCarloFanChart, MonteCarloInputs,
MonteCarloPresentation, MonteCarloMortalityPresentation, Run/RunService/Session, MainWindow,
ApplicationController, monte-carlo.css, ResultsView, baseline snapshot/factory/projection
workflow, Break-Even dialog, and Social Security analyzer layout/task patterns.

The existing Monte Carlo dialog wraps a naturally sized VBox in a width-fitting ScrollPane.
Its worker/session dispatches callbacks to FX, tracks source revisions, cancels cooperatively,
and freezes result metadata. The fan chart provides year hit areas, keyboard navigation,
selection/accessibility text, and mortality-aware small-sample warnings. ResultsView's
comparison/break-even entry uses controller caches; Break-Even and Social Security use separate
read-only dialogs. Their output/export paths are not reused or changed.

The committed `RetirementPlanSnapshot.fromRetirementPlan` already deep-copies baseline
snapshots through Jackson, despite the older AGENTS caveat. This phase preserves that actual
implementation and does not change baseline semantics.

## Entry point and source capture

Analysis > **Monte Carlo Strategy Comparison...** is a sibling of existing Monte Carlo
Retirement Analysis. The item is disabled without a saved baseline, with an adjacent explanatory
menu item: “Save a baseline before running Monte Carlo Strategy Comparison.” Menu availability
refreshes whenever Analysis opens. Direct construction of the view also disables Run and shows
the explanation. There is no implicit self-comparison fallback.

Strategy A is always **Current Plan**; Strategy B is always **Saved Baseline**.
`MonteCarloStrategyComparisonRunService.capture` reconstructs baseline input from the same five
snapshot fields used by BaselineProjectionService (household, account portfolio, planning
assumptions, Roth request, non-investable assets). It copies both plans before applying any
session inputs, then constructs Phase 4A candidates, which privately freeze their inputs.
Baseline acquisition never runs a deterministic projection or uses an output as a substitute
for baseline financial input. Neither live source is mutated.

Capture occurs on FX before worker admission. Labels, baseline description/save timestamp,
stochastic settings, mode, and details text are frozen with the prepared request. Completed
presentation retains no mutable live-plan reference. Source edits or baseline replacement
invalidate the session through the existing controller revision listener; old results remain
marked STALE and retain their original metadata.

Paired compatibility is explicit: same start date, birth dates, and mortality categories.
Fixed mode also requires matching configured death timing and uses Current Plan's horizon,
as Phase 4A already supports. The common horizon and baseline configured length are disclosed.
Invalid compatibility reports validation rather than silently changing plan dates or people.

## Inputs and execution

The new view reuses `MonteCarloInputs`, `MonteCarloMode`, and existing mortality adjustment /
survivor-age validation. Defaults match existing Monte Carlo: 5,000 simulations; seed 417;
plan expected return; 12% volatility; deterministic inflation by default; stochastic mean from
the current plan, 1.75% volatility and -2% floor. Optional inflation and longevity inputs share
a wrapping row to conserve height.

Mortality categories and conditioning date remain plan-derived/read-only, matching the existing
Monte Carlo interface: conditioning is the projection start, not an independent financial start.
Longevity factors initialize from controller session settings. Each strategy has its own
session-only survivor age initialized from that strategy's persisted election. Ages are applied
to isolated working inputs before freezing the candidates, preserving Phase 4A's per-strategy
survivor semantics. No shared age silently overwrites both strategies. No settings persist.

No shared-input-control refactor was needed: validation/models/defaults are reused, while the
existing single-plan view stays unchanged. A small comparison-specific session mirrors the
proven generation/revision/cancellation lifecycle, keeping the established single-plan session
API and behavior untouched rather than introducing a broad generic lifecycle refactor.

One daemon executor performs paired analysis through the existing Phase 4A analyzer. The run
wrapper retains the authoritative result plus immutable metadata and elapsed time. Progress is
“Comparing strategies — simulation N of requested”; 5,000 worlds never becomes 10,000 units.
FX mutations occur only on capture/render/dispatch. Controls disable during execution.

Cancel sets the cooperative token; queued completion is discarded when cancelled, invalidated,
or closed. No partial result is published. Close removes the controller listener, invalidates
callbacks and shuts down the executor. Edits mark a completed result stale; editing during a
run cancels it. Worker exceptions become visible failed-state diagnostics.

## Result layout and terminology

1. **Funding Reliability**: Current Plan probability, Saved Baseline probability, and Current
   minus Baseline difference, explicitly in percentage points. Four exact counts and fractions
   follow, with the all-requested denominator. Formatting changes units only: 0.005 becomes
   +0.5 percentage points; Phase 4B BigDecimals are unchanged.
2. **Financial Outcome Differences**: visible both-completed count/denominator; a compact
   four-row table of metric, Median A-minus-B, Mean A-minus-B, P(A>B), and sample count.
   Selecting a metric displays Min/P10/P25/Median/P75/P90/Max plus greater/equal/less probabilities.
   Missing populations display Unavailable, never fabricated zero values.
3. **Investable Assets Difference Over Time**: one paired fan chart and selected-year readout.

Assets/net worth/estate wording describes more/less assets. Tax details explicitly say “paid
MORE/LESS modeled income tax” and “Probability Current Plan paid more / equal / less”. No
positive/negative preference colors, score, recommendation, ranking, or strategy application
exists. The table's neutral P(A>B) is never described as a winning probability.

Longevity terminal results visibly explain that nominal differences are measured immediately
before each household's own second-death date, with different calendar years across worlds and
no discounting to a common date. Fixed results disclose the common horizon and nominal dollars.

P10/P25/Median/P75/P90 help uses comparable difference distributions. P10 explains 10% at/below
and 90% above; median explains half on each side with ties possible; P90 reverses those fractions.
Help also notes interpolation rather than probabilities of receiving an exact amount.

Analysis Details is collapsed by default. It includes frozen labels, baseline timestamp/description,
count/seed, return model/mean/volatility, general inflation mode/mean/volatility/floor,
healthcare inflation per plan, mode, mortality metadata/categories/conditioning/factors,
per-strategy survivor ages, and terminal comparable count. Fixed mode marks mortality table,
conditioning, and longevity factors as not applied. No PDF controls or exporter changes.

## Chart, population, accessibility and layout

`MonteCarloDifferenceChart` reads cached annual paired results. It draws P10/P25/Median/P75/P90
lines plus P10-P90 and P25-P75 filled bands. NumberAxis bounds are symmetric around zero and
support signed values. A dark, thicker dashed horizontal zero reference differs geometrically
from the solid median and thin quantile lines. Missing-sample years break paths; absent values
are never plotted as zero. Axis animation is disabled to avoid stale tick-label artifacts.
Floating-point values are used only for pixel geometry, never financial/statistical computation.

Hover and click select a year; click focuses the chart. Left/Right clamp at bounds, Home/End
select boundaries. A visible vertical guide follows selection. Selection text contains a two-line
summary: year/median/P10/P90, then comparable/requested, Current-only funded, Baseline-only funded,
both failed, and deceased/not living. These counts come directly from Phase 4B. Deceased
households are not funding failures. Zero samples show unavailable percentiles.

Small-sample heuristic matches existing mortality UI: comparable count 1–100 inclusive AND
at most 5% of requested worlds. A separate visible warning states the actual count and possible
instability; no data changes or suppression occur. The warning is included in accessible text.

The chart is focusable, has a role description and keyboard help, uses meaningful accessible
selected-year text, and has a visible focus border and selection guide. Controls have accessible
names and associated labels. No screen-reader speech was manually verified; accessibility state
is checked programmatically. Meaning does not depend on color alone.

1900 × 1040 is the primary target. Controls wrap, labels wrap naturally, the financial table is
bounded to four rows, and the chart has bounded height. The outer ScrollPane fits width and
allows vertical scrolling for smaller windows or expanded details; no primary horizontal scroll
is required. Existing typography/colors and panel/focus styles are reused. Only comparison-
specific CSS selectors were added; the existing fan chart and single-plan UI remain unchanged.

Initial visual inspection found selected-year/details below the viewport and animated axis tick
artifacts. Optional input rows were combined, vertical spacing tightened, chart height reduced,
and axis animation disabled. A subsequent strict preview bounds assertion caught a one-pixel
late-year details overrun, fixed with another 10-pixel chart reduction. These were UI fixes only.

## Verification and review boundary

Final automated counts, measured preview performance, visual findings, and exact file status
are recorded below. All Maven runs use IntelliJ's bundled Maven with
`-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.
No dependencies, build configuration, ProjectionEngine, Phase 4A/4B, stochastic generation,
plan schema, PDFs, or CSV code changed. No files staged, committed, or pushed.

## Preview generation and visual inspection

Opt-in command: `-Dtest=MonteCarloComparisonPreviewTest -Dmontecarlo.comparison.preview=true test`.
Final run: **2 tests passed**, no failures/errors/skips. It produces three screenshots using
real 5,000-world paired analyses, with stochastic inflation enabled:

| Preview | Path | Dimensions | Visual inspection |
|---|---|---|---|
| Fixed Lifespan | `target/phase4c-fixed.png` | 1900 × 1040 | Funding and all four table rows fit; positive and negative bands cross a legible dashed zero reference; year 2042 guide/readout and collapsed details are fully visible. No clipped or overlapping labels observed. |
| Longevity-Adjusted | `target/phase4c-longevity.png` | 1900 × 1040 | Session inputs and nominal terminal-date explanation are visible; chart/tables fit. Year 2042 shows 4,607 comparable of 5,000 and 393 deceased. No horizontal clipping or table truncation observed. |
| Late longevity selection | `target/phase4c-longevity-late.png` | 1900 × 1040 | Year 2067 shows 63 comparable of 5,000, 4,937 deceased, and the small-sample warning. The zero line and selection guide remain readable; selected text, warning and collapsed details fit, with details ending at y=1006. |

All three images were opened and visually inspected after the final layout correction, not
merely generated. Automated geometry assertions check the run action, funding row, table,
chart, selected readout, warning, and details against 1900-wide / 1015-high content bounds.
The selected label is checked for text truncation; details remain collapsed. Geometry logs
are `target/phase4c-{fixed,longevity,longevity-late}-geometry.txt`.

The real preview fixture uses a frozen original baseline, additional current-plan spending,
and a later pension; it is test data, not a user's financial plan. It intentionally produces
both positive and negative paired differences. Stochastic draws and financial rules are
unchanged. No recommendations are inferred from the fixture.

## Performance

Measured 5,000-world UI-worker times (including backend execution and cached summary):
**30.9176611 s fixed**, **25.0578498 s longevity**. Logs:
`target/phase4c-fixed-performance.txt`, `target/phase4c-longevity-performance.txt`.
These are consistent with the earlier approximately 30.4 s / 23.9 s paired timings, but
use a different two-strategy expense/pension fixture and are not a controlled speedup claim.
The existing Phase 4A/4B execution/reduction code is unchanged. Phase 4B previously measured
roughly 15–17 ms reduction for 5,000 worlds; no additional UI reduction was introduced.

Hover, year selection, metric selection and resizing only format cached values or update
plot geometry. A focused test performs 100 year selections plus metric selection and verifies
that the injected analysis service ran exactly once. No N-way search, claiming grid,
projection rerun, chart-time percentile calculation or random-stream access is added.

## Next-step recommendations

Review the on-screen structure, signed chart, denominator wording, and per-strategy survivor
inputs before designing comparison PDF output. A future PDF should use the same frozen result
and metadata, with an explicit selected-year context and no re-analysis. Expanded details
and smaller-window scrolling are available; PDF pagination should be designed separately.
No PDF work has begun. Arbitrary file comparisons and multi-strategy search remain out of scope.

## Final automated verification

All commands used IntelliJ's bundled Maven:
`C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd`,
with the repository-local Maven cache flag recorded above.

- Focused comparison tests: **22 passed** (11 presentation/capture, 5 lifecycle/input,
  6 JavaFX view/chart/menu), zero failures/errors. Initially run with
  `-Dtest=MonteCarloComparison*Test test`; final versions also passed in the Monte Carlo and
  full-suite runs. Preview tests are explicitly opt-in.
- All Monte Carlo regression tests: **219 tests, 0 failures, 0 errors, 4 skipped** with
  `-Dtest=MonteCarlo*Test,!*BenchmarkTest test`. Log: `target/phase4c-montecarlo.log`.
- Final full non-benchmark suite: **1,623 tests, 0 failures, 0 errors, 7 skipped**,
  BUILD SUCCESS, 1:56 minutes, with `-Dtest=*Test,!*BenchmarkTest test`.
  Log: `target/phase4c-full.log`.
- Explicit real-work previews: **2 passed**, producing all three inspected images, using
  `-Dtest=MonteCarloComparisonPreviewTest -Dmontecarlo.comparison.preview=true test`.
  Log: `target/phase4c-preview.log`.

Normal-suite skips are the opt-in preview classes/methods, two personal-plan audit tests,
and one explicitly disabled long production-mortality performance test. No unrelated
intermittent test failure occurred. Initial new-test issues were two Unicode-minus expected
strings written through the Windows shell and one nonexistent test setter; these were corrected
in tests. Preview bounds failures were corrected in layout as described above. No financial
behavior or unrelated production code was changed to make a test pass.

Coverage includes action availability/missing baseline, A/B labels, captured count/seed/settings,
funding fractions and percentage-point formatting, all four states, both-completed denominators,
median/mean/all requested quantiles, greater/equal/less fractions, neutral tax wording,
nominal longevity explanation, cached paired chart data, signed bounds/zero/guide, authoritative
population text, inclusive warning thresholds, Left/Right/Home/End/clamping/click-focus,
accessible text, no selection reruns, stale state, frozen metadata, cancellation/close,
identical-strategy zero display, and absence of recommendation terminology.

Phase 4A/4B tests passed unchanged. Seed-417 market/world/inflation fingerprints, random-stream
vectors, mortality results, existing fixed and longevity Monte Carlo, survivor claiming ages,
fan chart and small-sample warnings passed. Full-suite coverage also includes baseline isolation
and comparison, break-even, deterministic Social Security, existing PDF/CSV export, JSON
persistence/save-reload, and ProjectionEngine regression tests. Those production implementations
and the persisted schema are outside this diff.

## Exact final files and Git state

**15 files total: 9 production UI files (7 new Java classes, 1 modified Java class,
1 modified CSS file), 5 new test/fixture files, and 1 new report.** No deletions.

Modified production UI files:
- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java` — sibling Analysis action and availability notice.
- `src/main/resources/css/monte-carlo.css` — comparison-only funding/chart/zero/axis styles.

New production UI files under `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/`:
- `MonteCarloDifferenceChart.java`
- `MonteCarloStrategyComparisonDialog.java`
- `MonteCarloStrategyComparisonPresentation.java`
- `MonteCarloStrategyComparisonRun.java`
- `MonteCarloStrategyComparisonRunService.java`
- `MonteCarloStrategyComparisonSession.java`
- `MonteCarloStrategyComparisonView.java`

New tests/fixture under `src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/`:
- `MonteCarloComparisonFixtures.java`
- `MonteCarloComparisonPresentationTest.java`
- `MonteCarloComparisonPreviewTest.java`
- `MonteCarloComparisonSessionTest.java`
- `MonteCarloComparisonViewTest.java`

Report: `MONTE-CARLO-PHASE-4C-COMPARISON-UI.md`.
No existing single-plan UI class, ApplicationController, ResultsView, financial class,
Phase 4A/4B class, exporter, dependency, or build configuration changed.

`git diff --check`: passed. All changed/new files were also checked with
`git -c core.autocrlf=false diff --no-index --check -- /dev/null <file>` so untracked content
was included: no whitespace diagnostics after normalizing changed-file line endings.
HEAD remains `bd2e4e2`. `git diff --cached --name-only` is empty.
Nothing staged, committed, or pushed. Exact final `git status --short`:

```text
 M src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java
 M src/main/resources/css/monte-carlo.css
?? MONTE-CARLO-PHASE-4C-COMPARISON-UI.md
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloDifferenceChart.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonDialog.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonPresentation.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonRun.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonRunService.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonSession.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonView.java
?? src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloComparisonFixtures.java
?? src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloComparisonPresentationTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloComparisonPreviewTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloComparisonSessionTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloComparisonViewTest.java
```
