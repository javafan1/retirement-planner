# Claiming-strategy heat map

Both Deterministic Exhaustive Search and Longevity-Weighted integrated results
provide alternative Ranked Strategies and
Claiming-Age Heat Map tabs. The heat map is a 62–70 primary (columns) / spouse
(rows) claiming-age grid. Each cell retains the first
complete strategy for that age pair in the backend's successful ranked results,
including its exact survivor elections and original result occurrence ID.
The backend's highest successful result supplies the common comparison value.
Exact optimal ties receive a star, bold text, and a stronger border.

The shared immutable presentation model and JavaFX view consume analysis-specific
adapters. The shared metric/profile data supplies each mode's vocabulary and
available values; no financial engine is called by the heat map. Both modes
use the same result-tab layout, CSS, accessibility markers, tooltips, responsive
grid, and detail interaction.

Longevity-Weighted Integrated Analysis evaluates strategies across mortality
scenarios and probability weights. Its adapter computes only display ratios
and differences from the completed weighted aggregates:

- Percent of optimal = candidate expected PV after-tax estate / highest expected
  PV after-tax estate × 100, using BigDecimal DECIMAL128 division.
- Difference from optimal = candidate PV minus highest PV, without intermediate
  currency rounding.
- Difference from current uses the existing successful baseline comparison.

Percentages display one decimal place. If the highest objective value is zero or negative,
percentages are unavailable; dollar metrics remain available. Missing or failed
age pairs are unavailable, never zero. A partially successful pair identifies
how many complete strategies failed. Optimal means best successfully tested
strategy, not a recommendation.

Five longevity-weighted display modes use existing results: percent of optimal, expected PV
after-tax estate, difference from optimal, difference from current, and
future-dollar estate. Colors always represent percentage tiers using values
before display rounding. Future-dollar estate is the existing expected nominal
after-tax estate at second death. Expected PV Social Security is unavailable
in the weighted aggregate and is explicitly shown as Not Available in details;
its enum entry is reserved for future support.

Deterministic Integrated Analysis uses the configured plan death scenario and
projection horizon. Its objective remains After-Tax Estate, in future dollars.
It has four display modes: % of Optimal, Difference from Optimal, Difference
from Current Plan, and Future-Dollar Estate. Percent of optimal divides the
cell's after-tax estate by the backend's highest-ranked after-tax estate;
difference from optimal subtracts the latter. Difference from current copies
the existing deterministic candidate-minus-current after-tax-estate difference.
The deterministic baseline continues to use its persisted survivor policy.
There is no PV discounting, mortality weighting, or expected-value calculation.
Unavailable weighted metrics are omitted. Different optimal strategies between
the two modes are expected; neither ranking is reconciled or changed.

Both adapters read all successful ranked entries, rather than the limited
deterministic grouped table. The first backend-ranked entry for an age pair
retains its complete survivor strategy, rank, and original occurrence ID.
Deterministic detail navigation opens the existing Selected Outcome view for
that exact entry. If it is not a displayed group representative, table selection
is cleared without adding or replacing table rows; its compact metrics remain
available even when a full projection was not retained.

Selecting a cell, switching tabs, or changing metrics performs no projection,
search, request submission, or plan mutation. View Full Strategy Analysis switches
to Ranked Strategies, selects the original result, and reveals its existing
detail section. Each new result resets the metric to % of Optimal; tab switching
preserves the current selection and metric. Busy and stale indicators
follow each mode's existing analysis state. The heat map uses frozen result
metadata and the shared HelpIcon tooltip factory. Its content expands naturally
inside the outer analyzer scroller while the ranking table retains its viewport.
Selecting the heat-map tab brings the results area into view. Shared assumptions,
baseline inputs, run controls, and progress remain outside the result tabs.

At heat-map widths of at least 1200 logical pixels, the grid and selected details
appear side by side with roughly 73% / 27% of the available width. Narrower
windows place details below the grid. Cells target 40 pixels high and retain
normal text size. The compact header provides the longer explanation through
a HelpIcon. The complete grid fits the tested 1880 × 1000 desktop window;
smaller windows or increased operating-system display scaling may require
scrolling. Expanded ranking sections use their natural wrapped content height.

The feature does not change mortality, survivor optimization, current-strategy
baseline completeness, candidate ranking, persistence, or financial formulas.

## Export PDF

Both Deterministic Exhaustive Search and Longevity-Weighted results provide an
**Export PDF** action above the result tabs. It is available after a successful,
current analysis and disabled while analysis/export is running or results are
stale, incompatible, or unavailable. Export does not rerun calculations or
change the plan, result selection, heat-map metric, or analysis state.

The landscape US Letter report includes result-time assumptions, household and
run context, the selected strategy, the colored claiming-age heat map and its
legend, ranked strategies, and supporting analyzer results. It captures the
metric currently selected in the heat map. Selection comes from the active
result tab: the heat-map cell or the ranked-table detail selection (including
an exact deterministic strategy opened from an unlisted heat-map result).
If the selected table strategy has different survivor elections from the best
strategy at that age pair, the grid continues to show the best strategy and
outlines the selected age pair; the adjacent summary identifies the exact
selected candidate. Optimal stars remain independent of selection.

The PDF contains all rows represented in the current ranked table, including
rows beyond the viewport, in the current table order. The deterministic table
preserves its grouped top-outcome presentation and equivalent-strategy counts;
its report identifies the group count and the complete evaluated universe.
The weighted table preserves original occurrences, ranks, and failed rows.
Wide tables are presented as matching complete-elections and financial-outcomes
tables, joined by occurrence ID, with repeated headers on subsequent pages.

PDFBox renders the grid and text directly from an immutable report model; the
application window is not printed or screenshotted. JavaFX and PDF share the
semantic tier colors in `ClaimingHeatMapPalette`; percentages, dollar formatting,
unavailable values, exact optimum markers, and selection are captured from
the completed presentation data. Run-time Person mortality categories and
independent mortality factors are frozen with weighted results. Later edits
cannot change the assumptions in an already captured report.

The normal Save dialog selects the destination and handles overwrite prompts.
The report is rendered to a temporary file before replacing the destination;
an export error is reported without invalidating analysis results. No PDF
library, financial calculation, ranking, or persistence changes are introduced.
