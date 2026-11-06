# Phase 22B2 — Conditional Benefit Financial Integration

## Checkpoint A — selected contract before production edits

Branch main; HEAD 1bf90ad5c9d594015d75b5dbcc070e50d294954a. The index is empty. All 36 accepted B1 paths remain modified/untracked; their exact starting status and SHA-256 inventory are recorded under target/phase22b2. No accepted report or unrelated change will be overwritten. B0's authoritative pension selection and provenance are present.

### Annual evaluation and timing

Use one pure domain evaluator, immutable original DOB/opening/year/effective-death context, immutable annual totals and per-benefit receipts. No Person/Account/plan references, flags, RNG or live UI state. Resolve fixed dates, DOB.plusYears (Feb 29 to Feb 28), and effective January 1 named deaths. Coverage qualifies the event, independently of monthly termination. Living named recipients only.

Recurring recognition uses the actual eligible date interval intersected with projection opening and the current calendar year, then counts inclusive calendar months. Any overlapping partial month counts whole; an interval already terminated before opening has no overlap, even in the opening month. Monthly escalation starts in the first eligible payment year after not-before, never the opening year. Use BigDecimal DECIMAL128 exponentiation/multiplication and round the resulting annual receipt once to cents HALF_UP; this is a new benefit contract, not a change to legacy Pension arithmetic. Lump sums round once to cents in the event year, only if the event is within coverage and the modeled interval. Pre-opening lump sums never replay. Eligible zero-dollar receipts retain attribution.

### Authoritative cash and tax handoff

Evaluate once per year attempt from frozen inputs. The same detached receipt result supplies guaranteed cash and pre-conversion/final supplemental source attribution. Existing Medicare retries may repeat the pure evaluation from the original opening portfolio; no deposit occurs in evaluation. HouseholdCashSettlement alone commits genuinely unspent after-tax surplus to projected retained non-qualified assets. No direct Account deposit, contribution, synthetic negative expense or extra growth on new money.

Introduce distinct conditional recurring/lump income kinds. Add a runtime-only conditional ordinary-income field to TaxIncome with compatible constructors and JsonIgnore, so no persisted TaxIncome format changes. TaxIncomeAttribution attaches the selected taxable receipt amounts from the same supplied sources before every federal/state tax calculation, including tax funding and Roth bracket-fill trials. These receipts never enter pension, SS, IRA, conversion or interest buckets. RMD formulas and existing same-year IRMAA sequencing remain unchanged.

Michigan, Illinois and existing no-general-income-tax jurisdictions can consume this non-retirement ordinary income through their existing ordinary/AGI bases without retirement exclusions. Pennsylvania and Iowa source-classification paths require explicit rejection until their conditional-income mappings are validated; do not silently treat a new category as investment or retirement income. No new legal qualification or insurance exclusion is claimed.

### Compatible retained attribution and execution provenance

Preserve CompleteProjectionSnapshot V1, retained schemas 1/2, all existing envelope/MC payload/fingerprint versions, all legacy algorithm maps and pension-selection=death-selected-v2. No global version bump or incompatible shape is required.

Extend the existing retained Income attribution with one optional, omitted-when-absent conditional receipt fact. Preserve the old constructor, exact legacy serialization and strict required/unknown-field validation using a narrow known-optional-field reader, following the existing retained account/S3 compatibility pattern. New conditional source kinds require matching receipt metadata; old kinds cannot masquerade as conditional receipts. All original creator properties remain required. This is the smallest compatible optional attribution extension authorized by section 15, not a new snapshot version.

Use the existing complete-projection provenance string for an explicit deterministic conditional-benefit semantic and SHA-256 binding over original input digest plus retained annual evidence. B0 pension provenance remains separately present. A new enabled completion is independently checked against the pure evaluator before stamping; source IDs, original definitions, amounts, recipient, event/period and yearly source totals must agree. Historical loading validates strict capability/binding/metadata combinations without rerunning the evaluator. Legacy benefit-bearing artifacts lacking this capability remain readable but not current. Unknown or mismatched explicit capabilities are rejected. Empty-benefit completions retain their exact existing provenance and wire representation.

