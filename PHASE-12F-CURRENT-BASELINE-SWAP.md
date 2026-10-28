# Phase 12F — Atomic Current/Baseline Projection Swap

## Current implementation status

**READY FOR REVIEW AND NATIVE WINDOWS ACCEPTANCE.** The user approved both preflight policy decisions. Implementation is complete. Final focused: **219 passed**. Final full suite: **3,549 total; 3,505 passed; 44 skipped; 0 failures; 0 errors**, **10:30**. The decision gate preserved later in this document is an archived record, not the current implementation status. Native Windows acceptance of Phase 12F is pending; user-confirmed acceptance of 12D/12E is not substituted for it. No subsequent phase has begun.

### Approved financial and calculation boundaries

- Results Summary retains the unchanged ProjectionComparisonService: full-horizon income/growth/tax totals, peak tax and each projection's own original ending-year values. Both ending years are now explicitly displayed. No shared-year normalization or extrapolation is introduced.
- Break-Even retains its existing shared-year rules. Swapping and restored-results rendering do not invoke it. The Summary action requests it explicitly, using the newly installed roles. Directional results and insight ownership are detached on swap/invalidation and keyed to the exact current/baseline result and baseline identity.
- Projection engines, claiming searches, Monte Carlo, taxes, RMDs, Roth conversions, mortality models, rankings and financial golden values are unchanged. Existing financial arithmetic for Summary comparisons is preserved; it is not a projection or analyzer execution.

## State transition and atomicity

ApplicationController owns the operation. prepareProjectionSwap captures the active plan object, file binding, manifest and baseline identities, exact input freshness, source revision, projection generation, and an independent swap sequence. It returns an opaque prepared transaction; target mutable domain graphs are not exposed to callers.

The existing bounded AnalysisResultStore background worker verifies original immutable components against their owner/UUID/digest. A pending in-memory component is checked with the same bounded standard-projection encoder, without writing files. Input documents must reconstruct and reserialize exactly. Original baseline inputs must match their original component. Associated artifacts for the two changing roles are verified sequentially through the Phase 12E resolver, with exact projection-input and household binding; large decoded analyzer graphs are not accumulated.

Preparation restores the former baseline's five financial roots into a detached editable RetirementPlan with the SAME active plan UUID. It constructs the reversed manifest directly, retaining version, original role-reference UUIDs/digests, all analyzer associations and earlier pin references. It reconstructs both annual graphs, non-investable annual values, frozen input comparisons, all chart metrics/annotations, and summary/income/statistical values from immutable DTOs. No engine is available in this path.

commitProjectionSwap is an application-owner/FX-thread operation. It rechecks the captured state tuple and exact configured-input freshness. Only then does it install the complete prepared caches, replace the active plan object, rebind plan-owned accepted parameter metadata, invalidate old projection/analyzer admissions, increment the source revision, mark the document modified, and notify existing source listeners. Reconstruction and component verification cannot fail halfway through publication because they have already completed. No asynchronous work is awaited on the FX thread. Existing callback ownership checks reject the replaced plan object and changed revision/generation.

The original immutable snapshots are never modified. New current/baseline objects use the same snapshot UUIDs, original acceptance times, input digests, annual values, and provenance. Role-level baseline description identifies the original accepted projection; its role date uses that original acceptance instant in the system local zone. A second swap restores exact financial input documents, original role references, annual graphs, chart data and summaries. Ordinary dirty/revision/UI metadata and baseline role description/date need not be byte-identical.

## Eligibility and dirty-state behavior

The cache-only readiness explanation checks complete roles and baseline metadata, active ownership, restored data availability, matching current configured inputs, pending restoration, parameter-save failures/pending writes, historical clone completion, and the shared active Social Security job lease. It does not infer freshness from a clean dirty flag. Component corruption and unsupported schemas are rejected during worker preparation, before any publication. Input-only legacy baselines cannot be swapped.

An identical current/baseline UUID produces an explicit no-op; the UI does not dirty or save anything for that case. A second concurrent preparation is refused. Obsolete sequence callbacks cannot cancel a newer prepared transaction.

MainWindow refuses unresolved Household/Assumptions/Roth drafts with an explanation to apply/cancel and accept a matching projection first. It does not reuse saveCurrentPlan's draft-application branch, which can recalculate. Dirty but fresh plans receive exactly `Save Before Swapping Projections?` with `Save and Continue` and `Cancel`. The dialog is revalidated after confirmation. Never-saved plans must first use an explicit ordinary Save/Save As outside the swap; the swap does not substitute an implicit Save As.

