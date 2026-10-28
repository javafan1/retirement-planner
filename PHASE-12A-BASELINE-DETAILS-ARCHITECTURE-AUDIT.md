# Phase 12A — Baseline Details Architecture Audit

## 1. Executive summary and approval gate

**Audit only; no feature implemented.** Modern baseline capture now deep-copies configured financial inputs through Jackson. It does **not** persist the original annual projection, its completion timestamp, or algorithm/reference-data provenance. The displayed current projection retains only a small person/election summary, not its complete effective inputs. Reading live plan fields when opening the proposed modal would therefore be unsafe.

Recommended smallest design, requiring approval: reuse the existing baseline input snapshot; capture complete immutable input/chart context alongside each successful normal projection; consume only prepared baseline/current values in the modal. Label the cached baseline curve **rebuilt from saved baseline inputs using the current engine**, not an original archived calculation. Original capture-time results across restarts would need a separately approved persistence extension.

Approval is needed for that provenance policy, current-result context capture, legacy missing/defaulted inputs, and collection matching without durable entity IDs. No financial calculation, fingerprint, persistence, button, modal, CSS or test changes were made. Stop before Phase 12B.

## 2. Preparation and Git inventory

Read AGENTS.md, Break-Even analysis/context documentation, relevant Phase 9 snapshot/historical PDF reports, Phase 10B configuration-only transaction and Monte Carlo V2 compatibility material, and Phase 11D. Inspected actual model, controller, repository, MainWindow, Results Summary, chart and Break-Even source/tests.

Branch `main`; HEAD `e620bec4a0011ab12d15fce670bff5011093d9da`; index empty. Initial tree: **10 tracked modifications and 15 untracked Phase 11 files**. All preserved. This phase adds this report only; final untracked count is 16. Logs are ignored under target.

Modified production paths, relative to `src/main/java/com/daviddunn/retirementplanner/`:

- `app/export/IntegratedAnalyzerPdfExporter.java`
- `app/export/IntegratedAnalyzerReport.java`
- `ui/socialsecurity/AnalyzerNavigationView.java`
- `ui/socialsecurity/ClaimingStrategyHeatMapView.java`
- `ui/socialsecurity/IntegratedAnalysisResultTabs.java`
- `ui/socialsecurity/LongevityWeightedIntegratedView.java`
- `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java`
- `ui/socialsecurity/WeightedResultPersistence.java`

Modified tests under `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/`: `CoupleSocialSecurityPersistenceTest.java`, `ExhaustivePersistenceTest.java`.

Pre-existing untracked production files under `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/`: `AnalysisPdfData.java`, `ClaimingAgeGridView.java`, `QuickComparisonReportAdapter.java`, `QuickReportSession.java`, `ResultNavigationGuard.java`, `SocialSecurityClaimingGridAdapter.java`, `SocialSecurityOnlyReportAdapter.java`, `SocialSecurityReportSession.java`.

Pre-existing untracked tests under `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/`: `ResultNavigationTest.java`, `SharedClaimingAgeGridTest.java`, `SocialSecurityAndQuickPdfTest.java`.

Pre-existing untracked reports: `PHASE-11A-SOCIAL-SECURITY-UI-PDF-AUDIT.md`, `PHASE-11B-SHARED-GRID-AND-NAVIGATION.md`, `PHASE-11C-SOCIAL-SECURITY-AND-QUICK-PDF-EXPORT.md`, `PHASE-11D-FINAL-INTEGRATION-AND-REGRESSION-AUDIT.md`.

No unrelated change was found or modified. No resource/build changes or staging/history operations were performed.

## 3. Verified baseline lifecycle

