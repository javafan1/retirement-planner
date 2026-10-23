# Monte Carlo Persistence and Comparison — Phase 1 Architecture Audit

## 1. Executive recommendation and audit boundary

Audit date: October 20, 2026. Source baseline: `main`, HEAD `c11e7348b7f9b64b7b4f6b4c7c9daf8738388748`. The initial working tree and index were clean.

Prioritize durable Monte Carlo results before the remaining Individual Social Security Only and Individual Deterministic persistence work. Reuse the Phase 9 gzip JSON repository, durable plan identity, atomic writer, application-owned background store, compatibility statuses and Save As cloning. Add explicit Monte Carlo payload families and effective-input fingerprints; the current Social Security contracts do not already support these results.

The existing comparison engine already performs statistically paired financial evaluation. It evaluates Current Plan and Saved Baseline in the same generated market, inflation and lifetime world. Preserve that implementation. Persist its evidence rather than introducing a replacement comparison algorithm.

Implement fixed-lifespan standalone persistence first, then longevity-adjusted standalone and existing paired comparison persistence. Gate each on exact round trips, capacity measurements and restart/PDF acceptance. Comparing two independently saved runs is a separate feature: descriptive comparisons can be offered with qualifications, but paired probabilities require demonstrably matched worlds and retained trial evidence.

This is an audit only. No production code, tests, dependencies, schemas, financial values or UI behavior were changed. Proposed classes and policies below are recommendations, not implemented functionality.

## 2. Current architecture and feature inventory

Source locations below are relative to `src/main/java/com/daviddunn/retirementplanner/`.

| Layer | Verified implementation and responsibility |
|---|---|
| Entry points | `ui/MainWindow.java` analysis menu creates fresh `MonteCarloAnalysisDialog` and `MonteCarloStrategyComparisonDialog` instances. Comparison requires a saved baseline and compatible household composition. |
| Standalone UI | `ui/montecarlo/MonteCarloAnalysisView`, `MonteCarloInputs`, `MonteCarloRunService`, `MonteCarloSession`: capture inputs, copy plan, execute off FX, render frozen result and export. |
| Comparison UI | `MonteCarloStrategyComparisonView`, `MonteCarloStrategyComparisonRunService`, `MonteCarloStrategyComparisonSession`: Current Plan versus Saved Baseline, with shared simulation settings and paired results. |
| Simulation | `app/montecarlo/MonteCarloAnalyzer`, `MonteCarloScenarioGenerator`, `MonteCarloInflationGenerator`, `MonteCarloWorldGenerator`, `MonteCarloRandomStreams`. |
| Paired execution | `MonteCarloStrategyComparisonRequest`, `MonteCarloStrategyCandidate`, `MonteCarloStrategyComparisonAnalyzer`, `MonteCarloPairedOutcome`, accumulators and summary records. |
| Financial execution | Existing `domain/projection/ProjectionEngine` and `ProjectionEvaluationContext`, with standard withdrawal, federal/state tax, RMD, Roth, Medicare and authoritative Social Security processing. Persisting results needs no change to those calculations. |
| Rendering | `MonteCarloFanModel`, `MonteCarloFanChart`, `MonteCarloDifferenceChart`, standalone/mortality/comparison presentation helpers. |
| PDF | `ui/montecarlo/MonteCarloPdfReportAdapter`, `MonteCarloPdfExportAction`; `app/export/MonteCarloPdfReport`, `MonteCarloPdfChart`, `MonteCarloPdfExporter`. Both workflows support PDF. |

### Modes, settings and household support

Both standalone and comparison support `FIXED_LIFESPAN` and `LONGEVITY_ADJUSTED`, for single-person and couple plans. There is no Monte Carlo claiming-strategy optimization or automatic Roth/spending strategy search. A baseline and current plan can differ in those settings through ordinary plan editing.

