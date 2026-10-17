# Post-7C — Insufficient Funding Error Handling

## 1. Summary and root cause

**Classification: expected insufficient-funding domain outcome handled incorrectly at the application boundary (category 1).** Deterministic, real-wizard fixtures reproduce the supplied exception and typed cause. The allocator correctly refuses withdrawals exceeding eligible projected funds. No allocator defect or wizard input-mapping defect was found in these reproductions, and no financial algorithm was changed.

The individual user's plan was not supplied or inspected; this report does not infer their financial amounts. The supplied call path and reproduced cases establish the boundary defect without requiring private plan data.

The original application already had a broad exception handler in `MainWindow.refreshProjectionViews`. Therefore the reported stack trace is not by itself proof of an uncaught FX-thread exception: `handleProjectionFailure` explicitly prints unexpected exceptions and shows an error dialog. The actual problems were:

1. `ApplicationController.getCurrentProjection` called the legacy throwing `ProjectionEngine.project` despite an existing typed funding-outcome API.
2. Recognized funding failure reached the generic unexpected-error presentation, with no funding-specific year/shortfall information.
3. An ordinary failed refresh could leave previous result views populated because the exception interrupted result loading.
4. `MainWindow.onNew` activated the valid plan before automatic projection, then overwrote the failure status with the normal new-plan success text.

The fix uses the existing structured engine outcome, caches its immutable failure metadata, clears projection-dependent views through their existing unavailable paths, and shows a persistent nonmodal funding notice. Plan creation remains successful; projection completion is explicitly unsuccessful.

## 2. Initial audit and baseline

Read repository instructions and relevant Phase 7C.5 acceptance findings. Initial branch `main`, HEAD `a0fa631ccff92b2978450130aa4e91cc04ee94fd`, clean working tree. No pre-existing uncommitted files needed isolation; all previous phase work was preserved.

Inspected `FundingConstraint`, `FundingFailure`, `ProjectedWithdrawalAllocator`, `ProjectionEngine`, `ProjectionExecutionResult`, controller projection/summary/non-investable/cache/new/open paths, `MainWindow.onNew`, refresh and failure handlers, failure-message utility, Results/Results Summary/Dashboard/PortfolioChart/ProjectionYear unavailable paths, and existing domain/Monte Carlo funding, wizard lifecycle and UI/controller tests.

Phase 7C.5's passing baseline was **2,888 run / 2,857 passed / 0 failures / 0 errors / 31 skipped**. Its equivalence fixtures exercised funded plans and deliberately kept the empty-plan fixture below Medicare age; they did not cover valid underfunded plans through automatic post-Create projection. That was the acceptance gap. Historical counts are separate from this phase's new verification below.

## 3. Domain behavior and reproducible scenarios

`FundingConstraint.insufficient` attaches a package-private typed cause to the original exception class/message. Required/available amounts respect the eligible allocation; they are not necessarily the household's total assets. `ProjectedWithdrawalAllocator.allocateAdditionalWithdrawal` exhausts eligible projected balances in existing order and refuses a positive remainder. It does not mutate persisted balances.

`ProjectionEngine.projectWithOutcome` already executes the same yearly calculation and catches **only** a runtime exception whose direct cause is `FundingConstraint`. It returns `ProjectionExecutionResult.InsufficientFunds` with `FundingFailure` and any completed-year prefix. Other errors still throw. The legacy `project` API continues to throw unchanged.

`NewPlanFundingFailureTest` builds all fixtures using actual production wizard controls, normal Next/Review/Create, account and expense dialogs. They use a fixed January 1, 2027 start, three-year horizon, zero return/inflation assumptions, supported Florida state and explicit Single/MFJ selection. These are synthetic deterministic test inputs, not changed application defaults. Young fixtures use DOBs 1980/1982 to isolate spending; the Medicare fixture uses 1960.

