# Phase 9D-2 Gate 3 — Exhaustive accepted completion and restoration

## 1. Result and scope

Implemented application-owned persistence and asynchronous restoration for `COUPLE_EXHAUSTIVE`, using the Gate 2 payload and the established repository/store. Eligible successful runs now hand an immutable worker-built completion to storage. Restarted analyzers display the historical ranking, deterministic heat map, baseline, retained details, original assumptions and frozen SS cross-reference without another financial run.

**Gate 3 focused verification passed: 245 tests, zero failures/errors/skips. Stop for review. Gates 4 and 5 have not begun.** The full Maven suite and full historical PDF/native acceptance remain deferred.

Read the Gate 1/2 reports, Phase 9D audit, 9C/9D-1 findings and current completion, job, snapshot, store, binding, restoration, report and test source. Earlier reports describe their own historical boundaries; current source includes both approved codecs. Quick remains a transient analysis and its calculations/prerequisite behavior are unchanged.

## 2. Initial repository state and file inventory

Branch `main`; HEAD `4fddd4ee1eaebc8354b009b77a381459495d665c`. At this gate's start there were **three modified tracked files and seven untracked files** from Gates 1/2, with an empty index. All were preserved.

Gate 3 modifies only:

- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java`: completion handoff, asynchronous attach/clear, frozen SS reference, historical export readiness, plan/close hooks and a bounded-test calculation seam whose default remains the existing calculator.
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedResultPersistence.java`: reuse for Exhaustive, its loading text, read-only completed metadata, and opt-in replacement/load guards. The new replacement methods are called only by Exhaustive; existing weighted/SS acceptance, currency, storage and retry contracts remain in place.

Gate 3 adds:

- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveResultSnapshots.java`: pure immutable capture and pure presentation/context reconstruction; no controls or calculation services.
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java`: 11 focused integration/lifecycle tests.
- This report.

No Gate 3 changes were made to financial engines, ranking, projection metrics, mortality/SS calculations, storage limits, snapshot representations, algorithms/version identifiers, primary plan JSON, dependencies, rule resources, goldens or existing tests. The domain attribution factory visible in the combined working tree belongs to preserved Gate 1 work.

## 3. Accepted-completion flow

1. On FX, capture the existing isolated Exhaustive request, original plan/session identity and saved-file binding, original `IntegratedReportContext`, the available SS recommendation basis, and initial presentation preferences. The SS basis includes the exact PV/elections and original SS run UUID/input fingerprint when application-owned metadata exists; source revision is also retained.
2. Run the unchanged calculator on the admitted financial worker. The test seam permits small deterministic fixtures without a full production search; its normal implementation is the original calculator with original progress/cancellation.
3. On that worker, construct the existing grouped presentation, Gate 2 `ExhaustiveSnapshot`, frozen envelope context, effective deterministic fingerprints, compatibility digests and `AcceptedAnalysisCompletion`. Complete projection/account graphs are captured off FX, not in the UI callback. A result with no successful candidates creates no accepted completion.
4. The existing job controller still checks generation, revision, cancellation, ownership and eligibility before the FX success callback. The callback additionally checks the original plan/session identity. Rejected, obsolete, canceled and fatal runs do not reach storage acceptance.
5. The eligible callback publishes the existing results and transfers the prebuilt completion to `AnalysisResultStore`. The application store owns the write independently of the dialog. It retains the original plan ID/file even if later navigation or plan changes occur.
6. Existing bounded I/O, kind-specific slot, verification, backup and atomic replacement perform the durable commit. Previous durable bytes survive failed verification. No saved status is claimed before that commit.

The established `AnalysisSuccessPolicy.accepted(searchResult)` permits partial success. All compact entries, including structured failures, remain in the snapshot; all-failed results retain the previous result. No financial objective, ranking or candidate universe changes.

The SS reference is frozen from the current recommendation available for the original request, rather than being replaced later by another SS run. An absent or outside-universe reference remains explicitly unavailable. Exact values are stored, and ordinary UI formatting is applied only for display. It never becomes a Quick prerequisite or triggers SS/Quick execution.

## 4. Cross-session restoration

