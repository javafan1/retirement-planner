# Phase 13B — Application Settings Implementation

## Executive summary

Implemented the four approved work packages: application preferences and directory policy, the owner-modal Settings UI, immediate Light/Dark theming, and focused integration/visual regression checks. File → **Application Settings…** edits application preferences independently of retirement plans. No Phase 13C work is included.

Baseline: branch `main`, HEAD `cadeb3eb9e22c543f2091dbf711c054cbcb88f25`. Initial tracked/index state was clean; the untracked Phase 13A architecture report was preserved. No staging, commit, push, reset, clean or discard operations performed.

**Ready for review and native Windows acceptance.** Final focused and complete Maven verification passed. Native Windows acceptance remains pending.

## 13B1 — Preferences, transactions and directory policy

`ApplicationSettings` adds allowlisted `ApplicationTheme` (`LIGHT`, `DARK`) and nullable `defaultPlanDirectory`; existing `automaticallyOpenLastPlan` and `lastOpenedPlan` remain. Missing new fields use Light/no explicit directory. Snapshots returned by `ApplicationSettingsService` are detached copies. These are application settings only, not accepted analyzer parameters or plan metadata.

The application-owned service is the preference write authority. Each update acquires a single write boundary, copies the latest committed state, changes only its own fields, writes through the existing repository, then publishes the new committed copy. Settings Save cannot overwrite a newer last-plan path; ordinary plan operations cannot overwrite a newly saved theme or directory. `ApplicationController.saveSettingsQuietly` now records only its last-plan field through this service, preserving the successful-plan/failed-preference-warning policy and existing auto-open behavior. Existing ordinary plan Save remains synchronous as before; this phase does not refactor plan I/O.

Explicit preference validation and commits run off FX. The commit executor is one daemon worker with a bounded 16-task queue; directory probes use a separate bounded two-worker/16-task queue. Probe futures time out after eight seconds. A timed-out validation does not later schedule a preference commit. Java/OS filesystem calls may remain blocked after timeout, but concurrency is bounded and daemon workers do not hold the application open. Queue rejection/failure is returned honestly. Writes have no artificial timeout that could claim failure while an acknowledged commit later succeeds. Shutdown closes the preference workers; normal Settings Save closes only after durable acknowledgement.

### Repository compatibility and integrity

`JsonApplicationSettingsRepository` retains `~/.retirement-planner/settings.json`, injected test paths and `AtomicFileWriter` with staged verification/atomic replacement and no non-atomic fallback. Preferences do not gain a retirement-plan schema or a separate new persistence framework. Example:

```json
{
  "automaticallyOpenLastPlan": true,
  "lastOpenedPlan": "C:\\Plans\\RetirementPlan.json",
  "theme": "DARK",
  "defaultPlanDirectory": "C:\\Plans"
}
```

Settings documents are bounded at 64 KiB, including bounded stream reads; directory strings are limited to 4,096 characters. Duplicate JSON fields, trailing JSON, unsupported fields/enum values, null theme and malformed documents are rejected. Existing missing-file defaults and damaged-file preservation/recovery remain: defaults plus a local warning; automatic saving cannot replace damaged/unsupported files until recovery. Repository saves validate the existing document and compare it with the last observed bytes to refuse externally changed preferences. This is conservative conflict detection, not a new inter-process locking protocol.

Atomic failures preserve the prior durable document and committed service state. Retry reuses the draft and runs no financial work. The existing settings repository has no plan-style backup recovery contract (`planBackup=false`); this implementation does not claim otherwise. Existing warnings still direct users to back up/rename damaged settings and restart. External-change conflicts likewise require reloading/restarting rather than silently overwriting another document.

### Directory policy

`PlanDirectoryResolver` implements:

1. Explicit preferred directory.
2. Active plan's parent.
3. Last-opened plan's parent.
4. User home.
5. Native chooser default (initial directory left unset).

Candidates must exist and be readable/writable. Invalid paths, missing/deleted/non-directory/denied candidates fall through safely. Explicit preference Save requires a usable directory, normalizes it to an absolute path, and never creates a folder. The explicit preference is retained if a later chooser has to fall back; fallback does not silently rewrite it.

`MainWindow.onOpen` and `choosePlanSaveFile` use the resolver. A small cancelable progress dialog yields the FX event loop while probing directories; no filesystem probe executes in its event handler. Before showing the native chooser the captured plan object and file path are rechecked. Explicit Cancel or a changed owner/path aborts without selecting a file. An unavailable preferred folder produces status feedback. If a folder probe fails, times out or cannot be queued, the native chooser remains available with no initial directory and an explanatory status; no unverified path is supplied. If the directory disappears between validation and native chooser invocation, the chooser likewise retries with its native default. Browse uses JavaFX `DirectoryChooser` with worker-resolved initial location, preserves its draft on cancellation and reports unavailable locations.

