# R3-C · Versioned Roth scheduling execution

## Executive summary and baseline

**IMPLEMENTED_AND_TESTED.** Enabled version-1 schedules execute only through the explicit normal Florida Basic projection route. Annual windows are inclusive, and One Time retains equal calendar-year boundaries. Continue, Skip and Permanent use the existing conversion allocator and preserve R2 mandatory transaction ordering.

Local `main` and the `origin/main` tracking reference were at `43f4dda9dd544bba58df5e95d401063291ebe645`. Accepted R2 and R3-B code and tests remained unstaged, along with predecessor reports and the prior R2a3-B report edit. Those changes were preserved. R3-B's accepted bounded baseline was 5,780 passes, zero failures and errors, and 35 skips.

## Federal rule and admission

A required minimum distribution is excluded from an eligible rollover or conversion. Satisfying that mandatory amount leaves otherwise eligible additional principal potentially available in the same year. Beneficiary and actual own-IRA treatment require distinct account rights. A qualifying direct pretax employer-plan rollover into a traditional IRA remains separate from a taxable Roth conversion. Apply the final RMD regulations and preserve the supported R2 beneficiary method. [IRC §401](https://uscode.house.gov/us/usc/119-124/t26/s401/) · [IRC §402](https://uscode.house.gov/us/usc/119-124/t26/s402/) · [IRC §408](https://uscode.house.gov/us/usc/119-124/t26/s408/) · [IRC §408A](https://uscode.house.gov/us/usc/119-124/t26/s408A/) · [26 CFR §1.408A-4](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-408A-4.html) · [IRS Publication 590-A](https://www.irs.gov/publications/p590a) · [IRS Publication 590-B](https://www.irs.gov/publications/p590b) · [Treasury Decision 10001](https://www.irs.gov/irb/2024-33_IRB)

**UNSUPPORTED_OR_REFUSED.** Other states, Advanced, financial analyzer routes, and contextual or stochastic projection entry points refuse enabled version-1 requests. A modeled death within the horizon requires the applicable certified R2 route. R2 input admission remains authoritative within that route. The UI checks route eligibility and final execution independently verifies R2 facts and mandatory funding. A disabled versioned request remains usable as stored configuration. The last accepted projection is preserved after an unsupported execution attempt.

## Annual execution and legacy preservation

**IMPLEMENTED_AND_TESTED.** Dispatch happens at the existing scheduler boundary after the R2b-D opening employer payout/rollover, growth, regular mandatory allocations and ordinary spending. Version 1 computes the current flag with the strict positive sign of the full annual mandatory amount plus the separate opening January mandatory amount. Fully paid opening RMD evidence counts even when projected RMD cash is zero. No age trigger or rollover receipt is counted. New Permanent state is run-local and advances only after a completed year. Imports from prior unmodeled years do not seed that state. Medicare retries use the same accepted prior state.

| Policy | Annual decision |
| --- | --- |
| Continue Through End Year | Admit otherwise eligible years even when the current mandatory total is positive |
| Skip Years With RMDs | Block the positive year and allow later zero-obligation years within the window |
| Permanently Stop at First RMD | Block the first positive year and every later year, including when the first event precedes the start |

**EXISTING_BEHAVIOR_PRESERVED.** Legacy requests use their original scheduler and serialized fields. Legacy FIRST_HOUSEHOLD_RMD remains current-year-only on normal deterministic routes with prior-year memory in lifetime contexts. NEVER and null preserve existing behavior. Existing R2 rollover, RMD, R2a4 ordinary funding, tax and Medicare calculation paths remain intact. Accepted inputs preserve their original fingerprints and legacy serialization order.

The versioned route intersects the existing conversion owner set with living original owners. Same-owner Roth destinations, employer availability, account priority, eligible account types, fixed-dollar capacity and bracket strategy use the existing components. R2b-D amounts remain differently attributed across the employer minimum, tax-free transfer and any later conversion of the survivor's original assets. Effective successor sources and inherited beneficiary positions remain outside R3 authority. R4 will require its own explicit source change.

## Independent controls and financial coverage

| Synthetic control | Independent expected value |
| --- | --- |
| Fixed conversion after mandatory payout | Age 75, $246,000 ÷ 24.6 = $10,000. Convert $20,000. Traditional source closes at $216,000 and Roth receives $20,000. AGI from these two events is $30,000. |
| Presatisfied opening | $10,000 annual requirement, $0 in-period RMD cash. Continue executes $20,000. Skip and Permanent execute zero. |
| Positive-to-zero with event before window | $24,600 ÷ 24.6 = $1,000. $22,165.20 ordinary expense and $2,434.80 premium consume the remainder that year. The original source closes at zero and the next RMD is zero. Skip and Continue permit a $20,000 draw from the younger eligible owner's source. Permanent stays stopped. |
| 12% bracket | Under frozen 2026 Single values, $30,000 pension plus $10,000 RMD less $16,100 deduction gives $23,900 taxable income. $50,400 less $23,900 = $26,500 conversion. Higher baseline taxable income yields zero room. |
| R2b-D original owner source | $35,609.76 employer beneficiary minimum plus $9,887.01 separate owner IRA minimum gives $45,496.77. After a $20,000 conversion, the original survivor IRA is $155,000. The rolled occurrence remains $392,503.23 with its own RMD debit and supplies no conversion. |
| Delayed spouse beneficiary | Primary born 1963 and spouse 1976. Spouse dies in 2030. Seven $200,000 fixed conversions and the final capped $100,000 exhaust the original primary $1.5 million by 2037. Cash funds related spending and taxes. Primary age 75 in 2038 has zero RMD. The inherited $660,000 gives zero through 2050, then $660,000 ÷ 6.6 = $100,000 in 2051. That position supplies no conversion at any time. |

The fixed and targeted calculations retain the established tax funding and Medicare pipeline. Existing legacy financial and R2 fixtures remain in focused and bounded regression. Targeted scheduler tests cover inclusive boundaries, Skip/resume and Permanent memory independently of the bracket solver.

## Two editors and frozen disclosure

**IMPLEMENTED_AND_TESTED.** Both existing surfaces share the versioned adoption, End Year and When RMDs Begin controls. Legacy adoption is deliberate and reversible before Apply. The actual new-plan draft initializes the pending window and Continue selection in supported mode, with conversions disabled until enabled and applied. The frequency defaults to Annual for that new-plan preference. One Time requires end equal to start. Disabled accepted versioned values round-trip and can be reenabled in supported mode. The common transaction and same-owner destination confirmation remain. The summary card includes Cancel Roth Edits, and the dedicated view uses its existing Cancel. Draft fields are tracked along with legacy controls. Applied changes use the existing accepted-input fingerprint.

The accepted Results Summary uses frozen scheduling disclosure. PDF includes the frozen window, policy, frequency, existing strategy and actual conversion reporting, plus the custom taxable-income target when applicable. Legacy null-stop PDF rendering retains its safe label.

## Historical certification

**IMPLEMENTED_AND_TESTED.** Keep the retained annual schema and certified inheritance route. An orthogonal `FL-BASIC-ROTH-SCHEDULING-V1` provenance identity identifies the specific RMD policy and binds frozen inputs and annual disclosure. The complete snapshot validates identity, then independently executes the exact normal route from frozen inputs and compares the retained annual graph, including payouts, source occurrences, taxable attribution, requested and actual conversion, taxes, premiums, and closing balances. Opening evidence and prior raw RMD event history are recomputed during replay. Older schemas 1–11 retain their existing predicates and replay branches without an R3 stamp.

A changed end year with the provenance binding rebuilt is rejected when the recalculated year differs. The input binding alone does not replace numeric comparison. Existing Save/Open/Swap and detached PDF workflows use the same retained result model. The tested R2b-D combination retains its original inherited retirement identity and the independent Roth identity.

## R3-C changed-file inventory

Production paths relative to `src/main/java/com/daviddunn/retirementplanner`.

- `app/export/ProjectionPdfExporter.java`
- `app/projection/CompleteProjectionSnapshot.java`
- `domain/benefit/ConditionalBenefitAdmission.java`
- `domain/projection/ProjectionEngine.java`
- `domain/projection/ProjectionYearCalculation.java`
- `domain/roth/RothConversionRequest.java`
- `domain/roth/ScheduledRothConversionPolicy.java`
- `ui/controller/ApplicationController.java`
- `ui/controls/PendingRothSchedulingView.java`
- `ui/views/ResultsSummaryView.java`
- `ui/views/RothConversionView.java`

Tests relative to `src/test/java/com/daviddunn/retirementplanner`.

- `domain/roth/RothSchedulingConfigurationTest.java`
- `domain/roth/RothSchedulingExecutionTest.java`

- `persistence/ProjectionSnapshotPersistenceTest.java`
- `ui/charts/ProjectionPdfExportTest.java`

This report is the only added documentation for R3-C.

## Verification

**IMPLEMENTED_AND_TESTED.** IntelliJ bundled Maven, Java 25, and the repository-local Codex Maven cache were used. Counts below are per invocation and overlap.

| Maven invocation | Passed | Failures | Errors | Skips | Exit code |
| --- | ---: | ---: | ---: | ---: | ---: |
| Focused policy, tax, R2, replay, persistence and editor regression | 460 | 0 | 0 | 0 | 0 |
| Final focused admission, reference fixture and UI regression | 46 | 0 | 0 | 0 | 0 |
| Bounded regression with the established selector and exclusions | 5,792 | 0 | 0 | 35 | 0 |
| Supplemental versioned Save/Open/Swap and frozen PDF | 2 | 0 | 0 | 0 | 0 |

The bounded run added 12 passes to the accepted R3-B baseline. The skipped classes and counts matched the prior bounded run. Both Stage 5D cancellation cases passed. The two supplemental tests were added after bounded test compilation and ran separately against unchanged production code. The logs are in `target/r3c-verified-focused.log`, `target/r3c-admission-focused.log`, `target/r3c-broad.log`, and `target/r3c-lifecycle.log`.

Both `git diff --check` and `git diff --cached --check` exited 0. The index remained empty.

## Native acceptance and next phase

1. Create a synthetic Florida Basic plan with eligible original living traditional and Roth accounts. Verify the disabled Continue default, start and end values and both editors. Enable and apply a fixed request. Save, reopen, compare a baseline, swap and inspect the frozen PDF.
2. Reproduce the age-75 and exhausted-account controls above. Compare Continue, Skip and Permanent over the same modeled years. Change the start to after the positive obligation and confirm only Permanent remembers that earlier year.
3. Open a legacy file, edit an unrelated field and verify the original policy and history. Explicitly select the versioned schedule, cancel once, then apply. Disable and reenable to check stored fields. Repeat Light/Dark and keyboard verification.
4. Switch a synthetic enabled version-1 plan to an unsupported state or Advanced, or request a financial analyzer run. Confirm typed refusal and preservation of the last accepted result.

**DESIGNED_NOT_IMPLEMENTED.** Additional integration and native acceptance work belongs to R3-D. Successor conversion sources belong to R4.

**BLOCKED_PENDING_APPROVAL.** R3-D awaits native Windows acceptance of this delivery and explicit authorization.