Save and Continue uses the established ordinary repository Save transaction with a detached captured document and captured path, cooperative locking, component staging, durable checks and atomic replacement. Financial roots, baseline inputs and bounded immutable metadata are captured separately from live mutable state. This matters because the repository materializes references and clears the transient pending maps of its argument after commit; passing the live plan from a worker could otherwise erase a newly accepted association. It runs on the existing background worker. Owner-thread finalization clears the dirty flag and adopts materialized references only if owner UUID/object, path, revision, generation, input document, baseline, configuration and manifest still match. A plan switch or new association/configuration during Save may finish the explicitly accepted earlier document write to the original file, but cannot finalize or swap the changed active state. Its newer accepted memory state remains available for a subsequent ordinary Save. Save failure prevents preparation; Cancel prevents any role exchange. An accepted file write is not force-canceled if the progress dialog closes.

After successful exchange the plan is dirty again. The status explicitly asks for ordinary Save to keep the new arrangement. There is no automatic full-plan save of swapped inputs. Failure of that later Save preserves the old durable manifest and leaves the coherent in-memory swap dirty/retryable.

## UI, charts and comparisons

`Swap Current and Baseline` appears immediately after View Baseline Details in the existing wrapping FlowPane beside Break-Even. It retains standard JavaFX keyboard access, accessibility help and an explanatory readiness tooltip. MainWindow handles confirmation, modal bounded-work progress, cancellation, and guarded completion; no new swap screen or executor is introduced.

On success MainWindow reloads the editable input views and uses the existing restored-result refresh path, without invalidation/reprojection. Summary, dashboard, portfolio/annual views, original chart models, baseline comparison and Baseline Details all receive the matching prepared result context. The unchanged chart model retains all 13 metrics, actual conversion/RMD periods, exact-year Social Security annotations, hover and year-selection behavior. For restored/swapped Summary inputs, the initial Social Security monthly display reads the accepted income summary rather than invoking the editable-benefit preview calculator. A later explicit election edit still uses the established preview behavior.

Break-Even's initial post-swap presentation is unavailable, not stale or falsely reversed. Its Summary button remains available when both cached projections are present and calculates only on explicit user action. Original/restored displays also defer it until explicit request, avoiding automatic analysis on restart. Live directional pair keys are detached on invalidation and checked against the exact pair before reuse; immutable observations for an already-open original report are retained independently.

Manual role exchange does not alter analyzer navigation defaults, heat-map palettes, PDF adapters or accepted parameter configurations. Historical Analyzer History already resolves roles through UUID references; no renderer or payload copy is needed.

## Persistence, history and compatibility

No manifest, component, analyzer payload, financial fingerprint or storage-limit version changes. The existing version-2 manifest directly represents reversed role references. Phase 12D ordinary Save and Save As stage/verify the same immutable components, then atomically publish inputs and manifest. Save As retains original result IDs and data but establishes independent owner/path binding. Restart restores original results and the frozen configured inputs without projection or analyzer execution.

All 15 configurations and the existing plan-owned longevity session remain unchanged across a role exchange. All 15 immutable analyzer associations follow their original projection UUID; earlier pins remain retained. Replaceable last-success slots are neither relabeled nor rewritten by swapping. Missing/legacy associations are never inferred. Capacity remains 30 associations, 30 pinned projections, 32 MiB decoded/16 MiB compressed per component with the existing structural limits and shared 128 MiB/1,000-entry owner budget. Verified immutable in-memory components are reused after comparing their persisted contents; another large decoded DTO graph is not retained merely to change roles. No automatic garbage collection.

## Regression evidence and visual inspection

Existing preflight: 31 passed. Initial implementation focused run: 47 passed. Initial new swap/association tests: 23 passed. Broader focused run: **214 passed, 0 failed, 0 errors, 0 skipped**, Maven **01:05**, `target/phase12f-focused.log`; final follow-up/full-source results are recorded below. No tests were weakened, disabled, or golden values regenerated. Early compilation/test-fixture wiring mistakes were corrected before passing runs (JavaFX imports, repository fault-injection constructor, attaching CSS skins before node lookup, and avoiding the exit-drain API during a continuing test).

New tests independently compare restored annual DTOs, summary values, exact financial input strings/scales, UUIDs and all 13 chart values. They cover couple/individual double swaps, no-op/cancel, stale inputs, differing horizons and reversed full-horizon differences, explicit shared-year Break-Even, Save-before and Save failure, atomic post-swap Save failure/retry, corruption/missing/unsupported/legacy refusal, superseded projection and baseline preparation, plan switch during a controlled blocked Save, repeated preparation and obsolete callbacks, and all 15 parameter configurations. The analyzer matrix covers all 15 variants through role exchange, durable restart and independently owned Save As, with late association rejection and last-success slot independence. Throwing projection and Break-Even factories prove no engine execution during swap/render/restoration; historical rendering uses existing DTO-only adapters.

