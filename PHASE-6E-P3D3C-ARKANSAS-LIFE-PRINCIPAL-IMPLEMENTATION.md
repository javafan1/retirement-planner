# Phase 6E-P3D3C — Arkansas qualified life insurance death benefit

November 20, 2026. Deterministic Basic-mode implementation.

## 1. Executive summary

Arkansas now admits qualifying personal term-life and whole-life death principal for deterministic Basic-mode projections. The policy pays once to the other eligible modeled household member in the insured's modeled death year. The federal and Arkansas taxable principal are zero. The existing household cash settlement uses the gross payment for the year's expenses, Medicare and taxes. Unspent cash joins projected retained nonqualified assets and participates in investment growth beginning the next year.

Each benefit-bearing Arkansas result carries `AR-LIFE-PRINCIPAL-P3D3C-1`, including completed years with no payment. The engine and archive readers check recorded receipt evidence against the frozen definitions and modeled deaths. Advanced mode, ordinary conditional income, and financial analyzers retain their refusal boundaries.

## 2. Git baseline and preserved work

The phase opened on `main` at `2bedb55e99fb48aa31225e9c5549c842d77ebb2d`. The index and tracked worktree were clean. The only nonignored untracked path was the existing P3D3A report. Local tracking matched `origin/main` with ahead/behind counts `0 0`. This implementation used local Git inspection. The prior audit records its live remote verification limit. No changes were staged, committed or pushed.

Reviewed the audit and the prior admission, proof, history and premiums reports. The untracked P3D3A report was preserved. All monetary examples here are synthetic. [P3D3A audit](PHASE-6E-P3D3A-ARKANSAS-CONDITIONAL-BENEFITS-ARCHITECTURE.md) · [P3D2B](PHASE-6E-P3D2B-ARKANSAS-CONDITIONAL-INCOME-AND-UI-AUDIT.md) · [P3D2E](PHASE-6E-P3D2E-ARKANSAS-CAPPED-EXCLUSION-PROOF.md) · [P3D2G](PHASE-6E-P3D2G-ARKANSAS-SOURCE-REACHABILITY-PROOF.md) · [P3C2](PHASE-6E-P3C2-ARKANSAS-DURABLE-HISTORY-PROVENANCE.md) · [Phase 23C](PHASE-23C-INSURED-CONTINGENT-PREMIUM-EXPENSES.md)

## 3. Approved scope and legal assumptions

