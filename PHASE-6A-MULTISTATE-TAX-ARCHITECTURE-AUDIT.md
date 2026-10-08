# Phase 6A — Multistate Retirement Tax Architecture Audit

Audit date: October 8, 2026. This is an audit/design deliverable only. Production
code, tests, calculations, JSON schemas and UI were not changed. Phase 6B is not
authorized by this report.

## 1. Executive summary

Federal and Michigan formulas are separate, but the live application has no
working interchangeable state-tax boundary. Tax funding, projected rules, annual
results and presentation all assume Michigan. An unused StateIncomeTaxCalculator
interface and unused TaxContext already exist, but neither powers projections.

The inventory contains **32 active file-level coupling/configuration touchpoints**
and **9 additional dormant/support locations**. Of the 41 entries, 33 contain
explicit Michigan references; eight are implicit configuration, reconstruction,
loading or presentation seams. This is a count of unique files, not every import,
getter, string or call site. An enum mentioning Michigan is inventoried as support,
not falsely counted as an active Michigan calculator.

The current income boundary is adequate only for a constrained ordinary-retirement
model: pensions, pooled pretax distributions, conversions and gross Social Security.
It cannot support comprehensive California taxes because investment income,
basis, Roth qualification, pension categories and residency sourcing are absent.
There are also existing legacy behaviors that a state-interface extraction must
preserve rather than silently correct.

**Recommendation:** reuse the existing state-interface package/name with an
immutable request/result contract, introduce a registry and shared annual tax
evaluation service, and wrap the existing Michigan calculation unchanged.
Route both ordinary funding and Roth bracket-fill through that service. Add a
bounded full-year-resident California calculator, then additive Michigan-defaulted
configuration and state-aware presentation. Defer investment/basis/residency
engines to separately approved work.

The smallest useful California MVP covers SINGLE/MFJ full-year residents with
ordinary fully taxable retirement distributions/pensions, equal zero federal/CA
retirement basis, qualified Roth treatment, Social Security exclusion, standard
deduction, personal/senior credits and limitations, progressive tax and the
high-income behavioral-health surcharge. It must clearly identify unsupported
situations. This is not a full California return or an accurate taxable-investment
model.

## 2. Current tax architecture

Paths throughout this report are repository-relative. Java paths expand under
src/main/java/com/daviddunn/retirementplanner/; class names use the corresponding
com.daviddunn.retirementplanner package. The inventory gives complete file paths.

### Live federal calculation

- domain/tax/FederalTaxCalculator.java, calculate(TaxIncome, domain.rules.FilingStatus,
  GovernmentRules): computes taxable Social Security, AGI, standard deduction,
  taxable income, then progressive ordinary federal tax.
- domain/tax/SocialSecurityTaxCalculator.java, calculateTaxableBenefits: ordinary
  income plus half the gross SS benefit drives the 50%/85% inclusion calculation.
  There is no tax-exempt-interest input in the provisional-income formula.
- domain/tax/FederalTaxableIncomeCalculator.java, calculateTaxableIncome: subtracts
  the configured standard deduction and floors at zero. No itemized deduction model.
- domain/tax/FederalIncomeTaxCalculator.java, calculateTax: loops ordered
  FederalTaxBracket lower/upper bounds and rates. No separate preferential capital
  gain or qualified-dividend calculation.
- domain/tax/FederalTaxCalculation.java: immutable AGI, taxable SS, standard
  deduction, taxable income and federal tax.
- domain/rules/FederalTaxRules.java, FederalTaxBracket.java and GovernmentRules.java
  hold the rules. FederalTaxRuleProjectionService.project indexes thresholds and
  deductions and applies the optional future federal marginal-rate adjustment.
  TaxParameterProjectionService.project compounds and rounds projected parameters
  to whole dollars; years at/before the published year reuse the published amount.

TaxCalculator.calculate(AGI, filingStatus, GovernmentRules) and TaxCalculation are
a simpler standalone federal path; they do not calculate SS inclusion themselves.
FederalTaxBracketCalculator and FederalTaxBracketLookupService are additional
helpers, not a multistate abstraction. No live state-calculator call is hidden in
the federal formula. Nonetheless GovernmentRules construction requires a Michigan
rules object, so the federal input container is not structurally state-independent.

domain/rules/FederalTaxTable.java is another immutable year/status/deduction/bracket
container, but no runtime consumer was found. The active GovernmentRules resource
uses FederalTaxRules instead; do not assume the unused table type provides the
application's rule-version selection.

### Live Michigan calculation and simplifications

MichiganTaxCalculator.calculate receives one amount called retirementIncome,
filing status and MichiganTaxRules. MichiganRetirementDeductionCalculator caps its
deduction at the SINGLE/MFJ limits. MichiganTaxableIncomeCalculator subtracts that
deduction, floors at zero, and MichiganIncomeTaxCalculator multiplies by the rate.

This is the application's existing simplified Michigan model, not evidence of
complete Michigan statutory coverage. There are no birth-cohort elections,
public/private pension qualification, personal exemption counts, dependent/blind
facts, separate credits, state basis or local-city tax calculation. Only SINGLE
and MFJ deduction cases are implemented, although the live filing-status enum
and federal tables expose additional statuses.

Active rules are in src/main/resources/rules/government-rules-2026.json:
rulesVersion 2026.1, 4.25% rate, 40,762 SINGLE and 81,524 MFJ retirement caps.
MichiganTaxRuleProjectionService.project indexes those caps with general
inflation and leaves the rate unchanged. MichiganTaxParametersUnUsed contains
different caps, 65,897/131,794, but has no runtime callers. Those values must never
be substituted during extraction merely because the unused class looks official.

### Existing interfaces and configuration

domain/tax/state/StateIncomeTaxCalculator.java currently declares only
calculate(TaxIncome). No implementation or live caller was found. Its
StateIncomeTax result carries taxableIncome, retirementDeduction and tax,
which is too Michigan-shaped and lacks jurisdiction/year/provenance.

domain/tax/TaxContext.java has a USState, tax year, ordinary taxable income,
qualified dividends, long-term gains and local rate, but no live callers.
It uses domain.model.FilingStatus, unlike the live domain.rules.FilingStatus.
A third enum exists in domain.tax.FilingStatus. Reusing the dormant context
as-is would introduce enum confusion and still omit AGI, persons, basis and
state adjustments. No unrelated enum cleanup is proposed for Phase 6B.

TaxAssumptions stores federal bracket/deduction growth, state/local percentage
fields, filing status, an heir-tax proxy and optional future federal rate changes.
There is no tax jurisdiction. State/local rates are persisted/displayed but have
no live tax-calculation consumer; a zero state-rate field does not mean no
Michigan tax.

## 3. Actual dependency diagrams

Solid arrows describe current runtime calls/inputs, not proposed interfaces.
Funding A and Funding B are two separate instances of the same class.

