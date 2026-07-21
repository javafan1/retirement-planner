
7.21 roadmap

Planning Assumptions

[ Investment ]

Expected Return
Inflation
Cash Return

[ Taxes ]

Federal Tax Model
State
Roth Conversion Target
Capital Gains

[ Social Security ]

COLA
Taxability
Claiming Strategy

[ Healthcare ]

IRMAA
Medicare Inflation

[ Longevity ]

Life Expectancy
Override Ages

[ Simulation ]

Projection Years
Monte Carlo Trials

Retirement Planner
├── Household
├── Accounts
├── Income Sources
│     ├── Social Security
│     ├── Pensions
│     └── Other Income
├── Planning Assumptions
├── Projection
│     ├── Summary
│     ├── Yearly Table
│     ├── Charts
│     └── Scenario Comparison
└── Reports
├── PDF
├── Excel
└── Print

Projection Summary
================================================================

Household
Planning Assumptions
Projection Results

---------------------------------------------------------------
Year | Begin | Growth | Income | Expenses | Taxes | End Assets
---------------------------------------------------------------
2027
2028
2029
...

---------------------------------------------------------------

Summary Statistics

Ending Portfolio Value
Total Investment Growth
Total Withdrawals
Total Taxes Paid
Total Social Security
Total Pension Income
Highest Tax Bracket
First RMD Year
Age Assets Reach Peak
Age Assets Reach Minimum


7.14 MVP classes

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

7.12

Retirement Timeline Events object

2026
---------
Retire

Begin Pension

2028
---------
Lisa begins Medicare

2030
---------
David claims Social Security

2033
---------
Lisa claims Social Security

2035
---------
Roth conversions complete

2036
---------
First IRMAA surcharge

2038
---------
David begins RMDs

2040
---------
Mortgage paid off

2051
---------
Estimated portfolio peak

2064
---------
Portfolio becomes 80% Roth