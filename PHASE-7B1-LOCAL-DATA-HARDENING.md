# Phase 7B.1 — Local data hardening

Completed October 15, 2026. No Phase 7B.2 or distribution work.

## 1. Executive summary

Implemented local transactional plan/settings persistence, bounded adjacent
backups, explicit review-first backup recovery, nonfatal settings failures and
privacy-safe persistence notifications. No installer, networking, licensing,
telemetry, schema migration, autosave, encryption or financial changes.

## 2. Initial persistence audit (recorded before implementation)

Initial status: only the pre-existing, untracked Phase 7A report. That report is preserved.

`JsonRetirementPlanRepository` validates household references, then directly writes
Jackson JSON to the selected destination. New Save, overwrite and Save As share
this path. Serialization/write failure can truncate an existing file. Load reads
original bytes, checks explicit-null state configuration, deserializes with
JavaTimeModule and validates household references. Those JSON rules must remain.

`ApplicationController` clears dirty state only after repository save, and changes
the Save As path only after success, but subsequently failing settings writes
escape as apparent plan failures. Open activates a successfully loaded plan then
writes preferences; preference failure can interrupt cache invalidation. Last-plan
opening catches only IOException, clears the preference and writes settings again.
Settings load wraps corrupt/unreadable JSON in RuntimeException, potentially
preventing MainWindow construction. Settings writes directly overwrite the file.

`MainWindow` validates drafts before Save As; cancelling its native chooser returns
before applying edits or changing the active path. Existing chooser overwrite
confirmation remains platform-owned. Save applies edited views and can refresh
projections; this phase will not add calculation calls. Open uses the existing
departure confirmation. Failure handlers currently print raw exceptions and only
set status text. MainApplication constructs the window before showing the stage.

Ownership: model/repository validate; Jackson serializes; repositories write;
controller owns dirty/path/preferences; MainWindow owns notifications. There is
currently no backup/replacement/recovery owner. No dedicated settings tests were
found; repository and wizard end-to-end tests cover existing JSON/workflow behavior.
PDF/CSV output is mixed direct-write and temporary-replace, deferred unchanged.

## 3. Design decisions

Use a small NIO writer with deterministic operation hooks, sibling unpredictable
temporary files, force/close, repository verification and atomic moves only. Never
remove the primary before replacement. Serialize once to immutable bytes using
the existing mapper configuration. Serialize saves across repository instances in
this process; cross-process edits remain outside a distributed-lock guarantee.

Validate an existing primary before backing it up. Refuse a corrupt primary and
offer review of its validated adjacent backup as an unsaved, untitled session.
Save of that session uses Save As, never the backup source. Keep exact original
bytes in backups. Do not silently fall back to non-atomic replacement.

Java NIO specifies that ATOMIC_MOVE ignores other options; replacement of an
existing target is provider-specific. Unsupported/refused atomic replacement must
fail safely. Atomic rename is not a universal power-loss durability guarantee.
Official reference: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Files.html

The implementation uses exactly one new production class, package-private
`AtomicFileWriter`, with package-private failure hooks rather than a storage
framework. Existing repositories retain ownership of JSON interpretation;
ApplicationController retains session state; MainWindow remains the UI adapter.

## 4. Exact files changed

Created:

- `src/main/java/com/daviddunn/retirementplanner/persistence/AtomicFileWriter.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/TransactionalPlanPersistenceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/TransactionalSettingsTest.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/PersistenceControllerRecoveryTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/PersistenceRecoveryWorkflowTest.java`
- `PHASE-7B1-LOCAL-DATA-HARDENING.md`

Modified:

- `src/main/java/com/daviddunn/retirementplanner/persistence/JsonRetirementPlanRepository.java`
- `src/main/java/com/daviddunn/retirementplanner/persistence/JsonApplicationSettingsRepository.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/MainApplication.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/SinglePersonPlanPersistenceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/RothConversionPersistenceTest.java`

