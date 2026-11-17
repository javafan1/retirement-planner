# Aledrix — Phase 6E-L Oklahoma and Mississippi implementation

Implementation/legal recheck date: **2026-11-17**. Governing plan: the approved [Phase 6E-K audit](PHASE-6E-K-OKLAHOMA-MISSISSIPPI-TAX-AUDIT.md), especially Sections 16 and 20. Ordinary retirement-planning scope; independent production gates.

## 1. Executive summary and separate admission status

Independent Oklahoma and Mississippi calculators, immutable versioned rules and state-local source helpers are implemented. Each uses the existing annual state-tax/funding boundary. No federal calculation, other-state tax calculator, projection arithmetic, account execution semantics, persistence schema, dependency or build setting changed. Colorado remains absent from the production registry.

**Oklahoma: admitted as the 18th production jurisdiction. Mississippi: independently admitted as the 19th.** Each state's approved ordinary subset passed its financial, source, funding, integration, reporting, persistence and freshness gates. Final-source bounded verification passed 5,225 tests with no failures/errors and 35 existing skips. Native interactive review and Git publication remain user-controlled.

The source-confirmed registry now contains AK, AZ, FL, GA, IA, IL, KY, MI, MS, NC, NH, NV, OK, PA, SD, TN, TX, VA and WY: eleven taxable-state models plus eight no-general-income-tax jurisdictions. Colorado remains unregistered. No outstanding legal or financial blocker remains within either approved ordinary subset; the explicit unsupported boundaries in Section 12 remain enforced.

Focused acceptance has passed, including real brokerage spending, actual conversions/RMDs, per-person ownership, mathematical funding oracles, survivor projections, controlled Monte Carlo, integrated/longevity evaluation, plan round trips, frozen exports and rules freshness. The source-confirmed opening baseline was 17. The opening working tree contained only the untracked Phase 6E-K audit; it is preserved unchanged.

## 2. Legal verification, sources and rules versions

Distinctions retained: enacted law establishes schedules and exclusions; official instructions establish filing mechanics; Oklahoma IRA-conversion eligibility is the approved reasoned statutory application; ordinary private-plan qualification and missing-brokerage-gain treatment are disclosed planning assumptions. An unsupported source never becomes calculated zero.

