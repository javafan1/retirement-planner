# Phase 9B — Persistent Analysis Results Infrastructure

## 1. Executive summary

Implemented durable plan identity and the initial versioned snapshot infrastructure: explicit analysis kinds, weighted result DTOs, strict gzip JSON codecs, canonical fingerprints, compatibility assessment, transactional storage, bounded background I/O, last-success retention, Save As cloning, and a shutdown/drain contract.

**Production integration is intentionally a limited pilot: controller-bound individual Longevity-Weighted completion only.** Its accepted results can be written, and unsaved-plan results can remain in application-owned memory. No snapshot is loaded into an analyzer UI in this phase. Couple weighted callback integration and complete couple/individual weighted screen/PDF restoration belong to Phase 9C. Other callbacks/codecs belong to 9D. A written file is not advertised as cross-session-restorable.

No financial engine, ranking, tax, mortality, RMD, Roth, Monte Carlo, existing PDF calculation, navigation, financial default, dependency or golden resource changed. The only plan-format addition is optional `planId`.

## 2. Initial state and approved decisions

Initial branch: `main`; HEAD: `0fa51e012a5683bc9e82e30ce57f175146134378`.

Initial short status contained only the pre-existing, untracked `PHASE-9A-PERSISTENT-ANALYSIS-RESULTS-AUDIT.md`. It was preserved unchanged. AGENTS.md, Phase 9A and Phase 8A/8B/8C reports and the relevant current source/tests were reviewed.

Before-edit baseline: `SocialSecurityAnalyzerJobControllerTest,TransactionalPlanPersistenceTest`: **57 run, 57 passed, 0 failures/errors/skips**, BUILD SUCCESS, 10.949 seconds, October 19, 2026 at 10:36:52 EDT.

| Approved decision | Implementation |
|---|---|
| A: durable UUID | New plans get a stable UUID; missing legacy ID remains absent on load; successful explicit save adopts one; malformed IDs rejected |
| B: independent Save As | New saved-file identity is published only after commit; original file/ID retained; background historical snapshot copies preserve source provenance and are assessed separately |
| C: saved-plan prerequisite | Pilot persists only with a saved file/ID; unsaved/legacy pilot results use application-owned MEMORY_ONLY state; no autosave, recovery browser or automatic first-save backfill |
| D: candidate failures | Explicit family success policy; completed results with successful candidates retain structured failures; cancelled/stale/rejected/fatal/all-failed work cannot replace last success |

## 3. Exact files added and modified

All Java paths start at `src/main/java/com/daviddunn/retirementplanner/` unless a test prefix is shown.

### Modified production files — 7

| File | Purpose |
|---|---|
| `domain/model/RetirementPlan.java` | Additive optional UUID, strict validation, legacy-compatible constructor and existing-constructor compatibility |
| `persistence/JsonRetirementPlanRepository.java` | Commit identity after success; identity substitution preserves original numeric tokens; ordinary saves retain normal Jackson bytes |
| `ui/controller/ApplicationController.java` | Transactional Save As identity, isolated copy assessment/cloning, store access, transient legacy session ID and copy-completion reporting |
| `ui/MainApplication.java` | Stop the result executor only when accepted persistence work is idle/drained |
| `ui/MainWindow.java` | Async copy warnings and normal-exit result-save drain/retry/cancel-exit guard |
| `ui/socialsecurity/SinglePersonMortalityView.java` | Narrow accepted-result hook before render and scoped storage-status label |
| `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java` | Wire the controller-bound individual weighted pilot; informative unsaved-plan notices |

### Added production files — 17

| Directory | Files |
|---|---|
| `app/socialsecurity/results/` | `AnalysisKind.java`, `SnapshotContext.java`, `WeightedSnapshots.java`, `AnalysisSnapshot.java`, `AcceptedAnalysisCompletion.java`, `AnalysisSnapshotRepository.java`, `AnalysisStorageLocation.java`, `AnalysisFingerprintService.java`, `AnalysisCompatibilityPolicy.java`, `AnalysisSuccessPolicy.java`, `AnalysisVersions.java`, `AnalysisResultStore.java`, `ApplicationAnalysisResults.java` |
| `persistence/` | `AnalysisSnapshotCodec.java`, `JsonAnalysisSnapshotRepository.java` |
| `ui/socialsecurity/` | `IndividualCompletionPersistence.java`, `AnalysisSnapshotPresentationMapper.java` |

