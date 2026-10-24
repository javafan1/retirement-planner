# Phase 10A — Last-successful analysis parameter persistence audit

## 1. Recommendation and audit boundary

Implement a small, versioned, typed configuration section in each retirement plan, separate from existing result snapshots. Capture configurations from the immutable effective requests at the existing eligible acceptance callbacks, never from editable controls after completion. Use an application-owned asynchronous **configuration-only plan transaction**, rather than calling the existing whole-plan Save action. Restore configurations before normal input listeners are enabled; keep historical result restoration independent.

This document is a proposal. No production code, tests, schemas, financial calculations, snapshot formats, or defaults were changed during this audit. Findings below describe current source; classes marked proposed do not exist yet. Implementation requires approval, including the policy decisions in section 10.

Initial repository: branch `main`, HEAD `2e959d9770bd834cd4ca1bed3f50df1219607c65`, clean working tree. Earlier analysis phases are already committed at this HEAD. Read AGENTS.md, the Monte Carlo Phase 1/2A–2E and 2D-1/2D-2 reports, relevant Phase 9 reports, and current source. Source takes precedence over report proposals.

## 2. Complete analyzer inventory

There are **15 independent supported analysis identities**, already enumerated by `app/socialsecurity/results/AnalysisKind.java`: seven Social Security identities and eight Monte Carlo identities. Job mode alone is insufficient: individual deterministic work and couple exhaustive work both use EXHAUSTIVE admission mode.

| Identity | Effective request / result path | Temporary parameters to retain | Current result persistence |
|---|---|---|---|
| COUPLE_SOCIAL_SECURITY | `SocialSecurityStrategyAnalysisRequestFactory`; `SocialSecuritySurvivorClaimingOptimizationRequest` and optimization settings | Primary/spouse factors, conditioning date, valuation date, real discount rate; effective search profile | Complete Phase 9D-1 snapshot |
| COUPLE_QUICK | `SocialSecurityAnalyzerInputs.quick`; integrated comparison of selected SS candidates | Candidate count; exact prerequisite/candidate basis provenance | Memory only; deliberately no Quick persistence |
| COUPLE_EXHAUSTIVE | Standard integrated exhaustive search request | Effective search/ranking/retention profile; no extra financial override form | Complete versioned interned snapshot |
| COUPLE_WEIGHTED | Weighted request factory and frozen request snapshot | Shared mortality/valuation fields; optional current-strategy survivor baseline context | Complete weighted snapshot |
| INDIVIDUAL_DETERMINISTIC | `SinglePersonIntegratedView` builds nine-strategy integrated request | Optional primary death year, effective nine-strategy profile | Memory only |
| INDIVIDUAL_SOCIAL_SECURITY | `SinglePersonMortalityView`, SOCIAL_SECURITY_ONLY request | Independent primary factor, conditioning/valuation dates, real discount rate | Memory only |
| INDIVIDUAL_WEIGHTED | `SinglePersonMortalityView`, INTEGRATED request | Independent primary factor, conditioning/valuation dates, real discount rate | Complete weighted snapshot |
| COUPLE_MONTE_CARLO_FIXED | Standalone fixed request captured by MC run service | Common MC settings, fixed mode | Persistent |
| COUPLE_MONTE_CARLO_LONGEVITY | Standalone longevity request | Common MC settings, both mortality factors, effective survivor election | Persistent |
| COUPLE_MONTE_CARLO_PAIRED_FIXED | Matched-scenario comparison request, Current Plan and Saved Baseline | Common MC settings, fixed comparison mode, frozen baseline provenance | Persistent |
| COUPLE_MONTE_CARLO_PAIRED_LONGEVITY | Matched longevity comparison request | Common MC settings, both factors, separate Current Plan/Baseline survivor ages | Persistent |
| INDIVIDUAL_MONTE_CARLO_FIXED | Standalone fixed request | Common MC settings, fixed mode | Persistent |
| INDIVIDUAL_MONTE_CARLO_LONGEVITY | Standalone longevity request | Common MC settings, primary factor | Persistent |
| INDIVIDUAL_MONTE_CARLO_PAIRED_FIXED | Matched fixed comparison request | Common MC settings and baseline provenance | Persistent |
| INDIVIDUAL_MONTE_CARLO_PAIRED_LONGEVITY | Matched longevity comparison request | Common MC settings, primary factor and baseline provenance | Persistent |

