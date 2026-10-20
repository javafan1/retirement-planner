# Gate 3B-2 — Production lossless Exhaustive interning

## Outcome and scope

Implemented an explicitly versioned, typed, deterministic storage representation for `COUPLE_EXHAUSTIVE`. A fresh actual production search calculated all **5,184 candidates successfully**, retaining the normal twenty detailed projections. Its snapshot decreased from **20,479,403 raw / 837,669 gzip bytes** to **2,903,992 raw / 133,155 gzip bytes**. No financial data was removed or recomputed during restoration.

All physical document limits remain unchanged. The actual native **44,692,375-byte failure remains unresolved pending native acceptance**: neither its complete graph nor its component breakdown was available. The measured fixture is not a claim about that file or every possible retirement plan. Gates 4 and 5 acceptance activities have not been undertaken; running the regression suite here does not constitute those separate acceptance gates.

## Preparation and changed-file inventory

Read AGENTS.md and the Gates 1, 2, 3, 3A, 3B and 3B-1 findings against the current source. Initial branch `main`, HEAD `4fddd4ee1eaebc8354b009b77a381459495d665c`; five modified tracked files, eighteen untracked files, empty index. Preserved all pre-existing work.

Files changed in this gate:

| File | Change |
|---|---|
| `persistence/ExhaustiveSnapshotInterning.java` (new) | Explicit v2 wire transform, typed tables, exact deduplication, validation and expansion-work bounds |
| `persistence/AnalysisSnapshotCodec.java` | Exhaustive-only wire dispatch, exact tree handling, bounded shared-node traversal, streamed logical digest, safe numeric path names |
| `persistence/AnalysisSnapshotSizeDiagnostics.java` (pre-existing untracked) | Recognize contextual document paths and intern-table byte categories; avoid treating reference objects as account/year arrays |
| `domain/projection/ProductionExhaustiveInterningTest.java` (new test) | Original-versus-restored graph/identity/state/account tests, legacy Exhaustive read, corruption/trailing JSON, decimal boundaries |
| `persistence/ExhaustiveSnapshotInterningTest.java` (new test) | Determinism, scale distinctions, malformed tables/references and amplification limits |
| `domain/projection/RealisticExhaustiveCapacityProofTest.java` (existing measurement test) | Measure real production codec, reference counts/fan-out, independent repository restart and restored graph checks |
| `domain/projection/ExhaustiveCapacityInvestigationTest.java` (existing investigation test) | Preserve legacy-size comparison; assert optimized fidelity; inject real verification failure for retry/previous-file safeguards |
| `domain/projection/ExhaustiveInterningAuditTest.java` (existing audit test) | Explicitly serialize the historical unoptimized comparison baseline, rather than pass the now-optimized production wire into the old experiment |
| This report | Implementation, evidence and native checklist |

Production paths are under `src/main/java/com/daviddunn/retirementplanner/`; test paths under `src/test/java/com/daviddunn/retirementplanner/`. No financial engines, DTO financial semantics, analyzer UI, job admission, PDF contents, dependencies or goldens changed in this gate.

## Representation and exactness

The envelope remains schema 1 and the existing `ExhaustiveSnapshot` immutable calculation DTO remains unchanged. Only its physical `payload` representation changes for new Exhaustive writes:

```text
payload:
  exhaustiveRepresentation: 2
  tables:
    metrics: [exact fourteen-metric objects, including difference records]
    accounts: [exact ordered account-definition arrays]
    years: [exact ordered annual-record arrays]
  document:
    original Exhaustive payload fields
    only the allowlisted metric/difference/accounts/years slots become { "$ref": index }
```

Each slot selects its table by fixed code, never by an arbitrary class/type name in the document. Baseline and all detailed projections retain their own strategy, warnings, advanced-path flag and metrics. Candidates, original generation/ranking order, ties, failures, selection, frozen assumptions and SS reference stay in the document. No candidate universe is truncated.

Values are interned only when their exact Jackson JSON bytes match, with SHA-256 lookup and byte comparison on reuse. Table order is first encounter through baseline then original entry order; table field order is fixed. Maps are serialized using the existing sorted-map configuration. Hash-map iteration and JVM identity never assign persisted indices. Different BigDecimal scales remain different values, including scientific-notation scale. No approximate financial comparison is used.

Only immutable storage nodes share values. The decoder traverses their logical occurrences into the existing allowlisted DTO constructors. Each projection subsequently constructs its own detached account registry through Gate 1 reconstruction. Two equal-looking accounts in one registry stay two identities; equal projections do not share mutable accounts with each other or the active plan. There is no name-based account matching.

## Compatibility and integrity

