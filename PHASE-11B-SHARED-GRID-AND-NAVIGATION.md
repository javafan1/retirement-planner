# Phase 11B — Shared claiming-age grid and results navigation

## 1. Baseline and implementation summary

Implemented the approved couple SS Only visual changes and guarded initial result navigation. No Phase 11C PDF export was added.

Baseline: branch `main`, HEAD `e620bec4a0011ab12d15fce670bff5011093d9da`. The sole pre-existing change was untracked `PHASE-11A-SOCIAL-SECURITY-UI-PDF-AUDIT.md`; preserved unchanged. Read AGENTS.md, the Phase 11A audit, Phase 10D parameter report, and relevant Phase 9 SS/weighted restoration and Exhaustive historical PDF reports; inspected the actual current sources and tests.

Pre-edit focused baseline: **88 run, 87 passed, 1 skipped, zero failures/errors**, Maven **18.424 seconds** (`target/phase11b-baseline.log`). The skip was the existing opt-in navigation preview. No source changes preceded this baseline.

The SS grid now uses primary ages horizontally and spouse ages vertically, displays original Stage 1 expected Social Security PV dollars, shares integrated color tiers/legend/interaction, and shows both exact optimum and Stage 2 retention markers. Fresh accepted SS results select Claiming Grid. Fresh and restored completed Exhaustive/Weighted results initially select Claiming-Age Heat Map. Later manual navigation wins over pending completion/load/reveal callbacks.

## 2. Exact changed-file inventory

Paths below are relative to the repository. Production Java files share prefix `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/`; test files share `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/`.

| Status | File | Purpose |
|---|---|---|
| New production | `ClaimingAgeGridView.java` | Shared age-pair renderer, geometry, tier backgrounds, legend, selection/accessibility, arrow keys and responsive details layout |
| New production | `SocialSecurityClaimingGridAdapter.java` | Pure Stage 1 adapter preserving source cells, actual axes, exact ties and retention flags |
| New production | `ResultNavigationGuard.java` | FX-confined per-binding/result initialization, navigation epochs and guarded deferred reveal |
| Modified production | `ClaimingStrategyHeatMapView.java` | Delegate grid/legend/layout/selection rendering to the shared component; retain integrated metrics/details/report interfaces |
| Modified production | `AnalyzerNavigationView.java` | Small workspace-change notification separate from existing selection/readiness callback |
| Modified production | `IntegratedAnalysisResultTabs.java` | Support retained SS result tabs, natural height/minimum width, guarded delayed reveal and initial-selection suppression |
| Modified production | `LongevityWeightedIntegratedView.java` | Re-rendering the same result preserves table selection/sort and heat-map metric/cell |
| Modified production | `SocialSecurityStrategyAnalyzerDialog.java` | SS grid composition, retained tab reference, accepted completion/default restoration navigation and binding/closure integration |
| Modified production | `WeightedResultPersistence.java` | UI-only load-start hook captures navigation epoch before asynchronous restoration; no storage/schema/acceptance change |
| New test | `SharedClaimingAgeGridTest.java` | Stage 1 fidelity, tiers, ties, distinct Stage 1/2 values, keyboard/missing pairs and rendered logical-size previews |
| New test | `ResultNavigationTest.java` | Fresh callbacks, canceled/failed/obsolete/rejected replacements, manual selection, deferred reveal, independent result/binding initialization and Current/Outdated historical defaults |
| Modified test | `CoupleSocialSecurityPersistenceTest.java` | Passive restored SS stays on Recommendation |
| Modified test | `ExhaustivePersistenceTest.java` | Canceled/obsolete/fatal/all-failed replacements preserve manual Ranked selection and previous financial result |
| New report | `PHASE-11B-SHARED-GRID-AND-NAVIGATION.md` | This implementation/verification report |

The pre-existing Phase 11A audit remains untracked and is not a Phase 11B implementation change. Generated logs and PNGs are under ignored `target/`, not release sources. No dependency, resource/CSS, golden, financial domain, snapshot DTO, schema, fingerprint, parameter coordinator, PDF exporter, Monte Carlo or individual production file was changed.

## 3. Shared renderer architecture

`ClaimingAgeGridView` owns visual cells keyed by immutable `(primaryAge, spouseAge)` pairs. Callers supply display strings, optional percentages, optimum/retention flags, tooltips/accessibility descriptions and a selection callback. It has no request factory, optimizer, plan, projection, persistence service or financial objective.

Both existing integrated views continue to use `ClaimingStrategyHeatMapView`, whose metric selector, complete-strategy details, full-analysis action, pure adapters and PDF-facing model/selection APIs remain intact. That wrapper now delegates grid geometry, toggle selection, legend and responsive arrangement. The existing deterministic and weighted adapters are unchanged.

