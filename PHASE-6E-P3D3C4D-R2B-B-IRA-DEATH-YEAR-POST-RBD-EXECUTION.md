# Phase 6E-P3D3C4D-R2b-B — Florida traditional IRA death-year and post-RBD execution

## Scope and checkpoint

**IMPLEMENTED_AND_TESTED.** Florida Basic deterministic execution supports traditional IRA succession in the first distribution calendar year, the following January before the April 1 required beginning date (RBD), and a modeled January 1 death after RBD. An admitted post-RBD minimum is calculated under the decedent's owner rules and paid from the original IRA group to the surviving spouse. The following January applies either direct own-IRA redesignation or the ordinary sole-spouse beneficiary schedule. Existing successor ordinary spending and tax funding continue under both treatments after effective succession.

Local `main` and `origin/main` pointed to `43f4dda9dd544bba58df5e95d401063291ebe645`. The working tree already had R2a4, M1 and the R2b-A audit, plus the earlier `exio` edit to the R2a3-B report. The index was empty. The user accepted R2a4 and M1 natively and explicitly approved the R2b-A IRA design. Financial fixtures are synthetic. [Approved audit](PHASE-6E-P3D3C4D-R2B-A-DEATH-YEAR-POST-RBD-RMD-AUDIT.md) · [R2a4 execution](PHASE-6E-P3D3C4D-R2A4-SUCCESSOR-ORDINARY-WITHDRAWALS.md) · [M1 baseline](PHASE-6E-P3D3C4D-R2A4-M1-REGRESSION-TEST-MAINTENANCE.md)

## Federal rules and modeled chronology

