# Gate 3A — Exhaustive snapshot capacity investigation

## 1. Recommendation and limits of the evidence

**Keep the existing snapshot format and 32 MiB limit unchanged. Collect the live Retry Save breakdown before approving a storage correction.** Production changes in this investigation add diagnostics only. The native 44,692,375-byte result has not been inspected: the user confirmed that only its total size was collected. Its actual candidate/detail/year/account counts and individual component sizes therefore remain unknown. It would be incorrect to identify a particular live financial field as the cause from that total alone.

The size failure itself is established: `AnalysisSnapshotCodec.raw()` serializes the validated envelope, compares its byte count with 33,554,432, and throws `SIZE_LIMIT` **before compression, token scanning, or transactional replacement**. Compression or changing gzip settings alone cannot cure that rejection. The accepted result and previous durable snapshot remain protected.

Measured synthetic evidence now reproduces the same class and approximate magnitude of failure: **45,334,478 raw bytes**, with 5,184 candidates, 20 retained details, 40 years and six accounts. There is no need to assume a larger candidate universe or duplicate complete result serialization to explain a threefold size increase. Exact decimal scale propagated through existing pension calculations can produce it. This is a **demonstrated mechanism, not a diagnosis of the user's inputs**.

Conditional recommendation after collecting live evidence: prefer explicit lossless interning of exact repeated metric records and immutable projection subgraphs if the live duplicate counts justify it. Consider a kind-specific bounded size-policy increase only if necessary and separately approved. Neither correction has been enabled.

## 2. Scope, baseline and files

Branch `main`; HEAD `4fddd4ee1eaebc8354b009b77a381459495d665c`. Gate 1/2/3 work was already unstaged: five tracked files modified and ten untracked files. All were preserved. Read the prior reports and current payload, retained projection DTO, capture path, codec, repository, store and Retry Save implementation.

This investigation changes/adds only:

- `src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java`: eight-line diagnostic hook after serialization and before the unchanged raw-size rejection. Other changes in that file belong to prior gates.
- `src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotSizeDiagnostics.java`: bounded, privacy-safe streaming size attribution.
- `src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveCapacityInvestigationTest.java`: controlled fixture experiments, laboratory-only interning comparison and actual store Retry Save diagnostic test.
- `src/test/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotSizeDiagnosticsTest.java`: exact UTF-8 byte accounting, null-detail/count/privacy and constraint tests.
- This report.

No production payload/representation, financial calculation, projection, ranking, tax, SS, mortality, reference data, dependency, Quick persistence, PDF or storage limit changed. No native financial data or full serialized result contents were obtained or logged. Test-only experiments are not installed codecs and produce no production snapshot files.

## 3. What the live diagnostics measure

For `COUPLE_EXHAUSTIVE`, oversized serialization automatically emits one bounded log record beginning:

```text
Exhaustive snapshot size breakdown: Summary[rawBytes=..., complete=...,
candidates=..., retained=..., projections=..., annualRows=...,
maximumYears=..., maximumAccounts=..., maximumNumberLength=...,
tokens=..., bytes={...}, duplicateBytes={...}]
```

The same record can optionally be enabled for a successful smaller encode with JVM property `retirementplanner.snapshot.sizeDiagnostics=true`. It is not necessary to enable that property for the reported failure.

The `bytes` fields include:

| Field | Meaning |
|---|---|
| `compactEntriesIncludingSyntax` | Complete entries array minus non-null retained projection object bytes; includes compact values, strategy/failure/rank records, property names and array syntax |
| `retainedDetails` | Sum of all non-null complete retained projection DTO objects; absent details are not counted as projections |
| `baseline` | Complete original baseline projection DTO |
| `envelopeAndOtherMetadata` | Remaining document bytes outside baseline and entries |
| `frozenReportAndContext` | Payload report plus envelope context |
| `socialSecurityCrossReference` | Complete original structured SS cross-reference |
| `accountDefinitions` | Account definitions across baseline and retained projections |
| `annualAccountSnapshots` | Annual `balances` arrays, including within-projection account references and exact ending balances |
| `candidateMetrics`, `candidateDifferences`, `candidateStrategies` | Exact respective compact value objects |
| `projectionAnnualGraphs` | Complete `years` arrays across all retained/baseline projections |
| `annualField.*` | Original annual field-value byte totals, including state tax, income attribution, SS, Medicare and settlement subgraphs |

The first four partition the document exactly. Other fields are **overlapping drill-downs**, not additional bytes to add to that partition. Counts and maximum dimensions distinguish actual object shape from a numerical-text expansion.

`duplicateBytes` estimates repeated **identically serialized** metric, difference, strategy, account-definition and annual-graph content using internal SHA-256 keys. Only aggregate reusable byte totals are logged, not hashes or financial values. A future codec must confirm exact equality, type and reference validity, not use these diagnostic estimates as replacement rules.

