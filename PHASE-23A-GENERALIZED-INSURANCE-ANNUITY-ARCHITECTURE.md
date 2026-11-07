# Phase 23A — Generalized Insurance and Annuity Capability Architecture Audit

Audit date: November 6, 2026. Application: Aledrix Retirement Planning. Branch: `main`. Audited HEAD: `7db85b2e67066d73539fc68291e3df50d20730ee`.

## 1. Executive summary

Recommend **minimal tax/receipt enhancements first, followed only as needed by generalized asset primitives**. Aledrix does not currently need a universal insurance-policy or annuity-contract engine. Its existing pure conditional-benefit evaluator, common projection engine, mortality contexts, source attribution and household cash settlement are suitable foundations.

Three improvements offer the clearest initial planning value: restricted qualifying life-insurance death receipts; contractual premiums that stop on the insured's death without a household spending reduction; and quoted nonqualified annuity payments with validated taxable/basis splits. These are proposals, not current capabilities. Every existing Phase 22 conditional receipt remains fully taxable ordinary non-pension income.

MYGAs and other accumulating contracts are a different problem. Accurate intermediate value, tax deferral and restricted liquidity require a bounded deferred holding with basis and maturity rules. A future purchase also needs a conserved funding transaction. Do not present an ordinary account plus a maturity windfall as equivalent support. Keep initial MYGA workarounds explicitly illustrative; build the generalized holding only if its additional planning value is wanted.

Two source findings affect the minimum scope. First, current recurring expenses have no insured-person dependency and all are reduced by the household survivor-spending factor after a death. Second, growth of retained nonqualified cash is not automatically reported as taxable investment income. Excluded principal support must therefore disclose the existing investment-tax approximation and distinguish any taxable interest actually supplied with an insurance settlement.

No financial product is certified as completely supported merely because one payment can be entered. Household-only recipients, supported-state admission, January 1 death timing, existing terminal-estate horizons and frozen historical semantics remain firm boundaries. This audit makes no production changes and chooses no new snapshot version.

## 2. Scope and source inventory

Evidence categories used throughout:

- **Verified current:** inspected Java implementation, including admission and serialization contracts.
- **External documentation:** official public product/help/tax sources linked near the relevant statements; researched November 6, 2026. This was documentation research, not hands-on competitor testing.
- **Proposed:** architecture requiring a separate implementation authorization and regression gate.
- **Approximation:** a deliberately limited planning representation, not full product support.
- **Unsupported:** no safe complete calculation under the current financial contracts.

Reviewed the relevant portions of Phase 6 state-tax reports, Phase 11 PDF architecture, Phase 12 history/association/freshness, Phase 13 settings, Phase 14 stochastic expenses, Phase 19 codec compatibility, Phase 21 supported stochastic execution, and Phase 22A/B0/B1/B2/B3/B4. Earlier B1/B2 reports describe intentionally blocked analyzers that B3 subsequently enabled. Current source, not those intermediate restrictions, governs this audit. Section 22 lists the exact source/report inventory.

No real retirement plans were accessed. Source inspection was sufficient to establish the current contracts; no financial, Monte Carlo or full Maven suite was run for this documentation-only phase.

## 3. Source-grounded existing capability table

Java paths in this section are relative to `src/main/java/com/daviddunn/retirementplanner/`.

| Capability | Existing class/component and execution reference | Current support | Gap | Recommendation |
|---|---|---|---|---|
| Recurring premium expenses | `domain/financial/Expense`, `ExpenseType.RECURRING`; `ProjectionEngine.calculateProjectedExpenses` | Annual nominal amount, date overlap, General/Healthcare/None escalation, opening-year proration | No insured/payer role; any-death survivor factor affects all recurring items; no precise premium due-month schedule | Dated premium budget approximation now; add explicit life-contingent contractual expense policy before claiming scenario-correct term premiums |
| Conditional survivor income | `ConditionalBenefitEvaluator.evaluate`, `EffectiveHouseholdDeathView` | Monthly income after named death to living other household member; independent amount | Only fully taxable non-pension treatment; no external or estate recipient | Reuse unchanged timing/evaluation boundary |
| Death-triggered lump sums | Same evaluator and `ConditionalBenefitReceipt` | One event-year taxable receipt within inclusive coverage; no pre-opening replay | Not legally qualified insurance exclusion; no asset replacement | Add restricted receipt treatment, not a new mortality engine |
| Tax-excluded receipts | `ConditionalBenefitDefinition.TaxCharacter`, receipt constructor, `TaxIncomeAttribution.attach` | **Not supported** for conditional benefits; receipt constructor requires taxable=gross and excluded=0 | Definition, receipt validation, legal eligibility, state mapping and authenticated result capability all need extension | Restricted qualifying death-principal class first; taxable settlement interest separate |
| Partially taxable income | `IncomeSourceAttribution` has gross and modeled taxable facts | Infrastructure can carry separate facts, but current conditional receipt cannot have a split | No annuity basis/exclusion accounting or legal classification | Bounded quoted-payment/basis adapter with finite recovery ledger |
| Pension survivor benefits | `Pension`; `HouseholdPensionIncomeCalculator.calculateWithSources` | Independent survivor dollars, including zero owner payment; B0 shared cash/tax selection | Not a generalized annuity qualification or funding model | Preserve pensions; do not grant annuities pension exemptions by relabeling |
| Asset accumulation | `ProjectedPortfolio.applyGrowth/withGrowth`, projection return path | Ordinary projected investment growth and retained cash | No insurer-specific guaranteed rate, contract basis or renewal rules | Separate fixed-crediting policy only for a future deferred holding |
| Illiquid assets | `NonInvestableAsset`, separate asset projection | Net-worth valuation/growth separate from investable funds | No liquidation, maturity cash flow, surrender or beneficiary settlement | Manual valuation only; not a spendable contract simulation |
| Contract surrender value | No contract-specific component | Unsupported | Contract, surrender and accessible values are conflated if entered as an ordinary account | One holding with distinct valuation/access attributes; never three additive assets |
| Qualified/nonqualified basis | `Account`, `AccountType`, `TaxTreatment` | Broad cash/taxable/deferred/Roth classes | No nonqualified annuity investment-in-contract or general IRA/Roth basis-history ledger | Explicit qualification/basis gates; do not borrow another account type's tax rules |
| Funding transfers | Projected balances; Roth conversion's specialized same-owner transfer | Existing Roth mechanics only; ordinary withdrawals/expenses are not generic exchanges | No persistent account identifiers or future purchase exchange primitive | Stable account references and balanced funding event before account-linked purchases |
| RMD | `OwnerRmdCalculator`, `RmdBalanceAggregator`, account eligibility | Existing IRA/employer/inherited rules using prior projected December 31 balances | No QLAC excluded balance or annuitized payment coordination | Separate qualified-annuity subphase; preserve current RMD formulas |
| Beneficiary routing | Conditional PRIMARY/SPOUSE recipient; household roles | Living modeled person, distinct from deceased trigger; absent spouse rejected | No adult child, trust, estate or inherited-contract continuation | Keep household-only cash admission |
| Second-death estate settlement | `EstateAtSecondDeathCalculator.calculate`, `AfterTaxEstateCalculator` | Established prior-December-31 investable estate and tax-deferred haircut | Not a post-final-death claims ledger or estate/inheritance tax calculation | Separate optional estate-report event later; do not change the existing metric silently |
| Historical projection compatibility | `CompleteProjectionSnapshot`, codec, immutable analyzer associations | Original documents, results, digests and associations retained | Future new tax/value facts need exact capability/version validation | Explicit checkpoint per extension; no automatic upgrading |
| Monte Carlo support | `MonteCarloAnalyzer`, `MonteCarloStrategyComparisonAnalyzer` | All eight generated couple/single fixed/longevity configurations with current taxable benefits and optional expense volatility | Benefit-bearing supplied worlds/paths remain unsupported; new insurance tax/value classes unverified | Reuse scenario deaths and engine; qualify every new capability before admission |

Current `RetirementPlan` owns one immutable validated benefit collection. `Person` retains conventional income sources; benefits do not duplicate into both persons. `ConditionalBenefitsPane` and `ConditionalBenefitDialog` already provide complete supported list/editor workflows. The dialog explicitly warns that qualifying life-insurance proceeds have different rules not modeled by this feature.

## 4. Product support matrix

These classifications concern the whole planning scenario, not insurer administration. Common current source anchors: **CB** = conditional-benefit definition/evaluator/receipt; **EX** = Expense/projection expense calculation; **AC** = Account/ProjectedPortfolio; **NI** = NonInvestableAsset; **PN** = Pension/source-selected pension calculator. Tax and legal boundaries are detailed in section 9.

