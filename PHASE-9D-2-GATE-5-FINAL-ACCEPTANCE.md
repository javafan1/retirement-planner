# Phase 9D-2 Gate 5 — Final acceptance and release audit

## Executive summary

Final audit of the complete working tree against HEAD for Couple Deterministic Exhaustive persistence. No production or test code changes were necessary in Gate 5; this gate adds only this report. Existing work from every preceding gate is preserved.

The implementation supplies complete accepted-result persistence, asynchronous restart restoration, exact retained financial graphs, Current/Outdated historical viewing/export, bounded versioned lossless interning, recovery/retry and independent plan slots. The user has confirmed native saving and restart restoration of the previously failing real analysis. Gate 4's warning and large-value text-fitting corrections are included in this audit and regression run.

**Release recommendation: accept Phase 9D-2 for review and a separately authorized commit/release.** The complete Maven regression suite passed, compatibility/fidelity/recovery/PDF checks passed, and no material regression or unresolved Phase 9D-2 data-loss risk was found. Native confirmation is user-provided; automated financial/capacity/PDF evidence uses synthetic fixtures, never the user's plan.

## Repository baseline and scope audit

Branch `main`, HEAD `4fddd4ee1eaebc8354b009b77a381459495d665c`, up to date with `origin/main`. Initial state: seven modified tracked files, twenty-five untracked files, empty index. Read AGENTS.md and all eight preceding Phase 9D-2 reports and inspected the accumulated source/diff, tests and file inventory.

| Protected area | Audit result |
|---|---|
| Projection, financial, withdrawal, RMD, Roth and Medicare algorithms | No changed files |
| SS benefit calculations, search/candidate generation/ranking, mortality | No changed engine/model files |
| Federal/state tax calculation implementations and reference data | No changed files |
| Financial golden resources, seeded expectations | No changed test resources or seeds |
| Dependencies / Java / Maven / build settings | `pom.xml` and configuration unchanged |
| Other application navigation / wizard / unrelated UI | No modifications |
| Quick persistence | Not implemented; Quick calculation/prerequisite behavior remains unchanged |
| Remaining individual persistence modes | Not implemented |
| Other snapshot formats | Weighted and Couple SS logical/wire compatibility retained; exact frozen tests included |
| Durable plan identity/schema/persistence semantics | No new changes in this phase; existing UUID/file binding and Save As contracts reused |
| Logging/debug instrumentation | Approved bounded structural-size diagnostics and calculation-factory test seams only; no live instrumentation, raw payload or financial-value logging added |

The one changed domain source is `IncomeSourceAttribution`: an explicit historical `AccountReference.restore(identity, frozenParticipantFacts)` factory and constructor plumbing. Existing capture still uses the account's facts; equality/hash/identity semantics and financial eligibility logic are unchanged. This enables faithful historical reconstruction without overwriting an account's current participant facts. Its focused frozen-facts and identity tests are retained.

The common persistence helper adds an Exhaustive replacement/load guard. Other modes do not call that guard; existing weighted and Couple SS behavior remains covered. Streaming digest generation emits the same canonical logical bytes while avoiding a large intermediate byte array. Exhaustive v2 is explicitly dispatched; no unsafe arbitrary polymorphism was introduced.

## Complete Phase 9D-2 file inventory

All initial modified/untracked files belong to the accumulated Phase 9D-2 work; no unrelated initial changes were identified or discarded. Paths below use the package root `com/daviddunn/retirementplanner/`.

### Seven modified tracked production files

| Relative to `src/main/java/` package root | Purpose |
|---|---|
| `app/export/IntegratedAnalyzerPdfExporter.java` | Preserve cell geometry/colors while fitting unusually long monetary display text |
| `app/socialsecurity/results/AnalysisCompatibilityPolicy.java` | Exhaustive-specific effective-input assessment for historical Save As clones |
| `domain/income/IncomeSourceAttribution.java` | Frozen attribution facts restoration with detached account identity |
| `persistence/AnalysisSnapshotCodec.java` | Allowlisted Exhaustive payload dispatch; v2 interning and legacy read; exact trees, streamed unchanged digest and shared bounded component codec operations |
| `ui/socialsecurity/IntegratedAnalyzerReportAdapter.java` | Preserve original baseline and retained-detail warnings in deterministic PDFs |
| `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java` | Worker capture/eligible completion, historical restoration and status/selection/PDF integration |
| `ui/socialsecurity/WeightedResultPersistence.java` | Exhaustive slot/status integration, replacement/load race guard and frozen SS source metadata access |

