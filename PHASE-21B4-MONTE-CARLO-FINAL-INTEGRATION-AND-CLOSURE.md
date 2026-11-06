# Phase 21B4 — Monte Carlo Final Integration, Reporting and Closure

## 1. Executive summary

Phase 21B4 reconciles the accepted standalone and paired stochastic-expense implementation against current source, history, reporting and export workflows. All eight household/mode/family configurations are implemented. Generated standalone and comparison runs support positive recurring-expense volatility without editing or suppressing the user's percentages. Enabled caller-supplied worlds/paths remain explicitly unsupported.

Three demonstrated disclosure defects were corrected: an obsolete defensive error recommended setting volatility to zero; enabled descriptions did not explicitly state the unbounded positive tail/expected escalated base; fixed-horizon PDFs did not explicitly identify household configuration. Changes are confined to presentation and report context. No financial algorithm, random protocol, snapshot/fingerprint contract, persistence schema or navigation changed.

Implementation classification: **Level 2 — presentation/reporting workflow**, with focused verification during development. One complete normal Maven suite was also run for the final Phase 21 milestone: **4,210 reported, 4,164 passed, 46 existing skips, zero failures and errors**. Final totals and recommendation are recorded in sections 14 and 18. Native Windows acceptance is required before formal closure; prior B1/B2/B3 native acceptance is user-confirmed background evidence.

## 2. Complete capability matrix and production inventory

Every row supports configured zero and positive volatility under existing valid-input, demographic and resource constraints. “Supported” does not override those legitimate restrictions.

| Production family / AnalysisKind suffix | Household | Zero / positive | Fixed seed / financial engine | Persistence / history / PDF |
|---|---|---|---|---|
| MONTE_CARLO_FIXED | Couple | Supported / supported | Existing generated fixed path / approved expense adapter | Supported |
| MONTE_CARLO_LONGEVITY | Couple | Supported / supported | Existing shared mortality/return/inflation protocols | Supported |
| MONTE_CARLO_FIXED | Single person | Supported / supported | Existing primary-only fixed path / expense adapter | Supported |
| MONTE_CARLO_LONGEVITY | Single person | Supported / supported | Existing primary-only mortality path / expense adapter | Supported |
| MONTE_CARLO_PAIRED_FIXED | Couple | Supported / supported | Existing paired worlds and independent candidate bindings | Supported |
| MONTE_CARLO_PAIRED_LONGEVITY | Couple | Supported / supported | Existing paired lifetime worlds and candidate bindings | Supported |
| MONTE_CARLO_PAIRED_FIXED | Single person | Supported / supported | Existing primary-only paired worlds and bindings | Supported |
| MONTE_CARLO_PAIRED_LONGEVITY | Single person | Supported / supported | Existing primary-only paired mortality and bindings | Supported |

For each comparison row, zero/zero, positive/zero, zero/positive, identical positive definitions and different positive definitions/percentages are supported. The existing twenty-case `SupportedPairedExpenseTest` matrix verifies all four comparison configurations against internal full-engine oracles. Existing public standalone tests cover the other four. Neither candidate supplies expense assumptions to the other.

Production entry points are the Analysis-menu standalone and Strategy Comparison introductions in `MainWindow`; `MonteCarloAnalysisDialog/View` and `MonteCarloStrategyComparisonDialog/View`; their RunServices and sessions; last-success lifecycle restoration; and Results Summary/Baseline Details → `ProjectionAnalyzerHistoryDialog`. Historical selection is by recorded projection/analyzer identity, not by current live inputs. PDF entry points are the existing Export PDF buttons in both analyzer dialogs and the MC history branch. There is no additional independent MC PDF implementation.

## 3. Remaining restriction inventory

Searched production Java/resources for positive-volatility prohibitions, zeroing instructions, temporary restrictions and expense-model terminology. Inspected introductions, input controls, tooltips, run feedback, details, history, PDF adapters, settings/directory context and core admission.

