# Phase 7C.2 — Guided New Plan Wizard Shell

## 1. Executive summary

Implemented the presentation foundation for the existing six-page wizard: **Household, Accounts, Income, Expenses, Assumptions, Review**. Stable local step keys replace Review's positional routing. Wide windows use a numbered sidebar; narrower or shorter windows use an accessible visited-step selector. One existing page instance occupies the scrolling topic area, with the existing dialog action bar outside it.

Added one presentation-only production component, `WizardFieldRow`, applied to projection start, horizon and investment return. It exposes requiredness, visible units, keyboard help and local issues without changing parsing, defaults or domain validation. Guided errors persist until explicit revalidation. Next/Create failure reveals and focuses the blocking input, with a short step summary.

Financial, persistence, analyzer and activation algorithms are unchanged. No new inputs, dependencies, schema, save action, or seventh page were added. Phase 7C.3 has not begun.

## 2. Initial architecture findings

Reference: `PHASE-7C1-GUIDED-WIZARD-UX-AUDIT.md`, particularly sections 2, 5, 7–9, 13–17 and 19. The actual implementation matched its six-page description. Repository `AGENTS.md` and its Maven/local-repository instructions were followed.

- `ui/wizard/NewRetirementPlanWizard` registered six persistent `NewPlanWizardStep` objects, used one `ScrollPane`, validated on Next and all pages on Create, and returned a draft result without saving.
- `NewPlanDraft` owns a separate factory-created plan. `HouseholdWizardStep`, `WizardListStep` and guided `AssumptionsView` edit that draft, not the controller's active plan.
- Review Edit actions used integer indices. This was fragile when changing registration order.
- Back already retained raw controls without validation. Guided assumption refresh already preserves dirty edits. Both behaviors remain.
- Dialog buttons were already outside the content area; this implementation preserves that JavaFX footer rather than replacing dialog semantics.
- Preferred sizing was 760 × 700 with a 460 × 380 minimum. No sidebar or compact selector existed.
- `PersonCard` already had local DOB/category errors. `WizardDialogSupport` knew the current control during parsing but could retain the last parsed field for an unrelated builder failure. Several edit listeners cleared feedback before revalidation.
- `MainWindow::onNew` opens the isolated wizard before departure confirmation. Controller activation, untitled/unsaved behavior and conditional post-activation projection remain untouched.

Scope distinction: the audit proposed eventual seven-topic organization and spouse-removal confirmation. This phase deliberately retains the existing six pages and existing spouse-removal/departure workflows, as explicitly required by the implementation authorization.

## 3. Files changed

Paths use `J = src/main/java/com/daviddunn/retirementplanner` and `T = src/test/java/com/daviddunn/retirementplanner` below. **19 files belong to this phase: 15 modified, 4 created.**

| File | Change |
| --- | --- |
| `J/ui/wizard/NewPlanWizardStep.java` | Local `StepKey` and compatibility default for custom pages |
| `J/ui/wizard/NewRetirementPlanWizard.java` | Responsive shell, visited navigation, Review reconciliation, sizing, focus/scroll and summary |
| `J/ui/wizard/HouseholdWizardStep.java` | HOUSEHOLD identity only |
| `J/ui/wizard/AccountsWizardStep.java` | ACCOUNTS identity only |
| `J/ui/wizard/IncomeWizardStep.java` | INCOME identity only |
| `J/ui/wizard/ExpensesWizardStep.java` | EXPENSES identity only |
| `J/ui/wizard/AssumptionsWizardStep.java` | ASSUMPTIONS identity only |
| `J/ui/wizard/ReviewWizardStep.java` | REVIEW identity, key-based Edit callbacks and accessible Edit names |
| `J/ui/wizard/WizardListStep.java` | Wrapping action row using standard FlowPane |
| `J/ui/wizard/WizardDialogSupport.java` | Retain guided feedback through edits; revalidate explicitly; avoid last-field attribution for generic builder failures |
| `J/ui/wizard/WizardFieldRow.java` **new** | Presentation-only field wrapper |
| `J/ui/components/PersonCard.java` | Guided errors cleared on revalidation rather than incomplete typing |
| `J/ui/views/AssumptionsView.java` | Three guided field wrappers; explicit existing InputException-to-field mapping; opt-in error retention |
| `src/main/resources/css/new-plan-wizard.css` | Wizard-scoped navigation, focus, field and footer styles |
| `T/ui/wizard/GuidedWizardShellTest.java` **new** | Thirteen navigation, state, accessibility, field and focus cases |
| `T/ui/wizard/GuidedWizardLayoutPreviewTest.java` **new** | Five opt-in native-window logical-size probes using existing fixtures/support |
| `T/ui/wizard/CompleteWizardPreviewTest.java` | Updated guided stacked-field layout assertions and phase-specific artifact directory |
| `T/ui/wizard/NewPlanWizardTestSupport.java` | Bounded real FX-pulse synchronization for preview checks |
| `PHASE-7C2-GUIDED-WIZARD-SHELL.md` **new** | This report |

