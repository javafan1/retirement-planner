# Phase 23C — Insured-Contingent Insurance Premium Expenses

Implementation date: November 7, 2026. Application: Aledrix Retirement Planning. Initial branch `main`, HEAD `42950386184a80a0ae4521257bff261f2ecd2d94`. Regression classification: Level 3.

## 1. Executive summary

Implemented a closed contractual insurance premium form in the existing expense model and editor. Premiums use a named insured's existing death state, remain at their fixed nominal annualized rate while eligible, stop in the insured's January 1 death year, and bypass household survivor-spending reductions. They enter ordinary expenses and cash settlement once. No asset, deduction, random draw, policy linkage or automatic death benefit is created.

Supported execution uses the authoritative ProjectionEngine across deterministic, integrated, weighted and all eight generated Monte Carlo configurations. Premium-bearing weighted inputs use independent full evaluations; existing premium-free optimizations remain available. Phase 23B excluded death principal remains an independent, explicitly qualified conditional receipt.

Checkpoint A is recorded in `PHASE-23C-A-CONTRACTUAL-PREMIUM-ARCHITECTURE.md`. The selected additive contracts require neither a data migration nor a historical reinterpretation. Old expense readers reject the new required discriminator. Existing numerical/fingerprint/provenance contracts remain unchanged for plans without premiums.

Final complete normal regression: **4,592 tests, 4,546 passed, zero failures/errors, 46 unchanged existing skips across 514 suites**. Eighty-five new premium tests pass. Source/fixture preservation and whitespace checks passed; no old tests, golden values or build settings were changed. Native Windows acceptance is requested and has **not** been claimed.

## 2. Checkpoint A architecture decision

Selected Alternative A: extend Expense with a closed `CONTRACTUAL_INSURANCE_PREMIUM` form and required immutable policy. Alternative B would duplicate plan-level expense storage, copying, editing and evaluation. Alternative C would incorrectly turn expenditure into negative income and risk tax/source misclassification.

Source audit confirmed the old reader skips unknown properties, so an optional insured field alone is unsafe. The new form uses the existing `expenseType` discriminator; policy metadata is not sufficient without it. The preserved old reader is compiled in an isolated classloader to test actual behavior, rather than relying on an assumption about enum compatibility.

Reviewed the relevant Phase 23A/B, Phase 14 expense, Phase 21 stochastic expense, Phase 22 domain/execution/UI and Phase 12/19 history/compatibility reports against current code. Current source governs where intermediate reports describe restrictions later removed.

## 3. Exact domain and persistence contracts

`Expense` retains its existing constructors and adds an overload accepting `ContractualPremiumPolicy`. Existing ordinary values serialize without a policy property. The new type requires:

- Description: 1–128 printable characters, nonblank.
- Annual amount: nonnegative BigDecimal, at most 1,000,000,000,000, precision at most 24, scale −12 through 8.
- Named insured: PRIMARY or SPOUSE. JOINT is not representable as an insured.
- Required start, optional inclusive end, years 1000–9999 and ordered dates.
- `GrowthCategory.NONE`, zero volatility, model `INSURED_ALIVE_FIXED_NOMINAL_MONTHS_V1`.

The immutable policy contains only model and insured enums, without Person, account, owner, payer, beneficiary or UI references. No premium UUID is introduced: Expense already uses separate value occurrences. Identical independent premiums remain separate occurrences, while descriptions and plan UUIDs do not determine financial effects.

The local reader rejects missing/unknown/duplicate policy fields, unsupported model/insured/type, invalid numeric/date combinations, inflation or positive volatility. Premium forms reject unknown top-level properties as well. A policy on an ordinary expense is invalid. Legacy ordinary unknown-property behavior is retained. The original strict six-field snapshot requirements remain intact.

Household mutation and plan/snapshot admission validate spouse presence. Premium-bearing expense lists are bounded at 1,000 occurrences; replacing an item at the boundary is allowed. Existing resource/document/horizon/trial budgets remain in effect.

