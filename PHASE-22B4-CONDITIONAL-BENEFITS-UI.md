# Phase 22B4 — Conditional Benefits UI

## Checkpoint A — architecture recorded before production changes

Branch main; HEAD 1bf90ad5c9d594015d75b5dbcc070e50d294954a; index empty. All 94 accepted B1/B2/B3 modified/untracked paths are inventoried and hashed in target/phase22b4/initial-status.txt and initial-hashes.json. No real retirement plan will be accessed.

IncomeSourcesView is an immediate-commit list workspace: accepted Pension/Social Security modal results update the live plan, then invoke MainWindow's existing onPlanChanged callback. That callback advances controller source revision, refreshes projection views through the established workflow, and preserves accepted history. There is no whole-screen Apply/Cancel transaction to extend. Workspace Close remains Phase 17B navigation.

Add one contained Conditional Benefits section below the existing income table/actions, within a scrollable workspace body. Reuse TableView styling, keyboard-accessible actions, a selected-definition detail pane, InputHelp and existing confirmation-alert conventions. Keep the existing income table and editors intact. A focused section owns presentation/actions; RetirementPlan.setConditionalBenefits remains the sole validated list replacement. Commit a new immutable list once only after accepted modal validation; no reconstruction, serialization structure, controller identity API or navigation framework is needed.

Use a modal ConditionalBenefitDialog with a separate control draft, scrollable body and fixed standard Save/Cancel buttons. Link labels with RequiredFieldSupport's TextFlow markers, including conditional field visibility. Display only applicable date/person/age and monthly fields, retaining hidden draft text for exploratory switching. Death-benefit category constrains its trigger to death without destroying the other trigger draft. Build final definitions only from applicable fields, validate with the existing domain constructor and validateFor(household), and leave every draft unchanged on failure. Dates use ISO yyyy-MM-dd with the existing DatePicker and strict text parsing; numbers use BigDecimal decimal input. Percentage entry converts exactly to the domain decimal fraction without rounding.

Existing UUID is preserved on Edit. New Add/Duplicate drafts receive one new UUID; canceled drafts never enter the plan. Duplicate opens an editable copy before commit. The collection setter protects count/duplicate identity/household rules; section transactions also reject a stale edit or changed live-plan reference. Delete uses owned confirmation, affecting today's list only. Display sorting never changes plan order. A byte-identical edit is a no-op. Renames use the normal revision guard but retain B1 normalized financial fingerprint semantics; frozen original documents intentionally retain their original labels/IDs/order.

Read-only treatment is fully taxable ordinary non-pension income and household retained nonqualified cash. No account selector, tax-excluded insurance, annuity basis or new financial type. Show a contextual death-lump insurance limitation and an unsupported-state warning using the authoritative admission state check, without blocking structurally valid storage. Existing execution guards remain unchanged.

Financial evaluator, ProjectionEngine, tax/withdrawal/Roth/RMD/Medicare code, algorithm versions, fingerprints, snapshots, ownership/digests, Monte Carlo streams and PDF workflows will not be changed. Existing complete report contexts already disclose frozen definitions. Verify UI-created definitions through those accepted paths and historical exports.

Verification: Level 2 UI/workflow/persistence tests plus targeted accepted Level 3 financial/history contracts, actual Light/Dark 900×650 and 1180×820 scenes, isolated synthetic fixture and one complete normal Maven regression with unchanged opt-in skips. Use Java 25, required IntelliJ Maven, local .codex-m2/repository and existing -Xmx4g test setting. No exclusions/golden updates. Final implementation evidence will be appended below.

## 1. Executive summary

Implemented current-plan conditional-benefit creation and editing in the existing Income workspace. Users can Add, Edit, Duplicate and confirm Remove without JSON. The editor supports the accepted fixed-date, named-person age/death, monthly and lump-sum contracts; complete selected-definition details distinguish future receipts from current assets.

Only four production UI files change: IncomeSourcesView plus three focused new UI classes. No accepted B1/B2/B3 file is changed. Domain definitions, financial evaluation, taxes, projections, Monte Carlo streams, provenance, fingerprints, historical storage and PDF execution remain their accepted implementations. The complete normal suite passed: **4,441 tests, 4,395 passed, zero failures/errors and 46 unchanged existing skips**. Native Windows acceptance remains pending.

## 2. Existing UI architecture findings

