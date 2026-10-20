# Phase 9C — Longevity-Weighted Persistence and Restoration

## 1. Executive summary

Phase 9C adds application-owned, asynchronous restoration and accepted-completion persistence for couple and individual Longevity-Weighted Integrated analyses. Compatible previous results remain inspectable and exportable after relevant inputs change. Restoration uses the existing weighted codecs and pure presentation adapters; it does not invoke a projection, search, optimizer, financial job, or plan save.

The supported persistence boundary is **weighted analyses only**. Couple SS Only, Quick, Exhaustive, individual Deterministic, and individual SS Only restoration remain Phase 9D work. No financial algorithm, reference table, ranking rule, result metric, financial golden, dependency, or plan-schema change is part of 9C.

Automated acceptance passes: final full suite **3,015 run, zero failures/errors, 36 normal skips**. Detailed evidence is in Sections 17–18. Native assistive-technology and display-scaling acceptance remain manual.

## 2. Baseline Git state and tests

Initial branch: `main`. Initial HEAD: `0fa51e012a5683bc9e82e30ce57f175146134378`.

The working tree already contained all Phase 9B implementation work and the Phase 9A/9B reports: **9 tracked modifications and 28 untracked files**, using an expanded file inventory. These changes were preserved. In particular, the pre-existing modifications to `RetirementPlan`, the plan repository/controller, MainWindow/MainApplication, Stage5DComparisonTest, and wizard equivalence tests belong to 9B, not newly implemented 9C financial/schema changes.

Before-edit focused baseline: **13 tests, 13 passed, zero failures/errors/skips** (`AnalysisSnapshotCodecTest`, `IndividualCompletionPersistenceTest`, `AnalysisResultStoreTest`), `target/phase9c-baseline.log`. The previous 9B report records its full-suite baseline of 2,998 tests, zero failures/errors and 33 skips; that is historical evidence, not a new 9C baseline run.

Inspected `AGENTS.md`, Phase 9A/9B and 8A/8B/8C reports, store/repository/envelope/codec/fingerprint/compatibility classes, weighted DTOs and pure mappers, both weighted views, the dialog, navigation, request capture, job admission/publication, plan identity/Save As, PDF adapters/exporter, and relevant storage/job/presentation/PDF tests. Verified the reported 9B boundary: individual completion was wired, couple completion and both UI restoration paths were deferred.

## 3. Exact Phase 9C production and test inventory

Paths are relative to `src/main/java/com/daviddunn/retirementplanner/` unless stated otherwise. Eight listed files already contained 9B work before this phase.

| File | Phase 9C change |
|---|---|
| `app/socialsecurity/results/AcceptedAnalysisCompletion.java` | Pure `presentationSnapshot` for accepted memory-only metadata; durable `snapshot` retains its saved-plan requirement |
| `app/socialsecurity/results/AnalysisResultStore.java` | Bounded background load/restore work, saved-file binding checks, latest accepted memory precedence |
| `persistence/JsonAnalysisSnapshotRepository.java` | Refuse automatic replacement of historical snapshots with unsupported financial semantics, in addition to existing schema/integrity protection |
| `ui/socialsecurity/WeightedResultPersistence.java` — new | Guarded loading, currency/status/time/provenance, retry, frozen PDF status metadata, disposal |
| `ui/socialsecurity/IndividualCompletionPersistence.java` | Individual restore adapter and unified status; retain accepted-completion pilot |
| `ui/socialsecurity/AnalysisSnapshotPresentationMapper.java` | Immutable historical comparison/rank mapping, shared by completion and restoration |
| `ui/socialsecurity/SinglePersonMortalityView.java` | Attach restored result/report context, clear old binding, allow safe historical weighted export, reassess edited inputs |
| `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java` | Couple accepted handoff, lazy restore, frozen comparisons/ranks/context/preferences, close/plan guards and PDF integration |
| `ui/socialsecurity/LongevityWeightedIntegratedView.java` | Frozen historical deterministic-rank lookup for existing selected-strategy details |
| `ui/socialsecurity/IntegratedAnalyzerReportAdapter.java` | Weighted-only overload accepting historical deterministic ranks; existing callers retain original behavior |
| `ui/socialsecurity/ClaimingStrategyHeatMapView.java` | Validated occurrence-based selection restoration |
| `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedRestorationTest.java` — new | Restart, exact data, currency, lifecycle, bindings, PDF, retry and race tests |
| `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedRestorationPreviewTest.java` — new | Opt-in rendered couple/individual previews at four logical budgets |
| `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualCompletionPersistenceTest.java` | Wait for asynchronous presentation metadata before asserting weighted PDF readiness; original financial/storage assertions retained |
| Root `PHASE-9C-LONGEVITY-WEIGHTED-PERSISTENCE-RESTORATION.md` — new | This report |

