# Phase 14 — Recurring Expense Inflation and Stochastic Modeling Architecture Audit

## Executive summary

**Audit only; no implementation is authorized by this report.** Existing recurring expenses already inflate. General expenses use general inflation; Healthcare expenses use healthcare inflation. Simply adding a default-false checkbox would change saved-plan behavior, while applying another general-inflation multiplier would double-count escalation.

Recommend explicit per-expense escalation semantics, preserving legacy category-based escalation, plus an optional per-expense annual variability percentage used **only** by Monte Carlo. The initial stochastic model should be independent annual, mean-one lognormal **expense-level** multipliers, not a compounded random growth rate. Generate immutable scenario inputs outside ProjectionEngine; apply the same expense/year multiplier during all financial evaluations of that year. Keep the disabled path exactly unchanged.

Implementation is feasible within the existing isolated-plan, evaluation-context and independent-random-stream architecture. It needs an approved Healthcare/checkbox policy, sampling-correspondence protocol, migration and fingerprint/provenance design. It is not just a UI checkbox change. No existing financial formula, plan, snapshot, test or golden fixture was changed during this audit.

## Audit baseline and evidence

- Branch: main. HEAD: `7ee0365b3ce59cdcfb18c9ac161a2b2e49aad57d`.
- Pre-existing working-tree change: `.idea/encodings.xml`. Index empty. This change is preserved and excluded.
- Read AGENTS.md; inspected actual domain, projection, persistence, Monte Carlo, fingerprint, expense-editor/wizard, historical-input and comparison code. Reviewed the Phase 12D snapshot report to cross-check historical isolation against current `CompleteProjectionSnapshot` source.
- No real user-plan file was opened, modified or included in diagnostics. Examples below are synthetic.
- Verified existing behavior and proposed behavior are separated below. Proposed class/field names are architectural suggestions, not implemented APIs.

## 1. Verified existing expense and persistence model

| Area | Verified implementation |
|---|---|
| Expense | `domain.financial.Expense`: final description, annualAmount, growthCategory, startDate, endDate and expenseType fields; constructor-based Jackson loading; no expense UUID, inflation opt-out or volatility field. |
| Defaults | Missing growthCategory becomes GENERAL; missing expenseType becomes RECURRING. Convenience constructors also default to General/Recurring. |
| Categories | `GrowthCategory` contains GENERAL and HEALTHCARE; `ExpenseType` distinguishes RECURRING and ONE_TIME. No separate per-expense escalation rate or custom escalation schedule was found in this model. |
| Ownership | `Household` holds the expense list, returns an unmodifiable list, and edits entries through add/remove/replace. Expense does not override equals/hashCode to establish durable identity. |
| Validation | Expense requires nonnull description/amount and valid date ordering. It does not itself reject negative annual amounts. ExpenseDialog's amount path parses BigDecimal without a positive-amount check in its result construction. Do not silently reinterpret such legacy entries. |
| Plan storage | `JsonRetirementPlanRepository` serializes the plan's household expenses with Jackson; legacy constructor defaults govern missing fields. No new schema is introduced in this audit. |
| Editing | `ui.dialogs.ExpenseDialog` uses a General/Healthcare growth-category ComboBox and replaces Expense with a new instance. Its tooltip explicitly describes opening-year-dollar amounts. New fields must be loaded and preserved on edit, not reset accidentally. |
| Other UI | `ExpensesView` displays category and launches ExpenseDialog. `ExpensesWizardStep` reuses the dialog and explains that future amounts grow from the projection opening-year base. |
| One-time UI | Uses a purchase date as both start/end, hides growth category and selects General. Domain-loaded historical one-time expenses can still have a category; do not change their calculation semantics as collateral work. |

An amount is a **projection-opening-year spending base**, including an expense starting later. It is not currently a quoted amount in its own future start year. A new inflation option must not shift that reference year silently.

## 2. Verified projection flow and timing

`ProjectionEngine.calculateProjectedExpenses` iterates household expenses:

