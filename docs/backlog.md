
7.28 

Phase 1 – Finish the MVP (our immediate goal)
✅ Projection engine
✅ Federal tax engine
✅ Withdrawal strategies
✅ JSON persistence
⏳ JavaFX UI
⏳ Reports
Phase 2 – Make it pleasant to use

Instead of editing JSON by hand, the user should be able to:

Add/edit accounts
Add pensions
Add Social Security
Add expenses
Configure assumptions
Press Run Projection

That alone makes it a real application.

Phase 3 – Rich reporting

This is the part I'm excited about because it will make the planner much more useful than most commercial tools.

For each projection year, I'd like to display:

Year	Age	Expenses	Income	Withdrawals	Federal Tax	RMD	Ending Portfolio

Then allow expanding a year to see:

Account-by-account balances
Withdrawal breakdown
Tax calculation
RMD calculation
Effective tax rate
Cash flow summary

Phase 4 – Features that distinguish your planner

This is where your planner becomes something unique.

Some ideas we've discussed over the months include:

IRMAA modeling
Roth conversion planning
Social Security claiming optimization
Pension survivor comparisons
Home equity and other net-worth assets
Long-term care scenarios
Monte Carlo analysis
Multiple retirement "what-if" plans
Tax-efficient withdrawal optimization

These build naturally on the engine you've already created.
7.21 plan for planning assumptions

Investment Assumptions
----------------------
Expected Return
Expected Inflation
Expected Dividend Yield
Cash Return

Longevity Assumptions
---------------------
Primary Life Expectancy
Spouse Life Expectancy
Primary Death Age Override
Spouse Death Age Override

Social Security
---------------
Primary PIA
Spouse PIA
COLA
Claiming Ages
Taxability Rules

Pensions
---------
COLA
Survivor Percentage
Start Ages

Tax Assumptions
---------------
Federal Filing Status
State
Marginal Tax Target
Roth Conversion Tax Bracket
Capital Gains Rate
NIIT
IRMAA Enabled

Healthcare
----------
Medicare Start Age
IRMAA Thresholds
Medicare Inflation
Long-Term Care

Required Minimum Distributions
------------------------------
Use Current IRS Table
Custom Table
RMD Enabled

Simulation
----------
Projection Years
Monte Carlo Trials
Success Threshold

organized into fields
PlanningAssumptions
│
├── InvestmentAssumptions
├── InflationAssumptions
├── TaxAssumptions
├── SocialSecurityAssumptions
├── HealthcareAssumptions
├── LongevityAssumptions
├── RmdAssumptions
└── SimulationAssumptions

7.13
Backlog

□ Collectible assets

□ Vehicle depreciation

□ Home equity analysis

□ Long-term care strategies

□ Insurance planning

