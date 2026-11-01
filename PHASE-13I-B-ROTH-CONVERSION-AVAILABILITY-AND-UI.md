# Phase 13I-B — Roth Conversion Availability and UI Validation

**Implementation update:** the design below was reviewed and approved in the subsequent Phase 13I-B authorization. The completed implementation, actual verification and acceptance checklist are documented in section 12. Sections 1–11 retain the original gate findings for traceability; their “not implemented” status describes the earlier gate, not the current working tree.

## 1. Original architecture gate (subsequently approved)

**Original gate status (before subsequent approval): design only; no implementation at that time.**

The mandatory gate applies: durable employer availability and future-year execution require a **new optional persisted account property**, and the current strict retained-account format cannot retain it unchanged. Neither `RetirementParticipantFacts` nor `RothConversionRequest` represents distribution/rollover availability. Reusing retirement or separation facts would contradict the approved policy.

No production or test files were changed. Independent UI-only work is permitted by the assignment but is not required before this gate. This report recommends approving one coherent contract before delivering partial controls that cannot yet represent the requested availability states. Existing Phase 13I-A financial execution remains unchanged.

### Short design-gate proposal — not implemented

| Concern | Recommended contract requiring approval |
|---|---|
| Domain/schema | Optional immutable `EmployerRothConversionAvailability` on traditional 401(k)/403(b) accounts only; JSON property `rothConversionAvailability` |
| States | Missing/null = Unknown / Legacy; explicit `AVAILABLE`; explicit `NOT_YET_AVAILABLE`, with optional first eligible calendar year |
| Legacy default | Missing field remains missing on serialization; preserve current unknown-employer legacy execution without implying legal eligibility |
| New account default | Require an explicit Available or Not Yet Available selection; no silent Available default. Existing legacy accounts may retain Unknown when edited for unrelated reasons |
| Ownership | Uses existing account owner and household presence; no names, new account UUIDs, spouse transfers or destination creation |
| Effective year | Calendar-year boundary, January 1 inclusive. A future first eligible year gates conversion only; not RMDs or ordinary spending withdrawals |
| Financial identity | Include explicit availability/year in deterministic/integrated/Monte Carlo financial inputs; preserve absent legacy documents/digests. SS-only numerical fingerprints remain unaffected |
| Retained results | Retain new facts in a versioned retained-account representation with an explicit schema-1 decoder; do not append a required creator field to schema 1 or silently reinterpret old artifacts |
| Migration | No eager migration, account repair, snapshot rewrite, recalculation or new defaults during load. New explicit edits use ordinary dirty/revision/invalidation paths |
| Historical semantics | Restore saved annual results unchanged. Legacy inputs-only reconstruction runs the current engine with missing availability resolved to the existing legacy behavior, not reconstructed original implementation code |

## 2. Verified existing architecture

Reviewed the Phase 13I audit, Phase 13I-A implementation report and actual source. Relevant findings:

| Class/method | Verified behavior and constraint |
|---|---|
| `domain.roth.RothConversionEligibilityPolicy` | Separates source/destination type, same/present owner, projected balance and run-only availability. UNKNOWN execution is explicitly named a legacy assumption. No persisted availability or calendar-year evaluation |
| `domain.financial.Account` and traditional employer subclasses | Store name, owner, balance, opening RMD data and employer participant facts. No account UUID or distribution-availability field |
| `RetirementParticipantFacts` | Separation and retirement-condition facts with dates; not legal employer-plan distribution permission. Must remain independent |
| `RothConversionRequest` | Household enabled/start/amount/frequency/strategy/stop/target. No source-specific availability, selection or eligible year |
| `RothConversionView.updateStrategyFields`, `readValidated`, `refresh`, `applyChanges` | Enablement depends on checkbox/strategy; no account-availability assessment. Draft edits are preserved on refresh when dirty. Validation and Apply share a read path. Explicit disable currently returns null for an enabled saved request; an already-disabled saved request is preserved |
| `ResultsSummaryView.updateRothControls`, `applyRothConversion`, `loadRothConversion` | A second checkbox/strategy-based surface with independent parsing and immediate Apply callback. No source gate. Disabling sends null. Existing lifecycle is not a second copy of the tab's Apply/Cancel draft protocol |
| `MainWindow` Roth callbacks, `loadCurrentPlan`, `refreshAllViews` | Wires both surfaces to plan mutation/dirty/projection refresh; tab uses `refresh` to protect draft state. Availability refresh must not invoke these mutation callbacks merely to display guidance |
| `ApplicationController` accepted projection path | Freezes exact inputs, accepts matching results under revision/epoch/owner checks and validates retained charts before publication. A new availability edit must use these existing safeguards |
| `AccountDialog` result converter, `AccountsView.onAdd/onEdit/onRemove` | Shared editor creates a replacement Account object. New facts must be copied through editing and the wizard; a UI-only label would not survive replacement |
| `ProjectedPortfolioRothConverter` capacity and allocation | Uses shared policy but currently always calls `assessUnderLegacyEmployerAssumption`. No calendar-year argument. Existing owner-set overloads must remain compatible |
| `ProjectionEngine` annual calculation | Knows calendar year and existing eligible owners. Growth, RMD and spending precede capacity/allocation; actual conversion feeds taxes, funding and settlement |
| `RothConversionBracketFillCalculator.calculateConversion` | Trial attribution calls the converter, while the existing target solver computes hypothetical taxable income. Year-specific availability must reach trial allocation and final capacity consistently |
| `JsonRetirementPlanRepository.saveWithIdentity/load` | Atomic component/manifest save and identity publication. Availability must stay inside the detached financial graph; no separate sidecar or settings preference |
| `CompleteProjectionSnapshot.freeze/restoreInputs` | Exact configured-input document and SHA-256 digest. Optional new field changes identity when explicitly present; absent legacy bytes must remain exact |
| `RetainedProjectionSnapshot.AccountData.capture/restore` | Schema 1 captures type/name/owner/balance/opening RMD/participant/inherited data. Exact account class allowlist and indexed account identities. No availability slot |
| `AnalysisSnapshotCodec` | Strict unknown-field, missing-creator, duplicate/trailing/scalar/numeric and bounded document validation. Adding a missing required record component breaks old artifacts; global checks must not be weakened |
| `AnalysisFingerprintService.fingerprint` | Financial analyzer families include accountPortfolio; SS-only does not include that financial group. Existing deterministic expense-volatility exclusion must not be broadened to exclude conversion availability |
| `MonteCarloFingerprintService`, `MonteCarloSnapshot.Request` | Captures frozen plan A/B documents; financial canonicalization removes plan/storage/display metadata, not account financial facts. Both candidate documents must retain availability |
| `MonteCarloSnapshot` / `MonteCarloSnapshotMapping` / `MonteCarloPlanDocument` | Existing explicit versioned result/provenance and frozen-input reconstruction. New plan fields need compatible capture/restore, not new random streams or relabeling old outputs |

## 3. Proposed employer availability semantics

Use a small immutable value object associated with the account, not its display name or a household-wide switch. Suggested JSON, subject to approval:

```json
"rothConversionAvailability": { "status": "AVAILABLE" }
```

```json
"rothConversionAvailability": {
  "status": "NOT_YET_AVAILABLE",
  "firstEligibleYear": 2032
}
```

`NOT_YET_AVAILABLE` without a year means explicitly unavailable throughout the modeled horizon until the user changes the assumption. A year is an inclusive first eligible calendar year. `AVAILABLE` means available throughout the modeled years of that configured plan; it must not consult today's clock or silently use the year the file was opened. If eligibility begins later, use the future-year state instead.

Recommended validation: status required when object is present; `AVAILABLE` rejects an eligible-year field; `NOT_YET_AVAILABLE` accepts no year or a valid bounded calendar year under existing projection/date constraints. Do not invent a new arbitrary maximum year. Reject the property on IRA, Roth, inherited, taxable and cash types. Missing/null means legacy and is omitted; do not add a default UNKNOWN object when loading old JSON. Explicit malformed objects must not silently become legacy.

An optional explicit UI “Unknown / Legacy Assumption” value can canonicalize to the absent representation for legacy records. Whether newly added employer accounts may deliberately select legacy mode is an approval decision below; recommendation: require an explicit Available/Not Yet Available choice for new accounts, while allowing unrelated edits to old legacy accounts without forced migration.

Available is a **user assertion for planning**, not legal verification. Existing participant facts, age, separation, retirement, RMD age and balance cannot upgrade unknown/unavailable. No new plan-specific withdrawal rules, hardship, basis, designated-Roth rollover, in-plan conversion, plan-loan or beneficiary models are proposed.

## 4. Shared availability and configuration contract

Extend the existing pure policy, retaining separate dimensions. Proposed aggregate `RothConversionAvailabilityAssessment` contains immutable per-owner/per-occurrence findings, using existing portfolio occurrence ordering rather than account names or new IDs:

- Supported source type and whether its owner is present.
- Opening balance separately from year-specific post-RMD/post-spending projected capacity.
- Legacy unknown, explicitly available, unavailable, future first eligible year and depletion.
- Same-owner ordinary RothIRA destination readiness, independently of source readiness.
- Saved schedule enabled/disabled, whether new enablement is allowed, and specific warnings for presently unexecutable schedules.

