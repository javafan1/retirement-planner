# Monte Carlo Persistence and Comparison — Phase 2C Lifecycle Integration

## Scope and starting state

Implemented accepted-result lifecycle and asynchronous restoration for all eight Phase 2B Monte Carlo kinds. Reused the application-owned `AnalysisResultStore`, gzip repository, UUID/path binding, atomic verification/replacement, backup recovery, bounded I/O queue, Save As cloning and shutdown/drain contracts. No second repository or financial scheduler was added.

The initial working tree already contained Phase 1/2A reports and probe, plus the complete uncommitted Phase 2B codec implementation and report. These were preserved. Branch: `main`; HEAD at start: `c11e7348b7f9b64b7b4f6b4c7c9daf8738388748`.

No financial engine, random stream, trial scheduling, matched-world generation, tax/RMD/Roth/SS/mortality rule, golden resource, dependency, Phase 2B payload representation, Exhaustive interning representation or storage limit was changed. Historical PDF policy/fidelity remains Phase 2D.

## 1. Eight integrated variants

| Household | Standalone fixed | Standalone longevity | Paired fixed | Paired longevity |
|---|---|---|---|---|
| Couple | `COUPLE_MONTE_CARLO_FIXED` | `COUPLE_MONTE_CARLO_LONGEVITY` | `COUPLE_MONTE_CARLO_PAIRED_FIXED` | `COUPLE_MONTE_CARLO_PAIRED_LONGEVITY` |
| Individual | `INDIVIDUAL_MONTE_CARLO_FIXED` | `INDIVIDUAL_MONTE_CARLO_LONGEVITY` | `INDIVIDUAL_MONTE_CARLO_PAIRED_FIXED` | `INDIVIDUAL_MONTE_CARLO_PAIRED_LONGEVITY` |

Each uses its own existing kind/UUID slot. Changing the selected fixed/longevity mode clears the other mode's presentation and asynchronously restores the selected slot; it does not run an analysis or remove the previous slot. The two dialogs remain separate and keep their existing controls, tables and charts. A paired result remains one matched A/B result, not two independently simulated runs.

## 2. Accepted-result lifecycle

`MonteCarloSession` and `MonteCarloStrategyComparisonSession` retain their previous result when starting a replacement. The existing worker, cancellation token, generation and source revision checks remain authoritative. A new `start(work, accepted)` overload invokes the handoff only inside the eligible successful-completion branch. Existing callers can continue using `start(work)`.

Each view captures its source plan/request and original binding before dispatch. Its worker performs the existing calculation, checks cancellation, captures the Phase 2B immutable payload and frozen context, fingerprints it, and returns. The UI-dispatched eligible callback submits that completion to the application store. No codec encoding, compression, file read/write or pure presentation reconstruction is performed on JavaFX. Existing FX-side plan/request isolation is preserved; next-run currency capture uses that same isolation boundary before handing immutable input work to the store worker.

The accepted completion contains no controller, view, live plan, mutable account reference, or callback. Original seed, trial count, matched outcome ordering, lifetimes, financial histories, frozen plans and chart/report data remain in the unchanged Phase 2B payload. An all-funding-failed but normally completed Monte Carlo experiment is valid evidence under the established MC payload success contract; a fatal run exception is not.

Cancellation, failure, revision rejection and close-before-acceptance retain the previous accepted result and do not call the persistence handoff. A new accepted result appears in memory while its disk write is Pending. The previous durable file remains protected by the existing atomic repository. A failed write leaves that previous file intact. Retry Save retries the accepted snapshot without invoking the calculation work. Closing a dialog invalidates its callbacks but does not stop an accepted store write.

## 3. Asynchronous restoration

`MonteCarloResultLifecycle<R>` is a scoped UI-dispatched binding/status adapter over the existing store, not a new storage framework. The view supplies its existing pure Phase 2B restore adapter and attach callback. It has no simulation or projection dependency.

Restoration is lazy when a Monte Carlo dialog/workspace opens or changes its plan/mode binding; application startup does not eagerly decode every unselected snapshot. Existing snapshots are loaded on the store worker. Compatibility and pure run/chart reconstruction also occur on that worker. Only attaching the completed run and updating controls occur on JavaFX. Open dialogs follow subsequent plan-open notifications; the new file is read after the controller finishes assigning it.