Both UIs initially select Fixed Lifespan, 5,000 trials, seed 417, return volatility 12.00%, deterministic inflation, and expected return from the plan. Stochastic inflation controls initialize mean from the plan, volatility 1.75%, floor −2.00%. These are existing values, not proposed changes. UI validation permits 1–10,000 trials; `MonteCarloSettings` permits 1–100,000. Preserve both contracts. Seeds are signed 64-bit values.

Annual market returns use `LOGNORMAL_ARITHMETIC_MOMENTS_V1`, fitted to arithmetic mean and simple-return standard deviation. The legacy market generator uses an indexed `java.util.Random` stream derived from seed and scenario index; extending trial count does not change earlier indexed worlds. StrictMath/double operations occur at the existing stochastic sampling boundary, followed by BigDecimal conversion. Financial projections remain BigDecimal calculations.

Optional `FLOORED_NORMAL_GENERAL_INFLATION_V1` draws an independent annual general-spending inflation path. It does not randomize healthcare inflation, Social Security COLA, pension COLA, tax indexation, Medicare schedules or non-investable asset appreciation. `ProjectionEngine` supplies a cumulative general expense multiplier after the opening year; existing opening-year proration remains intact. Market, inflation and mortality are independent in this version.

Mortality uses explicit SHA-256 indexed random dimensions/protocol versions, the actual mortality table and authoritative distribution/scenario factories. Couple worlds sample ordered joint scenarios; individual worlds sample the primary distribution without spouse/joint controls. `MonteCarloMortalityRequest` conditions on plan projection start; partial-year timing uses the existing next-complete-birthday convention. Lifetime financial execution ends before the January 1 terminal death, with opening-date death handled by opening estate rather than a fabricated annual projection. Configured horizon is not an arbitrary cap on sampled longevity.

### Results and success definitions

Fixed funding probability is completed full-horizon trials / requested trials. Positive assets in every completed year is a different, conditional measure. Terminal distributions include successfully completed trials only. Annual distributions include trials that completed that year, including prefixes of trials that fail later. Failures are not inserted as zero assets.

Longevity funding probability is the fraction funding the sampled lifetime obligations. Annual percentiles condition on living households funded through that year. Deceased households are absent, not failures or zeros. Population counts distinguish living, funded, failed and deceased, with a couple-only both-alive/primary-only/spouse-only breakdown. Terminal observations have different sampled death dates and remain nominal values, not discounted common-date values.

Percentiles use the existing Type-7 interpolation and retain min, P10/P25/P50/P75/P90, max and sample count. No-data percentiles are absent. Standalone tables summarize terminal investable assets, net worth, after-tax estate and lifetime modeled income taxes; fan charts show annual investable-asset percentiles. Fixed charts also show a frozen deterministic reference and SS/Roth/RMD context. Longevity charts intentionally omit those configured-life reference overlays. Failure summaries expose first-failure year and allocator shortfall distributions, not a newly inferred annual spending deficit.

## 3. Existing comparisons and statistical meaning

`MonteCarloStrategyComparisonRunService.capture` independently freezes Current Plan (A) and Saved Baseline (B). It requires matching projection start, household composition, DOBs and mortality categories. Fixed comparisons use Current Plan's configured ending year as the common horizon; the baseline's configured length is reference information. Fixed death scenarios must agree. Longevity comparisons share sampled lifetimes; couple survivor settings can differ by strategy on private plan copies. Single-person analyses have no invented spouse fields.

`MonteCarloStrategyComparisonAnalyzer` generates one world per index, evaluates A then B with that same world, and retains paired outcomes. It supports generated and caller-supplied worlds. Candidate plans are private deep copies; engines never receive mutable live-plan accounts. Both sides use the standard financial engine, so changing Roth, spending, claiming elections or other supported plan inputs is already possible through Current versus Baseline.

Reported differences are **A minus B**. Four terminal metric distributions and means use BOTH_COMPLETED worlds, not independently completed populations. Relation probabilities preserve greater/equal/less counts. Funding differences use all requested pairs and retain four joint completion/failure states. Annual paired differences use common funded living-year observations; later failures do not erase earlier comparable years. These are percentiles of within-world differences, not differences between separately computed percentiles. Positive tax differences mean more tax, not an automatic improvement.

