# Phase 13H — Assumptions Screen UI Reorganization

## Scope and outcome

Reorganized the standard Assumptions screen into five responsive cards, with a scrolling input body and stable Apply/Cancel/status footer. Reused the existing input objects, labels, tooltips, actions and validation path. No financial values, defaults, formulas, parser rules, persistence, fingerprints, projection execution or analyzer behavior changed.

The separate guided new-plan presentation remains on its existing layout. This report concerns the requested Assumptions UI task; it does not replace the earlier application-versioning/About report with a different filename.

## Baseline and preserved work

Branch `main`, HEAD `42a519049cde5c8e9b39209e78c56530240e0600`; index empty. At entry the working tree contained the existing Phase 14B3A configuration changes/report, `.idea/encodings.xml`, and the preceding maximized-startup change in MainApplication/StartupSplashViewTest. These changes were preserved and are excluded from this task's file inventory. MainWindow was not modified by this task.

No staging, commit, push, reset, clean or discard operation was performed.

## Audit: every original input and its new location

The existing standard view constructed one long GridPane in a VBox. MainWindow hosts that view directly in its Assumptions tab; it did not provide an external assumptions-specific scroll wrapper. The view has 19 data controls, an ancillary Opening RMD action, Apply/Cancel and a status label.

| Section | Original field/control | Preservation |
|---|---|---|
| Planning Timeline | `projectionStartDatePicker` — Projection Start Date | Same date picker, editor, parsing and help |
| Planning Timeline | `projectionLengthField` — Assumed second death / end of household projection | Still a number of years, with the original years suffix; single-person label remains Configured projection length |
| Household & Survivor Planning | `deathScenarioComboBox` — Household survival scenario | Same enum values and listener; display-only friendly names |
| Household & Survivor Planning | `deathYearField` — Death Year | Original enablement and validation |
| Household & Survivor Planning | `survivorClaimingAgeComboBox` — Survivor Benefit Claiming Age | Original choices, dynamic survivor name, immediate-at-death behavior and tooltip |
| Household & Survivor Planning | `postDeathExpenseFactorField` — Post-Death Expense Factor | Same percentage input and death-scenario enablement |
| Economic & Investment Assumptions | `investmentReturnField` — Annual Investment Return | Same value, parser and help |
| Economic & Investment Assumptions | `inflationRateField` — General Inflation Rate | Same value, parser and help |
| Economic & Investment Assumptions | `healthcareInflationField` — Healthcare Inflation Rate | Same value, parser and help |
| Economic & Investment Assumptions | `socialSecurityColaField` — Social Security COLA | Same value, parser and help |
| Income Tax Assumptions | `filingStatusComboBox` — Filing Status | Same domain enum and existing friendly display strings |
| Income Tax Assumptions | `stateJurisdictionComboBox` — State of Residence | Same alphabetical choices, required marker, state-specific behavior and help |
| Income Tax Assumptions | `federalBracketGrowthField` — Federal Tax Bracket Growth | Same percentage input |
| Income Tax Assumptions | `standardDeductionGrowthField` — Standard Deduction Growth | Same percentage input |
| Income Tax Assumptions | `futureFederalMarginalRateAdjustmentField` — Future Federal Tax Rate Change | Same percentage-point semantics; grouped visually with effective year |
| Income Tax Assumptions | `futureFederalMarginalRateEffectiveYearField` — Effective Year | Same optional/required relationship and validation |
| Income Tax Assumptions | `stateIncomeTaxRateField` — Legacy State Income Tax Rate | Remains visible/editable under its original rules; no reinterpretation |
| Income Tax Assumptions | `localIncomeTaxRateField` — Local Income Tax Rate | Original rules retained |
| Estate & Heir Assumptions | `estimatedHeirTaxRateField` — Estimated Heir Tax Rate | Same rate and parser; moved out of the tax card |

All 19 original controls occur exactly once in the scene graph. No financial input was added or removed. The original Opening RMD button remains in Planning Timeline with its original conditional visibility and callback. The original `* Required` explanation moves to the footer; the State of Residence marker stays with its input.

## Layout implementation

Standard mode builds the existing controls and installs the same handlers/help, then reparents their original labels and editors into cards. The existing original-grid construction is still used by the guided workflow; there is no second set of data controls or separate editing model.