~~~mermaid
flowchart TD
  Plan["RetirementPlan / PlanningAssumptions"] --> Engine["ProjectionEngine.calculateProjectionYear"]
  Repo["GovernmentRulesRepository.load"] --> Rules["GovernmentRules: federal + Michigan + IRMAA + SS + RMD"]
  Rules --> ProjectRules["GovernmentRuleProjectionService.project"]
  Plan --> ProjectRules
  ProjectRules --> MIProject["MichiganTaxRuleProjectionService.project"]
  MIProject --> Params["TaxParameterProjectionService.project"]
  ProjectRules --> FProject["FederalTaxRuleProjectionService.project"]
  FProject --> Params
  ProjectRules --> Engine
  Engine --> Alloc["ProjectedWithdrawalAllocator"]
  Alloc --> Breakdown["WithdrawalBreakdown: CASH / TAXABLE / TAX_DEFERRED / ROTH"]
  Engine --> Fill["RothConversionBracketFillCalculator.calculateConversion"]
  Fill --> FB["TaxFundingCalculator B"]
  Engine --> FA["TaxFundingCalculator A"]
  Breakdown --> FA
  Breakdown --> FB
  FA --> Income["TaxIncomeCalculator.calculate"]
  FB --> Income
  Income --> TI["TaxIncome: pension / SS / pretax / conversion / interest"]
  TI --> Fed["FederalTaxCalculator.calculate"]
  Fed --> SS["SocialSecurityTaxCalculator"]
  Fed --> FTI["FederalTaxableIncomeCalculator"]
  Fed --> FT["FederalIncomeTaxCalculator"]
  TI -->|"ordinary total, no SS"| MI["MichiganTaxCalculator.calculate"]
  MI --> MID["MichiganRetirementDeductionCalculator"]
  MI --> MIT["MichiganTaxableIncomeCalculator"]
  MIT --> MID
  MI --> MIR["MichiganIncomeTaxCalculator"]
  Fed --> FundingResult["TaxFundingResult"]
  MI --> FundingResult
  FundingResult --> Engine
  Engine --> Settlement["HouseholdCashSettlement.calculate"]
  Engine --> Estate["AfterTaxEstateCalculator: configured heir-rate proxy"]
  Engine --> Year["ProjectionYear: federal + Michigan results"]
  Year --> Outputs["Results / details / charts / CSV / PDF / analysis metrics"]
~~~

~~~mermaid
flowchart LR
  Standard["ApplicationController / baseline service"] --> PE["ProjectionEngine"]
  Integrated["IntegratedSocialSecurityStrategyEvaluator"] --> PE
  Weighted["LongevityWeightedIntegratedStrategyEvaluator / LongevityContinuationSession"] --> PE
  SingleDet["SinglePersonIntegratedAnalysis.calculate"] --> Search["IntegratedSocialSecurityCompleteStrategySearchCalculator"]
  Search --> Integrated
  SingleLife["SinglePersonMortalityAnalysis integrated mode"] --> PE
  MC["MonteCarloAnalyzer: fixed + lifetime"] --> PE
  Paired["MonteCarloStrategyComparisonAnalyzer"] --> PE
  PE --> Tax["same TaxFundingCalculator + federal/Michigan services"]
  PE --> PY["ProjectionYear"]
  PY --> Metrics["ProjectionMetricsCalculator / comparisons / Results Summary"]
  Metrics --> PDFs["Report adapters / exporters"]
  PY --> BE["BreakEvenAnalyzer / BreakEvenInsightService"]
  SSOnly["SocialSecurityStrategyCalculator + mortality/PV analysis"] --> Gross["Gross SS results: no ProjectionEngine or income tax"]
  Dormant["StateIncomeTaxCalculator / TaxContext / CombinedTaxCalculator"] -. "not wired into runtime" .-> Tax
~~~

The dotted edge means absence of runtime wiring, not an indirect invocation.

## 4. Michigan coupling inventory

A = active calculation, configuration or output touchpoint.
D = dormant code, enum catalog or test-support builder. Counts deliberately
exclude test files themselves, which are listed separately.

| # | Status | Exact file path | Class/method or coupling |
| --- | --- | --- | --- |
| 1 | A | src/main/java/com/daviddunn/retirementplanner/domain/tax/state/michigan/MichiganTaxCalculator.java | calculate(retirementIncome, filingStatus, rules): concrete Michigan orchestration. |
| 2 | A | src/main/java/com/daviddunn/retirementplanner/domain/tax/state/michigan/MichiganRetirementDeductionCalculator.java | calculate / determineDeductionLimit: SINGLE or MFJ capped deduction; other statuses throw. |
| 3 | A | src/main/java/com/daviddunn/retirementplanner/domain/tax/state/michigan/MichiganTaxableIncomeCalculator.java | calculateTaxableIncome: subtracts retirement deduction from its ordinary-income argument. |
| 4 | A | src/main/java/com/daviddunn/retirementplanner/domain/tax/state/michigan/MichiganIncomeTaxCalculator.java | calculate: positive taxable income multiplied by Michigan flat rate. |
| 5 | A | src/main/java/com/daviddunn/retirementplanner/domain/tax/state/michigan/MichiganTaxCalculation.java | Record: retirementIncome, retirementDeduction, taxableIncome, incomeTax. |
| 6 | A | src/main/java/com/daviddunn/retirementplanner/domain/tax/state/michigan/MichiganTaxRuleProjectionService.java | project: indexes deduction caps with general inflation; holds rate constant. |
| 7 | A | src/main/java/com/daviddunn/retirementplanner/domain/rules/MichiganTaxRules.java | Constructor / getters: three persisted rule values; no cohort, exemption or credit model. |
| 8 | A | src/main/java/com/daviddunn/retirementplanner/domain/rules/GovernmentRules.java | @JsonCreator requires michiganTaxRules; getMichiganTaxRules / toString expose concrete type. |
| 9 | A | src/main/java/com/daviddunn/retirementplanner/domain/tax/GovernmentRuleProjectionService.java | Constructor / project: always creates/projects MichiganTaxRuleProjectionService. |
| 10 | A | src/main/java/com/daviddunn/retirementplanner/domain/tax/TaxFundingCalculator.java | Constructor and calculate overloads: creates MichiganTaxCalculator; calls it on every trial and final evaluation. |
| 11 | A | src/main/java/com/daviddunn/retirementplanner/domain/tax/TaxFundingResult.java | Constructor / getMichiganTaxCalculation / getTotalIncomeTax: Michigan-typed result and federal-plus-Michigan sum. |
| 12 | A | src/main/java/com/daviddunn/retirementplanner/domain/tax/TaxIncome.java | getMichiganRetirementIncome: Michigan-named aggregate; live funding instead passes ordinary income including interest. |
| 13 | A | src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java | Constructor / calculateProjectionYear: Michigan rule bundle, typed final result and ProjectionYear construction. |
| 14 | A | src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionYear.java | Constructors and getMichiganIncomeTax / getMichiganRetirementIncome / getMichiganRetirementDeduction / getMichiganTaxableIncome / getTotalIncomeTax. |
| 15 | A | src/main/java/com/daviddunn/retirementplanner/ui/help/PlanningInputHelp.java | STATE_RATE: explicitly says Michigan government rules override the stored rate field. |
| 16 | A | src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsView.java | createColumns: Michigan Tax column uses getMichiganIncomeTax. |
| 17 | A | src/main/java/com/daviddunn/retirementplanner/ui/summary/ProjectionYearDetailsPane.java | createMichiganTaxRows: Michigan retirement/deduction/taxable/tax headings and getters. |
| 18 | A | src/main/java/com/daviddunn/retirementplanner/ui/console/ConsoleReportPrinter.java | printProjection / printProjectionYear: Michigan Tax / Michigan Income Tax. |
| 19 | A | src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionCsvExporter.java | writeHeader / writeYear / writeIndividualHeader / writeIndividualYear: Michigan-named columns and getters. |
| 20 | A | src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonRunService.java | capture: frozen description defines lifetime modeled taxes as federal plus Michigan. |
| 21 | A | src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityPresentation.java | details: fixed/lifetime tax explanation names Michigan. |
| 22 | A | src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java | View construction/help text: lifetime taxes described as federal plus Michigan. |
| 23 | A | src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPdfReportAdapter.java | from: report explanation names Michigan; numeric values come from completed results. |
| 24 | A | src/main/resources/rules/government-rules-2026.json | michiganTaxRules: 0.0425 rate, 40762 SINGLE cap, 81524 MFJ cap; version 2026.1. |
| 25 | A | src/main/java/com/daviddunn/retirementplanner/domain/model/TaxAssumptions.java | Implicit configuration seam: no jurisdiction; stored state/local rates unused by live funding; MFJ fallback. |
| 26 | A | src/main/java/com/daviddunn/retirementplanner/domain/model/PlanningAssumptions.java | createDefaultTaxAssumptions: zero state/local rates despite live Michigan taxation; defaults and tax nesting. |
| 27 | A | src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java | Constructor / configureGuidedCreation / load / readDraft / toDraft: rate fields, no jurisdiction; rebuilds TaxAssumptions. |
| 28 | A | src/main/java/com/daviddunn/retirementplanner/persistence/GovernmentRulesRepository.java | load(resourcePath): generic loader deserializes Michigan-required GovernmentRules; no independent state rule catalog. |
| 29 | A | src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java | writeKeyAssumptions: displays stored state/local rates, not the actual Michigan rule rate/jurisdiction. |
| 30 | A | src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java | applyEconomicAssumptions: reconstructs TaxAssumptions; future state selection must survive this update. |
| 31 | A | src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualReportContext.java | capture: freezes stored state/local rate metadata for individual reporting. |
| 32 | A | src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAnalyzerInputSummary.java | from overloads, around lines 95–97: no named jurisdiction stored; displays unused configured rates. |
| 33 | D | src/main/java/com/daviddunn/retirementplanner/domain/tax/state/michigan/MichiganTaxParametersUnUsed.java | Dormant hard-coded 2026 / 0.0425 / 65897 / 131794 constants; differ from active JSON caps. |
| 34 | D | src/main/java/com/daviddunn/retirementplanner/domain/tax/CombinedTaxCalculator.java | calculate(federal, michigan): concrete Michigan sum; no live callers found. |
| 35 | D | src/main/java/com/daviddunn/retirementplanner/domain/tax/CombinedTaxCalculation.java | Michigan-typed record, only constructed by unused CombinedTaxCalculator. |
| 36 | D | src/main/java/com/daviddunn/retirementplanner/domain/tax/TotalTaxCalculation.java | Michigan-typed record; no live construction found. |
| 37 | D | src/main/java/com/daviddunn/retirementplanner/domain/rules/USState.java | MICHIGAN and CALIFORNIA enum entries; only referenced by dormant TaxContext, not a support registry. |
| 38 | D | src/main/java/com/daviddunn/retirementplanner/testutil/MichiganTaxRulesBuilder.java | aMichiganTaxRules / withIncomeTaxRate / withRetirementDeductionSingle / withRetirementDeductionMarried / build; 0.0425 default. |
| 39 | D | src/main/java/com/daviddunn/retirementplanner/testutil/MichiganTaxCalculationBuilder.java | aMichiganTaxCalculation / with... / build: concrete Michigan result fixture. |
| 40 | D | src/main/java/com/daviddunn/retirementplanner/testutil/GovernmentRulesBuilder.java | withMichiganTaxRules / build: constructs Michigan-required rule fixtures. |
| 41 | D | src/main/java/com/daviddunn/retirementplanner/testutil/ProjectionYearBuilder.java | withMichiganTaxCalculation / build: constructs Michigan-typed annual result fixtures. |