MainWindow entry points are Social Security Strategy Analyzer, Monte Carlo Analysis, and Monte Carlo Strategy Comparison. Ordinary projection/baseline comparison and the existing Break-Even dialogs consume plan or retained result data; they are not additional independently configured stochastic/search analysis kinds. The selected-strategy deterministic Break-Even feature derives from retained projections. No new persistence family is proposed for these derived views, PDF exports, heat-map metric selection, or table sorting.

### 2.1 Inputs, defaults, validation and initialization

Current sources: `ui/montecarlo/MonteCarloInputs.java`, `MonteCarloAnalysisView`, `MonteCarloStrategyComparisonView`, their run services and sessions; `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog`, `SinglePersonIntegratedView`, `SinglePersonMortalityView`, `CurrentStrategyBaselineView`; domain request/value-object validation.

| Input family | Existing initialization | Existing validation / effective interpretation |
|---|---|---|
| MC simulations | 5,000; offered choices 1,000/2,500/5,000/10,000 | UI accepts 1–10,000; domain supports a wider range. Do not expand UI bounds through configuration loading. |
| MC return | Plan investment return, displayed as percent | UI percent -99 through 100; exact BigDecimal converted to rate with movePointLeft(2). |
| MC volatility | 12.00% | UI 0 through 100%. |
| MC seed | 417 | Signed 64-bit integer. |
| MC mode | Fixed | Fixed/longevity are separate stored identities. |
| MC inflation | Deterministic initially; mean from plan, volatility 1.75%, floor -2.00% | Stochastic mean -99..100%, volatility 0..100%, floor -99..100%; underlying settings also validate floor relative to mean. Deterministic mode ignores dormant stochastic text. |
| MC mortality | Session factors initially 1 | MC controls enforce .50..3.00. Conditioning comes from plan projection start, not a separately editable MC valuation/discount input. |
| MC couple survivor ages | Relevant persisted scenario/session input; comparison has separate A/B values | Integer election age, existing request validation; do not invent a new upper bound. Required where existing longevity request requires it. |
| Couple SS/weighted mortality | Shared physical controls initialized at 1.00 and existing `LongevitySessionSettings` | Couple controls .50..3.00; plan supplies mortality categories and person facts. |
| Couple SS/weighted dates | Initially projection start; conditioning/session initialization then applied | Conditioning and valuation are independent dates. Existing request checks remain authoritative. |
| SS real discount | 1.0% initially | Exact percent-to-rate conversion; existing valuation request requires rate greater than -1. No new upper bound. |
| Quick candidate count | 10 | Spinner 1..20; Run requires a current compatible SS-only result. |
| Couple SS optimization profile | Retirement ages 62..70; survivor optimizer defaults retain five retirement candidates | Fixed effective service settings, not new editable controls. Capture profile version/settings rather than silently reinterpret future defaults. |
| Exhaustive profile | Standard full retirement/survivor universe, AFTER_TAX_ESTATE ranking, normal retained-detail count 20 | Service contracts; no mortality/valuation prerequisite. Grouping and metric selection remain presentation state. |
| Weighted optional baseline | Survivor age supplied from applicable plan death scenario, otherwise session field | Valid whole age at least 60. Missing/invalid optional baseline omits only comparison, not candidate search. See policy issue below. |
| Individual deterministic | Death-year text blank | Optional integer, cannot precede projection start; nine ages 62..70, retention nine. |
| Individual SS and weighted | Separate fields/instances: factor 1.00, discount 1.0%, dates initially projection start | Actual individual factor path uses positive finite mortality adjustment; do not impose the couple UI .50..3 range incidentally. Preserve existing date/rate checks. |

Percentages are UI representations; configurations should store the exact effective **rate**, explicitly named as such. Convert back to percent exactly when initializing controls. Do not round to a display precision that changes a previously accepted request. Original numeric scale is retained in storage even where numeric compatibility ignores scale.

