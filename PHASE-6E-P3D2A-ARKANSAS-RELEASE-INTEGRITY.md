# Phase 6E-P3D2A — Arkansas release integrity and native acceptance

Verified November 19, 2026.

## 1. Executive summary

The P3D2 build defect reproduced from a fresh Git archive of HEAD. The proposed Git tree restores all ten omitted Java files and includes the P3D2 survivor-election and History corrections. The clean candidate independently compiled with Java 25 and passed 5,464 bounded regression tests with 35 skips and zero failures or errors. The focused, separately selected launcher, capacity and scaling suites also passed.

The repository sandbox denied creation of `.git/index.lock`. The sixteen proposed files are staged in an isolated Git index and a replay-verified patch is available for checkpoint approval. The repository's real index remains empty. The actual Windows application completed the programmatically exercised workflows. Direct interactive acceptance remains incomplete.

## 2. Opening Git baseline

- Branch `main`, local upstream `origin/main`, ahead 0 and behind 0. HEAD `dcf013bb8d6149fa8dd5a04a58046e5ee56dfca0` was “Complete Arkansas production activation and analyzer provenance v2.”
- Opening index contained no staged changes. The working tree had three modified tracked files, ten untracked Java sources and the untracked P3D2 acceptance report. The three tracked changes were in `AssumptionsView.java`, `AssumptionsViewTest.java` and `ProjectionAnalyzerHistoryDialog.java`.
- Reviewed the P3D2 baseline and the P3D1/P3D1B route and compatibility records. [P3D2 acceptance](PHASE-6E-P3D2-ARKANSAS-NATIVE-ACCEPTANCE.md) · [P3D1 activation](PHASE-6E-P3D1-ARKANSAS-PRODUCTION-ACTIVATION.md) · [P3D1B capacity audit](PHASE-6E-P3D1B-ARKANSAS-ANALYZER-PROVENANCE-CAPACITY.md) · [P3D1B v2 implementation](PHASE-6E-P3D1B-ARKANSAS-PROVENANCE-V2-IMPLEMENTATION.md)

## 3. Missing source inventory and dependency review

Paths below are relative to the Java package root `com/daviddunn/retirementplanner`.

| Root | File omitted from HEAD | Required coverage or reference |
| --- | --- | --- |
| `src/main/java` | `app/socialsecurity/InsufficientAnalysisFundsException.java` | Carries a proved funding shortfall from the evaluator to typed analyzer feedback. |
| `src/main/java` | `app/socialsecurity/LongevityFinancialObservation.java` | Worker observation interface used by weighted evaluation, the continuation evaluator and the v2 evidence collector. |
| `src/main/java` | `app/socialsecurity/results/ArkansasAnalysisProvenanceReader.java` | Version-dispatched JSON reader selected by the Arkansas analyzer-provenance annotation. |
| `src/test/java` | `app/socialsecurity/ArkansasProvenanceV2CapacityProbeTest.java` | V2 lossless receipt attribution, archive limits and simultaneous-death carriers. |
| `src/test/java` | `domain/projection/ArkansasProductionPerformanceTest.java` | Joint retirement with funding and Roth proof, Monte Carlo and retained-history queue contention. |
| `src/test/java` | `ui/montecarlo/ArkansasMonteCarloLaunchTest.java` | Four analyzer modes, actual side routing, mixed jurisdictions, survivor overrides, shared or distinct UUIDs, Save As and swaps. |
| `src/test/java` | `ui/socialsecurity/ArkansasExhaustiveLaunchTest.java` | Actual exhaustive launcher and annual candidate details. |
| `src/test/java` | `ui/socialsecurity/ArkansasIndividualAnalyzerLaunchTest.java` | Integrated deterministic and mortality launchers and genuine evidence for request-only overrides. |
| `src/test/java` | `ui/socialsecurity/ArkansasSocialSecurityLaunchTest.java` | Quick launcher and frozen Arkansas survivor details. |
| `src/test/java` | `ui/socialsecurity/ArkansasWeightedLauncherPerformanceTest.java` | Weighted launcher, v2 archive round-trip and projection-owned History after reopening in both themes. |

