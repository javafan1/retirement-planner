# Phase 6E-P2 — Arkansas Funding, Roth Execution and Surplus Settlement

Verification date November 18, 2026. Scope is the isolated annual domain and history compatibility. The original Phase 6E-M, N2, O and P1 evidence and numeric fixtures are retained.

## 1. Executive summary and implementation outcome

P2 implements an account-backed Arkansas funding solver, exact surplus settlement, fixed Roth conversion followed by funding from the post-conversion portfolio, and a certified limited targeted Roth search. An isolated annual adapter uses the normal source, federal, Roth and withdrawal machinery. Arkansas and Colorado remain disabled. The representative discontinuity settles a real $1.34 surplus from a $16,974.00 executed withdrawal. The solver proves nominal cash sufficiency on the authorized grid and distinguishes capacity shortage from unavailable tax or proof.

The outer target search certifies the case where the federal no-funding ceiling or its covered neighborhood achieves the target after funding. An ordinary IRA-only case can exceed that neighborhood. This is an Arkansas production admission blocker until broader outer coverage is certified or an explicit narrower product policy is approved.

## 2. Confirmed production baseline

The current production enum and router contain AK, AL, AZ, FL, GA, IA, IL, KY, MI, MS, NC, NH, NV, OK, PA, SD, TN, TX, VA and WY. Arkansas and Colorado are absent from both. Arkansas `productionAdmitted()` remains false. Alabama's pension admission, preflight and final unavailable handling use their accepted paths. There is no new production freshness key.

## 3. Early architecture-gate findings

1. `ProjectionEngine.calculateProjectionYear` calls the legacy `TaxFundingCalculator` directly. The new Arkansas classes have no production engine or router caller. The existing engine and funding solver are unchanged.
2. `ProjectedWithdrawalAllocator.allocateAdditionalWithdrawal` returns exact projected debits and new balances together. Both reviewed strategies sort stable persisted order within tax priority. The new overload uses the same execution with an existing `TAX_FUNDING` purpose. The existing entry point still applies `CASH_FLOW`.
3. The existing retained household asset pool already supports deposit, growth, account ordering, next-year withdrawal and estate/net-worth inclusion. The settlement leaf is stored inside the existing annual DTO. An optional positive-only wire field can preserve default wire bytes while validating original required fields.
4. `ProjectedPortfolioRothConverter.convertHousehold` returns an immutable projected portfolio and real same-owner transfers. The isolated Arkansas path builds a fresh conversion and funds from that returned balance. The shared legacy bracket-fill path and accepted behavior are unchanged.

## 4. Year-aware low-income admission

Use the 2026 nominal P rates, deduction and ordinary credits for later estimated tax amounts. Independently evaluate each year's potential eligibility for missing statutory low-income relief.

- §26-51-301(c)/(e) applies the August 31 CPI period, a positive growth maximum of 3%, and nearest-dollar statutory rounding. Starting with the 2010 Single $14,000 and actual joint zero/one-dependent $22,400 endpoints, apply 1.03 and outward integer-dollar rounding for each 2010 through target year, inclusive. Applying the maximum starting in 2010 preserves the approved conservative margin. Compared to nearest-dollar statutory adjustment, each exact integer outward step is at least the legal result and is monotone in the input.
- §26-51-501(a)(6)(C) directs a different operation through §26-51-201(d), using the succeeding-year bracket method and nearest $100. Start from DFA's published 2025 $27,600 end of the positive-credit region. Model the greatest possible next endpoint by multiplying by 1.03 and rounding to the nearest $100 with upward ties. That result upper-bounds every permitted CPI and nearest-$100 tie choice. Repeat from the independent statutory envelope, then take its maximum with the P1 2026 $28,500 proof fixture. The 2026 fixture is an output floor for the envelope only. Feeding it back as a published table endpoint would compound the rounding margin.
- The §501 credit's actual filing, liability and calculation conditions still apply. No ordinary personal-credit revenue condition or projected CPI is substituted for either statutory calculation. The ceilings are refusal proofs. They are neither forecast brackets nor thresholds established for an actual filer.

