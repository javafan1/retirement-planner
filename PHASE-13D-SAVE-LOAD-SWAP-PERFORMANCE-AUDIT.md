# Phase 13D — Save, Load and Swap Performance Audit

## 1. Executive summary

**Investigation only. No production, test, CSS, financial, fingerprint or serialization changes.** The only new deliverable outside ignored `target/` is this report. Recommendations below require approval.

Confirmed: ordinary **Save and Save As execute expensive immutable-component preparation on the JavaFX Application Thread**. Even when the plan JSON is only about 13 KiB and the historical files already exist, `ProjectionSnapshotComponents.prepare` re-encodes and decodes/verifies associated analyzer payloads. With four different previously calculated 5,000-trial Monte Carlo variants, repeated Save blocked FX for **2.27–2.38 seconds** here. Recorded file read/write/force events summed to only about **47 ms across all three saves**. Sampled stacks implicate component codecs/Jackson/validation, not primarily writing the small plan JSON.

Open and Current/Baseline swap have **different execution profiles**. Open reads/binds the plan synchronously but restores saved projection components asynchronously. Swap already verifies/prepares results asynchronously, then commits and refreshes on FX. In the measured fixtures Open completed in about 0.09–0.18 seconds, A/B file switching in 0.10–0.11 seconds, and swap in 0.15–0.85 seconds. They did **not** reproduce the reported native 5–10-second freezes. Do not attribute that native duration to the Save bottleneck without a native trace. Shared save-lock contention, settings writes, filesystem conditions, larger projections and live UI listeners remain specific investigation candidates.

Recommend **Option D: combined approach**. Move unavoidable plan transactions off FX using detached prepared state and guarded publication; avoid repeating codec work for unchanged, strictly verified immutable artifacts; retain and improve swap's existing background path; add delayed operation-specific feedback. Optimization alone cannot ensure responsiveness when disk, verification or synchronization takes longer on another machine. A progress indicator alone cannot animate while its event handler blocks FX.

No new persistence framework, format, fingerprint contract or financial algorithm is needed for the first bounded implementation stages. Asynchronous normal projection execution is a separate approval gate because the existing normal engine intentionally runs against the live account graph.

## 2. Baseline, evidence and scope

Audit date: 2026-10-28. Branch `main`, HEAD `cadeb3eb9e22c543f2091dbf711c054cbcb88f25`; staging index empty. Accumulated Phase 13B/13C modifications, branding assets and reports remain uncommitted and preserved. Entry status is in ignored `target/phase13d/status-before.txt`. SHA-256 was captured for **1,113 existing source/test/resource files** in `target/phase13d/source-before.json`; final verification must show no changes. No stage/commit/push/reset/clean/discard operation was performed.

Inspected current source, project instructions and Phase 12F/13C implementation context. Two meanings of “swap” are covered: (a) Phase 12F **Swap Current and Baseline**, and (b) opening plan A, then plan B, then A again. MainWindow has one active controller/plan; no separate resident multi-plan/tab switcher was found. B is an independently owned Save As copy in the A/B benchmark, not another reference to A's live mutable plan.

### Temporary diagnostics

All instrumentation is ignored under `target/phase13d/`:

- `PerformanceProbe.java` and compiled helper classes: actual JavaFX-thread calls, `System.nanoTime()` stage timers, a 20 ms queued heartbeat, repository/ObjectMapper timing subclasses and existing AtomicFileWriter fault-stage hooks used observationally. No fault or delay was injected into the verified timing runs.
- `JfrAuditReport.java`: reads JDK Flight Recorder events and summarizes stage-scoped file events and inclusive sampled stacks.
- `operations.jfr`, `operations-multi.jfr`, `operations-counts.jfr`, CSVs and logs.
- Synthetic JSON plans, component directories, settings and isolated `user.home` beneath `target/phase13d`.

The harness uses reflection only to invoke existing private UI methods and inject a counting ProjectionEngine/Break-Even factory into synthetic controllers. It never links into the production build. It records names/counts/durations, not household financial values, input documents or account details. JFR captures method/thread/file-path metadata for synthetic data, not raw financial payloads.

## 3. Current execution paths