Actual PNG previews generated under ignored `target/phase12f-previews/` and inspected: couple before/after chart, individual after chart at 900×650 dark, couple/individual Baseline Details, both history dialogs with explicit not-captured state, action row, save confirmation and stale-input explanation. Standard light previews use 1180×820. Charts show the exchanged original values, readable currency/year axes and Social Security marker. Baseline Details shows the former current chart as the original saved baseline. The confirmation has only Save and Continue/Cancel. History's default standalone dialog styling remains existing light styling; no global theme redesign was attempted. The dark individual chart and Details are readable; system-native DPI, screen reader, high-contrast and full Windows behavior are not certified by snapshots. Existing chart palette/annotation styling is unchanged.

## Exact Phase 12F source/test inventory

Modified production files (relative to `src/main/java/com/daviddunn/retirementplanner/`):

1. `ui/controller/ApplicationController.java`: preparation/commit/save handoff, eligibility, generation guards and explicit directional Break-Even ownership.
2. `ui/MainWindow.java`: adjacent action wiring, Save/Cancel, captured background workflow, UI epochs, restored-only refresh; constructor overload accepts an application-owned controller without reopening another file.
3. `ui/views/ResultsSummaryView.java`: action/readiness, respective ending-year labels, explicit Break-Even request, accepted-income preview on restoration.
4. `persistence/ProjectionSnapshotComponents.java`: pure bounded validation of a pending immutable projection; no wire-format change.
5. `ui/socialsecurity/ProjectionAnalyzerHistoryDialog.java`: filter analyzer identities by the selected projection's recorded household shape, including when a former couple baseline is promoted over a single-person current projection (or vice versa). Uses immutable people/association metadata, with no financial calculation or large FX-thread decoding.

New test files (relative to `src/test/java/com/daviddunn/retirementplanner/`):

1. `persistence/ProjectionSwapTest.java`.
2. `ui/socialsecurity/Phase12FAnalyzerSwapTest.java`.
3. `ui/baseline/ProjectionSwapViewTest.java`.

Report: `PHASE-12F-CURRENT-BASELINE-SWAP.md`. All other tracked/untracked work is preserved from earlier phases. Generated PNGs, logs, local Maven cache and test artifacts are excluded from a future commit. No CSS, dependency, calculation engine, tax rule, financial golden resource, or seeded Monte Carlo expectation was modified.

## Native Windows acceptance checklist

1. Rebuild and restart normally. Open a saved couple plan with two complete distinct projections; note both original UUIDs, ending years, chart values, summary totals and analyzer history.
2. Swap a clean fresh pair. Confirm editable household/accounts/settings and all current charts become the former baseline; Baseline Details becomes the former current. No projection/analysis progress should appear.
3. Request Break-Even explicitly. Confirm its shared-year direction now follows the exchanged roles. No old directional result should appear before that request.
4. Swap again (Save and Continue when prompted). Confirm original UUIDs, financial values, assumptions and chart annotations return. Save and restart; confirm the saved arrangement and no automatic financial execution.
5. Repeat with an individual plan, different horizons, nonzero RMD/Roth activity and original Social Security claim markers. Summary must show each ending year and its own full-horizon values.
6. Dirty but fresh pair: Cancel must leave everything untouched; Save and Continue must durably save the pre-swap arrangement, then leave the new arrangement dirty until ordinary Save. Simulate a failed Save if practical and confirm no exchange.
7. Edit inputs without accepting a new projection; saving alone must not authorize swapping. Legacy/input-only or missing components must show refusal, never rebuild automatically.
8. Check all available analyzer associations under Current/Baseline, earlier pins and independent last-success dialogs. Their original financial results/settings/timestamps/PDFs must remain unchanged.
9. Save As the swapped plan, reopen original and clone independently, and verify ownership and role arrangement. Test reduced window/dark theme, keyboard controls, hover and click-to-select-year.

## Final-source focused verification and regression correction

Final focused run: **216 total, 216 passed, 0 failures, 0 errors, 0 skipped**, Maven **01:16**, completed October 27, 2026 at **11:36:58 America/New_York**. Log: `target/phase12f-final-focused.log`. This includes all **32** new Phase 12F cases (13 controller/storage, 15 analyzer variants, 4 JavaFX cases), including actual Save and Continue/repeated-click handling and actual nonempty Social Security/RMD/Roth annotations.

