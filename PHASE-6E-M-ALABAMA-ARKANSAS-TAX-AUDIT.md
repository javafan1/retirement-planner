# Aledrix — Phase 6E-M Alabama and Arkansas tax audit

Verification date: **2026-11-17**. **AUDIT ONLY.** Neither state is implemented, registered or admitted by this report. Colorado remains disabled. Phase 6E-N requires explicit approval of the decisions in Section 21.

Evidence labels: **Law** = enacted text; **Guidance** = official agency instructions/rules; **Code** = inspected working-tree behavior; **Interpretation** = reasoned application, not an agency ruling; **Proposal** = unapproved implementation or planning convention; **Open** = unresolved qualification or release requirement. Numeric planning examples distinguish statutory arithmetic from filed-return tables. No new calculator was used to generate expectations.

## 1. Executive summary

**Both states fit the existing annual state-tax interface, but neither is ready for unconditional production admission.** A combined development workstream is feasible with independent gates. Alabama is moderate to high complexity because of its federal-liability deduction and missing pension-plan classification. Arkansas is high complexity because of its two income schedules, stepped bracket adjustments, source-dependent retirement relief and funding behavior near the schedule transition.

The source confirms **19 production jurisdictions**. The opening working tree is clean. Alabama and Arkansas are absent from the production enum/router. Colorado's isolated rules still return `productionAdmitted() == false`.

Key conclusions:

1. **Alabama federal deduction:** current-year uncontested federal liability is a legally supported basis; it is not necessary to invent a withholding/payment history. However, `FederalTaxCalculation` is a modeled liability, not a complete Form 1040. Its omitted additional-age and enhanced-senior deductions can materially overstate the Alabama deduction and understate Alabama tax. Recommend a narrow Alabama-only ordinary federal-deduction helper using existing federal brackets and household facts, leaving federal results/cash unchanged. A raw-model alternative requires explicit acceptance of quantified error.
2. **Alabama pensions:** legal §414(j) defined-benefit payments are exempt, including qualifying beneficiary payments. A generic `Pension` is not proof of that classification. Cash-balance plans can be legally defined benefit despite account-like presentation. Accurate common pension support needs either a narrowly approved classification fact or an explicit, limited assumption; blanket exemption from a monthly payment/name is unsafe.
3. **Alabama senior relief:** the separate **$6,000 per qualifying age-65 recipient** remains in the 2026 enrolled law. It applies to otherwise taxable retirement receipts, not again to fully exempt defined-benefit payments. Official instructions explicitly place taxable IRA-to-Roth conversions on Schedule RS, which supports the ordinary age-65 pool treatment.
4. **Arkansas current law:** May 2026 Acts 1/2 establish a **3.7% top rate** for 2026 onward, with a lower/middle schedule, a distinct high-income schedule, and a **$290-to-$10 tax subtraction** over $94,701–$97,600. These are not interchangeable with a flat 3.7% tax or a smooth progressive curve.
5. **Arkansas IRA conflict:** Act 817 of 1999 and current DFA instructions resolve the older rule's exclusion of non-SEP IRAs. Ordinary own-IRA benefits after 59½ can share the recipient's $6,000 pool. Employer-plan cash has different rules; the recipient need not be retired. Do not import Mississippi's cash qualification rules.
6. **Arkansas conversions:** inclusion of taxable traditional IRA conversion income is established by DFA guidance. Applying the age-qualified $6,000 pool to an executed conversion is a supported statutory interpretation, not a located conversion-specific exemption ruling. Approve that interpretation explicitly or refuse the affected transaction; do not silently import another state's treatment.
7. **Arkansas funding gate:** independent decimal arithmetic under the proposed statutory cent extension produces a genuine two-cycle in an ordinary IRA-funded case at the high-income transition. The current fixed-point solver requires cent-level equality. It must not be assumed to converge everywhere. Section 13 supplies the exact counterexample and a bounded decision path; no production failure or new integration test was run during this audit.

Recommend developing the independent domain components first. Alabama can proceed separately once its amount/classification policies are approved. Arkansas must remain disabled until its rounding/table policy and ordinary funding boundary have passed independent acceptance. Reaching 21 states is not itself a release gate.

## 2. Source-confirmed baseline and architecture

### Actual production registry

`src/main/java/com/daviddunn/retirementplanner/domain/model/StateTaxJurisdiction.java` has exactly:

| Production treatment | Jurisdictions |
|---|---|
| Eleven dedicated calculators/adapters | Arizona, Georgia, Illinois, Iowa, Kentucky, Michigan, Mississippi, North Carolina, Oklahoma, Pennsylvania, Virginia |
| Eight no-general-individual-income-tax models | Alaska, Florida, Nevada, New Hampshire, South Dakota, Tennessee, Texas, Wyoming |
| Not registered | Alabama, Arkansas, Colorado |

`domain/tax/state/ProductionStateTaxService.java` explicitly routes the eleven and registers the eight zero-tax models. `domain/rules/USState.java` contains broader geographic names; that is not production registration. Colorado is `CO-2026-PREPARATORY-2`, unregistered, with `productionAdmitted() == false`. Current OK/MS rule versions are `OK-2026-1`/`MS-2026-1`, with independent reference keys. No historical admission count supersedes this source baseline.

Reviewed Phase 6E-I/J GA/VA, 6E-K/L OK/MS, 6E-E/F KY/CO preparation and Arizona B3 reports against current code. Their past test counts are historical evidence, not tests rerun for this audit.

### Reusable components and actual locations

Paths below are relative to `src/main/java/com/daviddunn/retirementplanner/`.

| Component | Inspected behavior / reuse |
|---|---|
| `domain/model/StateTaxAssumptions.java` | One full-year jurisdiction and simplified method; effective-rate overrides are disabled. No new annual state selector or multistate framework needed. |
| `domain/tax/state/StateIncomeTaxCalculator.java`, `StateTaxCalculationRequest.java` | Annual request already includes year, filing status, `TaxHouseholdContext`, attributed `TaxIncome`, full `FederalTaxCalculation` and projected government rules. Alabama does not need a new parameter merely to see federal liability. |
| `domain/tax/state/ProductionStateTaxResult.java`, `StateTaxUnavailableException.java` | Immutable result, complete limitations and structured unsupported reasons; unavailable tax is not zero. Funding requires a calculated result. |
| `domain/tax/state/TaxHouseholdContext.java` | DOBs, actual/active owners, representative distribution date and selected pension receipts. No all-payment-date ledger, plan-law classification or federal payment history. |
| `domain/tax/TaxIncome.java`, `TaxIncomeCalculator.java`, `TaxIncomeAttribution.java` | Scalars for pension, SS, taxable withdrawals, conversion, ordinary investments and admitted conditional income; attachment changes metadata, not those ordinary amounts. Conversion enters once. |
| `domain/income/IncomeSourceAttribution.java` | Account identity/type/owner, transaction kind/purpose/year, destination, taxable portion and frozen pension/conditional facts. Preserves current recipient separately from original pension owner. No defined-benefit enum, gain holding period or IRA basis history. |
| `domain/model/RetirementParticipantFacts.java` | Dated separation and retirement-conditions evidence. Its class explicitly says these are not plan qualification conclusions. Undated affirmative facts do not establish a date. |
| `domain/income/Pension.java`, `HouseholdPensionIncomeCalculator.java` | Monthly benefit, COLA, survivor benefit, start/end dates and participant facts. None establishes §414(j) defined-benefit status. `PensionFacts` freezes original owner/current receipt evidence but lacks classification. |
| `domain/tax/FederalTaxCalculation.java`, `FederalTaxBaseBreakdown.java` | AGI, taxable SS, modeled basic deduction/taxable base and federal total, optional NIIT evidence. Coverage is `MODELED_STANDARD_DEDUCTION_ONLY_V1`, not a complete tax return. |
| `domain/tax/FederalTaxCalculator.java` | Computes modeled ordinary federal tax; adds NIIT once when the guarded policy models it. Does not independently calculate ordinary age/enhanced-senior deductions or a federal credit/payment ledger. |
| `domain/tax/TaxFundingCalculator.java` | Rebuilds real withdrawal allocations, income, attribution, federal/state tax every trial. `MAX_ITERATIONS=100`, absolute tolerance `$0.01`; updates trial to taxes less available cash, then final recalculation. |
| `domain/roth/RothConversionBracketFillCalculator.java` | Iterative federal target incorporating funding. Capacity-complete trials are currently limited to KY/GA/OK/MS. Their branch prevents an unexecuted hypothetical residual from acquiring guessed owners. |
| `domain/roth/ProjectedPortfolioRothConverter.java` | Primary then spouse, persisted account order, same-owner Roth IRA destination, actual capacity and shortfall. Actual conversions never include RMD dollars or mutate live balances. |
| `domain/projection/ProjectionEngine.java` | Orchestrates RMDs, actual conversions, iterative taxes and annual results. Calls `HouseholdCashSettlement` and retains genuine surplus. Add no AL/AR financial arithmetic here. Ordinary brokerage growth/proceeds are not automatically realized taxable gains. |
| `domain/tax/state/OklahomaProductionTaxCalculator.java`, `oklahoma/OklahomaInvestmentReconciliation.java` | Strong latest per-recipient/source/B3 validation pattern. Government rules and $10,000 pool are not Alabama/Arkansas law. |
| `domain/tax/state/MississippiProductionTaxCalculator.java`, `mississippi/MississippiRetirementEligibility.java` | Useful source/receipt-interval and material-birthday refusal pattern, not reusable Mississippi legal qualification. Its married-column law must not be copied to AR. |
| GA/KY/VA/NC/AZ/IL/IA/PA calculators and Michigan adapter under `domain/tax/state/` | Existing small rules, recipient caps, age worksheets, admission, limitations and source-reconciliation examples. Preserve all existing calculations and goldens. |

Account types actually represented are traditional/rollover IRA, 401(k), 403(b), Roth, inherited IRA, brokerage/checking/savings. There is no separate cash-balance or 457 account class to certify by inference.

## 3. Alabama 2026 and future law

