# Phase 12G — Application-wide analyzer freshness and restoration audit

## 1. Executive summary

Audit only, 2026-10-27. No production, test, CSS, schema, fingerprint or financial changes were made. The only new deliverable is this report. Existing Phase 11–12 work is preserved.

The immutable projection/association infrastructure can recover and display recorded analyses without calculation. It does not currently select the appropriate pinned result into an ordinary analyzer's last-success session after a swap. That separation is intentional and must remain explicit. A warning about the last-success analysis for A while B is Current is often correct even when B has a valid result in Analyzer History.

Verified inconsistencies are narrower than a general UUID-based freshness fix:

1. Fresh Quick Comparison keeps a label-based stale state after input invalidation; its persistence assessment can return Current without clearing that label. Restored Quick has a clearing callback that fresh Quick does not use. Its PDF currency also reads that label.
2. Deterministic/Weighted cross-analysis joins require session revisions and a separate weighted-current flag, even when restored payload compatibility is established. Valid historical results can therefore remain unavailable for a cross-analysis join. Removing those checks without replacing their complete provenance proof would be unsafe.
3. Several views temporarily describe an unassessed result as Outdated because `current()` is false during asynchronous assessment. Pending assessment is not proof of a mismatch.
4. Ordinary compatibility is financial/request compatibility, not exact projection-association identity. It can report Current for equivalent inputs belonging to different projection UUIDs. This does not fabricate a pin, but is weaker than the proposed projection-specific CURRENT definition.

There is no evidence here of altered financial results, corrupt snapshots or a need to rerun a verified historical analysis. No specific user screenshot/native warning was reproduced in this audit. Findings below distinguish source-proven branches, existing automated coverage and proposed tests. Phase 12F's report records native acceptance pending; no additional native acceptance is claimed by this audit.

## 2. Baseline, scope and evidence

Branch: `main`. HEAD: `e620bec4a0011ab12d15fce670bff5011093d9da`. This remains the accumulated implementation baseline, not a new Phase 12G commit. Index empty. Before this report: 39 tracked modified files and 49 expanded untracked files. Afterwards: the same tracked changes and 50 untracked files. Expanded inventory is appended below.

Read AGENTS.md, Phase 12B/12C/12D/12E/12F reports and relevant Phase 10D, Phase 11C, and Monte Carlo numeric-compatibility material. Actual controller, dialog, session, fingerprint, association, history and report code governs this audit. Earlier statements that Quick/individual modes are memory-only are superseded by Phase 12E Option 2. The old AGENTS shallow-baseline caveat does not describe the complete immutable component introduced in 12D.

Analysis menu entry points in `MainWindow.createMenuBar`: Social Security Strategy Analyzer, Monte Carlo Retirement Analysis, Monte Carlo Strategy Comparison. Results Summary exposes Break-Even and baseline details/history; ordinary chart/dashboard/annual views consume accepted standard projection results. Social Security navigation dispatches couple or individual implementations according to household shape.

## 3. Analyzer inventory and freshness matrix

All 15 `AnalysisKind` slots have durable payloads and can have explicit projection associations. A slot is replaceable last success; an association names an immutable artifact. Neither implies the other.

