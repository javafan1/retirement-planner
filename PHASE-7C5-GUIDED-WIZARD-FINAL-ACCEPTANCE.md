# Phase 7C.5 — Guided Wizard Final Acceptance

## 1. Executive summary

Added independent financial-equivalence and final interaction acceptance tests for the completed guided wizard. Four scenarios compare real wizard-created plans with plans independently constructed through the factories/domain constructors used by the established application editors. Exact configured inputs, complete 20-year deterministic results, summary metrics and transactional reload/backup comparisons pass. Three populated scenarios additionally pass complete seeded Monte Carlo result comparisons.

No production defect requiring correction was found. This phase adds two test classes and this report only; the uncommitted Phase 7C.4 implementation remains unchanged. Financial algorithms, defaults, persistence, schemas, resources, lifecycle and golden expectations were not changed. Native Windows assistive-technology/DPI acceptance remains explicitly manual.

## 2. Initial repository and Git state

Initial branch: `main`; HEAD: `de2b3a4bb03e097377e0c43c996f92dfa3b0e3b0`.

Initial working tree contained nine modified tracked Phase 7C.4 files and two untracked Phase 7C.4 files (the report and `WizardClarityTest.java`). SHA-256 hashes of all eleven were recorded in ignored `target/phase7c5-initial-hashes.xml` and checked for preservation. No unrelated work was discarded.

Last completed baseline: Phase 7C.4 full suite **2,873 run / 2,842 passed / 0 failures / 0 errors / 31 skipped**, recorded in `target/phase7c4-full.log` and its implementation report. This is a historical baseline, not Phase 7C.5 verification. New execution results appear in sections 21–22.

Read `AGENTS.md` and all four Phase 7C.1–7C.4 reports. Inspected the actual wizard shell/draft/all steps, shared field/dialog support, PersonCard, AssumptionsView, four item dialogs and OpeningRmdDialog; MainWindow.onNew and controller lifecycle/save/open boundaries; domain factories/models, projection entry/results/metrics, Monte Carlo contract, transactional JSON repository, wizard/lifecycle/preview tests and relevant financial/persistence regression coverage. No material report/source discrepancy was found.

## 3. Scope and files changed

**Phase-local changes: three new files; zero existing files modified.**

| New file | Responsibility |
|---|---|
| `src/test/java/com/daviddunn/retirementplanner/ui/wizard/WizardFinancialEquivalenceTest.java` | Four independent construction-path scenarios, exact inputs, annual results, summaries, reload/backup and seeded MC |
| `src/test/java/com/daviddunn/retirementplanner/ui/wizard/WizardFinalAcceptanceTest.java` | Eleven parameterized/individual interaction cases: editor validation/focus, dates/ownership, Basic errors, Enter/Escape, resizing, later spouse removal |
| `PHASE-7C5-GUIDED-WIZARD-FINAL-ACCEPTANCE.md` | This report |

Pre-existing Phase 7C.4 files remain exactly as received. Generated screenshots, logs, surefire reports, temporary test plans and the Maven cache are not deliverable source changes. No new framework, dependency or production behavior was introduced.

## 4. Seven-step workflow audit

`NewRetirementPlanWizard` registers exactly:

| Position | Title | Stable key |
|---|---|---|
| 1 | Welcome and Household | HOUSEHOLD |
| 2 | Accounts | ACCOUNTS |
| 3 | Social Security | SOCIAL_SECURITY |
| 4 | Pensions | PENSIONS |
| 5 | Spending | EXPENSES |
| 6 | Assumptions | ASSUMPTIONS |
| 7 | Review and Create | REVIEW |

`IncomeWizardStep` is the pension-only page; the legacy INCOME key is not registered. `SevenStepWizardTest`, `CompleteWizardTest` and `GuidedWizardShellTest` verify registration, Next validation, Back without validation, visited-only jumps, compact selection, all six Review Edit destinations, pending-edit reconciliation and final Create/Cancel. The active page instances and isolated `NewPlanDraft` persist across navigation.

