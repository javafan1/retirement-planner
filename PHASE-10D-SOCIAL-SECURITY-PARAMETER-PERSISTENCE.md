# Phase 10D — Social Security accepted parameter persistence

## Scope, baseline and status

Integrated the existing plan-owned parameter infrastructure into the seven Social Security analysis identities. Configurations live in the existing optional `analyzerConfigurations` section, independently of historical result snapshots. No new settings file, result mode, Quick result persistence or individual result persistence was added.

Baseline: branch `main`, HEAD `2e959d9770bd834cd4ca1bed3f50df1219607c65`. The working tree already contained the accepted Phase 10A/10B/10C work. Those files were preserved; the Phase 10D inventory below distinguishes new changes from that baseline. User-confirmed Phase 10C native acceptance is the starting point, not a claim of native 10D verification.

Pre-edit targeted baseline: **58 passed, zero failures/errors/skips**, 23.309 seconds (`target/phase10d-baseline.log`). AGENTS.md, Phase 10A–10C reports, the relevant Phase 9 and single-person implementation reports, and current request/controller/presentation sources were reviewed. Source code governs this implementation.

## Seven identities and exact prepared-request mapping

| Identity | Authoritative prepared source | Saved typed configuration |
|---|---|---|
| COUPLE_SOCIAL_SECURITY | `SocialSecurityStrategyAnalysisContext` and its immutable optimization request | Primary/spouse factors, conditioning date, PV date/rate; `couple-ss-v1:top=N` records the actual retained retirement-cell setting |
| COUPLE_QUICK | `SocialSecurityAnalyzerInputs.Quick`, selected candidates and the current SS prerequisite | Candidate count, SS prerequisite fingerprint; bounded provenance contains the exact existing Quick input/candidate-basis fingerprint |
| COUPLE_EXHAUSTIVE | `IntegratedSocialSecurityCompleteStrategySearchRequest` | `couple-full-v1`, original ranking enum, retained detail count; no mortality overrides |
| COUPLE_WEIGHTED | `LongevityWeightedAnalysisRequestFactory.Snapshot` and baseline captured at preparation | Primary/spouse factors, conditioning/PV dates and exact rate, `couple-weighted-full-v1`, optional baseline states/text/source |
| INDIVIDUAL_DETERMINISTIC | Prepared nine-age integrated search request | `individual-nine-v1`, ranking enum, detail count and optional primary death year |
| INDIVIDUAL_SOCIAL_SECURITY | `SinglePersonMortalityView.CompletionInput` | Primary-only factor, conditioning/PV dates/rate and `individual-ss-nine-v1` |
| INDIVIDUAL_WEIGHTED | Its independent `CompletionInput` | Primary-only factor, conditioning/PV dates/rate and `individual-weighted-nine-v1` |

`SocialSecurityParameterMapping` captures small immutable values before work starts. No completion handler reads editable control text to construct accepted settings. It stores no plan financial copy, mortality table, trial history, projection, result graph or PDF. Exact BigDecimal representations remain in the typed schema. Percent controls use exact decimal `scaleByPowerOfTen(2).toString()`; individual factors use `toString()` so exponent/negative-scale representations are not flattened. Individual factor validation remains positive-only, with no newly imposed couple factor bounds. A production nine-strategy test accepts factor `0.2500`.

Stored noneditable search settings are honored when preparing the next request: SS top-cell retention, exhaustive ranking/detail retention, and individual deterministic ranking/detail retention. Recognized fixed profiles represent the existing service universe; they do not add controls or alter default algorithms. An unsupported profile is preserved and explicitly described as unavailable while default next-run controls are shown, rather than silently interpreted as a known profile.

## Acceptance, save ordering and status

`SocialSecurityParameterBinding` captures the original coordinator binding, per-run acceptance sequence, UI edit epoch, run UUID and immutable parameters. Only the existing eligible success branch submits `AcceptedAnalysisParameters`. The coordinator retains its final binding/sequence checks. All seven modes use the existing `AnalysisSuccessPolicy`; partial candidate success is accepted, and an all-unsuccessful replacement cannot replace accepted settings. Cancellation, fatal errors, rejected admission, closed views and obsolete revisions cannot accept settings.