IncomeSourcesView.addPension/addSocialSecurity/replaceIncomeSource/onRemove are immediate modal commits followed by notifyPlanChanged. MainWindow registers its existing onPlanChanged callback: controller.markModified advances sourcePlanRevision and existing refreshProjectionViews invalidates/refreshes calculations. That approved existing workflow remains intact, including its ordinary automatic projection refresh after accepted input edits. Cancel does not invoke it.

RetirementPlan.setConditionalBenefits already validates domain/household references, uniqueness and count, and replaces the list with ConditionalBenefits.validatedCopy. There is no reason for a second plan serializer, benefit mutation model or controller stale flag. MainWindow's existing workspace frames, busy guards, Phase 17B Close, Phase 20 applicable Apply/Cancel actions and existing view instances are unchanged. No Income-wide rollback is invented for already committed modal changes.

PensionDialog supplies the required-field, owned modal, scrolling, validation-focus and standard action conventions. RequiredFieldSupport owns the inline TextFlow asterisk. InputHelp/HelpIcon supplies tooltip and accessible-help presentation. ApplicationThemeManager already styles all application windows, including modal and confirmation dialogs.

## 3. Conditional Benefits section

IncomeSourcesView retains its existing income table, columns, Add/Edit/Remove behavior and callback, with the existing table/actions above the new section in a width-fitting ScrollPane. The first table has a bounded preferred height so both sections are reachable at compact size; extra rows retain normal table scrolling. No sidebar or View-menu item is added.

ConditionalBenefitsPane uses the existing TableView styles, six columns (Benefit, Payment, Trigger, Recipient, Timing, Type), exact decimal currency presentation and named-person labels. A monthly amount says per month; a lump sum says one-time receipt. Names/roles remain clearly distinguished; internal enums and UUIDs are not primary display text. Truncated cells have full-value standard tooltips and accessible text.

Selecting a row opens its full wrapped Selected benefit details pane; double-click or Enter opens Edit. It includes original timing, trigger coverage, escalation, recipient, tax/destination and scope notes. The empty table explains how to add a benefit. The count shows the maximum of 100 and explains why Add/Duplicate are unavailable at the limit. Loading an empty plan generates neither an example definition nor a UUID.

## 4. Add/Edit dialog fields

ConditionalBenefitDialog is a resizable owned modal with a scrollable body and standard fixed Save/Cancel buttons. Name receives initial focus. Its control draft contains:

- Required Benefit Name, Benefit Category, Payment Type and gross Payment Amount; the amount label specifies monthly or lump sum.
- Required Payment Trigger and the applicable required Trigger Date, named Trigger Person/Insured, or integer Trigger Age.
- Required named living Payment Recipient.
- Monthly-only optional Not Before Date and Payment End Date, plus required Annual Escalation (%) with valid zero default.
- Optional inclusive Coverage Start/End for the trigger event.
- Read-only Financial Treatment and contextual limitations.

The dialog offers only the current closed enums through friendly labels. Death Benefit category constrains Payment Trigger to death; a readable explanation accompanies the disabled trigger choice rather than relying solely on its dimmed presentation.

## 5. Dynamic field visibility and draft retention

Fixed-date mode shows only Trigger Date; age mode shows person/age; death mode shows the triggering person. Monthly timing/escalation disappears for a lump sum. Hidden row/control/label managed and visible state is synchronized, so hidden required markers do not reserve form space.

Controls retain their separate draft text across exploratory switches. Switching Conditional Income to Death Benefit remembers the previous compatible income trigger; switching back restores it. Editing an existing Death Benefit does not change its otherwise-compatible death trigger when the category changes. Save builds a closed Trigger and includes only applicable timing fields; a lump always has null not-before/end and zero escalation. Irrelevant hidden text cannot leak into the committed record.

## 6. Required-field indicators

Every required visible field uses RequiredFieldSupport.attach and its TextFlow marker, labelFor relationship and accessible required help. No second asterisk renderer or global CSS is introduced. Conditional requiredness follows visible applicable controls. The shared legend appears at the end of the form. Attachments are closed when the modal hides.

Tests inspect actual TextFlow graphics and marker visibility for date/person/age/escalation changes, not just label strings. Existing RequiredFieldSupport and navigation tests also pass focused verification.

## 7. Domain validation mapping

UI syntax checks cover required choices/text, decimal parsing, ISO yyyy-MM-dd dates and whole-number ages. Accepted definitions must pass ConditionalBenefitDefinition's immutable constructor and validateFor(household); collection commit must pass the existing plan setter. The UI neither changes nor bypasses domain validation.

