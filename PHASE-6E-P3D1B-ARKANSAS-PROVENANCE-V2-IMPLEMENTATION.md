# Phase 6E-P3D1B — Arkansas Analyzer Provenance V2 Implementation

## Executive summary

**READY_FOR_P3D2_NATIVE_ACCEPTANCE.** The final Java 25 bounded regression passed 5,462 tests, with 35 skips and zero failures or errors. The final focused suite passed 110 tests, and both automated scale runs passed all 10 cases. The full weighted profile completed and restored 5,184 strategies across 2,600 positive-probability worlds.

Arkansas analyzer execution now uses versioned evidence for Couple Deterministic Exhaustive, Individual Deterministic Integrated, Individual Integrated Mortality, Couple Longevity-Weighted and paired Monte Carlo. Pairing records Current/A and Baseline/B separately and uses the production engine appropriate to each side. The weighted evidence factors pension facts and receipt sequences independently from mortality and strategy attribution. The representative 100-strategy result retains all 18,616 logical receipts and both survivor directions.

The existing v1 reader and writer, schema-three financial history and numerical goldens remain in use. This phase covers source integration and automated verification. Native Windows acceptance and release validation are P3D2 work.

## Opening Git baseline

- Branch `main` was one commit ahead of the local `origin/main` reference. HEAD was `a1273a31c9aca014aeadd1280410258bd837b8d4`, the accepted P3C3 checkpoint.
- The index was empty. The working tree contained 55 modified tracked files and four new P3D1 files, plus the P3D1B architecture report. The existing reports and P3D1 work were the implementation baseline. In-scope amendments to the two existing Arkansas launcher tests exercise the enabled routes. The reviewed working tree held 74 modified tracked files and 18 untracked files, with the original HEAD and empty index.
- Opening source inspection covered the P3D1B architecture report, P3D1 activation, P3C2 durable history, P3C3 controlled execution, the version readers and writers, immutable projection components, dispatch, completion and production launchers. The focused Java 25 opening run passed 212 tests.

The prior reports record the P3D1 baseline manifest and the approved contract. [P3D1 activation](PHASE-6E-P3D1-ARKANSAS-PRODUCTION-ACTIVATION.md) · [P3D1B capacity audit](PHASE-6E-P3D1B-ARKANSAS-ANALYZER-PROVENANCE-CAPACITY.md)

## Architecture and canonical evidence

The existing analysis envelope carries `arkansasExecution.version`. V1 uses the original five properties, a 10,000-row receipt ceiling and its original canonical digest. V2 adds `executionEvidence` and leaves the v1 receipt list empty. Missing or incompatible version properties, duplicate fields and unknown versions fail closed. Older v1 readers reject v2. Save As validates and rebinds original components under the copied plan owner and preserves the analysis request UUID and projection UUIDs. There is no migration or recalculation of historical monetary results.

An accepted source binds its projection UUID, canonical frozen input digest, canonical annual digest and observed run and year totals. Engine policy versions come from the admitted annual results. The result binding includes the run UUID, input fingerprint and canonical payload digest. The verified worker token can only be redeemed for the exact payload and original immutable projection instances. A persisted snapshot independently validates the relationships and content commitments before reconstructing a result.

Deterministic evidence records generation order, exact strategy, request-only death context, observed year count and annual and reduced metric digests. Funding shortages retain their completed prefix, year, stage, owner, required and available amounts and shortfall. Unsupported tax retains the typed issue and year. Selected details are stored as existing schema-three projections and checked against the observed annual digest. Restoration preserves ranking, ties, baseline differences and break-even values. A configured baseline is linked to its original annual digest. A request-only individual death year identifies a separate financial context even when it lies beyond the configured annual horizon.

Individual integrated mortality verifies each observed positive scenario against its exact claiming strategy, death context and projection ending December 31 before January 1 death. Opening death retains the opening asset valuation with zero engine calls. The weighted result is verified against the stored probabilities and observed reduced outcomes. A failed positive scenario leaves the previous analysis available.

## Pension dictionary, mortality attribution and budgets

The pension dictionary records original source ordinal, original owner, actual recipient, original plan origin, receipt year, exact gross and qualification, and optional receipt evidence. Dictionary order is first use during observed execution. Ordered sequences of fact indices are also factored. They may be shared only when every index and its position match. Per-execution commitments hash the ordered canonical fact digests. Restoration checks existence, uniqueness, first use, absence of unused entries, recipient life and original pension provenance. A sequence is decoded once and can be traversed by reference for each affected execution.

