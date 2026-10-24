# Phase 10B - Analysis parameter schema and safe transactions

## 1. Scope and baseline

Implemented infrastructure only: typed accepted configurations, plan JSON compatibility, configuration-only transactions and an application-owned asynchronous coordinator. No analyzer completion callback, input initialization/rollback, snapshot migration, financial algorithm or result restoration adapter was changed. Phase 10C/10D are not started.

Initial branch `main`, HEAD `2e959d9770bd834cd4ca1bed3f50df1219607c65`. The only pre-existing working-tree file was untracked `PHASE-10A-ANALYSIS-PARAMETER-PERSISTENCE-AUDIT.md`; it was preserved without modification. This report distinguishes the new infrastructure tests from end-to-end analyzer UI behavior deferred to later phases.

## 2. Implemented JSON schema

`RetirementPlan.analyzerConfigurations` is optional. Absent legacy sections remain absent, so serializing an unchanged old plan does not invent configurations. Explicit null is rejected rather than treated as an empty accepted history. The container has `schemaVersion: 1`; each known entry has `configurationVersion: 1`, an existing `AnalysisKind`, matching household shape, accepted run UUID/time, typed parameters and bounded provenance.

Example accepted individual deterministic entry:

```json
{
  "analyzerConfigurations": {
    "schemaVersion": 1,
    "configurations": {
      "INDIVIDUAL_DETERMINISTIC": {
        "configurationVersion": 1,
        "kind": "INDIVIDUAL_DETERMINISTIC",
        "shape": "SINGLE",
        "acceptedRunId": "00000000-0000-0000-0000-000000000001",
        "acceptedAt": "2026-10-23T12:00:00Z",
        "parameters": {
          "profile": "standard-v1",
          "rankingMeasure": "AFTER_TAX_ESTATE",
          "retainedDetails": 9,
          "primaryDeathYear": null
        },
        "provenance": {}
      }
    }
  }
}
```

Profiles describe frozen effective service settings; they do not introduce editable controls or change defaults. Future integrations must capture the actual service profile and request values, not the illustrative example or current control text.

`AnalysisParameterConfigurations` has an explicit streaming reader and immutable raw JSON representation. Known kind/version pairs are decoded through a fixed switch to allowlisted parameter records. There is no Jackson class-name polymorphism. Missing required creator properties, null primitives, coercion, duplicate fields, trailing JSON, incorrect families/shapes and malformed metadata are rejected. Numeric parsing preserves BigDecimal scale; dates/timestamps are ISO values in the configuration section.

### Bounds

- Container: 65,536 UTF-8 bytes, 32 entries, 10,000 JSON tokens, nesting depth 12.
- Strings: 2,048 characters; field names: 128; kind keys: 96 and restricted uppercase identifier syntax.
- Numeric token: 512 characters; known decimal precision and absolute scale at most 128.
- Provenance: at most 16 entries, key length 64 and value length 512.
- Optional baseline original text/source: 256 characters each. Profiles: 128 characters.

These are new **small configuration metadata** bounds, not changes to any snapshot budget. Existing snapshot limits, codecs and representations remain unchanged.

Unknown future identities and future per-entry versions are retained opaquely within those bounds. Unknown future containers are retained but not executed. Updating an unsupported container or target entry is refused; other known entries can be updated without dropping bounded future entries or container metadata. Arbitrary unknown fields inside a known version's typed entry are rejected. A blocked merge retains the newly accepted values in coordinator memory, exposes Save Failed and leaves the protected plan section intact.

Financial-only state validation explicitly excludes this recognized metadata root. A future configuration may legitimately use a field name such as `stateTaxAssumptions` without that field being interpreted as the plan's financial state configuration. A dedicated repository regression verifies preservation of such metadata, including null and decimal scale. Actual financial state validation remains unchanged.

## 3. Typed parameter families and identities

`AnalysisParameterConfiguration` provides four sealed parameter families:

| Family | Existing identities | Preserved settings |
|---|---|---|
| MonteCarlo | All eight couple/individual, standalone/paired, fixed/longevity kinds | Trial count, exact seed, return/volatility rates, deterministic or stochastic inflation settings, relevant mortality factors and separate current/baseline survivor elections |
| Mortality | Couple SS, Couple Weighted, Individual SS, Individual Weighted | Independent factors as applicable, conditioning/valuation dates, exact real discount rate, optimizer profile and optional couple weighted baseline |
| Quick | COUPLE_QUICK | Candidate count and current SS prerequisite fingerprint provenance; this does not implement Quick result persistence |
| Search | COUPLE_EXHAUSTIVE, INDIVIDUAL_DETERMINISTIC | Search profile, ranking measure, retained detail configuration and optional individual primary death year |