Amounts use BigDecimal directly, without grouping/currency symbols, rounding or floating-point conversion. The domain retains 0–1,000,000,000,000, precision at most 24, scale −12 through 8. UI percentage entry is converted exactly with movePointLeft(2); the domain's 0–1 rate and scale bounds remain authoritative. Values from zero through the exact upper monetary boundary, eight-place dollars and six-place percentage entry are tested. Invalid negative/excess/NaN/infinite/overprecision input is rejected. Date years remain 1000–9999 and age 0–120. The UI does not impose a projection-horizon restriction.

Errors appear by the affected field, with invalid styling and a persistent Benefit not saved message above the scroller. Focus and scroll reveal the field; raw text remains unchanged. Only successful validation populates the modal result. Closed-domain explanations are mapped to relevant controls; persistence exception messages are not displayed by this editor.

## 8. Recipient and death rules

Primary/Spouse choices use reliable PersonReference values with friendly actual names. Single-person plans offer PRIMARY only. Save rejects an absent-spouse choice and rejects the same triggering deceased person as recipient. Reverse death/recipient direction is also tested. There is no JOINT, external person, estate or invented spouse option.

The editor explains January 1 modeled deaths and living-recipient eligibility. It does not select a scenario death, create mortality draws or change survivor payment calculations.

## 9. Timing and coverage

Monthly help explains inclusive eligible calendar months, partial months counted whole, living-recipient cessation, and escalation from the first eligible payment year after not-before, including eligibility before projection opening. Not-before and recurring termination are separate optional controls.

Coverage is explicitly labeled as qualifying the trigger event, not terminating an already-active recurring survivor stream. Domain rejection covers reversed/impossible bounds, known triggers outside coverage and death coverage with no possible January 1. A same-day qualifying January 1 coverage interval is accepted. Lump sums have no monthly-only fields and are described as one eligible trigger-year receipt, with no pre-opening replay. Legacy Pension conventions remain untouched.

## 10. Tax and destination disclosure

Financial Treatment is read-only: Fully Taxable Ordinary Non-Pension Income and Household Retained Nonqualified Cash. It explains expense/tax funding and retention of genuinely unspent after-tax cash. There is no account selector, brokerage deposit, contribution or alternative tax option.

Death-triggered lump sums display the requested warning that actual qualifying life-insurance proceeds generally have different tax rules and tax-excluded proceeds are not modeled by this feature. The list details likewise distinguish a future receipt from an existing account balance. A death benefit label does not imply an implemented insurance contract.

## 11. Unsupported state feedback

The editor asks the existing ConditionalBenefitAdmission state predicate for the supported tax subset. For unsupported jurisdictions, a persistent warning above the scrollable form says the definition can be saved but financial projections/integrated analyses are not yet supported for that state. No selected state changes. The current-plan section repeats a warning when nonempty unsupported definitions are present.

PA/IA valid definitions save successfully in tests; the accepted central financial guards remain authoritative. No pension/interest classification workaround, insurance exemption or state-tax expansion is introduced. Existing supported Michigan, Illinois and modeled no-general-income-tax jurisdictions are unchanged.

## 12. Add/Edit/Delete/Duplicate semantics

Add opens an isolated new draft. Edit opens a draft preserving the selected ID. Duplicate opens an independently editable copy with one new UUID and a review header. No operation commits until modal Save succeeds. Cancel and window close return no definition and do not invoke plan callbacks.

Each accepted action replaces a newly allocated whole list through the validated setter, then refreshes the section and invokes the one existing plan callback. Remove requires the application's standard owned confirmation alert; Cancel leaves the plan unchanged. It affects only today's list, not snapshots. Sorting changes table display order, not stored definition order. A no-change Save, including equivalent numeric scales, does not notify or alter the list.

## 13. Stable identity and transaction protection

One UUID is generated per genuinely new Add/Duplicate draft. Existing IDs survive Edit and all existing storage/copy/history paths. Refresh, selection, sorting and loading never generate replacements. The domain rejects duplicate IDs and more than 100 records.

Commit verifies the loaded plan reference has not changed. Edit also verifies the exact original record still occupies the current list and the accepted record retains its UUID; a stale modal cannot overwrite a newer definition. Add preserves concurrent valid list entries and the setter rechecks count/identity. Remove rechecks the selected original after confirmation. No mutable domain object is retained inside a benefit definition.

