# Phase 14B2 - Stochastic Recurring Expense Financial Integration

## 1. Executive summary

Implemented run-only expense-path integration into the full annual financial engine and package-scoped Monte Carlo execution for fixed/longevity, couple/single and paired analyses. Expected recurring spending is escalated first, then multiplied by the immutable scenario/year shock before existing proration and death adjustments. The existing withdrawal, tax, RMD, Roth, Medicare and settlement calculations consume that adjusted annual spending.

**Enabled execution remains internal-only.** There is no volatility UI, persisted Expense field, menu path, supported historical-result conversion or ordinary PDF export. Enabled caller-supplied worlds are rejected until Phase 14B3 can establish their provenance contract. Existing public Monte Carlo APIs remain expense-disabled. No random generator executes inside ProjectionEngine.

Final focused verification: **257 passed, zero failures/errors/skips**. All 30 new financial integration cases and the explicit opt-in benchmark passed. Complete regression: **3,755 total, 3,709 passed, 46 skipped, zero failures/errors**. Phase 14B2 is ready for review; Phase 14B3 remains deferred.

## 2. Baseline and actual scope

- Branch `main`, initial HEAD `29863debd1e43451e5b5616d049583e97457c031`; staging index empty.
- Pre-existing `.idea/encodings.xml` modification and untracked `PHASE-14-RECURRING-EXPENSE-STOCHASTIC-ARCHITECTURE.md` are preserved and excluded.
- Read AGENTS.md and Phase 14 architecture/14A/14B1 reports; inspected current source rather than assuming the proposed integration already existed.
- Verified the B1 specification, generator, immutable paths, dimension 6, canonical financial-definition/duplicate protocol and exact seeded vectors are present.
- Retained the existing sampler unchanged. No market, inflation, mortality, tax, Roth, RMD, Medicare, withdrawal or comparison formula was changed. No dependency, plan/snapshot schema, financial fingerprint, historical payload, algorithm version or financial golden resource changed.
- Inspected actual generated/supplied standalone and paired execution, snapshot mapping and PDF adapter parameter types before selecting the internal boundary.

## 3. Execution contracts and isolation

### InternalExpenseRun

A package-private immutable run configuration, created by `capture(plan, volatilities)`, with one decimal/null volatility per exact expense-list occurrence. Null/zero is disabled, following B1. It captures detached exact financial definitions, then prepares the original B1 sampler only if at least one expense is enabled.

The input list must cover all frozen expenses. Exact type, amount **including scale**, dates and escalation must still match the isolated run plan. No description, live UI node, plan graph, cache, mutable RNG or projection association is retained in this configuration. The existing analyzers still perform their established full-plan isolation once per run.

`apply` validates that expense configuration, generates one B1 path using the master seed, exact scenario index and requested absolute years, binds it to the frozen expense list, and returns a new evaluation context. Disabled configuration returns the original context without a sampler/path or multiplication.

This run-only configuration is not a new financial fingerprint or a persisted field. It is intentionally inaccessible from application menus and public analyzer entry points.

### InternalExpenseResults

Distinct package-private Fixed, Longevity and Comparison wrapper types hold the existing reduced results privately. They expose limited package-scoped observation/summary methods for internal tests and measurement, **no raw legacy-result accessor**, and no public construction, snapshot codec or PDF adapter. Existing snapshot/PDF APIs cannot accept these types; reflection regression tests verify the API boundary. Existing reducers and metrics are reused instead of introducing parallel financial summary logic.

Temporary legacy-shaped reduction objects stay inside the private execution/wrapper boundary. They are never published to the ordinary analyzer session, accepted-result slots, history repository or PDF pipeline. Existing model/version names retain their original supported meanings; no enabled public result is advertised under those contracts.

## 4. Expense-path binding and identity

The B1 sampling protocol is unchanged: full financial-definition SHA-256 key plus first-seen duplicate ordinal, seed/scenario and absolute year. Numeric normalization remains sampling-only; no plan digest is normalized.

`ProjectionExpensePath.bind(expenses, bindings)` adds immutable, detached financial definitions and input-list-to-occurrence bindings to a path. It shares the already immutable annual multiplier data rather than copying a second large value dataset. Enabled bindings must be distinct, nonnegative recurring expenses, and their occurrence set must equal the path's multiplier set. Missing, extra or one-time enabled bindings fail explicitly.

