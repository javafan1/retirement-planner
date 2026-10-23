# Monte Carlo Persistence and Comparison — Phase 2A Result Fidelity and Capacity Gate

## 1. Scope, recommendation and baseline

Audit/measurement date: October 20, 2026. Branch `main`; HEAD `c11e7348b7f9b64b7b4f6b4c7c9daf8738388748`.

Initial status contained the pre-existing untracked `MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-1-AUDIT.md`. It was preserved. This phase adds one test-only capacity/fidelity probe and this report. No production persistence, UI, financial algorithm, random stream, scheduling, dependency, plan schema, golden expectation or storage-limit change is implemented.

**Measured finding:** all eight independently calculated 5,000-trial result families fit the existing document limits in the test-only direct logical representation. The largest is the 50-year couple paired fixed analysis: 13,970,791 raw bytes, 4,642,675 gzip bytes and 1,333,333 tokens, retaining 500,000 annual trial records. At 10,000 trials the same representation exceeds the token limit, despite fitting both byte limits. Test-only contiguous-prefix encoding restores the complete original graph while fitting the existing limits. These measurements justify a focused structural encoding rather than a limit increase or general value interning.

Recommend explicit versioned fixed standalone DTOs and compact contiguous annual-prefix DTOs for longevity/paired evidence, preserving stored aggregates and complete per-trial observations. Reuse Phase 9 atomic persistence and lifecycle infrastructure later. Do not introduce Exhaustive-style general interning based on the presence of repeated field names; these trials have distinct outcomes.

## 2. Exact graph inventory and restoration requirements

Source paths below are relative to `src/main/java/com/daviddunn/retirementplanner/`.

### Common settings, provenance and mutability

`app/montecarlo/MonteCarloSettings` is an immutable record: simulation count, signed-long seed, BigDecimal expected return/volatility, optional `MonteCarloInflationSettings` (mean, volatility, floor). Settings permit up to 100,000 trials; UI permits up to 10,000 and defaults to 5,000. Preserve these execution contracts independently of persistence capacity.

Indexed market generation uses `MonteCarloScenarioGenerator.MODEL_VERSION` and the existing legacy indexed random stream. Inflation and mortality use independent versioned dimensions through `MonteCarloRandomStreams`; mortality additionally depends on table contents, ordered distributions, birthdates, categories, adjustments and timing convention. Equal seed alone is not full world identity.

`MonteCarloMortalityRequest` is immutable input context, including demographics, primary distribution request, optional couple longevity assumptions, immutable `SocialSecurityMortalityTable` and optional survivor election. Table metadata **and entries** are available; index lookup is derived, not separate financial evidence. Individual requests have no spouse or survivor fields. Restoration can reconstruct an allowlisted table from frozen metadata/entries or resolve a verified content digest; it must not regenerate sampled lifetimes.

`MonteCarloStrategyCandidate` encapsulates a mutable `RetirementPlan` behind a private frozen copy and exposes only label plus isolated working-copy creation. It is not an automatically serializable immutable DTO. Capture explicit frozen A/B inputs before context becomes inaccessible. Runtime plans, `Projection`, JavaFX controls, sessions and executors are mutable and must not be serialized as arbitrary object graphs.

Completed result records/classes expose copied lists and immutable sorted maps of immutable scalar/record values. `MonteCarloSession` and comparison session own mutable result/progress/state references; those state machines are not snapshots. Pure UI models and PDF report records contain immutable copied presentation data, not live plan references.

### Fixed standalone — single and couple

`MonteCarloAnalysisResult` contains:

- Settings and return-model ID.
- Ordered `RunOutcome` for every requested index: optional complete `ProjectionMetrics`, optional first nonpositive year-end, or complete structured `FundingFailure`.
- Sorted annual investable-asset percentile map, with absent observations distinct from observed zero.
- Four terminal distributions: investable assets, net worth, after-tax estate and lifetime taxes.
- Frozen deterministic `Reference`: annual asset values and either metrics or failure, including a failed reference's completed prefix.

