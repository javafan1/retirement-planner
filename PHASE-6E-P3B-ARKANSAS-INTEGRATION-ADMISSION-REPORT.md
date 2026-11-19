# Phase 6E-P3B — Arkansas Integration and Admission Verification

## 1. Executive summary

Arkansas runs through the real annual projection engine under a package-internal validation capability. The annual engine reproduced the $16,974 funding requirement and $1.34 retained surplus, carried the surplus into the next year, and reproduced the $8,949.09 deferred-funded and $16,500 cash-funded Roth controls. Bounded Monte Carlo and Social Security validations used the same engine and distinguished unavailable Arkansas results from actual asset shortfall.

**Recommendation: CONDITIONALLY_READY for the verified internal financial scope.** Admission still requires a production input policy for pension qualification and other missing evidence, an approved historical identity and end-user presentation path, and native Windows acceptance. Arkansas and Colorado remain disabled.

## 2. Repository checkpoint

Opening HEAD was `baa25930dd2a87c4bfa982acce728f65efcdb04b`, **Complete Arkansas tax funding and Roth conversion certification**. The checkpoint after P3A occurred and the working tree and index were clean at entry. The prior report's `6faf0b7` baseline is the checkpoint's parent. The M, N2, O, P1, P2 and P3A reports and the accepted tests remain part of the baseline.

## 3. Production baseline

Production jurisdiction entries remain AK, AL, AZ, FL, GA, IA, IL, KY, MI, MS, NC, NH, NV, OK, PA, SD, TN, TX, VA and WY. The enum, tax registrar, state selection, controller construction path and existing freshness registration are unchanged.

## 4. Official Arkansas recheck

