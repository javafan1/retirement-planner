# Aledrix Retirement Planning — Phase 6E-I
# Combined Georgia and Virginia State Income Tax Audit

Verification date: **2026-11-16**. **Audit and documentation only.** Neither state is implemented, registered or admitted. Phase 6E-J requires explicit authorization after review of the decisions in §17.

Labels: **Law** = verified enacted law or official guidance; **Code** = current source behavior; **Proposal** = recommended future implementation; **Approximation** = proposed disclosed planning convention requiring approval; **Open** = remaining verification/admission requirement. Acceptance of earlier Arizona/NC/KY policies does not automatically approve new GA/VA approximations.

## 1. Executive summary

**Both states fit the existing annual state-tax boundary.** Recommend one small calculator and immutable versioned rules component for each, with independent release gates. No general tax engine, tax-lot system, pension qualification screen, persistence migration, federal change or Colorado-style federal taxable-income bridge is needed for the ordinary subset.

**Georgia: moderate complexity.** Verified 2026 rate **4.99%**, standard deduction **$15,000 Single/$30,000 MFJ**, individual retirement exclusions **$35,000 at 62–64 / $65,000 at 65+**, with the latter increasing unconditionally to **$70,000 from 2027**. Subsequent rate cuts and standard-deduction increases are conditional. GA's broad exclusion includes eligible investment income, making ownership/reconciliation the central technical issue. Normal account, pension and annuity recipients are already represented. Recommend admitting unallocated ordinary MFJ investment income with a disclosed conservative **no-exclusion treatment for that remainder**, rather than inventing owners or stopping all projections.

**Virginia: moderate complexity, potentially lower than GA if the spouse adjustment is deferred.** Verified graduated rates **2%/3%/5%/5.75%**, state deduction schedule through 2029 and statutory **$3,000/$6,000 reversion from 2030**. Include ordinary **$930 filer exemptions, $800 age-65 exemptions**, the applicable **up-to-$12,000 age deduction** and the no-tax filing threshold. A birth-date guard alone is insufficient: distinguish the pre-1939 grandfathered cohort from the later income-tested cohort and apply the official joint worksheet. VA has no general ordinary-retirement exclusion analogous to GA's pool.

Both start from **federal AGI**, with their own adjustments/deductions. Federal basic, additional age and enhanced senior deductions do not automatically flow into either base. Conversion income enters once; GA can exclude qualified attributed amounts within the recipient's pool, while VA taxes the amount and may additionally reduce an income-tested age deduction.

Important approval choices: GA unallocated-investment policy and conditional-future-law policy; VA literal 2030 sunset and whether to defer the common **up-to-$259 spouse tax adjustment**; bounded source/credit limitations; exact-cent planning rounding; and GA-only capacity-complete bracket-fill trials. No new UI input is necessary for the proposed ordinary subset. A new UI field would be justified only if full allocation/optimization were required instead of the disclosed bounds.

## 2. Source-confirmed production baseline and architecture

Initial `git status --short` was **clean**. Direct inspection of `domain/model/StateTaxJurisdiction.java` and `domain/tax/state/ProductionStateTaxService.java` confirms **15 production jurisdictions**:

| Dedicated calculator/adapter | Existing no-general-individual-income-tax model |
|---|---|
| Michigan, Illinois, Pennsylvania, Iowa, Arizona, North Carolina, Kentucky | Alaska, Florida, Nevada, New Hampshire, South Dakota, Tennessee, Texas, Wyoming |

**GA, VA and CO are absent.** Colorado's `ColoradoTaxRules.productionAdmitted()` is false; its 2026 worksheet and deduction/source helpers have no production route. A research-catalog entry or preparatory class does not register a jurisdiction. The next independently admitted state would be 16; the other would be 17, regardless of development order.

Reviewed accepted reports: [Arizona B3](PHASE-6E-B3-ARIZONA-BROKERAGE-ADMISSION.md), [NC implementation](PHASE-6E-D-NORTH-CAROLINA-IMPLEMENTATION.md), [KY implementation/CO preparation](PHASE-6E-F-KENTUCKY-COLORADO-IMPLEMENTATION.md), and [CO completion/preparation](PHASE-6E-G-COLORADO-COMPLETION.md). Their historical test results are background, not newly executed audit tests.

Paths below are relative to `src/main/java/com/daviddunn/retirementplanner/`.

| Inspected component | Current behavior / reuse |
|---|---|
| `StateTaxAssumptions`, `StateTaxJurisdiction` | One full-year state for the plan, stable codes, shared alphabetical choices; only simplified estimates admitted. No residency timeline or active effective-rate override. |
| `StateIncomeTaxCalculator`, `StateTaxCalculationRequest` | Sufficient annual boundary: actual calendar year/status, household DOB/alive facts, attributed income, federal result, projected rules and state assumptions. |
| `ProductionStateTaxService`, `ProductionStateTaxResult` | Immutable routing; structured unsupported result has no numeric estimate. Funding must not replace unsupported with zero. |
| `TaxHouseholdContext` | Birth dates, household/active owners, distribution date and selected pension facts. Death is modeled January 1; annual joint filing continues through death year, then survivor Single. No disability/service-history eligibility inputs. |
| `TaxIncome`, `TaxIncomeCalculator` | Pension, traditional withdrawals/opening RMDs, conversions, modeled investment bucket, admitted conditional ordinary income and gross SS. Household taxable SS/AGI are calculated federally. Investment bucket is named `taxableInterestIncome`, but accepted policies reconcile other modeled ordinary investment categories to it. |
| `IncomeSourceAttribution`, `TaxIncomeAttribution` | Account type/current owner, actual conversion destination/amount, RMD purpose/year, current pension/survivor recipient, original pension owner and annuity taxable/basis receipt facts. Attaching evidence does not create another income channel; unattributed residuals remain visible. |
| `TaxFundingCalculator` | Reallocates trial withdrawals, rebuilds income/attribution, recomputes federal and selected state tax each iteration, and recalculates the final result. Existing tolerance $0.01, maximum 100 iterations. Cash reduces additional funding. |
| `RothConversionBracketFillCalculator` | Iterative state-aware funding, not one-pass bracket room. KY alone caps hypothetical conversions to executable same-owner capacity; earlier-state path remains aggregate-tolerant. |
| `ProjectionEngine` | RMD first; final conversion capped/executed by same-owner capacity; actual conversion feeds final tax once. Generic state result feeds annual/lifetime metrics. Real account balances are not mutated. Normal projection currently supplies **zero explicit modeled investment income** to bracket fill/final funding; registration will not create dividends or gains from portfolio growth. |
| `KentuckyProductionTaxCalculator`, `KentuckyTaxRules` | Closest GA per-recipient aggregation/unsupported/version pattern. Do not copy KY's $31,110 cap, no-age eligibility or credit. |
| `NorthCarolinaProductionTaxCalculator`, `NorthCarolinaTaxRules` | Closest small AGI calculator, annual immutable rules, B3 source reconciliation, complete frozen disclosure and version-reference template. VA/GA require additional state-specific adjustments. |
| `ArizonaProductionTaxCalculator` | Accepted unknown-basis proceeds admission and investment reconciliation. AZ age/senior deductions are not GA/VA rules. |
| Illinois / Iowa / Pennsylvania / Michigan | IL owner/source checks; Iowa modeled-base coverage/admission discipline; PA qualification/refusal and fractional-age patterns; MI's independent adapter/eligibility calculations. Their substantive exclusions, brokerage restrictions and goldens remain unchanged. |
| Annuity/conditional gates, snapshots, exporters and analysis metadata | Existing hooks cover admission, stored limitations, JSON, baseline/scenario and frozen reports. State-specific presentation/allowlist entries are still explicit. See §14. |

## 3. Verified Georgia rules — 2026 and future years

### 3.1 Rate, deduction and conditional changes

