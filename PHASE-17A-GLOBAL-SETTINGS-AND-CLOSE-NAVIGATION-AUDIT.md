# Phase 17A — Global Settings and Close Navigation Audit

## Executive summary

The application has a JavaFX `BorderPane` root. Its top currently contains a `MenuBar` with File, View, Analysis, and Help menus; its center is a shared `StackPane` workspace. Application Settings is currently a File menu item. Eight long-lived workspace view instances are retained and switched by `MainWindow.selectWorkspace`.

The approved settings relocation is a small composition change: keep the existing `MenuBar`, remove its Application Settings item, and place it beside a normal Settings button in a top `HBox`. A growable spacer between the menu bar and button will keep Settings at the far right without skin or absolute-position manipulation. The project already includes Ikonli JavaFX and Font Awesome 5 Solid, including the same `FontIcon` pattern used elsewhere.

For Close navigation, the seven plan-input workspace screens are the applicable targets. A shared frame/header can add a consistent Close button around each retained view and route through one MainWindow Results Summary navigation method. Results Summary itself, modal dialogs, analyzer windows, and the separate Yearly Detail window should not receive workspace Close-to-Summary behavior.

No production code was changed. The existing Phase 18A working-tree changes were present before this audit and remain untouched.

## Current menu and workspace architecture

`MainWindow` creates a `BorderPane root`, calls `root.setTop(createMenuBar())`, assigns `createWorkspace()` to the center, and uses a status bar at the bottom. `createMenuBar()` returns a `MenuBar` containing, in order, File, View, Analysis, and Help.

File currently contains New, Open, Save, Save As, Save Current as Baseline, Application Settings, and Exit. The Application Settings handler creates the existing window-modal `ApplicationSettingsDialog` with the current owner, settings service, and theme manager. Its Save/Cancel and live theme-preview rollback behavior are self-contained.

View contains radio items for Results Summary, Household, Accounts, Income, Expenses, Non-Investible Assets, Assumptions, and Roth Conversions. `MainWindow` creates one instance of each and places all eight in a `StackPane`. `selectWorkspace` changes only visibility/managed state, updates the radio selection, and restores focus. It does not invoke a projection, analyzer, plan mutation, refresh, or result replacement.

Yearly Detail was removed from the View menu in Phase 15C. The existing `ResultsView` instance is hosted in one owned, modeless `Stage`, created on demand by the Summary's View Yearly Details action. The stage closes when the main window hides. It is not a workspace child.

The current `MainWindowViewNavigationTest` asserts the menu order, menu contents, retained view instances, one-visible-view rule, Summary Key Assumptions presence, unchanged projection identity/revision/dirty state, and preserved draft text through navigation and plan refresh.

## Requirement 1 — Right-aligned Settings control

### Existing icon support

`pom.xml` already includes `ikonli-javafx` and `ikonli-fontawesome5-pack`. Existing views import `org.kordamp.ikonli.javafx.FontIcon` and `org.kordamp.ikonli.fontawesome5.FontAwesomeSolid`; `ProjectionYearDetailsDialog` constructs focusable controls with Font Awesome icons. No new dependency is needed. `FontAwesomeSolid.COG` is an appropriate standard gear glyph.

### Recommended layout

Replace the root's bare menu bar top node with a simple `HBox` containing:

1. The existing `MenuBar` with its current four menus.
2. A flexible `Region` spacer.
3. A standard JavaFX `Button` with the gear glyph.

Keep the menu bar at preferred width, let the spacer grow, and align the Settings button to the right. Set the button's accessible text/name to **Settings**, add a tooltip, and preserve normal tab focus and Enter/Space activation. Keep the icon and focus/hover treatment in CSS using existing semantic theme values such as `-app-text`, `-app-hover-surface`, `-app-border`, and `-app-accent`. Do not use a glyph-only control without an accessible name.

The button should call the same small Application Settings opening action that the File menu item currently uses. Remove the item and its separator from File. Preserve all other File actions and the menu ordering. Do not modify `MenuBarSkin`, use absolute coordinates, or turn Settings into a fifth drop-down menu.

