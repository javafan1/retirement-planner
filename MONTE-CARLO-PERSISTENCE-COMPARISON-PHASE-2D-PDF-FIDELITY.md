# Monte Carlo Persistence and Comparison — Phase 2D

## 1. Outcome and scope

Historical PDF export now permits compatible completed Monte Carlo results even when they are Outdated or current pending inputs need attention. Reports retain frozen financial/statistical data, show original analysis start/completion times separately from export time, identify historical status prominently, and report actual storage status. Incompatible/unavailable financial results remain blocked. No simulations, financial rules, random streams, snapshot representations, limits, dependencies, or golden resources were changed in this phase.

Phase 2C native acceptance was confirmed by the user. Phase 2D evidence below is automated JavaFX/PDF testing and actual PDF page-image inspection; new native application acceptance is recommended, not claimed. Stop after Phase 2D; Phase 2E is not implemented.

## 2. Source audit and eight-mode coverage

The two existing PDF adapter overloads consume immutable completed runs rather than controllers or editable controls (`MonteCarloPdfReportAdapter.from(MonteCarloRun)` and `from(MonteCarloStrategyComparisonRun)`). The retained run's settings, plan-detail strings, chart context, request, outcomes and cached summaries supply reports. Restoration uses `MonteCarloSnapshotPresentationMapper`; presentation/rendering does not call request world-generation functions.

| Shape | Variant | Frozen financial/statistical report data | Current and historical export |
|---|---|---|---|
| Couple | Fixed analysis | Funding probability, four terminal distributions, annual percentiles/reference, SS markers and Roth/RMD periods | Verified |
| Individual | Fixed analysis | Same supported single-person information, no invented spouse data | Verified |
| Couple | Longevity analysis | Mortality categories/factors/table, conditioning, survivor context, annual living/funded counts, terminal-year counts and distributions | Verified |
| Individual | Longevity analysis | Independent individual mortality settings, single-person population/terminal outcomes | Verified |
| Couple | Fixed paired comparison | Ordered matched A/B trials, completion states, four paired metric distributions/means/sign proportions, annual paired populations | Verified |
| Individual | Fixed paired comparison | Same existing comparison contract, individual lifetime shape | Verified |
| Couple | Longevity paired comparison | Matched market/inflation/lifetime worlds, paired financial results and mortality assumptions | Verified |
| Individual | Longevity paired comparison | Independent individual mortality request plus original matched A/B results | Verified |

Shared dependencies retained: trial count, original seed, expected return/volatility, deterministic or stochastic inflation configuration, original source/provenance, frozen plan strings and labels, reference incompleteness, funding failures, percentile method and sample denominators. The existing report has four terminal financial metric columns (assets, net worth, estate and taxes); this phase adds no metrics or unsupported charts. Charts remain investable-asset trajectories or paired investable differences; SS markers and Roth/RMD shading remain frozen reference information, not newly calculated income trajectories.

No report dependency was found that reads current mutable plan values. The defects were readiness and provenance: stale results were unnecessarily blocked from PDF export, and reports omitted original completion time/status. Live-plan changes still affect next-run inputs and currency; they never replace historical report assumptions.

## 3. Narrow changes and files

Production changes introduced in Phase 2D:

- `ui/montecarlo/MonteCarloHistoricalPdf.java` (new): pure export-time status/provenance wrapper; preserves original report type, chart, sections and every original context line.
- `ui/montecarlo/MonteCarloResultLifecycle.java` (pre-existing untracked Phase 2C file, extended): retains accepted completion context while asynchronous snapshot metadata is prepared; separates compatible exportability from Current status and durable save success. Snapshot encoding is not invoked on FX merely to export.
- `ui/montecarlo/MonteCarloAnalysisView.java` and `MonteCarloStrategyComparisonView.java`: allow safe historical export, capture currency invalidation immediately on plan revision, wrap the frozen report; keep existing layouts and simulation admission behavior.
- `ui/montecarlo/MonteCarloPdfExportAction.java`: historical-export help text.
- `app/export/MonteCarloPdfExporter.java`: bold historical/current notice, short Outdated running-header label and historical document subject; existing chart/table renderer and layout retained.