| Step | Actual authority and behavior |
|---|---|
| Capture | `MainWindow.onSaveCurrentAsBaseline()` confirms replacement, then controller `saveCurrentAsBaseline("Baseline")` captures **current configured inputs**, not the displayed Projection. No successful result is required/copied by the factory. |
| Freeze | `ProjectionBaselineFactory.create()` calls `RetirementPlanSnapshot.fromRetirementPlan()`, JSON-round-tripping only the snapshot to avoid recursive prior-baseline copying. Stores snapshot, local capture time and description. |
| Memory/dirty | Assigns plan baseline, clears baseline projection/non-investable caches, marks modified and increments source revision. This is not a durable plan save. |
| Save | Complete plan JSON includes baseline. Existing repository lock, verification, atomic replacement and backup mechanisms apply. Configuration-only auto-save must not save a dirty new baseline. |
| Restart | JSON restores saved inputs/date/description. Annual baseline/current results are not restored from this record. |
| Prepare baseline results | `ApplicationController.getBaselineProjection()` lazily calls `BaselineProjectionService.projectBaseline()`, which rebuilds a plan from snapshot fields and invokes the **current** ProjectionEngine/rules. Result and small BreakEvenPlanSummary are cached. |
| Cache ownership | `cachedBaselineIdentity` is compared with the selected plan's actual baseline object; a new identity clears baseline projection/non-investable/summary caches. This is runtime invalidation, not historical compatibility verification. |
| Prepare current results | `getCurrentProjection()` lazily calls `projectWithOutcome(currentPlan)`. Only a completed projection is exposed; insufficient-funds prefixes are not complete results. It captures a small person/election summary. |
| Summary | MainWindow refresh invalidates/recalculates current, loads Results Summary and prepares Break-Even. Summary calls `compareProjections()`, which may lazily calculate baseline/current and non-investable results. |
| Break-Even click | Consumes prepared BreakEvenAnalysisResult; cached analysis reads populated projections only. MainWindow adds existing explanatory/mortality context. That context factory builds mortality scenarios, though no financial projection; the new details modal should not invoke it unnecessarily. |
| Replacement/clear | Replacement confirmation warns old baseline cannot be recovered. Model `setBaseline(null)` permits removal; no separate clear-baseline UI/controller action was found. |
| Save As | Existing independent plan UUID save copies baseline inputs/date/description as part of JSON. Baseline has no source-plan UUID; destination UUID cannot prove original ownership. |
| Plan switch | Current caches invalidate and baseline identity is rechecked. A future modal must own a captured session, never retrieve Plan B values through a Plan A view. |

**AGENTS.md caveat correction:** current source and RetirementPlanSnapshotTest prove deep-copy isolation in `fromRetirementPlan()`. Its public constructor and getters still expose mutable domain objects; detached is not deeply immutable by API. Previously overwritten historical values cannot be recovered.

## 4. Exact persistence structure

Existing logical JSON shape, not a proposed extension:

```text
baseline:
  snapshot:
    household
    accountPortfolio
    planningAssumptions
    rothConversionRequest
    nonInvestableAssets
  savedAt
  description
```

Household contains persons, income and expenses; accounts include configured ownership/type/balance, participant, inherited and opening-RMD data. Planning assumptions contain economic, tax, withdrawal, death/survivor, state and horizon configuration. There is no baseline schema/version, run ID, source plan ID, input digest, rule/algorithm digest, annual result graph or original completion time. Root planId/analyzerConfigurations are not part of this historical snapshot.

`savedAt` is LocalDateTime capture time without timezone, not successful calculation completion. Projection permits addYear; getYears copies only the list. ProjectedAccountSnapshot holds an Account reference plus an immutable ending balance. A dialog should receive detached display values, not mutable domain graphs.

Legacy defaults are significant: PlanningAssumptions supplies missing economic/tax/withdrawal/death/state defaults; optional null/missing values can become indistinguishable after deserialization. A getter alone does not prove the original field was explicitly recorded. Do not label a newer release's default as proven original history.

## 5. Historical availability matrix

“Fully preserved” refers to supported explicitly recorded modern configuration, not original financial outputs or every legacy file.

