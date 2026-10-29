# Phase 13I — Social Security Initial Selection

## Initial architecture audit

Baseline main at 2cfb1555506f5257b2638630056cee4d284e7649. The staging index was empty; `.idea/encodings.xml` was already modified and is excluded from this phase. AGENTS.md and the current Phase 13G navigation/introduction implementation were inspected.

Verified causes: MainWindow introduces the first household-specific workspace before opening the SS window; AnalyzerNavigationView's constructor then selects its first Choice. Couple choices are Social Security Only, Deterministic Quick Comparison, Deterministic Exhaustive Search and Longevity-Weighted Search. Single-person choices are Deterministic Integrated, Social Security Only and Longevity-Weighted Integrated. Only explicit Run actions execute analyses.

Bounded design: nullable dialog-local selected Choice represents no selection (no synthetic Mode or persisted identity); neutral instruction content and an unselected compact/sidebar selector replace the implicit first choice. Hide contextual input header until a workspace is selected. Keep programmatic select() as a direct, introduction-free route. Restore committed selector state before every gated user request so Cancel needs no synthetic selection. Guard callbacks and result reveal against no selection. Reuse AnalyzerIntroductionGate's launch token/owner lifecycle for opening the neutral top-level window without an introduction; keep all existing per-mode settings keys and user-selection gates. No financial, persistence, freshness or execution-policy change is required.

Existing focused baseline: 17 total, 16 passed, one existing opt-in preview skipped, zero failures/errors (`target/phase13i/baseline.log`).

## Implemented initial-selection architecture

`AnalyzerNavigationView` no longer calls select(first choice) in its constructor. Its dialog-local selected Choice starts null; there is no fake analysis kind, new persisted setting or result identity. The compact ComboBox has a **Select an analysis method** prompt; sidebar toggles all begin unselected. A neutral workspace displays:

> Select an analysis method to explore your Social Security claiming strategies.

> Choose a method from the analysis choices. Opening a workspace does not start a calculation.

The status says **No analysis selected**. The common contextual-input header is hidden/unmanaged until a workspace is selected; the neutral pane is usable content rather than an empty result area. Wide layout retains the existing sidebar, and reduced layout retains the existing compact chooser. No CSS, palette, analyzer names, choice ordering or supported household shapes changed.

`isSelected(mode)` is a null-safe presentation check used by parameter visibility and deferred completed-result reveal. `onSelected` registers without emitting a fictional initial selection. `focusSelector` can focus the first choice or compact prompt safely while unselected. Keyboard routing uses the focused choice when no prior selection exists, retaining arrows/Home/End and the previous selected-workspace behavior afterward.

## Introduction launch sequence

The top-level MainWindow SS menu opens the existing owner-modal SS window immediately, without selecting an introduction identity. `AnalyzerIntroductionGate.openWithoutIntroduction` reuses the same request token, ownership listener, duplicate-launch guard and release logic; it is not a second launch framework. MainWindow retains plan object/UUID/revision and operation/swap guards. The existing showAndWait contract for the analyzer window remains unchanged. Monte Carlo launch paths continue using their original introduction gates.

Inside the SS window, the existing configureIntroductions user-selection hook remains authoritative. Sidebar clicks, compact choices, keyboard selection and Quick's explicit Go to Social Security action all request the selected mode's existing Phase 13G introduction. Continue enters that exact workspace; suppressed intros enter directly. No Run action is invoked. A same-workspace reselect retains keyboard focus without reopening an introduction.

Before invoking the gate, requestSelect restores the committed ComboBox value and every toggle. Thus an in-flight, canceled, escaped or window-closed introduction never leaves a tentative highlight/value. If selected was null, it stays null and the neutral instruction remains. If a previous workspace existed, its node, selection and scroll state remain until Continue commits a different selection. Repeated requests use the existing one-shot gate and cannot duplicate introductions or analyzer windows.

## Couple and single-person behavior

Couple: four original choices, with their exact current names listed in the audit. Single person: the three original choices, no Quick choice or spouse inputs introduced. Both begin neutral on each new ordinary dialog launch. MainWindow creates a new dialog on reopening. Existing explicitly requested/programmatic `select(mode)` still enters a workspace directly without an introduction; historical result restoration does not call the user-selection gate.

No introduction preference identifiers, defaults, suppression persistence or Settings reset behavior changed. Only Continue with suppression checked writes that preference, as before; canceling still does not persist. Startup splash/About/settings/theme behavior is untouched.

## Parameter restoration and historical compatibility

Saved-result loading remains calculation-free and can populate detached/hidden result workspaces. ResultNavigationGuard and its tab/epoch rules are unchanged. New null-safe visibility checks prevent deferred reveal of an unselected workspace while preserving original tab initialization and manual-navigation precedence.

