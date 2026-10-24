# Phase 10C — Monte Carlo last-successful parameters

## Implementation and boundary

Implemented automatic accepted-parameter persistence and control restoration for all eight existing Monte Carlo identities. This uses the Phase 10B application-owned coordinator and configuration-only plan transaction; it does not call ordinary Save after an analysis. Social Security parameter integration (10D) is not implemented.

Initial branch: `main`. HEAD: `2e959d9770bd834cd4ca1bed3f50df1219607c65`. The working tree already contained the complete unstaged/untracked Phase 10B implementation and the Phase 10A report. Those changes were preserved. No staging, commits, pushes, resets or discards were performed.

## Independent identities and effective request mapping

| Identity | Prepared source | Additional effective settings |
|---|---|---|
| COUPLE_MONTE_CARLO_FIXED | Standalone settings | None |
| COUPLE_MONTE_CARLO_LONGEVITY | Settings and mortality request | Primary/spouse factors, current survivor age |
| COUPLE_MONTE_CARLO_PAIRED_FIXED | Prepared comparison request | None |
| COUPLE_MONTE_CARLO_PAIRED_LONGEVITY | Prepared comparison request and its mortality assumptions | Primary/spouse factors, separate current and frozen-baseline survivor ages |
| INDIVIDUAL_MONTE_CARLO_FIXED | Standalone settings | None |
| INDIVIDUAL_MONTE_CARLO_LONGEVITY | Settings and mortality request | Primary factor only |
| INDIVIDUAL_MONTE_CARLO_PAIRED_FIXED | Prepared comparison request | None |
| INDIVIDUAL_MONTE_CARLO_PAIRED_LONGEVITY | Prepared comparison request and its mortality assumptions | Primary factor only |

Every identity stores simulations, seed, expected return rate, volatility rate and effective inflation mode. Stochastic inflation additionally stores mean, volatility and floor rates; deterministic inflation retains explicit unavailable rate fields. Fixed modes do not invent mortality inputs. Single-person modes do not acquire spouse or survivor controls.

`MonteCarloParameterMapping` captures these values before execution from immutable requests, not from mutable controls at completion. The small read-only `MonteCarloStrategyCandidate.survivorClaimingAge()` accessor retrieves the election from the already-frozen comparison candidate. No plan copies, trial histories, projections or financial results are put into configurations.

Exact BigDecimal values and scales are retained. Conversion back to percentage text uses exact decimal `scaleByPowerOfTen(2)` and an exact decimal string; no double conversion or rounding is introduced.

## Acceptance and automatic persistence

Both sessions retain their existing generation, revision, cancellation and dialog-lifetime checks. Only their eligible successful-result callback accepts parameters. The parameter handoff uses the same completion run UUID and timestamp as the accepted historical result, together with a sequence and original plan/path/binding captured before work. A completed simulation with zero funded trials remains a valid success under the existing session contract.

The typed event is submitted to `AnalysisParameterCoordinator`. JSON writes remain asynchronous, bounded and application-owned. The existing exact-token transaction preserves unrelated durable financial tokens and does not write dirty in-memory financial edits, mark the plan modified, increment financial revisions or invalidate projection caches. Snapshot and parameter durability are separate: the UI has separate result-save and parameter-save notices.

Parameter status exposes PENDING, SAVED, SAVE FAILED and MEMORY ONLY. **Retry Parameters Save** retries the immutable accepted configuration through the coordinator without submitting financial work. External-file conflicts receive a privacy-safe explanation. Never-saved and unmigrated legacy plans remain Memory Only until the existing required explicit Save. Closing a dialog detaches its status listener but leaves accepted persistence work owned by the application.

## Control restoration, mode switches and rollback

`MonteCarloParameterControls` is a scoped adapter over the original controls. It restores the selected kind's accepted settings before normal input listeners are installed. Later programmatic restoration uses a local suppression guard, preserves the established standalone valid-mortality-session propagation, and performs a readiness/currency refresh. Comparison mortality ownership remains independent as before.

Mode switches restore that kind's accepted settings, or established initial defaults when absent. **Unaccepted drafts are discarded on mode switching**, rather than treated as accepted settings. Reopening also starts from accepted settings. No automatic snapshot-to-configuration import is performed.

Both sessions now offer a terminal unsuccessful callback without changing their existing overloads or financial work. Actual cancellation or fatal failure restores accepted/default controls only when plan binding, kind, current dialog ownership and captured UI edit epoch still match. Validation errors leave invalid editable text in place. Later edits, plan switches and closed views are protected against late rollback. Historical results remain visible and are not replaced by unsuccessful work.

