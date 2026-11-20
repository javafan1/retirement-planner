# Phase 6E-P3D2G — Arkansas Roth target source reachability

## 1. Executive summary

The Arkansas Roth target proof retains the existing Arkansas tax lower bound when the federal target proves an inherited deferred source unreachable. The synthetic failure now certifies the independent monetary optimum with **42 of 4,096** inner evaluations. A separate read-only replay completed and matched an independent local check for the previously failing pass.

New annual evidence uses `AR-TARGET-P3D2G-1`. Older Arkansas target and funding tuples remain historically readable. Search budgets, monetary formulas, inherited IRA eligibility, source order and the optimizer's exact objective are preserved. The final bounded suite recorded 5,506 passes, 35 skips, and zero failures or errors.

## 2. Git baseline and preserved pending work

`main` opened at `74a78ae783727aa35012e5265f774f553c89a705`, one ahead of the local `origin/main`, with an empty index. Opening status contained 13 modified tracked files and nine individual untracked files. The pending P3D2C UI changes, P3D2E funding and provenance changes and P3D2B–F reports formed the baseline. No earlier files were removed or restored. This phase extends the existing provenance allowlists and tests in place. P3D2F's private saved-input copy and diagnostics remain ignored under `target/`. [P3D2A](PHASE-6E-P3D2A-ARKANSAS-RELEASE-INTEGRITY.md) · [P3D2C](PHASE-6E-P3D2C-ARKANSAS-PROJECTION-FAILURE-UI.md) · [P3D2D](PHASE-6E-P3D2D-ARKANSAS-FUNDING-BUDGET-AUDIT.md) · [P3D2E](PHASE-6E-P3D2E-ARKANSAS-CAPPED-EXCLUSION-PROOF.md) · [P3D2F](PHASE-6E-P3D2F-ARKANSAS-ACTUAL-PLAN-FUNDING-DIAGNOSTICS.md)

## 3. Verified original failure mechanism

P3D2F reproduced the failure with `AR-FUNDING-P3D2E-1` and `AR-TARGET-P3A-1`. The portfolio-wide source gate suppressed the Arkansas bound even when an unreviewed deferred funding source was outside the federal combined-income ceiling. With only the federal relaxation, 185 exact trials were funded but exceeded the target. The 186th consumed the seven evaluations remaining in the shared 4,096 allowance. All 19 of its inner interval bounds were available. The exact limits and error typing returned `TARGET_PROOF / FUNDING_BUDGET`.

## 4. Mathematical reachability certificate

The following obligations were documented before modifying the target proof.