The IRA branch uses the applicable-age year and the legally distinct April 1 RBD. For a June 1960 birth, the first distribution calendar year is 2035 and RBD is April 1, 2036. Both January 1, 2035 and January 1, 2036 are before RBD and generate no inherited death-year owner amount. January 1, 2037 is after RBD. Prior actual/model receipts remain assigned to their original year. For a post-RBD death, the original owner minimum uses the previous December 31 balance and birthday age for the year of death. The survivor receives the unpaid amount, with zero prior same-year owner payment under the modeled January 1 boundary. The implemented Uniform Lifetime branch excludes decedents with a sole spouse more than ten years younger when the joint table is required. [IRC §401](https://www.govinfo.gov/content/pkg/USCODE-2024-title26/html/USCODE-2024-title26-subtitleA-chap1-subchapD-partI-subpartA-sec401.htm) · [IRC §408](https://www.govinfo.gov/content/pkg/USCODE-2024-title26/html/USCODE-2024-title26-subtitleA-chap1-subchapD-partI-subpartA-sec408.htm) · [§1.401(a)(9)-2](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-2.html) · [§1.401(a)(9)-5](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-5.html) · [§1.401(a)(9)-9](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-9.html) · [IRS Pub. 590-B](https://www.irs.gov/publications/p590b)

Direct redesignation takes effect January 1 following death. The inherited source's closing value then enters the survivor's separate effective owner IRA group with the original survivor IRAs. Retained spouse beneficiaries instead start in the following year after a post-RBD death, using the greater of the current-year spouse Single Life factor and the deceased owner's death-year Single Life factor minus elapsed years. The existing pre-RBD delay and divisor survive for qualifying pre-RBD deaths. T.D. 10001 governs distribution calendar years since 2025. The ordinary sole-spouse method is a modeled qualifying assumption. The proposal for a spouse-as-employee election remains outside this phase, with the delayed future applicability described in IRS Announcement 2026-7. [§1.401(a)(9)-3](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-3.html) · [§1.408-8](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-408-8.html) · [IRS Announcement 2026-7](https://www.irs.gov/pub/irs-drop/a-26-07.pdf)

## Evidence, allocation and settlement

- The controller selects the new route at the existing Florida Basic deterministic gate for a qualifying IRA and age/calendar-year range. The policy distinguishes the exact modeled death date from RBD. The earlier pre-RBD calculation entry points remain available for historical replay and unchanged qualifying older routes.
- When opening at death, each original IRA requires its applicable-year opening record, matching prior December 31 and opening position values, and zero already paid in the death calendar year. If the plan starts after the decedent's first distribution year, the existing opening RMD entry accepts an explicit per-IRA confirmation that earlier calendar-year RMD obligations are resolved. This is user-affirmed evidence for the projection and implies neither an imported payment nor a tax receipt. Where the first living distribution year is modeled, existing owner execution supplies that compliance basis. Both the current year and first-year deadline remain separate.
- `SpousalIraRmdMembership` binds the death-year original IRA positions before allocation. `OwnerRmdService` sizes the deceased group from its preceding December 31 balances. `HouseholdRmdResult` carries the deceased mandatory IRA amount alongside survivor and existing independent obligations. `ProjectedWithdrawalAllocator` draws only from that group's original positions in existing order. A split uses distinct source occurrences and cannot draw the other spouse's own IRA toward the deceased obligation.
- The existing debit receipt carries the mandatory death-year treatment, original account and survivor payee. This keeps the decedent excluded from living-owner Roth and household activity. The cash proceeds enter existing household settlement and federal income once. Discretionary death-year access to the original retirement positions remains gated until effective succession.
- Following-year R2a4 funding keeps priority, actual source debit reconciliation, the beneficiary-death or owner 59½ treatment, tax gross-up and Medicare repricing. With an admitted positive death-year receipt, Medicare is recomputed from the settled AGI and retries the existing annual path if the funded premium changes. The legal benefit and rate formulas, NIIT, annuity gates and existing tax and Medicare model conventions remain in place. The current Roth stop condition receives the actual household requirement through its established input. No successor source is added to the conversion allocator.

## Independent numerical verification

| Synthetic control | Required figures |
| --- | --- |
| 2040 original decedent born 1960, survivor born 1965, both death orders | Decedent $808,000 ÷ 20.2 = $40,000, payable to survivor. Survivor own $246,000 ÷ 24.6 = $10,000. Zero growth, closes at $768,000 and $236,000. AGI of $50,000 yields $1,780 federal tax at the fixture's $32,200 deduction and 10% bracket. |
| 2041 own election | ($768,000 + $236,000) ÷ 23.7 = $42,362.87. |
| 2041 retained beneficiary | $768,000 ÷ max(14.1, 11.2 − 1) = $54,468.09. Survivor's separate $236,000 ÷ 23.7 = $9,957.81. Total $64,425.90. |
| 2042 younger decedent born 1965, survivor born 1960, both death orders | $458,000 ÷ 22.9 = $20,000. Original closes $438,000. In 2043 older spouse's factor 9.3 loses to 13.3 − 1 = 12.3, giving $35,609.76. |
| Two original IRAs | $20,200 and $787,800 total $808,000. Actual debits $20,200 and $19,800 satisfy the single $40,000 deceased group. Survivor's original account still pays $10,000. |
| Earlier living payment and pre-April death | Modeled 2035 payment $10,000 from $246,000, followed by a January 1, 2036 death. Preserve the $10,000 prior-year debit and $236,000 close. Zero 2036 decedent requirement and first beneficiary distribution year 2037. |
| 2041 successor funding | With $52,000 expense and $2,434.80 Medicare, $42,362.87 mandatory cash leaves $12,071.93 ordinary spending. Fixed Single brackets produce $4,352.176 tax before funding and $4,945.65 after the 12% gross-up. |
| Death-year Medicare boundary | $200,000 deceased minimum and $10,000 survivor RMD cover $210,000 expenses before premiums. Survivor-owned IRA then funds Medicare and tax. Final MAGI crosses the first MFJ Medicare tier, premium settles at $3,583.20, and the 24% bracket fixed point is $37,799.96 tax funding. Exactly one $200,000 successor receipt remains. |

## Historical replay and UI

**IMPLEMENTED_AND_TESTED.** Retained schema 9 applies `FL-BASIC-SPOUSAL-DEATH-YEAR-RMD-V1`. The frozen input graph establishes exact RBD classification, zero credited death-year amount, original IRA positions and earlier-year evidence. Annual mandatory totals, the tagged survivor RMD debit, original references, successor obligations and actual selected beneficiary divisor are retained by the existing ledger. The independent `CompleteProjectionSnapshot` replay compares the full annual result, including original positions, settlement, estate, taxes and final MAGI. Replay rejects changed source, payee, debit amount, missing proof and divisor even when provenance is rebuilt.

Save, Save As, Open, two-way Swap and PDF use the same complete snapshot paths. The frozen disclosure identifies traditional IRA Florida Basic scope and January 1 chronology. The only new input control is a scoped confirmation within the existing opening RMD dialog for applicable older original IRAs. Existing schema 1–8 policy names and replay paths remain compatible. Direct election has no separate rollover event or duplicated IRA.

**EXISTING_BEHAVIOR_PRESERVED.** The narrower accepted pre-RBD routes, both earlier employer routes, original tax-funding and cash conventions, old new-plan and analyzer trajectories, and state calculation gates retain their own policy identities. Cancellation and an invalid opening-evidence rerun both retain the last accepted result. The M1 fixture checks continue to exercise wizard defaults, layout, interning and the Alabama classification gate.

## Refusals and limits

**UNSUPPORTED_OR_REFUSED.** Missing or mismatched opening values, positive paid amount in the January death year, missing earlier-year confirmation, duplicate sources, unsupported joint table, other deceased-owned positive retirement assets, post-RBD employer rollover or beneficiary execution, and unapproved state/mode requests are outside the new authority. An original group with insufficient funds uses the existing structured funding shortfall and publishes no completed failing year. The existing typed discretionary-source refusal applies when expenses or taxes would require unauthorized death-year access. Occupation, employer payout alternatives, historical date/payment disputes, different beneficiaries, mixed basis, third-party beneficiaries, multiple deaths and spouse-as-employee elections are outside the bounded model. The ordinary sole-spouse and fully pretax facts continue to be planning assumptions for qualifying personal traditional positions. Current input types do not provide per-account alternative elections or basis fields.

**DESIGNED_NOT_IMPLEMENTED.** Historical within-year owner payments require verified dates and separately accounted opening cash. Employer post-RBD execution requires verified retirement and plan terms and, for rollover, a distinct rollover-year mandatory payout. R2b-C, R2b-D, R3 and R4 remain **BLOCKED_PENDING_APPROVAL**.

The existing applied-route eligibility still requires supported 1960-or-later birth cohorts and at least one modeled calendar year after death. The prior-year confirmation supplies an opening modeling fact. It does not independently verify account custodian records or cover an overdue distribution correction.

## Changed files

Paths below are relative to the indicated Java source root and the `com/daviddunn/retirementplanner` package.

| Root | Files |
| --- | --- |
| `src/main/java` | `app/projection/CompleteProjectionSnapshot.java`, `app/projection/SpousalInheritancePlanningEvidence.java`, `app/socialsecurity/results/RetainedProjectionSnapshot.java`, `domain/income/IncomeSourceAttribution.java`, `domain/projection/ProjectionEngine.java`, `domain/projection/SpousalIraRmdPolicy.java`, `domain/retirementownership/RetirementOwnershipDisposition.java`, `domain/retirementownership/RetirementOwnershipResolver.java`, `domain/rmd/HouseholdRmdResult.java`, `domain/rmd/OpeningRmdAccountData.java`, `domain/rmd/SpousalIraRmdMembership.java`, `ui/controller/ApplicationController.java`, `ui/dialogs/OpeningRmdDialog.java`, `ui/rmd/OpeningRmdWorkflowService.java` |
| `src/test/java` | `domain/projection/SpousalIraDeathYearExecutionTest.java`, `domain/tax/TaxFundingCalculatorTest.java`, `persistence/ProjectionSnapshotPersistenceTest.java`, `ui/charts/ProjectionPdfExportTest.java` |
| Repository root | `PHASE-6E-P3D3C4D-R2B-B-IRA-DEATH-YEAR-POST-RBD-EXECUTION.md` |

## Maven, Git and native acceptance

Java 25 and bundled Maven use the workspace-local repository. The broad selector is the unchanged bounded M1 selector, with its established long-running exclusions.

| Run | Passed | Failures | Errors | Skipped | Maven exit |
| --- | ---: | ---: | ---: | ---: | ---: |
| M1 bounded baseline | 5,707 | 0 | 0 | 35 | 0 |
| Final focused financial, history, UI and M1 verification | 649 | 0 | 0 | 1 | 0 |
| First bounded broad attempt | 5,729 | 1 | 0 | 35 | 1 |
| Final bounded broad rerun | 5,731 | 0 | 0 | 35 | 0 |

The first broad attempt exposed a timing-sensitive assertion in `Stage5DCancellationTest.cancellationWaitsForActiveEngineCallsAndStopsAtExistingBoundaries`, fallback case [2], line 66. The executor had reported termination and no queued work had started, but a worker still reported `Thread.isAlive()` at the immediate check. The exact fallback assertion also failed at repetition 175 when invoked repeatedly from the unmodified checkpoint's compiled test classes. Eight isolated Maven invocations passed at the checkpoint before the repeated-call diagnostic reproduced the failure. The analyzer test and production route were preserved.

The final retry passed both Stage 5D cases, all 21 new IRA execution cases and the 24 new tests overall. The 35 skips match M1 by class and count across 23 classes. The first broad attempt ran before the final January 2037 Roth-cutoff case was compiled. Raw Maven results are in `target/r2b-b-current-focused.log`, `target/r2b-b-broad.log` and `target/r2b-b-broad-retry.log`. The checkpoint diagnostic transcript is in `target/r2b-b-stage5d-checkpoint-probe.log`.

Both `git diff --check` and `git diff --cached --check` exited 0. The index is empty and HEAD remains at the starting checkpoint.

For native acceptance, use a synthetic Florida Basic couple with a June 1960 primary and June 1965 spouse. Set projection start to January 1, 2040, zero growth and zero other income. Use prior and current IRA values $808,000 and $246,000, zero already distributed in 2040 and mark preceding original IRA years resolved in Opening RMD. Select primary death in 2040, compare Owner and Beneficiary outputs against the table, Save As, reopen, swap the two elected results and export PDFs. Repeat with opposite owner identities. A subsequent run with the original IRA confirmation cleared should refuse while the accepted historical result remains available. Stop after native acceptance and await explicit R2b-C approval.
