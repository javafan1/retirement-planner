# Phase 9D-2 Gate 2 — Deterministic Exhaustive snapshot payload

## 1. Outcome and boundary

Gate 2 is implemented and its focused tests pass. `COUPLE_EXHAUSTIVE` now has an explicit immutable payload, allowlisted envelope dispatch, integrity verification, and exact reconstruction of the original deterministic result graph. The representative complete-universe fixture fits every established storage constraint.

**No analyzer completion callback, asynchronous UI restoration, PDF-export lifecycle, or Quick persistence was connected. Gate 3 remains unauthorized and has not begun.** This gate does not claim that production Exhaustive runs are already saved or restored across sessions.

Read the Gate 1 report, remaining-analyzers audit, repository instructions, current search/result/request records, presentation grouping, report context/adapter, SS cross-reference, snapshot codec, compatibility policy, repository and existing regression tests. Current source is authoritative. The roadmap now skips Quick persistence as instructed.

## 2. Initial state and changed files

Branch: `main`. HEAD: `4fddd4ee1eaebc8354b009b77a381459495d665c`.

The working tree already contained the completed Gate 1 work: modifications to `IncomeSourceAttribution.java` and `AnalysisSnapshotCodec.java`, plus the Gate 1 report, retained-projection DTO/codec and fidelity test. Those changes were preserved. The index was empty.

Gate 2 adds:

- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ExhaustiveSnapshot.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveSnapshotTest.java`
- This report.

Gate 2 modifies:

- `src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java`: explicit Exhaustive dispatch, privacy-safe numeric-path field names, and matching payload/envelope completion-time verification. Its other working-tree changes belong to Gate 1.
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java`: assess historical Exhaustive copies using existing deterministic effective inputs, without treating them as weighted payloads.

No financial engine, analyzer UI, job controller, projection metric implementation, financial golden, dependency, rule resource, PDF exporter, or primary plan schema changed in Gate 2.

## 3. DTO design and complete field coverage

`ExhaustiveSnapshot` implements `AnalysisPayload`; the existing version-1 envelope dispatches it only for `COUPLE_EXHAUSTIVE`. No runtime type names, arbitrary polymorphism, domain-object serialization, or new storage system are introduced.

| Original information | Stored representation / reconstruction |
|---|---|
| Complete current-plan baseline | Gate 1 `RetainedProjectionSnapshot.capture(IntegratedSocialSecurityStrategyResult)`; includes evaluated elections, all annual values/accounts/attribution, fourteen metrics, warnings and advanced-SS flag |
| Every compact candidate, including failures | Immutable `Entry` list in original contiguous generation order; no top-row truncation |
| Complete claiming identity | Existing exact `WeightedSnapshots.Strategy`: both retirement ages/dates and both full survivor candidates |
| Ranking objective | Original `IntegratedStrategyRankingMeasure` |
| Successful ranking, ties, competition ranks | Original rank on each entry plus ordered occurrence references; reconstructed ranking references the same reconstructed entry instances |
| All fourteen metrics and differences | Exact existing immutable `ProjectionMetrics` records; nullable spouse value remains distinct from zero |
| Retained projection details | Full Gate 1 graph for each originally retained result; `null` means details were unavailable, never a request to calculate them |
| Structured candidate failure | Original category/message, with strategy identity supplied by the enclosing entry; no Throwable graph |
| Current-plan metric rank | Explicit nullable integer mapped to `OptionalInt`; not recomputed |
| Search runtime | Exact `Duration`, including nanoseconds |
| Presentation grouping | Original display limit and ordered representative occurrence/count pairs; no regrouping on reconstruction |
| Frozen `IntegratedReportContext` | `Report` retains household/primary/spouse strings, original run start, every original report `Section` and line, and original baseline description |
| Financial assumptions | The complete original report-assumption sections, preserved verbatim; annual financial values remain exact numbers in baseline/details and metrics rather than formatted text |
| SS cross-reference | Explicit availability state, explanation, original source run UUID/fingerprint/revision where available, complete recommendation elections, exact SS expected PV, matching deterministic occurrence/rank/estate/gap |
| Completion time | Original `Instant`; codec refuses disagreement between payload and envelope |
| Presentation selection | Optional complete election identity plus occurrence, supported deterministic metric and result-tab key |
| Algorithm/reference provenance and identity | Existing envelope, unchanged UUID/shape/kind/run/fingerprint/version/digest/origin metadata |

`IntegratedReportContext` currently contains frozen display/report strings and sections, rather than a complete original plan model. The new `Report` preserves exactly that source information; it does not claim to recover additional raw plan facts from formatted labels. It does not convert or round any financial result. Gate 3 must capture the original context while it is available, not recreate it from a later plan.