ProjectionEngine validates the bound exact financial expense list and all required years before yearly financial execution. Unbound B1 paths remain useful sampling values but cannot be applied silently to an arbitrary plan. Binding positions address a validated frozen input list; they are not sampling identities. Reordering expenses requires a new correct binding but never changes the B1 draw for a unique definition.

Duplicate occurrences remain independently sampled and consistently paired by B1's first-seen ordinal. Names alone do not establish correspondence. A changed amount or volatility is a different sampling definition. Sampling keys do not replace UUIDs, ownership, history or financial fingerprints.

## 5. Evaluation context and annual financial flow

`ProjectionEvaluationContext` gains Optional<ProjectionExpensePath>. All prior constructor arities remain source-compatible, including the original six-component constructor, and default to empty. Existing inflation, exact/extension horizon and survivor-age builders preserve the path. Static factories create fresh contexts as before; callers can attach an explicit path afterward. Tests exercise all 24 orderings of the four existing instance builders.

No context/path was added to RetirementPlan or its persistence roots. Ordinary deterministic execution has an empty expense path.

ProjectionEngine's calculation sequence is:

1. Existing active-calendar-year test and all-household-deceased recurring exclusion.
2. Existing General, Healthcare or None expected-spending escalation.
3. If there is an enabled bound occurrence, read its fixed absolute-year multiplier.
4. Multiply nonzero expected spending using BigDecimal; an exact zero is left unchanged.
5. Existing opening-year recurring proration and cents rounding.
6. Existing survivor/death factor, household aggregation and rounding.

Inactive expenses and one-time purchases keep their existing behavior. Medicare premiums and taxes are not sampled; only explicitly bound eligible recurring expenses receive multipliers. With no path, the prior arithmetic remains intact, including BigDecimal scales. No multiplying by one, alternate escalation rate, stochastic inflation change or new calendar/day-proration rule is introduced.

Adjusted annual expenses enter the original cash-flow need and all subsequent calculations. They are not patched into output summaries after a simulation. Expenses are not treated as directly tax-deductible.

## 6. Medicare retry and settlement consistency

The same ProjectionExpensePath reference is passed through both calculateProjectionYear overloads and the existing authoritative-Medicare recursive reevaluation. The yearly lookup is pure and returns the same immutable multiplier. No resampling or RNG call occurs during tax funding, withdrawal allocation, Roth bracket fill or settlement.

A positive-Medicare synthetic test applies a factor of two to a $1,000 expense, verifies $2,000 (not $4,000) is reported/funded after retry, checks cash need equals spending plus independently computed Medicare, repeats execution, and compares every serialized annual result with a normal projection whose fixed budget was explicitly set to $2,000. Tax/Roth tests likewise compare every annual financial value with an equivalent fixed-budget oracle.

Existing financial reconciliation contracts are preserved: tax-funding feedback permits a two-cent residual, reported aggregate ending assets round to cents, while account-level balances carried into the next year retain their original precision. New tests use that established contract and also assert **exact** carry-forward from original account snapshots plus retained assets. No rule was changed to force a stricter synthetic equality.

## 7. Standalone Monte Carlo integration

Package-scoped `analyzeInternalExpenses` and `analyzeMortalityInternalExpenses` return only the unsupported internal wrapper types. They reuse the existing private execution loops, plan copying, deterministic reference, generated markets/inflation/lifetimes, outcomes, percentile reductions, cancellation and error handling.

- Fixed runs generate a path for each exact scenario index and configured annual horizon.
- Longevity runs use the original sampled lifetime and exact final-living-year horizon. Opening-date terminal outcomes retain the original no-annual-projection/no-expense-path branch.
- Couple survivor elections and individual absence of spouse retain existing validation.
- The deterministic reference is still evaluated without an expense path.
- Paths are local to one scenario and discarded after full financial results are reduced. They are not retained in final results.

Existing public standalone methods pass no expense configuration and cannot enable this feature.

## 8. Paired comparisons

Package-scoped comparison execution accepts separate frozen configurations for A and B, validates each against its isolated candidate, and generates each side's B1 path with the same master seed/scenario and its required years. Matching definitions/duplicates therefore share shocks; changed definitions receive different keyed shocks.

