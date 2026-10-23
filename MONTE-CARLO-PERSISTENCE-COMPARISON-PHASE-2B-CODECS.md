# Monte Carlo Persistence and Comparison — Phase 2B Versioned Snapshot Codecs

## 1. Scope and implementation summary

Implemented the production **codec layer only** for eight Monte Carlo snapshot slots: single/couple × fixed/longevity × standalone/paired. They use the existing `AnalysisSnapshot`, kind dispatch, SHA-256 content digest, gzip repository, UUID/path binding, atomic writer and recovery contracts. No MC UI action writes or restores these snapshots yet. Last-success lifecycle integration, financial currency assessment and historical UI/export statuses remain Phase 2C.

No financial engine, random generator, trial scheduling, mortality distribution algorithm, tax/RMD/Roth logic, PDF exporter, navigation, plan schema, dependency or golden expectation was changed. Existing public analysis constructors still use their original statistical reducers. New internal constructors allow the codec to attach the already calculated aggregates without reducing them again.

Baseline branch: `main`; HEAD `c11e7348b7f9b64b7b4f6b4c7c9daf8738388748`. Pre-existing untracked Phase 1/2A reports and `MonteCarloCapacityProbeTest` were preserved. The latter has one supporting visibility change (`fixture` becomes package-private) to reuse its real financial fixture without duplicating it.

## 2. Explicit kinds, versions and architecture

The existing envelope remains schema 1 / fingerprint schema 1. New `MonteCarloSnapshot.version` is **1**. Unknown payload versions and inconsistent mode/shape/kind combinations are rejected. The new identifiers are:

| Shape | Standalone | Paired |
|---|---|---|
| Couple, fixed | `COUPLE_MONTE_CARLO_FIXED` | `COUPLE_MONTE_CARLO_PAIRED_FIXED` |
| Couple, sampled longevity | `COUPLE_MONTE_CARLO_LONGEVITY` | `COUPLE_MONTE_CARLO_PAIRED_LONGEVITY` |
| Single, fixed | `INDIVIDUAL_MONTE_CARLO_FIXED` | `INDIVIDUAL_MONTE_CARLO_PAIRED_FIXED` |
| Single, sampled longevity | `INDIVIDUAL_MONTE_CARLO_LONGEVITY` | `INDIVIDUAL_MONTE_CARLO_PAIRED_LONGEVITY` |

`AnalysisSnapshotCodec` selects `MonteCarloSnapshot.class` only for these explicit enum kinds. The existing families retain their previous dispatch. There is no JSON-named class loading, default typing, general polymorphic serializer or unrelated storage system. Nullable DTO fields replace runtime Optional values explicitly; missing creator fields and unknown properties remain errors under the existing strict mapper.

The outer completion time and `SnapshotContext.startedAt` must match the corresponding payload times. Content digest covers the exact compact payload and frozen outer context, including BigDecimal scale. Re-encoding a decoded snapshot reproduces the canonical raw and gzip bytes. Maps use the existing sorted-key serializer, trial lists retain source order, and reference assignment is independent of object identity or HashMap iteration.

### Source files

New production files, relative to `src/main/java/com/daviddunn/retirementplanner/`:

- `app/montecarlo/MonteCarloSnapshot.java`: immutable explicit v1 DTO graph, shape/count/reference/population/resource validation.
- `app/montecarlo/MonteCarloSnapshotMapping.java`: pure capture/reconstruction of all result families.
- `app/montecarlo/MonteCarloPlanDocument.java`: bounded frozen financial inputs using the existing allowlisted plan schema and a private strict mapper.
- `app/montecarlo/MonteCarloSnapshotNumbers.java`: MC-only scalar resource validation, including pathological short exponent tokens.
- `ui/montecarlo/MonteCarloSnapshotPresentationMapper.java`: pure mapping of frozen run/chart/report inputs; no controls, listeners, store calls, tasks or lifecycle callbacks.

Modified production files:

- `app/montecarlo/MonteCarloMortalityAnalysisResult.java`: package-private frozen-aggregate constructor; original public path unchanged.
- `app/montecarlo/MonteCarloStrategyComparisonResult.java`: corresponding frozen-summary constructor.
- `app/montecarlo/MonteCarloMortalityRequest.java`: internal reconstruction factories accepting the original table and explicit optional election, without applying compatibility defaults or regenerating mortality.
- `app/socialsecurity/results/AnalysisKind.java`: eight explicit new kinds and MC-family flag.
- `app/socialsecurity/results/AnalysisFingerprintService.java`: refuse misuse of the SS fingerprint extractor for MC.
- `app/socialsecurity/results/AnalysisCompatibilityPolicy.java`: do not describe MC results as Current through the unimplemented currency path; cloned MC snapshots remain historical/unassessed.
- `persistence/AnalysisSnapshotCodec.java`: allowlisted dispatch, timestamp checks, MC-only bounded encoding/digest output and safe numeric diagnostic field names. Existing wire paths remain intact.

New test file: `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshotCodecTest.java`. Supporting test changes: Phase 2A fixture visibility and `ui/socialsecurity/AnalysisFingerprintTest.java`, which retains the original SS assertions and explicitly tests rejection of all new MC kinds by the SS-only fingerprint extractor. This report is also new.

## 3. Complete preserved graph

### Shared context and definitions

Stored settings include trial count, signed-long seed, exact expected return/volatility and nullable inflation settings. Provenance stores source return/mortality/inflation model identities, random-stream contract, explicit statistical-definition tag and generated-versus-caller-supplied source. Caller-supplied worlds require an external frozen source fingerprint rather than being mislabeled as generated indexed worlds. Their actual paths were not retained by the existing result APIs and are not fabricated by this codec.

Frozen request data includes the exact configured plan document, both independent financial plans and labels for paired results, projection start, fixed horizon/lifetime or mortality context, original primary distribution request, couple assumptions, **complete mortality table metadata and entries**, survivor input and original start/completion times. Frozen plan documents are immutable strings within the payload, not live domain objects. Decoding uses existing explicit account/domain subtype names; unknown fields/types, duplicate fields, trailing JSON and unsafe plan identities are rejected. No current plan is supplied to restoration.

Current MC objects do not retain full annual account/tax projections or generated market paths. The codec preserves their entire retained graph and frozen configured account inputs; it does not add invented per-trial financial details. Accounts and input plans reconstruct independently. Equal account names/balances do not establish identity or merge accounts.

The statistical tag records Type-7 percentiles, completed-terminal populations, funded living annual prefixes, paired BOTH_COMPLETED terminal statistics and A-minus-B direction. Counts and original distribution data are preserved. Existing derived getters such as funding fraction still operate on those frozen counts using the same runtime contract; no new probability definition is introduced.

Fixed standalone failure summaries are existing derived getters, not cached fields in that runtime record. They continue to derive from its exact frozen failures under the v1 statistical contract. Mortality and paired cached distributions/means are restored directly. Prefix reconstruction itself performs no numerical recomputation. Original world paths absent from the source APIs are not invented from the current plan or seed.

### Fixed standalone

Every ordered trial retains index, all fourteen `ProjectionMetrics`, optional first nonpositive year and structured failure. All annual percentile entries (including absent observations), four terminal distributions, deterministic reference history, reference metrics or reference funding failure survive. Metrics include total growth/income/taxes, peak tax, assets/net worth/estate, lifetime withdrawals/conversions/RMDs/Medicare and household/primary/spouse SS. Null spouse values remain distinct from zero.

### Longevity standalone

Every trial retains exact household membership/lifetime, index, full annual asset prefix, terminal date/assets/net worth/estate/taxes or structured failure. Failures retain year, projection index, optional ages/owner, stage and required/available/shortfall amounts.

Frozen cached statistics include all annual living/funded/failed/deceased populations, optional couple breakdown, exact distributions, completion counts, terminal-year counts, earliest/latest terminal dates and full failure statistics. Restore attaches those values directly, without running an engine, distribution factory, sampler or statistical reducer.

### Paired fixed/longevity

Each pair retains its original index and shared lifetime plus separate A/B prefixes and terminal/failure outcomes. Cached data includes all joint funding states, per-side failure statistics, four terminal difference distributions, exact means, greater/equal/less counts and all annual paired populations/differences. The original A/B financial plans remain detached and their labels/order are preserved. No paired result is reconstructed by subtracting independent aggregate percentiles.

