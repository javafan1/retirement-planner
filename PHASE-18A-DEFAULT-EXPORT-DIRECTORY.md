# Phase 18A — Default Export Directory

## Summary

Added a separately persisted Default Export Directory setting and connected it to every production PDF Save chooser. Changing the retirement-plan directory in Application Settings derives a new `export` subdirectory; users can then customize that path independently. Loading an older settings file derives the missing preference in memory without rewriting the file. A normal settings save persists it.

The Save chooser creates and validates the configured folder only after the user chooses **Save PDF**. View PDF and Cancel do not initialize this directory. An unusable configured path produces an explicit error and does not silently redirect the Save chooser.

## Architecture findings

- `ApplicationSettings` and `ApplicationSettingsService` are the existing preference model and persistence lifecycle. The new field uses that same JSON settings repository; no second preference store or plan-file change was introduced.
- Phase 17 shared PDF exports use `PdfExportDialogs` and its chooser adapter. Break-Even, Monte Carlo, individual Social Security, analyzer, history, and Results Summary also have custom chooser adapters, so each needed the shared directory preparation call.
- Plan-file Open/Save As and CSV Save remain unchanged.
- A settings service reference is attached to the main Stage and resolved through owned Stage/PopupWindow ancestry. Standalone windows without the application settings context retain the native chooser default.

## Settings and derivation behavior

- New setting: `defaultExportDirectory`, serialized with the existing Application Settings JSON.
- When absent in an older settings document, the service derives `<default plan directory>/export`; when no plan directory is set, it derives `<user home>/export`.
- This migration is lazy: constructing the service does not write settings or alter the legacy file. A normal settings save persists the derived value.
- Editing the plan-directory field derives its `export` child immediately in the dialog draft, even if the prior export path was customized. The export path remains editable after derivation.
- Editing only the export path does not change the plan path. Cancel discards both draft values. Saving persists both.
- The plan directory continues to use its existing validation. Export directory syntax is normalized on save; existence and permissions are checked when a PDF Save chooser is opened, and the directory is created at that point.

## PDF Save chooser coverage

| Report family / entry point | Chooser integration |
|---|---|
| Retirement Projection / Results Summary | `ResultsSummaryView` |
| Break-Even Analysis | `BreakEvenAnalysisDialog` |
| Couple Social Security integrated/analyzer reports | `SocialSecurityStrategyAnalyzerDialog` |
| Single-person Social Security reports | `IndividualPdfExportAction` |
| Historical associated Social Security and Monte Carlo reports | `ProjectionAnalyzerHistoryDialog` |
| Standalone Monte Carlo reports | `MonteCarloPdfExportAction` |
| Shared Phase 17 PDF workflows | `PdfExportDialogs` |

An audit of production `showSaveDialog` calls confirmed these PDF paths are covered. The separate Results Summary CSV chooser and MainWindow plan-file chooser are intentionally unchanged. Every in-scope PDF adapter calls `PdfExportDirectoryContext.prepare` immediately before opening its Save chooser, after the View/Save/Cancel workflow has selected Save. Existing suggested filenames, filters, extension handling, overwrite behavior, safe-write logic, report content, and identity guards remain in their owning exporters.

## Directory resolution and failure behavior

`PdfExportDirectoryContext.resolveAndCreate` normalizes the configured path, creates missing directories, and verifies that the result is a readable and writable directory. A missing, malformed, inaccessible, or unusable path prevents the native Save chooser from opening and displays an error. There is no fallback to an unrelated directory. No directory is created when the settings dialog opens, when the user chooses View PDF, or when the PDF workflow is canceled.

## Files changed

- `src/main/java/com/daviddunn/retirementplanner/application/settings/ApplicationSettings.java`
- `src/main/java/com/daviddunn/retirementplanner/application/settings/ApplicationSettingsService.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/settings/ApplicationSettingsDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/pdf/PdfExportDirectoryContext.java` (new)
- `src/main/java/com/daviddunn/retirementplanner/ui/pdf/PdfExportDialogs.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenAnalysisDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPdfExportAction.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualPdfExportAction.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ProjectionAnalyzerHistoryDialog.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/ApplicationPreferencesTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/settings/ApplicationSettingsUiTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/pdf/PdfExportDirectoryContextTest.java` (new)

## Verification

- Focused run: **39 tests, 0 failures, 0 errors, 0 skipped**. Covered settings persistence/migration, settings dialog Save/Cancel, chooser directory initialization, startup restoration, and analyzer introduction behavior.
- Full Maven suite: **3,986 tests, 0 failures, 0 errors, 46 skipped**; `BUILD SUCCESS`.
- `git diff --check`: passed. Git emitted only line-ending normalization notices.
- Actual JavaFX Application Settings previews were generated and inspected in both themes at 1180×820:
  - `target/phase13b-previews/settings-1180-LIGHT.png`
  - `target/phase13b-previews/settings-1180-DARK.png`
- Both directory fields, Browse controls, explanatory text, existing settings controls, and Save/Cancel actions were visible without clipping. The longer paths remained legible and the theme contrast was appropriate.

Native Windows behavior for opening the Save chooser at the configured path, directory creation under Windows permissions, and DPI scaling remains for acceptance testing. Automated tests do not launch external PDF viewers.

## Native Windows acceptance checklist

- Change the plan directory and confirm the export directory becomes its `export` child.
- Customize the export directory, close/reopen Settings, and confirm the custom path persists.
- Change only the export directory and confirm the plan directory is unchanged.
- Cancel after editing both fields and confirm neither change persists.
- Start Save PDF from Projection, Break-Even, Social Security, standalone/comparison Monte Carlo, and historical reports; verify the native chooser starts in the configured folder and existing filename/overwrite behavior remains intact.
- Confirm View PDF and Cancel do not create the configured directory.
- Test a missing directory, a path containing spaces/non-ASCII characters, and an inaccessible directory; confirm creation or clear failure without silent fallback.
- Reopen the application and verify both preferences persist.
- Check Application Settings and PDF chooser presentation in Light/Dark modes at 100%, 125%, and 150% Windows scaling.

## Git status and preservation

Branch: `main` (tracking `origin/main`). Changes remain unstaged; nothing was committed or pushed. The working tree contains the Phase 18A files listed above. No reset, clean, or destructive Git operation was used.

## Completion

Phase 18A implementation and automated verification are complete. Await native Windows acceptance before proceeding.