Phase inventory: **6 created, 7 modified**; six production files including the
new helper, six test files and this report. The pre-existing untracked Phase 7A
report is excluded and unchanged. Test/build logs are ignored under `target/`.

## 5. Transactional save implementation

1. Hold one process-wide persistence mutex, including repository validation and
   capture, so concurrent repository instances cannot interleave saves.
2. Apply existing household validation. Serialize once to immutable UTF-8 bytes
   using the existing Jackson module/indent configuration. No active-plan reload
   or mutation is performed.
3. Create an unpredictable sibling `.filename.rp-<random>.tmp`, write using a
   FileChannel, force file content/metadata, and close it.
4. Reload the temporary file through the existing repository's load rules. Its
   validation result is discarded; it never replaces the active in-memory plan.
5. If replacing a primary, validate that existing primary, prepare a byte-exact
   backup candidate and verify it. Preserve existing backup bytes in a separate
   owned rollback temporary file before changing the backup.
6. Publish the backup atomically, then atomically move the prepared new plan to
   the original destination. The original is never moved away first. Successful
   completion of the atomic move is the commit point.
7. Before commit, a failed replacement rolls back the backup to its previous
   bytes (or removes the newly created backup if none existed). The primary was
   never removed. If rollback itself fails, preserve its temporary bytes and
   attach the internal cause; never delete the only retained older bytes.
8. Clean up only explicitly tracked temporary paths from this operation.
   Post-commit cleanup failure becomes a warning, not a false save failure.

`ATOMIC_MOVE` is mandatory for both replacements. Java specifies that its other
move options are ignored; providers may atomically replace or refuse an existing
target. The tested Windows filesystem accepted replacement. Unsupported atomic
move produces an actionable IOException; no delete-first or ordinary-move fallback
exists. Windows locks, permission failures, antivirus and cloud synchronization
can cause safe refusal. No platform-independent promise is made about every
filesystem/provider or power-loss durability. The directory itself is not fsynced.

## 6. Backup behavior

The sole normal backup is `MyRetirementPlan.json.bak`, containing the immediately
previous valid primary's exact bytes, not a transformed JSON representation.
An adjacent `MyRetirementPlan.json.bak.rp-owner` identifies the reserved backup
slot with a fixed format and destination filename. This is local ownership
metadata, not a plan schema field, authentication mechanism or protection against
malicious same-user tampering. It prevents silent adoption/overwrite of an
unrelated pre-existing `.bak`. A matching marker is required before replacing an
existing backup. Preserve/rename an unrecognized backup and its metadata manually
before retrying; never delete the user's only recoverable copy.

| Situation | Implemented outcome |
| --- | --- |
| Valid primary, no backup | Prepare/verify previous bytes, create ownership record, atomically publish backup and primary |
| Valid primary, recognized valid backup | Retain old backup for rollback; rotate only after new/previous candidates are ready |
| Valid primary, recognized corrupt backup | Replace corrupt backup with verified primary; restore prior backup bytes if primary commit fails |
| Corrupt/empty primary, valid backup | Refuse ordinary Save; preserve both; offer review-first recovery |
| Corrupt primary, corrupt/missing backup | Refuse overwrite and report no valid automatic recovery; Save As to another path remains possible |
| Missing primary, valid backup | Backup can be reviewed explicitly; a new save at the missing primary does not rotate/erase backup |
| Unrecognized backup or ownership record | Refuse overwrite rather than silently destroying user files |
| Read-only primary | Refuse before preparing replacement; original remains |
| Backup publication/permission failure | Primary remains; no primary replacement attempted |
| Crash after backup publication, before primary commit | Primary remains valid; published backup also contains that valid primary; older backup may remain in an owned rollback temporary |
| Crash after primary commit | New primary plus previous valid primary as backup; crash remnants may remain |

Normal operation retains one backup and one small ownership record. It does not
create a version history. Abrupt crashes can leave temporary files; they are
conservatively retained rather than scanned/deleted. A marker left incomplete by
a crash fails closed on the next attempted backup rotation.

