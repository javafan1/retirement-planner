# Phase 12C — Complete Projection Snapshots and Baseline/Current Swap Audit

## 1. Executive summary

**Audit only. No implementation is authorized or included.** Current source supports a strong reusable financial-result codec and historical analyzer infrastructure, but does not yet support complete original standard-projection persistence or an atomic baseline/current swap.

Verified gaps:

1. `ProjectionBaseline` persists saved inputs, local capture time and description, not original annual results. Baseline capture currently reads the live plan, not the accepted current result's frozen request.
2. Phase 12B freezes current configured inputs in memory, with accepted-result generation guards. This context and ordinary projection caches are not persisted.
3. `RetainedProjectionSnapshot`/`RetainedProjectionCodec` provide lossless annual financial reconstruction. They do not include non-investable annual projections, ordinary dashboard income summaries, a complete editable-input document, chart presentation provenance or a standard-projection repository slot.
4. Normal MainWindow refresh unconditionally invalidates projections and can run the engine. It cannot be used for a no-calculation swap or restored-result attachment.
5. Existing analyzer storage is one replaceable last-success slot per plan/shape/kind. A pointer to that slot cannot permanently preserve the analysis associated with a baseline after a later analysis replaces it.
6. `ProjectionSummaryService` calls `IncomeSummaryService`, which invokes `SocialSecurityBenefitCalculator`. Restoring only annual Projection data and calling the existing summary service would violate calculation-free historical restoration.

**Recommended architecture:** versioned immutable projection artifacts containing exact inputs/results and frozen display dependencies; immutable pinned copies of existing analyzer payloads where available; a small versioned plan role manifest naming current and baseline artifacts; a separately staged, atomic in-memory role-exchange command followed by ordinary explicit Save. Reuse existing codecs, bounded asynchronous work, locks, atomic verified writes, backups and privacy diagnostics. Do not embed every Monte Carlo result in plan JSON or duplicate it into every chart.

Approval is required for new optional persistence metadata, immutable artifact retention/ownership, exact baseline capture semantics, legacy restrictions, unsaved-edit handling and swap coordination. No financial algorithm, ranking, mortality convention, fingerprint digest, dependency or accepted navigation rule needs to change. Metadata admission/filtering must be extended narrowly and proven digest-neutral before coding.

## 2. Preparation and verified existing persistence

Read AGENTS.md, Phase 12A/12B, Phase 9B infrastructure, Phase 9D-2 projection codec/interning/integration/PDF material, Monte Carlo Phase 2B codecs/capacity, Phase 10 configuration transactions, Phase 11D, chart and Break-Even documentation. Inspected the actual controller, MainWindow, domain snapshots/results, repositories, fingerprints, chart adapters, analyzer restoration and tests. Source takes precedence over older reports. In particular, the AGENTS baseline shallow-copy caveat is outdated for `RetirementPlanSnapshot.fromRetirementPlan()`: its current implementation JSON-deep-copies the five configured financial roots. The public constructor/getters still expose mutable domain objects.

| Existing object/path | What is actually preserved | Important boundary |
|---|---|---|
| `RetirementPlan` JSON | UUID, live household, portfolio, planning assumptions, Roth request, non-investable inputs, optional baseline and analyzerConfigurations | Editable configuration, not an accepted-result archive |
| `ProjectionBaseline` | `snapshot`, `savedAt` LocalDateTime, `description` | No original annual results, result ID, original calculation timestamp or calculation-version record |
| `RetirementPlanSnapshot` | Household/persons/income/expenses, portfolio/accounts, planning assumptions, Roth request, non-investable inputs | No recursive prior baseline/configuration section; no guaranteed immutable API or preserved legacy field-presence provenance |
| Normal `Projection` | Ordered annual ProjectionYear graph in controller memory | Mutable `addYear`; annual objects contain account references; cannot serialize the domain graph blindly |
| Phase 12B `ProjectionInputs` and Accepted context | Recursively immutable exact configured values, current run ID, identity/completion/revision in memory | Comparison format is not yet an allowlisted editable-plan restoration codec |
| `RetainedProjectionSnapshot` v1 | Annual results, account registry, taxes/state/Medicare, SS results, income attribution, conversions, RMDs, cash settlement; optional integrated metrics/strategy/warnings | Standalone component, not a full normal-plan snapshot; rejects orphan attribution references |
| Phase 9 `AnalysisSnapshot` | Plan/kind/shape/run/time, algorithms/references, fingerprints, payload digest, immutable family payload and context | Last-success slot file is replaceable, not an immutable pinned history reference |
| `analyzerConfigurations` | All 15 independent last-accepted typed configurations and provenance in plan JSON | Not a result, trial history, heat map or executable substitute for a prerequisite |
| Settings JSON | Last-opened-file and application settings | Not authoritative projection/role state |

`JsonRetirementPlanRepository` performs verified atomic plan replacement, backups, locking/cooperative configuration-only coordination and external-change checks. Configuration-only writes preserve financial numeric tokens and do not save dirty financial edits. `JsonAnalysisSnapshotRepository` binds UUID/path/shape/kind, verifies payloads and uses AtomicFileWriter. Its path ends in `<kind>.json.gz`; replacing that file changes the last-success slot. Save As saves the new plan identity first and then copies compatible historical slots asynchronously, with honest copy warnings. It is not currently a multi-file all-or-nothing snapshot-generation commit.

## 3. Proposed immutable complete projection artifact

Proposed names describe responsibilities, not approved production APIs:

```text
CompleteProjectionSnapshot v1
  snapshotId, sourceRunId, ownerPlanId, originPlanId
  householdShape, inputSchemaVersion, presentationVersion
  preparedAt, calculationStartedAt, completedAt, capturedAt
  inputDocument, inputDigest, account/entity occurrence bindings
  retainedProjection (existing explicit annual DTO family)
  annualNonInvestableResults
  frozenSummary (metrics, statistics, income summary and source labels)
  chartContext (people/elections, event markers, presentation conventions)
  calculationProvenance (application build, engines/rules/reference identifiers/digests)
  warnings, limitations, status = COMPLETE
  optional immutable analyzer attachment references
  component digests, logical content digest
```