### Enabled versus blocked execution

Enable direct deterministic ProjectionEngine entry points (ordinary and isolated explicit lifetime-context tests), normal ApplicationController accepted projections, and direct baseline projection in the validated state subset. Keep every public full-plan integrated/quick/grid/exhaustive/weighted analyzer, opening second-death shortcut, all standalone/paired Monte Carlo APIs and MC artifact interpretation guarded for nonempty benefits until B3. SS-only calculations remain unaffected. Pure copies and fingerprints remain allowed. Admission is refined, not globally removed.

### Reporting and compatibility

Annual household income/taxes/withdrawals/assets use completed values. Add compact read-only conditional receipt facts to existing annual details and projection PDFs, differentiating monthly streams from lump sums and using frozen inputs/results. No new input controls or income-editor changes. Display and export must never call the evaluator. Empty plans receive neither extra financial arithmetic nor new report sections. Existing original numerical results, document digests, UUID associations, snapshots, fingerprints, historical exports, Phase 21 streams and B0 pension semantics remain unchanged.

This checkpoint selects no incompatible persistence contract and therefore does not require a further migration approval. If implementation disproves that compatibility, stop before expanding the contract.

## 1. Executive summary and supported execution matrix

Implemented the approved deterministic conditional-benefit payment foundation. One immutable annual receipt result supplies household cash and explicit fully taxable non-pension attribution. Existing pension, Social Security, RMD, Roth, tax-rate, Medicare and investment calculation rules are unchanged. No insurance product or editor was introduced.

| Entry point | Benefit-bearing B2 status |
|---|---|
| Ordinary deterministic projection, couple/single person | Supported for the validated state subset |
| Explicit deterministic baseline recalculation | Supported; independently frozen baseline inputs |
| Direct ProjectionEngine explicit lifetime context | Supported as a deterministic scenario/oracle; not a weighted-analysis capability |
| Normal controller acceptance, Save/Load/Save As/Swap | Supported with authenticated deterministic provenance |
| Historical viewing/export of original completed values | Preserved; never reruns an evaluator |
| SS-only calculations | Unchanged; do not consume full-plan conditional cash |
| Integrated individual/couple, Quick, grid/heat map, exhaustive | Blocked until B3 |
| Individual/couple weighted and continuation/opening-estate paths | Blocked until B3 |
| All eight standalone/paired Monte Carlo configurations | Blocked for benefit-bearing inputs until B3 |
| Pure input capture, scenario copies and fingerprints | Allowed; not execution admission |

Michigan, Illinois and the existing no-general-individual-income-tax jurisdictions are supported for the single B2 tax class. Pennsylvania and Iowa benefit-bearing execution is explicitly unsupported pending validated source mapping. Existing no-benefit plans in every previously supported state retain their existing behavior. Post-hoc metrics/comparisons consuming completed deterministic projection values need no separate payment arithmetic; no new strategy-search capability is enabled.

## 2. Selected annual evaluator design

`ConditionalBenefitEvaluator.evaluate` takes an immutable definition list and `BenefitEvaluationContext`: detached original dates of birth, immutable effective death view, projection opening and calendar year. `AnnualBenefitEvaluation` owns an immutable receipt list and gross/taxable/excluded totals. `ConditionalBenefitReceipt` freezes ID, label, category, original trigger, recipient, pattern, event/eligibility dates, recognized interval/month count, year, gross/taxable/excluded amounts, tax character and destination.

There are no paid flags, Person/Account/plan/UI references in a receipt/context, random draws, or mutable contract state. Independent identical contracts remain independent by UUID. The evaluator validates duplicate IDs and the existing 100-definition bound.

ProjectionEngine captures definitions and original DOBs once per run. It evaluates one receipt result per calendar year outside the Medicare retry. The exact same annual object is passed through the retry and supplies pre-conversion and final source attribution. Completion verification independently calls the same pure evaluator before accepting a new result; historical restoration never calls it.

## 3. Detailed timing and trigger semantics