All fourteen `ProjectionMetrics` fields must survive: investment growth, income, total/peak taxes, ending investable assets/net worth/after-tax estate, lifetime withdrawals, Roth conversions, RMDs, Medicare premiums, household/primary/spouse SS. An absent spouse metric remains null, not zero. Every amount retains BigDecimal scale.

No per-trial annual asset maps, full trial `ProjectionYear` graphs or generated market paths are retained by this fixed result. Do not pretend to restore them. They are unnecessary for its current chart/PDF, but their absence limits arbitrary saved-run annual paired comparisons.

### Longevity standalone — single and couple

`MonteCarloMortalityAnalysisResult` retains request, return/mortality model IDs and ordered `WorldOutcome` records. Each outcome contains index, complete individual/couple `MonteCarloLifetime`, annual asset prefix and exactly one terminal outcome or funding failure. Each lifetime retains present people, optional death years and consistent projection timing. Individual absence must not become an artificial spouse.

`TerminalOutcome` retains balance date, investable assets, net worth, after-tax estate and lifetime taxes. `FundingFailure` retains calendar year, year index, optional primary/spouse ages, stage, optional owner, required/available/shortfall amounts. A shortfall is the allocator's first unsatisfied request, not a newly calculated annual deficit.

Private cached `MonteCarloMortalityAccumulator.Aggregates` includes annual populations/percentiles, completion count, four terminal distributions, earliest/latest terminal balance date, terminal-year counts and optional failure statistics. Preserve those cached historical values in addition to observations. The public result constructor currently reduces observations again; a future narrow restore factory/presentation adapter should consume validated frozen aggregates without changing their historical statistical semantics.

`MonteCarloMortalityAnnualResult` retains requested, living, completed-living, failed-living and deceased counts, optional couple population (both alive/primary only/spouse only), and optional investable-asset percentiles. Sample count and optionality reconcile. Mortality determines actual ending dates; the configured 50-year horizon is reference information, not a cap on sampled lifetimes.

### Paired fixed and longevity — single and couple

`MonteCarloStrategyComparisonRequest` retains two complete frozen candidates and `Fixed` or `Longevity` assumptions. Fixed assumptions explicitly contain common start, ending year and household lifetime. Longevity assumptions contain the shared mortality request; each strategy supplies its own effective survivor rules.

`MonteCarloStrategyComparisonResult` contains request, `WorldSource` (generated/caller-supplied), ordered `MonteCarloPairedOutcome` records and cached `MonteCarloStrategyComparisonSummary`. Each pair retains one index/lifetime and separate A/B `MonteCarloStrategyOutcome` values: each side's own annual asset prefix and terminal/failure. Preserve distinct sides even when financially equal. No full annual account/tax projections or economic paths are retained.

Cached summary includes all four joint completion/failure states; independent failure statistics; four `MonteCarloPairedMetricSummary` values (sample count, percentile distribution, mean, greater/equal/less counts); annual `MonteCarloPairedAnnualResult` populations and investable-asset differences. Preserve exact ordering and A-minus-B direction.

### Aggregate and statistical contracts

`MonteCarloPercentiles` retains min/P10/P25/P50/P75/P90/max and sample count, using existing Type-7 interpolation. `FundingFailureStatistics` retains count, year counts, probability per requested trial, conditional fraction among failures, first-shortfall-year percentiles and shortfall-amount percentiles.

Funding probability uses all requested trials. Terminal financial distributions condition on completed trials; paired terminal metrics condition on BOTH_COMPLETED. Annual samples include completed prefixes of later failures, and longevity samples require a still-living household. Deceased/failed observations are never inserted as zeros. Paired relation counts and paired funding differences have different denominators; keep them explicit.

