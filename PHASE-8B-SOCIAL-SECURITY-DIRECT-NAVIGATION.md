# Phase 8B — Social Security Analyzer Direct Navigation

## 1. Summary

Implemented direct, responsive analysis navigation without changing financial services or result renderers. Couple plans have four choices at one level: Social Security Only, Deterministic Quick Comparison, Deterministic Exhaustive Search, and Longevity-Weighted Search. Single-person plans retain their three supported analyses and deterministic initial selection.

The new UI-local `AnalyzerNavigationView` retains existing workspace Nodes, presents descriptive vertical choices when enough logical width is available, and uses a compact ComboBox otherwise. It does not parse inputs, build requests, run work, cancel jobs, invalidate results, or write plans. Quick Comparison has a visible prerequisite explanation and **Go to Social Security Only** action; neither selecting Quick nor following that link runs an analysis.

Implementation is limited to navigation, containment, prerequisite presentation, and a fixed active-job identity/status line. The existing shared input header stays in place. No Phase 8C contextual input relocation, result persistence, or result-history work was started.

## 2. Initial state and exact changed-file inventory

Initial branch: `main`; HEAD `bbc7bb07ecb54d58e9457f98e33a1bd9779f9c51`. Initial status contained only the untracked Phase 8A report, which was preserved unchanged.

Paths below are relative to the repository root. Package root is `com.daviddunn.retirementplanner`.

| Change | File | Responsibility |
|---|---|---|
| New production | `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerNavigationView.java` | Direct choice descriptor, retained-node host, wide/compact selectors, focus routing and active-job status |
| Modified production | `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java` | Four couple workspaces; replace nested task tabs; preserve shared inputs/result construction; Quick prerequisite link/status |
| Modified production | `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedView.java` | Three direct choices containing existing deterministic, SS-only and weighted workspaces; fixed active-job status |
| New test | `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerDirectNavigationTest.java` | Direct choices, state retention, prerequisite navigation, job admission/cancellation, responsive selector/focus and enabled rendered previews |
| Modified test | `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialogStateTest.java` | Replace old containment traversal; retain result/financial assertions; add current-result prerequisite/invalidation check |
| Modified test | `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedViewTest.java` | Direct three-choice assertions and updated preview containment |
| Modified test | `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/IntegratedAnalyzerPdfStateTest.java` | Preserve export capture across direct navigation; attach visible heat map before CSS inspection |
| Modified test | `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/IowaIntegratedVisualTest.java` | Select Quick directly in the existing optional preview |
| Modified test | `src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/InputTooltipPreviewTest.java` | Update only the SS analyzer preview routing formerly dependent on removed nested tabs |
| New documentation | `PHASE-8B-SOCIAL-SECURITY-DIRECT-NAVIGATION.md` | This implementation/verification report |

Total phase-owned inventory: **10 files: 3 production, 6 tests, 1 report**; **3 created and 7 modified**. The pre-existing untracked `PHASE-8A-SOCIAL-SECURITY-ANALYZER-UX-AUDIT.md` is not a Phase 8B change. Logs/screenshots are ignored artifacts under `target/`, not deliverable source files.

## 3. Before/after hierarchy

```text
Before — couples
Social Security Only → Recommendation / Claiming Grid / Top Strategies / Assumptions
Integrated Retirement Plan
  Deterministic
    Quick Comparison
    Deterministic Exhaustive Search → Ranked Strategies / Claiming-Age Heat Map
  Longevity-Weighted → Ranked Strategies / Claiming-Age Heat Map

After — couples
Social Security Only → original four result tabs
Deterministic Quick Comparison → original baseline / candidate / comparison / methodology
Deterministic Exhaustive Search → original Ranked Strategies / Claiming-Age Heat Map
Longevity-Weighted Search → original Ranked Strategies / Claiming-Age Heat Map
```

Only analysis-selection nesting is removed. Internal result tabs remain legitimate and unchanged. The deterministic explanatory heading/text is retained around each deterministic workspace rather than hidden in a removed parent tab.

## 4. Couple and single-person behavior

Couples initially select Social Security Only. Four retained `Choice` records map directly to the existing job `Mode` values. Selection replaces only the active workspace child in a presentation VBox; original controls and result objects are reused. The original shared household summary, Analysis Inputs and Plan Assumptions fold, longevity/valuation form, shared progress and cancellation controls remain above the active workspace in the same outer scroller.

Single-person plans initially select Deterministic Integrated and expose three choices: Deterministic Integrated, Social Security Only, Longevity-Weighted Integrated. They retain exactly nine claiming strategies per supported analysis, their original Run handlers, optional deterministic primary death year, independent SS/weighted mortality controls, individual PDFs and deterministic Break-Even action. No Quick choice, spouse input or survivor input is added. The dialog now hosts the single-person view directly, and its navigation component owns the existing vertical scroller so navigation remains outside it.