## 14. Plan mutation, revision and freshness

The existing IncomeSourcesView notification remains the only MainWindow integration. An accepted financial edit advances normal controller source revision and follows normal projection invalidation/refresh. This preserves in-flight acceptance guards and prior accepted results; no UI-only stale state is added. Cancel, validation failure, display sorting and unchanged Save generate no callback.

Tests use the real section callback with controller.markModified/invalidateProjection to verify revision changes, immutable previous annual results and current applicability. The actual Income workspace/modal actions are independently exercised, including owned dialogs, confirmed removal and no-op Save. Native acceptance will exercise the same installed MainWindow wiring.

## 15. Financial fingerprint compatibility

No fingerprint service changes. B1's normalized financial identity continues excluding labels, opaque IDs and irrelevant definition ordering, while retaining multiplicity and all financial fields. Tests demonstrate a rename advances the source revision guard without changing the normalized integrated financial fingerprint; an amount change changes the financial fingerprint and makes the accepted projection inapplicable.

Exact original-document identity remains intentionally separate: historical labels/IDs/order stay frozen even when financial applicability can agree. No algorithm version, MC payload/fingerprint 1–6, B0 pension tag, B2 deterministic stamp or B3 analyzer capability is changed.

## 16. Historical immutability

Existing snapshots and associations are not edited. Tests retain original projection input documents/annual results through UI add/edit/rename/delete, with original definitions accessible from frozen inputs. Two new real MC view lifecycle tests use editor-created definitions in standalone fixed and paired longevity results, edit today's amount/name, Save As and restart. Associated payload/run/fingerprint remain exact; a throwing engine and throwing restore workers prove historical access does not recalculate. The old result is explicitly Outdated under the edited inputs.

The accepted B3 all-eight history/capability/ownership tests and original-context PDF tests are included in focused and final regression. No old result is promoted to a new financial meaning, discarded or modified because today's list changes.

## 17. Persistence, Save As, restart and swaps

No new JSON property, serializer, repository or plan reconstruction code. Editor output is the accepted B1 collection. Add/edit/delete round trips retain exact descriptions, amounts and UUIDs. Existing absent/null/empty omission contracts are exercised unchanged.

New workflow tests Save, Save As, restart and swap Current/Baseline in both directions using a throwing projection engine. Each original projection ID, annual graph and definition is restored unchanged. Delete then Save As persists an empty current list while the frozen original results retain their benefits. Separate controllers use isolated temporary repositories and drain their background persistence before shutdown.

## 18. Actual Light/Dark screenshots

Actual JavaFX scenes, not CSS approximations, were generated in both themes at 900×650 and 1180×820. Representative monthly/lump editor top, financial-treatment and invalid-input states were inspected. Name/amount/person fields and inline markers are readable; disclosure paragraphs wrap; Save/Cancel remain visible outside the form scroller. A separate compact unsupported-state scene confirms its warning remains above scrolling content.

Income list and expanded selected-definition scenes were inspected in both themes. Cells may truncate long descriptions at compact widths, with accessible full tooltips and complete wrapped selected details. Normal vertical scrolling reaches all detail content without horizontal form overflow. No new stylesheet or global CSS override is required. The established disabled trigger appearance remains, with a readable adjacent explanation of its constrained meaning.

Evidence: target/phase22b4/previews/editor-<PATTERN>-<THEME>-<WIDTH>-top.png, -treatment.png, -validation.png; income-<THEME>-<WIDTH>-list.png / -details.png; unsupported-state-warning-900.png. Layout tests await real pulses after resizing/expansion before capture. Native Windows DPI and screen-reader quality remain acceptance checks.

## 19. Compact layout and accessibility

The dialog body fits width and scrolls vertically; fixed standard buttons remain inside scene bounds in all tested states. Validation scrolls/focuses the field while a persistent top explanation remains visible. Unsupported-state warning likewise stays outside the scroller. Calendar text is ISO with the existing DatePicker popup.

Labels are linked through labelFor; requiredness and help are accessible; controls use meaningful accessible names; table cells expose full values. Standard keyboard traversal, Enter/default Save and Escape/Cancel semantics are retained. Table Enter edits; Add/Edit/Duplicate/Remove are focusable standard buttons. Confirmations and editors have the workspace's window owner. Full native keyboard, multi-monitor/DPI and screen-reader operation is not claimed from structural JavaFX assertions alone.