Source and destination classification must delegate to `RothConversionEligibilityPolicy`; the UI must not introduce another type list. Year-based availability evaluation is also centralized. Messages may be formatted by a small shared UI adapter, but views must not calculate financial capacity from editable balance controls or claim an opening balance is the annual execution capacity.

| Condition | Proposed UI/configuration behavior |
|---|---|
| No supported source belonging to a present owner | Cannot newly enable; explain supported pretax types. Preserve saved schedule as read-only; permit explicit disable |
| Only an absent spouse has a source | Explain owner issue, do not treat it as primary capacity or create accounts |
| Supported IRA with zero opening balance | Distinct zero-balance message; configuration/future schedule not blocked solely by zero balance |
| Source has funds but no same-owner RothIRA | Configuration remains possible with a clear nonexecution warning; no account creation |
| Employer legacy/unknown | Disclose legacy assumption and missing legal confirmation; preserve existing execution |
| Employer explicit Available | Configuration permitted subject to owner/destination/capacity; assertion is not legal verification |
| Employer future year | Permit future schedule, show first eligible year; execute zero from this source before that year |
| Employer Not Yet Available without year | No supported current/future execution from this source. Preserve existing instructions; require an availability change or another usable source to newly enable |
| Mixed unavailable and usable sources | Do not block household; available same-owner sources remain usable in established allocation order |
| Source depleted later | Existing shortfall behavior; no account creation or silent schedule deletion |

Recommended shared validation distinguishes **new enablement**, **explicit schedule editing**, **unchanged saved enabled schedule**, and **explicit disable**. Loading or saving unchanged old instructions must not fail just because they are currently unexecutable. Programmatic Apply must use the same validator as the controls, so disabling a checkbox is not the only guard.

### Saved schedules and draft handling

Both surfaces currently remove an enabled request when explicitly disabled. To retain fields after disabling, recommend constructing the same immutable request with `enabled=false` and otherwise unchanged values; null remains a supported legacy/no-schedule representation. This is a proposed intentional UI/save behavior adjustment, not a change already implemented. It avoids losing saved fields while preserving the user's explicit off action. No load-time normalization is needed.

The tab keeps its existing Apply/Cancel draft lifecycle. Summary keeps its existing Apply lifecycle; do not add a second editing framework or an unrequested Cancel workflow. Both use the same availability assessment and transition validator. Rechecking availability after account changes must preserve pending field text, not call `load` indiscriminately. Account removal must revalidate a draft before Apply. A newly enabled draft that loses its last source is refused on Apply without writes; an unchanged saved schedule remains saveable.

Suggested common guidance:

- “No eligible pretax retirement account is configured. Add a Traditional IRA, Rollover IRA, or supported traditional employer retirement account to enable Roth conversions.”
- “No Roth IRA destination is configured for this account owner. Add a Roth IRA to execute conversions.”
- “Employer distribution availability is unknown. This plan uses the legacy modeling assumption; confirm availability under the employer plan's rules.”
- “This account is modeled as available beginning in 2032. Earlier scheduled conversions cannot use its funds.”

Guidance is read-only, wrapped, accessible and theme-aware. It must not run a projection, mark dirty, overwrite a schedule or change Current/Baseline selection just by opening a screen.

## 5. Financial integration proposal after approval

Add a calendar-year-aware converter evaluation path that resolves each source's account availability through the shared policy. Keep legacy overloads for existing callers; new explicit availability must never silently fall through to an overload that ignores its year. Callers without a year cannot safely evaluate a future-year record: recommend explicit rejection of such records in legacy overloads, while all old absent-property cases retain behavior. This API contract needs tests and approval with the schema.

Thread the annual year and existing owner context through:

1. `getMaximumConvertibleAmount`.
2. `convertHousehold` and the direct API where used.
3. Bracket-fill trial attribution.
4. ProjectionEngine final capacity/allocation.

An unavailable account contributes zero even with a positive balance. Other eligible sources continue in PRIMARY-first, owner/source-list order. First ordinary same-owner RothIRA destination and unchanged shortfall conventions remain authoritative. No new destination, balance, transfer rule, date-of-death rule or source account is introduced.

Keep tax-funding iterations, actual execution taxation, RMD-before-conversion and spending-before-capacity unchanged. The existing target solver can propose more than executable capacity. Its current hypothetical-income trials and state attribution may need careful testing when every employer source is gated off: do not silently redesign convergence or tax methodology. If consistent year-limited attribution requires changes beyond availability plumbing, stop at a financial decision gate with a failing case before making them.

Monte Carlo and weighted/integrated candidates must freeze the same new account values before execution. Resolve availability deterministically by the simulated **calendar year**, never scenario index, wall clock, RNG or mutable UI state. Market/inflation/mortality/expense streams remain unchanged. Availability restricts conversions only, not RMD or ordinary withdrawal distribution rules, which are separate existing models.

