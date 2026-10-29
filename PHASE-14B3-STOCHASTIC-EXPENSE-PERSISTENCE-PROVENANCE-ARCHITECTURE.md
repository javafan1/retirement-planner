# Phase 14B3 — Stochastic Expense Configuration, Persistence and Provenance Architecture

## 1. Executive summary and approval gate

Architecture audit only, October 29, 2026. No production, test, UI, persistence or financial calculation changes were made. Read AGENTS.md and the Phase 14 architecture, 14A, 14B1 and 14B2 reports; checked the actual implementation rather than treating their proposals as implemented contracts.

The B1 sampler and B2 financial integration are present. Enabled execution remains package-private and its wrapper results cannot enter ordinary historical storage or PDF adapters. A safe public release needs three coordinated changes: an optional Expense field whose disabled representation is absent, family-specific effective-input applicability, and a distinct enabled Monte Carlo artifact/provenance contract.

The most important finding is that adding an optional field alone is insufficient. CompleteProjectionSnapshot currently uses its exact configured-input document digest for `matches(plan)`. Integrated analyzer fingerprints include expenses. Ordinary expense edits enter the general projection refresh path. Without targeted changes, volatility-only edits would incorrectly affect deterministic freshness or trigger projection work. Raw document integrity must remain exact; calculation applicability needs a separate, narrowly defined comparison.

Recommend retaining the enabled supplied-world rejection gate for this release. No new stochastic algorithm, distribution, random stream, financial formula, database or dependency is needed. All design changes below are recommendations requiring approval, not implemented behavior. Stop here before Phase 14B3A.

## 2. Baseline and preserved working tree

Branch `main`; HEAD `29863debd1e43451e5b5616d049583e97457c031`; staging index empty.

Pre-existing tracked modifications preserved:

- `.idea/encodings.xml`
- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloAnalyzer.java`
- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonAnalyzer.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEvaluationContext.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionExpensePath.java`

Pre-existing untracked files preserved:

- `PHASE-14-RECURRING-EXPENSE-STOCHASTIC-ARCHITECTURE.md`
- `PHASE-14B2-STOCHASTIC-EXPENSE-FINANCIAL-INTEGRATION.md`
- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/InternalExpenseRun.java`
- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/InternalExpenseResults.java`
- `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/StochasticExpenseFinancialBenchmarkTest.java`
- `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/StochasticExpenseMonteCarloTest.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/projection/StochasticExpenseProjectionTest.java`

The sole deliverable added by this audit is this report. Generated test logs are under ignored `target/phase14b3/`.

## 3. Verified existing code and data flow

### Expense and editing

`domain/financial/Expense` has final description, annual amount, growth category, dates and expense type. Its six-argument Jackson constructor defaults missing category/type to GENERAL/RECURRING. It has no volatility property. Negative legacy amounts remain representable. `@JsonIgnoreProperties(ignoreUnknown = true)` is significant for downgrade safety.

`GrowthCategory` provides GENERAL, HEALTHCARE and NONE, independent of expense type; NONE is recurring-only. `ui/dialogs/ExpenseDialog` serves normal and guided creation through its `guidedCreation` constructor. `ui/wizard/ExpensesWizardStep` uses that editor. Reuse this shared editor; do not implement a second volatility widget in the wizard.

### Financial execution

`ExpenseSamplingSpecification`, `MonteCarloExpenseGenerator` and dimension 6 implement the frozen V1 keyed lognormal protocol. Correspondence includes effective type, amount, dates, escalation and volatility, excludes names and UUIDs, and distinguishes first-seen duplicate occurrences. Financial fingerprint numeric scale is not the sampling normalization rule.

`InternalExpenseRun.capture` binds detached exact expense definitions and supplied volatility values. `ProjectionExpensePath.bind` validates their occurrence mapping. `ProjectionEvaluationContext` retains the immutable path through context overrides. `ProjectionEngine` applies the fixed multiplier after escalation and before established proration/survivor adjustments; Medicare retries reuse the same path. No random generator runs inside the financial engine.

`MonteCarloAnalyzer` and `MonteCarloStrategyComparisonAnalyzer` expose enabled execution only through package-private internal methods and `InternalExpenseResults`. Public execution still follows the original disabled path. Enabled supplied worlds are explicitly rejected before invoking suppliers.

### Persistence and restoration