Explicit Michigan persisted fields occur in the **government-rule JSON**
michiganTaxRules and its three child properties. RetirementPlan JSON does not
store a jurisdiction or yearly Michigan tax results. It persists taxAssumptions,
and baseline snapshots contain the same PlanningAssumptions. ProjectionYear has
Michigan-named getters/fields and constructors, but annual projections are runtime
results rather than the saved RetirementPlan's tax schema.

The concrete return types in CombinedTaxCalculation/TotalTaxCalculation are
dormant; replacing them alone would accomplish nothing in the live engine.

### Tests with explicit Michigan references

These 15 files are the exact case-insensitive Michigan-text inventory under
src/test/java/com/daviddunn/retirementplanner/:

~~~text
domain/tax/state/michigan/MichiganTaxCalculatorTest.java
domain/tax/state/michigan/MichiganTaxableIncomeCalculatorTest.java
domain/tax/state/michigan/MichiganRetirementDeductionCalculatorTest.java
domain/tax/state/michigan/MichiganIncomeTaxCalculatorTest.java
domain/projection/ProjectionYearTest.java
domain/projection/ProjectionEngineTest.java
domain/projection/SocialSecurityColaSourceOfTruthTest.java
domain/projection/SinglePersonCoreProjectionTest.java
domain/baseline/ProjectionComparisonServiceTest.java
domain/breakeven/BreakEvenInsightServiceTest.java
integration/RetirementPlannerMvpIntegrationTest.java
app/socialsecurity/DavidClaimingAuditTest.java
app/montecarlo/MonteCarloFoundationTest.java
ui/summary/ProjectionYearDetailsOrganizationTest.java
ui/charts/ProjectionChartFixtures.java
~~~

This is not the complete regression surface: tests may depend on Michigan
indirectly through ProjectionEngine, controllers or analyzer fixtures. A lexical
search for the engine, funding, Michigan, Roth fill, integrated evaluator and
Monte Carlo evaluator names matched 86 *Test.java files. That is a reference
census, not an assertion that all 86 execute identical tax paths. Important
indirect safeguards include TaxFundingCalculatorTest, RothConversionBracketFillCalculatorTest,
SinglePersonStage3CoupleGoldenTest, SinglePersonStage4ACoupleGoldenTest,
HouseholdLifetimeProjectionTest, deterministic/weighted strategy tests, seeded
Monte Carlo suites, persistence and PDF/chart reconciliation tests.

## 5. Federal/state separation and tax funding

| Question | Verified answer |
| --- | --- |
| Federal tax independently callable? | Yes, mathematically/service-wise; its GovernmentRules input still requires Michigan metadata at construction. |
| Michigan consumes federal AGI? | No. |
| Michigan consumes federal taxable income or federal result? | No. |
| Michigan consumes classified income? | No: only TaxIncome.getOrdinaryIncomeBeforeSocialSecurity(), filing status and concrete rules. |
| Federal tax consumes state tax? | No; the current standard-deduction-only model has no SALT/itemized feedback. |
| State result independent? | MichiganTaxCalculation is separate, but its concrete type is embedded throughout funding and annual results. |
| Engine assumes Michigan? | Yes, through concrete result types, rule projection and both tax-funding constructions. |
| State tax affects cash funding? | Yes: federal plus Michigan liability drives tax withdrawals and cash settlement. |
| Extra withdrawals cause extra tax? | Yes for pretax funding; SS federal inclusion can also increase. |

TaxFundingCalculator.calculate's final overload computes a trial withdrawal
breakdown without mutating live accounts, adds it to existing withdrawals,
rebuilds TaxIncome, evaluates federal and Michigan tax, and updates:

~~~text
W_next = max(FederalTax(income including W) + StateTax(income including W)
             - availableHouseholdCash, 0)
~~~

Available cash comes from guaranteed income plus projection-period RMD less
spending/Medicare, floored at zero (ProjectionEngine around lines 605–611).
Opening RMD paid before the projection is taxable annual income, not new cash.
The loop starts at zero, allows 100 iterations and compares withdrawal changes
to 0.01. On convergence it reevaluates taxes using the final proposed withdrawal.
Non-convergence throws; its message still says Federal although Michigan is
included. Funding feasibility is checked by ProjectedWithdrawalAllocator.

State liability is not added as an ordinary Expense item. It is a separate
HouseholdCashSettlement obligation and a tax-funding withdrawal. Do not charge
it again in spending/reporting. Higher state liability may consume available
cash, cause additional pretax withdrawals, reduce retained assets and alter later
RMDs/estate outcomes.

**Convergence finding:** existing tests demonstrate Michigan behavior, not a
guarantee for an arbitrary state. For continuous monotone tax functions with
effective feedback slope below one, fixed-point contraction is plausible.
Progressive brackets alone do not justify changing the solver. Credits, annual
rounding/tax-table steps, exceptions, allocation transitions and future policy
changes require explicit residual/funding tests. The solver does not certify its
final tax-minus-cash residual after reevaluation and has no general proof or
state-specific stress coverage.

There is also a separate Medicare/IRMAA rerun:
ProjectionEngine.calculateProjectionYear around lines 778–805 recalculates a year
when the modeled premium differs from the prior trial. This recursive outer
mechanism has no explicit iteration cap comparable to TaxFundingCalculator's
100. State-funded pretax withdrawals can change federal AGI and move IRMAA bands.
Test the combined loop, not just a calculator's rate math.

