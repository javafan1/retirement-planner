# Gate 3B-1 — Realistic Exhaustive capacity proof

## Decision and scope

Investigation completed using one real, independently calculated full-universe search. All 5,184 candidates succeeded. No production format, limit, financial algorithm, PDF, Quick functionality or application behavior changed.

The measured existing snapshot fits the current limits: **20,479,409 raw bytes / 837,686 gzip bytes**. Test-only exact interning reduces it to **3,231,394 raw / 136,204 gzip bytes** (84.22% raw reduction). This is genuine repetition in independently calculated outcomes, rather than the previous artificial one-outcome fixture.

Recommend an explicitly versioned, Exhaustive-only, bounded interning representation within the existing single-file atomic repository, subject to approval. Do not increase limits based on this experiment. The native **44,692,375-byte** snapshot has not been obtained or measured by component; this investigation does not establish its exact composition or resolve that native failure.

## Preparation and reproducibility

Reviewed AGENTS.md, Gates 1/2/3/3A/3B reports and existing persistence findings, the actual search/evaluator/detail-retention path, snapshot DTOs, retained-projection codec, size diagnostics and test-only interning experiment. Current source remains authoritative.

Branch `main`, HEAD `4fddd4ee1eaebc8354b009b77a381459495d665c`. Initial state: five modified tracked files, sixteen untracked files, empty index. Preserved all existing work.

Added only:

- `src/test/java/com/daviddunn/retirementplanner/domain/projection/RealisticExhaustiveCapacityProofTest.java`
- This report.

The opt-in measurement test uses `RetirementPlanScenarioCopyService.copy`, `IntegratedSocialSecurityCompleteStrategySearchRequest.standard` and the actual `IntegratedSocialSecurityCompleteStrategySearchCalculator.calculate`. Every candidate follows normal production evaluation; no outcome or projection is substituted across unrelated candidates. The standard twenty-detail retention policy is unchanged.

Controls: one sequential search, ten-minute cooperative cancellation deadline, fifteen-minute test timeout, 4 GiB test heap. The single actual search completed in **31.917 seconds**. A synthetic-data, existing-format snapshot was retained under ignored `target/gate3b1-full-calculated.json.gz`; subsequent verification explicitly reused it with zero financial calculations. No live process was inspected or modified.

## Financial fixture

Synthetic couple: primary DOB 1960-02-01 / MALE, spouse DOB 1962-06-01 / FEMALE. FRA monthly estimates 3,000.00 and 2,000.00, stored valuation year 2027, current claiming ages 67. Pension 2,000.00 monthly, 0.0200 COLA and 1,000.00 survivor amount. These are test values, not user data.

Six accounts totaling 3.95 million: primary brokerage, primary Traditional and Roth IRAs, spouse Traditional and Roth IRAs, joint checking. Projection starts 2027-07-01 for forty years; investment return 0.045, general inflation 0.025, healthcare inflation 0.045, SS COLA 0.02. General annual spending 100,000, healthcare 8,000, one-time purchase 25,000 in 2028. Fixed annual Roth request 25,000 from 2027, no schedule stop. Configured primary death 2045, survivor age 67 and post-death spending factor 0.75. Federal MFJ and supported Michigan state rules apply through the normal engine, including subsequent surviving-filer treatment.

Assertions confirm actual positive investment growth, spending, federal/state taxes, RMDs and executed Roth conversions. Baseline has eighteen years with nonzero Part D IRMAA. Annual account snapshots, tax detail, cash settlement and income attribution are retained normally.

The frozen report includes configured assumptions; Social Security cross-reference explicitly records `NO_CURRENT_SOCIAL_SECURITY_RESULT`. A separate SS optimization was not run merely to populate that optional context. Thus this measurement does not characterize a large available SS cross-reference.

## Actual result dimensions and byte contributions

| Dimension | Measured |
|---|---:|
| Generated / successful / failed candidates | 5,184 / 5,184 / 0 |
| Distinct complete fourteen-metric records | 81 |
| Distinct candidate-minus-baseline records | 81 |
| Retained candidate projections | 20 |
| Baseline plus retained projections | 21 |
| Total annual rows | 840 |
| Maximum horizon / accounts | 40 / 6 |
| Maximum numeric-token length | 174 |
| JSON tokens | 1,072,920 |

Distinctness uses exact record equality, including BigDecimal scale. The 81 outcomes are actual engine outputs across the 5,184 complete elections; the test does not assume other death scenarios/plans have only 81 outcomes. All rankings, elections and candidate occurrences remain independently retained.

Disjoint top-level accounting:

| Contribution | Raw bytes |
|---|---:|
| Compact entries, excluding retained details, including syntax | 13,224,662 |
| Retained candidate details | 6,880,400 |
| Baseline | 345,246 |
| Envelope and other metadata | 29,101 |
| **Total** | **20,479,409** |