No Phase 9A/9B/8A–8C report was edited. No other Phase 9C file change is intended.

## 4. Couple weighted completion integration

`SocialSecurityStrategyAnalyzerDialog.runWeightedAnalysis` retains the existing request factory, admission controller, calculation service, eligible callback, and cancellation/revision gates. The callback accepts persistence only when `AnalysisSuccessPolicy.accepted(result)` finds a successful ranked candidate. An all-failed result leaves the prior successful presentation and durable snapshot intact, even if its separate baseline succeeded.

At admission, capture the original plan binding and fingerprints of the effective frozen request, not later live controls. At eligible completion, capture the original timestamp, `WeightedSnapshots.Couple`, report sections/names/start time, `CurrentStrategyBaseline` elections/sources/problem, historical comparison rows, and deterministic detail-rank lookup. The latter was previously read from a live exhaustive result and needed explicit freezing for faithful historical detail/PDF restoration. Existing typed historical attachments hold this lookup; the storage schema is unchanged.

The calculation function has a package-local test seam with the identical production default. Tests deliver deterministic completed fixtures through the real admission/publication path, without launching a production-length search. No financial scheduler or engine was replaced.

## 5. Individual weighted restoration

`IndividualCompletionPersistence.attach` still captures accepted individual weighted results through the existing completion callback. It now also reconstructs `WeightedSnapshots.Individual` and the frozen `IndividualReportContext`, then attaches them through `SinglePersonMortalityView.restore`.

All nine entries, original current claiming-age marker, ranks, exact values/scales, mortality distribution, per-death outcomes, dates, rates and projection-work count come from the saved result. The existing table/detail/PDF adapters are reused. Independent individual SS Only controls are neither restored nor merged into weighted controls. The individual SS Only slot is not written.

The pre-existing individual persistence label is reused inside the new status component. This avoids two competing notices and a stale “Save Failed” message after a successful retry.

## 6. Application-owned restoration lifecycle

On selecting a weighted workspace, `WeightedResultPersistence.load` requests the slot from `AnalysisResultStore`. The existing bounded application I/O executor performs file/binding checks, gzip decoding/integrity/schema validation, compatibility checks and pure model reconstruction. Only the returned attachment operation runs on FX.

The store prefers the latest accepted in-memory result over the older durable result, including after a write failure. A saved-plan load still validates its file binding before using memory, preventing an external duplicate UUID file from bypassing identity protection. Memory-only unsaved results can reopen in another analyzer within the same application session; they are not written to disk.

The view retains UUID **and normalized file-path** binding guards, a generation, close flag and accepted-completion generation. Old callbacks cannot attach after a plan switch, dialog disposal, or newer accepted completion. Loading a hidden retained weighted workspace does not select it, steal focus, invalidate Quick, or start financial work. Current next-run controls remain untouched.

Verified integration detail: `ApplicationController.open/newPlan` notify source-plan listeners before finishing the active-file assignment. The UI bridge suppresses intermediate restoration/assessment during that notification and schedules its load after the controller operation returns on FX. Financial invalidation remains synchronous and unchanged. This avoids binding a new UUID to the previous filename, including duplicate-UUID file switches; the controller lifecycle itself was not reordered.

## 7. Currency and compatibility

`AnalysisFingerprintService` and `AnalysisCompatibilityPolicy` remain the source of currency and financial compatibility. The UI supplies the current authoritative weighted inputs and plan. Plan revisions still protect in-flight work, but are not used as the sole saved-result validity test.

| State | Presentation and behavior |
|---|---|
| No Saved Results | Existing Run and empty result state |
| Current | “Current Results”, original UTC completion timestamp; normal supported export |
| Outdated | “Previous Results — Outdated” and the required previous-assumptions notice; retain all restored results/export |
| Inputs Need Attention | Preserve the result; identify invalid pending controls independently; never manufacture Current from old valid input |
| Incompatible or Unavailable | Nontechnical explanation, no historical financial rendering/export, preserve file; existing Run remains available |

Mortality, valuation/discount, financial/scenario/baseline and explanatory-context dependencies follow 9B. Presentation changes do not affect currency. Reverting effective inputs restores Current when compatible. Names are not financial dependencies. Version 1 deliberately preserves decimal scale conservatively: differently scaled values can be Outdated even when numerically equal. No automatic scale normalization was added.

