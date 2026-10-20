# Phase 9A — Persistent Social Security Analysis Results Architecture Audit

## 1. Executive summary and recommendation

**Audit only.** No persistence, UI, financial, schema, dependency or PDF implementation is included. Inspected October 19, 2026, on `main`, HEAD `0fa51e012a5683bc9e82e30ce57f175146134378`; initial working tree clean.

Recommend one immutable **last accepted successful snapshot** per plan identity, household shape and analysis type, stored as separate versioned **gzip JSON**, outside primary plan JSON. Use dedicated storage DTOs, exact decimals and ordering, frozen request/report contexts, SHA-256 mode-specific input fingerprints, and independent storage/algorithm/reference compatibility versions. Retain safely readable outdated results, including their existing details, heat maps and supported PDFs. Never replace the last success with cancelled, failed, rejected or obsolete work.

Feasible without changing financial algorithms. Existing calculation records, bounded detail retention, pure presentation adapters, job admission and transactional file writing are foundations, not an existing persistence system. Implementation needs explicit decisions on plan identity, unsaved recovery, Save As/copy behavior, completed searches containing candidate failures, and shutdown draining.

Calculation completion, acceptance and disk commit are different events. Closing a dialog after acceptance must not cancel storage. A crash before commit cannot guarantee retention of the newly computed result; it must preserve the previous intact committed result. Never report “saved” before commit.

## 2. Current architecture and evidence conventions

Paths are repository-relative. Class references below use these exact directory aliases:

| Alias | Directory |
|---|---|
| UI | `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/` |
| APP | `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/` |
| SS | `src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/` |
| PERSIST | `src/main/java/com/daviddunn/retirementplanner/persistence/` |
| EXPORT | `src/main/java/com/daviddunn/retirementplanner/app/export/` |
| TEST | `src/test/java/com/daviddunn/retirementplanner/` |

### Verified source findings

| Component | Actual responsibility / implication |
|---|---|
| UI `SocialSecurityStrategyAnalyzerDialog` | `runAnalysis`, `runIntegratedAnalysis`, `runExhaustiveSearch`, `runWeightedAnalysis` capture requests, submit work and render retained presentations. Presentations/report contexts are dialog fields. `close` disposes jobs/listeners, without durable storage. |
| UI `AnalyzerNavigationView` | Retains workspace Nodes and per-mode scroll positions. Selection changes containment without calculation. This preserves an instance's state, not results across dialog reconstruction. |
| UI `SinglePersonIntegratedView` | Owns deterministic completed analysis and separate SS/weighted views. `start` copies plan, captures context and requests nine ages with nine retained details. |
| UI `SinglePersonMortalityView` | Each SS/weighted instance owns independent factor/date/discount controls, completed result and currency flag. `render` uses frozen result values. |
| UI `SocialSecurityAnalyzerJobController` | `start`, `eligible`, `finish`, `invalidate`, `close` enforce generation/revision/cancellation gates, not persistence. |
| APP `SocialSecurityAnalysisJobCoordinator` | One application-level worker admission lease, retained through cleanup. Daemon executor; shutdown cancels the active lease. No result-write drain. |
| UI request factories | `SocialSecurityStrategyAnalysisRequestFactory.create/createIndividual`, `SocialSecurityAnalyzerInputs.quick/exhaustive`, `LongevityWeightedAnalysisRequestFactory.capture/create` translate authoritative inputs and isolate integrated plan copies. |
| Controller | `src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java`: current plan/file, source revision/listeners, longevity session settings, projection caches. Lifecycle hooks: `newPlan/open/save/saveAs`. No analysis repository. |
| Plan | `src/main/java/com/daviddunn/retirementplanner/domain/model/RetirementPlan.java`: household, portfolio, assumptions, Roth request, baseline, non-investable assets. No durable plan UUID or plan-name field. Household name is not unique identity. |
| Persistence | PERSIST `JsonRetirementPlanRepository.save/load`: complete plan JSON and household-reference validation; no completed analysis graph. |
| Shutdown | `src/main/java/com/daviddunn/retirementplanner/ui/MainApplication.java::stop`: closes analysis coordinator only. |

Phase 8C report **exists and implementation is present**: one retained shared couple configuration host is hidden for irrelevant modes; current inputs/reference/execution/completed results have contextual sections. Phase 8B direct navigation remains. Earlier nested-tab descriptions in AGENTS.md are historical, not the current hierarchy.

Existing: immutable lists/value records in many outputs, retained results, frozen PDF contexts, stable within-run occurrence IDs, current/stale flags, cancellation and transactional primary-plan saves.

Partially reusable: calculation records through explicit codecs; pure table/heat-map/report adapters; atomic writer and fault-injection seams; controller source listeners.

Missing: durable identity/binding, snapshot schema/codecs, completion timestamp, compatibility registry, semantic fingerprints, application-owned last-success store, restore entry points, outdated export readiness, write-failure presentation and orderly persistence shutdown.

## 3. Exact result-data inventory and restore dependency maps

Preserve everything the current completed presentation can show, not information the engine never retained. No restore-time financial calculations. Preserve structured failures rather than Throwable graphs. JavaFX state is handled separately.

### 3.1 Couple Social Security Only

Success supplies dialog `RunResult(context, result)`. Snapshot both UI `SocialSecurityStrategyAnalysisContext` and SS `SocialSecuritySurvivorClaimingOptimizationResult`.

Optimization result: request, retirement grid, retained retirement cells, primary/spouse survivor candidate lists, every evaluated complete strategy, highest precise PV, all highest-tied strategies, stage-two strategy count and deterministic evaluation count. Grid: request, ordered axes, joint mortality scenarios, cells, highest nominal/real/PV rankings and evaluation count. Each optimization cell: complete strategy and `SocialSecurityMortalityWeightedStrategyValue` (nominal, real, PV, primary-selected benefits, spouse-selected benefits, evaluation count).

