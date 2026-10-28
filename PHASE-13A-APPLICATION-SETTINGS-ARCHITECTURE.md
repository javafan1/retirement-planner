# Phase 13A — Application Settings Architecture Audit

## 1. Executive summary and baseline

**Audit only. No production code, tests, resources, plan schemas or financial data changed.** A small application-level Settings dialog is feasible using the existing settings repository and File menu. Theme coverage requires targeted replacement of presentation color literals; simply changing the main Scene background would leave substantial light-only UI.

Verified baseline on 2026-10-28: branch `main`, HEAD `cadeb3eb9e22c543f2091dbf711c054cbcb88f25` (`Complete Phase 11-12 projection history, analyzer persistence and freshness`). Initial tracked modifications: **none**. Initial untracked files: **none**. Staged files: **none**. Previous Phase 11–12 functionality is now committed at this HEAD, unlike the earlier reports' working-tree inventories. This audit adds only this report; generated inspection inventories and Maven logs are ignored under `target/`.

Recommendations below are proposals requiring Phase 13B authorization, not implemented behavior. Existing source takes precedence over older reports, including AGENTS.md's older baseline caveat. Read project instructions, relevant Phase 11D/12D report sections and inspected current production sources/tests. Existing simulated-dark preview evidence does not establish application-wide Dark support.

## 2. Existing architecture inventory

| Area | Verified implementation |
|---|---|
| Application startup | `ui/MainApplication.start` constructs `MainWindow`, calls `setStage`, creates its Scene, adds `/css/application.css`, then shows the Stage. There is no production theme selection or theme manager. No explicit application user-agent stylesheet override was found; standard controls use JavaFX's default Modena styling. |
| Main navigation | `ui/MainWindow.createMenuBar`: File, Analysis, Help. File has New, Open, Save, Save As, Save Current as Baseline, Exit. No Settings menu/command. `createTabPane` starts with Summary and Yearly Detail, followed by input views. No separate main toolbar was found; individual result views have action buttons. `createScene` uses 1200×800. |
| Ownership | `MainWindow` coordinates through `ui/controller/ApplicationController`; Settings must not call its financial plan-change/refresh callbacks. Existing UI edits may be pending, not applied to the plan. Opening Settings must not collect/apply those drafts. |
| Modal patterns | `SocialSecurityStrategyAnalyzerDialog` owns a separate WINDOW_MODAL Stage and Scene. Monte Carlo dialogs extend JavaFX `Dialog<Void>`, are WINDOW_MODAL/resizable, contain scrolling views and Close. Wizard, Baseline Details, Break-Even, History, alerts and yearly details use their own dialog/window paths. Owner assignment alone does not guarantee their roots inherit the main Scene's author styles. |
| Preferences model | `application/settings/ApplicationSettings`: mutable bean with `automaticallyOpenLastPlan=true`, nullable `lastOpenedPlan`; no theme or directory. `ApplicationSettingsRepository` exposes load/save. |
| Preferences storage | `persistence/JsonApplicationSettingsRepository`: default `~/.retirement-planner/settings.json`, injectable test path, formatted Jackson JSON, synchronized load/save, existing `AtomicFileWriter`. No new store is necessary. |
| Controller integration | Controller constructor loads preferences. `openLastPlan` uses the auto-open flag and exact last path. Successful Open/Save/Save As update `lastOpenedPlan` and call `saveSettingsQuietly`. A preference write failure warns without rolling back a successful plan operation. There is no public settings editing transaction/UI yet. |

### Stylesheet inventory (all nine current resources)

| Resource | Scope and theme gap |
|---|---|
| `css/application.css` | Currently only help-icon blue/hover literals. Not an application-wide theme. |
| `css/results-summary.css` | Light page/card/button backgrounds; literal title, subtitle, border, metric/status colors and chart/table presentation. |
| `css/projection-chart.css` | Grid lines, category/total lines and legend colors, Roth/RMD bands and period-label backgrounds/text. |
| `css/chart-timeline.css` | Social Security claiming event line and light label backing, border/text. |
| `css/new-plan-wizard.css` | Light review cards, sidebar/navigation, warnings/errors and focus states. Shared `WizardDialogSupport` and `NewRetirementPlanWizard` load it. |
| `css/monte-carlo.css` | Fixed light backgrounds, foregrounds, validation/status colors, chart grids, fan/difference series, legends and zero/reference lines. |
| `css/claiming-strategy-heat-map.css` | Light container/border, fixed dark cell/legend ink, fixed focus effect. Tier and selected/optimal colors already use `-heat-map-*` looked-up values. |
| `css/break-even.css` | Light cards, fixed text and grid colors, directional markers and selected table styling. |
| `css/baseline-details.css` | Mostly Modena looked-up colors, amber translucent changed overlay, derived unknown background. Good reusable pattern, still requires contrast checks under both themes. |