| Finding | Disposition |
|---|---|
| `ExpenseVolatilityAdmission.requireSupported` said execution was unavailable and instructed setting all percentages to zero | Corrected text only. It remains a defensive rejection for paths without the supported generated adapter. |
| Public generated standalone/comparison routing | Already correct in B2/B3; no obsolete positive-volatility UI block remains. |
| Enabled caller-supplied worlds/paths | Retained explicit UnsupportedOperationException before supplier use; unsupported provenance/coverage is not inferred. |
| Negative legacy expenses with positive volatility | Retained genuine validation; the conditional negative-amount notice is not a general Monte Carlo restriction. |
| One-time expenses | Retained exclusion and editor clearing notice. |
| Missing baseline, differing household composition/demographics/start or fixed death scenario, unsupported survivor/tax facts | Retained existing admission restrictions and explanations. |
| Bounds, busy/closed state, cancellation, source revision and storage validation | Retained. |
| Introduction screens / Application Settings | No obsolete expense restriction found; no unnecessary new controls or settings added. |
| V1 artifact with positive configured volatility but no authenticated capability | Preserved, explicitly unavailable for supported interpretation; not silently upgraded. |

## 4. B4 corrections and exact files

Production paths are relative to `src/main/java/com/daviddunn/retirementplanner/`:

- `app/montecarlo/ExpenseVolatilityAdmission.java`: replace only the obsolete failure text with a supported-generated-provenance requirement. Predicate, exception class and callers unchanged.
- `app/montecarlo/ExpenseExecution.java`: append one explanatory presentation paragraph shared with the paired description. Existing four disclosure paragraphs and their ordering remain intact.
- `app/montecarlo/PairedExpenseExecution.java`: append that same paragraph after existing candidate/protocol/correspondence disclosures.
- `ui/dialogs/ExpenseDialog.java`: clarify the annual relative standard deviation, expected escalated base, unbounded positive tail, independence and deterministic/one-time exclusions in the existing tooltip.
- `ui/montecarlo/MonteCarloPdfReportAdapter.java`: add one compact household line from immutable run/assumption metadata to each report family. This is the sole intentional zero-volatility report-content addition. Existing titles, values, chart models, tables, colors and sections remain.

Tests:

- New `app/montecarlo/MonteCarloClosureReportingTest.java`: 25 cases covering all eight families at zero/positive volatility, original input reporting, actual PDF output, older enabled display text, unchanged snapshot bytes/fingerprint/numerical records and retained defensive admission.
- New `ui/montecarlo/MonteCarloClosurePdfWorkflowTest.java`: eight cases, each exercising Cancel, View, canceled Save chooser and accepted Save, with actual exporter, fake opener, configured-directory preparation and extracted View/Save text equivalence.
- Extended `ui/dialogs/ExpenseVolatilityUiTest.java`: retain existing normal/guided precision/validation assertions and verify accurate tooltip terminology in both themes.
- This report. Ignored synthetic previews/inspection harnesses/logs are under `target/phase21b4`.

No dependencies, build configuration, golden values or permanent test exclusions changed.

## 5. Mathematics and random streams

Current source retains `INDEPENDENT_LOGNORMAL_EXPENSE_LEVEL_V1`. Configured v is annual relative standard deviation, with log variance ln(1+v²), log mean minus half that variance and multiplier exp(mu+sigma Z). Expected multiplier is one before ordinary financial rounding. The positive tail is unbounded; percentages are neither an inflation rate nor a guaranteed range. Annual expense-level shocks do not compound across years.

Expense dimension/version remains 6/1, correspondence version 1, using keyed SHA-256 and absolute calendar years. Market, mortality and inflation streams are unchanged. One-time expenses are not sampled; deterministic contexts have no expense path. No new distribution, latent-normal correspondence, monthly draw, correlation or durable expense identity was introduced.

Exact normalized financial definitions (type, amount, dates, escalation, volatility and duplicate ordinal) receive corresponding shocks. Definition edits are intentionally unmatched; descriptions, UUIDs and list positions do not establish identity. Economic/lifetime/inflation worlds keep their existing shared comparison semantics. Each side binds to its own original expense list.

## 6. Zero-volatility compatibility

