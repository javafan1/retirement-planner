# Phase 21B3 — Supported Monte Carlo Strategy Comparison with Stochastic Expenses

## Checkpoint A — contract selected before public enablement

Starting branch `main`, HEAD `81dd97a12a875dad864057a2662a7ac6bcdfefbd`. The existing 26 modified tracked files and 12 untracked files include the accepted Phase 21A/B1/B2 implementation. Initial status and SHA-256 preservation inventory are recorded in `target/phase21b3/initial-status.txt` and `initial-file-hashes.json`. No index changes are authorized.

Current source confirms that the private paired engine already accepts separate `InternalExpenseRun` instances, shares its original economic/lifetime worlds, binds a scenario-local expense path to each isolated candidate and reduces the original financial outcomes. Public execution is still gated. Comparison freshness capture currently invokes that execution gate. Its status/validation are inside the content ScrollPane and require an independent compact-window visibility regression before any presentation correction.

Selected version matrix:

| Result | MC payload | Envelope schema | Stored fingerprint | Capability/algorithm contract |
|---|---:|---:|---:|---|
| Legacy standalone/comparison, disabled expenses | 1 | 1 | 1 | Original algorithm maps; original EXACT_V1 verification and read-only TEMPORARY_NUMERIC_V2 applicability |
| Accepted B2 enabled standalone | 2 | 1 | 3 | Existing `ExpenseExecution` and EXPENSE_MODEL_V3, unchanged |
| Generated enabled comparison | 3 | 1 | 4 | New immutable `PairedExpenseExecution` V1, PAIRED_EXPENSE_V4 and payload-specific `paired-recurring-expenses` algorithm tag |

The new paired capability has ordered Current/A and Baseline/B members. Each member is either its independently captured existing `ExpenseExecution` or explicit null (disabled). At least one member must be enabled. Validation requires capability presence to agree with that side's original configured volatility, and validates every enabled manifest against its own exact frozen plan document/digest. Disabled sides are validated as disabled from their original request, whose integrity remains covered by the envelope payload digest. No second full budget, per-scenario path or random draw is persisted.

Model remains `INDEPENDENT_LOGNORMAL_EXPENSE_LEVEL_V1`; expense dimension/version 6/1; correspondence protocol 1. Exact normalized definition/duplicate ordinal correspondence is unchanged, including volatility in the definition. A distinct paired algorithm tag identifies that ordered, two-candidate execution contract; legacy and B2 algorithm maps are not globally bumped.

V3 permits only PAIRED_FIXED/PAIRED_LONGEVITY with generated original return/inflation/stream/mortality protocols, paired capability, fingerprint 4 and the exact paired algorithm map. V2 continues to permit only enabled standalone with fingerprint 3. V1 retains its original semantics. Unknown, missing, swapped or mismatched capabilities, unsupported combinations and digest/fingerprint failures remain rejected. New optional root fields are omitted from older payloads; streaming decoding preserves all original required properties and strict unknown/duplicate-field checks. Existing constructors remain compatible.

Fingerprint V4 covers ordered effective A/B financial inputs, original settings/lifetime context, model/protocol and each side's binding manifest. Exact request-document digests remain integrity bindings and are excluded from the effective financial hash, avoiding accidental dependencies on display names, UUIDs and baseline/source metadata. Original V1/V2/V3 representations remain unchanged.

Affected foundation paths: `MonteCarloSnapshot`/streaming decoder, comparison result carrier/mapping, fingerprint service, payload-specific algorithm/envelope compatibility, codec numeric allowlist and enabled fingerprint verification, lifecycle/history adapters. Existing repositories, immutable projection associations, atomic writes, raw/compressed/numeric limits and Phase 19 S3 decoder compatibility remain intact. No plan schema migration or historical rewrite is required. Old V1 positive-configured artifacts remain preserved and explicitly uninterpretable as enabled execution.

Sequence: test this foundation with public positive comparison still gated; then connect the supported result to the existing paired engine; then complete pure freshness capture, UI/history/PDF disclosures and lifecycle tests; finally unfiltered Level 3 regression and native acceptance. No unresolved legacy-interpretation conflict was found at this checkpoint.

