# Phase 6E-P3D3C3 — Arkansas Regular Tax Only

November 21, 2026. Bounded deterministic planning implementation.

## 1. Executive summary

Arkansas Basic deterministic projections offer **Regular Tax Only — Low-Income Relief Not Modeled**. Strict remains the default. The new selection computes the existing regular Arkansas tax model exactly and omits the §26-51-301 low-income relief and the §26-51-501(a)(6) additional credit. It permits supported survivor projections even when Strict cannot establish low-income eligibility. It preserves exact federal and Arkansas tax funding and Roth settlement, and discloses the approximation on the accepted projection and PDF. The actual private 2028 plan was not used for this implementation. Native review remains a separate acceptance step.

## 2. Git baseline and preserved work

The opening branch was `main`, HEAD `2bedb55e99fb48aa31225e9c5549c842d77ebb2d`, even with the local `origin/main` tracking reference. The index was empty. Twenty-three modified tracked files and ten untracked files were already present. The phase builds on the pending P3D3A–P3D3C2 work. The reports and diagnostics were reviewed alongside the accepted funding and source reachability proof contracts. Synthetic cases and local work logs were used throughout. [P3D3C life principal](PHASE-6E-P3D3C-ARKANSAS-LIFE-PRINCIPAL-IMPLEMENTATION.md) · [P3D3C1 audit](PHASE-6E-P3D3C1-ARKANSAS-DEATH-LOW-INCOME-AUDIT.md) · [P3D3C2 diagnostics](PHASE-6E-P3D3C2-ARKANSAS-NATIVE-LOW-INCOME-DIAGNOSTICS.md) · [P3D2E funding proof](PHASE-6E-P3D2E-ARKANSAS-CAPPED-EXCLUSION-PROOF.md) · [P3D2G source reachability proof](PHASE-6E-P3D2G-ARKANSAS-SOURCE-REACHABILITY-PROOF.md)

## 3. Approved calculation policy

| Selection | Modeled annual result |
|---|---|
| **Strict — Verify Low-Income Eligibility** | Stable regular zero is accepted under the existing policy. Positive regular tax is accepted only if existing evidence rules out both possible low-income provisions. The original typed refusal and both strict inequalities remain. |
| **Regular Tax Only — Low-Income Relief Not Modeled** | The supported regular worksheet supplies the state liability. The two omitted provisions are immaterial to that selected model. All validation outside those provisions still applies. |

The setting is Arkansas-specific and explicit. The engine never substitutes zero tax for unavailable regular tax and never falls back from Strict to Regular Tax Only on a refusal.

## 4. Legal and modeling boundaries