**Law:** §40-18-5 and [Rule 810-3-5-.01](https://admincode.legislature.state.al.us/api/rule/810-3-5-.01) provide marginal slices:

| Single taxable income `T` | MFJ taxable income `T` | Rate on the slice |
|---|---|---:|
| First $500 | First $1,000 | 2% |
| Next $2,500 | Next $5,000 | 4% |
| Above $3,000 | Above $6,000 | 5% |

Thus above the final boundary, Single tax is `.05T − 40`, MFJ `.05T − 80`. Floor the admitted taxable base at zero; no negative planning tax.

Alabama defines its own gross/net income under §§40-18-14/15. It does **not** simply adopt federal taxable income. A state-local bridge from modeled FAGI is practical only for reconciled ordinary sources: remove federally included SS and legally exempt retirement amounts; respect Alabama basis/source differences; then apply Alabama deductions/exemptions. Federal basic, additional-age and enhanced-senior deductions do not directly become Alabama deductions. Their effect on federal liability can indirectly affect Alabama's federal-tax deduction.

**2026 standard deduction:** Single maximum **$3,000**, minimum **$2,500**; MFJ maximum **$8,500**, minimum **$5,000**. It depends on **Alabama AGI before the federal-tax and standard deductions**, not federal taxable income. The official chart retains the maximum through $25,999, then uses completed $500 steps from $26,000. For the proposed cent extension:

`k = max(floor((AL_AGI − 25,500) / 500), 0)`

`D_single = max(3,000 − 25k, 2,500)`

`D_joint = max(8,500 − 175k, 5,000)`.

Both reach their floor at $35,500. Test every $500 boundary, including fractions. The chart's completed-step treatment must not be replaced with linear phaseout or ceiling rounding. Authority: [§40-18-15](https://alison.legislature.state.al.us/code-of-alabama?section=40-18-15), **enrolled 2026 HB527**, §15(b)(4), and official 2026 withholding chart. Withholding confirms dated amounts; its federal withholding formula does not replace the individual return's liability worksheet.

Personal exemption: **$1,500 Single / $3,000 MFJ**. These are deductions, not tax credits. Alabama has income-tested dependent exemptions ($1,000 at AGI through $50,000; $500 above $50,000 through $100,000; $300 above), but dependent eligibility is outside the proposed ordinary nondependent-filer subset. No automatic separate age-65 personal exemption is inferred.

**Subsequent law checked:** enrolled [2026 HB341 / Act 2026-603](https://alison.legislature.state.al.us/files/pdf/SearchableInstruments/2026RS/HB341-enr.pdf) retains the $6,000 senior retirement clause and DB exemption; adds limited National Guard IDT relief for 2027–2029. [HB527 / Act 2026-604](https://alison.legislature.state.al.us/files/pdf/SearchableInstruments/2026RS/HB527-enr.pdf) retains ordinary deduction amounts and introduces limited overtime-premium relief for 2026–2028. Those specialized wages are outside ordinary retirement scope. The proposed 2025 HB388 $12,000 retirement expansion and HB389 higher deductions are not enacted constants. The unrelated **2026** HB388 is a sales-tax bill, not that retirement proposal.

No verified unconditional future change to these ordinary AL bands, exemption or $6,000 cap was found. Recommend nominal continuation of reviewed fixed law; do not inflate these fixed amounts using the plan's inflation assumption. Final 2026 return instructions must be checked again before implementation constants are admitted.

## 4. Alabama federal deduction: legal basis and model compatibility

### Timing and eligible amounts

Use the **individual** [Rule 810-3-15-.20](https://admincode.legislature.state.al.us/api/rule/810-3-15-.20), not the similarly titled corporate FAQ as controlling authority. The rule distinguishes cash/accrual, then selects the accrual method under (3)(c)2(ii); uncontested federal tax is deducted for the year imposed. The individual Form 40 worksheet uses that year's federal return. Ordinary annual planning need not deduct withholding plus estimates plus the final balance, and must never double-count those payments.

The [Form 40 federal deduction worksheet, p.31](https://www.revenue.alabama.gov/wp-content/uploads/2026/01/25f40bk.pdf) takes federal **line 22 tax after nonrefundable credits**, **adds Form 8960 NIIT**, subtracts the enumerated refundable credits (EIC, additional child, refundable education/adoption and Form 2439 credits), and floors at zero. Federal total payments/refund or Form 1040 total-tax line are not substitutes. Self-employment tax, additional Medicare payroll tax, household-employment tax, penalties/interest and other excluded charges must not be indiscriminately deducted. NIIT is specifically included by the current worksheet; do not subtract it from the planner's total and then forget it, or add it a second time.

Previously deducted federal tax later refunded after an audit/amendment can require Alabama income inclusion to the extent of prior benefit; contested tax and transition years have special timing. These are outside the ordinary no-prior-adjustment subset, with disclosure/structured refusal when reliably represented. A normal refund of excess current withholding does not establish that the current year's accrued liability should be reduced by that refund again.

### What the current model supplies and omits

`getFederalIncomeTax()` already includes modeled NIIT when evidenced. It has no separate federal credits, AMT, tax-payment history or age-deduction breakdown. `niit().isEmpty()` is a coverage condition, not a legal declaration of zero NIIT. The ordinary domestic, no-credits/no-preference subset can compute an eligible federal deduction without a new persistent field. A field alone would not repair omitted liability arithmetic.

**Quantified ordinary senior gap, 2026 Single:** IRA $50,000, no other income. Basic federal deduction $16,100 gives modeled taxable income $33,900 and regular tax **$3,820**. Applying the additional-age $2,050 and temporary senior $6,000 deductions gives $25,850 and **$2,854** (10% of $12,400 plus 12% of $13,450). Both use independently sourced 2026 federal brackets. After AL's $6,000 retirement cap, AL AGI is $44,000, standard deduction $2,500, exemption $1,500:

| Eligible federal-deduction input | AL taxable base | AL planning tax |
|---|---:|---:|
| Raw modeled $3,820 | $36,180 | $1,769.00 |
| Ordinary age-corrected $2,854 | $37,146 | $1,817.30 |

Blindly using modeled federal total understates AL tax by **$48.30** here. For MFJ two seniors with $100,000 ordinary income, corrected federal tax is $5,804 versus $7,640 basic-only; AL tax differs by **$91.80**. This is recurring retirement scope, not merely a rare credit edge.

**Recommended proposal:** `alabama/AlabamaFederalLiabilityDeduction` derives an AL-only deduction estimate using existing projected federal brackets/basic deduction, actual FAGI/status/ages, applicable additional-age amounts and the temporary senior deduction/phaseout. Use existing `IncomeTaxCalculator` arithmetic, not a second federal engine. Add evidenced NIIT once; leave `FederalTaxCalculator`, its returned tax, Medicare and federal cash-funding result unchanged. Record that the AL deduction estimate can differ from the modeled federal-tax cash row. This intentional distinction requires approval and focused reconciliation tests.

For the domestic senior subset, the temporary deduction is available through **2028**, with the $6,000 per eligible individual reduced by 6% of MAGI above $75,000 Single/$150,000 MFJ, floored per eligible individual; no continuation after expiration. Additional-age amounts and basic/bracket projections must be year-aware and distinguish published amounts from future model assumptions. Sources: [IRS RP 2025-32](https://www.irs.gov/pub/irs-drop/rp-25-32.pdf), [IRS Schedule 1-A](https://www.irs.gov/pub/irs-pdf/f1040s1a.pdf), and current IRS instructions. No copying CO's state-base formula: this helper estimates a **deduction for federal liability**, not AL federal taxable income.

**Alternative requiring explicit approval:** use raw modeled liability consistently and disclose its senior/credit/preferential-rate limitations and direction of error. Timing is legally supportable, but the amount is not an automatically adequate retirement estimate. Known credits, foreign MAGI, federal preferences, AMT or audit/payment-history situations outside the chosen coverage must not be presented as exact. Unclassified ordinary investment dollars may retain the existing ordinary-income approximation, with a specific warning that overstated modeled federal tax can understate AL tax.

## 5. Alabama pension qualification matrix

**Law/guidance:** §40-18-19(a)(6) and [Rule 810-3-19-.04](https://admincode.legislature.state.al.us/api/rule/810-3-19-.04) exempt payments to a retiree or designated beneficiary from a §414(j) defined-benefit plan to the extent federally taxable. Certain government systems also have their own exemptions. Nonqualified DB/excess/SERP arrangements can fall within the rule, but no specialized qualification engine is proposed.

| Source | AL legal treatment | Existing facts / recommendation |
|---|---|---|
| Verified ordinary private DB pension | Fully exempt federally included payment | Current pension type does not verify DB. Need narrowly approved fact/assumption before advertising support. |
| Verified cash-balance DB payment | Can qualify as DB, despite hypothetical account presentation | [IRS plan definitions](https://www.irs.gov/retirement-plans/plan-participant-employee/retirement-plans-definitions) identify cash-balance as DB. Account balance/name is not classification. |
| Defined-contribution pension; ordinary 401(k)/403(b) | Taxable state portion, potentially senior pool | Account type helps ordinary employer cash; generic `Pension` does not distinguish DC from DB. |
| Traditional/rollover IRA and RMD | Taxable state portion, potentially senior pool | Own fully taxable modern IRA assumption is feasible; no DB exemption merely because funds once came from an employer. |
| Direct qualified DB survivor pension | Original plan exemption can continue to beneficiary | Freeze original plan classification and use current recipient. Survivor's age does not limit full DB relief. |
| DB lump sum paid directly | Plan classification matters | Outside routine monthly pension execution unless exact source facts establish it. |
| DB amount rolled into IRA | Rule creates AL basis for exempt rolled principal; later earnings taxable | Existing account has no AL rollover-origin/basis ledger. Represented special origin/basis is unsupported; no blanket continued DB exemption on all IRA withdrawals. |
| Government/military/railroad/deferred compensation | Source-specific law, sometimes fully exempt | No classification from names. Refuse represented special facts that ordinary model cannot calculate; do not invent service history. |
| Commercial annuity | No automatic DB exemption; taxable fraction may enter Schedule RS | Preserve admitted annuity/basis/NIIT evidence; separate senior-pool interpretation in Section 6. |
| Qualified Roth | Outside admitted taxable base | Existing qualified-withdrawal convention; no double subtraction. |

**Concrete data gap:** neither `Pension`, `PensionFacts` nor `RetirementParticipantFacts` records DB/DC legal plan type. Fixed monthly income, COLA, commencement at retirement and separation status are insufficient. The DB/DC choice can change tax on a $50,000 pension from zero to over $1,800; an undisclosed guess is material.

**Recommended narrow enhancement for approval:** one optional pension qualification fact, e.g. `UNKNOWN / DEFINED_BENEFIT_414J / DEFINED_CONTRIBUTION`, default UNKNOWN for existing JSON, captured immutably with original pension facts and inherited by direct survivor receipts. One compact optional selector/help item would be justified only for this distinction; no wizard or service-history fields. Existing states must ignore it financially. An additive optional field still needs explicit approval and frozen-snapshot/backward-compatibility tests; no schema migration is presumed.

If no new fact is approved, initially refuse materially ambiguous generic pension receipts only, while supporting exact IRA/employer/brokerage plans. A disclosed generic-DB assumption is a possible user choice, not verified qualification, and is **not recommended as silent default**. Taxing every generic pension conservatively is also not accurate DB support; its possible substantial overstatement must be approved explicitly. Do not reject unrelated plans because the pension subset is unavailable.

## 6. Alabama age-65 retirement exclusion and annuities

**Verified:** §40-18-19(a)(13), retained in 2026 HB341, exempts the first **$6,000 of taxable retirement income per individual age 65+**, beginning January 1, 2023. It is not income-tested or indexed in reviewed law. [Schedule RS](https://www.revenue.alabama.gov/wp-content/uploads/2026/01/25schrsblk.pdf) calculates primary and spouse separately, capped by each person's otherwise taxable retirement income. No transfer of unused cap; DB-exempt dollars do not consume it. It is separate from the standard deduction/personal exemption.

Own IRA/rollover IRA, ordinary qualified DC distributions, RMDs and taxable IRA conversions can share one recipient pool. A spouse cannot receive two caps by splitting one person's income. A younger direct DB survivor remains exempt under the DB rule; a taxable DC/IRA survivor uses the survivor's own age for the separate senior pool. Do not age a deceased filer into later eligibility. Ordinary midyear age-65 birthdays are compatible with annual household facts. The narrow January 1 next-year/day-before convention and age-at-death boundary need final 2026 guidance or a material-only structured refusal; do not import Oklahoma's convention by analogy.

**Commercial annuity distinction:** an admitted annuitized §72 receipt has a modeled taxable fraction and recovered investment. AL Schedule RS encompasses taxable annuities; it does not establish DB status. Proposal: allow that independently validated taxable annuity portion in the senior pool under the Schedule-RS interpretation, with no DB exemption and no tax on recovered basis. Verify the exact supported annuity qualification during implementation. Nonannuitized surrender/withdrawals, arbitrary conditional benefits or specialized contracts do not become qualifying retirement income merely because payment starts at retirement.

AL may have state basis different from federal basis: pre-1982 IRA contributions, pre-1985 employer contributions and DB rollovers are examples in official instructions. Initial support should explicitly assume fully taxable modern ordinary account distributions and matched admitted annuity basis, refuse represented contrary basis facts, and disclose possible overstatement from unknown historical AL basis. Do not build basis accounting or demand basis for ordinary brokerage principal.

## 7. Alabama Roth conversion treatment

Official Form 40 instructions expressly address traditional IRA-to-Roth conversions: taxable amount is what would have been included on an ordinary distribution, excluding return of AL basis, reported in Schedule RS Part II/III. Read with the senior-pool worksheet, a fully taxable evidenced own-IRA conversion belongs in that person's **same** age-65 $6,000 pool; it is not generally exempt. An under-65 conversion remains included. This conclusion has direct AL conversion inclusion guidance plus the ordinary RS exclusion worksheet, stronger than borrowing another state's exclusion.

Use only completed same-owner actual amounts. IRA withdrawals, RMD and conversion aggregate once; RMD cannot be converted. Tax-funding cash remains a separate distribution, even when associated with a conversion. Qualified Roth cash remains outside the base. Employer-to-Roth and in-plan variants need separate source-specific verification and should initially refuse; existing ability to execute an employer source does not prove state eligibility. Inherited, partial-basis, DB-origin rollover and specialized rollover/conversion transactions remain outside initial coverage when material facts are unrepresented.

A conversion also changes taxable SS, eligible federal liability, senior federal phaseout and AL standard-deduction bands. All must be recomputed each trial. Section 17 shows that a $20,000 conversion can change AL tax by $862.50 in one ordinary case, not mechanically $1,000.

## 8. Arkansas enacted 2026 brackets, deductions and future law

**Law:** [Act 1 / HB1001, 2026 First Extraordinary Session](https://www.arkleg.state.ar.us/Home/FTPDocument?path=/ACTS/2026S1/Public/ACT1.pdf) and companion [Act 2 / SB1](https://www.arkleg.state.ar.us/Home/FTPDocument?path=/ACTS/2026S1/Public/ACT2.pdf), approved in the May 2026 special session, amend §26-51-201(a)(4) effective for tax years beginning January 1, 2026. Do not use the old 3.9% table.

| State net taxable income `N` at/below $94,700 | Marginal rate |
|---|---:|
| $0–$5,599 | 0% |
| $5,600–$11,199 | 2% |
| $11,200–$15,999 | 3% |
| $16,000–$26,399 | 3.4% |
| $26,400–$94,700 | 3.7% |

Above $94,700 the **different** schedule taxes the first **$4,700 at 2%**, the rest at **3.7%**, then applies the bracket adjustment. The zero band and all middle-band benefits are not retained above the transition. Same statutory bands apply to an actual MFJ joint column; do not double them.

Arkansas defines its own gross/adjusted/net income under §§26-51-403/404 and specific conformity provisions. A reconciled ordinary modeled-FAGI bridge can remove federally included SS and eligible retirement relief, preserve ordinary taxable investments and apply state modifications. It is not universal adoption of federal AGI or federal taxable income. Federal below-AGI standard/age/enhanced-senior deductions do not directly enter this base. No Colorado taxable-income bridge is needed.

**Official 2026 planning amounts:** [AR1000ES, revision May 6, 2026](https://www.dfa.arkansas.gov/wp-content/uploads/2026_Final_AR1000ES_2.pdf), states **$2,470 standard deduction per taxpayer**, or **$4,940 actual joint**; ordinary nonrefundable personal tax credit **$29 each**, $58 joint. Age-65 relief includes an additional $29 and a separate $29 “65 Special” where no retirement exemption is claimed. These are **credits after tax**, not $29 deductions. No ordinary personal-exemption deduction is to be invented.

Final return tables may supply indexed amounts and low-income options not fully reproduced by the estimated-payment sheet. The 2025 booklet is evidence for continuing mechanics, not authority to silently promote every indexed 2025 number to 2026. Before production, recheck final 2026 credits/age rules and any low-income values selected for support.

**Future law:** reviewed ordinary top rate remains 3.7%; no unconditional later rate cut was established. Act 1 retains annual table adjustment under §26-51-201(d), with agency-prescribed dollar brackets and rounding to $100. Standard deduction under §26-51-430(c) is CPI-adjusted, capped at 3%, rounded to $10; CPI measurement uses the specified twelve-month period ending August 31. Personal/low-income credits also involve statutory/agency indexing. These differ from the fixed $6,000 retirement cap.

**Proposed long-range convention requiring approval:** use each published reviewed year, then hold the latest reviewed dollar parameters nominally with an explicit statement that actual future indexing may differ. Do not forecast a future rate cut or present plan-inflation multiplication as an enacted agency determination. No verified 2027 annual AR table was located as of this audit; check again at implementation. Corporate 2027 rate changes in the same act are not future individual-rate rules.

## 9. Arkansas high-income adjustment and cent/table policy

For the high schedule, exact statutory slice arithmetic before credits is:

`H(N) = .02 × 4,700 + .037 × (N − 4,700) = .037N − 79.90`.

The adjustment is a **tax subtraction**, not a deduction from income. For integer net incomes $94,701–$94,800 subtract $290; each next $100 band loses $10, ending $97,501–$97,600 at $10. At $97,601 and above subtract zero. Section 17 gives **all 29 band oracles**, not just endpoints.

The law states whole-dollar ranges. The dated AR1000ES extends classifications through `.99` and uses rounded/table tax amounts, rather than universally reproducing exact marginal slices. **Proposed planning extension P:** classify using `floor(N)` (e.g. $94,700.99 stays in the lower schedule; $94,701.00 starts the high schedule); calculate monetary slices on unrounded `N`; use continuous lower-schedule breakpoints 5,600/11,200/16,000/26,400; preserve the full discrete adjustment. Round final tax to cents. This convention requires approval and reconciliation with final annual instructions; it is not a claim that the statute itself specifies fractional-dollar brackets.

Under P the lower gross formula above $26,400 is `.037N − 367.20`. At $94,700 it gives **$3,136.70**; the high formula less $290 at $94,701 gives **$3,134.04**. That small **downward** transition must not be smoothed away without authority. Subsequent adjustment transitions jump approximately **$10** in addition to the ordinary marginal slice. Over a whole $100 adjustment band transition, effective incremental tax is about $13.70; it is not a uniform 3.7% marginal region.

**Official-table contrast, not a corrected legal constant:** AR1000ES gives a **$3,136** lookup for the first high band, while exact H−adjustment at its $94,701 lower endpoint is $3,134.04. Its ordinary schedule also carries rounded base constants (e.g. $111 at the $11,200 row versus $112 from continuous marginal slices). Do not mix those constants with exact statutory slices. Maintain separate return-table and planning-formula fixtures. A claim of penny-identical filed return calculation is not justified by this audit.

The whole-dollar table choice can move a discontinuity, not justify inventing a smooth progressive schedule. Before admission, settle P versus a faithfully verified annual table method and run funding oracles at **both** schedule transitions and **every** adjustment edge, using fractions around each threshold. If current official material does not resolve a chosen policy, withhold AR admission rather than silently changing the tax curve.

## 10. Arkansas retirement exemption and regulation reconciliation

**Controlling law:** §26-51-307 grants one **$6,000 annual pool per taxpayer** across qualifying own-IRA and public/private employment-related retirement benefits. No transfer of unused pool. Recover qualifying investment/basis under the governing §72 method first; do not exempt recovered principal again or grant $6,000 per account. The ordinary cap is not indexed in reviewed law.

**Conflict resolved:** [26 CAR §100-118](https://codeofarrules.arkansas.gov/Rules/Rule?chapterID=33&levelType=section&partID=941&sectionID=40225&subChapterID=261&subPartID=6195&titleID=26) retains an older provision excluding ordinary IRAs unless employer SEP. [Act 817 of 1999](https://www.arkleg.state.ar.us/Home/FTPDocument?path=%2FACTS%2F1999%2FPublic%2FACT817.pdf) expressly added ordinary IRA benefits and the 59½/death/disability rules. [Act 773 of 2001](https://arkleg.state.ar.us/Home/FTPDocument?path=%2FACTS%2F2001%2FPublic%2FACT773.pdf) retains IRA coverage. Current DFA Form AR1000F instructions explicitly recognize ordinary traditional IRA distributions. The stale regulatory sentence cannot override that later statute. The rule's employer-plan provisions remain useful where consistent; its publication/recodification date does not re-enact a contrary IRA restriction.

| Receipt | Governing ordinary eligibility | Proposed support boundary |
|---|---|---|
| Own traditional/rollover IRA cash after 59½ | Age at receipt, one shared $6,000 pool | Existing owner/DOB sufficient when qualified for the entire receipt interval. Fully taxable modern IRA assumption; no special state basis. |
| IRA cash clearly before 59½ | Not eligible except statutory death/disability cases | Include taxable amount; federal penalty exception alone is not enough. Preserve existing federal early-penalty limitations. |
| IRA interval crossing 59½ | Actual receipt timing matters | Refuse material uncertain portion; do not invent that all cash arrived after birthday. Opening RMD/period bounds stay distinct. |
| Ordinary 401(k)/403(b)/employment plan cash | Employer-related system; DFA says recipient need not be retired, lump sums or installments can qualify, federal early penalty can still apply | Approved ordinary qualified employer-plan assumption, with contrary facts overriding. Do not add a blanket 59½/separation/retirement gate. |
| RMD | Same source rules; normal owner RMD ages are already over IRA minimum | Aggregate with that person's other qualified receipts. |
| Direct ordinary private pension / qualified plan annuity | Employment-related source, taxable part after cost recovery | Ordinary employer pension assumption is narrower than AL DB classification: both DB/DC may qualify here. Freeze source/current recipient. |
| Survivor spouse pension | DFA expressly permits survivor spouse, only one $6,000 recipient pool | Own plus survivor income share survivor's cap; no deceased cap transfer. Other beneficiary/QDRO/disability cases need exact facts. |
| Commercial nonqualified investment annuity | Not established as employer retirement plan or IRA by annuitization alone | Tax admitted federally included portion normally; no guessed $6,000 exemption. If state basis/qualification cannot be reconciled, refuse that subset. |
| Military/railroad and specialized benefits | Separate exclusions/coordination | Outside initial ordinary scope if reliable special facts are represented. Military relief can consume the ordinary allowance; do not stack a second full pool. |
| Qualified Roth cash | Not included under admitted qualified Roth treatment | No cap consumption and no double subtraction. Nonqualified Roth is not newly modeled. |

The official instructions distinguish IRA age qualification from employer-plan qualification. Neither current annual timing nor a federal 10% additional-tax result proves every state source eligible. Disability, inherited/nonspousal, partial-basis and special rollover cases remain separate from the ordinary own-account subset.

## 11. Arkansas Roth conversion conclusion

[DFA Subject 300, IRAs](https://www.dfa.arkansas.gov/wp-content/uploads/300-IRAs.pdf) explicitly says traditional-to-Roth conversion produces income at conversion. Its old contribution limits are stale and are not proposed constants. It does not supply a conversion-specific ruling about §26-51-307's pool.

**Interpretation for explicit approval:** an executed, federally included own traditional IRA-to-Roth IRA source distribution received after 59½ can share the ordinary recipient $6,000 pool under §307 and DFA's traditional-IRA distribution instructions. There is no separate conversion exclusion or separate conversion cap. This follows Arkansas's own distribution language and inclusion guidance; it is not Mississippi's clause-(ff) exemption or an assumed Oklahoma/Kentucky rule.

For a normal own-IRA conversion before 59½, the source fails §307's age condition absent a separately established death/disability exception; include it in the ordinary state base. Lack of a federal conversion penalty does not erase the state age condition. Age-crossing annual conversion evidence lacks execution date; require entire-period qualification or a material-only unsupported result unless a reliable actual date is available. Do not invent a December execution date.

If this pool interpretation is not approved or authoritative follow-up contradicts it, return structured unsupported for material age-eligible conversions rather than silently granting relief or treating their potential exemption as legally zero. Ordinary nonconversion IRA/RMD/pension plans remain legally implementable. Routine Roth planning is central to Aledrix, so its limitation must be an explicit initial-admission decision, not buried in a help paragraph. Further research should seek a DFA written interpretation specifically addressing a taxable own-IRA conversion and §307, including its date/age requirement; no such conversion-specific determination was found in the reviewed official materials.

Employer-plan-to-Roth IRA and in-plan variants initially remain unsupported until separately verified. In-plan conversion is not currently the executor's modeled transaction. Partial-basis, inherited and DB-origin specialized transactions require facts the ordinary model does not carry. RMD cash and separately retained tax-funding withdrawals remain separate; no conversion exemption applies merely because cash was withdrawn alongside a conversion.

## 12. Per-person, dates and survivor feasibility

| Required fact | Existing reliability | Consequence |
|---|---|---|
| IRA/employer withdrawal, opening/current RMD owner | Account-reference owner and purpose/year | Sufficient for per-person pools; reconcile to scalar totals. |
| Actual conversion source/destination/recipient | Executor attribution and actual household/owner amounts | Sufficient source identity, not every eligibility date or plan-law variant. |
| Pension current recipient/original owner | `PensionFacts` and selected pension calculator | Sufficient for ordinary survivor aggregation; AL original DB classification missing. |
| DOB and annual filing status | Household context / projection annual status | Supports ordinary annual ages and death-year MFJ/later Single. Do not age inactive owners through later years. |
| All receipt dates during an annual period | Not represented | Material IRA 59½ crossings need bounds/refusal, not a new timing wizard. |
| DB/DC/cash-balance legal classification | Not represented | AL-specific pension gap; Section 5 decision required. |
| Historical AL account basis / DB rollover origin | Not represented | Bounded fully taxable assumption or refuse represented contrary source. |
| Investment ownership / gain holding period | Often aggregate; source kind lacks holding period | Actual-joint AR and ordinary AL do not need invented spouse allocation. Cannot certify AR long-term-gain relief. |
| Prior federal assessments/refunds/credits | Not represented | Ordinary AL no-prior-adjustment/no-credit scope; do not invent history. |

Use current recipient in death year and later survivor years. Aledrix models death as January 1 of the configured year, yet keeps configured MFJ through that year and Single afterward. Preserve that convention in both directions and explain its planning nature. For age-based relief, a deceased filer does not gain eligibility after the modeled death. AR's ordinary age-credit instructions use January 1 following the tax year; the death boundary needs an explicit guard/recheck rather than aging a deceased filer to that later date. Ordinary midyear DOBs are not a reason to reject all retirees.

Neither state needs Colorado's per-person allocation of federally taxable household Social Security for the ordinary subtraction: remove the included household total once. Recipient ownership is needed for retirement pools and relevant ages, not to manufacture separate taxable-SS shares.

AR **actual joint filing** is legally available, with one combined taxable-income column and ordinary joint standard deduction. Married filing separately on the same return can be lower but needs income ownership and separate computations. Recommend actual-joint initial support with a prominent possible-overstatement disclosure, not an automatic 50/50 split or filing optimizer. Individual retirement caps still apply separately before combining income.

## 13. Funding and bracket-fill implications

### Reuse and trial capacity

Keep the existing iterative solver and RMD-before-conversion order. Each trial must rebuild withdrawn account amounts, current owners and executed conversions; reconcile ordinary income, taxable SS, both states' adjustments, year/status, AL eligible federal deduction and all applicable deductions/credits. Cash/brokerage principal funding need not create taxable retirement cash. Traditional funding can exhaust pools and cross bands. No scalar conversion-times-rate replacement is valid.

**Proposed future reproduction, not an audit test:** copy the existing KY/GA/OK/MS over-capacity fixtures with a $1,000 same-owner executable source and larger target, including missing opposite-owner Roth destinations. Both AL's senior pool and AR's source/age-qualified pool make an unowned residual material. Only after reproducing the defect should the existing capacity-complete branch be extended to the affected new state. Actual execution/shortfall, account order, federal targets and the other 19 jurisdictions must remain unchanged.

### Alabama segment oracles

Single age 60, ordinary fully taxable IRA $50,000, no SS/credits, fully exhausted state deduction phaseout, unlimited same-owner IRA and no available tax cash. Independent 2026 regular federal tax is `F(W)=3,820+.12W`; AL tax is `S(W)=2,069+.044W`, since federal liability is deductible. Then:

`W = 5,889 / (1 − .12 − .044) = 7,044.25837…`.

Cent-closing oracle: **W $7,044.26; federal $4,665.31; AL $2,378.95**. Cash/brokerage-principal-funded taxes instead leave AL $2,069.00 and create no extra IRA income. This does not infer unmodeled brokerage gains.

Add an executed $20,000 IRA conversion: initial F $6,570, AL $2,931.50; the applicable trial segment is F marginal 22%, AL marginal `.05×(1−.22)=3.9%`. Funding **$12,822.54** closes with federal **$9,390.96**, AL **$3,431.58**. Verify same-owner capacity and federal band coverage. Cross an AL $500 deduction boundary, age-65 pool exhaustion, senior federal phaseout/expiration and SS taxation in separate tests; those are piecewise rather than the same linear oracle.

For the recommended AL senior liability helper, federal cash remains the existing modeled amount while the eligible AL deduction is separately reconciled. Oracles must explicitly carry both values; do not assert they are equal by definition.

### Arkansas ordinary convergence and a boundary counterexample

Single age 60, own IRA $50,000 qualified for the full year, $6,000 pool already used, standard deduction $2,470, personal credit $29, no SS: initial AR tax under P is **$1,140.41**. In the ordinary low/middle segment `F(W)=3,820+.12W`, `S(W)=1,140.41+.037W`. Unrounded W is `4,960.41/.843 = 5,884.234875…`; cent-closing **$5,884.24 = federal $4,526.11 + AR $1,358.13**. Match the existing cent tolerance; do not demand a differently rounded unrounded root.

**Independent mathematical admission counterexample under policy P:** Single age 60, ordinary own IRA withdrawals before tax funding **$86,197**, no SS, no available tax cash, source qualified throughout, fully taxable, no investment/basis/credit ambiguity. The $6,000 pool is exhausted; standard deduction $2,470 gives initial net taxable income **$77,727**. Federal 2026 tax throughout the relevant final segment is:

`F(W) = 10,133.34 + .22W`.

Below the schedule transition, net AR tax after $29 credit is `2,479.699 + .037W`. In the first high-adjustment band it is `2,476.999 + .037W`.

| Cent-rounded current trial W | AR net income | Federal tax | AR tax | Next required W |
|---:|---:|---:|---:|---:|
| $16,972.94 | $94,699.94 | $13,867.39 | $3,107.70 | $16,975.09 |
| $16,975.09 | $94,702.09 | $13,867.86 | $3,105.08 | $16,972.94 |

The independent decimal iteration still alternates after 100 updates, difference **$2.15**, above the production tolerance. The putative low-branch unrounded root $16,975.82638 lies in the high band; the putative high-branch root $16,972.19246 lies in the low band. Neither is self-consistent. This is not cured by more iterations, a fixed marginal gross-up or an invented smooth adjustment. It is not a production test failure: no AR calculator exists and no test/source was added here.

The same aggregate premise can be built from **IRA cash $66,197 plus an actual same-owner IRA conversion $20,000**; both already share the exhausted pool. Under P, a future minimal-sufficient-candidate oracle is **W $16,974.00**, N $94,701.00, federal $13,867.62, AR $3,105.04, with **$1.34 genuine surplus**. One cent less leaves N $94,700.99 and is insufficient. This is a proposed validation target for a separately approved correction, not current solver behavior.

**Gate:** reproduce against the chosen official/cent policy and actual account-backed funding in Phase N before registration. Table/whole-dollar conventions can change the cycle's location/values; the statutory-versus-table issue in Section 9 must be settled first. Upward adjustment steps can create overlapping candidate solutions; do not misdescribe all steps as downward jumps or assume one global marginal rate.

**Smallest proposed decision if confirmed:** an AR-only bounded discontinuity handler could select a verified sufficient funding candidate and retain genuine surplus through existing `HouseholdCashSettlement`, rather than demand a mathematically nonexistent equality. It must validate final source allocation/taxes, avoid underfunding, choose a documented minimal sufficient candidate, and preserve retained-cash accounting and all existing-state semantics. `ProjectionEngine` already deposits genuine surplus; that does not prove a new funding result is safe. Returning more withdrawal than exact tax is a financial-semantics decision requiring explicit approval and tests. If it cannot be confined safely, stop AR admission; do not redesign the general solver or hide nonconvergence as zero tax. Alabama remains independent.

## 14. Brokerage B3 and investment adjustments

Preserve actual Arizona B3 behavior: brokerage `grossAmount` is cash, optional missing taxable amount is unknown, and ordinary principal does not enter AGI. Missing basis alone must not block a projection. Count already modeled investment income once. `TaxIncomeCalculator` emits an unowned INTEREST placeholder; attached CAPITAL_GAIN/DIVIDEND can describe those same dollars without replacing a different-kind placeholder. Reuse the latest OK/GA local reconciliation pattern, not naive summation. Validate contradictory/excessive/duplicate/wrong-year evidence; do not weaken shared attribution or NIIT guards.

AL generally taxes ordinary capital gains with ordinary rates, with source-specific exceptions (e.g. US obligations, qualifying bullion relief and historical basis). AR generally taxes ordinary interest/dividends, but **50% of qualifying net long-term gains is excluded**, with special treatment above $10 million; short-term gains do not receive that relief. Authority: §26-51-815 and DFA Schedule AR1000D in the official return package. No automatic 50% reduction of the entire modeled investment aggregate or a record labeled merely CAPITAL_GAIN.

**Proposal:** unclassified ordinary modeled investments remain taxable once, with explicit possible-overstatement disclosure for unmodeled AR long-term-gain relief/state special sourcing. Known incompatible facts must refuse, not be relabeled unknown. If exact long-term relief is demanded, a narrow reliable holding-period/net-gain fact is required; do not add tax lots or infer it from holding a brokerage account. Missing realized gains can understate both state and federal tax; missing state relief can overstate state tax. AL additionally has the direction-of-error interaction through its federal deduction.

## 15. Source admission and common-scenario matrix

Categories describe **proposed feasibility**, not currently admitted AL/AR behavior: **A** existing reliable facts; **B** disclosed approximation requiring approval; **C** materially missing/contradictory evidence, structured unsupported; **D** outside ordinary scope.

| Scenario | Alabama | Arkansas |
|---|---|---|
| SS only | A; no state SS inclusion | A; no state SS inclusion |
| Fully taxable modern own IRA/rollover, no special basis | B ordinary basis assumption; age-65 pool when eligible | B source assumption; A age test if entire interval after 59½ |
| RMD plus IRA cash | A ownership/sequence; same AL pool | A ownership/sequence; same AR pool |
| Clearly early ordinary IRA cash | B included; no AL general 59½ pool gate | A included, no exemption; federal penalty limitations remain |
| Material 59½ annual crossing | No imported IRA age gate for AL's age-65 pool | C unless timing/other exhausted pool makes outcome unambiguous |
| Own IRA-to-Roth conversion | A inclusion and RS treatment; source/basis boundaries B/C | Inclusion A; age-qualified pool Interpretation/B pending approval |
| Employer/in-plan conversion | C/D initially pending independent verification | C/D initially pending independent verification |
| Qualified Roth cash | B existing qualified-withdrawal convention; no new exclusion | Same |
| Known §414(j) DB pension/cash-balance benefit | Law supports exemption, but current model lacks classification: C until approved enhancement | B ordinary employment-plan source; $6,000 cap, not full DB exemption |
| Generic unclassified Pension | C for exact AL exemption; approved explicit assumption is alternative B | B ordinary employer pension assumption, adverse facts override |
| DC pension/ordinary employer cash | B ordinary plan qualification; senior pool | B ordinary employment-plan scope; no new blanket retirement/age gate |
| Direct survivor pension | Original classification + current recipient needed; AL DB gap C | B ordinary spouse beneficiary, recipient's one pool |
| Admitted commercial taxable annuity | B taxable fraction; senior-pool RS interpretation needs approval, no DB inference | B taxable fraction with no retirement exemption; C if state basis/source cannot reconcile |
| Brokerage principal, unknown basis | B admitted cash approximation, no invented gains | Same |
| Modeled investment income | B ordinary bucket; federal-liability preference caveat | B ordinary bucket; missing long-term relief may overstate |
| Actual MFJ, different ages/owners | A ownership, B federal-liability coverage/classification choice | A individual pools, B actual-joint election instead of separate optimization |
| Represented special government/military, inherited, disability, QDRO or basis histories | C/D unless exact supported exception established | C/D; avoid stacking military and ordinary relief |
| Contradictory attributed income | C | C |
| Ordinary tax-funded conversion near AR transition | AL independent acceptance needed | C release gate until Section 13 resolved; not a permanent acceptable silent limitation |

## 16. Minimal implementation architecture

If approved, create independent:

- `domain/tax/state/AlabamaProductionTaxCalculator.java`, `alabama/AlabamaTaxRules.java`.
- AL-local federal-liability deduction and recipient retirement/pension helpers, with immutable intermediate worksheets if needed.
- `domain/tax/state/ArkansasProductionTaxCalculator.java`, `arkansas/ArkansasTaxRules.java`.
- AR-local receipt eligibility and high-income adjustment helper; an actual-joint standard-subset worksheet, not a general return optimizer.

Reuse `StateTaxCalculationRequest`; it already supplies the federal result, projected rules and household facts. No new federal result field is necessary for the proposed ordinary no-credit bridge. If implementation expands to actual credits/refunds/AMT, current data is insufficient; stop rather than invent them. Do not expose mutable federal calculations.

No general state-law/source framework, security-lot subsystem or new annual projection branch. The AL pension fact is a specific optional enhancement requiring approval, not authorization to classify every pension plan. It must default safely and be frozen in `PensionFacts`; existing states' behavior must remain unchanged. A schema migration is not presumed, but additive JSON/snapshot handling must be verified explicitly. No new fact is needed merely to split investments for actual-joint filing.

Each calculator must reconcile the ordinary federal/scalar/source base before exclusions, distinguish calculated zero from unsupported, return complete limitations, and use immutable versioned rules. Future keys: **`alabama-income-tax-rules`**, **`arkansas-income-tax-rules`**. Do not add either enum/router entry until its own gates pass.

## 17. Independent numeric golden designs

All values below were calculated with independent decimal worksheet arithmetic from the cited legal slices and stated inputs, **not production outputs**. No new Java/test file or integration suite was created or run. `HALF_UP` cents are proposed planning values; filed forms may use whole-dollar entries and lookup tables. P denotes the specific Arkansas extension in Section 9 and must be approved/reconciled before its values become production goldens.

### Alabama direct fixtures

Unless stated, full-year nondependent Single, no itemization/credits/special basis; F is the **specified eligible federal liability deduction**, not unlisted tax-funded cash. DBA means independently verified DB classification, which current inputs do not establish.

| ID | Independent premise | Expected planning result |
|---|---|---:|
| AL-01 | Taxable base 0 / 500 / 3,000 Single | 0.00 / 10.00 / 110.00 |
| AL-02 | Taxable base 0 / 1,000 / 6,000 MFJ | 0.00 / 20.00 / 220.00 |
| AL-03 | Each preceding upper boundary ±$0.01 | Continuous marginal-slice values, final cents; no whole-base top-rate application |
| AL-04 | AL AGI 25,999.99 / 26,000, F=0 | Single D 3,000/2,975, tax 1,035.00/1,036.25 |
| AL-05 | Same AGI pair, MFJ F=0 | D 8,500/8,325, tax 645.00/653.75 |
| AL-06 | AGI 35,499.99 / 35,500 | D Single 2,525/2,500; joint 5,175/5,000; extend every intervening $500 band |
| AL-07 | Ordinary nonretirement AL AGI 50,000, F=0 / 5,000 / 50,000 | 2,260.00 / 2,010.00 / 0.00 |
| AL-08 | Age 60 IRA 50,000, F=3,820 | AL AGI 50,000; tax 2,069.00 |
| AL-09 | Age 60 IRA 50,000 + executed IRA conversion 20,000, F=6,570 | AGI 70,000; tax 2,931.50; conversion increment 862.50 |
| AL-10 | Age 65 IRA 50,000, F=2,854 | Cap 6,000; AGI 44,000; tax 1,817.30 |
| AL-11 | AL-10 using raw model F=3,820, deliberately contrasted | 1,769.00; quantified 48.30 understatement, not interchangeable fixture |
| AL-12 | DBA pension 50,000, any age, F=2,854 | State pension base/tax 0; no extra $6,000 subtraction |
| AL-13 | Age 70 DBA pension 30,000 + IRA 20,000 + conversion 20,000; FAGI 70,000, F=5,254 | AL AGI 34,000; D 2,575; taxable base 24,671; tax 1,193.55 |
| AL-14 | MFJ both 65+, IRA 50,000 each, F=5,804 | Caps 12,000; AL AGI 88,000; taxable base 74,196; tax 3,629.80 |
| AL-15 | Same total/ages, all IRA owned by primary, F=5,804 | Only cap 6,000; AL tax 3,929.80 |
| AL-16 | Single age 65 IRA 50,000 plus gross SS 20,000, taxable SS 17,000, FAGI 67,000, F=4,894 | Remove only included SS; AL AGI 44,000; tax 1,715.30 |
| AL-17 | Admitted annuity gross 50,000/taxable 20,000, age 65, F=0, RS senior interpretation | Cap 6,000; AL AGI 14,000; tax 435.00; basis not retaxed |
| AL-18 | Same taxable 20,000 commercial receipt age 60, F=390 | No senior cap; taxable base 15,110; tax 715.50 |
| AL-19 | Principal-only brokerage 100,000, modeled taxable income/F=0 | 0.00, with B3 limitations |
| AL-20 | Five repeated AL-08 state-only rows with constant stated F and held rules | Annual 2,069.00; sum 10,345.00, not a prediction of actual future federal tables |
| AL-21 | Actual senior birthday, January 1/death edges, both survivor directions | Own age/cap only; unknown material convention refuses, no deceased cap transfer |
| AL-22 | DB lump sum rolled into IRA / represented state basis / unknown generic pension classification | Structured unsupported until exact scope/fact approved; not zero |

AL-18's F=390 follows basic-only federal taxable $3,900 at 10%, before any unlisted income. For AL-14 corrected MFJ federal taxable income is $52,500 after $32,200 basic, $3,300 additional-age and $12,000 temporary senior deductions: $2,480 + 12%×$27,700 = $5,804. These premises prevent the prior common fixture error of mixing an actual legal federal amount with a modeled basic-only amount.

Add deduction-helper unit fixtures for F line22=4,000, NIIT=500, enumerated refundable credits=100 -> eligible **4,400**, regardless of withholding; credits larger than eligible tax -> zero. Known prior audit refund cases are unsupported/out-of-scope, not generated from current cash. Test 2028/2029 senior expiration, one/two seniors and $75,000/$150,000 phaseout boundaries using official federal worksheets independently.

### Arkansas regular-path fixtures under P

“Tax” includes stated ordinary credits, excludes unapproved optional low-income tables/additional credits. Where income is low, specify gross tax separately rather than calling it a fully optimized legal return. Ordinary age 60 means IRA eligible throughout; age 50 cash is clearly early. Basis assumed fully taxable unless stated.

| ID | Independent premise | Expected result |
|---|---|---:|
| AR-01 | Net taxable N=0 / 5,600 / 11,200 / 16,000 / 26,400 | Gross tax 0.00 / 0.00 / 112.00 / 256.00 / 609.60 |
| AR-02 | Each lower threshold ±$0.01, and 94,700.99 / 94,701.00 | Classify under P; test separately from return rounding |
| AR-03 | N=94,700 / 94,701, before credits | 3,136.70 / 3,134.04 |
| AR-04 | N=94,800 / 94,801, before credits | 3,137.70 / 3,147.74 |
| AR-05 | N=97,600 / 97,601 / 100,000, before credits | 3,521.30 / 3,531.34 / 3,620.10 |
| AR-06 | Single age 60 ordinary investments 50,000; D=2,470, credit 29 | 1,362.41 |
| AR-07 | AR-06 plus eligible IRA 5,000 / 6,000 / 7,000 | Pool 5,000/6,000/6,000; tax 1,362.41 / 1,362.41 / 1,399.41 |
| AR-08 | Age 60 qualified IRA 50,000; D=2,470, credit 29 | 1,140.41 |
| AR-09 | Age 50 own IRA cash 50,000; no exception | 1,362.41; no $6,000 relief |
| AR-10 | Ordinary employer-plan cash 50,000, age 50, verified ordinary employment plan | 1,140.41; not disqualified merely by IRA age rule |
| AR-11 | Age 60 ordinary IRA cash 30,000 + executed IRA conversion 20,000, one owner, approved interpretation | Same as AR-08, one cap: 1,140.41 |
| AR-12 | Age 50 own IRA conversion50,000, no death/disability | 1,362.41; not Mississippi-style full exemption |
| AR-13 | Annual own-IRA receipt crosses59½, unknown timing | Materially ambiguous result unsupported; compare fully early/qualified bounded amounts |
| AR-14 | MFJ age 60 each, IRA 50,000 each, actual joint | Caps12,000; N83,060; credits 58; 2,648.02 |
| AR-15 | Same total, only one IRA owner | Cap6,000; N89,060; 2,870.02 |
| AR-16 | AR-14 separately computed actual-owner columns, legal method contrast only | 2×1,140.41 = 2,280.82; actual-joint approximation can overstate367.20 |
| AR-17 | Age 75 qualified own RMD 50,000, or RMD 30,000 plus executed IRA conversion 20,000; personal+age credits 58 | 1,111.41; no additional 65 Special while claiming retirement pool |
| AR-18 | Age 70 investments50,000, no retirement exemption claimed; three ordinary credits 87 | 1,304.41; verify eligibility using final 2026 age/credit worksheet |
| AR-19 | Commercial annuity gross50,000/taxable20,000, no retirement-plan evidence | Taxable state gross20,000, no 6,000 relief; compare gross P tax308.02 before credits/optional low-income path |
| AR-20 | Qualified employer plan annuity taxable20,000 | State gross14,000 after cap; no retaxing recovered investment; lower-income final choice needs explicit worksheet policy |
| AR-21 | Ordinary50,000 + already-modeled gain20,000, holding period unavailable | Include once, N67,530, credit 29, 2,102.41 under approved no-relief approximation |
| AR-22 | Same with independently verified qualifying net LTCG20,000, legal worksheet contrast | Half excluded, N57,530; 1,732.41; no inferred holding period in production |
| AR-23 | Brokerage principal100,000, no modeled taxable income | 0.00 with limitations |
| AR-24 | Surviving spouse receives ordinary pension50,000 plus own IRA 50,000, age 60; Single | One6,000 pool; N91,530, credit 29; 2,990.41 |
| AR-25 | Five repeated AR-08 state-only years, nominal held parameters | Annual1,140.41; sum5,702.05; disclosed future-index assumption |
| AR-26 | Wrong year / duplicate / excessive gain evidence; unsupported special basis or conversion variant | Structured unsupported, never calculated zero |

**Every high-adjustment band**, gross tax before personal/age credits, from exact H minus the statutory subtraction:

| Integer classification range | Tax subtraction | Tax at first dollar | Tax at last dollar |
|---|---:|---:|---:|
| 94,701–94,800 | 290 | 3,134.04 | 3,137.70 |
| 94,801–94,900 | 280 | 3,147.74 | 3,151.40 |
| 94,901–95,000 | 270 | 3,161.44 | 3,165.10 |
| 95,001–95,100 | 260 | 3,175.14 | 3,178.80 |
| 95,101–95,200 | 250 | 3,188.84 | 3,192.50 |
| 95,201–95,300 | 240 | 3,202.54 | 3,206.20 |
| 95,301–95,400 | 230 | 3,216.24 | 3,219.90 |
| 95,401–95,500 | 220 | 3,229.94 | 3,233.60 |
| 95,501–95,600 | 210 | 3,243.64 | 3,247.30 |
| 95,601–95,700 | 200 | 3,257.34 | 3,261.00 |
| 95,701–95,800 | 190 | 3,271.04 | 3,274.70 |
| 95,801–95,900 | 180 | 3,284.74 | 3,288.40 |
| 95,901–96,000 | 170 | 3,298.44 | 3,302.10 |
| 96,001–96,100 | 160 | 3,312.14 | 3,315.80 |
| 96,101–96,200 | 150 | 3,325.84 | 3,329.50 |
| 96,201–96,300 | 140 | 3,339.54 | 3,343.20 |
| 96,301–96,400 | 130 | 3,353.24 | 3,356.90 |
| 96,401–96,500 | 120 | 3,366.94 | 3,370.60 |
| 96,501–96,600 | 110 | 3,380.64 | 3,384.30 |
| 96,601–96,700 | 100 | 3,394.34 | 3,398.00 |
| 96,701–96,800 | 90 | 3,408.04 | 3,411.70 |
| 96,801–96,900 | 80 | 3,421.74 | 3,425.40 |
| 96,901–97,000 | 70 | 3,435.44 | 3,439.10 |
| 97,001–97,100 | 60 | 3,449.14 | 3,452.80 |
| 97,101–97,200 | 50 | 3,462.84 | 3,466.50 |
| 97,201–97,300 | 40 | 3,476.54 | 3,480.20 |
| 97,301–97,400 | 30 | 3,490.24 | 3,493.90 |
| 97,401–97,500 | 20 | 3,503.94 | 3,507.60 |
| 97,501–97,600 | 10 | 3,517.64 | 3,521.30 |

For **every** row additionally test end+.99, next integer, and adjacent cents under the approved classification/rounding policy. The 2026 estimated sheet's lookup amounts (first band3,136; last3,519) are separate official-table fixtures; they do not overwrite this statutory arithmetic. Funding tests must include Section 13's ordinary convergent oracle, exact two-cycle design, both funding sources, retirement-pool exhaustion and executed conversion shortfalls.

### Ordinary credits and low-income policy still requiring a decision

AR's official booklet has a separate optional Low Income Tax Table, and §26-51-501 includes an additional low-income credit. Do not conflate either with the zero marginal band. The 2025 booklet shows a $60 credit stepping down by $5 per $100 near $26,500–$27,600, but these indexed endpoints are **not verified final 2026 constants**. They must be updated from dated official material or explicitly omitted with possible-overstatement disclosure and approval. All high-income/funding examples above avoid that ambiguity; small retirement cases do not.

Recommend include ordinary personal/age credits. Also test a small eligible retirement amount for a senior: claiming its exclusion can forfeit the 65 Special credit. Compare the two legal choices in a narrow per-person worksheet (at most four two-spouse combinations), or disclose an approved nonoptimized retirement-claim policy. Do not blindly grant both. This bounded credit choice does not authorize a general credit/filing optimizer.

## 18. Bounded integration acceptance design

No implementation integration suite ran during this audit. Future minimum **separate** suites:

1. Direct legal/source fixtures first, with raw federal-vs-eligible AL liability distinguished and separate AR statutory/table expectations. Include unsupported results and material-only age ambiguities.
2. Account-backed Single/MFJ projections: own IRA,401(k)/403(b), opening and current RMDs, actual IRA conversions, request above capacity, absent same-owner Roth destination, multiple owners, source exhaustion and unchanged live balances. Preserve RMD-before-conversion.
3. Cash-, principal-brokerage- and traditional-funded tax oracles. AL federal deduction, SS interactions, senior federal phaseout/expiration and deduction steps. AR all adjustment boundaries and the no-equality example before admission. Reconcile annual cash conservation, actual withdrawals and retained surplus; no invented gains/owners.
4. Different ages, both death directions, death-year MFJ and later Single. Direct survivor pension classification/recipient, no deceased-cap transfer or aging. Exact second-death horizon in bounded longevity scenarios.
5. Two controlled **+4%/−2%** economic paths for principal-only and mixed IRA/conversion plans versus matching deterministic runs. Check annual federal/state tax, conversions, funding, ending assets and lifetime sums, not just success flags.
6. One small integrated Social Security comparison and two bounded longevity scenarios per state; no exhaustive strategy search or mortality ranking benchmark.
7. Single/MFJ JSON save/reload, baseline/scenario copies, state defaults, optional AL classification defaults/freeze if approved, stored history and source metadata.
8. Normal/retained Single/MFJ PDF/CSV, result-time state identity and every limitation, active editor switched to another state, old notes retained after rule update. Both sides of MC comparisons must remain frozen.
9. Independent new-state rules-key freshness, stale prior versions, unrelated-state and SS-only invariance; unsupported-source UI propagation without accepting an incomplete result.
10. Existing **19-state** financial goldens, source/B3 admission, tax funding and previously accepted frozen reports. Expand only registry lists after admission; never adjust old financial expected values.

Use Java25, IntelliJ bundled Maven and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`. Focused tests first; then the established bounded suite excluding Benchmark/CapacityProbe/Performance/Exhaustive/Search classes. No new-state admission while a common case or required check fails. Native interactive acceptance remains separate, not inferred from skipped preview tests.

## 19. Reporting, persistence and freshness integration

Future edits, only after independent admission gates, follow existing OK/MS patterns:

| Area | Minimal necessary integration |
|---|---|
| Enum/router/selection | Add each jurisdiction independently to `StateTaxJurisdiction` and `ProductionStateTaxService`; existing alphabetical choice method sorts it. No CO entry. |
| Conditional/annuity admission | Extend `domain/benefit/ConditionalBenefitAdmission` and `NonqualifiedAnnuityAdmission` independently only for legally verified receipt subsets. Preserve basis/NIIT/Advanced/growth guards. AL DB/senior and AR qualified-plan relief are not blanket conditional-income exemptions. |
| Results/help | `ui/views/AssumptionsView`, `ResultsSummaryView`, `ui/summary/ProjectionYearDetailsPane`; show all annual/range limitations without paragraph truncation. No tax math in JavaFX. |
| Analyzer and report contexts | `ui/socialsecurity/SocialSecurityAnalyzerInputSummary`, `IndividualReportContext`; capture state, version, policy and all limitations at result time. |
| MC snapshots | `ui/montecarlo/MonteCarloRunService`, `MonteCarloStrategyComparisonRunService`; retain both frozen plans, not active-editor state. |
| PDF/CSV | `app/export/ProjectionPdfExporter`, `ProjectionCsvExporter`; correct state identity, tax columns and original frozen notes. Historical export does not recalculate or inject current assumptions. |
| JSON/baseline/history | Reuse existing plan, `ProjectionYear` and accepted snapshots. Optional AL pension fact requires backward-compatible defaults and immutable receipt capture if chosen; no general persistence rewrite. |
| Provenance | `app/socialsecurity/results/AnalysisVersions`, `AcceptedAnalysisCompletion`, `AnalysisCompatibilityPolicy`; independent new keys from Section 16, including either frozen comparison side. |

Changing an AL/AR rules version must mark only affected full-plan analyses outdated; it must not replace stored tax/notes/references. Preserve SS-only and unrelated-state freshness. Baseline snapshots are not universally deep immutable copies; verify scenario/baseline behavior rather than assuming isolation. Historical result immutability relies on accepted frozen result data, not the live plan.

Minimum disclosures to freeze and display:

- Full-year ordinary Single/MFJ, standard-deduction and no-dependent/itemized/special-credit scope; planning cents versus return tables.
- AL federal-liability method, missing prior federal adjustments/credits/preferences and the chosen age-deduction treatment, including any difference from the federal cash row.
- AL pension qualification assumption/fact and historical state basis/DB-rollover limitations; separate own-recipient $6,000 pool, no double exemption.
- AR age/source eligibility, conversion interpretation/refusals, actual-joint versus separate-method overstatement and chosen credit/low-income bounds.
- AR two schedules, discrete adjustments and approved fractional/return-table convention; future indexing hold is an assumption, not enacted nominal constancy.
- Brokerage unmodeled gains can understate taxes; missing AR long-term/state special relief can overstate; no invented gains, owners or source qualifications.
- Ordinary survivor/death conventions and every material unsupported-source reason. Do not present unavailable tax as zero.

## 20. Separate admission gates and remaining blockers

| State | Gate / open decision | Admission consequence |
|---|---|---|
| AL | Federal deduction current-year timing established; amount coverage must include approved senior correction or explicitly accepted quantified model error | No unconditional raw-total admission claim. New helper may remain state-local; no federal-engine change. |
| AL | Generic pension DB/DC qualification missing | Approve narrowly captured fact, explicit assumption, or pension-only unsupported subset. No silent full exemption. |
| AL | Age/birthday/death and admitted annuity/basis boundaries | Verify ordinary source rules, refuse material ambiguity and pass independent fixtures. |
| AL | Funding, B3, actual conversion capacity, reporting/history/freshness,19-state regression | Required independently before registration. AR cannot veto successful AL admission. |
| AR | Current two schedules/adjustments verified; exact-cent vs official-table fractional behavior remains a policy/reconciliation gate | No flat3.7% proxy, stale3.9% or invented continuous adjustment. |
| AR | Conversion pool application is interpretation without a located conversion-specific exemption determination | Approve identified interpretation or explicit affected-path refusal; do not guess. |
| AR | Own-IRA age-crossing timing and employer/source qualification | Ordinary age-qualified inputs suffice; material missing dates refuse. No broad timing wizard necessary. |
| AR | Actual-joint/credit/low-income/indexing policy | Approve disclosed bounds or implement dated ordinary worksheets; no unpublished amounts as final law. |
| AR | Mathematical funding two-cycle under proposed exact-cent policy | Resolve through official-policy reconciliation and narrowly approved tested funding behavior. Common nonconvergence is a production blocker, not merely a disclosure. |
| AR | B3, survivors, source attribution, reports/persistence/freshness and 19-state regressions | Independent acceptance. AL's pension issue cannot veto AR if its own gates pass. |

No missing ordinary tax-payment history or new general architecture is needed. Actual material missing facts are AL pension legal type/historical basis and annual receipt timing for an affected AR IRA boundary; investment holding period matters only if exact capital-gain relief is promised. No rule should be broadened to hide these limits.

## 21. Explicit approval decisions and proposed Phase 6E-N plan

Approval of this audit alone should identify choices below before production implementation:

1. Authorize one combined workstream with independent AL/AR admission, ordinary full-year domestic Single/MFJ scope, exact decimal intermediates and final cents, all 19 existing-state values preserved and CO disabled.
2. **AL federal deduction:** approve the recommended state-local ordinary age/enhanced-senior liability correction, using existing federal tax arithmetic and leaving actual federal results unchanged. Alternatively explicitly accept the quantified raw-modeled-liability error. Approve no prior-history/credits/foreign/preference engine and its disclosures/refusals.
3. **AL pensions:** approve one optional legal DB/DC/UNKNOWN classification fact with backward-compatible/frozen propagation, or choose a clearly limited no-new-input pension refusal/assumption policy. No inference from names, monthly payments or cash-balance presentation.
4. Approve AL's $6,000 own-recipient senior pool for evidenced ordinary IRA/DC/conversion receipts, full verified DB relief separately, and the carefully bounded admitted annuity/modern-state-basis assumptions. Require material birthday/source guard verification.
5. **AR conversions:** approve the documented own-IRA §307 pool interpretation after59½, early own-IRA inclusion and material timing refusal, or require a DFA conversion-specific answer first. Employer/in-plan variants remain outside scope pending separate evidence.
6. Approve actual-joint AR calculations with separate recipient pools and possible-overstatement disclosure versus separately-on-same-return. No arbitrary investment split or filing-status optimizer.
7. Approve the AR planning-cent statutory extension P only after final official-table reconciliation, or specify a faithfully verified table policy. Keep all discrete adjustments and independent counterexamples; do not smooth away the legal mechanism.
8. Approve **only if reproduced**, an AR-local minimal-sufficient-funding/discontinuity correction with exact tax/source/cash reconciliation and retained surplus, preserving every existing state. This is an explicit financial-semantics decision, not routine plumbing. If unsafe or broader than this, leave AR disabled and report the required decision.
9. Approve ordinary AR personal/age credits, whether to compare retirement relief against 65 Special, and whether optional low-income tables/additional credits are implemented from final dated values or deliberately omitted with possible-overstatement disclosure. Do not promote the old indexed $60 worksheet endpoints without verification.
10. Approve latest-published-then-nominal-hold AR future dollar parameters, with statutory indexing explained, and ordinary unclassified investment treatment without guessed LTCG relief. AL fixed ordinary law continues nominally; federal helper follows its explicit yearly coverage, not an extended temporary senior deduction.
11. Approve source-local reconciliation and conditional/annuity bounds, full frozen disclosures and independent rules keys. Extend capacity-complete hypothetical trials only after focused evidence for the new state(s); no other financial semantics change.

**Proposed sequence:** legal/table/coverage decisions -> independently derived rules/worksheet tests -> AL/AR local calculator/source validation -> over-capacity reproduction and focused funding -> per-state deterministic/controlled MC/SS/longevity acceptance -> persistence/report/history/freshness -> bounded 19-state regressions -> register only passing jurisdiction(s) -> native interactive checklist/report. Do not wait for one state's blocker to investigate or finish safe work on the other. No Phase 6E-N code starts in this audit.

## 22. Authoritative sources, dates and audit verification

All sources below were checked **2026-11-17**. Governing years/effective dates are distinguished from webpage/upload dates. Some legislative URLs returned intermittent cache/access errors; indexed official text and separately accessible enrolled/agency documents were used, not a claim of unrestricted endpoint availability. JS-only statutory pages were corroborated by enrolled official text. No unofficial tax calculator supplies a golden expectation.

### Alabama / federal

- [Code §40-18-5](https://alison.legislature.state.al.us/code-of-alabama?section=40-18-5); [official Rule 810-3-5-.01](https://admincode.legislature.state.al.us/api/rule/810-3-5-.01): ordinary marginal rates.
- [§40-18-15](https://alison.legislature.state.al.us/code-of-alabama?section=40-18-15); [enrolled 2026 HB527](https://alison.legislature.state.al.us/files/pdf/SearchableInstruments/2026RS/HB527-enr.pdf), ordinary deduction text and2026–2028overtime provision; [ALDOR current issues](https://www.revenue.alabama.gov/individual-corporate/current-issues/).
- [§40-18-19](https://alison.legislature.state.al.us/code-of-alabama?section=40-18-19); [enrolled 2026 HB341](https://alison.legislature.state.al.us/files/pdf/SearchableInstruments/2026RS/HB341-enr.pdf), effective October 1, 2026, retaining ordinary DB/$6,000 provisions and adding dated specialized National Guard relief.
- [Rule 810-3-15-.20, individuals](https://admincode.legislature.state.al.us/api/rule/810-3-15-.20): accrual selection, eligible tax and later adjustments. [Rule 810-3-15-.19](https://admincode.legislature.state.al.us/api/rule/810-3-15-.19) is repealed, not current deduction authority.
- [Rule 810-3-19-.04](https://admincode.legislature.state.al.us/api/rule/810-3-19-.04): DB/beneficiary exemption and rollover basis. [ALDOR exempt-income guidance](https://www.revenue.alabama.gov/individual-corporate/income-exempt-from-alabama-income-taxation/).
- [2025 Form40 booklet](https://www.revenue.alabama.gov/wp-content/uploads/2026/01/25f40bk.pdf): continuing individual federal-deduction worksheet p31, IRA conversion/basis and standard-deduction mechanics. Not represented as final 2026instructions.
- [2025 Schedule RS](https://www.revenue.alabama.gov/wp-content/uploads/2026/01/25schrsblk.pdf); [standalone RS instructions](https://www.revenue.alabama.gov/wp-content/uploads/2025/01/24schrsinstr.pdf): separate recipient pool mechanics, corroborated by newer retained law/form.
- [2026 withholding booklet](https://www.revenue.alabama.gov/wp-content/uploads/2026/01/whbooklet_0126.pdf): dated standard-deduction/exemption corroboration, not annual federal-deduction/payment authority.
- [IRS plan definitions](https://www.irs.gov/retirement-plans/plan-participant-employee/retirement-plans-definitions); [DOL cash-balance fact sheet](https://www.dol.gov/agencies/ebsa/about-ebsa/our-activities/resource-center/fact-sheets/cash-balance-pension-plans): cash-balance legal DB distinction.
- [IRS 2026 amounts RP 2025-32](https://www.irs.gov/pub/irs-drop/rp-25-32.pdf); [Schedule 1-A](https://www.irs.gov/pub/irs-pdf/f1040s1a.pdf); [IRS 2027 published adjustments](https://www.irs.gov/newsroom/irs-releases-tax-inflation-adjustments-for-tax-year-2027-taxpayers-continue-to-benefit-from-the-working-families-tax-cuts): separate known legal amounts from projected future federal parameters. No federal code/result is changed.

### Arkansas

- [2026 special-session Act 1](https://www.arkleg.state.ar.us/Home/FTPDocument?path=/ACTS/2026S1/Public/ACT1.pdf), [Act 2](https://www.arkleg.state.ar.us/Home/FTPDocument?path=/ACTS/2026S1/Public/ACT2.pdf): controlling2026individual bands/adjustments; later corporate dates do not change individual law.
- [DFA 2026 AR1000ES](https://www.dfa.arkansas.gov/wp-content/uploads/2026_Final_AR1000ES_2.pdf), revision 5/6/2026: dated deductions/credits and estimated tax tables, explicitly separated from statutory cent arithmetic.
- [Official2026legislative tax handbook](https://webftp.blr.arkansas.gov/Home/FTPDocument?path=Assembly%2F2027%2FSummary+Budget+Manuals%2F2026+Tax+Handbook.pdf): legislative context; retained historical labels/figures must not override enrolled current law.
- [26CAR§100-118](https://codeofarrules.arkansas.gov/Rules/Rule?chapterID=33&levelType=section&partID=941&sectionID=40225&subChapterID=261&subPartID=6195&titleID=26); [official comprehensive rule PDF](https://webftp.blr.arkansas.gov/Home/FTPDocument?path=CAR%2FParts%2F26CARpt100.pdf): employer/source rules, older IRA sentence expressly reconciled with later statutes.
- [Act 817 of 1999](https://www.arkleg.state.ar.us/Home/FTPDocument?path=%2FACTS%2F1999%2FPublic%2FACT817.pdf), [Act 773 of 2001](https://arkleg.state.ar.us/Home/FTPDocument?path=%2FACTS%2F2001%2FPublic%2FACT773.pdf): IRA statutory amendment/retained coverage; current §26-51-307 and current agency instructions corroborate.
- [DFA 2025 AR1000F/NR instructions and schedules](https://www.dfa.arkansas.gov/wp-content/uploads/2025_AR1000F_and_AR1000NR_Instructions.pdf), pp. 10–14 and 21: ordinary retirement, survivor, age credits, filing/optional low-income mechanics and gain schedule. Old indexed amounts/rates are not automatically 2026 law. Standalone 2025 AR1000D endpoint returned 403; gain schedule in the retrieved official package and §26-51-815 provide corroboration.
- [DFA Subject300, IRA](https://www.dfa.arkansas.gov/wp-content/uploads/300-IRAs.pdf), revised 2/27/2023: explicit conversion inclusion; stale contribution limits not used and no conversion-specific exemption ruling claimed.
- [Act 1 of the 2021 Second Extraordinary Session](https://www.arkleg.state.ar.us/Home/FTPDocument?path=/ACTS/2021S2/Public/ACT1.pdf), §9 and related amendments: standard-deduction CPI/3%/$10 mechanism and ordinary credit context; read with current 2026law.
- [DFA individual forms](https://www.dfa.arkansas.gov/office/taxes/income-tax-administration/individual-income-tax/forms/): final 2026annual tables/credits and subsequent indexed amounts remain implementation recheck items.

### Repository verification / stop

Initial `git status --short`: **clean**. Final status contains exactly **`?? PHASE-6E-M-ALABAMA-ARKANSAS-TAX-AUDIT.md`**. `git diff --check` passes (exit 0); tracked and staged diffs are empty. A separate strict UTF-8/trailing-whitespace check covers the new untracked report.

Only intended new repository artifact: **this Phase 6E-M audit report**. No production/test/schema/UI/rule/registry changes, no calculator implementation, no CO activation, no staging/commit/push/tag. Independent arithmetic was evaluated with decimal calculations only; no Maven, Monte Carlo, exhaustive search or integration suite was run for this documentation audit. **Stop here for review; no Phase 6E-N implementation is authorized by this report.**