New acceptance executes the standard JavaFX default Next through Enter and cancellation through Escape after real FX pulses. Existing default/cancel properties and source `showStep` confirm action selection, including Create on Review. This does not claim physical-key or screen-reader testing of every dialog action.

## 5. Financial-equivalence fixture design

All fixtures are synthetic and fixed. Each scenario uses a 20-year projection beginning **July 1, 2027**, ending in calendar 2046. All eight guided assumptions are entered: 6.25% annual return, 2.25% general inflation, 4.125% healthcare inflation, 1.875% SS COLA, 20 years, actual start date, Single/MFJ as appropriate, and Florida. Other assumptions remain the established factory defaults.

Populated accounts, in order: Primary IRA $800000.1200; Primary Roth $200000.3400; brokerage $400000.5600 (joint only for the couple). The couple then adds Spouse IRA $500000.7800 and Spouse Roth $150000.9000.

Primary DOB is May 6, 1964 (FEMALE mortality category), except the opening-RMD case uses May 6, 1950. Spouse DOB is July 8, 1966 (MALE). Primary SS is $3000.1200/month at FRA, claiming age 68; spouse SS is $2400.3400, claiming age 66. Both stored valuation years are 2026. Benefit dates are independently expected at each actual DOB plus the configured claiming age.

Pensions begin March 1, 2027, end December 31, 2042, with decimal COLA **0.0175**. Primary monthly amount is $1000.5600; couple survivor amount is $600.7800. The couple adds a spouse pension of $900.1200 with $400.3400 survivor amount.

Spending, in order: General recurring $36000.1200/year from the projection start through December 31, 2046; Healthcare recurring $6000.3400/year through December 31, 2035; one-time General purchase $9876.5400 on May 6, 2030. No monthly conversion is used.

| Scenario | Particular boundary | Exact inputs | Full deterministic horizon | Seeded MC |
|---|---|---|---|---|
| SINGLE | Own IRA/Roth/brokerage, SS, pension, three expenses, nondefault assumptions | PASS | PASS — all 20 years and summaries | PASS — 3 trials per plan |
| COUPLE | Distinct people/categories, owner-specific IRAs/Roths, joint brokerage, two SS/pensions, survivor pension amounts | PASS | PASS — all 20 years and summaries | PASS — 3 trials per plan |
| OPENING_RMD | Blank person names, older filer, historical prior balance/distribution, exact decimals and valuation mismatch | PASS | PASS — all 20 years and summaries | PASS — 3 trials per plan |
| EMPTY | Blank names, DOB May 6, 2000, optional financial collections empty, nondefault assumptions | PASS | PASS — all 20 years and summaries | Not applicable: empty-plan legality/zero-flow scenario |

The empty fixture deliberately stays below Medicare age throughout its horizon so the equivalence check concerns a valid zero-flow projection, not an underfunded Medicare scenario. It is not described as a financially complete retirement strategy. No inherited account is configured in these four fixtures; inherited entry/ownership remains covered by existing account tests rather than claimed as new equivalence coverage.

## 6. Independent Plan A and Plan B construction methods

**Plan A: real wizard UI.** `throughWizard` constructs the production wizard using its ordinary public constructor, fills PersonCard controls, and uses normal Next/Review/Edit/Create actions. Accounts use actual `AccountDialog` actions; SS uses each owner's actual dialog; pensions and expenses use the actual guided editors and parsers. No completed financial plan is injected into `NewPlanDraft`, no validation is bypassed, and the result is obtained only from successful normal Create.

To fix SS's basis year independently of the test execution date, the UI first applies a 2026 start through Assumptions/Review, then enters SS through its actual dialogs, and finally changes the start through Assumptions to July 2027. This exercises the existing mismatch warning without changing source amounts or valuation years. The opening-RMD case opens the actual Review historical-information dialog, enters prior balance $820000.1200 and already distributed $1000.2500, and presses its existing Save action before Create.