Person facts, benefits and their valuation years, configured elections, COLA, financial assets, expenses, taxes, Roth/RMD settings and projection assumptions remain authoritative plan fields. Do not duplicate them as editable analyzer configuration. Fixed MC scenario/horizon comes from the effective captured plan(s), not a new override control.

### 2.2 Shared versus independent controls

Couple SS Only and Weighted use one authoritative physical longevity/valuation control set; Quick uses that SS result indirectly. Exhaustive does not consume those controls. Individual SS and weighted have separate independent controls. MC analysis and comparison dialogs are separate, but fixed/longevity choices share controls within each dialog, and mortality session settings have existing cross-view propagation.

Independent last-successful records therefore require a small per-kind control adapter/buffer, not duplicated fields or bidirectional bindings. Selecting a mode should show that mode's pending draft if deliberately retained, otherwise its accepted configuration, otherwise existing defaults. Reuse original nodes/listeners. Shared-session propagation and the existing invalidation matrix must be preserved; never mark a result Current merely because a configuration was selected. The exact per-mode draft navigation policy needs approval before implementation.

## 3. Fingerprint and frozen-request mapping

`AnalysisFingerprintService` explicitly extracts S (SS facts), M (mortality), V (valuation), F (integrated financial inputs), B (weighted baseline), and Q (Quick prerequisite/candidates). Its version-1 financial decimal handling is scale-sensitive. `MonteCarloFingerprintService` keeps exact stored V1 fingerprints and uses explicit temporary-numeric V2 compatibility reassessment. Only temporary MC rates/factors are normalized numerically; plan and baseline financial decimals remain scale-sensitive. `AnalysisCompatibilityPolicy` separately checks schema/financial/reference compatibility.

| Modes | Fingerprint dependencies | Configuration source |
|---|---|---|
| Couple SS | S+M+V | Accepted optimization request, settings and frozen analysis context |
| Couple Quick | Financial/S dependencies and Q: count, current SS prerequisite fingerprint, exact selected candidate elections/ranks/value provenance | Accepted quick capture; do not reverse the prerequisite hash |
| Couple Exhaustive | Integrated F/S scenario inputs, no M/V overrides | Accepted search request/profile |
| Couple Weighted | S+M+V+F+B, plus frozen explanatory context | Accepted weighted request snapshot and baseline capture |
| Individual deterministic | F/S and effective optional death-year override | Accepted nine-strategy request |
| Individual SS | S+independent M/V | Accepted individual mortality request |
| Individual weighted | S+independent M/V+F/scenario context | Accepted individual integrated mortality request |
| MC fixed standalone/paired | Settings, seed, trial count, frozen financial A and (paired) B plans, fixed end/scenario context and supplied-world identity where present | Accepted prepared request / `MonteCarloSnapshots` request data |
| MC longevity standalone/paired | Above plus relevant mortality factors/categories/table/date/survivor inputs and lifetime association | Accepted prepared longevity request, including paired A/B elections |

The fingerprints contain effective temporary inputs, but a hash cannot reconstruct them. Optional inactive text is intentionally not an effective input. Frozen DTO/request data is the source for configurations; full mortality distributions, scenarios, results and baseline plan documents do not belong in the small configuration section. Keep their provenance references, not duplicate graphs.

**Mandatory additive-schema integration:** both fingerprint services currently allowlist plan root properties and reject unknown fields. Adding `analyzerConfigurations` alone will break extraction. Explicitly classify and remove that one nonfinancial root before hashing; prove identical old digests with and without configurations. Do not loosen the unknown-field rejection generally. `MonteCarloPlanDocument.capture` must omit configuration metadata from financial request documents to avoid recursive/history growth. Preserve historical snapshot bytes and old V1 verification; do not rewrite frozen documents. Financial scenario-copy serialization must handle the additive field safely. Baseline `RetirementPlanSnapshot` should not acquire analyzer configurations.

## 4. Proposed versioned plan JSON

Illustrative structure; new schema requires implementation approval. Absent section means no recorded successful configurations. Use explicit kind dispatch and constructors, not arbitrary Jackson polymorphism.