## 4. Candidate tree and staging

The opening candidate was based on HEAD with fourteen explicit path additions or updates. The isolated index used the repository's line-ending configuration, recorded the new blobs in its own object store and read existing blobs from HEAD. `git write-tree` produced `39f2cc72d93ace7f41c8f7aaf87fc30fc5fd789d`. `git archive` followed by extraction into `target/phase6e-p3d2a/candidate` produced a fresh tree of 1,785 files. The inventory and all normalized blob hashes matched. The final source candidate, tree `deb7ae139a24bf294d8a77e68b7460db96f68523`, adds the clean-test output-directory correction and was freshly extracted into `target/phase6e-p3d2a/candidate-final`. The production tree was `60d934708df4580386e3234279ed797f222cdefe` in both snapshots. Both resolved Maven dependencies from the established local cache. UI driver sources and synthetic plans live outside the candidate snapshots. [Opening blob verification](target/phase6e-p3d2a/archive-verified.txt) · [Final tested build inputs](target/phase6e-p3d2a/final-tested-build-inputs.txt)

The repository's `git add -- <approved paths>` returned exit 128 and reported permission denied on `.git/index.lock`. The isolated index passed `git diff --cached --check <HEAD>` and its staged `--stat` and `--name-status` were inspected. A Markdown line break in the P3D2 report was converted to a blank line to pass the whitespace check. [Candidate manifest](target/phase6e-p3d2a/candidate-tree-manifest.txt) · [Proposed patch](target/phase6e-p3d2a/proposed-checkpoint.patch)

## 5. Clean compilation

The baseline used its own Git archive of HEAD and exited 1 at Java compilation. The log lists the three missing symbols and consequential interface errors. The new candidate compiled production and test classes with offline Maven, including all ten restored files. Temurin 25.0.4.1, JavaFX 25, IntelliJ Maven 3.9.9 and the repository's `.codex-m2/repository` were used on Windows 11 x64, build 26300. The archive had no Git metadata and used the normal archive build-identity fallback. [Tracked-only compile](target/phase6e-p3d2a/baseline.log) · [Candidate compile and focused tests](target/phase6e-p3d2a/focused.log)

The real `MainApplication` launched from the freshly compiled classes with native JavaFX 25 modules. Windows UI Automation observed the main window handle, Aledrix title, File menu and Recalculate control. Invoking Recalculate on a synthetic single-person plan produced an Arkansas 2026–2029 planning estimate. [Window](target/phase6e-p3d2a/native/smoke-window.txt) · [Projection](target/phase6e-p3d2a/native/smoke-results.txt)

## 6. Candidate regression results

The opening candidate passed 368 focused cases, 55 separately selected Exhaustive/Search cases, five capacity/performance cases and twelve scaling cases. The final source candidate, which includes the output-directory correction, passed 393 focused cases, including the 25 contractual-premium cases. Its separately selected suites passed again. The final bounded run discovered 5,499 tests, with 5,464 passed, 35 skipped, zero failures, zero errors and exit 0. It completed in 9 minutes 16 seconds. [Final bounded log](target/phase6e-p3d2a/final-bounded.log)

## 7. Survivor-election Save

`AssumptionsViewTest` revalidated Arkansas primary death and Michigan spouse death. The disabled immediate-at-death control displays the derived attained age while ordinary Save and an unrelated Assumptions Apply preserve the stored election of 65. Deliberate death-year changes still use the validated new context. A separate clean-tree controller and JavaFX editor diagnostic displayed 82 in the Arkansas fixture and 84 in the Michigan fixture. Ordinary Save, Save As and reopen preserved 65, the exact accepted projection UUID, the input fingerprint and the annual financial record in both cases. The quiet native application also used File Save and kept Arkansas's stored 65 with a matching accepted fingerprint. Native Save As chooser completion remains open. [Save As and fingerprint evidence](target/phase6e-p3d2a/native/survivor-round-trip-verified.log) · [Native saved inputs and archive](target/phase6e-p3d2a/native/archive-validation.log)