The artifact ID identifies an accepted financial result, not its current role. Role exchange must not regenerate IDs or calculation timestamps. Capture/role-assignment times are separate from original calculation times. Unknown legacy provenance is unavailable, never the current build falsely attributed to an old run. A partial insufficient-funds prefix is not a COMPLETE accepted projection.

### Input and account identity design

Store a bounded, allowlisted frozen financial-input document covering the existing five roots, excluding recursive baseline, accepted analyzer configurations, caches and artifact manifests. Exact BigDecimal value and scale remain intact. Reuse the financial snapshot field contract, not mutable domain objects or unrestricted polymorphism. Preserve null/missing/legacy-default provenance where actually known. Phase 12B's value tree can feed parameter comparison but must not be assumed to be a complete inverse domain constructor.

Use artifact-local account/occurrence identifiers and explicit role/portfolio bindings. These do not require a broad durable account-ID migration. Reconstruction creates an independently editable financial plan and a separate detached result graph: **never share mutable Account instances between editable inputs and historical results**. Inside each reconstructed projection, preserve authoritative account/source-attribution identity relationships. Charts consume the result's registry or frozen chart context, not accounts matched by name against the active plan. Equal-looking accounts remain distinct.

Existing retained capture gathers account definitions from ending-account snapshots. An attribution reference absent from that registry is rejected. Phase 12D must first test real ordinary projections, exhausted accounts and all supported variants; if a fuller prepared account registry is needed, introduce a deliberately versioned wrapper/capture extension after approval. Do not silently repair references by name or weaken the old codec. The current explicit account allowlist covers eleven types, including inherited variants; state results include optional jurisdiction/status/limitations and Michigan detail. Future unrecognized variants must fail safely.

### Exact results and summaries

Reuse all annual fields from the retained codec, including authoritative household/own/survivor SS values and correct absence of spouse data. Keep exact opening/ending balances, growth, expenses, withdrawals, RMD components, requested/executed owner-specific conversions, tax-funding, retained non-qualified assets/cash settlement, income-source attribution, optional taxable amounts, pension facts, federal/state/Medicare values and estate fields.

The fourteen `ProjectionMetrics` values are not the entire Summary contract: seven core totals/assets plus lifetime withdrawals/conversions/RMD/Medicare and household/primary/spouse SS are covered, but dashboard IncomeSummary and other statistics also require explicit frozen capture. Store the completed summary values/definitions rather than rerunning Social Security summary calculations on restore. Annual non-investable result values/totals are separate from ProjectionYear and must also be captured, not grown again from saved inputs.

Reuse annual results for standard series. Freeze small display/event context where inference would otherwise read mutable/current people, today's date, current tables or a changed presentation definition. Numerical rendering may derive series from stored values; it must not rerun financial calculations. Persisting a cached summary/annotation is justified when necessary to preserve an original definition, not permission to duplicate the full result per graph.

## 4. Exact baseline capture lifecycle

**Existing:** MainWindow confirms replacement; controller `saveCurrentAsBaseline()` calls ProjectionBaselineFactory on the current live plan, sets baseline, clears baseline caches and marks modified/source revision. No accepted Projection is required or copied. BaselineProjectionService later rebuilds a plan from saved inputs and calls the current ProjectionEngine. Clearing is model-supported through `setBaseline(null)`; there is no dedicated complete-role transaction.

**Proposed capture:**

1. Require a completed accepted projection bundle and verify plan/binding epoch, run ID and source input identity. Resolve drafts/stale financial edits; never capture current live inputs alongside older results.
2. Capture/validate the full immutable bundle from that accepted run, including non-investable results, summaries and chart context prepared for the same inputs. Assemble these during successful projection completion, not on the baseline-button click from live fields.
3. If an existing compatible complete artifact already represents that accepted run, reference it rather than copying its annual graph. Pin eligible immutable analyzer attachments separately.
4. Stage/validate new role references; atomically publish the baseline role and its paired saved-input view in memory. Mark dirty once. No ProjectionEngine invocation occurs on baseline capture.
5. Ordinary Save verifies all referenced components before committing the plan manifest. A failed write leaves the old durable baseline/current manifest intact and the accepted replacement dirty/in memory, with a retryable error.

Replacement changes a role reference, not an immutable artifact. Clearing removes the baseline role as one transition; invalidate pair-derived comparison/Break-Even displays without deleting objects needed by backups, another role or an in-flight write. Legacy inputs-only capture may remain an explicitly named separate operation if approved; it must not masquerade as capturing original completed results.

## 5. Historical visualization inventory

The following is source-based. “Existing persistence” means actual UI lifecycle support, not merely an enum or codec branch. Selection preferences are small presentation metadata; financial rankings and retained/unavailable details are primary result data.

