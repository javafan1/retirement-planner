# Stage 4A.1 — Current-UI single-person integration

## Starting state and read-only audit
Starting HEAD: `00bbc51 Add single-person Social Security support`.
The 15 existing unstaged/untracked Stage 4A files are preserved. No staging, commit or push is authorized.

The domain already represents one primary and an optional spouse; JSON preserves absence. `Household.getSpouse()` intentionally rejects absent-spouse consumers. HouseholdView nevertheless loads and validates two PersonCards, the New action uses a two-person factory, and ProjectionReadiness dereferences spouse. IncomeSourcesView, OpeningRmdWorkflowService, ResultsSummaryView and ProjectionYearDetailsPane have reachable unconditional spouse assumptions. Account/Pension dialogs offer nonexistent owners. Couple death/survivor controls remain visible in Assumptions and Summary. Tax filing status is stored independently but lacks an input in Assumptions. Stage 3/4A analyzer routing already offers the actual nine-strategy single-person views. Monte Carlo and projection export remain deferred but their normal UI entry points need clear disabling.

Implementation boundary: optional-spouse editing and reusable validation/composition guards; explicit Primary-only New path; actual-member readiness and view iteration; ownership-aware dialogs; independent filing-status editing; hide inapplicable couple controls; disable deferred Monte Carlo/report actions. No financial algorithms, schema, mortality mathematics, random streams, or new-plan wizard changes.

Household replacement must retain the actual primary object and household expenses. Removing a spouse with financial dependencies must be rejected with an explanation. Replacing the household object rather than mutating its membership also avoids changing membership of an existing baseline snapshot.

## Implementation

- The normal controller **New** action uses `createSinglePersonPlan()`. The existing programmatic `createEmptyPlan()` couple factory is retained for compatibility. No spouse is constructed in the Primary-only factory path.
- Household retains its Apply/Cancel editor. **Include spouse (optional)** exposes the second card only when selected. It creates a real Person only after that card validates. Unchecking and applying removes an empty spouse; Cancel restores the saved composition. No blank outlined spouse section remains in the single-person state.
- `PersonInformationValidation` shares the existing required birth-date/mortality-category rules with future input flows. Existing optional-name/whitespace rules are unchanged. Validation runs for Primary and, only when included, Spouse. Both selected cards validate before any existing person fields change.
- `RetirementPlan.setSpouse` is an explicit, non-serialized membership operation. It retains Primary and household expense objects. Spouse accounts/income, spouse/joint portfolio accounts, non-primary legacy Primary accounts, positive survivor-pension elections and configured couple death scenarios block removal/replacement. The message names affected records; nothing is deleted or reassigned automatically. Existing liabilities are not separately persisted on Person (`getTotalLiabilities()` currently returns zero).
- The household object is replaced rather than changing its membership in place. Audit correction to the older architecture note: the current `RetirementPlanSnapshot.fromRetirementPlan` already deep-copies through Jackson. Tests additionally prove the captured baseline household and its couple membership remain unchanged after removal. Baseline semantics themselves were not modified.
- Readiness checks birth dates for actual members. Income, opening-RMD and Social Security rows iterate actual members. The income-summary adapter now has an explicit primary-only constructor with absent spouse fields; the Dashboard hides inapplicable rows. Year details omit spouse/survivor rows and spouse conversions for single-person results. Chart election markers use only actual people.
- Account/Pension dialogs receive the household and offer only valid owners. A single-person pension hides its survivor input and uses appropriate input help. Existing invalid ownership/survivor records are rejected, never silently converted.
- Assumptions and Summary hide couple death/survivor inputs for a single person and call the deterministic horizon the configured projection length/end. Unrelated pending assumptions survive composition refresh; inapplicable couple-only drafts do not cause hidden-field validation errors.
- **Filing Status** is now editable in Tax Assumptions using the already-supported domain enum. It remains independent of household composition; neither adding nor removing a spouse changes it. Existing factory defaults are preserved. Users must explicitly select the appropriate status. No tax/Medicare/IRMAA formula or same-year-AGI behavior changed.
- Existing single-person analyzer routing is reused unchanged: Deterministic Integrated, Social Security Only, and Longevity-Weighted Integrated. All three evaluate nine actual Primary strategies. Selecting results still uses cached results. No new analyzer or mortality implementation was introduced here.
- Monte Carlo menu actions are disabled for a single-person current plan; comparison also requires a couple baseline. A visible disabled menu explanation identifies the Stage 4B boundary. Single-person Summary Export is disabled with a reporting-stage explanation; existing exporter guards and single-person analyzer PDF guards remain. No export format or frozen-report calculation was modified.
- A visual review caught labels collapsing in the narrower PersonCard grid; its label column now retains its natural minimum width.

