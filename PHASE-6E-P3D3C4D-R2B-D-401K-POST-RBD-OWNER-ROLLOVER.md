# R2b-D · Florida post-RBD spouse-as-owner 401(k) rollover

## Scope and starting checkpoint

**IMPLEMENTED_AND_TESTED.** Florida Basic normal deterministic projections under Spouse as Owner pay the original traditional 401(k)'s death-year minimum to the survivor. The following January 1, each original plan pays its own beneficiary minimum before its eligible balance enters effective survivor-owned traditional IRA treatment. The rollover retains the original investment occurrence and plan lineage.

**EXISTING_BEHAVIOR_PRESERVED.** Work started on `main` at `43f4dda9dd544bba58df5e95d401063291ebe645`, tracking the local `origin/main` reference. Status showed unstaged accepted R2a4 through R2b-C work, predecessor reports and the earlier R2a3-B report edit. All were preserved. The accepted comparison baseline was 5,753 bounded regression passes and 35 skips.

## Verified federal rules and plan-dependent facts

IRC §401(a)(9)(B)(i) continues distributions after a post-RBD death. T.D. 10001 applies to distribution calendar years beginning in 2025. Under 26 CFR §1.402(c)-2(j)(3)(i)(F), the death-year rollover exclusion follows §1.401(a)(9)-5(c). For subsequent distribution calendar years it follows §1.401(a)(9)-5(d) and (e). In the admitted ordinary sole-spouse life-expectancy branch, this means that the first beneficiary year's denominator is the greater of the spouse's current-year Single Life factor and the employee's death-year Single Life factor less elapsed years. The special ten-year-method catch-up provision is outside this branch. The modeled full remainder qualifies for direct rollover to the spouse's own traditional IRA under IRC §402(c), subject to the plan facts. The RMD exclusion and the rollover exclusion from current taxable income have separate effects. [IRC §401](https://uscode.house.gov/us/usc/119-124/t26/s401/) · [IRC §402](https://uscode.house.gov/us/usc/119-124/t26/s402/) · [T.D. 10001](https://www.irs.gov/irb/2024-33_IRB) · [IRS Publication 575](https://www.irs.gov/publications/p575)

The IRA's RMD ordinarily uses the December 31 balance of the preceding year. Section 1.408-8(d) applies §1.401(a)(9)-7(b) to the outstanding-rollover adjustment. A rollover sent and received in January contributes no amount to the preceding December 31 value. An amount sent in the earlier year and received in the next year can require an increase to that preceding December 31 value. The modeled transfer is fully completed on January 1 of the calendar year after death. Each receiving position contributes its actual closing balance to the next year's RMD valuation. [IRC §408](https://uscode.house.gov/us/usc/119-124/t26/s408/) · [T.D. 10001, §§1.401(a)(9)-7 and 1.408-8](https://www.irs.gov/irb/2024-33_IRB) · [IRS Publication 590-B](https://www.irs.gov/publications/p590b)

SECURE Act §401 generally applies to employee deaths after 2019. For the admitted birth cohorts, SECURE 2.0 §107 and §1.401(a)(9)-2 set the applicable age to 75. SECURE 2.0 §327 provides a separate surviving spouse election. The pertinent mechanics are reserved in T.D. 10001 and addressed in the proposal REG-103529-23. Announcement 2026-7 anticipates applicability no earlier than the distribution year beginning six months after final regulations issue. This branch requires ordinary life expectancy under confirmed plan terms. [Treasury final and proposed rules](https://www.irs.gov/irb/2024-33_IRB) · [IRS Announcement 2026-7](https://www.irs.gov/pub/irs-drop/a-26-07.pdf)

**IMPLEMENTED_AND_TESTED.** The model reuses the plan-confirmed RBD and both dated participant facts from R2b-C. The participant must have separated and met the plan's retirement conditions by December 31 of the applicable-age year. The verified date must match the legal April 1 date for the narrow admitted branch with no contradictory 5% owner or plan terms. The prior-year compliance confirmation and applicable opening values remain source-specific. At an opening January death, each position requires an explicit zero same-year payment and matching prior December 31 valuation. Exact historical payments and regulatory exceptions are outside the modeled sequence.

The existing traditional 401(k) Participant facts editor offers a scoped confirmation that the plan permits the sole surviving spouse to take the required payment first and directly roll the entire remaining pretax balance into their own traditional IRA on the modeled date. A date confirmation established under the Beneficiary phase supplies the RBD and ordinary payout facts. The direct-rollover permission is independent. Both are modeled attestations supplied by the user from plan facts. Plan-level Spouse as Owner remains the only election.

## Execution, allocation, tax and growth

**IMPLEMENTED_AND_TESTED.** The death-year binding, calculation, actual debit and survivor payee reuse R2b-C. The following January uses a temporary beneficiary binding for the source-specific minimum. The existing allocator pays it once before rollover and investment growth. The engine compares the actual remaining balance to prior December 31 minus the verified exclusion before authorizing each surviving positive position's rollover. The original position then participates in the survivor's qualified owner IRA group. Taxable payee, original account identity and each actual debit remain distinct from the rollover event.

The annual growth basis excludes the cash paid out on that January boundary. The annual opening total, portfolio withdrawals, available cash, household RMD and settlement preserve that same debit exactly once. The existing annual allocator handles the survivor-owned group's separately sized RMD after growth. In the fixture, the rolled position is first in the existing own-IRA withdrawal order and pays a separate owner-IRA RMD after receiving the transfer. The earlier employer payout is a distinct mandatory distribution.

Federal income, Social Security, Medicare and current tax funding use the ordinary taxable RMD receipts and any lawful ordinary debits. Direct pretax transfer principal remains in retirement assets. Annual cash funding includes mandatory payouts. The Medicare retry reuses the resolved January payment and eligible remainder. Subsequent ordinary spending and tax funding reuse the R2a4 receipt rules with existing age safeguards. The death exception applies while the source is a qualifying beneficiary distribution. Effective owner withdrawals retain their own applicable age gate. The household RMD input continues to drive the existing Roth cutoff. The source availability for Roth stays within its prior rules.

## Independent synthetic oracles

| Test | Independent calculation |
| --- | --- |
| Death in 2042, either spouse first | The 1965 employee age 77 has $458,000 ÷ 22.9 = $20,000 required, leaving $438,000 at zero growth. Survivor age 82 separately pays $185,000 ÷ 18.5 = $10,000 from the existing IRA. |
| January 2043 original plan | Age-83 spouse factor 9.3. Age-77 employee factor 13.3 less one = 12.3. The required plan payout is $438,000 ÷ 12.3 = $35,609.76. The eligible direct rollover is $402,390.24. |
| 2043 survivor IRA and 2044 valuation | The 2042 own-IRA close is $175,000. The 2043 owner minimum is $175,000 ÷ 17.7 = $9,887.01. Household 2043 minimum and AGI are $45,496.77 with no additional taxable receipts. Under the fixture's withdrawal order, the rolled position closes at $392,503.23 and the original IRA at $175,000. The 2044 owner minimum is $567,503.23 ÷ 16.8 = $33,779.95. |
| 10% positive growth | The death-year employer close is $483,800. January exclusion is $39,333.33 and eligible principal is $444,466.67 to cents. Current-year receiving IRA base is $193,500 and its minimum is $10,932.20. Positive growth on $444,466.67 followed by that owner payout leaves the rolled position at $477,981.14 to cents. |
| Two employer plans | A pays $20,000 then $35,609.76 and rolls $402,390.24. B starts at $229,000, pays $10,000 then $17,804.88 and rolls $201,195.12. The separate owner minimum $9,887.01 gives total rollover-year taxable receipts of $63,301.65. |
| Spending, tax funding and Medicare | With $95,000 spending and the settled $3,583.20 annual premium, the minimum leaves $53,086.43 of additional ordinary spending withdrawals. The independently solved federal gross-up is ($5,800 + 22% × ($98,583.20 − $16,100 − $50,400)) ÷ 78% = approximately $16,485.01. Ending retained spending cash is zero. |

[IRS Uniform Lifetime and Single Life tables](https://www.irs.gov/publications/p590b)

## Historical evidence and application workflows

**IMPLEMENTED_AND_TESTED.** Schema 11 and `FL-BASIC-SPOUSAL-401K-POST-RBD-OWNER-ROLLOVER-V1` bind the original employer occurrence to two distinct RMD phases and the eventual owner treatment. The frozen plan carries the RBD, opening facts, recipient derivation and rollover confirmation. The retained records provide the beneficiary numerator, divisor, required payout and eligible direct rollover amount. The complete snapshot independently replays the frozen inputs and compares the annual financial graph. The restoration path then rebinds the certified records using the original pre-transfer and post-transfer treatment as applicable to each receipt. Changed amount, gross source, recipient, divisor, RBD, eligible principal or removed evidence is rejected after provenance is rebuilt. Receiving-IRA valuation is independently established by annual replay and the numeric next-year controls.

Save, Save As, Open, accepted history, baseline, two-way Swap and PDF use the existing pathways. Frozen Results Summary and PDF disclosure explain the January post-RBD death, separate original-plan mandatory payment, direct rollover of the eligible remainder and receiving IRA year-end valuation. Cancellation and an inadmissible recalculation preserve the last accepted snapshot.

**EXISTING_BEHAVIOR_PRESERVED.** Schemas 1–10 keep their respective policies. Absent optional direct-rollover confirmation keeps the legacy participant-facts wire representation. Pre-RBD rollover and beneficiary routes, post-RBD retained beneficiary employer and IRA routes, owner IRA RMDs and existing other-state and analyzer controls retain their financial behavior.

## Refusals and remaining limitations

**UNSUPPORTED_OR_REFUSED.** Missing or inconsistent employer facts, opening history or prior compliance, a credited payment in the modeled January death year, unresolved original-plan amount, insufficient original-plan assets, unsupported Table II and divisor boundaries, mixed positive deceased retirement families, incompatible ownership or payout terms, unsupported plan basis and out-of-scope state, analysis and mode requests remain outside the applied policy. Incompatible beneficiaries, indirect or straddling-year rollovers, partial transfers, nonstandard plan terms and alternative elections are outside the input attestation and model boundary. The original employer balance is represented by one complete vested account occurrence per plan. An exhausted position contributes no positive transfer.

**DESIGNED_NOT_IMPLEMENTED.** The existing annual evidence can identify mandatory payments, ordinary distributions and direct rollover amounts for later Roth policy work. Expansion of conversion sources, historical payment imports and broader plan configurations require separate phases.

**BLOCKED_PENDING_APPROVAL.** R3 awaits native acceptance of R2b-D and explicit authorization.

## Changed files for R2b-D

Production paths relative to `src/main/java/com/daviddunn/retirementplanner`.

- `app/projection/CompleteProjectionSnapshot.java`
- `app/projection/SpousalInheritancePlanningEvidence.java`
- `app/socialsecurity/results/RetainedProjectionSnapshot.java`
- `domain/model/RetirementParticipantFacts.java`
- `domain/projection/ProjectionEngine.java`
- `domain/projection/SpousalIraRmdPolicy.java`
- `domain/retirementownership/RetirementOwnershipResolver.java`
- `domain/retirementownership/Spousal401kRollover.java`
- `domain/rmd/SpousalIraRmdMembership.java`
- `ui/controller/ApplicationController.java`
- `ui/controls/RetirementParticipantFactsEditor.java`

Test paths relative to `src/test/java/com/daviddunn/retirementplanner`.

- `domain/projection/RetainedProjectionCodecTest.java`
- `domain/projection/SpousalPostRbd401kOwnerExecutionTest.java`
- `persistence/ProjectionSnapshotPersistenceTest.java`
- `ui/charts/ProjectionPdfExportTest.java`

Documentation is this report. The inventory identifies this phase's edits within the existing unstaged work.

## Automated verification

Java 25 and IntelliJ's bundled Maven used the repository-local `.codex-m2/repository`, offline. The bounded run uses the existing `target/r2a4-suite-selector.txt` with its established exclusions.

| Run | Passed | Failures | Errors | Skipped | Maven exit |
| --- | ---: | ---: | ---: | ---: | ---: |
| Accepted R2b-C bounded baseline | 5,753 | 0 | 0 | 35 | 0 |
| Isolated owner, beneficiary and pre-RBD rollover classes | 33 | 0 | 0 | 0 | 0 |
| Final focused | 747 | 0 | 0 | 1 | 0 |
| Final bounded | 5,768 | 0 | 0 | 35 | 0 |

The final focused run includes 13 new execution cases and the new history and PDF workflow cases. Its skip is the existing opt-in audit. During development, the historical restoration cases exposed two distinct bindings needed for the original plan and effective owner receipts, including a year that carries both for the same original occurrence. The independent tamper fixture was also corrected to alter gross and taxable shares together so the malformed amount reaches the financial replay check. Numerical fixtures accounted for the existing high-precision growth and removed an unrelated cash reserve in the Medicare test.

Logs are `target/r2bd-verified-focused.log` and `target/r2bd-verified-broad.log`. The known timing-sensitive Stage 5D analyzer test stays in the same bounded suite with its existing assertions. Both Stage 5D cases passed. The 35 bounded skips matched the R2b-C counts in the same 23 classes. Both Git whitespace checks exited 0. The index is empty and HEAD remains at the starting checkpoint.

## Native Windows acceptance

1. Open a synthetic Florida Basic plan using Spouse as Owner and zero growth. Start January 1, 2042 with a 1965 employee and a 1960 spouse. Use the $458,000 original 401(k) and $185,000 survivor IRA fixture. Set first death to 2042 and retain at least three projection years.
2. Record dated retirement by 2040, compatible plan facts, and plan-confirmed RBD April 1, 2041 on the original 401(k). Confirm the plan permits the direct rollover. Supply both January opening RMD records and clear the original plan's earlier-year obligations with explicit zero paid in 2042.
3. Verify $20,000 original-plan death-year RMD and $10,000 survivor RMD. For 2043 confirm the $35,609.76 plan payment, $402,390.24 eligible transfer and independently sized $9,887.01 IRA minimum. Confirm the combined owner valuation generates $33,779.95 in 2044. The preceding oracles assume zero other taxable income and sufficient household liquidity.
4. Save, Save As, reopen, swap the result and a baseline twice, and inspect the PDF disclosure. Clear the direct-rollover permission and verify the new calculation refuses while the accepted history remains available. Repeat with the opposite death direction for native coverage.
