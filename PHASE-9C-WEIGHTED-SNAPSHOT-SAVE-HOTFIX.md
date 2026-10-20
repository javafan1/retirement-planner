# Phase 9C weighted snapshot save diagnostics hotfix

## Finding and scope

**The reported live save failure has not been reproduced, and its original cause cannot be established from the old generic message.** A complete synthetic 5,184-successful-candidate snapshot saves and restores successfully. No size limit was raised and no financial calculation, ranking, mortality input, plan identity rule or snapshot schema was changed.

The confirmed defect is loss of diagnostic evidence: `AnalysisResultStore.persist` caught every save exception, discarded it, and completed the status future with the same generic SAVE_FAILED message. Queue rejection also discarded its exception. Consequently Retry Save could repeatedly fail without exposing why.

This hotfix retains the original exception and adds privacy-conscious full stack/cause diagnostics, stage/size/limit metadata and a user-visible failure category. It does **not** claim that the live storage problem is fixed. The next step is to obtain the live failure evidence without rerunning the calculation.

## Initial state and source trace

Initial branch/HEAD: `main` / `0fa51e012a5683bc9e82e30ce57f175146134378`. The existing Phase 9A/9B/9C work was uncommitted: 12 tracked modifications and 32 expanded untracked files. It was preserved.

The preceding final Phase 9C baseline was 3,015 tests run, zero failures/errors, 36 skips (`target/phase9c-full-final.log`). The before-hotfix reproduction test also passed: one test, no failures/errors/skips (`target/hotfix-reproduction.log`).

Inspected the actual implementations of `WeightedResultPersistence`, `AnalysisResultStore`, `AcceptedAnalysisCompletion`, `AnalysisSnapshot`, `AnalysisSnapshotCodec`, `WeightedSnapshots`, `SnapshotContext`, `JsonAnalysisSnapshotRepository` and `AtomicFileWriter`, plus the couple completion callback, snapshot fixtures and repository/store/restoration tests.

The asynchronous path is:

1. The existing accepted couple completion callback freezes the payload and report/comparison context.
2. `WeightedResultPersistence.accept` hands the immutable accepted completion to the application-owned store. Financial results are already available for display.
3. The bounded storage worker claims the plan/file binding, constructs the snapshot/digest, and calls the repository.
4. The repository validates/serializes/compresses, acquires the slot lock, validates an existing snapshot/compatibility, and invokes the transactional writer.
5. The writer prepares, forces and closes a sibling temporary file; the repository reloads/verifies it; the writer preserves the recognized backup and atomically replaces the destination. There is no non-atomic fallback.
6. A failure previously became only SAVE_FAILED. The UI observes that status and exposes Retry Save.
7. Retry queues the **same accepted completion**, rebuilding storage bytes without running financial calculations. It does not start another weighted search.

The reported wording identifies a store/queue failure, rather than an ordinary financial job failure. It does not identify which persistence operation failed. The completed-candidate count is insufficient to identify a storage problem.

## Changes

Paths below are relative to the repository. Most were already untracked Phase 9B/9C files; they are modifications to existing work, not replacements of it.

| File | Hotfix change |
|---|---|
| `src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotSaveException.java` | New narrow IOException subtype carrying stage, known byte counts and the original cause |
| `src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java` | Stage-specific encoding diagnostics; measured encoding result; existing limits named and unchanged |
| `src/main/java/com/daviddunn/retirementplanner/persistence/JsonAnalysisSnapshotRepository.java` | Distinguish lock/setup, existing-snapshot validation/compatibility, atomic writing and verification; attach known encoded sizes |
| `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisResultStore.java` | Retain original failure; log metadata/full cause stacks; expose safe failure-category status for initial save and retry |
| `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedSnapshotSaveHotfixTest.java` | New full-universe save/reload/backup, original-cause/privacy/retry, binding and verification-limit tests |
| `src/test/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotRepositoryTest.java` | Assert diagnostic stages, original causes, byte counts and unchanged primary/backup for existing fault tests |
| `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedRestorationTest.java` | Assert the failure category reaches the existing UI notice |
| `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedRestorationPreviewTest.java` | Opt-in failure-category/Retry Save rendered preview at two logical sizes |
| `PHASE-9C-WEIGHTED-SNAPSHOT-SAVE-HOTFIX.md` | This report |