`JsonRetirementPlanRepository` uses Jackson, immutable component preparation/verification and `AtomicFileWriter`; authoritative references are published only after commit. `RetirementPlanScenarioCopyService` uses a JSON round trip and strips projectionState. A new Expense property will therefore flow through cloning as well as ordinary plans; every mapper boundary needs compatibility tests.

`CompleteProjectionSnapshot` version 1 stores an exact input document, its SHA-256 digest, original annual results and summary. Construction verifies that exact digest. `matches(plan)` compares against a newly frozen configured-input document. Ownership changes preserve original values and identity. Do not rewrite an old input document to append zero volatility.

`MonteCarloPlanDocument` captures bounded frozen plan JSON without analyzer configuration/projection-state metadata. `MonteCarloSnapshot` version 1 stores original requests/settings, frozen candidate plans, display context, fixed/mortality/paired aggregates, timestamps and provenance. Its provenance covers returns, mortality, inflation, streams, aggregation and world source; it does not describe enabled stochastic expenses. Its constructor accepts only its supported version.

`AnalysisSnapshot` schema 1 currently requires fingerprintVersion 1. `AnalysisSnapshotCodec` explicitly dispatches MC payloads to MonteCarloSnapshot, validates digests and timestamps, uses strict creator/unknown-field checks, and applies bounded serialization/decoding. Relevant limits include 32 MiB raw, 16 MiB compressed, 2 million tokens, 100,000 arrays, depth 80, string length 256,000 and numeric length 4,096. MonteCarloSnapshot additionally limits trials, years and annual observations. New payload handling must participate in every existing bound, numeric check and cached validation path.

`MonteCarloResultLifecycle` owns load/assessment generations, pending attachment, binding and accepted completion. It and `MonteCarloSnapshotPresentationMapper`, `ProjectionAnalyzerHistoryDialog` and `MonteCarloFingerprintService` contain concrete V1 payload assumptions. Merely making a new DTO serializable does not make restoration safe.

## 4. Proposed Expense domain and JSON contract

Use optional JSON property `annualExpenseVolatility`, a decimal relative annual standard deviation. Internally 0.20 means 20%. Store a nullable BigDecimal, canonicalizing any exact numeric zero to null. Preserve a nonzero value's original precision. Expose an ignored derived effective getter returning zero when absent. Annotate only the persisted raw property with NON_NULL; do not change global mapper inclusion.

Keep existing constructor overloads delegating to the new optional constructor. Missing/null means disabled. Require 0 through 1 inclusive, with bounded numeric input; nonzero requires RECURRING and a nonnegative annual amount. Zero annual amount may retain volatility but spending remains exactly zero. One-time expenses do not expose the field and reject nonzero values. Changing an enabled recurring expense into one-time must explicitly clear the setting with visible editor behavior, not silently serialize unsupported data.

Do not rely on generic NON_DEFAULT for BigDecimal zero: scale-sensitive equality and mapper introspection make it unsuitable as the compatibility guarantee. Exact omission, property ordering and old round-trip bytes must be tested through each actual mapper. The strict snapshot codec enables FAIL_ON_MISSING_CREATOR_PROPERTIES: adding a seventh creator argument can break older embedded expenses even when ordinary plan loading succeeds. Prefer an Expense-specific deserializer for strict historical readers that treats only this newly optional property as absent-safe; do not globally weaken missing-field or corruption validation. The implementation stage must prove this before acceptance.

Disabled JSON must retain the old six-property representation; explicit 0, 0.00 and absent normalize to the same absent representation. Old persisted component bytes remain untouched. Positive volatility is included in new frozen configured-input documents and integrity digests. No root retirement-plan migration or snapshot rewrite is proposed.

## 5. Fingerprints, freshness and configured-input identity

### Verified mechanisms

`AnalysisFingerprintService` preserves decimal value and scale. SS-only selects Social Security inputs and does not fingerprint expenses. Deterministic integrated/Quick/individual integrated and weighted families fingerprint financial plan trees that currently include all Expense properties. Weighted removes selected horizon/death reference context, not all expenses.

`MonteCarloFingerprintService` has EXACT_V1 stored fingerprints and TEMPORARY_NUMERIC_V2 compatibility assessment. V2 normalizes selected analyzer numeric settings; it is not permission to normalize all financial values. Comparison fingerprints include both candidates. Stored compatibility also validates algorithms, reference data and original payload fingerprints.