## 6. Persistence, snapshots and fingerprint decision

### Plan JSON

An optional account property permits backward-compatible plan loading without eager migration. Omit absent legacy state byte-for-byte, preserve owner/account order and exact numeric values. Editing a legacy account for its name/balance must preserve absence. Changing subtype must explicitly validate/reject or clear incompatible availability through visible editor behavior; never carry employer facts into a RothIRA invisibly.

AccountDialog creates replacement objects; both standard and guided account editing need the new value copied and validated. An explicit availability edit is a financial input edit and uses normal dirty/revision/cache invalidation. Save, Save As and detached workers must copy it with the existing graph, not mutate the live plan on a worker. Preserve atomic component validation/digest/manifest publication and independent Save As ownership.

### Retained financial account graph

Current retained schema 1 lacks availability. Recommend a **versioned retained representation** (new retained schema version 2, subject to approval), with an exact schema-1 compatibility reader. Schema-1 accounts resolve to absent legacy availability without rewriting original stored bytes or identifiers. New captured facts must survive account registry restoration and source/destination references. Continue rejecting malformed/unsupported properties, owner/type combinations, corrupt digests and unsupported versions.

Do not globally disable `FAIL_ON_MISSING_CREATOR_PROPERTIES` or relax unknown-field checks to accept missing fields. A scoped version-aware DTO/decoder can read old shapes and produce a current runtime graph; old digest verification must use original bytes, not reserialized new records. Preserve schema-1 recapture/fidelity comparison using a version-aware comparison. Existing strict `restoreProjection` compares captured annual records; simply bumping a constant or adding one nullable record field is insufficient.

`CompleteProjectionSnapshot.VERSION` and analyzer outer artifact versions should not be bumped mechanically. Determine which envelopes can legitimately carry the new nested retained version and which require a new supported payload contract/decoder. No old artifact meaning may be redefined. The narrow implementation must inventory these nested uses before choosing exact outer version numbers; that decision is part of this approval, not permission for a broad history migration.

### Financial identity

- Complete configured-input documents: new explicit account data changes the digest; missing legacy data remains exact. Keep exact document matching for role swaps and all execution revision/epoch guards.
- Deterministic/integrated/weighted families: include availability and first eligible year. Do not strip these fields under the unrelated Phase 14 expense-volatility exemption.
- SS-only financial/request assessment: account availability does not change the SS calculation; preserve that family-specific contract. A new projection UUID still does not invent an association to an old result.
- Monte Carlo: both frozen candidate documents include account availability. Existing EXACT_V1 and temporary numeric compatibility assessment must retain the field rather than classifying it as metadata. No stored fingerprint is rewritten. Paired candidates evaluate their own source availability against shared calendar years.
- Historical PDFs and result views: use frozen original account assumptions, label legacy unknown honestly, and never substitute the current account editor. Original accepted annual values stay unchanged.

Legacy inputs-only reconstruction uses the current engine implementation; it is not original saved result restoration. Missing availability preserves Phase 13I-A legacy behavior. New explicit availability legitimately alters a new projection. Earlier source/destination corrections can still make an old inputs-only rebuild differ from a pre-correction run; do not claim reconstructed values are original historical financial results.

## 7. Proposed implementation boundaries / files (no edits yet)

1. Domain and account JSON: new immutable availability value under `domain.roth`; `Account`, traditional employer accounts and optional-field serialization. Keep unrelated types free of the property.
2. Shared policy: extend `RothConversionEligibilityPolicy` with year resolution and aggregate configuration assessment; introduce a small shared transition validator rather than another source list.
3. Snapshot compatibility: `RetainedProjectionSnapshot`, `RetainedProjectionCodec`, `AnalysisSnapshotCodec` and nested consumers/version adapters; `CompleteProjectionSnapshot` only where the approved compatibility contract requires it.
4. Editor/validation: `AccountDialog`/shared wizard path, `RothConversionView`, `ResultsSummaryView`, and narrow `MainWindow` refresh wiring as needed to preserve drafts. AccountsView already uses replacement and standard plan-changed callbacks.
5. Engine plumbing: `ProjectedPortfolioRothConverter`, `ProjectionEngine`, `RothConversionBracketFillCalculator`; preserve old execution for absent-property accounts.
6. Identity/MC/history checks: `AnalysisFingerprintService`, `MonteCarloFingerprintService`, `MonteCarloPlanDocument`, `MonteCarloSnapshotMapping`, and relevant payload validators only as proven necessary. Do not edit unrelated expense-volatility contracts.
7. Tests: extend existing Roth policy/execution/financial, UI, account editor/wizard, persistence/snapshot, fingerprint, state settlement, swap, Monte Carlo and original-context PDF tests. No golden regeneration as a compatibility shortcut.