Verified Java-side color locations: `MainWindow` funding notice; `ui/summary/ProjectionYearDetailsPane` labels; `ui/socialsecurity/AnalyzerNavigationView` selected border; `LongevityWeightedIntegratedView` limitation border; `SocialSecurityStrategyAnalyzerDialog` three stale labels; `ProjectionAnalyzerHistoryDialog` individual age buttons; `ui/montecarlo/MonteCarloFanChart` single-point median/reference colors. Transparent chart hit targets in fan/difference charts are intentional interaction geometry, not theme defects. Search found 19 matching Java lines for color literals/direct Color usage in `ui`; this is a search inventory, not a count of all colored UI nodes. Full matched locations are in ignored `target/phase13a-color-inventory.txt`.

`ui/controls/HelpIcon` creates long-lived wrapped tooltips with inline font size only; its color comes from CSS/default tooltip styling. ComboBox lists, DatePicker popups, context menus and tooltips are separate popup surfaces needing verification. There is no DirectoryChooser currently. Existing test helpers simulate Dark by setting root `-fx-base`, `-fx-background`, inner-background and text colors; those test-local settings are not a production theme contract.

## 3. Proposed Settings UI

Add **File → Application Settings…**, separated from plan actions and before Exit. No new top-level navigation tab or toolbar is needed. Use a compact, resizable owner-modal `ApplicationSettingsDialog`, approximately 600×360 logical pixels initially, constrained to the available screen. Keep one instance per owner; owner-relative positioning, keyboard traversal, accessible labels and Esc/close-as-Cancel.

Controls:

- Theme: explicit **Light / Dark** choice; default Light for existing users. No automatic OS-theme mode in this phase.
- Default retirement-plan directory: text field, **Browse…** (`DirectoryChooser`) and **Use system default** (clear preference). Display explanatory text distinguishing this from existing-plan Save and report export locations.
- Save / Cancel; inline validation and a clear save-failure message. No plan Save prompt, calculation, projection refresh or analyzer rebinding.

Recommended semantics: edit a detached settings draft. Theme selection previews immediately across application windows without persisting. Directory drafts do not affect choosers until Save. Save validates and commits only these preference fields; successful durability closes the dialog. Cancel, Esc and window close restore the last committed theme and discard directory drafts. A failed write leaves the draft/dialog available for Retry or Cancel and must not falsely report Saved. Do not open a second Settings editor during preview. New windows opened during preview must use that preview theme, then follow rollback/commit.

If asynchronous save is used, disable Save/Cancel and window-close completion while the small atomic transaction is committing, with visible progress; do not treat a canceled dialog as proof that an already committed write was canceled. Preview is not a plan modification. Existing auto-open preference remains unchanged and need not be added to the initial UI.

## 4. Theme-management architecture

Introduce one application-owned `ApplicationThemeManager` in the UI layer, initialized before the main Stage is shown. Keep Modena; add application author styles with semantic JavaFX **looked-up colors** (not browser CSS `var()` syntax): surface, elevated surface, text, muted text, border, accent/focus, warning/error foreground/background, selection and chart grid/annotation colors. Seed appropriate Modena properties (`-fx-base`, `-fx-background`, control/text/selection/focus colors) and define light/dark application tokens. Preserve layout, geometry and calculation models.

Recommended resources: `theme-light.css`, `theme-dark.css` and shared semantic selectors in existing component CSS. Each theme sheet defines tokens on a manager-owned root style class applied consistently to scene roots/dialog panes/popup roots. Existing component styles consume those tokens. Replace narrowly identified inline chrome/status color values with looked-up values or style classes; font/padding inline styles can remain. Avoid broad conversion of unrelated control code.

