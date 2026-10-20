# Phase 9C — Weighted snapshot numeric-length correction

## Outcome and production evidence

The supplied production evidence establishes a **snapshot parser constraint failure**, not a financial search failure: written-file verification rejected Jackson number length 1,070 against the former limit 1,024. The successful 5,184-candidate calculation remained in memory. Its 29,978,548-byte JSON and 1,784,251-byte gzip were within the existing document limits.

Implemented a snapshot-only, bounded numeric token limit of **4,096 text characters**, with consistent encoding/decoding checks and structural-path diagnostics. Financial values and BigDecimal scale are preserved exactly. No financial algorithm, output rounding, schema, fingerprint, algorithm-compatibility identifier, plan identity or atomic replacement rule was changed.

**The supplied exception contains no field path. It cannot, by itself, prove which field in that specific live result was encountered first.** The exact deterministic reproduction field is `/payload/couple/entries/0/aggregate/expectedPvAfterTaxEstate`. The new diagnostic identifies the actual field families in the production snapshot when it is encoded or loaded; no private result file was accessed here. Do not interpret the reproduction path as an observation of the user's unavailable result graph.

## Numeric origin and precision audit

Verified source chain:

1. `domain/projection/ProjectedPortfolio.java`, `applyGrowth`, lines 199–230: this existing operation, used by the deterministic precision fixture, multiplies account balances by the return rate and retained assets by `1 + rate` without a MathContext. Scale can accumulate across calls. **This is not a claim that normal `ProjectionEngine` yearly growth uses this helper**: its `calculateInvestmentGrowth` rounds aggregate annual growth to two places and calls `withGrowth`, whose account shares use 12-place division. The model already has intentional rounding at particular stages; it has no blanket snapshot-output precision reduction.
2. `domain/estate/EstateAtSecondDeathCalculator.java`, `calculate`: takes investable assets, heir tax and after-tax estate from the required projection year without a snapshot-specific precision reduction.
3. `app/socialsecurity/LongevityWeightedIntegratedStrategyEvaluator.java`, lines 87–91, and `LongevityWeightedContinuationEvaluator.java`, lines 76–80: exact estate × discount-factor and probability × estate/PV products are summed without an additional MathContext. The discount-factor calculator's bounded working precision does not round the later exact products.
4. `LongevityWeightedIntegratedStrategyResult.expectedInvestableAssetsAtSecondDeath` also sums exact probability × investable-asset products.
5. `LongevityWeightedStrategyAggregate.from` copies those values into the aggregate; `WeightedSnapshots.Couple.from`/`Entry.from` retain that immutable aggregate without transformation.
6. Jackson writes these BigDecimals as JSON numbers, retaining their decimal representation/scale. The same mapper is used for payload digesting, encoding and restoration. A finite decimal with a long fractional tail can therefore have an ordinary financial magnitude and a thousand-character token.

Affected possible aggregate paths include `expectedPvAfterTaxEstate`, `expectedNominalEstateAtSecondDeath`, `expectedInvestableAssetsAtSecondDeath`, `minimumNominalScenarioEstate`, `maximumNominalScenarioEstate` and `totalEvaluatedProbability`, under entries or the baseline. Differences, retained scenario values and frozen historical comparison amounts can also carry exact long decimals. No field is coerced to a floating-point representation.

The fixture proves that supported exact operations can accumulate intermediate precision/scale reaching the durable result. The exact origin within the unavailable live graph is not established by its exception alone. Its trailing digits are nevertheless part of the existing output contract even if unnecessary for a currency display. There is no approved rule establishing that they may be discarded. It is therefore financially safer to preserve them and correct the storage constraint than to add rounding, `stripTrailingZeros`, a new MathContext or string conversion. The evidence does not establish an incorrect financial amount or a need to change calculation precision.

## Deterministic reproduction

`WeightedNumericLengthTest.compounded1070` exercises the **existing** `ProjectedPortfolio.applyGrowth` 62 times with a synthetic opening retained balance `10000000.00` and rate `0.00000000000000001`, then performs the same exact probability-weight multiplication used by the weighted evaluator. Probability `0.123456` yields scale 1,062 and a **1,070-character JSON decimal token**, without padding or rounding. These are fixed synthetic test inputs, not new defaults or advice.

Using probability `0.1234560` preserves an additional valid scale digit and yields a 1,071-character text token. An independent Jackson factory configured with the original limit reproduces the captured **`Number value length (1070) exceeds the maximum allowed (1024)`** at the aggregate path above.

Jackson's floating-point constraint count excludes the decimal point. The report and new accepted-token diagnostics use full token text length, including punctuation; thus a reported Jackson decimal length 1,070 can correspond to 1,071 characters. Explicit text-length checking makes the new upper bound unambiguous, including signs/exponent syntax.

