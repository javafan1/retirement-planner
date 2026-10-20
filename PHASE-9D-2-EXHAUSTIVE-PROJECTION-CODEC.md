# Phase 9D-2 — Gate 1 retained-projection codec

## 1. Result and authorized boundary

**Gate 1 fidelity proof passed for the current supported retained projection graphs. Stop for review.**

Implemented an explicit immutable retained-projection component and a standalone bounded gzip/JSON codec. It reconstructs `Projection`, every stored `ProjectionYear` field, nested account identities, source attribution, state/federal/Medicare details and the original fourteen metrics without calling the projection engine, Social Security calculator or mortality optimizer during restoration.

**No Exhaustive analysis payload, accepted-completion handoff, repository slot, UI restoration, heat-map integration or PDF integration was implemented.** `COUPLE_EXHAUSTIVE` remains unsupported by the analysis-envelope payload dispatcher. Gates 2–5 require separate authorization.

The updated roadmap supersedes the historical audit sequence: skip Quick persistence, preserve Quick functionality, prioritize Couple Exhaustive, and defer both remaining individual modes. No Quick or individual integration was changed here. Native acceptance of 9D-1 was reported by the user; it was not repeated or claimed as new testing in this gate.

## 2. Initial repository and inspected architecture

- Branch: `main`.
- HEAD: `4fddd4ee1eaebc8354b009b77a381459495d665c`.
- Initial working tree and index: clean. The completed 9D-1 work is committed at this starting point; nothing was reset, discarded or overwritten.
- Read `AGENTS.md`, the remaining-analyzers audit, 9D-1 implementation report and relevant 9B/9C persistence findings.
- Inspected actual `Projection`, `ProjectionYear`, `ProjectedAccountSnapshot`, account factory/types/inherited and opening-RMD data, `IncomeSourceAttribution`, pension/participant facts, Michigan decisions, production state results, Medicare/IRMAA and federal results, metrics, integrated strategy results/evaluator, codec and representative existing tests.

The current source confirms the audit's principal gap: complete projections cannot be losslessly persisted through their ordinary Jackson getters. Attribution and Michigan details have ignored accessors; attribution uses `Optional`/`OptionalInt` and opaque reference-based account identities. `Account` has mutable balance/participant/opening-history properties. `Projection` is a mutable list, and `ProjectionYear` has compatibility constructors with genuinely absent calculation objects.

## 3. Exact files changed

| Repository-relative file | Purpose |
|---|---|
| `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java` (new) | Versioned immutable graph, explicit account/income/state/Medicare DTOs, checked identity references, pure capture/reconstruction |
| `src/main/java/com/daviddunn/retirementplanner/persistence/RetainedProjectionCodec.java` (new) | Standalone fidelity codec using existing snapshot document bounds/configuration; no filesystem storage |
| `src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java` | Extract existing bounded document compression/reading into shared package-local operations; extend diagnostic field-name allowlist |
| `src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java` | Small immutable reconstruction factory preserving frozen participant facts while sharing restored account identity |
| `src/test/java/com/daviddunn/retirementplanner/domain/projection/RetainedProjectionCodecTest.java` (new) | 27 fidelity/boundary tests, reusing existing projection fixtures in their package |
| `PHASE-9D-2-EXHAUSTIVE-PROJECTION-CODEC.md` (new) | This report |

No engine, financial algorithm, request/default, state-tax rule, analyzer/controller, result/PDF view, financial golden, dependency or plan schema changed.

## 4. DTO design and field coverage

`RetainedProjectionSnapshot` has an independent component `schemaVersion = 1`, an ordered account registry, ordered annual DTOs and optional original integrated-strategy metadata. It does **not** implement `AnalysisPayload` and is not registered as a durable analysis mode.

Two capture entry points deliberately distinguish available information:

- `capture(Projection)` preserves the projection graph; the projection itself contains no metrics, original strategy or warning collection.
- `capture(IntegratedSocialSecurityStrategyResult)` additionally preserves the original `ProjectionMetrics`, complete evaluated strategy, advanced-SS-path flag and original ordered warnings. This is the appropriate entry point for later baseline/retained-candidate payload work. These values are copied, never recalculated from the projection.

