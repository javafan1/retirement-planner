# Phase 6E-P3D2 — Arkansas native Windows acceptance

**Acceptance date:** November 19, 2026

**Classification:** `RELEASE_BLOCKED`

## 1. Executive summary

The complete local working tree starts Aledrix on Windows, calculates Arkansas projections, and retains and reopens a full 5,184-strategy weighted result through its production UI. Acceptance found and corrected a Save defect that rewrote a survivor election and a History defect that rejected genuine Arkansas evidence. The tracked-only release checkpoint independently fails compilation because it omits three referenced production Java types and seven test sources. Release approval remains blocked. The native and automated results below apply to the complete local working tree.

## 2. Opening Git checkpoint

- `main` matched its local `origin/main` tracking reference at `dcf013bb8d6149fa8dd5a04a58046e5ee56dfca0`, “Complete Arkansas production activation and analyzer provenance v2”. The preceding checkpoint was `a1273a31`, the P3C3 baseline.
- The index and tracked working-tree diff were empty. The Git status listed ten preexisting untracked Java sources. Section 21 records their names and locations.
- Read the P3D1 activation, P3D1B capacity audit and P3D1B v2 implementation reports. Verified the registry, production MainApplication/MainWindow entry points, controlled projection and analyzer dispatch, historical component and analysis codecs, paired attribution and export paths in source. [Production activation](PHASE-6E-P3D1-ARKANSAS-PRODUCTION-ACTIVATION.md) · [Capacity audit](PHASE-6E-P3D1B-ARKANSAS-ANALYZER-PROVENANCE-CAPACITY.md) · [V2 implementation](PHASE-6E-P3D1B-ARKANSAS-PROVENANCE-V2-IMPLEMENTATION.md)
- Copied tracked `pom.xml` and `src/main` files into the ignored build directory `target/p3d2-tracked-84525646` and ran Maven `compile` against that copy. `git ls-tree` verified that the referenced types are absent from HEAD. See section 19 for the command and section 20 for the failure.

## 3. Native Windows environment

Windows 11 x64, build reported by .NET as 26300, in Windows session 1. The process saw eight logical processors and two 1920 × 1080 displays. Java was Eclipse Temurin 25.0.4.1, JavaFX 25 Windows modules, and Maven was IntelliJ's bundled 3.9.9. Hardware inventory via CIM was denied. Timings include a shared workstation with concurrent test and diagnostic activity.

Started the real `MainApplication` with the project’s compiled classes and locally cached OpenJFX Windows modules. The IntelliJ JavaFX Maven run configuration invokes the same application. The project-local Maven cache lacked the JavaFX Maven launcher plugin, so acceptance used its direct module launch equivalent with an isolated profile per scenario. The actual Aledrix HWND, title, size, menus and enabled controls were observed with Windows UI Automation. A separate driver entered the same `MainApplication.main` and exercised production control actions on the JavaFX application thread. It observed the branded splash, version 0.91.0, build `git-dcf013bb8d61`, and the main window handoff. Only synthetic plan documents under `target/phase6e-p3d2-native` were used.

The foreground Windows handle was unavailable to the automation process. Native UI Automation could invoke main-window controls but could not safely send global keyboard or mouse input. Native file-chooser completion, real keyboard/focus behavior and external PDF viewer validation remain open. Programmatic control activation in real windows and JavaFX component tests are described as such.

## 4. Production-state admission

Production choices are AK, AL, AZ, AR, FL, GA, IA, IL, KY, MI, MS, NC, NH, NV, OK, PA, SD, TN, TX, VA and WY. AR uses the controlled production engine and annual state worksheet with funding and Roth certification. The general scalar state tax path does not admit Arkansas. Colorado has no production state selection or execution route. Jurisdiction identity comes from the application model, and accepted historical financial evidence is validated separately. The Texas native control loaded and recalculated with its ordinary Texas presentation. [Production registry](src/main/java/com/daviddunn/retirementplanner/domain/model/StateTaxJurisdiction.java)

## 5. Single-person and couple plan workflows

The native application opened an Arkansas four-year single-person plan and two Arkansas 25-year couple plans, including a primary-death case with a survivor age of 65. The fixture documents started without recorded results. Invoking the actual Recalculate Projection button produced 2026–2029 and 2030–2054 result ranges with the Arkansas planning-estimate disclosure. The Texas control produced ordinary production results over 2030–2054. The starter windows, Assumptions summary and Recalculate actions were observed through Windows UI Automation.

