# Phase 9D — Remaining Social Security analyzers persistence audit

## 1. Executive recommendation and audit boundary

**Audit only. Phase 9D implementation has not started.** Create explicit immutable payloads and attach the five remaining modes to the existing accepted-completion store and asynchronous restoration lifecycle. Keep the repository, plan identities, gzip storage, transactional writer, bounds, diagnostics, job admission and weighted persistence architecture.

The five remaining modes match the request. Individual SS Only is the lowest-risk integration because its exact codec already exists; its completion/restoration wiring does not. Couple SS Only needs a new calculation/context payload and must precede Quick's prerequisite restoration. Quick, Exhaustive and individual Deterministic require a shared, lossless retained-projection codec, which is not present today.

Recommend a small prerequisite payload-extension proof, then **9D-1 Couple SS Only → 9D-2a retained-projection proof and Quick → 9D-2b Exhaustive → 9D-3 individual SS Only and Deterministic → 9D-4 cross-mode acceptance**. Individual SS Only could be moved immediately after 9D-1 for earlier low-risk coverage. Retain the user's main stage order and require review after each approved stage.

Two source findings materially qualify the proposed scope:

- Couple SS Only and Quick have **no existing PDF export**. Preserve their views and do not add PDF functionality. Existing PDFs to restore are couple Exhaustive, individual SS Only and individual Deterministic; both weighted PDF paths remain protected regressions.
- The envelope and codec are still specifically typed to `WeightedSnapshots.Payload`. New payload families require a narrow storage-contract extension, not a replacement persistence architecture. An additive record-field change without an explicit legacy decoder would break strict creator checks or old digests.

## 2. Baseline, evidence and path conventions

Audit date: October 20, 2026. Branch `main`; HEAD **`1ff71f3b71fca97af49a7d2058aa14284038dd9e`**. Initial `git status --short` was empty. Prior phase work has been committed since the previous session; nothing was reset or discarded.

Inspected current source, `AGENTS.md`, Phase 9A/9B/9C reports, both completed Phase 9C hotfix reports, result records, request capture, rendering, export, publication, storage, fingerprints and relevant tests. Historical architecture proposals are distinguished below from today's implementation.

Directory aliases used in this report:

| Alias | Repository-relative directory |
|---|---|
| R | `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/` |
| A | `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/` |
| U | `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/` |
| S | `src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/` |
| P | `src/main/java/com/daviddunn/retirementplanner/persistence/` |
| D | `src/main/java/com/daviddunn/retirementplanner/domain/` |
| T | `src/test/java/com/daviddunn/retirementplanner/` |

Source methods and type names below are verified. New DTO/helper/test names are **proposals**, not existing files.

## 3. Mode-by-mode readiness matrix

| Kind / current owner | Runtime request and accepted result | Snapshot readiness | Existing presentation / PDF | Work and risk |
|---|---|---|---|---|
| `COUPLE_SOCIAL_SECURITY`; `SocialSecurityStrategyAnalyzerDialog` | `SocialSecurityStrategyAnalysisContext` with `SocialSecuritySurvivorClaimingOptimizationRequest`; private `RunResult(context, SocialSecuritySurvivorClaimingOptimizationResult)` | Kind/fingerprint/success policy exist. **No payload codec or completion/load wiring** | `SocialSecurityStrategyAnalyzerPresentation`, Recommendation, Claiming Grid, Top Strategies, Assumptions. **No PDF** | New SS result/request/context DTOs; preserve all Stage 1/2 data and precise ranking. Medium |
| `COUPLE_QUICK`; same dialog | `SocialSecurityAnalyzerInputs.Quick` freezes plan and selected ranked SS candidates; `IntegratedSocialSecurityStrategyComparisonResult` mapped to `IntegratedSocialSecurityComparisonPresentation` | Kind/Q fingerprint/success policy exist. **No payload codec or completion/load wiring** | Baseline, candidate rows and metric details. **No heat map or PDF** | Exact selected SS provenance plus shared complete projection DTOs. High |
| `COUPLE_EXHAUSTIVE`; same dialog | `IntegratedSocialSecurityCompleteStrategySearchRequest`; `IntegratedSocialSecurityCompleteStrategySearchResult` mapped to `ExhaustiveIntegratedSearchPresentation` | Kind/F fingerprint/success policy exist. **No payload codec or completion/load wiring** | Grouped ranked table, baseline/gap, details, deterministic heat map, `IntegratedAnalyzerReportAdapter.deterministic` PDF | All compact entries plus actually retained projections, frozen SS cross-reference, grouping/selection. High |
| `INDIVIDUAL_SOCIAL_SECURITY`; `SinglePersonMortalityView` in SS mode | Frozen plan, `IndividualLongevityScenarios`, valuation and rate; `SinglePersonMortalityAnalysis.Result` with `Mode.SOCIAL_SECURITY_ONLY` | **Exact `WeightedSnapshots.Individual` codec already implemented/tested**, including SS mode. No production handoff/restore attachment | Nine ranked entries/details/current-age marker; `IndividualAnalyzerReportAdapter.mortality` PDF. No couple heat map | Parameterize individual attachment by explicit kind/mode, retain independent controls. Low–medium |
| `INDIVIDUAL_DETERMINISTIC`; `SinglePersonIntegratedView` | Nine-age complete-search request, optional primary death-year override; `SinglePersonIntegratedAnalysis` | Kind/death-override fingerprint/success policy exist. **No payload codec or completion/load wiring** | Nine ranked entries, selected details, stored Break-Even vs current plan, `IndividualAnalyzerReportAdapter.deterministic` PDF | Shared deterministic/projection DTOs plus complete Break-Even maps. High |

`AnalysisKind` has exactly these five and the two implemented weighted kinds. Single-person `Mode.EXHAUSTIVE` is a job-routing value, **not** the durable kind `COUPLE_EXHAUSTIVE`. Individual SS and weighted use the same runtime result class but different result modes, storage slots and independent input controls.