Removing the implicit first selection also removed a constructor-time side effect that had restored SS's accepted parameter configuration. A targeted restart regression established the consequence: primary mortality factor remained default `1` instead of accepted `1.1500` while neutral. The correction explicitly restores the existing hidden SS parameter binding after the bindings are installed, without selecting or revealing SS. This preserves original prerequisite settings before the existing SS result load, so opening Quick does not acquire a new compatibility convention. Its Run action still requires the original compatible SS result; no candidate/result is manufactured. Individual parameter binding initialization remains unchanged.

The restart regression first used the same controller and passed because session propagation retained the factor. It was strengthened to open the saved plan in a new controller, which exposed the missing initialization (`hidden-parameters-restart-before.log`). The correction retains all original accepted settings, precision and binding semantics. No financial fingerprints, execution revisions, result values, persistence schemas or algorithms changed.

## Automated verification

All Maven tests use IntelliJ bundled Maven, the required project-local `.codex-m2/repository` and `-DargLine=-Xmx4g`.

| Run | Total | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Existing navigation/introduction baseline | 17 | 16 | 0 | 0 | 1 |
| Final focused navigation, introductions, settings and parameter regression | 120 | 119 | 0 | 0 | 1 |
| Existing historical restoration/navigation/PDF regression | 64 | 64 | 0 | 0 | 0 |
| Grid/navigation tests after explicit grid-fixture selection | 55 | 55 | 0 | 0 | 0 |
| First complete suite (old grid fixture assumed automatic selection) | 3694 | 3645 | 0 | 5 | 44 |
| Final complete Maven suite | 3694 | 3650 | 0 | 0 | 44 |

Final focused log: `target/phase13i/focused-complete.log`, 48.784 seconds. Classes: AnalyzerNeutralSelectionTest, AnalyzerIntroductionNavigationTest, AnalyzerIntroductionMenuTest, AnalyzerDirectNavigationTest, AnalyzerContextualInputsTest, SocialSecurityStrategyAnalyzerDialogStateTest, SinglePersonIntegratedViewTest, SocialSecurityParameterPersistenceTest, AnalyzerIntroductionUiTest and AnalyzerIntroductionPreferencesTest. An earlier selector named AnalyzerResultNavigationTest did not match a class; the actual ResultNavigationTest was explicitly run in the separate restoration suite, alongside WeightedRestorationTest, CoupleSocialSecurityPersistenceTest, ExhaustivePersistenceTest and SocialSecurityAndQuickPdfTest (`restoration-focused.log`, 34.831 seconds).

Coverage includes all four couple/three individual choices; no initial selection/introduction; exact intended Continue destination; Cancel/Escape/window-close back to neutral or prior selection; sidebar/compact/keyboard synchronization; suppression bypass per original identifiers; rapid duplicate requests; real MainWindow menu opening/reopening with no intro; passive programmatic selection; unchanged Quick readiness; restart restoration of hidden accepted settings; preserved input document/snapshot UUID/revision/dirty state; throwing projection engine and empty manual analysis queue/job generation. Existing preference failure, async late callback and restart/suppression tests remain in the focused run.

Existing test updates are required-behavior changes, not weakened financial assertions: AnalyzerDirectNavigationTest now asserts null at initial construction; SinglePersonIntegratedViewTest asserts neutral before explicitly selecting the deterministic workspace it inspects/renders; menu tests verify direct neutral launch instead of the now-forbidden pre-launch introduction. Existing table geometry, calculations, fingerprints, golden values, settings and other assertions are retained. The first broad run exposed two old single-person fixture assumptions, corrected by explicit workspace selection. No tests were disabled and no new exclusions were added; the focused skip is the existing opt-in navigation preview case.

The first complete suite exposed the same old initial-selection assumption in SharedClaimingAgeGridTest's five logical-size cases: it rendered/measured SS cells without attaching the SS workspace. The fixture now explicitly selects SS before those measurements. All financial, axis, currency-width, marker, tooltip and geometry assertions are unchanged. The affected SharedClaimingAgeGridTest, AnalyzerNeutralSelectionTest and ResultNavigationTest pass together: 55/55, zero skips, in 23.884 seconds (`grid-navigation-final.log`). The first full log is retained as `full-suite.log`; final verification is `full-suite-final.log`.

## Visual verification

Actual JavaFX scene snapshots were generated and inspected, not mockups:

- `target/phase13i/previews/neutral-true-LIGHT-1180.png` — couple, Light, 1180×820 scene.
- `target/phase13i/previews/neutral-true-DARK-900.png` — couple, Dark, 900×650 scene.
- `target/phase13i/previews/neutral-false-LIGHT-900.png` — individual, Light, 900×650 scene.
- `target/phase13i/previews/neutral-false-DARK-1180.png` — individual, Dark, 1180×820 scene.