The common positive mortality sequence is stored once. An evaluated representative maps its scenario prefix to observed execution indices using a canonical big-endian unsigned 16-bit sequence in Base64. Code zero denotes validated opening assets. Other codes are one plus the execution index. The link position identifies the frozen death pair and probability. A continuation link comes from the existing finite-prefix proof and identifies the actual carrier. An equivalent occurrence identifies its earlier successful, proved representative. Pension-vector equality has no bearing on financial equivalence. Captured scenario reductions verify the aggregates on the worker. The artifact commits to those reductions and retains any selected scenario details; its compact rows carry digests of the omitted reduced values.

| Budget | Limit |
| --- | ---: |
| Unique pension facts and unique receipt vectors | 8,192 each |
| Fact indices in one vector | 16,384 |
| Physically encoded pension indices | 600,000 |
| Logical receipts across genuine executions | 3,000,000 |
| Financial executions or weighted occurrences | 32,768 |
| Shared positive worlds and represented scenario links | 16,384 and 1,000,000 |
| Paired worlds | 12,000 |
| V2 nesting above the evidence root | 24 |
| Envelope raw / gzip / token / array budgets | 32 MiB / 16 MiB / 2,000,000 / 100,000 |
| V2 worker preflight payload and evidence raw / gzip / token / array budgets | 28 MiB / 14 MiB / 1,750,000 / 91,808 |

Input preflight applies JSON bounds before DTO allocation. Construction and source validation enforce the logical bounds without expanding the graph into v1 rows. The v2 worker checks the prospective archive before admitting the result and reserves room for report context and envelope metadata. The final writer verifies the complete document. Exceeding a collector or worker document limit invalidates the replacement. Failed or discarded carriers cannot contribute successful outcomes. V1 and v2 retain their separate limits.

## Analyzer admission and history

| Production route | Implementation and verification |
| --- | --- |
| Social Security Only | Existing individual and couple calculation and freshness routes. |
| Couple Quick Comparison | Existing admitted v1 route with a matching accepted projection and schema-three details. |
| Couple Deterministic Exhaustive | V2 capture in the real launcher. Ranking and selected annual details round-trip in its dedicated test. |
| Individual Deterministic Integrated | V2 capture in the real launcher. Tests cover normal and request-only baselines, round-trip, and a typed unavailable year preserving the last success. |
| Individual Integrated Mortality | Controlled Arkansas engine in the actual launcher. Nine strategies, real lifetime ledgers, fresh association and round-trip. The separate opening-death fixture verifies zero engine calls. |
| Couple Longevity-Weighted | V2 capture at actual reduction and continuation boundaries. Dedicated representative and larger profiles verify the factored representation. The full desktop workload is tracked separately below. |
| Standalone fixed and longevity Monte Carlo | Existing admitted v1 routes. |
| Paired fixed and longevity Monte Carlo | V2 with ordered Current/A and Baseline/B, mixed admitted states and analysis-only survivor elections. |

Each paired source binds an accepted component and its effective analysis-only input digest. Reconstruction applies exactly the authorized survivor election to a detached source document. The accepted source projection is preserved. Each world’s reduced path and terminal or exact funding failure bind to that side’s observed annual evidence. A v2 source without an admitted jurisdiction or a verified accepted component is unavailable.

The production tests exercise Arkansas/Arkansas, Arkansas/Texas, Texas/Arkansas, equal inputs with distinct accepted UUIDs, and the same accepted UUID in both roles. The mixed fixture changes the age to 62 on A and 67 on B, saves the analysis, swaps both directions, uses Save As, reopens and verifies the original evidence and values. A result remains historical while the role ordering differs from its original dependencies. Current freshness requires the exact configuration, accepted projection IDs, ordered pins and applicable policy. A v2 last-success result opened from disk verifies its source components on the I/O worker before attaching to the UI.

## Calculation ownership, cancellation and error handling

The owner captures immutable inputs and prepares the existing revision, epoch and request ticket. The existing one-lease Social Security worker and the paired Monte Carlo worker calculate and observe annual results. The controlled Arkansas scope checks cancellation during funding and Roth proof and at annual, candidate, scenario and evidence boundaries. Owner callbacks check eligibility for progress, failure and result publication. A changed plan, replacement projection or superseded ticket revokes the result. Replacement failure preserves the previous accepted analysis. Proven financial shortage has a separate outcome and presentation from unsupported tax. The weighted analyzer cannot complete an aggregate using a failed positive-probability scenario.