The SS adapter returns immutable source-backed cells and actual age axes, without manufacturing complete survivor elections or estate metrics. Missing pairs are not fabricated by the renderer. Selection identity is the age pair, not a child index. SS rebuilds preserve a valid selection for the same result object; different results initialize from the original Stage 1 highest-cell ordering, with a first-source-cell fallback only if no available optimum pair exists.

The shared component uses the existing palette and CSS unchanged. Toggle selection uses the existing blue border; exact optimum uses star/green border/bold; retention uses a separate triangle. Combined `★ ▶` remains visible and is described in accessible text. Arrow keys select adjacent actual-axis cells and move focus as an explicit keyboard action; automatic result navigation never requests focus. Accessible descriptions update selected/not-selected state, ages, value, optimum and retention.

## 4. Financial meaning and exact color behavior

SS cells retain the original BigDecimal Stage 1 PV and existing currency formatting. Real/nominal values and existing `gridDetail()` expanded-result information remain available. Selected details explicitly distinguish Stage 1 retirement-age optimization from Stage 2 complete survivor-election strategies. The highest Stage 1 pair is not described as necessarily the highest Stage 2 strategy.

The denominator is the original highest Stage 1 expected PV. Zero/negative optimum retains exact optimal markers but yields unavailable percentage/gray background. A rounded displayed 100% never creates an optimum: optimum membership comes directly from the original ranking's highestCells list.

Percentages used for tier display retain 34 fractional places with truncation toward zero. This avoids nearest-rounding promotion across the existing finite-decimal thresholds, including an input infinitesimally below 99.5% with more than 34 decimal places. Classification is independent of the one-decimal percentage shown in descriptions. Original financial values are never rounded or mutated by this calculation; it is a presentation ratio only.

Existing palette remains: >=99.5% `#85c8bc`, >=99% `#b7ddd1`, >=97% `#e5e4b1`, >=95% `#f0c98a`, below95% `#e9a58d`, unavailable `#e5e8ec`; optimal border `#174d43`, selected border `#173f78`. Integrated percentage/objective adapters retain their previous arithmetic and financial interpretation.

## 5. Approved navigation behavior

| Event | Behavior |
|---|---|
| Fresh eligible accepted couple SS completion | Select existing Claiming Grid tab and defer reveal of its results TabPane |
| Passive historical SS restoration | Initially select Claiming Grid once per attached completed result, through the same guard; not a new financial completion (focused native-acceptance correction, Section 12) |
| Fresh eligible accepted Exhaustive or Weighted completion | Select Claiming-Age Heat Map once for the newly displayed result |
| First compatible Current/Outdated historical integrated attachment | Select Heat Map once, overriding older saved Ranked preference |
| Valid saved metric/strategy preferences | Restore normally; no snapshot rewrite |
| Manual result/workspace selection after admission/load begins | Preserve later manual choice; suppress delayed automatic override |
| Revisit same result in same dialog | Preserve manual tab, valid metric/cell and ranked-table state |
| Different result or different plan binding | Independent first-display initialization |
| Canceled/fatal/obsolete/rejected/all-unsuccessful replacement | Keep previous result and selected tab; existing gates remain authoritative |
| Input/currency/save/retry/status refresh | No default-selection callback and no scroll |
| Full-strategy detail action | Existing intentional navigation to Ranked Strategies remains available |

SS selection runs only inside the existing eligible completion callback after rendering and the existing success-policy check. Exhaustive and Weighted initialization follows their existing accepted-success branches. No job-controller acceptance, one-job admission, terminal rollback, cancellation or financial calculation semantics changed.

`IntegratedAnalysisResultTabs` now also supplies natural-height layout to the existing four SS result tabs. No additional Results tab or navigation layer was introduced. No minimum stage size was changed.

## 6. Historical restoration and race protections

`ResultNavigationGuard` captures a ticket containing the current navigation epoch and plan/session binding. Tabs and workspace selections advance the epoch; automatic initialization is suppressed from doing so. A completed result is marked initialized once per kind/binding/identity even when a later manual choice prevents automatic selection. A repeated metadata/render callback cannot subsequently reset that choice.

Fresh results use their accepted result object as a view-local identity; historical attachments use snapshot run UUID. The binding is included independently, so a Save As clone retaining a historical run UUID still initializes separately. Close invalidates the guard. Wrong-binding callbacks are rejected before recording initialization.