## 4. Old-reader compatibility behavior

The new premium uses `expenseType=CONTRACTUAL_INSURANCE_PREMIUM`. The accepted pre-change reader explicitly decodes ExpenseType, whose old values are only RECURRING and ONE_TIME. It rejects this value even when unknown properties are ignored. New readers also reject `contractualPremium` on either legacy type, preventing a mixed form from being interpreted as ordinary spending.

Four exact pre-change Java source fixtures under `src/test/resources/phase23c/legacy-expense/` reproduce the reader, Expense, ExpenseType and GrowthCategory. A test compiles them with Java 25 and loads their financial package separately from the current implementation. It successfully reads an ordinary record and rejects a premium record. This verifies the accepted predecessor, not every hypothetical older/custom application configuration.

Older releases cannot execute or restore premium-bearing financial inputs as ordinary expenses. Existing premium-free files retain their wire format; no migration or automatic rewrite occurs. Historical numerical results and original documents are not upgraded or recalculated.

## 5. Expense timing and escalation

The new form models an **annualized premium budget over eligible calendar months**, not an insurer billing ledger. Intersect the premium start/end with the projection opening and current calendar year. Inclusive overlapping months count once, including partial months:

`annual premium expense = annual amount × eligible months ÷ 12`, rounded once to cents HALF_UP.

Examples: $2,000 over twelve months = $2,000; six months = $1,000; a single eligible month = $166.67. A January 15 termination includes January. A December 1 termination includes December. A schedule ending in 2034 owes nothing in 2035. The selected death rule supersedes the whole annual interval.

Only fixed nominal escalation is supported. General inflation, healthcare inflation, stochastic inflation and ordinary expense volatility do not increase a contractual premium. Existing ordinary expense start/end overlap, opening-year proration, escalation, volatility and rounding are unchanged. An annual rate does not assert a January 1 annual bill, precise due-day cash timing or a carrier refund.

## 6. Insured death semantics

`ContractualPremiums.annual` uses the same EffectiveHouseholdDeathView as the projection. PRIMARY/SPOUSE are explicit insured roles. An insured death is effective January 1: no premium in that year or later. The other person's death does not terminate or reduce the premium. Pre-opening and opening-year deaths, both deaths, reverse order and explicit lifetime scenarios are tested.

No new mortality event, probability, refund calculation or random stream exists. A missing spouse is rejected rather than treated as dead. Single-person PRIMARY-insured premiums remain valid while the primary lives.

## 7. Survivor-spending exemption

The engine's premium branch runs before ordinary expense escalation/sampling/proration/survivor adjustment, then continues to the next expense. Ordinary `RECURRING` expenses retain their original path. Tests exercise factors 0%, 50%, 75% and 100%: a surviving insured's full-year premium remains $2,000 while a $10,000 lifestyle control follows the configured factor.

Insured death selection and survivor-spending exemption are separate rules. Exempting a premium from lifestyle reductions never keeps it payable after its own insured dies.

## 8. Federal and state tax effects

Premiums are ordinary cash expenses with no deduction, income source or retirement participant classification. Federal/state calculators and TaxIncome interfaces are unchanged. No insurance deduction, policy basis, new state treatment or pension exclusion is inferred.

New financial controls compare premium expenses with independently scheduled one-time spending of identical annual dollars under MI, IL, FL, PA and IA. They compare all annual BigDecimal projection outputs, including taxes, withdrawals, Roth, RMD, assets and estate, plus Medicare MAGI/premiums. Premium-only spending introduces no conditional-income state classification. Coexisting conditional benefits retain their accepted MI/IL/no-general-income-tax admission; PA/IA benefit restrictions are not broadened.

## 9. Household cash conservation

Premium selection adds to the authoritative annual expense total once. Existing withdrawal allocation, tax funding, Medicare and HouseholdCashSettlement consume that total. No direct live Account deposit/withdrawal, extra balance-sheet pool or policy asset is added.

