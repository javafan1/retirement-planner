# Phase 21B1 — Monte Carlo Run Control and Failure Feedback

Date: November 5, 2026. Regression level: **Level 2 — UI/workflow integration**.

## 1. Executive summary

Corrected a reproduced standalone-dialog feedback visibility defect. Current-run status, progress and validation now remain in a fixed footer outside the historical-results scroller. Scrolling through a retained result cannot scroll the new run's error out of the dialog. Saved-result freshness remains with the saved result.

Added explicit preflight feedback for the existing positive-expense-volatility restriction, useful fallbacks for empty exception messages, and privacy-safe run-stage diagnostics through Java logging. Valid runs still use the existing handler, session, worker service and current frozen inputs. No historical state reset, handler replacement, simulation change, enabled-volatility bypass or storage-format change was introduced.

The exact original native Windows no-response mechanism remains unconfirmed. The correction is justified by an independently reproduced visibility defect, as permitted by the authorization. Native retesting is required; this report does not claim the original native cause has been proven or fully resolved.

Final affected regression: **191 tests run, 191 passed, 0 failures, 0 errors, 0 skipped**. Actual JavaFX Light/Dark previews were generated and inspected. Phase 21B2–B4 have not begun.

## 2. Starting state and review

Branch `main`, starting HEAD `81dd97a12a875dad864057a2662a7ac6bcdfefbd`. The index and tracked files were clean. The sole pre-existing untracked file was `PHASE-21A-MONTE-CARLO-EXECUTION-AND-EXPENSE-VOLATILITY-AUDIT.md`; it was preserved without editing.

Reviewed the approved Phase 21A report and relevant Phase 12 immutable association, freshness and Monte Carlo lifecycle contracts, plus Phase 14 expense configuration/admission and internal financial-integration reports. Reconciled these with current standalone Dialog/View/Session/RunService, parameter acceptance, result lifecycle, controller association and existing tests. Current code, rather than superseded report limitations, determined the implementation.

## 3. Native no-response diagnosis and evidence

### Independently reproduced defect before correction

Used a synthetic plan with 25% recurring-expense volatility and a retained synthetic result. The real production `MonteCarloAnalysisDialog` was shown at 900×650. The existing worker correctly reached FAILED at `ExpenseVolatilityAdmission.requireSupported`; the error label was populated. Its geometry changed with the results scroller:

| Scroll position | Run-status Y bounds in scene | Results viewport Y bounds | Visibility |
|---|---|---|---|
| Top, 0 | 293 to 327 | 10 to 556 | Visible |
| Bottom, 1 | -403 to -369 | 10 to 556 | Completely outside viewport |

The original hierarchy put status/validation inside the same scrolling VBox as the historical chart/tables. Failure removed the progress bar, but did not reveal/focus a status outside the viewport. A user inspecting the previous result could therefore miss the failed replacement. This is a presentation defect; it does not show that the click handler failed.

Evidence: ignored `target/phase21b1/FeedbackVisibilityProbe.java`, `before.log`, `before-scroll-0.0.png` and `before-scroll-1.0.png`. The bottom-position rendering was visually inspected. No user plan was used.

A permanent regression, `MonteCarloRunFeedbackTest.failedRunFeedbackRemainsVisibleWhileInspectingPreviousResult`, was added before production changes. It failed on the negative-Y bounds. Maven reported one test / one error because the FX assertion was delivered through a FutureTask. Log: `target/phase21b1/before-test.log`. The same test passes with the correction.

### Other supported feedback corrections

- Preflight handlers formerly passed a possibly null exception message directly to the validation label. A null/blank preparation error now has a nonempty explanation, covered by an injected empty-message preparation failure.
- A worker exception with no message formerly rendered `Analysis failed. null`; it now provides a readable retry explanation, covered by an injected empty-message worker failure.
- Prefixing current-run status with the saved result's freshness mixed two independent states. The footer now reports execution; the existing saved-status notice continues to report historical applicability, association and durability.

### Remaining diagnostic gap

