# Phase 6E-P3D3C4D-R2a2-A2 — Florida spouse-as-owner IRA RMD execution

## 1. Executive summary

**IMPLEMENTED_AND_TESTED.** Florida Basic deterministic projections can apply the modeled spouse-as-owner election to qualifying personal traditional IRAs. The survivor's IRA group begins holding the qualifying positions on January 1 after the first modeled death. The same membership sizes and executes the RMD. The distribution is attributed to the survivor and debits the original account. The independent annual amounts were $15,000.00, $14,878.05 and $15,242.02.

**EXISTING_BEHAVIOR_PRESERVED.** Legacy financial transcripts and the existing Michigan and Arkansas tests retain their results. Other calculation routes continue to treat the saved inheritance preference as pending.

## 2. Baseline and Git safety

The opening worktree was on `main` at `2bedb55e99fb48aa31225e9c5549c842d77ebb2d`. It contained 41 tracked modifications, 39 untracked files and an empty index. The earlier Arkansas and survivor phases were reviewed in place. The final hash comparison found nine changed opening files, all listed in the exact changed-file table. Every other opening file matched. The new tests use synthetic data, and command logs and comparison transcripts are under ignored `target/phase6e-p3d3c4d-r2a2a2/`. No private plan was opened. [D0R audit](PHASE-6E-P3D3C4D0R-RMD-ROTH-MINIMAL-CHANGE-AUDIT.md) · [R1 foundation](PHASE-6E-P3D3C4D-R1-SURVIVOR-OWNERSHIP-FOUNDATION.md) · [R2a gate](PHASE-6E-P3D3C4D-R2A-SURVIVOR-RMD-IMPLEMENTATION.md) · [R2a1 foundation](PHASE-6E-P3D3C4D-R2A1-UNIFIED-SPOUSAL-INHERITANCE.md) · [R2a2-A gate](PHASE-6E-P3D3C4D-R2A2A-SPOUSE-OWNER-IRA-RMD.md) · [Attribution reuse](PHASE-6E-P3D3C4D-R2A2A1-ATTRIBUTION-REUSE.md)

## 3. Exact changed files

| Area | Files and method sites |
|---|---|
| Admission and annual binding | New [SpousalIraRmdPolicy.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/SpousalIraRmdPolicy.java) and [SpousalIraRmdMembership.java](src/main/java/com/daviddunn/retirementplanner/domain/rmd/SpousalIraRmdMembership.java). New typed [SpousalIraRmdUnavailable.java](src/main/java/com/daviddunn/retirementplanner/domain/rmd/SpousalIraRmdUnavailable.java). |
| RMD and allocation | [HouseholdRmdCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/rmd/HouseholdRmdCalculator.java) and [OwnerRmdCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/rmd/OwnerRmdCalculator.java) `calculate`, [OwnerRmdService.java](src/main/java/com/daviddunn/retirementplanner/domain/rmd/OwnerRmdService.java) `calculateIraRmd`, [ProjectedWithdrawalAllocator.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectedWithdrawalAllocator.java) RMD apply, allocation and breakdown methods. |
| Projection and Roth | [ProjectionEngine.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java) deterministic dispatch, annual loop, single-year calculation and RMD delegation. [RothConversionBracketFillCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/roth/RothConversionBracketFillCalculator.java) executable trial overload. |
| Frozen history | [CompleteProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java) constructor, execution-plan and projection restore, applicability and chart. [SpousalInheritancePlanningEvidence.java](src/main/java/com/daviddunn/retirementplanner/app/projection/SpousalInheritancePlanningEvidence.java) binding and disclosure. [RetainedProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java) scoped capture, schema and restore. [ProjectionSnapshotComponents.java](src/main/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotComponents.java) component verification, read and write validation. |
| Application and presentation | [ApplicationController.java](src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java) acceptance, restore, Swap and analysis gates. [MainWindow.java](src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java) refusal and prior-result display. [AssumptionsView.java](src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java) notice. [ResultsSummaryView.java](src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java) frozen PDF export. [MonteCarloAnalysisView.java](src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java) reference selection and [MonteCarloStrategyComparisonView.java](src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonView.java) paired admission. |
| Tests | New [SpousalIraRmdExecutionTest.java](src/test/java/com/daviddunn/retirementplanner/domain/projection/SpousalIraRmdExecutionTest.java). Extended [ProjectionSnapshotPersistenceTest.java](src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotPersistenceTest.java) and [ProjectionPdfExportTest.java](src/test/java/com/daviddunn/retirementplanner/ui/charts/ProjectionPdfExportTest.java). |

