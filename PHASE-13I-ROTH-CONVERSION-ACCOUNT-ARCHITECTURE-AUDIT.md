# Phase 13I — Roth Conversion Account Eligibility and Destination Architecture Audit

Audit date: October 30, 2026. **Audit only; no implementation authorized by this report.**

## 1. Executive summary

The current engine requires an existing same-owner account in the broad `ProjectionAssetType.ROTH` category. It does not create a destination. The production household converter skips an owner without a destination and converts zero if neither owner has one. The older single-owner converter throws instead. A saved conversion schedule can therefore exist without an executable conversion.

Three verified inconsistencies require financial-policy approval before correction:

1. `RolloverIRA` inherits `Account.isEligibleForRothConversion() == false`, although a supported pre-tax rollover IRA is an ordinary potential conversion source.
2. `Roth401K.isEligibleForRothConversion()` returns true. Its balance is treated as a fully taxable conversion source, although moving designated Roth funds to a Roth IRA is a different rollover transaction requiring different tax treatment. A Roth 401(k) may even be selected as its own destination.
3. Destination selection accepts Roth IRA, Roth 401(k), and inherited Roth IRA indiscriminately because all map to `ROTH`. A taxable conversion to an individual's Roth IRA must not be routed to an inherited IRA or assumed to be an eligible in-plan Roth transaction.

Existing UI screens enable scheduling from the checkbox/strategy state, without an account eligibility check. Employer-plan distribution availability is not checked by conversion execution. Existing employer participant facts support state-tax qualification but do not establish legal rollover availability.

**Preferred destination design: Option B, a visible, persisted, zero-opening-balance modeled Roth IRA, created only as an explicit, disclosed part of applying conversion configuration.** Reuse a valid existing same-owner Roth IRA first. Do not mutate the plan during projection, load, Save, history restoration, or analysis. Option A is a safe simpler interim design; Option C adds avoidable input/result/account-identity divergence. None is implemented here.

Automatic destinations would enable conversions that currently execute as zero and therefore change financial outcomes. Source/destination corrections also affect outcomes. These are financial changes requiring approval, not merely UI conveniences. Historical accepted results must remain untouched.

## 2. Scope, Git baseline and evidence

Branch `main`; HEAD `dd77e8388f00628cb8a0b2d9242563bfb77d5a4e`; staging index empty at entry. The earlier Assumptions layout work is now in HEAD. Existing uncommitted Phase 14B3A, startup, and IDE changes were preserved.

This audit inspected domain types, both conversion APIs, projection ordering, UI entry points, tax adapters, snapshots, fingerprints, account editing and representative consumers/tests. Findings explicitly described as verified refer to source inspection or the tests below. Recommendations, future test cases and illustrative arithmetic are not implemented behavior or new executed tests.

Only this report is a new intended source-controlled deliverable. Test logs under ignored `target/roth-architecture-audit/` are generated artifacts. No user plan documents or settings were opened for mutation. No production/test code, financial goldens, dependencies or schemas were changed.

## 3. Implemented account inventory and eligibility matrix

`domain.model.AccountType` has exactly eleven values; `domain.financial.AccountFactory.getSupportedTypes()` exposes all eleven. Jackson subtype tags live on `Account`. `AccountDialog` uses the factory and has a separate inherited-account creation path. Retirement accounts reject JOINT ownership; brokerage/checking/savings permit it. PRIMARY/SPOUSE identify the account holder, not the original deceased owner of an inherited IRA.

| Exact enum / concrete class / JSON subtype | Current taxable-conversion source | Legal/model assessment for a Roth IRA conversion | Current destination selection |
|---|---|---|---|
| `TRADITIONAL_IRA` / `TraditionalIRA` / `traditional` | Yes | Potential source; account basis can make part nontaxable, which this model does not track | No |
| `ROLLOVER_IRA` / `RolloverIRA` / `rollover` | **No**, inherited default | Potential source; omission from current support is inconsistent | No |
| `TRADITIONAL_401K` / `Traditional401K` / `traditional401k` | Yes, unconditional account predicate | Only eligible distributable amounts; plan and distribution conditions must be satisfied | No |
| `TRADITIONAL_403B` / `Traditional403B` / `traditional403b` | Yes, unconditional account predicate | Same distribution/rollover qualification issue; cannot assume availability from positive balance | No |
| `ROTH_IRA` / `RothIRA` / `roth` | No | Destination for the owner's conversion; moving existing Roth funds is not a new taxable pre-tax conversion | Yes, correct category but currently not exclusive |
| `ROTH_401K` / `Roth401K` / `roth401k` | **Yes** | May support a designated-Roth rollover to Roth IRA; not the fully taxable conversion modeled here | **Yes**, overbroad predicate |
| `INHERITED_TRADITIONAL_IRA` / `InheritedTraditionalIRA` / `inheritedTraditional` | No | Nonspouse inherited IRA conversion is not supported; spouse elections require separate treatment | No |
| `INHERITED_ROTH_IRA` / `InheritedRothIRA` / `inheritedRoth` | No | Not a destination for this individual's ordinary conversion; inherited rights are separate | **Yes**, overbroad predicate |
| `BROKERAGE` / `BrokerageAccount` / `brokerage` | No | Not a retirement conversion source; may fund taxes under existing withdrawals | No |
| `CHECKING` / `CheckingAccount` / `checking` | No | Not a conversion source; may fund cash requirements | No |
| `SAVINGS` / `SavingsAccount` / `savings` | No | Not a conversion source; may fund cash requirements | No |

