# Phase 14A - Deterministic Recurring Expense Escalation

## Implementation summary

Implemented the approved recurring-expense selector: **General Inflation**, **Healthcare Inflation**, and **No Inflation**. New expenses default to General Inflation. Editing a Healthcare expense preserves its existing policy unless the user changes it, including an explicit change to No Inflation.

No Inflation keeps the entered amount fixed in nominal dollars before the existing active-year, first-year proration and survivor adjustments. Existing General/Healthcare financial behavior is unchanged. Expense volatility, random generators, Monte Carlo sampling and the approved later stochastic model are outside this implementation.

Native Windows acceptance remains pending.

## Baseline and preserved work

- Branch: `main`; initial and final implementation HEAD: `7ee0365b3ce59cdcfb18c9ac161a2b2e49aad57d`.
- Read AGENTS.md and the Phase 14 architecture audit; inspected actual expense, projection, persistence, fingerprint, snapshot, editor and wizard implementations.
- Pre-existing modified `.idea/encodings.xml` and untracked `PHASE-14-RECURRING-EXPENSE-STOCHASTIC-ARCHITECTURE.md` are preserved and excluded from this implementation inventory.
- Index was empty. No staging, commits, pushes, resets, cleaning or discarding were performed. No user retirement-plan files were changed.

## Domain and persistence design

The existing `GrowthCategory` is used by expenses and already represents the first two approved policies. It now has `NONE`, avoiding a second, potentially conflicting policy field. This is a smaller implementation than the audit's suggested nullable override: there is still one authoritative policy, not an original category plus an overriding category.

The existing JSON property `growthCategory` retains its original GENERAL and HEALTHCARE spellings. No property, plan schema version, snapshot version, fingerprint algorithm or compatibility algorithm was added. NONE serializes explicitly only for expenses selecting No Inflation. Missing category/type still means GENERAL/RECURRING. Existing serialized expense objects remain byte-for-byte identical, including BigDecimal scale. The enum's `toString()` remains unchanged; a separate `displayName()` supplies readable UI labels without changing old report metadata.

NONE is valid only for recurring expenses. The domain rejects NONE with ONE_TIME. The existing one-time editor still hides escalation and uses General Inflation. Historically loaded General/Healthcare one-time records retain their existing calculations. Existing validation, including legacy amount handling, is otherwise unchanged.

Backward compatibility means this application reads previous plans; an older binary cannot necessarily read the new NONE enum. No broad migration is introduced. The optional volatility property and its future serialization remain deferred.

## Projection integration and amount semantics

Only `ProjectionEngine.calculateProjectedExpense` changes: NONE returns the original annual amount before the existing General/Healthcare growth branches. General and Healthcare still use the corresponding centralized `EconomicAssumptions` rate once; no duplicate rate or second multiplier is introduced.

The original entered amount is in the projection's **opening-year spending base**, including expenses starting later. Existing behavior remains:

- Calendar-year overlap determines whether an expense is active. A later-year midyear start/end does not introduce new daily/monthly proration.
- First-year recurring expense proration uses `(13 - projectionStartMonth) / 12`, with existing cents rounding.
- Existing post-death expense factors and household-death omission still apply.
- One-time purchase rules are unchanged.
- Annual expenses continue through the existing withdrawal, tax, Medicare, Roth, balance and estate flows. No downstream financial formula was edited.

For a July 2027 opening, three recurring expenses of $1,000 each (General 10%, Healthcare 20%, None) produce expenses of **$1,500.00, $3,300.00, $3,650.00** over 2027-2029. A later-starting 2029 General expense still uses $1,000 x 1.10^2, while No Inflation uses $1,000. These expectations are independently calculated in tests.

The shared projection engine also honors NONE when an existing run-only general-inflation path is supplied: a fixed nominal expense bypasses that inflation path. The original General path and Healthcare configured-rate behavior remain unchanged. No stochastic generator, stream, seed, distribution, volatility control or Monte Carlo execution code was modified.

## Financial fingerprints and historical isolation

Before editing production code, synthetic legacy fixtures captured and repeated exact input-document, integrated-analyzer and Monte Carlo EXACT_V1 fingerprints for both GENERAL and HEALTHCARE. New compatibility tests assert those fixed pre-change values and exact expense JSON. They pass after implementation; no expected financial golden resource was regenerated.

Selecting NONE changes the existing integrated and Monte Carlo financial fingerprints naturally because the financial input differs. The Social Security-only fingerprint stays unchanged because that analyzer does not consume expenses. No family-specific fingerprint contract was changed.