Programmatic controls in the actual Windows MainApplication completed guided new single and couple plans. Both selected Arkansas through the ordinary wizard choice, checked all 21 choices and the absence of Colorado, and opened the created plan's Assumptions. Cancel discarded a draft change to Texas and Apply accepted it. The guided fixture populated accounts, Social Security, pensions and expenses through the real wizard editors. A native single-person projection saved using the ordinary File menu. Complete manual entry and keyboard testing in the standalone income/account/expense/Roth editors, along with native file choosers, remain open. Native traces are under `target/phase6e-p3d2-native`.

## 6. Deterministic tax calculation

The focused suite exercised the existing Arkansas numerical fixtures for employment and survivor pensions in both death directions, recipient-specific exclusions, explicit qualifying receipt facts, partial-year IRA age evidence, Medicare and IRMAA feedback, RMDs, Roth and traditional accounts, and fixed and targeted Roth conversions. The existing cent-level shortfall test preserves genuine insufficiency. Additional coverage exercised annuity basis, NIIT, taxable brokerage funding and low-income ceilings. Existing expected monetary values and rule versions were used.

The full couple fixture begins January 1, 2030 and has a 25-year configured horizon. Each owner has two employer pensions of $3,400 and $2,700 per month with 2% COLA and survivor amounts of $2,650 and $2,100. Their Social Security full-retirement benefits are $3,000 and $1,000 per month with configured claiming age 67. The primary traditional and Roth opening balances are $500,000 and $100,000. Spouse opening balances are $420,000 traditional, $130,000 Roth and $200,000 taxable brokerage. The plan has $40,000 annual spending and a one-time 22% bracket-fill Roth conversion in 2030. The alternate native plan increases the primary traditional account balance by $75,000 and configures primary death in 2045 with survivor age 65. The native openings cover survivor-free and primary-death plans. Exact golden and opposite survivor direction comparisons are automated evidence.

## 7. State tax unavailable and pension-source review

The production warning reports a tax year and typed issue. The controlled path distinguishes pension-source ambiguity, unsupported retirement arrangements, early IRA timing, additional tax, unresolved basis, material NIIT and low-income uncertainty, and funding or Roth proof limitations from proven financial insufficiency. Unsupported years cannot publish a monetary projection, including a successful prefix.

On the real Dark 150% window, a materially ambiguous $12,000-per-month source produced the 2026 State Tax Unavailable modal. Review Pensions opened the original pension with origin Unknown. Cancel closed it. Recalculation produced the same warning, after which selecting employer retirement origin and confirming produced a new Arkansas projection and an ordinary Save. `ArkansasWorkflowUiTest` additionally verified exact previous-result preservation and independent Alabama defined-benefit classification. The projection and pension preflight tests covered original owner and recipient in both survivor directions. The initial multi-pension native fixture with one unknown origin cleared preflight because of its other qualifying income. That run provides no warning evidence. [Native warning trace](target/phase6e-p3d2-native/final-pension.stdout.log)

## 8. Social Security analyzer acceptance

| Route | Working-tree automated production path |
| --- | --- |
| Social Security Only | Existing benefit-only execution and freshness regressions, independent of Arkansas financial proof |
| Couple Quick Comparison | Real launcher against an accepted Arkansas projection. Genuine annual results and retained association |
| Couple Deterministic Exhaustive | Dedicated launcher and selected full annual detail, compact candidate coverage, rank and historical export |
| Couple Longevity-Weighted | Real launcher in Light and Dark. Standard 5,184-candidate universe with a narrowly conditioned mortality fixture, exact dependency and evidence round-trip |
| Individual Deterministic Integrated | Real launcher with explicit death-year override and original configured projection preserved |
| Individual Integrated Mortality | Real launcher with exact annual/death context. Opening-date zero-engine-call semantics covered by longevity unit tests |

The native individual analyzer opened from Analysis and completed all nine candidates in each mode. It reported zero financial engine calls for Social Security Only and 459 for Longevity-Weighted Integrated. The deterministic native run used the configured baseline. The request-only override and opening-date death were checked in the dedicated automated tests. The native couple Exhaustive Run on the three-year variant persisted a 141,212-byte v2 last-success result. Its decoded evidence verified 5,184 ranked candidates, 5,185 genuine executions including the baseline, 15,555 annual rows and 20 retained detail candidates against the originally accepted UUID and digests.