Known-answer zero-return tests reconcile guaranteed income + period RMD + spending withdrawals + tax-funding withdrawals against expenses + Medicare + taxes + retained surplus. The $2,000 expense lowers household wealth through established settlement. Rich IRA/Roth tests establish the resulting indirect taxes rather than assuming taxes are zero. Persisted account balances remain unchanged by projection.

## 10. Phase 23B interaction

Premium and benefit definitions are independent. In the tested PRIMARY-death case, death-year premium is zero, gross conditional receipt is $400,000, taxable conditional income is zero, and excluded principal is $400,000. Existing retained-cash settlement recognizes unspent proceeds once. The premium does not create the receipt or control its coverage.

No changes were made to the ConditionalBenefitEvaluator, legal qualification assertion, tax split, recipient rules or excluded-principal financial mathematics. The existing limitation concerning realization of retained nonqualified investment growth remains unchanged; premium functionality does not create an exemption for investment earnings.

## 11. Monte Carlo stochastic protocol

Premium specifications are disabled sampling entries. MonteCarloExpenseGenerator skips disabled entries before key generation, duplicate numbering and draws. Its code, normalized ordinary definition correspondence, random-stream registry and all market/inflation/mortality generators remain unchanged. Expense dimension/version stays **6/1**.

The engine's premium branch never requests an expense multiplier. Runtime ProjectionExpensePath financial bindings additionally retain the premium policy, so changing an insured cannot reuse a differently bound input path. This runtime-only binding extension does not change stored expense capability V1 or its stochastic correspondence.

Tests verify identical ordinary occurrence keys/ordinals and multiplier values after inserting a premium, unchanged economic/mortality/inflation worlds, same-seed reproducibility, and direct full-engine scenario oracles. No additional per-scenario receipt history or premium RNG is persisted.

## 12. Deterministic and weighted integration

| Workflow | Premium support / execution |
|---|---|
| Couple/single ordinary deterministic | Common ProjectionEngine expense selection |
| Couple integrated individual / Quick Comparison | Isolated full-plan projections, original premium policies retained |
| Complete integrated search / claiming grid / heat map | Existing strategies and metrics; each financial projection includes expenses |
| Single-person integrated deterministic/weighted | Existing full-plan projection and primary mortality context |
| Couple longevity-weighted / strategy comparison | Existing scenario deaths and prior-December-31 estate horizon; independent full evaluation |
| Continuation/equivalence/prefix optimizations | Narrowly disabled for premium-bearing inputs; original premium-free behavior retained |
| Opening last-death estate shortcut | No living insured / no modeled premium obligation after opening death; established estate metric unchanged |
| Social Security-only | Financial spending remains outside the objective and ranking |
| Generated MC: couple/single standalone fixed/longevity | Supported, with zero or positive ordinary expense volatility |
| Generated MC: couple/single paired fixed/longevity | Supported, candidate-specific original policies |
| Caller-supplied MC worlds/paths or internal expense overrides with premiums | Remain unsupported, with admission rejection |

Weighted tests compare reference and continuation entry points with explicit lifetime projections at matching deaths, preserving scenario probability and aggregation. No horizon, mortality weight, discount, claiming strategy, tax or estate definition changes.

## 13. Historical provenance and version matrix

CompleteProjectionSnapshot remains V1; retained annual and envelope schemas remain unchanged. Premium-bearing deterministic completions append a digest-bound `contractual-premiums=insured-alive-months-v1` stamp to existing provenance. Existing pension-selection=death-selected-v2 and benefit deterministic stamps retain their meanings. The binding authenticates original input digest and retained annual data with canonical ISO dates and sorted map keys, verified by two independent JVM readers. Restoration validates the saved binding without evaluating premiums. Duplicate/missing/mismatched premium stamps reject; unrelated unsupported legacy inputs retain their original read-only compatibility handling.

