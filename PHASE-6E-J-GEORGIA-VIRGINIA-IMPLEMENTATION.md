# Aledrix — Phase 6E-J Georgia and Virginia implementation

Legal review and implementation date: **2026-11-17**. Scope: approved ordinary retirement planning, independent state admission. See the accepted [Phase 6E-I audit](PHASE-6E-I-GEORGIA-VIRGINIA-TAX-AUDIT.md) for independent worksheets and the distinction between enacted law and approved approximations.

## 1. Executive summary

Independent Georgia and Virginia calculators, immutable versioned rules, annual source validation, disclosures and provenance have been implemented. Georgia has a state-local recipient/investment reconciler; Virginia has a state-local age worksheet. Both use the existing annual tax/funding boundary. No new UI inputs, federal calculation changes, persistence migration, general tax framework or tax-lot engine were introduced.

**Georgia and Virginia independently pass their production admission gates. The supported count is now 17 jurisdictions.** The source-confirmed opening baseline was 15. Colorado remains absent from the enum/router and its preparatory rules remain disabled. The opening working tree contained the untracked Phase 6E-I report; it was preserved unchanged. No deployment or Git publication is implied by local admission.

## 2. Legal verification and future-law assumptions

Verified again on **November 17, 2026**, using enacted text and official guidance rather than earlier bill drafts or a previous year's numeric tables:

| State | Verified law incorporated | Approved planning boundary |
|---|---|---|
| GA | 2026 flat 4.99%; standard deduction $15,000 Single/$30,000 MFJ; individual age-62–64 exclusion $35,000; age-65+ exclusion $65,000 in 2026, **$70,000 from 2027**; taxable SS subtraction | Latest reviewed rate/deduction held nominally until a legally established conditional update is incorporated. No revenue forecast, automatic conditional step or inflation indexing. |
| VA | 2% first $3,000; 3% next $2,000; 5% next $12,000; 5.75% above $17,000, same bands for joint filers | Nominal enacted bands; no doubled joint brackets. |
| VA deduction | 2026 $8,750/$17,500; 2027 $9,200/$18,400; 2028–2029 $9,300/$18,600; **2030+ $3,000/$6,000** | Literal enacted reversion, with visible/frozen disclosure. No silent continuation of the temporary increase. |
| VA ordinary relief | $930 per nondependent filer; $800 per qualifying age-65 filer; applicable age deduction and strict $11,950/$23,900 no-tax threshold | No spouse tax adjustment, itemization, dependent/blindness optimization or specialized credit engine. |