`MainWindow` connects ExpensesView to `onPlanChanged`, which marks modified and refreshes projection views. `ApplicationController.markModified` advances source-plan state; `projectionChanged` clears projection/chart/context caches and advances the projection epoch. `CompleteProjectionSnapshot.matches` is used by acceptance/restoration/baseline/swap checks. Volatility edits cannot simply reuse all these effects unchanged.

### Recommended family policy

| Consumer | Volatility-only edit | Expected amount/escalation edit |
|---|---|---|
| Deterministic projection and standard charts | Retain accepted financial applicability; no automatic recalculation | Existing invalidation remains |
| SS-only couple/individual | No financial mismatch | Existing SS-specific contract remains |
| Deterministic integrated, Quick, weighted integrated | Ignore only volatility in effective input comparison | Existing financial mismatch rules remain |
| Monte Carlo standalone and paired | Reassess with original/new volatility and model contract | Reassess as today, including both candidates |
| Historical artifacts | Remain original, separately classified | Remain original, separately classified |

Introduce a narrowly scoped effective deterministic input comparison that excludes only the new expense-volatility property at the known expense location. Keep the full configured input document/digest, immutable component integrity and all other financial inputs exact. No arbitrary recursive removal of similarly named fields. Preserve existing zero-plan fingerprints byte-for-byte. SS-only should need no financial hash change.

Preserve execution authorization separately: a volatility edit still marks the plan dirty and advances appropriate source/generation guards. It must not authorize old in-flight work to publish. Retaining accepted deterministic results does not mean relaxing revision admission or cross-analysis joins.

**Swap decision requiring approval:** retain exact configured-document matching as the precondition for role exchange, even when a volatility-only edit does not make deterministic financial results stale. Explain that the configured simulation assumptions differ. Saving alone does not resolve that mismatch. Never silently replace original snapshot volatility or assign it a new historical identity. A future alternative snapshot/configuration policy would need separate approval.

## 6. Enabled Monte Carlo version and provenance proposal

Keep existing V1 payload and algorithm meanings unchanged. Recommend a distinct enabled payload, provisionally `MonteCarloExpenseSnapshot` version 2, reusing existing immutable Settings, Request and financial aggregate DTOs. Do not wrap an enabled result inside a V1 snapshot that claims legacy semantics. A small common read-only payload adapter can support both variants; no parallel result repository or duplicated scenario dataset.

Use a new explicit enabled fingerprint contract, provisionally fingerprintVersion 3 / EXPENSE_MODEL_V3, avoiding the already-used temporary numeric V2 name. Keep envelope schema 1 if the structure is unchanged, but admit only the explicitly supported fingerprint/payload combinations. Unsupported combinations fail closed. Final version identifiers need approval before implementation.

The enabled immutable execution assumptions must include:

- Model ID `INDEPENDENT_LOGNORMAL_EXPENSE_LEVEL_V1`, enabled state and schema version.
- Dimension 6, sampling version 1 and correspondence protocol version 1.
- Master seed, trial settings and relevant horizon/lifetime configuration from the accepted request.
- Original candidate input documents including configured volatility and escalation.
- A bounded occurrence manifest (candidate side, definition key, duplicate ordinal and enabled status) if required to validate binding; derive repeated financial fields from the frozen plans rather than duplicate full expenses.
- Financial/request fingerprint contract, model capability and authoritative run timestamps; existing ownership, run UUID and projection association remain separate identities.

Construct provenance before computation from the exact detached request, validate it again before publishing, and validate restored correspondence against the frozen plans without sampling or financial execution. Reject mismatched settings, unknown protocols, missing enabled definitions and invalid numeric values. Do not persist per-scenario expense paths.

`AnalysisVersions.algorithms(kind)` is currently kind-based. Do not globally bump it and invalidate every old MC artifact. Introduce validated payload-capability-specific algorithm expectations; V1 retains its map, enabled V2 adds an explicit expense-model capability while unchanged market/inflation/mortality identities remain unchanged. Update all lifecycle/history compatibility consumers together.

## 7. Historical snapshots, associations and Save As

Continue using AnalysisSnapshot, AnalysisResultStore, the existing component codec/repository and Phase 12E projection associations. No second history system. Keep last-success slots separate from immutable associated results. Historical navigation uses original saved requests and never executes an engine or promotes history into live controls.

A UUID match is insufficient for Monte Carlo applicability when volatility has changed. Validate owner, projection association, artifact integrity, supported capability, original input/request fingerprints and current relevant configuration independently. Equal financial hashes do not invent associations. Swaps carry associations by original projection UUID, with original volatility intact.

