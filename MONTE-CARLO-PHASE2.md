# Monte Carlo Phase 2 — retirement analysis UI

Implemented September 23, 2026. Uncommitted, awaiting review.

## Scope and architecture

`Analysis > Monte Carlo Retirement Analysis...` opens a separate, resizable desktop dialog. It tests one isolated copy of the current plan, including configured claiming, survivor/death assumptions, conversions, expenses, pensions, taxes, accounts, and horizon. It does not optimize or change the plan. No persistence, CSV, PDF, financial-rule, parallel-simulation, stochastic-inflation, or mortality changes were introduced.

The UI captures the plan using the existing deep-copy service. A single daemon worker runs the authoritative ProjectionEngine/MonteCarloAnalyzer. The controller's new cache-only accessor permits reuse of a current deterministic projection without calculating on the FX thread. If unavailable, the worker obtains a structured deterministic outcome once. An underfunded reference retains only its completed prefix and is labeled accordingly.

The pure `MonteCarloSession` accepts an executor and UI dispatcher and owns lifecycle, progress, cancellation, stale state, and errors. Input changes or controller source revisions invalidate results. Result metadata remains frozen. Close cancels work and ignores subsequent callbacks. There is one run per session.

Existing neutral analysis progress/cancellation interfaces remain free of JavaFX. Progress reports completed simulation counts at approximately one-percent intervals (at most 101 notifications including zero). Cancellation is cooperative at simulation boundaries and before/after aggregation; cancelled work publishes no partial analysis. Unexpected exceptions abort, preserve their causes/context, clear pending results, and are logged; the normal UI shows a concise message.

## Inputs

| Input | Default | Validation |
|---|---|---|
| Simulations | 5,000 | Choices 1,000 / 2,500 / 5,000 / 10,000; model accepts 1–10,000 |
| Arithmetic expected annual return | Current plan return | -99% through 100%; finite decimal |
| Annual return volatility | Visible, editable 12% | 0% through 100%; finite decimal |
| Random seed | 417 | Signed Java long |

All are session settings, with explanatory tooltips. Editing expected return does not change EconomicAssumptions. During work inputs and Run are disabled; Cancel and real progress remain available. No settings fields were added to saved plans.

## Results and chart semantics

Funding Probability is completed simulations divided by requested simulations. Presentation is factual, without color scores or safety judgments. All-complete results omit failure details; mixed/all-failed results show funding-constraint counts and earliest/median/latest first-failure years. Near-boundary percentages are displayed without rounding an incomplete result to 100% or a nonzero result to 0%.

The Investable Assets fan uses supplied P10–P90 and P25–P75 bands, a median line, and a distinct dashed deterministic reference. For year Y, supplied annual percentiles include every simulation completing Y, including prefixes of later failures. Failing/incomplete and later years are not invented. Hover shows year, five quantiles, deterministic value, actual sample count versus requested count, and available event context. Missing data breaks bands; single-year prefixes remain visible.

Claiming markers reuse `ProjectionChartModel` and `TimelineClaimMarkers`. Roth/RMD shading reuses `ProjectionPlotDecorations`, including overlap treatment. Periods come from actual positive conversions/RMDs in the deterministic reference, not inferred ages or configured schedules. The chart identifies that reference basis.

The ending table uses existing domain percentiles, with Minimum / P10 / P25 / Median / P75 / P90 / Maximum for Investable Assets, Total Net Worth, After-Tax Estate, and Lifetime Taxes. All four distributions are conditional on completing the entire horizon. The notice is always visible. All-failed cells say unavailable; valid annual prefixes may still appear.

Analysis Details disclose independent annual lognormal gross returns matched to arithmetic mean/volatility, deterministic inflation/COLA/death assumptions, session-only settings, and the precise funding definition. Funding-constraint shortfalls retain their WITHDRAWAL_ALLOCATION, WITHDRAWAL_ESTIMATE, IRA_RMD, or ACCOUNT_RMD meaning; they are not presented as unmet living expenses. Existing estate/tax metric scope is disclosed.

## Files introduced

Under `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/`:

- MonteCarloAnalysisDialog.java
- MonteCarloAnalysisView.java
- MonteCarloFanChart.java
- MonteCarloFanModel.java
- MonteCarloInputs.java
- MonteCarloPresentation.java
- MonteCarloRun.java
- MonteCarloRunService.java
- MonteCarloSession.java

Styles: `src/main/resources/css/monte-carlo.css`.

Commit C changes only the Monte Carlo UI package, its tests and stylesheet, this document, MainWindow's menu entry, and ApplicationController's cache-only projection accessor. Shared chart prerequisites and the headless Monte Carlo foundation are already committed separately and are unchanged by this refinement.

## Accessibility and presentation refinement

The fan chart is one tab stop. Left/Right move the selected year within the available bounds; Home/End select the first/last year. Focus displays a thin dashed selected-year guide and a restrained chart border. Mouse hover updates the same selection; clicking also focuses the chart. Selection only reads the frozen fan model and never submits analysis work or changes the plan.