The existing `AnalysisParameterCoordinator` performs bounded asynchronous configuration-only writes, using the established durable UUID/path guards, exact financial-token preservation, shared Save coordination, atomic writing and recovery. The analyzer does not invoke ordinary Save automatically. Accepted metadata does not mark financial edits clean, increment financial revisions, invalidate projection caches or run financial work. Historical result storage has its own independent commit/status; parameter durability does not pretend the result snapshot commit succeeded, or vice versa.

Every workspace gets a wrapped, accessible parameter status and **Retry Parameters Save** action. Existing PENDING, SAVED, SAVE FAILED and MEMORY ONLY states are reused. Retry submits the immutable accepted configuration through the coordinator and does not rerun analysis. Never-saved and unmigrated legacy plans retain the established Memory Only policy. Ordinary Save and Save As can explicitly persist accepted memory entries. Unsupported future configuration metadata remains protected by Phase 10B.

## Shared couple controls and individual independence

The original couple mortality/valuation nodes are retained. Per-kind bindings restore the selected SS or Weighted accepted configuration into those same nodes. With no accepted entry, the established opening defaults are used. Quick restores only its count and Exhaustive only its noneditable service profile; neither applies mortality or valuation controls. Unaccepted drafts are not treated as accepted settings and are discarded when the relevant kind is initialized/restored.

Restoration suppresses original input listeners, followed by valid longevity-session propagation and normal input-summary/readiness/currency assessment. It does not synthesize a financial revision or SS completion. Programmatic date restoration replaces editor text even when the date value itself is unchanged, preventing an old invalid editor draft from surviving rollback. The individual weighted persistence editor listeners honor the same narrow guard.

Individual SS and individual Weighted retain separate physical controls and configurations. Individual deterministic retains its optional death year. No spouse/survivor controls were introduced into individual workspaces. Historical result loading does not overwrite next-run settings and no historical snapshot imports settings into the plan.

## Cancellation, fatal failure and races

`SocialSecurityAnalyzerJobController.start` has an additional overload with a terminal unsuccessful callback. Existing callers keep the previous overload. The hook runs after cooperative cancellation/fatal failure only while the same job still owns the controller and its revision remains eligible. It does not run after rejected admission, closure or an obsolete revision. Existing generation checks, lease release, error classification, progress and one-job admission remain in place.

The UI rollback additionally requires the captured plan/binding, selected owner, kind and unchanged edit epoch. It restores accepted/default values without a new analysis. Validation failures retain invalid text. Later edits are not overwritten by old rollback. The original result remains available throughout a replacement.

For individual memory-only result views, restoration assesses completed inputs through the existing `AnalysisFingerprintService`; it does not declare Current solely because controls were restored. This keeps previous results exportable when their original accepted inputs are reinstated. The view-local completed-input fingerprint is reset on a plan-object switch so numerically identical Plan B inputs cannot identify Plan A's memory-only result as Plan B's current result. Financial fingerprint algorithms themselves are unchanged.

A household-shape switch now closes the incompatible old dialog rather than letting spouse-only presentation code access an individual plan (or rebuilding views automatically). Accepted writes remain application-owned and continue. Reopen the analyzer for the new household. Same-shape switches use the original plan lifecycle and guarded deferred rebinding; the delayed restoration is ignored after newer edits or closure.

## Quick prerequisite and optional weighted baseline

Quick parameter restoration does not manufacture a Social Security result, publish a new SS completion, trigger calculation, or enable Run without its existing compatible current prerequisite. Its provenance is descriptive only. Tests reopen Quick parameters with no result and verify Run remains unavailable. Quick results remain memory-only.