The additional reachable gaps discovered by integration tests were `IncomeSummaryService` and `ProjectionChartModel.claims`: the first required spouse income, the second constructed a non-null two-element list with an absent spouse. Both were adapter/presentation assumptions, not missing projection mathematics. No Stage 4A structural blocker was found.

## Tests and regression scope

`SinglePersonHouseholdUiTest` exercises actual JavaFX editors and dialog result converters, then the normal controller/repository/engine path. Its nine tests cover:

1. Primary-only creation; required Primary fields; absent Spouse not validated; included Spouse validated; Cancel; addition/removal; baseline membership isolation.
2. Spouse financial dependency messages and atomic rejection of other pending Person edits.
3. Joint-account and survivor-pension removal guards; expense and Primary identity retention.
4. Composition refresh with pending assumption edits, preserving unrelated draft values.
5. Two save/reload cycles with an intervening edit, primary-owned accounts/income, own Social Security, annual RMDs, Roth conversions, positive estate, summary/details and identical reprojected ending assets.
6. All three real nine-strategy analyzers, including current and maximum-ranked strategy identification and absence of spouse elections.
7. Explicit filing status, actual ownership choices, hidden survivor controls and restored couple controls.
8. Normal MainWindow results loading and clean Monte Carlo/reporting deferral.
9. Opt-in visual previews with bounds and label non-truncation assertions.

The UI fixture has a 1965-02-01 Primary, Female mortality category, a 2027 start/20-year horizon, brokerage/Traditional IRA/Roth balances, own Social Security, pension, recurring expense and annual fixed Roth conversion. Filing Status is selected as Single through the assumptions editor. No production financial expectations were changed to make it pass.

Existing couple tests that previously obtained an automatic spouse from a new controller now explicitly configure a couple. Their financial assertions remain unchanged. Household test helpers locate the two TitledPanes and button/status controls by type instead of stale child indices. Golden resources were not regenerated for Stage 4A.1.

Verification logs and final totals are recorded below after final runs. The first complete run exposed two remaining couple test-fixture assumptions (chart preview and Monte Carlo action availability); those fixtures now explicitly add their intended spouse. Earlier focused failures exposed the two reachable summary/chart adapters described above. These were fixed, not skipped.

## Manual acceptance — exact current-UI path

These are synthetic test inputs, not financial recommendations. No JSON editing or dummy spouse is necessary.