The visible selected-year readout and chart accessible text reuse the existing tooltip formatter: calendar year, P10/P25/median/P75/P90, deterministic value, annual sample count, and available event context. Primary age is included when supplied by the reference chart context; unavailable reference years do not invent an age. Accessible help explains navigation. Missing percentile/reference values remain explicitly unavailable.

Median stroke increases from 2.8 to 3.3 pixels. Compact 26-pixel band swatches and solid/dashed line samples preserve existing colors. Analysis Details remains collapsed initially with all disclosure content retained.

The optional summary is omitted: the immediately adjacent funding percentage, funded/requested counts and run inputs already duplicate two of its three fields, and the ending table presents the median. The selected-year readout uses the additional space for chart access.

Expected-return tooltip: "Expected return is the arithmetic mean annual return used to generate simulated yearly returns. With volatility, the median compounded outcome will generally differ from a deterministic projection using the same percentage. Range: -99% to 100%."

No calculation, simulation default, terminal-table column, marker, shading, execution, persistence, cancellation or stale-result behavior changes.

## Tests and verification

New tests under the matching test package root:

- app/montecarlo/MonteCarloProgressTest.java — bounded monotonic progress, cancellation, non-mutation, reference reuse.
- ui/montecarlo/MonteCarloSessionTest.java — validation and deterministic queued lifecycle transitions, cancellation, staleness, errors.
- ui/montecarlo/MonteCarloFanModelTest.java — quantile mapping, declining/missing samples, prefixes, reference, claiming, executed Roth/RMD overlap, funding boundary formatting.
- ui/montecarlo/MonteCarloViewTest.java — separate menu, inputs, actual background execution, responsive FX cancellation, result states, errors and notices.
- ui/montecarlo/MonteCarloPreviewTest.java — opt-in actual 5,000-run JavaFX screenshot.
- ui/montecarlo/MonteCarloUiFixtures.java — deterministic fixtures.

Previously committed test support: MonteCarloFixtures exposes its representative household; MonteCarloBenchmarkTest accepts an optional output-file property to preserve historical benchmark artifacts.

### Refinement verification

IntelliJ bundled Maven and the repository-local Codex Maven cache were used.

- Focused UI package: 16 run, 15 passed, zero failures/errors, one opt-in preview skipped. Command selector: "-Dtest=com.daviddunn.retirementplanner.ui.montecarlo.*Test". Log: target/monte-carlo-refinement-ui.log.
- Full non-benchmark suite: 1,419 run, 1,415 passed, zero failures/errors, four skipped. Command selector: "-Dtest=*Test,!*BenchmarkTest". Log: target/monte-carlo-refinement-full.log.
- Explicit preview: one run/passed, zero failures/errors/skips. Log: target/monte-carlo-refinement-preview.log.
- Initial broader Monte Carlo verification: 44 run, 43 passed, zero failures/errors, one preview skipped. It also included the existing benchmark, which passed: 100 / 1,000 / 5,000 simulations took 0.513 / 3.076 / 14.372 seconds respectively. Log: target/monte-carlo-refinement-focused.log. No execution code changed.

View tests now cover focusability, Left/Right bounds, Home/End, empty/single-year reload, authoritative selected-detail formatting, no queued analysis work on navigation, preserved completed/nonstale result state, collapsed details, and expected-return wording. Existing fan-model tests retain exact percentile/reference identity and marker/shading coverage. Preview asserts the focused active-year indicator and existing annotation nodes. Screen-reader speech output was not manually tested.

## Final visual check

The representative 5,000-path preview completed all paths, with 101 FX progress changes. Worker time: 14.587 seconds; run-to-render: 15.065 seconds. Plan JSON remained unchanged.

The generated 1900 × 1040 image was reviewed with keyboard focus and 2028 selected. The chart, compact legend, single-line selected-year readout, full ending table, conditional notice, collapsed Analysis Details and Close button fit without visible clipping. Median is more apparent while the uncertainty bands retain their area emphasis. The thin dashed selected-year guide and border are legible; claiming markers and Roth/RMD shading remain present. This is an automated real-window preview, not manual screen-reader testing.

Screenshot: target/monte-carlo-phase2-preview.png. Timing: target/monte-carlo-phase2-preview.txt.

Final boundary audit: only the intended Commit C paths are modified/untracked. No Commit A/B files or non-C source files changed. Nothing staged or committed.

## Remaining limits and review

This is the initial current-plan UI, capped at 10,000 simulations. Year details support mouse inspection and keyboard navigation. The visual preview is an all-complete case; mixed and all-failed behavior is covered by deterministic model/view tests. Timing and heap measurements vary by machine. Cancellation waits for the active simulation boundary. No failure histogram, Monte Carlo PDF, strategy comparison, random mortality/inflation, or parallel execution was added.

No changes have been staged or committed. Review the screenshot and implementation before authorizing a commit.
