# Phase 6E-P3A — Arkansas Targeted Roth Optimality Certification

## 1. Executive summary

P3A replaces the 32-adjacent-point targeted conversion attempt with a bounded proof over executable conversion and funding intervals. The original deferred-funded case certifies $8,949.09 of conversion and $7,550.91 of IRA tax funding using two exact conversion trials. Independent small-domain enumeration also verifies a genuinely disconnected actual joint result. The production registry stays at 20 states. Arkansas and Colorado remain disabled.

## 2. Starting Git checkpoint

HEAD is `6faf0b7404e56d62b438f275a8125b28cdfef59d` (Alabama and Arkansas preparation). Phase O, P1 and P2 are already staged as 22 paths. Their checkpoint has not reached Git history. P3A treats those staged versions as its starting files and leaves the index untouched.

## 3. Confirmed 20-state registry

AK, AL, AZ, FL, GA, IA, IL, KY, MI, MS, NC, NH, NV, OK, PA, SD, TN, TX, VA and WY. Arkansas and Colorado have no route or enum entry.

## 4. Original P2 outer-search limitation

P2 obtains a necessary federal taxable-income ceiling without incremental funding. It tries exact conversions down from that ceiling, through at most 32 adjacent values after considering the fractional terminal. It verifies each trial's funding; it cannot exclude a long interval where taxable IRA withdrawals consume the target room. The checked-in fixture starts with $50,000 modeled interest, a $90,000 owner IRA, an empty same-owner Roth IRA, zero other tax cash, deferred-first order and a $50,400 Single target. A separate checking-account version is reachable immediately at $16,500.

## 5. Existing target policy and exact metric

`ProjectionEngine` resolves 12%, 22% and 24% strategies by the federal bracket's published rate index and supplies that bracket's projected upper taxable-income bound for the year's actual Single or MFJ status. A custom target supplies the taxable-income ceiling directly. This is federally taxable income after the modeled standard deduction and federal taxable Social Security, subject to the federal zero floor. It includes actual conversion and taxable tax-funding withdrawals. AGI, MAGI, cash taxes and NIIT are different quantities. Applicable regular tax calculations use the current federal basic-deduction model, which has no additional senior deduction in its taxable-income calculation.

The legacy iterative solver uses a $0.01 absolute convergence tolerance, gives zero conversion when the funded baseline already reaches the target, and permits a capacity-limited result below target. P2 Arkansas uses an exact inclusive target ceiling with unrounded federal taxable income. P3A preserves that ceiling and maximizes executable conversion on the admitted grid. A certified result below target retains an explicit unused-target amount. If even zero cannot meet the ceiling, the annual caller may use its original no-conversion policy, but the optimizer identifies the objective as unattainable. A target miss alone establishes no funding failure.

## 6. Actual conversion execution and ownership

The legacy converter takes a request and executes in primary-then-spouse order, breaking ties by persisted account order. It requires a valid same-owner Roth IRA destination and pretax source capacity. Actual amounts, shortfall and destination transfers come from the converter, after RMDs and ordinary spending. Subsequent withdrawals use the selected tax-priority strategy, in stable persisted order within each priority, with retained household assets first. P1 admission distinguishes own-IRA conversion evidence from special employer and basis cases. Qualifying conversion and funding receipts share each recipient's remaining Arkansas $6,000 pool and senior election; they can move net income across the raw statutory boundaries. Employer/source/age matters use existing P1 rules. Legacy conversion, withdrawal, tax, RMD, settlement, history and analysis contracts remain the production authorities for the 20 states.

## 7. Outer search domain

The domain is zero, every cent through actual convertible capacity, and the exact fractional terminal capacity when it lies above the last cent. Full conversion on a projected copy establishes authoritative source/destination capacity and order. The elected amount is an actual supported transfer. Unreachable requested principal above capacity does not enter either tax base. Fixed requests still use their prior request/actual/shortfall contract. Performance guards require a reviewed order, distinct account identities, at most 64 accounts, $1 trillion of total projected spendable capacity and bounded decimal scale.