| Category | Classification | Authority / limitation |
|---|---|---|
| Complete configured financial input roots | Fully preserved modern; partially preserved legacy provenance | Five snapshot roots cover configured financial data; runtime rules/versions and analyzer metadata are excluded. |
| Household/persons | Fully preserved where recorded | Names, membership, birth/mortality information. Age is derivable at a specified recorded date, not today's Person.getAge(). Roles exist, durable person UUIDs do not. |
| Economic assumptions | Fully preserved where recorded | Saved return/inflation/COLA and configured growth values. Missing/default provenance may be unknown. |
| Social Security | Fully preserved configured records where recorded | Benefit/valuation year, ownership, claiming age/start and survivor/death policy. No optimizer result, mortality table or session discount settings. Phase 10 analyzer settings are not deterministic plan inputs. |
| Pensions/guaranteed income | Fully preserved supported records | Amount/survivor amount, dates, COLA, ownership, participant facts; annual calculated totals are not archived here. |
| Accounts/ownership | Fully preserved configuration | Initial balances/type/ownership/participant/inherited/opening RMD data. Qualification/tax behavior is derivable from saved types. No durable account ID. |
| Annual account values | Not preserved durably as original results | Exist only in prepared rebuilt projection; initial configured balances are recorded. |
| Expenses | Fully preserved configuration | Amount, growth/type and dates in household list; no durable expense ID. |
| Taxes | Fully preserved user settings; partial effective provenance | Saved filing/rates/growth/state settings; original rule/table/software contents not pinned. |
| Roth request | Fully preserved recorded request; legacy absence may be ambiguous | Enable/start/amount/frequency/stop/strategy/custom target. Annual actual execution is not stored. |
| RMD inputs | Fully preserved configured facts where present; partial rule provenance | OpeningRmdAccountData, inherited and employer participant information; statutory table versions/annual distributions not archived. |
| Horizon/death/survivor | Fully preserved where recorded | Start, length, death scenario/year, survivor age and post-death expense factor; actual chart years must be labeled separately. |
| Non-investable assets | Fully preserved configuration | Value/growth; annual results are separately calculated/cached. |
| Original ProjectionYears | Not preserved | Current baseline cache is a recomputation from saved inputs using current engine/rules. |
| Capture date/description | Fully preserved | savedAt/description; timezone/completion time not recorded. |
| Original plan identity/version | Not preserved | Current/destination UUID is not original ownership evidence; no baseline version/fingerprint. |

No reverse engineering of inputs from result values is justified.

## 6. Current projection context — approval blocker

Controller `currentProjectionAssumptions` is only BreakEvenPlanSummary: person name/birth date/claim age/date/mortality category. There is no full financial request or projection start/completion timestamp associated with currentProjection.

`markModified()` marks dirty/revision but does not itself invalidate caches. Normal edit/apply paths refresh/invalidate/recalculate, but pending controls, direct edits or unusual callers can leave plan fields distinct from displayed results. ResultsSummary stores live plan and supplied projection references; assumptions controls and chart annotations read the plan at load. Neither those fields nor the durable file are a safe general source of displayed-result inputs.

**Proposed, not implemented:** capture complete immutable inputs plus plan/session/binding/revision and appropriate rule/build provenance at normal projection preparation; publish that context atomically with the completed result and timestamps. Do not change financial engine semantics or silently backfill old context. Failure/readiness rejection publishes no new paired context. Invalidating/replacing results invalidates/replaces context. An old retained result can be historical only with its own captured inputs.

Details opening must consume a prepared immutable session, not call lazy `getCurrentProjection`, `getBaselineProjection`, `compareProjections` or services. Existing caches without complete context show **Not recorded**/unavailable until an ordinary user recalculation, not a simulation triggered by opening the modal. A dedicated display assessment must not change analyzer fingerprint contracts.

## 7. Results Summary action integration

Actual insertion: `ResultsSummaryView.createBaselineComparisonSection()` (~980), currently heading, Break-Even button, metric cards. Put the two actions in one small FlowPane/HBox with View Baseline Details immediately after Break-Even in visual/keyboard order. Mirror `setOnBreakEvenAnalysis` callback ownership through MainWindow, without controller calls from the dialog.

Section currently hides without plan, baseline or current result. Break-Even enables for at least one comparable year, not a nonzero difference. Identical results are valid. Summary comparison sums each projection over its own horizon and compares each final endpoint; Break-Even intersects actual calendar years. Do not replace either financial convention with the parameter table.

| State | Recommended details policy, pending approval |
|---|---|
| No baseline | Existing section hidden/action unavailable. |
| Baseline, no current completed result | Preserve hidden-section scope initially; a standalone input-only inspector is a separate decision. |
| Identical | Enabled with prepared safe context; changed-only shows an honest empty-state, not no-baseline. |
| Different | Enabled with prepared context; changes are neutral, not recommendations. |
| Stale current with complete captured context | Historical viewing may be allowed with clear status; never pair with pending edits. |
| Current context missing | Disable full comparison with reason, or approved explicit partial Not recorded view. |
| Baseline incomplete/incompatible | No automatic projection retry; unavailable chart/rows or disabled unsafe session. |
| Different/no-overlap horizons | Chart retains baseline years; parameter comparison does not require common years. Break-Even rules remain independent. |
| Plan/baseline/result ownership changes | Reject stale publication; close or explicitly detach old captured modal. |

## 8. Investable Assets chart reuse

Current product/domain spelling is **Investable Assets**; the requested “Investible” wording must not create a different financial metric.