## Tax policy and existing-state invariance

Arkansas retains `AR-2026-P2-1`, `AR-FUNDING-P2-1` and `AR-TARGET-P3A-1`. Federal, ordinary state-tax, Roth and pension calculations follow their existing paths. The previously admitted jurisdictions are AK, AL, AZ, FL, GA, IA, IL, KY, MI, MS, NC, NH, NV, OK, PA, SD, TN, TX, VA and WY. Colorado remains unavailable. Neither the schema-three annual format nor numerical golden values were edited.

The individual deterministic search keeps its existing configured horizon. When a request-only death results in a modeled Arkansas tax year without an actual filer, the approved worksheet route reports typed unavailability for that year. The test deliberately verifies this boundary and checks preservation of the preceding result. Individual mortality has its existing exact pre-death cutoff and uses a different financial context.

## Changed-file manifest

Paths below are under `src/main/java/com/daviddunn/retirementplanner` unless stated otherwise.

| Area | Files |
| --- | --- |
| Paired execution and ordered identity | `app/montecarlo/MonteCarloSnapshot.java`, `app/montecarlo/MonteCarloStrategyComparisonAnalyzer.java`, `app/projection/AnalyzerAssociation.java`, `ui/montecarlo/MonteCarloResultLifecycle.java`, `ui/montecarlo/MonteCarloStrategyComparisonSession.java`, `ui/montecarlo/MonteCarloStrategyComparisonView.java` |
| Social Security observation | `app/socialsecurity/InsufficientAnalysisFundsException.java`, `IntegratedSocialSecurityCompleteStrategySearchCalculator.java`, `IntegratedSocialSecurityStrategyEvaluator.java`, `LongevityContinuationSession.java`, `LongevityFinancialObservation.java`, `LongevityWeightedContinuationEvaluator.java`, `LongevityWeightedIntegratedStrategyComparisonService.java`, `LongevityWeightedIntegratedStrategyEvaluator.java`, `SinglePersonIntegratedAnalysis.java`, `SinglePersonMortalityAnalysis.java` |
| Versioned proof and snapshots | Under `app/socialsecurity/results`, `AcceptedAnalysisCompletion.java`, `AnalysisSnapshot.java`, `ArkansasAnalysisEvidenceV2.java`, `ArkansasAnalysisProvenance.java`, `ArkansasAnalysisProvenanceReader.java`, `ArkansasDeterministicEvidence.java`, `ArkansasIndividualMortalityEvidence.java`, `ArkansasPairedEvidence.java`, `ArkansasWeightedAttribution.java`, `ExhaustiveSnapshot.java`, `IndividualDeterministicSnapshot.java` |
| Persistence and owner | `persistence/AnalysisSnapshotCodec.java`, `persistence/ProjectionSnapshotComponents.java`, `ui/controller/ApplicationController.java` |
| Social Security launch and presentation | Under `ui/socialsecurity`, `IndividualCompletionPersistence.java`, `SinglePersonIntegratedView.java`, `SinglePersonMortalityView.java`, `SocialSecurityAnalysisFailurePresentation.java`, `SocialSecurityAnalyzerJobController.java`, `SocialSecurityStrategyAnalyzerDialog.java`, `WeightedResultPersistence.java` |
| Tests | Under `src/test/java/com/daviddunn/retirementplanner`, `app/socialsecurity/ArkansasProvenanceV2CapacityProbeTest.java`, `ui/montecarlo/ArkansasMonteCarloLaunchTest.java`, `ui/socialsecurity/ArkansasExhaustiveLaunchTest.java`, `ui/socialsecurity/ArkansasIndividualAnalyzerLaunchTest.java`, `ui/socialsecurity/ArkansasSocialSecurityLaunchTest.java`, `ui/socialsecurity/ArkansasWeightedLauncherPerformanceTest.java` |

## Java 25 verification

Temurin 25.0.4.1 and IntelliJ's bundled Maven were used. Test counts are Surefire totals. The bounded selector excludes workload test class names matching any of the five listed patterns. Skips are the separately reported skips among selected cases.

