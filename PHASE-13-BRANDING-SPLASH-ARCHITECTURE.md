# Phase 13 — Aledrix Branding and Startup Splash Architecture

## Status and scope

Architecture audit only, 2026-10-28. No production code, tests, CSS, configuration or artwork changed. This report proposes a bounded implementation; it does not authorize it. This branding task is separate from the existing Phase 13A/13B Application Settings work, whose names and files remain unchanged.

Centralized branding and a splash are feasible without financial, plan-schema, fingerprint, analyzer or PDF changes. Two implementation prerequisites need explicit approval: preparation of clean Logo #5 assets, and narrowly separating startup loading from FX view construction. A splash placed before the existing synchronous MainWindow constructor alone would not meet the nonblocking requirement.

## Verified baseline and inventory

Branch `main`; HEAD `cadeb3eb9e22c543f2091dbf711c054cbcb88f25`. Index empty. Existing uncommitted Settings implementation is preserved, not treated as committed functionality. Current source, including that implementation, is authoritative.

Tracked modifications at entry (20):

- `src/main/java/com/daviddunn/retirementplanner/application/settings/ApplicationSettings.java`
- `src/main/java/com/daviddunn/retirementplanner/persistence/JsonApplicationSettingsRepository.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/MainApplication.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloFanChart.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/LongevityWeightedIntegratedView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/summary/ProjectionYearDetailsPane.java`
- `src/main/resources/css/application.css`
- `src/main/resources/css/break-even.css`
- `src/main/resources/css/chart-timeline.css`
- `src/main/resources/css/claiming-strategy-heat-map.css`
- `src/main/resources/css/monte-carlo.css`
- `src/main/resources/css/new-plan-wizard.css`
- `src/main/resources/css/projection-chart.css`
- `src/main/resources/css/results-summary.css`
- `src/test/java/com/daviddunn/retirementplanner/ui/charts/ProjectionChartViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SharedClaimingAgeGridTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAndQuickPdfTest.java`

Untracked files at entry (12):

- `PHASE-13A-APPLICATION-SETTINGS-ARCHITECTURE.md`
- `PHASE-13B-APPLICATION-SETTINGS-IMPLEMENTATION.md`
- `src/main/java/com/daviddunn/retirementplanner/application/settings/ApplicationSettingsService.java`
- `src/main/java/com/daviddunn/retirementplanner/application/settings/ApplicationTheme.java`
- `src/main/java/com/daviddunn/retirementplanner/application/settings/PlanDirectoryResolver.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/settings/ApplicationSettingsDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/settings/ApplicationThemeManager.java`
- `src/main/resources/css/theme-dark.css`
- `src/main/resources/css/theme-light.css`
- `src/main/resources/images/branding/aledrix-logo-reference.png`
- `src/test/java/com/daviddunn/retirementplanner/persistence/ApplicationPreferencesTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/settings/ApplicationSettingsUiTest.java`

The only new audit file is this report. Maven verification output goes under ignored `target/`. No staging, commit, cleanup or asset generation is performed.

## Existing startup, window and resource architecture

`MainApplication.start(Stage)` currently constructs MainWindow before creating/showing its Scene. It then schedules `showStartupPersistenceNotices`. There is no application-specific `init()`, splash, branding configuration or top-level fatal-startup handler. `stop()` closes window preferences/themes, analysis jobs and the idle result store.

`MainWindow()` creates ApplicationController and all input/result views, wires actions, builds menus/tabs, invokes `controller.openLastPlan()` and `loadCurrentPlan(true)`. Controller construction loads settings synchronously and creates a new plan. Last-plan reopening involves filesystem/JSON work. `loadCurrentPlan` uses the existing asynchronous saved-projection restoration path when applicable; otherwise it calls `refreshAllViews`, whose projection refresh can invoke `getCurrentProjection()` synchronously. Thus startup has both CPU/I/O work and mandatory FX control creation. Existing projection restoration already guards owner/result completion and must remain intact.

`MainWindow.updateWindowTitle()` is the single primary-title formatting point: `Retirement Planner - <filename or Untitled>` plus ` *` when modified. Recommend replacing only its base prefix with centralized `app.title`, retaining filename and dirty suffix. This preserves useful existing behavior while making the application identity Aledrix. If an exactly constant title without suffix is desired, approve that separately.

Help contains an About menu item without an action in `createMenuBar`. Do not implement About in this scope. Dialogs use descriptive operation titles; no bulk renaming is necessary. Search of current main/test sources found no application `getIcons()` installation or existing branding integration.

