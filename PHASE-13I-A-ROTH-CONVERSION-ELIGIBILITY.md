# Phase 13I-A — Roth Conversion Eligibility and Financial Correctness

## Summary and scope

Implemented the approved narrow source/destination corrections and a pure shared domain policy. Potential fully pretax sources are Traditional IRA, Rollover IRA, traditional 401(k) and traditional 403(b). Only an existing ordinary Roth IRA owned by the same present household owner can receive the conversion. Designated Roth, inherited, taxable and cash accounts are not taxable-conversion sources. Employer and inherited Roth accounts are not destinations.

No accounts are created. No account fields, employer-availability assumptions, schemas, fingerprints, historical payloads, calculation versions or UI controls were added or changed. Existing instructions are not repaired or deleted during loading or projection. No Phase 13I-B work is included.

Unknown employer distribution availability remains **UNKNOWN**, with existing execution permitted only under an explicitly named legacy modeling assumption. This does not establish legal availability. No age, employment separation, retirement status, participant facts or positive balance is interpreted as legal permission.

## Baseline and preservation

Branch `main`; HEAD `dd77e8388f00628cb8a0b2d9242563bfb77d5a4e`; staging index empty before work. Working-tree baseline and SHA-256 hashes were recorded before implementation in ignored `target/roth-eligibility/baseline-status.txt` and `preexisting-hashes.json`.

Existing tracked edits preserved: `.idea/encodings.xml`; Monte Carlo analyzer/comparison; CompleteProjectionSnapshot; AnalysisFingerprintService; Expense; MainApplication; ApplicationController; ExpenseDialog; Monte Carlo run services; ResultsSummaryView; ProjectionSwapTest; Phase12FAnalyzerSwapTest; StartupSplashViewTest. Existing untracked work preserved: Phase 13I architecture audit, Phase 14B3A report, ExpenseVolatilityAdmission, DeterministicExpenseInputs, ExpenseDeserializer, and the three corresponding volatility tests. These are excluded from this phase's change inventory.

The pre-existing modified/untracked files were hashed before editing and checked afterward; none was edited by this phase. Shared projection, fingerprint, controller, Monte Carlo and UI classes remain untouched.

## Employer-availability compatibility analysis (before execution changes)

| Treatment of unknown availability | Compatibility consequences | Legal meaning |
|---|---|---|
| Treat as unavailable | Stops previously modeled 401(k)/403(b) conversions; changes executed amounts, taxes, balances, future RMDs, estate and analysis rankings | Conservative refusal, but unsupported as a silent behavior change in this phase |
| Preserve legacy execution while retaining UNKNOWN | Preserves supported employer-source behavior when paired with a valid ordinary Roth IRA; overbroad destinations still intentionally rejected | A legacy modeling permission, **not established legal distribution eligibility** |

The authorized second option is implemented. `assessUnderLegacyEmployerAssumption` returns `EmployerDistributionAvailability.UNKNOWN` for supported employer sources, including when participant facts state separation/retirement conditions met. `usesLegacyEmployerAssumption()` explicitly identifies an otherwise executable transaction relying on that permission. Unsupported destination/type/owner or no balance still blocks execution.

The pure policy can separately assess caller-supplied ESTABLISHED, UNAVAILABLE and UNKNOWN states; these are run-only assessment inputs, not new persisted facts or an application workflow. An explicit UNAVAILABLE assessment never supplies capacity. The existing production converter always uses the legacy-UNKNOWN entry point for employer sources, because current plan facts cannot establish availability.

**Phase 13I-B decision:** define whether and how real employer-plan distribution/rollover availability should be established, including unknown/future availability and validation. No new field, inference or blanket employer-conversion block is introduced here.

## Shared domain policy

`domain.roth.RothConversionEligibilityPolicy` has no plan mutation, JavaFX, I/O, random state or persistence behavior. Its immutable `Assessment` contains:

- Supported pretax source type and supported ordinary-RothIRA destination type.
- Same-owner relationship and whether the source owner is in the caller's present/eligible-owner set; JOINT is not a retirement conversion owner.
- Available projected balance, separate from source-type support.
- Explicit employer distribution availability, with NOT_APPLICABLE for non-employer sources.
- Immutable reasons distinguishing unsupported source, missing destination, unsupported destination, different/absent owner, same-account transaction, no available balance, unavailable/unknown employer distribution, and unsupported transaction.
- `executableUnderLegacyEmployerAssumption`, `usesLegacyEmployerAssumption`, and `maximumConvertibleAmount`.

Zero/negative projected availability supplies zero capacity without changing source-type classification. No destination and an unsupported destination are separate reasons. Source identity equal to destination is explicitly refused. The assessment returns values/reasons, not mutable account references.

`Account.isEligibleForRothConversion()` now delegates its **potential-source-type** question to the shared policy. Redundant subclass true overrides were removed from TraditionalIRA, Traditional401K, Traditional403B and Roth401K. RolloverIRA inherits the corrected shared answer. Existing callers such as AccountPortfolio's eligible balance helper and EligibleTraditionalBalanceCalculator therefore receive the corrected types without adding independent lists. Their aggregate potential-source balances are not assertions of legal availability or executable same-owner destination capacity.