| Scenario | Inputs relevant to failure | First failure | Required allocation | Eligible funds | First shortfall | Stage |
|---|---|---:|---:|---:|---:|---|
| Single | Checking $100; recurring spending $1,000/year | 2027 | $1,000.00 | $100.00 | $900.00 | WITHDRAWAL_ALLOCATION |
| Couple | Checking $100; recurring spending $1,000/year | 2027 | $1,000.00 | $100.00 | $900.00 | WITHDRAWAL_ALLOCATION |
| Later depletion | Checking $1,500; spending $1,000/year | 2028 | $1,000.00 | $500.00 | $500.00 | WITHDRAWAL_ALLOCATION |
| Medicare, no accounts | Age 67; no accounts/income; legal zero-dollar expense entry; modeled Medicare | 2027 | $2,434.80 | $0.00 | $2,434.80 | WITHDRAWAL_ALLOCATION |
| Funded control | Checking $10,000; spending $1,000/year | None | — | — | — | Completes all three years; ends $7,000.00 |

The Medicare amount comes from the existing projected rules/calculation observed in the test; it is not a newly hardcoded rule. Its zero-dollar expense is a valid existing entry that makes the plan populated for the established automatic-projection policy; the required cost is Medicare, not fabricated spending. A completely empty plan still retains its approved deferred-projection behavior until projection is requested.

Before production changes, the five reproduction cases passed: the four underfunded cases produced the supplied legacy message/cause; structured outcomes identified the same first failing year and allocation. The later case had one completed year, which is deliberately **not** displayed as a complete projection. Raw BigDecimal failure amounts/scale remain in the domain record; presentation formats dollars only.

## 4. Selected application boundary

Modified `ApplicationController.getCurrentProjection` to call existing `projectWithOutcome`. A `Completed` outcome supplies the same complete `Projection`; an `InsufficientFunds` outcome caches only `FundingFailure` and returns no complete projection. No exception is classified by message and no broad `IllegalStateException` catch was added.

Added one additive accessor, `getCurrentFundingFailure(): Optional<FundingFailure>`, to distinguish unavailable funding from incomplete plan input without launching another calculation. Existing projection method signatures remain unchanged; their established nullable result is reused. This small status accessor is necessary for an accurate persistent UI explanation, not a new exception hierarchy, generalized result framework or financial model.

Caller audit:

- `MainWindow.refreshProjectionViews` is the sole direct external UI caller of `getCurrentProjection`.
- Controller summary and non-investable projection access already return null/empty when no complete projection exists; they now reuse the cached failure rather than retrying it.
- Controller comparison requires complete current/baseline projections. Its Results Summary caller is already guarded by a nonnull current projection; the unavailable path hides comparisons and clears their PDF metadata.
- `peekCurrentProjection` remains a noncomputing cache lookup and returns no stale successful result after invalidation.
- Baseline, analyzers, Monte Carlo and direct domain projection APIs were not changed.

## 5. User-facing behavior

Funding failure is shown in a wrapping, focusable, accessible notice above the ordinary status line, without opening a modal or printing this recognized condition as an unexpected stack trace. It identifies the actual calendar year, required allocation, eligible amount and shortfall. Text explicitly describes the **first unsatisfied allocation**, not total lifetime unmet spending or general insolvency.

Immediately after New Plan Create, the notice starts **“Plan created successfully.”** It then explains that the projection could not be completed, that the plan is preserved, and that the user can edit and retry. The normal new-plan status no longer overwrites that distinction. The completed plan remains active, untitled/unsaved, and editable through the existing tabs.

Ordinary refresh uses the same funding explanation without claiming a new plan was just created. A later Save/status update does not erase the separate persistent notice. A successful refresh hides it. Pending plan inputs, account balances, expenses, assumptions and the configured horizon are not rewritten to manufacture success.

`ResultsSummaryView` disables result export when no complete projection exists. Funded export behavior is unchanged; no PDF format or calculation was modified.

## 6. Cache and stale-result handling

`currentFundingFailure` belongs to the same invalidation lifecycle as the successful projection, summary, assumptions snapshot and non-investable caches. `projectionChanged` clears all of these on the existing invalidate/new/open/recovery paths. The existing UI edit callbacks still mark changes and refresh/invalidate in the established order; `markModified` alone retains its existing semantics.

Multiple summary/non-investable/result reads within one failed evaluation reuse the exact same cached failure object. They do not rerun the engine or emit repeated notifications. Explicit refresh can retry normally but still creates no funding-error dialogs.

On failure, the normal null-projection loading path clears:

- Yearly result tables/details and retained non-investable result data.
- Dashboard financial labels.
- Portfolio and summary chart data.
- Projection-year navigation/result state.
- Summary metrics, baseline-comparison visibility/PDF metadata and break-even presentation.
- Completed-projection export availability.

No completed prefix is substituted, no zero financial projection is returned, and no horizon is truncated. The original three-year plan remains three years even when the first refusal occurs in year two.

## 7. Exact changed-file inventory

| File | Change |
|---|---|
| `src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java` | Reuse existing structured engine outcome; cache funding metadata; additive read-only accessor; clear failure with existing invalidation |
| `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java` | Persistent nonmodal funding notice; preserve Create success versus projection failure distinction; existing views use unavailable path |
| `src/main/java/com/daviddunn/retirementplanner/ui/ProjectionFailureMessages.java` | Format the existing failure's actual year and allocation amounts into actionable text |
| `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java` | Disable completed-result export while projection is unavailable |
| `src/test/java/com/daviddunn/retirementplanner/ui/wizard/NewPlanFundingFailureTest.java` | Domain reproduction, actual File/New/Create, cache/recovery/switch/error-classification regressions and opt-in rendered previews |
| `POST-7C-INSUFFICIENT-FUNDING-ERROR-HANDLING.md` | This report |

Total: **four modified production files, one new test class, one new report**. No financial/domain, persistence, resource, schema, dependency or golden changes.

## 8. Regression tests and financial preservation

New tests cover:

1. Five actual wizard construction scenarios and existing legacy/typed domain outcomes.
2. File → New → Create activation for all five, including correct first year/shortfall and no rejection of valid underfunded plans.
3. No file association or changed new-plan dirty semantics after Create.
4. Cached failure identity across repeated summary/non-investable requests.
5. Successful/failed plan switches and cache resets.
6. Ordinary funded → underfunded refresh clearing every result surface and export availability.
7. Three repeated refreshes without any modal funding dialog.
8. Recovery through the actual non-wizard Accounts Edit dialog and its normal change callback, returning to a three-year $7,000 ending result.
9. Exact funded controller versus legacy engine annual scalar/enum results, Social Security/cash settlement records, optional spouse semantics and ending account balances.
10. A naturally required opening-RMD-information error still throws `IllegalStateException` and is **not** classified as funding failure.
11. Wide/narrow rendered notice and the real post-Create notice, including accessible text and cleared result presentation.

Existing wizard Cancel/window-close/declined-departure, lifecycle, financial equivalence, state tax, projection, controller and MainWindow tests were run unchanged. Existing structured state-tax-unavailability tests confirm unsupported tax is not converted to insufficient funding. Direct domain `project` continues to throw for insufficient funds as before.

One intermediate new test attempted to serialize an entire single-person ProjectionYear through Jackson, invoking an existing spouse-only throwing getter. The test was corrected to compare the actual financial getter contract and explicit optional spouse result, as in Phase 7C.5 acceptance. No domain/serialization behavior or financial expectation was changed to accommodate that test mistake. The intermediate log is retained locally rather than counted as passed verification.

## 9. Test execution results

All Maven commands use IntelliJ's bundled Maven and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.

| Execution | Run | Passed | Failed | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Pre-implementation real-wizard/domain reproduction | 5 | 5 | 0 | 0 | 0 |
| Initial File/New/Create plus reproduction | 10 | 10 | 0 | 0 | 0 |
| Focused boundary/controller/domain tests with preview enabled | 30 | 30 | 0 | 0 | 0 |
| Broad wizard/projection/MainWindow/controller regression | 511 | 505 | 0 | 0 | 6 |
| Final enabled JavaFX previews | 22 | 22 | 0 | 0 | 0 |
| Complete full suite | 2,902 | 2,870 | 0 | 0 | 32 |

Broad selector: `NewPlanFundingFailureTest,*Wizard*Test,*Projection*Test,*MainWindowTest,ApplicationControllerTest,ProjectionFailureMessagesTest`, BUILD SUCCESS, **1 minute 5 seconds**. The opt-in visual cases were enabled separately rather than represented as passed here.

Focused boundary selector also included `MonteCarloFundingTest` and `StateTaxUnavailableProjectionTest`, with `-Dfunding.preview=true`; BUILD SUCCESS, **19.448 seconds**.