The existing bounded analyzer original-input capability container (`benefitExecution`) adds disjoint **V3**, model `ANALYZER_CONTRACTUAL_PREMIUMS_V3`, algorithm `analyzer-contractual-premiums-v3`. Its historical property/key name is retained to avoid another envelope shape. V3 explicitly represents composed premium/conditional-receipt execution and is required iff an original candidate contains premiums. V1/V2 remain benefit-only. Candidate input and definition digests include original premium policies as well as any benefits. Family, protocol, roles, household shape and enabled facts are checked.

| Original financial features | MC payload | Stored fingerprint | Analyzer capability |
|---|---:|---:|---|
| Legacy disabled expenses/benefits | V1 | V1 | None |
| Temporary legacy numeric compatibility | V1 | Numeric V2 read-only | None |
| Standalone stochastic ordinary expenses | V2 | V3 | Existing ExpenseExecution |
| Paired stochastic ordinary expenses | V3 | V4 | Existing PairedExpenseExecution |
| Taxable conditional benefits: standalone / paired | V4 / V5 | V5 / V6 | Benefit V1 |
| Excluded death principal: standalone / paired | V6 / V7 | V7 / V8 | Benefit V2 |
| Premium-bearing standalone, optional supported benefits/ordinary volatility | **V8** | **V9** | **Composed V3** |
| Premium-bearing paired, either/both candidates | **V9** | **V10** | **Composed V3** |

Existing no-premium combinations and legacy maps are not repurposed. New contracts reject missing/downgraded capabilities, mismatched family/candidates/original inputs, wrong payload/fingerprint combinations and unauthenticated premium-bearing legacy payloads. Original artifacts, UUID/digest ownership, association fences, atomic writes and Retry Save contracts remain intact.

## 14. Current/Baseline independence

Each comparison candidate retains its own frozen expense list and insured policy. Tests cover Current-only, Baseline-only, identical, different amount, different insured, different dates, independent death benefits and positive ordinary volatility. Different descriptions and plan UUIDs do not change economic effects. Identical economic candidates have zero comparison deltas under shared worlds.

Financial input trees retain type, policy/model/insured, amount, dates and fixed nominal escalation. New premium numeric normalization and ordering apply only to premium occurrences. Descriptions and plan UUIDs remain excluded; exact original documents preserve descriptions, ordering and numeric scale. Existing ordinary-expense identities and premium-free fingerprints remain unchanged.

## 15. UI controls and validation

The existing Expenses workspace adds Contractual Insurance Premium to its household-aware editor. Fields: description, annual amount, insured person, required Premium Start Date, optional Premium End Date. Fixed nominal escalation is readable text. Premium volatility and inflation choices are hidden; unavailable fields do not enter the committed definition. Friendly Primary/Spouse choices map to closed roles, omitting an absent spouse.

Existing required-field TextFlow attachments mark description/type/amount, applicable date and insured controls. Domain constructors remain authoritative. Numeric and date parsing use BigDecimal and the existing calendar controls. Recoverable errors retain the draft and show feedback; invalid premium dates, absent insureds and unsupported combinations cannot Save.

The form scrolls independently from fixed Save/Cancel buttons. Editing returns a new immutable Expense through the existing household replacement and plan-changed callback. Switching to ordinary spending omits the policy. Cancel/window close do not commit or notify. The existing expense workspace has Add/Edit/Remove; it had no Duplicate action, so no new duplication workflow or identity system was invented. Independent identical expense entries remain supported.

Actual Light/Dark editor and expense-list scenes were generated and inspected at 900×650 and 1180×820. Fixed buttons, required markers, readable nominal escalation, wrapped explanation, selected list row, insured column and dates were visible. The list previews use the ignored `target/phase23c/PremiumListPreview.java` helper and the synthetic acceptance plan; they did not change the production/test sources under regression. Windows DPI variations, screen-reader announcements and final keyboard/modal behavior remain native acceptance checks. No global theme/CSS/navigation change.

## 16. Current/historical PDF disclosures

