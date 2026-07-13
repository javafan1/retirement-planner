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