| Analyzer | Current freshness mechanism | Swap behavior | False stale risk | Recommended correction |
|---|---|---|---|---|
| COUPLE_SOCIAL_SECURITY | `WeightedResultPersistence.reassess`; S/M/V input hash, context and versions; `refreshSocialSecurityResultStatus` controls prerequisite | Source listener invalidates execution, restores accepted controls, reassesses last-success slot; pin stays on original UUID | Temporary warning during assessment; settings for another accepted mode can legitimately differ | Explicit assessment-pending state; offer associated result separately; retain SS prerequisite proof |
| COUPLE_QUICK | F/S + candidate count, SS prerequisite hash and ordered candidate ranks/values; persistence plus `integratedStale` label | Last-success comparison remains independent; old report binding loses ownership on plan-object replacement until safely restored | **Verified fresh-result sticky label**, PDF label-derived currency; unavailable SS prerequisite is not corrupt historical data | One typed assessment for warning/PDF; separate historical export from new-run prerequisite |
| COUPLE_EXHAUSTIVE | F/S, context/algorithm/reference versions; persistence; session revision for cross-analysis joins | Last slot reassessed; role-associated result available through History | Transient source-change warning; revision-only cross-join refusal despite restored compatibility | Keep execution invalidation; replace accepted-result join revision proof only with validated source/request provenance |
| COUPLE_WEIGHTED | F/S/M/V plus frozen optional current-strategy election sections B; context horizon/death assumptions; separate weighted-current/revisions | Reassesses last slot under restored mode controls; original pin unchanged | Cross-join revision gate; shared controls legitimately select another configuration | Explicit per-result assessment; compare frozen optional baseline and exact universe before any cross-join |
| INDIVIDUAL_DETERMINISTIC | F/S + optional death year; `SinglePersonIntegratedView` fingerprint and generic persistence | Rebind/refresh; nine-strategy payload remains pinned to original projection | Interim local current flag versus persistence; not proven permanent false warning | Use persistence assessment for all accepted-result labels; keep job generation separate |
| INDIVIDUAL_SOCIAL_SECURITY | S/M/V, displayed-inflation context; individual persistence | Primary-only controls restored; original result remains available | Context-only change can conservatively say Outdated; this is existing contract | Explain context mismatch separately; no automatic contract change |
| INDIVIDUAL_WEIGHTED | F/S/M/V; configured horizon/death context; individual persistence | Independent settings and historical payload retained | Context-only mismatch and assessment-pending labels | Distinguish financial/request mismatch from reference-context mismatch without changing hashes |
| COUPLE_MONTE_CARLO_FIXED | MC V1 verification then V2 compatibility, plan A/settings/horizon | Session invalidated; last slot reloaded/reassessed, pins follow UUID | Transient stale while assessing; equivalent financial inputs do not prove same UUID | Typed pending state and independent projection-association status |
| INDIVIDUAL_MONTE_CARLO_FIXED | Same, primary-only request | Same | Same | Same |
| COUPLE_MONTE_CARLO_LONGEVITY | MC V1/V2, plan A/settings/mortality and context | Same, independent longevity slot | Actual restored mortality/date/election differences remain valid mismatches | Preserve exact effective mortality request and context checks |
| INDIVIDUAL_MONTE_CARLO_LONGEVITY | Same, individual lifetime model | Same | Same | Same, no spouse requirements |
| COUPLE_MONTE_CARLO_PAIRED_FIXED | Ordered A/B plan documents, shared scenario settings/seed/count, effective horizon | Swapping changes both comparison operands; old matched result is historical | Broad STALE wording conflates valid historical pair with invalid data | Explain ordered comparison mismatch; never reverse paired data by relabeling |
| INDIVIDUAL_MONTE_CARLO_PAIRED_FIXED | Same, individual shape | Same | Same | Same |
| COUPLE_MONTE_CARLO_PAIRED_LONGEVITY | Ordered A/B, mortality/world/settings/context; V1/V2 | Same; old lifetime pairing remains intact | Pending status; real baseline/election changes must remain Outdated | Preserve market/inflation/lifetime pairing and original request |
| INDIVIDUAL_MONTE_CARLO_PAIRED_LONGEVITY | Same, individual shape | Same | Same | Same |
| Break-Even | Live ordered current/baseline result identities and baseline object; shared years | Detaches directional cache; explicit request required | Conservative loss after restart is correct: no durable pair artifact | Optional separate pair-result persistence decision; never negate/relabel reversed result |
| Standard projection, summaries, 13 chart metrics | `CompleteProjectionSnapshot`, exact configured input digest, accepted generation | Restores original results and editable input roots; no engine | Dirty versus stale confusion if consumers use dirty flag | Keep `currentProjectionInputsMatch` and accepted identity authoritative |
| Baseline Details and parameter comparison | Frozen accepted inputs/results and role references | Follows baseline UUID; chart uses original saved results | Legacy missing data is unavailable, not stale | Preserve explicit Not recorded / original-results unavailable states |
| Deterministic/weighted comparison panel and SS cross-reference | Exact strategy elections/universe plus presentation revisions/current flags | Revisions change even when UUID/data are retained | **Verified conservative revision restriction**, not financial corruption | Provenance-based join proof requires approved bounded follow-up |