Matched-scenario evidence is one shared world generated once per index, then independently evaluated A and B by `MonteCarloStrategyComparisonAnalyzer`. Preserve index, membership, lifetime, settings, stream/model/reference versions and world-source provenance. A restored result must not infer pairing from seed alone, subtract independent percentiles or silently realign horizons.

### Frozen views, reports and baseline context

`ui/montecarlo/MonteCarloRun` retains a fixed/mortality result, `MonteCarloFanModel`, `BreakEvenPlanSummary`, first/last year, reference-incomplete flag, elapsed time and frozen plan-details text. The fan contains annual percentiles, optional deterministic line and mortality populations. Its `ProjectionChartModel` context includes annual metric/composition data, SS claim markers and Roth/RMD periods. These values, names and dates must come from the historical run, not the newly opened plan.

`MonteCarloStrategyComparisonRun` retains result, mode, frozen detail text and elapsed time. Capture the prepared UI context, A/B labels, original baseline description/time and both effective frozen plans. The current paired UI is Current Plan versus Saved Baseline, not an arbitrary historical-run selector. Baseline creation currently deep-copies through JSON; getters remain mutable, so persist the actual private captured inputs used for execution.

`MonteCarloPdfReportAdapter.from` reads frozen runs, not current controls. Existing report data includes funding tables, four terminal metric distributions, annual populations/percentiles, failure tables, assumptions, chart points/markers/periods and methodology. Paired reports add joint outcomes and paired differences/sign probabilities. `MonteCarloPdfReport.exportedAt` is export time; current runs have no separate durable completion timestamp. Future acceptance must add that timestamp without replacing original context. Both UIs currently block stale PDF export; supporting Outdated export requires a later authorized readiness/status change.

Chart active year, selected metric, focus, expanded folds and window geometry are presentation preferences. They must not invalidate financial currency or enter financial world identity. No heat map or individual annual projection drill-down is present in these MC results.

## 3. Test-only fixtures and methodology

Added `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloCapacityProbeTest.java` only. It is explicitly **not** a production codec or approved wire schema. It creates a logical JSON graph from trusted record components, handles Optional/BigDecimal/enum/date types explicitly, and reconstructs known record classes. No type name is loaded from JSON.

Couple fixture extends existing `MonteCarloWorldGeneratorTest.plan` / `MonteCarloFixtures.household`: primary born 1963, spouse 1965, distinct mortality categories, five brokerage/traditional/Roth accounts with joint/primary/spouse ownership, both SS records, pension, ordinary spending, home equity, federal/state settings and standard financial engine. Single fixture extends `SinglePersonMortalityAnalysisTest.plan`: one person born 1965, brokerage/IRA/Roth accounts, own SS and pension, living expenses and inherited established tax settings. All values are synthetic test data.

Both start January 1, 2027, with a 50-year configured horizon through 2076. Fixture conversion is $15,000.01 annually until first household RMD; paired alternative uses $30,000.02 with the same stop rule. Each alternative is independently copied; trials are neither fabricated nor reused. Existing production indexed generators use seed 417, 12% return volatility, plan expected return and stochastic general spending inflation (2% mean, 1.75% volatility, −2% floor). Mortality factors are 1; couple survivor age is explicitly 67. Fixed worlds retain configured survival; longevity worlds use actual production mortality generation.

The feature-check test executes the deterministic fixture through the real engine and verifies 50 annual rows, federal tax, state tax result, RMD, Roth conversion and Medicare processing. It also empties accounts in separate test copies to verify actual structured funding-failure round trips, without changing domain rules. Funded status is not artificially forced. These fixtures do not cover every jurisdiction/inherited-account variant or maximum possible mortality prefix; they are realistic capacity samples, not a worst-case proof.