Projection PDFs add a premium section only for premium-bearing originals, showing original amount, insured, payment dates, fixed nominal/month convention, insured-death stop and survivor exemption. Existing completed expense totals remain authoritative annual facts. Conditional death proceeds remain in their separate existing section.

Analyzer and MC reports reuse frozen original-input disclosures, separately labeled per candidate. No export invokes ProjectionEngine or either evaluator. Tests export original/history projection PDFs with identical extracted text after live edits; real MC views preserve original payload/fingerprint/run associations through Save As and restart with a throwing engine. Historical MC PDFs remain marked Historical/Outdated and show original $2,000, never today's edited $3,000.

Inspected rendered projection assumption/premium/benefit pages and the paired-longevity historical MC assumptions/table page. Text wraps, page numbers, charts and tables remain intact. Representative PDFs/images are under `target/phase23c/reporting/`. PDF View/Save/Cancel/default-directory code was not changed and remains covered by normal regression/native checks.

## 17. Focused verification

| Log | Outcome |
|---|---|
| reproduction.log | Permanent insured-death regression failed before adding the authoritative engine premium branch: expected zero, observed a continued premium |
| first-financial.log | 68 passed, including the existing Phase 23B life-insurance suite |
| premium-focused.log | 84 passed after domain/MC/editor integration, including existing expense UI tests |
| final-focused.log | **367 passed**, zero failures/errors/skips; premium, Phase 23B, expense, pension, fingerprint and snapshot compatibility suites |
| release-premium-focused.log | **83 new premium tests passed**, zero failures/errors/skips, after final timing/UUID boundary additions |
| compatibility-fix-focused.log | **153 passed**, zero failures/errors/skips; all 85 final premium tests plus legacy history/editor, expense, pension and snapshot regressions |
| required-field-preview-fix.log | **1 passed**, zero failures/errors/skips; existing Light/Dark main-view/editor required-field preview |

Development failures were fixed without modifying existing tests or financial expectations: a Jackson boolean/property-name collision that initially omitted the new policy; test fixtures with inconsistent DOB/claim dates; a fixed comparison test with mismatched configured deaths; and tests that initially compared object identity or the wrong cancellation exception type. These were implementation/test-construction corrections, not changes to established financial behavior.

The first complete regression exposed three errors across two integration causes missed by the initial focused selection: `BreakEvenReductionDisclosureTest` preserves invalid legacy financial inputs for read-only compatibility handling, while `RequiredFieldMainEditorTest` and `RequiredFieldMainViewsPreviewTest` inspect legacy editor labels before a ScrollPane skin exists. Premium validation now inspects only the decoded expense discriminator before requiring domain reconstruction; unrelated unavailable legacy inputs remain at their established compatibility boundary. The old dialog constructors retain their original content structure; the household-aware premium editor gets the scroll wrapper. The existing failing tests remain unchanged. The corrected financial/history/editor focused run passed **153 tests**, and the separate required-field preview passed. The fresh complete suite then passed; see section 18.

Restart verification also launches two independent Java 25 JVMs to read a saved Michigan complete projection, checking that canonical date/map serialization keeps its premium and existing benefit bindings valid. These child reads invoke no controller, evaluator or projection. An additional direct selector test rejects an absent spouse instead of treating absence as a death. There are now **85 new premium tests**.

Known-answer, source/use, independent scheduled-expense, direct scenario, state, Roth/RMD/IRMAA, all-eight generated, original-reader, capability, copy/fingerprint, UI transaction, Save/Load/Save As/restart/swap and historical PDF tests are included. Existing financial tests and golden resources were not edited.

## 18. Complete Maven regression

Final normal suite: **BUILD SUCCESS — 4,592 tests, 4,546 passed, zero failures/errors, 46 existing skips, 514 suites**, completed November 7, 2026 at 08:34:10 Eastern in **16:47**. Log: `target/phase23c/release-full-regression.log`. Totals are independently summed from the exact suite names in that fresh Maven log; an older October 19 phase9c compatibility report remains preserved in target and is not counted as a test from this run.

