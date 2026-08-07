
8.7

                 RetirementPlan
                       │
                       ▼
          ProjectedAssetPoolsBuilder
                       │
                       ▼
              ProjectedAssetPools
                       │
        ┌──────────────┼───────────────┐
        ▼              ▼               ▼
Investment      Withdrawals      Roth Conversions
Growth           / RMDs
│              │               │
└──────────────┼───────────────┘
▼
ProjectedAssetPools
│
▼
Tax Calculations
│
▼
ProjectionYear

8.3

I also want to point out something that has become clear over the past several weeks.

When we started, we were focused on building a retirement planner. As we've worked through taxes, Medicare, and now growth models, we've naturally evolved toward a layered architecture:

Economic assumptions (how the world changes)
Projection engine (what happens under those assumptions)
Strategy engine (what decisions the user makes)
Comparison engine (how different strategies compare)

That's a very clean separation, and I think it will make the application much easier to extend over time without accumulating lots of special-case logic.


8.1
com.daviddunn.retirementplanner.domain.tax
│
├── FederalTaxCalculator
├── FederalTaxCalculation
├── TaxIncome
├── TaxIncomeCalculator
│
└── state
│
├── StateIncomeTax
├── StateIncomeTaxCalculator
├── StateTaxParameters
└── michigan
├── MichiganIncomeTaxCalculator
├── MichiganRetirementDeductionCalculator
└── MichiganTaxParameters


7.28


Right now, the projection is essentially doing this each year:

✅ Applies investment growth.
✅ Adds income.
✅ Subtracts expenses.
✅ Updates the investable portfolio balance.
✅ Produces the yearly projection, dashboard, and portfolio chart.

What it is not doing yet includes:

❌ Federal income tax calculations.
❌ State or local income taxes.
❌ Progressive federal tax brackets.
❌ Standard deduction.
❌ Taxation of Social Security benefits.
❌ RMD calculations beginning at the applicable age.
❌ Automatic withdrawals from tax-deferred accounts to satisfy RMDs.
❌ Medicare IRMAA surcharges.
❌ Roth conversion modeling.
❌ Tax-efficient withdrawal sequencing.
The good news

You've already laid some of the groundwork:

PlanningAssumptions
TaxAssumptions
Economic assumptions
Cached Projection
Projection engine architecture

That means we can add taxes without redesigning the application.

The order I'd recommend

Rather than trying to add everything at once, I'd build it in stages:

Federal income tax calculation using tax brackets and the standard deduction.
State and local income tax (Michigan is straightforward).
Withdrawal planning, determining how much to take from taxable, tax-deferred, and Roth accounts.
RMD engine, forcing minimum withdrawals once a person reaches the applicable RMD age.
Social Security taxation.
IRMAA premiums.
Optional Roth conversion strategies.


                 MainWindow
                      │
                      ▼
          ApplicationController
             │              │
             │              ▼
             │        ProjectionEngine
             │              │
             ▼              ▼
      RetirementPlan   Projection
             │          │       │
             │          │       ▼
             │          │  ResultsView
             │          ▼
             │   ProjectionYearView
             ▼
    HouseholdView
    AccountsView
    IncomeSourcesView
    ExpensesView
    AssumptionsView


7.26

We have now proven end-to-end that the withdrawal strategy stored in the retirement plan actually changes account-level projection behavior. The original projection tests already establish the cash-flow/withdrawal mechanics we built on

PlanningAssumptions
└── WithdrawalAssumptions
└── WithdrawalStrategyType
│
├── TAXABLE_FIRST
└── TAX_DEFERRED_FIRST
↓
WithdrawalStrategyFactory
↓
WithdrawalStrategy
↓
ProjectedWithdrawalAllocator
↓
account-level balances
↓
ProjectedAccountSnapshot
↓
ProjectionYear

----------
Beginning ProjectedPortfolio
↓
Allocate investment growth
↓
Calculate RMD from prior Dec. 31 balances
↓
Remove RMD from correct accounts
↓
Use RMD toward spending need
↓
Additional withdrawal if needed
↓
Retain excess RMD as cash
↓
Actual Ending ProjectedPortfolio
↓
Next year's RMD snapshot

rmds
ok
2034 beginning assets       $1,000,000.00
2034 expenses                  -30,000.00
-------------
12/31/2034 RMD balance         970,000.00

2035 RMD:
$970,000 / 24.6                 39,430.89

2035 cash-flow need             30,000.00
2035 required withdrawal        39,430.89
2035 excess RMD                  9,430.89

WithdrawalResult
│
├── cashFlowNeed
├── requiredMinimumDistribution
├── totalWithdrawal
└── excessRmd
│
▼
ProjectionYear