Inspected the analyzer dispatch and `InternalExpenseRun.apply`: disabled runs use the original branch, without generator/path construction, expense draws or multiply-by-one expense arithmetic. Generator known-answer vectors, all eight disabled execution identities, exact complete-result financial goldens, legacy reencoding and V1 fingerprint tests are included in verification. None of those expected values were regenerated.

Zero-volatility reports gain only the explicit household label. They receive no enabled-expense model paragraph, percentages or tail explanation. The new sixteen-case report matrix asserts this and preserves original numerical records, chart models and section content across restoration. Existing historical PDF fidelity checks compare restored report text/page counts/pixels, independently of nondeterministic PDF metadata. Cached legacy report fixtures are not rewritten or regenerated.

## 7. Positive-volatility financial integration

The approved Phase 14 engine remains authoritative: expected escalated recurring amount → immutable scenario/year multiplier → existing first-year proration and survivor adjustments → ordinary household cash funding. That feeds withdrawals, RMD use and later balances, retained assets, Roth/bracket fill, federal/state taxes, Medicare/IRMAA, portfolio growth/survival, net worth and estate through the same engine.

`InternalExpenseRun.apply` generates one immutable scenario path outside financial evaluation. The same bound multiplier is reused for repeated candidate/scenario/year evaluation, including tax-funding and Medicare retries. No second expense calculator or new projection formula exists in reporting or orchestration.

Executable evidence includes `StochasticExpenseProjectionTest` fixed-budget oracles, `StochasticExpenseMonteCarloTest` direct-engine oracles, `SupportedStandaloneExpenseTest` and `SupportedPairedExpenseTest`, plus existing tax/RMD/Roth/Medicare and financial regression suites. These check reconciliation, not an assumed direction for taxes or conversions.

## 8. Snapshot, fingerprint and version compatibility

| Result contract | Payload | Envelope | Stored fingerprint | Capability |
|---|---:|---:|---:|---|
| Original disabled standalone/comparison | 1 | 1 | 1 | None; read-only TEMPORARY_NUMERIC_V2 applicability remains separate |
| Enabled generated standalone | 2 | 1 | 3 | ExpenseExecution V1 |
| Enabled generated comparison | 3 | 1 | 4 | Ordered PairedExpenseExecution V1; each side enabled or explicit disabled |

Current strict decoder requires all original creator properties, permits only the defined optional capability roots, and rejects unknown/duplicate/invalid combinations. Enabled envelopes require the exact payload-specific algorithm map; global legacy maps remain unchanged. The codec verifies original request digests, capability bindings, financial/context fingerprints, timestamps and payload digest. Candidate manifests cannot be swapped or omitted when required. The Phase 19 S3 narrowly bounded legacy Social Security creator/digest correction remains unchanged.

Limits remain 32 MiB raw, 16 MiB compressed, depth 80, two million tokens, 100,000 arrays, 256,000-character strings and 4,096-character numbers, plus existing financial numeric/observation constraints. No new version, schema migration, compatibility loosening or fingerprint redefinition occurred in B4.

## 9. History and immutable provenance

B2/B3 history/controller tests exercise all eight identities through accepted association, ordinary Save, Save As with independent ownership, restart, edits and both projection-swap directions. They verify original UUID/digest pinning and use throwing restored workers to prohibit recalculation. Enabled comparison tests retain independent original 25%/17% even after live Current/Baseline edits. Standalone tests retain original 25% after live changes to 17%.

B4 adds original-input report checks after detached expense replacement/removal and baseline clearing. More importantly, eight older-enabled-artifact cases remove only the newly added presentation paragraph from the saved Display, encode/decode the artifact, and then restore it. The existing read-only adapters add the explanatory paragraph once; the original Display, raw bytes, fingerprints, financial records and capabilities remain unchanged. No historical artifact is rewritten simply to improve presentation.

Inspected `ProjectionAnalyzerHistoryDialog` load-generation/ownership checks and concrete MC prepare dispatch. Selected associated artifacts, not current plan settings, supply run/report data. Corrupt or incompatible artifacts remain preserved with an explanation, not an empty success or recomputed replacement. Existing immutable component staging, ownership, atomic replacement and backup contracts remain unchanged.

## 10. Run controls and feedback