**Plan B: established builders, no wizard.** `throughEstablishedBuilders` uses `RetirementPlanFactory.createSinglePersonPlan`, Person setters/Household membership, `AccountFactory.create`, `SocialSecurityIncome`, `Pension`, `Expense`, `EconomicAssumptions`, `TaxAssumptions`, `PlanningAssumptions`, `StateTaxAssumptions` and `OpeningRmdAccountData`. These are the constructors/factories used by existing editors. All Plan B financial values are independently specified fixture literals; no wizard draft/helper or Plan A data is used. Plan B inputs are **all builders**, not automation of the active-plan tabs. Shared scenario constants specify test intent, not shared mutable financial objects.

## 7. Exact financial input comparison results

All four pass before projection. A complete Jackson persisted-input tree comparison covers household membership/DOB/category, ordered account and income collections, types/owners, all configured dates/amounts, participant/default information, expense categories/types, all assumptions and state. No fields/IDs are removed, rounded, renamed or normalized to obtain equality. These fixtures generate no differing nonfinancial ID requiring exclusion.

Additional direct BigDecimal `assertEquals` checks preserve **scale as well as value** for account balances, historical RMD amounts, SS FRA amounts, pension monthly/survivor amounts and pension decimal COLA, and expense amounts. Percentage assumptions use the existing numeric semantics and complete input comparison; no general rounding tolerance is introduced.

The same comparison runs after transactional reload and backup load and again after calculations to detect input mutation. Each construction path owns independent domain objects and account identities.

## 8. Deterministic annual projection comparison results

Each plan runs through ordinary `new ProjectionEngine().project(plan)` with the same shipped rules and default projection context. No strategy override, death/mortality override or separate calculator is used.

For every chronological year, tests compare every public no-argument `ProjectionYear` getter returning BigDecimal, int or enum. Actual APIs include calendar/projection year, primary age, investment growth, guaranteed income, annual expenses, cash need, withdrawals, all RMD components, requested/actual/owner Roth conversion fields where applicable, federal AGI/taxable SS/taxable income/tax, state identity/tax, tax-funding withdrawal, Medicare/IRMAA amounts/bracket, ending investable assets, retained assets/cash and after-tax estate.

Additionally compare the complete `HouseholdSocialSecurityResult`, `HouseholdCashSettlement`, and ending balance of each corresponding account **by input order**, using each projection's own account object. For single-person plans, the existing spouse-only throwing getter is not called: `spouseRothConversion()` absence is compared explicitly, preserving absent versus zero semantics. Annual pension/other guaranteed income is represented by the actual aggregate guaranteed-income API; no nonexistent pension-only annual result is invented.

The complete `ProjectionMetricsCalculator` record is compared, including total growth/income/taxes, peak tax, ending assets/net worth, after-tax estate, lifetime withdrawals/conversions/RMDs/Medicare and household/owner SS. Highest/lowest investable assets also match. Net worth includes the same empty non-investable collection in these fixtures; there is no claim of a nonexistent annual `ProjectionYear.getNetWorth` API.

All comparisons are exact, with no tolerance. Coverage includes 80 original annual row pairs and another 80 reload row pairs. An independent opening-year spending assertion verifies the existing July proration: **($36000.1200 + $6000.3400) × 6/12 = $21000.23**. No production arithmetic or golden resource was changed. Assertion context identifies scenario, year and getter on a mismatch.

## 9. Seeded Monte Carlo comparison

SINGLE, COUPLE and OPENING_RMD each run **three trials per independently constructed plan**, seed **7052026**, annual volatility **0.10**, expected return 0.0625, identical fixed horizon and existing default reference/mortality configuration. Inflation stochastic settings are not enabled. The test asserts all three reference-side trials completed funding, then compares the **entire `MonteCarloAnalysisResult` record**, including settings, outcome ordering/metrics, annual and terminal percentile contracts, taxes and deterministic reference.

All three comparisons pass: nine paired trial outcomes, eighteen trial executions across the two construction paths, plus each analyzer's normal reference calculation. No random streams, seeds, production counts, ranking logic or simulation code changed. This is fixed-horizon equivalence, not a new longevity-weighted Monte Carlo equivalence claim; existing longevity-weighted regressions remain in the full suite.

