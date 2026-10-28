# Phase 13D — Save, Load and Swap Responsiveness

## Summary and scope

Implemented the bounded Save optimization, detached Save/Open preparation, guarded owner-thread publication and delayed in-window feedback authorized after the performance audit. Swap retains its existing calculation-free prepare/commit transaction and now reuses strict artifact-validation receipts. No financial algorithms, financial fingerprints, document formats, snapshot versions, storage budgets, association policies, dependencies or golden resources were changed.

Native Windows acceptance remains pending. The exact reported 5–10-second Open/Swap freeze was not reproduced with the synthetic fixtures; this implementation addresses measured repeated codec work and clearly identified synchronous file/lock waits without claiming every native pause is resolved.

Baseline: branch `main`, HEAD `cadeb3eb9e22c543f2091dbf711c054cbcb88f25`. The index was empty. Accumulated Phase 13B/C work and all earlier reports were present before implementation and have been preserved. The incremental inventory below distinguishes this work from those changes.

## 13D1 — Artifact reuse without reduced corruption detection

`ProjectionSnapshotComponents` now maintains at most **64 validation receipts**, containing only complete immutable `AnalyzerAssociation` identities, never decoded trial/result graphs. A receipt is established only after successful full decoding/envelope validation or a successful validated component write.

For an already durable, unchanged-owner association in the same source/destination plan path, ordinary Save now:

1. Validates the referenced projection input digest and household shape as before.
2. Validates the storage path, symlink rules and exact plan-path binding marker.
3. Reads the **entire** analyzer artifact with the existing compressed-size bound.
4. Recomputes SHA-256 and requires the recorded component digest.
5. Requires a receipt for the complete recorded association; otherwise fully decodes and validates the envelope, payload and supported versions before recording a receipt.
6. Reuses the original association/reference and compressed bytes without serialization or rewriting.

The receipt is not based on file size, mtime, financial similarity or a role label. Corrupt/missing/oversized files still fail even with a warm receipt. Changed/unknown records require full validation. Restart starts with an empty receipt cache. Changed content cannot inherit a receipt merely because it occupies the same path.

Pending/memory artifacts and **Save As owner rebinding** retain the full materialize/encode/decode/validate path. Projection components retain their existing encoding/validation path. No artifacts are deleted, limits increased or historical associations inferred. Atomic staging, verified backup, expected-durable-state checks, cooperative locks and atomic manifest replacement remain in force.

`verifyAnalyzer` lets Swap's validation-only worker use the same bounded byte/digest verification without retaining a decoded simulation graph. Actual historical visualization still decodes its original saved data normally.

## 13D2 — Detached Save and guarded receipt

`ApplicationController.preparePlanSave` captures on the owner thread:

- Exact configured financial-input document and baseline input document.
- Active plan object, original identity/path, source revision and projection epoch.
- Role manifest, baseline identity, immutable accepted configurations and immutable pending/component maps.
- Save As destination/independent-owner decision and a parameter-acceptance fence.

A dedicated single-thread `retirement-plan-file` worker has a bounded one-entry queue, rejects overlapping admissions, uses daemon threads with idle expiry, and does not share the analyzer-result worker. It reconstructs the detached plan, prepares/validates components, serializes and invokes the **existing** repository atomic transaction. It never receives controls or the live editable graph. No projection or analyzer is run by this preparation.

`finishPlanSave` applies the durable receipt on FX. It checks operation sequence, active object/path/identity and captured financial/metadata context. Replayed receipts cannot release or overwrite a later operation. A changed financial document/revision/epoch leaves newer edits dirty. New metadata/pending acceptances are retained rather than cleared by an older detached save. Save As or legacy first identity assignment is not activated if captured inputs/metadata have changed; the earlier captured copy can have committed, and the UI explicitly reports that fact and requests review/retry. It does not falsely report a disk failure or replace the active ownership.

The repository's `parameterWriteFence` detects new registered accepted configurations before preparation/manifest replacement, including acceptance between financial capture and fence capture. For the same active file, registered run IDs must match the captured typed configurations, not merely a later registry snapshot. An older detached save refuses to overwrite those settings. Save-before-swap uses the same fence. Existing configuration-only writes remain serialized through their original locks and continue to target their captured file/owner; they are not redirected by this work. If an acceptance occurs after commit, its existing independent write/durability lifecycle still applies. UI gating is not used as a substitute for receipt/acceptance checks.

