# Phase 13H — Application Versioning and About

## Architecture audit (before implementation)

Branch main, HEAD 3e3b70ef31fc5c0da0e038e6cddef91031786b9d. Working tree and staging index were clean. AGENTS.md was read. Java 25.0.4.1 and IntelliJ bundled Maven are available.

Verified findings:

- pom.xml has project version 1.0-SNAPSHOT, standard resource handling, Surefire 3.5.3 and JavaFX Maven plugin 0.0.8. No existing build identity generator.
- MainApplication loads branding/settings in init(), starts the shared theme manager, shows StartupSplashView and uses ApplicationStartupCoordinator for genuine startup preparation. No lifecycle change is needed.
- StartupSplashView is presentation-only; BrandingImages already performs bounded classpath logo decoding with text-only fallback. The approved transparent logo and icon are reusable.
- MainWindow.createMenuBar already contains Help and an unused About item. Replace that item rather than adding another menu.
- ApplicationBranding centralizes title/product/tagline/logo with restart-scoped deployment overrides; ApplicationThemeManager owns scenes/windows and semantic colors. Settings remain separate and unchanged.

Implementation design: Maven project version 0.91.0, filtered application-version.properties, one build-only git-commit-id plugin to supply commit identity during initialize, immutable bounded runtime version loader, shared presentation of version/attribution in splash and About. No runtime Git, extra executor, financial dependency or startup-coordinator change. Git-unavailable builds use explicit unknown placeholders. Timestamp is informational. Default designation development; release selected explicitly with a Maven property. Company attribution is centralized branding metadata. No material architectural conflict identified.

The single build-only plugin is justified because Maven has no built-in Git revision property; using it avoids a custom process runner, generated tracked source or platform-specific build script. Its supported configuration was checked against the project's official documentation: https://github.com/git-commit-id/git-commit-id-maven-plugin/blob/master/docs/configuration-and-properties.md .

The following sections record implementation evidence and final verification.

## Implemented versioning contract

The authoritative product version is Maven's project version, now `0.91.0`. No production Java class repeats that value. Maven filters only `src/main/version/application-version.properties`; other resources, including PNGs, CSS, rules and PDF assets, remain unfiltered. Build output is `target/classes/application-version.properties`. Compilation never rewrites a tracked source/resource file.

`ApplicationVersion` is an immutable record with product version, full commit hash, abbreviated commit, development/release designation and optional UTC build timestamp. Its cached `current()` instance reads at most 8 KiB from the packaged classpath resource. Parsing validates version/commit syntax and matching abbreviation; malformed, unreadable, oversized or unresolved metadata cannot invent an identity. Invalid independent fields fall back independently; malformed property documents fall back as a whole. No runtime Git invocation, external version file, preferences access, clock read or worker thread.

Stable revision identity is the full Git commit; the UI build identifier is `git-` plus its first 12 characters. Abbreviations are convenient display identifiers, not a mathematical uniqueness guarantee; About includes the full hash. Rebuilding the same commit preserves version and commit identity. Timestamp is build-time UTC information and changes across builds, so this is stable version/revision identification, not a promise of byte-identical JARs. No commit sequence is used. Uncommitted working-tree edits do not receive a new commit identity; Development identifies the default build category, not a clean-tree certificate.

`aledrix.build.designation` defaults to `development`. Explicit release builds use `-Daledrix.build.designation=release`. There is no automatic release/commit/tag operation and no modification to the POM at build time. Invalid runtime designation falls back to Development.

One pinned build-only plugin was added: `io.github.git-commit-id:git-commit-id-maven-plugin:9.0.2`, bound to initialize. No application dependency was added. Only commit/hash-abbreviation properties are exposed; repository paths, author identities and remote URLs are not packaged. A file-activated `git-build-identity` profile requires the project's own `.git` marker (directory or worktree file), preventing accidental attribution to an enclosing repository.

### Fallback and development workflow

- Git checkout: `mvn compile`, `mvn test`, `mvn package` and `mvn compile javafx:run` generate metadata automatically in the normal lifecycle. The checked Maven executable is IntelliJ's bundled Maven with the required project-local repository.
- Source archive without `.git`: Git profile stays inactive; product version and timestamp still come from Maven, commit/build identity are explicitly `unknown`. Previously generated output is overwritten by ordinary resource filtering rather than retaining a stale Git properties file. The archive check was deliberately run inside this checkout to prove that its enclosing Git repository is not borrowed.
- Packaged distribution: include the filtered resource from Maven output; no Git executable or repository is needed at runtime. Missing timestamp is omitted from About. Missing/invalid identity is shown as unknown, never guessed from the local machine.
- IntelliJ: reimport the changed POM and delegate build/run to Maven or add Maven `compile` as a Before Launch step. Direct `javafx:run` without lifecycle preparation may use old output, just as any direct IDE launch can. An unfiltered resource/direct compiler launch without generated metadata safely displays unknown fields; it does not inspect a workspace POM or execute Git. IntelliJ GUI interaction remains a native acceptance item; the bundled Maven path was actually verified.
- Build with unavailable/unreadable Git metadata remains nonfatal and explicitly unknown. There is no automatic overwrite of preferences or plan data, and no persisted release metadata claim fabricated for source archives.

