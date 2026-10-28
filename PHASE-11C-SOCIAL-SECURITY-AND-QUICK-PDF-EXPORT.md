# Phase 11C — Social Security Only and Quick Comparison PDF Export

## Implementation status

Both new exports are implemented for review and native Windows acceptance. Phase 11D has not begun. Native Phase 11B acceptance is user-confirmed; Phase 11C native acceptance remains pending.

The earlier historical-household decision gate was resolved explicitly by the user. Compatible legacy SS results remain exportable, with `Original household name was not recorded.` rather than a name inferred from the current plan. This report supersedes the earlier preparation/blocker report.

## Baseline and preserved work

- Branch: `main`.
- HEAD: `e620bec4a0011ab12d15fce670bff5011093d9da`.
- Initial tree: eight modified tracked Phase 11B source/test files and seven untracked Phase 11A/11B files, plus this pre-existing Phase 11C preparation report; empty index. All are preserved.
- Read AGENTS.md, Phase 11A audit, Phase 11B implementation/correction report, Phase 9 historical PDF/restoration evidence and Phase 10D parameter report. Inspected current accepted callbacks, snapshot mappers, frozen context, reporting, palette, grid adapter and export infrastructure.
- Relevant pre-edit baseline: 30 tests passed, zero failures/errors/skips; 31.260 seconds (`target/phase11c-baseline.log`).

## Exact Phase 11C file inventory

Modified production files (all under `src/main/java/com/daviddunn/retirementplanner/`):

1. `app/export/IntegratedAnalyzerReport.java` — explicit SS Stage 1 grid and table-only visualization variants.
2. `app/export/IntegratedAnalyzerPdfExporter.java` — rendering/pagination for the new variants using the existing PDFBox infrastructure; existing variant rendering stays on its original path.
3. `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java` — frozen report preparation/acceptance, historical attachment, two export actions, readiness, ownership and background export.
4. `ui/socialsecurity/WeightedResultPersistence.java` — read-only current assessment accessor for safe SS exportability; existing Phase 11B navigation hooks retained.

New production files in `ui/socialsecurity/`:

5. `AnalysisPdfData.java` — shared pure display formatting, complete elections and explicit fourteen-metric accessor list.
6. `SocialSecurityReportSession.java` — immutable accepted SS context, original names/times and legacy-name policy.
7. `SocialSecurityOnlyReportAdapter.java` — pure SS result-to-report mapping.
8. `QuickReportSession.java` — immutable, compact accepted baseline/candidate metrics, elections, provenance, failures and warnings; no plan or Projection retained.
9. `QuickComparisonReportAdapter.java` — pure original-order, table-only report mapping.

New test:

10. `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAndQuickPdfTest.java`.

Updated report:

11. `PHASE-11C-SOCIAL-SECURITY-AND-QUICK-PDF-EXPORT.md`.

Existing Phase 11B changes are cumulative in the working-tree diff; they were not reimplemented or removed. In particular the shared grid, palette, default tabs, navigation guard and deferred reveal behavior are preserved.

## Report model and PDF architecture

The existing sealed report visualization contract now explicitly permits `SocialSecurityGrid` and `TablesOnly`, alongside the unchanged integrated `HeatMap` and individual `Individual` variants. SS grid cells retain exact source BigDecimals plus display strings and palette/marker flags. The grid validates axes and complete unique age-pair coverage. Quick has an actual table-only variant, not a null placeholder or a fabricated heat map. New render branches never cast these variants to an integrated heat map.

The existing PDFBox landscape pages, fonts, pagination, wrapping, alternating table rows, repeated headings, footer/page numbering, PDF exporter and file chooser conventions are reused. Existing integrated/individual rendering remains unchanged; footer code was extracted without changing its contents. No dependency, snapshot schema, fingerprint, financial calculation or Phase 11B UI change is required.

## SS original context and household-name decision

Fresh preparation captures the actual household name, original request/context, start time, owner binding and run UUID. The eligible accepted callback associates the immutable presentation, original context and accepted completion time with that preparation. PDF export never reads the current name, mortality controls or valuation controls for historical assumptions.