- FIXED_DATE uses its configured calendar date.
- AGE uses original DOB.plusYears(age), including February 29 to February 28 in non-leap years. It is a DOB-derived calendar trigger, not a Social Security claiming age or a separate annuity qualification rule. Initial definitions require a living recipient; they do not add another unconfigured trigger-person survival condition.
- DEATH uses the shared effective household death view: January 1 of the selected death year. No independent death or insurance draw exists.
- Coverage qualifies the event inclusively. It does not terminate a recurring stream that validly began during coverage.
- Monthly eligibility is max(event, not-before), intersected with projection opening, the calendar year and optional end date. Any overlapping partial calendar month counts whole. An interval already terminated before opening is not replayed, including an earlier date in the same opening month.
- A pre-opening death may activate recurring survivor income. A missing spouse is never treated as deceased. A deceased recipient receives no monthly or lump payment, including both-deceased states under explicit contexts.

Legacy Pension date, month and COLA conventions were not changed.

## 4. Monthly escalation and lump-sum rules

Monthly gross is original monthly amount × (1 + configured decimal-fraction escalation)^(year − first eligible payment year) × recognized months. FIRST_ELIGIBLE_PAYMENT_YEAR includes the not-before constraint and can precede projection opening. Escalation never resets at opening or compounds the previous year's payment.

New benefit arithmetic uses BigDecimal DECIMAL128 for exponentiation/multiplication and rounds each benefit's final annual receipt once to cents HALF_UP. Amounts are never converted to floating point. This is the explicit new-type convention; existing Pension arithmetic remains intact. Separate contracts round separately and preserve multiplicity.

Lump sums round once to cents, require an event inside inclusive coverage and the modeled interval, and appear only in that event year. Pre-opening events do not replay. Receipt metadata requires a one-time event date, zero monthly count and fully reconciled gross/taxable amounts. Capability validation also rejects duplicate lump IDs across years or changing event dates across annual evidence.

## 5. Gross/taxable financial attribution

Added CONDITIONAL_RECURRING and CONDITIONAL_LUMP_SUM source kinds with immutable optional receipt metadata. Recipient ownership is explicit; pension participant facts, account source and Roth destination are absent. Runtime source constructors and retained DTO constructors reject missing/mismatched receipt facts, wrong recipient/year/pattern/amount and misplaced metadata.

TaxIncome now carries a runtime-only conditional ordinary-income amount, with compatible old constructors and JsonIgnore on the new getter. No persisted TaxIncome format changes. Its ordinary income includes the selected conditional taxable amount; Michigan retirement income does not.

TaxIncomeAttribution uses the actual supplied receipt sources to set that separate amount before federal/state calculation in every tax-funding and Roth trial. B2's only tax class makes taxable equal gross and excluded zero. Receipts do not enter pension, Social Security, retirement withdrawal, conversion, or interest buckets.

## 6. Cash-source/use reconciliation

Selected gross receipts enter guaranteed household cash once. Existing cash-need/RMD/withdrawal decisions consume that cash. Existing HouseholdCashSettlement alone transfers genuinely unspent post-tax surplus into projected retained non-qualified assets at final year settlement. No live Account.deposit, synthetic expense, contribution or independent gross deposit was introduced.

Tests reconcile guaranteed income + projection-period RMD cash + spending withdrawal + tax-funding withdrawal against ordinary expenses + Medicare + total income taxes + retained surplus within two cents. A separate zero-opening-assets $200,000 lump oracle verifies zero receipt-year growth, exact after-tax retention, no repeated next-year receipt and use/growth of retained cash in the following year. Original plan/account JSON remains unchanged after execution.

## 7. Federal and supported-state tax integration

Federal AGI, taxable Social Security's ordinary-income base, taxable income, tax liability and marginal/bracket calculations consume the same selected non-pension amount through existing interfaces. No tax brackets, deductions or tax-law assumptions changed.

