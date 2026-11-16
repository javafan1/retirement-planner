# Aledrix Retirement Planning — Phase 6E-C
# North Carolina State Income Tax Audit

Audit/source-verification date: **2026-11-16**. **AUDIT ONLY. North Carolina is not implemented or registered. Phase 6E-D requires explicit approval.**

Evidence labels used below:

- **Verified law:** supported by the linked enacted law or official guidance, with publication-year qualifications identified.
- **Existing behavior:** established by source inspection; not a claim that new North Carolina tests have passed.
- **Proposed:** recommended implementation or acceptance work, not an existing capability.
- **Approximation:** an intentionally bounded planning assumption requiring visible disclosure.
- **Unresolved/decision:** a release requirement or scope choice that remains open.

## 1. Executive summary and implementation suitability

**North Carolina fits the existing infrastructure.** Its ordinary-retirement arithmetic needs one small state calculator and a year-aware immutable rate schedule. No generalized rules engine, federal calculation change, new pension UI, or projection/analyzer refactor is warranted. Core arithmetic complexity is **low**; complete admission, disclosures, persistence/report verification and financial regressions make the overall implementation **moderate**.

For the proposed ordinary full-year resident, Single/MFJ scope, use modeled federal AGI, subtract its federally taxable Social Security, subtract North Carolina's own standard deduction, floor taxable income at zero and apply the calendar-year rate. Fully taxable traditional distributions, RMDs, ordinary private pensions, taxable annuity receipts and actual taxable Roth conversions remain included once. This follows the resident AGI starting point in [G.S. 105-153.4(a)](https://www.ncleg.gov/EnactedLegislation/Statutes/HTML/BySection/Chapter_105/GS_105-153.4.html), subject to the adjustments reviewed below.