## 4. Existing infrastructure to reuse and necessary extension seams

### Preserve unchanged

- `ApplicationAnalysisResults.store()` lazily owns the application-lifetime `AnalysisResultStore`.
- Store `Slot(planId, kind)` separates mode and UUID; kind determines household shape. One bounded storage executor has queue capacity 8; memory entries are bounded to 64 slots. Financial work uses the existing coordinator separately.
- `accept`, `whenSettled`, `inMemory`, `retry`, `load`, `background`, `cloneSnapshots`, `drain` and shutdown contracts already implement accepted-memory versus durable state, ordered writes and retry without financial recalculation.
- `JsonAnalysisSnapshotRepository.path/read/write/claimPlan/loadBackup` implement UUID/kind/shape paths, binding/duplicate-copy guards, lock files, explicit compatibility checks and recognized backups.
- `AtomicFileWriter` remains the sole transactional writer: sibling temporary, force/close, verification, prior valid backup, atomic replacement only. Corrupt/unsupported primary files are not silently overwritten.
- Existing controller Save/Save As identity and copy-if-empty cloning, original provenance/timestamps, and exit drain remain authoritative. No autosave or new plan schema.
- Numeric-length hotfix and diagnostic retention are established requirements, not candidates for simplification.

### Actual narrow gaps

1. `AnalysisSnapshot.payload`, `AcceptedAnalysisCompletion.payload`, and `AnalysisSnapshotCodec.Content/digest` use the concrete `WeightedSnapshots.Payload`. That union accepts only couple weighted or individual mortality results. It intentionally rejects the other kinds.
2. `AnalysisCompatibilityPolicy.assessCopy` extracts either individual mortality inputs or couple weighted metadata. It must gain explicit new-family cases before Save As can independently assess all kinds.
3. `WeightedResultPersistence` already has the correct binding/generation/closed/load-versus-new-completion guards, but its loading text and report method are weighted-specific. Reuse this lifecycle through a small mode-neutral extraction/adapter; do not create five different loading/status implementations or a new framework.
4. `IndividualCompletionPersistence.attach` is hardcoded to `INDIVIDUAL_WEIGHTED`; the controller-aware dialog attaches it only to `weightedView`. `acceptedCompletion` on the SS view currently remains its no-op default.
5. Current export and Break-Even readiness for unimplemented modes uses volatile `current`/revision/stale booleans. It must distinguish safe historical export/viewing from currency, as weighted already does.

### Recommended payload-extension proof

Introduce a small explicit payload contract (`validate(kind)`, `successful()`) implemented by the existing weighted payload and new immutable families. Have the codec select a **fixed allowlisted DTO class using schema and `AnalysisKind`**, rather than default typing, Java class names from JSON or arbitrary polymorphic deserialization. Keep the same outer metadata, repository API, files and store lifecycle. The exact class name of this interface is not prescribed.

Preserve the old weighted payload's wire shape, including its explicit null family field, field order, scale, frozen context and payload digest. Use mode-specific DTO mapping for new kinds; do not add five nullable fields to the old record and assume legacy reads still work. `FAIL_ON_MISSING_CREATOR_PROPERTIES` and digest verification make that unsafe.

A schema-1 kind-dispatched extension is feasible because the new slots have never had valid payloads in this application. Prove legacy read/digest compatibility with **frozen pre-change bytes**, not newly regenerated fixtures. If this cannot be done without changing weighted wire bytes/digests or financial models, stop for review. Do not globally bump/migrate weighted snapshots merely to add new kinds. New mode payload schemas may have explicit local versions without changing existing weighted interpretation. Downgrade readers may refuse new mode slots safely; they must not corrupt known weighted slots.

Rejected alternatives: arbitrary Jackson serialization loses ignored/projection/optional fields; formatted reports lose exact data; embedding results in plans changes primary persistence; a new database/executor/history store would replace established architecture without need.

## 5. Accepted success, cancellation and last-success ownership

`SocialSecurityAnalyzerJobController.start/eligible/finish` provides the publication gate for all modes: owner/current job, generation, per-mode revision and cancellation, with an additional eligibility check after the state observer. Only the existing eligible success callback may hand off an accepted completion. Capture original UUID/file binding, immutable payload, admission context and completion timestamp; do not look up a later active plan inside the queued save.

| Mode | Exact existing `AnalysisSuccessPolicy` predicate | Failures/cancellation representation |
|---|---|---|
| Couple SS | `!result.evaluatedStrategies().isEmpty()` | Result has no retained candidate-failure alternative; fatal exception/cancellation is not a successful result |
| Quick | Any `entries().successful()` | Entry contains integrated result plus differences, or structured `IntegratedSocialSecurityStrategyEvaluationFailure`; baseline failure aborts rather than yielding a successful baseline |
| Exhaustive | Nonempty `rankedSuccessfulEntries()` | Compact entries preserve success metrics/rank/difference or structured failure; baseline is required |
| Individual SS | Exactly nine result entries | All nine own-benefit outcomes are a completed result; no partial nine-row search contract. Cancellation/fatal failure publishes none |
| Individual Deterministic | Delegates to complete-search successful ranking | Preserve candidate failures and missing Break-Even entries for unavailable candidates; no fabricated candidate result |

Do not infer acceptance from job status `Complete`, table population or candidate count alone. Existing unimplemented callbacks do not uniformly call this policy; add it at the handoff before replacing the saved result. A partial accepted deterministic search can replace prior success, with every failure retained. All-failed, cancelled, rejected or obsolete completions cannot.

For large projection DTOs, prepare immutable storage data on the financial worker **after calculation but before the eligible callback** returns its prepared completion package. Preparing data is not accepting/persisting it; rejected work must be discarded. This avoids copying large mutable `Projection` graphs on FX and prevents later mutation during asynchronous serialization. UI callbacks attach existing presentations and enqueue the already-frozen event only. Do not change coordinator admission or introduce another financial scheduler.

