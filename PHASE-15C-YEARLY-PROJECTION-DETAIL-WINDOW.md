# Phase 15C — Yearly Projection Detail Window

## Executive summary

Yearly Detail now opens from **Results Summary → View Yearly Details** in a single owned, resizable, modeless JavaFX window. The existing `ResultsView` instance is reused; it is no longer part of the View menu or shared workspace. `MainWindow.refreshProjectionViews()` continues to feed it the active projection and non-investable projection data through the same path used by Summary. Opening, focusing, resizing, or closing the detail window does not request or run a projection.

The Results Summary layout and its right-hand Key Assumptions column remain in place. The only Summary addition is the new button beside the Projection Summary heading. Yearly Detail's wide annual table now keeps readable column widths and exposes horizontal scrolling rather than squeezing its columns and truncating every header.

## Audit gate and existing architecture

The Phase 15A and 15B reports were reviewed against the current source. `ResultsView` is a self-contained `BorderPane` containing the yearly `TableView`, summary label, and Break-Even action. Its data is supplied through `load(Projection, List<NonInvestableAssetProjection>)`. Its handlers are installed once by `MainWindow`; there is no attachment to a tab controller, no calculation triggered by visibility, and no dependency on the original parent. Its fallback Break-Even dialog obtains an owner from its current Scene; production already wires the view to `MainWindow`'s shared Break-Even handler.

The main controller owns projection selection, restoration, revisions, and swaps. `MainWindow.refreshProjectionViews(...)` obtains the active projection and associated non-investable results, loads them into both Summary and `ResultsView`, and is already called for accepted calculation, plan load, startup restoration, and committed Current/Baseline swap. That provides a safe lifecycle for the modeless window without introducing a second data source or financial lifecycle. The audit gate passed; no financial-model or broad controller refactor was needed.

## Summary action and View menu

The button is in the Projection Summary heading, next to its title and dollar-basis subtitle. It uses existing JavaFX button/theme styling, has the stable ID `view-yearly-details`, and an accessible help description. It is disabled when the Summary has no nonempty projection, with a tooltip explaining that a completed projection is required. Clicking it only shows the already-populated `ResultsView`.

The View menu now contains **Results Summary**, a separator, and the existing plan-input views. **Yearly Detail** is removed from both the menu and the `StackPane` workspace. Results Summary remains the default. The same `ResultsView` is retained by `MainWindow` and attached to the details Stage only when first opened; no hidden duplicate or placeholder view is kept in the main workspace.

## Detail window, ownership, and projection lifecycle

`MainWindow` lazily creates one `Stage`, initializes the main application Stage as its owner, and sets `Modality.NONE`. The window is resizable, initially sized up to 1450 × 850 logical pixels within the selected monitor's visual bounds, with room left for native window decorations. The stage title is derived from centralized branding and reads “Aledrix Retirement Planning — Yearly Projection Details.” Repeated activation brings the same window forward. Closing it hides the reusable Stage, so reopening retains its session size and position.

Because the existing projection refresh path reloads the same `ResultsView` instance, an accepted recalculation, plan load, startup restoration, or Current/Baseline role swap updates the open window using the newly active results. If projection refresh fails or no projection is available, the detail table is cleared to its existing unavailable placeholder and the Summary button is disabled. A plan replacement test verifies that an open window cannot continue showing the prior plan's rows. No new historical-selection behavior was added; the view continues to follow the active projection as it did in the workspace.

The detail window is nonmodal, so Summary and other application workflows remain usable. Its main-window ownership makes it follow the main window lifecycle. When the main Stage is hidden during application exit, `MainWindow` also hides the detail Stage. Theme management discovers the new Stage/Scene through its existing global window lifecycle listener; no second theme manager or CSS ownership was added.

## Existing detail behavior and table layout

The annual `ProjectionYear` rows, all existing financial columns, cell formatting, row selection/sorting, double-click `ProjectionYearDetailsRequest`, and Break-Even handler remain on the same `ResultsView` instance. The existing year-detail dialog path remains unchanged. The yearly details screen did not have a direct table export action; Summary export controls remain untouched.

Rendered inspection showed that the old `CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS` compressed the wide set of annual columns into the view and visibly shortened headings. Since the dedicated details window is intended for reading those columns, each column now receives a preferred/minimum width based on its heading and the table uses JavaFX's unconstrained resize policy. This preserves all columns and adds the expected horizontal scrollbar; the user can scroll across the full financial detail. This affects only the now-dedicated `ResultsView`, not Summary's projection table.