| Visualization / source | Underlying values and controls | Existing persistence / missing pieces | Minimum snapshot addition and size implication |
|---|---|---|---|
| Standard Summary `ProjectionChartView` / ProjectionChartModel | 13 selectable series: investable assets, net worth, after-tax estate, income, expenses, taxes, SS, growth, withdrawals, non-investable assets, Medicare, actual Roth, projection-period RMD; asset composition; year readout | Model memory-only; chart built from annual results, separate non-investable results, people and actual account registry | Complete annual DTO + non-investable DTO + frozen election/marker context; small metric/year preferences, no full duplicate series required |
| `PortfolioChartView` / ProjectionChartBuilder | Ending investable assets line, exact calendar years; display geometry converts to double | Memory-only ordinary Projection | Same annual data, negligible extra metadata |
| Results Summary cards, DashboardView, annual ResultsView/ProjectionYearView/details | Totals, peaks, net worth/estate, income summaries, tax/cash/RMD/Roth/SS detail | Ordinary current/baseline caches only; IncomeSummaryService recalculates SS | Frozen summary/statistics/income values plus complete annual graph, not formatted strings |
| Baseline Comparison | Seven Summary deltas; other existing comparison/detail definitions where exposed | Compares prepared/rebuilt baseline and current, not original durable result pair | Two artifact IDs plus unchanged comparator/version; no duplicate pair of projections |
| Phase 12B Baseline Details | Cached baseline asset chart, exact input rows, unknown/ambiguous correspondence | Memory session; baseline curve explicitly rebuilt | Original saved annual data enables new truthful heading; existing comparison model reused with both frozen inputs |
| Break-Even `BreakEvenChart`, tables/insights | Cumulative SS, investable assets, net worth, after-tax estate; shared-year deltas, crossings, original years/people; optional survival/events overlay | Pair-derived memory result; controller's context factory builds mortality scenarios from current session settings/table | Reuse saved annual data for unchanged arithmetic; retain frozen completed overlay/context if historical overlay required; do not regenerate mortality scenarios at restore |
| Couple SS Only shared ClaimingAgeGridView | Stage 1 expected SS PV dollars, actual axes, exact optimal ties, Stage 2 retention; complete strategy rankings/elections and assumptions | Restores through CoupleSocialSecuritySnapshot, mapper and frozen context; Phase 11 PDF supported | Pin existing immutable snapshot; optional cell identity, not fake integrated metrics; KB–MB varies with distributions/scenarios |
| Couple Quick Comparison tables | SS-ranked caller order, baseline and candidate metrics/differences, complete elections, counts/failures, frozen prerequisite/report context | **Memory-only results**, though parameters persist and PDF session exists | New lossless result/session family required in 12E if “all completed” includes Quick; explicit approval needed; no heat map invented |
| Couple Exhaustive heat map/rankings/details | All compact candidates including failures, competition ties, 14 metrics/deltas, baseline and retained projections, SS cross-reference; metric/cell/grouping preference | Complete payload plus production v2 interning, historical UI/PDF restored | Pin existing artifact; do not duplicate retained projections in standard wrapper; unavailable detail remains unavailable |
| Couple Weighted heat map/rankings | Weighted expected metrics/probabilities/equivalence/ranks, original mortality/valuation and optional baseline, selected strategy | Existing WeightedSnapshots couple payload/context, historical UI/PDF | Pin existing payload. Does not imply full per-scenario annual projections exist; unavailable detail must not be manufactured |
| Individual deterministic nine-age view/table/selected strategy and its Break-Even | Nine complete deterministic candidates, retained details and report context | **Memory-only results** in current source; Phase 10 parameters are not restoration | New explicit result family/lifecycle needed in 12E, separate from couple Exhaustive kind |
| Individual SS Only nine-strategy value presentation | Mortality-weighted SS strategy values, rankings, assumptions and explanations | **Memory-only UI result**; codec family support alone does not make it restored. Dialog explicitly states persistence not enabled | Explicit completion/restoration integration and full field proof required in 12E |
| Individual Weighted nine-strategy presentation | Weighted integrated values/ranks, original primary-only mortality/valuation/report context | IndividualCompletionPersistence attaches INDIVIDUAL_WEIGHTED historical lifecycle | Pin existing individual weighted payload; preserve independent SS/weighted controls |
| All 8 Monte Carlo identities: standalone fixed/longevity and paired fixed/longevity × couple/single | Fan chart bands/percentiles, deterministic reference and Roth/RMD/election markers, annual funded/living populations, terminal tables, paired A-minus-B differences and selection | Existing MonteCarloSnapshot v1, bounded prefix representation, lifecycle and historical PDFs; fixed/longevity/paired families preserve their original supported trial graph | Pin existing encoded result, request/settings/plans/provenance and display context; no seed-only replay or newly sampled histories |

`MonteCarloFanModel`, `MonteCarloFanChart`, MonteCarloStrategyComparisonPresentation and MonteCarloDifferenceChart consume cached data; paired annual values are not independent percentile subtraction. Keep matched market/inflation/lifetime identities, seed, counts, pair ordering and original aggregation convention. Different scenario contracts cannot be labeled paired by merely linking two saved artifacts.

Chart selected years, metric choices, SS pair/strategy keys, grouping and zoom are optional bounded preferences validated against the saved result. Preserve accepted Phase 11 initial-tab defaults even if older metadata records Ranked Strategies. Hover, focus and transient drafts need not be durable. PDFs reuse original context and timestamps, not current controls; exported PDF bytes/screenshots are not canonical financial storage.

The headless deterministic retirement-age grid calculator and other evaluator services are not additional exposed accepted analysis identities. If a new UI is later added, inventory its payload separately. No other independent JavaFX chart family was found beyond the standard/portfolio, Break-Even, shared claiming grids and Monte Carlo charts above.

## 6. Versioned persistence proposal and alternatives

**Recommendation, not an existing schema:** small optional plan metadata plus separately bounded immutable components under the application repository's UUID namespace. This extends the existing persistence architecture; it does not introduce a second set of financial codecs or an unrelated cache framework.

```json
{
  "planId": "<existing durable identity>",
  "household": "<existing editable financial roots remain authoritative for edits>",
  "baseline": {
    "snapshot": "<legacy-compatible saved financial input shape>",
    "savedAt": "<existing capture time>",
    "description": "<existing description>"
  },
  "projectionState": {
    "schemaVersion": 1,
    "generationId": "<role-manifest generation>",
    "ownerPlanId": "<same plan identity>",
    "current": { "snapshotId": "<B>", "digest": "<hex>" },
    "baseline": { "snapshotId": "<A>", "digest": "<hex>" }
  }
}
```

The strings in this example stand for unchanged real objects, not a proposal to replace those objects with strings. Each complete snapshot has an explicit component manifest for input/result/summary/context and pinned analyzer artifacts, including supported type/version, lengths, digest and source identity. References use validated IDs, never arbitrary filesystem paths. A schema may permit a null role, but must distinguish that from a missing/corrupt referenced result. The role manifest does not contain simulation arrays or recursive copies of earlier baselines.

