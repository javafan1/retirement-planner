# Phase 9D-1 — Couple Social Security Only Persistence

## Current status: Phase 9D-1 implemented and verified; stopped for review

The user subsequently authorized Gates 2–5 after the successful Gate 1 verification recorded below. Couple SS persistence/restoration is now implemented. Final verification is recorded in the implementation section; the earlier Gate 1 history remains for traceability. Quick persistence and other Phase 9D stages are not implemented.

## Initial repository state

- Branch: `main`.
- HEAD: `1ff71f3b71fca97af49a7d2058aa14284038dd9e`.
- Initial working tree: only untracked `PHASE-9D-REMAINING-ANALYZERS-AUDIT.md`; preserved unchanged.
- Read the root instructions and inspected the approved audit, existing snapshot envelope, codec, weighted DTOs, completion/store contracts, compatibility/fingerprint policy, SS result/request types and existing dialog/restoration tests.

## Frozen pre-extension compatibility evidence

Before changing production source, a temporary capture test executed against the unchanged production codec. It captured these new storage-compatibility resources (not financial golden replacements):

| Resource under `src/test/resources/analysis-snapshots/` | Compressed bytes | SHA-256 |
|---|---:|---|
| `weighted-couple-v1.json.gz` | 3,261 | `fb4300180df4698722cbdde8b46359d21b7f0759736b3c177dc4d2e1458cc80a` |
| `weighted-individual-v1.json.gz` | 25,778 | `f064faeeb8f061a5131d34af068816d6dcf81f15203526ee4ccaa17541d15a07` |

The couple fixture contains tied weighted entries, retained scenario detail, a structured failure, baseline, equivalence/work metadata and frozen comparison/report context. The individual fixture uses the existing deterministic integrated-mortality fixture and its nine-entry result. These are synthetic test inputs, not user financial data.

Capture test: **1 executed, 0 failures, 0 errors, 0 skipped**; `target/9d1-freeze.log`, BUILD SUCCESS. The capture code was then replaced by read-only verification; there is no regeneration path in the retained test.

The retained `LegacyWeightedSnapshotCompatibilityTest` pins both gzip hashes and requires decode, exact raw JSON byte equality (including field order and decimal representation), payload digest equality, and exact gzip re-encoding.

## Attempted minimal contract extension

Added `AnalysisPayload` with validation/success methods and legacy weighted accessors. Changed the envelope, accepted completion and digest content declarations from the concrete weighted payload to that interface. The weighted record retains exactly its original two components; no fields, schema versions, financial algorithm identifiers or limits were changed.

The codec dispatch is explicit and allowlisted by analysis kind. It does not accept runtime type names or enable polymorphic deserialization. At this gate it still allows only the existing weighted/individual-mortality payload families. Couple SS is not yet enabled.

### Actual failed proof

Focused command used IntelliJ bundled Maven and the required project-local repository:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-Dtest=LegacyWeightedSnapshotCompatibilityTest' test
```

Result: **2 executed, 0 assertion failures, 2 errors, 0 skipped; BUILD FAILURE**. Evidence: `target/9d1-gate1.log` and the corresponding Surefire report.

Both errors are `MismatchedInputException`: a trailing `FIELD_NAME` after `WeightedSnapshots.Payload`, through `AnalysisSnapshot["payload"]`. The compiled custom deserializer called `JsonParser.readValueAs`, which invokes the mapper's root-level trailing-token enforcement inside the nested payload. The next legitimate envelope field was consequently rejected. This is a decoder-extension defect, not evidence of invalid financial data or corrupt frozen files.

While this Maven invocation was still running, source was narrowly corrected to use `DeserializationContext.readValue(parser, WeightedSnapshots.Payload.class)` instead. The failure stack confirms the invocation tested the earlier compiled `JsonParser.readValueAs` implementation. At the initial stop, this source correction had not been compiled or tested. The subsequently authorized verification below now establishes compatibility.

The initial recommended action was to compile and rerun the frozen-byte proof against the corrected nested deserialization call. That verification has now passed without weakening trailing-token enforcement or regenerating fixtures.

## Changes retained for review

Production modified:

- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AcceptedAnalysisCompletion.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisSnapshot.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/WeightedSnapshots.java`
- `src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java`

