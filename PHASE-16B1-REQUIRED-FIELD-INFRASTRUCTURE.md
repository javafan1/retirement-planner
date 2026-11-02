# Phase 16B1 — Required-Field Indicator Infrastructure

## Executive summary

Added a small JavaFX utility that attaches a literal, theme-colored asterisk to an existing Label without changing its text. Callers supply either a static-required state or an ObservableBooleanValue owned by their existing validation/configuration policy. The utility performs no validation, value mutation, enablement changes, dirty notification or financial work.

Added a reusable “* Required field” legend and two semantic CSS rules using the existing -app-error theme token. An isolated compact JavaFX test fixture exercises required, optional, conditional, tooltip-bearing, accessibility-described, disabled-required and legend cases. Actual Light and Dark screenshots were generated and inspected.

No form rollout occurred. No validation or financial behavior changed. The existing untracked Phase 16A report was preserved.

## Preliminary review and architecture

At the start, Git status contained only the untracked Phase 16A audit report. The approved report was reviewed in full. InputHelp remains responsible for tooltips and label links; WizardFieldRow remains unchanged and retains its current semantics. ApplicationThemeManager installs application.css and the active theme stylesheet on JavaFX scenes, including dialogs and popups.

The source supports the bounded API without a material conflict. A Label graphic is the available JavaFX composition point that leaves Label.getText() intact. Since replacing a pre-existing graphic could alter content and accessibility, the helper rejects a label with a graphic and leaves it untouched. Callers with existing graphics can compose a separate field row explicitly; this helper will not silently reconfigure them.

## Classes and files

- New: src/main/java/com/daviddunn/retirementplanner/ui/controls/RequiredFieldSupport.java
- Modified: src/main/resources/css/application.css
- New test: src/test/java/com/daviddunn/retirementplanner/ui/controls/RequiredFieldSupportTest.java
- New report: PHASE-16B1-REQUIRED-FIELD-INFRASTRUCTURE.md

The pre-existing PHASE-16A-APPLICATION-WIDE-REQUIRED-FIELD-AUDIT.md remains untracked and unchanged.

## Public helper API

Static required field:

    RequiredFieldSupport.Attachment marker =
        RequiredFieldSupport.attach(birthDateLabel, birthDatePicker);

Dynamic/conditional field:

    RequiredFieldSupport.Attachment marker =
        RequiredFieldSupport.attach(deathYearLabel, deathYearField, deathYearRequired);

Legend:

    Label legend = RequiredFieldSupport.createLegend();

The returned Attachment implements AutoCloseable. The owning form/dialog should close it when disposing the field surface. Repeated attachment to the same label/control is idempotent for the same static case or same observable. A conflicting second attachment fails clearly. Close removes registered listeners, restores the original label content-display/gap/association and the control's base accessible help, and removes the label's attachment marker. Keeping the attachment in the Label properties avoids a process-wide registry; ordinary label/attachment cycles remain collectible when the owner releases them. Observable sources that outlive a form require the explicit close contract.

## Required-state and visibility behavior

The utility reads only the caller-supplied Boolean state. It does not examine control type, value, disabled state, validation pseudo-class, or business data.

The marker updates on required-state changes. It remains visible for a disabled control if the caller says required. For presentation, the marker is hidden when the label or input itself is invisible or unmanaged, avoiding an orphan mark; this visibility suppression does not alter or infer the required Boolean. Hiding a field does not enable, disable, validate or mutate it.

The helper does not add a second five-state validation model. Optional/defaulted fields pass false; conditionally required fields bind the exact existing condition; always-required fields use the static overload or a true property. Future phases must source these values from the same validation mode/transition that governs Apply or Run. Do not reimplement rule logic in this helper.

## Accessibility and compatibility

The label's text remains unchanged. The asterisk is a non-focusable Text node attached as the Label graphic with ContentDisplay.RIGHT, so it follows the text. The label is associated with the supplied control if it was not already associated. An existing association to another control causes a clear exception.

While required, the input's accessibleHelp includes “Required.” Existing accessibleHelp is composed rather than replaced. When optional, the base description is restored. External accessibleHelp changes while attached are observed and recomposed, preventing duplicate suffixes. AccessibleText, control ID, tooltip, value, focus traversal and keyboard behavior are not changed.

