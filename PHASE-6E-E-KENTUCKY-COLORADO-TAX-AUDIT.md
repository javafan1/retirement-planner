# Aledrix Retirement Planning — Phase 6E-E
# Combined Kentucky and Colorado State Income Tax Audit

Verification date: **November 16, 2026**. **Audit and documentation only. Neither state is implemented, registered or admitted by this report.** Phase 6E-F requires explicit approval.

Status labels used below: **Law** = verified enacted provision or official administrative guidance; **Code** = inspected current source; **Proposal** = future implementation recommendation; **Approximation** = proposed planning convention requiring approval; **Open** = unresolved admission requirement. Existing Arizona B3/NC policies are accepted precedents, not automatic approval of new KY/CO approximations.

## 1. Executive summary

Both states fit the existing annual state-tax interface. Neither needs a general tax engine, tax-lot accounting, a new pension classification screen, or financial branches in JavaFX/ProjectionEngine. Kentucky is a **moderate** implementation; Colorado is **moderate to high** because of its federal taxable-income base, individual age/SS interactions, and a material November 2026 future-rate verification issue. Their releases must remain independent.

The central attribution finding is favorable: normal projections already identify the owner of traditional withdrawals, opening/current RMDs and executed conversions, the current recipient of pension/survivor payments, and each recipient's selected gross Social Security. Colorado publishes a proportional allocation of joint taxable SS; an equal split or independent federal SS calculation for each spouse would be wrong. Full attribution is not guaranteed for legacy/direct requests, generic payments, or over-capacity bracket-fill trials. These require narrow admission/plumbing work, not a new attribution framework.

Key findings and decisions:

* **KY:** 2026 rate **3.5%**; standard deduction **$3,360**, already announced **$3,470 for 2027**. General retirement exclusion **$31,110 per recipient**, not a transferable household allowance. Official Schedule M expressly includes traditional IRA-to-Roth conversion income. No general age minimum applies to that exclusion.
* **KY filing:** an actual joint state return uses one deduction. Married filing separately on a combined return computes two separate columns. Recommend mapping modeled MFJ to an actual KY joint return, explicitly foregoing election optimization; never silently double the deduction. Include the readily modeled $40 age-65 credit, or obtain explicit approval to omit it. Other credit omissions need disclosure.
* **CO:** verified 2026 estimated-tax rate **4.4%**. Pension limits generally **$20,000 at 55–64 / $24,000 at 65+**, separately for each spouse. SS uses the recipient's same limit first, with full-SS exceptions; it is not an additional unused pension allowance.
* **CO base:** current `MODELED_STANDARD_DEDUCTION_ONLY_V1` is **not a complete legal federal taxable-income base**. It omits federal age additions and the temporary enhanced senior deduction, among other items. Choose a narrowly scoped CO-only base bridge or explicitly approve the quantified modeled-base approximation before admission. Do not alter federal taxes in this phase's proposed state work.
* **CO future law:** official Amendment 87 text proposes graduated rates from 2027. The official election-results application was inaccessible to this audit, and certification/proclamation was not verified. **Do not promote that ballot text as enacted law or carry 4.4% indefinitely without resolving its legal status.** This is a CO admission blocker, independent of KY.
* **CO conversions:** DOR's eligible-IRA/nonpremature-distribution rules, read with IRS conversion guidance, support qualifying fully executed traditional IRA conversions. This is a reasoned application of official rules, not an express CO conversion example. Current rule text and its Roth-contribution exclusion must be reconciled before production constants/eligibility are approved; see Section 7.

No new UI inputs are necessary for the recommended ordinary, fully taxable retirement subset. Specialized government/railroad treatment, inherited-account histories, exceptions requiring unrecorded facts, and represented contradictory sources remain outside that subset.

## 2. Existing 14-state production baseline and architecture

### 2.1 Source-confirmed baseline

`domain/model/StateTaxJurisdiction.java` contains exactly **14** choices:

| Dedicated production calculator/adapter | No-general-individual-income-tax model |
|---|---|
| Michigan, Illinois, Pennsylvania, Iowa, Arizona, North Carolina | Alaska, Florida, Nevada, New Hampshire, South Dakota, Tennessee, Texas, Wyoming |

`domain/tax/state/ProductionStateTaxService.java` registers MI/IL/PA/IA/AZ/NC explicitly and registers the other eight through `usesNoGeneralIndividualIncomeTaxModel()`. KY and CO are absent. The research dataset is not an activation registry; a Colorado research entry is not production support. This count comes from code, not historical phase counts.

Paths below are relative to `src/main/java/com/daviddunn/retirementplanner/` unless stated otherwise.

| Inspected component | Existing behavior / reuse |
|---|---|
| `StateTaxAssumptions`, `StateTaxJurisdiction` | One full-year jurisdiction for the entire plan; stable textual codes; shared alphabetical choices. Only simplified estimates are enabled; overrides are rejected. No residency timeline. |
| `StateIncomeTaxCalculator`, `StateTaxCalculationRequest` | Sufficient immutable annual boundary: actual year, annual filing status, household facts, modeled income/federal result, projected rules and assumptions. |
| `TaxHouseholdContext` | Birth dates, household/active owners, annual distribution date, optional selected pension result. Active owners reflect January 1 modeled death; lifetime runs can supply the effective alive set. No government service, disability/penalty exception or legal annuity-maturity determination. |
| `ProductionStateTaxResult`, unavailable reasons/exception | Calculated zero is distinct from unsupported with absent numeric tax. Funding requires a calculated result. Reuse structured failures. |
| `TaxIncome`, `TaxIncomeCalculator` | Pension + traditional distributions + conversions + modeled investment bucket + admitted conditional taxable amounts form ordinary income. Household taxable SS comes from federal calculation. Selected gross SS and authoritative pension amounts are reused. |
| `TaxIncomeAttribution`, `IncomeSourceAttribution` | Replace aggregate metadata with account/recipient evidence; retain any unattributed residual. Source metadata is not another income channel. Account owner/type, conversion destination, pension original owner/current recipient, year and conditional receipt/basis facts are available. |
| `TaxFundingCalculator` | Rebuilds income and federal/state results for each trial withdrawal and final solution, including supplemental conversion/opening-RMD/pension/conditional sources. $0.01 convergence tolerance; 100-iteration bound. Existing household cash reduces additional funding. |
| `RothConversionBracketFillCalculator` | State-aware overload passes selected jurisdiction into iterative funding. Compatibility overloads default to MI. Trial conversion attribution uses executable accounts, but the scalar trial amount can exceed that execution; see Section 7. |
| `ProjectionEngine` | RMDs precede conversion. Final conversion is capacity-limited and actual execution feeds final tax funding once. Generic annual state results feed annual/lifetime metrics. Uses projected accounts, not real balances. Configured filing status applies through death year; later survivor years are Single. |
| `FederalTaxCalculation`, `FederalTaxBaseBreakdown` | Modeled AGI, taxable SS, projected basic deduction, signed AGI-minus-deduction and clamped taxable income. Coverage explicitly records omitted deductions as unknown, not legally zero. |
| `ArizonaProductionTaxCalculator` | Accepted B3 reconciliation of proceeds/known investment evidence; unsupported guards; frozen disclosures. Do not transplant AZ age exemptions or enhanced senior rules. |
| `NorthCarolinaProductionTaxCalculator`, `NorthCarolinaTaxRules` | Closest small calculator/rules/version/presentation template for KY's AGI structure. NC accepts unknown-basis brokerage and retains five common disclosure categories plus annual arithmetic. NC's deductions and rates are not KY/CO law. |
| `IllinoisProductionTaxCalculator` | Useful account/owner qualification and source-total reconciliation. Unlimited eligible subtraction and conservative pension treatment are Illinois-specific. |
| `IowaProductionTaxCalculator`, `iowa/IowaTaxBaseBridge` | Closest CO base-coverage/admission example; signed base reconciliation and explicitly incomplete modeled federal deductions. Iowa age/qualification, relief and stricter brokerage rules must not be copied. |
| `PennsylvaniaProductionTaxCalculator` | Useful fractional-age/source/qualification refusal patterns. Its state-defined base and complete-conversion exclusion differ from KY/CO. |
| `MichiganProductionTaxAdapter` | Existing context adapter to Michigan calculator; preserve its separate financial rules and golden values. |