Checkpoint A verification completed before modifying public execution: `target/phase21b3/foundation.log`, **62 tests, 60 passed, 0 failures, 0 errors, 2 existing opt-in skips**, BUILD SUCCESS (36.298 s). Included new paired capability matrix/tamper tests, accepted B2 capability tests, original numeric compatibility and MC codec tests, and Phase 19 S3 couple Social Security persistence tests. Structural new-payload fixtures at this checkpoint are explicitly not financial execution oracles.

## 1. Executive summary

Generated Monte Carlo Strategy Comparison now supports positive recurring-expense volatility for couple fixed, couple longevity, single-person fixed and single-person longevity modes. Each mode supports zero/zero, positive/zero, zero/positive, identical positive definitions and different positive definitions. Both candidates retain their own original frozen financial inputs. No configured percentage is modified or suppressed.

Public enablement followed the capability checkpoint above. The approved existing sampler, random protocols, internal paired execution, projection engine and comparison aggregation are reused. Zero/zero retains its original execution and storage contracts. Phase 21B4 has not begun.

## 2. Current/Baseline provenance

`PairedExpenseExecution` is an immutable ordered record containing version 1 and independently captured Current/A and Baseline/B capabilities. A null member explicitly represents a disabled candidate; both null is invalid. Every enabled member validates its document digest, normalized definitions, indices and duplicate ordinals against its own original request. Root snapshot validation also enforces report family, world protocols and the capability/version matrix.

Wrong manifests, edited original documents, missing required enabled members, swapped candidates, unsupported model/protocol versions, wrong families, envelope fingerprint mismatches and corrupt artifacts remain rejected. The original request documents are already retained by the existing snapshot. The capability adds only bounded binding metadata, not another full expense budget or scenario paths.

## 3. Common random numbers

The unchanged keyed SHA-256 expense stream uses dimension/version 6/1, correspondence protocol 1, master seed, scenario index and absolute calendar year. A definition includes type, annual amount, dates, escalation and volatility, plus a zero-based duplicate ordinal. Names, UUIDs and object/list identity are excluded. Exact definitions share keyed shocks; different financial definitions intentionally do not share a Gaussian draw.

Tests check matching multipliers, different-definition keys, duplicate definitions, expense reordering, unrelated zero-budget insertion, candidate swapping and unchanged economic path object identity. Generated market, mortality and inflation streams are not advanced by expense sampling. Existing known-answer vector and seeded tests remain unchanged.

## 4. Supported execution and financial integration

The public analyzer first isolates both candidates, validates their specifications and captures paired provenance. Only enabled sides receive `InternalExpenseRun.configured`; a disabled side follows the original calculation branch. Each scenario shares the established economic/lifetime world but generates and binds a distinct immutable expense path to each enabled candidate. One isolated plan per candidate is reused within the existing bounded sequential lifecycle. A final manifest validation precedes publication.

`ProjectionEvaluationContext.withExpensePath` feeds the existing annual expense calculation. No sampling occurs in tax, Medicare, Roth or reporting code. Repeated financial evaluation of a candidate/scenario/year sees the same multiplier. Existing full-engine fixed-budget oracles cover spending, proration, escalation, survivor adjustments, withdrawals, RMDs, taxes, Medicare reevaluation, Roth conversions, funding failure and estate values. New paired tests compare public execution with the established internal full-engine oracle for all twenty household/mode/volatility combinations, including shared inflation. Identical candidate inputs produce identical financial outcomes under shared worlds. No predetermined direction is imposed on tax or Roth effects.

No production files in `domain`, `ProjectionEngine`, the sampler, random generators, tax or Roth calculations were changed in this phase.

## 5. Admission and freshness

`ExpenseVolatilityAdmission.requireComparisonSupported` validates both frozen candidates. Public generated comparisons select the enabled adapter when either side is positive. The old defensive gate remains for paths lacking the supported adapter. Caller-supplied enabled worlds fail before a supplier or projection executes. Existing demographic, horizon and supported-input restrictions are retained.