Both production dialogs retain their accepted BorderPane structure: historical content in a fit-to-width scroller, current status/progress/validation in a fixed footer, and ordinary Close semantics. Historical applicability/durability remain distinct from current execution. Busy state controls Run/Cancel and prevents duplicate work; historical/outdated status alone does not disable Run.

Existing tests cover validation/admission failure, empty-message errors, cancellation, source edits, executor rejection, closure, previous-result retention, accepted completion and storage failure. Retry Save reuses the accepted completion without another simulation. Closed/obsolete sessions cannot publish late results. Native ownership, keyboard and screen-reader behavior are acceptance checks; geometric screenshots do not certify assistive technology.

No Run handler, session state machine, source-revision gate, modal navigation or worker policy changed in B4. B1's original native mechanism was not established at audit time; its independently demonstrated visibility correction and subsequent user-confirmed native acceptance are preserved without inventing a new diagnosis.

## 11. PDF View/Save and frozen reporting

Standalone/current reports use original plan details and settings; comparison reports retain candidate-specific disclosures in Frozen analysis details. Both now explicitly identify household configuration from frozen metadata. Reports retain original mode, trial count, seed, investment return/inflation, mortality where applicable, horizon, success and estate metrics. Zero contexts remain uncluttered by expense-enabled descriptions.

`MonteCarloPdfExportAction` and historical MC export continue through `PdfExportWorkflowFactory`, coordinator, preview store and desktop opener. The configured Default Export Directory is prepared only in Save chooser adapters. Suggested filenames, filters, extension/collision handling, native overwrite behavior and exporter safe-write semantics are unchanged. View publishes a complete managed PDF before opening; Cancel does not capture/create a preview. Automated openers are fake.

The eight-case B4 workflow matrix exercises every enabled family with actual rendering: View performs no Save chooser or export-directory initialization; Save prepares the configured directory and creates no managed preview; choice Cancel and chooser cancellation perform zero report capture/render. View and Save extracted text is equal for the same frozen report. Existing coordinator tests cover failure, busy and lifecycle behavior; directory/settings tests cover migration, preferences and failure. Actual native viewer association/launch and Windows chooser interaction remain native acceptance items.

## 12. Actual visual inspection

Existing real JavaFX preview tests were rerun on the final presentation changes for all eight configurations, Light/Dark and 900×650 / 1180×820. Standalone previews are under `target/phase21b2/previews`, comparisons under `target/phase21b3/previews`; selected fresh images are copied into `target/phase21b4/previews` for this report. Fixed footers remain visible at bottom scroll position, explanatory text wraps and Close remains reachable. Existing wide result-table scrolling is preserved rather than redesigned.

All sixteen zero/positive reporting fixtures generate actual PDFs under `target/phase21b4/reports`. `FinalPdfInspection.java` uses existing PDFBox to render first pages and tail-disclosure pages; no external viewer is launched. Representative standalone and paired pages, including current/history context and household identification, are inspected for clipping, overlap, charts/tables and pagination. Page-count observations and additional history/long-disclosure inspection findings are recorded with final verification. No CSS-only mockup is counted as visual inspection.

The additional ignored `HistoryLongPreview.java` harness creates isolated synthetic controller/repository fixtures with eight extra recurring expense definitions. It accepts one analysis, saves its immutable association, changes live volatility from 25% to 99%, and opens the actual `ProjectionAnalyzerHistoryDialog`. Both couple paired-fixed and single-person longevity History render in Light/Dark at both sizes, retaining original 25% (and paired Baseline 17%) and showing the new explanation. Analysis call count remains one. Long percentage lists and per-expense financial definitions wrap inside the existing scroller. Two additional original-context long-disclosure PDFs are generated. Logs: `history-visual.log` and `history-visual-geometry.log`.

An early compact History image was inspected before resize had settled. A settled geometry check found Close entirely within the client scene: Y=574.6–602.4 in a 611-pixel scene at 900×650; Y=744.6–772.4 in a 781-pixel scene at 1180×820. Subsequent images show the button correctly. This was not evidence of a production defect and no History layout was changed.