The preview test explicitly creates/asserts the requested scene dimensions instead of relying on an uncompleted native resize pulse. Final normal/reduced previews have readable instructions, no selected highlight, synchronized prompt, intact existing choice names and no introduction on opening. Contextual editable controls are not exposed before selecting a method. Wide views retain the original sidebar; narrower usable widths use the original compact selector. No clipping was observed in the instruction/selector/Close areas. Single-person dialogs retain their existing native window-close convention. Native Windows DPI, focus, screen-reader and multi-monitor acceptance remain pending.

## Exact changed-file inventory

Production (four modified):

1. `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java`
2. `src/main/java/com/daviddunn/retirementplanner/ui/introduction/AnalyzerIntroductionGate.java`
3. `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerNavigationView.java`
4. `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java`

Existing tests modified:

5. `src/test/java/com/daviddunn/retirementplanner/ui/introduction/AnalyzerIntroductionMenuTest.java`
6. `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerDirectNavigationTest.java`
7. `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedViewTest.java`
8. `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityParameterPersistenceTest.java`
9. `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SharedClaimingAgeGridTest.java`

Added:

10. `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerNeutralSelectionTest.java`
11. `PHASE-13I-SOCIAL-SECURITY-INITIAL-SELECTION.md`

Exclusions: pre-existing `.idea/encodings.xml` modification is preserved unchanged; ignored target logs/screenshots are not source changes. No dependencies, build configuration, CSS, logo assets, financial source, persisted settings model, plan schema, PDF exporter or golden resources changed.

## Native Windows acceptance checklist

1. Open the SS menu on a couple plan: existing window opens directly, neutral instructions visible, all four choices available and none selected, no introduction/calculation on opening. Close and reopen: neutral again.
2. Repeat on a single-person plan: only three original choices, all initially unselected; no Quick/spouse choice introduced.
3. Select every choice: verify its existing exact introduction title/content. Continue enters the correct workspace without starting a run. Quick remains blocked unless its SS prerequisite is compatible.
4. From neutral, cancel with button/Escape/X: neutral prompt, no leftover highlighted toggle or compact value. Switch from an existing workspace and cancel: previous selection/input/result/scroll state remains.
5. Suppress one intro and reopen: no initial analyzer selection; explicitly choosing that method bypasses only its intro. Other methods and Monte Carlo retain their original suppression behavior. Verify Settings reset and Cancel/Save semantics.
6. Test sidebar, compact dropdown, Tab/Shift-Tab, arrows/Home/End/Enter/Space across normal/reduced widths. Check focus after Continue/Cancel and resizing with no selection.
7. Rapidly click menu/choices/Continue; verify one SS window/introduction, no duplicate launch or run.
8. With saved SS parameters/results, restart, open neutral then Quick/SS: accepted settings and existing Current/Outdated/readiness classifications remain correct. Historical result viewing/restoration must not show an intro or overwrite last-success state.
9. Verify Light/Dark at 1180×820, 900×650 and normal Windows DPI scales; readable prompt/instructions/choice names, no stale highlight or clipped controls.
10. Check plan dirty flag, financial inputs, snapshot UUIDs, analyzer parameters, cancellation/progress and PDF output before/after navigation. Only an explicit existing Run action may start financial analysis.

## Limitations and review status

This is a dialog-local UI state, not a saved preference for the last-opened workspace. Existing results may restore in hidden views as before; no automatic historical promotion, result migration or fresh calculation is introduced. Each new ordinary MainWindow launch is neutral, while explicit programmatic navigation remains direct. No subsequent phase has begun. Final full-suite results and Git verification are recorded below.

## Final verification and delivery status

The final complete Maven run passed on 2026-10-29 at 09:10:35 America/New_York in 10:18 minutes: **3,694 total, 3,650 passed, 44 existing opt-in/environment-dependent skips, zero failures/errors**. Log: `target/phase13i/full-suite-final.log`. No tests were disabled, assertions weakened, exclusions added or financial golden values regenerated. The first full run and its five fixture errors remain documented above; the final run includes the corrected explicit grid-workspace setup.

Final `git diff --check` passed. Branch main and HEAD 2cfb1555506f5257b2638630056cee4d284e7649 are unchanged; staging index remains empty. The 11 Phase 13I files above comprise the intended change inventory. The unrelated pre-existing `.idea/encodings.xml` addition for UTF-8 src/main/version is preserved and excluded. No staging, commit, push, reset, cleanup or discard operation was performed.

Ready for review and native Windows acceptance. All requested automated verification and Light/Dark preview inspection are complete; no subsequent phase has begun.