Guaranteed income
Annual expenses
Prior 12/31 balances
│
▼
Calculate RMD
│
▼
Calculate cash-flow need
│
▼
WithdrawalCalculator
│
▼
max(cash-flow need, RMD)
│
▼
Total portfolio withdrawal
│
▼
Ending assets

Cash-flow need
│
├──────────────┐
│              │
▼              ▼
$40,000          RMD
$55,000
│              │
└──────┬───────┘
▼
Total withdrawal
$55,000
│
└── Excess RMD = $15,000


7.25

All tests are passing.
ProjectedPortfolio now lets the projection evolve without mutating the real accounts.
RmdBalanceSnapshot captures prior December 31 balances.
IRA RMDs use projected snapshot balances.
401(k) and 403(b) RMDs use their individual projected balances.
OwnerRmdCalculator and HouseholdRmdCalculator are snapshot-aware.
ProjectionEngine now carries projected account balances forward year-to-year.
RMDs are now calculated and reported in ProjectionYear.
Most importantly, we have not yet mixed RMDs into withdrawals, so we're stopping at a clean architectural boundary.

RetirementPlan
│
├── actual Primary IRA = $500,000
└── actual Spouse IRA  = $400,000

             NOT USED
                X

12/31/2034 RmdBalanceSnapshot
│
├── Primary IRA = $1,000,000
└── Spouse IRA  =   $750,000
│
▼
HouseholdRmdCalculator
│
┌───────┴────────┐
▼                ▼
Primary RMD         Spouse RMD
$40,650.41          $30,487.80
│                │
└───────┬────────┘
▼
Household RMD
$71,138.21
--------

HouseholdRmdCalculator
│
├── PRIMARY
│      ↓
│ OwnerRmdCalculator
│      ├── IRA
│      ├── 401(k)
│      └── 403(b)
│
└── SPOUSE
↓
OwnerRmdCalculator
├── IRA
├── 401(k)
└── 403(b)

All balances
↑
RmdBalanceSnapshot
↑
prior December 31
---------

Normal/current calculation

AccountPortfolio
↓
current account balances
↓
OwnerRmdCalculator
↓
OwnerRmdResult


Projection calculation

AccountPortfolio ──────→ account identity/type/owner
│
RmdBalanceSnapshot ────→ 12/31 projected balances
│
▼
OwnerRmdCalculator
│
▼
OwnerRmdResult


GovernmentRules
│
├── FederalTaxRules
│
└── RmdRules
│
├── StartingAgeRules
│     └── birth-year range → starting age
│
└── UniformLifetimeTable
└── age → distribution period

GovernmentRules
│
├── rulesVersion
├── taxYear
├── effectiveDate
│
└── Federal tax rules
│
├── SINGLE
│     ├── Standard Deduction
│     └── Tax Brackets
│
├── MARRIED_FILING_JOINTLY
│     ├── Standard Deduction
│     └── Tax Brackets
│
├── MARRIED_FILING_SEPARATELY
│
└── HEAD_OF_HOUSEHOLD
7.22
I've been thinking about your overall domain model, and I see two possible "owners" for different concepts:

Household owns the people and household-level information.
AccountPortfolio owns the investable assets.
Each Account knows which household member owns it.
Each IncomeSource knows which household member owns it.

That creates a consistent model:

RetirementPlan
│
├── Household
│     ├── Primary Person
│     └── Spouse
│
├── AccountPortfolio
│     ├── Traditional IRA (PRIMARY)
│     ├── Roth IRA (PRIMARY)
│     └── Traditional IRA (SPOUSE)
│
└── PlanningAssumptions

7.21 

ProjectionYear
├── Calendar Information
├── Asset Information
├── Income Information
├── Expense Information
├── Tax Information
├── Healthcare Information
└── Summary Information

RetirementPlan
│
├── Household
├── Accounts
├── Income Sources
├── Planning Assumptions
└── Projection
│
├── ProjectionYear
├── ProjectionStatistics
└── ProjectionSummary

ProjectionEngine
│
├── BeginningAssetsCalculator
├── GrowthCalculator
├── IncomeCalculator
├── TaxCalculator
├── MedicareCalculator
├── WithdrawalCalculator
└── EndingAssetsCalculator

7.15 mvc v1 packages
com.daviddunn.retirementplanner

app
data
domain
    financial
    income
    model
    projection
persistence
ui
    console
util

7.14 MVP class diagram

RetirementPlan
│
├── Household
│     ├── Person (David)
│     ├── Person (Lisa)
│     ├── Expense*
│     └── getAllAccounts()
│
├── PlanningAssumptions
│
Person
│
├── Account*
├── IncomeSource*
│
Account
│
├── TraditionalIRA
└── RothIRA

IncomeSource
│
├── Pension
└── SocialSecurity

7.13

Walking Skeleton
UI
│
Application
│
Persistence
│
Domain
│
Projection
│
Report