**Law:** [Georgia DOR current updates](https://dor.georgia.gov/taxes/important-tax-updates) confirm 4.99% and $15,000/$30,000 for 2026. [Signed HB 463, §§2-1–2-3 and 5-1](https://gov.georgia.gov/document/2026-signed-legislation/hb-463/download) amends O.C.G.A. §§48-7-20, 48-7-26 and 48-7-27. DOR's [2026 enacted-law summary, HB 463](https://dor.georgia.gov/document/document/2026-legislative-summary/download#page=5) records May 11, 2026 approval and application from January 1, 2026. Do not use 5.19% or older $12,000/$24,000 tables for 2026.

| Provision | Enacted mechanism / proposed planning policy |
|---|---|
| Income-tax rate | 4.99% in 2026. Prospective reductions of **0.125 percentage points** from 2027 toward 3.99%, subject to delay. They are not guaranteed calendar-year rates. |
| Standard deduction | $15,000 Single/$30,000 MFJ. Prospective annual increases $375/$750 from 2027 toward $18,000/$36,000, subject to the linked conditions. |
| Trigger conditions | As of December 1: succeeding-year revenue estimate at least 3% above current estimate; prior fiscal-year net collections higher than each of preceding three fiscal years; reserve exceeds projected revenue decrease from the prospective rate reduction. A failing condition delays the linked step; OPB reports determinations by December 1. |
| Dependents | $5,000 in 2026, prospective $125 steps toward $6,000 linked to delay rules. Dependents are outside the recommended nondependent-filer subset; do not grant this amount to the filer/spouse. |
| Long-range proposal | Hold the latest **reviewed, legally activated** rate and state standard deduction nominally until a reviewed update establishes another step. Currently use 4.99%/$15,000/$30,000 for an untriggered future assumption, with disclosure. No revenue forecast or generic plan inflation activates a step. |

No legally activated 2027 conditional step was established on **November 16**, before the statutory December 1 determination point. Recheck official OPB/DOR determinations immediately before implementation/release; an already-established step must be incorporated rather than ignored. This audit did not establish a subsequent amendment changing the ordinary rules. The [Governor's enactment announcement](https://gov.georgia.gov/press-releases/2026-05-11/gov-kemp-signs-legislation-lowering-taxes-and-supporting-economic-growth) corroborates the new law, but implementation must follow final text rather than earlier bill versions proposing different reductions.

### 3.2 Base, conformity, SS and filing

**Law:** O.C.G.A. §48-7-27(a) starts from **federal AGI**. See the legislature-hosted [2025 Code supplement, §48-7-27](https://www.legis.ga.gov/api/document/docs/default-source/joint-features-document-library/t48-ch7-8-%28v37%29-pdf.pdf), read with HB 463's amendments. The supplement's earlier deduction/rate amounts are superseded; its unchanged eligibility/source provisions remain useful. [Form IT-511, Form 500 instructions](https://dor.georgia.gov/document/document/2025-it-511-individual-income-tax-booklet/download#page=16) expressly distinguishes AGI from federal taxable income.

[Signed HB 1199 §1](https://gov.georgia.gov/document/2026-signed-legislation/hb-1199/download) updates the IRC reference to January 1, 2026 for years beginning from 2025, with specified exceptions. The [DOR 2026 summary, HB 1199](https://dor.georgia.gov/document/document/2026-legislative-summary/download#page=8) corroborates conformity exceptions including business provisions and federal tips/overtime/car-loan/nonitemizer deductions. None makes a general federal below-AGI deduction an automatic GA subtraction. The older generic federal-changes page is not sufficient evidence of the 2026 conformity date.

Federally included SS is subtracted separately under §48-7-27(a)(7); it **does not consume retirement exclusion**. The [DOR retiree FAQ](https://dor.georgia.gov/retirees-faq) also confirms SS/Railroad exclusion, but its $4,000 earned-income statement is stale: current statute/IT-511 use $5,000. [Rule 560-7-4-.02](https://rules.sos.ga.gov/gac/560-7-4) retains older earned-income examples; do not let those override the amended statute.

Proposal: ordinary full-year domestic Single/MFJ standard-deduction filers, using the annual modeled status through death year and Single thereafter. No separate-return optimizer, itemization or dependent fields. Apply verified state additions/subtractions before the exclusion; exempt Treasury/special municipal interest must not receive a duplicate retirement exclusion. Known special source facts require an actual adjustment or refusal. Do not add a former/federal age-based standard deduction to the new GA standard amount without an applicable current GA rule.

GA return instructions round to whole dollars. **Proposal:** preserve exact `BigDecimal` intermediates, floor final taxable income at zero and round final planning tax HALF_UP to cents, disclosed as a planning convention. Refund credits, loss carryovers, earned-income/business/foreign adjustments and itemization remain excluded, with possible tax overstatement disclosed.

## 4. Verified Virginia rules — 2026 and future years

### 4.1 Tax base, rates and deductions

**Law:** [§58.1-322](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-322/) starts from federal **AGI**, modified by state law. [§58.1-301](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-301/) now uses December 31, 2025 fixed conformity, with specified extender/decoupling provisions. Virginia's [2026 legislative summary](https://www.tax.virginia.gov/sites/default/files/inline-files/2026-legislative-summary.pdf) identifies HB 29 Chapter 7 and HB 30 Special Session I Chapter 1, including the new standard-deduction schedule. No Colorado taxable-income bridge is indicated.

[§58.1-320](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-320/) provides the same nominal bands for Single and joint returns:

| Virginia taxable income T | Exact planning rate formula |
|---|---|
| 0–$3,000 | .02 × T |
| Above $3,000 through $5,000 | $60 + .03 × (T − $3,000) |
| Above $5,000 through $17,000 | $120 + .05 × (T − $5,000) |
| Above $17,000 | $720 + .0575 × (T − $17,000) |

[§58.1-322.03(1)(b)](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-322.03/) and the 2026 summary establish:

| Tax year | Single standard deduction | MFJ standard deduction |
|---|---:|---:|
| 2026 | $8,750 | $17,500 |
| 2027 | $9,200 | $18,400 |
| 2028–2029 | $9,300 | $18,600 |
| **2030 onward under current enacted law** | **$3,000** | **$6,000** |

**Recommendation:** implement this literal schedule, including sunset. Keeping $9,300/$18,600 after 2029 would be a separately approved alternative-law assumption, not enacted law; do not silently carry it forward or index any band/deduction. The older 2025 booklet's 2027 sunset is superseded by the 2026 enactment.

### 4.2 Common retiree adjustments that the minimum model must address

- **SS:** subtract only the federally included amount under [§58.1-322.02(3)](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-322.02/). Ordinary IRA/401(k)/403(b), RMD, private pension/annuity and taxable conversion income otherwise remains in the base; no general $20,000/$31,110/$65,000 retirement pool.
- **Filer exemptions:** include **$930 per ordinary nondependent filer**, plus **$800 per qualifying age-65 filer**, under §58.1-322.03(2). These are separate from the standard deduction and the income-tested age deduction. The $800 does not disappear when the $12,000 deduction phases out. Blindness/dependents require facts outside the bounded subset.
- **No-tax threshold:** [§58.1-321](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-321/) and the Form 760 line-9 instructions establish no tax below **$11,950 Single/$23,900 joint**. Follow the official worksheet's adjusted line-9 income after age/SS modifications, before standard deduction/exemptions; do not compare final taxable income with these thresholds. Test strict “less than,” including older-filer ordering. Preserve signed modifications; do not invent an age-deduction addback from the statutory phrase referring to that modification.
- **Age deduction:** up to $12,000, with cohort/AFAGI/joint worksheet rules in §6. Required for common retirees, not an optional obscure credit.
- **Spouse tax adjustment (STA):** [Virginia filing-status guidance](https://www.tax.virginia.gov/filing-status) and the official Form 760 worksheet permit up to **$259** when both spouses have qualifying separate income. This is common, not negligible hidden tax relief. It requires separate VAGI/exemptions. **Proposal for smallest initial scope:** defer STA with explicit approval/disclosure of potential overstatement up to $259 per year. Full attribution could support it later; unallocated income must not be split to obtain it.

The [2025 resident instructions](https://www.tax.virginia.gov/sites/default/files/vatax-pdf/2025-760-instructions.pdf), particularly Exemptions, Age Deduction Worksheet, Form 760 lines 4–12 and STA worksheet, supply continuing methods checked against current statutes. Final 2026 resident instructions were not successfully established as available; do not describe the 2025 booklet as final 2026 law. The indexed [2026 early-release nonresident instructions](https://www.tax.virginia.gov/sites/default/files/taxforms/early-release/2026-individual/instructions/draft-2026-763-instructions-early-release.pdf) corroborate the moving 2026 age cutoff but are draft/nonresident guidance, not the proposed resident engine.

Other genuine rules include U.S.-obligation subtractions, other-state municipal additions, state refund adjustments, military/disability relief and credits. [§58.1-322.02(18)](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-322.02/) allows up to $40,000 qualifying military benefits from 2025, including covered survivors, without an age minimum; duplicate relief for the same income is restricted. Recommend disclosed ordinary private-benefit scope, refusal for reliably represented special classifications, and no military/service-history UI or name inference. Low-income/earned-income credits, disability elections and blindness are outside the initial subset; age-deduction/credit incompatibilities must not be ignored if such facts are represented.

## 5. Georgia retirement-exclusion matrix

Authorities: §48-7-27(a)(5), HB 463 §2-3, [DOR exclusion guidance](https://dor.georgia.gov/retirement-income-exclusion), Rule 560-7-4-.02 and IT-511 Schedule 1 retirement worksheet.

| Category / recipient | Legal treatment | Proposed bounded handling |
|---|---|---|
| Age under 62, ordinary nondisabled | No ordinary age exclusion | Tax normally; no inferred disability. |
| Age 62–64 | Up to $35,000 per person | Sum eligible receipts once for that person, then cap. |
| Age 65+, 2026 | Up to $65,000 per person | Nominal statutory cap, not another deduction per source. |
| Age 65+, 2027+ | Up to $70,000 per person, unconditional scheduled change | Apply actual year independently of conditional rate/deduction policy. No indexing found. |
| Traditional IRA/rollover IRA, RMD/opening RMD | Taxable IRA distributions enter worksheet | Account/owner/taxable amount and year must reconcile. No extra RMD exclusion. |
| Ordinary 401(k)/403(b) and private pensions | Taxable ordinary pension/retirement receipts can qualify | Existing fully taxable ordinary-plan assumption; no specialized qualification engine. |
| Taxable IRA-to-Roth conversion | Federal taxable IRA amount enters AGI and worksheet's taxable-IRA line | Eligible amount shares owner's same pool; see §8 for the legal application and source limits. |
| Employer-to-Roth / in-plan variant | Must establish taxable retirement-distribution character and federal reporting | Review independently; do not infer every variant from IRA wording. Initial unverified variants can be refused without blocking IRA/pension cases. In-plan execution is not currently modeled. |
| Private nonqualified annuity | Taxable annuity portion can qualify | Reuse admitted basis/receipt evidence; return of investment is not income. No CO-specific full-maturity restriction was established for GA. |
| Ordinary interest, dividends, capital gains | Eligible unearned retirement income for age-qualified owner | Exclude only amounts already modeled, reconciled and attributable. Loss/netting restrictions remain outside the nonnegative modeled subset. |
| Rental/royalty/partnership income | Potentially eligible; earned/unearned/FICA/material-participation distinctions apply | Current model has no sufficient separate rental/royalty/business tax channel. Legally eligible does not mean advertised supported; no new business-tax inputs. |
| Earned income | Up to $5,000 within the same total cap | Current normal tax model has no general wage/self-employment stream; do not classify generic conditional payments as wages or extend the cap. Not initial scope. |
| Federally taxable SS / separately exempt income | Separate subtraction; not retirement-pool consumption | Remove taxable SS once, exclude already-exempt income from the pool. |
| Known jointly owned property | Rule requires 50/50 allocation of its income | Use only explicit JOINT source/property evidence; household aggregation alone is not joint ownership. |
| Under-62 military / deceased veteran survivor | Separate statutory exclusions can apply | Disclose and leave specialized qualification unsupported; age-62+ ordinary military pool must not be stacked with an inapplicable under-62 exclusion. |

**Age convention:** statute §48-7-27(a)(5)(D) says qualifying age during any part of the taxable year. This supports the full annual cap upon reaching the threshold, without monthly cap proration. For normal birthdays use the recipient's threshold birthday in the year; a deceased filer cannot attain a later birthday after modeled January 1 death. **Open narrow boundary:** the reviewed GA sources do not expressly resolve importing a federal day-before-birthday rule to January 1 birthdays for this state-specific age test. Verify that boundary or refuse the affected boundary classification; do not silently copy AZ/CO/VA convention. Ordinary nonboundary birthdays need no new input.

## 6. Virginia age-deduction eligibility matrix

Authorities: §58.1-322.03(5), [Virginia retirement guidance](https://www.tax.virginia.gov/news/virginia-taxes-and-your-retirement) and the official Age 65 and Older Deduction Worksheet. No blanket denial to everyone born after 1939 is justified; equally, no blanket $12,000 deduction at 65 is justified.

| Cohort / status | Applicable deduction |
|---|---|
| DOB on/before January 1, 1939 | $12,000 for that qualifying filer, without the later-cohort income phaseout. Two qualifying spouses can each claim it. |
| DOB after January 1, 1939, below annual age eligibility | No age deduction. |
| Later cohort, eligible age 65, Single | `max(12,000 − max(AFAGI − 50,000, 0), 0)`. Fully phased out at $62,000. |
| MFJ, exactly one income-tested claimant | `max(12,000 − max(joint AFAGI − 75,000, 0), 0)`. Fully phased out at $87,000. Spouse's income participates even if spouse is younger. |
| MFJ, two income-tested claimants | Official worksheet: `max(24,000 − max(joint AFAGI − 75,000, 0), 0)` **combined**, then half assigned to each claimant. Fully phased out at $99,000. Do not subtract the full household excess twice. |
| MFJ, one grandfathered / one income-tested | Keep grandfathered $12,000 separate; apply the one-claimant income-tested worksheet to the other spouse. |
| Survivor Single after death year | Recompute Single AFAGI/$50,000 threshold and surviving filer's own eligibility. No continued joint threshold. |
| Disability/low-income/earned-income credit election | Incompatibilities can preclude the age deduction. Out of initial ordinary scope; represented competing eligibility requires refusal/review, not stacking. |

For the ordinary subset, **AFAGI = federal AGI, adjusted for applicable conformity differences, minus federally taxable SS/Tier 1 benefits**. Do not subtract federal basic/age/enhanced senior deductions, the VA standard deduction or VA personal exemptions when testing AFAGI. Do not assume every VA tax-exempt investment reduces AFAGI: [DOR ruling 13-5](https://www.tax.virginia.gov/laws-rules-decisions/rulings-tax-commissioner/13-5) addresses the distinction for U.S.-obligation income.

2025 instructions use DOB on/before January 1, 1961; this is an **annual age cutoff, not a permanent cohort closure**. For 2026 the moving cutoff is January 1, 1962, corroborated by 2026 early-release guidance. Derive from tax year; test January 1 versus January 2. The separate $800 exemption uses IRC §63(f) age facts. At modeled January 1 death, evaluate age at death rather than aging a deceased filer through year-end. No per-spouse retirement-income amount is needed to compute the household age deduction: an eligible nonworking spouse is not disqualified simply because the other spouse receives all retirement income.

## 7. Per-person attribution feasibility

Classes: **1 sufficient normal-path facts; 2 bounded assumption/proposal; 3 missing for full precision.** Qualification is distinct from ownership.

| Fact | GA | VA |
|---|---|---|
| Account-backed traditional withdrawals, current/opening RMD | **1** owner/type/year/amount supplied; **3** for scalar-only legacy residual requiring an exclusion | Household taxable scalar generally sufficient for base; explicit contradictions still fail. No owner-based retirement cap. |
| Executed conversion | **1** actual source/destination/owner; source qualification still required | Actual taxable scalar plus consistent evidence sufficient; owner restrictions remain execution rules. |
| Pension / ordinary survivor pension | **1** current recipient, original owner retained; **2** ordinary private-plan assumption | **1/2** taxable ordinary private-benefit assumption; age deduction uses filers/DOB, not pension source owner. |
| Admitted taxable annuity | **1** recipient/taxable amount/basis receipt; include eligible taxable portion in that person's pool | **1** taxable portion included, with existing federal/NIIT guards. |
| Gross selected SS | **1** actual current recipient, including survivor selection | **1**; taxable SS is subtracted at household level for base/AFAGI. |
| Individual taxable SS | Not needed for separate full SS subtraction; no arbitrary allocation | Not needed for proposed age-deduction/base scope. Do not import CO allocation without a VA use/rule. |
| Owned INTEREST/DIVIDEND/CAPITAL_GAIN / known taxable brokerage evidence | **1** where reliable owner and already-modeled taxable amount exist | Household total sufficient for ordinary base; owner evidence would matter for STA. |
| Explicit JOINT investment source | **1** legal 50/50 rule for that source, after reconciliation | Not necessary for proposed household tax; STA would require its own allocation rules. |
| Unallocated household investment total, MFJ | **3** for full legal exclusion; **2** proposal: leave remainder taxable without exclusion, disclose possible overstatement | Sufficient for base/AFAGI; **3** for accurate separate-income STA if enabled. |
| Single aggregate ordinary investment total | **2** proposal: attributable to the sole admitted resident filer when no contrary ownership/source evidence exists | Ordinary household total already sufficient. |
| Rental/business/royalty, disability, military/service-history detail | **3** specialized source/qualification absent | **3** for specialized relief/elections; not required for ordinary private income. |

**Recommended GA aggregation:** sum eligible known receipts by PRIMARY/SPOUSE; validate active recipient, year, category totals and account tax treatment. Sum each person's ordinary retirement and attributable eligible investment receipts before applying that person's cap. No unused-cap transfer. Refuse unowned retirement/conversion residuals that prevent an accurate common exclusion; treat unallocated *ordinary investment* remainder under the separately approved conservative policy instead of refusing the entire projection.

**Important Code reconciliation detail:** `TaxIncomeCalculator` emits an unowned `INTEREST` aggregate for its modeled investment bucket. `TaxIncomeAttribution.attach` replaces aggregates by source kind; attaching an owned CAPITAL_GAIN/DIVIDEND can leave that unowned INTEREST placeholder alongside the more specific evidence for the **same modeled dollars**. GA must reconcile the bucket once, distinguish the aggregate placeholder from additional explicit income, and compute any unallocated residual. Do not blindly sum both representations, create income, allocate the residual equally, or discard genuine conflicting/duplicate evidence. This is source-inspected, not an execution defect reproduced in this documentation audit.

The smallest future enhancement is a **GA-local investment receipt reconciliation/owner summary**, with real `TaxIncomeCalculator`/attachment integration tests. If an explicit origin marker proves necessary, propose the smallest optional runtime metadata addition separately; do not change shared attachment semantics or existing-state calculations to force GA admission. No general attribution framework or persisted/UI field is required for the proposed conservative subset.

## 8. Roth conversion eligibility and funding analysis

| Transaction | Georgia finding | Virginia finding |
|---|---|---|
| Fully taxable ordinary traditional/rollover IRA → same-owner Roth IRA | DOR IT-511's IRA section adopts federal taxability/conversion treatment; Schedule 1 explicitly includes taxable IRA distributions. **Application of those official rules:** the taxable traditional-source conversion is eligible for the age-qualified owner's shared retirement pool. It is not an extra exclusion or a tax-free Roth withdrawal. | [DOR ruling 98-44](https://www.tax.virginia.gov/laws-rules-decisions/rulings-tax-commissioner/98-44) expressly taxes conversion amounts included in federal AGI. Current AGI statute continues that principle. Its historical pre-1999 installment rules are not copied into 2026. |
| Ordinary employer 401(k)/403(b) → Roth IRA | Federal taxable pension-distribution character can bring it within GA's broad pool, but source/reporting/availability must be established independently. **Proposal:** verify this source-specific application at implementation before advertising it; initially refuse unverified variants rather than blocking IRA cases. | Federally included ordinary amount remains in VA base; no general retirement-source exclusion. Existing employer execution/qualification restrictions remain. |
| In-plan Roth conversion | [IRS designated-Roth guidance](https://www.irs.gov/retirement-plans/plan-participant-employee/retirement-topics-designated-roth-account) establishes possible federal taxable income; GA source qualification still requires review. No current Aledrix execution path to an in-plan Roth destination; do not add one here. | Federal AGI inclusion generally remains taxable; application execution is outside current supported destination subset. |
| Qualified Roth cash | Outside modeled federal AGI; neither taxable again nor another retirement exclusion | Same; no added state income or deduction. |
| Partial basis / nonqualified Roth / specialized inherited conversion | No new federal/basis qualification engine. Reliable represented exceptions outside the bounded source assumption require refusal/review. | Same boundary; do not silently classify all such receipts as qualified or tax-free. |

The GA IRA conclusion is a transparent application of DOR's conversion statement plus its taxable-IRA exclusion worksheet, not a newly located conversion-specific letter ruling. Ordinary IRA/RMD/conversion amounts share one recipient pool. The [IRS IRA conversion guidance](https://www.irs.gov/publications/p590a) distinguishes conversion income, qualified Roth withdrawals and nonconvertible RMDs. No independent state exemption for RMD dollars exists.

**Capacity:** `ProjectedPortfolioRothConverter` currently supports IRA/rollover/401(k)/403(b) pretax sources and **Roth IRA destinations only**; sources/destinations must have the same owner. Availability safeguards remain independent. `ProjectionEngine` taxes actual executed amounts, preserves requested shortfall and processes RMD first. Existing `BracketFillCapacityEvidenceTest` records a fixture where the earlier NC trial scalar exceeds executable ownership capacity (source inspected, not rerun); KY's bounded correction addresses that only for KY.

**Proposal:** GA needs equivalent new-jurisdiction-only trial capacity/attribution behavior, because recipient exclusion cannot be computed from an unowned trial residual. Reproduce with tests before any shared edit; cap trial scalar and attached actual sources consistently. Preserve KY and all earlier-state branches/financial goldens. VA's proposed aggregate-base/household-age model does **not** require owner-complete trials for a retirement pool. Keep its final actual-execution contract; if STA is later included or VA adopts a bounded capacity path, test separately rather than globally extending the KY branch.

Every funding/conversion evaluation must rebuild actual annual income, federal taxable SS/AGI, applicable exclusions/age phaseouts, status, deduction year, funded withdrawals and final tax. GA floor/cap crossings and VA age phaseout/no-tax threshold/2030 sunset make `conversion × marginal rate` inadequate. The VA state marginal effect can be **11.5%** while conversion income also removes an age deduction, before considering federal/funding feedback. A valid ordinary nonconvergence is a state-specific admission blocker; no solver redesign is authorized by this audit.

## 9. Federal tax-base compatibility

| Question | Georgia | Virginia |
|---|---|---|
| Legal starting base | Federal AGI with GA adjustments | Federal AGI with VA adjustments |
| Existing federal result needed | Modeled AGI and taxable SS; reconcile with `TaxIncome` | Same, plus household AFAGI for age deduction |
| Federal basic/age deductions | Do not subtract from GA AGI | Do not subtract from VA AGI |
| Federal temporary enhanced senior deduction | Below AGI; no automatic GA subtraction established | Below AGI; no automatic VA subtraction established |
| State senior relief | Personal eligible-income pool | VA's own age deduction and $800 exemption |
| Colorado bridge needed | No | No |

`FederalTaxBaseBreakdown.MODELED_STANDARD_DEDUCTION_ONLY_V1` exposes incomplete **federal taxable-income** coverage. Neither proposed state depends on that modeled taxable-income scalar, so omitted federal below-AGI deductions do not justify changing federal tax or importing Colorado's bridge. Nevertheless combined federal/state lifetime taxes retain existing federal modeling limitations, including qualification, investment income and deductions. A correct state estimate does not certify a complete federal return.

## 10. Brokerage admission policy

Apply Arizona B3 independently to each calculator:

1. Admit ordinary brokerage withdrawals with unknown tax basis. Gross proceeds fund cash/reduce assets; do not add principal to AGI or invent realized gains.
2. Keep the already-modeled federal investment bucket unchanged. Explicit taxable attribution explains/reconciles those dollars; it is not another income channel.
3. Reconcile source-kind aggregate placeholders separately from genuine additional/duplicate taxable evidence (§7). Refuse excess/wrong-year/contradictory/incompatible source facts; unknown basis alone is different.
4. GA: only attributable eligible investment dollars consume the appropriate recipient's exclusion. For approved unallocated MFJ remainder, no exclusion and a possible-overstatement disclosure. Do not make missing ownership a reason to reject all ordinary brokerage spending.
5. VA: ordinary modeled investments remain in the normal base/AFAGI, without a general retirement-income exclusion. Unknown ownership does not block that household calculation under the proposed STA omission.

Disclose both possible **understatement from unmodeled realized gains** and state-specific special-source/basis adjustments; GA additionally can **overstate** tax when eligible unallocated investment income receives no exclusion. No capital-loss/netting, special municipal/Treasury sourcing, security lots or foreign adjustments are created. Existing Advanced-mode annuity/NIIT/brokerage/growth safeguards remain independent; state B3 admission must not bypass them.

## 11. Supported and unsupported common scenarios

This is proposed feasibility after approval/testing, not current production support.

| Scenario | Georgia proposed scope | Virginia proposed scope |
|---|---|---|
| SS-only Single / ordinary survivor SS | Supported; subtract federally included amount separately | Supported; subtraction and no-tax threshold; no invented taxable SS |
| MFJ SS plus owned IRA/RMD | Supported with each recipient's own age/cap | Supported with actual filing status, household AFAGI/age worksheet |
| IRA + conversion / RMD + conversion | Supported verified IRA source, one recipient pool | Taxable actual conversion; age phaseout recalculated |
| One spouse pension, other conversion; different ages | Supported with actual owner amounts and separate caps | Supported household tax/individual age eligibility; no GA-style pool |
| Ordinary private pension / ordinary survivor pension | Supported ordinary-plan assumption, survivor's own cap | Supported ordinary taxable treatment; survivor age/status deduction rules |
| Admitted taxable nonqualified annuity | Taxable portion can enter recipient's pool | Taxable portion in base; preserve basis/NIIT admission |
| Qualified Roth withdrawals | Outside income; no additional pool deduction | Outside income; no invented tax/deduction |
| Brokerage principal-only / mixed brokerage + IRA | Supported B3 approximation | Supported B3 approximation |
| Known owner ordinary modeled investments | Eligible pool subject to reconciliation/age | Ordinary base/AFAGI; no general exclusion |
| Unallocated MFJ investments | Supported only with approved conservative no-exclusion remainder policy | Supported for household tax; STA omission disclosed |
| Explicit JOINT investments | 50/50 only for proven joint source, not all household income | Household total sufficient for initial scope |
| Scalar-only/unowned retirement or conversion | Unsupported when necessary recipient pool cannot be established | Aggregate taxable ordinary base may be sufficient; contradictions/special qualification still refused |
| Rentals/royalties, self-employment, wages, military/disability eligibility | Legally relevant but not initial model/qualification scope | Same; specialized elections/credit optimization not added |
| January 1 GA age-boundary ambiguity | Verify exact convention or explicit narrow refusal | VA moving cutoff supported by official age guidance; no fixed historical upper DOB cutoff |
| Itemization, dependent credits, partial-year/multistate, special basis | Outside bounded scope / represented unsupported facts refused | Same; STA/credit omission requires approval, not silent claiming of full return accuracy |

## 12. Minimal implementation architecture

Proposal for Phase 6E-J, with no implementation performed here:

- `GeorgiaProductionTaxCalculator` and immutable `georgia/GeorgiaTaxRules`; small state-local recipient/investment reconciliation helper if useful. NC supplies the AGI/version/disclosure structure, KY supplies per-person caps/capacity pattern. Neither supplies GA substantive constants.
- `VirginiaProductionTaxCalculator` and immutable `virginia/VirginiaTaxRules`; small VA-local age-worksheet helper with grandfathered count, income-tested count and AFAGI. Exact graduated slices fit a few state-local methods; no generic engine. Use the state standard schedule, filer/age exemptions and no-tax threshold.
- Existing annual request/result interface, structured unsupported reasons, router and source evidence. Do not branch tax arithmetic in ProjectionEngine/JavaFX or add new general income fields.
- Independent `georgia-income-tax-rules` / `virginia-income-tax-rules` version keys in the NC/KY freshness mechanism. Rule changes must invalidate associated full-plan analyses without changing stored results or other-state reference semantics.
- Register each state/conditional and annuity allowlist only after its own tests/coverage/disclosures pass. No CO registration or further CO implementation is implied.

Ordinary GA formula, after admitted state source adjustments:

```text
eligible[p] = ordinary qualifying retirement[p] + reconciled qualifying investments[p]
exclusion[p] = min(eligible[p], age/year cap[p])
base = max(federal AGI + admitted GA additions − admitted GA subtractions
           − federally taxable SS − sum(exclusion[p]) − GA standard deduction, 0)
tax = base × reviewed applicable rate, final HALF_UP cents
```

GA unallocated investments remain in AGI once but do not enter a person's `eligible[p]` under the proposed conservative MFJ approximation. Do not subtract gross SS, an unexecuted conversion or an unused spouse cap.

Ordinary VA formula:

```text
AFAGI = modeled federal AGI + applicable conformity adjustments − taxable SS
age = separate grandfathered deduction + official household income-tested age worksheet
VAGI = federal AGI + admitted additions − taxable SS − admitted other subtractions − age
if VAGI is below annual Single/MFJ no-tax threshold: tax = 0
else base = max(VAGI − annual VA standard deduction − filer/age exemptions, 0)
tax = graduated VA schedule(base), less only admitted adjustments/credits, final HALF_UP cents
```

Implement with signed intermediates before final floor. If an adjustment is already removed in the adopted base, never subtract it a second time. These formulas deliberately omit specialized sources/elections; §17 must approve the proposed STA/ancillary-credit bounds.

## 13. Independent golden-value worksheets and test designs

### 13.1 Worksheet method and scope

These are **independently calculated proposed fixtures, not executed application tests**. Decimal arithmetic was checked in PowerShell using the formulas below, without invoking any production calculator. Dollar amounts are exact; tax rounds only at the end to two places, HALF_UP. Official return instructions normally round return entries to whole dollars; retaining cents is a proposed planning convention, not a claim to reproduce a filed return.

Sources for the worksheet structure are the [GA IT-511 retirement worksheet and Schedule 1](https://dor.georgia.gov/document/document/2025-it-511-individual-income-tax-booklet/download), updated by [enacted HB 463](https://gov.georgia.gov/document/2026-signed-legislation/hb-463/download), and the [VA Form 760 instructions, age worksheet and tax computation](https://www.tax.virginia.gov/sites/default/files/vatax-pdf/2025-760-instructions.pdf), updated by the [current deduction statute](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-322.03/). Older booklet rates, deductions and sunset dates are not carried into the fixtures.

Unless specified otherwise: full-year resident; ordinary nondependent Single/MFJ filers; June birthdays to avoid unresolved GA January 1 treatment; all named traditional sources are qualifying, fully taxable and correctly owned; no itemization, blindness, dependent claims, special-source adjustments or credits; no incremental tax-funded withdrawal. Income means federally included income, not gross annuity proceeds or gross brokerage cash. VA numbers are **before any spouse tax adjustment** unless expressly ineligible; the proposed omission requires approval.

### 13.2 Georgia direct-calculator goldens

For 2026, compute `0.0499 × max(AGI − taxable SS − eligible owner exclusions − D, 0)`, with `D = 15,000/30,000`. Each eligible owner has one combined pool, not a new cap for each source. From 2027 the age-65 cap becomes $70,000. Future examples below hold the untriggered rate and deduction constant **only under the proposed planning policy**.

| ID / scenario | Independent worksheet | Expected GA tax |
|---|---|---:|
| GA-01 Single age 61, IRA $50,000, 2026 | No age exclusion; $50,000 − $15,000 = $35,000 taxable | $1,746.50 |
| GA-02 Same, ages 62 and 64 | $50,000 − $35,000 − $15,000 = $0 | $0.00 each |
| GA-03 Age 62, IRA $50,000 + traditional IRA-to-Roth conversion $20,000 | $70,000 − one $35,000 cap − $15,000 = $20,000 | $998.00 |
| GA-04 Age 62, private pension $30,000 + IRA $20,000 + IRA conversion $20,000, same recipient | Same $70,000 pool; no duplicate conversion exclusion | $998.00 |
| GA-05 Single age 65, IRA $100,000, 2026 | $100,000 − $65,000 − $15,000 = $20,000 | $998.00 |
| GA-06 Same, 2027, held rate/deduction policy | $100,000 − $70,000 − $15,000 = $15,000 | $748.50 |
| GA-07 Single age 65, IRA $85,000, 2026 | $85,000 − $65,000 − $15,000 = $5,000 | $249.50 |
| GA-08 MFJ, both 62, IRA $50,000 each | $100,000 − $35,000 − $35,000 − $30,000 = $0 | $0.00 |
| GA-09 Same total, all $100,000 owned by A | Only A's $35,000 exclusion; $35,000 taxable | $1,746.50 |
| GA-10 MFJ both 62, A pension $30,000 + A IRA $20,000 + B IRA conversion $20,000 | Exclusions A $35,000/B $20,000; remainder $15,000 below joint deduction | $0.00 |
| GA-11 Same $70,000, all owned by A | Exclusion $35,000; taxable $5,000 | $249.50 |
| GA-12 Single 62, gross SS $30,000 + IRA $50,000 | Federal taxable SS $25,500, AGI $75,500; subtract $25,500, then $35,000 and $15,000 | $0.00 |
| GA-13 GA-12 plus IRA conversion $20,000 | AGI $95,500; subtract taxable SS once and one retirement cap; taxable $20,000 | $998.00 |
| GA-14 Ordinary brokerage proceeds $100,000, no modeled taxable income | Proceeds do not enter AGI; base $0; missing-gain limitation required | $0.00 calculated |
| GA-15 Single 62, IRA $50,000 + owned modeled gain $5,000, with $100,000 brokerage cash | AGI $55,000, not $155,000; combined eligible pool capped at $35,000; taxable $5,000 | $249.50 |
| GA-16 Single 65, owner-attributed eligible interest/dividends/gains $50,000 | Eligible investment income below $65,000 cap; no brokerage principal | $0.00 |
| GA-17 Age 62, receipts $50,000 / $50,000.11 | Taxable $0 / $0.11; second raw tax $0.005489 | $0.00 / $0.01 |
| GA-18 Age 65 exclusion/deduction boundary | $80,000 receipts in 2026 / $85,000 in 2027 under held deduction | $0.00 each |

GA-12/13's federal SS amount follows the Single provisional-income worksheet in [IRS Publication 915](https://www.irs.gov/publications/p915): with $50,000 or more other ordinary income, 85% of $30,000 is taxable. The subtraction is that **$25,500**, never gross $30,000. Repeat with a lower provisional-income fixture so the state does not hard-code 85%.

The following ownership contrast is essential. MFJ, both 65, 2026: A owns IRA $90,000; modeled eligible investment income is $20,000; AGI $110,000; joint deduction $30,000:

| Investment evidence | Allowed owner exclusions | Taxable / tax |
|---|---|---|
| Unallocated household aggregate, proposed conservative policy | A $65,000 only; $20,000 remains taxable but present once | $15,000 / **$748.50** |
| All investment income established as B's | A $65,000 + B $20,000 | $0 / **$0.00** |
| All investment income established as A's | A's combined receipts still capped at $65,000 | $15,000 / **$748.50** |
| Income established as jointly owned under GA allocation rule | A gets $10,000/B gets $10,000; exclusions $65,000 + $10,000 | $5,000 / **$249.50** |

For each, test an unowned modeled `INTEREST` aggregate plus attached owned `CAPITAL_GAIN`/`DIVIDEND` evidence describing that same amount. Reconciliation must preserve AGI and avoid counting the evidence as another $20,000. Assert structured refusal for actual evidence exceeding the modeled bucket, incompatible ownership/source facts, unsupported losses and duplicate contradictory amounts. Test unallocated retirement receipts separately: the investment approximation must not fabricate a retirement owner.

**Conditional-law test, not an activated 2027 rule:** if the reviewed first conditional step is legally established, a Single age-65 IRA $100,000 fixture at 4.865%, deduction $15,375 and cap $70,000 yields `$14,625 × 0.04865 = $711.50625 → $711.51`. Until established and incorporated in a reviewed rules version, GA-06 remains the proposed held-policy expectation. Test that no projected revenue, inflation assumption or crossing a calendar year activates a conditional step.

**Annual/lifetime and survivor tests:** Single born June 1961, IRA $100,000 annually in 2026–2030, no tax-funded additions, held future-rate/deduction policy: `$998 + 4 × $748.50 = $3,992.00` cumulative state tax. This is policy-dependent, not a promise of actual future law. For a 2027 death-year MFJ household with both filers already 65 and $100,000 pension paid entirely to the survivor, cap $70,000 plus joint deduction $30,000 gives $0; the 2028 Single year gives $748.50. Reverse which spouse dies; attribute the actual survivor payment to the survivor, with no transferred unused deceased-person cap.

Qualification tests must distinguish supported ordinary private pension/taxable annuity receipts from generic conditional payments, military/disability facts and unsupported employer conversion variants. An annuity with $60,000 gross/$50,000 admitted taxable receipts has only $50,000 in the pool, not $60,000. Run ages 61/62/64/65, threshold birthday years, pre-birthday death, and the unresolved January 1 case as a verification/refusal test until resolved.

### 13.3 Virginia direct-calculator goldens

Let `V(T)` be the graduated schedule in §4, applied to nonnegative taxable income. These fixtures include **both** the state standard deduction and ordinary $930/$800 exemptions. Age deduction is calculated first using AFAGI, not after deducting the standard deduction. All elderly examples use already-attained eligibility unless the birthday itself is the test.

| ID / scenario | Independent worksheet | Expected VA tax |
|---|---|---:|
| VA-01 Single age 60, IRA $50,000, 2026 | $50,000 − $8,750 − $930 = $40,320; $720 + .0575 × $23,320 | $2,060.90 |
| VA-02 Same, 2027 | $50,000 − $9,200 − $930 = $39,870 | $2,035.03 |
| VA-03 Same, 2028 and 2029 | $50,000 − $9,300 − $930 = $39,770 | $2,029.28 each |
| VA-04 Same, 2030 statutory reversion | $50,000 − $3,000 − $930 = $46,070 | $2,391.53 |
| VA-05 Single 65, later cohort, IRA $50,000, 2026 | AFAGI $50,000; age deduction $12,000; $50,000 − $12,000 − $8,750 − $1,730 = $27,520 | $1,324.90 |
| VA-06 VA-05 plus IRA conversion $20,000 | AFAGI $70,000; age deduction $0; taxable $59,520 | $3,164.90 |
| VA-07 Single DOB January 1, 1939, income $100,000 | Unphased $12,000 age deduction; taxable $77,520 | $4,199.90 |
| VA-08 Single DOB January 2, 1939, same income | Income-tested deduction $0; taxable $89,520 | $4,889.90 |
| VA-09 MFJ both 65, later cohort, $80,000 income all paid to A | Age deduction $24,000 − $5,000 = $19,000 total; exemptions $3,460; standard $17,500; taxable $40,040 | $2,044.80 |
| VA-10 Same, only one spouse age-eligible | Age deduction $12,000 − $5,000 = $7,000; exemptions $2,660; taxable $52,840 | $2,780.80 |
| VA-11 Both 65, later cohort, joint income $99,000 | Combined age deduction $0; taxable $78,040 | $4,229.80 |
| VA-12 One grandfathered, other later-cohort 65, income $100,000 | $12,000 grandfathered + $0 tested; exemptions $3,460; taxable $67,040 | $3,597.30 |
| VA-13 Single 65, gross SS $30,000 + IRA $50,000 | AGI $75,500 − taxable SS $25,500 gives AFAGI $50,000; same deduction/base as VA-05 | $1,324.90 |
| VA-14 Ordinary brokerage cash $100,000 or qualified Roth distributions only, no modeled AGI | No principal/gain invention; state base $0 | $0.00 calculated |
| VA-15 Single 60, IRA $50,000 + modeled investment gain $5,000, with brokerage spending | AGI $55,000 once; taxable $45,320 | $2,348.40 |
| VA-16 Admitted annuity gross $60,000/taxable $50,000, Single 60 | Taxable receipts only; same base as VA-01 | $2,060.90 |

VA-06 minus VA-05 is **$1,840.00**, not $20,000 × 5.75%: the conversion also removes the $12,000 age deduction. This is an important integration oracle for withdrawal-funded conversions.

MFJ annual-deduction controls: both under 65, $100,000 income entirely A's, no spouse tax adjustment eligibility. Exemptions $1,860. Taxable bases/taxes: 2026 **$80,640/$4,379.30**; 2027 **$79,740/$4,327.55**; 2028–2029 **$79,540/$4,316.05**; 2030 **$92,140/$5,040.55**. Federal filing status does not double the VA rate bands.

Age worksheet controls, independently from final tax:

| Income-tested claimants / AFAGI | Expected combined age deduction |
|---|---:|
| Single: $50,000 / $50,001 / $62,000 | $12,000 / $11,999 / $0 |
| MFJ one claimant: $75,000 / $75,001 / $87,000 | $12,000 / $11,999 / $0 |
| MFJ two claimants: $75,000 / $80,000 / $99,000 | $24,000 / $19,000 / $0 |

For two tested spouses at $80,000, the official worksheet allocates **$9,500 each**, not $7,000 each. Test a qualifying spouse with no personal pension/IRA income: the age deduction is not a retirement-receipt exclusion. Retain the $800 exemption when the income-tested age deduction phases to zero. Test grandfathered-plus-tested and both-grandfathered combinations separately.

Birthday controls: 2026 eligibility cutoff is January 1, 1962, not a permanently fixed January 1, 1961 cutoff copied from 2025 instructions. Test January 1 versus January 2, year rollover and each survivor direction. Preserve the January 1 modeled death rule: do not let an already deceased person acquire a later-year birthday or future-year exemption. Test credits/subtractions that legally cannot be claimed with the age deduction as excluded classifications rather than silently stacking them.

Rate-unit fixtures: `V(0)=0`, `V(3,000)=60`, `V(5,000)=120`, `V(17,000)=720`; immediately below/at/above each breakpoint must be continuous before cent rounding. `V(17,000.01)=720.000575 → 720.00`. A pure rate-unit half-cent example `V(0.25)=0.005 → 0.01` verifies HALF_UP; it is **not** a full resident-return fixture, because the separate no-tax threshold applies to low-income households.

Threshold-order controls follow the official resident worksheet: under-65 Single VAGI $11,949.99 gives $0, while $11,950 gives taxable income `$11,950 − $8,750 − $930 = $2,270`, tax **$45.40**. MFJ under 65 at $23,900 gives taxable $4,540, tax **$106.20**; one cent below the VAGI threshold gives zero. Single 65 with ordinary AGI $23,949.99 has post-age VAGI $11,949.99 and zero tax; at AGI $23,950, post-age VAGI $11,950 gives taxable $1,470 and **$29.40**. These intentionally expose the strict threshold and ordering; reconfirm against the final 2026 resident instructions before admission.

**Annual/lifetime and survivor controls:** Single born June 1966, IRA $50,000 annually, 2026–2030: sum rounded annual taxes `$2,060.90 + $2,035.03 + $2,029.28 + $2,029.28 + $2,391.53 = $10,546.02`; ages stay below 65. In a separate 2027 death-year MFJ case with both spouses already age-eligible and $84,000 survivor pension entirely paid to the survivor, the two-claimant age deduction is $15,000, exemptions $3,460, standard $18,400: taxable $47,140, tax **$2,453.05**. In 2028 Single, AFAGI $84,000 eliminates the age deduction; taxable `$84,000 − $9,300 − $1,730 = $72,970`, tax **$3,938.28**. Repeat both death directions, retaining actual pension recipient and death-year filing status. No STA is due in these examples with income entirely one spouse's.

If omission of STA is approved, add a separate limitation test on a genuinely two-income MFJ household showing that the reported estimate excludes a potentially available adjustment of up to $259. If STA is instead included, independently complete its official ownership worksheet; do not retrofit the numeric fixtures by assigning income equally.

### 13.4 Independent tax-funding and bracket-fill controls

These are small algebraic oracles in fixed brackets, **not replacements for the iterative implementation**. They assume no SS, Medicare, other cash-flow adjustment or capital gain; the additional withdrawal is fully taxable traditional income of the named owner. Ordinary spending has already required $50,000 from the IRA, and taxes have no available cash unless stated. Federal values use the existing planner's 2026 basic-standard-deduction-only coverage: $16,100 deduction, 10% of the first $12,400 and 12% of the remaining relevant taxable income, independently sourced from [2026 IRS Form 1040-ES, standard deduction and Schedule X](https://www.irs.gov/pub/irs-pdf/f1040es.pdf). Thus baseline modeled federal tax is `$1,240 + .12 × $21,500 = $3,820`. They are **not full tax-return estimates for seniors**; this phase does not change the existing federal model.

| Control | Independent funding equation / expected components |
|---|---|
| GA age 62, IRA $50,000; traditional-funded taxes | At this deduction floor, each funding dollar adds GA tax .0499 and federal tax .12. `W = 3,820 / (1 − .12 − .0499) = 4,601.855198…`. Final rounded modeled federal tax **$4,372.22**, GA **$229.63**. Reconcile the one-cent iteration/final-rounding tolerance; do not hard-code raw W as a conflicting exact balance assertion. |
| Same GA plan, enough cash/brokerage principal to pay taxes | No extra traditional withdrawal: modeled federal $3,820, GA **$0.00**. Cash falls by tax; brokerage proceeds do not enter AGI. |
| VA age 60, IRA $50,000; traditional-funded taxes | `W = (3,820 + 2,060.90) / (1 − .12 − .0575) = 7,150.030395…`. Rounded federal **$4,678.00**, VA **$2,472.03**, funding **$7,150.03**. |
| VA age 65 later cohort, IRA $50,000; traditional-funded taxes | In this age-phaseout interval each funding dollar raises VA taxable income by $2. `W = (3,820 + 1,324.90) / (1 − .12 − .115) = 6,725.359477…`. Rounded modeled federal **$4,627.04**, VA **$2,098.32**, funding **$6,725.36**; W remains below the $12,000 phaseout interval. |
| VA age 60, same income, sufficient cash for taxes | No extra traditional income; modeled federal **$3,820**, VA **$2,060.90**; tax funding reduces available cash by **$5,880.90**. |

For an independent bracket-fill control, use starting traditional income **$15,000**, target **modeled federal taxable income $40,000**, sufficient same-owner traditional/Roth capacity, and no other income. The target AGI is $56,100 and modeled federal tax $4,552. GA age 62 has state tax `(56,100 − 35,000 − 15,000) × .0499 = $304.39`: with traditional-funded taxes the conversion is `$56,100 − $15,000 − $4,552 − $304.39 = $36,243.61`; with cash-funded taxes it is **$41,100**. VA age 60 has taxable state income $46,420 and state tax **$2,411.65**: traditional-funded conversion **$34,136.35**, cash-funded conversion **$41,100**. Test the helper's explicit target overload where available, or construct a current target-bracket request with independently recomputed room; do not label $40,000 as an actual statutory bracket endpoint.

Additional funding tests must straddle GA exclusion exhaustion, VA age phaseout start/end and no-tax threshold, state year transitions and survivor status. Keep attribution complete in GA capacity-limited trials. Test conversion request greater than executable capacity, exhausted primary capacity followed by spouse capacity, missing same-owner Roth destination, employer availability limits, actual versus requested conversion, and RMD-before-conversion. Assert no converted RMD, no unexecuted taxable residual, unchanged live balances and annual cash conservation. Refusal for genuinely missing owner facts must survive every funding iteration.

### 13.5 Bounded integration design, separately for each state

1. Real brokerage spending with missing basis; mixed brokerage/IRA/RMD/conversion plans; known gains already included once; inconsistent source refusal. Check actual account flows, not only synthetic `TaxIncome`.
2. Actual pension/annuity receipts, differing spouse ages, ordinary owner transfers on survivor pension, both death directions, death-year MFJ/later Single and opening versus projected RMDs. An ordinary conditional payment must not become a pension merely because it starts at retirement.
3. Two small fixed economic worlds, e.g. +4% and −2%, each compared with a deterministic run under identical economic assumptions. Match every year's taxes, actual conversions, funding and ending assets; retain result-time limitations and plan immutability. Follow `KentuckyBrokerageMonteCarloRegressionTest`; do not run a large statistical search.
4. One bounded integrated SS comparison and a small weighted scenario set, following `KentuckyIntegratedAcceptanceTest`. Check authoritative SS income, actual survivor ownership, exact longevity ending and annual/lifetime state metrics. Do not invoke exhaustive claiming optimization.
5. Save/reload, baseline/scenario copy, independent current-versus-baseline state selection, historical frozen results, Single/MFJ PDF/CSV and unsupported-result rendering. Assert exact stored taxes/limitations remain unchanged after editor-state or rule-version changes.
6. Independent GA/VA version-reference invalidation, both sides of retained Monte Carlo comparisons, and preservation of unrelated-state/SS-only freshness. Existing 15-state goldens, AZ B3 brokerage regressions, NC/KY integration and capacity regressions must remain unchanged.

These are proposed acceptance tests for implementation; none were added or run during this audit.

## 14. UI, reporting, persistence, history and freshness integration

**Code:** the financial boundary already feeds deterministic, Monte Carlo and integrated/weighted analysis. Registration alone is insufficient because presentation, source admission and frozen-result version capture have explicit state entries.

| Area | Required future implementation / acceptance |
|---|---|
| Jurisdiction and routing | Add GA and VA independently to `StateTaxJurisdiction` and `ProductionStateTaxService` only after that state's gates pass. Shared alphabetical selection follows the existing supported list; Colorado remains disabled. No state-specific arithmetic in JavaFX or `ProjectionEngine`. |
| Conditional/annuity admission | Add narrowly scoped entries to the existing conditional taxable-benefit and nonqualified-annuity gates only after source validation. Preserve basis, NIIT, advanced-calculation, growth and payment-classification safeguards. GA may include a qualifying taxable annuity in the recipient's pool; VA keeps it in ordinary income. |
| Deterministic/annual/lifetime results | Shared state result supplies annual tax and aggregates. Unsupported remains structured, not estimated zero. Test actual filing status, distribution/receipt owner and calendar-year rules. |
| State assumptions/help and results | Explain AGI starting base, state deduction/exemptions, GA recipient caps, VA age phaseout and dated future-law policies. Show material approximations on normal summaries, yearly details and range results; do not truncate to an older state's paragraph count. |
| SS analyzer | Extend immutable Analysis Inputs/Plan Assumptions rows and result-time summaries. SS-only ranking itself has no full-plan state calculation. Integrated deterministic and longevity evaluations use the annual service, authoritative household SS and frozen plan inputs. Do not redesign analyzer admission, ranking or exact second-death endings. |
| Individual/integrated/Monte Carlo report contexts | Capture state identity, rule reference and all applicable limitation paragraphs from the calculation-time plan/results. Both plan sides of comparisons require their own references. Never reconstruct historical GA/VA assumptions from today's editor selection. |
| CSV and PDF | `ProjectionCsvExporter`/`ProjectionPdfExporter` have explicit state metadata/limitation hooks beside generic financial totals. Add GA/VA hooks independently, include complete material notes and unsupported reasons, and test Single/MFJ and historical exports. Avoid numeric placeholder tax for refusal. |
| Plan JSON | Existing optional state assumptions/stable jurisdiction codes support new choices without a schema migration. Round-trip new plans and old 15-state plans, and test unrelated optional data. Do not persist fabricated source ownership. |
| Baseline/scenario copies | Use existing reconstruction/projection services; confirm the selected state's data survives copy/reload and runs the same tax boundary. `RetirementPlanSnapshot` currently retains domain references, so it is not a general deep immutable historical guarantee. Preserve that behavior; do not fold a baseline redesign into this phase. |
| Frozen retained results/history | `app/socialsecurity/results/RetainedProjectionSnapshot.State` stores jurisdiction, method, status, tax, limitations and unsupported reasons. Preserve those exact values on restore, including after editor state/rules change. A new rules version marks an analysis outdated; it never changes stored taxes or original notes. |
| Rule freshness | Add independent `AnalysisVersions` GA/VA rules references using the NC/KY pattern, capture through `AcceptedAnalysisCompletion.withStateTaxRules` from frozen input plans, and compare in `AnalysisCompatibilityPolicy`. Scope checks to analyses whose actual inputs used that state. Preserve other-state and SS-only behavior; no global fingerprint redesign. |

Recommended complete result-time disclosure content, with amounts/version/year supplied by the reviewed rules:

- **GA ordinary scope:** full-year residents, Single/MFJ, AGI base, own state standard deduction, ordinary taxable retirement/annuity income, per-person age caps, no unused-cap transfer. Taxable conversion income shares the source recipient's pool. Qualified Roth withdrawals stay outside modeled AGI.
- **GA ownership approximation:** known owner evidence controls; only established jointly owned income uses the official joint allocation. Unallocated MFJ modeled investment income remains taxable without a retirement exclusion under the proposed conservative policy and can overstate tax. Any approved Single convention must also be stated. This is separate from the brokerage missing-gain limitation, which can understate tax.
- **GA future law:** age-65 cap becomes $70,000 in 2027; conditional rate/deduction steps are not forecast. State the latest legally established reviewed step and the nominal continuation assumption. Do not imply that scheduled reviews guarantee reductions.
- **VA ordinary scope:** federal AGI less admitted adjustments, VA standard deduction, ordinary adult/age exemptions, official age-deduction cohort/phaseout and no-tax threshold. No general private-pension/IRA exclusion. A taxable conversion may reduce the age deduction as well as increase income.
- **VA future law and optional omission:** show the 2026–2029 standard deduction schedule and enacted 2030 reversion. If approved, disclose omission of the potentially available up-to-$259 spouse tax adjustment and other unmodeled credits; estimates may be higher. Do not hide this in a generic “simplified” label.
- **Both brokerage models:** gross proceeds are cash flow, not modeled gains. Unknown basis does not stop an ordinary plan. Only already modeled taxable income is included; unmodeled gains can make actual tax higher. Special investment-source adjustments and principal-residence, municipal-interest or government-obligation distinctions require separate consideration.
- **Both bounded tax models:** standard deduction only; no comprehensive credits/itemization, military/disability/service qualification, dependent/blindness optimization, separate-on-combined optimization, specialized basis or multistate/part-year/nonresident rules. Ordinary private pension assumption does not determine legal eligibility from a source name. The federal model's own disclosed coverage remains applicable to combined tax/funding estimates.

Persist the material applicable paragraphs with the calculated result. State-selection help is useful but does not replace frozen disclosures in history and exports.

## 15. Proposed Phase 6E-J workstream and independent admission gates

**Proposal only.** Develop both states in one workstream with separate calculators, rules versions, fixtures, source policies and release decisions. Estimated complexity is **moderate for GA** (owner pool, investment reconciliation and capacity-complete trials), **moderate for VA** (cohorts, joint age worksheet, exemptions, threshold and dated deductions). Including full VA spouse-tax optimization would add an ownership requirement and increase scope. No new general architecture is established as necessary.

1. Recheck current enacted law and official annual materials; resolve the specific source/birthday questions in §16. Record independently reviewed constants and activated conditional steps. Finalize the approved approximations from §17 before coding them.
2. Write direct independent golden/source/refusal tests before adding financial behavior. Add small immutable `GeorgiaTaxRules`/`VirginiaTaxRules`, one calculator each, and state-local source reconciliation. Keep them unregistered until ready for admission. Avoid a shared GA/VA law framework.
3. Reproduce GA bracket-fill capacity/evidence failures with focused tests. Extend only the narrow new-state capacity-complete path if proven necessary; preserve actual conversion order, same-owner destination, RMD and funding semantics. Keep existing-state expectations unchanged. VA's ordinary household age calculation should reuse the current annual funding path; any proposed extra capacity path must be separately justified/tested.
4. Implement each state's source admission and complete disclosures/version references, then bounded deterministic/funding/SS/Monte Carlo tests. Test threshold crossings and actual account flows, not only synthetic annual tax inputs.
5. Complete persistence, frozen history, UI/analysis metadata and PDF/CSV tests independently. Run focused tests first, then the established bounded state/projection/reporting regression set preserving all 15 existing financial goldens. Exclude exhaustive SS/search, performance, benchmark, capacity-probe and large Monte Carlo tests. Use IntelliJ bundled Maven and the project-local `.codex-m2/repository` when implementation testing is authorized.
6. Admit each state only after its own checklist passes. If only one passes, register that one as the 16th and keep the other isolated/disabled. The second can become the 17th after its own blockers are cleared. Produce the Phase 6E-J report with exact commands/results and native Windows acceptance instructions. Do not claim production support based on direct-calculator tests alone.

| Independent gate | Georgia | Virginia |
|---|---|---|
| Legal rules | Current reviewed rate/deduction step, 2027 cap, eligible sources and birthday guard verified or explicitly bounded/refused | Current rates, dated deductions/sunset, exemptions, age worksheet and threshold verified against current law/forms |
| Source/ownership | Per-recipient retirement pool, owner-specific conversion evidence, investment-bucket reconciliation; no invented spouse split | Ordinary AGI reconciliation, birth dates/status; no fabricated owner allocations for any optional spouse adjustment |
| Common financial paths | Brokerage, pension/annuity, RMD/conversion, actual capacity, exclusion exhaustion and traditional/cash funding correct | Brokerage, pension/annuity, RMD/conversion, age-phaseout/threshold/year transitions and traditional/cash funding correct |
| Analysis/integration | Two controlled MC paths, bounded SS/longevity, survivor directions, annual/lifetime totals | Same requirements with independent VA expected values |
| Retention/presentation | Approved approximation text frozen; JSON/copies/history/export and GA-specific freshness pass | Approved omission/sunset text frozen; JSON/copies/history/export and VA-specific freshness pass |
| Regression/admission | Existing 15-state behavior unchanged; GA independently registered only after all GA gates | Existing 15-state behavior unchanged; VA independently registered only after all VA gates |

Neither state's registration, golden expectations, version key or disclosures may depend on the other calculator passing. A common supporting-code failure affecting both must be fixed or both withheld; a GA ownership qualification question does not by itself block VA.

## 16. Remaining legal/modeling requirements and bounded exclusions

**No production acceptance tests have been performed and neither state is admitted.** The ordinary rate/base/exclusion/age rules above are sufficiently established to design implementation. Remaining work is not permission to guess missing facts.

| Requirement | Consequence / smallest safe resolution |
|---|---|
| GA threshold birthday convention, particularly January 1 | Statute says age attained during any part of the year, but reviewed GA materials do not expressly settle the day-before-birthday convention for this exclusion. Verify before advertising that boundary; otherwise explicitly refuse the affected ambiguous boundary. Do not import CO/IRS treatment without GA support. Ordinary midyear birthdays are not blocked. |
| GA conversion source qualification | Traditional IRA-to-Roth treatment is supported by the official federal-treatment guidance plus taxable-IRA exclusion worksheet, as an application of those authorities. Confirm the applicable taxable source for employer-plan variants before advertising them. Refuse an unverified variant without blocking supported ordinary IRA/pension scenarios. In-plan conversions have no current execution path and need no new subsystem. |
| GA unallocated investment ownership and aggregate overlap | Requires approval of the conservative MFJ policy and any Single convention, plus GA-local reconciliation tests. Do not reject ordinary brokerage cash for missing basis, allocate half a household aggregate, invent eligible income, or sum aggregate/evidence twice. If current runtime evidence cannot distinguish a real duplication safely, return structured unsupported for that evidence shape rather than infer an owner or alter AGI. |
| VA common spouse tax adjustment | Up to $259 may be available; accurate inclusion needs each spouse's relevant income/exemptions. Either approve explicit omission with a potential-overstatement note or scope a separately tested, fully attributed subset. Omitting it is an approximation, not a statement that VA lacks the adjustment. |
| VA annual forms versus amended law | Final 2026 resident instructions were not established in this audit. Recheck before implementation/admission, retaining current enacted deductions and the official age/threshold worksheet ordering. Do not use an old fixed annual birth cutoff or old sunset date. This is a publication refresh requirement, not an unresolved enacted 2026 rate. |
| VA common senior eligibility | A simple “65+ gets $12,000” guard is wrong. Include cohort, joint claimant count, AFAGI phaseout and separate $800 exemption; test mixed ages and death-year eligibility. Keep disability/credit incompatibilities outside the ordinary admitted subset unless facts suffice. |
| Both unsupported special sources | Known military/government-service exceptions, railroad classifications, disability, inherited/basis complications, exempt securities, business/rental/royalty losses, nonresident/part-year facts and inconsistent attribution need source-specific consideration. Refuse reliably represented incompatible facts; do not reject every ordinary pension merely because public/private classification is unavailable. |
| Combined federal coverage | Both state bases can use existing modeled AGI without a federal deduction bridge. Existing federal simplifications still affect combined taxes and funding. Do not silently claim additional federal deductions were modeled or change federal results to improve a state fixture. |
| Shared financial semantics | GA trial capacity/reconciliation must remain narrow. If a fix would change existing-state financial semantics or require a general attribution/persistence redesign, report that decision before expanding scope. No such redesign has been proven necessary by this read-only audit. |

Eligible investment categories in GA are broader than the current ordinary projection inputs. This report does not propose adding rental/business/tax-lot engines to exercise every statutory category. Missing investment gains remain disclosed missing modeled income, not a legal determination that no taxable gain exists.

## 17. Explicit user approval decisions before Phase 6E-J

The audit requests review of these concrete policies; **none is implemented here**:

1. Authorize combined GA/VA implementation with independent production admission; preserve all 15 accepted states and leave Colorado disabled. One passing state can be admitted while the other remains isolated.
2. GA: use reviewed 2026 4.99%/$15,000/$30,000 and the unconditional 2027 $70,000 age-65 cap. Incorporate future conditional changes only when legally established and reviewed; otherwise carry the latest reviewed rate/deduction nominally, with the explicit future-law limitation.
3. GA: calculate one recipient-owned pool from qualifying ordinary retirement and investment income. No cap transfer, no split of a household aggregate. Apply 50/50 only to established jointly owned property income. Approve the conservative MFJ **unallocated investment remainder gets no exclusion** policy; decide whether an otherwise uncontradicted Single household aggregate may be treated as the sole filer's. Preserve refusal for genuinely missing retirement ownership or contradictory evidence.
4. Approve the ordinary private-pension/admitted-annuity subset, with taxable basis treatment and existing NIIT/conditional safeguards. Include properly evidenced traditional IRA-to-Roth conversions within the same person's GA pool; separately verify/refuse unqualified employer variants. VA conversion amounts stay in ordinary AGI with age-phaseout interactions. No new in-plan conversion or qualification UI is proposed.
5. VA: implement ordinary $930 filer/$800 age exemptions, the official birth-cohort/joint age deduction and no-tax threshold. Use the **literal enacted 2030 $3,000/$6,000 reversion**. A frozen higher-deduction alternative would be an explicit legislative-change planning assumption, not current law, and would require a separately approved option.
6. VA: approve omission of the up-to-$259 spouse tax adjustment with prominent/frozen potential-overstatement disclosure, or explicitly choose the narrower fully attributed implementation instead. Also approve standard-deduction-only scope and omission of dependent/blindness optimization and specialized credits/adjustments; retain supported ordinary filer/age exemptions rather than omitting all personal exemptions.
7. Approve GA-only capacity-complete hypothetical conversions following the KY pattern, subject to focused reproduction and unchanged existing-state goldens. Keep RMD order, actual same-owner execution, cash conservation and iterative funding. Do not authorize broader financial semantics changes by implication.
8. Approve exact BigDecimal intermediates and final HALF_UP cents as a planning convention, complete frozen limitation paragraphs and independent GA/VA version references through existing infrastructure. Require all independent gates in §15 before either production registration.

No new source-entry field, general attribution framework, federal change, baseline redesign or persistence schema migration is recommended. Any later finding that actually requires one is a distinct scope decision.

## 18. Authoritative sources, verification record and audit checks

All legal findings were checked **2026-11-16** against the following primary materials. Statutes/session laws govern where an older booklet or administrative example has superseded amounts. The links in earlier sections identify the authority relevant to each rule; this catalog records coverage and cautions.

| Source | Coverage / verification caution |
|---|---|
| [GA DOR important tax updates](https://dor.georgia.gov/taxes/important-tax-updates) | Published 2026 rate/standard deduction. |
| [Signed GA HB 463](https://gov.georgia.gov/document/2026-signed-legislation/hb-463/download) and [DOR 2026 legislative summary](https://dor.georgia.gov/document/document/2026-legislative-summary/download) | Enacted 2026 amounts, conditional rate/deduction mechanism, 2027 retirement cap and effective dates. Do not substitute earlier bill versions. |
| [Georgia legislature-hosted Title 48, Chapter 7 supplement](https://www.legis.ga.gov/api/document/docs/default-source/joint-features-document-library/t48-ch7-8-%28v37%29-pdf.pdf) | O.C.G.A. §48-7-27: AGI modifications, age/category/recipient rules, SS and military provisions. This 2025 supplement must be overlaid with 2026 HB 463; its older standard deduction is not the 2026 amount. |
| [GA official administrative rules, Subject 560-7-4, Rule .02](https://rules.sos.ga.gov/gac/560-7-4) | Retirement-source allocation, known joint property, exclusion coordination. Older earned-income dollar examples are superseded by current statute/forms; do not copy $4,000. |
| [GA DOR retirement exclusion](https://dor.georgia.gov/retirement-income-exclusion) and [retirees FAQ](https://dor.georgia.gov/retirees-faq) | Official source categories, separate spouses and SS. FAQ earned-income amount is stale relative to the current $5,000 authority; statutory age-65 cap changes control from 2027. |
| [2025 GA IT-511](https://dor.georgia.gov/document/document/2025-it-511-individual-income-tax-booklet/download) | Federal conversion treatment, Schedule 1 and per-person retirement worksheets, return rounding. Not a source for 2026 rate/deduction constants. A final 2026 booklet was not established. |
| [Signed GA HB 1199](https://gov.georgia.gov/document/2026-signed-legislation/hb-1199/download) | 2026 conformity enactment; do not rely on an older generic federal-update page for current conformity. |
| [VA §§58.1-301](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-301/) and [58.1-322](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-322/) | Current conformity date and federal AGI starting point. |
| [VA §58.1-320](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-320/) | Graduated rates and bands. |
| [VA §58.1-322.03](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-322.03/) | Dated standard deductions, exemptions and age deductions. |
| [VA §58.1-322.02](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-322.02/) | Social Security, military and specialized subtractions. |
| [VA §58.1-321](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-321/) | No-tax threshold; read its definition/modification together with Form 760's ordering, not as permission to add a positive age deduction back to worksheet VAGI. |
| [VA §58.1-324](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-324/) and [official filing-status guidance](https://www.tax.virginia.gov/filing-status) | Joint treatment and spouse tax adjustment. |
| [VA 2026 legislative summary](https://www.tax.virginia.gov/sites/default/files/inline-files/2026-legislative-summary.pdf) and [new-law updates](https://www.tax.virginia.gov/news/new-virginia-tax-laws) | Enacted 2026 conformity and standard deduction schedule; supersedes the older booklet's future sunset. |
| [2025 VA resident Form 760 instructions](https://www.tax.virginia.gov/sites/default/files/vatax-pdf/2025-760-instructions.pdf) | Official age/threshold/spouse-adjustment worksheets and exemptions. Annual DOB cutoffs and future standard deductions must be updated from law. |
| [VA ruling 98-44](https://www.tax.virginia.gov/laws-rules-decisions/rulings-tax-commissioner/98-44) | Ordinary IRA-to-Roth inclusion and qualified Roth distinction; historical installment timing is not a 2026 rule. |
| [VA ruling 13-5](https://www.tax.virginia.gov/laws-rules-decisions/rulings-tax-commissioner/13-5) | Age AFAGI is not simply all income remaining after every VA subtraction. |
| [IRS Publication 590-A](https://www.irs.gov/publications/p590a), [Publication 575](https://www.irs.gov/publications/p575) and [designated Roth guidance](https://www.irs.gov/retirement-plans/plan-participant-employee/retirement-topics-designated-roth-account) | Federal conversion/source distinctions, not independent proof of a GA exclusion for every conversion variant. |
| [IRS Publication 915](https://www.irs.gov/publications/p915) and [2026 Form 1040-ES](https://www.irs.gov/pub/irs-pdf/f1040es.pdf) | Independent federal SS worksheet and basic deduction/rate inputs for the expressly bounded funding examples; no change to federal coverage. |

Some Virginia DOR direct requests timed out; official indexed/cached text was available for the legislative summary, resident instructions and specified rulings. This does not establish that unpublished final 2026 resident instructions exist. A draft 2026 nonresident instruction corroborates the annual birth-date cutoff but is not adopted as a resident tax engine. Georgia's individual-rule URL did not render the text; the official subject page supplied the rule. These access limitations and the narrow unresolved questions in §16 remain explicit.

**Repository/verification record:** initial working tree was clean. Only this audit document was created; no Java, test, state registration, federal calculation, persistence schema or accepted state rule was changed. No tests or Maven build were run for this documentation-only audit. The source inspections and independent decimal worksheet checks above establish design/test expectations, not release acceptance. Final `git status --short` lists only this new report; `git diff --check` passes. Because the report is untracked, its UTF-8 encoding and trailing whitespace were checked separately. No staging, commit, push or tag was performed.

**Completion decision:** stop at this report. GA and VA are suitable for bounded implementation subject to the stated independent gates and approval decisions. Phase 6E-J has not begun; neither state is production-supported by this audit.