Household-shape lifecycle, controller plan notifications and reopen policy are unchanged. No automatic scene rebuilding on household change was introduced.

## 5. Wide/compact responsive behavior

`AnalyzerNavigationView` uses a measured sidebar preferred width of 260 logical units and preserves a 900-logical-unit workspace budget derived from the existing analyzer's minimum usable window width. Sidebar mode is used when the actual view width can accommodate both (currently 1,160 logical units before any outer insets). This is a content-budget calculation, not a physical-resolution or Windows DPI test. The existing stage sizing/minimum policy is unchanged.

The threshold was exercised in rendered JavaFX windows and in resize tests at 1920×1080, 1366×768, 1180×820, 900×650 and 800×600 logical budgets. Couple 1180-wide windows may still accommodate the sidebar because their actual client width is slightly above the threshold; the single-person container's padding may put the same window below it. The decision always uses actual navigation width. The 800-wide couple preview explicitly relaxes the stage minimum for the test; ordinary production still retains its existing 900 minimum.

The wide selector uses wrapped ToggleButtons with title, description, selected border/bold state and standard focus rendering. Narrow mode detaches the sidebar and shows the ComboBox. Compact controls are invisible and unmanaged in wide mode; detached sidebar buttons are not in the narrow scene traversal. Both selectors address the same retained workspace list. Resize does not recreate controls or change the selected analysis.

The selector and a selected-analysis description remain outside the result scroller. Existing table columns and horizontal scrolling are preserved, and topic/result content remains vertically scrollable. Existing wide table headers can truncate at narrow widths; no columns or metrics were removed or shrunk by this phase. Input forms were deliberately not reflowed or redesigned.

## 6. Quick Comparison prerequisite

The existing `socialSecurityResultCurrent` flag and Run readiness remain authoritative. `refreshQuickPrerequisite` reads that flag; it does not introduce a second prerequisite model.

- No current result: visible text says to run Social Security Only with current inputs first; the existing Quick Run remains disabled.
- Current result: visible text confirms the prerequisite is satisfied; existing job/busy eligibility still applies.
- Existing input/plan invalidation: the original flag and stale handling still disable Quick; the new explanation updates accordingly.
- **Go to Social Security Only** selects that workspace and focuses the visible selector. It does not execute, cancel, mutate input, or create a candidate list.

Candidate count stays 1–20 with initial 10. Candidate selection, precise PV cutoff, exact ties, analyzer ordering and the comparison service are unchanged. Missing weighted baseline facts do not affect this Quick prerequisite and do not block weighted candidate search.

## 7. State retention

`Choice` identity uses existing job modes rather than labels or tab indices. Its workspace reference is retained for the dialog lifetime. Navigation has no reference to the mutable plan, financial factories or job-controller execution methods. It owns only selection, displayed description, presentation focus and per-workspace vertical scroll positions.

The following remain in their existing owners:

- Pending text and selected dates: existing controls, including malformed intermediate input.
- Couple shared longevity session settings: existing dialog/controller listeners.
- Individual independent mortality values: existing `SinglePersonMortalityView` instances.
- Completed financial results: existing presentation fields and frozen request/report context.
- Selected strategies, table selections and result tabs: existing controls.
- Heat-map metric/cell selection: existing heat-map instances.
- Plan/assumption revisions, stale flags and callback eligibility: existing dialog/job-controller logic.
- PDF capture/export state: existing dialog/export actions.

Existing invalidation matrix is preserved: plan affects all modes; mortality/discount/date changes affect SS/Quick/Weighted but not deterministic exhaustive; Quick count/new SS result affects Quick; weighted baseline edits remain weighted-specific; pure navigation affects none. No request or financial-result class was modified.

## 8. Job execution and cancellation

All Run/Cancel handlers, `SocialSecurityAnalyzerJobController`, coordinator leases, worker executor, revisions, generations, stale callback rejection, failure classification and close cleanup are unchanged.

A fixed status label in the navigation header identifies the actual running job and its existing status/progress text even if a different workspace is selected. Existing shared graphical progress remains in the original header, reachable through the outer scroller. Existing local and shared Cancel controls are retained; none was consolidated or removed. In individual views the fixed job identity likewise supplements the original progress and Cancel controls.

Navigation stays enabled while work is running, but existing Run actions stay disabled under the common job lifecycle. Switching a workspace does not request cancellation or release admission. Tests explicitly navigate all choices during an admitted weighted job, then cancel through the existing shared handler and verify that admission persists until worker cleanup and no cancelled result publishes.

## 9. Results and PDF preservation