The native couple Social Security Only entered its retirement-grid stage with the standard mortality inputs. Its end-to-end completion was unverified within the bounded attempt. An extreme-age conditioning shortcut returned a generic calculation failure, so it supplied no result for a native Quick run. Quick used the actual launcher in automated regression. The associated Arkansas source remains the exact accepted UUID, inputs and annual digest. V2 carries observed candidate identity, claiming decisions, outcome and reduced result binding. Native full-window weighted results are recorded in section 10. Manual interaction, candidate-by-candidate rank review, couple Quick and Exhaustive native cancellation and individual restored analyzer views remain open. [Individual native trace](target/phase6e-p3d2-native/final-individual.stdout.log) · [Native Exhaustive archive validation](target/phase6e-p3d2-native/couple-native-exhaustive-archive.log) · [Couple Social Security progress](target/phase6e-p3d2-native/couple-ss-observe.stdout.log)

## 9. Monte Carlo acceptance

The dedicated launch tests covered fixed and longevity-adjusted runs, fixed and longevity paired comparison, Arkansas/Arkansas, both mixed-jurisdiction orientations, equal inputs with distinct UUIDs, a shared valid UUID and explicit, ordered roles. Run-only survivor elections bound the effective amended input and preserved the accepted original. Automated swap, Save As, reload and frozen association checks covered both orientations. Each side dispatches to its own admitted engine, with Arkansas under its annual worker scope. Proven shortage and unsupported tax remain separate model outcomes.

The production Windows Monte Carlo window completed fixed and longevity-adjusted runs from the Analysis menu using the offered 1,000-simulation count. The longevity view reported 1,000 completed lifetime worlds and zero funding failures for this strong-balance fixture. Initial cache writes received Access Denied at the normal Windows LOCALAPPDATA directory outside the workspace. Subsequent child-process profiles redirected only their own LOCALAPPDATA into the acceptance directory and completed last-success cache writes. The standard projection remains separately bound to the immutable plan association. [Native Monte Carlo trace](target/phase6e-p3d2-native/durable-monte.stdout.log)

The actual paired window also completed 1,000 fixed and 1,000 longevity worlds, using an Arkansas current and baseline sharing one valid accepted projection. Both sides completed every requested world in each run. The saved baseline referenced the same UUID as Current. The run-only longevity controls supplied age 65 on each side. The fixed cache decoded as v2, verified both original UUID and annual bindings, and contained 1,000 trials in each of the ordered `CURRENT_A` and `BASELINE_B` roles. Diagnostic shutdown started during the subsequent plan save. Native paired longevity archive and paired plan reload remain unverified. The automated launch and history tests covered the durable paths, both role orientations and genuinely mixed jurisdictions. [Native paired trace](target/phase6e-p3d2-native/routes-monte.stdout.log) · [Native fixed paired archive](target/phase6e-p3d2-native/routes-monte-archive.log)

## 10. Full weighted workload and capacity

Fresh standalone diagnostic reproduced the standard profile with 5,184 claiming strategies and 2,600 positive-probability SSA period 2022 mortality worlds. All 5,184 strategies completed, with 8,200 genuine carrier executions, 418,200 annual rows, 213,200 represented candidate-scenario links, and 1,672,800 logical pension receipts. The v2 archive held 404 unique pension facts, 100 shared receipt vectors and 20,400 encoded receipt references. Raw JSON was 16,586,803 bytes and compressed evidence 1,148,049 bytes. Worker time was 128,380 ms, encode 302 ms, decode 660 ms and additional restoration validation 17 ms. A sampled heap reading was 34.8 MiB before and 135.9 MiB after. These samples are GC-sensitive.

| Fresh bounded diagnostic | Strategies × worlds | Engine runs / annual rows | Logical receipts / facts / encoded references | Raw / compressed bytes | Worker / encode / decode / restore validation ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| 100-strategy v2 fixture | 100 × 16 | 182 / 4,654 | 18,616 / 174 / 716 | 273,898 / 27,967 | 2,575 / 7 / 37 / 12 |
| Larger v2 fixture | 324 × 16 | 574 / 14,678 | 58,712 / 174 / 716 | 776,120 / 70,567 | 9,958 / 22 / 129 / 18 |
| Full table, standalone | 5,184 × 2,600 | 8,200 / 418,200 | 1,672,800 / 404 / 20,400 | 16,586,803 / 1,148,049 | 128,380 / 302 / 660 / 17 |

The 16-world diagnostics use primary deaths in 2037, 2043, 2049 and 2055 and spouse deaths in 2038, 2044, 2050 and 2056. The 100-strategy set tests retirement ages 62, 64, 66, 68 and 70 plus first and last supported survivor elections. The 324-strategy set uses all nine retirement ages plus those same survivor elections. The successful standalone runs recorded zero failed carrier proofs. The `-Xmx1g` full diagnostic had a 180-second deadline. These are bounded measurements and do not establish a performance guarantee.

