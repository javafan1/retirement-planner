Stage 4: one-strategy longevity-weighted integrated evaluation
=============================================================

LongevityWeightedIntegratedStrategyEvaluator accepts one complete claiming strategy,
a source plan, prepared HouseholdLongevityScenarios, the analyzer present-value base
date, and a real discount rate. Mortality conditioning remains a separate input in
the prepared assumptions. No claiming search, ranking, UI, or persistence is involved.

Each positive-probability scenario runs through the existing ProjectionEngine on a
deep copy. Birthday mortality outcomes retain their original probabilities and map
to January 1 of their calendar death years. The context's optional ending-year
override extends coverage to at least secondDeathYear - 1 and never shortens the
configured horizon or changes the source plan. Sequential runs reuse one engine.

EstateAtSecondDeathCalculator selects the preceding December 31 projection row.
The death-year row is not used. Death exactly at a January 1 projection opening uses
the opening account composition and the existing AfterTaxEstateCalculator. A second
death before available opening balances fails. Investable assets include retained
non-qualified balances; non-investable assets are excluded. Estimated heir tax is a
valuation haircut on tax-deferred assets, never a first-death payout or withdrawal.

For t = actual days from analyzer PV base date to modeled second death / 365.25:

    realEstate = nominalEstate / (1 + generalInflationRate)^t
    pvEstate = realEstate / (1 + realDiscountRate)^t

EstatePresentValueCalculator uses decimal logarithm/exponential series with 40-digit
working precision and 34-digit factors. No binary floating-point money calculations
or intermediate cent rounding are introduced. The existing annual engine retains its
own financial rounding. Factors are cached by second-death date; weighted sums use
the original probabilities without rounding or renormalization. Result values are
unrounded aggregates; presentation callers should round only for reporting.

Results retain compact outcomes and methodology/limitations, not full projections.
Progress reports positive-probability scenario runs. Zero mass is skipped. Failure of
any positive-mass scenario or boundary cancellation throws without returning a result.
The prepared birthday distributions have unique annual death-year pairs, so each
positive scenario currently requires one projection. No prefix/state caching is used.

Financial limitations
---------------------
The approved Stage 3.5A household convention remains in force: deceased-owner assets
stay invested and available to ordinary spending/tax funding, with unchanged tax
classification and estate inclusion, but no deceased-owner RMD or Roth conversion.
There is no inheritance/retitling, inherited-account distribution schedule, beneficiary
rule, or estate settlement. These omissions can materially affect expected estate.
Heir tax remains a simplified tax-deferred haircut. Independent mortality and full-year
January 1 deaths are modeling assumptions. A living owner outside the supported RMD
table still fails; terminal age-120 scenarios exclude deceased owners before lookup.

Ordinary, empty-context, and strategy-only projections retain their prior behavior
when no horizon override is supplied. Deterministic rankings are unchanged.
