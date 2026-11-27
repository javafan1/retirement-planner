# R2b-C · Florida post-RBD spouse beneficiary 401(k) execution

## Applied scope and baseline

**IMPLEMENTED_AND_TESTED.** Florida Basic normal deterministic projections can pay a traditional 401(k) participant's death-year minimum to the sole surviving spouse and continue the ordinary spouse-beneficiary life-expectancy method. The original employer account remains the source throughout. The policy is `FL-BASIC-SPOUSAL-401K-POST-RBD-BENEFICIARY-V1`, retained schema 10.

**EXISTING_BEHAVIOR_PRESERVED.** Work started on `main` at `43f4dda9dd544bba58df5e95d401063291ebe645`, tracking `origin/main`. The working tree already contained R2a4, M1 and R2b-B production and test changes, predecessor reports and the R2a3-B report edit. All were preserved. R2b-B native acceptance and its bounded 5,731 passes with 35 skips are the comparison baseline reported by the user.

## Federal rules and plan facts

**IMPLEMENTED_AND_TESTED.** IRC §401(a)(9)(C) and 26 CFR §1.401(a)(9)-2 ordinarily set an employer-plan RBD on April 1 after the later of the applicable-age year and the retirement year. Statutory 5% ownership and plan-specific age-based terms can change the retirement dependency. Actual payment commencement does not establish the legal RBD. The admitted subset requires a traditional fully pretax plan, birth in 1960 or later, dated separation and met retirement conditions by December 31 of the applicable-age year, and plan confirmation of the compatible April 1 RBD. For this subset the two eligible dates resolve to the same age-based result. Older cohorts, separation after the applicable-age year, special owner cases and terms with a different RBD are outside this slice. [IRC §401](https://uscode.house.gov/us/usc/119-124/t26/s401/) · [IRS employer-plan RMD guidance](https://www.irs.gov/retirement-plans/a-guide-to-common-qualified-plan-requirements) · [Treasury Decision 10001, §1.401(a)(9)-2](https://www.irs.gov/irb/2024-33_IRB)

**IMPLEMENTED_AND_TESTED.** §1.401(a)(9)-4 recognizes a qualifying surviving spouse as an eligible designated beneficiary. For a post-RBD death, §1.401(a)(9)-5 governs the open death-year minimum and the subsequent account distributions. The beneficiary minimum begins the following year and uses the greater of the spouse's annually recalculated Single Life factor and the employee's death-year Single Life factor less elapsed calendar years. The employee's death-year owner minimum uses the prior December 31 plan balance and birthday-age Uniform Lifetime divisor. The Joint and Last Survivor Table case remains outside admission when the sole spouse is more than ten years younger. T.D. 10001 applies for distribution calendar years starting January 1, 2025. Its §1.402(c)-2 applies to distributions from January 1, 2025 and excludes RMDs from eligible rollovers. [T.D. 10001, §§1.401(a)(9)-3, -4, -5, -9 and 1.402(c)-2](https://www.irs.gov/irb/2024-33_IRB) · [IRC §402](https://uscode.house.gov/us/usc/119-124/t26/s402/)

**EXISTING_BEHAVIOR_PRESERVED.** SECURE 2.0 §327 provides the separate spouse-as-employee election. The implementing provisions remain reserved in T.D. 10001 and were proposed under REG-103529-23. IRS Announcement 2026-7 anticipates applicability to the distribution calendar year beginning no earlier than six months after final regulations are issued and calls for reasonable good-faith interpretation in the interim. The ordinary post-RBD spouse method here uses the final table provisions and compatible plan terms with no alternative election. [T.D. 10001 and REG-103529-23](https://www.irs.gov/irb/2024-33_IRB) · [IRS Announcement 2026-7](https://www.irs.gov/pub/irs-drop/a-26-07.pdf)

**IMPLEMENTED_AND_TESTED.** Employer payout terms are plan-dependent. The optional date in the existing account Participant facts editor attests that the administrator confirmed the legal RBD for the fully pretax original plan with its complete vested value represented by one account occurrence, the modeled spouse is the sole direct beneficiary, and the plan permits the ordinary life-expectancy method and additional beneficiary payouts. It also attests no conflicting payout election or 5% owner exception applies. Actual plan terms can require faster payouts. The model treats these as supplied confirmations. [IRS beneficiary guidance](https://www.irs.gov/retirement-plans/plan-participant-employee/retirement-topics-beneficiary) · [IRS 401(k) distribution rules](https://www.irs.gov/retirement-plans/plan-participant-employee/401k-resource-guide-plan-participants-general-distribution-rules)

## Chronology, balances and execution

**IMPLEMENTED_AND_TESTED.** Death is modeled January 1, strictly after the confirmed RBD. When opening on that date, the prior December 31 balance must match the opening original position, the opening distribution year must match, and the same-year paid amount must be explicitly zero. An opening after the first distribution year requires source-specific confirmation that earlier calendar-year obligations are resolved. When the projection models earlier living distribution years, the existing living-owner calculation supplies those years' required distributions. The separate opening workflow confirmation is per account.

The annual ownership resolver binds the payment to the living beneficiary without adding the participant to the set of living owners. The existing employer RMD calculator sizes each original plan minimum. The household RMD result carries those account-specific amounts through the existing mandatory allocator. One plan's assets cannot satisfy another plan's obligation. The debit is attributed once as fully taxable ordinary retirement income to the surviving spouse. The prior position, actual account debit and remaining original position are reused by the existing projection and settlement. The annual filing status and Medicare covered-owner conventions are carried through. Fully pretax beneficiary distributions are taxable income and qualify for the death exception to the 10% early distribution additional tax. [IRC §72(t)(2)(A)(ii)](https://uscode.house.gov/us/usc/119-124/t26/s72/)

**IMPLEMENTED_AND_TESTED.** Mandatory cash can fund expenses and taxes immediately. Discretionary spending and tax funding continue from the following year under R2a4, with original account bindings, existing priorities and household cash settlement. Medicare repricing uses the existing retry when income changes. A death-year fixture uses $210,000 household RMD cash for $210,000 of expenses. The living IRA covers premiums and federal tax. The existing Medicare convention settles at $3,583.20, and the fixture's federal bracket fixed point is x = $35,932 + 0.24 × (x − $30,016.80), giving about $37,799.96 of tax funding. There is one $200,000 death-year employer receipt. Tax, Medicare, NIIT and Roth reuse their existing formulas. The death-year minimum enters the same household RMD total that drives the current Roth cutoff. Beneficiary employer positions keep their existing Roth ineligibility.

The annual membership keeps source bindings and prior balances in existing indexes. Mandatory processing and discretionary funding reuse the existing annual valuation, withdrawal and settlement sequence. Opening-year annual and remaining requirements carry the same unpaid decedent minimum. The allocator executes the remaining requirement once.

## Independent financial checks

Amounts below use the applicable published Uniform Lifetime and Single Life tables, rounded to cents. The source fixtures use zero investment growth.

| Fixture | Death year | First beneficiary year |
| --- | --- | --- |
| Older survivor, both death directions | 2042 employer minimum $458,000 ÷ 22.9 = $20,000. Original balance $438,000. | 2043 factor max(9.3, 13.3 − 1) = 12.3. Employer minimum $438,000 ÷ 12.3 = $35,609.76. |
| Younger survivor, both death directions | 2040 employer $808,000 ÷ 20.2 = $40,000, leaving $768,000. Separate survivor IRA $246,000 ÷ 24.6 = $10,000, leaving $236,000. Household RMD and AGI $50,000. | 2041 employer factor max(14.1, 11.2 − 1) = 14.1, giving $54,468.09. Survivor IRA $236,000 ÷ 23.7 = $9,957.81. Combined $64,425.90. |
| Two separate plans | 2042 additional plan $229,000 ÷ 22.9 = $10,000, leaving $219,000. The first plan pays $20,000. | Additional plan $219,000 ÷ 12.3 = $17,804.88, separate from $35,609.76. |
| Prior compliance from modeled living years | 2040 pays $246,000 ÷ 24.6 = $10,000. 2041 pays $236,000 ÷ 23.7 = $9,957.81. | The 2041 closing valuation is $226,042.19. The 2042 death-year RMD from that balance is $9,870.84 and goes to the survivor. |
| Mandatory shortfall | A market loss leaves the first plan below its required minimum despite liquidity elsewhere. The result is an account-RMD funding failure. | Structured outcome with zero completed annual results. |

[IRS statutory table reproduction, Publication 590-B Appendix B](https://www.irs.gov/publications/p590b) · [T.D. 10001, §1.401(a)(9)-9](https://www.irs.gov/irb/2024-33_IRB)

## Historical proof and application workflows

**IMPLEMENTED_AND_TESTED.** Schema 10 identifies the employer death-year receipt separately from subsequent plan minimums and ordinary spending or tax-funding receipts. The certified input graph stores the plan-specific date confirmation, dated participant facts, opening values and prior-year clearance. The original anonymous account occurrence, survivor recipient, actual source debit, ending balance and annual settlement are retained. Subsequent rows store the first distribution year, prior valuation, selected divisor and each employer amount. The component Single Life factors can be recomputed from frozen birthdays, distribution year, death year and the policy.

The complete snapshot re-executes the frozen inputs and compares the entire annual result before accepting retained evidence. Tests cover amended amount, payee, source, divisor, deleted payment evidence, and contradictory RBD after digest and provenance rebuilding. Save, Save As, Open, baseline and two-way Swap exercise frozen UUIDs and annual evidence. Results Summary and PDF use the accepted disclosure after a live assumption change. An interrupted or refused recalculation preserves the prior accepted result. Schemas 1–9 retain their policy dispatch and old participant facts documents retain their original serialization when the optional date is absent.

## Admission limits and next phase

**UNSUPPORTED_OR_REFUSED.** Unconfirmed or contradictory employer facts, missing or inconsistent valuation or chronology, unresolved prior obligations, dated already-paid death-year amounts, unsupported Table II and life-expectancy depletion boundary, mixed positive deceased-owned retirement account families, incompatible balances or source bindings, second death and nonspousal or alternative payout arrangements remain outside admission. A shortfall in an original plan remains a funding failure. Earlier pre-RBD 401(k) branches retain their original cutoffs. Other states and analyzer modes keep their existing route semantics. The new branch requires Florida Basic and an effective following year in the projection horizon.

**DESIGNED_NOT_IMPLEMENTED.** R2b-D employer rollover requires a separate distribution and transfer sequence, an eligible transfer balance and rollover-year receiving IRA evidence. The mandatory distributions established here can inform that work. The spouse-as-employee fork, dated payment imports, broader RBD plan terms, Roth policy work and additional state or analyzer authority remain outside this phase.

**BLOCKED_PENDING_APPROVAL.** R2b-D is pending native acceptance of this slice and explicit authorization.

## Changed-file inventory for R2b-C

Production files are listed relative to `src/main/java/com/daviddunn/retirementplanner`.

- `app/projection/CompleteProjectionSnapshot.java`
- `app/projection/SpousalInheritancePlanningEvidence.java`
- `app/socialsecurity/results/RetainedProjectionSnapshot.java`
- `domain/income/IncomeSourceAttribution.java`
- `domain/model/RetirementParticipantFacts.java`
- `domain/projection/ProjectionEngine.java`
- `domain/projection/SpousalIraRmdPolicy.java`
- `domain/retirementownership/RetirementOwnershipDisposition.java`
- `domain/retirementownership/RetirementOwnershipResolver.java`
- `domain/rmd/EmployerPlanRmdService.java`
- `domain/rmd/HouseholdRmdResult.java`
- `domain/rmd/SpousalIraRmdMembership.java`
- `ui/controller/ApplicationController.java`
- `ui/controls/RetirementParticipantFactsEditor.java`
- `ui/dialogs/AccountDialog.java`
- `ui/dialogs/OpeningRmdDialog.java`

Test files are listed relative to `src/test/java/com/daviddunn/retirementplanner`.

- `domain/projection/RetainedProjectionCodecTest.java`
- `domain/projection/SpousalIraDeathYearExecutionTest.java`
- `domain/projection/SpousalPostRbd401kExecutionTest.java`
- `persistence/ProjectionSnapshotPersistenceTest.java`
- `ui/charts/ProjectionPdfExportTest.java`

Documentation is this report. The inventory distinguishes this phase from the earlier changes already in the worktree.

## Automated verification

Java 25 and IntelliJ's bundled Maven used the workspace-local `.codex-m2/repository`. The bounded selector remains `target/r2a4-suite-selector.txt` with the established long-running exclusions.

| Run | Passed | Failures | Errors | Skipped | Maven exit |
| --- | ---: | ---: | ---: | ---: | ---: |
| Accepted R2b-B bounded baseline | 5,731 | 0 | 0 | 35 | 0 |
| First bounded R2b-C run | 5,748 | 2 | 0 | 35 | 1 |
| Final focused run | 732 | 0 | 0 | 1 | 0 |
| Final bounded run | 5,753 | 0 | 0 | 35 | 0 |

The first bounded run identified two assertion corrections. `RetainedProjectionCodecTest.immutableSharedLeafSchemasAndProjectionFieldInventoryAreExplicit` now lists the schema 10 optional plan-confirmation field. `ProjectionSnapshotPersistenceTest.employerPostRbdHistoryCertifiesPerPlanPaymentAndSwaps` now compares $39,000 by numeric value across exact decimal scales. Both corrections retain the independently calculated financial goldens. The previously reported timing-sensitive `Stage5DCancellationTest` passed both cases in both bounded runs. Its source is unchanged.

The final bounded run added 22 passes over R2b-B. The 35 skips matched the same 23 classes and counts. The final focused selector includes all spousal RMD and withdrawal tests, the retained codec and Swap classes, and the M1 maintenance classes. The focused skip is the established opt-in `DavidMedicareDeathAuditTest`. Logs are `target/r2bc-broad.log`, `target/r2bc-focused-certified.log` and `target/r2bc-broad-final.log`. Both Git whitespace checks exited 0. The index is empty and HEAD is still at the starting checkpoint.

## Native Windows acceptance pending

1. Open a synthetic Florida Basic plan using Spouse as Beneficiary. Model a 2042 first death and an original traditional 401(k) balance of $458,000. Use the older-survivor fixture's 1965 and 1960 births, zero growth and dated retirement in 2030. Enter the administrator-confirmed April 1, 2041 date in the original 401(k)'s Participant facts.
2. Start the projection on January 1, 2042. In Opening RMD Information enter $458,000 preceding December 31 value, explicitly enter zero already distributed and confirm prior-year obligations are resolved. Supply any separate survivor owner evidence as applicable.
3. Confirm the $20,000 original-plan minimum paid to the spouse, $438,000 original-plan close before additional activity and $35,609.76 following-year minimum. Verify the Results Summary and PDF disclose the confirmed plan terms and January 1 death.
4. Save, Save As and Open the synthetic result, create or restore a baseline and Swap twice. Confirm the accepted history remains available when a new run with missing plan confirmation is refused. Repeat with opposite owners if verifying both UI directions.