Window lifecycle:

1. Register the main Scene explicitly before show. Track newly appearing application Windows/Scenes, including Dialog and Stage, with one FX-thread manager and an idempotent stylesheet/root-class installation.
2. Track scene/root replacement, unregister old listeners and closed windows, and avoid retaining disposed dialog graphs. Application shutdown removes global listeners.
3. Refresh all registered roots immediately on preview/commit/rollback. Preserve existing component sheets and inline geometry; do not repeatedly append duplicate stylesheets or replace Scene contents.
4. Handle popup surfaces explicitly. Verify menus, tooltips, ComboBox lists and DatePicker calendars receive the effective theme before first visible frame. An observable Window registry alone may be too late to prevent a flash; add narrow pre-show registration in custom Stage/Dialog creation paths where required. Do not rely on owner inheritance or unmanaged polling.
5. Use public JavaFX APIs, not `com.sun.*` hooks. No scene-wide `setStyle` replacement that erases existing heat-map palette definitions.

### Financial visualization and PDF invariants

Keep exact heat-map tier hex values, thresholds, optimal/selected borders, unavailable color and cell contrast unchanged (`ClaimingHeatMapPalette`, renderer and existing PDF palette). Those cells can deliberately retain dark ink on their fixed light tier backgrounds in both themes. Theme their surrounding surfaces, headings, details and focus visibility. Preserve Stage 1 versus integrated objectives and accessibility markers.

Chart grid/background/axis/annotation/legend **chrome** should adapt. Preserve financial series identities and established color meaning; improve surrounding contrast and marker strokes without changing values/order or financial palette semantics. Direct single-point paints need style classes if theme-specific contrast is required. Do not replace invisible hit-test geometry with visible theme fills.

PDFs use detached report data and PDFBox drawing (`PdfReportSupport`, `IntegratedAnalyzerPdfExporter`, Monte Carlo and Break-Even exporters), not live scene screenshots. Keep their explicit print palette separate. Do not make `ClaimingHeatMapPalette` a mutable theme source or pass a theme through report DTOs. Results Summary CSV/PDF and analyzer PDF source values, timestamps, rankings and provenance must remain unchanged. Prove theme-independent PDF text/drawing colors with fixed export times; PDF file bytes alone are not a reliable equality check because document metadata can vary.

Native Windows title bars, file/directory chooser chrome and OS high-contrast integration are controlled by the platform. JavaFX author CSS does not certify or guarantee those surfaces become Dark. Document that boundary instead of introducing native dependencies.

## 5. Default retirement-plan directory

### Exact integration points

| Workflow | Current behavior | Proposed boundary |
|---|---|---|
| `MainWindow.onOpen` | Creates JSON FileChooser, no initial directory; confirmation of plan departure remains authoritative. | Apply central validated plan-directory resolver just before show. |
| `MainWindow.choosePlanSaveFile` / `onSaveAs` | JSON chooser with `RetirementPlan.json`; no initial directory. Overridable test seam. | Same resolver; preserve chooser cancellation, overwrite confirmation, validation, Save As identity and snapshot clone workflow. |
| `MainWindow.onSave` | Existing file uses controller ordinary Save; untitled plan goes through Save As. | Existing file path must never be redirected. Untitled first Save uses the preference via the existing chooser. |
| New wizard / `onNew` | Creates unsaved plan; no path selection during wizard completion. | Do not add a wizard file step; first explicit Save uses existing Save As path. |
| Recovery / startup | `openLastPlan` uses exact persisted path; recovery reviews backup and requires appropriate Save As. | Do not override last-plan reopening or recovery ownership restrictions with the preferred directory. Recovery Save As uses the central chooser without suggesting unsafe original/backup overwrite. |
| Analyzer parameters/components | Paths derive from durable plan identity/path, with bounded atomic storage. | No directory preference involvement after a plan path is selected. |
| CSV/PDF exports | Choosers in Results Summary, Break-Even, individual/Monte Carlo PDF actions, SS dialog and History. | Keep unchanged: this is a retirement-plan directory preference, not an export directory preference. |

