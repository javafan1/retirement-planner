# Phase 15A — Main Workspace Navigation Architecture Audit

## 1. Executive summary

The main workspace is composed by `ui.MainWindow`. It eagerly constructs one instance of each currently navigable view and puts those instances into a non-closable JavaFX `TabPane`. `ResultsSummaryView` is the default first tab. No tab-selection listener currently performs a plan refresh, validation, calculation, or result restoration. Plan loading and accepted projection restoration happen through explicit `MainWindow` lifecycle methods, independently of tab selection.

The most important layout constraint is explicit and measurable in source: `ResultsSummaryView` uses a `BorderPane` whose center holds the summary and whose right side is the fixed Key Assumptions panel (`minWidth=300`, `prefWidth=320`, plus a 12-pixel left margin). The existing summary center already scrolls horizontally at 900×650 in the actual JavaFX preview inspected for this audit. A permanent expanded sidebar would reduce that width further. The Key Assumptions panel must remain fully visible and functional on the right.

Two viable migrations exist:

* **Persistent sidebar:** most discoverable and scalable, but consumes horizontal space. It is feasible only with a compact/collapsible design and width-specific acceptance proving Summary remains usable. At reduced widths, an expanded sidebar is incompatible with the observed Summary layout.
* **Input menu plus shared content host:** replace tabs with an `Input` menu between `File` and `Analysis`; keep Summary as the default content and navigate among the existing view instances in a single host. This preserves all horizontal space for Summary and is the lower-risk first migration, at the cost of requiring a menu action to change screens and to return to Summary.

**Recommendation:** use the Input-menu content-host design in Phase 15B. Do not merely add a menu while leaving visible horizontal tabs; that duplicates navigation and does not meet the stated purpose. Keep the Analysis menu unchanged. Reconsider a sidebar only after Phase 15B has native Windows measurements across supported resolutions/scales, or after Summary itself has an independently approved responsive layout change. No financial, persistence, view lifecycle, or analyzer changes are needed for either navigation approach.

No production or test source was changed during this audit. The requested report is the only new file. The initial and final pre-report working-tree entries are recorded in section 13.

## 2. Current navigation architecture and class inventory

### Main window and startup

`ui.MainApplication` owns startup settings/theme initialization, the startup splash, and the primary `Stage`. It creates `MainWindow`, assigns the scene, maximizes the stage, then shows it. `MainWindow.createScene()` creates a 1200×800 scene. `MainWindow` owns the active `ApplicationController`, one instance of each view, the menu bar, root `BorderPane`, operation state, and stage integration. It sets the menu bar at the top, the `TabPane` at center, and the operation/status area at bottom.

The current top-level menus are `File`, `Analysis`, and `Help`, in that order. File contains New, Open, Save, Save As, Save Current as Baseline, Application Settings, and Exit. Analysis contains the Social Security Strategy Analyzer, Monte Carlo Retirement Analysis, and Monte Carlo Strategy Comparison. Help contains About. Break-Even is opened from a projection-results action rather than a top-level Analysis menu item. Projection Analyzer History is opened from a button in Results Summary.

### Existing top-level workspace tabs

`MainWindow.createTabPane()` adds these non-closable tabs in this exact order:

| Order | Tab | Existing view class | Ownership/use |
|---|---|---|---|
| 1 | Summary | `ResultsSummaryView` | Main result charts, metrics, annual summary table, Key Assumptions column, baseline controls, export and projection history actions. Default on first construction. |
| 2 | Yearly Detail | `ResultsView` | Detailed annual projection `TableView`, summary line and Break-Even entry point. Double-clicking a year opens `ProjectionYearDetailsDialog`. |
| 3 | Assumptions | `AssumptionsView` | Full financial/tax/planning assumptions, Social Security benefit preview, and draft Apply/Cancel controls. |
| 4 | Roth Conversion | `RothConversionView` | Roth configuration draft, availability assessment, Apply/Cancel and explicit modeled destination confirmation workflow. |
| 5 | Accounts | `AccountsView` | Account table and add/edit/remove workflows. |
| 6 | Income | `IncomeSourcesView` | Person income-source table and editing. |
| 7 | Expenses | `ExpensesView` | Recurring/one-time expense table and editing. |
| 8 | Household | `HouseholdView` | Household draft/edit workflow. |
| 9 | Non-Investable Assets | `NonInvestableAssetsView` | Non-investable asset table and editing. |

