# Phase 17B5 — PDF Export Final Reconciliation

## Executive summary

The application-wide source audit found that all identified user-facing PDF export actions now use the accepted Phase 17B1 View/Save/Cancel workflow. This includes projection, Break-Even, Social Security analyzer, Monte Carlo and historical associated-result reports. The View action renders a managed preview; Save retains each workflow's original chooser and write behavior; Cancel does not capture or render a report.

One latent bypass remained in `ProjectionAnalyzerHistoryDialog`: unsupported history content could fall through to a direct Save chooser and the legacy exporter. Supported current history records already provide a frozen report, so this was not normally reachable, but it could violate the shared-workflow contract. The fallback was removed, and unsupported content now has PDF export disabled with an explanatory message. Focused regression coverage was added.

No financial model, calculation, PDF renderer, report content, persistence schema, or export framework was changed. Phase 17 is ready for final native Windows acceptance and closure.

## Inventory and coverage matrix

Every row below is a user-facing PDF action. All use `PdfExportWorkflowCoordinator<R>` through `PdfExportWorkflowFactory`, with the shared choice dialog, preview store, and opener. “No rerun” means export consumes a completed result/report snapshot and does not launch the underlying analysis.

| Entry point / report family | View / Save / Cancel | Coordinator | No analysis rerun and result identity retained | Save compatibility | Relevant automated coverage | Native acceptance |
|---|---|---|---|---|---|---|
| Results Summary — Retirement Projection | Yes / Yes / Yes | Yes | Yes; selected projection context frozen | Existing chooser, name, direct-write behavior retained | `ProjectionPdfExportTest` | Accepted in B1–B4 testing; final review requested |
| Break-Even Analysis | Yes / Yes / Yes | Yes | Yes; selected comparison/report snapshot frozen | Existing chooser, extension and write behavior retained | `BreakEvenPdfWorkflowIntegrationTest` | Accepted in B1–B4 testing; final review requested |
| Couple Social Security — Deterministic Integrated | Yes / Yes / Yes | Yes | Yes; completed strategy/result and assumptions captured | Existing integrated Save behavior retained | `IntegratedAnalyzerPdfStateTest`, SS PDF tests | Accepted in B1–B4 testing; final review requested |
| Couple Social Security — Longevity-Weighted Integrated | Yes / Yes / Yes | Yes | Yes; completed weighted result and assumptions captured | Existing integrated Save behavior retained | `IntegratedAnalyzerPdfStateTest`, SS PDF tests | Accepted in B1–B4 testing; final review requested |
| Couple Social Security Only | Yes / Yes / Yes | Yes | Yes; selected completed result captured | Existing extension/collision policy retained | `SocialSecurityPdfWorkflowIntegrationTest`, report tests | Accepted in B1–B4 testing; final review requested |
| Couple Quick Comparison | Yes / Yes / Yes | Yes | Yes; comparison result and strategy context captured | Existing per-export Save policy retained | SS PDF integration/report tests | Accepted in B1–B4 testing; final review requested |
| Single-person Deterministic Integrated | Yes / Yes / Yes | Yes | Yes; selected completed individual result captured | Existing individual Save adapter retained | SS PDF integration/report tests | Accepted in B1–B4 testing; final review requested |
| Single-person Social Security Only | Yes / Yes / Yes | Yes | Yes; selected completed individual result captured | Existing individual Save adapter retained | SS PDF integration/report tests | Accepted in B1–B4 testing; final review requested |
| Single-person Longevity-Weighted Integrated | Yes / Yes / Yes | Yes | Yes; selected completed individual result captured | Existing individual Save adapter retained | SS PDF integration/report tests | Accepted in B1–B4 testing; final review requested |
| Historical associated Social Security results (couple SS-only, quick, exhaustive, weighted; individual deterministic, SS-only, weighted) | Yes / Yes / Yes | Yes | Yes; selected history item and associated projection identity frozen | Historical chooser behavior retained | `Phase12EAssociationTest`, SS PDF integration tests | Accepted in B1–B4 testing; final review requested |
| Standalone Monte Carlo — fixed horizon, supported household variants | Yes / Yes / Yes | Yes | Yes; completed simulation result/settings/seeds captured | Existing safe-write, naming and extension behavior retained | `MonteCarloPdfWorkflowIntegrationTest`, exporter tests | Accepted in B1–B4 testing; final review requested |
| Standalone Monte Carlo — longevity adjusted, supported household variants | Yes / Yes / Yes | Yes | Yes; completed simulation result/settings/seeds captured | Existing safe-write, naming and extension behavior retained | MC integration/exporter tests | Accepted in B1–B4 testing; final review requested |
| Monte Carlo Strategy Comparison — fixed | Yes / Yes / Yes | Yes | Yes; both selected results and comparison identity frozen | Existing comparison Save behavior retained | MC integration/comparison tests | Accepted in B1–B4 testing; final review requested |
| Monte Carlo Strategy Comparison — longevity adjusted | Yes / Yes / Yes | Yes | Yes; both selected results and comparison identity frozen | Existing comparison Save behavior retained | MC integration/comparison tests | Accepted in B1–B4 testing; final review requested |
| Historical standalone Monte Carlo results (fixed/longevity, supported household variants) | Yes / Yes / Yes | Yes | Yes; exact selected historical result and association captured | Existing historical chooser behavior retained | `Phase12EAssociationTest`, MC integration tests | Accepted in B1–B4 testing; final review requested |
| Historical paired Monte Carlo results (fixed/longevity, supported household variants) | Yes / Yes / Yes | Yes | Yes; exact pair and historical association captured | Existing historical chooser behavior retained | `Phase12EAssociationTest`, MC integration tests | Accepted in B1–B4 testing; final review requested |