### Build checks and bounded correction

Initial generated metadata correctly contained version 0.91.0, commit `3e3b70ef31fc5c0da0e038e6cddef91031786b9d`, abbreviation `3e3b70ef31fc`, Development and a UTC timestamp. A second normal lifecycle run preserved version/full/abbreviated commit while timestamp changed. An explicit release build passed all five metadata tests and changed only the designation/timestamp.

The first nested source-archive experiment exposed the plugin's enclosing-repository lookup. The localized `.git` profile guard corrected that provenance issue. Repeating the experiment produced `product.version=0.91.0`, `build.commit=unknown`, `build.commit.abbrev=unknown` and a valid timestamp, even with an enclosing checkout and stale generated output present. Evidence: `target/phase13h/metadata-first.properties`, `metadata-repeated.properties`, `metadata-release.properties`, `source-archive-build-final.log` and `source-archive/target/classes/application-version.properties`.

## Splash and About implementation

`StartupSplashView` retains the accepted logo, product/tagline, initialization wording and natural-height layout. It adds the shared version summary and understated bottom attribution. `ApplicationBranding.COMPANY_ATTRIBUTION` is the only production Java definition of the exact text `A product of ZaZoot LLC, USA`. Branding deployment overrides and application Settings are unchanged.

`AboutApplicationDialog` reuses bounded `BrandingImages` loading and the same branding/version sources. It is owner-modal, resizable, centered over the owner when shown and uses nonblocking `show()`. It displays product/logo/tagline, version/build/designation, full Git hash, optional informational timestamp and exact attribution. Missing artwork uses text-only presentation. Content scrolls at reduced sizes; Close, Escape and window-close use ordinary JavaFX dialog behavior. `MainWindow` wires the previously unused Help item as **About Aledrix Retirement Planning** (uses the configured product name if overridden), with stable action ID `about-application-menu`.

Scoped semantic CSS uses existing `-app-text` and `-app-muted`; the shared ApplicationThemeManager covers both scenes and live theme changes, detaching on window closure. No second theme/settings authority. MainApplication, ApplicationStartupCoordinator, pulse handoff, shutdown/failure paths, primary title/filename/dirty-marker formatting, startup timing and last-plan restoration remain unchanged. No artificial delay, executor, progress percentage or new analysis callback.

## Tests and visual verification

Baseline focused suite: **26 total, 26 passed, zero failures/errors/skips** (`target/phase13h/baseline.log`). Final focused suite: **52 total, 52 passed, zero failures/errors/skips**, 19.082 seconds (`focused-final.log`). Classes: ApplicationVersionTest, AboutApplicationDialogTest, StartupSplashViewTest, ApplicationStartupCoordinatorTest, ApplicationBrandingLoaderTest, ApplicationPreferencesTest, ApplicationSettingsUiTest. Explicit release build: **5 total, 5 passed, zero failures/errors/skips** (`release-build.log`).

New tests cover Maven-filtered version, formatting/commit validation, stable identity across timestamp/designation changes, unknown Git, unresolved IDE placeholders, missing/malformed/oversized/unreadable metadata, shared splash/About labels, optional timestamp, production and missing logos, actual Help action, owner modality, Close/Escape/window-close, both themes and live switching, reduced owner sizes, unchanged financial input document/snapshot/revision/dirty state and no preference file writes. A throwing projection engine proves About does not calculate. Existing startup, title/dirty suffix, Settings preview rollback and preference tests remain intact.

The first focused run had one failed new test because its synthetic commit fixture had 41 rather than 40 hexadecimal characters. Correcting the fixture retained the strict production validation and expected identity assertion. No existing tests or golden fixtures were edited or weakened.

Actual rendered JavaFX previews (not mockups) are generated by AboutApplicationDialogTest in `target/phase13h/previews`: splash/about × LIGHT/DARK × 1180/900 owner widths, with owner heights 820/650. Light/Dark splash and About images were inspected. Logo, full hash, UTC timestamp, exact attribution, typography and Close action are readable without clipped text; splash natural height expands to about 403 pixels. Reduced-size dialog remains compact. Existing splash previews also exercise 2x snapshots. These are not native Windows DPI, taskbar, screen-reader or focus certification.

Full Maven regression: **3,687 total, 3,643 passed, 44 existing opt-in/environment-dependent skips, zero failures/errors**, BUILD SUCCESS in 10:39 minutes, completed 2026-10-29 07:51:23 America/New_York (`target/phase13h/full-suite.log`). All runs use the project-local `.codex-m2/repository`, Java 25 and `-DargLine=-Xmx4g` where tests run. No test exclusions, financial expectation changes or dependency/runtime-JVM option changes.

