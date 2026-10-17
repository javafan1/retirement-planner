# Phase 7C.4 — Spending, Assumptions and Social Security Clarity

## 1. Executive summary

Implemented presentation improvements within the existing seven-page wizard. Spending now explains its actual annual/purchase conventions and exposes selected-item details. Assumptions has six Basic inputs and two existing Advanced inputs, initially collapsed. Social Security and Review display derived, owner-specific benefit-valuation-year notices without changing benefits. Review includes spending interpretation and all eight existing guided assumptions.

No financial algorithms, defaults, domain objects, persistence, schemas, tax rules, analyzer algorithms, PDF exporters or dependencies were changed. No Phase 7C.5 work is included.

## 2. Initial semantic audit findings

Read `AGENTS.md` and the Phase 7C.1, 7C.2 and 7C.3 reports; inspected the actual wizard, controls, dialogs, financial models and relevant tests. Initial branch was `main`, HEAD `de2b3a4bb03e097377e0c43c996f92dfa3b0e3b0`, with a clean working tree.

The actual registration in `ui/wizard/NewRetirementPlanWizard.java` agrees with the completed reports: HOUSEHOLD, ACCOUNTS, SOCIAL_SECURITY, PENSIONS, EXPENSES, ASSUMPTIONS, REVIEW. Navigation, persistent page instances, isolated `NewPlanDraft`, Review reconciliation and Create/Cancel lifecycle are retained unchanged.

Actual spending conventions:

- `domain/financial/Expense.java` stores `annualAmount`, type RECURRING or ONE_TIME, General/Healthcare growth category, and optional start/end dates. There is no monthly expense input or new frequency selector.
- `domain/projection/ProjectionEngine.calculateProjectedExpenses` uses calendar-year overlap, with the opening interval beginning on the actual projection start. Only opening-year recurring spending is prorated using the existing month convention; later active years use the annual amount. Existing death adjustments apply to recurring spending.
- `calculateProjectedExpense` grows the entered base by the offset from the projection's opening year, including when spending starts later. Therefore help says **projection opening-year spending base**, not purchase-year nominal dollars, universally today's dollars or an automatically converted monthly amount.
- `getExpenseGrowthRate` selects the record's actual Healthcare/General category. `ui/dialogs/ExpenseDialog` configures a one-time purchase with equal start/end dates and General inflation. The underlying model can contain older/custom records with other categories/date ranges: presentation reads the actual category and does not rewrite those records. The editor's narrower behavior is identified as editor behavior, not a new universal financial rule.
- Medicare premiums are modeled separately; guidance recommends checking healthcare entries for double counting, without changing them.

Actual Social Security conventions:

- `domain/income/SocialSecurityIncome` stores the FRA monthly benefit and `benefitValuationYear`. The latter is the estimate's dollar basis, not the claiming year, derived start date or projection opening year.
- Existing calculations use the stored valuation year and plan-level Social Security COLA. Existing legacy source-level COLA remains outside the guided active inputs.
- `SocialSecurityDialog` retains its existing explicit convention checkbox and save behavior. No new year editor, conversion, rebase or automatic financial adjustment was introduced.

Actual assumptions/validation:

- `AssumptionsView(true)` already exposes eight guided inputs and applies through the existing validated input structure. `InputException` carries its actual control: the new expansion logic uses that association, not exception-message guessing.
- `AssumptionsWizardStep.onEntering` calls the existing refresh path, which retains dirty/pending controls. The existing editor already performs dirty-state bookkeeping on edits; this phase adds no validation-on-every-keystroke mechanism.
- No material discrepancy required an architecture or financial behavior change.

## 3. Files modified and created

Paths below are relative to the repository; Java paths begin with `src/main/java/com/daviddunn/retirementplanner/` unless explicitly under test.

| File | Change |
|---|---|
| `ui/wizard/ExpensesWizardStep.java` | Guidance, accurate inflation display, selected details, accessibility and selection preservation |
| `ui/dialogs/ExpenseDialog.java` | Guided, visible units/basis/recurrence explanation only |
| `ui/views/AssumptionsView.java` | Guided Basic/Advanced grouping, pending-value summary and explicit error expansion |
| `ui/wizard/SocialSecurityWizardStep.java` | Valuation explanation and derived per-person notices |
| `ui/dialogs/SocialSecurityDialog.java` | Clear gross-monthly and valuation-year wording; unchanged parser/save behavior |
| `ui/wizard/ReviewWizardStep.java` | Actual spending descriptions and deduplicated SS notices inside the existing section |
| `src/main/resources/css/new-plan-wizard.css` | Wizard-scoped Advanced summary/title/focus styles |
| `src/test/java/com/daviddunn/retirementplanner/ui/wizard/CompleteWizardFixtures.java` | Explicitly render/expand Advanced before entering its existing fields |
| `src/test/java/com/daviddunn/retirementplanner/ui/wizard/CompleteWizardPreviewTest.java` | Expanded-state geometry awareness and new rendered clarity scenarios |
| `src/test/java/com/daviddunn/retirementplanner/ui/wizard/WizardClarityTest.java` | New focused presentation, preservation, warning and validation tests |
| `PHASE-7C4-SPENDING-ASSUMPTIONS-SS-CLARITY.md` | This implementation report |