Starting/cancelling/failing a replacement leaves the last successful view and durable snapshot intact. A successful accepted result may replace the visible in-memory result while its save is Pending; Save Failed retains the preceding durable file, shows Retry Save and keeps the new accepted result in memory. Normal close drains existing accepted I/O. A crash before commit restores the last durable version, not an uncommitted success.

## 6. Exact DTO inventories and restore maps

### 6.1 Couple Social Security Only

Proposed `CoupleSocialSecuritySnapshot` stores:

- Entire optimization request/settings and frozen `SocialSecurityStrategyAnalysisContext`: names, FRA monthly amounts and valuation years, original current claim dates, categories/factors, mortality table metadata and longevity assumptions.
- Base strategy request: analysis/end dates, complete person elections, independent survivor dates, death dates and plan COLA. No plan/Person references.
- Both original individual mortality distributions, ordered grid axes, stored joint mortality scenarios, all grid cells and evaluation counts, all three highest-value grid rankings and ties.
- Retained retirement cells, exact primary/spouse survivor candidate lists, every Stage 2 complete strategy/value, original ordering, precise maximum/ties, Stage 2 counts and original selected-benefit/evaluation audit values.

The request uses ages 62–70; default top-retirement setting is **5**, not the Quick count **10**. Store actual settings, not these defaults. Exact ties can affect retained cells. Never regenerate joint scenarios or run the optimizer to restore. Use within-snapshot references to avoid repeating cells/distributions, and validate every reference and count. `SocialSecurityMortalityDistribution` requires explicit list reconstruction; do not assume automatic Jackson construction is lossless.

Restore: DTO → original request/context and `SocialSecuritySurvivorClaimingOptimizationResult` → pure `SocialSecurityStrategyAnalyzerPresentation.from` → dialog `render(context, presentation)`. Reuse existing Recommendation, original Button-based Claiming Grid/retained/highest markers, Top Strategies, details and result-time Assumptions. This is not the deterministic `ClaimingStrategyHeatMapView`; do not replace it or add a PDF.

### 6.2 Couple Quick Comparison

Proposed `QuickComparisonSnapshot` stores:

- Full `IntegratedSocialSecurityStrategyComparisonResult`: baseline, supplied candidate count, unique entries in original caller order.
- Each entry's full complete strategy, original SS expected-value record or absence, full integrated result or structured failure, and all exact metric differences.
- **Original selected SS list** with ranks, complete identities, precise values and order, including information preceding deduplication; requested count and source SS input/compatibility/run provenance.
- Frozen run assumptions/context, completion time and valid optional selection identity.

`IntegratedSocialSecurityComparisonPresentation.from(comparison, selected)` uses `callerOrder - 1` to recover the original SS rank. Caller-order gaps after deduplication are meaningful; do not replace them with row indices or rerank by deterministic estate. Baseline/candidate `IntegratedSocialSecurityStrategyResult` contains strategy, full Projection, fourteen metrics, advanced-SS flag and warnings.

Restore: DTO + stored selected basis → original comparison → existing pure presentation mapper → `renderIntegrated`. Historical viewing must work even if the source SS result is missing or outdated. **Only a new Run** requires a current SS result. No Quick heat map or PDF is introduced.

### 6.3 Couple Exhaustive

Proposed shared `DeterministicSearchSnapshot` plus couple context stores:

- Full current-plan baseline; ranking measure; every generation-ordered compact entry; original ranked-successful occurrence order, exact competition ranks/ties and optional current-plan rank.
- Each entry's full election identity, fourteen metrics and metric differences or structured failure; optional full retained detail **only where originally retained**, including warnings and advanced-path flag.
- Runtime, configured presentation group limit, frozen `IntegratedReportContext`, frozen SS recommendation cross-reference and its availability/provenance, completion time and validated metric/strategy selection preferences.

Do not serialize only the top 20 groups. The default backend retains up to 20 full candidate projections, but retains compact data for the complete universe (5,184 in the established couple case; actual universe dimensions depend on request). `ExhaustiveIntegratedSearchPresentation.from` groups exact BigDecimal-valued keys; scale normalization can change grouping even when numeric comparisons tie.

Restore: stored baseline/entries/ranked references → `IntegratedSocialSecurityCompleteStrategySearchResult` → existing presentation mapper with original runtime/group setting → `renderExhaustive` and `DeterministicHeatMapAdapter.from` → existing result tabs, all five supported deterministic display metrics, exact heat-map occurrence/detail navigation and `IntegratedAnalyzerReportAdapter.deterministic`.

The existing `socialSecurityCrossReference` is dynamically read from the dialog's currently available SS result, including at PDF capture. Freeze the original companion comparison at accepted completion for historical restore/export. Store exact contributing strategy/value/rank/estate inputs plus the original unavailable reason; formatted text alone is not numerical truth. Do not substitute an unrelated new SS result into an old report. A dedicated frozen SS-cross-reference DTO is clearer than putting SS PV into a field named weighted PV in the existing weighted comparison rows. Existing weighted historical comparisons remain unchanged.

### 6.4 Individual SS Only

Reuse `WeightedSnapshots.Individual.from/restore` with `INDIVIDUAL_SOCIAL_SECURITY`, not the weighted slot. Existing codec tests already round-trip a real completed nine-entry SS fixture and compare the pure PDF adapter.

Stored fields already include independent mortality distribution/request/table metadata/convention, original primary name/current claiming age, valuation date, exact discount/COLA/inflation, nine ordered complete strategies and exact PV/nominal values, projection count and original scenario metadata. SS entries have absent integrated assets and no integrated per-death outcomes. Preserve these absence rules, not zero substitutes.