The initial attempt (`target/phase23c/full-regression.log`) ran 4,590 tests: 4,541 passed, zero assertion failures, three errors and 46 existing skips, in 48:26. Its three errors are the compatibility cases described above; all three passed unchanged after the source fixes, both focused and in the final complete suite. No failed test was excluded or weakened.

The 46 skips remain **42 existing opt-in/disabled cases and four pending-authoritative-guidance Iowa/Mississippi worksheet cases**. No new premium test is skipped. Exact classes, test names and reasons are retained in `target/phase23c/skip-inventory.json`; the unresolved worksheet research is not promoted to production tax support.

Existing Michigan attributed goldens (40 cases), supported-state goldens (58), Stage 3/4A couple goldens, Monte Carlo random-vector tests, Phase 21 stochastic expense/provenance tests, B0 pension tests, B2/B3/B4/23B benefit financial/history contracts and normal UI/export regressions pass unchanged. No numerical expectation or golden resource was regenerated. Initial integration failures involved legacy decode/editor structure, not a changed financial golden.

Invocation (PowerShell):

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' `
  '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' `
  '-DargLine=-Xmx4g' test
```

Environment: IntelliJ bundled Maven 3.9.9; Eclipse Adoptium Java 25.0.4.1; Windows 11, UTF-8. No test-class filter, new exclusions, build/dependency changes, fixture regeneration or test weakening. All **1,291 files** in the final production/test/resource/pom release manifest match their pre-run SHA-256 hashes. Final totals and skip evidence are recorded in `final-suite-totals.json` and `skip-inventory.json` under target/phase23c.

## 19. Synthetic acceptance fixtures

Production JsonRetirementPlanRepository writes/reads these ignored synthetic files without overwriting existing files. Each contains $500,000 brokerage assets, zero returns/inflation, controlled zero Social Security and full-year premium assumptions; Medicare is still an ordinary modeled cash use.