Store memory takes precedence over the older disk version, including a newly accepted result whose disk save failed. A fresh controller/store/repository/view can instead read the last durably committed snapshot after a restart. Passive `session.restore` does not invoke the accepted callback, financial work, progress publication or a replacement write. It does not change current next-run controls.

Corrupt, unsupported or financially incompatible data is not rendered. The UI reports Incompatible or Unavailable, preserves the historical file and leaves Run available under its normal readiness rules. No automatic repair, deletion or recalculation is introduced.

## 4. Currency and historical-result UI

`MonteCarloFingerprintService` hashes canonical effective frozen inputs using the existing canonical SHA-256 machinery. Its mode-specific inputs include:

- Kind/household and standalone versus matched-pair identity.
- Exact settings, seed, trial count, return/inflation values and optionality.
- The standalone plan, or both original paired candidate plans in A/B order.
- The configured start/horizon and financial/economic/account/income/spending/death/survivor configuration represented by those plans.
- Longevity-only mortality requests, independent factors, conditioning date, metadata and table entries.
- Explicit caller-supplied world identity where present; a generated-world input assessment cannot silently match a supplied-world source.

UUIDs, stored baseline metadata inside an already captured candidate, names/descriptions and presentation selections are excluded. Baseline financial inputs participate separately as candidate B for paired analyses; changing a standalone plan's saved baseline does not affect its standalone financial run. Unknown root plan fields are rejected instead of silently excluded. BigDecimal scale remains significant, consistently with Phase 2B. Sampled horizons and overridden configured death timing use the separate context digest, not the financial digest. A paired fixed baseline's configured length is also reference context; its common endpoint comes from the current-plan request. The post-death expense factor remains financial. Fixed-mode mortality categories do not participate in financial validity. Changing retained explanatory context can still make the historical presentation Outdated, without claiming that a sampled financial endpoint changed.

`AnalysisVersions` adds MC algorithm/stream/statistical compatibility tags and mode-appropriate reference digests. Existing kinds' maps and reference lists are unchanged. `AnalysisCompatibilityPolicy` now accepts MC assessments; the SS fingerprint extractor still explicitly rejects MC kinds, preventing accidental use of the SS dependency matrix. Save As copies remain unassessed historical copies until the destination view evaluates its effective current controls.

The views add a wrapping status notice and Retry Save button, with accessible text and the original completion timestamp. Current, Previous Results — Outdated, Inputs Need Attention, Incompatible or Unavailable and durability states remain separate. Valid input changes are reassessed asynchronously; invalid pending text never falls back to an old valid value. Returning inputs to the original values can restore Current. Rankings/chart data remain frozen while these statuses change. Missing or invalid next-run prerequisites can produce Inputs Need Attention while the historical result stays available.

During a replacement, progress/cancellation describe the running job while the notice explicitly identifies the previous accepted result that remains displayed. The selected table/inspection state is not rebuilt on every progress update. A rendered preview found and corrected a contradictory legacy STALE banner on matching restored results; both status areas now consult the same current assessment.

## 5. Save As, unsaved plans and Retry Save

Existing Save As logic assigns an independent UUID and queues snapshot cloning after the plan commit. All eight kinds participate in the existing slot iteration and copy-if-absent protection. Original payload/digest, run identity, completion time and origin provenance survive. Destination currency is assessed independently. The source file and snapshot remain unchanged.

`ApplicationController.addAnalysisBindingListener` notifies successful Save/Save As file/identity changes independently of financial source revisions. Existing financial revision and projection cache semantics are unchanged. Failed Save As does not emit that notification. Both MC views detach this listener when closed. Open/new source notifications invalidate publication immediately; file-aware loading follows after controller assignment.

Unsaved and legacy-unbound plans remain Memory Only. No plan is autosaved. Their accepted result can be reopened from the application store in the same session under its existing session identity. Cross-session persistence requires a successfully saved valid binding. As in the established Phase 9 policy, saving a previously memory-only completion does not silently promote it into a durable snapshot; the notice instructs the user to save the plan and rerun. No unattached recovery browser is introduced.