Pure comparison capture no longer invokes execution admission. Worker and UI preflight retain supported validation. Positive inputs can therefore be assessed normally rather than automatically classified as INPUTS_NEED_ATTENTION. Acceptance uses `forAcceptance`, choosing fingerprint 4 only for enabled comparisons. Changes to either candidate make an old comparison outdated without mutating it. Original V1, read-only numeric V2 and accepted standalone V2/V3 semantics remain distinct.

## 6. UI feedback and lifecycle

A deterministic compact-window test demonstrated a separate visibility defect: after scrolling the old comparison content, the current failure label occupied scene coordinates approximately -337 through -320 and was invisible. `feedback-before.png` preserves that reproduction. This evidence justified moving the existing progress, current-run status and validation nodes into a fixed footer below the dialog scroller, following accepted standalone B1 behavior. No Run handler or historical state was reset speculatively.

The footer distinguishes current execution feedback from saved-result freshness and supplies a clear fallback for exceptions with empty messages. Tests retain the previous successful result after cancellation, source revision changes, closure, worker/executor failure and invalid preflight. Duplicate requests remain blocked. Four mode-specific storage failure tests verify that a completed enabled result remains unsaved/exportable, the old file remains byte-for-byte intact and Retry Save persists the same result without another simulation.

## 7. Historical storage and restoration

The existing codec, result store, immutable association and atomic-save architecture are used. Payload 3/fingerprint 4 is validated and restored explicitly; V1 and B2 results are not upgraded. Save, Save As, controller restart, original projection pinning, Current/Baseline edits and swaps in both directions are exercised with isolated synthetic repositories. Restored workers deliberately throw if invoked, proving that restoration/reporting performs no simulation. Association IDs and saved numerical outcomes remain unchanged.

A changed projection or current plan changes applicability, not saved contents. Unsupported legacy positive configurations remain preserved with explicit compatibility feedback rather than being represented as authenticated stochastic execution. No historical snapshot or outer plan schema is migrated.

## 8. Current, historical and PDF disclosures

Enabled comparison details disclose both original candidates independently, including active/disabled status, original percentages, expected amounts, dates, escalation, model/version, annual-relative-standard-deviation interpretation and exact-definition correspondence. They explain that volatility is not a maximum increase, shocks do not compound, one-time expenses are unaffected and deterministic/zero-volatility paths are unchanged. Existing details retain seed, trial count, market, inflation and mortality assumptions.

The existing comparison PDF adapter already exports run details in its **Frozen analysis details** section. The same frozen capability-backed disclosure therefore appears in current and historical PDFs without changing the renderer or chart/table code. Tests inspect section paragraphs as well as report context; comparison details were never top-context paragraphs. Legacy zero/zero details and PDF paths remain unchanged. View/Save/Cancel and Phase 18A directory adapters are retained.

## 9. Files changed by B3

Production additions: `app/montecarlo/PairedExpenseExecution.java`.

Production updates under `app/montecarlo`: `ExpenseVolatilityAdmission`, `InternalExpenseRun` (wording only), `MonteCarloFingerprintService`, `MonteCarloSnapshot`, `MonteCarloSnapshotDeserializer`, `MonteCarloSnapshotMapping`, `MonteCarloStrategyCandidate`, `MonteCarloStrategyComparisonAnalyzer`, `MonteCarloStrategyComparisonResult`.

Compatibility updates: `app/socialsecurity/results/AnalysisCompatibilityPolicy`, `AnalysisSnapshot`, `AnalysisVersions`; `persistence/AnalysisSnapshotCodec`.

UI updates: `ui/montecarlo/MonteCarloStrategyComparisonDialog`, `MonteCarloStrategyComparisonRun`, `MonteCarloStrategyComparisonRunService`, `MonteCarloStrategyComparisonView`; `ui/dialogs/ExpenseDialog` (obsolete unsupported-execution tooltip sentence only). Existing B1 CSS is reused.

New tests: `PairedExpenseCapabilityTest`, `SupportedPairedExpenseTest`, `ComparisonExpenseFeedbackTest`, `PairedExpenseHistoryTest`, `PairedExpenseLifecycleTest`, `PairedExpensePreviewTest`.

