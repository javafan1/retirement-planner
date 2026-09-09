# Stage 5E: Social Security analyzer job lifecycle

## Recovery inspection (2026-09-09)

The repository at recovery was HEAD f9bc653 (Stage 5D). `git diff` and
`git diff --stat` were empty. Only two untracked Stage 5E test drafts survived:
`SocialSecurityAnalyzerJobControllerTest.java` (06:16:26) and
`SocialSecurityAnalysisProgressModelTest.java` (06:16:38). Both contained
malformed method declarations; one also needed an explicit generic type for
an always-throwing work lambda. Their structure was retained and repaired.
No Stage 5E production files, documentation, logs, Maven results or running-test
evidence survived. There were no truncated tracked files, conflict markers,
Failsafe reports, or crash dumps in the inspected project. A crash cannot be
established as the cause of the test draft errors.

The 172 original Surefire XML reports summed to **989 tests, 0 failures,
0 errors, 7 skipped**. `stage5d-full-suite-final.log` completed BUILD SUCCESS
at 2026-09-08 19:21:47 -04:00. No production source changed after that result.
This is accepted Stage 5D evidence, not evidence of Stage 5E completion.
An inventory of individual suite counts/timestamps was preserved before
reports were overwritten in `target/stage5e-recovery-existing-evidence.csv`.
The original stage logs remain intact. Generated reports, DLLs, and logs are
ignored artifacts and were not deleted.

Recovery checklist (initial classification):

| Items | Initial status | Evidence / first unfinished work |
|---|---|---|
| A admission, B controller, C publication token, D revisions, E dispatch | NOT STARTED | No implementation files; dialog still used per-dialog Task/executor |
| F progress model, G authoritative busy state | PARTIAL | Existing Task-bound progress/UI disabling; new model only a test draft |
| H SS-only cancellation, I Quick cancellation | NOT STARTED | No token overloads |
| J deterministic exhaustive lifecycle | PARTIAL | Service cancellation existed; UI cancelled Task before cleanup |
| K dispose, L footer, M onHidden | NOT STARTED | Footer bypassed cleanup; no onHidden handler |
| N previous results | PARTIAL | Existing render-on-success retained results but lacked revision guards |
| O frozen Quick, P frozen exhaustive, Q plan notifications | NOT STARTED | Worker read live plan; no revision subscription |
| R weighted adapter, S resource ownership, T failure classification | NOT STARTED | No shared lifecycle or adapter test |
| U documentation | NOT STARTED | Stage 5E document absent |
| V focused tests | PARTIAL | Two noncompiling untracked drafts |
| W UI/state tests, X service/reference tests | PARTIAL | Earlier-stage tests passed; Stage 5E coverage absent |
| Y complete suite | NEEDS RECHECK | Stage 5D passed; no Stage 5E run existed |
| Z final diff review | NOT STARTED | Recovery inspected before making changes |

Implementation resumed at A. It did not reset, restore, discard, delete,
reimplement surviving production work, or commit anything.

## Admission and resources

`MainApplication` owns one `SocialSecurityAnalysisJobCoordinator` and closes it
from `Application.stop()`. All normal dialog constructors share it. It owns
one reusable outer executor and a single admission lease. SS-only, Quick,
deterministic exhaustive, and future weighted work use the same gate.
Competing launches return false immediately; they are not queued.

**Cancelled is not terminated.** Cancellation sets the lease's sticky atomic
flag. Admission is released in the outer execution wrapper's finally block,
after the service returns or throws and its worker cleanup finishes. Only
then is the completion callback dispatched. Closing the application requests
cancellation and shuts down admission/executor without blocking JavaFX.

The Stage 5D representative pool remains separately owned by Stage 5D; it is
not merged with the outer executor and no nested financial parallelism was
introduced. SS-only grid and survivor phases remain sequential with respect
to each other, each owning its existing parallel executor. They now wait for
that executor to terminate before returning, including exceptional paths.

## Dialog session and publication