| Product | Current planning scenario/workaround | Accuracy and tax restrictions | Liquidity and beneficiary restrictions | Smallest safe enhancement | Whole-product classification |
|---|---|---|---|---|---|
| 1. Term life | EX dated premium budget; CB death-trigger timing/coverage and living spouse receipt | Current receipt fully taxable; premiums not insured-contingent and may be survivor-reduced | No contract value needed, but external/final-death proceeds cannot enter household cash | Restricted excluded death principal plus precise insured-contingent premium policy | **Small reusable extension required**; timing alone is not current insurance support |
| 2. Whole life | NI user-supplied valuation, EX premium budget; CB can illustrate a taxable payment | No policy basis, dividend/loan/MEC/surrender accounting; no automatic value replacement | NI not spendable; ordinary AC falsely implies liquidity; full benefit plus retained cash value double-counts | Restricted illustration-based holding and replacement settlement, if demanded | **Approximation possible with disclosure**; complete contract modeling deferred |
| 3. Universal life | Same manual valuation/cash-flow inputs | No cost-of-insurance/lapse/premium sufficiency or death-option modeling | Accessible value/death settlement not linked | Generalized valuation/settlement primitive with externally supplied assumptions only | **Approximation possible**; contractual guarantees unsupported |
| 4. Indexed universal life | User-supplied illustrated value only | No index crediting, caps, participation, charges, lapse or guarantees; global market return is not an IUL formula | Same replacement/liquidity gaps | No immediate product implementation; explicit external illustration stress cases only | **Substantial specialized modeling** for product fidelity; defer |
| 5. Variable universal life | User-supplied valuation or separate stress scenario | Ordinary portfolio return cannot establish policy charges, tax qualification or lapse | No policy loan/surrender ledger or insured settlement | No new product type now | **Substantial specialized modeling**; defer |
| 6. SPIA | CB quoted fully taxable monthly cash-flow leg, living recipient; PN only a clearly labeled pension-like approximation | No nonqualified exclusion ratio; qualified RMD coordination not established; pension state classification not interchangeable | Purchase must already be outside opening assets; no period-certain external continuation | Quoted annuity tax/basis adapter; funding event for future purchase | **Small reusable extension** for bounded in-force quotes; current inputs cover only a restricted payment leg |
| 7. DIA | CB date/age deferral and living-recipient payment; PN deferred stream | Same tax/qualification gaps; age of another triggering person is not proof that the annuitant survives | No funded purchase, refund guarantee or external continuation | Same adapter plus explicit annuitant survival where distinct from recipient | **Small extension for restricted single-life quoted stream**; broader joint contracts deferred |
| 8. QLAC | CB can illustrate delayed income only | Not QLAC-qualified; no premium admission, excluded RMD value or qualified distribution coordination | Account ownership/beneficiary and guaranteed refund options absent | Dedicated bounded qualification/RMD adapter after funded-asset foundation | **Unsupported today; specialized qualified integration required** |
| 9. MYGA | NI value estimate or isolated manual maturity illustration | No contract-specific tax-deferred crediting, basis or earnings-first withdrawal; maturity windfall can double-count principal | No lock/surrender/maturity transfer | General fixed-rate deferred holding, not insurer-specific engine | **Generalized asset extension required**; approximation is not complete support |
| 10. Fixed deferred annuity | Same valuation/quote approximations | No declared-rate changes, qualification/basis or distribution rules | No accessible/surrender distinction or renewal action | Fixed-term subset of deferred holding; reject unsupported renewal/withdrawal cases | **Generalized asset extension required** |
| 11. Fixed indexed annuity | User-supplied guaranteed/income quote leg, not index result | No crediting, reset, spread/cap or rider accounting; benefit base not investment value | Missing access limits and coupled death/withdrawal guarantees | Allow isolated quoted-payment approximation only after tax adapter | **Substantial specialized modeling** for whole product; defer |
| 12. Variable deferred annuity | Ordinary AC can illustrate market exposure but is not an annuity | Missing tax deferral, fees, basis and death guarantees | Withdrawals and insurer guarantees not modeled | Deferred holding could later accept an external value path; no present promise | **Substantial specialized modeling**; illustration-only approximation |
| 13. GLWB annuity | CB can show a quoted income amount | Cannot show rider withdrawal, account depletion and continuing guarantee consistently; no benefit-base accounting | Keeping full account plus independent income may manufacture wealth | Coupled account/withdrawal/guarantee model only on demonstrated demand | **Should remain unsupported as a whole product** |
| 14. LTC insurance | EX premium and healthcare-expense stress budget | No morbidity, ADL/qualification, waiting period, daily cap, reimbursement or tax-qualified benefit logic | No claims recipient/service settlement | Explicit expense stress scenarios, not claims simulation | **Approximation possible for expense planning; insurance claims deferred** |
| 15. Hybrid life/LTC | Separate manually labeled stress cases | Accelerated LTC benefits can reduce death value; independent benefits would double-count | Cash value, residual death benefit and external settlement missing | Requires coupled benefit-limit/asset model | **Should remain unsupported** absent demonstrated demand |

No carrier guarantee, return or tax qualification is inferred from a product name. No new product is implemented by this audit.

## 5. Life-insurance approach

For simple term coverage benefiting a living spouse, reuse the existing death-triggered CB schedule, existing mortality event, and cash settlement. Add only the missing restricted tax treatment and contractual premium eligibility. Insured, owner, premium payer and recipient are different roles; initial implementation can restrict supported ownership/qualification instead of inventing arbitrary persons.