Context: primary/spouse names, exact FRA monthly amounts, valuation years, original current claim dates, authoritative mortality categories/factors, mortality table metadata and longevity assumptions. `SocialSecurityStrategyAnalyzerPresentation` retains result/ranked rows, **not the whole context passed to render**; persisting only that presentation loses frozen context.

Map: `RunResult` → result + context snapshot → `SocialSecurityStrategyAnalyzerPresentation.from(result)` → dialog `render(context, presentation)` → Recommendation, Claiming Grid, Top Strategies, Assumptions. Ranking uses precise PV, competition ranks and stable original tie order. No existing couple SS-only PDF to add.

### 3.2 Couple Deterministic Quick Comparison

APP `IntegratedSocialSecurityStrategyComparisonResult`: current-plan baseline, supplied candidate count, ordered unique entries. Entry: caller order, complete strategy, optional SS expected values, integrated result or failure, and metric differences. `IntegratedSocialSecurityStrategyResult`: evaluated strategy, full `Projection`, `ProjectionMetrics`, advanced-path flag, warnings.

Also store original selected SS candidates, original SS ranks, exact expected values/order, candidate count and prerequisite provenance. `IntegratedSocialSecurityComparisonPresentation.from(comparison, selected)` needs the second argument to recover original SS ranks, including deduplication behavior.

Map: comparison + selected candidates → `IntegratedSocialSecurityComparisonPresentation.from` → dialog `renderIntegrated` → existing baseline/candidate table/details. No existing Quick PDF.

### 3.3 Couple Deterministic Exhaustive Search

APP `IntegratedSocialSecurityCompleteStrategySearchResult`: baseline, ranking measure, all generation-ordered entries, ranked-successful ordering, current-plan rank. Entry: `generationOrder`, complete elections, optional after-tax-estate rank, metrics, differences, retained detail or failure. Preserve full baseline and **only actually retained** candidate projection details, including annual `ProjectionYear` and nested financial records. Preserve UI runtime and `IntegratedReportContext`.

`domain/projection/summary/ProjectionMetrics.java` contains fourteen fields: investment growth, income, total taxes, peak annual tax, ending investable assets, ending net worth, after-tax estate, lifetime portfolio withdrawals, Roth conversions, RMDs, Medicare premiums, household/primary/spouse SS. Nullable spouse SS means absence, not observed zero.

Map: complete result + runtime → `ExhaustiveIntegratedSearchPresentation.from` → `renderExhaustive`; `DeterministicHeatMapAdapter.from` → `ClaimingStrategyHeatMapModel` → existing view/details. PDF: context + presentation + map/metric/selection + actual ordered grouped rows + frozen SS reference → `IntegratedAnalyzerReportAdapter.deterministic` → EXPORT `IntegratedAnalyzerReport` → `IntegratedAnalyzerPdfExporter`.

Top displayed groups are not a complete persistence source: all compact entries are needed for heat-map winners, unlisted strategy details, exact ties and failures. Grouping uses a BigDecimal-valued key, so blanket scale normalization can change grouping.

Retained projection codec inventory from `domain/projection/ProjectionYear.java`: projection/calendar year, primary reporting age, beginning assets/growth, guaranteed income and authoritative household SS result, annual expenses/cash-flow need, portfolio/tax-funding withdrawals, statutory/before-projection/in-projection/excess RMD, ending assets and ordered account snapshots, AGI/taxable SS/federal taxable income/deduction/tax, state result and Michigan detail where present, Medicare calculation, requested/actual/owner-specific Roth conversions, retained non-qualified opening/growth/ending assets, household cash settlement, combined effective rate, heir tax and after-tax estate. Preserve nested source attribution explicitly: `getIncomeSources` and Michigan optional detail are `@JsonIgnore` in the runtime class, another reason automatic Jackson serialization of the result graph is insufficient. Computed aliases need not become independent stored amounts, but their restored values must agree. Every constructor/default path needs a codec test before claiming retained annual detail equivalence.

### 3.4 Couple Longevity-Weighted Integrated Search

APP `LongevityWeightedIntegratedStrategyComparisonResult`: objective, metadata, ordered entries, ranked-successful entries, optional baseline, work, elapsed time, completion status, optional equivalence plan, evaluations avoided.

Metadata: methodology version/reference, longevity assumptions, valuation date, general inflation, real discount, original scenario count/probability, financial limitations. Work: stage-four/scenario/projection counts and completed counts, continuation evaluations and `LongevityContinuationWork`.

Entry: `inputOrder` (candidate occurrence identity; baseline 0), complete strategy, aggregate or failure, optional scenario details, optional competition rank, PV/nominal differences. `LongevityWeightedStrategyAggregate`: expected PV after-tax estate, expected nominal estate, expected investable assets, minimum/maximum nominal scenario estate, evaluated probability, original scenario count, actual projection run count. Preserve equivalence groups: presentation `provenEquivalentCount` reads them.

UI `LongevityWeightedAnalysisRequestFactory.create` requests `aggregateOnly()`. No hypothetical full scenario-projection graph should be stored or promised. If details are actually retained by a supported future policy, its codec must preserve them explicitly.

Also store frozen `CurrentStrategyBaseline` elections/source rows, optional complete strategy/problem, `IntegratedReportContext`, and historical cross-analysis comparisons. Revisions are provenance, not durable currency.