Ordinary synchronous controller Save/Open APIs remain available to existing startup, tests and non-UI callers. Main-window Save and Save As use the new detached path. They preserve validation/apply of pending Household/Assumptions/Roth drafts and the existing normal-projection behavior of that path. This phase **does not move live-graph normal projection calculations onto a worker or silently defer them**.

## 13D3 — Open, restoration and Swap

### Open and recovery

`JsonRetirementPlanRepository.prepareLoad` reads/deserializes/validates a detached document without changing the repository's authoritative expected-state binding. Only `adoptLoad` installs its captured digest/configuration binding after the application accepts the receipt. Rejected loads therefore do not silently authorize a later overwrite of externally changed content. If durable content changes after reading, the existing subsequent save conflict checks remain conservative.

`preparePlanOpen` runs that I/O and validation on the file worker. `finishPlanOpen` rechecks owner, path, revision, projection epoch, exact configured document, baseline, role manifest and accepted-configuration identity. An intervening accepted result/configuration cannot be lost by an older load. The old plan remains visible until acceptance; failed or rejected preparations leave it intact. A stale receipt gets a state-change explanation rather than an inappropriate corrupt-file recovery prompt.

At adoption, old projection caches are invalidated before source listeners observe the new plan. Existing original-snapshot restoration remains on its existing bounded worker with its existing owner/epoch guards and unavailable states. No projection runs to replace missing original results. Backup availability checks and explicit recovery loading also run off FX; explicit affirmative review and preservation of the damaged primary remain unchanged.

Last-plan preferences use field-level serialized updates on the existing `ApplicationSettingsService` writer. They are submitted only for accepted plan operations. The main UI waits through the responsive event loop for that preference receipt, including warnings, before completing Save/Open. Preference failure is a separate notice, not a false financial-save failure. No second preference repository or settings framework was added.

### Swap

The existing `prepareProjectionSwap`/`commitProjectionSwap` ownership, freshness, revision, generation and association safeguards remain authoritative. Validation-only artifact checking uses the same full-byte/digest receipt optimization. Original UUIDs, result values, ending years, financial input documents and analyzer associations remain unchanged. No standard projection, Social Security, Monte Carlo or Break-Even engine is called.

Dirty Save and Continue still uses the ordinary detached transaction and revalidates before swapping. Warning collection and last-plan preference writes no longer require synchronous storage-lock acquisition in its FX completion. The initial Save and Continue/Cancel confirmation remains. The old immediate progress dialog is replaced with one delayed in-window feedback lifecycle across save-before-swap and preparation. A Cancel action remains available during reversible preparation; it invalidates the prepared generation without mutating roles. It is absent during the durable Save phase. A canceled preparation's late callback cannot release or cancel a newer request. Repeated clicks remain ignored while the asynchronous swap is active.

## 13D4 — UI feedback and confinement

`PlanOperationFeedback` uses FX `PauseTransition`s solely to reveal feedback:

| Elapsed | Behavior |
|---|---|
| Less than about 300 ms | Feedback remains invisible and unmanaged. |
| About 300 ms | Operation-specific text and a small indeterminate spinner in the existing bottom status area. |
| About 2 seconds | Larger padding/text and a semantic focus-color border make the same in-window strip prominent. |
| Completion/failure | Timers stop and the strip is removed immediately; no minimum display time. |

File work starts immediately. No fabricated percentage, native chooser timing or artificial delay is introduced. Save/Open preserve existing boolean departure decisions by using `Platform.enterNestedEventLoop`, **not `Future.get` on FX**. Completion is queued back to FX. This permits repainting, timers and window events while a detached write is blocked. Swap keeps its asynchronous return semantics.

Main financial controls and the menu are disabled during the admitted operation. Save, Save As, Open, New, Exit and baseline-capture handlers reject conflicting reentry. Window-close requests are consumed during accepted writes/swap, so a durable write is not falsely canceled midway. Already-running analyzers retain their original admission/generation/ownership checks; later metadata is fenced or retained, not silently discarded. There is no new cancel promise once a durable write is underway.

CSS is limited to two new `.plan-operation-*` classes using existing looked-up colors. Branding, global chart/heat-map palettes and PDF styles are unchanged.

## Measurements