Total: **11 files: nine modified, two new**. Seven presentation production files, three test files, one report. No new production class.

## 4. Spending presentation changes

The introductory text distinguishes recurring annual spending from a one-time purchase, explains the opening-year base and configured inflation, and leaves the section optional. The table exposes description, type, entered dollars, actual inflation category and start. It does not sort or reorder the underlying list.

Selected-row details show the exact `BigDecimal.toPlainString()` amount, annual/purchase interpretation, dates and inflation category. Recurring details identify calendar-year overlap and possible opening-year proration. Existing table formatting remains a display convenience; exact detail values are not rounded or written back.

Existing `ExpenseDialog` controls/builders/parsers are reused. Guided instructions explain that amounts are not monthly, recurring dates are optional, and the one-time editor uses a purchase date and General inflation. Add/Edit/Remove and narrow-layout wrapping remain the existing actions. Returning to the page refreshes from the draft while retaining a valid selection. Empty spending remains valid and receives the existing nonblocking Review reminder.

## 5. Basic/Advanced assumptions design

**Basic Assumptions:** projection start date, horizon, annual investment return, general inflation, federal filing status, state selection.

**Advanced Assumptions:** healthcare inflation and plan-level Social Security COLA, inside a standard JavaFX `TitledPane`, initially collapsed for each new editor. These are the same existing controls and draft values, grouped once during guided-view construction.

Visible guidance explains that return is assumed rather than guaranteed, inflation affects applicable spending/rules, the horizon includes the opening calendar year, and household choice does not set filing status. Existing contextual help is reused for detailed inflation/COLA meanings. No tax-state coverage, filing default, return default or horizon default changed.

## 6. Advanced value-preservation strategy

Expansion/collapse changes only `TitledPane.expanded`. It does not call apply, refresh, plan construction, projections, persistence or default initialization. The controls are not reconstructed. A binding reads their current text for the collapsed summary, explicitly labelled **entered %**. Malformed text is displayed as entered; blank text says `Needs attention (blank)`. The summary never substitutes the last valid financial value.

Existing validation still reads all eight controls regardless of visibility. A failing healthcare/COLA `InputException.control` expands Advanced before applying the field-local error and requesting focus. The existing wizard then scrolls the focused field into view. Next and Review reconciliation remain blocked on that error. Errors survive collapse and unrelated edits; successful existing revalidation clears them. No additional financial validator was added.

## 7. Social Security valuation-year warning logic

`SocialSecurityWizardStep.valuationWarning(plan, source)` is a presentation-only helper. It compares `source.getBenefitValuationYear()` with the successfully applied draft's projection start year. Matching years return no notice. Earlier or later years produce an owner-labelled notice containing both actual years, explaining that the existing calculation rules will be used and suggesting review only if a different year's estimate was intended.

The notice is not an assertion that the benefit is wrong. It does not change the amount, precision, valuation year, claiming age, derived benefit date, COLA or source identity. The current source remains authoritative. No warning flag or state is persisted.

## 8. Cross-page update behavior

The existing Social Security `onEntering` presentation rebuild reads current draft records. It also runs after successful Add/Edit/Remove. Repeated visits clear/rebuild cards rather than append notices. After a valid Assumptions application changes projection start, revisiting SS compares against that new year. Malformed or unvalidated pending start text does not become a fabricated year.

Review derives notices independently from the same helper and current validated draft; it filters empty messages and deduplicates identical messages within the SS section. No listeners modify SS records in response to changing start year. The pre-existing explicit editor convention action remains user-directed and is covered separately from automatic warning behavior.

## 9. Review changes

All six sections and stable Edit links remain: Household, Accounts, Social Security, Pensions, Spending, Assumptions. Review uses current draft values, not a second cache/model.

Spending now includes exact entered amounts, recurrence/purchase interpretation, basis, applicable dates and category. SS summaries retain actual valuation years and add visible, nonblocking notices inside that section. Existing optional-empty statuses and account/spending/opening-RMD reminders remain.

The existing Assumptions summary already exposes start, horizon/end year, return, general inflation, healthcare inflation, SS COLA, filing and state; it remains intact and is exercised with changed Advanced values. Entering Review still validates/applies pending pages and returns to the first failure. Create still performs final validation without automatically saving.

## 10. Accessibility changes