## 8. Historical Arkansas restoration

The weighted launcher regression ran at both scales, created genuine v2 outputs, saved the plan, reopened it, and verified that the actual History dialog presented the accepted projection UUID and its committed immutable component. The controller verifies original dependencies first. The read-only view applies the recorded policy context when checking compatibility. Live freshness remains governed by the separate live binding checks. The candidate included association, freshness, swap and persistence tests for original source fingerprints, annual digests and last-success behavior. A fresh native launch of the 25-year plan used the associated-result action and opened the original heat map and committed component under UUID `b28cb4a7-fa21-48fa-8a15-8e5cc54a041f`. [Native History](target/phase6e-p3d2a/native/reopen.stdout.log)

## 9. Windows acceptance matrix

**A** means direct interactive desktop operation. **B** means a real Windows JavaFX application with programmatic control activation. **C** means automated component or integration evidence from the exact candidate. **D** means manual verification remains.

| Workflow | Candidate evidence | Outstanding interaction |
| --- | --- | --- |
| Startup, resources, Arkansas selection and Recalculate | B. Actual main window and four-year AR results. C. Registry, chooser, startup and projection checks. | D. Manual focus and keyboard. |
| Guided single and couple, Assumptions | B. Created both plan shapes as Arkansas from the actual wizard, then Cancel retained Arkansas and Apply accepted Texas. C. Wizard and state-choice tests. | D. Direct keyboard and focus checks. |
| Standalone accounts, income, expenses and Roth editors | B. Renamed the IRA and pension, changed expense to $40,100 and switched Roth frequency to Annual. Ordinary Save preserved all four edits and a new projection UUID. | D. Direct traversal and resizing. |
| Pension review | B. Actual 2026 pension-origin warning, navigated to the Unknown source, cancelled, reopened, applied the qualified origin and saved. C. Previous result preservation and both survivor directions. | D. Direct interaction and remaining warning categories. |
| Save, Save As, Open, reload and role changes | B. File Save, startup reload and both AR/TX swaps preserved both original role UUIDs and digests. C. Save As, reopen and component ownership. | D. Keyboard and system Open/Save As chooser. |
| Analyzer runs and cancellation | B. Full weighted, cancel, History and four ordered mixed-state paired runs. C. Launcher and ownership matrix. | D. Direct interaction and remaining cancellation cases. |
| PDF and CSV | B. Actual format and PDF action dialogs. C. Compared frozen monetary and policy fields on the native-produced component. | D. System destination chooser, OS-associated PDF viewer and visual review. |

The candidate was launched in session 1. Programmatic operation is reported as B even when it uses Windows UI Automation. The Save As menu action was invoked, but the system chooser did not provide a safe controllable target. The foreground HWND probe returned zero, so physical keyboard and mouse input could not be verified. Direct interactive desktop operation remains open. [Native logs](target/phase6e-p3d2a/native) · [Both saved role orientations](target/phase6e-p3d2a/native/swap.stdout.log)

## 10. Social Security analyzers

Candidate tests covered the production Quick Comparison, Deterministic Exhaustive, Longevity-Weighted, Individual Deterministic Integrated and Individual Integrated Mortality launchers. The bounded suite includes benefit-only Social Security algorithm and freshness tests. Tests preserve the successful projection UUID, exact claiming and mortality context, financial evidence and stable ranking. Opening-date death uses the existing zero-engine path. The actual app completed couple Social Security Only with mortality conditioned at January 1, 2060 and valuation held at January 1, 2030. Its results fed the normal Quick Comparison action, which completed and committed its Arkansas plan association. An earlier driver selected the valuation control by matching a tooltip that mentioned mortality conditioning. Correcting the driver resolved that failed attempt.

