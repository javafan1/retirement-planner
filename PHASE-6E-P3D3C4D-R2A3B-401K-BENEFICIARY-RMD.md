# Phase 6E-P3D3C4D-R2a3-B — Florida spousal beneficiary 401(k) RMDs

## 1. Executive summary

**IMPLEMENTED_AND_TESTED.** Florida Basic deterministic projections now calculate and pay qualifying traditional 401(k) beneficiary RMDs under **Spouse as Beneficiary**. Each inherited employer plan has a separate obligation and pays it from its original projected position. The survivor receives one fully taxable receipt per positive plan debit. Beneficiary IRAs, the survivor's own IRAs and the survivor's workplace plans keep their separate obligations. The inherited 401(k) remains invested in its existing position during deferral, with no new rollover or transfer.

## 2. Git and workspace baseline

Work began on `main` at `2bedb55e99fb48aa31225e9c5549c842d77ebb2d`, zero ahead or behind the local upstream reference. There were 54 tracked modifications and 52 untracked files, and the index was empty. Hashes checked against the opening worktree show all 106 paths present, with 88 byte-identical and 18 changed at the scoped source and test sites below. Existing Arkansas and survivor changes remain in place. All runs used synthetic inputs. Build logs, selectors, and probe transcripts are in ignored `target/phase6e-p3d3c4d-r2a3b/`. The user reported R2a3-A native acceptance. This phase used automated Windows and JavaFX tests, with separate interactive native acceptance still outstanding.

## 3. Exact modified files
exio
All Java paths below are relative to `src/main/java/com/daviddunn/retirementplanner/` or `src/test/java/com/daviddunn/retirementplanner/`.

| Area | Files and sites |
|---|---|
| Ownership | `domain/retirementownership/RetirementOwnershipDisposition.java` adds a beneficiary employer treatment. `RetirementOwnershipResolver.java` resolves cleared original 401(k) sources in `resolveAccount`. `SpousalInheritanceDisposition.java` classifies the resolved employer plan as the supported modeling assumption. |
| Obligation and membership | New `domain/rmd/Beneficiary401kRmd.java` validates a source-specific year, recipient, valuation basis, divisor and amount. `SpousalIraRmdMembership.java` indexes binding and authorizes the actual debit. `HouseholdRmdCalculator.java` passes the verified obligations into `HouseholdRmdResult.java`, which includes their amounts in the existing annual sum. |
| Projection and payment | `domain/projection/SpousalIraRmdPolicy.java` admits the route and validates chronology and employer facts. `ProjectionEngine.java` adds the request, execution and historical ownership entry points, and passes the evidence into the existing `ProjectionYear.java` row. `ProjectedWithdrawalAllocator.java` reuses the exact-position RMD debit in `applyHouseholdRmds`. `domain/income/IncomeSourceAttribution.java` authorizes the survivor for the existing debit row. |
| History and workflow | `app/socialsecurity/results/RetainedProjectionSnapshot.java` adds the schema 7 obligation and receipt marker, capture and restore. `app/projection/CompleteProjectionSnapshot.java` certifies the frozen graph and checks current-policy compatibility. `app/projection/SpousalInheritancePlanningEvidence.java` binds and discloses the applied scope. `ui/controller/ApplicationController.java` selects the qualifying route, captures the new schema and retains the existing publication boundary. |
| Tests | New `domain/projection/SpousalBeneficiary401kRmdExecutionTest.java`. Extended `domain/projection/RetainedProjectionCodecTest.java`, `persistence/ProjectionSnapshotPersistenceTest.java` and `ui/charts/ProjectionPdfExportTest.java`. The earlier last-success test now uses an unsupported 403(b) where it previously used a 401(k). Existing financial expectations stay intact. |

This report is the only new documentation file.

## 4. Reused architecture

The R1/R2a2 ownership resolver, R2a3-A annual beneficiary membership and Table I resource, projected balances, household RMD calculator, exact-account allocator, annual source registry and retained snapshot remain the execution path. There is one financial engine and one record of each actual distribution. The supporting annual obligation is evidence of what each position owes. It does not create income. The predecessor contracts were checked against their implementation and regression suites. [Minimal-change audit](PHASE-6E-P3D3C4D0R-RMD-ROTH-MINIMAL-CHANGE-AUDIT.md) · [R1](PHASE-6E-P3D3C4D-R1-SURVIVOR-OWNERSHIP-FOUNDATION.md) · [R2a1](PHASE-6E-P3D3C4D-R2A1-UNIFIED-SPOUSAL-INHERITANCE.md) · [Attribution reuse](PHASE-6E-P3D3C4D-R2A2A1-ATTRIBUTION-REUSE.md) · [Owner IRA](PHASE-6E-P3D3C4D-R2A2A2-OWNER-IRA-RMD-EXECUTION.md) · [Owner 401(k)](PHASE-6E-P3D3C4D-R2A2B-401K-SPOUSAL-ROLLOVER.md) · [Beneficiary IRA](PHASE-6E-P3D3C4D-R2A3A-SPOUSE-BENEFICIARY-IRA-RMD.md)