Maven already packages `src/main/resources` conventionally. Java 25 / JavaFX 25, PDFBox and existing dependencies suffice. Load packaged branding through classpath streams, not source-tree filesystem paths, so packaged JAR execution works. The concept sheet need not be loaded by production code, even if packaged as a resource.

## Logo inspection and asset preparation

Viewed the actual `src/main/resources/images/branding/aledrix-logo-reference.png`: a 1536 × 1024 concept sheet, 1,716,794 bytes. SHA-256: `805458B37F394CDB0C2343E53C8ECECE6CECD2857A9906F1060256E1B1E8306F`.

Logo **#5 is second row, first column**. It has the requested blue-gradient A, navy lower/right stroke and sweeping blue and muted-gold elements. Other numbered designs, app-icon examples and monochrome examples are not approved substitutes. In particular the bottom app-icon A lacks the exact #5 gold treatment and is not a production asset for this task.

The selected symbol occupies approximately x=94–284, y=335–470 (about 190 × 135 source pixels), above the separate ALEDRIX wordmark. This is an inspection estimate, not an extraction specification. Its background is shaded/off-white, not transparent; antialiasing contains background colors. Cropping even this symbol alone leaves a rectangular/background fringe; cropping its entire panel also includes unwanted wordmark, tagline and panel edges. Enlarging it does not restore detail. It is insufficient for a clean large/high-DPI splash without additional asset preparation.

Preferred next step: obtain the original transparent high-resolution/vector Logo #5 artwork. If unavailable, approve a faithful traced reconstruction, with human visual comparison before integration. Preserve A geometry, overlapping arc shapes, blue/navy/gold gradients and negative space. A trace cannot be claimed mathematically identical to an unavailable vector original. Do not generatively redesign the mark or substitute another sheet design.

Proposed outputs: transparent `aledrix-logo.png` around 1024 pixels on its long dimension, and transparent/padded square `aledrix-icon.png` at 256/512 pixels, both derived from the same approved master. Optional SVG is a design-source asset; JavaFX Image does not natively decode SVG, so do not add an SVG dependency. Raster output should have correct alpha edges on both themes. A dark-theme mark variant is unnecessary unless actual preview contrast demonstrates a need; any variant needs approval and must remain Logo #5.

The concept sheet remains byte-for-byte unchanged. Production must never fall back to displaying the sheet or another numbered logo. Missing production assets should yield a text-only splash with a nonfatal local diagnostic, not a crash or unrelated mark.

## Proposed branding configuration

Introduce an immutable `ApplicationBranding` record/value object in `application.branding` and a small read-only `ApplicationBrandingLoader` in persistence. Load once at startup and pass the resolved instance to the window/splash; no static mutable branding preferences.

Packaged `/application-branding.properties`:

```properties
app.title=Aledrix Retirement Planning Science
app.productName=Aledrix Retirement Planning
app.tagline=The Science of Retirement Planning
app.logo=images/branding/aledrix-logo.png
```

Proposed optional external file: `${user.home}/.retirement-planner/application-branding.properties`, next to but independent of `settings.json`. Precedence is packaged validated defaults, followed by valid external fields. Missing file is normal; no file is created/written. No registry, environment variable, current-directory search or additional system-property override is needed initially. Changes require restart; no file watcher or Settings fields.

Use explicit UTF-8 Reader decoding. Suggested bounded contract: 16 KiB document, four recognized keys, title/product at most 160 characters, tagline at most 240, logo resource name at most 256. Reject duplicate keys rather than silently taking the last; reject malformed escapes/control characters/blank required text. Unknown keys produce a nonfatal warning and are ignored, not executed. A malformed/unreadable/oversized external document is ignored as a whole; otherwise an invalid recognized field falls back to its packaged value with a diagnostic. Preserve the external file untouched. Never log arbitrary file contents or financial paths. A bad packaged configuration is a build/test defect; retain a small validated emergency default so users can still open the application.

Recommended initial `app.logo` contract is a normalized, case-sensitive **classpath resource name**, confined to `images/branding/`, without leading slash, traversal, URI schemes or network access. Resolve with a classpath stream and verify a bounded PNG (suggested 8 MiB and 4096 × 4096 maximum, checked before full pixel allocation). Reject unsupported image data safely. External configuration can choose another already packaged permitted asset; it does not permit arbitrary remote images. Replacing the product text needs no recompile. If replacement image files outside the package are required, that is a separate approval decision: a bounded sibling local-asset directory can be added later; do not ambiguously treat the same string as either classpath or filesystem.

The icon may be a fixed companion classpath resource initially; avoid inventing an undocumented `app.icon` preference. A future explicit branding field can centralize that path if approved. Neither branding nor its diagnostics enters retirement-plan JSON, projection documents, analyzer parameters or financial fingerprints.