The candidate's actual individual window completed nine candidates in each of its three modes. Social Security Only used zero financial engine calls and Integrated Mortality used 459. The deterministic integrated result used the configured plan baseline. The focused regression covered request-only death overrides, failed candidates, ranking and association. P3D2 supplied the full native Exhaustive result used in this acceptance, and the exact final candidate separately passed its exhaustive launcher tests. [Actual couple Social Security and Quick launch](target/phase6e-p3d2a/native/native-ss-quick-verified.stdout.log) · [Actual individual modes](target/phase6e-p3d2a/native/individual-all.stdout.log)

## 11. Monte Carlo and paired analysis

The candidate launcher tests exercised fixed and longevity-adjusted simulations and both paired modes, including AR/AR, AR/another admitted state and the reverse. Ordered Current/A and Baseline/B identity survived equal documents, distinct UUIDs or an explicitly shared UUID, temporary survivor elections, role swaps and Save As. Unsupported tax outcomes remain separate from proved financial ruin. Cancellation kept last success. The actual Windows app additionally ran Fixed and Longevity in both ordered Arkansas/Texas orientations, 1,000 requested worlds each. Every world completed for both sides in each of the four runs. The longevity comparison used run-only ages 64 and 66. All four ordered associations were observed on disk. The driver closed while a later save callback was draining and emitted shutdown exceptions. Independent reread verified all four archives and the distinct original UUID and digest bindings. Relaunching the app opened the original paired longevity result for each role with a committed immutable History notice. [Native mixed paired results](target/phase6e-p3d2a/native/mixed-pairs.stdout.log) · [Four archive bindings](target/phase6e-p3d2a/native/mixed-archives.log) · [Both native History views](target/phase6e-p3d2a/native/mixed-history.stdout.log)

The final candidate also completed standalone Fixed and Longevity-Adjusted from its actual Analysis menu. That longevity run reported 1,000 completed simulations and zero funding failures. [Native standalone modes](target/phase6e-p3d2a/native/standalone-mc.stdout.log)

## 12. Pension review and tax unavailability

The focused candidate suite verified qualified employer pensions, material Unknown origin, source owner versus actual recipient, both survivor directions, review navigation, Apply and Cancel, and independent Alabama DB/DC classification. Typed refusal cases include source arrangements, early distribution timing, additional tax, basis, low-income coverage, NIIT evidence and proof capacity. Domain tests distinguish proven shortage and tax unavailability. UI tests preserve a previously accepted projection and reject partial replacement. The release-integrity work leaves Arkansas's modeling scope intact.

## 13. Historical v1/v2 compatibility

The v1 reader preserves the historical contract. The v2 reader verifies its explicit version, associated projection UUIDs, exact frozen inputs, canonical annual evidence, policy context, and its lossless pension facts and attribution. Clean-tree capacity and codec tests checked bounded structures and malformed rejection. Save As and swap tests used original components. Earlier results remain historical where their live dependencies differ. The actual Quick result proved a v1 archive with 65 genuine executions and 1,625 completed annual rows. A fresh application launch opened it under its original projection UUID and committed status. The weighted and paired native archives used v2. [V1 decode and bindings](target/phase6e-p3d2a/native/quick-archives.log) · [V1 native History](target/phase6e-p3d2a/native/quick-history.stdout.log)

## 14. Performance and JavaFX responsiveness

The quiet repeat used the P3D2 alternate 25-year Arkansas couple, primary death in 2045, original survivor election 65, $575,000 primary traditional IRA, four employer pensions with survivor income, year 2030 one-time 22% bracket-fill Roth conversion, and both analyzer-only survivor ages of 65. The child VM had `-Xmx768m` and Dark 150% JavaFX. Windows exposed eight logical processors. The initial candidate compile and tests finished before the timed run and the comparison benchmark started after the run. System-wide load was not controlled.

