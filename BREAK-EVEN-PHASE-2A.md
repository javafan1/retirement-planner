# Break-Even Phase 2A

## Audit and reused implementations

Mortality context uses the same `SocialSecurityMortalityTables.ssaPeriod2022()`,
`AnalyzerLongevityAssumptions`, `SocialSecurityMortalityAdjustment`,
`HouseholdLongevityScenarioFactory`, and `HouseholdLongevityScenarios` used by
Longevity-Weighted Integrated analysis. The factory uses
`SocialSecurityMortalityDistributionProvider` and
`SocialSecurityIndependentJointMortalityCalculator`. Annual timing reuses
`HouseholdLifetimeScenarioMapper`.

The table stores conditional annual qx: death between exact age x and x+1.
The distribution provider conditions on the selected mortality base date and
uses `NEXT_COMPLETE_BIRTHDAY_INTERVAL`: a date between birthdays starts at the
next birthday's complete interval. Interval x to x+1 is assigned death age x+1,
with remaining mass assigned at the table's terminal age.

The integrated analyzer then maps birthday death dates to **January 1 of the
same calendar year**. This enhancement uses that same annual mapping: survival
in calendar year Y is the probability of a modeled death year **later than Y**.
It describes survival during the entire modeled year, not conventional exact-date
survival to the start/end of a real calendar year. It does not shift deaths to
the following year. Dates before the conditioning year are unavailable.

Spouse mortality is independent in the existing model. Rather than implement
a new household formula, Phase 2A sums the existing joint scenario probabilities
where either member is alive after annual mapping. Tests verify equality with
the equivalent marginal-survival complement formula and with the weighted
analyzer's scenario mass. No mortality tables, adjustments, interpolation, or
financial projection calculations were changed.

## Inputs and availability

Mortality categories and birth dates are captured with each projection's
existing immutable Break-Even person metadata. Different Baseline/Current birth
dates or mortality categories produce an explicit unavailable message. Missing
categories, missing birth dates, and unsupported table ages also produce an
explanation rather than inferred probabilities.

Longevity factors and mortality conditioning date were previously analyzer
dialog-session inputs, not persisted Baseline/Current assumptions. They now
share one non-persisted session selection through ApplicationController. Initial
defaults remain the analyzer defaults: projection-start conditioning date and
factors of 1 for both people. The last valid selections are reused when reopening
the analyzer or opening Break-Even; changing plans resets them. Editing these
settings does not mark/save a plan or run an analysis. The shared conditioning
date, factors, and table identity are disclosed in survival help text. The user
was asked about this choice; absent a reply, the recommended shared-session
behavior was used and announced.

The probability context uses stochastic longevity assumptions, not the fixed
death scenario underlying either deterministic financial projection. It never
multiplies benefits, assets, net worth, estate, or differences by probabilities.

## Claim events

The normal projection adapter's retirement election uses each Social Security
record's **stored start date** (`SocialSecurityProjectionIncomeProvider.election`),
so metadata now captures that exact date alongside the saved claiming age.
No birthday-plus-age or January 1 date is invented. Event tooltips retain the
exact date; visible markers use its calendar year.

Events group by household role and year. Matching Baseline/Current elections
display once as “Both plans”; differing years remain separate. The rare case
of different saved ages in a common year displays both ages. Events outside
comparison start/end are omitted. These are configured retirement elections,
not new benefit eligibility/receipt calculations. Missing/ambiguous Social
Security records have no fabricated event date.

## Presentation

- Claim events use secondary dashed vertical lines and staggered compact labels.
- The selected metric retains its sustained-break-even marker; Social Security
  identifies its callout as “SS Break-Even”.
- A probability row below the year axis uses the axis's actual major tick
  positions. Tick density responds to width. Unavailable tick years show a dash.
- Valid sustained-year cards and yearly/marker hover descriptions show the
  prepared household probability. Other card states have no fabricated value.
- Percentages round to whole percent for display only; prepared values retain
  full BigDecimal precision.
- Help text explains the annual convention, independence, shared assumptions,
  and that probabilities are modeled context, not individual lifespan predictions.

## Files

Added:

- `app/breakeven/BreakEvenContextFactory.java`
- `domain/breakeven/BreakEvenContext.java`
- `domain/breakeven/BreakEvenEvent.java`
- `domain/breakeven/BreakEvenSurvivalPoint.java`
- `domain/socialsecurity/analysis/LongevitySessionSettings.java`
- Test: `app/breakeven/BreakEvenContextFactoryTest.java`
- This report.

Modified (Java paths relative to `com/daviddunn/retirementplanner`):

- `domain/breakeven/BreakEvenPlanSummary.java` — additional non-persisted metadata.
- `ui/controller/ApplicationController.java` — session settings/context preparation.
- `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java` — shares valid session selections.
- `ui/MainWindow.java`, `ui/views/ResultsView.java`, `ui/views/ResultsSummaryView.java` — prepared-context handoff.
- `ui/breakeven/BreakEvenAnalysisDialog.java`, `BreakEvenAnalysisView.java`,
  `BreakEvenChart.java`, `BreakEvenPresentation.java` — presentation.
- `src/main/resources/css/break-even.css`.
- Tests: `ui/breakeven/BreakEvenAnalysisViewTest.java`,
  `ui/controller/BreakEvenControllerTest.java`,
  `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialogStateTest.java`.

`BreakEvenAnalyzer`, its mathematical/result status logic, baseline snapshot
behavior, persisted plan schema, and projection/mortality engines are unchanged.

## Verification and limits

Tests cover probability boundaries, calendar/age mapping, next-complete-interval
conditioning, exact joint-mass agreement, longevity-factor effects, mismatched
and missing inputs, claim-date grouping/range exclusion, responsive tick
alignment and label collision avoidance, card/tooltip context, absent false
card percentages, session synchronization, and no plan writes/projection calls.

A full 2027–2051 synthetic David 70/Lisa 62 versus David 70/Lisa 70 workflow
produces one common David event, two Lisa events, and 25 probability points.
All four deterministic metric result objects (annual values, statuses, first
crossover, and sustained year) compare exactly equal before/after context
preparation; cached projection identities remain unchanged.

The workspace `plan.json` does not contain the user's saved Baseline/Current
comparison, so the specific reference years 2043/2048/2048/2047 could not be
independently reproduced. They are not hard-coded. The unchanged analyzer and
exact-result regression checks establish that Phase 2A does not change those
years when run on the same projections.

Focused log: `target/break-even-phase2-focused.log` (81 tests, no failures/errors).
Full non-benchmark suite: 1,350 tests, zero failures/errors, 3 skipped; BUILD SUCCESS.
Full-suite log: `target/break-even-phase2-full.log`.
No commits were made.
