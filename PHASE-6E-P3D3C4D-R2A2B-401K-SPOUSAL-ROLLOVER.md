# Phase 6E-P3D3C4D-R2a2-B — Florida traditional 401(k) spousal rollover

## 1. Executive summary

**IMPLEMENTED_AND_TESTED.** Florida Basic deterministic projections can model a qualifying, fully pretax direct spousal 401(k) rollover into own-IRA treatment. The rollover retains one original position and adds no taxable income or household cash. Once effective, the position can pay the survivor’s IRA RMD. A plan distribution paid and received on January 1 contributes to the recipient IRA’s prior-balance base beginning the following year.

**UNSUPPORTED_OR_REFUSED.** The requested 1960/1962 example with death in 2034 and rollover in 2035 reaches the employer plan’s first beneficiary distribution calendar year. The January 1 payment does not exempt the calendar-year minimum. The default life-expectancy branch refuses until that amount can be certified and excluded. Changing the synthetic decedent’s birth year to 1961 leaves the survivor age, date and account balances intact and verifies the $15,000.00 later RMD.

## 2. Git and workspace baseline

Opened on `main` at `2bedb55e99fb48aa31225e9c5549c842d77ebb2d`, with 50 tracked modifications, 44 untracked files and an empty index. The prior source and reports were reviewed in place. All opening files outside the listed source changes matched their opening hashes. Synthetic logs and checks are under ignored `target/phase6e-p3d3c4d-r2a2b/`. No private plan was opened. [Minimal-change audit](PHASE-6E-P3D3C4D0R-RMD-ROTH-MINIMAL-CHANGE-AUDIT.md) · [Ownership foundation](PHASE-6E-P3D3C4D-R1-SURVIVOR-OWNERSHIP-FOUNDATION.md) · [R2a admission](PHASE-6E-P3D3C4D-R2A-SURVIVOR-RMD-IMPLEMENTATION.md) · [Unified assumption](PHASE-6E-P3D3C4D-R2A1-UNIFIED-SPOUSAL-INHERITANCE.md) · [IRA scope](PHASE-6E-P3D3C4D-R2A2A-SPOUSE-OWNER-IRA-RMD.md) · [Attribution reuse](PHASE-6E-P3D3C4D-R2A2A1-ATTRIBUTION-REUSE.md) · [Accepted IRA execution](PHASE-6E-P3D3C4D-R2A2A2-OWNER-IRA-RMD-EXECUTION.md)

## 3. Exact source changes