The full run presented all 5,184 successful rankings in 124,459 ms from the Run action, including worker execution and UI publication, and saved the committed manifest in 125,947 ms. Sampled Java heap peaked at 304 MiB. The archive captured 2,600 mortality worlds, 8,200 genuine executions, 418,200 annual rows and 1,672,800 logical pension receipts. There were 404 facts, 100 shared vectors and 20,400 encoded references. The archive was 19,174,123 raw JSON bytes and 1,175,232 compressed bytes. Independent bound decode took 1,372 ms, dependency validation 186 ms and re-encode 382 ms, measured separately. [Quiet full run](target/phase6e-p3d2a/native/quiet-weighted.stdout.log) · [Archive measurements](target/phase6e-p3d2a/native/archive-validation.log)

The 2,436 independent JavaFX dispatch probes measured 8.158 ms median, 24.974 ms p95, 29.054 ms p99 and 203.690 ms maximum. The 564 separate view probes peaked at 117 ms. Two delayed samples near completion showed ToggleButton skin initialization and text layout on JavaFX. The sampling thread's maximum gap was 64.908 ms. The GC log recorded a longest individual pause of 11.674 ms across the process. The prior 650 ms and 1,389 ms delays did not recur in this repeat. Final-result presentation contributed to the measured tail. These observations do not isolate the earlier delays or establish a general latency guarantee.

A fresh controlled cancellation during strategy-equivalence work dispatched in 62 ms and showed the cancelled status within 326 ms, including the polling interval. A separately instrumented 64-world test with six overlapping history reads measured 151.44 ms to first-year progress, 0.029 ms to revoke on the owner and 0.365 ms to stop the worker. The ten-run deterministic sample was 15.93 ms median and 39.01 ms p95. The 64-world run took 759.68 ms and recorded zero proof exhaustions or funding failures. The 100-strategy capacity test retained all 18,616 logical receipts. [Cancellation](target/phase6e-p3d2a/native/quiet-cancel.stdout.log) · [First capacity and contention run](target/phase6e-p3d2a/capacity.log)

## 15. PDF and CSV

The candidate passes the dedicated historical exhaustive PDF suite and its standard historical export and renderer regressions. The two Arkansas PDF and CSV exports were rendered again from the verified, native-produced alternate 25-year component. Under each result scope the comparison checked Arkansas and federal tax, gross and taxable funding, executed Roth conversion, assets and estate against every frozen year. PDF text checked separate tax lines, the retirement exclusion and all three approved policy labels. The actual application opened its export format chooser and the View PDF / Save PDF / Cancel dialog at Dark 150%. Cancel was used. The specialized Arkansas projection PDF/CSV includes the retirement exclusion and senior elections. Per-account ending balances and survivor claiming elections are available in the projection and its accepted inputs. They are outside those export fields. External PDF viewing, Save PDF and CSV destination interaction remain manual. [Export verification](target/phase6e-p3d2a/native/exports.log) · [Actual export dialogs](target/phase6e-p3d2a/native/pdf-choice.stdout.log)

## 16. Light, Dark and scaling

One 100% and one 150% candidate run each passed six cases. Both theme variants covered warning, progress, pension review, Results Summary, Annual Detail and weighted History. Reviewed the captured warning wrap, visible review and Cancel controls, and Summary and Annual Detail at the test sizes. The summary table clips column labels to the configured widths and provides horizontal scrolling. The application launch requests JavaFX render scale explicitly. System chooser DPI, keyboard traversal, focus, modal behavior and clipping in a human-operated session require direct acceptance. [Final extracted-tree captures](target/phase6e-p3d2a/candidate-final/target/phase6e-p3d1-previews)

## 17. Official Arkansas tax policy