The first full run found one regression: BreakEvenControllerTest requires frozen explanatory observations for an already-open original report to survive projection-cache invalidation. Clearing both the live pair and the immutable observation cache violated that contract. The narrow correction detaches only the live pair's projection/baseline identity keys, while retaining the immutable original report context. No assertion or financial expectation was changed. The existing regression and new explicit post-swap directional tests pass in the final focused run.

Further role-navigation review identified that the Phase 12E history selector originally filtered all roles by the active editable household. The local correction filters by the selected immutable projection's recorded people metadata, or its recorded association shape for earlier pins. This preserves visibility when the two accepted roles have different household compositions. A new regression promotes a couple baseline over an accepted single-person current projection and verifies each role's distinct analyzer choices. This case is included in the final follow-up results below; it is additional to the preceding 216-test run.

The detached Save handoff also fixes a demonstrated concurrency risk at the new background Save boundary. A controlled repository VERIFY latch permits an eligible analyzer association to be accepted while Save is in progress. The captured document commits coherently, finalization rejects the changed manifest, the newer pending association is retained, and an explicit retry commits it without recalculation. Another case changes the UUID during preparation and verifies refusal. These are transaction/ownership corrections within Phase 12F, not financial or schema changes.

Latest focused acceptance run: **219 total, 219 passed, 0 failures, 0 errors, 0 skipped**, Maven **01:07**, completed **October 27, 2026, 12:01:50 America/New_York**. Log: `target/phase12f-acceptance-focused.log`. It contains all **35 new Phase 12F cases**: 15 controller/storage, 15 analyzer identities, and 5 JavaFX cases. The preceding release-focused run passed 217 and its full run passed 3,547 total / 3,503 passed / 44 skipped / zero failures/errors in 10:23, before the last detached-Save and explicit UUID tests; those are not substituted for the final full-source acceptance result below.

That earlier full run executed **3,544 tests: 3,499 passed, 1 failed, 0 errors, 44 skipped**, Maven **10:32** (`target/phase12f-full.log`). It preceded the last two new cases and final corrections and is not release evidence. Final full-source results are recorded below. The final source also updates the swap notice after successful ordinary Save/Save As so it no longer claims that already-saved roles still require saving.

## Known limitations and future recommendations

- Legacy input-only baselines cannot swap. Missing/corrupt components require explicit recovery; no automatic calculation repairs them.
- Snapshot completeness/freshness, not the dirty flag alone, governs promotion. Pending editable drafts are refused rather than implicitly applied/recalculated.
- Accepted role changes are memory-only until ordinary Save. Canceling a progress dialog cannot undo a file transaction already explicitly accepted; it does prevent the later swap.
- Local role description/capture metadata may change; original component acceptance timestamps, IDs, digests and financial values do not. No unrecorded historical build provenance is invented.
- Earlier pin artifacts are not rewritten merely to change visible roles. Their existing explicit load-time validation remains authoritative; the changing role artifacts are reverified during preparation.
- No new full-size capacity simulation or native acceptance is claimed. Existing bounds/Phase 12D/12E capacity evidence are reused, and pending in-memory component validation uses their unchanged encoder.
- No subsequent phase is started or recommended for automatic execution. Await Phase 12F review and native acceptance.

## Final acceptance verification

Final full-source run, October 27, 2026, finished **12:12:21 America/New_York**:

| Verification | Total | Passed | Failures | Errors | Skipped | Maven time |
|---|---:|---:|---:|---:|---:|---|
| Focused acceptance | 219 | 219 | 0 | 0 | 0 | 01:07 |
| Complete Maven suite | 3,549 | 3,505 | 0 | 0 | 44 | 10:30 |

Logs: `target/phase12f-acceptance-focused.log` and `target/phase12f-acceptance-full.log`. Both native Maven exits were **0**. Independently summed current-run Surefire XML for the **405 classes named in the full log** agrees exactly; older XML left in target was not counted as a new execution. All **35 new Phase 12F tests passed**. The 44 skips remain existing conditional/opt-in benchmark, visual/manual and deferred cases. No exclusions, disabled tests, relaxed assertions, new dependencies, regenerated goldens or seeded-result changes were introduced.

The complete suite includes previous Phase 9–12 snapshot, codec, repository, compatibility, financial, Monte Carlo, UI, PDF and parameter regressions. Two earlier full iterations also passed (3,546 total in 10:14 and 3,547 total in 10:23) before the final race/ownership additions; they are supporting history, not substitutes for this final source run. The first full failure and its frozen-insight correction remain documented above.