- Michigan includes conditional receipts in ordinary income, excludes them from retirement income/retirement eligibility, and applies no pension participant facts.
- Illinois includes them through its existing federal-AGI base and never classifies them as retirement distributions or Social Security subtractions.
- Existing no-general-income-tax adapters retain their established zero-tax model and limitations.
- Pennsylvania and Iowa reject the new source kinds explicitly; common deterministic admission explains the unvalidated tax classification before execution. No insurance exclusion, pension exemption or unverified ordinary-income fallback is invented.

The modeled fully taxable class is not a claim that actual life insurance death proceeds are taxable. Tax-excluded insurance, basis recovery and product-specific legal treatment remain outside B2.

## 8. Roth/RMD/IRMAA downstream verification

Independent living-recipient scheduled-payment controls exercise MI/IL/FL with spouse-owned $2 million traditional IRA, Roth destination, optional taxable assets, statutory opening RMD data, $250,000 spending and 22% bracket filling. Conditional survivor payments of $2,500/$15,000 monthly are compared with an ordinary living-recipient pension control whose existing state qualification is conservatively taxed.

Every annual BigDecimal getter is compared, except Michigan retirement income, which must intentionally differ because conditional income is not retirement income. Federal/state taxes, conversions, tax funding, RMDs, withdrawals, retained cash, investment balances, net worth and estate agree. Medicare recorded MAGI, premiums and IRMAA agree with the independent control. Both death directions, single-person date/age income and ordinary-versus-explicit-lifetime consistency are separately tested. The control establishes equivalent scheduled dollars; it is not used to misclassify production conditional income as pension.

No predetermined direction of every tax/conversion outcome is assumed. RMD statutory formulas, source eligibility, same-owner Roth rules and same-year IRMAA conventions are unchanged.

## 9. Selected provenance and compatibility contract

| Contract | B2 policy |
|---|---|
| Legacy CompleteProjectionSnapshot V1 | Original inputs, numerical values, digests and meaning preserved |
| Retained projection schemas 1/2 | Preserved; one known optional conditional receipt on existing Income DTO, omitted when absent |
| B0 pension-selection=death-selected-v2 | Preserved independently and required alongside new deterministic benefit provenance |
| New deterministic benefits | `conditional-benefits=deterministic-annual-v1; binding=<SHA-256>; ` plus original B0/projection provenance |
| Legacy benefit-bearing artifact without capability | Readable but not current; never promoted from its definition list alone |
| Unknown/mismatched explicit capability | Rejected |
| MC V1, read-only numeric V2, enabled standalone V2/V3, enabled paired V3/V4 | Unchanged; no benefit-enabled analyzer contract in B2 |

The binding covers the exact original input-document digest and retained annual evidence. It uses the existing artifact-integrity/digest/ownership model, not a new signing service. The constructor validates explicit capability, original definitions, IDs, recipient/year/amount metadata, modeled interval, one-time uniqueness, consistent events, supported state and cash/tax evidence. A B0 stamp alone cannot attest to conditional execution. New receipt-bearing graphs cannot masquerade as legacy projections. New analyzer receipt metadata under an analyzer payload is rejected until B3 supplies an approved family capability.

The optional Income reader requires all ten original creator properties, rejects unknown/duplicate keys and defaults only the known new optional receipt. It reads exact BigDecimal tree nodes, preserving decimal value and scale. No global missing-creator/unknown-field validation was relaxed. No snapshot/envelope/fingerprint version or legacy algorithm map was changed, and the Phase 19 compatibility handling remains intact.

## 10. Deterministic acceptance and historical restoration

Before stamping a new completion, the controller compares recorded per-year receipts with the authoritative evaluator on the exact frozen plan. An injected engine that omits an applicable payment fails before replacing accepted history. Existing plan/revision/epoch/input-document checks still gate publication. New full-plan analyzer associations are refused for benefit-bearing plans; SS-only associations retain their independent contract.

Tests prove enabled receipt facts, original definitions, capability, UUIDs/digests and annual values survive Save, Save As, restart and swaps in both directions. Restoration uses an engine that throws if called. Tampered inputs, missing/unknown capability and mismatched binding are rejected. Historical rendering validates evidence without evaluating payments; old results remain immutable and exportable under their original context.