The already-untracked `PHASE-7C1-GUIDED-WIZARD-UX-AUDIT.md` was present at initial inspection and was not edited. Generated screenshots, geometry and Maven logs are under ignored `target/`; they are verification artifacts, not additional source/resource changes.

## 4. Stable step-key design

`NewPlanWizardStep.StepKey` is wizard-local, not a domain or persisted enum. The six registered identifiers are HOUSEHOLD, ACCOUNTS, INCOME, EXPENSES, ASSUMPTIONS and REVIEW. SOCIAL_SECURITY and PENSIONS are reserved but unregistered. CUSTOM is the default for lightweight custom test pages, preserving the existing list-based constructor. Duplicate non-CUSTOM keys are rejected.

Registration order remains one explicit list. Position is still used for numbering, Back/Next and ephemeral layout state; Review now supplies keys through `Consumer<StepKey>`. Identity does not depend on titles or list position. A parameterized test deliberately exchanges Accounts and Income and verifies all five Review Edit links.

## 5. Navigation behavior

| Action | Behavior |
| --- | --- |
| Next | Calls the current page's unchanged validation/apply boundary; advances only on success |
| Back | Returns to the preceding visited page without validation |
| Sidebar/compact selection | Allows only already-visited pages; no forced validation when leaving a page |
| Unvisited future step | Disabled sidebar button; absent from compact choices; internal visited guard also refuses selection |
| Entering Review | Validates/applies all non-Review pages first, including pending edits left by earlier jumps; stops on the failing page with raw text retained |
| Review Edit | Routes by stable identity; each section has a distinct accessible action name |
| Create | Retains all-page final validation, `draft.complete()` and the existing result converter |
| Cancel/close | Retains existing dialog cancellation; no plan/settings writes or analysis |

Visited means only **previously visited**, never financially complete. The heading is `Step N of 6 — Topic`. The retained progress bar is explicitly described both visibly and accessibly as **navigation position, not plan completeness**. Its full Review position is not labeled completed or successful creation. Navigation state is not persisted.

## 6. Responsive layout

```text
Wide:                         Narrow/short:
Title + introduction          Title + introduction
Navigation-position note      Navigation-position note
[numbered sidebar] [heading]  Step N of 6 — Topic
                   [position] [visited-step selector]
                   [summary] [position / error summary]
                   [topic ↕] [same topic ↕]
--------------------------------------------------
Existing dialog actions: Back / Next or Create / Cancel
```

The sidebar reserves 176 logical units plus a 16-unit gap. It collapses below a 732-unit **body** width, preserving approximately 540 units for the topic; outer content padding contributes another 36. It also collapses when the available body height cannot contain its natural preferred height. This height condition matters on high-DPI/short work areas.

The shell uses JavaFX screen **visual bounds**, choosing the owner's screen where available. Initial outer dimensions are capped at 980 × 740 and the work area minus 32 units, with position clamped inside that area. The existing 460 × 380 dialog-pane minimum remains. The main reading region is capped at 1000 logical units when resized larger. No physical-pixel/DPI conversion is applied to JavaFX coordinates.