Generalize `IndividualCompletionPersistence.attach` by explicit supported kind/mode. Attach it to the existing `ssView`; do not merge controls with `weightedView`. Freeze `IndividualReportContext`, use the existing mortality render/restore path, and retain its own result slot and pending input text. Existing SS PDF filtering of result-time person/SS inputs remains; never recapture context from a changed plan.

### 6.5 Individual Deterministic

Reuse shared deterministic/projection DTOs, plus a proposed `IndividualDeterministicSnapshot` containing `SinglePersonIntegratedAnalysis`'s frozen name, start/end years, optional primary death year and **complete `breakEvenByClaimingAge`**.

Break-Even data must retain each `BreakEvenAnalysisResult`: baseline/current/comparison horizons, comparable-year count, horizon-difference flag, both frozen `BreakEvenPlanSummary` values, per-metric status/annual values/crossings and first/sustained crossover years. `BreakEvenYearResult` includes year, optional ages, baseline/current/difference values. Encode maps with explicit keys and deterministic order; do not serialize enum-key maps ambiguously or recompute Break-Even during restore/export.

The view explicitly requests all nine retained details (overriding the backend default of 20). Restore via existing `render`, table selection and `IndividualAnalyzerReportAdapter.deterministic`. The current `current` boolean disables both PDF and Break-Even after edits; introduce safe historical-view/export readiness distinct from financial currency. The historical current-strategy marker remains the original baseline, not the modified live election. No spouse/survivor fields, Quick mode or couple heat map are added.

## 7. Retained projection codec: highest-risk fidelity gate

No complete persisted `Projection`/`ProjectionYear` codec exists. `Projection` is mutable (`addYear`) and does not have the immutable constructor used by ordinary result records. `ProjectedAccountSnapshot` retains a mutable Account reference. `ProjectionYear` has multiple compatibility constructors and no all-fields Jackson creator; its income-source list and optional tax details include `@JsonIgnore` paths.

Proposed `ProjectionSnapshot`/`ProjectionYearSnapshot` must capture immutable values and reconstruct through the appropriate existing constructors. Cover:

- Ordered calendar/projection years, reporting age, opening assets/growth, guaranteed income and complete authoritative `HouseholdSocialSecurityResult` selections/amounts. Preserve absent spouse components for single-person rows.
- Expenses/cash-flow need, portfolio/tax-funding withdrawals, statutory RMD versus pre-opening/in-period/excess RMD, requested/actual/owner-specific Roth conversions.
- Retained non-qualified opening/growth/ending assets and `HouseholdCashSettlement`.
- Ending investable assets and ordered account balances with **frozen** account type/owner/name and all required account facts; use within-snapshot account references, never resolve by name or against live accounts.
- Federal tax inputs/results, supported state result and Michigan optional detail, Medicare/IRMAA breakdown, effective rate, estimated heir tax and after-tax estate.
- Income-source attribution: kind/purpose, optional owner/account/destination, description, exact gross/optional taxable amount, optional tax year/pension/participant/inherited facts. Unknown taxable amount must remain absent, not zero.

Use explicit allowlisted account/state variants and nullable optional forms. Reuse existing constructors/value records only where round-trip proof confirms every field; do not enable arbitrary runtime polymorphism or serialize Throwable graphs. All fourteen `ProjectionMetrics` fields and nullable spouse SS must survive unchanged.

**Acceptance gate:** compare every relevant getter/nested value after reconstruction, including ignored fields, old/null shapes and all currently produced state/account variants. Constructor defaults cannot silently replace recorded data. Current full `ProjectionYear` construction is available, but compatibility constructor shapes need explicit tests. If an accepted retained graph cannot be reconstructed without new financial behavior or broad domain changes, stop and propose a reconstruction-only alternative for approval. Do not weaken assertions or omit annual detail to fit the budget.

Pure validation, grouping, formatting and rebuilding a Projection with stored years are permitted restoration operations. ProjectionEngine, tax calculators, SS optimization, mortality searches and BreakEvenAnalyzer must not run.

## 8. Existing fingerprint dependencies and currency integration

Use `AnalysisFingerprintService` Version 1; do not replace it with a plan revision. It canonicalizes exact decimal value/scale, stable object-key order and meaningful list order. Identity, stored unrelated baselines and display names are excluded from integrated F. All top-level financial plan fields are classified; unknown added fields fail closed rather than being silently ignored.

| Kind | Existing digest dependencies | Integration requirements |
|---|---|---|
| Couple SS | S + shared M + shared V | S includes DOBs, ordered owner SS records, FRA amounts, claiming ages/dates, valuation years and plan COLA; M includes categories/factors/conditioning; V includes valuation/discount. Balances/spending/taxes do not enter. Preserve fixed request universe/settings and compatibility metadata |
| Quick | S + configured F + Q | Q requires count 1–20, source prerequisite fingerprint, exact ordered `Candidate(strategy, rank, values)` list. Mortality/valuation enter via the current SS prerequisite, not independent controls |
| Exhaustive | S + configured F | No shared M/V. Preserve ranking/universe/detail policy as frozen request/version context; no new override or mortality dependency |
| Individual SS | S + independent M/V | General inflation is additionally in `effectiveContextFingerprint` because it is displayed. Do not claim an SS-only financial dependence on spending/accounts |
| Individual Deterministic | S + configured F + optional primary death-year override | No M/V. Keep blank death year absent and malformed pending text as Inputs Need Attention |

F conservatively includes ordered account/balance/ownership/inherited/opening-RMD facts, incomes/pensions/qualification, expenses, economic/tax/state/withdrawal/Roth/non-investable/death/survivor assumptions as currently serialized. Do not redesign dependency extraction in this phase. UI sort, selected heat-map metric/cell and navigation are not financial inputs. Names can change while financial currency remains Current; old report labels stay frozen.