### Six new production files

| Relative to `src/main/java/` package root | Purpose |
|---|---|
| `app/socialsecurity/results/ExhaustiveSnapshot.java` | Complete immutable result/context/ranking/reference DTO and checked reconstruction |
| `app/socialsecurity/results/RetainedProjectionSnapshot.java` | Explicit complete annual/account/income/state/Medicare/Roth/SS reconstruction graph |
| `persistence/RetainedProjectionCodec.java` | Bounded standalone retained-projection fidelity codec |
| `persistence/AnalysisSnapshotSizeDiagnostics.java` | Privacy-safe bounded byte-contribution/count diagnostics |
| `persistence/ExhaustiveSnapshotInterning.java` | Deterministic typed v2 tables, references and logical expansion guards |
| `ui/socialsecurity/ExhaustiveResultSnapshots.java` | Pure frozen-context capture/restoration and historical SS reference mapping |

### Eleven new test/support files

Relative to `src/test/java/` package root:

1. `domain/projection/RetainedProjectionCodecTest.java` — exact getters/fields, account identity, supported accounts/states, optionality, long decimals and invalid references.
2. `domain/projection/ExhaustiveSnapshotTest.java` — complete result/order/rank/context/payload/repository fidelity, failure/tie data and compatibility.
3. `domain/projection/ExhaustiveCapacityInvestigationTest.java` — precision-driven capacity experiment and retained-primary/retry fault injection; artificial duplication explicitly labeled.
4. `domain/projection/ExhaustiveInterningAuditTest.java` — proves that the old synthetic reuse was not a realistic financial fixture; keeps a legacy-wire comparison baseline.
5. `domain/projection/RealisticExhaustiveCapacityProofTest.java` — opt-in actual 5,184-candidate search and cached no-recalculation measurements.
6. `domain/projection/ProductionExhaustiveInterningTest.java` — legacy/new wire, exact decimal boundaries, actual state/account/attribution graph and independent identity fidelity.
7. `domain/projection/ExhaustivePdfFidelitySupport.java` — bridge to existing independent annual-field/getter/account-identity oracle.
8. `persistence/AnalysisSnapshotSizeDiagnosticsTest.java` — byte accounting, privacy and numeric guards.
9. `persistence/ExhaustiveSnapshotInterningTest.java` — determinism, type/scale distinctions, malformed table/reference and excessive expansion rejection.
10. `ui/socialsecurity/ExhaustivePersistenceTest.java` — eligible success, restart, plan-switch/rerun/close races, cancellation/failure retention, retry, clones and corruption.
11. `ui/socialsecurity/ExhaustiveHistoricalPdfFidelityTest.java` — original/restored/Outdated report/raster parity, frozen SS context, incompatible export blocking, warnings and long-value fitting.

### Nine reports, including this gate

- `PHASE-9D-2-EXHAUSTIVE-PROJECTION-CODEC.md`
- `PHASE-9D-2-GATE-2-EXHAUSTIVE-SNAPSHOT.md`
- `PHASE-9D-2-GATE-3-EXHAUSTIVE-INTEGRATION.md`
- `PHASE-9D-2-GATE-3A-CAPACITY-INVESTIGATION.md`
- `PHASE-9D-2-GATE-3B-LOSSLESS-INTERING.md` — original requested filename retained, including its spelling.
- `PHASE-9D-2-GATE-3B1-REALISTIC-CAPACITY.md`
- `PHASE-9D-2-GATE-3B2-PRODUCTION-INTERNING.md`
- `PHASE-9D-2-GATE-4-PDF-FIDELITY.md`
- `PHASE-9D-2-GATE-5-FINAL-ACCEPTANCE.md`

## Full regression suite

