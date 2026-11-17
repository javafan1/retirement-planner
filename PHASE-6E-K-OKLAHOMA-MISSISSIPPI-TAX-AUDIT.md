# Aledrix — Phase 6E-K Oklahoma and Mississippi tax audit

Verification date: **November 17, 2026**. Scope: **audit and documentation only**. This report authorizes no implementation, registration, tax-rule change or Colorado activation. Proposed policies below require approval for Phase 6E-L.

Evidence labels used throughout:

- **Law:** enacted statutory provisions; effective dates matter.
- **Guidance:** tax-agency instructions, regulations or explanations, identified separately where appropriate.
- **Code:** behavior inspected in the current working tree, not a claim that tests were run today.
- **Interpretation:** application of identified provisions to a modeled transaction; not a newly obtained agency ruling.
- **Proposal:** a recommended implementation or disclosed planning approximation, not existing behavior or an already approved new-state policy.
- **Open:** qualification, evidence or acceptance work that must be resolved or explicitly excluded before admission.

## 1. Executive summary and recommendation

**Both states fit the existing annual state-tax boundary.** Develop them in one workstream if approved, with independent admission. Oklahoma is a moderate implementation; Mississippi is moderate to high because retirement qualification, receipt timing and married-return columns require more care than its flat positive rate suggests. Neither needs Colorado's federal-taxable-income bridge, a new general tax engine or a persistence migration.

The source-confirmed baseline is **17 production jurisdictions**. Oklahoma and Mississippi are absent; Colorado remains disabled. This audit changes none of them.

The important retirement conclusions are:

1. **Oklahoma:** the ordinary exclusion is one shared **$10,000 pool per recipient**, not per account or transaction. A taxable, properly completed traditional IRA-to-Roth IRA conversion falls within the statute's broad IRA-distribution definition on the legal interpretation documented in Section 6. There is no ordinary exclusion age restriction to import from Mississippi. Certain government benefits have different relief; they cannot be classified from pension names.
2. **Mississippi:** **Miss. Code §27-7-15(4)(ff) expressly excludes qualifying traditional IRA-to-Roth IRA conversions**, separately from the general retirement-payment exclusion. Ordinary IRA cash distributions and employer-plan distributions still require their own qualification analysis. Federal inclusion, federal penalty exceptions and Mississippi retirement qualification are different questions.
3. **Mississippi MFJ:** a one-column joint return and a two-column combined return are not interchangeable. The latter requires each spouse's income. Two $10,000 zero-rate bands cannot be manufactured by dividing one person's income or an unowned household aggregate in half.
4. **Annual timing:** existing sources generally identify owner, source and year, not every payment date. Mississippi withdrawals in a year straddling age 59½ need a bounded qualification rule or structured refusal. This does not create an age restriction on the separate IRA-conversion exemption.

Recommend an ordinary private-retirement subset, an explicitly disclosed standard-deduction model, B3 brokerage admission and state-local reconciliation. Do not advertise blanket exemption of all Mississippi annuities, employer Roth rollovers or early withdrawals. No fundamental unresolved rate question prevents a bounded implementation. Specialized qualification questions can remain excluded; ordinary timing/ownership safeguards and funding tests are actual admission gates.

## 2. Source-confirmed production baseline and architecture

### Registry and working tree

`src/main/java/com/daviddunn/retirementplanner/domain/model/StateTaxJurisdiction.java` contains:

| Production treatment | Jurisdictions |
|---|---|
| Dedicated calculator/adapter, nine | Arizona, Georgia, Illinois, Iowa, Kentucky, Michigan, North Carolina, Pennsylvania, Virginia |
| No general individual income tax, eight | Alaska, Florida, Nevada, New Hampshire, South Dakota, Tennessee, Texas, Wyoming |
| Not registered | Oklahoma, Mississippi, Colorado |

`domain/tax/state/ProductionStateTaxService.java` explicitly routes the nine taxable states and registers the eight no-general-income-tax jurisdictions. Research catalogs and audit worksheets are not this registry. GA rules have version `GA-2026-1` and key `georgia-income-tax-rules`; VA has `VA-2026-1` and `virginia-income-tax-rules`. `colorado/ColoradoTaxRules.java` has `CO-2026-PREPARATORY-2` and `productionAdmitted() == false`; no Colorado production route exists.

Initial `git status --short` was empty. Reviewed the accepted Phase 6E-I/J GA/VA reports, Phase 6E-E/F KY/CO reports and Phase 6E-B3 Arizona brokerage report against current sources. Historical admission counts in those reports are chronological, not the present baseline. The old `IowaMississippiAdmissionWorksheetTest` explicitly describes research worksheets and pending qualifications; it is not a Mississippi production calculator or current-law certification.

### Actual source locations and reuse

Paths in the following table are relative to `src/main/java/com/daviddunn/retirementplanner/`.

| Component/path | Inspected behavior and relevance |
|---|---|
| `domain/model/StateTaxAssumptions.java` | Selected jurisdiction and calculation method; no new tax-entry model presumed. |
| `domain/tax/state/StateIncomeTaxCalculator.java`, `StateTaxCalculationRequest.java` | One annual calculator interface; request carries year, filing status, household facts, income, federal result, projected rules and assumptions. |
| `domain/tax/state/ProductionStateTaxResult.java` | Calculated zero differs from `UNSUPPORTED`; unsupported has reasons/limitations and no numeric tax. Never default unavailable tax to zero. |
| `domain/tax/state/TaxHouseholdContext.java` | DOBs, household/active owners, representative projection date and selected pension result; not a ledger of all distribution dates. |
| `domain/tax/TaxIncome.java`, `TaxIncomeCalculator.java`, `TaxIncomeAttribution.java` | Household scalar income plus source metadata. Conversion enters ordinary income once. Attachment replaces compatible unowned aggregates and retains an unattributed residual where not reconciled. |
| `domain/income/IncomeSourceAttribution.java` | Owner, account identity/type, destination, purpose/year, optional taxable portion, pension and conditional-receipt evidence. Unknown brokerage taxable portion is absent, not legally zero gains. |
| `domain/model/RetirementParticipantFacts.java` | Dated separation/retirement-condition evidence; affirmative facts without an effective date are not automatically established on a distribution date. These facts do not certify every plan's legal qualification. |
| `domain/tax/FederalTaxCalculation.java`, `FederalTaxBaseBreakdown.java` | Modeled AGI, taxable SS, taxable income, deduction and NIIT evidence. New states can use the ordinary modeled income boundary without changing federal results. |
| `domain/tax/TaxFundingCalculator.java` | Each trial allocates actual withdrawals, rebuilds income/attribution, recalculates federal/state tax, then recomputes the final result; bounded iteration and cent tolerance already exist. |
| `domain/roth/RothConversionBracketFillCalculator.java` | Iterative federal target with state-aware funding. KY/GA alone currently use capacity-complete hypothetical trials; see Section 13. |
| `domain/roth/ProjectedPortfolioRothConverter.java` and conversion eligibility policy | Same-owner executable transfers to Roth IRA, account-order execution, actual/shortfall recording, inherited/source restrictions. No in-plan conversion execution is currently modeled. |
| `domain/projection/ProjectionEngine.java` | Orchestrates annual tax, RMDs before conversions, funding and result accumulation. Passes frozen owner evidence; ordinary investment-income scalar is currently zero in its normal call. Portfolio growth/brokerage proceeds do not create realized gains. Add no new state arithmetic here. |
| `domain/tax/state/KentuckyProductionTaxCalculator.java`, `kentucky/KentuckyTaxRules.java` | Closest Oklahoma per-recipient retirement-pool pattern, with different legal amounts/eligibility. |
| `domain/tax/state/GeorgiaProductionTaxCalculator.java`, `georgia/GeorgiaInvestmentReconciliation.java` | Strong source-validation/B3 pattern. Its aggregate-placeholder reconciliation is useful; Georgia investment exclusion eligibility and joint allocations are not Oklahoma/Mississippi law. |
| `domain/tax/state/VirginiaProductionTaxCalculator.java`, `virginia/VirginiaAgeDeduction.java` | Examples of annual filer-age/household worksheets and exemptions, not reusable legal constants. |
| `domain/tax/state/NorthCarolinaProductionTaxCalculator.java`, `northcarolina/NorthCarolinaTaxRules.java` | Small versioned yearly rules and ordinary AGI/SS boundary. |
| `domain/tax/state/ArizonaProductionTaxCalculator.java`, Illinois/Iowa/Pennsylvania calculators and `MichiganProductionTaxAdapter.java` | Existing admission/reconciliation and retirement-specific boundaries; preserve all their financial results. |

The normalized investment aggregate is especially important: `TaxIncomeCalculator` emits an unowned `INTEREST` placeholder for the whole modeled investment bucket. Attached `CAPITAL_GAIN`/`DIVIDEND` records may describe those same dollars without replacing that different-kind placeholder. An ordinary `INTEREST` replacement works differently. New-state helpers must recognize the actual placeholder shape and reconcile, not sum all records. Duplicate, excessive, wrong-year or contradictory evidence must still fail.

## 3. Oklahoma verified current rules

**Law:** HB 2764, approved May 28, 2025, amended **68 O.S. §2355(D)** for 2026. The old six-positive-band schedule is not the 2026 schedule. Let `T` be nonnegative Oklahoma taxable income after the admitted adjustments, state deduction and exemptions:

| Single taxable income | Tax before credits | MFJ taxable income | Tax before credits |
|---|---:|---|---:|
| Through $3,750 | $0 | Through $7,500 | $0 |
| $3,750–$4,900 | 2.5% × (T − 3,750) | $7,500–$9,800 | 2.5% × (T − 7,500) |
| $4,900–$7,200 | $28.75 + 3.5% × (T − 4,900) | $9,800–$14,400 | $57.50 + 3.5% × (T − 9,800) |
| Above $7,200 | $109.25 + 4.5% × (T − 7,200) | Above $14,400 | $218.50 + 4.5% × (T − 14,400) |

