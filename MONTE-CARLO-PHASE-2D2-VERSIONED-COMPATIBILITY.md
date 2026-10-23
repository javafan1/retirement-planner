# Monte Carlo Phase 2D-2 — Versioned Numeric Compatibility

## 1. Summary and root cause

Phase 2D-1's retained false-Outdated reproducer now passes. Temporary percentage text such as `-2` and `-2.00` parses into numerically equal BigDecimals with different scales. The original canonical fingerprint intentionally includes scale, which is appropriate for the established conservative plan/baseline contract but overstates differences in temporary analyzer settings.

The correction adds a separate, explicitly versioned **read-only compatibility representation**. It does not migrate snapshots, alter stored hashes, reset user inputs, or run financial calculations. The specific original native snapshot has not been inspected; resolving this reproducible path is not proof that it has no additional mismatched inputs.

## 2. Contract/version design

`MonteCarloFingerprintService.Contract` explicitly distinguishes:

- `EXACT_V1`: the existing exact input-and-scale representation, including its internal version 1 domain marker. `storedV1` computes this from frozen snapshot inputs.
- `TEMPORARY_NUMERIC_V2`: a separate representation with internal version 2 domain marker and a strict temporary-field allowlist. Default `assess` uses this for current compatibility comparisons.

The stored envelope's `fingerprintVersion` remains **1** because stored fingerprints continue to use the original V1 contract. `MonteCarloResultLifecycle.completion` explicitly calls `storedV1`; a V2 compatibility hash is never silently written into a V1 envelope. No new snapshot field or encoding is introduced. This separation is intentional: envelope version describes the stored bytes, while the application compatibility version describes a comparison of available original and current inputs.

`compatibility(saved, current)` dispatches only the supported V1 stored contract, verifies analysis kind and payload kind, recomputes the original V1 fingerprints and requires both stored input/context hashes to match. It then applies the existing financial algorithm and reference compatibility policy. Only after those checks does it reassess both the immutable original inputs and current inputs under V2. Equal effective/context digests produce Current; real differences produce Outdated. Invalid pending inputs produce Inputs Need Attention. Unknown/unverifiable contracts remain Incompatible or Unavailable rather than being presumed Current.

No stored payload, digest, original completion timestamp, provenance, plan UUID, household shape, file binding, source snapshot or Save As copy is rewritten to perform this reassessment. Existing codec/repository validation still occurs before a snapshot reaches the lifecycle.

## 3. Exact normalization scope

Normalization operates only on newly built JSON comparison nodes at these explicit paths:

```text
/settings/expectedReturn
/settings/volatility
/settings/inflation/expectedInflationRate
/settings/inflation/inflationVolatility
/settings/inflation/minimumInflationRate
/mortality/primary/mortalityAdjustment/factor
/mortality/coupleInputs/primaryAdjustment/factor
/mortality/coupleInputs/spouseAdjustment/factor
```

The repeated primary factor occurs in both person and couple request representations and is normalized consistently. The actual source records remain untouched. Each numeric value uses exact `BigDecimal.stripTrailingZeros`, with no double conversion, rounding, tolerance, or numerical recomputation. Numerically equal finite decimals therefore have a stable common representation, including zero. Numeric differences remain distinct.

Only allowlisted numeric nodes are normalized; strings, dates, categories, mortality table values, array ordering, seed, trial count, source identity, survivor ages and other fields retain their existing meanings. Null/absent optional inflation remains absent, distinct from an active configuration with numeric values. Required null inputs are invalid and do not generate a valid compatibility digest. The UI's existing parsing/validation rejects invalid intermediate text instead of substituting a prior valid value.

Source audit: existing return/inflation generators consume the numeric settings, not user text formatting. Mortality factors represent the same exact hazard multiplier regardless of scale. No engine or generator was changed. This compatibility rule does not claim a rerun must reproduce identical BigDecimal representation scales; it preserves the original already-computed results and compares the approved numerical settings only.

## 4. Plan and baseline protection

Neither global `AnalysisFingerprintService` nor Phase 9 policy was changed in this phase. The MC plan extractor remains unchanged. Financial plan documents A and B keep the V1 scale-sensitive canonicalization, ordering, owners, accounts, income, expenses, taxes, Roth/RMD/death policy and explanatory-context treatment. No global stripTrailingZeros traversal is used.

Tests independently change only a Current Plan account balance's scale and require Outdated. Paired-mode tests do the same to the Saved Baseline alone and require Outdated. Actual balance changes and survivor policy changes remain detected. JSON reconstruction into a new baseline object does not affect currency when its inputs are unchanged.