## 11. Blocked analyzer matrix

All B1 caller guards remain on integrated strategy evaluators, Quick Comparison, claiming grids, complete exhaustive search, individual integrated mortality, both weighted evaluators/continuation/comparison services, opening second-death estate, standalone MC and paired MC. MonteCarloSnapshot validation/interpretation still refuses original requests containing benefits. Pure request/fingerprint capture remains valid.

The standalone fixed UI run service additionally blocks before generating its deterministic reference; longevity service blocks at entry. This closes the common-engine topology change introduced by supported B2 deterministic execution. Comparison service/core admission remains blocked. No market, mortality, inflation or expense stream changed. Existing progress/cancel/failure/last-success behavior is retained.

## 12. Historical and PDF reporting

ProjectionYear's existing total-income, federal/state tax, withdrawal, balance, net-worth and estate values reflect the completed financial stream. Existing income-source storage now retains separate monthly/lump receipt metadata. Yearly Details adds read-only received totals and wrapped recipient/payment facts only when current/baseline annual data contains receipts. Untriggered future definitions are not displayed as received income.

Projection PDFs show original conditional definitions separately from completed annual receipts, label monthly income versus one-time lump sums, disclose fully taxable non-pension treatment, and retain the existing charts/tables/workflow. New conditional money formatting stays BigDecimal; legacy formatting is untouched. Where old annual data has no receipt facts, the report states that fact and does not infer that no payment was due or that the old artifact executed benefits.

Current and historical PDFs have identical extracted text/page counts after live-plan edits; export performs no projection/evaluation. Zero-benefit reports omit the new section. Detailed conditional disclosures in specialized analyzer reports remain B3/later work; those benefit-bearing execution paths are not enabled.

Actual JavaFX screenshots at 900×650 and 1180×820 in Light/Dark and PDFBox-rendered pages were inspected under target/phase22b2/reporting. Receipt labels wrap, monthly/lump amounts and recipient facts are readable, and there is no new text overlap. The pre-existing wide comparison-grid minimum remains unchanged; native DPI/screen-reader behavior still requires acceptance. A 17-page enabled report preserves its original report sections and paginates the long new assumption/receipt block cleanly. Optional null dates are omitted from prose and lump sums are not described with recurring escalation.

## 13. Exact zero-benefit compatibility

The empty collection selects the original financial branch: no benefit arithmetic, expense draw or enabled provenance. The runtime conditional tax amount stays zero and is not added to legacy ordinary-income arithmetic. New retained metadata is omitted, preserving legacy wire data. B1's independent accepted-HEAD frozen-document/fingerprint oracle remains unchanged and passes. No existing financial golden resource was regenerated or edited. Full seeded/golden results will be recorded with the complete suite below.

## 14. Modified files

Paths relative to src/main/java/com/daviddunn/retirementplanner:

- New: domain/benefit/BenefitEvaluationContext.java, AnnualBenefitEvaluation.java, ConditionalBenefitReceipt.java, ConditionalBenefitEvaluator.java; app/projection/ConditionalBenefitExecutionSemantics.java.
- domain/benefit/ConditionalBenefitAdmission.java; domain/projection/ProjectionEngine.java; domain/baseline/BaselineProjectionService.java.
- domain/income/IncomeSourceAttribution.java; domain/tax/TaxIncome.java; domain/tax/TaxIncomeAttribution.java.
- domain/tax/state/PennsylvaniaProductionTaxCalculator.java; domain/tax/state/iowa/IowaTaxBaseBridge.java (explicit unsupported source classification only).
- app/projection/CompleteProjectionSnapshot.java; app/socialsecurity/results/RetainedProjectionSnapshot.java; persistence/AnalysisSnapshotCodec.java.
- ui/controller/ApplicationController.java; ui/MainWindow.java; ui/montecarlo/MonteCarloRunService.java; ui/summary/ProjectionYearDetailsPane.java; app/export/ProjectionPdfExporter.java.