Basic/Advanced headings use standard readable JavaFX labels. Advanced is a focusable standard `TitledPane`, exposing native expansion state and keyboard operation. Existing `WizardFieldRow` help buttons, label/control relationships, requiredness and percentage units are reused for both Advanced controls. Collapsed content uses the standard control's visibility/traversal behavior.

Notices are visible wrapping labels with the actual affected owner/name and years; they do not depend on hover or color alone. Spending Edit/Remove actions have explicit accessible names. Fixed footer, tab navigation and stable Review action names remain unchanged. Automated keyboard/focus tests and actual rendered inspection were performed; Narrator/high-contrast certification and native DPI acceptance are not claimed.

## 11. Draft and lifecycle preservation

No changes were made to `NewPlanDraft`, `NewRetirementPlanWizard`, `NewPlanWizardStep`, `AssumptionsWizardStep`, `ApplicationController`, `MainWindow`, repositories, financial/domain services or serialization. Existing page instances, raw pending text, source IDs/order, ownership, balances, assumptions and active-plan isolation remain the same pathways.

Navigation/expansion introduces no projection, optimization, settings write or file write. Existing end-to-end tests exercise cancellation, declined departure, unsaved Create, selected-state save/reload, exact source values and ordinary projections. The Phase 7B.1 transactional recovery paths are unchanged and remain part of regression verification.

## 12. Focused test results

Commands use IntelliJ's bundled Maven and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository` throughout.

Initial focused baseline (`AssumptionsWizardStepTest,ExpensesWizardStepTest,CompleteWizardTest`): **21 run, 21 passed, 0 failures, 0 errors, 0 skipped**.

An intermediate all-wizard run exposed fixture lookups attempting to enter collapsed, not-yet-skinned Advanced controls. Fixtures now explicitly apply CSS/layout and expand before editing. An added synthetic keyboard test initially delivered its key before native window focus settled; it now waits for real FX pulses, requests/asserts focus, and sends Space to the standard control. These issues were corrected without changing financial assertions or algorithms.

Expanded focused execution (`-Dtest=WizardClarityTest,*Wizard*Test`, `target/phase7c4-focused.log`), before the last test additions: **187 run, 183 passed, 0 failures, 0 errors, 4 skipped**, BUILD SUCCESS. Final focused clarity cases and enabled previews then ran together as the 51-test successful execution below; the complete final wizard tests also passed in the full suite. Intermediate failing executions are not counted as successful verification.

New `WizardClarityTest`: **18 run, 18 passed, 0 failures, 0 errors, 0 skipped** in the enabled-preview verification. Coverage includes grouping/defaults/control identity; raw valid/malformed/blank text preservation; hidden-field Next focus/visibility; Review reconciliation and fresh summaries; real focused Space expansion; matching/earlier/later year notices; owner-specific couple notices; malformed versus applied projection start; unchanged precision/source dates; repeated visits; explicit existing editor action/removal/addition; nonblocking actual Create; spending category/date/order/exact-value presentation; editor Cancel isolation.

Existing wizard tests retain their financial assertions, including single/couple, source editing, all six Edit destinations, ownership/RMD, lifecycle, persistence, state selection and projection compatibility. No goldens were regenerated.

## 13. Full suite results

Full established command: bundled Maven with the required project-local repository and `-DargLine=-Xmx8g test`. No preview or benchmark defaults were changed.

**2,873 run, 2,842 passed, 0 failures, 0 errors, 31 skipped**, BUILD SUCCESS. Elapsed time **7 minutes 18 seconds**, finished October 17, 2026, 07:43:33 EDT.

Existing deterministic, longevity-weighted, Social Security, tax, RMD, Roth, seeded Monte Carlo, JSON, lifecycle and transactional persistence/recovery regression expectations passed without golden changes. The additional opt-in preview method accounts for one additional default skip; enabled relevant previews passed separately. This is a new execution result, not a historical baseline.

The full-suite log is `target/phase7c4-full.log`. Relevant previews are enabled separately; default opt-in skips are not reported as passed visual tests.

## 14. Visual verification evidence

Enabled command selects `WizardClarityTest,GuidedWizardShellTest,GuidedWizardLayoutPreviewTest,CompleteWizardPreviewTest,SevenStepWizardTest` with `-Dcomplete.wizard.preview=true -Dguided.wizard.preview=true`: **51 run, 51 passed, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS, 40.854 seconds.

Actual JavaFX dialogs were rendered on Windows and PNG artifacts opened for inspection. New `target/phase7c4-clarity-preview/` contains **17 states, each with initial and bottom-scroll images**, plus three contact sheets:

- Spending empty, recurring, one-time and 20-row long list.
- Assumptions Advanced collapsed, expanded and validation-error/focused.
- SS matching, earlier, later and couple/different valuation years.
- Populated Review and empty optional Review.
- Narrow Assumptions, Spending, Review and SS.