## 5. Lifecycle integration and legacy handling

Only `MonteCarloResultLifecycle.refreshAssessment` switches from comparing stored V1 hashes directly against current input hashes to the verified V1-to-V2 reassessment. Work remains on the application-owned background executor. Existing binding/generation/edit guards, accepted-success gating, async store, replacement retention, file identity, retry, backup, cloning and shutdown contracts are unchanged.

All eight variants use the same versioned compatibility path. A formatting edit clears an in-progress assessment as before; its eventual V2 match returns the view to Current and suppresses the conservative session STALE banner. Restoration never overwrites controls with saved settings. Historical financial charts, results and PDFs remain frozen.

Valid legacy snapshots require no disk migration and no simulation. If their exact original contract cannot be reproduced safely, they remain preserved with conservative compatibility status. Unsupported versions and malformed snapshots still fail through existing envelope/codec/repository guards. Existing algorithm/reference differences cannot be hidden by numeric formatting equivalence.

## 6. Files changed in this phase

All pre-existing Phase 1–2D/2D-1 work is preserved. Phase 2D-2 changes only:

- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloFingerprintService.java` (pre-existing untracked file): versioned representations, temporary numeric allowlist, verified legacy reassessment.
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloResultLifecycle.java` (pre-existing untracked file): explicit stored-V1 completion fingerprint and worker-side compatibility entry point.
- `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloNumericCompatibilityTest.java` (new): eight-mode legacy/V2 proof, exact bytes, protected financial scale, PDF/restart and invalid-input checks.
- `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloRestorationViewTest.java` (pre-existing untracked test): async formatting edits return to Current in actual views with throwing calculation seams.
- `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloRestoredFingerprintInvestigationTest.java` (Phase 2D-1 test): adds exact pre-correction V1 hash constants without changing its expected-Current assertion.
- This report (new).

The Phase 2D-1 failing expected-Current assertion is retained and now passes through the new default V2 assessment. Two exact V1 digest constants from its pre-correction log are checked separately; they are not regenerated from the new implementation. No financial expected values were regenerated.

## 7. Regression evidence

Commands use IntelliJ bundled Maven, the project-local `.codex-m2/repository`, Java 25, and `-DargLine=-Xmx4g`.

- Initial fingerprint/investigation/lifecycle/restored-view/PDF selection: **70 passed**, zero failures/errors/skips, 24.977 s (`target/mc-2d2-first.log`).
- Broader compatibility/fingerprint/lifecycle/codec/restored-view/PDF/frozen-weighted selection: **95 run, 92 passed, zero failures/errors, 3 opt-in skips**, 31.021 s (`target/mc-2d2-focused.log`). Optional capacity/replay and large historical PDF tests are skipped in this ordinary selection; previously executed capacity evidence is unchanged.
- Full Maven suite: **3,256 run; 3,212 passed; 0 failures; 0 errors; 44 skipped. BUILD SUCCESS. Total 10:19**, completed October 21, 2026 at 12:40:08 EDT (`target/mc-2d2-full.log`). Existing Phase 9 compatibility, seeded MC/financial regression, lifecycle, PDF and plan-persistence tests passed. The 44 skips retain existing opt-in capacity/preview/private/reference-data conditions; none was added or disabled in this correction.

Assertions cover -2/-2.00, 5/5.0-equivalent percentage rates, 1.00/1.01 mortality factors, trial seed changes, unchanged JSON-restored baselines, real current/baseline changes, survivor change/revert, all eight shapes/modes, legacy V1 hash verification, corrupted original hashes, null/optional inputs, immutable snapshot bytes/scale and asynchronous actual-view status refresh. No financial work is invoked for compatibility. Existing throwing work/projection seams protect restoration, chart selection and export paths.

The new PDF/restart checks export each frozen variant, decode into a fresh graph and compatibility service, and require Current with numerically equivalent pending inputs. They preserve original completion timestamps and snapshot bytes. Final targeted execution of those added checks is recorded below.

## 8. Native acceptance checklist

