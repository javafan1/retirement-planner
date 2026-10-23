# Monte Carlo Persistence and Comparison — Phase 2E Final Acceptance

## 1. Release recommendation and audit boundary

Audit date: October 22, 2026. Branch `main`, HEAD `c11e7348b7f9b64b7b4f6b4c7c9daf8738388748`. This is a final audit of the accumulated Monte Carlo Phases 1, 2A, 2B, 2C, 2D, 2D-1 and 2D-2 changes against HEAD, including untracked files. All seven reports, the actual changed implementation, tests, and existing Phase 9 infrastructure were reviewed. Historical reports retain their original stage-specific findings; later approved work supersedes earlier deferrals and the previously failing 2D-1 reproducer.

**Recommendation: ready for release review and explicit release authorization within the documented storage/runtime contracts.** The complete suite and enabled focused acceptance checks passed; no material regression or loss of accepted/durable results was found. No features, production changes, test changes, financial algorithms, dependencies or golden updates were introduced by Phase 2E. This report is its only release-file addition. Existing work was preserved. Release, staging and committing still require user authorization.

The user explicitly confirmed that **all native acceptance tests passed, including the versioned numeric compatibility correction**. This is user-confirmed manual evidence, not a claim that Codex independently performed those native tests.

## 2. Exact release inventory

Initial tree: 21 modified tracked files and 22 untracked files. All belong to these Monte Carlo phases; no unrelated pending file was identified. With this final report, the recommended release inventory is **44 files: 23 production sources, 13 tests, 8 reports**. Status prefixes below distinguish modifications from additions. The exact inventory is appended in section 12.

Production changes are limited to:

- Explicit immutable MC storage/request/result/presentation DTOs and pure mapping.
- Codec-only request factories and frozen-aggregate constructors; original execution constructors retain the original reducers.
- Eight explicit MC analysis kinds and scoped Phase 9 codec, version-policy and delegation integration.
- Application binding notifications and MC session/lifecycle/view integration.
- Historical MC export readiness and provenance/status presentation.
- Read-only versioned numeric compatibility for temporary analyzer settings.

Tests cover codecs/capacity, independent result equality, lifecycle/races, UI restoration, historical PDF rendering, numeric compatibility, existing-session behavior, MC delegation and the existing single-person end-to-end workflow. Existing assertions were updated only where the authorized behavior intentionally changed (historical retention, independent mode slots and exportability); financial expectations were not weakened. The 2D-1 reproducer remains in the release and now passes, with pre-correction V1 digest constants retained.

Diff review found no change to financial generators, random streams, trial scheduling, candidate algorithms, financial domain engines, tax/RMD/Roth/Medicare/SS/mortality calculations, plan schema, government/mortality resources, seeded financial goldens, dependencies or build configuration. The changes to the three existing app/montecarlo result/request classes are restoration factories/constructor overloads, not changes to the normal calculation path. Existing Phase 9 payload wire paths remain distinct.

### Exact exclusions

Do not include any ignored/generated artifacts, notably:

- The entire `target/` tree: compiled classes, Surefire XML/text, JVM dumps, Maven logs, test PDFs/PNG previews, report caches, capacity TSVs, temporary plans and generated snapshots.
- `target/mc-codec-capacity-*.json.gz` (nine generated capacity fixtures), `target/mc-production-codec-*.tsv`, `target/mc-capacity-*.tsv`.
- `target/mc-2d-pdf/`, `target/phase4d-pdf-preview/`, and prior lifecycle/wizard/Phase 9 preview outputs under target.
- `target/mc-2e-full.log`, final focused log, capacity replay evidence and `target/mc-2e-initial-files.csv` (audit evidence, not release sources).
- The entire `.codex-m2/` local Maven repository and all machine/application caches, real plan JSON files and saved user-analysis directories.