Other discovered calculation paths: headless retirement-age grid, complete-strategy evaluator/comparison, monthly SS comparisons and SS break-even calculator, weighted scenario evaluation, MC accumulators, income/projection statistics and Break-Even insight/context. These are subordinate services or test/console entry points, not extra independently persisted application analyzer slots. Their results inherit caller ownership. Dashboard, annual account/tax/income tables, standard CSV/PDF and analyzer PDF exports are presentations of those results, not new run identities. No independent freshness contract should be invented for each chart.

## 4. Exact implementation paths and user messages

### Couple analyzers

`SocialSecurityStrategyAnalyzerDialog` source listener (around lines 249–272) replaces its plan reference, restores parameter bindings when the plan object changes, increments `planRevision`, calls `jobs.invalidate(PLAN)`, then `markStale()`. It sets `"Plan changed - run exhaustive search again."` for an existing Exhaustive presentation. A swap preserves owner UUID but replaces the plan object, so it takes this path.

`markStale` (around 1699) invalidates assumptions, clears local SS/weighted-current booleans, shows `"Inputs changed - run Social Security analysis again."`, and marks Quick `"Social Security inputs changed - rerun Social Security analysis and Quick Comparison."`. It then reassesses persistent SS/Weighted inputs. `baselineChanged` invalidates only WEIGHTED_SETTINGS and reassesses its optional baseline fingerprint. Execution invalidation here is correct and must remain.

`WeightedResultPersistence.binding/load/reassess/sourceChanged` (roughly lines 69–228) binds UUID/kind/path, reconstructs off FX, rejects old load generations, and applies `AnalysisCompatibilityPolicy` against effective current controls. Same-owner/path source notifications do not themselves require a new historical decode: `load()` can reassess an attached snapshot. `current()` only means the compatibility assessment is CURRENT; it does not test projection UUID. `show()` uses `Current Results`, `Previous Results — Outdated`, `Inputs Need Attention`, `Incompatible or Unavailable`, with separate durability/retry information.

SS and Exhaustive status callbacks replace provisional warnings with the persistence assessment. Weighted's callback clears its stale label when compatible, but does not itself update every cross-analysis revision/boolean. The source listener can set an Exhaustive provisional warning after its earlier status callbacks; subsequent scheduled reassessment clears it. This is an ordering/transient-warning risk, not a demonstrated permanent financial mismatch.

**Quick source-proven sticky state:** its changed callback (around lines 402–404) synchronizes `integratedStale` only when `quickWasRestored && quickReport != null`. A fresh accepted result sets `quickWasRestored = false` and clears the label once. `markIntegratedStale` later writes the warning. Reverting inputs can make `quickPersistence.current()` true, but the callback's guard prevents clearing the fresh-result label. `prepareAdditionalPdf(false)` (around 1868) derives currency from `integratedStale.getText().isBlank()`, so the same divergence can affect the PDF status notice. Frozen financial PDF data remain original. This is a concrete source-level failure path; no new native or UI reproducer was executed here.

`quickInputs()` rejects a missing/non-current SS presentation, then fingerprints candidate count, compatible SS prerequisite hash and selected complete candidate identities/ranks/expected values. That is necessary for a new Quick run. It is not necessary to declare an already verified frozen historical comparison financially corrupt or unavailable for historical export.

`IntegratedAnalysisComparisonPresentation.compatible` explicitly requires `weighted.planRevision() == currentPlanRevision`, `deterministicRevision == currentPlanRevision`, weighted-current, identical universe counts and full election multiset equality. `refreshWeightedComparison` passes those revision/current values when constructing a live join. For restored Weighted results it instead preserves the frozen historical comparison rows and deliberately clears the live deterministic reference. Immutable restored presentations can carry zero or older session revisions. Thus fingerprint-compatible results do not automatically qualify for a new live join; original frozen historical comparisons remain available. The correction must retain universe/election/source-context proof, not simply remove revisions or compare displayed ranks.

### Individual analyzers

`SinglePersonIntegratedView.assessCompletedInputs` recomputes the deterministic input hash with optional death override. `SinglePersonMortalityView.assessCompletedInputs/edited/invalidate/refreshSavedState` handles mortality requests. Changing the plan object clears its local accepted hash; generic persistence subsequently reassesses the saved result. `edited()` first writes `"Inputs changed - displayed results are stale. Run analysis again."`; `refreshSavedState` can replace this with `"Completed results match current inputs."` or historical wording. These are not proven permanently sticky in the same way as fresh Quick.

