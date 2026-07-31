7.31

Tax Optimization Tab

✓ Keep me below the next IRMAA bracket

✓ Fill the 22% federal bracket

✓ Minimize lifetime taxes

✓ Delay Social Security until age 70

✓ Compare Roth conversion strategies

7.24.26

One RetirementPlan → multiple Scenarios → one independent Projection per Scenario → optional side-by-side comparison.


Beginning account balances
↓
Investment growth
↓
Guaranteed income
↓
Required distributions
↓
Roth conversion strategy
↓
Taxes / MAGI
↓
Medicare / IRMAA
↓
Expenses
↓
Cash surplus or deficit
↙             ↘
Surplus          Deficit
↓                ↓
Taxable/cash      Withdrawal
reinvestment       strategy
↓
Ending account balances

| Item                            | Classification                                |
| ------------------------------- | --------------------------------------------- |
| Medicare eligibility/start date | Derived from DOB / user override if necessary |
| Medicare Part B enrollment      | User data                                     |
| Medicare Part D enrollment      | User data                                     |
| MAGI                            | Calculated                                    |
| Filing status                   | Calculated/rules                              |
| IRMAA tier                      | Calculated                                    |
| Part B standard premium         | Government rule                               |
| Part B IRMAA surcharge          | Government rule                               |
| Part D IRMAA surcharge          | Government rule                               |
| IRMAA thresholds                | Government rule                               |
| IRMAA threshold growth          | Planning assumption                           |
| Medicare premium growth         | Planning assumption                           |
| IRMAA lookback rules            | Government rule                               |
| Life-changing-event treatment   | Government rule                               |


RetirementPlanner
│
├── retirement-plan.json
│     David/Lisa
│     Accounts
│     Income
│     Expenses
│     Planning assumptions
│     Scenario assumptions
│
└── Government Rules
├── federal-tax-rules.json
├── rmd-rules.json
├── irmaa-rules.json
├── michigan-tax-rules.json
└── local-tax-rules.json

Traditional IRA ──────┐
Rollover IRA ─────────┤
Pre-Tax 401(k) ───────┼── TAX_DEFERRED
Pre-Tax 403(b) ───────┘

Roth IRA ──────────────┐
Roth 401(k) ───────────┴── ROTH

Brokerage ───────────────── TAXABLE

Cash ────────────────────── CASH

Inherited Traditional IRA ─ TAX_DEFERRED_INHERITED
Inherited Roth IRA ───────── ROTH_INHERITED


User data → facts such as DOB, account type, balance, ownership, inherited-IRA information.

Planning assumptions → uncertain future values such as inflation, tax-bracket growth, investment return, survivor expense percentage.

Scenario variables → choices we want to compare, such as David dies at 80, Lisa dies at 85, or convert $150K/year to Roth.

Government rules → tax brackets, RMD tables, IRMAA thresholds, Social Security taxation rules, etc. These should come from versioned rule tables rather than user-entered assumptions.

I would start with Accounts and Tax Treatment, because taxes, RMDs, inherited IRAs, Roth conversions, and death scenarios all depend on getting that model right.


7.12


Before you stop for the day...

I have one "homework assignment"—not coding, just thinking.

I'd like you to think about what questions you want this software to answer.

Not features.

Questions.

For example, you've already given me some excellent ones:

Should I take the lump sum or the annuity?
When should David claim Social Security?
When should Lisa claim Social Security?
How much can I convert to Roth without crossing the next IRMAA bracket?
How much can we safely spend each year?
What happens if one of us dies at age 78?
Should we spend more while we're younger?
How much do we need to leave our children?
How much income tax will we pay over our lifetime?
How much will IRMAA cost us?
How much of our portfolio should be Roth by age 80?

I think that list will become our product backlog.

Instead of inventing features, we'll build capabilities that answer real retirement planning questions.

## Functional Requirements

Decision audit trails.. reproducible calculations
Recommendation:
Take the 80% Survivor Pension

Assumptions

Investment Return: 6.5%

Inflation: 2.5%

Federal Tax Rules: 2026

IRMAA Rules: 2026

Social Security:
David age 70
Lisa age 62

Result

Lifetime Spending

$4.82M

Estate

$6.11M


Version 1 
-Financial Model
Households
People
Accounts
Pensions
Social Security

- Projection
Annual projections
Net worth tracking
Income tracking
Expense tracking

-Taxes
Federal tax
Michigan tax
IRMAA

- Decisions
Pension analysis
Social Security optimization

- future version
Monte Carlo
Charitable giving
Trusts
Estate tax
International taxation
## Pension Decision Analysis

The application shall evaluate all pension
election options.

Examples:

- Lump Sum
- Single Life
- Joint 50%
- Joint 75%
- Joint 80%
- Joint 100%

For every option the engine shall calculate:

- Lifetime spending
- Lifetime taxes
- Lifetime Medicare premiums
- Lifetime IRMAA
- Ending estate
- Survivor income
- Probability of success

The application shall recommend the
best election based upon user goals.

### Accounts

- Traditional IRA
- Roth IRA
- Brokerage
- 401(k)
- HSA
- Cash Accounts

### Income

- Pension
- Social Security
- Employment
- Rental Property
- Annuities

### Expenses

- Living Expenses
- Healthcare
- Travel
- One-Time Purchases

### Taxes

- Federal
- State
- Capital Gains
- NIIT
- Social Security Taxation

### Medicare

- Eligibility
- IRMAA
- Premium Forecasting

# Retirement Decision Engine

## Vision

The Retirement Decision Engine helps households make
better retirement decisions by modeling multiple
financial scenarios and explaining the tradeoffs.

The application is intended to optimize long-term
retirement outcomes rather than simply calculate
financial projections.

---

## Primary Goals

- Model a household's financial life.
- Project retirement over time.
- Compare alternative retirement strategies.
- Explain recommendations.
- Optimize for user-defined goals.

---

## Major Decision Types

- Pension election
- Social Security claiming
- Roth conversions
- Withdrawal strategy
- Asset allocation
- Medicare / IRMAA planning
- Tax planning
