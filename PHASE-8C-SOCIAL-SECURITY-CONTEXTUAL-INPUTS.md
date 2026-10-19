# Phase 8C — Social Security Analyzer Contextual Inputs

## 1. Executive summary

Implemented contextual configuration and execution layout while retaining Phase 8B direct navigation and all existing financial capabilities. Couple Social Security Only and Longevity-Weighted Search expose the original five shared longevity/valuation controls. Quick Comparison exposes its existing candidate count and explicit SS-result prerequisite instead of duplicate mortality inputs. Deterministic Exhaustive Search shows inherited plan context with no active longevity/PV controls.

The original shared controls have **one stable parent** for the entire dialog session. Relevant modes show the host; unrelated modes hide/unmanage it. No controls are recreated on navigation, no mirrored values or bindings between duplicate fields are introduced, and no input listeners, validation, defaults, request semantics or financial algorithms change.

The hierarchy now separates **From Retirement Plan**, **Inputs for Next Run**, **Run / Cancel / Progress**, and **Completed Results**. Exhaustive does not get an empty editable-input section. Complete input-reference information remains expandable. The original shared graphical progress, status and Cancel are moved to the fixed navigation header alongside the existing Phase 8B active-job identity. Completed result views and PDF capture remain unchanged.

## 2. Initial Git status and baseline

Initial working tree: clean. Branch: `main`. HEAD: `e9622dc63aad26271fd6318142cfcc5641104b85`. The Phase 8A and Phase 8B reports were already committed and were read as the design/preservation baseline. `AGENTS.md` and actual production/test implementations were inspected before editing.

Before-edit focused baseline: **219 run, 217 passed, 0 failures, 0 errors, 2 skipped**. This ran the complete `ui.socialsecurity` test package plus `InputTooltipTest`. The skipped cases were opt-in analyzer rendering and the older Iowa visual preview. No existing work was reset, discarded or overwritten.

## 3. Exact changed-file inventory

Paths are relative to the repository root; all Java classes are under `com.daviddunn.retirementplanner`.

| Status | File | Change |
|---|---|---|
| New production | `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerConfigurationLayout.java` | Stateless analyzer-only layout/provenance formatting: headings, wrapped notes, original-control field rows and FlowPane action/field groups |
| Modified production | `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerNavigationView.java` | Selection callback for presentation visibility; attach existing fixed execution-status nodes; responsive behavior retained |
| Modified production | `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java` | Stable contextual host, inherited summary, reference fold, execution grouping, Quick prerequisite/basis provenance, fixed existing shared progress/Cancel |
| Modified production | `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/LongevityWeightedIntegratedView.java` | Constructor containment/headings around Run and completed results; ranked content/rendering unchanged |
| Modified production | `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedView.java` | Inherited summary, optional death-year field grouping, execution/results headings and existing PDF placement |
| Modified production | `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonMortalityView.java` | Existing independent inputs in wrapping field groups; provenance/execution/results headings; existing PDF placement |
| New test | `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerContextualInputsTest.java` | Four-mode visibility, control/help identity, pending text/dates, listener counts, session propagation, fixed progress, frozen results, reference fold and layout |
| Modified test | `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerDirectNavigationTest.java` | Reuse pulse-synchronized previews with configurable ignored output folder; extra reference/pending-input captures |
| Modified test | `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialogStateTest.java` | Intentional field-parent/label assertions updated; original financial/state assertions retained |
| Modified test | `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedViewTest.java` | Verify result-detail reachability using the existing scroller after added configuration sections |
| Modified test | `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonMortalityViewTest.java` | Use production-equivalent scrolling in geometry checks; preserve completed values/selection/frozen-input checks |
| Modified test | `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/IowaIntegratedVisualTest.java` | Reference-fold title updated in optional preview |
| New documentation | `PHASE-8C-SOCIAL-SECURITY-CONTEXTUAL-INPUTS.md` | This report |

Total: **13 phase-owned files: 6 production, 6 tests, 1 report; 3 new and 10 modified**. No build configuration, dependencies, CSS/resources, persistence, request factories, result DTOs, financial algorithms or financial golden files changed. Generated screenshots/logs remain ignored under `target/`.