### Frozen presentation and PDF data

Standalone metadata preserves people, fan names (including fixed-mode display defaults), all fan years, optional deterministic values, mortality populations, original chart metric/composition maps, claim markers and Roth/RMD periods, first/last year, reference-incomplete state, elapsed time and original explanatory text. Pair metadata preserves its original details, labels, mode and elapsed time.

The pure presentation mapper reconstructs the existing run models. Tests compare the resulting existing PDF adapter contexts, chart data and report sections; only export time differs. No PDF layout, exporter, financial report computation, UI readiness or stale export policy was changed. This phase does not claim that historical exports are enabled in the application.

## 4. Prefix representation and security

Each `History` contains `firstYear`, nullable `source`, `sharedLength` and exact ordered `suffix` values. No history duplicates year keys for every observation. A plain A/standalone history has no source and sharedLength zero. Only side B may use source **0**, meaning side A of that same pair. Its shared length covers exactly equal leading BigDecimals, compared using `equals` so scale is significant. The remaining B values remain its own suffix. Empty, partial, shorter and financially different histories are explicit.

There are no cross-trial tables, arbitrary reference types, forward references or supported cycles. A referring A, missing source, nonzero source ID, negative/overflowing length, mismatched year origin, noncanonical zero-length reference or shared prefix longer than A is rejected. Trial indices must be contiguous in original order and counts must match settings. Lifetimes, terminal dates, failed-year endpoints and annual prefix lengths must reconcile.

MC decoding specifically disables Jackson float-to-integer coercion. Fractional references, shared lengths, indices, trial counts, seeds and versions are rejected rather than truncated to a valid integer. This is scoped to the MC ObjectReader and private frozen-plan reader; existing families retain their original configuration. A targeted diagnostic found that `ALLOW_COERCION_OF_SCALARS` alone did not reject `source: 0.5`; the new regression exercises the full envelope, including cases where truncation would otherwise produce the original valid digest.

Restoration constructs domain maps directly from these bounded vectors; it never materializes an expanded JSON document. New domain maps/outcomes are independent, while immutable BigDecimal values can be safely shared. Each restored candidate plan is separately decoded/copied; no name-based live account lookup is used.

Unchanged document limits: **33,554,432 raw bytes; 16,777,216 gzip bytes; 2,000,000 tokens; 100,000 array containers; depth 80; strings 256,000 characters; numeric tokens 4,096 characters**. Existing strict parsing, duplicate detection, trailing-token checks and payload digest verification remain enabled.

Additional MC work bounds: at most 100,000 trials, 1,000 years per history and **1,500,000 total logical annual observations**, counting both sides even when B shares stored values. Capture preflights observation totals before copying histories. Decode validates reference lengths and total work before domain-map reconstruction. Frozen context/chart collections are bounded as well. These are storage resource guards, not changes to simulation inputs or scheduling. No claim is made that every possible 100,000-trial or extreme-horizon runtime configuration fits the snapshot contract.

MC scalar validation also bounds decimal precision, scale magnitude and integer expansion to 4,096 digits, preventing a short exponent token such as a billion-digit magnitude from causing huge later formatting allocations. It never rounds or rewrites a value. Legitimate 1,070-character values round-trip exactly. Frozen plan parsing has the same scoped protection; application-wide Jackson settings and existing snapshot families are unchanged.

MC digest output and raw encoding are streamed through the existing 32 MiB ceiling. An oversized source graph cannot first allocate an arbitrarily large JSON byte array. Existing save diagnostics classify the limit failure; no new exception hierarchy was introduced. Repository files, atomic commit, backups, locks, recovery and UUID/path validation were not replaced or weakened.

When streaming stops at the raw ceiling, the diagnostic `MAX_RAW_BYTES + 1` is an exceeded-limit lower bound, not a measurement of the full rejected graph. Full sizes in the successful capacity table are measured exact sizes.

## 5. Capacity proof and measurements

