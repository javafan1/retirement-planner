# Phase 19C — Break-Even Social Security Reduction Disclosure

## Executive summary

Added a read-only Social Security benefit reduction disclosure to the existing Break-Even comparison summaries and PDF assumption blocks. Current and baseline values are derived independently from each accepted projection's frozen, digest-validated plan inputs. Nonzero policies display as “Social Security benefit reduction: R% beginning Y”; a trusted zero reduction adds no line. Missing or invalid provenance is never replaced with the currently loaded plan's settings.

No Break-Even calculations, projection values, crossover years, sustained Break-Even rules, charts, or metric definitions were changed. The existing Phase 17 View/Save/Cancel export path and Phase 18A export directory behavior are untouched.

## Architecture and provenance findings

The CompleteProjectionSnapshot inputsDocument is the frozen RetirementPlanSnapshot used by the projection, and inputDigest authenticates that document. The saved baseline independently retains a RetirementPlanSnapshot. These are the appropriate sources; the current plan cannot safely supply historical or baseline settings.

BreakEvenAnalysisResult already carries separate immutable baseline/current BreakEvenPlanSummary instances through BreakEvenAnalyzer. The UI and BreakEvenPdfReport already consume those per-side summaries. The smallest integration therefore enriches that existing metadata at controller projection restoration/acceptance, leaving the analysis result and its consumers comparison-specific.

CompleteProjectionSnapshot.restoreBreakEvenAssumptions() first restores inputs through the existing digest check, derives the policy from those inputs, checks any in-memory denormalized policy against it, then returns the existing people summary with that policy. An invalid or unreadable frozen input is rejected. A legacy frozen input that genuinely omits the Phase 19 fields is decoded by the established compatibility defaults (0% / 2032), which is trustworthy evidence of the pre-feature no-reduction assumption.

### Historical and compatibility behavior

- Accepted current projections, restored history entries, and saved baselines each derive their own policy from their own frozen inputs.
- Current/Baseline swapping swaps their associated summaries with their projections; it does not read current settings.
- A legacy pre-Phase-19 snapshot with valid frozen plan inputs obtains 0% / 2032.
- Corrupt or unavailable provenance is not treated as zero and is not backfilled from live settings. The presentation explicitly says the reduction is unavailable for that projection; malformed frozen inputs fail the restoration path through existing error handling.
- Projection IDs, fingerprints, digests, snapshots, and persisted JSON formats are unchanged.
- BreakEvenPlanSummary remains serialized with its historical primary and spouse properties. Reduction policy is transient in-memory metadata because the complete projection inputs are the persisted provenance. Equality and hash code retain the original two-field value semantics.

## Implementation

### UI

The existing Current and Baseline information area now has a compact read-only disclosure directly under each side's existing claiming-age summary. The two labels have distinct IDs for testing and use the existing theme text color. No disclosure is created for a trusted 0% assumption. The comparison chart, its markers, four metric cards, calculation boundaries, dialog actions, and scroll container are unchanged.

### PDF

BreakEvenPdfExporter appends each side's disclosure within its existing plan-assumption paragraph. Both nonzero assumptions are separately labeled by the existing BASELINE PLAN and CURRENT PLAN headings. Zero assumptions add no text. No new report section or page is introduced solely for this line; the existing page-count assertion remains in place. PDF rendering consumes the already accepted BreakEvenAnalysisResult and does not calculate projections or rerun Break-Even analysis.

## Files changed

Production:

- src/main/java/com/daviddunn/retirementplanner/domain/breakeven/BreakEvenPlanSummary.java
- src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java
- src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java
- src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/SinglePersonIntegratedAnalysis.java
- src/main/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenPresentation.java
- src/main/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenAnalysisView.java
- src/main/java/com/daviddunn/retirementplanner/app/export/BreakEvenPdfExporter.java
- src/main/resources/css/break-even.css

Tests:

- src/test/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenReductionDisclosureTest.java (new)
- src/test/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenCompactLayoutTest.java
- src/test/java/com/daviddunn/retirementplanner/ui/controller/BreakEvenControllerTest.java
- src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloCapacityProbeTest.java (trusted test-only logical encoder/decoder now handles the immutable two-property summary class)

## Verification

### Focused tests