`IndividualCompletionPersistence.attach` captures the immutable prepared request/hash and association admission, then accepts only through the job-controller success branch and `AnalysisSuccessPolicy`. Individual SS and Weighted have independent primary-only controls/configurations. Historical context comes from the payload and `SnapshotContext`, not current controls.

### Monte Carlo, all eight identities

`MonteCarloSession` and `MonteCarloStrategyComparisonSession.finish` enforce generation, cancellation and captured revision before accepted publication. `invalidate()` increments revision, marks a displayed result stale and cancels work. Passive `restore(result, true)` deliberately starts conservatively; it is not a new accepted run.

`MonteCarloResultLifecycle.bind/unbind/load/assess/refreshAssessment` separately checks owner/path/kind, load generation, assessment edit generation and original snapshot identity. Reconstruction and compatibility run on the store worker. `MonteCarloAnalysisView.refresh` warns only when `session.stale() && !persistence.current()`; paired view does the same with `"STALE — inputs, Current Plan or Saved Baseline changed. Run again."`. Therefore a revision change alone does not require a lasting warning after successful matching reassessment. While assessment is pending, `current()` is false, so an interim warning can appear. Do not diagnose the old -2/-2.00 bug as still present: V2 explicitly fixes it and its tests pass.

Source listeners unbind when the plan object changes (including swap), invalidate in-flight sessions, restore accepted parameters and rebind the last-success slot. They do not select a projection-associated artifact. `report()` uses frozen payload/context and explicit currency/durability; no simulation occurs during restoration/export.

## 5. Fingerprint and identity contracts

`AnalysisFingerprintService.VERSION = 1` remains scale-sensitive. S includes participant birth dates, SS records/elections/benefit valuation and COLA. Mortality families add M (factors/categories/conditioning) and V (valuation/discount). Integrated families add F (allowlisted financial plan roots, excluding plan UUID, baseline, analyzerConfigurations and projectionState; display names removed). Weighted financial F excludes scenario-overridden timing/horizon fields, but `effectiveContextFingerprint` separately preserves configured horizon/death context. Couple Weighted also hashes optional baseline election/source sections. Individual SS context includes displayed general inflation. Quick adds prerequisite and ordered candidate data; individual deterministic adds optional death year.

Consequently some Outdated assessments concern reference context, not changed financial outcomes. That is an existing intentional contract, not authorization to change fingerprints. Search-profile settings are preserved in typed requests/configurations/payloads but not every profile is an independently editable/hash field here. Any new applicability resolver must validate supported profiles and exact relevant effective settings; it must not assume parameter configuration equality alone is sufficient or change old bytes to add fields.

`MonteCarloFingerprintService.compatibility` verifies original stored V1 bytes against frozen payload first; only then compares frozen/current effective inputs under TEMPORARY_NUMERIC_V2. Exact decimal normalization is allowlisted to temporary return/inflation/mortality values, not financial plan/baseline values. Ordered plan A/B, settings, mortality, end/context and supplied world fingerprint participate. Trial count, seed and matched scenarios cannot be ignored. Equal financial inputs from separately accepted projections remain distinct projection identities.

`CompleteProjectionSnapshot.matches` uses a different, exact five-root input-document SHA contract. It cannot be substituted for the analyzer-specific hash: SS can ignore some financial fields while weighted/paired analysis has additional parameters and contexts. Dirty state is neither of these identities.

Necessary proof for proposed projection-specific CURRENT:

1. Verified owner/path/household/kind/schema/algorithm/reference versions and payload/component digests.
2. Recorded association names the active accepted projection UUID and its exact input digest; no inference from an equal financial hash.
3. Configured inputs still match the accepted current projection (unaccepted edits block CURRENT under this definition).
4. Analyzer-specific effective request and context match the selected controls/configuration, using the existing versioned compatibility contract.
5. Result being assessed is the artifact named by that association, not another last-success result of the same kind.