The scanner traverses existing UTF-8 bytes without constructing a JSON tree. It logs no names, dates, balances, benefit/expense/tax amounts, serialized values, plan UUIDs or filesystem paths. Annual field labels are allowlisted from the retained DTO; an unknown key is reported as `<unrecognized>` rather than echoed. It reports at most a fixed collection of field categories regardless of candidate count.

Diagnostic safety budgets are 128 MiB of already-produced bytes, 8 million scanned tokens, depth 80, the existing numeric/string limits, and 100,000 duplicate keys. These are **inspection budgets, not accepted-document limits**. A larger/incomplete scan is marked `complete=false`; diagnostic failure cannot permit a write or change its original failure category. Partial scans omit the derived full-document partition rather than printing negative/misleading totals.

The production acceptance limits remain **32 MiB raw, 16 MiB gzip, 2 million tokens, 100,000 arrays, depth 80, string length 256,000 and numeric length 4,096**. Atomic verification, backups, binding and Retry Save are unchanged.

## 4. Controlled reproduction and origin of expansion

The earlier Gate 2 fixture used 40 years, six accounts and normal-scale pension COLA. Its surviving-household case also used repeated compact metric patterns to bound financial work; it was explicitly not a fully calculated 5,184-strategy production search. It proved one graph fits, not that production graphs with arbitrary precision fit.

The new fixture retains the same dimensions and all entries, baseline, 20 actually evaluated detailed projections, report context, SS reference and selection. Remaining compact outcomes repeat the evaluated pattern solely for storage-scale testing. The original engine, validated elections and domain rules produce all retained annual financial values. No numbers are padded in serialized output to manufacture a size failure.

The controlled variable is the stored scale of an existing pension COLA: numerically the same rate, represented with 2, 20 or 21 fractional places. `Pension.calculateAnnualIncome()` uses `ONE.add(annualColaRate).pow(yearsSinceStart)`, then exact multiplications without truncating its scale. Those outputs enter income/tax/retained-cash/source-attribution records and fourteen-metric summaries. Their long exact decimal representations are then serialized repeatedly for compact candidates and detailed annual values. This is not a discovered calculation error and is not permission to normalize, round or strip financial scale.

Earlier exploratory checks also found:

- Changing only **general inflation's trailing-zero scale** from 2 to 8/14 in this particular fixture did not alter serialized size. That result must not be generalized to every assumption/path.
- Increasing accounts from 6 to 24/36, with this same 40-year case, raised raw size from 15,176,328 to 16,799,776/17,544,280 bytes. It did not reproduce the threefold expansion.

Those checks narrow the synthetic mechanism; they do not reveal the live plan's account count, pension values, COLA scale or horizon.

## 5. Measured component sizes

Final reproducible observations: `target/9d2-gate3a-verified.log`. Units are bytes, not MiB. All three cases have **5,184 candidates, 20 retained projections plus baseline, 840 annual rows, 40 years and six accounts**.

| Component | Scale 2 | Scale 20 | Scale 21 |
|---|---:|---:|---:|
| Full raw document | 15,176,328 | 43,747,176 | 45,334,478 |
| Gzip, laboratory measurement | 679,717 | 839,196 | 847,353 |
| Maximum numeric length | 92 | 812 | 852 |

For the 45,334,478-byte failure fixture, the exact partition is:

| Contribution | Bytes |
|---|---:|
| Compact entries including syntax | 31,864,214 |
| Retained detailed projections | 12,802,740 |
| Baseline projection | 639,966 |
| Envelope/other metadata | 27,558 |
| **Total** | **45,334,478** |

Overlapping drill-downs:

| Field family | Bytes |
|---|---:|
| Candidate metrics | 14,929,920 |
| Candidate-minus-baseline metrics | 15,085,440 |
| Complete candidate strategies | 1,378,296 |
| Annual graphs, all 21 projections | 13,341,195 |
| Annual state-tax data | 4,235,238 |
| Annual income-source attribution | 2,060,769 |
| Annual cash settlement | 1,696,698 |
| Annual account balance snapshots | 256,809 |
| Account definitions | 32,886 |
| Frozen report and context | 489 |
| Historical SS cross-reference | 637 |

Measured exactly repeated content: candidate metrics 14,927,040 bytes; differences 15,082,530; annual graphs 12,070,928; account definitions 31,320. These content families, **not** duplicated assumptions or SS text, dominate this fixture. Repeated annual graphs arise because the fixture's first twenty complete survivor elections share retirement dates and the modeled BOTH_SURVIVE scenario; exact equality is checked, not assumed as a general financial equivalence.