## 20. UI-created financial execution examples

The synthetic UI-created couple plan has $500,000 brokerage assets, zero returns/inflation/SS/spending, January 1, 2030 opening, primary death January 1, 2031, living spouse, $2,500/month survivor income and one $400,000 covered lump sum.

| Year | Conditional gross / federal AGI contribution | Receipt facts |
|---|---:|---|
| 2030 | $0 | No trigger yet |
| 2031 | $430,000 | $30,000 monthly income plus $400,000 once |
| 2032–2034 | $30,000 annually | Monthly income only; no repeated lump |

Actual ProjectionEngine results agree with literal receipt oracles, cash-source/use reconciliation and ending assets = prior assets + receipts − taxes − Medicare for this controlled no-growth case. Persisted account balances and plan JSON remain unchanged by projection. UI timing/amount fields are not another financial calculator.

Rich MI/IL scenarios use the UI-created monthly definition and compare every annual BigDecimal financial getter with an independently scheduled living-recipient pension control, except intentionally different Michigan retirement-income classification. RMD, tax-funding withdrawals, Roth/asset/estate outcomes and recorded Medicare MAGI agree. Existing B2 tests retain the broader IRMAA/Roth/timing financial controls.

UI-created benefits also execute through integrated individual/Quick and generated fixed MC with positive expense volatility. Same seed reproduces outcomes; original benefit/expense capabilities and disclosure remain frozen after live edits. Complete normal regression exercises all eight accepted B3 modes and weighted boundaries without financial changes.

Current/history projection PDFs generated from the original frozen UI plan have identical extracted text and page count after today's definitions change/delete. Their rendered assumption/receipt pages show $400,000 as one future/received lump, separate $30,000 annual income, recipient, coverage and full tax treatment; neither is an existing brokerage balance. No PDF renderer or workflow is modified.

## 21. Focused verification

Regression classification is Level 2 for the actual UI/input-workflow implementation, with targeted Level 3 financial, identity and historical contracts plus the user-required complete normal suite. Java 25.0.4.1, IntelliJ Maven, workspace .codex-m2/repository and -DargLine=-Xmx4g; synthetic/temporary data only.

| Log under target/phase22b4 | Result |
|---|---|
| compile.log | Production compile passed |
| focused-ui.log | 47 tests passed before the last validation/preview additions |
| history-navigation.log | 23 tests passed, including two UI-created analyzer restart/history cases and existing navigation/required-field tests |
| final-focused.log | 391 passed, zero failures/errors/skips: all benefit tests plus B0 pension, Phase 21 standalone/paired expenses, projection history/swap, fingerprints, navigation, required indicators and PDF directory/workflow |
| final-editor-preview.log | 47 passed, zero failures/errors/skips after the last scoped UI/readability and preview adjustment: 39 editor, three actual-scene, five workspace transaction tests |

Counts overlap and are not cumulative unique tests. B4 adds 54 normal tests across five test classes, plus a shared synthetic UI helper. No existing test source is weakened or modified.

Development failures are documented: the first 35-test run found one real field-routing defect (coverage was mistakenly matched as age in a validation-message substring); the UI mapping was corrected. A 40-test run correctly rejected the stale original controller's Save after another controller changed its destination; the delete round trip was isolated with a different Save As filename. One test compile used incorrect existing method/constant names and was corrected to the actual APIs. A 45-test run had a PDF capitalization assertion and a January-1 SS fixture claiming-date mismatch; a seven-test diagnostic isolated the latter. The fixture now uses the authoritative candidate generator's date convention. These required no financial rule, guard, golden or provenance change. A synthetic invalid-role test also revealed an unhandled display-converter spouse lookup; the UI formatter now checks household membership before formatting. Final checks have no unresolved test failure.

Existing JDK/JavaFX unnamed-module/native-access warnings, transient theme/Modena conversion warnings in broad existing preview tests and PDFBox notices when replacing generated test PDFs are recorded in logs. Generated reports are never read while being overwritten. No build settings or global CSS were changed to suppress warnings.

## 22. Complete Maven regression