A valid legacy last-success result can still be financially compatible without a recorded UUID association. Preserve that capability with an explicit association-unknown dimension; do not silently manufacture a pin or discard the result. User approval is needed before replacing today's meaning of Current with the stronger definition.

## 6. Recorded association versus execution authorization

`ApplicationController.prepareAnalyzerAssociation` captures plan UUID, exact current projection UUID/input digest, source revision, projection generation, kind, request hash and a fresh ticket. `associateAnalyzer` checks all of them plus latest admission, completion run/kind/owner, financial freshness and request hash. Swap increments generation/revision and clears admissions, rejecting late completions. `SocialSecurityAnalyzerJobController.owns/eligible/finish` rechecks job ownership/revision/cancellation even after state observers run. MC sessions enforce their corresponding generation checks. Keep all these guards unchanged.

`AnalyzerAssociation`, `ProjectionRoleManifest` v2 and `ProjectionSnapshotComponents` give durable, bounded reference identity. `loadAssociatedAnalyzer` validates membership, projection owner/UUID/input digest, household shape and original artifact. Repository resolution validates immutable component and payload integrity. Maxima remain 30 associations, 30 pinned projections, existing per-component limits and shared owner budget. No garbage collection or payload duplication is proposed.

`ProjectionAnalyzerHistoryDialog` captures plan object/UUID, selected projection and association, load generation and export ownership. It closes on replaced ownership. `load()` verifies saved algorithm/reference context against the original saved hashes, not editable controls; that internal CURRENT assessment means original semantic readability, not live financial applicability. Its visible message correctly says `"Original saved analyzer result"` and `"Original assumptions are shown. Editable inputs and last-success slots do not change this association."` Missing pins say `"Analyzer result not captured for this projection..."`; failed resolution says original unavailable/incompatible while preserving the reference. History does not replace ordinary sessions, accepted configurations or last-success slots.

## 7. A/B swap, restart and Save As trace

| Step | Existing behavior | Interpretation |
|---|---|---|
| A Current, B Baseline; accepted analyses recorded | Associations refer to original UUIDs; slots contain each kind's latest success, not necessarily A or B | Correct distinct stores |
| Swap to B | Exact snapshots and editable B inputs installed; admissions/revisions invalidated, original association references unchanged | In-flight invalidation required; accepted artifact integrity unaffected |
| Open ordinary analyzer | Restores controls from last-success parameter configuration; loads last-success kind slot and reassesses | May correctly show A's result Outdated, even when B has an applicable pin |
| Open History for B or A | Explicit selected role/UUID artifact; read-only original assumptions/rankings/charts/PDF | No recalculation; no current-session promotion |
| Change analyzer settings | Ordinary applicability reassessed; pin remains valid for original request | Historical validity and current applicability differ |
| Swap back to A | Original UUID/results restored; matching last-success inputs can reassess Current | Quick sticky label/cross-join revisions remain exceptions above |
| Save/restart | Manifest pins restore; last-success slots and accepted configs separately restore; no legacy association inference | Save does not bless stale inputs; no automatic best-pin selection |
| Save As | New owner and binding, copied independent components and original result identities/provenance; slot copying separate | Revalidate copied ownership and requests; original plan unchanged |

Ordinary Save and configuration-only saves do not themselves prove freshness. An accepted parameter configuration records the last accepted effective request, not a result's durable integrity, projection pin, current draft or complete financial input context. Reopening shared couple controls for another mode can legitimately change the request being assessed. Restoring the actual original controls may recover compatibility without recalculation, provided all other dependencies also match.

## 8. Break-Even exception

`ApplicationController.getCachedBreakEvenAnalysis`, `computeBreakEvenAnalysis`, `requestBreakEvenAnalysis` use actual current/baseline annual graphs, frozen assumptions, non-investable results and baseline identity. Post-swap/restoration `breakEvenExplicitOnly` prevents automatic execution. The live cache returns a result only when the exact ordered result objects/baseline identity match. Swap detaches those keys; frozen observations for an already-open original report remain separate.

`BreakEvenAnalyzer.analyze` intersects annual year keys; Results Summary retains each projection's full original horizon and ending year. Those financial definitions must not be unified. Role reversal requires a newly requested directional calculation; it is not accomplished by changing labels, sign or UUID order on the old result. Currently no persisted general current/baseline pair-result artifact exists. Individual deterministic candidate payloads containing original candidate Break-Even data are not substitutes for this pair result.