Existing-file Save stays at its current path. New plans still have no implicit file until explicit Save/Save As; their first chooser uses this policy. Startup last-plan reopening remains exact-path based. Save As, overwrite confirmation, recovery protections, plan UUID ownership, snapshot copying and analyzer component paths are unchanged. PDF/CSV chooser code was not changed.

## 13B2 — Settings dialog

The new File menu command opens a compact resizable WINDOW_MODAL `ApplicationSettingsDialog` owned by the main window. Theme controls display **Light / Dark**; no OS-auto selection. Directory editing includes **Browse…** and **Use System Default**. Labels/accessibility descriptions identify their controls; content is scrollable at reduced sizes.

The dialog starts from a detached preference copy. Theme selection previews immediately across application windows; directory typing is not published. Save disables editing/Cancel while validation/commit is outstanding and prevents window-close completion, then closes only on durable success. A failure keeps the dialog/draft available with an actionable generic notice and enables Retry/Cancel. Cancel, Esc and window-close use the Dialog's cancel lifecycle; hidden without commit rolls the preview back to the latest committed theme. It does not apply financial drafts or invoke ordinary plan Save.

The owner-modal dialog prevents competing user Settings editors through the main menu. Already accepted financial results, last-success analyzer slots, historical associations and current/baseline roles are never promoted or replaced by Settings.

## 13B3 — Theme lifecycle and CSS coverage

`ApplicationThemeManager` keeps Modena and owns application author styles. `MainWindow.createScene` initializes it from committed preferences before show. `MainApplication` delegates common CSS ownership to the manager and closes preferences/theme resources at application stop.

The manager tracks public JavaFX Window/Scene/root lifecycle events, applying the active theme to existing/new Stages, Dialogs and PopupControl scenes. Explicit Scene preparation is supported; Settings prepares its dialog Scene. Repeated application is idempotent per root. Old scene/root listeners and styles are removed; closing/reopening windows reattaches the current theme. Scene tracking and owned-common-style tracking use weak keys to avoid retaining unused prepared scenes. Existing component styles are preserved. No private JavaFX APIs or new dependencies are used.

`theme-light.css` and `theme-dark.css` define semantic surface/background/text/border/accent/warning/error colors and map them to standard Modena looked-up colors. Eight existing CSS resources consume standard looked-up colors for backgrounds, text, borders and annotation chrome; existing Baseline Details CSS already did so and was left unchanged. Common CSS supplies help/warning/outcome styles. Targeted inline literal changes cover the main funding notice, SS stale labels, weighted limitation border and yearly-details text. Font sizes, geometry, heat-map layout and navigation are unchanged.

Dark-specific rules cover chart axes/grids/legends, median/reference contrast, Summary hover/selection, wizard current-step/warning states and highlighted Break-Even rows. Monte Carlo single-point nodes gain style classes; data, stream identities and median/reference meaning are unchanged. Exact heat-map colors/thresholds, selected/optimal borders, unavailable fills and dark cell ink remain fixed in both themes. No changes were made to `ClaimingHeatMapPalette`, financial result models, rankings or render data.

### PDF isolation

No PDF exporter or print palette was changed. PDFs still render detached report data through PDFBox, independent of Scene CSS. The new regression exports one frozen SS report under Light and Dark with fixed report times, asserts equal extracted text/page counts and exact rendered pixels for pages 1 and 2 (summary and grid). Original numerical precision and historical provenance remain in existing adapters; theme state is not added to report DTOs.

## 13B4 — Automated verification

Established command configuration: IntelliJ bundled Maven, project-local `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`, `-DargLine=-Xmx4g`. No dependencies, Java version or build configuration changed.

Final focused selection:

`ApplicationPreferencesTest,ApplicationSettingsUiTest,TransactionalSettingsTest,PersistenceRecoveryWorkflowTest,AssumptionsMainWindowTest,RothConversionMainWindowTest,SharedClaimingAgeGridTest,ProjectionChartViewTest,SocialSecurityAndQuickPdfTest`

The initial focused selection passed **95 total, 95 passed, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS; 40.952 seconds**, finished `2026-10-28T08:00:18-04:00` (`target/phase13b-focused-acceptance.log`). Expanded final verification additionally includes `SinglePersonHouseholdUiTest`, `CompleteWizardEndToEndTest`, and `StateTaxWizardIntegrationTest`: **182 total, 182 passed, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS; 1:31 minutes**, finished `2026-10-28T08:14:06-04:00`. Log: `target/phase13b-focused-verified.log`.