For a future state that genuinely fails stress tests, recommend a separately
reviewed cent-safe bracketed funding fallback and structured non-convergence
diagnostics, with the existing Michigan path/results preserved. Do not silently
change the solver in the extraction phase.

Future itemized federal SALT modeling would create another interdependence.
It is not present now and is outside the California MVP.

## 6. Income classification audit

| Income/flow | Current representation | At state boundary? |
| --- | --- | --- |
| Traditional/rollover/inherited IRA withdrawals | Account type/owner known in projected portfolio | Only pooled TAX_DEFERRED amount; gross assumed fully taxable |
| 401(k)/403(b) withdrawals | Distinct account classes/types | Merged with IRA amounts |
| RMDs | AccountRmd/OwnerRmdResult/HouseholdRmdResult; before/during amounts distinct earlier | Projection-period RMD merged with spending withdrawals; opening pre-projection amount added into pretax total |
| Roth conversions | ProjectedRothConversionResult allocations preserve source/destination/owner upstream | Household executed total only |
| Qualified Roth distributions | ROTH withdrawal bucket | Excluded from TaxIncome; no qualification evidence |
| Nonqualified Roth distributions | Same ROTH bucket | No taxable earnings/contribution ordering, age/holding-period or penalty inputs |
| Pension income | Person-owned Pension, monthly/annual benefit and survivor amount | Aggregate pension amount; no basis/public/military/railroad category |
| Social Security/survivor SS | HouseholdSocialSecurityResult retains owner/selected benefits upstream | Gross household benefit in TaxIncome; federal taxable portion in FederalTaxCalculation |
| Brokerage/bank interest | TaxIncome has a taxableInterestIncome argument | ProjectionEngine passes BigDecimal.ZERO to funding and bracket fill |
| Ordinary/qualified dividends | No active source/yield classification | Absent; unused TaxContext fields do not generate income |
| Short-/long-term gains | Portfolio growth and taxable withdrawal amounts exist | No realized gain, cost basis, loss/carryover or holding period |
| Other supported income | Persisted IncomeSource subtypes are Pension and SocialSecurityIncome only | No generic wage/rental/business/annuity-income category |
| Tax-exempt interest | No active income field/source | Absent, including federal SS provisional-income adjustments |

Exact early aggregation points:

1. ProjectedWithdrawalAllocator.calculateWithdrawalBreakdown(beginning, ending)
   and its public trial-withdrawal overload switch only on TaxTreatment. Owner,
   specific account type, RMD purpose and basis are lost in WithdrawalBreakdown.
2. ProjectionEngine combines RMD/spending breakdowns, passes the opening RMD total
   and getTotalConversion() to TaxFundingCalculator, and passes zero interest.
3. TaxIncomeCalculator.calculate loops Persons into aggregate pension/SS totals
   and adds opening pretax distributions to taxDeferredWithdrawals.
4. TaxFundingCalculator reduces that five-part TaxIncome again to one ordinary
   amount at MichiganTaxCalculator.calculate.
5. Investment growth is calculated across total projected assets and applied to
   accounts/retained assets. Appreciation is not classified as interest, dividends
   or realized gains; taxable account withdrawals are not a gains calculation.

### Smallest safe preservation change to recommend, not implement

First capture an immutable TaxIncomeSnapshot of the existing amounts and frozen
filer facts; keep the Michigan adapter's old aggregate input exactly.
Where classification is needed, retain allocation events alongside existing
totals in ProjectedWithdrawalAllocation/WithdrawalBreakdown plumbing:

~~~text
TaxDistribution: stable account identity, owner, AccountType, purpose
                 (RMD/SPENDING/TAX_FUNDING/CONVERSION), gross amount, period
~~~

Reuse the same allocator trial/execution logic. Preserve opening RMD events and
conversion allocations before their totals are taken, and recipient-specific
pension/SS components before aggregation. The event ledger is projection-run-only;
it must not mutate account balances twice or invent persisted wizard records.
Use a run-local portfolio/source key for identity; existing Account has no persisted
account UUID, and adding one is not necessary merely to preserve tax classifications.

This recovers known metadata, not missing economics. Accurate realized gains,
IRA/state basis recovery, qualified/nonqualified Roth distributions and municipal
income still need separately approved domain inputs and projection logic.
Never call every taxable withdrawal a gain or every portfolio growth dollar
taxable interest.

### Existing pension/death inconsistency requiring an approval decision

ProjectionEngine supplies authoritativePensionIncome only when
evaluationContext.householdLifetimeScenario() is present (around lines 472–475).
Configured-death cash income instead filters dead owners in calculateIncome and
adds calculateSurvivorPensionIncome. TaxIncomeCalculator, when that optional is
empty, loops all household members' Pension.getAnnualIncome without applying the
death view; deathAssumptions there affect the SS fallback, not pension taxation.

Thus a configured-death tax request can contain original deceased-person pension
amounts rather than the actual survivor cash-flow total. Lifetime runs use
domain/income/HouseholdPensionIncomeCalculator.calculate's authoritative total.
This is a source-traced discrepancy, not a newly asserted numerical golden result.

Do not harmonize these paths silently. Phase 6B must preserve them. Before releasing
California survivor integration, approve a focused characterization/correction
decision, or limit the initial California admission envelope to supported scenarios
and clearly defer affected cases. A correct state policy cannot repair a wrong
income input merely by changing brackets.

## 7. California requirements and authoritative research

FTB sources were accessed October 8, 2026. Finished 2025 return instructions/tables
are the initial reproducible reference. This section distinguishes law/data from
recommended model scope; sources are official FTB documents rather than third-party
tax summaries.

### Essential rules and supported-envelope decisions

| Rule | California requirement / architectural implication | MVP category |
| --- | --- | --- |
| Progressive rates | Nine regular rates: 1%, 2%, 4%, 6%, 8%, 9.3%, 10.3%, 11.3%, 12.3%; filing-status tables, not a flat federal-taxable-income percentage | A |
| State taxable base | Start with federal AGI, state additions/subtractions, then California deductions; independently calculate California taxable income | A |
| Standard deduction | 2025 SINGLE/MFS 5,706; MFJ/HOH/qualifying survivor 11,412; never reuse federal deduction | A within supported statuses |
| Personal/senior credits | 2025 personal credit 153 per qualifying person, MFJ personal total 306; senior credit eligibility is separate | A |
| Credit limitations | Federal-AGI-based exemption-credit limitation must be applied, even when SS is excluded from California taxable income | A |
| Ordinary retirement income | Ordinary taxable IRA/employer-plan distributions and pensions included; taxable conversions included subject to basis differences | A with zero/equal basis |
| Social Security | Exclude U.S. SS, including survivor benefits; equivalent Tier 1 railroad benefits excluded too | A for supported SS; railroad category deferred |
| Capital gains | Short/long gains taxed as ordinary California income, with California basis adjustments where applicable | D in current projection; not safely inferable from withdrawals |
| Interest/dividends | Taxable interest and ordinary/qualified dividends generally included; no California preferential qualified-dividend rate | D until investment-income inputs exist |
| Municipal/U.S. obligation interest | California versus out-of-state municipal income and qualifying U.S. obligation interest need separate classifications | B/D |
| High-income extra tax | 1% of taxable income above 1,000,000: Behavioral Health Services Tax, formerly Mental Health Services Tax | A; not a blanket 1% addition |
| Full-year residency | Resident return covers taxable worldwide income | A; initial single state for entire modeled year |
| Special situations | Military pension, basis differences, early distributions, AMT, dependents/blindness/itemizing, RDP/separate returns, residency changes | B/C/D as below |

A = essential within the initial supported retirement envelope.
B = additional user facts needed.
C = advanced feature that may be deferred with an explicit scope limitation.
D = current model cannot calculate accurately, regardless of calculator plumbing.