## Summary and Key Assumptions preservation

`ResultsSummaryView` remains the same view instance and retains its existing Summary layout. The button is placed in the projection table's own heading. Tests confirm the button shares the heading that belongs to the Projection Summary table and the existing Key Assumptions node remains on the right side. The existing Summary horizontal scrolling at compact width is still present; this phase does not claim to eliminate it.

## Files changed

- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java` — removed Yearly Detail from workspace/menu, added the owned modeless Stage, bounds, reuse/foreground behavior, owner shutdown handling, and unavailable refresh cleanup.
- `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java` — added the projection-heading button, availability tooltip, and launch callback.
- `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsView.java` — retained the existing details view behavior while keeping column headers readable and providing horizontal scrolling.
- `src/test/java/com/daviddunn/retirementplanner/ui/MainWindowViewNavigationTest.java` — updated workspace/menu expectations and added tests for ownership, singleton/reopen behavior, projection availability, unchanged controller state, plan refresh, and Light/Dark visual capture.
- `PHASE-15C-YEARLY-PROJECTION-DETAIL-WINDOW.md` — this report.

The working tree was clean before Phase 15C began. No pre-existing modified or untracked files were present to overwrite. No files were staged, committed, or pushed.

## Verification

Focused command, run with IntelliJ's bundled Maven and the repository-local Maven cache:

```text
mvn -Dmaven.repo.local=<workspace>/.codex-m2/repository -DargLine=-Xmx4g -Dtest=MainWindowViewNavigationTest,ProjectionYearDetailsNavigationTest,ProjectionSwapViewTest,ResultsSummaryBaselineMetricsTest test
```

Result: **22 tests, 0 failures, 0 errors, 0 skipped**.

Full command:

```text
mvn -Dmaven.repo.local=<workspace>/.codex-m2/repository -DargLine=-Xmx4g test
```

Result: **3,934 tests, 0 failures, 0 errors, 46 skipped**; `BUILD SUCCESS`. Skips are the suite's existing opt-in visual/benchmark cases. Existing injected persistence and analyzer failures emitted their expected warning logs while their tests passed. No financial golden values were changed.

`git diff --check` is run after this report is written.

## JavaFX visual inspection

The JavaFX test rendered the real Summary and details views in both themes at 1180 × 820 and 900 × 650 logical sizes. Captures are under:

```text
target/phase15c/previews/{light,dark}-{1180x820,900x650}/
```

Each folder includes `results-summary.png`, `view-menu-expanded.png`, `yearly-details.png`, and `yearly-details-scrolled.png`, along with the other View workspaces used by the existing capture test. Actual rendered captures were inspected for Light and Dark at both sizes. The new button is fully visible in the Projection Summary header at 1180 × 820; the Summary's right-side Key Assumptions column remains intact. At 900 × 650, the existing Summary horizontal content scroll remains. The detail window shows readable headers and a horizontal scrollbar in both themes, and the scrolled captures expose later financial columns. The owner and modeless behavior were exercised with both main and detail Stages showing.

These are JavaFX logical-size previews, not native Windows display-scaling measurements. 100%, 125%, and 150% Windows scaling were not exercised here.

## Known limitations and native Windows acceptance checklist

Native Windows review remains necessary for display scaling, native decorations, focus restoration, and taskbar/window behavior. Please verify:

- Open Yearly Details from the Summary header; confirm it is owned by the main window, modeless, resizable, and can remain open beside Summary.
- Open it repeatedly; confirm the same window is raised. Close and reopen it; confirm its current-session size and position are retained.
- Confirm it starts within the usable monitor area at 100%, 125%, and 150% display scaling, including a smaller laptop display.
- Scroll horizontally through the complete table and confirm all financial headers and values are readable.
- Recalculate while the window is open; confirm it follows the newly accepted projection without a second calculation.
- Load a different plan and perform a Current/Baseline swap while it is open; confirm rows follow the active projection and are cleared if no projection is available.
- Restore a saved projection and confirm the detail rows match the projection shown in Summary.
- Switch Light/Dark themes while the window is open; confirm its colors update.
- Close the detail window and verify focus returns appropriately. Exit the application and confirm no detail window remains orphaned.
- Confirm Summary charts, controls, export/history actions, compact scrolling, and the right-hand Key Assumptions column remain unchanged.

Phase 15C is implemented and automated verification is complete. Native Windows acceptance is pending; Phase 15D has not begun.