1. Checks `Expense.isActiveDuringYear(calendarYear, projectionStartDate)` using overlap with the modeled calendar year, or the first partial-year interval.
2. Omits recurring expenses when the entire household is deceased.
3. Uses `calculateProjectedExpense` / `getExpenseGrowthRate` to grow the original annual amount from opening-year offset zero.
4. For first-year recurring expenses only, multiplies by `(13 - projectionStartMonth) / 12`, rounding to cents HALF_UP at that point.
5. Applies the configured post-death recurring-expense factor when a modeled death has occurred.
6. Sums expenses and rounds the household total to cents HALF_UP.

Later-year starts/ends are annual overlap gates; the engine does **not** prorate each expense to its precise active days/months in every year. A midyear start in a later year can therefore receive the full annual amount. First-year proration is based on the projection start month, not a separate expense-specific active-month count. One-time expenses are not subject to recurring proration/death-factor rules. These are existing semantics, not defects being corrected here.

### Inflation sources

- `EconomicAssumptions` centralizes investment return, general inflation, healthcare inflation and Social Security COLA. Legacy `expectedAnnualInflationRate` maps to general inflation; missing specialized rates default through the existing constructor. These are distinct current assumptions, not interchangeable new controls.
- Deterministic spending: General and Healthcare use their respective configured rates through `CompoundGrowthService.project(amount, rate, yearOffset)`.
- Existing stochastic general inflation: `ProjectionInflationPath` represents general **spending** inflation only. Its rate for Y grows spending from Y-1 into Y. Opening-year spending is not inflated by its recorded opening-year rate.
- ProjectionEngine maintains a cumulative `generalExpenseMultiplier`, multiplying successive path rates only after offset zero. With a path present, non-healthcare expenses use that multiplier instead of deterministic general compounding. Healthcare retains its configured healthcare escalation.
- The stochastic spending path is not a global replacement for government-rule inflation, healthcare inflation, Social Security COLA, property appreciation or other specialized rates. The proposal must retain that scope.

### Downstream financial effects

Annual ordinary expenses feed `cashFlowExpenses` (ordinary expenses plus separately calculated Medicare), then `WithdrawalCalculator`, statutory-RMD allocation, additional withdrawals, available cash for taxes, Roth conversion capacity/bracket-fill context, tax-funding feedback and `HouseholdCashSettlement`. Ending projected balances feed subsequent growth, prior-December-31 RMD snapshots and future-year taxes/estate results.

Expense shocks must enter **before** this flow, not be added to a Monte Carlo summary afterward. Same-year statutory RMD is not directly a function of current spending; current shocks can change how RMD cash is used and later balances/RMD requirements. Expenses are not automatically tax-deductible; tax effects arise through the existing funding and taxable-withdrawal mechanics.

**Critical replay finding:** `calculateProjectionYear` can call itself again with authoritative Medicare premiums. It consequently recalculates expenses. Never draw randomness inside that function or its expense helper. Otherwise one calendar year could receive different spending during its own cash-flow/tax/Medicare evaluations.

## 3. Analyzer-family scope

| Consumer | Inflation option | Expense volatility |
|---|---|---|
| Standard deterministic projection and explicit deterministic reference | Uses selected escalation policy with existing centralized rates | Always ignored; no random generator reachable |
| Couple/individual Social Security Only | Expenses do not affect benefit-only calculation/ranking | Not used; should not invalidate its financial applicability just because expense volatility changes |
| Quick, exhaustive and individual deterministic integrated analyses | Full-plan projections must honor the selected expense policy | Not used; do not add market/expense randomness |
| Longevity-weighted integrated analyses | Honor expense policy in each existing lifetime projection; retain exact death horizons | Not used. Lifetime probabilities are not authorization for Monte Carlo expense draws |
| Break-Even and baseline comparisons | Use their established projections/results and financial definitions | No stochastic execution introduced |
| All eight Monte Carlo identities | Respect selected policy and existing Fixed/Longevity, standalone/paired, couple/individual scope | Use enabled run-only expense multipliers in full financial execution |
| Historical results/PDFs | Preserve original saved inputs and results | Never rerun/reconstruct historical results to add shocks |

## 4. Proposed domain and UI model — approval required

### Per-expense configuration

Keep expense-specific choices on immutable Expense, not in Application Settings and not as duplicate per-expense economic rates:

- Optional explicit `escalationPolicy`: GENERAL, HEALTHCARE or NONE. Absent means **legacy growth-category behavior**. Retain the existing growthCategory data for legacy deserialization; an explicit policy, when present, is the sole effective source, not a second multiplier.
- Optional `annualExpenseVolatility`: BigDecimal rate; absence/zero means disabled. It is the standard deviation of the annual multiplier, expressed as a percentage of that year's expected expense, **not** the log-distribution sigma, inflation rate or dollar standard deviation.
- Derived effective-policy/volatility accessors must be JsonIgnored; raw optional fields must omit absent/default values as specified by the compatibility contract. Do not serialize an extra computed financial assumption accidentally.

Example: 10% variability on an expected $10,000 annual expense means a $1,000 standard deviation before existing proration/death adjustments. Expected spending still comes from the inflation policy. No new inflation percentage is added to the expense editor.

Recommended defaults: new recurring expense General escalation enabled, volatility disabled/0%; existing General stays General, existing Healthcare stays Healthcare, all existing volatility stays disabled. Defaulting all old records to no inflation is rejected. One-time expenses are out of stochastic scope initially; preserve their existing behavior and hide/disable new recurring-only controls.

### Checkbox ambiguity that must be resolved

The requested **Adjust using general inflation** checkbox cannot honestly represent all existing General, Healthcare and fixed-dollar cases as one boolean. In particular, unchecked must not silently mean both “fixed nominal dollars” and “still uses healthcare inflation.”

**Recommended approval:** use the existing category selection to expose three explicit escalation modes (General inflation, Healthcare inflation, No inflation), with the requested checkbox synchronized as a view of the General choice rather than an independent persisted flag. Check selects General; unchecking an active General choice selects None. For Healthcare, retain an explicitly labeled Healthcare state rather than treating an ordinary unchecked box as no escalation. Prefer approving the three-way selector as the primary control; if the checkbox must be the sole control, approve a clearly explained indeterminate/legacy-Healthcare state first. Do not implement that UI policy implicitly.

Proposed tooltip: “Use the plan's General Inflation assumption to increase this expense from the projection's opening-year dollar amount. This does not add another inflation rate.” Healthcare/None must have equally explicit descriptions.

## 5. Proposed stochastic model

For each enabled positive recurring expense e, simulation i and calendar year y, let v be the configured relative annual standard deviation:

```
s² = ln(1 + v²)
mu = -s² / 2
M(i,e,y) = exp(mu + sqrt(s²) * Z(i,e,y)),  Z ~ N(0,1)
E[M] = 1; Var[M] = v²
```