New:

- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisPayload.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/LegacyWeightedSnapshotCompatibilityTest.java`
- Both frozen gzip resources listed above.
- This report.

During the authorized verification-only resumption, also modified `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisSnapshotCodecTest.java` to add complete-document trailing-JSON rejection coverage. Added a clarifying comment at the corrected nested decode call and explicit comparison of all frozen/re-encoded numeric tokens using scale-sensitive `BigDecimal.equals`.

The pre-existing untracked Phase 9D audit remains present. Nothing was staged, committed, pushed, reset or discarded. No user snapshot files were accessed or changed.

## Deferred behavior and verification

At the Gate 1 review stop, Gates 2–5 were unimplemented. The implementation and measurements below supersede that earlier boundary. Frozen weighted resource sizes above are not Couple SS capacity estimates.

No financial engines, rankings, mortality models, algorithm identifiers, PDF exporters, golden expectations, persistence limits or transactional writer behavior were changed. During the Gate 1-only resumption, the full Maven regression suite was not run, as instructed. The subsequent implementation full-suite result appears at the end of this report.

## Authorized Gate 1 verification findings

Verification date: October 20, 2026. Initial status at resumption matched the previous stop; all existing changes were preserved.

### Cause and minimal correction

Both original errors share the same stack: `JsonParser.readValueAs` called `ObjectMapper._readValue`, which called `_verifyNoTrailingTokens` while processing `AnalysisSnapshot["payload"]`. The following enclosing `context` field was mistaken for trailing document content. The failure was introduced by the dispatch adapter; it was **not a genuine legacy wire-format incompatibility**.

The corrected call uses the active `DeserializationContext` to read exactly one nested `WeightedSnapshots.Payload` value. Its enclosing `ObjectReader` remains responsible for complete-document consumption. `FAIL_ON_TRAILING_TOKENS` remains enabled globally on this snapshot-specific mapper; no validation feature was disabled. The preliminary complete-document tree read also retains that setting. The correction adds no parser, financial model, alternate schema or compatibility-version changes.

### Exact legacy fidelity

Both frozen resource SHA-256 hashes above remain unchanged. Tests verify exact uncompressed JSON byte equality, exact gzip re-encoding, and the original payload digests:

| Frozen family | Original payload digest | Result |
|---|---|---|
| Couple weighted | `ef23be83283383bb22de67cafb7da201664915f4a526c247e0dfcf4caf12e569` | Exact match |
| Individual weighted | `b058f2b969b0ec3a0a16b595ec231e922bb4062714bde8f70a1ac68d259794e0` | Exact match |

The numeric-token comparison uses `BigDecimal.equals`, preserving unscaled values and scales, in addition to byte equality. Existing codec/numeric tests separately compare restored financial DTOs and explicit scales, including 1,024-, 1,070- and 4,096-character values. No fixture was regenerated.

### Trailing-content regression

New test `AnalysisSnapshotCodecTest.completeSnapshotRejectsUnexpectedTrailingJson` appends an object, array, `null`, or number to a valid snapshot. Each fails with Jackson's trailing-token `MismatchedInputException`. A whitespace-only suffix is accepted and reconstructs the same snapshot. Together with frozen envelopes containing fields after `payload`, this proves the separation between nested-value consumption and full-document validation.

### Executed tests

1. Frozen compatibility proof alone: **2 run, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS; `target/9d1-gate1-resume-compatibility.log` (13.697 seconds).
2. Targeted regression command:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-DargLine=-Xmx8g' '-Dtest=LegacyWeightedSnapshotCompatibilityTest,AnalysisSnapshotCodecTest,AnalysisSnapshotRepositoryTest,WeightedRestorationTest,WeightedNumericLengthTest,WeightedSnapshotSaveHotfixTest,AnalysisResultStoreTest,TransactionalPlanPersistenceTest,AnalysisIdentityTest' test
```

Result: **82 run, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS, 33.825 seconds. Log: `target/9d1-gate1-resume-regression.log`. This includes the two compatibility tests again. Fault-injection tests intentionally emit diagnostic exceptions; their assertions passed.