## 10. Single-person lifecycle acceptance

New equivalence uses actual one-person entry and optional blank names. Existing `NewRetirementPlanWizardTest`, `NewPlanWizardLifecycleTest` and `CompleteWizardEndToEndTest` protect required DOB/category, one-person creation, unsaved activation, deferred empty-plan projection, ordinary populated projection, cancellation/window-close isolation and precise persistence.

Single-person account ownership remains PRIMARY where required; no placeholder spouse is introduced. A valid single-person plan does not automatically switch the existing MFJ default: tests explicitly choose Single where intended and separately verify default independence.

## 11. Couple lifecycle acceptance

Existing tests cover spouse addition, confirmation acceptance/cancellation, financial dependency guards, raw spouse entries, blocked removal with named dependencies and no deletion/reassignment. `WizardFinalAcceptanceTest.removingSpouseAfterLaterPagesRetainsPrimaryAndIndependentFilingDefault` adds successful removal after reaching Review, verifies no phantom spouse, retains primary information and the unchanged independent MFJ setting.

The couple equivalence scenario checks owner-specific income/account ordering, valid joint brokerage, distinct DOBs/categories and survivor pensions. No household/domain ownership or filing rules were changed.

## 12. Navigation and validation findings

New interaction tests exercise malformed account, SS, pension and spending amounts through each actual guided dialog: submission remains open, explicit invalid control/error appears, focus is on the blocking field after pulses, the field is on-screen, unrelated label edits retain the error, and corrected input is accepted with the error cleared. Label/control requiredness and accessible help are checked.

Malformed projection start, horizon and investment return are tested through the real wizard with focus/scroll assertions, Back/Next raw-text preservation and successful correction/Review. Pension date validation is corrected through normal revalidation. A joint brokerage changed to an IRA clears incompatible ownership and blocks until an explicit valid owner is selected.

Existing tests cover invalid DOB, missing mortality category, inherited conditional dates, date boundaries, healthcare inflation, SS COLA, required-field timing, visited-step preservation, optional lists and stale Review prevention. No new numeric bounds or blocking requirements were added. Builder-level cross-field failures retain the existing message mapping; no broad validator refactor was needed.

## 13. Advanced assumptions acceptance

`WizardClarityTest` verifies six Basic/two Advanced controls, existing defaults, initially collapsed state, native-control Space expansion after focus, control identity and raw valid/malformed/blank text preservation. Collapsed summary reads actual pending text. Validation includes collapsed fields, expands before focus/scroll, blocks Next and Review reconciliation, and clears errors only through successful revalidation.

No model mutation or projection is wired to expansion/collapse; inspected `AssumptionsView` bindings only change presentation. Existing editor dirty-state bookkeeping remains unchanged. Final equivalence verifies actual nondefault Advanced rates survive creation/reload and affect identical projections.

## 14. Social Security acceptance

Existing tests verify one initial source per owner, Add/Edit/Remove, supported ages 62–70/default 67, actual-owner DOB-derived date, stored valuation year, plan-level COLA, independent pension records and DOB refresh without monetary changes. New numeric validation checks the actual guided editor and claiming choices/default.

`WizardClarityTest` checks matching/earlier/later years, both owners, valid applied-start changes versus malformed pending dates, repeated navigation without duplicate notices, no automatic rebasing and nonblocking actual Create. The new independent fixtures preserve a deliberate 2026/2027 mismatch and exact source values through projections/reload. No analyzer is invoked by wizard navigation or warnings.

## 15. Spending acceptance

Existing `Expense`/`ProjectionEngine` conventions remain: recurring amounts are annual opening-year-base dollars; one-time editor purchases use General inflation and a purchase date. No monthly conversion, rebase or new recurrence is introduced. Actual stored category is displayed, including any legacy/custom category outside the editor's narrower one-time choices.