Map: result + baseline + context → `LongevityWeightedIntegratedPresentation` → `LongevityWeightedIntegratedView.render` → original rankings/baseline/details/methodology/technical folds. `LongevityWeightedHeatMapAdapter.from` rebuilds map; `IntegratedAnalyzerReportAdapter.weighted` supplies existing PDF.

### 3.5 Individual Deterministic Integrated

APP `SinglePersonIntegratedAnalysis`: complete search result, insertion-ordered `breakEvenByClaimingAge`, primary name, start/end year, optional primary death year. Preserve baseline, nine candidate outcomes/retained projections, `BreakEvenAnalysisResult` contents and `IndividualReportContext`.

Map: snapshot → `SinglePersonIntegratedView.render` → existing table/details/Break-Even action. PDF: `IndividualAnalyzerReportAdapter.deterministic(context, completed, selectedAge)` → shared report/exporter via `IndividualPdfExportAction`. Do not replace individual nine-age output with a couple heat map.

### 3.6 Individual Social Security Only

APP `SinglePersonMortalityAnalysis.Result`: mode, primary name, current claiming age, `IndividualLongevityScenarios`, valuation date, discount, COLA, inflation, nine entries in generation order, projection count. Entry: primary-only strategy, expected PV, expected nominal, nullable expected investable assets, outcome list. SS mode has empty integrated outcomes and absent integrated assets; preserve these distinctions. Mortality includes distribution/request, table metadata, partial-year convention and scenarios. Preserve `IndividualReportContext`.

Map: result → SS-only `SinglePersonMortalityView.render` → table/details/frozen summary; `IndividualAnalyzerReportAdapter.mortality` → existing PDF. Reconstruct ranks from exact entries, without benefit calculation.

### 3.7 Individual Longevity-Weighted Integrated

Same result contract, explicitly `Mode.INTEGRATED`, with independent inputs. Each entry additionally retains current `Outcome(deathYear, probability, nominalEstate, investableAssets, presentValue)` rows. These are not full per-scenario projections. Preserve current-age marker, ordering and report context.

Map: snapshot → weighted `SinglePersonMortalityView.render` → existing table/details; `IndividualAnalyzerReportAdapter.mortality` → existing PDF. Do not merge individual mortality controls during restore.

### Shared presentation and currently lost information

`IntegratedAnalysisComparisonPresentation.compatible/create` joins exact elections using source revisions. Dialog `capturePdfReport` reads another live completed presentation; exhaustive's SS reference can refresh after a new SS result. Freeze historical comparison rows, source result IDs/fingerprints and unavailable explanations. Never silently join an old restored result to an unrelated new result. A later explicit compatible comparison can update a snapshot attachment transactionally without changing core financial results.

No inspected result contract supplies durable completion time: report contexts contain **run start**; exporters also record export time. Add accepted-completion Instant separately. SS render context and Quick original selected ranks must be captured before their callback-local values disappear.

Selected tab/strategy, metric, sort and scroll are JavaFX state. Optionally persist validated view preferences separately or in a small optional snapshot section; identify strategies by exact elections plus occurrence ID, never row index. Tables, maps and formatting are derived presentation; frozen inputs/outputs/report contexts are durable data. Never serialize controls, Nodes, Scenes, listeners, tasks or executors.

## 4. Snapshot DTO and serialization recommendations — proposed

Use explicit versioned storage DTOs. Runtime records are good conversion sources, but Optional/OptionalInt, polymorphic objects, package-local UI types and mutable `Projection.addYear` make automatic runtime graph serialization an unstable contract. Existing Jackson Java-time support does not establish a codec for these graphs. Use explicit nullable/tagged success-or-failure storage fields rather than adding a dependency merely for Optional.

Illustrative envelope only, not implemented code:

```json
{
  "snapshotSchemaVersion": 1,
  "planIdentity": "opaque-plan-uuid",
  "householdShape": "COUPLE",
  "analysisKind": "COUPLE_LONGEVITY_WEIGHTED",
  "runId": "opaque-run-uuid",
  "completedAt": "2026-10-19T14:00:00Z",
  "applicationBuild": "diagnostic-build-id",
  "algorithmCompatibility": {"weighted": "v1", "projection": "v1"},
  "referenceVersions": {"mortality": "version-and-content-digest"},
  "fingerprintVersion": 1,
  "inputFingerprint": "sha256-of-canonical-effective-inputs",
  "payloadDigest": "sha256-of-canonical-payload",
  "requestContext": {},
  "calculationResult": {},
  "reportContext": {},
  "historicalComparisons": [],
  "viewPreferences": {}
}
```

Proposed `AnalysisKind` has seven explicit supported household/mode values; job `Mode.EXHAUSTIVE` alone cannot identify individual deterministic output. Payload family: `CoupleSocialSecuritySnapshot`, `QuickComparisonSnapshot`, `DeterministicSearchSnapshot`, `WeightedComparisonSnapshot`, `IndividualDeterministicSnapshot`, `IndividualMortalitySnapshot` with validated SS/integrated kind. Share election/metric/projection-year/mortality/report-section DTOs, not a second financial model.

Preserve every original BigDecimal token and scale; no doubles or formatted currency as numerical truth. Copy nested mutable projections into immutable annual records. Preserve null/absent semantics, original ranks/order and explicit occurrence references. Deduplicate repeated objects using within-snapshot IDs and validate all references. Versioned pure presentation transformations may reconstruct maps/grouping, but must match original exact values/ties/order; financial engines must never run to restore.

## 5. Storage alternatives, plan identity and cache location