Coverage includes couple/individual DTO fidelity, pure presentation restoration, asynchronous weighted restoration and binding guards, outdated/current state, failed-save retry, application-owned write ordering, Save As identity/clone behavior, corruption/incompatible files, numeric constraints, transactional verification and preservation of previous primary/backup files. No visual preview, native Windows accessibility check or full Maven suite was run during this verification-only task.

### Final Git state and review boundary

`git diff --check` passes. Branch and HEAD remain as initially recorded. Five tracked files are modified: the four production files listed above and `AnalysisSnapshotCodecTest.java`. Untracked files remain the interface, frozen compatibility test, two frozen resources, this report and the pre-existing Phase 9D audit. No files were staged, committed, pushed, reset or discarded. Generated logs remain under ignored `target/`.

**Gate 1 compatibility proof passed.** Work stopped for review at that point. The user then explicitly authorized the implementation below.

## Gates 2–5 implementation

### Architecture and exact file inventory

The existing repository, bounded application-owned result store, job controller/coordinator, transactional writer, fingerprints, algorithm/reference identifiers, plan identity and Save As lifecycle remain authoritative. No new persistence executor, financial model or dependency was introduced.

Production changes, including the retained Gate 1 changes:

| File under `src/main/java/com/daviddunn/retirementplanner/` | Change |
|---|---|
| `app/socialsecurity/results/AnalysisPayload.java` (new) | Minimal typed payload-family contract |
| `app/socialsecurity/results/AnalysisSnapshot.java` | Envelope accepts that contract, with existing validation |
| `app/socialsecurity/results/AcceptedAnalysisCompletion.java` | Accepted-completion payload contract |
| `app/socialsecurity/results/WeightedSnapshots.java` | Implements the contract; weighted record components unchanged |
| `app/socialsecurity/results/CoupleSocialSecuritySnapshot.java` (new) | Explicit immutable SS storage graph and checked local references |
| `app/socialsecurity/results/AnalysisCompatibilityPolicy.java` | Assess Save As copies using frozen Couple SS settings |
| `persistence/AnalysisSnapshotCodec.java` | Explicit kind dispatch; new field-name allowlist for privacy-conscious numeric diagnostics; nested decode correction |
| `ui/socialsecurity/CoupleSocialSecuritySnapshotMapper.java` (new) | Pure capture/restoration mapping of the frozen UI context |
| `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java` | Couple SS completion/load/status integration and test seam, using existing workspaces and gates |
| `ui/socialsecurity/WeightedResultPersistence.java` | Reuse existing lifecycle/status helper for SS; analysis-neutral accessible text and kind-specific loading label |

Tests added/modified under `src/test/java/com/daviddunn/retirementplanner/`:

- `ui/socialsecurity/CoupleSocialSecurityPersistenceTest.java` (new): ten tests covering DTO fidelity, both stages, ties, references, restart, currency, Quick readiness, save failures/retry, clone identity, incompatible schema, late loads, actual completion and replacement eligibility, independent slots and relevant/irrelevant changes.
- `ui/socialsecurity/CoupleSocialSecurityPersistencePreviewTest.java` (new): opt-in actual JavaFX render of restored Current/Outdated states at wide/compact budgets.
- `persistence/AnalysisSnapshotFaults.java` (new): test-only bridge to the existing package-local transactional verification fault seam. Production visibility/APIs were not widened.
- `ui/socialsecurity/LegacyWeightedSnapshotCompatibilityTest.java` (new in Gate 1) and `ui/socialsecurity/AnalysisSnapshotCodecTest.java` (modified in Gate 1), plus both immutable frozen gzip resources.

Documentation: this report updated; pre-existing `PHASE-9D-REMAINING-ANALYZERS-AUDIT.md` preserved.

### Complete immutable snapshot and fidelity

`CoupleSocialSecuritySnapshot.Request` stores the original base strategy, ordered retirement-age axes, each person's exact mortality distribution once, conditioning/valuation dates, discount rate and optimizer settings. Frozen context retains names, benefit amounts and valuation years, original claiming dates, mortality categories/factors/table metadata and the complete longevity convention. No current plan references are retained.