The probe includes frozen plan JSON (both plans for pairs), settings, primary mortality request/table metadata and couple assumptions, calculation observations, cached aggregates, and standalone frozen people/fan/report text. It compares logical JSON recursively using BigDecimal.equals, reconstructs domain outcome/result records and compares independently with originals. Small standalone cases also reconstruct fan/people and compare existing PDF adapter data, excluding only export timestamp. Enum identifiers use stable names, not display labels.

Limitations: the test does not yet decode a complete independent cross-session request (the immutable original request is reused for result-constructor validation). Full mortality table entries/content digest and the paired UI's prepared details string are not part of measured bytes; its two complete financial plans and mortality metadata are included. Envelope compatibility/binding/digest/timestamp metadata are not included. Thus these are **measured logical candidate payload sizes**, not final production snapshot sizes. Future DTO proof must include all final context and envelope bytes.

Reconstruction of mortality/paired result classes currently performs their existing statistical reductions. That is separately timed validation, not a financial projection, search or random-world regeneration. Frozen cached summaries are also decoded/compared directly. This test does not claim a production zero-reduction restore adapter exists.

### Environment and measurement interpretation

JDK `25.0.4.1+1-LTS`, OpenJDK 64-Bit Server VM; Windows 11 amd64; eight logical processors visible to Java; `-Xmx4g` (4,294,967,296 bytes). Exact CPU model query through CIM was denied by the environment; no hardware identification is claimed.

Each cell runs sequentially. Before each cell, the probe requests GC and resets memory pool peaks. Engine-and-capture time includes real simulation plus test logical-tree construction; encode time is JSON serialization plus gzip; decode time is gzip plus JSON parsing; rebuild time includes known record reconstruction, statistical reduction where required and equality checks. They are single observations, not warmed statistical benchmarks. Tree normalization can be less efficient than a dedicated DTO codec.

Memory figures are observed heap growth and the sum of heap-pool peak-used readings. They include transient engine, tree, serialization and reconstructed objects; they are neither retained-result size, OS RSS nor a synchronized exact peak. File size and numeric diagnostics contain counts/lengths only, not financial values. Large JSON/gzip graphs are not written into repository resources.

## 4. Measured capacity

### Default UI count: 5,000 independently calculated trials per cell

All below use 50 configured years. Annual trial records count actual retained asset observations, summed across both sides for pairs. Fixed standalone retains no annual trial maps; zero in that column does not mean zero financial years were calculated. Completed means funded standalone outcomes or BOTH_COMPLETED pairs. Every cell has **5,000 distinct outcome histories/metric-or-failure records**, excluding scenario index from the distinctness test.

| Household | Mode | Paired | Annual trial records | Completed | Raw bytes | Gzip bytes | Tokens | Max numeric characters |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Single | Fixed | No | 0 | 4,871 | 4,778,025 | 859,368 | 198,931 | 250 |
| Single | Fixed | Yes | 498,799 | 4,870 | 13,040,321 | 4,220,410 | 1,308,095 | 299 |
| Single | Longevity | No | 115,276 | 4,999 | 4,119,428 | 1,131,963 | 420,916 | 170 |
| Single | Longevity | Yes | 230,552 | 4,999 | 7,615,374 | 2,184,648 | 769,324 | 208 |
| Couple | Fixed | No | 0 | 5,000 | 5,400,651 | 1,012,139 | 200,359 | 184 |
| Couple | Fixed | Yes | 500,000 | 5,000 | 13,970,791 | 4,642,675 | 1,333,333 | 303 |
| Couple | Longevity | No | 131,290 | 5,000 | 4,723,872 | 1,382,033 | 478,246 | 172 |
| Couple | Longevity | Yes | 262,580 | 5,000 | 8,657,206 | 2,598,375 | 858,248 | 172 |

Largest array in each cell contains 5,000 outcomes. Maximum depth is 7. Arrays number 15–27; all document screens pass. Actual mortality prefixes vary with sampled deaths, so the 50-year input must not be interpreted as 50 retained annual values for every longevity world. Numeric values themselves are not logged; the table reports textual token lengths.