`WeightedSnapshots`, `WeightedResultPersistence` and `AtomicFileWriter` were inspected but not changed. Existing shared financial/UI completion gates and retry handlers remain in place.

## Failure diagnostics and privacy

Categories cover queue admission, plan binding, snapshot construction, DTO validation, JSON serialization, gzip compression, safety limits, snapshot file locking/setup, existing-snapshot validation/compatibility, transactional writing and written-file verification.

The original throwable is retained in `AnalysisResultStore.failure(slot)` until successful persistence or replacement of that accepted result. Its complete original message, causes and suppressed exceptions remain available for local debugger inspection. A retry failure records its own new evidence.

`java.util.logging` WARNING records include analysis kind, exact stage, raw/compressed byte counts when known, and the configured limits. A size of -1 explicitly means that stage had not produced the size. Limits remain:

- Uncompressed JSON: 33,554,432 bytes; compressed file: 16,777,216 bytes.
- Tokens: 2,000,000; arrays: 100,000; depth: 80.
- String length: 256,000; number length: 1,024.

The diagnostic trace preserves every original stack frame, exception type, nested cause and suppressed failure. Arbitrary exception messages are redacted because Jackson/parser/filesystem messages can expose scalar values or paths. Audited fixed validator/writer messages, structural JSON field paths and stream-constraint diagnostics remain visible. No serialized contents, balances, benefits, names, dates of birth or plan paths are added to logging. Tests verify that sensitive synthetic exception messages do not appear while cause/suppressed stacks remain present. This is deliberate message redaction, not retention of a truncated cause chain.

Logging uses the existing JDK logging mechanism and normally appears in the application's standard-error/IntelliJ Run or Debug console. This change does **not** install a permanent log-file handler or transmit diagnostics.

The existing UI notice now includes, for example, `Failure category: written snapshot verification` or `Failure category: plan identity binding`. The original saved-result retention and Retry Save wording remain. The UI receives safe categories, not raw exception text or stack traces. Retry stays available and completed results/PDF access remain usable under the existing safeguards.

## Reproduction and preservation evidence

The new full-universe fixture uses the existing standard 5,184 election identities and deterministic synthetic completed values. It includes 5,184 successful ranked entries, a baseline, equivalence groups/representative identities, coverage/work metadata, frozen report assumptions, historical comparison data and all 5,184 frozen strategy-detail rank rows. No production search is run to construct it.

The previous size fixture lacked the full per-strategy historical detail attachment and complete baseline/equivalence graph. The new fixture exercises those together through codec round-trip, actual transactional repository storage, the accepted-completion store path, repeat save and recognized backup reload.

Final focused measurement: **6,259,359 raw JSON bytes; 220,406 gzip bytes**, with 5,184 successful candidates. Exact DTO equality and ordering survive reload. This demonstrates that candidate count and that complete synthetic graph are supported; it does not establish the size or cause of the user's actual result.

Additional deterministic checks:

- Injected write exceptions retain the original cause object, nested cause and suppressed failure; initial attempt and retry both log metadata. A subsequent retry saves the same accepted completion without recalculation.
- A duplicate live plan UUID/file binding reports PLAN_BINDING, preserves the existing snapshot and succeeds after the disposable test binding conflict is resolved.
- A deliberately overlong synthetic numeric token triggers the existing 1,024-character parser constraint during temporary-file verification. The diagnostic reports VERIFICATION and actual byte counts; the old primary bytes are unchanged. This is a fault-injection check, **not evidence that the live failure is a numeric-limit problem**.
- Existing CREATE/WRITE/FORCE/CLOSE/VERIFY/BACKUP/REPLACE fault tests retain primary and backup bytes and the injected cause. Unsupported atomic move and access-denied causes remain identifiable.
- Existing corruption and unsupported-schema guards continue to block destructive replacement.

No real retirement-plan or result-cache file was opened, changed, deleted or overwritten during diagnosis. All storage tests use disposable test directories.

## Verification