The baseline adapter preserves ABSENT, NULL, VALUE and UNAVAILABLE separately in accepted metadata. Valid values retain exact optional original text and source. Invalid optional text remains UNAVAILABLE rather than becoming zero or an election. Existing baseline completeness rules remain responsible for comparison availability, and candidate search still runs when the comparison is unavailable.

Accepted unavailable text is bounded by the existing 256-character contract. If the original exceeds that budget, the retained prefix explicitly includes a text-unavailable marker and remains invalid, so truncation cannot accidentally create a valid age. Saved optional states are preserved across unchanged restoration/capture. NULL and ABSENT both have blank editable representation while their metadata remains distinct. No fingerprint is changed to make an unavailable historical baseline appear compatible.

## Save, Save As, shutdown and isolation evidence

The coordinator remains application-owned; view closure only detaches UI observers. Accepted writes keep their captured source path and UUID through switches/Save As. Existing configuration-only transactions preserve other analyzer entries and unrelated financial tokens. Tests block a parameter write, perform ordinary Save and independent Save As, close the dialog, release the writer and verify both files retain the accepted entry under independent UUIDs.

A fault-injection test fails an Exhaustive parameter write, retains accepted memory/results, retries without an additional calculation, and verifies that an unrelated dirty account edit stayed out of the durable file and the modified flag/revision were retained. Shared Phase 10B/10C coordinator and transaction tests continue to cover queue bounds, external conflicts, stale sequences, legacy migration, shutdown drain and exact-token safety across all kinds. No second executor or financial scheduler was added here.

## Fingerprints, historical snapshots, reports and financial invariants

Phase 10D does not modify `AnalysisFingerprintService`, Monte Carlo V1/V2 compatibility, compatibility policies, snapshot DTOs/codecs/digests, storage limits, financial/projection/SS/mortality/tax engines, PDFs or golden resources. The pre-existing Phase 10B fingerprint metadata exclusion remains as accepted. Financial decimal scales remain significant under their existing contracts.

Existing Couple SS, Exhaustive, Couple Weighted and Individual Weighted restoration tests are included in the final focused run. Their frozen assumptions, comparisons, heat maps and PDF paths remain in their original adapters. Parameter restoration performs no job admission or financial calculation; restart tests assert zero job generations and an empty calculation queue. No new result restoration is claimed for the memory-only modes.

## Automated verification

- Baseline: **58 passed**, zero failures/errors/skips, 23.309 seconds.
- Parameter/controller/contextual run: **62 passed**, zero failures/errors/skips, 32.506 seconds.
- Expanded intermediate run: **66 passed**, zero failures/errors/skips, 37.252 seconds.
- Initial final focused run: **135 passed**, zero failures/errors/skips, 1 minute. After the final plan-owner guard and additional cases, **139 passed**, zero failures/errors/skips, **57.446 seconds** (`target/phase10d-final-focused.log`). Includes all eight Phase 10C parameter variants and existing SS/Weighted/Exhaustive restoration.
- First complete Maven run: **3,384 total; 3,340 passed, 44 skipped, zero failures/errors**, **9 minutes 37 seconds** (`target/phase10d-full.log`). A subsequent complete run includes the final plan-owner guard and added cases.
- Final complete Maven suite: **3,388 total; 3,344 passed, 44 skipped, zero failures/errors**, BUILD SUCCESS, **9 minutes 36 seconds** (`target/phase10d-final-full.log`). All behavioral changes, the final plan-owner guard and all 28 new integration invocations are included. The established suite ran without added exclusions; its 44 existing opt-in/private/visual skips remain. A subsequent focused recompile verifies the final help-text-only correction found during preview review.
- Post-preview focused recompile/run: **139 passed**, zero failures/errors/skips, **56.616 seconds** (`target/phase10d-post-preview-focused.log`). This verifies the final wording correction without repeating unchanged financial benchmarks. `git diff --check` passes.