| Year | §301 Single gross | §301 actual MFJ gross | §501 net |
| ---: | ---: | ---: | ---: |
| 2026 | $23,148 | $37,035 | $28,500 |
| 2027 | $23,843 | $38,147 | $29,300 |
| 2030 | $26,055 | $41,686 | $32,000 |
| 2040 | $35,023 | $56,028 | $43,100 |
| 2100 | $206,432 | $330,175 | $253,700 |
| 2426 | $3,160,420,703 | $5,054,773,467 | $3,882,564,700 |

Positive estimates require strict clearance of both eligible-region envelopes. The test admits $31,000 of ordinary Single income in 2026 at $659.41 and refuses the same nominal case in 2027. Stable zero remains provable where the reviewed ordinary best election is exactly zero under all represented eligibility choices. Positive years past 2426 return a structured outside-policy result. The model does not claim a new Arkansas filed-return methodology. [§26-51-301(e)](https://law.justia.com/codes/arkansas/title-26/subtitle-5/chapter-51/subchapter-3/section-26-51-301/) · [2021 Act 1 §10](https://webftp.blr.arkansas.gov/Home/FTPDocument?path=ACTS%2F2021S2%2FPublic%2FSearchable%2FACT1.pdf) · [§26-51-201(d)](https://law.justia.com/codes/arkansas/title-26/subtitle-5/chapter-51/subchapter-2/section-26-51-201/) · [2025 DFA return instructions](https://www.dfa.arkansas.gov/wp-content/uploads/2025_AR1000F_and_AR1000NR_Instructions.pdf)

## 5. Source contracts and changed file manifest

Paths below use the common Java root `com/daviddunn/retirementplanner/`. Changes are relative to the opening P1 working tree.

| Path | P2 change |
| --- | --- |
| `src/main/java/.../domain/tax/ArkansasAnnualFunding.java` | Annual capture and reconciliation, tax candidate, evidence binding and exact settlement of accepted copy |
| `src/main/java/.../domain/tax/ArkansasMinimumFundingSolver.java` | Reviewed source intervals, ascending proof, exact terminal capacity and typed outcomes |
| `src/main/java/.../domain/tax/ArkansasRothFunding.java` | Isolated fixed conversions and bounded authoritative targeted trials |
| `src/main/java/.../domain/tax/state/ArkansasProductionTaxCalculator.java` | Year-aware admission and interval state-tax floor |
| `src/main/java/.../domain/tax/state/arkansas/ArkansasTaxRules.java` | `AR-2026-P2-1` and distinct annual proof bounds |
| `src/main/java/.../domain/tax/state/arkansas/ArkansasJointWorksheet.java` | Overload bounding incremental ordinary and qualifying receipts |
| `src/main/java/.../domain/projection/ProjectedWithdrawalAllocator.java` | `allocateTaxFundingWithdrawal` additive purpose-specific entry |
| `src/main/java/.../domain/projection/HouseholdCashSettlement.java` | New retained cash field, exact factory, strict localized wire reader |
| `src/main/java/.../domain/projection/ProjectionYear.java` | Positive settlement/source validation and frozen detail accessor |
| `src/main/java/.../ui/summary/ProjectionYearDetailsPane.java` | Positive-only result-time surplus detail |
| `src/test/java/.../domain/tax/ArkansasAccountFundingTest.java` | Account-backed annual, conversion, cash, tax and proof fixtures |
| `src/test/java/.../domain/tax/ArkansasDomainCalculationTest.java` | Independent year-aware legal bounds and later refusal |
| `src/test/java/.../domain/projection/RetainedProjectionCodecTest.java` | Updated explicit leaf schema inventory, legacy wire and tamper tests |
| `src/test/java/.../persistence/ProjectionSnapshotPersistenceTest.java` | Positive synthetic accounting component and real Save As/restore/swap |
| `PHASE-6E-P2-ARKANSAS-FUNDING-SETTLEMENT-IMPLEMENTATION.md` | This report |

The original Arkansas rate worksheet, eligibility helper, funding prototypes, P1 evidence class and source reconciliation are preserved. The tax rules version is separate from `AR-FUNDING-P2-1`, which identifies the account order, cent/terminal domain, exact tax cash, discontinuity proof, retained priority and typed outcomes. `arkansas-tax-funding` is a candidate future reference key.

## 6. Minimum-sufficient algorithm

A pass freezes filing status, government rules, P method, selected income, account sources, receipt facts, guaranteed cash, projected-period RMD cash, spending and Medicare. The solver starts from the post-RMD, post-spending and post-conversion portfolio. It prices zero first, then visits cumulative source domains in ascending order, and evaluates the exact fractional terminal capacity last if distinct from its floor cent.

On each source interval, price the first admissible cent against the real allocator and both tax authorities. Return it if sufficient. Otherwise try a proof of impossibility for the interval. Split any unresolved interval at an interior integer index and visit the earlier half before the later half. A leaf is priced exactly. A supported lower tax envelope above all available cash throughout the interval proves that interval insufficient. A missing legal classification or exhausted proof budget returns unsupported. Multiple roots, jumps over equality and temporary later insufficiency remain visible to the proof.

## 7. Breakpoint and interval model

- Retained household cash is first. The allocation partition then follows exact cumulative account capacity in reviewed stable strategy order. A fractional interior boundary is located between its neighboring lattice points. The next source's domain starts at the first cent strictly above its prior cumulative capacity.
- Within a source domain, the variable withdrawal is either wholly included in ordinary income or adds no modeled taxable income. Previous debits are fixed. For the variable retirement source, receipt window and owner are fixed. As ordinary income increases, each recipient's added qualifying income is at most that increase. Pool exhaustion, the Arkansas zero floor, the deduction, the senior elections and the upper bound on taxable income therefore give a conservative net interval for each election. An unproved age, an uncertain exclusion or a potentially unsupported low-income range blocks an interval certificate. Exact remaining points can still use the normal P1 rules.
- The state envelope evaluates both interval ends and both sides of the high-schedule start, 29 adjustment bands and final exit. Each continuous section, including the lower slices, has nonnegative slopes. State credits, floor and final rounding are applied to each election's envelope. The minimum across lawful elections gives a valid lower bound even when the winning election changes at an interior point. For coverage of a stable zero, the minimum of the per-election maxima is an upper bound on the best lawful election.
- The federal lower bound is the native federal tax at the interval's left evidence point. For frozen annual facts, ordinary income, taxable Social Security, federal taxable income, ordinary liability and admitted NIIT are nondecreasing. The existing SS zero, 50% tier, 85% tier and benefit caps, standard-deduction floor, federal rate boundaries, MAGI thresholds and NII caps preserve that property. The federal bound covers those breakpoints without requiring explicit roots for each. The accepted trial always uses the full federal model at its own point.
- No aggregate cent rounding is used for the acceptance inequality. State and NIIT apply only their ordinary existing model rounding. A candidate must cover the raw federal amount, including subcent precision.

## 8. Minimality, shortage and termination

For a source-homogeneous interval with first point `l` and last point `r`, a certified lower bound is `federal(l) + minArkansas(l..r)`. If this is strictly greater than `otherCash + r`, every candidate is insufficient. The relaxed envelope may include nontested tax bases outside the actual feasible path. That can require more splits and cannot hide a sufficient cent. A positive result is always repriced by executing the candidate on the account portfolio. This is minimal because all earlier points were either tested and insufficient or discarded by the bound. Shortage requires excluding every permitted point, including the terminal when applicable. Tax or proof unavailability is separately typed.

The solver covers at most 64 persisted account slots, $1 trillion of executable capacity and 512 candidate evaluations. For the maximum domain, recursion depth is at most 48 levels. Each nonpruned interval partitions into smaller integer ranges. At most 512 actual allocation trials and, conservatively, 512 times the depth in attempted bound operations are possible. There is no portfolio-sized cent scan. Depth-first storage is proportional to depth times held immutable trial size plus the bounded source partition. Additional proof beyond the cap returns `UNSUPPORTED/BUDGET_EXHAUSTED` and supplies no candidate.

## 9. Account allocation and immutable trace authority

The allocation record contains exact withdrawal total, original frozen references, purpose, year and tax character. The executed balance debits and total gross trace are cross-checked against the request. The successful `Candidate` carries the allocator's own immutable ending portfolio and the calculated taxes from that trace. Settlement uses that portfolio once. It executes no second withdrawal. Rejected trial copies and their ledgers remain isolated from persisted accounts.

## 10. Source-owner and tax attribution

Existing income capture supplies the selected pension and household Social Security sources, opening and current RMD, ordinary spending, conditional payments and investment evidence. The cash gate reconciles the three available cash categories to attributable annual receipts and executed distributions. Seed facts with unpriced cash fail explicitly. An explicitly different source year is rejected before shared attachment can normalize it. Securities principal and retained cash affect liquidity without becoming taxable gains in Basic. Advanced NIIT keeps its original evidence requirements. Owner-specific retirement exclusions and senior choices continue through the P1 calculator. Death, direct survivor and original pension eligibility require corroborated P1 evidence. Annual windows are bounded by the actual modeled projection slice and preserve any narrower represented dates. No conversion or withdrawal execution date is inferred within that slice.

## 11. RMD before conversion

The isolated caller processes opening and projected-period RMDs and ordinary spending before invoking the conversion and funding classes. Opening RMD attributable earlier in a partial first year enters AGI and the actual owner's retirement pool but contributes no current cash. Current RMD cash contributes only its executed amount. The real converter sees the portfolio after account RMDs and prior spending, so used principal cannot move to Roth.

## 12. Fixed Roth conversions

Each fixed trial invokes the real projected converter with active owners and current year. The trace records actual owner, source and eligible same-owner Roth destination. Requested amounts beyond actual capacity are shortfalls. Income receives just the successful transfers. Funding uses remaining traditional balances and eligible cash, including Roth withdrawals treated as tax-free by the existing modeled conventions. The fixture requests $48,000 after a $10,000 RMD, then verifies $1,000 of residual traditional funding before Roth cash. A $55,000 request against the same $49,000 remaining convertible assets records $6,000 shortfall. Without a destination, the conversion is zero and the full request is a shortfall.

## 13. Targeted Roth coverage

A monotone auxiliary federal taxable-income floor, with actual conversion as ordinary income and zero funding, rules out every higher cent above the no-funding federal cap. It does not price or choose a tax funding amount. From that cap, test the fractional maximum if relevant, then descend through at most 32 remaining actual conversion points. Every trial independently executes the conversion and invokes the inner funding proof. First feasible actual conversion in that region is the maximum because all larger amounts were ruled out by a necessary federal condition or executed and checked. A legal/funding proof gap stops the outer decision. The outer actual predicate is never binary-searched.

For a $50,000 ordinary base and $50,400 federal target, cash-backed funding succeeds with $16,500 actual conversion, zero taxable funding income and one outer trial. The comparable ordinary IRA-only case exceeds 32 points and returns structured unsupported. Broader certified outer bounding, particularly across deferred funding, source exhaustion and the state discontinuities, is required before admitting common targeted cases. The legacy solver stays separate.

## 14. Federal, SS and NIIT integration

The normal yearly taxable SS result and ordinary federal calculator price each candidate. The ordinary federal calculation retains exact decimals, and the existing Advanced-mode NIIT implementation produces its own evidenced amount. Retained source reporting may include the candidate's completed NIIT evidence, while trial income never reuses completed NIIT evidence. Tests check the first and second federal SS tiers, a funding-driven MAGI crossing with evidenced interest, federal bracket targeting and a Medicare repricing pass.

## 15. Exact surplus ledger

First use guaranteed income and projected-period RMD for expenses including Medicare. Spend any remaining guaranteed income and RMD cash on taxes, in that order. Apply only actual funding cash to the remainder. The funding category is `executedFunding − max(taxes − otherCash, 0)` and must be nonnegative. Deposit it with any independently remaining guaranteed income or RMD cash into the same household retained pool. No supplemental tax, ordinary income, retirement credit, conversion principal or fictitious pension amount follows from that deposit.

## 16. Settlement compatibility

The original ten-argument constructor and `calculate` retain their prior values and two-cent tolerated source/use reconciliation. The added final amount canonicalizes zero to `BigDecimal.ZERO`. The separately named `settleMinimumSufficient` requires exact cash coverage, verifies ordinary spending, and checks priority and attribution. Positive historical records must corroborate annual funding, tax and retained balances with the frozen funding trace. The old factory rejects the $1.34 case. The new one accepts it once.

## 17. Later retained growth and use

The $1.34 test deposit produces $1.3936 after +4% and $1.3132 after −2%. The next withdrawal spends retained assets first, with the IRA balance unaffected by that retained withdrawal. The twelve-account fixture separately carries the prior result through a second funded year under matched return paths. Both use the existing household nonqualified pool and its normal estimate, ordering and estate behavior.

## 18. Legacy construction and JSON

Zero is omitted regardless of its representation on construction. The specialized leaf reader requires all ten original fields and accepts only the optional new numeric property alongside them. It rejects nulls, strings, duplicate fields and unknown properties, and retains original monetary scale. Existing canonical property names and order are unchanged. The old wire fixture is verified byte-for-byte including `100.000` and `1.000`, with an identical SHA-256 before and after. A new positive field is lossless and rejects negative values, changed attribution totals or an inconsistent annual trace.

## 19. Snapshot, hash, Save As and baseline

The retained codec preserves and re-encodes an account-backed positive annual fixture exactly. Complete snapshots use the same nested settlement leaf and existing checksum verification. The Save As test writes a positive synthetic baseline component, copies it under a new plan identity, opens it without running an engine, and swaps both roles while retaining the original annual result. A pre-extension-shaped ordinary snapshot retains compressed bytes on decode/re-encode. Existing accepted analysis payload and historical compatibility fixtures run through the existing codecs. No Arkansas accepted-analysis calculation, active rule key or migration is introduced. Positive test history explicitly uses a synthetic test envelope since the production DTO requires a registered state; it is never evaluated or published as a Florida or Arkansas outcome.

Scenario input copies continue to clear the original result-role manifest deliberately. They preserve input identity without carrying forward a different scenario's accepted outputs. Rebinding and carrying original history belong to Save As and the snapshot component roles, as verified above. Unregistered Arkansas cannot originate a positive accepted analysis payload in production during P2.

## 20. Annual result and report seam

The new frozen-value accessor is shown in annual details only if current or baseline funding surplus is positive. Gross tax funding and withdrawals keep their existing values. Historical CSV columns and PDF layout are preserved. A dedicated Arkansas PDF/CSV breakdown can consume the frozen category after P3 acceptance. No existing export gains a normally zero row.

## 21. Adversarial numeric fixtures

| Fixture | Verified outcome |
| --- | --- |
| Original two-cycle | $16,972.94 reprices to $16,975.09 and $16,975.09 reprices to $16,972.94 at displayed precision |
| First sufficient | $16,973.99 is insufficient. $16,974.00 meets native total $16,972.66 and retains $1.34 |
| Alternate published ES model | Original independent $16,973.36 and displayed $16,974.79 cases retained. Later exact sufficient cent is $16,974.80 |
| Exact subcent capacity | A $0.0050154 deficit fails at $0.004 terminal cash. $0.006 succeeds with $0.0009846 retained |
| Source exhaustion | Split before $16,974.00 across two IRAs. One-account solution when exhaustion is after the discontinuity |
| Tax edges | Real account-backed pricing checks three adjacent cents at the start of every band and the final exit |
| Ownership and legality | Actual Single/MFJ and survivor, unsupported dead-owner debit, dual strategies, complementary retirement pools, unknown brokerage basis and no duplicate investment income |
| Funding blockers | Proof budget, incapable account balances, unsupported Form 5329, birthday timing, mismatched cash source and duplicate account identity |

## 22. Performance and measured search

| Fixture | Source segments reached | Candidate allocations | Interval bounds attempted | Maximum depth | Budget |
| --- | ---: | ---: | ---: | ---: | ---: |
| Original discontinuity | 1 | 16 | 39 | 21 | 512 |
| Twelve-account annual case | 6 | 18 | 33 | 17 | 512 |

One candidate performs one authoritative allocation and one P1 worksheet pricing, with a federal calculation plus P1's independent consistency recheck. An interval proof repeats the federal consistency check and scans the 30 state discontinuity boundaries for at most four per-household elections. A bound attempt does not execute another withdrawal. Metrics do not measure runtime or imply a sustained production load SLA. The depth-first proof retains a bounded chain of trial state and stable frozen source data. The outer targeted cap is independent of the 512-candidate per-trial cap.

## 23. Controlled nonproduction integrations

The focused harness uses actual projected account balances and identity, actual withdrawals, explicit RMDs with their normal projection allocator, actual converter transfers, annual tax/income assembly, Medicare recalculation and the minimum-sufficient cash contract. It runs a second test year after account depletion under +4% and −2% paths and compares two strategy/owner paths. The history adapter only manufactures the test envelope required by the registered-enum DTO. No Arkansas production Monte Carlo, integrated Social Security, full live ProjectionEngine route or UI acceptance was run. Such execution needs separately approved P3 wiring.

## 24. Test execution

Used the IntelliJ bundled Maven, Java 25 and workspace-local `.codex-m2/repository`. Development and history checks are in `target/phase6e-p2-history.log`. Initial and final runs use the corresponding logs with and without the `-final` suffix. Bounded filter `*,!*Benchmark*,!*CapacityProbe*,!*Performance*,!*Exhaustive*,!*Search*`.

The initial and final runs had identical counts. The final focused and bounded runs completed at 16:27:25 and 16:48:06 EST, respectively, on November 18, 2026.

| Suite | Discovered | Passed | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: | ---: |
| Focused | 606 | 606 | 0 | 0 | 0 |
| Bounded | 5,425 | 5,390 | 0 | 0 | 35 |

The 110 Arkansas cases include 87 carried from P1 and 23 new funding and year-aware proof cases. The two additional history cases ran in both suites. All Arkansas cases ran without skips and the final pair covers the pre-attachment source-year guard.

## 25. Existing-state invariance

The legacy solver, projection orchestration, Medicare, Roth execution, accepted state calculators, existing expected monetary goldens, engine version registry, Monte Carlo and Social Security interfaces were left in their original paths. The shared allocator changes only entry dispatch for the new call. The settlement JSON change adds no zero wire field. Focused references include Michigan, Alabama, Oklahoma, Mississippi, Georgia, Kentucky, no-income-tax jurisdictions, funding, projection and older snapshot tests.

## 26. Remaining Arkansas production admission blockers

- Broader certified outer conversion coverage is required for common deferred-funded targeted conversions. An inner funding proof alone does not certify outer optimality.
- Final Arkansas annual guidance and actual indexed low-income forms must be reviewed. The current conservative refusal policy still excludes material ordinary cases. Timing, Form 5329, special pensions and investment/NIIT evidence remain explicitly bounded by P1.
- Real Arkansas yearly admission needs the complete source/evidence routing and unsupported-reason translation across deterministic, Monte Carlo and Social Security interfaces. Native preview and full Arkansas results cannot be claimed from the synthetic test envelope.

## 27. Recommended P3 work

First secure explicit approval. Confirm rule versions and unsupported boundaries, then implement a tax/financial proof adapter for targeted conversion upper intervals with real post-execution source balances. Certify the ordinary deferred-funded case with independently expected values. Add Arkansas-only annual engine dispatch with recalculated Medicare and one final state commitment. Run independent real source, JSON/reporting, paired-path Monte Carlo and limited Social Security analysis gates before any production admission decision. Register independently reviewed tax and funding versions only with final Arkansas approval. Continue to keep Colorado disabled.

## 28. Native Windows acceptance

After authorized full integration, verify actual Single and MFJ/survivor projections, pension and timing unavailability, both withdrawal orders, a $1.34 retained detail, exact retained year-to-year behavior, fixed and certified targeted Roth strategy cases, and positive versus no-surplus comparison rows in Light and Dark. Test new and frozen reports, Save As/reload, baseline swaps, and stable legacy results. Complete the existing bounded suite and capture the native session. There was no P2 native Arkansas production path to accept.

## 29. Opening Git state and final verification

Opening and closing HEAD was `6faf0b7404e56d62b438f275a8125b28cdfef59d`. The working tree already contained Phase O/P1 reports, prototypes and source changes. P2 preserved that work. `git status --short --untracked-files=all` matched the opening files and P2 manifest. `git diff --check` passed. The new files were also checked for trailing whitespace, and the index remained empty. No staging, commit, push, reset or tag occurred.
