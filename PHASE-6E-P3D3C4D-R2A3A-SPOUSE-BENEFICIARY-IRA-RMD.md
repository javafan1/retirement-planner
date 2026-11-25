# Phase 6E-P3D3C4D-R2a3-A — Florida spousal beneficiary IRA RMDs

## 1. Executive summary

**IMPLEMENTED_AND_TESTED.** Florida Basic deterministic projections now pay required distributions from qualifying personal traditional IRAs retained by the sole surviving spouse as beneficiary. The modeled method uses the decedent’s applicable year for commencement and redetermines the surviving spouse’s Single Life divisor each year. The beneficiary group stays separate from the survivor’s own IRA and workplace plans. Each actual debit keeps its original investment position and identifies the survivor as its taxable recipient.

The verified $500,000 fixtures produce a lawful deferral while the older survivor owes an own-IRA RMD, then a $61,728.40 beneficiary RMD in its first required year. In the reverse death order the younger survivor owes a $30,487.80 beneficiary RMD before reaching their own first RMD year.

## 2. Current Git and worktree baseline

Opened on `main` at `2bedb55e99fb48aa31225e9c5549c842d77ebb2d` with 52 tracked modifications, 47 untracked files and an empty index. Eighteen opening files changed, all listed in the source and test inventory. All other opening files matched their opening hashes. Logs and synthetic outputs are under ignored `target/phase6e-p3d3c4d-r2a3a/`. No private saved plan was opened.

R2a2-A2 native acceptance was reported. The R2a2-B report records automated acceptance, with no native acceptance claimed here. The applicable foundation, source, and tests were reviewed before implementation. [Minimal-change audit](PHASE-6E-P3D3C4D0R-RMD-ROTH-MINIMAL-CHANGE-AUDIT.md) · [Ownership foundation](PHASE-6E-P3D3C4D-R1-SURVIVOR-OWNERSHIP-FOUNDATION.md) · [Unified assumption](PHASE-6E-P3D3C4D-R2A1-UNIFIED-SPOUSAL-INHERITANCE.md) · [Attribution reuse](PHASE-6E-P3D3C4D-R2A2A1-ATTRIBUTION-REUSE.md) · [Owner IRA](PHASE-6E-P3D3C4D-R2A2A2-OWNER-IRA-RMD-EXECUTION.md) · [Owner 401(k) rollover](PHASE-6E-P3D3C4D-R2A2B-401K-SPOUSAL-ROLLOVER.md)

## 3. Exact modified files and method sites

The paths in this table are relative to the indicated source tree.

| Area | Files and sites |
|---|---|
| Policy and execution | [SpousalIraRmdPolicy.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/SpousalIraRmdPolicy.java) admission, `membership` and `ownership`. [ProjectionEngine.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java) applied entry and accepted annual output. [ProjectionYear.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionYear.java) immutable obligation evidence. |
| Ownership | [RetirementOwnershipDisposition.java](src/main/java/com/daviddunn/retirementplanner/domain/retirementownership/RetirementOwnershipDisposition.java), [RetirementOwnershipResolver.java](src/main/java/com/daviddunn/retirementplanner/domain/retirementownership/RetirementOwnershipResolver.java) and [SpousalInheritanceDisposition.java](src/main/java/com/daviddunn/retirementplanner/domain/retirementownership/SpousalInheritanceDisposition.java) beneficiary disposition, effective recipient and verified successor classification. |
| RMD and debits | New [BeneficiaryIraRmd.java](src/main/java/com/daviddunn/retirementplanner/domain/rmd/BeneficiaryIraRmd.java), [SingleLifeRmdTable.java](src/main/java/com/daviddunn/retirementplanner/domain/rmd/SingleLifeRmdTable.java) and [Table I resource](src/main/resources/rules/rmd-single-life-v1.txt). [SpousalIraRmdMembership.java](src/main/java/com/daviddunn/retirementplanner/domain/rmd/SpousalIraRmdMembership.java), [RmdCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/rmd/RmdCalculator.java), [HouseholdRmdCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/rmd/HouseholdRmdCalculator.java), [HouseholdRmdResult.java](src/main/java/com/daviddunn/retirementplanner/domain/rmd/HouseholdRmdResult.java) and [ProjectedWithdrawalAllocator.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectedWithdrawalAllocator.java) annual obligation, calculation and ordered allocation. [IncomeSourceAttribution.java](src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java) adds obligation-checked beneficiary authorization. |
| History and presentation | [RetainedProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java), [CompleteProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java) and [SpousalInheritancePlanningEvidence.java](src/main/java/com/daviddunn/retirementplanner/app/projection/SpousalInheritancePlanningEvidence.java) schema, replay certification and frozen wording. [ApplicationController.java](src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java) dispatch and pending notice. [MainWindow.java](src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java) existing failure handling includes the new route. |
| Tests | New [SpousalBeneficiaryIraRmdExecutionTest.java](src/test/java/com/daviddunn/retirementplanner/domain/projection/SpousalBeneficiaryIraRmdExecutionTest.java). Extended [RetainedProjectionCodecTest.java](src/test/java/com/daviddunn/retirementplanner/domain/projection/RetainedProjectionCodecTest.java), [ProjectionSnapshotPersistenceTest.java](src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotPersistenceTest.java) and [ProjectionPdfExportTest.java](src/test/java/com/daviddunn/retirementplanner/ui/charts/ProjectionPdfExportTest.java). |