Overlapping drill-downs (do not add these again to the preceding table): candidate metrics 5,789,376; differences 5,572,096; strategy elections 1,378,296; all projection annual graphs 7,160,593; account definitions 33,453; annual account snapshots 270,496; frozen report/context 1,212; historical SS cross-reference 324 bytes. Within annual graphs, state-detail fields contribute 2,496,018, income-source attribution 1,776,248, cash settlement 519,935, Medicare 304,682 and authoritative Social Security 218,496 bytes.

Measured byte-identical redundancy includes 5,698,917 metric bytes, 5,485,032 difference bytes, 6,477,651 annual-graph bytes and 31,860 account-definition bytes. These are nested contributions, not an independent additive file-size calculation. Actual retained projections average 344,020 bytes; baseline is 345,246 bytes.

## Performance and memory

Windows, Temurin OpenJDK 25.0.4.1+1-LTS, Maven test heap `-Xmx4g`. CPU inventory via CIM was denied by the environment; no hardware model is asserted. Single observations, no statistical benchmark or warmup guarantee.

| Operation | Original full-search run | Cache-based verification |
|---|---:|---:|
| Existing JSON serialization | 44.857 ms | 50.998 ms |
| Privacy-safe breakdown scan | 238.966 ms | 261.660 ms |
| Gzip | 128.793 ms | 133.154 ms |
| Lab gzip + JSON decode | 231.524 ms | 154.298 ms |
| Pure result reconstruction | 35.019 ms | 7.987 ms |
| Test-only parse/intern/encode | 321.150 ms | 300.903 ms |
| Interned gzip | 19.981 ms | 20.200 ms |
| Exact original JSON expansion | 100.048 ms | 81.937 ms |

Production `AnalysisSnapshotCodec.encodeMeasured` accepted this snapshot in the verification run: **263.831 ms encode / 581.125 ms decode**. It produced the exact same gzip bytes and reconstructed the exact snapshot. Production timings include its additional validation and safety work; lab timings are not substitutes for repository commit latency.

Heap sampling every 100 ms observed peak **513,225,504 bytes** during the initial search plus measurements (starting 30,562,528; maximum heap 4,294,967,296). Verification peak was 485,644,512 bytes. These are approximate sampled used-Java-heap figures, include coexisting measurement trees/arrays and GC effects, exclude native RSS and can miss spikes. Per-alternative incremental peak memory was not isolated; no unsupported claim that interning lowers peak heap is made.

## Alternatives

| Alternative | Measured size for this result | Fidelity / compatibility | Cost and safety |
|---|---|---|---|
| A. Existing snapshot | 20,479,409 raw; 837,686 gzip | Exact existing representation; production codec accepts | Existing atomic single-file protocol; substantial repeated decimals. Larger plans may exceed raw/token limits despite small gzip. |
| B. Exact interning experiment | 3,231,394 raw; 136,204 gzip; 165 pool entries | Byte-for-byte expansion to original JSON verified; no rounding, field omission or recalculation | Prototype uses trees and expansion and is not a production decoder. Requires versioning, typed bounded tables, reference/expansion validation and independent mutable-domain reconstruction. |
| C. Separate bounded components | Data-only sum 20,479,537 raw; 891,066 gzip; largest 345,246 raw | Component measurement only; manifest/reference/recovery design not implemented | 63 values: baseline, 20 details, 41 compact chunks of at most 128 entries, metadata. Manifest overhead **not included**; no complete-format or atomic acceptance proof. More filesystem/protocol complexity and slightly worse compression. |
| D. Higher raw limit | Same bytes as A; no new limit tested | Wire unchanged; financial fidelity unchanged | No need for this fixture. A hypothetical 64 MiB cap would admit the observed native total but does not establish other limits, memory safety or larger-plan capacity. Not implemented or recommended on this evidence. |

Compressed sizes are measured, not estimates. Option C's real total must exceed the data-only measurement by its as-yet-undesigned manifest, bindings and integrity metadata; no invented final size is provided. Independent component compression took 126.736 ms initially / 135.625 ms on verification, excluding partition construction and any manifest/filesystem operations. Component restoration timing is unavailable because no component format was implemented.

### Recommended B: bounded single-file exact interning

The existing test-only experiment interns exact serialized metrics, differences, account definitions and annual graphs using type-qualified keys and stable first occurrence. Its 165 entries here demonstrate real opportunity. It verifies expansion to exactly the original bytes, thereby preserving even scale and field ordering. This is evidence for design, not an approved durable format.

A future production design should explicitly dispatch a new Exhaustive representation version while preserving old Exhaustive reads and all weighted/Couple SS bytes, schemas and digests. Use allowlisted typed tables and deterministic first-occurrence assignment; never approximate numeric equality. Preserve separate candidate occurrences and account/source indices. Immutable storage values may share data; detached mutable accounts and projections must be reconstructed with their original projection-local identity relationships, never matched by name or merged by equal balances.

