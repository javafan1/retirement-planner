# Phase 6E-N1 — Alabama implementation

Verification date: **2026-11-17**. Governing authorization: Phase 6E-N, following the accepted complete Phase 6E-M audit. This report distinguishes enacted law, agency instructions, approved ordinary planning coverage and excluded facts. Arkansas preparation is documented separately in `PHASE-6E-N2-ARKANSAS-PREPARATION.md`.

## 1. Result and admission gate

Alabama's ordinary calculator, explicit pension classification, federal-liability deduction, source reconciliation, capacity-complete conversion trials and application integration are implemented. **Independent automated admission passed: Alabama is registered as the20th production jurisdiction in this working tree.** The final bounded suite reports5,354 tests,0 failures/errors and35 existing skips. Native interactive acceptance and Git publication remain user-controlled.

Opening source baseline was exactly **19** registered jurisdictions: MI, FL, IL, PA, AK, NV, NH, SD, TN, TX, WY, IA, AZ, NC, KY, GA, VA, OK, MS. The local implementation adds AL independently. Arkansas and Colorado have no enum entries, production routes, selectable states or newly activated analysis keys. Colorado preparatory rules are unchanged.

Initial `git status --short --untracked-files=all` contained only the pre-existing untracked `PHASE-6E-M-ALABAMA-ARKANSAS-TAX-AUDIT.md`. Its SHA-256 is preserved:

`163813C64272EDEBE5983550CF8B4BEF586C6B2170CF07C255596BFCE0A54536`.

## 2. Reviewed legal rules and sources

The ordinary rules version is **AL-2026-1**, analysis reference key **alabama-income-tax-rules**. Constants were rechecked before calculation implementation against the following official material, including enrolled 2026 laws. A 2025 annual booklet is identified as continuing administrative guidance, not mislabeled final 2026 instructions.