`SocialSecurityAnalyzerJobController` owns a UUID session, monotonically
increasing generation, current job identity, per-mode revision snapshots,
sticky cancellation, progress, and IDLE/RUNNING/CANCELLING/CLOSED state.
Execution ownership and publication eligibility are distinct: invalidating a
result does not release the running job's admission.

All worker progress and completion travel through the injected dispatcher.
Production uses `Platform::runLater` at this single boundary. Tests use
manual queues or a deterministic dispatcher. Eligibility is checked inside
each dispatched callback, immediately before observer/result mutation. An
obsolete completion cannot clear the busy state or publish into a newer job.
A second eligibility check follows the state observer to cover reentrancy.
The owning obsolete job may clear its own busy state after cleanup, without
publishing results or clearing result stale markers.

Revision dependencies:

| Change | SS-only | Quick | Deterministic exhaustive | Weighted |
|---|---|---|---|---|
| Mortality category/adjustment, real discount rate, valuation date | invalidate | invalidate | retain financial result | invalidate |
| Quick candidate count | retain | invalidate | retain | retain |
| New SS-only result | retain | invalidate SS cross-reference/comparison | refresh SS cross-reference only | retain |
| Plan replacement/modification | invalidate | invalidate | invalidate | invalidate |
| Future weighted settings | retain | retain | retain | invalidate |

`ApplicationController` exposes a narrow UI-thread source-plan revision
subscription, increments it for new/open, markModified, and baseline changes,
and returns a listener-removal action. The dialog updates its plan reference,
marks existing results stale, and detaches this subscription on disposal.

## Progress, busy state, retention, disposal

`SocialSecurityAnalysisProgressModel` orders phases explicitly (not enum
ordinal), rejects backwards phase/count movement, and gives cancellation
priority. The controller keeps at most one queued progress callback per job
and coalesces to the newest advancing update. Counts are actual work counts;
percentages explicitly describe the current phase, never a fabricated overall
percentage. Weighted labels support Preparing longevity scenarios, Proving
equivalent claiming strategies, Evaluating retirement outcomes, and Complete.
Cancelling overrides progress text. Stage 5F will supply real preparation
progress as its scenario preparation work is integrated.

RUNNING disables inputs and all Run actions and enables Cancel. CANCELLING
keeps inputs/Run disabled and disables Cancel until cleanup releases admission.
CLOSED rejects all publication. SS-only and Quick now expose Cancel as well.

Footer Close, window close, and onHidden share one idempotent disposal path.
Disposal closes the controller, invalidates generation, requests cooperative
cancellation, removes input/source-plan listeners, and detaches progress.
It never waits for worker termination on JavaFX. Background cleanup still
releases admission. The previous successful presentation remains visible
through reruns, cancellation, fatal failure, and obsolete completion; only a
valid current success replaces it.

## Frozen inputs and service cancellation

Quick captures the complete plan copy and immutable selected ranked candidates
on the UI thread through `SocialSecurityAnalyzerInputs`. Its baseline and
candidate evaluations use that same frozen plan. Deterministic exhaustive
builds its standard universe from one copied plan at launch. Neither worker
reads live controls or the live plan. Source mutations can invalidate
publication but cannot alter in-flight financial input. SS-only already builds
its value request at launch. Weighted requests retain their existing deep copy.

Backward-compatible token overloads were added to
`SocialSecurityMortalityWeightedClaimingGridCalculator`,
`SocialSecuritySurvivorClaimingOptimizationCalculator`, and
`IntegratedSocialSecurityStrategyComparisonService`. Existing overloads use
the no-cancellation token. Cancellation checks surround safe cell/strategy
boundaries and the final return; Quick checks baseline/candidate boundaries
and does not misclassify cancellation as a structured strategy failure.
Existing exhaustive cancellation now flows through the common controller.
No Task.cancel, Thread.stop, or forced FX-thread waiting remains in the dialog.
Cancellation latency includes any already-running financial cell/strategy.

## Failure presentation and Stage 5F boundary