| State | Verified rule / implementation | Authority rechecked 2026-11-17 |
|---|---|---|
| Oklahoma | 2026 Single zero/2.5%/3.5%/4.5% bands at 3,750/4,900/7,200; MFJ boundaries doubled | [Enrolled HB 2764, §2](https://www.oklegislature.gov/cf_pdf/2025-26%20ENR/hB/HB2764%20ENR.PDF) |
| Oklahoma | Conditional cuts require certification; no invented calendar elimination or automatic inflation trigger | [Enrolled HB 4072, including amended §34.103](https://www.oklegislature.gov/cf_pdf/2025-26%20ENR/hB/HB4072%20ENR.PDF), [2026 legislative summary](https://oklahoma.gov/content/dam/ok/en/tax/documents/resources/publications/legislation/2026LegislativeUpdate.pdf) |
| Oklahoma | Standard deduction 6,350/12,700; 1,000 ordinary filer exemption; income-tested 1,000 senior exemption and IRA-conversion adjustment | [2026 Form 511 developer packet, pp. 8–9](https://oklahoma.gov/content/dam/ok/en/tax/documents/forms/software-developers/Dev511-Pkt-26.pdf) |
| Oklahoma | Per-person 10,000 pool; §408 distribution definition; government coordination and separately qualifying benefits | [Official §2358 publisher](https://govt.westlaw.com/okjc/Document/ND6B5D5114F1E11F19208D31E62F66B8C?viewType=FullText), [OTC retirement guidance](https://oklahoma.gov/tax/helpcenter/income-tax.html), [OAC Chapter 50, particularly -30/-49](https://oklahoma.gov/content/dam/ok/en/tax/documents/resources/rules-and-policies/agency-rules/2025/Chapter_50-Income_Tax-2025.pdf) |
| Mississippi | 2026 4%; unconditional 2027 3.75%, 2028 3.50%, 2029 3.25%, 2030 3%; applicable 10,000 zero band | [Enrolled HB 1, §§1–2](https://billstatus.ls.state.ms.us/documents/2025/pdf/HB/0001-0099/HB0001SG.pdf), [DOR dated general-information table](https://www.dor.ms.gov/general-information) |
| Mississippi | Own net-income/filing framework, deduction 2,300/4,600; exemption 6,000/12,000 plus 1,500 qualifying senior; actual married columns | [Official Form 80-100 instructions](https://www.dor.ms.gov/sites/default/files/tax-forms/individual/80100251%202.pdf), [Form 80-105](https://www.dor.ms.gov/sites/default/files/tax-forms/individual/80105258%201.pdf) |
| Mississippi | General qualifying retirement payments, early-payment exclusion from relief, beneficiary extension | [SOS Title 35, Part III, Subpart 2, Chapter 07](https://www.sos.ms.gov/adminsearch/ACCode/00000158c.pdf) |
| Mississippi | Separate traditional IRA-to-Roth IRA conversion exclusion, §27-7-15(4)(ff) | [Official enrolled 2022 HB 1529, retained clause](https://billstatus.ls.state.ms.us/documents/2022/html/HB/1500-1599/HB1529SG.htm), [2026 existing-law recitation](https://billstatus.ls.state.ms.us/documents/2026/html/HB/1000-1099/HB1072PS.htm), [DOR-authored 2011 enactment notice, archived p. 1](https://taxsim.nber.org/historical_state_tax_forms/MS/2011/indiv_8010011i.pdf) |
| Mississippi | Subsequent enactment review; no identified replacement of this ordinary schedule/conversion clause | [DOR 2026 legislation](https://www.dor.ms.gov/forms-resources/laws-regulations/2026-legislation) |

The Form 511 packet is explicitly a 2026 software-developer publication; Mississippi return mechanics above come from the available 2025 instructions, with rates taken from governing dated law. Stale Mississippi FAQ prose showing 4.4% is not a 2026 constant. Restricted/intermittent access to older official codification/enrollment endpoints is disclosed in the accepted audit; indexed official text and the primary DOR enactment notice corroborate clause (ff). House-passed 2026 proposals are not treated as enacted additions.

Rules: **`OK-2026-1`**, key **`oklahoma-income-tax-rules`**; **`MS-2026-1`**, key **`mississippi-income-tax-rules`**. Parameters remain nominal unless reviewed changes are incorporated. Oklahoma retains its reviewed 2026 bands; a verified certified step would lower every positive band by 0.25 percentage point, not just the highest rate. The first December 2026/February 2027 certification sequence cannot establish a step before the following January 2028 effective date. No step is assumed here. Mississippi implements each fixed step and holds 3% after 2030. Neither fiscal trigger is forecast. Mississippi's unusual literal conditional-percentage scale must be reviewed authoritatively before incorporating a triggered reduction; this does not alter the unconditional schedule.

## 3. Exact files added and modified

The manifest below enumerates every intentional source/test change. It excludes the unchanged opening audit. No resources, federal tax classes, existing state calculators, `ProjectionEngine`, persistence schemas or build configuration were modified. The implementation report itself is one additional new file.

### Production source

Added (8):

```text
src/main/java/com/daviddunn/retirementplanner/domain/tax/state/MississippiProductionTaxCalculator.java
src/main/java/com/daviddunn/retirementplanner/domain/tax/state/OklahomaProductionTaxCalculator.java
src/main/java/com/daviddunn/retirementplanner/domain/tax/state/mississippi/MississippiInvestmentReconciliation.java
src/main/java/com/daviddunn/retirementplanner/domain/tax/state/mississippi/MississippiMarriedWorksheet.java
src/main/java/com/daviddunn/retirementplanner/domain/tax/state/mississippi/MississippiRetirementEligibility.java
src/main/java/com/daviddunn/retirementplanner/domain/tax/state/mississippi/MississippiTaxRules.java
src/main/java/com/daviddunn/retirementplanner/domain/tax/state/oklahoma/OklahomaInvestmentReconciliation.java
src/main/java/com/daviddunn/retirementplanner/domain/tax/state/oklahoma/OklahomaTaxRules.java
```

Modified (17):

```text
src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionCsvExporter.java
src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AcceptedAnalysisCompletion.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisVersions.java
src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitAdmission.java
src/main/java/com/daviddunn/retirementplanner/domain/benefit/NonqualifiedAnnuityAdmission.java
src/main/java/com/daviddunn/retirementplanner/domain/model/StateTaxJurisdiction.java
src/main/java/com/daviddunn/retirementplanner/domain/roth/RothConversionBracketFillCalculator.java
src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ProductionStateTaxService.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRunService.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonRunService.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualReportContext.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAnalyzerInputSummary.java
src/main/java/com/daviddunn/retirementplanner/ui/summary/ProjectionYearDetailsPane.java
src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java
src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java
```

### Tests and test fixtures

Added (27):

```text
src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MississippiBrokerageMonteCarloRegressionTest.java
src/test/java/com/daviddunn/retirementplanner/app/montecarlo/OklahomaBrokerageMonteCarloRegressionTest.java
src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/MississippiIntegratedAcceptanceTest.java
src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/OklahomaIntegratedAcceptanceTest.java
src/test/java/com/daviddunn/retirementplanner/domain/projection/MississippiBrokerageProjectionRegressionTest.java
src/test/java/com/daviddunn/retirementplanner/domain/projection/MississippiFundingTest.java
src/test/java/com/daviddunn/retirementplanner/domain/projection/MississippiProjectionAcceptanceTest.java
src/test/java/com/daviddunn/retirementplanner/domain/projection/MississippiReportingAcceptanceTest.java
src/test/java/com/daviddunn/retirementplanner/domain/projection/OklahomaBrokerageProjectionRegressionTest.java
src/test/java/com/daviddunn/retirementplanner/domain/projection/OklahomaFundingTest.java
src/test/java/com/daviddunn/retirementplanner/domain/projection/OklahomaMississippiAnnualScheduleTest.java
src/test/java/com/daviddunn/retirementplanner/domain/projection/OklahomaMississippiFundingBoundaryTest.java
src/test/java/com/daviddunn/retirementplanner/domain/projection/OklahomaProjectionAcceptanceTest.java
src/test/java/com/daviddunn/retirementplanner/domain/projection/OklahomaReportingAcceptanceTest.java
src/test/java/com/daviddunn/retirementplanner/domain/tax/OklahomaMississippiConditionalFixture.java
src/test/java/com/daviddunn/retirementplanner/domain/tax/OklahomaMississippiInvestmentReconciliationTest.java
src/test/java/com/daviddunn/retirementplanner/domain/tax/OklahomaMississippiSourceAdmissionTest.java
src/test/java/com/daviddunn/retirementplanner/domain/tax/state/MississippiProductionTaxCalculatorTest.java
src/test/java/com/daviddunn/retirementplanner/domain/tax/state/OklahomaMississippiAuditCompletionTest.java
src/test/java/com/daviddunn/retirementplanner/domain/tax/state/OklahomaMississippiQualificationBoundaryTest.java
src/test/java/com/daviddunn/retirementplanner/domain/tax/state/OklahomaProductionTaxCalculatorTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/MississippiFreshnessTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/MississippiInputReportTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/OklahomaFreshnessTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/OklahomaInputReportTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/MississippiUiAcceptanceTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/OklahomaUiAcceptanceTest.java
```

Modified (11):

```text
src/test/java/com/daviddunn/retirementplanner/domain/projection/PennsylvaniaProductionProjectionTest.java
src/test/java/com/daviddunn/retirementplanner/domain/tax/state/NoIncomeTaxAdmissionTest.java
src/test/java/com/daviddunn/retirementplanner/domain/tax/state/colorado/ColoradoWorksheetCompletionTest.java
src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/IowaMississippiAdmissionWorksheetTest.java
src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/PennsylvaniaParticipantFactsAuditTest.java
src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/PennsylvaniaProductionAdmissionAuditTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/ArizonaUiAcceptanceTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/GeorgiaUiAcceptanceTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/KentuckyUiAcceptanceTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/NorthCarolinaUiAcceptanceTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/VirginiaUiAcceptanceTest.java
```

Added report: `PHASE-6E-L-OKLAHOMA-MISSISSIPPI-IMPLEMENTATION.md`.

Existing test edits change selector/production-membership assertions from 17 to 19 and remove Mississippi from disabled-state lists. The historical research catalog remains independent. The existing all-jurisdiction PA bracket-fill worksheet gains only the independently calculated **new Oklahoma** expected value 9,421.00; all previous-state amounts remain unchanged. Colorado's worksheet test changes its registry count while retaining every disabled/future-rule refusal assertion.

## 4. Oklahoma rules, exclusions and conversion findings

The calculator reconciles modeled AGI and federally taxable SS against annual scalar income, then validates source totals without adding attribution as income. It aggregates qualifying receipts by active PRIMARY/SPOUSE recipient, caps each at 10,000, applies the state standard deduction and filer/senior exemptions, floors taxable income and applies statutory marginal slices. Exact decimal intermediates, final `HALF_UP` cents; small differences from return-table/whole-dollar filing are disclosed.

Traditional/rollover IRA, ordinary qualified 401(k)/403(b), opening/current RMDs, fully taxable ordinary private pensions and completed own-IRA conversions share that person's pool. Qualified Roth cash is already outside AGI. No general age-59½ gate applies to the pool. The senior exemption uses 15,000 Single/25,000 MFJ AGI ceilings after removing admitted IRA conversion income **solely for that eligibility test**. January 1 next-year birthdays follow the verified Oklahoma convention; dead filers do not age into later eligibility.

The conversion conclusion applies the statute's broad §408 distribution/withdrawal definition with the [IRS source-distribution characterization](https://www.irs.gov/publications/p590a). It is not an OTC conversion-specific ruling. Source and same-owner Roth-IRA destination must actually establish the completed transaction. Employer-to-Roth/in-plan, inherited and partial-basis variants are refused in the initial subset.

Ordinary private-qualified-plan assumptions permit pensions without a government-service screen. Pension names do not establish classification. Explicitly unclassified income (`OTHER`), or reliably represented special/conflicting facts, refuses; missing government/private classification alone does not reject an ordinary pension. Capped A5 government benefits coordinate with A6; full qualifying CSRS/military/railroad relief is not guessed. Government fixtures remain legal coordination/source-boundary worksheets, not promises of comprehensive government support. A commercial nonqualified annuity retains only its already-modeled taxable fraction and receives no retirement-pool exclusion; recovered investment is not taxed again.

## 5. Mississippi qualification, conversion and married columns

Mississippi defines its own net income. The ordinary bridge reconciles modeled federal AGI by removing included SS, independently qualifying retirement receipts and separately exempt executed IRA conversions once; federal basic/additional-age/enhanced-senior below-AGI deductions are not imported.

`MississippiRetirementEligibility` checks own-IRA cash against the modeled receipt interval, using DOB plus 59 years and six months. Already-qualified entire intervals are admitted; clearly early ordinary cash remains taxable; an interval straddling qualification without payment dates refuses. Projection-period and opening-receipt bounds remain distinct. No universal actual payment date is invented. Ordinary employer/private-plan assumptions respect adverse dated conditions; retirement qualification is not inferred merely from a federal penalty exception. Qualified survivor pensions follow original-plan evidence and current recipient without an invented beneficiary minimum age.

Final hardening distinguishes an employer plan whose dated retirement conditions are already met throughout the receipt interval from an own-IRA age crossing. The former is not refused merely because the unrelated ordinary IRA 59½ boundary occurs later that year. A focused test reproduced this new helper's overrestriction before its state-local ordering correction. Another initial test attempted to put participant facts on an IRA; the existing model correctly rejected that premise. The fixture was corrected to use ordinary IRA DOB/receipt evidence, and the employer-only input guard was preserved.

**Clause (ff) conversion relief is separate:** completed traditional/rollover IRA-to-Roth IRA conversions can be exempt before 59½. Retained/tax-funding cash must independently qualify. Requested/unexecuted income, RMD dollars and employer/in-plan variants are not reclassified as exempt IRA conversions. Nonqualified Roth/basis/inherited rules are not added.

The current generic commercial-annuity terms establish a taxable/basis receipt, not a qualifying retirement plan. Mississippi keeps that subset unsupported, including its earlier annuity admission guard. Ordinary qualifying plan-annuity payments represented as supported fully taxable pension/account receipts use that source's retirement rules. **Audit fixture reconciliation:** OK-22 and MS-21/22's independently specified qualified-annuity bases are checked as legal worksheets; the generic commercial/partial-pension representation cannot establish those exact qualification/basis premises. Tests explicitly refuse that representation rather than inserting a new field or granting a guessed exemption. The actual Oklahoma commercial-annuity fixture taxes 20,000 of 50,000 at **354.50**; the corresponding unverified Mississippi receipt refuses.

The narrow Mississippi senior day-before-birthday convention was not authoritatively established. Only a boundary where the alternate senior allowance changes calculated tax is unsupported: a January 1 birthday following year-end, or a January 2 birthday immediately after the modeled January 1 death. Ordinary DOBs and immaterial zero-tax cases remain admitted. This applies the same approved material-ambiguity policy at the actual age-testing date; it does not age a dead filer through the rest of the year.

For fully owned nonnegative MFJ state net income, `MississippiMarriedWorksheet` uses the official standard-subset equivalence:

`rate × max(max(N_primary − 10,000, 0) + max(N_spouse − 10,000, 0) − shared deduction/exemptions, 0)`.

No income or unused zero band transfers. Actual owner eligibility determines total additional senior exemptions, which official combined mechanics allow to be allocated. Unallocated ordinary investment income uses the approved **one-column joint fallback**, with possible-overstatement disclosure. Explicit JOINT evidence without individual shares remains unallocated; Georgia's 50/50 rule is not imported. Contradictory sources refuse instead of triggering the fallback. Single uses the uncontradicted sole-filer convention.

## 6. Source reconciliation and B3 behavior

Each state has a small local investment reconciler. Real `TaxIncomeCalculator` emits an unowned INTEREST placeholder for a modeled bucket; attached CAPITAL_GAIN/DIVIDEND can describe those same dollars without replacing that placeholder. The helpers reconcile that signature, owned interest replacement and explicit totals against the scalar once. Neither source metadata nor brokerage proceeds becomes a second income channel.

Actual unknown-basis brokerage withdrawals remain admissible. Missing taxable portions do not mean legally zero gain. Wrong-year, duplicate, excessive, overlapping gain/withdrawal or incompatible retirement/investment evidence refuses structurally. Existing federal NIIT/basis/Advanced-mode/conditional/growth guards remain. Oklahoma's commercial-annuity allowlist is extended; Mississippi's is deliberately not. Ordinary conditional taxable receipts may be admitted without being labeled retirement income.

## 7. Actual Roth execution, capacity and tax funding

Before shared bracket-fill modification, each new state's 1,000-capacity fixture failed with `StateTaxUnavailableException`: an oversized hypothetical conversion left an account-less/unowned taxable residual. Log: `target/phase6el-capacity-before.log` (two expected reproduction errors).

The only shared financial edit adds OK/MS to the established KY/GA **capacity-complete trial condition**. Every other jurisdiction retains its previous path; the preexisting NC capacity-evidence test still passes. Actual source/destination selection, owner order, RMD-before-conversion sequence, execution limits and shortfall remain unchanged. Funding remains iterative, rebuilding actual income/source evidence and selected state tax each trial. No fixed-rate production gross-up was introduced.

Independent funding tests cover exclusion exhaustion, cash versus traditional funding, modeled investment tax with exempt Mississippi funding, executable capacity and explicit federal-taxable-income targets. Account-backed projection tests cover actual versus requested conversion, both owners, missing primary destination, opening/current RMD and cash conservation. Real account balances remain unchanged.

Additional piecewise oracles pass: OK remaining $100 pool exhaustion gives funding **2,105.39**, federal **1,660.65**, state **444.74**; an OK senior AGI cutoff crossing gives funding/state **125.90**, with federal zero and added IRA cash still within its retirement pool. MS funding that exhausts a $1,000 exempt IRA then uses explicitly unqualified employer receipts gives funding **6,009.52**, federal **4,541.14**, state **1,468.38**. A conversion/SS fixture independently verifies that Oklahoma's senior test retains the incremental federally taxable SS even while removing IRA conversion income.

## 8. Independent goldens and financial results

Examples below assume the exact independent audit premises, with no unlisted incremental funding:

| Independent worksheet | Result verified |
|---|---:|
| OK Single IRA 50,000 | 1,254.50 |
| OK Single IRA 50,000 + IRA conversion 20,000, one pool | 2,154.50 |
| OK MFJ IRA 50,000 each / 100,000 one owner | 2,509.00 / 2,959.00 |
| OK senior investment 14,000 + IRA conversion 20,000, conversion-adjusted senior test | 489.50 |
| OK commercial annuity gross 50,000 / taxable 20,000 | 354.50 |
| OK five IRA-50,000 years under reviewed held schedule | 6,272.50 |
| MS Single investment 50,000, 2026/27/28/29/30 | 1,268.00 / 1,188.75 / 1,109.50 / 1,030.25 / 951.00 |
| MS same five annual investment incomes | 5,547.50 |
| MS MFJ owned investment 50,000 each / one owner 100,000, under 65 | 2,536.00 / 2,936.00 |
| MS MFJ two seniors, same known equal ownership / unallocated fallback | 2,416.00 / 2,816.00 |
| MS age-50 early IRA cash 50,000 / completed IRA conversion 50,000 | 1,268.00 / 0.00 |
| MS age-50 early cash 30,000 + completed conversion 20,000 | 468.00 |
| MS 2027 death-year MFJ one-owner investment 100,000, two already-65 filers / 2028 Single | 2,640.00 / 2,807.00 |
| Both: brokerage principal 100,000, no modeled taxable income | 0.00 with limitations |

OK-F1/F2 verify state 1,254.50 with cash funding versus **1,527.98** after traditional funding (total added withdrawal approximately **6,077.25**, within solver cent tolerance). OK-F3's explicit federal taxable-income target 40,000 yields conversion **41,100** with cash or **35,019** with traditional funding, state tax **1,529**.

MS-F1 verifies qualified-traditional funding approximately **4,340.91**, state zero. MS-F2 with investment 50,000 verifies approximately **5,781.82** funding, federal **4,513.82**, state **1,268.00**. MS-F3 conversion is **36,548** traditional-funded versus **41,100** cash-funded. MS-F4 adds investment 30,000 and verifies conversion **6,080** versus **11,100**, state **468.00**. Targets are explicit unit-test targets, not invented built-in bracket boundaries. Federal early-withdrawal penalties are not claimed covered by a state-only early-income worksheet.

Source, qualification and independent legal worksheets cover the remaining OK-01–28/G1–G4 and MS-01–32 boundary premises, including rounded marginal slices, unsupported rows and the qualified-annuity representation distinction above. No prior-state financial golden was changed to make these pass.

Fixture-premise correction for MS-30: the direct death-year worksheet now explicitly marks the deceased filer inactive and uses DOBs already age 65 at January 1 death, rather than a helper's year-end age-65 birthday later that year. Its 2,640.00/2,807.00 expectations remain unchanged. A deceased filer who would turn 65 only after death receives no new senior exemption; the separate 2026 one-senior joint worksheet is 2,876.00. This changes no production death convention or prior-state value.

## 9. Deterministic, Monte Carlo and integrated acceptance

Separate suites exercise Single/MFJ account-backed RMDs/conversions, real principal-only/mixed/conversion brokerage spending, tax funding, source ownership, actual execution/shortfall, unchanged live balances and annual/lifetime sums. Different-age spouses and both pension survivor directions use the current recipient and annual filing status.

Each state's three brokerage scenarios run two controlled **+4%/−2%** paths against matching deterministic economic paths, comparing annual state/federal taxes, withdrawals, conversions, ending assets and cash conservation. Each state also has one bounded integrated Social Security comparison and two longevity scenarios; exact second-death-minus-one-day estate/horizon and unchanged input plan are asserted. No large random benchmark or exhaustive search was used for new-state acceptance.

## 10. Persistence, frozen history, reports and freshness

Single/MFJ JSON save/reload, scenario copies and saved baselines retain selected state and financial rows. Normal and retained Single/MFJ CSV/PDF tests verify tax/state identity and limitations. Completed MC report metadata remains frozen after the active plan moves to Florida. Retained projection tests replace notes with original historical notes and prove export does not inject today's policy or change stored taxes.

New independent version keys are captured from frozen full-plan inputs using the existing NC/KY/GA/VA mechanism, including either side of a paired MC calculation. Changed versions mark only associated financial analyses outdated. Historical references/payload/context/amounts remain intact; SS-only and unrelated-state references remain unchanged.

All material limitations are included in help, summary, annual/range tooltips, analyzer inputs, individual/MC contexts and exports. Oklahoma has six full planning paragraphs and Mississippi seven, plus annual calculation notes. No truncation to a generic short warning and no active-editor substitution for old result assumptions.

## 11. Test execution and bounded existing-state regression

Java **25.0.4.1**, IntelliJ bundled Maven and `.codex-m2/repository` used. Local ignored `target/` logs:

| Run | Result |
|---|---|
| `phase6el-domain.log` | 57 direct worksheet tests; 0 failures/errors/skips |
| `phase6el-capacity-before.log` | Expected defect reproduction: 2 errors, unowned hypothetical residual, before shared condition edit |
| `phase6el-focused-domain.log` | 87 tests; 0 failures/errors/skips, including funding and unchanged NC/KY/GA references |
| `phase6el-focused-integration.log` | 158 tests; 7 failures/0 errors/0 skips, isolated to new CSV headers and copied disclosure-text assertions; corrected in scope |
| `phase6el-focused-final.log` | 165 tests; 0 failures/errors/skips |
| `phase6el-acceptance.log` | **426 tests; 0 failures, 0 errors, 4 skips; BUILD SUCCESS** |
| `phase6el-bounded.log` | **5,219 tests; 3 failures, 0 errors, 35 skips**. Two obsolete PA registry-list assertions and one unchanged SS cancellation thread-liveness assertion; details below. |
| `phase6el-qualification-before.log` | 13 tests; one new fixture-construction error (IRA participant facts are prohibited); new funding oracles and unchanged cancellation test passed. Fixture corrected without relaxing the model guard. |
| `phase6el-employer-before.log` | One expected failure reproducing the new MS employer-qualification ordering overrestriction, before state-local correction. |
| `phase6el-final-hardening.log` | **164 tests; 0 failures, 0 errors, 0 skips; BUILD SUCCESS**. Includes all new states, extra boundary oracles, unchanged cancellation test, updated PA registry assertion and NC/KY/GA funding references. |
| `phase6el-bounded-final.log` | **5,224 tests; 0 failures, 0 errors, 35 skips; BUILD SUCCESS**. Completed before the last material senior-birthday guard/test. |
| `phase6el-final-boundary.log` | **165 tests; 0 failures, 0 errors, 0 skips; BUILD SUCCESS**. Final source, including the material senior boundary at modeled January 1 death and the explicit MS-30 death-year fixture premise. |
| `phase6el-bounded-verified.log` | **5,225 tests; 0 failures, 0 errors, 35 skips; BUILD SUCCESS**. Final source; 24:52 elapsed, completed 2026-11-17 12:35:34 EST. |
| `phase6el-native-exit.log` | **57 direct calculator tests; 0 failures, 0 errors, 0 skips; BUILD SUCCESS; explicit native Maven exit 0**. Closing shell-status verification, no source changes. |

The four acceptance skips are unchanged historical research worksheets MS11/MS12/MS21/MS27, not skipped new production conversion tests. Their old pending flags do not override the express conversion statute and passing new production fixtures; the employer/conditional-future cases remain outside implemented scope. Automated JavaFX selector/annual/summary checks ran natively; skips are not represented as manual interactive acceptance.

The bounded run's 35 existing skips also include opt-in visual previews, external/developer-plan and fixture-dependent checks (including mortality and legacy MC snapshot/PDF cases). No new Oklahoma/Mississippi acceptance test is skipped. These unchanged skips are reported, not counted as successful desktop review; Section 13 remains the manual acceptance checklist.

The initial bounded cancellation failure was `Stage5DCancellationTest` line 66, its final `Thread.isAlive` assertion after coordinator termination. It passed unchanged in isolated recheck, focused runs and both clean bounded reruns. No Social Security cancellation code or assertion was modified. The two Pennsylvania failures were only the hard-coded 17-state alphabetical list in `PennsylvaniaParticipantFactsAuditTest`; its financial/qualification assertions remain intact. The final-source bounded verification additionally covers the last senior-birthday guard.

Shell-status distinction: the final bounded log reports Maven `BUILD SUCCESS` and the totals above, but its Windows PowerShell wrapper returned 1 after native stderr redirection. This wrapper behavior was independently reproduced using a native command deliberately writing stderr while exiting 0 (`phase6el-powershell-exit-reproduction.log`); explicitly capturing and returning `$LASTEXITCODE` returns 0 (`phase6el-powershell-exit-capture.log`). It is not a hidden test failure. A final direct-calculator verification with explicit native Maven exit capture is recorded in `phase6el-native-exit.log`.

The bounded suite uses the established exclusions for Benchmark, CapacityProbe, Performance, Exhaustive and Search classes. It includes existing MI/IL/PA/IA/AZ/NC/KY/GA/VA goldens, no-tax states, brokerage/source safeguards and persistence/export/freshness. Only registry-membership expectations are expanded, plus an independent new-state row; existing financial expected amounts remain intact. No unrestricted suite or unrelated benchmark was run.

## 12. Remaining source/refusal boundaries

- Oklahoma: ordinary private-qualified-plan assumption; specialized government/CSRS/military/railroad, inherited, service and basis histories outside scope. Explicit contrary evidence refuses. No classification from names.
- Mississippi: general cash qualification is source/period dependent; material crossings and unsupported penalty exceptions refuse. Clause-ff IRA conversion relief remains independent. Unverified commercial annuities, employer/in-plan conversions, inherited and partial-basis transactions do not receive guessed relief.
- Mississippi's material senior-birthday boundaries remain precise refusals; zero-tax/ordinary birthday cases remain supported. Explicit JOINT shares not established by inputs use disclosed joint-column fallback, not equal allocation.
- Both: qualified Roth convention and existing federal limitations retained; no new federal early-penalty, cost-basis, security-lot, itemization, dependent/blind, business/foreign, part-year/multistate or comprehensive credit calculations. Omitted OK sales/property relief may lower actual tax. Missing gains may understate; specialized investment adjustments or MS fallback may overstate.

These are disclosed/refused bounded subsets, not permission to convert unavailable tax to zero. There is no unresolved common supported financial or regression failure. Oklahoma and Mississippi independently pass admission within these boundaries; no broader government, inherited, commercial-annuity qualification or employer-conversion promise is made.

## 13. Native Windows acceptance checklist

From repository root in PowerShell:

```powershell
$maven = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd'
$repository = '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository'
& $maven $repository '-Dtest=Oklahoma*,Mississippi*,BracketFillCapacityEvidenceTest,GeorgiaFundingTest,KentuckyFundingTest,GeorgiaVirginiaSourceAdmissionTest,PennsylvaniaProductionProjectionTest' test
& $maven $repository '-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*Performance*,!*Exhaustive*,!*Search*' test
git status --short
git diff --check
```

Manual desktop review is separate; none is claimed performed:

1. Verify 19 alphabetical state choices with OK/MS and no Colorado. Read complete help and state summary/year/range limitations; review all six/seven categories.
2. Open/save/reload Single/MFJ ordinary plans; compare cash, brokerage and traditional funding. Check IRA, current/opening RMD, actual conversion and shortfall/source/destination ownership; compare known worksheet cases above.
3. For OK review separate recipient caps, the senior-AGI conversion adjustment, January 1 birthday convention and commercial-annuity taxable fraction. Check specialized/unclassified source refusal without name-based classification.
4. For MS compare known-owner combined columns with unallocated/JOINT joint fallback, early cash versus completed IRA conversion, a 59½ crossing refusal, material senior refusals at the January 1 birthday/year-end and January 2 birthday/January 1 death boundaries, and commercial-annuity/employer-conversion refusal.
5. Review 2026–2030 rates and held future assumptions, different spouse ages, both survivor directions and later Single results; no unused deceased cap/zero-band transfer.
6. Create scenario/baseline copies; export normal Single/MFJ PDF/CSV. Open retained results after editor state changes: original taxes/limitations/version identity must remain. New-rule invalidation is tested with synthetic prior references, not by editing real historical artifacts.
7. Review a small controlled MC and bounded integrated/longevity result. Inspect both sides of comparisons and frozen metadata. No exhaustive searches or large simulations are needed for desktop smoke acceptance.

## 14. Final Git/whitespace verification and stop boundary

Opening untracked audit SHA-256: **1732CEB7041287D7D0DA905B96A3C34EA9C3EFF4709A1B95FF54A637983C5720**. Final hash matches exactly. `git status --short --untracked-files=all` records 28 modified files and 37 untracked artifacts: 36 new implementation/report artifacts plus the preserved audit. The complete status is recorded in ignored `target/phase6el-final-status.log`; the exact intentional manifest is Section 3.

`git diff --check` exits **0**. All 64 intentional changed/new artifacts also pass strict UTF-8 decoding and a separate trailing-whitespace scan, including untracked files. The sorted path/content SHA-256 manifest of the 63 source/test files is **80468938E44443D991CC95235224E9BCE69F0F5A083777BB0B92715D02579F9A**, unchanged between final bounded-run start and closing review. The index is empty of staged changes. Existing-state calculators, federal calculation, `ProjectionEngine` and Colorado preparatory sources have no diff.

No stage, commit, push or tag. No other state implementation or Colorado activation. Phase 6E-L stops after this report; native interactive review/publication are separate.
