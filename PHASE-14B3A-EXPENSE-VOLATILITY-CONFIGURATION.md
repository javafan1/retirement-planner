# Phase 14B3A — Expense Volatility Configuration

## Implementation summary

Implemented the approved optional recurring-expense volatility configuration, lossless persistence, shared normal/guided editor, deterministic applicability separation and temporary public Monte Carlo admission gate. Enabled stochastic financial execution remains internal-only. This phase does not implement 14B3B provenance or 14B3C public enabled execution.

No projection, expense escalation, tax, withdrawal, Roth, RMD, Medicare, mortality, return or sampling formula changed. No existing random-stream identity, financial golden fixture, global mapper configuration, snapshot schema version or accepted historical result was changed.

## Git baseline and preserved work

Branch `main`, starting HEAD `fbf84d1440e640b852c3f0733a6834f7615cab00`. Initial `git status --short` showed only modified `.idea/encodings.xml`; there were no untracked Phase 14B2 source/report files or uncommitted Phase 14B2 changes. Phase 14B2 was already present in the committed baseline. The index was empty and remains empty.

Preserved `.idea/encodings.xml` without editing it. No staging, committing, pushing, resetting, cleaning or discarding occurred.

Read AGENTS.md, the approved Phase 14B3 architecture report, and the Phase 14 architecture/14A/14B1/14B2 reports; verified their assumptions against actual source.

## Domain and persistence contract

`Expense` now has optional decimal `annualExpenseVolatility`. Existing two-, three- and six-argument constructors remain compatible; a seven-argument overload supports explicit configuration.

- Null/missing/numeric zero means disabled. All numeric zeros, including 0.00, normalize to null and serialize as the original property-absent form.
- Nonzero BigDecimal precision/scale is preserved; no double conversion or rounding occurs in the domain or persistence layer.
- The inclusive decimal range is [0, 1]. Nonzero values require recurring type and a nonnegative annual amount. A zero amount can have volatility. Legacy negative expenses remain loadable when disabled.
- The new raw getter alone uses NON_NULL. The effective zero-default getter is ignored by Jackson. No global inclusion/coercion setting changed.
- General, Healthcare and No Inflation remain independent from volatility. One-time expenses reject nonzero volatility.

`ExpenseDeserializer` is a focused streaming adapter. It delegates individual values to the current Jackson context, preserving the mapper's decimal/date/enum validators, numeric limits and scalar-coercion policies. When FAIL_ON_MISSING_CREATOR_PROPERTIES is enabled, all six original creator properties remain required; only the newly optional volatility property may be absent. Required amount/description remain non-null. Expense's pre-existing unknown-property behavior remains unchanged. No global corruption or creator check was relaxed.

Existing exact legacy JSON/input/analyzer/Monte Carlo fingerprint assertions pass unchanged. New tests cover missing/null/0/0.00/0E+3 omission, strict missing-old-property rejection, invalid required values and a nonzero value `0.1234567890123456789000` through ordinary Save/Load, another file, scenario cloning, configured-input freezing/restoration and strict expense decoding.

The Save As/restart controller test proves configured volatility and the accepted original snapshot remain separate: the new plan owns its independent components, the original snapshot UUID and input digest survive unchanged, and the original zero-volatility configured document is not rewritten to match a later edit.

## Deterministic applicability and exact role eligibility

`DeterministicExpenseInputs.removeVolatility` removes only `annualExpenseVolatility` in the known `household.expenses` array, and only for recurring records. It does not remove other similarly named properties or any amount, date, inflation/escalation, horizon or other financial assumption.

`CompleteProjectionSnapshot.matchesDeterministicInputs` compares input documents using exact decimal trees after this narrow exclusion. Its existing `matches`, `inputsDocument`, `inputDigest`, immutable identity and digest verification are unchanged. Tests prove annual amount changes, escalation changes and a financial amount scale change remain differences.

`AnalysisFingerprintService` applies the same narrow exclusion to deterministic/weighted integrated plan inputs. The Social Security-only input contract remains unchanged. Existing zero-volatility fingerprint constants continue to pass; no fingerprint version or numeric normalization was changed.

For expense edits, MainWindow checks deterministic applicability before advancing the ordinary dirty/source-revision guards. If applicable, it retains the accepted projection, charts and summaries and refreshes only command eligibility/title/status. It does not call the projection refresh path, rebuild charts, or invoke Break-Even. Amount/escalation or other effective input differences still use the original projection refresh behavior. An unavailable/unverifiable accepted context remains conservative.

ApplicationController restoration notices and Baseline Details deterministic status use this financial applicability check. Full configured-input equality remains authoritative for swaps, baseline capture and analyzer-association admission. A volatility-only edit therefore remains an unsaved configured-input edit even though its accepted deterministic financial results are applicable. Saving that edit does not silently change the accepted input document or authorize a swap.