The menu currently owns the full top row. Adding the icon consumes a small amount of horizontal space. This does not reduce the Results Summary center content width; the top row only becomes slightly taller/wider. Verify the button at 900×650 and 1180×820, and inspect keyboard traversal, focus, hover, and both themes.

## Requirement 2 — Screen inventory and close classification

### Primary shared-workspace screens

| Screen | Current host and lifecycle | Editable/pending state | Close-to-Summary classification |
|---|---|---|---|
| Results Summary | Retained workspace node; default selection. Contains metrics, charts/table, export/history actions, and right-hand Key Assumptions. | Key Assumptions controls use section-specific Apply actions; Summary is retained when another workspace is selected. | **Exclude.** It is already the destination. Keep the right-hand Key Assumptions column and all existing actions untouched. |
| Household | Retained workspace node; Apply/Cancel and dirty state. | Person/spouse fields are draft values until Apply; `cancelChanges` reverts. | **Include.** A Close route should only select Summary, retaining the same view instance and draft state. Do not implicitly Apply or Cancel. |
| Accounts | Retained workspace node; add/edit/remove through account dialogs and immediate view callbacks. | Account changes are submitted through modal editor actions; no separate whole-view draft lifecycle was found. | **Include.** Route to Summary without invoking callbacks or projection refresh. |
| Income | Retained workspace node; pension and Social Security editor dialogs. | Editors return values on submit; dialog Cancel/window close dismisses without applying that editor result. | **Include.** Route to Summary; leave open editor behavior unchanged. |
| Expenses | Retained workspace node; shared ExpenseDialog for add/edit. | Dialog submission applies the expense; cancel/window close returns without the pending result. | **Include.** Route to Summary without changing expenses. |
| Non-Investible Assets | Retained workspace node; asset dialogs and view-level operations. | Dialog forms have submit/cancel behavior; operations are committed only by their existing handlers. | **Include.** Route to Summary without calling those handlers. |
| Assumptions | Retained workspace node; Apply/Cancel and dirty state. | Assumption controls are drafts; cancel restores loaded values. | **Include.** Route to Summary while retaining draft text and dirty state. Never refresh or apply merely to close. |
| Roth Conversions | Retained workspace node; Apply/Cancel, dirty state, shared validation and transactional Apply. | Pending schedule fields remain draft until Apply; transactional Apply may confirm/create modeled destination accounts. | **Include.** Route to Summary without applying, canceling, creating accounts, or changing plan revision. |

The shared workspace contains exactly the eight nodes listed above. Existing navigation is nondestructive because it retains and hides the same node instances. The recommended Close action should use this same contract.

### Results, projection detail, and secondary views

| Screen/view | Current lifecycle | Classification |
|---|---|---|
| Yearly Projection Details (`ResultsView`) | Separate owned modeless `Stage`; opened from Summary. The active details table follows projection refresh/load; main-window hide closes it. | **Exclude from workspace routing.** Its window Close should dismiss only that owned detail window and return focus to the still-open owner. Do not navigate or recalculate. |
| Projection Year Details | Separate application-modal `Stage` with previous/next year controls, invoked from projection tables. | **Exclude.** Preserve normal modal dismissal and return to the underlying view. |
| Dashboard, Portfolio Chart, Projection Year views | Constructed/updated internally by `MainWindow`, but not inserted in the shared workspace or exposed by View navigation in the inspected implementation. | **Exclude from user-facing Close rollout.** Reassess only if a later phase makes them navigable screens. |

### Analysis screens and historical results