No direct evidence establishes the original user's mode, scroll position, post-click session state or exception. At the top of the synthetic compact dialog, the original error was visible. If the native symptom occurs with zero effective volatility and visible feedback, further native evidence is needed. No speculative busy-state reset or historical-result deletion was implemented.

## 4. Run-handler and session findings

The existing `run.setOnAction(event -> start())` remains. Run remains disabled during RUNNING/CANCELLING; the session still rejects duplicate/closed starts. Historical/outdated status is not a Run-disable condition.

Valid requests still parse settings, check readiness, isolate the current plan, construct the existing fixed/longevity request, capture any valid reference, obtain the existing parameter/projection admission, and use `MonteCarloSession.start`. A small executor observation wrapper records successful submission without changing worker selection, scheduling, result acceptance or shutdown behavior.

The following contracts are preserved and covered by the final focused suites:

| Operation | Result |
|---|---|
| Invalid preflight input or preparation error | No worker; explicit footer validation; controls remain usable; previous result retained. |
| Unsupported expense volatility | Existing policy rejects before worker submission; explicit persistent explanation; no parameter acceptance or plan mutation. |
| Valid Run from historical/outdated display | One worker uses current isolated inputs; old display remains until accepted completion. |
| Duplicate activation while busy | No second worker. |
| Worker exception or executor rejection | Existing FAILED state; controls restored; meaningful footer status; old result retained. |
| Cancel or source revision change | Existing cooperative cancellation and revision checks; no partial result or association published. |
| Dialog close | Existing session generation/token invalidation, listener detachment and executor shutdown; no late result publication. |
| Successful eligible completion | Existing acceptance, result storage, parameter handling and original-projection association guards. |
| Accepted result storage failure | Completed result remains available/exportable; footer distinguishes save failure from simulation failure; Retry Save does not rerun. |

No changes were made to `MonteCarloSession`, `MonteCarloResultLifecycle`, controller association logic or repositories.

## 5. Historical/outdated result behavior

The original result, charts, tables, frozen settings and historical association remain unchanged when a replacement is blocked, fails, is canceled or becomes obsolete. New work uses current captured plan inputs, never the retained historical plan as a substitute.

The saved-status notice remains in the results content and continues to show compatibility, pending assessment, completion time, association and durability under Phase 12 contracts. The persistent footer is explicitly headed **Current run**. On a stale retained result, it indicates that the previous result is retained and current inputs can be analyzed. During a new failed attempt, it reports that attempt's failure without an unrelated historical-warning prefix.

Two existing `MonteCarloViewTest` assertions now check the unchanged Assessing/not-yet-Outdated contract in `#mc-saved-status`, where saved-result status belongs. Those checks were retained rather than removed. Existing historical PDF/export, restoration, association and numeric-compatibility behavior was not changed.

## 6. Positive-volatility admission feedback

The view calls the existing `ExpenseVolatilityAdmission.requireSupported` against the isolated current plan after applicable input validation and before dispatch. The worker/core defenses remain intact and unchanged. The UI does not duplicate the mathematical predicate or clear/override any percentage.

The message is:

> Positive recurring-expense volatility is not yet supported by the public Monte Carlo execution workflow. Your configuration and any previous results have been preserved.

It is preceded by **Run not started. Unsupported configuration.** The message does not recommend clearing configuration as a permanent solution. Both standalone fixed and longevity modes are covered. Future supported execution can evolve the authoritative admission policy; B1 does not introduce an alternative enabled path.

## 7. Implemented layout, accessibility and diagnostics

### Persistent footer

`MonteCarloAnalysisView` owns one `runFeedback` VBox containing the existing progress, status and validation nodes plus the Current run caption. When hosted by `MonteCarloAnalysisDialog`, those same nodes are transferred to the bottom of a BorderPane, alongside the original center ScrollPane. No duplicate labels or view instances are created. The existing dialog Close button, modality, owner and close callback remain.

Direct view embeddings retain the feedback in the view. The production dialog keeps it outside the scroller. Run/Cancel/Export controls retain their existing location and keyboard behavior. No modal alert or forced focus jump was added for ordinary validation.