- **§26-51-301** contains the low-income filing-category exemptions, credit and eligibility conditions. Subsection (g) uses all-source gross income. Subsection (h) and DFA's instructions distinguish the election from claiming specified pension/IRA relief or itemized deductions. The DFA low-income lookup and the regular tax table are separate. Omitting the §301 route leaves the ordinary §26-51-201 rate schedule, bracket adjustment and ordinary deductions intact. §301's references to other provisions are eligibility restrictions for its relief. [Arkansas Act 483 of 2021, §6](https://arkleg.state.ar.us/Acts/FTPDocument?ddBienniumSession=2021%2F2021R&file=483.pdf&path=%2FACTS%2F2021R%2FPublic%2F) · [Arkansas 2026 Act 1](https://arkleg.state.ar.us/Home/FTPDocument?path=%2FACTS%2F2026S1%2FPublic%2FACT1.pdf) · [DFA 2025 instructions and tables](https://www.dfa.arkansas.gov/wp-content/uploads/2025_AR1000F_and_AR1000NR_Instructions.pdf)
- **§26-51-501(a)(6)** adds a separate, income-limited, nonrefundable credit for timely filers. DFA uses net taxable income and doubles its scheduled amount for MFJ. The ordinary personal and senior credits in §501 and the modeled 65 Special election remain. The statutory regular zero-rate band is part of §201 and remains available. [Arkansas Act 1 of 2021, Second Extraordinary Session, §10](https://webftp.blr.arkansas.gov/Home/FTPDocument?path=ACTS%2F2021S2%2FPublic%2FSearchable%2FACT1.pdf) · [DFA 2025 instructions and additional-credit worksheet](https://www.dfa.arkansas.gov/wp-content/uploads/2025_AR1000F_and_AR1000NR_Instructions.pdf)
- The current ordinary Arkansas bridge retains the per-recipient $6,000 eligible retirement pool, $2,470 modeled standard deduction per filer, ordinary personal and applicable senior credits, and exclusion of federally included Social Security. The ordinary worksheet contained no computation of either omitted benefit before this phase. Both modes use the reviewed `AR-2026-P2-1` cents policy and its nominal continuation for later years. Support remains an estimate for the existing full-year resident Single and actual MFJ filing and source domain, subject to the earlier qualification and tax-law limitations. Published 2025 table amounts are comparison evidence, separate from the 2028 modeled proof ceilings or actual 2028 law. [DFA 2025 instructions, ordinary pension, deduction and 65 Special rules](https://www.dfa.arkansas.gov/wp-content/uploads/2025_AR1000F_and_AR1000NR_Instructions.pdf) · [Arkansas 2026 Act 2](https://arkleg.state.ar.us/Home/FTPDocument?path=%2FACTS%2F2026S1%2FPublic%2FACT2.pdf) · [ArkansasJointWorksheet.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/arkansas/ArkansasJointWorksheet.java) · [ArkansasRateWorksheet.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/arkansas/ArkansasRateWorksheet.java)

## 5. UI and persisted configuration

Economic Assumptions → Income Tax Assumptions shows **Arkansas Low-Income Tax Treatment** for Arkansas residence. The keyboard-focusable selector and wrapped help use the existing draft Apply/Cancel and required-field conventions. Changing states hides the Arkansas control and keeps the preference dormant so a return to Arkansas restores the selected value. Regular Tax Only is available in the existing plan editor. Guided plan creation starts with Strict. The tests exercise reversible editing and Light and Dark styling at 700 pixels.

`StateTaxAssumptions` stores one optional enum property. The original wire shape is emitted for Strict, including when selected explicitly. Missing legacy values restore Strict, unknown strings or ordinal values refuse, and the alternative has a stable serialized name. Other states use their existing calculation modes. [AssumptionsView.java](src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java) · [StateTaxAssumptions.java](src/main/java/com/daviddunn/retirementplanner/domain/model/StateTaxAssumptions.java) · [ArkansasLowIncomePolicy.java](src/main/java/com/daviddunn/retirementplanner/domain/model/ArkansasLowIncomePolicy.java)

## 6. Exact annual execution and source restrictions

The controller captures the existing immutable input document before dispatch. `ArkansasProjectionValidation` reads the effective policy from that plan and attaches it to the annual funding facts along with the same cash, life receipt, pension, retirement and death evidence. Every exact fixed, targeted or terminal funding evaluation uses those facts. On Medicare recalculation the engine uses the same frozen policy and opening portfolio with the premium added to spending. Regular Tax Only changes only the two `LOW_INCOME` checks after the ordinary election is determined and the corresponding proof eligibility conditions. Source reconciliation remains ahead of policy admission. [ArkansasProjectionValidation.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ArkansasProjectionValidation.java) · [ArkansasAnnualFunding.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasAnnualFunding.java) · [ArkansasProductionTaxCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasProductionTaxCalculator.java)

Absent or inconsistent dates, recipient/owner evidence, pension origin, retirement basis and receipt evidence continue to affect regular worksheet admission when material. Reached inherited accounts that lack supported evidence, unsupported additional tax, unrelated state/source rules, and Advanced NIIT remain outside this capability. The deterministic feature keeps the qualifying Basic life-principal category and its existing exclusion. It makes no change to general conditional income. [ArkansasIncomeReconciliation.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasIncomeReconciliation.java) · [ConditionalBenefitAdmission.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitAdmission.java)

## 7. Funding and Roth proof compatibility

The Strict inner proof can decline even a conservative positive state floor near unresolved relief. Simply removing the exact guard while keeping that fallback can cause cent-by-cent candidate evaluation to run into the 512 evaluation allowance. Regular Tax Only uses the existing regular liability envelope without requiring a low-income exclusion certificate. This is a scoped proof-premise change, with no change to either solver's search or limits.

- **Inner proof.** Over the existing homogeneous funding source segment, original and capped retirement pools retain their documented bounds. Every additional eligible receipt adds at most its taxable ordinary increment to the unused exclusion, so net taxable income cannot fall below the previously proven left bound for any permitted election. Evaluate the existing regular schedule over the full enclosed net-input range, including the rate-adjustment steps. Subtract each election's ordinary credits, round by the existing rule, and minimize over the permitted elections. The result bounds even the best regular election from below. The existing state/federal floor, strict pruning inequality, candidate repricing and cash reconciliation then apply. An unresolved age/source/cap condition still declines the proof. A conclusive pruned interval contains no sufficient executable withdrawal under the selected model.
- **Outer proof.** Retain the federal envelope, source-prefix and reachability checks. Each existing target box bounds ordinary increments and the maximum/minimum retirement relief for every election, and hence its taxable net range. Minimum regular tax across the enclosed range and across elections is a valid state floor throughout that box, even across bracket-adjustment boundaries and credit floors. A higher conversion can only be excluded by the existing certified bounds or exact trial. Final capacity, low-income status under Strict, federal bracket feasibility, minimum funding and settlement are evaluated through existing authorities. An unsupported source or proof budget exhaustion remains typed unavailable.
- **Identifiers and limits.** Strict keeps `AR-2026-P2-1`, `AR-FUNDING-P3D2E-1` and `AR-TARGET-P3D2G-1`. Regular Tax Only adds `AR-REGULAR-TAX-ONLY-P3D3C3-1` and uses `AR-FUNDING-P3D3C3-1` and `AR-TARGET-P3D3C3-1` for the policy-specific floor premise. The regular worksheet identity and `AR-LIFE-PRINCIPAL-P3D3C-1` remain applicable. Both paths use the same 512 per-pass exact funding cap, 4,096 cumulative exact inner evaluations, 512 outer intervals, 160 boxes per interval and 8,192 total boxes.

[ArkansasMinimumFundingSolver.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasMinimumFundingSolver.java) · [ArkansasRothFunding.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasRothFunding.java) · [ArkansasTargetBounds.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasTargetBounds.java) · [ArkansasValidationTax.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ArkansasValidationTax.java)

A local synthetic probe checked both worksheet lower envelopes against independent regular cents arithmetic at 3,535 points in 35 intervals. It covered the regular bands, each high-schedule adjustment transition, and a positive low-income-region case with a saturated retirement pool. Neither envelope exceeded the independent liability at any sampled point. The interval argument above supplies the continuous bound beyond those sampled cents.

## 8. Independent oracles and permanent survivor matrix

All examples use synthetic values. Most open in 2028 and apply the same reviewed nominal tax inputs in the next year. The non-Medicare spouse-death fixture uses the $60,000 original pension and $30,000 direct survivor pension. The full payout is $100,000 of excluded principal. The death-year filer remains MFJ. The following survivor year uses Single. [ArkansasRegularTaxOnlyTest.java](src/test/java/com/daviddunn/retirementplanner/domain/projection/ArkansasRegularTaxOnlyTest.java)

| Scenario | Disposition and independent oracle |
|---|---|
| Both members alive, above both ceilings, Strict and alternative | `SUPPORTED_SUCCESS`. With $60,000 pension, subtract $6,000 and $4,940. State tax is $609.60 + 3.7% of $22,660 − $58, rounded to $1,390.02. Federal tax is $2,840. |
| First death, $30,000 pension and $30,000 obligations, no policy | `EXPECTED_STRICT_REFUSAL` in Strict, `SUPPORTED_SUCCESS` under Regular Tax Only. Net before tax funding is $19,060. Regular tax before funding is $302.04 and federal tax stays zero. The first sufficient cent is $312.67, since `roundCents(302.04 + 0.034w)` equals $312.67 at both $312.66 and $312.67. |
| First death, $70,000 obligations, no policy | `SUPPORTED_SUCCESS` under Regular Tax Only. $40,000 taxable spending draw, $6,880.22 tax funding, $2,014.59 state and $4,865.6264 federal. A withdrawal one cent lower is insufficient on the same source segment. |
| First death, same $70,000 obligations, qualifying payment | `EXPECTED_STRICT_REFUSAL` when both bounds remain unresolved. `SUPPORTED_SUCCESS` under Regular Tax Only. No taxable spending or funding draw, $302.04 state, $0 federal, and $59,697.96 unspent household cash. Federal AGI and modeled IRMAA MAGI both remain $30,000. |
| First death, gross-only, net-only and both Strict checks unresolved | `EXPECTED_STRICT_REFUSAL`, classified through the prior diagnostic. Alternative `SUPPORTED_SUCCESS`. Gross-only case has $38,000 taxable ordinary conversion and no eligible pool, giving $33,060 net and $798.02 state tax. The net-only case has $40,000 ordinary, one $6,000 pool, $29,060 net and $650.02 state. The both-unresolved case yields $302.04 state and zero additional draw with the receipt. |
| Later survivor | `SUPPORTED_SUCCESS` under the alternative. $21,530 taxable net after one pool and deduction. Regular state tax $415.02 and federal tax $1,420. |
| Zero regular liability, first-year retention and next-year growth | `SUPPORTED_SUCCESS`. $104,800 guaranteed including life principal less $54,800 spending gives $50,000 retained with zero state and federal tax. Retained opening growth is zero in the payment year, then $2,500 at 5% the next year. One receipt occurs. Evidence retains the policy in the zero-tax year. |
| Targeted Roth with funded tax at a capacity boundary | `SUPPORTED_SUCCESS`. Converts the full $10,000 executable capacity. $40,000 ordinary yields $650.02 state and $780 federal. $1,430.02 tax funding comes from available Roth assets and adds zero ordinary income. The balance/capacity and funded federal target verify optimality. |
| Inherited source beyond an independently proved ordinary prefix | `SUPPORTED_SUCCESS` in both modes. At the federal target, exact conversion is $110,197.48 and tax funding $44,402.52. Arkansas tax is $8,470.52 with one capped $6,000 exclusion. The regular-only proof is subject to the same reachability requirements as Strict. |
| Material unresolved pension origin | `EXPECTED_UNSUPPORTED_SOURCE_REFUSAL` in both modes, with typed `PENSION_ORIGIN`. Advanced returns the existing typed NIIT boundary, and alternate Arkansas analyzer entry is refused. |

The receipts, current funding and annual balance equations are asserted independently from the tax calculator. The same categories and first-death/later-survivor fixtures should be required when onboarding further states.

## 9. Life insurance, Medicare and IRMAA

Life principal enters household cash once. It supplies expenses and tax funding without entering regular taxable income, the retirement pool, RMD, Roth conversion or IRMAA directly. The insured's contractual premium ends in the modeled death year, as before. A separate RMD fixture distributes $60,000 and converts $10,000, against $70,000 of AGI despite receiving excluded principal. The next-year test proves growth on retained balances only after unspent proceeds have settled.

In the Medicare fixture, $19,440 survivor pension plus the $100,000 payout cover the original $119,440 obligations. Strict fails on the Medicare-aware pass. Regular Tax Only funds the $2,434.80 base premium from IRA spending cash, then $20.10 from the same funding source for exact regular tax. Final AGI is $21,894.90 and federal tax remains $0. The existing premium lookup continues to report the initial $19,440 modeled MAGI. The frozen benefit and policy are used in both passes. [ProjectionEngine.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java) · [ArkansasRegularTaxOnlyTest.java](src/test/java/com/daviddunn/retirementplanner/domain/projection/ArkansasRegularTaxOnlyTest.java)

## 10. Historical evidence and analyzer boundaries

The new preference is part of the frozen deterministic input and resulting digest. Annual Arkansas context uses the exact new tuple, with a separate accepted combination when P3D3C life principal applies. Restoring a complete Arkansas result checks the tuple against the saved input policy and deterministic route. Both Current and Baseline point to immutable versions of their own inputs and annual evidence. Save, Save As, Open and swap retain UUIDs, input bytes and annual canonical bytes. A changed preference makes the installed result stale under normal input comparisons. Existing Strict archives keep the original wire shape, digests and legacy version tuples. No schema migration is needed.

Regular Tax Only is limited to controlled Basic deterministic projections. The shared analyzer admission rejects it before fixed or longevity Monte Carlo and integrated/weighted financial computation. Arkansas paired-source admission and financial analyzer association independently reject the new tuple. Archive readers preserve the previous v1/v2 Strict contracts and refuse evidence claiming the new policy in unsupported analyzer payloads. Existing history can be displayed without a financial rerun. [CompleteProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java) · [RetainedProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java) · [ArkansasAnalysisProvenance.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ArkansasAnalysisProvenance.java) · [ArkansasControlledWorkflowTest.java](src/test/java/com/daviddunn/retirementplanner/ui/controller/ArkansasControlledWorkflowTest.java)

## 11. Reporting and disclosure

Results Summary, the Arkansas projection PDF and its CSV identify the result's frozen policy. The PDF uses the existing detached report and the same View PDF and Save PDF export flow. Historical exports keep the original result policy even if the current plan has changed. A Regular Tax Only result displays the full disclosure, including when its tax is zero.

> Arkansas Regular-Tax Approximation: Low-income relief under §§26-51-301 and 26-51-501 is not modeled. Actual Arkansas income tax may be lower. This difference may affect withdrawals, Roth conversions and projected ending assets.

The disclosure follows the immutable result rather than the editing control, and does not require a modal warning. An additional synthetic extraction check prepared live and restored PDFs for the combined life-principal and Regular Tax Only tuple at zero state liability. Both contained the complete disclosure. [ResultsSummaryView.java](src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java) · [ArkansasValidationReport.java](src/main/java/com/daviddunn/retirementplanner/app/export/ArkansasValidationReport.java)

## 12. Accuracy and limitations

These examples use the **2025 published DFA return tables** for scale. Each assumes a timely Single return for a resident under 65 with one ordinary $29 personal credit, no other exemptions or adjustments, and all-source income equal to Arkansas AGI. The app's cents policy instead uses its reviewed 2026 regular schedule and its nominal future treatment.

| Published comparison | Regular before the provision | Relief calculation | Illustration |
|---|---:|---:|---|
| §301 table, $14,520 AGI | $12,050 regular net and $138 table tax, less $29 ordinary credit = $109 | Low-income table lists $0 at this AGI | $109 direct difference before the separately evaluated §501 credit. If that $60 credit also applies on the regular path, the corresponding net difference is $49. |
| §501 credit, $21,420 AGI | $18,950 regular net and $356 table tax, less $29 ordinary credit = $327 | $60 additional credit and $267 tax after the credit | $60 direct difference. This gross exceeds the 2025 Single low-income table range. |

These examples establish neither a universal error bound nor exact 2028 relief. Extra tax can induce a larger taxable withdrawal. For illustration, a $60 fixed difference paid by pretax withdrawals at a combined 15.4% marginal rate would consume about $70.92 of additional funds before rounding and other thresholds. With all other target inputs held fixed, that amount of extra taxable funding would reduce $10,000 of federal conversion room to $9,929.08. Changes in the taxable draw and executed conversion can also affect future growth, premiums and the account mix. Later financial metrics can change in either direction as a result. [DFA 2025 instructions, Single and Regular tax tables and additional tax credit worksheet](https://www.dfa.arkansas.gov/wp-content/uploads/2025_AR1000F_and_AR1000NR_Instructions.pdf)

## 13. Verification, cancellation and performance

Build and test results are recorded in the phase's ignored target directory. IntelliJ's bundled Maven ran Java 25, the workspace-local dependency cache, and isolated per-run profiles. The focused and normal bounded runs use `-o -B` and the repository's bounded selector excludes benchmark, capacity, performance, exhaustive and search-named suites.

```powershell
$m = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd'
$repo = "-Dmaven.repo.local=$((Get-Location).Path)\.codex-m2\repository"
& $m $repo "-Duser.home=$profile" -o -B "-Dtest=$focused" test
& $m $repo "-Duser.home=$profile" -o -B '-Dtest=*,!*Benchmark*,!*Capacity*,!*Performance*,!*Exhaustive*,!*Search*' '-Dsurefire.rerunFailingTestsCount=2' test
```

The focused selector included `ArkansasRegularTaxOnlyTest`, `StateTaxAssumptionsTest`, `ArkansasDomainCalculationTest`, `ArkansasLifePrincipalProjectionTest`, `ArkansasLifePrincipalReconciliationTest`, `ArkansasLowIncomeDiagnosticsTest`, `ArkansasLowIncomeExactFundingDiagnosticsTest`, `ArkansasAccountFundingTest`, `ArkansasCappedExclusionProofTest`, `ArkansasFundingCounterexampleTest`, `ArkansasSourceReachabilityTest`, `ArkansasTargetCertificationTest`, `ArkansasProjectionIntegrationTest`, `ArkansasControlledWorkflowTest`, `ArkansasFundingProvenanceTest`, `ArkansasLongevityValidationTest`, `ArkansasProjectionFailureUiTest`, `ProjectionSnapshotPersistenceTest`, `AssumptionsViewTest`, `InputTooltipTest`, `StateTaxWizardIntegrationTest` and `LifeInsuranceHistoryTest`.

| Verification | Total | Passed | Failed | Errors | Skipped | Exit |
|---|---:|---:|---:|---:|---:|---:|
| Initial compatibility | 89 | 89 | 0 | 0 | 0 | 0 |
| First new-case test run | 78 | 67 | 2 | 9 | 0 | 1 |
| Second new-case test run | 81 | 79 | 2 | 0 | 0 | 1 |
| Focused before UI audit | 342 | 342 | 0 | 0 | 0 | 0 |
| Initial bounded | 5,597 | 5,561 | 0 | 1 | 35 | 1 |
| Focused after tooltip fix, before archive fixture rebinding | 353 | 352 | 1 | 0 | 0 | 1 |
| Final focused | 353 | 353 | 0 | 0 | 0 | 0 |
| Final bounded | 5,597 | 5,562 | 0 | 0 | 35 | 0 |

The final focused run took 1:22. It included the new financial and survivor tests, the compact assumptions control and tooltip, Save and role changes, PDF extraction, archived Arkansas evidence, and existing proof and cancellation coverage. Development runs exposed fixture assumptions about one-time expenses, archive instance identity, Roth cash availability, the timing of the displayed premium MAGI, and the existing digest check that precedes policy verification. Those fixtures were corrected against existing contracts. The first bounded run found the missing tooltip, which was connected to the existing input help system. It also recorded a worker-liveness flake in the unchanged `Stage5DComparisonTest` that passed on retry. The final bounded run took 8:51 and recorded no flakes. Both Git diff checks passed. The index remained empty and nothing was committed or pushed.

The first candidate at the synthetic regular-only Roth capacity optimum was certified with one outer exact trial, 11 inner exact candidates and 28 inner interval tax bounds. Existing fixed-segment and inherited reachability tests ran with the unchanged budgets. Cancellation inside Roth work publishes no year, and the existing controlled worker retains the previously accepted result after refusal. Prior P3D3C2 diagnostics remain opt-in for Strict and never supply a substitute estimate.

## 14. Native Windows review and outstanding scope

1. Open `native-synthetic.json` in the ignored phase directory under `target`. This is a generated synthetic Arkansas couple fixture verified through the plan repository. It starts in Basic with Strict, an employment pension of $5,000/month and $2,500/month payable to the survivor, both people younger than 65 in 2028, an opening date of January 1, 2028, and zero inflation and growth. Confirm both alive completes. Save this result as Baseline for the later role check.
2. Set the employee spouse's modeled death to 2028 and recalculate in Strict. With guaranteed income left at the $30,000 annual survivor pension and deductible-source facts supported, observe the low-income refusal and retained prior result. Select Regular Tax Only, apply and recalculate. The first year remains MFJ, and the following survivor year uses Single if the horizon includes it.
3. Add a qualifying $100,000 P3D3C lump-sum life policy payable to the survivor and a one-time $70,000 expense in 2028. Confirm one death-year receipt, the survivor pension, no taxable income from principal and the effect on household cash. Verify $59,697.96 goes to retained cash in the death year. Review Results Summary and View PDF, then Save PDF and check the same disclosure.
4. Save, Save As and Open. Compare Strict and Regular Tax Only roles after swap, and change one role's policy to confirm stale status. Verify Advanced and financial analyzers still refuse the new policy. The automated run covers supported synthetic behavior and archived evidence. Interactive review is a separate user gate.

Full §301 and §501 tax-return modeling, non-admitted source categories, other filing statuses and full-year residency exceptions, Advanced NIIT for this mode, and alternate-policy analyzer execution remain outside this phase. **Readiness is automated verification passed and ready for native Windows acceptance as a Basic deterministic planning approximation.**

## 15. Files changed in this phase

| Area | Files |
|---|---|
| Policy and admission | `ArkansasLowIncomePolicy.java`, `StateTaxAssumptions.java`, `ConditionalBenefitAdmission.java` |
| Annual execution and evidence | `ProjectionEngine.java`, `ArkansasProjectionValidation.java`, `ArkansasValidationTax.java` |
| Regular tax and proof premises | `ArkansasProductionTaxCalculator.java`, `ArkansasJointWorksheet.java`, `ArkansasAnnualFunding.java`, `ArkansasTargetBounds.java` |
| History and analysis guards | `CompleteProjectionSnapshot.java`, `RetainedProjectionSnapshot.java`, `ArkansasAnalysisProvenance.java`, `ConditionalBenefitAnalyzerValidation.java`, `MonteCarloSnapshot.java` |
| Controller, assumptions, reporting | `ApplicationController.java`, `AssumptionsView.java`, `ResultsSummaryView.java`, `ArkansasValidationReport.java` |
| Tests | `ArkansasRegularTaxOnlyTest.java`, `StateTaxAssumptionsTest.java`, `ArkansasControlledWorkflowTest.java`, `AssumptionsViewTest.java` |
| Review document | This report. The synthetic native fixture, probes and logs are ignored build artifacts. |