## 8. Regression plan after approval

Add tests for all requested conditions: unsupported/zero/funded IRA sources, rollover, absent owner, spouse-only and mixed sources; legacy/explicit available/unavailable/future employer states; missing same-owner destination; saved unexecutable schedule preservation/disable; account removal while a draft is open; both UI surfaces; Apply/Cancel without mutation; add/edit/wizard replacement; no default migration; exact legacy JSON/digests; Save/restart/Save As; new retained version and strict old decoder; corrupt/unsupported data; calendar boundary and differing horizons; depletion/shortfalls; RMD/spending ordering; bracket-fill trial attribution and actual taxation; tax funding/IRMAA/state unavailable states; Monte Carlo deterministic availability/no new draws; historical results/PDFs/associations; stale completions and exact swaps.

Independent invariants: unavailable source has zero conversion capacity; available source is not blocked by another unavailable account; source and same-owner destination debit/credit equal actual conversion; zero balance is not unsupported type; no unsupported/different-owner destination receives credit; original annual results and configured documents remain immutable; explicit eligibility year changes financial identity without changing SS-only numerical identity.

UI previews after implementation: Light/Dark, normal/reduced sizes, single/couple, no source with saved schedule, future employer source, zero source, and missing destination. Verify wrapped guidance, keyboard/tooltips, draft preservation and no automatic financial execution on opening.

## 9. Actual verification at this gate

Used IntelliJ bundled Maven, required project-local `.codex-m2/repository` and `-DargLine=-Xmx4g`.

- Existing `*Roth*Test,ProjectionSnapshotPersistenceTest` selection: **178 tests, 0 failures, 0 errors, 0 skips**, BUILD SUCCESS (17.009 seconds). An additional requested name `MonteCarloFingerprintServiceTest` did not exist and was not run under the no-match-tolerant selector; the actual Monte Carlo tests are run separately below.
- Existing `MonteCarloRestoredFingerprintInvestigationTest,MonteCarloSnapshotCodecTest`: **21 tests, 19 passed, 0 failures, 0 errors, 2 skipped**, BUILD SUCCESS (14.704 seconds). No existing assertions or skip conditions were changed. Logs: `target/roth-availability-gate/focused.log` and `monte-carlo.log`.
- Full regression suite is **not rerun for this architecture-only gate**; no production/test code changed. The Phase 13I-A full suite already passed and received native acceptance, but that is prior evidence, not a new run for this phase.
- No new visual previews: UI changes were not implemented. No claim of Phase 13I-B native acceptance or feature completion.

## 10. Git preservation and exact changed files

Branch `main`, HEAD `dd77e8388f00628cb8a0b2d9242563bfb77d5a4e`; index empty at entry. The complete status and SHA-256 inventory of all pre-existing modified/untracked files is saved under ignored `target/roth-availability-gate/baseline-status.txt` and `baseline-hashes.json`. This includes Phase 13I-A, Phase 14B3A and startup/UI/IDE work.

Only new deliverable: **`PHASE-13I-B-ROTH-CONVERSION-AVAILABILITY-AND-UI.md`**. No production files, tests, plans, settings, historical artifacts, schemas or golden resources changed. Generated Maven logs remain ignored. Final SHA-256 comparison found **zero changed pre-existing files**; `git diff --check` passed (exit 0); staging index remains empty. No staging, commit, push, reset, clean or discard commands were run.

## 11. Approval decisions and Phase 13I-C readiness

Approval is requested for:

1. Optional persisted employer-account availability value and the proposed omitted-legacy representation, including Available vs Not Yet Available with optional inclusive year. Reject explicit malformed data rather than treating it as legacy.
2. New employer-account UI requiring a deliberate choice; retain Unknown for old accounts without forced migration. Decide whether new accounts may explicitly opt into legacy unknown or only Available/Not Yet Available.
3. Versioned retained-account compatibility work with exact schema-1 preservation; authorize only the minimally necessary nested/outer version changes discovered through compatibility tests, not a broad artifact migration.
4. Calendar-year-aware capacity/allocation/trial APIs, including explicit refusal of new future-dated records in no-year overloads instead of silently ignoring their year.
5. Saved-schedule handling: preserve an unchanged unexecutable request; refuse new enablement with no supported present-owner source; retain fields using `enabled=false` when explicitly disabled; permit zero-balance/future schedules; warn rather than create missing destinations.

Phase 13I-C is not ready to begin: automatic destination creation remains unimplemented and separately gated. The approved Phase 13I-A exact ordinary-RothIRA policy is a useful foundation; this availability/persistence contract must be reviewed before adding automatic modeled destinations.

