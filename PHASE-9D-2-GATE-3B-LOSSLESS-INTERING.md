# Gate 3B — Mandatory interning audit: stopped before implementation

## 1. Decision

**Stopped at the user's mandatory pre-implementation audit gate. No production interning format was implemented.**

The Gate 3A proof depends materially on artificial duplication: it assigns one metric record and one difference record to all 5,184 candidates, although their elections span 81 distinct retirement-age pairs. The retained detail sample is also restricted to the first twenty survivor-election combinations of one retirement pair, rather than the twenty results actually retained by the search's ranking policy.

An independently calculated bounded search confirms that the copied outcomes are not valid outcomes for every represented election. Interning remains feasible and some repetition is real, but the reported approximately 92% reduction and five-entry pool are not representative production-capacity/resource evidence. Proceeding directly to production would disregard the explicit instruction: “If the test-only optimization depends on unrealistic duplication, stop and report that before implementing it.”

**Recommendation:** first replace the storage-only proof with an independently calculated full-universe fixture that retains the actual top details, plus a distinct-output stress fixture. Review those measurements before implementing the representation. Do not change financial calculations to make interning more effective.

## 2. Repository baseline and preparation

Branch `main`; HEAD `4fddd4ee1eaebc8354b009b77a381459495d665c`. Initial working tree: five modified tracked files and fourteen untracked files from Gates 1–3A, with an empty index. Preserved all of that work.

Reviewed repository instructions, the Gate 1/2/3/3A reports, 9B/9C/9D-1 persistence findings, the synthetic fixture/experiment, immutable payload and retained-projection structures, actual search/evaluation/detail-retention code and compatibility tests. Current source is authoritative.

This gate adds only:

- `src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveInterningAuditTest.java`
- This report, at the exact filename requested by the user.

No existing test was weakened or changed in this gate. No production code, storage representation, limit, financial algorithm, golden, dependency, PDF or live process changed.

## 3. Exact source of the synthetic redundancy

In `ExhaustiveCapacityInvestigationTest.fixture(...)`:

1. The real request generates all 5,184 complete elections.
2. Only the first twenty elections are evaluated. All twenty share the same primary/spouse retirement-age pair; they vary only survivor elections in a BOTH_SURVIVE scenario. Their metric equality is checked.
3. `metrics` and `differences` are taken from that first financial outcome.
4. The loop constructing all 5,184 entries assigns those same two records to every election, including different retirement ages/dates.
5. Entries 1–20 keep those first detailed evaluations; later entries omit detail. This is not the actual search's ranked retention policy.

The fixture was explicitly labeled a storage-scale experiment in Gate 3A. Its byte-lossless expansion proof is valid for **that synthetic document**. It is not evidence that the same duplicate distribution holds in a real completed search. Earlier capacity figures should retain that qualification.

The actual `IntegratedSocialSecurityCompleteStrategySearchCalculator.evaluate(...)` independently evaluates each complete strategy and calculates that candidate's metrics and candidate-minus-baseline differences. Its detail queue retains results by ranking measure and configured retention count. The production data model stores these individual outcomes; it does not assign one pair's outcomes across all retirement pairs.

The experiment interns exact serialized metric/difference records, account arrays and complete annual arrays. In the synthetic case, almost every metric/difference occurrence repeats the same value, and twenty detailed annual graphs have been deliberately selected from one equivalent pair. This explains the unusually small five-entry pool.

## 4. Independent calculation evidence

New test: `ExhaustiveInterningAuditTest.syntheticMetricReuseIsNotRepresentativeOfIndependentlyCalculatedRetirementPairs`.

The audit uses synthetic financial inputs, the real supported request/elections, 40 projection years, a couple household and the established engine/search. It independently calculates sixteen complete strategies: two primary retirement ages × two spouse retirement ages × two primary survivor choices × two spouse survivor choices. All sixteen succeed and retain their details under the normal retention budget.

Assertions verify:

- The existing 5,184-candidate fixture spans **81 retirement pairs** but contains **one exact metric record and one exact difference record**.
- The sixteen independently evaluated candidates contain **four exact metric records and four exact difference records**. Equality is record/BigDecimal-scale sensitive, not formatted or approximate.
- A represented age-70/age-70 complete strategy's copied synthetic estate outcome differs from the independently evaluated outcome for those same complete elections. No private numerical values are printed.
- Experimental expansion of the independently calculated snapshot reproduces every original serialized byte, showing that exact interning itself can preserve data even when the duplicate distribution changes.

Measured audit output:

```text
syntheticCandidates=5184
syntheticRetirementPairs=81
syntheticMetricRecords=1
syntheticDifferenceRecords=1
actualCandidates=16
actualMetricRecords=4
actualDifferenceRecords=4
actualRetained=16
actualRawBytes=4512639
experimentalRawBytes=1342920
experimentalPoolEntries=14
reductionPercent=70.241
```

| Case | Original raw bytes | Experimental raw bytes | Pool entries | Interpretation |
|---|---:|---:|---:|---|
| Gate 3A synthetic large fixture | 45,334,478 | 3,649,633 | 5 | Historical storage-only experiment; artificially reused compact outcomes |
| New independently calculated bounded search | 4,512,639 | 1,342,920 | 14 | Actual candidate outcomes; exact byte restoration verified |

These are different fixture dimensions, so the percentages must not be used to extrapolate a real full search's size. The new test does not establish the eventual number of unique full-universe outcomes, nor claim that real interning will fail to fit. It establishes that the previous one-outcome distribution is not representative.

The live **44,692,375-byte** snapshot still has no collected component breakdown. Neither fixture identifies its actual dominant values or duplicate counts. No expensive native analysis was rerun or live data accessed.