Directory: `C:\Users\david\IdeaProjects\retirement-planner\target\phase23c\`

| Exact filename | Household/death setting | Expected annual expense |
|---|---|---|
| couple-alive.json | Couple, both survive | $2,000 in 2030–2037 |
| spouse-dies.json | PRIMARY insured, spouse dies 2035, survivor factor 75% | $2,000 in 2030–2037 |
| insured-dies.json | PRIMARY insured, primary dies 2035 | $2,000 in 2030–2034; $0 from 2035 |
| premium-and-death-principal.json | Same primary death; separate qualified $400,000 spouse receipt, coverage 2030–2040 | Premium $0 in 2035; one $400,000 excluded principal receipt in 2035 |
| single-primary.json | Single primary alive | $2,000 in 2030–2037 |
| ordinary-control.json | Ordinary nominal $2,000 expense; spouse dies 2035; factor 75% | $2,000 through 2034; $1,500 from 2035 |

Premium coverage/payment interval is January 1, 2030 through December 31, 2040. Fixture death-benefit coverage is independent. Amounts above are expense/receipt facts, not a promise that final assets ignore Medicare or other cash uses. Original account balances remain $500,000; no insurance asset is present.

Full native paths:

```text
C:\Users\david\IdeaProjects\retirement-planner\target\phase23c\couple-alive.json
C:\Users\david\IdeaProjects\retirement-planner\target\phase23c\spouse-dies.json
C:\Users\david\IdeaProjects\retirement-planner\target\phase23c\insured-dies.json
C:\Users\david\IdeaProjects\retirement-planner\target\phase23c\premium-and-death-principal.json
C:\Users\david\IdeaProjects\retirement-planner\target\phase23c\single-primary.json
C:\Users\david\IdeaProjects\retirement-planner\target\phase23c\ordinary-control.json
```

## 20. Native Windows acceptance checklist

1. Open each fixture; confirm the existing Expenses workspace shows type, annual amount, named insured and dates. Ordinary controls remain ordinary.
2. Edit a premium to $3,000 and Cancel; reopen and confirm $2,000. Repeat with window close. Save an intentional edit and confirm revision/outdated behavior.
3. Add a premium, choose each existing insured direction, test missing insured/start, invalid dates/amounts, and a single-person plan. Verify no spouse option for single plans.
4. Switch premium to ordinary and back; verify no hidden mortality or volatility policy is committed. Remove a test premium deliberately.
5. Save/Save As, restart and reload. Swap Current/Baseline both directions and confirm frozen originals remain distinct.
6. Run the fixture projections and verify annual numbers in section 19, including ordinary survivor reduction versus full contractual premium.
7. Confirm the independent $400,000 death benefit remains qualified/excluded, payable once in 2035, with no death-year premium or direct brokerage deposit.
8. Run small integrated/weighted and generated standalone/paired fixed/longevity analyses, including positive ordinary volatility and differing Current/Baseline premiums.
9. Inspect history after amount/insured/date edits; original numerical results/definitions must remain available, marked outdated where appropriate. Retry Save after a storage failure must not rerun analysis.
10. Check Light/Dark, 900×650 and 1180×820, Windows DPI, scrolling, footer visibility, tab traversal, modal ownership, accessible names, Save/Cancel/Close and Run/Cancel feedback.
11. View/Save current/historical PDFs; inspect original premium and death-benefit assumptions, charts, tables and pagination. Cancel export and verify no unintended saved file; confirm configured export directory.

Native acceptance remains requested, not completed. Do not proceed to Phase 23D without a separate authorization.

## 21. Exact changed files

Paths below are relative to `src/main/java/com/daviddunn/retirementplanner/` unless otherwise stated.

Modified existing production files (27):

```text
app/export/ProjectionPdfExporter.java
app/montecarlo/MonteCarloAnalyzer.java
app/montecarlo/MonteCarloFingerprintService.java
app/montecarlo/MonteCarloSnapshot.java
app/montecarlo/MonteCarloStrategyComparisonAnalyzer.java
app/projection/CompleteProjectionSnapshot.java
app/projection/ConditionalBenefitDisclosures.java
app/projection/ConditionalBenefitExecutionSemantics.java
app/projection/DeterministicExpenseInputs.java
app/socialsecurity/LongevityWeightedContinuationEvaluator.java
app/socialsecurity/LongevityWeightedIntegratedStrategyComparisonService.java
app/socialsecurity/results/AcceptedAnalysisCompletion.java
app/socialsecurity/results/AnalysisFingerprintService.java
app/socialsecurity/results/ConditionalBenefitAnalysisExecution.java
domain/benefit/ConditionalBenefitAdmission.java
domain/financial/Expense.java
domain/financial/ExpenseDeserializer.java
domain/financial/ExpenseType.java
domain/model/Household.java
domain/projection/ProjectionEngine.java
domain/projection/ProjectionExpensePath.java
ui/controller/ApplicationController.java
ui/dialogs/ExpenseDialog.java
ui/socialsecurity/FrozenConditionalBenefitDetails.java
ui/socialsecurity/IndividualReportContext.java
ui/socialsecurity/SocialSecurityAnalyzerInputSummary.java
ui/views/ExpensesView.java
```

New production files (4):

```text
app/projection/ContractualPremiumDisclosures.java
app/projection/ContractualPremiumExecutionSemantics.java
domain/financial/ContractualPremiumPolicy.java
domain/financial/ContractualPremiums.java
```

New tests under `src/test/java/com/daviddunn/retirementplanner/` (8):

```text
app/montecarlo/ContractualPremiumMonteCarloTest.java
app/socialsecurity/ContractualPremiumIntegratedTest.java
domain/financial/ContractualPremiumDomainTest.java
domain/projection/ContractualPremiumFinancialTest.java
domain/projection/ContractualPremiumHistoryTest.java
ui/dialogs/ContractualPremiumDialogTest.java
ui/montecarlo/ContractualPremiumHistoryTest.java
ui/socialsecurity/ContractualPremiumAnalyzerContractTest.java
```

New reader evidence under `src/test/resources/phase23c/legacy-expense/`: `Expense.java.txt`, `ExpenseDeserializer.java.txt`, `ExpenseType.java.txt`, `GrowthCategory.java.txt`. Documentation: this report and the separate Checkpoint A report. Generated logs, screenshots, PDFs, manifests and native fixtures remain ignored under `target/phase23c/`.

## 22. Git state and preservation

Initial branch/HEAD recorded above; index and working tree were clean. Starting tracked-file hashes are retained in `target/phase23c/starting-hashes.json`. Of **1,510 original tracked files**, **1,483 are byte-for-byte unchanged** and **27 have the authorized production changes listed above**; none are missing or unexpectedly modified. There are 18 new files: four production classes, eight test classes, four legacy-reader evidence files and two reports. No preexisting test, resource, report or build configuration changed.

The final regression source set was frozen in `release-source-hashes.json`; all 1,291 production/test/resource/pom entries match after regression. The first attempt's source manifest is retained separately. All six synthetic native plan files match their earlier hashes and were not overwritten on subsequent tests. All four legacy-reader source fixtures exactly match the original accepted source bytes. `git diff --check` and new-untracked-file whitespace checks pass. Branch/HEAD remain unchanged, and the index is empty. Evidence: `preservation-summary.json`, `final-release-verification.json`, `legacy-reader-evidence.json`, `native-fixture-hashes.json`, `untracked-whitespace.txt` and `final-status.txt` under target/phase23c.

No staging, commit, push, reset, clean, revert or history operation was performed. No real retirement plan was accessed. Accepted Phase 23B and prior work is preserved except for the explicitly listed additive premium integration points. No old test, golden resource, build configuration, random generator, benefit evaluator or tax calculator was changed.

## 23. Remaining modeling limitations and resource observations

This is fixed nominal annualized expense planning, not carrier billing. No medical-age repricing, schedules of insurer increases, refunds, premium deduction, owner/payer ledger, policy cash value, loan, surrender, policy linkage or contractual guarantee is inferred. Multiple dated definitions can represent separate obligations; users remain responsible for any intentional overlap.

Premium-bearing weighted workloads use the slower full reference route. A bounded 64-trial, ten-year couple fixed run with ordinary expense volatility, a premium and excluded principal took **78,743,900 ns (about 0.079 seconds)** and allocated **155,242,240 bytes on the calling thread** in the final complete suite. Evidence: `target/phase23c/bounded-performance.json`. These are warmed, small synthetic observations, not production-scale latency or memory guarantees. Allocation measures bytes allocated over time, not retained heap or a per-trial memory limit. Cancellation is checked with the existing cancellation exception contract; existing scenario-boundary cancellation/progress tests also pass.

Annual evaluation remains bounded by expense occurrence/horizon limits. No event log, mutable paid flag, new stochastic draw, unbounded cache, whole-plan annual deep copy or serialization in the tax loop was introduced. Existing trial/document/numeric/cancellation/progress protections remain. Existing retained-cash investment-income and terminal-estate approximations are unchanged.

## 24. Deferred functionality and completion gate

Deferred: policy assets/cash value, premium-funded investments, automatically linked benefits, insurer schedules, annuity/MYGA taxation or accumulation, policy loans/surrender, external beneficiaries, estate/trust/final-death settlement, new insurance deductions, Medicare lookback changes and new state jurisdictions. Phase 23D has not begun.

Implementation, complete regression, preview inspection and preservation checks are complete. Recommend **native Windows acceptance using section 20 before any further phase**. No additional architectural approval was needed because the implemented contracts are additive and disjoint, without historical migration or stochastic-protocol changes. Native acceptance remains the user's gate; Phase 23D is not started or authorized by this report.
