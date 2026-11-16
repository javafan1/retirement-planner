# Phase 6E-B — Bounded Arizona implementation

Date: 2026-11-15. **Partial implementation; production admission blocked. Arizona remains unregistered.**

## 1. Implementation summary

Followed the accepted Phase 6E-A audit and the explicit instruction to implement only validated rules if a material rule remains unverified. Added an isolated Arizona retirement-adjustment component and independent domain tests. No provisional standard deduction was promoted into production. There is no final Arizona tax estimate, selector entry, dispatcher registration or alternate UI/projection calculation path.

The component calculates the federally taxable Social Security subtraction, nominal $2,100 exemption per eligible senior, temporary enhanced senior subtraction with per-person MAGI phaseout, and signed adjusted-income intermediate. It preserves negative intermediates rather than prematurely flooring them. It does not claim to calculate final Arizona taxable income or tax.

The production interface currently requires a production StateTaxJurisdiction even for an unsupported result. AZ enum values are also production selection choices. Rather than changing that contract or adding a misleading selectable state, the dedicated production adapter and its integration are deferred until admission is possible. No generalized framework was introduced.

## 2. Authoritative tax-rule verification

All verification attempts below occurred **2026-11-15**. Direct ADOR form retrieval frequently returned 403 or inaccessible responses. Indexed official material is distinguished from fully reviewed instructions. No third-party tax calculator supplies production constants.