- At input-body widths of at least 1,000 logical pixels, an HBox contains two independent VBox stacks with 16-pixel spacing. Left: Planning Timeline, Economic & Investment Assumptions, Estate & Heir Assumptions. Right: Household & Survivor Planning, Income Tax Assumptions. Cards retain their preferred heights; the columns do not stretch to match each other.
- Below 1,000 pixels, the same card objects reparent into one stack in the approved order: Timeline, Household, Economic, Tax, Estate. Reparenting occurs only when crossing the breakpoint, not on every width change.
- Labels wrap and align in a consistent column within each card. Individual rows stack label above editor below 430 pixels of row width.
- The future federal tax adjustment and effective year remain consecutive inside a shared inset group with a semantic border.
- The body ScrollPane fits content width and suppresses horizontal scrolling. Apply, Cancel, required-field explanation and status remain outside the body, so they remain accessible while scrolling.
- Card surfaces, borders, text and footer use existing JavaFX semantic looked-up colors. The stylesheet is scoped to `.assumptions-view`; it does not modify global charts, dialogs, PDF print styling or the theme manager.
- Existing font conventions remain; section headings add restrained bold emphasis.

## Functional, visibility and accessibility preservation

Apply and Cancel are the same buttons with the same actions and dirty bindings. All parsing/domain validation completes through the existing atomic apply path; no parsing or model-writing code changed. Status text and validation messages are unchanged. Cancel reloads the applied plan as before. Existing tests continue to verify no model writes or projection callbacks before successful Apply, one notification for a valid change, invalid-edit isolation, high-precision values, save/reload and plan switching.

The original single-person rule hid the four couple-only inputs. Its implementation depended on contiguous rows in one GridPane. That layout dependency was adapted to the new rows/cards without changing the rule. The empty survivor card is hidden for single-person plans, and the remaining cards reflow. Loading a couple plan again restores the card and all four inputs. The guided workflow retains its original row-based visibility implementation.

For couples, inactive death-year, survivor-age and expense-factor controls remain visible and disabled under the original rules. Original labels retain tooltips, making help available when an editor is disabled. Survivor-age labeling now has an explicit label-for link and screen-reader help using the existing HelpText constant; its dynamic tooltip and available choices remain controlled by the unchanged SurvivorBenefitClaimingControls helper.

The household-scenario selector displays Both survive, Primary dies and Spouse dies through a view-only StringConverter. It still selects/stores the same DeathScenario values. Filing-status and state selectors already had friendly domain display names and were left unchanged.

## Tests and defects resolved during development

Initial smoke testing exposed an existing absence of a label-for link on the survivor-age control, which the reparenting lookup needed. Added that link. The new accessibility test then exposed the same control's lack of accessible help; supplied its existing help constant without changing tooltip semantics. Single-person checks prompted the explicit adaptation of the old contiguous-grid visibility code. These were localized presentation corrections; no financial or persistence behavior changed.

The prior tooltip/no-help-icon test assumed the first child was the original GridPane. It now verifies the same absence of label-side help icons across the entire view rather than casting the first child. Existing behavioral assertions and financial expectations were retained.

Focused behavior run: **121 tests passed, zero failures/errors/skips**, 55.174 seconds:

- AssumptionsViewTest — 36
- AssumptionsMainWindowTest — 12
- AssumptionsWizardStepTest — 3
- StateTaxWizardIntegrationTest — 66
- Initial AssumptionsLayoutTest cases — 4

Final layout/render run: **8 tests passed, zero failures/errors/skips**, 14.986 seconds. These include the same four structural cases plus four pulse-synchronized render/keyboard cases. The two focused runs overlap in the four structural cases; they are not claimed as 129 distinct tests.

Coverage includes:

- All 19 original controls exactly once, linked labels, tooltips and accessible help.
- Exact plan input documents unchanged by layout, resizing, cancellation and plan-shape switching.
- Both single-person and couple plans in Light/Dark at both requested sizes.
- Original inactive-field visibility/disablement and single-person hiding rules.
- Two-column versus single-column card layout, width fitting and footer visibility.
- Keyboard Tab from Projection Start Date to the horizon editor through the actual JavaFX event dispatch path.
- Scrolling until the heir field is visible above the stable footer.
- Existing Apply/Cancel, dirty-state, validation, state-tax and persistence behavior.

The first full suite ran all 3,798 tests and found one obsolete presentation test expectation, with zero financial assertion failures: PlanningHorizonLayoutTest still expected the old headings. Updated that test to require the approved headings and retain their ordering. Its semantic label traversal now explicitly visits ScrollPane content even before skins exist. All Apply and JSON assertions remain intact. A premature repeat was interrupted before completion when this traversal requirement was identified; it is not counted as a completed verification.