## 4. Before/after configuration hierarchy

```text
Before
  Shared household summary
  Complete assumptions/reference fold
  Always-visible shared longevity/PV form, including for Exhaustive
  Shared Cancel/status/progress within the outer scroller
  Selected workspace: explanation, local actions and existing results

After
  Existing direct analysis selector / selected-analysis identity [fixed]
  Existing active-job identity + original shared Cancel/status/progress [fixed while busy]
  Selected workspace content in the existing outer scroller
    From Retirement Plan [concise, read-only, relevant source facts]
    Input Reference — Complete Dependencies and Plan Assumptions [collapsed initially]
    Inputs for Next Run
      SS / Weighted: original shared five-control host
      Quick: original candidate count + prerequisite/link
      Exhaustive: no irrelevant editable-input section
    Weighted only: original optional Current Strategy Baseline inputs
    Run / Cancel / Progress [original Run and local Cancel/status handlers]
    Completed Results [original result tabs, tables, details, assumptions and PDF actions]
```

Configure/Run/Results remain sections of one workspace, not new pages or tabs. Original repetitive deterministic wrapper headings were replaced with contextual provenance/execution labels; their financial meaning remains explained in the workspace and complete reference material.

## 5. Per-analysis visibility and source ownership

| Analysis | Active editable configuration | Inherited context | Preserved result presentation |
|---|---|---|---|
| Couple SS Only | Same primary/spouse factors, conditioning date, valuation date, real discount rate | Identities, DOBs, mortality categories, SS records, valuation years/current elections, plan COLA | Recommendation, Claiming Grid, Top Strategies, result-time Assumptions |
| Couple Quick | Same 1–20 candidate count, default 10; explicit current/required SS-result status and navigation link | Household, projection start/horizon, configured death scenario, return/inflation/state; current completed SS candidate basis when available | Current-plan baseline, candidate table/details, analyzer order and methodology |
| Couple Exhaustive | No new overrides; shared longevity/PV controls hidden | Household, start/horizon, configured death/survivor scenario, relevant economic/state assumptions | Grouped ranked outcomes, summary, selected outcome, heat map and PDF |
| Couple Weighted | Same shared five controls plus original optional primary/spouse survivor baseline ages | SS/person facts, start, plan COLA; second-death horizon explicitly distinct from configured horizon | Same ranking, current strategy, comparison, selected detail, methodology/result-time assumptions, technical fold, heat map and PDF |
| Individual deterministic | Existing optional primary death year | Actual individual/person/SS record, start/horizon/COLA | Same nine-strategy result, PDF and Break-Even |
| Individual SS / weighted | Each view retains its own four original mortality/valuation controls | Actual individual/person/SS record, start/horizon/COLA | Same separate nine-strategy results, details, frozen assumptions and PDFs |

Inherited information is read-only, with no duplicate plan editor. Compact couple summaries reuse `SocialSecurityAnalyzerInputSummary` rows for configured assumptions; the original complete matrix/source rows are untouched. The weighted compact horizon text points to the retained exact snapshot convention in Input Reference. Missing facts are displayed as not configured, never fabricated inputs.

Current inherited/next-run values are distinct from frozen result-time assumptions. New headings/notes explain that next-run edits do not rewrite the completed run; existing renderers and report contexts remain authoritative for completed metadata.

## 6. Shared-control identity and lifecycle strategy

`sharedConfiguration` is a single retained VBox in the existing dialog header. Its five original controls are constructed once, given presentation field containers once, and never reparented during navigation. A UI-only navigation selection callback updates the host's `visible`/`managed` properties and read-only inherited text. It does not call any financial calculation, request factory, input setter, invalidation method, or job operation.

The new stateless helper formats display facts and arranges existing Nodes. It owns no financial/session state, has no mutable input cache, and installs no input listeners. Labels remain associated with the original controls through `InputHelp.link`; original tooltips and event handlers remain attached. No parsing, range, date converter or numerical formatting of editable text changed.

`currentInputSummary` retains the immutable display snapshot already produced by the original refresh path so navigation can render relevant rows without rebuilding financial requests. It is presentation data, not a second financial draft or a result snapshot.