The scheduled rates are **3.99% in 2026; 3.49% in 2027–2029; 3.24% in 2030–2032; 2.99% from 2033**, before any legally established conditional reduction. These are enacted steps, not projected revenue forecasts. See [S.L. 2026-41 §44.1, printed page 607](https://www.ncleg.gov/EnactedLegislation/SessionLaws/PDF/2025-2026/SL2026-41.pdf#page=607).

Two qualifications determine safe release:

1. Ordinary brokerage withdrawals must be admitted under the accepted Arizona B3 policy. Missing tax-lot basis alone is not a refusal condition. Gross proceeds do not become AGI; missing realized gains remain a disclosed model limitation.
2. Bailey-qualified government benefits and the separate military retirement exclusion can materially change tax, including some conversions. Existing pension records cannot establish these classifications. Ordinary private pensions are suitable under a visible private-pension assumption; automatic government-pension eligibility detection is not possible.

No unresolved ordinary-retirement rate or standard-deduction value was found. Release still depends on explicit implementation approval, approved coverage/rounding policies, complete disclosure plumbing, and passing focused financial/integration checks. Final 2026 annual-return instructions were not located on the official forms index; enacted law and the official 2026 estimated-tax worksheet establish the ordinary rules without pretending a final booklet was obtained.

## 2. Existing codebase architecture and reusable functionality

Java paths in this report are relative to `src/main/java/com/daviddunn/retirementplanner/`; test paths use the corresponding `src/test/java/` tree.

| Inspected component | Existing behavior and North Carolina reuse |
|---|---|
| `domain/model/StateTaxJurisdiction.java` | Explicit production choices, stable textual enum codes, alphabetical presentation. North Carolina is absent. Append NC only in an approved implementation. |
| `domain/model/StateTaxAssumptions.java` | One full-year jurisdiction throughout a projection. Only `SIMPLIFIED_STATE_ESTIMATE` is enabled; effective-rate override is rejected. No residency timeline. |
| `domain/tax/state/StateIncomeTaxCalculator.java` | Existing `calculate(StateTaxCalculationRequest)` interface is sufficient. |
| `StateTaxCalculationRequest.java`, `TaxHouseholdContext.java` | Immutable annual year/status, modeled income/federal result, projected rules, state assumptions, owners/birth dates and tax date. No mutable plan is needed in a state calculator. |
| `ProductionStateTaxService.java` | Single production registry and routing-metadata validation. Research catalog entries do not activate a state. |
| `ProductionStateTaxResult.java`, `StateTaxUnavailableReason.java`, `StateTaxUnavailableException.java` | `UNSUPPORTED` has no numeric estimate and structured reasons. Calculated zero remains distinct. Funding requires a calculated result. Reuse this contract. |
| `ArizonaProductionTaxCalculator.java` | **Closest overall structural example:** modeled AGI, taxable-SS subtraction, flat-rate arithmetic, B3 brokerage reconciliation, unsupported results and frozen limitation strings. Do not copy Arizona's senior/age adjustments or constant rate. |
| `arizona/ArizonaRetirementAdjustmentsCalculator.java` | Arizona-specific age exemption, temporary enhanced senior subtraction and phaseout. None belongs in North Carolina. Reuse no financial arithmetic from this component. |
| `IllinoisProductionTaxCalculator.java` | Secondary AGI/flat-tax example. Its qualifying retirement/conversion subtraction and personal/senior exemptions are not North Carolina rules. |
| `IowaProductionTaxCalculator.java` | Useful admission/disclosure pattern. Its federal taxable-base bridge, credits and low-income relief are unnecessary here. |
| `PennsylvaniaProductionTaxCalculator.java` | Useful refusal/qualification examples. Its state-defined income base and retirement qualification rules must not be copied. |
| `domain/tax/TaxIncome.java` | Ordinary income is pension + tax-deferred withdrawals + Roth conversion + `taxableInterestIncome` + conditional ordinary income. Conversion is already a separate included category. |
| `TaxIncomeCalculator.java` | Includes opening distributions; accepts authoritative household SS and pension income. Opening RMD is taxable annual income even though its cash was distributed before the projection. |
| `TaxIncomeAttribution.java` | Attaches/replaces metadata and includes admitted conditional taxable receipts. Metadata is not a second gain/conversion income channel. Some contradictions fail here before the state service is reached. |
| `domain/income/IncomeSourceAttribution.java` | Distinguishes cash-flow withdrawals, traditional withdrawals, RMD/opening RMD, conversion, pension, interest/dividend/gain and annuity receipts. Taxable-account withdrawal amounts are normally unknown; cash/Roth are modeled zero; traditional receipts are modeled fully taxable. |
| `FederalTaxCalculation.java`, `FederalTaxCalculator.java` | Supply modeled AGI and taxable SS. Federal modeled standard-deduction coverage is incomplete; it is not a complete Form 1040. North Carolina should not use federal taxable income or import federal deductions. |
| `TaxFundingCalculator.java` | Rebuilds annual income and federal/state tax for trial withdrawals, then recalculates final results. Tolerance is $0.01, maximum 100 iterations. Subtracts available household cash before funding taxes. |
| `domain/roth/RothConversionBracketFillCalculator.java` | Explicit state-aware overload passes selected assumptions into every funding calculation; compatibility overloads default to Michigan. Preserve the explicit production path and feedback iterations. |
| `domain/projection/ProjectionEngine.java` | Applies RMDs before conversion, caps/executes conversion in projected accounts, passes **actual** execution into final tax funding, then retains generic state results in `ProjectionYear`. Real account balances are separate. |

Important source findings:

- `ProjectionEngine` currently passes `BigDecimal.ZERO` for its explicit taxable-investment-income argument in both bracket fill and final funding. The adapter can honor modeled income presented through supported callers/receipts, but registration will not make normal portfolio growth or proceeds generate realized gains. There is no separate general dividend/gain input total in `TaxIncome`.
- `IncomeSourceAttribution.PensionFacts` and `RetirementParticipantFacts` hold ownership, benefit/survivor details, timing and separation/retirement conditions. They have **no** government/private/Bailey/military classification or 1989 vesting/service history. Display names are not classification evidence.
- `ConditionalBenefitAdmission` and `NonqualifiedAnnuityAdmission` have state-specific allowlists. Approved NC taxable annuity support needs a narrow addition to both applicable gates, preserving all current basis, NIIT, mode and growth safeguards. Registry changes alone are insufficient.
- Current generic annual tax results already carry limitations. Several UI/export paths additionally branch on IA/AZ; those branches need NC-specific presentation work after approval.

The reviewed historical reports are [Phase 6E-A](PHASE-6E-A-ARIZONA-STATE-TAX-AUDIT.md), [Phase 6E-B2](PHASE-6E-B2-ARIZONA-ADMISSION.md) and [Phase 6E-B3](PHASE-6E-B3-ARIZONA-BROKERAGE-ADMISSION.md). B3 supersedes the earlier Arizona brokerage refusal policy. Historical state counts and earlier acceptance caveats are not current source evidence.

## 3. Current supported-state baseline

**Existing behavior: 13 production states**, confirmed directly from [StateTaxJurisdiction](src/main/java/com/daviddunn/retirementplanner/domain/model/StateTaxJurisdiction.java) and [ProductionStateTaxService](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ProductionStateTaxService.java):

| Production path | States | Count |
|---|---|---:|
| Dedicated/adapted income-tax calculations | Michigan, Illinois, Pennsylvania, Iowa, Arizona | 5 |
| No-general-individual-income-tax calculator | Alaska, Florida, Nevada, New Hampshire, South Dakota, Tennessee, Texas, Wyoming | 8 |
| Total | | **13** |

North Carolina, Kentucky and Colorado are absent. North Carolina would be the 14th production state only after approved admission. The eight-jurisdiction research JSON contains a different set, including research-only states, and is not the production baseline. No NC catalog entry was found.

No existing state calculation or golden value needs to change. Later registration-count/choice assertions may legitimately become 14; financial expectations for Michigan, Illinois, Pennsylvania, Iowa and Arizona must remain unchanged.

## 4. Verified North Carolina rules and source assessment

### 4.1 Starting point, conformity and deductions

**Verified law:** resident taxable income begins with federal AGI, modified by state law. S.L. 2026-31 §12(a) updated the IRC reference to **July 5, 2025**, including provisions enacted by then with later effective dates. North Carolina uses fixed-date conformity, not automatic adoption of every future federal change. The same law creates R&E decoupling adjustments outside ordinary-retirement scope. See [enacted S.L. 2026-31, printed pages 42–44](https://www.ncleg.gov/EnactedLegislation/SessionLaws/PDF/2025-2026/SL2026-31.pdf#page=42) and [NCDOR's notice, updated October 9, 2026](https://www.ncdor.gov/taxes-forms/information-tax-professionals/tax-bulletins-directives-and-other-important-notices/important-notices-and-frequently-asked-questions-personal-taxes/important-notice-impact-recently-enacted-laws-north-carolina-individual-and-corporate-income).

**Verified law:** the state standard deduction is **$12,750 Single / $25,500 MFJ**. No automatic inflation provision or enacted future change to these amounts was identified. Holding them nominally constant therefore follows the reviewed law; it is not Arizona's approximation to indexed deductions. There is no additional NC standard deduction for age 65 or blindness. Standard-deduction eligibility restrictions still exist. See [G.S. 105-153.5(a)(1)](https://www.ncleg.gov/EnactedLegislation/Statutes/HTML/BySection/Chapter_105/GS_105-153.5.html) and [NCDOR deduction guidance](https://www.ncdor.gov/taxes-forms/individual-income-tax/filing-topics/north-carolina-standard-deduction-or-north-carolina-itemized-deductions).

**Verified law:** federal enhanced senior deductions are taken after AGI and do **not** reduce NC taxable income. Updating conformity did not create a similar NC subtraction. NCDOR expressly answers this in [post-enactment FAQ Q14–Q15](https://www.ncdor.gov/taxes-forms/corporate-income-franchise-tax/frequently-asked-questions-and-important-notices/faqs-regarding-recent-session-law-changes). Proposed NC calculations must not subtract $6,000/$12,000, Arizona's $2,100 age exemption, or federal age additions. The existing federal model's omission of some below-AGI deductions does not justify an NC-only federal repair; it can still affect combined tax funding and lifetime outcomes.

### 4.2 Retirement and investment treatment

| Income/source | Verified treatment and proposed application | Authority |
|---|---|---|
| Social Security | Remove exactly the amount included in federal AGI, not gross benefits again. | [NCDOR Social Security guidance](https://www.ncdor.gov/taxes-forms/individual-income-tax/filing-topics/social-security-and-railroad-retirement-benefits); G.S. 105-153.5(b)(3). |
| Traditional IRA; traditional 401(k)/403(b); RMD | Ordinary federally taxable amounts remain in the base. No general age-based retirement exclusion. Initial model assumes fully taxable traditional receipts. | [Resident AGI definition](https://www.ncleg.gov/EnactedLegislation/Statutes/HTML/BySection/Chapter_105/GS_105-153.4.html); [NCDOR explanation of the retirement-deduction repeal](https://www.ncdor.gov/documents/policies/2014-individual-income-tax-changes/open). |
| Ordinary private pension | Federally taxable pension remains included; no former $2,000 private retirement deduction. | [NCDOR 2014 changes](https://www.ncdor.gov/documents/policies/2014-individual-income-tax-changes/open), confirmed against current adjustment law. |
| Taxable nonqualified annuity | Include the already-modeled taxable receipt, not its return-of-investment portion or total gross cash again. | [Resident AGI definition](https://www.ncleg.gov/EnactedLegislation/Statutes/HTML/BySection/Chapter_105/GS_105-153.4.html); [IRS Publication 575](https://www.irs.gov/publications/p575) for underlying federal pension/annuity inclusion. |
| Taxable Roth conversion | Ordinary taxable conversion stays in AGI once. Bailey-qualified sources are a material exception outside initial scope. | [NCDOR PD-14-1](https://www.ncdor.gov/taxes-forms/individual-income-tax/personal-taxes-division-directives/pd-14-1-bailey-v-state-north-carolina-emory-v-state-north-carolina-patton-v-state-north-carolina). |
| Qualified Roth withdrawal | No federal AGI inclusion; no NC addition merely because cash was withdrawn. Qualification remains the existing model assumption. | [IRS Roth account guidance](https://www.irs.gov/retirement-plans/roth-acct-in-your-retirement-plan), applied through the resident AGI base. |
| Ordinary taxable interest, dividends, modeled capital gains | Federally included ordinary investment amounts remain included at the state's annual flat rate; no general preferential state capital-gain rate was found. Special source/state-basis adjustments are outside initial scope. | [G.S. 105-153.4](https://www.ncleg.gov/EnactedLegislation/Statutes/HTML/BySection/Chapter_105/GS_105-153.4.html), [G.S. 105-153.5](https://www.ncleg.gov/EnactedLegislation/Statutes/HTML/BySection/Chapter_105/GS_105-153.5.html). |
| Ordinary brokerage withdrawal | Proceeds include principal; only income already modeled as taxable belongs in AGI. A withdrawal does not prove a gain amount. | [IRS investment-income guidance](https://www.irs.gov/publications/p550), applied through NC's AGI starting point. |

The Social Security and Bailey filing-topic pages identify their default discussion as tax year 2025. Their continuing principles were checked against current statutes and the 2026 amendment record; they are not represented as final 2026 instructions. Older Roth directive PD-98-4 contains obsolete conversion limits and retirement deductions and must not be copied into 2026 rules.

Other actual adjustments include US-obligation interest and certain NC obligations, out-of-state tax-exempt interest additions, state/federal basis differences and railroad benefits. They cannot be reconstructed from a generic investment total. Initial admission should disclose these exclusions and refuse explicit contrary source facts rather than assume a legally required adjustment is zero. See [G.S. 105-153.5(b),(c)](https://www.ncleg.gov/EnactedLegislation/Statutes/HTML/BySection/Chapter_105/GS_105-153.5.html).

### 4.3 Amendments checked after S.L. 2026-41

The [official biennium modification table](https://www.ncleg.gov/Laws/Modifications/2025) lists §44.1 of S.L. 2026-41 as the amendment to G.S. 105-153.7; no later enacted rate amendment was identified as of this audit. The [not-yet-incorporated modifications page](https://www3.ncleg.gov/Laws/Modifications/Current) also lists it. This explains why the general statute HTML and the older NC-40 worksheet still show 3.99% after 2025/2026. Use the enacted amendment for future rates.

**Later amendment found:** [S.L. 2026-59 §17.6(b),(c),(f), printed page 20](https://www.ncleg.gov/EnactedLegislation/SessionLaws/PDF/2025-2026/SL2026-59.pdf#page=20) adds a deduction for included portable-benefit-account contributions and an addition for withdrawals not used for permitted portable benefits, effective from January 1, 2027. This does not alter the ordinary-retirement rate or standard-deduction schedule. It is specialized independent-contractor account treatment outside the proposed scope; it must not be confused with an ordinary IRA or brokerage withdrawal.

S.L. 2026-41 §44.2 also added an itemized gambling-loss deduction, and S.L. 2026-31 changed several business/other adjustments. These do not require a comprehensive itemization or business engine for ordinary retirees. The October NCDOR notice above explains the enacted changes. No pending bill or revenue forecast is treated as law.

## 5. Enacted future-year rate schedule

**Verified law:** [NCGA's §44.1 summary](https://www.ncleg.gov/Legislation/SummariesPublication/Summary/2026/11/S257-SMSV-136%28sl%29-v-4/) corroborates the enacted text, effective July 7, 2026.

| Tax years | Enacted scheduled rate | Proposed decimal |
|---|---:|---:|
| 2026 | 3.99% | `0.0399` |
| 2027–2029 | 3.49% | `0.0349` |
| 2030–2032 | 3.24% | `0.0324` |
| 2033 onward | 2.99%, subject to legally triggered later reductions | `0.0299` |

Conditional reductions can apply in **2035–2040**, in 0.25 percentage-point steps, to 2.74% and then 2.49%, with a 2.49% floor. The law compares final General Fund revenues, reported by the State Controller in August, with these thresholds:

| Revenue fiscal year | Revenue must exceed | Tax year potentially reduced |
|---|---:|---:|
| 2033–2034 | $40,258,000,000 | 2035 |
| 2034–2035 | $41,087,000,000 | 2036 |
| 2035–2036 | $41,965,000,000 | 2037 |
| 2036–2037 | $42,896,000,000 | 2038 |
| 2037–2038 | $43,892,000,000 | 2039 |
| 2038–2039 | $44,957,000,000 | 2040 |

Authority: [S.L. 2026-41 §44.1(a)](https://www.ncleg.gov/EnactedLegislation/SessionLaws/PDF/2025-2026/SL2026-41.pdf#page=607). PDF extraction includes struck prior-law rows; the official summary confirms replacement of the 2027–2034 triggers rather than accumulation of old and new schedules.

**Proposed policy:** select the enacted scheduled rate by calendar year; hold 2.99% after 2033 unless a later reduction has been legally established and included in a reviewed rule update. Do not forecast state revenue, activate 2.74%/2.49% merely because a projection reaches 2035, or extrapolate further reductions after 2040. Carry forward any subsequently established rate until law changes again. Keep the existing published schedule immutable per run, with a version/citation and a visible future-law assumption. No live network dependency belongs in projections.

## 6. Common retirement scenario support matrix

These are **proposed NC admission outcomes after implementation**, not current NC capabilities. All assume domestic full-year residents using Single/MFJ and the existing modeled federal income coverage. “Existing inputs” does not mean complete tax-return coverage.

| # | Scenario | Proposed outcome | Additional data genuinely required? |
|---:|---|---|---|
| 1 | Single, Social Security only | **Supported with existing inputs**; modeled NC tax zero after taxable-SS subtraction. | No. |
| 2 | MFJ, SS plus fully taxable IRA withdrawals | **Supported with existing inputs**; subtract taxable SS, retain IRA. | No. |
| 3 | Traditional IRA withdrawal plus taxable Roth conversion | **Supported with existing inputs**; both included once, with iterative funding. | No for fully taxable ordinary accounts. |
| 4 | Ordinary private pension plus SS | **Supported with existing inputs**, under prominently disclosed ordinary/private fully taxable pension scope. | No new field; existing data cannot verify private/government classification. |
| 5 | Taxable nonqualified annuity receipts | **Supported with existing inputs within the existing admitted annuity subset**; allowlist plumbing required. | Existing annuity basis/terms are required by that model, not new NC fields. |
| 6 | Brokerage-funded retirement spending, unknown basis | **Supported with disclosed approximation**; proceeds fund cash flow, no invented gain. | No for estimate. Realized-gain/basis data is needed for more complete taxation. |
| 7 | Brokerage withdrawals plus IRA income | **Supported with disclosed approximation**; tax modeled IRA/investment income, not gross proceeds. | Same as #6. |
| 8 | Known already-modeled taxable investment income | **Supported with existing request inputs**, with federal-model coverage disclosure; reconcile attribution. | No new NC data. Normal projections do not automatically produce general gains/dividends. |
| 9 | Qualified Roth distributions | **Supported with existing inputs**, under the existing qualification assumption. | Nonqualified cases need basis/history and remain outside scope. |
| 10 | RMD commencement | **Supported with existing inputs**; use existing projected prior-December-31 snapshots and opening/current RMD separation. | No new NC data. |
| 11 | Death-year MFJ, Single in later years | **Supported with existing inputs**, preserving existing death-year convention. | No. No new qualifying-surviving-spouse status engine. |
| 12 | Tax years 2026, 2027, 2030, 2033 | **Supported with existing inputs** through the explicit rate schedule. | No. |
| 13 | Conditional post-2034 reductions | **Supported with disclosed future-law assumption** at 2.99%; automatic untriggered reductions **unsupported**. | An official legal/revenue determination and reviewed rule update before applying a lower rate. |
| 14 | Bailey government pension/distribution/conversion | **Unsupported specialized classification** in initial scope; unknown government qualification cannot be auto-detected. | Actual plan/vesting/service and distribution/rollover history for individualized treatment, outside initial UI. |
| 15 | Explicit contradictory income attribution | **Unsupported**, or validation fails before adapter entry; no numeric zero fallback. | Correct the contradictory facts; do not request basis for every ordinary brokerage account. |

Tax-funding from brokerage/cash is admissible within the ordinary model. Existing Advanced/annuity NIIT restrictions can independently refuse brokerage assets or unclassified nonqualified growth; NC admission must not bypass these unrelated, existing capability guards.

## 7. Bailey and other specialized pension policy

**Verified law:** Bailey protects specified NC state/local and federal government retirement benefits, including qualifying beneficiary benefits. Defined-benefit eligibility generally requires five years of creditable service by **August 12, 1989**; the identified NC §401(k)/§457 plans use historical contribution/contract conditions. Other states' government plans are not automatically covered. Eligibility is not established by a familiar pension name. See [NCDOR Bailey guidance](https://www.ncdor.gov/taxes-forms/individual-income-tax/filing-topics/bailey-decision-concerning-federal-state-and-local-retirement-benefits).

Rollovers can change protected treatment. In particular, an eligible Bailey-plan rollover to Roth can be state-exempt to the extent included federally, while a nonqualifying source remains taxable. See [NCDOR PD-14-1](https://www.ncdor.gov/taxes-forms/individual-income-tax/personal-taxes-division-directives/pd-14-1-bailey-v-state-north-carolina-emory-v-state-north-carolina-patton-v-state-north-carolina). It would be wrong to promise that all government-plan conversions have the ordinary NC treatment.

**Also material:** the separate uniformed-services deduction applies to qualifying retirement pay, including the specified 20-year-service or medical-retirement cases and eligible survivor benefits. It is not limited to Bailey's historical date. See [NCDOR military retirement guidance](https://www.ncdor.gov/taxes-forms/individual-income-tax/filing-topics/military-retirement).

**Proposed bounded policy:**

- Support ordinary fully taxable private pensions and ordinary non-Bailey traditional accounts.
- Disclose Bailey and military/railroad exclusions as requiring separate consideration. Do not calculate historical eligibility, infer names, or add pension-classification UI.
- Refuse explicitly represented facts that require unavailable special treatment using `UNSUPPORTED_MODEL_RULE`/`UNSUPPORTED_TRANSACTION_TYPE` or an existing applicable reason, with an explanatory limitation.
- Be candid about representability: current pension facts contain no Bailey flag. A fixture named “Bailey pension” must not trigger invented classification. Generic pension records will receive ordinary taxable treatment under the disclosed scope. A blanket missing-classification refusal would unnecessarily reject ordinary private pensions.
- Future structured special-classification support must add an admission guard before treating those facts as supported. No new classification field is recommended for this phase.

**Suitability assessment:** safe for the explicitly bounded ordinary-private-pension scope, with visible limitations. It is not a generally accurate government-retiree estimator. Treating an undetected Bailey pension as ordinary can substantially overstate annual/lifetime tax and distort conversion/break-even comparisons; a hidden backend note is insufficient disclosure. Approval of this bounded admission policy is required before release.

## 8. Brokerage withdrawal admission policy

Use the accepted post-B3 Arizona behavior as the acceptance standard, without modifying Arizona:

1. Admit positive ordinary `ORDINARY_WITHDRAWAL` cash flow with empty taxable metadata. Empty means the model did not calculate its taxable portion; it does not establish legal tax exemption.
2. Start from federal AGI already modeled. Never add gross proceeds, account principal or a percentage of withdrawals as invented gain.
3. Known taxable gain/dividend/proceeds portions must reconcile with the existing modeled investment-income category (`getTaxableInterestIncome()` despite its narrow name). Attribute/check them; do not add them again.
4. A `CAPITAL_GAIN` record by itself does not create a federal income amount. An explicit positive taxable attribution without matching modeled income is a contradiction, not permission to silently ignore income or repair federal AGI.
5. Preserve wrong-year, excessive-category-total, unknown taxable retirement, explicitly taxable nonqualified Roth, special/exempt investment-source and unclassified transaction guards. Do not double-count duplicate gain/proceeds evidence for the same modeled income.
6. Disclose that unmodeled realized gains can make actual tax higher. NC should not inherit Arizona's distinct warning about omitting an Arizona qualifying-gain subtraction.

**Existing acceptance references inspected:** `domain/projection/ArizonaBrokerageProjectionRegressionTest.java` uses real brokerage withdrawals in 2027, successful Michigan-to-Arizona recalculation, principal/mixed/conversion scenarios, unchanged live balances and cash conservation. `app/montecarlo/ArizonaBrokerageMonteCarloRegressionTest.java` checks two controlled economic paths against matching deterministic execution, including state results, funding, conversion and assets. Mirror the substance for NC; do not replace brokerage accounts with cash in every acceptance fixture.

Unmodeled gain limitations also affect federal tax, Social Security taxation, Medicare and combined withdrawal funding. A state adapter cannot repair them. Existing federal preferential dividend/gain coverage remains unchanged.

## 9. Roth conversion correctness assessment

**Proposed annual formula within admitted scope:**

```text
NC adjusted income = modeled federal AGI - federally taxable Social Security
NC taxable income  = max(NC adjusted income - NC standard deduction(status), 0)
NC tax estimate    = NC taxable income * enacted rate(calendar year)
```

Use `BigDecimal`, preserve intermediates and apply approved final rounding. The formula deliberately excludes specialized adjustments outside initial scope; it does not claim those adjustments never exist.

| Correctness issue | Required behavior and existing support |
|---|---|
| IRA distribution plus conversion | AGI already contains both. Attribution verifies inclusion; never add conversion sources to AGI a second time. |
| RMD and conversion in same year | Existing RMD allocation precedes conversion. Annual RMD, pre-projection RMD and in-projection cash remain distinct. Tax their modeled distributions once; convert only eligible remaining assets. |
| Conversion request exceeds capacity | Existing same-owner execution/caps determine actual conversion. Final state tax uses actual household amount, not requested/unexecuted dollars. |
| Social Security interaction | Use the authoritative household annual SS result in the existing federal/funding path. Each trial subtracts that trial's federally taxable SS from its AGI. Changes in federal taxable SS alone cancel out of the NC base; they can still increase federal tax and funding needs. |
| Filing-status transition | Consume the annual request status. `getProjectionFilingStatus` keeps configured status through the death year, then Single. Do not continue the joint deduction merely because two owners remain in household metadata. |
| Tax-funded traditional withdrawals | Funding increases modeled distributions/AGI; recompute NC tax on every trial and final pass, including deduction-floor crossings. |
| Cash/brokerage/qualified Roth funding | Funding does not automatically increase taxable income. Respect allocator/source totals and the B3 approximation. |
| Bracket fill | Continue calling the state-aware iterative funding path. State tax changes how much traditional withdrawal funds taxes, which changes federal bracket room. No one-pass room or constant marginal gross-up replacement. |
| Future years | Resolve the rate separately for each actual tax year, including in mortality/MC scenarios. Do not freeze the opening-year rate for the run. |
| Annual/lifetime tax | Retain the final state result in every `ProjectionYear`; lifetime state tax is the sum of those annual state taxes. Existing combined-tax metrics must continue to sum federal + state. |

For fixed income above the deduction and no feedback, an ordinary conversion increment may equal rate × conversion. That shortcut is not valid across the income floor, differing funding sources or federal feedback. There is no Arizona senior phaseout to reproduce in NC.

**Existing limitation:** `ProjectionMetricsCalculator` exposes combined `totalTaxes`, not a dedicated lifetime-state component. Annual state amounts are available. Test their independent sum; do not label the combined metric “lifetime state tax.” A new displayed state-only aggregate is a separate presentation decision if desired, not a prerequisite to calculate annual NC tax.

## 10. Minimal implementation and integration design

### 10.1 Proposed domain change

Add `domain/tax/state/NorthCarolinaProductionTaxCalculator.java` implementing the existing interface and, preferably, one small immutable `northcarolina/NorthCarolinaTaxRules.java` with:

- A reviewed version such as `NC-2026-1`, legal citations and supported minimum year 2026.
- The two state deductions and the four explicit rate periods; a simple year lookup suffices.
- Explicit no-untriggered-reduction policy. No generalized multistate scheduler, new library, remote lookup or rule-loader hierarchy.

Use Arizona B3's admission/reconciliation pattern selectively, then the much smaller NC formula. Do not extract/change Arizona code merely to share checks. Validate state/year/status and federal/source consistency; use existing unsupported results. NC has no age deduction, so birth dates need not become a new tax-specific eligibility requirement. Preserve existing household context validity; do not build an age/service eligibility engine.

Only after approved implementation/testing, append `NC` to `StateTaxJurisdiction`, register its calculator, and add the narrow existing annuity/conditional-income allowlist entries needed for the approved scope. No new plan input fields or persistence schema are proposed.

### 10.2 Integration assessment

| Surface and inspected components | Proposed integration / acceptance requirement |
|---|---|
| Deterministic `ProjectionEngine` | Existing selected-state funding path is sufficient. Verify partial first year/opening RMD, conversion execution, Medicare funding, retained cash and immutable live accounts. Do not prorate annual state deduction/rate merely because the opening projection period is partial. Do not claim the model reconstructs all pre-opening income. |
| `MonteCarloAnalyzer` | Uses isolated plans and the same `ProjectionEngine` with supplied economic paths. Two controlled worlds are enough for admission consistency checks; no new MC tax engine or exhaustive simulation. Preserve structured unavailable behavior. |
| `LongevityWeightedIntegratedStrategyEvaluator`, continuation evaluator | Existing exact ending at second-death year minus one must remain. Rate depends on scenario calendar year/status. Opening second-death snapshots require no engine call/state-tax year. No mortality weighting inside the calculator; preserve scenario probabilities and continuation fallback semantics. |
| Integrated SS evaluator/comparison/grid/exhaustive search | Deep-copied scenarios already carry state assumptions through full projections. Test a small baseline/candidate set and structured failures. No rank/objective/search changes. SS-only benefit analysis does not invoke full-plan state tax. |
| Roth planning | Use explicit selected-state overloads; test fixed conversion and funded bracket fill. Preserve all same-owner/source-capacity behavior and RMD ordering. |
| Break-even | `BreakEvenAnalyzer` consumes supplied projection snapshots; after-tax estate/assets already reflect funded NC taxes. It performs no new projection or state tax. No change to SS-only break-even calculations. |
| Plan JSON and saved baselines | `PlanningAssumptions` persists state assumptions; omitted legacy state defaults to MI; explicit invalid/null data is guarded. Test NC textual-code round trips and old MI/AZ documents. `RetirementPlanSnapshot` retains object references; do not silently change baseline isolation semantics. |
| Retained projection/history | `RetainedProjectionSnapshot.State` captures jurisdiction, status, numeric result, reasons and limitations. Restore/export saved NC data without recalculation or replacement by today's rules. Current-state changes must not relabel old results. |
| Analyzer freshness | `AnalysisFingerprintService` includes full-plan planning assumptions in integrated analyses; MC has its own captured-plan fingerprints. State edits must invalidate full-plan results appropriately, while SS-only financial independence remains. |
| Rule-version freshness gap | `AnalysisVersions.references()` hashes federal/state research/Iowa resources; compiled Arizona parameters are not a dedicated reference hash. A compiled NC schedule likewise would not automatically fingerprint future rule edits. Approve an NC-scoped use of existing algorithm/reference-version infrastructure before later NC rule updates can be presented as fresh. A version string in a tooltip alone is not a freshness key. No broad history migration/refactor is justified for initial admission. |
| UI assumptions/results | Add NC help in `AssumptionsView`; frozen annual/range notes in `ResultsSummaryView` and `ProjectionYearDetailsPane`; NC rows in `SocialSecurityAnalyzerInputSummary`. Backend strings alone are insufficient. |
| Individual/MC report metadata | Extend `IndividualReportContext`, `MonteCarloRunService`, and `MonteCarloStrategyComparisonRunService` to capture NC assumptions at calculation time, including different-state comparisons. |
| PDF/CSV | `ProjectionPdfExporter`/`ProjectionCsvExporter` already export generic annual state tax, but IA/AZ limitation paths are explicit. Add NC limitations and future-rate explanation to normal and non-investable CSV branches/PDF; preserve other-state output and frozen history. Individual/MC exports must retain captured notes. |

## 11. Proposed independently calculated golden tests

**None added or run during this audit.** Expected values below were calculated independently with decimal arithmetic, not by invoking production calculators.

### 11.1 Worksheet basis and monetary convention

Use [NCDOR NC-40 worksheet, page 2 of instructions / PDF page 3](https://www.ncdor.gov/individual-estimated-income-tax/open#page=3): federal AGI, additions, deductions, state standard deduction, taxable income and amount of tax. For future rates, substitute enacted §44.1 rates; the worksheet's older “2026 onward” 3.99% label cannot override later law. Withholding, payments and estimated-payment thresholds do not reduce modeled annual liability.

**Proposed planning convention:** exact intermediates and final HALF_UP cents, consistent with existing state estimates and funding. The [2025 D-401, page 4](https://www.ncdor.gov/2025-d-401-individual-income-tax-instructions/open#page=4) instead instructs whole-dollar return rounding. The cents below are therefore independent **planning goldens**, not claims of exact filed-return dollars. Review 2026 instructions when available; do not make tax-return rounding a hidden source of funding discontinuity.

### 11.2 Direct state-calculator fixtures

Unless stated otherwise: 2026, no special adjustments, adequate separate cash to avoid changing the stated annual income through tax funding. AGI/taxable-SS consistency must be established independently in the fixture.

| Fixture | Worksheet taxable base | Expected NC estimate |
|---|---:|---:|
| Single: SS $30,000 only; taxable SS $0 | $0 | **$0.00** |
| Single: IRA $12,749 / $12,750 / $12,751 | $0 / $0 / $1 | **$0.00 / $0.00 / $0.04** |
| MFJ: IRA $25,500 / $25,501 | $0 / $1 | **$0.00 / $0.04** |
| Single: IRA $50,000 | $37,250 | **$1,486.28** |
| MFJ: IRA $60,000 + SS $40,000; taxable SS $34,000, AGI $94,000 | $34,500 | **$1,376.55** |
| Single: IRA $50,000 + conversion $20,000 | $57,250 | **$2,284.28** |
| Single: pension $45,000 + SS $25,000; taxable SS $21,250, AGI $66,250 | $32,250 | **$1,286.78** |
| Single: annuity gross $30,000, independently established taxable receipt $18,000 | $5,250 | **$209.48** |
| Single: brokerage proceeds $100,000, no modeled income | $0 | **$0.00**, calculated |
| Same brokerage proceeds + IRA $50,000 | $37,250 | **$1,486.28** |
| Same proceeds + IRA $50,000 + already-modeled investment income/gain $5,000 | $42,250 | **$1,685.78**, no second gain addition |
| Same $5,000 modeled total described as an assessed taxable proceeds portion | $42,250 | **$1,685.78**, same income once |
| Qualified Roth cash $40,000, no modeled income | $0 | **$0.00** |
| Single: ordinary IRA $20,000 + RMD $10,000 + conversion $15,000 | $32,250 | **$1,286.78** |
| Same total with RMD split opening $5,000/current $5,000 | $32,250 | **$1,286.78**, opening cash not received twice |
| Single: ordinary taxable income $60,000, age 64 versus 65; also 2028 versus 2029 at the same 3.49% rate | 2026 $47,250; 2028/29 $47,250 | **$1,885.28** in 2026; **$1,649.03** in each of 2028/29; no NC senior sunset |

Traditional IRA, rollover IRA, traditional 401(k) and 403(b) fixtures with identical included income should give the same state tax. Preserve existing qualified-Roth/full-taxable-traditional assumptions; do not claim these tests establish after-tax basis.

### 11.3 Future rates, survivors and lifetime arithmetic

For MFJ with $100,000 included ordinary income and no taxable SS, the base is $74,500:

| Years tested | Independent multiplication | Expected |
|---|---|---:|
| 2026 | $74,500 × .0399 | **$2,972.55** |
| 2027 and 2029 | $74,500 × .0349 | **$2,600.05** |
| 2030 and 2032 | $74,500 × .0324 | **$2,413.80** |
| 2033, 2034, 2035, 2040 and 2041 without established triggers | $74,500 × .0299 | **$2,227.55** |

Constant-income, zero-inflation/return 2026–2034 fixture: **lifetime NC $22,469.20** = 2,972.55 + 3 × 2,600.05 + 3 × 2,413.80 + 2 × 2,227.55. Supply sufficient pension/cash for taxes so funding does not alter that income. Independently sum annual state taxes; separately verify combined lifetime taxes.

Death in 2027, fixed $70,000 ordinary income in both 2027/2028: death-year MFJ gives `(70,000 − 25,500) × .0349` = **$1,553.05**; next-year Single gives `(70,000 − 12,750) × .0349` = **$1,998.03**. Also test the opposite spouse surviving and actual survivor SS/pension amounts. Fixed-income arithmetic isolates the deduction change; it does not assert survivor income stays unchanged in real plans.

### 11.4 Independent tax-funding and Roth bracket-fill fixtures

Use an ordinary Single filer age 60 in 2026, no SS, ordinary pretax assets and same-owner Roth capacity. [IRS 2026 Form 1040-ES](https://www.irs.gov/pub/irs-pdf/f1040es.pdf) supplies the $16,100 basic federal deduction and rate schedule. No federal senior deduction is involved.

- **State-only funding feedback:** $15,000 IRA-funded expenses, no spare household cash, tax funding entirely traditional. Federal tax remains zero. Solve `T = .0399 × (15,000 + T − 12,750)`: unrounded T = **93.5058848036…**, final **$93.51** funding and NC tax; final modeled AGI **$15,093.51**. With sufficient cash funding instead, NC tax is **$89.78** and no additional traditional withdrawal is required.
- **Floor crossing with conversion:** $12,250 ordinary IRA spending and $1,000 conversion, taxes funded from traditional assets. Before conversion state tax is zero. Solve `T = .0399 × (500 + T)`: **$20.78** funding/state tax, not zero or rate × the entire conversion. Federal tax remains zero.
- **State-aware bracket fill:** $15,000 existing IRA spending, custom federal taxable-income target $5,000, no spare cash, all tax funding traditional. Target AGI is **$21,100**. Independent federal tax is **$500.00**, NC tax `(21,100 − 12,750) × .0399` is **$333.17**. Combined funding is **$833.17**; conversion is **$5,266.83**. With taxes funded by available cash instead, conversion can be **$6,100.00**. Verify the iterative result within its existing cent tolerance and execute/cap it through actual same-owner accounts.

Add focused 12%/22%/24% boundary cases from published federal worksheets, selected-SS interaction, finite conversion capacity and an enacted-rate-boundary run. Expected values must be written independently before changing domain arithmetic. No fixed marginal-rate replacement is proposed.

### 11.5 Unsupported, persistence, reporting and bounded integration tests

- Unknown taxable retirement amount; positive taxable Roth receipt outside qualified scope; wrong attribution year; inconsistent federal AGI/SS; $5,000 explicitly taxable investment attribution with no modeled investment income; duplicated/excess attribution; unclassified `OTHER` and explicit exempt/special-source facts: unavailable or upstream validation, never calculated zero.
- Ordinary brokerage unknown basis and unknown gain metadata remain admitted. A display name suggesting government/Bailey status must not establish eligibility. Current DTOs cannot encode an actual Bailey classification: do not fabricate a passing automatic-detection test. Test represented unsupported classifications and document this representability limit.
- NC plan/current and saved-baseline round trips; omitted-state legacy MI behavior; retained NC state results/reasons/limitations; no replacement of older frozen notes on load/export.
- Normal and non-investable CSV/PDF, annual details/range help, shared analyzer assumptions, individual and MC captured reports. Change the active plan's state after saving a result and confirm the old export remains unchanged.
- Real 2027 brokerage principal/mixed/RMD/conversion projections; both tax-funded and household-cash-funded cases; no double counting, lost cash or mutated actual balances.
- Two controlled MC paths (for example +4% and −2%) against deterministic runs with identical contexts/paths. Compare annual state/federal tax, AGI, withdrawals, conversion and ending assets, and snapshot the unchanged source plan. Equality alone is not a tax-law oracle; retain the independent state goldens above.
- One small integrated baseline/candidate comparison, bounded longevity scenarios including a rate boundary and opening death, and observational break-even checks. Preserve admission/progress/cancellation behavior; no exhaustive strategy or MC search is needed.

Preserve and rerun existing `ProductionStateTaxServiceTest`, `StateTaxUnavailableContractTest`, `StateTaxGoldenFixturesTest`, Michigan tax tests, Illinois/Pennsylvania/Iowa production tests, all Arizona tax/brokerage regressions, `StateTaxFundingFeedbackTest`, and applicable annuity tests. Do not alter their financial expected values. If a conflicting existing expectation represents a different intended financial rule, stop and explain it rather than changing semantics to pass.

## 12. Supported limitations and required user-visible disclosures

**Proposed disclosure text/content**, captured with calculated results and shown in existing assumptions, tooltips and exports:

1. “North Carolina full-year domestic Single/MFJ retirement-planning estimate. Uses modeled federal AGI, subtracts federally taxable Social Security, and applies North Carolina's own $12,750/$25,500 standard deduction. Ordinary private pensions, fully taxable traditional distributions and taxable Roth conversions remain included.”
2. “Brokerage proceeds are cash flow, not automatically taxable income. Uses taxable investment income already modeled. Unmodeled realized gains may make actual taxes higher; security lots, capital losses/carryovers and special bond/state-basis adjustments are not modeled.”
3. “Uses enacted scheduled rates: 3.99% in 2026, 3.49% in 2027–2029, 3.24% in 2030–2032 and 2.99% from 2033. Conditional 2035–2040 reductions are not assumed. State standard deductions remain nominal under reviewed law; future legislative changes require updated rules.”
4. “Bailey-qualified government benefits, eligible military/railroad benefits and special rollover/conversion exclusions require separate consideration. Pension names do not establish eligibility. This estimate assumes ordinary taxable private pensions and does not determine historical government-plan qualification.”
5. “Standard-deduction estimate; assumes eligible nondependent filers. Itemization, dependent/child benefits, specialized credits, business/portable-benefit adjustments, part-year residence and multistate sourcing/credits are outside scope. Assumes fully taxable traditional and qualified Roth withdrawals. Federal below-AGI deductions are not North Carolina deductions; existing federal-model omissions can affect combined outcomes. Final cents may differ from whole-dollar return rounding.”

These are modeling boundaries, not assertions that excluded deductions/credits have zero legal value. Itemization/special exclusions can make actual state tax lower; omitted gains can make it higher. Future-rate assumptions can change long-horizon conversions and estate comparisons. No claim of tax-preparation precision, automatic pension classification, or complete federal capital-gain treatment is appropriate.

Preserve frozen notes on saved results. Avoid a renderer that takes only three limitation paragraphs and accidentally hides Bailey or future-rate disclosures: arrange all required content deliberately in the existing UI/report pattern, with full annual detail available. Displayed rule version and each year's applied rate/deduction/base should be retained as annual metadata/limitations without introducing a new persisted tax-detail hierarchy.

## 13. Proposed Phase 6E-D implementation plan and release gates

**Proposed sequence, only after approval:**

1. Confirm the bounded policies in section 14. Recheck relevant enactments/forms at implementation time; preserve this report's audit-date provenance.
2. Add focused independent domain tests first, then the small NC calculator/rules component. Validate floors, filing status, SS subtraction, admitted receipts, brokerage reconciliation and all four year periods. Keep other-state/federal arithmetic untouched.
3. Wire approved NC jurisdiction/routing and existing conditional/annuity admission paths. Use selected-state funding/bracket fill; no special NC logic in JavaFX or `ProjectionEngine`.
4. Add the explicitly identified disclosures, frozen report context, CSV/PDF handling and history/freshness acceptance checks. Resolve the NC rule-version freshness requirement using existing infrastructure without rewriting historical artifacts.
5. Run focused domain tests, bounded deterministic/MC/integrated/report/persistence acceptance and existing state goldens. Then run the full Maven test suite as practical under the implementation task's verification requirements; do not certify completion if required checks fail. Do not launch unrelated performance/exhaustive benchmarks merely for this audit.
6. Review the complete diff and report exact files/test results. Native desktop smoke-test state selection, ordinary pension/IRA/SS/conversion and real brokerage plans, visible assumptions, save/reload and exports. No stage/commit/push unless separately requested.

Use the required IntelliJ bundled Maven and project-local repository for later verification:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-Dtest=NorthCarolina*Test,ProductionStateTaxServiceTest,StateTaxGoldenFixturesTest,StateTaxFundingFeedbackTest,Arizona*Test,IllinoisProductionTaxCalculatorTest,PennsylvaniaProductionTaxCalculatorTest,IowaProductionTaxCalculatorTest' test
```

This is a proposed command, **not executed in this audit**. Use the same executable/repository for the approved broader/full checks; no dependency, Java-version or build-configuration changes are needed.

**Release gates:** approved scope; reviewed versioned rules; no brokerage-basis blanket refusal; accurate funded conversions and survivor/year boundaries; annuity gates covered if advertised; all required visible/frozen disclosures; persistence/history/freshness handling; passing financial golden and bounded integration checks. Final 2026 booklet retrieval is a follow-up verification item, not a reason to invent provisional values or silently copy federal deductions.

## 14. Explicit user approval decisions

No implementation approval is implied by this report. Before Phase 6E-D, obtain decisions on:

1. **Authorize Phase 6E-D implementation/admission** of NC as the 14th production state within the existing architecture; no Kentucky/Colorado work.
2. **Approve the ordinary full-year Single/MFJ scope** and existing fully taxable traditional/qualified Roth assumptions, including existing supported annuity receipts with narrow allowlist plumbing and unchanged NIIT guards.
3. **Approve the enacted year schedule and conditional-rate policy:** all scheduled reductions; no untriggered 2035+ reductions; constant statutory NC deductions unless law changes. No inflation forecast applied to those deductions.
4. **Approve Bailey/military/railroad handling:** ordinary private-pension assumption, prominent exclusions, guards only on represented contrary facts, no name inference and no new classification UI. Acknowledge that generic pension data cannot automatically detect these special cases.
5. **Approve B3-style brokerage admission** with no new tax-lot inputs, no invented gains/principal taxation, explicit reconciliation and disclosure. More complete federal/state gain accounting would be a separately approved task.
6. **Approve exact-intermediate/final-cent planning rounding** and the standard-deduction-only/special-adjustment disclosures. This intentionally differs from return whole-dollar rounding.
7. **Approve a narrowly scoped NC rule-version freshness solution** using existing metadata/version infrastructure, preserving saved artifacts. Initial NC selection is already part of effective inputs; future compiled rule changes must not silently appear fresh. No broad analyzer/history refactor is requested.

No additional common-retirement tax input fields are recommended. No further financial-model change outside these decisions is authorized.

## Verification record and stop point

Initial `git status --short`: clean. Inspected the requested production classes, related attribution/admission/projection/persistence/report paths, three Arizona reports and the two B3 brokerage regression references. Researched official NCGA/NCDOR sources and limited IRS sources for federal inclusion/independent funding fixtures. The report's illustrative arithmetic was checked with decimal operations without calling production tax code.

Only `PHASE-6E-C-NORTH-CAROLINA-TAX-AUDIT.md` was created. Production code, tests, rules, registration and existing reports remain unchanged. No Maven tests were run for this documentation-only audit; no runtime certification is claimed. Final `git status` confirmed only this untracked report; `git diff --check` passed. A separate no-index check of the new file produced no whitespace diagnostics (its difference exit code reflects the added file). No staging, commits or pushes.

**Stop here. Phase 6E-D has not begun.**