Date assessment handles both an untouched, not-yet-skinned DatePicker and malformed pending editor text. It uses the control converter or the supported JavaFX default converter; it never changes the editor/value. Existing rate and mortality validation are reused. Fingerprinting the admitted request does not add a new Run restriction.

## 8. Original assumptions versus current controls

Restored `SnapshotContext` supplies original names, report assumptions, dates, baseline elections and comparison provenance. Editable controls remain the current next-run inputs. No “Use Saved Inputs” action, plan mutation, automatic benefit rebasing, or automatic recalculation was introduced.

The status component gives a general reliable outdated explanation rather than inventing individual differences. Nondefault historical session inputs can consequently be Outdated after restart when next-run controls use existing defaults; their actual original settings remain inspectable. Returning controls to the same effective values can restore Current.

## 9. Couple heat maps, details and comparisons

The existing presentation, heat-map and result renderers reconstruct from the complete saved weighted result. Exact competition ranks, tie order, occurrence identities, failures, aggregates, baseline, work/continuation/equivalence metadata and retained optional details remain in the original DTO graph.

All existing display metrics, optimal markers, tier colors, tooltips and strategy-detail navigation use the unchanged adapters. Valid optional metric/tab/occurrence preferences are applied; missing or nonmatching preferences use existing defaults. Occurrences are matched within the original result, not table row positions. New completions currently use default preferences; continuously persisting later UI selection changes is not implemented.

Historical comparison rows and detail-rank lookup remain frozen for both accepted managed results and restored results, including after input edits. They are not joined to a newly computed unrelated exhaustive result. If no compatible comparison existed at original completion, its unavailable values remain unavailable. Rank capture uses an exact-election lookup retaining first-occurrence behavior rather than a quadratic repeated search. Existing unpersisted exhaustive results do not yet have durable run IDs; nullable source-ID fields are not fabricated.

## 10. Single-person presentation

The original nine-row ranked table and details are restored. The original claiming-age marker remains the marker from that analysis, even if current plan elections subsequently change. The global Outdated status distinguishes that historical marker from current plan inputs. No couple heat map, survivor control, Quick analysis or new individual capability was added.

## 11. PDF restoration and historical labeling

Both weighted PDF paths accept safely restored Current and Outdated results. They use frozen report context, original financial data and original analysis start. The report context adds the original completion timestamp; export time remains a separate existing report field.

Outdated/attention-state reports receive a prominent **Historical Analysis — Outdated** analysis heading and the required historical notice. Existing financial tables, heat maps, palette, result sections and calculation values are unchanged. No live plan assumptions replace historical assumptions, and PDF capture invokes no engine.

Tests compare Current/Outdated financial report sections, capture through the actual dialog/view export paths, generate both household PDFs and extract their text to verify historical labeling and completion time. Unknown/incompatible stored data is not exportable.

## 12. Replacement-analysis behavior

Starting a replacement retains prior successful tables/heat maps while existing progress/cancellation continues. Cancellation, fatal failure, rejected callbacks and all-candidate failure cannot replace the previous success. A partial accepted candidate search may replace it, with original failures preserved.

A new accepted result renders through the existing eligible callback and queues persistence independently of dialog lifetime. Until verified commit it is Pending; after commit it is Saved. On storage failure the new accepted result remains in memory, while the prior durable snapshot remains intact. Restart restores the last durable commit, not an accepted-but-uncommitted result.

## 13. Save failure and Retry Save

The weighted status component displays the store's Pending/Saved/Save Failed/Memory Only state independently of financial currency. Retry Save calls `AnalysisResultStore.retry` only; it does not construct a new financial request or run an engine. Retry readiness is visible and keyboard accessible. Metadata reconstruction is also bounded background work and can retry after transient queue pressure.

The 9B drain/retry/exit contract is retained. Closing an analyzer does not cancel accepted writes. No result is described as saved merely because computation completed or its in-memory metadata exists.

## 14. Save As clones

Existing 9B Save As creates an independent durable UUID, then performs recoverable copy-if-empty historical cloning. Restored weighted clones retain original completion/context/payload and origin UUID. The destination's effective inputs are independently assessed; copying alone does not imply Current.

Tests verify source snapshot equality, new identity, original timestamp/context/payload, visible historical-copy notice and destination currency. The 9B copy-if-empty tests continue to protect a newer destination result from a clone retry.

## 15. Async safety and storage invariants

Asynchronous restoration is separate from financial admission and uses the existing bounded store executor. No new financial executor, threads per navigation, speculative history store, or unbounded queue was introduced. Tests verify unchanged job generation during restoration, no active financial job, safe close/plan-switch callbacks, and a new eligible completion winning over an older in-flight load.

