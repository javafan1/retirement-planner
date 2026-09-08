Run-only lifetime scenarios: Stage 3 and Stage 3.5A
=================================================

HouseholdLifetimeScenario is immutable evaluation-context state, never part of
RetirementPlan JSON. Optional death years derive January 1 death dates. An absent
scenario uses existing deterministic behavior; an explicitly empty scenario means
both people survive. Birthday mortality outcomes are mapped at the Social Security
application boundary without changing their dates or probability weights.

The existing ProjectionEngine continues through the configured horizon. After both
deaths, person income, survivor pensions, Medicare and recurring expenses are zero.
Scheduled one-time expenses retain existing rules. Configured filing status applies
through the first death year, with SINGLE in subsequent years.

Lifetime-only survivor financial mechanics
-----------------------------------------
RMD calculation excludes deceased owners before opening-data eligibility and age
factor lookup. Survivor annual and remaining opening RMDs retain existing rules.
Missing/stale excluded-owner opening data is ignored. Matching-year positive RMDs
already distributed for an excluded owner cause a clear inconsistent-input error;
no historical distribution is silently erased or counted as new cash.

Roth capacity and execution use the same eligible-owner set, retaining PRIMARY then
SPOUSE priority and same-owner destination rules. Requested amounts remain targets;
actual conversions are limited to eligible capacity and taxes use actual amounts.
FIRST_HOUSEHOLD_RMD remains stopped after the first positive annual household RMD
requirement observed in the run, including an opening requirement satisfied before
projection. The historical stop is run-only and does not affect deterministic runs.

One authoritative annual pension calculation supplies lifetime cash income and
pension tax income. Existing pension start/end dates, active months and COLA apply.
Survivor pensions require a deceased owner and living survivor. Present zero pension
income overrides the legacy tax calculation. The value reaches tax-funding and
bracket-fill iterations, final recalculations and Medicare cash funding.

Temporary household-asset convention and limitations
---------------------------------------------------
Deceased-owner accounts remain invested household assets, accessible to ordinary
spending and tax funding, with unchanged ownership labels, tax classifications and
estate inclusion. They generate no owner RMD and cannot participate in Roth
conversion. No retitling, inherited-account distribution schedule or beneficiary
rules are modeled. This is an explicit simplified financial model, not a complete
inheritance model; omitted distributions may materially affect expected estate.

There is no estate settlement or second-death termination. Ending estate remains a
configured-horizon estimate, never estate-at-second-death. A living owner above the
supported RMD-table age is still unsupported; deceased owners bypass lookup.
No weighted evaluation, UI, ranking, persistence or SS benefit rules are changed.
All corrections above require a supplied lifetime scenario. Ordinary and strategy-
only deterministic runs retain their previous pension-tax, RMD and Roth behavior.
