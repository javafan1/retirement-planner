# Phase 6E-B3 — Arizona brokerage withdrawal admission correction

Date: 2026-11-16. Continues the existing Arizona implementation and preserves all preexisting uncommitted Phase 6E work. Arizona remains the 13th admitted production jurisdiction. No North Carolina work, staging, commits or pushes.

## Root cause and trace

The reported 2027 failure was an Arizona admission error, not evidence that brokerage proceeds were taxable gains or that federal income attribution was contradictory.

1. `IncomeSourceAttribution.withdrawal(...)` emits `ORDINARY_WITHDRAWAL` for ordinary brokerage receipts, with a positive `grossAmount`, source account type `BROKERAGE`, and **empty** `modeledTaxableAmount` for `TaxTreatment.TAXABLE`. Empty means unknown taxable portion; gross proceeds are not automatically income. Cash/Roth principal has a known zero taxable amount.
2. `ProjectionEngine` passes the annual withdrawal breakdown and supplemental sources into `TaxFundingCalculator.calculate(...)`.
3. Tax funding constructs `TaxIncome` using the modeled tax-deferred withdrawal, conversion, selected pension/SS and taxable investment-income totals. It does not convert gross brokerage proceeds into taxable income. `TaxIncomeAttribution.attach(...)` attaches the withdrawal evidence and tax year; ordinary brokerage metadata does not itself change AGI.
4. Trial and final tax funding create a `StateTaxCalculationRequest` containing those annual facts and the calculated federal result. `ProductionStateTaxService.calculate(...)` dispatches to `ArizonaProductionTaxCalculator.calculate(...)`.
5. The previous Arizona `ORDINARY_WITHDRAWAL` branch rejected `taxable.isEmpty()` (and positive known taxable portions), returning `UNSUPPORTED`/`MISSING_DISTRIBUTION_BASIS` with the exact brokerage-proceeds message. Thus a normal unknown-basis brokerage withdrawal was enough to fail, regardless of whether any gain was modeled federally.
6. `StateTaxUnavailableException.requireCalculated(...)` in tax funding propagates the unavailable state result, stopping the year's projection before tax funding can complete. The year appears in the user-facing message because it is attached to the annual request, not because 2027 law differs.

The former CAPITAL_GAIN branch also refused all explicit gains solely because Arizona-specific adjustment facts were missing. That policy is replaced with the approved disclosed no-subtraction approximation.

### Comparison with existing states

| State | Inspected current behavior; unchanged by B3 |
|---|---|
| Michigan | `MichiganProductionTaxAdapter.calculate(...)` delegates to `michigan.MichiganTaxCalculator.calculate(...)`, using `TaxIncome.getOrdinaryIncomeBeforeSocialSecurity()` and existing eligibility calculations. Unknown brokerage principal is not added to ordinary income and this source does not trigger Arizona's former guard. |
| Illinois | `IllinoisProductionTaxCalculator.calculate(...)` starts from federal AGI and examines retirement-distribution attribution for Illinois exclusions. Ordinary brokerage proceeds do not trigger a basis admission failure or become income. |
| Pennsylvania | `PennsylvaniaProductionTaxCalculator.calculate(...)` has a state-defined income/admission boundary; its ordinary-proceeds branch refuses missing gains/basis. Explicit investment categories require established taxable amounts. |
| Iowa | `iowa.IowaTaxBaseBridge.reconcile(...)` also refuses ordinary proceeds without gain/basis and has its own strict base/qualification rules. |

Only Arizona is changed. Pennsylvania and Iowa are not made to share Arizona's bounded admission policy, and no other-state numeric regression expectation was revised.

## Corrected bounded policy

* Keep the existing federal AGI reconciliation and every applicable Arizona deduction/exemption/rate calculation.
* Admit ordinary brokerage withdrawals with empty taxable metadata. Do not infer realized gains, add proceeds to AGI, or reject merely because security acquisition dates/basis are absent.
* Accept explicit capital-gain attribution without claiming an Arizona qualifying long-term-gain subtraction. If a taxable portion is known, it is checked against the existing modeled investment-income category. It is not added a second time to federal AGI.
* An empty gain taxable amount does not establish a new income amount. The federal model's already-calculated investment-income total remains in AGI unchanged; no actual gain is assumed to be legally zero.
* Known taxable ordinary-proceeds portions participate in the same investment-attribution reconciliation. Positive explicitly attributed amounts exceeding the modeled investment-income bucket remain unavailable. This does not create a new taxable-income channel or brokerage accounting model.
* Keep guards for wrong-year sources, inconsistent federal AGI/SS, excessive attributed category totals, unknown taxable retirement amounts, explicitly taxable nonqualified Roth receipts, unclassified OTHER income, special/exempt investment sources and invalid household/year/status context.