| Operation | FX/application-owner work | Background work | Important consequence |
|---|---|---|---|
| Save | MainWindow.onSave → saveCurrentPlan → ApplicationController.save → JsonRetirementPlanRepository.save/saveWithIdentity → component preparation, serialization, atomic write/verification; then parameter acknowledgement, title/warnings and lastOpenedPlan update | Accepted parameter/result writers may already be working independently | The ordinary Save handler itself blocks FX until the repository and settings work finish. |
| Save As | onSaveAs validates drafts, obtains chooser, collects edits, then controller.saveAs → saveWithIdentity with proposed identity; root-token replacement/rebinding and history components are synchronous; active identity/path/title publication follows | Last-success analyzer-slot copying through AnalysisResultStore.copyPlan; snapshot-copy completion notification | Existing file Save stays at its path. Save As must preserve independently owned components, not alias the source owner. “Plan saved” and later slot-copy success are separate outcomes. |
| Open/Load | onOpen → confirmPlanDeparture (possibly synchronous Save), chooser, controller.open → repository.load → plan validation/binding/source revision/last-plan preference write → loadCurrentPlan | restoreProjectionSnapshots uses AnalysisResultStore.background for component reads, decoding, detached projections/summary/chart preparation; guarded callback refreshes views on FX | Reading/binding plan JSON is synchronous; original result restoration is not. UI may render placeholders then completed results. |
| Open A/B | Same Open path; each selected file is read/bound anew; active caches are replaced | Same saved-component restoration path; last-success slots rebind by owner/file in analyzer views | No per-plan resident controller/model cache bypasses disk loading. Current immutable results can still be restored without projection. |
| Clean Current/Baseline swap | MainWindow.swapCurrentAndBaseline preflight/freshness; prepareProjectionSwap dispatch; commitProjectionSwap; loadCurrentPlan(false); title/status | ProjectionSnapshotComponents.read/readAnalyzer/resolveAnalyzer, input reconstruction, component verification and RestoredProjection.prepare run on the bounded result worker | Most expensive verification is already off FX. The final ownership checks, source listeners and UI rebuild remain on FX. |
| Dirty-plan swap | Confirmation; snapshot capture and guarded finishProjectionSwapSave on FX; later swap commit/view refresh | saveBeforeProjectionSwap saves a detached copy through the existing ordinary repository transaction, then swap preparation runs | A useful existing prototype for asynchronous Save, but not a drop-in API for every ordinary Save/Save As case. |

### Save pipeline in detail

`MainWindow.saveCurrentPlan` validates Assumptions/Roth, atomically applies Household/Assumptions/Roth drafts under `collectingViewEdits`, then calls account/income/expense save hooks. Account changes already update the domain directly; AccountsView.save is a no-op. If any pending Household/Assumptions/Roth draft existed, Save **calls refreshProjectionViews**, which invalidates and obtains a new normal projection synchronously before file persistence.

`JsonRetirementPlanRepository.saveWithIdentity` holds `AtomicFileWriter.SAVE_LOCK` and a cooperative per-plan file lock while it:

1. Checks expected durable bytes/path/recovery state and household references.
2. Calls ProjectionSnapshotComponents.prepare for current, baseline, pinned projections and analyzer associations.
3. Serializes the plan and substitutes the prepared manifest using token-preserving transactions; Save As also replaces the owner token without changing decimal tokens.
4. Parses the captured plan for committed parameter metadata.
5. Uses AtomicFileWriter.write: temporary write, force, validation, expected-state guards, verified backup/rollback preparation, atomic replacement.
6. Publishes committed identities/component references/transient-map changes only after the write succeeds.

This is more work than “write a JSON file.” All integrity/backup/ownership contracts are intentional and must survive optimization.

`ProjectionSnapshotComponents.prepare` loops all referenced original/pinned projections. It obtains/reconstructs each immutable component, calls ownedBy/encodeProjectionDocument, computes its digest and verifies an existing destination or stages a new component. For **each associated analysis**, it resolves the original payload, possibly decodes it from disk, clones ownership when required, encodes it again, calculates digests, and **reads/decodes the existing destination again** when that file exists. The scalar JSON plan only contains references; large trial histories are separate compressed components. A small main JSON therefore does not imply a cheap Save.

`AtomicFileWriter` also verifies the old primary and prepared backup. `JsonRetirementPlanRepository.readBytes` performs a validation-tree parse followed by constructor-based binding and reference checks. These repetitions are measurable, but were much cheaper than large analyzer codec work in this fixture.

### Open, restoration and state publication

`JsonRetirementPlanRepository.load` synchronizes on the same global SAVE_LOCK, reads the plan bytes, validates/binds them and records expected durable state. `ApplicationController.open` installs the plan, notifies source listeners, updates path/parameter binding/preferences, invalidates old caches and schedules `restoreProjectionSnapshots`. Therefore **simply putting controller.open on a worker is unsafe**: it mutates active state and invokes subscribers intended for FX.

The existing restore worker loads current/baseline components, reconstructs immutable result/summary/chart data and checks owner/manifest/baseline/epoch before publishing. `MainWindow.observeProjectionRestoration` checks plan and future ownership before its FX refresh. Older input-only files deliberately show unavailable original results rather than silently calculating replacements. Preserve that distinction.

`ApplicationSettingsService.recordLastPlan` is synchronous and calls its serialized repository update. Consequently Open/Save/Save As—and finishProjectionSwapSave—can perform an additional atomic settings write on FX. It was not dominant on the local synthetic path, but can wait for its write lock or slow filesystem.