## 8. Segmentation and interval construction

A root interval is bounded by adjacent cumulative conversion-source endpoints from the real converter. A fractional source boundary is assigned to its neighboring grid points without rounding any account upward. The roots and subdivided regions are visited in descending conversion order.

For a conversion interval [l,h] and a continuous interval [a,b] in total new pretax income, define C as executed conversion, D as executed taxable funding debit and Y = C + D. Hence max(a−h,0) ≤ D ≤ max(b−l,0). For each original deferred account i, the converted amount at C is clamp(C−s_i, 0, k_i), using the converter's executable cumulative prefix s_i and account transfer capacity k_i. Endpoint debits imply its remaining balance lies between balance−converted(h) and balance−converted(l). The deferred funding prefix lies between the sums of those corresponding endpoint balances in the selected withdrawal order. Subtract the widest prefix from the least D for a lower fund bound. Subtract the narrowest prefix from the greatest D for an upper bound. Clamp each bound to the appropriate account remainder. Add conversion and funding receipt ranges, capped above by the original account balance. This encloses shifts in funding account, owner and eligibility without assuming monotone actual target feasibility.

## 9. Federal target upper-bound proof

For this modeled, fully pretax ordinary source domain, every new taxable funding dollar and every conversion dollar increases ordinary income by one dollar. Their sum Y determines federal taxable income given frozen ordinary income and gross Social Security. The native Social Security calculator is nondecreasing across its zero, 50%, 85%, and gross-benefit caps. Federal AGI, modeled standard deduction and the zero taxable-income floor preserve nondecrease under both supported statuses. The absolute cap target plus modeled deduction minus original ordinary income is an upper bound even before taxable Social Security. A monotone inversion tightens it against the authoritative federal functions, using an outward upper bound within $0.0000000001. At an accepted cent or exact residual terminal the exact target is checked again. The inversion never applies to a guessed partially taxable conversion. Source law not supported by the transaction authority does not produce an accepted candidate.

## 10. Arkansas discontinuity handling

For each legal senior election, start with the P1 reconciler's certain and possible annual qualifying receipts per owner. Add the relevant bounded qualifying amounts from the projected conversion and funding source profile. The largest extra qualifying relief cannot exceed the aggregate income increase or the sum of each referenced recipient's remaining cap. The smallest relief uses the certain qualifying amounts. With the approved nominal deduction, these produce a continuous enclosing range for state taxable income. All statutory slices are nondecreasing inside their respective intervals. The worksheet checks the lower/high transition, both sides of all 29 adjustment starts, and the exit, and takes the smallest gross tax within the enclosure. It applies nonrefundable credits, floors at zero and rounds in the accepted manner. The minimum across elections allows the optimal credit selection to change. A low-income admission gap or an unestablished state proof base supplies zero as a lower bound. The exact authority still returns the original unsupported reason if a material point cannot be excluded. The solver neither interpolates statutory jumps nor requires equality at a cash discontinuity.

## 11. Funding-source transitions

Let A be original deferred account capacity and N be the exact nondeferred cash before the first funded deferred account under the selected order, including the retained household pool. Conversions reduce total deferred assets dollar-for-dollar, transfer only to Roth, and leave N and total portfolio value unchanged. If the funding debit has yet to exhaust all remaining deferred assets, then funding spendable cash is at most N + D, so the upper cash bound on an income box is other tax cash + N + b − l. This bound also covers the case where no deferred funds have been reached. If funding can reach Roth or trailing cash, all remaining deferred balances are exhausted and Y = A. Check that branch separately with its own federal ceiling and maximum total portfolio cash. A fractional funding terminal necessarily exhausts every remaining source, so it belongs to the all-exhausted branch. Under taxable-first and deferred-first, the deferred segment is contiguous and their different orders give different N and actual owner debit bounds. Unknown brokerage basis remains cash principal under the accepted Basic model. Advanced NIIT retains its usual source refusals.