### Added test/support files — 9

Prefix: `src/test/java/com/daviddunn/retirementplanner/`.

- `persistence/AnalysisIdentityTest.java`
- `persistence/AnalysisSnapshotRepositoryTest.java`
- `ui/socialsecurity/SnapshotFixtures.java`
- `ui/socialsecurity/AnalysisSnapshotCodecTest.java`
- `ui/socialsecurity/AnalysisFingerprintTest.java`
- `ui/socialsecurity/AnalysisResultStoreTest.java`
- `ui/socialsecurity/AnalysisSuccessPolicyTest.java`
- `ui/socialsecurity/AnalysisSnapshotMeasurementTest.java`
- `ui/socialsecurity/IndividualCompletionPersistenceTest.java`

Modified existing tests — 2:

- `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/Stage5DComparisonTest.java`: compare each independent source plan against its own unchanged JSON and assert distinct UUIDs. Financial comparison assertions remain unchanged.
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/WizardFinancialEquivalenceTest.java`: exclude only nonfinancial `planId` when comparing independently constructed financial inputs; add identity independence/preservation checks. Exact financial inputs, annual outputs, scales and seeded Monte Carlo assertions remain unchanged.

Added documentation: this report. **36 task files: 9 modifications and 27 additions.** The pre-existing Phase 9A report is outside that count. No financial assertion/golden value was weakened or regenerated.

## 4. Durable UUID schema and migration

`RetirementPlan.getPlanId()` serializes an optional canonical lowercase UUID string. Existing Java constructors keep their signatures and assign a new identity; the Jackson constructor accepts a nullable sixth `planId` argument. Missing/null legacy identity remains null on load. Reading a plan does not rewrite it.

`validatedPlanId` rejects shortened UUID forms, malformed strings, uppercase/noncanonical values, empty strings and path-like values. UUIDs are independent of names, paths and financial content. Scenario JSON copies preserve the existing ID; calculations never consult it.

`JsonRetirementPlanRepository.saveWithIdentity` writes the proposed ID into the file first, through the existing verified transactional writer. Only after successful replacement does it publish that identity to the in-memory plan. Failed legacy migration leaves the old bytes and null in-memory ID intact. Ordinary repeated saves preserve the same ID and the original Jackson serialization bytes. When identity changes, streaming token copying preserves numeric text/precision/scale; a general JsonNode round trip is deliberately avoided for plan saves.

## 5. Save As and snapshot cloning

First Save As of an untitled plan binds its existing new-plan UUID. Save As of an already saved plan to a different normalized path creates a new UUID; a recovery Save As also treats the original recovery path as the identity source. Saving to the same current path preserves identity.

The controller commits the new plan before changing its active path/identity. Failed Save As leaves the original path, UUID, file and dirty state intact. Successful plan save is not reclassified as failed if historical copying fails.

After commit, an isolated plan copy and frozen source/target IDs are handed to the background store. Available readable supported snapshots are cloned with the new binding and `originPlanId`, retaining original run ID, completion time, outputs, assumptions, algorithms, references and historical context. Source snapshots are not modified. A clone-of-clone retains the earliest recorded origin.

`copyAssessment` evaluates financial/context fingerprints and compatibility against the new plan under the original frozen analysis settings. It does not infer Current merely from copying. A later UI must independently reassess its next-run controls. Wrong household shape is historical/outdated. Unknown/unreadable source files generate copy warnings and remain untouched.

Copy completion is a separate future; `MainWindow` surfaces asynchronous failure through existing persistence warnings. There is no success claim that all copies completed before the future resolves. Copying is atomic **copy-if-empty** under the destination file lock: an initial or retried historical copy cannot replace an existing result computed for the new plan. Existing target data is retained and assessed instead. The original snapshots remain available; `cloneSnapshots` is an explicit retryable API. A dedicated failed-copy retry screen remains 9E work.

Persistent `plan-binding.txt` and a binding file lock reject a second existing path with the same UUID once an identity has been claimed. An external move is permitted when the previous path no longer exists. External copies made before the first claim, hard-link/alias distinctions, and richer copy/move resolution remain 9E limitations. Snapshot and binding commits use separate OS locks; same-plan multi-process business ordering is not a distributed synchronization guarantee.

## 6. Unsaved plans

All existing analyses remain runnable. The pilot captures accepted results into MEMORY_ONLY application-store entries when no saved-file binding exists. Legacy plans use a transient controller session ID without mutating the legacy plan UUID. Snapshot materialization refuses a null plan-file binding.

Memory-only results are available programmatically for the application lifetime and do not enqueue disk writes or block normal exit. The current view continues showing its completed data. Restoring them into a newly constructed view is not implemented before 9C. Unwired modes retain their existing in-dialog behavior; they are not claimed protected by the pilot.

The UI explicitly states that the plan must be saved for persistence and that no plan was autosaved. First successful save does **not** silently backfill an earlier result. Current minimal behavior is to rerun after saving. A safe future explicit “save this completed result” action could compare the current effective input/compatibility fingerprints to its frozen completion, attach the saved identity without recalculation, and preserve the original completion timestamp. That action is a recommendation, not implemented recovery functionality.

## 7. Snapshot envelope and exact codec coverage

Schema/fingerprint versions are both 1. The envelope includes UUID binding, explicit seven-valued analysis kind and household shape, run ID, accepted-completion Instant, diagnostic build ID, algorithm/reference maps, input/context fingerprints, content digest, structured payload, frozen report/request context, typed baseline provenance, historical comparison attachments, validated optional view preferences, and optional clone origin.

Gzip JSON uses existing Jackson and JDK gzip. Dates/times/durations use ISO representation. Exact BigDecimal tokens/scales, nulls, ordered lists, success/failure alternatives and Optional/OptionalInt distinctions have explicit storage forms. There is no default typing or runtime-object serialization API.

| Kind | Codec/data support in 9B | Production completion integration | UI restore |
|---|---|---|---|
| Couple SS Only | Identifier, fingerprint and success contract; full codec deferred | Deferred 9D | Deferred |
| Couple Quick | Identifier, Q fingerprint and success contract; full codec deferred | Deferred 9D | Deferred |
| Couple Exhaustive | Identifier, F fingerprint and success contract; retained-projection codec deferred | Deferred 9D | Deferred |
| Couple Weighted | Complete current weighted result/baseline/equivalence/work/context codec foundation | Deferred 9C | Deferred 9C |
| Individual deterministic | Identifier, fingerprint and success contract; projections/Break-Even codec deferred | Deferred 9D | Deferred |
| Individual SS Only | Complete nine-entry mortality result codec foundation | Deferred 9D | Deferred 9D |
| Individual Weighted | Complete mortality/outcome/result/context codec foundation | Limited controller-bound pilot | Deferred 9C |

Unsupported payload families are rejected explicitly; there are no placeholder projection codecs. No retained `ProjectionYear`/source-attribution data is silently discarded by a purported deterministic codec.

## 8. Weighted DTO fidelity

`WeightedSnapshots.Couple` retains objective, full metadata, original ordered entries, ranked occurrence references, optional baseline, work/continuation counters, elapsed time, completion status, optional equivalence groups/representatives/coverage statistics and avoided evaluations. Entries preserve exact elections, aggregates or structured failures, optional retained scenario details, nullable rank and differences. Null details versus present empty details remain distinct.

Individual DTOs retain mode/name/current age, complete frozen mortality distribution/request/table/convention, valuation/discount/COLA/inflation, nine ordered elections/entries, all retained per-death outcomes and original projection count. SS-only absent assets/empty integrated outcomes remain absent/empty rather than invented zero results.

`SnapshotContext` retains original formatted report sections exactly, structured baseline election values/sources/problem and optional strategy, plus typed historical comparison rows with exact decimals/nullable ranks and source identity/fingerprint availability. `AnalysisSnapshotPresentationMapper` round-trips the existing pure comparison rows; no view is restored. Human-readable sections supplement these typed values instead of replacing them.

Known immutable scalar/value records are reused only as explicitly typed leaves (metadata, work, aggregates, mortality scalar records, estate/outcome records). No polymorphic arbitrary object field exists. Schema guard tests freeze the principal reused result-record component lists so those contracts cannot grow silently. Runtime graphs containing Optional, UI-private context, mutable Projection or ignored source details are not serialized automatically.

Codec tests verify new object graphs, exact values/scales/order/ties/failures/details/equivalence/context, pure weighted heat-map equivalence, and individual PDF adapter data equivalence. Reconstruction invokes no benefit/projection/optimization engine. Preparing the original individual test fixtures does execute the existing deterministic test analysis, separately from codec reconstruction.

## 9. Fingerprint design and dependency coverage

`AnalysisFingerprintService` performs versioned SHA-256 canonicalization, sorting object keys but retaining array order. Decimal value and scale both participate; names/paths/random UUIDs/unrelated times do not. Effective defaulted model values are captured through existing model serialization, not UI display text. S/M/V/B/Q are explicit typed groups; integrated F includes the complete effective persisted financial graph after documented exclusions. Unknown top-level plan fields are refused until classified.

| Kind | Included dependencies |
|---|---|
| Couple SS | S, shared M/V; no balances/spending/tax inputs |
| Quick | S/F plus Q: prerequisite fingerprint, count, exact ordered candidates, original ranks and all precise SS expected values |
| Couple exhaustive | S/F, configured horizon/death/survivor context; no analyzer mortality/valuation settings |
| Couple weighted | S/M/V/F/B; scenario timing replaces configured timing; expense factor remains financial |
| Individual deterministic | Primary S/F and optional death-year override; no analyzer M/V |
| Individual SS | Primary S and its independent M/V; general inflation reference is contextual, not own-benefit PV |
| Individual weighted | Primary S/F and its own independent M/V; exact individual death horizon |

F includes ordered accounts and ownership/balances/inherited/basis/participant/opening-RMD data; income/pensions and qualification/survivor facts; spending and recurrence/inflation/dates; economic/tax/state/withdrawal/Roth/non-investable/estate assumptions. Stored unrelated plan baselines and identity/display names are excluded. Nested financial properties remain included by default; newly added top-level plan properties require explicit classification.

Weighted timing exclusions remove only configured death scenario/year/survivor-age timing and configured length. Source review confirmed `ProjectionEngine` still applies `postDeathExpenseFactor` to recurring spending under scenario timing: it is retained in F and has a dedicated mutation case. No engine change was needed. The configured horizon/timing description is also compared through a separate effective-context digest.

The assessment API requires callers to report invalid pending text, yielding INPUTS_NEED_ATTENTION rather than silently supplying last-valid UI values. Completed snapshots fingerprint the actual frozen request; current-editor currency/restore integration is not implemented in 9B. Returning effective values to the original restores Current when compatibility/context match.

Mutation tests cover all seven modes, identity/name independence, balances/order, spending, opening RMD, economic/tax/COLA/death-expense fields, M/V/candidate/baseline separation, map ordering, decimal scale, revert and clone assessment.

## 10. Compatibility policy

Storage existence/readability, financial semantics, input currency, durability and running replacement are separate contracts. Principal assessment states are No Saved Results, Current, Outdated, Incompatible/Unavailable, plus Inputs Need Attention. Store durability is MEMORY_ONLY/PENDING/SAVED/SAVE_FAILED, independent of financial job state.

Unknown schema, missing fields, malformed identity, unknown properties, duplicate JSON keys, bad integrity or invalid graph is rejected and left intact. Known readable v1 financial meanings can be compared with current compatibility maps; unsupported saved algorithm semantics are Unavailable, while supported historical semantics with changed current versions are Outdated. UI-only build ID is diagnostic and not a currency dependency.

`AnalysisVersions` provides explicit semantic compatibility IDs and shipped resource digests. SS-only does not depend on tax resources. Integrated reference identity conservatively covers the current government/state/Iowa resources; splitting a shared state dataset into per-state dependency digests is deferred. Reference hashing is local and runs on the I/O worker, not FX.

## 11. Atomic repository and recovery

`JsonAnalysisSnapshotRepository` uses UUID/enum-derived paths under `AnalysisStorageLocation`; no household/name input constructs a path. Windows uses LOCALAPPDATA with an AppData/Local fallback, macOS Application Support, and other systems an absolute XDG_DATA_HOME or `.local/share` fallback.

Reuses the existing package-private `AtomicFileWriter`, unchanged: unpredictable sibling temp, file force/close, temp verification, recognized adjacent `.bak`/ownership record, old destination verification, atomic replacement only, rollback of backup preparation, conservative cleanup. No non-atomic fallback. Primary plan persistence has its own validator; result verification cannot alter plan JSON.

Snapshot writes and identity claims use dedicated OS file locks as well as the existing in-process writer lock. Paths reject symbolic links; binding text is bounded and must contain an absolute path. Existing live-path UUID conflicts refuse the write instead of merging snapshots.

Limits: 16 MiB compressed, 32 MiB decoded, nesting depth 80, string length 256,000, numeric token length 1,024, at most 2,000,000 JSON tokens and 100,000 arrays. File reads are bounded even if a file grows after its size check. An indeterminate/unreadable file is not treated as absent. Strict typed constructors verify shape, identities, occurrence/ranking references, ties, success/failure exclusivity, equivalence coverage and individual mortality outcomes.

Corrupt or unknown-schema primary files cannot be overwritten through normal save. A recognized valid backup can be loaded explicitly; it is not silently promoted. Unknown crash remnants are not scanned/deleted. Permission/atomic-move/write/verify/backup failures preserve prior committed data; cleanup failure can leave an owned temp remnant without invalidating an already committed result.

All-bytes codec/writer and global SAVE_LOCK are retained. The measured 5,184-entry DTO fixture is below configured limits, but larger production distributions and low-memory hardware require further 9E measurements. Compression and initial payload encoding occur before acquiring the transactional writer lock; verification still holds it. Atomic rename/file force do not guarantee every hardware/power-loss failure or directory-metadata outcome.

## 12. Accepted-completion integration

No coordinator/job-controller financial scheduling change. Existing `eligible/finish` ownership, generation, revision and cancellation checks—including the second post-observer check—remain intact.

The pilot prepares frozen plan/file identity, input/context fingerprints and report context at admission. The existing eligible-success callback hands the immutable result to `IndividualCompletionPersistence` before rendering, then continues original render/PDF behavior. Closing the dialog after acceptance cannot cancel the background store. An old-plan callback cannot redirect its write to a new active plan path.

Family predicates: SS requires completed optimization candidates; Quick/deterministic searches require a successful candidate; weighted comparison requires a nonempty successful ranking; individual mortality requires the complete nine-entry result (its calculator throws on scenario failure). DTO validation rejects incomplete graphs. Cancellation, stale callback, fatal failure and rejection never enter this handoff. Partial candidate failures remain explicit and do not renormalize probability.

Other callbacks are unwired and explicitly deferred. No production analyzer is claimed cross-session-restorable.

## 13. Last success and background ordering

Application-owned `AnalysisResultStore` uses one I/O worker and an eight-task bounded queue, separate from financial admission. At most 64 active in-memory slots are retained; durably saved memory entries may be released while disk data remains. If all retained slots still need retention, admission reports backpressure instead of growing without bound.

Each slot has a monotonic acceptance sequence. A queued older completion can be coalesced only in favor of a newer **accepted success**, never a started/cancelled/failed run. The single I/O worker ensures a newer commit follows an already-running older write. Previous disk primary remains until verified commit. Write failure retains the newer completion in memory, the prior disk result intact, SAVE_FAILED status and a retry API requiring no recalculation.

Plan switches do not change a frozen key. Pending/saved/failure futures allow presentation without making controls part of storage. MEMORY_ONLY entries never materialize a durable snapshot or enqueue disk I/O. Snapshots on disk are not automatically evicted.

## 14. Shutdown behavior

`drain(Duration)` stops new persistence admission, waits behind accepted work and returns pending/failure information. It runs off FX and never reports success before disk operations complete. A cancelled exit can resume admission; failed accepted saves can retry. Explicit successful drain/idle shutdown closes the I/O executor.

MainWindow keeps the existing plan-departure decision first. If result work remains, it drains asynchronously, displays a saving notice, and either closes after success or stays open with retry/cancel-exit handling. This does not alter plan dirty-state/save confirmation semantics. MainApplication refuses an idle shutdown claim when accepted result work is pending.

Native forced termination/power loss remains outside normal-exit guarantees. Cancellation of the saving notice can take until the bounded drain returns before another exit attempt; richer close/retry UX and dedicated failed-clone retry presentation remain 9E work.

## 15. Actual storage/performance measurements

Measurement uses existing completed synthetic couple presentation fixtures, real deterministic individual test fixtures, and a clearly labeled **DTO-only** complete candidate-universe scale fixture. No hour-long search ran. Fixture construction is outside timing. Three warm-ups, ten measured iterations per sample; durations are mean wall-clock milliseconds. Encode includes validation/digest and gzip; decode includes parse/digest/validation, so the separately timed pure runtime reconstruction is an additional breakdown, not a subtraction.

Environment: Temurin OpenJDK 25.0.4.1+1-LTS, Windows 11, 8 available logical processors, `Intel64 Family 6 Model 204 Stepping 3, GenuineIntel`, `-Xmx8g` (8,589,934,592 bytes). WMI CPU model lookup was denied; no hardware minimum is inferred. No native DPI or assistive-technology certification is claimed.

Measured in the successful full run logged at `target/phase9b-full-final.log` (snapshot codec/payload schema is unchanged by the subsequent copy-if-empty repository safeguard):

| Fixture | Strategies / compact outcomes | Raw JSON bytes | Gzip bytes | Serialize+gzip ms | Decode+validate ms | Pure result reconstruction ms | Presentation ms | Net used-heap delta, bytes |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Couple weighted completed synthetic presentation fixture | 3 / 1 | 10,832 | 3,279 | 0.407 | 0.581 | 0.006 | 0.044 | 2,099,800 |
| Couple complete-universe **DTO-only scale fixture**, not a financial search | 5,184 / 0 | 3,674,550 | 155,569 | 33.645 | 42.985 | 0.751 | 1.398 | -158,779,224 |
| Individual SS completed test fixture | 9 / 0 | 9,778 | 3,952 | 1.327 | 2.534 | 0.030 | 0.006 | 4,194,304 |
| Individual weighted completed test fixture | 9 / 522 | 126,185 | 25,803 | 3.021 | 3.138 | 0.126 | 0.010 | 50,331,648 |

Couple presentation timing is the pure heat-map adapter; individual timing is ranking reconstruction only. Individual report-adapter fidelity is tested separately; full UI/PDF timing was not measured. All implemented payloads retain zero full ProjectionYear graphs; that is the actual weighted/individual result shape, not omitted retained deterministic detail. Heap figures are net used-heap changes over ten loops without forced GC, **not retained size or peak memory**; negative values indicate collection/noise. This is a fixture measurement, not a production hardware benchmark or a promise about every possible result size.

## 16. Focused test execution

All Maven runs use IntelliJ's bundled executable and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`. No dependencies/build configuration changed.