The coordinator retains a newer application-owned accepted configuration when the exact same saved UUID/path is reopened into a new plan object, including failed/pending writes. It never redirects the original write to a new path. A guarded deferred UI refresh handles the controller's source notification occurring before its final file binding. This prevents losing accepted in-memory settings after a failed write while avoiding restoration over a newer user edit.

## Save, Save As and shutdown

The Phase 10B transaction/coordinator remains responsible for ordinary Save interleavings, independent Save As identity/configuration copies, cooperative locking, external conflicts, backup recovery and shutdown draining. The new integration does not call `ApplicationController.save()` automatically. An accepted pending write retains its original plan/path when Save As occurs. Existing committed-run acknowledgements resolve matching retained states after an ordinary Save has actually committed them.

The focused UI integration test blocks a configuration write, performs ordinary Save and Save As, closes the view, then releases the writer and verifies both independently identified files retain the accepted configuration. Other tests verify failed-write retry, same-plan reopen, dirty-finance isolation and stale-plan completion rejection. Underlying transaction/coordinator tests exercise conflict, queue ordering, failure and shutdown contracts.

## Fingerprints, snapshots and PDFs

No Phase 10C edits change fingerprint algorithms, numeric compatibility versions, historical payloads/digests, snapshot codecs, storage limits, result DTOs, financial engines, random streams, trial scheduling or PDF adapters. Restoring controls does not declare a result Current: the existing compatibility assessment remains authoritative. Formatting-equivalent decimal changes use existing V2 numeric compatibility; numerical changes and reverts are tested against restored results.

Existing tests that asserted the entire plan JSON remained unchanged after a successful analysis now compare the financial document excluding only the recognized `analyzerConfigurations` metadata. Numerical tokens remain intact in the comparison helper. Existing UI test setup selects the mode before setting deliberate trial counts, matching the newly authorized mode restoration policy; financial goldens and assertions were not weakened.

## Automated verification

- Pre-edit focused baseline: **71 passed**, zero failures/errors/skips, 33.904 seconds (`target/phase10c-baseline.log`).
- Expanded integration run: **114 passed**, zero failures/errors/skips, 43.353 seconds (`target/phase10c-expanded.log`).
- Final focused compatibility/UI/PDF run: **117 total; 116 passed, 1 skipped, zero failures/errors**, 1 minute (`target/phase10c-final-focused.log`). The skip is an existing opt-in test, not a new exclusion.
- Full Maven suite: **3,356 total; 3,312 passed, 44 skipped, zero failures/errors**, BUILD SUCCESS, **9 minutes 51 seconds** (`target/phase10c-full.log`). The complete established suite ran without added exclusions; existing opt-in/private/visual skips remain. The final focused skip was in `MonteCarloHistoricalPdfFidelityTest`.

The new `MonteCarloParameterPersistenceTest` executes twenty test invocations: accepted request/JSON/restart fidelity and cancellation/failure/edit-epoch protection for each of eight identities, plus four cross-mode/retry/Save As/obsolete-plan scenarios. It uses real engines with bounded two-trial deterministic fixtures. Restart views receive throwing calculation factories and make zero calculation calls. Exact accepted values, run identity, time and saved JSON entries are independently checked. These tests are integration evidence, not production-scale performance measurements.

Maven uses IntelliJ's bundled Maven, the required project-local `.codex-m2/repository`, and `-DargLine=-Xmx4g`. Failure-injection tests intentionally exercise disk errors; their logged exceptions do not signify failing test assertions. No golden values were regenerated and no tests were disabled.

## Rendered evidence and native acceptance

All eight restored variants were rendered at 900×650 under `target/phase10c-previews/<ANALYSIS_KIND>.png`. The couple paired-longevity and individual Fixed images were visually inspected: parameter status, original controls and results are reachable in the existing scroller. These previews do not certify Windows DPI, Narrator or high contrast. They are ignored generated artifacts, not source assets.

Native Windows acceptance remains **pending user execution**:

1. Rebuild and restart normally; open an existing saved plan (explicitly migrate a legacy plan first).
2. Run standalone Fixed with distinctive settings. Confirm Parameters SAVED without clicking Save; restart and verify those settings.
3. Run standalone Longevity-Adjusted with different settings. Switch modes and verify each accepted configuration returns.
4. Repeat with paired comparison and verify its Fixed/Longevity configurations remain independent of standalone.
5. Cancel a replacement, then exercise a failed replacement if practical; confirm prior settings/results return. Validation errors should retain invalid text for correction.
6. Change a numeric value and revert it: verify Outdated then Current under the usual dependency rules. Formatting-only decimal changes should remain compatible.
7. Save As and reopen both files; verify independent identities/configurations. Retry a failed parameter write without rerunning.
8. Leave a deliberate financial edit unsaved, run an analysis, and confirm the modified indicator stays set and only parameters reached the durable file.
9. Repeat appropriate modes for a single-person plan. Confirm historical PDF data/timestamps remain unchanged.