## 12. Separate inner and outer optimality arguments

The inner solver prices the actual post-conversion portfolio and returns the smallest executable sufficient funding amount on its established grid. The outer proof asks a weaker question before excluding anything. Could *any* funding debit under the federal ceiling possibly meet its cash taxes? If the answer is proved negative even with relaxed source and liability ranges, the inner minimum cannot satisfy the target. Where the relaxation still permits a solution, the algorithm subdivides or executes an exact trial. Any exact trial uses actual owner-preserving conversion, actual post-conversion balances and the accepted inner solver. Only the exact inner trace can be accepted.

The no-funding ceiling, actual converter capacity and descending partition order exclude or test every greater request before returning a certified result. The reported greatest amount may leave unused target room. The proof works with disconnected feasible ranges and does not assume that passing one request implies passing any neighboring request.

## 13. Proof certificates and exclusion rules

1. Converter capacity excludes unexecutable requests. Federal taxable income without additional withdrawals excludes larger aggregate pretax inclusions by monotonicity.
2. A cash interval is excluded when its maximum permitted cash is strictly below the sum of federal lower liability at the income interval's low end and the Arkansas lower envelope. If the all-deferred-exhausted branch could succeed, the interval cannot be dropped on the prefix proof. Only a complete cover of the boxes establishes interval exclusion.
3. A singleton can be excluded by its exact funded target miss or an actual P2 proof of insufficient funds. P2 unsupported is propagated when the independent necessary conditions cannot rule out that point. A failed midpoint alone supplies no exclusion.
4. `NO_FEASIBLE_TARGET` means the target could be ruled out for the entire admitted grid, including at zero when appropriate. `INSUFFICIENT_FUNDS` uses an independent lower certificate when baseline federal obligations exceed all executable assets plus existing tax cash. The latter proof is intentionally conservative. Some more complex whole-domain cash failures can still return a proved target miss without a separate general insolvency assertion. `UNSUPPORTED` supplies no candidate.

## 14. Budget and termination

`AR-TARGET-P3A-1` caps the run at 512 outer interval visits, 160 income boxes for one interval, 8,192 income boxes in total, and 4,096 accepted P2 inner pricing evaluations combined. A local income-box cap means the outer search must subdivide or execute because the box proof is incomplete. A global cap yields unsupported if the needed proof cannot finish. The existing P2 512-evaluation ceiling applies independently to an exact funding pass, subject to the remaining combined budget. Partitions shrink deterministically on integer-cent midpoints. Income boxes shrink to a $0.0000000001 resolution. Neither sparse adjacent trial enumeration nor an unbounded per-cent fallback is used. Recursion and kept portfolios are bounded by the dimensional depth, the frozen source profile, and one retained outer funded trial at a time. The full-conversion scout supplies geometry and does not fund or settle anything.

## 15. Original P2 deferred-funded fixture

The unmodified financial inputs from P2 certify **$8,949.09** of conversion and **$7,550.91** of taxable IRA funding. The original $50,000 ordinary base plus $16,500 total new ordinary income yields $50,400 federal taxable income. Federal liability is $5,800. Arkansas uses one $6,000 pool, $2,470 deduction and $29 credit. State net $58,030 gives $1,750.91. With no other cash and enough remaining IRA assets, the funding obligation at the federal ceiling is exactly $7,550.91. The original fixture finishes with $73,500 traditional and $8,949.09 Roth. A real fixed trial at $8,949.10 after conversion exceeds the federal ceiling with the required funding. Exact target room is zero at the certified result.

For independent arithmetic in this region, the combined liability at allowed Y depends only on Y once the owner pool is saturated. Here the admitted federal slopes plus Arkansas slope are below one. The required W at the ceiling leaves at most $8,949.09 for conversion. The more general interval proof also checks lower income portions, legal exclusions and all greater cents. The cash-backed P2 version retains its $16,500 exact candidate.

## 16. Independent exhaustive comparisons

