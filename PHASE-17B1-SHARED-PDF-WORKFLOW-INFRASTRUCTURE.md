# Phase 17B1 — Shared PDF View/Save Workflow Infrastructure

## Summary

Implemented reusable, testable JavaFX infrastructure for the approved PDF **View PDF / Save PDF / Cancel** workflow. Existing PDF export buttons, report adapters, report generators, filenames, and PDF contents remain unchanged. No current export entry point invokes the new dialog or coordinator.

The shared infrastructure separates the choice UI, user-selected Save destination, report capture, background rendering, OS viewer launch, completion/error callbacks, and private preview-file storage. The coordinator accepts a frozen report value from the caller and does not run projections or analysis. A safe startup hook performs best-effort stale-preview cleanup on the existing startup worker.

## Architecture and contracts

- `PdfExportChoiceDialog` is a compact owner-aware JavaFX dialog. View is first and is the default Enter action; Save follows; Cancel, Escape, and closing the dialog map to cancellation. Its JavaFX nodes receive the centrally managed Light/Dark CSS.
- `PdfExportWorkflowCoordinator<R>` receives its choice prompt, Save chooser, `PdfPreviewStore`, `PdfOpener`, worker `Executor`, and FX dispatcher as dependencies. A request supplies readiness/authorization, suggested filename, one FX-thread report-capture supplier, a renderer accepting the captured report and destination `Path`, a completion-time validity guard, and busy/success/failure callbacks.
- The coordinator asks for a choice before capture. Cancel does no capture, chooser, rendering, or preview creation. Save opens the injected chooser after selection. View bypasses the chooser. Both accepted paths capture once and render on the injected worker. The opener runs on that worker only after the preview store publishes a complete PDF. Callers should provide immutable, already-frozen report data and owner/result identity guards appropriate to their workflow.
- Busy state is owned on the FX thread; a second start is rejected while a worker task is active. Completion and error callbacks are dispatched to the FX thread and suppressed when the caller's validity guard reports that the originating result/view is no longer valid.
- `DesktopPdfOpener` uses `java.awt.Desktop` capability checks and `Desktop.open`; it does not invoke a shell. Unsupported desktop/open actions, security restrictions, and I/O failures are surfaced as errors. Viewer launch is only a launch request; the coordinator does not claim that a viewer has finished loading the document.
- `PdfExportDialogs` supplies standard dialog and chooser adapters plus a failure alert. The Save chooser is injectable so later integrations can preserve per-export extension and overwrite-confirmation policies rather than standardizing them here.

The existing four PDF exporter services and their structured report adapters were inspected and left unchanged. Projection and Break-Even remain direct-path exporters; Monte Carlo and Integrated Analyzer retain their current safe sibling-temp-and-move implementation. Those internal exporter temp files are not used as viewer previews.

## Preview storage and cleanup

The store uses an application-managed directory derived from the existing per-user application-data convention:

- Windows: `%LOCALAPPDATA%\RetirementPlanner\pdf-previews\v1` (falling back to the user's `AppData\Local` directory when `LOCALAPPDATA` is unavailable).
- macOS: `~/Library/Application Support/RetirementPlanner/pdf-previews/v1`.
- Linux/other Unix: `$XDG_DATA_HOME/RetirementPlanner/pdf-previews/v1`, or `~/.local/share/RetirementPlanner/pdf-previews/v1` when XDG data home is unavailable.

The directory is created only when a preview is requested. POSIX permissions are restricted to the current user where supported; Windows uses the inherited ACL of the per-user local application-data directory. A store identity file and per-preview owner sidecar verify which files Aledrix owns. PDF names contain only a random UUID (`preview-<uuid>.pdf`), never plan or account details. A renderer writes to an ownership-marked `.part` file; the store verifies it is a regular, non-symlink PDF with both a `%PDF-` header and a `%%EOF` terminator, then publishes it to a unique final name without replacement. The opener receives only the published path.

Completed previews remain after successful or failed viewer launch. Failed/incomplete writes are removed best-effort. Startup launches `cleanupStale()` on an independent daemon virtual thread; startup and the JavaFX thread do not wait for directory scanning or stale-file removal. It checks ownership sidecars, direct-child paths, regular-file status and symlink status, and deletes only store-owned partial or complete previews whose file modification time is at least 30 days old. Permission errors, locked files, and concurrent cleanup races are tolerated; a file that cannot be deleted remains for a later cleanup attempt. Cleanup is not allowed to fail application startup. Manually remove retained reports by closing Aledrix and deleting files from the `pdf-previews/v1` folder shown above; do not delete the ownership file if previews should continue to be cleaned automatically.

## Visual verification

The actual JavaFX-rendered choice dialog was captured and inspected in both themes:

- `target/phase17b1-pdf-choice/choice-light.png`
- `target/phase17b1-pdf-choice/choice-dark.png`

Both previews show readable wrapping, visible View/Save/Cancel buttons, good theme contrast, and View as the emphasized default action. The dialog is owner-aware and does not add focusable content beyond its three actions. JavaFX theme-manager window tracking applies the active theme to the dialog.

## Changed files

Production:

- `src/main/java/com/daviddunn/retirementplanner/ui/pdf/PdfExportChoice.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/pdf/PdfExportChoiceDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/pdf/PdfExportWorkflowCoordinator.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/pdf/PdfExportDialogs.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/pdf/PdfPreviewStore.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/pdf/PdfOpener.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/pdf/DesktopPdfOpener.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/MainApplication.java` (best-effort startup cleanup hook only)
- `src/main/resources/css/application.css` (choice-dialog theme styling)

Tests:

- `src/test/java/com/daviddunn/retirementplanner/ui/pdf/PdfPreviewStoreTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/pdf/PdfExportWorkflowCoordinatorTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/pdf/PdfExportChoiceDialogPreviewTest.java`

The pre-existing Phase 17A audit remains unchanged.

## Test results

Focused final verification:

- New infrastructure tests: 10 executed, 10 passed, 0 skipped.
- Existing focused PDF workflow tests: 33 executed, 33 passed, 0 skipped.
- Combined focused PDF run: 43 passed, 0 failures, 0 errors, 0 skipped.
- Final startup-lifecycle plus PDF rerun: 47 passed, 0 failures, 0 errors, 0 skipped.

Full Maven regression on final source: **3,965 tests, 0 failures, 0 errors, 46 skipped; BUILD SUCCESS** (11m 19s). Skips are those reported by the existing suite (including opt-in benchmark/visual tests); no tests were excluded by this run.

`git diff --check`: passed.

No test launches a real external viewer. Viewer success/failure behavior is tested through an injected fake. Filesystem symlink creation is platform/permission-dependent; the tests attempt it and retain the other path-safety assertions where symlink creation is unavailable.

## Scope and limitations

- No production export button, menu item, filename, report content, report metadata, financial computation, plan persistence, analyzer eligibility, or result identity was changed.
- Save extension and overwrite differences remain the responsibility of the adapters supplied by later per-export integrations.
- The opener cannot confirm that the external viewer finished reading a report. Therefore preview deletion is delayed to stale cleanup rather than tied to viewer launch or application shutdown.
- Native Windows association failures, ACL inspection, and locked-file cleanup require acceptance testing on the target Windows installation. Automated tests do not open the viewer.
- No PDF export action is integrated in Phase 17B1; that work remains for a later approved stage.