Tests added/extended:

- `app/montecarlo/MonteCarloHistoricalPdfFidelityTest.java` (new).
- `app/montecarlo/MonteCarloRestorationViewTest.java` (pre-existing Phase 2C test, extended).
- `ui/montecarlo/MonteCarloPdfExportTest.java`.
- `ui/views/SinglePersonHouseholdUiTest.java`: replaces the obsolete stale-export prohibition with historical availability/status and frozen chart/section assertions; original single-person and no-additional-analysis assertions remain.

Paths are relative to the corresponding `src/main/java/com/daviddunn/retirementplanner/` or `src/test/java/com/daviddunn/retirementplanner/` roots. Other working-tree changes are preserved earlier-phase work, not attributed to Phase 2D.

## 4. Original, restored and Outdated fidelity

The existing `MonteCarloSnapshotCodecTest.allEightFamiliesRetainExactEvidenceAndFrozenPdfInputsAcrossSeeds` computes all eight variants using real engines at seeds 417 and 918, captures the original runtime results, serializes/decodes, reconstructs and independently compares original runtime outcomes, settings, summaries, plan documents and chart/report data. Comparisons use exact record/BigDecimal equality, retaining scale, not rounded display tolerances. Paired alternatives use different Roth settings. Existing test bodies and calculation assertions are unchanged.

The new historical PDF test uses independently calculated eight-mode Phase 2C fixtures, compares pre/post production-envelope report data, adds identical Current metadata and fixed export time, exports both PDFs, and compares every page's extracted text and rendered RGB pixels at 50 DPI. Exact report chart/section equality also holds for the Outdated version; all original context lines and completion times remain present. Raw PDF byte identity is not required because document metadata/IDs may vary.

Actual JavaFX view restoration tests now capture Current and Outdated reports for all eight variants. Changing the seed preserves historical chart/sections and pending controls. Existing PDF action tests also change DOB and economic/planning assumptions and verify the original context/financial data remain unchanged. Invalid pending inputs retain historical results instead of inventing a matching fingerprint. An unassessed result is conservatively labeled historical, with an explicit warning that currency has not yet been assessed.

## 5. Paired statistical fidelity

`MonteCarloStrategyComparisonAnalyzer` obtains one indexed world per trial, evaluates A and B against that same world and stores one `MonteCarloPairedOutcome` with original index and lifetime. This code is unchanged. Snapshot restoration preserves ordered pair outcomes, seeds/settings, lifetime information, cached summaries, failures, annual paired counts and both original plan documents. Market/inflation stream and algorithm provenance is now visible in export context. Caller-supplied-world fingerprints remain identified as supplied, rather than claiming that a seed alone can regenerate them.

PDFs retain the existing matched-world explanation, both-completed conditional financial denominators, all-requested funding denominators, tie probabilities, tax sign caveat and late-life population warning. Independent saved simulations are never combined or described as paired. No new cross-result statistical comparison is introduced.

## 6. Large result and visual verification

The opt-in test `realCachedTenThousandPairedTrialsExportWithoutCalculation` decoded the real independently calculated Phase 2B 10,000-trial couple fixed paired snapshot (`target/mc-codec-capacity-true-false-true-10000.json.gz`), reconstructed its complete comparison, and exported an Outdated PDF. It checked trial count, rendered pages and verified that the source snapshot bytes were unchanged. This is cached real calculation evidence, not a new 10,000-trial simulation or a duplicated synthetic-trial fixture. Original large-runtime A/B fidelity remains the Phase 2B evidence; fresh all-eight original/runtime comparisons are the smaller deterministic codec tests described above.

