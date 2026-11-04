# Phase 17B — Global Settings and Standardized Close Navigation

## Summary

Implemented the approved top-bar Settings gear and common Close header for the seven plan-input workspace screens. The horizontal workspace navigation and existing view instances remain in place; the Summary screen is not wrapped or redesigned. Closing an input screen routes to the existing Results Summary selection without reconstructing the plan or requesting a projection.

## Settings control

The existing `MenuBar` now shares a horizontal `HBox` with a trailing gear button. The menu bar grows with the available width and the Settings button stays at the far right. The gear uses the project's existing Ikonli FontAwesome icon dependency. It exposes accessible text and tooltip `Settings`, has a stable test ID, and opens the existing Application Settings dialog and settings service. Application Settings was removed from File; the other File actions and the File | View | Analysis | Help ordering are preserved.

The Settings dialog and preference behavior were reused. No new settings persistence mechanism was added by this phase. Phase 18A's PDF export-directory work was present before this implementation and remains intact, including its integration in `MainWindow`.

## Workspace Close behavior

`WorkspaceScreenFrame` provides the shared title/header and right-aligned X icon plus `Close` button. The button delegates to one callback supplied by `MainWindow`; it does not contain view-specific navigation or plan logic. Existing view nodes are retained inside the frames and shown/hidden through the current workspace host.

| Workspace screen | Close action |
|---|---|
| Household | Results Summary |
| Accounts | Results Summary |
| Income | Results Summary |
| Expenses | Results Summary |
| Non-Investible Assets | Results Summary |
| Assumptions | Results Summary |
| Roth Conversions | Results Summary |
| Results Summary | No Close button; it is already the destination |

Yearly Projection Details remains a separate modeless window and keeps normal window-close behavior. Analyzer windows, Application Settings, the New Plan wizard, and input/edit dialogs retain their existing close, cancel, and modality semantics; they do not receive workspace Close buttons.

No global discard prompt was added. Navigation keeps the same screen instances and drafts, including pending Assumptions edits. The integration test confirms selecting Close leaves the same plan/projection objects, plan revision and dirty state intact, and returns to the existing Summary node. The workspace's existing disabled/busy state also disables the shared Close control. No Apply/Cancel, validation, projection, analyzer, or account-creation behavior was changed.

## Files changed for Phase 17B

- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/navigation/WorkspaceScreenFrame.java` (new)
- `src/main/resources/css/application.css`
- `src/test/java/com/daviddunn/retirementplanner/ui/MainWindowTestSupport.java` (new)
- `src/test/java/com/daviddunn/retirementplanner/ui/MainWindowViewNavigationTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/RothConversionMainWindowTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/SinglePersonHouseholdUiTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/CompleteWizardEndToEndTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanFundingFailureTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanWizardLifecycleTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/StateTaxWizardIntegrationTest.java`

The test updates account for the menu bar now being a child of the shared top HBox. They do not alter application or wizard workflows.

## Visual and accessibility verification

The JavaFX navigation-rendering test generated Light and Dark previews at 900×650 and 1180×820 for Summary and all seven input screens. Inspected actual rendered previews included:

- `target/phase15c/previews/light-900x650/household.png`
- `target/phase15c/previews/dark-900x650/household.png`
- `target/phase15c/previews/light-1180x820/assumptions.png`
- `target/phase15c/previews/dark-1180x820/roth-conversions.png`
- `target/phase15c/previews/light-900x650/results-summary.png`

The gear is visible at the right edge; the Close header sits at the upper-right of applicable input screens. Light and Dark button surfaces, text, hover/focus CSS states, icon contrast and compact layout remain theme-token based. The compact Summary preview confirms the Key Assumptions column remains on the right with its controls and scrolling intact. The existing two-column Summary may scroll within its content at 900×650; this phase did not redesign that layout. JavaFX keyboard metadata and activation wiring are covered by tests. Native Windows DPI scaling and hands-on keyboard/screen-reader behavior remain for acceptance.

## Verification

- Focused navigation and related UI suite: 157 tests, 0 failures, 0 errors, 2 skipped.
- Final `MainWindowViewNavigationTest`: 12 tests, 0 failures, 0 errors, 0 skipped.
- Full Maven regression suite using IntelliJ's bundled Maven and the repository-local `.codex-m2/repository`: **3,988 tests, 0 failures, 0 errors, 46 skipped**; `BUILD SUCCESS`.
- `git diff --check`: passed. Git emitted only line-ending conversion advisories for working-tree files.
- Actual JavaFX Light/Dark previews were produced and inspected at 900×650 and 1180×820. Native Windows display scaling was not separately exercised.

## Working-tree preservation and limitations

The pre-existing Phase 18A changes were preserved, including the settings model/repository/service, Application Settings dialog, PDF Save chooser adapters, `PdfExportDirectoryContext`, its test, and the Phase 18A report. `MainWindow.java` was a shared-file overlap; its Phase 18A `PdfExportDirectoryContext` stage attachment remains present. The Phase 18A report and pending files remain in the working tree. No files were staged, committed, pushed, reset, cleaned, or reverted.

The workspace does not enforce a custom minimum Stage size; compact rendering was verified at 900×650. Native acceptance should check actual minimum-size behavior and Windows scaling. This phase has not received native Windows acceptance.

## Native Windows acceptance checklist

- Confirm the Settings gear is right-aligned and opens the existing Application Settings dialog.
- Confirm File no longer contains Application Settings, while all other menu actions work.
- Use keyboard-only menu traversal and activate Settings and each Close control.
- Visit all seven input screens in both themes and confirm Close returns to Summary.
- Enter unapplied edits, navigate away and back, and confirm drafts remain as before.
- Confirm busy operations prevent inappropriate Close activation.
- Confirm Summary metrics, charts, and the right-side Key Assumptions panel remain unchanged.
- Exercise supported monitor sizes and 100%, 125%, and 150% Windows display scaling.
- Confirm modal dialogs, analyzers, wizard, and Yearly Details still close using their established behavior.