All **15** existing `AnalysisKind` identities are supported and validated. Single-person families cannot acquire spouse controls; fixed MC cannot carry longevity overrides; standalone MC cannot acquire a paired-baseline election. Inactive configurations for other household shapes can remain in the plan container. New automatic writes conservatively require the captured configuration shape to match the durable plan; an unsaved household-shape change therefore requires an ordinary Save before that automatic write can succeed.

Optional weighted baseline elections have explicit ABSENT, NULL, VALUE and UNAVAILABLE states. Unavailable original text/source is preserved without parsing it as a valid election. Only VALUE may carry a numeric age. This implements the approved optional-text policy without blocking financial searches or changing baseline fingerprint semantics.

No plan copies, accounts, simulation histories, mortality tables, projection results or PDF bytes are allowed by the typed families. Quick prerequisite provenance is not treated as sufficient to reconstruct a ranking or bypass existing readiness rules. Later UI integrations must use the accepted request as their source and retain existing result/context mechanisms for financial provenance.

## 4. Configuration-only atomic transaction

`JsonRetirementPlanRepository.saveParameters` operates on the **durable file**, not the live financial plan. It requires an already opened/saved path, valid durable UUID, expected document digest, matching shape and latest registered accepted run for that slot. Never-saved and legacy-without-UUID configurations remain Memory Only until explicit ordinary Save/Save As.

Writes share `AtomicFileWriter.SAVE_LOCK` and a persistent sibling `.rp-lock` cooperative file lock with ordinary Save/Save As. Unsupported files, symlinks, competing locks and external digest changes refuse replacement. The lock sidecar intentionally remains in place to avoid replacing a lock inode while another process uses it.

`PlanConfigurationTransaction.replace` copies the original JSON token stream and replaces only the root configuration section with a validated merge of the targeted entry. Every unrelated number is emitted using its original textual numeric token, preserving exponent forms and trailing zeros. Other JSON token values and order are preserved; whitespace and equivalent string escaping may be reformatted. This is not a generic financial readTree/writeTree rewrite. Other configurations and bounded unknown metadata are preserved.

Existing atomic writer behavior remains: sibling temporary file, forced bytes, validation, recognized backup retention, atomic replacement and rollback on failure; no non-atomic fallback. An optional commit guard rechecks the expected durable hash and latest accepted run after verification and immediately before replacement. The old writer overload remains unchanged for other repositories/snapshots. Checks after fault injection also detect changes at the final replacement boundary.

The cooperative lock coordinates participating processes. Hash checks detect nonparticipating external changes up to the final guard; no portable filesystem operation can guarantee compare-and-swap against an arbitrary editor writing in the tiny guard-to-rename interval. This is the approved cooperative-lock/conflict-detection contract, not an absolute external-writer guarantee.

The existing explicit recovery behavior is preserved: ordinary Save may recreate a missing primary file. Automatic configuration updates never recreate a missing file or silently adopt a new durable binding. UUID publication still occurs only after successful verified ordinary save.

## 5. Fingerprint isolation and historical compatibility

`AnalysisFingerprintService` and `MonteCarloFingerprintService` explicitly recognize and remove only `analyzerConfigurations` from financial root extraction. General unclassified-root rejection remains intact. MC V1 stored digests and V2 temporary-numeric compatibility are unchanged; plan/baseline decimals remain scale-sensitive.

`MonteCarloPlanDocument.capture` omits configuration metadata with exact numeric-token copying before freezing financial request documents. Without configurations it retains the existing serialization path. Historical documents/hashes/digests are not rewritten. `RetirementPlanScenarioCopyService` required no code change: its existing JSON round trip preserves the additive metadata, and financial fingerprint extraction excludes it.

New tests compare old digests before/after adding configurations for seven SS identities and all eight MC identities under both MC contracts. The full compatibility suite covers frozen weighted bytes, Couple SS, Exhaustive interning, MC snapshots and repository integrity.

## 6. Application ownership, ordering and durability

`ApplicationController` owns an `AnalysisParameterCoordinator`; `getAnalysisParameters()` exposes the infrastructure for future accepted-completion integrations. New/Open/Save/Save As refresh the captured binding. `AcceptedAnalysisParameters` carries an immutable configuration, binding and positive acceptance sequence. Reserve sequence via `nextAcceptanceSequence()` at run capture; submit only after the existing final job success/eligibility gate. No callback is wired in this phase.