The existing common market, inflation and lifetime world is preserved, including the same market-path object being passed to both financial evaluations. Tests verify identical plans have identical expense-adjusted outcomes in fixed/longevity and couple/single modes, plus independent direct-engine oracles for financially different candidates. Cancellation between A and B still publishes no partial pair.

The established paired financial contract uses one comparison horizon (or shared sampled lifetime), not independent side-specific ending years. It was not changed. Different standalone/path horizons retain identical common-calendar-year shocks; tests exercise 30-year versus 60-year coverage without inventing financial rows beyond a requested execution horizon.

## 9. Supplied-world gate

No enabled internal entry point accepts caller-supplied worlds or market paths. Private execution boundaries additionally reject a supplied source if enabled configuration reaches them. Existing ordinary supplied-world overloads remain expense-disabled and keep their previous validation/caller-provenance contracts.

Reflection tests deliberately attempt enabled calls through all three private boundaries (fixed market paths, longevity worlds, paired worlds). Each fails with UnsupportedOperationException **before the supplier or projection can execute**. This rejects incomplete/mismatched/unversioned input wholesale; it does not guess missing paths or synthesize unrelated shocks.

Per section 9's authorized fallback, compatible enabled supplied-world coverage/scenario/model/protocol/provenance validation remains unavailable until Phase 14B3. No persisted provenance format was invented here. This is a closed execution gate, not weaker validation or a silently accepted partial world.

## 10. Disabled-path and historical compatibility

- Every public analyzer entry is still expense-disabled. No UI control or settings/Expense field enables the internal API.
- Internal null/zero volatility generates no expense path or RNG and makes no multiplier arithmetic change.
- Tests compare disabled internal versus original outputs across all eight standalone/paired, fixed/longevity, couple/single identities, including deterministic references and annual aggregates.
- Existing seeded financial regression hashes, market/inflation/mortality stream vectors, B1 exact expense vectors and numerical fingerprint contracts pass focused verification without regeneration.
- Existing expense JSON/input digests and saved projection restoration tests pass, including complete historical values restored without engine execution.
- Projection snapshots, analyzer associations, original historical results, plan dirty state and original PDFs have no new integration path or persisted fields.
- Snapshot/PDF conversion requires the existing supported types; internal wrappers cannot be passed or cast to them. No historical repository, exporter or artifact version was changed.

## 11. Financial integration coverage

New `StochasticExpenseProjectionTest` (12 invocations):

- Independently expected General/Healthcare/None values with 2x, 3x and 0.5x annual level shocks and an opening July partial year.
- Future starts, end-before-horizon, calendar overlap, 0.75 survivor factor, all-household death, untouched one-time purchase, and zero spending under a very large multiplier.
- $1,000,000 zero-return brokerage, $10,000 annual spending, first-year factor 2: $20,000 cash need/withdrawal, $980,000 first ending assets/estate, $970,000 second ending assets/net worth. No tax deduction or account mutation.
- Fixed-year Medicare retry and repeatability; full annual fixed-budget equivalence.
- IRA spending, tax-funding feedback, spending-plus-tax withdrawals, lower available Roth bracket-fill conversion and complete financial oracle equality.
- Unchanged current statutory RMD, lower next-year RMD from shocked prior balances, exact raw balance carry-forward, lower after-tax estate and unchanged persisted account balance.
- Explicit structured $1,200,000 need / $1,000,000 available / $200,000 shortfall.
- Missing coverage, stale financial definition, unbound path, missing binding and one-time binding refusal.
- Context builder permutations and legacy constructor defaults.

New `StochasticExpenseMonteCarloTest` (18 invocations):

- Four standalone variants: fixed/longevity, couple/single, repeatability and reverse-order per-scenario direct-engine oracles; unchanged deterministic reference.
- Four paired variants with duplicate definitions: identical-plan invariance and repeatability.
- Four disabled cases comparing all eight original execution identities exactly.
- Internal API/snapshot/PDF type boundary and supplied-world rejection before execution.
- Frozen-input mismatch, standalone cancellation, structured funding failures.
- Matched shocks after unrelated addition, reordering and horizon extension.
- Financially different paired side oracles, unchanged shared market-world identity.
- Paired cancellation between sides and software-error stage/scenario attribution.

The core integration run passed all 30 cases. Statistical/generator tests are the unchanged B1 suite. Existing financial settlement, RMD, Medicare, Roth, snapshot and fingerprint tests are also in the final focused selection.