1. Choose **File → New**. Open **Household**. Leave **Include spouse (optional)** unchecked. Enter First Name `Primary`, Last Name `Example`, Birth Date `02/01/1965`, Mortality category `Female`. Names remain optional under existing validation; birth date and category are required.
2. Before applying the household, visit **Accounts** and add a Primary-owned **Brokerage** account named `Funding` with balance `1200000`. Return to Household and click **Apply**. This order avoids an interim unfunded projection while the draft has no assets. The existing application may show a funding warning if the household is applied before funding is entered; that warning does not mean a spouse is required.
3. In **Assumptions**, set Projection Start Date `01/01/2027`, configured projection length `20`, and explicitly select **Filing Status: Single**. Click **Apply**. Leave other valid defaults unless testing different assumptions. Household composition does not silently set tax status.
4. In **Income → Add**, select **Social Security**, enter a name (`Own Social Security`), Owner **Primary**, FRA Monthly Benefit `3000`, Claiming Age `67`. Keep the plan-start valuation convention; the start date is derived/read-only. Click **OK**. There is no Spouse owner option. These household, funding, projection and own-benefit inputs are sufficient for all three analyzer modes; a pension, expense and Roth conversion are not required merely to enable the analyzers.
5. Choose **File → Save As** and save a new test JSON file. Close/reopen it using **File → Open**, or reopen the application and load that file. Confirm Household has only Primary and the checkbox remains unchecked. Edit the Primary name, Apply, save and reopen again. No spouse should appear.
6. In **Summary**, choose **Recalculate Projection** if needed. Check Investable Assets, Net Worth and After-Tax Estate; the Social Security card has only Primary and the horizon card has no couple death/survivor inputs. Select a year to inspect detail rows. The configured tax status remains Single after save/reload.
7. Choose **Analysis → Social Security Strategy Analyzer**. Select **Social Security Only**, retain valid Primary longevity/conditioning/valuation settings, and click **Run Nine Claiming Strategies**. Check exactly ages **62–70**, the current age 67, ranked expected PV and no spouse axis/input.
8. In **Deterministic Integrated**, leave the optional Primary death year blank and click **Run Nine Claiming Strategies**. Check nine rows, current-plan identification, deterministic financial details and the optional **Break-Even vs Current Plan** action. This mode uses the configured deterministic horizon.
9. In **Longevity-Weighted Integrated**, click **Run Nine Claiming Strategies**. Check nine rows and the Primary-only mortality inputs. Its expected values use individual lifetime scenarios, not the configured horizon as a mortality substitute. Leave conditioning and valuation at the plan start for this acceptance example.
10. Close the analyzer. Open **Analysis** again: Monte Carlo actions are disabled with the Stage 4B explanation. Summary Export remains disabled pending the reporting stage. These are intentional boundaries.
11. To exercise couple editing, check **Include spouse**, supply a real second person's birth date/category and Apply. Both cards must validate. Cancel must discard an un-applied toggle. An empty spouse can be removed; a spouse with accounts/income, joint accounts or survivor dependencies produces a named blocking message. Resolve those records explicitly before removal. Do not use this test to delete real financial data.

For a fuller projection exercise, replace the single funding account with Primary-owned Brokerage `400000`, Traditional IRA `700000`, and Roth IRA `100000`; add a Primary pension of `1000` monthly starting `01/01/2027`, no end date, COLA `0.02`; add a recurring `Living` expense of `40000` annually with General growth and a valid start date. Set annual fixed Roth conversion `5000` from 2027, stop rule Never. A horizon through 2046 includes the Primary's statutory RMD years. The integration test exercises this shape. Opening-RMD historical inputs are only needed if starting a plan in an applicable distribution year; they are not required for the age-62 opening used here.

## Deferred work and future New Plan design

- No wizard or dedicated creation page was built. The existing tabs remain the editor. The future creation experience can reuse `PersonInformationValidation`, the explicit membership operation, existing ownership validation, and the filing-status input semantics.
- Current validation remains distributed across domain value objects and the existing Apply editors. A future wizard should coordinate those validators, not infer composition from blank/dummy people. Domain draft Persons remain loadable before all editor-required fields are supplied.
- Single-person Monte Carlo remains Stage 4B; broad PDFs/CSV/export cleanup remains the reporting stage. No fake records were added to cross those boundaries.
- Existing configured death-scenario, same-year-AGI IRMAA, SS, RMD, Roth, pension, expense, mortality, seed and financial formulas were not changed.
- This review uses automated FX interaction and inspection of generated real JavaFX screenshots, not a claim of an unattended human click-through or screen-reader speech test. The steps above are provided for user acceptance.

## Final verification results