There is currently no picker comparing arbitrary persisted Monte Carlo runs, no durable scenario archive, no confidence-interval/significance analysis and no strategy optimization in these views. Do not imply those features exist.

### Saved-result compatibility rules (proposal)

| Difference between saved results | Safe proposed treatment |
|---|---|
| Different seeds, RNG/reference versions or world identity | Descriptive side-by-side only; never paired win probabilities or paired difference percentiles. |
| Different trial counts | Show original denominators; do not silently align or resample. Explicit common-index subset analysis would need separate authorization and evidence. |
| Different horizons/start dates or lifetime populations | Clearly label differing endpoints/populations; do not present terminal differences as the existing matched comparison. |
| Different return/inflation assumptions | Treat as distinct environments, even with equal seeds. Same seed does not make the generated economic paths identical. |
| Different financial strategies under identical world provenance | Pairing is feasible if original indexed trial observations and common dates/populations are retained. Fixed standalone currently lacks individual annual asset paths, so it cannot support arbitrary historical annual paired curves without new execution-time evidence. |
| Caller-supplied worlds | Require captured canonical world/path digests and provenance. Settings/seed alone cannot prove what the caller supplied. |

Prefer persisting a completed existing paired analysis as a first-class result. It already contains valid paired evidence and requires no new statistical algorithm. An arbitrary historical comparator should default to explicitly unpaired descriptive output until strict matching is established.

## 4. Complete immutable persistence inventory

Do not serialize JavaFX nodes, session state machines, tasks, engines, listeners or arbitrary runtime classes. Use explicit allowlisted, versioned DTOs with exact BigDecimal value **and scale**, explicit Optional/null distinctions, ordered trial arrays and sorted year maps.

| Family | Required retained calculation graph |
|---|---|
| Fixed standalone | `MonteCarloAnalysisResult`: settings, return-model ID; every ordered `RunOutcome` (index, complete `ProjectionMetrics` OR structured `FundingFailure`, optional first nonpositive year); annual optional percentile map; four terminal distributions; deterministic `Reference` annual values and metrics/failure. |
| Longevity standalone | `MonteCarloMortalityAnalysisResult`: request/settings and source metadata, return/mortality model IDs; every `WorldOutcome` index, individual/couple lifetime, annual investable-asset prefix, terminal outcome OR structured failure; complete cached aggregates: annual population/distributions, completion counts, four terminal distributions, terminal-date bounds, terminal-year counts and failure statistics. |
| Paired, either mode | `MonteCarloStrategyComparisonResult`: frozen request and world-source provenance; every ordered `MonteCarloPairedOutcome` with shared lifetime and two `MonteCarloStrategyOutcome` graphs (annual prefix, terminal OR failure); full cached summary: joint states, per-side failure statistics, all four paired metric summaries including means/sign counts/percentiles, annual paired populations and differences. |

`ProjectionMetrics` has fourteen values: total growth, income, taxes, peak tax, ending investable assets, net worth, after-tax estate, lifetime withdrawals, Roth conversions, RMDs, Medicare premiums, household/primary/spouse Social Security. The spouse field can be null for a single-person plan; preserve that distinction.

`FundingFailure` includes year, projection index, optional ages, allocation stage, optional owner, required/available/shortfall amounts. Retain all fields rather than only a display message. `TerminalOutcome` includes balance date, investable assets, net worth, after-tax estate and lifetime taxes. Percentile and failure-statistics records retain their exact values, counts and denominators.

### Frozen request, reference and report context

Persist exact simulation settings; effective frozen plan inputs for standalone and both strategies for comparison; current/baseline labels and baseline description/time; household facts; death/survivor settings; mortality adjustment/category/conditioning conventions and table provenance; generator and reference versions; captured completion time/run ID; original elapsed runtime and explanatory notes. Store the actual mortality input/distribution metadata necessary to describe and reproduce the run, without rebuilding scenarios during restoration. Sampled lifetimes already retained in outcomes must be restored directly.