### Annual data

The explicit `Annual` DTO captures all **41 nonstatic instance fields** in `ProjectionYear`, including the nested information represented by those fields. A field-inventory test fails if that source inventory changes without review.

| Area | Actual retained fields/data preserved |
|---|---|
| Position and age | Projection index, calendar year, primary reporting age |
| Assets and growth | Beginning/ending investable assets, investment growth, ordered account balances |
| Income/spending | Guaranteed income, complete annual household SS result, annual expenses, cash-flow need |
| Withdrawals | Portfolio withdrawal, tax-funding withdrawal, annual statutory RMD, pre-projection RMD, projection-period RMD, excess RMD |
| Roth | Requested amount, actual household/primary/spouse amounts, including absent spouse; derived shortfall getter verified exactly |
| Retained nonqualified assets | Beginning balance, growth, ending balance and every cash-settlement/attribution component |
| Federal tax | AGI, taxable SS, taxable income, deduction, income tax and exact combined effective rate |
| State tax | Jurisdiction/method/status, optional estimated amount, limitations/reasons, complete Michigan details and source-level eligibility decisions |
| Medicare | MAGI, participant count, all monthly/annual B/D premiums, total and complete IRMAA bracket |
| Estate | Estimated heir tax and after-tax estate |
| Source details | Ordered income attribution with original owner/account/destination, gross and optional taxable amounts, optional tax year, pension and participant facts |

Derived getters such as net cash flow, asset change, total income tax, conversion shortfall, retained aliases and IRMAA display are tested separately, including their existing absence/error behavior. No unsupported new reporting value is manufactured.

### Account data and identity

`AccountData` stores the exact available `Account.getCurrentBalance()` value, name, owner, type, opening-RMD history, participant facts and inherited-owner metadata. For ordinary engine outputs the account balance is the unchanged initial source-account balance; it is named `currentBalance` in the DTO to avoid asserting a different meaning for arbitrary mutable input graphs. Every annual ending balance is stored independently.

Account types use a fixed allowlist of the eleven current supported concrete classes: Traditional IRA, Rollover IRA, Roth IRA, Traditional 401(k), Traditional 403(b), Roth 401(k), Brokerage, Checking, Savings, Inherited Traditional IRA and Inherited Roth IRA. Reconstruction uses the existing explicit account factories and setters on **new detached accounts**. No Java class name is loaded from JSON.

The registry assigns local IDs by first encounter in the ordered annual account snapshots. Identical names and identical financial attributes do not merge separate accounts. Repeated references to an account across years, source/destination attribution and Michigan eligibility decisions point to the same newly constructed account. Ownership/type restrictions and inherited/opening-history rules remain those of the existing constructors.

Opening-RMD data retains distribution year, prior December 31 balance and prior distributed amount. Inherited metadata retains both original-owner dates and relationship. Account-specific withdrawal/conversion/RMD detail is retained through the existing attribution records; unavailable per-account data is not invented.

### Frozen reference facts

`IncomeSourceAttribution.AccountReference` deliberately exposes no mutable account. Its equality is identity-based, but participant facts are captured immutably and can differ from the account's subsequently edited facts.

The new `AccountReference.restore(Account identity, RetirementParticipantFacts frozenParticipantFacts)` factory preserves that distinction. It constructs an immutable reference and neither exposes nor mutates the supplied account. Existing `capture`, equality and hash behavior are unchanged. This is reconstruction plumbing, not a qualification or financial-rule change.

Each attribution reference stores a checked registry ID and its original frozen participant facts. Name/type/owner/inherited information comes from the same identity's immutable account attributes. A test edits employer participant facts after projection calculation and proves the original attribution facts and reconstructed identity link both survive.

## 5. Reconstruction and optionality