### Swap preparation versus commit

`ApplicationController.prepareProjectionSwap` captures owner/path/revision/generation/manifest and performs component verification, analyzer validation and complete target preparation on AnalysisResultStore.background. `commitProjectionSwap` rechecks freshness/ownership, installs prepared caches and editable inputs, increments the existing generation/revision, rebinds parameters and notifies source listeners. Neither the operation nor subsequent restored-results rendering should invoke a projection or directional Break-Even calculation.

`MainWindow.runSwapBackground` already uses a responsive Dialog/indeterminate indicator and plan/view/epoch guards. It currently shows immediately, potentially twice for dirty-save-then-prepare, even when the task is quick. Do not replace its validated transaction with a new role model.

## 4. Measured fixtures and method

Windows, Temurin **25.0.4.1+1**, JavaFX 25, existing compiled project classes, **4 GiB heap**, local repository workspace. Three repeated operation samples per fixture (Save As and isolated codec/model micro-measurements have one sample). These are diagnostic samples, not p95 estimates or guaranteed thresholds. Files were generally warm in the OS cache; no disk-cache flushing, network/removable/cloud-drive or antivirus experiment was performed.

The timer helper reports the middle sample, using the upper middle for an even sample count. The primary three-sample operation medians are ordinary medians; the ten-sample validation/fingerprint micro-measurements use that upper-middle convention.

Fixtures use the frozen production-engine capacity plan: **50 standard projection years, five accounts**, couple household, Social Security, spending, taxes, Medicare/RMD/Roth behavior. Two actual deterministic projections were calculated during setup: baseline alternative Roth inputs, then original current inputs. No projection/simulation was run to manufacture a restored historical result.

| Fixture | Associated historical analysis | Plan JSON after Save As |
|---|---|---:|
| Projection only | None | 10,213 bytes |
| One paired result | Existing independently calculated 5,000-trial couple paired fixed payload | 11,026 bytes |
| Four variants | Existing independently calculated 5,000-trial couple fixed, longevity, paired fixed and paired longevity payloads | 13,381 bytes |

**Cached evidence, not newly calculated Monte Carlo runs:** read the four `target/mc-codec-capacity-true-…-5000.json.gz` files from earlier realistic capacity work through the production codec. The paired-fixed artifact alone is **3,810,793 compressed bytes**. Original trial values, ordering/scenarios and trial counts were not manufactured or duplicated. Exact frozen financial input documents were checked before the four variants were attached to synthetic diagnostic admissions. Each fixture has independent plan ownership. These test-only admissions do not migrate/infer any real user association or last-success history. Standard projection horizon and analyzer scenario horizons remain their original distinct values.

An initial probe incorrectly reused a cached plan UUID at a second path; the binding validator correctly refused it. That incomplete run is excluded. The verified runs assign independent diagnostic plan identities before persistence. No validator was bypassed and no production change was made.

### Timings (milliseconds)

| Stage | Projection only | One paired 5,000 | Four distinct 5,000 variants |
|---|---:|---:|---:|
| Clean Save controller, 3 samples, median [min–max] | 72.8 [71.3–130.2] | 1,004.9 [976.1–1,075.6] | **2,273.0 [2,269.4–2,383.8]** |
| Save As controller, 1 sample | 98.6 | 1,074.5 | **2,381.0** |
| Extra last-success copy wait after Save As | 7.8 | 3.5 | 7.4 |
| Open through restoration/UI layout barrier, 3 samples, median [min–max] | 142.9 [127.1–154.2] | 86.5 [86.0–99.6] | 142.4 [125.4–176.6] |
| Swap total, 3 samples, median [min–max] | 172.1 [148.6–195.3] | 357.7 [344.9–359.6] | 807.4 [732.5–846.5] |
| Swap remaining background wait, median | 29.2 | 268.6 | 674.6 |
| Swap FX commit, median | 0.38 | 0.26 | 0.48 |
| Swap FX view reload, median | 31.8 | 18.8 | 28.4 |
| Explicit following CSS/layout barrier, median | 40.2 | 25.4 | 34.9 |
| Existing component preparation alone, 1 sample | 54.5 | 967.4 | **2,263.9** |

The four-variant A/B file-switch test alternated the original and independently owned Save As file: **101.3–110.2 ms**, median **108.2 ms**, controller Open **7.7–10.6 ms**. It does not measure a resident-plan tab feature.

Timings exclude human confirmation/chooser time. Open totals include available saved-restoration work and a queued FX layout barrier; background restore can overlap initial view population. “Remaining wait” is not a separate full worker CPU measurement and should not be added to controller/UI durations. The barrier does not measure every later OS paint, GPU submission, listener, tooltip or future analyzer view. JavaFX method time and CSS time are distinguished, not equated with end-to-end native visual readiness.