Only the active topic is attached to the content scroller. The footer remains JavaFX's existing DialogPane button bar. Long domain messages stay in the topic, and the shell summary is short. List action buttons now wrap instead of forcing a wider horizontal action row. Existing bounded table scrolling is retained.

## 7. Shared field component

`WizardFieldRow` extends VBox and wraps an existing Control, label, display-requiredness, visible units, help button/explanation and local error label. Requiredness supports Required, Conditionally Required, Recommended, Optional and Advanced; this metadata imposes no validation rules.

Applied only to the existing guided projection start, projection length and investment return controls. Existing IDs, values, parsers and builders are unchanged. Units are Calendar date, Years and Percentage per year. The existing tooltip text supplies explanations. Changes to the owner's tooltip update visible/accessible help, including single/couple horizon wording.

Help is a real focusable Button toggling readable text, with the existing `InputHelp`/`HelpIcon` tooltip infrastructure retained. No example is inserted as input. No new money/date parser or financial model exists. All other item editors and fields remain available; broad field conversion is deferred.

## 8. Error presentation

- Untouched controls remain unmarked initially.
- Existing validation is invoked on Next, editor OK, Review entry or Create; there is no new domain validation on keystrokes.
- Guided issues survive unrelated edits and incomplete corrections. Explicit revalidation clears old markers and displays the current existing-rule failure. Corrected fields no longer retain obsolete issues after that validation.
- `AssumptionsView.InputException.control` directly identifies the failing input. The three representative wrappers show its message beside that input. Existing PersonCard DOB/category messages remain local.
- Item dialog parsing retains its explicit current-field association. Before invoking the existing domain builder, that association is reset, avoiding attribution of a generic builder failure to the last successfully parsed input. Existing message-based matching for named builder errors remains a limited compatibility fallback; ambiguous cross-field messages remain a summary.
- A short step-level summary explains that validation must be corrected. The shell's existing deferred FX focus/reveal path locates the invalid control, scrolls it into view and requests focus. Tests assert both focus ownership and visible bounds after the queued work.

The phase does not collect every possible domain error at once or replace existing validation with a new framework. Field-local expansion beyond the representative fields remains later UX work.

## 9. Accessibility changes

Sidebar buttons expose number, topic and Current/Previously visited/Not yet visited as text and accessible names. Future buttons are disabled. The compact ComboBox has an explicit accessible name and standard keyboard behavior. Hidden navigation is both invisible and unmanaged. Review Edit buttons identify their section.

Field labels target their controls; requiredness and units are included in accessible text. Help is keyboard focusable. Invalid inputs retain borders and textual messages; navigation is not color-only. Supported JavaFX accessible text-change notification is used for the summary. Wizard-scoped focus borders supplement native controls. Existing Enter/default Next/Create and Escape/cancel semantics remain.

Automated checks include an Up-key event on the actual compact selector, default/cancel button flags and first-error focus/scroll. Windows Narrator, Windows high contrast, full physical Tab/Shift+Tab traversal and real mixed-monitor DPI transitions are **not certified** by these checks.

## 10. Draft-state preservation

No page is reconstructed by navigation or responsive layout changes. The same isolated NewPlanDraft and controls remain alive. Tests retain malformed DOB, malformed horizon and pending name text across Back/visited navigation. Record identity/order tests protect accounts, incomes and expenses across every Review edit route. Existing single/couple tests protect spouse creation/removal guards and owner references.

There is no navigation-time projection, Social Security optimization, Monte Carlo, controller activation, settings write or JSON write. Existing assumptions refresh/apply and household validation remain authoritative. All existing financial/default assertions are retained.

## 11. Lifecycle compatibility

`MainWindow`, `ApplicationController`, repositories and domain models were not modified. The wizard still precedes the existing departure prompt. Cancel and declined departure preserve the old active plan. Successful Create returns the same validated isolated draft through the existing lifecycle; it does not save and does not change the existing new-plan dirty/path convention or conditional ordinary projection.

