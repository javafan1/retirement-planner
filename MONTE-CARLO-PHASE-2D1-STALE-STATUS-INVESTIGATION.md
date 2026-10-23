# Monte Carlo Phase 2D-1 — STALE status investigation

## Outcome and approval boundary

A deterministic false-Outdated path has been reproduced. A numerically equal inflation-floor input with a different BigDecimal scale changes the version-1 Monte Carlo fingerprint. Reentering survivor ages cannot fix that independent mismatch. No production correction has been applied: the requested implementation gate requires stopping before changing effective-input semantics. The existing version-1 implementation explicitly treats decimal scale as significant; normalizing that equivalence is not merely clearing a UI flag.

**The specific native snapshot has not been inspected.** Visible formatted settings alone do not reveal BigDecimal scale, original saved-baseline contents, or original session overrides. The reproduced path is consistent with the reported symptoms but is not proof that the actual user's snapshot differs only in this field. Do not claim the native result was genuinely financially Outdated or that its exact mismatch is established.

## Reproduction and precise measured mismatch

New test: `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloRestoredFingerprintInvestigationTest.java`.

It uses the production representative couple plan fixture, a real plan baseline, the existing JSON plan repository, `MonteCarloInputs`, `MonteCarloStrategyComparisonRunService.capture`, and the production fingerprint service. No simulation or projection is necessary to reproduce a fingerprint mismatch.

Original inputs use the reported 5,000 trials, seed 417, longevity mode, return 5%, volatility 12.00%, stochastic inflation mean 2.75%, volatility 1.75%, floor entered as `-2`, mortality factors 1/1, and survivor ages 67/67. After actual JSON save/load, the default floor text `-2.00` is used; other settings are identical. Expected unchanged currency fails. The privacy-safe recursive diagnostic reports exactly:

```text
settings.inflation.minimumInflationRate (scale-only)
```

Original and reopened fingerprint hashes differ. Diagnostics log structural paths/change categories and bounded collection indices only, never account balances, names, raw plan JSON or financial input values. They are test-scoped, not permanent production debug logging.

The separate unchanged-input control uses the exact same input representations before/after JSON load and passes. Its baseline object is a different Java object after loading. A change from survivor ages 67/67 to 66/67 changes the digest; returning to 67/67 restores it. Genuine Current Plan balance edits change the digest; undoing them restores it. A genuine Saved Baseline balance change changes the digest independently. Baseline object identity is not the cause in that fixture.

## Fingerprint dependency trace

`MonteCarloFingerprintService.comparison` captures:

- Both detached Current Plan and Saved Baseline financial plan documents.
- Shared settings: trial count, seed, return and volatility, optional stochastic inflation settings.
- Mortality inputs: primary/couple categories, birth/date context, adjustment factors, conditioning, mortality table metadata and entries, survivor age.
- Fixed ending year when applicable, analysis kind/household shape and optional supplied-world fingerprint.

The plan extractor removes durable plan IDs, nested baseline metadata and display names. For longevity it moves configured horizon/death scenario/death year into a separate explanatory-context fingerprint. Post-death expense factors remain effective. Paired survivor policy values in **both** captured plan documents remain effective; `capture` explicitly applies the respective current/baseline session survivor age to each detached plan. The primary mortality request also records the current-side survivor age.

`AnalysisFingerprintService.canonical` encodes decimal unscaled value **and scale**. `MonteCarloInputs.percentage` uses BigDecimal parsing and `movePointLeft(2)`, retaining typed scale. Therefore formatting differences in return, volatility, inflation settings (and potentially adjustment factors) can independently change the fingerprint. The plan-field scale policy is intentionally conservative and already covered by `fingerprintParserHonorsTheExisting4096DigitContractAndPreservesInputScale`; this investigation does not weaken that test or policy.

Comparison across the requested boundaries:

| Boundary | Evidence |
|---|---|
| A — accepted request | Production capture/fingerprint path audited; reproduction captures original next-run request without running 5,000 simulations |
| B — persisted snapshot reconstruction | Existing all-eight codec and lifecycle regressions pass, preserving original settings/request values/scales; no codec change proposed |
| C — reopened current effective inputs | Reproduction performs real JSON plan/baseline save/load, then captures constructor-equivalent next-run settings; floor scale-only mismatch measured |
| D — 67/67 reentry | Correct survivor values affect survivor fields only, leaving an independent floor/settings mismatch intact; exact survivor change/revert control passes |

These observations establish a false fingerprint mismatch mechanism, not the exact A/B/C/D contents of the unavailable native snapshot.

## Survivor ownership, defaults and initialization

`MonteCarloStrategyComparisonView` constructs fresh session controls. Current/baseline survivor fields initialize from their respective plan death-scenario elections; they are not automatically populated from historical analysis inputs. Manual edits are session-only and applied to detached candidate copies during capture. Snapshot restoration deliberately does not overwrite next-run controls.