Production tests execute the actual production engine and matched comparison path on the Phase 2A five-account couple and three-account single fixtures, with 50-year fixed horizons, seed 417, real taxes/SS/RMD/Roth/Medicare/spending and independent stochastic trials. Paired alternatives have different Roth conversion amounts. Longevity uses the original sampled endpoint rather than truncating to configured horizon. No trial outcome is manufactured or reused to improve compression.

Measurements include the full production envelope, complete MC payload, frozen table entries, plans, statistics and display context. Comparison tests use an explicitly frozen synthetic explanatory string; the real application details string will be captured verbatim when Phase 2C is wired. Measurements establish these fixtures, not every possible financial plan/horizon or a native UI acceptance claim.

The complete calculated matrix passed in **335.0 s** test time (Maven **5:50**). A previous development measurement also passed; the table uses the final independently calculated fixtures. The test writes synthetic accepted snapshot artifacts under ignored `target/` so final codec-only replay verifies subsequent streaming/resource checks without repeating financial calculations. No real user plan or live analysis result was accessed.

| Household | Mode | Paired | Trials | Raw bytes | Gzip bytes | Tokens | Maximum numeric characters |
|---|---|---:|---:|---:|---:|---:|---:|
| Single | Fixed | No | 5,000 | 4,657,725 | 852,693 | 198,712 | 250 |
| Single | Fixed | Yes | 5,000 | 9,574,101 | 3,389,813 | 868,943 | 299 |
| Single | Longevity | No | 5,000 | 3,275,799 | 936,702 | 336,480 | 170 |
| Single | Longevity | Yes | 5,000 | 6,023,631 | 1,828,036 | 599,413 | 208 |
| Couple | Fixed | No | 5,000 | 5,284,450 | 1,005,017 | 200,023 | 184 |
| Couple | Fixed | Yes | 5,000 | 10,359,990 | 3,810,793 | 867,788 | 303 |
| Couple | Longevity | No | 5,000 | 3,604,060 | 1,126,222 | 352,717 | 172 |
| Couple | Longevity | Yes | 5,000 | 6,677,018 | 2,172,945 | 631,155 | 172 |
| Couple | Fixed | Yes | **10,000** | **20,695,338** | **7,613,054** | **1,732,788** | **303** |

**Every measured case passes all unchanged physical limits.** The 10,000-trial paired fixed case preserves its full one million annual observations, rather than omitting histories. Compared with Phase 2A's direct test representation (27,911,139 raw bytes / 2,663,333 tokens), the production compact DTO satisfies the original token ceiling. This is not general interning of fabricated identical outcomes.

Measured final codec-only replay, with a fresh codec instance on decode and byte-identical output:

| Household/mode | Paired | Encode ms | Decode ms | Domain rebuild ms |
|---|---:|---:|---:|---:|
| Single fixed, 5,000 | No | 117 | 123 | 4 |
| Single fixed, 5,000 | Yes | 417 | 273 | 73 |
| Single longevity, 5,000 | No | 108 | 87 | 19 |
| Single longevity, 5,000 | Yes | 219 | 165 | 44 |
| Couple fixed, 5,000 | No | 145 | 136 | 6 |
| Couple fixed, 5,000 | Yes | 433 | 257 | 33 |
| Couple longevity, 5,000 | No | 125 | 84 | 38 |
| Couple longevity, 5,000 | Yes | 243 | 172 | 31 |
| Couple fixed, 10,000 | Yes | 856 | 479 | 96 |

The original calculated measurement includes independent comparisons with the actual financial result graphs; replay does not rerun engines, and verifies the saved accepted payload re-encodes exactly through the final bounded writer and decoder. These are individual timings, not averages. An identical new calculation has new plan IDs and elapsed metadata, so independently generated gzip sizes can differ slightly; deterministic re-encoding is tested on the same immutable snapshot.

Calculated proof used-heap readings ranged from **67,889,968 to 663,990,304 bytes** across cells, with summed pool peaks **319,992,968 to 737,390,624 bytes**. For the 10,000-trial case those readings were 663,990,304 and 737,390,624 bytes. They include transient source results, DTOs, parsing and reconstruction, not isolated DTO retention or native memory. Final replay timings include the later bounded-stream safeguards; heap numbers are from the independently calculated proof, not a separate replay-memory study.