ResultsSummary.updateCharts builds `ProjectionChartModel.from(years, nonInvestable, people, expectedAccounts)` and loads `ProjectionChartView`. Exact asset values are ProjectionYear.getEndingInvestableAssets(), not reconstructed totals or inflation-adjusted values.

- Immutable model sorts actual calendar years, rejects duplicates and retains BigDecimals, composition and claim/Roth/RMD annotations. Its full builder reads existing other annual metrics too; do not fabricate partial ProjectionYears just to draw assets.
- View is a VBox with metric selector, custom legend, plot, notice and selected-year details; default is Investable Assets.
- With valid reconciled account composition it uses a nonanimated **StackedAreaChart**, existing category series/decorations and total line. Otherwise a **LineChart** of authoritative totals with a composition-unavailable notice. It is not always a single line.
- NumberAxis years are integral calendar years, bounds first−0.5/last+0.5 with responsive ticks. Dollar axis uses existing UIFormatters.money, forced zero and “Projected dollars (same values as table)”. Rendering coordinates are not financial calculations.
- Reuses chart-card/title styling, `chart-timeline.css`, `projection-chart.css`, wrapped custom legend, hover tooltips/selection and actual configured election markers. Model selection never invokes a controller.
- Plot min height 300, preferred/max 330, fluid width. Empty model disables metric and shows No projection available. Preserve actual early/partial year ranges; do not extrapolate or expose rejected funding prefixes as complete projections.

Recommended narrow reuse: overload/factory for title and fixed metric/hidden selector, leaving existing defaults intact; separate JavaFX node per modal, never reparent current Results chart. Baseline-only is the smallest faithful first chart. Current overlay is not a public multi-model API today; defer it or separately approve a thin two-line view using shared styling. No broad chart refactor is required for baseline-only.

**Account identity hazard:** composition checks actual Account identities, not names. Build chart data with the registry that supplied the prepared projection. An independently deep-copied input context has different identities and must not be matched by name to fabricate composition. Baseline service currently uses the snapshot account portfolio. Freeze pure chart values alongside result preparation; do not regenerate them on modal open from current accounts/people.

## 9. Proposed immutable parameter model

Suggested pure types, not implemented: BaselineDetailsSession, ProjectionInputContext, ParameterComparison/Category/Row and allowlisted typed values. Row fields: category, stable parameter path, label, baseline/current values, exact optional difference, units, changed/unchanged/unknown status, provenance/availability per side and tooltip. Session holds captured ownership/result identities, known timestamps, chart models and copied rows; no controller/controls/mutable plan.

- Currency: exact BigDecimal Current minus Baseline, dollar formatting only at presentation.
- Rates: rate delta ×100, explicitly **percentage points**; 0.05−0.04 = +1 point, not 25% relative growth.
- Numeric display equality: propose compareTo equality, preserving source scale. This is display-only; no normalization of financial plan/baseline fingerprints or expansion of Monte Carlo V2 rules.
- Counts/age elections: integer count/year differences. Historical ages require a known reference date, not today's age helper.
- Dates: both original dates plus signed elapsed days where meaningful; no invented fractional years.
- Enums/text/booleans/elections: both values and Changed/Unchanged, no numeric delta.
- Absence, explicit null, not applicable and unrecorded remain distinct. Unknown historical side displays **Not recorded**, with no delta and UNKNOWN status, never UNCHANGED.
- Changed-only should retain unknown/uncomparable rows or a clearly visible separate unavailable-data group; do not conceal missing history as unchanged. Final filter wording needs approval.

Actual categories: persons/membership; retirement/elections/horizon/death; accounts and inherited/participant facts; Social Security; pensions; expenses; economic assumptions; federal/state/local taxes; withdrawal order; Roth; opening RMD/participant settings; non-investable assets. Temporary Monte Carlo/optimizer settings are not normal deterministic projection inputs.

### Collection identities

Inspected account, expense, pension/income and non-investable schemas have no durable entity UUID. People have PRIMARY/SPOUSE roles. Object identity/list index does not survive JSON; duplicate names are valid. Owner/type/name is descriptive, not proof of identity through rename/replacement.

Conservative initial recommendation: match exact configured-value records as multisets, preserving duplicate multiplicity; show unmatched records as additions/removals or ambiguous correspondence, not a proven rename/change. Composite-key pairing requires qualified provenance and approval, never forced name matching. Fully reliable field-level entity tracking after rename/reorder requires separately approved stable IDs/schema migration. Do not silently include that migration in a modal feature.

## 10. Persistence alternatives