Federal rules generally exclude qualifying death proceeds but distinguish taxable settlement interest and transferred-policy restrictions. That supports a narrow qualified-principal receipt, not a general user-selected tax-free switch. See the [IRS life-insurance FAQ](https://www.irs.gov/faqs/interest-dividends-other-types-of-income/life-insurance-disability-insurance-proceeds). Employer-owned policies have additional statutory conditions; exclude them initially unless separately validated. See [IRS Bulletin 2009-24, employer-owned contracts](https://www.irs.gov/irb/2009-24_IRB). Reportable policy sales and transfer-for-value exceptions need qualification review rather than a blanket checkbox; see [2019 final regulations](https://www.irs.gov/irb/2019-47_IRB).

Proposed initial admission: documented ordinary personal coverage, qualifying death principal, no policy sale/assignment exception requiring unsupported analysis, living modeled other-person recipient, and individually validated state treatment. Reject unknown qualification rather than infer exclusion. Record the supplied qualification facts and original assumptions in frozen inputs. Do not turn saved Phase 22 death-benefit definitions into excluded policies.

| Beneficiary | Current meaningful household treatment | Proposed boundary |
|---|---|---|
| Primary after spouse death | CB recipient PRIMARY if living; currently fully taxable | Restricted excluded principal when legally qualified |
| Spouse after primary death | CB recipient SPOUSE if present/living; currently fully taxable | Same |
| Adult child or trust | No modeled household receipt destination | Optional later report-only event; never household investable cash |
| Estate | No supported estate claim settlement | Keep separate from current retirement/terminal-estate metrics |
| No eligible modeled beneficiary | No household receipt | Explicitly unsupported household payment; do not fabricate spouse |

First-death proceeds can affect the existing living-household years without horizon changes. At final death there is no eligible living household recipient. An external claim is economically different from retirement cash and must not rescue a portfolio-failure metric.

Excluded principal would fund spending/taxes and retained assets without directly increasing AGI. It can indirectly change withdrawals, conversions, taxes and later balances. Do not promise automatic taxation of reinvested yields: `ProjectionEngine` supplies zero modeled taxable interest to its ordinary tax-funding call, and retained growth is not a realized-income ledger. The feature must disclose this inherited approximation. Interest explicitly paid by the insurer is a separate taxable source, never part of excluded principal.

## 6. Cash-value policy approach

Current NI valuation can display an externally supplied cash-value estimate, with explicit disclosure that it is not spendable and does not settle on death. An ordinary brokerage or savings account is unsuitable if it would become freely withdrawable, receive market rather than contract returns, or lose basis/qualification facts. Combining either with a full CB death receipt leaves the old value in place and can overstate wealth.

A later restricted holding should have **one economic value** with separate contract value, net surrender value and allowed withdrawal value. Basis is a tax attribute; death benefit and income benefit base are contingent rights, not additional current assets. The death event consumes the applicable old holding before crediting its settlement value. A policy with an explicitly additive death option requires its documented formula; never assume every policy adds cash value to face amount.

User-supplied annual guaranteed/assumed value schedules could support transparent planning without reproducing policy administration. Such schedules require scenario assumptions, surrender limits and a replacement rule; they cannot establish lapse protection. A surrendered policy may produce taxable excess over its cost; [IRS Publication 525 (2025), life-insurance proceeds](https://www.irs.gov/publications/p525) establishes why premium total, cash value and tax basis must not be treated as interchangeable.

Defer loans, MEC distribution rules, dividends used for paid-up additions, detailed cost-of-insurance deductions, lapse, indexed crediting and variable subaccount charges. Their interactions can materially alter tax/coverage; label external illustrations as assumptions, not computed contractual guarantees.

## 7. Income annuity approach

Choose **a combination of B and C** from the user's alternatives: reuse the conditional schedule for a quoted non-pension payment, extend its tax facts narrowly, and link a future funded purchase only after a conserved funding primitive exists. Do not expand Pension into an omnibus product or add an independent parallel income calculator.

For an already purchased single-life quote with no residual accessible asset, users can supply monthly gross payment, commencement, recipient/annuitant, escalation and applicable tax information. Aledrix need not calculate insurer pricing. Existing CB date/age triggers and living-recipient termination cover the simple same-annuitant/same-recipient case. Joint-life changes need an explicit both-alive/first-death payment-state definition; adding separate date and death streams without reducing the original payment can duplicate income. Existing PN survivor elections are useful mechanics evidence, not permission to apply pension-specific taxation to annuities.

Nonqualified periodic payments need validated recovery of investment in the contract. Recommend an initial provider-supported fixed excluded amount per payment and remaining recoverable basis, with taxable remainder and a finite recovery cap, only for a verified tax method. Do not implement an arbitrary percentage exclusion forever. The General Rule and Simplified Method are not interchangeable. See [IRS Publication 939 (December 2025)](https://www.irs.gov/publications/p939) and [Tax Topic 411](https://www.irs.gov/taxtopics/tc411). A quote avoids actuarial pricing, not tax validation, survivor-basis rules or payment guarantees.

Initially exclude period-certain continuation after final death, refund beneficiaries outside the household, variable-payment basis recovery and unverified COLA/tax interactions. A date-bounded monthly stream does not by itself authenticate a period-certain insurance guarantee. Qualified IRA-funded annuities require separate distribution/RMD validation, even if every payment is taxable. QLAC is a restricted qualified contract, not a synonym for DIA: IRS instructions identify special reporting, commencement and RMD-balance treatment. See [Form 1098-Q instructions, April 2025](https://www.irs.gov/instructions/i1098q). Do not hard-code current premium limits in domain logic.

## 8. MYGA and deferred annuity alternatives

### Representative Aspida case and research limits

Aspida's official Synergy Choice MYGA disclosure identifies long-term accumulation, issuer claims-paying guarantees, possible early surrender/withdrawal charges and contract/state-specific terms. The indexed official disclosure is available, but direct page rendering exposed only JavaScript infrastructure in this environment. It has no verified publication date; retrieved November 6, 2026. No current carrier rate, surrender percentage, MVA formula, renewal default, free-withdrawal allowance or particular death option is asserted. See [Aspida's official disclosure](https://www.aspida.com/synergychoicemygadisclosure). The [official rates page](https://aspida.com/financialprofessionals/rates) likewise did not provide a readable current rate sheet. Third-party hosted dated brochures were not used as current authoritative specifications.

Accordingly this is a generalized modeling comparison, not a Synergy Choice quotation. There is no assumed 5% guarantee. For a user-validated fixed annual compounding assumption, the simple maturity oracle is `V(n) = P × (1+r)^n`, before charges/withdrawals; this is a proposed mathematical test case, not a statement of an Aspida contract's actual crediting convention.

| Alternative | Planning value | Current feasibility and accuracy | Decision |
|---|---|---|---|
| A. Manual cash-flow approximation | A maturity-date sensitivity illustration with little development | Aledrix lacks the exact per-account zero-growth/exclude-from-withdrawals/transfer combination used in some competitor workarounds. Existing cash can be spent before maturity; NI values cannot become cash automatically; an added full maturity receipt can duplicate principal; current CB taxes principal too | Allow explanatory isolated examples only. Do not market as accurate MYGA support |
| B. Generalized fixed-rate deferred holding | Correct intermediate value, restricted access, basis, maturity and source funding within a narrow contract subset | Material integration across return model, withdrawals, taxes, estate, snapshots and analyzers; feasible without issuer-specific engine | Preferred eventual support **if** that value justifies the cost; separate approval after receipt work |
| C. Product-specific engine | Exact rider/surrender/MVA/renewal options for selected contracts | High continuing documentation, tax, regression and maintenance cost; brittle across state/form/version | Defer; no present justification |

Proposed B minimum: holding identity/owner, opening value, documented fixed rate/crediting interval, term, tax qualification, nonqualified basis, maturity action, explicit accessible value and simplified supported surrender policy. Start with hold-to-maturity/no early withdrawal if desired; a liquidity shortfall must remain a shortfall. If a selected spending strategy demands an unsupported surrender, report it rather than release fictional cash.

For a nonqualified cash maturity, remove the holding, recover principal/basis once, recognize the correct taxable gain and settle net funds once. Qualified maturity/rollover is a different transaction. Initially reject automatic renewals, exchanges, variable crediting and annuitization actions until each is supported. A fixed deferred guarantee must not be driven by simulated market returns or an income-benefit-base rollup. Issuer credit/default risk remains a disclosed modeling assumption, not a government guarantee.

## 9. Tax-treatment architecture

Current facts are explicit: `TaxIncomeAttribution.attach` aggregates conditional **modeled taxable** source amounts into the runtime non-pension ordinary-income field. `TaxIncome.getOrdinaryIncomeBeforeSocialSecurity` includes that amount; retirement-specific bases remain separate. The closed definition, receipt and execution validation currently permit only gross=taxable. Supporting another treatment changes an authenticated financial contract, not just UI wording.

Minimum proposed receipt facts: gross cash, taxable ordinary component, non-taxable component with a specific legal/model reason, recipient, tax year, source capability, and any basis recovered. For split annuity income, remaining basis is a bounded projected state derived from frozen opening basis and recognized payments; it is not a mutable flag shared among retries. New payment/holding classifications must reach every bracket-fill and tax-funding evaluation from the same authoritative source facts.

Federal distinctions below are planning boundaries, not a complete transaction-tax implementation. [IRS Publication 575 (2025)](https://www.irs.gov/publications/p575) distinguishes nonperiodic nonqualified annuity distributions, including earnings-first treatment for the applicable modern-contract subset, from periodic annuity payments. Special contract dates, penalties, exchanges and qualifications require additional validation; no universal simplified rule is approved here. [IRS Publication 590-B (2025)](https://www.irs.gov/publications/p590b) distinguishes IRA basis and qualified versus nonqualified Roth distributions. Do not assume every Roth-funded contract receipt is qualified merely because the wrapper says Roth.

| Receipt/transaction | Federal AGI and modeled Medicare income | State requirement | Roth/tax-funding/cash/estate consequence | Current status / proposed limit |
|---|---|---|---|---|
| Fully taxable ordinary conditional income | Gross enters AGI, taxable income and existing same-year modeled MAGI | Current MI/IL ordinary treatment, no general-income-tax subset; no retirement exclusion | Taxable gross plus available cash affect bracket fill, withdrawals, taxes and retained estate once | Supported Phase 22 subset |
| Restricted federal-excluded receipt | Excluded principal does not enter AGI; distinguish exclusion reason from tax-exempt interest | Verify each supported state and exceptions before enablement | Gross cash still funds uses; indirect conversion/withdrawal effects; retained assets count once | Proposed legal-qualified class, not generic tax-free income |
| Partially taxable periodic annuity | Only validated taxable part enters AGI; basis recovery separately tracked | Non-pension annuity qualification may differ from current retirement exclusions | Gross funds cash, tax component funds tax loop; basis never below zero | Proposed fixed quote/finite recovery subset |
| Nonqualified deferred annuity withdrawal | Applicable earnings-first gain is ordinary income, not capital gains; gain/basis tracked | New explicit state source classification | Remove holding value as cash is received; surrender charges can make tax and net cash differ | Unsupported; later deferred holding gate |
| Return of nonqualified basis | Not ordinary income to extent valid unrecovered basis | Do not invent retirement subtraction | Decrease basis/holding; cash transfer is not new wealth | Unsupported without basis provenance |
| Traditional IRA-funded annuity distribution | Generally ordinary inclusion; IRA basis can change taxable amount | Existing IRA support does not prove annuity qualification | Coordinate payment, qualified holding depletion and RMD satisfaction; never duplicate IRA withdrawal | Separate qualified-annuity gate |
| Roth IRA-funded annuity distribution | Qualification/history determine taxable treatment | Existing modeled Roth assumptions are not full annuity qualification | Do not create conversion capacity or ignore distribution ordering/history | Separate gate; no blanket exclusion |
| Qualifying life death principal | Proposed excluded principal only after qualification | Explicit new class required, not pension/SS fallback | Cash/retained balance change without direct AGI; indirect taxes remain | Proposed first receipt enhancement |
| Interest paid with death proceeds | Taxable interest distinct from excluded death principal | Explicit taxable-interest treatment | Raises applicable tax/Medicare inputs and available cash | Current CB is not an interest source; explicit adapter required |
| State-specific classification | Federal inclusion is necessary data, not a complete state decision | MI, IL, PA, IA and current no-general-income-tax scope only | Wrong exclusion changes funding, conversions and balances as well as tax | Unsupported mappings must reject explicitly |

The current Medicare calculator uses federal AGI as its same-year modeled MAGI. Actual IRMAA MAGI includes tax-exempt interest; SSA also describes use of available IRS tax information. See [SSA POMS HI 01101.010, December 2, 2025](https://secure.ssa.gov/POMS.nsf/lnx/0601101010). A life principal exclusion is not municipal-bond interest. Do not map all excluded cash into one MAGI-addback category, and do not silently undertake the deferred lookback redesign during this initiative.

### Supported-state audit and safe admission

| Current production implementation | Verified behavior | Implication for proposed insurance/annuity classes |
|---|---|---|
| Michigan: `MichiganProductionTaxAdapter`, `MichiganTaxCalculator`, retirement eligibility/source services | Ordinary-income base and separate retirement eligibility; original participant facts retained for pension selection | New non-pension income must not acquire pension treatment. Validate each exclusion/split category independently |
| Illinois: `IllinoisProductionTaxCalculator.calculate` | Federal AGI base; subtracts known qualifying retirement sources and taxable SS. Unknown pension qualification is conservatively taxed with warning | A CB or quoted annuity name does not establish a retirement subtraction. New classes require explicit qualification/mapping |
| Pennsylvania: `PennsylvaniaProductionTaxCalculator` | Source/category-based calculation; legal pension/retirement/basis qualification can be unavailable | Current conditional plans blocked. Do not substitute interest/pension or use federal AGI as automatic PA answer |
| Iowa: `IowaTaxBaseBridge.reconcile` | Federal bridge plus tightly qualified retirement-source adjustments; missing qualification/history yields unsupported status | Current conditional plans blocked. A new annuity cannot inherit IRA qualifications by label |
| Existing no-general-individual-income-tax jurisdictions | Zero general state-income-tax model under current assumptions | Does not prove every insurance levy, residence rule or other state tax is modeled; disclose the existing scope |

Initial proposals should remain within individually validated MI/IL/no-general-income-tax treatment. No PA/IA expansion is implied. Future implementation must validate relevant state department authority and tax-year datasets; this audit does not claim a newly established state insurance exemption. Unsupported qualification must produce an accurate admission explanation, not silently omit taxable income.

## 10. Premium and funding architecture

An existing recurring Expense can represent a dated fixed premium **budget** using NONE escalation and zero stochastic volatility. It cannot reliably stop at the insured's scenario death, remain unreduced when another person dies, or reproduce all premium due-months. The engine's ordinary opening-year proration and active-year overlap conventions are not an insurance billing schedule. A fixed rider fee paid externally has the same limitations. A charge deducted from contract value is not an ordinary household expense.

Propose the smallest typed contractual-expense policy: named insured death eligibility, coverage/due interval, explicit calendar-month recognition if chosen, fixed nominal amount, and exemption from the household survivor-lifestyle reduction. Link by insured, not owner/payer by assumption. Do not change behavior for legacy ordinary expenses or allow an unexplained stochastic premium. Premium payment failure/lapse is initially unsupported unless expressly modeled as a disclosed coverage assumption.

**Compatibility hazard:** `ExpenseDeserializer.deserialize` skips unknown fields. Simply adding `insured` to Expense would permit an older reader to ignore it and calculate incorrect premiums. Before implementation select explicit minimum-reader/capability admission or a bounded closed structure that old readers cannot silently treat as ordinary expenses. Any eligibility fact must enter fingerprints and expense-binding identity; the existing exact-definition stochastic correspondence protocol must not silently omit it. This is a future checkpoint, not a selected schema migration.

| Economic transaction | Correct planning treatment | Unsafe substitute |
|---|---|---|
| Term premium from household cash | Expense with insured eligibility; no accessible asset created | Creating an account equal to cumulative premiums |
| Cash-value premium | Expense/funding classification and contract value supplied consistently; premium not automatically value or basis | Adding full premium to value while also using an illustration already including it |
| Income-only annuity purchase | Debit identified funding once; create payment rights; no retained liquid principal unless contract supplies it | Keep source assets and independently add income |
| Purchase of deferred holding | Balanced source decrease/destination holding increase, charges and tax treatment explicit | Ordinary spending plus a separately added maturity windfall |
| Qualified internal transfer | Preserve ownership/qualification; distinguish transfer from taxable distribution | Tax every transfer or treat it as new contribution/RMD automatically |
| Contract rider charge | Decrease contract value under its value policy | Deduct household cash a second time |

Account names, array indices and Java object identity are not durable funding references. Persistent account identifiers are a prerequisite to a general saved account-linked exchange, not to independent conditional receipts. Introduce them only in a separately approved compatibility foundation: never generate replacements during historical decode, never change exact old frozen documents, and keep opaque identity out of financial fingerprints except where an actual source/destination financial relationship matters. Existing Roth transfer code demonstrates conservation/owner checks; it is not a general purchase API.

## 11. Liquidity and net-worth modeling

The current distinction is investable projected account balances versus separately valued non-investibles. It does not distinguish an insurance contract's gross value from cash obtainable today. Ordinary withdrawal strategies cannot safely consume a value that is locked or penalized without an explicit availability policy.

For a future holding distinguish:

- Contract value: internal accumulation measure.
- Cash surrender value: net quoted/derived exit value before separately modeled income tax.
- Accessible withdrawal value: currently allowed funding capacity under approved rules.
- Tax basis and qualification: tax facts, not extra assets.
- Death settlement and income guarantees: contingent promises, not additive present balances.

Use one documented net-worth valuation policy, with gross and surrender measures disclosed separately where needed. Count only allowed access in sustainable-spending/failure calculations. After-tax estate must apply the appropriate unrealized gain/qualification treatment rather than the current generic tax-deferred haircut by convenience. A future inaccessible holding cannot satisfy an RMD or fund a conversion solely because its gross value appears in net worth.

A maturity-only fixed holding is a reasonable initial simplification; make access restrictions explicit and fail safely when cash is needed early. A simplified surrender schedule is acceptable only if accurate for the approved subset. Unmodeled MVA must not be presented as zero risk or guaranteed accessible principal. No new liquidity optimization is recommended during this audit.

## 12. Death-benefit settlement

| Event | Current boundary | Proposed handling |
|---|---|---|
| Independent first-death receipt, no existing contract asset | CB timing and settlement available, fully taxable only | Restricted eligible receipt with approved tax class |
| Death value replaces a current policy/annuity holding | Unsupported linkage | Pure settlement result consumes old holding and emits one eligible net receipt; identity binds both sides |
| Survivor continues inherited contract | No contract inheritance/basis/election ledger | Defer; no automatic spouse ownership or distribution assumption |
| Adult child/trust receives claim | Outside household recipient set | Optional estate disclosure only; household assets decrease if an owned holding leaves, never receive that claim |
| Claim after last household death | Existing weighted metric stops before the selected final-death year | Unsupported terminal settlement; separate report/metric authorization needed |

Replacement must be idempotent under repeated annual tax/Medicare evaluations. Calculate from immutable opening state and event identity; commit one finalized projected closing state. Do not keep a global paid flag or mutate live accounts. Binding a future settlement to a holding identity is distinct from the existing independent CB UUID.

`EstateAtSecondDeathCalculator` uses the previous December 31 snapshot, or opening estate for an opening-date final death. A last-death claim must not be moved backward into that value. `AfterTaxEstateCalculator` is an investable-estate estimate with a tax-deferred haircut, not estate tax, probate, trust administration or insurance estate-inclusion analysis. Estate inclusion can differ from income-tax exclusion; no legal estate conclusion is implied by this proposal.

## 13. Boldin capability benchmark with official sources

Only documented behavior is confirmed. Relative update labels are reproduced as relative labels rather than converted into invented exact dates. All links were researched November 6, 2026. Separate product articles take precedence over inference from broad marketing categories.

| Capability | Official documented feature/workaround and date | Documented limitation / evidence boundary | Aledrix comparison and narrow lesson |
|---|---|---|---|
| Existing income annuity / SPIA-like quote | [How to enter an annuity](https://help.boldin.com/en/articles/5805679-how-to-enter-an-annuity-in-your-plan), July 20, 2026: payment, start/stop, tax selection, COLA and survivor entry | Article notes COLA behavior and limits coordination of excess annuity payments with other qualifying-account RMDs | Aledrix CB/PN cover only payment aspects. Quote entry is valuable, but tax/basis and RMD admission must be honest |
| Future annuity purchase / DIA | [Deferred annuities—future purchase](https://help.boldin.com/en/articles/8454126-deferred-annuities-future-purchase), visible relative update: over a week ago; documented future purchase and quote-adjustment workflow | Documentation uses a calculator/quote workflow, not proof of every carrier or guarantee | Funding/debit and later payment must be linked; do not copy a spending workaround into Aledrix's tax paths |
| Nonqualified / variable annuity | [Nonqualified or variable annuity entry](https://help.boldin.com/en/articles/11402484-how-to-enter-a-non-qualified-or-variable-annuity), visible update: over a month ago; manual basis account plus taxable-income/transfer workaround, or taxable/non-taxable payment split | Article explicitly describes no dedicated tax feature, manual fixed schedules, omitted accumulated earnings from net worth, and limitations for unwithdrawn earnings/heirs | Aledrix can differentiate through explicit gross/taxable/basis facts, not a claim to simulate variable contracts |
| MYGA | [How do I enter a MYGA?](https://help.boldin.com/en/articles/5804542-how-do-i-enter-a-myga), April 17, 2026: zero-return savings principal excluded from withdrawals, maturity interest as taxable lump pension, then transfer | Explicitly no MYGA account type. This is a maturity workaround, not a complete intermediate contract valuation | Aledrix lacks the same access/transfer controls; recipe cannot be transplanted safely. A real bounded deferred holding would add value |
| Term life and cash-value insurance | [Life insurance policies](https://help.boldin.com/en/articles/5946765-life-insurance-policies), visible update: over three weeks ago; nontaxable windfall at insured longevity, cash-value/other-asset workaround | Explicitly no dedicated life section. Documentation does not establish automatic asset replacement, full basis accounting or arbitrary beneficiary routing | Aledrix already has explicit scenario death triggers but needs restricted tax exclusion and premiums; later replacement could improve conservation |
| Survivor scenario | [Modeling loss of a spouse](https://help.boldin.com/en/articles/8117023-modeling-the-loss-of-a-spouse), May 19, 2026: scenario longevity and survivor planning with windfall | Not evidence of all trust/child/estate legal settlements | Keep Aledrix's actual modeled recipient explicit; avoid silently treating outside claims as spouse cash |
| Premium budgets | [Entering monthly amounts](https://help.boldin.com/en/articles/17201132-entering-monthly-amounts), relative/undated documentation: recurring expense entry with schedules | Automatic insured-contingent premium cessation is **not established** by this article | Aledrix has dated expense budgets; a named-insured policy would be a meaningful precise improvement |
| Tax-free windfalls | Life-insurance article above documents this workaround | Does not establish validation of all legal exclusion exceptions | Prefer restricted qualified receipt classes, not an unlimited tax-free dropdown |
| Beneficiaries / estate | [AI estate transfer report](https://help.boldin.com/en/articles/17349284-use-boldin-ai-to-create-an-estate-transfer-report), visible update: over three weeks ago; documented heir-allocation reporting | A report/prompt workflow is not evidence of a full beneficiary cash-flow ledger | An optional Aledrix estate report can be separate from household spending and current terminal metrics |
| LTC planning | [Long-term care assumptions](https://help.boldin.com/en/articles/5847824-what-are-the-long-term-care-assumptions), May 19, 2026: insurance choice models 20% of modeled care expense; premiums separately entered | This is a planning expense adjustment, not documented actual policy reimbursement administration | Aledrix can offer labeled stress budgets now; do not equate them with claims support |

Inference: useful product positioning comes from transparent, source-attributed cash/tax/value effects and explicit limitations. Public documentation supports a staged general-purpose approach; it does not justify duplicating insurer engines. Absence of evidence in these pages is not a claim that Boldin has no other feature. No proprietary implementation was inspected.

## 14. Four-strategy comparison

Scores are subjective design judgments on a 1–5 scale, **5 favorable**. For effort, integration, regression, testing and maintenance, 5 means lower burden/risk. Equal weighting is illustrative, not objective prioritization or measured development time.

| Factor | 1. Current inputs only | 2. Minimal tax/receipts and premiums | 3. Generalized product primitives | 4. Specialized contracts |
|---|---:|---:|---:|---:|
| User value / common cases | 2 | 4 | 5 | 4 |
| Tax accuracy within declared scope | 2 | 4 | 5 | 4 |
| Liquidity accuracy for accumulating products | 1 | 1 | 4 | 5 |
| Low implementation effort | 5 | 4 | 2 | 1 |
| Low integration burden | 5 | 4 | 2 | 1 |
| Low regression risk | 5 | 3 | 2 | 1 |
| Low testing burden | 5 | 3 | 2 | 1 |
| Historical compatibility simplicity | 5 | 4 | 2 | 1 |
| Low maintenance burden | 5 | 4 | 3 | 1 |
| Marketability / differentiation | 1 | 4 | 5 | 4 |
| Fit with common Aledrix engine | 5 | 5 | 4 | 2 |
| Illustrative total / 55 | 41 | 40 | 36 | 25 |

The high no-change total reflects low cost, not adequate tax or liquidity support. Exclude strategies that fail a desired use case before comparing cost scores. Strategy 2 is preferred for term-survivor receipts and quoted income; Strategy 3 becomes necessary when accurate MYGA liquidity/value is a requirement. Strategy 4's larger product breadth would be hard to validate and maintain; more complexity does not assure more accuracy.

## 15. Recommended architecture

Keep one authoritative chain: frozen financial definitions → pure annual event/payment/holding evaluation → immutable source/tax facts → ProjectionEngine funding/Roth/Medicare retries → HouseholdCashSettlement → completed annual results → authenticated immutable history/reporting. The same original annual facts feed cash and taxable selection.

Extend it in layers rather than invent a universal contract graph:

1. **Receipt treatment capability:** closed legally supported tax reasons, separate taxable settlement interest, gross/taxable/excluded consistency, original eligibility facts.
2. **Contractual expense eligibility:** precise named-insured stop behavior and exemption from lifestyle reduction, with explicit old-reader protection.
3. **Quoted non-pension annuity payment:** existing trigger schedule plus verified basis recovery and explicit annuitant survival; no pricing engine.
4. **Funding exchange:** projected source and holding/destination changes balance; owner, qualification and tax character explicit.
5. **Deferred holding and settlement:** fixed accumulation/access/basis/maturity, optional later cash-value replacement; no all-product abstraction now.

All definitions remain bounded and immutable. Repeated calculations use detached projected state, never live Account mutations or shared event flags. Derive dates from frozen DOBs; reuse mortality state and January 1 deaths. Labels and opaque IDs remain nonfinancial except that resolving a relationship to a particular financially different source is material. Unsupported legal/product combinations receive explicit admission failure.

New tax semantics require a distinguishable capability. Existing Phase 22 all-taxable provenance must remain unchanged; a new enum value alone is insufficient because completed receipts and historical authenticity checks enforce all-taxable facts. At each implementation checkpoint choose explicit capability/payload/fingerprint combinations from then-current code. No new number is allocated speculatively here.

## 16. Required architectural decisions

| Decision requested | Recommendation and rationale | Alternative rejected / limitation |
|---|---|---|
| 1. Dedicated insurance-policy abstraction now? | **No.** Restricted receipt qualification and contractual expense eligibility cover the initial high-value case | Universal policy administration duplicates projection machinery; thin qualification facts are still necessary |
| 2. Dedicated annuity-contract abstraction now? | **No universal engine.** Use quoted schedules first; a bounded deferred holding later if funded accumulation is wanted | A single broad product class would combine incompatible basis, liquidity and guarantee promises |
| 3. Restricted excluded receipt first? | **Yes**, with legal/state qualification and separate interest | Generic tax-free toggle would conceal exceptions; old taxable definitions never change meaning |
| 4. Partial annuity income first? | **Next after the initial receipt/premium package**, separately approvable; useful in-force quotes need no purchase IDs | Finite basis recovery is more complex than excluded principal; arbitrary forever-exclusion percentage rejected |
| 5. Existing recurring expenses for premiums? | **Yes for disclosed dated budgets**, not full scenario-correct coverage | Current survivor factor and death independence prevent complete premium support |
| 6. Owner-contingent termination? | **Named-insured-contingent termination is necessary**; owner/payer may differ | Stopping at owner death by default can stop or continue the wrong premium |
| 7. MYGA initially approximate? | **Yes only as an explicitly limited illustration**, not a supported account product | Aledrix cannot reproduce the competitor recipe faithfully with current controls; B is justified if accurate access/value is required |
| 8. Persistent account IDs prerequisite? | **Yes for general persisted source-account funding**, not initial independent receipts/quotes | Names, positions and runtime references are unstable. Historical IDs must not be fabricated |
| 9. First-class cash surrender value? | **Later as a valuation/access attribute of one holding**, not a second balance | Contract value plus surrender value as separate assets double-counts |
| 10. Household-only beneficiaries? | **Yes** for executable initial receipts | External recipients need a different ledger and must not finance retirement spending |
| 11. External proceeds household assets or estate reports? | **Report-only optional scope**, with owned asset leaving household when applicable | Existing last-death metric does not include later settlements; report scope still needs approval |
| 12. Insurer-specific mechanics? | **Defer indefinitely absent demonstrated demand** | Carrier/version/state maintenance outweighs present planning value; approximations must be labeled |
| 13. Which features justify regression cost? | Restricted receipts, precise premiums, quoted tax-split income; deferred holding/funding only for demanded liquidity/value use cases | GLWB, indexed crediting, policy loans and claims administration do not currently justify their coupling/regression cost |

## 17. Proposed phased roadmap

Every row requires separate authorization. Relative complexity is a dependency assessment, not a calendar estimate. Each financial/persistence phase needs focused tests followed by full normal Level 3 Maven regression and native acceptance; documentation-only work is Level 1.

| Proposed bounded phase | User value / financial scope | Persistence and historical gate | Tax and analyzer effects | Complexity / approval gate |
|---|---|---|---|---|
| R1 — Restricted death-principal receipts | Accurate living-spouse cash with no direct federal inclusion; settlement interest separate | Explicit qualification facts and disjoint enabled capability; old Phase 22 all-taxable artifacts unchanged | Individually validated MI/IL/no-general-income-tax mapping; all supported deterministic/weighted/generated MC families and current/history disclosures before public enablement | Medium; approve exact legal subset, state set, current yield-tax disclosure and version matrix first |
| R2 — Contractual premium schedule | Scenario-correct insured death/coverage stop; no lifestyle survivor discount | Resolve old Expense unknown-field hazard; new financial identity and binding rules without changing legacy expense streams | Same authoritative expense/cash path; no new RNG; test both death orders and asymmetric candidates | Medium; approve due-month timing/eligibility and backward-reader behavior. Term-life planning support only after R1+R2 |
| R3 — In-force quoted annuity income | Gross versus taxable payment and finite basis recovery; restricted single-life quote | Explicit basis/annuitant/tax capability, original quote assumptions; strict historical decoding | Non-pension source mapping, current/history PDFs; scenario survival and tax retries. Qualified products remain blocked | Medium-to-high; approve supported tax method, survival/COLA subset and basis oracles |
| R4 — Stable account references and funding exchange | Future purchase without duplicate wealth or accidental tax | Legacy omission/digests preserved; persisted links validated through Save As, copies and swaps; no inferred historical IDs | Ownership, qualified transfer versus distribution, source gains/basis must be supported before purchase admission | High; checkpoint source-account tax scope and atomic financial conservation before engine changes |
| R5 — Fixed deferred holding / maturity | Useful MYGA accumulation, access and tax-deferral planning | New bounded holding capability, valuation/basis/state snapshots; old account contracts retained | Fixed crediting separate from market streams; earnings/basis maturity, liquidity/failure, estate and all analyzers | High; approve fixed-term/no-early-access subset or validated surrender policy; no product-specific rates |
| R6 — Restricted value replacement | Externally supplied cash-value policy/deferred-contract settlement without double-counting | Link holding to event; original value/settlement facts bound independently for each candidate | Remove old asset, pay eligible settlement once; basis/state legal treatment independently supported | High; approve valuation schedules, tax subset and living-beneficiary limitations |
| Optional later — Qualified annuity / QLAC | Accurate RMD-coordinated qualified income | Qualification, balance-exclusion and source-account provenance | RMD/payment coordination, Roth/IRA basis and state distinctions; no borrowing current IRA assumptions | High; separate architecture/approval gate, not bundled with R3 |
| Optional closure per delivered subset | Clear UI/help/current and historical PDF disclosures | No reinterpretation of old names, definitions or tax classes | Native windows, compact/themed controls, PDF View/Save/Cancel and unsupported feedback | Level 1/2 unless identity code changes; major milestone full suite |

R1 and R2 can be reviewed independently, but neither alone justifies marketing complete term-life scenario support. R3 does not require R4 for already purchased quotes. R5 requires the relevant funding/basis foundation for future purchases. This order avoids forcing an account migration into the first useful receipt feature.

## 18. Unsupported and deferred features

Retain explicit unsupported status for external/trust/estate household receipts, claims after last modeled death, guaranteed post-final-death continuation, complex inherited annuities, insurer pricing, MVA formulas, indexed/variable crediting, GLWB account/guarantee coupling, benefit-base rollups as wealth, loans/MEC administration, lapse/underwriting, LTC morbidity/reimbursement and hybrid benefit depletion. A manually supplied illustration may support a labeled stress scenario without certifying those mechanisms.

No new state coverage, estate-tax engine, Medicare lookback redesign, variable tax basis method or insurer rate feed is proposed implicitly. Product-specific features can be reconsidered only with demonstrated demand, authoritative rules and a bounded correctness test plan.

## 19. Financial correctness risks and invariants

| Risk | Required invariant / independent oracle |
|---|---|
| Double-counted principal | Funding reduces source by the same supported economic value it creates; maturity/settlement removes old holding before receipt. Zero-return/no-tax balance conservation oracle |
| Double-paid death/lump event | Pure same-year result identical under retries; final cash settlement includes event once; later-year event receipts zero |
| Wrong insured/recipient | Use scenario's existing named death; absent spouse is not dead; recipient alive at eligible event/payment; both death directions tested |
| Tax-free mislabeled cash | Exclusion requires supported legal category and original facts; interest remains taxable; unknown eligibility rejected |
| Annuity basis error | Gross = taxable + valid non-taxable components; cumulative recovered basis never exceeds original adjusted basis; exhausted basis changes taxable payment |
| False pension/RMD treatment | Non-pension sources carry no retirement participant exemption; qualified annuity RMD effects require explicit supported capability |
| Wrong premium behavior | Surviving insured's premium not lifestyle-reduced; insured-death policy and due-month timing use documented convention |
| False liquidity | Only accessible value funds withdrawals; locked asset cannot hide funding failure; surrender fees/tax reconciled separately |
| Lost future return taxation | Excluded principal not mislabeled exempt yield; current modeled investment-tax scope disclosed; separate supplied settlement interest checked |
| Roth/IRMAA feedback omitted | Same immutable source facts supplied to bracket fill, funding and Medicare retries; compare full financial control cases, no fixed-direction assumptions |
| Death timing/horizon distortion | January 1 mortality and original projection interval retained; no claim moved into prior December 31 estate |
| Historical reinterpretation | Old document/results/digests/UUID unchanged; new capability not inferred from live product labels; export runs no evaluator |
| Old-reader silent omission | Nonempty new financial policy never executes as if its unknown fields were absent; explicit compatibility admission |
| Scenario noise introduced | No new mortality draw; deterministic new events add no market/inflation/expense RNG consumption |
| Candidate contamination | Freeze both candidates independently; financial equivalence with shared worlds gives zero differences regardless of labels/opaque IDs |

## 20. Historical and Monte Carlo compatibility

The verified current matrix is broader than the older Phase 21-only matrix:

| Existing result contract | Payload / envelope | Stored fingerprint / capability |
|---|---|---|
| Legacy Monte Carlo | Payload V1 / envelope V1 | V1; temporary numeric V2 remains read-only compatibility, not an expense or insurance model |
| Standalone stochastic expense MC | Payload V2 / envelope V1 | V3, `ExpenseExecution` |
| Paired stochastic expense MC | Payload V3 / envelope V1 | V4, `PairedExpenseExecution` |
| Conditional-benefit standalone MC | Payload V4 / envelope V1 | V5, analyzer conditional-benefit capability; optional existing expense capability |
| Conditional-benefit paired MC | Payload V5 / envelope V1 | V6, independently bound candidates; optional paired expense capability |
| B0 pension correction | Existing family contracts | Separate `pension-selection=death-selected-v2` applicability semantic |
| B2 deterministic benefits | Complete projection V1, existing retained annual schemas | Separate `conditional-benefits=deterministic-annual-v1` binding to original document/completed evidence |
| B3 integrated/weighted benefits | Existing family payload/envelope V1 with known optional capability | `ANALYZER_ANNUAL_CONDITIONAL_BENEFITS_V1`, `conditional-benefits=analyzer-annual-v1`, original family/scenario/candidate binding |

Do not reuse these identifiers for excluded receipts, basis recovery or contract assets. Their current assumptions include the existing fully taxable benefit subset. A future capability selection must validate exact payload/fingerprint/family/algorithm combinations and preserve raw/compressed/structural/numeric bounds, strict unknown/missing-field handling, ownership, digests and atomic writes. Provenance is integrity binding under the application's existing digest architecture, not an insurer guarantee or digital signature.

Original snapshots, labels, IDs, numeric results and projection associations remain frozen. Do not assign new IDs or tax classes on load. New current financial edits can stale relevant results; old results remain historically viewable/exportable under their original contract. SS-only analysis remains independent where it consumes no full-plan product facts. No global algorithm-map bump is selected here.

For new supported features retain all eight generated household/mode combinations, subject to individually validated capability admission. Conditional events use the existing configured deaths in fixed mode and the already sampled deaths in longevity mode. No additional mortality or insurance draw. Expense dimension/version **6/1** and exact normalized-definition correspondence, scenario index, absolute year and duplicate ordinal stay unchanged for old expenses. A future insured-contingent expense field requires a separately explicit binding decision rather than silently changing V1 key meaning.

Current/Baseline freeze independently. New account/holding/benefit facts must survive RetirementPlan copies, baseline capture, CompleteProjectionSnapshot.freeze, controller restoration/swaps and Monte Carlo request capture. Prefix/continuation/equivalence optimizations remain conservative; current benefit-bearing weighted evaluation uses the reference path rather than unproven reuse. A new holding cannot be enabled solely because ProjectionEngine can calculate one year.

## 21. Suggested testing approach for future implementation

Use Level 3 for any tax, timing, persisted financial input, capability, funding, holding or identity change. Focused tests first; full normal Maven regression once integrated, with original opt-in skips, no regenerated goldens and no additional exclusions. Use Java 25, required IntelliJ Maven, repository-local `.codex-m2/repository` and established JVM memory settings.

Required layers: pure event and monetary known-answer tests; source-level gross/taxable/excluded/basis tests; ordinary/explicit-lifetime equivalence; both survivor orders and single-person restrictions; same-year retries with no duplicate deposit; MI/IL/no-general-tax and PA/IA admission; Roth/RMD/IRMAA funding controls; cash conservation/withdrawal/estate oracles; and zero-feature exact legacy financial/random compatibility.

For accumulation use independently calculated fixed-rate and finite-basis examples, accessible-value shortfalls, boundary maturity/surrender cases and asset replacement with no market growth. For annuities use independently supplied tax method/quote facts and basis exhaustion rather than engine-generated expected values. Test insurer interest separately from excluded principal.

Exercise all eight generated MC configurations with zero/positive stochastic expenses, fixed-seed reproducibility and Current-only/Baseline-only/identical/different new facts. Keep return, inflation, mortality and expense known-answer vectors unchanged. Do not test theoretical expense means by requiring an arbitrary finite sample to equal the mean.

Persistence tests must cover exact zero-feature wire/digests, old-reader rejection strategy, strict malformed/unsupported capability combinations, tampered/swapped manifests, legal-source facts, bounds, Save/Load/Save As/restart/swaps and no historical recalculation. UI/reporting tests use original frozen assumptions after live edits, storage failure/Retry Save without rerun, cancellation, Light/Dark compact layout, rendered PDF inspection and native Windows acceptance. Performance tests remain bounded; no production-scale guarantee from synthetic small trials.

## 22. Exact files inspected

The following inventory identifies source files inspected directly or through targeted symbol/body searches; it does not claim every line of every large class was read. Paths below are relative to `src/main/java/com/daviddunn/retirementplanner/`. The audit reconciled the relevant mechanisms rather than rerunning accepted phases.

- [app/export/IntegratedAnalyzerPdfExporter.java](src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerPdfExporter.java)
- [app/export/ProjectionPdfExporter.java](src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java)
- [app/export/ProjectionPdfReport.java](src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfReport.java)
- [app/montecarlo/ExpenseSamplingSpecification.java](src/main/java/com/daviddunn/retirementplanner/app/montecarlo/ExpenseSamplingSpecification.java)
- [app/montecarlo/MonteCarloAnalyzer.java](src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloAnalyzer.java)
- [app/montecarlo/MonteCarloExpenseGenerator.java](src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloExpenseGenerator.java)
- [app/montecarlo/MonteCarloFingerprintService.java](src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloFingerprintService.java)
- [app/montecarlo/MonteCarloRandomStreams.java](src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloRandomStreams.java)
- [app/montecarlo/MonteCarloSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshot.java)
- [app/montecarlo/MonteCarloSnapshotMapping.java](src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshotMapping.java)
- [app/montecarlo/MonteCarloStrategyComparisonAnalyzer.java](src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonAnalyzer.java)
- [app/projection/CompleteProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java)
- [app/projection/ConditionalBenefitDisclosures.java](src/main/java/com/daviddunn/retirementplanner/app/projection/ConditionalBenefitDisclosures.java)
- [app/projection/ConditionalBenefitExecutionSemantics.java](src/main/java/com/daviddunn/retirementplanner/app/projection/ConditionalBenefitExecutionSemantics.java)
- [app/projection/ConditionalBenefitFinancialInputs.java](src/main/java/com/daviddunn/retirementplanner/app/projection/ConditionalBenefitFinancialInputs.java)
- [app/socialsecurity/results/AnalysisCompatibilityPolicy.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java)
- [app/socialsecurity/results/AnalysisFingerprintService.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisFingerprintService.java)
- [app/socialsecurity/results/AnalysisSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisSnapshot.java)
- [app/socialsecurity/results/AnalysisVersions.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisVersions.java)
- [app/socialsecurity/results/ConditionalBenefitAnalysisExecution.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ConditionalBenefitAnalysisExecution.java)
- [app/socialsecurity/RetirementPlanScenarioCopyService.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/RetirementPlanScenarioCopyService.java)
- [domain/benefit/ConditionalBenefitAdmission.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitAdmission.java)
- [domain/benefit/ConditionalBenefitDefinition.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitDefinition.java)
- [domain/benefit/ConditionalBenefitDefinitionDeserializer.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitDefinitionDeserializer.java)
- [domain/benefit/ConditionalBenefitEvaluator.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitEvaluator.java)
- [domain/benefit/ConditionalBenefitExecutionVerification.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitExecutionVerification.java)
- [domain/benefit/ConditionalBenefitReceipt.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitReceipt.java)
- [domain/benefit/ConditionalBenefits.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefits.java)
- [domain/estate/AfterTaxEstateCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/estate/AfterTaxEstateCalculator.java)
- [domain/estate/EstateAtSecondDeathCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/estate/EstateAtSecondDeathCalculator.java)
- [domain/financial/Account.java](src/main/java/com/daviddunn/retirementplanner/domain/financial/Account.java)
- [domain/financial/Expense.java](src/main/java/com/daviddunn/retirementplanner/domain/financial/Expense.java)
- [domain/financial/ExpenseDeserializer.java](src/main/java/com/daviddunn/retirementplanner/domain/financial/ExpenseDeserializer.java)
- [domain/income/HouseholdPensionIncomeCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/income/HouseholdPensionIncomeCalculator.java)
- [domain/income/IncomeSource.java](src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSource.java)
- [domain/income/IncomeSourceAttribution.java](src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java)
- [domain/income/Pension.java](src/main/java/com/daviddunn/retirementplanner/domain/income/Pension.java)
- [domain/medicare/MedicarePremiumCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/medicare/MedicarePremiumCalculator.java)
- [domain/model/AccountType.java](src/main/java/com/daviddunn/retirementplanner/domain/model/AccountType.java)
- [domain/model/Person.java](src/main/java/com/daviddunn/retirementplanner/domain/model/Person.java)
- [domain/model/RetirementPlan.java](src/main/java/com/daviddunn/retirementplanner/domain/model/RetirementPlan.java)
- [domain/model/TaxTreatment.java](src/main/java/com/daviddunn/retirementplanner/domain/model/TaxTreatment.java)
- [domain/noninvestable/NonInvestableAsset.java](src/main/java/com/daviddunn/retirementplanner/domain/noninvestable/NonInvestableAsset.java)
- [domain/projection/EffectiveHouseholdDeathView.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/EffectiveHouseholdDeathView.java)
- [domain/projection/HouseholdCashSettlement.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/HouseholdCashSettlement.java)
- [domain/projection/HouseholdLifetimeScenario.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/HouseholdLifetimeScenario.java)
- [domain/projection/ProjectedPortfolio.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectedPortfolio.java)
- [domain/projection/ProjectionEngine.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java)
- [domain/projection/ProjectionEvaluationContext.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEvaluationContext.java)
- [domain/rmd/OwnerRmdCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/rmd/OwnerRmdCalculator.java)
- [domain/rmd/RmdBalanceAggregator.java](src/main/java/com/daviddunn/retirementplanner/domain/rmd/RmdBalanceAggregator.java)
- [domain/roth/RothConversionBracketFillCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/roth/RothConversionBracketFillCalculator.java)
- [domain/roth/RothConversionEligibilityPolicy.java](src/main/java/com/daviddunn/retirementplanner/domain/roth/RothConversionEligibilityPolicy.java)
- [domain/tax/state/IllinoisProductionTaxCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/IllinoisProductionTaxCalculator.java)
- [domain/tax/state/iowa/IowaTaxBaseBridge.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/iowa/IowaTaxBaseBridge.java)
- [domain/tax/state/michigan/MichiganRetirementEligibility.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/michigan/MichiganRetirementEligibility.java)
- [domain/tax/state/michigan/MichiganTaxCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/michigan/MichiganTaxCalculator.java)
- [domain/tax/state/MichiganProductionTaxAdapter.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/MichiganProductionTaxAdapter.java)
- [domain/tax/state/PennsylvaniaProductionTaxCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/PennsylvaniaProductionTaxCalculator.java)
- [domain/tax/state/ProductionStateTaxService.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ProductionStateTaxService.java)
- [domain/tax/TaxFundingCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/TaxFundingCalculator.java)
- [domain/tax/TaxIncome.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/TaxIncome.java)
- [domain/tax/TaxIncomeAttribution.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/TaxIncomeAttribution.java)
- [domain/tax/TaxIncomeCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/TaxIncomeCalculator.java)
- [persistence/AnalysisSnapshotCodec.java](src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java)
- [persistence/JsonRetirementPlanRepository.java](src/main/java/com/daviddunn/retirementplanner/persistence/JsonRetirementPlanRepository.java)
- [ui/benefits/ConditionalBenefitPresentation.java](src/main/java/com/daviddunn/retirementplanner/ui/benefits/ConditionalBenefitPresentation.java)
- [ui/controller/ApplicationController.java](src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java)
- [ui/dialogs/ConditionalBenefitDialog.java](src/main/java/com/daviddunn/retirementplanner/ui/dialogs/ConditionalBenefitDialog.java)
- [ui/montecarlo/MonteCarloHistoricalPdf.java](src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloHistoricalPdf.java)
- [ui/montecarlo/MonteCarloPdfReportAdapter.java](src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPdfReportAdapter.java)
- [ui/socialsecurity/FrozenConditionalBenefitDetails.java](src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/FrozenConditionalBenefitDetails.java)
- [ui/views/ConditionalBenefitsPane.java](src/main/java/com/daviddunn/retirementplanner/ui/views/ConditionalBenefitsPane.java)
- [ui/views/IncomeSourcesView.java](src/main/java/com/daviddunn/retirementplanner/ui/views/IncomeSourcesView.java)

Relevant predecessor reports reviewed (selected architecture, compatibility, implementation and verification sections):

- [PHASE-11C-SOCIAL-SECURITY-AND-QUICK-PDF-EXPORT.md](PHASE-11C-SOCIAL-SECURITY-AND-QUICK-PDF-EXPORT.md)
- [PHASE-12D-COMPLETE-PROJECTION-SNAPSHOT-PERSISTENCE.md](PHASE-12D-COMPLETE-PROJECTION-SNAPSHOT-PERSISTENCE.md)
- [PHASE-12E-IMMUTABLE-ANALYZER-ASSOCIATIONS.md](PHASE-12E-IMMUTABLE-ANALYZER-ASSOCIATIONS.md)
- [PHASE-12G-ANALYZER-FRESHNESS-IMPLEMENTATION.md](PHASE-12G-ANALYZER-FRESHNESS-IMPLEMENTATION.md)
- [PHASE-13B-APPLICATION-SETTINGS-IMPLEMENTATION.md](PHASE-13B-APPLICATION-SETTINGS-IMPLEMENTATION.md)
- [PHASE-14B1-STOCHASTIC-EXPENSE-SAMPLING.md](PHASE-14B1-STOCHASTIC-EXPENSE-SAMPLING.md)
- [PHASE-14B2-STOCHASTIC-EXPENSE-FINANCIAL-INTEGRATION.md](PHASE-14B2-STOCHASTIC-EXPENSE-FINANCIAL-INTEGRATION.md)
- [PHASE-19B4-S3-ANALYZER-SNAPSHOT-SAVE-FAILURE.md](PHASE-19B4-S3-ANALYZER-SNAPSHOT-SAVE-FAILURE.md)
- [PHASE-21B2-STANDALONE-STOCHASTIC-EXPENSE-EXECUTION.md](PHASE-21B2-STANDALONE-STOCHASTIC-EXPENSE-EXECUTION.md)
- [PHASE-21B3-MONTE-CARLO-COMPARISON-STOCHASTIC-EXPENSES.md](PHASE-21B3-MONTE-CARLO-COMPARISON-STOCHASTIC-EXPENSES.md)
- [PHASE-21B4-MONTE-CARLO-FINAL-INTEGRATION-AND-CLOSURE.md](PHASE-21B4-MONTE-CARLO-FINAL-INTEGRATION-AND-CLOSURE.md)
- [PHASE-22A-CONDITIONAL-INCOME-AND-INSURANCE-ARCHITECTURE.md](PHASE-22A-CONDITIONAL-INCOME-AND-INSURANCE-ARCHITECTURE.md)
- [PHASE-22B0-PENSION-CASH-TAX-CONSISTENCY.md](PHASE-22B0-PENSION-CASH-TAX-CONSISTENCY.md)
- [PHASE-22B1-CONDITIONAL-BENEFIT-DOMAIN-AND-PERSISTENCE.md](PHASE-22B1-CONDITIONAL-BENEFIT-DOMAIN-AND-PERSISTENCE.md)
- [PHASE-22B2-CONDITIONAL-BENEFIT-FINANCIAL-INTEGRATION.md](PHASE-22B2-CONDITIONAL-BENEFIT-FINANCIAL-INTEGRATION.md)
- [PHASE-22B3-CONDITIONAL-BENEFIT-ANALYZER-INTEGRATION.md](PHASE-22B3-CONDITIONAL-BENEFIT-ANALYZER-INTEGRATION.md)
- [PHASE-22B4-CONDITIONAL-BENEFITS-UI.md](PHASE-22B4-CONDITIONAL-BENEFITS-UI.md)
- [PHASE-6C2-RETIREMENT-INCOME-SOURCE-ATTRIBUTION.md](PHASE-6C2-RETIREMENT-INCOME-SOURCE-ATTRIBUTION.md)
- [PHASE-6D1-MULTISTATE-PRODUCTION-MI-FL.md](PHASE-6D1-MULTISTATE-PRODUCTION-MI-FL.md)
- [PHASE-6D2-ILLINOIS-PRODUCTION-INTEGRATION.md](PHASE-6D2-ILLINOIS-PRODUCTION-INTEGRATION.md)
- [PHASE-6D3B-PENNSYLVANIA-PRODUCTION-INTEGRATION.md](PHASE-6D3B-PENNSYLVANIA-PRODUCTION-INTEGRATION.md)
- [PHASE-6D5B-IOWA-PRODUCTION-INTEGRATION.md](PHASE-6D5B-IOWA-PRODUCTION-INTEGRATION.md)

Repository instructions: AGENTS.md and the session-supplied Windows/Maven, financial safeguard and preservation requirements.


## 23. Verification performed

Documentation-only risk scope. Read source, reviewed predecessor report evidence, and researched official public sources. No test/build/JavaFX/PDF generation was needed to modify a report; no Maven or expensive simulations were run. Prior B4's report records 4,441 tests / 4,395 passed / 46 skips / zero failures/errors; that is **prior reported implementation evidence**, not a new Phase 23A run or new native acceptance claim.

Final verification: `git diff --check` passed (exit 0). A separate check found zero trailing-whitespace lines in this untracked report, which ordinary Git diff does not inspect. All 25 required numbered sections are present; every local Markdown link resolves. SHA-256 comparison of all 1,499 initially tracked files found zero changes. The final index is empty, HEAD is unchanged, and Git status contains only this new untracked report. Ignored `target/phase23a/` records the starting state, source inventory and preservation evidence.

Unverified areas include hands-on Boldin behavior, current Aspida rates/form-specific contract terms, new state-specific legal qualification and future-product financial execution. No runtime tests, actual UI previews or rendered PDF inspections were performed in this documentation phase. Nothing proposed here is certified implemented.

## 24. Git preservation

Initial branch `main`, HEAD `7db85b2e67066d73539fc68291e3df50d20730ee`, empty index and clean working tree. Unlike earlier phase reports, there were no outstanding accepted source changes to merge: accepted work was already present in this HEAD. Starting status/index/HEAD and SHA-256 hashes of all tracked files were recorded under ignored `target/phase23a/`.

Only this audit report is intended as a new visible file. Ignored audit evidence is not application data. No staging, commit, push, reset, clean, revert, production/test modification or user-plan access was performed.

Final verification confirms that intention: all 1,499 pre-existing tracked files are byte-for-byte unchanged, including accepted Phase 22 source, tests and reports. There are no new production/test files, no staged changes and no branch/HEAD change.

## 25. Explicit approval decisions requested

Please approve or revise the thirteen decisions in section 16 and the bounded roadmap in section 17, especially:

1. Strategy 2 first: restricted qualifying death principal plus explicit premium eligibility; preserve current fully taxable definitions and household-only beneficiaries.
2. Quoted nonqualified annuity income next, limited to validated tax/basis methods and living-recipient contracts; no implicit pension exemption or qualified-annuity/RMD promise.
3. Defer accurate MYGA/deferred holding and source funding until their additional value is requested; manual current-input scenarios remain labeled approximations.
4. Require explicit old-reader and new-result capability checkpoints; preserve all historical artifacts, existing algorithms and random streams.
5. Keep cash-value replacements, external estate reports, qualified annuities and all insurer-specific mechanics behind separate approvals; accept the stated investment-tax and terminal-estate boundaries.

Stop gate: this audit authorizes no implementation. The next step is the user's selection of a bounded feature and its legal/tax, timing, compatibility and verification scope.