| Format | Size/performance | Recovery/versioning/portability | Decision |
|---|---|---|---|
| Plain JSON | Readable exact decimals; repeated projections can be large; bytes unmeasured | Simple explicit schema, atomic sibling replacement, easy inspection | Keep as inspection representation; viable fallback if compression measurements disappoint |
| Gzip JSON | JDK compression; extra CPU; actual savings unmeasured | Same schema, standard decompression tools; validate stream/checksum and enforce decoded limits | **Recommended initial storage**, no new library |
| SQLite | Useful indexed transactions/history; blobs still need explicit codecs | Additional driver, DB schema, locking/backup support | Not justified for one last snapshot per mode |
| Explicit binary | Potential compactness/speed, unmeasured | Bespoke schema tooling/migrations; poorer diagnostics | Defer; reject default Java object serialization |

Store one atomic file per slot, e.g. `<root>/<plan-id>/<shape>/<kind>.json.gz`. Optional small index is rebuildable, never an authority requiring an atomic multi-file commit with the payload. Enforce compressed/decompressed size, collection count and nesting limits. Disable unsafe polymorphic default typing; checksum detects corruption, not malicious modification by the same user.

Recommend `%LOCALAPPDATA%/RetirementPlanner/analysis-results/v1` on Windows via a platform-aware location service. Explicitly define/test fallback and non-Windows paths; do not migrate current settings as a side effect. No network. Snapshots contain sensitive financial data and frozen assumptions: treat them as **user data**, not automatically evictable cache. Preserve through updates; provide backup/export/import/delete guidance in 9E. Local storage is not encryption.

Sidecars improve portability with plans but inherit unwritable/cloud/network folder issues and can be separated on move/copy. Prefer application-data storage plus explicit portability. Embedding large results in primary JSON couples plan saving to expensive results and is rejected initially.

### Identity recommendation and approval gate

Current `RetirementPlan` has no durable identifier; controller uses in-memory identity/currentFile. Recommend an **optional persisted UUID**, assigned to new plans and adopted by legacy plans on explicit successful save. This is a future additive plan-format change requiring approval; none is made here. Local bindings bridge legacy/unsaved identities.

If plan-field changes are excluded, a path-to-opaque-ID registry is feasible, but moves/copies need explicit reassociation. It cannot transparently supply portable identity. Fingerprints and household names cannot substitute for identity.

| Event | Recommended behavior, pending approval |
|---|---|
| Save | Same identity; input fingerprints determine currency |
| First Save As of untitled plan | Bind session UUID only after successful plan save; accepted writes retain original key |
| Save As of saved plan | New independent identity; explicitly clone last snapshots with origin provenance if approved; old slots retained |
| External rename/move | UUID survives; update location binding on explicit open; ambiguous multiple live paths require resolution |
| External copy | Copied UUID creates ambiguity: ask moved original versus independent copy, fork on explicit copy; no silent slot overwrite |
| Identical names/content | Distinct identities; content equality is validity, not ownership |
| Delete plan | Retain snapshots until explicit deletion; missing-plan recovery/export path |
| Household change | Separate SINGLE/COUPLE slots; retain former-shape historical results outside wrong-shape workspace |
| Legacy JSON | Open unchanged; registry identity until optional UUID is written on explicit save |
| Unsaved plan | Session UUID plus durable unattached-result index; no nonexistent filename dependency |

Unsaved recovery requires an approved “Unattached saved analyses” listing with frozen source descriptor, timestamp and export/reassociation actions. Do not silently activate or autosave a plan. Without this surface, promising preservation of untitled analyses across restart is incomplete. Explicit reassociation is safer than content-based merging.

## 6. Input fingerprint dependency matrix

Proposed SHA-256 over canonical **effective mode-specific request contracts**, not generic revision or arbitrary whole-plan JSON. Separate financial digest, frozen contextual provenance and plan identity.

Dependency groups:

- **S**: represented DOBs/ownership, SS FRA benefits/valuation years, source/election facts used by request, plan SS COLA, current elections for baseline/current-marker/context, candidate-age/survivor universe and optimization settings.
- **M**: authoritative mortality categories/factors, conditioning date, table content/version, partial-year/terminal conventions.
- **V**: independent valuation date and real discount rate.
- **F**: effective projection inputs: start/contextual horizon; ordered account types/owners/balances and inherited/basis/participant/opening-RMD facts; ordered income/pension amounts/dates/COLA/survivor/qualification; ordered spending/dates/recurrence/inflation; return/general/healthcare/SS assumptions; filing/state/all tax-growth/future/heir settings; withdrawal priorities; Roth schedule/target/stop; non-investable/estate inputs; effective death/survivor context; applicable benefit/government/tax/RMD/Medicare reference versions. Unsupported-boundary facts affect outcome success too.
- **B**: optional current-strategy baseline elections, sources and completeness/problem state.
- **Q**: count and current SS prerequisite input/algorithm digest, exact selected candidates/order/ranks/precise expected values.

| Mode | S | M | V | F | Additional / exclusions |
|---|---|---|---|---|---|
| Couple SS | Yes | Shared couple | Shared | No | Grid/optimizer settings; balances/spending/taxes/configured horizon do not drive this calculation |
| Couple Quick | Through Q and baseline | Through Q | Through Q | Configured deterministic | Count and exact candidate basis; no independent mortality override |
| Couple Exhaustive | Yes including baseline | No | No | Configured deterministic | Ranking/detail policy; shared longevity/PV edits must not stale it |
| Couple Weighted | Yes | Shared couple | Shared | Scenario context | B; exact second-death ending, not configured horizon |
| Individual deterministic | Primary only | No | No | Deterministic | Optional primary death-year override, nine ages, Break-Even context |
| Individual SS | Primary only | Own SS controls | Own SS controls | No | Current-age marker; frozen inflation reference discussed below |
| Individual weighted | Primary only | Own weighted controls | Own weighted controls | Exact individual death context | Per-death outcomes; no spouse/survivor baseline |