Bindings capture plan object, UUID, normalized path and epoch. Older sequences and obsolete bindings are rejected. Accepted writes retain their original destination across plan switching and Save As; they are never redirected to the active file. The coordinator keeps last accepted values in memory even when disk or metadata merging fails.

One lazily started worker uses a 32-item bounded queue. Superseded work skips publication; repository registration and commit guards prevent older queued events from replacing newer committed state. Saturation exposes Save Failed while retaining the latest immutable accepted event. Retry submits that same event without financial work.

States are PENDING, SAVED, SAVE_FAILED and MEMORY_ONLY. Original exceptions are retained for explicit diagnostics, with generic user-facing messages; parser exception contents or raw plan data are not automatically logged. `lastAccepted`, `state` and `failure` expose the infrastructure state. No analysis/projection/simulation service is a dependency of the coordinator.

Normal Save includes accepted in-memory metadata; its successful completion marks only the entries actually verified in the committed document, not a newer acceptance that happened during the save. Save As retains independent durable plan UUIDs and copied immutable configuration values. Existing result snapshot cloning is unchanged and remains separately recoverable. Automatic writes do not call controller Save, clear `modified`, increment financial revision or invalidate projections.

## 7. Failure, retry and shutdown

Configuration save failure retains live accepted inputs, previous durable plan/backup, dirty financial edits and historical result snapshots. Retry does not capture edited controls or recalculate. Unsupported future metadata is never overwritten just to retry; explicit schema resolution may be necessary. A real filename and ordinary UUID migration remain prerequisites for automatic persistence.

Coordinator drain counts queued/active I/O as well as Pending states, so a manual Save cannot make shutdown falsely assume queued work is gone. `beginExit` freezes new handoffs; canceled exit resumes acceptance. MainWindow's existing background result-drain flow also drains parameter writes and reports failures before exit, with retry. Successful exit shuts down the parameter executor without canceling queued work. This is necessary application-lifecycle plumbing, not analyzer UI integration. No new progress/result navigation was added.

Rollback of edited analyzer controls, validation-error behavior, asynchronous configuration application to controls and acceptance observers are deliberately deferred to 10C/10D. No automatic historical snapshot import occurs on plan opening.

## 8. Automated evidence

Commands use IntelliJ bundled Maven and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`, with `-DargLine=-Xmx4g`.

Focused tests include configuration typing/limits, exact decimals and optional states, all 15 persistence slots, unknown future data, external conflicts, cooperative locks, every atomic failure stage, backup recovery, original numeric lexemes, dirty-flag isolation, stale queues, saturation, Memory Only/legacy migration, Retry, Save As, restart and shutdown.

Final-revision infrastructure/compatibility focused run: **210 passed, zero failures/errors/skips**, Maven BUILD SUCCESS, 34.309 seconds (`target/phase10b-verified-focused.log`). It includes frozen weighted compatibility, Couple SS persistence, Exhaustive restoration/PDF, snapshot repository, all new schema/transaction/coordinator/controller tests and both fingerprint families. The earlier 209-test expanded run also passed before the final metadata-name isolation case was added.

An initial full run reported **3,331 total, 3,286 passed, zero failures, one error, 44 skipped**, 9:39. The error was an existing historical-PDF test synchronization race: durable snapshot commit can precede the separate presentation-metadata task. The test waited for "Results saved" and attempted capture before metadata made the result exportable. A second existing-state wait for "Current Results" fixes that test precondition; every financial/PDF assertion remains unchanged, and no PDF production code changed. The corrected four-test PDF class passed in 18.152 seconds (`target/phase10b-pdf-readiness.log`).

Intermediate complete-suite rerun: **3,335 total, 3,291 passed, zero failures/errors, 44 skipped**, 9:41 (`target/phase10b-final-full.log`). The final repository metadata-name isolation correction was made during that run and then covered by the 210-test focused run; a further complete-suite run is used for final-revision evidence rather than claiming that intermediate compilation includes the correction.

**Final source revision: 3,336 total; 3,292 passed; zero failures; zero errors; 44 skipped. BUILD SUCCESS. Total Maven time 8:58, completed October 23, 2026 at 08:29:45 EDT.** Log: `target/phase10b-verified-full.log`.

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-DargLine=-Xmx4g' test
```