Keep legacy baseline input fields for compatibility and financial comparisons, but validate that they agree with the complete baseline artifact's inputs. Reject inconsistent pairs; do not silently prefer one and conceal corruption. Root editable inputs may legitimately be dirty/outdated relative to the current accepted artifact; record and display that status. Before swap they must be reconciled explicitly.

| Alternative | Assessment |
|---|---|
| All results inline in plan JSON | Simplest single-file atomic replacement and portability, but large histories repeatedly inflate ordinary/config saves, plan deep copies, memory and strict MC frozen-plan contexts. Reject as the initial design for all analyzer results. |
| Optional inline ordinary projection only | Plausible bounded alternative for 12D alone; still needs explicit metadata exclusion, size proof and forward handling. Does not solve pinned large analyses. Avoid designing 12D in a way that forces duplication in 12E. |
| Immutable components plus one small role manifest | Recommended for the complete objective. Reuses existing financial payloads and atomic writer; requires approved manifest, recovery, ownership and reference retention protocol. |
| Rebuild from inputs/seed or save screenshots | Does not preserve original results or editable/inspectable financial values; unacceptable substitute. |

### Commit and recovery protocol

1. Capture the immutable accepted generation and destination identity/path/binding before background I/O. Validate complete graph, types, reference limits and source result coherence.
2. Write new immutable components to staged/unique locations with existing atomic write/verification machinery. Validate exact digests and financial fidelity; never overwrite the currently referenced components.
3. Under the shared plan/configuration save lock, recheck durable file hash, owner, expected generation and accepted configuration merge state. Confirm all required referenced components are durable and readable.
4. Atomically replace the small plan manifest/plan JSON as the sole durable role-selection commit point. The prior plan and backup continue referencing prior valid components until this step succeeds.
5. Publish Saved only after verification. A failed precommit leaves unreferenced staged objects, not a partially accepted role state. Failure after commit is detected by reading the authoritative manifest; retry must not create an alternative ambiguous commit.

Existing AtomicFileWriter guarantees a single-file replacement; it is not a multi-file transaction or power-loss proof for this proposed protocol. Phase 12D must specify filesystem durability assumptions, directory/object publication ordering, binding-file interaction, crash recovery and fault-injection tests before implementing the protocol. No global file-size increase is recommended.

Retain every object reachable from current/baseline roles, last-success analyzer slots, durable backup manifests and in-flight writes. Do not immediately delete replaced results. Garbage collection/retention limits require approval and separate tests; initially conservative retention is safer than deleting backup dependencies. Bound storage growth and refuse a new acceptance/save honestly when the approved budget cannot fit it. No unlimited analysis-history browser is implied.

### Fingerprints and strict compatibility

AnalysisFingerprintService and MonteCarloFingerprintService explicitly allowlist root plan properties, exclude planId/baseline/analyzerConfigurations from financial plan groups, and preserve scale-sensitive financial values. Paired Monte Carlo separately fingerprints its reconstructed baseline financial inputs. Adding `projectionState` without updating those exact-property boundaries will fail or pollute compatibility. MonteCarloPlanDocument currently serializes a plan then removes only analyzerConfigurations; its bounded frozen plan text is at most 256,000 characters. RetirementPlanScenarioCopyService JSON-copies the complete current model.

Approval is therefore required for **additive metadata admission and exclusion** in these boundaries and repository/configuration durable-state handling. This must leave old financial fingerprint bytes, MC V1/V2 compatibility, saved baseline financial input semantics and payload digests unchanged. Strip only recognized snapshot metadata from financial/scenario documents; continue rejecting arbitrary unknown financial properties. Do not globally normalize decimals. New complete-projection input/result digests are separate versioned contracts, not replacements for Phase 9 hashes. No financial fingerprint semantic change is presently justified.

Unsupported future manifest/component versions must be preserved and protected from destructive save/swap. Prefer explicit inspection/recovery restrictions to silently dropping references. Missing metadata in old plans remains valid. Unsupported financial result meaning may allow viewing frozen labels/inputs where safe, but must block editable promotion if safe reconstruction or compatibility cannot be established.

## 7. Legacy compatibility and migration

- Legacy baseline with inputs only: continue Phase 12B's honest parameter view and cache-only **rebuilt** chart. Never relabel it original. Do not fill original result fields by automatically projecting it.
- Complete current context absent: Not recorded. A normal future successful calculation may create a new original artifact for that new run; it cannot recover old annual history.
- Swap requires two complete safe roles. Legacy input-only baseline is ineligible for exact swap. An explicitly requested rebuild can later create a **newly calculated from legacy inputs** artifact with a new run/time/provenance and user confirmation; this is not a migration that recovers old results.
- Existing Phase 9 weighted/SS/Exhaustive and MC snapshot bytes remain readable without rewrite. Preserve SS legacy household-name disclosure and original report context. Pin only results whose source association can be demonstrated; old hashes cannot be reversed into complete input contexts.
- Allow missing optional metadata in older JSON; preserve unsupported bounded future metadata rather than overwriting it. A model/schema extension and forward-safety policy are necessary implementation approvals even if no top-level historical plan version has previously existed.
- An older application build is not guaranteed to read the new optional root property. Document downgrade restrictions; do not claim backward reads imply forward compatibility with old executables.

## 8. Swap state transition and reversibility

Let plan container P retain its durable UUID/file and plan-owned analyzerConfigurations. Let A and B be complete immutable artifacts with their original result identities and all pinned analysis/context references.

```text
Before: P.current = B; P.baseline = A; editableInputs = detached(B.inputs)
After:  P.current = A; P.baseline = B; editableInputs = detached(A.inputs)
Again:  P.current = B; P.baseline = A; editableInputs = detached(B.inputs)
```

No projection, SS optimization, Monte Carlo simulation, mortality sampling or new financial analysis runs on role exchange. Inputs are the original opening/configured values, not ending account balances from a projection year. Preserve all persons/membership, account types/owners, income/elections/pensions, expenses, economic/federal/state settings, withdrawal/Roth/RMD/inherited/participant inputs, horizon/death and non-investable inputs. Newly editable objects are detached copies; immutable artifacts never become editable domain objects.