Existing label graphics are rejected before mutation rather than discarded or silently moved. If using a label with a graphic in a future phase, use an explicit wrapper/composition and retain the label's association, base name and help.

The indicator is not focus traversable. It uses the literal asterisk as a non-color cue; the focused control also exposes the word “Required” through accessibleHelp. The legend exposes “Required field” as accessible text.

## Theme styling and legend

application.css adds:
- .required-field-indicator, using -fx-fill: -app-error for JavaFX Text.
- .required-field-legend, using -fx-text-fill: -app-error.

The Light and Dark theme files already define -app-error, so no new theme manager or duplicate theme colors were added. The legend is a non-focusable Label; creating it does not insert it into screens automatically.

## Test coverage

RequiredFieldSupportTest covers:
- Static required marker, exactly one asterisk, unchanged label text, preserved control ID, label association and disposal.
- Duplicate static attachment idempotence.
- Dynamic optional-to-required-to-optional and repeated transitions.
- Existing accessible-help composition, external help update and restoration on close.
- Existing descriptions that already end with a required statement do not receive a duplicate announcement.
- Disabled-but-required marker retention.
- Hidden/unmanaged field orphan suppression.
- Existing graphic rejection without modification.
- Legend text/accessibility and non-focusability.
- Tooltip and accessible-text preservation.
- Computed Light/Dark indicator colors after theme application and theme switching.

The compact fixture rendered all requested demonstration types. Screenshots:
- target/phase16b1/previews/required-indicators-light.png
- target/phase16b1/previews/required-indicators-dark.png

Actual JavaFX rendering was used, then both images were opened and visually inspected. The asterisk appears immediately after each required label in both themes; optional has none; disabled required remains marked; the legend is readable; Light uses the existing dark red token and Dark uses the existing light red token. Compact alignment and form spacing were acceptable. This is fixture-only verification, not an application-screen rollout or native Windows DPI acceptance.

## Verification results

Focused tests, using the repository's IntelliJ Maven executable and .codex-m2 repository:
- RequiredFieldSupportTest
- HouseholdViewTest
- AssumptionsViewTest
- WizardClarityTest
- DatePickerThemeRenderingTest

Result: 80 tests, 0 failures, 0 errors, 0 skipped.

After the final accessible-help de-duplication refinement, the component plus directly relevant existing form tests were rerun: 79 tests, 0 failures, 0 errors, 0 skipped. The component-only test also passed after that refinement (5 tests).

Complete Maven test suite, run before that final narrowly scoped accessibility suffix de-duplication refinement:
- 3,939 tests run
- 0 failures
- 0 errors
- 46 skipped
- BUILD SUCCESS

The skipped tests are existing opt-in benchmark/visual-preview tests. Existing fault-injection tests emitted expected diagnostic warning logs but passed. No new failures occurred.

git diff --check passed. Maven compiled 670 production Java files and 466 test Java files. The run displayed pre-existing JavaFX/JDK restricted-access and deprecation warnings.

## Existing behavior preserved and deferred issues

No existing UI form was attached to this helper. InputHelp and WizardFieldRow were not modified. No validation, default, label text, tooltip, accessible text, financial calculation, persistence, fingerprint, navigation or historical result changed. The Phase 16A name-policy ambiguities remain unresolved and must be handled according to its approval constraints before rollout.

No native Windows acceptance was performed. The fixture ran on Windows 11 in the Codex test environment, but this does not replace user review in the native IntelliJ/display-scaling setup.

## Native Windows acceptance checklist

- Open a small test form using the helper and confirm the star remains immediately beside the label at 100%, 125% and 150% Windows scaling.
- Confirm Light and Dark theme switching without rebuilding the form.
- Verify screen-reader output announces Required once and preserves existing control help.
- Confirm keyboard traversal skips the indicator and retains label/control association.
- Confirm labels with existing graphics fail clearly until explicitly composed.
- Review both fixture screenshots at native display scaling.

Future rollout phases should attach the helper only after identifying the current Apply/Run validation condition. Use a bindable condition for dynamic requirements, dispose the Attachment with its form, and do not change validation to match a visual marker.

## Git status

Current changes are the shared helper, application.css additions, its new test, the new implementation report, and the preserved untracked Phase 16A report. Nothing was staged, committed or pushed.

**Phase 16B1 is complete. Stop for review and explicit approval for native Windows acceptance before Phase 16B2.**