Tests navigate repeatedly through all modes and assert identical control objects, parents, tooltip objects and `labelFor` associations. Invalid factor text, valid pending discount text, selected dates and unfinished date-editor text survive. One subsequent factor edit increments the established assumptions revision once, demonstrating no duplicate invalidation listeners. Valid edits still propagate to `LongevitySessionSettings`; invalid intermediate text leaves the last valid settings intact without modifying the plan revision.

## 7. Quick Comparison prerequisite presentation

Uses exactly the existing `socialSecurityResultCurrent` flag and Run eligibility:

- **Social Security Only result required**: explains the manual prerequisite; existing Quick Run stays disabled.
- **Social Security Only result current**: confirms the prerequisite and identifies the existing completed ranking as candidate source.
- When present/current, displayed conditioning date, valuation date and discount rate come from the **completed SS request**, not mutable next-run controls.
- **Go to Social Security Only** retains Phase 8B navigation/focus behavior and never starts work.

The explanation directs users to edit shared mortality/PV inputs in SS Only and rerun that analysis. Quick does not display editable copies. Candidate count is immediately before its Run section. The existing explanation of analyzer order/no integrated winner remains with the completed-result area rather than repeating it above the configuration controls.

No automatic pipeline, candidate-count change, precise-PV cutoff change, tie-policy change, ordering change or new readiness model.

## 8. Execution, progress and cancellation layout

Run now follows relevant configuration. Wrapping FlowPane action rows retain the original Run and local Cancel Nodes and handlers, while status Labels wrap separately instead of extending a long HBox offscreen.

The original couple `sharedCancel`, `sharedStatus` and `sharedProgress` are moved once into the fixed top area of `AnalyzerNavigationView`. Visibility follows the existing busy/Cancel state. Phase 8B's active-job identity/status line remains the source for the named active analysis. The original progress view continues displaying phase, completed/total and percentage when available from the same job progress model. There is no second status model, scheduler, executor or cancellation owner.

Existing local Cancel controls are not removed or consolidated. Selecting another workspace does not cancel work or enable a competing Run. Tests navigate during an admitted weighted job, verify fixed progress/Cancel ancestry and active mode/generation, then use the original Cancel handler and prove admission remains held until cleanup and cancelled work cannot publish.

Individual execution sections likewise retain their existing Run/Cancel/progress callbacks and Phase 8B fixed active-job identity; no input sharing or job-controller replacement is introduced.

## 9. Single-person compatibility

Three original direct choices, deterministic initial selection and nine-strategy capabilities remain. Individual SS and weighted factor/date/discount controls remain independent objects and values. The deterministic optional primary death year remains optional and analysis-only. No Quick, spouse or survivor inputs are added.

Original PDF buttons move into their Completed Results section, keeping readiness, handlers, frozen capture and exporter behavior. Deterministic Break-Even stays with its completed result. Current inherited facts are displayed separately from the existing frozen completed summary/details.

The added provenance/configuration sections can place result details below the opening viewport. Geometry tests now use the existing production scroller/reveal path to assert details are reachable and visible, rather than asserting every section must fit initially. All monetary, ranking, frozen-summary and cancellation assertions remain intact.

## 10. Invalidation and state preservation

The Phase 8A matrix remains unchanged:

| Change | SS Only | Quick | Exhaustive | Weighted |
|---|---|---|---|---|
| Plan revision | Stale | Stale | Stale | Stale |
| Mortality/discount/conditioning/valuation edit | Stale | Stale | Preserved | Stale |
| Quick candidate count | Preserved | Stale | Preserved | Preserved |
| New SS result | Current new result | Stale | Preserved | Preserved |
| Weighted baseline edit | Preserved | Preserved | Preserved | Existing weighted-specific stale behavior |
| Navigation/reference folding | Preserved | Preserved | Preserved | Preserved |

Existing job controller/coordinator, request factories, input snapshots, source-plan listeners, baseline validation, stale flags and PDF readiness methods were not changed. Tests also prove invalid hidden longevity input does not disable Exhaustive or alter its frozen presentation, selected heat-map cell or metric. Missing optional weighted baseline still omits only the comparison.

