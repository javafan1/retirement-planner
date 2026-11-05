# Phase 19B4-S2 — Privacy-Safe Save Failure Diagnostics

## Summary

Added diagnostic capture to the existing Save and Save As failure handlers without changing plan serialization, validation, atomic writing policy, or the user-facing error text. The original exception and cause chain remain intact through `awaitPlanFile`; a privacy-filtered record is emitted without attaching/stringifying the `Throwable`.

This change is diagnostic only. It does not repair or explain the native failure. The earlier Phase 19B4-S1 synthetic reproduction remains green, and the captured native root was not available to this environment.

## Diagnostic record

On Save/Save As failure, the handler records:

- Operation (`SAVE` / `SAVE_AS`).
- Packaged application product version when available.
- Exception class names from a cycle-protected, bounded cause chain (maximum 8).
- Up to 12 sanitized `class#method` frames from `com.daviddunn.retirementplanner` only.
- Last known save stage.
- Whether bytes were written to a sibling temporary file.
- Whether the final atomic replacement of the target was actually attempted.
- A timestamp.

Exception messages, stack-frame file names and line numbers, method arguments, full paths, plan values, JSON, account data, and user input are excluded. A fault before the final `Files.move` reports `atomicReplacementAttempted=false`; a fault after the move is attempted reports `true`. The write-start flag becomes true only immediately before writing bytes to the temporary file.

The diagnostic is written to the existing JDK logging facility and to the bounded application diagnostic file:

`%USERPROFILE%\\.retirement-planner\\diagnostics\\plan-save-diagnostics.log`

The fixed diagnostic file is capped at 256 KiB; when a new record would exceed that limit, the old contents of this dedicated log are truncated. The directory/file receive owner-only POSIX permissions where supported; Windows inherits the user-profile ACL. Symbolic-link/non-regular diagnostic targets are refused. Logging/storage errors are swallowed after a static, path-free warning and cannot replace the original Save exception. The diagnostic directory is separate from the selected retirement-plan directory.

## Stage tracking and Save path preservation

`ApplicationController` holds one transient diagnostic context for the in-progress/current plan save. It records owner capture/copy reconstruction, then `JsonRetirementPlanRepository` records validation, projection-component preparation, JSON serialization, round-trip verification, and writer stages. `AtomicFileWriter` records temp creation/write/force/close, candidate verification, backup preparation, and final target replacement.

The diagnostic propagation retains the repository's existing four-argument `saveWithIdentity` virtual dispatch by scoping the context with a `ThreadLocal`. Existing repository test doubles and subclasses continue to intercept the same method. No write, backup, validation, rollback, or atomic-move behavior changed. Successful receipt and preference completion update the diagnostic context to `COMPLETE`.

Save and Save As failure alerts retain their generic wording. No raw trace or internal message is shown to users.

## Files changed for S2

Production:

- `src/main/java/com/daviddunn/retirementplanner/persistence/PlanSaveDiagnostics.java` — context, bounded sanitizer, JDK logger and capped private diagnostic file.
- `src/main/java/com/daviddunn/retirementplanner/persistence/JsonRetirementPlanRepository.java` — stage instrumentation around validation, component preparation, serialization and round-trip verification; diagnostic scope preserving existing virtual dispatch.
- `src/main/java/com/daviddunn/retirementplanner/persistence/AtomicFileWriter.java` — temp write and target atomic replacement stage/flag instrumentation.
- `src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java` — per-operation context and capture/receipt lifecycle.
- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java` — sanitized logging from both existing Save/Save As exception handlers.

Tests:

- `src/test/java/com/daviddunn/retirementplanner/persistence/PlanSaveDiagnosticsTest.java` — message/path exclusion, bounded frames/log size, temporary write and atomic replacement status.
- `src/test/java/com/daviddunn/retirementplanner/ui/SocialSecurityReductionSaveWorkflowTest.java` — preserved exception cause and stage for a worker failure, plus isolated reduction Save/Save As round trips.

Preexisting Phase 19A–19B4 working-tree files and the S1 report/test were preserved.

## Verification

Focused command covered diagnostic formatting/storage, controller Save/Save As, transactional writer, plan JSON compatibility, projection snapshots, analyzer associations, and swaps:

- **91 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.**

The complete Maven regression suite on the final production source was run with IntelliJ's bundled Maven and the repository-local `.codex-m2/repository`:

- **4,032 tests, 0 failures, 0 errors, 46 skipped; BUILD SUCCESS.**
- Skipped tests were existing opt-in, benchmark, or visual-preview cases.
- `git diff --check` completed successfully. Git emitted only existing line-ending conversion notices for modified files.

## Native retest and log retrieval

After a native Save/Save As failure, retrieve the diagnostic from `%USERPROFILE%\\.retirement-planner\\diagnostics\\plan-save-diagnostics.log` and share only the newest `Plan save failure diagnostics` record. It contains no full plan paths or values. The stage and flags will distinguish a pre-write analyzer/snapshot failure from temporary-file, backup, or atomic target replacement failure. Save As should be retried only after preserving the original plan and backup; this phase does not authorize overwriting either as a diagnostic step.

The S1 reproduction did not fail, so this phase cannot claim that the original native Save defect is repaired. The underlying cause must be investigated using the newly captured safe record. S3 has been queued and has not been started.

## Git preservation

All existing Phase 19A–19B4 modifications, untracked files, the S1 report, and the S1 test were preserved. No plan files were accessed. No files were staged, committed, pushed, reset, cleaned, or reverted.
