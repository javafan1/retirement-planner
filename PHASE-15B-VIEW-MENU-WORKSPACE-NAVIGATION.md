# Phase 15B — View Menu and Shared Workspace Navigation

## Executive summary

Replaced the primary horizontal tab strip with a top-level **View** menu and a shared JavaFX content host. The menu navigates among the existing Results Summary, Yearly Detail, and plan input view instances. Navigation only changes which existing node is visible; it does not refresh or calculate a projection, apply drafts, modify plan state, or introduce a second editing lifecycle.

The Results Summary remains the default workspace. Its existing right-hand **Key Assumptions** column and all Summary content are unchanged. Yearly Detail remains an ordinary workspace view. The Analysis and Help menus and their actions remain in their former positions relative to each other, with View inserted between File and Analysis.

## Final View menu structure

Top-level menus are `File | View | Analysis | Help`.

The View menu contains radio-style selection items in this order:

1. Results Summary
2. Yearly Detail
3. Separator
4. Household
5. Accounts
6. Income
7. Expenses
8. Non-Investible Assets
9. Assumptions
10. Roth Conversions

This uses the input-screen order specified for Phase 15B. The prior tab strip had a different order for some input tabs; the new order follows the approved View-menu organization.

## Shared workspace and view lifecycle

`MainWindow` now owns a `StackPane` workspace containing the same nine view objects it constructed before this change. An ordered view-ID map associates each stable internal ID with its existing node. Navigation toggles `visible` and `managed` on those nodes, keeping exactly one workspace view active and avoiding view reconstruction or duplicate event subscriptions.

Each View item is a `RadioMenuItem` in one `ToggleGroup`, so the selected workspace is visible in the menu and remains synchronized with the content host. Menu selection uses JavaFX's built-in keyboard navigation and radio-selection accessibility. The menu has no new accelerators, avoiding conflicts with existing shortcuts. The active view's most recent focused node is retained when focus moves within that view and restored when the user returns; otherwise focus moves to the first focusable control in the selected view.

The workspace keeps the existing BorderPane window structure and status bar. No permanent sidebar, editing dialog, new view controller, or duplicate screen was added. All existing view wiring stays in `MainWindow`.

## Summary and Key Assumptions preservation

The existing `ResultsSummaryView` instance is retained and installed as the first/default workspace view. Its right-hand Key Assumptions column remains the same node in the same `BorderPane` right position. The Summary charts, metrics, projection summary, controls, exports, history access, validation and theme styling were not redesigned.

The navigation test records the original Key Assumptions node and confirms that it is still attached to the Summary after navigating through all other workspaces and returning. Actual JavaFX captures show it on the right at 1180×820 and 900×650 in both themes. At 900×650, the existing Summary content pane displays its pre-existing horizontal scrollbar and clipped metric/table columns; this is not a navigation regression. The Key Assumptions panel remains visible and independently scrollable. No claim is made that existing Summary horizontal scrolling was eliminated.

## Yearly Detail and analyzer compatibility

`Yearly Detail` routes to the existing `ResultsView` instance. Existing table, chart, export and double-click behavior remains attached. Opening this workspace does not run a projection. The Phase 15C detailed-window proposal is deferred.

The Analysis menu was not migrated or redesigned. Social Security, Monte Carlo, comparison, history and other analysis entry points retain their existing event handlers, gating, ownership, modality and progress behavior. Existing Results Summary actions such as Break-Even and analyzer history are untouched. No analyzer is launched by workspace navigation.

## Draft edits, plan workflows, and financial behavior

Switching views leaves each view instance and its draft controls in memory. It does not call `save`, `applyChanges`, `refreshAllViews`, `refreshProjectionViews`, or a controller calculation method. Existing Apply/Cancel and validation behavior therefore remains the same. New Plan continues to select Results Summary after a plan is accepted; canceling the wizard preserves the prior workspace selection and drafts.

Plan load and projection swap continue to update the existing view instances through the existing `loadCurrentPlan` path. The selected workspace is not implicitly changed by that refresh. Save, Save As, baseline, swap, history, projection restoration, analyzer freshness, and financial code were otherwise left unchanged. No financial formulas, persistence formats, plan schemas, fingerprints, or golden expectations were modified.

## Files changed for Phase 15B

- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java` — View menu, workspace host, view selection/focus retention, New Plan Summary selection.
- `src/test/java/com/daviddunn/retirementplanner/ui/MainWindowViewNavigationTest.java` — menu/order, single-visible-view, instance-reuse, Summary right-column, draft and calculation-invariance coverage; JavaFX preview capture.
- `src/test/java/com/daviddunn/retirementplanner/ui/views/RothConversionMainWindowTest.java` — draft-preservation navigation test now traverses View menu items.
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanFundingFailureTest.java` — New Plan destination assertion updated for the workspace host.
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanWizardLifecycleTest.java` — wizard selection/draft assertions updated for View navigation.
- `PHASE-15B-VIEW-MENU-WORKSPACE-NAVIGATION.md` — this report.

The existing uncommitted Phase 13J work and the Phase 15A architecture report were preserved. They were present before this implementation and were not part of the Phase 15B changes: `AssumptionsView`, `ResultsSummaryView`, their related tests, `SocialSecurityBenefitPreview.java`, `PHASE-13J-A-SOCIAL-SECURITY-AND-ECONOMIC-ASSUMPTIONS-UI.md`, and `PHASE-15A-MAIN-WORKSPACE-NAVIGATION-ARCHITECTURE.md`.

## Tests and verification

Focused Maven command:

```text
mvn -Dmaven.repo.local=<workspace>\.codex-m2\repository -DargLine=-Xmx4g -Dtest=MainWindowViewNavigationTest,NewPlanWizardLifecycleTest,NewPlanFundingFailureTest,RothConversionMainWindowTest,ProjectionYearDetailsNavigationTest,AnalyzerIntroductionNavigationTest test
```

Result: **52 tests, 0 failures, 0 errors, 1 skipped**. After adding an additional test for the existing plan-refresh path used after Open and committed Swap, `MainWindowViewNavigationTest` was rerun: **7 tests, 0 failures, 0 errors, 0 skipped**.

Complete Maven command:

```text
mvn -Dmaven.repo.local=<workspace>\.codex-m2\repository -DargLine=-Xmx4g test
```

Executed with IntelliJ IDEA's bundled Maven and the repository-local Maven cache required by `AGENTS.md`. Result: **3,930 tests, 0 failures, 0 errors, 46 skipped**; build succeeded. This full-suite run preceded the final additional plan-refresh test and test-only Analysis-menu assertion; production code did not change afterward. The follow-up navigation test passed as reported above. Existing deliberately injected persistence/analysis failure tests emitted expected warning logs while passing. No financial golden values were changed.

`git diff --check` completed without whitespace errors. Git emitted only its normal LF-to-CRLF working-copy conversion notices.

## JavaFX visual verification

The test suite captured actual rendered JavaFX workspace scenes and the expanded View-menu popup in both Light and Dark themes at 1180×820 and 900×650. Files are under:

```text
target/phase15b/previews/{light,dark}-{1180x820,900x650}/
```

Each folder contains captures for `results-summary`, `yearly-detail`, `household`, `accounts`, `income`, `expenses`, `non-investable-assets`, `assumptions`, `roth-conversions`, and `view-menu-expanded`.

Inspection confirmed readable menu labels and selected-item check state in both themes, consistent theme rendering in the selected views, and the retained Key Assumptions column on the Summary's right. At compact width, existing Summary horizontal content scrolling remains visible. The captures use JavaFX logical dimensions; Windows display scaling at 100%, 125%, and 150% was not changed or measured in this environment.

## Known limitations and deferred work

- Native Windows review is still needed for 100%, 125%, and 150% scaling, real keyboard traversal/focus restoration, and menu interaction on the target display setup.
- The Summary's pre-existing horizontal scrolling at 900×650 remains.
- Yearly Detail remains in the main workspace in this phase; Phase 15C may separately evaluate a details window.
- Analyzer navigation remains under the existing Analysis menu; Phase 15D is deferred.

## Native Windows acceptance checklist

- Open the application and verify Results Summary is selected by default.
- Verify the top-level order is File, View, Analysis, Help and that no horizontal workspace tabs remain.
- Open each View item by mouse and keyboard; confirm the selected indicator follows the displayed view.
- Navigate away from and back to Summary; confirm the right-side Key Assumptions controls, entered drafts, scroll position, charts, tables and history/projection controls remain available.
- Confirm Yearly Detail opens in the workspace and does not trigger recalculation.
- Enter unapplied drafts in Household, Assumptions and Roth Conversions; navigate away and back; verify they remain unchanged and are not silently applied.
- Load a plan and perform a Current/Baseline swap; verify the chosen workspace shows the active plan's data and projection state.
- Verify Analysis menu launch items, dialogs, ownership, focus restoration, busy state and history access.
- Inspect Light and Dark modes at 1180×820, 900×650 and a larger desktop window.
- Repeat the menu, Summary Key Assumptions and input-screen checks at 100%, 125% and 150% Windows display scaling.

## Repository status

The implementation began with the following pre-existing modified/untracked work, which was preserved: Phase 13J modifications in `AssumptionsView`, `ResultsSummaryView` and their related tests; the untracked Phase 13J report and `SocialSecurityBenefitPreview.java`; and the untracked Phase 15A architecture report. Phase 15B added/modified only the files listed above. No files were staged, committed, pushed, reset, cleaned or discarded.