The final focused run covered Break-Even disclosure, controller, view, compact layout, PDF export and workflow, analyzer, projection persistence/swap, and Phase 12 analyzer snapshot compatibility:

- 100 tests: 100 passed, 0 failures, 0 errors, 0 skipped.
- A subsequent compatibility-focused run included the Monte Carlo capacity probe and snapshot codecs: 26 tests, 0 failures, 0 errors, 2 opt-in cases skipped.

The new tests cover independent 22% / 2032 versus 17% / 2034 assumptions, current-only and baseline-only disclosures, zero/zero omission with differing effective years, BigDecimal scale equivalence, strict legacy two-property JSON round-trip, frozen current/baseline and role swaps, legacy defaults, corrupt provenance rejection, UI labels, all four PDF disclosure combinations, unchanged Break-Even metrics, and chart/summary presentation.

### Visual verification

Actual JavaFX snapshots were generated and inspected at 900×650 and 1180×820 in Light and Dark themes:

- target/break-even-reduction-disclosure/break-even-light-900.png
- target/break-even-reduction-disclosure/break-even-light-1180.png
- target/break-even-reduction-disclosure/break-even-dark-900.png
- target/break-even-reduction-disclosure/break-even-dark-1180.png

The new disclosures appear on the correct side, remain readable, and retain existing scrolling and chart layout. The four PDF variants were exported, text-checked, rendered to page images, and inspected:

- target/break-even-reduction-disclosure/different-page-1.png
- target/break-even-reduction-disclosure/current-only-page-1.png
- target/break-even-reduction-disclosure/baseline-only-page-1.png
- target/break-even-reduction-disclosure/zero-page-1.png

No overlap or unintended extra pagination was observed. The zero/zero PDF contains no reduction disclosure. Native Windows DPI acceptance is still required.

### Full regression

The first complete suite run exposed one test-only failure: MonteCarloCapacityProbeTest assumed every immutable metadata value was a Java record. It did not report a production serialization or financial failure. The test helper was updated to represent BreakEvenPlanSummary using its retained primary/spouse wire contract.

The first full run after that test correction passed the focused tests but exposed one unrelated timing failure in Stage5DComparisonTest.callbackFailureCleansUpOutstandingWorkers (4,038 tests, 1 failure, 0 errors, 46 skipped). The failing test passed independently (23 tests, 0 failures/errors/skips). A subsequent complete Maven run passed: 4,038 tests, 0 failures, 0 errors, 46 skipped. The skips are existing opt-in benchmarks and visual previews.

git diff --check completed with no whitespace errors. Git emitted only line-ending normalization notices.

## Financial and lifecycle invariants

All Break-Even financial values, four metrics, first-crossover and sustained Break-Even calculations remain unchanged. New coverage asserts analysis metrics are identical before/after presentation and PDF disclosure. Export uses the accepted result object; it does not recalculate. The Phase 17 shared PDF workflow and configured Save directory were not modified.

## Known limitations

The UI can display an explicit unavailable-provenance notice only when it receives a summary without policy metadata. Normal accepted/restored projection paths use frozen plan inputs and compatibility defaults. Historical analyzer payloads that contain only the legacy people summary and lack a trustworthy associated frozen plan snapshot cannot prove a nonzero policy; they must remain unavailable rather than use live settings.

This test environment runs JavaFX on Windows 11 and generated actual JavaFX/PDF renderings, but it does not replace user acceptance of the packaged application, actual saved-plan history, Windows display scaling, and native PDF viewer/workflow behavior.

## Native Windows acceptance checklist

- Compare Current and Baseline with different nonzero reduction settings.
- Swap Current/Baseline and verify each disclosure follows its projection.
- Restore an older projection and verify its frozen settings are shown.
- Verify a legacy pre-Phase-19 projection displays no reduction line (0%).
- Verify corrupt/unavailable provenance is never represented as zero or current settings.
- Inspect Break-Even UI and exported PDF in Light/Dark themes.
- Confirm PDF View/Save/Cancel and default export directory behavior are unchanged.
- Confirm all existing Break-Even values and chart behavior are unchanged.

## Git status

The starting branch was main at origin/main, with a clean working tree. No pre-existing user changes were present at task start. The final working tree contains only the Phase 19C production/test changes and this report. No files were staged, committed, pushed, reset, cleaned, or reverted.