Final `git diff --check`: **exit 0**. Index check: **exit 0 / empty**. Branch and HEAD unchanged. There are **39 modified tracked files and 49 expanded untracked files** across all preserved phases. The Phase 12F scope is the nine paths listed above; the complete expanded inventory follows later in this report. Generated logs/previews and `.codex-m2/repository` are confirmed ignored. No staging, commits, pushes, resets, cleaning, or discarded work.

**Release handoff:** implementation and automated verification are complete; perform the native checklist before declaring Windows acceptance. New role arrangements require explicit ordinary Save for durability. Input-only legacy or stale projections cannot swap. No further feature phase is started.

---

## Archived preflight decision-gate record (superseded by user approval)

The following records the original preflight and initial working-tree inventory. Its blocked status and approval requests describe the earlier gate only; both decisions are now explicitly approved and implemented above.

## Executive summary

**BLOCKED — POLICY CLARIFICATION REQUIRED. Implementation has not begun.**

The requested preflight found two material differences between the requested comparison behavior and the existing implementation. They must be resolved before publishing a swap or changing its UI refresh path. No production code, tests, financial semantics, persistence schemas, or accepted prior-phase behavior were modified.

The user confirms native Windows acceptance of Phases 12D and 12E. That is user-provided evidence, not an independently executed native test. Phase 12F is not complete or ready for native acceptance.

## Baseline and preserved work

- Branch: `main`.
- HEAD: `e620bec4a0011ab12d15fce670bff5011093d9da`.
- Initial inventory: 39 modified tracked files and 45 expanded untracked files; index empty. All accumulated Phase 11/12 work is preserved.
- This phase adds only this report. Generated Maven output is under ignored `target/`.
- Read the project instructions and relevant Phase 12C/12D/12E/12B and Phase 11 report material; inspected actual controller, manifest, immutable projection component, baseline input restoration, plan model, comparison service, Break-Even analyzer, repository-facing save paths, parameter coordinator, MainWindow, and Results Summary call sites. This is a targeted preflight, not a completed whole-feature concurrency or release audit.

## Finding 1: Different-horizon comparison policy

`domain/baseline/ProjectionComparisonService.compare` has one comparison entry point. It calls `calculate` separately for each complete projection. That method sums each projection's own annual investment growth, guaranteed income, and taxes, computes its own peak annual tax, and reads its own final year's investable assets, net worth, and after-tax estate. The comparison's calendar-year label is the current projection's final year. There is no shared-year comparison overload or intersection in this service.

`ApplicationController.compareProjections` and `ResultsSummaryView.updateBaselineComparison` use that service directly. `ProjectionComparisonServiceTest.calculatesLifetimeMetricsAndFinalYearValues` explicitly verifies full-horizon sums and final-year values.

In contrast, `domain/breakeven/BreakEvenAnalyzer.analyze` intersects the saved annual year sets. It is the source of existing shared-year rules, including cumulative Social Security beginning at the first shared year.

Section 11 of the Phase 12F request requires preserving the existing Summary financial definitions while also requiring existing shared-year comparison rules for differing horizons. Those rules belong to different services today. Truncating Summary inputs to a shared interval would change lifetime totals, peak tax, and potentially ending values. It cannot be described as a role-only exchange preserving the existing Summary definitions.

**Recommended decision:** preserve Results Summary's existing full-horizon/own-ending-year definitions after swapping, and keep shared-year behavior confined to the existing Break-Even analysis. Document unequal horizons clearly; no extrapolation or invented annual results. If shared-year Summary values are required instead, that is a separately approved financial presentation/calculation contract change with dedicated tests, not an assumed swap plumbing change.

## Finding 2: No-analysis swap versus automatic Break-Even refresh

`ApplicationController.getCachedBreakEvenAnalysis` is cache-only with respect to projection execution, but it is not a getter for an already computed Break-Even result. Each call invokes `new BreakEvenAnalyzer().analyze(...)` and prepares insights from the populated annual result caches.

It is called automatically in both:

- `MainWindow.refreshProjectionViews`, including the restored-results rendering path.
- `ResultsSummaryView.updateBaselineComparison`, during Summary loading.

Consequently, merely reusing the accepted load-only refresh after a swap would run an analyzer. It would not run a projection engine, but would violate the explicit Phase 12F prohibition on any other analyzer executing as part of swapping.