Final confirmation focused run: **63 passed, zero failures/errors/skips**, 18.134 seconds, covering AssumptionsLayoutTest, AssumptionsViewTest, AssumptionsMainWindowTest, AssumptionsWizardStepTest and PlanningHorizonLayoutTest. The separate 66-case state-tax wizard run above also passed unchanged.

Final full regression results: **3,798 total, 3,752 passed, 46 skipped, zero failures/errors; BUILD SUCCESS, Maven exit 0, 10:19 minutes.** Fresh Surefire XML agrees with Maven. All original financial assertions/goldens passed unchanged.

Skipped cases remain the existing 41 property-gated optional visual/performance/capacity/user-plan cases, four worksheet cases awaiting guidance and one explicitly disabled multi-minute mortality benchmark. No tests were newly disabled or excluded. Exact names/reasons and fresh totals are saved in `target/assumptions-ui/skipped-tests.txt` and `surefire-totals.json`. Timestamp filtering excludes the pre-existing October 19 diagnostic XML; no old artifact was deleted.

`git diff --check` passed (exit 0). The index remains empty and HEAD is unchanged. Existing working-tree changes remain preserved. Native Windows manual acceptance remains outstanding.

Maven uses the prescribed IntelliJ bundled executable, project-local `.codex-m2/repository` and `-DargLine=-Xmx4g`. Logs are in `target/assumptions-ui/`, including `focused-final.log`, `layout-final.log`, `focused-confirmed.log`, the first `full-suite.log` and final `full-suite-confirmed.log`.

## Actual rendered screenshots

Actual JavaFX AssumptionsView scenes were rendered with the existing ApplicationThemeManager. These are not mockups. Scene content sizes are 1180×820 and 900×650 logical pixels; screenshots exclude native title-bar decorations. Rendering waits for real JavaFX pulses after scrolling, so bottom images show the actual scrolled content.

Inspected all eight top/bottom views under `target/assumptions-ui/previews/`:

| Theme | Scene size | Screenshots |
|---|---|---|
| Light | 1180×820 | `LIGHT-1180x820-top.png`, `LIGHT-1180x820-bottom.png` |
| Dark | 1180×820 | `DARK-1180x820-top.png`, `DARK-1180x820-bottom.png` |
| Light | 900×650 | `LIGHT-900x650-top.png`, `LIGHT-900x650-bottom.png` |
| Dark | 900×650 | `DARK-900x650-top.png`, `DARK-900x650-bottom.png` |

At 1180×820 all five couple cards fit in the scene, with a stable footer. At 900×650 the cards stack and scroll; the bottom view shows the income-tax group and estate field above the footer. Labels, dropdown values and enabled text are readable in both themes. Disabled controls retain the existing theme treatment; their labels and explanatory tooltips remain available. No horizontal clipping or overlapping fields was observed at the requested sizes.

Generated previews and automated keyboard events do not constitute manual native Windows/DPI/screen-reader acceptance.

## Exact changed files for this task

Production:

1. `src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java` — standard layout, display converter, scoped accessibility and adapted layout-dependent visibility.
2. `src/main/resources/css/assumptions.css` — new view-scoped semantic card/footer styling.

Tests:

3. `src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsViewTest.java` — remove obsolete first-child GridPane assumption; retain tooltip/help-icon checks.
4. `src/test/java/com/daviddunn/retirementplanner/ui/views/PlanningHorizonLayoutTest.java` — approved heading names/order and semantic traversal of the new scroll body; unchanged financial/persistence assertions.
5. `src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsLayoutTest.java` — new structural, theme/size, keyboard and rendered scroll coverage.

Documentation:

6. `PHASE-13H-ASSUMPTIONS-UI-REORGANIZATION.md` — this report.

Ignored generated logs/screenshots are not intended commit files. All pre-existing modified/untracked files are excluded from this task's inventory and remain preserved. No domain, engine, persistence, fingerprint, PDF, dependency or financial golden file was modified.

## Native Windows acceptance checklist

1. Open Assumptions for a couple plan in Light and Dark at 1180×820 and 900×650; verify five cards, no horizontal overflow and a stable footer while scrolling.
2. Tab and Shift+Tab through every enabled field and action; verify DatePicker keyboard entry, dropdown selection and focus scrolling. Check Windows 100%, 125%, 150% and 200% scaling where practical.
3. Switch Both survive / Primary dies / Spouse dies. Confirm the same available survivor ages, disabled fields, named survivor labels and tooltips as before.
4. Switch between single-person and couple plans; confirm the same four couple-only fields hide/reappear and the timeline label retains its original semantics.
5. Confirm Filing Status and State of Residence display friendly names, with unchanged choices and required-state validation.
6. Edit the federal adjustment/effective year, including invalid combinations; verify original messages, focus behavior and no partial model writes. Check all other boundary validation cases as before.
7. Apply a valid edit once and confirm the established dirty/projection behavior. Cancel a pending edit and verify unchanged applied plan inputs. Check navigation away/back and ordinary Save/Load/Save As.
8. Check Opening RMD visibility/action on a plan that requires the workflow; confirm it invokes the existing dialog.
9. Verify tooltip and screen-reader descriptions on enabled and disabled fields, especially the survivor-age selector and horizon semantics.