Status and validation expose their current text through accessible text properties. Status help distinguishes current execution from saved-result freshness. Feedback styling uses existing theme text/background/border tokens; validation uses `-app-error` when the application theme is present. There are no new focusable controls or keyboard accelerators. Native Narrator behavior remains an acceptance item, not a claim based on scene assertions.

### Privacy-safe diagnostic events

`MonteCarloRunDiagnostics` uses existing `java.util.logging` at INFO/WARNING. Events are limited to view readiness, click entry, validation success, admission/preflight rejection, worker submission, session transitions, a deferred layout observation, and close. It does not log per-simulation progress or add a file writer, network telemetry, dependency or setting.

Each record has only:

- Fixed event/category and mode names.
- Session state before/after.
- Booleans for Run enabled, validation passed, worker submitted, status updated and status geometrically visible.
- Exception class name, when applicable.

No Throwable is attached to the logger, and no exception message, stack trace, plan/input value, account data, UUID, personal identifier or path is emitted. The former raw worker-exception log in this view was replaced. Tests verify that formatting does not even call exception `getMessage()`/`toString()`, no raw Throwable reaches a handler, and a failing logging handler cannot mask execution feedback.

`statusVisible` checks attachment to a showing window, scene bounds, visible ancestors and any ancestor ScrollPane viewport. The deferred observation runs after CSS/layout. It describes JavaFX geometry, not native window occlusion or a screen-reader announcement. Detached test scenes correctly report false. No diagnostic filesystem location is added; records follow the application's existing Java logging handlers/console.

## 8. Modified and new files

Production:

1. `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisDialog.java` — fixed feedback footer; package-scoped constructor for exercising the real dialog with an injected view.
2. `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java` — feedback grouping, separation of run/saved status, authoritative preflight admission, error fallbacks and diagnostic calls.
3. `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRunDiagnostics.java` — new small sanitized event formatter/logger.
4. `src/main/resources/css/monte-carlo.css` — two narrowly scoped footer/validation rules.

Tests:

5. `src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRunFeedbackTest.java` — new real-dialog, failure, lifecycle, storage and preview coverage.
6. `src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRunDiagnosticsTest.java` — new privacy/failing-handler coverage.
7. `src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloViewTest.java` — two saved-freshness assertions moved to the saved-status node.

Deliverable: this report. Ignored diagnostics/previews/logs reside under `target/phase21b1/` only.

## 9. Automated verification

Used IntelliJ's bundled Maven, Java 25, the required workspace `.codex-m2/repository`, and `-DargLine=-Xmx4g`.

| Run | Tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Before-fix visibility reproducer | 1 | 0 | 0 | 1 expected FX assertion error | 0 |
| Focused final feedback/diagnostics/view/session set | 31 | 31 | 0 | 0 | 0 |
| Final affected Level 2 regression, including that focused set | **191** | **191** | **0** | **0** | **0** |

The 31-test set is a subset of the 191, not an additional unique-test total. One intermediate test compilation error used the wrong Account getter in a new assertion; it was corrected to the existing `getCurrentBalance()` API. Final compilation and all final tests pass. No financial expected value or golden assertion was changed.

Final selection:

```text
MonteCarloRunFeedbackTest, MonteCarloRunDiagnosticsTest,
MonteCarloViewTest, MonteCarloSessionTest, MonteCarloMortalityViewTest,
MonteCarloInflationViewTest, MonteCarloParameterPersistenceTest,
MonteCarloProjectionAssociationTest, MonteCarloResultLifecycleTest,
MonteCarloRestorationViewTest, MonteCarloPdfExportTest,
MonteCarloPdfWorkflowIntegrationTest, ExpenseVolatilityAdmissionTest,
MonteCarloComparisonSessionTest, AnalyzerIntroductionNavigationTest
```

Logs: `target/phase21b1/feedback-final.log` (31 tests, 23.494 s) and `target/phase21b1/regression-final.log` (191 tests, 58.663 s). Expected failure-injection warnings occurred in passing tests. Existing actual acceptance/association tests cover all eight MC identities; the new storage-failure test preserves a completed exportable result and proves Retry Save calls financial work only once overall.