| Approach | Compatibility and recommendation |
|---|---|
| A: reuse existing baseline snapshot | Recommended foundation: no JSON change/duplicate financial graph; convert mutable data to immutable rows. Original annual outputs and legacy presence provenance remain unavailable. |
| B: additional bounded assumption snapshot | Duplicates existing inputs; justified only for presence/provenance/version context. Requires approved optional schema, strict bounds and legacy rules. No simulations/results copied into it. |
| C: optional metadata on ProjectionBaseline | Preferred later location for source UUID/version/rule or presence information if required. Preserve five-field financial snapshot shape; protect unknown future versions. Save As retains origin while destination gets independent UUID. |
| D: partial legacy view | Honest fallback for supported recorded fields, Not recorded for unrecoverable information. Defaults must be disclosed/unavailable, not asserted original. Overwritten history cannot be migrated back. |
| Original annual curve across restarts | A–D do not provide it. Needs a separately approved bounded result/chart artifact captured from a successful result, compatibility metadata and atomic ownership. Existing Phase 9 codecs can inform design, not be assumed to archive ordinary baselines. |

**Smallest proposal: A + D, new in-memory complete current-result context and cache-only chart clearly labeled rebuilt.** No new baseline schema is inherently required for that narrower honest scope. If original capture-time curve/identity/version is mandatory, approve C plus bounded artifact design before coding. Storage size was not measured because no new payload exists; do not invent a budget or duplicate assumptions unnecessarily.

Any future extension must preserve token/BigDecimal fidelity, unknown-version safety, transactional save/reload/Save As and configuration-only isolation. AnalysisFingerprintService and MonteCarloFingerprintService exclude root baseline/config metadata from ordinary plan groups; paired Monte Carlo separately fingerprints its effective reconstructed baseline financial plan. Do not assume arbitrary new nested metadata is ignored. Keep metadata outside financial snapshot/reconstruction and prove existing digests unchanged; no historical hash/version rewrite or financial equivalence change.

## 11. Proposed modal and highlighting

Read-only owner-bound resizable modal using BreakEvenAnalysisDialog screen-capped sizing, Close and scroll conventions. Header: Baseline Projection Details; known capture/current completion times; explicit provenance/status/Not recorded. No Apply/Save/recalculate or new PDF action.

Chart: “Investable Assets — Baseline Projection”, original prepared years/styles, unavailable message rather than automatic calculation. Parameter | Baseline | Current | Difference table, collapsible TitledPane categories, Show Changed Values Only checkbox. Prefer bounded/virtualized category tables or one grouped virtualized table; avoid many unbounded nested scrollers.

Subtle semantic amber on changed baseline/current cells; neutral unchanged; visible difference units and textual changed/added/removed/unavailable indicators. Never imply an input increase is favorable. Existing financial green/red only where outcome interpretation is unambiguous. Inherit app styling and test light/dark contrast, keyboard/filter/expansion/close and reduced-window behavior. No theme/DPI/assistive-technology certification was performed in this audit. Freeze modal content at opening; no silent Plan A→B refresh.

## 12. Risks and recommended implementation gates

Risks: lazy getters calculate on FX; old outputs mixed with live controls; deep-copy mistaken for immutable API; current-engine replay labeled original; defaulted legacy data mislabeled; wrong chart account registry; ambiguous name matching; mismatched horizons; stale plan/dialog callbacks; metadata changes affecting paired fingerprints; parameter auto-save writing dirty inputs; altered summary/Break-Even conventions; chart annotations reconstructed from current people; broad chart/PDF changes.

Proposed stages: **12B approved context/capture and pure comparison fidelity**; **12C chart/session/modal integration**; **12D legacy/reload/identity and native visual verification**, then final regression. These are recommendations, not implementation authorization.

Anticipated production files after approval:

- `ui/views/ResultsSummaryView.java`: adjacent callback/action and prepared enablement.
- `ui/MainWindow.java`: prepared session ownership/dialog lifecycle.
- `ui/controller/ApplicationController.java`: successful context/chart capture and cache-only reads/invalidation.
- New agreed `app/baseline` immutable capture/comparison/session classes.
- New `ui/baseline/BaselineDetailsDialog.java`, `BaselineDetailsView.java`.
- Small `ui/charts/ProjectionChartView.java` overload/wrapper, existing model/presentation reused.
- Scoped changed/unavailable CSS, reusing current chart styles.
- Only with separate approval: optional `domain/baseline/ProjectionBaseline.java` metadata/codec/validation. No engine/fingerprint/entity changes assumed.