`DashboardView`, `PortfolioChartView`, and `ProjectionYearView` are also constructed and populated by `MainWindow`, but are not currently top-level tabs. Historical/commented tab construction is not active navigation.

The view instances are fields on `MainWindow`; `createTabPane()` wraps those same objects rather than creating views on tab selection. This is a good basis for retaining the views in a new content host. A JavaFX `Node` cannot simultaneously belong to both a retained `TabPane` and a new host, so a migration must choose one owner and must not try to mirror the same nodes in both structures.

## 3. View lifecycle, selection, and editing findings

There are no `Tab.setOnSelectionChanged`, selected-item listeners, or selection handlers in `MainWindow`. Switching the visible tab is currently a presentation-only action. It does not call `loadCurrentPlan`, `refreshAllViews`, `refreshProjectionViews`, projection calculation, analyzer execution, or view validation.

`MainWindow.loadCurrentPlan(...)` explicitly loads the active plan into Household, Accounts, Income, Expenses, Assumptions, Roth Conversion, and Non-Investable Assets. It then refreshes projection views according to the controller's restored/prepared/current mode. `refreshProjectionViews(...)` clears directional Break-Even display state, and then either reads the accepted cached projection (`peekCurrentProjection`) or explicitly invokes `getCurrentProjection()` when a projection run is requested. The latter may synchronously run the projection engine. Navigation must not call this refresh method.

Most data-list screens mutate through their established editor callback; Household, Assumptions, and Roth Conversion keep explicit draft state and Apply/Cancel behavior. Existing top-level tab navigation does not prompt when leaving a dirty draft: because `MainWindow` retains each view instance, switching back preserves its draft. Plan departure is a separate boundary: Open/New/Exit use `confirmPlanDeparture()`, which checks ordinary plan dirtiness and the Household, Assumptions, and Roth draft states, then offers Save, Discard, or Cancel. Save validates and applies those drafts through the existing normal save lifecycle. A navigation redesign should preserve this distinction and retain the view instances.

`MainWindow.onNew()` selects Summary after a new plan is created. Startup selects Summary because it is the first tab. Opening another plan does not explicitly reset the selected tab; the current tab remains selected. Phase 15B should preserve this behavior unless a default-on-plan-load policy is separately approved. The future menu navigation should also expose an explicit Summary command so users can return from an input screen.

Keyboard navigation today is inherited from JavaFX `TabPane` and its menu controls; `MainWindow` does not define tab accelerators or a custom focus system. A replacement must supply keyboard-focusable navigation controls, visible focus indication, accessible names, deterministic focus movement, and mnemonics/accelerators that do not conflict with editors or existing menu actions.

## 4. Results Summary and Key Assumptions preservation

`ResultsSummaryView` extends `BorderPane`. The constructor places its header at top, `ScrollPane(createMainContent())` at center, and `createAssumptionPanel()` at right. The right panel is currently a fixed-width `VBox` with 300-pixel minimum and 320-pixel preferred width, separated from the center by a 12-pixel margin. It contains a fixed “KEY ASSUMPTIONS” heading and its own vertically scrolling content. It includes the existing planning/death, Social Security, economic, and Roth controls and their Apply handlers, validation, tooltips, and synchronization callbacks.

This right-hand panel is a required part of the Summary workspace. It must not become a navigation item, modal, collapsible/hidden panel, or separately navigable screen. Any replacement must keep the same `ResultsSummaryView` instance in the main content area and leave its `setRight(assumptionsPanel)` relationship unchanged.

### Actual JavaFX preview evidence

The focused existing `ResultsSummarySocialSecurityPreviewTest` ran as part of this audit's test selection and generated JavaFX captures under `target/phase13f-implementation/previews/`. I inspected Light 1180×820, Light 900×650, and Dark 900×650 captures. At 900×650, the right Key Assumptions column remains visible and scrollable. The summary center itself already shows horizontal scrolling/clipping around its metric row and chart content. At 1180×820, the full right panel remains present, but the Summary center's metric strip and annual table are still constrained and show horizontal scrolling. These captures demonstrate the existing tradeoff; they do not simulate an added sidebar.