### Major-stage separation

- Four-variant raw plan file read: **0.049–0.275 ms**, median 0.076; input-document reconstruction 0.261 ms; household reference validation median 0.013 ms (10 samples).
- Financial-input freshness check: 10 samples per fixture, four-variant median **0.142 ms**, maximum 0.561. Not a significant bottleneck here; this is not an analyzer-wide benchmark.
- Plan ObjectMapper instrumentation across the four-variant operations: tree parse median 0.108 ms, binding 0.394 ms, write 0.399 ms. These aggregates exclude the separate analyzer codec's mapper and are not additive operation totals.
- One paired analyzer read/decode: **246.4 ms**; encode of that saved result including its retrieval: **728.4 ms**. Existing four-variant component preparation: 2,263.9 ms. Codec integrity validation and reconstruction/reduction of stored data are work even without a projection/simulation engine.
- Four-variant isolated chart reconstruction: **8.2 ms**; ResultsSummary reload: **7.9 ms**; full view reload/CSS costs are larger as shown above. Isolated chart/model calls do not include all subsequent paint work.
- Initial direct MainWindow construction in the harness took about **2.86 s** on its first case, including one-off resource/font setup; the later case was 0.49 s. This is **not** a repeated Save/Load measurement. The harness bypasses MainApplication; current Phase 13C already prepares the exporter's first-use font discovery on its startup worker.
- Standard projections during setup took approximately 24–263 ms, depending on cold/warm execution. They are not part of clean Save/Open/Swap timings.

### FX blocking and JFR evidence

For four variants, the 20 ms queued heartbeat experienced approximately **2.26–2.27 s** latency during Save; Save As latency was similarly tied to its synchronous repository call. This directly demonstrates an unresponsive event queue. Heartbeat samples can include adjacent pulse/layout scheduling and are not exact exclusive per-method attribution.

Across three four-variant Save calls totaling **6,926 ms**, recorded JFR file events summed to:

- FileRead: **16.091 ms**
- FileWrite: **1.005 ms**
- FileForce: **29.786 ms**

These events are not an exhaustive measurement of filesystem metadata, lock wait, scheduling or physical media latency. Nevertheless their size, the standalone component timing and inclusive sampled stacks through `ProjectionSnapshotComponents.prepare`, `AnalysisSnapshotCodec.decode/decodeSnapshot` and Jackson serialization/deserialization strongly support **component CPU/validation work as the primary measured Save cost**. Do not attribute all non-file-event time precisely to CPU or infer a universal percentage.

Raw evidence: `timings.csv` (projection-only/one paired), `timings-multi.csv` (verified four variants and A/B), `timings-counts.csv`, `jfr-summary.txt`, `jfr-summary-multi.txt`, `probe-verified.log`, `probe-multi-final.log`, `probe-counts.log`, and the JFR recordings under ignored `target/phase13d/`.

## 5. Ranked findings and recalculation

| Priority / confidence | Finding and responsible methods | Consequence / recommendation |
|---|---|---|
| 1 — measured | MainWindow.onSave/onSaveAs → ApplicationController.save/saveAs → JsonRetirementPlanRepository.saveWithIdentity → ProjectionSnapshotComponents.prepare | Large historical payloads are repeatedly processed synchronously. Move the transaction off FX and safely reuse verified immutable references. |
| 2 — verified structural risk, native duration unmeasured | AtomicFileWriter.SAVE_LOCK wraps whole plan preparation; repository.load and atomic settings/result writes also use it | An FX load/save/settings call can wait behind a background commit/verification. Keep lock correctness; remove FX waits and profile contention before narrowing lock scope. |
| 3 — measured behavior, complex-plan cost unmeasured | MainWindow.saveCurrentPlan → refreshProjectionViews → getCurrentProjection | Pending Assumptions/Household/Roth edits cause a new standard projection during Save. One engine run was observed, not an uncontrolled loop. Changing when this runs needs a separate policy decision. |
| 4 — measured redundancy | getCachedBreakEvenAnalysis → computeBreakEvenAnalysis during normal draft-save refresh | One draft application invoked the Break-Even factory **twice**. Cache-only naming does not guarantee no calculation in normal mode. Deduplicate exact ordered in-memory comparisons without changing methodology or adding persistence. |
| 5 — verified, currently modest timings | loadCurrentPlan/observeProjectionRestoration, ResultsSummaryView.load/updateCharts, ProjectionChartView.load/render, projection table selection listener | Placeholders then restored UI are two legitimate render phases. Charts/axes/series/tooltips are rebuilt; selectLast can recompute a full-horizon comparison. Reuse prepared models and avoid identical repeated render/comparison work; not the first measured bottleneck. |
| 6 — hypothesis requiring native evidence | Much larger annual/input graphs, open analyzer windows, external filesystems/antivirus/native chooser, allocation/GC pressure | These could explain the native 5–10 s Load/Swap report. They were not reproduced/quantified here. Capture the actual FX stack/blocked monitor before choosing a correction. |

