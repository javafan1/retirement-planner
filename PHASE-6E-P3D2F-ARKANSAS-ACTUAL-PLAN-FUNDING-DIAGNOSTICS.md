# Phase 6E-P3D2F — Arkansas actual-plan funding proof exhaustion diagnostics

## 1. Executive summary

**Reproduced in an isolated read-only replay.** The replay loaded `AR-FUNDING-P3D2E-1` and reproduced `TARGET_PROOF / FUNDING_BUDGET`. Parity with unsaved live inputs and the earlier process's loaded classes remained unverified.

The outer Roth target proof gated the Arkansas tax lower bound on the entire portfolio. An unreviewed source could disable the bound even when the federal target proved that source unreachable. With the Arkansas component absent from the necessary cash test, 185 conversions reached exact funding and exceeded the federal target. The 186th trial began with seven of the 4,096 shared inner candidates left and exhausted them. All 19 of that trial's Arkansas interval bounds were available. The recorded path exhausted the shared allowance. The capped-exclusion guard and the full 512-candidate local limit were separate.

An independent decimal check and exact detached quotes confirmed a feasible local point within the federal target. The replay did not publish an accepted projection. An independently constructed synthetic fixture reproduced the mechanism by adding a single unreachable inherited deferred account. Production sources, budgets, policy identities and persisted evidence were unchanged in this phase.

## 2. Git baseline

`main` at `74a78ae783727aa35012e5265f774f553c89a705`, one commit ahead of the local `origin/main`. The index was empty. The P3D2A checkpoint is committed. The P3D2B and P3D2D reports, P3D2C presentation code, and P3D2E funding, provenance and test changes were present in the working tree. Opening status showed 13 modified tracked paths and eight untracked files after expanding directories. This phase adds this report and ignored scratch diagnostics under `target/phase6e-p3d2f/`. Earlier work remained in place. [P3D2A](PHASE-6E-P3D2A-ARKANSAS-RELEASE-INTEGRITY.md) · [P3D2C](PHASE-6E-P3D2C-ARKANSAS-PROJECTION-FAILURE-UI.md) · [P3D2D](PHASE-6E-P3D2D-ARKANSAS-FUNDING-BUDGET-AUDIT.md) · [P3D2E](PHASE-6E-P3D2E-ARKANSAS-CAPPED-EXCLUSION-PROOF.md)

## 3. Active runtime proof-version verification

| Runtime evidence | Proof identity and class origin | Result |
| --- | --- | --- |
| Uninstrumented read-only replay | Runtime policy map reported `AR-FUNDING-P3D2E-1`. The solver and state calculator loaded from the current build. | `TARGET_PROOF / FUNDING_BUDGET`. |
| Earlier isolated P3D2A build | Runtime map reported `AR-FUNDING-P2-1`. Both classes loaded from the P3D2A candidate build. | Same failure classification on the same detached input. |
| Instrumented replay | Runtime map reported `AR-FUNDING-P3D2E-1`. Both observed classes came from the scratch overlay compiled from the current files. | Same failure classification with the bounded summary. |
| Isolated Windows JavaFX startup | The real `MainApplication` reached a visible Aledrix window with its menu. The smoke probe printed the active P3D2E identity and the overlay class origin. | Verified the opt-in launch line in an isolated profile. |

The project selects Temurin 25 and JavaFX 25. The current Maven bytecode reproduced the failure. The earlier process's loaded class location remained unverified. [Project settings](.idea/misc.xml#L11) · [JavaFX POM entry](pom.xml#L95)

## 4. Exact failure classification

The initial annual pass failed before the Medicare recalculation. The terminal trial was in the descending exact-singleton search after the upper-end scout. `TARGET_PROOF` identifies the annual route and `FUNDING_BUDGET` identifies the inner reason. `TARGET_BUDGET` is the distinct outer-limit result. The reduced inner allowance ran out inside the terminal call, and the fallback necessary-condition test could not exclude that singleton.