Other constructor defaults include fixed mode, deterministic inflation, 5,000 simulations, seed 417, volatility text `12.00`, inflation volatility `1.75`, floor `-2.00`, expected return/general inflation initialized from the current plan, and mortality factors from controller longevity settings. Thus reopening does not necessarily reproduce the original session overrides, even when rounded visible values look equal. Blank or genuinely different survivor values are a real dependency difference until corrected, but are not the only possible difference.

`ApplicationController.open` synchronously loads the plan object (including baseline) before notifying source revision changes. No incremental baseline deserialization is used. Current-file binding is published afterwards and the existing binding/deferred synchronization handles that transition. Existing plan-switch/load-generation tests pass. No evidence was found that unchanged baseline identity alone affects the digest.

## UI state and PDF audit

Passive session restore sets its stale flag conservatively. The displayed warning checks `session.stale() && !persistence.current()`, so a matching completed compatibility assessment suppresses the old session banner. Editing triggers compatibility reassessment. Existing eight-mode actual-view tests pass Current -> Outdated -> Current behavior and suppress contradictory stale banners once Current. No UI-only unconditional reset is safe when a fingerprint genuinely differs.

PDF adapters and the historical wrapper read frozen runs/snapshot context and do not modify current inputs, plan or baseline. Phase 2D PDF tests pass repeated exports/no-extra-analysis checks and frozen-assumption comparisons. The investigation found no write-back or PDF-driven fingerprint mutation. Native export/restart with this specific user's data is not reproduced here.

## Test results and failing evidence retained

Using IntelliJ bundled Maven and the project-local `.codex-m2/repository`:

1. Reproduction selection: **2 tests, 1 passed, 1 failed, 0 errors/skips**, 10.209 s. Ignored evidence: `target/mc-2d1-reproduction.log`. The failed expected-Current assertion is retained, not disabled or converted into an expected false-Outdated assertion.
2. Controls and relevant existing regressions: **88 run, 86 passed, 0 failures/errors, 2 opt-in skips**, 19.423 s. Evidence: `target/mc-2d1-regressions.log`. Selection explicitly includes only the unchanged-input control from the investigation class, plus the full MC lifecycle (52), eight-mode restoration (8), codec and PDF action tests. It does **not** claim the known failing reproducer passed.
3. Full suite was not rerun after establishing the approval gate; it would include the deliberately preserved failing regression. Prior Phase 2D baseline was 3,208 passed, 44 skips, zero failures/errors. This investigation is not an implementation completion or green-build claim.

No golden expectations, calculation algorithms, random streams, snapshot representations, storage limits or production sources changed in Phase 2D-1.

## Recommended correction requiring approval

Approve a narrowly scoped, versioned compatibility comparison for **temporary analyzer numeric inputs**, treating proven numerically equivalent formatting as equivalent, while retaining exact snapshot values/scales and the conservative scale-sensitive financial-plan field policy. Assess legacy saved results by recomputing the comparable effective-input digest from their immutable frozen requests under the same comparison rule as current inputs; do not rewrite their stored digests or snapshot bytes. Explicitly preserve algorithm/reference compatibility checks, real value changes, baseline dependencies, and invalid-input handling. Audit any parameter for which scale could affect financial evaluation before including it in this equivalence rule.

Do not globally call `stripTrailingZeros` on plan data, globally change the canonical fingerprint service, restore historical controls over the user's edits, force-clear stale flags, or rerun the simulation to hide the mismatch. These would either exceed scope or mask genuine differences.

## Native evidence and retest

Before declaring the native cause, compare its original snapshot settings/request against a captured reopened request locally using the test-scoped path-only diagnostic. Needed evidence is the mismatching structural paths and whether each is presence/value/scale-only, plus compatibility status/version. Do not send raw plans, balances, personal data or complete snapshot contents. Numeric text formatting in the original session is useful if known, but formatted PDF percentages alone cannot establish original scale.

After approval and correction: reopen an unchanged plan and choose original mode/settings, enter 67/67, confirm Current without rerunning; vary each survivor age and restore; change/revert real current-plan and baseline inputs independently; export/restart and verify unchanged currency; exercise pending invalid text and fast plan switches. Recheck all eight modes, prior snapshots and PDF/no-calculation tests before full-suite acceptance. Until then historical results/PDFs remain safely inspectable and no snapshot has been overwritten by this investigation.

## Git status

Initial main/HEAD and all 21 modified tracked / 18 untracked Phase 1–2D files were preserved. Phase 2D-1 adds only this report and the test-scoped investigation class. The normal full suite now has one known failing reproducer awaiting an authorized correction. No staging, commit, push, reset or discard. Generated logs remain ignored under target. Final exact status follows.

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
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloRestorationViewTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloRestoredFingerprintInvestigationTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloResultLifecycleTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshotCodecTest.java
```