| Screen | Current lifecycle | Classification |
|---|---|---|
| Social Security Strategy Analyzer | Custom owned `Stage`, `WINDOW_MODAL`, shown with `showAndWait`; closes its job controller on close/hidden. | **Exclude.** Keep window dismissal and its job cleanup/cancellation contract. Do not route the blocked main workspace to Summary from inside it. |
| Monte Carlo Retirement Analysis | Owned resizable `Dialog`, `WINDOW_MODAL`, with Close button; hidden handler closes its view/session. | **Exclude.** Preserve dialog close and result/session cleanup. |
| Monte Carlo Strategy Comparison | Owned resizable `Dialog`, `WINDOW_MODAL`, with Close button; hidden handler closes its view/session. | **Exclude.** Preserve comparison lifecycle. |
| Break-Even Analysis | Resizable JavaFX `Dialog` with Export PDF and Close actions; launched from Summary/Yearly Detail. | **Exclude.** Its Close is dialog dismissal. |
| Projection Analyzer History | Read-only owned dialog with projection/analyzer selectors and Close. It detaches listeners and invalidates view generation on hidden. | **Exclude.** Closing should dismiss history and release listeners; do not alter workspace selection. |
| Social Security analyzer/history nested result views | Contents within the analyzer or history owners, not independently hosted main-workspace destinations. | **Exclude.** Preserve their parent window's close semantics. |

The Social Security analyzer's user-facing modes are Social Security Only and Integrated Retirement Plan. The integrated mode contains Quick Comparison, Deterministic Exhaustive Search, and Longevity-Weighted result presentations; single-person plans select their corresponding integrated/mortality view. These are nested contents of the same owned modal analyzer stage, not separately navigable screens. The Monte Carlo Analysis dialog contains its fixed-horizon/longevity-adjusted mode controls and result view; the Strategy Comparison dialog owns a separate paired comparison view/session. None should receive a main-workspace Close header.

### Settings, editors, and application dialogs

| Dialog/window group | Current lifecycle and actions | Classification |
|---|---|---|
| Application Settings | Owned `WINDOW_MODAL` dialog; Save persists preferences; Cancel/window close rolls back a live theme preview when not committed. | **Exclude.** Keep Save/Cancel and existing window dismissal. The top Settings button opens this existing dialog. |
| Account, Pension, Social Security, Expense, Non-Investible Asset editors | Dialogs opened from input views, generally `showAndWait`; typed results are accepted by owning view only when submitted. | **Exclude.** Keep editor Save/Cancel semantics and return to the owning input screen. |
| Opening RMD and Roth destination confirmation | Owned modal workflows; Opening RMD returns a result, Roth destination creation requires explicit confirmation. | **Exclude.** Do not change transaction boundaries or dismissal results. |
| New Retirement Plan Wizard | Modal multi-step dialog with Back/Next/Save/Cancel validation and draft plan state. | **Exclude.** Cancel closes the wizard; do not navigate the main workspace on wizard step changes or cancellation. |
| Baseline Details and analyzer introduction | Dialog flows with their own Close/Continue/Cancel and introduction-save lifecycle. | **Exclude.** Preserve local dialog behavior. |
| About, plan-operation progress, alerts, PDF choice/errors, confirmations | Owned application/modal dialogs or progress windows, not navigable workspace screens. Some progress windows deliberately consume close requests while critical operations run. | **Exclude.** Their current dismissal/busy rules govern. |
| Startup splash and startup errors | Short-lived startup stages/alerts before or during MainWindow handoff. | **Exclude.** Not workspace screens. |

The remaining reusable dialogs discovered in `ui/dialogs` (`AccountDialog`, `ExpenseDialog`, `PensionDialog`, `SocialSecurityDialog`, `NonInvestableAssetDialog`, and `OpeningRmdDialog`) are invoked by their owning input view or assumption workflow and return a typed result or confirmation. `RothDestinationCreationDialog` is a static, explicitly confirmed operation. `ProjectionYearDetailsDialog` is the modal year drill-down. These are all local dialog/window lifecycles rather than View-menu destinations. Validation alerts, Save/Discard prompts, export-choice dialogs, PDF errors, and operation feedback are transient workflow surfaces and are not screens for the shared Close convention.

## Reusable Close navigation architecture

### Recommended implementation

Introduce one small reusable `WorkspaceScreenFrame` or equivalent shared header component for only the seven input workspace screens. It should display the existing view title on the left and a consistently styled **Close** button with an X glyph on the upper right. Use Font Awesome 5 Solid already present. The button should include visible text, accessible name **Close**, and a tooltip if needed; it must remain keyboard-focusable.