## 5. Read-only replay and evidence limits

The loader read a byte-for-byte detached copy for the replay. Local integrity checks verified the source and copy before and after. The replay made no writes or result associations. Evidence about unsaved live inputs remained unavailable.

## 6. Funding interval characterization

The terminal interval had an available Arkansas bound and insufficient cash at both sampled endpoints. The interval remained inside one marginal segment. Tax liability varied while the elected Arkansas exclusion stayed constant. The precise endpoint evidence remained in the local witness.

The existing outward federal bound can rule out reaching an unreviewed deferred source when the reviewed pretax prefix strictly exceeds that bound. This certificate concerns reachability. Pricing an actual inherited distribution still requires reviewed tax evidence.

## 7. Effective candidate budgets

| Limit or stage | Observed saved replay |
| --- | ---: |
| Maximum exact candidates per funding pass | 512 configured |
| Shared inner allowance across one Roth target pass | 4,096 configured and consumed |
| Shared use before the terminal exact trial | 4,089 |
| Effective limit and consumption in terminal trial | 7 and 7 |
| Outer intervals, available cap | 405 visited of 512 |
| Income boxes, available total cap | 252 of 8,192 |
| Trial number and first scout effort | 186th, first trial used 21 candidates |
| Earlier target misses | 185 fully funded, 20–25 inner candidates each |

