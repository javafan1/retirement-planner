# Analysis Inputs and Plan Assumptions

The analyzer has one shared expandable section below household/benefit identity
and above the analysis tabs. It contains a 34-row dependency matrix, objective
summaries, visible date/horizon definitions, and a current-value grid with
Input / Current Value / Source columns. The section starts collapsed; its title
and the input controls remain available regardless of the selected analysis tab.

## Input matrix

Every individual DOB, FRA benefit, retirement election, survivor election,
mortality category and adjustment has its own row. Rows use text, not color-only
markers. These groups summarize the complete matrix:

| Input group | SS only | Deterministic financial outcome | Longevity-weighted |
| --- | --- | --- | --- |
| Both DOBs, FRA benefits, retirement/survivor elections, SS COLA | Used | Used | Used |
| Both mortality categories/adjustments, conditioning date, valuation date, real discount rate, mortality distribution | Used | Not used | Used |
| Projection start, general inflation, investment return, expenses, federal/state taxes, Medicare/IRMAA, RMDs, Roth, pensions, accounts/ownership | Not used | Used | Used |
| Configured projection end and length | Not used | Used | Reference only |
| Configured plan death scenario and configured-horizon estate | Not used | Used | Not used |
| Estate at household second death | Not used | Not used | Used |
| Ranking objective | Used | Used | Used |

Claiming elections mean each candidate's elections. Persisted elections identify
the current-plan reference/baseline. SS only ranks expected PV of benefits.
Deterministic Exhaustive Search ranks after-tax estate at the configured horizon.
Quick Comparison uses SS-ranked candidates and shows SS expected PV alongside
deterministic outcomes; it does not select an estate-ranked winner. Its candidate
selection and SS columns therefore depend on mortality and valuation inputs even
though deterministic projection calculations do not.

Weighted ranking is expected PV **after-tax investable estate at household second
death**, excluding non-investable assets; it is not total net worth.

## Current-value sources

`SocialSecurityAnalyzerInputSummary` creates immutable display rows from the
current `RetirementPlan` and typed analyzer controls. It does not project finances
or calculate a mortality distribution. The view never parses display strings
back into financial objects.

- Planning assumptions supply projection start and configured calendar-year
  count. Following ProjectionEngine, ending year = start year + count - 1;
  displayed end is December 31 of that year. For example, July 1, 2026 with length
  2 means July 1, 2026 through December 31, 2027, not July 1, 2028. The partial
  opening year counts as one modeled calendar year.
- Economic assumptions supply investment return, general/healthcare inflation
  and plan-level SS COLA. Income-record legacy COLA does not replace plan COLA.
- Tax assumptions supply filing status, bracket/deduction growth, future federal
  adjustment/year, estimated heir tax rate and state/local rates. The domain does
  not store a named state jurisdiction, so the grid explicitly says so.
- Expenses and pensions show counts and up to three compact configurations,
  including amounts, schedules and growth/COLA/survivor settings. These are
  configured inputs, not newly calculated projected expenses or benefits.
- Accounts use authoritative portfolio totals and totals by ownership. Roth uses
  the effective plan request, including schedule, stop rule and optional target.
- Medicare/IRMAA is automatically modeled using age/alive status, annual federal
  tax results, filing status and projected government rules; there is no separate
  enrollment control to display. RMD inputs are owner DOBs, eligible accounts and
  prior December 31 balances; opening-data availability is counted without
  calculating an RMD in the view.
- Analyzer controls supply independent dates, categories, adjustments and real
  discount rate. The configured SSA table is named from its authoritative
  metadata. Invalid/missing editable values are labeled rather than defaulted.
- SS records supply FRA monthly benefits, benefit valuation years and current
  retirement ages/dates. The shared persisted survivor age is shown for each
  person, or explicitly as missing.

## Independent dates and compatibility

Both DatePickers initialize from the former single-date default: the plan's
projection start. Thereafter neither changes the other. Controls remain
session-level; no persistence field was added.

Mortality conditioning assumes both people are alive through the conditioning
date, applying the existing next-complete-birthday-interval convention. SS-only
remaining-benefit analysis starts on this date, as it did under the former
single-date path. Valuation date is solely the PV measurement origin. It is not
passed as the SS analysis start, mortality base date, or financial projection
start. This distinction prevents a valuation edit from changing cash flows.

`SocialSecurityStrategyAnalysisRequestFactory` and
`LongevityWeightedAnalysisRequestFactory` now accept both dates. Their old
single-date overloads delegate with equal dates for compatibility. Quick uses
the completed SS result's candidate ordering and precise PV values. Deterministic
exhaustive request construction remains plan-only.

