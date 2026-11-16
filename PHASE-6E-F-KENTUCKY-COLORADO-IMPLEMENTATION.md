# Aledrix Retirement Planning — Phase 6E-F
# Kentucky implementation and isolated Colorado preparation

Implementation/verification date: **2026-11-16**. Follows the accepted [Phase 6E-E audit](PHASE-6E-E-KENTUCKY-COLORADO-TAX-AUDIT.md) and the explicit Phase 6E-F decisions. Kentucky and Colorado have independent admission gates. No other state was implemented; no staging, commit, push or tag.

## 1. Implementation summary

**Kentucky is implemented using the existing annual state-tax boundary.** One small calculator and immutable `KentuckyTaxRules` component use modeled federal AGI, subtract federally taxable SS and separately capped eligible retirement receipts, apply one actual-joint/Single deduction, apply 3.5%, and subtract eligible nonrefundable age credits. Exact `BigDecimal` intermediates are preserved; final planning tax is HALF_UP cents and nonnegative.

**Colorado remains unregistered.** Its final election certification/effective-law and conversion-interpretation requirements were not established from authoritative evidence. Verified 2026 worksheet components are isolated under `domain/tax/state/colorado`: a rules component that refuses future years, a CO-only ordinary federal-base bridge, and proportional SS/personal-cap arithmetic. They have no production caller or jurisdiction enum entry. No guessed 2027 schedule or conversion eligibility was inserted. A `ColoradoProductionTaxCalculator` and production integration were deliberately not created ahead of those legal gates.

**Kentucky has passed its independent gates and is admitted as the 15th production jurisdiction.** The finished-source bounded regression result is recorded in Sections 8–11. Colorado's isolated tests are not described as production acceptance.

## 2. Legal verification and source-specific boundaries

### Kentucky