`TaxIncome.getTaxableInterestIncome()` is the existing modeled investment-income bucket used for these admitted caller-established investment classifications. No new gain/dividend fields or federal preferential-rate rules are introduced. Gross proceeds and known taxable amounts have different roles: the former is cash flow, the latter is evidence checked against income already modeled.

## Exact B3 changes

Paths are relative to `src/main/java/com/daviddunn/retirementplanner/` unless noted.

1. `domain/tax/state/ArizonaProductionTaxCalculator.java`: replaced the ordinary-proceeds and capital-gain blanket refusals with reconciliation of known taxable portions; unknown portions no longer block. Updated the shared planning-limitations paragraph. All tax-law parameters and adjustment arithmetic are unchanged.
2. `ui/views/AssumptionsView.java`: Arizona state-selection help explains modeled investment income, nonautomatic taxation of proceeds and the possible under/overestimation directions.
3. `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/ArizonaProductionTaxCalculatorTest.java`: replaced the superseded missing-basis rejection expectation with the approved policy; added independent combinations, represented gain/proceeds portions and contradiction/special-source checks. Existing financial goldens were retained.
4. `src/test/java/com/daviddunn/retirementplanner/domain/projection/ArizonaReportingAcceptanceTest.java`: checks the new warnings in PDF and verifies a restored saved result retains its original limitation text and tax values when exported to CSV.
5. New `src/test/java/com/daviddunn/retirementplanner/domain/projection/ArizonaBrokerageProjectionRegressionTest.java`: real 2027 BrokerageAccount withdrawals, prior Michigan success, subsequent Arizona success, principal/combined retirement-income cases, unchanged live balances and cash conservation.
6. New `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/ArizonaBrokerageMonteCarloRegressionTest.java`: two controlled economic worlds per scenario, matched against independent deterministic projections using identical context/path.
7. This deliverable, `PHASE-6E-B3-ARIZONA-BROKERAGE-ADMISSION.md`.

No B3 changes to federal tax, other state calculators, income attribution, account balances/withdrawal scheduling, RMDs, Roth execution, tax funding, projection engine, annuity admission/calculations, dependencies, persistence schemas or supported-state registration. Existing AGI, SS and senior phaseout are reused, not recalculated in the UI.

## Financial implications and limitations

For ordinary unknown-basis brokerage withdrawals, the previous outcome was an unavailable projection. The new outcome is a calculated planning estimate using exactly the income that the federal model includes. Brokerage spending still reduces projected assets through the existing withdrawal flow. It does not also create an invented tax-base addition.

Illustrative independent adapter fixtures for a Single filer under 65 in 2027, each also carrying $100,000 unknown-basis proceeds:

| Modeled income | Arizona estimated tax |
|---|---:|
| None | $0.00 |
| Traditional IRA $50,000 | $847.50 |
| Social Security $30,000 only | $0.00 |
| Taxable Roth conversion $20,000 only | $97.50 |

These are planning-model outcomes, not a conclusion that actual brokerage gains are tax exempt. For $50,000 IRA plus $5,000 explicitly modeled taxable investment income, estimated Arizona tax is $972.50, whether the source describes a gain or an assessed taxable portion of larger proceeds. No gross-proceeds addition and no unverified Arizona gain subtraction is made. Known $5,000 taxable attribution with **no** corresponding modeled investment-income total remains unsupported rather than silently ignored.

Actual taxes can be higher if realized gains are missing from the federal model. Conversely, omitting a legally available Arizona qualifying gain subtraction can overestimate state tax. This phase does not implement complete Arizona gain treatment, netting, loss carryovers, holding periods, security lots, acquisition dates, special-source bond adjustments or federal preferential capital-gain rates.