The small-domain oracle independently enumerates 0.01 increments through at most $0.50 and, when applicable, the exact terminal. It uses the real converter and unchanged P2 funding authority for every request, without calling the outer optimizer or using its partition logic.

- The two-owner current-RMD fixture covers a disconnected set. At the federal cap, C = $0.02 still lands on the lower-tax side of the $94,701 transition. C = $0.03 through $0.09 end in the other region. At $0.10 the recipient pools move the state taxable base back to the favorable side. The exhaustive oracle agrees with the optimized maximum of $0.25 and, when the source cap ends in the gap, $0.02. That last case requires the optimized search to skip greater physically executable points.
- All 29 adjustment starts and the exit have account-backed short-domain comparisons at adjacent cents, with independent statutory amount assertions. Fractional conversion terminal, Social Security tiers, NIIT and senior-election switch use additional small-domain comparisons.

## 17. Single, MFJ and survivors

The original Single and mixed-age actual MFJ cases prove the target against real source owners. The converter exhausts both primary IRA sources before using the spouse's IRA in one fixture. In a separate actual MFJ fixture the withdrawal order exhausts the spouse's $200 balance before drawing the primary source, while conversion takes primary funds. Both modeled death-order directions are tested in a subsequent survivor Single year with an evidenced direct spouse pension. The survivor's original pension-source facts remain frozen, while its relief goes to the actual living recipient. Inactive account balances are preserved and do not create conversion principal.

## 18. Social Security, NIIT and senior credits

The federal upper proof and liability envelopes use the native federal taxable-benefit, taxable-income and regular-tax functions. Added deferred funding and conversion do not create modeled new investment income. Recognized NIIT components are frozen from the authoritative base, and MAGI and the native rounded NIIT amount are reevaluated. Exact annual federal and Arkansas computations still use the full executed trace. Tests isolate the 50% entry, the transition to 85%, and a $0.01 NIIT threshold rounding result without changing the taxable-income cap. An opening $783.76 RMD and a small own-IRA conversion cross from the tied 65 Special choice to the pooled retirement relief election. Opening RMD is taxable without supplying current cash.

## 19. Conversion and funding source conservation

Each funded test verifies actual same-owner transfers, source and destination identity, correct owner order, real executed withdrawal purposes and the exact conversion-principal transfer. Total investment value before and after conversion is the same. Funding debits the projected portfolio once, and the immutable P2 candidate holds the exact source trace that was priced. The Medicare case rebuilds the spending and conversion base in its second independent pass. The first pass makes no retained deposit. No live balances are changed.

## 20. Account balances and surplus retention

The accepted P2 $86,197 income fixture and original assertions are unchanged. It still funds $16,972.66 of liabilities with the minimal $16,974 withdrawal and deposits the real $1.34 only once. The original grown and spent later-year retained balance, exact fractional terminal, separate RMD cash and frozen surplus persistence continue to pass. Targeted fixtures compare post-conversion and post-funding total projected balances and the independently reconciled final ledger before retaining any positive surplus.

## 21. Measured performance and proof statistics

The test suite records exact outer trial, income box, federal and Arkansas proof-probe counts. Account allocation operations and exact Arkansas trial estimates each correspond to the P2 candidate count when pricing succeeds. The successful P2 candidate path calls the full federal calculator twice per exact funding candidate, once for the first computation and once for P1 consistency. Each P2 lower-bound attempt can add another consistency federal quote. Proof probe counts below are separate from those inner calls. Early unsupported probes may terminate during source reconciliation before a second quote. See the measured run logs for wall-clock timings. Peak retained funded outer trial states was one in each measured case. Inner depth and retained state continue to follow the P2 allocation solver contract.