The ignored diagnostic probe uses the audit's same cached engine-calculated **four distinct 5,000-trial couple Monte Carlo variants**, 50 projection years and five accounts. It generates independent synthetic files/owners under `target/phase13d-implementation`; no new Monte Carlo simulations or real financial files are used. Main plan JSON remains 13,381 bytes. JVM: Java 25, JavaFX 25, 4 GiB heap; JFR plus queued 20 ms FX heartbeat, local warm filesystem, generally three samples per operation. Small samples are evidence, not CI speed guarantees.

| Stage, milliseconds | Audit median [min, max] | Implementation median [min, max] |
|---|---|---|
| Existing synchronous clean Save transaction | 2,273.017 [2,269.383, 2,383.782] | 100.291 [96.695, 157.755] |
| Existing-component prepare, one sample | 2,263.890 | 45.147 |
| Swap total, including view/layout barriers | 807.393 [732.499, 846.528] | 174.148 [153.913, 182.871] |
| Remaining Swap worker wait | 674.563 | 35.704 [33.088, 41.329] |
| Direct synchronous Save As, one sample | 2,380.951 | 2,357.827 |

Clean transaction latency decreased about **96%** on this fixture. Save As still needs full independent-owner re-encoding and validation; its unchanged cost is moved off FX rather than hidden or weakened.

New detached Save measurements:

- FX capture/dispatch: median **0.319 ms**, range 0.266–5.354.
- Remaining background wait: median **62.343 ms**, range 60.575–75.452.
- FX commit: median **0.323 ms**, range 0.302–1.079.
- Total including preference completion: median **69.082 ms**, range 66.787–95.321.
- Actual main-window Save action, including nested event-loop time: median **128.521 ms**, range 85.934–152.235. This is wall time, **not exclusive FX blocking**. Heartbeats ran during it: 11 samples, median queued delay 19.127 ms, maximum 74.452 ms, versus roughly 2.26 seconds during audit synchronous Save. CSS/layout and adjacent pulses contribute to those samples; no claim of a universal frame-time guarantee.
- Actual main-window Save As action: **2,330.198 ms** in one sample, with **103 heartbeat samples**, median queue delay **6.048 ms**, maximum **28.673 ms**. The native chooser was replaced only in the ignored probe using the existing protected chooser seam; no human selection time is included. The actual Save As handler, detached transaction, FX receipt/title updates and preference completion ran normally. This is evidence that an unavoidable multi-second rebind need not block FX.

New detached Open: FX capture median **0.183 ms**, FX adoption median **0.411 ms**, UI reload median **16.079 ms**, total median **75.551 ms** [30.667–87.490]. Warm direct Open and A/B switching varied across runs; no universal Open speedup is claimed. The meaningful improvement is moving file/global-lock waits off FX and preserving transactional adoption.

Engine counters remained **zero** for clean Save/Save As/Open/A–B switch/Swap, including the new paths. The separate pending-draft application probe retained its existing **one normal projection and two Break-Even invocations**. Deduplicating normal draft-refresh comparisons or changing normal projection execution is deferred; the approved calculation-free swap policy remains intact.

Probe sources, JFRs, timing CSVs and logs are ignored diagnostics under `target/phase13d-implementation`. The audit's original timing files remain unchanged. The table and new-path measurements above use the **final compiled implementation**, after the full test run finished, without competing Maven test CPU load. Final CSV/JFR: `target/phase13d-implementation/final-probe/timings-multi.csv` and `operations-multi.jfr`; log: `target/phase13d-implementation/probe-final.log`, process exit 0. Four cached compressed analyzer payloads total **8,114,977 bytes**; they were not newly simulated.

## Regression evidence

Commands use IntelliJ's bundled Maven, `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository` and `-DargLine=-Xmx4g`.

