# Phase 21A — Monte Carlo Execution and Expense Volatility Audit

Audit date: November 5, 2026. Branch: `main`. Starting HEAD: `81dd97a12a875dad864057a2662a7ac6bcdfefbd`. Starting tracked/untracked working-tree status and index were clean.

## 1. Executive summary

**The comparison rejection is fully explained by an intentional public-execution gate.** `ExpenseVolatilityAdmission.requireSupported` rejects any expense whose effective annual volatility is positive. Both standalone modes and both comparison candidates are covered, for single-person and couple plans. The guard is still active in current production source.

**A historical/outdated result does not itself block standalone Run.** The handler is installed, Run is disabled only while its session is busy, and the session permits replacement of a restored result. A synthetic JavaFX probe successfully executed a new zero-volatility analysis while retaining a stale result before the run. The same probe with positive volatility entered RUNNING, then FAILED at the admission guard, retaining the old result and publishing an explicit error in `#mc-status`.

**The native “nothing happens” presentation has not been reproduced.** The exact native mode, current input state, post-click session state and exception are unavailable. The proven positive-volatility guard explains why a new simulation cannot execute with those settings; it does not prove why the user did not see the existing error. Do not claim a missing handler or historical-result execution lock. If the native plan has zero effective volatility, this guard cannot explain its failure. Phase 21B1 should establish that remaining execution/feedback mechanism before correcting it.

**The stochastic expense engine is already implemented internally.** Phase 14B1 provides a frozen mean-preserving annual lognormal sampling protocol. Phase 14B2 provides immutable expense paths, full financial integration, and internal standalone/comparison execution. Phase 14B3A added configuration, persistence and the explicit temporary public guard. The unfinished work is supported enabled-result provenance, compatibility/history/report adapters and public admission, rather than a new distribution or financial engine. Removing the guard alone would advertise enabled runs through legacy result contracts that do not identify the expense model.

No production or test source, financial logic, plan schema, fingerprint, UI handler or PDF was changed in this audit. Only this report is a tracked-file deliverable. Existing focused tests: **228 executed, 228 passed, 0 failures, 0 errors, 0 skipped**, in two non-overlapping selections. No full financial suite was run.

## 2. Review scope and current architecture

Reviewed relevant architecture, lifecycle and compatibility sections of the Phase 12D/E/F/G projection snapshot, immutable analyzer association, swap and freshness reports; Phase 14 architecture and B1/B2/B3/B3A reports; Phase 15B/C navigation reports; Phase 19B3/B4 integration/reporting reports; and the Phase 20 Apply/Cancel report. Monte Carlo comparison, mortality, single-person, persistence lifecycle and numeric-compatibility reports were checked against current source.

Older statements are not treated as current implementation. For example, early Phase 14 reports describe Expense before it acquired a persisted volatility field; early single-person reports describe restrictions that have since been removed. Current source supports single-person fixed and longevity runs subject to the actual validation below. The accepted Phase 19 Social Security reduction flows through frozen plan inputs and the common engine; it is separate from expense volatility.

Primary source groups inspected:

- `ui/MainWindow`; `ui/montecarlo/MonteCarloAnalysisDialog`, `MonteCarloAnalysisView`, `MonteCarloSession`, `MonteCarloRunService`.
- Comparison Dialog/View/Session/RunService and `MonteCarloParameterControls`, `MonteCarloResultLifecycle`, snapshot presentation and PDF adapters.
- `app/montecarlo/MonteCarloAnalyzer`, `MonteCarloStrategyComparisonAnalyzer`, request/candidate/world types, settings, all random generators, `InternalExpenseRun`, `InternalExpenseResults`, `ExpenseVolatilityAdmission` and fingerprint/snapshot types.
- `domain/financial/Expense`, `ExpenseDeserializer`; `ui/dialogs/ExpenseDialog`; `domain/projection/ProjectionEngine`, `ProjectionExpensePath`, `ProjectionEvaluationContext`, `ProjectionReadiness`.
- Controller association admission, `CompleteProjectionSnapshot`, `AnalysisResultFreshness`, `AnalysisVersions`, `AnalysisSnapshotCodec`, and historical analyzer report dispatch.

The Monte Carlo analyzers are owned modal Analysis-menu dialogs, not View-menu workspaces. Phase 20 workspace Apply/Cancel callbacks do not dispatch their Run actions or alter their cancellation lifecycle.

## 3. Standalone Run execution trace and diagnosis

### Production execution sequence