The new parameter integration class contains 28 test invocations: accepted/restart cases for all seven identities; cancellation/edit/validation cases; fatal failure and plan-switch cases for all seven; shared versus independent controls; optional states; retry/dirty isolation/Save As; individual factor/date restoration; blocked-write closure/Save coordination; identical-finance plan binding; never-saved Memory Only behavior followed by first explicit Save; and both directions of household-shape switching with in-flight work. Four additional parameterized controller cases distinguish cancel/fatal/obsolete/closed outcomes across all job modes.

Expensive couple lifecycle tests use bounded existing completed-result fixtures. Their request/result codec fidelity is separately verified by the existing persistence tests; no duplicated fixture is presented as a production financial capacity proof. Individual success cases use the real nine-strategy production paths. No hour-long weighted search was run. Error-injection tests intentionally produce diagnostic exceptions; final test failures/errors, rather than those expected logs, determine pass status.

Maven uses IntelliJ's bundled executable and the required project-local `.codex-m2/repository`, with `-DargLine=-Xmx4g`. No tests, financial assertions or golden values were disabled/regenerated. One contextual test was updated for the explicitly authorized accepted/default mode initialization policy; its valid-session and financial-revision assertions remain.

## Native Windows acceptance checklist

Native 10D acceptance is **pending**. Rebuild and restart normally; no JVM hot-patching is required.

Fourteen configuration-only JavaFX previews were generated at scene sizes 900×650 and 1180×820 under `target/phase10d-previews/<ANALYSIS_KIND>/<size>.png`. The compact Couple Weighted, Individual SS and Quick images were inspected. Restored values and Quick's prerequisite remain readable within the original scrolling layout. The preview intentionally contains no calculated results: it seeds synthetic accepted configuration metadata, and asserts zero job generations/calculation calls. It is not financial fidelity evidence or native acceptance. Visual review exposed outdated session-only baseline help; wording was narrowly corrected to distinguish accepted analyzer settings from unchanged financial assumptions. Existing help/tooltips remain associated with the original controls. The harness and generated demo JSON/images are ignored test artifacts, not release files. No Windows DPI, screen-reader or high-contrast certification is claimed.

1. Open a saved/migrated couple plan. Run SS Only with distinctive factors, dates and discount. Confirm Parameters SAVED without clicking Save; restart and verify exact settings.
2. Run Weighted with different settings. Navigate between SS and Weighted and verify independent accepted configurations in the same shared controls. Verify unavailable optional baseline text still permits candidate search and remains descriptive on reopen.
3. Run Quick with a distinctive count. Restart and check the count; Run must still require a current compatible SS result. No automatic SS or Quick calculation should occur.
4. Complete Exhaustive; confirm parameter save and normal Current/Outdated historical-result behavior without mortality prerequisites.
5. In an individual plan, run SS Only and Weighted with different factors/valuation settings; restart and verify independence and primary-only controls.
6. Run individual deterministic with an optional death year and verify restoration. Preserve existing Break-Even/PDF behavior.
7. Cancel a replacement and confirm prior accepted settings/results return. Validation errors must retain text; newer edits must not be overwritten by late rollback.
8. Leave a deliberate financial edit unsaved, complete an analysis and confirm only configuration metadata was automatically written and the modified indicator remains set.
9. Verify numerical changes/reverts against ordinary historical compatibility checks, and export a historical PDF to confirm frozen content/timestamps remain.
10. Exercise Save As and reopen both independent files. Switch among same-shape plans and between couple/individual plans; reopen the analyzer after a shape switch closes the old dialog.
11. If a parameter write fails, use Retry Parameters Save and verify no new financial job is started.

## Exact Phase 10D file inventory

Under `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/`:

- Added `SocialSecurityParameterBinding.java` — scoped control/coordinator binding, status/retry, guarded rollback.
- Added `SocialSecurityParameterMapping.java` — immutable request mappings and exact display conversion.
- Modified `SocialSecurityStrategyAnalyzerDialog.java` — four couple identities, shared control initialization, accepted callbacks and shape guard.
- Modified `SocialSecurityAnalyzerJobController.java` — narrow terminal outcome overload.
- Modified `CurrentStrategyBaselineView.java` — optional text/state restoration and bounded capture.
- Modified `SinglePersonIntegratedView.java` — deterministic parameters and independent child attachment.
- Modified `SinglePersonMortalityView.java` — separate individual settings, accepted handoff, rollback and currency refresh.
- Modified `IndividualCompletionPersistence.java` — suppress editor reassessment during programmatic parameter restore only; existing result contract retained.

