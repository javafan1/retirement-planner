# Phase 13E — Progress Dialog and Break-Even Dark Mode

## Summary and scope

Phase 13E replaces the bottom operation strip with delayed, owner-modal progress feedback and corrects the Break-Even chart callout's theme contrast. No financial calculations, engine execution, document formats, transaction semantics, projection fingerprints, analyzer associations, PDF rendering code, or dependencies changed.

Baseline: branch `main`, HEAD `cadeb3eb9e22c543f2091dbf711c054cbcb88f25`. The existing Phase 13B/C/D working tree was preserved. The staging index was empty before work. Incremental source hashes were recorded in `target/phase13e/source-before.json`; the incremental inventory is `target/phase13e/incremental-files.txt`.

## Break-Even root cause and correction

The affected node is the unmanaged `Label` named `break-even-annotation` in `BreakEvenChart`, positioned by the existing chart overlay layout. It is not a tooltip or PDF annotation. Its `.break-even-annotation` CSS combined an almost-white `rgba(255, 255, 255, 0.96)` background with semantic `-fx-text-base-color`. Dark theme supplies pale `#edf1f7` text, producing the reported white box with practically invisible text.

The new actual-chart regression reproduced this combination before the correction and failed its minimum 4.5:1 computed contrast assertion. An initial test-harness run omitted the stylesheet normally attached by `BreakEvenAnalysisDialog`; the harness was corrected to use that stylesheet before the defect was reproduced. The production correction changes only the callout background to `-fx-control-inner-background`. Text, green border/connector, chart series, coordinates, wording, marker selection, first/sustained/multiple-crossover semantics and PDF appearance remain unchanged.

The test renders the complete Break-Even view at 1180×820 and 900×650, checks text and available height, switches themes on the open chart, reopens in Dark theme, and checks the no-break-even case. Existing chart tests continue covering source values, sustained markers, first/multiple crossings, tooltips and metric selection.

## Progress architecture and timing

`PlanOperationFeedback` remains the single feedback owner. It now lazily creates a 360×200 logical-pixel undecorated, nonresizable Stage with the main Stage as owner and `Modality.WINDOW_MODAL`. The Stage contains an indeterminate spinner, wrapping centered operation message and a reserved optional Cancel area. Shared semantic CSS and `ApplicationThemeManager.prepare(scene)` supply the committed application theme and live theme updates.

The existing approximately 300 ms `PauseTransition` delays presentation, never work. Completion before the delay closes the timer without constructing a Stage. Beyond two seconds the same stable dialog remains visible; there is no second enlargement, fabricated percentage or minimum display duration. Phase changes retain fixed geometry. Save As now says “Saving retirement plan as…”. Swap messages describe preparation or its actual ordinary Save phase, not a projection calculation.

The old bottom-strip insertion/removal and obsolete strip/emphasis CSS were removed. The main controls retain existing immediate operation gating even before the dialog appears. Owner movement and resizing recenter the dialog. Closing feedback stops the delay, removes owner listeners and button handlers, hides the Stage and detaches its Scene. Owner hiding also performs cleanup. Closed feedback cannot be resurrected by a late timer callback. No explicit keyboard-focus request is made.

## Modality, nested event loops and completion

The dialog uses `show()`, never `showAndWait()`. Its modality restricts owner-window input without blocking the FX event queue. Existing `MainWindow.awaitPlanFile` still uses its established `Platform.enterNestedEventLoop` contract and exits that same loop from the worker's guarded `Platform.runLater` completion. No new nested loop, worker, `Future.get()` on FX, or controller lifecycle was introduced.

Regression tests exercise a genuinely showing owner-modal Stage while the nested loop processes successful and exceptional completion, closes feedback and exits correctly. Existing Save/Open tests exercise detached persistence, dirty-state protection, ordering and failure paths; the swap view regression now locates Cancel in the owned Stage and retains its prior stale-generation assertions.

## Cancellation, failures and races

Save, Save As and Open have no Cancel action. Window close requests are consumed; the undecorated Stage has no close decoration and no Escape-to-cancel binding. Dismissing feedback never cancels a durable write.

Swap preparation retains only its existing generation-invalidation Cancel callback. During its required ordinary Save, Cancel is hidden and its handler removed. The same feedback instance spans phase transitions. Existing epoch/owner checks and the rule preventing an obsolete callback from releasing newer feedback remain intact. Completion/failure closes feedback before error handling returns control to the owner. Owner shutdown cleanup is tested; operating-system focus, taskbar and DPI behavior still require native acceptance.

## Exact incremental file inventory

Production:

- `src/main/java/com/daviddunn/retirementplanner/ui/PlanOperationFeedback.java`: replace strip with delayed owner-modal Stage and lifecycle cleanup.
- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java`: pass the owner, remove strip plumbing, clarify Save As message; preserve operation guards and workers.
- `src/main/resources/css/application.css`: replace obsolete strip rules with semantic dialog styling.
- `src/main/resources/css/break-even.css`: one callout background correction.

Tests:

- `src/test/java/com/daviddunn/retirementplanner/ui/PlanOperationFeedbackTest.java`: delayed presentation, modality, ownership, close prevention, cancellation phase changes, centering, cleanup, nested-loop success/failure, live themes and previews.
- `src/test/java/com/daviddunn/retirementplanner/ui/PlanFileOperationsTest.java`: move/adapt the two former strip-specific tests into the new modal test class; retain all twelve file-operation tests.
- `src/test/java/com/daviddunn/retirementplanner/ui/baseline/ProjectionSwapViewTest.java`: locate preparation Cancel in the new modal without changing its generation/race expectations.
- `src/test/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenAnalysisViewTest.java`: actual-chart computed contrast, live switching, reopening and preview regression.

Documentation: this report. All other preexisting modifications and untracked files are excluded from the Phase 13E incremental inventory and preserved.

## Automated verification

All Maven commands used IntelliJ's bundled Maven, project-local `.codex-m2/repository`, and `-DargLine=-Xmx4g`.

| Run | Total | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Existing baseline: PlanFileOperationsTest, ProjectionSwapViewTest, BreakEvenAnalysisViewTest | 31 | 31 | 0 | 0 | 0 |
| Corrected UI: same classes plus PlanOperationFeedbackTest | 35 | 35 | 0 | 0 | 0 |
| BreakEvenPdfExportTest, BreakEvenAnalyzerTest, BreakEvenControllerTest, ApplicationPreferencesTest, IntegratedAnalyzerPdfReportTest, SocialSecurityAndQuickPdfTest | 48 | 48 | 0 | 0 | 0 |
| Full Maven suite | 3649 | 3605 | 0 | 0 | 44 |

Full-suite result: BUILD SUCCESS, elapsed 10:43. The 44 skips are existing opt-in capacity, diagnostic, resource-dependent and visual-preview cases; no new exclusions or skip annotations were added. Per-class skipped counts are recorded in `target/phase13e/skipped-tests.txt`. Expected fault-injection warnings and JavaFX native-access/unnamed-module warnings do not represent test failures. The full-suite counts were taken from the current Maven run and independently matched against current-run Surefire XML (3649 total, 44 skipped, zero failures/errors), excluding stale archived XML. The outer PowerShell redirection session reported exit 1 despite Maven completing with BUILD SUCCESS; native JVM warning output was surfaced as PowerShell NativeCommandError records. Maven summary and current-run XML agree on the successful test result.

Logs: `target/phase13e/baseline.log`, `before-contrast.log` (intentional failing reproducer), `focused.log`, `regression-focused.log`, `full-suite.log`. No financial golden resources were updated and no assertions were relaxed to accommodate financial changes.

## Visual inspection

Generated actual JavaFX view snapshots are under `target/phase13e/previews/`:

- `before-break-even-DARK-1180.png`: inspected the reproduced pale-text/white-callout defect.
- `after-break-even-{LIGHT,DARK}-{1180,900}.png`: inspected all four corrected full-view snapshots. Callout text and both lines fit; series and marker placement remain intact. At 900×650 the established outer scrolling is needed to see the lower chart/table, rather than compressing chart text.
- `progress-{LIGHT,DARK}-{1180,900}.png`: actual dialog-content snapshots generated while the owner-modal Stage was showing at each owner size. Inspected representative Light/1180 and Dark/900 images: readable message, semantic background, compact spinner and no clipped text. These images show the dialog content, not a desktop screenshot or proof of native focus behavior. Automated assertions separately check owner identity, modality and centering after movement/resizing.

## Remaining native Windows acceptance

1. In both themes, Save, Save As and Open a small plan: no dialog for fast completion; no flashing or orphan after completion/failure.
2. Use a large plan: delayed centered dialog, responsive spinner, blocked owner controls, correct message, no false percentages or extra enlargement after two seconds.
3. Move/resize the owner while feedback is visible; verify centering, multi-monitor placement, 100/125/150/200% scaling and text readability.
4. Try Escape, Alt+F4 and window-close shortcuts during a durable write; verify no unintended cancellation. Verify owner focus returns naturally after completion.
5. Test dirty-plan Save Before Swapping, successful Save followed by preparation, preparation Cancel, rapid repeated requests and a failed Save. Verify original generation/role protection and no Cancel during Save.
6. Test application close attempts during operations and shutdown cleanup; verify no retained progress window.
7. Open Break-Even in Light and Dark, switch theme while it remains open and reopen it. Check first/sustained/multiple/no-crossing fixtures, unchanged annotation wording, placement and hover behavior at both supported sizes.
8. Export Break-Even and analyzer PDFs; verify existing print appearance and values.

Native Windows acceptance is pending. No subsequent phase has begun.

## Final repository verification

After verification, `git diff --check` passed (exit 0; only preexisting line-ending conversion warnings). Branch and HEAD remain unchanged and the staging index is empty. No files were staged, committed, pushed, reset, cleaned or discarded. The preexisting source-file inventory has zero missing files; only the eight listed source/test/resource files changed relative to the start-of-phase hashes. Final status is recorded in `target/phase13e/status-final.txt`. Phase 13E is ready for review and native Windows acceptance; acceptance is not claimed.