**Counting-engine result:** clean Save, Save As, repeated Open, A/B switching and Current/Baseline swap made **zero additional standard ProjectionEngine calls** in the verified cases. One pending inflation edit applied by the actual saveCurrentPlan route caused **one** normal projection. No Monte Carlo simulation was run in the audit; cached artifacts were used. Codec validation can rebuild/reduce already-recorded statistics; do not confuse that with a new simulation or financial projection.

Swap/restored rendering keeps `breakEvenExplicitOnly=true`, so it does not perform the normal automatic Break-Even calculation. Preserve Phase 12F's approved calculation-free swap policy and invalidate reversed directional context. No persisted directional artifact or reversed-result reuse is proposed.

`ResultsSummaryView.setupProjectionSelection` invokes updateBaselineComparison for any non-null selected year. The method currently calls controller.compareProjections with the unchanged full-horizon financial definitions, not a selected-year argument. Repeating the same comparison on row changes is an optimization candidate, not permission to change those definitions.

No runaway load-listener projection loop was observed. AssumptionsView uses its `loading` guard; MainWindow collects draft application callbacks before one explicit refresh. Social Security/Monte Carlo source listeners deliberately invalidate execution tokens and reassess/rebind sessions; dialog/view close paths detach them. MonteCarloResultLifecycle performs codec/digest/currency work on the existing store worker. WeightedResultPersistence.reassess computes its analyzer-specific fingerprint on FX; benchmark it separately for the native case rather than disabling compatibility checks. ProjectionAnalyzerHistoryDialog loads/prepares saved data on the worker, renders on FX and detaches its source listener on close. No evidence was established that closed dialogs accumulate listeners.

## 6. Recommended architecture (Option D)

### A. Optimize necessary work without weakening integrity

Prefer reuse of a **verified immutable component reference**, not re-encoding every existing payload on every Save. Cache only bounded verification metadata, keyed by owner, plan/path binding, component UUID, exact compressed digest, schema/algorithm versions and required payload/context identity. Validate bounded disk bytes/digest and binding when reusing. If there is no trusted prior verification, or content/owner/version differs, perform the existing full decode/validation off FX. Pending/new analyses still require full encoding/verification. Size/collection limits remain unchanged.

Do not use mtime/filename/UUID alone as a verification substitute, reuse across owners, infer projection associations from equal financial hashes, or silently drop history to save time. Save As must produce independently owned artifacts under the existing contract. Do not retain every decoded trial graph in an unbounded cache, introduce automatic garbage collection, or raise storage limits. Continue stage → verify → atomic manifest publication; newly staged orphan components on failure must not make an uncommitted manifest authoritative.

Reuse the existing exact-result comparison context and prepared chart model where valid. Any comparison cache must retain the ordered current/baseline result identities and relevant context; reversal is a different comparison. Do not skip fresh financial-input checks solely because the dirty flag or revision is unchanged; direct model edits and financial freshness remain separate from durability.

### B. Two-stage plan operations, not background calls on live controllers

Introduce the smallest application-owned bounded **execution coordinator** for plan file operations; it is not a second repository or persistence format. Use a single admitted operation/short bounded queue, operation generation and captured owner/path/revision. Avoid putting long full-plan transactions into the existing single analyzer-results queue, which could starve history/restore work or deadlock if work waits on that same queue. The preferences directory-probe executor's timeout policy is also inappropriate for durable writes.

Reuse the pattern already demonstrated by `saveBeforeProjectionSwap` / `finishProjectionSwapSave`:

1. **FX capture:** validate/collect drafts through existing workflows; capture exact financial input document, baseline input document/metadata, role manifest, immutable component/pending association references, accepted configurations, current owner/path/financial revision/projection epoch and acceptance sequence. This short capture must be measured for very large plans.
2. **Background preparation/write:** reconstruct a detached save document, validate and stage components, serialize preserving numeric tokens, run existing atomic transaction and return a committed receipt. Never serialize/mutate the live plan graph on a worker.
3. **FX commit:** recheck ownership/generation and adopt only the matching committed identity/metadata. Clear dirty only if the saved financial state still equals the active state. Preserve newer accepted parameters/history; do not clear live pending maps from an older captured save. Title/path/status change at the correct accepted boundary.

For Load, prepare a detached validated plan and original result components without changing active plan, active bindings or lastOpenedPlan. Commit them together on FX only if the departure decision/owner/epoch is still valid; then schedule the existing guarded rendering/compatibility paths. A canceled/stale load must not advance the active file's repository expected-durable-state registration. Separate repository read/prepare from durable-binding adoption where required; simply calling existing controller.open on a worker is not safe.