```powershell
$maven = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd'
$repo = '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository'
& $maven $repo '-Dtest=*Arkansas*,!*Performance*,Phase12ECodecTest,ProjectionSnapshotPersistenceTest,MonteCarloStrategyComparisonTest' test *> target/phase6e-p3d1b-v2-opening.log
& $maven $repo '-Dtest=ArkansasProvenanceV2CapacityProbeTest,ArkansasIndividualAnalyzerLaunchTest,ArkansasMonteCarloLaunchTest,ArkansasExhaustiveLaunchTest,ArkansasSocialSecurityLaunchTest,ArkansasLongevityValidationTest,AnalysisSnapshotCodecTest,LongevityContinuationTest,Stage5DCancellationTest,Stage5GContinuationTest' test *> target/phase6e-p3d1b-v2-compacted-focused.log
& $maven $repo '-Dtest=ArkansasProvenanceV2CapacityProbeTest,ArkansasProductionPerformanceTest,ArkansasWeightedLauncherPerformanceTest,ArkansasExhaustiveLaunchTest,ArkansasIndividualAnalyzerLaunchTest,ArkansasMonteCarloLaunchTest,ArkansasLongevityValidationTest,AnalysisSnapshotCodecTest,ExhaustiveSnapshotTest,ExhaustiveSnapshotInterningTest,ExhaustiveHistoricalPdfFidelityTest,ExhaustivePersistenceTest,SocialSecurityAnalyzerJobControllerTest' test *> target/phase6e-p3d1b-v2-final-focused.log
& $maven $repo '-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*Performance*,!*Exhaustive*,!*Search*' test *> target/phase6e-p3d1b-v2-final-regression.log
& $maven $repo '-Dtest=ArkansasExhaustiveLaunchTest,ExhaustiveSnapshotTest,ExhaustiveSnapshotInterningTest,ExhaustiveHistoricalPdfFidelityTest,ExhaustivePersistenceTest' surefire:test *> target/phase6e-p3d1b-v2-verified-excluded.log
& $maven $repo '-Dtest=ArkansasProvenanceV2CapacityProbeTest,ArkansasProductionPerformanceTest' surefire:test *> target/phase6e-p3d1b-v2-verified-performance.log
& $maven $repo '-Dglass.win.uiScale=1.0' '-Dtest=ArkansasWorkflowUiTest,ArkansasIndividualAnalyzerLaunchTest,ArkansasMonteCarloLaunchTest,ArkansasWeightedLauncherPerformanceTest' surefire:test *> target/phase6e-p3d1b-v2-archive-ui-100.log
& $maven $repo '-Dglass.win.uiScale=1.5' '-Dtest=ArkansasWorkflowUiTest,ArkansasIndividualAnalyzerLaunchTest,ArkansasMonteCarloLaunchTest,ArkansasWeightedLauncherPerformanceTest' surefire:test *> target/phase6e-p3d1b-v2-archive-ui-150.log
```

| Run | Discovered | Passed | Skipped | Failed | Errors | Exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Opening | 212 | 212 | 0 | 0 | 0 | 0 |
| Focused with packed attribution | 46 | 46 | 0 | 0 | 0 | 0 |
| Final focused with archive admission | 110 | 110 | 0 | 0 | 0 | 0 |
| Intermediate bounded | 5,497 | 5,462 | 35 | 0 | 0 | 0 |
| Final bounded | 5,497 | 5,462 | 35 | 0 | 0 | 0 |
| Dedicated evidence budgets and performance | 5 | 5 | 0 | 0 | 0 | 0 |
| Dedicated excluded classes | 47 | 47 | 0 | 0 | 0 | 0 |
| Glass 100% | 10 | 10 | 0 | 0 | 0 | 0 |
| Glass 150% | 10 | 10 | 0 | 0 | 0 | 0 |

The earlier 74-case integration run covered the launcher variants, the paired history regression and all 40 Social Security job controller tests. It passed with exit 0. During implementation, the exhaustive serialization check exposed different payload and envelope timestamps. The writer now uses the observed completion time for both. The tests also caught unsaved freshness and ordinary Current refresh behavior in the mixed pair harness. The final focused cases verify fresh and historical dependencies before and after persistence and role changes. [Focused integration log](target/phase6e-p3d1b-v2-focused-final.log)