1. `MainWindow.createMenuBar` wires `monte-carlo-analysis-menu` to the existing introduced-dialog path. The menu is disabled if there is no plan. The introduction gate verifies plan/revision and plan-operation state before opening.
2. `MonteCarloAnalysisDialog` constructs one view, wraps it in a fit-to-width ScrollPane, and closes the view when hidden. The production view owns a single-thread daemon executor named `monte-carlo-analysis`.
3. `MonteCarloAnalysisView` installs `run.setOnAction(event -> start())` at line 275. `refresh()` sets Run disabled from `session.busy()` at line 427. Freshness is not part of that predicate.
4. `start()` at line 379 returns early only when busy. It parses settings, checks `ProjectionReadiness`, freezes the current plan, and constructs fixed or longevity inputs. Fixed mode optionally copies the valid controller reference projection. It does not select historical inputs as the new request.
5. `startPersisted()` binds result storage, captures parameter/association admission, retains the old displayed result, and calls `session.start` with worker, accepted and rollback callbacks.
6. `MonteCarloSession.start` rejects busy/CLOSED sessions, transitions to RUNNING, calls its UI change listener, and submits work. There is no dependence on `stale`, the old result, a matching fingerprint, or a current historical association.
7. The worker invokes `MonteCarloRunService.run` or `runMortality`. Both check `ExpenseVolatilityAdmission` **before** reference projection/financial simulation. A positive setting throws `IllegalArgumentException` immediately.
8. Worker failures return through the dispatcher to `session.finish`; it enters FAILED, invokes parameter rollback, and retains its previous result. `refresh()` puts `Analysis failed. ...` into `#mc-status` and logs the failure. The progress bar is removed once no longer busy. The preflight `#mc-validation` label is a different channel and can remain empty for a worker failure.
9. Successful execution captures an immutable `MonteCarloSnapshot` and accepted completion off FX. The eligible session callback accepts it into the existing result store and accepts parameter settings. Association is made only when the original controller admission is still valid. Save retry does not execute another simulation.
10. Cancel requests cooperative cancellation; source/input edits invalidate the session revision and cancel running work. Close increments generation, requests cancellation, detaches listeners and shuts down the owned executor. Stale/closed completions cannot publish. An accepted storage write is governed by the existing store lifecycle independently of dialog closure.

The controller supplies the plan, current cached projection, result store and association/parameter services. It is not the Monte Carlo financial worker dispatcher. These paths do not use the Social Security exhaustive-analysis admission controller as a global Monte Carlo queue.

### Deterministic synthetic evidence

An ignored diagnostic harness at `target/phase21a/MonteCarloAuditProbe.java` uses the existing synthetic UI fixture, actual view, session and `MonteCarloRunService`, a queued executor, and one real three-year simulation. It changes no source/test file and accesses no user plan. It restores a synthetic previous run as stale before clicking the real button. Results in `target/phase21a/run-probe.log`:

| Probe | Run disabled before click | After click | After worker | Old result |
|---|---|---|---|---|
| All expense volatility zero, stale result present | false | RUNNING; one job queued | COMPLETED; a new result published | Replaced only on successful completion |
| One recurring expense at 25%, stale result present | false | RUNNING; one job queued | FAILED | Same old result retained |

Positive-case first failing methods:

```text
ExpenseVolatilityAdmission.requireSupported (line 13)
MonteCarloRunService.run (line 38)
MonteCarloAnalysisView.start / startPersisted worker
MonteCarloSession worker dispatch
```

The error is:

> Stochastic expense execution is not yet available. Set annual expense volatility to 0% for all expenses before running Monte Carlo analysis.

The synthetic `#mc-status` contained that error after the worker completed. `#mc-validation` was empty, consistently with worker failure presentation. This probe establishes the admission mechanism and replacement behavior, not the precise native display/scroll/focus state or a durable historical-file reproduction. Existing restoration and association tests cover the durable paths separately.

### Remaining native feedback question and bounded B1 plan

No missing handler, disabled-by-history predicate, or history-only early return was found. Existing view tests also exercise real background responsiveness, validation, failures and cancellation. The audit therefore does not authorize a speculative handler replacement or a reset of historical results.

For the reported native symptom, establish whether the button is disabled, the session ever enters RUNNING, and whether `#mc-validation` or `#mc-status` changes after the click. Record mode, a boolean “any expense volatility positive”, stage and exception class only; no plan contents are needed. Check error visibility in the actual dialog at the user's window/scroll position. If necessary, B1 can add bounded privacy-safe stage diagnostics and a stable failure reason after approval. It should provide prominent preflight/worker failure feedback, preserve the old result, and distinguish that result's freshness from the failed replacement. A null exception message must have a useful generic fallback if this case is demonstrated; current preflight handlers use `getMessage()` directly.

Do not make zeroing configured volatility the final product solution. Until supported enabled execution exists, the guard should be explained explicitly rather than bypassed. Any B1 correction beyond established failure presentation requires a reproducer of the specific native mechanism.

## 4. Historical/outdated result state

`MonteCarloResultLifecycle` asynchronously loads a selected kind's last-success slot and reconstructs it without calculation. It preserves files on decode/compatibility failure, reports unavailable historical results, and leaves normal new-run admission available. Passive restore never invokes accepted-completion callbacks or writes a replacement result.

The following are independent:

- Applicability: CURRENT, OUTDATED, INPUTS_NEED_ATTENTION, INCOMPATIBLE_OR_UNAVAILABLE, or pending assessment.
- Association: recorded Current projection, another historical projection, edited configured inputs, or unrecorded association.
- Execution: IDLE/RUNNING/CANCELLING/COMPLETED/CANCELLED/FAILED/CLOSED session state.
- Durability: memory-only, pending, saved or failed save.

`AnalysisResultFreshness` is a presentation assessment, explicitly not execution authorization. `prepareAnalyzerAssociation` can return null when no unchanged accepted projection is available; `MonteCarloParameterControls.begin` permits that and the run can still become last-success without a projection pin. Equal hashes never invent a projection association. An outdated result remains inspectable/exportable with frozen context.

One admission/assessment inconsistency is verified: comparison freshness capture uses `MonteCarloStrategyComparisonRunService.capture`, which also calls the positive-volatility execution guard. Consequently positive configured volatility can yield INPUTS_NEED_ATTENTION during comparison assessment before effective-input comparison. Standalone freshness capture does not call that guard and can assess the changed expense input. This is not a historical Run lock. Future enabled support should separate pure supported-input capture/assessment from execution admission while preserving original fingerprints.

## 5. Execution admission and failure inventory

These are the current explicit application gates and relevant lower-level invariants. No new restrictions are proposed by this table.

| Source / method | Actual condition | Current user behavior | Intent and recommendation |
|---|---|---|---|
| `MainWindow` Analysis menu / introduction gate | No plan; plan operation/swap or captured plan/revision no longer valid | Menu disabled or guarded launch not admitted | Keep existing lifecycle restrictions. |
| Comparison menu | No saved baseline, or Current/Baseline composition differs | Disabled item plus explanatory menu notice | Keep; no fake baseline or spouse. |
| Standalone `refresh`, `start`; `MonteCarloSession.start` | RUNNING/CANCELLING; session start also rejects CLOSED | Run disabled; duplicate invocation returns without new work | Keep, verify terminal paths always restore controls. |
| Comparison equivalents | Busy, or no baseline; session also rejects CLOSED | Run disabled or request refused | Keep. Composition is revalidated during capture. |
| `MonteCarloInputs.settings` | Count outside 1–10,000; malformed/out-of-range mean (-99..100%), return deviation (0..100%); seed not signed 64-bit integer | Field-specific preflight label, no worker | Keep. UI offers 1,000/2,500/5,000/10,000; core settings allow up to 100,000. |
| Inflation inputs/settings | Active stochastic inflation has malformed/range-invalid mean, deviation or floor; floor must exceed -100% and not exceed its mean; nonfinite transforms | Preflight feedback, or worker failure for generated invalid data | Keep. Inactive stochastic fields are not required. |
| `ProjectionReadiness.isReady` | Missing plan/primary, a present person's DOB, assumptions or projection start | Explicit preflight validation | Keep; does not require a current accepted projection. |
| Freeze/capture and fingerprint boundaries | Plan cannot be copied/frozen, unsupported input shape, or bounded input document invalid | Preflight exception; compatibility may separately say Inputs Need Attention | Keep corruption checks; give a nonempty reason. Do not substitute old inputs. |
| `ExpenseVolatilityAdmission.requireSupported` | Any effective expense volatility > 0 | Standalone worker fails; comparison preflight capture fails | Intentional temporary Phase 14B3A gate, not an acceptable final capability. Replace only with complete enabled execution/provenance support. |
| `MonteCarloRunService` fixed/mortality | Same expense predicate before reference/simulation | Worker FAILED; old result retained | Early guard prevents unnecessary reference work. Keep until enabled path is supported. |
| Public `MonteCarloAnalyzer` fixed/mortality cores | Same expense predicate after plan isolation; applies to generated and caller-supplied paths/worlds | Explicit exception; UI service catches through session | Defense against non-UI bypass; remove/dispatch only with approved enabled contracts. |
| Public comparison core | Guard independently applied to candidate A and B | Fails before generating worlds | Keep equivalent defense until both candidates can carry supported expense provenance. |
| Longevity UI/request | Missing/invalid primary or spouse adjustment (0.50..3.00); couple survivor age missing/invalid (existing policy minimum 60 and valid dates); missing category | Preflight feedback | Keep. Individual requests omit spouse adjustment and survivor election. |
| `MonteCarloAnalyzer.validateMortalityPreparation` | Couple not supported by advanced SS provider; missing survivor election; request/plan DOB, category, conditioning/start or composition mismatch | Worker failure with reason | Keep. Couple longevity requires the existing advanced SS configuration, unlike fixed compatibility paths. |
| Single-person engine validation | Spouse/survivor context, joint account, non-primary income ownership, positive survivor pension election, or missing primary DOB | Explicit worker rejection; applies to opening-only mortality outcomes too | Keep supported shape rules. There is no blanket single-person Monte Carlo ban in current source. |
| Comparison `RunService.capture` | Start dates, composition, birth dates or mortality categories differ | Explicit preflight rejection | Keep common-population requirements. Categories currently checked even in fixed capture. |
| Comparison fixed capture/core | Configured death timing differs; fixed end before start; invalid fixed lifetime shape | Explicit preflight/core rejection | Keep existing comparison definition. Current Plan supplies common end; differing configured lengths alone are allowed. |
| Comparison longevity core | Unsupported advanced couple SS; demographic mismatch; each candidate lacks its own valid survivor election | Explicit failure | Keep per-side elections; never copy Current's election onto Baseline. |
| Reference/world/coverage validation | Fixed reference not complete contiguous requested horizon; null/wrong-index/world composition/timing/path; invalid generated dates or coverage | Worker failure, previous result retained | Keep. Bare headless reference provenance is caller-owned; production uses controller-valid cached reference. |
| Projection tax boundary / `StateTaxUnavailableException` | A selected state's calculation lacks required supported facts/history or is otherwise unavailable | Worker failure; standalone `MonteCarloExecutionException` preserves the typed cause and state-unavailability explanation | Keep existing tax capability restrictions. Do not reinterpret unavailable tax results as zero tax or an ordinary funding-failure trial. |
| Internal enabled `InternalExpenseRun.requireGeneratedWorlds` | Enabled expense execution supplied external worlds/paths | UnsupportedOperationException before supplied source use | Keep for first supported release; current external worlds do not prove complete expense protocol/bindings. |
| Expense path/specification bounds | Invalid volatility/type/amount/date/binding; >1,000 occurrences, >1,000 years or >100,000 enabled occurrence-years | Explicit failure | Keep bounded behavior; expose clear limits before costly execution where possible. |
| Session executor rejection / unexpected worker exception | Executor refuses submission or task throws | FAILED; session changed callback restores busy UI | Keep, add native coverage if implicated. No fabricated financial failure observation. |
| Cancel/source revision/close | Cancellation requested, input/source revision changed, or obsolete generation | CANCELLING/CANCELLED or suppressed closed completion | Keep cooperative boundaries and previous accepted result. |
| Snapshot/result storage admission | Invalid/oversize completed artifact, encoding or disk/storage failure | Capture error can fail the new run; accepted-store save failure has existing retry/memory result behavior | Separate from financial execution. Never discard old durable artifacts or rerun on save retry. |