`MonteCarloRun` additionally freezes `BreakEvenPlanSummary people`, `MonteCarloFanModel`/`ProjectionChartModel` reference annotations, first/last years, reference-incomplete flag and plan-details text. Preserve claims, Roth/RMD periods, names and reference values needed by charts/PDFs. Do not obtain those from the newly opened live plan. Comparison freezes mode/details/elapsed time but its request contains opaque `MonteCarloStrategyCandidate` private plans; capture explicit plan context while preparation still has access to those copies.

Current run records lack a durable original completion timestamp. PDF adapter `Instant.now()` is export time, not analysis completion. A later phase must capture completion at the accepted boundary and distinguish both timestamps. Textual plan-details strings alone are insufficient as complete reproducibility or fingerprint input.

Fixed standalone does **not** retain every annual trial projection or market path. Longevity and comparison retain annual asset maps, but not full annual tax/account projections or generated paths. Do not invent absent detail during restoration, or require retaining every full Projection merely for existing charts/PDFs. Preserve all existing compact evidence; a view-only aggregate archive should not be advertised as lossless restoration of the complete current result contract.

Mortality and paired result constructors currently reduce outcomes to cached aggregates. For faithful historical restoration, add narrow pure restore factories accepting validated stored aggregates, or dedicated restored presentation adapters. Do not rerun financial engines, rebuild mortality scenarios or silently recompute historical percentiles using later algorithm versions.

## 5. Phase 9 infrastructure fit and fingerprints

Verified sources: `app/socialsecurity/results/{ApplicationAnalysisResults,AnalysisResultStore,AnalysisKind,AnalysisFingerprintService,AnalysisCompatibilityPolicy,AcceptedAnalysisCompletion}`, `persistence/{AnalysisSnapshotCodec,JsonAnalysisSnapshotRepository,AtomicFileWriter}`, and Phase 9B/9C/9D reports.

Reuse durable UUID/path binding, separate per-plan slots, gzip JSON, digest checking, temporary verification, atomic replacement/backups, original-plan provenance and copy-if-empty Save As behavior. Store has one background I/O worker, queue capacity eight, accepted-memory precedence, retry, bounded retained slots and drain/failure reporting. Loading/restoration work is separate from financial scheduling. Keep this ownership application-wide rather than dialog-owned.

`AnalysisKind` currently lists seven Social Security kinds; codec dispatch explicitly allowlists supported payload families. Existing fingerprint S/M/V/F/B/Q inputs and some frozen contexts are Social Security-specific. Add explicit MC kinds/contracts, not a catch-all serializer or misuse of a weighted slot. Recommend distinct slots for standalone fixed, standalone longevity, comparison fixed and comparison longevity, separately for each household shape (eight kinds). This retains each mode's last success; whether users prefer only one slot per UI workflow requires approval.

Preserve existing weighted/Couple SS/Exhaustive wire bytes, payload digests and algorithms. Add codecs through kind dispatch; do not apply Exhaustive JSON interning to MC without measured duplication and expansion proofs. No change to plan JSON is necessary beyond the already implemented durable UUID.

### Proposed fingerprint dependency matrix

| Dependency | Standalone fixed | Standalone longevity | Paired fixed | Paired longevity |
|---|---|---|---|---|
| Trial count, seed, return mean/volatility, inflation mode/settings | Yes | Yes | Yes | Yes |
| Financial effective inputs (ordered accounts/owners/inheritance, opening RMD facts, income/SS, spending, withdrawal/Roth, taxes/state, non-investable assets) | One plan | One plan | Both plans | Both plans |
| Projection start and opening-year rules | Yes | Yes | Common start | Common start |
| Configured horizon | Calculation endpoint | Context; sampled endpoint governs | Current common endpoint; baseline length reference | Context; sampled endpoint governs |
| Mortality table/category/factors/conditioning/timing | No stochastic mortality | Primary or joint as applicable | No stochastic mortality | Shared primary or joint as applicable |
| Fixed death scenario / survivor rules | Effective plan | Explicit lifetime/survivor override rules | Common fixed lifetime + effective sides | Shared lifetime + own survivor rules |
| Baseline financial contents | No | No | Yes | Yes |
| Names, chart selection, window state | Frozen report context, not financial validity | Same | Same | Same |