The full-scale fixture preserves the existing 5,184 election identities, ordered rankings, baseline, equivalence groups/representative identities, coverage/work metadata, report context, historical comparisons and per-strategy detail ranks. Four aggregate fields per candidate carry long exact decimals. The measured fixture is **28,239,515 raw bytes / 372,872 gzip bytes** and round-trips with exact envelope/payload equality. Its repeated synthetic decimal tails compress more than the live result; it is a structural/precision regression fixture, not a measured reproduction of the user's data or compression ratio. No expensive production search was run to build it.

## Correction and bounded protections

Only `persistence/AnalysisSnapshotCodec.java` changes production behavior in this correction:

- `MAX_NUMBER_LENGTH`: 1,024 → **4,096**. This is a bounded fourfold mantissa/token allowance with headroom for the evidenced 1,070 count, not an unlimited precision policy. Inputs above it still fail safely.
- The codec's Jackson factory uses that same limit for all parsing passes.
- A shared `checkDocument` scans both newly encoded JSON and decompressed input before object/tree reconstruction. It checks full numeric token characters as well as the original token/array limits. Consequently encoding cannot produce a snapshot the same codec rejects solely for token length. Temporary-file verification and cross-session restoration already call the same decoder.
- A failure before compression has a known raw size and compressed size -1; this is reported accurately. It never reaches destination replacement.
- Decoding independently rejects over-limit externally supplied files. Verification-failure tests bypass normal encoding only by modifying a disposable temporary file through the existing fault seam.

Unchanged protections: raw JSON **33,554,432 bytes**, gzip **16,777,216 bytes**, **2,000,000 tokens**, **100,000 arrays**, depth **80**, string length **256,000**, strict duplicates/unknown properties/creator/null/enum/coercion checks, digest validation, slot binding, compatibility checks, file locks, sibling temporaries, force/close, recognized backups, validated atomic replacement and no destructive non-atomic fallback.

The file-size cap still bounds the total numeric material. The per-token cap prevents unbounded individual BigDecimal tokens. This remains local snapshot parsing; application-wide Jackson defaults and plan JSON parsing are untouched.

## Structural diagnostics and privacy

For numbers longer than the former 1,024 threshold, INFO diagnostics report only the structural JSON path family, maximum text length, permitted length and raw byte size. For example:

```text
Exact snapshot numeric tokens: JSON path=/payload/couple/entries/*/aggregate/expectedPvAfterTaxEstate maximumLength=1070 permittedLength=4096 rawBytes=...
```

Array indices are grouped to avoid 5,184 repeated messages per field per scan. Exceptions identify the concrete index/path when parsing fails, and preserve the original nested Jackson cause. No numeric token, financial amount or serialized contents are logged. Path names are restricted to known DTO/financial record component names; unrecognized object field names are redacted. Existing full stack/cause logging and retained original exceptions from the preceding hotfix remain in force. All logs use the existing JDK logger/Run console; no remote diagnostics or new log-file service was added.

## Compatibility and retry

The durable schema, JSON numeric representation and payload digest algorithm are unchanged. Old valid snapshots remain readable. New exact values previously rejected at 1,024 can now be persisted/restored without conversion. Older application builds retain their old parsing limit and may reject such files; downgrading the application is not a way to recover them. No algorithm-version bump or automatic Outdated classification is justified because no financial semantics changed.

Retry Save still uses the same accepted in-memory completion, its original plan binding, timestamp, frozen assumptions and result values. The test injects an initial disk failure, confirms previous bytes are unchanged, then retries and verifies the new durable result plus the original backup without invoking a projection/search engine. There is no automatic analysis rerun.

## Files changed in this correction

1. `src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java` — bounded limit, shared checks and safe structural diagnostics.
2. `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedNumericLengthTest.java` — new precision, boundary, full-size, diagnostic and retry coverage.
3. `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedSnapshotSaveHotfixTest.java` — the previous injected 1,100-digit failure is now below the approved cap, so its rejection fixture now exceeds 4,096; asserts early safe encoding rejection rather than waiting for temporary-file verification. No financial assertion was relaxed.
4. `src/test/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotRepositoryTest.java` — inject an over-limit token into the written temporary file; verify concrete path and preservation of primary and backup.
5. `PHASE-9C-WEIGHTED-NUMERIC-LENGTH-FIX.md` — this report.

All pre-existing Phase 9A/9B/9C/hotfix work is preserved. `WeightedSnapshots`, financial engines, controllers, result views, PDF adapters and `AtomicFileWriter` were inspected as relevant and not modified in this correction.

## Tests

Coverage includes:

- Exactly 1,024, 1,070 and 4,096 text characters: exact BigDecimal equality **and scale**, plus complete snapshot equality.
- Exact old Jackson failure at length 1,070 from production-style compounding/weighting.
- 4,097-character rejection during encoding and independent decoding.
- Concrete path diagnostics, grouped accepted-token diagnostics and absence of financial token values in logs.
- Full 5,184-candidate high-scale save, temporary verification and fresh-repository reload.
- Failed disk save followed by Retry Save of the same accepted completion without recalculation.
- Over-limit temporary-file verification leaves primary **and backup bytes unchanged**.
- Existing compatibility, corruption, checksum, unknown-schema, atomic-unsupported, write-fault, recovery and restoration safeguards.

Initial focused run: **27 run, one failure** in the new reproduction test because it expected Jackson to count the decimal point. The financial value and codec round-trips passed; the test was corrected to distinguish Jackson's count from text-token length, and an exact captured-message reproduction was added. No financial behavior or precision was changed to satisfy the test.

Intermediate focused run: **68 passed**, no failures/errors/skips (`target/numeric-focused-final.log`). Final focused run including the full high-scale fixture: **69 passed**, zero failures/errors/skips, 32.041 seconds (`target/numeric-focused-complete.log`). Classes: `WeightedNumericLengthTest`, `WeightedSnapshotSaveHotfixTest`, `AnalysisSnapshotCodecTest`, `AnalysisSnapshotRepositoryTest`, `AnalysisResultStoreTest`, `WeightedRestorationTest`, `TransactionalPlanPersistenceTest`.

Verification uses IntelliJ bundled Maven, JDK 25 and the required project-local repository. Focused final and full-suite commands use `-DargLine=-Xmx8g`; full suite uses `test` without filtering. **Final full regression: 3,028 run, 2,991 passed, zero failures/errors, 37 skipped; BUILD SUCCESS**, 8 minutes 26 seconds, completed October 19, 2026 at 19:01:17 EDT (`target/numeric-full.log`). Existing financial, seeded Monte Carlo, weighted, UI, PDF, identity, corruption/recovery, lifecycle and wizard-equivalence coverage passed. No failing test was disabled or golden regenerated. The 37 skips are the unchanged existing opt-in/disabled checks. No new UI layout was introduced, so no additional visual preview was required.

## Keeping an already completed live result

Do not restart a process holding the only accepted result merely to install this build. Running JVMs do not automatically adopt source changes. The application itself offers no claim that a failed save is durable.

For an already debugger-attached session, an experienced local developer can pause **Retry Save** before `repository.save` and inspect the existing `JsonAnalysisSnapshotRepository` instance's `codec.mapper.getFactory()`. Jackson 2.20 exposes `streamReadConstraints().rebuild()` and `setStreamReadConstraints`, verified against the local installed jar. Changing **only that repository factory's** number limit to 4,096 permits the old verifier to retry the accepted result while preserving all other factory limits:

```java
// factory refers to that specific repository codec's existing JsonFactory, not a global default.
factory.setStreamReadConstraints(
        factory.streamReadConstraints().rebuild().maxNumberLength(4096).build());
```

Resume the pending save; do not click Run Analysis. This optional debugger procedure was **not performed on the live application here** and requires access to its actual repository instance; ordinary hot swap may not support the added methods in this source change. Do not guess at a different mapper, change global defaults, disable verification, or raise other limits. If another error occurs, keep the process open and retain its diagnostic. After a verified Saved status, the updated application can reopen the durable snapshot and log its actual long-field families without recalculating. No live process or user cache was modified by this task.

## Final verification and Git

Branch/HEAD remain `main` / `0fa51e012a5683bc9e82e30ce57f175146134378`; initial expanded status was 12 tracked modifications and 35 untracked files, all preserved. Final expanded status is **12 tracked modifications and 37 untracked files**, including the new numeric test and this report. The other three files in this correction's inventory already belonged to the untracked prior work. The pre-existing tracked diff remains 12 files, 429 insertions and 25 deletions; it does not represent this correction alone.

`git diff --check` passes, and the new/untracked changed files were separately checked for trailing whitespace. The staged diff is empty. Financial-engine directories, golden/resource directories, `AtomicFileWriter.java` and `pom.xml` have no diff. Generated logs and isolated audit scripts remain under ignored `target/`; the project-local Maven repository remains ignored. Nothing staged, committed, pushed, reset or discarded.

Ready for review. Remaining manual verification: retry the actual accepted completion in a process using the corrected constraint, confirm verified Saved status, record the structural field-path diagnostics, and reopen the saved result without recalculation. The live retry itself was not performed here. Values exceeding 4,096 token characters or the unchanged total-size limits continue to fail safely; further changes would require new evidence.
