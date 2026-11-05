# Phase 19B4-S1 — Save and Save As Failure Investigation

## Executive summary

The native Windows Save/Save As failure could not be reproduced in this environment, so its root cause is not established. Per the root-cause gate, no production code was changed and no speculative persistence correction was made.

An isolated regression test exercised the same `ApplicationController.preparePlanSave` capture/reconstruction path, `JsonRetirementPlanRepository.saveWithIdentity`, verified atomic writer, and `finishPlanSave` receipt path used by the application. It successfully saved and reloaded 0%/2032, 22%/2032, 17%/2034, changed 22% back to 0%, and Save As variants. It also covered a new plan Save As, a legacy document missing the two properties, and retained projection/baseline state. Focused persistence and controller tests passed: **86 tests, 0 failures, 0 errors, 0 skipped**.

This is evidence that these tested inputs and local temporary destinations do not fail in the shared persistence path. It does not identify the exception from the user's acceptance run, verify the user's destination filesystem, or establish that the native failure is fixed.

## Reported failure

The user set Social Security Benefit Reduction to 22% and Effective Year to 2032, applied the assumption, then received the same generic “Unable to save plan” message for both File → Save and File → Save As to a different filename. No user plan was accessed, copied, edited, or used in testing.

## Save and Save As execution trace

The relevant production flow is:

1. `MainWindow.onSave()` or `MainWindow.onSaveAs()` checks busy state and applies the existing Assumptions/Roth/Household and other view edits through `saveCurrentPlan()`.
2. Save As obtains a destination with the native chooser; Save uses the current file.
3. Both call `ApplicationController.preparePlanSave(destination, saveAs)`.
4. On the owner/FX thread, that method freezes the plan and baseline into `CompleteProjectionSnapshot` documents and captures projection manifests/components, pending associations, analyzer configuration, and identity metadata.
5. On the dedicated `retirement-plan-file` worker, it restores an isolated plan copy from the frozen inputs, restores the baseline, attaches captured projection/analyzer metadata, then calls `JsonRetirementPlanRepository.saveWithIdentity`.
6. The repository validates household references, prepares projection components, serializes through its Jackson `ObjectMapper`, replaces the serialized projection manifest when applicable, reads the candidate bytes back through the production Jackson deserializer/plan validation, and passes the verified bytes to `AtomicFileWriter`.
7. The writer creates and forces a sibling temporary file, validates it, checks the commit guard, verifies any existing target before backup work, writes/updates the owned adjacent backup, and atomically replaces the target. Failure cleanup removes incomplete temp files where possible and restores the previous backup if replacement did not commit.
8. On success, the UI calls `finishPlanSave(receipt)` to publish the saved identity/state and update application preferences.

Save and Save As therefore converge at `preparePlanSave` and the repository's `saveWithIdentity`/atomic writer. Save As differs in destination and whether the captured plan receives a forked identity; it does not bypass plan capture, Jackson round-trip verification, or atomic writing.

## Exception and diagnostic evidence

There was no exception in the deterministic reproduction. Therefore there is no failing class, root cause, first failing method, file operation, or exception chain to report for the native incident.

The current `MainWindow` handlers catch `Exception ex`, set the status to “Save failed.”, and show `showSaveFailure()` without logging or retaining `ex`. The generic dialog is not evidence that the failure occurred in the atomic writer; the catch can also receive a worker failure from plan capture/reconstruction, Jackson, verification, filesystem I/O, atomic replacement, or receipt completion. The exception is currently masked from diagnostics.

For the successful isolated writes:

- The candidate plan serialized and deserialized through the repository's production mapper.
- A sibling temporary file was used by the atomic writer.
- Existing-target saves completed the backup/replace path.
- Save As to new destinations completed without changing the prior destination.
- The files reloaded with exact percentage values and effective years.
- No test fixture file was damaged or lost.

Because the failing native run cannot be reproduced, it is not possible to state whether its temporary file was created, whether backup creation started, or whether the destination was modified. The writer's existing transactional tests separately verify failure preservation at CREATE, WRITE, FORCE, CLOSE, VERIFY, BACKUP, and REPLACE stages.

## Phase 19 serialization audit

`EconomicAssumptions` keeps the two properties as immutable values. The Jackson creator takes nullable values and maps missing or explicit-null values to 0%/2032; invalid non-null values are rejected. Custom inclusion filters omit the default percentage/year but include a nonzero percentage or nondefault year. The production repository's `ObjectMapper` serializes and then deserializes candidate plan bytes before commit. New properties also pass through `CompleteProjectionSnapshot` freezing/restoration.