Second swap without edits/recalculations restores role IDs, inputs, annual results, attached analysis identities and original timestamps. Dirty flag/revision/operation time need not revert byte-for-byte: two intentional operations can leave the document dirty under the existing conservative modified contract. Artifact creation times must not change. Different horizons remain different, and comparison differences remain Current minus Baseline. Do not merely negate old break-even “first/sustained crossing” labels; those are directional and must use unchanged existing observational rules for the exchanged pair.

### Plan-owned versus role-owned analyzer state

Keep all 15 last-success parameter configurations on P; swapping financial roles is not a newly successful analyzer run. Do not overwrite those configurations from an artifact or make its metadata trigger automatic parameter persistence. Frozen settings associated with a historical analysis belong to that analysis's report context. The current compatibility assessment may legitimately become Outdated after promotion.

Existing last-success analyzer slots remain independently available. Each role pins its own associated completed analyses, so neither disappears when a slot is replaced. A result calculated from different inputs or paired against a different baseline must remain labeled with its actual source context. After swapping a baseline, an older paired comparison still describes its original A/B scenarios; it is not automatically the new current-versus-baseline comparison. Quick admission continues requiring an actual compatible SS prerequisite, never a parameter entry or synthetic restored completion.

New attachment capture should record its exact prepared financial input digest and source revision/run receipt alongside family-specific fingerprints. Mode-specific hashes intentionally omit some irrelevant fields, so equality alone is not proof that every original plan input equals a projection artifact. Unprovable legacy associations stay in the independent historical slot, explicitly unassociated; never silently attach the newest available result to a projection.

## 9. Atomic in-memory transaction and concurrency

Prepare everything before changing active state: validated artifact references, restored editable input graph, detached annual result graphs, summaries, chart data, parameter comparison session and pair-derived data. Validate future schemas, all account/reference mappings and plan/household compatibility. If preparation fails, publish nothing.

Use a controller-owned transition command with expected plan UUID/path/binding epoch, role generation, source revision and accepted run IDs. Freeze new job admission for the brief transition. Either require existing jobs to finish or explicitly cancel and await their terminal eligibility boundary; merely pressing Cancel is not proof a worker cannot finish. Invalidate all old completion/load/export/navigation generations. Never let a late callback publish results or parameters into the promoted financial role.

At one FX/controller commit boundary replace active financial graph, current/baseline artifact roles, all cache/context/summary references and source generation as one staged application state. Preserve file/UUID and plan-owned configuration entries. Notify dependent views after the coherent state exists, once. Existing setters plus `refreshAllViews()` are not a transaction and would clear/recalculate the restored results. Prefer a staged state object/reference assignment, not a sequence of mutations that needs fragile rollback.

Preparation/ownership conflicts cancel without state change. If UI attachment fails after the commit, render the already committed immutable state again or restore the prior staged application state under the same transition barrier, without incrementally reverting individual financial fields. Define this boundary and fault tests before implementation; arbitrary UI listener side effects must not determine financial transaction success.

Drain/cooperate with pending accepted configuration writes and snapshot saves before transition where necessary, without canceling already accepted durable work. Those writes retain captured plan binding and immutable settings, not current mutable UI inputs. A swap is an in-memory dirty operation; it must not silently write financial edits to disk. Distinguish successful in-memory exchange from successful later Save. Unexpected external file changes make Save fail conservatively while the coherent swapped state remains dirty/retryable.

## 10. Dirty edits, enablement and confirmation

Place **Swap Baseline and Current** beside Break-Even Analysis and View Baseline Details in the existing Baseline Comparison action group. Enable only with two complete, validated, promotable snapshots, correct ownership, supported editable input schema, coherent current-result inputs and no conflicting admitted work. Unlike the Phase 12B details action, swap may be useful even when financial outcomes are equal. Do not enable it from edited controls or input-only legacy baselines.

Recommended confirmation explains exact role exchange, editable-plan replacement, retained original results, no recalculation, dirty state and later Save behavior. It must state that original baseline inputs become the active editable inputs and current accepted inputs/results become baseline.

There are two distinct unsaved cases:

1. Coherent current inputs/results merely not yet saved: allow the existing Save/Discard/Cancel workflow before swap. Save serializes those edits but does not turn stale result data into an accepted matching projection.
2. Drafts or live financial edits differ from the accepted current artifact: block swap until explicitly resolved. Offer cancel, explicit discard back to the accepted current inputs, or a separate normal calculation to accept a new current artifact. No calculation occurs *because of swapping*. Saving unmatched edits alone is insufficient.

Existing `confirmPlanDeparture()` checks controller dirty state plus Household/Assumptions/Roth drafts. Its Discard branch cancels those drafts but does not reconstruct all already-mutated live plan data. Do not reuse its return value as evidence that all live inputs now equal the accepted result. A swap-specific reconciliation step must verify that equality and must never silently discard account/expense/other direct model edits. Preserve the original state until confirmation and staging have completed. A separate saved copy/Save As may preserve unmatched financial edits before the user chooses to return to accepted inputs.

## 11. Save, Save As, reload and navigation

- **Save:** merge accepted plan-owned configurations under the existing cooperative lock, commit only when all required artifacts are durable, preserve exact financial tokens/scales and external-change detection. Clear modified only after successful plan-manifest verification.
- **Save As:** allocate independent destination plan identity, stage/verify all mandatory role components with authorized destination ownership/origin provenance, then commit the new plan manifest. Failure before commit leaves source identity/path/state intact. Existing best-effort asynchronous analyzer-slot cloning is insufficient for mandatory role references; distinguish optional independent slot copies from required pinned components. Binding-file/manifest crash handling needs an explicit tested protocol.
- **Reload:** asynchronously decode/validate components, then attach one accepted role generation. Do not route through the ordinary calculate-on-open refresh. Missing/corrupt components leave a clearly unavailable original result, with backup recovery; do not silently rebuild and present it as original.
- **Plan switch/dialog close:** cancel relevance of pending attachment callbacks by owner/binding/generation, not by file name. Accepted background saves may finish for their captured owner. Close or detach stale details/export sessions safely.
- **UI:** a load-only accepted-results path must update editable input tabs under listener suppression, Summary, dashboard, portfolio/annual details, charts, comparisons and Break-Even readiness from the same installed state. Do not call invalidateProjection as part of rendering a swapped result. Preserve Phase 11 result-tab initialization/manual-navigation guards.
- **Portable files:** external components mean copying a JSON file alone does not necessarily transport results. Choose repository-managed export/bundle or an explicitly documented companion-data workflow before release. Save As must not merely point into the source plan's replaceable slot namespace.