Save As must independently own references under existing component rules while preserving original result values, run identities, original context and valid digests. Atomic component staging, budget verification and manifest replacement remain unchanged. Do not drop pins to meet limits; no automatic garbage collection.

## 8. Supported execution and supplied worlds

After provenance approval, add a supported request/result boundary that adapts persisted volatility to the existing B1 specifications and B2 execution. Capture the exact occurrence list once from the detached request. Fixed/longevity, single/couple and paired modes must use the same adapter. Keep disabled public calls on their original branch without expense RNG, path allocation or multiplication by one.

Replace the internal wrapper restriction only when a supported result carrying complete provenance can be built. A private shared execution body may return compact immutable financial data to either legacy or enabled result construction. Do not expose a legacy-shaped enabled result temporarily. Preserve cancellation, progress, failures and generation admission; failed/canceled runs publish neither a result nor an association.

Paired UI execution currently compares Current Plan and Saved Baseline with frozen candidate plans. Derive each side's volatility independently from its original inputs. Do not copy Current settings onto the saved baseline. Matching expense definitions share B1 shocks; edited definitions do not. Preserve the existing common fixed comparison horizon and longevity worlds rather than changing financial comparison definitions.

**Recommend deferring enabled caller-supplied worlds.** Existing market/lifetime/inflation world types do not carry a complete expense binding/protocol contract. Keep enabled overloads rejected before suppliers or financial execution; never fill missing paths using unrelated draws. Disabled overloads remain supported unchanged. A later extension would need seed/scenario/candidate identity, exact occurrence mapping, protocol versions, all enabled occurrences and calendar-year coverage, plus paired-side consistency.

## 9. User interface and original-context disclosures

Add `Annual Expense Volatility (%)` to recurring ExpenseDialog rows, including guided creation. Numeric input 0–100, default 0, visible validation, keyboard/accessibility metadata, wrapping help and semantic theme styling. Hide for one-time expenses; disable positive input for negative legacy amounts with explanation. Zero expenses may retain valid settings. Escalation remains independent.

Recommend up to two decimal places for newly entered percentage values, while preserving higher-precision imported values until explicitly edited; never round stored historical values to fit the display. Confirm this policy before implementation.

Tooltip: “Controls how much this recurring expense may vary from year to year during Monte Carlo simulations. For example, 20% volatility introduces annual spending variation around the expected expense. It does not change deterministic projections or the expense's selected inflation setting.” Add: “This is an annual relative standard deviation, not a maximum increase or decrease.”

Relevant result assumptions should list original expected amount, escalation and annual volatility, identify enabled/disabled expense variability and candidate side, and show model/protocol provenance for enabled results. Add no new outcome chart or unsupported risk statistic.

`MonteCarloRun`/`MonteCarloStrategyComparisonRun` already carry frozen details; `MonteCarloPdfReportAdapter` consumes those, not live plans. Extend their accepted context with typed enabled assumptions derived from original requests. Historical reconstruction must create the same context. Preserve legacy PDF text/palette/data where no new model was enabled.

Required disclosure: “Recurring expense volatility models independent annual variation around expected spending. It does not represent a guaranteed maximum change and does not compound from one year's expense shock to the next.” Explain that each shock affects that year's expected escalated spending; its resulting balance/tax effects can persist financially even though the shock itself does not compound.

## 10. Migration, rollback and stage-release safety

No bulk migration, old-component rewrite or golden regeneration. Missing volatility is zero. New binaries retain old result interpretation. Unknown enabled artifacts fail safely and remain unavailable rather than being reconstructed.

**Downgrade limitation:** Expense currently ignores unknown JSON properties. An old binary may therefore silently ignore positive volatility in a new plan, even though the new result artifact is unsupported. Backward readability by the new application is achievable; faithful execution by an older application is not guaranteed. Recommend an explicit release/downgrade warning and backup guidance. Adding a plan capability gate would be a separate approved persistence decision; do not quietly add a root schema migration.

Stage A cannot safely expose nonzero settings while ordinary MC silently ignores them. Until B/C complete, block MC admission for any candidate containing positive volatility, with an explicit unsupported-until-enabled message. Non-MC family applicability must be addressed before exposing those edits, or controls must remain non-public until the later stage. This sequencing dependency needs approval; do not ship an intermediate stage claiming the feature executes.

## 11. Proposed implementation file inventory