In the actual native standard-plan window, the weighted Run action used both analyzer-only survivor elections of 65. The ranking displayed 5,184 rows in 124,432 ms, and the run's plan association passed ordinary Save by 126,069 ms. Sampled peak Java heap was 302 MiB with `-Xmx768m`. The 566 periodic JavaFX queue probes had an observed maximum round-trip of 96 ms. Reading the committed native result through the ordinary component reader verified the original accepted UUID, frozen input digest and annual digest with 8,200 executions, 418,200 annual rows, 2,600 worlds, 1,672,800 logical receipts, 404 facts and 20,400 encoded references. The compressed plan component was 1,175,358 bytes. Read and proof validation took 1,483 ms on the shared workstation. [Native run](target/phase6e-p3d2-native/verified-standard.stdout.log) · [Component verification](target/phase6e-p3d2-native/verified-standard-restoration.log)

The alternate Dark 150% window completed 5,184 strategies with zero failed candidates. It displayed results in 143,718 ms with a sampled peak Java heap of 239 MiB. The 640 JavaFX probes observed a maximum delay of 1,389 ms on this run, compared with 96 ms for the standard run. A repeated run with a writable last-success profile completed in 164,237 ms with 236 MiB sampled peak heap and a 650 ms observed maximum JavaFX delay. Its 1,175,235-byte cache decoded and verified in 8,016 ms, including the exact alternate input/annual binding, 8,200 executions and 1,672,800 logical receipts. The diagnostic ended during the independent ordinary plan save, so the successful alternate restorable artifact is the last-success cache. Multiple Aledrix processes and test diagnostics overlapped. The observed delay includes the UI queue, rendering and scheduling effects. No measurement attributed it to tax proof on JavaFX. [Alternate native run](target/phase6e-p3d2-native/final-alt.stdout.log) · [Alternate cache verification](target/phase6e-p3d2-native/real-alt-cache-verified.log)

## 11. Cancellation, threading and responsiveness

The native recalculation action routes Arkansas by jurisdiction through the controlled background worker. It captures plan inputs and an owner-thread ticket before dispatch. Completion is checked against the current owner, revision, projection epoch, request and immutable documents before association. The worker checks cancellation at annual calculation and proof boundaries. Analyzer launch and restoration contracts bind accepted dependencies and reject superseded results. Existing single successful results survive cancellation or unsupported replacements.

Focused tests covered before-work and in-work cancellation, source edit, supersession, plan replacement, save of retained evidence, and proving a shortage without accepting its prefix. The funding and Roth proof tests exercise their cooperative cancellation boundaries. The performance diagnostic recorded six retained reads overlapping a 64-world run, 116.63 ms from queue admission to first year, 0.029 ms to revoke on the owner, and 0.402 ms to stop the worker after revocation in that run. The 25-year deterministic sample of ten was 14.10 ms median, 48.49 ms p95. The 64-world fixed run took 784.84 ms, with zero funding failures or proof exhaustion. The observed heap delta was 150.37 MiB. These are small samples.

The actual weighted Cancel action completed dispatch in 1 ms while the full alternate-plan request was proving equivalence. The next periodic probe observed the Cancel control inactive and “Analysis cancelled” after 214 ms. This bounds UI observation at the harness polling interval. The worker stop measurement above comes from a separate fixture. [Native cancellation trace](target/phase6e-p3d2-native/accepted-cancel.stdout.log)

## 12. Historical v1/v2 compatibility

V1 retains its original evidence reader, writer, canonical digest and at-rest interpretation. V2 dispatches through its explicit version and preserves schema-three financial components. Accepted source documents, jurisdiction, UUIDs, annual digests and policy versions stay separate from effective run-only contexts. Pension facts are losslessly factored by canonical dictionary and attribution vectors with strict reference, coverage and result validation. Unknown versions, malformed indices, excess sizes, or mismatching digests fail closed. No migration or financial recomputation is involved in reading historical evidence.

The reviewed v2 evidence bounds include 8,192 pension facts, 8,192 vectors, 32,768 executions, 16,384 references per execution, 600,000 encoded references and 3,000,000 logical receipts. Document caps are 32 MiB raw, 16 MiB compressed, two million tokens, 100,000 arrays and nesting depth 80. Existing capacity and codec tests exercised limits and malformed evidence. Existing artifacts, including supplied v1 evidence, were used without modifying original files.

## 13. Current, Baseline and freshness