The test reproduction confirmed round-trip behavior for included values and defaults through the complete plan/controller path. It did not find a serialization-versus-deserialization failure. No evidence supports changing conditional serialization, creator properties, snapshots, or fingerprints.

## Reproduction coverage and results

Isolated temporary-directory cases exercised:

| Case | Operation | Outcome |
|---|---|---|
| Existing plan, 0% / 2032 | Save | Saved and reloaded |
| Existing plan, 22% / 2032 | Save | Saved and reloaded |
| Existing plan, 22% / 2032 | Save As | Saved and reloaded |
| New plan, 22% / 2032 | Save As | Saved and reloaded |
| Existing plan changed 22% → 0% | Save | Saved and reloaded |
| Existing plan, 17% / 2034 | Save | Saved and reloaded |
| Existing plan, 22% / 2034 | Save As | Saved |
| Legacy file with new properties omitted | Load then Save | Loaded with compatibility defaults and saved |
| Plan with accepted projection and baseline | Save/Save As paths | Projection manifest and baseline remained serializable |

The repository serialization/persistence test additionally covers explicit-null defaults. The adjacent analyzer-association, projection snapshot, projection swap, and atomic-failure suites passed. A combined fixture with a retained historical analyzer association and nonzero reduction was not produced; the native failure itself was not reproducible, and no indication from existing association tests implicated those records.

The reproduction was run against isolated `%TEMP%`/JUnit temporary paths. It does not exercise the user's actual directory, permissions, endpoint protection, sync software, lock state, filesystem atomic-move support, or exact plan document.

## Root-cause gate and correction decision

**Root cause: undetermined.** The tested common path succeeds; the native error is masked by the current UI handler, and the destination/environment involved in the acceptance failure is unknown. It cannot be established whether Phase 19 introduced the problem. No production correction was made because doing so without a reproducible failing operation would be speculative and could weaken the existing file-preservation guarantees.

## Files changed in this investigation

- Added `src/test/java/com/daviddunn/retirementplanner/ui/SocialSecurityReductionSaveWorkflowTest.java` to exercise isolated application-controller Save/Save As flows and Phase 19 values.
- Added this investigation report.
- No production files, user plans, backups, historical records, or existing reports were modified.

## Verification

Focused command used IntelliJ's bundled Maven and the repository-local `.codex-m2/repository`:

`-Dtest=SocialSecurityReductionSaveWorkflowTest,SocialSecurityReductionPersistenceTest,PlanFileOperationsTest,TransactionalPlanPersistenceTest,JsonRetirementPlanRepositoryTest,ProjectionSnapshotPersistenceTest,AnalyzerAssociationStorageTest,ProjectionSwapTest test`

Result: **86 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. The focused run covered actual detached controller Save/Save As orchestration, JSON compatibility, projection snapshot persistence, analyzer association persistence, swap, and atomic failure behavior. No full Maven suite was run because the root-cause gate stopped production implementation; none was necessary to validate a correction because no correction was made.

`git diff --check` is recorded after the report and test addition.

## Remaining limitation and requested native retest diagnostics

The acceptance failure still needs a captured exception from the running application or reproduction on the same destination environment. The current UI intentionally does not reveal stack traces, and this investigation did not add diagnostic logging because the fault could not be reproduced and a production diagnostic change would be an ungrounded code change.

For the next retest, capture the complete exception chain in a developer/debug diagnostic channel or provide the exact exception from the application log, without including plan JSON or personal financial details. Record whether Save and Save As target local NTFS paths, whether the target or adjacent `.bak`/`.bak.rp-owner` files already exist, and whether either path is synchronized, network-mounted, locked, or protected by endpoint software. Preserve the plan and adjacent backup unchanged until the failure is understood. Retest Save As to a new local folder only if the user can safely choose a separate destination; do not overwrite or move the original plan as a diagnostic step.

## Git preservation status

All preexisting Phase 19A–19B4 modified and untracked files were preserved. The only additions from this investigation are the diagnostic test and this report. Nothing was staged, committed, pushed, reset, cleaned, or reverted. No actual user plan was accessed.

**Stop here pending a reproducible native failure or its underlying exception. No Phase 19 completion or repair is claimed.**