Updated tests: `ExpenseVolatilityAdmissionTest`, `SupportedStandaloneExpenseTest` (supplied-world rejection type), `AnalyzerIntroductionMenuTest`, `MonteCarloComparisonViewTest`, `MonteCarloPdfWorkflowIntegrationTest`. Earlier accepted modifications to other files shown by Git status belong to B1/B2 and are preserved.

## 10. Automated verification

Regression level is **Level 3**, because supported stochastic execution and persisted result contracts change. Focused suites cover capability/codec compatibility, paired engine execution, MC/random/expense regressions, association/history/save behavior, worker lifecycle, UI, PDF workflows and actual preview rendering. Final focused, opt-in preview and unfiltered Maven totals are recorded below. No test configuration or financial golden expectations were weakened.

| Verification | Total discovered | Passed | Failures | Errors | Skipped | Evidence |
|---|---:|---:|---:|---:|---:|---|
| Checkpoint A foundation | 62 | 60 | 0 | 0 | 2 | `target/phase21b3/foundation.log` |
| Final focused selection | 671 | 661 | 0 | 0 | 10 | `target/phase21b3/focused-verified.log`, 2 min 14 s |
| Cached opt-in PDF previews | 1 | 1 | 0 | 0 | 0 | `target/phase21b3/cached-pdf-preview.log`, 17.597 s |
| Complete unfiltered Maven regression | 4,177 | 4,131 | 0 | 0 | 46 | `target/phase21b3/full-regression.log`, BUILD SUCCESS, 13 min 40 s |

Focused selection: `*MonteCarlo*Test,*Expense*Test,*AnalysisSnapshot*Test,*AnalyzerAssociation*Test,*AssociationTest,*WeightedSnapshot*Test,*Save*Test,*SocialSecurityReduction*Test,AnalyzerIntroductionMenuTest,!*BenchmarkTest`. This development selection intentionally omitted expensive unrelated calculations and benchmark classes; the final unfiltered run includes normal benchmark/regression classes. The opt-in preview used its four cached completed 5,000-trial report fixtures without regenerating simulations.

All Maven runs use Java 25, IntelliJ's required bundled `mvn.cmd`, `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository` and `-DargLine=-Xmx4g`. The final command is `mvn.cmd <repository and heap options> test`, with no `-Dtest` selection or added exclusions. Existing opt-in conditions remain unchanged.

The final run finished November 5, 2026 at 14:18:44 Eastern, process exit 0. It includes the original financial/seeded golden assertions, market/mortality/inflation known-answer tests, exact disabled-path comparisons, legacy wire/fingerprint compatibility, B2 standalone enabled restoration, Phase 19 snapshot correction, all new B3 tests and existing PDF/navigation/settings workflows. All passed without changing their financial expectations. The 76 additional cases over the accepted 4,101-case B2 baseline introduce no additional skips.

The 46 skips comprise **42 existing opt-in/disabled preview, investigation or production-scale benchmark cases** and **four existing Mississippi worksheet assumptions awaiting authoritative guidance** (MS11, MS12, MS21 and MS27). No permanent test configuration was changed. The cached MC PDF preview was additionally enabled in its separate successful run. Per-test skip reasons and current-run XML reconciliation are recorded in `target/phase21b3/full-skips.csv` and `full-totals.json`. XML aggregation reconciles all 473 classes executed in the final Maven log, excluding stale artifacts from unrelated prior runs.

Development failures were investigated rather than hidden: PDF provenance tests initially searched only the top context instead of the existing details section; UI fixtures initially used a shared default result store and were corrected to isolated temporary repositories. An existing standalone test's immediate asynchronous status assertion failed once in the broad focused run, passed in isolation and passed in the repeated 671-test selection after new-fixture isolation. No standalone production behavior or assertion was changed to address it. The before-fix offscreen-feedback test intentionally failed to establish the visibility defect. These are not accepted final failures.

## 11. Actual JavaFX and PDF inspection

Actual dialogs were rendered in Light and Dark at 900×650 and 1180×820 for all four household/mode combinations. The previews include busy state, completed charts/details and empty-message worker failure while previous results remain displayed. The fixed footer remains inside the visible scene when the outer content is scrolled. Per-candidate 25%/17% details wrap normally; theme colors, chart controls and action buttons remain readable. Artifacts are under `target/phase21b3/previews`.