The final bounded run completed in 9 min 52 s. The final focused run completed in 1 min. The final 100% and 150% scale runs took 26.7 s and 26.3 s. [Final bounded regression](target/phase6e-p3d1b-v2-final-regression.log) · [100% scale](target/phase6e-p3d1b-v2-archive-ui-100.log) · [150% scale](target/phase6e-p3d1b-v2-archive-ui-150.log)

One intermediate bounded run overlapped compilation and reported five class-loading errors, with 5,457 passes, 35 skips, zero assertion failures and exit 1. An isolated repeat at that point passed 5,462 with 35 skips and exit 0. The first weighted launcher harness waited inside a modal show call and reported two timeouts. The next harness used a conditioning date outside the mortality table's supported intervals. The corrected harness opens the stage without blocking its test callback and conditions within the supported intervals. The final run includes the direct simultaneous-death regression and the corrected launcher harness. [Overlapped bounded run](target/phase6e-p3d1b-v2-bounded-final.log) · [Isolated repeat](target/phase6e-p3d1b-v2-bounded-verified.log) · [Final focused](target/phase6e-p3d1b-v2-final-focused.log)

## Capacity and performance

The representative profile uses the P3D1B fixture beginning January 1, 2030, with a 25-year configured horizon, two pensions per person, reciprocal survivor pensions, $500,000 and $420,000 traditional IRAs, $100,000 and $130,000 Roth accounts, the fixture's $200,000 brokerage account, Medicare, RMDs and a one-time 22% bracket-targeted Roth election. Birth dates are February 29, 1960 and June 15, 1962. The fixture uses 3% expected return, 2% inflation, 4% healthcare inflation, 1% Social Security COLA, $40,000 opening annual spending and a 3% real valuation discount.

Representative claiming ages are 62, 64, 66, 68 and 70 for both persons, crossed with each person's first and final survivor candidate. The 16 equal-probability worlds combine primary deaths in 2037, 2043, 2049 and 2055 with spouse deaths in 2038, 2044, 2050 and 2056. The 324-strategy expansion uses every retirement age 62 through 70 with those same survivor alternatives and death worlds. The standard-strategy control uses all 5,184 claiming candidates with primary death in 2049 and spouse death in 2056. Each includes the configured strategy as a separate baseline.

| Fixture | Genuine executions / years | Logical receipts / facts / encoded indices | Scenario links / vector count | Raw / gzip bytes | Worker / encode / decode / verification (ms) |
| --- | --- | --- | --- | --- | --- |
| 100 strategies, 16 worlds | 182 / 4,654 | 18,616 / 174 / 716 | 416 / 7 | 273,898 / 27,967 | 2,121.981 / 6.522 / 24.099 / 10.425 |
| 324 strategies, 16 worlds | 574 / 14,678 | 58,712 / 174 / 716 | 1,312 / 7 | 776,120 / 70,573 | 6,617.786 / 29.970 / 117.704 / 15.193 |
| 5,184 strategies, one world | 82 / 2,132 | 8,528 / 104 / 104 | 82 / 1 | 8,640,937 / 163,111 | 2,991.140 / 163.518 / 336.145 / 21.202 |
| 5,184 strategies, 2,600 positive worlds with archive admission | 8,200 / 418,200 | 1,672,800 / 404 / 20,400 | 213,200 / 100 | 16,586,798 / 1,148,048 | 119,609.646 / 205.348 / 518.020 / 13.671 |

The v1 100-strategy probe in the audit rejected that replacement. Its diagnostic receipt array was 9,060,681 raw bytes. The v2 numbers above cover the complete analysis envelope, including results and both pension and scenario attributions. The 100-strategy result was stable through encode, decode and source verification. Synthetic structure checks reach the logical receipt boundary and reject its next increment, oversized encoded collections, a malformed scenario link, and oversized compressed and expanded documents. Corruption checks reject unknown versions, missing and duplicate references, and out-of-range references.

The final 25-year performance run measured 10 deterministic samples, with 14.55 ms median and 38.18 ms nearest-rank p95. It ran 64 seeded worlds in 862.17 ms with zero observed funding failures or proof exhaustions. Six retained reads overlapped the stochastic sample. Queued work reached its first annual proof in 98.62 ms. The owner revoked it in 0.014 ms and the worker stopped in 0.437 ms. The 100-strategy fixture sampled heap occupancy at 226.31 and 169.54 MiB during the final focused suite. These are workstation observations and two-point heap samples. Full native application responsiveness remains a P3D2 check.