**Important Code limitation:** ProjectionEngine currently supplies `BigDecimal.ZERO` for explicit taxable investment income in both bracket-fill and final funding. Ordinary portfolio growth and withdrawals do not manufacture dividends or realized gains. The state boundary can honor caller-established modeled investment income; registration does not create a general investment-income engine.

Reviewed accepted reports: [Arizona B3](PHASE-6E-B3-ARIZONA-BROKERAGE-ADMISSION.md), [NC audit](PHASE-6E-C-NORTH-CAROLINA-TAX-AUDIT.md), and [NC implementation](PHASE-6E-D-NORTH-CAROLINA-IMPLEMENTATION.md). Also inspected the actual `ArizonaBrokerageProjectionRegressionTest` and `ArizonaBrokerageMonteCarloRegressionTest`: they require real brokerage sources, cash conservation, unchanged live balances and two controlled economic paths, rather than synthetic proceeds alone. Earlier Arizona blanket basis refusal is superseded.

## 3. Verified Kentucky rules

### 3.1 Rate, deductions, conformity and filing

| Topic | Law / official evidence | Implementation implication |
|---|---|---|
| 2026 rate | [KRS 141.020(2)(f)–(g)](https://apps.legislature.ky.gov/law/statutes/statute.aspx?id=56339), [enacted HB 1 record](https://apps.legislature.ky.gov/record/25RS/hb1.html): 3.5% from 2026. | Use year-aware reviewed rules; no enacted unconditional lower future rate was established. |
| Conditional reductions | KRS 141.020 contains revenue/reserve tests and legislative action requirements; DOR must not implement a reduction without General Assembly action. 2025 HB 775 changed the process. | Do not forecast a glide path to zero. Hold 3.5% unless a reviewed enactment establishes a lower rate; recheck before 6E-F. |
| Standard deduction | DOR announcements: [2026 $3,360](https://revenue.ky.gov/News/Pages/Kentucky-DOR-Announces-2026-Standard-Deduction.aspx), [2027 $3,470](https://revenue.ky.gov/News/Pages/Kentucky-DOR-Announces-2027-Standard-Deduction.aspx). | These are verified annual amounts, not federal deductions. Freezing $3,360 in 2027 would already be wrong. |
| Later deductions | [KRS 141.081](https://apps.legislature.ky.gov/law/statutes/statute.aspx?id=29067) prescribes annual CPI-U adjustment with a no-decrease factor. | Exact later CPI amounts are not known. Proposal: use published years, then disclose nominal $3,470 carry-forward from 2028 until a reviewed update. This is an approximation, not statutory freezing or the plan's generic inflation rate. |
| Starting base | [KRS 141.019](https://apps.legislature.ky.gov/law/statutes/statute.aspx?id=57914) adjusts federal-style AGI; remove federally included SS and eligible retirement amounts. [KRS 141.010(21)](https://apps.legislature.ky.gov/law/statutes/statute.aspx?id=57913) adopts the December 31, 2025 IRC for years beginning from 2026. | Use modeled AGI for admitted ordinary sources. The generic DOR individual page still says 4%/December 2024: it is not the controlling 2026 source. |
| Federal deductions | KRS 141.019(2) has its own exclusions, including §151 deductions and tips/overtime/car-loan deductions under the 2026 amendments. | Do not import federal basic/age/enhanced-senior deductions into an AGI-minus-KY-standard calculation. Federal enhanced senior deduction is under §151; no KY subtraction is recommended for it. |
| Joint versus combined | [103 KAR 17:020 §§2–3](https://apps.legislature.ky.gov/law/kar/titles/103/017/020/) and [Form 740 page 1](https://revenue.ky.gov/Forms/740%20%282025%29.pdf): actual joint income/deductions are aggregate; separate-on-combined has two independently calculated columns. | One $3,360 deduction for actual 2026 joint return; two separate deductions only with properly allocated separate-column income/floors. Federal MFJ alone establishes neither election nor two deductions. |
| Separate allocation | [KRS 141.016](https://apps.legislature.ky.gov/law/statutes/statute.aspx?id=29032) assigns income to earner/property owner where separate treatment is elected. | Unallocated modeled investment income cannot be split equally to optimize separate filing. |
| Floors/rounding | Form 740 shows whole-dollar fields and nonnegative tax after credits. | Proposal: nonnegative taxable income/tax; exact decimal intermediates and final HALF_UP cents, disclosed as a planning convention rather than a return-preparation rounding engine. |

**Recommended filing policy:** actual KY joint return for modeled MFJ; actual Single after the death-year transition. Compute retirement exclusions independently even on the joint return. This fits current inputs and avoids a separate-return optimizer. Disclose that separate-on-combined may reduce actual taxes. If election optimization is required instead, common investment/property ownership and separate deductions need further bounded work; it is not authorized merely by approving this audit.

### 3.2 Common credits and adjustments that cannot be silently ignored

[KRS 141.020(3)](https://apps.legislature.ky.gov/law/statutes/statute.aspx?id=56339) and [DOR personal-credit guidance](https://revenue.ky.gov/Individual/Individual-Income-Tax/Pages/default.aspx) establish a **$40 age-65 personal credit per qualifying individual**. Proposal: include this small nonrefundable credit using existing birth/alive facts; obtain a specific omission approval if excluded. Do not create blindness/National Guard inputs.

The [2026 estimated instructions](https://revenue.ky.gov/Forms/740-ES%20Instructions%20%282025%29.pdf) also discuss family-size relief. That document's filename says 2025 but contents identify 2026; its family-credit threshold appears carried from the prior year. Do not promote that threshold as a newly verified final 2026 value. Family-size relief depends on modified gross income and family/dependent facts, not simply post-exclusion taxable income. Recommend explicitly disclosing its omission initially, including the possibility of overstated low-income taxes; an approved limited adult-only credit would need its own current worksheet verification. This is common enough to call out, not hide under specialized business credits.

Out-of-state municipal interest additions, U.S. obligation subtractions, state/federal basis differences, itemization, business adjustments and historical government provisions are genuine rules. They need source evidence or an explicit scope limitation; an ordinary interest/dividend/gain total alone cannot identify them.

## 4. Verified Colorado rules and future-law verification gate

### 4.1 Starting base and 2026 rate

The [DOR Individual Income Tax Guide, Parts 2–3](https://tax.colorado.gov/individual-income-tax-guide) starts with **federal taxable income**, modified by Colorado additions/subtractions. The [2026 DR 0104EP worksheet, PDF page 3](https://tax.colorado.gov/sites/default/files/documents/DR_0104EP_2026.pdf#page=3) applies **4.4%** to estimated 2026 Colorado taxable income. It is not an AGI-minus-Colorado-standard-deduction state.

Federal deductions already lawfully included in that starting base must remain unless a specific CO addback applies. The [official Legislative Council OBBBA analysis, printed pages 12–13](https://content.leg.colorado.gov/sites/default/files/images/lcs_june_2025_forecast_updated_with_obbba_fiscal_impacts.pdf#page=12) identifies the federal enhanced senior deduction as reducing CO revenue and identifies the overtime addback from 2026. The current DOR guide lists no senior-deduction addback. **Finding:** rolling conformity carries the enhanced senior deduction into the ordinary CO base, subject to eligibility/phaseout; do not add it back merely because NC does not use it. The absence of a later applicable decoupling must be rechecked at implementation.

For independent ordinary 2026 worksheets, [IRS Revenue Procedure 2025-32 §3.14](https://www.irs.gov/pub/irs-drop/rp-25-32.pdf) gives basic deductions $16,100 Single/$32,200 MFJ and additional age amounts $2,050 unmarried/$1,650 married per eligible person. [IRS senior guidance](https://www.irs.gov/newsroom/working-families-tax-cuts-individuals-and-workers) establishes up to $6,000 per eligible senior in 2025–2028, with MAGI phaseout above $75,000/$150,000 and eligibility conditions. Those deductions affect taxable income, not federal AGI or the SS provisional-income calculation.

**Code mismatch:** `FederalTaxBaseBreakdown` guarantees only modeled AGI minus modeled basic deduction. It cannot certify full Form 1040 taxable income. Two permissible future choices need approval:

1. **Preferred for common-retiree accuracy:** a CO-only bridge that reconciles that coverage and computes the omitted ordinary domestic age/enhanced-senior deductions from existing dates, filing status and AGI under explicit nondependent/valid-SSN/no-foreign-adjustment assumptions. Keep the original federal result immutable. Reconcile any high-income CO deduction addback against the complete deduction represented by the bridge. No unrelated federal change.
2. **Smaller disclosed approximation:** follow Iowa's explicitly incomplete modeled-base approach, omitting those deductions. State plainly that legal deductions are unknown and estimated CO/combined lifetime taxes can be too high. The $81.40 versus $435.60 example in Section 11 quantifies the difference. This is not equivalent to legally complete CO taxation and is not yet approved.

Neither approach permits subtracting a second basic standard deduction. Itemization, donations, QBI, foreign income, state adjustments and other unmodeled deductions remain bounded limitations or supported-source refusals.

### 4.2 Material additions and subtractions

Under the DOR guide, for 2026 onward filers with **AGI above $300,000**, the federal standard/itemized deduction is effectively limited for this purpose to **$1,000 Single / $2,000 joint**, by an addback of the excess. The threshold is strict, not a phaseout. A large conversion can reach it. This is material ordinary retirement planning and should be implemented for the supported standard-deduction subset, rather than silently excluded as a business rule. Final 2026 worksheet confirmation remains an acceptance step.

Other source-sensitive adjustments include out-of-state municipal interest, U.S. government interest, state income tax deducted through itemization, QBI/business adjustments, charitable contributions for nonitemizers, and restricted capital-gain relief (not a general brokerage gain exemption). Overtime is added back from 2026; tips have no corresponding addback in the guide. Known incompatible source facts must produce the established unsupported result. Routine taxable investment income remains in the base once.

Floor final Colorado taxable income at zero. Preserve signed base information while reconciling additions; adding a high-income deduction addback to a previously clamped zero could otherwise lose negative base information. Match the legal return's ordering with independent worksheets before admission. Exact intermediates/final cents are a proposed disclosed planning convention; do not claim exact agreement with Colorado's whole-dollar/table filing arithmetic.

### 4.3 Future rates: do not treat ballot text as certification

The legislature's [Amendment 87 page](https://leg.colorado.gov/ballots/graduated-income-tax-2) and [official final measure text, §3](https://leg.colorado.gov/ballots/graduated-income-tax-2/files/text-of-measure) establish what was submitted for the November 3, 2026 election, including this **proposed** schedule from 2027:

| Colorado taxable-income slice | Measure's marginal rate, subject to verified enactment |
|---|---:|
| $0–$25,000 | 3.7% |
| $25,000–$100,000 | 4.2% |
| $100,000–$500,000 | 4.4% |
| $500,000–$750,000 | 7.4% |
| $750,000–$1,000,000 | 7.9% |
| Above $1,000,000 | 8.4% |

The text uses the same dollar bands for individuals/joint returns, contains a separate 4.4% taxable-principal-residence-gain rule, and provides no automatic bracket inflation in this schedule. A legislature page marked “Completed” describes the ballot process; it does not prove enactment. The SOS [Initiative 195 title-board page](https://www.sos.state.co.us/pubs/elections/Initiatives/titleBoard/results/2025-2026/195Results.html) is also **not an election-result certificate**, despite “Results” in its title.

The [official statewide results application](https://results.enr.clarityelections.com/CO/127407/) returned a JavaScript/anti-bot page. This audit did not verify certified statewide passage, proclamation/effective law, or the final interaction with competing Proposition 136. Consequently, the table above is **not a production constant recommendation yet**. Recheck official certification and resulting law in 6E-F. A lifetime CO projection must not silently freeze 4.4% if the new law is established; if legal status remains unresolved, keep CO disabled independently of KY. Conditional TABOR reductions/refunds likewise must not be forecast from revenue estimates; incorporate only established reviewed adjustments.

The pension-cap repeal proposals [SB25-136](https://leg.colorado.gov/bills/sb25-136) and [HB26-1062](https://leg.colorado.gov/bills/hb26-1062) were **postponed indefinitely**, not enacted. Their introduced summaries do not remove the current $20,000/$24,000 limits.

## 5. Separate retirement-income exclusion matrices

### 5.1 Kentucky

Authority: [KRS 141.019(1)(e)–(g)](https://apps.legislature.ky.gov/law/statutes/statute.aspx?id=57914), [Schedule M instructions/worksheet, PDF page 3](https://revenue.ky.gov/Forms/Schedule%20M%20%282025%29.pdf#page=3), and [Schedule P](https://revenue.ky.gov/Forms/Schedule%20P%20%282025%29.pdf). Continuing 2025 instructions were checked against the current statute, including 2026 amendments; they are not represented as final 2026 return instructions.

| Income | KY legal treatment / proposed boundary |
|---|---|
| Federally taxable SS | Subtract fully; outside the retirement cap. Never subtract gross SS from AGI. |
| Ordinary traditional IRA, 401(k)/403(b), RMD | Eligible taxable retirement income shares recipient's $31,110 limit; no general age test. RMD has no extra exclusion. |
| Traditional IRA-to-Roth conversion | Expressly included by DOR. Shares the same recipient's limit with withdrawals/pensions; not a second limit. Employer-to-Roth pathways need source-specific verification before being advertised. |
| Ordinary private pension | Written retirement plan, qualified or unqualified, eligible. Proposal: assume generic Pension represents such an ordinary plan, with prominent limitation; names are not evidence. |
| Admitted nonqualified annuity | Statute includes §1035 annuity contracts. Only federally taxable portion enters the exclusion/base; basis recovery is not income. Preserve current federal admission. |
| Survivor pension/death benefits | Recipient's own eligible retirement pool, not deceased owner's remaining cap. DOR expressly includes death benefits. |
| Qualified Roth withdrawal | Excluded federally; no AGI and no second subtraction or cap consumption. Explicit taxable/nonqualified Roth or mixed basis remains unsupported. |
| Interest/dividends/modeled gains | Ordinary admitted taxable amount remains taxable; not retirement-exclusion income merely because used for retirement. |
| Brokerage principal | Cash flow, not AGI. Unknown basis alone is admitted with disclosure. |
| Special government/RR benefits | Pre-1998 KY/federal/local service and other special provisions can increase exemption. Do not implement historical service/plan accounting. Known unsupported classifications need refusal; no name inference. |

The $31,110 cap is a nominal statutory amount with no indexing provision identified. Each spouse computes independently; unused amounts do not transfer or carry forward. Combined exclusions may be added on a joint return only after individual calculations. Ordinary fully taxable private-plan assumptions are proposed; comprehensive government qualification is not.

### 5.2 Colorado

Authority: DOR [retiree guidance](https://tax.colorado.gov/retirees), [Social Security, Pensions and Annuities publication](https://tax.colorado.gov/sites/default/files/documents/ITT_Social_Security_Pensions_and_Annuities_Jan_2025.pdf), and its [current online worksheet](https://tax.colorado.gov/income-tax-topics-social-security-pensions-and-annuities). The online publication includes the 2025 onward SS changes. Rule 39-22-104(4)(f) is the cited administrative rule; the [official CCR index](https://www.sos.state.co.us/CCR/DisplayRule.do?action=ruleinfo&ruleId=1936&seriesNum=1%20CCR%20201-2) identifies the current compilation as effective March 2, 2026. The compiled PDF itself was not retrieved successfully; conversion interpretation therefore retains the gate in Section 7.

| Recipient / income | CO legal treatment / proposed boundary |
|---|---|
| Age 55–64 qualifying pension/annuity | Individual limit $20,000; no spouse transfer. |
| Age 65+ qualifying pension/annuity | Individual limit $24,000; no spouse transfer. |
| Under 55 ordinary retirement income | No ordinary age-based subtraction. Direct qualifying death benefits can use $20,000; generic death-labelled payments are not proof. |
| Traditional IRA/RMD | Eligible taxable IRA receipts subject to age and federal premature-distribution restriction. RMD is ordinary eligible income, not an additional allowance. |
| Employer 401(k)/403(b) | Qualifying distributions can enter the pool; premature-distribution restriction has different scope for ordinary employer sources versus IRA/self-employed plans. Do not copy IRA restrictions indiscriminately. |
| Taxable Roth conversion | See separate finding/gate in Section 7. Ordinary traditional source and complete same-owner execution are essential. |
| Private pension | Qualifying periodic retirement benefit; proposed ordinary private-employer plan assumption, not qualification by label. |
| Nonqualified annuity | Fully matured privately purchased annuity can qualify, taxable portion only. Current annuity terms do not explicitly certify CO maturity; use only an approved bounded annuitized-product assumption or leave this subset unsupported. |
| SS, age 65+ | Full federally taxable share from 2022 onward, regardless of AGI. |
| SS, age 55–64 | From 2025, full taxable share if FAGI ≤$75,000 Single/≤$95,000 joint; otherwise limited to $20,000. These are household AGI thresholds on joint returns. |
| SS, under 55 | No ordinary age-based SS subtraction; do not confuse qualifying pension death benefits with blanket survivor-SS eligibility. |
| Qualified Roth / brokerage principal | Neither becomes taxable income or an additional subtraction. |
| Ordinary investment income | Remains in federal base once, subject to known CO adjustments. |
| RR/military/PERA history | Special exemptions/limits and prior-taxed contributions need separate treatment. Exclude the specialized qualification engines; disclose possible overstatement. |

For each person, calculate eligible SS subtraction first. Other eligible pension subtraction is at most `max(personal cap − person's SS subtraction, 0)`, also limited by that person's eligible other receipts. When full SS exceeds $24,000/$20,000, the excess is still excluded where the full-SS rule applies, but no other pension allowance remains for that recipient. Never subtract SS again as part of another pool. No indexing of the general $20,000/$24,000 caps was established.

Federal premature IRA distributions are generally before age 59½, with exceptions. Being 55 alone does not make an ordinary early IRA cash withdrawal eligible. Actual receipt timing in a year crossing 59½ is not fully captured by an annual total; flag that boundary for an explicit convention or unsupported result rather than assuming December 31 age applies to every receipt.

## 6. Critical per-person attribution feasibility

Categories: **1 sufficient** for the normal attributed path; **2 bounded assumption** requires explicit approval/disclosure; **3 missing** prevents the claimed precise adjustment. Category 1 ownership is not itself proof of legal qualification.

| Annual fact | Classification and evidence | Required treatment |
|---|---|---|
| Ordinary traditional withdrawal | **1:** account reference/type/owner in WithdrawalBreakdown sources. | Sum taxable amounts by PRIMARY/SPOUSE; never infer half ownership. |
| Projected RMD | **1:** account-based RMD source, year and taxable amount. | Aggregate with same owner's other retirement receipts, once. |
| Opening RMD | **1** when opening calculation supplies account sources; **3** for scalar-only legacy input. | Include tax-year income even when its cash predates projection. Reconcile complete per-owner totals. |
| Executed fixed conversion | **1:** source/destination owner/type and actual amount. | Use actual execution, retain owner capacity and no cross-owner transfer. |
| Bracket-fill hypothetical conversion | **3** if requested trial amount exceeds executable source/destination capacity. | Existing attachment retains an unattributed residual; resolve the trial contract before claiming complete per-person exclusion support. |
| Private pension | Recipient **1**, legal ordinary written/retirement plan **2**. | Current owner field is recipient. `PensionFacts.originalOwner` preserves origin; display name proves nothing. JOINT/unowned pension cannot get two caps. |
| Survivor pension | Current recipient/original owner **1**; specialized legal death-plan qualification **2/3**. | `HouseholdPensionIncomeCalculator` redirects actual survivor payment to survivor; use survivor's pool and age. Under-55 CO exception only with adequate qualifying direct-payment facts. |
| SS own/spousal/survivor selected receipt | Gross recipient **1** in TaxIncomeCalculator sources from HouseholdSocialSecurityResult. | Use selected gross benefit, including actual survivor selection, not the original worker's own benefit. |
| Each person's taxable SS | No precomputed scalar per person, but **1 derivable** for CO under official proportional worksheet. | Allocate household taxable SS by each recipient's gross share; no new input or separate §86 calculation. |
| Admitted commercial annuity | Recipient/taxable/basis/date **1**; fully matured CO contract **2/3**. | `ConditionalBenefitReceipt.annuityBasis().terms()` supplies existing finite-basis facts. Do not infer maturity solely from its label or a payment date. |
| Generic conditional income | Recipient and ordinary taxable amount **1**; retirement/death qualification **3**. | Tax as ordinary nonretirement income under disclosed scope where supported; never grant exclusion just because a trigger is retirement/death. |
| Interest/dividend/gain ownership | Often household bucket/unowned aggregate **3** for separate-column filing; **1** household total for joint-base calculation. | Does not block proposed KY actual-joint or CO joint base. Blocks unsupported election optimization/source-specific relief. |
| After death / age facts | Birth dates and effective active owners **1**. | Income belongs to actual recipient; no deceased unused cap transfer. Annual death is January 1, status changes after death year. Do not age deceased filer through later birthdays. |
| Inherited account qualification/basis exceptions | Some inherited account facts exist, but complete eligibility/history **3**. | Keep specialized cases unsupported; do not generalize ordinary IRA policy to every inherited account. |

### Colorado joint SS allocation

The official worksheet says, for household taxable SS `T` and selected gross receipts `G_A`, `G_B`:

```text
T_A = T × G_A / (G_A + G_B)
T_B = T − T_A
```

Use nonnegative, complete, year-matched sources; reconcile their gross sum to TaxIncome SS and require `T ≤ 0.85 × total gross`. If total gross is zero, taxable SS must be zero. Use high-precision decimal division and a residual for the second recipient to preserve the total; do not round separate percentages to whole numbers or produce duplicate pennies. Apply each recipient's own eligibility to these allocated shares. This is an official allocation method, not a planning approximation.

**Minimal enhancement proposal:** state-local owner aggregation/reconciliation, with no persisted fields. Normal projection inputs need no general redesign. If a financial caller supplies only aggregate retirement amounts, return unsupported instead of allocating arbitrarily. Separately repair or bound KY/CO bracket-fill trial attribution as described next; final actual execution alone does not fix an earlier trial refusal.

## 7. Roth conversion eligibility and funding correctness

**KY verified:** Schedule M expressly names regular IRA-to-Roth conversion income. Include it in AGI once, then combine its eligible taxable portion with that owner's IRA/RMD/pension/annuity receipts before the $31,110 cap. A conversion by one spouse does not consume the other spouse's cap. Employer-source conversions should not receive an automatically inferred exclusion without completing their source-specific legal review.

**CO finding, with a narrower verification status:** DOR includes federally taxable traditional IRA distributions and excludes distributions subject to federal early-distribution additional tax. [IRS Publication 590-A, “Converting From Any Traditional IRA Into a Roth IRA”](https://www.irs.gov/publications/p590a) states that a properly completed conversion is taxable income as applicable and is not subject to that early-distribution tax; any retained portion can be different. Reading those official rules together supports a **fully executed traditional-IRA conversion at age 55+ sharing the person's pension pool**, even when an ordinary cash distribution at 55–59½ would fail the premature-distribution restriction. Below 55 there is no ordinary age subtraction.

This is a **legal inference from official source definitions**, not an express conversion ruling. The [State Auditor's pension evaluation](https://content.leg.colorado.gov/sites/default/files/2021_te20_pension_or_annuity_deduction.pdf) also reports the rule's exclusion of contributions to/distributions from Roth IRAs. A conversion's taxable traditional-source distribution must be distinguished from a new deductible Roth contribution or later Roth distribution. **Before CO admission, retrieve/reconcile current Rule 39-22-104(4)(f) and confirm this application; obtain official clarification if the text leaves a conflict. Do not silently choose fully taxable or fully exempt treatment to make a test pass.** Employer-plan-to-Roth/in-plan variants remain separately gated. Conditional CO conversion goldens below must not be promoted ahead of this check.

**Code:** final ProjectionEngine execution caps a requested conversion at same-owner capacity, executes it, then taxes the actual scalar and corresponding sources. RMD is allocated first and not converted. This final pathway is reusable.

**Open technical issue for both states:** bracket fill first computes a household proposal from target federal taxable income, then creates trial sources through `convertHousehold(...)`. That returns only executable amounts, while the scalar passed to TaxFundingCalculator remains the requested proposal. `TaxIncomeAttribution.attach` retains an unowned positive residual when actual attribution is smaller. AZ/NC can tax the aggregate, but a new calculator requiring owner-complete exclusions cannot accurately allocate that residual. This is a source-inspected risk, not a failing test reproduced in this documentation phase.

Proposal: add a tightly scoped trial-capacity/attribution admission test first. Resolve feasible trial amount and same-owner allocation consistently before the state call, or explicitly prevent infeasible trials in a bounded KY/CO pathway. Do not relax owner reconciliation, invent an owner, or broadly change existing-state financial behavior. If correction requires a shared semantic change beyond preserving the existing 14-state goldens, stop for explicit approval. Fixed-conversion final execution should not be blocked by this trial-only issue.

Every trial/final state calculation must recompute owner pools, SS share/eligibility, deduction floors, year and filing status. Tax-funded traditional withdrawals can exhaust a retirement cap; brokerage-funded tax cash does not create traditional income. CO funding can cross the $75,000/$95,000 SS eligibility or $300,000 deduction-addback threshold, causing a discontinuity. Existing fixed-point/bracket-fill convergence at those boundaries must be demonstrated; never replace it with `conversion × rate` or assume a constant marginal rate. A nonconvergent valid common case is an independent CO admission blocker requiring a narrow proposed fix, not a silent zero estimate.

## 8. Brokerage withdrawal admission policy

Reuse accepted AZ B3/NC behavior independently in each new calculator:

1. Admit ordinary gross brokerage withdrawals with unknown taxable metadata. Missing basis alone is not unsupported.
2. Preserve the modeled federal base. Principal pays cash flow and reduces assets; it is not automatically AGI or taxable income.
3. Use modeled taxable investments already included. Source gain/dividend/proceeds evidence classifies/reconciles that bucket and is never added again.
4. Reject wrong-year/duplicate/excess taxable attribution, inconsistent federal totals, explicitly incompatible investment sources and other contradictory evidence. Unknown basis is different from known conflicting facts.
5. Do not invent gains, a state capital-gain subtraction, or loss carryovers. Disclose that missing realized gains can understate actual tax and omitted special-source adjustments may move tax in either direction.

An ordinary proceeds-only $100,000 withdrawal with no modeled taxable income should yield **calculated $0.00** for each state, with limitations. Existing annuity/Advanced NIIT safeguards may independently reject a plan containing brokerage/growth inputs; state admission must not bypass those guards or advertise broader federal investment modeling.

## 9. Minimal independent implementation architecture

**Proposal only:** one `KentuckyProductionTaxCalculator` with immutable `kentucky/KentuckyTaxRules`, and one `ColoradoProductionTaxCalculator` with immutable `colorado/ColoradoTaxRules`. Separate version keys, source citations, year policies, tests, limitations and registration decisions. Small private helpers/state-local owner summaries are sufficient.

KY's proposed ordinary joint/Single formula is:

```text
E_person = min(eligible taxable retirement receipts for person, 31,110)
base = max(modeled federal AGI − federally taxable SS − sum(E_person) − KY deduction, 0)
tax = max(base × applicable rate − admitted nonrefundable credits, 0)
```

CO needs its own base bridge plus state-local per-person SS/pension calculation:

```text
base = max(approved federal taxable-base representation + CO additions
           − sum(personal SS subtractions) − sum(personal other-retirement subtractions)
           − other admitted subtractions, 0)
tax = applicable reviewed annual schedule(base), subject to admitted credits
```

The bridge must explain its coverage, preserve an original federal result and avoid duplicate deductions/conversions. The high-income standard-deduction addback belongs in the CO calculator/bridge, not JavaFX or ProjectionEngine. A small CO schedule can support flat or verified graduated years without a generalized state-rules framework. NC supplies the versioned component template; Iowa supplies the coverage-contract example; neither supplies these states' substantive law.

Do not add enums/routes/allowlist entries until each state's gates pass. Existing `ConditionalBenefitAdmission` and `NonqualifiedAnnuityAdmission` require separate explicit entries. Generic taxable conditional receipts must not become retirement income merely because the new state is allowlisted. For CO annuities, settle maturity classification first. Preserve NIIT, mode, basis, growth, household and optimization guards.

## 10. Proposed common-scenario support and exclusions

“Supported” here means feasible with current ordinary inputs **after** an approved implementation and the relevant state gates. It is not current production availability. CO legal-base/future-rate decisions apply across its column.

| Scenario | Kentucky proposal | Colorado proposal / missing facts |
|---|---|---|
| Single SS only | Supported; taxable SS removed, normally zero. | Supported by age rules; under-55 taxable benefits not blanket exempt. |
| MFJ, SS and IRA, different ages | Supported; per-recipient caps, joint deduction policy disclosed. | Supported with official SS allocation, separate ages/caps and valid IRA eligibility. |
| IRA + conversion / RMD + conversion | Supported for verified IRA source, shared owner cap. | Conditional on conversion verification; same owner pool and SS priority. |
| One spouse converts, other has pension | Supported; no equal split or cap transfer. | Supported within verified conversion and ordinary pension scope; each spouse's own age. |
| Private pension + SS | Supported with ordinary written-plan assumption. | Supported with ordinary qualifying periodic-pension assumption. |
| Taxable commercial annuity | Existing admitted taxable portion can qualify; no second basis exclusion. | Requires maturity assumption/proof decision; otherwise this subset unsupported. |
| Brokerage only / brokerage plus IRA | Supported with disclosed missing gains. | Supported with disclosed missing gains, subject to independent federal/annuity guards. |
| Explicit modeled ordinary investments | Supported once, no pension exclusion. | Supported once; known state-specific sources need adjustments or refusal. |
| Qualified Roth withdrawals | Supported federal-qualified assumption; no cap use. | Same; a taxable Roth distribution is not automatically qualifying pension income. |
| RMD commencement, including opening RMD | Supported where complete account sources exist. | Same, with age/source qualification. Scalar-only opening attribution insufficient for owner caps. |
| Survivor pension / death-year MFJ → Single | Supported using survivor recipient; own cap, not deceased unused cap. | Ordinary qualifying survivor pension supported; under-55 direct death-benefit exception needs reliable source facts. |
| Age 55 / 59½ / 65 boundary | No ordinary retirement-exclusion age gate; senior credit separate. | Exact year-end pension age plus distribution-date early-IRA test; midyear 59½ cash timing needs explicit boundary policy. |
| Unknown owner / explicit contradictory attribution | Unsupported; no guessed allocation. | Unsupported; no guessed allocation. |
| Known government/RR/military special classification | Unsupported specialized treatment, with clear reason. | Unsupported specialized treatment; no historical PERA or military engine. |
| Generic nonretirement conditional taxable receipt | Ordinary taxable, if current admission permits; no guessed retirement exclusion. | Same. Death trigger alone does not prove eligible pension death benefit. |
| Special basis, nonqualified Roth, foreign/part-year/multistate | Unsupported or expressly excluded under existing bounded admission. | Same, plus unmodeled legal-base adjustments. |
| 2026 and 2027 onward | Verified rate/published deduction; later deduction approximation needs approval. | 2026 verified rate; later legal schedule unresolved until official status check. |

No proposed pension-name classifier, tax-lot UI, dependent-credit screen or historical service input. Do not reject every generic pension: disclose the ordinary-plan assumption and reject reliable represented contradictions instead.

## 11. Independent golden-value test designs

These are **independent worksheet arithmetic**, not results from production calculators. Decimal arithmetic was checked separately in PowerShell. All amounts are dollars; fixtures are full-year domestic nondependent residents, ordinary fully taxable receipts, no unlisted adjustments, no incremental tax funding unless specified. Ages refer to the legally relevant dates and exclude boundary birthdays unless named. No family-size/other credits apply to the numeric ordinary-income fixtures; KY senior credit is separately identified. CO examples distinguish legal-base and modeled-base outcomes. Do not change production financial semantics to fit a fixture with different assumptions.

### 11.1 Kentucky worksheet matrix

Use Schedule M's individual worksheet, Form 740's actual-joint column, current statute/rate and DOR annual deduction announcements.

| Fixture | Independent computation / expected |
|---|---|
| Single, age 60, IRA $50,000, 2026 | `(50,000−31,110−3,360) × .035` = **$543.55**. |
| Same, plus IRA conversion $20,000 | One $31,110 pool: `(70,000−31,110−3,360) × .035` = **$1,243.55**. |
| Single conversion $20,000 only | Retirement exclusion $20,000; **$0.00**. Repeat ordinary IRA at age 50 to show no KY exclusion age gate; no claim about omitted federal early-withdrawal penalties. |
| Single private pension $50,000 / admitted annuity taxable $50,000 | Age 60, same eligible pool: **$543.55**. For annuity gross $60,000/basis recovery $10,000, only $50,000 enters income/exclusion. |
| SS-only gross $30,000 | Federal taxable SS from independent IRS worksheet; subtract exactly that amount. **$0.00** KY. |
| IRA $50,000 + SS $30,000 | IRS §86 worksheet: taxable SS $25,500, AGI $75,500; subtract SS and $31,110, then KY deduction: **$543.55**. |
| MFJ ages 60/62, each IRA $50,000 | Exclusions $31,110 each; actual-joint one deduction: **$1,204.70**. Separate-on-combined, if separately elected/proven, would be **$1,087.10**. Keep these as distinct fixtures/policies. |
| MFJ, one owner IRA $100,000, other $0 | Only $31,110 exclusion: `(100,000−31,110−3,360)×.035` = **$2,293.55**. No doubled cap. |
| Pension $30,000 to A, conversion $40,000 to B | Separate exclusions $30,000/$31,110, joint deduction $3,360: **$193.55**. Move both receipts to A: **$1,243.55**. |
| Single age 65, IRA/RMD $50,000 | Precredit **$543.55**, less admitted age credit $40 = **$503.55**. Omission policy must not label $543.55 legally complete. |
| Exclusion/deduction boundaries | $31,109.99/$31,110/$31,110.01 eligible pool; combined retirement threshold $34,470 gives zero. $34,470.15 gives raw tax $.00525 → **$0.01** under proposed cents convention. |
| $100,000 brokerage proceeds, no modeled taxable income | **Calculated $0.00**, missing-gain disclosure; real brokerage source must be present. |
| IRA $50,000 + modeled investments $5,000 + proceeds $100,000 | `(55,000−31,110−3,360)×.035` = **$718.55**. Same tax with reconciled gain or known taxable-proceeds evidence; never add $5,000 twice. |
| Single survivor pension $50,000 | Recipient's own $31,110/deduction, **$543.55 at age 60**; death-year MFJ base/status tested separately with actual recipient and no unused-cap transfer. |
| 2027 IRA $50,000, age 60 | Deduction $3,470: **$539.70**. 2028 fixture uses this only under approved nominal approximation. Same fixed-income 2026+2027 state sum **$1,083.25**. |
| Tax-funded traditional withdrawals | Isolated state-only oracle: `T=.035×(50,000+T−31,110−3,360)` ⇒ **$563.2642487…** before final-cent convention; cash-funded state tax is $543.55. This oracle is not a complete federal/state projection fixture. |

### 11.2 Colorado worksheet matrix

Use DR 0104EP's 2026 rate, DR 0104AD/DOR personal SS/pension worksheets, independent IRS deduction/SS worksheets, and the approved coverage policy. No tax credits, AMT or specialized adjustments in these fixtures.

| Fixture | Independent computation / expected |
|---|---|
| Single age 60, IRA $50,000 | Federal taxable base $33,900; pension subtraction $20,000: **$611.60**. Legal/model base coincide for this fixture. |
| Same plus IRA conversion $20,000 | **Conditional conversion fixture**: eligible pool remains $20,000; base $53,900−20,000: **$1,491.60**, pending Section 7 verification. |
| Single age 65, pension/IRA $50,000 | Legal basic+age deduction $18,150 and enhanced senior $6,000: FTI $25,850; less $24,000 ⇒ **$81.40**. Existing modeled basic-only base instead yields **$435.60**. Difference **$354.20**; never confuse these two coverage contracts. |
| MFJ A60/B66, IRA $50,000 each | Legal FTI `100,000−32,200−1,650−6,000=60,150`; subtract $20,000+$24,000 ⇒ **$710.60**. Basic-only modeled alternative: **$1,047.20**. |
| Same ages/total, all IRA income belongs to A60 | Legal FTI $60,150; only A's $20,000 cap ⇒ **$1,766.60**. B's unused $24,000 does not transfer. |
| SS allocation/priority | MFJ A66/B62 gross SS $30,000/$10,000; investments $80,000; IRA $10,000 each. IRS taxable SS $34,000, AGI $134,000. CO shares $25,500/$8,500; A has no remaining pension cap, B subtracts own IRA $10,000. Legal FTI $94,150, total subtractions $44,000 ⇒ **$2,206.60**. |
| Move that $20,000 IRA entirely to A66 | SS still $34,000 excluded; no other pension allowance for A. Same FTI $94,150 less $34,000 ⇒ **$2,646.60**. Tests no household pooling/double SS subtraction. |
| SS full-subtraction threshold | Single age 62, gross SS $40,000, ordinary investments $44,540: provisional $64,540; taxable SS $30,459; AGI $74,999. Full SS subtraction, CO tax **$1,251.36**. Increase investments to $44,541: taxable SS $30,459.85, AGI $75,000.85; SS subtraction only $20,000; CO tax **$1,711.64**. Test $75,000 exact and joint $95,000 separately. |
| SS-only age 67, gross $30,000 | No invented taxable amount; independent IRS SS worksheet then CO full share subtraction: **$0.00**. |
| Qualified Roth/proceeds only | $100,000 ordinary brokerage proceeds or qualified Roth withdrawal, no modeled taxable income: **calculated $0.00**, no invented subtraction/gain. |
| IRA $50,000 + modeled investments $5,000 | Single age 60: FTI $38,900 less $20,000 ⇒ **$831.60**; proceeds/gain attribution cannot add the investment bucket again. |
| High-income standard-deduction addback | Single age 60, ordinary investments $300,000: `(300,000−16,100)×.044` = **$12,491.60**. At $300,001 AGI, add back $15,100: `(300,001−1,000)×.044` = **$13,156.04**. Verify final 2026 worksheet ordering before promotion. |
| Cap/age boundaries | $19,999.99/$20,000/$20,000.01 at 60; $23,999.99/$24,000/$24,000.01 at 65; one day either side of 55/65; IRA receipt either side of 59½. Include no SS and SS already consuming each cap. |
| Survivor/inherited receipts | Survivor ordinary pension age 60 uses own $20,000; age 67 uses $24,000 minus own SS subtraction. Under-55 direct death exception needs source qualification; trust-paid/inherited-history cases do not get guessed relief. |
| Future graduated schedule | **Conditional on official enactment verification:** taxable base $25,000 → $925; $100,000 → $4,075; $500,000 → $21,675; $750,000 → $40,175; $1,000,000 → $59,925; $1,000,001 → $59,925.08. These test measure arithmetic only, not enacted status. |

For final-rounding coverage just above a half-cent boundary, a reconciled age-60 ordinary-investment-only CO base $100.125 produces $4.4055 → **$4.41**; corresponding AGI is $16,200.125 with no retirement/SS subtraction. This is a direct high-precision boundary test, not a cent-only annuity quotation.

### 11.3 Funding, conversion, lifetime and bounded integration oracles

For a Single age-60 2026 IRA $50,000 fixture, independent federal basic-only tax is `$12,400×10% + $21,500×12% = $3,820`. With no cash available and all additional funding from the same traditional owner, within that federal bracket:

* KY: `W=(3,820+543.55)/(1−.12−.035)=5,163.964497…`. At cent funding around $5,163.97, federal tax is about $4,439.68 and KY $724.29. Match the established iterative cent tolerance; do not pretend the continuous solution and separately rounded taxes must be identical at every intermediate step.
* CO: `W=(3,820+611.60)/(1−.12−.044)=5,300.956937…`. Cent result about $5,300.96; federal $4,456.12, CO $844.84. Brokerage/cash-funded taxes leave the original IRA income and base unchanged if no gains are modeled.

These are noncliff, no-SS funding fixtures. Add explicit exclusion-floor crossing, owner-source exhaustion, RMD+conversion, high-AGI/SS discontinuity, same-owner destination absence and execution-shortfall cases. Independently record the final actual conversion, each owner's eligible receipts, annual state taxes and sum of annual taxes; never derive expected exclusions from production helpers.

For CO, any future-year lifetime numeric golden must wait for the applicable legal schedule and approved base policy. Test 2028→2029 enhanced-senior expiration separately from state rate changes; do not hold a temporary deduction indefinitely. For KY, use 2026/2027 published deductions before any later-year approximation. Include death-year MFJ and next-year Single with recipient changes and both annual tax and lifetime aggregation.

Smallest meaningful integration suite per state: calculator/rules parameterized matrix; source/admission tests; one real brokerage and one mixed IRA/RMD/SS/conversion projection; cash and traditional funding/bracket fill; save/reload/baseline/scenario/history/export round trip; two controlled +4%/−2% MC worlds matched to deterministic paths; one small integrated SS comparison and bounded longevity/death scenarios. No exhaustive strategy universe or large random search is needed.

## 12. Integration, persistence, reporting and version freshness

| Surface | Proposal / acceptance requirement |
|---|---|
| Deterministic/annual/lifetime | Existing ProjectionEngine/ProjectionYear state results, totals, survivor status, dates and cash conservation. No state-specific financial branch. |
| Monte Carlo | Same shared calculation boundary and actual annual year. Compare the two controlled worlds with deterministic identical paths, including owner caps and state disclosures. |
| Longevity-weighted / integrated SS | Preserve exact-ending contexts, effective alive owners and authoritative selected SS/pensions. Test both death directions, a bounded second-death ending and current-plan comparison. No analyzer redesign/search benchmark. |
| Roth planning / break-even | Existing state-aware funding and projection evaluation. Verify trial attribution/capacity issue and state-tax deltas through deduction/cap/SS thresholds; comparison objectives do not change. |
| Plan JSON | Existing stable enum codes and StateTaxAssumptions serialization can later hold KY/CO. Defaults for older plans remain MI. No schema change proposed. |
| Baselines / scenarios | State assumptions flow through existing plan snapshots/copies and BaselineProjectionService. Current baseline snapshot holds domain references; do not claim deep historical isolation or redesign that behavior. Test copies/reload and no mutation of live accounts. |
| Retained projection/history | `RetainedProjectionSnapshot.State` records jurisdiction/status/tax/limitations/reasons. Persist original notes and amounts, including the chosen coverage, deduction-year policy and rules version. Restoration/export must not recalculate under newer law. |
| Version freshness | Extend existing NC-specific reference approach with independent KY and CO rule keys/versions only for newly calculated relevant full-plan results. Capture actual frozen plans, including either MC comparison side. Compare saved keys to current reviewed versions; changed version means outdated, not a rewritten historical payload. |
| Existing freshness behavior | `AnalysisVersions`, `AcceptedAnalysisCompletion.withStateTaxRules`, `AnalysisCompatibilityPolicy` and MC lifecycle already provide the hook. Preserve NC and all other existing references/algorithm semantics, plus SS-only behavior. No global fingerprint/schema redesign or retroactive “upgrade” of old artifacts. Missing new-state provenance must not be treated as fresh when applicable. |
| Selection/help/current results | Shared alphabetical choice list only after independent admission. Add state-specific assumptions in AssumptionsView, ResultsSummaryView, ProjectionYearView and SocialSecurityAnalyzerInputSummary. Keep calculations in domain services. |
| Individual/integrated/MC reports | Capture full frozen assumptions through IndividualReportContext, IntegratedReportContext and both MC run/comparison services; show the actual calculated jurisdiction if editor state later changes. |
| CSV/PDF | Existing exporters contain explicit IA/AZ/NC paths. Extend narrowly for each new state, both Single/MFJ and noninvestable outputs. Follow NC's complete common-note rendering, not AZ's three-note truncation. Historical exports retain recorded coverage/rates/notes. |
| Annuity/conditional income | Add only independently verified state allowlist entries; preserve basis, NIIT, Advanced-mode brokerage and growth restrictions, plus analysis admission. Test eligible and refused receipts. |

### Required visible/frozen limitation categories

Each state's notes must independently cover all material categories, not a fixed short paragraph count:

1. Full-year domestic Single/MFJ scope, ordinary private-plan/fully taxable traditional/qualified Roth assumptions, per-person retirement cap and survivor ownership.
2. Brokerage proceeds versus modeled income, missing realized gains, special-source/basis adjustments and no tax-lot accounting.
3. Actual annual rate/deduction rules, version and future-law policy; KY published deduction versus nominal future approximation; CO verified annual schedule and no speculative TABOR reduction.
4. Excluded government/RR/military/historical qualification and inherited/exception cases; no name-based classification.
5. Standard-deduction/credit exclusions, including KY joint-election/family-credit assumptions and any omitted senior credit; CO federal coverage, omitted legal deductions if applicable, SS allocation/shared cap, premature IRA and annuity maturity boundaries.
6. Exact-cent planning rounding and general federal-model limitations. A CO-only improved state base does not imply federal combined taxes were repaired.

Annual details should additionally record individual eligible retirement amounts/subtractions, allocated taxable SS and subtraction, chosen deduction/base coverage, any standard-deduction addback, rate/schedule and final tax. Existing textual limitations can carry these immutable diagnostics without a new persistence schema. Do not expose account identities unnecessarily in exports.

## 13. Separate production admission requirements

### Kentucky gate

* Reverify current statute/rate and published annual deductions; approve actual-joint policy and later deduction approximation.
* Verify all advertised conversion source types; ordinary IRA conversion is established, employer variants are separate.
* Complete per-owner reconciliation including opening RMD, actual/funding withdrawals, pension/survivor and annuity taxable portion; resolve infeasible bracket-fill trials.
* Decide age credit and explicitly disclose omitted family/other credits and specialized exclusions.
* Pass independent calculator, brokerage, Roth/funding, survivor, annuity admission, persistence/history/disclosure and KY version-freshness tests.

### Colorado gate

* Resolve official Amendment 87/competing-measure status and legally applicable 2027+ schedule before lifetime production admission. No guessed/frozen future law.
* Approve and test a CO-only ordinary federal-base bridge or the quantified modeled-base approximation; verify senior conformity and high-income deduction addback.
* Reconcile current administrative rule with IRA conversion eligibility; do not promote a conditional fixture as a legal conclusion.
* Verify individual age/SS ratio/cap priority, early-distribution and midyear 59½ boundaries, direct survivor death exception, and advertised annuity maturity scope.
* Demonstrate convergence/cash conservation near SS and high-income addback discontinuities, plus capacity-complete bracket fill.
* Pass independent CO calculator, brokerage, conversions/funding, survivor, annuity, persistence/reporting and CO version-freshness tests.

For either state, a missing gate keeps that jurisdiction unregistered/unselectable. KY can be admitted alone if CO's legal/base gates remain open. All existing MI/IL/PA/IA/AZ/NC goldens and applicable other-state registration behavior remain unchanged. No code or test in this audit establishes that future admission has occurred.

## 14. Proposed Phase 6E-F workstream and bounded acceptance

1. Obtain explicit approval of the decisions in Section 15. Recheck legal developments, especially CO certification and KY annual deductions. Maintain separate state checklists; do not wait on CO to complete safe KY work.
2. Write independent worksheet fixtures and source/admission tests first. Add each small calculator/rules component behind test configuration. Resolve owner aggregation and the trial conversion contract without weakening existing checks.
3. Build the approved CO base/SS helpers only as needed; no generalized multistate engine. Handle ordinary KY joint computation separately. Verify admitted annuity/conditional cases before allowlisting.
4. Add frozen disclosures, state selection presentation and independent provenance keys using existing completion/history infrastructure. Test changed-rule freshness and old-result exports with intentionally older recorded assumptions.
5. Run focused new-state tests first, then a bounded regression set preserving `MichiganAttributedGoldenTest`, IL/PA/IA production/funding tests, AZ calculator/projection/reporting and both Arizona B3 brokerage regressions, all NC focused calculator/funding/schedule/brokerage/reporting/freshness tests, and applicable annuity/source/persistence contracts. Do not edit unrelated expected financial values.
6. Independently register each state only after its own financial/legal/integration gates pass. If order differs from KY then CO, report the actual resulting state count rather than promising a predetermined ordinal.
7. Native Windows smoke acceptance: select state; SS/private pension/IRA/conversion; mixed-age couple; opening/current RMD; survivor transition; real unknown-basis brokerage; cash conservation; save/reload/baseline/scenario; frozen PDF/CSV after changing editor state; visible complete limitations. No expensive MC benchmark or exhaustive claiming search.
8. Produce Phase 6E-F report with exact modified files, commands/results and separate admission status. Run git status/diff checks; no staging/commit/push without separate authorization.

For future verification use IntelliJ bundled Maven and the workspace repository:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-Dtest=<approved focused class list>' test
```

This is an instruction for the later implementation, **not a test command executed in this audit**. Bounded class selection should exclude unrelated capacity/performance/exhaustive suites, and failed relevant tests must not be described as passing admission.

## 15. Explicit user approval decisions

No approval to implement is inferred from this audit. An implementation authorization should resolve:

1. **Batch with independent gates:** approve KY/CO development in one workstream, while allowing KY-only release if CO remains blocked.
2. **KY filing convention:** approve actual-joint treatment for modeled MFJ, one joint standard deduction and separately calculated retirement caps, with no separate-on-combined optimization. Alternative optimization requires a separate ownership/data decision.
3. **KY future deductions/credits:** approve published 2026/2027 deductions and disclosed nominal carry-forward thereafter; include the $40 age-65 credit (recommended), and explicitly approve the initial family-size/ancillary-credit omission or specify bounded additional relief.
4. **Ordinary source assumptions:** approve written private retirement-plan treatment for generic pensions and standard fully taxable accounts, plus clearly bounded survivor/annuity handling; no name inference or blanket pension refusal.
5. **CO federal-base policy:** choose the narrow CO-only ordinary deduction bridge (preferred for common-retiree accuracy) or explicitly accept incomplete modeled federal taxable income and quantified overstatement. Neither changes the federal calculator or licenses a complete federal return engine.
6. **CO eligibility boundaries:** approve the ordinary IRA/plan subset only after conversion-rule verification; settle annuity maturity and midyear 59½ timing assumptions. Leave facts requiring specialized exception/beneficiary history unsupported.
7. **Narrow conversion-trial plumbing:** approve tests and a bounded fix for infeasible trial attribution/capacity if demonstrated; preserve existing-state financial results. Any wider semantic change returns for approval.
8. **Brokerage/disclosures/freshness:** retain B3 principal admission and reconciliation; approve complete frozen state-specific limitations and independent rule-version keys through existing infrastructure.
9. **Hard legal gates:** CO future schedule certification and ambiguous conversion interpretation must be resolved by authoritative evidence, not waived by accepting a guessed value. No production admission while a material legal requirement remains unresolved.

## 16. Sources, verification limits and repository record

All authoritative URLs above were consulted or explicitly identified as inaccessible on **2026-11-16**. Principal sources are current KY Chapter 141 (database states enactments through 2026), Kentucky DOR annual announcements/forms, Colorado DOR current guide/retirement publications/2026 estimated worksheet, official Colorado legislative bill/ballot materials, the official CCR index, and IRS deduction/conversion guidance. Research distinguished enacted amendments from introduced/lost bills and ballot text from legally established election results.

**Specific verification limits:** final 2026 annual return booklets were not established as available; the KY full resident instruction-packet URL could not be retrieved, so filing structure was checked against Form 740, KRS 141.016/141.081 and 103 KAR 17:020. Colorado's compiled current CCR PDF and complete current Title 39 PDF were not successfully retrieved. The current DOR publications support ordinary retirement amounts/SS allocation; conversion reconciliation and later-law recheck remain express CO gates. Official election-result application required JavaScript; title-board results are not a substitute for certification. No proposed value in this report is promoted into code.

Initial `git status --short`: **clean**. Source, metadata/report/persistence paths and accepted reports/tests were inspected read-only. No Maven, full regression, exhaustive Social Security, large Monte Carlo or performance suite was run; none is needed to validate a documentation-only change. Illustrative arithmetic used decimal worksheet calculations without production tax code.

Final repository verification: `git status --short` lists only this new untracked report; `git diff --check` passed. The new report is also checked directly for whitespace because ordinary Git diff excludes untracked files. No production Java, tests, state rules, federal calculations, persistence schema, registry or existing report was changed. No staging, commit, push or tag. **Stop here; Phase 6E-F has not begun.**