Future reusable cache identity would require ordered current/baseline UUIDs AND verified component/input identities, compatible frozen annual/non-investable/assumption context, shared-year range and analyzer/reference version. Optional mortality/insight/PDF context needs its own identity. Retaining an already computed exact ordered-pair result in bounded memory is feasible; cross-restart recovery requires a separately approved persisted artifact/schema/budget design. Swapping must never automatically calculate it.

## 9. Recommended common presentation model (proposal only)

Use a small pure assessment service over existing verified records, not a new analyzer framework. Return reason categories and separate dimensions:

- Artifact: readable/verified, missing, corrupt, unsupported.
- Association: exact selected projection, different recorded projection, legacy/unknown.
- Applicability: matching, mismatching, invalid pending inputs, assessment pending; retain context-only explanation.
- Execution: idle/running/canceling/failed/canceled/superseded, independently.
- Durability: pending/saved/save failed/memory only, independently.

User-facing CURRENT means proven applicability under the approved definition; HISTORICAL means valid original result viewed in its recorded context; STALE means not applicable to the requested live calculation; UNAVAILABLE means cannot safely read/render. A valid historical result may also fail live applicability: do not force integrity and applicability into one exclusive boolean. Keep `Inputs Need Attention` and `Assessing` explicit rather than falsely reporting Outdated. Show mismatch categories, never raw financial data or personal values in diagnostics.

Suggested text: `Current — matches this projection and analysis settings`; `Historical — original saved result for projection …`; `Not current — analysis settings changed`; `Not current — configured financial inputs differ from the accepted projection`; `Association not recorded — effective inputs match`; `Assessing saved result`; `Unavailable — saved artifact could not be verified`. Preserve exact original PDF assumptions/timestamps. History exports should retain neutral historical identity, not relabel as an active calculation merely because its role becomes Current.

Offer an explicit action to view the selected projection's saved analysis if one exists. Do not automatically replace the current analyzer session, overwrite drafts, rewrite last-success slots/configurations or mark results Current solely on navigation.

## 10. Bounded implementation stages and approval gates

1. **Approve terminology and applicability policy.** Decide whether to retain today's financially compatible Current for unassociated legacy slots, or require exact UUID for the label and add a compatible/unassociated state. Decide explicit View Associated Result UX; no automatic session promotion recommended.
2. **Localized UI correction.** Add failing regressions for fresh Quick change/revert, then source all warning/PDF currency from the same typed assessment. Add assessment-pending presentation; preserve prerequisite admission and export safety. No hash/schema change required.
3. **Pure association/applicability resolver.** Reuse validated artifacts and existing fingerprint dispatch. Add structured reasons; preserve all async execution gates. Do not infer pins for equal fingerprints/different UUIDs.
4. **Optional cross-analysis join correction.** Replace revision-only accepted-result restrictions only with complete source projection/request/universe/election/context proof. This is a separate approval gate because it changes when joins are shown. Do not loosen ranking or calculation semantics.
5. **Optional directional Break-Even reuse.** Separate proposal for bounded ordered-pair caching/persistence; no implied authorization here. Native acceptance after any user-visible corrections.

No new dependencies, global decimal normalization, financial fingerprint rewrite, snapshot migration or calculation changes are recommended. Any discovery requiring them must stop for approval.

## 11. Proposed regression and native acceptance

Automated follow-up tests should parameterize all 15 identities and both household shapes:

1. Exact UUID, financial digest and effective request match; then changed request; then exact revert.
2. Equal financial hash with different accepted UUID: compatible data but no invented historical association.
3. Unaccepted financial edits with same accepted UUID: never blindly Current; dirty-only nonfinancial change must not falsely invalidate financial applicability.
4. A/B swap and double swap, with distinct pins and last-success slots; role references/financial values unchanged; no engine calls.
5. Save/restart and Save As ownership; parameters independent from pins; legacy unknown association remains explicit.
6. Wrong owner/projection/hash/kind/version, missing/corrupt artifacts and failed load: unavailable, references preserved, no fallback calculation.
7. Controlled in-flight completion after swap/plan switch: rejected; separately accepted old immutable result can reassess when its original projection/settings return.
8. Fresh Quick change/revert must clear both UI and PDF warning; new-run prerequisite remains enforced; new SS result never rewrites frozen prior Quick provenance.
9. Pending MC assessment does not flash a definitive mismatch; V1 integrity and V2 numeric compatibility retain -2/-2.00 equality while financial plan/baseline scales remain significant.
10. Weighted optional baseline ABSENT/NULL/VALUE/UNAVAILABLE, shared-control mode changes, context-only horizon changes; exact strategies/universe proof for joins, no approximate matching.
11. History selection does not alter current session, parameters, dirty flag or snapshots; original PDF values/names/timestamps remain exact.
12. Break-Even ordered A/B result not valid for B/A; explicit post-swap request uses established shared-year calculation, Results Summary remains full-horizon; double swap restores exact financial state without execution.

Native checklist: accept distinct A/B projections and distinctive analyzer settings; record which kind's last-success slot is shown; save pins; swap and inspect ordinary dialogs versus explicit History; change/revert settings; swap back; save/restart; Save As; repeat individual/couple and MC paired/unpaired. Confirm original chart values/PDF assumptions/timestamps, no automatic calculations, no cross-plan callbacks, and no pending-assessment warning mistaken for a proven mismatch. Native Windows warning reproduction and UI correction acceptance remain future work.

## 12. Verification actually executed

Existing tests only; no added or modified tests. IntelliJ bundled Maven, project-local `.codex-m2/repository`, `-DargLine=-Xmx4g`:

`-Dtest=Phase12FAnalyzerSwapTest,ProjectionSwapTest,MonteCarloSessionTest,MonteCarloComparisonSessionTest,MonteCarloNumericCompatibilityTest,MonteCarloResultLifecycleTest,AnalysisFingerprintTest,BreakEvenControllerTest,Phase12EAssociationTest,Phase12EIndividualLifecycleTest,MonteCarloProjectionAssociationTest test`

**207 tests, 207 passed, 0 failures, 0 errors, 0 skipped. BUILD SUCCESS. 48.305 seconds; finished 2026-10-27 14:59:28 -04:00.** Log: ignored `target/phase12g-focused.log`. JavaFX emitted its existing unnamed-module configuration warning; tests passed. These substantiate fingerprints, sessions, all-kind association swap/restart/clone and directional cache behavior. They do not constitute a newly added Quick sticky-label or every-dialog native warning reproducer.

Full suite not rerun in this audit-only phase; no code changed. Phase 12F report's prior full-suite evidence is 3,549 total / 3,505 passed / 44 skipped / zero failures/errors in 10:30; this is reused background evidence, not a Phase 12G run. No visual previews or native Windows tests executed for this audit. `git diff --check` passed; Git emitted line-ending conversion notices for existing files, not whitespace errors. Index remains empty. No Git mutation commands executed.

## 13. Risks and conclusion

The largest risk is fixing an apparent stale warning by weakening execution guards or treating a matching UUID as sufficient for changed analyzer settings. The second is silently substituting a pinned result into an ordinary session, overwriting user intent. Legacy association ambiguity, context-only fingerprints, Quick prerequisites and paired/directional identities require explicit handling.

Source-proven UI-state divergence warrants a bounded correction, subject to approval. Most post-swap last-success warnings cannot be classified as false without identifying the displayed artifact, projection UUID and effective request. Existing immutable history can already be viewed without recalculation. Approve the proposed state/legacy policy and localized follow-up scope before implementation. This phase stops at audit and documentation.

## 14. Exact working-tree inventory

The following lists include all accumulated pre-existing work; inclusion here does not claim it was created or changed by Phase 12G. Only this audit report was added in this phase. Ignored Maven outputs are excluded from the Git inventory.

Tracked modifications (39):