Retry Save uses the accepted immutable completion in the application store, retaining the original run ID and calculations. Storage admission unavailability is reported as Save Failed instead of escaping from an FX completion callback; Retry Save can retry admission. The existing bounded store and normal shutdown/drain behavior remain authoritative.

## 6. Concurrency safeguards

- Session generation/revision/token gates reject canceled, failed, closed and obsolete runs before handoff.
- Each lifecycle binds the original UUID, normalized file and kind; later source changes cannot redirect an accepted write.
- Binding/view generations reject late load, metadata and currency callbacks.
- A replacement invalidates older in-progress loads, so they cannot overwrite the new run.
- If a new binding loads while the old session is still cancelling, its pure result is held until the session is idle; it is not dropped by the session's busy guard. A late historical restore preserves a failed/cancelled replacement's job status rather than impersonating a new completion.
- Store-memory run ID is checked before attaching a loaded older disk result.
- Current-input assessments coalesce while one is in flight and reject obsolete edit revisions.
- Source identity changes clear the old view before deferred path loading; switching modes has independent slots.
- Accepted I/O remains application-owned after dialog close; slow-write tests verify Pending and shutdown drain before commit.
- Existing per-slot ordered writes, atomic replacement/backups, bounded queue and copy-if-empty semantics remain unchanged.

## 7. Tests and rendered evidence

All commands use IntelliJ's bundled Maven, `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`, and `-DargLine=-Xmx4g`.

Baseline: **29 run, 27 passed, 0 failures/errors, 2 opt-in skips**, 13.710 s (`target/mc-2c-baseline.log`).

New `MonteCarloResultLifecycleTest` uses independently calculated small real production fixtures for all eight modes, then performs restoration-only tests. Coverage includes fresh repository/store restart, exact histories and summaries, pair identity/order, Current → Outdated → Current, invalid pending text, clone provenance and independent identities, all eight simultaneous slots, failed writes/Retry Save, Memory Only, obsolete loads, plan switching, replacement guards, corrupt/incompatible sources, dialog close, pending slow writes and binding notifications.

New `MonteCarloRestorationViewTest` restores every mode into the actual JavaFX view with throwing simulation executors/work callbacks and throwing controller projection methods. Charts/tables populate without invoking them. Current and Outdated states and invalid pending controls retain the restored result. Source-level inspection confirms the lifecycle/presentation adapters have no financial execution path.

Session tests now assert the authorized last-success retention behavior instead of the previous clear-on-failure behavior. Additional cases count accepted callbacks across cancellation, failure, revision rejection, close and success for both session types. Existing UI navigation assertions were updated only where separate mode slots intentionally replace cross-mode display retention; all rendering/financial assertions remain.

Intermediate integration selections passed **134 tests** and then **153 tests**, zero failures/errors/skips. An initial selection exposed two old mode-navigation assertions expecting a different mode's result to remain displayed; these were corrected to test independent slots. One development compile error from a private test helper reference, and one misplaced listener-detach assignment, were corrected without changing calculation code.

Broader focused verification: **400 run, 391 passed, 0 failures/errors, 9 skips**, Maven **3:26** (`target/mc-2c-focused.log`). This includes Monte Carlo calculation/seeded tests, codecs/PDF adapters, frozen weighted compatibility, weighted restoration, Couple SS persistence, Exhaustive persistence/interning, repository/atomic/transactional tests and controller tests. The skips retain existing opt-in capacity/preview gates; the new view-render captures ran with `-Dmc.lifecycle.preview=true`.

Final verification results are recorded below after the final status-banner and slow-write tests.

The first complete Maven regression passed **3,229 run, 3,186 passed, 0 failures, 0 errors, 43 skipped**, **10:32**, completed October 21, 2026 at 06:24:27 EDT (`target/mc-2c-full-suite.log`). Final review then added the banner assertion, long numeric input fingerprint coverage, explicit financial/context dependency coverage, slow-write/binding notifications and the cancelling-session restoration guard. Subsequent focused selections passed **215 run / 213 passed / 2 skips** (50.716 s), **217 run / 215 passed / 2 skips** (52.435 s), and the final lifecycle/view/session/frozen-compatibility proof passed **76 run / 76 passed / no skips** (28.167 s). Every selection had zero failures/errors. A fresh complete suite on the final implementation is the final acceptance result below.