Rechecked November 18, 2026. The indexed DFA official 2026 section lists AR1000ES estimated-payment vouchers. The AR1000F, instructions, tax tables and additional-credit worksheets on the same index are under tax year 2025. A direct fetch of the DFA forms index returned HTTP 403, so the official indexed content and separately published sources were used. The official special-session summary and enacted Act 2 still establish the 2026 schedules and 3.7% top rate. The legislative tax handbook supports the enacted regime. No final 2026 annual table, low-income schedule or annual rounding instruction, and no reviewed 2027 individual adjustment or superseding statutory interpretation, was found. The P1 statutory-cent planning convention, P2 proof bounds and point-of-use refusals are preserved. AR1000ES remains an estimated-payment comparison method. [DFA individual forms index](https://www.dfa.arkansas.gov/office/taxes/income-tax-administration/individual-income-tax/forms/) · [DFA 2025 archive](https://www.dfa.arkansas.gov/office/taxes/income-tax-administration/individual-income-tax/forms/2025-tax-forms/) · [Enacted Act 2](https://arkleg.state.ar.us/Home/FTPDocument?path=%2FACTS%2F2026S1%2FPublic%2FACT2.pdf) · [Legislative tax handbook](https://www.arkleg.state.ar.us/Home/FTPDocument?path=%2FAssembly%2F2027%2FSummary+Budget+Manuals%2F2026+Tax+Handbook.pdf)

The separately hosted estimated schedule shows the May 6, 2026 revision and its estimated-payment band amounts. The special-session summary identifies the January 2027 amendment as corporate. Those materials do not establish a 2027 individual parameter or an annual filed-return rounding method. [DFA revised 2026 AR1000ES](https://www.dfa.arkansas.gov/wp-content/uploads/2026_Final_AR1000ES_2.pdf) · [2026 special-session summary](https://arkleg.state.ar.us/Home/FTPDocument?path=%2Fassembly%2F2025%2F2026F%2FDocuments%2F2026+Fiscal+Session+Summary+Book.pdf)

## 5. Gated integration architecture

A package-private `ProjectionEngine` constructor requires the package-private `ArkansasProjectionValidation` capability. The only construction with this capability is the isolated test harness. The public constructors select the existing legacy branch. The explicit `AnnualIncomeTaxFacts` record carries annual income, filing status, household, federal calculation and projected rules with no jurisdiction member. The integrated engine uses it directly. The existing registered request may supply an annual-facts view for compatibility.

Arkansas validation years carry a separate strongly typed disclosure and annual scalar liabilities. They have no `ProductionStateTaxResult`, and a consumer requesting their registered jurisdiction or Michigan tax alias gets an explicit error. Metrics use the actual state tax and federal liability from the annual row. Tests and exporters determine validation identity from the dedicated result, independent of the persisted jurisdiction on the plan shell.

## 6. Full annual execution

The shared engine still projects opening balances, taxes' government-rule inputs, growth, authoritative Social Security and pension/conditional benefits, RMD, then ordinary spending. Arkansas uses actual active owners for RMD and conversion. After allocation, it invokes P2 fixed or P3A targeted execution, then carries the actual funding result into the standard Medicare calculation. If premiums are present, the normal engine restarts the year from the original beginning portfolio and repeats spending, conversion and funding on the final premium pass. Arkansas settles the passed candidate once. It then joins normal year-end account snapshots, estate calculation, next-year RMD snapshot and immutable annual result assembly.

Arkansas uses the exact allocation sum and exact retained deposit for ending assets. Legacy reported rounding and account behavior are preserved. The actual projection engine rounds aggregate growth and proportional retained growth to cents under its previous rules.

## 7. Source evidence adapter

`ArkansasProjectionValidation` combines executed spending/RMD sources, earlier same-year RMD, authoritative Social Security, pension selection, conditional receipts, and typed run-only evidence. It derives the observed annual receipt window from actual projection start through December 31 in the first year and January 1 through December 31 in following years. The later-year December 31 engine reporting date is not used as a fact that all transactions happened that day. Death facts are generated only from the effective household timing input. The adapter uses the actual post-spending portfolio and corroborated current recipient/account facts. Actual cash categories are reconciled to their sources before pricing.

Investment-income evidence can supply an explicitly modeled annual taxable amount and corroborating facts for validation. The current normal projection engine supplies zero in that argument. No interest, gain or taxable basis is invented from the asset return. The original Roth controls provide the same $50,000 of modeled net ordinary investment income and no outside tax cash. A future public source input for such receipts is separate from the gated harness.

## 8. Income and retirement qualification

P1 remains the calculation authority for Arkansas ordinary base, taxable Social Security exclusion, a single $6,000 retirement pool per actual eligible recipient, the senior election, modeled fully taxable traditional conversion, investment reconciliation and applicable source/timing boundaries. The test adapter can provide affirmative ordinary employer-pension qualification for a specific selected receipt. Saved pension inputs cannot establish the P1 Arkansas ordinary-plan fact on their own. Arkansas makes no inference from Alabama DB/DC, pension title or federal inclusion. An unresolved pension stops the annual calculation, and an evidenced direct spouse-survivor source retains original owner and current recipient separately.

Actual conditional and commercial annuity receipts enter once. Their qualified net investment income evidence and unrecovered basis use the existing federal rules. Traditional distributions remain taxable before Arkansas relief, earlier opening RMD enters the tax base without a current cash deposit, brokerage principal remains cash, and Roth cash uses P2's existing admissible modeled characterization.

## 9. Roth execution and source allocation

The scheduler uses the existing one-time or annual stop rules. Arkansas resolves a scheduled target using the same projected bracket or custom taxable-income ceiling. Fixed execution captures requested and actual principal, and both paths fund only from the converter's resulting portfolio. A missing destination and a $2,000 request against a $1,000 source produce distinct fixed conversion outcomes, with an executable tax-funded year where available. A proved target miss runs the established zero-conversion annual policy. An unsupported target aborts with its original detail.

| Original target control | Conversion | Tax-funding cash | Taxable funding | Federal tax | Arkansas tax |
| --- | ---: | ---: | ---: | ---: | ---: |
| Tax-deferred-first | $8,949.09 | $7,550.91 | $7,550.91 | $5,800.00 | $1,750.91 |
| Checking funded | $16,500.00 | $7,550.91 | $0.00 | $5,800.00 | $1,750.91 |

Both reach $50,400 exactly with the same $16,500 combined pretax addition. No P3A proof or owner logic changed.

## 10. Minimum-sufficient funding

Every candidate uses the P2 allocator-produced gross debit, source purposes, exact federal and Arkansas liabilities and executable post-conversion balances. The legacy fixed-point solver is bypassed only for the internal capability. On a proven small-domain shortage the engine prices the executable terminal amount to report exact required cash versus available amount, retaining the branch's proof of insufficiency. No failed source or proof is accepted as a dollar value or converted into insolvency.

## 11. Arkansas discontinuity regression

With the same 2026 Single age-60 attributed $86,197 IRA ordinary spending withdrawal, no other available cash and sufficient traditional IRA, the real annual engine returns $16,974.00 of funding and $16,972.66 combined liability. A separate actual plan whose remaining capacity is just $16,973.99 returns a structured funding shortfall. The full 29 adjustment bands and exit are also exercised through a cash-funded real annual pass, with independently calculated Arkansas gross tax less credit and an unchanged modeled investment base during brokerage principal withdrawal. Predecessor/delta boundary and alternate published ES values remain covered in the original P1/P2/P3A suites.

## 12. Surplus accounting

The original fixture ends with $896,829 in the IRA and $1.34 in retained nonqualified assets on a $1 million opening balance. Total investable assets are $896,830.34 and estimated after-tax estate is $672,623.09 at the stipulated 25% heir rate. Federal adjusted gross income is $103,171. There is no fabricated pension, RMD, conversion, or duplicated tax-funding income. In year two +4% growth contributes $0.05 under the legacy cent-rounding convention. A $1.39 expense withdraws the full retained asset first. The initial live account remains $1 million. Every tested accepted ledger reconciles exact cash sources and uses.

## 13. Medicare and IRMAA

The original premium authority uses the pre-premium federal calculation for the annual IRMAA bracket. A second pass starts anew with that premium, and the accepted ledger includes Medicare and only the final portfolio. The test that begins at $105,000 modeled ordinary income passes the $109,000 Single boundary after funding. It verifies that final AGI is higher after premium-related spending, while the stored premium uses the existing first-pass MAGI. The partial first-year RMD fixture also verifies the accepted full annual Medicare convention. This is the existing engine's current-year projection convention. No new historical two-year lookback data was inferred.

## 14. Single, MFJ and survivor projections

Tests cover both death orders with documented direct spouse pension, MFJ in the death year and Single afterward. The original pension holder is distinct from the surviving recipient. A separate pair of actual strategies uses different current-owner funding order. Single tests include an earlier unknown Form 5329 outcome and a partial first-year opening after 59½. An existing same-year opening RMD and the current withdrawal are both recognized before fixed conversion. A 30-year admitted path preserves exact annual cash identities.

## 15. Monte Carlo propagation and isolation

`MonteCarloAnalyzer` and `MonteCarloStrategyComparisonAnalyzer` use their existing engine injection and actual `projectWithOutcome`. Two matched +4%/−2% worlds retain independent plan/account copies and finite paired observations. The base plan remains unchanged. In a separate world a shortfall is genuine and contributes no terminal value. In another, lower cash makes funding reach an IRA with material unsupported early-distribution evidence. The existing Monte Carlo wrapper carries the Arkansas exception and the analysis aborts instead of returning a percentile with a fictitious loss.

## 16. Integrated Social Security and longevity

Single and couple deterministic claiming runs use their existing income engine and actual Arkansas projected accounts. Modeled federal taxable Social Security is included once in AGI and the Arkansas-only base reconciles it out. The comparison evaluates both baseline and alternate claiming strategies through an injected engine. Two bounded mortality scenarios are run by the independent weighted evaluator and the continuation carrier/fallback engine. Their exact estates and weighted aggregates agree. An unsupported actual Arkansas year preserves the reason and prevents publication of an expected estate. Social Security-only calculation remains unchanged.

## 17. Unavailability versus insolvency

The internal annual outcome follows existing application semantics. A real account funding inability is a structured `ProjectionExecutionResult.InsufficientFunds`. Source or tax unavailability, funding-proof failures and targeted-proof failures throw `ArkansasProjectionUnavailableException` with year, category and the underlying explanation. The public plan workflow keeps its existing State Tax Unavailable path; the internal Arkansas exception carries its heading without a fabricated registered-state result. No partial Arkansas prefix is returned as a completed projection, and effectively unavailable cases have no accepted history. New unsupported source tests cover pensions, early IRA withdrawals, later-year low-income relief and dependent analyzer execution.

Generic opening-RMD input validation still runs before the state pricing adapter. If required opening account information is missing, the original engine precondition aborts the projection. The Arkansas adapter prices only the resolved opening and current RMD facts.

## 18. Input persistence and historical snapshots

Plan JSON gains no Arkansas property, enum or activation flag. Normal input freeze and fingerprints remain stable. The production retained snapshot rejects internal Arkansas any-year capture, including integrated detailed results. No enum carrier, plan file migration or new codec is used. Existing tests for canonical zero settlement fields, positive legacy validation-fixture surplus, historical payable tax and exact retained decoding continue exercising the pre-existing formats. A detached validation report has immutable annual monetary data plus three version identifiers.

## 19. Save As, scenarios and baseline swaps

Ordinary input scenario copying continues to discard original projected result associations, and the same frozen input hashes survive isolated Arkansas runs. Existing full snapshot Save As, reopen and baseline swap tests run unchanged, including P2's separately marked synthetic accounting fixture. Genuine P3B Arkansas years are refused by the production snapshot boundary, so there is no P3B Arkansas accepted Save As or baseline-swap path. Preserving the original projected history requires a separate, provenance-bearing accepted Arkansas annual component approved for admission.

## 20. Versioning and freshness

Gated validation rows freeze `arkansas-income-tax-rules = AR-2026-P2-1`, `arkansas-tax-funding = AR-FUNDING-P2-1`, and `arkansas-roth-target = AR-TARGET-P3A-1` separately. The disclosure can compare its exact captured map against reviewed versions. No global production or Social Security-only freshness key was activated. Paired accepted-analysis current/baseline freshness will require each side's real Arkansas provenance once the durable format is approved. Compact Monte Carlo and weighted results from the internal harness are validation objects, with no production accepted-analysis integration added.

## 21. PDF, CSV and annual details

The current exporter entry points dispatch only tagged Arkansas validation years to the detached validation export. The explicit scope displays Arkansas tax, federal tax, gross and taxable funding, actual Roth transfer, credited surplus, retained balance, ending assets, estate, exclusions in the annual calculation notes, limitations and versions. The PDF displays a positive source line for $1.34. It uses result-time facts, with no historical means test against an edited plan. CSV after a live account edit retains byte-for-byte original content from the frozen report object. Ordinary PDF layout and CSV schema stay on their existing paths.

## 22. Selector, registration and activation checks

No AR/CO enum entry, calculator registration, user toggle, global flag or JSON injection path exists. The standard engine on the same input produces an ordinary registered-state projection, and entering AR in saved plan JSON fails to load. The integration uses the real tax and funding classes from the package-internal capability. The regular UI and controller are unable to construct that capability through supported APIs.

## 23. Performance

Measured ordinary/fixed/targeted and difficult-band cases via the actual engine. The $1.34 case's funding metrics and the cash and deferred target metrics are printed for reproducibility. The 30-year validation uses 385 final funding candidate evaluations. The small supplied-path Monte Carlo and bounded claiming comparison are logged separately. These timings are individual JVM test measurements. No JavaFX task was used to schedule an Arkansas validation, no UI responsiveness claim was made, and no memory profile or interactive load test was performed.

| Focused-run case | Elapsed | Certifying work |
| --- | ---: | --- |
| Discontinuous year plus following ordinary year | 2.680 ms | 15 funding evaluations and 41 interval bounds in the first year |
| 30 actual adjustment-start/exit projections | 99.408 ms | 30 annual calls using brokerage principal |
| Fixed conversion, opening RMD and Medicare | 10.983 ms | 14 funding evaluations and 35 interval bounds in the accepted pass |
| Cash-funded target year | 18.541 ms | 1 exact conversion trial, 12 funding evaluations, 30 inner bounds |
| Deferred-funded target year | 14.591 ms | 2 exact conversion trials, 32 funding evaluations, 76 inner bounds, 195 income boxes |
| 30-year deterministic path | 96.791 ms | 385 final-pass funding evaluations |
| Controlled small Monte Carlo sequence | 24.731 ms | Matched-path and shortfall runs, excluding separate unavailable check |
| Bounded claiming comparison and explicit validation | 152.934 ms | Current baseline, two distinct candidate inputs and explicit alternate |

Counts report the accepted pass unless the timed operation is a scenario sequence. Preliminary Medicare recomputations are included in elapsed time. Each P2 candidate prices one native annual federal calculation and Arkansas quote, with the Arkansas reconciliation checking federal facts. The bounds may add federal consistency quotes and Arkansas interval work. Candidate counts should not be read as exact low-level function invocation counts.

## 24. Independent financial controls

| Area | Verified scope |
| --- | --- |
| Discontinuity and next-cent deficit | Real one-year original, account-limited one-cent-short counterpart, following-year retained growth and withdrawal |
| Ordinary annual liability | Real Arkansas band starts and exit checked against independent rates, credits and adjustment amounts |
| Conversion execution | Both original targeted controls, different funding strategies, missing and exhausted fixed-conversion capacity, actual same-owner trace |
| Sources and pensions | Both survivor directions, independent actual MFJ year transition, partial-year opening/current RMD, age qualification/refusal and brokerage principal |
| Federal and commercial receipts | Native taxable SS, commercial annuity basis, NIIT control with $218.88, Medicare premium and IRMAA pass |
| Longitudinal and analyses | 30 years, short matched Monte Carlo, genuine insolvent and unavailable worlds, small claiming and continuation equivalence |
| Immutable downstream | Production history rejection for Arkansas, frozen CSV/PDF facts, existing codec/Save As/baseline regressions |
| Retained independent domains | All prepared ES/P comparison points, P1 legal boundaries, 65 Special, two-owner multiple feasible partitions and proof budgets |

Full generated expense-volatility and Arkansas stochastic mortality Monte Carlo were not exercised. Existing production workflow coverage remains in the bounded regression. Domain proof and all-band tests supplement the integrated cases rather than expand the admitted source policy.

## 25. Focused regression

IntelliJ bundled Maven and Java 25 with the project-local `.codex-m2/repository`. The isolated development run passed 146 Arkansas cases, of which 19 are new true-engine and compatibility validations. The final focused run finished November 18, 2026 at 19:41:05 EST with **678 passed**, zero failures, zero errors and zero skips. It included registry and Alabama/Michigan/representative other-state tests, legacy funding and conversion, snapshots, existing exports, Monte Carlo comparison and longevity references. The log is `target/phase6e-p3b-focused.log`.

## 26. Bounded regression

The bounded selector is `*,!*Benchmark*,!*CapacityProbe*,!*Performance*,!*Exhaustive*,!*Search*`. The final log is `target/phase6e-p3b-bounded.log`. No full claiming matrix or unrestricted stochastic run was launched.

The bounded run completed November 18, 2026 at 19:50:44 EST. It discovered 5,461 cases, with **5,426 passed**, 35 skips, zero failures and zero errors. All 146 Arkansas tests ran and passed. The 35 skips match the P3A baseline.

## 27. Existing-state invariance

The accepted P1/P2/P3A tax, funding, conversion and surplus semantics are preserved. The 20 registered jurisdictions still use their legacy solver and annual behavior. The common engine gained a typed bridge at the already established conversion/funding boundary. No registered state goldens, routing/freshness keys, household accounting codecs, federal calculations or existing export schemas were changed.

## 28. Exact changed-file manifest

The following relative package names are below `com/daviddunn/retirementplanner/`.

| Path | Status and purpose |
| --- | --- |
| `src/main/java/.../app/export/ArkansasValidationReport.java` | New detached validation artifact and CSV/PDF writer |
| `src/main/java/.../app/export/ProjectionCsvExporter.java` | Typed validation dispatch |
| `src/main/java/.../app/export/ProjectionPdfExporter.java` | Typed validation dispatch |
| `src/main/java/.../app/socialsecurity/IntegratedSocialSecurityStrategyEvaluator.java` | Optional explicit engine injection |
| `src/main/java/.../app/socialsecurity/LongevityWeightedIntegratedStrategyEvaluator.java` | Optional explicit engine injection |
| `src/main/java/.../app/socialsecurity/LongevityWeightedContinuationEvaluator.java` | Explicit engine reaches both existing paths |
| `src/main/java/.../app/socialsecurity/results/RetainedProjectionSnapshot.java` | Prevents unregistered validation history capture |
| `src/main/java/.../domain/projection/ArkansasProjectionUnavailableException.java` | New typed internal unavailable outcome |
| `src/main/java/.../domain/projection/ArkansasProjectionValidation.java` | New package-internal source adapter and validated annual service dispatch |
| `src/main/java/.../domain/projection/ArkansasValidationTax.java` | New isolated annual tax identity, rules and diagnostics |
| `src/main/java/.../domain/projection/ProjectionEngine.java` | Gated real annual dispatch and accepted settlement |
| `src/main/java/.../domain/projection/ProjectionYear.java` | Validation tax alternative independent of production state identity |
| `src/main/java/.../domain/tax/ArkansasAnnualFunding.java` | Neutral annual request plus authoritative terminal deficit quote |
| `src/main/java/.../domain/tax/state/AnnualIncomeTaxFacts.java` | New jurisdiction-neutral annual income and federal evidence |
| `src/main/java/.../domain/tax/state/ArkansasIncomeReconciliation.java` | Uses neutral facts |
| `src/main/java/.../domain/tax/state/ArkansasProductionTaxCalculator.java` | Accepts neutral annual facts |
| `src/main/java/.../domain/tax/state/StateTaxCalculationRequest.java` | Additive view of annual facts |
| `src/main/java/.../ui/summary/ProjectionYearDetailsPane.java` | Isolated Arkansas annual details |
| `src/test/java/.../app/socialsecurity/ArkansasLongevityValidationTest.java` | New bounded claiming and longevity tests |
| `src/test/java/.../domain/projection/ArkansasProjectionIntegrationTest.java` | New real annual engine tests |
| `src/test/java/.../domain/projection/ArkansasValidationAnalysisTest.java` | New exports, scope and controlled Monte Carlo tests |
| `src/test/java/.../domain/projection/RetainedProjectionCodecTest.java` | Explicit structural inventory update for the internal field |
| `PHASE-6E-P3B-ARKANSAS-INTEGRATION-ADMISSION-REPORT.md` | This report |

## 29. Remaining legal, source and integration gates

- Final DFA filed-return guidance, future parameter verification, low-income forms and uncertain retirement timing remain governed by the existing bounded policy. Material ordinary cases remain explicitly unavailable.
- Legacy saved pension facts lack Arkansas employer-plan qualification. A user-visible, persisted, auditable Arkansas retirement source decision with legacy unknown handling is needed before claiming general pension support. The validation evidence provider is intentionally not a user assumption switch.
- Production-admitted annual and compact-analysis results require a versioned genuine Arkansas identity. Validate serialization and metadata of that future type, including existing same-file checksums, current and baseline role freshness, Save As, historical reopen, and PDF/CSV from reopened records.
- Public controller/UI State Tax Unavailable presentation, background scheduling with cancellation for integrated Arkansas, broader stochastic/expense-law paths, and native end-user acceptance remain gates.

## 30. Native Windows acceptance checklist

After separate activation authorization, verify state picker, Single and actual MFJ, both survivors, fixed and targeted Roth, both spending/funding strategies, the $1.34 retained flow and next-year behavior, special-source unavailability, and active plan/history preservation after failure. Verify persistent user pension evidence, Save As, both baseline swap roles, old snapshot reopening, current and stale disclosures, results PDF/CSV and Light/Dark. Compare Alabama and Michigan on native controls and measure practical responsiveness and cancellation. P3B does not expose Arkansas to exercise this checklist.

## 31. Admission recommendation

**CONDITIONALLY_READY** for the verified financial integration. Keep the production selector at 20 states. A narrowly scoped follow-up should resolve pension evidence and durable provenance, recheck official annual guidance, then establish actual production workflow and native acceptance. The current evidence does not justify final admission approval.

## 32. Git verification

Closing HEAD matches the opening commit. `git status --short --untracked-files=all` matches the 23-path manifest above. `git diff --check` and the separate cached check passed. The index stayed empty. UTF-8 and trailing whitespace were checked on new files. No staging, commit, push, reset, tag or deployment occurred.