New fixtures include both categories, an actual one-time editor action, ordered entries and start/end boundaries. Exact amounts/dates/categories compare before and after calculations/reload. The $21000.23 first-year assertion checks existing proration. Existing clarity/expense tests and rendered long-list previews verify selected-row detail, optional empty lists and editor Cancel.

## 16. Review acceptance

All six existing sections and stable accessible Edit actions pass registration/routing tests: Household, Accounts, SS, Pensions, Spending, Assumptions. Review reads the actual validated draft; no presentation cache/model is added.

Tests exercise optional empty statuses, account/spending/opening-RMD reminders, precise incomes and spending interpretations, all eight assumptions, nonblocking valuation notices, backward changes and invalid pending Advanced reconciliation. New older-filer equivalence enters opening history through the actual Review action before successful Create. New financial fixtures reach Review/Create through validation and compare the resulting complete input contract.

## 17. Persistence and isolation acceptance

`MainWindow.onNew` still opens the wizard before departure confirmation; only an accepted completed result followed by accepted departure invokes `ApplicationController.newPlan`. Empty-plan projection is deferred; populated plans use ordinary projection. No production lifecycle code changed.

Existing lifecycle/end-to-end tests cover dirty active plans, unchanged plan file/revision/cache/tab drafts on Cancel, window close, declined departure and successful untitled/unsaved activation. Wizard/draft classes contain no repository/settings write path; navigation/resize/expand do not acquire one. This is supported by source inspection and existing isolation tests, not a claim of instrumenting every OS filesystem write.

Existing end-to-end tests invoke actual controller Save As/reload on temporary files. New equivalence additionally invokes its underlying transactional repository, saves twice, verifies the recognized backup, and compares exact reloaded inputs/projections. `TransactionalPlanPersistenceTest`, `TransactionalSettingsTest` and `PersistenceControllerRecoveryTest` were included in the focused run. No real user plan was used and recovery semantics remain unchanged.

## 18. Accessibility verification

Automated checks cover explicit current/visited navigation text, disabled future steps, accessible compact selector, six distinct Review Edit names, linked required labels, accessible input help, keyboard-help buttons, Space expansion, error focus/visibility, Enter default Next and Escape Cancel. Resizing retains raw controls and a reachable footer. Spouse confirmation retains conservative Cancel default and named action; removal failures retain actionable text.

Native JavaFX controls expose standard roles/expansion behavior. Warnings use owner/year text and remain visible without hover or color-only meaning. Existing guided help is available through keyboard-operable field-row buttons or accessible-help metadata; not every legacy modal tooltip was converted to a new button.

No Narrator, high-contrast switch, mixed-monitor setup or OS display-scale change was performed. Automated key events are JavaFX event-path checks, not physical-key/assistive-technology certification. Full Tab/Shift+Tab traversal and actual spoken labels remain manual acceptance items. No accessibility compliance certification is claimed.

## 19. Responsive-layout and screenshot evidence

Enabled actual JavaFX previews reran during this phase. Existing output directory names are retained to avoid modifying prior test files; generated images are fresh execution artifacts, not reliance on old screenshots.

- `target/phase7c4-wizard-preview/`: wide single/couple Household, Accounts, SS, Pensions, Spending, Assumptions, populated/empty/error Review, help, compact pages and bottom views.
- `target/phase7c4-clarity-preview/`: 17 states with initial/bottom images, including 20-row spending, collapsed/expanded/invalid Advanced, all valuation comparisons, couple cards, Review and narrow pages.
- `target/phase7c3-wizard-preview/`: rerendered spouse confirmation/blocked removal and 21-row narrow Accounts/detail/actions.
- `target/phase7c3-layout-preview/`: five established logical fit probes and footer/input geometry.

Opened/inspected the three clarity contact sheets, broader wizard contact sheets 0/1/4, full-resolution Advanced error and long narrow Accounts. Also inspected lower-page Review, expanded Advanced, pension details, narrow spending/SS and smallest-budget error/footer images. Text wraps, footer remains fixed, selected details expose truncated table cells, and vertical scrolling reaches actions. No verified clipping/reachability defect required a layout change.