## Proposed splash presentation and lifecycle

Use a small `StartupSplashView` with a normal JavaFX layout (StackPane/VBox), an undecorated nonmodal splash Stage and constrained responsive sizing, approximately 560 × 370 logical pixels. Logo ImageView preserves ratio (roughly 160–200 logical pixels wide); product text is separate and wraps if overridden; tagline is separate italic text. Use installed JavaFX system fonts, no new font dependency. Apply navy/blue/gold sparingly; generous spacing and no decorative financial charts. A single neutral `Initializing…` label is enough; indeterminate activity only while actual asynchronous initialization is pending, never invented percentage progress.

Theme uses existing `-app-background`, `-app-surface`, `-app-text`, `-app-muted` and accent roles, with local splash styles only. Do not recolor chart series, heat-map tiers or PDF palettes. Supply high-resolution PNGs, preserve ratio and allow JavaFX scaling; test Windows 100/125/150/200% DPI and monitor transitions. Native taskbar/icon display still needs Windows acceptance.

### Required startup sequencing change (proposal, not implemented)

1. Introduce one application-owned startup context: resolved branding, existing settings service/committed theme and theme manager. Load bounded configuration/preferences outside FX (Application.init is suitable for non-FX preflight); create no Stage, Scene or controls there. Loading before start means the splash cannot cover JVM/JavaFX bootstrap or preflight I/O; do not claim otherwise.
2. In start, initialize the existing theme manager once, prepare/show the splash and yield to a real JavaFX pulse. No minimum display timer. A one-shot pulse callback can start the next phase; `show()` followed by a long constructor in the same handler does not guarantee a rendered splash.
3. Separate last-plan I/O and any existing initial calculation from FX view construction through a narrow startup orchestration boundary. Use a single bounded startup task only if needed; the verified synchronous loading/calculation path establishes that need. Existing projection-component restoration continues using its existing bounded repository worker and ownership guards; do not put startup work on analyzer admission queues. Do not move MainWindow construction, listeners, Scene creation or all of ApplicationController wholesale onto a worker. Prepare isolated non-FX data, publish once to the owned controller through its existing acceptance mechanisms, then build/render controls on FX. Preserve current restoration-versus-calculation policy, financial inputs, accepted result identity and warnings. UI construction itself still consumes FX time; do not promise uninterrupted animation during control creation.
4. Show the primary stage only after its initial scene is attached, styled and usable. It need not wait for all lazy analyzer history loads. Pending original projection restoration remains explicitly pending; do not calculate a substitute. On the first usable main frame, close splash in the same guarded handoff and let main acquire focus once. Do not call repeated requestFocus/toFront or introduce artificial hold time. Small synchronous window systems may still show only a brief splash; never delay merely to guarantee logo exposure.
5. Serialize completion and shutdown through a tiny startup coordinator: preparing, showing, ready, failed, stopped. Late worker callbacks must verify lifecycle generation and not recreate windows after shutdown. Prevent implicit-exit gaps when hiding the splash before showing main. On failure, close splash, present one owned generic startup error with Exit, clean up services; distinguish fatal initialization from recoverable plan-open warnings. Keep existing delayed persistence notices/recovery after main readiness, so alerts do not appear behind the splash. Never overwrite damaged files as recovery.

The exact isolated startup DTO/controller entry point must be settled before implementation. It must not become a general projection lifecycle rewrite. If isolation requires broad controller/financial changes, stop rather than moving unsafe mutable objects between threads. A splash-only cosmetic patch that leaves all startup I/O/calculation blocking FX would not satisfy the stated requirements.

### Existing theme ownership conflict

MainWindow.createScene currently calls ApplicationThemeManager.start, which closes any prior global manager. Starting another manager for the splash and leaving this call unchanged would replace ownership mid-startup. Proposed fix: initialize one manager in MainApplication and inject/reuse it in MainWindow, while retaining isolated test-window construction support. Share the one settings service rather than creating a second producer/repository whose stale state can overwrite lastOpenedPlan. MainApplication.stop closes these resources once, including failure before MainWindow exists. Settings preview/rollback must continue operating on that manager. This is presentation/startup wiring only.

## Proposed implementation inventory and bounded stages

New production classes (names provisional):

- `application/branding/ApplicationBranding.java` — immutable validated values.
- `persistence/ApplicationBrandingLoader.java` — bounded packaged/external resolution and diagnostics.
- `ui/startup/StartupSplashView.java` — FX presentation only.
- `ui/startup/ApplicationStartupCoordinator.java` — one startup lifecycle/cleanup boundary if required by isolated loading.
- Optional small immutable prepared-startup DTO at that boundary; determine exact placement after the approved lifecycle design is validated.