- Construct an isolated account registry through existing allowlisted factories.
- Rebuild source/destination references against that registry, retaining separately frozen participant facts.
- Rebuild complete Michigan decisions and production state results, Medicare/IRMAA and federal-tax value objects.
- Construct modern years using the existing complete `ProjectionYear` constructor.
- Construct legacy years with absent state/Medicare calculations through the existing compatibility constructor.
- Recapture each reconstructed annual DTO and require exact equality with the stored annual DTO. Reject reconstruction drift rather than silently losing fields, changing scale or applying new defaults.
- Assemble a new `Projection` in the original stored order.

The existing full constructor computes its reporting effective-tax-rate ratio. Its exact result, including scale, must match the stored rate through the equality guard. This uses no tax/projection service and does not replace any stored amount with a newly calculated financial result. Future constructor behavior that cannot exactly represent an older DTO is refused.

`Optional` and `OptionalInt` are explicitly represented as nullable DTO values and restored as the correct empty/present wrappers. Missing taxable amount stays unknown; known zero stays present with its original scale. Absent spouse SS components, spouse conversions and spouse lifetime metrics remain absent. Absent legacy calculation objects remain absent, including the original getter exception behavior where applicable.

Immutable scalar records (`HouseholdSocialSecurityResult`, `HouseholdCashSettlement`, `RetirementParticipantFacts`, `ProjectionMetrics`) are reused only as explicit leaf types. Their component schemas are pinned in tests. Mutable `Projection`, `ProjectionYear`, `Account`, opaque references and optional-bearing attribution/state graphs are never directly serialized.

## 6. State-tax and income fidelity

All twelve current production jurisdictions were tested: MI, FL, IL, PA, AK, NV, NH, SD, TN, TX, WY and IA. The codec stores results, not a newly selected tax adapter or recomputed estimate.

Michigan captures all nine calculation components, including every original eligibility decision with its full source attribution, qualification, eligible amount and reason, plus original limitations. Non-Michigan results retain absent Michigan detail. Income source order, conversion source/destination ownership, pension ownership/survivor amounts/start/end/COLA and participant facts are preserved.

Initial generic state fixtures were correctly rejected by PA/IA for unavailable pension legal-plan qualification. Tests were corrected to use the existing admitted `PennsylvaniaProductionProjectionTest.plan` fixtures for those jurisdictions. No engine/rule was changed to make a fixture pass. The broader Michigan/Florida fixtures retain pension coverage.

An unsupported state estimate is not zero and is not a successful annual projection: current admission/constructor behavior refuses it. The existing `StateTaxUnavailableProjectionTest` still passes. The codec does not bypass that condition or fabricate an annual zero-tax result.

## 7. Fidelity tests and independent oracle

`RetainedProjectionCodecTest` executes **27 cases**, covering:

- Four-year integrated results for every supported jurisdiction, including original warning strings, evaluated elections, all fourteen metrics and non-investable value in the original ending-net-worth metric.
- Genuine single/couple results with nullable spouse components and metrics.
- All eleven account types, primary/spouse/joint owners where allowed, duplicate account names, inherited information, opening-RMD facts and shared identity across every annual balance and attribution.
- Frozen participant facts distinct from later mutable account facts; immutable DTO isolation after original account-balance edits.
- Positive ordinary spending and tax-funding withdrawals, with unknown modeled-taxable attribution preserved.
- Capacity-limited actual Roth conversion with nonzero primary/spouse execution, original request, shortfall and conversion destinations.
- Death/survivor SS behavior and a pension with exact decimal monthly/survivor/COLA values, optional end date and participant facts.
- Unknown versus known-zero taxable amounts and absent optional owner/account/year/pension fields.
- Legacy absent tax/Medicare calculations, empty projections, exact ordering and all annual public getters.
- 1,024-, 1,070- and 4,096-character decimal tokens, with exact value/scale equality; rejection above the limit.
- Invalid account references, unsupported/missing component schema, unexpected fields/type metadata, trailing JSON, compressed/decompressed/depth bounds.
- Explicit source-field inventory and immutable leaf schema guards.