The controlled transition test prepares a swap, edits volatility and marks modified, then proves the source revision advanced, the projection and snapshot are retained, exact matching fails, swap availability refuses the transition, and the obsolete prepared swap is rejected. Another test invokes the actual MainWindow expense callback with throwing projection and Break-Even factories and checks retained results and refreshed swap-button eligibility. Fifteen parameterized cases prove that older analyzer admissions cannot attach after a volatility edit, for every supported analyzer identity. No execution admission/revision/generation or cross-analysis join guard was relaxed.

## Public Monte Carlo admission gate

`ExpenseVolatilityAdmission.requireSupported` rejects any positive configured volatility with:

> Stochastic expense execution is not yet available. Set annual expense volatility to 0% for all expenses before running Monte Carlo analysis.

The gate runs in both public standalone execution cores (fixed and mortality) and for each paired candidate. It also runs in the standalone UI service before reference projection work and in paired UI capture for Current and Saved Baseline. Caller-supplied paths/worlds cannot bypass the public gate. Package-private internal B2 execution remains separate and its wrappers are not exposed as legacy results.

Zero-volatility execution takes the original financial branch and generates no expense path. Existing market, inflation and mortality streams and seeded financial expectations are unchanged. Paired admission tests cover couple/single × fixed/longevity and reject enabled candidates before a throwing world supplier can execute. Existing B2 enabled internal integration tests continue to pass.

## Shared editor and wizard

The existing ExpenseDialog serves both normal and guided editing; no separate wizard implementation was introduced.

- Label: **Annual Expense Volatility (%)**; default 0; inclusive 0–100.
- Newly entered values allow up to two decimal places. The exact original imported text may contain more precision and is preserved if unchanged. No rounding is used to fit the control.
- Accessible name/linked label and wrapping tooltip explain independent annual variation, deterministic exclusion, independence from escalation and relative standard deviation rather than a maximum change. The tooltip also explains the temporary execution gate.
- The field is hidden/unmanaged for one-time expenses. Switching to one-time explicitly clears it and displays a visible clearing notice.
- Negative legacy values show explanatory text and disable the zero-valued volatility field. A positive value being edited can still be cleared before saving a negative amount; nonzero negative combinations are rejected.
- Guided validation uses the existing in-dialog error mechanism. Normal validation uses the established expense error dialog. Cancel preserves the original immutable expense.
- Existing theme styling is inherited; no CSS palette or global style change was required.

## Automated verification

Initial compatibility run: 25 total, 23 passed, 2 optional skipped, no failures/errors. It covered legacy exactness, existing escalation UI and snapshot codecs.

First expanded run: 70 total, 67 passed, 2 skipped, one new-test error. The test incorrectly expected obsolete swap rejection to return false; the established API throws IllegalStateException. Corrected only that new expectation to assert the existing rejection contract. No production behavior or existing test was changed to accommodate the error.

Intermediate focused run: 151 total, 149 passed, 2 skipped, zero failures/errors; BUILD SUCCESS, 30.376 seconds. During final review the non-Monte-Carlo family exclusion was made explicit and the late-completion/UI-callback coverage was added. Its first run had one new-test setup error: scene lookup could not find an unskinned tab's button. The corrected test obtains the actual existing button field and retains the same disabled-state assertion; no production correction or existing assertion weakening was needed.

Final focused run: **182 total, 180 passed, 2 skipped, zero failures/errors; BUILD SUCCESS, Maven exit 0, 34.097 seconds.** Selection:

- ExpenseVolatilityCompatibilityTest
- ExpenseVolatilityUiTest
- ExpenseVolatilityAdmissionTest
- ProjectionSwapTest
- Phase12FAnalyzerSwapTest
- ExpenseEscalationCompatibilityTest
- MonteCarloSnapshotCodecTest
- StochasticExpenseMonteCarloTest
- MonteCarloNumericCompatibilityTest
- AnalysisFingerprintTest
- ExpensesWizardStepTest

The two optional skipped codec cases require `mc.codec.capacity` and `mc.codec.replay`. Maven used the mandated IntelliJ executable, project-local `.codex-m2/repository` and `-DargLine=-Xmx4g`. Logs in `target/phase14b3a/` include `initial.log`, `focused.log`, `focused-final.log`, `focused-integrated.log`, `focused-complete.log`, `full-suite.log` and `full-suite-final.log`.

First full run: **3,773 total, 3,727 passed, 46 skipped, zero failures/errors; BUILD SUCCESS, 10:37 minutes.**

Final full run, including the explicit family guard and additional admission/UI cases: **3,789 total, 3,743 passed, 46 skipped, zero failures/errors; BUILD SUCCESS, Maven exit 0, 10:42 minutes.** Fresh Surefire XML agrees with the Maven totals. One pre-existing October 19 diagnostic XML (`WeightedRestorationTest-phase9c-compatibility.xml`) was excluded from XML aggregation by timestamp, not deleted; it was not part of this Maven execution.