The payload stores every original joint mortality scenario and Stage 1 grid cell. Each of the three maximum rankings retains its measure, exact maximum and ordered references to every tied cell. Retained Stage 1 cells use validated references to that same grid. Both ordered survivor-candidate lists, every Stage 2 complete strategy/value, exact maximum, ordered tied-strategy references, and both stage evaluation counts are preserved.

Validation checks reference bounds/uniqueness, grid row-major identities, mortality-distribution/scenario references and total probability, expected evaluation counts, all maximum ties, source-retirement/survivor membership and unique complete strategy identities. Runtime constructors retain their existing domain checks. Reconstruction creates value objects only: it does not call the optimizer, joint-scenario factory, mortality calculator, benefit calculator or projection engine.

Ranks are restored by the existing pure presentation transformation using the original ordered exact values; stable ties and competition ranks match. All decimal values retain scale. A runtime distribution lacks value-based `equals`; tests compare its explicit probability DTOs and all other context fields instead of relying on object identity. No financial assertion was relaxed or golden regenerated.

The original accepted table selection is captured as a validated within-snapshot occurrence reference when present. This is not a persisted row-sort index. Subsequent navigation/selection changes are not autosaved; financial strategy identities and all ranking data remain intact.

### Accepted completion and last-success retention

Only the existing eligible `SOCIAL_SECURITY` success callback hands a completion to the store, and only when `AnalysisSuccessPolicy.accepted(result)` holds (nonempty evaluated strategies). Couple SS has no structured per-candidate failure list in its runtime result contract: fatal calculation errors publish no accepted search. No new partial-success definition was invented.

Plan UUID/file and input/context fingerprints are captured before admission. Frozen result/context and original completion time are handed to the application-owned store. Compression, digest creation, disk writing and written-file verification remain on its bounded background I/O executor. The UI presents the new calculation separately from Pending/Saved/Save Failed state; Retry Save reuses that accepted completion without calculation.

Starting, canceling, failing or invalidating a replacement leaves the previous successful presentation and durable file intact. Existing generation/revision/cancellation gates are unchanged. Closing the dialog closes only the view's loading/status callbacks; accepted storage work remains application-owned. No plan autosave, settings writes or automatic analysis occurs during restoration.

### Asynchronous restoration and status

The SS workspace uses the existing binding/generation-aware `WeightedResultPersistence` helper, retaining its name to avoid a broad refactor. The selected initial SS workspace loads asynchronously. Decoding, validation and pure presentation/context construction happen off FX; only attachment to the original Recommendation, Claiming Grid, Top Strategies/details and Assumptions controls happens on FX.

The helper retains the original completion timestamp and exposes Current, Previous Results — Outdated, Incompatible or Unavailable, Inputs Need Attention, and the existing durability statuses. Invalid raw dates or factors do not substitute old valid values to manufacture Current. Compatible historical results remain inspectable. Reverting effective inputs restores Current. Irrelevant expenses or presentation changes do not invalidate the SS input fingerprint.

Different plan UUID/file bindings, newer accepted completions, closed views and obsolete asynchronous loads are guarded by the same existing generation/binding checks. No next-run field is overwritten from stored inputs.

### Quick prerequisite and independent slots

Passive restoration publishes the original immutable result/request metadata into the existing `presentation` and sets `socialSecurityResultCurrent` only after compatibility/fingerprint assessment. Existing Quick readiness and candidate selection consume those original strategies as before. Invalid/outdated/unavailable SS does not satisfy the prerequisite. No `SOCIAL_SECURITY_RESULT` job invalidation or job-generation increment is emitted for a load. A genuine new SS completion retains its existing Quick invalidation behavior.

Quick is never automatically run, and Quick persistence is not implemented. No Couple SS PDF exporter was added. Both weighted slots remain separate and pass their existing restoration/codec tests.

### Save As and identity

The existing independent UUID, copy-if-empty and original-provenance machinery is reused. `assessCopy` now extracts SS factors/dates/rate from this payload, so currency is assessed independently against destination effective inputs. Tests verify the clone's independent UUID, original timestamp/context/payload, source provenance and unchanged source snapshot. Existing store/identity tests retain copy ordering and failure safeguards. External-copy and concurrent Save As lifecycle limitations remain those of the existing infrastructure, not new identity semantics.

### Capacity measurements

