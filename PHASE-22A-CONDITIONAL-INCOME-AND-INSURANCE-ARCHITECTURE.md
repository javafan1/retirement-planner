# Phase 22A — Conditional Income, Pension and Insurance Capability Architecture Audit

Audit date: November 5, 2026. Branch `main`; HEAD `e6d8a9cc6c70377b12237d1b25f3f92ca3203b41`. Starting working tree and index were clean. **Architecture only: no production, test, schema or build changes.** Phase 21 is accepted background functionality, not unfinished work.

## 1. Executive summary

**Do not replace the Pension model to obtain independent survivor amounts: it already has them.** `Pension.survivorMonthlyBenefit` is a separate monthly dollar value. The editor explicitly requests dollars, accepts a zero primary payment, and does not derive the survivor amount from a percentage. A conventional survivor-only pension can already be represented as primary $0 / survivor $2,500, subject to its shared commencement/end/COLA rules and an important tax-path limitation below.

**A demonstrated existing cash/tax discrepancy needs separate approval before expansion.** A synthetic full projection with that pension pays $30,000 after the primary's death. The ordinary configured-death path nevertheless records $0 pension AGI; the equivalent lifetime-scenario path records $30,000. The latter supplies authoritative death-selected pension income to tax calculations, while the former independently reads original owner income. This is not a Phase 21 stochastic-expense defect. Do not market the existing representation as fully correct survivor-only financial modeling until this boundary is addressed. No correction is implemented here.

**Recommend a staged hybrid:** preserve ordinary pensions and Social Security; add a small immutable conditional-benefit definition and shared year-evaluation result for genuinely new date/age/death-conditioned payments. Start with living primary/spouse recipients, monthly recurring payments and one-time receipts. Do not build an unrestricted event-expression language or a universal insurance product engine. Funding transactions, annuity basis, surrender values and insurance cash values need a separate contract/accounting layer.

**The hardest boundaries are tax classification, destination ownership and terminal estate timing, not the trigger predicate.** Accounts have no persisted stable ID; household retained cash has no individual legal owner. Longevity analyses stop before second-death year. Neither mechanism can be presented as complete beneficiary/estate settlement. Prioritize first-death benefits payable to a surviving modeled person; defer outside-beneficiary/second-death estate settlement until explicitly designed.

Focused existing verification: **160 tests, 160 passed, zero failures/errors/skips**. A separate ignored synthetic diagnostic reproduced the pension discrepancy and verified independent survivor persistence and no plan mutation. No full financial suite was run, as requested for this documentation-only phase.

## 2. Existing model and editor inventory

Source paths below are relative to `src/main/java/com/daviddunn/retirementplanner/`. Method references describe actual current source, not historical design claims.

| Surface | Actual implementation and consequence |
|---|---|
| `domain/income/IncomeSource` | Abstract person-associated income; Jackson `incomeType` recognizes only `pension` and `socialSecurity`. Required name/ownership/start date; optional end. `getAnnualIncome` calls `getActiveMonths` then subtype arithmetic. There is no production salary, generic windfall, insurance or annuity subtype. |
| `domain/income/Pension` | Independent owner/survivor monthly amounts, one start/end range, one fixed annual COLA. Financial fields final; participant facts remain mutable through `setRetirementParticipantFacts`. It is not a fully immutable contract graph. |
| `domain/model/Person` | Mutable person and income collection (`addIncomeSource`, `replaceIncomeSource`, `removeIncomeSource`); `getAge(LocalDate)` is date-derived. Person ownership is represented by primary/spouse placement, not arbitrary beneficiaries. |
| `domain/model/Household` | Primary plus optional spouse, `members`, `peopleByOwner`, reference validation. No estate/trust/external-person ledger. `JOINT` account ownership is not a person. |
| `domain/model/AccountOwnership` | PRIMARY, SPOUSE, JOINT only. No owner/insured/beneficiary/payer role distinctions. |
| `domain/financial/Account`, `AccountType` | Bank/brokerage, traditional/rollover/employer retirement, Roth and inherited IRA families. CASH/TAXABLE/TAX_DEFERRED/ROTH classifications do not constitute insurance tax or liquidity rules. No persisted account UUID. |
| `domain/noninvestable/NonInvestableAsset` | Name, current value, annual growth; separately projected and combined into net worth. No cash-flow liquidation, premium, death benefit, basis, surrender or annuitization mechanics. |
| `ui/views/IncomeSourcesView` | Actual current Income workspace (there is no current `IncomeView.java`). Add chooses Pension/Social Security; accepted dialogs update the appropriate person's list and invoke the plan-change callback. List edits are immediate commits, not a reversible whole-screen draft. |
| `ui/dialogs/PensionDialog` | Shared ordinary/guided editor; separate survivor monthly dollar field, start/end dates, decimal COLA and participant facts. `validateBeforeSubmit` requires a valid name, owner, start and parseable numbers; it does not require primary benefit > 0 or enforce a survivor/primary ratio. Zero is accepted. Do not silently tighten negative/legacy numeric policy in this initiative. |
| `ui/wizard/IncomeWizardStep` | Pension-only list over `NewPlanDraft`; `edit` reuses `PensionDialog(..., true)`. The draft controls final wizard acceptance, distinct from workspace immediate commits. |
| `domain/projection/IncomeStep` | Placeholder `execute` has no income implementation. The authoritative integration is `ProjectionEngine`; do not implement a second engine in this placeholder. |

## 3. Pension, survivor and death-state behavior

