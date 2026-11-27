# R3-D · Roth scheduling integration and acceptance

## Scope and starting Git state

**VERIFIED_AND_TESTED.** This phase verifies the accepted Florida Basic normal deterministic version-1 scheduling feature. R3-C's accepted bounded baseline was 5,792 passes, zero failures or errors, and 35 skips. Two subsequent lifecycle tests also passed separately in R3-C. The same bounded selector includes their classes in R3-D.

**EXISTING_BEHAVIOR_PRESERVED.** Local `main` and its `origin/main` tracking ref were at `43f4dda9dd544bba58df5e95d401063291ebe645`. The index was empty. Existing R2 and R3 source, test, and report changes were present, including the earlier R2a3-B report edit. This phase adds tests and this report.

## Admission and household policy

**VERIFIED_AND_TESTED.** The normal route admits enabled version-1 schedules for certified Florida Basic inputs. The Michigan refusal control checks the typed exception, unchanged request and unchanged accepted result. The legacy projection entry point and legacy scheduler also reject enabled version-1 schedules. Code review confirms the explicit normal entry point, the contextual projection guard, and the separate conditional-benefit analyzer preflight. Disabled metadata can round-trip without enabling conversions.

**UNSUPPORTED_OR_REFUSED.** Other states, Advanced/NIIT, stochastic and lifetime contexts, unsupported financial analyzers, and noncertified inheritance paths remain outside version-1 execution. Coverage combines direct refusal tests and existing route regressions with inspection of the shared gates.

| Control | Independent expected result and verification |
| --- | --- |
| Continue after RMD | $246,000 divided by 24.6 yields a $10,000 minimum. A separate $20,000 conversion leaves $216,000 in the original IRA, credits $20,000 to its same-owner Roth, and contributes $30,000 total to AGI. |
| Skip and resume | $24,600 divided by 24.6 yields $1,000. The remaining source is consumed through $22,165.20 in spending and $2,434.80 in Medicare premiums. The next year has zero RMD, and the younger owner's eligible IRA can supply $20,000. |
| Permanent | The same modeled positive obligation precedes the window. Permanent suppresses the next year's otherwise eligible draw even with zero current RMD. |
| Presatisfied opening | A $10,000 opening mandatory requirement with zero projected mandatory cash still blocks Skip and Permanent. Continue can convert $20,000. |
| Receiving IRA and rollover | Existing owner and January employer beneficiary minima are $9,887.01 and $35,609.76. The ledger carries $45,496.77 in household RMD. The $20,000 conversion comes from the survivor's independently eligible original IRA. The rolled original position closes at $392,503.23. |
| 12% bracket | $30,000 pension plus $10,000 RMD less $16,100 standard deduction leaves $23,900 taxable income. The $50,400 bracket ceiling leaves $26,500 for conversion under controlled funding. A larger pension leaves zero room. |

**VERIFIED_AND_TESTED.** Seven new execution cases intersect stored windows with the 2026–2028 horizon. They cover a start before opening, an end after closing, equal-year Annual and One Time requests, and disjoint past and future windows. Exact annual conversion amounts prove the boundary years and preservation of stored inputs. An expired One Time request supplies zero in all modeled years. The existing policy control separately checks the current positive and later zero predicates.

The collection point follows opening employer mandatory payments and rollover, portfolio growth, regular RMD allocation and ordinary spending. The raw positive event combines the annual requirement and separate January mandatory amount. The completed annual calculation carries the event back to the run-local memory. Medicare retry uses the same accepted prior-year memory. Account allocation still requires a living original owner and a same-owner Roth destination. The existing funding, tax, Social Security and Medicare regressions exercise these financial components. The focused controls do not independently pin every policy to a forced IRMAA retry or an isolated receipt category.

## Spousal and exhausted accounts

**EXISTING_BEHAVIOR_PRESERVED.** The R2 focused classes exercise both death directions, older and younger survivors, first distribution years, and pre- and post-RBD chronology. They cover IRA and employer beneficiary treatment, owner succession, original position lineage, multiple sources, and ordinary withdrawals and tax funding. Versioned controls independently combine the R2b-D rollover-year minimum and living-owner conversion, and the delayed inherited IRA with a depleted living owner's IRA. The focused policy-specific controls are representative and do not cross every R2 permutation with all three policies.

The delayed beneficiary fixture uses births in 1963 and 1976 and a 2030 spouse death. Seven $200,000 conversions and a capped $100,000 conversion exhaust the original $1.5 million by 2037. Separate synthetic cash funds spending and taxes in this fixture. The age-75 owner's 2038 RMD is zero. The deferred account pays zero through 2050 and $660,000 divided by 6.6, or $100,000, in 2051. It supplies no conversion. This control uses conversion debits to deplete the original account. The separate Skip/Permanent oracle tests ordinary spending depletion.

**DEFERRED_TO_R4.** Beneficiary positions and effective survivor-owned successor positions remain outside the R3 conversion pool.