New ignored artifacts: `target/mc-2d-pdf/`, with original/restored/outdated PDFs for all eight kinds and the large paired historical PDF. Page images use 90 DPI for inspection; automated original/restored pixel comparison uses 50 DPI. Actual images inspected:

- `COUPLE_MONTE_CARLO_FIXED-0.png`: chart, uncertainty bands, reference line, SS markers, Roth/RMD strips, full legend and historical context.
- `COUPLE_MONTE_CARLO_LONGEVITY-0.png` and `-1.png`: mortality context, nominal trajectory, terminal distributions, sample denominators and continued population table.
- `COUPLE_MONTE_CARLO_PAIRED_FIXED-1.png`: paired table, tax interpretation and long annual table.
- `COUPLE_MONTE_CARLO_PAIRED_LONGEVITY-2.png`: annual living/comparable population, repeated table headings and page footer.
- `INDIVIDUAL_MONTE_CARLO_FIXED-2.png`: continued percentile table and long multi-million monetary formatting.
- `large-0.png` and `large-1.png`: 10,000-trial stochastic-inflation paired chart, negative/positive financial values, matched-world text, sample counts and historical label.

No clipping, missing chart bands/legends, broken table headings, missing footers or unexpected blank pages was observed in these inspected images. Existing report colors, ranking-free MC tables and percentile definitions were preserved. Native DPI/Narrator/high-contrast checks are not claimed.

## 7. No-recalculation and safety

The actual restoration views inject throwing financial-reference/projection methods, simulation work callbacks and executors that fail if restoration schedules simulation. Historical PDF capture succeeds from these restored views. Existing PDF action tests count analysis calls and export repeatedly, then edit plan/input fields and export frozen data without increasing the count. Pure PDF report/export types have no calculation/controller dependency. Actual restored views also receive End/Home/Left/Right chart events and paired metric-row selection while every calculation seam throws; subsequent historical reports retain the same sections/chart. Chart/metric selection handlers operate solely on retained models.

Exports do not write snapshots or plans. The large-case test explicitly verifies unchanged source snapshot bytes. Incompatible results cannot pass lifecycle exportability checks; no rejected snapshot is reinterpreted as Current. Save Failed/Memory Only/Pending are separate from currency and appear as such in provenance. Background persistence, atomic replacement/backups, Save As binding and generation guards are unchanged. All existing size/token/depth/numeric limits and payload encodings remain unchanged.

## 8. Verification

Commands use IntelliJ bundled Maven with the project-local repository and `-DargLine=-Xmx4g`.

- Baseline PDF/restoration/frozen-weighted selection: **18 passed**, zero failures/errors/skips, 16.613 s (`target/mc-2d-baseline.log`).
- Initial PDF/restoration/lifecycle selection: **68 passed**, zero failures/errors/skips, 22.924 s (`target/mc-2d-first.log`).
- Historical fidelity including explicitly enabled real 10,000-trial export, actual eight-mode restoration and PDF action tests: **18 passed**, zero failures/errors/skips, 19.604 s (`target/mc-2d-historical.log`).
- First complete suite: **3,252 run, 0 failures, 1 error, 44 skipped**, 8:52 (`target/mc-2d-full.log`). The sole error was an older single-person end-to-end assertion requiring stale exports to be disabled. Phase 2D intentionally requires them to remain available. It was replaced with historical-label and frozen-financial-data assertions, retaining the analysis-call count check. No financial expectation changed.
- A focused invocation begun before that source edit was compiled reported the same obsolete assertion; it is not counted as a passing verification (`target/mc-2d-final-focused.log`).
- Corrected final focused selection: **21 passed**, zero failures/errors/skips, **22.910 s** (`target/mc-2d-corrected-focused.log`). It includes all eight restored views with explicit chart-keyboard/metric-selection and invalid-input export checks, the new eight-mode historical PDF proof, the enabled real 10,000-trial export, the existing five-report preview, and the complete single-person household UI tests.
- Final full Maven suite: **3,252 run; 3,208 passed; 0 failures; 0 errors; 44 skipped. BUILD SUCCESS. Total 8:50**, completed October 21, 2026 at 07:59:53 EDT (`target/mc-2d-final-full.log`). This run includes the final keyboard/selection and invalid-input export tests and corrected single-person historical-export expectation.