| Household/mode/pair | Engine + capture ms | JSON + gzip ms | Decode ms | Rebuild/validate ms | Observed heap growth MiB | Summed pool peaks MiB |
|---|---:|---:|---:|---:|---:|---:|
| Single fixed | 15,203 | 143 | 65 | 129 | 213.1 | 335.7 |
| Single fixed paired | 29,046 | 706 | 106 | 492 | 466.0 | 581.5 |
| Single longevity | 6,487 | 169 | 29 | 263 | 173.8 | 383.4 |
| Single longevity paired | 12,271 | 312 | 53 | 248 | 282.7 | 460.4 |
| Couple fixed | 32,009 | 141 | 37 | 87 | 217.9 | 287.8 |
| Couple fixed paired | 62,975 | 746 | 136 | 368 | 504.0 | 764.0 |
| Couple longevity | 14,764 | 182 | 33 | 239 | 116.0 | 352.2 |
| Couple longevity paired | 28,733 | 361 | 91 | 267 | 213.9 | 451.9 |

The measured default-size matrix completed in 208.3 seconds of test execution; Maven total 3:37. A preliminary independently calculated 1,000-trial eight-cell matrix also passed (45.78 seconds test execution, Maven 54.933 seconds). Its direct payloads ranged from 869,041 to 2,819,939 raw bytes and 188,635 to 933,972 gzip bytes. It is supporting evidence, not extrapolation substituted for 5,000 trials.

### UI maximum and prefix experiment

The separate 10,000-trial couple fixed matched comparison calculated 10,000 distinct histories, 10,000 BOTH_COMPLETED pairs and **1,000,000 annual trial records**, across 50 years per side. No financial result was reused across worlds.

| Measurement | Direct logical payload | Test-only contiguous-prefix payload |
|---|---:|---:|
| Raw JSON bytes | 27,911,139 | 21,471,139 |
| Gzip bytes | 9,277,109 | 7,637,296 |
| JSON tokens | **2,663,333 — exceeds 2,000,000** | 1,763,333 |
| All object/array containers (diagnostic, not a global cap) | 110,244 | 130,244 |
| Array containers | 27 | 20,027 |
| Largest array | 10,000 | 10,000 |
| Maximum numeric token length | 303 | 303 |
| Maximum depth | 7 | 7 |
| JSON/gzip encode ms | 1,469 | 971 (includes test tree copy/packing) |
| Gzip/JSON decode ms | 250 | 285 (includes structural expansion and exact comparison) |
| Runtime record rebuild/validate ms | 752 | Not separately repeated; expansion reproduces the exact direct graph |

Direct engine-and-capture time was 130,553 ms. Observed heap growth was 764,411,904 bytes (729.0 MiB), with summed pool peaks 1,261,740,536 bytes (1,203.3 MiB). Those readings precede the prefix experiment; isolated prefix-experiment memory was not measured. The test uses an extra copied JSON tree, which is not a recommended production memory design.

The experiment changes only the test logical encoding of actual contiguous annual maps: first year plus exact ordered numeric values. No amounts, scale, dates, side identity, failures or cached aggregates are removed; expansion verifies original year/value mappings exactly. Empty histories stay empty. Packing asserts consecutive years; expansion reconstructs each year key with checked integer addition. This is structural encoding, not financial recalculation or value interning. Raw size fell by 6,440,000 bytes and tokens by 900,000; this is not the earlier Exhaustive duplicate-outcome compression claim.

The experiment remains test-only. It does not implement production format dispatch, untrusted-prefix validation or an atomic persistence path. A future decoder must reject invalid first-year/count/type combinations, excessive prefixes, overflow and total reconstructed-element budgets before allocation. Final DTOs must include the omitted envelope/request/report context listed above and be measured again. The 100,000-trial domain maximum and all other possible horizons remain unproven; no universal capacity guarantee is made.

## 5. Storage architecture comparison