Representative manually inspected images include standalone couple/fixed Light 900 details, single/longevity Dark 1180 details, comparison couple/fixed Dark 900 details, long paired History Dark 900 and its original-context PDF pages. All sixteen new standard PDFs were rendered; the standalone zero/positive page counts were couple fixed 4/4, couple longevity 5/5, single fixed 3/4 and single longevity 6/6. Paired counts were couple fixed 6/6, couple longevity 6/6, single fixed 5/5 and single longevity 6/6. These are synthetic-fixture observations, not guaranteed production counts. Additional content uses existing pagination; no text overlap, lost disclosure or missing chart/table was observed in the representative inspected pages.

Native DPI scaling, keyboard interaction with real system windows, Narrator and high-contrast behavior remain explicit acceptance limitations.

## 13. Performance and resource review

Scenario paths remain transient O(enabled expenses × years). There is no new cache, persisted draw/path collection or per-trial deep plan copy. Standalone isolates its original plan once; comparison isolates each candidate once. Existing limits remain 1,000 occurrences, 1,000 years and 100,000 enabled occurrence-years, with existing core/UI trial limits and cancellation polling. The sampler/engine/reducers are unchanged.

Bounded measurements from the B4 focused run, 4 GiB test heap:

| Workload | Observed time | Raw / compressed envelope | Capability |
|---|---:|---:|---:|
| Standalone, 100 trials / 10 expense definitions | 0.362 s | 125,872 / 25,578 bytes | 1,340 bytes |
| Paired, 100 trials / 10 per side / 30 years | 0.743 s | 178,035 / 53,592 bytes | 2,716 bytes |

These are bounded observations, not production-scale guarantees or independent peak-allocation profiling. Existing normal benchmarks run in the complete milestone suite; opt-in production-capacity probes remain opt-in. No performance optimization or numeric shortcut was added.

## 14. Focused and complete regression results

Required environment: Java 25.0.4.1; IntelliJ bundled Maven at the AGENTS.md path; workspace `.codex-m2/repository`; `-DargLine=-Xmx4g`.

| Verification | Tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Initial reporting/UI/PDF focused set | 44 | 43 | 0 | 0 | 1 existing opt-in |
| Broad affected MC/expense/history/codec/save/directory/coordinator selection | 703 | 693 | 0 | 0 | 10 existing opt-in |
| Eight-family workflow matrix and cached opt-in PDF preview | 9 | 9 | 0 | 0 | 0 |
| Complete normal milestone suite | **4,210** | **4,164** | **0** | **0** | **46 existing** |

The selections overlap; do not add them as unique-test totals. Logs are `target/phase21b4/reporting-focused.log`, `focused-final.log` and `workflow-cached-preview.log`. No failed assertion or compilation error occurred in these completed runs. Existing fault-injection exceptions in passing tests are expected.

The broad selection is `*MonteCarlo*Test,*Expense*Test,*AnalysisSnapshot*Test,*AnalyzerAssociation*Test,*AssociationTest,*WeightedSnapshot*Test,*Save*Test,*SocialSecurityReduction*Test,AnalyzerIntroductionMenuTest,PdfExportDirectoryContextTest,PdfExportWorkflowCoordinatorTest,!*BenchmarkTest`. Expensive benchmarks are omitted only during this development selection. The cached PDF preview uses its existing four 5,000-trial completed fixtures without regenerating simulations.

Final milestone command is the required bundled `mvn.cmd`, `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`, `-DargLine=-Xmx4g`, `test`. There is no test-class selection or new exclusion. The one complete normal suite after final production/test changes finished with **BUILD SUCCESS**, exit 0, on November 5, 2026 at 15:22:17 Eastern, in **12 minutes 40 seconds**. Log: `target/phase21b4/full-regression.log`. Independent current-run XML totals and skip reasons are in `full-totals.json` and `full-skips.csv`; stale XML from prior diagnostic runs is excluded by matching the classes actually executed in this Maven log.