| Question | Verified answer |
|---|---|
| Primary payment / owner | `monthlyBenefit` and `IncomeSource.ownership`; normal UI offers PRIMARY and, when present, SPOUSE. Person-list placement matters to the legacy survivor scan, so owner/list consistency is important. |
| Commencement / end | Explicit dates, not persisted claiming/start ages. `IncomeSource.getActiveMonths` counts included calendar months, including partial start/end months as whole active months. No independent survivor commencement/end date. |
| Survivor percentage | Not stored or calculated. Existing `survivorMonthlyBenefit` is independent dollars, nullable; blank/null means no survivor stream. Do not reinterpret it during migration. |
| Beneficiary | Implicit opposite household member, not a selectable named person/account/estate. Single-person editor/core reject spouse-owned/positive survivor pensions. |
| Owner's death | `ProjectionEngine.calculateIncome` suppresses dead-owner income; `calculateSurvivorPensionIncome` finds the deceased person's pensions when exactly one member is alive. Lifetime runs instead use `HouseholdPensionIncomeCalculator.calculateWithSources`. |
| Survivor's death | Lifetime calculator returns zero after `isHouseholdDeceased`. No guarantee payable to an outside beneficiary. Ordinary persisted death configuration models BOTH_SURVIVE or one named first death, not both deaths. |
| COLA | Both owner and survivor amounts multiply by `(1 + annualColaRate)^(paymentYear - originalStartYear)`. Survivor COLA does not reset at death. Same original date range limits both. No separate index or benefit-election pricing. |
| Single / joint election | Inferred from presence of survivor amount, not an explicit single-life/joint-life contract enum. JOINT is not offered as a pension owner; it is not a shortcut to joint-annuitant guarantees. |
| Pension taxation | Existing model generally treats pension gross as fully federally taxable. `IncomeSourceAttribution.pension` sets modeled taxable = gross and carries participant facts for supported-state classification. No annuity investment-in-contract basis. |

**Zero primary / nonzero survivor is representable and persists today.** No constructor, editor or percentage constraint prohibits it. It is limited by the shared pension schedule/COLA, spouse-only beneficiary and the following ordinary-path tax discrepancy.

### Reproduced discrepancy: existing pension tax selection

`ProjectionEngine.executeInternal` sets `lifetimeRun` only when `evaluationContext.householdLifetimeScenario()` is present. `calculateProjectionYear` supplies `HouseholdPensionIncomeCalculator`'s authoritative total and sources only in that branch. Without it, guaranteed income uses death-selected owner/survivor payments, but `TaxIncomeCalculator.calculateIncome` independently reads each original pension's owner amount without the equivalent death selection.

Ignored `target/phase22a/PensionAuditProbe.java` uses an existing synthetic fixture, zero returns/SS amounts, pension start 2030, primary death 2031, $0 primary / $2,500 survivor, no COLA. A lifetime override additionally places spouse death in 2033. Results:

| Year | Configured-death gross pension | Configured-death AGI | Lifetime gross pension | Lifetime AGI |
|---|---:|---:|---:|---:|
| 2030 | 0 | 0 | 0 | 0 |
| 2031 | 30,000 | 0 | 30,000 | 30,000 |
| 2032 | 30,000 | 0 | 30,000 | 30,000 |
| 2033 | 30,000 | 0 | 0 | 0 |
| 2034 | 30,000 | 0 | 0 | 0 |

The different 2033+ gross values are intentional different death scenarios; the **2031/2032 cash/AGI divergence under the same alive state is the defect**. JSON round-trip preserved primary zero and independent survivor amount. Source plan JSON was unchanged. The first diagnostic fixture omitted SS records and correctly hit the existing advanced-couple lifetime admission guard; it was corrected with zero-benefit SS records, not by changing that guard. Final evidence: `target/phase22a/pension-probe.log`.

Recommend a separately authorized **22B0 pension cash/tax consistency correction**, with characterization and tax/Roth/state regression before changing ordinary behavior. This audit neither fixes it nor changes golden values. Existing lifetime reconciliation tests pass because they exercise the authoritative branch.

## 4. Reusable projection mechanisms and their limits

| Mechanism | Safe reuse / required boundary |
|---|---|
| `EffectiveHouseholdDeathView.resolve/state/deathDate/isAlive/isHouseholdDeceased` | Immutable effective life state for a run. Resolve once from configured timing or existing lifetime override. Do not use current UI ages or draw new deaths. |
| `HouseholdLifetimeScenario.primaryDeathDate/spouseDeathDate` | Death years map to January 1. Missing year means survives through horizon, not a claim event. Absent spouse is distinct from deceased spouse. |
| `ProjectionEvaluationContext.withLifetimeScenario/withExactEndingYear/withExpensePath` | Existing scenario overrides and immutable stochastic paths; new benefit evaluation must consume these rather than replace them. |
| `HouseholdPensionIncomeCalculator.calculateWithSources` | Useful pattern: one selected gross amount and original-owner/actual-recipient attribution for cash and tax. Do not blindly treat every new benefit as pension income. |
| `ProjectionEngine.calculateProjectionYear` | Safest cash/tax integration boundary after effective timing and before cash need/RMD/withdrawals/conversion/tax funding. Existing Medicare retry recursively recalculates a year from its opening portfolio; evaluator must be pure. |
| `HouseholdCashSettlement.calculate` | Authoritative post-tax sources/uses reconciliation; retained surplus deposited exactly once at final year completion. New receipts must enter one source channel, not receipt + duplicate deposit. |
| `ProjectedPortfolio.from/withBalance/withAdditionalRetainedNonQualifiedAssets` | Projected balances separate from persisted accounts. Run-only retained non-qualified assets are a household pool; no person-specific beneficiary account ownership. Never call live `Account.deposit` during projection. |
| `TaxIncomeCalculator`, `TaxFundingCalculator`, Roth bracket fill, `ProductionStateTaxService` | Reuse iterative calculation, but extend gross/taxable classification explicitly. Existing five-component TaxIncome is not an arbitrary payment ledger. |
| `ProjectionMetricsCalculator`, Break-Even comparison | Consume completed projection values; do not add independent benefit arithmetic to comparisons. |
| `IncomeSummaryService.calculatePension` | Configured monthly owner-amount display only; it is not death/date-aware payable income. New conditional payments must not be shown as already guaranteed/current monthly income by summing face values. |

## 5. Supported and missing capabilities

Current support: future-date monthly pensions, scheduled end dates, separate survivor dollar amounts, owner death cessation, pension COLA and normal full-plan downstream cash/tax handling with the caveat above. An age can be translated manually to a date, but there is no explicit age trigger or automatic pension start-date rebinding when DOB changes.