Algorithm maps and content digests from `AnalysisVersions` remain separate from plan-input digests. SS-only references exclude government/state rules; Exhaustive and individual Deterministic exclude mortality tables. Quick includes its mortality-based SS prerequisite dependencies. Application build identifiers alone do not invalidate finance.

For assessment use actual current controls, including raw DatePicker editor text and pending numeric text. Do not manufacture matching fingerprints from last-valid session values. Capture accepted-run fingerprints from the actual frozen admitted inputs separately. A result can remain viewable while current controls need attention.

### Quick's prerequisite after restoration

Lazily restore/load SS metadata/results before declaring Quick ready. Restoring SS **must not** call `runAnalysis` or its accepted-new-result callback, invalidate Quick as a new calculation, or increment financial job generations. A restored compatible SS snapshot can satisfy readiness only if its effective fingerprint and compatibility match current controls/plan.

Store source run identity for provenance, source effective fingerprint/compatibility and exact selected candidates for semantic Q. An outdated/missing SS snapshot never satisfies new-Run readiness. Quick's old result remains independently viewable even when its source snapshot is unavailable or has been replaced; preserve the frozen candidate basis inside Quick.

Do not misclassify “current prerequisite unavailable” as corrupt/incompatible Quick data. Use Outdated with an explicit prerequisite explanation while keeping the existing Go to Social Security Only action; malformed pending text remains Inputs Need Attention.

Review decision: use semantic prerequisite fingerprint plus exact candidate basis, **not a random run ID**, for currency. An identical SS rerun can then yield the same Q and permit Current reassessment. Keep the existing `SOCIAL_SECURITY_RESULT` job invalidation/eligibility gate unchanged. The old volatile UI stale text is not the durable fingerprint contract; this distinction needs explicit acceptance tests rather than silently making every rerun permanently stale.

## 9. Async restoration, status and historical views

Reuse the Phase 9C lifecycle with one binding per explicit kind, and a small common status adapter. Lazily load when a workspace is first selected; avoid queuing seven large loads on plan open. Quick may request its SS prerequisite through the same bounded store. Subsequent visits reuse the instance/model; navigation does not run analysis or reset inputs.

Decode, validate/digest and build pure immutable presentations on the storage worker. Only control attachment happens on FX. Retain the existing generation, UUID, normalized file-path and disposed-view checks. A new accepted result increments the view's load generation so an older load cannot replace it. Never resolve an accepted write against the currently selected plan after the fact.

The source controller's plan-revision notification can precede current-file assignment. Preserve the Phase 9C deferred `sourceChanged`/binding handling across every mode. Clear another plan's visible data immediately on binding change, then restore the correct slot. Do not clear the same plan's historical result merely on a relevant edit. A household-shape change retains separate historical slots but does not automatically rebuild the dialog; preserve the existing reopen/shape guards.

Status conventions remain: No Saved Results, Current Results, Previous Results — Outdated, Incompatible or Unavailable, Inputs Need Attention; durability separately Pending/Saved/Save Failed/Memory Only. Always show original completion time. Outdated data stays fully inspectable; unsupported storage or financial semantics is explicitly unavailable, not partially reinterpreted. Existing protection preserves incompatible files and may block replacement until explicit recovery; do not add a silent delete/overwrite policy here.

Keep next-run controls independent of historical assumptions. Unsaved/legacy plans remain runnable and memory-only; saved-plan binding is required for durability. No autosave or new unattached-analysis browser. Preserve the established lack of automatic first-save backfill unless separately approved.

## 10. PDF and selection preservation

| Mode | Existing export | Required restoration work |
|---|---|---|
| Couple SS | None | No new exporter; restore all current views/context |
| Quick | None | No new exporter; restore baseline/rows/details and exact candidate provenance |
| Exhaustive | `IntegratedAnalyzerReportAdapter.deterministic` → existing PDF exporter | Original `IntegratedReportContext`, full calculation result, heat-map model/metric/selection, selected occurrence, original grouped data and frozen SS cross-reference |
| Individual SS | `IndividualAnalyzerReportAdapter.mortality` → `IndividualPdfExportAction` | Original `IndividualReportContext`, mortality result and current-age marker, selected claiming age; no live-plan substitution |
| Individual Deterministic | `IndividualAnalyzerReportAdapter.deterministic` → same action | Original context, deterministic result, selected age and safe historical readiness; retain Break-Even for UI even though current PDF adapter does not consume that map |

Current results export normally; compatible historical results remain exportable with the Phase 9C historical notice and original completion/start time distinct from export time. Existing layouts, financial sections, tables, metrics and palette remain unchanged. No engine calls during capture.

Selection preferences are optional validated presentation metadata. Use complete elections/within-result occurrence identity or individual claiming age, never a current TableView row index. Restore valid preferences; otherwise use existing defaults. Do not make a selection or table sort financially invalidate results. Preserve scale-dependent grouping and current table-order capture rules. Continuous disk writes for every selection are not required; agree whether to capture preferences at accepted completion only or add bounded explicit preference persistence separately. Do not rerun an analysis merely to restore selection.

## 11. Capacity audit and required measurements

**No new codec prototype, synthetic cache file or large analysis was implemented in this audit.** New mode sizes are not measured yet; ordinary runtime-object Jackson dumps would not measure a correct lossless snapshot.