The Phase 12C audit already identified this policy choice in its Break-Even integration section and decision 7. Phase 12E did not add a persisted pair-context artifact or both precomputed directed Break-Even results. The current manifest contains projection-role references, analyzer associations, and pinned projection references, not a directed baseline/current comparison component. Individual deterministic snapshots' stored candidate comparisons are unrelated to the ordinary current/baseline pair.

**Recommended decision for a strict no-analysis swap:** allow the swap-specific refresh to invalidate the previous directional Break-Even presentation without running its analyzer. Keep the new Summary and charts immediately correct from saved data. Make Break-Even computation an explicit later user action using the existing unchanged analyzer and shared-year rules. This would change immediate post-swap Break-Even readiness/display behavior and therefore requires approval rather than being silently introduced.

Alternatives needing explicit authorization are permitting existing observational Break-Even arithmetic during swap refresh, or introducing bounded precomputed directed pair contexts outside the swap. The latter adds lifecycle/storage design beyond a role-reference exchange. Negating old crossings or relabeling the old result is unsafe: first and sustained crossover semantics are directional.

## Intended state transition and invariants (proposal, not implementation)

1. Capture active plan object/UUID/path, manifest, baseline identity, accepted current/baseline references, financial input document, source revision, projection generation, and relevant analyzer/save lifecycle state.
2. Reject missing/incomplete/legacy/corrupt components, ownership or input-digest mismatches, stale configured inputs, and unresolved conflicting work. Equal role UUIDs return a no-op without mutation.
3. If dirty, ask `Save Before Swapping Projections?` with only Save and Continue or Cancel. Use an ordinary Save, never configuration-only save or an implicit Save As. Revalidate the captured identities/generations and exact input freshness afterward.
4. On the existing bounded worker, verify required immutable components and prepare detached editable financial roots from the former baseline's original input document; prepare annual graphs, summaries, non-investable data, chart models and input contexts. Do not execute financial engines. Preserve all 15 plan-owned accepted parameter configurations independently of historical financial inputs.
5. Before publication, recheck the entire ownership/generation/manifest/input tuple. A newly accepted result, baseline change, plan switch, or stale callback invalidates the preparation.
6. Publish the prepared state on the FX/application owner thread as one coherent transition: exchange the same UUID references, install restored financial inputs and matching result caches, retain historical associations on original projection UUIDs, invalidate old generations and pair-specific presentation, and mark the plan modified. The method of preventing observer/listener exposure to partial state still requires implementation and regression proof; this report does not claim an existing transaction API already guarantees it.
7. Render inputs, Summary, all standard charts and history from that accepted state without invoking lazy calculation paths. The exact Break-Even branch depends on the decision above.
8. Ordinary Save remains the durability boundary. Reuse immutable components and existing atomic manifest commit; failed Save leaves the prior durable arrangement usable and coherent in-memory state dirty/retryable. Save As must retain logical roles with independent owner binding.
9. A second swap with no edits/calculations restores original financial input documents, role UUIDs, annual data, summaries and association identities. Dirty flags and operation/UI metadata need not return byte-for-byte.

No snapshot schema change or new financial fingerprint exclusion is proposed for the role exchange itself. No last-success analyzer slot is rewritten to impersonate a projection-associated historical result. Earlier pinned projections remain retained; the 30/30 association/pin bounds and shared storage limits remain unchanged.

## Other verified preflight risks to cover after the decision

- `MainWindow.saveCurrentPlan` can refresh/recalculate projections when applying view drafts. Save-before-swap must not blindly reuse a path that recalculates while claiming a calculation-free swap. Pending drafts need explicit freshness/refusal handling before ordinary Save; a successful write alone cannot authorize promotion.
- The standard `refreshAllViews` path recalculates. The existing restored-results path is the starting point for non-projecting rendering, subject to removing automatic Break-Even execution only as approved.
- Replacing the editable plan object would affect application-owned parameter bindings and pending writes. Preserve/revalidate accepted metadata and binding semantics, rather than copying financial roots while silently losing pending accepted settings.
- `ProjectionBaseline` has role-level description/capture time in addition to the immutable projection's acceptance time. Preserve original snapshot UUID/provenance/acceptance time and explicitly document any newly assigned role metadata.
- Current and baseline references may initially identify the same accepted projection. A no-op must not dirty the plan or alter associations.

These are implementation requirements, not additional claimed blockers or completed fixes.

## Verification actually run

October 27, 2026, IntelliJ bundled Maven, project-local `.codex-m2/repository`, `-DargLine=-Xmx4g`:

`-Dtest=ProjectionComparisonServiceTest,BreakEvenAnalyzerTest,ProjectionSnapshotPersistenceTest test`