The 46 skips are 41 existing property-gated optional visual/performance/capacity/user-plan cases, four worksheet cases awaiting guidance and one explicitly disabled multi-minute production mortality benchmark. No exclusions, disabled tests or weakened assertions were introduced. Exact skipped names/reasons: `target/phase14b3a/skipped-tests.txt`; fresh XML totals: `target/phase14b3a/surefire-totals.json`. Expected injected repository failures were logged by recovery tests that passed. No financial golden or seeded expectation changed.

`git diff --check` passed (exit 0); only routine LF/CRLF conversion notices were emitted. Final staging index is empty; HEAD remains the starting commit. No resources, financial golden files, POM/dependencies or unrelated work were modified.

## Visual verification

Generated and inspected actual JavaFX DialogPane snapshots, not mockups:

- `target/phase14b3a/previews/LIGHT-standard.png`
- `target/phase14b3a/previews/DARK-standard.png`
- `target/phase14b3a/previews/LIGHT-guided.png`
- `target/phase14b3a/previews/DARK-guided.png`

The field, imported precision, labels and buttons are readable in both themes. Guided content wraps, with no clipping observed at its normal compact size; the inspected snapshots are approximately 550–560 pixels wide and 360–485 pixels high. Automated interaction verifies invalid entry handling, clearing/hiding on type change, negative-amount explanation and preservation of imported precision. These generated previews do not claim manual native Windows acceptance or DPI/keyboard certification.

## Exact implementation inventory

Production modified (paths relative to `src/main/java/com/daviddunn/retirementplanner/`):

1. `domain/financial/Expense.java`
2. `app/projection/CompleteProjectionSnapshot.java`
3. `app/socialsecurity/results/AnalysisFingerprintService.java`
4. `app/montecarlo/MonteCarloAnalyzer.java`
5. `app/montecarlo/MonteCarloStrategyComparisonAnalyzer.java`
6. `ui/MainWindow.java`
7. `ui/controller/ApplicationController.java`
8. `ui/dialogs/ExpenseDialog.java`
9. `ui/montecarlo/MonteCarloRunService.java`
10. `ui/montecarlo/MonteCarloStrategyComparisonRunService.java`
11. `ui/views/ResultsSummaryView.java`

Production added under the same root:

12. `domain/financial/ExpenseDeserializer.java`
13. `app/projection/DeterministicExpenseInputs.java`
14. `app/montecarlo/ExpenseVolatilityAdmission.java`

Tests modified:

15. `src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSwapTest.java` — three additional cases; existing assertions retained.
16. `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/Phase12FAnalyzerSwapTest.java` — fifteen additional parameterized admission cases; existing assertions retained.

Tests added:

17. `src/test/java/com/daviddunn/retirementplanner/persistence/ExpenseVolatilityCompatibilityTest.java`
18. `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/ExpenseVolatilityAdmissionTest.java`
19. `src/test/java/com/daviddunn/retirementplanner/ui/dialogs/ExpenseVolatilityUiTest.java`

Report added: `PHASE-14B3A-EXPENSE-VOLATILITY-CONFIGURATION.md`.

Excluded from intended change inventory: pre-existing `.idea/encodings.xml`, ignored target logs/Surefire output/previews. No financial engine, sampler, golden resource, PDF adapter, application settings or dependency file was changed.

## Remaining 14B3B requirements and limitations

Positive settings can be saved but cannot yet execute as supported Monte Carlo analyses. This is intentional and visible in the editor tooltip and admission error. The next separately approved stage must implement explicit enabled model provenance, the approved distinct artifact/fingerprint contracts, bounded validation and original-context historical handling before enabled public execution is authorized in C.

As approved, exact configured-document matching can refuse swaps/baseline capture after a volatility-only edit despite applicable deterministic results. No snapshot is silently upgraded. Old binaries can ignore unknown expense fields, so faithful downgrade execution of positive-volatility plans is not guaranteed; retain backups/use the newer application. Existing immutable historical payloads are unchanged.

No new unresolved policy decision was required. Phase 14B3B has not begun.

## Native Windows acceptance checklist

1. Normal expense editor and new-plan wizard: default 0%, two-decimal entry, 0/100 boundaries, negative/over-100/invalid text and high-precision imported values.
2. Check Light/Dark, keyboard labels/tooltips, Escape/Cancel, reduced window and Windows display scaling.
3. Toggle recurring to one-time and confirm visible clearing; verify escalation remains independent; test negative legacy and zero-dollar expense records.
4. Save, close/reopen and Save As: confirm exact nonzero values and independent ownership.
5. With an accepted projection, change only volatility: dirty marker updates, charts/financial values remain unchanged and no projection runs. Check swap refuses until exact configured-input mismatch is explicitly resolved.
6. Change annual amount or escalation: existing projection refresh/invalidation behavior remains.
7. Standalone fixed/longevity and paired Current/Baseline Monte Carlo: either positive candidate produces the explanatory refusal; all-zero plans run as before.
8. Open existing historical results/PDFs: original values, identities and contexts remain unchanged.

Stop after verification and request review/approval before Phase 14B3B.