Automated history, analyzer association, dependency and swap tests distinguish Current and Baseline, including equal-input cases. Active freshness depends on ordered accepted dependencies and current context. Source edits leave accepted historical values and captured report inputs intact. The actual native Yearly Projection Details window showed separate federal and state tax columns, the Arkansas state label, Roth conversion, retirement-account groups and estate. [Native Annual Detail](target/phase6e-p3d2-native/probe-ui-save-single.stdout.log)

## 14. Save As and reload

Automated plan file operations, projection snapshot persistence, analyzer codec, mixed-state paired tests and two-way swap tests covered preservation of plan identity, accepted projection component identity and result association through Save As and load. The native standard plan reopened with “Arkansas retained history” and “Original projection restored from saved results.” After the History correction, the native analyzer's associated-result action opened the original weighted heat map with the same projection UUID and a committed immutable-history notice. The binary artifact independently decoded and validated against the persisted component. Native Save As chooser completion requires an interactive desktop. [Native History reopen after correction](target/phase6e-p3d2-native/corrected-reopen.stdout.log)

## 15. PDF and CSV fidelity

The working-tree tests exercised historical and current export data separately, including the dedicated historical exhaustive PDF and Monte Carlo PDF checks. Both production Arkansas renderers exported the 25-year annual component saved by the actual application, once under each current/history scope. The CSV verification compared federal and Arkansas income tax, gross and taxable funding, executed conversion, surplus, retained and ending assets, and estate against every frozen year. Both 25-page PDFs exposed separate federal and Arkansas labels, the retirement exclusion and all three approved policy versions. Arkansas projection exports carry senior retirement-pool elections and filing status. Survivor claiming elections and per-account ending balances are available in the accepted input and financial component. The Arkansas projection PDF/CSV format omits those two fields. Supported frozen analyzer reports carry their result-time claiming context. External PDF viewing, native destination browsing and interactive export checks remain open. [Frozen export verification](target/phase6e-p3d2-native/verified-standard-exports.log)

## 16. Light, Dark and scaling

The 100% and 150% Maven runs each passed six JavaFX tests. They verified output scale and exercised both themes in warning/progress, pension review, Results Summary/Annual Detail, and weighted launcher fixtures. Reviewed rendered warning wrap, visible Progress Cancel, Results Summary and Annual Detail. The Results Summary fixture uses horizontal scrolling for wider columns and value cards. The actual native HWNDs had distinct control geometry at 100% and 150%. Profiles set the application theme and JavaFX output scale. Windows Display Settings and system chooser DPI were outside the automation session. Clipping, scrolling, resizing, contrast, modal ownership, keyboard traversal and focus still need interactive review in all four theme/scale combinations. [Fixture captures](target/phase6e-p3d1-previews)

## 17. Official Arkansas rule review