## 4. ProjectionEngine method footprint

`projectSpousalIraRmd` is the explicit top-level path on frozen inputs. `requestsSpousalIraRmd` scopes dispatch. `executeInternal` supplies the existing routes with an absent policy. `runProjection` resolves one membership before the shared calculation. `calculateProjectionYear` sends it to the RMD calculator and allocator and carries it through the existing Medicare recursion. `calculateRequiredMinimumDistribution` forwards the optional membership. `spousalIraRmdOwnership` derives structural attribution for certified history, without executing a projection. Growth, RMD, spending, conversion, funding, Medicare and settlement use the existing annual execution sequence.

## 5. Approved admission criteria

**IMPLEMENTED_AND_TESTED.** Florida, Basic, ordinary deterministic entry, a two-person household, selected Owner treatment, an original decedent personal traditional IRA and a reachable next-year election. The first death must be on or after the modeled opening date, within the modeled horizon, and strictly before the decedent's first IRA distribution year. The applied cohort is 1960 or later and uses supported Uniform Lifetime factors. The planning facts cover direct sole-spouse beneficiary status, unlimited withdrawal rights, an ordinary fully pretax IRA with zero relevant basis and compatible account history, and the default life-expectancy beneficiary path before the modeled election. These are hypothetical planning facts under the approved convention. The frozen plan directly provides original account class and identity, the roster, birth and death dates and any supplied opening-RMD records. Florida has no personal income tax. [Florida Department of Revenue](https://floridarevenue.com/faq/Pages/FAQDetails.aspx?FAQID=1466)

## 6. Mandatory clearance derivation