## 4. Verified governing IRS rules

Under §1.408-8(a) and (b), IRA beneficiary distributions use the §401(a)(9) regulations and the prior December 31 balance. A sole individual surviving spouse is an eligible designated beneficiary. For the supported pre-required-beginning-date branch, §1.401(a)(9)-3(c)(4) and (d) permit annual life-expectancy payments and the special spousal commencement deferral. Section 1.401(a)(9)-5(a)(2)(iii), (d)(2) and (d)(3)(i) govern the first year and annual spouse redetermination. T.D. 10001 applies to RMD distribution calendar years from January 1, 2025. [26 CFR §1.408-8](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-408-8.html) · [26 CFR §1.401(a)(9)-3](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-3.html) · [26 CFR §1.401(a)(9)-5](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-5.html) · [T.D. 10001](https://www.irs.gov/irb/2024-33_IRB) · [IRS beneficiary RMD guidance](https://www.irs.gov/retirement-plans/required-minimum-distributions-for-ira-beneficiaries)

## 5. Supported beneficiary method

Florida Basic ordinary deterministic, one eligible first death during the modeled horizon from 2025 onward, two spouses born in 1960 or later, and a qualifying original personal traditional IRA. The plan-level Beneficiary selection models sole direct spouse status, a fully pretax IRA, no contradictory inherited facts, and continued beneficiary status under the life-expectancy method. First death must precede the decedent’s first applicable distribution calendar year. Opening RMD information must agree with that conclusion. This narrow gate avoids the separate legal branch involving an unpaid death-year RMD or the first distribution year's later payment deadline.

## 6. Special spouse rules and elections reviewed

The applied V1 convention assumes no §327 spouse-as-employee election and uses Single Life. It also assumes the spouse does not elect own-IRA status or an alternative ten-year method. The §327 statute and its pending interpretive rules can change the denominator for other methods. The 2024 proposal would apply a spouse-as-employee rule automatically in specified pre-RBD cases and use the Uniform Lifetime Table. IRS Announcement 2026-7 provides anticipated applicability and reasonable good-faith interim interpretation. No final rule replacing this no-election calculation was located as of November 24, 2026. Future projected years keep this explicitly disclosed calculation assumption. An unsupported election or changed applicable method requires a separately reviewed policy. [IRC §401(a)(9)(B)(iv)](https://uscode.house.gov/us/usc/119-124/t26/s401/) · [REG-103529-23](https://www.irs.gov/irb/2024-33_IRB) · [IRS Announcement 2026-7](https://www.irs.gov/irb/2026-11_IRB) · [IRS Publication 590-B](https://www.irs.gov/publications/p590b)

## 7. Distribution commencement year

The first beneficiary year is the later of the year after death and the decedent’s applicable age year. Both cohorts use age 75 under the supported input rules. The annual evidence carries an explicit deferred obligation of zero before commencement and no divisor. It also records the real December 31 balance for that year. No beneficiary receipt is created during deferral. The annual owner RMD requirement continues on its independent schedule. [26 CFR §1.401(a)(9)-3(d)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-3.html) · [IRS Publication 590-B, special rules for surviving spouse](https://www.irs.gov/publications/p590b)

## 8. Beneficiary divisor methodology

The pinned resource contains Table I from §1.401(a)(9)-9(b). The applicable divisor is based on the beneficiary’s age on their birthday in the distribution year. The existing decimal RMD arithmetic divides each eligible account’s prior December 31 balance and rounds to cents. The payable group amount is the sum. A missing prior balance or unavailable divisor refuses, and an explicitly zero prior balance is a valid verified input. [26 CFR §1.401(a)(9)-9(b)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-9.html)

## 9. Original and effective account identity

The original account keeps its participant, type, reference, and projected position. It has an effective beneficiary recipient and has no effective ordinary owner RMD slot. Both death directions use the same implementation. The position remains in the household portfolio and follows the original investment return and exposure.

## 10. RMD grouping and aggregation

The immutable annual membership binds the original portfolio, separate own-IRA and beneficiary memberships, and prior balance occurrences. One beneficiary group can combine ordinary inherited IRAs with the same modeled decedent and sole beneficiary. The survivor’s own IRAs, workplace plans, inherited assets from other decedents and other classes cannot satisfy that group. Within each permitted group the established original account order controls debits. Living-owner 401(k) requirements remain account-specific. [26 CFR §1.408-8(e)(1)–(3)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-408-8.html)

## 11. Actual debit and taxable receipt

The shared allocator receives the same obligation instance that was sized from annual membership. It debits only beneficiary member positions for this payment, in order, and preserves the existing `IRA_RMD` shortfall result. The resulting balance difference is the actual distribution. Two receipts are generated only if two original accounts were actually debited. A receipt identifies the original account and survivor and pays fully pretax ordinary income once.

## 12. Federal tax, Medicare and cash

The 2035 tax fixture uses a $15,000.00 beneficiary RMD and $24,000.00 each of pension and gross Social Security. Provisional income is $51,000.00 and taxable Social Security is $18,950.00. AGI and Medicare MAGI are $57,950.00. Under the frozen flat 2026 rules, federal tax is $4,774.00, Medicare is $2,434.80, and additional tax funding is $3,408.80. Retained cash ends at $49,286.80 and the after-tax estate ends at $414,786.80. The original IRA closes at $231,000.00. Inheritance alone creates no income or cash. Existing federal and state tax formulas are unchanged by this applied treatment. [IRS Publication 915](https://www.irs.gov/publications/p915) · [IRS 2026 tax schedules](https://www.irs.gov/publications/p505) · [CMS 2026 Medicare premiums](https://www.cms.gov/newsroom/fact-sheets/2026-medicare-parts-b-premiums-deductibles)

## 13. Existing attribution reuse

The accepted RMD row has a narrowly scoped beneficiary treatment marker. It requires a fully taxable positive debit, a verified beneficiary disposition, and a payable obligation matching the parties and calendar year. The amount cannot exceed the obligation. The existing ordinary source-owner guard and both owner-mode markers remain intact. The annual obligation is proof data and uses the existing snapshot. The monetary receipt remains the one income row.

## 14. Roth eligibility and cutoff safeguards

The inherited source stays excluded from fixed and targeted conversion. The surviving original eligible source can still execute under the existing schedule, funding and cap rules. Tests verify an existing first-household-RMD stop yields zero conversions in the first beneficiary RMD year, and the existing Never stop permits a $1,000.00 fixed or $51,500.00 targeted living-owner conversion in that fixture. RMDs execute first. The revised R3 roadmap is recorded for future approval, including continued conversion by configured ending year, a positive-RMD-year skip strategy, and a permanent first-RMD stop. Legacy stopping remains versioned separately.

## 15. Discretionary withdrawal boundary

An executed beneficiary RMD can fund normal expenses and taxes through the existing cash path. An ordinary positive spending or tax-funding draw against a successor retirement source outside its mandatory payment refuses and leaves the last accepted result available. R2a4 can reuse the original-account authorization boundary for separately verified ordinary or tax-funding receipts, using its own allowed financial scope. R2a4 execution is deferred.

## 16. Owner-mode nonregression

**EXISTING_BEHAVIOR_PRESERVED.** The Owner IRA commencement, 401(k) rollover-year exclusions, own IRA divisors, Roth restrictions, tax settlement and history are covered by the predecessor tests. The same-household comparison exercises both Owner and Beneficiary policies with equivalent year, accounts and assumptions. Stored policy identities remain distinct. The beneficiary method adds no optional field to serialized schemas 1 through 5 when its evidence is absent.

## 17. Employer beneficiary follow-on design

**DESIGNED_NOT_IMPLEMENTED.** R2a3-B needs a separately verified plan method, applicable first beneficiary year, employer-plan terms, account-specific balance and denominator, qualifying spouse status, and prior mandatory history. It can reuse the annual obligation, source-debit authorization and historical certification concepts. Employer obligations cannot be combined with the IRA beneficiary group or each other solely because one spouse receives them.

## 18. Applied historical identity

`FL-BASIC-SPOUSE-BENEFICIARY-IRA-RMD-V1` is frozen with schema 6. It identifies the no-election life-expectancy method, binds the input digest and annual receipt graph, and holds one obligation per applicable year. The record includes year, commencement, original decedent, survivor recipient, prior group balance, divisor when due, and required amount. The original account reference is still in the existing income source and retained portfolio graph. Certification re-executes the frozen route and compares exact stored values, including cash, tax and closing amounts. The archived replay depends on its compatible 2026.1 government rules and V1 Single Life resource.

## 19. Save, Open, Swap and PDF validation

Save, Save As, Open, original projection restoration and two-way Current/Baseline Swap were exercised with the original accepted Owner result. Deferred obligations with no divisor survive codec round-trip. After a live strategy edit the PDF still names the frozen applied treatment. Tests reject forged commencement, divisor, source occurrence, actual amount, payment year, payee, duplicated receipt, closing balance and AGI, including when the caller rebuilds provenance.

## 20. Independent financial oracles

| Fixture and date | Independent calculation | Expected result |
|---|---|---:|
| Older survivor age 75 in 2035 | Own IRA $246,000 / 24.6, inherited IRA retains $500,000 under the legal deferral | $10,000.00 own, $0 beneficiary |
| Same survivor in 2036 | Own IRA $236,000 / 23.7, beneficiary first year 2045 | $9,957.81 own, $0 beneficiary |
| Same survivor age 85 in 2045 | $500,000 / Table I 8.1 | $61,728.40 beneficiary |
| Same survivor age 86 in 2046 | $438,271.60 / Table I 7.6 | $57,667.32 beneficiary |
| Younger survivor age 73 in 2035 | $500,000 / Table I 16.4 | $30,487.80 beneficiary |
| Same survivor age 74 in 2036 | $469,512.20 / Table I 15.6 | $30,096.94 beneficiary |

## 21. Forward and reverse death tests

1962 SPOUSE beneficiary of 1960 PRIMARY, dying in 2034, first distributes in 2035 based on decedent age year. The original PRIMARY source closes at $469,512.20 after the first $30,487.80 payment. The surviving spouse’s existing IRA stays unchanged that year. In the reverse fixture, 1970 SPOUSE dies in 2035, the 1960 PRIMARY already has an own-IRA requirement, and the inherited $500,000 remains intact until the 2045 commencement. Both original sources debit with the survivor’s payee and receive a fresh divisor in the following year.

The separate group tests verify a beneficiary obligation of $15,000.00 split into $6,000.00 and $9,000.00, reversed original-source ordering, preserved own IRA and living employer obligations, and a typed $1.00 funding shortfall. In the latter case $14,999.00 is available and the survivor's own IRA cannot cover the beneficiary group's shortfall.

## 22. Unsupported legal branches

**UNSUPPORTED_OR_REFUSED.** Owner death in or after the first distribution year, unpaid death-year RMD, post-required-beginning-date method, alternate elections, then-applicable spouse-as-employee rules, ten-year and catch-up methods, beneficiaries other than the sole spouse, preexisting inherited positions, trust or mixed basis, and existing rollover histories outside the supported IRA category. Duplicated membership, missing valuation or unsupported inherited positions refuse. Explicit nonspouse, trust, election, method or basis values outside the model schema fail strict frozen input restoration. The hypothetical planning convention supplies qualifying facts for ordinary supported original accounts.

## 23. Focused test results

Java 25.0.4.1 and IntelliJ Maven used the approved project-local repository in offline batch mode. Scope included the predecessor focused selector, the beneficiary suite and applied history/PDF coverage. Exhaustive Monte Carlo, longevity searches, and benchmarks were excluded.

| Run | Passed | Failed | Errors | Skipped | Maven exit |
|---|---:|---:|---:|---:|---:|
| Opening baseline | 600 | 0 | 0 | 2 | 0 |
| Production compilation | — | — | — | — | 0 |
| Beneficiary, persistence and PDF selection | 92 | 0 | 0 | 0 | 0 |
| Final expanded focused selection | **613** | **0** | **0** | **2** | **0** |

The two skips are existing interactive funding and wizard preview tests. `git diff --check` and `git diff --cached --check` both exited 0. Git reported line-ending normalization notices.

## 24. Legacy financial comparison

Eleven full legacy five-year snapshots and eleven characterization variants matched digest `05BC3D18C9975B6BBFE8D3157BC066F2C0803362108F105922FB4B8F1FC1F656`. Eight legacy engine pending-choice fixtures matched digest `22A7E84046D07633A09F899EDF8D03BC28AA88919305731EECD5C871A57DBCCC`. Both probe processes exited 0. They cover both-alive and survivor calculations, Florida, Michigan, Arkansas, fixed and targeted Roth, and a legacy lifetime control. The focused suite also exercises the accepted applied 401(k) and Owner IRA paths and existing Arkansas funding proofs.

## 25. Performance and cancellation

Annual membership indexes original positions and prior December 31 balances once. Existing owner and employer RMD calculations reuse their pathways. Beneficiary sizing is one pass over its members and uses one direct Table I lookup. The annual membership passes into the existing Medicare recalculation. Certification replay reuses the original engine. A refusal or cancellation leaves the previously accepted history attached. Arkansas budgets remain unchanged by this implementation.

## 26. Exact remaining Florida limitations

Applied beneficiary support is limited to qualifying original personal traditional IRA mandatory payments in Florida Basic deterministic mode. Retained inherited 401(k)s, employer plan distribution methods, designated Roth, 403(b), discretionary inherited spending and funding and successor conversions remain outside the applied scope. Broader states, Advanced mode, lifetime and other analyzer routes cannot publish applied beneficiary results. No separately launched native application acceptance was performed for R2a3-A.

## 27. Readiness for R2a3-B

**DESIGNED_NOT_IMPLEMENTED.** The application now has an existing-ledger beneficiary event, explicit policy identity, frozen annual obligation and separate membership concept suitable for the employer review. Employer support requires legal verification and separate plan/participant applicability evidence, commencement and annual expected values, and an account-specific debit proof. It should add a separately versioned applied policy.

## 28. Explicit approval decisions

**BLOCKED_PENDING_APPROVAL.** Employer-plan beneficiary distribution execution is the next decision. Separately approve alternate beneficiary elections, post-RBD or death-year unpaid amounts, discretionary withdrawal funding, R3 stopping strategy, R4 conversion scope and state or analyzer expansion. R2a3-B has not started. Nothing was staged, committed or pushed.