The controller-aware couple dialog attaches the existing persistence binding to `COUPLE_EXHAUSTIVE`. Selecting the Exhaustive workspace lazily initiates loading; existing plan-source notifications also request rebinding/loading after controller file assignment. Nothing is loaded into a different household's analysis slot.

- Repository reads, gzip/JSON validation, compatibility checks, Gate 1 projection reconstruction and pure grouped presentation/report reconstruction run on the application I/O worker.
- Ranked occurrence references reconnect to the original generation-ordered entries. Stored groups and ranks are used directly; neither grouping nor rankings are regenerated.
- Only attaching/rendering the existing JavaFX controls runs on FX. The existing deterministic heat-map adapter derives its view from saved compact outcomes; it does not invoke an evaluator or projection engine.
- Existing baseline, summaries, detailed candidate projections, warnings, detached account references and source attribution are preserved by the approved codec. No live-plan account is substituted by name. The restart test deliberately uses duplicate account names and verifies restored identities are detached.
- Saved initial metric/tab/complete selected-strategy identity are applied when available. Attachment does not request focus or take keyboard focus from the selector.
- Original report names, assumptions, run start, completion timestamp and original SS cross-reference remain separate from next-run inputs. Restoring never edits the active plan or analyzer inputs.

The passive load does not increment job generation, admit a financial job, invalidate Quick, or announce a new financial completion. Restored Exhaustive results are not assigned a fresh live-run revision for unrelated weighted cross-analysis joins. Frozen comparisons remain historical; no comparison with a newly unrelated result is fabricated.

The snapshot stores initial completion preferences, not an autosaved history of every later sort, click or heat-map selection. That does not limit inspection of any stored candidate after restoration.

## 5. Currency, durability and replacement behavior

The existing mode-specific financial fingerprints and compatibility service assess the restored result. Current plan revision is not the sole currency test.

| State/event | Implemented behavior |
|---|---|
| Current | Matching compatible effective inputs; original completion time shown |
| Outdated | Historical results stay fully accessible with the established notice; restoring original inputs restores Current |
| Inputs Need Attention | Existing binding/service assessment remains supported; no last-valid fallback is introduced |
| Irrelevant pending mortality/PV text | Does **not** invalidate or block deterministic Exhaustive; this mode has no such next-run overrides |
| Incompatible/Unavailable | Historical file remains intact; result controls are cleared/hidden for the new binding and unsafe data is not exported |
| Pending | Accepted computation and queued write are distinct from a saved result; existing saving status applies |
| Saved | Only after verified durable commit |
| Save Failed | Newly accepted result remains in memory; previous durable file is retained; Retry Save uses the same accepted completion |
| Memory Only | Unsaved plans can run and reopen their result in the current application session; no autosave or cross-session promise |
| Replacement running | Keep the displayed historical ranking/heat map; fixed job progress/cancel remain operational; normal busy export guard remains enforced |
| Replacement canceled/failed/obsolete/all-failed | Retain the previous accepted result and original durable snapshot |
| Replacement succeeds | Publish/store only the eligible accepted completion; old durable file is replaced only after verification succeeds |

Plan saving, Save As, legacy identity migration and shutdown drain use the established services. An unsaved completion does not gain retroactive durability automatically after a first save; the existing memory-only policy requires a saved-plan run. No unattached recovery system was added.

## 6. Race and failure safeguards

The existing persistence generation, current plan UUID/file binding and closed-view checks guard every asynchronous attach. The new Exhaustive-only replacement guard invalidates an already pending older load without deleting the displayed snapshot. A load cannot attach while its replacement is running; eligible acceptance advances generation again. Finishing a canceled/failed replacement can resume a needed historical load.

Job-idle handling defers this completion of the replacement guard to the next FX turn, after the job controller has had its chance to publish eligible success. This avoids a premature old load racing the success callback. The original job-controller publication gates are unchanged.

Verified cases in `ExhaustivePersistenceTest`:

| Test | Evidence |
|---|---|
| Restart and currency | New store/controller/dialog load saved results; zero financial job generations; exact entries/ranks/details; Current → Outdated → Current; invalid irrelevant mortality text preserved without invalidation |
| Real accepted callback / memory-only reopen | Existing `runExhaustiveSearch` handoff runs the injected bounded financial fixture off FX; saved result survives dialog reopen; unsaved result reopens in memory |
| Verification failure / Retry Save | Previous durable bytes remain byte-identical; retry succeeds; calculation count remains one |
| Cancel/fatal failure/obsolete/all-failed reruns | Exercises production start/callback path and gates; original displayed object, run ID and durable bytes retained; expected failure alert dismissed by the test |
| Plan switch during blocked load | Clear old presentation; late original callback cannot attach to the destination; snapshot UUID is destination's; no job starts |
| Accepted replacement during blocked old load | New accepted run ID wins after old I/O unblocks; older restoration cannot replace it |
| Save As | Independent destination ID, original timestamp/provenance and exact payload, unchanged original snapshot; both identities reopen independently |
| Corrupt snapshot | Incompatible/Unavailable, file bytes preserved, no unsafe view/export |
| Close during blocked load | Disposed dialog receives no result; no financial work started |
| Partial success | Structured failure/order retained; successful snapshot committed in Exhaustive slot only; no Quick/SS/weighted slot created |
| Close with pending accepted write | Persistence stays pending independently of dialog; verified save completes after close; reopened dialog restores it |

The store's existing ordering, shutdown, queue-failure, backup and transactional tests were also run. The test fixtures use six calculated candidates with three retained projections and two years, plus the existing Gate 2 full-universe capacity/fidelity fixture. No hour-long production run was needed.

## 7. Compatibility and PDF boundary

Frozen couple/individual weighted snapshot compatibility tests passed unchanged, including exact representations, payload digests and BigDecimal values/scales. Frozen file SHA-256 values remain:

- Couple: `fb4300180df4698722cbdde8b46359d21b7f0759736b3c177dc4d2e1458cc80a`
- Individual: `f064faeeb8f061a5131d34af068816d6dcf81f15203526ee4ccaa17541d15a07`

Existing Couple SS and weighted restoration, failure/retry, numeric-length and job/state tests pass. Gate 3 has not changed payload wire formats or any configured bounds. The same Gate 2 payload is used by the real callback.

The existing deterministic report adapter accepts the reconstructed result, heat map, original `IntegratedReportContext` and frozen SS reference. Historical exportability is now distinct from Current status using the established persistence report wrapper. Busy/export-in-progress guards still apply. A boundary test captures the report DTO before/after a relevant live-plan edit and verifies identical frozen assumptions, ranking tables and visualization, with the historical notice and original completion time. The calculation hooks reject accidental recalculation during restoration/report capture.

**No PDF framework, page layout or financial report calculation changed.** There is no identified need for recalculation. Full serialized-PDF content/layout fidelity, native visual PDF review and export acceptance remain Gate 4 work; the existing four `IntegratedAnalyzerPdfStateTest` tests are regression coverage, not a claim that Gate 4 is complete.

## 8. Tests actually executed

All Maven commands used IntelliJ bundled Maven, the required project-local repository and `-DargLine=-Xmx8g`. Logs are ignored under `target/`.

| Run | Result |
|---|---|
| Initial integration compatibility run, `9d2-gate3-first.log` | 73 tests, zero failures/errors/skips |
| Initial new integration tests, `9d2-gate3-integration.log` | 3 tests, zero failures/errors/skips |
| Expanded lifecycle run, `9d2-gate3-lifecycle.log` | 8 tests, zero failures/errors/skips |
| Focused regression, `9d2-gate3-regression.log` | 208 tests, zero failures/errors/skips, 56.768 s |
| **Final focused regression**, `9d2-gate3-final.log` | **245 tests, zero failures/errors/skips, BUILD SUCCESS, 51.674 s** |

Counts overlap; they are not an additive unique total. Final executed classes:

| Class | Tests |
|---|---:|
| ExhaustivePersistenceTest | 11 |
| ExhaustiveSnapshotTest | 15 |
| RetainedProjectionCodecTest | 27 |
| LegacyWeightedSnapshotCompatibilityTest | 2 |
| AnalysisSnapshotCodecTest | 7 |
| AnalysisSnapshotRepositoryTest | 12 |
| WeightedRestorationTest | 14 |
| WeightedNumericLengthTest | 7 |
| WeightedSnapshotSaveHotfixTest | 4 |
| CoupleSocialSecurityPersistenceTest | 10 |
| SocialSecurityAnalyzerJobControllerTest | 36 |
| AnalysisResultStoreTest | 5 |
| SocialSecurityStrategyAnalyzerDialogStateTest | 32 |
| DeterministicHeatMapAdapterTest | 6 |
| TransactionalPlanPersistenceTest | 21 |
| IntegratedAnalyzerPdfStateTest | 4 |
| AnalysisFingerprintTest | 32 |

Expected fault-injection diagnostics appear in logs for successful failure/recovery tests. No financial golden changed, failing test was disabled, or fixture regenerated to hide a regression. The full Maven suite was deliberately **not run**, per Gate 3 authorization. No native screenshots, Windows DPI, Narrator or high-contrast acceptance are claimed.

## 9. Native manual acceptance instructions

Use disposable saved couple plans; retain backups. No production hour-long run is required for these deterministic checks.

1. Open a saved couple plan, select Exhaustive directly and run it. Verify the previous results remain usable during a rerun, job identity/progress/cancel are visible, then confirm **Results saved** before exiting.
2. Close/reopen the analyzer, then exit/restart the application and reopen the same plan. Select Exhaustive. Confirm original completion time, baseline, grouped rankings/counts, all four deterministic heat-map metrics, optimal markers, selected details and retained annual projections. Confirm no search/projection progress starts on load.
3. Change a relevant assumption or account balance. Reopen/select Exhaustive: verify Outdated with prior values intact. Restore the exact original input/scale and verify Current returns. Edit an unrelated mortality/discount field and confirm Exhaustive does not become stale because of it.
4. Run with a current SS result, save, then change/rerun SS. Confirm historical Exhaustive's **Original SS-only recommendation cross-reference** does not silently change. Also inspect a run completed without a current SS recommendation.
5. Start/cancel a rerun and verify the old ranking, heat map, completion time and saved file remain. Repeat after a recoverable failure if available. For a save failure, use Retry Save and verify no financial rerun begins.
6. Switch between two saved plans and close/reopen while results load. Verify names, times and results always belong to the selected plan. Save As to a new filename and verify historical-copy notice/independent identities; reopening the original must preserve its snapshot.
7. Run an unsaved plan and confirm Memory Only/no autosave. Saving a plan does not falsely announce that an earlier memory-only analysis became durable.
8. At practical wide/narrow logical sizes, verify status text wraps, Retry Save and navigation remain reachable, table/heat-map scrolling works, and keyboard focus stays on the selector during asynchronous load. Perform actual Windows scaling/Narrator/high-contrast checks separately if required.
9. Check export readiness for Current and compatible Outdated results; defer full PDF numerical/layout acceptance to Gate 4. Do not manually damage a valuable historical snapshot to test corruption.

## 10. Remaining gates and final Git state

Gate 4: full historical PDF fidelity and visual acceptance using restored values, original assumptions, selected strategies, original SS reference and baseline.

Gate 5: broader capacity acceptance, full Maven/financial regression suite and final native acceptance. Existing 32 MiB raw / 16 MiB gzip / 4,096-character numeric bounds remain enforced; a larger unsupported payload must report failure while retaining previous durable data, not omit detail.

`git diff --check` passes; Git emits only ordinary LF-to-CRLF notices. Branch/HEAD are unchanged. Combined working tree (including preserved Gate 1/2 files):

```text
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java
 M src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java
 M src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedResultPersistence.java
?? PHASE-9D-2-EXHAUSTIVE-PROJECTION-CODEC.md
?? PHASE-9D-2-GATE-2-EXHAUSTIVE-SNAPSHOT.md
?? PHASE-9D-2-GATE-3-EXHAUSTIVE-INTEGRATION.md
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ExhaustiveSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/RetainedProjectionCodec.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveResultSnapshots.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveSnapshotTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/RetainedProjectionCodecTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java
```

Index is empty. Generated logs remain ignored. No staging, commits, pushes, resets or discarded changes. **Stop after Gate 3 for review.**