| Rule implemented | Authoritative basis, rechecked 2026-11-16 |
|---|---|
| 3.5% from 2026 | [KRS 141.020(2)(f)–(g)](https://apps.legislature.ky.gov/law/statutes/statute.aspx?id=56339), [enacted HB 1](https://apps.legislature.ky.gov/record/25RS/hb1.html). Further conditional reductions require legislative action; none is forecast. |
| Standard deductions | DOR [2026 $3,360 announcement](https://revenue.ky.gov/News/Pages/Kentucky-DOR-Announces-2026-Standard-Deduction.aspx), [2027 $3,470 announcement](https://revenue.ky.gov/News/Pages/Kentucky-DOR-Announces-2027-Standard-Deduction.aspx). The approved 2028+ nominal $3,470 carry-forward is a disclosed approximation, not the statutory CPI calculation under [KRS 141.081](https://apps.legislature.ky.gov/law/statutes/statute.aspx?id=29067). |
| AGI adjustments / $31,110 individual retirement limit | [KRS 141.019(1)(e)–(g)](https://apps.legislature.ky.gov/law/statutes/statute.aspx?id=57914): federally taxable SS subtraction; ordinary pension/annuity/retirement distributions. No general age minimum or cap transfer is imported. |
| Confirmed IRA conversion | DOR [Schedule M instructions, PDF page 3](https://revenue.ky.gov/Forms/Schedule%20M%20%282025%29.pdf#page=3) expressly include regular IRA-to-Roth conversion income and require separate taxpayer/spouse exclusion calculations. The continuing rule was checked against current statute. |
| Ordinary employer withdrawals | KRS 141.019 and Schedule M cover ordinary written employer retirement plans, including 401(k)/similar deferred compensation. IRA/rollover IRA, ordinary 401(k)/403(b) cash distributions and RMDs are admitted within the fully taxable ordinary-plan assumption. Employer-to-Roth/in-plan conversion variants remain unsupported pending source-specific review. |
| Actual joint filing | [103 KAR 17:020 §2](https://apps.legislature.ky.gov/law/kar/titles/103/017/020/), [Form 740](https://revenue.ky.gov/Forms/740%20%282025%29.pdf). Modeled MFJ uses actual-joint computation with one deduction; no separate-on-combined optimizer. Individual retirement exclusions remain separate. |
| $40 age credit | KRS 141.020(3) and DOR [Schedule ITC, Section B](https://revenue.ky.gov/Forms/Schedule%20ITC%20%282025%29.pdf#page=2). Applies once per qualifying annual filer, floors tax at zero; a deceased filer is evaluated at modeled January 1 death, not aged through later birthdays. |
| Specialized treatment | DOR [Schedule P](https://revenue.ky.gov/Forms/Schedule%20P%20%282025%29.pdf) covers government/service-history situations outside this implementation. No plan qualification is inferred from names. Generic private pensions are admitted under the approved ordinary written-plan assumption. |

Federal basic/age/enhanced-senior deductions are not copied into the KY base. The family-size credit, itemization, blindness/other credits, business/foreign adjustments, special bond/basis treatment and multistate/part-year taxation remain disclosed exclusions. Specialized tax facts that contradict the ordinary supported classification remain unavailable. No new qualification UI was added.

### Colorado: verified preparation versus unresolved law

The [2026 DR 0104EP worksheet, PDF page 3](https://tax.colorado.gov/sites/default/files/documents/DR_0104EP_2026.pdf#page=3) supports the isolated **4.4% 2026** rate. The [DOR current income-tax guide](https://tax.colorado.gov/individual-income-tax-guide) establishes federal taxable income as the starting base and the 2026+ standard/itemized deduction addback above $300,000 AGI, using $1,000 Single/$2,000 joint limits for that deduction. These were not substituted into federal tax calculations.

The [official Legislative Council OBBBA analysis](https://content.leg.colorado.gov/sites/default/files/images/lcs_june_2025_forecast_updated_with_obbba_fiscal_impacts.pdf#page=12) identifies federal senior deductions flowing into Colorado and the overtime addback. The bridge uses ordinary domestic nondependent standard-deduction/valid-SSN assumptions, verified 2026 IRS basic/age amounts from [Rev. Proc. 2025-32 §3.14](https://www.irs.gov/pub/irs-drop/rp-25-32.pdf), and the temporary senior deduction/phaseout described in [IRS guidance](https://www.irs.gov/newsroom/working-families-tax-cuts-individuals-and-workers). [IRS Publication 501](https://www.irs.gov/publications/p501) establishes federal age 65 on the day before the birthday, including at death; the bridge has a dedicated tested federal-age check rather than borrowing the state pension-age calculation. Unmodeled itemization, foreign MAGI, blindness and other federal deductions are not certified by the bridge. Future years are refused rather than extrapolated.

The [DOR pension/SS publication](https://tax.colorado.gov/income-tax-topics-social-security-pensions-and-annuities) supports the isolated $20,000/$24,000 personal caps, age/AGI SS rules, proportional joint taxable-SS allocation and SS priority. The helper accepts only amounts already qualified by its future caller; it does not establish conversion, early-IRA, death-benefit, inherited-account or annuity-maturity qualification.

**Independent Colorado legal blockers:**

1. Final statewide certification/proclamation/effective law for Amendment 87 was not established. The official [2026 election calendar](https://www.sos.state.co.us/pubs/elections/calendars/2026ElectionCalendar.pdf#page=13) places the canvass deadline on November 25, after the November 16 verification date. Official statewide result access was not successfully obtained; unofficial election calls are insufficient under the authorization.
2. The resulting legal interaction with Proposition 136 was not certified. The [official Amendment 87 text](https://leg.colorado.gov/ballots/graduated-income-tax-2/files/text-of-measure) and [Proposition 136 text](https://leg.colorado.gov/ballots/income-tax-rate-limit-2/files/text-of-measure) describe submitted measures, not proof of final adoption. No six-bracket 2027 production schedule was coded and no indefinite 4.4% future forecast was used.
3. The [current CCR index](https://www.sos.state.co.us/CCR/DisplayRule.do?action=ruleinfo&ruleId=1936&seriesNum=1%20CCR%20201-2) identifies the current compilation, but current Rule 39-22-104(4)(f) was not successfully reconciled to the IRA-conversion application. DOR's IRA/nonpremature rules read with [IRS conversion guidance](https://www.irs.gov/publications/p590a) support a legal inference; the [State Auditor's evaluation](https://content.leg.colorado.gov/sites/default/files/2021_te20_pension_or_annuity_deduction.pdf) also describes a Roth-contribution/distribution exclusion requiring careful distinction. The production gate is not waived by that inference.

Complete current-law recheck, source admission and funding convergence tests are still required before CO admission. The pension-cap repeal bills cited in the audit were not treated as enacted law.

## 3. Per-person attribution decisions

Kentucky aggregates eligible taxable amounts in a state-local `EnumMap<AccountOwnership, BigDecimal>` and caps each owner once. It does not modify shared attribution or persisted income schemas.

* Traditional/RMD/opening-RMD sources require account/type, known fully taxable amount and active recipient. Per-category totals must reconcile exactly to modeled traditional income; unowned residuals are refused.
* Conversions require a confirmed traditional/rollover IRA source and same-owner Roth IRA destination with conversion purpose. Actual execution enters AGI once. Employer conversion eligibility is not generalized from IRA guidance.
* Pension income requires actual recipient attribution and fully taxable ordinary-plan treatment. A joint/unowned pension cannot receive two caps. Survivor sources use current recipient while preserving original pension owner.
* Already-admitted annuity receipts supply the taxable amount and finite basis evidence. Only their taxable portion enters the pool. Generic taxable conditional receipts remain ordinary non-pension income; retirement/death trigger names do not create exclusions.
* KY subtracts household federally taxable SS; individual allocation is unnecessary for that full subtraction. CO's isolated helper uses the official gross-benefit ratio and preserves the total with high-precision division plus residual allocation, never an equal split.

Wrong-year, partial-basis, taxable Roth, inherited-history, unknown/unclassified and excess/contradictory evidence is refused under the existing unsupported-result contract, without a fabricated numeric estimate. Represented tax-deferred account receipts cannot masquerade as taxable investment proceeds even when their amount matches the investment bucket. Missing ordinary brokerage basis is handled differently, as described next.

## 4. Brokerage policy and verification

Ordinary unknown-basis brokerage withdrawals are admitted for Kentucky under the accepted B3 policy. Gross proceeds fund cash flow and reduce assets; they do not become AGI. Explicit taxable gain/dividend/proceeds evidence reconciles against the existing modeled investment-income bucket and is not added again. Positive duplicate/excess evidence or special/exempt-source contradictions remain unsupported. No tax lots, basis reconstruction, invented gains or gain exemption was added.

Independent direct fixtures include:

| 2026 Single, age 60, no incremental funding | KY tax |
|---|---:|
| $100,000 ordinary brokerage proceeds, no modeled taxable income | $0.00 |
| IRA $50,000 | $543.55 |
| IRA $50,000 plus conversion $20,000, same owner | $1,243.55 |
| IRA $50,000 plus modeled investment income $5,000, with any admitted ordinary proceeds | $718.55 |
| MFJ age 60/62, IRA $50,000 each; one joint deduction | $1,204.70 |
| MFJ all $100,000 traditional income belongs to one spouse | $2,293.55 |

Real brokerage regression tests cover principal-only, mixed income and RMD/conversion paths, source presence, no principal double taxation, unchanged live balances and cash conservation. Three scenarios each run two controlled +4%/−2% MC worlds against deterministic projections with identical economic paths. Existing annuity/Advanced NIIT brokerage/growth restrictions remain in force independently of state admission.

## 5. Roth conversions, funding and capacity reproduction

`BracketFillCapacityEvidenceTest` ran before shared-code correction and passed: the existing NC bracket-fill path requests beyond a $1,000 executable IRA capacity and attachment retains an unowned residual. The test remains as an explicit existing-state behavior check.

`KentuckyFundingTest.capacityLimitedTrialDoesNotInventUnownedIncome` then reproduced Kentucky's strict owner-admission failure before the fix. The narrow correction in `RothConversionBracketFillCalculator` applies **only when the jurisdiction is KY**: cap each trial to existing same-owner executable capacity, and return that capacity when the target remains above the feasible ceiling. No old jurisdiction enters the new branch. No owner is invented, no federal rule is changed, and no one-pass conversion-tax calculation replaces iterative funding.

Final ProjectionEngine execution remains unchanged: RMD first, requested versus actual conversion retained, owner-specific source/destination capacity, then actual executed conversion into final tax funding. Tests include missing primary Roth destination, actual household/owner totals and requested shortfall. Employer-source conversions remain unavailable under the source restriction even when the generic converter can execute them.

Independent funding worksheet, Single age 60 in 2026 with IRA $50,000 and no tax cash:

```text
Federal base tax = 3,820; incremental federal bracket = 12%
KY base tax = 543.55; incremental KY rate = 3.5% after cap exhaustion
W = (3,820 + 543.55) / (1 − .12 − .035) = 5,163.964497…
```

Existing cent-tolerance solution is about $5,163.97 additional withdrawal, federal tax $4,439.68 and KY $724.29. Cash-funded tax preserves KY $543.55. A separate bracket-fill worksheet targets federal taxable income $40,000: AGI $56,100, federal tax $4,552, KY $757.05, conversion approximately $35,790.95 after funding; with ample cash conversion is $41,100. Tests preserve the established $0.01 solver tolerance.

CO's SS and $300,000 deduction thresholds create discontinuities. No production CO convergence claim is made from isolated arithmetic tests. Cross-threshold funding/bracket-fill acceptance remains required after legal admission inputs are resolved.

## 6. Supported/excluded scenarios and future-year assumptions

Kentucky supports full-year domestic Single/actual joint filers with ordinary fully taxable traditional IRA/rollover IRA and employer 401(k)/403(b) receipts, opening/current RMDs, verified IRA conversions, qualified Roth cash, ordinary private pensions, admitted taxable nonqualified annuities, authoritative SS, modeled ordinary investment income, brokerage spending and survivor recipient/status transitions.

Each recipient's eligible retirement receipts share one nominal $31,110 pool. Qualified Roth/principal is not income or another subtraction. Senior credit is $40 per qualifying annual filer, nonrefundable. Actual joint filing has one deduction. Years 2026 and 2027 use published $3,360/$3,470 amounts; 2028+ holds $3,470 nominally with disclosure. 3.5% continues until a reviewed enactment changes it; conditional reductions are not predicted.

Excluded: employer-plan conversion variants pending review; specialized government/pre-1998 service, military/railroad qualification; inherited history and partial traditional basis; taxable nonqualified Roth; itemization, family-size and ancillary credits; special investment sourcing/basis/carryovers; foreign/business adjustments, part-year residence and multistate tax. Names do not establish classifications and generic private pensions are not blanket-refused. Existing federal omissions remain combined-tax limitations.

Annual schedule tests independently verify 2026–2034 state taxes totaling **$20,291.15** for one $100,000 annual pension under a joint return with the modeled age-credit commencement. Both death directions verify recipient transfers and death-year MFJ/later Single treatment. No future Colorado schedule, annuity maturity assumption, early IRA exception or special death-benefit eligibility is promoted into production.

## 7. Disclosures, persistence and rule freshness

Kentucky has five complete common limitation paragraphs plus immutable annual arithmetic/recipient diagnostics. All material categories appear in selector help, result summary, yearly-detail tooltip, shared analyzer inputs, individual/integrated report context, standalone/paired MC context and PDF/CSV. PDF/summary common limitations are not truncated to Arizona's three paragraphs. CSV includes a Kentucky limitations column in both Single and MFJ layouts, preserving noninvestable fields.

Plan JSON uses stable `KY` through existing assumptions persistence. Baseline/scenario/reload tests exercise the same engine. Historical snapshots retain original tax amounts and limitation strings. Tests deliberately replace recorded notes with older text, change the active plan to Florida, then verify original KY labels/notes/taxes in restored CSV/PDF. No historical recalculation or schema migration was introduced; existing baseline reference semantics remain unchanged.

Independent provenance key `kentucky-income-tax-rules` captures `KY-2026-1` from newly completed frozen full-plan inputs, including either MC comparison side. Changed saved KY version marks results outdated while preserving payload/context/digest. SS-only and preexisting-state reference behavior is unchanged. Colorado's separate `colorado-income-tax-rules` identifier belongs only to its isolated preparatory component; it is not recorded as a completed production CO analysis or inserted into existing-state freshness maps.

## 8. Focused and independent admission checks

Commands used IntelliJ bundled Maven and only the required project-local `.codex-m2/repository`:

```powershell
$maven = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd'
$repository = '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository'
& $maven $repository '-Dtest=BracketFillCapacityEvidenceTest' test
& $maven $repository '-Dtest=KentuckyProductionTaxCalculatorTest' test
& $maven $repository '-Dtest=KentuckyFundingTest' test
& $maven $repository '-Dtest=KentuckyFundingTest,BracketFillCapacityEvidenceTest' test
& $maven $repository '-Dtest=Kentucky*Test,ColoradoPreparatoryRulesTest,BracketFillCapacityEvidenceTest' test
& $maven $repository '-Dtest=Kentucky*Test,ColoradoPreparatoryRulesTest,BracketFillCapacityEvidenceTest,PennsylvaniaProductionProjectionTest,NorthCarolinaUiAcceptanceTest,ArizonaUiAcceptanceTest' test
& $maven $repository '-Dtest=KentuckyProductionTaxCalculatorTest,ColoradoPreparatoryRulesTest,Stage5DComparisonTest' test
& $maven $repository '-Dtest=Kentucky*Test,ColoradoPreparatoryRulesTest,BracketFillCapacityEvidenceTest,Stage5DComparisonTest' test
```

Results retained under `target/phase6ef-*.log`:

* Capacity evidence before shared change: **1 passed**, `phase6ef-capacity-evidence.log`.
* Initial Kentucky calculator: **15 passed**, `phase6ef-ky-domain.log`.
* Funding reproduction before correction: **3 tests, 1 expected diagnostic error** in the new capacity test; other two passed, `phase6ef-ky-funding-before.log`. This failing run is not admission evidence.
* Funding after correction plus unchanged NC capacity evidence: **4 passed**, `phase6ef-ky-funding-after.log`.
* An intermediate compile attempted the preparatory CO tests before their new components were present and failed on those missing classes, `phase6ef-ky-focused-initial.log`; corrected by adding the planned components.
* Initial combined focused checks: **51 passed**, including 8 isolated CO worksheet cases, `phase6ef-focused.log`.
* Focused expansion with JavaFX/all-state worksheets: **63 passed**, `phase6ef-focused-final.log`; annual/lifetime expansion: **64 passed**, `phase6ef-focused-complete.log`.
* Final new-state acceptance after ordinary-source and actual survivor coverage: **62 passed, zero failures/errors/skips**, `phase6ef-acceptance.log`. This selector differs from the preceding run; counts are not additive unique test totals.
* Final boundary tests reproduced two issues before correction (traditional-account investment misclassification and federal senior birthday convention); **53 tests, 2 diagnostic failures**, while the unchanged Stage 5D class passed all 23, `phase6ef-boundary-before.log`.
* Finished-source focused acceptance including the two corrections and unchanged Stage 5D: **87 passed, zero failures/errors/skips**, `phase6ef-final-focused.log`.

The final CO subset has **9 passing worksheet cases**, including the federal birthday/death boundary. CO tests prove only verified 2026 base/addback and SS/personal-cap arithmetic, unchanged original federal results, future-year refusal and disabled admission. They do not establish legal conversion qualification, production routing, future-year lifetime tax or integrated funding/reporting acceptance.

## 9. Existing-state bounded regression

```powershell
& $maven $repository '-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*PerformanceAudit*,!*Exhaustive*,!*Search*' test
& $maven $repository '-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*PerformanceAudit*,!*Exhaustive*,!*Search*' '-Dsurefire.rerunFailingTestsCount=1' test
```

Initial bounded run: **4,864 tests, 1 failure, 0 errors, 35 skips**, BUILD FAILURE, 8 minutes 46 seconds (`target/phase6ef-bounded.log`). The sole failure was the unchanged Michigan-based `Stage5DComparisonTest.callbackFailureCleansUpOutstandingWorkers` thread-termination timing assertion, also documented as transient in Phase 6E-D. All 23 tests in that unchanged class passed on the direct rerun. No unrelated worker-lifecycle code or test was modified. The two new boundary tests were subsequently reproduced and corrected as recorded above.

Final finished-source bounded regression: **4,866 tests, 0 failures, 0 errors, 35 skips**, **BUILD SUCCESS**, 9 minutes 23 seconds (`target/phase6ef-bounded-final.log`). One Surefire retry was permitted for the observed timing flake, but **no retry/flaky-test outcome occurred**. The unchanged Stage 5D class passed its 23 tests on the first attempt. Optional/visual skips are not represented as manual acceptance.

The bounded suite includes applicable MI/IL/PA/IA/AZ/NC financial goldens, Arizona B3 and NC brokerage regressions, source/annuity/funding contracts, reporting/persistence/freshness, plus the new checks. Unrelated benchmark/capacity-probe/performance/exhaustive/search classes are excluded as authorized. No unrestricted full-suite or large-MC performance claim is made. Existing financial expectations were not changed: the all-state worksheet adds only the independent KY expectation; selector counts/lists add KY while keeping CO disabled.

## 10. Exact files added/modified

The file inventory below is generated from the final working-tree changes. The preexisting untracked Phase 6E-E report is preserved and excluded from this phase's change inventory.

Production/support source (22 files):

- `src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionCsvExporter.java` (modified)
- `src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java` (modified)
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AcceptedAnalysisCompletion.java` (modified)
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java` (modified)
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisVersions.java` (modified)
- `src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitAdmission.java` (modified)
- `src/main/java/com/daviddunn/retirementplanner/domain/benefit/NonqualifiedAnnuityAdmission.java` (modified)
- `src/main/java/com/daviddunn/retirementplanner/domain/model/StateTaxJurisdiction.java` (modified)
- `src/main/java/com/daviddunn/retirementplanner/domain/roth/RothConversionBracketFillCalculator.java` (modified)
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/KentuckyProductionTaxCalculator.java` (added)
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ProductionStateTaxService.java` (modified)
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/colorado/ColoradoFederalTaxBaseBridge.java` (added)
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/colorado/ColoradoRetirementAdjustments.java` (added)
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/colorado/ColoradoTaxRules.java` (added)
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/kentucky/KentuckyTaxRules.java` (added)
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRunService.java` (modified)
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonRunService.java` (modified)
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualReportContext.java` (modified)
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAnalyzerInputSummary.java` (modified)
- `src/main/java/com/daviddunn/retirementplanner/ui/summary/ProjectionYearDetailsPane.java` (modified)
- `src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java` (modified)
- `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java` (modified)

Tests (20 files):

- `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/KentuckyBrokerageMonteCarloRegressionTest.java` (added)
- `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/KentuckyIntegratedAcceptanceTest.java` (added)
- `src/test/java/com/daviddunn/retirementplanner/domain/projection/KentuckyAnnualScheduleTest.java` (added)
- `src/test/java/com/daviddunn/retirementplanner/domain/projection/KentuckyBrokerageProjectionRegressionTest.java` (added)
- `src/test/java/com/daviddunn/retirementplanner/domain/projection/KentuckyFundingTest.java` (added)
- `src/test/java/com/daviddunn/retirementplanner/domain/projection/KentuckyProjectionAcceptanceTest.java` (added)
- `src/test/java/com/daviddunn/retirementplanner/domain/projection/KentuckyReportingAcceptanceTest.java` (added)
- `src/test/java/com/daviddunn/retirementplanner/domain/projection/PennsylvaniaProductionProjectionTest.java` (modified)
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/BracketFillCapacityEvidenceTest.java` (added)
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/KentuckyProductionTaxCalculatorTest.java` (added)
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/NoIncomeTaxAdmissionTest.java` (modified)
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/colorado/ColoradoPreparatoryRulesTest.java` (added)
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/IowaMississippiAdmissionWorksheetTest.java` (modified)
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/PennsylvaniaParticipantFactsAuditTest.java` (modified)
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/PennsylvaniaProductionAdmissionAuditTest.java` (modified)
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/KentuckyFreshnessTest.java` (added)
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/KentuckyInputReportTest.java` (added)
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/ArizonaUiAcceptanceTest.java` (modified)
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/KentuckyUiAcceptanceTest.java` (added)
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/NorthCarolinaUiAcceptanceTest.java` (modified)

Added documentation: `PHASE-6E-F-KENTUCKY-COLORADO-IMPLEMENTATION.md`. Existing test edits only add KY selector/count/worksheet coverage; existing-state financial values remain unchanged.

No dependencies, Java version, build configuration, federal tax calculator, other-state tax rules, persistence schemas, ProjectionEngine financial branches or analyzer architecture were changed.

## 11. Separate production admission status

**Kentucky: ADMITTED as the 15th production jurisdiction within the approved bounded scope.** Verified rules, complete owner reconciliation, ordinary brokerage admission, actual/funded IRA conversions, senior credits, survivor/annual/lifetime calculations, annuity restrictions, persistence/history/disclosures, KY-specific rule freshness, focused checks and the bounded existing-state regression gate passed. No deployment or Git publication is implied.

**Colorado: NOT ADMITTED.** No `CO` enum, selector choice, production service route, annuity allowlist entry or production completion/version metadata was added. The verified preparatory helpers are isolated and unused by production. Completing final legal status, current conversion interpretation, year-aware bridge/age qualification, unsupported-source boundaries, discontinuity funding, history/disclosures and the full independent CO integration gate remains necessary. The supported-state count is not claimed to be 16.

## 12. Remaining blockers and scope decisions

No new approval was requested for ordinary authorized implementation/testing. Colorado is stopped at the material legal verification boundary required by the authorization; an unofficial result or guessed rule cannot resolve that boundary. Once authoritative evidence is available, the remaining CO work must still pass its own financial/integration gates. No common source was silently treated as qualifying solely to reach a state count.

Kentucky's explicit limitations remain planning approximations, not hidden blockers: actual-joint election, nominal future deductions, ordinary private-plan assumption, omitted family-size/itemized/ancillary adjustments, and unmodeled realized brokerage gains. Employer conversion variants remain specifically unsupported instead of preventing unrelated IRA/pension projections.

## 13. Native Windows acceptance instructions

1. Launch through the existing IntelliJ Java 25/JavaFX configuration. State selection should contain **15 jurisdictions**, including Kentucky and North Carolina, with Colorado absent.
2. Select Kentucky for a full-year Single retiree; verify IRA $50,000 in 2026 gives direct calculator tax $543.55 before additional funded withdrawals; age 65 gives $503.55. Adding $20,000 eligible IRA conversion at age 60 gives $1,243.55 before funding.
3. For MFJ, put $50,000 IRA income on each owner, then all $100,000 on one owner. Confirm different individual exclusions with one joint deduction. Review age credits through the death year and later Single years, and pension recipient/original-owner details.
4. Use actual brokerage spending without basis. Proceeds must fund cash and reduce assets without a gross-proceeds income addition. Inspect actual conversions, RMDs, funding withdrawals, Medicare and retained surplus. Existing Advanced annuity/NIIT refusals must still explain unsupported inputs.
5. Exercise fixed/bracket-fill conversions, exhausted sources and missing same-owner Roth destination. Actual conversion and owner totals must match executed transfers; unsupported employer conversions must not silently receive IRA treatment.
6. Inspect all five common limitation categories in help, normal summary/year details and analyzer assumptions. Compare 2026/2027/2028 deductions; future carry-forward must be disclosed.
7. Save/reload and compare baseline/scenario results. Export Single/MFJ CSV and PDF, including noninvestable values. Export a retained KY result after editing current residence to Florida: original KY label, tax and notes must remain.
8. Open bounded individual/MC/integrated results; confirm frozen metadata and outdated status under an older KY rules reference. Do not run exhaustive search or large random simulations to perform this smoke test.

Automated JavaFX tests passed, but these human desktop checks are instructions, not a claim that a manual interactive review was performed.

## 14. Repository verification and stop point

Initial `git status --short` contained only the preexisting untracked `PHASE-6E-E-KENTUCKY-COLORADO-TAX-AUDIT.md`. That document and unrelated user work were preserved. Final `git status` lists the in-scope source/test/report changes plus that preserved audit. `git diff --check` passed; new untracked Java/Markdown files were also checked directly for trailing whitespace because ordinary Git diff excludes untracked files. No stage, commit, push or tag; no other state or unrelated feature was started. **Stop after Phase 6E-F reporting.**