| Run | Run / passed / failures / errors / skipped |
|---|---|
| Before-edit job/file baseline | 57 / 57 / 0 / 0 / 0 |
| First existing persistence/controller/job regression | 187 / 186 / 1 / 0 / 0 |
| Corrected identity/codec/fingerprint/file checks | 67 / 67 / 0 / 0 / 0 |
| Added store and measurement checks | 73 / 73 / 0 / 0 / 0 |
| Actual individual pilot/lifecycle checks | 7 / 7 / 0 / 0 / 0 |
| Consolidated gate checks before final review | 129 / 129 / 0 / 0 / 0 |
| Final infrastructure plus Stage 5D focused checks | 153 / 153 / 0 / 0 / 0 |
| Independent wizard/Stage 5D/fingerprint follow-up | 59 / 59 / 0 / 0 / 0 |
| Final copy-if-empty/bounded-read/retention storage checks | 26 / 26 / 0 / 0 / 0 |

Counts overlap. The existing failure detected identity-field reordering in an intermediate writer, not numerical changes. Ordinary saves were corrected to retain exact Jackson bytes; the existing assertion was not changed. One initial test compilation attempt also caught an invalid compound `var` declaration in a new test, corrected locally before execution. Final verification below supersedes intermediate runs.

Coverage includes old primary/backup bytes at every precommit fault seam, permission/atomic-move failure, corruption/recognized recovery, unknown schema, oversized/over-nested gzip, bad integrity/binding, UUID migration/failure/fork, source-copy provenance, independent household keys, exact DTO round trips, unchanged pure presentation/PDF values, bounded queue, retry/drain, original callback rejection and actual unsaved/saved pilot completion.