The existing `SnapshotContext.household` field is now populated with that captured name on new SS snapshots. The existing bounded `request` sections also record `Original household name` with that exact captured string. This is ordinary supported report metadata, not a new schema field/version or a fingerprint input. The codec, repository, restoration and Save As tests prove that this data survives exact persistence and reconstruction.

Legacy snapshots whose household is the ambiguous literal `Couple`, without that original-name section, export using the approved disclosure. Original participant names are retained. A newly captured actual name `Couple` is trusted only when the matching explicit section survived persistence. No legacy provenance is invented and no old file is rewritten. Other historical recorded names remain their saved names. Name changes alone do not make a financial result Outdated under existing fingerprints; report names nevertheless remain the original names after rename and Save As.

## SS PDF contents

- Original household or missing-name disclosure, participant names, run/completion/export times, result identity and Current/Outdated/input-attention status.
- Honest result durability: Saved, Pending, Save Failed or Memory Only; PDF availability does not imply a successful snapshot commit.
- All original Stage 1 age pairs and exact PV source values. Primary ages are horizontal, spouse ages vertical. Dollar display uses existing formatting.
- Existing palette and unrounded-percentage tiers; vector star for exact optimum and independent triangle for Stage 2 retention; combined markers remain visible. The export-time selection has a blue outline and is explicitly labeled as an export-time choice.
- Full legend including unavailable percentage behavior for a zero/nonpositive optimum. No denominator convention or fabricated values were added.
- Stage 1 optimum and selected-pair context, original best expanded Stage 2 strategy, full survivor elections, expected PV/real/nominal distinctions and tie counts.
- Every original ranked complete strategy in original order/ranks, split into complete-election and expected-value tables; no visible-row truncation.
- Original mortality table/category/factors, conditioning/valuation, discount/COLA, optimization settings/counts, benefit records and methodological limitations.

A completed historical result remains exportable when next-run text is invalid. Missing or incompatible results and lost ownership disable export with a readable explanation. Existing assessment is read, not replaced or forced Current.

## Quick original context and export policy

Before admission, capture the original financial report assumptions/names/start time, selected candidates in SS order, SS ranks and exact SS values, requested count, prerequisite fingerprint/run identity and its frozen assumptions. The final eligible success callback extracts the original baseline, compact metrics/differences, full candidate elections, counts, structured failures, advanced SS path flags, warnings and completion time into an immutable report session. No current-plan assumptions or newly completed SS prerequisite are substituted later.

Only the existing accepted-success policy can replace the session. Canceled, obsolete or fatal replacements retain it. An all-unsuccessful comparison now explicitly retains the previous accepted comparison/report rather than rendering a nonaccepted replacement. This applies the established success policy, without changing evaluation, ranking or financial semantics.

Quick results remain memory-only. No new kind/persistence callback, result slot, snapshot payload or reconstruction path was added. Accepted parameter configurations do not fabricate a Quick result after restart. New Quick runs still require the original current-compatible SS prerequisite. A stale prerequisite can block a new run while the safely owned previous report remains exportable with the explicit historical notice.

PDF contents:

- Original names/times, Current/Outdated notice, memory-only limitation, original prerequisite fingerprint/run and original requested/supplied/unique/successful/failed counts.
- Original baseline complete elections and original available baseline metrics.
- Supplied candidate basis in original order, original SS ranks/PVs, compared entries in first-seen deduplicated caller order and complete retirement/survivor dates.
- Separate status/failure table and paginated financial comparisons for all fourteen existing metrics, with original candidate-minus-baseline differences. Failed results are explicitly unavailable, never fabricated.
- Original warnings, advanced-path flags, financial and prerequisite assumptions and limitations. No integrated winner/ranking or heat map is invented.

## Concurrency and no-recalculation boundary

The FX thread captures only the immutable accepted session, existing assessment/durability strings and selected age pair. A supplier formats the detached report on the background export task, where PDF generation and output writing also run. Existing export busy-state conventions prevent overlapping exports.

Eligibility is checked before and after the file chooser/overwrite confirmation. Captured plan/session binding and accepted-session identity protect completion/error UI callbacks; disposed/closed views and wrong-plan bindings cannot export or receive obsolete success alerts. An already captured immutable report may finish writing after closure without reading another plan.