Tests: new domain/benefit/ConditionalBenefitEvaluatorTest.java and domain/projection/ConditionalBenefitFinancialIntegrationTest.java / ConditionalBenefitReportingTest.java; extended persistence/ProjectionSnapshotPersistenceTest.java; refined B1 ConditionalBenefitFingerprintAdmissionTest / ConditionalBenefitPersistenceTest for intentionally enabled deterministic execution while retaining blocked analyzer assertions. This report is new. B2 affects 28 paths: 21 production, six test files and this report, including nine necessary overlaps with accepted B1 work. Exact inventories are under target/phase22b2.

## 15. Focused regression results and development findings

Regression level: **Level 3**, because financial execution, taxable income, immutable result evidence and historical compatibility change. Java 25, mandated IntelliJ Maven, local .codex-m2/repository and -DargLine=-Xmx4g; synthetic/temp plans only.

| Run under target/phase22b2 | Result |
|---|---|
| evaluator.log | 11 passed |
| attribution-compatibility.log | 60 total, 58 passed, two existing skips |
| before-engine-reproduction.log | Two expected B1-admission errors before engine integration |
| engine-integration.log | 25 passed, including B0 pension consistency |
| provenance-foundation.log | 44 total, 42 passed, two existing skips |
| downstream-oracles.log | Five synthetic DOB/SS-date fixture errors; zero financial assertion failures |
| focused-integration.log | 99 total, 85 passed, 14 snapshot-verification errors from the new reader's temporary floating-point tree |
| exact-attribution-recovery.log | 28 passed after exact-decimal reader correction |
| history-capability.log | 43 passed |
| reporting-focused.log | 112 total, one PDF whitespace assertion failure, two skips; wrapped text assertion corrected without changing required content |
| final-focused.log | 150 total, 148 passed, two existing skips |
| admission-final.log | 150 total, 148 passed, two existing skips; final caller/publication guards verified |
| reporting-final.log | Two passed; actual PDFs and four JavaFX previews generated |

The rich fixture initially changed a spouse DOB without rebuilding its zero-dollar SS election; that correctly hit the existing age-70 claiming guard. The fixture was corrected to use DOB+67. The snapshot reader issue was reproduced by existing permanent Save/Load/Swap tests and corrected locally by retaining BigDecimal tokens and scale; a new long-decimal strict-creator regression remains. Routine test-compile mistakes used two nonexistent getter names and were corrected to the existing API. No production financial rule, existing golden or admission guard was weakened to satisfy a mistaken test.

Counts overlap and are not cumulative unique tests. The focused selections include all B1 benefit/legacy contracts, new evaluator/financial/reporting tests, projection persistence, MC codec and supported standalone/paired expense suites.

## 16. Complete Maven regression

**BUILD SUCCESS, exit 0: 4,312 tests total, 4,266 passed, zero failures, zero errors, 46 unchanged existing skips.** Duration 14:01, completed November 6, 2026 at 12:03:42 Eastern. Evidence: target/phase22b2/full-regression.log and full-regression-exit.txt. This complete run followed the final production/test edit; subsequent edits are documentation only.