Logical outer-window probes: 980x700, 980x560, 980x740, 980x620, 900x480, plus 490x570 narrow cases. New acceptance resizes 490→980→490→980 while malformed pending return text remains in the same control. Host JavaFX output scale is 1.0x1.0, visual work area 1920x1032. These are **logical fit tests**, not native 125/150/200% DPI validation.

## 20. Defects found and fixes made

No production wizard mapping, financial equivalence, lifecycle or layout defect was found requiring a fix. No production code was modified.

The first new equivalence execution had three test-construction errors, retained in `target/phase7c5-equivalence.log`: it invoked a spouse-only throwing getter on a single-person result; it selected an RMD dialog button by renamed visible text rather than its unchanged ButtonType; and it gave the empty/unfunded fixture a DOB reaching Medicare age within the horizon. Corrections use the existing optional spouse result, actual OK ButtonType, and a younger empty-plan fixture. No mismatching Plan A/Plan B input or deterministic financial value was concealed. The initial couple comparison already passed; all four corrected scenarios pass. No algorithm, expectation or golden was changed to resolve those test errors.

## 21. Focused test results

All Maven commands use IntelliJ's bundled Maven at the AGENTS.md path and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.

| Execution | Run | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Corrected independent equivalence (`WizardFinancialEquivalenceTest`) | 4 | 4 | 0 | 0 | 0 |
| New acceptance + equivalence | 15 | 15 | 0 | 0 | 0 |
| Broad focused wizard/financial/persistence run | 255 | 250 | 0 | 0 | 5 |
| Final enabled acceptance/previews, including both new classes | 66 | 66 | 0 | 0 | 0 |

Broad selector: `*Wizard*Test,SinglePersonCoreProjectionTest,OpeningRmdProjectionTest,TransactionalPlanPersistenceTest,TransactionalSettingsTest,PersistenceControllerRecoveryTest`. BUILD SUCCESS, **54.835 seconds**. Default opt-in preview skips were then enabled separately.

Final enabled selector: `WizardFinancialEquivalenceTest,WizardFinalAcceptanceTest,WizardClarityTest,GuidedWizardShellTest,GuidedWizardLayoutPreviewTest,CompleteWizardPreviewTest,SevenStepWizardTest`, with `-Dcomplete.wizard.preview=true -Dguided.wizard.preview=true`. BUILD SUCCESS, **43.946 seconds**. This final run also includes the added proration and corrected-date assertions.

Logs: `target/phase7c5-equivalence-final.log`, `phase7c5-acceptance.log`, `phase7c5-focused.log`, `phase7c5-previews.log`. Intermediate failures are described above, not counted as passed verification. No assertions were weakened, failing cases skipped, goldens regenerated or seeds/trial counts changed.

## 22. Full-suite test results

Established complete command: bundled Maven, required project-local repository, **`-DargLine=-Xmx8g test`**. No preview/benchmark defaults changed.

**2,888 run / 2,857 passed / 0 failures / 0 errors / 31 skipped**, BUILD SUCCESS. Elapsed **7 minutes 28 seconds**; finished **October 17, 2026, 08:39:27 EDT**. The fifteen new cases account for the increase from the 2,873-case baseline; default skips are unchanged. Relevant previews were enabled separately rather than counted as passed here.

Existing financial, tax, Social Security, RMD, Roth, deterministic/weighted, seeded Monte Carlo, persistence/recovery and lifecycle expectations passed without modification. This is a new full-suite execution, not an inference from targeted tests or the historical baseline.

Log: `target/phase7c5-full.log`. The historical Phase 7C.4 baseline is stated separately in section 2.

## 23. Known limitations

