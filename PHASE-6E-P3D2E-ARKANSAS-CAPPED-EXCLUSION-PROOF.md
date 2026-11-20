# Phase 6E-P3D2E — Arkansas capped-exclusion funding proof

## 1. Executive summary

The demonstrated synthetic 2028 funding failure is resolved for zero Roth conversion, a fixed $5,000 conversion, and targeted 22% bracket-fill. The exact funding proofs complete in 11, 13, and 13 candidates respectively. The proof recognizes that the documented pension already fills the same recipient's $6,000 exclusion. The Unknown-origin pension keeps its original classification. Exact candidate pricing, statutory cents, withdrawal ordering, the $6,000 limit, and the funding and Roth budgets were preserved. The original reported Windows plan was unavailable for retest.

New calculations use funding proof identity `AR-FUNDING-P3D2E-1`. Pre-change Arkansas annual and analyzer evidence remains readable using its original `AR-FUNDING-P2-1` identity. The new identity separates historical results from current results when changed proof-operation counts affect canonical annual digests.

Final bounded regression passed with 5,489 passing tests and 35 skips. Both focused runs also passed. The archive probe verified original digests and role-swap behavior on pre-change v1 and v2 files.

## 2. Git baseline and preserved pending work

- Branch `main`, local HEAD `74a78ae783727aa35012e5265f774f553c89a705`, one commit ahead of local `origin/main`. The index was empty.
- Seven existing tracked edits belonged to P3D2C in `ConditionalBenefitAdmission`, `ArkansasProjectionWarning`, `MainWindow`, `ProjectionFailureMessages`, `ApplicationController`, `ProjectionFailureMessagesTest`, and `ArkansasControlledWorkflowTest`.
- Existing untracked work comprised the P3D2B, P3D2C, and P3D2D reports, `ProjectionFailureWarning.java`, and `ArkansasProjectionFailureUiTest.java`. All opening edits and untracked items were preserved.
- Reviewed the P2 funding, P3A target, P3C2 durable history, P3D2A release integrity, P3D2C warning and P3D2D audit evidence. The tests in this phase ran against the current working tree including the pending UI work. The performance comparison used the earlier isolated P3D2A build for the original algorithm. [P2 funding](PHASE-6E-P2-ARKANSAS-FUNDING-SETTLEMENT-IMPLEMENTATION.md) · [P3A target proof](PHASE-6E-P3A-ARKANSAS-ROTH-OPTIMALITY-CERTIFICATION.md) · [P3C2 history](PHASE-6E-P3C2-ARKANSAS-DURABLE-HISTORY-PROVENANCE.md) · [P3D2A release integrity](PHASE-6E-P3D2A-ARKANSAS-RELEASE-INTEGRITY.md) · [P3D2C UI](PHASE-6E-P3D2C-ARKANSAS-PROJECTION-FAILURE-UI.md) · [P3D2D audit](PHASE-6E-P3D2D-ARKANSAS-FUNDING-BUDGET-AUDIT.md)

## 3. Original candidate-budget failure