Coverage includes old/default JSON; exact retained auto-open/last-plan fields; detached copies; interleaved field updates; bounded probe queue rejection; malformed/unsupported/duplicate/trailing/oversized documents; external unsupported-file protection; invalid directories; injected atomic failure and successful retry; full folder precedence/deletion fallback; absolute normalization; preview/Cancel/window-close; failed validation retaining a draft; durable Save/restart restoration; popup/root replacement/reopen lifecycle; Dark hover/current-step contrast; unchanged pending financial draft, plan bytes, dirty state, revision and accepted projection identity; Save As independent identity/preference preservation; exact grid colors and chart annotations; theme-independent PDF text/pixels.

Full suite final rerun: **3,600 total, 3,556 passed, 0 failures, 0 errors, 44 skipped; BUILD SUCCESS; 10:54 minutes**, finished `2026-10-28T08:25:02-04:00`, process exit 0 (`target/phase13b-full-verified-final.log`). No command-line exclusions were applied. The 44 existing opt-in/private/capacity/preview/assumption skips remain unchanged; current-run XML totals were cross-checked and individual reasons captured in ignored `target/phase13b-skipped.txt`. Stale earlier XML reports were excluded by final-run start time. Source/test/resource hashes confirm no implementation edits during the final rerun.

Pre-final full attempts were intentionally interrupted while correcting contrast/timeout-fallback details and are not counted as completed verification. Intermediate test-helper compile errors, an incorrect tooltip-test owner after root replacement, and a fixture that had not explicitly accepted/displayed a projection were corrected; the final test requires a non-null accepted snapshot and preserves it exactly. No assertions or financial expectations were weakened.

### Full-suite test-isolation defect and correction

The first completed full run (`target/phase13b-full-acceptance.log`) reported **3,600 total, 3,529 passed, 0 failures, 27 errors, 44 skipped; 10:42 minutes**. All 27 errors occurred in later restoration tests: `SinglePersonHouseholdUiTest` (3), `CompleteWizardEndToEndTest` (2), and `StateTaxWizardIntegrationTest` (22). Their restored projections were unavailable, causing null dereferences; preceding diagnostics contained rejected background work.

Root cause: the new Settings UI test used the controller's default application-global `AnalysisResultStore` and then shut it down in test cleanup. The singleton is intentionally application-lifetime and later wizard tests reused that closed worker. The correction is **test-only**: inject a temporary, test-owned result store and await its Save As copying before closing only that store. No projection/restoration lifecycle or financial code was changed to accommodate these errors. Expanded focused and complete reruns passed, including all previously affected cases.

`git diff --check`: **passed, exit 0**. Final inventory: **20 modified tracked files, 11 untracked files (including the preserved Phase 13A report), 0 staged paths**. No changes to financial-domain/app-result implementations, golden resources or `pom.xml`; no new dependencies. Standard JavaFX native-access/unnamed-module startup warnings remain environment notices, not test failures.

## Rendered inspection

Generated/inspected actual PNGs, not just creation logs:

- `target/phase13b-previews/settings-900-DARK.png` and `settings-1180-LIGHT.png`: readable inputs/actions, compact scrollable content, explicit directory explanation. Initial low-contrast Dark default button was corrected to white on dark blue and reinspected.
- `controls-1180-DARK.png`: readable standard field/list/check/menu/date controls; alternating rows and borders visible. ContextMenu and Tooltip theme registration are also automated checks.
- `tooltip-dark.png`: actual tooltip popup root has readable light ink on its Dark background. Root and descendant selectors are covered. Modena selected-text ink is explicitly corrected for the Dark accent/selection pairing; native text-selection checks remain on the checklist.
- `main-1180-DARK.png`, `main-900-DARK.png` and `main-900-LIGHT.png`: actual main window/menus/tabs/Summary and assumption sidebar. This review corrected Dark metric/assumption-title contrast and black metric icons. Reduced-width Summary cards/tables retain their existing horizontal scrolling; no broad Summary layout redesign was performed.
- `chart-900-DARK.png` and `chart-1180-LIGHT.png`: original investable-asset values/year mapping, category fills/total line, legend contrast and SS/RMD/Roth annotations visible. The bounded fixture has three prepared years; its narrow first-year period label remains dense, not a newly calculated financial result.
- `grid-900-DARK.png` and `grid-1180-LIGHT.png`: all 81 original pair positions, original selected/optimal marker, exact tier palette, unavailable cells, axes and details. This existing deterministic fixture intentionally has only two available pairs; unavailable positions are represented, not omitted.
- `target/phase11c-pdf/phase13b-dark-page-1.png`: original print styling, titles/values/timestamps and page numbering. Automated pixel equality with Light covers both summary and grid pages.