| Scenario | Outer intervals | Excluded intervals | Exact trials | Inner allocations | Inner bounds | Federal proof | Arkansas proof | Income boxes | Outer depth |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Cash-backed P2 | 0 | 0 | 1 | 12 | 30 | 7 | 0 | 0 | 0 |
| Original deferred P2 | 33 | 11 | 2 | 32 | 76 | 236 | 228 | 195 | 22 |
| Mixed-age two owners | 29 | 8 | 2 | 29 | 70 | 173 | 165 | 136 | 21 |
| Twelve-account deferred | 27 | 8 | 2 | 43 | 75 | 183 | 175 | 148 | 19 |
| Disconnected MFJ, cap $0.09 | 8 | 3 | 2 | 75 | 112 | 281 | 233 | 225 | 4 |
| Proved no target | 1 | 1 | 1 | 15 | 42 | 9 | 2 | 1 | 1 |
| Unsupported low income | 24 | 8 | 2 | 2 | 0 | 124 | 116 | 90 | 17 |

In the final focused run, those seven cases took 11.792, 10.517, 7.282, 7.228, 6.345, 1.519 and 0.426 milliseconds in table order. The second disconnected fixture, with capacity $0.25, took 1.390 milliseconds. These measurements are individual test calls on this Windows workspace and include their respective solver work.

## 22. Supported and unsupported scope

| Tested area | Result |
| --- | --- |
| Ordinary fully taxable own-IRA conversion with IRA tax funding | Certified in the original Single, actual MFJ, mixed age and multi-account fixtures |
| Cash, retained assets, basic brokerage principal, mixed tax character and terminal Roth funding | Certified with real strategy source order and cash reconciliation |
| Owner-specific eligibility, different accounts, RMDs, survivor treatment | Certified for the documented dated/source scenarios. Unknown legal facts retain typed refusal |
| Ordinary employers | The solver can bound their admitted cash withdrawals. The existing Arkansas P1 refusal for employer-to-Roth and specialized conversions still applies |
| Early own-IRA rules | Completed conversion is included. An uncertain receipt interval is refused when material and not otherwise excluded. A separately evidenced later interval can certify |
| No Roth destination or capacity | Certified zero when the funded baseline meets the ceiling. Actual conversion shortfall retains the fixed-path semantics |
| Federal target too low or funding pushes it past the target across the grid | Certified target miss |
| All cash cannot cover the baseline federal tax | Certified insufficient funds |
| Unsupported P1 tax, unexpected source basis/classification, proof budget or unknown ordering | Structured unsupported unless an independent necessary-condition certificate rules the region out |

Ordinary deferred-funded bracket-fill is now certified across the tested ordinary Single and MFJ source profiles, including the formerly refused P2 case. This is broader support than the prior cash-backed subset. It is a bounded proof service, so a supported tax/source case still needs the specific annual proof to finish within budget.

## 23. Changed-file manifest

Paths using package shorthand are under `com/daviddunn/retirementplanner/`. Changes are relative to the starting staged Phase P2 baseline.

| Path | Change |
| --- | --- |
| `src/main/java/.../domain/tax/ArkansasRothFunding.java` | Replaces the 32-adjacent-cent targeted path with descending certified partitions, typed outcomes and metrics. Fixed conversion behavior is preserved |
| `src/main/java/.../domain/tax/ArkansasTargetBounds.java` | Federal upper ceiling, conversion/funding topology and exclusion-only income boxes |
| `src/main/java/.../domain/tax/ArkansasAnnualFunding.java` | Package-visible reuse of the P2 evidence normalizer for the unconverted proof base |
| `src/main/java/.../domain/tax/state/ArkansasProductionTaxCalculator.java` | Translates normalized P1 source facts and age evidence into a state-only lower-envelope callback |
| `src/main/java/.../domain/tax/state/arkansas/ArkansasJointWorksheet.java` | Applies P1 exclusions, Arkansas credit choices and state discontinuities to outer proof bounds |
| `src/test/java/.../domain/tax/ArkansasAccountFundingTest.java` | Upgrades original cash/deferred target assertions to the certified results. Non-targeted P2 tests are unchanged |
| `src/test/java/.../domain/tax/ArkansasTargetCertificationTest.java` | Independent small outer oracles and adversarial account-backed coverage |
| `PHASE-6E-P3A-ARKANSAS-ROTH-OPTIMALITY-CERTIFICATION.md` | This report |