The observed live total, **44,692,375**, lies between the scale-20 and scale-21 fixtures. We have reproduced the failure magnitude without knowing or claiming the live cause. Live component byte counts remain to be collected.

## 6. Alternatives: evidence and tradeoffs

### A. Keep existing representation and 32 MiB bound

For scale 21: 45,334,478 raw bytes and 847,353 gzip bytes in the lab. Production correctly stops before gzip and retains the previous file. No compatibility/security change; the accepted completion remains in memory for Retry Save. Larger plans continue to fail safely. This is the current implementation while the diagnostic evidence is collected.

### B. Explicit lossless immutable interning — proposal only

A **test-only** wrapper interns exact metric/difference records, account-definition arrays and annual projection arrays, with typed within-document references. It does not deduplicate merely numerically equal decimals, drop candidates/projections, infer equivalent strategies, rerank, or calculate any financial value. Expanding it reproduces **every original JSON byte**, including order, numeric precision/scale, references, warnings, assumptions, selection and checksum metadata.

| Fixture | Existing raw | Experimental pooled raw | Existing gzip | Experimental gzip |
|---|---:|---:|---:|---:|
| Scale 2 | 15,176,328 | 2,852,677 | 679,717 | 108,547 |
| Scale 20 | 43,747,176 | 3,607,685 | 839,196 | 114,047 |
| Scale 21 | 45,334,478 | 3,649,633 | 847,353 | 114,088 |

There are five unique pooled values in these tie-heavy fixtures. This is favorable evidence, not a claimed compression ratio for the live plan. Distinct projection years or distinct exact metrics must all remain distinct; a less repetitive plan could save much less.

Required future implementation/approval boundaries:

- Introduce an explicit Exhaustive-only representation version/allowlist, retaining legacy reads and all existing weighted/SS digests and wire representations. Do not silently reinterpret version-1 bytes.
- Type-check pool references and bounds; reject invalid, external, cyclic or cross-type references; validate occurrence/election/rank/detail links and account references after reconstruction.
- Share immutable DTO data only. Reconstruct each projection's detached mutable account identities independently; never merge account identity based on names or matching balances.
- Preserve/verify integrity using an explicitly versioned canonical contract. Avoid materializing the expanded 45 MB JSON buffer merely to restore it; construct the approved DTO graph directly and stream any needed integrity digest.
- **Add explicit logical expansion/fan-out resource protections.** A small pooled document can expand to a large financial graph. The existing decoded-byte constraint must not be silently treated as unlimited logical expansion. Keep physical document limits and define reviewed expanded-object/work budgets.

No such production representation, migration or restore path has been implemented. The laboratory wrapper is rejected by the existing application codec and is not saved by the repository. It proves losslessness/size potential, not production security acceptance.

### C. Controlled decoded-limit increase — requires approval, not implemented

A hypothetical 64 MiB allowance would hold the 45,334,478-byte document with unchanged bytes/digests and existing gzip representation, provided **all other** constraints also pass. Its raw/gzip sizes are exactly those in option A. The measured fixture has 1,017,483 tokens, so its current token/numeric/depth limits would still fit.

That does not establish that the live snapshot has fewer than two million tokens, or that the actual application's heap has sufficient headroom. A limit increase alone is not a capacity strategy for arbitrarily longer/larger plans. Prefer a kind-specific reviewed policy over raising a global limit for unrelated modes; retain compressed/numeric/depth/token limits, transactional verification and decompression-bomb protection. A readable old format still needs higher bounded decode/verification budgets consistently, not a write-only exception.

Do not approve this merely from the raw total. First collect live token/dimension/component counts and assess memory. Both this change and a new representation require explicit user approval.

## 7. Timing and memory observations

Single observations on Temurin 25, Windows, Maven test heap `-Xmx8g`; not a controlled benchmark or a production-memory guarantee. No one-hour analysis ran.

| Laboratory operation | Scale 2 | Scale 20 | Scale 21 |
|---|---:|---:|---:|
| Legacy JSON serialization | 36.029 ms | 70.149 ms | 55.178 ms |
| Diagnostic scan + legacy gzip, combined | 221.893 ms | 417.552 ms | 364.778 ms |
| Legacy gzip decode + exact object reconstruction | 106.393 ms | 342.156 ms | 294.887 ms |
| Experimental parse/intern/write | 195.147 ms | 504.044 ms | 519.703 ms |
| Experimental gzip + expansion to original bytes | 77.177 ms | 111.298 ms | 131.973 ms |

The legacy oversized decoder measurements use the exact mapper in an **isolated test**, bypassing only the production document-entry gates so comparison can be measured. Production encoding/decoding is still asserted to reject the oversized original. No raised runtime policy exists. Experimental expansion is not a production DTO decode benchmark.