## 12. Phase 12B and Break-Even integration

When a baseline has an original complete artifact, use **Original Baseline Projection — Saved Results** and its saved annual data. Both parameter sides come from their own frozen input documents. Phase 12B's unknown/missing handling, exact numeric differences, amber styling and conservative collection matching remain useful. Rebuilt legacy cache curves retain the old rebuilt heading and unavailable-cache explanation; they cannot enable an exact swap.

Existing BreakEvenAnalyzer is observational arithmetic over supplied results: intersect calendar years; zero crossing tolerance; cumulative SS begins at the first shared year; preserve first/sustained crossover rules and warnings about differing horizons. Reusing that unchanged service on two saved annual graphs is not financial resimulation. If policy requires *zero new pair-analysis arithmetic* at swap, stage/version/cache the directed pair results for both orientations ahead of time; do not change crossing semantics by negating old labels. This distinction requires approval.

**Recommended strict default for the stated no-analysis swap requirement:** prepare and save both directed comparison/Break-Even contexts when the complete baseline/current pair is established, outside the Swap command. Reference them through a bounded pair-context artifact keyed by both result IDs and definitions. Swap only selects the existing orientation. Missing required pair context disables swap until an explicitly approved separate preparation operation, rather than calculating during the exchange. Allowing new observational pair arithmetic inside swap is an alternative requiring explicit approval, not assumed permission.

BreakEvenContextFactory currently constructs mortality scenarios using session longevity settings and the current table. Original historical survival overlays therefore require a saved completed context/overlay with its settings/table provenance. If absent, show it unavailable; do not infer original survival probabilities from current settings. Single-person deterministic break-even remains available, while its deferred survival overlay must not be invented by this phase.

Household shape is validated independently for each artifact, not assumed equal merely because it shares a plan UUID. Promotion replaces the complete membership graph, never merges a former spouse into a single-person snapshot. Test couple→single and single→couple UI reconstruction, existing deterministic comparison support and unavailable survival-overlay conventions. If an existing presentation cannot safely compare those valid roles, block that combination with an explanation until separately resolved; do not alter financial rules to make it fit.

## 13. Storage and performance evidence

**No new full financial universe or Monte Carlo simulation was executed for capacity in this audit.** The following measured values are prior reproducible report evidence, not new measurements or a guarantee for every user plan:

| Evidence | Raw bytes | Gzip bytes | Notes |
|---|---:|---:|---|
| Realistic 5,184-candidate Exhaustive, old logical graph | 20,479,403 | 837,669 | Baseline + 20 details, 840 annual rows, 40-year/6-account maximum |
| Same fixture, production v2 | 2,903,992 | 133,155 | Exact results; 162 metric/difference values, one account table and two annual graph values in this fixture |
| Couple fixed standalone MC, 5,000 trials | 5,284,450 | 1,005,017 | Independent production scenarios, prior Phase 2B |
| Couple paired fixed MC, 5,000 trials | 10,359,990 | 3,810,793 | Original paired histories retained |
| Couple paired fixed MC, 10,000 trials | 20,695,338 | 7,613,054 | 1,732,788 tokens, one million annual observations; bounded prefix format |

Eight prior 5,000-trial MC fixtures span approximately 3.28–10.36 MB raw and 0.85–3.81 MB gzip each. Two copies of the measured 10,000-trial paired raw payload total 41,390,676 bytes, already beyond a single 32 MiB document before plan/ordinary results. Therefore do not inline both roles' analysis universes into one bounded snapshot. Sharing identical immutable references can avoid duplicating the same run, but no assumption of high interning across independently calculated trials is justified.

Ordinary projection sizing has **not** been measured as a complete proposed payload. Its storage scales with years × accounts plus attribution/tax/state detail and decimal length; simple chart-series metadata scales with roughly 13 × years and a few events. Baseline plus one current result should be much smaller than thousands of retained trial histories in typical cases, but there is no approved byte estimate or maximum-plan guarantee. Prior Exhaustive compression ratios cannot be blindly applied to different ordinary plans. Phase 12D needs real long-horizon, many-account, inherited/state-tax and long-decimal measurements.

Retain existing per-artifact protections: 32 MiB decoded, 16 MiB compressed, 2,000,000 physical tokens, 4,096 numeric characters, depth 80, strings 256,000, and the existing 100,000 **array-container** scanner bound (not a universal current “items per array” limit). Exhaustive v2 additionally limits tables/references/expanded logical work; MC bounds trials/years/annual observations and prefix expansion. Reuse these guards without changing old wire formats. New manifests need their own small reference/count limits, total generation byte/expansion quota and decoded working-set budget; per-component ceilings alone are insufficient.

Load only the active pair/selected analyzer asynchronously rather than decoding all attachments on the FX thread. Bound workers and queues; stream hashing/encoding where available; validate references and logical expansion before allocation. Domain reconstruction needs detached objects and can require substantially more heap than compressed bytes. Prior Exhaustive sampled heap figures include calculation/experiments and are not an isolated new snapshot memory budget. No new latency, heap or disk-capacity measurement is claimed here. Stage gates must measure capture/encode/decode/reconstruction/UI attachment separately, with only structural/privacy-safe diagnostics.

## 14. Regression risk matrix