Source: listed UI request factories; APP `SinglePersonMortalityAnalysis.calculate`, `SinglePersonIntegratedAnalysis.calculate`, `IntegratedSocialSecurityStrategyEvaluator.evaluate/extractCurrentStrategy`, `LongevityWeightedIntegratedStrategyEvaluator.evaluate`; `domain/projection/ProjectionEngine.java` uses portfolio, assumptions, withdrawal and timing contexts. Weighted explicitly uses `withExactEndingYear(deathYear - 1)`; scenario timing overrides configured deterministic timing.

This is a dependency design, not a claim that every field omission is already proven safe. 9B must enumerate all current financial model fields, classify their getter/use paths and mutation-test each inclusion/exclusion; no default ignored bucket. Include baseline/current-marker dependencies even when candidate ranking alone is unchanged.

Frozen contextual assumptions need a separate digest: individual SS reads/stores general inflation for Result/PDF but does not use it to calculate own-benefit PV. Weighted configured horizon/death descriptions may change while scenario objectives do not. Show an assumption-context Outdated reason without pretending those changes altered PV. Conservative overall Outdated is safer than claiming changed explanatory assumptions are current.

### Canonicalization and currency rules

1. Typed versioned keys in deterministic order; UTF-8, fixed enums, ISO dates/Year/YearMonth. UTC timestamps are provenance, not financial inputs. Exclude random run IDs/export times/object identity/locale.
2. Use effective values after existing legacy/default interpretation. Preserve meaningful null/omitted distinctions; invalid explicit state configuration must not silently become Michigan. Invalid pending UI text is “Inputs need attention,” not a fabricated valid fingerprint.
3. Preserve payload scale always. Fingerprint V1 should encode unscaled decimal plus scale conservatively because existing grouping uses scale-sensitive keys. Numeric normalization may be introduced per field only after proving equivalent calculations/presentation. Harmless scale-only edits may conservatively show Outdated; do not claim blanket `stripTrailingZeros` is safe.
4. Preserve execution order for accounts/incomes/expenses/candidates/scenario aggregation. Sort only declared unordered keys/sets. Account order controls source execution priority.
5. Names/paths/formatting are frozen provenance, not financial identity. Name-only edits need not stale calculations; show original result-time names. Exclude unrelated stored plan baselines/UI preferences only after use-path proof.
6. Include reference content digests and semantic versions, not filenames alone. Relevant resources: `src/main/resources/mortality/ssa-period-life-table-2022.csv`, `rules/government-rules-2026.json`, `tax/state/state-tax-2026.json`, `tax/state/iowa-tax-2026.json`. Prefer mode/state-specific dependency sets; unrelated state data should not stale SS-only.
7. Validate derived claim dates/universe against captured inputs; do not mix newly derived and older stored values. Do not run optimization merely to compute currency. Use a pure inexpensive dependency extractor rather than expensive request/scenario construction on FX.
8. Returning effective inputs to original values restores Current if semantic compatibility matches. Revisions remain in-flight safety gates, not cross-session identity.

## 7. Current / Outdated / Incompatible model

Keep loadability, currency, execution and durability as independent axes.

| State | Required behavior |
|---|---|
| No saved results | No intact recognized snapshot; existing Run offered |
| Current | Safe decode/validation and matching financial/context/algorithm/reference dependencies; normal existing views |
| Outdated | Safe decode, mismatching relevant dependencies; full previous results retained, original completion time visible |
| Incompatible or Unavailable | File exists but unsupported schema/semantics, corrupt/checksum/count failure, inaccessible or wrong binding; preserve evidence, explain, never call it current |

Outdated banner: **“These results were generated using previous plan or analysis assumptions and may not reflect the current retirement plan.”** Include completion timestamp, frozen plan identity and mismatch reasons. Another plan's old results must not appear under the new plan's current header. Former household-shape results remain recoverable without forcing them into an incompatible workspace.

Known older algorithms can remain readable Outdated; unsupported metric interpretation needs a reader adapter or Incompatible. A replacement run overlays status while old results remain; no silent recalculation or replacement.

## 8. Last-successful lifecycle and precise publication boundary

Existing `SocialSecurityAnalyzerJobController.finish` checks ownership/generation/revision/cancellation, transitions IDLE, calls observer, and **rechecks eligibility** before success. Preserve both checks. Computation finished but cancelled before publication is not accepted success (`cancelAfterComputationBeforePublicationSuppressesSuccess`).

```mermaid
sequenceDiagram
    participant Job as Existing admitted job
    participant Gate as Existing publication gate
    participant Store as Application result store
    participant Disk as Atomic repository
    participant UI as Analyzer
    Job->>Gate: Completed result and frozen request context
    Gate->>Gate: Generation/revision/cancellation checks
    Gate->>Store: Accepted immutable completion and original plan key
    Store->>UI: Computation complete; saving results
    Store->>Disk: Background encode, verify, commit
    Disk-->>Store: Commit success or failure
    Store-->>UI: Saved or save failed; prior saved result retained
```

Introduce a small accepted-completion handoff before rendering can lose the result, without bypassing stale-callback rejection or introducing a new financial job engine. Capture accepted-completion Instant, keep disk completion separate. Once accepted, dialog close/navigation/plan edits cannot cancel the write or retarget its original plan key.