Atomic gzip persistence, integrity validation, strict DTO limits, recognized backups, atomic-only replacement, per-slot write ordering and shutdown drain remain the 9B mechanisms. No plan file/schema or result payload schema changed. Unknown schema files are not overwritten automatically. 9C also rejects replacement of readable snapshots whose financial algorithm meaning is unsupported, before touching their primary/backup bytes. A new computation whose target historical file is incompatible can remain in memory with Save Failed until an explicit recovery/replacement policy is applied; richer recovery UX is deferred to 9E.

## 16. Accessibility and responsive-preview findings

The status label exposes its actual changing text through supported JavaFX accessibility properties, including status, warnings and completion time. Retry Save has an explicit accessible name and normal keyboard focus. No restore callback requests focus or overwrites inputs. Hidden result controls cannot expose another plan's financial data. The Phase 8B/8C shell, scrolling, fixed job controls and navigation are retained.

Opt-in previews use shown JavaFX stages at **1920×1080, 1366×768, 1180×820 and 900×650 logical window budgets**. Window decorations mean scene dimensions are smaller. Existing wide sidebar/compact navigation, result-column scrolling, result tabs, footer and readable status components are inspected. These are not native DPI configurations.

Evidence is under ignored `target/phase9c-restoration-preview/`: `couple-current-*`, `couple-outdated-*`, `couple-heat-map-outdated-*`, `individual-current-*`, `individual-outdated-*`, and explicit `*-outdated-status-*` node snapshots. The initial-outdated narrow frame is captured separately. Repeated whole-scroll-pane snapshots can retain an earlier parent frame while the actual label/skin and direct status snapshot are updated; that capture limitation is not counted as native repaint acceptance. A native Robot screen-readback probe returned black frames in this environment and is not a passed native check.

Native Narrator, high contrast, 100/125/150/200% DPI, mixed monitors and interactive desktop repaint/keyboard acceptance remain manual. No accessibility certification is claimed.

## 17. Focused verification

All Maven commands use IntelliJ's bundled Maven and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`. Full/final consolidated checks use `-DargLine=-Xmx8g`. No dependency/build change was made.

Before-edit baseline: 13/13 passed. Initial existing UI/job/PDF/completion regression: 52/52 passed. The consolidated analyzer/identity/transactional-persistence run passed **323 tests, zero failures/errors, 4 normal opt-in skips** (`target/phase9c-focused-acceptance.log`). Enabled restoration/previews then passed **11/11**, and later isolated preview runs passed **2/2**. Counts overlap.

Intermediate failures were corrected, not skipped: a fixture's decimal scale differed from the established shared default; an untouched DatePicker had no initialized editor/converter; a test raced asynchronous metadata preparation; preview test record-accessor/import mistakes; and a PowerShell rewrite damaged three Unicode test literals. None required financial-default, engine or golden changes. Visual checks also led to unifying the individual status label and binding its accessible text to the current message.

Expanded focused acceptance passed: **350 run, 345 passed, zero failures/errors, 5 normal opt-in skips**, 1 minute 51 seconds, finished October 19, 2026 at 15:47:27 EDT (`target/phase9c-final-focused.log`). This includes the analyzer UI package, transactional plan/identity/snapshot repositories, individual mortality analysis and weighted comparison tests. Its 13 restoration cases include unsaved reopening, all-failed/cancelled/failed replacements, separate close/switch cases and accepted-completion races.

The subsequent unsupported-financial-meaning repository guard passed **1/1** in an isolated compiled-test execution (`surefire:test`, `WeightedRestorationTest#unsupportedFinancialMeaningIsNeitherRenderedNorAutomaticallyReplaced`, separate report suffix), 3.638 seconds, finished 15:53:58 EDT (`target/phase9c-compatibility-guard.log`). No source compilation or existing test report was replaced by that probe. The final full run also passed all **14** restoration cases and the two original individual completion tests.

Final enabled previews: **3/3 passed**, zero skips/failures/errors, 24.586 seconds, finished 15:47:54 EDT (`target/phase9c-final-preview.log`). Representative full frames and status-node images were inspected, including `couple-restored-initial-outdated-900x650.png`. The tests generate 29 rendered PNGs; reviewer copies and black native-readback probes are not counted as additional successful renderings.

## 18. Full Maven regression

Intermediate complete run: **3,009 run, 2,974 passed, zero failures/errors, 35 skips**, BUILD SUCCESS, 16 minutes 46 seconds, finished October 19, 2026 at 15:43:02 EDT (`target/phase9c-full.log`). It predates final notice/binding fixes and expanded checks and is not final-source acceptance.