The small `WeightedResultPersistence.onLoadStarted` hook captures a ticket only when a real asynchronous load starts. It does not reset the ticket on every readiness/currency refresh. Existing persistence generation, binding and replacement guards still reject obsolete loads before UI attachment. No historical result calculation or parameter import was added.

Automatic initial selection suppresses the TabPane's ordinary deferred scroll and schedules one guard-owned reveal instead. That callback rechecks epoch, binding, result identity, selected target and current workspace. Manual Heat Map/Claiming Grid selection uses a local selection epoch plus a dialog binding/navigation guard; changing tabs/workspaces or closing before the queued callback runs suppresses its scroll. No keyboard focus request is made.

Tab/cell selection and export adapters do not accept parameters, mark financial edits, increment revisions or write snapshots. Existing Phase 10 restoration on explicit analysis-workspace selection remains unchanged. Save As, plan switching, current/outdated assessments, SS prerequisite publication, historical PDFs and Quick's memory-only result policy remain under their existing services.

## 7. Automated verification

All Maven commands use IntelliJ's bundled Maven and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`. Test JVM heap: `-DargLine=-Xmx4g`.

| Run | Result | Time / log |
|---|---|---|
| Pre-edit focused baseline | 88 run; 87 passed; 1 skipped; 0 failures/errors | 18.424 s; `target/phase11b-baseline.log` |
| Initial existing grid/navigation/restoration/PDF checks | 116 run; 115 passed; 1 skipped; 0 failures/errors | 41.427 s; `target/phase11b-intermediate.log` |
| Expanded focused compatibility run | 277 run; 276 passed; 1 skipped; 0 failures/errors | 1:11 min; `target/phase11b-focused.log` |
| Final new renderer/navigation/preview cases after compact marker fix | 39 run; 39 passed; 0 skipped/failures/errors | 25.440 s; `target/phase11b-layout-final.log` |
| Complete Maven suite | **3,427 run; 3,383 passed; 44 skipped; 0 failures/errors; BUILD SUCCESS** | **10:13 min**; `target/phase11b-full.log` |

Expanded focused coverage includes existing HeatMap model/view/adapter tests, direct navigation, dialog state, job controller, all seven SS parameter identities, all eight Monte Carlo parameter identities, SS/Exhaustive/Weighted restoration, historical Exhaustive PDF fidelity, integrated PDF state/report, individual report and individual view tests. The one skip is the existing opt-in `analyzer.navigation.preview` test; equivalent new Phase 11B previews execute normally. No tests were disabled or weakened. No golden files were changed.