- Legacy Exhaustive payloads without the v2 marker still decode through their original DTO; unknown/malformed markers are refused.
- Weighted and Couple SS writes retain their original representation, schemas, field order and digest interpretation. Both frozen weighted byte fixtures pass unchanged.
- Integrity remains the SHA-256 of the original logical `Content(payload, context)`, not a rounded or reformatted financial model. Digest generation now streams the same Jackson bytes into `DigestOutputStream`, avoiding allocation of an expanded logical JSON byte array. Frozen digest tests verify the bytes retain their prior meaning.
- The v2 document is validated, expanded as shared immutable nodes, mapped to the exact original DTO and checked against that logical digest. Corruption cannot be accepted by merely changing a pool/reference.
- Envelope UUID, kind/shape, fingerprint, algorithm/reference provenance, timestamp, file-binding and repository compatibility policies remain in force.
- Old application builds cannot read v2 Exhaustive payloads; they must not be used to rewrite those results. This is a forward representation change with backward reads, not a change to weighted/SS financial compatibility versions.

## Decoder and allocation safety

Unchanged physical guards: **33,554,432 raw bytes; 16,777,216 gzip bytes; 4,096 numeric characters; 2,000,000 JSON tokens; 100,000 array containers; depth 80; string length 256,000**. The previous scanner's array-container counter is preserved; the new interning validator additionally bounds individual reconstructed array lengths to 100,000.

New independent logical guards:

- At most 100,000 total table values.
- At most 100,000 references.
- At most 8,000,000 expanded logical tokens and 128 Mi characters of aggregate expanded field-name/scalar text.
- Depth 80 and collection-length 100,000 enforced over logical occurrences before DTO/domain allocation.

These are finite reconstruction-work limits, not increases to the physical raw-document or numeric limits. They permit bounded repeated immutable content that previously required repeated bytes. They do not guarantee every graph fits a small Java heap.

Wrapper/table fields must exactly match the supported schema. Implicit contiguous array indices avoid a separate duplicate-ID namespace. Reject unsupported versions, missing tables/slots, wrong table shapes, noninteger/negative/out-of-range indices, unknown reference properties, duplicate exact table values, unused values and nested references. Pools contain plain values: no references within tables, external references or cycles are supported. Unknown financial fields remain subject to the existing strict DTO reader. Root trailing tokens and duplicate JSON keys are still rejected.

Reference replacement attaches existing immutable tree nodes; it does not copy entire annual graphs for each use. A bounded logical walk runs before constructing the DTO graph. The decoder traverses the tree directly and does not generate a full expanded legacy JSON byte document. Exact decimal parsing is used for the intermediate tree; normal family readers remain unchanged. Encoding validates its own output using the same interning rules.

## Lifecycle integration

All Gate 3 writes already flow through `AnalysisSnapshotCodec` in `JsonAnalysisSnapshotRepository`; therefore accepted completions, retry and clone saves automatically use v2 without a second persistence mechanism. Read dispatch handles both representations through the same asynchronous restoration path.

No job eligibility or accepted-completion gate changed. No task is launched by decoding. The application-owned bounded writer still captures the original plan slot, performs temporary-file verification and atomic replacement, preserves the prior durable primary/backup on failure, and supports Save Failed / Retry Save without recalculation. Historical result retention, Current/Outdated transitions, plan-switch/load-generation guards, Memory Only and Save As identity/clone behavior continue through existing Gate 3 code.

The updated capacity failure test injects an actual written-file verification failure now that the formerly oversized fixture fits v2. It still asserts two failed attempts retain the accepted in-memory object and exact prior durable bytes. It does not remove the physical size-limit tests or pretend compression makes an oversized physical document acceptable.

Diagnostics remain financial-value-free. V2 paths identify document fields and intern-table contributions. Table byte counts describe stored shared values, not a sum of every expanded logical occurrence; use the explicit measurement test for expanded dimensions. Reference objects are not scanned as arrays (which would otherwise make the old diagnostic counting loop invalid).

## Capacity and performance evidence

Reused the approved realistic synthetic household configuration: forty-year horizon, six primary/spouse/joint accounts, both SS participants, pension, spending/investment growth, actual federal/Michigan tax calculations, Medicare IRMAA, RMDs, Roth conversions and a configured death/survivor scenario. The new search again ran the real engine independently for every candidate; no output was copied across unrelated candidates. Normal top-twenty detail retention was preserved.

Fresh search: **32.237 seconds, 5,184 successful, zero failed**, 81 distinct exact fourteen-metric records and 81 distinct exact differences. Baseline plus twenty details: 840 annual rows, maximum forty years/six accounts; eighteen baseline years with Part D IRMAA. The SS cross-reference in this real fixture is explicitly unavailable. Populated cross-reference fidelity is covered separately by the smaller complete result tests.