| Event | Proposed behavior |
|---|---|
| Successful accepted run | Enqueue complete immutable snapshot; old disk slot retained until commit |
| Navigation/dialog close/app reopen | Application-owned store survives dialog; restore asynchronously |
| Relevant edit | Recompute currency, retain original snapshot |
| Irrelevant edit | Keep current |
| Start rerun | Keep previous successful snapshot visible |
| Cancel/fail/reject/obsolete callback | No replacement |
| Successful replacement | Commit atomically, then update durable status |
| Crash during write | Previous intact primary/recognized backup recoverable |
| Missing/corrupt cache | Nonfatal; primary plan unaffected |

Weighted has `COMPLETED_WITH_FAILURES`. Recommend persisting a fully accepted completed search containing at least one successful candidate, retaining every typed failure; all-candidate-failed/baseline-fatal runs retain old success. This policy needs approval: returned DTO alone must not silently define success. Never renormalize failed scenarios or fabricate values.

Per-slot monotonic acceptance sequence and serialized commits prevent older queued results overwriting newer accepted successes. A failed replacement does not erase either the prior durable slot or newly completed in-memory result. Keep the analysis coordinator's one-job admission unchanged.

## 9. Quick prerequisite and provenance

Outdated restored SS is viewable but cannot satisfy Quick readiness. Verify restored SS input/compatibility digest against actual current shared controls, reconstruct its original precise ranking/tie order, then use existing `IntegratedSocialSecurityCandidateSelector`.

Quick digest: prerequisite input/algorithm/reference digest, count, exact ordered elections, SS ranks/precise values, and F. Keep prerequisite run ID separately as provenance; random completion time must not make identical candidate content numerically different. Live SS publication still invalidates Quick as today; only complete equality checks can later establish matching durable currency.

Saved Quick can be viewed if its source SS file is missing, but rerun requires current SS under existing rules. No automatic SS pipeline or single-person Quick.

## 10. UI restoration design

Load small index/header asynchronously on plan open; decode selected mode lazily on first workspace selection. Quick may trigger lazy SS prerequisite loading. Do not block FX on file I/O/decompression. Tag loads with plan binding/view generation so obsolete loads cannot publish into a new plan.

Decode immutable DTOs off FX; run only pure presentation transforms off FX, and attach controls/table/map data on FX. Add narrow restore methods accepting snapshot plus explicit currency state. They must not impersonate newly completed jobs, invalidate Quick as a new run, mutate next-run controls or increment job generation. Existing renderers reset some selections: apply optional validated metric/tab/strategy preferences after render, fall back to established defaults for unknown IDs.

Retain Phase 8B/8C navigation/contextual host/fixed job status/result tabs and existing tables/heat maps. No result history. Keep next-run inputs separate from frozen assumptions. Recommend an explicit **Use saved analysis inputs** action rather than silently overwriting session settings. If session defaults differ, display Outdated honestly until inputs match. Automatic first-session initialization is an alternative approval decision; individual SS/weighted settings must remain independent and couple shared settings authoritative.

## 11. PDF compatibility

| Mode | Existing PDF | Frozen sources |
|---|---|---|
| Couple SS / Quick | No | Restore screens only; no added PDF |
| Couple Exhaustive | Yes | Full compact result/runtime/context, map/metric/selection, actual grouped display order, historical SS reference |
| Couple Weighted | Yes | Complete aggregate result/baseline/context, map/selection/display rows and historical cross-analysis attachment |
| Individual deterministic | Yes | Analysis including retained details/Break-Even, context, selected age |
| Individual SS / weighted | Yes | Original mortality result/context, selected age |

Current dialog `canExportPdf` and individual readiness require current results. Outdated export is an intentional **future readiness-policy change**, not existing support. Separate safe exportability from current-input match, preserve existing busy/selection/capture rules, and add prominent Current/Outdated status plus original completedAt. Preserve original start time; distinguish export time. All facts/names/baseline/comparisons come from frozen snapshot, never today's plan. Incompatible data is not financially exportable without a tested reader.

Reuse report DTO/exporter/individual background action; narrowly extend report metadata/adapters in 9C–9E. Do not change metrics or add unsupported mode exports. PDF is not a reloadable substitute for structured snapshots.

## 12. Algorithm/schema/reference compatibility

Separate snapshot schema, canonical-fingerprint schema, per-mode financial compatibility, projection/benefit/valuation compatibility, reference content versions and optional presentation-adapter version. Build ID is diagnostic: UI-only releases should not invalidate everything.

Explicit reader registry: unknown schema retained as Incompatible; known older schema uses pure tested conversion without rewriting the only copy. Algorithm/reference changes mark readable historical results Outdated. Changed financial meaning without faithful adapter is Incompatible. Never rerun old financial results under new rules to “upgrade” them. Report/export original provenance.

## 13. Atomic persistence, recovery and shutdown

PERSIST `AtomicFileWriter` uses unpredictable sibling temps, `FileChannel.force(true)`, verification, `ATOMIC_MOVE`/`REPLACE_EXISTING`, no non-atomic fallback, regular-file/symlink checks, backup ownership records and backup rollback. `JsonRetirementPlanRepository` serializes bytes, verifies by reload and keeps recognized `.bak`. Writer is package-private and globally synchronized through `SAVE_LOCK`.

Reuse behind a **separate analysis repository**, with gzip/snapshot-specific verification, not primary-plan load. Existing byte-array interface can accept gzip without dependencies. Measure lock duration and all-bytes memory amplification before adopting for large results. If unsuitable, extract only a tested per-target/streaming write boundary; do not casually rewrite plan persistence.

Verify stream integrity/schema/digest/binding/kind/shape/counts/unique occurrences/rank references/success-failure exclusivity/report context. Preserve old destination until verified replacement, and never replace a good backup with corrupt primary. Corrupt cache primary may need explicit recognized recovery/quarantine before a new snapshot replaces its slot. Do not weaken rules because it is called a cache.