## 7. Recovery workflow

Manual Open failure offers **Review backup** only after the exact adjacent backup
is loadable using current repository validation. Its filename alone does not make
it valid. Confirmation is required; no original/backup is written by review.
The session records original path, backup source and recovered in-memory plan.
It is marked dirty with **no active save path**. Save routes through existing
Save As; directly attempting controller Save without a path is rejected. Save As
to the backup source is rejected. Saving over the damaged original is also refused
by the corrupt-primary rule; use a different filename, then manage damaged files
explicitly after review. Successful Save As activates only the new path. Direct
Open of an application-owned `.bak` also becomes this unsaved recovery session;
selecting the backup explicitly is the user's review action. Repository Save
refuses a recognized backup destination even outside that controller workflow.

Startup auto-open failure preserves the last-file preference and file, records a
warning and continues with a usable unsaved default session. After the stage is
visible it offers the same explicit recovery when a valid backup exists. Manual
Open remains available. Missing/corrupt backup and recovery-read failure leave the
previous active session intact.

No broad scanner or automatic temp recovery exists. For an interrupted-save
remnant, preserve the original and backup, copy a chosen remnant to a **different
disposable filename**, then use Open to validate/review it. Do not infer authority
from modification time or automatically delete unknown files. Cleanup uses only
operation-owned paths; deleting a link deletes that directory entry, not its
target. Primary/backup non-regular files and symlinks are refused for replacement.

## 8. Settings resilience

Default location remains `${user.home}/.retirement-planner/settings.json`.
Missing settings use defaults. Invalid JSON, empty/null JSON or unreadable input
use defaults and queue a concise warning; the damaged file is preserved.
The original settings-load exception is retained only in the repository for
local debugging, never serialized, displayed as raw text or transmitted.
Automatic preference writes are disabled for that repository session after a
failed load. Back up/rename the damaged settings file and restart to restore
normal preference saving. No automatic quarantine/deletion or location migration.

Writes capture JSON, prepare/force/close a sibling temp, verify deserialization
and replace atomically without a non-atomic fallback. Settings do not use plan
backups or the plan recovery dialog. Controller catches preference-write failure
after successful plan operations and queues a nonfatal notice. The UI status bar
retains the successful operation message and adds a local-storage notice with a
wrapped tooltip containing details. Startup shows notices after the stage exists;
it does not depend on remote diagnostics or an additional modal settings dialog.

## 9. Dirty-state behavior

| Operation | Path / dirty result |
| --- | --- |
| Save succeeds | Existing active path; clean only after commit |
| Save As succeeds | New path becomes active after commit; clean |
| Save/Save As fails before commit | Previous path and dirty state retained |
| Backup or atomic move fails | Same failed-save behavior; existing file preserved |
| Settings write fails after plan commit | Plan is saved/clean; warning only |
| Save As cancelled | No file/path/dirty/revision changes; native chooser behavior retained |
| Recovery accepted | Unsaved recovered plan, original/source metadata, dirty, no active path |
| Cleanup fails after commit | Saved/clean; warning, possible owned temp remnant |

Controller saves add no projection calls and do not invalidate projection caches.
Existing UI draft collection/refresh behavior is retained, not expanded. Open
still invalidates caches as before, now without preference failure interrupting
that process. UI notifications use generic messages without paths, financial
contents or raw stack traces for these persistence paths. Existing non-persistence
tax/projection/Monte Carlo diagnostics were not changed.

## 10. JSON compatibility evidence

No mapper features, model constructors/defaults, top-level JSON fields, migrations,
reference rules or financial scalars changed. Serialization uses the same
ObjectMapper/JavaTimeModule/INDENT_OUTPUT setup. Load continues deserializing original
numeric bytes after explicit-null checks, preserving existing BigDecimal scale.