Result construction/rendering, table columns, candidate order, ranks, ties, summary metrics, result-time assumptions, grouped outcomes, optimal markers, heat-map profiles/colors/legends and full-detail callbacks are unchanged. `LongevityWeightedIntegratedView`, `ClaimingStrategyHeatMapView`, `IntegratedAnalysisResultTabs`, all financial services, report adapters and PDF exporters have no production edits.

The deterministic heat map continues to expose its existing applicable metric subset; weighted retains all five existing display choices. No new deterministic PV metric was invented. Internal result tabs retain their selection and existing scroll-to-details behavior. Optional weighted Current Strategy Baseline inputs and all five result folds are retained.

PDF tests now navigate all direct choices before capturing deterministic and weighted reports, preserving selected heat-map cell, metric, ranked selection, precise values and job generation. Export readiness, frozen report metadata, background export and lack of couple SS-only/Quick PDF capabilities remain as before. Existing individual SS-only PDF is preserved.

## 10. Accessibility

New controls provide meaningful accessible names/help. ToggleButton native selected state, border/bold styling and text identify the current choice without color alone. Up/Down/Home/End on sidebar buttons selects/focuses a direct choice; native button activation and ComboBox keyboard behavior are retained. The compact selector is named “Analysis type” and uses the existing `InputHelp` tooltip helper; the current description exposes selected-analysis context. The Quick navigation link is a normal keyboard-operable Button with descriptive help.

Focus remains on the user's selector while navigating. If a resize hides the focused selector, focus transfers to its visible equivalent. Existing table, heat-map and result-fold keyboard handling is untouched. A pulse-synchronized test verifies sidebar focus, arrow routing and compact-selector focus after resize.

No claim is made for Windows Narrator, high-contrast or native DPI certification. Manual OS-level checks remain listed below.

## 11. Tests executed

All Maven commands used IntelliJ's bundled Maven and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`. No dependency/build settings were changed.

| Execution | Run | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Before-edit baseline: existing dialog state, job, input, PDF state and individual view tests | 77 | 77 | 0 | 0 | 0 |
| Updated navigation/state/individual/PDF focused run | 52 | 51 | 0 | 0 | 1 |
| Entire `ui.socialsecurity` test package | 208 | 206 | 0 | 0 | 2 |
| Enabled direct-navigation tests and rendered previews | 16 | 16 | 0 | 0 | 0 |
| Complete suite, first attempt (missing selector tooltip discovered) | 2,915 | 2,881 | 0 | 1 | 33 |
| Tooltip correction: `InputTooltipTest`, navigation and PDF state | 27 | 26 | 0 | 0 | 1 |
| Complete established Maven suite, final repeat, `-DargLine=-Xmx8g test` | 2,915 | 2,882 | 0 | 0 | 33 |

The two normal analyzer-package skips are optional navigation rendering and the opt-in Iowa visual test. Navigation rendering was enabled and passed separately. The enabled run includes five parameterized rendered-preview cases plus eleven ordinary navigation tests. Counts overlap across commands and must not be summed as unique tests.

Existing job tests exercise admission, cancellation cleanup, obsolete publication and per-mode revisions. Existing input/request/date tests protect exact input semantics and independent dates; existing PDF and result tests protect report context, rankings, heat-map selection and formatting. Current-result prerequisite tests use established completed-result fixtures, not a newly introduced combined execution pipeline.

During implementation, intermediate tests exposed test-harness issues: a CSS assertion inspected a detached unselected map; a new readiness fixture set a flag without its completed presentation; and a resize assertion did not allow window-manager/pulse propagation. The tests were corrected to select the visible view, use the existing complete fixture and exercise logical resizing/pulse synchronization. No financial assertion, parser, golden or engine was weakened. A preview-helper reflection call initially needed checked-exception handling; that compilation error was corrected. Successful verification runs and the first failed complete-suite attempt are listed separately above.

The first complete suite additionally found a real presentation omission: the compact selector had accessible help but lacked the standard tooltip required by `InputTooltipTest`. Added descriptive help through the existing `InputHelp.install` helper, preserving the accessibility text and all financial behavior. The focused tooltip/navigation/PDF rerun passed; the complete suite was repeated rather than treating the earlier error as a pass.

Final full-suite result: **BUILD SUCCESS**, 7 minutes 50 seconds, completed October 18, 2026 at 11:47:58 EDT. All existing financial, persistence, seeded simulation and wizard financial-equivalence checks passed without changed expectations. The 33 skips are the established opt-in/disabled cases, including the new opt-in navigation preview; its five rendered cases passed separately when enabled.

Logs: `target/phase8b-baseline.log`, `target/phase8b-navigation.log`, `target/phase8b-focused.log`, `target/phase8b-preview.log`, `target/phase8b-help-focused.log`, `target/phase8b-full.log` (first attempt), `target/phase8b-full-final.log` (final repeat). These are ignored local artifacts.