Backup is recovery redundancy, not history. Offer explicit validated recovery; identify recovered timestamp honestly. Do not delete unknown files or scan broadly. Missing/cache failure never prevents plan open or touches primary plan JSON. No unsafe path components from plan names; store IDs validated under the configured root.

Use one application-owned bounded I/O executor separate from FX/analysis admission. On failure retain new result in memory and say: “Analysis completed, but results could not be saved. The previous saved result was retained.” Offer Retry Save without recalculation. Handle disk-full/read-only/unsupported atomic move safely.

Normal exit must wait for accepted writes through visible asynchronous drain/retry/cancel-exit handling. `MainApplication.stop` alone is too late for that UX; integrate with existing close lifecycle without changing plan departure semantics. Analyzer close remains immediate. Cross-process conflicts need a dedicated per-slot OS lock during commit plus sequence validation; busy means defer/retry, not blind overwrite. No distributed lock manager.

Atomic rename/file force do not guarantee every device/power-loss outcome or directory metadata durability. Promise previous validated committed data under supported atomic conditions, not impossible absolute crash resistance.

## 14. Performance/storage findings

**No byte-size, compression-ratio, codec or reconstruction timing measurements were performed.** No persistence prototype or hour-long analysis ran. Runtime-graph serialization would not measure the recommended explicit codec, which does not yet exist.

| Mode | Verified size drivers |
|---|---|
| Couple SS | 9×9 retirement cells; stage two = retained cells × both survivor-list lengths; joint mortality scenarios and compact expected values |
| Quick | Count 1–20, default 10; fewer unique candidates possible; baseline plus full successful candidate projections grows with horizon |
| Exhaustive | 9×9×both survivor-list lengths; all compact entries, bounded retained full details plus baseline |
| Couple weighted | Same full universe factors; aggregate-only UI retention, shared metadata, optional baseline/equivalence/work; expensive runtime does not imply stored full scenario projections |
| Individual deterministic | Nine strategies/baseline, retained annual projections, nine Break-Even comparisons |
| Individual SS | Nine compact entries plus mortality, empty integrated outcomes |
| Individual weighted | Nine entries with positive-death-scenario outcomes, not full scenario projections |

Evidence: result constructor count invariants, request age lists, weighted factory nested enumeration/aggregateOnly, individual `start/calculate`. Do not assume identical survivor-list lengths for every DOB.

9B isolated measurement gate: convert existing completed test fixtures for seven modes; measure raw/gzip bytes and exact decode; count strategies/years/outcomes; time encode/decode/pure-presentation/FX attachment separately with warm-up/repetitions. Add DTO-only scale fixtures, clearly not completed financial runs. Record JDK/heap/hardware/peak memory and limits. No invented acceptance thresholds or compression ratios; no expensive new financial run required for initial codec experiment.

## 15. Tests and audit verification

### Actually executed

Used AGENTS.md IntelliJ Maven executable with project-local repository:

```text
mvn.cmd -Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository -Dtest=SocialSecurityAnalyzerJobControllerTest,TransactionalPlanPersistenceTest test
```

**57 run, 57 passed, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, 11.363 seconds.** Job controller 36; transactional plan persistence 21. Finished October 19, 2026 at 10:20:12 EDT. Ignored log `target/phase9a-focused.log`. These verify existing publication/cancellation and file safety, not snapshot implementation. No full suite, new rendered previews or native OS tests were needed or run for this documentation audit. No tests changed.

### Future automated acceptance

| Area | Existing tests to extend / new proposed tests |
|---|---|
| All payloads | `AnalysisSnapshotCodecTest`: exact decimals/scales/nulls, elections/order/ranks/failures/full retained detail/Break-Even/PDF context |
| Restart | New store/controller/view instances, empty memory; restore seven modes without engines; exact data/map/detail/report equivalence |
| Validity | Per-mode field mutation matrix, irrelevant edits, revert, scale policy, null/default legacy, list/map order, versions/reference changes |
| Last success | Extend `SocialSecurityAnalyzerJobControllerTest`: cancel/fail/reject/stale/all-failed cannot replace, accepted completion survives close, older writes cannot overwrite newer |
| Faults | Extend patterns in `TransactionalPlanPersistenceTest`: serialize/create/write/force/close/verify/backup/replace/cleanup, unsupported atomic move, corrupt primary/intact backup, shutdown/multiple processes |
| UI | `SocialSecurityStrategyAnalyzerDialogStateTest`, `AnalyzerDirectNavigationTest`, `AnalyzerContextualInputsTest`, `SinglePersonIntegratedViewTest`, `SinglePersonMortalityViewTest`: lazy-load race/shape change, pending invalid text, status/time, old results during rerun |
| Heat maps | Existing heat-map/adapter tests: all metrics/optimal/ties, failure cells, original occurrence identities, unlisted details, restore selection after render |
| PDF | `IntegratedAnalyzerPdfStateTest`, `IntegratedAnalyzerPdfReportTest`, `IndividualReportTest`: restored current/outdated frozen capture, old completion time, no live-plan leakage, unsupported-schema refusal |
| Quick | Restored current SS prerequisite; stale/missing prerequisite, exact candidate order/ranks/ties, no autoexecution |
| Identity | New `AnalysisPlanBindingTest`: first/failed/fork Save As, copy/move/rename, identical names/content, legacy adoption, unsaved recovery, shape separation, deletion/import |
| Responsiveness | Existing preview sizes; bounded load/FX attach/memory, shutdown drain, reference folds/navigation/PDF selections preserved |

Keep financial goldens/seeds/requests unchanged. Focused/full regression and actual rendered inspection belong to implementation acceptance; this audit's baseline is not end-to-end persistence verification.