Tests also generate the corresponding Light/Dark Settings/control/chart/grid views at both 1180×820 and 900×650. A compact Settings modal intentionally stays approximately 600×390 within either owner size. Not every page of every preexisting analyzer dialog was manually inspected. These are automated JavaFX/PDF previews, not native Windows DPI, assistive-technology or system high-contrast certification.

## Exact file inventory

Modified production Java (`src/main/java/com/daviddunn/retirementplanner/`):

- `application/settings/ApplicationSettings.java`
- `persistence/JsonApplicationSettingsRepository.java`
- `ui/MainApplication.java`
- `ui/MainWindow.java`
- `ui/controller/ApplicationController.java`
- `ui/montecarlo/MonteCarloFanChart.java`
- `ui/socialsecurity/LongevityWeightedIntegratedView.java`
- `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java`
- `ui/summary/ProjectionYearDetailsPane.java`

New production Java under the same root:

- `application/settings/ApplicationTheme.java`
- `application/settings/ApplicationSettingsService.java`
- `application/settings/PlanDirectoryResolver.java`
- `ui/settings/ApplicationThemeManager.java`
- `ui/settings/ApplicationSettingsDialog.java`

Modified `src/main/resources/css/`: `application.css`, `results-summary.css`, `projection-chart.css`, `chart-timeline.css`, `new-plan-wizard.css`, `monte-carlo.css`, `claiming-strategy-heat-map.css`, `break-even.css`.

New CSS: `theme-light.css`, `theme-dark.css`.

New tests under `src/test/java/com/daviddunn/retirementplanner/`: `persistence/ApplicationPreferencesTest.java`, `ui/settings/ApplicationSettingsUiTest.java`.

Extended tests under the same root: `ui/charts/ProjectionChartViewTest.java`, `ui/socialsecurity/SharedClaimingAgeGridTest.java`, `ui/socialsecurity/SocialSecurityAndQuickPdfTest.java`.

New report: this file. Preexisting untracked `PHASE-13A-APPLICATION-SETTINGS-ARCHITECTURE.md` preserved unchanged. Exclude ignored `target/` logs/PNG/PDF evidence, `.codex-m2/`, runtime settings and plan files from any future commit. No financial golden resources, government rules, plan repositories, snapshot codecs or fingerprint implementations were edited.

## Limitations and native Windows acceptance

- Native title bars, native FileChooser/DirectoryChooser appearance and OS high-contrast behavior remain platform-controlled; JavaFX CSS does not force them Dark.
- Directory read/write predicates are advisory; permissions/disconnects may change before a later native chooser or actual Save. Existing save-failure handling remains authoritative. Blocking OS probes cannot always be interrupted; result waits/concurrency are bounded, and timed-out validation cannot commit later.
- Application preferences deliberately preserve damaged/unsupported/external-changed files instead of silently repairing them. Recovery/restart may be required before preferences can save again.
- Existing ordinary plan I/O is unchanged; preference validation/explicit commit and directory probes are off FX. No broad async Save refactor was introduced.
- Fixed heat-map fills/ink are intentional in Dark. Chart line identities and financial values remain stable while surrounding presentation adapts. Dense preexisting chart annotations can still require more space.
- Preview rendering is not native acceptance. Additional native popup/DPI/layout checks below remain required.

Native checklist:

1. Open File → Application Settings with clean and dirty plans, including invalid unapplied Assumptions/Roth drafts. Confirm no plan edits apply, no recalculation, no changed result status/dirty indicator.
2. Preview Light/Dark, then Cancel, Esc and window close. Confirm all visible windows roll back; Save, restart and confirm durable theme plus existing last-plan auto-open behavior.
3. Inspect menu/context/ComboBox/DatePicker popups, tooltips, wizard current/disabled/error/review states, Summary hover/selection, Baseline Details, Break-Even, SS/individual/integrated grids, Monte Carlo charts and History in both themes. Check 1180×820, 900×650 and Windows scaling.
4. Browse a preferred folder; cancel Browse; clear with Use System Default. Verify Open, untitled first Save and Save As use configured-first precedence; ordinary Save stays at its existing path. PDF/CSV locations remain unchanged.
5. Delete/disconnect the preferred folder and verify fallback/notification. Try invalid/unwritable locations; no folder is created and no false Saved message appears.
6. Exercise a preference write failure and Retry/Cancel. Check previous settings are retained. Corrupt/unsupported settings remain untouched with the existing recovery notice.
7. Verify Save As retains independent plan UUID/snapshots/associations and the selected application preferences; export PDFs in both themes and confirm unchanged print appearance and financial information.

**Stop after Phase 13B. Do not begin Phase 13C.**