For space estimates, the current Summary has an explicit 300-pixel minimum assumptions panel, 12-pixel separation, and 24 pixels of outer view padding. Ignoring window decorations and menu/status heights, that leaves approximately 544 pixels of Summary center at a 900-pixel scene width and approximately 824 pixels at 1180. The inspected preview's center also contains its own scroll viewport and visible horizontal scrollbar. A proposed expanded sidebar of roughly 200–220 logical pixels plus a gap would reduce those centers to around 312–332 pixels and 592–612 pixels, respectively. A compact 56–64-pixel rail plus a gap would leave approximately 468–476 pixels at 900 and 748–756 pixels at 1180. These are arithmetic estimates from source constraints, not JavaFX measurements of a sidebar prototype.

At Windows 125%/150% scaling, effective logical dimensions depend on the monitor's physical resolution and JavaFX/Windows DPI handling. No native scaling test was performed in this audit. For example, a 1366-physical-pixel-wide laptop at 150% corresponds to roughly 911 logical pixels before system/window chrome, near the constrained 900-pixel scenario.

## 5. Option A — Persistent left-hand navigation

### Recommended structure if this option is selected

Use a root `BorderPane` beneath the current menu bar/status bar. Place a keyboard-accessible left navigation rail in `left` and a single content host in `center`. A `StackPane` can own the existing view nodes and toggle only the selected node's visibility/managed state; alternatively, a single `center` slot can be assigned the already-constructed selected view. Do not reconstruct screens when selected. Do not use an invisible-header `TabPane` as the long-term content architecture: hiding the tab header relies on internal CSS selectors, leaves hidden tabs in the accessibility/focus model, and makes navigation ownership less clear.

The proposed groups are Results → Summary and Plan Inputs → Household, Accounts, Income, Expenses, Non-Investible Assets, Assumptions, Roth Conversions. Keep the current menu bar as File | Analysis | Help. Keep Yearly Detail temporarily reachable from a clearly labeled Summary action during the 15B migration; its final presentation is a separate 15C decision.

### Compact/collapsible behavior

An expanded rail is not compatible with the current 900-pixel Summary width. If testing an always-present rail, start with a compact icon rail around 56–64 logical pixels and an explicit expand/collapse button. Use tooltips and accessible names for icons; the icons alone are not adequate names. At width below a threshold informed by actual JavaFX layout tests, collapse labels automatically or let the rail overlay/drawer temporarily rather than consuming another 200 pixels. If a rail must be fully collapsed, provide a keyboard-accessible reopen control and do not make navigation discoverability depend solely on hover.

The sections may be collapsible, but a collapsed section must preserve a visible route to its entries, expose expanded state accessibly, and not change the selected screen. Persisting expanded/collapsed UI state would be a separate app-preference decision; the minimal Phase 15B version should keep this ephemeral.

### Lifecycle and risk

The sidebar changes only selection. Existing view callback ownership remains in `MainWindow`; current plan, revision, histories, projection snapshot identities, Apply/Cancel rules, and analyzers are untouched. A navigation action must not call `refreshAllViews()` or use `getCurrentProjection()` to “prepare” content. Existing drafts should remain in their view instances. Plan departure continues to use `confirmPlanDeparture()`.

The largest risk is space and horizontal clipping, followed by keyboard/accessibility parity and the implementation boundary between persistent view ownership and layout switching. Light/Dark theme support is feasible with the existing semantic theme CSS and existing Aledrix card/navigation style conventions, but would require rendered verification for the selected rail states.

## 6. Option B — File / Input / Analysis menu with shared content host

This is a genuine replacement architecture, not an extra menu above the existing tabs. Keep one content host and the existing view instances, but replace the visible top-level tabs with a menu bar containing File | Input | Analysis | Help. Input contains Summary (return/home action) and the seven plan-input screens. Selecting an item changes the content host; it does not open a dialog and does not reinstantiate a view. Keep Analysis and Help behavior unchanged.