Focused existing tests cover legacy assumptions, ownership and single/couple
plans, opening RMD data, Roth requests, state configuration and baseline contents.
`IowaProductionProjectionTest` verifies repeated financial rows and save/reload
equivalence for single and couple plans with conversions/RMDs. Wizard end-to-end
tests exercise the actual controller Save As/Save/Open path and compare plan trees.
The full financial suite is the additional regression gate below; no goldens
were regenerated.

Two fixture adjustments reflect the intentionally stricter filesystem contract:
Roth persistence tests now save to nonexistent paths inside @TempDir instead of
creating empty destination files first; all Roth assertions remain. The single
person invalid-load test now asserts corrupt-primary overwrite refusal and uses
a separate disposable file for its independent death-scenario assertion. No
financial expected values or validation assertions were weakened.

## 11. Failure-injection tests

Package-private writer hooks cover create, write (including partial bytes), force,
close, verification, backup preparation, atomic replacement and cleanup. Tests
assert byte-exact original and backup preservation, not just exceptions. A failing
ObjectMapper covers serialization; invalid ownership covers model validation.
Corrupted temp content tests actual verification reload. Atomic-move refusal is
injected deterministically. Controller/repository seams exercise settings write,
settings load and recovery read failures without permission hacks or sleeps.

A subprocess halts with exit code 17 immediately before primary replacement,
after backup publication. The parent verifies both primary/backup are valid and
unchanged primary bytes survive, with conservative crash remnants retained.
Concurrent repository instances target the same file and verify two complete
versions, without mixed JSON. This does not test storage-controller power loss.

JavaFX tests invoke the real recovery dialog with an affirmative review action,
assert unsaved state/source preservation, and cancel Save As through a narrow
native-chooser seam. No artificial sleeps or financial test changes.

## 12. Focused test results

Final focused command used IntelliJ bundled Maven and the required project-local
repository, selecting the four new classes, existing persistence tests, repository
tests, wizard end-to-end/lifecycle and Iowa projection persistence tests.

**88 run / 88 passed / 0 failures / 0 errors / 0 skipped**, 20.830 seconds.
Log: `target/phase7b1-focused.log`. New tests comprise 21 transactional-plan,
4 settings, 7 controller/recovery and 2 JavaFX workflow cases (34 total).

Development verification initially exposed the corrupt-file fixture reset and
empty pre-created destination assumptions described above. A development run was
stopped after a modal settings notice blocked UI automation; the implementation
was corrected to a nonmodal status/tooltip notice. These were corrected runs,
not suppressed/ignored tests. An initial full run was stopped and restarted after
final review added retained local settings exception causes and direct read-only/
exact-byte precision tests. The final focused run passed against that final source.

## 13. Full test-suite results

**Final full suite: 2,824 run / 2,796 passed / 0 failures / 0 errors /
28 skipped**, BUILD SUCCESS, **7:37**. Maven summary and independently summed
Surefire XML agree. Finished October 15, 2026 at 17:14:23 America/New_York.
Existing financial regression tests passed without expectation updates. The
28 skips are existing conditional/optional cases; all 34 new cases passed.
The preceding completed full checkpoint passed 2,823 run / 2,795 passed /
0 failures / 0 errors / 28 skipped in 7:09. A final direct-backup-Open safety guard
and regression test were added afterward, and the full suite was rerun rather
than treating that checkpoint as final-source verification. Checkpoint log:
`target/phase7b1-full-checkpoint.log`.
Command: IntelliJ bundled `mvn.cmd`,
`-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`,
`-DargLine=-Xmx8g test`. Log: `target/phase7b1-full.log`.

## 14. Known limitations

- Atomic replacement is filesystem/provider-specific and deliberately fails if
  unsupported/refused. No promise of universal hardware/power-loss durability.
- One process-wide mutex prevents in-process save interleaving, not malicious
  same-user path races or changes made by another application/process. No prior
  cross-process conflict detector existed; external edits require user care.
- UI-thread ownership of mutable plan editing remains; unrelated threads must not
  mutate the plan during serialization. Serialized bytes are fixed thereafter.