Observed whole-experiment heap deltas were -85,965,600, +606,820,560 and -243,334,840 bytes; GC and live objects from other phases make these **unreliable as per-option memory/peak estimates**. They are reported rather than converted into a fictitious stable memory figure. A 45 MB raw buffer alone is known, but concurrent serialization/digest buffers, JSON trees, decoded DTOs and financial graphs add materially more. The test-only tree/string experiment is not a recommended low-memory implementation. Heap/peak allocation profiling on representative live dimensions is required before approving higher budgets. Interned physical bytes can be much smaller while the restored logical financial graph remains large.

## 8. Verification

Final focused run: `target/9d2-gate3a-verified.log`: **134 tests, zero failures/errors/skips, BUILD SUCCESS, 50.307 s**. IntelliJ bundled Maven, mandatory project-local repository, 8 GiB test heap. No full Maven suite or Gate 4/5 acceptance was run.

Executed:

- `ExhaustiveCapacityInvestigationTest` 2: exact original object round-trip in the isolated lab; byte-identical expansion of the experimental wrapper; representative 45.33 MB failure; real store Retry Save logs the requested breakdown twice without recalculation or replacing the prior durable file.
- `AnalysisSnapshotSizeDiagnosticsTest` 2: exact UTF-8/component accounting and dimensions, null details, unknown-key/name/value privacy, unchanged numeric and storage bounds.
- `ExhaustiveSnapshotTest` 15; `ExhaustivePersistenceTest` 11; `RetainedProjectionCodecTest` 27.
- `AnalysisSnapshotCodecTest` 7; `AnalysisSnapshotRepositoryTest` 12; `TransactionalPlanPersistenceTest` 21.
- `LegacyWeightedSnapshotCompatibilityTest` 2; `CoupleSocialSecurityPersistenceTest` 10; `WeightedRestorationTest` 14; `WeightedNumericLengthTest` 7; `WeightedSnapshotSaveHotfixTest` 4.

Frozen weighted compatibility resources were not regenerated. No financial assertions or safety tests were weakened. A local Jackson test-prototype type-inference compilation issue was fixed before verification; it did not change production behavior.

## 9. Collecting live evidence without recalculation

1. **Do not close the live application or discard its accepted in-memory completion.** A restart can lose this particular unsaved completion.
2. The new diagnostic class and changed codec method must be loaded into that live process before Retry Save can report the new breakdown. Recompiling source alone does not replace an already loaded method.
3. If the application is already under IntelliJ Debug and supported HotSwap is available, load the rebuilt diagnostic class and updated codec method through that established debugging workflow. Do not assume a normal Run or packaged application supports live redefinition. No debugger injection, process modification, restart or external plan access was performed here.
4. Once diagnostics are present in the live process, click **Retry Save once**. Existing store retry reuses the accepted immutable completion; no financial search is invoked. Expect the original 32 MiB failure plus the new bounded `Exhaustive snapshot size breakdown` record.
5. Collect that single Summary record and the existing failure stage/size/limit record. No raw result JSON, plan file, amounts or private financial inputs are needed.
6. Compare candidate/detail/projection counts and maximum years/accounts with the fixture; inspect compact vs retained/baseline sizes, maximum numeric length, token count, annual attribution and duplicate byte totals. Decide the correction from those actual measurements.

The user confirmed no live per-component breakdown exists yet and authorized only safe diagnostics while leaving format/limits unchanged. **The user subsequently confirmed the live launch mode is Normal IntelliJ Run.** Standard IntelliJ HotSwap is therefore unavailable; an ordinary rebuild cannot load this changed method into that running session. Preserve the process. Collecting its breakdown without recalculation would require a separately approved, narrowly controlled live-process diagnostic technique (for example a temporary JDK instrumentation agent, subject to JDK/permission feasibility review). No agent was created, attached or executed, and no live-process or operating-system setting was changed. Do not restart or promise automatic instrumentation. The current deliverable is tested source diagnostics, not deployment into the existing live JVM.

## 10. Git and stop decision

All prior work is preserved. `git diff --check` passes; index remains empty. Final combined status is **five modified tracked files and fourteen untracked files**, including the preserved Gate 1/2/3 work. Gate 3A's additions are the diagnostic helper, two focused test classes and this report; its only tracked edit is the codec diagnostic hook. Combined working-tree inventory also includes the unchanged Gate 1/2/3 reports and implementation files.

No snapshot format or storage limit was changed; no financial algorithm, golden, dependency or unrelated file changed. Logs are ignored under `target/`. Nothing was staged, committed, pushed, reset or discarded.

**Stop for review and the live size breakdown. The live failure is not yet corrected: preserving the 32 MiB boundary intentionally keeps that save rejected. Approval is required before implementing either an interned snapshot representation or any decoded-size-policy change.**