## Exact changed-file inventory

Modified:

1. `pom.xml` — product version, filtered metadata resource, Git-only build profile/plugin and designation/timestamp defaults.
2. `src/main/java/com/daviddunn/retirementplanner/application/branding/ApplicationBranding.java` — centralized company attribution.
3. `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java` — existing Help/About action.
4. `src/main/java/com/daviddunn/retirementplanner/ui/startup/StartupSplashView.java` — shared version summary and attribution.
5. `src/main/resources/css/startup-splash.css` — semantic metadata/attribution styles.

Added:

6. `src/main/java/com/daviddunn/retirementplanner/application/branding/ApplicationVersion.java`
7. `src/main/java/com/daviddunn/retirementplanner/ui/startup/AboutApplicationDialog.java`
8. `src/main/resources/css/application-about.css`
9. `src/main/version/application-version.properties`
10. `src/test/java/com/daviddunn/retirementplanner/application/branding/ApplicationVersionTest.java`
11. `src/test/java/com/daviddunn/retirementplanner/ui/startup/AboutApplicationDialogTest.java`
12. `PHASE-13H-APPLICATION-VERSIONING-ABOUT.md`

Ignored diagnostics, source-archive experiment and screenshots under target are excluded from a future commit. No original logo assets, plan schemas, fingerprints, analyzer results, financial engines, Settings model/repository, PDFs or golden fixtures changed. AGENTS.md's overview still describes the former Maven version; this report and the POM record the authorized new version without editing project instructions.

## Native Windows acceptance checklist

1. Reimport POM in IntelliJ; use Maven compile before launching MainApplication. Check version 0.91.0 and the current committed hash in both splash and About.
2. Check Help contains one About command with owner-modal behavior, keyboard activation, Escape/window-close and ordinary focus return. Opening/closing must not dirty a plan or alter Current/Baseline/history/analyzer state.
3. Check Light and Dark splash/About at 1180×820 and 900×650, plus 100/125/150/200% DPI and multiple monitors. Verify readable attribution, full hash wrapping, correct logo and no clipped controls.
4. Confirm splash-to-main handoff, last-plan reopening, saved-result restoration and startup failure recovery are unchanged, with no minimum splash delay or duplicate window.
5. Verify Settings preview/Cancel rollback/Save and theme persistence; About already open must follow a theme change. Existing title filename, Untitled and dirty suffix remain unchanged.
6. Build twice at the same commit: version/hash stay the same, timestamp may differ. Build explicitly with `-Daledrix.build.designation=release`; check Release. Restore normal development build afterward.
7. Test a packaged distribution without Git installed: metadata remains from its build; source-archive build without Git must say unknown identity. Missing logo still allows text-only About/splash.
8. Spot-check projection/analyzer/history/PDF behavior; no financial values or persisted settings should change from viewing metadata.

## Known limitations

No installer, About links/licensing/update check, PDF header redesign, auto-release tagging, signing, clean-tree release enforcement, build counter or new packaging system is introduced. Stable identity identifies committed source, not uncommitted edits, transitive build inputs or byte-identical output. Native IntelliJ UI, DPI, focus and packaged-distribution launch acceptance remain user checks; the actual Maven lifecycle/resource and JavaFX rendering behavior was verified here. Raw IDE launches without Maven-generated resources deliberately degrade to explicit unknown metadata. The pinned plugin has build-time transitive dependencies but adds no runtime libraries.

A broader source audit found a pre-existing fixed `1.0-SNAPSHOT` application-version marker in `AcceptedAnalysisCompletion.presentationSnapshot`. It is not consumed by splash/About. That analyzer payload writer and all existing historical metadata are intentionally unchanged in this UI/build phase. Any future integration of centralized version metadata into newly written analyzer artifacts should be reviewed separately for provenance/compatibility; this phase does not claim to migrate or correct those historical labels.

## Final packaging and Git verification

Ordinary Maven `package -DskipTests` succeeded after the complete suite (14.518 seconds). This packaging-only invocation did not substitute for or exclude tests from the preceding full run. The existing JAR packaging produced `target/retirement-planner-0.91.0.jar`; no new packaging/distribution system was introduced.

A separate Java source-launch probe loaded ApplicationVersion from that JAR (verified through its code-source location), asserted product version, exact full commit and available timestamp, and printed `Version 0.91.0 | Build git-3e3b70ef31fc | Development`. The packaged loader used only classpath data; the probe contains no Git invocation. Evidence: `target/phase13h/package.log`, `InspectPackagedVersion.java` and `packaged-metadata.log`. This verifies packaged metadata loading, not a full installed JavaFX distribution launch.

Final `git diff --check` passed. Branch remains main at 3e3b70ef31fc5c0da0e038e6cddef91031786b9d; staging index remains empty. All changes are the 12 files inventoried above. No source, tests, assets or unrelated files were discarded. No files staged, committed or pushed. Phase 13H is ready for review and native Windows acceptance; no subsequent phase started.