Paths below are relative to `src/main/java/com/daviddunn/retirementplanner/`; proposed, not modified. Final per-stage reports must list exact actual changes.

| Area | Existing files / proposed additions |
|---|---|
| Domain/configuration | `domain/financial/Expense.java`; optional focused expense JSON adapter and effective-input comparison service |
| Editor/wizard | `ui/dialogs/ExpenseDialog.java`, `ui/wizard/ExpensesWizardStep.java` only if integration requires it, `ui/views/ExpensesView.java` if a distinct edit classification is needed; existing semantic CSS only where necessary |
| Projection applicability | `app/projection/CompleteProjectionSnapshot.java`, `ui/controller/ApplicationController.java`, `ui/MainWindow.java`; keep exact digest and swap checks distinct |
| Fingerprints | `app/socialsecurity/results/AnalysisFingerprintService.java`, `app/montecarlo/MonteCarloFingerprintService.java`, `AnalysisVersions.java` and compatibility callers |
| Enabled artifacts | new `app/montecarlo/MonteCarloExpenseSnapshot.java`, immutable execution assumptions/common payload adapter; reuse `MonteCarloSnapshot` nested financial DTOs |
| Codec/integrity | `persistence/AnalysisSnapshotCodec.java`, `app/montecarlo/MonteCarloSnapshotNumbers.java`, `app/montecarlo/MonteCarloSnapshotMapping.java`, AnalysisSnapshot supported fingerprint combinations |
| Execution | `app/montecarlo/MonteCarloAnalyzer.java`, `MonteCarloStrategyComparisonAnalyzer.java`, existing internal run/result boundary; no replacement sampler or engine algorithm |
| UI lifecycle/history | `ui/montecarlo/MonteCarloRunService.java`, `MonteCarloStrategyComparisonRunService.java`, `MonteCarloRun.java`, `MonteCarloStrategyComparisonRun.java`, `MonteCarloResultLifecycle.java`, `MonteCarloSnapshotPresentationMapper.java`, `ui/socialsecurity/ProjectionAnalyzerHistoryDialog.java` |
| Reporting | existing `MonteCarloPdfReportAdapter.java`, `MonteCarloMortalityPresentation.java` and result assumption presentation; preserve historical PDF context |
| Repository | existing JSON repository/copy/component services should normally need tests rather than format changes; modify only if a verified compatibility boundary requires it |

## 12. Test strategy

Extend existing ExpenseEscalationCompatibilityTest, ExpenseEscalationUiTest, ExpensesWizardStepTest, AnalysisFingerprintTest, ProjectionSnapshotPersistenceTest, MonteCarloNumericCompatibilityTest, MonteCarloSnapshotCodecTest, MonteCarloResultLifecycleTest and MonteCarloProjectionAssociationTest. Retain B1 and B2 sampling/financial tests unchanged as compatibility oracles. Add focused enabled artifact/admission/assumption tests.

Required acceptance branches:

1. Exact old expense JSON for absent/null/0/0.00, constructor compatibility and strict historical creator handling; positive precision survives ordinary Save/Load/Save As, copy and snapshot round trips.
2. Range/type/negative/zero validation, wizard/standard editor parity, light/dark/reduced-size rendering and keyboard operation.
3. Volatility-only dirty edit retains deterministic display and SS/integrated applicability without running engines; amount/escalation edits retain existing invalidation. Raw configured digest still changes for positive volatility. Execution generations still reject obsolete callbacks.
4. Legacy zero-plan deterministic, SS, MC and numeric compatibility fingerprints unchanged. Enabled MC fingerprints include both paired candidate definitions and model identity. Change/revert, equal hash/different UUID, pending assessments and wrong owner remain correctly classified.
5. New artifact round trip, digest corruption, missing required data, unsupported versions, malformed numbers, oversize raw/compressed payloads, unknown protocols and mismatched occurrence manifests fail closed without weakening old assertions.
6. All eight existing MC identities (standalone/comparison, fixed/longevity, single/couple), no partial acceptance, cancellation, supplied-world rejection and disabled supplied-world compatibility.
7. Save/restart/Save As/swap preserve original enabled assumptions and identity. Throwing engines prove historical visualization/PDF export performs no sampling or financial rerun. Last-success and associated history remain separate.
8. Original-context UI/PDF disclosures agree after live plan edits; no altered legacy financial values, chart colors or golden fixtures.
9. Stage-A admission gate blocks nonzero unsupported runs on either comparison side; removing the gate is tested only after complete provenance support.