## 12. Performance and memory measurements

Opt-in `-Dexpense.financial.benchmark=true -Dtest=StochasticExpenseProjectionTest,StochasticExpenseMonteCarloTest,StochasticExpenseFinancialBenchmarkTest`; 4 GiB heap. Synthetic couple plans, 1,000 scenarios, seed 417, return volatility 12%, configured 30-year horizon, $10,000 total recurring spending split over 10 or 50 entries. Enabled expense volatility 25%, disabled 0%; other settings identical. Longevity preserves its true sampled horizon: 26,342 annual rows across this 1,000-world sample, rather than imposing a new 30-year cap.

| Expenses | Mode | Disabled seconds | Enabled seconds | Separate enabled sampling + binding seconds |
|---:|---|---:|---:|---:|
| 10 | Fixed | 3.942 | 3.310 | 0.068 |
| 10 | Longevity | 3.021 | 2.716 | 0.050 |
| 50 | Fixed | 3.397 | 3.872 | 0.257 |
| 50 | Longevity | 2.809 | 3.233 | 0.247 |

At 50 expenses, observed enabled elapsed costs were about 14-15% higher; the isolated sampling/binding cost was about 0.25 seconds per 1,000 scenarios. Ten-expense enabled runs being faster is ordinary warm-up/workload noise, not a claimed speed improvement. Enabled spending changes actual withdrawal/tax branches, so total elapsed differences are not a pure sampler overhead estimate. Measurements are informational single-run comparisons without brittle timing assertions or incompatible optimizations.

| Expenses / mode | Disabled / enabled summed heap-pool peak MiB | Disabled / enabled retained-result heap delta MiB |
|---|---|---|
| 10 Fixed | 260.84 / 299.88 | 1.90 / 1.03 |
| 10 Longevity | 299.68 / 265.76 | 3.10 / 3.08 |
| 50 Fixed | 303.99 / 262.18 | 0.99 / 1.02 |
| 50 Longevity | 328.18 / 305.98 | 3.05 / 3.08 |

Each enabled case generated 1,000 scenario paths; disabled cases generated zero. All enabled cases observed **zero live path weak references after GC**, while retaining the reduced result. This is an observation, not a guarantee of GC timing. Retained-result deltas include normal outcomes/aggregates, and summed pool peaks include garbage and non-simultaneous high-water marks. No full scenario-path collection is retained in production; benchmark references are weak only. Memory remains bounded by B1's per-scenario path limits plus the pre-existing result reductions.

## 13. Verification results

IntelliJ bundled Maven, project-local `.codex-m2/repository`, `-DargLine=-Xmx4g`.

| Run | Total | Passed | Skipped | Failures | Errors | Result |
|---|---:|---:|---:|---:|---:|---|
| Existing focused baseline | 79 | 79 | 0 | 0 | 0 | Success, 14.833 s |
| Pre-implementation new tests | Not executed | | | | | Expected test compilation failure: missing bind/withExpensePath APIs |
| First engine checks | 13 | 13 | 0 | 0 | 0 | Success, 16.292 s |
| New financial fixture development | 11 | 8 | 0 | 1 | 2 | New fixture assumptions corrected; see below |
| First combined integration checks | 26 | 23 | 0 | 3 | 0 | New rounding/reflection expectations corrected |
| Combined integration recheck | 26 | 26 | 0 | 0 | 0 | Success, 12.314 s |
| Broad focused checks | 255 | 255 | 0 | 0 | 0 | Success, 20.368 s |
| Final new tests + explicit benchmark | 31 | 31 | 0 | 0 | 0 | Success, 45.662 s |
| Final focused checks | 257 | 257 | 0 | 0 | 0 | Success, 20.488 s |
| Complete Maven regression | 3755 | 3709 | 46 | 0 | 0 | Success, 10:26 min |

New-test development corrections were local test issues, not changes to financial semantics: supplying the required advanced Social Security fixture for lifetime execution; using the RMD **distribution** year rather than prior-balance year; respecting established two-cent tax settlement and rounded aggregate versus precise account balances; excluding inherited Object.equals from API-adapter reflection checks. A preliminary test also used a nonexistent ProjectionYear net-worth getter; it was replaced with the existing metrics calculator. No existing assertion, golden or financial rule was weakened or changed.