Persistence tests save a complete current/baseline NONE projection, load it into a fresh controller with a projection engine that throws if invoked, and verify original snapshot UUID, input document/digest, chart data and annual financial getters. Save As changes ownership while retaining the original snapshot UUID and input digest. Legacy input documents also restore unchanged. Historical components, original results, snapshot schemas and persistence machinery were not modified.

## Editor and wizard

Both standard and guided `ExpenseDialog` paths expose the same three-choice selector, labeled **Escalation**. A StringConverter renders the labels while keeping the original enum values. Accessible text identifies recurring-expense escalation; existing label association, keyboard controls and semantic theme ownership are reused.

Tooltips explain the opening-year base, existing economic assumptions, fixed nominal meaning, first-year proration and survivor adjustments. Expense lists and wizard tables/details display No Inflation explicitly instead of incorrectly falling through to General. Editing/canceling does not mutate the original immutable Expense.

The guided field's generated test ID changes from `wizard-input-growth-category` to `wizard-input-escalation`; two existing UI test callers were updated. No financial expectations were changed.

## Exact implementation inventory

Production modifications:

1. `src/main/java/com/daviddunn/retirementplanner/domain/financial/GrowthCategory.java` - NONE and presentation labels.
2. `src/main/java/com/daviddunn/retirementplanner/domain/financial/Expense.java` - recurring-only NONE validation.
3. `src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java` - fixed nominal expense branch.
4. `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/ExpenseDialog.java` - selector labels, accessible text and help.
5. `src/main/java/com/daviddunn/retirementplanner/ui/views/ExpensesView.java` - escalation column and labels.
6. `src/main/java/com/daviddunn/retirementplanner/ui/wizard/ExpensesWizardStep.java` - escalation table/details and guidance.

New tests:

7. `src/test/java/com/daviddunn/retirementplanner/domain/projection/ExpenseEscalationTest.java` - exact annual values, dates, death, existing inflation path, legacy defaults, validation, both household shapes.
8. `src/test/java/com/daviddunn/retirementplanner/persistence/ExpenseEscalationCompatibilityTest.java` - pre-change digests/JSON, round trips and analyzer-family financial identity.
9. `src/test/java/com/daviddunn/retirementplanner/ui/dialogs/ExpenseEscalationUiTest.java` - three policies, defaults, edit/cancel, one-time behavior, list/wizard labels, actual two-theme previews.

Existing tests modified:

10. `src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotPersistenceTest.java` - exact NONE current/baseline restoration and independent Save As without engine execution.
11. `src/test/java/com/daviddunn/retirementplanner/ui/wizard/CompleteWizardFixtures.java` - renamed guided selector ID.
12. `src/test/java/com/daviddunn/retirementplanner/ui/wizard/WizardFinancialEquivalenceTest.java` - renamed guided selector ID only.

Documentation:

13. `PHASE-14A-RECURRING-EXPENSE-ESCALATION.md` - this report.

Excluded/preserved: `.idea/encodings.xml` and the preceding Phase 14 architecture report. Diagnostic logs/helper files and screenshots are under ignored `target/phase14a`; they are not intended commit files. No dependencies, CSS, tax rules, financial golden resources or stochastic execution classes changed.

## Automated verification

Used IntelliJ's bundled Maven with the project-local `.codex-m2/repository` and `-DargLine=-Xmx4g`.

| Run | Total | Passed | Skipped | Failures | Errors | Result |
|---|---:|---:|---:|---:|---:|---|
| Existing focused baseline | 83 | 83 | 0 | 0 | 0 | Success, 15.240 s |
| Initial new tests before implementation | 7 | 2 | 0 | 0 | 5 | Expected red: unsupported NONE |
| First implementation focused run | 38 | 38 | 0 | 0 | 0 | Success, 21.002 s |
| Final focused run | 135 | 132 | 3 | 0 | 0 | Success, 27.367 s |
| First complete regression suite | 3708 | 3663 | 44 | 1 | 0 | Failure, 10:28 min; unchanged worker-cleanup assertion |
| Unchanged Stage5DComparisonTest recheck | 23 | 23 | 0 | 0 | 0 | Success, 13.383 s |
| Complete regression repeat | 3708 | 3664 | 44 | 0 | 0 | Success, 10:40 min |

The final focused command selects `ExpenseEscalationTest,ExpenseEscalationCompatibilityTest,ExpenseEscalationUiTest,ProjectionSnapshotPersistenceTest,ProjectionInflationPathTest,ExpensesWizardStepTest,WizardFinancialEquivalenceTest,CompleteWizardPreviewTest,WizardClarityTest,AnalysisFingerprintTest,MonteCarloNumericCompatibilityTest`.