1. Rebuild and restart normally; do not hot-patch the running JVM. Preserve the saved plan/snapshot.
2. Select a saved couple plan with its original baseline and run the 5,000-trial Longevity-Adjusted paired comparison with the desired shared settings and survivor ages 67/67. Confirm Saved, export a Current PDF, and record its original completion timestamp.
3. Restart/reopen the same plan. Select the original mode and reenter any session-only settings not initialized from the plan, including stochastic inflation and survivor ages. Historical restore intentionally does not replace next-run controls.
4. Confirm unchanged effective inputs produce Current. Type inflation floor `-2`, then `-2.00`; after async assessment both must remain Current and the STALE warning must clear.
5. Change the floor numerically (for example, -1.99 while otherwise valid): require Outdated. Return to -2 or -2.00: require Current without rerunning.
6. Change/revert temporary expected-return formatting and mortality-factor formatting; only real numerical differences should invalidate compatibility.
7. Change survivor policy, a genuine current financial input, and a baseline input independently: require Outdated; revert each actual change and require Current.
8. Export restored Current/Outdated PDFs and compare original financial data, assumptions and original start/completion times. Export time/status may differ; historical data must not.
9. If the original native snapshot still reports Outdated, do not rerun or claim this fix resolved every dependency. Use the Phase 2D-1 test-scoped structural-path diagnostic locally to compare original/current settings, mortality, Current Plan and Saved Baseline. Report only mismatching paths and value/scale/presence category, not raw financial values or plan files. Actual other differences remain valid reasons for Outdated.

## 9. Limitations and review boundary

The specific original native mismatch remains unconfirmed. This change resolves the proven numerical-format false mismatch, not a genuine baseline/plan/settings change. No automatic historical-input restoration, global financial normalization, cache migration, algorithm modification, extra persistence mode, storage-limit change, snapshot encoding change or Phase 2E work is included.

Older application builds can still read newly saved snapshots under V1; they may retain the old conservative false-Outdated behavior because they do not implement V2 reassessment. No forward-version payload is written for them to misinterpret.

A formatting edit may briefly show the existing assessment-in-progress state until the background worker completes. Pending invalid input remains Inputs Need Attention, not Current. Any newly added temporary numeric setting must be explicitly audited/versioned rather than automatically normalized.

Final verification, exact Git inventory and review result follow. Nothing staged, committed, pushed, reset or discarded.

## Git inventory

```text
 M src/main/java/com/daviddunn/retirementplanner/app/export/MonteCarloPdfExporter.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityAnalysisResult.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityRequest.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonResult.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisFingerprintService.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisKind.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisVersions.java
 M src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java
 M src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPdfExportAction.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloSession.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonSession.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonView.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloComparisonSessionTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPdfExportTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloSessionTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisFingerprintTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/SinglePersonHouseholdUiTest.java
?? MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-1-AUDIT.md
?? MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-2A-CAPACITY.md
?? MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-2B-CODECS.md
?? MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-2C-LIFECYCLE.md
?? MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-2D-PDF-FIDELITY.md
?? MONTE-CARLO-PHASE-2D1-STALE-STATUS-INVESTIGATION.md
?? MONTE-CARLO-PHASE-2D2-VERSIONED-COMPATIBILITY.md
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloFingerprintService.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloPlanDocument.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshotMapping.java
?? src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshotNumbers.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloHistoricalPdf.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloResultLifecycle.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloSnapshotPresentationMapper.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloCapacityProbeTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloHistoricalPdfFidelityTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloNumericCompatibilityTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloRestorationViewTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloRestoredFingerprintInvestigationTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloResultLifecycleTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshotCodecTest.java
```

## Final verification and review result

Final focused verification: **16 passed; zero failures/errors/skips; 20.791 s**, completed October 21, 2026 at 12:40:35 EDT (`target/mc-2d2-final-focused.log`). This run explicitly includes the final frozen V1 digest constants from the pre-correction failure log, all-eight numeric/legacy/financial-plan-scale/PDF-restart assertions, all-eight asynchronous restored views with throwing calculation seams, original/restored/historical PDF fidelity, the enabled real cached 10,000-trial paired historical export, and both frozen weighted compatibility fixtures. These test-only assertions were finalized during the full suite and then executed on the final compiled source in this focused run. No production change occurred after the full suite began.

The retained Phase 2D-1 expected-Current test passes. Both exact V1 digest constants still match. No financial or payload hashes were regenerated to obtain this result.

Final `git diff --check` passed (exit 0); staged diff is empty. Accumulated working tree: **21 tracked modified files and 22 expected untracked source/test/report files**, preserving the pre-existing work. Only the six Phase 2D-2 files listed above changed in this phase. Domain calculations, golden resources, dependencies/build configuration, snapshot limits, payload encoding, PDFs and global Phase 9 fingerprints are untouched by the correction. Logs, test PDFs and images remain ignored under target. No staging, commit, push, reset or discard.

Phase 2D-2 implementation and automated verification are complete for review. Native acceptance remains required for the original user's snapshot; no claim is made that every possible remaining input difference was resolved. Stop before Phase 2E.
