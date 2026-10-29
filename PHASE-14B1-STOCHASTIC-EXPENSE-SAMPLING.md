# Phase 14B1 - Stochastic Recurring Expense Sampling Protocol

## Executive summary

Implemented pure, versioned, scenario-local recurring-expense sampling infrastructure. It produces independent annual mean-preserving lognormal multipliers and supports strict paired correspondence without financial execution, UI or persistence integration.

**No existing projection or Monte Carlo caller uses this sampler.** Expense, ProjectionEngine, ProjectionEvaluationContext, market/inflation/mortality generators, world records, analyzer execution, fingerprints, financial golden resources and saved-result formats are unchanged. The only modification to an existing production class adds two new dimension constants.

All 195 focused tests passed. The complete suite passed with 3,679 passed, 45 skipped, zero failures/errors (3,724 total). All 12 requested sampling benchmark workloads completed successfully. Phase 14B2 financial integration requires separate approval.

## Baseline and source verification

- Branch `main`, HEAD `f4215bc08ddb274a7321011987a4b71fb0edfb5c`; index empty.
- Pre-existing modified `.idea/encodings.xml` and untracked `PHASE-14-RECURRING-EXPENSE-STOCHASTIC-ARCHITECTURE.md` preserved and excluded from the implementation inventory.
- Read AGENTS.md, the approved Phase 14 audit and completed Phase 14A report. Verified Phase 14A's GENERAL/HEALTHCARE/NONE expense enum, recurring-only NONE validation, existing JSON and single escalation branch.
- Inspected actual Expense, GrowthCategory, ProjectionEngine, ProjectionEvaluationContext, MonteCarloRandomStreams, ScenarioGenerator, InflationGenerator, WorldGenerator, World, ComparisonWorld, Analyzer, StrategyComparisonAnalyzer and StrategyComparisonRequest.
- Existing market V1 still uses its original java.util.Random formula. Existing new-dimension registry remains household mortality 1, primary mortality 2, spouse mortality 3 (reserved), general inflation 4 and healthcare inflation 5 (reserved). Dimension 6 was available.
- Existing standalone and comparison execution builds contexts only with its existing inflation/lifetime/horizon overrides. No conflicting integration change was necessary. Existing algorithm/provenance version identities remain unchanged.

## New public contracts

### ExpenseSamplingSpecification

Immutable, detached record containing expense type, annual amount, optional start/end dates, effective escalation policy and annual relative standard deviation. `from(Expense, BigDecimal)` copies only these values, never a plan graph, description, owner, UUID or JavaFX object.

- Volatility is decimal relative standard deviation, inclusive range [0, 1]. Null or exact zero means disabled. Negative and above-one values are rejected before floating-point conversion.
- Enabled sampling requires a nonnegative recurring expense. Negative legacy amounts and one-time records remain representable when disabled; no existing Expense validation is changed. A zero amount is permitted; financial integration must retain its exact zero rather than fabricate spending.
- Dates must be ordered. Type, amount and escalation are required.
- BigDecimal cannot represent NaN/Infinity; these fail at parsing. Generated normal/multiplier nonfinite values and nonpositive floating-point results fail explicitly.
- Sampling-only numeric canonicalization maps zero to BigDecimal.ZERO and other values to stripTrailingZeros(), then encodes BigDecimal.toString(). Thus 1000.0000 and 1E+3 share a sampling key, while original plan values/scales are untouched. Canonical scientific notation is allowed and bounded; no enormous toPlainString expansion is used.
- A decimal precision bound of 4,096 digits limits detached request size. This is a new sampling bound, not a change to persisted input or snapshot limits.

### MonteCarloExpenseGenerator

Immutable prepared sampler, safe for concurrent generation calls. Its constructor takes a bounded list of specifications, snapshots their definition keys/parameters, and exposes immutable `occurrencesInInputOrder()` bindings. Disabled entries have empty bindings.

`generate(masterSeed, scenarioIndex, firstYear, lastYear)` returns Optional.empty() if all entries are disabled. Otherwise it materializes one immutable ProjectionExpensePath. `generatePaired(other, seed, scenario, first, last, otherFirst, otherLast)` applies the same protocol on both sides, including different horizons. It does not require, modify or create a shared financial world.