`git check-ignore` confirms representative PDFs, capacity snapshots, the audit log and local Maven repository are ignored. `git ls-files -- target .codex-m2` is empty. Every nonignored untracked file is an expected Java source/test or phase report; none is a generated binary, private plan or simulation output. No new secret, raw financial-result dump, arbitrary polymorphic deserialization or debug logging was found. Existing diagnostic logs use bounded structural paths/counts/lengths; existing commented controller prints and failure logging predate this phase.

## 3. Regression execution

Complete suite command (IntelliJ bundled Maven, project-local repository):

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-DargLine=-Xmx4g' test
```

Full-suite result: **3,256 total; 3,212 passed; 0 failed; 0 errors; 44 skipped. BUILD SUCCESS. Execution time 9:41**, completed October 22, 2026 at 17:14:50 EDT (`target/mc-2e-full.log`). JDK: Temurin 25.0.4.1+1-LTS, Windows/PowerShell, 4 GiB test heap. No test exclusions, assertion changes, seed changes, golden regeneration or disabled tests were introduced. Existing opt-in/private/reference-data conditions are reported as skips rather than passed verification. Expected injected storage failures in the test log are distinguished from actual test failures.

After the suite, an explicitly enabled focused run replays cached capacity evidence and historical/PDF preview cases; its results and exact settings are recorded below. This does not substitute cached results for the small newly calculated all-eight fidelity tests that execute in the ordinary suite.

## 4. Financial and statistical fidelity

| Supported kind | Preserved original result | Proof |
|---|---|---|
| COUPLE_MONTE_CARLO_FIXED | Ordered outcomes, all fourteen metrics, funding failures, annual distributions and deterministic reference/context | Codec/domain and restored-view tests |
| INDIVIDUAL_MONTE_CARLO_FIXED | Same single-person contract, including nullable spouse metrics | Same |
| COUPLE_MONTE_CARLO_LONGEVITY | Ordered lifetimes, full retained annual prefixes, terminal/failure values, cached population/distribution/terminal-year statistics | Same |
| INDIVIDUAL_MONTE_CARLO_LONGEVITY | Independent individual mortality inputs and all original single-person outcome/statistical data | Same |
| COUPLE_MONTE_CARLO_PAIRED_FIXED | Original paired A/B order, shared lifetime identity, both annual histories and terminal/failure results, cached paired summary | Same |
| INDIVIDUAL_MONTE_CARLO_PAIRED_FIXED | Corresponding single-person paired graph and common endpoint | Same |
| COUPLE_MONTE_CARLO_PAIRED_LONGEVITY | Matched sampled lifetime/market/inflation evidence, side-specific survivor policies and complete paired aggregates | Same |
| INDIVIDUAL_MONTE_CARLO_PAIRED_LONGEVITY | Individual matched-world graph, independent mortality controls, original request/report context | Same |

`MonteCarloSnapshotCodecTest.allEightFamiliesRetainExactEvidenceAndFrozenPdfInputsAcrossSeeds` runs actual small production calculations at seeds 417 and 918, captures original runtime results, round-trips through the production envelope, and compares reconstructed domain outcomes, settings, paired summaries, plan documents, mortality context and chart/PDF data independently of DTO equality. Exact BigDecimal equality includes scale. Cached means/distributions are restored, not recomputed using current assumptions or a later reducer.

Success probabilities retain the original denominators: complete fixed horizon or sampled lifetime trials divided by requested count; terminal statistics condition on successful completion; annual distributions condition on funded living prefixes. Failed/deceased outcomes are not silently inserted as zero financial samples. Failure records and warnings remain intact.

Paired calculations still generate one world per index and evaluate A/B against that same world. No arbitrary saved-run pairing, independent-simulation subtraction, new win metric or new statistical inference is introduced. Original seeds, source/model/reference identities, trial ordering, lifetimes, paired sides, percentile definitions, means and greater/equal/less counts are preserved. Caller-supplied sources require their own provenance; equal seeds alone do not prove identical supplied worlds.

The original result APIs do not retain every generated market path or full annual account/tax Projection for every trial. Persistence preserves their complete retained graph, not invented additional detail. Fixed standalone retains annual distributions and compact trial metrics rather than individual annual histories; longevity/paired retain their existing annual histories. Frozen configured plans and historical report details are retained separately.

Pure restoration adapters have no financial execution dependencies. Actual JavaFX restoration tests inject throwing simulation executors/work and controller projection methods; restored charts, keyboard selections, tables and exports succeed without calling them.

## 5. Capacity, bounds and security

The Phase 2B independently calculated fixture matrix is retained under ignored target artifacts. All eight 5,000-trial cases fit. The real couple fixed paired 10,000-trial/50-year case retains **one million annual observations**, with original measurements:

| Measurement | Production snapshot |
|---|---:|
| Raw JSON bytes | 20,695,338 |
| Gzip bytes | 7,613,054 |
| JSON tokens | 1,732,788 |
| Maximum numeric-token characters | 303 |

These are cached prior actual calculation measurements, not newly simulated 10,000 trials during this release audit. Phase 2E codec replay revalidates the actual cached bytes using current code and reconstructs the existing result models without financial recalculation. Fresh encode/decode/rebuild timings from replay are recorded below; historical simulation timings are not relabeled as new measurements.

Unchanged document guards: 33,554,432 decoded bytes; 16,777,216 compressed bytes; 2,000,000 JSON tokens; 100,000 array containers; depth 80; string length 256,000; numeric token length 4,096. The array-container check is not misrepresented as an arbitrary expansion guarantee. MC additionally bounds trials to 100,000, individual histories to 1,000 years, and total logical annual observations to 1,500,000 across both sides, even when encoded values are shared. Decimal precision/scale/integer expansion are independently bounded against short-exponent allocation attacks.

History references are confined to side B -> side A within the same pair, source 0, with an exactly equal prefix including BigDecimal scale. No cross-trial references/cycles exist. Counts, origins, prefix lengths, endpoints, membership and aggregate populations are validated before domain-map expansion. Strict MC integer decoding rejects fractional references/counts rather than truncating. Domain maps are constructed directly; no unbounded expanded JSON document is materialized.

Unknown fields/types/versions, duplicate/trailing JSON, truncation, malformed references, invalid digests and wrong binding are rejected. Payload checksums, UUID/kind/shape/path binding, timestamp consistency, bounded streaming encode, gzip/decode checks and MC-only parser configuration remain enforced. Existing Phase 9 atomic sibling temporary-file verification, replacement, backups, recovery, ordering, retry, controlled queue and shutdown contracts are reused unchanged. Incompatible files are preserved, not silently deleted or described as Current.

Capacity is not universal: extreme plans/trials/horizons may exceed existing physical/logical budgets and fail safely. No limit increase or data omission is recommended for release.

## 6. Accepted-result lifecycle and compatibility

Session cancellation/revision/generation/admission gates remain authoritative. Only eligible completion reaches the store. A normally completed all-funding-failed MC experiment remains valid evidence; fatal/canceled/obsolete work cannot replace the last accepted result. Historical data remains visible through replacements and their failures/cancellation. Computation acceptance is distinct from durable save: Pending/Save Failed/Memory Only are not falsely labeled Saved.

Immutable accepted events retain original plan UUID, file and kind. Async decode/reconstruction/currency runs off FX; attach/update runs on FX. Binding/view/edit guards reject obsolete callbacks. A running replacement cannot be overwritten by an older load; failed/canceled job status is preserved when historical data attaches. Dialog closure does not abandon accepted persistence writes. Mode changes use independent slots without financial execution.

Save As uses independent identity, original provenance and copy-if-empty cloning, then reassesses destination inputs. Retry Save uses the existing accepted completion, not simulations. Failed writes preserve the prior durable snapshot. Unsaved/unbound plans remain Memory Only; no plan is autosaved or silently promoted. The established first-save policy requires saving then rerunning for durable publication of an earlier memory-only result.

Versioned numeric compatibility remains narrow: stored V1 fingerprints are verified from original frozen inputs, then frozen/current temporary settings are compared under V2. Only the allowlisted temporary decimal rates and mortality factors normalize numerical formatting on fresh comparison nodes. Exact saved values/scales, source digest and all plan/baseline fields remain untouched. Null/absence/invalid input remains distinct. Formatting-only edits remain Current; real numeric, seed, survivor, current-plan or baseline changes remain Outdated; reverting effective inputs returns Current. Unknown/unverifiable contracts remain conservative.

## 7. Historical PDFs and visual evidence

The pure PDF adapter reads completed runs, never the live plan. Historical wrappers retain the existing chart/sections/context and add original start/completion time, export-time currency/durability and scenario provenance. Current and compatible Outdated results export normally; incompatible results cannot export as valid. Export time differs from original analysis completion. Original/restored report data and extracted text match; deterministic-time render comparison checks every page's RGB pixels. Outdated wrappers preserve all original financial chart/table data and add a prominent notice and running header.

Existing PDF tests count calculation calls; actual restored-view tests throw on calculation access, including chart/table selection and PDF capture. A real cached 10,000-trial paired result is also exported without resimulation. The source snapshot remains byte-identical after export. Histories are never recalculated from current plan assumptions to produce a PDF.

Phase 2D documented actual rendered inspection of standard, longevity, individual, paired and 10,000-trial Outdated reports. Phase 2E also reviews representative regenerated/currently available page images; actual Phase 2E inspections include `target/mc-2d-pdf/COUPLE_MONTE_CARLO_FIXED-0.png`, `COUPLE_MONTE_CARLO_LONGEVITY-1.png`, `large-0.png`, `large-1.png`, and `COUPLE_MONTE_CARLO_PAIRED_LONGEVITY-2.png`. The final two were inspected after the enabled focused run regenerated them. No visible clipping, missing legend/bands, broken population-table continuation or timestamp/status ambiguity was observed in those inspected pages. Existing blue percentile bands/median, orange deterministic line, dark zero reference, SS/Roth/RMD context, legends, financial tables, population warnings, methodology and page breaks remain intact. Large-value formatting and long tables remain under the existing renderer; no unrelated PDF redesign occurred.

## 8. Native acceptance

The user confirmed all native acceptance tests passed, including restart restoration, historical retention, Current/Outdated and the numeric compatibility correction. This supersedes earlier reports' pending native checklists for release acceptance. Codex independently verifies automated source/tests and rendered PDF artifacts only; no independent Narrator, Windows high-contrast, mixed-DPI or OS-level certification is claimed. The exact original live mismatch was not forensically decomposed by Codex; the proven correction and user-confirmed acceptance are kept distinct from that unmeasured causal detail.

## 9. Remaining limitations and risks

- Last-success snapshot per UUID/shape/kind only; no historical browser, arbitrary saved-result paired comparator, automatic optimizer or additional modes.
- Session-only next-run settings are not silently restored over user controls; initialization may differ from historical inputs and legitimately produce Outdated until matched.
- Plan/baseline scale sensitivity is intentionally preserved. Formatting equivalence applies only to approved temporary numeric settings; future settings require explicit dependency/version review.
- First-save Memory Only behavior and ordinary application drain/failure UI remain existing contracts. External filesystem copies with duplicate UUIDs remain subject to existing identity protection and previously documented operational limitations; application Save As is the supported independent-copy operation.
- Cached capacity fixtures are generated prerequisites, not committed binary fixtures. Clean checkouts can regenerate through opt-in production tests; ordinary suites intentionally skip full expensive capacity regeneration.
- Capture validation can reject an out-of-budget graph before accepted completion; this differs from a later disk-save failure of an already accepted result. The previous accepted/durable result remains protected, but this audit does not promise historical UI publication of every unsupported oversized computed graph. No new representation, limit increase or broader capture-failure behavior is approved here.
- Storage limits bound supported snapshots, not all possible domain trial-count/horizon combinations. Large cases fail visibly, retain prior durable data and support retry; this audit does not approve larger budgets.
- Existing baseline model reference-sharing semantics are unchanged. This work freezes captured candidate inputs independently; it does not redesign ordinary baseline editing semantics.
- Prior native accessibility limitations and inability to certify every extreme 4,096-digit table cell remain documented. No new blocking regression/data-loss risk has been identified in the reviewed scope; final recommendation depends on completed verification below.

## 10. Recommended commit message and release boundary

Recommended subject:

```text
Persist Monte Carlo results and preserve historical comparisons
```

Suggested body: Add versioned, bounded snapshots for all eight Monte Carlo variants using the existing atomic store. Retain last-success results across reruns and sessions, restore frozen charts/comparisons/PDFs without financial work, and reassess legacy inputs with narrowly scoped numeric compatibility. Include capacity, lifecycle, compatibility and PDF fidelity tests and acceptance reports.

Include only the exact 44-file source/test/report inventory. Exclude all paths listed in section 2. No staging or commit is performed by this audit. Review and explicit release authorization remain with the user.

## 11. Updated development roadmap

1. Review this acceptance report and authorize the release/commit separately if satisfied.
2. Monte Carlo persistence/comparison scope is complete after acceptance; no further feature work is started here.
3. Remaining Individual Social Security Only and Individual Deterministic persistence are still separate deferred work requiring authorization and their own fidelity/acceptance gates.
4. Couple Quick persistence remains deliberately skipped; existing Quick functionality/prerequisites are preserved.
5. Any future arbitrary saved-run comparisons, history browsing or broader recovery/identity improvements require a new design approval; do not imply current paired statistical evidence supports independent-run pairing.

## 12. Exact Git release inventory and final verification

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
?? MONTE-CARLO-PERSISTENCE-COMPARISON-PHASE-2E-FINAL-ACCEPTANCE.md
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

### Final enabled acceptance run

**117 total; 116 passed; 0 failures; 0 errors; 1 skipped. BUILD SUCCESS. Execution time 36.658 s**, completed October 22, 2026 at 17:15:56 EDT (`target/mc-2e-focused-replay.log`). Selection:

```text
MonteCarloSnapshotCodecTest,MonteCarloHistoricalPdfFidelityTest,
MonteCarloNumericCompatibilityTest,MonteCarloRestoredFingerprintInvestigationTest,
MonteCarloRestorationViewTest,MonteCarloResultLifecycleTest,
MonteCarloPdfExportTest,MonteCarloPdfPreviewTest,
LegacyWeightedSnapshotCompatibilityTest,TransactionalPlanPersistenceTest
```

Explicit flags: `-Dmc.codec.replay=true -Dmc.pdf.historical.large=true -Dmontecarlo.pdf.preview=true`, with the same local Maven repository and 4 GiB heap as the full suite. The single skip is the **full production capacity regeneration** test, deliberately not enabled; capacity was byte-exact replay of nine actual prior calculated snapshots. This is not a skipped failure or an unexecuted large-result proof. Small original-versus-restored calculations still run normally in the selected codec tests. No test was changed or weakened by this audit.

This run checks all eight fidelity families; decimal scale/order/optionality; malformed references/expansion/coercion/digest/schema guards; original V1 digest constants and V2 compatibility; actual FX restoration and async guards; historical export without financial work; the cached real 10,000-trial PDF; frozen legacy weighted bytes; five rendered PDF previews; and transactional save/recovery fault injection. Full-suite coverage additionally includes existing Couple SS, Couple/Individual Weighted and Exhaustive persistence/restoration, seeded MC/financial goldens, identity and Save As tests. Expected injected-exception traces are not failed tests.

### Fresh codec-only replay measurements

All nine raw/gzip byte sequences re-encoded exactly and restored under current code. Measurements below are new **codec replay timings**, not new simulations. Units are milliseconds; encoding includes gzip. Single observations on Temurin 25.0.4.1+1-LTS, Windows, 4 GiB heap, not warmed performance guarantees.

| Shape | Mode | Paired | Trials | Raw bytes | Gzip bytes | Encode ms | Decode ms | Rebuild ms |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Single | Fixed | No | 5,000 | 4,657,725 | 852,693 | 135 | 142 | 6 |
| Single | Fixed | Yes | 5,000 | 9,574,101 | 3,389,813 | 453 | 243 | 54 |
| Single | Longevity | No | 5,000 | 3,275,799 | 936,702 | 126 | 91 | 37 |
| Single | Longevity | Yes | 5,000 | 6,023,631 | 1,828,036 | 240 | 170 | 24 |
| Couple | Fixed | No | 5,000 | 5,284,450 | 1,005,017 | 147 | 139 | 3 |
| Couple | Fixed | Yes | 5,000 | 10,359,990 | 3,810,793 | 492 | 282 | 30 |
| Couple | Longevity | No | 5,000 | 3,604,060 | 1,126,222 | 139 | 125 | 17 |
| Couple | Longevity | Yes | 5,000 | 6,677,018 | 2,172,945 | 268 | 178 | 27 |
| Couple | Fixed | Yes | 10,000 | 20,695,338 | 7,613,054 | 943 | 645 | 57 |

Evidence: `target/mc-production-codec-replay.tsv`. The unchanged bytes preserve the original token/numeric counts; codec structural screens execute again. The 10,000-trial document uses about 87% of the token budget, so no claim is made about longer/larger configurations. Prior capacity generation observed 663,990,304 used heap bytes and 737,390,624 summed heap-pool peak bytes for that case; those are historical JVM observations, not a newly measured OS peak/RSS or exact retained-result size. No fresh peak-memory measurement is invented here. Successful replay under the established heap and the unchanged logical expansion bounds are the present evidence.

The five existing cached report previews were re-exported without simulations: 3/7/5/6/7 pages for fixed/longevity/fixed comparison/longevity comparison/late-longevity focus. All text-bounds, missing-glyph, nonblank-page and page-footer checks passed. Export-only times were 3.7604 / 9.5565 / 10.8837 / 8.2691 / 11.7545 ms, respectively (`target/phase4d-pdf-preview/export-timings.txt`). These report-only caches predate the historical provenance wrapper; new wrapped original/restored/Outdated PDFs are independently checked by the all-eight historical tests.

### Git and preservation result

The required `git status`, `git diff --check`, `git diff --stat` and `git ls-files --others --exclude-standard` were executed. Final accumulated inventory: **21 tracked modified, 23 untracked; 44 exact release files**. Tracked diff statistics are **21 files, 460 insertions, 70 deletions**; untracked additions are intentionally not counted by `git diff --stat`. The staged diff is empty. HEAD remains `c11e7348b7f9b64b7b4f6b4c7c9daf8738388748`.

SHA-256 content checks against the audit-start manifest confirm **zero content changes to all 43 pre-existing modified/untracked files**. Phase 2E added only this report; Maven regenerated ignored outputs. No unrelated work was overwritten or discarded. Protected domain/reference/golden/build paths have an empty diff. No sensitive plan, binary snapshot, generated PDF/PNG, local repository or log appears in the nonignored release inventory. Git may print existing LF/CRLF conversion notices; `git diff --check` exits 0 with no whitespace errors.

No staging, commits, pushes, resets or discards were performed. This audit does not authorize or perform a release. Stop for user review and release authorization.