Missing: general conditionally triggered income, death-triggered lump sums, independently timed survivor schedules, first/second-death contract definitions, payment-count/period guarantees, selectable beneficiary/destination, premium funding/lapse, contract basis/cash/surrender value, annuitization transactions and product-specific tax accounting. An Expense.ONE_TIME is spending, not a receipt. Negative spending, a one-month pension, a renamed IRA or a growing NonInvestableAsset is not a supported insurance implementation.

## 6. Use-case compatibility matrix

“Supported today” below means the stated component is actually represented; it does not certify unsupported tax/product features.

| Use case | Current evidence | Classification and recommendation |
|---|---|---|
| A: $0 primary, $2,500/month surviving spouse | Independent amount and zero primary accepted; original schedule bounds; stops after spouse death in lifetime context | **Representable today for pension cash flow; narrow correction required for consistent ordinary tax results.** Use existing Pension when it really is an ordinary taxable pension. Non-pension insurance installments need explicit tax treatment, not a pension disguise. |
| B: $400,000 on covered insured death to living spouse | No benefit event, lump-sum subtype or beneficiary destination | **Requires small conditional-benefit + cash/tax settlement foundation.** Named-account deposit additionally needs a stable destination-reference policy. Full term-policy premiums/lapse belongs to specialized contract layer. |
| C: premium now, income at age 75 for life | Date-delayed pension can approximate the payment stream | **Payment leg narrow/reusable; complete funded annuity requires specialized transaction/tax support.** Purchase must remove accessible assets once; do not claim a whole annuity contract from income alone. |
| D: joint-life immediate annuity, distinct post-first-death amount | A normal owner's pension can continue to opposite spouse, but does not express one symmetric both-alive/first-death schedule | **Bounded benefit schedule extension**, with mutually exclusive both-alive/primary-only/spouse-only branches. Full pricing/funding/basis still specialized. Do not synthesize two overlapping pensions as a generic solution. |
| E: period-certain, beneficiary continuation | Pension end date alone still stops on death | **Benefit guarantee model required; external/after-second-death continuation deferred** pending recipient/terminal-valuation design. Life-or-guarantee is a union of eligibility, not an earliest-of-end rule. |
| F: fixed deferred annuity/MYGA | No contract account/basis/surrender restrictions | **Specialized contract model; medium priority after funding/basis foundation.** Not just a fixed growth rate on a normal account. |
| G: life insurance with cash value | No combined premium, cash/surrender value and death-benefit accounting | **Specialized insurance model; defer automatic mechanics.** Later supported illustration schedules may be safer than attempting universal insurer product formulas. |

## 7. Architecture alternatives and recommendation

| Alternative | Assessment |
|---|---|
| A — extend Pension for every product | Small initial UI change but wrong tax/role/value semantics; would overload employer pension facts and risk legacy survivor calculations. Use only for genuine narrow pension additions. |
| B — separate ConditionalIncome record | Best small initial addition for date/death recurring income; needs explicit gross/taxable/recipient mapping. Alone does not solve premiums, assets or annuity basis. |
| C — reusable BenefitSchedule owned by pension/contract | Appropriate composable evaluation for future contracts. Do not migrate all old pensions at once or expose arbitrary expressions. |
| D — universal insurance/event engine | Excessive for initial needs; defer. |

**Choose B with a small C-style evaluator, preserving legacy pensions.** Proposed names are architectural suggestions, not implemented APIs:

- `ConditionalBenefitDefinition`: immutable ID/label, start rule, coverage bounds, payment rule, named-person roles, stop rule, escalation origin/rate, tax character and destination. Initially a bounded plan-level list, since insured and recipient can be different people; do not duplicate the record in both persons' lists.
- Closed trigger variants: fixed date; age of PRIMARY/SPOUSE resolved from frozen DOB; death of PRIMARY/SPOUSE; first death; second death. Initially enable only approved date/age/named-first-death cases. Optional not-before date/age means max(trigger, threshold), not a recursive boolean DSL.
- Closed payment variants: monthly level recurring payment and one-time amount first; annual due-date payments only with an explicit recurrence/timing convention. Later add bounded guarantee/payment-count rules and finite mutually exclusive joint-life branches.
- `BenefitEvaluationContext`: frozen household membership, effective death view, projection start/year and approved escalation context. No mutable plan/UI reference or RNG.
- `AnnualBenefitEvaluation`: immutable per-definition cash receipts, taxable/excluded portions, premium/funding flows when supported, recipient/destination and event keys. Pure evaluation; cash/tax/settlement consume the same result.

Validate nonnegative amounts, finite supported rates, valid dates/counts, coverage ordering, present required people, supported role combinations, taxable ≤ gross and bounded definition/payment counts. Exact bounds must be chosen in B1 capacity tests; reuse current year/document limits rather than inventing unlimited schedules. Do not tighten old pension validation as an incidental migration.

## 8. Person, recipient and destination roles

Use a dedicated named-person reference (PRIMARY/SPOUSE), not `AccountOwnership.JOINT` as a life. Keep policy owner, insured, annuitant, recipient, beneficiary and payer conceptually separate even when UI defaults them to the same person. Initial support need not persist all seven optional roles on a simple income record: require only those its selected type uses; a future contract owns owner/insured/payer facts and its benefit schedule supplies recipient/beneficiary.

Single-person plans permit PRIMARY income/payer/insured roles, with no fabricated spouse. A second-death trigger requires a couple; an absent spouse must not satisfy a death predicate. First-death may reduce to primary death for a single person only if explicitly approved and clearly labeled.

External beneficiary/estate metadata may be recorded later, but no external amount becomes household investable assets. Initially reject unsupported payable-outside-household configurations rather than relabeling them as PRIMARY. A possible future informational external distribution must be a separate non-household flow, excluded from retirement success, available cash and estate metrics unless an explicit settlement model says otherwise.