All SHA-256 objects, buffers and result lists are call-local. There is no global RNG, retained scenario cache, mutable generator inside a path, or background executor. No new dependency was added.

### ProjectionExpensePath

Domain-only immutable value container. `Occurrence` is the full lowercase 64-hex-character SHA-256 correspondence key plus a nonnegative duplicate ordinal. The path stores a dense year list for each enabled occurrence and supports `multiplier(occurrence, absoluteYear)` and `requireCoverage`.

Missing occurrences/years, invalid horizons, empty paths, wrong row lengths and nonpositive multipliers are rejected explicitly; there is no fallback multiplier. Constructor and accessors defend nested collections. Repeated lookups return the same immutable BigDecimal value, suitable for same-year financial retries in a later phase.

Bounds: at most 1,000 input occurrences, 1,000 years and 100,000 enabled occurrence/year entries per path. Space is O(E*Y) per scenario plus O(E) prepared metadata. Paths are not retained for all scenarios. Empty disabled generation allocates no expense path, digest or random buffers. Two enabled paired sides currently materialize two bounded paths; their equal entries are reproduced, not stored as an unbounded shared cache.

## Statistical model and derivation

For decimal relative annual standard deviation v:

```
s2 = log1p(v*v)
mu = -s2/2
s = sqrt(s2)
M = exp(mu + s*Z), Z ~ N(0,1)
E[M] = exp(mu+s2/2) = 1
Var[M] = (exp(s2)-1)*exp(2*mu+s2) = v*v
```

This is an independent annual **level** multiplier, not an inflation rate or compounded random walk. No multiplier depends on the previous year's shock. Escalation, partial-year and survivor rules remain future financial integration responsibilities. GENERAL, HEALTHCARE and NONE can all have independently specified sampling volatility.

StrictMath.log1p/sqrt/exp/log/cos implement statistical transforms. Only finite strictly positive multipliers are converted with BigDecimal.valueOf, with no subsequent decimal normalization or financial arithmetic here. A test-only/package-visible transform boundary rejects injected nonfinite normals, overflow and underflow-to-zero rather than clipping or substituting values.

There is no artificial tail cap, floor, winsorization or sample moment normalization. The ideal model is unbounded above; as with any finite-bit pseudorandom implementation, its reachable uniforms/normals have finite resolution. Extremely small nonzero volatility can round to unit multipliers at the existing double statistical-transform precision; it is not reclassified as disabled. Financial arithmetic is reserved for BigDecimal in the next phase.

## Frozen random protocol

Dimension **6**, dimension version **1**, model identifier **INDEPENDENT_LOGNORMAL_EXPENSE_LEVEL_V1**, correspondence version **1**. These constants reserve a new dimension without changing any existing sequential stream bytes. Expense V1 uses its own keyed protocol, not `MonteCarloRandomStreams.create`'s counter consumption.

All integers are signed two's-complement, explicitly big-endian. `str(s)` is int32 UTF-8 byte length followed by exactly those bytes. Fixed tags are ASCII. Definition fields are in this exact order:

```
definition = "RPEX" || int32(1)
             || str(expenseType.name())
             || str(normalizedAnnualAmount.toString())
             || str(startDate ISO-8601 or "")
             || str(endDate ISO-8601 or "")
             || str(escalationPolicy.name())
             || str(normalizedAnnualVolatility.toString())
correspondence = SHA-256(definition)                 // all 32 bytes

header = "RPMC" || int32(1) || int64(masterSeed)
         || int32(nonnegativeScenarioIndex)
         || int32(6) || int32(1)
         || int32(32) || correspondence || int32(duplicateOrdinal)
key = SHA-256(header)                               // 68-byte header
block = SHA-256(key || int32(absoluteCalendarYear))  // 36-byte input
```

The first two big-endian int64 words of block each yield:

```
U = ((word >>> 12) + 0.5) * 2^-52
Z = sqrt(-2*log(U1)) * cos(2*pi*U2)
```

Both uniforms are strictly inside (0,1). Only the cosine result is used; no cached sine or mutable draw cursor. This is the existing inflation normal convention, applied to new independent keyed bytes. The remaining digest bytes are unused random output, not a truncated sampling identity. Full definition digests enter the root key; no Java hashCode, object identity, description or plan/projection UUID enters sampling identity.

## Correspondence and duplicates