Existing lifecycle and complete end-to-end tests were run unchanged. These include single/couple activation, cancellation, departure handling, save/reload, exact entered financial values, deterministic integrated evaluation and small seeded Monte Carlo checks. Phase 7B.1 persistence/recovery coverage is included in the full suite.

## 12. Focused tests

All Maven commands used IntelliJ bundled Maven and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`. No dependency/build configuration was edited.

| Execution | Run | Passed | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| Initial stable-identity check: CompleteWizardTest, NewRetirementPlanWizardTest | 23 | 23 | 0 | 0 | 0 |
| Final focused `-Dtest=*Wizard*Test test` | 157 | 154 | 0 | 0 | 3 |
| Opt-in visual/navigation `-Dtest=GuidedWizardShellTest,GuidedWizardLayoutPreviewTest,CompleteWizardPreviewTest -Dcomplete.wizard.preview=true -Dguided.wizard.preview=true test` | 19 | 19 | 0 | 0 | 0 |

The 13 new shell cases include five reordered Review routes plus future/visited navigation, raw DOB/name preservation, Review reconciliation, malformed horizon retention, responsive page identity/footer, keyboard selection, focused/scrolled error, field help/error behavior and unchanged record ordering. Focused tests also include 66 existing state-tax wizard integration cases.

Three preview classes are opt-in and skipped in an ordinary focused run. Both relevant complete/new-shell preview classes were explicitly enabled in the separate visual run; the older household-only preview was not needed. During development a new native-resize assertion ran before the window-manager layout event and failed; its scene-layout check was made deterministic, while actual native windows remain covered by the rendered preview probes. Final counts above are successful reruns, not that earlier attempt.

## 13. Full suite results

Full established Maven command: `test -DargLine=-Xmx8g` with the same bundled Maven and project-local repository. No test filter, benchmark exclusion, seed or trial-count change. **BUILD SUCCESS**, 7 minutes 28 seconds, completed October 16, 2026 at 09:05:41 EDT. Log: `target/phase7c2-full.log`.

| Run | Passed | Failures | Errors | Skipped |
| ---: | ---: | ---: | ---: | ---: |
| 2,838 | 2,809 | 0 | 0 | 29 |

Existing financial regressions, tax fixtures, deterministic/weighted analysis and seeded Monte Carlo tests passed without golden changes. Existing Phase 7B.1 save/recovery and settings tests also passed. Skips retain existing opt-in benchmark/preview conditions; the additional layout preview class is opt-in as well. Relevant wizard previews were separately enabled, not counted as passed by this default run.

Historical Phase 7B.1 baseline was 2,824 run / 2,796 passed / 0 failures / 0 errors / 28 skipped. That historical baseline is not claimed as current verification.

## 14. Visual verification evidence

Actual JavaFX dialogs were rendered on Windows. The host reports a 1920 × 1032 logical visual work area and output scale 1.0 × 1.0. Snapshots were generated using synthetic fixture identities and inspected with the image viewer; no private plan was used.

`target/phase7c2-wizard-preview/` contains 19 topic states, Review bottom views, a hovered investment-return tooltip, four contact sheets and geometry. Inspected contact sheets cover Household single/couple, empty/populated Accounts, Income, Expenses, Assumptions, Review, error/warning states and narrow pages. Individual narrow assumptions and smallest-work-area Household/error/bottom snapshots were opened at full artifact size.

Observed: sidebar text fits at wide size; narrow selector and footer remain reachable; local errors remain readable; focused invalid inputs have a visible border; scrolling reaches the lower assumptions and Review sections. Existing populated Accounts fit the narrow viewport using the existing constrained table policy. No essential text input required horizontal scrolling in the probes.

`target/phase7c2-layout-preview/` additionally contains five logical-window probes, each with Household, validation-focus and bottom-of-Assumptions snapshots:

| Named fit case | Outer logical window tested |
| --- | --- |
| 1366 × 768, 100% reference | 980 × 700 |
| 1366 × 768, 125% reference | 980 × 560 |
| 1920 × 1080, 100% reference | 980 × 740 |
| 1920 × 1080, 150% reference | 980 × 620 |
| 1920 × 1080, 200% reference | 900 × 480 |

These names describe **simulated logical fit budgets**, not changes to Windows display scaling. Tests assert visible footer geometry, a usable vertical viewport and horizontal input fit after actual FX pulses. The smallest probe produced an 884 × 441 pane and a 150-unit viewport at the captured bottom/error state. Native 125/150/200% rendering, font rasterization and monitor transitions still require the manual checks below.

## 15. Known limitations

- Full screen-reader/high-contrast and OS DPI acceptance remains manual; no accessibility compliance claim is made.
- Field-row conversion is intentionally limited to three assumption controls. Other forms retain their existing labels/tooltips and editor summaries, including the limited message-based mapping for named domain builder errors.
- Very short windows require vertical topic scrolling. The retained minimum dialog size is a usability floor, not support for arbitrarily tiny work areas.
- Existing table cell truncation for unusually long user text and larger item-editor DPI layouts are not comprehensively redesigned here.
- The retained position bar reaches its final position at Review, with explicit navigation-only wording; it is not a validated-completion indicator.
- No autosave/draft persistence or external listeners/executors were introduced. Session-local focus/scroll memories disappear with the wizard.

## 16. Deferred Phase 7C.3 work

The Social Security/Pensions split and seven-topic registration, household/account/income UX expansion, broader field/unit conversion and any additional spouse-removal UX require the next authorization. No new financial defaults, qualification assertions or persistence semantics are proposed as a side effect of that UI work. Later spending/assumptions and final usability stages should reuse the controls and keys established here.

## 17. Manual Windows acceptance checklist

1. At actual 1366 × 768 and 1920 × 1080 displays, exercise the requested 100/125/150/200% settings where available. Verify the dialog stays inside the work area, including taskbar and decorations. Move between mixed-DPI monitors.
2. Resize wide, narrow and short on every page; confirm navigation switches without losing text, selection or records, actions remain visible and all fields are vertically reachable.
3. Use Tab/Shift+Tab, arrow keys, Enter and Escape. Confirm focus is visible; disabled future steps cannot be reached as actions; hidden navigation is absent from traversal; Help opens without a mouse.
4. With Narrator and high contrast, verify current-step announcements, requiredness, units, help and validation messages. Record any platform-specific failures before claiming compliance.
5. Enter an invalid DOB and horizon, move Back/through visited steps, and return. Confirm raw text remains; unrelated edits retain errors; successful revalidation clears corrected issues and Review never summarizes stale pending edits.
6. Add representative accounts, Social Security, pensions and expenses for single/couple households. Confirm list ordering, owner references and exact input values remain unchanged after navigation and resizing.
7. Exercise every Review Edit link, including returning directly to Review after an earlier invalid edit. The failing page must reopen with focus and the invalid text intact.
8. Cancel with an existing dirty active plan; confirm no file/settings write or departure prompt. Complete and decline the existing departure prompt; confirm the active plan remains unchanged.
9. Complete an accepted single/couple draft. Verify current unsaved lifecycle, existing tabs and conditional ordinary projection, then use normal transactional Save As and reload on disposable files. Do not alter a user's only saved plan for testing.

## 18. Git status

Initial branch `main`, HEAD `8892c2b19b6105a04c4db75f37418f5092ecb9f4`. Initial working tree contained only untracked `PHASE-7C1-GUIDED-WIZARD-UX-AUDIT.md`; it remains untouched.

Final verification: `git diff --check` returned 0; the four new phase files were also scanned explicitly for trailing whitespace, with zero findings. `git diff --stat` reports 15 modified tracked files; four additional phase files are untracked and listed in section 3. Git status additionally retains the one pre-existing untracked 7C.1 report. `git diff --cached --name-only` is empty. Branch/HEAD remain unchanged.

Protected-path diff checks returned 0 for domain Java, repositories, Maven configuration, tax/reference resources and test resources. No staging, commit, push or history operation was performed. Financial/domain Java, persisted models, repository code, tax/reference JSON, financial fixtures/goldens, Maven configuration and analyzer screens are outside the change inventory.