## 17. Full Maven regression

The established complete suite is run with `-DargLine=-Xmx8g test`, including its normal benchmark/opt-in conventions.

The first complete run: **2,996 run, 2,958 passed, 5 failures, 0 errors, 33 skipped**, 8 minutes 35 seconds, finished October 19, 2026 at 11:21:18 EDT. All five failures were the newly invalid assumption that independently created plans have identical whole JSON despite their approved distinct UUIDs: one Stage 5D concurrency case and four wizard financial-equivalence cases. The two tests were adapted only for identity metadata as described in Section 3; no expected financial number changed. The follow-up covering those cases passed 59/59. Source review also retained the post-death expense factor in financial fingerprints and completed typed historical comparison attachments before final verification.

The second complete run passed: **2,997 run, 2,964 passed, 0 failures, 0 errors, 33 skipped**, 9 minutes 2 seconds, finished October 19, 2026 at 11:32:28 EDT. It also supplies the measurements above. A subsequent review added atomic copy-if-empty protection so retrying historical cloning cannot replace the target's own successful result, and bounded/indeterminate file-read handling. The 26 final storage checks passed.

**Final acceptance run: 2,998 run, 2,965 passed, 0 failures, 0 errors, 33 skipped; BUILD SUCCESS, 9 minutes 7 seconds, finished October 19, 2026 at 11:43:33 EDT.** This compiled and tested the final storage guard, bounded reads and all other production/test changes. Existing financial goldens, deterministic/weighted calculations, seeded Monte Carlo, state/federal tax, RMD, Roth, wizard lifecycle/equivalence, persistence, analyzer job/state and PDF tests passed. The normal opt-in/disabled cases account for the skips; no failing test was newly skipped. No enabled screenshot/native accessibility check is claimed for this infrastructure phase.