| Option | Fidelity and identity | Capacity/security | Atomicity/compatibility/complexity |
|---|---|---|---|
| A: explicit direct versioned DTOs | Preserves all current observations and frozen summaries; original trial and paired side identities are explicit. | Measured default cells fit. Repeated year keys add tokens; final envelope and larger horizons need proof. Bound every collection and numeric/string scalar. | Best reuse of existing single-file gzip/digest/atomic/backup repository. New kind dispatch only; existing SS/weighted/Exhaustive bytes stay unchanged. Lowest complexity. |
| B: genuine immutable sharing/interning | Safe only for byte/scale-identical immutable values, never mutable accounts or merely rounded outcomes. | 5,000 distinct outcomes in every cell defeats a duplicated-outcome shortcut. Settings/table/report context can be stored once naturally. Gzip already compresses repeated keys; general reference tables add expansion/reference risks. | Extra version dispatch, reference checks and expansion budget. No measured need for Exhaustive's generic interning structure in MC. Not recommended initially. |
| C: bounded components + manifest | Can preserve full trial histories without recomputation; component identity/order and paired membership must be committed as one generation. | Each component and total reconstructed work need explicit approved bounds. Dividing files must not bypass existing global safety policy with unbounded expansion. | Requires staging generation, verified immutable components, atomic manifest acceptance, recovery and orphan handling. Existing one-file atomic writer is not a multi-file transaction. Defer unless final DTO capacity genuinely requires it. |
| D: sufficient exact evidence without redundant derived storage | Keep all retained observations and cached summaries. Encode consecutive annual prefixes as firstYear + ordered values; derive year keys structurally, not balances. Chart/report duplicates can be bounded references to frozen aggregates. | Removes redundant year tokens without merging financial values. Bounds on prefix length, trial counts, total observations and expansion are mandatory. Do not drop cached historical means/percentiles or annual evidence to fit. | Explicit versioned DTOs can remain in the existing single-file envelope. More focused than generic interning, and compatible with strict allowlisted decoding. Recommended for annual histories subject to final proof. |

Mode recommendation: A for fixed standalone; D for longevity standalone and paired fixed/longevity, with complete observations plus frozen aggregates. The 10,000-trial measurement demonstrates why the paired direct year-map layout should not become the universal production contract. Store settings, immutable table provenance and frozen plans once. Do not persist all full trial Projections: current functionality does not retain or consume them. Do not omit any currently retained annual history.

Safety screening preserves raw 33,554,432 bytes, gzip 16,777,216 bytes, numeric 4,096 characters, tokens 2,000,000, depth 80 and string 256,000. Source clarification: `AnalysisSnapshotCodec.checkDocument` counts START_ARRAY occurrences against `MAX_ARRAYS=100,000`, not all object containers or items in one array. The probe reports total containers separately and additionally screens largest array length at 100,000. Future MC DTOs must explicitly enforce trial/prefix/total-element bounds; constant names alone are not an expansion policy. No existing check is weakened or changed.

## 6. Lifecycle replacement design (not implemented)

Exact clear points: `ui/montecarlo/MonteCarloSession.java:75` and `MonteCarloStrategyComparisonSession.java:75` assign `result = null` in `start`, after generation/revision capture and before RUNNING notification/worker admission. They also clear failure/stale/progress. `finish` at line 105 in each rejects wrong generation/closed state, checks cancellation and captured revision, then publishes a non-null completed result. Failure/cancellation currently cannot recover the result erased by start. `invalidate` increments revision, marks stale and cancels current work. Close increments generation and cancels; dialogs close sessions on hidden.

Recommended sequence:

1. Retain `lastAccepted` separately from active replacement job/progress/failure. Starting a run clears only job-specific error/progress. Capture immutable request/plans/mode/source UUID/file binding and frozen report context before execution.
2. Construct snapshot data on worker. Keep existing generation/revision/cancellation gates at publication. An eligible complete MC analysis is accepted even when all trials contain structured funding failures; software error/cancellation/rejection is not accepted success.
3. At accepted completion, replace visible accepted data and hand off to application-owned store. Keep previous durable generation until new gzip file is written, verified and atomically committed. Pending/Save Failed describes durability independently of financial Current/Outdated.
4. Cancellation/failure/obsolete callbacks retain previous accepted data. Retry Save consumes immutable accepted data without starting projections. Accepted storage survives dialog close and captures original UUID; plan switching cannot redirect it.
5. Restore asynchronously under UUID/path, household/mode, view generation and accepted-result sequence guards. A delayed load must not overwrite a newer accepted run. Never populate live next-run controls from historical context or invoke financial work.
6. Currency uses versioned effective-input fingerprints, not revision alone. Invalid pending input yields Inputs Need Attention. Returning effective inputs restores Current; presentation selection does not affect currency. Incompatible files remain protected. Compatible Outdated results stay fully viewable with timestamp and frozen assumptions.
7. Save As gets independent identity only after plan commit, then clones per-mode historical snapshots copy-if-empty with original provenance; reassess against destination inputs. A paired result remains its original frozen A/B experiment, not a new comparison against whichever baseline is now selected.

Baseline special case: currency depends on both effective plans and shared simulation assumptions. A change to the active baseline must not silently rewrite side B of a saved experiment. Mismatched seeds, counts, horizons, lifetime rules or model/reference versions cannot be presented as an existing statistically paired experiment merely because two results are available. Preserve original populations and A-minus-B direction.

## 7. Proposed Phase 2B gates

1. Approve exact MC kind/slot taxonomy and the DTO inventory. Keep single/couple and fixed/longevity/paired results independent. Do not reuse a weighted slot or global default Jackson typing.
2. Implement fixed standalone pure immutable DTO/restore proof, all fourteen metrics, structured failures, deterministic reference and full frozen chart/report context. Preserve scale, absent spouse and original time. Include existing frozen Phase 9 compatibility bytes/digests in regression.
3. Prove bounded annual-prefix DTOs on actual measured 5,000/10,000 results before integrating longevity/paired persistence. Include full frozen request/table provenance, UI comparison details, compatibility envelope and validated total expansion budget. Persist original cached aggregates. Seek review if final logical contract still exceeds any limit.
4. Implement last-accepted/job-state separation with tests for cancellation, all-funding-failed acceptance, failure, stale callback and out-of-order load/save. No engine scheduling change. Add gated handoff to existing asynchronous store and UUID/Save As integration only after codec proof.
5. Restore fixed standalone first; separately authorize longevity and paired integration. Compare exact report data and original chart context after a new application/view instance. Verify no projection/search/random generator calls on restore/export and no current-input replacement.
6. Focused suites, full Maven regression and native restart/Save As/Outdated/Retry Save/shutdown acceptance before release. Historical PDF export readiness needs explicit change and tests, not a PDF redesign. No remaining Individual SS persistence work is a dependency.

## 8. Verification evidence, limitations and reproducibility

Commands use IntelliJ bundled Maven and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`, with `-DargLine=-Xmx4g`. Normal probe tests remain small; large tests are opt-in, consistent with existing benchmark infrastructure. No existing test is disabled.

Reproduce matrix: `-Dtest=MonteCarloCapacityProbeTest#representativeProductionTrialCounts -Dmc.capacity=true -Dmc.capacity.trials=5000 test`. Reproduce UI boundary: `-Dtest=MonteCarloCapacityProbeTest#longestFixedPairedCaseAtUiTrialMaximum -Dmc.capacity.maximum=true test`.