- Initial focused baseline: **63 passed, zero failed/errors/skips**, 37.643 s.
- Expanded focused selection: **181 passed, zero failed/errors/skips**, about 65 s. Covers parameter coordinator/controller, Monte Carlo and Social Security parameter persistence, Phase 12G freshness, all 15 Phase 12E associations, projection persistence/swap, settings and recovery.
- Final UI/receipt/recovery/swap selection: **37 passed, zero failed/errors/skips**, 26.650 s. Includes 14 new plan-operation tests plus the controlled cancellation/newer-swap callback regression.
- Transaction/parameter-persistence selection: **94 passed, zero failed/errors/skips**, 46.375 s.
- Full final regression: **3,645 total; 3,601 passed; 44 skipped; zero failures; zero errors; BUILD SUCCESS**, **10:55**, completed **2026-10-28 14:13:15 -04:00**. Log: `target/phase13d-implementation/full-suite-final-accepted.log`. No tests were newly disabled/excluded. The 44 existing skips comprise property-gated capacity/performance/native-visual or user-plan diagnostics, one explicitly manual multi-minute benchmark, and four pre-existing Mississippi admission worksheet assumptions. They are not claimed as executed acceptance tests. XML totals were independently checked against suites listed in the current run; one pre-existing archived `WeightedRestorationTest-phase9c-compatibility` XML report was excluded from report aggregation, not deleted or treated as a test exclusion.

New coverage proves blocked file I/O leaves FX free, detached capture retains exact original values, later edits stay dirty, failed writes retain durable state and allow retry, independent Save As restores exact original projection UUID/results, restoration does not invoke throwing projection factories, stale/replayed receipts cannot cross owners or release a newer operation, accepted parameters cannot be overwritten by an earlier Save, changed metadata rejects a load, legacy first identity assignment cannot rebind newer state, and feedback cannot reappear after completion.

All 15 existing association fixtures now additionally verify unchanged compressed artifact bytes/references across repeated Save. A warm-cache Quick artifact corruption test flips a byte, requires failure without manifest replacement, restores the original bytes and verifies retry. Existing codec/ownership/oversize/unsupported-version/atomic-failure tests remain enabled.

Development corrections: an initial synchronous swap wait changed rapid-click timing; restoring asynchronous return fixed it without changing acceptance rules. Recovery-dialog tests now wait for the real dialog after background validation rather than assuming the next FX callback already contains it. Async preference cleanup is explicitly awaited. A legacy-first-Save fixture initially used a newly created UUID-bearing plan; it was corrected to model an actual UUID-less legacy document. Preliminary full attempts were stopped during receipt/fence review; an intermediate full run subsequently passed **3,643 total / 3,599 passed / 44 skipped / zero failures/errors**, 10:47. The final run includes preparation cancellation and preference-failure safeguards as well. No assertion or financial golden was weakened.

## Visual verification

Generated feedback-component previews at **1180×820 and 900×650**, in both existing Light and Dark themes, under `target/phase13d-implementation/previews`. Inspected all four rendered PNGs: text/spinner/border are readable, the strip fits the width and no new horizontal overflow is visible. An initial preview omitted the existing `app-theme` root class and therefore did not exercise Dark; the fixture was corrected and asserts the actual dark text color. Production theme ownership was already correct and unchanged.

These are component previews, not certification of full native Windows progress timing, DPI, keyboard/taskbar behavior or remote/network storage. Full-app native verification remains required. Existing full regression previews/PDF fixtures continue to use original report data and styles.

## Exact incremental inventory

Production modifications (including files already changed by earlier phases):

1. `src/main/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotComponents.java`
2. `src/main/java/com/daviddunn/retirementplanner/persistence/JsonRetirementPlanRepository.java`
3. `src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java`
4. `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java`
5. `src/main/java/com/daviddunn/retirementplanner/application/settings/ApplicationSettingsService.java` — existing untracked Phase 13B file; added serialized asynchronous last-plan update only.
6. `src/main/resources/css/application.css` — added scoped feedback classes only.

New production file:

7. `src/main/java/com/daviddunn/retirementplanner/ui/PlanOperationFeedback.java`

Test modifications:

8. `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12EAssociationTest.java`
9. `src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSwapTest.java` — wait for new preference completion during cleanup.
10. `src/test/java/com/daviddunn/retirementplanner/ui/baseline/ProjectionSwapViewTest.java` — same cleanup boundary; controlled cancel/newer-request race regression.
11. `src/test/java/com/daviddunn/retirementplanner/ui/PersistenceRecoveryWorkflowTest.java` — await asynchronous recovery dialog, preserving all assertions.

New test/report:

12. `src/test/java/com/daviddunn/retirementplanner/ui/PlanFileOperationsTest.java`
13. `PHASE-13D-SAVE-LOAD-SWAP-PERFORMANCE-IMPLEMENTATION.md`