Keep the previous plan/results visible until an accepted load commit. Incomplete/corrupt legacy results remain explicitly unavailable, not recalculated. Load should not instantiate an unrelated analyzer, promote historical sessions or overwrite last-success slots.

Preserve one existing ApplicationSettingsService writer. Last-plan preference persistence should be submitted in accepted operation order off FX, without creating another stale settings repository or allowing an old completion to overwrite a later selected path. A preference failure after a successful plan save/open is a separate warning, not a false financial-save failure.

### C. Retain swap's prepared transaction

Keep prepareProjectionSwap/commitProjectionSwap, exact snapshot UUIDs/input restoration, analyzer associations following UUIDs, generation checks and the existing dirty-save confirmation. Reuse immutable verification metadata where safe; keep throwing reconstruction on the worker. Instrument live source listeners and final UI pulses if native swapping still blocks.

Do not add shared-year normalization to Results Summary, persist a new directional Break-Even artifact, negate a reversed result, auto-promote analyzer sessions, or recalculate to satisfy a swap. The existing approved financial/ownership policies remain authoritative.

### D. Pending edits and normal calculation are a separate boundary

Initial performance work can move repository I/O/codec work without changing the existing auto-projection-on-draft-Save policy. If native traces show normal projection dominates, return for approval to either (a) preserve that behavior using a separately designed detached projection request/guarded acceptance, or (b) save inputs while honestly retaining an Outdated accepted projection until explicit recalculation. Do not silently choose (b).

The existing getCurrentProjection deliberately runs the normal engine on the live account graph to preserve result/account identities. It cannot simply be moved to a background Task while views mutate that graph. Algorithms/golden values remain unchanged; any new execution boundary must preserve exact input/result association and account ownership/identity requirements.

## 7. Responsive feedback

Recommended defaults are timing policies for **showing feedback**, not artificial operation delays:

| Elapsed | Presentation |
|---|---|
| < about 300 ms | Complete silently; ordinary final status only. |
| About 300 ms–2 s | Operation-specific status and small indeterminate indicator, using existing theme controls. |
| > about 2 s | Existing styled owner-modal progress pattern or a compact busy overlay where conflicts must be blocked; keep FX event loop responsive. |

Messages follow actual phases: “Saving retirement plan…”, “Loading retirement plan…”, “Restoring saved projection…”, “Switching retirement plans…” or “Preparing projection swap…”. Use “Calculating retirement projection…” only when one is actually running. No percentages without real completed/total work. Cancel feedback timers immediately on completion; no minimum display/animation duration.

Use one feedback lifecycle across dirty-save-then-swap instead of flashing two dialogs. Preserve the explicit Save and Continue / Cancel decision. Disable conflicting Save/Open/Save As/Swap and financial edits during a first bounded implementation; keep repainting, window management and safe read-only viewing responsive. Do not disable the whole FX thread or cancel an existing analyzer merely to simplify UI state.

Load/preparation may be canceled before publication; stale results are ignored. Once an atomic save commit is underway, do not offer a misleading cancellation promise or time out a write as “failed” while it can still commit. Show “Finishing save…” and wait for a definitive receipt. Application shutdown must drain/track accepted writes honestly. Native file chooser, folder validation and user confirmation timing are separate from repository progress; existing asynchronous directory fallback behavior must be preserved. Theme uses existing ApplicationThemeManager/looked-up colors, with no CSS palette or branding redesign.

## 8. Consistency and concurrency risks

| Risk | Required boundary |
|---|---|
| Edits during serialization | Detached immutable capture; initially gate mutations; never pass the live graph to a worker. |
| Dirty state cleared for newer edits | Receipt matches saved financial document/revision before clearing; otherwise newer edits remain unsaved. |
| Partial load / stale title/path | Prepare before active-state adoption; one FX commit guarded by owner/operation generation. |
| Save As while analysis/parameters complete | Explicit admission/coordination policy, captured acceptance order, independent owner rebind; preserve newer settings and last-success identity. |
| Configuration-only write interleaving | Keep cooperative file/global lock and expected durable-state checks; merge accepted metadata without writing unrelated dirty financial edits. |
| External modifications | Preserve digest/conflict checks; visible conflict instead of replacement. |
| Canceled/superseded task | No publication to another plan; file commits must be reported truthfully even if active view changed. |
| Background snapshot restoration races | Preserve epoch/manifest/baseline checks and current/assessment-pending separation. |
| Cross-analysis/history misuse | Preserve exact projection UUID/owner/request/schema identity; no inferred associations or automatic session promotion. |
| Save-lock contention | Keep FX free of long-held locks; do not narrow locking without proving atomic component/configuration coordination. |
| Queue saturation/shutdown | Bounded operation admission, explicit busy/retry state and nonblocking drain; no abandoned acknowledged writes. |