Ignored artifacts: `target/mc-capacity-production-1000.tsv`, `target/mc-capacity-production-5000.tsv`, `target/mc-capacity-ui-maximum.tsv`, `target/mc-capacity-prefix-experiment.txt`, `target/mc-capacity-environment.txt`, and corresponding Maven logs. They contain privacy-safe counts/measurements, not serialized financial payloads. Final measurements/results below distinguish successful runs from development fixture/helper corrections.

| Final verification | Run | Passed | Failures/errors | Skipped | Maven elapsed |
|---|---:|---:|---:|---:|---|
| 1,000-trial eight-cell matrix | 1 | 1 | 0/0 | 0 | 54.933 s |
| 5,000-trial eight-cell matrix | 1 | 1 | 0/0 | 0 | 3:37 |
| 10,000-trial boundary/prefix experiment plus small eight-cell, decimal and financial-feature/failure checks | 4 | 4 | 0/0 | 0 | 2:25 |
| Final focused regression | 171 | 169 | 0/0 | 2 | 29.473 s |

The final focused selection was `MonteCarloCapacityProbeTest,MonteCarloFoundationTest,MonteCarloRandomStreamsTest,MonteCarloPairedWorldTest,MonteCarloPairedAggregationTest,MonteCarloStrategyComparisonTest,MonteCarloInflationRegressionTest,SinglePersonMonteCarloTest,MonteCarloSessionTest,MonteCarloComparisonSessionTest,MonteCarloPdfExportTest,MonteCarloWorldGeneratorTest,MonteCarloMortalityExecutionTest,MonteCarloMortalityResultsTest,MonteCarloMortalityAccumulatorTest,MonteCarloFundingTest,AnalysisSnapshotCodecTest,ExhaustiveSnapshotInterningTest,CoupleSocialSecurityPersistenceTest`.

The two normal skips are the newly added opt-in production-count and UI-maximum probes, both run successfully in the separate commands above. Expected fault-injection diagnostics in persistence/PDF tests did not indicate suite failures. Initial development runs caught a test helper import error, missing fixture survivor age for couple longevity comparison, and display-label versus enum-name encoding in chart context; these were corrected only in the new test infrastructure. Final green runs supersede those failed development attempts; no financial assertion or algorithm was weakened.

Exact reconstruction evidence includes original-versus-decoded record equality, scale-sensitive recursive scalar comparisons, full stored cached-summary comparisons, preserved trial order/paired lifetimes, raw absence/nullable-spouse distinctions, unchanged source-plan JSON, structured all-funding-failed results, and original-versus-rebuilt standalone PDF adapter data excluding only export time. Rebuilding trial records calls no financial engine or optimizer; existing mortality/paired constructors redo statistical reductions for verification, as disclosed above. A production factory avoiding that reduction remains Phase 2B work.

`git diff --check` passed. The complete Maven suite was not run: this phase changes only test infrastructure and a report, and no release-ready production persistence is claimed. Full regression and native historical restoration are future acceptance gates.

Not covered: all possible plans/jurisdictions, 100,000 domain trials, every 10,000-trial mode, arbitrarily long sampled lifetimes, native UI restoration, production storage durability, actual cross-session request reconstruction, full current-versus-outdated PDF behavior or a full-suite acceptance run. There is no production MC codec or persistence claim in this phase. Measurements are reproducible fixture evidence, not a guarantee of worst-case size, hardware-independent latency or future compression ratios.

## 9. Git inventory

Pre-existing untracked report preserved: `MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-1-AUDIT.md`.

New files for this phase: this report and `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloCapacityProbeTest.java`. No tracked production/test files were edited. No staged changes, commits, pushes, resets or discards. Measurement artifacts remain ignored under `target/`.

Final short status:

```text
?? MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-1-AUDIT.md
?? MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-2A-CAPACITY.md
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloCapacityProbeTest.java
```

Tracked and staged diff statistics are empty because these additions are untracked. The untracked inventory contains only these three expected files; no generated logs, large outputs or sensitive plan files are included.

Stop after Phase 2A for review; no Phase 2B implementation is authorized by this report.