The independent test oracle traverses every original/reconstructed private domain instance field, including ignored attribution and opaque account identity data. Only tests use this reflection. Production mapping uses explicit getters/constructors. `BigDecimal.equals` is scale-sensitive; no numeric tolerance or blanket rounding is used. A persistent identity map verifies repeated original accounts correspond to the same restored account. Every zero-argument public annual getter is compared separately, and `getEndingBalance(restoredAccount)` is checked across years. This is stronger than comparing a DTO against its own recapture alone.

Restoration code has no `RetirementPlan`, `ProjectionEngine`, search, mortality factory or financial request dependency. Only fixture construction executes the engine. Metrics, warnings and elections are copied from completed fixture outputs before encoding.

## 8. Shared codec and legacy compatibility

The standalone `RetainedProjectionCodec` delegates document handling to package-local operations extracted from `AnalysisSnapshotCodec`. Existing analysis encoding still validates envelope/payload digests before the same compression operation. Existing analysis decoding still performs schema/UUID/kind checks and strict payload dispatch after the same bounded decompression/document scan.

Unchanged protections: 32 MiB decoded document, 16 MiB compressed document, 4,096-character numeric token, two-million-token, 100,000-array, depth-80 and string-length constraints; duplicate/unknown/missing fields, scalar/enum constraints, complete-document trailing-token checks and no unsafe default typing. Additional numeric-diagnostic paths are drawn only from explicit DTO/leaf record fields, never financial values.

Both frozen pre-9D-1 weighted fixtures passed unchanged, preserving exact raw/gzip bytes, field order, payload digests and all numeric values/scales:

| Frozen file under `src/test/resources/analysis-snapshots/` | SHA-256 |
|---|---|
| `weighted-couple-v1.json.gz` | `fb4300180df4698722cbdde8b46359d21b7f0759736b3c177dc4d2e1458cc80a` |
| `weighted-individual-v1.json.gz` | `f064faeeb8f061a5131d34af068816d6dcf81f15203526ee4ccaa17541d15a07` |

Couple SS exact DTO/precision/order and restart/current/outdated/clone/retry tests also passed with the shared document extraction. No Couple SS DTO, wire field or dispatch was changed. This SS compatibility evidence uses the existing complete runtime fixtures; no new frozen SS artifact is claimed. Existing atomic repository, backups, verification and diagnostic hotfix tests pass. The repository/writer itself was not modified.

No `COUPLE_EXHAUSTIVE` payload is registered, no algorithm identifier changed, no old schema migrated, no file was written by this standalone codec, and no UI advertised newly restorable Exhaustive results.

## 9. Executed verification

All runs used IntelliJ bundled Maven, the required project-local `.codex-m2/repository`, and `-DargLine=-Xmx8g` for test runs. Logs are ignored under `target/`.

| Run | Actual result |
|---|---|
| Before edits: frozen weighted, existing snapshot codec and Couple SS persistence | **19 tests, 0 failures/errors/skips**, BUILD SUCCESS; `target/9d2-gate1-baseline.log`, 23.021 s |
| Initial new fidelity cases after fixture corrections plus frozen weighted | **27 tests, 0 failures/errors/skips**, BUILD SUCCESS; `target/9d2-gate1-third.log`, 14.537 s |
| Expanded codec, repository, weighted restoration/numeric/hotfix, Couple SS, RMD/Roth/single-person/state-unavailable regression | **108 tests, 0 failures/errors/skips**, BUILD SUCCESS; `target/9d2-gate1-regression.log`, 39.208 s |
| Final DTO naming and direct attribution regression run | **61 tests, 0 failures/errors/skips**, BUILD SUCCESS; `target/9d2-gate1-final.log`, 26.890 s |

The final 61 are: `RetainedProjectionCodecTest` (27), `IncomeSourceAttributionProjectionTest` (5), `TaxIncomeSourceAttributionTest` (10), `AnalysisSnapshotCodecTest` (7), `CoupleSocialSecurityPersistenceTest` (10), and `LegacyWeightedSnapshotCompatibilityTest` (2). The requested pattern `MichiganRetirementEligibilityTest` matched no standalone class; no execution of such a class is claimed. Michigan decisions are covered by the actual projection/attribution cases.