Exact PowerShell invocation:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-DargLine=-Xmx4g' test
```

No additional exclusions or permanent test skips were introduced. The 46 original skips remain across 33 suites: optional production capacity/performance proofs, selected large historical PDF/codec exercises, opt-in visual previews, explicitly disabled production mortality benchmark and audit.plan cases. No real-plan audit was enabled. Per-suite/case names and exact conditions are in target/phase22b2/normal-skips.json. B2's two reporting tests ran normally, including four actual JavaFX previews and rendered PDFs. Native DPI/screen-reader acceptance and opt-in production-scale exercises remain outside this normal suite.

## 17. Financial golden compatibility

No existing financial expectation or golden resource was changed. The complete normal suite passed the existing financial, B0 pension, Phase 21 seeded expense/random-stream, original snapshot, numeric compatibility, immutable association, export-directory and B1 fingerprint contracts. Conditional-income outcomes are new supported results backed by literal payment oracles, independent scheduled-payment financial controls, no-growth lump settlement and preserved original-plan JSON. No old numerical result or stored fingerprint was rewritten.

Resource review: at most 100 definitions, one bounded immutable annual receipt list per year, no retained per-scenario paths or unbounded cache, no per-year deep plan copy, no new random dimension, and existing document/raw/compressed/numeric/horizon limits remain enforced. Completion validation is proportional to completed years and receipt count. No production-scale performance guarantee is inferred from small fixtures.

## 18. Native Windows acceptance checklist

Use only the synthetic target/phase22b2/reporting/synthetic-benefits.json fixture or another isolated test plan. Fixture creation preserves an existing file rather than overwriting it.

- Open and explicitly run the ordinary projection; verify $0 conditional receipts in 2030, $30,000 monthly income plus one $400,000 lump sum in 2031, then $30,000/year, with actual taxes/Medicare/retained cash.
- Verify the future 2045 definition is not current income; Yearly Details separates monthly and lump facts and shows the spouse recipient.
- Save, Save As, restart and baseline/current swaps; verify original definitions, annual values and current applicability remain with their own projection.
- Edit current synthetic definitions and verify old history/PDFs preserve originals without recalculation. Verify failed execution preserves prior history.
- Check Light/Dark, 900×650 / 1180×820, native DPI, keyboard and screen-reader status/detail presentation.
- View/Save/Cancel projection PDF; check default export directory, original charts/tables, wrapped receipt disclosures and frozen historical context.
- Verify benefit-bearing integrated/weighted/MC/strategy runs are explicitly unavailable until B3, while SS-only remains independent.
- Verify PA/IA benefit-bearing plans receive the explicit unvalidated tax-classification explanation; do not change states merely to pretend unsupported taxation is modeled.
- Verify existing no-benefit pensions, SS, Roth, RMD, stochastic expense workflows and editor/navigation behavior remain unchanged.

## 19. Git working-tree preservation

Initial and final branch main, HEAD 1bf90ad5c9d594015d75b5dbcc070e50d294954a, empty index. All 36 pre-existing B1 paths were recorded in target/phase22b2/initial-status.txt and initial-hashes.json. Nine accepted B1 files necessarily overlap B2: admission, engine, baseline service, complete snapshot, controller/status handling and three tests. The other 27 B1 file hashes are unchanged, including the accepted B1 report; accepted 22A/B0 reports are also untouched. The final combined working tree has 55 paths, comprising the preserved B1 work plus B2 additions/overlaps. B2's own inventory has 28 paths as listed above. Expanded status and overlap inventory are in target/phase22b2/final-status.txt, b1-overlap-paths.txt and b2-files.txt.

No financial golden, plan schema version, build configuration, actual user plan or historical artifact was changed. Nothing was staged, committed, pushed, reset, cleaned or reverted. `git diff --check` passes. New untracked source/report text is also checked for trailing whitespace. Verification artifacts remain under ignored target/phase22b2.

## 20. Remaining Phase 22B3 dependencies

- Public integrated/Quick/exhaustive/weighted analyzers, continuation/equivalence optimizations, all eight MC modes and paired scenario results remain unsupported for benefits.
- B3 must prove scenario-specific deaths, lifetime coverage, shared-world behavior, source identity/freshness and new analyzer capability/provenance before enabling those workflows. Existing MC payload/fingerprint versions must not be repurposed.
- PA/IA conditional ordinary-income treatment needs validated supported mapping or continued explicit rejection.
- No editor, premium/funding, annuity basis/value, insurance exemption, estate/external recipient or product guarantee support was added.
- Native acceptance is pending. Historical capability validation is structural/digest validation, not historical payment recalculation or a new signing service.

Stop after B2 verification/reporting and request native Windows acceptance. No B3 work is authorized by this implementation.

**Recommendation:** Phase 22B2 is ready for native Windows acceptance within the explicit deterministic/state matrix. Obtain acceptance and separate B3 authorization before enabling any benefit-bearing integrated/weighted/Monte Carlo strategy workflow. The report does not declare Phase 22 complete.