The separate diagnostic compiled against Surefire's Java classpath in `target/phase6e-p3d1b-probe` and ran with `java -Xmx1g` in the repository. The expanded and one-world standard profiles exited 0. The default desktop setup uses the standard SSA period table, standard mortality adjustment for each person, and conditions at the opening date. It yields 2,600 positive worlds. The 120-second sample cancelled after 4,993 of 5,185 comparison occurrences and exited 3 as configured. It published no partial result. [Expanded profile](target/phase6e-p3d1b-v2-compacted-larger.log) · [Standard one-world profile](target/phase6e-p3d1b-v2-compacted-standard.log) · [120-second cancellation probe](target/phase6e-p3d1b-v2-compacted-desktop.log)

A 180-second implementation run exposed an overly narrow validator for a later simultaneous-death carrier covering an earlier both-alive prefix. The corrected check follows the existing continuation rule and has a direct regression. Two repeats exited 0, with all strategies successful, 5,103 uses of already-proved representatives, and zero failed carriers, proof exhaustions or scenario failures. Each encoded, decoded and verified the same archive and original source component. Worker time was 133.3 seconds on the first repeat and 119.6 seconds with the final archive admission check. The final repeat sampled heap occupancy at 35.56 and 84.08 MiB. Full desktop timing includes the existing equivalence planner and runs concurrently with regression activity. [Corrected desktop profile](target/phase6e-p3d1b-v2-desktop-verified.log) · [Desktop profile with archive admission](target/phase6e-p3d1b-v2-desktop-archive-gate.log)

```powershell
$tests = [xml](Get-Content target/surefire-reports/TEST-com.daviddunn.retirementplanner.app.socialsecurity.ArkansasLongevityValidationTest.xml -Raw)
$classpath = ($tests.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
$probe = 'target/phase6e-p3d1b-probe'
& javac -cp $classpath -d $probe "$probe/ArkansasEvidenceV2CapacityProbe.java"
& java '-Xmx1g' -cp "$probe;$classpath" com.daviddunn.retirementplanner.app.socialsecurity.ArkansasEvidenceV2CapacityProbe desktop *> target/phase6e-p3d1b-v2-desktop-verified.log
```

## JavaFX and scaling evidence

Automated tests passed at Glass scale factors 1.0 and 1.5. Each run opened the real weighted launcher in Light and Dark, checked its output scale and retained the standard candidate universe using mortality conditioned near the table endpoint. The scale suites also exercise the individual and paired launchers and the original Light/Dark render, wrapped warning height, progress and pension-review checks. The existing workflow renders are under `target/phase6e-p3d1-previews`. Native Windows keyboard, focus and external viewer acceptance remain in P3D2.

## Remaining limitations

- The configured policy still controls Arkansas availability for each year and owner. An unsupported tax year displays its typed reason. A proven shortage carries its own financial evidence. The individual deterministic request-only death context can reach the existing no-filer boundary.
- The full desktop fixture demonstrates one workload's storage and execution costs and one configured mortality distribution. Other plan inputs can reach proof, execution or retention limits. The results above establish fixture measurements; P3D2 exercises responsiveness and capacity on the native application with additional representative plans.
- Historical compact results retain the original financial values, source evidence, outcome bindings and any selected annual details. The original detail-retention policy governs which annual ledgers remain available for display.

## P3D2 native Windows acceptance checklist

- Run each admitted analyzer from the native main window. Exercise navigation, keyboard and focus, owner-scoped cancellation and typed error placement in Light and Dark at 100% and 150%.
- Run representative full desktop longevity and paired profiles. Verify annual proof cancellation, continued interaction, previous-result retention on failure and the configured evidence budgets.
- Reopen associated results after plan edits, both role swaps and Save As. Verify freshness, the original request settings, pensions and ordered identities.
- Review the native PDF viewer, CSV, Results Summary and Annual Detail against retained values. Confirm the original P3D1 workflow, including Review Pensions Apply and Cancel, and check final annual filing guidance before release validation.

## Final readiness classification

**READY_FOR_P3D2_NATIVE_ACCEPTANCE.** The production analyzer launchers, ordered paired history, v1 compatibility and v2 archive admission passed the focused and bounded automated checks. The default weighted fixture fit the physical and logical budgets, preserved all logical receipts and restored its original source association. HEAD and the index match the opening Git state. Native Windows acceptance and any release decision remain in P3D2.