## 12. Rendered preview evidence

Enabled `AnalyzerDirectNavigationTest` with `-Danalyzer.navigation.preview=true`. It opens actual JavaFX stages and waits for real animation pulses between workspace transitions and screenshots. It captures **45 PNGs** under ignored `target/phase8b-navigation-preview/`: nine per size budget (four couple workspaces, weighted heat map, active-job navigation, and three individual workspaces). Values are synthetic test fixtures, not user data. The active-job picture uses deterministic queued test work and does not pretend a real financial search ran to completion.

Representative images opened and inspected include:

- `couple-QUICK-1920x1080.png`: four direct descriptions, current-selection styling, prerequisite explanation/link and unchanged candidate controls.
- `couple-WEIGHTED-1920x1080.png`: original survivor baseline, optional-baseline message, weighted table and result tabs.
- `couple-SOCIAL_SECURITY-1366x768.png`: wide selector, shared inputs and original SS result tabs.
- `couple-WEIGHTED-1180x820.png`: preserved input/form and optional baseline at the tested sidebar content budget.
- `couple-SOCIAL_SECURITY-900x650.png` and `couple-EXHAUSTIVE-900x650.png`: compact selector, visible footer and vertical content reachability.
- `couple-QUICK-800x600.png`: narrower compact navigation and retained shared form; workspace content continues below in the existing scroller.
- `couple-heat-map-1366x768.png`: existing complete grid, legend, metric selector and selected details; navigation stays visible beside it.
- `couple-active-job-900x650.png`: SS workspace selected while fixed status identifies the weighted job; original Cancel and progress remain operational.
- `single-EXHAUSTIVE-1920x1080.png` and `single-WEIGHTED-800x600.png`: wide/compact three-choice navigation, original actions and input controls, no spouse/Quick additions.

Early same-FX-turn screenshots displayed stale rendering; those were rejected. The final preview harness waits for pulses, creates a correctly sized individual stage, and uses existing heat-map reveal navigation rather than an arbitrary scroll-to-bottom offset. Source/test rendering checks retain strategy identity and column assertions. This is actual JavaFX rendering at logical-size budgets, not an OS display-scaling, Narrator or high-contrast test.

## 13. Limitations and deferred work

Differences from Phase 8A's broader recommendation are intentional scope choices: this phase leaves the shared input header in place rather than contextualizing it; it needs only `AnalyzerNavigationView`, not the optional `AnalyzerWorkspace` helper; and single-person navigation uses three supported choices rather than a disabled fourth choice. All are permitted by the Phase 8B instructions. No financial or lifecycle deviation was necessary.

- Shared input placement, extensive header text and form grids remain intentionally unchanged; contextual grouping belongs to Phase 8C.
- Wide result tables retain existing horizontal scrolling/truncated headings at compact widths. No result redesign was authorized or performed.
- The stage's existing minimum size is preserved. The 800-wide couple rendering is a deliberately relaxed test budget, not a new supported minimum.
- No persistent result history, new result schema, input unification or household-shape rebuilding.
- Optional older tooltip/Iowa preview fixtures were updated to address the new containment. They are not represented as enabled executions in the table unless explicitly stated.

Manual Windows acceptance: open both household shapes; keyboard through each selector; resize across the content budget with pending input; run SS then Quick; run exhaustive/weighted and navigate during work; cancel and close; select a heat-map strategy and export supported PDFs. Verify Narrator selected-state/status reading, high contrast, 100/125/150/200% scaling and actual work-area fit. These OS-level checks were not performed by this implementation session.

## 14. Final Git verification

`git diff --check` passes for tracked changes; new Java/report files are checked separately for trailing whitespace. Protected paths (domain algorithms, application financial services, persistence, production resources, financial test resources and `pom.xml`) have no changes. No result/PDF formats or dependencies changed. No files staged, committed, pushed, reset or discarded. The pre-existing Phase 8A report remains untracked and preserved. Generated PNGs/logs stay under ignored `target/`.

Final status: **7 tracked files modified**, matching the modified-file rows in Section 2; **4 untracked files**: the preserved Phase 8A audit, this Phase 8B report, `AnalyzerNavigationView.java`, and `AnalyzerDirectNavigationTest.java`. Index is unchanged/empty of staged changes. Phase-owned changes remain 10 files. Final `git diff --check` and new-file whitespace checks pass; protected paths remain unchanged. Nothing staged, committed or pushed.

**Final acceptance:** all Phase 8B automated and rendered-navigation criteria pass. Native Windows assistive-technology/display-scaling checks remain manual as explicitly documented. Stop here; Phase 8C and persistent-results work have not begun.

PHASE-8B-SOCIAL-SECURITY-DIRECT-NAVIGATION-COMPLETE