| Risk | Required protection / acceptance evidence |
|---|---|
| Inputs/results from different runs | Atomic accepted bundle with input digest, run ID and revision/binding tests; stale baseline capture rejected |
| Hidden financial work on restore | Throwing ProjectionEngine, SS calculator/optimizer, MC and mortality factories; frozen IncomeSummary/non-investable/context data |
| Mutable accounts corrupt historical results | Detached editable and result graphs, explicit local registry bindings; equal-name/equal-value account and orphan-reference tests |
| Snapshot defaults/versions fabricate history | Legacy Not recorded/provenance labels; unsupported schema blocks promotion; no automatic rebuilt-to-original migration |
| Swap partial state or late callbacks | Staged transition, job admission/terminal guards, epoch invalidation and rollback fault injection; second-swap equality |
| Dirty/draft financial edits lost | All draft/direct-edit paths audited; explicit reconciliation; Save alone does not assert result matching |
| Baseline/comparison/Break-Even financial drift | Existing seven Summary deltas and shared-year/crossover goldens unchanged; directed pair tests and horizon cases |
| Analyzer results lost or misassociated | Immutable pinned attachments, independent 15 slots/configurations, validated source relationships, no fake Quick prerequisite |
| MC pairing/seeds or numeric compatibility change | Existing MC prefix/paired identities and seeded regressions; V1/V2 and scale-sensitive financial digest proofs |
| Save/config auto-write/Save As conflict | Shared locks, captured binding, durable generation validation, dirty isolation, clone/restart and external-change fault tests |
| Corrupt references or partial disk commit | Immutable verified components, manifest commit point, backup reachability, path/binding and crash-point tests |
| Summary/chart/PDF reads live fields | Frozen render DTOs; original/restored/edited-current export comparison; no live-account name reconciliation |
| Memory/file growth | Per-object plus total referenced-generation budgets; lazy bounded loads; orphan/backup retention policy; no limit increases by convenience |
| Couple/single membership drift | Full role/input validation, optional spouse fidelity, both household shapes, no spouse requirement for single-person artifacts |

## 15. Recommended stages and exact implementation boundaries

**12D-0 — fidelity/transaction design gate:** approve schema and ownership/retention protocol; prove complete ordinary result/context capture including income/non-investable/markers and account references, using independent field comparisons and exact scales. Audit default/missing provenance. Do not assume the existing retained codec covers every required normal-projection dependency.

**12D — complete standard projection persistence:** new immutable wrapper/manifest DTOs and bounded codec; accepted-success bundle capture; exact baseline capture; component write/verify/recovery; ordinary Save/Save As/Open integration; load-only rendering and Phase 12B original-results mode. No swap yet. Require original versus restored charts/Summary/details/PDF data equality, explicit legacy fallback, current input staleness and no engine invocation.

**12E — historical analyzer attachment coverage:** reuse/pin existing SS/Exhaustive/weighted/8 MC families without changing their wire contracts. Explicitly authorize whether Quick, individual deterministic and individual SS memory-only result modes are to gain persistence; all three need implementation if the objective is every completed existing analyzer. Add frozen pair/Break-Even overlay context as separately approved. Validate attachment provenance, independent configurations and bounded capacity with real production fixtures.

**12E-acceptance — migration/limits/security gate:** exercise old JSON, frozen legacy snapshots, unknown future schema, corruption/expansion, manifest recovery and cross-plan/Save As ownership. Measure complete generation costs; approve total storage/retention quotas and portable-copy policy. No financial golden regeneration.

**12F — atomic swap:** implement staged command and edit-resolution/confirmation UI, reuse load-only render path, invalidate old job generations, preserve all settings/results, mark modified once, exact two-swap round trip and Save/reload/Save As. No calculations on the role-exchange path; pair arithmetic policy decided below.

**Final integration/native gate:** complete Maven suite, golden/seeded regressions, original-versus-restored visuals/PDFs, race/crash tests and user-performed Windows acceptance. Native success for Phase 11 does not certify this unimplemented work; Phase 12B's report still records its own native acceptance as pending.

Anticipated existing change points after approval: ApplicationController and MainWindow (accepted-state publication, render-only path and transition); ProjectionBaseline/ProjectionBaselineFactory/BaselineProjectionService (optional original reference and legacy-only rebuild); RetirementPlan and JsonRetirementPlanRepository/PlanConfigurationTransaction (bounded metadata, coordinator/locking); AnalysisResultStore/repository integration (immutable pins without replacing existing slots); precise fingerprint/MonteCarloPlanDocument/scenario-copy property boundaries; ResultsSummaryView, ProjectionChartModel adapters, Phase 12B session/view and frozen Summary adapters. Add dedicated app/projection immutable DTO/session/coordinator types and persistence codec/repository extensions following existing packages. Keep ProjectionEngine and financial calculators unchanged.

Tests to extend include RetainedProjectionCodecTest, RetirementPlanSnapshotTest, ProjectionBaselineTest, BaselineDetailsTest/ViewTest, BreakEvenControllerTest/AnalyzerTest/ContextFactoryTest, ProjectionChartModelTest/ReconciliationTest, TransactionalPlanPersistenceTest, PlanConfigurationTransactionTest, AnalysisSnapshotCodecTest/RepositoryTest, frozen legacy weighted and Exhaustive/MC codec tests, parameter-coordinator/all-15 configuration tests, seeded/golden and existing PDF suites. New transition tests need all prepare/commit failure points, canceled/obsolete jobs, unchanged versus stale current inputs, two swaps, independent Save As and loading races.

## 16. Decisions requiring approval before implementation