Counts overlap across invocations and are not a unique combined total. Initial scaffolding compilation errors and non-admitted PA/IA fixtures were corrected locally; no assertions, financial algorithms or goldens were weakened. All final matched tests passed.

The full Maven suite, complete Exhaustive capacity measurement and rendered/PDF acceptance were **not run for Gate 1**; those belong to the separately authorized later acceptance gates. There is no UI change to preview in this gate. No hour-long analysis was run.

## 10. Explicit limitations and remaining reconstruction responsibilities

1. **Source graph boundary:** `ProjectionYear` contains annual SS outcomes, not the full original monthly SSA calculation/election request, benefit valuation records, plan start date or spouse reporting age. When supplied, the evaluated complete claiming strategy is preserved by the outer overload. Later Gate 2 must capture the remaining frozen request/plan/report context while available; these facts cannot be inferred losslessly from annual totals. Likewise the per-year Roth requested amount is retained, while original scheduling/stop/strategy settings live in the run context.
2. **Account opening detail:** the graph contains the source account balance and per-year ending snapshots, not independent per-year per-account opening/growth objects. The codec preserves all existing fields and attribution, and does not invent missing account histories.
3. **Opaque orphan references:** capture refuses an attribution identity with no matching account anywhere in the retained annual snapshots. Such an opaque reference exposes no opening balance/history; fabricating a zero-balance account would be unsafe. Current `ProjectionEngine` output keeps its source accounts in the ending portfolio/snapshots and the supported fixtures pass. Manually built/custom projection graphs with orphan attribution need an explicit source-account context before expanding support. Invalid decoded references are rejected.
4. **Unsupported variants:** arbitrary Account subclasses, unknown future account/state/schema variants and non-admitted state results are refused. The eleven actual supported concrete accounts and all twelve current production states were tested under admitted conditions.
5. **Identity scope:** restored accounts are detached new objects. Identity links are exact within a restored projection, not Java object identity across application sessions or against a live plan. Later views must use restored account identities rather than matching names or passing live-plan accounts to `getEndingBalance`.
6. **No full payload capacity claim:** individual fixture fidelity does not prove a complete Exhaustive universe plus baseline and retained detailed projections fits the existing budget. Gate 5 must measure the assembled lossless payload without discarding data or increasing limits without approval.

No unresolved fidelity difference was found for the actual supported retained projections tested. The limitations above identify absent source context and deliberately refused non-engine/custom graphs, not silently omitted financial fields.

## 11. Final Git status and recommendation

`git diff --check` passes; index remains empty. Branch/HEAD remain as recorded. Final task inventory: two tracked files modified and four new untracked files (including this report). No pre-existing changes were present or discarded.

```text
 M src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java
 M src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java
?? PHASE-9D-2-EXHAUSTIVE-PROJECTION-CODEC.md
?? src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java
?? src/main/java/com/daviddunn/retirementplanner/persistence/RetainedProjectionCodec.java
?? src/test/java/com/daviddunn/retirementplanner/domain/projection/RetainedProjectionCodecTest.java
```

The only domain-file change is the immutable reference reconstruction factory and its private constructor delegation; no financial algorithm changed. No UI, controller, analyzer, rule/resource, existing report, golden, dependency or plan-schema changes occurred. Generated logs remain ignored in `target/`. Nothing was staged, committed, pushed, reset or discarded.

**Recommend reviewing/approving Gate 1, then separately authorizing Gate 2 only:** compose the full `COUPLE_EXHAUSTIVE` DTO from the original baseline and all compact entries, with this component for every originally retained projection, and freeze complete report/plan assumptions and historical SS cross-reference at the accepted result boundary. Do not connect callbacks/restoration or PDF export until their separate gates. Do not add Quick persistence or remaining individual persistence.