Moving work off FX requires these boundaries; a Task wrapping `controller.save/open/saveAs` unchanged is not the recommended implementation.

## 9. Phased implementation recommendation

1. **Native trace confirmation:** capture the exact reported Load/Swap freeze, runtime/source build identity, actual plan/component sizes and concurrent operations. Retain audit diagnostics only; do not add permanent logging. This can run in parallel with approval of the measured Save correction.
2. **Bounded asynchronous Save/Save As:** detached transaction/receipt and operation gate, preserving repository/UUID/configuration semantics; delayed feedback. Reuse dirty-swap Save preparation where appropriate rather than a general controller rewrite.
3. **Bounded asynchronous Open/A–B activation:** detached prepare and atomic FX adoption, preserving last-plan settings order, original-result restoration, departure choices and recovery.
4. **Immutable-artifact reuse and targeted UI deduplication:** verified reference/digest fast path and operation-scoped repeated comparison/render suppression; exact same-direction in-memory Break-Even deduplication only if approved. Rebenchmark against the unchanged wire/digest contracts.
5. **Native-driven residual work:** only if evidence warrants it, separately approve asynchronous normal projection preparation/account mapping or a specific costly analyzer/UI callback. Keep swap's current background architecture and financial definitions.

Option A alone helps average time but cannot bound I/O or lock waits. Option B alone keeps the UI alive but wastes repeated codec CPU. Option C alone is ineffective if work remains on FX. Option D is the best fit, delivered in small tested increments. No broad chart, controller or persistence rewrite is justified by these measurements.

## 10. Tests and repeatable benchmarks

Existing focused verification used IntelliJ bundled Maven, project-local `.codex-m2/repository`, `-DargLine=-Xmx4g`, with:

`ProjectionSnapshotPersistenceTest,ProjectionSwapTest,ProjectionSwapViewTest,Phase12EAssociationTest,AnalysisSnapshotRepositoryTest,TransactionalSettingsTest,ApplicationPreferencesTest,AssumptionsMainWindowTest,PersistenceRecoveryWorkflowTest,Phase12GFreshnessTest`.

**109 executed, 109 passed, 0 failed, 0 errors, 0 skipped; BUILD SUCCESS; 46.407 seconds**, completed 2026-10-28 12:51:20 -04:00. Log: `target/phase13d/focused-tests.log`. The full Maven suite was not rerun for this audit-only phase. No tests or golden values were changed. Actual synthetic JavaFX/JFR probe processes completed with exit 0; the separately excluded initial path-binding refusal is documented above.

Proposed implementation regressions:

- Throw if repository/codec/expensive validation runs on FX after preparation; throw if controls are touched by the worker.
- A deliberately blocked fake I/O stage must still allow an FX heartbeat and progress/Cancel handling. Use latches/manual schedulers, not brittle per-machine 100 ms limits.
- Exact financial tokens/scales, original result UUID/digests/years, correct dirty/title state, Save As ownership, component/backup failure and external-change recovery.
- Concurrent accepted parameters, pending history components, configuration-only writes, Save As and shutdown; no newer entries lost by a captured save.
- Stale/late Save/Open/swap completions, canceled load, owner switch during preparation, failed stage and partial commit receipts.
- Zero projection/analyzer calls during original-result Open/swap; exactly the existing intended calculation count for a draft Save until a new policy is approved.
- In-memory Break-Even reuse only for the identical ordered result pair; swap invalidates direction and remains calculation-free.
- No automatic historical promotion; legacy missing information and assessment pending remain honest.
- Delayed indicator thresholds using a fake clock; completion suppresses a queued show; Light/Dark styling and reduced-window accessibility.

Repeat benchmarks with isolated synthetic homes/documents: no history, one large artifact, several distinct analysis variants, larger annual/account graphs, first versus repeated saves, pending versus already durable components, same-file Open versus independent A/B activation, dirty-save-then-swap and worker contention. Report medians/ranges/counts, bytes/component counts and engine-call counts; do not gate CI on an exact machine-dependent speed target. Do not invent duplicated trials/results or change storage limits to make a benchmark look better.

The temporary probe can be compiled with the existing test classpath and run with `-Xmx4g`; `PerformanceProbe` defaults to projection-only/one-paired cases, `multi` selects four cached distinct variants and A/B tests, `counts` isolates the existing draft-save calculation counters. It uses only synthetic files beneath target and is not shipped code. Its cached Monte Carlo inputs must exist; do not silently replace missing cached artifacts with duplicated data.

## 11. Native Windows / IntelliJ profiling procedure

The audit **did run actual JavaFX windows and record their FX thread**, but not the user's exact native plan, machine conditions, chooser interaction or production binary. The exact 5–10 s Load/Swap symptom remains unconfirmed. JFR sampling/event overhead, small sample counts and warm cache apply. Allocation/GC pauses and simultaneous background writer contention were not separately quantified in this run. No individual-household performance fixture, 30-association stress run or 10,000-trial case was timed; correctness coverage for those identities comes from existing tests, not these performance numbers.