Evidence: ignored `target/mc-production-codec-capacity.tsv`, `target/mc-production-codec-replay-final.tsv`, `target/mc-codec-production-capacity-final.log`, `target/mc-codec-final-proof.log`, `target/mc-codec-final-enum-regression.log`, and nine `target/mc-codec-capacity-*.json.gz` synthetic snapshots. Capacity command: `-Dtest=MonteCarloSnapshotCodecTest#realProductionCapacityMatrixAndUiMaximum -Dmc.codec.capacity=true`; replay command: `-Dtest=MonteCarloSnapshotCodecTest -Dmc.codec.replay=true`, using the normal Maven setup below. Both heavy tests are opt-in; normal regression does not recalculate this matrix.

## 6. Fidelity and compatibility tests

`MonteCarloSnapshotCodecTest` covers all eight families across seeds 417/918; counts 1/2/3/4 and production 5,000/10,000; nullable inflation and spouse data; real empty/partial failed prefixes; all-funding-failed accepted evidence; exact BigDecimal scale including long decimals; independent outcome/metric/failure/summary comparisons; trial ordering; A/B source plan isolation; equal-looking separate accounts; strict version/kind/count/digest/unknown-field/trailing/truncation rejection; invalid/cyclic/oversized references; pre-allocation expansion rejection; pathological exponent values; bounded encoding; repository reload and independent clone provenance; deterministic raw/gzip re-encoding and frozen PDF input equality.

A cached mean with an extra exact trailing-zero scale is restored unchanged: the result constructor must not replace it by recomputing the old mean. A supplied opening-death world restores with an empty history and without calling the original world callback again. These complement source inspection of pure reconstruction paths: no ProjectionEngine, optimizer, world generator or reducer is invoked during restoration.

An additional real paired longevity test has an absent **shared** survivor election while its candidates each have their own configured age 67. Restore preserves the shared absence instead of defaulting it from side A. The correction uses an internal request reconstruction factory, not a change to simulation behavior.

Frozen weighted compatibility tests preserve their original compressed bytes, raw bytes, numeric values/scales and digests. Fixture SHA-256 values remain:

- Couple: `fb4300180df4698722cbdde8b46359d21b7f0759736b3c177dc4d2e1458cc80a`.
- Individual: `f064faeeb8f061a5131d34af068816d6dcf81f15203526ee4ccaa17541d15a07`.

Existing Couple SS, Weighted restoration, Exhaustive v2 interning, numeric-length, repository and transactional tests are included in focused regression. Neither their DTO definitions nor Exhaustive interning code was modified. No frozen fixture or golden result was regenerated.

## 7. Verification results

All Maven invocations use IntelliJ bundled Maven, the project-local `.codex-m2/repository` and `-DargLine=-Xmx4g` for tests. The focused regression selection initially passed **188 run, 187 passed, zero failures/errors, one opt-in capacity skip**, Maven 45.837 s. Additional final resource tests and replay are documented with final verification below rather than counted as that earlier run.

Performance is measured on JDK `25.0.4.1+1-LTS`, Windows 11 amd64, eight Java-visible logical processors, 4 GiB maximum heap. Timings are single fixture observations, not warmed comparative benchmarks or hardware-independent guarantees. Heap measurements are used heap and summed memory-pool peak readings, not OS RSS or exact retained result size. Expected fault-injection warnings are distinguished from failed tests.

Development corrections were restricted to codec/test plumbing: explicit MC household accessor avoids the existing weighted payload accessor name, imports and test lambda capture were corrected, and independent chart display names were preserved. No financial rule or assertion was changed to obtain a passing result.

Final broad focused selection plus capacity replay passed **190 run, 189 passed, zero failures/errors, one capacity skip**, Maven **52.453 s**. After the explicit-absence request correction, MC codec tests plus frozen compatibility and replay passed **20 run, 19 passed, zero failures/errors, one capacity skip**, Maven **27.084 s**. The capacity test was executed separately, not disabled to hide a failure. An initial full-suite run was deliberately interrupted before completion to include that newly discovered fidelity regression; it is not reported as a completed suite. The complete suite was restarted on the final code.