## 13. Automated testing plan

1. Extend ResultsSummaryBaselineMetricsTest: adjacent order, no baseline/current, identical/different, independent Break-Even readiness, missing/stale/incompatible context and wrong ownership.
2. Extend BreakEvenControllerTest/new context tests: exact successful inputs, unsaved/pending edits cannot alter accepted context, failure/invalidation, replace/clear, plan switch, Save As and obsolete publication.
3. Extend RetirementPlanSnapshotTest/ProjectionBaselineTest/BaselineProjectionServiceTest: detached modern inputs, UI immutable boundary, legacy defaults/provenance and configured-input versus rebuilt-output distinction.
4. New pure row tests: exact dollars/scales, percentage points, age/day differences, enum/text/boolean/elections, all optional/unavailable states, categories/order and changed-only behavior.
5. Collection tests: duplicates, reorder, same names/different owners/types, additions/removals and rename ambiguity; stable-ID changes only if approved.
6. Extend ProjectionChartModelTest/ReconciliationTest/ViewTest: exact source values, correct account identity, annotations, original/different/nonoverlapping years, empty/partial data and independent chart selection.
7. Extend JsonRetirementPlanRepositoryTest/TransactionalPlanPersistenceTest: existing JSON shape, exact decimals, legacy/save/reload, failures/backups and Save As; optional schema only after approval.
8. Existing fingerprint, Monte Carlo compatibility and Phase 10 tests: unchanged digests/all fifteen identities, dirty/revision isolation, retries and paired baseline semantics.
9. New modal tests using throwing engines/repositories: open/filter/expand/selection never calculates or saves; original plan/context unchanged; closed/obsolete/cross-plan callbacks rejected.
10. Existing BreakEvenCompactLayoutTest/chart patterns for light/dark and reduced sizes: labels, amber contrast, keyboard accessibility, chart/table/filter scrolling. Existing PDFs, financial goldens and seeded simulations remain unchanged.

## 14. Verification actually executed

IntelliJ bundled Maven, required `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`, `-DargLine=-Xmx4g`, existing tests only:

`RetirementPlanSnapshotTest, ProjectionBaselineTest, BaselineProjectionServiceTest, ProjectionComparisonTest, ProjectionComparisonServiceTest, ResultsSummaryBaselineMetricsTest, BreakEvenControllerTest, BreakEvenContextFactoryTest, BreakEvenAnalyzerTest, BreakEvenAnalysisViewTest, BreakEvenCompactLayoutTest, ProjectionChartModelTest, ProjectionChartViewTest, ProjectionChartReconciliationTest, JsonRetirementPlanRepositoryTest, TransactionalPlanPersistenceTest`.

**93 total; 93 passed; zero failures/errors/skips; BUILD SUCCESS; 19.684 seconds.** Log: `target/phase12a-focused.log`. No source/test changes or new feature acceptance claimed. Full suite was not repeated for this audit-only report; unchanged-source Phase 11D full verification (3,398 passed, 44 skipped) is prior evidence, not a Phase 12A execution. No financial behavior changed.

`git diff --check` exits 0. Branch/HEAD/index unchanged. Final tree: 10 preserved modified tracked Phase 11 files, 16 untracked files (15 preserved Phase 11 files plus this report). No button/modal/schema/CSS implementation or unexpected artifact added.

## 15. Explicit approvals needed before Phase 12B

1. **Chart truth:** cache-only rebuilt baseline curve with explicit current-engine provenance, or original archived curve across restart requiring new storage design. No projection on opening.
2. **Current context:** complete immutable successful-run input/chart capture. Old incomplete contexts are unavailable/Not recorded until ordinary recalculation, never backfilled from live plan.
3. **Legacy behavior:** partial recorded-input view, default/unknown provenance and whether insufficient context disables action or permits explicit partial inspection.
4. **Collection matching:** conservative exact/multiset/ambiguous correspondence, or separately approved durable entity IDs for reliable rename tracking.
5. **Persistence:** initial A/D proposal requires no extra baseline schema. Source identity/version/presence/original annual artifact needs explicit approval and compatibility proofs.
6. **Presentation:** baseline-only chart initially; unchanged results inspectable; unknown information visible in changed-only mode; safe stale contexts explicitly historical.

No changes to baseline financial semantics, fingerprints, save coordination, algorithms or historical data are authorized by this audit. Stop for review and approval; do not begin Phase 12B.
