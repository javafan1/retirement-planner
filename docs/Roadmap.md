
7.29

Phase 1 – Cash Flow Engine
Annual income
Annual expenses
Investment growth
Portfolio withdrawals
End-of-year balances

Status: ✅ Complete (MVP)

Phase 2 – Tax Engine
Federal tax brackets
Standard deduction
Filing status
Michigan income tax
Effective & marginal tax rates

This is where we'll start today.

Phase 3 – Withdrawal Engine
Taxable accounts first
Traditional IRA/401(k)
Roth IRA
Cash reserves
Configurable withdrawal strategies
Phase 4 – RMD Engine
Calculate annual RMDs
Apply IRS life expectancy tables
Force minimum withdrawals
Feed taxable income into the Tax Engine
Phase 5 – Social Security
Benefit timing
Taxability of benefits
Survivor benefits
COLA
Phase 6 – Medicare
IRMAA
Medicare premiums
Net retirement income
Phase 7 – Advanced Planning
Roth conversions
Multi-year tax optimization
Legacy projections
Monte Carlo simulation

7.24

v0.1    MVP

v0.2.1  Tax-Aware Domain Foundation
v0.2.2  Account-Level Projection
v0.2.3  Tax Engine
v0.2.4  RMD Engine
v0.2.5  Medicare / IRMAA
v0.2.6  Survivor Scenarios
v0.2.7  Roth Strategy & Reporting

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