Executed the complete suite without `-Dtest`, exclusions, seed changes or golden updates:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-DargLine=-Xmx8g' test
```

Environment: Windows, Temurin OpenJDK 25.0.4.1, JavaFX 25, established project-local Maven repository and 8 GiB test-JVM maximum heap. Log: ignored `target/9d2-gate5-full.log`.

**BUILD SUCCESS**, finished 2026-10-20 16:58:29 America/New_York:

| Total | Passed | Failed | Errors | Skipped | Maven execution time |
|---:|---:|---:|---:|---:|---|
| 3,138 | 3,099 | 0 | 0 | 39 | 8 minutes 43 seconds |

No suite exclusions were supplied. Of the 39 skips, 35 are existing conditional/manual benchmark, preview or private-audit cases and four are the pending Mississippi worksheets described below. The full-search capacity case is additionally executed in cached no-recalculation mode after the suite; this does not alter the authoritative full-suite totals.

The normal suite keeps existing preview/benchmark/private-audit opt-in conditions. The full 5,184 financial search measurement is deliberately opt-in rather than repeatedly executed by every normal suite run; its completed graph is separately verified/reused. In addition, four existing Mississippi worksheet cases are assumption-skipped pending authoritative guidance: MS11 IRA-to-Roth after eligibility, MS12 before eligibility, MS21 employer-plan Roth conversion and MS27 the 2031 contingent reduction. Their inputs have no numeric expected tax; no value was invented or assertion weakened to turn them green. These pre-existing skips are outside Phase 9D-2. No test was disabled or assertion changed in Gate 5. Expected fault-injection stack traces and JVM JavaFX/native-access/deprecation warnings are not silently counted as test failures.

## Snapshot compatibility and safety

| Contract | Evidence |
|---|---|
| Frozen couple/individual weighted bytes | `LegacyWeightedSnapshotCompatibilityTest`: exact original gzip/raw bytes, every numeric token's value/scale, canonical digest and re-encoding |
| Couple SS Only | `CoupleSocialSecurityPersistenceTest`, codec/presentation/job coverage; independent restored current prerequisite behavior retained |
| Couple/individual weighted | `WeightedRestorationTest`, `IndividualCompletionPersistenceTest`, weighted codec, numeric/hotfix, PDF/report and lifecycle tests |
| Legacy Exhaustive | `ProductionExhaustiveInterningTest.legacyExhaustiveBytesRemainReadableWithUnchangedLogicalDigest` and the preserved completed legacy-format synthetic calculation artifact |
| Interned Exhaustive | Exact full DTO and independent domain graph checks; deterministic re-encoding, repository save/verification/reload and all kind/UUID/digest guards |
| Strict validation | Missing/unknown/duplicate fields, trailing JSON, bad indices/types/versions, duplicate/unused intern values, nested refs/cycles and expansion guards |
| Atomic save/recovery | Repository/transactional tests exercise precommit stages, verification failures, primary/backup preservation, recognized backup recovery, unknown schemas, unsupported atomic move, permissions and binding |

Frozen gzip SHA-256 values remain:

- Couple: `fb4300180df4698722cbdde8b46359d21b7f0759736b3c177dc4d2e1458cc80a`
- Individual: `f064faeeb8f061a5131d34af068816d6dcf81f15203526ee4ccaa17541d15a07`

Envelope schema and logical digest meaning remain unchanged. New Exhaustive writes use `exhaustiveRepresentation: 2` inside the allowlisted payload; old Exhaustive payloads remain readable. Weighted and Couple SS representations/digests remain their existing form. Older application builds are not forward-compatible with new Exhaustive v2 results and must not be used to rewrite them; unsupported files are protected rather than silently reinterpreted.

Unchanged physical limits: **33,554,432 decoded bytes; 16,777,216 compressed bytes; 4,096 numeric characters; 2,000,000 tokens; 100,000 array containers; depth 80; string length 256,000**. The existing scanner's array counter counts array containers, not individual items; interning additionally caps reconstructed collection lengths at 100,000.

Exhaustive logical guards: total table values 100,000; references 100,000; expanded logical tokens 8,000,000; aggregate field-name/scalar text 128 Mi characters. Depth and collection checks also apply before DTO/domain allocation. No expanded legacy JSON byte document is generated to restore results. These bounded work guards do not promise every possible graph fits every configured application heap.

## Exact financial fidelity and no recalculation

The Gate 1 oracle compares every original/restored private domain field and public annual financial getter, not merely a DTO against its own recapture. Exact BigDecimal equality includes scale. Coverage includes all eleven supported account types, owners/joint accounts where valid, inherited facts and opening RMD history, identity links across years/source attribution/conversion destinations, frozen participant facts and missing optional data.

The complete Exhaustive tests compare every occurrence and election, generation and successful ranking orders, competition ranks/ties, all fourteen financial metrics and differences, baseline, retained/unavailable detail distinction, warnings/failures, runtime, frozen groups/selection/report and historical SS provenance. Actual state variants, Michigan source eligibility details, federal/state taxes, actual RMD/Roth execution, Medicare/IRMAA and cash settlement are covered. Long exact numeric tokens at and around the configured boundary remain tested.

`RetainedProjectionSnapshot` reconstructs explicitly allowlisted detached account instances and checked projection-local references. Orphan references are rejected; accounts are not repaired, reassigned or matched by name against the live plan. Unsupported domain variants fail safely instead of dropping fields.

Restore paths invoke constructors/mappers/validation, not projection, search or mortality/SS optimizers. UI tests install throwing calculation factories, assert no financial job generation on restore, and retain exact original data through selection and export. The PDF source graph is compared before presentation formatting. Current/Outdated status uses existing mode-specific effective-input fingerprints; irrelevant mortality/valuation edits do not invalidate deterministic results.

## Lifecycle and failure recovery

Reviewed worker snapshot capture and eligibility callback in `runExhaustiveSearch`: request plan is isolated using `RetirementPlanScenarioCopyService`; immutable completion carries original plan/file/kind/run/context. Only an eligible accepted success reaches the application-owned store. A partial completed search with successful candidates retains every structured failure; all-failed/canceled/stale/rejected runs cannot replace historical success.

`ExhaustivePersistenceTest` covers:

- Restart restoration and Current → Outdated → Current reversion.
- Accepted off-FX capture, Memory Only and accepted writes surviving dialog close.
- Verification failure, previous durable bytes and Retry Save without recalculation.
- Canceled/failed/obsolete/all-failed replacement retention.
- Plan switching or closing while loading and a newer accepted replacement beating a delayed old load.
- Independent original/Save As clone restoration and corruption preservation.
- Partial success and independent mode slots.

Application-owned store/repository/controller tests additionally retain write ordering, plan binding, successful-save identity, shutdown/drain contracts and backup handling. These paths are reused, not replaced with a second scheduler/store. Incompatible snapshots stay protected. Unsaved/legacy-without-saved-binding plans remain explicitly Memory Only; no autosave or unattached recovery browser is introduced.

## Capacity, performance and security evidence

Three distinct evidence categories must not be conflated:

1. **Early artificial size/precision fixtures:** one copied financial outcome over many elections; useful for serialization stress/fidelity, not real financial capacity. The audit explicitly retains this limitation.
2. **Fully calculated realistic fixture:** actual production search computed 5,184 independent candidates, all successful, normal twenty retained projections, forty-year horizon, six accounts, taxes/spending/growth/RMD/Roth/Medicare. The repeated values were discovered after calculation, not supplied as substitutes.
3. **Native user case:** original log measured only 44,692,375 raw bytes. The user confirmed optimized save and restart restoration passed. Its internal component composition was never measured here.

Approved Gate 3B-2 measurement for category 2:

| Measure | Existing logical form | Production v2 |
|---|---:|---:|
| Raw bytes | 20,479,403 | 2,903,992 |
| Gzip bytes | 837,669 | 133,155 |
| Maximum numeric token | 174 | Same exact values |
| Distinct fourteen-metric/difference records | 81 / 81 | Same |
| Table values | Not applicable | 162 metrics/differences + 1 account array + 2 annual arrays |
| References / maximum fan-out | Not applicable | 10,410 / 64 |

Fresh financial search took 32.237 seconds. Final cached measurement: production encode 346.606 ms, decode 151.055 ms, pure reconstruction 5.955 ms; these are single-run observations, not guaranteed latency. Sampled heap peak during fresh search plus experiments was 517,454,352 bytes with a 4 GiB heap; cached measurement peak 323,687,744 bytes. These are sampled used heap, not RSS or isolated incremental codec allocation.

Gate 5 reused the reproducible completed synthetic graph under ignored `target/gate3b1-full-calculated.json.gz` rather than repeat expensive calculations unnecessarily. The separate measurement run passed: **one test, zero failures/errors/skips**, Maven 13.843 seconds, with explicit `FULL_UNIVERSE_CACHE reused=true financialCalculations=0`. Current production sizes remain **2,903,992 raw / 133,155 gzip bytes**, tables `[162, 1, 2]`, 10,410 references and maximum fan-out 64. It verifies exact snapshot equality, independent repository reload and restored financial graphs.

Gate 5 single-run timings: encode **385.761 ms**, decode **265.393 ms**, pure reconstruction **7.445 ms**. Sampled peak used heap **495,836,784 bytes**, maximum heap 4,294,967,296; the sampler also covers comparison-tree experiments and is not isolated native memory or a guaranteed peak. Evidence: ignored `target/9d2-gate5-capacity-reuse.log`. The command adds `-Dretirementplanner.exhaustiveCapacityProof=true -Dretirementplanner.reuseCapacitySnapshot=true -Dtest=RealisticExhaustiveCapacityProofTest -DargLine=-Xmx4g` to the same bundled Maven/project-local-repository invocation. The source test can calculate the fixture when intentionally run without cache reuse. No real-world private snapshot or hour-long weighted search was needed.

Malformed/adversarial intern tables are exercised by the normal suite. Exact serialization comparison, typed reference lookup, preflight logical work checking, payload SHA, strict Jackson reading and the unchanged atomic repository remain enforced. No limit was raised and no retained projection/candidate was removed to fit a fixture.

## Historical PDF verification

Gate 4 tests run again in the final suite. The fresh actual 324-candidate/all-81-age-pairs fixture, with exact ties and twenty retained details, produces original and restored Current reports for all four deterministic display metrics. Full report financial data, all-page extracted text and page pixels match when export time alone is normalized by the comparison test. Actual production export timestamps remain actual export times.

After live assumptions/names/SS values change, Outdated PDFs retain the original metrics, baseline, rankings, chosen retained strategy, frozen report assumptions, source SS comparison and original completion time. Existing historical notices identify the result. Incompatible export is blocked. Exporting/selection does not invoke an analysis or modify plan/snapshot files.

Gate 4 corrections retained: baseline/selected-detail warnings are included; long cell values fit the unchanged grid using bounded font fitting. Standard-size values retain their existing rendering. The report still shows aggregate metrics and detail-retained status rather than printing annual account tables; complete annual source graphs are verified independently and preserved in the snapshot.

Gate 4 rendered evidence was inspected as actual PDFBox raster output, including complete maps/palette, selection/optimal markers, ranks/groups, frozen assumption pages, metrics/warnings/details, long table continuation and a twenty-one-page large-value fixture. Generated artifacts remain ignored under `target/gate4-exhaustive-pdf/`. This is not native printer/DPI/Narrator certification.

## Native acceptance confirmation

The user explicitly states that native application acceptance passed, including successful persistence and cross-session restoration of the real Exhaustive analysis that originally exceeded the raw limit. That satisfies the outstanding original production-case acceptance; the earlier investigation reports remain historical records of what was known at their dates.

This audit did not inspect the user's financial file, measure the live snapshot's component composition, hot-patch a JVM or independently repeat that native run. Native PDF printing and assistive-technology checks were not asserted as performed.

## Remaining limitations and roadmap

- Very large or unusually unique graphs may still reach existing physical/logical budgets; safe refusal preserves prior durable data. No universal compression ratio or unbounded-magnitude PDF guarantee is claimed.
- Future financial DTO/account/state variants require explicit codec/version/compatibility review; unsafe reconstruction is refused.
- Historical result inputs are not automatically copied into current plan/session controls. Unsaved plans retain the approved Memory Only contract.
- Platform-specific long-name/print/DPI/accessibility stress checks remain manual as appropriate; they are not grounds to change financial semantics or storage budgets silently.
- **Remaining roadmap:** Individual Social Security Only persistence/restoration, then Individual Deterministic Integrated persistence including retained projections, Break-Even/report context and historical PDF fidelity. Audit and authorize each separately, reusing existing infrastructure.
- Couple Quick Comparison persistence stays intentionally skipped; all existing Quick functionality and its current SS-only prerequisite stay intact.
- No history browser, additional analysis types or unrelated development is authorized by this release audit.

## Git readiness and recommended commit

Recommend including all thirteen accumulated production files, eleven test/support files and nine Phase 9D-2 reports in a reviewed Phase 9D-2 change. Existing tracked weighted frozen resources/goldens remain untouched. No unrelated working-tree files were found.

Recommended commit message (not executed):

```text
Persist couple deterministic exhaustive analysis results