| Mode | Verified dimensions / known retained graph | Measurement and capacity gate |
|---|---|---|
| Couple SS | 81 standard retirement cells; actual retained Stage 1 ties, two survivor lists and all resulting Stage 2 strategies; joint mortality scenarios | Store shared distributions/cells once via validated references. Measure full standard output and tie-heavy cases; do not duplicate mortality context per strategy |
| Quick | UI count 1–20, default 10; baseline plus every successful unique candidate's full horizon | Up to 21 full projections, not merely 20 compact metrics. Long horizons/many accounts/income attributions may challenge raw/token/array limits |
| Exhaustive | Complete compact universe, baseline, up to default 20 candidate projection details | Compact metrics plus differences for every occurrence and 21 full projections are the highest shared DTO/capacity risk. Measure actual supported universe and retained details together |
| Individual SS | Nine ordered entries, distribution, zero integrated outcomes | Historical Phase 9B exact fixture: **9,778 raw / 3,952 gzip bytes**, not newly measured today. Existing real codec baseline passes |
| Individual Deterministic | Nine requested retained details plus baseline, configured horizon, Break-Even metric-year maps | Measure all ten projections and complete Break-Even maps; do not omit maps or annual data based on a smaller table-only fixture |

Established context, not a capacity guarantee for new modes: the preceding numeric fix measured a 5,184-entry high-scale weighted fixture at 28,239,515 raw bytes; the user reports real weighted success at 29,978,548 raw bytes. Weighted success cannot prove that deterministic retained projections fit the same limits.

For each future codec measure raw/gzip bytes, token/array counts, maximum numeric length, decode/validation and pure presentation/PDF reconstruction time, and peak/approximate memory where practical. Record JDK/heap, fixture dimensions, warm-up/methodology and repeated observations. Use bounded deterministic fixtures, not hour-long searches. Counts/horizon formulas above are source facts, **not invented byte estimates**.

Within-snapshot account tables and projection references can remove duplicated data without dropping facts. Preserve exact ordering and nulls. Do not lower detail retention, round numbers, omit ignored fields or raise any safety limit to obtain a green fixture. If faithful representative payloads exceed 32 MiB/16 MiB or structural limits after lossless sharing, stop with evidence and request a storage-budget decision.

All new parsing must retain the 4,096-character numeric check and privacy diagnostics, strict schema/kind dispatch, checksums and atomic verification. Extend the diagnostic field-name allowlist for new DTO record components without permitting arbitrary user keys or financial values in logs.

## 12. Proposed implementation stages and acceptance gates

### Prerequisite within 9D-1: extension proof

Likely changes: R `AnalysisSnapshot`, `AcceptedAnalysisCompletion`, `WeightedSnapshots` only as needed to implement the common payload contract; P `AnalysisSnapshotCodec`; R `AnalysisCompatibilityPolicy.assessCopy`; a small U common persistence-status adapter extracted from `WeightedResultPersistence` with existing weighted adapters unchanged. New proposed `CoupleSocialSecuritySnapshots` lives under R, with pure mappers under U where UI context types are package-local.

Acceptance: pre-change weighted gzip fixtures/digests read exactly; weighted Current/Outdated, heat maps, PDF, cloning, numeric limits, fault handling and Retry Save remain green. No global schema rewrite, financial engine or job-scheduler changes. Stop if existing weighted compatibility cannot be demonstrated.

### 9D-1 — Couple SS Only

Implement explicit request/result/context mapping, immutable completion package and success-policy handoff in `runAnalysis`; kind-specific load/render/status integration in the dialog. Restore into the existing SS presentation. Add prerequisite metadata publication that identifies a restored current SS result without imitating a newly calculated callback. Update copy assessment for this kind.

Acceptance: exact full grid/expanded rankings/ties/context, reopen/restart, invalid pending input, stale/revert, cancelled/fatal/rejected rerun retention and independent weighted slots. No SS PDF feature. Measure the complete representative snapshot before enabling persistence. Focused tests and full Maven suite; stop for review.

### 9D-2a — Retained projection proof and Quick

New proposed R `ProjectionSnapshots`/`DeterministicSnapshots`/`QuickSnapshots` with explicit immutable scalar/state/account/attribution variants and exact reverse mappers. Extend kind dispatch and copy assessment. Modify only necessary dialog Quick completion/render/readiness/context seams; keep existing selector and comparison service.

Acceptance: all retained projection getters/nested optional state compare exactly; max-20 Quick candidates, caller-order dedup gaps and SS ranks/provenance survive; missing/outdated SS blocks only a new Run; historical Quick remains viewable. Failed write/retry retains exact accepted output. No Quick heat map/PDF. Capacity proof plus focused/full suites; review before Exhaustive.

### 9D-2b — Exhaustive

Reuse proven deterministic DTOs; map full compact universe, ranked references, retained details/runtime/context and frozen SS comparison. Adapt `runExhaustiveSearch`, `renderExhaustive`, `socialSecurityCrossReference`, `canExportPdf(false)` and `capturePdfReport(false)` as narrowly needed. Reuse `DeterministicHeatMapAdapter`, `ExhaustiveIntegratedSearchPresentation` and exporter.

Acceptance: all original occurrences/competition ranks/scale-based groups, five heat-map metrics/colors/markers/selections, retained-versus-unavailable detail, frozen Current/Outdated PDFs and no cross-analysis contamination. No modifications to weighted result calculation or frozen comparisons. Capacity proof plus focused/full suites; review.

### 9D-3 — Individual SS Only and Deterministic

First parameterize `IndividualCompletionPersistence` for the already-proven SS DTO and attach the existing `ssView`, preserving independent weighted controls. Then integrate `SinglePersonIntegratedView` through the shared deterministic codec plus complete Break-Even DTOs. Extend safe historical PDF/Break-Even readiness and kind-aware copy assessment; preserve existing rendering/adapters.

Acceptance: nine entries/current markers, independent SS/weighted controls, optional deterministic death override, no spouse or Quick capability, exact Break-Even curves/status/crossings and current/outdated frozen PDFs, Save As/reopen and per-kind independence. Measure both complete snapshots; focused/full suites after each approved integration; review.

### 9D-4 — Cross-mode acceptance

Exercise all seven slots across distinct plans, UUID/path bindings, independent Save As clones, incomplete clone failures, household shapes, concurrent accepted writes, failed/cancelled replacements, plan switch/close during restore, outdated export and unchanged weighted behavior. Use the existing application-owned store/drain/diagnostics; no new history browser or automatic rerun.