New resources: `application-branding.properties`, `css/startup-splash.css`, approved `images/branding/aledrix-logo.png` and `aledrix-icon.png`; optional master vector is artwork only. Original concept sheet unchanged.

Modified production files: MainApplication (startup/handoff/error/cleanup), MainWindow (branding title injection and shared theme/settings ownership, narrowly separated startup attachment), ApplicationController only where required to prepare/adopt existing startup data without changing acceptance semantics. Existing ApplicationThemeManager may need only ownership/constructor integration, not a new theme framework. No plan repository, financial code, analyzer codec, PDF exporter, installer or POM changes are planned.

Tests: new ApplicationBrandingLoaderTest, StartupSplashViewTest, ApplicationStartupCoordinatorTest; extend ApplicationSettingsUiTest/PersistenceRecoveryWorkflowTest and title checks in AssumptionsMainWindowTest as appropriate. Test resource fixtures stay small and nonfinancial.

Stages: (1) approve/review master artwork and raster previews; (2) implement pure branding/configuration with tests; (3) implement minimal shared startup ownership and title integration; (4) splash lifecycle and failure tests; (5) light/dark/DPI previews, full regression and native acceptance. Do not implement until this report is approved.

## Verification and acceptance plan

Pure tests: exact default strings; partial external override; absent/unreadable/malformed/duplicate/oversized files; Unicode, blank and long text; resource traversal/URI rejection; missing/corrupt/oversized PNG fallback; immutable configuration; restart-only behavior. Ensure unrelated preferences remain intact and corrupt settings protection unchanged.

FX tests: title prefix plus existing filename/dirty marker; exact product/tagline; symbol-only resource; fixed aspect ratio; both themes; shared manager ownership across splash/main/dialogs/tooltips; no theme preference writes from display; completion/error/close races; recoverable last-plan warning; delayed restoration; cleanup before window exists; one main window; no focus stealing; no minimum duration. Inject controllable startup tasks rather than sleep-based assertions. Existing controller tests must prove projection acceptance/generation and historical restoration remain unchanged. Verify no duplicate startup projection or analyzer calls.

Native Windows checklist: first launch/no settings; auto-open saved complete snapshot; legacy/normal initial calculation path; missing/damaged plan; damaged branding/settings; Light/Dark restart; 100/125/150/200% scaling; monitor transition; taskbar/Alt-Tab icon; short/slow startup; close during startup; main focus and recovery dialog ownership; Settings Cancel rollback; existing dirty plan title; no theme/PDF palette changes. Do not claim Windows acceptance from JavaFX previews alone.

Audit verification: IntelliJ bundled Maven, project-local `.codex-m2/repository`, `-DargLine=-Xmx4g`, selected `ApplicationPreferencesTest,TransactionalSettingsTest,ApplicationSettingsUiTest,PersistenceRecoveryWorkflowTest,AssumptionsMainWindowTest`. **35 executed, 35 passed, 0 failed, 0 errors, 0 skipped; BUILD SUCCESS; 21.276 seconds**, completed 2026-10-28 09:43:02 -04:00. Log: ignored `target/branding-audit-focused.log`. Full suite was not run for this documentation-only audit; no new branding behavior is claimed tested. `git diff --check` passed (existing LF/CRLF advisory warnings only). The concept-sheet SHA-256 remains unchanged. Final index remains empty; only this report was added by the audit, with all pre-existing modifications and untracked files preserved.

## Risks and explicit approval decisions

1. Approve obtaining original Logo #5 master artwork, or a faithful traced reconstruction reviewed before integration. The reference alone cannot support a clean high-DPI extraction with original vector fidelity.
2. Approve external path, restart-only overrides, bounded field fallback, classpath-only logo selection and text-only missing-logo fallback. If externally replaceable local image files are required initially, approve that explicit contract instead.
3. Approve preserving filename/Untitled/dirty suffix after the new application-title prefix.
4. Approve narrowly separating real startup loading from FX construction and application-owned settings/theme lifecycle as described. Do not authorize a broad projection/controller rewrite. If that boundary cannot be isolated safely, return to a decision gate.
5. Approve undecorated nonmodal splash, no artificial minimum duration, and text-only activity rather than fabricated progress. Branding is not edited in Settings; About/PDF/installer reuse is deferred.

Principal risks are shaded low-resolution reference artwork, main-constructor synchronous calculation, theme-manager replacement, settings-service duplication, late startup callbacks, long override strings, platform focus/DPI behavior and external-path I/O. Financial behavior, stored snapshots, associations and PDF appearance remain explicit invariants. No production implementation has begun.