- New equivalence covers four representative wizard-supported configurations, not every account/state/income combination or every possible unsupported legal tax shape. Existing regressions remain essential.
- Plan B uses the exact established builders, not complete UI automation of every active-plan tab. Neither path copies the other's plan or bypasses wizard validation.
- Roth conversions remain disabled under existing initial-plan defaults; new equivalence does not claim nonzero conversion strategy coverage. Existing Roth/RMD/tax-funding regressions protect those algorithms.
- Fixed-horizon seeded comparisons are deliberately small; no new longevity-weighted equivalence universe or statistical-performance claim is made.
- Narrow tables can truncate cells; full selected details are vertically reachable. Standard nested bounded table scrolling is retained.
- Screen-reader speech, physical keyboard traversal, high contrast, native DPI and mixed-monitor behavior remain manual; no certification is implied.
- Deferred post-create settings/features remain in the application tabs. No Save Draft, autosave, plan-name field or new financial model was added.

## 24. Manual Windows acceptance checklist

Use synthetic plans and disposable files only.

1. On actual 1366x768 and 1920x1080 displays, exercise supported 100/125/150/200% scaling and mixed-DPI monitor transitions. Check work-area containment, text wrapping and visible footer on all seven pages and item dialogs.
2. With keyboard only, use Tab/Shift+Tab, compact-selector arrows, visited sidebar steps, Help, Advanced Space expansion, Enter Next/Create and Escape Cancel. Check focus visibility and that hidden controls/future disabled actions are not traversable.
3. With Narrator, check person-specific action names, mortality-category meaning, requiredness/units, current/visited state, Advanced expansion, notices and error announcements. Repeat important paths in Windows high contrast.
4. Build single/couple plans with synthetic accounts, both SS/pension paths and three spending types/categories represented above. Check full selected details, source/order integrity and exact dates/decimal values.
5. Enter malformed data in Household, each item editor and both assumption groups. Verify readable feedback, focus/scroll, unrelated-error retention, correction and fresh Review. Resize while text is pending.
6. After visiting later steps, try spouse removal: cancel; confirm without dependencies; confirm with spouse/joint accounts or survivor income. Ensure no automatic reassignment/deletion and unchanged independent filing status.
7. Inspect actual benefit valuation years and matching/earlier/later notices; apply a new projection start and revisit. Verify no source rebase and nonblocking Create.
8. Confirm all six Review Edit routes, empty optional sections, actual Advanced values and historical RMD entry. Create remains unsaved; cancel/window close/declined departure preserve the active plan and its pending edits.
9. Save As/reload a disposable created plan through the normal application, save an edit to produce a backup, and use the existing explicit recovery flow on a disposable damaged copy. Never corrupt the only copy of a real plan.

Native checks above remain outstanding. Record OS/display/assistive-technology versions and any defect before claiming native acceptance.

## 25. Final acceptance recommendation

**Accept the completed guided wizard against its existing financial model and lifecycle.** Single-person, couple and relevant edge-case financial equivalence pass; full regression has zero failures/errors; focused navigation/validation/accessibility-property checks and enabled rendered previews pass. No financial or lifecycle exception was needed and no production fix was justified.

Native Windows Narrator/high-contrast/DPI/mixed-monitor and full physical keyboard traversal remain explicitly unperformed manual acceptance tasks before claiming native accessibility or broad public-release readiness. These limitations do not invalidate the verified financial equivalence or automated workflow acceptance. No further feature development is included in this phase.

## 26. Git status

`git diff --check` returned 0. An explicit whitespace scan of the three new untracked phase files found zero trailing-whitespace issues. `git status --short`, `git diff --stat`, untracked-file inventory, protected-path checks and the index were reviewed.

Final cumulative working tree: **nine modified tracked files and five untracked files**. Eleven of those entries are the pre-existing Phase 7C.4 work; all eleven initial SHA-256 hashes still match. The only Phase 7C.5 additions are the three files in section 3. Existing tracked diff stat remains **227 insertions / 26 deletions across nine files**, entirely inherited from Phase 7C.4; ordinary diff stat omits the new untracked tests/reports.

Protected financial/domain, persistence, application/export, controller/MainWindow, reference/tax resources, test resources/goldens, Maven configuration and prior tracked reports have no changes. The index is empty. Branch `main` and HEAD `de2b3a4bb03e097377e0c43c996f92dfa3b0e3b0` are unchanged.

Nothing was staged, committed or pushed. No unrelated development was begun.