Acceptance: every new mode passes exact input/output/restoration/PDF checks where supported; all original weighted and financial golden/seeded Monte Carlo regressions pass; size/time evidence is recorded; enabled previews are inspected at 1920x1080, 1366x768, 1180x820 and 900x650 logical budgets. Native Windows DPI/Narrator/high-contrast checks remain explicitly manual unless actually performed.

## 13. Regression plan and concrete test reuse

Extend existing fixtures and tests, do not duplicate financial suites or regenerate goldens:

| Coverage | Existing tests to reuse/extend | New targeted tests proposed |
|---|---|---|
| Storage/codec/fingerprint | `AnalysisSnapshotCodecTest`, `AnalysisSnapshotRepositoryTest`, `AnalysisResultStoreTest`, `AnalysisFingerprintTest`, `AnalysisSuccessPolicyTest`, `WeightedNumericLengthTest`, `WeightedSnapshotSaveHotfixTest` | `RemainingSnapshotCodecTest`, `ProjectionSnapshotCodecTest`; frozen old weighted-byte compatibility fixtures |
| Accepted callbacks/admission | `SocialSecurityAnalyzerJobControllerTest`, existing individual/weighted completion tests | Remaining-mode callback tests for accepted/partial/all-failed/fatal/cancelled/stale/closed outcomes |
| Couple SS/Quick | `SocialSecurityStrategyAnalyzerDialogInputTest`, `IntegratedSocialSecurityComparisonPresentationTest`, candidate-selector tests, SS optimization/grid tests | `CoupleSocialSecurityRestorationTest`, `QuickRestorationTest`, explicit current-prerequisite and frozen-candidate tests |
| Exhaustive | `DeterministicHeatMapAdapterTest`, `ClaimingStrategyHeatMapViewTest`, `IntegratedAnalyzerPdfStateTest`, `IntegratedAnalyzerPdfReportTest`, complete-search calculator tests | `ExhaustiveRestorationTest`, all metrics/occurrences, frozen SS-cross-reference and Outdated PDF text/data |
| Individual | `SinglePersonIntegratedViewTest`, `SinglePersonMortalityViewTest`, `IndividualReportTest`, `SinglePersonDeterministicStrategyTest`, `SinglePersonMortalityAnalysisTest` | `IndividualRemainingRestorationTest`, full Break-Even fidelity and historical-readiness coverage |
| Identity/recovery/lifecycle | `AnalysisIdentityTest`, `TransactionalPlanPersistenceTest`, `WeightedRestorationTest` | Seven-kind Save As/clone/binding matrix; delayed callback/plan switch and mixed-slot write failures |
| Financial protection | Existing couple golden, deterministic/mortality projection, federal/state/RMD/Roth and seeded Monte Carlo suites | Assert no financial engine invocation during decode/render/PDF/Retry Save; no financial assertion changes |

Each new mode: accepted-success write; exact value **and scale**; ordering/occurrence/rank/tie/absence; fresh repository/store/controller/view restart; same-name different UUID; switching back to each plan; Current→Outdated→Current; irrelevant selection/sort; invalid text; algorithm/reference/schema mismatch; cancellation/failure retaining prior success; pending/save-failed/retry and shutdown; corrupt primary/intact backup; old-byte preservation on failed verification. Use synthetic disposable plans only.

For supported PDFs compare pure report financial sections/visualizations/frozen context before/after restore, plus actual generated PDF text for historical status and completion time. Do not demand byte-identical PDF files containing export timestamps. For unsupported PDFs assert no new export action. Verify selected strategy and all supported heat-map metrics without calculating anything. For individual Break-Even compare the full stored result map and navigation, not only a final crossover summary.

## 14. Main risks and decisions requiring approval

| Priority | Risk / decision | Recommendation |
|---|---|---|
| 1 | Concrete weighted-only envelope; strict creator/digest compatibility | Approve a minimal mode-dispatched payload extension with pre-change weighted-byte acceptance first. No weighted migration or architecture replacement |
| 2 | Lossy or mutable retained Projection graphs; ignored attribution/state details | Require explicit codec proof before Quick/Exhaustive/individual Deterministic; stop if domain behavior must change |
| 3 | Retained projections may exceed raw/token/array budget | Measure faithful representative graphs; retain all existing limits; seek approval with evidence for any budget change |
| 4 | Restored SS mistakenly invalidates Quick, or outdated SS satisfies readiness | Separate passive restoration, semantic currency and existing in-flight eligibility; retain exact Q provenance |
| 5 | Historical PDF/companion comparison reads live plan or live SS | Freeze original context/cross-reference; compatible outdated export remains allowed, unsafe incompatible data remains unavailable |
| 6 | Plan change races, partial clone and kind collisions | Reuse UUID+file+shape+kind/generation guards; extend copy assessment without replacing identity/store contracts |

Approval decisions for implementation:

1. Authorize 9D-1 and the payload-extension compatibility proof; keep subsequent stages separately reviewable.
2. Confirm no new couple SS/Quick PDF functionality, consistent with actual current capabilities.
3. Confirm semantic Quick prerequisite currency rather than random run-ID currency, while leaving financial job invalidation gates intact.
4. Confirm original frozen Exhaustive SS cross-reference for historical display/PDF, including unavailable-at-completion cases.
5. Confirm completion-time validated selection capture plus existing defaults when absent. Any continuous preference-saving enhancement should be separately bounded/reviewed.
6. Retain explicit Unavailable for unsafe incompatible snapshots and existing preserved-file/replacement behavior; no new destructive recovery policy or automatic limit increase.

No approval question blocks finishing this audit: the recommendations are concrete for review, and no implementation assumption has been applied.

## 15. Inspection inventory and executed verification