The exclusion applies to the modeled principal of an ordinary qualifying personal policy payable by reason of death. IRC §101(a) supplies the federal exclusion subject to its exceptions. IRC §7702 defines life insurance for federal tax purposes. Arkansas independently applies its adoption of IRC §101 as of January 1, 2007 under Ark. Code Ann. §26-51-404(b)(3). Rule 26 CAR §130-119(x) corroborates the state exclusion and treats interest as a separate taxable amount. The program requires the existing personal-life principal qualification affirmation. It does not determine legal entitlement. [IRC §101](https://uscode.house.gov/us/usc/t26/s101/) · [IRC §7702](https://uscode.house.gov/us/usc/t26/s7702/) · [26 CAR §130-119(x)](https://codeofarrules.arkansas.gov/Rules/Rule?chapterID=33&levelType=section&partID=946&sectionID=40283&subChapterID=262&subPartID=6196&titleID=26) · [Arkansas DFA tax-year 2025 instructions](https://www.dfa.arkansas.gov/wp-content/uploads/2025_AR1000F_and_AR1000NR_Instructions.pdf)

The approved 2026 Arkansas rules and existing future-year planning treatment remain in effect. This change does not assume Arkansas adopts later federal amendments. The Arkansas grant of income-tax exclusion does not alter the application's modeled after-tax investable estate measure.

## 4. Existing model reuse

The implementation uses `ConditionalBenefitDefinition`, `ConditionalBenefitEvaluator`, `ConditionalBenefitReceipt` and `IncomeSourceAttribution.conditional`. It uses the existing `DEATH_BENEFIT`, `ONE_TIME_LUMP_SUM`, `QUALIFIED_LIFE_INSURANCE_DEATH_PRINCIPAL_EXCLUDED`, `PERSONAL_LIFE_PRINCIPAL_ONLY_V1` and `HOUSEHOLD_RETAINED_NON_QUALIFIED_CASH` values. The definition schema has the same fields and enums. Federal `TaxIncomeAttribution` adds only the receipt's taxable amount. The existing `ConditionalBenefitExecutionVerification` remains the analyzer verifier for already-admitted workflows. The deterministic archive has its own verification against the frozen input DTOs. [ConditionalBenefitDefinition.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitDefinition.java) · [ConditionalBenefitEvaluator.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitEvaluator.java) · [ConditionalBenefitReceipt.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitReceipt.java) · [TaxIncomeAttribution.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/TaxIncomeAttribution.java)

## 5. Exact Arkansas admission policy

The entire definition list is checked. It must consist only of explicitly qualified single-principal lump sums payable upon the other modeled member's death. Existing definition validation checks IDs, amount, coverage and household references. The default effective Basic mode applies to legacy plans with no explicit mode and no annuity. Explicit Basic mode is also supported. Advanced fails without changing the configured mode. Admission is independent of whether the payment falls in the horizon. An otherwise valid pending definition can have zero actual receipts. [ConditionalBenefitAdmission.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitAdmission.java)

## 6. Annual source and tax reconciliation

Controlled Arkansas projection supplies run-only life-principal evidence derived from the exact admitted definitions, original opening and effective death view. The Arkansas reconciler checks the annual household and active members, death and opening evidence, and the exact list of attributed receipts. This equality includes the full immutable receipt and its ID, amount, tax split, label, insured, recipient, trigger, dates and destination. Duplicate, omitted, substituted and unqualified payments fail closed, including an unsupported zero amount masquerading as a valid payment. A qualified receipt cannot carry retirement receipt metadata. The scoped path requires no annual NIIT calculation. For a validated qualified payment the gross enters household cash and zero enters both ordinary tax bases or the retirement-exclusion pool.

The annual evidence remains attached through exact funding candidates and both state lower-bound calculations. Arkansas source mismatches use `LIFE_PRINCIPAL_EVIDENCE`. Advanced refusal uses `NIIT`. Unknown classifications, actual shortages and proof-budget exhaustion remain distinguishable. [ArkansasTaxEvidence.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasTaxEvidence.java) · [ArkansasIncomeReconciliation.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasIncomeReconciliation.java) · [ArkansasTaxIssue.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasTaxIssue.java) · [ArkansasProjectionValidation.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ArkansasProjectionValidation.java) · [ArkansasProductionTaxCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasProductionTaxCalculator.java)

## 7. Death and beneficiary timing

January 1 is the existing modeled event date. Coverage is inclusive. The recipient must be alive in the year and be the other modeled household member. A pre-opening event produces no receipt, even during a partial first year. The last executed year may receive a benefit. A later death can leave a valid definition with no receipt. The shared event evaluator produces no household payment when the recipient dies first or both die in the same year. These latter scenarios were verified at event selection. Supplying a lifetime or other nonconfigured projection context to run this new Arkansas financial capability remains out of scope. Filing status and survivor Social Security and pension timing rely on the existing policies. Annual deaths do not establish real-world survival-hour eligibility.

## 8. Household cash conservation

The engine uses the existing one-time selection, adds gross payments once to guaranteed household cash and settles the year's obligations exactly. The selected funding result creates a single retained household surplus. No persisted account receives a separate credit. Future retained growth and retained-first withdrawals use the existing portfolio. The Medicare-aware annual pass recalculates from the same opening portfolio and source receipts. Only the final pass is published. [ProjectionEngine.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java) · [ArkansasAnnualFunding.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasAnnualFunding.java) · [HouseholdCashSettlement.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/HouseholdCashSettlement.java)

The synthetic 2027 oracle uses a separately scheduled `$97,168` conversion and 1.5% federal parameter growth. The federal standard deduction becomes `$32,683` and the 10% joint bracket ends at `$25,172`. Federal tax is `$2,517.20 + 12% × $39,313 = $7,234.76`. Arkansas holds its reviewed rules. The `$6,000` retirement exclusion and `$4,940` deductions leave `$86,228` of net income. The Arkansas marginal slices yield `$2,823.236` before `$58` of credits, producing `$2,765.24` of tax.

| Receipt-year oracle | Amount |
| --- | ---: |
| Eligible death principal | $100,000.00 |
| Same-year ordinary obligations | $40,000.00 |
| Independent federal and Arkansas tax due | $10,000.00 |
| Discretionary spending and tax withdrawals | $0.00 |
| Retained household cash | $50,000.00 |
| Growth on current-year proceeds | $0.00 |
| Next-year growth on retained opening at 5% | $2,500.00 |

A funded zero-benefit control holds taxable inputs constant. The two cases have equal AGI, taxable Social Security, direct Medicare MAGI and federal and Arkansas taxes. The cases use different cash sources for obligations. Separate target tests show the indirect tax-funding and conversion effects when the benefit displaces taxable withdrawals. [ArkansasLifePrincipalProjectionTest.java](src/test/java/com/daviddunn/retirementplanner/domain/projection/ArkansasLifePrincipalProjectionTest.java)

## 9. Medicare and IRMAA

Excluded life principal is neither included in federal AGI nor treated as tax-exempt interest. The existing modeled Medicare MAGI input therefore sees no direct change. The Medicare-aware pass has one receipt and one authoritative premium. Eligibility still uses the surviving modeled person's age and status. Changes to actual IRA withdrawals or conversions may change modeled MAGI indirectly. This phase uses the application’s established same-year AGI estimate for Medicare. SSA's ordinary administrative process generally uses a prior return. [SSA Medicare premiums](https://www.ssa.gov/benefits/medicare/medicare-premiums.html)

## 10. Roth conversion and funding proof effects

The existing Arkansas fixed and targeted methods receive the new post-spending portfolio and tax-cash intercept. They price actual conversion and withdrawal sources against the existing federal and Arkansas bases. The independent RMD fixture uses `$1,530,000 ÷ 25.5 = $60,000` and a separate `$10,000` conversion. It verifies `$70,000` AGI, the RMD taken in full, and no tax-funding withdrawal despite the additional `$100,000` pay-in.

In the bracket-fill fixture, the eligible payout covers spending and taxes, permitting a `$133,000` conversion at the joint `$100,800` federal taxable ceiling. The fixture with no payout needs positive tax withdrawals and supports a smaller conversion. In the inherited-source witness, the payout reduces the deferred spending withdrawal to `$5,000`. With the existing pension and bracket ceiling, the independently derived combined conversion/funding cap is `$154,600`. Exact tax funding is `$44,402.52`, conversion is `$110,197.48`, and no inherited account debit occurs.

The proof algorithms, order, rates and budgets remain unchanged. The configured limits are 512 exact funding evaluations per pass, 4,096 cumulative inner evaluations, 512 outer intervals, 160 boxes per interval, and 8,192 total boxes. `AR-FUNDING-P3D2E-1` and `AR-TARGET-P3D2G-1` retain their existing meaning. [ArkansasMinimumFundingSolver.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasMinimumFundingSolver.java) · [ArkansasRothFunding.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasRothFunding.java) · [ArkansasTargetBounds.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasTargetBounds.java)

## 11. Premium and estate behavior

The existing insured-contingent `INSURED_ALIVE_FIXED_NOMINAL_MONTHS_V1` premium remains optional and independent. The test charges a `$1,200` premium while the insured lives and zero in the modeled death year. There is no automatic deduction, lapse, refund, linked policy account or duplicate-expense merge. Users can enter more than one expense independently. Distinct policy IDs may generate multiple legitimate receipts with the same label.

Term face value and potential future whole-life principal carry no before-death asset value in this model. The payout increases modeled after-tax investable estate through actual available household balances. No non-investable policy asset, tax-deferred heir haircut on cash, separate insurance receivable, probate or estate-tax result is created. [ContractualPremiums.java](src/main/java/com/daviddunn/retirementplanner/domain/financial/ContractualPremiums.java) · [AfterTaxEstateCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/estate/AfterTaxEstateCalculator.java)

## 12. User interface

The existing Income workspace's Conditional Benefits section offers **Add Life Insurance Death Benefit** in Arkansas. The dialog constrains category, death trigger, one-time amount and excluded tax character. It requires insured, beneficiary, amount and an explicit personal-policy affirmation. Coverage dates remain editable. The dialog explains January 1 timing and retained household cash routing. A single-person Arkansas plan cannot add the benefit. Unsupported existing records can still be edited and retained. Advanced mode has a distinct NIIT message and never switches modes automatically. The existing detached Save/Cancel behavior, required field feedback and themes remain in use. [ConditionalBenefitsPane.java](src/main/java/com/daviddunn/retirementplanner/ui/views/ConditionalBenefitsPane.java) · [ConditionalBenefitDialog.java](src/main/java/com/daviddunn/retirementplanner/ui/dialogs/ConditionalBenefitDialog.java) · [ProjectionFailureMessages.java](src/main/java/com/daviddunn/retirementplanner/ui/ProjectionFailureMessages.java)

## 13. PDF and reporting

The existing Arkansas PDF now reports gross principal, zero taxable principal, Arkansas-excluded principal, recipient, payment year and retained household routing from the frozen annual evidence. A scoped year without a receipt identifies that outcome. The existing View PDF / Save PDF paths continue to use the same report. A text extraction test verifies the fields from a restored snapshot. The CSV schema and analyzer PDF contracts are unchanged. [ArkansasValidationReport.java](src/main/java/com/daviddunn/retirementplanner/app/export/ArkansasValidationReport.java)

## 14. Scoped policy and provenance

The annual versions map adds `arkansas-life-principal=AR-LIFE-PRINCIPAL-P3D3C-1` only when the frozen Arkansas input has at least one admitted definition. The version tuple retains `AR-2026-P2-1`, `AR-FUNDING-P3D2E-1` and `AR-TARGET-P3D2G-1`. The retained reader recognizes the exact added tuple alongside the prior tuples. Completed annual receipt structures retain gross, taxable and excluded principal without a new payment or account record format.

`CompleteProjectionSnapshot` ties the scoped policy to a frozen deterministic input document, original horizon, opening, modeled deaths and precise receipt selection. It also checks the expected receipt cash plus Social Security and pension sources against annual guaranteed household cash. The retained accounting reader checks cash settlement, balances and original source metadata. Reading or swapping a scoped result verifies the pure receipt selection and stored facts without running the financial projection or rewriting the artifact. A change to definitions invalidates the live match even if no benefit would occur. [ArkansasValidationTax.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ArkansasValidationTax.java) · [ArkansasAnnualProvenance.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ArkansasAnnualProvenance.java) · [RetainedProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java) · [CompleteProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java) · [ConditionalBenefitExecutionSemantics.java](src/main/java/com/daviddunn/retirementplanner/app/projection/ConditionalBenefitExecutionSemantics.java)

## 15. Historical v1 and v2 compatibility

The existing plan and annual schemas, conditional benefit fingerprint normalization, analyzer receipts and reader versions remain in use. The no-benefit Arkansas tuple is unchanged. Earlier funding and target versions remain readable and historical under the existing current-policy comparison. The v1 Quick and v2 weighted and paired evidence contracts retain their original allowed policy maps. Deterministic scoped history cannot seed those analyzers. The new controller test exercises definition save and reload, deterministic Save, Save As, Open and swap with original UUID, frozen input, provenance and byte-identical canonical annual evidence. Existing tests verify no-benefit Arkansas archives and non-Arkansas life-benefit history. [ArkansasFundingProvenanceTest.java](src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ArkansasFundingProvenanceTest.java) · [ArkansasLongevityValidationTest.java](src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/ArkansasLongevityValidationTest.java) · [ArkansasMonteCarloLaunchTest.java](src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/ArkansasMonteCarloLaunchTest.java) · [LifeInsuranceHistoryTest.java](src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/LifeInsuranceHistoryTest.java)

## 16. Deterministic test coverage

The focused fixtures exercise both insured directions, inclusive coverage start/end, first and final projection year, outside coverage and horizon, no death, pre-opening death, no-payment scoped evidence, separately timed premiums, independent policy IDs, zero and positive principal, same-year spending and taxes, subsequent growth, RMD, Roth target, recorded IRMAA input, the premium-aware pass, and PDF restoration. The inherited-source witness validates the exact post-spending capacity and the unchanged source proof. The same-year and beneficiary-first multiple-death cases exercise the ordinary event evaluator while the new Arkansas analyzer admission remains closed. [ArkansasLifePrincipalProjectionTest.java](src/test/java/com/daviddunn/retirementplanner/domain/projection/ArkansasLifePrincipalProjectionTest.java) · [LifeInsuranceDialogTest.java](src/test/java/com/daviddunn/retirementplanner/ui/dialogs/LifeInsuranceDialogTest.java)

## 17. Negative and boundary case coverage

Tests reject missing qualification, insured-as-recipient, single-person and absent references, duplicate definitions, duplicate receipts, substituted benefit IDs and amounts, omitted or unsupported zero receipts, recurring and mixed life principal, a taxable insurer payment relabeled as generic death income, commercial annuities, generic conditional income outside the horizon, Advanced mode, missing or unknown scoped versions, and a scoped attempt to seed an analyzer. Domain-construction errors are distinct from Arkansas admission or annual evidence refusals. A rejected Advanced request leaves the last successful result installed. [ArkansasLifePrincipalReconciliationTest.java](src/test/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasLifePrincipalReconciliationTest.java) · [ArkansasControlledWorkflowTest.java](src/test/java/com/daviddunn/retirementplanner/ui/controller/ArkansasControlledWorkflowTest.java)

## 18. Analyzer admission boundaries

The shared analyzer admission continues to refuse Arkansas conditional definitions. The configured deterministic route is separately admitted. The controller, Arkansas provenance capture and controlled projection engine also refuse scoped analyzer use, including paired dependencies, observed analysis calls and nonconfigured financial contexts. Fixed and longevity Monte Carlo, integrated Social Security, individual mortality, weighted financial analysis, Arkansas/Arkansas and mixed-state paired analysis keep their previous admission contracts. The stored fingerprint formats and ordinary non-Arkansas behavior remain unchanged. Unsupported execution never becomes an insufficient-funds outcome. [ConditionalBenefitAdmission.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitAdmission.java) · [ApplicationController.java](src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java) · [ArkansasAnalysisProvenance.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ArkansasAnalysisProvenance.java)

## 19. Maven verification

IntelliJ bundled Maven and Java 25 used the workspace-only Maven repository and isolated per-run test profiles. The bounded selector excludes benchmark, capacity, performance, exhaustive and search-named suites. No full weighted performance run was requested or performed.

```powershell
$m = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd'
$repo = "-Dmaven.repo.local=$((Get-Location).Path)\.codex-m2\repository"
& $m $repo "-Duser.home=$profile" -o -B "-Dtest=$selector" test
git diff --check
git diff --cached --check
```

```text
ArkansasLifePrincipalProjectionTest,ArkansasLifePrincipalReconciliationTest,ArkansasFundingProvenanceTest,ArkansasLongevityValidationTest,ArkansasControlledWorkflowTest,LifeInsuranceDialogTest,ArkansasCappedExclusionProofTest,ArkansasSourceReachabilityTest,ArkansasAccountFundingTest,ArkansasTargetCertificationTest
*,!*Benchmark*,!*Capacity*,!*Performance*,!*Exhaustive*,!*Search*
```

| Run | Executed including skips | Passed | Skipped | Failures | Errors | Exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| [Focused acceptance](target/phase6e-p3d3c/focused-acceptance.log) | 104 | 104 | 0 | 0 | 0 | 0 |
| [First bounded regression](target/phase6e-p3d3c/bounded.log) | 5,563 | 5,527 | 35 | 1 | 0 | 1 |
| [Isolated Stage 5D cancellation check](target/phase6e-p3d3c/stage5d-recheck.log) | 2 | 2 | 0 | 0 | 0 | 0 |
| [Bounded repeat](target/phase6e-p3d3c/bounded-repeat.log) | 5,563 | 5,528 | 35 | 0 | 0 | 0 |

The first bounded run reached the complete selection. The sole failure was the final `Thread.isAlive()` assertion in the fallback case of the unchanged `Stage5DCancellationTest`. Its earlier executor-termination and queued-work assertions passed. Both parameterized cases passed in isolation with `-Dtest=Stage5DCancellationTest` and in the bounded repeat. The bounded repeat used the same selection and `-Dsurefire.rerunFailingTestsCount=2`. Surefire recorded one flake in the unchanged `Stage5DCoordinatorTest.reverseCompletionStillReturnsOriginalPositionsAndReleasesThreads` at its thread-liveness assertion. That case passed on its second attempt and is included in the 5,528 passes. The focused run took 1:10, the first bounded run took 17:48, and the successful bounded repeat took 9:07.

The early focused runs identified fixture issues involving exact federal precision, use of deceased-owner assets in a comparison, expense type, canonical encoding and weighted baseline metadata. The fixtures were corrected against the established contracts. Existing financial rules and expected historic results were preserved. The recorded bounded run is the release regression for this phase.

## 20. Cancellation and proof work

The new Arkansas evidence is confined to the annual controlled run and contains an immutable list. The Medicare pass and Roth/funding proof reuse existing cancellation and publication boundaries. The targeted cancellation fixture cancels inside Arkansas Roth execution before a year publishes. The controlled workflow tests also cover stale and cancelled requests and preserve the last success.

| Synthetic target witness | Outer intervals | Exact Roth trials | Exact inner funding evaluations | Arkansas proof calls | Boxes |
| --- | ---: | ---: | ---: | ---: | ---: |
| Proceeds fund the year's obligations and taxes | 0 | 1 | 1 | 0 | 0 |
| Inherited source remains beyond the reachable funding prefix | 34 | 2 | 42 | 247 | 213 |

These are deterministic test-fixture work counts for the reported annual proof. They do not estimate performance for every Arkansas plan or establish analyzer capacity.

## 21. Remaining unsupported cases

Advanced NIIT, fully taxable generic or survivor-only income, taxable windfalls and insurer interest, mixed payments, delayed payouts, installments, named account credit, external or contingent beneficiaries, estate or trust settlements, employer-owned or transferred-for-value policies, annuity death benefit or basis expansion, cash value, loans, surrender, lapse, refunds, legal beneficiary adjudication, probate and estate tax remain outside P3D3C. Financial analyzers with Arkansas life benefits remain unavailable. The deterministic result continues to refuse otherwise unsupported source, timing or tax circumstances under Arkansas's existing rules.

## 22. Native Windows acceptance

1. In a synthetic Arkansas couple plan choose Basic, configure the insured's modeled death year and open **View → Income → Conditional Benefits**.
2. In Light and Dark modes open **Add Life Insurance Death Benefit**. Check keyboard and mouse focus, required affirmation, visible footer, and coverage fields at normal and compact window sizes. Cancel a changed amount, reopen and verify the saved value.
3. Project with an eligible survivor and inspect the receipt year, gross/excluded principal, zero taxable principal, spending and retained cash. Repeat with the insured surviving the horizon and verify the scoped zero-payment history.
4. Exercise **View PDF** and **Save PDF**, then Save, Save As, Close and Open. Verify the completed receipt and original balances persist. Capture a baseline and swap the roles after a new accepted run.
5. Switch to Advanced and attempt a projection. Verify the NIIT explanation and the previous successful result. Attempt a full-plan analyzer from the accepted scoped result and verify the refusal. Cancel a longer deterministic run and verify the previous accepted result remains installed.

Automated Windows JavaFX dialog and controlled workflow checks cover the stated fixtures. Direct interactive native desktop acceptance is pending review.

## 23. Readiness

**Implementation verified and ready for review.** Focused verification and the bounded repeat passed with the Stage 5D timing observations recorded above. Direct interactive Windows acceptance remains pending. P3D3D and any expansion of the supported cases require separate approval.

### Files changed

Production Java paths below are relative to `src/main/java/com/daviddunn/retirementplanner/`.

- `domain/benefit/ConditionalBenefitAdmission.java`
- `domain/projection/ArkansasAnnualProvenance.java`, `ArkansasProjectionValidation.java`, `ArkansasValidationTax.java`, `ProjectionEngine.java`
- `domain/tax/ArkansasAnnualFunding.java`
- `domain/tax/state/ArkansasIncomeReconciliation.java`, `ArkansasTaxEvidence.java`, `ArkansasTaxIssue.java`
- `app/projection/CompleteProjectionSnapshot.java`, `ConditionalBenefitExecutionSemantics.java`
- `app/socialsecurity/results/ArkansasAnalysisProvenance.java`, `RetainedProjectionSnapshot.java`
- `app/export/ArkansasValidationReport.java`
- `ui/ProjectionFailureMessages.java`, `ui/controller/ApplicationController.java`, `ui/dialogs/ConditionalBenefitDialog.java`, `ui/views/ConditionalBenefitsPane.java`

Test Java paths below are relative to `src/test/java/com/daviddunn/retirementplanner/`.

- Added `domain/projection/ArkansasLifePrincipalProjectionTest.java` and `domain/tax/state/ArkansasLifePrincipalReconciliationTest.java`
- Updated `app/socialsecurity/ArkansasLongevityValidationTest.java`, `app/socialsecurity/results/ArkansasFundingProvenanceTest.java`, `ui/controller/ArkansasControlledWorkflowTest.java` and `ui/dialogs/LifeInsuranceDialogTest.java`

This report was added. The P3D3A audit remains the preexisting untracked report. P3D3D has not started.