No test exclusions, new disabled tests, seed/trial-count changes or golden regeneration. The 44 existing skips are opt-in/environment/reference-data/preview conditions, not passed verification. No native parameter-control acceptance is claimed: those producer and control integrations are deferred. Existing full-suite financial, lifecycle, PDF and snapshot compatibility tests pass on the final revision.

The initial expanded infrastructure run passed 166 tests before final lifecycle hardening. Early development runs exposed a date-format mismatch in the new configuration encoder and an overly strict missing-primary check; both were corrected without weakening existing assertions or financial behavior. Development failures are not hidden or counted as successful runs.

## 9. Exact Phase 10B inventory

Production additions:

- `src/main/java/com/daviddunn/retirementplanner/app/analysis/parameters/AnalysisParameterConfiguration.java`
- `src/main/java/com/daviddunn/retirementplanner/app/analysis/parameters/AnalysisParameterConfigurations.java`
- `src/main/java/com/daviddunn/retirementplanner/app/analysis/parameters/AcceptedAnalysisParameters.java`
- `src/main/java/com/daviddunn/retirementplanner/app/analysis/parameters/AnalysisParameterCoordinator.java`
- `src/main/java/com/daviddunn/retirementplanner/persistence/PlanConfigurationTransaction.java`

Production modifications:

- `src/main/java/com/daviddunn/retirementplanner/domain/model/RetirementPlan.java`
- `src/main/java/com/daviddunn/retirementplanner/persistence/JsonRetirementPlanRepository.java`
- `src/main/java/com/daviddunn/retirementplanner/persistence/AtomicFileWriter.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisFingerprintService.java`
- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloFingerprintService.java`
- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPlanDocument.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java`

Test additions:

- `src/test/java/com/daviddunn/retirementplanner/app/analysis/parameters/ParameterFixtures.java`
- `src/test/java/com/daviddunn/retirementplanner/app/analysis/parameters/AnalysisParameterConfigurationsTest.java`
- `src/test/java/com/daviddunn/retirementplanner/app/analysis/parameters/AnalysisParameterCoordinatorTest.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/PlanConfigurationTransactionTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/controller/AnalysisParameterControllerTest.java`

Test modifications:

- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisFingerprintTest.java`
- `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloNumericCompatibilityTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveHistoricalPdfFidelityTest.java` (asynchronous readiness synchronization only)

New report: this file. Total Phase 10B inventory: **13 production files, eight test files, one report**. Pre-existing untracked Phase 10A report is separate. Build outputs/logs remain under ignored `target/`.

## 10. Limitations and next review gate

No user-visible analyzer parameter autosave/initialization is advertised yet: the producer callbacks and control mapping are Phase 10C/10D. The typed contract and coordinator are ready for that integration after review and final regression evidence below.

Whole financial-plan manual serialization remains the existing explicit Save behavior. Configuration-only saves reformat JSON whitespace but preserve financial token values and original numerical lexemes. Independent external editors must honor cooperative locks for a complete cross-process exclusion guarantee. Future-version/oversized metadata is retained and blocks unsafe replacement rather than being silently dropped.

No schema migration is automatically run on Open; no historical snapshot is imported. Legacy plans need ordinary migration Save. Native analyzer control-restoration and canceled/fatal-run rollback acceptance belongs to later phases, not this infrastructure phase. No native Windows accessibility/DPI certification is claimed.

No financial engines, seeded goldens, snapshot limits, snapshot payload representations, PDFs, dependencies or analyzer request semantics were modified. No files were staged, committed, pushed, reset or discarded. Stop after Phase 10B for review.

## 11. Final Git verification and recommendation

Branch and HEAD remain `main` / `2e959d9770bd834cd4ca1bed3f50df1219607c65`. Final inventory: **11 modified tracked files and 12 untracked files**; one untracked file is the preserved pre-existing Phase 10A report. The other 22 files are exactly the Phase 10B inventory in section 9. The index is unchanged and empty of staged modifications.

`git diff --check` passes. Untracked files were separately checked for trailing whitespace because ordinary diff does not include them. `git status --short`, `git diff --stat` and `git ls-files --others --exclude-standard` were inspected. All nonignored additions are expected Java sources/tests or reports. No generated plan JSON, PDFs, snapshot caches, logs, secrets or temporary files appear in the release inventory. Maven logs and generated evidence are ignored under `target/`. No resource, financial golden, dependency or build file changed.

Recommendation: **Phase 10B is ready for review and subsequent Phase 10C authorization.** Do not advertise automatic analyzer parameter persistence to users until the later accepted-completion/control integrations and their native acceptance pass. No Phase 10C or 10D work was started.