Fatal service failure preserves the prior result and reports "Analysis failed
before completion". Structured candidate failures remain successful completed
service results and expose unavailable counts. Cancellation reports "Analysis
cancelled". Unsupported modern-cohort model boundaries have a separate concise
explanation. Missing complete weighted baseline/survivor policy explains the
requirement and never invents age 60. Existing deterministic service baseline
semantics and legacy evaluator representation are unchanged; baseline failures
still abort the deterministic comparison and differences are unavailable.
No financial formula, persisted model, ranking, or Stage 4/5A-5D service was
changed to implement error presentation.

`Stage5EWeightedAdapterTest` runs the real weighted comparison service through
the controller, exercises success, cooperative cancellation, admission,
cleanup, queued publication and stale rejection, observes its actual phases,
and compares the success result exactly with the sequential reference.
This is the lifecycle adapter seam for Stage 5F, not a weighted UI/search
implementation. Stage 5F still owns scenario preparation, weighted settings,
and the actual weighted analyzer view.

## Machine-stability verification policy

Recovery verification uses one Maven process at a time, no benchmarks or
stress loops. The existing weighted test constructor is invoked with worker
limit **2**. Test JVMs use `-XX:ActiveProcessorCount=2`, enforcing the existing
Stage 5D processor bound even when earlier parameterized tests request larger
limits. Two existing SS-only equality tests use their supported explicit
2-thread constructor. These are verification constraints only.

**Stage 5D production default remains 4, hard cap remains 8, bounded by
available processors and representative count. No Stage 5D production file
changed.** JavaFX uses software rendering (`-Dprism.order=sw`). Existing native
DLLs were extracted into ignored `target/javafx-natives` and provided through
`java.library.path` after sandbox denial of the home JavaFX native cache.
No system installation or build/dependency setting changed.

Maven executable:
`C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd`

Every invocation uses
`-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.

## Verification evidence

| Run | Tests | Failures | Errors | Skipped | Result / evidence |
|---|---:|---:|---:|---:|---|
| Accepted Stage 5D full suite, before recovery | 989 | 0 | 0 | 7 | SUCCESS, stage5d-full-suite-final.log; original 172 XML reports inventoried in target/stage5e-recovery-existing-evidence.csv |
| Initial controller attempt | 0 | - | - | - | Test compilation failed on surviving generic lambda draft; stage5e-resume-controller.log |
| Controller/progress | 33 | 0 | 0 | 0 | SUCCESS, stage5e-resume-controller-2.log |
| Initial UI attempt | 6 | 0 | 1 | 0 | JavaFX toolkit cache access failed; 4 controller + 1 input tests passed, stage5e-resume-ui.log |
| UI state retry | 8 | 0 | 0 | 0 | SUCCESS, stage5e-resume-ui-2.log |
| Service/reference + weighted adapter | 33 | 0 | 0 | 0 | SUCCESS, stage5e-resume-services.log |

The final focused run follows listener-disposal and final-cancellation-boundary
edits. The full suite is required because Stage 5E changed relevant source
since the accepted Stage 5D result. Prior Stage 5C/5D standalone reference and
benchmark runs are accepted from original logs; no separate reference or
benchmark rerun is warranted. No interrupted Stage 5E run was identifiable at
recovery; failed attempts above occurred during this session, not the crash.
## Final file inventory

Added production files:

- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/SocialSecurityAnalysisJobCoordinator.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAnalyzerJobController.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAnalysisProgressModel.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAnalyzerInputs.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAnalysisFailurePresentation.java`

Added/recovered tests:

- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAnalyzerJobControllerTest.java` (recovered draft)
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAnalysisProgressModelTest.java` (recovered draft)
- `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/Stage5EWeightedAdapterTest.java`

Added documentation: `STAGE5E-ANALYZER-JOB-LIFECYCLE.md`.

Modified production files:

- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/IntegratedSocialSecurityStrategyComparisonService.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityMortalityWeightedClaimingGridCalculator.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecuritySurvivorClaimingOptimizationCalculator.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/MainApplication.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisProgressView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java`

Modified tests:

- `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/IntegratedSocialSecurityStrategyComparisonServiceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityMortalityWeightedClaimingGridCalculatorTest.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecuritySurvivorClaimingOptimizationCalculatorTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/controller/ApplicationControllerTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialogStateTest.java`

The standard full suite includes earlier exact-output correctness tests in
classes named Benchmark (including Stage 5A/5C2). No standalone benchmark was
launched; the opt-in Stage 5D performance benchmark remains disabled. Existing
historical standalone reference logs report Stage 5C2 105/0/0/1,
Stage 5C3 69/0/0/2, Stage 5D references 149/0/0/3 and Stage 5D focused
37/0/0/1 (tests/failures/errors/skipped). The final pre-recovery Stage 5D full
suite is the authoritative accepted source-state evidence; it supersedes
older standalone runs if later Stage 5D edits affected their dependencies.
## Completion (2026-09-09)

All checklist items A-Z are COMPLETED for Stage 5E. The final focused run
passed **58/0/0/0** at 07:20:05 -04:00
(`stage5e-resume-final-focused.log`). It covers coordinator/controller (32),
progress/failure presentation (2), real weighted adapter (1), UI/state (9),
and the two SS-only services (7 each). Separately accepted final-source
focused evidence includes Quick (10) and deterministic exhaustive (8) in
`stage5e-resume-services.log`, input (1) and application controller (4) in
`stage5e-resume-ui.log`. Counts overlap and must not be added as unique tests.

The **one full Maven suite** completed BUILD SUCCESS at
**2026-09-09 07:24:06 -04:00**, duration 02:00, with
**1,031 tests, 0 failures, 0 errors, 7 skipped**.
Evidence: `stage5e-resume-full.log`, all **175** Surefire XML reports,
and `target/stage5e-final-evidence.csv`. The XML aggregate independently
matches the Maven summary. JVM arguments in those reports confirm
`-XX:ActiveProcessorCount=2` and software rendering. The Stage 5D benchmark
report confirms its opt-in system property was absent and its test skipped.

Reference class sets included in this full run (not separately rerun):

| Original reference grouping | Final-suite tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| Stage 5C2 reference | 105 | 0 | 0 | 1 |
| Stage 5C3 reference | 69 | 0 | 0 | 2 |
| Stage 5D references | 149 | 0 | 0 | 3 |
| Stage 5D focused | 37 | 0 | 0 | 1 |

Groupings overlap. Final-source analyzer UI/state coverage is 9 state tests,
1 input test, and 4 application-controller tests. The service/adapter focused
run was 33 tests; its affected SS-only and weighted cases were included again
in the final focused run after the final cancellation-boundary edits.

Final review: `git status --short`, `git diff --check`, and `git diff --stat`
completed. No conflict markers, truncated files, obsolete activeTask logic,
new debug instrumentation, new TODOs, scattered Platform.runLater calls,
or unrelated changes were found. All production/test changes preceded the
successful full run except two whitespace-only line breaks in the dialog;
a before/after non-whitespace comparison confirmed identical source tokens,
so they do not invalidate verification. Documentation was completed afterward.
Logs, CSV inventories, extracted DLLs and the JavaFX cache remain ignored in
the workspace. There are 9 added/untracked deliverable files and 13 modified
tracked files. No files were deleted and no commit was made.

No deterministic or weighted output differences were detected in exact
reference tests. Stage 4, Stage 5A/5B, Stage 5C1/C2/C3, Stage 5D production,
projection formulas, ranking and persisted models are unchanged. Stage 5D
production default is 4, hard cap 8. Recovery financial verification used
at most 2 representative workers. No benchmark needs rerunning for Stage 5E
correctness. Cancellation waits for a safe active cell/strategy boundary;
that is an intentional latency limit, with cleanup off JavaFX.

Stage 5F is ready to build on the verified lifecycle and weighted adapter.
Its actual weighted UI/settings/scenario-preparation integration remains
Stage 5F work, not an unfinished Stage 5E implementation.