The clean implementation is also a `StackPane` or single-slot host, not a `TabPane` whose header is hidden by CSS. Each Input action selects the corresponding existing instance. Add stable menu IDs for testability and standard JavaFX mnemonics. A small non-modal navigation toolbar/button could make the active screen and route back to Summary more visible, but that would be an additional UI decision; the strict menu-only alternative has lower screen cost and lower navigation discoverability.

This option protects all existing Summary width: its Key Assumptions panel stays on the right, and its main chart/table center receives the same width as before. On return to Summary, the same view instance and its already-loaded accepted projection remain available. No projection or analyzer needs to run on selection.

Risks are menu discoverability, one extra interaction to change/return screens, and users not seeing the current screen group at a glance. Mitigations are a Summary item at the top of Input, clear menu mnemonics, consistent screen titles, and optionally shortcut assignments after checking collisions. Do not add a modal dialog per input screen: that would fragment the workspace, introduce nested ownership and draft-lifecycle questions, and be less consistent with current views.

Adding Input while leaving all horizontal tabs visible is not recommended: it duplicates navigation, leaves the original space problem untouched, and creates two selection affordances that must remain synchronized.

## 7. Side-by-side comparison

Scores are qualitative relative to the current implementation; “High” regression risk means more integration/acceptance work.

| Criterion | Option A: persistent sidebar | Option B: Input menu + host |
|---|---|---|
| Development effort | Medium; new navigation component, responsive states, content host, focus behavior | Low–medium; menu actions plus content host and selected-screen state |
| Main production surface | `MainWindow`, navigation CSS/theme, possibly focused nav utility/tests | `MainWindow`, focused navigation tests; minimal CSS |
| Controller/view lifecycle change | None required if views are retained; selection must stay presentation-only | None required if views are retained; selection must stay presentation-only |
| Summary/Key Assumptions preservation | Possible, but center width shrinks by rail width | Strongest; preserves current width exactly |
| Horizontal space | Compact rail costs ~68–76 px; expanded ~212–232 px | No persistent navigation width cost |
| 900×650 / scaled laptop | Compact only; current Summary already scrolls horizontally; needs strong acceptance | Best fit; same constrained layout as current, no added width loss |
| Light/Dark | Supported with new semantic rail styling and state checks | Existing native menu theming; little new CSS |
| Keyboard/accessibility | Excellent if roving focus/list semantics, labels and focus states are built; more custom work | Native menu keyboard access; screen switch may require explicit focus restore |
| New-user discoverability | High; navigation always visible | Moderate; users must open Input menu and learn Summary return action |
| Experienced-user efficiency | High; one-click persistent targets | Moderate; menu open + item selection; accelerators can help |
| Future scalability | High; can add grouped destinations, but analyzer duplication risk | Moderate; long Input menu can grow and becomes less glanceable |
| Regression risk | Medium, driven by compression and custom focus/state | Low–medium, mostly menu routing and content-host ownership |
| Maintainability | Good after a dedicated, tested navigation component exists | Simplest for a small fixed set of workspaces |
| Reversibility | Good if content-host selection remains separate from view state | Good; menu can be replaced by sidebar later without changing views/controller |

## 8. Responsive layout and screen-space analysis

Source defines a 1200×800 initial JavaFX scene; application startup subsequently maximizes the Stage. No minimum main-stage width is configured. Results Summary sets its Key Assumptions panel to minimum 300/preferred 320 and keeps it fixed at right. The center is a fit-to-width `ScrollPane`; the annual table is configured with constrained column resizing, while the 900×650 preview still has a horizontal scrollbar. No source-level measurement establishes a safe expanded-sidebar breakpoint.

The current 900×650 and 1180×820 preview evidence above was generated by an existing test in this audit's focused test run, and screenshots were inspected. It does not constitute DPI testing or a sidebar prototype. No actual 100%, 125%, or 150% Windows scaling measurements were made.