All original financial/seeded golden expectations passed. The suite includes exact disabled-path values/vectors/wire/fingerprints, enabled standalone/paired engine oracles, tamper/unsupported-version rejection, history/controller/association Save/Save As/restart/swaps, storage and Retry Save, Phase 19 compatibility, Phase 18 directory and existing navigation regressions. The 33 additional B4 cases introduce no new skips. No complete suite was repeated merely for a presentation adjustment.

## 15. Existing skips and limits

The accepted B3 baseline and B4 milestone have the same 46 existing skips: 25 opt-in visual previews, 14 opt-in capacity/performance/large-artifact probes, two private-plan audits, one explicitly disabled production mortality benchmark and four Mississippi worksheet assumption aborts awaiting authoritative guidance (MS11, MS12, MS21, MS27). Normal suite benchmarks did run. No configuration, assertion or financial expected value was weakened to alter that baseline. The opt-in cached MC PDF preview was additionally run and passed separately. Real-plan audits were not enabled.

Remaining product boundaries: generated expense worlds only; exact-definition V1 correspondence; legitimate single-person/couple/survivor/tax and resource constraints; legacy positive-configured artifacts without capability explicitly unavailable for interpretation; unchanged wide-table behavior. Correlation, monthly expense sampling, durable IDs, alternate distributions and partial correspondence remain deferred. No new insurance, conditional-income, licensing, state-tax or Phase 16B3 work was started.

## 16. Native Windows acceptance checklist

- Run each of the eight configurations with zero and positive volatility; verify progress, Cancel, duplicate suppression and retained previous results.
- Compare zero/positive, positive/zero, identical positive and different positive definitions under a fixed seed. Confirm each side's original assumptions and zero deltas for identical financial candidates.
- Inspect tooltips, Light/Dark, 900×650 / 1180×820, 100%/125%/150% scaling, bottom scrolling, footer status, focus, keyboard activation, Close and Narrator.
- Inspect long expense disclosures and the explicit household line. Verify that volatility is understood as relative standard deviation, not a cap/inflation increment, and that one-time/deterministic behavior is unchanged.
- Edit live expenses, dates, escalation and baseline after a run; Save/Save As/restart/swap/open associated History. Confirm original assumptions, metrics and association without rerunning.
- View and Save current/historical PDFs in the real default viewer/configured export directory; test Cancel and chooser cancellation. Inspect all pages, charts and per-candidate details, including one-side-disabled comparisons.
- Exercise storage/viewer failures safely with synthetic plans. Verify previous files/results survive, retained PDF paths remain available and Retry Save does not simulate.

## 17. Git and data preservation

Starting branch `main`, HEAD `81dd97a12a875dad864057a2662a7ac6bcdfefbd`, empty index. The initial 35 modified tracked and 20 untracked entries are all accepted Phase 21A/B1/B2/B3 work. Their status and SHA-256 hashes are preserved in `target/phase21b4/initial-status.txt` and `initial-file-hashes.json`.

All **55** initial files remain present: **51 byte-for-byte unchanged**, with only the four pre-existing files listed in the preservation record receiving B4 text extensions. Two previously clean tracked files receive PDF-context/tooltip-test updates; two new B4 test classes and this report are untracked. Existing financial engine, snapshot/codec/fingerprint/association implementations and prior reports remain unchanged from the initial hashes. The final accumulated working tree has **37 modified tracked files and 23 untracked files**; the index is empty, branch/HEAD unchanged. Exact preservation and status inventories are under `target/phase21b4`.

`git diff --check` passed; new B4 test/report files were also checked for trailing whitespace. No real user plan, backup or artifact was accessed or modified. Nothing was staged, committed, pushed, reset, cleaned or reverted.

## 18. Closure recommendation

**Ready for final native Windows acceptance; not yet formally closed.** All eight supported configurations and approved stochastic/provenance contracts pass automated milestone verification. The identified obsolete restriction/disclosure issues are corrected, representative actual JavaFX/PDF inspection found no blocker, and no unresolved financial or persistence defect was identified. Untested native accessibility/DPI/viewer interactions and opt-in production-scale probes are explicitly listed above.

After the checklist is accepted, Phase 21 can be formally closed. This implementation stops at B4; it neither starts another phase nor authorizes a commit. Await explicit native Windows acceptance.