All Maven commands used IntelliJ's bundled Maven and
`-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.

| Verification | Result | Log |
|---|---|---|
| Initial corrected focused selection | 103 tests, 0 failures/errors, 0 skipped | `target/stage4a1-focused-final.log` |
| Preview run plus chart/comparison regressions | 19 tests, 0 failures/errors, 0 skipped | `target/stage4a1-previews.log` |
| Complete `mvn test`, including existing benchmarks | **1,725 tests, 0 failures/errors, 13 skipped**, BUILD SUCCESS, 5:20 | `target/stage4a1-full-final.log` |
| Final non-benchmark suite with previews enabled (`-Dtest=*Test,!*BenchmarkTest -Dsingle.stage4a1.preview=true`) | **1,704 tests, 0 failures/errors, 9 skipped**, BUILD SUCCESS, 2:06 | `target/stage4a1-final-nonbenchmark-preview.log` |
| Final focused rerun, including explicit couple selection in the existing Household callback fixture | **115 tests, 0 failures/errors, 0 skipped**, BUILD SUCCESS, 18.733 seconds | `target/stage4a1-final-focused.log` |

The final non-benchmark run includes the final label-sizing correction and regenerated previews. The nine added test methods increase the comparable baseline from 1,695 to 1,704. The complete run includes 21 additional benchmark cases, four of which are pre-existing opt-in skips. No test was newly disabled or skipped.

The nine non-benchmark skips remain the existing opt-in David claiming/Medicare audits (2), production-mortality audit (1), input-tooltip preview (1), comparison preview (1), Monte Carlo PDF preview (1), and fixed/longevity Monte Carlo previews (3). The new Stage 4A.1 preview ran explicitly in the final non-benchmark suite.

Both `SinglePersonStage3CoupleGoldenTest` and `SinglePersonStage4ACoupleGoldenTest` passed unchanged, as did `SinglePersonCoreProjectionTest`, the existing couple survivor/own-benefit tests, integrated rankings, heat-map, break-even, persistence, projection, income-summary and UI tests. Existing unrounded couple golden values remain unchanged.

Existing seed-417/random-stream/world-generation, inflation regression, mortality execution/distribution, paired-world and strategy-comparison tests all passed. There are no Stage 4A.1 generator, stochastic execution, reduction or expected-fingerprint edits. As additional evidence from the full run, the unchanged 5,000-world paired benchmark measured fixed 29.960 seconds and longevity 23.685 seconds (ratios 1.966 and 1.977 against single execution); cached reduction took 0.016985 and 0.013238 seconds. No single-person Monte Carlo was enabled.

`git diff --check` passed (exit 0). Git emits the existing Windows LF-to-CRLF normalization notices for some working files; there are no whitespace-error findings. `git diff --cached --name-only` is empty, and HEAD remains `00bbc51`.

## Visual review

All eight representative JavaFX PNG previews were opened and inspected, not merely generated. Final narrow and couple captures were inspected again after the label-width fix. Household bounds checks assert that visible managed controls fit the scene, and labels retain their full preferred text width. The narrow scene additionally asserts its actual 720 × 560 dimensions before capture.

| Preview under `target/single-stage4a1-preview/` | Size | Findings |
|---|---|---|
| `primary-only.png` | 1000 × 760 | Primary form and optional checkbox clear; no empty spouse pane; Apply/Cancel state correct. |
| `add-spouse-validation.png` | 1000 × 760 | Spouse form appears intentionally; missing birth date shown; focus moves to the required field; no clipping. |
| `couple.png` | 1000 × 760 | Both existing-style cards fit, labels and values readable, no layout regression. |
| `blocked-removal.png` | 1000 × 760 | Named financial dependency message visible; pending checkbox does not imply a completed removal. |
| `removed-spouse.png` | 1000 × 760 | Spouse pane gone after successful Apply; compact Primary-only form restored. |
| `primary-narrow.png` | 720 × 560 | All labels now fully visible, including Mortality category; buttons/status fit; no overlap. |
| `single-assumptions.png` | 1100 × 1000 | Explicit Single filing selection; no spouse/death/survivor controls; configured horizon wording and input help present. |
| `single-results-summary.png` | 1900 × 1040 | Real projected assets/estate, Primary-only SS marker/card, RMD/Roth periods, no spouse election rows; Export disabled; normal existing table/vertical scrolling retained. |

Native checkbox, date, combo and Apply/Cancel behavior is retained. New controls use the shared wrapped/delayed InputHelp mechanism; visible labels remain available, and validation remains inline with focus on the invalid input. No tooltip-only validation or new navigation system was introduced.

## Exact initial status

```text
 M src/main/java/com/daviddunn/retirementplanner/app/breakeven/BreakEvenContextFactory.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedIntegratedStrategyComparisonRequest.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedIntegratedStrategyRequest.java
 M src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityStrategyCalculator.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalysisRequestFactory.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedViewTest.java