Exact effective financial definition matches include type, normalized amount, start date, end date, selected escalation and volatility. There is no description-only or approximate matching. A changed amount, date, escalation or volatility breaks correspondence. Central economic rate values are not duplicated into the expense key; future expected-dollar spending still comes from each plan's own approved escalation calculation.

Duplicate ordinals are zero-based first-seen counts **within the same complete definition**, not list positions. Unique financial definitions retain their draws after reordering or unrelated additions. Duplicate 0 on A pairs with duplicate 0 on B; duplicate 1 pairs with duplicate 1, independently of duplicate 0. Extra unmatched occurrences have distinct streams. Disabled entries have no stream and consume no ordinal of an enabled definition.

These are exchangeable sampling correspondences, not durable entity or historical identity. Reordering indistinguishable duplicates cannot preserve an unknowable user-intended identity. Editing an expense breaks its pairing; durable IDs and relaxed correspondence remain deferred. Equal sampling keys never create projection/history associations.

## Seeded regression vectors

Synthetic reference: recurring 1000.0000, start 2027-01-01, no end, GENERAL, volatility 0.2500. Its canonical amount is `1E+3`, volatility `0.25`.

Definition bytes, hex:

```
525045580000000100000009524543555252494e470000000431452b330000000a323032372d30312d3031000000000000000747454e4552414c00000004302e3235
```

Full correspondence digest:

```
ae24c2ab949dbd6df768173d5b096a41cb009beefbdb9f6b8a13eeaaf5b88935
```

| Seed | Scenario | Duplicate | Year | Exact Java BigDecimal multiplier |
|---:|---:|---:|---:|---|
| 417 | 0 | 0 | 2027 | 0.7319083882747449 |
| 417 | 0 | 0 | 2056 | 1.153103762542285 |
| 417 | 0 | 1 | 2027 | 0.7458950207931494 |
| 417 | 0 | 1 | 2056 | 1.188382935464238 |
| 417 | 7 | 0 | 2027 | 1.181267366556483 |
| 417 | 7 | 0 | 2056 | 0.7299399035940793 |
| 417 | 7 | 1 | 2027 | 0.7900043297929266 |
| 417 | 7 | 1 | 2056 | 0.5818707134078651 |
| -417 | 0 | 0 | 2027 | 0.8004549847406232 |
| -417 | 0 | 0 | 2056 | 0.9211853954254114 |
| -417 | 0 | 1 | 2027 | 0.9137002315713875 |
| -417 | 0 | 1 | 2056 | 1.0711113206514076 |
| -417 | 7 | 0 | 2027 | 1.1379372178769283 |
| -417 | 7 | 0 | 2056 | 0.9995487411742241 |
| -417 | 7 | 1 | 2027 | 0.7753394310880257 |
| -417 | 7 | 1 | 2056 | 1.1465773909992676 |

For seed 417/scenario 0/duplicate 0/year 2027, block is `1bd8194881c7030b5766f08bf3d4adbfbec8aa125a34977e55988c144c0e2400`; U1 is exactly the double `0.10876615543545765`, and Z is approximately -1.1444514649585678.

Independent .NET byte encoding/SHA-256/Math calculations cross-check six signed-seed/scenario/duplicate/year vectors. Statistical values permit small independent-library rounding differences; a separate 16-vector exact Java StrictMath/BigDecimal fixture freezes actual decimal value and scale. Algebraic Z=0 expectations are 1/sqrt(2) at v=1 and 2/sqrt(5) at v=0.5. No old financial golden was regenerated. Reference helper and full independent outputs are under ignored `target/phase14b1`.

## Disabled-path and existing compatibility evidence

- Null/zero volatility creates empty bindings and Optional.empty(), without expense digests at generation, random draws, path allocation or multiplication by BigDecimal.ONE.
- Existing code has no new sampler calls or optional fields. Consequently disabled and enabled standalone sampler requests cannot change existing financial execution.
- Tests interleave expense generation into a partially consumed existing inflation stream and verify its exact remaining bytes are unchanged.
- A synthetic plan's JSON and every deterministic annual result remain exact before/after standalone enabled sampling. Legacy scale-sensitive expense JSON/input/analyzer digests pass unchanged.
- Existing market/fixed and longevity financial regression hashes, inflation vectors, mortality/world vectors, paired-world behavior, all eight numerical fingerprint contracts and analyzer-family fingerprint tests pass in the focused suite.
- No Expense field, persistence schema, projection/context builder, UI control, provenance artifact or algorithm version changed.