Logs under `target/phase14b2`: `baseline.log`, `before.log`, `engine-first.log`, `engine.log`, `financial.log`, `integration.log`, `integration-final.log`, `focused.log`, `benchmark.log`, `focused-final.log`, `full.log`.

Final focused selection: StochasticExpenseProjectionTest, StochasticExpenseMonteCarloTest, ExpenseEscalationTest, ExpenseEscalationCompatibilityTest, MonteCarloExpenseGeneratorTest, MonteCarloRandomStreamsTest, ProjectionInflationPathTest, MonteCarloInflationTest, MonteCarloInflationRegressionTest, MonteCarloFoundationTest, MonteCarloWorldGeneratorTest, MonteCarloPairedWorldTest, MonteCarloStrategyComparisonTest, MonteCarloNumericCompatibilityTest, ProjectionEngineTest, HouseholdCashSettlementProjectionTest, MedicareCashFlowProjectionTest, RothConversionProjectionTest, OpeningRmdProjectionTest, ProjectionSnapshotPersistenceTest and AnalysisFingerprintTest.

The complete suite had no intermittent failures. All existing golden, seeded, snapshot, fingerprint, baseline, analyzer, PDF and UI regression assertions passed unchanged. No test exclusions were applied. The 46 skips consist of 41 property-gated optional performance/capacity/user-plan/visual cases (including the B1 sampling and B2 financial benchmarks), one existing disabled multi-minute production mortality benchmark and four existing worksheet parameter cases awaiting authoritative guidance. The B2 benchmark was separately executed successfully. Exact skipped case names/reasons are in `target/phase14b2/skipped-tests.txt`.

No new UI previews are required or claimed. There are no UI changes. Final `git diff --check` passed. Branch and HEAD remain unchanged and the staging index is empty. The only pre-existing working-tree entries remain preserved as recorded above.

## 14. Exact implementation inventory

Modified production:

1. `src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEvaluationContext.java` - optional path, compatible constructors, preserving builders.
2. `src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionExpensePath.java` - immutable validated financial binding.
3. `src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java` - binding/coverage validation, multiplier application and retry plumbing only.
4. `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloAnalyzer.java` - package-scoped wrapped execution and optional private-loop configuration.
5. `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonAnalyzer.java` - package-scoped wrapped paired execution and side-specific configuration.

New production:

6. `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/InternalExpenseRun.java`.
7. `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/InternalExpenseResults.java`.

New tests:

8. `src/test/java/com/daviddunn/retirementplanner/domain/projection/StochasticExpenseProjectionTest.java`.
9. `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/StochasticExpenseMonteCarloTest.java`.
10. `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/StochasticExpenseFinancialBenchmarkTest.java`.

New report:

11. `PHASE-14B2-STOCHASTIC-EXPENSE-FINANCIAL-INTEGRATION.md`.

No existing test file was modified. Excluded/preserved: `.idea/encodings.xml` and the preceding untracked Phase 14 architecture report. Ignored `target/phase14b2` diagnostics are not intended commit files. No staging, committing, pushing, resetting, cleaning or discarding occurred.

## 15. Phase 14B3 requirements and limitations

- Define enabled public request/result provenance, compatible fingerprint semantics, explicit artifact/model capability/version checks and original-context PDF disclosures before exposing this feature.
- Add optional persisted volatility/default omission only under that compatibility design; old expense inputs/digests and historical results must remain unchanged.
- Establish enabled supplied-world identity, coverage and version validation before opening the current fail-closed gate. Do not silently generate missing shocks alongside a supplied world.
- Replace the unsupported internal wrapper boundary with a deliberately supported result contract only after persistence/export safety is proven. No automatic promotion or historical rewrite is authorized.
- Preserve deterministic/reference exclusion and the exact disabled branch; expand public admission/configuration tests only after those request contracts exist.
- Existing paired financial comparisons retain their common-horizon methodology. Distinct path horizons share shocks, but independent financial side horizons are not a new supported comparison mode.
- Future exposure must explain annual level variability and duplicate correspondence limitations. No persistent-shock model, expense UUID migration, correlation model, execution change to deterministic analyzers or UI is included here.
- Single-run timing and GC observations are not performance certification; no native Windows UI acceptance claim is needed for this internal-only phase.

No additional financial or historical compatibility policy change was needed. Stop after Phase 14B2 for review and explicit approval before Phase 14B3.