An authoritative `InsufficientFunds` trial outcome is not a software/admission failure. It contributes to success probability and failure statistics; even a completed all-funding-failed experiment is valid evidence. Absence of a plan filename is not a Run gate: existing memory-only result behavior is intentional.

Historical History dialogs are read-only: opening a stored result performs verification/restoration, not a new simulation. An invalid historical artifact cannot be shown as valid, but is not deleted and does not become a prerequisite for a fresh standalone run.

## 6. Current expense-volatility implementation

| Layer | Current verified behavior |
|---|---|
| Domain | Immutable `Expense.annualExpenseVolatility`, decimal relative standard deviation in [0,1]. Missing/null/all numerical zeros normalize to absent; effective getter returns zero. Nonzero requires recurring type and nonnegative annual amount; zero amount is allowed. No durable expense ID. |
| UI | Shared normal/guided ExpenseDialog exposes percent units, default zero, up to two decimal places for changed/new entries, preserving unchanged higher-precision imports. One-time hides/unmanages and explicitly clears with notice. Negative legacy expenses cannot acquire nonzero volatility. Tooltip already says relative standard deviation, not a maximum change, and explains temporary run blocking. |
| Plan persistence | Focused Expense deserializer preserves original six-property strict behavior while allowing absent/null new volatility. Nonzero exact decimal remains in plans, clones and frozen input JSON. No outer migration is needed. |
| Deterministic projection | Ordinary context has no expense path, so volatility does not affect expected expenses or financial results. Existing General/Healthcare/None escalation remains. |
| Sampling | `ExpenseSamplingSpecification`, `MonteCarloExpenseGenerator` and `ProjectionExpensePath` implement the existing frozen independent annual level protocol. |
| Financial integration | Engine accepts only an explicitly bound expense path; applies it before existing proration, survivor adjustment and cash funding. Medicare reevaluation reuses the same path. |
| Internal Monte Carlo | Package-private fixed/longevity/couple/individual/paired execution uses `InternalExpenseRun`; internal wrapper results cannot enter ordinary snapshot/PDF APIs. |
| Supported Monte Carlo | All public execution families reject positive configured volatility. There is no supported public enabled branch elsewhere in production. |
| Deterministic identity | `DeterministicExpenseInputs` excludes only the known recurring-expense volatility property for deterministic applicability. Complete configured documents/digests remain exact. A volatility-only edit can retain applicable deterministic output while still changing configured identity/source revision. |
| Monte Carlo identity/history | MC frozen plan inputs include positive volatility; effective fingerprints do not strip it. Existing stored result provenance has no expense-model capability. Original historical values remain frozen. |
| PDF | Current MC PDFs use frozen completed reports and original context. There is no supported enabled expense model disclosure/result artifact today. Plan JSON containing volatility is insufficient to certify that a historical report used it. |

