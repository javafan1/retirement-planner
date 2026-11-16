# Phase 6E-B2 — Arizona production admission

Verification date: **2026-11-16**. Continues the accepted Phase 6E-A audit and Phase 6E-B adjustment component. No North Carolina work, staging, commits or pushes.

## 1. Rule resolution

The earlier requirement for a final 2026 booklet is replaced by the user's explicit authorization to rely on enacted law and applicable published inflation provisions. The following conclusions distinguish that legal derivation from secondary corroboration. Some direct ADOR requests still return 403; indexed official instructions are identified where used.

| Rule | Authoritative basis and conclusion |
|---|---|
| 2026 basic deduction | [2026 Chapter 140, HB4168, section 16](https://www.azleg.gov/legtext/57Leg/2R/laws/0140.pdf) updates the statutory basic amounts. [ARS 43-1041(A,H)](https://www.azleg.gov/ars/43/01041.htm) sets Single $15,750/MFJ $31,500 bases, subject to annual inflation adjustment in the same manner as the federal basic deduction. [IRS Rev. Proc. 2025-32, section 3.14](https://www.irs.gov/irb/2025-45_IRB) publishes the corresponding 2026 amounts: **$16,100 Single/$32,200 MFJ**. The statutory base is the 2025 amount, not an exemption from subsection H. Applying that inflation provision to the amended base resolves the 2026 values; this is an enacted-law derivation, not merely copying a provisional audit fixture or assuming the whole federal standard deduction applies. Federal age additions are not imported. |
| Rate | **2.5%** confirmed by official [2026 140ES instructions](https://azdor.gov/sites/default/files/document/FORMS_INDIVIDUAL_2026_140iES.pdf), [worksheet](https://azdor.gov/sites/default/files/document/FORMS_INDIVIDUAL_2026_140ES_f.pdf), and [ADOR withholding guidance](https://azdor.gov/individuals/withholding-calculations), available as indexed official text. Historical brackets from an older ARS 43-1011 rendering are not used. |
| Social Security | [ARS 43-1022(10)](https://www.azleg.gov/ars/43/01022.htm) subtracts the federally taxable amount. Gross benefits are not deducted again. |
| Age exemption | [ARS 43-1023(E)](https://www.azleg.gov/ars/43/01023.htm): **$2,100 per eligible filer**; nondependency and joint-return requirements apply. Ordinary plan scope assumes filers are not another person's dependents. |
| Enhanced senior subtraction | [ARS 43-1022(35)](https://www.azleg.gov/ars/43/01022.htm) incorporates IRC 151(d)(5)(C). [IRS Schedule 1-A Part V](https://www.irs.gov/pub/irs-pdf/f1040s1a.pdf) independently establishes the per-person formula: max(6,000 minus 6% of positive MAGI excess over 75,000 Single/150,000 MFJ, zero), summed across eligible filers. [Public Law 119-21](https://www.govinfo.gov/content/pkg/PLAW-119publ21/pdf/PLAW-119publ21.pdf) limits the temporary deduction to 2025–2028. Implementation begins at 2026, stops enhanced subtraction in **2029**, and preserves federal AGI including taxable SS for phaseout. Assumes valid senior SSNs and domestic MAGI=AGI, explicitly disclosed. |
| January 1 birthday | Official indexed [ADOR 2025 Form 140A instructions](https://azdor.gov/sites/default/files/document/FORMS_INDIVIDUAL_2025_140Ai.pdf) and [Booklet X](https://azdor.gov/sites/default/files/document/FORMS_INDIVIDUAL_2025_BookletX_Vol1.pdf) recognize January 1 of the next year for prior-year age eligibility. [IRS Schedule R instructions](https://www.irs.gov/instructions/i1040sr) state age is attained the day before the birthday. Thus January 1, 1962 qualifies for 2026; January 2, 1962 does not. |
| Death-year eligibility | Arizona uses the federal age convention in the cited instructions; [IRS Publication 501](https://www.irs.gov/publications/p501) requires age attainment by death, rather than a later birthday in that tax year. Adapter uses December 31 for active filers and modeled January 1 death for inactive filers. Existing MFJ-through-death-year/Single-afterward behavior stays unchanged. No actual death-date field or analyzer redesign. |
| Taxable-income floors | Official indexed [Form 140 instructions](https://azdor.gov/sites/default/files/document/FORMS_INDIVIDUAL_2025_140i.pdf), lines 42 and 45, floor Arizona adjusted gross income and final taxable income at zero. Adapter applies both floors; all deductions are nonnegative. |
| Rounding | Official indexed [Form 140 booklet](https://azdor.gov/sites/default/files/document/FORMS_INDIVIDUAL_2025_140Booklet.pdf) requires line-by-line nearest whole dollar, half-up. This resolves the legal convention. The app deliberately preserves exact intermediates and rounds final state estimate HALF_UP to cents, consistent with existing planning calculations and smooth funding feedback. The difference from tax-return rounding is visible in every Arizona limitations payload. This is an explicit planning approximation, not a claim of exact return reproduction. |

No material rule uncertainty remains for this bounded planning calculation. Final 2026 booklet retrieval is not represented as accomplished. No secondary calculator was used to establish production values. Future legislation, individualized deductions/credits and taxpayer-specific facts remain outside scope rather than unresolved ordinary-scenario rules.

## 2. Exact implementation changes

* Added `domain/tax/state/ArizonaProductionTaxCalculator.java`, implementing the existing StateIncomeTaxCalculator contract. Contains small versioned state parameters (`AZ-2026-1`), final AGI-based calculation, admission checks and three stable disclosure paragraphs. A separate JSON loader for two deductions and a rate was unnecessary.
* Reused `domain/tax/state/arizona/ArizonaRetirementAdjustmentsCalculator.java`; only its now-obsolete isolation comment was updated. Its tested financial arithmetic was not duplicated or replaced.
* Added AZ at the end of `domain/model/StateTaxJurisdiction.java`, preserving existing enum order/codes, and registered the adapter in `domain/tax/state/ProductionStateTaxService.java`.
* `domain/benefit/ConditionalBenefitAdmission.java` and `NonqualifiedAnnuityAdmission.java`: add AZ to the existing state-specific allowlists so approved already-modeled annuity/conditional taxable receipts reach the same adapter. All federal NIIT, basis, growth, mode and other-state guards remain intact.
* `ui/views/AssumptionsView.java`: Arizona selection help explains nominal parameters and exclusions; choices reuse alphabeticalChoices.
* `ui/views/ResultsSummaryView.java`: wrapped Arizona range tooltip uses frozen projection limitations, not current-plan or newly loaded rule assumptions.
* `ui/summary/ProjectionYearDetailsPane.java`: wrapped Arizona state-tax detail tooltip includes frozen annual limitations/calculation details.
* `ui/socialsecurity/SocialSecurityAnalyzerInputSummary.java`: Arizona assumptions in the existing shared inputs/assumptions rows.
* `ui/socialsecurity/IndividualReportContext.java`: captures the same Arizona notes in individual deterministic/longevity report metadata, including saved exports.
* `ui/montecarlo/MonteCarloRunService.java` and `MonteCarloStrategyComparisonRunService.java`: capture Arizona disclosures at run time for retained report metadata.
* `app/export/ProjectionCsvExporter.java`: Arizona Planning Limitations column for Arizona rows only; other-state headers retained.
* `app/export/ProjectionPdfExporter.java`: Arizona section renders the three frozen common limitations. Per-year arithmetic details stay in annual result metadata/CSV, avoiding one extra disclosure block per year.
* Added Arizona unit/integration/report tests listed in section 5. Updated the prior Arizona admission-gate assertion to expect admission; its adjustment expected values are unchanged. `domain/tax/state/NoIncomeTaxAdmissionTest.java` now expects 13 production states. `domain/projection/PennsylvaniaProductionProjectionTest.java` adds a separately derived Arizona bracket-fill worksheet value ($5,285 = (243,600 AGI minus 32,200 deduction) times .025); all preexisting state golden values remain unchanged.
* Updated only production-state count/list assertions in `domain/tax/state/simplified/IowaMississippiAdmissionWorksheetTest.java`, `PennsylvaniaParticipantFactsAuditTest.java`, and `PennsylvaniaProductionAdmissionAuditTest.java` to include admitted Arizona. Their existing financial/qualification worksheets and unsupported-state expectations remain unchanged.
* Added this report. Prior untracked audit and implementation reports are preserved without edits.

Paths above are relative to `src/main/java/com/daviddunn/retirementplanner/`. No changes to ProjectionEngine, FederalTaxCalculator, tax funding, Roth execution, existing state calculators/rules, dependencies or build configuration. Existing projection/analysis loops invoke the adapter through the single production tax boundary.

## 3. Calculation and supported scenarios

Start from modeled federal AGI, subtract its federally taxable SS, verified age exemption and enhanced subtraction using existing adjustment calculator. Floor adjusted income at zero, subtract the Arizona basic deduction, floor again, multiply by .025 and round final estimate to cents. Conversion and ordinary traditional distributions stay in AGI and are never re-added from attribution.

Supports full-year domestic Single/MFJ households, ordinary private pensions, fully taxable IRA/401(k)/403(b) and similar modeled traditional receipts, opening/current-year RMDs, actual taxable conversions, modeled qualified Roth withdrawals, selected Social Security, ordinary bank interest, and admitted taxable conditional/annuity receipts. Ordinary represented dividends are accepted only when their assessed amounts already reconcile to the modeled investment-income bucket; no preferential federal dividend rule is created.

Different spouse ages and annual survivor status use immutable annual TaxHouseholdContext facts. Single requires one active filer; MFJ requires two household filers. Missing birth dates or inconsistent filer/federal context produce unavailable estimates. Live account balances remain untouched by tax calculations and projections.

## 4. Exclusions, assumptions and unavailable results

The adapter uses existing ProductionStateTaxResult.unsupported, absent numeric tax and existing reason enums. It refuses explicit positive capital-gain facts without required Arizona gain/holding-period/acquisition-date treatment; unknown or taxable ordinary brokerage proceeds without basis; unknown retirement taxable amounts; explicitly taxable Roth distributions outside qualified scope; non-fully-taxable pension source facts; unclassified OTHER income; wrong-year attribution; and source totals exceeding modeled annual categories.

Brokerage principal is never added to AGI. Known zero-tax cash/principal and qualified Roth receipts can fund spending without state income. No security lots or new account qualification engine.

Pension source type is not present in current immutable pension facts. Ordinary private pensions use modeled taxable income and all results disclose government/military exclusions as unmodeled. Names are deliberately not parsed. There is no represented government/private enum to inspect; claims of automatic detection would be false. Future explicit source data must receive a source-specific guard before support, not an invented exemption.

Source-specific Treasury/municipal differences cannot be inferred from an unattributed ordinary interest total. Explicit exempt/partial investment facts are refused; undetectable source differences remain a disclosed approximation. Gains/capital losses, specialized pension exclusions, credits/refunds, charity enhancements, itemization, dependency, foreign MAGI, nonresidency and multistate taxation are outside scope. The scope assumes valid senior SSNs, nondependent filers, fully taxable traditional receipts, qualified Roth and ordinary domestic sources. No new input UI.

## 5. Acceptance tests and results

Source-backed golden fixtures derive from the statutes, published inflation amounts and senior worksheet independently of production methods. Examples: MFJ seniors with 60,000 IRA plus 40,000 SS yields 290.00; Single 60 with 50,000 IRA plus 20,000 conversion yields 1,347.50; Single senior with pension 45,000 and SS 25,000 yields 520.00; Single senior ordinary 60,000 rises from 895.00 in 2028 to 1,045.00 in 2029 at fixed nominal parameters.

`domain/tax/state/ArizonaProductionTaxCalculatorTest`: independent Single/MFJ goldens, SS-only, pension/IRA/interest/conversion, qualified Roth/cash principal, mixed ages, January birthday, death-year/Single, senior phaseout, sunset, signed floors/cents, unknown gains/basis, investment inclusion, missing birth date/base consistency, unsupported status/year and no pension-name inference.

`domain/tax/state/arizona/ArizonaRetirementAdjustmentsCalculatorTest`: retained phaseout component fixtures and updated admission assertion.

Focused acceptance plus existing state goldens: **246 tests, zero failures/errors/skips, BUILD SUCCESS** (`target/phase6eb2-focused.log`). Four additional independent Social Security/phaseout conversion fixtures then passed in a **27-test** Arizona calculator run (`target/phase6eb2-marginal.log`). Those fixtures show $530/$560 additional state tax on a $20,000 conversion for one/two seniors in the phaseout band. An isolated selector/results UI check also passed (`target/phase6eb2-ui.log`). Bounded regression results are recorded below after completion. No unrelated benchmark/exhaustive search was requested. Existing Michigan/Illinois/Pennsylvania/Iowa golden expectations are unchanged.

Additional classes: `domain/projection/ArizonaProjectionAcceptanceTest` (RMD/actual conversion/funding, capacity shortfalls/owner isolation, JSON/scenario copies/baseline, bracket fill/lifetime sums, taxable annuity versus gross and unchanged NIIT); `ArizonaReportingAcceptanceTest` (frozen projection PDF/CSV and retained Monte Carlo PDF); `app/montecarlo/ArizonaMonteCarloAcceptanceTest` (two controlled worlds, exact deterministic/state/funding equality, both death orders through 2029); `app/socialsecurity/ArizonaIntegratedAcceptanceTest` (quick comparison and one weighted scenario); `ui/wizard/ArizonaUiAcceptanceTest` (13 choices, help, wrapped annual tax tooltip and frozen range disclosure after active-state edit).

`ui/socialsecurity/ArizonaIndividualReportContextTest` verifies saved individual result-time assumptions retain Arizona notes after active-plan state changes.

Admission/final-disclosure recheck: **291 tests, zero failures/errors, four skipped, BUILD SUCCESS** (`target/phase6eb2-admission-recheck.log`). The first broad run found six obsolete admission assertions (12-state counts/lists and an all-non-Michigan-zero worksheet assumption). These were updated only for approved Arizona admission; existing states' financial expected values were preserved. The final bounded run uses `target/phase6eb2-regression-final.log` and includes the newly captured individual report notes.

**Final bounded regression: 4,715 tests, one failure, zero errors, 35 skipped**, in 9 minutes 28 seconds. Every Arizona test and state-tax regression passed. The sole failure was the unchanged `ApplicationSettingsUiTest.directoryDerivationExportOverrideSaveAndCancelUseDetachedDraft`, where the asynchronous save still held the previous directory after its wait. That class passed in the earlier broad run and **passed all 10 tests when rerun in isolation**, without any code change (`target/phase6eb2-settings-recheck.log`, BUILD SUCCESS). This indicates an intermittent unrelated UI/save timing failure. No settings changes, test weakening, or unrelated refactor was made. **An entirely green single full-suite execution is not claimed**; the broad-run failure and successful isolated retry remain a validation caveat, rather than being hidden or attributed to Arizona tax arithmetic.

## 6. Integration and visible limitations

Deterministic and Monte Carlo tax calculations share TaxFundingCalculator and the adapter. Roth bracket-fill trials already pass selected state assumptions; compatibility overloads are not used for production AZ. State funding changes taxable traditional withdrawals and federal SS/AGI, so phaseout is recomputed each trial. State marginal conversion cost is not fixed at 2.5% during senior phaseout. Qualified Roth principal remains outside AGI.

No analyzer search objectives, mortality weights, exact second-death ending or financial continuation rules change. Baselines/scenario copies and persisted plans retain the new stable state code through existing Jackson pathways. State-change freshness uses existing planning-assumption fingerprints. Existing RetainedProjectionSnapshot.State preserves jurisdiction/status/amount/limitations; history does not recalculate frozen facts from the active plan.

Common Arizona notes appear in state-selection help, shared analysis inputs, normal projection range/detail tooltips, projection PDF/CSV, Monte Carlo run/report metadata and retained snapshot exports. Notes identify pension/brokerage/credit exclusions, MAGI/qualification assumptions, frozen nominal parameters, enhanced-subtraction expiration, federal-model omissions and cents-versus-return rounding. Annual result details include actual applied amounts. CSV uses the frozen annual result, not live settings.

## 7. Future-year policy

Nominal verified 2026 basic deductions and $2,100 age exemption remain fixed for later modeled years. Enhanced senior amount/phaseout remain fixed during 2026–2028 and expire in 2029. Rate remains .025 absent separately supported enacted change. This is the approved planning approximation to indexed future law, disclosed prominently, not a generalized indexing engine or assertion of permanent statutory freeze. Historical years before 2026 are unavailable.

## 8. Admission outcome

**Arizona is admitted as the 13th production jurisdiction.** The mandatory bounded tax-rule questions and Arizona acceptance checks are resolved/passing. No remaining Arizona-specific admission blocker was found. The unrelated intermittent settings UI test described above is the remaining broad-regression caveat; it passed unchanged on isolated retry. North Carolina remains unsupported and untouched. Registration is local and not a deployment or commit. This does not claim all possible Arizona tax situations or an entirely green full-suite run.

## 9. Native Windows acceptance

Use Java 25 and IntelliJ bundled Maven. PowerShell focused command:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-Dtest=Arizona*Test,ProductionStateTaxServiceTest,StateTaxGoldenFixturesTest,MichiganTaxCalculatorTest,IllinoisProductionTaxCalculatorTest,PennsylvaniaProductionTaxCalculatorTest,IowaProductionTaxCalculatorTest' test
```

Broad routine regression uses the same executable/repository with `'-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*PerformanceAudit*,!*Exhaustive*,!*Search*' test`. No build exclusions or dependencies changed. Skipped visual-preview tests are not manual acceptance.

Launch MainApplication from IntelliJ. Create/open a disposable retirement plan; choose Arizona from alphabetical State of Residence choices and review its help. Run Single and MFJ projections with IRA, pension, SS and a conversion. Inspect state-tax range/detail tooltips, shared analyzer assumptions and 2028/2029 results. Save/reload and confirm Arizona/amounts persist. Export PDF/CSV and verify limitations. Change active state after retaining an analysis; historical export must retain Arizona metadata. Known unsupported brokerage/gain facts must show unavailable rather than zero tax. Use small controlled MC requests, not performance/exhaustive analyses.

No manual native desktop acceptance is claimed by automated tests. Verify `git status --short` and `git diff --check`; do not stage or commit automatically.

Final repository review: initial untracked Phase 6E-A/6E-B documents and adjustment component/tests were preserved and continued in place. Tracked diff contains only the reported Arizona integration and state-admission test updates. `git diff --check` passed; no staging, commit, push or North Carolina changes. Newly added files also received no-index whitespace checks.