Measured with Temurin OpenJDK 25.0.4.1, Windows JavaFX, Maven test JVM `-Xmx8g`, existing deterministic synthetic fixtures. The timing is one in-process measurement after fixture calculation, not a performance guarantee or production-sized mortality benchmark. Hardware enumeration through CIM was unavailable in the sandbox.

| Fixture | Retirement grid | Stage 2 strategies | Joint scenarios | Raw bytes | Gzip bytes | Encode ms | Decode/validate/reconstruct ms |
|---|---:|---:|---:|---:|---:|---:|---:|
| Positive benefits, full axes | 81 cells | 320 | 4 | 244,833 | 11,085 | 7.19 | 34.47 |
| Zero benefits, every cutoff/max tied | 81 cells | 5,184 | 4 | 2,970,497 | 43,121 | 41.72 | 108.54 |

Evidence: `target/9d1-final-focused-preview.log`. UUID changes account for small compressed-byte differences across runs. These are complete legitimate optimizer results under deliberately small two-point mortality distributions, not fabricated values. The actual Run-button test separately uses the factory's authoritative table-derived distributions at a late, valid conditioning date to keep runtime bounded.

No hour-long analysis was run. Broader production mortality-support performance remains a manual/9E measurement recommendation. No measured fixture approached the existing 32 MiB raw / 16 MiB gzip budgets. The 4,096-character numeric limit, token/array/depth/string bounds, checksums, backups, locking and atomic replacement guarantees are unchanged.

### Focused verification and rendered evidence