Opened all three contact sheets and full-resolution collapsed/expanded/error Assumptions, Review bottom, narrow Spending bottom and narrow SS bottom. The images show visible footer actions, compact/wide navigation, wrapped notices, actual selected details and field-local error/focus. In narrow spending, table-cell descriptions may truncate; full exact values and dates remain available below the selected row, with Add/Edit/Remove reachable by vertical scrolling. No essential action requires horizontal scrolling.

Existing preview scenarios also ran: seven-step wide/compact pages, owner-specific source entry, Review routes, spouse confirmation/blocked removal and logical-size fit probes. The five established outer-window budgets (980x700, 980x560, 980x740, 980x620, 900x480), plus the new 490x570 narrow cases, simulate available logical work areas. They are not equivalent to actually changing Windows display scaling. Previous preview methods may continue writing ignored `target/phase7c3-*` directories; those newly generated artifacts are not historical evidence and are not source changes.

## 15. Known limitations

- Native Windows 125/150/200% scaling, mixed-monitor transitions, Narrator and high-contrast use require manual checks. No accessibility compliance certification is asserted.
- Existing tables retain bounded internal scrolling; outer topic scrolling reaches details/actions. Narrow cells may truncate while selected details expose full content.
- Summary percentage formatting on Review uses the existing formatter; the collapsed Advanced summary intentionally shows raw entered percentages rather than disguising pending malformed text.
- Expense editor one-time conventions are narrower than every legacy/domain shape. No existing legacy record is converted or repaired. No recurrence engine was introduced.
- Social Security mismatch is informational, not a validation of the user's estimate or a new revaluation tool. The existing explicit convention checkbox remains unchanged.
- Existing validation reports its first blocking control; cross-field/step summaries remain in the existing validation system. No comprehensive multi-error framework was added.

## 16. Deferred Phase 7C.5 work

Not implemented: broader final usability polishing, more extensive screen-reader/high-contrast testing, wider user acceptance, and any further error-summary refinements. No draft saving, financial model extensions, new assumptions, new expense frequencies, benefit conversions or distribution/licensing work was started.

## 17. Manual Windows acceptance checklist

Use disposable/new plans, not the only copy of a real plan.

1. Open a new single/couple wizard; verify the same seven topics and default assumptions.
2. Leave Spending empty and proceed. Add recurring General/Healthcare entries and a one-time purchase; inspect units, exact selected details, dates, order and editor Cancel.
3. Open Assumptions. Verify six Basic fields, collapsed Advanced and its actual-value summary. Expand with keyboard focus plus Space; edit both existing percentages.
4. Collapse/reopen with valid pending text, then malformed text. Confirm no reset or loss. Press Next with malformed Advanced text; verify expansion, readable field error, focus/scroll and visible footer.
5. Return toward Review using visited navigation with an invalid Advanced value; verify reconciliation stops at Assumptions. Correct/revalidate and check actual Review values.
6. Add SS estimates; inspect monthly FRA basis, claiming age, derived date and actual valuation year. Compare matching/earlier/later years and both owners. Confirm notices do not block Create.
7. Apply a different valid projection start, revisit SS and Review, and confirm notices update without changing source values. Malformed pending start text must not be used as a year.
8. Check all six Review Edit actions and empty optional sections. Cancel once and verify the active plan is unchanged; create another plan and confirm it remains unsaved until the existing save workflow is invoked.
9. Save As/reload a disposable plan; confirm precise expense and benefit values, dates, years, categories and ownership remain intact.
10. Test 1366x768 and 1920x1080 at supported 100/125/150/200% scaling, including compact layout and long lists. Confirm no horizontal scrolling is needed for essential actions and footer buttons remain visible.
11. Check Tab/Shift+Tab, help buttons, focus indication, Narrator descriptions/expansion and high contrast manually. These checks remain outstanding rather than being counted as automated passes.

## 18. Git status

Final `git diff --check` returned 0. An explicit trailing-whitespace scan also checked both untracked deliverables, which ordinary Git diff omits. `git status --short`, `git diff --stat` and untracked-file inventory were reviewed: **nine modified tracked files and two untracked files**, exactly the section 3 inventory. The tracked diff contains 227 insertions and 26 deletions; that stat excludes the new test and report.

Protected-path checks show no changes to financial/domain Java, persistence, application/export services, test resources/goldens, Maven configuration or earlier phase reports. Controller/MainWindow, wizard registration/draft/navigation and analyzer implementations are unchanged. The index is empty; branch and HEAD remain the initial values above.

Generated PNGs, Maven logs, surefire reports and the project-local Maven cache are ignored local artifacts, not deliverable source files. Nothing was staged, committed or pushed. Phase 7C.5 was not begun.
