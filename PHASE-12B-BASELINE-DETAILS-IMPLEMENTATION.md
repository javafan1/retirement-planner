# Phase 12B — Baseline Projection Details

## Executive summary

Implemented a read-only, resizable Baseline Projection Details modal and its Results Summary action. The comparison uses saved baseline input values and detached inputs captured with the accepted current projection. Opening, filtering, expanding or viewing the chart performs no financial calculation or save. Native Windows acceptance remains pending. Phase 12C has not begun.

No financial algorithms, persisted schemas, fingerprints, analyzer parameter contracts, golden values, dependencies or build configuration changed. Original annual baseline results remain unavailable: the chart honestly identifies existing cached results as **Baseline Projection — Rebuilt from Saved Inputs**.

## Baseline and exact Phase 12B inventory

Branch `main`; baseline/current HEAD `e620bec4a0011ab12d15fce670bff5011093d9da`. The index was empty. Before implementation there were 10 modified tracked files and 16 untracked files, all preserved Phase 11 work and the Phase 12A audit. This phase does not alter those files.

Modified production files (relative to `src/main/java/com/daviddunn/retirementplanner/`):

- `ui/MainWindow.java`: modal ownership and lifecycle wiring.
- `ui/controller/ApplicationController.java`: accepted input context, generation guards and cache-only details session.
- `ui/views/ResultsSummaryView.java`: adjacent action and authoritative comparison enablement.
- `ui/charts/ProjectionChartView.java`: optional fixed Investable Assets presentation using the existing renderer.

Added production files under that same root:

- `app/baseline/ProjectionInputs.java`
- `app/baseline/BaselineParameterComparison.java`
- `app/baseline/BaselineDetailsSession.java`
- `ui/baseline/BaselineDetailsView.java`
- `ui/baseline/BaselineDetailsDialog.java`

Other new Phase 12B files:

- `src/main/resources/css/baseline-details.css`
- `src/test/java/com/daviddunn/retirementplanner/app/baseline/BaselineDetailsTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsViewTest.java`
- This report.

Two existing tests also receive teardown-only corrections after demonstrated Windows cleanup races:

- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedRestorationTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/SinglePersonHouseholdUiTest.java`

These 15 paths are the exact intended Phase 12B commit inventory. Exclude all pre-existing Phase 11 modifications/untracked files and `PHASE-12A-BASELINE-DETAILS-ARCHITECTURE-AUDIT.md` from a Phase-12B-only commit; retain them in the working tree. Logs and PNGs under ignored `target/` are verification artifacts, not commit inputs. No files were staged, committed, pushed, reset, cleaned or discarded.

## Results Summary integration and enablement

`View Baseline Details` immediately follows `Break-Even Analysis` in the same responsive FlowPane. It uses the existing button conventions, accessible help and an explanatory disabled tooltip. Reduced widths can naturally wrap the action pair.

Enablement consumes a prepared `ProjectionComparison`, its exact accepted current Projection reference, baseline identity and plan owner. It checks the seven existing Summary deltas: investment growth, income, total taxes, peak annual tax, ending investable assets, net worth and after-tax estate. No new financial comparison is calculated for enablement. Identical outcomes remain disabled even if configured assumptions differ. Unsaved edits alone cannot manufacture a new comparison.

No baseline, no accepted current projection, cleared/replaced result, wrong owner or zero differences disables the action. Missing baseline chart data does not disable it. The handler revalidates ownership and readiness at click time. Stale accepted results, where still retained by existing controller behavior, compare only their frozen inputs and receive a conservative potentially-outdated notice.

## Frozen current-result context and lifecycle

`ProjectionInputs` freezes the existing five configured financial roots: household, account portfolio, planning assumptions, Roth request and non-investable assets. A private Jackson mapper converts their configured representation immediately into recursively immutable maps, lists, BigDecimals, strings, booleans and explicit empty/missing values. It retains no domain object references. Exact decimal values/scales survive; this representation is memory-only and is not a new persistence schema.

The normal projection publication point is `ApplicationController.getCurrentProjection()`. Current source uses synchronous normal projection execution; analyzer workers do not publish normal current results through this method. Inputs are captured before execution. A matching completed outcome publishes its projection and immutable context together, including a run UUID, captured plan identity, completion Instant and source revision. A generation token plus owner/revision checks reject late or reentrant superseded work. Funding failure, thrown failure and cancellation publish no new context.

Existing invalidation continues to clear the accepted normal result, associated context and prepared comparison together. This phase does not introduce last-success retention for ordinary projections or a new asynchronous scheduler. A failure after invalidation cannot leave an input context falsely attached to a new result. Mere later live-plan edits cannot mutate the frozen data. Missing context explicitly produces Not recorded rows.

New/Open invalidates current ownership. Save does not modify captured inputs. Save As keeps any already displayed result associated with its original captured identity and input values; subsequent recomputation captures the new identity. Baseline replacement/clearing invalidates the baseline association. MainWindow closes an open modal on plan/baseline ownership change, unregistering its listener on exit. All controls operate on the detached session rather than looking up another plan through the controller.

## Baseline cache provenance and chart reuse

The existing normal comparison path may already rebuild baseline results from saved inputs. While that path has the correct baseline account registry, it prepares a detached `ProjectionChartModel` using the unchanged adapter. The existing chart view accepts a heading/fixed-assets option; its default constructor and existing current-projection behavior remain unchanged. Year order, currency axis, tooltips, legend, asset composition reconciliation and colors come from the same implementation. No account-name rematching is introduced.

The details handoff never calls getBaselineProjection, ProjectionEngine, non-investable calculation or a financial comparison. It reads only the prepared chart, validated against baseline cache identity. Missing, empty or unavailable chart data displays exactly:

> Baseline chart unavailable. Original annual baseline results were not saved, and no valid rebuilt projection is currently cached.

The comparison remains available. The view never mutates cached projections. There is no current overlay. Baseline and current year ranges are reported separately; no extrapolation or historical recalculation occurs on opening.

## Comparison model and fields

`BaselineParameterComparison` produces immutable rows with category, parameter path/key, label, original typed values, exact numeric delta, units, state, match confidence and explanation. Numeric comparison uses BigDecimal comparison; subtraction precedes display formatting. Formatting cannot change row equality or financial fingerprints.

Coverage is driven by the actual saved financial roots, including person facts, projection horizon/death settings, opening accounts and ownership, inherited/participant/opening RMD data, Social Security elections, pensions and other income, recurring expenses, economic/tax/state assumptions, withdrawal settings, Roth requests and non-investable inputs. Only categories containing actual rows are created. Person and collection labels retain owner/context. Runtime government rules or original software versions absent from the snapshot are not invented.

- Currency deltas are Current minus Baseline; exact values are available in tooltips. Nonzero sub-cent differences are identified even when displayed dollars round to zero.
- Stored rates display as percentages and differences as percentage points, without converting through double.
- Ages/years display differences in years; ISO dates use signed calendar-day differences.
- Enums, text and booleans show Changed/Unchanged, not numeric deltas.
- Unrecorded data shows Not recorded; a configured null shows Not configured; confidently absent collection members show Not present.

### Collection matching

Exact configured records are paired first with stable occurrence order and an explicit statement that no durable identity is asserted. Accounts can otherwise match only on a unique owner/type signature on both complete sides. Income records can similarly use a unique ownership/income-type signature. Names never establish correspondence. Structural matches are labeled as such in tooltips, not represented as durable IDs.

If unmatched records remain on both sides, they are shown separately as uncertain; no numeric differences, definitive additions or removals are inferred. If both collections were recorded and only one side has unmatched records, additions/removals are explicit. Exact duplicates are paired by their identical configured values without merging their domain objects. No IDs or migration were introduced. Ambiguity is an expected, visible result, not guessed correspondence.

### Historical missing data

Legacy baseline deserialization can supply defaults without recording whether those fields were explicit in the original file. The modal discloses this limitation and describes baseline data as the saved snapshot as loaded. It does not claim proven original explicit presence. A missing current context is never replaced by live plan inputs. Original baseline annual outputs, original run ID, source-plan ID and software/rule provenance cannot be recovered and are not fabricated.

## Modal and styling

The owner-modal dialog is resizable, screen-bounded and read-only. It contains metadata, cached chart or explanation, a default-unchecked Show Changed Values Only checkbox, and collapsible categories with Parameter/Baseline/Current/Difference columns. Tables are individually bounded/virtualized within natural outer scrolling. Columns wrap at reduced widths, and row heights follow wrapped content.

Changed/added/removed values receive subtle amber backgrounds in both value columns, with a bold Difference column. Unknown rows use neutral secondary styling; no input change is labeled favorable/unfavorable. CSS derives from JavaFX control/text colors and uses a translucent amber layer. Filtering removes unchanged rows only; uncertain/unrecorded rows remain in explicitly labeled groups. Filtering does not recompute comparisons. Expansion state survives filter changes where the category remains applicable.

Rendered inspection found overlapping wrapped labels at narrow widths during implementation. Row-height calculation now follows wrapped text across all four columns; regenerated narrow previews were inspected. Automated previews use JavaFX light styling and a dark inherited-color test setup, not native Windows theme certification.

Final static review also found that expanding an explicitly null optional object into field rows could lose the distinction between recorded null and unrecorded history. Field traversal now propagates the original empty-state value, with a regression assertion for a previously unconfigured Roth request. No numeric difference is manufactured for that case.

## Persistence, fingerprints and financial safeguards

No repository, domain financial calculation, baseline persistence model, snapshot codec/schema/version, financial fingerprint, accepted analyzer configuration, PDF exporter or Monte Carlo algorithm is changed. No new disk metadata is written. Capturing/displaying context does not mark modified, increment source revisions, invalidate projection caches or accept/save analyzer parameters. Financial values in the existing projections/comparison are untouched.

## Verification

Baseline focused tests before implementation: **60 passed, zero failures/errors/skips**, Maven 17.505 s (`target/phase12b-baseline.log`).

Focused union after implementation: **157 passed, zero failures/errors/skips**, Maven 46.075 s (`target/phase12b-focused.log`). Includes new model/controller/UI cases, baseline/snapshot/comparison services, Break-Even contexts/analysis/layouts, chart model/reconciliation/view, transactional plan persistence, Social Security and Monte Carlo parameter persistence, and frozen legacy weighted snapshot compatibility.

New tests exercise exact decimal scale, dates/booleans/enums, mutation isolation, missing context, duplicate names/ambiguity, unique structural matching, definite additions/removals, both household shapes, identical and edited-only enablement, cached chart source equality, missing chart with a throwing engine, plan switching, Save As/reopen, rejected older calculations, canceled/failed work, no dirty/revision/config changes on click, adjacency, filtering, expansion, resize and theme previews.

Final full Maven verification: **3,456 tests total; 3,412 passed; 44 skipped; zero failures; zero errors; BUILD SUCCESS**, 9 min 52 s (`target/phase12b-full-verified.log`, completed 2026-10-25 18:07:42 EDT). Used IntelliJ bundled Maven, the required project-local `.codex-m2/repository`, and `-DargLine=-Xmx4g`. No new test exclusions were introduced. The 44 existing skips are opt-in capacity/performance/replay, external-plan audit and preview tests, plus an explicitly manual multi-minute benchmark; this phase's new tests are not skipped. Existing financial/golden, seeded Monte Carlo, historical snapshot/PDF and parameter-persistence regressions passed.

Final `git diff --check`: **exit 0**. Branch/HEAD unchanged; staging index empty. Final tree has **16 modified tracked files and 25 untracked files** when individual paths are counted. No generated preview/log artifacts entered that inventory.

Final expanded focused verification: **181 passed, zero failures/errors/skips**, 1 min 1 s (`target/phase12b-verified-focused-final.log`). This is the 157-test union plus WeightedRestorationTest (14) and SinglePersonHouseholdUiTest (10), including the final teardown corrections.

Final-change focused rerun: **24 passed, zero failures/errors/skips**, 38.656 s (`target/phase12b-final-focused.log`): both new test classes plus `SinglePersonHouseholdUiTest`. This includes the recorded-null correction and separate-FX-turn preview capture.

The first full-suite attempt ran **3,456 tests: 3,411 passed, 44 skipped, zero assertion failures, one error**, in 9 min 56 s. The error was JUnit temporary-directory teardown (`DirectoryNotEmptyException`) after `SinglePersonHouseholdUiTest.uiCreatedSavedReloadedPlanRunsFixedLongevityAndPairedMonteCarlo`; the test leaves application-owned asynchronous persistence running after closing its view. Its isolated class rerun passed. A second full-source run had the same totals/time but exposed the equivalent teardown race in `WeightedRestorationTest.olderRestoreCannotOverwriteANewAcceptedCompletion`. That test drains the result store but previously did not drain accepted parameter writes. Logs are retained as `target/phase12b-full.log` and `target/phase12b-full-final.log`.

Both affected test fixtures now explicitly await their owned parameter writers before JUnit removes the temporary directory. The single-person Monte Carlo fixture additionally waits on a queued barrier in the shared application result store, without changing shared-store admission or shutting it down. New teardown checks require completion; existing assertions are unchanged. There is no delay-based workaround, test exclusion, production persistence change or weakened cleanup policy. This is verification-harness stabilization, not a new feature.

An intermediate expanded focused attempt had 181 tests, 180 passed and one failure in a newly added teardown assertion. That assertion incorrectly required the shared application result store to have no historical save failures; this fixture does not own that store, and sandbox/default-path writes can fail independently of its financial assertions. It was replaced with the proper queue barrier, which waits for work without changing shared state. The failed attempt is retained in `target/phase12b-verified-focused.log`. The final focused and complete reruns above both passed. Default-path persistence warnings from older UI fixtures and deliberate failure-injection traces are not evidence of successful durable writes; dedicated isolated persistence tests provide that verification.

### Actual visual inspection

Generated 12 PNGs (top and filtered for six cases) under ignored `target/phase12b-previews/`. Inspected actual rendered images, including:

- `changed-1180-light-top.png`: reused cached chart, axes/legend/claim annotation, provenance header and neutral table.
- `changed-900-dark-filtered.png`: checked changed-only filter, readable wrapped account label, amber baseline/current values and signed delta.
- `ambiguous-760-light-filtered.png` and `ambiguous-760-dark-filtered.png`: narrow uncertain group, separate records, no numeric delta and no overlapping wrapped labels.
- `missing-900-light-filtered.png`: exact unavailable-cache disclosure with the comparison still usable.
- `unchanged-1180-light-filtered.png`: empty changed-only presentation with clear explanatory text and preserved chart.

The original narrow preview exposed overlapping rows, corrected before these final inspections. The preview harness now waits until a separate FX turn to inspect filtered table skins; its row-height assertion guards readable rendered rows. Scrollbars are present for long tables/content; viewing all records requires scrolling. These previews are synthetic presentation sessions with chart data from a real production-engine fixture, not native Windows acceptance or a claim of original historical annual results.

## Native Windows acceptance checklist

1. Open a saved couple plan, capture a baseline and retain identical projected outcomes: the adjacent details action should be disabled.
2. Change an input, recalculate and confirm differing Summary outcomes enable the action immediately beside Break-Even.
3. Open details: verify frozen parameter values, signed differences, amber changes, exact year ranges and rebuilt-cache chart disclosure.
4. Test Show Changed Values Only, uncertain groups, category expansion, scrolling and resizing around 1180×820, 900×650 and 760×650.
5. Check long names/labels, currency values and keyboard accessibility with actual Windows DPI/theme settings.
6. Confirm later unprojected edits are not presented as the accepted current projection's inputs and do not alone enable an identical comparison.
7. Repeat with a single-person plan, duplicate-looking accounts, added/removed records and different horizons. Uncertain matches must not show guessed deltas.
8. Save/reopen and perform normal projection preparation: baseline inputs survive; the current context belongs to that new successful calculation. Original annual baseline history is not implied.
9. Exercise baseline replacement, plan switching and Save As; no dialog may silently rebind to another plan.
10. Verify no opening/filtering interaction changes dirty state, triggers analyzer parameter saves or runs projections. Missing chart availability is covered by automated cache-removal/throwing-engine tests.

## Limitations and readiness

Current input context is deliberately memory-only. Baseline annual outputs are rebuilt with current software/rules elsewhere in the normal workflow; exact original annual history is not available. Legacy default provenance is disclosed. Collections without durable IDs can remain uncertain. The source-revision notice is conservative and may reflect a nonfinancial revision; it does not replace any financial compatibility contract. No normal-projection asynchronous scheduler/cancel API was added. Native Windows DPI, screen-reader, high-contrast and theme acceptance remain user checks. The reused chart keeps its existing colored legend; blue/purple legend text has lower contrast in the inherited dark preview. No new application-wide theme or chart-palette change was introduced.

Ready for review and native Windows acceptance. No Phase 12C work is included. Suggested future commit message: `Add read-only baseline details with frozen projection input comparisons`. No commit operation was performed.

## Exact exclusions from a Phase-12B-only commit

Preserved pre-existing tracked modifications, relative to `src/main/java/com/daviddunn/retirementplanner/`:

- `app/export/IntegratedAnalyzerPdfExporter.java`
- `app/export/IntegratedAnalyzerReport.java`
- `ui/socialsecurity/AnalyzerNavigationView.java`
- `ui/socialsecurity/ClaimingStrategyHeatMapView.java`
- `ui/socialsecurity/IntegratedAnalysisResultTabs.java`
- `ui/socialsecurity/LongevityWeightedIntegratedView.java`
- `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java`
- `ui/socialsecurity/WeightedResultPersistence.java`

Preserved pre-existing tracked test modifications:

- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/CoupleSocialSecurityPersistenceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java`

