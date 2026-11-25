# Phase 6E-P3D3C4A — Arkansas Social Security claiming-age differential audit

## Executive summary

A synthetic Arkansas Basic couple reproduced the reported pattern across all five survivor elections. Ages 61 and 62 refused in 2056. Ages 63, 64 and 67 completed. The age-62 and age-63 runs first diverged in 2028, when the age-62 survivor Social Security benefit began. By 2056, later claiming left more cash in this synthetic plan. Age 62 reached the deceased primary's traditional IRA for ordinary spending. The new diagnostic recorded that exact check as `ORDINARY_RETIREMENT_WITHDRAWAL / CASH_FLOW`, owner `PRIMARY`, active set `SPOUSE`. The valid life payment to the spouse had already passed the same check in 2027.

**Native receipt and stage remain unknown.** A matching private input was unavailable for replay. The user's observed `CONDITIONAL_LUMP_SUM` invocation was valid. Its membership test returned true, so that invocation could not throw the reported error. The matching synthetic age pattern supports an allocation hypothesis, without establishing the source used by the private plan.

## Git baseline and materials reviewed

The audit opened on `main` at `2bedb55e99fb48aa31225e9c5549c842d77ebb2d` with zero ahead/behind against the local `origin/main` reference and an empty index. There were 32 tracked modifications and 14 untracked entries before this phase. Existing work was retained. This phase adds diagnostic-only code, focused tests and this report. No private plan was used, staged, committed or transmitted.

Reviewed the qualified principal model, earlier eligibility analysis, low-income instrumentation, regular-tax policy, recipient audit and accepted proofs. The actual production code is the authority for the branch and execution conclusions here. [P3D3C](PHASE-6E-P3D3C-ARKANSAS-LIFE-PRINCIPAL-IMPLEMENTATION.md) · [P3D3C1](PHASE-6E-P3D3C1-ARKANSAS-DEATH-LOW-INCOME-AUDIT.md) · [P3D3C2](PHASE-6E-P3D3C2-ARKANSAS-NATIVE-LOW-INCOME-DIAGNOSTICS.md) · [P3D3C3](PHASE-6E-P3D3C3-ARKANSAS-REGULAR-TAX-ONLY.md) · [P3D3C4](PHASE-6E-P3D3C4-ARKANSAS-DEATH-RECEIPT-AUDIT.md) · [P3D2E](PHASE-6E-P3D2E-ARKANSAS-CAPPED-EXCLUSION-PROOF.md) · [P3D2G](PHASE-6E-P3D2G-ARKANSAS-SOURCE-REACHABILITY-PROOF.md)

## Claiming semantics