| Measurement | Legacy logical representation | Production v2 |
|---|---:|---:|
| Raw JSON bytes | 20,479,403 | 2,903,992 |
| Gzip bytes | 837,669 | 133,155 |
| Maximum financial numeric-token length | 174 | Same exact numbers |
| Table values | Not applicable | 162 metrics/differences, 1 accounts, 2 annual graphs |
| References / maximum one-value fan-out | Not applicable | 10,410 / 64 |

Production v2 reduces raw bytes approximately 85.82%. The prior experiment's generic per-reference type labels are unnecessary in production because slot types are fixed; this explains the additional reduction versus its approximately 3.23 MB result.

Initial production codec measurement: encode **368.407 ms**, decode **285.228 ms**. After the final decoder avoided a second parse of the physical tree, cache-based verification measured encode **346.606 ms**, decode **151.055 ms**, followed by **5.955 ms** pure result reconstruction. A new repository instance also loaded the saved full result exactly. Measurements are single-process observations, not statistical promises; validation, cache warmth and GC affect them.

Temurin OpenJDK 25.0.4.1+1-LTS, Windows, measurement heap `-Xmx4g`. Sampled used-heap peak during the fresh search plus all comparison experiments: **517,454,352 bytes**; cache-based measurement peak **323,687,744 bytes**. Sampling interval 100 ms; these figures are neither RSS nor isolated incremental codec memory, and can miss spikes. CPU model could not be enumerated via the sandbox CIM interface in the preceding investigation. No fabricated hardware measurement is supplied.

The precision-stress fixture still serializes to **45,334,478 legacy raw bytes**, beyond the native total. It now passes exact production-codec round-trip below the unchanged limits. This fixture deliberately has artificial repeated outcomes and establishes precision/capacity safety only, not a forecast of real reduction. Its original artificiality remains tested and documented. No claim that the live 44.69 MB result has the same repetition follows from it.

Evidence: ignored `target/9d2-gate3b2-fidelity.log`, `target/9d2-gate3b2-regression-final.log`, `target/9d2-gate3b2-final-capacity.log`. The existing-format synthetic calculation artifact remains under ignored `target/gate3b1-full-calculated.json.gz`; later measurements explicitly reuse it without another search.

## Fidelity and focused verification

- Initial codec/legacy/SS check: **34 tests passed**.
- Fresh full-universe calculation, retained-projection fidelity, optimized security, Exhaustive lifecycle and frozen weighted fixtures: **57 tests passed**.
- Expanded repository, security, state/identity, transactional-write, weighted restoration/hotfix, Couple SS and Exhaustive lifecycle checks: **122 tests passed**, zero failures/errors/skips; 43.700 seconds.
- Final decimal, deterministic/malformed/expansion checks, cached full snapshot/repository reconstruction and frozen weighted compatibility: **36 tests passed**, zero failures/errors/skips; 17.775 seconds. Counts overlap; they are separate commands, not distinct totals.

Tests compare every candidate's metrics, differences, order/rank/failure and full immutable payload; Gate 1's independent field/identity oracle compares original and restored baseline/detail domain graphs. Optimized tests cover every actual state jurisdiction, all supported account variants including inherited accounts, multiple owners, equal-looking distinct accounts, detached independent projection registries, long decimal tokens at 1,024/1,070/4,096 and rejection at 4,097, scale-sensitive pool distinctions, populated historical SS context, legacy payload reads, trailing JSON and digest corruption. Existing weighted/Couple SS, cancellation/obsolete callbacks, asynchronous plan switching, Save As, retry/backup and controller persistence tests remain enabled.

An initial new all-account fixture omitted required opening-RMD facts and was rejected by the unchanged engine. The fixture was corrected to supply supported opening history and participant facts using the existing fidelity-test pattern; no domain validation or financial assertion was changed. An early full-suite attempt was interrupted to update the historical test-only experiment's input from production wire to its explicit legacy comparison representation, then restarted. No failing test was skipped or disabled.

Full Maven suite: **BUILD SUCCESS — 3,134 tests, zero failures, zero errors, 39 skipped (3,095 passed)**. Runtime **8 minutes 35 seconds**, finished 2026-10-20 16:19:30 America/New_York. Evidence: `target/9d2-gate3b2-full-final.log`. Skips are existing opt-in preview/benchmark conditions plus the opt-in full-search measurement, which was executed separately above; no failing test was disabled. The run includes deterministic/seeded Monte Carlo, financial golden, tax/RMD/Roth, identity/transactional persistence, all analyzer persistence and wizard-equivalence regressions.