Development iterations exposed test-harness issues (attempting to reuse a root in a second Scene; assuming the bounded real mortality fixture necessarily changed Stage 2's PV). The harness now explicitly detaches the root and uses a separately identified synthetic Stage 1/2 distinction case alongside exact production-result cell comparisons. Logical scene dimensions are set using sizeToScene rather than confusing constructor stage dimensions with the test budget. Earlier failing iteration logs do not represent final results.

Focused tests prove actual accepted callbacks, current/outdated historical initialization with old Ranked preferences, same-result/manual-choice preservation, cancellation/failure/obsolete/rejected runs, no wrong-binding navigation, passive SS Recommendation behavior, exact Stage 1 values/scales/axes, boundary tiers, ties, unavailable percentages, missing pairs, arrow keys and rendered-cell accessibility. Existing throwing calculation factories in restoration tests continue to reject any accidental financial work. Historical PDF source/raster tests and parameter persistence regressions pass unchanged.

The complete suite ran ordinary `test` without a test selector or new exclusions. All 39 new cases passed again, including final source changes and the added passive-SS/all-failed-Exhaustive navigation assertions. The 44 skips comprise 40 pre-existing opt-in previews/capacity/benchmark/manual cases and four pre-existing assumption-aborted Mississippi admission worksheets awaiting authoritative guidance. These were not disabled by Phase 11B. The full run includes frozen legacy weighted compatibility, Couple SS/Exhaustive/Weighted restoration, atomic snapshot tests, all fifteen parameter identities, historical PDFs, seeded Monte Carlo, tax/mortality/projection and golden financial regressions. No test expectation or golden was regenerated.

## 8. Rendered previews and inspection

Generated and inspected real JavaFX PNGs under `target/phase11b-previews/`:

- `ss-{1920x1080,1366x768,1180x820,900x650,760x650}.png`: actual workspace view after revealing completed tabs.
- `ss-grid-{1920x1080,1366x768,1180x820,900x650,760x650}.png`: full natural-height grid/legend/details for the same logical budgets.
- `exhaustive-{1920x1080,1366x768,1180x820,900x650}.png` and equivalent `weighted-...png`: existing integrated wrapper through the shared renderer, using bounded result fixtures (unavailable pairs are intentional in these fixtures).

Directly inspected SS grid images at every listed width, the compact SS workspace, Exhaustive at 900 and 1920, and Weighted at 900 and 1366. Checked axes, palette, legend, dollar values, combined markers, selected border, missing cells and stacked/side-by-side details. Shared details move beside the grid when the actual available layout width is at least 1200; navigation sidebar space naturally causes stacking at some nominally wide stage sizes.

Inspection found and corrected selected SS optimum dollar truncation beside combined markers at compact widths. The shared renderer keeps existing integrated 40-unit cells, while SS marker grids use 52-unit cells and put markers on their own line below 110 units of cell width. Wider SS cells keep one-line values/markers. The new test checks the actual skinned Text node for an ellipsis, not merely the underlying button string. Text was not shrunk.

At compact heights the full nine-row grid plus legend/details exceeds the viewport. All content remains in the natural-height outer analyzer scroller; no nested claiming-grid scroller or horizontal clipping is introduced. Full-content screenshots intentionally extend beyond the viewport to show reachable lower rows/details. This is not a claim that every row/detail fits simultaneously in 900×650.

No native DPI, Narrator or high-contrast certification is claimed. The user subsequently confirmed native Windows acceptance of Phase 11B except the historical SS initial-tab behavior; Section 12 records that finding and the focused correction awaiting native retest.

## 9. Known limitations and deferred work

- No new SS or Quick PDF export: approved Phase 11C work remains deferred.
- Grid-cell selection is view-local; no new snapshot preference or schema was introduced.
- Same-result SS selection preservation uses the retained immutable result identity, not approximate equality of calculated values.
- Extremely large monetary strings beyond the representative tested fixture may still require normal tooltip/detail inspection; no financial formatting precision or font size was changed to squeeze values in.
- The manual Windows checks below remain necessary, particularly native focus/scroll behavior and display scaling.

## 10. Native Windows acceptance checklist

1. Rebuild and restart normally. Open a saved couple plan and run SS Only with distinctive accepted settings. Verify Claiming Grid is selected/revealed without focus theft.
2. Check primary horizontal/spouse vertical axes; PV dollar cells, unchanged colors/legend; exact-tie stars and separate retention triangles; selected details distinguish Stage 1/2. Use Tab/arrow keys, hover and click.
3. During a run, manually choose another result tab/workspace; completion must retain that choice. Cancel/fail a replacement; old result/tab remains. Correct invalid inputs without unexpected navigation.
4. Complete Exhaustive and Weighted; each should initially show Heat Map. Select Ranked manually and revisit, edit inputs or retry parameter save; Ranked remains selected. Full Strategy Analysis still deliberately opens Ranked.
5. Restart/reopen Current and Outdated integrated snapshots, including old Ranked preferences. Heat Map is the initial view, saved metric/strategy remains valid, and no analysis reruns. Historical SS restoration initially shows Claiming Grid; subsequent manual tab choices remain selected.
6. Switch plans (including Save As clones) during loads, close dialogs and reopen. No old callback should select or scroll another plan's view. Verify original and clone results independently.
7. Check 1920×1080, 1366×768, 1180×820 and 900×650 logical layouts; scroll to lower rows/details. Confirm no clipped dollar text/axes, readable combined markers and normal outer scrolling.
8. Verify all fifteen accepted parameter configurations, automatic parameter save/retry, unsaved financial edit isolation, Quick prerequisite enforcement, historical PDFs, individual analyzers and Monte Carlo remain normal. Navigation alone must not save parameters or mark the plan modified.

## 11. Final Git status and stop boundary

Final `git diff --check` passed. Branch/HEAD remain `main` / `e620bec4a0011ab12d15fce670bff5011093d9da`; index is empty. Final status:

```text
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerNavigationView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ClaimingStrategyHeatMapView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IntegratedAnalysisResultTabs.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/LongevityWeightedIntegratedView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedResultPersistence.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/CoupleSocialSecurityPersistenceTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java
?? PHASE-11A-SOCIAL-SECURITY-UI-PDF-AUDIT.md
?? PHASE-11B-SHARED-GRID-AND-NAVIGATION.md
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ClaimingAgeGridView.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationGuard.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityClaimingGridAdapter.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SharedClaimingAgeGridTest.java
```

This is eight modified tracked files and six new Phase 11B files, plus the one preserved pre-existing Phase 11A report. No staging, commit, push, reset, clean or discard was performed. Automated implementation/verification is complete and ready for review/native acceptance. Stop after Phase 11B; Phase 11C has not begun.

## 12. Focused correction — historical SS Claiming Grid default (2026-10-25)

### Native finding and exact cause

The user reports that Phase 11B native Windows acceptance passed except that a reopened completed couple SS Only analysis still initially selected Recommendation. Fresh SS completion already selected Claiming Grid. This is user-confirmed manual evidence, not a claim that Codex performed those native checks.

The cause was an explicit earlier design choice, not a calculation, compatibility or snapshot defect. The SS TabPane initially selects its first tab (Recommendation). The historical SS attachment callback reconstructed/rendered the result and restored its saved strategy occurrence, but omitted `displayResult`. Only the integrated historical paths captured a load-start navigation ticket. The earlier test deliberately asserted Recommendation. The new user instruction supersedes that earlier historical-SS policy; Phase 11A remains preserved as the original audit.

### Narrow correction and files

Only four files receive additional edits for this correction, relative to the preserved Phase 11B working tree:

- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java`: three added lines: SS historical load ticket field; SS `onLoadStarted` capture; `displayResult(COUPLE_SOCIAL_SECURITY, saved.runId(), ticket)` at the end of the fully rendered historical attachment callback.
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/CoupleSocialSecurityPersistenceTest.java`: update the historical default expectation; prove Recommendation remains selected through reassessment/re-render/workspace revisits, and a new dialog initializes the same saved result to Claiming Grid.
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationTest.java`: extend historical Current/Outdated cases to SS and add four blocked-SS-load cases for manual tab selection, workspace navigation, closure and plan switching. Existing per-identity initialization and fresh/canceled/failed/obsolete/rejected run tests are retained.
- This report: current policy, verification and native acceptance update.

No second navigation mechanism, result gate or executor was added. The existing guard uses the original run UUID, binding and epoch; applies the initial selection once; rejects later manual-navigation conflicts; and guards deferred reveal without requesting focus. Empty/unavailable results do not execute the attachment callback. No result, grid value, ranking, fingerprint, persisted preference, schema, PDF data or accepted parameter configuration is changed. Historical attachment is still passive and never invokes a financial engine or impersonates fresh SS completion for Quick invalidation.

### Verification

Focused run: **141 run, 141 passed, zero failures/errors/skips**, Maven **44.899 seconds**, `target/phase11b-ss-history-focused.log`. It includes `ResultNavigationTest` (18 cases), `CoupleSocialSecurityPersistenceTest`, `ExhaustivePersistenceTest`, `WeightedRestorationTest`, `SocialSecurityAnalyzerJobControllerTest`, `SocialSecurityParameterPersistenceTest` and `MonteCarloParameterPersistenceTest`. Earlier Section 7 totals remain the original Phase 11B evidence. The earlier assertion that passive SS restoration stays on Recommendation is intentionally superseded by the new Claiming Grid assertion, not removed without a replacement contract.

Complete Maven suite: **3,433 run, 3,389 passed, 44 skipped, zero failures/errors**, **BUILD SUCCESS**, **9:46 minutes**; `target/phase11b-ss-history-full.log`. Used IntelliJ bundled Maven, the required project-local repository and `-DargLine=-Xmx4g`, with no test selector or new exclusions. The existing 44 opt-in/manual/assumption-guarded skips remain unchanged. All six added parameterized cases pass, along with fresh SS completion, canceled/failed/obsolete/rejected replacements, independent result identities, parameter persistence, historical compatibility and PDF regressions. No assertions, goldens, dependencies, storage limits or financial outputs were changed to obtain these results.

The blocked-load tests use the existing store/repository path with a bounded latch, throwing SS calculation factory and job-generation assertions. They check no completed attachment initially leaves Recommendation selected; manual Top Strategies/workspace choices survive attachment; a closed dialog or changed plan cannot receive the old result/navigation; and restoration performs no analysis. Controller close's existing generation invalidation is distinguished from admitting financial work.

Native status: all other Phase 11B checks are user-confirmed passed. This focused correction requires native retest: reopen Current and Outdated SS snapshots and confirm Claiming Grid; manually select Recommendation/Top Strategies/Assumptions, revisit/refresh and confirm preservation; navigate during a pending load and confirm no delayed tab/scroll override. Close/reopen the analyzer and confirm the new dialog initializes Claiming Grid again. Phase 11C remains unstarted.

Final `git diff --check` passes. Branch/HEAD and the complete working-tree file inventory remain as listed in Section 11; the correction adds no files and preserves the pre-existing Phase 11A audit and all Phase 11B work. The index remains empty. No staging, commits, pushes, resets, cleans or discards were performed. Stop for review and the focused native retest.