Under `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/`:

- Added `SocialSecurityParameterPersistenceTest.java`.
- Modified `SocialSecurityAnalyzerJobControllerTest.java`.
- Modified `AnalyzerContextualInputsTest.java`.

Added this report. All other modified/untracked files predate 10D and belong to preserved 10A–10C work. Generated logs and test artifacts remain in ignored `target/`.

## Limitations and readiness

No native Windows 10D acceptance, screen-reader/DPI certification or new performance claim is made. Existing conservative configuration/schema budgets apply. Very long unavailable optional text cannot be retained in full; the bounded representation explicitly discloses this. Unsupported service profiles are not silently executed under another profile. Snapshots and configuration-only plan writes remain separate transactions with separate honest durability states. Unsaved/legacy plans need the existing explicit Save policy; historical snapshot import remains out of scope.

Phase 10D is ready for review and native acceptance based on passing automated verification. Phase 10E has not started and requires subsequent authorization after review/native acceptance.

## Final Git status

```text
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloFingerprintService.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPlanDocument.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyCandidate.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisFingerprintService.java
 M src/main/java/com/daviddunn/retirementplanner/domain/model/RetirementPlan.java
 M src/main/java/com/daviddunn/retirementplanner/persistence/AtomicFileWriter.java
 M src/main/java/com/daviddunn/retirementplanner/persistence/JsonRetirementPlanRepository.java
 M src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java
 M src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloSession.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonSession.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/CurrentStrategyBaselineView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualCompletionPersistence.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonMortalityView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAnalyzerJobController.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java
 M src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloNumericCompatibilityTest.java
 M src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloRestorationViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloInflationViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPdfExportTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPreviewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisFingerprintTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerContextualInputsTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveHistoricalPdfFidelityTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAnalyzerJobControllerTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/SinglePersonHouseholdUiTest.java
?? PHASE-10A-ANALYSIS-PARAMETER-PERSISTENCE-AUDIT.md
?? PHASE-10B-ANALYSIS-PARAMETER-SCHEMA-TRANSACTIONS.md
?? PHASE-10C-MONTE-CARLO-PARAMETER-PERSISTENCE.md
?? PHASE-10D-SOCIAL-SECURITY-PARAMETER-PERSISTENCE.md
?? src/main/java/com/daviddunn/retirementplanner/app/analysis/parameters/AcceptedAnalysisParameters.java
?? src/main/java/com/daviddunn/retirementplanner/app/analysis/parameters/AnalysisParameterConfiguration.java
?? src/main/java/com/daviddunn/retirementplanner/app/analysis/parameters/AnalysisParameterConfigurations.java
?? src/main/java/com/daviddunn/retirementplanner/app/analysis/parameters/AnalysisParameterCoordinator.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/PlanConfigurationTransaction.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloParameterControls.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloParameterMapping.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityParameterBinding.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityParameterMapping.java
?? src/test/java/com/daviddunn/retirementplanner/app/analysis/parameters/AnalysisParameterConfigurationsTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/analysis/parameters/AnalysisParameterCoordinatorTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/analysis/parameters/ParameterFixtures.java
?? src/test/java/com/daviddunn/retirementplanner/persistence/PlanConfigurationTransactionTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/controller/AnalysisParameterControllerTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloParameterPersistenceTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityParameterPersistenceTest.java
```

Index: no staged files. Branch and HEAD remain unchanged. All Phase 10A–10C work is preserved; Phase 10D additions/modifications are identified above. target/ previews, generated demonstration plan JSON, the temporary preview harness and logs are excluded by the existing ignore rules.