Ignored evidence logs: `target/phase9b-baseline.log`, `phase9b-existing.log`, `phase9b-focused-second.log`, `phase9b-focused-third.log`, `phase9b-lifecycle.log`, `phase9b-focused-gates.log`, `phase9b-final-focused.log`, `phase9b-identity-regression.log`, `phase9b-copy-guard.log`, `phase9b-full.log`, `phase9b-full-final.log`, and `phase9b-full-acceptance.log` (all under `target/`). Intermediate compile/focused logs are also confined to that ignored directory. Counts across runs overlap and are not unique totals.

## 18. Known limitations and next phases

- **9C:** wire couple weighted accepted completion; build complete couple/individual weighted UI restore adapters, current/outdated labels/timestamps, selection restoration and frozen historical PDF paths. No restore UI or outdated PDF-readiness change was implemented here.
- **9D:** full couple SS context/optimization codec; Quick original-ranking/provenance codec; exhaustive retained projections/source data and individual deterministic Break-Even codecs; remaining completion callbacks and restoration. Unsupported families cannot silently pass a weighted codec.
- **9E:** larger distribution/low-memory measurements, native Windows file/DPI/assistive checks, broader crash/process/copy/alias cases, richer retry/portability/deletion UX, dedicated failed-copy recovery and compatibility migrations. No migration framework or analysis browser is present.
- Version IDs must be maintained deliberately when algorithms/reference interpretation change. Integrated shared dataset digests can conservatively mark unrelated state changes outdated.
- Memory-only results survive in the application store only for the wired pilot; no cross-session promise applies before a successful plan save, and first save does not automatically backfill.
- Persisted files contain sensitive results and original report assumptions, including local plan-path binding metadata. No encryption, telemetry, network, automatic cleanup or remote service was added.