## 11. Responsive layout and accessibility

Phase 8B's actual-logical-width sidebar/compact selection and existing stage minimum remain unchanged. Original fields use wrapping 270-logical-unit presentation groups; action rows wrap without changing input interpretation. Configurations are verified at 1920×1080, 1366×768, 1180×820, 900×650 and an explicitly relaxed 800×600 couple preview budget. Single-person previews also use 800×600. This does not change the production stage minimum.

The complete Input Reference fold remains naturally sized in the existing outer scroller, collapsed initially. Expanding/collapsing it does not apply defaults or invalidate results. Fixed navigation and progress stay reachable even while the reference is expanded; existing result reveal/navigation provides access farther down the scroller.

Headings identify source and lifecycle. Existing label/help associations, tooltip appearance, accessibility metadata and validation semantics are retained. Requiredness notes follow existing request requirements; weighted survivor baseline and deterministic death year are explicitly optional. Hidden shared controls have invisible/unmanaged ancestry and are excluded from normal keyboard traversal. No selection callback requests focus away from the selector.

No result columns, heat-map geometry/color rules, global theme or application-wide navigation were changed. Native Narrator, Windows high contrast and physical DPI configurations were not tested and are not certified here.

## 12. Results, heat maps and PDFs

Completed table/data rendering methods, metrics, ranking/ties, retained strategy identities, current comparisons, legends, optimal markers, result tabs, full-strategy callbacks and PDF report formats are untouched. The only weighted view edits are constructor containment around execution/completed-results headings. All five ranked-result folds retain their original contents. The optional baseline's title now explicitly identifies comparison settings; its original input view and completeness rules are unchanged.

Existing result/PDF tests run alongside the new contextual tests. Phase 8B navigation tests continue proving selected heat-map metric/strategy, candidate count and completed values survive switches. PDF tests still assert frozen capture, readiness and selected table/heat-map state. No report adapters/exporters or result DTOs changed.

## 13. Tests executed

All runs used IntelliJ's bundled Maven and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`. No dependency, Java version or Maven configuration changes.

| Execution | Run | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Before-edit analyzer package + `InputTooltipTest` baseline | 219 | 217 | 0 | 0 | 2 |
| First integration run, before updating obsolete layout assertions | 219 | 214 | 0 | 3 | 2 |
| Existing analyzer/help tests after layout assertion updates | 219 | 217 | 0 | 0 | 2 |
| New contextual + navigation/dialog state/help targeted checks | 69 | 68 | 0 | 0 | 1 |
| Final complete analyzer package + help coverage | 233 | 231 | 0 | 0 | 2 |
| Enabled contextual/navigation rendered previews and contextual checks | 30 | 30 | 0 | 0 | 0 |
| Established complete Maven suite, `-DargLine=-Xmx8g test` | 2,929 | 2,896 | 0 | 0 | 33 |

Counts overlap across commands and are not unique totals. Normal focused skips are the opt-in navigation preview and the older Iowa preview; current navigation/context rendering was enabled separately. The enabled run includes fourteen contextual cases, eleven ordinary direct-navigation cases and five parameterized rendered-preview cases.

The first integration run exposed only old layout expectations: a GridPane parent cast, the removed always-visible heading, and an expectation that individual details fit the first viewport. These were updated for the approved wrapping/context/scroller structure without changing financial assertions or golden values. No failing financial calculation required correction.

Focused tests cover control visibility and identity, raw text/date preservation, one-edit/one-invalidation behavior, session ownership, candidate prerequisite, hidden invalid inputs, optional baseline, fixed cancellation/progress, reference folds and Run reachability. Existing request/job/date/result/PDF tests protect original semantics and publication rules. Preview fixtures use synthetic values and deterministic queued work; an active-job screenshot is not represented as a completed financial search.

Complete suite: **BUILD SUCCESS**, 7 minutes 47 seconds, finished October 18, 2026 at 15:58:42 EDT. The established opt-in/disabled cases account for the 33 skips; current rendered previews passed separately when enabled. Existing financial-equivalence, deterministic/weighted analysis, seeded Monte Carlo, tax, RMD, Roth, persistence and wizard lifecycle checks passed with no golden updates. No sandbox/dependency restriction blocked verification.