Boundaries are continuous; the marginal rate applies only to the portion in a band. [Enrolled HB 2764, §2, pp. 11–12](https://www.oklegislature.gov/cf_pdf/2025-26%20ENR/hB/HB2764%20ENR.PDF).

**Guidance/current official amounts:** federal AGI is the opening base; standard deduction **$6,350 Single / $12,700 MFJ**; ordinary personal exemption **$1,000 for each eligible nondependent filer**. These amounts are independently confirmed in OTC's [2026 annual report, individual income-tax description](https://oklahoma.gov/content/dam/ok/en/tax/documents/resources/reports/annual-reports/otc/AR-2026.pdf) and [2026 MeF guide, Appendices B/C](https://oklahoma.gov/content/dam/ok/en/tax/documents/resources/publications/software-developers/2026MeFDeveloperGuide.pdf). Federal standard-deduction filers use the state standard deduction; federal itemizers have a different state path outside this scope.

**Common senior provision:** another **$1,000 per qualifying age-65 filer**, subject to federal AGI of **at most $15,000 Single / $25,000 MFJ**. Traditional IRA-to-Roth conversion income is removed **for this eligibility test**, not automatically removed in full from Oklahoma taxable income. The published birthday convention includes a person turning 65 on January 1 of the following year. A deceased filer must not acquire later birthdays. [OTC 2026 Form 511 developer packet, exemptions, pp. 8–9](https://oklahoma.gov/content/dam/ok/en/tax/documents/forms/software-developers/Dev511-Pkt-26.pdf).

Subtract federally included Social Security. Ordinary taxable interest, dividends and modeled gains remain ordinary state income; no preferential general capital-gain rate. **68 O.S. §2358** and OAC **710:50-15-49** govern retirement adjustments; see Sections 5–6. State-source municipal interest, federal-obligation interest and qualifying Oklahoma gains can require separate adjustments, not invented from the generic investment bucket. [Official statute publisher, §2358](https://govt.westlaw.com/okjc/Document/ND6B5D5114F1E11F19208D31E62F66B8C?viewType=FullText), [OTC Chapter 50 rules](https://oklahoma.gov/content/dam/ok/en/tax/documents/resources/rules-and-policies/agency-rules/2025/Chapter_50-Income_Tax-2025.pdf).

**Thresholds/rounding:** gross-income filing requirements are not a Virginia-style no-tax switch. For an ordinary under-65 nondependent with no retirement adjustment, the state deduction, exemption and zero band produce zero tax through **$11,100 Single / $22,200 MFJ**, not merely through the filing threshold. Official Form 511 uses whole-dollar entries and a tax table below $100,000. **Proposal:** use statutory continuous brackets, exact `BigDecimal` intermediates and final `HALF_UP` cents; disclose small return-table/whole-dollar differences. These goldens test that planning policy, not an identical filed Form 511.

There are ordinary low-income benefits outside the proposed income-tax calculation: sales-tax relief under Form 538-S, elderly/disabled property-tax relief under Form 538-H, and other credits. They need household-income/property/eligibility facts and may lower actual taxes or generate refunds. The senior exemption above should be implemented; omission of credits must be visible, not described as absence of benefits. [OTC income-tax help](https://oklahoma.gov/tax/helpcenter/income-tax.html), [current resident forms/instructions](https://oklahoma.gov/content/dam/ok/en/tax/documents/forms/individuals/current/511-Pkt.pdf).

## 4. Oklahoma future-law schedule

| Period | Verified legal position | Proposed projection policy |
|---|---|---|
| 2026 | New 0%/2.5%/3.5%/4.5% bands | Implement the correct Single/MFJ bands. |
| 2027 | No new reduction established in the reviewed evidence | Carry the 2026 schedule. |
| Potential 2028 and later | Additional cuts require statutory certification; no unconditional dated elimination | Retain the latest reviewed legally effective schedule until an actual reduction is incorporated in a new rules version. |

HB 2764 ties reductions to **62 O.S. §34.103(D)**: comparison collections must exceed the historical base plus the reduction-cost threshold. Each certified step reduces **each positive marginal rate by 0.25 percentage point**, with zero as the floor; it is not just a top-rate cut. Final February certification precedes the effective January 1; the first December preliminary determination is in **December 2026**, so the first possible ensuing final February/January sequence is **February 2027 / January 2028**. Revenue-failure safeguards can prevent a cut.

**Subsequent enactment checked:** HB 4072, signed April 23, 2026 and effective August 13, 2026, creates the Taxpayer Endowment Trust Fund and amends §34.103. Its retained certification framework still compares nonlocal OTC collections, using the December 2024 reporting methodology, with the highest earlier fiscal-year collections and the statutory cost threshold. The threshold uses 1.25 times estimated cut cost, with prior-loss bounds. The special December 2026 certification also tests first-five-and-a-half-month FY2027 revenues against 95% of the February estimate and absence of revenue failure. The fund's hoped-for earnings are not a legally established future tax schedule. [Enrolled HB 4072, §§5/8](https://www.oklegislature.gov/cf_pdf/2025-26%20ENR/hB/HB4072%20ENR.PDF), [Governor's signed-law announcement](https://oklahoma.gov/governor/newsroom/newsroom/2026/governor-stitt-signs-bill-to-accelerate-path-to-zero-income-tax-secure-oklahomas-historic-savings.html).

Reviewed OTC's 2026 legislative summary, including HB 4432's **2027 wagering-loss itemized-deduction** amendment; it does not change the ordinary standard-deduction model or justify a retirement exclusion increase. No additional unconditional ordinary rate/deduction change was identified. Do not assume the summary is an exhaustive codification. [OTC 2026 legislative update](https://oklahoma.gov/content/dam/ok/en/tax/documents/resources/publications/legislation/2026LegislativeUpdate.pdf).

**Proposal:** hold standard deductions, exemptions and retirement caps nominally; no indexing provision for these reviewed ordinary amounts was identified. Plan inflation does not trigger legal rate changes. Recheck post-audit enactments/certifications before setting production constants.

## 5. Oklahoma retirement and government-plan matrix

**Law/guidance:** §2358(E)(14)'s definition covers distributions/withdrawals from enumerated qualified retirement arrangements. Schedule 511-A line 6 is limited to the federally included amount and **$10,000 less the recipient's line-5 government exclusion**. There is one pool per individual, no spousal transfer and no separate RMD/conversion cap. The historical income limitation on the ordinary exclusion ended in 2010. [Official §2358 retirement definition](https://govt.westlaw.com/okjc/Document/ND6B5D5114F1E11F19208D31E62F66B8C?viewType=FullText), [OTC retirement FAQ](https://oklahoma.gov/tax/helpcenter/income-tax.html).

| Source/classification | Legal adjustment | Initial bounded recommendation |
|---|---|---|
| Ordinary qualified IRA/rollover IRA, §408 retirement annuity/SEP | Within recipient's $10,000 pool | Admit taxable modeled portion with reliable source/owner. No general minimum age established for this pool. |
| Ordinary qualified §401 pension/401(k), §403(a)/(b) annuity/403(b), eligible §457 | Same pool | Admit ordinary withdrawals/RMDs subject to existing source/availability safeguards; do not extrapolate to every conversion variant. |
| Ordinary private defined-benefit pension | Same pool if qualified retirement plan | Disclose ordinary qualified private-plan assumption; names do not certify qualification. Contradictory facts override it. |
| Social Security / qualified Roth withdrawal | SS subtraction / already absent from modeled AGI | No consumption of $10,000 pool. |
| Commercial nonqualified investment annuity under §72 | Not automatically a §408 retirement annuity or §403 employee annuity | Preserve federal taxable portion as ordinary income when its nonqualified character is established; do not award a pool exclusion merely because payments are called an annuity. Return of basis is not taxed again. |
| Enumerated Oklahoma government systems; ordinary qualifying federal civil-service benefits | Schedule A5, ordinarily up to $10,000 | Shares the ordinary cap through A6 reduction. No default full government exemption. Explicit special facts require qualified handling or unsupported status. |
| Qualifying federal civil-service benefits paid instead of SS, including qualifying survivor annuity (CSRS category) | Schedule A3, fully excluded under current schedule | Specialized classification not presently established by ordinary pension inputs; initially outside supported classification. |
| Military retirement | Schedule A4, fully excluded from 2022 | Specialized military scope excluded initially; do not use obsolete 75% guidance. |
| Early termination payment from government plan | May fail A5 but can qualify under A6's distinct rules | Do not import the A5 retirement/disability condition into every private IRA distribution. |
| Railroad, disability, inherited/basis/service-history exceptions | Separate legal/factual rules | Out of ordinary scope unless already supported by exact admitted evidence. |

The full CSRS category is not all FERS. OTC distinguishes the qualifying CSRS component and certain FERS supplements; eligibility is not discernible from a generic pension's owner/amount/start date. A3 income cannot also be deducted on A5/A6. A5 reduces available A6; the separately excluded A3/A4 amounts should not be subtracted from that cap merely by analogy. [2026 Schedule 511-A instructions, A3–A7](https://oklahoma.gov/content/dam/ok/en/tax/documents/forms/software-developers/Dev511-Pkt-26.pdf), [OAC 710:50-15-49](https://oklahoma.gov/content/dam/ok/en/tax/documents/resources/rules-and-policies/agency-rules/2025/Chapter_50-Income_Tax-2025.pdf).

**Proposal:** ordinary pensions may proceed under the stated private-qualified-plan assumption; unknown government/private classification alone must not reject every retiree. Reliable represented special-plan evidence must return structured unsupported rather than silently applying the wrong private-pension calculation. Current pension evidence lacks the service/CSRS-component facts necessary for comprehensive government treatment. No classification UI is proposed.

## 6. Oklahoma Roth conversion conclusion

**Interpretation supported by identified law:** a properly completed taxable traditional IRA-to-Roth IRA conversion is a distribution from a §408 IRA; §2358's ordinary definition covers total distributions/withdrawals from those arrangements. IRS Publication 590-A explicitly treats the source payment as a distribution whose otherwise taxable portion remains income on conversion. Neither the reviewed ordinary definition nor A6 adds a general age or cash-retention condition. The separate Oklahoma senior-exemption rule expressly recognizes IRA conversion income. These provisions support including the federally taxable completed IRA conversion in the same recipient's ordinary $10,000 pool. This is an application of the statute, **not an Oklahoma conversion-specific letter ruling**. [IRS Publication 590-A, converting a traditional IRA](https://www.irs.gov/publications/p590a), [official Oklahoma §2358](https://govt.westlaw.com/okjc/Document/ND6B5D5114F1E11F19208D31E62F66B8C?viewType=FullText).

| Transaction | Proposed treatment / confidence boundary |
|---|---|
| Actual same-owner, fully taxable traditional/rollover IRA → Roth IRA | Share owner pool with withdrawals/pension/RMD. Support on the reasoned statutory application above. |
| RMD plus conversion | RMD first; remaining eligible assets alone convert. Both taxable qualifying receipts share one cap, with no duplicate conversion income. |
| Employer plan → Roth IRA | Source may appear in the statutory list, but conversion-specific/source conditions need independent verification before advertising eligibility. Initially refuse the affected variant if not established. |
| In-plan Roth conversion | Not current executor behavior; out of scope, no invented representation. |
| Partial IRA basis | Exclude only federally included taxable portion if reliably modeled. Do not create pro-rata basis accounting; unsupported if the present fully-taxable model conflicts with represented basis facts. |
| Early ordinary IRA cash distribution | Ordinary Oklahoma pool is not automatically denied due to age; federal additional-tax coverage is a separate existing limitation. |
| Qualified Roth cash distribution | Already outside modeled AGI; no second subtraction. |

Current account source, destination and executed amount establish ordinary IRA conversion identity/owner without a new field. They do not establish every employer conversion variant. Cash retained or withdrawn to fund conversion taxes is a separate receipt; a conversion label must not reclassify it.

## 7. Mississippi verified current rules

**Law:** Miss. Code **§27-7-5**, as amended by HB 1 (2025), leaves **2026 at 4% of taxable income above $10,000 per taxpayer computation**. There is no preferential ordinary capital-gain rate. [Enrolled HB 1, §1](https://billstatus.ls.state.ms.us/documents/2025/pdf/HB/0001-0099/HB0001SG.pdf).

**Guidance/ordinary amounts:** Single standard deduction **$2,300**, personal exemption **$6,000**; joint/combined **$4,600 / $12,000**. Additional exemption **$1,500 for each eligible age-65 filer**. Blind/dependent provisions exist but are outside the no-dependent ordinary subset. Use age status at year-end, or applicable death-date status, rather than aging a dead spouse forward. The instructions say 65 or over, despite a general webpage's shorthand “over 65.” A January 1 birthday convention not expressly resolved in the reviewed Mississippi materials should be verified before advertising that edge; an isolated ambiguous case can be refused without blocking ordinary DOBs. [DOR general information](https://www.dor.ms.gov/general-information), [2025 Form 80-100, additional exemptions](https://www.dor.ms.gov/sites/default/files/tax-forms/individual/80100251%202.pdf).

Mississippi defines its **own gross/net income and state adjusted income**; Form 80-105 assembles categories rather than simply copying federal taxable income. Qualified retirement receipts, SS and the separately specified IRA conversions are excluded by state provisions. **Proposal:** for the admitted ordinary sources, bridge from modeled federal AGI by removing only their federally included state-exempt portions and reconciling the component totals. Do not claim that Mississippi universally adopts federal AGI. No federal standard/age/enhanced-senior below-AGI deduction automatically becomes a Mississippi deduction. [Form 80-105, income and adjustment columns](https://www.dor.ms.gov/sites/default/files/tax-forms/individual/80105258%201.pdf), [Mississippi Code access through DOR](https://www.dor.ms.gov/forms-resources/laws-regulations/2026-legislation).

The public FAQ still gives **4.4%**, a 2025 rate; the general page contains both old 2025 prose and a dated table correctly giving 4% for 2026. Use enacted HB 1 and the dated table, not undated FAQ wording. Ordinary gross filing requirements of $8,300/$16,600 are not a substitute for applying deductions/exemptions and the zero band. With no senior exemption, ordinary Single income reaches state tax only above **$18,300**; one-column MFJ above **$26,600**. Two-column treatment depends on actual spouse income. [DOR FAQ](https://www.dor.ms.gov/individual/individual-income-tax-frequently-asked-questions).

**Rounding proposal:** retain exact decimal income/adjustments, floor the reconciled taxable base at zero, apply actual-year rate and round final planning tax to cents `HALF_UP`. Filed return fields are whole dollars; disclose that distinction. No general age credit replacing the $1,500 exemption was established for this scope; itemization and ancillary credits are excluded expressly.

## 8. Mississippi 2027–2031+ schedule and conditions

| Calendar tax year | Rate above the applicable $10,000 zero band | Status |
|---|---:|---|
| 2026 | 4.00% | Enacted |
| 2027 | 3.75% | Unconditional enacted step |
| 2028 | 3.50% | Unconditional enacted step |
| 2029 | 3.25% | Unconditional enacted step |
| 2030 | 3.00% | Unconditional enacted step |
| 2031 onward | Further reductions only if statutory conditions are met | **Proposal: hold 3.00% until a legally established reduction is reviewed/versioned.** |

HB 1 §2 requires a fully funded Working Cash-Stabilization Reserve Fund and compares adjusted General Fund revenue with the following fiscal year's defined appropriations. LBO figures begin October 2029, and DOR cut-cost estimates begin December 2029. Initial revenue/appropriations years are FY2029/FY2030; a resulting cut first applies in calendar 2031. Appropriations exclude specified excess PERS contributions; revenue removes nonrecurring receipts. [HB 1, §2](https://billstatus.ls.state.ms.us/documents/2025/pdf/HB/0001-0099/HB0001SG.pdf), [DOR enacted-2025 legislation summary](https://www.dor.ms.gov/forms-resources/laws-regulations/2025-legislation).

**Important literal-text issue:** the enrolled text expresses excess thresholds as **0.85%, 1% and 1.15% of the defined cost of a one-percent cut**, leading to rate reductions of 0.20, 0.25 or 0.30 percentage point. It must not silently be rewritten as 85%, 100% and 115% of that cost. The unusual scale requires current authoritative interpretation/amendment review before implementing any triggered reduction. This does not prevent implementation of the unconditional 2026–2030 schedule and a disclosed post-2030 hold. No forecast of fiscal conditions or elimination date is authorized.

Reviewed DOR's 2026 income-tax legislation summary as well; no replacement ordinary rate/exemption schedule or repeal of the IRA-conversion exclusion was identified. Its listed credit changes do not establish a new retirement exemption. [DOR 2026 legislation](https://www.dor.ms.gov/forms-resources/laws-regulations/2026-legislation). No automatic inflation of the reviewed deduction/exemption amounts was established.

## 9. Mississippi retirement eligibility and early-distribution matrix

The governing regulatory provision is **35 Miss. Admin. Code, Part III, Subpart 2, Chapter 07**, paragraphs 100–105. It includes IRAs/Keogh and qualifying retirement plans, imposes retirement conditions for deferred compensation, excludes early distributions from the general exemption, follows IRC treatment of Roth distributions, and extends exemption to a spouse/beneficiary after the primary retiree's death. **The chapter does not itself replace every plan condition with a universal numeric 59½ test.** [Secretary of State, Title 35 compilation, PDF p. 18](https://www.sos.ms.gov/adminsearch/ACCode/00000158c.pdf).

Classification: **A** = existing exact facts can suffice; **B** = proposed disclosed ordinary-plan assumption; **C** = material missing facts/legal qualification, unsupported affected source; **D** = outside planned scope. B is not yet approved.

| Receipt | Mississippi treatment and initial boundary | Class |
|---|---|---|
| Ordinary fully taxable, noninherited traditional IRA cash, after normal qualification throughout receipt period | Exempt qualifying retirement income. Age 59½ already attained before all relevant receipts is the ordinary IRA boundary; no wage-retirement test should be invented for an IRA. Verify source/timing. | A for proved period; B for stated ordinary-qualified-IRA assumption |
| Ordinary IRA cash clearly before 59½ and subject to federal early-distribution additional tax | Not generally exempt; include federally taxable amount. State has no parallel early-withdrawal penalty. Full-plan federal penalty coverage is separate. | A state treatment; federal admission limitations remain |
| Early IRA cash with a federal penalty exception | Absence of federal penalty alone does not establish state retirement qualification. Disability/death/periodic-payment conditions require their own proof. | C/D |
| Year/partial period straddles 59½ | Annual total does not prove which side of birthday payments occurred; no automatic year-end qualification. | C unless receipt evidence resolves timing |
| Ordinary noninherited RMD at normal RMD age | Generally qualified retirement income; validate owner/source and opening versus current receipt totals. | A/B |
| 401(k)/403(b), defined-contribution/deferred-compensation payments | Exempt when recipient satisfies applicable plan retirement requirements at receipt. Age 55 penalty exception or account type alone is not universal proof. Dated existing participant facts can support an ordinary qualified-plan assumption. | B with evidence; C if adverse/ambiguous |
| Ordinary private defined-benefit pension | Qualified retirement pension exempt; pension start date/name alone cannot certify employment/service exceptions. | B; explicit conflict C |
| Employer/IRA qualifying retirement annuity | Taxable included portion may be exempt if plan/receipt conditions established; basis portion never enters taxable income again. | A/B |
| Privately purchased commercial nonqualified annuity, fully/partly taxable | An IRC §72 basis calculation does not establish membership in a qualifying private retirement system/plan. Ordinary investment-contract qualification is not established here. Restrict affected exemption, or seek specific DOR clarification before broader support. | C for disputed qualification; D for specialized conditions |
| Survivor pension from a qualified primary retiree | Exemption extends to beneficiary. Current recipient may be young; do not deny solely for survivor's age or assign income to deceased owner. Retain original qualification evidence. | A/B; missing original qualification C |
| Inherited IRA/death benefits | Death extension matters, but beneficiary/source facts and state scope must be established. Do not treat every inherited receipt as ordinary own-IRA cash or every death payment as pension. | C/D initially |
| Qualified Roth distribution | Federal exclusion already keeps it out of modeled base; no second subtraction. | A under existing qualified-Roth assumption |
| Nonqualified Roth earnings/distribution | State follows IRC distribution treatment; current qualified-withdrawal model is insufficient for basis/order/exception calculations. | C/D |
| Proper traditional IRA → Roth IRA conversion | Separate statutory exclusion, Section 10; not the ordinary cash age test. | A for evidenced supported conversion |
| Employer-to-Roth / in-plan conversion | Dedicated IRA clause does not expressly establish these variants. Separate general retirement qualification, if any, needs verification. | C/D initially |

The official instructions flag early/excess distributions under the IRC, reference Form 5329 and distinguish separation pay from retirement. They also require deferred-compensation eligibility at receipt. Do not infer that all federal penalty exceptions satisfy the state test. [Form 80-100, pension/annuity income instructions](https://www.dor.ms.gov/sites/default/files/tax-forms/individual/80100251%202.pdf).

**Annual timing proposal:** use DOB and the actual modeled receipt interval. For full-year own-IRA payments, age 59½ attained by the opening of the interval safely avoids the crossing issue; no invented universal withdrawal date. For a partial first year, check projection-period receipts separately from pre-projection/opening RMD evidence. If the annual modeled amount crosses the threshold and its timing cannot be established, return a precise unsupported qualification reason. Do not add a timing wizard. Ordinary older retirees/RMDs and the independently exempt conversion path remain usable.

**Existing-data limit:** `retirementConditionsStatusOn(date)` and pension source facts can establish represented conditions; they cannot reconstruct an unrepresented plan document, disability exception or historical service period. Admit ordinary pension/retirement plans only with an approved disclosed ordinary-plan qualification policy and honor contrary represented facts.

## 10. Mississippi Roth conversion conclusion and remaining questions

**Statutory conclusion:** §27-7-15(4)(ff), effective from tax year 2010, excludes amounts properly converted under the IRC **from a traditional IRA to a Roth IRA**. It separately extends the provision to the spouse/beneficiary at the primary retiree's death. It is not merely the general paragraph (4)(l) retirement-payment exemption, and its text does not impose the cash-distribution 59½ qualification test.

Evidence chain: Mississippi DOR's **2011 Form 80-100 enactment notice** identifies HB 1673 and its January 1, 2010 effective date; subsequent official legislative texts retain clause (ff), including enrolled 2022 HB 1529 and the existing-law recitation in 2026 HB 1072. The latter's proposed new provisions are **not** treated as enacted. The current codified clause also corroborates the result. [DOR-authored 2011 instructions, archived by NBER, p. 1](https://taxsim.nber.org/historical_state_tax_forms/MS/2011/indiv_8010011i.pdf), [official enrolled 2022 HB 1529](https://billstatus.ls.state.ms.us/documents/2022/html/HB/1500-1599/HB1529SG.htm), [official 2026 existing-law recitation, clause ff](https://billstatus.ls.state.ms.us/documents/2026/html/HB/1000-1099/HB1072PS.htm). See Section 21 for access limitations and codification cross-check.

**Proposal:** support completed fully taxable traditional/rollover IRA-to-Roth IRA conversions with current same-owner execution evidence, including an owner under 59½. Subtract only federally included conversion income, once. A retained amount used to pay taxes is a separate cash distribution and must independently pass the general retirement test. A failed/requested conversion is not exempt executed income. An RMD cannot become an exempt conversion; it is distributed first and evaluated as an RMD.

**Not established by clause ff:** direct employer-plan-to-Roth IRA rollovers, in-plan conversions, nonqualified Roth cash, partial-basis reconstruction, special inherited transactions. Do not manufacture eligibility by treating an employer source as an imaginary intermediate IRA. Current source/destination identity distinguishes supported own-IRA conversions from employer sources. This source-specific limitation is suitable for an initial bounded admission if visible and structurally refused; normal own-IRA conversion planning need not be blocked on the mistaken premise that Mississippi never addresses conversions.

## 11. Tax bases, deductions and exemptions — comparison

| Item | Oklahoma | Mississippi |
|---|---|---|
| Legal starting approach | Federal AGI with §2358 modifications | Own gross/net/adjusted income rules; ordinary modeled AGI can be reconciled as a bridge |
| Federally taxable SS | Subtract included amount once | Remove included amount once; state-exempt |
| State standard deduction, Single/MFJ | $6,350 / $12,700 | $2,300 / $4,600 |
| Ordinary filer exemptions | $1,000 per eligible filer | $6,000 Single / $12,000 joint-combined |
| Common senior exemption | $1,000 each; AGI ceilings and IRA-conversion adjustment | $1,500 each, ordinary age eligibility |
| Retirement relief | Recipient $10,000 ordinary pool with A5 coordination | Qualifying retirement income exempt without ordinary dollar cap; separate IRA-conversion clause |
| Federal basic/age/enhanced senior deductions | Below AGI: do not import | Do not substitute for separate state deductions/exemptions |
| Married computation | Joint bands/deduction, individual retirement caps | One-column joint or owner-based combined columns; Section 12 |
| Other state adjustments | Federal obligations, nonstate municipal interest, special gains, business etc. require facts | Similar special securities/gain/state-income differences require facts |

**Scope:** full-year domestic Single/MFJ, ordinary nondependent filers, standard deduction, fully taxable modeled traditional accounts, existing qualified Roth convention. Do not fabricate blind/dependent exemptions, itemization, tax-exempt security mix, loss carryovers or specialized credits. Known incompatible special evidence requires unsupported status. Ordinary missing brokerage basis uses the approved B3 approximation, not blanket refusal. Federal tax calculations remain untouched.

## 12. Individual/spousal ownership requirements

| Income evidence | Existing sufficiency | New-state use |
|---|---|---|
| Account withdrawals, opening/projected RMDs | Account identity, owner, purpose/year available | OK aggregate qualifying receipts by recipient; MS test recipient/source eligibility and taxable column. Keep opening/current RMD distinct. |
| Actual conversions | Executed source/destination and owner totals available | Both states distinguish IRA from employer source; cap/qualification applies to actual income. Hypothetical trials need capacity fix analysis. |
| Pension/survivor pension | Selected pension result has current recipient and original source facts | Income belongs to recipient. OK cap never transfers from deceased; MS qualifying-survivor exemption can continue without survivor's own age minimum. |
| Conditional/annuity receipt | Existing admitted taxable fraction, basis and source terms | Does not by itself certify a qualified retirement plan. Ordinary conditional income is not retirement merely because it begins at retirement. |
| Gross SS / household federally taxable SS | Recipient gross and household taxable total available | Both states fully exclude federally included SS: no proportional taxable-SS allocation needed for their ordinary state calculation. Do not invent individual taxable shares. |
| Modeled investment aggregate | Often household/unowned | OK retains taxable amount without owner-dependent investment exclusion. MS needs ownership for exact combined columns; fallback below requires approval. |
| Explicit investment sources | Potential PRIMARY/SPOUSE or represented joint evidence | Reconcile against same scalar once. Do not import GA's 50/50 legal rule without independently applicable ownership facts; unproven joint split remains unallocated. |
| Death year/later Single | Existing status, DOB/alive facts and survivor sources | MFJ through modeled death year, Single later. Do not give future age benefits or unused retirement cap to deceased. |

### Mississippi married-return computation

Official Form 80-100 permits joint, combined and separate methods. Joint places all income/deductions in column A; combined uses each spouse's income in A/B, allowing deductions/exemptions to be allocated flexibly. One spouse's income cannot be split between spouses. Additional exemptions can also be allocated on a combined return. [Official 2025 instructions, pp. 5–6](https://www.dor.ms.gov/sites/default/files/tax-forms/individual/80100251%202.pdf).

**Proposed small worksheet, not a general filing optimizer:** for the nonnegative ordinary standard-deduction subset, let `N_A,N_B` be each spouse's state net income after qualified exclusions, `X` the shared deduction plus exemptions, and `r` the actual-year positive rate. Exact minimum combined-column tax is:

`r × max(max(N_A − 10,000, 0) + max(N_B − 10,000, 0) − X, 0)`.

This is an independently derived equivalence: each dollar of flexible deduction/exemption first reduces a column above its zero band; the remaining positive bases can therefore be reduced by the available total X. It does not transfer unused zero bands or move income between owners. Negative business/loss situations are outside the equivalence's admitted assumptions. At implementation, compare the formula to explicit official two-column worksheets, including unequal allocation examples.

**Proposed unknown-ownership policy:** where an MFJ ordinary taxable investment remainder cannot be assigned reliably, use the legally available **one-column joint method** on household income and disclose possible overstatement compared with an accurately attributed combined return. This remains an explicit approximation, even if both spouses have some income. In this ordinary flat-positive-rate subset the extra tax cannot exceed one $10,000 positive-rate band ($400 at 2026's rate), before excluded credits/other adjustments. Do not present the fallback as the optimum return. Single can assign an otherwise uncontradicted ordinary household aggregate to its sole filer. Contrary facts override that assumption.

**Approval alternative:** initial one-column-only married support is smaller but needlessly forgoes exact calculations where ownership is already available. Recommend exact combined where fully evidenced plus disclosed joint fallback. Neither choice needs a new filing-option UI. Contradictory evidence is unsupported, never a reason to choose the fallback and hide a conflict.

## 13. Funding and Roth bracket-fill implications

**Code:** final execution already caps conversion by valid same-owner source/Roth destination capacity and excludes RMD dollars. Funding recomputes federal/state tax with actual allocated withdrawal sources. KY/GA's bracket-fill trial path additionally caps the *hypothetical* amount before constructing attribution. Other jurisdictions can still trial an amount above executable capacity, leaving an unowned attributed residual. The existing accepted states' final semantics must stay unchanged.

**Proposal:** reproduce that hypothetical defect independently for OK/MS in focused tests before any shared edit, then add only the necessary new-state condition to the established capacity-complete path. OK needs exact owners to apply pools. MS still needs source qualification/owner evidence even when its supported conversions are completely exempt; an unowned scalar is insufficient. Do not invent a source owner or globally alter the 17 existing-state paths.

Every funding trial must recalculate ordinary income, actual source withdrawals, conversion evidence, SS/federal AGI, retirement exclusion/qualification, senior exemptions and actual-year state tax. Oklahoma cap exhaustion and the senior AGI cutoff can change effective marginal behavior; Mississippi can have exempt traditional funding, taxable early funding and mixed-source transitions. Cash/brokerage principal funding normally adds no taxable income; it is not equivalent to traditional funding.

Section 16 supplies closed-form **test oracles only**, within stated linear segments. The production solver remains iterative. Test withdrawal/source exhaustion, cap crossings and age-exemption discontinuities explicitly. A genuine common-plan nonconvergence or cash discrepancy is a state's release blocker, not permission to replace the solver with conversion × marginal rate. Lifetime state tax is the sum of actual-year results, with survivor status and rule changes applied annually.

## 14. Brokerage B3 and special investment policy

Preserve Arizona B3/NC/KY/GA/VA behavior: admit ordinary gross brokerage spending with unknown taxable portion; principal is cash, not AGI. Keep federal modeled investment income once; source evidence describes those dollars rather than adding income. Do not manufacture gain, treat unknown gain as legally zero, or apply a special-state gain rule to generic brokerage proceeds.

Use state-local normalization patterned on GA: reconcile the exact aggregate placeholder with owned explicit records; reject duplicates, excess amounts, wrong year, incompatible account/source facts and overlapping known gain/withdrawal attribution describing the same dollars twice. Missing basis alone is not one of these contradictions. Tests must exercise actual `TaxIncomeCalculator`/attachment behavior, not just synthetic normalized records.

Oklahoma has a deduction for certain qualifying Oklahoma property/business-security gains with holding/situs conditions; Mississippi exempts certain qualifying domestic-company/financial-institution gains under its specific rules. Neither is a general exclusion for a retiree's brokerage gains. Federal-obligation interest and state/nonstate municipal interest also require source facts. Initially retain ordinary modeled taxable investment income; exclude specialized adjustments from advertised scope and disclose possible overstatement, alongside possible understatement from unmodeled realized gains. Known explicit special classification cannot silently be recast as ordinary. [OAC 710:50-15-48 and related interest rules](https://oklahoma.gov/content/dam/ok/en/tax/documents/resources/rules-and-policies/agency-rules/2025/Chapter_50-Income_Tax-2025.pdf), [Mississippi DOR capital-gain guidance](https://www.dor.ms.gov/individual/individual-income-tax-frequently-asked-questions).

## 15. Minimal implementation architecture and scenario admission

Create independent `OklahomaProductionTaxCalculator` and `MississippiProductionTaxCalculator` implementing the existing interface, with `oklahoma/OklahomaTaxRules` and `mississippi/MississippiTaxRules` immutable, year-aware and independently versioned. Add small state-local recipient/eligibility/reconciliation helpers; MS additionally needs the small married-column worksheet. No generic tax-law hierarchy or attribution framework is justified.

Proposed ordinary state formulas:

- OK: modeled AGI − included SS − qualifying recipient retirement adjustments + admitted state additions/subtractions − state standard deduction − eligible filer/senior exemptions; floor final base, apply year-specific bands.
- MS: reconcile ordinary federal income to state net income by removing included SS, independently qualifying retirement payments and clause-ff conversion amounts; reconcile recipient ownership for the selected joint/combined method, then state deductions/exemptions and per-column zero bands. Never subtract the same dollar under both general retirement and conversion provisions.

| Common scenario | Oklahoma proposal | Mississippi proposal |
|---|---|---|
| SS only | A: zero calculated tax | A: zero calculated tax |
| Older own-IRA/RMD retirement | A/B: individual pool | A/B: qualifying income exempt |
| Own IRA + completed IRA-to-Roth conversion | A with Section 6 interpretation | A under separate clause ff; normal cash separately qualified |
| Private qualified pension / survivor pension | B ordinary-plan assumption, current-recipient cap | B ordinary qualification, survivor extension |
| Taxable commercial nonqualified annuity | A/B taxable portion, no inferred retirement pool | C for unverified retirement qualification; isolate affected subset pending clarification/approval |
| Already-qualified retirement annuity | A/B pool | A/B exemption |
| Ordinary brokerage spending, no gains modeled | B: admitted zero additional investment income, gains limitation | B: same |
| Known ordinary taxable investment income | A, normal base | A Single/owned combined; B unknown-owner joint fallback |
| Qualified Roth cash | A existing federal treatment | A existing federal treatment |
| Clearly early own-IRA cash | A state pool where qualified source; federal limitation retained | A state-taxable; federal penalty/exception coverage not invented |
| Cash receipt interval straddles 59½ | No new ordinary age test | C without timing evidence; not a block on clause-ff conversion itself |
| Explicit government/service/disability/inherited exception | C/D unless exact classification admitted | C/D unless exact qualifying facts admitted |
| Contradictory/excessive attribution | C, structured unsupported | C, structured unsupported |
| MFJ death year → later Single | A/B annual current-recipient rules | A/B status/qualification and column changes |

Required plumbing after independent gates: enum/router, necessary conditional/annuity admission entries, shared disclosure/report hooks and independent freshness keys. No state financial arithmetic in `ProjectionEngine` or JavaFX. Preserve existing NIIT, basis, Advanced-mode, taxable-receipt and growth/source safeguards. Adding a state to an allowlist does not prove every conditional receipt is retirement income.

## 16. Independent numeric golden fixtures and funding oracles

These are **new audit worksheets, not executed application tests**. Calculations use the statutes/official return structure above and decimal arithmetic independent of production calculators. Monetary examples are calendar 2026, full-year resident, standard-deduction, nondependent filers unless otherwise stated. No unlisted credits, wages, SS or incremental funding withdrawals. “Investment” means already modeled, ordinary federally taxable income, not gross brokerage proceeds. IRA/pension qualification is an explicit fixture premise; rows marked conditional or unsupported are not evidence that the real application can establish it.

### 16.1 Oklahoma direct worksheets

For under-65 Single: `T = max(AGI − taxableSS − admittedRetirementExclusion − 6,350 − 1,000, 0)`. For ordinary MFJ substitute deduction $12,700 and two $1,000 filer exemptions. Apply Section 3's continuous statutory bands, with only final cents rounding.

| ID | Inputs / independent worksheet | Expected planning state tax |
|---|---|---:|
| OK-01 | Single direct taxable bases T = 0; 3,750; 3,751 | $0.00; $0.00; $0.03 |
| OK-02 | Single T = 4,900; 4,901; 7,200; 7,201 | $28.75; $28.79; $109.25; $109.30 |
| OK-03 | MFJ T = 7,500; 7,501; 9,800; 9,801 | $0.00; $0.03; $57.50; $57.54 |
| OK-04 | MFJ T = 14,400; 14,401 | $218.50; $218.55 |
| OK-05 | Single investment $11,100; $11,101 → T 3,750; 3,751 | $0.00; $0.03 |
| OK-06 | MFJ investment $22,200; $22,201 → T 7,500; 7,501 | $0.00; $0.03 |
| OK-07 | Single SS only, even if federal result includes taxable SS | $0.00 |
| OK-08 | Single IRA $50,000 → exclusion 10,000; T 32,650; 109.25 + .045 × 25,450 | **$1,254.50** |
| OK-09 | OK-08 plus gross SS $30,000; federally taxable SS $25,500, AGI $75,500 → subtract that SS once, same T | **$1,254.50** |
| OK-10 | Single investment $20,000 plus IRA $9,000; $10,000; $15,000 → T 12,650; 12,650; 17,650 | $354.50; $354.50; $579.50 |
| OK-11 | Single IRA $50,000 plus completed IRA conversion $20,000 → one exclusion 10,000; T 52,650 | **$2,154.50** |
| OK-12 | Single RMD $30,000 plus IRA conversion $20,000 → same owner's pool once; T 32,650 | $1,254.50 |
| OK-13 | Single qualified private pension $30,000 plus IRA $20,000 → one cap; T 32,650 | $1,254.50 |
| OK-14 | MFJ IRA $50,000 each owner → exclusions 20,000; T 65,300 | **$2,509.00** |
| OK-15 | MFJ IRA $100,000 all one owner → exclusion only 10,000; T 75,300 | **$2,959.00** |
| OK-16 | MFJ pension $50,000 PRIMARY, IRA conversion $50,000 SPOUSE, ages 60/75 → two caps, not age-limited; T 65,300 | $2,509.00 |
| OK-17 | Single age 65 investment $14,000 → senior extra 1,000; T 5,650. Age 64 same income → T 6,650 | $55.00 / $90.00 |
| OK-18 | Single age 65 investment $15,000; $15,000.01 → senior exemption 1,000; 0; T 6,650; 7,650.01 | $90.00; $129.50 |
| OK-19 | Single age 65 investment $14,000 + IRA conversion $20,000. Senior test 34,000 − 20,000 = 14,000; retirement pool 10,000; T 15,650 | **$489.50** |
| OK-20 | Senior DOB Jan 1, 1962 versus Jan 2, 1962, tax year 2026; investment $14,000 | $55.00 / $90.00 |
| OK-21 | Admitted nonqualified commercial annuity gross $50,000, federally taxable portion $20,000, no qualified-plan pool → T 12,650 | $354.50 |
| OK-22 | Verified qualifying retirement annuity, included taxable portion $20,000 → pool 10,000, T 2,650; nonincluded principal never subtracted again | $0.00 |
| OK-23 | Brokerage proceeds $100,000, no modeled taxable income | $0.00 plus gains limitation |
| OK-24 | Brokerage proceeds $100,000 and modeled ordinary investment $50,000, explicit gain describes that same $50,000 → T 42,650 | **$1,704.50**, not tax on $150,000 |
| OK-25 | Qualified Roth cash $50,000, no other modeled income | $0.00 |
| OK-26 | Qualified private pension $100,000 to survivor: death-year MFJ, then later Single; high AGI denies extra senior exemption | $2,959.00 then $3,504.50 under unchanged reviewed bands |
| OK-27 | Five ordinary IRA-$50,000 years 2026–2030, no certified cut incorporated, no added funding | Annual $1,254.50; total **$6,272.50** |
| OK-28 | Explicit duplicate/excess/wrong-year attribution, or unowned positive recipient-dependent retirement residual | `UNSUPPORTED`, no numeric tax |

Fixture OK-09 uses the independently computed federal taxable-SS amount for that precise income shape; alternative funding withdrawals require a new federal result. OK-26 is a filing-status worksheet, not a claim that all deceased pensions automatically continue unchanged; the scenario must actually establish the survivor's $100,000 receipt. Test both death directions and a death before the deceased's qualifying birthday.

**Government coordination legal worksheets, not initial advertised production classifications:**

| ID | Exact legal premise | Independently computed outcome / production boundary |
|---|---|---|
| OK-G1 | Single A5 government pension $8,000 + qualified IRA $8,000 + investment $20,000 | A5 8,000 plus A6 2,000, total exclusion 10,000; T 18,650; **$624.50**. Never exclude both 8,000 receipts in full. |
| OK-G2 | Single fully qualifying A3 CSRS $40,000 + IRA $20,000 + investment $20,000 | A3 40,000 plus ordinary A6 10,000; T 22,650; **$804.50**. No second deduction of the CSRS dollars. |
| OK-G3 | Same amounts as G2, but government pension only qualifies under capped A5 | Total A5/A6 exclusion 10,000; T 62,650; **$2,604.50**. Classification is financially material. |
| OK-G4 | Generic pension named “Federal” without reliable special facts; versus explicit reliable CSRS/service classification | Name alone does not prove G2. Under approved ordinary assumption use ordinary pool with disclosure; represented unsupported special classification must refuse, not silently use G3. |

**Future policy test:** retain 2026 bands for all untriggered future years. A synthetic rules-component test may apply one legally assumed certified step to each positive band and check continuity/recomputed cumulative amounts, but must be labeled hypothetical; no actual 2028 reduced-rate fixture is authorized by this audit. Do not hard-code a future elimination year.

### 16.2 Mississippi direct worksheets

Single under 65: `tax = r × max(stateNet − 2,300 − 6,000 − 10,000, 0)`. Eligible senior subtracts another 1,500. Qualified retirement/conversion removal occurs before state deductions. MFJ examples explicitly state the column method.

| ID | Inputs / independent worksheet | Expected planning state tax |
|---|---|---:|
| MS-01 | Single ordinary investment $18,300; $18,301 | $0.00; $0.04 |
| MS-02 | Single investment $18,300.12; $18,300.13 → unrounded tax .0048; .0052 | $0.00; $0.01 |
| MS-03 | Single investment $50,000 → positive taxed base 31,700 | **2026 $1,268.00** |
| MS-04 | Same under-65 income in 2027/2028/2029/2030 | **$1,188.75 / $1,109.50 / $1,030.25 / $951.00** |
| MS-05 | Same in 2031, no later certified/incorporated reduction | **$951.00**, disclosed hold |
| MS-06 | MS-03 shape each year 2026–2030 | Sum **$5,547.50**; no rate fixed at opening year |
| MS-07 | Single age 65 investment $50,000 → 50,000 − 2,300 − 6,000 − 1,500 − 10,000 = 30,200 | **$1,208.00** |
| MS-08 | MFJ one owner investment $100,000, both under 65; one column → 100,000 − 16,600 − 10,000 = 73,400 | **$2,936.00** |
| MS-09 | MFJ investment $50,000 each, both under 65; combined → 40,000 + 40,000 − 16,600 = 63,400 | **$2,536.00** |
| MS-10 | MFJ owners $90,000/$10,000, under 65 → 80,000 + 0 − 16,600 = 63,400 | $2,536.00; flexible deductions placed where useful |
| MS-11 | MFJ owners $100,000/$0, under 65 → 90,000 + 0 − 16,600 | $2,936.00; cannot invent second zero band |
| MS-12 | MS-09 with one / two qualifying senior filers; X = 18,100 / 19,600 | $2,476.00 / **$2,416.00** |
| MS-13 | MFJ investment aggregate $100,000, owners unknown, both seniors: proposed one-column fallback → 100,000 − 19,600 − 10,000 | **$2,816.00**, explicit potential overstatement |
| MS-14 | SS only, or qualifying own IRA $50,000 plus SS, including federal taxable SS | $0.00 |
| MS-15 | Own IRA cash $50,000, all receipts after ordinary normal qualification (age 60 entire year) | $0.00 |
| MS-16 | Own IRA cash $50,000, age 50 entire year, clearly early and unqualified | **$1,268.00 state tax**; federal additional tax not addressed by this state worksheet |
| MS-17 | Same owner age 50, **completed IRA-to-Roth conversion** $50,000, no retained cash | **$0.00 under clause ff**, distinct from MS-16 |
| MS-18 | Age 50: early unqualified IRA cash $30,000 + completed IRA conversion $20,000 | State net 30,000; **$468.00**; conversion exemption does not shelter retained cash |
| MS-19 | RMD $30,000 + own IRA conversion $20,000, ordinary age 75, no other income | $0.00, no double subtraction |
| MS-20 | Qualified private pension $50,000 + ordinary investment $50,000, age 60 | $1,268.00; pension exempt, investment not retirement |
| MS-21 | Verified qualifying retirement annuity $40,000 gross, included taxable portion $20,000 + investment $30,000, age 60 | $468.00; remove only included annuity 20,000 |
| MS-22 | Verified qualifying fully taxable retirement annuity $50,000 + investment $30,000, age 60 | $468.00; no ordinary dollar exclusion cap |
| MS-23 | Commercial nonqualified annuity taxable portion $20,000 without reliable retirement-plan qualification | **Unresolved qualification / unsupported affected source**. If independently established taxable investment receipt, conditional worksheet $68.00; never an unconditional exemption golden. |
| MS-24 | Own IRA cash $50,000 in year crossing 59½, timing unavailable | `UNSUPPORTED`. Proved all-qualified receipts → $0.00; proved all-early unqualified receipts → $1,268.00. |
| MS-25 | Ordinary IRA conversion $50,000 during that crossing year, properly completed | $0.00 under separate ff, without deeming every cash distribution qualified |
| MS-26 | Brokerage proceeds $100,000 only | $0.00 plus gains limitation |
| MS-27 | Proceeds $100,000 + modeled investment $50,000, explicit gain is same modeled amount, Single under 65 | $1,268.00, not tax on $150,000 |
| MS-28 | Qualified Roth cash only | $0.00 through federal base |
| MS-29 | Qualified primary-retiree survivor pension $50,000 paid to age-50 survivor + investment $50,000, Single | $1,268.00; no invented survivor-age denial of qualified death extension |
| MS-30 | 2027 death-year MFJ, both age 65 already at death/opening, investment $100,000 all survivor-owned → one column; 2028 surviving Single, same investment | **$2,640.00** then **$2,807.00**; rates/deductions/status all recomputed |
| MS-31 | Clearly early cash with unrepresented federal penalty exception; unsupported employer conversion; incompatible inherited/basis facts | Structured unsupported where qualification cannot be established; no guessed zero |
| MS-32 | Wrong year, duplicate/excess attribution or contradictory ownership | Structured unsupported, not conservative joint fallback |

For MS-09 a direct official-column allocation also proves the result: allocate 8,300 of D/E to each $50,000 column, yielding 41,700 taxable each, then 31,700 × 4% twice = 2,536. For MS-10 allocate all 16,600 to the $90,000 column, leaving 73,400 taxable there and 10,000 in the other column; tax = 63,400 × 4%. Equal deductions would be a worse choice; equal *income* allocation is prohibited without facts.

Add explicit DOB/death-date and participant-facts boundary rows: normal age already reached at interval start, threshold on first receipt, threshold the following day, retirement conditions met partway through year, January 1 senior birthday pending verified convention, death before birthday and both survivor directions. Age-ambiguous rows assert reasons/no tax result, not an arbitrary numeric expected value. Verify ordinary pension/annuity legal premises with real model evidence before turning a worksheet into advertised source support.

### 16.3 Independent funding and bracket-fill oracles

Use **2026 Single age 60**, no SS/NIIT/Medicare/other deductions, fully taxable ordinary income, federal basic deduction **$16,100**, first **$12,400 taxable at 10%**, then 12% within these examples. Federal constants independently checked against [IRS Revenue Procedure 2025-32 in IRB 2025-45](https://www.irs.gov/irb/2025-45_IRB). This tests compatibility with the existing basic federal model, not new federal calculations or senior deductions. Assume enough same-owner balances/Roth destination capacity. Ordinary spending uses all original receipt cash, so no original cash remains for taxes unless explicitly supplied.

| Oracle | Independent arithmetic | Expected state/funding outcome |
|---|---|---|
| OK-F1: IRA $50,000, sufficient separate cash/brokerage principal for tax | Federal TI 33,900; F = 1,240 + .12 × 21,500 = 3,820; S = 1,254.50 | State **1,254.50**; total tax cash **5,074.50**; no extra taxable traditional withdrawal |
| OK-F2: same, all tax from qualified traditional assets | W = 3,820 + .12W + 1,254.50 + .045W; W = 5,074.50/.835 = **6,077.24550898…** | Final F **4,549.27**, S **1,527.98**. Cent-funded total near **6,077.25**; assert established solver cash tolerance, not more precision than its contract. |
| OK-F3: explicit federal taxable-income target 40,000, starting IRA 15,000 | AGI 56,100; F 1,240 + .12 × 27,600 = 4,552; state T 38,750 → S **1,529** | Cash-funded conversion **41,100**. Traditional-funded conversion **35,019** because C + 4,552 + 1,529 = 41,100. |
| MS-F1: qualifying IRA $50,000, all tax traditional-funded | S stays 0; W = 3,820 + .12W = 3,820/.88 = **4,340.90909091…** | F/W near **4,340.91**; S **0.00**; qualified funding withdrawals stay state-exempt |
| MS-F2: investment $50,000, qualified traditional assets fund taxes | S = 1,268 independent of qualified added W; W = (3,820 + 1,268)/.88 = **5,781.81818182…** | F **4,513.82**, S **1,268.00**; near **5,781.82** funding; principal-funded comparator needs only 5,088 |
| MS-F3: target federal TI 40,000, starting qualified IRA 15,000 | AGI 56,100; F 4,552; S 0 | Cash-funded C **41,100**; qualified-traditional-funded C **36,548** |
| MS-F4: same target, starting qualified IRA 15,000 plus investment 30,000 | Available income increment 11,100; S = .04 × (30,000 − 18,300) = **468**; F 4,552 | Cash-funded C **11,100**; qualified-traditional-funded C **6,080** |

These targets are explicit unit-oracle targets, not a claim that $40,000 is a built-in federal bracket boundary. Bind the target in the test as the accepted GA/VA fixtures do. Actual built-in bracket-fill strategies also need account-backed integration checks.

Additional mathematical acceptance designs:

- OK: solve piecewise on either side of the recipient's remaining $10,000 exclusion and the senior AGI threshold; validate the solution belongs to its assumed segment. Recompute SS/federal phase behavior for separate SS scenarios rather than using the no-SS slopes above.
- MS: contrast funding from qualified IRA, clearly state-taxable source and principal; changing source qualification must change the state trial, not the conversion's statutory identity. Early-distribution federal penalties are outside these linear oracles and cannot be silently ignored in a full-plan acceptance claim.
- Both: requested conversion above valid same-owner capacity, Roth destination absent/wrong owner, one owner exhausted then the other, opening RMD already satisfied versus projected RMD, and conversion shortfall. Check unowned residual refusal before any new-state capacity-path fix, then correct trial execution after it.
- Exact cash equation: income + RMD/withdrawal cash = ordinary spending + Medicare + tax payments + retained cash, within existing cent tolerance. Conversion principal transfers between account classes and is not spending cash. Persisted account balances remain unchanged. Do not require exact separate rounding identities where the current solver admits a one-cent residual.

## 17. Reporting, persistence and bounded integration plan

The existing infrastructure can carry either new state without migration, but explicit hooks must be covered; registry edits alone are insufficient. Paths below are relative to `src/main/java/com/daviddunn/retirementplanner/`.

| Surface | Focused Phase 6E-L work and acceptance |
|---|---|
| `domain/benefit/ConditionalBenefitAdmission.java`, `NonqualifiedAnnuityAdmission.java` | Add independent narrow state admission only for sources whose qualification/taxable amount is settled. Retain basis validation, NIIT, Advanced-mode, growth/source restrictions. Ordinary conditional payments are not automatically retirement income. |
| `ui/views/AssumptionsView.java` | Enum's alphabetical choices plus state-specific help; no qualification screens or tax-entry fields. |
| `ui/views/ResultsSummaryView.java`, `ui/summary/ProjectionYearDetailsPane.java` | Show all material limitation categories in summary, annual and range details; do not truncate to one generic paragraph. |
| `ui/socialsecurity/SocialSecurityAnalyzerInputSummary.java`, `IndividualReportContext.java`, `IntegratedReportContext.java` | Current assumptions shown separately from frozen report-time assumptions; integrated financial runs use annual state calculations. SS-only benefit ranking/calculation unchanged. |
| `ui/montecarlo/MonteCarloRun.java`, `MonteCarloRunService.java`, comparison/report adapters | Capture state/version/limitations at run time, including both comparison plans; economic paths change income/funding, not legal rules. |
| `app/export/ProjectionCsvExporter.java`, `ProjectionPdfExporter.java`, `ProjectionPdfReport.java`, MC/integrated exporters | Single/MFJ state amounts, full limitations and unsupported reasons; normal and retained exports must use result-time tax and notes. Do not reconstruct old assumptions from current editor state. |
| `persistence/JsonRetirementPlanRepository.java`, `domain/baseline/RetirementPlanSnapshot.java`, baseline/scenario services | Stable enum-code round trips after registration; preserve default/legacy plans. Baseline snapshot currently stores references in parts of the object graph; do not redesign baseline isolation or claim a new immutability guarantee. Test isolated scenario/baseline runs with existing semantics. |
| `app/socialsecurity/results/AnalysisVersions.java`, `AcceptedAnalysisCompletion.java`, `AnalysisCompatibilityPolicy.java` | Add independent OK/MS keys using NC/KY/GA/VA pattern, e.g. `oklahoma-income-tax-rules` and `mississippi-income-tax-rules`. Capture from frozen full-plan inputs. Changed relevant version marks that state's financial analysis outdated; preserve SS-only and all other-state behavior. |
| `app/socialsecurity/results/RetainedProjectionSnapshot.java` | Frozen state record already stores jurisdiction, method/status, amount, limitations and reasons. Preserve old results when rules/editor change; freshness changes interpretation of currency, not stored history. |

Mandatory disclosure categories, state-specific and persisted with results:

1. Full-year resident/Single-MFJ, ordinary fully taxable retirement/qualified-Roth and standard-deduction assumptions; no federal below-AGI deduction substitution.
2. Brokerage proceeds not taxed as principal; unmodeled realized gains can understate tax; specialized security/state-source adjustments can change tax in either direction.
3. Actual enacted year schedule and nominal future-law hold; OK certification policy and MS untriggered post-2030 policy, without fiscal forecasts.
4. OK ordinary per-person cap, private-plan assumption and special government/military/railroad exclusions outside scope; no name-based classification.
5. MS qualifying-retirement conditions, annual timing boundary, distinct IRA-conversion exemption, refused employer/in-plan variants and unverified commercial-annuity/inherited exceptions.
6. MS unknown-owner joint fallback if approved, including possible overstatement versus an accurately attributed combined return.
7. Omitted itemization, dependent/blind provisions and ancillary credits, including OK low-income sales/property relief; statutory-rate cents differ from return tables/whole dollars.

**Small independent integration suite per state, proposed only — not run during this audit:**

- Account-backed Single/MFJ deterministic projections: real brokerage spending, IRA/RMD/conversion mixture, actual/source-specific conversion amounts, cash-funded and traditional-funded tax. Check income reconciliation, no principal taxation, cash equation, lifetime totals and unchanged live balances.
- Different DOB/ownership households and both survivor directions. Include pension recipient transfer, deceased DOB cutoff, opening/current RMD distinction; MS qualified/early/crossing receipts and combined/fallback columns.
- Two controlled economic paths (for example +4% and −2%, no random search), matched to deterministic scenarios under identical returns/inflation/withdrawals. Compare annual/final state taxes and portfolios, not just success counts.
- One small integrated SS baseline/candidate comparison and bounded longevity scenarios crossing annual status. Use the actual common projection boundary; no SS-only engine changes, mortality-weighted search or exhaustive claiming grid.
- Single/MFJ JSON save/reload, baseline/scenario copies, frozen annual/history values after editor changes, both sides of MC comparisons, and normal/retained PDF/CSV text/amount assertions.
- Independent rule-version changes mark only affected full-plan analyses outdated and leave saved taxes/limitations, other-state references and SS-only freshness unchanged.
- Unsupported sources retain reasons and absent tax through service, projection, analyzer and export; never turn an unavailable amount into a numeric zero.

Run focused direct/source tests first during implementation, then the accepted bounded existing-state suite, including MI/IL/PA/IA/AZ/NC/KY/GA/VA goldens and Arizona/Kentucky/Georgia/Virginia brokerage regressions. Preserve all 17 expectations. Exclude expensive performance/capacity-probe/exhaustive search suites. This audit does not inherit a new passing test claim from earlier reports.

## 18. Independent production admission gates and Phase 6E-L plan

### Oklahoma gate

Require current 2026 rate/deduction/exemption verification and subsequent-law review; all direct worksheets; per-person $10,000 reconciliation; senior conversion-adjusted AGI test; ordinary qualified IRA conversion interpretation explicitly accepted; special government classification refusal/disclosure; B3 principal admission; actual capacity/funding/SS/survivor tests; full reporting/history/freshness/persistence; unchanged 17-state goldens. No registration before those pass.

Government eligibility data is not needed to model an explicitly bounded ordinary private-plan subset. Missing comprehensive government qualification is not itself a blanket state blocker. A represented special classification with an unsupported calculation remains a per-plan/source refusal.

### Mississippi gate

Require unconditional 2026–2030 schedule plus post-2030 hold; ordinary state-base reconciliation; deduction/exemption/death/DOB rules; independent general-retirement eligibility versus clause-ff IRA conversion; source-specific supported timing interval; approved ordinary-plan assumption; exact owner-based combined worksheets and approved unknown-owner fallback, or a specifically chosen narrower married policy. Require funding/capacity/brokerage, persistence/history/disclosures and 17-state regressions independently.

**Ordinary own-IRA conversion qualification is supported by an express statute, not a remaining blanket legal unknown.** Production source notes should preserve the complete clause and recheck current codification. Direct employer-to-Roth/in-plan and unverified commercial annuity rules remain specialized legal boundaries; keep those sources unsupported rather than guessing. Ordinary IRA/pension plans can be admitted if the practical qualification policy and tests succeed. If implementation cannot distinguish a common qualifying source/timing path safely, withhold that scope or Mississippi admission; Oklahoma proceeds independently.

### Coordinated workstream

1. Freeze initial status and current 17-state regression fixtures. Refresh law/source versions and confirm approved decisions below.
2. Write independent OK/MS worksheet and real-attachment tests first. Reproduce hypothetical-capacity residuals before touching the narrowly conditional bracket-fill branch.
3. Build Oklahoma rules/calculator and recipient pool/senior test. Build Mississippi rules/calculator, qualification/conversion helper and married-column worksheet separately. Keep both unregistered while incomplete.
4. Exercise common account-backed funding/conversion/SS/survivor paths; fix only authorized new-state plumbing. A newly discovered existing-state financial defect outside scope requires a separate decision, not changed goldens.
5. Complete frozen disclosures, provenance, normal/retained exports and persistence. Execute focused then bounded tests with IntelliJ Maven/project-local repository.
6. Register each state only on its own completed gate, rerun its routed acceptance, document exact files/tests/limitations and stop with the Phase 6E-L report. Potential counts are 18 for either single admission, 19 for both; no dependency on Colorado.

**Estimated complexity:** Oklahoma moderate (small arithmetic/recipient helper; meaningful existing integration surface). Mississippi moderate to high (qualification/timing plus source-aware married worksheet and more refusal boundaries). Most work is focused validation/integration, not a new architecture. No honest elapsed-time estimate can replace the acceptance gates; both can share a workstream without sharing a tax-law calculator.

## 19. Remaining legal/modeling blockers and risk register

| Issue | Status | Smallest safe disposition |
|---|---|---|
| OK IRA conversion pool | Reasoned application of broad statute/IRS characterization; no conversion-specific OTC ruling found | Approve this narrowly evidenced interpretation or seek a direct OTC clarification before advertising it. Do not claim an agency ruling exists. |
| OK government/civil-service components | Law materially differs; ordinary inputs lack full classification | Private subset with prominent limitation; structured refusal for reliable represented special facts. No qualification engine. |
| MS ordinary IRA → Roth | Express clause ff; current legislative/code corroboration and DOR enactment notice | Preserve/reverify current source text at implementation; do not confuse general early cash rules with conversion clause. |
| Employer/in-plan conversion variants | Not independently established for either advertised scope | Refuse affected variant initially; targeted state-agency interpretation research if later desired. |
| MS commercial nonqualified annuity exemption | Existing basis/maturity data does not establish qualifying retirement system/plan | Restrict affected receipts; ask DOR specifically about privately purchased §72 investment contracts versus employment/IRA retirement annuities. No blanket exemption or guessed taxable classification. |
| MS ordinary cash crossing 59½ / plan retirement date | Annual totals lack intra-year dates | Use evidenced eligible period or precise refusal. No payment-date UI presumed; assess actual ordinary paths in implementation tests. |
| MS plan/service/disability/death qualification | Partly available dated participant/pension facts; not a universal eligibility certificate | Approved ordinary-qualified-plan assumption plus contrary-fact refusal; special exceptions excluded. |
| MS married taxable ownership | Known for account/pension sources, often absent for investment aggregate | Exact combined formula where evidence suffices; approved conservative actual joint method otherwise. No equal split. |
| MS senior Jan 1 birthday | Reviewed instructions do not expressly settle day-before convention | Resolve narrowly with current authoritative guidance; specific boundary refusal if not established. Midyear DOBs remain supported. |
| MS conditional reduction scale | Enrolled text's unusual percentages require care | Hold 3% after 2030; authoritative interpretation/update required before incorporating a triggered cut. Not a present scheduled-rate blocker. |
| OK post-2026 triggers | No final certification yet under first Dec2026/Feb2027 sequence as of review | Hold reviewed schedule; never promise zero tax by a future date. |
| Bracket-fill hypothetical residual | Known KY/GA-specific remedy, no OK/MS trials implemented | Reproduce first, narrowly extend only new-state trial path as needed. |
| Federal penalties/nonqualified basis | Not solved by state exemption work | Retain existing federal/source admission safeguards; no extra penalty/basis engine. State-only early-income worksheet is not a full-plan federal accuracy claim. |
| Funding/integration tests | Not run; no new calculators exist | Actual ordinary-case convergence, source reconciliation, history/export/freshness and regression success are mandatory before admission. |

There is **no present need for a broad input/model change** for ordinary older own-IRA retirees, completed IRA conversions, normal qualified pension assumptions or B3 brokerage spending. Narrow state-local eligibility/column/reconciliation helpers and conditional capacity-path plumbing are required. Supporting every midyear cash timing exception, pension classification, inherited or commercial-annuity qualification would require facts outside the bounded scope; do not add those features by implication.

## 20. Exact approval decisions and audit verification

Phase 6E-L must be explicitly authorized. Recommended decisions to approve or revise:

1. **Rules:** OK 2026 continuous statutory bands; MS 2026–2030 enacted rates; nominal deductions/exemptions and latest-reviewed-rate holds for untriggered future years. No inflation/revenue forecast of legal steps.
2. **OK ordinary subset:** $10,000 recipient pool, no general age gate, ordinary private-qualified-plan assumption, A5 coordination; special government/military/railroad calculations excluded with represented-fact refusal.
3. **OK conversion:** accept Section 6's traditional IRA conversion interpretation and the distinct senior-AGI conversion removal. Employer/in-plan variants require separate verification/admission.
4. **MS conversion:** implement express clause ff for completed traditional IRA-to-Roth IRA conversions, independent of ordinary cash age qualification; retained tax-funding cash separately tested. Do not exempt unverified employer variants.
5. **MS qualification:** ordinary own-IRA eligible-period and ordinary qualified pension/employer-plan assumption using existing facts; ambiguous 59½/retirement-date crossings and unverified special exceptions refused. No new timing or pension wizard.
6. **MS married policy:** exact standard-subset combined worksheet when taxable ownership is fully reliable; disclosed one-column joint fallback for unallocated ordinary investment income. This approximation may overstate tax. Choose a narrower one-column-only policy explicitly if preferred; never automatic two-band household splitting.
7. **Annuities:** OK established nonqualified investment receipts taxed on modeled taxable portion without invented retirement exclusion; qualifying plan annuities treated separately. MS unverified commercial-annuity qualification remains unsupported pending targeted clarification; existing basis/NIIT/Advanced guards retained.
8. **Approximations/omissions:** B3 unknown-gains policy; cents/statutory-rate planning versus tax-return rounding/tables; standard deduction only; omit ancillary credits, itemization, dependent/blind and specialized security/service adjustments with prominent frozen disclosures. Include common supported senior exemptions.
9. **Technical bounds:** independent rules/calculators/helpers and version keys; new-state-only capacity-complete trial conditions if reproduced/necessary; no federal, existing-state, schema or general architecture change.
10. **Release:** one coordinated workstream with independent gate/registration/test results. An unresolved Mississippi source does not block Oklahoma; an Oklahoma qualification question does not block Mississippi. No Colorado/other-state work.

**Audit verification:** initial Git status was clean; no production Java, test, resource, registry or persistence changes were made. No Maven, regression, Monte Carlo or analyzer suites were run. Read-only source inspection, authoritative web research and independent decimal worksheet arithmetic were performed. Final `git status --short` lists only `?? PHASE-6E-K-OKLAHOMA-MISSISSIPPI-TAX-AUDIT.md`; `git diff --check` passes. Because this file is untracked, its trailing whitespace was also checked separately: zero findings. All 21 numbered sections and explicitly named local Java paths were checked. No staging, commit, push or tag.

## 21. Authoritative sources, governing dates and verification record

All sources below were reviewed **2026-11-17**. Agency summaries/FAQs do not override statutes. Publication year is stated so that 2025 return mechanics are not mislabeled as final 2026 instructions.

| ID | Source and governing date | Use / evidence limits |
|---|---|---|
| O1 | [Oklahoma enrolled HB 2764 (2025)](https://www.oklegislature.gov/cf_pdf/2025-26%20ENR/hB/HB2764%20ENR.PDF); [official bill status](https://www.oklegislature.gov/BillInfo.aspx?Bill=hb2764&Session=2500) | Approved May 28, 2025; 2026 §2355 bands and conditional reduction framework. |
| O2 | [Oklahoma enrolled HB 4072 (2026)](https://www.oklegislature.gov/cf_pdf/2025-26%20ENR/hB/HB4072%20ENR.PDF); [Governor's signed-law notice](https://oklahoma.gov/governor/newsroom/newsroom/2026/governor-stitt-signs-bill-to-accelerate-path-to-zero-income-tax-secure-oklahomas-historic-savings.html) | Signed April 23, 2026; effective August 13, 2026. Endowment and amended certification/apportionment; not an unconditional dated tax repeal. |
| O3 | [OTC 2026 tax legislation summary](https://oklahoma.gov/content/dam/ok/en/tax/documents/resources/publications/legislation/2026LegislativeUpdate.pdf) | Subsequent ordinary-rule/credit review; HB 4432 itemized wagering change effective 2027. Summary expressly nonexhaustive. |
| O4 | [Official Oklahoma statute publisher, §2358](https://govt.westlaw.com/okjc/Document/ND6B5D5114F1E11F19208D31E62F66B8C?viewType=FullText) | Current indexed text includes May 6, 2026 amendment and points to an alternate 2026 amendment. Direct full-page access was restricted; indexed clause text was checked against OTC current instructions/rules. Do not rely on an old 1999 statute copy or a proposed pension bill. |
| O5 | [OTC Chapter 50 rules, 2025 compilation](https://oklahoma.gov/content/dam/ok/en/tax/documents/resources/rules-and-policies/agency-rules/2025/Chapter_50-Income_Tax-2025.pdf) | 710:50-15-30 exemption rules, -48 qualifying gain deduction, -49 retirement, related interest rules. Older citations/typographical references do not displace current statute/2026 guidance. |
| O6 | [OTC 2026 Form 511 packet, software-developer publication](https://oklahoma.gov/content/dam/ok/en/tax/documents/forms/software-developers/Dev511-Pkt-26.pdf); [2026 Form 511 grid](https://oklahoma.gov/content/dam/ok/en/tax/documents/forms/software-developers/Dev511-26Grid.pdf) | 2026 guidance on A3/A5/A6, SS, senior conversion adjustment, birthday rule and return/table mechanics. Developer-path publication, not falsely described as final general-public 2026 filing release. |
| O7 | [OTC 2026 MeF guide](https://oklahoma.gov/content/dam/ok/en/tax/documents/resources/publications/software-developers/2026MeFDeveloperGuide.pdf); [OTC 2026 annual report](https://oklahoma.gov/content/dam/ok/en/tax/documents/resources/reports/annual-reports/otc/AR-2026.pdf) | Independently corroborate 2026 deductions/exemptions/rate structure and federal-AGI starting point. |
| O8 | [OTC income-tax help](https://oklahoma.gov/tax/helpcenter/income-tax.html); [current public Form 511 packet](https://oklahoma.gov/content/dam/ok/en/tax/documents/forms/individuals/current/511-Pkt.pdf) | Retirement/government coordination and credits; current public packet was 2025 during review. Some other state benefit pages retain obsolete military percentages; not relied upon. |
| M1 | [Mississippi enrolled HB 1 (2025), §§1–2](https://billstatus.ls.state.ms.us/documents/2025/pdf/HB/0001-0099/HB0001SG.pdf); [DOR 2025 enacted legislation summary](https://www.dor.ms.gov/forms-resources/laws-regulations/2025-legislation) | Enacted 2025 Build Up Mississippi Act; 2026 retained 4%, 2027–2030 fixed steps, conditional 2031+ mechanism. Literal trigger scale reported, not silently corrected. |
| M2 | [DOR 2026 legislation](https://www.dor.ms.gov/forms-resources/laws-regulations/2026-legislation) | Subsequent enactment review and official links to Code/Administrative Code. No identified ordinary schedule replacement or conversion repeal. |
| M3 | [Mississippi SOS Title 35 administrative code](https://www.sos.ms.gov/adminsearch/ACCode/00000158c.pdf) | Part III, Subpart 2, Chapter 07, ¶100–105 for general retirement, early payments, Roth distributions and death extension. IRA rollover deduction provisions elsewhere do not replace specific statutory conversion exclusion. |
| M4 | [DOR general individual income information](https://www.dor.ms.gov/general-information) | Dated 2026/2027 rate table, exemption/deduction amounts and columns. Old 2025 prose coexists on the page. |
| M5 | [DOR individual FAQ](https://www.dor.ms.gov/individual/individual-income-tax-frequently-asked-questions) | Ordinary retirement-condition and specialized gain guidance; explicit informal-guidance disclaimer. Its 4.4% rate prose is stale for 2026. |
| M6 | [2025 DOR Form 80-100 instructions](https://www.dor.ms.gov/sites/default/files/tax-forms/individual/80100251%202.pdf); [2025 Form 80-105](https://www.dor.ms.gov/sites/default/files/tax-forms/individual/80105258%201.pdf) | Actual joint/combined column mechanics, exemptions/age/death, category-built state base and early-pension instructions. Rates updated separately by enacted law, not copied from 2025 examples. |
| M7 | [DOR-authored 2011 Form 80-100, NBER archive](https://taxsim.nber.org/historical_state_tax_forms/MS/2011/indiv_8010011i.pdf) | Primary agency publication: enactment notice for HB 1673 conversion exclusion, effective January 1, 2010. Archive host is not the author; old rates elsewhere in it are not used. |
| M8 | [Official enrolled 2022 HB 1529](https://billstatus.ls.state.ms.us/documents/2022/html/HB/1500-1599/HB1529SG.htm); [official 2026 HB 1072 House-passed text](https://billstatus.ls.state.ms.us/documents/2026/html/HB/1000-1099/HB1072PS.htm) | Retained §27-7-15(4)(ff) text. First intermittently accessible via search index rather than direct fetch; second directly read, used for existing-law corroboration only, not proof that its proposed additions passed. |
| M9 | [Current codified §27-7-15, secondary reproduction](https://law.justia.com/codes/mississippi/title-27/chapter-7/article-1/section-27-7-15/); [2010 HB 1673 enrolled-text/status mirror](https://legiscan.com/MS/text/HB1673/id/370406) | Secondary corroboration of current clause and March 24, 2010 approval. Substantive conclusion also has the primary DOR enactment notice and official legislative recitations above. Direct older official history XML/SG endpoints were unavailable to this research tool; do not claim they were fully retrieved. |
| F1 | [IRS Publication 590-A](https://www.irs.gov/publications/p590a); [IRS traditional/Roth IRA guidance](https://www.irs.gov/retirement-plans/traditional-and-roth-iras) | Federal characterization of conversion/source distribution, basis and completed-rollover/retained-cash distinction; not an independent state eligibility ruling. |
| F2 | [IRS IRB 2025-45 / Revenue Procedure 2025-32](https://www.irs.gov/irb/2025-45_IRB) | 2026 basic deduction/brackets used only in independently derived no-senior funding examples. No federal code or federal expected values changed. |

Implementation must refresh the current statutes/agency materials and retain source notes when rules are locked. Source-access limits are disclosed; they do not justify replacing express statutory IRA-conversion language with an unsupported blanket early-distribution rule. Conversely, the conversion clause does not establish every employer rollover, inherited exception or commercial-annuity qualification.

**Completion boundary:** audit report only. Oklahoma/Mississippi remain unregistered; Colorado remains disabled. Await explicit Phase 6E-L approval and decisions in Section 20.