## 24. Focused verification

Used Java 25 and IntelliJ's bundled Maven with the workspace-local `.codex-m2/repository`. The isolated optimizer tests cover 17 new cases and all 16 prior account-backed cases. Focused regression also covers P1 and P2, the unchanged solver, retained settlement and snapshot history, Alabama, Michigan, other state goldens, the production registry, and the legacy Roth conversion tests. Logs are `target/phase6e-p3a-oracles.log` and `target/phase6e-p3a-focused.log`.

The final oracle/account run passed all 33 cases. The focused run completed November 18, 2026 at 18:06:49 EST with **623 passed, zero failures, zero errors and zero skips**.

## 25. Bounded regression

The established bounded filter is `*,!*Benchmark*,!*CapacityProbe*,!*Performance*,!*Exhaustive*,!*Search*`, logged at `target/phase6e-p3a-bounded.log` using the same JDK and Maven. No unrestricted strategy or analysis suite was launched.

The bounded run completed November 18, 2026 at 18:16:03 EST. It discovered 5,442 cases, with **5,407 passed, 35 skipped, zero failures and zero errors**. All 127 Arkansas cases ran and passed, including the 17 new cases. The skip count is identical to the P2 baseline.

## 26. Existing-state financial invariance

The enum and production tax service retain the original 20-state registry. The existing bracket-fill, conversion execution, national tax/rules, projected withdrawal, ledger, projection orchestration, persistence, analysis and exports carry no P3A edits. No prior monetary fixture changed. The public domain methods and any synthetic state carrier are unreachable through the production projection router.

## 27. Remaining limitations

- Initial Arkansas scope remains the P1/P2 bounded ordinary model. No final expanded low-income table, new senior deduction, liquidation gain model, tax-withholding transaction, inherited/legal conversion basis or specialized employer conversion rule was implemented.
- Funding from Roth balances uses P2's existing modeled tax character. P3A adds no new qualification assumption, clock or basis determination for Roth withdrawals.
- The geometric certificate intentionally relaxes uncertain source distributions. Some complex profiles may exceed proof budgets. A missing certificate or material unknown source may not be converted into a valid estimate, default conversion or funded analysis result.
- The controlled tests call the real account and tax services outside normal application routing. Neither actual Arkansas admitted analyses nor long-running Arkansas Monte Carlo have been verified.
- Targeted performance values are test measurements, with no interactive or Monte Carlo latency guarantee. Additional production-path characterization and choice mapping remain required.

## 28. Proposed Phase P3B integration gates

Obtain explicit authorization, then wire Arkansas-only yearly dispatch and structured result mapping. Preserve RMD/spending, independent Medicare repricing, actual source trace commitment and the exact no-candidate treatment. Check deterministic future yearly and survivor paths, representative shared-path Monte Carlo and bounded Social Security outcomes without treating unknown as ruin. Validate source policy, working-file state, historical results, export detail, versioning and supported state/funding reference keys. Require independent admission approval before registering Arkansas. Colorado stays disabled.

## 29. Native Windows acceptance

After admission wiring is separately approved, reproduce both original cash/deferred fixtures, the $1.34 retained result, qualified and unavailable source evidence, unreachable and unfilled targets, and ordinary multi-owner Roth/withdrawal ordering in a native Windows build. Verify current and baseline result reads after Save As and restart and confirm frozen PDF/CSV/report totals. Exercise supported and unsupported notices and stable results in Light and Dark. P3A's test-only service has no Arkansas production route to accept natively.

## 30. Git verification and approval request

The closing HEAD matches the starting commit. The full index-entry comparison matches the staged starting snapshot. `git status --short --untracked-files=all` shows the opening staged work and the eight unstaged or new P3A files in the manifest. `git diff --check` passed, along with UTF-8 and trailing-whitespace checks of the new files. No stage, commit, push, tag, reset or history change occurred. Phase 6E-P3B requires separate explicit user approval.