The next exact price would exceed the residual allocation. An exact candidate checks cancellation before comparing the counter and increments only when it is allowed to price. The annual adapter therefore correctly reported the inner budget issue without converting it to depletion, shortfall or an accepted result. [Default bound and exact accounting](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasMinimumFundingSolver.java#L24) · [Shared inner accounting](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasRothFunding.java#L218) · [Outer bounds](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasTargetBounds.java#L27)

## 8. Candidate and interval-bound diagnostics

The failed trial tried 19 bounds, pruned six intervals, and split 13 more whose available floor did not exceed maximum cash. It reached depth 14. Across the previous 185 trials the observer counted 3,567 pruned inner intervals and zero unavailable bounds. The full target pass attempted 11,025 inner bounds and reported 12 outer exclusions, 899 federal outer proof calls and zero Arkansas outer proof calls. Maximum outer recursion depth was 25. No non-shared inner cap or box limit was binding.

Existing metrics count exact candidate evaluations and bound attempts. The scratch observer adds source category and owner ordinal to the inner segment, distinct missing-bound reasons, prune counts, cumulative target outcomes, state-bound admission reason, pass kind, cancellation and terminal category. A terminal line is emitted for a targeted year only. Candidate amounts, balances and account labels are absent from the ordinary trace. [Sanitized trace](target/phase6e-p3d2f/trace.log) · [Interval decision](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasMinimumFundingSolver.java#L151) · [Scratch observer](target/phase6e-p3d2f/ArkansasFundingTrace.java)

## 9. P3D2E applicability

The capped-exclusion guard ran throughout the observed inner trials, with no cap-equivalence or missing-bound condition driving the refusal. The outer state-floor factory was never called because the portfolio-wide source precondition failed first. Prior focused tests continued to validate cap saturation and separate-recipient pools.

## 10. Root cause and remaining hypotheses

**Confirmed by the read-only replay and synthetic reproduction.** `ArkansasTargetBounds` accumulated `ordinaryState` over the full conversion and withdrawal profile. An inherited deferred balance anywhere in the sorted portfolio could make the condition false and leave `stateFloor` empty. An isolated call confirmed that the state-floor factory could prepare the bound from the annual base evidence. During search, `taxFloor` supplied only the federal portion of the cash lower bound. Higher conversions could pass that relaxation while exact federal-plus-Arkansas funding pushed taxable income above target. The solver correctly refused when it could no longer certify the next singleton.

**Other explanations.** Both the older and current bytecode reproduced the refusal. The local cap, missing inner bounds, unsupported exact evidence, provisional Medicare and outer traversal limits were excluded as triggers. Neighboring conversion amounts represent different exact pricing contexts. The observer did not establish duplicate exact evaluations.

## 11. Local and synthetic reproduction evidence

The isolated synthetic pair uses a Basic couple, one 2028 year, zero growth and indexing, $250,000 own traditional IRA for each recipient, an empty same-owner Roth destination for the primary, a $7,000 monthly documented employer pension, $90,000 annual spending, and one-time 22% fill. The control has no inherited account. The perturbation adds a $1,000 inherited traditional IRA downstream of the two own IRAs. The ordinary prefix exceeds the federal combined ceiling. No actual plan values were copied to the synthetic fixture. [Synthetic driver](target/phase6e-p3d2f/SyntheticFunding.java)

| Synthetic run | Result | Target proof |
| --- | --- | --- |
| Ordinary control | Completed | 2 exact trials, 42 inner prices, 94 inner bounds, 183 Arkansas outer proof calls |
| One unreachable inherited account added | 2028 `TARGET_PROOF / FUNDING_BUDGET` | 189 trials, 188 funded target misses, 4,096 inner prices, 9,798 inner bounds, zero Arkansas outer proof calls, 11 candidates available at last trial |

The control's exact money and canonical annual digest were identical on the original runtime, the overlay with tracing disabled, and the overlay with tracing enabled. The trace-off run did not append to the log. The perturbation refused in all three modes. P3D2E's distinct saturated-pension regression continued to pass. [Unmodified synthetic replay](target/phase6e-p3d2f/synthetic-unmodified.log) · [Trace off](target/phase6e-p3d2f/synthetic-trace-off.log) · [Trace on](target/phase6e-p3d2f/synthetic-trace-on.log)

## 12. Independent financial checks

**Read-only replay.** An opt-in decimal verifier independently checked the applicable federal and state calculations and the exact cent boundary around a feasible local point. One cent less funding produced a deficit. One cent more conversion at the same funding exceeded the target. This confirmed local feasibility. Global optimality remained a separate solver obligation. The replay published no projection.

**Synthetic oracle.** The modeled combined ceiling is `$211,400 + $32,200 − $90,000 = $153,600`. Federal liability is `$24,800 × 10% + $76,000 × 12% + $110,600 × 22% = $35,932.00`. The Arkansas high schedule uses `$232,660 × 3.7% − $79.90 − $58 = $8,470.52`, rounded after credits. The conversion is `$153,600 − $44,402.52 = $109,197.48`. The selected withdrawal fully funds liability. The prior withdrawal cent keeps the rounded state liability and reduces federal liability by $0.0022, leaving $0.0078 unpaid. For any higher conversion and smaller combined income within the ceiling, cash falls by at least the conversion cent plus the combined-income reduction. Over that range the federal rate is 22% and Arkansas is on the high schedule past its adjustment region. A reduction of `k` combined cents can reduce Arkansas cents by at most `ceil(0.037 × k)`. The resulting cash deficit stays positive. This proof uses the synthetic rate region and assumes no conversion or withdrawal from the excluded source, supported by the independently bounded ordinary prefix.

## 13. Recommended bounded correction

**Prefer a source-reachability certificate for the existing outer Arkansas lower bound.** For the first unreviewed deferred funding source, measure the initial pretax ordinary prefix that would have to be debited to reach it. Conversion can reduce that prefix only by moving dollars already counted as taxable conversion. If `c` dollars of that prefix convert, subsequent funding must debit its remaining amount before accessing the unsupported account. Therefore combined taxable conversion and ordinary funding must include at least the original prefix. Check it using recognized, fully pretax sources and an outward federal bound. A strict prefix above the attainable federal ceiling proves that source unreachable for every target-admissible point. Prove the independent conversion-source prefix and any all-deferred-exhausted or terminal path separately. Establish both before admitting the existing Arkansas bound for a covered rectangle. Keep the original accounts, order and exact evaluators. Equality, a reachable unsupported source, uncertain basis, unsupported conversion flow or uncertain tax input should retain the existing refusal or conservative fallback. [P3A proof contracts](PHASE-6E-P3A-ARKANSAS-ROTH-OPTIMALITY-CERTIFICATION.md#11-funding-source-transitions) · [Existing exact tax refusal for inherited retirement history](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasIncomeReconciliation.java#L160)

| Option | Evidence, proof obligation and impact |
| --- | --- |
| A. Restore the existing bound on a proved reachable source domain | Directly addresses the observed global gate. Use the synthetic pair and independently verify withdrawal/conversion prefix and target ceiling, including equal/fractional boundaries. Conservative pruning can reduce exact work under the same budgets. Moderate implementation scope with shared analyzer effect. Prove compatibility before changing archived proof identity. |
| B. Broaden recipient-equivalent evidence | P3D2E already covers the separate capped-pension case. It has no demonstrated leverage here. Further coverage would require a new recipient-specific cause and statutory/evidence oracle. |
| C. Prune a specific tax discontinuity | Unsupported by this trace as a root-cause correction. Keep existing rate bounds. A separate discontinuity defect would require an interval proof on both sides and an exact cent oracle. |
| D. Remove duplicate work | No identical trial contexts were shown. Different conversion elections and exact funding traces cannot be treated as one. Only a bounded pass-local equivalence with matching evidence could justify reuse. |
| E. Bounded second-stage proof | Defer. It increases complexity and cumulative workload, including in analyzers. It would require its own proof and approved work accounting. |
| F. Retain explicit unavailability | Required fallback for reachable or unproved source domains. Keeps strict funding and target semantics and existing archive compatibility. |

## 14. Historical and analyzer implications

Diagnostics live outside application source, archive serialization, controller commit handling and every policy tuple. The native controller captures frozen inputs, schedules the controlled worker and validates acceptance on the owner thread. The scratch run bypassed publication entirely. Test parity established stable canonical annual bytes on the successful synthetic case with tracing on and off. No reader, UUID, digest rule, state policy or Monte Carlo failure mapping was edited.

A future corrected proof may change operation counts in new results and therefore canonical annual bytes. Review whether to add an Arkansas **target** proof identity while retaining historical v1/v2 and annual format compatibility. Existing Current, Baseline and analyzer objects keep the tuple and digest recorded when accepted. Any new computation must continue distinguishing unsupported tax from proven funding shortage under fixed and stochastic evaluation. Verify paired role attribution in both mixed-state orientations. [Controller worker](src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java#L1087) · [Canonical annual extension](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java#L143) · [P3C2 archive contract](PHASE-6E-P3C2-ARKANSAS-DURABLE-HISTORY-PROVENANCE.md#10-arkansas-rule-version-identity)

## 15. Focused test results

Temurin 25.0.4.1 and IntelliJ Maven used an isolated `user.home` and `LOCALAPPDATA`. No exhaustive, capacity or full weighted suites were executed in this diagnostic phase.

```powershell
$d = (Resolve-Path target/phase6e-p3d2f).Path
$env:JAVA_HOME = Split-Path (Split-Path (Get-Command java).Source)
$env:LOCALAPPDATA = (Join-Path $d 'localappdata')
$m = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd'
$profile = Join-Path $d 'profile'
& $m "-Dmaven.repo.local=$((Get-Location).Path)\.codex-m2\repository" "-Duser.home=$profile" -o -B '-Dtest=ArkansasCappedExclusionProofTest,ArkansasAccountFundingTest,ArkansasTargetCertificationTest,ArkansasFundingCounterexampleTest,ArkansasValidationAnalysisTest,ArkansasFundingProvenanceTest' test
```

Maven reported **60 discovered, 60 passed, zero failures, errors or skips**, exit 0 in 18.094 seconds. The read-only replay, synthetic parity checks, standalone oracle and JavaFX startup are excluded from those counts. The overlay compiled against the Surefire runtime classpath. The synthetic driver ran on both the ordinary and overlay classpaths with tracing disabled and enabled. The replay verifier and its inputs remain local.

## 16. Performance observations

The uninstrumented saved-plan replay took 904 ms from projection start to refusal with P3D2E bytecode and 889 ms in the earlier P3D2A runtime. The instrumented terminal pass took roughly 0.6–0.8 seconds across observations. The synthetic measured cases took about 0.15–0.19 seconds for the control and 0.32–0.38 seconds for the inherited perturbation in scratch JVM runs. These are individual elapsed observations including cold-path effects and varying workstation activity. CPU time and peak heap were not sampled. Headless diagnostic JVMs ran with a 384 MiB cap. There is no established overhead percentile or runtime guarantee.

The 4,096 exact candidates represent 4,096 allocations with tax quotes in the saved trace. From the observed successful quote and reconciliation paths, two full federal calls per exact candidate plus a federal reconciliation per attempted state bound give approximately 19,217 inner federal evaluations, separate from the 899 reported outer federal probes. This is a code-derived work estimate, not a separately instrumented counter. The focused target suite observed 10.175 ms for its existing controlled interval-cancellation fixture. This phase measured no GUI dispatch latency. The live controller executes annual proof and the optional terminal write on its existing worker path. [Exact quote and interval calls](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasAnnualFunding.java#L200) · [Reconciliation](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasIncomeReconciliation.java#L136) · [Cancellation log](target/phase6e-p3d2f/focused.log)

## 17. Privacy and diagnostic limitations

The overlay is compiled under ignored `target/` and is absent from the ordinary build. The trace flag defaults off. The observer emits bounded terminal summaries for a selected diagnostic year, with no per-candidate disk output. The optional verifier keeps exact annual evidence in the ignored local witness. The scratch compile fails if its production anchors drift.

## 18. Reproduction

The independent synthetic control and inherited-source perturbation in section 11 reproduce the mechanism. Run the focused Maven selectors in section 15 against the matching compiler output to verify the permanent cases.

## 19. Explicit design decisions requiring approval

Approve a follow-up only for proving which sources an outer target can reach and using the existing Arkansas bound on that proven domain. Require a conservative fallback at ambiguous boundaries. Exclude inherited distributions from any broader new tax classification. Keep the inner 512 and shared 4,096 limits, existing target objective, exact cash and allocation, and no approximate settlement. A revised proof policy tuple requires separate compatibility review for its archived operation counts. No budget increase or broader admission change is supported by the evidence. Retaining an in-app diagnostic facility beyond the scratch overlay would be a separate scope decision with its own privacy and overhead tests.

## 20. Proposed P3D2G implementation scope

1. Test the synthetic control and unreachable inherited perturbation with the production engine. Pin exact finance and largest eligible conversion with an independent oracle. Trace the same covered-domain certificate against the saved copy locally after approval.
2. Add negative controls for a reachable inherited first or later source, equality at the federal upper envelope, exhausted and fractional prefixes, deferred-first and taxable-first ordering, retained cash and account boundaries, and conversion sourced outside the proved prefix. Keep unsupported evidence distinct from proved shortage. Include no Roth and fixed Roth controls.
3. Cover primary and spouse ownership, survivor transitions both ways, separate cap saturation and partial saturation, taxable Social Security, federal boundary cents, Arkansas discontinuities and second-pass Medicare cash. Preserve cancellation, previous accepted results, and unsupported tax versus genuine modeled ruin.
4. Verify retained annual and v1/v2 history, fingerprints, mixed paired orientations, Save and Save As, role swapping, and bounded fixed/longevity Monte Carlo and integrated analysis. Confirm the old artifacts retain their original digest and meaning. Compare candidate counts, total bound attempts and controlled cancellation without changing any limits or existing-state monetary expectations.

**P3D2F disposition.** The read-only replay and independent synthetic reproduction established the cause under P3D2E. Parity with earlier live inputs and the earlier process classpath remained unverified. Financial correction awaited explicit P3D2G approval.