Run each stage's focused tests, then the full suite before that stage's acceptance. No golden updates. Native Windows acceptance must include expense editor/wizard, restored history, settings themes, swap freshness messaging and original-context PDF inspection.

## 13. Performance considerations

Preserve scenario-local paths, not trial-count-sized retention. Added persistent configuration is O(expenses), and provenance/manifest is O(enabled occurrences), not O(scenarios × expenses × years). Render charts from existing aggregates. Do not duplicate full annual scenario paths or plan graphs in the enabled envelope.

B2's prior measurements are context, not rerun results: 1,000 scenarios and 50 expenses showed approximately 14–15% enabled financial execution overhead, with sampling/binding roughly 0.25 seconds and no retained paths. Rebenchmark enabled supported artifact encoding/decoding and history loading, including component limits, during B/C/D. Avoid repeated re-encoding large results merely to compute freshness.

## 14. Baseline verification actually executed

IntelliJ bundled Maven, project-local `.codex-m2/repository`, 4 GiB test heap. Focused run: **238 tests, 236 passed, 2 skipped, 0 failures, 0 errors; BUILD SUCCESS, 27.865 seconds.** Log: `target/phase14b3/baseline-tests.log`; Surefire XML confirms totals.

| Test class | Total | Skipped |
|---|---:|---:|
| ExpenseEscalationCompatibilityTest | 2 | 0 |
| ExpenseEscalationTest | 7 | 0 |
| StochasticExpenseProjectionTest | 12 | 0 |
| StochasticExpenseMonteCarloTest | 18 | 0 |
| MonteCarloExpenseGeneratorTest | 15 | 0 |
| MonteCarloNumericCompatibilityTest | 3 | 0 |
| MonteCarloSnapshotCodecTest | 19 | 2 |
| MonteCarloResultLifecycleTest | 60 | 0 |
| MonteCarloProjectionAssociationTest | 8 | 0 |
| ProjectionSnapshotPersistenceTest | 14 | 0 |
| AnalysisFingerprintTest | 71 | 0 |
| ExpenseEscalationUiTest | 4 | 0 |
| ExpensesWizardStepTest | 5 | 0 |

Skipped optional codec tests: `realProductionCapacityMatrixAndUiMaximum` requires `mc.codec.capacity`; `replayAcceptedCapacitySnapshotsWithoutFinancialExecution` requires `mc.codec.replay`. Expected injected repository-failure diagnostics occurred in lifecycle tests; assertions passed. The command wrapper reported exit 1 despite Maven's final BUILD SUCCESS; verification here uses the explicit Maven summary and fresh Surefire results, not that wrapper status alone.

Full suite and performance workloads were not rerun for this architecture-only phase. No native Windows or new feature visual acceptance is claimed. `git diff --check` passed; Git emitted only existing LF/CRLF conversion notices for pre-existing B2 files. Index remains empty.

## 15. Explicit approval decisions and implementation sequence

Approval requested for:

1. Nullable optional `annualExpenseVolatility`, exact zero omission, strict per-Expense historical default handling, and the described UI precision/negative-amount policy.
2. Narrow deterministic effective-input comparison excluding only volatility, while retaining full immutable configured-document integrity and exact swap input matching; distinct simulation-only edit handling without weakening execution guards.
3. Enabled payload version 2 and fingerprint contract version 3, with payload-capability-specific algorithm validation rather than global version invalidation.
4. Continued rejection of enabled supplied worlds, and documented downgrade limitations without a new plan root migration.
5. Intermediate-stage admission blocking: settings may be saved in A, but enabled MC remains blocked until B/C can publish supported provenance. Resolve applicability before public editing, or keep controls unavailable until ready.

Recommended bounded stages, each with its own report and approval gate:

- **14B3A:** optional field, strict JSON/clone/snapshot compatibility, editor/wizard and safe intermediate admission/applicability handling. Prove legacy exactness before exposing positive settings.
- **14B3B:** enabled artifact, provenance, supported fingerprint combinations, bounded codec and historical ownership/association validation. Do not expose execution yet.
- **14B3C:** supported fixed/longevity/single/couple/paired execution using existing B1/B2; publish only complete accepted provenance, retain supplied-world rejection, then remove the temporary admission block.
- **14B3D:** original-context results/PDF disclosures, UI/history visual verification, complete regression and native Windows acceptance.

No implementation stage has begun. Explicit approval of Phase 14B3A and its above prerequisites is required next.