The current UI's SS cross-reference is only formatted text and is refreshed against live SS state. Gate 3 must freeze the new **structured** reference at completion, using the original recommendation's exact PV and source provenance. The new payload supports available, no-current-SS-result, and outside-search-universe states. Restoration must retain that historical state instead of joining to a later SS result.

## 4. Pure reconstruction and validation

`restore()` constructs a new baseline and candidate graph through Gate 1, then reconnects original ranked occurrence IDs to those entries. It does not call the search calculator, evaluator, projection engine, optimizer, mortality factory, ranking service, or SS benefit engine.

Validation rejects duplicate/missing/unordered generation identities; duplicate elections; orphan, duplicate or incomplete successful-ranking references; malformed competition ranks/order; wrong-household entries; invalid retained-detail strategy/metric association; invalid group references/counts; unsupported selection keys or mismatched selected elections; missing comparison provenance; mismatched cross-reference values; and inconsistent timestamps. Rank/order/group/gap checks verify stored invariants and do not replace stored values or produce a new ranking/comparison.

Gate 1 continues to rebuild detached account identities and within-projection references, preserves nullable state and income-attribution details, and refuses orphan account references. No live-plan account matching, synthetic zero account, or silent repair is used. Its unsupported-domain-variant and source-graph limitations remain in force.

The four actual deterministic heat-map display metrics are accepted; the weighted-only PV metric is not made into a deterministic feature. Failed cells and missing optional selection are supported without adding a new view.

## 5. Compatibility and security

The envelope schema, fingerprint schema, existing financial algorithm identifiers, weighted payload records, and Couple SS payload record remain unchanged. New dispatch uses `DeserializationContext.readValue` for the nested payload; complete-document trailing-token rejection remains enabled.

Both frozen pre-change weighted fixtures passed exact compressed/raw representation, payload-digest and decimal value/scale checks. Their file SHA-256 values remain:

- Couple: `fb4300180df4698722cbdde8b46359d21b7f0759736b3c177dc4d2e1458cc80a`
- Individual: `f064faeeb8f061a5131d34af068816d6dcf81f15203526ee4ccaa17541d15a07`

Existing Couple SS round-trip, restart/restoration, status and lifecycle tests passed. No frozen fixture or golden was regenerated. The new Exhaustive test also verifies exact re-encoding, normal repository save/verification/load, digest-corruption rejection, wrong-kind rejection and cloned identity/provenance preservation.

All existing limits remain: 32 MiB raw, 16 MiB compressed, 4,096-character numeric tokens, 2,000,000 tokens, 100,000 arrays, depth 80 and string length 256,000. Duplicate/unknown/missing/null-primitive/type-coercion safeguards are retained. New numeric diagnostics identify structural field paths without logging result values. Atomic writer, backups, file binding, locks and Retry Save implementation were not changed.

## 6. Capacity proof

Final measurement: `target/9d2-gate2-capacity-final.log`, test `completeUniverseCapacityAndTieFailureFidelity`.

Fixture dimensions:

- All **5,184** supported complete elections for the chosen modern cohorts, in actual request generation order.
- Actual 40-year baseline plus **20 independently evaluated 40-year detailed candidate projections**: 840 retained annual rows in total.
- Six accounts, primary/spouse/joint owners, IRA/Roth/brokerage/checking variants; real MI tax details, pensions/SS/income attribution, Medicare, RMDs and Roth execution from the existing engine. A synthetic additional cash account makes this bounded long-horizon fixture fundable.
- Other compact outcomes repeat the twenty evaluated metric patterns to exercise ties at full storage scale. Their candidate-minus-baseline differences are exact fixture subtractions. One structured failed candidate and missing details are retained. **This is a storage-scale fixture, not a claim that all 5,184 candidates were calculated for this measurement.**
- Original report context, exact SS reference/provenance, grouping and selection metadata are included.

| Measurement | Observed | Existing limit |
|---|---:|---:|
| Raw JSON | 15,174,908 bytes | 33,554,432 |
| Gzip | 679,770 bytes | 16,777,216 |
| Tokens | 1,017,429 | 2,000,000 |
| Arrays | 5,112 | 100,000 |
| Maximum nesting | 14 | 80 |
| Maximum numeric token | 92 characters | 4,096 |
| Encode, validation and compression | 203.550 ms | Informational |
| Decode, validation and reconstruction | 326.391 ms | Informational |
| Additional explicit pure reconstruction | 9.246 ms | Informational |

Timing uses `System.nanoTime()` around the actual codec operations, after payload/envelope construction, followed by a separate `restore()` call. These are single in-process observations, not statistically controlled performance guarantees. JVM: Temurin 25.0.4.1+1, Windows, test heap maximum 8 GiB. No standalone retained-memory measurement was made; hardware identification through CIM was unavailable under sandbox permissions. No hour-long weighted analysis was run.

