# Aledrix Retirement Planning — Phase 6E-D
# North Carolina State Income Tax Implementation

Implementation and verification date: **2026-11-16**. Implements the approved bounded policies in [Phase 6E-C](PHASE-6E-C-NORTH-CAROLINA-TAX-AUDIT.md). No Kentucky or Colorado implementation, staging, commits, pushes or tags.

## 1. Implementation summary

North Carolina uses the existing `StateIncomeTaxCalculator` / `ProductionStateTaxService` boundary. One small calculator and an immutable, versioned `NorthCarolinaTaxRules` component calculate:

```text
NC taxable income = max(modeled federal AGI - federally taxable Social Security
                        - NC standard deduction for annual filing status, 0)
NC planning tax   = taxable income × enacted rate for the actual calendar year
```

Intermediates remain exact `BigDecimal`; final tax is rounded HALF_UP to cents. No age exemption, enhanced senior deduction, federal standard deduction or additional conversion income is imported into the NC base. State rules are `NC-2026-1`. Conditional revenue-trigger reductions are not predicted.

The calculator returns ordinary calculated estimates, including calculated zero, or the existing structured unsupported result with no numeric estimate. Existing projections, iterative tax funding, Roth execution and state-aware bracket fill supply its annual facts. There are no NC financial-calculation branches in `ProjectionEngine` or JavaFX, no new tax-input fields, no generalized rules engine, no dependency/build changes and no federal or other-state calculation changes.

## 2. Tax-rule verification and authoritative sources

Rules were rechecked against official sources before implementing constants:

| Rule | Verified basis and implementation |
|---|---|
| Resident AGI starting point | [G.S. 105-153.4(a)](https://www.ncleg.gov/EnactedLegislation/Statutes/HTML/BySection/Chapter_105/GS_105-153.4.html). Use modeled federal AGI with the admitted adjustments, not federal taxable income. |
| Standard deduction and SS | [G.S. 105-153.5(a)(1),(b)(3)](https://www.ncleg.gov/EnactedLegislation/Statutes/HTML/BySection/Chapter_105/GS_105-153.5.html), [NCDOR deduction guidance](https://www.ncdor.gov/taxes-forms/individual-income-tax/filing-topics/north-carolina-standard-deduction-or-north-carolina-itemized-deductions), and [NC-40 worksheet PDF page 3](https://www.ncdor.gov/individual-estimated-income-tax/open#page=3): $12,750 Single/$25,500 MFJ; remove the federally included SS amount only. No state age addition or automatic inflation adjustment was found. |
| Enacted rates | [S.L. 2026-41 §44.1, printed page 607](https://www.ncleg.gov/EnactedLegislation/SessionLaws/PDF/2025-2026/SL2026-41.pdf#page=607), corroborated by the [official legislative summary](https://www.ncleg.gov/Legislation/SummariesPublication/Summary/2026/11/S257-SMSV-136%28sl%29-v-4/). Use the amended schedule rather than older statute/worksheet language showing 3.99% indefinitely. |
| Later amendments | The [official biennium modification record](https://www.ncleg.gov/Laws/Modifications/2025) still identifies §44.1 as the rate amendment. No later rate/deduction amendment affecting this scope was identified. S.L. 2026-59 §17.6 adds specialized portable-benefit-account adjustments from 2027, outside this implementation. |
| Federal conformity / senior deduction | [NCDOR post-enactment FAQ Q14–Q15](https://www.ncdor.gov/taxes-forms/corporate-income-franchise-tax/frequently-asked-questions-and-important-notices/faqs-regarding-recent-session-law-changes) explains why federal deductions taken after AGI do not become NC deductions. The updated July 5, 2025 IRC reference does not create an NC senior subtraction. |
| Special pension treatment | [NCDOR Bailey guidance](https://www.ncdor.gov/taxes-forms/individual-income-tax/filing-topics/bailey-decision-concerning-federal-state-and-local-retirement-benefits), [PD-14-1](https://www.ncdor.gov/taxes-forms/individual-income-tax/personal-taxes-division-directives/pd-14-1-bailey-v-state-north-carolina-emory-v-state-north-carolina-patton-v-state-north-carolina), and [military retirement guidance](https://www.ncdor.gov/taxes-forms/individual-income-tax/filing-topics/military-retirement). These can materially change distributions/conversions; their eligibility is not calculated. |
| Rounding and independent funding fixtures | [D-401 page 4](https://www.ncdor.gov/2025-d-401-individual-income-tax-instructions/open#page=4) describes return whole-dollar rounding. Exact-intermediate/final-cent calculations are the approved planning convention. [IRS 2026 1040-ES](https://www.irs.gov/pub/irs-pdf/f1040es.pdf) supplies the federal deduction/rate worksheet used for independent funded-conversion examples. |

Some NCDOR filing-topic pages default to 2025. Their continuing rules were checked against enacted law; they are not described as final 2026 instructions. The final 2026 annual-return booklet was not established as available. No provisional deduction or revenue forecast was used as a production constant.

## 3. Annual rate schedule

| Tax years | Applied scheduled rate |
|---|---:|
| 2026 | 3.99% |
| 2027–2029 | 3.49% |
| 2030–2032 | 3.24% |
| 2033 onward | 2.99% |

Statutory standard deductions remain nominally $12,750/$25,500. A future reviewed rules update is required to incorporate changed law or a legally established conditional reduction. Merely reaching 2035–2040 does not reduce the modeled rate. Each annual request resolves its own year; the opening-year rate is not reused throughout the projection.

## 4. Exact files added or modified

Production paths below are relative to `src/main/java/com/daviddunn/retirementplanner/`.

**Added:**

- `domain/tax/state/NorthCarolinaProductionTaxCalculator.java` — admitted-scope reconciliation, NC calculation, five common disclosure categories and frozen annual arithmetic metadata.
- `domain/tax/state/northcarolina/NorthCarolinaTaxRules.java` — immutable deduction/rate constants, reviewed version and NC reference key.

**Modified:**

- `domain/model/StateTaxJurisdiction.java` — append stable `NC` code and North Carolina display name; alphabetical choices remain shared.
- `domain/tax/state/ProductionStateTaxService.java` — route NC to its calculator.
- `domain/benefit/ConditionalBenefitAdmission.java` — add NC to the existing deterministic/analyzer state allowlist.
- `domain/benefit/NonqualifiedAnnuityAdmission.java` — admit the existing bounded annuity subset for NC; other qualification, basis, NIIT, calculation-mode and growth guards remain intact.
- `app/socialsecurity/results/AnalysisVersions.java` — add an NC-only captured reference to new full-plan completions.
- `app/socialsecurity/results/AcceptedAnalysisCompletion.java` — capture that reference from the actual frozen plan through existing integrated completion paths, including results without conditional benefits.
- `app/socialsecurity/results/AnalysisCompatibilityPolicy.java` — compare a retained NC reference with the current NC version; preserve existing-state reference behavior.
- `ui/montecarlo/MonteCarloResultLifecycle.java` — capture NC references from either frozen comparison side or the standalone frozen plan.
- `ui/views/AssumptionsView.java` — existing state-selection help includes all five NC categories.
- `ui/views/ResultsSummaryView.java` — frozen NC range limitations, with no three-paragraph cap.
- `ui/summary/ProjectionYearDetailsPane.java` — wrapped NC annual limitations/details.
- `ui/socialsecurity/SocialSecurityAnalyzerInputSummary.java` — current NC assumptions in existing immutable input rows.
- `ui/socialsecurity/IndividualReportContext.java` — capture all five NC categories in result-time report metadata.
- `ui/montecarlo/MonteCarloRunService.java` — capture NC notes in standalone MC report metadata.
- `ui/montecarlo/MonteCarloStrategyComparisonRunService.java` — capture NC notes for each applicable comparison side.
- `app/export/ProjectionCsvExporter.java` — NC limitations column in Single/MFJ exports, including non-investable asset values; frozen annual notes.
- `app/export/ProjectionPdfExporter.java` — full frozen NC limitations and NC result jurisdiction in the assumptions section, even after the active editor changes state. Existing-state behavior remains unchanged.

**Added tests** (relative to `src/test/java/com/daviddunn/retirementplanner/`):

- `domain/tax/state/NorthCarolinaProductionTaxCalculatorTest.java`
- `domain/projection/NorthCarolinaFundingTest.java`
- `domain/projection/NorthCarolinaAnnualScheduleTest.java`
- `domain/projection/NorthCarolinaProjectionAcceptanceTest.java`
- `domain/projection/NorthCarolinaBrokerageProjectionRegressionTest.java`
- `domain/projection/NorthCarolinaReportingAcceptanceTest.java`
- `app/montecarlo/NorthCarolinaBrokerageMonteCarloRegressionTest.java`
- `app/socialsecurity/NorthCarolinaIntegratedAcceptanceTest.java`
- `ui/socialsecurity/NorthCarolinaFreshnessTest.java`
- `ui/socialsecurity/NorthCarolinaInputReportTest.java`
- `ui/wizard/NorthCarolinaUiAcceptanceTest.java`

**Modified existing tests — registration counts/choices and an added NC-only worksheet, no existing-state financial golden changes:**

- `domain/tax/state/NoIncomeTaxAdmissionTest.java`: 13 → 14 production jurisdictions.
- `domain/tax/state/simplified/IowaMississippiAdmissionWorksheetTest.java`: 13 → 14.
- `domain/tax/state/simplified/PennsylvaniaParticipantFactsAuditTest.java`: include NC in the production list.
- `domain/tax/state/simplified/PennsylvaniaProductionAdmissionAuditTest.java`: include NC in the production list.
- `ui/wizard/ArizonaUiAcceptanceTest.java`: selector count 13 → 14; Arizona financial/disclosure assertions unchanged.
- `domain/projection/PennsylvaniaProductionProjectionTest.java`: its all-jurisdiction bracket-fill worksheet adds the independently computed NC expectation `(243600 - 25500) × .0399 = 8702.19`; all prior-state expectations remain unchanged.

**Documentation:** this report was added. The preexisting untracked Phase 6E-C report was preserved without edits. No changes to federal tax, funding, conversion execution, `ProjectionEngine`, other state calculators/rules, persistence schemas, dependencies or build configuration.

## 5. Supported and excluded scenarios

Supports ordinary full-year domestic Single/MFJ residents using modeled fully taxable IRA/401(k)/403(b) receipts, opening/current RMDs, actual taxable conversions, qualified Roth withdrawals, private pensions, authoritative annual SS, existing admitted taxable annuity/conditional receipts, modeled ordinary investment income and brokerage-funded retirement spending. The current death-year status remains configured/MFJ; later surviving-filer years use Single.

Bailey, military and railroad eligibility/exclusions are not calculated. Pension names are never parsed. Generic ordinary pensions remain admitted under a prominently disclosed fully taxable private-pension assumption; missing government/private classification does not reject every pension. Current pension DTOs cannot represent reliable Bailey qualification, so automatic detection is not claimed. Explicit unclassified, special/exempt investment or contradictory/unsupported taxable-source evidence remains unavailable.

No general security-lot/basis accounting, capital-loss carryovers, special bond/state-basis adjustments, itemization, dependent/child benefits, specialized credits, business/portable-benefit adjustments, part-year moves or multistate sourcing/credits. Existing Advanced/annuity NIIT guards can independently refuse brokerage assets or unclassified nonqualified growth; NC admission does not bypass them.

## 6. Brokerage, conversion and funding verification

The accepted Arizona B3 regression behavior is preserved for NC:

- Actual brokerage withdrawals with empty taxable metadata are admitted. Gross proceeds reduce projected assets and fund cash flow; they do not create AGI or invented gains.
- Explicit taxable gain/dividend/proceeds attribution is checked against the already-modeled investment bucket and never added again. Positive attribution without corresponding modeled income, duplicate/excess totals and wrong-year evidence remain unavailable.
- Existing federal AGI is preserved. The model does not claim actual gains are legally zero. All required disclosures explain possible higher actual tax from unmodeled gains.
- Real 2027 principal/mixed/RMD/conversion fixtures verify ordinary brokerage sources, tax-funding cash conservation, repeated deterministic results and unchanged real balances. Two controlled +4%/−2% worlds match deterministic execution with identical paths/contexts.

Independent direct goldens include Single IRA $50,000 → **$1,486.28**; IRA $50,000 plus conversion $20,000 → **$2,284.28**; MFJ $100,000 ordinary income → **$2,972.55 / $2,600.05 / $2,413.80 / $2,227.55** at the four scheduled rates; brokerage $100,000 with no modeled taxable income → **calculated $0.00**. The direct fixtures assume no extra tax-funded income.

Independent funding worksheets establish **$93.51** traditional tax funding on $15,000 IRA spending with federal tax still zero, versus **$89.78** NC tax and no added withdrawal when household cash funds taxes. A $1,000 conversion on $12,250 IRA spending crosses the NC deduction floor and needs **$20.78** funding. These are not conversion-times-rate approximations.

The custom federal taxable-income target $5,000 has theoretical AGI $21,100, federal tax $500, NC tax $333.17 and conversion $5,266.83 when traditional assets fund taxes, versus conversion $6,100 when available cash funds them. **Existing convergence tolerance is retained:** a small target residual near raw NC tax $333.165 can produce $333.16 instead of $333.17. Tests compare funded cent outputs within the established cent tolerance and verify target income, while direct calculator goldens remain exact. No solver or federal behavior was changed to satisfy a fixture.

Projection acceptance covers ordinary withdrawals plus conversions, opening/current RMDs, actual/requested shortfall, source/destination owner matching, and a missing primary Roth destination that cannot be replaced by a spouse destination. A bounded annuity fixture retains taxable $51,360 of gross $60,000, with $294,240 conversion; 2030 MFJ AGI $345,600 produces NC **$10,371.24**, using the taxable receipt once and preserving federal tax/NIIT.

Calendar-rate tests cover each boundary and untriggered 2035/2040/2041 policy. The independent 2026–2034 $100,000-equivalent annual pension worksheet totals **$22,469.20** in state taxes; combined lifetime taxes equal federal plus state. The monthly pension representation is carried to 40 decimal places, far below cents; direct $100,000 calculator inputs are exact. Survivor fixtures use exactly $7,000/month to avoid recurring-decimal input at a half-cent boundary: $84,000 ordinary income gives **$2,041.65** death-year MFJ and **$2,486.63** later Single at 3.49%, for either spouse surviving.

## 7. Rule-version freshness and retained history

New NC full-plan completions capture `north-carolina-income-tax-rules = NC-2026-1` in the existing immutable `references` map. Integrated Quick/Exhaustive/individual/weighted success paths already pass their frozen plan to the shared completion method; that method now captures NC provenance even when conditional benefits are absent. MC completion reads its frozen plan document(s), including both sides of a comparison. SS-only results receive no state-tax reference.

Compatibility compares the captured NC value with the current reviewed component version. A changed version yields **OUTDATED**, preserving payload, taxes, limitations and context. It does not rewrite saved references, recalculate history, change fingerprint schemas or make old results unreadable. Developers must increment the NC version when reviewed NC calculation/admission rules change.

Existing-state references/fingerprints retain their prior behavior. State selection is already part of effective full-plan inputs. Tests cover current NC provenance, simulated prior NC rules, unchanged historical payload/context/digest, other-state/SS-only reference identity, either comparison side and actual MC snapshot codec round trip/current compatibility.

## 8. UI/export disclosures and persistence

Every calculated NC year contains five common categories plus annual AGI, taxable-SS subtraction, state deduction, taxable base, applied rate and version:

1. Ordinary NC retirement income and state deduction assumptions.
2. Brokerage realized-gain and special investment-source limitations.
3. All enacted rate periods, conditional-rate policy and future-law assumptions.
4. Bailey/military/railroad and special rollover/conversion exclusions.
5. Standard-deduction-only scope, specialized adjustments, ordinary basis/qualification assumptions, federal-model limits and planning rounding.

Selection help, current analyzer inputs, result range/detail tooltips and result-time individual/MC contexts expose these categories. NC range/PDF rendering includes all frozen common notes rather than inheriting Arizona's three-paragraph cap. Annual arithmetic remains in yearly details/CSV. Unsupported results retain structured reasons with absent estimates.

NC textual state codes round-trip through existing plan persistence, saved baselines and scenario copies. Retained snapshots restore original annual taxes/limitations. Historical CSV/PDF tests preserve intentionally older recorded notes after the current plan moves to Florida; the NC PDF state label comes from the result. MC and individual exports retain captured metadata. Tests cover both Single/MFJ CSV paths and non-investable values. Existing baseline reference semantics are not redesigned.

## 9. Focused and bounded regression results

- Initial independent calculator run: **34 tests, zero failures/errors/skips**, BUILD SUCCESS (`target/phase6ed-domain.log`).
- Final NC focused acceptance: **66 tests, zero failures/errors/skips**, BUILD SUCCESS (`target/phase6ed-focused.log`). Includes calculator/attribution, funding, annual schedule/survivors, projection, real brokerage, persistence, PDF/CSV, MC, integrated SS/weighted, metadata/freshness and JavaFX checks.
- Corrected focused/regression checks: **98 tests, zero failures/errors/skips**, BUILD SUCCESS (`target/phase6ed-corrected-focused.log`), including all NC tests, the existing all-state Pennsylvania projection worksheets and unchanged `Stage5DComparisonTest`.
- Initial broad bounded run: **4,800 tests, 2 failures, 0 errors, 35 skips**, BUILD FAILURE, 8 minutes 50 seconds (`target/phase6ed-bounded.log`). One failure was the newly enumerated NC jurisdiction falling into an old zero-tax default in the all-state worksheet; its independent NC expectation was added without changing existing-state values. The other was `Stage5DComparisonTest.callbackFailureCleansUpOutstandingWorkers`, an existing Michigan-based worker-termination timing assertion; all 23 tests in that unchanged class passed in the subsequent focused run.
- Final bounded regression: **4,800 tests, 0 failures, 0 errors, 35 skips**, **BUILD SUCCESS**, 9 minutes 6 seconds (`target/phase6ed-bounded-final.log`). One Surefire retry was permitted for the observed timing failure, but **no retry/flaky-test outcome was needed**. The unchanged Stage 5D class and corrected all-state worksheet both passed on the first attempt in this run. Skipped visual/optional tests are not claimed as manual acceptance.

Initial test development caught a missing test helper type/codec call, inherited Arizona-only report expectations and the exact half-cent/convergence fixture issues described above. These were corrected in the new tests without changing existing financial expected values or production funding/federal behavior. No failing test run is represented as passing.

Commands used IntelliJ bundled Maven and the required project-local repository:

```powershell
$maven = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd'
$repository = '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository'
& $maven $repository '-Dtest=NorthCarolinaProductionTaxCalculatorTest' test
& $maven $repository '-Dtest=NorthCarolina*Test' test
& $maven $repository '-Dtest=NorthCarolina*Test,PennsylvaniaProductionProjectionTest,Stage5DComparisonTest' test
& $maven $repository '-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*PerformanceAudit*,!*Exhaustive*,!*Search*' '-Dsurefire.rerunFailingTestsCount=1' test
```

The bounded suite excludes the named benchmark/capacity/performance/exhaustive/search classes as requested. It is not an unrestricted full-suite claim. Existing Michigan, Illinois, Pennsylvania, Iowa and Arizona goldens, Arizona brokerage regressions and applicable annuity/freshness/report tests are included. No new large MC/exhaustive search was introduced.

## 10. Remaining blockers and limitations

**No remaining blocker to the approved bounded NC admission was found.** Required automated acceptance checks and the final bounded regression passed. Specialized legal exclusions and unmodeled gains are the approved scope limitations, not promises of automatic eligibility detection. Existing federal omissions and funding tolerances remain disclosed.

Final 2026 return instructions should be reviewed when published; current constants are established from enacted law and official estimated-tax guidance. Native manual desktop acceptance of a user's actual private plan has not been performed; automated JavaFX/report checks are distinguished from manual steps below.

## 11. Production admission status

**North Carolina is admitted as the 14th production jurisdiction within the approved bounded planning scope.** Verified rules, attribution/brokerage admission, funded conversions, annuity restrictions, all five visible/frozen disclosure categories, NC rule-version freshness, persistence/reporting and existing-state regressions passed their required automated gates. No Kentucky or Colorado routing was added. Admission describes this local implementation; no deployment or Git publication is implied.

## 12. Native Windows acceptance

1. Use Java 25 and the bundled Maven commands above. Review focused and bounded logs for BUILD SUCCESS. Run no unrelated benchmark/exhaustive search for this acceptance.
2. Launch `MainApplication` from IntelliJ. Open a disposable plan copy. Select North Carolina in the existing State of Residence control; confirm 14 choices and all five assumption/limitation categories. Other state choices remain available.
3. Run Single and MFJ plans with SS, ordinary private pension/IRA income, opening/current RMDs, qualified Roth cash and taxable conversions. Check annual rates at 2026/2027, 2029/2030 and 2032/2033. Check death-year MFJ and following Single.
4. Use a real brokerage account for retirement spending. Missing basis alone must not make the NC projection unavailable. Check that proceeds fund cash flow and reduce assets without a gross-proceeds AGI addition. Inspect tax-funding withdrawals, Medicare, retained cash and conversion source/destination owners.
5. Inspect result-range and year-detail notes. Verify Bailey/military/railroad exclusions, gain omissions, standard deduction, conditional-rate policy and planning rounding. Do not expect specialized benefits to be inferred from account/pension names.
6. Save/reload; capture a baseline/scenario result and export PDF/CSV. Change the active state after retaining a result and confirm the original NC state, taxes and recorded limitations remain in historical exports. Optional small MC/integrated runs must retain their result-time NC notes.
7. If testing an admitted annuity, use its existing verified terms and calculation mode. Existing Advanced/NIIT unsupported-input safeguards remain in force; do not add fake basis/gain evidence to bypass them.

## Repository verification and stop point

Initial `git status --short` showed only the preexisting untracked Phase 6E-C audit. That file remains untouched. Final `git status` shows the listed implementation/test changes and new files, with no staging. `git diff --check` passed; separate no-index checks of all 14 newly added implementation/test/report files produced no whitespace diagnostics. Source files retain Windows line endings and UTF-8 encoding. No commits, pushes, tags, deletions of project files or Git-history changes.

Stop after Phase 6E-D reporting. No further state work is authorized.