The three focused skips are the existing opt-in `CompleteWizardPreviewTest`, requiring `complete.wizard.preview=true`. The new expense UI tests execute normally and generate their own previews. No test was disabled or assertion weakened for this phase. Logs: `target/phase14a/baseline.log`, `before.log`, `focused-first.log`, `focused.log`, `full.log`.

The full run's 44 existing skips consist of 39 property-gated optional capacity, performance, user-plan audit or visual-preview cases; one explicitly disabled multi-minute production mortality benchmark; and four Mississippi worksheet parameter cases. Their exact case names and reasons are recorded in `target/phase14a/skipped-tests.txt`. No command-line test exclusion was used for the complete suite. The additional complete-run log is `target/phase14a/full-repeat.log`; isolated worker recheck is `target/phase14a/worker-recheck.log`.

`git diff --check` passed after the complete run. There are 14 additional test invocations relative to the 3,694-test prior suite; no financial golden expectations were changed.

The first full run failed only `Stage5DComparisonTest.callbackFailureCleansUpOutstandingWorkers`, line 266: the immediate `threads.stream().noneMatch(Thread::isAlive)` assertion observed a live worker after a callback failure. This test and its coordinator are unchanged; the same complete 23-test class passed on the isolated recheck and in the complete repeat. This demonstrates an intermittent concurrency failure, not proof that its underlying race is resolved. No assertion, test or worker-lifecycle code was weakened or changed to obtain a pass. The successful complete repeat is recorded separately rather than hiding the first failure. Investigating that unchanged worker-cleanup timing issue is separate follow-up work.

## Visual verification

Actual JavaFX tests generated 12 screenshots under `target/phase14a/previews`: standard/guided editors, all three policies, both LIGHT/DARK themes. The owner uses a 900 x 650 scene. Inspected all 12 images: labels, exact amount input, date controls, selector and OK/Cancel buttons are readable and fit without clipping. The guided editor is approximately 560 x 440 and standard editor approximately 521 x 315 in these renders.

Existing theme CSS handles both states; no hard-coded theme colors were added. Automated rendering does not certify Windows DPI scaling, screen-reader behavior or interactive native acceptance.

## Limitations and deferred work

- No per-expense stochastic volatility UI, optional volatility field, lognormal multipliers, pairing protocol or sampling changes; those remain later approved work.
- No new daily/monthly expense proration semantics, expense-ID migration or custom escalation rate.
- Existing historical financial results remain original; changing a current expense policy does not rewrite them or recalculate on restoration.
- Older binaries may reject plans containing NONE. Missing historical data is not fabricated.
- Native Windows acceptance is still required.

## Native Windows acceptance checklist

1. Open existing General and Healthcare plans; verify selectors preserve policies and saved historical results remain unchanged.
2. Add a recurring expense in both regular editor and wizard: default General; select each of the three policies; edit and Cancel without changes.
3. Edit Healthcare to No Inflation; verify the persisted selection survives Save, restart and Save As. Check amount precision and optional dates survive.
4. Verify existing one-time purchase flow still hides escalation, requires its purchase date and uses General Inflation.
5. Run synthetic projections with known General/Healthcare rates and No Inflation; confirm opening-year proration, later-start opening-year base and existing survivor adjustments.
6. Capture a complete baseline with NONE, save/restart, inspect original baseline chart and frozen input comparison. Verify ordinary historical navigation does not recalculate.
7. Change only expense escalation: integrated/Monte Carlo results should assess changed financial inputs; Social Security-only expense-independent applicability should remain unchanged. Preserve established execution guards.
8. Exercise couple and single-person plans and the existing baseline/current workflow; confirm original immutable results and UUIDs remain intact.
9. Inspect regular/guided expense forms, tables, tooltips, keyboard selection and focus in Light/Dark, reduced windows and Windows DPI scaling.
10. Confirm no expense-volatility controls or random spending model were introduced and existing seeded simulations/golden outcomes remain unchanged for unchanged policy.

Stop after Phase 14A for review; do not begin stochastic implementation.

## Final Git state and readiness

HEAD and branch remain unchanged; staging index is empty. The six production files, three existing test files, three new test files and this report are the intended Phase 14A inventory listed above. The pre-existing IDE change and Phase 14 audit remain present and untouched. No source, test, schema, fingerprint or golden changes outside that inventory were made.

**Ready for review and native Windows acceptance**, with the initial intermittent existing concurrency-test failure disclosed above. No native acceptance is claimed. No Phase 14B implementation was begun.