No recent-files list/menu exists. `lastOpenedPlan` records successful Open/Save/Save As; it is neither a configured default folder nor updated by a canceled chooser. There are no production `setInitialDirectory` calls found. Do not depend on native chooser remembered state for consistent behavior.

Recommended resolver order for **both** applicable plan choosers: (1) valid explicit preferred directory; (2) valid active file parent; (3) valid last-opened file parent; (4) valid user home; (5) leave initial directory unset for the native chooser. This makes the preference deliberate and consistent, while ordinary Save remains path-bound. Choosing a different location once does not silently replace the saved default; it only updates existing last-plan history after success.

Resolve absolute normalized paths without expanding executable shell syntax or scanning folders. Validate path syntax, directory existence and access; catch invalid path/security failures. Explicit user entry at Settings Save should require an existing readable/writable directory (Open still needs read and Save needs write); don't create directories silently. Allow clearing the preference. Browse uses `DirectoryChooser` with a safe initial directory, retains the draft when canceled and never saves a plan.

Revalidate at each chooser open: a directory may have been deleted, disconnected or become inaccessible after preference Save. Fall back without throwing or clearing the stored preference; expose a concise nonmodal notice. Access predicates are advisory on Windows ACL/network paths; actual chooser/file write errors remain authoritative and must use existing failure handling. Avoid repeated network probes on the FX thread: validate potentially slow paths through a bounded worker, then recheck dialog ownership before showing a chooser. No new executor per chooser.

## 6. Preference persistence recommendation

Extend the existing application settings bean/repository, not `RetirementPlan`, plan JSON, projection components or analyzer settings. Proposed on-disk document:

```json
{
  "automaticallyOpenLastPlan": true,
  "lastOpenedPlan": "C:\\Plans\\Example.json",
  "theme": "LIGHT",
  "defaultPlanDirectory": "C:\\Plans"
}
```

Use a small allowlisted theme enum, nullable/absent directory meaning no explicit preference, and defaults for absent new fields. An application-preferences schema version is not necessary for two additive fields; do not add a retirement-plan schema version. Old settings remain readable, preserving auto-open and last-plan behavior. Do not rewrite preferences merely to add defaults on startup. Bounded path strings/document size should be validated; no arbitrary polymorphic deserialization.

Existing repository facts: load failure/null/malformed JSON falls back to defaults, records a warning and marks storage damaged; later save refuses to overwrite the original until the documented backup/rename/restart recovery. Missing file returns defaults. Writes use a staged verified atomic replacement with `AtomicFileWriter`, with `planBackup=false` (no plan-style adjacent backup contract). Atomic-move failure preserves the prior file rather than using a non-atomic fallback. Do not advertise automatic settings backup recovery that does not exist.

Retain the corruption-preservation policy. For a new invalid enum/path value, recommend safe defaults plus an actionable warning and preserve raw data until explicit Settings Save; never silently truncate or overwrite unsupported preference values through routine last-plan writes. This requires deliberate parsing/validation behavior: current Jackson's default unknown-field/enum rejection can damage the whole settings load. Do not simply enable global unknown-property ignoring. Decide whether bounded unknown future fields are retained or unsupported documents become read-only; the conservative recommendation is preserve/lock unsupported documents and permit session-only defaults until recovery. This affects application preferences only.

Use a small application-owned settings service/edit transaction around the existing repository. All preference producers, including `ApplicationController.saveSettingsQuietly`, must update the **latest committed settings** by field, rather than persisting a stale whole-dialog copy. Otherwise a Settings draft can overwrite a newer `lastOpenedPlan`, or an ordinary plan Save can overwrite newly selected theme/directory. Capture immutable write values, serialize operations through one bounded queue/service, publish accepted preference fields only after durable success, and preserve the original successful-plan-operation warning policy. Repository synchronization alone does not solve stale mutable-object snapshots. Preserve settings on Save As without treating it as a theme change or financial revision.

Failed explicit Settings Save: keep previous durable preferences, allow Retry/Cancel, do not claim persistence. Failed incidental last-plan update: retain successful plan operation and existing local-storage notice. Theme preview rollback must use the latest committed theme, not stale plan context. No plan is dirtied, projected, saved or rebound by preferences.

## 7. Proposed implementation inventory

Paths below are relative to `src/main/java/com/daviddunn/retirementplanner/` unless stated otherwise. These are anticipated Phase 13B edits, **not changes made in this audit**.