The configured plan horizon is authoritative for deterministic outcomes. Weighted
scenarios retain Stage 5G exact ending semantics: January 1 second death uses the
preceding December 31 snapshot (secondDeathYear - 1); second death at projection
opening uses the opening snapshot. Configured length/end are reference only.
No weighted scenario or projection formula was changed.

## Current inputs, frozen results and revisions

The top grid refreshes on analyzer edits and source-plan revision callbacks.
Each summary is immutable. Completed SS assumptions use the captured context;
weighted methodology uses captured result metadata. Current edits never replace
those dates, rates or assumptions inside an old result. Old results stay visible
and receive stale indicators.

The job controller has independent conditioning-date and valuation-date revision
counters and change types. Either edit invalidates SS, Quick and Weighted
publication/current status. Neither invalidates Deterministic Exhaustive. Quick
must rerun because its candidate set and SS PV presentation depend on SS inputs,
even though its deterministic financial formula is unchanged. Plan changes still
invalidate all modes. The existing aggregate assumptions revision remains the
weighted result's overall input version; it does not couple the DatePickers or
their independent date counters.

Valuation-only factory tests hold mortality, cash flows, strategies and rates
fixed and confirm common scaling and unchanged ranking. Conditioning-only tests
confirm changed valid distributions/expected values with fixed valuation and
plan start; ranking is allowed to change. Existing mathematical/date validation
boundaries are retained. The completed SS precision correction is untouched.

## Layout and scope

One outer analyzer ScrollPane fits width but not height. The shared inputs and
result tabs use natural preferred vertical size. The previous weighted outer
ScrollPane was removed to avoid an extra vertical viewport inside the new outer
one. No individual text section receives a fixed height or internal scrollbar.
Wrapped grid labels have preferred-height minimums and width-constrained columns.

The weighted ranking retains minHeight 110, computed preferred/max height,
Priority.NEVER and TableView scrollbars. The later asset/heir-value enhancement
uses unconstrained columns to allow horizontal scrolling (see
`INTEGRATED-RESULT-ASSET-METRICS.md`). Its height
does not depend on row count or shared-section expansion. Other table settings
are unchanged. Existing dedicated SS-grid/table/text-area scrolling is retained.
Layout tests cover 1180x820, 900x650 and 1366x768, complete content, collapse,
re-expansion, keyboard expansion and stable ranking viewport height.

No financial formulas, rankings, persistence, Stage 5D worker defaults, or
completed precision files were changed in this task. Existing UI sizing edits
and Stage 5F/G implementations are preserved.

## Verification and changed files

Final verification on 2026-09-10, with IntelliJ bundled Maven and the required
repository-local `.codex-m2/repository`:

| Pass | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Input presentation (included in final UI/presentation pass below) | 4 | 0 | 0 | 0 |
| Date requests, compatibility factories and revisions | 46 | 0 | 0 | 0 |
| New valuation-origin / conditioning semantic tests | 2 | 0 | 0 | 0 |
| UI/layout/state and current-value presentation | 25 | 0 | 0 | 0 |
| Stage 5E/F/G and affected precision references | 77 | 0 | 0 | 0 |
| Complete non-benchmark suite (one stabilized run) | 1095 | 0 | 0 | 3 |

All Maven runs were sequential with software JavaFX rendering and
`-XX:ActiveProcessorCount=2`; production Stage 5D remains at 4. Benchmark classes
were excluded with `-Dtest=*Test,!*BenchmarkTest`. No full 5,184-strategy weighted
evaluation was run. The relevant valuation-invariance reference was rerun because
the request plumbing changed; the completed precision implementation was not
edited. The full suite retained all other precision and Stage 5A-G references.
`git diff --check` passed. Generated logs remain under ignored `target/`.

Added (Java paths below are under the existing `ui/socialsecurity` packages):

- `SocialSecurityAnalyzerInputMatrix.java`
- `SocialSecurityAnalyzerInputSummary.java`
- `SocialSecurityAnalyzerInputView.java`
- `SocialSecurityAnalyzerInputPresentationTest.java`
- `SocialSecurityAnalyzerDateRequestTest.java`
- `SocialSecurityAnalyzerDateSemanticsTest.java`
- `ANALYZER-INPUTS-AND-DATES.md`

Modified for this task:

- `SocialSecurityStrategyAnalyzerDialog.java`
- `SocialSecurityStrategyAnalysisRequestFactory.java`
- `LongevityWeightedAnalysisRequestFactory.java`
- `SocialSecurityAnalyzerJobController.java`
- `SocialSecurityStrategyAnalyzerDialogStateTest.java`
- `SocialSecurityAnalyzerJobControllerTest.java`
- `AGENTS.md` (durable input/date/layout architecture only)

Earlier uncommitted SS precision and weighted sizing files remain in the working
tree. They were not discarded or reimplemented. No commit was made.