The codec also round-trips a **1,070-character exact decimal** in a metric difference, with independent scales across all fourteen metrics. Existing numeric-length tests cover 1,024/1,070/4,096 boundaries, over-limit rejection and previous-file protection.

This evidence proves the representative fixture fits; it does not prove every possible horizon/account graph fits. The unchanged enforced limits still apply to larger real plans. No detail was discarded and no limit was raised to obtain this result.

## 7. Exact fidelity and focused verification

`ExhaustiveSnapshotTest`: **15 tests, zero failures/errors/skips**. Includes a real 24-strategy deterministic search with the maximum normal 20 details, complete envelope equality/re-encoding, all entries and ranked references, exact fourteen-metric values/scales, original baseline and retained projections compared using the Gate 1 independent field/identity oracle, explicit unavailable cross-references, malformed references, corruption, completion-time consistency, cloning and currency assessment.

Focused regression command used IntelliJ bundled Maven, the mandatory project-local `.codex-m2/repository`, and `-DargLine=-Xmx8g`. Actual matched classes:

- `ExhaustiveSnapshotTest` (15)
- `RetainedProjectionCodecTest` (27)
- `LegacyWeightedSnapshotCompatibilityTest` (2)
- `AnalysisSnapshotCodecTest` (7)
- `AnalysisSnapshotRepositoryTest` (12)
- `WeightedRestorationTest` (14)
- `WeightedNumericLengthTest` (7)
- `WeightedSnapshotSaveHotfixTest` (4)
- `CoupleSocialSecurityPersistenceTest` (10)
- `IntegratedSocialSecurityCompleteStrategySearchCalculatorTest` (8)
- `OpeningRmdProjectionTest` (4)
- `MultiOwnerRothConversionProjectionTest` (7)
- `SinglePersonCoreProjectionTest` (12)
- `TransactionalPlanPersistenceTest` (21)

Result: **150 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**, `target/9d2-gate2-final.log`, 59.458 s. This includes the existing actual full-universe deterministic search regression and transactional fault-injection/backup checks. Expected injected-failure diagnostic stacks appear in the log; they are passing safety tests.

After improving the capacity fixture's complete displayed groups and separating encode timing from payload construction, reran `ExhaustiveSnapshotTest`: **15/15 passed**, `target/9d2-gate2-capacity-final.log`, 14.303 s. These overlap the 150; they are not 165 distinct tests. Production code was unchanged after the 150-test run.

Earlier local scaffolding failures were corrected: the older-cohort fixture legitimately had 3,969 elections, the deep comparison initially used record equality across mutable projections, and its modern-cohort replacement required age-consistent opening-RMD history. A getter-name compilation error was also fixed. No domain validation, golden, or financial assertion was weakened.

The complete Maven suite was **not run**, as explicitly requested for this gate. No new UI behavior exists to preview. Cross-session Exhaustive UI/PDF acceptance is not claimed.

## 8. Remaining Gate 3 work and risks

1. Capture the payload and original report/structured SS reference at the accepted Exhaustive completion boundary, preserving existing generation/revision/cancellation gates and success policy.
2. Connect application-owned asynchronous saving/loading, plan/view guards, durability status and Retry Save. Do not identify an accepted-but-uncommitted result as saved.
3. Reattach the restored graph to the existing presentation/heat-map/report paths. Use the stored grouping/rank/selection references and original report assumptions; do not call financial calculators or join to a later SS result.
4. Exercise Current/Outdated/reversion/invalid-input status and replacement retention in the actual UI. The new Save As compatibility branch and clone test prepare for this; they do not activate that lifecycle.
5. Verify historical PDFs and rendered layouts at their separately authorized gates, then complete final capacity/regression acceptance with the full suite.

Gate 1's deliberate rejection of orphan references and unsupported future account/state types remains a boundary, not an instruction to fabricate replacement data. For capacity beyond the established budget, retain the previous valid snapshot and report evidence rather than silently truncating detail or increasing limits.

## 9. Final Git status

`git diff --check` passes (only normal LF-to-CRLF Git notices). Index is empty. Combined working tree includes preserved Gate 1 work and Gate 2 additions:

```text
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java
 M src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java
 M src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java
?? PHASE-9D-2-EXHAUSTIVE-PROJECTION-CODEC.md
?? PHASE-9D-2-GATE-2-EXHAUSTIVE-SNAPSHOT.md
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ExhaustiveSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/RetainedProjectionCodec.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveSnapshotTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/RetainedProjectionCodecTest.java
```

Generated logs are ignored under `target/`. No generated snapshots, local Maven artifacts, golden resources or unrelated files are included. Nothing was staged, committed, pushed, reset or discarded. **Stop after Gate 2 for review; Gate 3 requires explicit authorization.**
