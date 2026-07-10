
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