## Statistical results

100,000 scenarios per volatility, seed 417, independent annual keyed draws:

| Volatility | Expected mean | Observed mean | Expected variance | Observed variance | Maximum |
|---:|---:|---:|---:|---:|---:|
| 0.25 | 1 | 0.999943909767 | 0.0625 | 0.062582146358 | 3.031139656949 |
| 1.00 | 1 | 1.002645492127 | 1 | 1.040411130986 | 35.930798618875 |

Tolerances were specified from six standard errors, not tuned to observed means: mean error 6*v/sqrt(N); variance error approximately 6*sqrt((centralMoment4-v^4)/N), with centralMoment4=(1+v^2)^6-4*(1+v^2)^3+6*(1+v^2)-3. This is a fixed-seed diagnostic, not a proof of exact finite-sample moments.

All samples were strictly positive. Separate tests cover normal extremes, an unclipped multiplier exceeding 800 from a valid extreme uniform block, and injected nonfinite/overflow/underflow failures. Fixed-seed duplicate/year covariance checks use 20,000 scenarios with a predeclared absolute 0.06 tolerance; reproducibility/concurrent-instance tests verify no execution-order state.

## Performance and memory

Opt-in benchmark `-Dexpense.sampling.benchmark=true -Dtest=MonteCarloExpenseGeneratorTest,MonteCarloExpenseSamplingBenchmarkTest`, 4 GiB heap, Java 25.0.4.1, 8 available processors. Warmed the transforms, materialized/discarded one scenario at a time, consumed one multiplier to prevent unused-result elimination. No projection or analyzer executed.

| Expenses | Years | Scenarios | Multipliers | Seconds | Summed heap-pool peak MiB | Post-GC heap delta MiB |
|---:|---:|---:|---:|---:|---:|---:|
| 10 | 30 | 1,000 | 300,000 | 0.090 | 37.97 | -0.005 |
| 10 | 30 | 10,000 | 3,000,000 | 0.619 | 165.40 | 0.000 |
| 10 | 60 | 1,000 | 600,000 | 0.111 | 87.39 | 0.000 |
| 10 | 60 | 10,000 | 6,000,000 | 1.212 | 165.39 | 0.000 |
| 50 | 30 | 1,000 | 1,500,000 | 0.304 | 169.46 | 0.000 |
| 50 | 30 | 10,000 | 15,000,000 | 2.640 | 167.46 | 0.000 |
| 50 | 60 | 1,000 | 3,000,000 | 0.532 | 167.51 | 0.000 |
| 50 | 60 | 10,000 | 30,000,000 | 5.518 | 167.54 | 0.000 |
| 100 | 30 | 1,000 | 3,000,000 | 0.624 | 169.55 | 0.000 |
| 100 | 30 | 10,000 | 30,000,000 | 5.681 | 167.58 | 0.000 |
| 100 | 60 | 1,000 | 6,000,000 | 1.032 | 165.69 | 0.000 |
| 100 | 60 | 10,000 | 60,000,000 | 10.605 | 165.71 | -0.023 |

Peaks are summed JVM heap-pool high-water observations, including temporary garbage, not isolated live-path footprints; pool peaks need not be simultaneous. Post-GC deltas are approximate/noisy (negative values are possible), and show no accumulated retained scenario paths. A 100-expense/60-year path has 6,000 entries. No exact wall-clock performance assertion is used, and the 158.4-million-cell benchmark is opt-in rather than normal-suite work. No incompatible optimization was needed.

## Automated verification

All Maven runs use IntelliJ bundled Maven, the project-local `.codex-m2/repository`, and `-DargLine=-Xmx4g`.

| Run | Total | Passed | Skipped | Failures | Errors | Elapsed |
|---|---:|---:|---:|---:|---:|---|
| Existing focused baseline | 100 | 100 | 0 | 0 | 0 | 17.679 s |
| Initial focused implementation | 193 | 193 | 0 | 0 | 0 | 24.276 s |
| Explicit benchmark and then-current new tests | 15 | 15 | 0 | 0 | 0 | 46.402 s |
| Final focused implementation | 195 | 195 | 0 | 0 | 0 | 18.796 s |
| Complete Maven regression | 3724 | 3679 | 45 | 0 | 0 | 10:29 min |