## Exact Phase 10C file inventory

Paths below are relative to `src/main/java/com/daviddunn/retirementplanner/` unless marked otherwise:

- Added `ui/montecarlo/MonteCarloParameterControls.java`.
- Added `ui/montecarlo/MonteCarloParameterMapping.java`.
- Modified `app/montecarlo/MonteCarloStrategyCandidate.java` (frozen election accessor only).
- Modified `app/analysis/parameters/AnalysisParameterCoordinator.java` (pre-existing untracked Phase 10B file; added status observation, retained same-binding accepted settings and save acknowledgement/conflict handling).
- Modified `ui/montecarlo/MonteCarloAnalysisView.java`.
- Modified `ui/montecarlo/MonteCarloStrategyComparisonView.java`.
- Modified `ui/montecarlo/MonteCarloSession.java`.
- Modified `ui/montecarlo/MonteCarloStrategyComparisonSession.java`.

Under `src/test/java/com/daviddunn/retirementplanner/`:

- Added `ui/montecarlo/MonteCarloParameterPersistenceTest.java`.
- Modified `app/montecarlo/MonteCarloRestorationViewTest.java`.
- Modified `ui/montecarlo/MonteCarloInflationViewTest.java`.
- Modified `ui/montecarlo/MonteCarloMortalityViewTest.java`.
- Modified `ui/montecarlo/MonteCarloPdfExportTest.java`.
- Modified `ui/montecarlo/MonteCarloPreviewTest.java`.
- Modified `ui/montecarlo/MonteCarloViewTest.java`.
- Modified `ui/views/SinglePersonHouseholdUiTest.java`.

Added this report. All other existing working-tree modifications/untracked files belong to the preserved Phase 10A/10B baseline. Full final Git status is appended below after verification.

## Limitations and recommendation

No native acceptance is claimed. Invalid drafts are intentionally not retained across mode switches. Conservative Phase 10B configuration bounds still apply; unsupported future metadata is protected rather than rewritten. An external-file conflict requires reviewing/reopening the changed file before retrying. Accepted settings and result snapshots have separate commits; their independent failure states remain visible. Memory-only results/settings do not promise cross-session recovery until explicit Save. Legacy snapshot import remains outside scope.

Automated verification is complete and Phase 10C is ready for review and native acceptance. `git diff --check` passes. Phase 10D should proceed only after review, native acceptance of 10C and explicit authorization. No Social Security integration or other phase was started.

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
 M src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloNumericCompatibilityTest.java
 M src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloRestorationViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloInflationViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPdfExportTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPreviewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisFingerprintTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveHistoricalPdfFidelityTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/SinglePersonHouseholdUiTest.java
?? PHASE-10A-ANALYSIS-PARAMETER-PERSISTENCE-AUDIT.md
?? PHASE-10B-ANALYSIS-PARAMETER-SCHEMA-TRANSACTIONS.md
?? PHASE-10C-MONTE-CARLO-PARAMETER-PERSISTENCE.md
?? src/main/java/com/daviddunn/retirementplanner/app/analysis/parameters/AcceptedAnalysisParameters.java
?? src/main/java/com/daviddunn/retirementplanner/app/analysis/parameters/AnalysisParameterConfiguration.java
?? src/main/java/com/daviddunn/retirementplanner/app/analysis/parameters/AnalysisParameterConfigurations.java
?? src/main/java/com/daviddunn/retirementplanner/app/analysis/parameters/AnalysisParameterCoordinator.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/PlanConfigurationTransaction.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloParameterControls.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloParameterMapping.java
?? src/test/java/com/daviddunn/retirementplanner/app/analysis/parameters/AnalysisParameterConfigurationsTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/analysis/parameters/AnalysisParameterCoordinatorTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/analysis/parameters/ParameterFixtures.java
?? src/test/java/com/daviddunn/retirementplanner/persistence/PlanConfigurationTransactionTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/controller/AnalysisParameterControllerTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloParameterPersistenceTest.java
```

No files are staged. The status includes preserved Phase 10A/10B work listed in their reports. Generated logs/previews remain under ignored target/.