The first completed full run reported **3,185 tests, zero assertion failures, 31 errors, 43 skips**, Maven **8:49**. All 31 errors came from SS-specific `AnalysisFingerprintTest` parameterizations/loops automatically including new MC enum constants and expecting the SS extractor to accept them. No financial mismatch occurred. The tests were extended to assert explicit rejection/invalid assessment for the new MC cases; the original seven kinds retain every original assertion. No case was disabled, filtered out or converted to a skip. These enum-wide cases now add useful coverage of the codec-only boundary. Focused MC/fingerprint/frozen/replay verification then passed **76 run, 75 passed, zero failures/errors, one capacity skip**, Maven **23.620 s**. A fresh complete suite is the final acceptance run below.

That corrected suite passed **3,185 run, 3,142 passed, zero failures/errors, 43 skips**, Maven **8:34**, before the final fractional-reference hardening. The added full-envelope coercion regression and compatibility/replay selection passed **84 run, 83 passed, zero failures/errors, one capacity skip**, Maven **30.605 s**.

### Final full-suite acceptance

**3,186 run; 3,143 passed; 0 failures; 0 errors; 43 skipped. BUILD SUCCESS. Maven total 8:53**, completed October 20, 2026 at 20:29:58 EDT. Evidence: ignored `target/mc-codec-full-suite.log`.

The 43 skips retain the repository's existing opt-in preview/private/performance/reference-data conditions, plus the four new opt-in Phase 2A/2B capacity/replay tests. Capacity generation and codec replay were executed separately as documented. The pre-existing pending-authoritative-reference skips were not changed. No assertion, financial golden, seed or trial count was weakened to obtain this result.

Full-suite command:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-DargLine=-Xmx4g' test
```

## 8. Phase 2C work and limitations

Still required and intentionally not implemented:

1. Separate last accepted result from replacement-job state; existing sessions still clear results on a new run.
2. Worker-side capture and accepted completion handoff under existing cancellation/generation/revision checks.
3. MC-specific effective-input fingerprints, algorithm/reference compatibility assessment and Current/Outdated UI. The SS fingerprint extractor explicitly refuses MC kinds; the compatibility policy cannot silently mark these codec-only snapshots Current.
4. Application-owned asynchronous load/publication guards, plan switching and dialog lifecycle integration.
5. Durability status/Retry Save/Memory Only UI, timestamp presentation and unsaved/legacy plan behavior.
6. Real application Save As acceptance and source/destination currency reassessment. Codec/repository cloning preserves original data and provenance, but no MC UI claims snapshots were copied or saved.
7. Historical PDF readiness/status notices and native restart/Outdated acceptance. This phase preserves exporter inputs; it does not change PDF behavior.

There is no persistent analysis browser, automatic simulation, UI restoration, analysis scheduler replacement, history retention or cross-saved-run statistical pairing in this phase. Caller-supplied source fingerprints must be provided by the caller because original paths were not retained by those APIs. Larger cases exceeding document or logical-work bounds fail safely; no values or trial histories are removed to fit.

## 9. Git status and review boundary

Final changed/untracked inventory and verification results are recorded below. All generated logs, synthetic capacity snapshots and test PDFs remain ignored under `target/`; no `.codex-m2` artifacts or real financial files are included. No staging, commits, pushes, resets or discards were performed.

`git diff --check` passes. No financial engine/reference/golden resource, dependency/build file, existing view/dialog/controller or PDF exporter is modified. The new UI-package file is a pure presentation-model codec adapter. Tracked diff: eight files (seven production, one test); the untracked inventory has ten expected files, including the three pre-existing Phase 1/2A artifacts. Tracked statistics do not include the new codec files.

Final short status:

```text
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityAnalysisResult.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityRequest.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonResult.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisFingerprintService.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisKind.java
 M src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisFingerprintTest.java
?? MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-1-AUDIT.md
?? MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-2A-CAPACITY.md
?? MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-2B-CODECS.md
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPlanDocument.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshotMapping.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshotNumbers.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloSnapshotPresentationMapper.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloCapacityProbeTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshotCodecTest.java
```

Stop after Phase 2B for review. Do not begin Phase 2C without authorization.