?? SINGLE-PERSON-STAGE-4A-MORTALITY.md
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/SinglePersonMortalityAnalysis.java
?? src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/IndividualLongevityScenarios.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonMortalityView.java
?? src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/SinglePersonMortalityAnalysisTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/SinglePersonStage4ACoupleGoldenTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonMortalityViewTest.java
?? src/test/resources/single-person-stage4a-couple-golden.txt
```

## Stage 4A.1 file boundary

30 files: 20 production files, 9 test files, and this report. The prior 15 Stage 4A files remain separate and intact.

```text
 M src/main/java/com/daviddunn/retirementplanner/domain/factory/RetirementPlanFactory.java
 M src/main/java/com/daviddunn/retirementplanner/domain/model/RetirementPlan.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionReadiness.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/summary/IncomeSummary.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/summary/IncomeSummaryService.java
 M src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java
 M src/main/java/com/daviddunn/retirementplanner/ui/charts/ProjectionChartModel.java
 M src/main/java/com/daviddunn/retirementplanner/ui/components/PersonCard.java
 M src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java
 M src/main/java/com/daviddunn/retirementplanner/ui/dialogs/AccountDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/dialogs/PensionDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/rmd/OpeningRmdWorkflowService.java
 M src/main/java/com/daviddunn/retirementplanner/ui/summary/ProjectionYearDetailsPane.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/AccountsView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/DashboardView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/HouseholdView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/IncomeSourcesView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java
 M src/test/java/com/daviddunn/retirementplanner/ui/charts/ProjectionChartViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/controller/ApplicationControllerTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/controller/BreakEvenControllerTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloComparisonViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsMainWindowTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/HouseholdViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/RothConversionMainWindowTest.java
?? SINGLE-PERSON-STAGE-4A1-UI.md
?? src/main/java/com/daviddunn/retirementplanner/domain/model/PersonInformationValidation.java
?? src/test/java/com/daviddunn/retirementplanner/ui/views/SinglePersonHouseholdUiTest.java
```

## Exact final git status --short

```text
 M src/main/java/com/daviddunn/retirementplanner/app/breakeven/BreakEvenContextFactory.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedIntegratedStrategyComparisonRequest.java
 M src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedIntegratedStrategyRequest.java
 M src/main/java/com/daviddunn/retirementplanner/domain/factory/RetirementPlanFactory.java
 M src/main/java/com/daviddunn/retirementplanner/domain/model/RetirementPlan.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionReadiness.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/summary/IncomeSummary.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/summary/IncomeSummaryService.java
 M src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityStrategyCalculator.java
 M src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java
 M src/main/java/com/daviddunn/retirementplanner/ui/charts/ProjectionChartModel.java
 M src/main/java/com/daviddunn/retirementplanner/ui/components/PersonCard.java
 M src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java
 M src/main/java/com/daviddunn/retirementplanner/ui/dialogs/AccountDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/dialogs/PensionDialog.java
 M src/main/java/com/daviddunn/retirementplanner/ui/rmd/OpeningRmdWorkflowService.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalysisRequestFactory.java
 M src/main/java/com/daviddunn/retirementplanner/ui/summary/ProjectionYearDetailsPane.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/AccountsView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/DashboardView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/HouseholdView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/IncomeSourcesView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java
 M src/test/java/com/daviddunn/retirementplanner/ui/charts/ProjectionChartViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/controller/ApplicationControllerTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/controller/BreakEvenControllerTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloComparisonViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonIntegratedViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsMainWindowTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/HouseholdViewTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/views/RothConversionMainWindowTest.java
?? SINGLE-PERSON-STAGE-4A-MORTALITY.md
?? SINGLE-PERSON-STAGE-4A1-UI.md
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/SinglePersonMortalityAnalysis.java
?? src/main/java/com/daviddunn/retirementplanner/domain/model/PersonInformationValidation.java
?? src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/IndividualLongevityScenarios.java
?? src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonMortalityView.java
?? src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/SinglePersonMortalityAnalysisTest.java
?? src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/SinglePersonStage4ACoupleGoldenTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SinglePersonMortalityViewTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/views/SinglePersonHouseholdUiTest.java
?? src/test/resources/single-person-stage4a-couple-golden.txt
```

Total worktree boundary: 45 files including prior Stage 4A work. The index is empty. Nothing was staged, committed or pushed.