| Authority | Finding / governing date |
|---|---|
| [Alabama Code 40-18-5](https://alison.legislature.state.al.us/code-of-alabama?section=40-18-5), [Rule 810-3-5-.01](https://admincode.legislature.state.al.us/api/rule/810-3-5-.01) | Single marginal slices: first $500 at 2%, next $2,500 at 4%, remainder 5%; joint doubles those boundaries. |
| [Code 40-18-15](https://alison.legislature.state.al.us/code-of-alabama?section=40-18-15), [enrolled HB527 / Act 2026-604](https://alison.legislature.state.al.us/files/pdf/SearchableInstruments/2026RS/HB527-enr.pdf) | Retains ordinary income-dependent standard deductions. Separate overtime provisions are outside retirement-only scope. |
| [Code 40-18-19](https://alison.legislature.state.al.us/code-of-alabama?section=40-18-19), [enrolled HB341 / Act 2026-603](https://alison.legislature.state.al.us/files/pdf/SearchableInstruments/2026RS/HB341-enr.pdf) | Retains DB relief and separate age-65 $6,000 relief; amendment effective October 1, 2026 does not turn ordinary DC/IRA receipts into fully exempt DB pensions. Specialized National Guard relief is excluded. |
| [Rule 810-3-15-.20](https://admincode.legislature.state.al.us/api/rule/810-3-15-.20), [2025 Form40 booklet](https://www.revenue.alabama.gov/wp-content/uploads/2026/01/25f40bk.pdf), federal-deduction worksheet | Current-year uncontested accrued eligible federal liability is a defensible annual method. Payments/withholding are not an extra deduction; later federal adjustments require separate treatment. Repealed Rule .19 is not used as current authority. |
| [Rule 810-3-19-.04](https://admincode.legislature.state.al.us/api/rule/810-3-19-.04), [ALDOR exempt-income guidance](https://www.revenue.alabama.gov/individual-corporate/income-exempt-from-alabama-income-taxation/) | Qualifying IRC414(j) defined-benefit payments, including direct beneficiary benefits, receive relief. An exempt pension rolled into an IRA can present state-basis issues. |
| [2025 Schedule RS](https://www.revenue.alabama.gov/wp-content/uploads/2026/01/25schrsblk.pdf), [RS instructions](https://www.revenue.alabama.gov/wp-content/uploads/2025/01/24schrsinstr.pdf), [dated 2026 withholding booklet](https://www.revenue.alabama.gov/wp-content/uploads/2026/01/whbooklet_0126.pdf) | Separate recipient pools, standard deduction and ordinary exemption corroboration. Annuitized taxable commercial receipts use the explicitly approved Schedule RS interpretation, not an assertion that every insurance payment qualifies. |
| [IRS RP2025-32, 2026 amounts](https://www.irs.gov/pub/irs-drop/rp-25-32.pdf), [Schedule1-A](https://www.irs.gov/pub/irs-pdf/f1040s1a.pdf), [IRS published 2027 adjustments](https://www.irs.gov/newsroom/irs-releases-tax-inflation-adjustments-for-tax-year-2027-taxpayers-continue-to-benefit-from-the-working-families-tax-cuts) | Ordinary federal additional-age deductions, temporary enhanced-senior deduction, phaseout and expiration used only for the Alabama liability estimate. |
| [DOL cash-balance facts](https://www.dol.gov/agencies/ebsa/about-ebsa/our-activities/resource-center/fact-sheets/cash-balance-pension-plans) | Account-like benefit presentation does not establish DC classification; a cash-balance arrangement can legally be DB. |

No subsequent located enactment displaced the ordinary reviewed 2026 amounts. No final 2026 source conclusively resolved Alabama's narrow January1/day-before convention for this pool; only material affected calculations are unavailable. This is not a claim of a comprehensive legislative or tax-return engine.

## 3. Alabama calculation

`AlabamaProductionTaxCalculator` uses the existing immutable annual request. It reconciles modeled federal AGI and source totals, subtracts only included Social Security, verified DB amounts and eligible own-recipient senior relief, then computes Alabama deductions and marginal tax.

Let G be Alabama income after those state exemptions. Completed steps are `max(floor((G - 25,500)/500), 0)`. Single standard deduction is `max(3,000 - 25*steps, 2,500)`; MFJ is `max(8,500 - 175*steps, 5,000)`. Personal exemptions are $1,500 / $3,000. Taxable income is floored only after subtracting the state standard deduction, personal exemption and eligible federal liability. Marginal rates apply to slices, never the entire base at its highest rate.

All financial arithmetic uses BigDecimal. Exact liability intermediates are retained; final Alabama planning tax uses HALF_UP cents. No filing lookup table or whole-dollar return equivalence is claimed. Fixed enacted Alabama amounts carry nominally until reviewed law changes; federal basic deductions/brackets retain existing projection assumptions. No Alabama arithmetic was placed in ProjectionEngine or JavaFX.

## 4. Pension classification and compatible persistence

One optional fact, `PensionPlanClassification`, has UNKNOWN, DEFINED_BENEFIT_414J and DEFINED_CONTRIBUTION. Existing constructors and missing/null JSON default to UNKNOWN. UNKNOWN is omitted on the wire, preserving existing default documents/fingerprints. No migration or new pension hierarchy is introduced.

The existing pension dialog has one compact legal-plan selection and explanatory tooltip; guided creation also exposes the optional selection. Neither names, payments, COLA, dates nor an account-like display infer legal qualification. Explicit cash-balance DB selection uses the same legal classification.

`IncomeSourceAttribution.PensionFacts` freezes the original classification while its source owner records the actual current recipient. Direct survivor benefits retain the original plan's class. Retained pension facts capture/restore it. A narrow reader permits only the new classification property to be absent; original required retained fields and unknown-field rejection remain strict. Initial tests exposed 24 legacy codec errors when the additive record property alone was used with strict missing-creator validation; the scoped reader fixed those errors without loosening the global codec.

Save/reload/Save As-style separate-file saves, scenario copies, baseline projections, frozen complete-input documents and retained annual sources are tested. Mutating an editor pension afterward does not change frozen classification/taxes. Existing baseline reference-sharing semantics were not redesigned; the tests establish the exercised copy/restore paths, not universal deep immutability of all live baseline objects. CompleteProjectionSnapshot's frozen input document, used for historical restoration/swaps, retains explicit classification; existing swap lifecycle coverage is included in bounded regression and native swap checks remain required.

All prior states ignore this fact financially. UNKNOWN pensions receive no automatic full AL exemption: compare bounded DB/DC outcomes and return structured unsupported if tax differs. An immaterial tax-equivalent UNKNOWN pension may pass. An IRA/brokerage-only plan is not rejected for missing pension facts.

## 5. Eligible federal liability: separate from federal cash

`AlabamaFederalLiabilityDeduction` uses the existing federal bracket arithmetic and request's federal base breakdown. It does not modify FederalTaxCalculator, TaxFundingCalculator, federal tax results or cash funding.

The helper verifies the modeled regular-tax-plus-evidenced-NIIT result and basic-deduction coverage. An unexplained credit/special-tax discrepancy is unsupported. For the ordinary no-credit subset it recomputes eligible liability after the applicable additional-age and enhanced-senior deductions, then includes evidenced NIIT exactly once. A separate worksheet method tests known after-credit liability plus NIIT less enumerated refundable credits. There are no plan credit/payment-history fields and none are invented.

Federal eligibility uses the verified day-before-65 convention, independently of the unresolved AL birthday edge. Additional-age deductions are $2,050 Single / $1,650 per MFJ senior for2026; published2027 amounts $2,100 / $1,700 are held nominally afterward, explicitly a future assumption. Enhanced relief is up to $6,000 per eligible senior through2028, reduced 6% of AGI above $75,000 Single / $150,000 MFJ per eligible person, floored at zero. It expires after2028. Ordinary domestic AGI is used for this bounded MAGI coverage.

| Independent control | Modeled federal cash | Eligible deduction / AL result |
|---|---:|---:|
| Single65, IRA50,000,2026 | 3,820.00 | Eligible2,854.00; AL1,817.30 |
| Incorrect raw-liability comparator | 3,820.00 | AL1,769.00; would understate tax48.30 and is not used |
| MFJ100,000, two65 seniors,2026 | 7,640.00 | Eligible5,804.00 |
| Same income, one senior | 7,640.00 | Eligible6,722.00 |
| Single65 IRA50,000,2028, held basic rules | 3,820.00 | Eligible2,848.00 |
| Single66 same income,2029 | 3,820.00 | Eligible3,568.00 after temporary relief expires |
| Single65 investment300,000,2026, evidenced NIIT3,800 | 71,934.25 | Eligible71,216.75, NIIT once |
| Stated after-credit regular4,000 + NIIT500 - known refundable100 | Not payments | Eligible4,400.00 |

Annual state result notes record modeled federal cash, eligible deduction, their difference, additional-age/enhanced deductions and NIIT. Annual detail/CSV and PDF retain the actual numeric worksheet notes. Analyzer/MC input contexts also freeze pension legal classifications and the full method limitations.

This is an approved ordinary liability estimate, not a complete Form1040. Unmodeled federal credits/preferences can overstate the deduction and understate AL tax. AMT, foreign MAGI, prior refunds/audits and payment history are outside scope. Do not interpret their absence from the model as legally zero.

## 6. Retirement, source and basis boundaries

| Source | Admitted treatment / boundary |
|---|---|
| Explicit qualifying DB private/cash-balance pension, direct survivor | Included taxable amount subtracted in full; no second senior subtraction. Original class and current recipient required. |
| Qualified ordinary DC pension and traditional IRA/rollover/employer withdrawals/RMDs | Taxable; one $6,000 pool per current age65 recipient, no transfer. Ordinary plan qualification is bounded; affirmative unmet conditions override it. |
| Completed fully taxable own IRA-to-Roth IRA conversion | AGI once; shares the owner's same senior pool. Actual execution/account/destination evidence required. |
| Employer-to-Roth/in-plan, inherited or partial-basis retirement transactions | Not broadened into own-IRA conversion coverage; structured unsupported where represented. |
| Qualified Roth cash | Outside modeled AGI; consumes no pool. Existing federal qualification convention retained. |
| Admitted annuitized commercial receipt | Only evidenced taxable fraction enters income and approved senior pool. Return of investment not retaxed; no automatic DB relief. Basis/NIIT/Advanced/growth admission remains. |
| Arbitrary conditional taxable benefit | Ordinary taxable income, not automatically retirement relief. Existing conditional-benefit admission remains. |
| Specialized government/military/railroad, historical AL basis/DB rollover history | No name inference or special eligibility engine; represented incompatible facts are outside the ordinary subset. No state basis ledger is built. |

Age eligibility is recipient-specific. An inactive/deceased filer is not aged through the remainder of the year. Material unresolved January1/death-day birthday alternatives are compared and refused when tax changes. Death-year MFJ and later Single remain the existing projection convention. Direct survivor pension tests exercise both directions. IRA inheritance is not inferred from deceased source ownership; explicit inconsistent taxable ownership remains unavailable.

## 7. Brokerage and reconciliation

The Alabama-local helper follows accepted OK/GA aggregate-placeholder behavior. Interest/dividend/gain evidence describes already-modeled dollars. Actual TaxIncomeCalculator + TaxIncomeAttribution tests cover attached INTEREST replacing its placeholder and DIVIDEND/CAPITAL_GAIN describing the same aggregate without replacement. Federal AGI is preserved; taxable investment income enters once. Duplicate, excessive, overlapping, incompatible and wrong-year evidence is rejected.

Unknown brokerage basis alone never rejects ordinary spending, invents a gain or adds principal to AGI. Single sole-filer attribution is bounded; no arbitrary MFJ investment split is needed for Alabama's actual joint base. Nonincome cash/brokerage principal can remain account-labeled with the original deceased owner without becoming taxable income or a new retirement recipient; positive taxable evidence for inactive recipients still fails. A survivor test reproduced the initially overrestrictive cash guard before that narrow Alabama-only correction.

Unmodeled gains may understate taxes; omitted state-specific securities, losses or historical basis relief may overstate them. Existing federal NIIT and annuity safeguards remain intact.

## 8. Independent financial fixtures and funding

Expected monetary values come from Phase6E-M Section17 and separately stated decimal worksheets, not outputs copied from the new calculator. AL01–03 cover marginal/floor boundaries; AL04–06 cover all20 completed-$500 steps; AL07 is a stated-federal-deduction legal worksheet, not fabricated federal-return evidence.

| Fixture group | Expected Alabama planning values |
|---|---|
| AL04 Single G25,999.99 /26,000,F0 | 1,035.00 /1,036.25 |
| AL05 MFJ same G/F | 645.00 /653.75 |
| AL07 G50,000,F0 /5,000 /50,000 | 2,260.00 /2,010.00 /0.00 |
| AL08 age60 IRA50,000 | 2,069.00 |
| AL09 same +executed20,000 conversion | 2,931.50 |
| AL10/11 senior corrected versus deliberately incorrect comparator | 1,817.30 /1,769.00 |
| AL12 explicit DB50,000 | 0.00 |
| AL13 DB30,000+IRA20,000+conversion20,000,age70 | 1,193.55 |
| AL14/15 MFJ two seniors,100,000 retirement, two owners /one owner | 3,629.80 /3,929.80 |
| AL16 senior IRA50,000+grossSS20,000,includedSS17,000 | 1,715.30 |
| AL17/18 actual admitted annuity gross50,000,taxable20,000,65 /60 | 435.00 /715.50 |
| AL19 principal-only brokerage100,000 | 0.00 |
| AL20 five constant-premise executed50,000 conversions,age60–64 | 2,069.00 annually;10,345.00 accumulated |
| AL21/22 | Both survivor directions, original class/current recipient, material birthday and unsupported source/classification boundaries |

The annual AL20 account-backed test uses zero federal bracket/deduction growth, ample cash, no extra unlisted income and actual executed conversions. It does not predict unknown future federal tables.

Independent age60 funding controls, no SS/NIIT/available tax cash unless stated:

| Starting IRA cash / conversion | Additional traditional funding | Modeled federal tax | Alabama tax |
|---|---:|---:|---:|
| 50,000 /0 | approximately7,044.26 | 4,665.31 | 2,378.95 |
| 50,000 /0, ample cash | 0 incremental traditional requirement | 3,820.00 | 2,069.00 |
| 50,000 /20,000 executed | approximately12,822.54 | 9,390.96 | 3,431.58 |

Tests respect the established one-cent funding tolerance and assert cash conservation/live-balance immutability. The AL bracket-fill capacity test first failed with unowned hypothetical retirement income. Only then was AL added to the existing KY/GA/OK/MS executable-capacity trial branch. No global funding algorithm changed. Requested/actual conversion, absent same-owner destination, RMD-before-conversion and capacity shortfalls remain distinct.

An additional independent bracket-fill worksheet starts with IRA cash15,000 and federal taxable-income target40,000: AGI56,100, modeled federal4,552 and Alabama2,337.40. Traditional-funded conversion is34,210.60; with ample tax cash it is41,100. The existing all-jurisdiction fixture also adds only the new AL expectation9,903.40 at AGI243,600: `(243600 - 5000 - 3000 - 35932)*.05 - 80`. All prior branches remain byte-for-byte in their monetary constants.

## 9. Application, reporting and history acceptance

Alabama uses the existing enum/router and alphabetical selector. Conditional/annuity allowlists add only AL; the calculators retain the existing receipt guards. No new state-specific math in ProjectionEngine, JavaFX or the federal calculator. No migration/dependency/build change.

All seven limitation categories are displayed without truncation: ordinary base/deductions, explicit pension/legal classification, own senior pool, separate federal-liability method, future federal assumptions, brokerage/state basis, and annuity/specialized scope. Annual details include computed worksheet data. Range/help uses complete static limitation paragraphs. Single/MFJ CSV headers align with rows and include frozen annual notes. PDF uses frozen AL identity, complete limitations and actual annual liability/classification notes, including after the editor moves to another state.

AnalysisVersions/AcceptedAnalysisCompletion capture AL-2026-1 only for associated full-plan analyses, including either side of paired MC. AnalysisCompatibilityPolicy compares current AL rules only for saved AL references. A prior version becomes outdated without changing tax values, payloads, notes or original references. SS-only and unrelated-state reference maps remain unchanged.

Acceptance includes real IRA/RMD/conversion/brokerage accounts; Single/MFJ; pension DB/DC/UNKNOWN; different ages; both direct survivor directions; cash and traditional funding; two controlled +4%/-2% economic paths matched against deterministic equivalents; one bounded integrated SS comparison and two longevity scenarios with exact second-death horizons; plan/scenario/baseline persistence; frozen complete inputs/retained annual sources; normal and retained CSV/PDF; and freshness.

## 10. Automated verification and final admission

Environment: Temurin **25.0.4.1**, IntelliJ bundled Maven specified by AGENTS.md, project-local `.codex-m2/repository`. No unrestricted full suite, exhaustive strategy search or new benchmark was requested/run.

| Log under target/ | Result |
|---|---|
| phase6en-classification.log | Before fix:81 tests,24 errors,2 skips; strict optional retained property issue. |
| phase6en-domain-base.log | After scoped codec fix:103 tests,0 failures/errors,2 existing skips. |
| phase6en-alabama-domain-final.log |33 direct tests,0 failures/errors/skips. |
| phase6en-alabama-capacity-before.log | Before AL capacity admission:1 reproduced unowned-residual error. |
| phase6en-alabama-funding.log |50 tests including KY/GA/OK/MS funding references,0 failures/errors/skips. |
| phase6en-alabama-integration.log |63 tests,0 failures/errors/skips. |
| phase6en-final-focused.log |139 tests including four accepted-state funding references,0 failures/errors/skips. |
| phase6en-final-pension-source.log |7 final strict pension/source tests,0 failures/errors/skips. |
| phase6en-focused-complete-final.log |**127 tests,0 failures/errors/skips**, final AL/AR/classification selection. |
| phase6en-bounded-regression.log | Initial5354 tests:5 failures,1 error,35 skips. New AL expectations/fixtures were missing in several registry-wide tests; one independent worker-cleanup timing assertion also failed. |
| phase6en-regression-corrections.log |107 tests,0 failures/errors/skips, including unchanged Stage5DComparisonTest. |
| phase6en-arkansas-final.log |49 Arkansas tests,0 failures/errors/skips, including final annuity/lower-edge checks. |
| phase6en-bounded-final.log | **5,354 tests:5,319 passed,0 failures,0 errors,35 skips. BUILD SUCCESS, native exit0,9:34.** Finished2026-11-17 at17:07:41 Eastern. |

Intermediate new-test setup errors were corrected against actual APIs/premises: account-reference factory, ProjectionYear deduction accessor and indexed versus held deduction inputs. A Single fixture initially contained an invalid spouse-survivor amount; it was corrected to an ordinary valid Single pension. The ES fixed point fixture was independently corrected to16,973.36 after rounding federal and state components separately; no production arithmetic was changed to fit it. No existing-state financial expected values changed. Existing tests update registry counts/lists, add an independently derived new AL branch in the all-jurisdiction bracket-fill worksheet, and declare DC classification only for the new AL retained-codec fixture. The latter tests actual supported classification rather than silently exempting UNKNOWN.

The initial worker-cleanup assertion was transient in these runs: Stage5DComparisonTest passed unchanged in the107-test targeted rerun and the final bounded rerun. It was not fixed or hidden by changing an assertion or financial calculation. The35 existing skipped cases include opt-in visual previews, legacy/disabled codec cases, research worksheets and assumption-controlled tests; none is a substitute for manual acceptance. All new AL/AR/classification focused cases ran without skips.

**Final production admission: ALABAMA ADMITTED as the20th jurisdiction, pending user-controlled native interactive acceptance and Git publication.** The reviewed ordinary subset has no remaining automated legal/source/funding/reporting gate failure. The19 accepted states retain their financial golden constants and passing regression behavior. Arkansas remains preparation-only; Colorado remains disabled.

## 11. Native Windows acceptance checklist

Automated Windows JavaFX tests do not substitute for interactive native review. After reviewing this report:

1. Confirm Java25 and project-local Maven; run the focused command and established bounded command below. Inspect summaries and native process exit, not PowerShell's incidental native-stderr presentation.
2. Launch the desktop application using the existing IntelliJ MainApplication run configuration with Java25. Confirm20 alphabetical jurisdictions, Alabama selectable, Arkansas/Colorado absent, prior saved MI/OK/MS/GA/VA choices unchanged.
3. Open a legacy plan with an unclassified pension: UNKNOWN is visible. Verify explicit DB/DC editing, compact layout, tooltip, guided creation, cancel versus save, cash-balance explanation and no name inference. Save As/reload each class; check unrelated state taxes remain unchanged.
4. Run the senior50,000 control: modeled federal3,820 remains, eligible deduction2,854 is visible, AL1,817.30 before incremental tax funding. Test sufficient cash versus IRA-funded taxes and a20,000 executed conversion.
5. Run ordinary DB, DC and materially UNKNOWN pensions; UNKNOWN must produce unavailable, not zero. Exercise both direct survivor directions, death-year MFJ/later Single, pool ownership and different ages. Check RMD/conversion shortfalls and absent same-owner Roth destination.
6. Check principal-only and mixed brokerage plans, admitted annuity taxable/basis receipts and NIIT refusal paths. Compare normal annual/range disclosures, analyzer inputs and MC report assumptions; all paragraphs and separate federal figures must remain readable.
7. Save accepted projection and analysis history; edit active state/classification; reopen history, export Single/MFJ PDF and CSV, and verify original taxes, class, notes and AL version. Exercise baseline/current scenario swap and reload; old graphs must remain detached. Do not infer acceptance from skipped preview tests.
8. Check rule-version outdated behavior with a test fixture (not by editing production constants in an accepted plan), SS-only invariance and both sides of MC comparison. Do not enable AR/CO or publish Git as part of acceptance.

```powershell
$maven = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd'
& $maven '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-Dtest=Alabama*,Arkansas*,PensionClassificationTest' test
& $maven '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*Performance*,!*Exhaustive*,!*Search*' test
```

## 12. Files and Git verification

Exact final source/test manifest and final Git/whitespace outcome are appended after completion below. No staging, commit, push, tag, significant deletion or unrelated refactor is authorized/performed. The opening Phase6E-M report remains untracked and byte-identical. Remaining disclosed limitations are the ordinary estimate boundaries above; Arkansas's separate funding decision is not an Alabama admission requirement.

### Exact Alabama/shared source and test manifest

Arkansas-only paths are listed in N2. The following paths are relative to the repository root. Existing test changes add Alabama registration/admission coverage; all prior monetary expectations remain intact.

| Git change | Path |
|---|---|
| Modified | src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionCsvExporter.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AcceptedAnalysisCompletion.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisVersions.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitAdmission.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/domain/benefit/NonqualifiedAnnuityAdmission.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/domain/income/Pension.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/domain/model/StateTaxJurisdiction.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/domain/roth/RothConversionBracketFillCalculator.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ProductionStateTaxService.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/ui/dialogs/PensionDialog.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRunService.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonRunService.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualReportContext.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAnalyzerInputSummary.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/ui/summary/ProjectionYearDetailsPane.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java |
| Modified | src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java |
| Modified | src/test/java/com/daviddunn/retirementplanner/domain/projection/PennsylvaniaProductionProjectionTest.java |
| Modified | src/test/java/com/daviddunn/retirementplanner/domain/projection/RetainedProjectionCodecTest.java |
| Modified | src/test/java/com/daviddunn/retirementplanner/domain/tax/state/NoIncomeTaxAdmissionTest.java |
| Modified | src/test/java/com/daviddunn/retirementplanner/domain/tax/state/colorado/ColoradoWorksheetCompletionTest.java |
| Modified | src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/IowaMississippiAdmissionWorksheetTest.java |
| Modified | src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/PennsylvaniaParticipantFactsAuditTest.java |
| Modified | src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/PennsylvaniaProductionAdmissionAuditTest.java |
| Modified | src/test/java/com/daviddunn/retirementplanner/ui/wizard/ArizonaUiAcceptanceTest.java |
| Modified | src/test/java/com/daviddunn/retirementplanner/ui/wizard/GeorgiaUiAcceptanceTest.java |
| Modified | src/test/java/com/daviddunn/retirementplanner/ui/wizard/KentuckyUiAcceptanceTest.java |
| Modified | src/test/java/com/daviddunn/retirementplanner/ui/wizard/MississippiUiAcceptanceTest.java |
| Modified | src/test/java/com/daviddunn/retirementplanner/ui/wizard/NorthCarolinaUiAcceptanceTest.java |
| Modified | src/test/java/com/daviddunn/retirementplanner/ui/wizard/OklahomaUiAcceptanceTest.java |
| Modified | src/test/java/com/daviddunn/retirementplanner/ui/wizard/VirginiaUiAcceptanceTest.java |
| Added | src/main/java/com/daviddunn/retirementplanner/domain/income/PensionPlanClassification.java |
| Added | src/main/java/com/daviddunn/retirementplanner/domain/tax/state/AlabamaProductionTaxCalculator.java |
| Added | src/main/java/com/daviddunn/retirementplanner/domain/tax/state/alabama/AlabamaFederalLiabilityDeduction.java |
| Added | src/main/java/com/daviddunn/retirementplanner/domain/tax/state/alabama/AlabamaInvestmentReconciliation.java |
| Added | src/main/java/com/daviddunn/retirementplanner/domain/tax/state/alabama/AlabamaTaxRules.java |
| Added | src/test/java/com/daviddunn/retirementplanner/app/montecarlo/AlabamaBrokerageMonteCarloRegressionTest.java |
| Added | src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/AlabamaIntegratedAcceptanceTest.java |
| Added | src/test/java/com/daviddunn/retirementplanner/domain/income/PensionClassificationTest.java |
| Added | src/test/java/com/daviddunn/retirementplanner/domain/projection/AlabamaAnnualGoldenTest.java |
| Added | src/test/java/com/daviddunn/retirementplanner/domain/projection/AlabamaBrokerageProjectionRegressionTest.java |
| Added | src/test/java/com/daviddunn/retirementplanner/domain/projection/AlabamaFundingTest.java |
| Added | src/test/java/com/daviddunn/retirementplanner/domain/projection/AlabamaPensionPersistenceTest.java |
| Added | src/test/java/com/daviddunn/retirementplanner/domain/projection/AlabamaProjectionAcceptanceTest.java |
| Added | src/test/java/com/daviddunn/retirementplanner/domain/projection/AlabamaReportingAcceptanceTest.java |
| Added | src/test/java/com/daviddunn/retirementplanner/domain/tax/AlabamaInvestmentReconciliationTest.java |
| Added | src/test/java/com/daviddunn/retirementplanner/domain/tax/state/AlabamaProductionTaxCalculatorTest.java |
| Added | src/test/java/com/daviddunn/retirementplanner/domain/tax/state/AlabamaSourceBoundaryTest.java |
| Added | src/test/java/com/daviddunn/retirementplanner/domain/tax/state/alabama/AlabamaFederalLiabilityDeductionTest.java |
| Added | src/test/java/com/daviddunn/retirementplanner/domain/tax/state/alabama/AlabamaTaxRulesTest.java |
| Added | src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AlabamaFreshnessTest.java |
| Added | src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AlabamaInputReportTest.java |
| Added | src/test/java/com/daviddunn/retirementplanner/ui/wizard/AlabamaUiAcceptanceTest.java |

Both N1/N2 reports are new untracked deliverables. The opening untracked Phase6E-M audit is preserved, not recreated or edited.

### Final repository verification

Final `git status --short --untracked-files=all`: **35 modified tracked files and31 untracked files**. These comprise the exact manifests above/in N2, the two new reports and the one preserved opening audit. Of the31 untracked files,9 are new production-source files (including4 isolated Arkansas helpers),19 are tests,2 are the requested new reports and1 is the unchanged opening audit. No unrelated opening change was overwritten.

`git diff --check`: **exit0**. Separate strict UTF-8 decoding and all-changed-file trailing-whitespace scan: **PASS**, including untracked artifacts. `git diff --cached --quiet`: **exit0**, no staged changes. Original Phase6E-M SHA-256 remains exactly the value in Section1. Git's LF/CRLF conversion notices are informational, not whitespace errors. No commit, push, tag, reset, migration, dependency change, federal calculation edit, shared tax-funding/settlement edit, Arkansas registration or Colorado activation occurred.

Phase6E-N is complete within the approved staged scope. Stop for native acceptance and separate Arkansas decisions; no Phase6E-O work has begun.