## 16. Detailed Phase 9B–9E roadmap

Proposed classes only; none created here. Root package remains `com.daviddunn.retirementplanner`.

### 9B — Snapshot infrastructure

- Add scoped `app.socialsecurity.results` contracts: `AnalysisKind`, envelope/payload DTOs, `AnalysisFingerprintService`, `AnalysisCompatibilityPolicy`, `AnalysisResultStore`, `PlanAnalysisIdentityService`.
- Add persistence codecs/location/binding repository and `JsonAnalysisSnapshotRepository`; narrow controller/application lifecycle hooks and accepted-completion handoff.
- Dependencies: approve identity/legacy migration, Save As/copy, unsaved recovery, structured-failure success definition and shutdown policy.
- Tests: exact codec/fingerprint/identity/fault/sequence/drain tests, representative weighted/individual fixture round trips and measured codec limits. No production mode claimed persistent until its restore adapter ships.
- Risk: incomplete graph/identity, all-bytes memory and global lock. Review gate after infrastructure measurements; no financial changes.

### 9C — Longevity-Weighted restoration

- Modify couple weighted success/restore path, `LongevityWeightedIntegratedView/Presentation`, baseline/context mappers; individual weighted `SinglePersonMortalityView`; report adapters/metadata for historical PDFs.
- Requires 9B; freeze cross-analysis attachment even if exhaustive persistence is not enabled. No unretained scenario loading or individual-input unification.
- Acceptance: both weighted modes survive restart, exact ranking/details/map/technical/PDF, old success survives edits/cancel/failure/write faults, lazy load invokes no engine; enabled previews and regression.

### 9D — Remaining analyzers

- Add SS context/result mapper, Quick selected-rank/provenance mapper, exhaustive compact/retained-detail mapper, individual deterministic/SS mapper and restore dispatch.
- Requires stable 9B/9C contracts. Guard bounded retention, Break-Even and current markers; no new modes.
- Acceptance: all seven modes round trip; exact ranks/occurrences/annual details, Quick prerequisite, original exports and lifecycle; focused/full regressions.

### 9E — Hardening and acceptance

- Finish existing application-close coordination, recovery/portability/delete UX, version fixtures, limits and Windows process/file handling; documentation.
- Requires all modes. Risks: native permission/cloud-folder/process cases, user-data retention and support policy.
- Acceptance: crash/fault/corruption/version matrix, pending-write shutdown, cross-session moves/copies, stale PDFs, measured performance/heap, actual previews and full suite zero failures/errors with unchanged financial goldens.

Sequence identity/success decisions → canonical DTO/codec/version proof → atomic store/publication/shutdown → weighted → remaining modes → hardening. Pure codec/fingerprint inventory and proposed UI copy can proceed independently after contracts settle. Avoid broad engine/dialog rewrites; review after every phase.

## 17. Decisions, risks and recommendation

| Approval question | Recommendation / consequence |
|---|---|
| Optional durable UUID? | Yes, additive field only on explicit successful save; otherwise path registry cannot transparently survive copies/moves |
| Save As/copy identity? | Saved-plan Save As forks; explicit copy/move conflict resolution; approve whether snapshots clone with provenance |
| Unsaved recovery? | Durable unattached analysis listing and explicit association; requiring first save is simpler but narrows preservation |
| Storage/location? | Gzip JSON in application data, recognized backup, no automatic eviction; explicit portability |
| Completed-with-failures? | Accepted full result with at least one successful candidate can replace; all-failed cannot |
| Outdated PDF? | Permit only already supported modes, prominent status/completion time/frozen context |
| Last-run input restoration? | Explicit use-saved-inputs; automatic initialization needs separate approval |
| Shutdown? | Drain accepted writes before normal exit with visible retry/cancel-exit |
| Compatibility? | Readable old values Outdated; unknown semantics/schema Incompatible; build-only changes do not invalidate all |
| Retention/privacy? | Sensitive local user data, backed up/deletable/portable; never disposable cache cleanup |

Highest risks: incomplete original SS/Quick/report context, ambiguous identity, accidental last-success overwrite, false durability at exit, and false Current/export claims from runtime revisions. No financial change is necessary or recommended.

Recommend approval of **9B infrastructure only**, with identity/unsaved-recovery decisions before coding and codec/performance review before 9C. Existing admission/callback rejection remain authoritative; accepted immutable snapshots gain application lifetime ownership.

## 18. Files changed, source evidence and Git verification

Initial branch/HEAD: `main`, `0fa51e012a5683bc9e82e30ce57f175146134378`; clean status. Only new persistent deliverable: **`PHASE-9A-PERSISTENT-ANALYSIS-RESULTS-AUDIT.md`**.

Inspected evidence: AGENTS.md; Phase 8A/8B/8C reports; class/method sources explicitly cited above, including result/entry/value contracts, request factories, presentation/context/report/heat-map adapters, navigation, job controller/coordinator, plan/assumptions/projection APIs, controller/startup and transactional writer/repository; relevant existing test names/cases and both executed baseline classes. Performance proposals are not claimed measurements.

Final verification: `git diff --check` passed; separate trailing-whitespace inspection found none in the new report. Final short status contains only `?? PHASE-9A-PERSISTENT-ANALYSIS-RESULTS-AUDIT.md`. Tracked diff/stat and staged diff/stat are empty. No production/test/resource/build/schema/golden changes; index unchanged. Maven log remains ignored under `target/`. No staging, commits, pushes, resets or discarded work. Stop here; no Phase 9B implementation.

PHASE-9A-PERSISTENT-ANALYSIS-RESULTS-AUDIT-COMPLETE