| Scenario | Option A compact rail (estimate) | Option A expanded (estimate) | Option B |
|---|---:|---:|---|
| 1180 logical width | Summary center about 748–756 px | About 592–612 px | Current ~824 px before window/root chrome adjustments |
| 900 logical width | About 468–476 px | About 312–332 px | Current ~544 px before window/root chrome adjustments |
| 1366 physical at 150% | Around 470 logical px before chrome if compact | Around 320 logical px | Current constrained center, no navigation penalty |

The rough values subtract the 300-pixel minimum Key Assumptions panel, its 12-pixel gap, 24 pixels outer padding, and the estimated rail/gap. Actual JavaFX sizing must be recorded from a real Stage before deciding a sidebar breakpoint. Inputs (especially tables and multi-field Assumptions) may also become narrow; preserve their existing scrolling behavior and do not silently reduce fonts or hide fields.

Dialogs and popups already use a mix of owner-modal/modeless patterns. The navigation host should not resize/reparent dialogs, and dialogs should continue to use the primary Stage as owner where they currently do. Phase 15B should add size/focus tests for the navigation itself but not redesign existing dialog bounds.

## 9. Phase 15C — Yearly Detail feasibility

The current `ResultsView` is the Yearly Detail tab. It renders a wide `TableView` of `ProjectionYear` fields, summary text, and a Break-Even button. Double-clicking a year captures a `ProjectionYearDetailsRequest` containing the current ordered annual results, selected year, and non-investable annual results. `MainWindow.showProjectionYearSummary(...)` supplies the current baseline's annual and non-investable data and opens `ProjectionYearDetailsDialog`.

The detailed view is calculated-data presentation; opening it does not call a projection or analyzer. It displays current-year details and matching-year baseline details when available. The request copies its lists but carries no explicit accepted projection UUID, owner plan UUID, or source revision. The year objects are references from the already accepted projection. Role swaps reload results and replace these data sets, and the active snapshot model is available through `ApplicationController`, but the details request does not currently bind itself to that identity.

`ProjectionYearDetailsDialog` uses `Modality.APPLICATION_MODAL`, `showAndWait()`, width 1400 and height 800 (max width 1400, max height 850). The caller does not initialize its owner. It contains previous/next year navigation and a `ProjectionYearDetailsPane`, which renders year detail and comparison fields. This is a functional but older modal pattern; it blocks the application while open and is wider than a 900-pixel display.

For Phase 15C, prefer a **resizable, owner-scoped modeless window** for detail inspection, provided its request captures and displays the current projection identity/context and holds immutable accepted result data. This keeps Summary usable and avoids application-wide modality. If two independent detail windows or simultaneous plan changes cannot be handled safely, use `WINDOW_MODAL` with the primary stage as owner as the fallback. Do not use a request built from a later-selected controller projection after the window opens. Keep current/baseline values tied to the same captured year and snapshot contexts; state “Baseline unavailable for this year” where horizons do not overlap rather than inventing years. Size to available visual bounds and retain keyboard year navigation.

The “View Yearly Details” action should sit next to the existing projection summary table in Summary and pass the exact accepted current data used by that table. Whether the old Yearly Detail tab remains as a transitional route during 15B is an approval choice; the lower-risk ordering is keep it until the 15C window is accepted, then remove its permanent workspace entry in 15C.

## 10. Phase 15D — Analyzer navigation feasibility

The current Analysis menu has three launch entries: Social Security Strategy Analyzer, Monte Carlo Retirement Analysis, and Monte Carlo Strategy Comparison. The Social Security analyzer opens a dialog with the existing four couple choices (three choices for single-person plans) and its own internal result tabs. Its introduction gate validates the captured plan/revision before opening; selecting a workspace can start an asynchronous analyzer under existing admission/generation safeguards. The top-level menu action opens the analyzer dialog but does not immediately run an analysis.

Monte Carlo Retirement Analysis and Monte Carlo Strategy Comparison each open their existing configuration/result dialogs after their Phase 13G introduction gates. These dialogs own run inputs, progress, cancellation, freshness and accepted-result publication. Comparison is conditionally enabled based on baseline availability and compatible household composition. The menu refreshes that availability on showing.