There is no implemented traditional governmental 457(b), Roth 403(b)/457(b), SEP/SIMPLE IRA, standalone annuity, or generic pension-account balance subtype. Pensions are income, not convertible account balances. Their legal availability must not be inferred as application support.

### IRS research and boundaries

The [IRS rollover chart](https://www.irs.gov/pub/irs-tege/rollover_chart.pdf) distinguishes pre-tax IRA/qualified-plan/403(b)/governmental-457(b) transfers to Roth IRAs from designated-Roth rollovers and limits in-plan Roth rollovers to their own category. These are not interchangeable conversion destinations or tax events.

[IRS Publication 590-A](https://www.irs.gov/publications/p590a) describes taxable conversion amounts, return of basis, exclusions such as RMDs/hardship distributions from eligible rollovers, and inherited-account restrictions. Nonspouse inherited IRAs cannot be treated as the beneficiary's own account; a surviving-spouse election is a separate case. A nonspouse inherited employer-plan direct rollover into an inherited Roth IRA is a distinct exception, not authority to use an ordinary inherited IRA as a destination here. Basis/pro-rata and employer after-tax splits are outside the existing conversion representation.

The [IRS 401(k) distribution rules](https://www.irs.gov/retirement-plans/plan-participant-employee/401k-resource-guide-plan-participants-general-distribution-rules) describe distribution conditions such as severance and permitted in-service distributions. Employment/separation facts alone do not establish every plan's rules or distribution eligibility; hardship availability does not establish rollover eligibility.

[IRS Publication 590-B](https://www.irs.gov/publications/p590b) distinguishes qualified Roth distributions from conversion recapture and their separate five-year rules. Aledrix has no Roth contribution/conversion-lot/earnings clock or basis ledger. An automatic modeled Roth IRA would inherit that limitation; it would not establish real-world withdrawal qualification.

These sources establish research boundaries, not a claim that Aledrix currently enforces all legal conditions. No new tax-law implementation is proposed as part of the audit.

## 4. Current conversion execution flow

### UI and request

`ui.views.RothConversionView.load`, `updateStrategyFields`, `readValidated`, `save` and `applyChanges` edit a household-level `RothConversionRequest`. `ResultsSummaryView.updateRothControls` and `applyRothConversion` provide a second configuration surface. `MainWindow` wires both to the existing plan-change/projection lifecycle and validates pending Roth edits before ordinary Save.

The request stores enabled, start year, amount, frequency, stop rule, strategy and custom taxable-income target. **There is no selected source or destination account reference.** Both surfaces validate schedule/amount fields, not whether an account is eligible, funded, distributable, or paired with a destination. No source is silently created. The guided wizard's Assumptions step points users to advanced Roth settings later; its Accounts step uses the shared account editor.

### Annual engine ordering

`domain.projection.ProjectionEngine` performs the following relevant work in its annual calculation:

1. Establish year, household, death/lifetime context, income, expenses and government rules.
2. Compute growth, then allocate projection-period RMDs before additional ordinary spending withdrawals.
3. Read `RothConversionRequest`. `ScheduledRothConversionPolicy.shouldExecuteConvert` checks enabled/start/frequency and optional first-household-RMD stop rule.
4. Fixed strategy supplies a household target; custom/bracket-fill strategies call `RothConversionBracketFillCalculator.calculateConversion`. Its iterative tax-funding feedback must be retained.
5. Limit the requested amount with `ProjectedPortfolioRothConverter.getMaximumConvertibleAmount` using the post-RMD/post-spending portfolio and eligible owners.
6. `convertHousehold` allocates PRIMARY first, then SPOUSE; within owner, source portfolio order. It uses the first same-owner broad-Roth destination, skips depleted sources, and continues to later sources/other owner.
7. Report actual household/owner conversion; produce source/destination attribution; tax actual executed conversion; fund taxes; settle cash; compute ending balances, estate and next-year RMD snapshots.

RMD amounts are not converted. Pre-satisfied opening RMDs are accounted for separately and do not by themselves prevent conversion of the remaining balance. A configured stop-at-first-RMD rule remains distinct from legal RMD exclusion.

The maximum-capacity method requires both an eligible source predicate and a same-owner destination. Current absence of a destination makes capacity zero, not an implicit Roth balance. Conversion shortfall is the positive difference between requested and executed amounts. The direct single-owner `convert` requires one source sufficient for the entire amount and throws for missing destination; it is not the production household multi-account allocation contract.

`eligibleOwners` includes present household owners; lifetime-context runs also filter modeled alive owners. The ordinary configured-death path does not apply that same alive filter at this point. Do not infer comprehensive beneficiary rollover/re-titling behavior from ownership checks. Any change to conversion-after-death semantics requires a separate approved test matrix.

### Source predicates and secondary APIs

`Account.isEligibleForRothConversion` defaults false; `TraditionalIRA`, `Traditional401K`, `Traditional403B` and `Roth401K` override true. `EligibleTraditionalBalanceCalculator` and `AccountPortfolio.getEligibleTraditionalBalance` reuse this predicate; they are not a substitute for current projected capacity or destination/distribution checks.

`RothConversionExecutor.execute` transfers aggregate `ProjectedAssetPools`; `domain.withdrawal.RothConversionPlanner.plan` is an older aggregate bracket-room helper. No active production instantiations of these two were found in the source search; their tests must remain distinct from the account-level production path. They do not supply missing account destinations.

### Confirmed financial risks, not corrected

- Rollover IRA exclusion, Roth 401(k) inclusion, and broad destination classification described above.
- If a Roth 401(k) is the only eligible same-owner source and first Roth-category account, source and destination are identical. The converter subtracts then adds the amount back, but records that amount as a conversion and taxable income. This is a source-derived counterexample, not a newly executed financial fixture.
- No employer distribution-availability gate, IRA after-tax basis/pro-rata allocation, conversion withholding, Roth five-year/early-distribution accounting, or custodial transaction implementation exists in this conversion path.
- Passing existing tests establishes implementation consistency in their covered scenarios, not legal completeness.

## 5. Exact destination cases

The following describes `convertHousehold` as it exists, assuming no other broad-Roth account supplies a destination:

| Plan shape | Current behavior |
|---|---|
| Primary Traditional IRA, no Roth | Primary skipped; actual conversion zero; instruction retained |
| Spouse Traditional IRA, no spouse Roth | Spouse skipped, even if primary has Roth |
| Both have sources, neither has Roth | Both skipped; zero conversion |
| Only one owner has Roth | Only that owner's eligible balances participate; no cross-spouse transfer |
| Several same-owner Roth IRAs | First in portfolio order receives conversions, no destination selector |
| Single-person eligible source, no Roth | Primary skipped; zero conversion |
| Saved schedule, missing destination | Schedule loads; execution constrained as above; load does not create account |
| Destination removed/type changed | No stored destination pointer becomes dangling; next run selects next broad-Roth account or skips owner |

Because current lookup is broad, a Roth 401(k) or inherited Roth IRA can alter the outcomes above despite absence of an ordinary Roth IRA. This is precisely why explicit account-type eligibility is required. Renaming alone does not affect selection; removing/reordering/replacing accounts can.

## 6. Alternatives and decision matrix

Scores: 5 = strongest/lowest burden or risk; 1 = weakest/highest burden. These are architectural judgments, not measured financial scores. Option B assumes the controlled lifecycle below, not projection-time mutation.

| Criterion | A: explicit existing Roth IRA | B: persisted automatic modeled Roth IRA | C: projection-only destination |
|---|---:|---:|---:|
| Financial correctness with narrow eligibility policy | 5 | 5 | 3 |
| Architectural simplicity | 5 | 3 | 2 |
| Persistence compatibility | 5 | 3 | 2 |
| Reproducibility | 5 | 5 | 3 |
| Analyzer/account-level compatibility | 5 | 5 | 2 |
| User experience | 2 | 5 | 3 |
| Single/couple owner isolation | 5 | 5 | 3 |
| Future extensibility | 4 | 4 | 2 |
| Low regression risk | 5 | 3 | 2 |

**A:** require an explicit zero-or-positive-balance same-owner Roth IRA. Fewest persistence changes; users must understand/create an account first. Still needs source-policy corrections and shared UI gating.

**B (preferred, subject to approval):** when a user applies enabled conversion configuration, show which missing owner destinations will be added to the modeled plan, then atomically add ordinary `RothIRA` accounts with zero opening balances and the schedule. These are modeling records, not accounts opened at a custodian. Source/destination tax semantics remain explicit and all consumers see the same plan graph. New projections can change because previously unexecuted schedules become executable; no silent legacy upgrade.

**C:** needs an account-like object inserted into the projected portfolio, not a pooled tax-free balance. `withBalance` only replaces existing identity entries; it does not append absent accounts. Retained account registries could represent an ordinary RothIRA instance, but configured inputs, Accounts view, expected-account chart reconciliation and swap reconstruction would not describe that generated account. Deterministic identity, restoration and qualification would need a new explicit convention. Avoid this design in the current architecture.

## 7. Proposed centralized policy and account lifecycle

### Shared policy, not one balance boolean

Introduce a pure domain eligibility assessment used by both UI surfaces, projected capacity/allocation, and bracket-fill trial attribution. Return separate facts: supported source type, valid owner present, distribution availability/unknown, current positive capacity, destination requirement, and reasons. Proposed taxable sources are Traditional IRA, Rollover IRA and conditionally distributable traditional 401(k)/403(b). Ordinary Roth IRA is the only proposed destination. Exclude inherited accounts and designated-Roth rollovers from this taxable-conversion feature unless separately modeled.

Distribution availability needs explicit approval: the existing facts are not a complete legal qualification model. Either keep a prominently disclosed distributable-pre-tax-funds assumption, or add a bounded availability model after a separate persistence design. Do not silently reinterpret unknown participant facts as permission or introduce a blanket age-59½ rule for all conversions.

| Condition | Proposed UI / execution distinction |
|---|---|
| No supported source type/owner | Disable enabling/new configuration; show saved schedule read-only; retain an explicit ability to disable it; explain “Add a supported pre-tax retirement account to model a Roth conversion.” Engine capacity remains zero |
| Supported source with zero balance | Explain no current funds; do not equate zero with legal ineligibility or delete future schedule. Future yearly capacity is assessed from projected balances |
| Employer funds unavailable | Show why and applicable future availability if known; do not execute before it. Unknown is a separate state requiring chosen policy |
| Only spouse source | Household schedule can use it, with same-spouse destination; never require primary source |
| Owner absent from household | Invalid owner relationship; no execution/automatic destination for absent person |
| Future depletion | Convert only available amount, report shortfall; do not recreate money or source accounts |
| Future availability | Preserve schedule; execute only when the approved availability rules establish it. Existing model has no general future contribution/unlocking schedule to infer |
| Source added/last source removed | Reassess both screens on existing plan-change refresh; never erase saved instruction automatically |

### Proposed Option B lifecycle

- Creation occurs in a controller/application-level prepared **Apply** transaction, after all form and owner checks succeed. Both configuration surfaces call the same operation. Cancel/invalid input creates nothing. Never create during projection or passive opening of a plan.
- Reuse the first valid same-owner ordinary Roth IRA in existing order. If none, create at most one per eligible owner, append in deterministic PRIMARY/SPOUSE order, initial balance exactly zero. Names can follow the current person's name plus “Roth IRA (Modeled automatically)”; type/owner, not name, establish destination reuse.
- Accounts tab displays the new ordinary account immediately. Explanation: “A modeled destination for projections; this does not open a financial account.” It participates in the same persisted portfolio and dirty-state transaction as schedule editing.
- Account currently has **no UUID**. Runtime account identity is object identity; persisted results use an indexed account registry. A broad durable-account-ID migration is not necessary to add an ordinary Roth IRA and is not recommended as a prerequisite. No random UUID is generated per projection. If product requirements mandate a stable account UUID, that is a separate schema decision.
- For durable identification as automatically generated after renaming, propose optional origin metadata on ordinary Roth IRA accounts, absent on legacy/manual accounts. This needs a narrow serialization/retained-codec design and approval. A name suffix alone cannot remain authoritative after rename; do not infer origin by name.
- Allow rename/balance editing through the existing account editor while preserving optional origin metadata. Deletion requires an explanation if a schedule depends on it. It is never silently recreated during projection, Save or load. Reapplying conversion settings can explicitly recreate a missing destination.
- A later manual Roth IRA does not trigger silent balance transfer, deletion or reordering. Existing order continues to determine destination. Users may deliberately remove an unused zero-balance modeled account; historical results remain separate.
- Retain the modeled account when conversions are disabled; converted funds remain financial assets. No automatic cleanup or garbage collection.
- Ordinary Save/Load/Save As handles the account with other configured inputs. Plan switching has no global destination cache. Save As preserves account order, names, owners and values under independent plan ownership.
- Single-person plans create only PRIMARY destination. Removing a spouse requires the existing household/account ownership workflow to resolve spouse-owned accounts and pending schedules. Never retitle or transfer spouse assets automatically.

## 8. Financial projection impact matrix

Illustrative independent invariant: immediately before tax funding, source $100,000 and Roth $0 with a $50,000 executed conversion become $50,000 and $50,000. Total assets remain $100,000. For the current all-pre-tax assumption, taxable conversion is $50,000. Actual taxes then reduce household wealth through established funding, not by withholding the same tax again from the destination. This is a mathematical acceptance example, not an observed run from this audit.

| Area / exact existing boundary | Verified behavior and required preservation |
|---|---|
| `ProjectedPortfolioRothConverter.convertHousehold` | Same-owner debit/credit, multi-source capacity, PRIMARY-first ordering. Never mint a positive opening balance for destination |
| `ProjectedPortfolio.withWithdrawal`, `withBalance` | Immutable portfolio replacement; live Account balances untouched; identity membership matters |
| `ProjectionEngine` growth/withdrawal/conversion order | Existing year growth precedes conversion. Ordinary spending/RMDs reduce available source first; preserve ordering, partial-year and rounding boundaries |
| `TaxIncomeCalculator`, `TaxIncome`, `ProjectedRothConversionResult.getIncomeSources` | Actual executed gross is currently wholly taxable, attributed to source/destination/owner. No IRA basis ledger or withholding split; do not invent either |
| `RothConversionBracketFillCalculator.calculateConversion` | Iterates tax-funding consequences and builds trial conversion attribution. Shared eligibility must apply to trial and actual execution identically |
| `TaxFundingCalculator` / `ProjectedWithdrawalAllocator` | Cash/retained assets/portfolio withdrawals fund taxes under the selected strategy. Tax-deferred funding can itself increase tax. Insufficient funds retain existing failure behavior |
| Federal Social Security taxation | Conversion adds ordinary taxable income and can alter taxable Social Security; gross benefit formulas unchanged |
| Medicare/IRMAA | Existing annual authoritative premium settlement/re-evaluation can respond to conversion income. Preserve the same-year retry inputs and exact annual premium funding convention; do not introduce a new lag methodology |
| RMDs / `RmdBalanceSnapshot` | Current RMD first, conversion reduces future pre-tax balance; ordinary Roth IRA is not owner-RMD eligible. Conversion is not RMD satisfaction |
| Growth / `ProjectedPortfolio.withGrowth` | New Roth receives the existing portfolio-return allocation, not a new special investment assumption; next-year balances include conversions |
| Withdrawals / Roth qualification | Roth-category funds participate in existing withdrawals. No contribution/earnings lots or five-year penalties exist; do not present generic new Roth as legally qualified immediately |
| `AfterTaxEstateCalculator` | Heir-rate haircut applies to tax-deferred ending snapshots, not Roth. Correct transfer changes composition and after-tax estate, not pre-tax wealth by itself |
| Death/survivor and single-person | Owner presence/lifetime rules and survivor tax context remain distinct; no automatic beneficiary transfer or spouse retitling. Normal/lifetime conversion owner filtering difference requires review before extending policy |

### Implemented state jurisdictions

`ProductionStateTaxService.defaultCalculators` is authoritative; the research catalog is not the production registry. Current jurisdictions are MI, IL, PA, IA, and FL/AK/NV/NH/SD/TN/TX/WY under the no-general-individual-income-tax model. This audit verified code routing, not independent state-law completeness.

- **Michigan:** `MichiganProductionTaxAdapter` and `domain.tax.state.michigan.MichiganRetirementEligibility` consume attributed income and qualification facts. Preserve source owner/type/frozen participant facts; do not infer qualification from a generated destination's name.
- **Illinois:** `IllinoisProductionTaxCalculator` separates conversions and qualifying retirement deductions with source validation. An automatic RothIRA cannot bypass missing employer qualification or basis limitations.
- **Pennsylvania:** `PennsylvaniaProductionTaxCalculator` explicitly requires an ordinary IRA-to-`ROTH_IRA` complete attributed transfer for admitted conversions, otherwise reports unsupported/qualification/basis reasons. Employer-plan qualification remains unavailable even with participant facts. The broad current destination predicate can conflict with this narrower adapter.
- **Iowa:** `IowaProductionTaxCalculator` / `IowaTaxBaseBridge` require complete executed source/destination attribution and modeled taxable amounts. Missing conversion attribution is not zero tax. Preserve existing owner eligibility and unavailable states.
- **FL, AK, NV, NH, SD, TN, TX, WY:** routed to `NoGeneralIndividualIncomeTaxCalculator`; local/federal tax and modeled boundary checks remain independent.
- Legacy flat/local tax behavior remains separate from state-specific adapters. No state rules, tax rates, RMD rules or IRMAA formulas should change merely to add a destination.

## 9. Analyzer and reporting impact matrix

There are no durable account UUIDs in the current domain. Most outcome tables aggregate amounts; retained financial results **do preserve account-level data**. `IncomeSourceAttribution.AccountReference` freezes descriptive/qualification facts while equality identifies the runtime Account instance. `RetainedProjectionSnapshot` interns those identities into indexed `AccountData` entries and restores linked allocations/snapshots. A new subclass would fail its exact-class allowlist; use an ordinary RothIRA and explicitly version any new provenance metadata.

| Consumer / source | Account/data expectations | Impact and compatibility requirement |
|---|---|---|
| Accounts / `AccountsView`, `AccountDialog` | Persisted objects; edit replaces object at portfolio index | Option B appears naturally; preserve origin on edit; deliberate removal changes future destination selection |
| Roth Conversion / `RothConversionView` and Summary controls | Household request, no account selector | Same shared availability/preparation policy in both; display saved schedule and shortfall honestly |
| Standard deterministic / `ProjectionEngine` | Explicit projected account graph | Option B uses existing graph; C would require new synthetic membership rules |
| Results Summary / `ResultsSummaryView` | Accepted summary totals/charts, request controls | New results reflect actual transfers/taxes; old accepted summaries never recalculated in place |
| Yearly Detail / `ProjectionYearView` | Annual projection snapshots/metrics, account references where rendered | New account must appear consistently in full saved result graph, not merely aggregate Roth totals |
| Charts / `ProjectionChartModel.from` | Expected account list and complete annual snapshots; category aggregation | B keeps inputs and snapshots aligned. C risks incomplete-composition reconciliation or missing account disclosure |
| Couple/individual SS-only | SS inputs, no full financial projection | No new conversion computation; account additions must not invalidate its numerical/request contract merely through an unrelated financial hash |
| Quick / `IntegratedSocialSecurityStrategyComparisonService` | Complete isolated plan and full projections | Same frozen account list for baseline/candidates; no candidate-time creation |
| Deterministic Exhaustive / `IntegratedSocialSecurityCompleteStrategySearchCalculator` | Full-plan financial outcomes with bounded retained details | Account policy changes can change rankings; new execution provenance required; no historical reranking |
| Longevity-weighted / weighted comparison services | Isolated full-plan runs; owner/death contexts | Same destination on every scenario; no random IDs or mutable input creation; preserve exact second-death handling |
| Individual deterministic/weighted | Supported single-person graph | Primary-only destination, no fabricated spouse |
| Break-Even / `BreakEvenAnalyzer` | Directional baseline/current full-plan comparisons | Frozen account graphs must stay with each role; old inputs-only rebuilds can change under corrected calculation rules and need clear compatibility policy |
| Monte Carlo / `MonteCarloAnalyzer.analyze`, `analyzeMortality` | Scenario evaluations through the same ProjectionEngine | Identical preprepared account graph across scenarios; no additional RNG or per-scenario destination mutation |
| Paired Monte Carlo / `MonteCarloStrategyComparisonAnalyzer` | Two isolated candidates and shared worlds | Prepare each candidate before admission/fingerprinting; never add accounts inside scenario loop or perturb streams |
| Baseline/current details and swaps | Exact configured documents, accepted UUIDs, role manifest | Adding a zero-balance account is still an input-document change; must accept new projection before eligible swap. Never repair old snapshots |
| Projection/analyzer history | `CompleteProjectionSnapshot`, `RetainedProjectionSnapshot`, `AnalyzerAssociation` | Preserve exact values/ownership/digests and account index references; no association inference or rewriting |
| Projection PDF / `ProjectionPdfExporter`, `ProjectionPdfCharts` | Frozen inputs, annual metrics/composition and conversion bands | Disclose modeled account, use original context; exact historical charts remain exact |
| SS/integrated/weighted/MC PDFs | Existing frozen report contexts and retained/aggregate outcomes | Never substitute current new account into an old report; do not promise missing per-account MC histories |
| Console/CSV and aggregate estate/statistics | Projection values/account type aggregation | Consume the same completed projection; no independent destination generation |

The named exhaustive calculator is verified in the application source; individual and weighted variants reuse existing full-plan evaluation rather than a new Roth engine. Account additions change integrated and Monte Carlo financial identities. `AnalysisFingerprintService.fingerprint` includes accountPortfolio and Roth request for financial families, excludes unrelated plan storage fields, and deliberately omits the financial group for SS-only. Monte Carlo uses its own effective-input contracts. Do not add a metadata exclusion without verifying all these paths.

## 10. Persistence, migration and reproducibility

`JsonRetirementPlanRepository` already serializes ordinary `RothIRA` accounts with constructor-based Jackson. Adding one through an explicit editing transaction needs no new account type or broad plan migration. Its zero balance still changes the configured-document digest and can change projections by enabling conversions. It must not be treated as financially irrelevant metadata.

Optional generated-origin metadata is a separate compatibility extension: absent must preserve existing JSON/digests; old constructors/defaults must work; strict snapshot readers and `RetainedProjectionSnapshot.AccountData` must preserve new original facts when present. Do not weaken unknown-field/corruption checks. If retained format changes require a new version, keep old decoder meaning and add a bounded new version rather than silently redefining schema 1. Approval is required before selecting that schema.

No automatic migration should add accounts or execute previously ineffective instructions. On loading an old plan, show the saved schedule plus an actionable missing-source/destination explanation. Only a deliberate Apply may add a modeled destination. Do not rewrite accepted projections, baseline documents, UUIDs, timestamps, annual values, fingerprints or association payloads.

`CompleteProjectionSnapshot.freeze/restoreInputs` preserves exact original inputs. `ProjectionRoleManifest` and `AnalyzerAssociation` bind immutable accepted identities. `ApplicationController.projectionSwapUnavailableReason` and prepared swap validation retain freshness, ownership and exact-document guards. A newly inserted account requires the existing dirty/revision/invalidation path and a new accepted current projection; saving alone must not authorize swapping stale results. Save As creates independent ownership through existing repository contracts, without rebuilding financial results or regenerating account identities per run.

Repeated runs of an unchanged prepared plan must have the same account ordering, definitions and numerical results. No projection-time account insertion or cleanup is permitted. Account names are presentation, not source/destination or historical-association identity. Existing account-level runtime identity and retained indices should be reused; a UUID migration is optional future work, not an excuse to infer identity from names.

## 11. Proposed implementation phases and file boundaries

1. **Policy correction first:** approve supported taxable sources, exclusive ordinary-RothIRA destinations, employer availability assumptions, and known limitations. Add independent tests exposing RolloverIRA omission, Roth401K self-conversion and inherited/employer destination misuse before fixing. Main scope: `Account` subclasses, new pure policy under `domain.roth`, `ProjectedPortfolioRothConverter`, bracket-fill trial integration. Changing financial outputs is expected in these previously misclassified cases; no golden regeneration without independent evidence.
2. **Unified availability UI:** reuse policy in `RothConversionView` and `ResultsSummaryView`; preserve saved schedules, owner/zero-balance/unknown distinctions; explain shortfalls and missing destinations. No automatic source creation. Cover MainWindow refresh and account add/edit/remove.
3. **Explicit modeled-destination transaction (if B approved):** shared controller/application preparation operation, atomic account+request Apply, user disclosure, ordinary RothIRA zero balance, lifecycle rules above. Finalize optional origin metadata/strict codec versioning before any persistence implementation. `AccountDialog`, `AccountsView`, `MainWindow`, guided account editing must preserve metadata and owner relationships.
4. **Persistence/history/analyzer qualification:** `JsonRetirementPlanRepository`, snapshot codecs/models and financial family provenance reviewed narrowly; Save As, exact swaps, history/PDF tests. Retain old accepted artifacts and last-success distinction. Never relabel new-engine output as original historical results.
5. **Financial/regression/native acceptance:** independently calculated examples, settlement/state/RMD/MC coverage, full suite, theme/DPI UI checks and documentation. No dependencies, tax-law expansion or durable-ID migration unless separately approved.

## 12. Testing and native acceptance strategy

Proposed tests are not added in this audit. Reuse/extend the following existing classes:

- `ProjectedPortfolioRothConverterTest`: supported and unsupported types, owner isolation, first valid destination, several sources/destinations, source equals destination rejection, zero balances, missing destination, no mutation, exact debit/credit conservation.
- `MultiOwnerRothConversionProjectionTest`, `RothConversionProjectionTest`: fixed/bracket/custom, source depletion/shortfall, spouse-only/single, opening and annual RMD ordering, before/after spending capacity, real taxes only on actual execution, death/lifetime differences, next-year balances and after-tax estate.
- `RothConversionBracketFillCalculatorTest`, `RothConversionBracketFillStrategyTest`, `ScheduledRothConversionPolicyTest`: target feedback, available cash/tax funding, insufficient capacity, stop rule vs mandatory RMD exclusion and future eligibility. Do not replace iterations with a one-pass calculation.
- `RothConversionPersistenceTest`, `RothConversionRequestJacksonTest`, `ProjectionSnapshotPersistenceTest`: absent metadata legacy bytes/digests, strict new optional metadata, account/reference reconstruction, Save/Load/Save As, clone isolation, exact original annual values, failure/cancel no partial insertion.
- `AccountTypeTest`, `AccountFactoryTest`, `AccountTaxTreatmentTest`, account dialog/wizard tests: all eleven types/owners, validation, manual/automatic edit/rename/delete/replacement, absent spouse, household removal; no unsupported source fabricated.
- `IncomeSourceAttributionTest`, `TaxIncomeSourceAttributionTest` and state production suites: actual source/destination identity and owner, federal conversion inclusion, Social Security tax interaction, IRMAA retry consistency, MI/IL/PA/IA qualification/unavailable states and every enabled no-general-tax jurisdiction. Do not convert unavailable tax into zero.
- `RothConversionViewTest`, `RothConversionMainWindowTest`, `ResultsSummaryRothValidationTest`: both configuration surfaces, pending drafts/Cancel, missing eligible source read-only schedule, explicit disable action, zero balance vs unsupported type, add/remove refresh, Apply creates at most one destination per needed owner and marks dirty once.
- Existing snapshot/swap, integrated/weighted/individual, Monte Carlo and PDF suites: immutable associations follow original UUIDs, stale edited inputs refused, double-swap exactness, no recalculation on history opening, original-context export, deterministic/MC consistent account graphs, repeated seeded draws unchanged when policy/configuration is unchanged.

Independent financial invariants must include: transfer alone conserves total balance; no cross-owner credit; actual conversion <= available eligible post-RMD balance and requested amount; RMD remains a distribution, never conversion; taxes use executed amount; no double tax deduction; future RMD uses reduced prior December 31 pre-tax balance; after-tax estate changes only through composition/taxes under existing definitions; retained cash neither vanishes nor becomes a conversion source; zero-opening destination creates no wealth. Verify taxable cash versus tax-deferred tax funding separately because gross-up differs. For a fully funded existing correct TraditionalIRA-to-RothIRA scenario, all existing golden values should remain exact.

Native checklist after an approved implementation: couple/single plans; both themes at supported sizes/DPI; no source, zero source, spouse-only source, employer unavailable/unknown; missing destination disclosure; Apply/Cancel and rapid clicks; add/remove source/destination while schedule retained; automatic-account explanation/edit/delete; Save/restart/Save As/switch/swap; original and new history/PDF labels; no background analysis launched just by viewing eligibility.

## 13. Actual verification performed

IntelliJ bundled Maven was used with `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository` and `-DargLine=-Xmx4g`.

| Existing test selection | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| `*Roth*Test,AccountOwnershipTest` (no-match tolerance for the ownership name; actual classes below) | 114 | 0 | 0 | 0 | BUILD SUCCESS, 14.953 s |
| `AccountTypeTest,AccountFactoryTest,AccountTaxTreatmentTest,ProjectionSnapshotPersistenceTest,IncomeSourceAttributionTest,TaxIncomeSourceAttributionTest,MultiOwnerRothConversionProjectionTest` | 83 | 0 | 0 | 0 | BUILD SUCCESS, 12.893 s |

The runs overlap in `MultiOwnerRothConversionProjectionTest`; their counts must not be presented as 197 unique tests. The first run includes request/Jackson/persistence/policy/converter/bracket/executor/planner/demo and UI Roth tests. It does not establish new legal-case coverage. Logs: `target/roth-architecture-audit/focused.log` and `compatibility.log`.

No new tests were authored, per audit restriction. Full Maven suite, new failure reproductions, native UI, Monte Carlo benchmarks and new PDF previews were not run for this audit; the focused existing suites substantiate current behavior without implementation. Financial-policy defects above remain source findings requiring new regression tests after approval.

`git diff --check`: **exit 0** after report validation. Staging index remains empty. No staging, commit, push, reset, clean, settings writes, plan migrations or production modifications were performed.

## 14. Explicit approval decisions

1. Approve correcting source eligibility: include supported pre-tax RolloverIRA; remove Roth401K from fully taxable conversion execution. Treat designated-Roth rollovers as a separate deferred capability.
2. Approve restricting destinations to same-owner ordinary RothIRA, excluding inherited Roth and employer Roth accounts. This changes outcomes for previously misrouted schedules; preserve historical results and disclose old-model limits.
3. Choose employer-plan availability policy: explicit modeling assumption with clear limitations, or a separately designed qualification/availability model. Existing separation/retirement facts are insufficient for blanket legal availability.
4. Approve Option B's disclosed account+schedule Apply transaction, or choose Option A instead. No automatic insertion on load/projection. Decide optional origin metadata and any required retained-artifact version extension before code.
5. Approve preserving schedule visibility while preventing new enablement without a supported source; retain explicit disable action, zero-balance/future/unknown distinctions and no schedule deletion.
6. Confirm Roth basis/five-year/withholding and beneficiary/inherited conversion workflows remain unsupported with honest disclosures; any expanded tax treatment needs its own financial design.
7. Approve a bounded compatibility/provenance policy for new calculations after source/destination correction, including legacy inputs-only rebuilds. Do not redefine an existing historical calculation identity or rewrite accepted artifacts silently.
8. Review ordinary configured-death versus lifetime owner availability before changing that rule; automatic destination work must not silently implement beneficiary transfers or conversion-after-death changes.

**Stop here for review and explicit implementation approval.**

## Appendix: preserved working-tree inventory

Tracked modifications present before this audit:

- `.idea/encodings.xml`
- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloAnalyzer.java`
- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonAnalyzer.java`
- `src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisFingerprintService.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/financial/Expense.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/MainApplication.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/ExpenseDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRunService.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonRunService.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSwapTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12FAnalyzerSwapTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/startup/StartupSplashViewTest.java`

Untracked files present before this audit:

- `PHASE-14B3A-EXPENSE-VOLATILITY-CONFIGURATION.md`
- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/ExpenseVolatilityAdmission.java`
- `src/main/java/com/daviddunn/retirementplanner/app/projection/DeterministicExpenseInputs.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/financial/ExpenseDeserializer.java`
- `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/ExpenseVolatilityAdmissionTest.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/ExpenseVolatilityCompatibilityTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/dialogs/ExpenseVolatilityUiTest.java`

New audit file: this report only. Generated test output is ignored and excluded from a future commit inventory.