## 5. What can and cannot be reused from the proof

Verified useful properties:

- It merges only exactly serialized values, retaining BigDecimal scale and field/collection order.
- First-encounter traversal and table assignment are stable for the fixed original document; hash/object identity is not used as serialized identity.
- Some redundancy exists in real outputs: the bounded real calculation repeats financial outcomes across survivor choices that do not affect its BOTH_SURVIVE scenario.
- Account definitions can repeat across projection snapshots without implying that reconstructed mutable account objects should be shared.

Not yet production-proven:

- Capacity and unique-table counts for independently calculated full-universe results and varied death/scenario contexts.
- Peak memory for many distinct annual graphs and long decimals, rather than five interned values.
- Bounded logical expansion and reconstruction work under adversarial references.
- Full versioned dispatch, compatibility/digest migration, malicious-table handling, restored UI/lifecycle coverage and production Retry Save for an optimized representation.

The experiment's in-memory JSON tree/string processing and expansion back to large raw JSON are **not** an acceptable production decoder. It has no production resource/compatibility contract. Its strict reference checks in a bounded laboratory fixture do not prove the complete security requirements in the request.

An additional source observation matters for future bounds: the current codec's `MAX_ARRAYS` check counts `START_ARRAY` tokens, not items in each individual array. A production intern-table decoder must explicitly enforce table/reference/expanded collection sizes; it must not assume the existing array-counter check supplies that protection.

## 6. Proposed design direction — not implemented

If the next proof justifies production work:

1. Use explicit Exhaustive-only version dispatch, retaining all valid legacy Exhaustive, Couple SS and weighted representations/digests. Do not silently repurpose old schema bytes or enable arbitrary polymorphism.
2. Consider type-specific immutable tables for exact metrics/differences, account definitions and annual records/arrays. Use stable first-encounter ordering and scale-sensitive equality. Do not infer equivalence from retirement age pairs or formatted financial values.
3. Preserve every candidate, original rank/tie, failure, complete election, retained projection, warning, baseline, assumption, SS reference and presentation selection. Never omit distinct values to fit.
4. Keep mutable reconstructed account identities independent within/across their required scopes. Reused immutable account DTOs are values, not permission to merge account-list positions or source identities.
5. Require explicit supported versions, type-specific bounded indices, valid IDs, no unsupported cycles/external references, unexpected-field rejection, and a bounded preflight expanded-work count before domain reconstruction.
6. Keep all existing physical limits. Specify additional logical expansion/fan-out budgets rather than expanding a small document without bound. Avoid allocating a full expanded legacy JSON copy merely to decode or compute integrity.
7. Only after those contracts pass fidelity/adversarial/compatibility tests should production completion/repository/restore paths use the new representation.

This is a recommendation for the next reviewed step, not a completed snapshot schema or implementation.

## 7. Verification actually executed

IntelliJ bundled Maven, required project-local repository, `-DargLine=-Xmx8g`.

Final focused log: `target/9d2-gate3b-audit-final.log`.

| Class | Tests | Result |
|---|---:|---|
| ExhaustiveInterningAuditTest | 1 | Passed |
| ExhaustiveSnapshotTest | 15 | Passed |
| RetainedProjectionCodecTest | 27 | Passed |
| LegacyWeightedSnapshotCompatibilityTest | 2 | Passed |
| CoupleSocialSecurityPersistenceTest | 10 | Passed |
| **Total** | **55** | **Zero failures/errors/skips; BUILD SUCCESS** |

Elapsed Maven time: 21.886 s. The first audit test attempt selected a survivor election outside the bounded request and errored in test lookup; selection was corrected to the same supported complete election in both data sets. Final financial assertions were not weakened and production source was unchanged.

No full Maven suite was run because the mandatory gate stopped this phase before production implementation. No new representation's repository/UI/PDF/Retry Save acceptance, peak-memory benchmark, native acceptance or worst-case capacity is claimed. Gate 4/5 work has not begun.

`git diff --check` passes with normal LF-to-CRLF Git notices. No financial goldens, seeded Monte Carlo expectations or frozen snapshots changed.

## 8. Native application boundary and next approval

No hot-patch, agent, debugger attachment or running-JVM modification was attempted. No rebuild/restart is needed to use a new optimized format because **there is no production optimized format in this gate**. The current 32 MiB rejection remains in force. Do not claim the native 44,692,375-byte case is resolved.

Recommended review decision: authorize a corrected full-universe capacity proof using actual calculated outcomes and actual ranked retention, with varied survivor/death contexts and distinct-value adversarial cases, then reassess production interning. A bounded test fixture must remain synthetic in personal data but accurate in result generation. The earlier shortcut is appropriate for testing serialized graph size/fidelity, not for qualifying expected interning ratios.

When production work is subsequently approved and complete, provide normal IntelliJ rebuild/restart instructions and native save/restart/restore acceptance. The existing live accepted-but-unsaved result must not be assumed to survive a restart, and no live failure should be declared fixed based solely on these synthetic measurements.

## 9. Complete Git status

Combined working tree includes all preserved prior work plus this gate's one new test and report. Index is empty; branch/HEAD unchanged.

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
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ExhaustiveSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotSizeDiagnostics.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/RetainedProjectionCodec.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveResultSnapshots.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveCapacityInvestigationTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveInterningAuditTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveSnapshotTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/RetainedProjectionCodecTest.java
?? src/test/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotSizeDiagnosticsTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java
```

Logs remain ignored under `target/`. Nothing was staged, committed, pushed, reset or discarded. **Gate 3B production implementation is blocked at its explicit audit stop condition; stopped for review.**