Audit actual getter/use paths before finalizing extraction: overrides must be fingerprinted as effective values. In particular the fixed deterministic reference can depend on configured plan return even when simulation return is overridden; retained chart/report context needs its own fingerprint. Do not blindly omit plan return or include every unused configured horizon as a financial dependency.

Canonical SHA-256 input DTOs should preserve BigDecimal scale conservatively, null/absence, ISO dates, and semantically ordered accounts/trials. Separate calculation-input digest, world/scenario provenance digest and frozen report-context digest. Compatibility metadata includes return/inflation/mortality sampling and stream versions, financial/aggregation semantics, government/reference-data digests and snapshot schema version. Unrelated UI builds should not invalidate financial currency.

Invalid pending text yields Inputs Need Attention, preserving the prior snapshot. Returning effective values to the saved values restores Current when compatibility matches. A plan revision counter remains useful for in-flight acceptance, but cannot determine historical currency alone.

## 6. Last-successful lifecycle and historical UX

Current `MonteCarloSession.start` and `MonteCarloStrategyComparisonSession.start` clear their result before running. Invalidations increment revision and cancel in-flight work; publication checks generation/revision/cancellation. Dialog hidden handlers close the view/session and shut down owned executors. Reopening creates fresh views. These results are memory-only and lost on close/restart; a failed/cancelled replacement can also leave no prior result visible.

Separate the last accepted immutable result from replacement-job state. Capture snapshot context on the worker and submit only after existing eligibility checks accept completion. Navigation/close must not cancel an already accepted storage write; plan switches must not redirect its captured UUID. Keep the previous durable snapshot until verified commit. Persisted status and computation status remain independent: Memory Only, Pending, Saved and Save Failed/Retry Save.

**MC success is not SS candidate success.** An eligible run completing all requested trials, even with zero financially funded trials, is a valid informative Monte Carlo result and should be retained. Structured funding failure is data. Fatal software failure, cancellation or rejected stale callbacks are not accepted completions. Do not reuse a policy requiring at least one funded trial.

Restore lazily and asynchronously on opening a workspace, with plan UUID/path, household/mode and view-generation guards. A newer accepted replacement takes precedence over an older load. Render without changing current next-run controls, admitting financial work or invalidating unrelated analyzers.

Use Current, Outdated, Incompatible/Unavailable and Inputs Need Attention independently from durability. Show original completion time and original assumptions. Incompatible files remain protected, not relabeled as merely outdated. Unsaved/legacy plans can run in memory; no autosave or promise of cross-session safety. Existing first-save behavior does not automatically backfill an earlier memory-only completion; a later phase must explicitly decide whether to add that handoff.

Save As binds a new UUID after successful plan save, clones historical results independently and reassesses currency; original snapshots remain unchanged. Comparisons must retain the original frozen A/B pair rather than rebind to the destination's current baseline.

### PDF preservation

`MonteCarloPdfReportAdapter.from` already uses frozen run data without consulting current controls or the live plan. Preserve that boundary and all existing tables, chart colors, population caveats and metric direction. Both views currently require COMPLETED and not-stale for export (`canExportPdf`); historical export therefore needs an explicitly authorized narrow readiness change, not merely a codec.

Allow compatible Outdated exports using original data with an historical notice and original completion timestamp; never export incompatible data as valid. Keep export time separate. Selecting chart/presentation preferences should not recalculate financial outcomes or modify the snapshot. No PDF redesign is recommended.

## 7. Capacity, performance and safety

Existing limits remain: 32 MiB raw, 16 MiB gzip, 4,096-character number, 2,000,000 tokens, 100,000 array/container bound, depth 80, string length 256,000. Bounded compressed storage alone does not bound decoded maps, references or reconstructed work. Preserve numeric scale and long compounded tokens without rounding.