- Broad focused run: **188 tests, 0 failures, 0 errors, 1 existing opt-in navigation-preview skip**, BUILD SUCCESS, 54.268 seconds (`target/9d1-focused.log`). It includes repository/identity/transactional tests, fingerprints/store/job gates, weighted hotfix/numeric/restoration/codec, navigation/contextual-input and existing PDF-state regressions.
- Final new-mode/reference checks plus enabled preview and frozen compatibility tests: **13 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS, 27.121 seconds (`target/9d1-final-focused-preview.log`). The ten new-mode tests include the additional reference integrity and cross-mode checks.
- Earlier fixture setup mistakes (wrong request overload, comparison of identity-only runtime wrappers, and a conditioning date at the table's terminal age) were corrected in tests, not by changing domain behavior. All final focused tests pass.

Actual JavaFX images are generated under ignored `target/phase9c-restoration-preview/` with the `9d1-ss-` prefix, reusing the established preview writer. Preview synchronization was tightened after inspection found a stale viewport frame in an initial Outdated capture. Final recapture/inspection findings and full-suite results follow when complete.

### Manual acceptance checklist and limitations

1. Use a disposable saved couple plan with both Social Security records. Run SS Only once and wait for **Results saved**, not merely calculation completion.
2. Inspect Recommendation, grid cells/tie markers, Top Strategies and details, and frozen Assumptions. Close/reopen the analyzer, then restart the application and reopen the plan. Confirm the same completion timestamp and values without Run.
3. Verify Quick becomes ready from a Current restored SS result without automatic execution. Change a mortality factor/discount/date: old results remain visible and Quick becomes ineligible. Revert exact effective inputs and confirm Current/readiness returns.
4. Enter malformed pending text; verify Inputs Need Attention while historical results remain visible and unchanged.
5. Cancel a replacement, then attempt a replacement with an invalid required SS input on a disposable plan. The original successful SS snapshot remains intact. Runtime calculation and write failures are covered through deterministic automated fault injection. Do not corrupt or overwrite a user's real plan or snapshot.
6. Save As to another disposable filename; verify independent UUID and historical-copy notice, original completion time and untouched source result. Switch between plans and check correct household/results.
7. Test an unsaved plan: calculation remains available, but the message must describe memory-only results and saving the plan first; no autosave occurs.
8. At native Windows scaling settings and with Narrator/high contrast, verify status/help/Retry Save, keyboard access, scrolling and existing tables. These OS configurations have not been manually certified here.

Only last-success snapshots are retained. Post-completion selection changes are session UI state. Unsupported/corrupt historical files remain preserved and unavailable; explicit recovery policy remains deferred. Memory-only completion followed by the first plan save and concurrent Save As behavior remain subject to the established 9B/9C contracts. No automatic historical browser, eviction, new PDF export or Quick/Exhaustive/individual remaining-mode persistence was introduced.

## Final regression, preview inspection and Git verification

### Complete Maven regression

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-DargLine=-Xmx8g' test
```

**BUILD SUCCESS: 3,042 tests, 0 failures, 0 errors, 38 skipped (3,004 passed).** Runtime 9 minutes 51 seconds; finished October 20, 2026 at 09:34:59 America/New_York. Evidence: `target/9d1-full.log`. The skips are the suite's opt-in preview/benchmark conditions, including the new opt-in SS preview; no failing test was disabled. The full run includes all ten new SS tests, both frozen compatibility proofs, trailing-document validation, weighted persistence/numeric hotfixes, deterministic/seeded Monte Carlo and financial regressions, controller/persistence/lifecycle and wizard-equivalence tests.

The only later test-code adjustment synchronized preview capture with an additional existing JavaFX pulse barrier. That preview is disabled in a normal full run and was executed explicitly afterward:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-DargLine=-Xmx8g' '-Dtest=CoupleSocialSecurityPersistencePreviewTest,LegacyWeightedSnapshotCompatibilityTest' '-Dcouple.ss.persistence.preview=true' test
```

**BUILD SUCCESS: 3 tests, 0 failures, 0 errors, 0 skipped**, 15.536 seconds. Evidence: `target/9d1-preview-final.log`. Both unchanged frozen fixtures passed again.

### Actual rendered inspection

Inspected the final JavaFX images:

- `target/phase9c-restoration-preview/9d1-ss-current-1366.png`
- `target/phase9c-restoration-preview/9d1-ss-current-900.png`
- `target/phase9c-restoration-preview/9d1-ss-outdated-1366.png`
- `target/phase9c-restoration-preview/9d1-ss-outdated-900.png`
- `target/phase9c-restoration-preview/9d1-ss-outdated-status-900.png`

Requested window budgets: 1366×768 and 900×650 logical coordinates. The rendered content areas are reduced by normal window decorations. Wide navigation/sidebar and compact selector remain usable; the Current/Outdated notice, original timestamp, Saved status, existing result tabs, recommendation and fixed Close footer are readable. The compact Outdated warning fits without essential clipping. The scroller is intentionally positioned at the result-status section for these captures; next-run controls remain above it. The recaptured Outdated images correctly display the updated warning, not the preceding cached frame. The initial capture issue was test synchronization only; no production layout change was necessary.

These are actual rendered JavaFX checks at logical-size budgets. Native display-scaling changes, Narrator and high-contrast behavior were not tested; the manual checklist remains outstanding.

### Final Git state

Branch `main`; HEAD remains `1ff71f3b71fca97af49a7d2058aa14284038dd9e`. `git diff --check` passed. The staged diff is empty. The final working tree contains **8 modified tracked files and 11 untracked files** (including the pre-existing, unchanged Phase 9D audit):

```text
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AcceptedAnalysisCompletion.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisSnapshot.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/WeightedSnapshots.java
 M src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedResultPersistence.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisSnapshotCodecTest.java
?? PHASE-9D-1-COUPLE-SS-PERSISTENCE.md
?? PHASE-9D-REMAINING-ANALYZERS-AUDIT.md
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisPayload.java
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/CoupleSocialSecuritySnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/CoupleSocialSecuritySnapshotMapper.java
?? src/test/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotFaults.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/CoupleSocialSecurityPersistencePreviewTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/CoupleSocialSecurityPersistenceTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/LegacyWeightedSnapshotCompatibilityTest.java
?? src/test/resources/analysis-snapshots/weighted-couple-v1.json.gz
?? src/test/resources/analysis-snapshots/weighted-individual-v1.json.gz
```

Tracked diff: 8 files, with the complete new-file inventory explicitly included above because `git diff --stat` omits untracked files. Protected-domain/resource/dependency and financial-golden paths have no tracked changes. Generated logs/images are ignored under `target/`; no local repository or generated artifact is staged. Frozen gzip hashes remain exactly those recorded at Gate 1.

Nothing was staged, committed, pushed, reset or discarded. **Stop for review after Phase 9D-1. Phase 9D-2a, Quick persistence and retained-projection codecs have not been started.**