**Destination decision:** safest initial receipt destination is existing household non-qualified cash, disclosed as such. It does not promise deposit into the surviving spouse's legally owned account. If Case B requires a specific account immediately, introduce a separately approved stable account identity/reference before it is released. `IncomeSourceAttribution.AccountReference` explicitly uses opaque runtime identity because `Account` has no persisted ID; names and unvalidated list indices are unsafe durable references. Qualified account destinations cannot be treated as ordinary deposits or contributions without eligibility/rollover rules. Deletion/change of a referenced account must block or explicitly reselect, never silently route elsewhere.

## 9. Event timing and pure evaluation policy — approval required

1. **Death:** retain January 1 of the selected death year for projection/insurance events. New features must not invent a separately sampled month/day. Advanced Social Security monthly rules remain in their own authoritative calculator.
2. **Existing income convention:** pension start/end calendar months are inclusive; partial months count as active months. Pension first-year income is not automatically clipped to the projection opening month when its own start predates that opening. Existing July-opening tests report the full eligible pension calendar-year amount. Expenses have a different explicit opening-year proration rule. Do not silently unify these historical conventions.
3. **New payments:** recommend recognizing only new-type payments due in the modeled interval beginning at projection start, to prevent replay of pre-opening receipts. Use explicit inclusive calendar-month recognition for monthly payments, with no daily proration; resolve age dates from frozen DOB (including documented leap-day policy). This opening cutoff is an approved new-type policy, not a retroactive change to Pension. Alternatively retaining legacy calendar-year income is a user decision, not an implementation assumption.
4. **Death recurring income:** eligible at January 1 death, limited by coverage/not-before/start/end and living recipient. No own/survivor double payment for a single exclusive benefit schedule. Death before the projection start can establish an already-active recurring stream; it must not cause a historical one-time payment to be replayed.
5. **Lump sum:** event date must fall in coverage and the modeled interval. It is evaluated exactly in its event year. A repeat call returns the same receipt; it is not another financial deposit. Coverage endpoint inclusivity and same-day start/end are explicit B1 policies. Recommend inclusive coverage dates.
6. **Ordering:** compute year receipts before withdrawal/tax-funding/Roth decisions; preserve current opening-balance investment growth conventions. Recommend no new same-year full-year growth on fresh benefit cash unless explicitly specified. Spending/tax uses receipts first, and unspent after-tax cash is retained once by final settlement.
7. **Retries:** compute from immutable opening-year inputs; Medicare/tax funding retries must not carry “already paid” flags or mutate a contract. Event key can be definition ID + payment/event date; legitimate identical separate contracts retain distinct IDs. Commit to the next projected portfolio only once after the year converges.
8. **Guarantees:** fixed payment count is derived arithmetically from commencement; no mutable decrementing counter. Guarantee duration, life continuation and recipient succession are distinct rules. Death before deferred commencement does not imply an undocumented refund/death rider.
9. **Both deceased:** ordinary living-recipient income is zero. Estate/external guarantees are not converted into household cash. Terminal rules below require separate approval.

## 10. Tax and account-funding requirements

The following are architectural boundaries, not a complete tax-law implementation or product eligibility certification.

| Treatment | Existing capability / required addition |
|---|---|
| Ordinary taxable pension | Reuse federal pension bucket and supported-state participant facts after correcting death-selected cash/tax consistency. Existing pension gross is generally fully taxable in the model. |
| Other ordinary conditional income | Needs explicit non-pension ordinary tax aggregation. `TaxIncome` currently contains pension, SS, deferred withdrawals, conversion and taxable interest only. Merely subclassing IncomeSource or emitting Kind.OTHER does not add federal taxable income. |
| Qualified annuity distributions | Existing retirement withdrawal/RMD infrastructure is useful, but annuitized holding valuation/distribution attribution and account aggregation require separate rules. Do not treat an inside-IRA purchase as an outside taxable cash withdrawal automatically. |
| Nonqualified periodic annuity | Needs investment-in-contract, commencement, method/expected-return factors, cumulative basis recovery and survivor/guarantee treatment. Do not label the whole payment tax-free or ordinary pension by default. |
| Life insurance death proceeds | Separate excluded principal from taxable interest and exceptional cases. Do not place gross proceeds in pension/SS or use the same gross as taxable income. |
| Deferred contract growth / surrender | Needs contract basis, gain recognition, charges and distribution ordering; current TAX_DEFERRED account classification is insufficient and may imply incorrect retirement/RMD behavior. |
| Tax-deferred account funding | Distinguish external distribution from internal qualified transfer/annuity holding purchase; coordinate statutory RMD and opening distributions. No tax duplication through withdrawal plus pension receipt. |
| Roth funding/distribution | Current projection models Roth withdrawals under its established assumptions; it is not a new annuity qualification/holding-period engine. Complex/nonqualified Roth-funded arrangements need explicit supported-case rules. |
| Beneficiary/inherited proceeds | Existing inherited IRA models do not automatically apply to insurance or inherited annuities. Destination, beneficiary and income-in-respect-of-decedent rules need validation. |
| Estate inclusion | Current `AfterTaxEstateCalculator` applies a configured heir-tax haircut to deferred investable balances. It is not a federal/state estate-tax or insurance incidents-of-ownership calculator. |

Authoritative starting points checked during this audit:

- Fully taxable versus basis-containing pension/annuity payments and qualified Roth distinctions: [IRS Topic 410](https://www.irs.gov/taxtopics/tc410). Preserve the distinction between funding source and payment character.
- Periodic/nonperiodic and survivor annuity taxation are different; inherited deferred-annuity gains cannot simply be treated as tax-free life-insurance principal: [IRS Publication 575](https://www.irs.gov/publications/p575). These rules require a product-specific implementation gate.
- Basis recovery is generally capped by investment in contract, and the tax-free portion can differ from changes in gross payments: [IRS Topic 411](https://www.irs.gov/taxtopics/tc411). A fixed taxable percentage is at most a clearly labeled user-supplied planning approximation, not statutory exclusion-ratio support.
- Death proceeds are generally excluded but interest is taxable; transfer-for-value exceptions exist: [IRS life-insurance proceeds FAQ](https://www.irs.gov/faqs/interest-dividends-other-types-of-income/life-insurance-disability-insurance-proceeds/life-insurance-disability-insurance-proceeds). Cash surrender can recognize gain over policy cost: [IRS Publication 17, insurance](https://www.irs.gov/publications/p17).
- IRA distributions/RMD/beneficiary rules require separate qualification checks: [IRS Publication 590-B](https://www.irs.gov/publications/p590b). Income-tax exclusion is not proof of exclusion from the taxable estate: [IRS estate-tax FAQ](https://www.irs.gov/businesses/small-businesses-self-employed/frequently-asked-questions-on-estate-taxes).

State integration must use `ProductionStateTaxService.calculate`, whose current registry explicitly includes Michigan, Illinois, Pennsylvania, Iowa and jurisdictions using the no-general-income-tax model. The research catalog is not an activation registry. `IncomeSourceAttribution` carries source/recipient, pension participant and known/unknown modeled-taxable facts. Its `destination` currently permits only same-owner Roth conversions, so it is not a general benefit destination field. New insurance/annuity facts must not borrow pension retirement exclusions falsely. Unknown eligibility is unknown, not zero tax; unsupported combinations need clear admission/approximation feedback. No fifty-state insurance-tax expansion is proposed.

Future authoritative validation gates include section 72 periodic/nonperiodic basis methods, inherited annuity rules, section 1035 exchanges, qualified annuitization/RMD aggregation and QLACs, early-distribution exceptions, MEC/loan/lapse rules, transfer-for-value, ownership/estate inclusion, state adjustments and withholding versus tax liability. No limits/rates are hard-coded or new legal calculations promised in this audit.

## 11. Contract value and double-counting policy

| Value | Proposed economic role |
|---|---|
| Premium / purchase price | Funding transaction or expense, reducing accessible resources once; not a second asset. |
| Contract/account accumulation value | One explicitly classified owned holding; tax/withdrawal/liquidity rules separate from ordinary portfolio growth. |
| Cash surrender value | Net accessible amount after charges/constraints; not an additional asset on top of account value. |
| Income benefit base | Non-liquid contractual calculation reference, never investable assets or net worth by itself. Rollup is not investment return. |
| Death benefit | Contingent payable amount; not pre-death investable assets. Contract terms determine whether cash value is included or additional. |
| Current income payment | Cash flow once, with taxable/basis portions; reduce a contract balance when its distribution design requires it. |
| Future guarantee | Disclosure/optional separately valued promise; no automatic addition to net worth or retirement funding. |

Use a specialized immutable `InsuranceContractDefinition` / `AnnuityHoldingDefinition` later, with a run-local deterministic roll-forward (or pure prefix evaluation) for basis and values. No mutable state shared across projections/scenarios. Annuitization must remove/convert the original accessible contract/account holding once and activate its payment schedule; a separate pension entry must not duplicate that same income. Opening contracts already purchased must not replay a premium. A sourced purchase, annuitized income and remaining accessible value must reconcile.

Existing NonInvestableAsset cannot enforce any of this. Nor should an ordinary IRA be renamed MYGA to obtain nonqualified tax deferral. Premiums can currently be entered as generic expenses only as a disclosed manual approximation: those expenses lack insured-death/lapse logic and may receive household survivor adjustments or configured stochastic volatility. A supported contract should own its premium schedule, with deterministic contractual premiums excluded from expense sampling unless a separately approved model says otherwise. Do not create a duplicate user Expense plus contract premium.

## 12. Deterministic, longevity and comparison integration

All new full-plan payments should flow through one projection evaluation result. `IntegratedSocialSecurityStrategyEvaluator.evaluate` isolates the plan and changes only claiming context; Quick Comparison, exhaustive grids and integrated search consume that engine. They must retain the new benefit list through every copy/override. SS-only deterministic/mortality grids calculate SS benefits independently and should remain SS-only, not start adding pensions or insurance to their objective. Where SS-only ranking selects integrated candidates, describe that ranking honestly; do not redefine its objective.

`LongevityWeightedIntegratedStrategyEvaluator`, `SinglePersonMortalityAnalysis` integrated paths and `HouseholdLifetimeScenarioMapper` provide scenario deaths. Reuse those deaths and probabilities. Conditional payouts change completed projection values, not mortality weights, discount rates, SS claiming rules or Break-Even crossover algorithms. Full-plan prefix/carrier/equivalence optimizations must prove new benefit schedule coverage and distinguish trigger differences; disable unsupported reuse conservatively until proven, not silently reuse a carrier with different conditional income.

**Terminal boundary:** `EstateAtSecondDeathCalculator.calculate` takes prior December 31 balances; weighted and longevity MC runs end at second-death year minus one, with a special opening-death case. Therefore a second-death insurance claim or guaranteed payments after last death are outside the current financial horizon. Do not inject them into the prior-year balance or silently extend projections. Initially support living-spouse first-death receipts; estate/external terminal settlement or discounted remaining guarantees require a separately approved metric/horizon/beneficiary design. Single-person death benefits normally go outside the modeled living household and cannot rescue pre-death retirement success.

## 13. Monte Carlo and Phase 21 interaction

Fixed and longevity standalone/paired analyzers already call the common engine with frozen inputs. New benefits must consume the existing `EffectiveHouseholdDeathView` for that scenario. No insurance-specific mortality draw, random dimension or seed change is warranted. In fixed mode use its configured death convention; in longevity mode use existing sampled deaths. Term cover pays only for an eligible event during coverage; deferred life income can remain unpaid if annuitant dies before commencement; period guarantees follow approved recipient/terminal rules.

Current/Baseline each retain their own complete benefit definitions and funding assumptions, while sharing existing economic/lifetime worlds. New conditional income must not change Phase 21 expense correspondence: exact normalized expense definitions and expense dimension 6/1 remain unchanged. Do not encode premiums as new stochastic expenses inadvertently or insert draws into market/mortality/inflation streams.

Empty/no-new-benefit plans must retain exact seeded outputs and existing zero/positive expense behavior. New conditional payments naturally affect withdrawals, taxes and success through the engine, not a second MC approximation. Public analyzer admission must reject unsupported new benefit/terminal cases clearly until the full matrix is verified; persisted definitions must never be silently ignored by a partially integrated analyzer.

## 14. Persistence, fingerprints and historical results

Recommend an optional, immutable, bounded plan-level `conditionalBenefits` collection: missing/null maps to empty for legacy compatibility; empty is omitted when serialized. Invalid explicit nonempty definitions are rejected. Old Pension fields and their meanings remain unchanged. Existing plan loading must not rewrite files or introduce placeholder benefits.

**Required reconstruction inventory:** `RetirementPlan` constructors; `RetirementPlanSnapshot` and `ProjectionBaselineFactory`; `CompleteProjectionSnapshot.freeze(plan)` (currently explicitly constructs a five-component RetirementPlanSnapshot); `BaselineProjectionService.projectBaseline`; `RetirementPlanScenarioCopyService.copy`; `ApplicationController` swap/save/preparation/restored-execution constructors; `MonteCarloStrategyComparisonRunService.capture`'s manual baseline plan construction; demo/factory/wizard construction. A new root field is not automatically preserved by these explicit constructors. Current baseline objects retain references; only frozen documents/detached copies establish historical isolation.

`AnalysisFingerprintService.digest` and `MonteCarloFingerprintService.plan` have explicit allowed root keys. Add the new collection deliberately to effective financial inputs, excluding only approved display/opaque identity fields. Do not remove benefits when sampled death assumptions are moved into context: their scenario dependence remains financial. Empty omission must preserve original configured documents/digests and V1/V2-compatibility/V3/V4 MC contracts. Benefit edits advance revision/freshness normally; projection swaps and Save As preserve original UUID/digest associations.

Reuse frozen-plan strings and existing full projection/analysis snapshots where sufficient. `RetainedProjectionSnapshot.Annual` already stores income-source attribution and completed financial values. Do not rerun an event model merely to display a historical result. If new per-benefit annual lineage/tax facts cannot fit the existing attribution shape, perform a bounded DTO compatibility checkpoint before selecting optional fields or a version; do not silently omit material cash flows. A new domain field alone is not a reason to bump every snapshot or global algorithm tag. Selective capability/algorithm identification may be needed for results that actually use new benefit semantics; original result interpretations remain intact.

Preserve Phase 19's strict-creator lesson: adding an optional Java record component does not automatically make it optional under `AnalysisSnapshotCodec.FAIL_ON_MISSING_CREATOR_PROPERTIES`. Test exact old wire/digests and introduce only narrow, known legacy defaults/deserialization if necessary. Never globally loosen unknown-field, digest, ownership, size, numeric or association validation. No new stored version is selected or implemented in this audit.

## 15. UI/editor recommendation

Choose a staged hybrid of A/C/E from the UI alternatives: keep the current Income workspace and existing Pension/Social Security editors; extend the existing Add-type choice with an appropriately named conditional-income/benefit editor when its engine is supported. A real pension keeps the familiar separate survivor dollar field. A survivor-only convenience mode may populate zero primary amount only for a genuinely compatible pension and must explain shared dates/COLA/tax treatment.

The conditional editor should progressively reveal trigger person/date/age, living recipient, amount/frequency, end condition, escalation and supported tax character. Product premiums, contract balance/surrender values and riders remain hidden unless a later specialized type is selected. Do not add a new Insurance workspace simply to accommodate the first survivor income case; reassess it only once real contract holdings justify a distinct lifecycle.

Preserve MainWindow workspace navigation and the shared Close-to-Summary contract. Income list operations currently commit when their modal dialog accepts; no fake whole-screen Cancel rollback. Modal Cancel preserves the underlying plan; New Plan wizard edits its isolated draft and must reuse the specialized editor through that draft. Keep existing RequiredFieldSupport handles/conditional observables, InputHelp, owner-aware dialogs, persistent action bars/scrolling, Light/Dark and 900×650. Labels should distinguish scheduled/conditional income, premium spending, accessible contract value, surrender value and non-liquid benefit base. Current-income summaries must not display an untriggered death benefit as current guaranteed income.

PDF/Results/Yearly Details should disclose nonempty original benefit definitions and selected receipts compactly, using frozen inputs/annual attributions. Preserve the Key Assumptions column and Phase 17/18 View/Save/Cancel/default-directory behavior. No new screens or editor changes are implemented here.

## 16. Official product comparison and design lessons

Public documentation was researched on the audit date. Confirmed below means the official page describes the feature, not that Aledrix should reproduce its calculations or that every product edge case is supported. No competitor application was executed.

| Product / documented capability | Evidence and Aledrix lesson |
|---|---|
| Boldin pensions | Documents monthly and lump-sum pensions, recipient, age boundaries, COLA, tax selection, survivor continuation and account deposit for lump sums. Lesson: distinguish stream versus receipt and make tax/destination visible. Does not establish independent zero-primary survivor-only support. [Pension documentation](https://help.boldin.com/en/articles/5805678-how-to-enter-a-pension-in-your-plan) |
| Boldin annuities | Documents current income annuities and future purchases with chosen funding account and purchase/income ages. It also discloses nonqualified-tax and IRA RMD modeling limitations. Lesson: pair funding and income and state unsupported details explicitly. [Annuity documentation](https://help.boldin.com/en/articles/5805679-how-to-enter-an-annuity-in-your-plan) |
| Boldin MYGA / death benefit / LTC | MYGA page explicitly describes a savings/lump-sum/transfer workaround, not a dedicated contract. Spouse-loss page suggests a life-insurance windfall. LTC page describes an expense-reduction assumption and separate premium expenses, not verified contractual reimbursement. Lesson: label approximations rather than presenting them as full product accounting. [MYGA](https://help.boldin.com/en/articles/5804542-how-do-i-enter-a-myga), [spouse loss](https://help.boldin.com/en/articles/8117023-modeling-the-loss-of-a-spouse), [LTC assumptions](https://help.boldin.com/en/articles/5847824-what-are-the-long-term-care-assumptions) |
| RightCapital annuities | Distinguishes asset and income-only entries and warns against duplication. Documents fixed/variable/indexed holdings, annuitization, certain/life options and an income-rider benefit base distinct from account value. Lesson: separate accessible assets, guarantees and payouts; do not implement every rider in the first phase. [Annuities](https://help.rightcapital.com/data-entry/net-worth/investments/annuities1/annuities) |
| RightCapital term life | Separates owner, insured, household beneficiary fraction, premiums and coverage end; includes first/last-to-die choices and excludes child/grandchild proceeds from household projections. Lesson: roles and household inclusion must be explicit. [Term life](https://help.rightcapital.com/data-entry/net-worth/insurance/term-life-insurance) |
| RightCapital cash-value life / LTC | Whole-life entry includes cash value approaches/distributions separately from death benefit and premiums. LTC entry has purchase/premium timing, tax setting and daily benefit parameters. These pages confirm specialized product inputs, not unlimited insurer mechanics. [Whole life](https://help.rightcapital.com/data-entry/net-worth/insurance/whole-life-insurance), [LTC](https://help.rightcapital.com/data-entry/net-worth/insurance/long-term-care-insurance) |
| ProjectionLab pensions / milestones | Official defined-benefit article describes salary-linked pension contributions and payout dates by age/calendar/milestone; explicit timing constructs are useful. [Defined benefit pension](https://projectionlab.com/help/defined-benefit-pension), [age milestones](https://projectionlab.com/help/specific-age-milestone) |
| ProjectionLab variable lifetime annuity | Its dated December 2024 help article describes an upfront/recurring expense plus custom income approximation and acknowledges incomplete product complexity. Treat this as the documented workflow, not proof of today's complete annuity-contract capability or absence of newer features. Survivor-only, cash-value insurance and LTC contract support were not established by these sources. [Annuity article](https://projectionlab.com/help/variable-lifetime-income-annuity) |

The applicable lesson is a small condition/payment foundation with explicit specialized contract boundaries, not copying a competitor's workaround into Aledrix's tax engine. Exact competitor zero-primary survivor acceptance and detailed legal/accounting correctness remain unverified.

## 17. Major risks, invariants and independent oracles

| Risk | Required invariant / test oracle |
|---|---|
| Pension regression | No new-benefit plan preserves old pension monthly/COLA/date/SS values exactly; approved B0 correction isolated with old-versus-corrected tax characterization. |
| Income double counting | Receipt + withdrawal + opening cash = spending + premiums + taxes + retained cash + funded holdings; one premium/deposit/benefit entry per economic transaction. |
| Survivor misallocation | Primary/spouse reverse cases, both/one/neither alive; actual recipient sources sum to household payable amount; no absent-person or external money added to household. |
| Timing/event duplication | Years before/at/after trigger, opening date, leap birthday, equal deaths, coverage endpoints; repeated same-year evaluation identical and no portfolio mutation. |
| Tax/basis errors | Gross = taxable + excluded; cumulative recovered basis bounded; direct independently computed taxable/excluded oracle; tax-funding/Medicare retries use the same receipt. |
| Account/RMD/Roth interactions | Same-owner valid destinations, legal transfer classification, prior-December balances and no RMD dollars converted; funding one contract reduces only its valid sources once. |
| MC mortality inconsistency | Same supplied scenario yields same trigger as deterministic context; no additional death RNG. Fixed seed/no-new-benefit output and Phase 21 streams remain exact. |
| Paired comparison noise | Identical plans under shared worlds yield identical conditional flows/results; each side's edited benefits remain independent; expense correspondence unchanged. |
| Snapshot/fingerprint drift | Legacy absent/null/empty round trips and exact digests; new edits stale correct analyses; immutable owner/run/projection IDs; Save As/restart/swaps never recompute. |
| Historical reinterpretation | Restore after current benefit/premium edits with throwing engines; original yearly amounts/tax sources/report context unchanged. |
| Post-death estate promises | First-death living-spouse payment modeled in correct year; second-death/external/guarantee settlement explicitly unavailable until approved terminal metric exists. |
| Cash value vs benefit base | Account/surrender/base/death values separately classified; annuitization/claim removes appropriate old value; net worth never adds contingent face value or benefit-base rollup as investable wealth. |
| UI complexity | Ordinary pension unchanged; conditional fields required only when active; invalid Save retains drafts; modal/workspace/wizard lifecycle tests and actual compact Light/Dark previews. |

## 18. Proposed bounded implementation phases

The provisional sequence needs one prerequisite and earlier scenario integration than a UI-first rollout. **Each row requires separate approval.**

| Phase | Scope / gate |
|---|---|
| **22B0 — Existing pension cash/tax consistency** | Separately authorize the reproduced ordinary/lifetime discrepancy. Establish correct selected pension facts for cash, federal/state tax, Roth and funding; retain original snapshot/history values. Level 3 full regression and explicit financial acceptance. Not an audit implementation. |
| **22B1 — Domain/persistence and compatibility checkpoint** | Immutable bounded definitions/roles/trigger variants, optional empty collection, validation, exact legacy omission, complete reconstruction inventory, fingerprints and strict DTO compatibility. Decide destination identity and supported tax characters first. No partially supported public execution. Level 3. |
| **22B2 — Authoritative financial integration** | Pure per-year evaluator; date/age/named-death recurring and first-death living-recipient lump receipts; gross/taxable/settlement integration and no duplicate retries. Full deterministic and equivalent explicit lifetime-context tests before UI. Reject unsupported terminal/external/product cases. Level 3. |
| **22B3 — Longevity/Monte Carlo and provenance** | All eight Phase 21 modes, paired candidate isolation, weighted scenario coverage/equivalence, original fingerprints/report assumptions, fixed-seed absence compatibility, cancellation/storage and history. No new random streams. Complete before public UI rollout; Level 3. |
| **22B4 — Editors and truthful initial reporting** | Existing Income workspace type choice and wizard draft integration, conditional fields/tooltips/requiredness, original-input summaries/PDFs, normal Apply/Cancel/Close semantics. Level 2 if pure wiring, escalated if financial mapping changes; native acceptance. |
| **22C1 — Term contracts and premiums** | Insured/beneficiary/coverage, deterministic premium schedule and explicit lapse/funding policy; narrow eligible death proceeds and proper tax/household destination. Level 3. |
| **22C2 — Basic funded income annuities / guarantees** | Purchase/annuitization transactions, qualified versus nonqualified boundaries, basis and periodic tax treatment; period guarantees only within approved recipient/terminal capabilities. Level 3. |
| **22C3 — MYGA / surrender** | Separate contract accumulation/basis/liquidity and surrender accounting with authoritative tax validation; no account-value/benefit-base conflation. Level 3. |
| **Later separately approved work** | Indexed/rider formulas, permanent life loans/lapse/MECs, external/estate settlement and LTC reimbursement triggers. Do not imply these are included in initial 22C. |
| **22D — Reporting/reconciliation/closure** | Full product capability matrix, immutable historical/PDF verification, resource limits, complete regression and native acceptance; no automatic expansion. |

Highest-priority survivor-only recurring income and independent survivor amounts need less model work than assumed, but B0 is a prerequisite to trustworthy use. Death-triggered lump receipts and deferred date/age income follow. Fully funded contracts cannot precede funding/tax correctness merely because their payment stream is easy to approximate. Moving scenario verification before public editor rollout avoids users entering benefits that a supported analyzer silently ignores.

## 19. Explicit decisions requiring approval

1. Approve a separate investigation/correction scope for the reproduced pension cash/tax divergence before new financial features rely on it; no blanket authorization to change tax laws or existing historical values.
2. Approve the staged hybrid, preserving existing pension meaning and using a small bounded conditional record/evaluator rather than a universal event DSL or immediate insurance workspace.
3. Approve initial living PRIMARY/SPOUSE recipients and first-death focus; defer external/estate and after-second-death guarantees until terminal metrics/settlement are defined.
4. Approve January 1 scenario deaths, explicit age-to-date conversion, inclusive coverage/month conventions and the proposed projection-opening cutoff for **new types only**. Decide same-day/annual payment/leap-day details in B1 tests.
5. Decide whether initial lump receipts may use household retained non-qualified cash or require a specific legally owned account. If named accounts are mandatory, approve the stable-reference foundation first.
6. Approve explicit gross/taxable classification and a restricted supported tax set. No automatic nonqualified basis, qualified transfer, Roth qualification, insurance exclusion or state pension exemption without its validated contract.
7. Approve one accessible asset per contract, separate non-liquid benefit base/contingent death guarantee, and explicit purchase/annuitization removal. No negative-expense or noninvestable-asset shortcuts advertised as complete products.
8. Approve optional empty persistence with no old-plan rewrites, explicit reconstruction/fingerprint classification, and a narrow snapshot compatibility checkpoint rather than automatic version bumps.
9. Approve the adjusted phase ordering with scenario/MC verification before public UI rollout, and the separate product/tax acceptance gates above. No later phase starts automatically.

## 20. Focused verification and research limitations

Used Java 25, required IntelliJ bundled Maven, workspace `.codex-m2/repository` and `-DargLine=-Xmx4g`. Existing selection:

```text
PensionTest, SurvivorFinancialMechanicsTest, SurvivorMechanicsCharacterizationTest,
DeterministicDeathCharacterizationTest, HouseholdCashSettlementProjectionTest,
IncomeSourceAttributionProjectionTest, IncomeSourceAttributionTest,
TaxIncomeCalculatorTest, TaxIncomeSourceAttributionTest, SinglePersonCoreProjectionTest,
SinglePersonPlanPersistenceTest, PensionLayoutAndRequiredNameTest, IncomeWizardStepTest,
SupportedStandaloneExpenseTest, SupportedPairedExpenseTest,
MonteCarloSurvivorAssumptionTest, CoupleSocialSecurityPersistenceTest
```

`target/phase22a/focused-verification.log`: **BUILD SUCCESS; 160 tests, 160 passed, 0 failures, 0 errors, 0 skips**, 29.867 seconds, completed November 5, 2026 at 19:26:25 Eastern. These passing characterization tests verify current contracts; they do not negate the separate newly reproduced ordinary pension tax discrepancy. The ignored synthetic probe is diagnostic evidence, not extra JUnit cases or a changed test suite. It uses only existing synthetic fixtures and temporary outputs; no real plan is read.

No full long-running financial, production-scale Monte Carlo or exhaustive analyzer suite was run: this phase changes only documentation and the user explicitly requested focused audit verification. No permanent test was disabled, edited or weakened. Financial implementation phases above require Level 3; UI-only later phases use Level 2 with escalation on mapping/identity changes.

Official public documentation research was available. Competitor claims are restricted to linked pages; hands-on competitor behavior, undocumented edge cases and insurer illustrations were not tested. The tax links are starting authoritative references, not a completed legal implementation specification. Native UI previews of future editors were not produced because no editor was implemented.

## 21. Git preservation and approval gate

Starting branch/HEAD and clean status are recorded in `target/phase22a/initial-head.txt` and `initial-status.txt`. Accepted Phase 21 work is in the current committed source. Final branch/HEAD are unchanged; the index is empty and this report is the sole Git-visible addition. Ignored probe/classes/logs stay under `target/phase22a`. Production code, test sources, schemas, build configuration, historical artifacts and user files are unchanged. `git diff --check` passed; the new report was separately scanned for trailing whitespace. Final status is recorded in `target/phase22a/final-status.txt`.

Stop after Phase 22A. No 22B/22C/22D implementation, new screen, insurance product or financial correction is authorized by this report. Nothing was staged, committed, pushed, reset, cleaned or reverted. **Request approval of the architecture, priorities, event timing, tax/destination boundaries and implementation sequence, including the separately gated pension consistency prerequisite, before production changes.**