The shared annual adapter reports `FUNDING_PROOF / FUNDING_BUDGET` for direct funding exhaustion and `TARGET_PROOF / FUNDING_BUDGET` when an inner funding exhaustion escapes the Roth target proof. The 2028 reproduction reached the same reported target branch. The synthetic recipient had $24,000 certain and $84,000 possible qualifying pension receipts. Exact state tax was stable because both produced the same exclusion. The earlier interval guard declined a bound because the uncapped totals differed. Lower-first traversal priced consecutive cents until the 512-candidate allowance ran out. The reported stack establishes the failure category. The synthetic reproduction checks the capped-exclusion mechanism independently. [Annual validation](src/main/java/com/daviddunn/retirementplanner/domain/projection/ArkansasProjectionValidation.java#L96) · [Funding interval traversal](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasMinimumFundingSolver.java#L146) · [Audit evidence](PHASE-6E-P3D2D-ARKANSAS-FUNDING-BUDGET-AUDIT.md)

## 4. Mathematical proof of capped equivalence

Reconciliation produces `certain ≤ qualified ≤ possible` separately for each verified actual recipient and year. For that recipient's retirement-exclusion election, `E(x) = min(x, L)` with the statutory limit `L`. This function is nondecreasing. Therefore `E(certain) = E(possible)` implies `E(qualified)` is the same for every qualification admitted by that evidence. When the raw totals differ and their capped values agree, the certain receipts already fill the cap. The alternate senior election forgoes this exclusion and still subtracts zero. Neither election depends on resolving the Unknown pension as employer income.

The whole-interval claim uses the funding contract. Within a single marginal withdrawal-source segment, greater withdrawal amounts retain preceding allocations and previously reconciled receipts. Additional qualifying income can add to receipts but cannot make the saturated pool larger or reduce already certain receipts. The existing worksheet covers added ordinary income and possible exclusions for the marginal source through the interval. State rate and adjustment boundaries, age-credit and senior elections, the unresolved low-income envelope and the federal floor keep their existing treatment. Any recipient whose possible receipts still produce a different capped result continues to decline the bound. Pruning still requires maximum available cash to be **strictly below** the combined floor. The chosen amount is allocated, repriced and settled exactly. [Source reconciliation](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasIncomeReconciliation.java#L204) · [Funding floor](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasProductionTaxCalculator.java#L83) · [Joint worksheet](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/arkansas/ArkansasJointWorksheet.java#L89) · [State rate worksheet](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/arkansas/ArkansasRateWorksheet.java#L50) · [Strict pruning](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasMinimumFundingSolver.java#L146)

## 5. Source files and exact changes

| File | P3D2E change |
| --- | --- |
| [ArkansasProductionTaxCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasProductionTaxCalculator.java#L83) | Compare each verified recipient's capped certain and possible amounts in the interval gate. Keep raw facts and the existing worksheet inputs. |
| [ArkansasMinimumFundingSolver.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasMinimumFundingSolver.java#L24) | Identify the new proof through the existing funding version field. Search mechanics and limits stayed as written. |
| [RetainedProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java#L143) | Allow exactly the original and the new Arkansas annual policy tuples. |
| [ArkansasAnalysisProvenance.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ArkansasAnalysisProvenance.java#L65) | Allow the two policy tuples for retained analysis and check each Arkansas dependency's original tuple before existing identity and digest checks. |
| [ArkansasCappedExclusionProofTest.java](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasCappedExclusionProofTest.java#L91) | Add 14 synthetic production, recipient, interval, and budget cases with independent arithmetic. |
| [ArkansasFundingProvenanceTest.java](src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ArkansasFundingProvenanceTest.java#L23) | Add byte round-trip, historical freshness, and policy relabel rejection coverage. |
| [ArkansasValidationAnalysisTest.java](src/test/java/com/daviddunn/retirementplanner/domain/projection/ArkansasValidationAnalysisTest.java) | Make the invalid-proof-key fixture independent of the active proof identifier. |
| [ArkansasHistoryUiTest.java](src/test/java/com/daviddunn/retirementplanner/ui/wizard/ArkansasHistoryUiTest.java) | Assert the active production proof for newly generated evidence. Pre-change proof identity has separate explicit coverage. |
| This report | Record the pre-edit proof-version decision, implementation, and verification. Temporary probes and logs are under `target/phase6e-p3d2e/`. |

## 6. Proof-version and provenance decision

Before changing the solver, inspection established that funding and target operation counts are serialized and feed the annual digest. Quick Comparison and deterministic analysis compare an independently evaluated baseline with the accepted annual digest. Keeping the previous proof version could therefore leave a saved result classified current while a fresh analyzer rejects its changed proof-count bytes. The current-policy comparison is intentionally version-sensitive. [Annual extension](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java#L143) · [Projection freshness](src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java#L173) · [Quick baseline digest](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ArkansasAnalysisProvenance.java#L431) · [Deterministic baseline digest](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ArkansasDeterministicEvidence.java#L111)

The new proof identity uses the existing policy map. Both explicit approved tuples retain `AR-2026-P2-1` and, when applicable, `AR-TARGET-P3A-1`. Schema-three annual rows, original v1/v2 payload layouts, canonical encoding and UUIDs retain their existing contracts. The reader checks each Arkansas dependency's policy tuple as well as its frozen UUID and annual digest. Ordinary-state paired sides retain their existing validation. Older saved Arkansas evidence opens as historical under the current build. To participate in fresh financial analysis, an Arkansas dependency needs a newly accepted current-proof result, including an older Arkansas Baseline if selected for a new paired calculation. Existing artifacts remain available with their original policy context and money. An older application build will reject the new tuple under its existing allowlist. [Accepted tuples](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java#L143) · [Dependency verification](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ArkansasAnalysisProvenance.java#L505)

## 7. Independent numerical oracles

Fixture uses three calendar years opening January 1, 2026, a single primary born July 1, 1965, and zero growth and indexing. Tax and other annual parameters remain nominal at the fixture's 2026 values. Starting accounts hold $200,000 in a traditional IRA, $30,000 in checking, and $0 in the same owner's Roth. Taxable-first withdrawal order applies. Beginning January 2028 it receives $2,000 per month from a documented employer pension and $5,000 per month from a pension whose origin is varied between Unknown and documented. Annual 2028 expense is $84,000. The synthetic year has no modeled Social Security, RMD, Medicare, or death. [Fixture](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasCappedExclusionProofTest.java#L260)

Calculate federal taxable income as `$84,000 + executed conversion − $16,100`. Independently sum the federal slices `$12,400 × 10%`, `$38,000 × 12%`, and `(taxable income − $50,400) × 22%`. For Arkansas use `$84,000 + executed conversion − $6,000 − $2,470`. The zero and fixed cases sum `$5,600 × 2%`, `$4,800 × 3%`, `$10,400 × 3.4%`, and `(Arkansas net income − $26,400) × 3.7%`. The high-income targeted case applies `Arkansas net income × 3.7% − $79.90`. Subtract the applicable $29 Arkansas credit and round final Arkansas liability to cents. [Independent oracle](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasCappedExclusionProofTest.java#L91)

| 2028 case | Executed conversion | Federal tax | Arkansas tax | Tax funding | Arkansas exclusion |
| --- | ---: | ---: | ---: | ---: | ---: |
| None | $0.00 | $9,650.00 | $2,398.41 | $12,048.41 | $6,000.00 |
| Fixed | $5,000.00 | $10,750.00 | $2,583.41 | $13,333.41 | $6,000.00 |
| 22% bracket-fill | $37,800.00 | $17,966.00 | $4,084.31 | $22,050.31 | $6,000.00 |

All six production executions match these amounts exactly. At each selected point, checking funds the whole liability and leaves a zero tax-funding deficit. All earlier admissible points are still in checking and leave tax unchanged. The previous cent leaves a $0.01 deficit, so all earlier amounts also fail. The targeted execution ends at federal taxable income $105,700. Tests verify the exact collection and account attribution, Roth transfer, IRA balance, checking balance, and pension-origin evidence.

## 8. Synthetic 2028 before and after

The permanent test class was run before the production edit. The confirmed red run reproduced typed 2028 exhaustion for all three production variants with Unknown origin and exposed the missing interval on the exact cap and both survivor scenarios. The optimized run completes all three cases. Fully documented automated controls complete before and after with the same measured candidate and bound counts as below. An independent scratch comparison used identical inputs against P3D2A and the final working-tree build. [Confirmed red run](target/phase6e-p3d2e/red-confirmed.log) · [Focused results](target/phase6e-p3d2e/focused.log) · [Original algorithm probe](target/phase6e-p3d2e/performance-before.log) · [Optimized probe](target/phase6e-p3d2e/performance-after.log)

## 9. Candidate and interval-bound measurements

| Unknown-origin case | Before exact candidates / attempted bounds | After exact candidates / attempted bounds | After maximum search depth |
| --- | ---: | ---: | ---: |
| No conversion, direct and 2028 projection | 512 / 526 measured on the direct pass | 11 / 30 | 23 |
| Fixed $5,000, 2028 projection | 512 allowance exhausted / bounds unavailable from refusal | 13 / 31 | 21 |
| 22% target, direct and 2028 projection | 512 / 526 measured in the first exact trial | 13 / 32 | 23 |

The target used one exact outer trial and zero outer interval visits before and after. Inner work fell from 512 to 13 candidates. The before/after target probe also recorded federal proof calls falling from eight to seven and Arkansas proof calls from one to zero outside the inner solver. Interval metrics count all bound attempts. Successful interval-prune counts are unavailable from the existing metrics. No new production instrumentation was introduced. [Original algorithm probe](target/phase6e-p3d2e/performance-before.log) · [Optimized probe](target/phase6e-p3d2e/performance-after.log)

## 10. Budget accounting verification

Default inner funding continues to permit 512 exact evaluations, including zero and a distinct fractional terminal amount when needed. Roth target work retains 4,096 cumulative inner evaluations, up to 512 outer intervals, and the existing 160 per-interval and 8,192 total box limits. Lower-first traversal, leftover per-trial allowance, cancellation and Medicare second-pass behavior were left intact. [Funding limits](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasMinimumFundingSolver.java#L24) · [Roth limits](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasRothFunding.java#L27) · [Roth inner accounting](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasRothFunding.java#L218) · [Box limits](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasTargetBounds.java#L28)

The old synthetic $5.11/$5.12 unpruned conditions no longer isolate the exact-candidate cutoff because the improved proof can prune them. The new test instead injects the existing one-candidate allowance. Zero is feasible and succeeds on the last permitted evaluation. Raising the need by one cent makes another exact price necessary and returns typed `FUNDING_BUDGET` at the same recorded allowance. Separate regressions cover zero funding capacity, independently proven shortage, unresolved tax evidence, fractional terminal capacity, and distinct outer `TARGET_BUDGET` exhaustion. [Budget regression](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasCappedExclusionProofTest.java#L210) · [Account funding regressions](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasAccountFundingTest.java) · [Target certification regressions](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasTargetCertificationTest.java)

## 11. Mixed-owner and survivor tests

Joint configurations covered both recipients capped, primary only capped, spouse only capped, and neither capped, with distinct origin evidence. The unresolved pool continues to refuse its bound even when the other spouse is capped. Single-person controls at $5,999.99, $6,000.00, and $6,000.01 distinguish the boundary without rounding. A deferred funding interval crossed Arkansas rate and schedule-adjustment edges. Both survivor directions exercised opening and death-year evidence, married filing through the death year, and the 2028 Single transition. The retained evidence distinguishes original pension owner from actual survivor recipient. [Joint and exact-cap tests](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasCappedExclusionProofTest.java#L156) · [Both survivor transitions](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasCappedExclusionProofTest.java#L231)

## 12. Roth conversion tests

The fixed and 22% targeted runs use the production engine and the exact funding and conversion oracles above. Target-source and ordering tests cover recomputation after deferred funding, exact-candidate basis, fractional terminal funding, earlier qualifying cents, and cancellation in target work. The outer optimizer still certifies the same target objective and exact executed amount. The new bound applies through its existing shared inner funding solver. [Roth funding integration](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasRothFunding.java#L218) · [Target certification suite](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasTargetCertificationTest.java)

## 13. Federal and Arkansas tax invariance

The edit is in an Arkansas lower-bound gate. Reconciliation, tax worksheets, withdrawal and settlement, Medicare/IRMAA, RMD, Social Security, Roth execution, exclusions and source eligibility continue using their established paths. Existing low-income, NIIT, basis, early-distribution, additional-tax and unknown-origin refusals remain part of the focused tests. The exact synthetic control verifies funding inflows, preserved gross income, withdrawal funding source, actual Roth amount and resulting balances as well as both liabilities. No numerical golden changed. [Tax calculator](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasProductionTaxCalculator.java#L83) · [Tax evidence tests](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasDomainCalculationTest.java) · [Worksheet tests](src/test/java/com/daviddunn/retirementplanner/domain/tax/state/arkansas/ArkansasWorksheetTest.java)

## 14. Historical archive preservation

The permanent provenance test verifies canonical legacy bytes after decode/encode, the original identity and inputs, historical-vs-current policy behavior for identical financial amounts, and rejection of incompatible policy relabeling. A separate scratch probe used actual P3D2A synthetic archives created by the older build. It reopened a v1 Quick archive, a v2 weighted archive, and a v2 two-role paired archive. It checked four projection role entries and six analyses across those three files, including original UUIDs, frozen fingerprints, policy identities, annual canonical bytes, analyzer digests, and historical classification. It exercised Save As and reopen on all three, plus ordinary Save and both role swaps on the paired archive. The 13 source-file SHA-256 entries for the selected plans and their archive artifacts matched before and after. The old plans were read from existing synthetic test profiles. Writes went to a new isolated profile. Existing v1/v2 codec, malformed-data rejection and association tests ran separately. [Permanent test](src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ArkansasFundingProvenanceTest.java#L23) · [Archive probe output](target/phase6e-p3d2e/legacy-history.log) · [Before hashes](target/phase6e-p3d2e/old-artifacts-before.txt) · [After hashes](target/phase6e-p3d2e/old-artifacts-after.txt)

## 15. Analyzer integration verification

| Mode | Evidence in this phase |
| --- | --- |
| Deterministic annual | Production-engine synthetic fixture spanning three years and three Roth choices. |
| Fixed and longevity-adjusted Monte Carlo | Actual JavaFX production launch routes exercised with bounded synthetic settings. |
| Paired AR/AR, AR/TX and TX/AR | Launcher tests for ordered source roles, equal frozen inputs with shared or distinct UUIDs, mixed-state orientations, and temporary survivor elections. Old paired results reopened in both orientations. |
| Integrated Social Security | Actual Couple Quick Comparison and individual deterministic or mortality-integrated launch routes. |
| Longevity-weighted integration | Bounded Arkansas real-engine financial and continuation coverage plus old v2 weighted archive restoration. The dedicated full weighted performance workload was out of scope. |
| Roth targeting | Production yearly execution in all three variants and inner/outer certification, separate shortage classification, and cancellation tests. |

Admission and failure tests verify unsupported tax stays separate from proved financial shortage or Monte Carlo ruin. Controlled-workflow regressions exercise last-success preservation and publication after cancellation or replacement. The paired and association tests verify original role-specific source bindings. [Monte Carlo launch tests](src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/ArkansasMonteCarloLaunchTest.java) · [Quick launch tests](src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ArkansasSocialSecurityLaunchTest.java) · [Individual launch tests](src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ArkansasIndividualAnalyzerLaunchTest.java) · [Controlled workflow tests](src/test/java/com/daviddunn/retirementplanner/ui/controller/ArkansasControlledWorkflowTest.java)

## 16. Cancellation and performance observations

Cancellation checkpoints and worker ownership are unchanged. The focused run logged a controlled target-interval cancellation at 14.524 ms. The integration log recorded queued UI cancellation dispatch of 47.946 ms. These are fixture measurements from different tests. The proof hot loop added no locks, worker allocations or cross-run cache. [Focused results](target/phase6e-p3d2e/focused.log) · [Integration results](target/phase6e-p3d2e/integration.log)

Both scratch performance JVMs used Temurin 25.0.4.1 with `-Xmx384m`, three warmups and nine timed observations per variant. The fixture and probe source were shared. The original and final algorithms ran against separate compiled class trees and JVMs. Across all five Unknown-origin measurement paths, the old build refused all 45 timed samples and the new build completed all 45. Timing measures a refusal on the old path and a success on the new path.

| Unknown-origin path | Original min / median / max (ms) | Optimized min / median / max (ms) |
| --- | ---: | ---: |
| Direct no-conversion pass | 12.189 / 12.933 / 18.727 | 2.177 / 4.332 / 5.236 |
| Direct Roth target | 8.237 / 11.047 / 14.199 | 1.788 / 3.122 / 4.539 |
| Three-year projection, no conversion | 8.934 / 9.578 / 15.143 | 1.816 / 2.973 / 5.236 |
| Three-year projection, fixed | 8.942 / 11.511 / 18.086 | 1.581 / 3.254 / 3.589 |
| Three-year projection, target | 5.941 / 7.724 / 9.319 | 1.581 / 1.852 / 3.951 |

The documented control kept identical proof counts and was slower in this sampled JVM run, for example 0.331 to 0.633 ms in the direct zero-conversion case. Largest post-invocation used-heap samples were 231,483,648 bytes before and 125,655,264 bytes after. These samples do not measure peak live heap. Warmup differed because the earlier JVM performed substantially more exact tax work on preceding fixtures. Workstation scheduling and GC were not isolated. Proof counts are the robust result of this comparison. The sample establishes no general timing or memory guarantee. [Probe source](target/phase6e-p3d2e/FundingProofPerformance.java) · [Before](target/phase6e-p3d2e/performance-before.log) · [After](target/phase6e-p3d2e/performance-after.log)

## 17. Test commands and final results

Executed with Temurin 25.0.4.1 and IntelliJ's bundled Maven. Each Maven command used the project-local repository and isolated synthetic settings.

```powershell
$m = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd'
$repo = "-Dmaven.repo.local=$((Get-Location).Path)\.codex-m2\repository"
$env:JAVA_HOME = Split-Path (Split-Path (Get-Command java).Source)
# $profile and LOCALAPPDATA each resolve to the isolated synthetic paths for that run.
& $m $repo "-Duser.home=$profile" -o -B "-Dtest=$selector" test
```

| Run and log | Tests | Passed | Skipped | Failed | Errors | Exit | Maven time |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| [Initial pre-edit run](target/phase6e-p3d2e/red.log) | 13 | 4 | 0 | 2 | 7 | 1 | 14.127 s |
| [Confirmed pre-edit reproduction](target/phase6e-p3d2e/red-confirmed.log) | 14 | 5 | 0 | 2 | 7 | 1 | 14.597 s |
| [Focused proof and financial tests](target/phase6e-p3d2e/focused.log) | 174 | 174 | 0 | 0 | 0 | 0 | 27.273 s |
| [Launcher, history and state integration](target/phase6e-p3d2e/integration.log) | 171 | 171 | 0 | 0 | 0 | 0 | 1 min 4 s |
| [Final bounded regression](target/phase6e-p3d2e/bounded.log) | 5,524 | 5,489 | 35 | 0 | 0 | 0 | 9 min 10 s |

Exact `$selector` values in run order. Both pre-edit runs used the first selector.

```text
ArkansasCappedExclusionProofTest
ArkansasCappedExclusionProofTest,ArkansasAccountFundingTest,ArkansasTargetCertificationTest,ArkansasFundingCounterexampleTest,ArkansasFundingArchitectureTest,ArkansasProjectionIntegrationTest,ArkansasControlledWorkflowTest,ArkansasValidationAnalysisTest,ArkansasDomainCalculationTest,ArkansasWorksheetTest
ArkansasFundingProvenanceTest,ArkansasMonteCarloLaunchTest,ArkansasLongevityValidationTest,ArkansasSocialSecurityLaunchTest,ArkansasIndividualAnalyzerLaunchTest,ProjectionSnapshotPersistenceTest,ProjectionSwapTest,ArkansasHistoryUiTest,ArkansasWorkflowUiTest,Phase12ECodecTest,Phase12EAssociationTest,Phase12FAnalyzerSwapTest,Phase12GFreshnessTest,ProductionStateTaxServiceTest,AlabamaAnnualGoldenTest,MichiganTaxCalculatorTest,ConditionalBenefitFinancialIntegrationTest
*,!*Benchmark*,!*CapacityProbe*,!*Performance*,!*Exhaustive*,!*Search*
```

The initial red run also identified missing explicit survivor claiming settings in two new fixtures. The confirmed pre-edit run assigned age 67 and added the accounting boundary test before production changes. Both red runs remain recorded. For scratch probes, `$cp` was the test runtime classpath from the `java.class.path` property of the `ArkansasAccountFundingTest` Surefire XML. `$beforeCp` substituted the isolated P3D2A candidate-final `target/test-classes` and `target/classes` entries. `$classes` and the sources are under `target/phase6e-p3d2e/`.

```powershell
javac -cp $cp -d $classes $performanceSource
java -Xmx384m "-Duser.home=$profile" -cp "$classes;$beforeCp" com.daviddunn.retirementplanner.domain.tax.FundingProofPerformance before
java -Xmx384m "-Duser.home=$profile" -cp "$classes;$cp" com.daviddunn.retirementplanner.domain.tax.FundingProofPerformance after
java -Xmx512m "-Duser.home=$profile" -cp $cp $historySource
git diff --check
```

Both performance probes and the archive probe exited zero. They are standalone diagnostics. Maven test totals exclude their observations. The final tracked and cached diff checks exited zero, with only Git line-ending conversion notices. The index stayed empty. [Process exits](target/phase6e-p3d2e/exits.txt)

## 18. Existing-state regression results

The focused integration run passed production-state dispatch, Alabama annual goldens, Michigan tax, and conditional-benefit financial integration. The 20 previously admitted states are AK, AL, AZ, FL, GA, IA, IL, KY, MI, MS, NC, NH, NV, OK, PA, SD, TN, TX, VA and WY. Arkansas remains admitted through its controlled path, and Colorado remains unavailable. The bounded selector covers ordinary federal, state, Medicare, RMD, Roth, Social Security, seeded Monte Carlo, and persistence and export regressions. No state availability or existing state tax logic was edited. [Production state tests](src/test/java/com/daviddunn/retirementplanner/domain/tax/state/ProductionStateTaxServiceTest.java)

## 19. Remaining limitations and unsupported scenarios

A recipient with unresolved eligibility or tax-relevant qualification and available exclusion capacity can still decline the bound. Unverified low-income schedules, uncertain pension origin that materially changes tax, unresolved tax basis or prepayment, insufficient NIIT evidence, unapproved early distribution or additional tax, and unsupported conditional income retain their preexisting treatment. A valid proof can still exceed the fixed work allowances in other scenarios. Approximate candidates remain unaccepted. The original customer's 2028 inputs were unavailable and could involve a different cause. No new input capability or tax-policy interpretation was introduced.

## 20. Native Windows acceptance requirements

Automated Windows JavaFX launch and history tests ran with synthetic plans. A direct interactive rerun remains outstanding. Recheck with the controlled synthetic fixture and, if offered for isolated reproduction, a copy of the original plan. Verify the projected 2028 numbers, progress and cancellation, last-success retention on any renewed refusal, and v1/v2 history through Save As and role swapping. Allow old artifacts to open in their original historical context and use newly accepted projections when starting current-policy financial analysis. Pending P3D2C UI acceptance and earlier P3D2A native acceptance remain separate. No original user plan was used or modified.

## 21. Final readiness classification

**Implementation verified. Native acceptance incomplete.** The demonstrated synthetic 2028 refusal is resolved within the unchanged production limits. The original Windows plan has not been retested. This phase leaves the working tree for review and requires separate authorization for a Git checkpoint or further financial-engine work.