### Rendered evidence

Sixteen actual rendered JavaFX captures are generated under ignored `target/`: `mc-2c-<analysis-kind>-current.png` at 1180×820 and `mc-2c-<analysis-kind>-outdated.png` at 900×820, for all eight kinds. These use synthetic two-trial fixtures, not private plans. They exercise the existing outer scrolling, wrapping notices, completion timestamp, chart/table retention, control reachability and disabled historical PDF policy. These logical sizes are not native DPI or assistive-technology certification.

The initial inspection of couple paired Current and individual longevity Outdated caught the conflicting STALE banner described above. Updated final captures were regenerated and visually inspected: couple paired fixed Current, individual longevity Outdated, couple paired longevity Outdated, and individual fixed Current. The corrected Current banner agrees with the persistence notice; timestamps and prior seed are readable, charts/tables are retained, and compact notices/action rows wrap with outer scrolling. Sixteen images were generated; these four final images were inspected, rather than claiming a native manual review of every image or every scroll position.

The unchanged Phase 2B realistic capacity evidence remains applicable: the fully calculated 10,000-trial paired fixed payload retained all one million annual observations and measured 20,695,338 raw bytes, 7,613,054 gzip bytes and 1,732,788 tokens. Phase 2C does not change that payload or rerun the expensive capacity matrix merely for lifecycle wiring. Existing raw/gzip/token/array/depth/string/numeric and logical-expansion limits are unchanged. Frozen legacy weighted snapshot bytes/digests are not regenerated.

## 8. Native acceptance checklist

1. Rebuild and restart normally in IntelliJ; no live-process patching is required.
2. Save a synthetic/familiar plan with a durable UUID. Run fixed and longevity standalone and paired analyses as supported by its household/baseline. Confirm Pending then Saved and the original completion time.
3. Start a replacement; confirm prior chart/tables remain visible with the replacement notice. Cancel it; repeat with a safely failing test configuration if available. Confirm historical results survive.
4. Close and reopen each dialog, then exit/restart the application and reopen the plan. Select each mode. Confirm restoration without simulation progress and inspect chart years/table metrics.
5. Match the prior controls to see Current; change seed/return or relevant financial inputs to see Outdated. Verify frozen displayed run inputs remain original. Restore the original effective values and confirm Current. Enter malformed pending text and confirm Inputs Need Attention without deleting results.
6. Save As to a new filename, wait for cloning, reopen original and new plans independently and verify original completion/provenance and separate slots. Change only the destination and confirm independent currency.
7. Exercise a controlled snapshot-directory permission/disk failure only in a disposable test environment. Confirm Save Failed and prior durable file retention, restore access, then Retry Save without simulation work.
8. Test an unsaved plan: Run remains allowed and the notice says Memory Only; no autosave or cross-session guarantee appears.
9. Check narrow window scrolling, keyboard access to status/Retry Save, and normal close during a pending accepted write. Native Narrator/high contrast/DPI checks remain manual.

## 9. Remaining Phase 2D work and limitations

Full historical PDF fidelity/status acceptance is deferred. Existing current-result export and Phase 2B pure report reconstruction are preserved. A still-Current accepted result remains eligible after a failed/cancelled rerun once the session is idle; readiness follows the accepted result instead of confusing the failed replacement with that result. This phase does not enable or certify Outdated historical export, add cross-saved-result comparisons, or redesign reports. Incompatible data is never made exportable as a valid historical financial result.

Next-run controls are not overwritten with historical settings. Therefore a restored run with nondefault settings can initially be Outdated until the current controls match; this is intentional. Missing/invalid prerequisites are shown separately as Inputs Need Attention. No analysis history browser, autosimulation or recovery system for unattached unsaved results is implemented.

Native application acceptance has not been claimed. Automated restart tests use new repositories/stores/views and disposable files. Screenshots demonstrate actual JavaFX logical layouts, not Windows Narrator/high-contrast/scaling certification. Extreme runtime configurations outside the existing codec budgets still fail safely; no limits are raised and no trial evidence is discarded.

## 10. Phase 2C files and Git boundary