- Total: **31**.
- Passed: **31**.
- Failures: **0**.
- Errors: **0**.
- Skipped: **0**.
- Maven time: **14.066 seconds**.
- Log: `target/phase12f-preflight.log`.

These are existing preflight tests, not new swap acceptance coverage. No tests were changed or disabled. The complete suite was not rerun because implementation stopped at the mandatory policy gate. Previous Phase 12E full-suite results are not claimed as a new Phase 12F run.

`git diff --check` passed using command-local `-c core.safecrlf=false` to suppress line-ending advisories; no repository configuration was changed. No native previews were generated: there is no implemented swap UI to inspect. Native Windows swap acceptance remains pending.

## Exact Phase 12F inventory and completion gate

Added: `PHASE-12F-CURRENT-BASELINE-SWAP.md` only.

Production/test/resource changes: none. No staging, commits, pushes, resets, cleaning, deletion, or discarded work. No subsequent phase began.

Approval requested for the Summary differing-horizon policy and the strict no-analysis Break-Even refresh policy described above. After those decisions, resume the authorized bounded implementation, add the requested acceptance/race/persistence/UI tests, run focused and full regression suites, inspect previews, and replace this gate status with actual implementation evidence.

## Working-tree inventory at preflight completion

The following inventory includes preserved prior-phase work and this report; it is not a proposal to commit unrelated changes.

Modified tracked paths (39):

- `src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerPdfExporter.java`
- `src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerReport.java`
- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloFingerprintService.java`
- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPlanDocument.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/RetirementPlanScenarioCopyService.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AcceptedAnalysisCompletion.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisFingerprintService.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ExhaustiveSnapshot.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/baseline/ProjectionBaseline.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/model/RetirementPlan.java`
- `src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java`
- `src/main/java/com/daviddunn/retirementplanner/persistence/JsonRetirementPlanRepository.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/charts/ProjectionChartView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloParameterControls.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerNavigationView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ClaimingStrategyHeatMapView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveResultSnapshots.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualCompletionPersistence.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IntegratedAnalysisResultTabs.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/LongevityWeightedIntegratedView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonMortalityView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityParameterBinding.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedResultPersistence.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/controller/ApplicationControllerTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/controller/BreakEvenControllerTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloComparisonViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/CoupleSocialSecurityPersistenceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedRestorationTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/SinglePersonHouseholdUiTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/CompleteWizardEndToEndTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/StateTaxWizardIntegrationTest.java`

Untracked paths (46):

- `PHASE-11A-SOCIAL-SECURITY-UI-PDF-AUDIT.md`
- `PHASE-11B-SHARED-GRID-AND-NAVIGATION.md`
- `PHASE-11C-SOCIAL-SECURITY-AND-QUICK-PDF-EXPORT.md`
- `PHASE-11D-FINAL-INTEGRATION-AND-REGRESSION-AUDIT.md`
- `PHASE-12A-BASELINE-DETAILS-ARCHITECTURE-AUDIT.md`
- `PHASE-12B-BASELINE-DETAILS-IMPLEMENTATION.md`
- `PHASE-12C-PROJECTION-SNAPSHOT-AND-SWAP-ARCHITECTURE-AUDIT.md`
- `PHASE-12D-COMPLETE-PROJECTION-SNAPSHOT-PERSISTENCE.md`
- `PHASE-12E-IMMUTABLE-ANALYZER-ASSOCIATIONS.md`
- `PHASE-12F-CURRENT-BASELINE-SWAP.md`
- `src/main/java/com/daviddunn/retirementplanner/app/baseline/BaselineDetailsSession.java`
- `src/main/java/com/daviddunn/retirementplanner/app/baseline/BaselineParameterComparison.java`
- `src/main/java/com/daviddunn/retirementplanner/app/baseline/ProjectionInputs.java`
- `src/main/java/com/daviddunn/retirementplanner/app/projection/AnalyzerAdmission.java`
- `src/main/java/com/daviddunn/retirementplanner/app/projection/AnalyzerAssociation.java`
- `src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java`
- `src/main/java/com/daviddunn/retirementplanner/app/projection/ProjectionRoleManifest.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/IndividualDeterministicSnapshot.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/QuickComparisonSnapshot.java`
- `src/main/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotComponents.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisPdfData.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ClaimingAgeGridView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/HistoricalAnalyzerPresentation.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ProjectionAnalyzerHistoryDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/QuickComparisonReportAdapter.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/QuickReportSession.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/QuickSnapshotAdapter.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationGuard.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityClaimingGridAdapter.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityOnlyReportAdapter.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityReportSession.java`
- `src/main/resources/css/baseline-details.css`
- `src/test/java/com/daviddunn/retirementplanner/app/baseline/BaselineDetailsTest.java`
- `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/Phase12EHistoryFixtures.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/AnalyzerAssociationStorageTest.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotPersistenceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloProjectionAssociationTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12EAssociationTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12ECodecTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12EIndividualLifecycleTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SharedClaimingAgeGridTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAndQuickPdfTest.java`