v0.1
Project skeleton

v0.2
Financial model

v0.3
Projection engine

v0.4
JSON persistence

v0.5
Console reports

-------------------

MVP Released

-------------------

v0.6
Federal taxes

v0.7
Michigan taxes

v0.8
IRMAA

v0.9
Social Security optimization

v1.0
Pension optimization

MVP Plan

                UI
                 │
                 ▼
        RetirementPlannerApplication
                 │
                 ▼
        RetirementPlanRepository
                 │
                 ▼
           RetirementPlan
                 │
                 ▼
         ProjectionEngine
                 │
                 ▼
           Projection
                 │
                 ▼
          ConsoleReport

Hierarchy change to support Assets instead of just accounts

Asset
│
├── FinancialAsset
│      ├── TraditionalIRA
│      ├── RothIRA
│      ├── BrokerageAccount
│      ├── CheckingAccount
│      └── SavingsAccount
│
├── RealEstate
│      ├── PrimaryResidence
│      ├── VacationHome
│      └── RentalProperty
│
├── Vehicle
│
└── Collectible
├── CoinCollection
├── ComicCollection
├── CardCollection
└── Artwork

7.12

src/main/java
└── com
└── daviddunn
└── retirementplanner
│
├── app
│      RetirementPlannerApplication.java
│
├── data
│      DemoDataFactory.java
│
├── domain
│      ├── model
│      ├── financial
│      ├── income
│      └── projection
│
├── persistence
│      (empty for now)
│
├── ui
│      (empty for now)
│
└── util


MVC like layers
View

↓

Application Layer

↓

Domain Layer

↓

Persistence



                        RetirementPlan
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
    Household         PlanningAssumptions      PlanningGoals
         │
         ▼
      Person
         │
┌───────┴────────┐
│                │
Accounts      IncomeSources

                         RetirementPlan
                               │
     ┌─────────────────────────┼─────────────────────────┐
     │                         │                         │
Household              PlanningAssumptions          PlanningGoals
│
├─────────────────────────────────────────────────────────────┐
│                                                             │
Person                                                      Person
│                                                             │
┌───┴──────────────┐                                    ┌─────────┴───────┐
│                  │                                    │                 │
Accounts      IncomeSources                        Accounts        IncomeSources

7.11

Phase 1 – Domain Model (current)
Person
Household
Financial accounts
Income sources
Institutions

Phase 2 – Projection Engine
Project one calendar year
Expand to multi-year projections
Support configurable assumptions

Phase 3 – Tax & Medicare Engine
Federal income tax
State tax (Michigan initially)
Social Security taxation
RMD calculations
IRMAA calculations
Medicare premium forecasting

Phase 4 – Decision Engine
Pension lump sum vs. annuity
Social Security claiming optimization
Roth conversion optimization
Withdrawal strategy optimization
Asset allocation comparisons

Phase 5 – Reporting
Lifetime cash flow
Lifetime taxes
Lifetime Medicare premiums
Estate projections
Sensitivity analysis
Human-readable recommendations


from 7.10.26
I think the project should be organized by business domain, not by technical function.

Here's the package hierarchy I'd recommend today

src
├── main
│   └── java
│       └── com
│           └── daviddunn
│               └── retirementplanner
│
│                   ├── Main.java
│                   │
│                   ├── app
│                   │      └── RetirementPlannerApplication.java
│                   │
│                   ├── model
│                   │      ├── Person.java
│                   │      ├── Household.java
│                   │      └── Institution.java        (next)
│                   │
│                   ├── financial
│                   │      ├── Account.java
│                   │      ├── AccountType.java
│                   │      ├── TraditionalIRA.java
│                   │      ├── RothIRA.java
│                   │      ├── BrokerageAccount.java   (next)
│                   │      ├── FourZeroOneK.java       (next)
│                   │      └── HSA.java                (later)
│                   │
│                   ├── income
│                   │      ├── IncomeSource.java       (next)
│                   │      ├── Pension.java
│                   │      ├── SocialSecurity.java
│                   │      └── EmploymentIncome.java
│                   │
│                   ├── projection
│                   │      ├── ProjectionRequest.java
│                   │      ├── ProjectionResult.java
│                   │      ├── ProjectionYear.java
│                   │      └── ProjectionEngine.java
│                   │
│                   ├── decision
│                   │      ├── Scenario.java
│                   │      ├── DecisionEngine.java
│                   │      ├── Recommendation.java
│                   │      └── DecisionReport.java
│                   │
│                   ├── tax
│                   │      ├── TaxProfile.java
│                   │      └── TaxCalculator.java
│                   │
│                   └── util
│                          ├── Money.java
│                          └── CurrencyFormatter.java
│
└── test
└── java
└── com
└── daviddunn
└── retirementplanner
├── model
├── financial
├── projection
└── decision