Stop after implementation and regression verification for review; no subsequent feature work is included.

## Native-feedback refinement: independent card spacing

The initial two-column GridPane shared row heights between cards. A taller survivor or tax card therefore pushed the next left-column card down, leaving excessive empty space. Top alignment alone could not remove that shared-row dependency.

Replaced only the standard screen's card container with an HBox of two independent VBox columns. Both columns use 16 logical pixels between managed cards. Equal column widths are derived from the available body width; HBox fill-height is disabled and cards retain their natural preferred heights. The existing 1,000-pixel body-width breakpoint remains unchanged. Only crossing that breakpoint reparents the existing card objects; ordinary resizing does not repeatedly rebuild controls. Hidden single-person survivor content remains unmanaged, with no reserved gap.

The left column contains Timeline, Economic, Estate; the right contains Household, Tax. Narrow mode retains Timeline, Household, Economic, Tax, Estate. The scroll pane, stable footer, original control instances, help, visibility, validation and Apply/Cancel handlers are unchanged. The guided new-plan layout and semantic stylesheet were not edited by this refinement.

### Refinement verification

- Focused command: IntelliJ bundled Maven with the required project-local repository and `-DargLine=-Xmx4g`, selecting `AssumptionsLayoutTest,AssumptionsViewTest,AssumptionsMainWindowTest,AssumptionsWizardStepTest,PlanningHorizonLayoutTest`.
- Focused result: **71 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS (23.207 seconds). Log: `target/assumptions-spacing/focused.log`.
- Sixteen layout/render cases cover Light/Dark, couple/single, equal column widths, 16-pixel spacing, no overlap, both responsive directions, exact card order, all 19 controls appearing exactly once, hidden/disabled inputs, scrolling, stable footer and keyboard navigation. Increasing the right tax card's preferred height by 400 pixels leaves all left-card Y positions exactly unchanged.
- Complete Maven regression: **3,806 tests, 3,760 passed, 0 failures, 0 errors, 46 skipped**, BUILD SUCCESS, exit 0 (10:29 minutes). Log: `target/assumptions-spacing/full.log`. Independently summed the 435 Surefire XML reports written during this run; totals agree with Maven. The same existing optional/disabled tests remain skipped; no tests were disabled or weakened for this correction.
- Final `git diff --check`: exit 0. Only existing LF/CRLF conversion notices were emitted. Index remains empty; branch and HEAD are unchanged.

### Refinement visual evidence

Generated and inspected all twelve actual JavaFX snapshots in `target/assumptions-spacing/previews/`: `LIGHT` and `DARK`, each at `1180x820`, `900x650` and `1920x1080`, with `-top.png` and `-bottom.png` captures. The original previews remain under `target/assumptions-ui/previews/` for comparison.

At both wide sizes the left cards now follow each other with compact, consistent gaps independent of the taller right cards. At 900x650 the approved single-column order and scrolling remain intact, with the estate field reachable above the stable footer. No overlapping cards or horizontal clipping was observed. Top/bottom captures are identical when the full body fits, as expected. Theme colors and input styling remain unchanged.

1920x1080 is an additional representative wide scene, not a claimed reproduction of an unavailable native screenshot's exact dimensions. These are actual JavaFX scene renders; manual Windows focus, DPI and accessibility acceptance remains pending.

### Exact refinement file inventory and acceptance

1. `src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java` — independent responsive card stacks only.
2. `src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsLayoutTest.java` — geometry, resize, control preservation and wider rendered-preview coverage.
3. `PHASE-13H-ASSUMPTIONS-UI-REORGANIZATION.md` — this refinement and verification evidence.

All other pre-existing changes, including the original reorganization's CSS/test changes and unrelated Phase 14B3A work, remain preserved. No staging or commit operation was performed.

For native acceptance, repeat the earlier checklist and specifically verify independent left-card spacing at 1180x820 and a maximized wide window, in both themes; resize through the breakpoint in both directions; check single-person and couple plans; confirm scrolling/footer and Apply/Cancel remain unchanged.