Reports inspected: `AGENTS.md`; `PHASE-9A-PERSISTENT-ANALYSIS-RESULTS-AUDIT.md`; `PHASE-9B-PERSISTENT-ANALYSIS-INFRASTRUCTURE.md`; `PHASE-9C-LONGEVITY-WEIGHTED-PERSISTENCE-RESTORATION.md`; `PHASE-9C-WEIGHTED-SNAPSHOT-SAVE-HOTFIX.md`; `PHASE-9C-WEIGHTED-NUMERIC-LENGTH-FIX.md`.

Principal inspected source files (aliases from Section 2):

- R: `AnalysisKind.java`, `AnalysisSnapshot.java`, `AcceptedAnalysisCompletion.java`, `WeightedSnapshots.java`, `SnapshotContext.java`, `ApplicationAnalysisResults.java`, `AnalysisResultStore.java`, `AnalysisFingerprintService.java`, `AnalysisCompatibilityPolicy.java`, `AnalysisSuccessPolicy.java`, `AnalysisVersions.java`.
- P: `AnalysisSnapshotCodec.java`, `JsonAnalysisSnapshotRepository.java`; prior writer/recovery design was cross-checked against the completed reports and repository calls. No writer change is proposed.
- U: `SocialSecurityStrategyAnalyzerDialog.java`, `SinglePersonIntegratedView.java`, `SinglePersonMortalityView.java`, `IndividualCompletionPersistence.java`, `WeightedResultPersistence.java`, `AnalysisSnapshotPresentationMapper.java`, `SocialSecurityStrategyAnalysisContext.java`, `SocialSecurityStrategyAnalysisRequestFactory.java`, `SocialSecurityAnalyzerInputs.java`, `SocialSecurityAnalyzerJobController.java`, `IntegratedSocialSecurityComparisonPresentation.java`, `ExhaustiveIntegratedSearchPresentation.java`, `DeterministicHeatMapAdapter.java`, `IntegratedReportContext.java`, `IndividualReportContext.java`, `IndividualAnalyzerReportAdapter.java`. Export/job/status call sites in the dialog were traced directly.
- A: `IntegratedSocialSecurityStrategyComparisonResult.java`, `IntegratedSocialSecurityStrategyComparisonEntry.java`, `IntegratedSocialSecurityStrategyResult.java`, `IntegratedSocialSecurityCompleteStrategySearchResult.java`, `IntegratedSocialSecurityCompleteStrategySearchEntry.java`, `IntegratedSocialSecurityCompleteStrategySearchRequest.java`, `SinglePersonIntegratedAnalysis.java`, `SinglePersonMortalityAnalysis.java`; comparison-service failure/cancellation paths.
- S: `SocialSecuritySurvivorClaimingOptimizationRequest.java`, `SocialSecuritySurvivorClaimingOptimizationResult.java`, `SocialSecuritySurvivorOptimizationSettings.java`, `SocialSecurityMortalityWeightedClaimingGridRequest.java`, `SocialSecurityMortalityWeightedClaimingGridResult.java`, `SocialSecurityStrategyRequest.java`.
- D: `projection/Projection.java`, `projection/ProjectionYear.java`, `projection/ProjectedAccountSnapshot.java`, `projection/summary/ProjectionMetrics.java`, `income/IncomeSourceAttribution.java`, `income/HouseholdSocialSecurityResult.java`, `financial/Account.java`, `breakeven/BreakEvenAnalysisResult.java`, `breakeven/BreakEvenMetricResult.java`, `breakeven/BreakEvenYearResult.java`, `breakeven/BreakEvenPlanSummary.java`.
- UI lifecycle: `ui/controller/ApplicationController.java` Save As/binding integration; `ui/MainWindow.java` drain hooks; `ui/MainApplication.java` shutdown hooks.
- Test source inspected: `AnalysisSnapshotCodecTest`, `AnalysisFingerprintTest`, `IntegratedSocialSecurityComparisonPresentationTest`, `IntegratedAnalyzerPdfReportTest`, `IndividualReportTest`, `SinglePersonIntegratedViewTest`; additional existing coverage was inventoried for the proposed extension plan. Tests run are listed separately below.

Executed a focused current-source baseline using IntelliJ bundled Maven and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository -DargLine=-Xmx8g`:

`AnalysisFingerprintTest,AnalysisSuccessPolicyTest,SocialSecurityAnalyzerJobControllerTest,AnalysisSnapshotCodecTest,WeightedRestorationTest,WeightedNumericLengthTest`

**97 run, 97 passed, zero failures/errors/skips; BUILD SUCCESS**, 27.577 seconds, October 20, 2026 at 08:37:40 EDT. Log: ignored `target/phase9d-audit-baseline.log`. This includes the existing exact individual SS codec case and weighted/numeric regressions; it does **not** demonstrate the five missing production integrations.

No new tests, codec prototypes, user cache files or production analyses were created. Existing baseline tests use their normal disposable fixtures. The full suite and visual previews were not rerun for this documentation-only audit; they are explicit acceptance requirements after each approved implementation stage. The previous numeric-fix report records its historical 3,028-test full green suite, not a fresh audit run.

## 16. Final Git verification and stop gate

Only `PHASE-9D-REMAINING-ANALYZERS-AUDIT.md` is added by this audit. No production Java, tests, resources, CSS, financial goldens, dependencies, primary plan schemas or persistence behavior changed. Generated Maven output stays under ignored `target/`; the configured local Maven repository stays ignored.

`git diff --check` passes. The untracked report also passes a separate trailing-whitespace check. Tracked diff/stat and staged diff are empty. Final `git status --short` is exactly:

```text
?? PHASE-9D-REMAINING-ANALYZERS-AUDIT.md
```

No files were staged, committed, pushed, reset or discarded. **Stop here: 9D-1 and every later implementation stage require explicit authorization.**