The JsonIgnore annotation remains on the base predicate. No derived eligibility/availability field is serialized.

## Execution integration and preserved ordering

`ProjectedPortfolioRothConverter` uses the shared policy for household projected capacity and actual allocation. Destination lookup uses exact ordinary `AccountType.ROTH_IRA`, never broad `ProjectionAssetType.ROTH`. Both direct and household APIs use the same supported source/destination classification and transaction assessment.

Preserved behavior:

1. Household allocation remains PRIMARY first, then SPOUSE, filtered by the existing caller-provided eligible-owner set.
2. Source iteration order and first eligible same-owner destination order remain portfolio order. Unsupported Roth destinations are skipped, so a later valid Roth IRA remains usable.
3. Projected capacity and actual allocation require the same source/destination/owner rules. A positive balance or broad Roth category cannot supply capacity by itself.
4. Direct single-owner conversion still requires one sufficient source and retains existing missing-source/destination error contracts. Household conversion still skips owners without destinations, converts available funds and lets the engine report the existing shortfall.
5. Transfers debit the source and credit the same-owner destination by the same gross amount. Persisted balances and account collections remain unchanged.
6. Existing zero-target behavior remains unchanged.

No ProjectionEngine ordering or arithmetic changed: RMDs and ordinary spending withdrawals occur before conversion capacity; scheduled/requested amounts, requested-minus-executed shortfalls, actual-conversion taxation, tax-funding feedback, Medicare retries and rounding remain on existing code paths.

Bracket-fill trial **allocation attribution** already calls `ProjectedPortfolioRothConverter.convertHousehold`; it now receives exactly the same corrected policy without a duplicate list or an alternate trial converter. The existing hypothetical taxable-income target solver and its feedback iterations were not redesigned. Its final execution is still limited to actual supported capacity by ProjectionEngine. No aggregate legacy helper was reintroduced into the production path.

## Intentional differences and compatibility

| Configuration | Before | Now |
|---|---|---|
| Valid TraditionalIRA -> same-owner RothIRA | Supported | Same financial results |
| RolloverIRA -> same-owner RothIRA | Excluded by default source predicate | Supported, using existing all-pretax taxation |
| Traditional401K/403B -> same-owner RothIRA | Supported without legal availability facts | Preserved under explicit legacy-UNKNOWN modeling assumption |
| Roth401K as source | Could record a fully taxable conversion | No taxable conversion capacity |
| Roth401K as its own source/destination | Could leave balance unchanged yet record taxable conversion | Rejected; no conversion or conversion income |
| Traditional source -> Roth401K or inherited Roth | Broad category admitted it | No capacity or transfer to that destination |
| Unsupported Roth before a valid RothIRA | Could select the wrong destination | Skip unsupported accounts, use first valid same-owner RothIRA |
| No ordinary same-owner RothIRA | Household might use another broad-Roth type | Household skips owner; no account creation; request retained |

No old plan or historical result is migrated. Existing schema versions and financial fingerprints are not redefined. Accepted snapshots restore their saved values, including old-model results; they are not recalculated or repaired by this change. A newly executed analysis can legitimately differ in the intentionally corrected cases above. Equal configured input digests are not used to rewrite old accepted artifacts.

Michigan already distinguishes traditional IRA qualification from rollover/original-employer-plan qualification; enabling rollover conversion does not make those state-tax qualifications equivalent. The new federal-equivalence fixture explicitly uses Florida's existing no-general-income-tax model to isolate conversion arithmetic. No state adapter or tax rule was changed.

Unsupported IRA basis/pro-rata calculations, designated-Roth rollovers, inherited-beneficiary conversions, new employer availability contracts, Roth five-year accounting and automatic Roth IRA destinations remain outside scope. No UI eligibility gating or new user controls were added in this narrow domain-correction phase.

## Regression coverage and development findings

New tests were written before the financial changes. The initial `RothEligibilityExecutionTest` run produced **16 tests, 7 failures, 0 errors, 0 skips**, demonstrating the old eligibility defects. Log: `target/roth-eligibility/before.log`.

New coverage:

- `RothEligibilityExecutionTest` — all eleven source types, wrong Roth destinations, later valid destination ordering, spouse-owner isolation, absent owner/zero projected capacity, self-conversion rejection, exact $50,000 debit/credit and total balance conservation, no persisted mutation.
- `RothConversionEligibilityPolicyTest` — type vs balance distinction, immutable reasons, projected vs live balance/precision, missing/different/absent owner and same-account reasons, explicit availability states, participant facts not upgrading UNKNOWN, legacy-assumption flag and unavailable refusal.
- `RothEligibilityFinancialTest` — every existing schedule strategy across single/couple households, rollover vs traditional federal financial outcomes, all strategy types with unsupported destinations/sources, zero actual conversion income in rejected cases, save/reload/copy, strict configured-document and digest preservation, no new serialized fields, retained-result encode/decode/exact restoration, no automatic account insertion.