| Area | Files and methods |
|---|---|
| Eligibility and execution | [SpousalIraRmdPolicy.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/SpousalIraRmdPolicy.java) admission, `rolloversAtOpening`, `membership`, `ownership). [ProjectionEngine.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java) ordinary deterministic entry, loop and accepted annual output. [ProjectionYear.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionYear.java) immutable transition metadata. |
| Ownership and attribution | New [Spousal401kRollover.java](src/main/java/com/daviddunn/retirementplanner/domain/retirementownership/Spousal401kRollover.java). [RetirementOwnershipResolver.java](src/main/java/com/daviddunn/retirementplanner/domain/retirementownership/RetirementOwnershipResolver.java) scoped event-aware resolution. [RetirementOwnershipDisposition.java](src/main/java/com/daviddunn/retirementplanner/domain/retirementownership/RetirementOwnershipDisposition.java) treatment and effective category. [SpousalInheritanceDisposition.java](src/main/java/com/daviddunn/retirementplanner/domain/retirementownership/SpousalInheritanceDisposition.java) applied fact classification. [SpousalIraRmdMembership.java](src/main/java/com/daviddunn/retirementplanner/domain/rmd/SpousalIraRmdMembership.java) transition-year base and shared membership. [IncomeSourceAttribution.java](src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java) existing guarded successor-RMD factory and version marker. |
| History and disclosure | [RetainedProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java) schema 5, capture and certified reconstruction. [CompleteProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java) policy-specific replay and currentness. [SpousalInheritancePlanningEvidence.java](src/main/java/com/daviddunn/retirementplanner/app/projection/SpousalInheritancePlanningEvidence.java) scope binding and disclosure. [ApplicationController.java](src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java) dispatch and acceptance. [MainWindow.java](src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java) existing prior-result handling includes employer rollover requests. |
| Tests | New [Spousal401kRolloverExecutionTest.java](src/test/java/com/daviddunn/retirementplanner/domain/projection/Spousal401kRolloverExecutionTest.java). Extended [ProjectionSnapshotPersistenceTest.java](src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotPersistenceTest.java), [RetainedProjectionCodecTest.java](src/test/java/com/daviddunn/retirementplanner/domain/projection/RetainedProjectionCodecTest.java) and [ProjectionPdfExportTest.java](src/test/java/com/daviddunn/retirementplanner/ui/charts/ProjectionPdfExportTest.java). |

## 4. Reused R2a2-A2 components

The engine reuses its annual loop, ordinary IRA RMD calculator, employer RMD service, allocation, withdrawal breakdown, Roth source caps and trial pricing, tax funding, Medicare recalculation, settlement, and accepted history publication. The IRA-only engine entry and schema 4 remain separately callable for their original policy. No account record or portfolio is recreated at transition.

## 5. Current IRS and Treasury findings

Under IRC §402(c)(1), (4), (8), and (9), a surviving spouse can roll over an otherwise eligible qualified-plan distribution to a receiving traditional IRA as if the spouse were the employee. A direct rollover uses the direct payment route under §401(a)(31). A required minimum distribution is excluded from eligible rollover dollars. For death before the required beginning date, §1.402(c)-2(j)(3) excludes any required beneficiary amount for the distribution calendar year. Death on or after that date requires the separate decedent or beneficiary analysis. The 2024 final regulations apply to the modeled RMD and rollover rules from 2025. The future projection continues these rules as a modeling assumption. [IRC §402(c)](https://www.govinfo.gov/content/pkg/USCODE-2024-title26/html/USCODE-2024-title26-subtitleA-chap1-subchapD-partI-subpartA-sec402.htm) · [26 CFR §1.401(a)(31)-1](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a31-1.html) · [26 CFR §1.402(c)-2(j)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-402c-2.html) · [T.D. 10001](https://www.irs.gov/irb/2024-33_IRB)

The default life-expectancy method for the eligible spouse may defer first required distribution until the later of the year after death and the year the employee would attain the applicable age. That is a distribution calendar year. The later payment deadline for a first owner RMD through April 1 and the employer option to defer the owner start until retirement are distinct. The phase uses the earliest age-based owner year as its conservative gate and takes no credit for an employer-plan extension. The special ten-year catch-up and alternate spouse-as-employee election are outside this supported path. Treasury has separately addressed anticipated applicability of outstanding regulations. [IRC §401(a)(9)](https://uscode.house.gov/us/usc/119-124/t26/s401/) · [26 CFR §1.401(a)(9)-2](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-2.html) · [26 CFR §1.401(a)(9)-3](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-3.html) · [26 CFR §1.401(a)(9)-5(a)(2)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-5.html) · [IRS Announcement 2026-7](https://www.irs.gov/irb/2026-11_IRB)

## 6. Employer-plan assumption

Under the existing Owner selection, the admitted hypothetical qualified plan permits a fully pretax, full direct rollover by its sole surviving-spouse beneficiary to the spouse's own traditional IRA. The default life-expectancy method applies while the beneficiary balance remains in the plan. Actual plan terms can vary. The input model records original type, participant facts and opening RMD data. Other plan restrictions, loans, basis and beneficiary arrangement details have no plan-input fields. The supported personal modeling convention supplies these limits. Explicit incompatible values in the frozen input are rejected.

## 7. Supported rollover eligibility

Florida Basic ordinary deterministic, exactly two people, a frozen Owner selection, an eligible modeled death in 2025 or later, both births in the 1960-or-later cohort, original positive traditional 401(k) balance, separate sole survivor, and the January 1 after death within the horizon. Original and prior-balance occurrences must bind. A positive designated Roth, existing inherited or otherwise unsupported deceased retirement position refuses applied succession. The existing Florida path imposes no personal state income tax. [Florida Department of Revenue](https://floridarevenue.com/faq/Pages/FAQDetails.aspx?FAQID=1466)

## 8. Mandatory RMD exclusion

Death before the earliest age-based participant first distribution year clears the admitted owner branch, subject to consistent opening records. The employer-paid beneficiary rollover year must also precede the participant’s would-be commencement year under the modeled life-expectancy method. At equality, a beneficiary minimum belongs to that calendar year and must be distributed separately before rollover. The positive event shape requires a verified zero excluded amount. No amount is manufactured for dates with unresolved obligations. [26 CFR §1.402(c)-2(j)(3)–(4)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-402c-2.html)

## 9. Rollover timing

The model pays the employer distribution and completes the direct receipt on the same January 1 after death. The year of death retains the employer classification and has no rollover. Each positive source transitions once at the opening balance before annual growth and debits. The model leaves a zero source without an event.

## 10. Prior December 31 balance treatment

The plan’s December 31 closing balance certifies the amount paid and received on January 1. The receiving IRA’s prior December 31 balance has no amount attributable to this same-year transfer. Therefore it adds zero to current-year own-IRA RMD sizing. The resulting IRA can pay the survivor’s otherwise required own-IRA RMD under the ordinary IRA aggregation rule. Its actual December 31 closing balance contributes the following year. Cross-calendar-year payments and receipts have a prior-balance adjustment and are outside the modeled timing. [26 CFR §1.401(a)(9)-7(b)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-7.html) · [26 CFR §1.408-8(d)–(e)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-408-8.html)

## 11. Original account and effective IRA identity

The original `AccountReference`, participant and physical `ProjectedPortfolio` position remain unchanged. The event names the original source, effective survivor, date and amount. The policy identifies the receiving status as survivor-owned traditional IRA. The original withdrawal source remains the existing employer occurrence. Effective IRA classification begins with the completed rollover.

## 12. Membership transition

While the participant lives, the source has its original employer RMD category. After death and before rollover it has no ordinary survivor IRA membership. Once the rollover is verified, each event binds one source to the survivor’s existing annual IRA group. Personal IRA elections remain individually resolved. The survivor's own workplace plan retains a separate employer RMD.

## 13. RMD sizing and account debits

The existing annual ownership membership selects both prior-balance sizing and permitted original-position debits. Same-year amounts add zero to sizing and can satisfy the group’s verified requirement. Later years include the actual prior balance. Allocation follows persisted position order. Split debits use the existing breakdown. When $14,999.00 is available for a $15,000.00 verified requirement, the existing `IRA_RMD` funding constraint identifies the $1.00 shortfall. [IRS RMD FAQs and IRA aggregation rules](https://www.irs.gov/retirement-plans/retirement-plan-and-ira-required-minimum-distributions-faqs)

## 14. Existing attribution reuse

The rollover is opening transition evidence on the existing annual projection. A later distribution uses the existing RMD cash-flow row and `forSuccessorRmd` authorization. The new marker requires a qualifying resolved rollover disposition, the original 401(k) occurrence, matching year and the actual fully taxable debit. The payee is the survivor. Ordinary source-owner validation remains the default.

## 15. Federal income and cash

The transfer generates $0 taxable income, $0 spendable cash and $0 change in net worth. The actual RMD enters ordinary federal income once. In the positive-tax fixture, a $15,000.00 RMD plus $24,000.00 each of Social Security and pension produces $51,000.00 of provisional income and $18,950.00 taxable Social Security. AGI and modeled Medicare MAGI are each $57,950.00. With the fixture’s flat 2026 parameters, federal tax is $4,774.00, modeled Medicare is $2,434.80, additional tax funding is $3,408.80, and after-tax estate is $457,917.20. [IRS Publication 915](https://www.irs.gov/publications/p915) · [IRS 2026 tax schedules](https://www.irs.gov/publications/p505) · [2026 CMS Medicare premiums](https://www.cms.gov/newsroom/fact-sheets/2026-medicare-parts-b-premiums-deductibles)

## 16. Roth restrictions

The applied route uses the existing living-original-owner restrictions during fixed execution and executable targeted trials. An original deceased source stays excluded regardless of IRA RMD membership. The mandatory debit occurs before conversion. The fixed control converts $1,000.00 of living-owner assets. The capped target converts the living spouse’s $30,000.00 and funds $1,420.00 of federal tax. The automatic cutoff still uses the accepted RMD trigger.

## 17. Discretionary withdrawal limitations

Positive ordinary successor retirement draws and tax-funding withdrawals remain refused. Original eligible living sources and ordinary household cash remain usable. The smallest follow-on would approve one bounded fully pretax discretionary debit shape, with separate effective-recipient evidence, funding certification, and frozen history tests. Conversion authorization remains a separate scope.

## 18. Applied history identity

`FL-BASIC-SPOUSE-OWNER-401K-DIRECT-ROLLOVER-V1` binds retained schema 5 and its source occurrences to the frozen inputs and whole-year payload. The existing Annual rows hold cash, income, RMD, and closing balances. The additive event list records each actual positive rollover once. Its schema implies one traditional IRA receiving classification and zero required amount excluded. Earlier IRA-only schema 4 and its original identifier remain distinct.

## 19. Snapshot reconstruction

Applied history replay uses the corresponding existing financial path on frozen inputs and compares exact retained events and annual ledgers. A stored marker cannot prove its own amount. The test rejects a forged balance or date even with rebuilt provenance. Certified presentation carries the transition to its annual projection, rebinds the original occurrence and restores the existing successor debit attribution. Save, Save As, Open and bidirectional Swap with an IRA-only baseline preserve roles, UUIDs, ledgers and provenance. The replay contract depends on retaining compatible historical government rules and financial semantics, currently the 2026.1 resource. A future incompatible calculation requires a separate identity and the compatible historical validation path.

## 20. PDF and UI disclosure

The existing two-option editor explains the employer availability caveat. Results Summary and PDF distinguish a frozen IRA-only result from one with a qualified 401(k) rollover. A subsequent live edit cannot alter the accepted disclosure. A schema-5 result with an exhausted balance says that no employer balance remained to roll over. Nonadmitted modes continue using pending language.

## 21. Forward-death oracle

1961 PRIMARY dies on January 1, 2034. The 1962 survivor receives $246,000.00 of virtual rollover treatment on January 1, 2035, before the participant’s 2036 commencement year. Existing survivor IRA balance is $123,000.00. No owner RMD occurs through 2036. In 2037, $369,000.00 / 24.6 = **$15,000.00**. Original PRIMARY closes at $231,000.00 and survivor IRA at $123,000.00. Survivor taxable amount is $15,000.00, with one receipt. After-tax estate is $365,891.20 in the isolated zero-growth fixture. For the requested 1960 PRIMARY birth, the 2035 rollover instead gets `UNRESOLVED_MANDATORY_DISTRIBUTION`.

## 22. Reverse-death oracle

The 1962 SPOUSE dies in 2035. The 1960 PRIMARY has a $10,000.00 own-IRA requirement in 2035 ($246,000.00 / 24.6). The plan rolls $123,000.00 on January 1, 2036. The rollover-year base is $236,000.00, so the owner RMD is **$9,957.81** at divisor 23.7. The original employer occurrence pays it, closes at $113,042.19 and leaves the own IRA at $236,000.00. In 2037 the combined $349,042.19 / 22.9 produces **$15,242.02**, paid once to PRIMARY. Moving the younger spouse's death to 2036 would put the 2037 rollover in the plan’s first beneficiary distribution year and refuses.

## 23. Multiple 401(k) and IRA tests

Two rolled 401(k)s of $6,000.00 and $4,000.00, one $3,000.00 deceased IRA and one $356,000.00 living IRA form a $369,000.00 group. Ordered debits are $6,000.00, $4,000.00, $3,000.00 and $2,000.00. A separate $24,600.00 living 401(k) pays its own $1,000.00 RMD. A zero employer balance has no event. Reversed priority pays the $15,000.00 IRA total from the living IRA and leaves the separately required employer debit.

## 24. Rejected unsupported scenarios

**UNSUPPORTED_OR_REFUSED.** Required-beneficiary rollover years and death during or after owner commencement, existing inherited and designated Roth assets, incompatible opening-RMD history or participant dates, missing, duplicate or mismatched source and prior balances, forged applied history, unsupported taxable or discretionary draws, and non-Florida or analyzer execution. The synthetic input decoder checks that explicitly specified nonspouse, trust, basis, loan, plan restriction or alternative receiving facts are refused as outside the input schema. They cannot be provided as ordinary supported plan facts. Unrelated payee and the wrong year remain incompatible with the existing authorized debit checks. Inherited plan subtype coverage is limited to the currently recognized inherited account inputs.

## 25. Legacy financial comparisons

**EXISTING_BEHAVIOR_PRESERVED.** The predecessor’s focused suite served as baseline. The historical regression exercises both applied schemas plus legacy and pending restorations. Eleven five-year synthetic snapshots and eleven characterization variants matched digest `05BC3D18C9975B6BBFE8D3157BC066F2C0803362108F105922FB4B8F1FC1F656`. The eight pending-choice Florida and Michigan configurations matched digest `22A7E84046D07633A09F899EDF8D03BC28AA88919305731EECD5C871A57DBCCC`. Both probes exited 0. This covers both-alive and earlier deaths, legacy Florida, Michigan and Arkansas, fixed and targeted Roth, and a legacy lifetime control.

## 26. Performance and cancellation

One indexed scan of prior closing balances establishes the rollover amounts at the eligible opening. The resulting immutable map is reused for later years. Existing annual membership and the Medicare pass share the same source set. No extra tax iteration or Arkansas budget is added. Cancellation and refusals retain the last accepted result. No partial result is attached to history.

## 27. Exact test results

Java 25 and IntelliJ bundled Maven use the workspace-local dependency repository and offline batch mode. Scope includes the previous focused selector and employer rollover tests. Exhaustive Monte Carlo, weighted longevity, benchmarks and large capacity suites were excluded.

| Run | Passed | Failed | Errors | Skipped | Maven exit |
|---|---:|---:|---:|---:|---:|
| Opening focused baseline | 585 | 0 | 0 | 2 | 0 |
| Production compile | — | — | — | — | 0 |
| Initial applied test selection | 53 | 1 | 0 | 0 | 1 |
| First expanded focused selection | 596 | 3 | 0 | 2 | 1 |
| Final focused selection | **600** | **0** | **0** | **2** | **0** |

The initial failing negative test exposed that invalid historical 401(k) paid amounts reached the older opening-RMD calculator first. The admission now classifies that contradictory history explicitly before annual execution. The expanded run identified two legacy refusal-message assertions and an explicit field inventory for retained records. The original refusal wording was restored for legacy history and the inventory now accounts for schema 5. The two final skips are existing interactive preview tests. Both Git whitespace checks exited 0 with line-ending normalization notices.

## 28. Outstanding limitations

**UNSUPPORTED_OR_REFUSED.** Alternate beneficiary methods, including an optional ten-year branch, actual plan administration and earlier plan-specific payment requirements, out-of-transit adjustments, mixed or after-tax balances, designated Roth and 403(b) succession, and separately payable beneficiary or death-year amounts. Rollover results are Florida Basic deterministic only. No separate native application acceptance was performed in this phase.

## 29. Next beneficiary implementation contracts

**DESIGNED_NOT_IMPLEMENTED.** An employer-beneficiary phase needs a frozen plan method and chronology, the applicable distribution calendar year and denominator, prior assets and paid amounts, attribution to the beneficiary, actual original-source mandatory payments, and a tax/funding/reporting proof. The plan and IRA beneficiary branches need separate account categories and aggregation rules. An eventual post-RMD direct rollover can consume only the independently reconciled eligible remainder.

## 30. Explicit decisions requiring approval

**BLOCKED_PENDING_APPROVAL.** Authorize any beneficiary schedules, ten-year or catch-up variation, and the separate treatment of calendar-year amounts before a later rollover. Review the distinct scope for discretionary funding and for successor Roth conversion. Additional states, analyzer delivery and employer-plan classes need their own admission. This work stops before R2a3. No staging, commits or pushes were performed.