Each frame receives a `Runnable`/navigation callback from `MainWindow`. The callback calls one named `navigateToResultsSummary()` method, which delegates to the existing `selectWorkspace("results-summary")` path. Keep all other workspace navigation through the same selection method. The Close action must not call `refreshAllViews`, `refreshProjectionViews`, `onPlanChanged`, any controller mutator, or Apply/Cancel methods.

The frame should retain the existing view node as its center and the shared header as its top. This leaves input view internals and their own Apply/Cancel/editor flows intact. To avoid stale-node or duplicate-listener behavior, keep exactly one original view instance per workspace view and wrap it rather than reconstructing it on navigation. Maintain focus memory for the underlying view and restore focus after navigation.

If wrapping nodes requires changing `workspaceViews` from content nodes to frame nodes, retain an explicit mapping to content nodes for focus tracking and tests. Ensure only one frame is visible/managed at a time. Do not add a second navigation controller.

Results Summary should not receive a Close control. Yearly Details should retain its ordinary window close. Modal windows keep their own Close/Cancel semantics.

### Unsaved changes

The Close button must not imply Save, Apply, or Cancel. For Household, Assumptions, and Roth Conversions, the established workspace switch model keeps the nodes alive, so their draft values remain visible if the user later returns. This satisfies the no-data-loss requirement without prompting or mutating the plan.

For Accounts, Income, Expenses, and Non-Investible Assets, the current editors have their own submission/cancel boundaries and the screens do not expose an equivalent screen-level draft set. The Close route must leave those boundaries alone.

Do not introduce a global dirty-state system in this change. If a future navigation design destroys/recreates view nodes, it must first add an explicit draft-preservation or Save/Discard/Cancel contract. Do not route Close through the application-exit `confirmPlanDeparture()` prompt: that prompt is for leaving/replacing the plan, not switching workspaces.

Summary's existing per-section Apply behavior and right-side Key Assumptions controls must remain unchanged. Avoid a Close control on Summary.

### Busy operations

MainWindow disables its top and center regions while plan operations or projection swaps run. The new Close button will therefore be disabled with its screen during those operations. Do not make it bypass this lock.

Analyzer windows are modal and retain their current job-controller close/cleanup behavior. Monte Carlo views close sessions from their dialog hidden handlers. PDF workflows own their progress and completion callbacks. Do not add a Close action to those windows through the main workspace router. The separate modeless Yearly Detail stage is closed by the main stage's hidden handler and otherwise owns no calculation task.

## Theme, layout, and accessibility

- Settings and Close controls should use centralized Light/Dark semantic theme colors, with visible hover, pressed, and keyboard-focus states.
- Use actual text labels in addition to icons; accessible names should be “Settings” and “Close”.
- Avoid adding a focusable decorative icon separate from its button.
- Keep header height consistent across the seven input screens and use layout containers rather than absolute coordinates.
- Check header wrapping/clipping at 900×650 and 1180×820 in both themes. A wrapper consumes vertical space, so test tall form screens and retain their existing scroll behavior.
- Do not change Results Summary sizing or the Key Assumptions column. The settings control is in the top menu row and Close headers affect only the selected input workspace content.
- The running JavaFX tests capture actual workspace and View-menu snapshots, but this audit did not visually inspect new Settings/Close previews; those previews belong to implementation verification.

## Likely implementation files and tests

Expected production files:

- `ui/MainWindow.java` — top-row composition, File menu removal, shared Settings action, single Results Summary route, wrapping of seven input views.
- A new `ui/navigation/WorkspaceScreenFrame.java` or similarly scoped component — shared header and Close button.
- `ui` theme stylesheet(s) — narrow semantic styles for the right-aligned gear control and Close header/button if existing generic styles are insufficient.

Expected test updates/additions:

- `ui/MainWindowViewNavigationTest.java` — adapt root-top lookup to the composed top bar; assert File no longer contains Settings, gear accessible name/action, right alignment, menu ordering, and Close routing for each of seven views.
- Add tests verifying Close returns to Summary without changing current plan identity, plan revision, projection object/identity, analyzer associations, or `controller.isModified()`.
- Verify Household/Assumptions/Roth draft values survive Close-to-Summary and return-to-screen; Close must not Apply or Cancel.
- Verify Results Summary has no Close button and its Key Assumptions node remains at right.
- Verify modal editor/analyzer/window Close behavior remains local and does not change `selectedWorkspaceId`.
- Verify repeated navigation does not reconstruct views or add listeners.
- Light/Dark JavaFX screenshot coverage for the top-right Settings button and each shared Close header at standard and compact sizes, plus focus/keyboard checks.

No projection, tax, Social Security, Monte Carlo, PDF, persistence, fingerprint, history, or analyzer calculation changes are expected.

## Architectural risks

1. **Wrapper and focus ownership:** The current workspace map contains direct view nodes and focus is tracked inside those nodes. Wrapping them requires careful separation between visible frame selection and child-view focus tracking.
2. **Vertical space:** A new common header consumes some height. Forms already scroll differently; each input screen needs compact-size verification to avoid clipping.
3. **Draft loss from accidental refresh:** The current `selectWorkspace` path is safe because it only changes visibility. A Close handler that calls plan refresh or reload would overwrite local drafts and is incorrect.
4. **Disabled state during operations:** Any custom header must remain under the existing root top/center disable rules; it must not bypass plan operation locks.
5. **Settings layout migration:** Returning an `HBox` or `BorderPane` instead of a `MenuBar` from `createMenuBar` will require updating current tests that cast `root.getTop()` directly to `MenuBar`, while preserving menu accessibility and order.
6. **Native menu behavior:** Do not use the system menu bar for this UI because arbitrary right-aligned controls cannot be placed beside it consistently across platforms.
7. **Scope boundaries:** Adding Close to modal analyzers or editor dialogs would conflate dismissing a dialog with selecting a blocked workspace and can break cleanup/cancellation contracts.

## Recommended implementation phases

### Phase 17B — Settings control

- Extract one settings-dialog launch method.
- Remove Settings from File.
- Compose MenuBar, expanding spacer, and accessible gear button in an `HBox`.
- Add focused menu-order, alignment, keyboard, theme, tooltip, and dialog-opening tests.
- Check 900×650 and 1180×820.

### Phase 17C — Shared workspace Close header

- Add one reusable header/frame component with Close callback injection.
- Route through `MainWindow.navigateToResultsSummary()` and existing `selectWorkspace` state/focus behavior.
- Apply it only to Household, Accounts, Income, Expenses, Non-Investible Assets, Assumptions, and Roth Conversions.
- Keep Results Summary, Yearly Detail, analyzer windows, settings, and modal editors excluded.
- Add draft-preservation, plan/revision/projection identity, one-visible-view, modality, and busy-state regression tests.
- Capture actual Light/Dark screenshots at compact and standard sizes and inspect the Summary's unchanged right-hand Key Assumptions layout.

Use separate approval gates for each phase if requested. Neither phase changes financial behavior.

## Verification

- Existing focused navigation run: **29 tests, 0 failures, 0 errors, 1 skipped**. Tests included `MainWindowViewNavigationTest`, `ProjectionYearDetailsNavigationTest`, `AnalyzerDirectNavigationTest`, and `AnalyzerIntroductionNavigationTest`. The single skipped test is the existing opt-in test under its configured skip condition.
- These tests ran against the current source, including pending Phase 18A settings/export work. No production source or test code was modified for this audit.
- `git diff --check`: passed (Git emitted only line-ending notices).
- JavaFX rendering was exercised by existing navigation screenshot tests; no new screenshots were produced or visually inspected for this audit.

## Git status and preservation

Branch: `main` tracking `origin/main`. The pre-existing Phase 18A modifications and report were present in the initial status and remain unchanged. This audit added only this report. Nothing was staged, committed, or pushed.

## Approval gate

This document is an audit and implementation recommendation only. Review the Settings top-row approach, the seven-screen Close scope, and the draft-preservation contract before authorizing implementation.