| Mandatory item | Evidence and conclusion | Implementation/admission status |
|---|---|---|
| Applicable 2026 standard deduction | [ARS 43-1041 A,H](https://www.azleg.gov/ars/43/01041.htm) provides $15,750 Single/$31,500 MFJ base with inflation adjustment in the federal basic-deduction manner. [Enacted HB4168/SB1861 summary](https://www.azleg.gov/legtext/57leg/2R/summary/S.4168-1861ATT_ASENACTED.DOCX.htm) confirms increased bases. [ADOR highlights](https://azdor.gov/forms/individual-income-tax-highlights) still labels those amounts **2025**. [IRS 2026 estimated-tax instructions](https://www.irs.gov/pub/irs-pdf/f1040es.pdf) give federal $16,100/$32,200, not independently certified Arizona amounts. | **Unresolved blocker.** Statutory base is not necessarily final indexed deduction. Audit provisional $16,100/$32,200 remain unverified Arizona constants and are absent from new production code/tests. |
| Rate | [ADOR 2026 140ES instructions](https://azdor.gov/sites/default/files/document/FORMS_INDIVIDUAL_2026_140iES.pdf) and [worksheet](https://azdor.gov/sites/default/files/document/FORMS_INDIVIDUAL_2026_140ES_f.pdf) indexed official text specifies 2.5%. [ADOR withholding guidance](https://azdor.gov/individuals/withholding-calculations) describes 2.5% for 2023 onward. | Verified at official-publication/index level; no rate computation is activated while other rules are blocked. |
| Age-65 exemption | [ARS 43-1023 E](https://www.azleg.gov/ars/43/01023.htm): $2,100 for qualifying taxpayer/spouse, not claimed as a dependent, with joint-return requirements. | Amount implemented. Eligibility is a precondition, not automatically inferred by this component. |
| Enhanced senior formula and expiration | [ARS 43-1022(35)](https://www.azleg.gov/ars/43/01022.htm) adopts IRC 151(d)(5)(C). [Public Law 119-21](https://www.govinfo.gov/content/pkg/PLAW-119publ21/pdf/PLAW-119publ21.pdf) provides temporary period. [IRS Schedule 1-A, Part V](https://www.irs.gov/pub/irs-pdf/f1040s1a.pdf), lines 31–37 independently confirms thresholds, 6% reduction of each $6,000 amount, and addition of eligible spouses' amounts. | Formula implemented for 2026–2028; zero enhanced subtraction from 2029. MAGI includes taxable SS before Arizona adjustment. Domestic MAGI=AGI is an explicit precondition; foreign addbacks are not modeled. |
| Eligibility details | Same IRS worksheet requires valid SSN, joint filing if married, and age eligibility. Arizona age exemption separately requires nondependency. | Caller must establish all applicable conditions before supplying eligible senior count. No new qualification UI or account rules. This component does not establish age/SSN/dependency proof. |
| Birthday/death | [ADOR 2025 Form 140A instructions](https://azdor.gov/sites/default/files/document/FORMS_INDIVIDUAL_2025_140Ai.pdf) and [Booklet X](https://azdor.gov/sites/default/files/document/FORMS_INDIVIDUAL_2025_BookletX_Vol1.pdf) indexed text recognizes next-January-1 birthday for prior year. One indexed Form 140 note contains inconsistent date/birth-year text. [IRS Publication 501](https://www.irs.gov/publications/p501) states deceased must already qualify at death. | Evidence improves birthday understanding, but final Arizona 2026/death convention review remains incomplete. No automatic age-at-death algorithm was guessed or wired into projection. |
| Rounding/floors | [ADOR X/Y tables page](https://azdor.gov/forms/individual/form-140-x-y-tables) indexed official notice says optional/X-Y tables ended with flat tax. 140ES worksheet uses nonnegative liability. Full final 2026 annual-return rounding instructions were not reviewed. | **Final rounding/floor review unresolved.** Component performs exact BigDecimal intermediate arithmetic, no return rounding or final taxable-income floor. |

Other verified rule: [ARS 43-1022(10)](https://www.azleg.gov/ars/43/01022.htm) removes federally taxable SS, not gross benefits. Ordinary federally taxable retirement income and conversions are not broadly subtracted under that provision.

Failed final-document attempts included `https://azdor.gov/sites/default/files/document/FORMS_INDIVIDUAL_2026_140i.pdf` and `https://azdor.gov/sites/default/files/document/FORMS_INDIVIDUAL_2026_140_f.pdf`. Failure to retrieve is not proof a document does not exist. The [2025 MCTCP worksheet](https://azdor.gov/sites/default/files/document/FORMS_INDIVIDUAL_2025_MCTCP_0.pdf) has official indexed corroboration of senior-subtraction linkage, but is not a substitute for final 2026 instructions.

## 3. Exact files added and preserved

* `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/arizona/ArizonaRetirementAdjustmentsCalculator.java`: isolated verified adjustment arithmetic and immutable result. Constants reside in the state-specific component, not projection logic. No unverified deduction resource is needed yet.
* `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/arizona/ArizonaRetirementAdjustmentsCalculatorTest.java`: independent source/formula-based adjustment fixtures and unregistered-state regression.
* `PHASE-6E-B-ARIZONA-IMPLEMENTATION.md`: this report.

Initial status contained the untracked Phase 6E-A report; it was read and preserved without edits. No existing production, configuration, resource or test file was modified. Maven generated ordinary ignored target output and logs. No staging, commits or pushes.

## 4. Supported scenarios versus deferred integration

The new component accepts an annual filing status, federal AGI, federally taxable SS and already-established eligible senior count. It supports exact adjustment arithmetic for Single/MFJ, different eligible spouse counts, supplied death-year/survivor facts, senior phaseout and expiration. It leaves all other income in the base.

It intentionally returns **no tax**. There are currently **no new user-facing supported Arizona scenarios**. SS-only, pension/IRA/RMD, conversions, qualified Roth, annuity and bank-interest projections must await production adapter/admission. Their arithmetic should reuse existing TaxIncome/FederalTaxCalculation and selected annual income; no second income calculation should be introduced.

## 5. Exclusions and approximations

The approved bounded scope remains the target, not a completed feature claim:

* Private pensions will use modeled taxable income; government/military exclusions remain excluded with visible disclosure. Existing facts do not supply a general pension source classification; names must not be parsed. Only actual explicit classification evidence can trigger a source guard.
* Brokerage principal must never be added to taxable income. Acquisition dates, gains/losses and holding periods remain unmodeled. Detectable material missing facts must produce existing unsupported results; source limitations must be visible where not detectable.
* Qualified Roth/fully taxable traditional distributions retain existing model assumptions; no new retirement qualification rules.
* Itemization, dependent/refundable credits, special bond adjustments, foreign MAGI and specialized arrangements remain outside bounded scope unless existing sufficient facts establish support.
* For this isolated component, caller establishes both applicable exemption and enhanced-subtraction eligibility. A single count is only valid when those conditions coincide; it is not a complete eligibility engine. A future adapter must not infer valid SSNs or nondependency from absent inputs without an explicit scope assumption.

No application estimate can currently hide these limitations because Arizona cannot be selected or deserialized as a supported state. Production UI/export disclosure wiring is deferred with the adapter; storing internal notes is not presented as satisfying that acceptance requirement.

## 6. Roth conversion verification

The component never subtracts conversions or retirement distributions as categories. A hand fixture supplies AGI 90,000 = withdrawal 50,000 + conversion 20,000 + taxable SS 20,000. Enhanced subtraction is 5,100, age exemption 2,100, leaving signed income 62,800. Without conversion and with stipulated same taxable SS, signed income is 41,900; the conversion raises the base by 20,900 because phaseout loses another 900. This is a controlled arithmetic fixture, not a claiming/SS calculation.

MFJ 178,000 with two eligible seniors independently yields 8,640 enhanced subtraction, not a one-time reduction from a 12,000 pool. Threshold-plus-one cases retain exact cents. Status-change fixture at AGI 160,000 gives 10,800 enhanced subtraction with two MFJ seniors, versus 900 for Single with one eligible senior.

These tests establish adjustment behavior, **not end-to-end Arizona conversion tax**. Actual conversion execution, RMD aggregation, qualified Roth treatment, authoritative SS recalculation, iterative tax funding and lifetime totals still need adapter-level tests after admission. Existing federal/state funding behavior was not changed. No claim of universally 2.5% conversion marginal tax is made.

## 7. Future-year policy

Implemented nominal $2,100 age exemption and fixed enhanced thresholds/amounts, with enhanced subtraction stopping in 2029. Years before 2026 rejected. No generalized indexing machinery. Final standard-deduction freeze cannot be implemented until the starting 2026 amount is verified. Once verified, follow the approved nominal freeze, disclosed as a planning approximation rather than permanent statutory freeze.

## 8. Test results

Focused run: **207 tests, zero failures/errors/skips; Maven BUILD SUCCESS.** Includes 17 new Arizona adjustment cases plus ProductionStateTaxServiceTest, StateTaxGoldenFixturesTest, MichiganTaxCalculatorTest, IllinoisProductionTaxCalculatorTest, PennsylvaniaProductionTaxCalculatorTest and IowaProductionTaxCalculatorTest. Existing golden expectations were not changed.

Fixtures independently follow IRS Schedule 1-A Part V and the Arizona exemption/SS statutes; no provisional deduction or proposed full calculator supplies expectations. Includes Single/MFJ threshold/beyond-threshold/full-phaseout, one/two/no seniors, signed negative intermediate, taxable-SS/MAGI interaction, supplied status transition, sunset, invalid inputs and AZ admission remaining unavailable.

Unrestricted `mvn test` was attempted, then interrupted when the default suite entered `MonteCarloPairedBenchmarkTest` (5,000 worlds per mode). Despite its opt-in comment this test runs by default. Completion of unrelated expensive benchmarks would conflict with the requested scope. No unrestricted full-suite success is claimed. The broad regression run excluded classes matching Benchmark, CapacityProbe, PerformanceAudit, Exhaustive and Search and finished **BUILD SUCCESS: 4,669 tests, zero failures/errors, 35 skipped**, in 8 minutes 36 seconds. Logs: `target/phase6eb-focused.log`, `target/phase6eb-full.log`, `target/phase6eb-regression.log`. All Maven invocations use bundled IntelliJ Maven and project-local `.codex-m2/repository`.

Deferred Arizona acceptance matrix: actual SS-only/qualified Roth zero-tax golden, pension/IRA/annuity/bank-interest final-tax goldens, RMD plus conversion, actual death/birthday context, tax-funding fixed point, annual/lifetime totals, persistence/reload, unsupported classification/basis guards, visible projection/PDF/CSV/frozen history disclosures, freshness and controlled deterministic/Monte Carlo equality. Passing existing suites is not evidence these Arizona integrations exist. No additional exhaustive analyses were requested; only normal test-suite execution.

## 9. Admission blockers and next work

Primary blocker is an authoritative final applicable Arizona 2026 standard deduction reconciliation, not permission to use audit provisional values. Final state age/death and return rounding/floor review is also incomplete. Arizona was therefore left out of both StateTaxJurisdiction and ProductionStateTaxService.

When evidence is available: add the dedicated production adapter/rules, existing unsupported-result guards, dispatcher/selector and persistence tests, visible disclosures through existing report/snapshot paths, focused projection/Roth/controlled MC tests, and existing/full regression runs. This report does not mark those tasks complete or authorize North Carolina.

## 10. Native Windows acceptance

1. Use Java 25. Preserve local work; no Git staging required.
2. Run focused command in PowerShell:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-Dtest=ArizonaRetirementAdjustmentsCalculatorTest,ProductionStateTaxServiceTest,StateTaxGoldenFixturesTest,MichiganTaxCalculatorTest,IllinoisProductionTaxCalculatorTest,PennsylvaniaProductionTaxCalculatorTest,IowaProductionTaxCalculatorTest' test
```

3. For the broad bounded regression used here, replace `-Dtest` with `'-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*PerformanceAudit*,!*Exhaustive*,!*Search*'`. An unrestricted suite uses `test` with no `-Dtest`, but includes expensive benchmark/search cases and was not completed for this task. No build configuration was changed to exclude them.
4. Launch application normally from IntelliJ. Arizona must **not** appear in state choices. Existing supported-state plans must still open. Do not edit saved plan JSON to force AZ; unknown code is intentionally rejected.
5. There are no Arizona numeric projection/export results to manually accept yet. After source blockers are resolved, complete deferred acceptance matrix before admission.
6. Run `git diff --check` and inspect `git status --short`. Only the three files above are new for Phase 6E-B; preserve the preexisting untracked audit.

Final verification: tracked diff is empty; `git diff --check` and separate no-index whitespace checks of the three new files produced no whitespace diagnostics. Initial/final status preserves the preexisting audit and adds only the three reported files. No native manual application acceptance was performed; automated JavaFX regression coverage passed as part of the bounded run.