Preserve complete ranked outcomes, retained projections and frozen report context.
Restore current and outdated results with bounded lossless snapshot interning,
atomic persistence and retry/plan-binding safeguards. Verify legacy snapshots,
financial graph fidelity and historical PDF exports.
```

Do not include `target/**` (PDFs, PNGs, benchmark snapshots, Surefire output, Maven logs), `.codex-m2/**`, personal plan files, local application analysis-result directories, IDE state or other unrelated machine artifacts. These are not present in the untracked release inventory. All untracked files inspected are small UTF-8 Java/Markdown files; the largest before this report is under 29 KiB, not a generated financial graph. Synthetic fixture values are labeled; no user financial data or secrets were found.

`git diff --stat` covers only the seven tracked modifications (228 insertions, 20 deletions); it excludes the explicitly inventoried new files. Do not mistake that stat for the entire release inventory.

## Acceptance outcome

**Accepted for Phase 9D-2 review/release preparation:** complete suite green, cached full-universe exact reconstruction/repository reload green, unchanged frozen weighted resources/digests, historical PDF tests green and user-confirmed native persistence/restoration. No production/test corrections were required at this gate.

Final Git checks executed: `git status`, `git diff --check`, `git diff --stat`, `git ls-files --others --exclude-standard`, and empty-index verification. `git diff --check` passed; line-ending notices are informational. Final tree: **seven modified tracked files, twenty-six untracked files**, all inventoried Phase 9D-2 material. No generated result/log/PDF/cache artifact appears in the untracked list. No staging, committing, pushing, resetting or discarding was performed. Stop after Gate 5 for review; do not begin the remaining individual modes.

Complete final working-tree status:

```text
 M src/main/java/com/daviddunn/retirementplanner/app/export/IntegratedAnalyzerPdfExporter.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java
 M src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java
 M src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IntegratedAnalyzerReportAdapter.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedResultPersistence.java
?? PHASE-9D-2-EXHAUSTIVE-PROJECTION-CODEC.md
?? PHASE-9D-2-GATE-2-EXHAUSTIVE-SNAPSHOT.md
?? PHASE-9D-2-GATE-3-EXHAUSTIVE-INTEGRATION.md
?? PHASE-9D-2-GATE-3A-CAPACITY-INVESTIGATION.md
?? PHASE-9D-2-GATE-3B-LOSSLESS-INTERING.md
?? PHASE-9D-2-GATE-3B1-REALISTIC-CAPACITY.md
?? PHASE-9D-2-GATE-3B2-PRODUCTION-INTERNING.md
?? PHASE-9D-2-GATE-4-PDF-FIDELITY.md
?? PHASE-9D-2-GATE-5-FINAL-ACCEPTANCE.md
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ExhaustiveSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotSizeDiagnostics.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/ExhaustiveSnapshotInterning.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/RetainedProjectionCodec.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveResultSnapshots.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveCapacityInvestigationTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveInterningAuditTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustivePdfFidelitySupport.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ExhaustiveSnapshotTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/ProductionExhaustiveInterningTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/RealisticExhaustiveCapacityProofTest.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/RetainedProjectionCodecTest.java
?? src/test/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotSizeDiagnosticsTest.java
?? src/test/java/com/daviddunn/retirementplanner/persistence/ExhaustiveSnapshotInterningTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustiveHistoricalPdfFidelityTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java
```