Break-Even is opened from Results Summary/Yearly Detail result controls. It uses an ordered Current/Baseline context and is not a standalone permanent input workspace. Projection Analyzer History is a Summary action that opens a read-only dialog tied to accepted snapshot context. Social Security analyzer history is also shown through the existing History dialog. There is no present top-level permanent Analyzer tab.

Recommendation: retain the Analysis menu in Phase 15B and keep analyzer controls out of the input sidebar/menu. Do not duplicate analyzer entry points in both menu and sidebar. A later Analysis sidebar group (option 2) could share a single command registry with the top menu, but duplication must use one action object/state source and must never permit duplicate launches. Prefer retaining Analysis menu permanently unless user testing demonstrates a strong need for an integrated navigation section. If later moving analysis navigation, separately assess dialogs with long-running work, progress/cancel, result history, active projection direction, disabled-state explanation, and menu/dialog accessibility.

## 11. Projection lifecycle, persistence, and financial integrity

The active plan, source-plan revision, projection epoch, current projection, accepted snapshot, baseline role, non-investable yearly data, analyzer admissions, and file-operation guards remain owned by `ApplicationController`. The controller has explicit open/save/swap prepare/commit operations. `MainWindow` handles prompts, validates editable drafts, uses background file preparation with guarded FX commits, and blocks conflicting plan operations. Plan change events mark the plan modified and invalidate/recalculate through the established path.

Navigation must be a presentation-only state transition:

* Do not call `MainWindow.refreshProjectionViews()` on selection. Its normal path invalidates the accepted projection and may calculate through `getCurrentProjection()`.
* Do not call `loadCurrentPlan()` for same-plan view selection; that reloads controls and can discard drafts.
* Do not call a view's `load` or `refresh` merely because it becomes visible.
* Do not apply draft edits or invoke Roth automatic destination creation from navigation.
* Do not change plan revision, dirty state, configured-input identity, financial fingerprints, projection UUID, accepted timestamp, or analyzer associations.
* Keep the plan departure Save/Discard/Cancel boundary unchanged.
* Keep a selected input view bound to the exact active plan after load/swap; existing guarded `loadCurrentPlan` remains the only plan-wide rebinding path.
* Keep asynchronous operation/busy guards on menu commands. The content host should not make a disabled/hidden operation command available while Save/Load/Swap is active.

The safest implementation is to change only the `MainWindow` workspace composition and selection API, with UI-only tests proving the same view instances are reused and that changing selection does not modify controller revisions, dirty state, accepted UUIDs, or invoke engines. No persistence schema or projection change is justified.

## 12. Recommended migration sequence

### Phase 15B — Input-menu workspace migration (recommended)

**Expected files:** `MainWindow.java`; a small `MainWorkspaceNavigation`/content-host class if it makes selection state clearer; focused `MainWindow`/navigation UI tests; semantic nav CSS only if needed.

**Change:** replace the visible `TabPane` with a single shared content host; add File | Input | Analysis | Help; add Summary and the seven existing input routes in Input; reuse the exact existing view objects; retain Yearly Detail as an Input/menu transition route until Phase 15C removes it; default to Summary on new application construction. Existing behavior of preserving selection on plan open should remain.

**Risks:** menu discoverability, focus transfer into the selected view, preserving drafts when their view is hidden, and accidental refresh calls. **Tests:** all entries select their exact existing node; repeated selection does not reconstruct; menu/keyboard access; draft survives navigation away/back; no controller revision/dirty/engine changes; new plan and open behavior; single/couple; theme; Summary Key Assumptions remains at right; 1180×820 and 900×650 actual previews.

**Native acceptance:** Windows 100/125/150% scaling, menu mnemonics, keyboard-only operation, visible selected screen, Summary chart/table and right assumptions usability, opening/saving/swapping while on each screen, single/couple, Light/Dark. **Rollback:** restore the existing `createTabPane()` and root-center assignment; view construction/controller lifecycle remains the same.

### Phase 15C — Yearly Detail presentation

**Expected files:** `ResultsView.java`, `ResultsSummaryView.java`, `MainWindow.java`, `ProjectionYearDetailsRequest`/pane/dialog and tests. Add explicit accepted projection identity and captured baseline comparison context only if the existing snapshot APIs can supply it without altering schema. Add a Summary button near the annual table, open a bounded resizable owner window, preserve original result data and no-calculation behavior, then remove the permanent Yearly Detail navigation route only after acceptance.