1. **Schema/storage:** optional role manifest plus immutable components versus inline ordinary results; artifact location/portability and explicit multi-file commit/recovery protocol. New schema/versioning is necessary.
2. **Fingerprint isolation:** narrowly allow/exclude the new nonfinancial metadata in existing strict boundaries, with old digest bytes unchanged; no financial fingerprint semantic change.
3. **Baseline capture semantics:** require the exact accepted bundle, replacing live-input-only capture for complete baselines. Whether to retain a separately labeled inputs-only capture action.
4. **Legacy swap eligibility:** no exact swap for input-only baselines; any explicit rebuild creates a new dated calculation, not recovered original history.
5. **Edits policy:** block swap when drafts/live inputs do not match accepted current inputs until explicitly discarded, preserved separately or normally recalculated. No silent discard and no recalculation by swap.
6. **Analyzer coverage/retention:** pin immutable per-role analyses; keep all 15 plan-owned settings unchanged; explicitly authorize persistence of the three currently memory-only result modes. Define bounded attachment association and backup-aware retention, not unlimited history.
7. **Break-Even boundary:** permit unchanged observational pair arithmetic over saved results, or require precomputed directed pair results. Freeze/restore optional survival context rather than regenerate mortality scenarios.
8. **Ownership and concurrency:** stage one accepted application state, quiesce conflicting jobs/writes, clone mandatory components before new manifest acceptance and preserve origin. Approve the required narrow state-publication/render-path refactor.
9. **Capacity/forward policy:** choose total generation/disk/decoded-memory quotas from measured fixtures, conservative unsupported-version restrictions and portable-copy workflow before release.

No implementation, financial change, storage-limit increase or migration is approved by this report. Stop for design review; do not begin Phase 12D.

## 17. Verification actually executed

Existing tests only, IntelliJ bundled Maven, required project-local repository and `-DargLine=-Xmx4g`:

- `target/phase12c-audit-tests.log`: **176 total, 174 passed, 2 skipped, zero failures/errors**, BUILD SUCCESS, 31.627 s. Executed AnalysisParameterCoordinatorTest; BaselineDetailsTest/ViewTest; BreakEvenContextFactoryTest/AnalyzerTest/ControllerTest; MonteCarloSnapshotCodecTest; BaselineProjectionServiceTest/ProjectionBaselineTest/ProjectionComparisonServiceTest/RetirementPlanSnapshotTest; ExhaustiveSnapshotTest/ExhaustiveSnapshotInterningTest; AnalysisSnapshotRepositoryTest/CodecTest; TransactionalPlanPersistenceTest; ProjectionChartModelTest/ReconciliationTest; LegacyWeightedSnapshotCompatibilityTest. Two MC codec capacity/replay tests remain existing opt-ins, not newly excluded tests.
- `target/phase12c-fidelity-tests.log`: **27 passed, zero failures/errors/skips**, 13.303 s: actual `domain.projection.RetainedProjectionCodecTest`. Initial broad selectors included some guessed class names without matches; those are not counted as executed. Source discovery identified the actual retained codec test, run separately here.
- `target/phase12c-compatibility-tests.log`: **111 passed, zero failures/errors/skips**, 12.903 s: AnalysisFingerprintTest, MonteCarloRestoredFingerprintInvestigationTest, PlanConfigurationTransactionTest and AnalysisParameterConfigurationsTest.

Combined current audit verification: **314 test invocations, 312 passed, 2 skipped, zero failures/errors**. No tests/assertions/goldens changed. The complete suite was not repeated for this report-only audit; Phase 12B's documented final full result (3,456 total, 3,412 passed, 44 skipped, zero failures/errors) is prior evidence, not a new Phase 12C run. No new capacity simulation, swap, schema or native acceptance is claimed.

`git diff --check` completed with exit 0. Exact inventory is recorded below. Only this report is added by Phase 12C; test-generated files/logs remain ignored under target.

## 18. Exact Git working-tree inventory

Branch: main. HEAD: e620bec4a0011ab12d15fce670bff5011093d9da. Staging index empty (0 staged paths). Before audit: 16 modified tracked paths, 25 untracked files. After audit: 16 modified tracked paths, 26 untracked files. All pre-existing work is preserved; the only new audit file is this report. No staging, commit, push, reset, clean, discard or production/test/CSS/persistence edit was performed.

Modified tracked files (all pre-existing):

- src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerPdfExporter.java
- src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerReport.java
- src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java
- src/main/java/com/daviddunn/retirementplanner/ui/charts/ProjectionChartView.java
- src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java
- src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerNavigationView.java
- src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ClaimingStrategyHeatMapView.java
- src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IntegratedAnalysisResultTabs.java
- src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/LongevityWeightedIntegratedView.java
- src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java
- src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedResultPersistence.java
- src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java
- src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/CoupleSocialSecurityPersistenceTest.java
- src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java
- src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedRestorationTest.java
- src/test/java/com/daviddunn/retirementplanner/ui/views/SinglePersonHouseholdUiTest.java

Untracked files (all pre-existing except this report):

- PHASE-11A-SOCIAL-SECURITY-UI-PDF-AUDIT.md
- PHASE-11B-SHARED-GRID-AND-NAVIGATION.md
- PHASE-11C-SOCIAL-SECURITY-AND-QUICK-PDF-EXPORT.md
- PHASE-11D-FINAL-INTEGRATION-AND-REGRESSION-AUDIT.md
- PHASE-12A-BASELINE-DETAILS-ARCHITECTURE-AUDIT.md
- PHASE-12B-BASELINE-DETAILS-IMPLEMENTATION.md
- PHASE-12C-PROJECTION-SNAPSHOT-AND-SWAP-ARCHITECTURE-AUDIT.md
- src/main/java/com/daviddunn/retirementplanner/app/baseline/BaselineDetailsSession.java
- src/main/java/com/daviddunn/retirementplanner/app/baseline/BaselineParameterComparison.java
- src/main/java/com/daviddunn/retirementplanner/app/baseline/ProjectionInputs.java
- src/main/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsDialog.java
- src/main/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsView.java
- src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisPdfData.java
- src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ClaimingAgeGridView.java
- src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/QuickComparisonReportAdapter.java
- src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/QuickReportSession.java
- src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationGuard.java
- src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityClaimingGridAdapter.java
- src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityOnlyReportAdapter.java
- src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityReportSession.java
- src/main/resources/css/baseline-details.css
- src/test/java/com/daviddunn/retirementplanner/app/baseline/BaselineDetailsTest.java
- src/test/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsViewTest.java
- src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationTest.java
- src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SharedClaimingAgeGridTest.java
- src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAndQuickPdfTest.java