These moment choices follow the lognormal mean and coefficient-of-variation identities in the [NIST lognormal reference](https://www.itl.nist.gov/div898/handbook/eda/section3/eda3669.htm). The parameter selection above is the proposed model derivation, not an existing application formula. Multipliers are positive, skewed and unbounded above; their median is below their mean. Mean preservation does not imply unchanged average wealth after nonlinear taxes, withdrawals and funding failures.

Alternatives considered: a normal multiplier around one can produce negative spending; clipping/truncation would require a different moment-calibration policy. A positive Gamma multiplier is another candidate but would require a separately specified/reproducible sampler rather than reusing the existing lognormal transform pattern. Empirical/bootstrap spending shocks would require a defensible dataset and calibration not present in this application. The lognormal choice is therefore a bounded engineering recommendation, not an assertion that real household spending necessarily follows that distribution.

Recommend independent annual **level** shocks, independent across distinct expense occurrences and independent of existing returns, inflation and mortality. A high-spending year does not permanently raise next year's expense base. Inflation still compounds; expense shocks do not. A persistent random-growth model or a shared household expense component would be a separate model requiring explicit assumptions/calibration. Do not imply medical/living-cost correlations are modeled in the initial independent model.

Expected amount = base amount × selected inflation factor. For enabled expenses, apply the year's multiplier before the existing first-year proration and death adjustments; retain existing rounding boundaries. Apply a first-year level shock even for a partial year, then use existing proration; do not silently introduce square-root-of-time scaling. Explicit None still permits stochastic level variability in Monte Carlo, but no trend escalation.

Zero expected expense remains exactly zero. Negative legacy expenses must remain unchanged when volatility is disabled. Recommend rejecting enabled stochastic treatment of negative amounts with a targeted request-validation message, not silently clamping/reclassifying them. Proposed initial volatility bound: 0–100% relative annual standard deviation, subject to approval. Invalid/nonfinite generated values are structured execution errors under the existing Monte Carlo failure policy, not fabricated zero spending or artificial funding failures. No tail clipping unless a separately approved distribution/normalization policy is designed.

## 6. Seeded architecture and calculation flow

### Existing reusable infrastructure

- `MonteCarloScenarioGenerator`: frozen V1 market seed formula uses `java.util.Random(seed + constant * scenarioIndex)` and a StrictMath lognormal transform. Never consume additional draws from that stream.
- `MonteCarloRandomStreams`: SHA-256 counter streams with frozen IDs: household mortality 1, primary mortality 2, spouse mortality 3, general inflation 4, healthcare inflation 5 (including reserved IDs). Do not repurpose any existing/reserved dimension.
- `MonteCarloInflationGenerator`: independent dimension 4; uses a documented two-word Box–Muller conversion and no cached sine. This is a reusable protocol pattern, not permission to advance its state for expenses.
- `MonteCarloWorldGenerator`, `MonteCarloWorld`, `MonteCarloComparisonWorld`: immutable exogenous scenario inputs; comparison evaluates both plans against a shared generated world. Caller-supplied worlds have explicit provenance requirements.
- `ProjectionEvaluationContext`: immutable, projection-run-only overrides, with existing withInflationPath/lifetime/horizon builders. Ordinary execution has an empty context. Every builder must preserve any future expense path.

### Recommended extension

Introduce a small immutable `ProjectionExpensePath` containing validated per-expense-occurrence/per-year BigDecimal multipliers, plus a Monte Carlo-only generator. Pass it as an optional evaluation-context field. Domain code consumes immutable values; no RNG, JavaFX object, settings service or plan mutation enters ProjectionEngine.

Use a **new versioned expense dimension**, proposed ID 6 after checking the allocation registry at implementation time. Existing `create` stream bytes and return-generator formula must remain byte-for-byte compatible. The new keyed helper must incorporate master seed, scenario index, versioned expense correspondence key, duplicate occurrence and absolute calendar year. Use full digest keys with explicit lengths/byte order; do not truncate arbitrary hashes into small integers or use Java hashCode/object identity.

Generate one normal draw per `(scenario, expense occurrence, year)`, using the established StrictMath/finite-BigDecimal conversion boundary. Financial arithmetic remains BigDecimal. A keyed calendar-year design avoids draw shifts from reordering unrelated expenses, horizon extension, inactivity, death, cancellation or changed simulation counts. Define/freeze byte-to-uniform, normal transform, numeric conversion and model version before implementation tests.

```
Frozen accepted/request plan inputs + Monte Carlo seed/settings
  -> existing market / mortality / general-inflation generators (unchanged)
  -> new isolated expense generator
  -> immutable world, with side-specific expense bindings for comparisons
  -> ProjectionEvaluationContext.withExpensePath(...)
  -> expense escalation -> fixed year's level multiplier -> existing proration/death rules
  -> ordinary cash flow / RMD allocation / withdrawals / Roth / taxes / cash settlement
  -> original projected balances, next-year RMD snapshots and Monte Carlo reductions
```

At v=0 for all expenses, generate no expense stream/path at all; take the exact existing arithmetic path. Even multiplying by BigDecimal.ONE or normalizing scales unnecessarily can change exact representation, so require exact disabled-path regression checks. Deterministic references and all deterministic/longevity-weighted claiming analyses always omit the path regardless of configured volatility.

Within a simulation, reuse the same immutable multiplier during Medicare recursive reevaluation and any other same-year retries. Never resample during withdrawal allocation, tax funding or Roth bracket-fill calculations. Compute expense-adjusted spending before financial decisions, and let existing services propagate its consequences.

## 7. Paired comparisons and expense correspondence

Current `MonteCarloStrategyComparisonRequest.worldSource()` builds common market/inflation/lifetime worlds; `MonteCarloStrategyComparisonAnalyzer` evaluates A then B with no RNG reachable from financial evaluation. Extend this boundary; do not independently create sequential expense RNGs inside each side.

Expense has no durable ID. Recommend a bounded initial **sampling correspondence key**, not a new financial/projection identity: canonical effective financial definition (type, amount, dates, effective escalation and volatility), plus duplicate occurrence within that definition. Exclude display descriptions and plan/projection UUIDs. Document numeric normalization for this new key only, so financially equal decimal representations can share a draw; do not change existing scale-sensitive financial fingerprint rules. No approximate/name-only matching.

Exact matching financial definitions on A/B share the same normal/multiplier for each absolute calendar year. Reordering unrelated entries or different projection horizons must preserve matched common-year shocks. Exact duplicate definitions get distinct independent occurrence streams; first-seen duplicate occurrence pairs with the equivalent occurrence on the other side. This is exchangeable sampling correspondence, **not** a claim of durable household-entity/history identity. Additional unmatched occurrences get their own streams.

With strict definition matching, editing amount or volatility can break correspondence and increase comparison noise. If users require the same underlying shock for an edited expense, approve explicit stable expense IDs/correspondence separately; do not infer it from matching names. A broad durable-ID migration is not recommended in this first implementation. An alternative common household shock is simpler but changes cross-expense correlation and must not be smuggled in as a matching workaround.

Supplied-world overloads must explicitly provide compatible expense paths/provenance when volatility is enabled, or fail validation. Do not silently generate missing shocks alongside caller-supplied financial worlds. Association validity still requires the recorded projection UUID/owner; a sampling-key match must never create a historical association.

## 8. Backward-compatible persistence and freshness design

### Old plans and original snapshots

Add optional expense fields through compatible constructors; old constructors delegate to legacy escalation/disabled variability. Preserve absence rather than materializing new defaults in old JSON. Derive effective legacy policy at execution. Explicit zero volatility should normalize to the omitted disabled representation without modifying pre-existing financial decimal fields/scales.

This matters beyond deserialization: `CompleteProjectionSnapshot.freeze/matches` uses serialized input-document digests. `MonteCarloPlanDocument` freezes plan JSON; integrated `AnalysisFingerprintService` includes the financial plan tree; `MonteCarloFingerprintService` verifies stored V1 before its explicit numeric V2 assessment. Default getters serialized automatically could mark unchanged old accepted results stale or fail swap freshness even though the new options are off.

Require byte/digest and exact seeded-output tests before accepting migration. Never rewrite saved input documents, component digests, original result timestamps, UUID associations or historical PDFs. Legacy baselines/results retain original data; original annual results need no rebuilding. Explain legacy volatility as “not modeled in this run” where the historical model contract establishes that fact; do not manufacture recorded input values.

### New assumptions and result versions

- Explicit escalation changes are calculation-relevant inputs for standard projections and all full-plan integrated analyses, not Social Security Only.
- Expense volatility/model/correspondence identity are effective Monte Carlo inputs. Both sides must participate in comparison fingerprinting, original-context PDFs and restoration verification.
- Volatility alone does not change deterministic output. Recommend classifying it as simulation-only for deterministic analyzer applicability, while retaining it in frozen configured documents. This requires an explicit, narrowly reviewed fingerprint classification/compatibility change; do not globally remove financial expense fields.
- Existing exact V1 validation must remain valid for original saved artifacts. Preserve the current temporary numeric V2 policy; do not redefine its old meaning to hide a mismatch. Unknown new expense-model versions must be unavailable, never silently evaluated under a guessed model.
- `MonteCarloSnapshot.Provenance` currently records returns, mortality, inflation, streams, aggregation and supplied-world identity, but no expense model/correspondence version. New enabled results need explicit versioned expense provenance and schema/codec validation. Recommend a new supported Monte Carlo payload version for enabled features, retaining original V1 decoding and disabled legacy encoding where applicable. Define that versioned extension before production work; do not just change a global algorithm tag and make every old result unavailable.
- `AnalysisVersions` currently supplies fixed projection/Monte Carlo/stream versions. Future model capability checks must be feature-aware so unchanged legacy calculations keep their old compatibility, while new enabled calculations cannot masquerade as old ones.

No broad root-plan migration is recommended. Optional per-expense fields and a bounded versioned analyzer payload extension are the proposed boundary. Backward compatibility means the new application reads old files unchanged. Older binaries can ignore unknown expense properties because Expense currently ignores unknown fields; they cannot be promised to execute new policies correctly. Clearly document that new-feature plans require a supporting application. No audit claim of forward compatibility.

## 9. UI, result and PDF disclosures

- ExpenseDialog/ExpensesWizardStep: expose the approved escalation control and **Annual expense variability (Monte Carlo only)**, default disabled. Explain percentage semantics, independence, non-persistence of annual shocks, opening-year base and existing partial-year treatment. No second inflation rate field.
- ExpensesView: show effective escalation and enabled variability without relying on color alone; edit/cancel must preserve all fields. One-time controls remain excluded.
- BaselineParameterComparison: classify new fields under Expenses, distinguish legacy policy from absent historical data and preserve conservative collection matching. Its comparison matching must not be repurposed silently as the RNG matching protocol.
- Monte Carlo parameter/results assumptions: freeze and display the original expense policies, variability percentages, independence/model version and paired correspondence basis. The deterministic reference explicitly excludes expense shocks.
- `MonteCarloPdfReportAdapter`, `MonteCarloHistoricalPdf`, snapshot presentation/mapping and `MonteCarloPdfExporter`: original frozen plan settings, optional-feature/model disclosure and comparison pairing limits. Do not read current editable expenses for a historical export. No need to add millions of raw shock rows or redesign financial PDF charts.
- Integrated/standard reports: disclose effective escalation; deterministic reports may state configured Monte Carlo variability is not used. SS Only does not claim to model expenses.
- Add to the existing applicable Phase 13G Monte Carlo introduction limitations only after actual implementation; keep their six stable IDs and suppression settings.

## 10. Affected-class inventory for future implementation

| Layer | Existing classes/resources to inspect/modify narrowly |
|---|---|
| Expense domain | Expense, GrowthCategory/ExpenseType compatibility, Household edit/copy paths; proposed ExpenseEscalationPolicy |
| Projection | ProjectionEngine expense helpers and recursive-year plumbing; ProjectionEvaluationContext and all with* builders; proposed ProjectionExpensePath |
| Random generation | MonteCarloRandomStreams (new dimension/helper only), proposed MonteCarloExpenseGenerator; keep ScenarioGenerator and InflationGenerator old protocols unchanged |
| Execution | MonteCarloAnalyzer fixed/longevity paths; MonteCarloWorldGenerator/World; MonteCarloComparisonWorld, StrategyComparisonRequest.worldSource and StrategyComparisonAnalyzer; supplied-world validation |
| Frozen contexts | RetirementPlanScenarioCopyService, RetirementPlanSnapshot, CompleteProjectionSnapshot; preserve original documents/digests and boundaries |
| Plan persistence | JsonRetirementPlanRepository/Jackson expense loading and optional serialization; do not expand unrelated root schema |
| Analyzer artifacts | MonteCarloSnapshot, MonteCarloSnapshotMapping/PresentationMapper, MonteCarloPlanDocument, snapshot codec/repository bounds and supported-version checks |
| Freshness | AnalysisFingerprintService, MonteCarloFingerprintService, AnalysisVersions; existing accepted-parameter bindings and 15 identities remain unchanged unless separately justified |
| UI | ExpenseDialog, ExpensesView, ExpensesWizardStep, BaselineParameterComparison and assumption labels; existing theme CSS only if required for scoped controls |
| Reporting | MonteCarloPdfReportAdapter, MonteCarloHistoricalPdf, MonteCarloPdfReport/Exporter and relevant assumption sections of integrated/standard exporters |

These are candidate integration points, not authorization to modify every file. Reuse existing result values and request codecs; no duplicated large plan/result graphs, screenshots or JavaFX objects in persisted snapshots.

## 11. Risks and storage/performance bounds

| Risk | Required mitigation |
|---|---|
| Existing Healthcare expense accidentally switches/loses inflation | Preserve legacy category and require an explicit UI policy decision |
| Inflation counted twice | Resolve one escalation source and apply it once; expense level shock is a separate mean-one factor |
| Same-year financial rerun draws a different expense | Precompute/key once per expense/year and reuse immutable values |
| Disabled feature changes old outputs/digests | No RNG/path/multiplication on disabled branch; omit legacy defaults; exact scale/hash/golden assertions |
| Adding/removing an expense shifts market or mortality draws | Separate new dimension and keyed expense/year streams |
| Paired sides receive unmatched/randomly shifted shocks | Shared world, documented financial-definition key and duplicate protocol; no name-only matching |
| Positive lognormal model hides negative legacy inputs or extreme tails | Preserve disabled legacy behavior; reject unsupported enabled cases; explicit bounds/nonfinite errors; no silent clipping |
| New fields alter SS-only freshness | Preserve family-specific dependencies; full-plan versus benefit-only classification tests |
| Legacy artifacts become unavailable from global version bump | Original contract verification and explicit new feature/provenance support; no snapshot rewrite |
| Large expense-path allocation | Bound expense count × horizon, generate per scenario and release after reduction; do not retain all N×E×Y shock paths |
| False historical association | Projection UUID/ownership rules unchanged; sampling keys are not historical identity |

For E=50 expenses and Y=60 years, a dense path is 3,000 multipliers per scenario; raw 16-byte numeric payload alone would be about 48 KB, with real BigDecimal/maps/keys using materially more. Retaining 10,000 such paths would mean 30 million values and substantial memory. This is a design estimate, not a measured benchmark. Prefer sequential per-scenario materialization or immutable deterministic keyed lookup with request-local memoization; neither may resample on financial retry. Preserve current shared component/storage limits and reject excessive requests explicitly rather than silently dropping results. Existing frozen plan text is bounded at 256,000 characters; new fields consume that existing budget.

## 12. Proposed tests

1. Legacy GENERAL/HEALTHCARE/ONE_TIME load/save and exactly unchanged expense amounts; no omitted-default field inflation of old hashes.
2. General on/off and Healthcare policies with independently calculated compound values; future-start opening-year base; inactive expenses, leap/calendar boundaries, partial first year, post-death factor and all-deceased omission.
3. Complete input/snapshot/Save As/restart round trips; legacy digest/freshness and immutable original charts/assumptions; no snapshot migration or recomputation.
4. v=0 exact seeded Monte Carlo goldens, returned BigDecimal scales and unchanged market/mortality/inflation byte-stream vectors; no generator invocation for disabled features.
5. Deterministic and longevity-weighted analyzers with nonzero configured volatility: throwing RNG proves no stochastic sampling; unchanged results versus the same deterministic assumptions.
6. Independently calculated normal-to-lognormal multipliers (including Z=0), exact expense application, zero amounts, invalid volatility/nonpositive enabled amounts and nonfinite paths. Fixed-seed distribution diagnostics for mean/variance/tails with statistical tolerances, not exact sample mean=1 assertions.
7. Repeated/recursive Medicare-year evaluations reuse identical expense multipliers. Verify downstream withdrawals, tax-funding feedback, Roth bracket fill, cash settlement, next-year RMD balances and estate metrics from the same shocked annual budget.
8. Repeatability across scenario order/count, horizon extension, expense reordering, inactivity, canceled jobs and independent runs; no process/global RNG state.
9. Paired identical plans yield exact zero differences under shared shocks; matching expenses share Z, duplicates are independent but paired consistently, extra unmatched expenses and changed definitions follow the documented policy. Different horizons retain common-year shocks.
10. All eight Monte Carlo variants, both household shapes, zero-opening-lifetime outcomes, partial funding failures and supplied-world provenance/coverage failures.
11. New/old fingerprint and artifact versions, corrupted/oversized components, disabled defaults, changed inflation policy, simulation-only volatility, original-context PDF disclosures and unavailable unsupported model versions.
12. UI edit/cancel/wizard validation, Light/Dark, keyboard accessibility, percentage formatting, legacy Healthcare explanation and explicit labels. No financial analysis, plan mutation or preferences write from merely viewing assumptions.

Extend existing fixtures rather than regenerating goldens. Relevant classes include ProjectionEngineTest, ProjectionInflationPathTest, SurvivorFinancialMechanicsTest, MonteCarloFoundationTest, MonteCarloRandomStreamsTest, MonteCarloInflationRegressionTest, MonteCarloPairedWorldTest, MonteCarloStrategyComparisonTest, MonteCarloNumericCompatibilityTest, MonteCarloSnapshotCodecTest, AnalysisFingerprintTest, ExpensesWizardStepTest and historical PDF/fingerprint/association tests.

## 13. Audit verification actually performed

Existing focused Maven tests: **189 total, 187 passed, zero failures/errors, two skipped**, BUILD SUCCESS in 21.599 seconds, completed 2026-10-29 09:46:06 America/New_York. Log: `target/phase14/focused.log`. Used IntelliJ bundled Maven, project-local `.codex-m2/repository` and 4 GiB test heap.

Classes run: ProjectionEngineTest, ProjectionInflationPathTest, SurvivorFinancialMechanicsTest, MonteCarloRandomStreamsTest, MonteCarloInflationRegressionTest, MonteCarloFoundationTest, MonteCarloPairedWorldTest, MonteCarloStrategyComparisonTest, MonteCarloNumericCompatibilityTest, AnalysisFingerprintTest, ExpensesWizardStepTest and MonteCarloSnapshotCodecTest. The two existing skipped codec cases require `mc.codec.capacity=true` and `mc.codec.replay=true`; no test was disabled or weakened.

The complete suite, opt-in capacity/replay proofs, native UI interaction and new-model statistical/performance experiments were **not** run for this architecture-only audit. No implementation exists to validate; the focused tests substantiate the existing baseline, not the proposed feature. No generated golden or source changes. Full regression, capacity checks and native acceptance are required after an approved implementation.

## 14. Recommended implementation sequence

1. **Approve policies and compatibility contract first:** Healthcare/checkbox semantics, defaults, variability bounds and distribution, paired matching, optional-field serialization and artifact/fingerprint capability rules.
2. **14A — Deterministic expense escalation:** focused domain tests first, compatible optional policy, editor/wizard integration, existing economic rate reuse and explicit historical-digest regression. No stochastic paths yet.
3. **14B1 — Pure sampling protocol:** freeze new keyed dimension, distribution transforms and immutable path; unit/vector/statistical tests and zero-feature invariance. No engine-side RNG.
4. **14B2 — Full financial integration:** pass paths through all context/world builders and Monte Carlo variants; validate tax/Roth/RMD/cash feedback and same-year retry reuse; preserve deterministic references.
5. **14B3 — Persistence/freshness/reporting:** versioned enabled-result provenance, old exact decoding, original-context disclosures, paired supplied-world handling, ownership and storage limits. This must complete before the feature is offered as durable historical analysis.
6. **Integration acceptance:** focused then complete Maven suite, unchanged old financial/seeded goldens, new exact fixtures, per-scenario memory/performance checks and Windows UI/PDF verification. Stop at any discovered financial-contract conflict rather than editing old expected values.

## 15. Explicit approval decisions

1. **Inflation UI and Healthcare:** approve three explicit General/Healthcare/None states and how the requested checkbox represents Healthcare; a plain boolean with a silent migration is not safe.
2. **Defaults:** approve General enabled for new recurring expenses; legacy category escalation retained exactly; zero/absent stochastic variability disabled. Decide whether to expose opting Healthcare out as None in the same editor.
3. **Stochastic policy:** approve independent annual level shocks, mean-one lognormal arithmetic relative-SD percentages, no cross-expense/common macro component initially, partial-year treatment, proposed 0–100% bound, no clipping and enabled negative-amount rejection.
4. **Pairing:** approve strict effective-definition/duplicate-occurrence correspondence and its limits when expenses are edited; otherwise approve a separate durable-ID/correspondence design. No inference from names or projection fingerprint equality.
5. **Persistence and freshness:** approve optional expense fields with absent-default preservation, explicit enabled-model artifact provenance/versioning, family-aware fingerprint classification and original-contract compatibility tests. No blanket global invalidation or historical rewrite.

Only this report was added. The pre-existing IntelliJ encoding change is preserved. No production/test/CSS/persistence implementation, financial calculation, schema, golden fixture, stage, commit or push operation was performed. Stop here for architecture review; implementation requires explicit authorization.

Final `git diff --check` passed. HEAD remains 7ee0365b3ce59cdcfb18c9ac161a2b2e49aad57d and the staging index is empty. Final working-tree inventory: this new report plus the unchanged pre-existing `.idea/encodings.xml` modification.