**Risks:** modeless-window data consistency during plan switch or role swap, baseline horizon differences, excessive detail-table width and modality changes. **Tests:** exact selected year and snapshot identity; no projection run; current/baseline shared-year comparison; modeless owner/close/focus; window bounds; stale owner/plan rejection; save/open/swap while viewing details; existing table double-click navigation. **Rollback:** retain old detail tab and dialog route until the replacement passes acceptance.

### Phase 15D — Analyzer navigation decision

**Expected files if expanded:** `MainWindow`, a shared analyzer command/state provider, sidebar/nav CSS, analyzer launch/navigation tests. No calculation or persistence classes should change.

**Change boundary:** first decide whether Analysis remains top-menu-only or is additionally represented as a collapsible sidebar group. If duplicated, both locations must invoke the same guarded command and use the same availability state. Do not move analyzer result panes into the workspace without a separate architecture approval.

**Risks:** duplicate launches, stale enablement, cross-analysis joins, captured-plan/revision guards, progress/cancel UI, historical result vs last-success separation, and directional Break-Even invalidation. **Tests:** menu and optional sidebar invoke one launch; no duplicate run; busy/disabled behavior; association/history context; single/couple applicability; stale/legacy/unavailable states; race guards unchanged. **Rollback:** retain Analysis menu as authoritative and remove only the secondary presentation.

## 13. Verification and repository status

### Initial worktree status

Before inspection, the worktree showed these existing Phase 13J-A changes and report; they were not altered by the audit:

```text
 M src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsLayoutTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/FutureFederalTaxRateChangePresentationTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/ResultsSummarySocialSecurityPreviewTest.java
?? PHASE-13J-A-SOCIAL-SECURITY-AND-ECONOMIC-ASSUMPTIONS-UI.md
?? src/main/java/com/daviddunn/retirementplanner/ui/views/SocialSecurityBenefitPreview.java
```

### Focused existing tests

Used IntelliJ bundled Maven with the project-local `.codex-m2\repository` and `-DargLine=-Xmx4g`:

```text
mvn -Dtest=AssumptionsMainWindowTest,RothConversionMainWindowTest,ProjectionYearDetailsNavigationTest,ProjectionSwapViewTest,ResultsSummaryBaselineMetricsTest,ResultsSummarySocialSecurityPreviewTest test
```

Result: **49 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Relevant coverage exercised MainWindow/Roth navigation integration, projection detail navigation, swap visual/state behavior, summary metrics, and Summary Social Security controls. Existing test JavaFX warnings about runtime configuration and restricted native access were informational.

The existing Summary test rendered previews at 1180×820 and 900×650 in both themes. Light 1180, Light 900, and Dark 900 captures were inspected. The Key Assumptions panel remains on the right; the center already scrolls horizontally at compact width. No new prototype was made. No Windows DPI scale was changed.

`git diff --check` completed with no whitespace errors after report creation. Final worktree status adds only this report to the initial entries; no production/test/resource files were changed.

## 14. Outstanding decisions requiring approval

1. Approve **Option B (Input menu plus shared content host)** for Phase 15B as recommended, or select Option A (compact/collapsible sidebar) with acceptance that it consumes Summary width and requires a measured responsive breakpoint.
2. If Option B is approved, confirm that **Input → Summary** is the explicit route back to Results Summary and whether `Yearly Detail` remains temporarily in Input until Phase 15C.
3. Confirm the Phase 15B plan-selection policy: recommendation is preserve the existing selected workspace when opening a different plan, and return to Summary on New as today.
4. For Phase 15C, approve the recommended resizable modeless Yearly Details window, with `WINDOW_MODAL` as fallback only if immutable context/lifecycle cannot be guaranteed modeless.
5. For Phase 15D, retain Analysis menu as the authoritative analyzer entry point; decide later whether an additional sidebar group is warranted. Do not duplicate analyzer launch controls before that decision.

No implementation is authorized by this audit. Phase 15B/C/D work should begin only after explicit approval of the selected architecture and unresolved policies.