Final preview selector: `NewPlanFundingFailureTest,CompleteWizardPreviewTest,GuidedWizardLayoutPreviewTest` with `-Dcomplete.wizard.preview=true -Dguided.wizard.preview=true -Dfunding.preview=true`.

Full suite command uses the established **`-DargLine=-Xmx8g test`** and no changed preview/benchmark defaults. Logs are under ignored `target/post7c-funding-*.log`; counts above are actual executions, with the historical baseline separately labelled.

Full suite: BUILD SUCCESS, **7 minutes 37 seconds**, finished **October 17, 2026, 14:00:00 EDT**. The added fourteen cases account for the run-count increase; one is the opt-in funding preview, enabled and passed separately. Existing financial, seeded Monte Carlo, deterministic/weighted, tax, RMD, Roth, persistence and lifecycle regressions passed without golden changes.

## 10. Rendered visual evidence

Actual JavaFX windows were rendered and PNGs opened for inspection. `target/post7c-funding-preview/funding-1200.png` and `funding-780.png` show a later-year funding refusal with an empty Summary/table/chart, unavailable metrics and disabled export. The notice remains fully wrapped and visible above the status line at both tested widths, with the actual 2028/$1,000/$500/$500 condition.

`target/post7c-funding-preview/funding-after-create.png` was also rendered through the actual MainWindow New → wizard Create path and opened. It shows the preserved Household editor, “Plan created successfully,” the 2027 $900 shortfall, and a distinct incomplete-projection status. No modal blocks further editing. Final enabled previews completed with BUILD SUCCESS in **42.158 seconds**.

Existing wizard and logical-fit previews were rerun to protect the surrounding Create/navigation layout. These are rendered logical-size checks on the current host, not native Narrator/high-contrast/mixed-DPI certification. No private financial plan was loaded for screenshots. Existing narrow Summary horizontal scrolling is outside this targeted correction; the new notice itself is reachable and wraps without horizontal scrolling.

## 11. Remaining limitations and manual checks

- Only the first unsatisfied allocation is reported, as supplied by the engine. No total lifetime shortfall, alternative strategy or inferred funding source is invented.
- No partial-year-prefix results are exposed as a successful full plan. Reviewing partial failure scenarios is a separate feature, not part of this fix.
- Underfunded baselines and unexpected validation/state-tax errors retain their existing failure handling. This fix is for recognized current-plan funding outcomes, not a redesign of every application error path.
- Users must apply valid edits and retry/recalculate using existing controls. Raw pending edits do not silently change the calculation.
- Completely empty plans retain existing deferred automatic projection after Create; a later explicit projection request can still reveal Medicare funding requirements.
- Native Windows DPI, screen-reader announcements and high contrast remain manual checks. No accessibility certification is asserted.

Manual acceptance on disposable synthetic plans: create a valid low-asset plan with positive annual expenses; verify successful unsaved creation, specific funding notice and empty results. Check the later-depletion/Medicare cases, press Recalculate repeatedly, edit assets through Accounts until funded, and switch between funded/underfunded plans. Verify no stale amounts, no modal funding errors, successful projection after recovery, normal Save As, and unchanged Cancel/declined-departure behavior. Never use or damage a user's only saved plan for testing.

## 12. Final verification and Git status

Final `git diff --check` returned 0. The new test and report were also scanned explicitly for trailing whitespace (ordinary Git diff omits untracked files), with zero findings. `git status --short` and `git diff --stat` were reviewed, including both new untracked deliverables.

Final inventory: **four modified tracked files and two new untracked files**, exactly section 7. The tracked diff is 58 insertions and 10 deletions across four UI/controller files; the new test/report are not included in that stat. No generated artifacts are part of the source inventory.

Protected-path checks show no changes to domain algorithms/models, persistence, app/export implementations, resources/tax rules/state registry, schemas, Maven configuration or test/golden resources. Earlier phase reports/tests remain unchanged. The index is empty; branch `main` and HEAD `a0fa631ccff92b2978450130aa4e91cc04ee94fd` are unchanged.

Root cause is established, all required regression executions are green, and the correction is ready for use within the documented limitations. No financial-engine change was required.

No staging, commit, push, reset or unrelated development was performed.