**The original architecture gate ended here. Subsequent implementation was explicitly approved and is recorded below.**

## 12. Approved implementation and verification

This section supersedes the original gate's implementation status. The user approved the optional account value, new-account explicit choice, nested retained versioning, year-aware execution and shared UI transitions. No automatic destination creation or Phase 13I-C functionality is included.

### Implemented domain, defaults and ownership

Added immutable `EmployerRothConversionAvailability` with AVAILABLE and NOT_YET_AVAILABLE and optional `firstEligibleYear`. The property name is `rothConversionAvailability`. Missing/null remains legacy unknown and is omitted during serialization. Available rejects a year; malformed states, nonintegral/string years, unknown fields and out-of-calendar-range years are rejected. Non-null values are rejected on all types other than traditional 401(k)/403(b). No names, UUIDs, account balance, age, separation or retirement facts determine availability.

AVAILABLE covers all modeled years. NOT_YET_AVAILABLE with a year becomes available January 1 of that year inclusive; without a year it remains unavailable. Existing employer accounts without the property continue Phase 13I-A execution under the named legacy assumption, retaining UNKNOWN in assessments. The new value is a planning assertion, not legal verification.

The ordinary and guided account editors share `EmployerRothAvailabilityEditor`. New employer records have an unselected required choice; Unknown is not offered. Legacy records show Unknown/Legacy and a deliberate “Set rollover/conversion availability explicitly” action. Unrelated edits preserve absence. Existing explicit values are loaded, validated and copied into replacement account objects. Hidden incompatible account-type fields are not carried into a different account type. No automatic source/destination or user account UUID is created.

### Shared availability, transitions and both UI surfaces

`RothConversionConfiguration` is a pure shared assessment/validator that delegates all supported source/destination classification and annual availability to `RothConversionEligibilityPolicy`. It retains separate per-source owner presence, opening balance, annual availability, future year and destination-readiness facts. Opening balances are never represented as authoritative post-RMD/post-spending annual capacity.

Both RothConversionView and ResultsSummaryView show the same guidance. New enablement requires a supported present-owner source available now, legacy-unknown, or with an established future year. Zero opening balance does not prevent future configuration. Missing destinations warn without creating an account. Explicitly unavailable sources without a future year do not authorize new enablement, but another usable household source still does.

Saved enabled schedules remain visible and can be saved unchanged even when currently unexecutable; editors become read-only while explicit disabling remains possible. Explicit disable preserves the original schedule fields with `enabled=false`; legacy null remains valid. Validation rechecks at Apply, so stale/programmatic edits cannot bypass the checkbox. Re-enabling a disabled schedule with no supported source is refused. Displaying guidance makes no plan writes or projection calls.

The tab retains Apply/Cancel and typed dirty comparison. On account refresh it preserves pending input text while reassessing availability. Results Summary retains its Apply-only lifecycle; a narrow UI baseline retains pending Roth edits during same-plan/account refreshes until Apply changes the saved request or plan ownership changes. It does not add a competing draft/persistence framework. Successful Apply refreshes both surfaces through existing callbacks.

### Financial execution and year-aware contracts

Added explicit calendar-year overloads for direct conversion, maximum capacity and household allocation. Existing overloads preserve old accounts and support explicit all-year AVAILABLE or indefinitely unavailable states. A no-year API refuses to evaluate a future-dated source when encountered; it never silently treats that source as eligible.

ProjectionEngine passes the annual calendar year to capacity and actual execution. Bracket-fill trial attribution passes the same projection year through the existing converter. Source ordering, PRIMARY-first allocation, same-owner ordinary RothIRA destinations, RMD-before-conversion and ordinary-spending-before-capacity are unchanged. No sampling, tax formula, target-solver iteration, IRMAA/state rule, rounding or new balance was introduced.

Unavailable sources contribute zero conversion capacity; other sources remain usable. Actual executed conversion remains the taxable amount in final settlement. Existing RMD and ordinary withdrawal behavior is independent: this new assumption gates **Roth conversions only**, not every employer-plan distribution. Beneficiary/death-year conversion policies remain unchanged.

### Persistence and nested schema decision

Ordinary plan persistence uses the optional account getter/setter; no general mapper settings changed. Exact legacy configured documents omit the property and retain their digests. Save/Save As keep explicit values in detached input graphs and independent plan ownership. No load-time migration, default insertion or historical recalculation occurs.

RetainedProjectionSnapshot captures schema **1** when no account has explicit availability and schema **2** when an explicit value must be retained. AccountData has a scoped optional field and a token-preserving reader. The reader still requires all original seven account fields, rejects duplicates/unknown fields and retains exact BigDecimal token precision/scale. Schema 1 rejects explicit availability; schema 2 requires it. Account ordering and indexed source/destination identity links are preserved. Original schema-1 documents and stored digests are never rewritten.