Historical PDFs were generated from all twelve asymmetric household/mode fixtures. Representative pages were rendered with PDFBox and inspected for both-positive, Current-only and Baseline-only cases. The Baseline-only single-person longevity page correctly says Current disabled and Baseline 17%, even after today's inputs changed. No overlapping text or missing disclosures were observed. Existing tables/charts remain intact. Longer disclosures can continue through existing pagination; representative both-positive reports have six pages and asymmetric single-person reports five. No PDF renderer/layout changes were made.

## 12. Resource observations

A bounded synthetic workload used 100 paired trials, ten expenses per candidate and a thirty-year fixed horizon. One measured execution took 0.867 seconds; the resulting raw envelope was 177,729 bytes, compressed artifact 53,446 bytes and paired capability 2,716 bytes, under a 4 GiB test JVM. Timing is an observation, not a production-scale performance guarantee.

Only two isolated candidate plans are retained during execution; no per-trial plan copies or unbounded expense cache were introduced. Scenario paths are transient and not serialized. Existing trial, horizon, occurrence, document, numeric and artifact budgets, cancellation polling and progress remain enforced. Peak allocation has not been independently profiled.

## 13. Remaining compatibility boundaries

Enabled caller-supplied worlds remain unsupported. Exact-definition correspondence deliberately treats changed amounts, percentages, dates or escalations as unmatched. Durable IDs, partial correspondence, correlations, monthly sampling and additional distributions remain deferred. Existing legitimate demographic restrictions are unchanged. This phase provides truthful minimum disclosures; broader B4 presentation refinement is deferred.

Native viewer launching, Windows DPI behavior and production-scale performance require native acceptance. Automated tests use synthetic inputs and fake openers; no real retirement plan files or external PDF viewers are used.

## 14. Native Windows acceptance checklist

- Run all four household/mode combinations with Current-only, Baseline-only and both-positive volatility; confirm normal progress and cancellation.
- Compare identical candidates under one seed; verify zero deltas and independently configured asymmetric assumptions.
- Scroll a compact dialog to the bottom, trigger invalid input or a failed run, and verify persistent current-run feedback with the previous result intact.
- Save, Save As, restart, restore history and swap projections in both directions; verify original assumptions, association and applicability.
- Exercise a storage failure and Retry Save; confirm no simulation rerun and preservation of the original plan/backup.
- View and Save current/historical PDFs; inspect both independent percentages, disabled-side wording, charts/tables and configured export directory.
- Verify Light/Dark, keyboard focus, owner/modality, 100%/125%/150% DPI and supported compact sizing.

## 15. Git preservation and approval gate

Initial per-file hashes and status are retained under `target/phase21b3`. Changes overlapping accepted B2 files extend their contracts; unrelated pre-existing files and reports remain preserved. The final hash/status/index check is recorded with regression results. Nothing was staged, committed or pushed. Stop at B3 and request native Windows acceptance before B4.

Preservation check: all **38** initially changed/untracked files remain present; **24** are byte-for-byte unchanged and **14** are intentionally extended by B3 (listed in `target/phase21b3/preservation-final.json`). Accepted standalone production classes, B1 diagnostics/CSS, earlier reports and unrelated accepted tests remain unchanged from the starting hash inventory. The index is empty, branch remains `main`, and HEAD remains the value recorded at Checkpoint A. Nine additional tracked files are edited by B3; eight new B3 source/test/report files are untracked. No destructive Git operation was used.

`git diff --check` passed after implementation and report updates. New B3 untracked files were also checked for trailing whitespace. The final working tree contains 35 modified tracked files and 20 untracked files, including all pre-existing work; `target/phase21b3/status-final.txt` records the full inventory.

## 16. Final acceptance recommendation

All four household/mode combinations and asymmetric configured volatility combinations are supported and pass automated verification. Financial calculation, random-stream, legacy artifact and existing workflow regressions pass. JavaFX/PDF inspections found no blocking layout issue. Phase 21B3 is ready for **native Windows acceptance**, with the explicit limitations and checklist above. Phase 21B4 and unrelated work remain deferred. No commit is authorized by this report.