Both approved withdrawal strategies take retained household cash first. Taxable-first then uses cash, brokerage, deferred and Roth. Deferred-first takes deferred before persisted cash and brokerage, then Roth. Equal-priority accounts use stable portfolio order. Conversions use primary then spouse, and persisted account order within each owner, with active owners and same-owner destinations. Execute the same full-conversion probe and retain the existing guard that every conversion source is a reviewed own IRA. That guard precludes reaching the inherited source by a distinct conversion route. A reviewed own IRA can convert after the inherited account in withdrawal order. Its conversion stays in the tax envelope, while its later funding debit is excluded only when the inherited source is proved unreachable. Other unreviewed source categories keep the old fallback. [Allocator](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectedWithdrawalAllocator.java#L345) · [Converter](src/main/java/com/daviddunn/retirementplanner/domain/roth/ProjectedPortfolioRothConverter.java#L84)

Let `P` be the unrounded sum of reviewed ordinary deferred balances in funding order before the first positive inherited deferred balance. Funding can reach the inherited account only after depleting the ordinary balances. If a conversion takes `Cprefix` from these balances, reaching the inherited account requires ordinary funding of at least `P − Cprefix`. All these modeled ordinary deferred debits are fully pretax and the existing exact authority rejects unrepresented basis or character. All reviewed conversions contribute at least `Cprefix` federally, including conversions whose funding position is after the inherited source. Hence every candidate reaching it requires combined taxable inclusion of at least `P`. Confirm `P` is **strictly above** the existing outward federal combined upper envelope before arming the Arkansas floor. This comparison uses decimals, with equality and unresolved domains keeping the existing fallback. [Fully taxable modeled debits](src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java#L181) · [Exact tax evidence and inherited refusal](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasIncomeReconciliation.java#L158) · [Federal upper envelope](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasTargetBounds.java#L116)

The upper envelope uses the frozen year and filing status, ordinary income, modeled standard deduction, and monotone federal Social Security inclusion. It is found independently of Arkansas liability. The portfolio and annual tax input already incorporate the actual RMD and spending debits. A second Medicare pass builds a fresh portfolio and seed from its own spending, making the certificate independent per pass. The proof uses no cent-rounded balance threshold. It applies to fractional conversion and withdrawal terminals and supports changing marginal sources within the reachable prefix. The all-deferred-exhausted branch is outside the certified domain because the full deferred total is at least `P`. Cash before the prefix helps cash feasibility but contributes nothing to `P`. For the state envelope, only reachable ordinary sources contribute funding receipts. Conversions from reviewed own IRAs outside that funding prefix still contribute their genuine recipient-specific conversion receipts. Neither the portfolio nor conversion/funding attribution will change. [Projection staging and second pass](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java#L897) · [Existing target necessary condition](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasTargetBounds.java#L139)

The implementation records the first positive inherited traditional IRA boundary in the configured withdrawal order. It retains the full deferred total for the original cash and terminal tests. The taxable-inclusion ceiling uses the established monotone Federal calculation with outward bisection. Only the state recipient envelope is restricted to covered sources. The full conversion probe still verifies original own-IRA eligibility, owner and destination, and any other unreviewed source condition leaves the federal-only path. Within a covered search box a later own IRA can contribute its genuine conversion receipts to that recipient's Arkansas pool. Its funding is zero, as is any funding from the inherited or later accounts. This restriction holds throughout the search domain, including singleton exclusions after an unsupported exact pricing trial. A funding candidate is still priced and settled exactly by the existing evaluator. [Reachability gate](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasTargetBounds.java#L125) · [Source envelope](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasTargetBounds.java#L203) · [Exact pricing](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasAnnualFunding.java#L200)

## 5. Source files changed

| File | Phase change |
| --- | --- |
| [ArkansasTargetBounds.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasTargetBounds.java#L66) | Capture the ordinary withdrawal prefix, check the strict federal ceiling, and count only possible funding or conversion receipts in the existing state envelope. |
| [ArkansasRothFunding.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasRothFunding.java#L150) | Apply the optional covered-state proof after the federal ceiling and identify the new target proof. |
| [RetainedProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java#L143) | Add one explicit policy tuple to the annual reader. |
| [ArkansasAnalysisProvenance.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ArkansasAnalysisProvenance.java#L65) | Add the same approved tuple to the v1/v2 provenance check. Existing dependency and source checks apply. |
| [ArkansasSourceReachabilityTest.java](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasSourceReachabilityTest.java#L71) | Add 16 persistent reproduction, bounds, owner, survivor, and cancellation cases. |
| [ArkansasFundingProvenanceTest.java](src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ArkansasFundingProvenanceTest.java#L30) | Extend P3D2E coverage to targeted tuples, original dependencies, digest, and freshness. |
| [ArkansasProjectionIntegrationTest.java](src/test/java/com/daviddunn/retirementplanner/domain/projection/ArkansasProjectionIntegrationTest.java#L120) · [ArkansasValidationAnalysisTest.java](src/test/java/com/daviddunn/retirementplanner/domain/projection/ArkansasValidationAnalysisTest.java#L290) | Check the current target identity and keep the invalid-version tamper test tied to the actual emitted value. Financial goldens are unchanged. |
| This report | Record reasoning and verification. Replay, timing and standalone archive helpers remain under ignored `target/phase6e-p3d2g/`. |

## 6. Target proof-version decision

Before the solver edit, the policy decision was recorded here. Target intervals, bounds, and exact evaluation counters enter retained annual canonical bytes. A proof change can therefore alter a digest while the monetary outcome is identical. Under the same proof identity, an earlier result could appear current but fail a new analyzer's full-digest comparison. Target proof identity is the narrow available distinction. The funding and tax proof identities remain unchanged.

| Annual tuple funding ID | Annual tuple target ID | Treatment |
| --- | --- | --- |
| `AR-FUNDING-P2-1` | `AR-TARGET-P3A-1` | Read original money as historical. |
| `AR-FUNDING-P3D2E-1` | `AR-TARGET-P3A-1` | Read P3D2E money as historical. |
| `AR-FUNDING-P3D2E-1` | `AR-TARGET-P3D2G-1` | Current proof when other dependency conditions match. |

All tuples require `AR-2026-P2-1`. Annual evidence uses schema three as before. V1/v2 retain the target key when applicable and continue validating original UUID, input digest, annual digest and the policy on the exact dependency. Untargeted analyzer dependency policy maps still omit the target key. Full projection freshness uses the recorded full tuple, including on previously accepted untargeted Arkansas projections. Old software versions only know their previously approved tuples. No format or archive migration was required. [Annual policy tuple and metrics](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java#L143) · [Projection freshness](src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java#L173) · [Dependency validation](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ArkansasAnalysisProvenance.java#L505) · [Durable history contract](PHASE-6E-P3C2-ARKANSAS-DURABLE-HISTORY-PROVENANCE.md#10-arkansas-rule-version-identity)

## 7. Synthetic red/green reproduction

The permanent test uses Basic, a joint 2028 year, zero growth and indexing, two $250,000 own traditional IRAs, an empty primary Roth, a documented $7,000/month employer pension, $90,000 annual expense and one-time 22% fill. One variant adds a $1,000 spouse-owned inherited traditional IRA after the own IRAs. The test ran before the proof edit. The ordinary case passed. The inherited case produced the expected 2028 exception in the first Maven run. Both variants now complete with the same requested and executed sums, original source references and identical proof counts. The inherited account's identity, owner, beneficiary relationship and value are retained. No distribution, conversion or RMD debits it. [Permanent production test](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasSourceReachabilityTest.java#L71) · [Red run](target/phase6e-p3d2g/red.log) · [Green proof log](target/phase6e-p3d2g/focused.log)

## 8. Independent monetary and optimality oracles

Pretax ordinary modeled income before the conversion and tax-funding debits is $90,000, including $6,000 of retirement withdrawals for spending. The combined target ceiling is `$211,400 + $32,200 − $90,000 = $153,600`. Independent federal slices give `$24,800 × 10% + $76,000 × 12% + $110,600 × 22% = $35,932.00`. Arkansas net income is `$90,000 + $153,600 − $6,000 − $4,940 = $232,660`. The high schedule after credits is `$232,660 × 3.7% − $79.90 − $58`, which rounds to **$8,470.52**. Exact funding **$44,402.52** and executed conversion **$109,197.48** sum to the ceiling. The previous funding cent at that conversion has a positive **$0.0078** deficit after recomputing both taxes.

For any higher cent conversion in this fixture, combined income above the ceiling fails the federal target. At the ceiling at least a cent of funding is displaced. At an amount `k` combined cents below the ceiling, funding loses a further `k` cents. The checked rate region still has a 22% federal marginal rate and the Arkansas high schedule beyond its adjustment bands. Federal tax can fall by `0.22k` cents and rounded state tax by at most `ceil(0.037k)` cents. The deficit is strictly positive for `k > 0` because `1 + k − 0.22k − ceil(0.037k) ≥ 0.743k`. For `k = 0` the deficit is at least one cent. The fixture's ordinary capacities and retained statutory policies rule out a fractional conversion terminal at this higher threshold. This is a proof on the synthetic region, independent of candidate traversal. Tests bind both funding and executed conversion to the primary ordinary IRA, and the conversion destination to the same owner's Roth. [Oracle and source assertions](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasSourceReachabilityTest.java#L81)

## 9. Source-boundary negative tests

- Tested a zero prefix and inherited funding first, reachable deferred prefixes, strict equality, both immediate target directions, and decimal balances a tenth of a cent either side of the boundary. The state floor activates only with strict separation.
- Verified that retained household cash, persisted cash and brokerage assets do not enter the pretax prefix in either approved withdrawal order. The fixture includes two inherited owners and balances.
- Exact fixed-zero, fixed-positive and targeted pricing through an actually reachable inherited distribution retain `RETIREMENT_SOURCE`, with no funded candidate. A portfolio with potential employer conversion retains the old fallback even with an otherwise unreachable inherited slot.
- The synthetic full upper scout consumes part of the ordinary prefix. A separate source-order test converts an ordinary account after the inherited funding position while funding from the earlier spouse account. The after-boundary conversion retains its separate owner's eligibility.
- The existing suite covers additional-tax or uncertain timing exclusions, account-type restrictions, finite fractional terminal routing and zero-capacity funding. A standalone countercheck supplied a prior ordinary debit with absent taxable evidence or an explicit taxable amount less than gross. Even with a provably unreachable inherited source, both cases returned typed `BASIS`, with no trial acceptance or Arkansas outer bound. [Budget and boundary tests](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasSourceReachabilityTest.java#L141) · [Funding exact tests](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasAccountFundingTest.java) · [Roth coverage](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasTargetCertificationTest.java) · [Exact inherited refusal](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasIncomeReconciliation.java#L158) · [Basis countercheck](target/phase6e-p3d2g/basis-countercheck.log)

## 10. Single, couple and survivor evidence

Single-person boundary and reachable-source tests cover either withdrawal strategy. Joint production cases verify a spouse-owned inherited account, and an additional production case puts a primary-owned inherited source between the funding IRA and the primary's conversion IRA. That case keeps the two recipients' independent exclusions, preserves both owner receipts and matches the no-inherited monetary control. Both death directions exercise joint filing through the death year, Single in the following year, original employer pension owner and surviving payee, and a new target calculation with the survivor's destination. Complementary focused tests cover saturated and partially used pools, taxable Social Security, funded Medicare premiums, opening RMD provenance, and exact IRMAA/second-pass changes. [Owner order](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasSourceReachabilityTest.java#L191) · [Survivor tests](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasSourceReachabilityTest.java#L208) · [RMD and Medicare engine tests](src/test/java/com/daviddunn/retirementplanner/domain/projection/ArkansasProjectionIntegrationTest.java)

## 11. Read-only replay verification

The replay used the detached input preserved during P3D2F. Integrity checks matched before and after. The production engine reported `AR-TARGET-P3D2G-1`, `AR-FUNDING-P3D2E-1` and `AR-2026-P2-1`. The complete projection returned successfully. An independent pre-fix calculation matched the previously failing provisional pass, and the subsequent premium-aware pass completed.

The replay ran outside the owner-thread acceptance pipeline and published no result. It preserved retained evidence and performed no save. The evidence is limited to the detached saved input.

## 12. Exact Roth and funding proof counts

| Path | Exact Roth trials | Cumulative inner exact prices | Inner bounds | Outer visits | State outer calls | Income boxes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Synthetic ordinary control, before and after | 2 | 42 | 94 | 31 | 183 | 152 |
| Synthetic inherited, before | 189 | 4,096 | 9,798 | 417 | 0 | 336 |
| Synthetic inherited, after | 2 | 42 | 94 | 31 | 183 | 152 |
| Read-only replay, initial pass, before | 186 | 4,096 | 11,025 | 405 | 0 | 252 |
| Read-only replay, initial pass, after | 2 | 40 | 97 | 35 | 226 | 191 |
| Read-only replay, premium-aware pass, after | 2 | 40 | 97 | 31 | 170 | 139 |

Each successful replay pass used 19 candidates, 45 attempted bounds and depth 27 for the funded candidate, against the 512 local allowance. The new proof runs once per pass using the portfolio after RMD, spending and existing-cash treatment.

## 13. Budget and financial invariance

The exact-limits set remains **512** candidates per funding pass, **4,096** cumulative inner candidates, **512** outer visits, **160** income boxes per outer interval and **8,192** total. The inner traversal, descending outer order, highest funded executed conversion objective and exact final state/federal price and cash settlement use the same methods. The original estimator still rejects inherited, special eligibility and unestablished basis where relevant. A covered account remains in the original portfolio and exact account order. A refusal may still occur when the necessary condition fails to prove exclusion within the fixed limits. [Funding solver maximum](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasMinimumFundingSolver.java#L26) · [Roth counter enforcement](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasRothFunding.java#L199) · [Income box limits](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasTargetBounds.java#L29)

## 14. Historical v1/v2 archive compatibility

The permanent legacy tests exercise original P2 and P3D2E tuples, exact re-encoding, equal monetary rows with different annual evidence digests across proof versions, original UUID/fingerprint retention, targeted v1 round trip, version mismatch refusal and the invalid hybrid tuple. A separate scratch writer used the saved pre-change P3D2E production class tree to accept two genuine synthetic targeted projections in distinct roles. The revised build restored their exact bytes and policy, preserved them through Save As, reopen, ordinary Save and both role swaps, and classified them as historical. The synthetic source artifact hashes remained equal. A separate existing-artifact replay reopened P3D2A v1 Quick, v2 weighted and v2 paired archives. It verified four projection role entries and six analyses, unchanged annual and analysis digests and original bindings, Save As and reopen on each archive, and Save and both swaps for paired. All 13 original files still match the earlier recorded SHA-256 manifest. [Permanent targeted history](src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ArkansasFundingProvenanceTest.java#L30) · [Old-target fixture creation](target/phase6e-p3d2g/target-history-write.log) · [Old-target replay](target/phase6e-p3d2g/target-history-reopen.log) · [Old v1/v2 archive replay](target/phase6e-p3d2g/legacy-history.log)

An older Baseline can be read, compared historically and swapped as stored. A new paired financial analysis requires current approved source evidence for each dependency. Recalculate a matching Arkansas Baseline before reusing it in a new current-policy paired run. The archived policy and values stay in their original context.

## 15. Analyzer integration

The focused integration command ran real JavaFX launchers for Arkansas fixed and longevity-adjusted Monte Carlo, fixed Arkansas/Arkansas pairing, and longevity pairing with Arkansas/Texas and Texas/Arkansas in their original role orders. It covers equal inputs with shared or distinct projection UUIDs, temporary survivor elections, exact dependency order and post-swap historical association. Integrated Social Security Quick and individual deterministic and mortality launchers passed. Bounded longevity-weighted real-engine tests ran actual candidate and survivor continuations. Normal fixed and bracket target/Roth comparison coverage ran through focused domain and production projection tests. P3D2E ordinary-stage typed failure and cancellation tests remained in the run. No full 5,184-strategy workload was required for the source-domain proof obligation. [Paired launcher modes](src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/ArkansasMonteCarloLaunchTest.java#L51) · [Longevity real engine](src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/ArkansasLongevityValidationTest.java#L31) · [Integration results](target/phase6e-p3d2g/integration.log)

## 16. Cancellation and responsiveness

The new optional preparation checks the existing token before arming the bound. A focused test cancelled at that call, verified the assets remained unchanged and reran successfully. That fixture measured 2.025 ms. The existing deferred target interval fixture measured 13.284 ms and the controlled JavaFX revocation fixture logged 0.360 ms. Analysis cancellation, staleness/supersession and last-success archive behavior remain covered on the existing worker paths. These are individual observations without a GUI latency SLA. [New cancellation test](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasSourceReachabilityTest.java#L240) · [Measurement log](target/phase6e-p3d2g/focused.log)

The separate before/after performance runs used the same fixtures, Temurin 25.0.4.1, `-Xmx384m`, five warmups and 12 samples per variant. The ordinary control median was 2.877 ms before and 6.911 ms after, with unchanged operation counts. The inherited perturbation was refused in all 12 earlier samples and accepted in all 12 later samples. Its min/median/max elapsed measurements were 80.603/98.096/118.199 ms before and 3.739/7.737/11.120 ms after. Maximum sampled post-call used heap was 229,711,392 bytes before and 163,371,232 bytes after, with two workloads mixed in each JVM. These are not peak live-heap measurements. Warmup work substantially differed because the earlier JVM processed thousands of exact prices for inherited variants. Runs shared a workstation with regression activity. The comparable work-count result is **4,096 → 42** exact inner prices for the inherited synthetic fixture under the unchanged limit. The uninstrumented read-only replay took 618.481 ms end to end. [Performance driver](target/phase6e-p3d2g/ReachabilityPerformance.java) · [Before timing](target/phase6e-p3d2g/perf-before.log) · [After timing](target/phase6e-p3d2g/perf-after.log)

## 17. Existing-state monetary invariance

The scope is the Arkansas outer proof and Arkansas retained policy allowlists. Production-state dispatch, Alabama annual goldens, Michigan tax, current conditional-benefit financial integration, Roth eligibility and execution and RMD aggregation passed the targeted suite. Existing federal, state-tax, cash-flow, Medicare/RMD, seeded stochastic and golden regressions are part of the bounded run. The 20 earlier production states, Arkansas and Colorado's unavailable status retain the existing admission rules. No golden or pricing formula changed. [Production state tests](src/test/java/com/daviddunn/retirementplanner/domain/tax/state/ProductionStateTaxServiceTest.java) · [Integration log](target/phase6e-p3d2g/integration.log)

## 18. Focused and bounded regression results

The following Java 25 and IntelliJ Maven command ran each selector below. Local profiles for the first four runs used `target/phase6e-p3d2g/{profile,localappdata}` for `user.home` and `LOCALAPPDATA`. The bounded run used `bounded-profile` and `bounded-localappdata` at the same parent. The test runtime and compilation used the project-local cache.

```powershell
$m = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd'
$repo = "-Dmaven.repo.local=$((Get-Location).Path)\.codex-m2\repository"
$env:JAVA_HOME = Split-Path (Split-Path (Get-Command java).Source)
& $m $repo "-Duser.home=$profile" -o -B "-Dtest=$selector" test
git diff --check
git diff --cached --check
```

| Run | Discovered | Passed | Skipped | Failed | Errors | Exit | Maven time |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| [Expected red reproduction](target/phase6e-p3d2g/red.log) | 2 | 1 | 0 | 0 | 1 | 1 | 14.353 s |
| [Initial green focused](target/phase6e-p3d2g/focused-initial.log) | 177 | 177 | 0 | 0 | 0 | 0 | 29.629 s |
| [Final focused proof and controlled workflow](target/phase6e-p3d2g/focused.log) | 192 | 192 | 0 | 0 | 0 | 0 | 21.577 s |
| [Integration, history and monetary invariance](target/phase6e-p3d2g/integration.log) | 243 | 243 | 0 | 0 | 0 | 0 | 1 min 23 s |
| [Final bounded regression](target/phase6e-p3d2g/bounded.log) | 5,541 | 5,506 | 35 | 0 | 0 | 0 | 9 min 22 s |

Exact `$selector` values in run order, with the initial and final focused use of the second line.

```text
ArkansasSourceReachabilityTest
ArkansasSourceReachabilityTest,ArkansasTargetCertificationTest,ArkansasAccountFundingTest,ArkansasFundingCounterexampleTest,ArkansasFundingArchitectureTest,ArkansasCappedExclusionProofTest,ArkansasFundingProvenanceTest,ArkansasProjectionIntegrationTest,ArkansasValidationAnalysisTest,ArkansasControlledWorkflowTest,ArkansasDomainCalculationTest,ArkansasWorksheetTest
ArkansasMonteCarloLaunchTest,ArkansasLongevityValidationTest,ArkansasSocialSecurityLaunchTest,ArkansasIndividualAnalyzerLaunchTest,ProjectionSnapshotPersistenceTest,ProjectionSwapTest,ArkansasHistoryUiTest,ArkansasWorkflowUiTest,Phase12ECodecTest,Phase12EAssociationTest,Phase12FAnalyzerSwapTest,Phase12GFreshnessTest,ProductionStateTaxServiceTest,AlabamaAnnualGoldenTest,MichiganTaxCalculatorTest,ConditionalBenefitFinancialIntegrationTest,ProjectedPortfolioRothConverterTest,RothEligibilityExecutionTest,RothEligibilityFinancialTest,AccountTaxTreatmentTest,RmdBalanceAggregatorTest,MultiOwnerRothConversionProjectionTest,RothConversionProjectionTest
*,!*Benchmark*,!*CapacityProbe*,!*Performance*,!*Exhaustive*,!*Search*
```

The saved replay, native startup, archived read/write and standalone performance processes exited zero. Additional diagnostic counts are excluded from Maven test counts. The pre-change runtime was copied at the red reproduction and independently queried for `AR-TARGET-P3A-1`. The phase scripts used Java classpaths from Surefire, and the standalone harness switched the sole main `target/classes` entry to the preserved `before/classes` for the original calculation. The final tracked and staged diff checks returned zero. The index remains empty. [Process exits](target/phase6e-p3d2g/exits.txt)

## 19. Remaining unsupported boundaries

The source certificate covers a positive inherited traditional IRA only when its actual prior ordinary tax-deferred prefix exceeds the independently obtained ceiling and all other reviewed source conditions hold. Equality, a reachable inherited funding source, and unreviewed potential conversion keep conservative handling. The inherited account receives no tax treatment from the proof. The existing exact evaluator may still refuse an inherited distribution, uncertain IRA basis, inconsistent or specialized source facts, unsupported low-income or pension evidence, or other unproved cash/target cases. There is no fallback approximation. Older frozen results can be viewed without using today's proof. Using their inputs in a new financial analysis requires a valid current acceptance.

## 20. Native Windows acceptance status

The Windows JavaFX startup smoke ran the real `MainApplication` in an isolated synthetic profile and observed its visible Aledrix menu window. Runtime identity reported `AR-TARGET-P3D2G-1`. The targeted UI tests also exercised programmatic production launchers. Native interactive acceptance was pending when P3D2G concluded. The user confirmed that acceptance before the P3D2H checkpoint request.

## 21. Final readiness classification

**Implementation verified in P3D2G.** The read-only replay and bounded suite completed. The user subsequently confirmed native Windows acceptance for the checkpoint.