To capture the native symptom without production source changes:

1. Record Java version, launch classpath/build output, source revision plus uncommitted-build identity, plan JSON size, component count/total bytes and whether analysis/parameter writes are pending. HEAD alone is insufficient while the accepted changes remain uncommitted.
2. Use a safe representative test plan or an independent copy made through **application Save As**. Explorer-copying a bound plan file without its correctly rebound components is not equivalent. Keep personal JFRs local; share only sanitized aggregate timings/stacks.
3. In IntelliJ's Run VM options add, for example: `-Xmx4g -XX:StartFlightRecording=name=aledrix-perf,settings=profile,filename=target/aledrix-native-perf.jfr,dumponexit=true,maxsize=128m`. Do not change application dependencies or financial settings for the capture.
4. Alternatively attach the configured JDK's `jcmd <PID> JFR.start name=aledrix-perf settings=profile filename=target/aledrix-native-perf.jfr duration=180s maxsize=128m` to the running JVM. Use an absolute output path when the application's working directory differs. Obtain the PID with that JDK's `jcmd -l` or the IntelliJ run process; no elevated OS installation is needed.
5. After warm startup, note wall-clock times and perform separately: unchanged Save; Save with a valid pending edit; Save As; Open B then A; clean swap and double swap; dirty Save and Continue swap. Note confirmation/chooser time separately. Repeat while known accepted background writes are pending, without altering their safety limits.
6. Inspect **JavaFX Application Thread** samples, blocked monitor owner and waits, FileRead/FileWrite/FileForce, allocation/GC pauses, and the single `analysis-snapshot-io` queue. Profile settings normally provide the JVM events; enable JavaFX pulse events if the viewer/runtime offers them, rather than assuming they were captured. The temporary audit recording had explicitly enabled execution/file/monitor events, not a complete pulse/GC analysis.
7. Correlate the frozen interval with stack names: component preparation/codec; SAVE_LOCK or preference write-lock wait; projection engine; Break-Even; ResultsSummary load/comparison; chart/CSS/layout; analyzer callbacks; or native chooser. A long `WAITING/BLOCKED` FX interval is not evidence of computation. Use JFR tooling available in IntelliJ or the JDK `jfr summary/print`; installing a new profiler is not required.

If method-stage timing is still needed, use temporary non-suspending IntelliJ logging breakpoints around MainWindow.onSave/saveCurrentPlan, controller.save/saveAs/open/commitProjectionSwap, repository.saveWithIdentity/load, component.prepare/readAnalyzer, refreshProjectionViews and ResultsSummary.load. Log nanoTime/thread/method only, no method arguments or financial objects. Debugger instrumentation adds overhead; compare a separate JFR-only run and remove the logpoints afterward.

Native acceptance for a future implementation should verify responsive status/progress in both themes at normal/reduced sizes, safe Cancel before load publication, no false cancellation during durable commit, unchanged Results Summary/history/Current–Outdated/Save As behavior, and no projection or analyzer starting during swap/restoration.

## 12. Decisions requiring approval

1. Approve **Option D** and a bounded application-owned plan-operation coordinator with detached prepare/guarded FX commit, starting with Save/Save As, then Open. No live-controller calls are moved wholesale to background threads.
2. Approve initial conflicting-action/edit gating during a transaction, rather than supporting unrestricted financial editing during Save As. Define treatment of already-running analyses/pending accepts without relaxing their existing publication guards.
3. Approve strict immutable-artifact reference/digest reuse (with full first-use validation and changed/unknown fallback), unchanged ownership/bounds/wire formats and no automatic GC. Do not approve mtime-only validation or financial-hash-based association inference.
4. Approve delayed status at about 300 ms and long-operation feedback at about 2 s, one lifecycle across save-then-swap, indeterminate progress and honest noncancelable durable commit.
5. Approve deduplicating the repeated **same ordered in-memory Break-Even** calculation using existing result identities. No persisted/reversed result reuse or financial methodology change.
6. **Separate gate:** retain existing auto-projection on draft Save for the first I/O stage, or explicitly approve a follow-up design if native traces justify moving/deferring that calculation. Detached normal-projection/account-identity work is not implicitly authorized here.
7. Supply a sanitized native trace if the actual Load/Swap freeze persists. Those durations were not reproduced, so a universal “same bottleneck” fix is not yet justified.

Audit completion: all **1,113 source/test/resource hashes unchanged**; `git diff --check` passed, and the staging index remains empty. The only new non-ignored audit file is this report; diagnostic helpers, recordings, fixtures and logs remain under ignored `target/phase13d`. Existing Phase 13B/13C work and earlier reports were preserved. Stop for review. No production implementation is authorized by this document.
