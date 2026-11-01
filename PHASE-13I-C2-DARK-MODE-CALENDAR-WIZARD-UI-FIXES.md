# Phase 13I-C2 - Dark Mode Calendar and Wizard UI Fixes

## Confirmed causes

The JavaFX 25 Modena stylesheet renders DatePicker month navigation arrows as shapes whose fill is derived from `-fx-base`. Aledrix's Dark theme uses a dark base (`#292e38`), so Modena's normal, hover, and pressed derivations remain too dark against the calendar header. The popup is a separate JavaFX window, but `ApplicationThemeManager` registers its Scene and applies the application theme stylesheets, including for DatePickers hosted in the wizard.

The wizard `DialogPane` carries both `app-theme` and `new-plan-wizard` classes on its root. The old Dark selector `.app-theme .new-plan-wizard ...` required one class to be an ancestor of the other and therefore did not match this root. In addition, the wizard stylesheet defines light semantic-color fallbacks on `.new-plan-wizard`; on a nested wizard root those local fallback tokens overrode tokens inherited from the themed scene. The selected-step button then combined a pale background with near-white foreground, which looked like a blank white rectangle. The buttons, navigation workflow, and selected pseudo-class were functioning.

## Changes

- Added semantic colors for calendar arrows in both themes, covering normal, hover, pressed/armed, and disabled states.
- Added shared Modena popup arrow-shape rules in `application.css`; JavaFX's standard calendar controls and navigation behavior remain intact.
- Reworked wizard navigation styling to use semantic background, foreground, hover, selected, focus, visited, and disabled colors.
- Added theme-scoped wizard tokens for both descendant roots and roots carrying both theme and wizard classes. Kept fallbacks for isolated unthemed wizard previews.
- Added a `visited` pseudo-class to already visited noncurrent steps for visual distinction only.
- Added JavaFX rendering tests that exercise actual calendar popups and all seven wizard selections in both themes at 1180x820 and 900x650. Tests check popup theme participation, computed arrow contrast, selected-step contrast, and draft-value preservation while navigating.

## Files changed for this phase

- `src/main/resources/css/application.css`
- `src/main/resources/css/new-plan-wizard.css`
- `src/main/resources/css/theme-light.css`
- `src/main/resources/css/theme-dark.css`
- `src/main/java/com/daviddunn/retirementplanner/ui/wizard/NewRetirementPlanWizard.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/DatePickerThemeRenderingTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/WizardNavigationThemeRenderingTest.java`
- `PHASE-13I-C2-DARK-MODE-CALENDAR-WIZARD-UI-FIXES.md`

The working tree already contained uncommitted Phase 13I and Phase 14 work. It was preserved and not included in this change.

## Verification

The focused UI regression run covered `ApplicationSettingsUiTest.darkHoverAndWizardCurrentStepKeepTextContrast`, `DatePickerThemeRenderingTest`, `WizardNavigationThemeRenderingTest`, and `RothAvailabilityUiTest`: 23 tests, 0 failures, 0 errors, 0 skipped. The broader focused run covering those rendering tests, wizard lifecycle/final acceptance, and Roth availability UI passed 41 tests with 0 failures, 0 errors, and 0 skipped.

The complete Maven suite ran with the project-local Maven repository and `-Xmx4g`: 3,919 tests, 0 failures, 0 errors, 46 skipped. Existing benchmark and integration workloads account for the long run. Existing opt-in and visual-preview tests account for the skipped tests; no assertions were weakened.

`git diff --check` passed. Git printed line-ending conversion notices for existing modified working-tree files; those are not whitespace errors.

## Rendered visual evidence

Actual JavaFX previews were generated and inspected under `target/phase13ic2/previews/`:

- `calendar-stage-light.png` and `calendar-stage-dark.png`: month and year navigation arrows are visible with contrasting fills.
- `calendar-wizard-dark.png`: the wizard-hosted popup retains Dark theme styling and visible arrows.
- `wizard-light-1180x820/selected-step-1.png` and `wizard-dark-1180x820/selected-step-1.png`: the selected step is clearly distinguished without a white rectangle.
- `wizard-dark-900x650/selected-step-6.png`: compact layout keeps the selected step readable and controls within the window.
- The rendering test generates previews for all seven selected steps in both themes and at both supported test dimensions.

The previews exercise real JavaFX controls and the wizard rather than stylesheet-only mockups. Windows display scaling at 125% and 150% is not available in this environment.

## Remaining limitations and native Windows checklist

Please verify on Windows:

1. Open calendar selectors in a regular view, a dialog, and the New Retirement Plan wizard in Light and Dark themes. Check month/year header navigation plus normal, hover, pressed, focused, and disabled contrast.
2. Inspect all seven wizard steps in both themes at 1180x820 and 900x650. Confirm current, visited, unvisited, hovered, keyboard-focused, and disabled states; verify keyboard navigation, draft retention, and wizard completion.
3. Repeat at 100%, 125%, and 150% Windows scaling and confirm the popup arrows and wizard text remain crisp and unclipped.
4. Recheck the Phase 13I-C1 employer availability editor's Save/Cancel footer and year input.

No financial logic, plan persistence, DatePicker values, wizard creation workflow, Roth conversion behavior, or projection behavior changed. Phase 13I-D has not been started.