No MC durable codec exists, so there are **no measured MC snapshot JSON/gzip sizes or serialization/restoration timings in this audit**. No production-sized run or compression benchmark was executed. Existing focused tests provide deterministic small results, not a production capacity proof. `MonteCarloFixtures.household` supplies five accounts, SS, pension, Roth conversion and a 30-year horizon; `MonteCarloFoundationTest` uses small runs including eight trials and seed 417. These are suitable codec proof fixtures, not substitutes for realistic capacity tests.

Structural estimates, not measurements:

| Shape | Storage-driving evidence | Illustrative annual observations |
|---|---|---|
| Fixed standalone | O(N × 14) terminal metrics/failures + O(Y) aggregates/reference | No retained N × Y annual trial map |
| Longevity standalone | O(N × Y) annual asset prefixes + O(N) lifetime/terminal/failure | 5,000 × 60 = 300,000 |
| Either paired mode | O(2 × N × Y) annual asset prefixes + O(N) paired terminal/failure | 5,000 × 60 × 2 = 600,000 |

At an illustrative 30 bytes per JSON year/value observation, 600,000 observations alone require 18,000,000 bytes before outcomes, summaries, context or long decimals. At 10,000 paired trials and 60 years that becomes 36,000,000 bytes, exceeding 32 MiB before overhead. These are arithmetic estimates with an explicitly assumed representation, not expected compression ratios or measured application sizes. Token and container limits can fail before byte limits. Domain maximum 100,000 trials amplifies the problem. Mortality has variable horizons and failure prefixes, so use actual distributions and conservative reconstruction bounds.

Recommendation: retain existing limits and require a capacity gate before promising persistence at every supported run size. Measure small deterministic fixtures, then real independently evaluated runs at relevant trial/horizon boundaries once, with bounded heap. Record raw/gzip bytes, token/container/depth/number maxima, elapsed serialization/decode/reconstruction, heap methodology and JVM/hardware. Do not assume Exhaustive's large interning gain applies to genuinely different stochastic balances.

If full fidelity does not fit, present measured alternatives for approval: a more compact explicit annual-value layout with exact scalars and validated ordering, or separately bounded components with manifest/atomic last-success acceptance and recovery. Neither is implemented or preapproved. Never silently discard retained evidence, round values, reduce allowed run counts or increase limits. A too-large save should retain the accepted memory result and previous durable file with an accurate save failure.

## 8. Tests inspected and executed

Executed baseline with IntelliJ bundled Maven, project-local `.codex-m2/repository`, `-DargLine=-Xmx4g`:

`MonteCarloFoundationTest,MonteCarloRandomStreamsTest,MonteCarloPairedWorldTest,MonteCarloPairedAggregationTest,MonteCarloStrategyComparisonTest,MonteCarloInflationRegressionTest,SinglePersonMonteCarloTest,MonteCarloSessionTest,MonteCarloComparisonSessionTest,MonteCarloPdfExportTest`.

**84 run, 84 passed, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS; Maven total 18.828 seconds.** Log: ignored `target/monte-carlo-phase1-audit-baseline.log`. Full suite and native/visual previews were not run for this audit. PDF export tests passed; no new manual rendered-page inspection is claimed.

Relevant additional inventory: `MonteCarloFundingTest`, `MonteCarloFinalizationTest`, `MonteCarloProgressTest`, mortality distribution/execution/results/accumulator tests, world-generator and survivor tests, inflation tests, paired aggregation integration, state-tax MC integrations, view/mortality/inflation/comparison/fan-model/presentation tests, optional preview and benchmark tests. Preserve all seeded expected values and existing Phase 9 codec/store/identity/restoration/transactional tests.