The rate schedules also supply the published MFJ example: California taxable
income 125,000 gives regular tax 4,768.10 before whole-dollar return rounding to
4,768, before exemption credits/additional taxes. This is a regular-tax fixture,
not a total-return golden. [2025 California rate schedules](https://www.ftb.ca.gov/forms/2025/2025-540-tax-rate-schedules.pdf)

Form 540 directs tax-table lookup at taxable income of 100,000 or less, and rate
schedules above that amount. The 2025 exemption limitation begins at federal AGI
252,203 SINGLE/MFS or 504,411 MFJ/qualifying survivor (HOH 378,310), with prescribed
ceilings/increments. Senior eligibility includes the January 1 birthday convention.
Do not conflate credit-eligibility counts with merely alive account owners.
[2025 Form 540 instructions](https://www.ftb.ca.gov/forms/2025/2025-540-instructions.html),
[2025 Form 540 credit fields](https://www.ftb.ca.gov/forms/2025/2025-540.pdf)

California Schedule CA's SS subtraction removes the federally taxable SS amount,
not the whole gross benefit a second time. Its capital-gain treatment requires
ordinary California rates and adjustments when federal/state basis differs.
Registered domestic partnerships can require different federal/state filing
treatment; the existing spouse property does not establish these legal facts.
[2025 Schedule CA instructions, revised April 2026](https://www.ftb.ca.gov/forms/2025/2025-540-ca-instructions.html)

Qualifying Roth distributions are exempt; conversions and IRA distributions can
differ when California basis differs from federal basis. Historical California
basis recovery is a multiyear ledger, not a permanent tax percentage or one
arbitrary adjustment. [2025 FTB Publication 1005](https://www.ftb.ca.gov/forms/2025/2025-1005-publication.pdf)

Taxable dividends have no special California qualified-dividend rate.
[FTB 1099 guidance](https://www.ftb.ca.gov/file/personal/income-types/information-returns-1099.html)
Qualifying California municipal and U.S.-obligation income may be exempt while
other-state municipal income is taxable; fund pass-through qualification needs
additional detail. [2025 FTB Publication 1001](https://www.ftb.ca.gov/forms/2025/2025-1001-publication.pdf)

Early distributions can incur a 2.5% additional tax; SIMPLE early-period rules
can use 6%, with applicable exceptions. Contribution/basis/qualification and
exception facts are absent from the current model.
[2025 Form 3805P instructions](https://www.ftb.ca.gov/forms/2025/2025-3805p-instructions.html)
California AMT and credit ordering need Schedule P; ordinary income alone is
not evidence that every AMT situation is irrelevant.
[2025 Schedule P instructions](https://www.ftb.ca.gov/forms/2025/2025-540-p-instructions.html)

Military retirement/SBP has a limited exclusion up to 20,000 for 2025–2029,
subject to federal-AGI limits of 125,000 individual / 250,000 joint or qualifying
survivor. Pension currently lacks the category/eligibility facts. Deferral must
exclude this situation from the supported ordinary-pension claim, rather than
silently taxing it as if no special rule existed.
[FTB military guidance](https://www.ftb.ca.gov/file/personal/filing-situations/military.html)

### 2026 availability and data-quality finding

FTB's October 2026 news announces 3.4% indexing, SINGLE/MFS deduction 5,900,
MFJ/HOH/qualifying-survivor deduction 11,800, personal credit 158 and the 2026
rate schedules. It also says complete annual rates/exemption information will
be available in late December. Its MFJ 8% row has a boundary of 118,996 but prints
118,966 as the subtraction base. This internal inconsistency must be resolved
against final FTB tables before treating that row as a golden fixture.
[FTB October 2026 Tax News, 2026 Indexing](https://www.ftb.ca.gov/about-ftb/newsroom/tax-news/)

The already-published 2026 estimated-tax worksheet explicitly uses 2025 tables
and exemption figures; it is not a complete enacted 2026 return table.
[2026 Form 540-ES instructions](https://www.ftb.ca.gov/forms/2026/2026-540-es-instructions.pdf)

Recommendation: validate a complete 2025 bundle first, then independently verify
the 2026 bundle as publication permits. A 2025-rule extrapolation used for a 2026
projection must be labeled projected, not published 2026 law. Do not silently
repair a source typo or substitute inflation-generated tables for enacted data.

### Smallest useful California MVP

Support ordinary SINGLE and legally married MFJ full-year resident returns with
standard deduction, no dependents/blindness/itemization/business/AMT preference
facts, equal zero retirement basis and qualified Roth distributions. Include
personal/senior credits, credit phaseouts, SS exclusion, progressive regular tax
and behavioral-health surcharge. Use published tables/rounding for exact annual
fixtures; if a smooth bracket-only projection policy is chosen below 100,000,
name it as an approximation and test/document the difference. Prefer exact
published-table behavior for the first tax implementation.

Initially admit only the income classifications the engine genuinely supplies.
The clearest accurately bounded portfolio is pretax retirement plus confirmed
qualified Roth, without modeled taxable investment receipts or special pension
categories. Brokerage/cash balances alone do not establish that taxable earnings
are zero. A broader legacy investment plan can be projected only with an explicit
simplified-income limitation; do not advertise it as complete California taxation.

Basis, military status, dependents, blindness, itemized deductions and retirement
qualification are extra inputs; current data cannot prove their absence.
The future UI/configuration must explain the supported envelope rather than
manufacture favorable exemptions. Survivor integration also requires the pension
tax-input decision in section 6. Refundable credits and estimated-payment/penalty
cash timing are outside this nonnegative annual-liability MVP.

## 8. Recommended immutable state interface

Reuse domain.tax.state.StateIncomeTaxCalculator's package/name rather than adding
a competing unused abstraction. Its unused signature can evolve in approved 6B.
Retain a Michigan adapter around existing MichiganTaxCalculator; do not rewrite
its financial formula. Keep FederalTaxCalculator and existing federal result.

Illustrative design only; none of these declarations were implemented:

~~~java
public interface StateIncomeTaxCalculator {
    StateTaxResult calculate(StateTaxRequest request, StateTaxRules rules);
}

public record StateTaxRequest(
        int taxYear,
        FilingStatus filingStatus,          // domain.rules.FilingStatus
        TaxHouseholdFacts household,        // immutable return participants
        TaxIncomeSnapshot income,           // existing totals; later typed flows
        FederalTaxCalculation federal,
        StateTaxOptions options) { }

public record StateTaxResult(
        USState state,
        int taxYear,
        TaxRuleProvenance provenance,
        BigDecimal adjustedGrossIncome,
        BigDecimal taxableIncome,
        BigDecimal regularTax,
        BigDecimal creditsApplied,
        BigDecimal additionalTaxes,
        BigDecimal totalLiability,
        List<StateTaxLine> details,
        List<TaxModelLimitation> limitations) { }
~~~

These conceptual records require constructor validation and List.copyOf. Future
classified-income validation must permit signed adjustments and net gains/losses
where appropriate; do not impose TaxIncome's nonnegative-only constraints on all
future categories. The ordinary-retirement MVP does not add a loss ledger.

Income and person facts must be snapshots, not references to mutable Person/Account/
Household/RetirementPlan or JavaFX controls. Return participants, DOBs, modeled
death dates and recipient ownership belong in TaxHouseholdFacts. Filing status is
resolved once using existing run conventions. State legal age/credit rules can
inspect immutable dates without sharing mortality/UI objects.

The existing federal result supplies AGI and taxable SS required for California
adjustments/credit limitations. It is not California taxable income. The typed
income snapshot allows exemptions/source adjustments; aggregate AGI alone cannot
support future states. StateTaxRules/StateTaxOptions must be typed validated data,
not arbitrary mutable maps or callbacks that inspect a plan.

A registry binds USState to calculator/rule provider; calculator implementations
need not select their own jurisdiction. A narrow AnnualTaxCalculationService
coordinates the existing federal evaluation and the chosen state policy, returning
federal/state results and total annual liability. Pass that same stateless service
or immutable projection tax session to both TaxFundingCalculator instances,
including the one inside RothConversionBracketFillCalculator.

Michigan adapter behavior must retain the exact old ordinary-income argument
and projected deduction/rate, even where naming/classification is misleading.
Michigan-specific detail lines can expose retirementIncome/retirementDeduction.
California must not be required to invent Michigan retirement fields.

Evolve TaxFundingResult and ProjectionYear to carry state-neutral results.
Michigan constructors/getters can remain compatibility APIs for Michigan results,
but must not silently label California tax Michigan. Move consumers to state
getters and state-specific optional detail rendering. Generalize only live seams;
do not make an unused CombinedTaxCalculator the new orchestration center.

Alternatives:
- Merely add a switch on state inside ProjectionEngine: smallest immediate patch,
  but repeats changes for each state, misses Roth funding and leaves result/report
  coupling. Reject.
- Replace GovernmentRules with a complete state-map/tax DSL now: broad rule-format
  migration and unnecessary engine/persistence risk. Defer.
- Recommended hybrid: keep legacy GovernmentRules readable and Michigan data
  unchanged; add an offline state-rule provider/registry behind a narrow tax session.
  Future states add implementations/data/registration without engine branches.

## 9. State configuration and UI design recommendation

Place an immutable StateTaxConfiguration inside TaxAssumptions, already nested
in PlanningAssumptions. Reuse domain.rules.USState identifiers. An enum containing
all states does not mean those states are implemented: UI options come from the
supported calculator/rules registry. Start with Michigan/California only.

Initial configuration can hold the state and narrowly required state-specific
options/support declarations. Avoid one enormous object containing every state's
rules, and do not require a separate subclass for a state needing no user options.
Published laws/tables live in rule data, not editable plan settings. Future growth
assumptions should be explicit and separate from known law.

New-plan default remains Michigan. AssumptionsView would add Tax State beside
filing/tax settings, retain direct control tooltips and required text, and show
rule year/model limitations where useful. The wizard's compact Assumptions and
Review should show the same selection and facts. No screen was changed here.

Keep legacy stateIncomeTaxRate/localIncomeTaxRate fields readable/round-trippable
during migration; they cannot become silent California overrides. Their present
help already says the Michigan rate is government-rule-driven. Deprecate or
clearly identify inactive rate inputs in a separately approved UI stage.

All three live TaxAssumptions construction sites must preserve state configuration:
PlanningAssumptions.createDefaultTaxAssumptions, AssumptionsView.readDraft and
ResultsSummaryView.applyEconomicAssumptions. Otherwise editing return/inflation
or a federal rate assumption can accidentally reset California to Michigan.

## 10. Persistence compatibility

Recommended additive field: planningAssumptions.taxAssumptions.stateTaxConfiguration,
containing a stable state identifier and any supported options. This is design,
not a schema change made in 6A.

Missing configuration, including in old baseline snapshots, means Michigan.
An explicitly unsupported/unknown state must fail with an actionable unsupported
jurisdiction error; it must never silently become Michigan. No-tax states require
a registered no-income-tax policy rather than an unknown-state zero result.
Defaults must not depend on a user's address, current UI or tax year.

JsonRetirementPlanRepository.save/load use constructor-based Jackson and validate
household references. RetirementPlanScenarioCopyService.copy round-trips complete
JSON for integrated and Monte Carlo isolation, so new state fields must survive
that copy and every reconstruction. BaselineProjectionService.projectBaseline
rebuilds a plan from RetirementPlanSnapshot, including PlanningAssumptions.

RetirementPlanSnapshot currently holds references to mutable household/accounts.
Do not assume baseline isolation, or fix that unrelated architecture in this work.
Immutable state configuration helps preserve its own value semantics but does
not make the entire old snapshot a deep copy.

Backward compatibility means new software reads old plans as Michigan with
unchanged results. It does not promise old executables can safely understand a
new California plan; unknown-field behavior/version handling must be explicit.
No wizard-only or projection-ledger properties should leak into plan JSON.

## 11. Single-person, couple and survivor context

ProjectionEngine.getProjectionFilingStatus preserves configured filing status
for a single person, configured status through the couple's death year, and
SINGLE in subsequent years. It does not implement automatic qualifying-survivor
eligibility. The factory's TaxAssumptions fallback is MFJ independently of
household membership; the UI may expose statuses Michigan cannot calculate.

Account/Person ownership and conversion allocations are available upstream.
Joint taxable accounts are household accounts, not independently taxed spouses.
The state boundary currently has neither person facts nor a separate-return/
community-property model. California MFJ can often use household totals in the
restricted envelope; MFS/HOH/QSS/RDP need legal/return facts not established merely
by having one or two Person records.

Freeze actual return participants separately from alive/distribution-eligible
owners. A death-year joint return and senior-credit eligibility need review of
deceased-person rules; do not automatically count all stored Persons or remove
every deceased spouse from every credit. Resolve legal age conventions using
DOB/date facts and year-specific rules. Preserve the engine's January 1 modeled
death convention, and test rather than silently redesign it.

Configured and lifetime paths intentionally differ in some owner/RMD eligibility
and pension plumbing. The pension tax discrepancy in section 6 must be resolved
by an explicit decision before claiming fully correct California survivor results.
SS survivor income already comes from the same authoritative household result
used for cash flow and federal tax; California excludes the supported SS benefit.

## 12. Roth conversions and optimization implications

The live path is ScheduledRothConversionPolicy → ProjectionEngine conversion
strategy branch → RothConversionTargetBracketResolver → (for bracket filling)
RothConversionBracketFillCalculator.calculateConversion → TaxFundingCalculator.

Targets are **federal** 12%/22%/24% bracket boundaries or a custom federal taxable
income target. Fixed conversions use the configured amount. The fill calculator
iterates to meet final federal taxable income after tax-funding withdrawals; it
uses a secant-style adjustment after sign changes, a 0.01 tolerance and 100 limit.
It does not optimize a combined marginal rate or search for globally optimal
Roth amounts.

ProjectedPortfolioRothConverter.convertHousehold executes same-owner transfers,
and ProjectedRothConversionResult preserves owner allocation before the engine
takes its household total. Final taxes use actual conversion execution/capacity,
not an unexecuted requested conversion. Preserve all these safeguards.

State effects:
- Higher tax can require more pretax tax-funding withdrawals and reduce federal
  bracket room; if paid from existing cash/Roth/taxable principal, that particular
  federal-room effect may be absent.
- Fixed conversion elections do not automatically become optimal or change their
  named strategy, but higher funding costs/capacity failures can change outcomes.
- Ending pretax/Roth balances, later RMDs and after-tax estate values can change.
- AfterTaxEstateCalculator uses a separately configured heir-tax rate on ending
  pretax balances. It is not recalculated from Michigan/California marginal rates.
  State selection affects the annual asset path, not this proxy's rate itself.
- Integrated SS rankings can change because full-plan estate/tax metrics change;
  SS-only mortality/PV rankings are gross-benefit calculations and remain untaxed.

Both tax-funding instances must receive identical state selection, rules and
classified income. Do not add California only to the final engine tax call.
Retain named federal bracket targets; do not relabel them combined targets.
A future combined-tax conversion comparison should quote incremental total tax,
funding, SS inclusion, credits and Medicare consequences through the same annual
evaluation service. Adding nominal marginal federal/state rates is insufficient
near phaseouts or funding transitions. Any objective change needs its own approval.

domain/withdrawal/RothConversionPlanner.plan and
domain/roth/RothConversionBracketFillStrategy.calculateConversion are simpler
legacy/standalone paths with no live engine call; do not accidentally route the
new state-aware engine through their one-pass bracket-room logic.

## 13. Projection, analyzers, Monte Carlo and reporting

| Path | Actual tax route / exception |
| --- | --- |
| Standard deterministic | ApplicationController → ProjectionEngine.project/projectWithOutcome → live funding services |
| SS Only analyzer | SocialSecurityStrategyCalculator and mortality/PV services; no projection or income-tax computation, intentionally gross |
| Integrated deterministic quick/grid/exhaustive | IntegratedSocialSecurityStrategyEvaluator's private project constructs ProjectionEngine; comparisons/search use its results |
| Longevity weighted | LongevityWeightedIntegratedStrategyEvaluator.evaluate and LongevityContinuationSession project isolated plans through ProjectionEngine with exact lifetime context |
| Single-person integrated/weighted | SinglePersonIntegratedAnalysis and SinglePersonMortalityAnalysis integrated mode use the same engine; SS-only mode remains gross |
| Fixed/longevity Monte Carlo | MonteCarloAnalyzer.analyze/analyzeMortality → ProjectionEngine.projectWithOutcome with economic/lifetime contexts |
| Paired Monte Carlo | MonteCarloStrategyComparisonAnalyzer.analyzeComparison/execute → same engine for each frozen strategy/world |
| Opening-date terminal cases | Opening-estate calculations; intentionally zero yearly projections/taxes, not a Michigan bypass to be patched with fictitious tax years |
| Baselines | BaselineProjectionService.projectBaseline uses the same engine; compares baseline's own stored assumptions |
| Break-even | BreakEvenAnalyzer.analyze / BreakEvenInsightService observe supplied ProjectionYear results; no tax recalculation |
| Results Summary | ProjectionMetricsCalculator.calculate aggregates getTotalIncomeTax; ResultsSummaryView displays/rebuilds assumptions, not a separate tax engine |
| Charts/PDF/CSV | ProjectionChartModel total-tax series, report adapters and exporters render completed projections/metrics; no new tax calculations |

No alternative California-ready tax engine was found in analyzers. The duplicate
tax work that matters is repeated funding evaluation and the separate Roth
funding instance, not a second tax algorithm inside Monte Carlo or PDFs.

IndividualReportContext.capture, SocialSecurityAnalyzerInputSummary, integrated
report contexts, MonteCarloPdfReportAdapter.from and ProjectionPdfExporter must
freeze/display the chosen jurisdiction, rule provenance and model scope.
The current Michigan text and inactive rate metadata need consistent replacement;
never combine result-time California numbers with current-time Michigan labels.

State selection must invalidate projection/analysis caches via the existing
plan-changed/source-revision workflow. JSON copies, frozen requests, candidate
baselines and paired worlds must retain the correct state configuration.
Equivalence/continuation reuse is only safe within matching complete tax context;
tax parameters/configuration belong in compatibility checks if reuse later spans
different plans or states.

## 14. Future residency changes

Recommend **A: one state for all projection years initially**, behind a
StateTaxConfigurationResolver.resolve(config, year) seam. It initially returns a
constant. This gives a future year-selection hook without premature timeline JSON,
partial-year records or multiple-return solvers.

A timeline-compatible persisted single period (option B) would reduce a later
schema change but introduce dates/overlap/default semantics now, without income
sourcing. It risks suggesting that changing an annual dropdown correctly models
a midyear move. Defer that representation until its rules are specified.

A later Jan-1 full-year timeline can resolve California 2026–2030 and Florida
2031 onward, once both policies are supported. True part-year/nonresident tax
needs receipt dates, residency periods/persons, source jurisdiction, worldwide
and in-state taxable amounts, deduction/credit allocation, and other-state credit
coordination; potentially multiple returns in one year.

California does not tax qualifying retirement/IRA distributions of nonresidents;
part-year residents have resident-period worldwide income and nonresident-period
California-source income. It is not simply annual resident tax multiplied by a
fraction of days. Nonresident rate computations can depend on worldwide income.
Published examples distinguish retirement receipts from California-source deferred
wages and illustrate source/date treatment.
[FTB Publication 1100](https://www.ftb.ca.gov/forms/misc/1100.html),
[2025 Schedule CA (540NR) instructions](https://www.ftb.ca.gov/forms/2025/2025-540nr-ca-instructions.html)

Separate spouse residency, community property, special nonqualified deferred
compensation and credits require more than a household-level state enum. None
of these future features should change the first single-state MVP's old-plan
Michigan fallback.

## 15. State rule versioning and offline operation

Use a hybrid:
- Immutable year-specific JSON tables for brackets, deductions, credits, phaseout
  parameters and thresholds; source URL/document edition/access date/version.
- Small Java calculators for state law algorithms, qualifications, credit ordering,
  exclusions and adjustments.
- Immutable published/projected provenance: source tax year, projection year,
  enactment/effective/sunset dates and explicit future-indexing policy.

Keep the existing Michigan government JSON compatible. A separate state catalog
can initially adapt its Michigan record and load California bundles without
replacing every GovernmentRules constructor/resource. Tax rules need not share
a single published year with federal rules. Preserve the original Michigan
cap-inflation behavior exactly during extraction.

GovernmentRuleProjectionService currently replaces GovernmentRules.taxYear with
the projection year while retaining the published rulesVersion/effectiveDate.
That object alone cannot clearly distinguish original versus projected state
parameters. The proposed provenance must retain both years explicitly.

Enacted annual tables take priority. Unknown future years require an explicit
projection policy, with selected state bracket/deduction/credit indexing and
rounding. Do not blindly apply federal bracket growth to California, index every
threshold (including the million-dollar surcharge threshold), or inflate tax
rates. A statute's sunset is not a growth parameter. Known 2026 publications must
be distinguished from 2025-derived assumptions and checked for inconsistencies.

No runtime external tax API is recommended. Source review/import is a development
process; projections operate entirely offline from validated packaged rules.
A declarative all-state tax-language engine would add complexity without resolving
missing income facts.

## 16. Performance and concurrency

Existing ProjectionEngine construction reparses the government-rule resource.
IntegratedSocialSecurityStrategyEvaluator constructs an engine per evaluation;
weighted/Monte Carlo paths may reuse engines within their sessions. The proposed
state provider must not add another parse per year, candidate or funding iteration.

Load validated immutable state tables once into a bounded version/year catalog.
Resolve/project rules once per state/year/assumption set outside funding loops.
Cache keys must include state, published bundle/version, projection year and
every rule-growth/future-law assumption. Do not cache tax results by year alone:
withdrawal trials change income.

Capture immutable household/configuration facts once per run. Retain detailed
allocation events only when needed; annual aggregate tax facts should be compact.
Reuse invariant pension/SS components during funding trials when their
authoritative values are already supplied. Preserve evaluation order and
BigDecimal precision/rounding; do not adopt float/double tax math.

Calculators should contain no mutable request state, JavaFX references, I/O or
static current-state variables. A thread-safe immutable catalog and per-run local
scratch permit concurrent analysis without cross-plan state leakage.
Rule-loading counts, iterations, retained result size and engine-run counts should
be measured independently of financial correctness.

## 17. Recommended testing strategy

### Michigan preservation

Before extraction, freeze existing scalar/type behavior:
deduction caps, rate, exclusions/aggregate treatment, SINGLE/MFJ and unsupported
filing-status failures; zero/positive interest argument; opening RMD and actual
conversion amounts; tax-funding residual/cash availability; result/CSV/PDF labels.
Characterize the existing pension/death discrepancy rather than rewriting golden
values to accommodate a refactor.

Run existing couple golden tests, deterministic/weighted values, single-person
core projection, seeded fixed/longevity/paired Monte Carlo, RMD/Roth/tax/cash
settlement, JSON/baselines and PDF/report expectations. Missing-state legacy
plans and snapshots must use Michigan and preserve their existing numbers.

### California calculator fixtures

Test SINGLE/MFJ and boundary/zero income; every published bracket/table interval;
standard deductions; one/two personal credits; senior Jan-1 convention; federal-AGI
phaseout steps; SS exclusion without double subtraction; pensions, pretax
IRA/employer withdrawals, conversion income and qualified Roth exclusion;
million-dollar surcharge; rule year and future-policy provenance.

Use the FTB 2025 MFJ 125,000 → 4,768 regular-tax example, published tax-table cells,
Form 540 exemption-credit worksheet and Schedule CA SS adjustment lines as
independent fixtures. Preserve FTB whole-dollar/two-decimal stage rounding.
The FTB calculator accepts California taxable income, not AGI; it is a regular-tax
cross-check, not proof of full household liability or income classification.
[FTB official tax calculator](https://webapp.ftb.ca.gov/taxcalc)

For later basis capability, Publication 1005's 2025 Example 1 has an 800 federal
taxable IRA distribution, 300 remaining California basis, 500 California taxable
distribution and zero remaining basis. Its other IRA/Roth worksheets can seed
future multiyear golden fixtures. Do not implement basis behavior merely to make
these future tests applicable to today's model.

Test extra-input/unsupported envelopes explicitly: nonqualified Roth, military,
basis differences, unclassified gains/dividends, resident/nonresident/part-year,
AMT preferences, separate/RDP returns and refundable credits must not masquerade
as fully supported ordinary resident calculations.

### Funding and integration

Use synthetic progressive-state tests to exercise the interface before California:
cash already sufficient, deferred funding, cash/Roth/taxable-principal transitions,
portfolio exhaustion, crossing bracket/credit/surcharge boundaries and large
conversions. Verify final taxes equal recomputation, residual is funded within
approved cent policy, actual withdrawals occur once and live Account balances
remain unchanged. Stress nested Roth fill and Medicare/IRMAA recursion.

Run equivalent populated single/couple California fixtures through standard
projection, deterministic integrated analysis, weighted lifetime analysis,
zero-volatility seeded MC, longevity MC and paired identical strategies.
Compare annual federal/state totals, funding, accounts and metrics—not just
terminal tax. When supported, survivor filing/credits/pension/SS must agree with
actual annual income facts and existing death conventions.

Verify state persistence, deep copies, old baselines, source revisions and UI
assumptions reconstruction. Ensure changing general return assumptions does not
reset state. Verify Results/details, charts, CSV, all PDF adapters and frozen
result labels show the same state/year/scope. SS-only results must remain gross
and unchanged by tax-state selection.

Performance benchmarks run only after numerical correctness; never change
expected financial values to pass performance/regression tests.

### Read-only baseline run during 6A

IntelliJ bundled Maven was used with the required repository-local cache:
-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository.
Selection: Michigan*Test, TaxFundingCalculatorTest, Federal*Tax*Test,
TaxIncomeCalculatorTest, SocialSecurityTaxCalculatorTest,
RothConversionBracketFillCalculatorTest, SinglePersonCoreProjectionTest,
SinglePersonStage*GoldenTest, JsonRetirementPlanRepositoryTest,
SinglePersonPlanPersistenceTest.

**79 tests passed; zero failures/errors/skips.**
Log: target/phase6a-audit-baseline.log. No test expectations or test files changed.
This confirms existing selected behavior, not prospective California convergence.
A full suite was not rerun for this documentation-only audit.

## 18. Recommended implementation roadmap

Estimates are engineering planning ranges for one developer with tax-source
review, not delivery commitments. Missing income models and law verification
can dominate implementation time.

| Phase | Scope/classes likely affected | Risk / required tests / acceptance | Complexity |
| --- | --- | --- | --- |
| 6B: Michigan-preserving seam | StateIncomeTaxCalculator/StateIncomeTax evolution; immutable request/result; Michigan adapter; AnnualTaxCalculationService/registry; TaxFundingCalculator/TaxFundingResult; both engine and Roth-fill wiring; state-neutral ProjectionYear with Michigan compatibility; rule-session seam | Exact Michigan results/types/diagnostics preserved; synthetic second policy reaches both funding paths; no state UI/plan schema change; old rules/data retained; full existing suites/goldens pass | Medium, roughly 3–5 days |
| 6C: bounded California backend | California rules/data/calculator; immutable filer/income facts; standard deduction/credits/phaseout/SS exclusion/brackets/surcharge; support-envelope checks | FTB golden tables/worksheets and funding stress pass; verify rule editions; no invented basis/investment/military facts; separately approve pension/death correction before survivor release; no solver change without demonstrated failure and review | Medium–high, roughly 5–10 days |
| 6D: selection and persistence | TaxAssumptions/StateTaxConfiguration; legacy constructors/Jackson; AssumptionsView; ResultsSummaryView reconstruction; wizard Assumptions/Review; scenario-copy/baseline tests | Missing state remains Michigan; explicit unsupported state fails; state survives unrelated edits, save/reload and isolated copies; only registered supported states selectable; direct tooltips/clear scope | Medium, roughly 3–5 days |
| 6E: integrated release verification | ProjectionYear consumers, ResultsView/details, CSV/console, input/report snapshots and all PDF adapters; analyzer/MC context/cache propagation | Same state throughout ordinary/Roth funding, standard/weighted/MC/paired modes; return participants and supported survivor cases verified; frozen reporting provenance; Michigan full-suite/golden/seeded/PDF parity | High verification load, roughly 5–10 days |
| 6F: additional states and residency design | New calculator/rules registrations; resolver evolution; explicit distributions/investment/basis/source requirements | Add a state without new engine branches; separate approval for timelines, sourcing, multiple returns or missing investment model; offline determinism/concurrency tests | Per-state variable; timeline/basis work is high complexity |

The bounded release is roughly **3–6 engineer-weeks** across 6B–6E, excluding
separate investment/basis/part-year engines and unresolved source-law review.
A full California tax-return model is substantially larger.

Two release decisions should precede 6C: exact tax-table versus smooth projection
rounding, and the supported input/death-scenario envelope. Backend registration
is not UI support until 6D/6E acceptance. If discovery requires correcting legacy
financial semantics, stop for approval rather than hiding that correction in
interface extraction.

## 19. Risks and unresolved questions

1. Michigan's current capped ordinary-income model is simplified; interest is
   included in the deduction base by the live caller. Preserve that behavior in
   6B; any statutory correction is separate work.
2. Configured-death pension cash/tax inputs can differ; needs characterization and
   explicit approval before promising correct California survivor integration.
3. Missing investment returns/basis/qualification/source facts prevent broad
   California accuracy; a new interface cannot manufacture them.
4. Filing status is independent of household count; MFJ defaults and unsupported
   Michigan statuses already exist. Avoid legal-status inference or silent changes.
5. Annual tables/whole-dollar credit steps interact with funding precision; current
   fixed-point and recursive Medicare loops need stress verification for new policies.
6. FTB 2026 indexing news is incomplete and one row inconsistent. Confirm final
   sources; separate enacted and projected values and preserve source snapshots.
7. Federal/state enum duplication and dormant abstractions invite using the wrong
   types/path. Keep the live domain.rules.FilingStatus and minimal approved seams.
8. Adding configuration without updating ResultsSummaryView/AssumptionsView
   reconstruction can silently reset a user's state.
9. Report labels, frozen analyzer metadata and CSV column names are compatibility
   surfaces. Decide whether generic CSV headers require a versioned export option.
10. Baselines are not fully deep snapshots; do not broaden this phase into a
    historical-isolation refactor.
11. AMT, military exclusions, credits, separate/RDP status, death-year senior
    eligibility and future nonresident rules require explicit support decisions.
12. No blanket convergence/performance claim is justified before progressive-state
    stress tests. Do not replace the solver or optimize away needed recalculations
    merely because California rates are higher.

## 20. Explicit architecture recommendation and next approval gate

Approve **Phase 6B only as a Michigan-preserving tax boundary extraction**:
immutable income/return facts, an evolved existing state calculator interface,
unchanged Michigan adapter, one registry/rule-session path and identical policy
routing through ordinary funding and Roth bracket fill. Preserve existing plan
JSON, rules and numeric outputs; no California/UI implementation in that stage
unless separately authorized.

Then implement the bounded resident California backend, additive Michigan-defaulted
configuration and comprehensive mode/report integration. Use a constant state
resolver initially; reserve timelines and income sourcing for dedicated later work.

Only this report was created in Phase 6A. Nothing was staged, committed or pushed.
Production code/tests/calculations/schemas/UI remain unchanged. Await approval
before implementing Phase 6B.