GA authorities: [DOR current tax updates](https://dor.georgia.gov/taxes/important-tax-updates), [signed HB 463 §§2-1–2-3](https://gov.georgia.gov/document/2026-signed-legislation/hb-463/download), [DOR retirement guidance](https://dor.georgia.gov/retirement-income-exclusion), [official Rule 560-7-4-.02](https://rules.sos.ga.gov/gac/560-7-4), and [IT-511 IRA/retirement worksheets](https://dor.georgia.gov/document/document/2025-it-511-individual-income-tax-booklet/download). The last source supplies continuing worksheet/source methods; its old rate/deduction amounts are superseded by HB 463. [HB 1199](https://gov.georgia.gov/document/2026-signed-legislation/hb-1199/download) supplies the updated conformity enactment. Searches of official DOR/OPB materials did not establish activation of the next conditional step as of this review, before December 1. GA's prospective 0.125-percentage-point cuts and $375/$750 deduction steps are not production calendar-year constants.

VA authorities: current [§58.1-320](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-320/), [§58.1-322.03](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-322.03/), [§58.1-321](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-321/), [§58.1-322.02](https://law.lis.virginia.gov/vacode/title58.1/chapter3/section58.1-322.02/), the [2026 legislative summary](https://www.tax.virginia.gov/sites/default/files/inline-files/2026-legislative-summary.pdf), and [official Form 760 worksheets](https://www.tax.virginia.gov/sites/default/files/vatax-pdf/2025-760-instructions.pdf). Current law controls the new deduction schedule; the older booklet's sunset is superseded. The moving 2026 age cutoff is also corroborated by [official early-release 2026 instructions](https://www.tax.virginia.gov/sites/default/files/taxforms/early-release/2026-individual/instructions/draft-2026-763-instructions-early-release.pdf), which are draft/nonresident material, not a substitute resident engine. Some direct VA DOR requests timed out; retrieved official/indexed worksheet text and current statutes supplied the methods. No final 2026 resident booklet was represented as verified.

Both bases start from **modeled federal AGI**, not federal taxable income. State-standard deductions are applied once; federal basic, additional-age and temporary enhanced-senior below-AGI deductions are not imported. Existing federal coverage and its limitations remain unchanged. Financial intermediates use BigDecimal; final planning state tax uses HALF_UP cents, an approved departure from whole-dollar return-entry rounding.

## 3. Exact files added and modified

The list below excludes the preexisting unchanged Phase 6E-I audit. No accepted state calculator/rules, federal calculator, `ProjectionEngine`, dependency/build configuration or persistence schema was edited. Existing-test edits change only jurisdiction cardinality or add independent GA/VA expectations; prior financial expectations remain intact.

**Production (23):**

- modified: `src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionCsvExporter.java`
- modified: `src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java`
- modified: `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AcceptedAnalysisCompletion.java`
- modified: `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java`
- modified: `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisVersions.java`
- modified: `src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitAdmission.java`
- modified: `src/main/java/com/daviddunn/retirementplanner/domain/benefit/NonqualifiedAnnuityAdmission.java`
- modified: `src/main/java/com/daviddunn/retirementplanner/domain/model/StateTaxJurisdiction.java`
- modified: `src/main/java/com/daviddunn/retirementplanner/domain/roth/RothConversionBracketFillCalculator.java`
- modified: `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ProductionStateTaxService.java`
- modified: `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRunService.java`
- modified: `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonRunService.java`
- modified: `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualReportContext.java`
- modified: `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAnalyzerInputSummary.java`
- modified: `src/main/java/com/daviddunn/retirementplanner/ui/summary/ProjectionYearDetailsPane.java`
- modified: `src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java`
- modified: `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java`
- added: `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/GeorgiaProductionTaxCalculator.java`
- added: `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/VirginiaProductionTaxCalculator.java`
- added: `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/georgia/GeorgiaInvestmentReconciliation.java`
- added: `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/georgia/GeorgiaTaxRules.java`
- added: `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/virginia/VirginiaAgeDeduction.java`
- added: `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/virginia/VirginiaTaxRules.java`

**Tests (32):**

- modified: `src/test/java/com/daviddunn/retirementplanner/domain/projection/PennsylvaniaProductionProjectionTest.java`
- modified: `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/NoIncomeTaxAdmissionTest.java`
- modified: `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/colorado/ColoradoWorksheetCompletionTest.java`
- modified: `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/IowaMississippiAdmissionWorksheetTest.java`
- modified: `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/PennsylvaniaParticipantFactsAuditTest.java`
- modified: `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/PennsylvaniaProductionAdmissionAuditTest.java`
- modified: `src/test/java/com/daviddunn/retirementplanner/ui/wizard/ArizonaUiAcceptanceTest.java`
- modified: `src/test/java/com/daviddunn/retirementplanner/ui/wizard/KentuckyUiAcceptanceTest.java`
- modified: `src/test/java/com/daviddunn/retirementplanner/ui/wizard/NorthCarolinaUiAcceptanceTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/GeorgiaBrokerageMonteCarloRegressionTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/VirginiaBrokerageMonteCarloRegressionTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/GeorgiaIntegratedAcceptanceTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/VirginiaIntegratedAcceptanceTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/domain/projection/GeorgiaBrokerageProjectionRegressionTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/domain/projection/GeorgiaFundingTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/domain/projection/GeorgiaProjectionAcceptanceTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/domain/projection/GeorgiaReportingAcceptanceTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/domain/projection/GeorgiaVirginiaAnnualScheduleTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/domain/projection/VirginiaBrokerageProjectionRegressionTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/domain/projection/VirginiaFundingTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/domain/projection/VirginiaProjectionAcceptanceTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/domain/projection/VirginiaReportingAcceptanceTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/domain/tax/GeorgiaInvestmentReconciliationTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/domain/tax/GeorgiaVirginiaSourceAdmissionTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/GeorgiaProductionTaxCalculatorTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/VirginiaProductionTaxCalculatorTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/GeorgiaFreshnessTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/GeorgiaInputReportTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/VirginiaFreshnessTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/VirginiaInputReportTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/ui/wizard/GeorgiaUiAcceptanceTest.java`
- added: `src/test/java/com/daviddunn/retirementplanner/ui/wizard/VirginiaUiAcceptanceTest.java`

**Report (1):**

- added: `PHASE-6E-J-GEORGIA-VIRGINIA-IMPLEMENTATION.md`


## 4. Georgia retirement exclusion and ownership

The calculator reconciles federal AGI/SS with the annual modeled categories, then sums qualifying retirement receipts by actual active PRIMARY/SPOUSE recipient. Ordinary traditional IRA/rollover/employer withdrawals, opening/current RMDs, confirmed traditional IRA-to-Roth IRA conversions, ordinary private pension and admitted taxable annuity receipts share **one** recipient cap. Age qualification is annual; unused caps do not transfer. Survivor pensions use the current recipient, retaining original-owner evidence without assigning that owner's unused cap to the survivor.

Known owned eligible investment amounts enter the same pool. Established JOINT income is allocated 50/50 only for that explicit evidence. Approved approximations: unallocated MFJ investment remainder stays in AGI without a retirement exclusion; an uncontradicted Single ordinary aggregate is assigned to the sole active resident filer, including a surviving spouse. Contrary evidence is not overwritten. These conventions can overstate GA tax and are disclosed.

The reviewed GA sources did not settle the day-before-birthday treatment at the January 1 threshold edge. An income-bearing case where that ambiguity affects reaching 62/65 is **structured unsupported with a January 1 reason**. Ordinary June birthdays, threshold attainment during the year, separate spouse ages and post-death status remain supported. A deceased filer is not aged through a later birthday; zero eligible income does not require an irrelevant age exclusion.

Employer-to-Roth and in-plan variants are not inferred from IRA guidance. GA refuses those unverified conversion variants while admitting ordinary employer-plan withdrawals. Military/disability/service-history, inherited/partial basis and business/rental/royalty qualification are outside scope. Pension display names never establish a legal classification.

## 5. Virginia age deduction, exemptions and threshold

`VirginiaAgeDeduction` uses actual annual filer DOB/alive facts. Qualifying DOB on/before January 1, 1939 receives the grandfathered $12,000. Later eligible filers use AFAGI: modeled AGI adjusted for admitted conformity facts and reduced by federally taxable SS. The ordinary subset has no additional conformity difference to invent. The official day-before-birthday convention gives a moving annual cutoff; January 1 versus January 2 and death-before-birthday are tested.

The Single income-tested pool is $12,000 less excess AFAGI over $50,000. Joint returns use $75,000: one tested claimant has a $12,000 pool, two have a combined $24,000 pool, reduced **once** by joint excess. At AFAGI $80,000, two tested spouses retain $19,000 combined, not $14,000. A grandfathered spouse's deduction remains separate. An eligible nonworking spouse need not own retirement income.

The separate $800 age exemption remains after the income-tested deduction is zero. Standard deduction and $930/$800 exemptions follow the age subtraction. The strict no-tax test uses the official pre-standard-deduction Virginia adjusted amount after age/SS modifications: below $11,950 Single/$23,900 MFJ gives zero; equality does not. Tests include the age/threshold cliff and tax funding that crosses it.

The approved omission of the **up-to-$259 spouse tax adjustment** is prominent in normal inputs/results, stored notes and exports. Eligible two-income married households may owe less than the estimate. No household investment split is invented to calculate that adjustment.

## 6. Roth conversions and iterative funding

GA traditional IRA conversion eligibility applies DOR's federal conversion treatment together with its taxable-IRA exclusion worksheet; it is not a newly found source-specific letter ruling. VA conversion amounts included in AGI remain taxable; [VA ruling 98-44](https://www.tax.virginia.gov/laws-rules-decisions/rulings-tax-commissioner/98-44) supports that treatment, without importing its historical installment timing. Qualified Roth cash remains outside modeled AGI and consumes no exclusion.

The pre-change GA capacity test reproduced a trial above the executable $1,000 balance, leaving an unowned residual and a structured refusal. The only shared financial edit adds **GA** to KY's existing same-owner capacity-complete trial condition. KY/earlier-state outcomes remain unchanged; VA retains the existing aggregate trial path. Final projections still tax actual executed conversions, record shortfalls and restrict same-owner destinations. RMD processing remains first.

Every funding trial still rebuilds income/evidence and recomputes federal and selected state tax. No fixed marginal gross-up replaced the solver. Independent controls include:

| Ordinary Single 2026 control | Independent expected result |
|---|---|
| GA age 62, IRA $50,000 + IRA conversion $20,000, no extra funding | State $998.00 |
| GA age 62, IRA $50,000, traditional-funded taxes | Funding about $4,601.85–$4,601.86; federal $4,372.22/state $229.63, within existing cent tolerance |
| VA age 60, IRA $50,000, traditional-funded taxes | Funding $7,150.03; federal $4,678.00/state $2,472.03 |
| VA age 65, IRA $50,000, traditional-funded taxes in age phaseout | Funding $6,725.36; modeled federal $4,627.04/state $2,098.32 |
| VA age 65, IRA $23,900 before funding | Initially below age-adjusted no-tax threshold; funding crosses it and converges near $918.64, state $46.77 |
| Explicit modeled federal taxable-income target $40,000, starting IRA $15,000 | Traditional-funded conversion GA $36,243.61 / VA age-60 $34,136.35; sufficient cash gives $41,100 in each case |

These federal controls intentionally use existing standard-only federal coverage, not a claim to model every legal senior deduction. Their basic-rate inputs come from [2026 IRS Form 1040-ES](https://www.irs.gov/pub/irs-pdf/f1040es.pdf). The explicit $40,000 target is a test overload, not a statutory bracket endpoint.

Cash-funded controls add no extra traditional income. Real mixed brokerage/IRA/RMD/conversion tests verify account ownership, opening/current RMD distinction, executed amounts and unchanged live balances. Cash residuals are checked against the existing raw $0.01 funding tolerance; separately rounding each side of an otherwise sub-cent residual can differ by one cent. The shared tolerance/settlement engine was not changed.

## 7. Brokerage and investment reconciliation

Gross brokerage proceeds remain cash flow. Missing cost basis alone is admitted; no gain is invented, principal is not taxed and source metadata never adds another income channel.

The GA-local helper recognizes the existing unowned INTEREST aggregate signature emitted by `TaxIncomeCalculator`/`TaxIncomeAttribution`, validates its relationship to same-kind replacement evidence and reconciles attached gains/dividends against the bucket once. Complete/partial known ownership is used only for the GA exclusion. The actual calculator/attachment integration tests exercise CAPITAL_GAIN, DIVIDEND and INTEREST forms, owner contrasts and duplicates. VA independently reconciles aggregate/evidence for its household base without importing GA exclusion rules or a shared new framework.

Known wrong-year, excessive, duplicate, incompatible or ambiguously overlapping source evidence is refused. Special/exempt interest needs separate adjustment facts. Ordinary source/basis/NIIT/Advanced and conditional-income guards remain independent. Missing gains may understate actual tax; GA's unallocated-investment approximation may overstate it. Both limitations are retained rather than treating unknown gains as legally zero.

## 8. Independent financial goldens

Phase 6E-I §13 fixtures were implemented as independently specified expected constants. No expected value was obtained from a new production calculator. Important controls include:

- GA-01–18 ordinary/source/boundary cases: $50,000 IRA at 61 gives $1,746.50; at 62/64 gives $0; age-65 IRA $100,000 gives $998.00 in 2026/$748.50 from 2027 under the held-law policy. MFJ $50,000 per spouse versus $100,000 to one age-62 spouse gives $0 versus $1,746.50. One combined pension/IRA/conversion cap and exact-cent rounding are tested.
- GA ownership contrast, MFJ age 65, IRA A $90,000/investments $20,000: unallocated or A-owned $748.50; B-owned $0; explicit JOINT $249.50. AGI stays $110,000 throughout. $100,000 principal-only brokerage produces calculated $0 with limitations.
- VA-01–16 controls: age-60 IRA $50,000 gives $2,060.90/$2,035.03/$2,029.28/$2,029.28/$2,391.53 for 2026–2030. Age-65 $50,000 gives $1,324.90; adding $20,000 conversion gives $3,164.90, a $1,840 change including lost age relief. Joint two-claimant $80,000 gives $2,044.80, one claimant $2,780.80. All rate boundaries, moving DOB cutoff, grandfathered/mixed cohorts, exemptions and strict no-tax tests are included.
- Actual evaluated annuity gross $60,000/taxable $50,000 gives GA age-62 $0 / VA age-62 $2,060.90. Basis principal is not taxed again. The larger existing guarded annuity/NIIT fixture independently gives GA $12,255.44 / VA $19,070.55 at its specified 2030 AGI, with identical federal result to the control.
- Annual actual cash-funded IRA conversions reproduce cumulative GA $3,992.00 and VA $10,546.02 over 2026–2030. Both survivor directions reproduce GA $0 death-year/$748.50 later Single and VA $2,453.05/$3,938.28 in the audit's pension-only fixtures.
- The old all-state bracket-fill worksheet retains every earlier state's expected value and adds only independently calculated GA $8,912.14 and VA $12,636.30 at its $243,600 target AGI.

Fixture reconciliation: a first pension-only survivor fixture accidentally retained a large IRA that generated additional RMDs, including an account owned by a deceased person. The fixture was corrected to the worksheet's pension-only, cash-funded assumptions; no production income owner, RMD behavior or expected tax was changed to force the fixture. Retained JSON-tree mutation tests now use exact decimal trees matching the existing codec, avoiding test-only floating-point/scale loss. These are not financial-engine fixes.

## 9. Deterministic, Monte Carlo and integrated acceptance

Separate GA/VA account-backed suites cover fixed and bracket-fill conversions, owner capacity/destinations, RMD ordering, funding, brokerage principal and mixed receipts, annual status/rules, survivor pension ownership and balance/cash checks.

Each state's brokerage MC test uses two controlled paths (+4%/−2%) for principal, mixed and conversion cases. Every annual state/federal tax, actual conversion, funding, cash flow and ending assets is compared with the deterministic equivalent under the identical path. Plan capture before/after is unchanged. This is controlled consistency testing, not a statistical performance claim.

Each state also has one bounded integrated SS comparison and two weighted longevity outcomes, checked against direct deterministic context runs and exact second-death endings. There is no analyzer ranking/search redesign or state-specific arithmetic in the projection engine.

## 10. Persistence, disclosures, exports and freshness

JSON save/reload, scenario copies and saved baselines retain state selection and financial results through existing services. Baseline reference semantics were not redesigned. All material limitation paragraphs are included in state selection help, Results Summary range tooltips, annual details, analyzer input rows, individual context, both MC report paths and normal/retained PDF/CSV. GA ownership/conditional-law and VA sunset/omitted-STA notes are not truncated to an older state's paragraph count.

Frozen result amounts/limitations survive active-plan edits and exact round trips. Historical exports use stored jurisdiction/notes, including after the editor moves to Florida. Historical-note tests replace only the recorded limitation text and verify that current policy is not substituted during restore/export. Single and MFJ PDF/CSV controls are included.

Versions: **GA-2026-1**, key `georgia-income-tax-rules`; **VA-2026-1**, key `virginia-income-tax-rules`. Fresh full-plan completions capture keys from the actual frozen plan(s), including both sides of a comparison. Compatibility checks mark an older version outdated without altering its payload, tax or context. Unrelated-state and SS-only references remain unchanged. No persistence schema migration was introduced.

## 11. Test execution and existing-state regression

IntelliJ bundled Maven, Java 25 and the project-local `.codex-m2/repository` were used throughout. Logs are local ignored `target/` artifacts.

| Check | Result / log |
|---|---|
| Initial calculator/reconciliation plus preexisting capacity evidence | 49 passed; `target/phase6ej-domain.log` |
| GA capacity reproduction before the bounded correction | Expected failure: unowned trial residual; `target/phase6ej-capacity-before.log` |
| Focused acceptance, including independent worksheets and prior KY funding/PA worksheet | 146 passed, no failures/errors/skips; `target/phase6ej-focused-acceptance.log` |
| Final focused/source hardening plus prior selector contracts | **190 passed**, no failures/errors/skips; `target/phase6ej-focused-hardening.log` |
| Final bounded regression | **5,067 tests, 0 failures, 0 errors, 35 skips; BUILD SUCCESS**, 10m05s; `target/phase6ej-bounded-final.log` |

Bounded command excludes classes matching Benchmark, CapacityProbe, Performance, Exhaustive and Search. It preserves the established bounded regression scope rather than invoking the unrestricted suite. Existing MI/IL/PA/IA/AZ/NC/KY tax goldens, AZ B3 brokerage regressions, funding, annuity/conditional guards, persistence/export/freshness and native JavaFX acceptance are included. Selector/enum cardinalities change from 15 to 17; Colorado's non-admission assertions remain. No earlier financial expected value was changed.

The first bounded run completed **5,063 tests, 3 failures, 0 errors, 35 skips**, in 9m54s (`target/phase6ej-bounded.log`). All three failures were hard-coded 15-jurisdiction lists in two PA audit classes; only those lists were extended, retaining PA qualification/refusal assertions. The final source-hardening tests also first reproduced four expected failures for ambiguous overlapping investment evidence and a Roth source mislabeled as a pretax conversion (`target/phase6ej-contradictions-before.log`). Only the new GA/VA admission checks were tightened; normal unknown-basis brokerage cash remains admitted. No old-state tax calculation was changed.

## 12. Independent admission status

**Georgia: ADMITTED as the 16th production jurisdiction within the approved bounded scope.** Current law/held conditional policy, per-recipient age caps, real aggregate reconciliation, ordinary brokerage admission, IRA conversions/capacity/funding, survivor and annual/lifetime fixtures, complete frozen limitations, persistence/reports and rule freshness pass. Narrow January 1 ambiguity and unverified employer conversion variants remain explicit unsupported cases, not guessed values.

**Virginia: ADMITTED as the 17th production jurisdiction within the approved bounded scope.** Current rates and 2030 deduction reversion, official age/AFAGI/joint worksheet, exemptions and strict threshold, ordinary sources/conversions, funding across age/threshold changes, brokerage, persistence/history/reports and independent version capture pass. Omitted spouse-adjustment relief is prominently disclosed and frozen.

The final bounded run passed without retrying tests. The 35 skips are existing optional/visual checks; they are not represented as manual desktop acceptance. Both states separately pass their domain and integration suites, and the existing-state bounded regression passes unchanged financial expectations. No material ordinary-scope release blocker remains. Colorado is still unregistered and its preparatory rule version/calculations are unchanged.

## 13. Bounded exclusions and remaining questions

GA: the January 1 age-threshold ambiguity is explicitly refused where material; unverified employer-to-Roth/in-plan variants, military/disability/historical service and inherited/basis qualification remain outside scope. Ordinary private pensions are not rejected simply because classification is unavailable. Complete per-person retirement attribution is required; no deceased/nonrecipient account is silently reassigned. Known JOINT taxable investments conflicting with a Single survivor require reliable ownership treatment rather than inferred transfer.

Both states admit ordinary unknown-basis brokerage spending, but actual unmodeled gains/losses and special securities/state-basis adjustments require separate consideration. No rental/royalty/business engine, tax-lot accounting, itemization, dependent/blindness optimization or special military/disability/railroad credit engine is advertised. VA omits the up-to-$259 spouse adjustment by approval. Combined outcomes retain existing federal-model limitations. Future law requires a reviewed version update, not automatic forecasting.

These disclosed/refused subsets do not authorize guessing qualification or silently treating an unsupported result as zero. Any remaining common-scenario financial or regression failure would withhold the affected state's admission.

## 14. Native Windows acceptance instructions and final checks

PowerShell, from the repository root:

```powershell
$maven = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd'
$repository = '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository'
& $maven $repository '-Dtest=Georgia*,Virginia*,BracketFillCapacityEvidenceTest,KentuckyFundingTest,PennsylvaniaProductionProjectionTest' test
& $maven $repository '-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*Performance*,!*Exhaustive*,!*Search*' test
git status --short
git diff --check
```

Desktop smoke review, separate from automated JavaFX checks:

1. Confirm 17 alphabetical choices, GA/VA selectable and Colorado absent. Read complete state-selection help and normal/range/year limitations, especially GA ownership/future-law and VA sunset/STA notes.
2. Use ordinary Single/MFJ midyear DOB cases, different spouse ages, IRA/RMD/private pension/annuity income and cash/brokerage/traditional-funded conversions. Check actual execution, shortfall and owner restrictions rather than requested conversion alone.
3. Review 2026/2027 GA cap and VA 2027/2028/2030 deduction transitions, both pension survivor directions and the strict VA income threshold. A material GA January 1 ambiguity should report unavailable with its reason.
4. Save/reload, create scenario/baseline and compare results. Export Single/MFJ PDF/CSV. Open a retained GA/VA result after changing the editor to Florida; original tax, state and limitations must remain. Run the focused freshness tests to verify old-version invalidation without editing real historical artifacts.
5. Use the small controlled MC/integrated tests above; do not run exhaustive searches or large random benchmarks for this acceptance.

Automated native Windows tests are distinguished from these manual instructions; no manual interactive review is claimed. Final `git status --short --untracked-files=all` and `git diff --check` were run. Changed/new source and report files were additionally checked for trailing whitespace and valid UTF-8 because Git's normal diff check does not include untracked additions. The exact list is in §3: 23 production files, 32 test files and this report. The opening Phase 6E-I report remains unchanged (SHA-256 `A2316DB86AB9E6470881C1D6AF8FAFE3DFDBCFDE275BD2DA51388C399BB2698E`). No staging, commit, push or tag was performed. Phase 6E-J stops here; no other state work was begun.