Initial compatibility testing caught nested trailing-token handling and tree-induced decimal-scale changes. The implementation was corrected to read original account tokens directly; global strict creator/unknown/trailing/scalar validation was **not** relaxed. The bounded numeric-path registry includes the new value object's calendar-year field.

The existing CompleteProjectionSnapshot and analyzer envelopes can safely carry the versioned nested record; no unrelated outer schema or calculation version was bumped. New normal-projection provenance records the actual retained schema, preserving the original text for schema 1. Complete component save/load and exact schema-2 restoration were tested through the production component loader. Prior accepted annual values/UUIDs/associations remain unchanged. Older binaries that do not support the new field/schema are not promised forward compatibility.

### Fingerprints, Monte Carlo and history

The explicit account field naturally participates in complete configured-input digests and existing deterministic/integrated/Monte Carlo financial fingerprints. No new exclusion or stored-fingerprint rewrite was necessary. SS-only numerical fingerprints remain unaffected. Exact role-swap configured-document matching and generation/revision/admission safeguards are unchanged.

Monte Carlo plan/candidate copies retain the optional account value through existing serialization. Availability is evaluated by absolute simulation calendar year, not scenario index or new random draws. Tests verify fixed-horizon single/couple repeatability and paired frozen candidates; changing the live account after candidate capture does not change those candidates' modeled results. Existing longevity/random-stream/golden coverage remains in the full suite. No stochastic algorithms, return identities or expense-volatility execution gates were altered.

Historical restoration reads original saved assumptions and annual results, without engine execution. Legacy input-only baseline rebuilds still use the current engine: absence preserves legacy availability, but a rebuild is not execution of the original historical binary. Original saved results remain the authoritative historical evidence.

### Exact implementation file inventory

New production files:

1. `src/main/java/com/daviddunn/retirementplanner/domain/roth/EmployerRothConversionAvailability.java`
2. `src/main/java/com/daviddunn/retirementplanner/domain/roth/RothConversionConfiguration.java`
3. `src/main/java/com/daviddunn/retirementplanner/ui/controls/EmployerRothAvailabilityEditor.java`

Modified production files:

4. `src/main/java/com/daviddunn/retirementplanner/domain/financial/Account.java`
5. `src/main/java/com/daviddunn/retirementplanner/domain/roth/RothConversionEligibilityPolicy.java`
6. `src/main/java/com/daviddunn/retirementplanner/domain/roth/ProjectedPortfolioRothConverter.java`
7. `src/main/java/com/daviddunn/retirementplanner/domain/roth/RothConversionBracketFillCalculator.java`
8. `src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java`
9. `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java`
10. `src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java`
11. `src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java` — actual retained-version provenance only; accepted-result guards unchanged.
12. `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/AccountDialog.java`
13. `src/main/java/com/daviddunn/retirementplanner/ui/views/RothConversionView.java`
14. `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java`

New tests:

15. `src/test/java/com/daviddunn/retirementplanner/domain/roth/EmployerRothAvailabilityTest.java`
16. `src/test/java/com/daviddunn/retirementplanner/ui/views/RothAvailabilityUiTest.java`

Adjusted existing tests:

17. `src/test/java/com/daviddunn/retirementplanner/ui/views/RothConversionViewTest.java` — supported-source fixture and approved disabled-request retention assertion.
18. `src/test/java/com/daviddunn/retirementplanner/ui/views/RothConversionMainWindowTest.java` — zero-balance supported-source fixture; existing lifecycle assertions preserved.
19. `src/test/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryRothValidationTest.java` — verifies preservation before parsing disabled inputs instead of obsolete null-removal spelling.
20. `src/test/java/com/daviddunn/retirementplanner/ui/wizard/AccountsWizardStepTest.java` — explicit new employer availability choice; existing account/type/ownership assertions preserved.

21. This report, updated after architecture approval.

No financial golden, dependency, stylesheet, PDF exporter, Monte Carlo algorithm or application preference file was intentionally changed. Existing overlapping Phase 13I-A/14B3A edits were preserved; shared files received only the in-scope additions above.

### Actual focused tests and visual inspection

All commands use IntelliJ bundled Maven, the required project-local Maven repository and `-DargLine=-Xmx4g`.