Preserved pre-existing untracked reports:

- `PHASE-11A-SOCIAL-SECURITY-UI-PDF-AUDIT.md`
- `PHASE-11B-SHARED-GRID-AND-NAVIGATION.md`
- `PHASE-11C-SOCIAL-SECURITY-AND-QUICK-PDF-EXPORT.md`
- `PHASE-11D-FINAL-INTEGRATION-AND-REGRESSION-AUDIT.md`
- `PHASE-12A-BASELINE-DETAILS-ARCHITECTURE-AUDIT.md`

Preserved pre-existing untracked production files under `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/`:

- `AnalysisPdfData.java`
- `ClaimingAgeGridView.java`
- `QuickComparisonReportAdapter.java`
- `QuickReportSession.java`
- `ResultNavigationGuard.java`
- `SocialSecurityClaimingGridAdapter.java`
- `SocialSecurityOnlyReportAdapter.java`
- `SocialSecurityReportSession.java`

Preserved pre-existing untracked tests under `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/`:

- `ResultNavigationTest.java`
- `SharedClaimingAgeGridTest.java`
- `SocialSecurityAndQuickPdfTest.java`

These exclusions mean keep separate from this feature's proposed inventory, not delete or discard. Verification logs, Maven caches, Surefire reports and preview PNGs remain in ignored project directories.