The implementation stopped deliberately before the Phase 14B3B/C public provenance/execution stages. Later Phase 19 work retained this gate; it did not introduce the original limitation.

## 7. Recommended stochastic model

### Meaning and distribution

Retain the already implemented interpretation: configured percentage / 100 is **annual relative standard deviation of that expense's level around its escalated expected amount**, not a maximum percentage swing and not a random inflation rate.

For `v` in [0,1], use:

```text
s² = ln(1 + v²)
μ  = -s² / 2
M  = exp(μ + sqrt(s²) Z),  Z ~ N(0,1)
expense(year) = expectedExpense(year) × M(expense, scenario, calendarYear)
```

Then `E[M] = 1` and `Var[M] = v²`. The derivation follows the lognormal mean and coefficient-of-variation relationships in the [NIST lognormal distribution reference](https://www.itl.nist.gov/div898/handbook/eda/section3/eda3669.htm). Thus the deterministic escalated expense is the conditional expected amount before existing financial rounding. Finite samples need not average exactly to it. Cents rounding can introduce small rounding effects, and nonlinear taxes/withdrawals/survival mean deterministic final wealth is not necessarily expected simulated wealth.

| Distribution | Assessment |
|---|---|
| Normal additive/multiplicative level | Simple moments but permits negative spending. Clamping at zero changes the expected value; a truncated recalibration would change the accepted protocol. Not recommended. |
| Mean-one lognormal level | Strictly positive for positive base, matches the intended mean/relative deviation, permits asymmetric adverse tails, and already has tested V1 implementation. Recommended. |
| Bounded uniform/triangular or truncated law | Can enforce a maximum swing, but that is a different interpretation and requires new tail/calibration choices. Defer as an optional future model rather than reinterpret existing percentages. |

The lognormal tail is unbounded in the ideal model; 100% volatility does not cap spending at twice the expected amount. Do not clip tails or normalize a sample to force a mean. Existing statistical transforms use `StrictMath` with explicit finite/positive checks, then one `BigDecimal.valueOf` boundary; subsequent money operations remain BigDecimal. Preserve known-answer outputs, not a new all-BigDecimal transcendental implementation. Very small positive volatility can be below transform precision; do not silently label it disabled.

### Frequency, correlation and timing

Use one independent **level shock per enabled expense occurrence per absolute calendar year per scenario**. It does not accumulate from the previous year's shock. Keep shocks independent across distinct expenses, years, market returns, inflation draws and mortality. Shared inflation can still induce common variation in expected dollar spending; independence here concerns the additional expense shock. Serial persistence, correlated categories and market-linked shocks require separately approved models.

The current Expense model stores annual amounts and calendar-year overlap, not monthly occurrence schedules or custom per-record escalation rates. General/Healthcare/None are the implemented escalation choices. Monthly input concepts must continue to use the existing annualized amount; do not create twelve independent draws or new monthly timing in this phase.

Preserve the existing order:

1. Active-year overlap and all-household-deceased recurring exclusion.
2. General/Healthcare/None escalation using each plan's existing assumptions; stochastic general inflation affects its existing scope only.
3. One fixed expense/year multiplier.
4. Existing first-year recurring proration `(13 - startMonth) / 12` and rounding.
5. Existing post-death recurring factor; household aggregation and cents rounding.

Later-year midyear start/end uses annual overlap today, not new day/month proration. One-time expenses are not sampled. Zero-budget entries remain zero. Negative legacy amounts are retained with volatility disabled. Zero-volatility execution must take the exact legacy branch without expense draws, allocations or multiplication by one.

## 8. Random streams and comparison correspondence

Preserve the frozen existing streams:

- Market V1: `java.util.Random(seed + 0x9E3779B97F4A7C15L × scenarioIndex)`, with intentional long overflow and the original annual Gaussian sequence.
- Existing independent SHA-256 streams: household mortality dimension 1, primary mortality 2, general inflation 4; spouse mortality 3 and healthcare inflation 5 remain reserved as in current source.
- Expense dimension 6/version 1 uses its own keyed SHA-256 protocol, not draws inserted into any sequential stream.

Expense V1 keys include master seed, scenario index, model/dimension version, full normalized expense-definition digest, duplicate ordinal and absolute calendar year. StrictMath Box–Muller derives the normal from fixed digest bytes. Changes to count, scheduling, expense list ordering or another expense must not shift return/mortality/inflation sequences or common expense/year prefixes.

Reuse this implemented correspondence policy:

- A complete normalized sampling definition includes type, annual amount, dates, escalation enum and volatility. Names, plan UUIDs and Java object identity are excluded.
- Distinct definitions pair only if all these sampling fields match. Financial fingerprints remain separately exact/scale-sensitive; sampling normalization never rewrites inputs or identity.
- Exact duplicate definitions receive zero-based occurrence ordinals within that definition. Global list positions are only bindings to a validated frozen list, never random identity.
- Reordering unique entries or adding unrelated expenses preserves their shocks. Corresponding duplicate 0 pairs with duplicate 0, duplicate 1 with duplicate 1. Extra/unmatched expenses have separate streams. Each side uses its own frozen expense values.
- Editing amount, dates, escalation or volatility deliberately breaks current V1 correspondence. This is conservative and already tested. There is no durable entity ID for proving that edited records are “the same expense”.

Recommend approving this conservative policy for the first public release. Sharing latent normals for edited amounts/volatilities could reduce comparison noise, but would need a new explicit correspondence protocol or stable identity/mapping decision. Do not infer identity from descriptions or add plan-schema IDs as incidental enablement work. Matching random keys do not establish historical associations.

Paired A/B plans retain the same existing economic/lifetime/inflation worlds and comparison horizon. Corresponding expense definitions receive equivalent keyed shocks on the two sides. Bind each generated path to its own frozen plan; never copy Current's expense list or volatility onto Saved Baseline.

## 9. Financial integration design

Reuse `ProjectionEvaluationContext.withExpensePath` and the existing `ProjectionEngine.calculateProjectedExpenses` boundary (line 1133). `InternalExpenseRun.apply` already generates and binds a pure immutable path outside the engine. A supported execution adapter should read configured volatility once from detached inputs and reuse this implementation; it should not introduce a second expense calculator in Monte Carlo orchestration.

```text
frozen plan expenses + approved model/seed/scenario
  -> immutable expense path, bound to exact financial definitions
  -> existing escalated annual expenses × fixed annual multiplier
  -> ordinary expenses + Medicare
  -> guaranteed-income/RMD funding and remaining withdrawals
  -> taxes, Roth/bracket-fill and tax-funding feedback, settlement
  -> projected balances and subsequent-year growth/RMDs
  -> annual results, portfolio survival, percentiles and estate metrics
```

Taxable and tax-deferred withdrawal ordering remains authoritative. Spending is not automatically tax-deductible. Federal/state taxes and IRMAA can change indirectly through income/funding changes; do not impose a predetermined direction. Statutory current-year RMD is based on the existing prior-year snapshot, while shocks can alter the use of RMD cash and future balances. Roth strategy/execution rules remain unchanged; applicable cash/tax funding responds through the common engine.

Medicare retries, tax funding and any repeated same-year evaluation must use the same immutable multiplier. Never sample in `calculateProjectionYear`, tax calculators or report adapters. Existing B2 tests compare against equivalent fixed-budget financial oracles and verify no double application during Medicare reevaluation.

Prepared metadata is O(expenses); one generated scenario path is O(enabled expenses × years), with the existing 100,000-entry bound. Paths are discarded after reducing each scenario, rather than retained across all trials or persisted. Paired execution holds at most the required side paths during evaluation. Do not add an unbounded cache or per-scenario deep plan copy.

## 10. Persistence, input identity and history

Plan-level configuration is already supported; no additional Expense field or outer plan schema migration is recommended. Positive volatility already participates in MC financial input fingerprints through frozen plan JSON. Default omitted volatility preserves existing zero-input documents.

The missing contract is **execution provenance**:

- `MonteCarloSnapshot` supports version 1 only. Its `Provenance` covers returns, mortality, inflation, streams, aggregation and world source, but has no enabled expense-model identifier/binding contract.
- `AnalysisSnapshotCodec` currently dispatches all MC kinds to that concrete V1 class. Lifecycle, numeric validation, history and PDF restoration make the same concrete assumption.
- `AnalysisVersions.algorithms(kind)` supplies kind-based V1 tags. `MonteCarloFingerprintService` verifies EXACT_V1 original hashes and uses TEMPORARY_NUMERIC_V2 only for approved numeric-setting compatibility. V2 is not an unused slot for expense semantics.
- `AnalysisSnapshot` currently accepts fingerprintVersion 1 only. A new supported fingerprint contract therefore requires an explicit compatible envelope-validation change, not merely a new enum constant in the fingerprint service.
- Internal enabled result wrappers intentionally cannot be passed as ordinary supported legacy results. Simply removing them/the gate would lose the semantic separation.

Recommend following the unimplemented Phase 14B3 design: an explicitly supported enabled payload/capability (provisionally expense snapshot V2), plus a distinct enabled fingerprint contract (provisionally V3), with one small read-only adapter shared by existing result/history/PDF infrastructure. Final names/version numbers require approval. Preserve the original envelope and V1 decoder/hash behavior; permit only explicit supported envelope/payload/fingerprint combinations. Do not globally bump the algorithm map for every legacy MC artifact.

Enabled provenance should include the expense model ID `INDEPENDENT_LOGNORMAL_EXPENSE_LEVEL_V1`, dimension/version 6/1, correspondence version 1, original frozen candidate documents, seed/settings/lifetime source, and bounded occurrence/binding metadata needed to verify the model. Derive duplicate financial facts from frozen plans where possible. Do not persist every expense path or scenario random draw.

Coordinate codec dispatch, strict/bounded validation, numeric budgets, accepted-result capture, compatibility, last-success storage, immutable association loading, Save/Load/Save As/Swap and historical report adapters before enabling a public result. Existing raw/compressed limits (32/16 MiB), depth/token/array/number limits, ownership/digests and atomic-write guarantees remain. Keep the Phase 19 snapshot compatibility correction intact.

An old V1 result remains its original evidence; absence of expense provenance is not permission to call it a new enabled run. If a V1 artifact contains positive configured volatility, do not infer how it was executed, silently upgrade it, or recompute it. Preserve original evidence and classify unsupported capability explicitly pending investigation.

Volatility-only edits still advance configured revision/admission guards, even if accepted deterministic financial results remain applicable. Exact configured-input swap/baseline preconditions must not be relaxed as a side effect. Associations follow original projection UUID/digest, not the labels Current/Baseline. New runs must freeze inputs once, and old results must never be recomputed from changed live assumptions.

Keep enabled caller-supplied worlds/paths explicitly unsupported in the first release: they currently do not carry validated expense protocol, candidate mapping or complete expense/year coverage. Generated public UI runs can be supported without inventing this additional API.

## 11. Reporting requirements

Before an enabled run is published, its minimum UI/report context must identify that expense volatility was active and name the annual independent, mean-preserving level model. Standalone reports use the original run's configured expenses. Comparison reports disclose each candidate's enabled settings and the strict common-random-number correspondence policy independently. Retain seed, trial count, return/inflation/mortality context and original result dates.

Recommended compact explanation: volatility is relative annual standard deviation, not a maximum increase; deterministic escalation is the expected spending base; one-time expenses and deterministic projections are unaffected. Disclose independence and unbounded model tails without implying that every expense uses one common shock.

Use existing Analysis Details/assumptions sections and frozen PDF adapters; avoid adding random multipliers to every financial row. Zero-volatility reports should retain their original layout/content where possible. Historical reports use their own frozen input/provenance, never today's plan or a freshly sampled path. Preserve existing View/Save/Cancel, default export directory, charts, PDF content unrelated to the new capability, and no-rerun guarantees.

## 12. Proposed bounded implementation phases

| Phase | Scope and acceptance gate |
|---|---|
| **21B1 — Run control/feedback** | Reproduce the native no-response mechanism with sanitized session-stage evidence. Correct only established handler/admission/feedback/lifecycle defects. Make legitimate blocked attempts understandable and keep history inspectable. No removal of positive-volatility financial gate or stochastic math change. If evidence requires engine/persistence changes, stop or escalate scope/test level. Native UI acceptance before closure. |
| **21B2 — Supported standalone stochastic expenses** | Reuse/reverify B1/B2 math; first implement the approved capability, immutable enabled request/result, codec/fingerprint compatibility, restoration and minimum model disclosure. Then adapt configured expenses into existing internal execution and enable generated fixed/longevity, couple/individual standalone paths. Preserve disabled branch exactly. Do not publish a legacy-shaped enabled result as an intermediate release. Gate on Level 3 and native acceptance. |
| **21B3 — Supported comparisons** | Extend the approved capability to A/B frozen requests, exact-definition CRN correspondence, per-side provenance, shared horizons/lifetimes, history and report adapters. Only then enable positive volatility on either candidate. Test all four household/mode combinations and asymmetric enabled candidates. Gate on Level 3 and native acceptance. |
| **21B4 — Reporting and closure** | Reconcile every current/history/PDF surface and all eight MC kinds, finish optional layout/disclosure refinement, performance/limits and native acceptance. Required truthful provenance/disclosures must already accompany enabled B2/B3 publication. Select Level 1/2 for presentation only, Level 3 for result identity or financial dependencies; complete milestone regression before release. |

The existing engine makes a new mathematics-first rewrite unnecessary. Splitting 21B2 into an approved provenance foundation and a supported execution increment is safer than enabling execution before its immutable artifact contract is complete. Phase 16B3 and unrelated work remain deferred.

## 13. Explicit decisions requiring approval

1. Retain the existing relative-standard-deviation, annual independent mean-one lognormal V1 model, including its unbounded positive tail and existing numeric transform boundary.
2. Retain annual level sampling, existing escalation/proration/death rules and independence from market, mortality and inflation shocks. No monthly model or serial/category correlation in the first release.
3. Retain strict complete-definition/duplicate correspondence for CRN; changed definitions are unmatched. Durable expense IDs or looser matching are deferred.
4. Approve an explicit enabled snapshot/capability and fingerprint contract, with final identifiers determined before implementation, preserving all existing V1/V2 compatibility and immutable artifacts.
5. Support generated worlds first and retain explicit enabled supplied-world rejection until a separate complete provenance/coverage contract is approved.
6. Use the phased sequence above, with minimum provenance/disclosure complete before public enablement and native acceptance at each implementation gate.
7. For 21B1, obtain the remaining native stage/visibility evidence rather than treating the unproven no-response symptom as an established handler defect. Positive-volatility execution rejection is already reproduced and requires the broader supported capability, not a bypass.

## 14. Risk-based test plan

### 21B1 — Level 2 unless dependencies expand

Run current MC view/session, input-validation, parameter acceptance/rollback, historical restoration, association/freshness and worker rejection/cancel/close tests. Add the native-specific failing regression after its cause is known. Cover old result plus new valid Run, invalid inputs with explicit feedback, fast worker rejection with a visible persistent reason, rapid clicks, source revision changes, dialog closure, null-message fallback if implicated, Light/Dark and compact viewports. Verify zero projection/analyzer work for blocked preflight and no historical mutation.

### 21B2/21B3 — Level 3

Require the full Maven suite and unchanged financial/seeded golden assertions. Include:

- Exact zero-volatility JSON, fingerprints, random bytes and financial results, with absence/null/zero/decimal-scale variants.
- Positive reproducibility at multiple seeds, count/order/horizon-prefix independence, exact known-answer vectors, nonnegative/zero spending, bounds and failed sampling validation.
- Analytic mean/variance derivation plus deterministic large-sample statistical tests with justified tolerances, never assertions that every finite sample has its exact target mean.
- General/Healthcare/None, first-year proration, active overlap, survivor changes, opening-only mortality, one-time exclusion and duplicate/add/remove/reorder cases.
- Fixed-budget direct-engine oracles for taxes, Roth, RMD use, withdrawals, Medicare retry, settlement, balances and estate. No predetermined universal tax direction.
- Standalone versus direct projections and comparisons; identical A/B equality; asymmetric volatility; matching definitions share draws and unmatched definitions do not shift unrelated streams.
- Couple/individual × fixed/longevity; unchanged mortality distributions, survivor elections and market/inflation streams.
- Supported enabled payload round trips, corruption/unsupported versions, legacy restoration, raw/compressed budgets, ownership/association validation, Save/Load/Save As/Swap, stale-result assessment and historical PDF original context.
- Cancellation, worker exceptions, no partial accepted result/association, retained old results, storage retry without rerun, closure/generation and source-edit guards.

### 21B4 and milestone closure

Use Level 1 for text/CSS/layout and Level 2 for PDF/UI workflow changes. Escalate to Level 3 if frozen report mapping, serialization, fingerprints or result identity changes. Inspect actual Light/Dark renderings/PDFs and native Windows 100/125/150% scaling; do not claim automated scene assertions certify screen readers or native visibility. Run the complete suite at the final significant integration/release milestone.

## 15. Audit verification performed

Used the mandated IntelliJ bundled Maven and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`, Java 25, with `-DargLine=-Xmx4g`.

| Existing focused selection | Run | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| MC views/sessions/comparison/lifecycle/restoration; admission; expense editor/sampler/financial-path; numeric compatibility and random streams | 155 | 155 | 0 | 0 | 0 |
| Expense configuration compatibility; MC projection association, parameter persistence and mortality view | 73 | 73 | 0 | 0 | 0 |
| **Total, disjoint selections** | **228** | **228** | **0** | **0** | **0** |

Exact first selection:

```text
MonteCarloViewTest, MonteCarloSessionTest, MonteCarloComparisonViewTest,
MonteCarloComparisonSessionTest, MonteCarloResultLifecycleTest,
MonteCarloRestorationViewTest, ExpenseVolatilityAdmissionTest,
ExpenseVolatilityUiTest, MonteCarloExpenseGeneratorTest,
StochasticExpenseProjectionTest, StochasticExpenseMonteCarloTest,
MonteCarloRandomStreamsTest, MonteCarloNumericCompatibilityTest
```

Second selection:

```text
ExpenseVolatilityCompatibilityTest, MonteCarloProjectionAssociationTest,
MonteCarloParameterPersistenceTest, MonteCarloMortalityViewTest
```

Logs: `target/phase21a-focused.log` (27.456 s) and `target/phase21a-history-focused.log` (44.692 s). Expected failure-injection warning traces occurred in passing tests. The separate one-trial synthetic audit probe is diagnostic evidence and is not counted as additional JUnit tests.

No full long-running financial regression, exhaustive analyzer search, production-scale Monte Carlo benchmark or opt-in capacity suite was run: no production behavior changed, and the user explicitly restricted this audit to focused verification. These omissions leave production-scale capacity and native presentation unverified in this phase, not permanently skipped/disabled. No test source or assertion was changed. Existing UI tests render scenes, but this audit does not claim manual Light/Dark Run-dialog visual inspection, native DPI or accessibility certification.

`git diff --check` passed. Final Git status contains only this new report; tracked production/test files and the index are unchanged. Ignored diagnostic harness/classes/logs are under `target/` only. No real retirement plan, user financial record, snapshot or backup was accessed or modified. Nothing was staged, committed, pushed, reset, cleaned or reverted.

## 16. Approval gate

Phase 21A ends with this audit. Approve the model, correspondence, provenance and phased implementation decisions before Phase 21B work. The comparison and positive-volatility standalone execution block is established; the specific native no-visible-response mechanism remains an explicit diagnostic gap, not a claimed repaired defect.