Manual acceptance on disposable synthetic plans: confirm legacy Open does not rewrite JSON; save/reopen preserves UUID; Save As forks and keeps source unchanged; run saved individual weighted and observe saving/saved status; run untitled individual weighted and observe memory-only/no autosave; test pending-write exit/retry; inspect cloned origin/time; verify native file-lock/read-only behavior. A reopened analyzer is expected to lack restored result views until 9C—do not present that deferred feature as passed.

## 19. Git verification and final decision

Final branch/HEAD remain `main` / `0fa51e012a5683bc9e82e30ce57f175146134378`. Expanded working-tree inventory: **9 tracked modifications and 28 untracked files**, including the one pre-existing Phase 9A report. Task scope is therefore **36 files: 9 modified and 27 added**, exactly Section 3. The pre-existing Phase 9A report remains unchanged/untracked.

`git diff --check` passes; separate trailing-whitespace inspection includes the new files/report. Staged diff is empty. Protected resource/golden/build/projection/tax/RMD/Roth/export paths have no diff. Source inspection confirms the financial engines and job-controller/coordinator implementation were not changed. Generated Maven logs/artifacts remain ignored under `target/` and the established local Maven repository. No staging, commit, push, reset or discarded work.

Infrastructure is ready for review with the codec/pilot boundaries and native/manual checks above. Complete cross-session analyzer restoration is **not** a Phase 9B acceptance claim.

Phase 9B stops at infrastructure review; Phase 9C is not begun.

PHASE-9B-PERSISTENT-ANALYSIS-INFRASTRUCTURE-COMPLETE