- Main focused selection (`*Roth*Test`, retained/complete projection codecs/persistence, account wizard, Monte Carlo snapshot and restored-fingerprint tests): **275 tests, 0 failures, 0 errors, 2 existing property-gated skips**, BUILD SUCCESS (24.123 seconds). Log: `target/roth-availability/focused-verified.log`.
- Final domain/UI refresh and seeded standalone tests: **35 tests, 0 failures/errors/skips**, BUILD SUCCESS (16.528 seconds). Log: `target/roth-availability/last-focused.log`.
- Final paired-candidate and domain check: **28 tests, 0 failures/errors/skips**, BUILD SUCCESS (15.298 seconds). Log: `target/roth-availability/paired-final.log`.
- These runs overlap and must not be summed as unique test totals. Earlier failures during development are retained in target logs; no assertions/goldens were weakened. Existing null-removal and employer-editor fixture expectations changed only to reflect the explicitly authorized behavior.

Generated and inspected twelve actual JavaFX previews under `target/roth-availability/previews/`: Light/Dark at 1180x820 and 900x650, each with Roth tab, actual Summary card, and account editor. Standard editors are shown in the larger preview; guided editors in the smaller preview. Guidance wraps within the Summary card, the year has a persistent visible label, and buttons/text remain readable without clipping. Existing theme manager and semantic styling are reused; no global CSS or PDF palette changed. These automated scene renders do not replace manual Windows DPI/keyboard/focus acceptance.

Final complete regression: **3,892 tests, 3,846 passed, 0 failures, 0 errors, 46 skipped**, BUILD SUCCESS, exit 0 (11:30 minutes). Log: `target/roth-availability/full-final.log`. Independently summed the 440 Surefire XML reports written during the final run; totals agree with Maven. Older diagnostic XML was excluded by run-start timestamp. Existing optional/disabled skip conditions remain unchanged.

Final `git diff --check` passed (exit 0), with only existing LF/CRLF conversion notices. Index remains empty; branch and HEAD unchanged. SHA-256 comparison against the implementation baseline confirms only these six already-modified/untracked files were intentionally extended: Account.java, RothConversionEligibilityPolicy.java, ProjectedPortfolioRothConverter.java, ApplicationController.java, ResultsSummaryView.java, and this report. Every other pre-existing worktree file is unchanged. Original overlapping Phase 13I-A/14B3A edits in the six shared files remain preserved.

The first full run completed **3,892 tests, 0 failures, 1 error, 46 skipped** (14:54 minutes). Existing `InputTooltipTest` caught a missing tooltip on the new legacy-replacement checkbox. Added the tooltip/accessibility help without changing the test. The correction selection (`InputTooltipTest,RothAvailabilityUiTest,EmployerRothAvailabilityTest`) then passed **47 tests, 0 failures/errors/skips**, BUILD SUCCESS (23.507 seconds). Log: `target/roth-availability/final-correction.log`. A complete rerun is required and its final result is recorded below; the first run is not presented as success.

### Remaining limitations and native acceptance

No new legal qualification, hardship, in-plan Roth conversion, designated-Roth rollover, basis, loan offset or beneficiary model exists. Existing broad withdrawal availability is not changed by the conversion-only assumption. The calendar-year model is annual and does not represent a midyear employer distribution date.

Native acceptance checklist:

1. New 401(k)/403(b) in ordinary and guided editors: OK cannot accept an unselected choice; Available and Not Yet Available, including blank/invalid/future year, behave as labeled. Confirm Cancel makes no plan edits.
2. Existing legacy employer account: open/rename/save without adding availability; verify legacy disclosure and prior financial behavior. Explicitly replace legacy with either supported state and confirm dirty/freshness behavior.
3. Both Roth configuration surfaces: no source, zero-balance IRA, rollover, spouse-only source, absent-owner problem, no same-owner RothIRA and mixed available/unavailable sources. Confirm same guidance and correct new-enablement restrictions.
4. Saved unexecutable schedule remains visible/saveable; explicit disable retains fields with enabled=false. Null legacy/no-schedule plans still load normally.
5. Add/remove/edit accounts with pending Roth edits; tab Cancel restores the saved schedule and Summary Apply retains its established lifecycle. No projection starts merely by opening guidance.
6. Projection before/at/after the first eligible year: unavailable source stays unused for conversion; other valid sources continue in original order. Check fixed/custom/bracket strategies, RMD-year ordering, taxes/IRMAA/state boundaries and shortfalls.
7. Save/restart/Save As/swap: exact explicit assumptions and original schema-2 annual data restore; old schema-1 history and associations remain exact and read-only. No automatic accounts appear.
8. Light/Dark at both supported sizes and Windows scaling: labels, wrapped warnings, tooltips, Tab/Shift+Tab, legacy checkbox and validation focus.

No Phase 13I-C work has begun. Automatic modeled Roth IRA creation remains separately gated after review and native acceptance of this phase.

**Release recommendation: ready for review and native Windows acceptance of Phase 13I-B. No unresolved implementation approval gate remains in this phase.** Native DPI, physical keyboard/focus and real-plan acceptance are still pending; automated previews and tests are not presented as native acceptance.
