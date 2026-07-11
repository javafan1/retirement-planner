

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