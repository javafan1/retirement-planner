# Break-Even Insight — Phase 2B

## Projection data audit

| Requested value | Authoritative source / availability |
| --- | --- |
| Annual Social Security | `ProjectionYear.getSocialSecurityResult().householdBenefit()` |
| Cumulative Social Security | Existing `BreakEvenMetricResult` annual points; sums only actual shared calendar years |
| Portfolio / total withdrawals | `ProjectionYear.getPortfolioWithdrawal()`; engine supplies RMD + additional spending withdrawal + tax-funding withdrawal |
| Spending withdrawal | `HouseholdCashSettlement.spendingWithdrawal()` is the additional spending withdrawal after income/RMD funding, not total household consumption |
| Tax-funding withdrawal | `ProjectionYear.getTaxFundingWithdrawal()`; already included in portfolio withdrawals |
| Roth conversions | Requested/actual household and owner-specific getters; internal transfers, excluded from withdrawal observation |
| RMD | Annual requirement, distributed before projection, and distributed within projection are separate getters; do not add RMD to total withdrawals again |
| Federal / state tax | `getFederalIncomeTax()` / `getMichiganIncomeTax()` |
| Total income taxes | `getTotalIncomeTax()` adds those authoritative components; excludes Medicare and estimated heir taxes |
| Investment growth | `getInvestmentGrowth()`; includes retained non-qualified asset growth, so do not add that component again |
| Contributions / deposits | `getRetainedHouseholdSurplus()` and its excess-RMD / guaranteed-income attribution are available; not a general external-contribution ledger |
| Investable assets | `getEndingInvestableAssets()` |
| Non-investable assets | Separate `NonInvestableAssetProjection.getTotalValue()` by calendar year |
| Net worth | Already derived by existing Break-Even analysis from investable + non-investable assets; reused unchanged |
| After-tax estate | `getAfterTaxEstateValue()`; existing after-tax investable estate definition, unchanged |
| Cash flow / shortfall | `getCashFlowNeed()` and cash settlement; `getNetCashFlow()` is guaranteed income minus ordinary expenses, not a complete after-tax funding-shortfall measure |

The engine calculates gross portfolio withdrawals separately from Roth transfers. RMD distributions can subsequently be retained/redeposited. Accordingly, the panel explicitly calls these **gross portfolio withdrawals**, never consumption or net external spending. It does not claim they caused an asset gap.

## Implementation

`BreakEvenInsightService` reads the existing analysis result and the same cached projection years. It uses the existing sustained Social Security break-even year as its reference, falling back to comparison end when none exists. All metric snapshots reuse the existing annual comparison points. Gross withdrawals, income taxes and investment growth are summed inclusively through that reference year over the analyzer's actual shared-year list, with unrounded BigDecimal arithmetic.

No missing years are synthesized. If raw projections are unavailable, driver observations are omitted rather than zero-filled. The controller captures the immutable insight at the same time as the comparison result and only returns those prepared observations for that exact result object. Older/unrecognized results still support their own timing/metric snapshot but cannot accidentally receive newer projection drivers.

There are no changes to the analyzer, projection engine, plan persistence, mortality service, event detection or probability calculations. The new service has no projection-engine dependency.

## Presentation

The compact panel sits directly after the four summary cards, before Comparison Period. The headline follows the selected metric; Total Net Worth remains the default. Selecting cumulative Social Security compares SS timing with investable-asset timing. Other selections compare that metric with SS. Calendar-year gaps are presentation arithmetic on the supplied sustained years, not another crossover calculation.

The default panel contains the timing headline, an observed gross-withdrawal difference when available, and signed wealth differences at SS sustained break-even (or an explicitly labeled comparison-end snapshot). The snapshot wraps at narrow widths.

Collapsed **Show details** expands to:

- Baseline, Current and difference for cumulative SS and the three wealth balances at the reference year.
- Cumulative gross portfolio withdrawals, income taxes and investment growth through that year.
- Explicit shared-year, comparison-end, and non-causal interpretation notes.
- Existing household survival probability at SS break-even, when available, with the existing mortality tooltip.

No financial values are mortality-weighted. No percentage is manufactured when SS has no sustained break-even.

Already-ahead, identical, unreached and temporary-crossing states use the analyzer's classifications. Earlier first crossovers are distinguished from sustained recovery. Non-recovery wording describes the endpoint deficit, without incorrectly claiming the metric was below Baseline in every preceding year.

## Scenario limitation and exact wording

The workspace `plan.json` has no saved Baseline, so the exact user's 70/70-versus-70/62 comparison and dollar amounts cannot be independently reproduced here. No illustrative values are hard-coded.

Given the supplied reference results, the default Total Net Worth headline is:

> Total Net Worth reaches sustained break-even in 2048, 5 years after cumulative Social Security (2043).

Selecting Social Security or Investable Assets gives:

> Investable Assets reach sustained break-even in 2048, 5 years after cumulative Social Security (2043).

Selecting After-Tax Estate gives:

> After-Tax Estate reaches sustained break-even in 2047, 4 years after cumulative Social Security (2043).

The associated dollar values are read from the actual supplied comparison and are intentionally not invented in this report. Exact manual visual verification of the user's saved comparison remains pending access to that comparison.

## Deliberately omitted

- Causal attribution, counterfactual lost-growth estimates and additive allocation of the wealth gap. Supporting these would require a separately designed causal/counterfactual analysis; no small display formula can establish them.
- General external contributions and net portfolio consumption: the annual result does not expose a complete transaction-classified external-flow ledger. A future narrowly scoped enhancement could expose an authoritative annual external-flow summary, separately identifying distributions, redeposits, contributions and internal transfers.
- Automatic favorable/unfavorable interpretations of income tax differences.

## Files

New:
- `domain/breakeven/BreakEvenInsight.java`
- `domain/breakeven/BreakEvenInsightService.java`
- `ui/breakeven/BreakEvenInsightPresentation.java`
- `domain/breakeven/BreakEvenInsightServiceTest.java` (test tree)
- `ui/breakeven/BreakEvenInsightPresentationTest.java` (test tree)
- This report.

Modified:
- `ui/controller/ApplicationController.java`
- `ui/MainWindow.java`
- `ui/breakeven/BreakEvenAnalysisDialog.java`
- `ui/breakeven/BreakEvenAnalysisView.java`
- `ui/controller/BreakEvenControllerTest.java` (test tree)
- `ui/breakeven/BreakEvenAnalysisViewTest.java` (test tree)

Existing dirty files from earlier work were preserved. No commit was made.

## Validation

Focused tests: `BreakEven*Test,HouseholdLongevityScenarioFactoryTest,SocialSecurityMortalityDistributionProvider*Test` — 59 tests, zero failures/errors/skips.

Coverage includes before/same/after timing, no recovery in either direction, already-ahead/identical/temporary states, first vs sustained crossover, differing horizons and internal missing years, signed precise values, RMD/tax/Roth double-counting protection, taxes/growth totals, immutable observations, no projection calls/plan mutation, frozen observations after cache invalidation, unchanged Phase 1 results and Phase 2A context, panel ordering/disclosure/metric selection, SS-year snapshot and survival display, and responsive widths 1100/700/420.

Full non-benchmark suite: **1,358 tests, zero failures/errors, 3 skipped; BUILD SUCCESS**. See `target/break-even-insight-full.log`. The final focused run also passed all 59 tests; see `target/break-even-insight-focused.log`.
