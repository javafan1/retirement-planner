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