Compare the January 1 modeled death year to the original owner's first distribution calendar year from the existing rules. A strictly earlier death is before the underlying owner obligation and its later April payment deadline. Confirm that any decedent opening-RMD record belongs to the opening calendar year and shows no prior paid amount. Beneficiary commencement under the modeled default sole-spouse life-expectancy path can wait until year-end when the decedent would reach the applicable age. The next-January own-IRA election occurs by the start of that calendar year. The catch-up exception for the ten-year path is outside these facts. In the reverse fixture, the election occurs at the start of the year in which the decedent would reach 75, and its owner amount is calculated under the survivor's owner schedule. Death in the first distribution year, contradictory distribution evidence, and unresolved beneficiary obligations refuse. [26 U.S.C. §401(a)(9)](https://uscode.house.gov/us/usc/119-124/t26/s401/) · [26 CFR §1.408-8(c)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-408-8.html) · [26 CFR §1.401(a)(9)-3(c)–(d)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-3.html) · [26 CFR §1.402(c)-2(j)(4)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-402c-2.html)

## 7. R1, R2a1 and R2a2-A1 reuse

The existing frozen plan choice supplies the convention. The adapter gives R1's `resolveForPlanning` the separately derived clearance set. The existing `AnnualRetirementOwnership` and `RetirementOwnershipDisposition` control effective membership. The A1 `forSuccessorRmd` factory, optional retained marker and contextual row reader carry the authorized payee. Source, amounts, date and recipient remain part of the original RMD ledger row.

## 8. Annual account membership

The annual view binds the R1 references to prior December 31 entries and to the opening projected portfolio's original position identities. It rejects duplicated entries, absent sources and the wrong snapshot date. Explicit zero balances remain valid. The allocator visits these original positions in persisted IRA order. Both living spouses follow their pre-existing separate groups. On the modeled death year the survivor keeps their independent owner obligation. Beginning the following January, a qualifying successor traditional IRA and the survivor's own traditional IRAs enter one effective owner group.

## 9. Correct RMD sizing

The existing `OwnerRmdService` uses the verified group balance, survivor birth date and the applicable divisor through `RmdService`. The existing cent-rounding method runs once. Individual living-owner 401(k)/403(b) calculations retain their separate existing paths. The current government resource is 2026.1. The applicable Uniform Lifetime factors used here are 24.6, 23.7 and 22.9 at ages 75, 76 and 77. [IRS Publication 590-B, Appendix B, Table III](https://www.irs.gov/publications/p590b)

## 10. Actual original-account debit

Sizing and the allocator share the same membership. The allocator subtracts from actual projected positions in the established order. The difference between balances before and after the allocator supplies the source debit evidence. An unsatisfied verified amount uses the existing `IRA_RMD` funding constraint, including required and available amounts, before cash or result publication.

## 11. Attribution reuse

An actual inherited-source debit initially has the original RMD source record. The existing guarded factory authorizes its recipient using the corresponding resolved disposition and dates it in that calendar year. The engine uses this one record for tax and annual reporting. Ordinary source attribution and Roth destination owner-match guards still apply.

## 12. Effective survivor taxable recipient

The retained account slot remains the original PRIMARY or SPOUSE position. The positive successor RMD identifies the other spouse as the effective modeled payee. The reverse-direction and reversed-priority cases verify both a cross-owner receipt and an ordinary own-account receipt within the same effective pool. An election with no debit has zero successor receipts.

## 13. Federal taxable income

A fully pretax RMD enters the existing ordinary-income path once. The forward no-other-income projection records $15,000.00 in federal AGI and MAGI. Under the fixture's flat 2026 Basic assumptions, modeled federal and Florida income taxes are zero. No transfer cash or separate inheritance income is generated.

## 14. Social Security and Medicare effects

The positive-tax fixture uses $24,000.00 each of Social Security and pension income and a $15,000.00 RMD. Single provisional income is $51,000.00. Taxable Social Security is $18,950.00, AGI and MAGI are each $57,950.00, and taxable income is $41,850.00 after the modeled $16,100.00 deduction. Federal tax is $4,774.00. With $59,200.00 of spending and $2,434.80 in modeled Medicare expenses, the second pass funds another $3,408.80. Retained cash closes at $92,417.20 after including the primary's $12,000.00 spousal top-up in 2033. After-tax estate closes at $457,917.20. These are synthetic flat-rule assumptions for the existing federal model. [IRS Publication 505, 2026 tax schedules](https://www.irs.gov/publications/p505) · [IRS Publication 915](https://www.irs.gov/publications/p915) · [CMS 2026 Medicare premiums](https://www.cms.gov/newsroom/fact-sheets/2026-medicare-parts-b-premiums-deductibles)

## 15. Roth safety

The original living-owner set controls fixed and targeted conversion sources and destinations in the death and successor years. RMDs execute before conversion. For the applied post-death target, each trial is capped by authorized conversion capacity. The same converter produces actual taxable conversion and the portfolio after conversion for trial funding. Final execution uses the same source/destination constraints. The tax-positive capped fixture requests and executes $30,000.00, produces $1,420.00 of federal tax and funds $1,420.00 from the existing available cash path. The fixed control executes $1,000.00 from the living spouse. The existing first-household-RMD latch sees no phantom obligation in 2035. It stops at the actual 2037 obligation in the dedicated latch fixture.

## 16. Employer-account boundary

**UNSUPPORTED_OR_REFUSED.** An original decedent employer or Roth position with a positive balance at first death or later requires separate treatment and refuses the applied route. A preconfigured inherited account with positive assets also refuses. A zero position is permitted when it creates no outstanding account balance or modeled continuation event. A positive living-owner employer position stays independently eligible under the existing account-specific RMD rules. A living rollover IRA with a positive balance stays outside the initial effective personal IRA scope.

## 17. Applied result identity

Schema 4 on the existing retained projection uses `FL-BASIC-SPOUSE-OWNER-IRA-RMD-V1`. The applied identity and canonical annual payload bind to the frozen input digest. The input document retains the selected choice and the annual income row retains the actual debit/payee marker only when a distribution occurs. Pending and legacy retained schemas preserve their old shapes and identities. A pre-policy Florida pending result remains readable and is outdated for recalculation when the live plan now requests the applied route.

## 18. Whole-year historical validation

The applied Complete snapshot independently reruns the same bounded deterministic calculation on restored inputs and compares the recaptured retained graph exactly. This validation works from the original account registry and prior balance flow, through the divisor and ordered debit, to final source receipts, federal amounts, Medicare, cash and closing balances. It validates years with zero distributions too. A retained row supplies no independent authority for its own debit. Certified display restoration rebinds the existing row through `restoreSuccessorRmd` to a detached original account graph and recaptures all stored fields without recalculation. The component proof retains existing checksums, occurrence indices and role IDs. The new applied contract depends on preserving replay-compatible V1 rules and financial semantics for subsequent builds.

## 19. Save, Open, Swap and PDF

Save and Save As retain the original result UUID and applied evidence. Open validates the applied component before display. Swap verifies the checksum and bytes of a previously certified applied component without running financial work. Current and Baseline retain their own frozen inputs. Summary and ordinary PDF use the accepted applied notice. Editing the live preference cannot relabel a frozen applied result. Pending exports retain the planning-only notice.

## 20. Forward-death numerical oracles

| Synthetic fixture | Calculation | Independent expected result |
|---|---|---|
| 1960 PRIMARY dies in 2034, 1962 SPOUSE survives | First survivor commencement in 2037. $369,000 / 24.6. | $0 in 2034–36 and $15,000.00 in 2037. Original PRIMARY ends at $231,000.00, SPOUSE at $123,000.00. |
| Same chronology with $120,000 survivor prior IRA | $366,000 / 24.6. | $14,878.05. Original PRIMARY ends at $231,121.95 and SPOUSE at $120,000.00. |

The first fixture retains $12,565.20 of the RMD after Medicare. Total investable assets close at $454,391.20. A 25% estimated heir tax on $354,000.00 of deferred assets gives an after-tax estate of $365,891.20.

## 21. Reverse-death numerical oracle

The younger SPOUSE dies in 2036. The older PRIMARY receives ordinary amounts of $10,000.00 in 2035 and $9,957.81 in 2036. The verified prior year combined balance is $349,042.19. Dividing by the age-77 factor 22.9 yields $15,242.02 in 2037. With SPOUSE's original position first, it closes at $107,757.98. PRIMARY's original position remains $226,042.19 and the taxable recipient is PRIMARY.

## 22. Split-debit test

The $15,000.00 obligation uses $6,000.00 from PRIMARY's original account and $9,000.00 from SPOUSE's own account, each attributed to SPOUSE. Removing $1.00 from verified available funds yields an `IRA_RMD` shortfall with $14,999.00 available. The immutable input portfolio keeps both original positions on that failure.

## 23. Other negative cases

Missing or duplicate prior position, non-December-31 snapshot, unresolved death year, positive unimplemented decedent employer obligation, unsupported successor discretionary draw and conflicting opening-RMD history refuse. Michigan applied and beneficiary requests are outside the scope. Recomputed component provenance cannot authorize changed receipt amount, recipient, source occurrence, closing balance, obligation or AGI. A1's focused tests also check year, unsupported treatment token and forged payee. [SuccessorRmdRetainedIncomeTest.java](src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/results/SuccessorRmdRetainedIncomeTest.java)

## 24. Legacy financial comparisons

**EXISTING_BEHAVIOR_PRESERVED.** Eleven complete five-year snapshots and eleven legacy characterization variants matched the pre-change transcript, including exact frozen input and annual digests. SHA-256 `05BC3D18C9975B6BBFE8D3157BC066F2C0803362108F105922FB4B8F1FC1F656`. A separate eight-configuration Florida/Michigan raw-engine comparison of both selected-but-pending treatments matched its original annual transcripts. SHA-256 `22A7E84046D07633A09F899EDF8D03BC28AA88919305731EECD5C871A57DBCCC`. The checks include both alive, both death directions, fixed and targeted Roth, Florida, Michigan, Arkansas and a lifetime control. Michigan's legacy hypothetical-age RMD remains a characterization.

## 25. Performance and cancellation

Each applied execution builds the annual membership once, with a linear reference/balance scan, and reuses it through the Medicare pass. The existing solver iteration limits and Arkansas budgets remain in place. An independent certification execution occurs when an applied Complete result is constructed or decoded. Display and certified Swap reconstruct from retained values. The scoped annual engine honors thread interruption before and after each year and before a second Medicare pass. A canceled or refused run leaves the accepted component installed.

## 26. Exact focused test results

The focused verification uses Java 25, IntelliJ's bundled Maven, offline mode and the project-local Maven repository. The selection covers R1 and R2a1, attribution, RMD and opening RMD, withdrawals, legacy survivor calculations, federal and state tax, Medicare, cash settlement, fixed and target Roth, Arkansas proofs, snapshots, disclosures and admission. Excluded exhaustive Monte Carlo, weighted longevity search, benchmarks and large capacity runs.

| Verification | Passed | Failed | Errors | Skipped | Exit |
|---|---:|---:|---:|---:|---:|
| Pre-change focused baseline | 571 | 0 | 0 | 2 | 0 |
| Initial synthetic RMD run | 6 | 1 | 0 | 0 | 1 |
| Initial history test compile | — | — | — | — | 1 |
| Initial combined run | 39 | 2 | 0 | 0 | 1 |
| Corrected combined run | 41 | 0 | 0 | 0 | 0 |
| First full focused selection | 584 | 1 | 0 | 2 | 1 |
| Final focused selection | **585** | **0** | **0** | **2** | **0** |

The retries corrected a synthetic total-assets oracle, two test imports, the expected existing allocator exception class and a scale-sensitive decimal assertion. The first full selection surfaced the test's omitted $12,000.00 pre-death spousal benefit, which the corrected financial oracle checks independently. Financial code did not change to match those assertions. The final skips were existing interactive funding and wizard preview tests. Both Git whitespace checks exited 0. Git emitted line-ending normalization notices.

## 27. Remaining limitations

**UNSUPPORTED_OR_REFUSED.** Advanced mode, other states, applied strategies, existing inherited positions, mixed basis, deferred or contradictory mandatory distribution evidence, positive decedent Roth and employer positions, and separate beneficiary distributions remain outside this applied scope. Positive discretionary spending or funding from a successor retirement source refuses. Ordinary standalone analyzers can use their existing legacy routes, and financial analyzer associations and paired execution cannot present an applied result as their financial reference. Native acceptance of the synthetic Florida scenario has not been run in a separately launched application.

## 28. Readiness for R2a2-B

**DESIGNED_NOT_IMPLEMENTED.** A qualifying fully pretax direct 401(k) rollover would need reviewed plan eligibility and payout terms, a supported date and receiving IRA category, proof of any required distribution carved out of the rollover, and a reconciled prior December 31 transition. Reuse original plan positions and ordered source evidence. Qualifying post-rollover amounts could join survivor IRA membership only after classification and valid non-taxable transfer evidence. Conversion permission and later state qualification need separate approval and tests.

## 29. Explicit future approval decisions

**BLOCKED_PENDING_APPROVAL.** Review the employer-plan authority and exact rollover/election evidence before R2a2-B. Approve beneficiary schedules and death-year residual obligations separately. R3 owns cutoff semantics and R4 owns successor conversion permission. Broader state and analyzer admission and native acceptance remain independent decisions. The worktree is left for review without staging, commits or pushes.