**Final-source full regression: 3,015 run, 2,979 passed, zero failures, zero errors, 36 skipped; BUILD SUCCESS.** Runtime **8 minutes 47 seconds**, finished October 19, 2026 at **15:56:42 EDT** (`target/phase9c-full-final.log`). The 36 normal skips include the three new opt-in preview tests, which passed separately; no failing test was newly disabled or skipped.

This final build recompiled the final production sources, including the unsupported-financial-meaning replacement guard, and the final tests. Existing deterministic, mortality-weighted, tax/state, RMD/Roth, seeded Monte Carlo, projection funding, wizard equivalence/lifecycle, transactional persistence, analyzer job/input/result and PDF regressions passed. No financial golden was regenerated or edited.

The final full run, rather than the earlier run or focused checks alone, establishes automated acceptance.

## 19. Limitations, deferrals and manual Windows checklist

Implemented: both weighted completion paths, lazy cross-session restoration, current/outdated/attention/unavailable status, frozen tables/heat maps/details/context/PDFs, original time, binding/generation guards, separate durability/retry and historical clone restoration.

Deferred: all remaining analyzer persistence (9D); result history/browser; automatic input restoration; richer corrupt/incompatible-file recovery/replacement policy; automatic association/backfill of analyses completed before first save; continuous persistence of later presentation selections; native OS accessibility/DPI acceptance and larger field portability/crash checks (9E). The original 9B size/queue limits and compatibility-ID maintenance obligations remain. Close/reopen the analyzer to retry a transient load failure; no separate Retry Load or backup-selection UI was added. Restart tests create fresh repository/store/controller/view graphs; a native application-process restart is on the manual checklist.

Manual acceptance uses **disposable synthetic plans/cache copies only**, never a user's only real plan:

1. Save a couple plan; complete weighted analysis; observe Saving then Saved and original completion time. Close/reopen analyzer, then restart/open plan: selecting weighted must display saved data without starting work.
2. Repeat for single-person weighted. Check all nine entries and original current-age marker; changing individual SS Only inputs must not overwrite weighted controls/results.
3. Change mortality factor, discount and spending: old results remain visible and Outdated. Enter malformed pending text: Inputs Need Attention. Revert exact effective inputs: Current when compatible.
4. Inspect every heat-map metric, selected strategy/detail, frozen assumptions, baseline and historical comparison. Export Current and Outdated PDFs; verify historical heading and distinct original start/completion/export times.
5. Start a replacement, navigate, cancel; repeat with a controlled test failure. Previous success must remain. Under a controlled storage failure observe Save Failed, restore access and Retry Save without recalculation.
6. Save As, reopen the destination and inspect historical-copy notice/time, independent UUID and independently assessed currency; source must remain unchanged. External filesystem copies with duplicate UUID must not silently share bindings.
7. Use a disposable snapshot copy for corruption/unknown-version checks: Unavailable, no historical data rendered, original file preserved, no automatic run.
8. Close while storage is pending and verify existing drain/retry behavior. An unsaved plan must remain runnable and memory-only, without autosave or a cross-session promise.
9. Verify Tab/Shift+Tab, selector, PDF and Retry Save, Narrator reading actual status, high contrast and the four requested Windows DPI settings/mixed monitors. Check dynamic status repaint, wrapping, scrolling, footer/actions and focus on a native desktop.

## 20. Final Git status and acceptance

Final branch/HEAD remain `main` / `0fa51e012a5683bc9e82e30ce57f175146134378`. Expanded working tree: **12 tracked modifications and 32 untracked files**, including all pre-existing 9B work and the 9A report. Phase 9C touches **15 files** in Section 3: 11 production files, three test files and this report; eight already contained 9B work, while seven are new changes relative to the initial status inventory. `git diff --stat` covers tracked changes only (12 files, 429 insertions, 25 deletions); untracked sources/reports were reviewed separately.

`git diff --check` passes, as does separate trailing-whitespace inspection of the new sources/tests/report. Staged diff is empty. Resource/golden directories and `pom.xml` have no diff. No projection, Social Security, mortality, tax, RMD, Roth, ranking, Monte Carlo or reference-data engine was changed by 9C. The existing domain/model UUID and primary plan persistence changes visible in Git remain the pre-existing 9B work, not new 9C schema or calculation changes.

No staging, commit, push, reset or discarded work is authorized or performed. Generated logs/previews remain under ignored `target/`; the project-local Maven repository remains local/ignored. Phase 9D is not started.

Both weighted persistence/restoration paths are ready for review with automated acceptance green and the clearly identified native/manual checks outstanding.

PHASE-9C-LONGEVITY-WEIGHTED-PERSISTENCE-RESTORATION-COMPLETE