```json
{
  "analyzerConfigurations": {
    "schemaVersion": 1,
    "configurations": {
      "COUPLE_MONTE_CARLO_PAIRED_LONGEVITY": {
        "configurationVersion": 1,
        "acceptedRunId": "00000000-0000-0000-0000-000000000001",
        "acceptedAt": "2026-10-23T12:00:00Z",
        "parameters": {
          "simulations": 5000,
          "seed": 417,
          "expectedReturnRate": 0.05,
          "returnVolatilityRate": 0.1200,
          "inflation": {
            "mode": "STOCHASTIC",
            "meanRate": 0.0275,
            "volatilityRate": 0.0175,
            "floorRate": -0.0200
          },
          "primaryMortalityFactor": 1.00,
          "spouseMortalityFactor": 1.00,
          "currentPlanSurvivorClaimingAge": 67,
          "baselineSurvivorClaimingAge": 67
        },
        "provenance": { "parameterContractVersion": 1 }
      }
    }
  }
}
```

Use UUID/run/time metadata from acceptance, not PDF export time or file modification time. Identity key fixes household shape and payload family; reject a conflicting shape/discriminator. No financial fingerprint contract version is changed merely by configuration schema versioning.

Typed parameter families:

- MC fixed: exact `MonteCarloSettings`, typed deterministic/stochastic inflation; fixed mode implied by kind. Longevity adds the relevant factors and survivor elections. Paired kinds add their distinct A/B optional elections and baseline provenance. Single-person types forbid spouse inputs.
- Couple SS: factors, conditioning/valuation dates, exact discount rate, explicit effective optimizer profile. Individual SS/weighted: their independent primary-only equivalents.
- Couple weighted: shared-field values at this run plus typed optional baseline setting and frozen source/completeness context. See section 10 for invalid optional text conflict.
- Quick: candidate count and immutable prerequisite provenance. If retained for provenance, exact selected candidate identities/ranks are bounded by the existing count. Do not use them to bypass current-result prerequisite checks or introduce Quick result persistence.
- Exhaustive: effective search/ranking/retention profile only; no new financial controls. Individual deterministic: optional death year plus effective nine-strategy profile.

Preserve exact BigDecimal values/scales; null/absent/invalid are distinct. Invalid required controls never produce an accepted configuration. An absent inactive field is not zero. Use tagged unavailable optional state where applicable. No simulation histories, projections, account balances, mortality grids or whole plans in this section.

Future versions: retain bounded opaque unknown entries/containers when round-tripping a plan, never execute them or initialize controls from them. Block automatic writes to an unsupported target/container version rather than discard it. Known unrelated entries must survive every update. Define conservative configuration-specific byte/string/entry limits before coding; do not grant this small metadata section the entire result snapshot budget. Unsupported unsafe data should yield a clear non-destructive save/load warning or refusal, not silent defaults followed by overwrite. Older application binaries may reject the additive field; backward readability means new application reads old files, not a promise that old binaries read the new schema.

## 5. Accepted-success and automatic-write transaction

### 5.1 Current acceptance boundaries

`SocialSecurityAnalyzerJobController` rejects closed, wrong-generation, canceled and changed-revision work, releases the coordinator lease and rechecks eligibility after observer notification before calling success callbacks. Couple callbacks in `SocialSecurityStrategyAnalyzerDialog` freeze/render/persist accepted results. `SinglePersonIntegratedView` and `SinglePersonMortalityView` have their own success callbacks; `IndividualCompletionPersistence` wires weighted persistence only. MC sessions and `MonteCarloResultLifecycle` already preserve accepted results through rejected replacement runs.

Apply `AnalysisSuccessPolicy` as well as job eligibility: SS has evaluated complete strategies; Quick has at least one successful entry; Exhaustive/weighted have ranked successful entries; individual deterministic follows exhaustive; individual mortality has the accepted nine-entry result contract. A normally completed MC experiment with no funded trials is still a valid analysis, not a fatal calculation failure. Do not invent a different financial success threshold. Partial accepted candidate failures remain part of accepted results.

Capture small immutable parameter data on the worker from the exact request that ran. Publish a run-ID/binding/generation-stamped configuration event **only** from the final eligible success callback. Do not read controls there: the user may already have edited them. Quick and the individual memory-only result modes still need this parameter acceptance handoff even though they have no durable result adapter.

### 5.2 Four distinct outcomes