```text
src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerPdfExporter.java
src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerReport.java
src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloFingerprintService.java
src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPlanDocument.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/RetirementPlanScenarioCopyService.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AcceptedAnalysisCompletion.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisFingerprintService.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ExhaustiveSnapshot.java
src/main/java/com/daviddunn/retirementplanner/domain/baseline/ProjectionBaseline.java
src/main/java/com/daviddunn/retirementplanner/domain/model/RetirementPlan.java
src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java
src/main/java/com/daviddunn/retirementplanner/persistence/JsonRetirementPlanRepository.java
src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java
src/main/java/com/daviddunn/retirementplanner/ui/charts/ProjectionChartView.java
src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloParameterControls.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerNavigationView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ClaimingStrategyHeatMapView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveResultSnapshots.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualCompletionPersistence.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IntegratedAnalysisResultTabs.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/LongevityWeightedIntegratedView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonMortalityView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityParameterBinding.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedResultPersistence.java
src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java
src/test/java/com/daviddunn/retirementplanner/ui/controller/ApplicationControllerTest.java
src/test/java/com/daviddunn/retirementplanner/ui/controller/BreakEvenControllerTest.java
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloComparisonViewTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/CoupleSocialSecurityPersistenceTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedRestorationTest.java
src/test/java/com/daviddunn/retirementplanner/ui/views/SinglePersonHouseholdUiTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/CompleteWizardEndToEndTest.java
src/test/java/com/daviddunn/retirementplanner/ui/wizard/StateTaxWizardIntegrationTest.java
```

Untracked files (50), including this report:

```text
PHASE-11A-SOCIAL-SECURITY-UI-PDF-AUDIT.md
PHASE-11B-SHARED-GRID-AND-NAVIGATION.md
PHASE-11C-SOCIAL-SECURITY-AND-QUICK-PDF-EXPORT.md
PHASE-11D-FINAL-INTEGRATION-AND-REGRESSION-AUDIT.md
PHASE-12A-BASELINE-DETAILS-ARCHITECTURE-AUDIT.md
PHASE-12B-BASELINE-DETAILS-IMPLEMENTATION.md
PHASE-12C-PROJECTION-SNAPSHOT-AND-SWAP-ARCHITECTURE-AUDIT.md
PHASE-12D-COMPLETE-PROJECTION-SNAPSHOT-PERSISTENCE.md
PHASE-12E-IMMUTABLE-ANALYZER-ASSOCIATIONS.md
PHASE-12F-CURRENT-BASELINE-SWAP.md
PHASE-12G-ANALYZER-FRESHNESS-AUDIT.md
src/main/java/com/daviddunn/retirementplanner/app/baseline/BaselineDetailsSession.java
src/main/java/com/daviddunn/retirementplanner/app/baseline/BaselineParameterComparison.java
src/main/java/com/daviddunn/retirementplanner/app/baseline/ProjectionInputs.java
src/main/java/com/daviddunn/retirementplanner/app/projection/AnalyzerAdmission.java
src/main/java/com/daviddunn/retirementplanner/app/projection/AnalyzerAssociation.java
src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java
src/main/java/com/daviddunn/retirementplanner/app/projection/ProjectionRoleManifest.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/IndividualDeterministicSnapshot.java
src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/QuickComparisonSnapshot.java
src/main/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotComponents.java
src/main/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsDialog.java
src/main/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisPdfData.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ClaimingAgeGridView.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/HistoricalAnalyzerPresentation.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ProjectionAnalyzerHistoryDialog.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/QuickComparisonReportAdapter.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/QuickReportSession.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/QuickSnapshotAdapter.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationGuard.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityClaimingGridAdapter.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityOnlyReportAdapter.java
src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityReportSession.java
src/main/resources/css/baseline-details.css
src/test/java/com/daviddunn/retirementplanner/app/baseline/BaselineDetailsTest.java
src/test/java/com/daviddunn/retirementplanner/app/montecarlo/Phase12EHistoryFixtures.java
src/test/java/com/daviddunn/retirementplanner/persistence/AnalyzerAssociationStorageTest.java
src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotPersistenceTest.java
src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSwapTest.java
src/test/java/com/daviddunn/retirementplanner/ui/baseline/BaselineDetailsViewTest.java
src/test/java/com/daviddunn/retirementplanner/ui/baseline/ProjectionSwapViewTest.java
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloProjectionAssociationTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12EAssociationTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12ECodecTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12EIndividualLifecycleTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12FAnalyzerSwapTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SharedClaimingAgeGridTest.java
src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAndQuickPdfTest.java
```

Staged paths: 0.