Core modifications: `application/settings/ApplicationSettings.java`, `ApplicationSettingsRepository.java` if status/edit contracts require it; `persistence/JsonApplicationSettingsRepository.java`; `ui/controller/ApplicationController.java` (route existing preference producer only); `ui/MainApplication.java`; `ui/MainWindow.java` (menu, chooser application, funding-notice tokens).

Proposed additions: `application/settings/ApplicationTheme.java`, `ApplicationSettingsService.java`; `ui/settings/ApplicationSettingsDialog.java`, `ApplicationThemeManager.java`, `PlanDirectoryResolver.java`. Keep names/package placement adjustable to the smallest coherent implementation. A pure directory resolution policy should be separated from native chooser display; there is no need for a general dialog framework.

Resources: add `src/main/resources/css/theme-light.css`, `theme-dark.css`; narrowly update all nine inventoried CSS resources as needed to consume shared tokens while preserving layout and fixed heat-map colors.

Targeted Java presentation edits: `ui/summary/ProjectionYearDetailsPane.java`, `ui/socialsecurity/AnalyzerNavigationView.java`, `LongevityWeightedIntegratedView.java`, `SocialSecurityStrategyAnalyzerDialog.java`, `ProjectionAnalyzerHistoryDialog.java`, `ui/montecarlo/MonteCarloFanChart.java`. Theme registration may require narrow pre-show hooks in `MonteCarloAnalysisDialog`, `MonteCarloStrategyComparisonDialog`, `wizard/NewRetirementPlanWizard`, `wizard/WizardDialogSupport`, Baseline/Break-Even/History/year-details dialog constructors. Verify global public-window registration first; do not edit every dialog speculatively. `HelpIcon` needs changes only if popup testing shows registration/style hooks are missing.

Tests: extend `persistence/TransactionalSettingsTest.java`, `ui/PersistenceRecoveryWorkflowTest.java`; add settings service, directory policy, Settings dialog and theme lifecycle tests under matching packages. Extend existing MainWindow tests or add a narrow chooser-policy test using `choosePlanSaveFile`'s seam, plus `ui/charts/ProjectionChartViewTest`, `ui/socialsecurity/SharedClaimingAgeGridTest`, Baseline Details and wizard layout tests for both themes. Existing PDF fidelity tests should verify theme isolation. No proposed financial-domain, result DTO, fingerprint, snapshot schema, PDF palette or golden-resource edits.

## 8. Risks and compatibility

- Partial theme coverage: child stylesheets and inline literal colors outrank inherited tokens. Test computed styles, not just existence of a dark CSS file.
- Popup ownership/style inheritance and late window registration can cause white flashes or unreadable tooltips. Use explicit lifecycle tests and native confirmation.
- Fixed heat-map tiers require intentional fixed foreground contrast; global dark text overrides must not turn these cells white-on-pastel.
- Existing charts have known simulated-dark legend contrast limitations in Phase 12D. Address presentation contrast within this phase's approved palette boundary; do not alter series/data semantics.
- Preview cancellation and background preference writes can race with automatic last-plan updates. Use detached drafts and serialized field updates.
- Network/disconnected paths can block UI or change access between validation and use. Fallback is not a promise that a later save will succeed.
- Modena defaults, disabled/selected/focused states and system-owned window chrome need separate acceptance. No unsupported native theming or dependencies recommended.
- Settings actions must not call `markModified`, invalidate projection caches, collect input drafts, change plan identity, trigger parameter persistence or alter Current/Baseline/History. PDF exporting remains print-styled and calculation-free.

## 9. Test plan and actual audit verification

Future automated tests should cover defaults/old JSON, exact preservation of existing settings, malformed/null/unknown/unsupported preferences, size bounds, injected atomic write failure, retry, concurrent last-plan updates, detached Cancel, commit only after success, and no plan writes/revisions. Directory tests should cover explicit/current/last/home/unset precedence; deleted/non-directory/access-denied/invalid paths; Browse cancellation; untitled Save, existing-file Save, Save As, recovery and chooser cancellation. Use injectable path accessibility and chooser seams rather than assuming an administrator's filesystem denies reads.