1. Calculation completed.
2. Result/configuration accepted by eligibility and success policy.
3. Historical result snapshot committed.
4. Plan configuration JSON committed.

These are not a single cross-file transaction. The last accepted configuration updates application memory at step 2. Snapshot persistence and configuration persistence are independent jobs. UI must report each durability honestly. Configuration save failure retains the accepted in-memory configuration, existing result and previous durable JSON; Retry Parameters Save retries that immutable event without recalculation. A snapshot write failure must not prevent saving an otherwise accepted parameter configuration. A configuration write failure must not erase a successfully persisted snapshot.

### 5.3 Why existing Save cannot be called

`ApplicationController.save()` saves the whole live plan, clears `modified`, updates settings/binding, and would silently save unrelated unsaved financial edits. `saveAs()` changes identity only after successful file creation and separately clones result slots. Neither is a configuration-only transaction.

Proposed `AnalysisParameterCoordinator` is application-owned, bounded and asynchronous. It orders accepted writes by captured plan identity/path and per-kind acceptance sequence, exposes Pending/Saved/Save Failed/Memory Only, retries immutable accepted data and drains during shutdown. It does not schedule financial work. Plan switches never redirect a captured event to the new plan. Configuration metadata changes do not increment financial revisions, invalidate projection caches or call `sourcePlanChanged`.

Proposed repository transaction:

1. Capture UUID/path/binding epoch, accepted run and current durable-file token; immutable accepted configuration map update occurs on FX without clearing unrelated dirty state.
2. Under the same ordering/lock as ordinary Save and Save As, read and validate the latest durable document, UUID/path and supported configuration schema.
3. Patch only the requested configuration entry into the **durable** plan document using exact token-preserving copying. Preserve every other financial token, decimal scale, unknown configuration, and unrelated field. A generic readTree/writeTree monetary rewrite is not acceptable.
4. Verify expected durable document state and per-kind acceptance ordering; refuse conflict rather than overwrite newer external content. Coordinate ordinary saves so they include current accepted configurations and update the durable token.
5. Reuse `AtomicFileWriter`: sibling temp, force, strict verification with plan/config readers, recognized backup, atomic replacement. Previous file remains intact on failure.
6. Mark Saved only after commit and only for the matching acceptance sequence. A late success cannot mark a newer pending configuration durable.

`AtomicFileWriter.SAVE_LOCK` serializes JVM writes but is not a cross-process compare-and-swap. An expected hash checked before replacement alone has a TOCTOU window. Recommend a cooperative file lock plus inside-lock recheck and conservative external-change conflict policy; an arbitrary noncooperating editor can still race. Decide the acceptable external-edit contract before claiming absolute protection. Do not silently merge or save unreviewed external financial changes into live memory.

Dirty-plan example: an accepted run uses unsaved spending changes. Auto-write only parameters into the old durable financial document; keep live spending dirty. On restart those settings restore, but its snapshot may correctly be Outdated against the older saved finances. Automatically saving spending to force Current is outside scope.

Unsaved plans have no safe destination: recommend accepted parameters remain Memory Only until first explicit Save/Save As, which includes them without rerunning analysis. Do not autosave a new financial plan or choose an undisclosed filename. Legacy opened plans without UUID can receive a path/hash-bound configuration-only patch without assigning UUID; explicit ordinary save continues the established identity migration. This policy needs approval; requiring first ordinary Save is a conservative alternative.

Save As copies the accepted configuration map as small plan metadata into the independent new UUID. Original parameters/results remain independent. Serialize accepted configuration events and the fork operation so neither old-file queues nor late completions become new-identity writes. Snapshot cloning remains a separate recoverable operation. Manual Save must never serialize pending editor text as accepted settings.

## 6. Cancel, failure and control-restoration design

Current analyzers generally retain edited control text after cancellation/failure; no uniform rollback mechanism exists. Existing result-retention behavior must remain unchanged.