Final focused selection: MonteCarloExpenseGeneratorTest, MonteCarloRandomStreamsTest, MonteCarloInflationTest, MonteCarloInflationRegressionTest, MonteCarloWorldGeneratorTest, MonteCarloPairedWorldTest, MonteCarloStrategyComparisonTest, MonteCarloNumericCompatibilityTest, ExpenseEscalationCompatibilityTest, ExpenseEscalationTest, ProjectionEngineTest, ProjectionInflationPathTest, AnalysisFingerprintTest and MonteCarloFoundationTest.

Logs: `target/phase14b1/baseline.log`, `focused.log`, `focused-final.log`, `benchmark.log`, `full.log`. No financial golden changes, assertion weakening, new dependencies or test exclusions. The new opt-in benchmark skipped in the normal full suite; it was explicitly executed successfully above. The remaining 44 skips retain their existing policies: 39 property-gated performance/capacity/user-plan/visual cases, one explicitly disabled multi-minute production mortality benchmark and four worksheet parameter cases awaiting authoritative guidance. Exact case names/reasons are recorded in `target/phase14b1/skipped-tests.txt`.

The previously documented Phase 14A intermittent worker-cleanup assertion is unchanged and passed in this complete run. No unrelated intermittent failure occurred in Phase 14B1 verification. Existing financial golden and seeded regression assertions passed without regenerated expectations.

`git diff --check` passed after the full suite. Branch and HEAD remain unchanged and the staging index is empty. No staging, committing, pushing, resetting, cleaning or discarding occurred. No native UI preview is needed: no UI changes.

## Exact file inventory

Modified production:

- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloRandomStreams.java` - dimension 6/version 1 constants and explanatory comment only; old stream implementation untouched.

New production:

- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/ExpenseSamplingSpecification.java`.
- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloExpenseGenerator.java`.
- `src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionExpensePath.java`.

New tests:

- `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloExpenseGeneratorTest.java`.
- `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloExpenseSamplingBenchmarkTest.java`.

New report: `PHASE-14B1-STOCHASTIC-EXPENSE-SAMPLING.md`.

Excluded and preserved: `.idea/encodings.xml`, existing Phase 14 audit. Diagnostics, benchmarks and reference helpers under ignored `target/phase14b1` are not intended commit files. No existing test file was changed.

## Deferred Phase 14B2 integration and risks

1. Add approved optional volatility persistence/default omission without changing legacy input documents or digests. The present standalone specification is not a plan field.
2. Bind occurrences to the frozen accepted expense list; never use current UI state, description or household UUID as random correspondence. Preserve first-seen duplicate limitations.
3. Add optional expense paths to scenario/world/context plumbing only under separate integration authorization. All context builders must preserve them, and caller-supplied worlds must validate enabled coverage/provenance explicitly.
4. Apply the fixed annual level multiplier once after expected escalation and before existing proration/death adjustments; reuse it for Medicare retries and all downstream financial funding calculations. Never sample inside ProjectionEngine.
5. Keep deterministic/longevity-weighted analysis free of expense randomness. Zero/missing volatility must take the exact old financial arithmetic branch without multiplying by one; zero-amount handling must preserve existing representation.
6. Explicitly version enabled-model provenance, fingerprints, historical payload compatibility and original-context disclosures before executing or saving enabled financial results. Existing versions must not silently acquire new meaning.
7. All eight Monte Carlo identities, paired financial execution, supplied-world failures, original PDFs, downstream cash/tax/Roth/RMD outcomes and cancellation require integration tests in that later phase. Passing this pure sampler does not establish those unimplemented behaviors.
8. Key matches establish sampling correspondence only. They do not establish historical association, durable identity or financial freshness. Cryptographic collisions remain the ordinary full-SHA-256 assumption.
9. Memory/time scale with E*Y per scenario and N*E*Y total work. The 100,000-entry path bound must fail explicitly; do not silently drop expenses or retain every scenario path. No automatic cache, persistence, executor or garbage collector is introduced.

No additional policy gate arose within this bounded infrastructure implementation. Stop for review and approval before financial integration; do not begin Phase 14B2.