- Backup/primary are two separate atomic publications, not a multi-file filesystem
  transaction. Rollback restores ordinary failed saves. A second failure while
  rolling back can retain older backup bytes in an owned temporary file; it never
  removes the still-valid primary to attempt repair.
- Permissions/ACLs of prepared files derive from their destination directory;
  no new ACL/security framework or exact original security-descriptor preservation
  is introduced. Choose private user-controlled folders, particularly for exports.
- Crash remnants and incomplete ownership records require conservative manual
  review. Normal successful cleanup is bounded; no automatic scanner is added.
- Corrupted settings are preserved with writes disabled until backup/rename and
  restart; no automatic settings repair UI is introduced.
- Native Save As overwrite confirmation remains supplied by JavaFX/platform and
  still needs manual acceptance on target Windows configurations.

## 15. Deferred work

PDF/CSV output remains unchanged: projection/break-even PDF and CSV write directly;
Monte Carlo/integrated PDFs use sibling temporary files then a non-atomic replace.
Transactional export hardening is a separate phase. Also deferred: packaging,
AppData migration, settings recovery UI, cross-process optimistic conflict checks,
filesystem-specific durability/ACL support, model/schema migration, encryption,
autosave and any distribution/productization work.

## 16. Git status and verification

`git diff --check` passed. Separate checks of tracked modifications and untracked
text files found no trailing whitespace or missing final newlines. Git's LF-to-CRLF
normalization notices are not whitespace failures.

`git status --short` shows exactly the seven modified tracked paths and six new
phase paths listed in Section 4, plus the unchanged pre-existing untracked Phase
7A report. `git diff --cached --name-only` is empty. Branch remains `main`, HEAD
`fc01947e6a4a1119f0c4ec9d41ea3f4e8b39edad`.

No changes to `pom.xml`, dependencies, production resources, plan schema/models,
financial/domain algorithms, tax registry/rules, Social Security ranking,
Monte Carlo seeds/order or golden test data. Exporters were inspected only.
No runtime network dependency introduced. Nothing staged, committed or pushed.

## 17. Manual acceptance checklist

Use disposable copies and an isolated Windows test account/settings backup.
Never corrupt the only real plan or the only settings copy.

1. Open a copied existing plan; change a person's display name or safe description.
2. Save; confirm the UI reports success. Open the JSON again and verify the change.
3. Verify `.json.bak` loads and contains the previous version, alongside its
   `.json.bak.rp-owner` metadata. Repeat Save and confirm only the previous version
   is retained normally. Preserve backup/marker together when moving copies.
4. Save As to a new filename; verify active title/path and clean state update.
5. Invoke Save As again, cancel, and verify original path/dirty edits remain.
6. Save As to an existing disposable valid plan; verify native overwrite
   confirmation. Cancel once, then repeat/confirm and inspect its backup.
7. Make two saves of a disposable plan, then corrupt only its primary copy. Open
   it; choose Cancel at recovery and confirm nothing changed. Repeat and choose
   Review backup; inspect the recovered plan, unsaved marker and Save As behavior.
8. Save recovered data to a different filename. Verify damaged primary and backup
   are unchanged. Do not delete recovery evidence until the new file is verified.
9. With the application closed, back up test-account settings and corrupt only its
   disposable settings file. Launch: verify a usable window/default settings and
   status notice/tooltip, then manual Open/Save. Plan success must survive the
   preference warning and damaged settings must remain unchanged.
10. Back up/rename damaged test settings, restart, and verify ordinary preferences
    can be saved. Restore the original test-account settings afterward if needed.
11. Test read-only/locked disposable destinations and an unsupported atomic-move
    location if available: save must refuse safely with original bytes intact.
12. Inspect plan/backup after ordinary interruption, and verify that unexpected
    sibling temporary files are neither loaded nor deleted automatically.

Automated JavaFX recovery/cancellation tests ran; the full native chooser,
interactive read-only/cloud-folder and fresh-launch manual matrix remains for a
local developer. No claim of completing that manual checklist is made here.