This is the **Phase 13D incremental inventory**, not permission to stage these paths in isolation: shared files contain accepted earlier-phase work. Preserve the complete accumulated worktree for eventual review. Exclude ignored `target/**`, synthetic plans/components, JFRs, timing logs, diagnostic Java/classes and rendered previews from a future commit. All pre-existing branding/settings reports/assets/source/tests remain outside this incremental scope and were preserved. No dependency/build configuration, financial source, golden fixture, plan schema or PDF exporter belongs to this change.

## Native Windows acceptance checklist

1. Normal plan: clean Save, edited-draft Save, Save As and Open; verify filename/title/dirty marker and restart. Fast operations should not flash an indicator.
2. Large plan with several original 5,000/10,000-trial analyzer artifacts: repeat clean Save, close/reopen, inspect original results/PDFs and compare UUIDs/status. Verify responsive repainting while saving.
3. Use an intentionally slow test destination: status after about 300 ms, prominent indeterminate strip after about two seconds, promptly removed on success/failure. Verify both themes at 1180×820 and 900×650 and Windows DPI scaling.
4. While busy, attempt rapid Save/Open/New/Swap/Exit and window close; no conflicting operation or partial plan adoption should occur. Do not force-kill a real financial save as a UI cancel test.
5. Test locked/read-only/full/unavailable destination and external changes using disposable copies. Confirm prior file/backup stays usable and dirty state remains honest; retry after fixing the condition.
6. Save As: verify independent ownership, all historical artifacts and last-success copying, original file unchanged, original parameters/frozen assumptions preserved.
7. Open A/B and restore legacy/missing-component plans: no fabricated original results, no projection/analyzer automatically runs, unavailable context is explicit. Test explicit backup review after damaging a disposable primary.
8. Clean swap and double swap, plus dirty Save and Continue/Cancel: exact original ending years/results and role UUIDs, charts/history/associated results correct; no Break-Even/projection/analyzer runs from swapping. Cancel a slow preparation and retry; the old callback must not undo the new request. Cancel must not be offered during the durable Save phase.
9. Where a running analysis completes during file preparation, verify newer parameters/results remain present or the operation refuses safely with a retry notice. Verify no false Current assessment or cross-plan publication.
10. Repeat the original native 5–10-second reproducer with a local JFR as described in the audit if a pause remains. Record actual FX stacks/GC/lock owners rather than assuming a remaining pause is disk I/O.

## Limitations and next steps

- Original owner-changing artifacts still require full codec work; now it runs on the worker. Cold first-use validation and genuine file/lock waits can still take seconds without freezing the file-operation FX loop.
- Pending draft Save still applies inputs and performs its established normal projection on FX. The final measured fixture took about 45 ms; a native plan dominated by that calculation needs a separately approved detached projection/account-identity design. No such redesign is included here.
- UI control/chart construction remains FX-owned. Existing measured reload/layout durations are retained; expensive native listeners or pulses require a trace before further optimization.
- No persisted directional Break-Even cache, schema change, automatic history promotion, garbage collection or analyzer-policy change was introduced.
- File preparation is conservatively gated rather than promising arbitrary concurrent financial editing. Programmatic/async changes are still defended by receipts and parameter fences. Load cancellation UI is not added. Swap preparation retains cancellation; durable writes finish or reject safely.
- Forced process termination cannot be prevented; existing atomic replacement/recovery contracts remain the protection. Normal UI exit refuses while an admitted operation is active.

## Final verification and readiness

Final full suite passed with the totals above. The final-source synthetic JavaFX/JFR probe exited 0. All four feedback previews were inspected. `git diff --check` passed; the staging index is empty; branch/HEAD remain `main` / `cadeb3eb9e22c543f2091dbf711c054cbcb88f25`.

SHA-256 comparison against the audit's **1,113-file source/test/resource inventory** identifies exactly the ten existing files listed in this report as modified, plus the two new production/test files. The other **1,103 original hashes** are unchanged, including financial/golden resources, branding artwork, earlier theme files and unrelated earlier-phase source. All new diagnostic data stays under ignored `target`; the only new non-source deliverable in this implementation is this report. The earlier audit and accumulated reports remain preserved.

**READY FOR REVIEW AND NATIVE WINDOWS ACCEPTANCE.** The original native 5–10-second symptom still requires user verification on the affected plans; this report makes no claim of completed native acceptance. No staging, commit, push, reset, clean or discard was performed. Stop after Phase 13D.