| Event | Proposed parameter action |
|---|---|
| Admission rejected / computation never started | No acceptance/write. Restore prior accepted settings only under approved rollback policy; explain rejection. |
| Validation failure | No write. Recommended literal requirement: show error then restore last accepted set; this differs from today's correction-in-place UX and needs approval. |
| Cancel during work | Wait for the owning run's terminal cleanup; restore prior accepted parameters if same plan/mode/edit epoch still owns the form. |
| Fatal computation failure | Preserve historical result/configuration; restore last accepted controls, show existing failure plus rollback notice. |
| Obsolete completion | Never accept/write. Do not roll back another plan, newer run or edits made after its cancellation. |
| Dialog close | Discard unaccepted draft; accepted persistence is application-owned and continues. Reopen from last accepted configuration. |
| Plan switch | Detach old view generation; initialize new plan's own settings. Old callbacks cannot restore old controls into it. |
| Application shutdown | Drain accepted writes or explicitly report pending/failure and allow established retry/exit handling; no silent durability promise. |

Rollback uses the latest **accepted in-memory** configuration, even if its automatic write is pending/failed. The last durable configuration is separately tracked. If no successful run exists, restore the as-opened/default buffer and make no successful-configuration claim.

Add a narrow run-scoped terminal-outcome callback for SS jobs: cancellation/obsolescence currently do not necessarily invoke the ordinary failure callback. Add equivalent scoped observer hooks to MC sessions. Guards must include plan binding, mode, run ID/generation and edit epoch; never roll back from a late obsolete callback after a newer accepted run. Keep one-job admission and cooperative cancellation semantics unchanged.

Apply controls atomically with a scoped listener guard, then one legitimate readiness/currency refresh. Preserve existing valid session propagation without duplicate listeners or synthetic financial plan revisions. Historical results remain frozen and may be Outdated while controls differ. Config restore does not execute an analysis or masquerade as an SS completion that invalidates Quick. Load configuration before listener registration where practical; asynchronous imports must not replace values the user has already touched.

## 7. Migration, compatibility and historical results

Precedence:

1. A supported explicit configuration for that exact kind in the plan wins.
2. A newer eligible accepted in-memory event wins an older disk version while pending/retrying.
3. Only if no entry exists, an approved migration may derive a candidate from the same plan/shape/kind's compatible frozen historical request.
4. Otherwise retain existing defaults; never invent a successful run.

Snapshot data is sufficient for the eight MC modes, couple SS/Exhaustive/Weighted and individual Weighted. Quick and individual SS/deterministic lack cross-session result snapshots, so no historical fallback exists for them. Full request/result restoration is not required to extract settings; use typed frozen request data, never decode a fingerprint hash. Check provenance, algorithm/reference compatibility and optional data before offering migration. An Outdated result can still supply valid old temporary settings, but must not overwrite authoritative plan facts.

Recommend no silent migration write on ordinary opening: initialize an explicitly identified migration candidate only under an approved precedence policy, then persist on next eligible run or explicit migration. Alternative automatic one-time config-only adoption from a prior accepted snapshot needs approval. Never overwrite an existing or unknown-version configuration merely because a result snapshot is newer. Never rewrite snapshots/digests to add configuration metadata.

Existing Current/Outdated and historical PDF assessments continue to compare frozen results to current effective requests. Parameter restoration may legitimately make a result Current again, but must not set a stale boolean blindly or substitute historical financial plan/baseline data. Keep MC temporary numeric V2 equivalence and exact plan/baseline fingerprints unchanged.

## 8. Proposed implementation stages and anticipated files

Paths below are relative to `src/main/java/com/daviddunn/retirementplanner/` unless stated. This is the anticipated review inventory, not a claim that every listed existing file must change. Avoid unrelated refactors.

### 10B — Typed schema and safe plan transactions

Proposed additions:

- `domain/analysis/parameters/AnalysisParameterConfigurations.java`: versioned immutable container, bounded unknown-entry preservation.
- `domain/analysis/parameters/AnalysisParameterConfiguration.java`: allowlisted typed parameter families/provenance; reuse existing `AnalysisKind` identities.
- `persistence/PlanConfigurationTransaction.java`: exact configuration-only patch, binding/conflict validation and atomic verification.
- `app/analysis/parameters/AcceptedAnalysisParameters.java`: immutable eligible handoff.
- `app/analysis/parameters/AnalysisParameterCoordinator.java`: write ordering, durability, retry, drain.