**Coverage conclusion:** all production PDF export entry points identified in the complete source search are covered. CSV export and plan Save/Save As/Open choosers are not PDF exports and remain outside this workflow. Low-level `exportPdf(Path)` / exporter service methods remain callable by tests and internal code; they are rendering/writing APIs, not user-facing export actions. The old historical UI fallback that could invoke a low-level method directly has been removed.

## Shared workflow and result identity

The shared coordinator performs readiness checks before presenting the choice. Cancel exits before report capture, chooser, render, or preview creation. View does not show a Save chooser. Save obtains the destination before capture, so cancelling the native chooser does not render. Accepted work captures report inputs on the FX thread, renders and performs file I/O on the worker, then publishes UI completion on the FX thread. Busy/reentrancy state prevents duplicate requests.

Projection, Break-Even, Social Security and Monte Carlo integrations use frozen report inputs/results. Export does not rerun projections, Social Security search, longevity weighting, Break-Even calculations, Monte Carlo simulations, or random-number generation. Captured historical and paired reports keep their selected result/projection associations; later UI changes cannot substitute another report. Existing completion validity checks suppress stale success messaging while rendering failures remain reportable. A generated preview remains available if its originating view closes or the OS opener fails.

The audit found no export worker accessing live JavaFX controls. JavaFX-dependent data capture is performed before dispatch; PDF rendering and file operations are off the FX thread. The production opener uses the shared injectable Desktop implementation; automated tests use fake openers and do not launch an external viewer.

## Content and Save compatibility

The B1–B4 changes route existing captured report data through a choice step and shared coordinator. Report exporter implementations and their titles, tables, charts, values, heat maps, colors, page layout, disclosures, and metadata conventions remain unchanged. Existing PDF content and financial golden assertions passed. View and Save call the same report renderer for a given capture.

Save remains intentionally export-specific: prior chooser titles, suggested names, extension normalization, initial directory, overwrite/collision behavior, direct versus sibling-temp-and-move writes, and success/error conventions were preserved. The audit did not force formerly different exporters into one naming or overwrite policy. Results Summary’s existing PDF/CSV format selection and CSV export are unchanged.

## Preview store security and lifecycle

The shared `PdfPreviewStore` uses the existing per-user Aledrix application-data location (Windows: `%LOCALAPPDATA%\\RetirementPlanner\\pdf-previews\\v1`, with the documented profile fallback), unpredictable UUID filenames, an ownership marker and per-preview sidecar. Rendering uses an incomplete `.pdf.part` path; only a validated completed PDF is published and passed to the opener. Creation requests private permissions where supported. Cleanup is bounded to verified store-owned files older than 30 days, does not follow symbolic links, tolerates locked or inaccessible files, and does not remove unrelated content. Concurrent processes use unique names; publication does not overwrite an existing preview. Viewer failure retains the completed PDF and reports its path. Save does not use or depend on preview storage.

No defect requiring a storage-policy change was found. Reparse-point and ACL behavior remains subject to the already completed native Windows acceptance; automated tests cover path/ownership and symlink safety where supported by the platform. Stale preview cleanup is best effort and may leave locked files for a later run.

## Lifecycle, errors, and accessibility

The shared choice UI presents View PDF as the default, with Save PDF and Cancel visible. Escape and window close cancel. Theme-aware styling and keyboard focus are covered by JavaFX tests and generated Light/Dark previews. Rendering and viewer-launch failures are distinct; viewer failures retain a complete preview and provide its location. Busy state restores after worker completion/failure. Shutdown and closed-view guards avoid stale success presentation without invalidating a completed file.