New production:

- `app/montecarlo/MonteCarloFingerprintService.java`
- `ui/montecarlo/MonteCarloResultLifecycle.java`

Modified production:

- `app/socialsecurity/results/AnalysisCompatibilityPolicy.java`
- `app/socialsecurity/results/AnalysisVersions.java`
- `ui/controller/ApplicationController.java`
- `ui/montecarlo/MonteCarloAnalysisView.java`
- `ui/montecarlo/MonteCarloSession.java`
- `ui/montecarlo/MonteCarloStrategyComparisonSession.java`
- `ui/montecarlo/MonteCarloStrategyComparisonView.java`

New tests:

- `app/montecarlo/MonteCarloResultLifecycleTest.java`
- `app/montecarlo/MonteCarloRestorationViewTest.java`

Modified tests:

- `ui/montecarlo/MonteCarloSessionTest.java`
- `ui/montecarlo/MonteCarloComparisonSessionTest.java`
- `ui/montecarlo/MonteCarloMortalityViewTest.java`

Paths above are relative to the corresponding `src/main/java/com/daviddunn/retirementplanner/` or `src/test/java/com/daviddunn/retirementplanner/` root. This report is new. Other changed/untracked files were already present from Phase 2B and the prior audits; their complete inventory will be retained in final Git status below.

No staging, commits, pushes, resets or discards. Generated screenshots, test snapshots, PDFs, Maven logs and `.codex-m2` remain ignored artifacts. No real financial files are added.


### Complete working-tree inventory

```text
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityAnalysisResult.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityRequest.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonResult.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisFingerprintService.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisKind.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisVersions.java
 M src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java
 M src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloSession.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonSession.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonView.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloComparisonSessionTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloSessionTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisFingerprintTest.java
?? MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-1-AUDIT.md
?? MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-2A-CAPACITY.md
?? MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-2B-CODECS.md
?? MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-2C-LIFECYCLE.md
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloFingerprintService.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPlanDocument.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshotMapping.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshotNumbers.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloResultLifecycle.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloSnapshotPresentationMapper.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloCapacityProbeTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloRestorationViewTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloResultLifecycleTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshotCodecTest.java
```

There are 17 tracked modified files and 15 expected untracked files across the preserved Phase 2B work and Phase 2C. The staged diff is empty. No generated target artifacts appear in this inventory.

## Final acceptance results

**Final full Maven suite: 3,250 run; 3,207 passed; 0 failures; 0 errors; 43 skipped. BUILD SUCCESS. Total 9:52. Completed October 21, 2026 at 06:40:13 EDT.** Evidence: ignored `target/mc-2c-final-full-suite.log`. This final run includes the final financial/context fingerprint separation, 4,096-character fingerprint parser contract, Current-banner correction, slow-write/drain tests, binding notifications, late failed-run status retention and cancelling-session attachment guard.

The 43 skips retain existing optional preview/capacity/private/reference-data gates, including the prior Phase 2A/2B opt-in capacity/replay tests. No failing test was disabled or converted to a skip. The Phase 2C view captures were explicitly executed in the focused proof with `-Dmc.lifecycle.preview=true`; the final ordinary suite does not need to regenerate them. The final scoped lifecycle proof ran **76 tests**, including **52 lifecycle cases, 8 actual-view restoration cases, 14 session cases and 2 frozen weighted compatibility cases**, with zero failures/errors/skips. Broader existing MC calculation, seeded, PDF-input, projection, SS, weighted, Exhaustive, identity and transactional coverage also passed in the complete suite.

Final command:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-DargLine=-Xmx4g' test
```

`git diff --check` passed. Protected domain calculation paths, financial golden/test resources and `pom.xml` have no diff. Existing Couple SS, Couple/Individual Weighted, legacy weighted and Exhaustive compatibility/recovery tests passed. The Phase 2B wire encoding and all snapshot safety limits remain unchanged. Generated logs, synthetic snapshots, PDFs, screenshots and the local Maven repository remain ignored. No staging, committing, pushing, resetting or discarding occurred.

Phase 2C implementation and automated verification are complete for review. Native acceptance remains manual as listed above. **Do not begin Phase 2D without authorization.**