The first post-change focused run found an existing direct-converter error-message contract had shifted because destination assessment preceded the old source selection contract. The implementation was corrected; the existing assertion was preserved. A subsequent new-fixture failure exposed Michigan's deliberate rollover qualification difference; the new comparison fixture was changed to isolate federal arithmetic in Florida, rather than altering state financial rules or weakening an existing test.

Existing Roth tests continue to exercise multi-source/owner allocation, source depletion and capacity shortfalls, requested-vs-actual taxation, pre-satisfied opening RMDs, RMD-before-conversion ordering, bracket-fill tax feedback, fixed/custom schedules, persistence and both UI configuration surfaces. No existing test or golden was edited.

## Verification

All Maven commands use IntelliJ's bundled Maven, the required project-local `.codex-m2/repository`, and `-DargLine=-Xmx4g`.

Focused selection: `*Roth*Test,AccountFactoryTest,AccountTaxTreatmentTest,AccountTypeTest,ProjectionSnapshotPersistenceTest`.

Final focused result: **210 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS (20.290 seconds). Log: `target/roth-eligibility/focused-verified.log`.

Full Maven result: **3,856 tests, 3,810 passed, 0 failures, 0 errors, 46 skipped**, BUILD SUCCESS, exit 0 (10:35 minutes). Log: `target/roth-eligibility/full.log`. Summing the 438 Surefire XML reports written during this run independently confirmed Maven's totals; older diagnostic XML was excluded by run-start timestamp. Existing optional/disabled tests remain skipped; no tests were disabled or weakened. Financial golden, seeded Monte Carlo, state-tax, snapshot, swap, analyzer, PDF and UI regression tests ran under the existing suite configuration without changing expected resources.

Final `git diff --check`: **exit 0**; only existing LF/CRLF conversion notices. SHA-256 recheck confirmed **zero changes to every pre-existing modified/untracked file**. Staging index remains empty; branch and HEAD are unchanged. No UI previews are needed for this domain-only change; existing UI tests are included. Native Windows acceptance remains pending.

## Exact phase file inventory

Production additions:

1. `src/main/java/com/daviddunn/retirementplanner/domain/roth/RothConversionEligibilityPolicy.java`

Production modifications:

2. `src/main/java/com/daviddunn/retirementplanner/domain/financial/Account.java`
3. `src/main/java/com/daviddunn/retirementplanner/domain/financial/TraditionalIRA.java`
4. `src/main/java/com/daviddunn/retirementplanner/domain/financial/Traditional401K.java`
5. `src/main/java/com/daviddunn/retirementplanner/domain/financial/Traditional403B.java`
6. `src/main/java/com/daviddunn/retirementplanner/domain/financial/Roth401K.java`
7. `src/main/java/com/daviddunn/retirementplanner/domain/roth/ProjectedPortfolioRothConverter.java`

Test additions:

8. `src/test/java/com/daviddunn/retirementplanner/domain/roth/RothEligibilityExecutionTest.java`
9. `src/test/java/com/daviddunn/retirementplanner/domain/roth/RothConversionEligibilityPolicyTest.java`
10. `src/test/java/com/daviddunn/retirementplanner/domain/roth/RothEligibilityFinancialTest.java`

Documentation:

11. `PHASE-13I-A-ROTH-CONVERSION-ELIGIBILITY.md`

Generated logs/hash inventories remain ignored under target. All baseline work is excluded from this phase inventory. No staging, commit, push, reset, clean or discard command was run.

## Native acceptance checklist and Phase 13I-B boundary

1. Run an unchanged valid Traditional IRA -> same-owner Roth IRA plan; compare amounts, taxes, RMDs, balances and estate to the accepted original result.
2. Run a Rollover IRA source with a same-owner ordinary Roth IRA; confirm execution and actual-conversion tax treatment, while retaining state-specific qualification messages.
3. Test a Roth401K-only source, a traditional source with only Roth401K/inherited-Roth destinations, and no destination. Confirm no invalid conversion, no automatic account and the saved schedule remains visible.
4. Test primary-only, spouse-only and single-person plans; several sources/destinations and a depleted source. Confirm owner isolation, order, capacity and shortfall.
5. Verify existing traditional 401(k)/403(b) -> ordinary RothIRA cases still execute as before. This is the legacy modeling assumption, not confirmation of real-world distribution eligibility.
6. Save/reopen/Save As and inspect original history/baselines/associated PDFs. No accepted historical result should be recalculated, rewritten or silently repaired. Projection swapping retains existing exact-document/ownership guards.
7. Check fixed, custom and bracket-fill strategies, RMD-year cases, conversion tax funding and Medicare settlement using the existing workflow.

Stop after automated verification for review/native acceptance. Automatic destinations, availability fields/policies and any Phase 13I-B UI changes require subsequent explicit authorization.