Commands used IntelliJ bundled Maven, always with `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`. Focused measurements used `-DargLine=-Xmx4g`; the full run used the established `-DargLine=-Xmx8g` and `test` without test exclusions. Full-universe measurement was enabled using `-Dretirementplanner.exhaustiveCapacityProof=true`; final measurements additionally used `-Dretirementplanner.reuseCapacitySnapshot=true` after the fresh search had completed.

## Native acceptance checklist

1. Save the retirement plan normally and retain any existing durable result. An accepted memory-only result in the currently running process is not guaranteed to survive exit; this implementation does not hot-patch that JVM.
2. In IntelliJ, Build Project and restart the JavaFX application through its normal Run configuration. No HotSwap, debugger attachment or live instrumentation is required or attempted.
3. Open the saved couple plan and run Deterministic Exhaustive Search normally. Confirm complete candidate count, unchanged rankings/details/heat map, and Pending becoming Saved. If Save Failed appears, retain the process/result and collect the privacy-safe stage/size/limit diagnostic; Retry Save must not launch financial analysis.
4. Close the application normally after durable Saved status, restart, reopen that same plan and select Exhaustive. Confirm original completion timestamp, baseline, ranking universe/order, all heat-map metrics/selection and retained details restore without progress from a new financial job.
5. Change a relevant assumption. Confirm Previous Results — Outdated remains usable with frozen assumptions. Restore the original assumption and verify Current when effective fingerprints match.
6. Exercise Save As and reopen original and destination independently; confirm clone provenance and no cross-plan contamination.
7. Export the restored result using the existing PDF action. Retain the PDF for separately authorized Gate 4 historical financial/assumption/color/layout verification. This gate neither redesigns nor certifies that PDF.

Record actual raw/gzip counts and native successful restart restoration before declaring the original 44,692,375-byte case resolved. Native Windows DPI/Narrator/high-contrast checks were not performed or certified.

## Remaining risks and stopping boundary

One realistic plan is not a worst-case universe. Plans with more unique outcomes, accounts, long decimal precision or horizons may still hit physical or logical limits; a safe refusal must preserve the previous durable result. Shared JSON nodes reduce stored duplication but bounded independent DTO/domain reconstruction still consumes memory. No limit increase or omission is used to make these tests pass.

Legacy Exhaustive reading is exercised against the previous explicit wire serialization and preserved calculation cache; unlike weighted compatibility, no new checked-in frozen Exhaustive resource was fabricated. Exact frozen weighted files were not regenerated. Financial algorithm/reference versions were not changed for this storage-only representation.

No Quick/remaining-individual persistence, historical browser, PDF redesign, financial algorithm, reference-table, default or dependency change. Stop after this implementation for review; no Gate 4/5 acceptance claim.

## Git verification

`git diff --check` passed (existing LF/CRLF notices only). Final state: five modified tracked files and twenty-two untracked files, including all preserved prior-gate changes. Index is empty. No golden resource, build configuration or financial engine changed; the domain attribution change visible in the full working-tree diff predates this gate. Nothing staged, committed, pushed, reset or discarded. Generated logs and calculation artifacts are ignored under `target/`.

Current complete working-tree inventory (five modified tracked, twenty-two untracked; includes preserved prior gates):

```text
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java
 M src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java
 M src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedResultPersistence.java
?? PHASE-9D-2-EXHAUSTIVE-PROJECTION-CODEC.md
?? PHASE-9D-2-GATE-2-EXHAUSTIVE-SNAPSHOT.md
?? PHASE-9D-2-GATE-3-EXHAUSTIVE-INTEGRATION.md
?? PHASE-9D-2-GATE-3A-CAPACITY-INVESTIGATION.md
?? PHASE-9D-2-GATE-3B-LOSSLESS-INTERING.md
?? PHASE-9D-2-GATE-3B1-REALISTIC-CAPACITY.md
?? PHASE-9D-2-GATE-3B2-PRODUCTION-INTERNING.md
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ExhaustiveSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotSizeDiagnostics.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/ExhaustiveSnapshotInterning.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/RetainedProjectionCodec.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveResultSnapshots.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveCapacityInvestigationTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveInterningAuditTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveSnapshotTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ProductionExhaustiveInterningTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/RealisticExhaustiveCapacityProofTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/RetainedProjectionCodecTest.java
?? src/test/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotSizeDiagnosticsTest.java
?? src/test/java/com/daviddunn/retirementplanner/persistence/ExhaustiveSnapshotInterningTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java
```