Accepted callbacks continue through the existing job generation, revision, cancellation and admission gates. Neither adapter has an engine, optimizer, calculator, controller or repository dependency. Historical sessions are restored through existing validated snapshots. Restoration tests use a throwing SS calculation factory and assert zero job generations; report preparation/export runs without financial work. Exports do not mutate the plan, accepted parameters, snapshot bytes, revisions or compatibility.

## Automated evidence

Focused classes:

`SocialSecurityAndQuickPdfTest, IntegratedAnalyzerPdfReportTest, IntegratedAnalyzerPdfStateTest, IndividualReportTest, ExhaustiveHistoricalPdfFidelityTest, CoupleSocialSecurityPersistenceTest, ExhaustivePersistenceTest, WeightedRestorationTest, ResultNavigationTest, SharedClaimingAgeGridTest, SocialSecurityParameterPersistenceTest, MonteCarloParameterPersistenceTest, QuickComparisonPrecisionTest, LegacyWeightedSnapshotCompatibilityTest, MonteCarloHistoricalPdfFidelityTest`.

Focused result: **161 total, 160 passed, 1 existing opt-in skip, zero failures/errors; BUILD SUCCESS; 1 minute 8 seconds** (`target/phase11c-focused.log`). The initial new-export-only run passed all nine cases. Final full-suite result will be recorded below after completion.

New tests verify legacy Current/Outdated exports and disclosure; participant/PV/rank/time fidelity; invalid pending text; original snapshot bytes/revisions unchanged by export; full 81-cell grid and combined indicators; exact optimal ties/zero percentage unavailability; complete ranked output; original-name persistence/restart/rename/Save As; explicit real-name `Couple` provenance through the existing codec with unchanged financial hash; failed verification/export/retry with intact previous bytes and no jobs; Quick original caller order/elections/all fourteen exact metric/difference records and BigDecimal scales; partial failures/counts/warnings; Current/Outdated report-table equality; stale prerequisite safety; canceled/all-failed/fatal retention; newer prerequisite isolation; wrong-plan rejection; no Quick persistence/restart fabrication; missing/incompatible export rejection with original file retained.

Existing suites additionally cover historical integrated/individual PDFs, all fifteen parameter identities, Monte Carlo V2 compatibility, legacy weighted byte/digest compatibility and Phase 11B navigation. Initial test expectations that a name-only change should make results Outdated were corrected: existing financial semantics intentionally exclude that nonfinancial label. The actual Outdated test changes a mortality factor instead. No financial behavior or golden expectation was changed.

Full Maven: **3,442 total; 3,398 passed; 44 skipped; zero failures/errors; BUILD SUCCESS; 10 minutes 2 seconds** (`target/phase11c-full.log`). Used IntelliJ bundled Maven with the required project-local repository and `-DargLine=-Xmx4g`; no test exclusions were added. Skips are the existing opt-in preview/capacity/benchmark cases, including the large historical Monte Carlo PDF test (`mc.pdf.historical.large`); none were disabled for this change. No golden files were changed. The final export rerun also passed: **9 total, 9 passed, zero failures/errors/skips; 21.909 seconds** (`target/phase11c-final-export-tests.log`), after correcting a test-only notice string. Production sources are unchanged from the full-suite run.

## Rendered PDF inspection

Generated deterministic test PDFs and actual PDFRenderer PNG pages under ignored `target/phase11c-pdf/`; no personal production financial data was used. Inspected images, not just generation logs:

- `ss-full-grid-page-3.png`: all 81 cells, primary/spouse orientation, five color tiers, readable dollar cells, selected blue border and independent combined star/triangle markers.
- `ss-full-grid-page-4.png`: grid explanation, mortality source/category/factors, dates, valuation, search settings and original record text; readable wrapping and pagination.
- `ss-legacy-outdated-page-1.png`: prominent historical notice, missing-household disclosure, original participants/timestamps, Stage 1/Stage 2 distinctions and export-time selection.
- `quick-outdated-page-1.png`: original prerequisite and times, historical/memory-only notices, original baseline and assumptions.
- `quick-current-page-3.png` and `page-4.png`: separate supplied-candidate and complete-survivor-election tables, original order and readable date columns.
- `quick-outdated-page-8.png`: paginated fourteen-metric table, explicit failed values, warnings and limitations.