Expected existing changes: `domain/model/RetirementPlan.java`; `persistence/JsonRetirementPlanRepository.java`; narrow `persistence/AtomicFileWriter.java` commit-guard extension if required; `ui/controller/ApplicationController.java`; `ui/MainApplication.java` for drain ownership. Explicit metadata classification in `app/socialsecurity/results/AnalysisFingerprintService.java`, `app/montecarlo/MonteCarloFingerprintService.java`, `app/montecarlo/MonteCarloPlanDocument.java`; audit/update `app/socialsecurity/RetirementPlanScenarioCopyService.java` serialization handling only.

Gate: old JSON/constructor compatibility, unknown versions, exact numeric token preservation, dirty-plan isolation, external conflict tests, failed atomic write, manual save ordering and Save As identity. All old financial fingerprint digests identical with config section absent/present. No financial engine changes.

### 10C — MC acceptance and rollback integration

Expected changes: `ui/montecarlo/MonteCarloAnalysisView.java`, `MonteCarloStrategyComparisonView.java`, `MonteCarloSession.java`, `MonteCarloStrategyComparisonSession.java`, `MonteCarloResultLifecycle.java` only as needed for run-scoped acceptance/terminal hooks. Reuse existing prepared requests and persistence completion envelopes. Add scoped control mapping rather than a generic form framework.

Gate: all eight independent configurations, restart, matched seeds/scenarios unchanged, prior settings on cancellation/failure, no rollback across plan switches, numeric V2 compatibility, pending write shutdown and retry without simulation.

### 10D — SS acceptance, shared controls and migration

Expected changes: `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java`, `SinglePersonIntegratedView.java`, `SinglePersonMortalityView.java`, `SocialSecurityAnalyzerJobController.java`, `CurrentStrategyBaselineView.java` for guarded application; inspect `IndividualCompletionPersistence.java` to avoid coupling parameter acceptance to weighted-only snapshot persistence. Reuse existing request factories; change them only if a small immutable effective-parameter capture is otherwise inaccessible.

Proposed `app/analysis/parameters/AnalysisParameterSnapshotImporter.java` extracts only supported old frozen settings if migration is approved. A narrowly scoped UI control adapter may be added with a concrete name per family, not a generalized UI/state framework.

Gate: all seven identities including modes without result persistence; Quick retains current prerequisite/readiness; one couple shared control instance; individual mortality independence; optional baseline policy approved/tested; no new financial runs during initialization.

### 10E — Acceptance

Run focused then complete Maven suite, frozen snapshot compatibility, seeded golden regressions, automated restart/save fault tests and rendered/native Windows acceptance. No schema/algorithm compatibility ID changes outside the additive parameter contract.

### Exact anticipated test files

New tests (under mirrored `src/test/java/com/daviddunn/retirementplanner/`):

- `domain/analysis/parameters/AnalysisParameterConfigurationsTest.java`
- `persistence/PlanConfigurationTransactionTest.java`
- `app/analysis/parameters/AnalysisParameterCoordinatorTest.java`
- `app/analysis/parameters/AnalysisParameterSnapshotImporterTest.java` if migration approved
- `ui/montecarlo/MonteCarloParameterRestorationTest.java`
- `ui/socialsecurity/SocialSecurityParameterRestorationTest.java`

Extend existing `persistence/JsonRetirementPlanRepositoryTest.java`, `TransactionalPlanPersistenceTest.java`, `ui/controller/ApplicationControllerTest.java`, `ui/socialsecurity/AnalysisFingerprintTest.java`, `SocialSecurityAnalyzerJobControllerTest.java`, `app/montecarlo/MonteCarloNumericCompatibilityTest.java`, `MonteCarloRestoredFingerprintInvestigationTest.java`, `MonteCarloResultLifecycleTest.java`. Existing frozen weighted, SS, Exhaustive and MC codec/repository compatibility suites remain regression evidence; do not regenerate fixtures or goldens.

## 9. Regression plan and audit verification

Required implementation tests:

- Each of 15 accepted kinds persists exact effective parameters, not edited controls; real restart/new controller restores them without financial work.
- All numeric values/scales preserved; MC formatting-equivalent settings remain compatible; genuine changes/revert behave correctly; plan/baseline scale-sensitive contracts unchanged.
- Separate couple/individual, MC analysis/comparison/fixed/longevity slots; inactive old household configurations survive safely.
- Cancel before/during work, fatal failure, validation failure, dialog close, stale callback and newer edit/acceptance races; prior configuration and result survive.
- Parameter and snapshot disk outcomes independently tested in all success/failure combinations. Retry never computes. Normal Save/Save As interleavings cannot drop newer accepted metadata.
- Config-only write leaves durable financial fields byte/token-equivalent and live financial edits dirty; UUID/path/hash conflict, external writer, unsupported schema and unknown-entry cases preserve original files.
- Fault-inject every atomic stage, read-only/disk-full behavior, backup recovery, queue saturation and shutdown drain. Application Memory Only behavior has no implicit plan save.
- Migration precedence, missing legacy config, incomplete optional baseline context, source labels and shared-control identity/listener counts.
- Throwing simulation/projection factories prove no calculations during configuration initialization, migration or retry. Historical charts/PDFs/snapshots remain exact.

Executed existing focused audit tests using IntelliJ bundled Maven and project `.codex-m2/repository`, `-DargLine=-Xmx4g`:

`MonteCarloNumericCompatibilityTest,MonteCarloRestoredFingerprintInvestigationTest,SocialSecurityAnalyzerJobControllerTest,TransactionalPlanPersistenceTest,AnalysisFingerprintTest,MonteCarloResultLifecycleTest`.

**169 tests passed, zero failures/errors/skips; Maven BUILD SUCCESS; 20.159 seconds.** Log: ignored `target/phase10a-audit.log`. Tests were executed, not merely inspected. No new tests were written. No full suite was run for this audit; the previously reported full-suite numbers are not a new verification claim.

## 10. Risks and decisions requiring approval

1. **Unsaved/legacy plans:** recommend Memory Only until a real filename exists; first explicit Save includes accepted parameters. Permit path/hash-bound config-only writes to opened legacy files without silently assigning UUID, or require their first explicit migration Save. A guarantee of automatic durable JSON for never-saved plans is impossible without choosing a file/autosaving financial data.
2. **Unrelated dirty edits:** approve configuration-only durable-document patching while financial edits remain unsaved. This can correctly produce an Outdated result after restart. Do not silently save those edits.
3. **Shared controls:** approve per-kind pending buffers/last-success initialization over one physical couple control set. This preserves independent configurations but changes which accepted values appear when navigating compared with one global session value.
4. **Optional weighted baseline:** current successful searches may include invalid/missing optional baseline text. Exact text/source is frozen and participates in baseline fingerprint context. Do not block a previously valid search or silently normalize its financial/context contract. Recommended typed unavailable-baseline state plus frozen explanatory provenance, restoring only valid editable values; this can conservatively remain Outdated when original invalid text cannot be reproduced. Alternatively explicitly allow restoring bounded optional text as historical comparison context. Decide before implementation; storing arbitrary partial text as a successful numeric configuration conflicts with the objective.
5. **Validation/rejected admission:** approve rollback after showing validation/error information (literal cancellation/failure requirement), or explicitly retain invalid text for correction. The latter is today's usual editing behavior but is an exception to broad rollback wording.
6. **Legacy snapshot import:** recommend explicit/opt-in adoption when no configuration exists; no automatic overwrite on open. Approve whether one-time automatic adoption/write is desired instead.
7. **External editors/processes:** approve cooperative-lock plus exact conflict detection and documented noncooperating-writer limitations. Do not claim JVM locking alone prevents external lost updates.

No financial-model change or new dependency is recommended. The work does require an approved additive plan schema and a narrowly scoped automatic configuration transaction. Existing snapshots remain separate and retain all current safety limits.

## 11. Final Git state and review gate

Only new file: `PHASE-10A-ANALYSIS-PARAMETER-PERSISTENCE-AUDIT.md`. No production/test/resource/pom changes. No staged changes, commits, pushes, resets or discarded work. Generated Maven output is under ignored `target/` and is excluded from the deliverable. `git diff --check` is part of final verification; the untracked report also requires whitespace inspection because ordinary diff does not include it.

Stop at Phase 10A. Review and approve the schema/transaction approach and the seven policy decisions above before implementing Phase 10B.