Theme tests should exercise main and already-open/new/reopened Stage/Dialog roots, root replacement, menu/date/list/tooltip popups, preview/Cancel/Save/restart, no duplicate sheets/listener leaks, no data or dirty-state changes, and computed text/background/selection/focus colors. Retain existing cell tier/optimal/selection values exactly. Render charts, SS/individual/integrated grids, Monte Carlo views, History, Baseline Details, Break-Even, yearly details, wizard warnings/review and alerts at 1180×820 and 900×650 in each theme. Compare original PDF report values and print colors before/after switching. Preserve all seeded/golden expectations.

Executed existing tests only, using IntelliJ bundled Maven, `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`, `-DargLine=-Xmx4g`:

`-Dtest=TransactionalSettingsTest,PersistenceRecoveryWorkflowTest,AssumptionsMainWindowTest,RothConversionMainWindowTest,SharedClaimingAgeGridTest,ProjectionChartViewTest test`

**62 total, 62 passed, 0 failed, 0 errors, 0 skipped; BUILD SUCCESS; 23.905 seconds**, finished `2026-10-28T07:07:23-04:00`. Log `target/phase13a-focused.log`. The existing JavaFX unnamed-module startup warning occurred; it did not fail the tests. No tests were edited. Full Maven suite not run for this documentation-only audit; these tests verify existing behavior, not an unimplemented Settings feature. No new theme previews or native chooser interaction were performed.

`git diff --check`: **passed, exit 0**. Final working tree: this report is the only untracked file; no tracked modifications and no staged paths. Report UTF-8 validation passed. No staging/history operations performed.

### Native Windows acceptance for implementation

1. Open File → Application Settings by mouse/keyboard, with an unsaved plan and invalid unapplied drafts; no plan/draft state changes.
2. Preview Light/Dark, Cancel/Esc/close, then Save/restart. Verify the committed choice and existing last-plan auto-open behavior.
3. Inspect standard controls, menus/popups/tooltips, wizard, chart axes/legends/annotations, heat-map dollar text/markers, Current/Outdated notices, History, Baseline Details and Break-Even at both target sizes and Windows scaling settings.
4. Set directory with Browse, cancel Browse, type invalid/nonexistent/inaccessible paths, then delete/disconnect a saved folder. Confirm safe fallback and honest notices.
5. Verify Open and untitled Save/Save As use the approved directory order. Ordinary Save stays at its original path; Save As retains independent identities, snapshots and associations. Export directories remain unchanged.
6. Trigger a preference write failure; previous preferences and successful plan saves remain intact. Retry/Cancel are honest. Corrupt preference recovery preserves the original file.
7. Export representative PDFs in both themes; colors/financial data remain identical in meaning and formatting. No analysis executes, no financial revisions/dirty state or analyzer configuration changes.

## 10. Decisions requiring approval before Phase 13B

1. **UI:** File → Application Settings… as a small owner-modal dialog, detached draft, immediate application-wide theme preview, Cancel rollback, durable Save before closing; no OS-auto theme.
2. **Directory precedence:** explicit configured directory first for both Open and Save As, then active/last-plan parents, home, native default. Existing-file Save and PDF/CSV exports unaffected. No automatic creation or silent replacement of the preferred folder.
3. **Validation:** configured directory must currently exist and be readable/writable; later unavailability falls back with notice. Network directory latency requires bounded background validation, not unbounded FX-thread probes.
4. **Persistence:** extend existing application settings JSON; Light/no-directory defaults for old documents; one field-update settings coordinator/service for all producers. Preserve malformed/unsupported documents and existing recovery policy; do not silently overwrite future settings on incidental plan saves.
5. **Theme scope:** targeted semantic-token adoption across listed chrome/inline colors, keeping Modena, fixed heat-map financial palette and print/PDF appearance. No global UI redesign or dependencies; native title/file-dialog chrome remains OS-controlled.

Recommended stages: (B1) preferences model/service + atomic tests and directory policy; (B2) Settings dialog and chooser integration; (B3) theme manager + targeted CSS coverage and popup lifecycle; (B4) full regression and native acceptance. These are bounded work packages within a future authorized implementation, not authorization to begin it.

**Stop after Phase 13A. Approval of the above policies and Phase 13B implementation is required before source changes.**