## 5. Verified Federal legal requirements

- The employer RBD normally falls on April 1 after the later of the applicable age year and retirement year. The 5% owner and permitted age-based plan branches can remove the retirement delay. V1 conservatively requires death before the earliest possible age-based distribution calendar year, so it is also before the corresponding April 1 RBD. Age 75 is supported for births from 1960 onward. The method neither assumes a still-working extension nor starts the participant obligation from the April payment deadline. Positive opening paid amounts or conflicting years refuse. The at-or-after-RBD branch generally requires a death-year payment to the extent unpaid and can have a different continuing denominator. [26 U.S.C. §401(a)(9)](https://uscode.house.gov/us/usc/119-124/t26/s401/) · [26 CFR §1.401(a)(9)-2](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-2.html) · [26 CFR §1.401(a)(9)-5](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-5.html)
- A sole designated spouse is an eligible designated beneficiary. A covered pre-RBD defined contribution plan can use life-expectancy payments, or its terms can specify the 10-year method or permit a choice. Sole-spouse deferral and annually redetermined life expectancy apply under the governing final rules for the selected life-expectancy branch. [26 CFR §1.401(a)(9)-3](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-3.html) · [26 CFR §1.401(a)(9)-4](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-4.html) · [26 CFR §1.401(a)(9)-5](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-5.html)
- T.D. 10001 applies to RMD calendar years from January 1, 2025. Section 401(a)(9)(B)(iv) and the final §1.401(a)(9)-5(g)(3)(i) provide for spouse-as-employee election treatment. The 2024 proposal would deem the election made for specified pre-RBD sole-spouse life-expectancy cases and use the Uniform Lifetime Table if the election applies. That proposal is not the currently applicable final rule. Announcement 2026-7 anticipates application no earlier than the distribution calendar year beginning six months after publication of final rules and calls for a reasonable good-faith interpretation in the interim. IRS Notice 2026-13 likewise describes life-expectancy distributions where the spouse does not elect. V1 uses the disclosed no-election plan branch of the current final rules. It holds that version constant for future projection years and requires review if a later applicable rule changes that branch. [T.D. 10001 and REG-103529-23](https://www.irs.gov/irb/2024-33_IRB) · [IRS regulatory status](https://www.irs.gov/retirement-plans/treasury-regulations) · [Announcement 2026-7](https://www.irs.gov/pub/irs-drop/a-26-07.pdf) · [IRS Notice 2026-13](https://www.irs.gov/irb/2026-06_IRB)
- Section 1.402(c)-2 excludes required amounts from eligible rollovers and provides separate catch-up treatment for certain spousal rollovers. This plan remains in beneficiary status under the life-expectancy method, so neither a rollover nor a catch-up rollover calculation arises. [26 CFR §1.402(c)-2](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-402c-2.html)

## 6. Employer-plan modeling assumptions

The Florida Basic model assumes sole direct spouse beneficiary status, permission to take the supported life-expectancy payout and use the permitted spousal deferral, a fully pretax traditional balance, and an original modeled position representing the whole applicable benefit in a distinct plan. There are no outstanding plan loans, insurance or annuity slices, designated Roth components, after-tax contributions, or beneficiary or plan elections inconsistent with this method. Dated employer facts that fall after death and incompatible opening mandatory history refuse. Actual plan terms may require an earlier or different payout. Plan documents are outside the input contract. [IRS 401(k) plan distribution rules](https://www.irs.gov/retirement-plans/plan-sponsor/401k-resource-guide-plan-sponsors-general-distribution-rules)

## 7. Supported distribution method

**IMPLEMENTED_AND_TESTED.** The qualifying spousal life-expectancy method V1 is selected only for the frozen **Spouse as Beneficiary** preference, Florida Basic ordinary deterministic, two modeled spouses with supported birth dates and a single first death from 2025 onward, followed by at least one modeled survivor year. It retains a qualifying traditional 401(k) in its original employer plan. A ten-year payout, a spouse-as-employee election, a later rollover and a second death have separate legal treatments and remain outside this policy.

## 8. First beneficiary distribution year

The first beneficiary year is the later of the year following the modeled death and the year the participant would reach the applicable age. The beneficiary payment is due by December 31 of the first distribution year. The owner first-payment April 1 rule remains separate. A supported post-death year before beneficiary commencement carries the source's verified balance, a zero obligation and an absent divisor. It produces no beneficiary receipt. [26 CFR §1.401(a)(9)-3(c)–(d)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-3.html) · [26 CFR §1.401(a)(9)-5(a)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-5.html)

## 9. Correct beneficiary divisor

Section 1.401(a)(9)-5(d)(2) and (d)(3)(iv) use the sole spouse's birthday age each distribution year under this method. The shared, versioned `rmd-single-life-v1.txt` is Table I under §1.401(a)(9)-9(b). Each payable amount divides the verified plan balance by that year's divisor and applies existing cent rounding. The model assumes its previous December 31 snapshot is the applicable plan valuation basis with no additional adjustments. A plan using a different valuation fact pattern or requiring post-valuation adjustments is outside V1. The regulation uses the last valuation date in the preceding calendar year with applicable adjustments. [26 CFR §1.401(a)(9)-5(b), (d)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-5.html) · [26 CFR §1.401(a)(9)-9(b)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-9.html)

## 10. Separate employer obligations

One original employer position represents one complete modeled plan. Each has its own prior balance, requirement and debit even if the survivor and life-expectancy divisor are shared. A different plan, an IRA, or the survivor's own workplace account cannot satisfy that requirement. The regulation aggregates subaccounts inside a single plan, so plans split across independent modeled accounts or holding mixed unsupported account components are outside V1. No grouping is inferred from name or employer labels. [26 CFR §1.401(a)(9)-5(b)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-5.html) · [IRS RMD FAQs](https://www.irs.gov/retirement-plans/retirement-plan-and-ira-required-minimum-distributions-faqs) · [IRS Form 5329 instructions](https://www.irs.gov/instructions/i5329)

## 11. Original account identity

The frozen source reference identifies the original occurrence, participant facts, original owner and traditional 401(k) category. Existing investments and projected positions remain in the portfolio. The beneficiary disposition has a survivor recipient and no effective owner-IRA grouping. It carries neither a receiving IRA nor a rollover.

## 12. Actual RMD debit reconciliation

Annual membership binds one original position and one prior balance to each employer obligation. The same obligation is consumed by sizing and checked by identity at the debit boundary. The existing allocator debits the exact account and derives the income row from the actual before/after balance difference. A plan with $14,999 available against $15,000 required raises the existing `ACCOUNT_RMD` funding constraint even when a separate plan has $82,000. Missing and duplicate prior bindings are `MISSING_OR_DUPLICATE_SOURCE`; an explicit zero balance is supported.

## 13. Survivor tax attribution

`IncomeSourceAttribution.forBeneficiary401kRmd` authorizes only a positive, fully taxable actual RMD debit whose source occurrence, participant facts, recipient, year and amount agree with the verified original plan obligation. The single receipt preserves the decedent as original owner and recognizes the surviving spouse as recipient. Ordinary attribution keeps its owner-equality requirement.

## 14. Federal tax and Medicare effects

The $246,000 fixture produces exactly $15,000 of employer RMD and cash. With single 2026.1 tax rules at zero inflation and $24,000 each of annual Social Security and pension, provisional income is $51,000 and taxable Social Security is $18,950. AGI and Medicare MAGI are $57,950. Federal tax is $4,774, annual Medicare is $2,434.80 and tax funding is $3,408.80. The original plan closes at $231,000, retained assets at $49,286.80 and after-tax estate at $414,786.80. The heir-tax rate and premium mechanics are those already configured by the synthetic fixture. Medicare repricing sees the same obligation, and inheritance creates no cash or taxable amount by itself.

## 15. Existing withdrawal limitations

**UNSUPPORTED_OR_REFUSED.** Actual beneficiary RMD cash can fund expenses or taxes. Additional positive discretionary draws from a successor retirement position still refuse at the existing spending and tax-funding source boundaries. The plan may keep investments economically available without treating every funding use as financially supported. Refusal retains the last accepted result.

## 16. Roth conversion safeguards

RMDs execute first. A retained employer beneficiary position cannot supply fixed or targeted Roth conversions. The existing deceased-original-owner exclusion applies to both search pricing and execution. The survivor's own eligible sources remain available under existing rules. The tests exercise the existing first-household-RMD stop and verify $1,000 fixed and $51,500 target conversions from living-original-owner assets when that stop is disabled.

## 17. Owner-mode nonregression

**EXISTING_BEHAVIOR_PRESERVED.** The owner IRA and owner rollover fixtures retain their original distinct applied identities, rollover-year treatment, debit validations and timing refusals. A beneficiary 401(k) never enters an owner IRA group through this route. Earlier owner snapshots continue using the original respective engines on historical replay.

## 18. IRA beneficiary nonregression

**EXISTING_BEHAVIOR_PRESERVED.** Schema 6 retains its own capture and replay identity and omits the new field. Its commencement, separate prior balance, rounding and allocation are unchanged. In a mixed result schema 7 retains the separate IRA evidence and the plan-by-plan employer obligations. The two classes do not pay each other's obligation.

## 19. Applied historical policy

`FL-BASIC-SPOUSE-BENEFICIARY-401K-RMD-V1` uses retained schema 7 with `MODELED_SPOUSAL_BENEFICIARY_401K_SINGLE_LIFE_V1` on positive employer receipts. It binds the frozen selected policy and input fingerprint to the entire retained annual projection. The new immutable obligation records the source occurrence, recipient, obligation year, first distribution year, prior balance, divisor and amount. Frozen household inputs identify the death and birth dates. The existing rows hold the actual debit, taxable amount, cash and closing investment evidence. The new optional field is omitted in schemas 1 through 6. Certification replays the compatible bundled 2026.1 government rules and the V1 Table I resource. A future rule data update that changes replay results requires retention of a compatible historical version before publication.

## 20. Save, Open, Swap and PDF validation

Save, Save As and Open preserved the applied UUID and exact annual evidence. Current and Baseline were swapped in both directions between mixed schema 7 and IRA-only schema 6. The schema 7 replay rejects a forged prior balance and obligation, changed receipt amount, payee, year, source, duplicated receipt, changed closing balance, annual RMD total and AGI even after recomputing the provenance. Existing component checks remain in use. Deferred history also restores without fabricating a receipt. Results Summary and PDF read the frozen disclosure, including the employer-plan limitation. A live edit to the selection did not relabel the accepted result.

## 21. Older-survivor financial oracle

The survivor was born in 1960. The 1970-born participant dies January 1, 2035, before the earliest possible first distribution year of 2045 and RBD of April 1, 2046. The original employer plan has $500,000 at zero growth. The survivor independently pays $10,000 own-IRA RMD in 2035 and $9,957.81 in 2036. The employer obligation is zero during the permitted deferral. At age 85 in 2045, $500,000 ÷ 8.1 = **$61,728.40**, leaving $438,271.60. In 2046, age 86, $438,271.60 ÷ 7.6 = **$57,667.32**. The original spouse's plan is debited for the primary survivor in both years.

## 22. Younger-survivor financial oracle

The 1960-born participant dies January 1, 2034. The applicable age year is 2035 and the earliest RBD is April 1, 2036. The survivor was born in 1962 and turns 73 in the first beneficiary year of 2035. $500,000 ÷ 16.4 = **$30,487.80**, paid from the original primary 401(k) to the spouse. The plan closes at $469,512.20 and the survivor's own IRA remains $123,000. In 2036 the divisor is 15.6 and the independently calculated payment is **$30,096.94**. There is no death-year owner RMD under the admitted chronology.

## 23. Multiple employer account tests

Two deceased-employer balances of $164,000 and $82,000 require individual $10,000 and $5,000 debits at divisor 16.4, leaving $154,000 and $77,000. A retained IRA with $32,800 pays its own $2,000, giving $17,000 household RMD. Reversed source order keeps those same per-plan totals. Separate living-owner IRA and employer debits of $5,000 and $1,000 execute at the survivor's applicable age. The one-plan shortfall case verifies no substitution from the other plan.

## 24. Unsupported scenarios

**UNSUPPORTED_OR_REFUSED.** The route requires one in-scope spouse death and a supported cohort, table, opening history, account class, funding and source graph. A death in the earliest first distribution year refuses before calculation. Contradictory participant dates, unsupported source types, inherited 403(b)s and positive preconfigured inherited-account obligations refuse. The frozen input parser rejects explicit unsupported fields for alternate, trust or nonspouse beneficiary status, ten-year method, spouse-as-employee election, basis, loan, designated Roth balances, plan payout restrictions or unsupported shared-plan grouping. Unmodeled facts cannot be confirmed by this application. Michigan, Arkansas and Advanced requests remain outside this applied route, as do Monte Carlo, lifetime/weighted, paired and integrated financial analyses. Existing routes that show planning-only disclosure remain available. Cancellation does not replace the accepted result.

## 25. Legacy regression results

**EXISTING_BEHAVIOR_PRESERVED.** The unchanged-route transcript compares 11 five-year financial snapshots and 11 characterization variants against the R2a3-A baseline, including balances, tax, RMD, Roth, Medicare, cash, estate and retained identity. The SHA-256 stayed `05BC3D18C9975B6BBFE8D3157BC066F2C0803362108F105922FB4B8F1FC1F656`. The eight raw legacy-engine pending-selection controls stayed `22A7E84046D07633A09F899EDF8D03BC28AA88919305731EECD5C871A57DBCCC`. Both probes exited 0. Living-owner employer RMD, Florida legacy, both owner treatments, IRA beneficiary, Michigan and Arkansas proofs passed their existing focused assertions. These comparisons exercise unchanged routes. The new applied route has separate numerical oracles above.

## 26. Performance and cancellation

The existing engine constructs the immutable membership once per applicable projected year and reuses it through calculation, allocation and Medicare repricing. Reference indexes bind plan sources and prior balances with bounded passes. Positive employer payments use the existing immutable portfolio debit operation. Historical replay deliberately performs the existing complete certification of frozen inputs before publication or restoration. Existing cancellation checks and the last-success publication boundary remain in force. No Arkansas search budgets changed.

## 27. Exact focused test results

Java Temurin 25.0.4.1 and IntelliJ bundled Maven ran offline in batch mode using the workspace-local dependency cache. The selector is archived in `target/phase6e-p3d3c4d-r2a3b/focused-tests.txt`. It covers ownership, RMD, opening balances, attribution, withdrawal, state and federal tax, Medicare, normal and targeted Roth, Arkansas proof, historical payloads, JavaFX summary/PDF, last-success and workflow. The excluded exhaustive Monte Carlo, longevity searches and benchmarks were not run.

| Run | Passed | Failed | Errors | Skipped | Maven exit |
|---|---:|---:|---:|---:|---:|
| Opening baseline | 613 | 0 | 0 | 2 | 0 |
| Production and test compilation before new tests | — | 0 | 0 | — | 0 |
| First new-test compilation | — | 0 | 6 compiler diagnostics | — | 1 |
| First executed targeted pass | 119 | 1 | 3 | 0 | 1 |
| Expanded focused pass | 624 | 0 | 0 | 2 | 0 |
| Final focused verification | 626 | 0 | 0 | 2 | 0 |

The intermediate failures were missing test imports, scale-sensitive comparisons in a new assertion, and the new 401(k) marker missing from the retained income constructor's allowlist. These were corrected without changing a financial golden. The two skips are the established opt-in funding and new-plan wizard preview tests. `git diff --check` and `git diff --cached --check` exited 0. Nothing was staged, committed or pushed.

## 28. Florida completion roadmap

**DESIGNED_NOT_IMPLEMENTED.** R2a4 can use the existing withdrawal allocator and actual-debit receipt authorization. For each positive ordinary or tax-funding debit, it needs an independently admitted successor payout class, an original reference, the resolved recipient, the amount actually withdrawn and precise taxable character. Preserve account order and the payer-specific mandatory debt cleared first. Accept the existing own-IRA recipient for owner IRA and rolled-over owner 401(k), the beneficiary recipient for retained IRA, and retained employer distributions only when the selected plan model permits additional payouts. Extend the existing receipt's authorization to eligible purposes with separate frozen execution identity and per-pass funding/history reconciliation. No second ledger is needed. R3 remains separately gated and covers configured continuation, positive-RMD-year skipping and legacy first-RMD stopping. R4 successor conversions and R5 additional states and routes require their own admission.

## 29. Remaining legal and modeling limitations

A real employer plan may mandate a shorter payout, prohibit life-expectancy distributions, use a different valuation basis or have account components requiring adjustments. V1 models a whole pretax plan position, compatible plan terms and currently effective no-election rules. Known alternative elections and later legal changes require new certification. Previously paid mandatory benefits, post-RBD death, alternate recipients, rollover treatment under Beneficiary, and second-death branches are outside scope. A plan with no modeled survivor year stays pending. Historic accepted results remain readable under their respective policy and compatible rules.

## 30. Decisions requiring user approval

**BLOCKED_PENDING_APPROVAL for follow-on scope.** R2a4 execution requires explicit approval, including the retained employer payout assumption for discretionary payments. Any spouse-as-employee or ten-year method, post-RBD or death-year treatment, new Roth policy and successor conversions, state expansion, and analyzer expansion require separate decisions. Automated verification for R2a3-B is recorded above. Native acceptance for this phase has not been reported.