`git diff --check` passed. Only standard LF/CRLF conversion notices were emitted. New report/source files were also checked for trailing whitespace.

## 10. Light/Dark and compact-window verification

Generated twelve actual JavaFX DialogPane previews, using synthetic data:

```text
target/phase21b1/previews/{LIGHT,DARK}-{900,1180}-{admission,validation}.png
target/phase21b1/previews/{LIGHT,DARK}-900-{busy,worker-failure}.png
```

Dialog windows were set to 900×650 and 1180×820. Captured content dimensions are smaller because of native window decorations. Tests check feedback bounds and nontruncated text after layout; the permanent regression also verifies visibility with the results scroller moved to the bottom.

Visually inspected representative final images: DARK-900-admission, LIGHT-900-admission, DARK-1180-validation, LIGHT-1180-admission, DARK-900-worker-failure and LIGHT-900-busy, plus the original before-fix bottom-scrolled rendering. They show readable wrapped messages, persistent status after progress disappears, retained result content, enabled Cancel during work, and no footer/Close clipping. The existing chart/table content continues to scroll; B1 does not redesign wide historical tables or claim to eliminate their existing compact-width limitations.

The previews attach a synthetic prior result directly for deterministic presentation tests; real saved-result restoration/provenance is covered by the existing persistence/lifecycle test selection. Native Windows interaction, Narrator and 100%/125%/150% DPI acceptance remain outstanding.

## 11. Regression scope and intentionally excluded work

Level 2 is appropriate because changes are confined to standalone JavaFX presentation/admission feedback, diagnostic observation and tests. Financial formulas, simulation requests/algorithms, random streams, ProjectionEngine, plan/snapshot serialization, fingerprints, historical associations and PDF report mapping were not modified.

The full long-running suite, exhaustive Social Security searches, production-scale MC benchmarks and unrelated financial golden suites were intentionally excluded under the user's risk-based policy. Existing focused history, actual association/parameter persistence, retained-result PDF and admission tests were included because they directly protect this workflow. No test was disabled, weakened or permanently skipped.

Comparison execution, public positive-volatility capability, Phase 19/20 behavior and Phase 16B3 remain unchanged. No Phase 21B2/B3/B4 work was implemented.

## 12. Native Windows retest and unresolved questions

1. Launch the rebuilt application with its normal Java logging console available (for example, through IntelliJ). No real plan file is needed for diagnostic sharing; use a synthetic test plan where practical.
2. Open standalone Monte Carlo with an existing historical/outdated result. Confirm saved-result status remains with that result and Run remains available when not busy.
3. With supported zero-volatility inputs, start a new analysis. Confirm a visible Current run footer, normal progress/Cancel and a new accepted result on success.
4. With positive recurring-expense volatility, confirm immediate **Run not started. Unsupported configuration.** and its full explanation. Verify the configuration, retained result and export remain intact.
5. Enter invalid inputs, repeat Run, scroll to the historical chart/tables, and confirm validation remains readable outside the scroller. Cancel an active replacement and close during work; confirm the established lifecycle.
6. Inspect Light/Dark at 900×650, 1180×820 and 100%/125%/150% Windows scaling; verify Tab/Enter/Escape and Narrator.
7. If the original silent behavior persists, provide only the `MonteCarloRun event=...` diagnostic records (including wrapped continuation lines). They establish mode, click entry, validation/admission, submission, terminal state and geometric visibility without user data. Do not share complete application logs or plan files.

Remaining questions: did the original click enter the handler; was the prior session still busy; were current inputs valid; did admission reject; was a worker submitted; and was the relevant feedback outside the viewport? B1 now records these stages. It does not claim to know the original answers.

## 13. Git preservation and approval gate

The existing untracked Phase 21A report was preserved. Completed Phase 19, Phase 19C and Phase 20 source remained in the starting committed baseline and was not altered. The only changes are the files listed above and this report; no actual user plan, backup or historical artifact was accessed or modified.

Nothing was staged, committed, pushed, reset, cleaned or reverted. Request native Windows acceptance for Phase 21B1 before proceeding to Phase 21B2.