Maven: IntelliJ bundled Maven 3, JDK 25, always using `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.

- Before-hotfix reproduction: **1 passed**, no failures/errors/skips, 12.160 seconds (`target/hotfix-reproduction.log`).
- Initial focused diagnostics: **25 passed**, no failures/errors/skips (`target/hotfix-focused.log`).
- Final focused set: **63 passed**, no failures/errors/skips, 38.296 seconds (`target/hotfix-focused-final.log`). Classes: `WeightedSnapshotSaveHotfixTest`, `AnalysisSnapshotCodecTest`, `AnalysisSnapshotRepositoryTest`, `AnalysisResultStoreTest`, `WeightedRestorationTest`, `IndividualCompletionPersistenceTest`, `TransactionalPlanPersistenceTest`.
- Enabled failure preview: **1 passed**, no failures/errors/skips, 13.768 seconds (`target/hotfix-preview.log`).
- Full regression: **3,020 run; 2,983 passed; zero failures/errors; 37 skipped; BUILD SUCCESS**, 8 minutes 36 seconds, finished October 19, 2026 at 17:17:40 EDT (`target/hotfix-full.log`). The established full-suite command used `-DargLine=-Xmx8g test`. The new opt-in failure preview accounts for the one additional normal skip relative to the 36-skip baseline and passed separately. No failing test was disabled. Existing financial, identity, transactional recovery, lifecycle, analyzer/job/input/result, heat-map and PDF regressions passed.

Rendered JavaFX snapshots were inspected at 1366x768 and 900x650 logical window sizes. The wrapped diagnostic, Retry Save, prior rankings, result tabs, PDF action and Close remain available. Evidence under ignored `target/phase9c-restoration-preview/`: `hotfix-save-failure-1366x768.png`, `hotfix-save-failure-900x650.png`, and corresponding `hotfix-save-failure-status-*.png`. These are rendered logical-size previews, not native DPI/Narrator/high-contrast certification.

## Live evidence still needed, without another calculation

**Keep the application containing the completed analysis open. Do not restart it merely to install this diagnostic change, because an uncommitted result may exist only in that process.** A running JVM does not automatically acquire newly compiled classes.

If the current application is running under an attachable debugger, capture the existing failure without installing the hotfix: break on the caught exception in the loaded `AnalysisResultStore.persist` method (or a caught-exception breakpoint scoped to that worker), then click **Retry Save**. Inspect the exception before the old catch discards it. Do not click Run Analysis. Keep exception messages containing private values local; provide a redacted trace if necessary. Debugger attachment/hot replacement availability depends on how that JVM was launched; it was not attempted here.

For an application already running the updated classes, click **Retry Save** once and collect:

1. The new failure-category text and the WARNING beginning `Analysis snapshot save failed: kind=COUPLE_WEIGHTED stage=...`.
2. The full diagnostic stack, including all `Caused by` and `Suppressed` sections, and its byte-count/limit line.
3. If the underlying message was redacted and is needed, inspect the retained original throwable locally through `AnalysisResultStore.failure(slot)`; share only nonfinancial diagnostic details.
4. Whether a prior saved snapshot exists, whether storage is local/network/cloud-synchronized, and whether another application instance is using the same plan. No plan contents or full private file paths are needed initially.

That evidence is required to distinguish a live binding/compatibility/permission/lock/encoding/limit/verification failure. No specific cause is inferred in its absence. No limit increase, validation bypass, snapshot deletion, automatic recovery replacement or expensive rerun is proposed.

## Remaining risks and Git status

The live persistence problem remains unresolved pending its exception. This hotfix corrects diagnostic loss and verifies safe retry/preservation, rather than claiming a guessed storage correction. Existing Phase 9C restoration and Phase 9B identity behavior are unchanged.

Final branch/HEAD are unchanged. Expanded `git status --short --untracked-files=all` contains **12 tracked modifications and 35 untracked files**: all initial work plus the three new hotfix files (exception, regression test, report). Six other hotfix files already belonged to the untracked Phase 9B/9C work. The exact nine-file hotfix inventory is above; the pre-existing tracked diff remains 12 files, 429 insertions and 25 deletions.

`git diff --check` passes. Separate whitespace inspection covers the hotfix's untracked sources/tests/report. The staged diff is empty. `pom.xml`, financial golden/resources directories, financial engines and `AtomicFileWriter.java` have no diff. No additional domain, identity, UI-navigation or PDF-content changes were made by the hotfix. Generated logs/previews and the project-local Maven repository remain ignored.

Nothing has been staged, committed, pushed, reset or discarded. This diagnostic hotfix is ready for review; resolution of the actual live save failure awaits the evidence described above.