Tax funding itself is unchanged. An admitted state result allows its existing iterative withdrawal/tax feedback to finish. Consequently, a previously failing plan can now produce cash-flow and asset results; no additional deduction or tax funding path was added. Existing annuity/Advanced-mode NIIT source and growth safeguards remain unchanged and may independently refuse inputs outside their supported subset.

## User-visible and frozen disclosures

The shared Arizona limitations now state all four required points:

* Brokerage withdrawal proceeds are not automatically taxable income.
* The estimate uses modeled federal AGI and modeled taxable investment income.
* Unmodeled realized gains are not included and may cause actual taxes to be higher.
* Unverified Arizona-specific qualifying capital-gain subtractions are not applied, potentially overestimating tax when applicable.

The existing B2 plumbing carries these current-run notes into normal projection tooltips, shared analyzer assumptions, projection PDF/CSV, captured individual report context and Monte Carlo report metadata. The state-selector help is also updated. No new tax-input UI.

Previously saved results keep their captured limitation strings and numeric taxes. Exporters still read frozen annual/state or run-context metadata, rather than replacing historical disclosures with the current calculator's text. The retained-snapshot regression restores intentionally older disclosure text, verifies identical state taxes, and checks the CSV contains that saved text without the new warnings. No historical snapshot is rewritten or rerun by B3.

## Tests and results

Initial focused Arizona calculator run: **33 tests, zero failures/errors/skips, BUILD SUCCESS** (`target/phase6eb3-domain.log`).

Final focused acceptance/regression run: **417 tests, zero failures/errors/skips, BUILD SUCCESS**, 18.156 seconds (`target/phase6eb3-focused.log`). Includes all `Arizona*Test` classes, ProductionStateTaxServiceTest, StateTaxGoldenFixturesTest, Michigan tax calculator tests, Illinois/Pennsylvania/Iowa production calculators, StateTaxFundingFeedbackTest, NonqualifiedAnnuityFinancialTest and NoIncomeTaxAdmissionTest.

Coverage includes actual 2027 empty-taxable-metadata BrokerageAccount withdrawal reproduction; brokerage-only spending; independent IRA/SS/conversion combinations; combined RMD/SS/IRA/conversion spending; known gains/assessed taxable proceeds already included in the investment bucket; no invented gain/subtraction or principal double counting; contradictory attribution; specialized-income guards; UI/PDF/CSV and frozen metadata; tax funding and cash conservation; unchanged live balances; controlled deterministic/MC equality; existing state goldens and annuity financial regressions.

Per the requested focused test scope, no full unrestricted suite, long-running benchmarks, performance audits or exhaustive analyzers were launched. No existing state golden values were changed to make the tests pass. Git status was inspected before/after; `git diff --check` passed. No staging, commits or pushes.

## Native Windows acceptance

Use Java 25 and IntelliJ's bundled Maven. PowerShell:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-Dtest=Arizona*Test,ProductionStateTaxServiceTest,StateTaxGoldenFixturesTest,Michigan*Tax*Test,IllinoisProductionTaxCalculatorTest,PennsylvaniaProductionTaxCalculatorTest,IowaProductionTaxCalculatorTest,StateTaxFundingFeedbackTest,NonqualifiedAnnuityFinancialTest,NoIncomeTaxAdmissionTest' test
```

1. Restart the application with the new build. Open a disposable copy of the Michigan retirement plan that failed in 2027; record its existing balances, withdrawal settings and retirement elections.
2. Change only State of Residence to Arizona and recalculate through 2027. Ordinary brokerage proceeds with unknown basis should no longer produce the quoted unavailable-tax error. Do not add an invented gain to obtain a result.
3. Review AGI, state tax, brokerage withdrawals, tax-funding withdrawals and ending assets. Proceeds must not be added again to AGI. An estimate of zero does not claim actual realized gains are tax exempt.
4. Inspect Arizona help/result tooltips and PDF/CSV for both potential underestimation from omitted gains and overestimation from omitted qualifying Arizona gain subtractions.
5. Open/export a previously retained result. Its captured tax and limitations must remain unchanged. New calculations should show the updated policy notes.
6. Optionally use a small controlled MC request with the same plan assumptions. Existing Advanced/annuity unsupported-input guards still apply; B3 does not bypass them.

Automated checks include JavaFX/report paths; manual native desktop acceptance of the user's actual private plan was not performed. All prior uncommitted work remains in place. Stop here; North Carolina is not started.
