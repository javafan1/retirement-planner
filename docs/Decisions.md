7.23 

I think we should eventually model each person like this
Person
--------
name
birthDate
expectedDeathDate

For the MVP, expectedDeathDate can simply be entered manually.

Later we could support:

actuarial life expectancy
Monte Carlo longevity
user-defined scenarios

7.11.26

Refactor

build Goal==>

Maximize Estate

Maximize Lifetime Spending

Minimize Taxes

Maximize Survivor Income

Minimize RMDs

Preserve Guaranteed Income

Household
│
▼
RetirementPlan
│
├── Assumptions
├── Goals
├── Constraints
└── Scenarios

Household
│
├── People
│
├── Accounts
│
├── Income Sources
│
├── Expenses
│
├── Assumptions
│
└── Goals


I don't want Person to own everything.

Instead, I'd like Person to represent exactly one thing:

A human being.

Everything else should be attached to that person.
Household
│
├── David (Person)
│      │
│      ├── FinancialProfile
│      │       ├── Accounts
│      │       ├── Income Sources
│      │       └── Liabilities
│      │
│      ├── HealthProfile
│      │
│      └── EmploymentProfile
│
└── Lisa (Person)