The persisted own election belongs to `SocialSecurityIncome`, which refuses ages below 62 or above 70. The survivor election belongs to `DeathScenarioAssumptions`. The shared survivor UI offers eligible whole ages and can offer 61 without a migration. Under the modeled January 1 death date, the applicable survivor date is the selected birthday or the death date if that date is later, with immediate eligibility in the modeled late-death case at or after survivor full retirement age. The synthetic spouse qualifies for the age-61 survivor setting. Its own election stays at 67 across the experiment. Which exact UI control was used in the native run has not been independently observed, although age 61 is consistent with the supported survivor control. [SocialSecurityIncome.java](src/main/java/com/daviddunn/retirementplanner/domain/income/SocialSecurityIncome.java) · [DeathScenarioAssumptions.java](src/main/java/com/daviddunn/retirementplanner/domain/model/DeathScenarioAssumptions.java) · [SurvivorBenefitClaimingControls.java](src/main/java/com/daviddunn/retirementplanner/ui/views/SurvivorBenefitClaimingControls.java) · [SurvivorBenefitClaimingPolicy.java](src/main/java/com/daviddunn/retirementplanner/domain/income/SurvivorBenefitClaimingPolicy.java) · [SSA survivor full retirement age](https://www.ssa.gov/survivor/full-retirement-age-survivor) · [SSA retirement eligibility](https://www.ssa.gov/retirement/plan-for-retirement)

The paired fixture exercises the modern two-person monthly provider with one own record per person. It translates both independent own dates and the spouse's applicable survivor date into one strategy request. Annual results retain own and selected benefits. The higher eligible payment governs the modeled household benefit in each month, with own and survivor excess coordinated. The synthetic spouse begins its own record in 2033 regardless of survivor age. The higher modeled survivor payment still determines the total. No household status transition follows from a Social Security election. January 1 death and the configured MFJ first-death filing followed by Single apply identically at each age. Legacy household shapes have a separate provider fallback. This fixture establishes behavior on the modern path. [SocialSecurityProjectionIncomeProvider.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/SocialSecurityProjectionIncomeProvider.java) · [SocialSecurityStrategyCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityStrategyCalculator.java) · [SocialSecuritySurvivorBenefitCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecuritySurvivorBenefitCalculator.java) · [SSA benefit coordination](https://www.ssa.gov/planners/survivors/1962s.html)

## Paired synthetic reproduction and oracles

The same modeled household starts January 1, 2027 and runs through 2056. The primary dies January 1, 2027. Both synthetic birth dates are June 15, 1966. The own elections are 67, on modeled monthly full-retirement amounts of $3,000 primary and $700 spouse. One qualifying $8,000 principal payment goes to the spouse in 2027. The primary's traditional IRA opens at $100,000 and the spouse's traditional IRA opens at zero. Growth, inflation and Social Security COLA are zero. There is no pension, no non-life conditional receipt, no Roth election and no RMD distribution. The only ordinary expense is a synthetic $770,000 obligation in 2056, intentionally chosen between independently calculated cash outcomes. Annual Medicare adds $2,434.80 beginning in 2031. Each treatment changes only the whole-year survivor election.

| Spouse survivor age | First survivor benefit year | Full-year 2056 selected Social Security | Cash through 2056, net of Medicare and before ordinary expense | 2056 outcome |
|---:|---:|---:|---:|---|
| 61 | 2027 | $27,205.68 | $749,529.90 | `SOURCE_OR_TAX`. Initial ordinary spending uses inactive primary IRA. |
| 62 | 2028 | $28,671.48 | $764,221.67 | `SOURCE_OR_TAX`. Same exact initial spending branch. |
| 63 | 2029 | $30,137.16 | $775,978.53 | Completed. $5,978.53 retained cash. |
| 64 | 2030 | $31,602.84 | $784,804.03 | Completed. $14,804.03 retained cash. |
| 67 | 2033 | $36,000.00 | $793,695.20 | Completed. $23,695.20 retained cash. |

For this synthetic worker the unreduced monthly survivor basis is $3,000. For the selected birthdays the hand oracle applies the model's linear reduction of 28.5% over 84 months to the months remaining to FRA and rounds the modeled monthly amount to cents. Age 62 gives $2,389.29 and age 63 gives $2,511.43. The first year pays seven months for each, then full calendar years. The cash oracle sums those payments and $8,000 principal, subtracts 26 modeled annual Medicare premiums and then applies the single ordinary expense. The amounts describe the Aledrix planning fixture. SSA's benefit processing can have additional rules and different rounding.

| Time | Age 62 | Age 63 | Attribution consequence |
|---|---:|---:|---|
| 2027 first death | $8,000 cash from the life benefit, no Social Security | Same | MFJ. Receipt is a one-time excluded principal payment with `SPOUSE` active. Both pass reconciliation. |
| 2028 first divergence | Starts survivor benefit, $16,725.03. Closing retained cash $24,725.03. | Survivor benefit waits. Closing cash $8,000. | Single in both. Expected Social Security entitlement and cash difference. |
| 2029 | $28,671.48 Social Security, $53,396.51 retained | $17,580.01 Social Security, $25,580.01 retained | Age 63 starts. No new conditional receipt. |
| 2030 onward | $28,671.48 annual selected benefit | $30,137.16 annual selected benefit | Age 63 gains $1,465.68 each full year. On this fixture cumulative cash overtakes in 2048. |
| 2055 ending retained | $737,984.99 | $748,276.17 | Both complete to this point. Same owner, filing, pension, RMD and life evidence outcomes. |
| 2056 initial pre-premium cash for the bill | $766,656.47. Needs $3,343.53 from the next spending source. | $778,413.33. Covers the bill. | Age 62 creates an ordinary withdrawal attributed to the inactive primary. Zero additional **tax-funding** dollars in that failed check. |
| 2056 Medicare-aware pass | The $770,000 case stops during initial pricing. | Funded. Zero federal AGI, federal/AR tax, RMD, Roth conversion and additional tax withdrawal. $5,978.53 retained. | Medicare causes repricing from the same original annual state. |

The matched prefix checked unchanged own Social Security, tax policy, qualified life receipt, no other conditional income, no pension or survivor pension payments, zero AGI, no retirement distributions and no change in the recipient timeline. The age-62 refusal occurs before a final tax result or settlement. The matched age-63 final result shows both ordinary expenditure and Medicare funded. A separate sensitivity fixture uses $765,000 for both ages. Age 62 clears initial spending by $1,656.47, misses by $778.33 once Medicare is included, and refuses at `MEDICARE_AWARE`. Age 63 completes with $10,978.53 retained. This pass-specific result was verified in both Strict and Regular Tax Only. It does not classify the earlier private 2028 low-income refusal. [ArkansasRecipientClaimingAgeTest.java](src/test/java/com/daviddunn/retirementplanner/domain/projection/ArkansasRecipientClaimingAgeTest.java) · [ProjectionEngine.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java)

## Exact refusal and alternatives

`ArkansasIncomeReconciliation.activeRecipient` resolves the receipt owner and checks membership in the actual annual active-owner set. The reported message corresponds to a present owner outside that set. The refusal is `UNSUPPORTED_MODEL_RULE / ATTRIBUTION / SOURCE_OR_TAX`. Absent owner has a separate message. Both the exact calculator and optional interval and target proofs invoke reconciliation. A bound-stage rejection may simply remove an optional proof bound. The stage field identifies the producing call. It does not imply that a particular optional bound failure escaped the engine.

| Source family | Evidence and status |
|---|---|
| Qualified conditional lump sum to the surviving spouse | **EXCLUDED_BY_EVIDENCE** for the debugger invocation whose check was true. **CONFIRMED_SYNTHETIC** valid in both age-62 and age-63 runs, same amount, year, trigger, living recipient and exact frozen evidence. Life receipt generation depends on its own definition and effective death, rather than on Social Security election. Mismatching qualified evidence takes its own rejection path. |
| Unsupported generic conditional receipt with an inactive recipient | **CONFIRMED_SYNTHETIC** at isolated calculator input, which reaches the check and reports `CONDITIONAL_LUMP_SUM / INCOME / FIXED_DATE`. Arkansas Basic plan admission precludes this generic category in normal controlled production. |
| Discretionary traditional IRA spending or tax funding | **CONFIRMED_SYNTHETIC** through controlled production. Age-pair witness used spending with exact additional-funding candidate `ZERO`. A separate same-year fixed conversion from the active spouse exposed the inactive primary IRA during positive `TAX_FUNDING`. Removing that synthetic unavailable balance allowed $5,884.24 of funding through the active spouse with the same $50,000 actual conversion. |
| Inherited retirement, ordinary RMD, opening RMD, conversion, Roth cash | **CONFIRMED_SYNTHETIC** as isolated calculator branches with deliberately invalid inactive-owner evidence. **POSSIBLE** discretionary inherited or Roth cash exposure in production because the allocator can reach individual account balances. Arkansas's normal RMD and conversion selection already use the living-owner scope. A persisted inherited account can have a living modeled beneficiary as owner, independently of its original owner. |
| Pension or direct survivor pension | **CONFIRMED_SYNTHETIC** that an injected inactive pension receipt fails. Normal annual selection stops an individual owner's pension at their modeled death and directs a configured survivor payment to the living spouse. A positive `JOINT` pension can independently fail the check. P3D3C4 exercised it through controlled production while both people lived. **POSSIBLE** as a native alternative pending evidence. |
| Native 2056 exception | **CONFIRMED_NATIVE** as reported for Basic, Regular Tax Only and the relevant ages. Actual receipt source, owner class, pass and trial are **UNKNOWN**. A second modeled death is outside the ordinary deterministic path. Given the reported first death in 2027, 2056 would be a later survivor year in that path. |

The ordinary allocator uses retained household cash first, then ordered portfolio balances. An account debit uses that account's own attribution. Arkansas recomputes the actual active-owner set and then checks memberships. The validated failure protects annual tax attribution. Its upstream synthetic cause is cash allocation from an inactive owner without an authorized successor receipt. Regular Tax Only skips its low-income eligibility proofs and still reaches this independent guard. [ArkansasIncomeReconciliation.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasIncomeReconciliation.java) · [ArkansasProjectionValidation.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ArkansasProjectionValidation.java) · [ArkansasAnnualFunding.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasAnnualFunding.java) · [ArkansasProductionTaxCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasProductionTaxCalculator.java) · [ProjectedWithdrawalAllocator.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectedWithdrawalAllocator.java) · [ConditionalBenefitDefinition.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitDefinition.java) · [ConditionalBenefitEvaluator.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitEvaluator.java) · [ConditionalBenefitAdmission.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitAdmission.java) · [IncomeSourceAttribution.java](src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java) · [EffectiveHouseholdDeathView.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/EffectiveHouseholdDeathView.java) · [HouseholdPensionIncomeCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/income/HouseholdPensionIncomeCalculator.java)

## Redacted diagnostic

Set the application JVM property `-Daledrix.diagnostics.arkansasRecipient=true`. The controlled Arkansas worker opens a per-run capture. The annual call supplies death-year, mode, policy, Medicare pass and whether annual execution had a Roth target. The exact funding evaluator supplies zero or positive additional funding. The tax calculator identifies exact pricing or a specific proof boundary. Only the failed membership branch constructs and sends the record. The scope closes on return, failure or cancellation and never enters a frozen plan or annual ledger. The text contains only allowlisted classifications and the calendar year.

The sink publishes at most **four distinct records per run**, deduplicated by the full redacted record. Each is atomically published to a fresh `target/phase6e-p3d3c4a/arkansas-recipient-*.txt`. The cap keeps the first four distinct checks if the run encounters more. No diagnostics are recorded once cancellation is observed. An already-published record can describe a failed check in a run later invalidated by the UI. Direct calculator calls without annual or funding context give `UNKNOWN` for unavailable dimensions. The stage and candidate fields do not assert final settlement. `TARGETED` identifies the annual control path, which can delegate to an existing fixed fallback. The opt-in process uses the repository-root working directory. If atomic publication fails, a static warning omits all private data and the projection still sees the original exception. [ArkansasRecipientDiagnostic.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasRecipientDiagnostic.java) · [ArkansasRecipientDiagnosticFile.java](src/main/java/com/daviddunn/retirementplanner/ui/controller/ArkansasRecipientDiagnosticFile.java) · [ApplicationController.java](src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java)

Example for the **synthetic** age-62, $770,000 case:

```text
phase=P3D3C4A
year=2056
filing=SINGLE
activeOwners=SPOUSE
receiptOwner=PRIMARY
receiptKind=ORDINARY_RETIREMENT_WITHDRAWAL
receiptPurpose=CASH_FLOW
accountType=TRADITIONAL_IRA
conditionalTrigger=NOT_APPLICABLE
deathYear=SURVIVOR
mode=BASIC
policy=REGULAR_TAX_ONLY_LOW_INCOME_RELIEF_NOT_MODELED
pass=INITIAL
rothPath=FIXED_OR_NONE
stage=EXACT_PRICING
candidate=ZERO
reason=UNSUPPORTED_MODEL_RULE
category=ATTRIBUTION
issue=SOURCE_OR_TAX
```

## Funding and Roth proof implications

An ownership correction would change the executable cash source universe. Spending, the exact additional withdrawal allocator, terminal repricing, segment boundaries and maximum drawable capacity would need one eligible view with stable ordering within it. The Roth proof must use the same after-spending balances, aggregate and per-recipient capacity, capped recipient exclusion pools, and inherited prefix. Removing a source from the actual route while leaving it in a state floor or the federal reachability envelope can change minimum or maximal feasible results. Preserve the strict inequality used to prove an inherited prefix unreachable, with equality retaining the existing conservative path, and verify cents and fractional-capacity terminals. The check should independently price the minimum funded cent and prove all larger feasible conversion amounts excluded or exact-checked. Both Medicare passes must reconstruct eligible cash from their respective spending allocations. No proof algorithms or budgets changed here. A future changed executable set warrants review of a scoped new identity with reader support rather than reuse by assumption. [ArkansasMinimumFundingSolver.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasMinimumFundingSolver.java) · [ArkansasRothFunding.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasRothFunding.java) · [ArkansasTargetBounds.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasTargetBounds.java)

Strict currently uses `AR-FUNDING-P3D2E-1` and `AR-TARGET-P3D2G-1`. The Regular Tax Only tuple uses `AR-FUNDING-P3D3C3-1` and `AR-TARGET-P3D3C3-1`. The life-principal context remains separate. [ArkansasValidationTax.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ArkansasValidationTax.java)

## Permanent regression design

| Fixture | Required outcome and independent reason |
|---|---|
| Same plan through 2056, age 62 or 63, 2027 death, valid qualified lump sum | Current characterization remains typed refusal at the age-62 inactive spending debit and exact success at age 63. Check the analytical cash totals, the first differing entitlement in 2028, and one valid living recipient in 2027. A later approved correction must assert the new explicit outcome at the same cash constraint. |
| Same pair, enough verified living cash or active retirement capacity, with inactive IRA ordered first | Approve an expected success only with a proved living source meeting the exact obligations. Assert cent-level funding, owner-specific AGI and Arkansas relief, and unchanged inactive balance. If only inaccessible capacity remains, require a typed unsupported-source outcome until succession policy is approved. |
| Inactive or missing benefit recipient, unsupported taxable lump sum, and joint pension | Require typed source or plan-admission refusal appropriate to the exact condition. A valid single survivor pension should continue with documented living payee. Preserve life evidence mismatch classification and test the no-payout case separately. |
| Fixed/targeted conversion, positive and zero tax funding and Medicare-aware spending | Independently check expenditures, tax and source caps. Exercise both policies and both annual passes. Include the inherited account inside, outside and at equality of the eligible prefix and an unsupported source beyond it. Refuse on unavailable proof. |
| Persistence, controls and analyzers | Verify age-61 survivor selection and own-age rejection, analyzer refusals, last-success preservation, historical bytes, UUIDs, role swaps and frozen export. A future result that changes provenance must become stale under existing freshness rules. |

## Historical compatibility

The diagnostic remains outside frozen inputs, state annual evidence, fingerprints, result IDs, Current/Baseline comparisons and PDF/CSV. Save, Save As, Open and Swap continue using existing scope and provenance. The diagnostic is neither part of a history reader nor a requirement to render an old report. If a later fix changes payment eligibility or account availability, store the applicable context in a scoped, versioned identity for new results. Preserve prior financial records and their original fingerprints and avoid recomputation when opening history. Run the existing restore and export checks along with the new ownership controls. Existing Arkansas conditional-benefit and alternate-policy analyzer gates should remain separate. [CompleteProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java) · [RetainedProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java) · [ProjectionPdfExporter.java](src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java) · [ProjectionCsvExporter.java](src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionCsvExporter.java)

## Native Windows capture

1. Build and run this working copy from the repository root with JDK 25 using the normal IntelliJ Maven JavaFX Run or Debug configuration (`javafx:run`), or an IntelliJ Application configuration launching `com.daviddunn.retirementplanner.ui.MainApplication` with the existing JavaFX runtime setup. For an **Application** configuration, add `-Daledrix.diagnostics.arkansasRecipient=true` to its **VM options**. For the **Maven** configuration, set `JDK_JAVA_OPTIONS=-Daledrix.diagnostics.arkansasRecipient=true` in that configuration's **Environment variables** so the child `java.exe` sees it. Preserve any existing options in that variable. A Maven-only VM option, `MAVEN_OPTS`, or a Maven project `-D` property alone does not reliably set the forked JavaFX application's JVM property. Breakpoints are unnecessary.
2. Use a local read-only copy of the matching plan under ignored `target/phase6e-p3d3c4a/`. Keep the original closed. Set `$captureStarted = Get-Date` in a repository-root PowerShell session before recalculating. In Basic mode with Regular Tax Only, use the survivor election control in the death scenario, keep primary death 2027, select survivor age 62, and recalculate through 2056. Do not save edits to the original. Verify the reported warning appears in this run.
3. From the repository root, view only the new redacted file(s), using the newest first. If more than one appears for the run, compare their stage fields and keep the 2056 exact-price record together with any bound-stage record. A swallowed bound failure alone does not establish the final refusal.

```powershell
Get-ChildItem target/phase6e-p3d3c4a -Filter 'arkansas-recipient-*.txt' -File |
    Where-Object { $_.LastWriteTime -ge $captureStarted } |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 4 |
    ForEach-Object { Get-Content -LiteralPath $_.FullName }
```

4. If useful, repeat with age 63 and the same other inputs. A completed run whose membership checks all pass produces no recipient record. If the age-62 warning reappears with no fresh file, verify the JavaFX application picked up the property, its working directory points to this repository, and it ran the compiled audit build. Use the current warning year and stage, since earlier files from other attempts remain. Disable the opt-in when finished. Share only the redacted record if requesting follow-up.

## Focused tests and workspace verification

Tests use IntelliJ's bundled Maven with the configured repository-local `.codex-m2/repository`, offline with `-o -B test` and explicit `-Dtest` selections. No exhaustive longevity, Monte Carlo, benchmark or capacity suites ran.

| Run | Passed | Failures | Errors | Skips | Exit |
|---|---:|---:|---:|---:|---:|
| Three new suites, before adding the separate positive tax-funding case | 23 | 0 | 0 | 0 | 0 |
| Final focused regression, 23 suites, including all 24 new cases | 281 | 0 | 0 | 0 | 0 |
| Standalone Windows controlled-worker synthetic replay | 1 | 0 | 0 | 0 | 0 |

The focused selection covered Arkansas calculation, low-income and life-principal reconciliation, the paired recipient tests, conditional benefits, Social Security provider and survivor calculation, death and lifetime characterization, withdrawal allocator, account funding, capped exclusion, target and source reachability, archive provenance and snapshots, and controlled warning and publication. Ignored Maven logs are in `target/phase6e-p3d3c4a/`. [ArkansasRecipientDiagnosticTest.java](src/test/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasRecipientDiagnosticTest.java) · [ArkansasRecipientDiagnosticFileTest.java](src/test/java/com/daviddunn/retirementplanner/ui/controller/ArkansasRecipientDiagnosticFileTest.java)

The standalone replay saved and reopened a **synthetic** age-62 plan using the JSON repository. The actual application controller accepted age 63, then applied the age-62 assumptions and refused at 2056. The worker wrote exactly one redacted record as shown above, and the accepted age-63 snapshot stayed installed. The replay used a separate ignored working directory, so its output does not appear in the native capture's top-level record listing.

A pre-verification compile attempt exited 1 on an incorrect package import in the new diagnostic class. It was corrected before the successful test commands. The launcher check confirmed that Java 25 received the property via `JDK_JAVA_OPTIONS`. The matching private end-to-end JavaFX run remains a native acceptance step.

`git diff --check` and `git diff --cached --check` passed. New files passed a separate whitespace check. File scope was reviewed against opening status. Source changes are confined to the worker activation, annual/pass context, funding candidate classification, calculator stage classification, exact recipient branch and the new redacted sink. The report and three focused test classes are the other additions. Existing test and financial formulas, serializers, historical identities, account and benefit semantics, and proof budgets were left untouched.

## Recommendation and approval decisions

1. Use the opt-in capture to confirm the native 2056 source before selecting a financial correction. An exact `CASH_FLOW` record would distinguish the demonstrated spending path from a `TAX_FUNDING`, `PENSION` or conditional receipt.
2. If the native receipt is a discretionary debit from a deceased owner's account, approve an Arkansas-scoped eligible-source rule with compatible spending, exact funding and proof capacity. Keep the account's reported balance and original modeled ownership. If required cash depends on unmodeled succession, return a typed availability refusal until a separate succession rule is approved.
3. If the native receipt is joint or otherwise ambiguous pension income, the smaller response is validated payee input and targeted review feedback. If it is conditional, confirm the exact trigger, receipt recipient and frozen evidence before proposing any change to benefit semantics. If the result is missing or ambiguous, gather more structural evidence.
4. Approve any revised proof and historical identity separately before financial implementation. This audit stops at diagnostics and root-cause evidence.