The November 19 review found the Arkansas special-session 2026 individual rate changes in Act 2 and the official published rule on employment-related retirement income. The DFA forms index, accessed via its indexed copy after a direct HTTP 403, listed the 2026 estimated-payment packet and final annual resident/nonresident forms for tax year 2025. No final 2026 AR1000F/AR1000NR instructions or annual tables were verified. The May 2026 AR1000ES packet is estimated-payment guidance. The 2025 annual guidance provides the latest verified filing examples for the qualifying exclusion, IRA age and additional-tax/basis provisions, Social Security exemption and low-income table conditions. [Act 2](https://www.arkleg.state.ar.us/Home/FTPDocument?path=%2FACTS%2F2026S1%2FPublic%2FACT2.pdf) · [Retirement income rule](https://codeofarrules.arkansas.gov/Rules/Rule?chapterID=33&levelType=section&partID=941&sectionID=40225&subChapterID=261&subPartID=6195&titleID=26) · [DFA forms](https://www.dfa.arkansas.gov/office/taxes/income-tax-administration/individual-income-tax/forms/) · [2025 annual instructions](https://www.dfa.arkansas.gov/wp-content/uploads/2025_AR1000F_and_AR1000NR_Instructions.pdf) · [2026 estimated-payment packet](https://www.dfa.arkansas.gov/wp-content/uploads/2026_Final_AR1000ES_2.pdf)

The approved `AR-2026-P2-1`, `AR-FUNDING-P2-1` and `AR-TARGET-P3A-1` policies, statutory cents, owner-specific retirement pools, and evidence limitations are intact. Holding nominal parameters for future projections is a planning assumption. A conflict affecting tax or eligibility would require separate approval.

## 18. Existing-state invariance

The selector admits Arkansas in addition to AK, AL, AZ, FL, GA, IA, IL, KY, MI, MS, NC, NH, NV, OK, PA, SD, TN, TX, VA and WY. Colorado remains disabled. The only behavior changes from HEAD are the P3D2 UI fixes carried into the candidate. Existing monetary goldens, federal tax rules, Social Security calculations, Medicare, RMDs, Roth and other state engines were not edited.

## 19. Exact commands, results and logs

Maven invocations used the following common PowerShell variables. Unless stated otherwise, Maven ran from the extracted candidate root. Test runs also supplied `"-Duser.home=$profile"` with a synthetic profile and a child-local LOCALAPPDATA. All logs live in `target/phase6e-p3d2a`.

```powershell
$m = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd'
$repo = '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository'
# Focused and ancillary profile: target/phase6e-p3d2a/test-profile
# Opening bounded profile: target/phase6e-p3d2a/bounded-test-profile
# Final focused and ancillary runs: target/phase6e-p3d2a/final-test-profile
# Final bounded rerun: target/phase6e-p3d2a/restarted-bounded-profile
```

| Arguments after `& $m $repo` | Discovered | Passed | Skipped | Failed | Errors | Exit | Log |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `-o -B -f target/phase6e-p3d2a/baseline/pom.xml compile` (from repository root) | — | — | — | — | Compile failed | 1 | `baseline.log` |
| `"-Duser.home=$profile" -o -B '-Dtest=*Arkansas*,!*Performance*,!*CapacityProbe*,!*Exhaustive*,AssumptionsViewTest,PlanFileOperationsTest,ProjectionSnapshotPersistenceTest,Phase12EAssociationTest,Phase12FAnalyzerSwapTest,Phase12GFreshnessTest,SocialSecurityPdfWorkflowIntegrationTest,MonteCarloStrategyComparisonTest,ColoradoWorksheetCompletionTest' test` | 368 | 368 | 0 | 0 | 0 | 0 | `focused.log` |
| `"-Duser.home=$profile" -o -B '-Dtest=ArkansasExhaustiveLaunchTest,ExhaustiveSnapshotTest,ExhaustiveSnapshotInterningTest,ExhaustiveHistoricalPdfFidelityTest,ExhaustivePersistenceTest,IntegratedSocialSecurityCompleteStrategySearchCalculatorTest' surefire:test` | 55 | 55 | 0 | 0 | 0 | 0 | `excluded.log` |
| `"-Duser.home=$profile" -o -B '-Dtest=ArkansasProductionPerformanceTest,ArkansasProvenanceV2CapacityProbeTest' surefire:test` | 5 | 5 | 0 | 0 | 0 | 0 | `capacity.log` |
| `"-Duser.home=$profile" -o -B '-Dglass.win.uiScale=1.0' '-Dtest=ArkansasWorkflowUiTest,ArkansasHistoryUiTest,ArkansasWeightedLauncherPerformanceTest' surefire:test` | 6 | 6 | 0 | 0 | 0 | 0 | `scale-100.log` |
| `"-Duser.home=$profile" -o -B '-Dglass.win.uiScale=1.5' '-Dtest=ArkansasWorkflowUiTest,ArkansasHistoryUiTest,ArkansasWeightedLauncherPerformanceTest' surefire:test` | 6 | 6 | 0 | 0 | 0 | 0 | `scale-150.log` |
| Opening candidate, `"-Duser.home=$profile" -o -B '-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*Performance*,!*Exhaustive*,!*Search*' test` | 5,499 | 5,463 | 35 | 0 | 1 | 1 | `bounded.log` |
| Final candidate, `"-Duser.home=$profile" -o -B '-Dtest=*Arkansas*,!*Performance*,!*CapacityProbe*,!*Exhaustive*,AssumptionsViewTest,PlanFileOperationsTest,ProjectionSnapshotPersistenceTest,Phase12EAssociationTest,Phase12FAnalyzerSwapTest,Phase12GFreshnessTest,SocialSecurityPdfWorkflowIntegrationTest,MonteCarloStrategyComparisonTest,ColoradoWorksheetCompletionTest,ContractualPremiumMonteCarloTest' test` | 393 | 393 | 0 | 0 | 0 | 0 | `final-focused.log` |
| Final candidate, `"-Duser.home=$profile" -o -B '-Dtest=ArkansasExhaustiveLaunchTest,ExhaustiveSnapshotTest,ExhaustiveSnapshotInterningTest,ExhaustiveHistoricalPdfFidelityTest,ExhaustivePersistenceTest,IntegratedSocialSecurityCompleteStrategySearchCalculatorTest' surefire:test` | 55 | 55 | 0 | 0 | 0 | 0 | `final-excluded.log` |
| Final candidate, `"-Duser.home=$profile" -o -B '-Dtest=ArkansasProductionPerformanceTest,ArkansasProvenanceV2CapacityProbeTest' surefire:test` | 5 | 5 | 0 | 0 | 0 | 0 | `final-capacity.log` |
| Final candidate, `"-Duser.home=$profile" -o -B '-Dglass.win.uiScale=1.0' '-Dtest=ArkansasWorkflowUiTest,ArkansasHistoryUiTest,ArkansasWeightedLauncherPerformanceTest' surefire:test` | 6 | 6 | 0 | 0 | 0 | 0 | `final-scale-100.log` |
| Final candidate, `"-Duser.home=$profile" -o -B '-Dglass.win.uiScale=1.5' '-Dtest=ArkansasWorkflowUiTest,ArkansasHistoryUiTest,ArkansasWeightedLauncherPerformanceTest' surefire:test` | 6 | 6 | 0 | 0 | 0 | 0 | `final-scale-150.log` |
| Final candidate, `"-Duser.home=$profile" -o -B '-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*Performance*,!*Exhaustive*,!*Search*' test` | 5,499 | 5,464 | 35 | 0 | 0 | 0 | `final-bounded.log` |

The validation daemon restarted during the first bounded run on the final source candidate. Maven had written no suite totals or process exit. The partial log was preserved, and the full selector completed on the same candidate with a new isolated profile. The final source candidate passed an independent normalized-blob check across all 1,785 extracted tracked files. Native programs used the normal JavaFX entry point in an isolated Windows process. Their exact JVM arguments are preserved as `native/<run>.args` and their results as `native/<run>.stdout.log` or the linked verification logs. [Interrupted log](target/phase6e-p3d2a/final-bounded-daemon-interrupted.log) · [Final archive check](target/phase6e-p3d2a/final-archive-verified.txt) · [Maven and quiet-run statuses](target/phase6e-p3d2a/exits.txt)

## 20. Defects corrected

- Restored the missing sources to the proposed Git tree. This addresses source-control completeness and preserves the verified implementation.
- Included both accepted P3D2 corrections and their tests. The layer changes preserve selected survivor inputs and use the verified archive's frozen policy context for historical presentation.
- The first bounded run reproduced a test-only clean-tree defect. `ContractualPremiumMonteCarloTest` wrote `target/phase23c/bounded-performance.json` before creating its parent. The test now creates its own output directory and retains the same measurements, cancellation check and financial assertions. The original failure is retained in `bounded.log`. The final repeat used a fresh archive without that directory pre-created.

## 21. Remaining manual acceptance

- Confirm the actual repository index in a Git session with write access, compare it with the verified proposal, and authorize the checkpoint separately.
- Run the workflow matrix on an interactive desktop. Prioritize native Open and Save As, external View PDF, Save PDF, CSV destination, focus and chooser DPI at both scales.
- Repeat the analyzer and editor paths with direct input. Complete the interactive cancellation/supersession matrix, native Save As for paired longevity history and warning review in both survivor directions. Verify the remaining typed warning presentations.

## 22. Proposed checkpoint contents

The proposed checkpoint includes the ten files in section 3 and six additional files.

- `src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ProjectionAnalyzerHistoryDialog.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/ContractualPremiumMonteCarloTest.java`
- `PHASE-6E-P3D2-ARKANSAS-NATIVE-ACCEPTANCE.md`
- `PHASE-6E-P3D2A-ARKANSAS-RELEASE-INTEGRITY.md`

The final report is the sole difference between the tested snapshot and the proposed Git tree. The exact staged inventory passed `git diff --cached --check`, and replaying the binary-safe patch against an independent index initialized to HEAD reproduced the proposed tree exactly. Generated output, logs, synthetic plans and diagnostic artifacts are outside the proposal. With a writable repository index at the recorded HEAD, the verified patch can be checked with `git apply --cached --check target/phase6e-p3d2a/proposed-checkpoint.patch` and staged with the same command omitting `--check`. [Exact path and status manifest](target/phase6e-p3d2a/proposal-name-status.txt) · [Stat](target/phase6e-p3d2a/proposal-stat.txt) · [Tree identity](target/phase6e-p3d2a/proposal-tree.txt) · [Replay verification](target/phase6e-p3d2a/proposal-verification.txt) · [Checkpoint patch](target/phase6e-p3d2a/proposed-checkpoint.patch)

The proposed commit message is `Restore complete Arkansas release checkpoint and preserve native acceptance fixes`.

## 23. Known unsupported scenarios

Arkansas refuses unsupported retirement sources, uncertain timing or basis, additional taxes, material low-income and NIIT evidence gaps, and calculation or archive coverage beyond the approved limits. The application distinguishes those refusals from proved financial shortfalls and preserves the last successful result. Colorado is outside production admission. The specialized projection export field limitation is recorded in section 15.

## 24. Final readiness classification

**NATIVE_ACCEPTANCE_INCOMPLETE**

The 16-file checkpoint proposal is independently reproducible and passed the required automated verification. It is ready for review and transfer to the repository index once `.git/index.lock` can be created. HEAD still has the original missing-source defect until the proposed checkpoint is approved and committed.

Programmatic operation of the real Windows application and the automated scale tests passed. Direct interactive desktop acceptance, including system file choosers, the external PDF viewer, keyboard and focus behavior, and the remaining warning and cancellation workflows, is still open. Release approval remains pending.