Expected injected failure logs from persistence/fault-injection tests during the full suite do not indicate PDF workflow failures; those tests pass. No PDF-specific worker, identity, or UI defect remained after the historical fallback correction.

## Defect classification and changes

- **A — Verified correct:** shared choice/coordinator flow, result capture, renderer reuse, save adapters, preview publication/cleanup, opener behavior, and existing PDF content for the reviewed export families.
- **B — Minor defect corrected:** unsupported historical content could fall through to the legacy direct Save chooser. Such content is now non-exportable through the UI; supported frozen reports continue through the shared workflow. Regression coverage verifies the gate.
- **C — Significant defect:** none found.
- **D — Deferred/out of scope:** CSV export; low-level direct-path rendering APIs; any future history payload type without a frozen report representation. Unsupported future history payloads are now clearly unavailable rather than bypassing the shared flow.

Phase 17B5 changes are limited to:

- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/ProjectionAnalyzerHistoryDialog.java` — remove legacy UI fallback and gate on frozen report availability.
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityPdfWorkflowIntegrationTest.java` — assert history export readiness for frozen/unsupported content.
- `src/test/java/com/daviddunn/retirementplanner/ui/pdf/PdfExportChoiceDialogPreviewTest.java` — add single-person Social Security owner-context previews in Light and Dark themes.
- This report.

The working tree also contains Phase 17B1–B4 implementation files and reports. They were pre-existing and preserved; no unrelated changes were reset, cleaned, staged, committed, or pushed.

## Visual verification

Actual JavaFX previews were generated and inspected for representative Projection, Break-Even, couple and single-person Social Security, standalone and comparison Monte Carlo, historical analyzer, busy, viewer-error, and render-error workflows in Light and Dark themes. The View/Save/Cancel controls, default action, ownership context, wrapping, and failure messaging rendered without clipping. New single-person previews are under `target/phase17b3-social-security-pdf-choice/`, including:

- `single-person-deterministic-light.png` / `single-person-deterministic-dark.png`
- `single-person-social-security-light.png` / `single-person-social-security-dark.png`
- `single-person-longevity-weighted-light.png` / `single-person-longevity-weighted-dark.png`

Existing B2–B4 preview artifacts remain under their corresponding `target/phase17b*-...` directories. Actual external viewer launch and Windows DPI checks are native acceptance items; automated tests do not launch the OS viewer.

## Verification results

All tests used IntelliJ bundled Maven with the repository-local `.codex-m2\\repository`.

| Verification | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| Focused PDF infrastructure and integration set (coordinator/store/dialog, projection, Break-Even, Social Security/history, Monte Carlo) | 91 | 0 | 0 | 1 | Passed |
| Cached opt-in Monte Carlo PDF preview | 1 | 0 | 0 | 0 | Passed |
| Final focused JavaFX choice-dialog preview set | 5 | 0 | 0 | 0 | Passed |
| Complete Maven suite after final code and preview-test changes | 3,981 | 0 | 0 | 46 | BUILD SUCCESS |

The 46 skips are existing opt-in/conditional visual or fixture-based tests. No test was disabled or weakened, and no expected financial values or PDF golden values were regenerated. `git diff --check` passed; Git emitted only existing LF-to-CRLF working-copy warnings.

## Limitations and native Windows acceptance checklist

No unresolved correctness blocker was identified. As final acceptance, verify on the target Windows installation:

- View PDF opens each representative report in the configured default PDF viewer; missing association produces a clear retained-path message.
- Save PDF preserves the existing chooser, filename, extension, overwrite and collision behavior.
- Light/Dark choice dialogs remain legible at 100%, 125%, and 150% display scaling.
- Closing views, switching plans/results, and shutting down during an export do not show stale success or lose completed previews.
- Preview files reside in the managed per-user folder and stale cleanup does not remove unrelated files.

The user has stated that Phases 17B1–17B4 passed native Windows acceptance. Phase 17B5 adds no OS launch mechanism or visual policy change; this report requests final B5 review and Phase 17 closure.

## Git status and final recommendation

Branch: `main`. The pre-existing Phase 17B1–B4 modified and untracked work remains present, along with the narrowly scoped B5 changes above and this report. `git diff --check` is clean aside from line-ending warnings. Nothing was staged, committed, or pushed.

**Recommendation:** approve final native Windows acceptance and close Phase 17 after confirming the checklist above. Every identified user-facing production PDF export is covered by the shared workflow.