Rechecked November 19, 2026. The enrolled Arkansas 2026 special-session income-tax acts apply the revised individual schedule from January 1, 2026. Act 2 establishes the graduated schedule through $94,700, the high-income worksheet above it, an adjustment through $97,600, and statutory inflation adjustment for subsequent annual schedules. The approved planning implementation uses statutory cents under `AR-2026-P2-1` and deliberately holds the approved nominal parameters for future-year projections, with separate low-income refusal boundaries. Future filing years require their actual published amounts for returns. The funding and Roth proof versions remain `AR-FUNDING-P2-1` and `AR-TARGET-P3A-1`. [Act 1](https://www.arkleg.state.ar.us/Home/FTPDocument?path=%2FACTS%2F2026S1%2FPublic%2FACT1.pdf) · [Act 2](https://www.arkleg.state.ar.us/Home/FTPDocument?path=%2FACTS%2F2026S1%2FPublic%2FACT2.pdf)

The latest final DFA annual instructions verified in public official material were for tax year 2025. They document Social Security exemption, the $6,000 qualifying retirement exclusion, the general traditional-IRA age 59½ condition and death/disability treatment, nondeductible IRA basis treatment, potential Arkansas additional tax associated with federal Form 5329 and the low-income tables. The official retirement-income rule distinguishes employment-plan qualification and spouse receipts from traditional-IRA treatment. [DFA 2025 annual instructions](https://www.dfa.arkansas.gov/wp-content/uploads/2025_AR1000F_and_AR1000NR_Instructions.pdf) · [DFA 2025 tax tables](https://www.dfa.arkansas.gov/wp-content/uploads/2025_TaxTables.pdf) · [26 CAR 100-118](https://codeofarrules.arkansas.gov/Rules/Rule?chapterID=33&levelType=section&partID=941&sectionID=40225&subChapterID=261&subPartID=6195&titleID=26)

The revised 2026 AR1000ES worksheet is an estimated-payment document. The reviewed official guidance did not verify a final 2026 AR1000F/AR1000NR annual packet, resident tables or final low-income schedule. The forms index returned HTTP 403 on direct retrieval during this review, so linked documents and indexed listings were checked separately. No verified source required a change within the approved supported calculation boundary. Reconcile the final 2026 forms when published or accessible. Any material conflict about calculated money or eligibility requires separate tax-policy approval. [DFA forms index](https://www.dfa.arkansas.gov/office/taxes/income-tax-administration/individual-income-tax/forms/) · [DFA 2025 forms](https://www.dfa.arkansas.gov/office/taxes/income-tax-administration/individual-income-tax/forms/2025-tax-forms/) · [2026 estimated-payment instructions](https://www.dfa.arkansas.gov/wp-content/uploads/2026_Final_AR1000ES_2.pdf)

## 18. Existing-state regression

The 20 previously supported states remain in the registry and retain their original calculation routes. Coverage includes Colorado preparatory and exclusion tests, Michigan pensions, Alabama DB/DC classification, federal calculations, RMD, Medicare, Social Security, Roth, seeded Monte Carlo, historical codecs and export suites. No expected numerical values were changed. See the bounded run counts below.

## 19. Reproducible commands and test counts

All Maven commands below use the same PowerShell setup from the repository root. Each log is under `target`. Counts pertain to the listed run and overlap between suites.

```powershell
$m = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd'
$repo = '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository'
```

| Run and arguments after `& $m $repo` | Discovered | Passed | Skipped | Failed | Errors | Exit | Log |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `'-Dtest=*Arkansas*,!*Performance*,!*CapacityProbe*,!*Exhaustive*,Phase12ECodecTest,ProjectionSnapshotPersistenceTest,MonteCarloStrategyComparisonTest,ColoradoWorksheetCompletionTest' test` | 241 | 241 | 0 | 0 | 0 | 0 | `phase6e-p3d2-focused.log` |
| `'-Dtest=ArkansasSocialSecurityLaunchTest,ArkansasExhaustiveLaunchTest,ArkansasIndividualAnalyzerLaunchTest,ArkansasWeightedLauncherPerformanceTest,ArkansasMonteCarloLaunchTest,AnalyzerDirectNavigationTest,Phase12EAssociationTest,Phase12FAnalyzerSwapTest,Phase12GFreshnessTest,ArkansasHistoryUiTest' surefire:test` | 92 | 91 | 1 | 0 | 0 | 0 | `phase6e-p3d2-launchers.log` |
| `'-Dtest=ArkansasProductionPerformanceTest,ArkansasProvenanceV2CapacityProbeTest' surefire:test` | 5 | 5 | 0 | 0 | 0 | 0 | `phase6e-p3d2-performance.log` |
| `'-Dglass.win.uiScale=1.0' '-Dtest=ArkansasWorkflowUiTest,ArkansasHistoryUiTest,ArkansasWeightedLauncherPerformanceTest' surefire:test` | 6 | 6 | 0 | 0 | 0 | 0 | `phase6e-p3d2-100.log` |
| `'-Dglass.win.uiScale=1.5' '-Dtest=ArkansasWorkflowUiTest,ArkansasHistoryUiTest,ArkansasWeightedLauncherPerformanceTest' surefire:test` | 6 | 6 | 0 | 0 | 0 | 0 | `phase6e-p3d2-150.log` |
| `'-Dtest=ArkansasExhaustiveLaunchTest,ExhaustiveSnapshotTest,ExhaustiveSnapshotInterningTest,ExhaustiveHistoricalPdfFidelityTest,ExhaustivePersistenceTest' surefire:test` | 47 | 47 | 0 | 0 | 0 | 0 | `phase6e-p3d2-excluded.log` |
| `'-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*Performance*,!*Exhaustive*,!*Search*' test` | 5,497 | 5,462 | 35 | 0 | 0 | 0 | `phase6e-p3d2-bounded.log` |
| `'-Dtest=IntegratedSocialSecurityCompleteStrategySearchCalculatorTest' surefire:test` | 8 | 8 | 0 | 0 | 0 | 0 | `phase6e-p3d2-search.log` |
| Before the fix, `'-Dtest=AssumptionsViewTest#immediateSurvivorDisplayPreservesTheStoredElectionOnSaveAndUnrelatedApply' test` | 2 | 0 | 0 | 0 | 2 | 1 | `phase6e-p3d2-survivor-before.log` |
| After the fix, `'-Dtest=AssumptionsViewTest,PlanFileOperationsTest,ArkansasWorkflowUiTest,ArkansasControlledWorkflowTest,ArkansasProjectionIntegrationTest,ArkansasMonteCarloLaunchTest,ArkansasIndividualAnalyzerLaunchTest,StateTaxWizardIntegrationTest' test` | 157 | 157 | 0 | 0 | 0 | 0 | `phase6e-p3d2-survivor-after.log` |
| Before the History fix, `'-Dtest=ArkansasWeightedLauncherPerformanceTest' test` | 2 | 0 | 0 | 2 | 0 | 1 | `phase6e-p3d2-history-before.log` |
| After both fixes, `'-Dtest=ArkansasWeightedLauncherPerformanceTest,ArkansasLongevityValidationTest,ArkansasSocialSecurityLaunchTest,ArkansasExhaustiveLaunchTest,ArkansasIndividualAnalyzerLaunchTest,ArkansasMonteCarloLaunchTest,Phase12EAssociationTest,Phase12FAnalyzerSwapTest,Phase12GFreshnessTest,AnalysisSnapshotCodecTest,SocialSecurityPdfWorkflowIntegrationTest,AssumptionsViewTest' test` | 140 | 140 | 0 | 0 | 0 | 0 | `phase6e-p3d2-history-after.log` |
| After both fixes, `'-Dglass.win.uiScale=1.0' '-Dtest=ArkansasWorkflowUiTest,ArkansasHistoryUiTest,ArkansasWeightedLauncherPerformanceTest' surefire:test` | 6 | 6 | 0 | 0 | 0 | 0 | `phase6e-p3d2-final-scale-1.0.log` |
| After both fixes, `'-Dglass.win.uiScale=1.5' '-Dtest=ArkansasWorkflowUiTest,ArkansasHistoryUiTest,ArkansasWeightedLauncherPerformanceTest' surefire:test` | 6 | 6 | 0 | 0 | 0 | 0 | `phase6e-p3d2-final-scale-1.5.log` |
| `-f target/p3d2-tracked-84525646/pom.xml compile` | — | — | — | — | Compile failed | 1 | `phase6e-p3d2-tracked-compile.log` |

The bounded regression ran before the targeted UI corrections. The first post-fix suite covered Assumptions and ordinary plan save, Arkansas calculations and relevant launchers, and the 66 state-tax wizard integration cases. The second included Arkansas launchers, historic association and freshness, v1/v2 evidence and exports. The bounded skips were existing conditional tests and opt-in previews. The one launcher-suite skip was the opt-in navigation rendering method. Surefire classified the two failing Save regression assertions as errors because they were raised through the JavaFX call wrapper. Both corrected runs reported zero failures and errors.

The standalone capacity profiles used the same compiled classes and local dependencies as Surefire.

```powershell
$xml = [xml](Get-Content 'target/surefire-reports/TEST-com.daviddunn.retirementplanner.app.socialsecurity.ArkansasLongevityValidationTest.xml' -Raw)
$cp = ($xml.testsuite.properties.property | Where-Object { $_.name -eq 'java.class.path' }).value
$probe = 'com.daviddunn.retirementplanner.app.socialsecurity.ArkansasEvidenceV2CapacityProbe'
java -Xmx1g -cp "target/phase6e-p3d1b-probe;$cp" $probe desktop
java -Xmx1g -cp "target/phase6e-p3d1b-probe;$cp" $probe larger
```

Both diagnostic processes exited 0. Logs are `phase6e-p3d2-probe-desktop.log` and `phase6e-p3d2-probe-larger.log`. These are standalone runs without JUnit discovery counts.

## 20. Defects discovered and corrections

**Release checkpoint incomplete.** The tracked-only compile failed with unresolved `InsufficientAnalysisFundsException`, `LongevityFinancialObservation` and `ArkansasAnalysisProvenanceReader` and consequential type errors. The complete working tree compiled because the preexisting untracked files were available. Their implementation must accompany the tracked source in an authorized release checkpoint. Preserve the existing files and rerun compilation and acceptance against the resulting clean checkout. No application source correction was necessary to diagnose this artifact defect.

**Ordinary Save rewrote a configured survivor election.** A loaded Arkansas couple with primary death in 2045 and a persisted survivor election of 65 correctly displayed “Immediate at death (Age 82)” in Assumptions. Plain Save wrote 82 into the input document. The Arkansas analyzer then correctly rejected the changed live inputs as a match for the previously accepted projection. The Assumptions validation now retains the originally stored election for an unchanged, immediate-at-death scenario and year. A newly selected death context still uses the existing validated choices. This protects frozen input identity and preserves the domain's established effective benefit date. The new regression covers Arkansas with primary death and Michigan with spouse death, unrelated Apply, and explicit change of death year. The corrected application saved the native alternate plan with the election still 65 and admitted it for weighted analysis.

**Read-only History rejected valid Arkansas archives.** The normal associated-result dialog ran a current-input compatibility check without Arkansas policies. It returned a historical status, which the dialog translated to unavailable. The History dialog now uses the recorded Arkansas policies after the original dependency, artifact and result checks have succeeded. Existing algorithm and reference checks and live freshness rules still apply in their respective paths. Both theme fixtures reproduced the failure and passed after the correction. The actual application then reopened the previously saved standard workload through the same production button.

Three harness issues were isolated. Copying the same plan UUID to several files in one parent triggered the intended projection path binding check, so later copies used separate parents. Early driver shutdown with modal loops active emitted Glass teardown exceptions. Later runs closed owned dialogs before JavaFX exit. A separate harness shutdown during a save was isolated by inspecting the final plan and cache artifacts independently.

## 21. Changed-file manifest

- `PHASE-6E-P3D2-ARKANSAS-NATIVE-ACCEPTANCE.md` records the acceptance result.
- `src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java` preserves the stored survivor election while displaying the derived immediate-at-death value.
- `src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsViewTest.java` verifies preservation and explicit death-context changes for both survivor directions.
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ProjectionAnalyzerHistoryDialog.java` uses verified, frozen Arkansas policy context for the read-only component.
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ArkansasWeightedLauncherPerformanceTest.java` extends the preexisting two-theme launcher test to verify that the bound v2 component opens after save and reload.
- Preexisting untracked main sources, below `src/main/java/com/daviddunn/retirementplanner/`: `app/socialsecurity/InsufficientAnalysisFundsException.java`, `app/socialsecurity/LongevityFinancialObservation.java` and `app/socialsecurity/results/ArkansasAnalysisProvenanceReader.java`.
- Preexisting untracked test sources, below `src/test/java/com/daviddunn/retirementplanner/`: `app/socialsecurity/ArkansasProvenanceV2CapacityProbeTest.java`, `domain/projection/ArkansasProductionPerformanceTest.java`, `ui/montecarlo/ArkansasMonteCarloLaunchTest.java`, `ui/socialsecurity/ArkansasExhaustiveLaunchTest.java`, `ui/socialsecurity/ArkansasIndividualAnalyzerLaunchTest.java`, `ui/socialsecurity/ArkansasSocialSecurityLaunchTest.java` and `ui/socialsecurity/ArkansasWeightedLauncherPerformanceTest.java`.
- Ignored working evidence includes the compile reproduction, test and probe logs, native driver and synthetic plan copies under `target`.

## 22. Known unsupported scenarios

Arkansas still returns typed unavailability for cases outside the approved evidence boundary, including special employment/retirement arrangements, materially uncertain pension-source qualification, uncertain IRA age or timing, potentially applicable unmodeled additional tax, unresolved traditional IRA basis and material low-income or NIIT uncertainty. Annual totals alone cannot supply dates or source qualifications. Funding, Roth target, archive and parse budgets can return explicit unavailability while preserving last success. Colorado remains unavailable. Exceptional modeled input combinations need their own financial evidence.

## 23. Remaining native actions

1. Validate a build from a complete, separately authorized checkpoint using a clean checkout.
2. On an interactive Windows desktop, repeat guided creation and Assumptions with keyboard and focus checks. Complete account, income, expense and Roth edits in their standalone workspaces. Exercise the remaining typed unsupported cases, pension review for both survivor directions and previous-result preservation.
3. Complete the remaining interactive analyzer and cancellation matrix, both genuine mixed-state paired-role perspectives, paired longevity reopen, and both Current/Baseline swaps. Exercise edits, plan replacement, role swap, closing and queued history during active work. Confirm alternate weighted plan association in ordinary Save and repeat the observed long UI delay under a quieter workstation workload.
4. Complete the native Open and Save As dialog, View and Save PDF with an external viewer, CSV destination and values, and compare frozen values after edits and reload.
5. Verify Light and Dark at Windows 100% and 150%, including system chooser scaling, clipped controls, keyboard traversal, focus, modal ownership, wrapping, resizing and contrast. Recheck the official final 2026 DFA annual materials when verified.

## 24. Final readiness classification

**RELEASE_BLOCKED.** The release checkpoint is missing referenced production source and fails a tracked-only compile. Native interaction requirements remain open. The working-tree validation is evidence for the existing local implementation and does not establish release approval.