Before publication, validate reference types/ranges, required tables, duplicates, unsupported versions/fields and cycles. Bound table entries, total references, expanded annual rows/accounts/elements and reconstruction work **before allocating** expanded graphs. The prototype itself is not such a security implementation. Keep raw 33,554,432, gzip 16,777,216, numeric 4,096, tokens 2,000,000, array 100,000, depth 80 and string 256,000 protections unchanged. Preserve payload integrity and the existing temporary-file verification, backup and atomic commit protocol.

### Why not C or D now?

Components could avoid loading a monolithic graph and support larger data, but require an immutable generation manifest with ordered references, type/size/digest/UUID binding, bounded aggregate reads and an atomic manifest commit only after all components validate. Keep the previous manifest and its components intact for recovery; interrupted writes must never replace that generation. Windows crash/recovery and file cleanup require dedicated tests. Splitting a document is not authorization to bypass aggregate resource limits. This is a larger architectural change than B.

A limit increase is simpler but retains repetition and increases exposure to allocation/parse work. This real fixture already fits; the native total alone cannot justify a resource policy. Seek explicit approval and further measurements before either C or D.

## Verification

One opt-in full calculation test passed. Subsequent focused verification: **64 tests, zero failures/errors/skips**, BUILD SUCCESS, 31.421 seconds, reusing the completed snapshot with `financialCalculations=0`:

- `RealisticExhaustiveCapacityProofTest`
- `RetainedProjectionCodecTest`
- `ExhaustiveSnapshotTest`
- `LegacyWeightedSnapshotCompatibilityTest`
- `AnalysisSnapshotCodecTest`
- `AnalysisSnapshotSizeDiagnosticsTest`
- `CoupleSocialSecurityPersistenceTest`

These 64 include the measurement test; do not count the first run as an additional distinct test. Frozen weighted compatibility, exact scales, strict codec validation, retained-projection field fidelity and Couple SS persistence remain passing. Full result DTO equality and nested domain graph comparisons cover baseline and twenty retained details; every compact metric/difference is compared. Experimental expansion must equal original JSON bytes exactly. Original production-codec gzip bytes and decoded snapshot equality are also asserted.

Used IntelliJ bundled Maven and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`. Measurement flags: `-Dretirementplanner.exhaustiveCapacityProof=true`; verification additionally `-Dretirementplanner.reuseCapacitySnapshot=true`. The opt-in condition prevents inadvertently repeating this benchmark in ordinary test runs and does not disable an existing test. Logs under ignored `target/9d2-gate3b1-measure.log` and `target/9d2-gate3b1-verification.log` contain privacy-safe counts/bytes/timings only for the measurement.

No full Maven suite was run: this is investigation-only with no production edits; final full regression acceptance remains Gate 5. No native UI, PDF or real user snapshot acceptance is claimed. `git diff --check` passed; only existing LF/CRLF notices were printed.

## Limitations and next decision

This is one realistic full calculation, not a worst-case proof. It has six accounts, forty years, all successful candidates and an unavailable SS cross-reference. Different survivor scenarios, longer/precise financial values, more accounts, candidate failures and a populated historical cross-reference can change duplication and size. The actual native component breakdown remains unconfirmed. No universal compression ratio or guarantee for the 44,692,375-byte case follows from this result.

Approve a separate bounded, versioned Exhaustive-only interning implementation if desired, including adversarial reference/expansion tests, existing-format compatibility, independent financial-graph comparisons and distinct-output stress cases. Retain all current limits. Native acceptance must subsequently save and reopen a real Exhaustive result before declaring the reported production failure resolved. No Gate 4 or Gate 5 work was begun.

## Final Git status

Five modified tracked files, all pre-existing:

```text
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java
 M src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java
 M src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedResultPersistence.java
```

Eighteen untracked files, including preserved previous work:

```text
PHASE-9D-2-EXHAUSTIVE-PROJECTION-CODEC.md
PHASE-9D-2-GATE-2-EXHAUSTIVE-SNAPSHOT.md
PHASE-9D-2-GATE-3-EXHAUSTIVE-INTEGRATION.md
PHASE-9D-2-GATE-3A-CAPACITY-INVESTIGATION.md
PHASE-9D-2-GATE-3B-LOSSLESS-INTERING.md
PHASE-9D-2-GATE-3B1-REALISTIC-CAPACITY.md
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ExhaustiveSnapshot.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java
src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotSizeDiagnostics.java
src/main/java/com/daviddunn/retirementplanner/persistence/RetainedProjectionCodec.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveResultSnapshots.java
src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveCapacityInvestigationTest.java
src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveInterningAuditTest.java
src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveSnapshotTest.java
src/test/java/com/daviddunn/retirementplanner/domain/projection/RealisticExhaustiveCapacityProofTest.java
src/test/java/com/daviddunn/retirementplanner/domain/projection/RetainedProjectionCodecTest.java
src/test/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotSizeDiagnosticsTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java
```

Index empty. No files staged, committed, pushed, reset or discarded. Generated measurement artifacts remain under ignored `target/`.