The first rendering exposed insufficient spacing between the last financial table row and the warnings heading. Added 24 points of separation in the new-variant path only, regenerated and inspected the corrected image; no overlap remains in that case. Existing report layouts were not redesigned.

Reports deliberately include the complete ranking: the full-grid fixture produces 39 pages, while the Quick three-candidate example produces 8 pages. Large rankings may create lengthy PDFs; this is not truncation. The existing long-token wrapping remains in use. This is representative rendered verification, not a Windows print/DPI/screen-reader certification or an exhaustive inspection of every page of every ranking.

## Native Windows acceptance checklist

1. Rebuild/start normally; open a saved couple plan. Run SS Only with distinctive factors/dates/rate. Confirm Phase 11B still selects Claiming Grid and its styling/values are unchanged.
2. Export SS PDF, inspect grid, combined markers, selected pair, complete rankings/elections, original assumptions and timestamps. Confirm PDF output does not start a calculation.
3. Restart/open the same plan and export Current historical SS results. Change relevant temporary assumptions and export Outdated; original financial values/names/times must stay fixed. Invalid next-run text must not block the safe historical export.
4. Open a legacy saved SS result. Confirm the exact missing-household disclosure and original participant names; no current plan name should replace the missing historical label.
5. Rename the plan household after a new SS run, then Save As/reopen original and copy. Confirm both PDFs retain the original accepted report name.
6. With a current SS prerequisite, run Quick using a distinctive candidate count. Export and check SS order/ranks, full survivor elections, original baseline, all available metrics/differences, counts and failures/warnings.
7. Change relevant assumptions or complete a newer SS run. The old Quick PDF must preserve its original prerequisite/data and show Outdated as appropriate; stale prerequisites still block invalid new runs.
8. Cancel/fail a replacement. Previous accepted report remains usable. Switch plans/close/reopen; Quick must not be fabricated or exported under another owner, while persisted SS restores normally.
9. Verify Pending/Save Failed/Memory Only text when applicable and Retry Save without rerunning. Check SS and Quick exports do not change the plan modified flag or accepted parameters.
10. Spot-check existing Exhaustive, Weighted, individual and Monte Carlo PDF/report behavior. Phase 11C native acceptance is pending until the user performs these checks.

## Known limits and scope

- Legacy missing household names cannot be recovered and are disclosed, never guessed. Existing files are not migrated.
- Quick has no cross-session financial-result restoration; PDF availability ends with its owned accepted in-memory context/view. Its prior stale-state contract remains conservative; no new Quick fingerprint semantics were introduced.
- Export buttons are disabled during active analysis/export according to the established busy convention, while previous results stay visible.
- Representative PDFs are fully paginated but may be long; no new metric, optimizer recommendation, snapshot format, or PDF library was added.
- Native file chooser/overwrite/Windows-print interactions require user acceptance; no native certification is claimed.

## Git and release scope

No staging, commit, push, reset, clean or discard. Empty index. No new dependency, financial algorithm, golden file, random stream, persistence schema/version/limit, historical hash contract, or Quick result persistence changes.

The final working-tree inventory includes the eleven Phase 11C files above and preserved Phase 11A/11B work listed below. Generated PDFs/PNGs/logs stay under ignored `target/` and must not be included in Git. Phase 11D has not begun.

Preserved modified tracked Phase 11B files not newly changed by Phase 11C:

- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerNavigationView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ClaimingStrategyHeatMapView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IntegratedAnalysisResultTabs.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/LongevityWeightedIntegratedView.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/CoupleSocialSecurityPersistenceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ExhaustivePersistenceTest.java`

Preserved untracked Phase 11A/11B files:

- `PHASE-11A-SOCIAL-SECURITY-UI-PDF-AUDIT.md`
- `PHASE-11B-SHARED-GRID-AND-NAVIGATION.md`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ClaimingAgeGridView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationGuard.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityClaimingGridAdapter.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/ResultNavigationTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SharedClaimingAgeGridTest.java`

Final verification: full suite and final export rerun passed; `git diff --check` exit 0. Git status: **10 modified tracked files, 14 untracked files, zero staged files**, including all preserved Phase 11A/11B work. The eleven Phase 11C files and thirteen preserved files are inventoried above. Branch/HEAD unchanged. Ready for review and native Windows acceptance; stop before Phase 11D.