## Final working-tree inventory (all accumulated work)

Branch main; HEAD e620bec4a0011ab12d15fce670bff5011093d9da. Index empty. 39 modified tracked paths and 49 expanded untracked paths. The nine Phase 12F paths are identified above; all earlier-phase paths are preserved. Generated target outputs and the local Maven repository remain ignored and excluded.

Modified tracked paths:

- `src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerPdfExporter.java`
- `src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerReport.java`
- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloFingerprintService.java`
- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPlanDocument.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/RetirementPlanScenarioCopyService.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AcceptedAnalysisCompletion.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisFingerprintService.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ExhaustiveSnapshot.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/baseline/ProjectionBaseline.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/model/RetirementPlan.java`
- `src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java`
- `src/main/java/com/daviddunn/retirementplanner/persistence/JsonRetirementPlanRepository.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/charts/ProjectionChartView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloParameterControls.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerNavigationView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ClaimingStrategyHeatMapView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveResultSnapshots.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualCompletionPersistence.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IntegratedAnalysisResultTabs.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/LongevityWeightedIntegratedView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonMortalityView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityParameterBinding.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedResultPersistence.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/controller/ApplicationControllerTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/controller/BreakEvenControllerTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloComparisonViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/CoupleSocialSecurityPersistenceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedRestorationTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/SinglePersonHouseholdUiTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/CompleteWizardEndToEndTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/StateTaxWizardIntegrationTest.java`

Untracked paths:

- `PHASE-11A-SOCIAL-SECURITY-UI-PDF-AUDIT.md`
- `PHASE-11B-SHARED-GRID-AND-NAVIGATION.md`
- `PHASE-11C-SOCIAL-SECURITY-AND-QUICK-PDF-EXPORT.md`
- `PHASE-11D-FINAL-INTEGRATION-AND-REGRESSION-AUDIT.md`
- `PHASE-12A-BASELINE-DETAILS-ARCHITECTURE-AUDIT.md`
- `PHASE-12B-BASELINE-DETAILS-IMPLEMENTATION.md`
- `PHASE-12C-PROJECTION-SNAPSHOT-AND-SWAP-ARCHITECTURE-AUDIT.md`
- `PHASE-12D-COMPLETE-PROJECTION-SNAPSHOT-PERSISTENCE.md`
- `PHASE-12E-IMMUTABLE-ANALYZER-ASSOCIATIONS.md`
- `PHASE-12F-CURRENT-BASELINE-SWAP.md`
- `src/main/java/com/daviddunn/retirementplanner/app/baseline/BaselineDetailsSession.java`
- `src/main/java/com/daviddunn/retirementplanner/app/baseline/BaselineParameterComparison.java`
- `src/main/java/com/daviddunn/retirementplanner/app/baseline/ProjectionInputs.java`
- `src/main/java/com/daviddunn/retirementplanner/app/projection/AnalyzerAdmission.java`
- `src/main/java/com/daviddunn/retirementplanner/app/projection/AnalyzerAssociation.java`
- `src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java`
- `src/main/java/com/daviddunn/retirementplanner/app/projection/ProjectionRoleManifest.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/IndividualDeterministicSnapshot.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/QuickComparisonSnapshot.java`
- `src/main/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotComponents.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisPdfData.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ClaimingAgeGridView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/HistoricalAnalyzerPresentation.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ProjectionAnalyzerHistoryDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/QuickComparisonReportAdapter.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/QuickReportSession.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/QuickSnapshotAdapter.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationGuard.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityClaimingGridAdapter.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityOnlyReportAdapter.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityReportSession.java`
- `src/main/resources/css/baseline-details.css`
- `src/test/java/com/daviddunn/retirementplanner/app/baseline/BaselineDetailsTest.java`
- `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/Phase12EHistoryFixtures.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/AnalyzerAssociationStorageTest.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotPersistenceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSwapTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/baseline/ProjectionSwapViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloProjectionAssociationTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12EAssociationTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12ECodecTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12EIndividualLifecycleTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12FAnalyzerSwapTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SharedClaimingAgeGridTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAndQuickPdfTest.java`