**BUILD SUCCESS, exit 0: 4,441 tests, 4,395 passed, zero failures, zero errors, 46 unchanged existing skips across 497 suites.** Duration 14:35, completed November 6, 2026 at 14:36:09 Eastern. This is the accepted B3 total plus 54 new passing tests. Exact invocation:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-DargLine=-Xmx4g' test
```

Log: target/phase22b4/full-regression.log; process exit evidence: full-regression.exit; independent XML totals: full-regression-totals.json. XML counts use exactly the 497 classes identified in this run rather than stale earlier report files. No test-class selection or additional exclusions. No production/test source changed after launch; only this report and ignored verification artifacts were completed during the run. Source hashes confirm that boundary.

Existing financial/seeded goldens, Phase 21 random/expense contracts, B0 pension consistency, B2 financial oracles, B3 all-eight execution/provenance/history tests, fingerprint/snapshot compatibility, Save/Load/Swap and PDF workflow/directory tests passed unchanged. No golden was regenerated. Intentional storage-fault tests log expected atomic-write failures while asserting safe preservation; those warnings are not unresolved Maven errors.

## 23. Existing skips and untested scope

The final 46 skips across 33 suites match accepted B3 exactly by class, case name and reason: zero added, removed or changed entries. Inventory: target/phase22b4/normal-skips.json, compared with target/phase22b3/normal-skips.json. They cover established opt-in production/capacity/visual/real-plan audits and an explicitly disabled mortality benchmark. No opt-in real-plan audit or production-scale experiment was enabled. No normal regression class was intentionally omitted. Native external PDF viewer, save-dialog interaction, DPI and screen-reader acceptance remain operator checks.

## 24. Modified files

Production paths beneath src/main/java/com/daviddunn/retirementplanner:

1. ui/views/IncomeSourcesView.java — contained section and existing callback wiring.
2. ui/views/ConditionalBenefitsPane.java — new current-plan list, details and atomic list actions.
3. ui/dialogs/ConditionalBenefitDialog.java — new draft modal and authoritative validation mapping.
4. ui/benefits/ConditionalBenefitPresentation.java — new friendly exact-decimal input presentation.

New test paths beneath src/test/java/com/daviddunn/retirementplanner:

- ui/dialogs/ConditionalBenefitUiTestSupport.java.
- ui/dialogs/ConditionalBenefitDialogTest.java.
- ui/dialogs/ConditionalBenefitUiPreviewTest.java.
- ui/views/ConditionalBenefitsWorkflowTest.java.
- domain/projection/ConditionalBenefitUiFinancialTest.java.
- ui/montecarlo/ConditionalBenefitEditorHistoryTest.java.

This report is new. Total B4 inventory: 11 paths, with no overlap with the accepted 94 starting changed paths. No domain, calculation, tax, snapshot/fingerprint, repository, settings, PDF, navigation, build or golden-resource file is changed by B4.

## 25. Native Windows acceptance checklist

Use the isolated fixture in section 26, preferably Save As to another test filename before experimenting. Native acceptance is pending, not inferred from automated previews.

1. Open the synthetic plan; select View → Income. Locate Conditional Benefits and inspect both complete definitions.
2. Edit Survivor Lump Sum ($400,000); change to $450,000; Cancel and confirm $400,000 remains. Repeat with window close.
3. Reopen, Save the edit, confirm stable identity through Save/reload and the normal revision/projection freshness behavior. Confirm the brokerage opening balance is still $500,000.
4. Add an age-triggered monthly payment to Primary/Spouse. Verify name/category/payment/trigger/recipient required markers and exact decimal amounts/percentages.
5. Switch Age/Date/Death and monthly/lump, then switch back; retained draft text remains. Save includes only applicable fields. Death Benefit constrains its trigger without reusing Social Security claiming ages.
6. Exercise invalid/blank fields, same-person death recipient, reversed/impossible coverage, invalid dates/amounts/escalation and confirm visible field feedback with no commit.
7. Inspect tax/destination and death-lump insurance notes. Check a PA/IA synthetic copy: definitions may save, but execution is explicitly unsupported; no state changes automatically.
8. Duplicate a definition and inspect/edit the independent copy; Cancel a duplicate. Confirm Remove requires deliberate confirmation. Delete only the newly created test copy.
9. Save, Save As, restart/reload. IDs, descriptions, amounts and dates survive. Display sorting does not reorder financial inputs or advance revision.
10. Run the ordinary projection: original fixture has $0 conditional receipts in 2030, $430,000 in 2031 and $30,000 afterward, with actual taxes/Medicare/retained cash. After an accepted $450,000 edit the lump contribution changes accordingly.
11. Run integrated Quick/individual and a small generated fixed/longevity MC; optionally compare the independently saved baseline. Inspect progress, current run footer, original assumptions, cancel/failure and prior-result retention.
12. Capture two projections/baseline, edit/rename/delete today's benefits, Save/restart and swap both directions. Original history/analyzer inputs, values, IDs and PDFs remain frozen and exportable without recalculation.
13. Inspect Light/Dark at 900×650 and 1180×820, native DPI, tab traversal, Enter/Save, Escape/Cancel, screen reader and ownership of editors/confirmations.
14. View/Save/Cancel projection/analyzer PDF with the configured default export directory. Preserve charts/tables and original conditional definitions/receipts; no future face amount appears as an existing account balance.

## 26. Safe synthetic fixture

Exact path:

```text
C:\Users\david\IdeaProjects\retirement-planner\target\phase22b4\synthetic-conditional-benefits.json
```

Created from accepted editor results through JsonRetirementPlanRepository only if the path did not already exist. Later tests preserve that file. It contains synthetic Primary/Spouse (DOBs 1960-01-01 / 1962-01-01), one Primary brokerage with $500,000, zero-dollar SS records, Florida, zero return/general/healthcare inflation, no ordinary spending, 2030–2034 projection and primary death January 1, 2031.

Survivor Income: $2,500/month after PRIMARY death to living SPOUSE, no extra monthly date bounds or escalation. Survivor Lump Sum: $400,000 once on PRIMARY death to living SPOUSE, inclusive trigger coverage 2030-01-01 through 2040-12-31. Both use the sole fully taxable non-pension/household retained cash treatment. There is no premium, tax-excluded insurance, policy balance, external beneficiary or prepopulated historical result. No real user data was used or replaced.

An ignored VerifyB4Fixture helper independently loaded this exact existing file through the production repository, checked the household/two definitions/$500,000 brokerage, and projected the literal $0/$430,000/$30,000 gross-and-AGI sequence. It performed no write. native-fixture-verification.log records success; native-fixture-hash.json confirms unchanged SHA-256 B29FF32CC41ADA77A215D222A3D4F3BE81B11E9367D4F95A6DABF7A02C34D37F. This is synthetic automated verification, not operator acceptance.

## 27. Git and accepted-work preservation

Starting and final branch main, HEAD 1bf90ad5c9d594015d75b5dbcc070e50d294954a, empty index. All **94 accepted starting paths remain byte-for-byte unchanged**, with zero missing paths and zero staged entries. B4 uses 11 additional paths; the combined final status has 105 paths. Accepted work is not relabeled as B4 modifications. Initial/final status, HEAD, preservation hashes and B4 inventory remain under target/phase22b4 (initial-hashes.json, final-preservation.json, final-status.txt, final-head.txt, b4-files.txt). No source changed after the successful full-suite launch.

git diff --check passed. Separate whitespace checks cover the untracked B4 source/report files, which ordinary Git diff does not include. No trailing-whitespace issues were found. The synthetic fixture's hash remains unchanged after the full suite and independent production-loader verification.

No staging, commit, push, reset, clean or revert. No actual user plan, backup, historical artifact or running user application is accessed, modified or closed. Existing test data/artifacts under ignored target are synthetic; accepted production code/reports remain intact.

## 28. Deferred product capabilities and stop gate

No new financial capability is introduced. Tax-excluded life proceeds, premiums, insurance cash/surrender values, annuity basis/exclusion ratios, purchase funding, new account types or deposit destinations, external beneficiaries, estate settlement and post-last-death guarantees remain deferred. PA/IA conditional classification and benefit-bearing supplied Monte Carlo worlds remain unsupported. Benefit-bearing weighted analyses retain B3's verified slower reference path.

No New Plan Wizard presentation/editor, Pension/Social Security editor, settings, navigation or analyzer/PDF redesign is included. Newly created plans can configure benefits after entering the existing Income workspace. Existing NewPlanDraft/reconstruction contracts preserve collections but no new wizard control is added.

Stop after B4 implementation, automated verification and this report. Request native Windows acceptance before further Phase 22 work. Do not declare native acceptance or commit without the user's separate authorization.

**Recommendation:** Phase 22B4 is ready for native Windows acceptance using sections 25–26. There are no unresolved automated regressions. Existing financial/product/state limitations and native interaction checks above remain explicit; no further Phase 22 implementation is started.