Proposed new tests: fixed/mortality/paired DTO fidelity; all fourteen metrics including absent spouse; exact scale and conditional denominators; full failure records; all-funding-failed accepted completion; cached-summary fidelity; frozen chart/PDF context; restart in new controller/view; invalid pending inputs; relevant/irrelevant/reverted fingerprints; unknown schema/reference mismatch; no engine calls on restore/export; cancel/fail/stale replacement retention; plan-switch/load races; Save Failed/retry; independent Save As; atomic-write fault injection; drain; malformed counts/indices, oversized documents, expansion bounds and capacity fixtures. Extend current session and PDF tests rather than duplicate the financial engine suite.

## 9. Proposed implementation stages and approval gates

1. **Contracts and fixed standalone proof.** Approve slot taxonomy and complete snapshot inventory; introduce MC-specific DTO/fingerprint/compatibility adapters alongside existing infrastructure. Round-trip fixed run/context exactly; prove frozen weighted/SS/Exhaustive compatibility. Measure realistic fixed payloads before UI wiring.
2. **Fixed accepted-completion and restoration.** Separate historical result from job state; wire gated handoff, asynchronous restoration, save statuses/retry, original timestamps and frozen PDF context. Gate on restart, all-failure-result handling, cancellation, races, Save As and unchanged seeded regressions.
3. **Longevity standalone.** Preserve sampled lifetimes, annual maps, mortality provenance and cached aggregates, with independent single/couple inputs. Gate on capacity at real trial/horizon dimensions, zero-engine restoration, exact denominators and historical PDF fidelity. Seek storage-policy approval if current bounds cannot accommodate required evidence.
4. **Existing paired comparison persistence.** Preserve both frozen strategies and shared-world provenance; retain individual annual prefixes and original paired summary. Gate on exact A-minus-B calculations, joint failure states, frozen baseline, long-horizon capacity and cross-session comparisons/PDFs. No new simulation algorithm.
5. **Optional saved-result comparison.** Separately approve a historical selector and compatibility matrix. Begin with qualified descriptive comparisons; enable paired analysis only for proven matched worlds with sufficient retained observations. Do not imply same seed alone establishes pairing.
6. **Acceptance.** Focused tests then complete Maven suite, existing seeded/golden regressions and Phase 9 persistence; rendered charts/PDFs, native Windows restart/Save As/failure/shutdown acceptance. No remaining individual SS persistence work is needed as a dependency.

## 10. Risks and decisions requiring approval

- Approve eight per-shape/mode slots versus one last result per dialog. Exactly-one-last-success retention is recommended per distinct analysis mode, with no history browser initially.
- Approve preserving the full current compact result graph, including retained annual trial evidence, rather than a smaller view-only aggregate archive. Full projections/market paths not currently retained are outside the minimum restoration requirement.
- Resolve capacity policy only after codec measurements. Existing UI run sizes must remain runnable even if persistence cannot commit them.
- Approve historical PDF export readiness/labeling and completion timestamps; current stale export restriction is a verified behavioral boundary.
- Decide whether first successful plan save may attach a previously accepted memory-only result without rerunning. Existing store text currently advises save and rerun; no automatic backfill exists.
- Reproducibility requires effective plans, reference-data/algorithm provenance and actual world-generation contract, not only seed. Persisting a result is not a promise that future changed algorithms recreate it.
- AGENTS.md's baseline alias warning is partly outdated: current `RetirementPlanSnapshot.fromRetirementPlan` lines 114–127 deep-copies via JSON. Getters still expose mutable domain objects. Comparison capture makes private copies; freeze those effective inputs instead of assuming baseline object immutability forever. No baseline semantic change is proposed.
- Monte Carlo sessions own their executors rather than the Social Security job controller. Reuse persistence I/O ownership without replacing financial scheduling or inadvertently altering existing admission behavior.
- Exact financial payloads and contexts are sensitive data. Keep diagnostics to counts, sizes, versions, stages and configured limits; do not log result `toString`, plan JSON, balances or personal details.

## 11. Git status and files changed

Only added file: `MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-1-AUDIT.md` (this audit). Expected final short status is `?? MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-1-AUDIT.md`; the index remains unchanged. Maven artifacts/logs are under ignored `target/`. No staging, commits, pushes, resets, discards or production/test edits were performed.

Stop for review and authorization before implementation.