The large historical test is opt-in in ordinary suites because its real capacity snapshot is an ignored generated prerequisite. It was explicitly executed successfully in this phase, not silently skipped as acceptance evidence. Existing optional capacity/private/reference/preview skips are retained. No failing test or financial assertion was disabled or weakened.

## 9. Phase 2E and manual acceptance

Remaining release work: final accumulated-diff audit, native original/restored/Outdated PDF checks for representative saved plans, final release readiness and environment-level checks. Recommended manual steps: rebuild/restart normally; export a Current restored standalone and paired result; edit relevant plan inputs; confirm Outdated export stays available with original dates/assumptions; inspect chart/table pages; cancel/fail a replacement and confirm historical export remains; inspect Save As clone provenance and incompatible-result blocking. No live JVM patching, automatic reruns or historical-input replacement is introduced.

Known limits: no new arbitrary saved-result pairing, no unsupported metrics/export modes, no native accessibility certification. Very large financial values retain the existing formatter/wrapper behavior; tests do not certify every possible 4,096-character decimal at every PDF table position. The 10,000-trial fixture must be regenerated using the existing opt-in production capacity test on a clean checkout before its opt-in PDF test can execute.

## 10. Git status

Initial branch `main`, HEAD `c11e7348b7f9b64b7b4f6b4c7c9daf8738388748`; 17 tracked modifications and 15 untracked earlier-phase files were preserved. Phase 2D adds only the scoped changes listed above. Final exact inventory and full-suite result follow. No staging, commits, pushes, resets or discards. Generated reports/images/logs remain ignored under `target/`, and local Maven artifacts remain ignored.

### Final working-tree inventory

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
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloResultLifecycleTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshotCodecTest.java
```

The complete accumulated tree contains 21 tracked modified and 18 untracked expected files, including this report. The staged diff remains empty. No domain-engine, financial golden/resource or pom.xml changes are present; no target artifacts appear in the untracked inventory.

Additional enabled preview verification used existing cached, independently calculated 5,000-world report inputs (`target/phase4d-pdf-preview/*-completed.json`) without regenerating simulations. All five reports passed page bounds, no-missing-glyph, no-blank-page and page-footer checks. Actual `fixed-page-1.png` and `comparison-longevity-page-6.png` were inspected at 120 DPI, including the complete frozen assumptions, mortality-table context, methodology and inflation limitations. Existing layout remained readable. Cached report timestamps remain original; these legacy report-only preview caches do not themselves include the new snapshot provenance wrapper. New wrapped historical reports are separately verified above.

## Final verification and review boundary

The full suite retained the existing 43 optional/private/reference/preview skips and added one opt-in large historical PDF case. That new case and the existing five-report rendered preview were explicitly enabled and passed in the 21-test corrected focused run. No additional exclusion was introduced. Frozen weighted compatibility, Couple SS/Weighted/Individual Weighted/Exhaustive snapshots, transactional persistence, seeded MC, financial goldens and wizard financial equivalence remain green.

Final `git diff --check` passes. `git diff --cached --stat` is empty. All untracked files were inspected by inventory and size: expected source/tests/reports only, maximum approximately 43 KiB, no generated artifacts or user financial data. The protected domain/resource/build diff is empty. Final accumulated inventory: 21 modified tracked files and 18 expected untracked files; pre-existing work retained. No staging, committing, pushing, resetting or discarding occurred.

Phase 2D implementation and automated/visual verification are complete for review. Native Phase 2D manual verification remains recommended as above. Do not begin Phase 2E without authorization.