## Editors, defaults and lifecycle

**EXISTING_BEHAVIOR_PRESERVED.** Both Roth editors share the scheduling control and common apply transaction. Existing editor tests cover validation, availability and draft changes. The new-plan preference starts disabled with the projection start, original horizon end, Annual and Continue. Legacy wire-format coverage includes Never, First Household RMD and null, and explicit disabled requests retain their versioned configuration. Draft edits do not change the frozen financial input. Accepted schedule fields are fingerprint-significant.

**VERIFIED_AND_TESTED.** The versioned lifecycle fixture saves and opens the new result beside a legacy baseline, swaps in both directions, preserves result UUIDs, and restores the $20,000 accepted conversion. It exercises Save As and Save. The separate PDF fixture edits live scheduling values after acceptance and checks the frozen disclosed window. The existing PDF suite includes detached capture and the legacy nullable stop case. Existing Phase 12E association tests cover result-associated analyzer slots. Native keyboard and Light/Dark acceptance is listed separately below.

## Historical and failure safety

**VERIFIED_AND_TESTED.** Versioned results carry `FL-BASIC-ROTH-SCHEDULING-V1` alongside the existing inherited RMD identity. Complete replay restores the frozen plan, selects the certified route, captures the reproduced annual graph, and compares it against the retained graph. Schemas 1–11 and the prior identities retain their accepted dispatch. The new replay controls independently change each of the start year, end year and policy. They rebuild the input digest and applied provenance and require financial replay to reject the inconsistent annual graph. The R2 and codec suites exercise existing source and attribution tamper checks. A per-field R3 mutation matrix for every receipt and destination was not added.

The enabled version-1 refusal fixture preserves the previously accepted snapshot. Existing cancellation and persistence tests exercise interrupted analysis and accepted result retention. Run-local policy state is created anew in each projection, and the same-year event is evaluated during the existing retry pipeline. There is no dedicated native cancellation test in this phase for a PDF write or the precise point of result replacement.

## Changed files and verification

- Production files changed in R3-D — none.
- Test file — `src/test/java/com/daviddunn/retirementplanner/domain/roth/RothSchedulingExecutionTest.java`.
- Report — this file.

| Maven invocation | Passed | Failures | Errors | Skipped | Exit |
| --- | ---: | ---: | ---: | ---: | ---: |
| Focused Roth, R2, history, tax, Medicare, PDF and cancellation | 463 | 0 | 0 | 1 | 0 |
| Focused scheduling and replay oracles | 19 | 0 | 0 | 0 | 0 |
| Established bounded selector | 5,803 | 0 | 0 | 35 | 0 |
| Separate lifecycle, editor help, wizard and theme verification | 21 | 0 | 0 | 0 | 0 |

The bounded result adds nine R3-D cases and the two R3-C supplemental lifecycle cases to the accepted 5,792-pass baseline. The same 35 skips matched by class and count. The focused skip was the opt-in `DavidMedicareDeathAuditTest`. Both Stage 5D cancellation tests passed with their original assertions. The broad selector and exclusions remained unchanged. Counts overlap between invocations. Maven logs are `target/r3d-focused.log`, `target/r3d-oracles.log`, `target/r3d-broad.log`, and `target/r3d-lifecycle.log`.

Both Git whitespace checks exited 0. The index remained empty and HEAD remained at the starting checkpoint.

## Native Windows acceptance procedure

1. Through the wizard, create a synthetic Florida Basic plan with a known horizon, eligible original traditional accounts, and same-owner Roth destinations. In both editors, confirm conversions start disabled and that the pending Annual preference shows the first projection year, the original horizon end and Continue. Check keyboard focus, invalid-year display, enable/disable, One Time, all three bracket targets and the custom target in Light and Dark.
2. Use the age-75 and exhausted-account figures above to apply each policy. Check both editors against the same accepted input. Cancel a changed end year and verify that neither the plan nor its projection freshness changes. Apply it and confirm that refresh is required. Disable and reenable a saved versioned schedule and verify the exact selection survives.
3. Open a synthetic legacy plan, save an unrelated edit, and verify legacy behavior. Explicitly adopt version 1 and cancel once before applying. Save As, reopen, compare Current and Baseline, swap twice, and export both frozen results after changing the live schedule. Confirm the detached PDF reports the retained configuration.
4. With an accepted result available, attempt a Michigan, Advanced or financial-analyzer run with version 1 enabled. Check the typed refusal and the existing result association. Cancel a running supported projection and confirm that no partial result appears. If the export surface offers cancellation, verify that last accepted projection history remains intact.

**DEFERRED_TO_R3_E.** Michigan and other state authority remain gated. The Florida suite and explicit Michigan refusal provide a comparison baseline for a separate Michigan admission review.

**BLOCKED_PENDING_APPROVAL.** Native acceptance of R3-D and explicit approval are required before R3-E1.