Logs are ignored local artifacts: `target/phase8c-baseline.log`, `phase8c-first.log`, `phase8c-focused-existing.log`, `phase8c-context-tests.log`, `phase8c-focused-final.log`, `phase8c-preview-final.log`, and `phase8c-full.log` (all under `target/`).

## 14. Rendered-preview evidence

Reused the existing pulse-synchronized JavaFX preview infrastructure with:

```text
-Dtest=AnalyzerDirectNavigationTest,AnalyzerContextualInputsTest
-Danalyzer.navigation.preview=true
-Danalyzer.context.preview=true
-Danalyzer.preview.output=target/phase8c-context-preview
```

Generated **55 PNGs**: eleven per size budget (four couple workspaces, expanded reference, retained invalid input, weighted heat map, active-job navigation and three individual workspaces). Actual JavaFX windows were rendered and representative images opened/inspected, including:

- `couple-SOCIAL_SECURITY-1366x768.png`: authoritative plan facts, five shared fields, nearby Run and unchanged four result tabs.
- `couple-QUICK-900x650.png`: candidate count, explicit missing prerequisite/link, Run after configuration; no editable mortality/PV fields.
- `couple-EXHAUSTIVE-900x650.png`: read-only configured plan summary and visible Run; no irrelevant editable inputs.
- `couple-WEIGHTED-1920x1080.png`: shared settings, optional comparison baseline, execution and original ranked-result content.
- `couple-pending-invalid-1180x820.png`: the same unfinished factor text retained after switching to Weighted.
- `couple-active-job-900x650.png`: active weighted identity, original graphical progress and Cancel fixed above a different selected workspace.
- `couple-reference-expanded-900x650.png`: retained full objectives, dependency matrix and explanatory content; navigation/footer remain available.
- `couple-heat-map-1366x768.png`: existing grid/legend/metric/details navigation preserved.
- `single-SOCIAL_SECURITY-800x600.png` and `single-EXHAUSTIVE-1180x820.png`: compact source/input/execution/results grouping; original individual controls/actions.

Inspection found excess repeated Quick text and missing Exhaustive padding during intermediate layout work; the existing explanation was retained with results and padding restored before final capture. No result data/content was removed. Final previews use actual JavaFX logical budgets, not native Windows scaling or assistive-technology certification.

## 15. Limitations and Phase 8D deferrals

- Reference material remains extensive when expanded; it is intentionally retained, collapsed by default and vertically scrollable.
- Optional weighted baseline retains its existing initial expansion behavior. Small windows may need vertical scrolling to reach Run/results; geometry tests verify reachability.
- Existing result-table horizontal scrolling/truncated headers remain; no result redesign was authorized.
- Native Windows Narrator, high contrast and 100/125/150/200% scaling remain manual checks. Verify focus/traversal after hiding controls, shared progress at high text scaling, reference collapse and PDF selection capture in the local desktop environment.
- No Phase 8D/8E feature expansion, persistent result storage, history, automatic SS-to-Quick pipeline or household lifecycle redesign.

## 16. Final Git status

Final working tree contains **10 tracked modifications and 3 untracked new files**, exactly the Section 3 inventory. New files are this report, `AnalyzerConfigurationLayout.java`, and `AnalyzerContextualInputsTest.java`. Index is unchanged with no staged changes. No commits, pushes, resets or unrelated work removal have occurred. Protected financial/application-service/persistence/resource/golden/build paths have no diff; the existing Phase 8A/8B reports are unchanged.

Final `git diff --check` passes (exit 0), as do separate trailing-whitespace checks on new files. Generated PNGs and Maven logs remain ignored under `target/`. No financial, persistence or result-format changes were made. Phase 8C implementation, focused/full regression and rendered-preview criteria are complete; native OS-level manual checks remain explicitly documented above. Stop here without beginning Phase 8D, Phase 8E or persistence work.

PHASE-8C-SOCIAL-SECURITY-CONTEXTUAL-INPUTS-COMPLETE
