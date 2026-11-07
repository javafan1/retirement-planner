# Phase 23B — Qualified Life Insurance Death Receipts

## Checkpoint A — architecture and compatibility contract, before production edits

Branch main; HEAD 7db85b2e67066d73539fc68291e3df50d20730ee. Index empty. The pre-existing untracked Phase 23A report is preserved. Starting status, index, HEAD and SHA-256 hashes of every tracked file plus the audit report are recorded under ignored target/phase23b.

### Closed domain and qualification

Add QUALIFIED_LIFE_INSURANCE_DEATH_PRINCIPAL_EXCLUDED, restricted to DEATH_BENEFIT / ONE_TIME_LUMP_SUM / named DEATH / different living modeled recipient. Add an optional, omitted-when-absent closed qualification assertion PERSONAL_LIFE_PRINCIPAL_ONLY_V1. Its meaning is the user's acknowledgment of ordinary personal life-insurance principal only: no known transfer-for-value, reportable sale, employer-owned Section 101(j), special ownership/assignment/business exception, or settlement interest. The assertion is required for the new class, prohibited for legacy taxable definitions, and never inferred from a name/category. It is a planning assertion, not an application legal determination. One concise required UI acknowledgment presents these boundaries.

Old constructors and exact null-field omission preserve existing taxable plans and frozen documents. The strict local definition decoder accepts only the one new optional known property; unknown values/properties and incompatible combinations fail. Old readers already reject the new tax enum, preventing silent old-engine execution.

### Annual cash/tax contract

Reuse the authoritative pure evaluator and existing receipt fields. Gross = taxable + excluded; qualifying principal is gross / zero / gross. TaxCharacter is the explicit exclusion reason, bound to its qualified original definition. No negative deduction, pension/IRA/interest classification, new deposit or RNG. TaxIncomeAttribution already selects the receipt's taxable amount independently of gross cash. AnnualBenefitEvaluation.excluded must sum recorded excluded facts. Historical evidence checks must compare taxable totals, rather than gross proceeds, to AGI while retaining gross-cash reconciliation. No retirement, Medicare or settlement arithmetic changes are planned.

### Exact version matrix

| Inputs / family | Deterministic semantic | Analyzer capability / algorithm | MC payload / stored fingerprint | Envelope |
|---|---|---|---|---|
| No benefits | Existing unchanged | Existing unchanged | Existing V1–V3 and fingerprint V1–V4 contracts | Existing V1 |
| Phase 22 fully taxable benefits only | deterministic-annual-v1 unchanged | Capability V1 / ANALYZER_ANNUAL_CONDITIONAL_BENEFITS_V1 / analyzer-annual-v1 | Standalone V4 / V5; paired V5 / V6 unchanged | V1 |
| At least one qualified excluded principal, deterministic | deterministic-life-principal-v2 | Not an analyzer stamp | Not applicable | CompleteProjectionSnapshot V1, retained annual schemas unchanged |
| At least one qualified excluded principal, integrated/weighted | Not a deterministic stamp substitute | Capability V2 / ANALYZER_CONDITIONAL_LIFE_PRINCIPAL_V2 / analyzer-life-principal-v2 | Existing non-MC family payload and fingerprint envelope version; explicit new capability/tag required | V1 |
| At least one qualified excluded principal, generated standalone MC | Not applicable | Same V2 capability, original family/world protocol | New payload V6 / new fingerprint V7 | V1 |
| Either candidate has qualified excluded principal, generated paired MC | Not applicable | V2 capability binding both original candidates independently, including taxable/disabled side | New payload V7 / new fingerprint V8 | V1 |

Capability V1 must reject excluded originals; V2 requires at least one excluded original. Existing expense capabilities compose unchanged. Exact family/payload/fingerprint/algorithm combinations are validated; neither an old all-taxable stamp nor a new stamp on all-taxable inputs is valid. No global algorithm map bump. B0 pension-selection=death-selected-v2 remains separate. Existing financial normalization remains byte-identical for taxable/empty inputs; an explicit life-principal input sub-contract is used only when excluded definitions occur. Labels and opaque IDs remain excluded from financial identity.

All new fields are either existing receipt facts or an omitted optional plan definition assertion. No incompatible snapshot migration, legacy reinterpretation or new annual result shape is selected. Historical validation uses original documents and saved evidence without evaluator/projection calls. No old artifact is rewritten. Strict decoder bounds, digest/association/ownership checks and atomic storage remain in force.

### Admission matrix

Enable the new supported tax subset through the existing ordinary/direct baseline deterministic engine, couple integrated individual/Quick/grid/complete search, individual integrated workflows, couple/single weighted reference paths, and all generated standalone/paired fixed/longevity MC families after exact capability validation. A single-person plan cannot hold this new other-spouse death-principal definition; its existing scheduled taxable benefits and all four generated modes remain unchanged. SS-only objectives unchanged. Supplied-world/path restrictions, weighted optimization guards, living-recipient and final-death horizon restrictions remain. No external beneficiary or terminal estate settlement.

Michigan/Illinois use the independently reviewed ordinary/federal-income base without an addback for this restricted principal; no pension exemption or municipal-interest treatment. Existing no-general-individual-income-tax models remain limited to their current tax scope. PA/IA remain blocked for benefit-bearing execution; no new legal mapping is claimed merely because federal taxable amount is zero.

### Authoritative legal references and model boundary

Federal exclusion and transfer/sale/employer boundaries: [26 USC 101, text through October 20, 2026](https://uscode.house.gov/us/usc/119-134/t26/s101/) and [IRS life-insurance FAQ](https://www.irs.gov/faqs/interest-dividends-other-types-of-income/life-insurance-disability-insurance-proceeds). Death principal is distinguished from taxable interest; no exception calculations are implemented.

Michigan base and adjustments: [2025 MI-1040 instructions](https://www.michigan.gov/-/media/Project/Websites/taxes/Forms/IIT/TY2025/MI-1040-Book.pdf), income return/Schedule 1, not the separate household-resources credit worksheet. Illinois AGI and listed additions: [2025 IL-1040 Step 2](https://tax.illinois.gov/forms/incometax/currentyear/individual/il-1040-instr/step-2---income.html) and [Schedule M instructions](https://tax.illinois.gov/content/dam/soi/en/web/tax/forms/incometax/documents/currentyear/individual/il-1040-schedule-m-instr.pdf). The supported personal death-principal mapping uses exclusion from the income base, not a newly invented deduction. Existing state simplifications remain.

Retained cash growth is not automatically realized investment income in the existing engine. No interest amount or exempt yield is fabricated. Settlement interest is outside this principal-only feature and must not be included in it. UI/reports disclose that future earnings may be taxable. No brokerage-tax overhaul, premiums or asset/policy accounting is authorized.

### Verification plan and checkpoint decision

Add focused domain/evaluator and strict wire/version tests before modifying financial selection. Then validate literal cash/tax/AGI/MAGI oracles, supported-state controls, Roth/RMD funding, all generated modes and candidate independence; original taxable goldens unchanged. Verify history/codec, UI transactions, current/historical rendered PDFs and actual JavaFX Light/Dark scenes, then the full normal Java 25 Maven suite with local cache and -DargLine=-Xmx4g. Keep existing skips and no additional exclusions.

The selected extension uses compatible explicit opt-in contracts; no architecture stop condition is presently established. Proceed within Phase 23B only. Implementation evidence, final modified-file inventory, test totals and native acceptance fixture will be appended after verification. This checkpoint is not a completion or native acceptance claim.

## 1. Executive summary

Implemented restricted qualifying personal life-insurance death principal through the existing conditional-benefit system. An explicitly qualified one-time death benefit supplies gross household cash and excluded principal, with zero taxable conditional income. Existing taxable definitions, empty plans, investment/mortality/expense streams, pension semantics and historical numerical results retain their original contracts.

No insurance-policy asset, premiums, purchase funding, cash value, special ownership tax calculations, settlement-interest calculation or new beneficiary destination was added. Supported states remain MI, IL and the existing no-general-individual-income-tax models. PA/IA remain explicitly unsupported for benefit-bearing execution. Native Windows acceptance remains required.

Final normal regression: **4,507 tests, 4,461 passed, zero failures/errors, 46 unchanged existing skips across 506 suites**. Sixty-six new tests were added; no financial goldens or existing test sources were changed. Final source, history, synthetic fixtures and whitespace checks passed.

## 2. Source architecture audit

Reviewed Phase 23A and Phase 22A/B0/B1/B2/B3/B4 against the current implementation. Important integration findings:

| Component | Verified contract and implementation decision |
|---|---|
| ConditionalBenefitDefinition / local deserializer / ConditionalBenefits | Closed immutable definitions, exact role/numeric/date bounds and household validation; add one optional known qualification assertion with legacy omission |
| ConditionalBenefitEvaluator / Receipt / AnnualBenefitEvaluation | Existing event selection and rounding remain authoritative. Extend only the final gross/taxable/excluded split and excluded-total accessor |
| IncomeSourceAttribution / TaxIncomeAttribution.attach | Exact gross and modeled taxable receipt facts already travel separately, retaining recipient/year/identity without account or pension facts. No new income bucket or negative deduction needed |
| TaxIncome / TaxIncomeCalculator / TaxFundingCalculator | Existing conditional ordinary-income aggregate uses supplied taxable amounts. A zero taxable component does not enter pension, SS, IRA, conversion or interest totals |
| ProjectionEngine / HouseholdCashSettlement | Gross receipts already enter guaranteed household cash; original annual source facts flow into bracket fill and final tax funding. Final settlement alone retains unspent surplus |
| Federal/state tax / Roth bracket fill / Medicare | Existing taxable-income and AGI interfaces consume the authoritative source selection; no tax-law, RMD, Roth eligibility, Medicare lookback or settlement formula changes |
| Deterministic execution semantics | Original evidence validator bounded gross receipts by AGI; excluded principal instead requires gross bounded by cash and taxable receipts bounded by AGI. New semantic binds original inputs and retained evidence |
| Analyzer capability / AnalysisVersions / compatibility | V1 authenticates taxable-only inputs; V2 requires an excluded original. Exact version/model/algorithm/family and original candidate bindings remain mandatory |
| MonteCarloSnapshot / mapping / fingerprints / codec | New payloads V6/V7 and stored fingerprints V7/V8; old versions unchanged. Envelope/retained schemas need no migration or global algorithm bump |
| Editor/list / frozen detail / PDF adapters | Existing detached draft, required-marker and fixed-footer patterns retained. The list's definition-comparison reconstruction also had to preserve qualification |

Inspected the current codec, complete/retained snapshots, controller acceptance, scenario copies, historical adapters and generated MC entry points. Existing strict parsing, resource limits, source-revision acceptance, ownership, associations and atomic-write machinery are reused. Production files changed are listed in section 22.

## 3. Legal/model qualification boundaries

The user explicitly asserts ordinary personal life-insurance principal paid because the insured died. The closed assertion means no known transfer-for-value limitation, reportable policy sale, employer-owned Section 101(j) case, unresolved special ownership/assignment/business exception, or included settlement interest. Unknown or incompatible assertion values fail decoding. There is no automatic inference from a benefit name, category or death event.

This is a supported planning assumption, not an automated legal qualification decision. Section 101 supplies the general death-payment exclusion and exceptions; the IRS separately identifies taxable interest. The authoritative links at Checkpoint A establish this restricted scope. Neither trusts/estates nor exception calculations are supported. Income-tax exclusion is not a finding that proceeds escape estate inclusion.

## 4. New tax-character contract

`QUALIFIED_LIFE_INSURANCE_DEATH_PRINCIPAL_EXCLUDED` requires `DEATH_BENEFIT`, `ONE_TIME_LUMP_SUM`, `DEATH` of a named modeled person, a different modeled recipient, and `PERSONAL_LIFE_PRINCIPAL_ONLY_V1`. The recipient must exist and be living under the effective death state when payment is recognized.

The old constructor continues to create only its supplied taxable definition with null qualification. Taxable definitions prohibit a life-insurance qualification assertion. Serialization omits that new property when absent; no old plan or frozen document acquires it automatically. Unknown fields/enum values and invalid combinations remain rejected by the local strict reader.

## 5. Annual receipt facts

The immutable receipt already records original UUID/label/category/trigger, recipient, pattern, event/eligibility/payment dates, year, gross/taxable/excluded amounts, tax character and destination. The tax character explicitly identifies the exclusion reason; the original qualified definition and result capability authenticate the assumption. No extra receipt schema or per-scenario policy history is introduced.

Selection still uses frozen DOBs and EffectiveHouseholdDeathView, January 1 deaths, inclusive coverage, living recipients, the original projection opening, DECIMAL128 internal arithmetic and one HALF_UP rounding to annual cents. No replay, mutable paid flag, separate mortality event or random-number draw.

## 6. Gross/taxable/excluded reconciliation

For the restricted $400,000 event the known answer is gross 400,000.00 / taxable 0 / excluded 400,000.00. For a legacy taxable receipt it remains gross / gross / zero. Closed receipt validation enforces these exact alternatives, which imply gross = taxable + excluded without unnecessary decimal expansion during validation.

Annual excluded totals now sum recorded excluded amounts. Repeated identical evaluation returns identical facts. Death outside coverage, no death, dead beneficiary, both deceased or a pre-opening event produces no qualifying lump sum. Reverse insured/recipient direction and rounding at 400,000.005 are tested.

## 7. Federal income tax integration

No federal bracket, deduction, Social Security tax or funding algorithm changed. IncomeSourceAttribution retains a conditional lump source, not a pension, distribution, conversion or interest source. TaxIncomeAttribution selects its zero taxable amount. Consequently the principal has no direct AGI, federal taxable-income, taxable-Social-Security or tax-exempt-interest addback effect.

Independent tests include literal zero-other-income facts and a positive-Social-Security control. Changes in cash availability can still change taxable withdrawals, Roth execution and final taxes. The implementation does not assert that all taxes stay constant when an excluded receipt is introduced.

## 8. State-tax admission matrix

| Jurisdiction | Phase 23B admission | Verified mapping / limitation |
|---|---|---|
| Michigan | Supported restricted principal | Source's taxable ordinary amount is zero; existing ordinary-income base does not add this principal back. No retirement deduction or pension participant facts are assigned. Reviewed MI-1040 income/Schedule 1 instructions; household-resource credit worksheets are a different calculation |
| Illinois | Supported restricted principal | Existing federal-AGI base with explicit retirement/SS adjustments does not add qualified death principal as municipal interest or pension. Reviewed IL-1040 and Schedule M additions |
| AK, FL, NV, NH, SD, TN, TX, WY | Existing no-general-individual-income-tax models | Use the established modeled scope. No claim to calculate every insurance levy or state estate/inheritance tax |
| Pennsylvania | Blocked | Current source-classification/conditional admission remains unextended; federal exclusion alone is not a new PA execution capability |
| Iowa | Blocked | Current bridge/conditional admission remains unextended; no automatic expansion of taxable or excluded conditional classes |

Tests verify MI/IL/FL/TX literal controls, richer MI/IL/FL Roth/RMD/tax controls and explicit PA/IA failure. Existing normal suite covers the no-general-income-tax registry. No selected state changes automatically.

Additional official sources reviewed for boundaries: [Pennsylvania income guide](https://www.pa.gov/agencies/revenue/forms-and-publications/pa-personal-income-tax-guide/gross-compensation) and [Iowa expanded instructions](https://revenue.iowa.gov/taxes/tax-guidance/individual-income-tax/1040-expanded-instructions/note-additional-information). These do not establish a newly implemented calculator mapping; PA/IA remain blocked. No new state jurisdiction or insurance exception was implemented.

## 9. Medicare IRMAA handling

The principal does not enter the application's modeled same-year Medicare MAGI, which currently uses federal AGI. Literal and positive-SS controls compare MAGI and Part B/D amounts against the no-principal plan. Rich funding controls compare Medicare alongside conversions and tax-funded withdrawals.

No municipal-interest classification, additional MAGI category or two-year lookback was introduced. Actual IRMAA MAGI differs from an unlimited generic concept of tax-free cash; see [SSA POMS HI 01101.010](https://secure.ssa.gov/POMS.nsf/lnx/0601101010). Future taxable activity and conversion/withdrawal changes can affect Medicare independently of the excluded principal.

## 10. Roth, RMD and cash-funding effects

The existing bracket-fill and tax-funding loops receive the same conditional sources as final taxation. An independent zero-return control funds the plan with $400,000 additional nonqualified opening cash, with no conditional receipt, then compares AGI, total taxes, RMD, actual Roth conversion, Medicare, ending assets and after-tax estate. It matches the new receipt path in MI/IL/FL under the tested timing assumptions.

Existing statutory IRA RMD distributions remain positive in those controls: excluded cash never counts as an IRA distribution satisfying the requirement. Pure principal-only fixtures create no RMD or retirement account balance. No Roth account eligibility, owner ordering or RMD formula changed.

## 11. Retained household asset treatment

Gross receipt enters household funds once through the pre-existing engine path. Final HouseholdCashSettlement funds expenses/Medicare/taxes and retains only actual surplus. No brokerage/IRA/Roth deposit, negative expense, independent reducer credit or extra growth was added.

Literal zero-growth tests reconcile cash sources and uses and ending assets = beginning assets + gross receipts − Medicare in the isolated no-other-spending/tax fixture. Persisted brokerage balance stays $500,000. At a separately tested 10% return, the incremental ending wealth is $400,000 in the receipt year and $440,000 the next year; receipt cash receives no artificial extra receipt-year growth. These are controlled test oracles, not return predictions.

## 12. Future taxable-growth limitation

The existing engine does not automatically recognize every retained-cash growth amount as taxable interest or realized gain. This phase preserves that limitation and discloses it. Excluding death principal does not grant an exclusion to future yield.

Settlement interest is not implemented in this principal-only feature, is excluded from the qualification assertion and cannot be supplied as a new unknown settlementInterest definition field. Do not bundle it into excluded principal. Separately supported financial inputs and future explicit taxable-interest work must retain their correct tax character; this phase supplies no automatic interest estimate or brokerage-tax overhaul.

## 13. Deterministic execution

Ordinary configured-death/direct baseline paths reuse ProjectionEngine and the accepted evaluator. Both insured directions, active coverage, no replay, living-recipient checks and explicit-lifetime equivalents are covered. Complete deterministic acceptance uses `deterministic-life-principal-v2` only when excluded definitions occur. Prior taxable inputs continue using `deterministic-annual-v1`.

New result acceptance verifies completed receipt facts before binding. Stored history validates original definitions, receipt metadata and reconciled annual evidence without running an evaluator. Taxable-only and empty plans retain original execution branches and data contracts.

## 14. Integrated and longevity-weighted execution

Verified integrated individual full-plan evaluation, Quick Comparison, a bounded complete search, claiming grid, weighted lifetime reference evaluation and continuation fallback using the same B2 engine. Weighted known-answer cases include qualifying first death, death before/outside coverage, simultaneous deaths and reverse death order. Scenario weights and prior-December-31 terminal estate remain unchanged.

No benefit arithmetic was added to SS-only rankings. Existing single-person date/age benefits and integrated/weighted tests continue unchanged; the new excluded other-spouse event is invalid in a single-person domain. Unproven benefit-bearing prefix/equivalence reuse stays blocked and uses the accepted reference path. No post-final-death event is moved into an earlier estate snapshot.

## 15. Eight Monte Carlo configurations

| Generated family | Couple | Single person |
|---|---|---|
| Standalone fixed | New excluded principal supported | Existing supported taxable benefits/no-benefit plans preserved; no other household beneficiary exists for a new death-principal definition |
| Standalone longevity | New excluded principal supported with existing sampled deaths | Same domain limitation; scheduled taxable benefits preserved |
| Paired fixed | Independent excluded/taxable/disabled candidates supported | Existing supported definitions preserved |
| Paired longevity | Independent excluded/taxable/disabled candidates supported | Existing supported definitions preserved |

New couple tests cover zero/positive expense volatility, repeat seeds, direct financial-world controls, Current-only/Baseline-only/identical/different proceeds, excluded-versus-taxable, coverage edits and reversed beneficiary direction. Existing all-eight taxable/generated tests remain in focused/full coverage. Fixed comparison fixtures explicitly match their configured death request; mortality comparisons use their established shared worlds.

Benefit-bearing caller-supplied worlds/paths remain unsupported. No public analyzer bypasses the qualification/state/capability guards merely because ProjectionEngine can compute a receipt.

## 16. Random-stream preservation

No generator, seed protocol, scenario/year identity, mortality table, investment/inflation draw, expense dimension 6/1 or exact-definition expense correspondence changed. Receipt evaluation is deterministic given frozen definitions and the already selected death context.

New tests compare economic/lifetime worlds with and without the benefit; existing Phase 21 random-vector, seeded financial and stochastic-expense contracts are included in normal regression. No benefit UUID/label is used to create random draws or copy Current assumptions into Baseline.

## 17. New capability/version matrix

The exact selected matrix at Checkpoint A is implemented: deterministic life-principal v2; analyzer capability version 2/model ANALYZER_CONDITIONAL_LIFE_PRINCIPAL_V2/algorithm analyzer-life-principal-v2; standalone MC payload V6/fingerprint V7; paired MC payload V7/fingerprint V8. Envelope V1 and retained annual schemas are unchanged.

Capability V1 rejects excluded originals, V2 requires at least one excluded original, and MC payload/fingerprint selection must match that capability and family. Optional existing expense capabilities compose unchanged. Non-MC family payload/envelope fingerprint numbering stays unchanged while its explicit execution capability/tag and life-principal financial-input sub-contract distinguish the new semantics.

Malformed original qualification, input-digest/candidate binding, swapped unequal candidates, wrong family/version, old MC payloads carrying new capabilities and old benefit fingerprint contracts applied to excluded inputs are rejected. Opaque names/IDs remain outside financial identity; exact original documents and digests retain them.

For retained principal receipts, original rounded gross amount, inclusive coverage and January 1 event facts are checked without evaluating a payment again. Corrupt amount attribution is rejected. Candidate count/role bounds are enforced before the capability rereads original documents.

## 18. Legacy historical compatibility

Legacy taxable benefit wire omission, fingerprints, numerical results and algorithm tags remain unchanged. No automatic migration or old-artifact rewriting. The new classification is explicit opt-in for today's definition only.

Tests calculate a taxable historical projection, then an excluded current projection using the same benefit identity, Save As, restart with a throwing engine, and swap both directions. Original annual graphs/UUIDs remain exact; original taxable AGI differs from the new excluded AGI as expected. An old stamp cannot authenticate new excluded evidence.

New actual MC UI completion tests cover the four applicable couple families, change today's benefit back to taxable, Save As and restart, then load original associated analyzer snapshots with a throwing engine. Original payload, run identity, fingerprint and excluded definition remain exact while the current projection is outdated. Existing B4 history tests exercise the opposite direction: old taxable history after current edits. Codec validation and reporting do not recalculate history.

## 19. Current/Baseline independence

Each ordered candidate retains its own frozen input document, financial definition, qualification and receipt meaning. A V2 paired capability can include a taxable or disabled other side but requires an excluded definition on at least one side. Changing Current does not copy its tax character or coverage into Baseline.

Identical financial candidates under shared worlds give zero estate differences. Unequal candidate swaps fail original-input validation. If both exact original documents are identical, swapping the document strings is not a distinguishable mutation; role ordering and family checks still apply.

## 20. UI input, qualification and validation

The existing dialog now has Tax Treatment choices. Fully Taxable Ordinary Non-Pension Income remains the default. The excluded choice appears only for an eligible DEATH_BENEFIT/death/lump/different modeled recipient configuration. It requires one concise qualification acknowledgment using the established required marker and help conventions.

Exploratory changes retain detached draft information, while an incompatible visible configuration uses the taxable choice and cannot persist an invalid excluded definition. Cancel/window close leave the plan unchanged. Editing preserves UUID and qualification; duplicate requires its own acknowledgment and new UUID. The list's scale-insensitive no-change comparison now preserves the qualification field.

New workflow tests verify unchanged qualified edits are no-ops, labels preserve normalized financial identity, amount/tax changes alter it, and the standard change callback is used. Original frozen labels/documents stay separate from financial applicability.

The new Current-result explanation explicitly names the life-principal fingerprint contract V7/V8. Its old fallback would otherwise have said temporary numeric V2 despite correct new stored metadata. The correction is presentation-only for this new capability; no fingerprint, state or legacy explanation was changed.

Actual scenes at 900×650 and 1180×820 in Light and Dark were generated and inspected. The qualification/disclosure text wraps; fixed Save/Cancel remain visible. Images: target/phase23b/previews/qualified-principal-{LIGHT,DARK}-{900,1180}.png. Native DPI, screen readers and system interaction remain acceptance tasks.

## 21. Current/historical PDF reporting

Projection assumptions distinguish each original tax character, the qualification assertion and future-earnings limitation. Completed annual facts separately show gross, taxable and excluded principal; untriggered definitions are not presented as received cash. Yearly Details no longer labels an excluded receipt fully taxable.

Integrated/MC report adapters consume frozen capability disclosures. Actual current and outdated historical PDFs were generated for all four applicable couple MC families, including independent Current/Baseline assumptions, charts and tables. An actual restored exhaustive integrated result was exported after live definitions were removed. Current/historical projection PDF text is identical when based on the same frozen result.

Inspected rendered projection and paired-history pages; disclosure paragraphs wrap and paginate, with original gross $400,000 / taxable $0 / excluded $400,000 visible. Existing View/Save/Cancel/default-directory workflow is reused without exporter orchestration changes. Reporting artifacts are under target/phase23b/reporting. No report calls the evaluator or projection engine to recreate history.

## 22. Exact modified files

Production files (23):

- [src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java](src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java)
- [src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloFingerprintService.java](src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloFingerprintService.java)
- [src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshot.java)
- [src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshotMapping.java](src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshotMapping.java)
- [src/main/java/com/daviddunn/retirementplanner/app/projection/ConditionalBenefitDisclosures.java](src/main/java/com/daviddunn/retirementplanner/app/projection/ConditionalBenefitDisclosures.java)
- [src/main/java/com/daviddunn/retirementplanner/app/projection/ConditionalBenefitExecutionSemantics.java](src/main/java/com/daviddunn/retirementplanner/app/projection/ConditionalBenefitExecutionSemantics.java)
- [src/main/java/com/daviddunn/retirementplanner/app/projection/ConditionalBenefitFinancialInputs.java](src/main/java/com/daviddunn/retirementplanner/app/projection/ConditionalBenefitFinancialInputs.java)
- [src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AcceptedAnalysisCompletion.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AcceptedAnalysisCompletion.java)
- [src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisCompatibilityPolicy.java)
- [src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisVersions.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisVersions.java)
- [src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ConditionalBenefitAnalysisExecution.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ConditionalBenefitAnalysisExecution.java)
- [src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ConditionalBenefitAnalyzerValidation.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ConditionalBenefitAnalyzerValidation.java)
- [src/main/java/com/daviddunn/retirementplanner/domain/benefit/AnnualBenefitEvaluation.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/AnnualBenefitEvaluation.java)
- [src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitDefinition.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitDefinition.java)
- [src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitDefinitionDeserializer.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitDefinitionDeserializer.java)
- [src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitEvaluator.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitEvaluator.java)
- [src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitReceipt.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitReceipt.java)
- [src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefits.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefits.java)
- [src/main/java/com/daviddunn/retirementplanner/ui/benefits/ConditionalBenefitPresentation.java](src/main/java/com/daviddunn/retirementplanner/ui/benefits/ConditionalBenefitPresentation.java)
- [src/main/java/com/daviddunn/retirementplanner/ui/dialogs/ConditionalBenefitDialog.java](src/main/java/com/daviddunn/retirementplanner/ui/dialogs/ConditionalBenefitDialog.java)
- [src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/FrozenConditionalBenefitDetails.java](src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/FrozenConditionalBenefitDetails.java)
- [src/main/java/com/daviddunn/retirementplanner/ui/summary/ProjectionYearDetailsPane.java](src/main/java/com/daviddunn/retirementplanner/ui/summary/ProjectionYearDetailsPane.java)
- [src/main/java/com/daviddunn/retirementplanner/ui/views/ConditionalBenefitsPane.java](src/main/java/com/daviddunn/retirementplanner/ui/views/ConditionalBenefitsPane.java)

New focused tests (9; existing test sources unchanged):

- [src/test/java/com/daviddunn/retirementplanner/app/montecarlo/LifeInsuranceMonteCarloTest.java](src/test/java/com/daviddunn/retirementplanner/app/montecarlo/LifeInsuranceMonteCarloTest.java)
- [src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/LifeInsuranceIntegratedTest.java](src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/LifeInsuranceIntegratedTest.java)
- [src/test/java/com/daviddunn/retirementplanner/domain/benefit/LifeInsurancePrincipalTest.java](src/test/java/com/daviddunn/retirementplanner/domain/benefit/LifeInsurancePrincipalTest.java)
- [src/test/java/com/daviddunn/retirementplanner/domain/projection/LifeInsuranceFinancialTest.java](src/test/java/com/daviddunn/retirementplanner/domain/projection/LifeInsuranceFinancialTest.java)
- [src/test/java/com/daviddunn/retirementplanner/domain/projection/LifeInsuranceReportingHistoryTest.java](src/test/java/com/daviddunn/retirementplanner/domain/projection/LifeInsuranceReportingHistoryTest.java)
- [src/test/java/com/daviddunn/retirementplanner/ui/dialogs/LifeInsuranceDialogTest.java](src/test/java/com/daviddunn/retirementplanner/ui/dialogs/LifeInsuranceDialogTest.java)
- [src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/LifeInsuranceHistoryTest.java](src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/LifeInsuranceHistoryTest.java)
- [src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/LifeInsuranceAnalyzerContractTest.java](src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/LifeInsuranceAnalyzerContractTest.java)
- [src/test/java/com/daviddunn/retirementplanner/ui/views/LifeInsuranceWorkflowTest.java](src/test/java/com/daviddunn/retirementplanner/ui/views/LifeInsuranceWorkflowTest.java)

New report: PHASE-23B-QUALIFIED-LIFE-INSURANCE-DEATH-RECEIPTS.md. Ignored logs, scenes, PDFs, synthetic fixtures and the standalone native-fixture preparation source are under target/phase23b; they are verification artifacts, not production/build changes.

## 23. Focused test results

| Log under target/phase23b | Evidence |
|---|---|
| red-receipt.log | Permanent regression failed before production changes: the old reader rejects the new qualification property |
| foundation.log | 201 passed after the first foundation integration, zero failures/errors/skips |
| financial-focused.log | Intermediate fixture failures: fixed comparison death context and zero-SS claim dates did not match the test's modified demographics; corrected the synthetic inputs, not financial rules |
| life-focused.log / final-life-focused.log | Intermediate missing Save between swaps and whitespace-sensitive PDF phrase assertion corrected in new tests; preserved exact original/historical text equality |
| verified-focused.log | 262 passed, zero failures/errors/skips: new LifeInsurance tests plus existing ConditionalBenefit tests |
| final-receipt-check.log | 26 passed, zero failures/errors/skips after the numeric-validation cleanup |
| final-focused.log | 266 passed, zero failures/errors/skips after final original-receipt/resource guards, explicit excluded-capability failed-save/Retry Save and mixed taxable/excluded-income controls |
| release-focused.log | 117 passed, zero failures/errors/skips: the corrected Current-status explanation, financial fingerprints, capabilities, new MC execution and actual history workflows |
| native-fixture-verification.log | Production repository load and ordinary ProjectionEngine verify both separately created native fixtures' $400,000 cash and $0 versus $400,000 AGI |

Counts overlap and are not cumulative unique coverage. No existing test source, golden expectation, opt-in condition or build configuration was changed. A final numeric-validation cleanup enforces the closed gross/taxable/excluded alternatives without an unnecessary BigDecimal addition; the full normal suite uses that final source.

There are 66 new normal tests across nine classes. Literal cash/AGI/MAGI, finite rounding, original-definition receipt checks, zero-return funded controls, mixed taxable/excluded receipts, all applicable generated modes, strict capability round trips, actual UI completion/restart, failed storage/Retry Save, themed layouts and frozen PDFs passed.

## 24. Full Maven regression totals

The first complete normal checkpoint passed: 4,503 tests, 4,457 passed, zero failures/errors and 46 existing skips; 13:39, completed November 6, 2026 at 19:28:23 Eastern (full-regression.log). Final review added one excluded-capability Retry Save test, three mixed-income state controls, and tightened structural resource/receipt validation.

The next complete normal checkpoint also passed: 4,507 tests, 4,461 passed, zero failures/errors and 46 existing skips; 14:23, completed at 19:44:21 Eastern (final-full-regression.log). The remaining change was the new-capability Current-status wording described in section 20. Its 117 affected checks passed.

**Final complete normal run: BUILD SUCCESS, exit 0; 4,507 tests, 4,461 passed, zero failures, zero errors, 46 existing skips; 506 suites.** Duration 14:52, completed November 6, 2026 at 20:01:35 Eastern. Log: target/phase23b/release-full-regression.log. Independent XML totals: target/phase23b/full-suite-summary.json. All 32 modified/new source files still match the SHA-256 hashes captured before this run in release-source-hashes.json. Exact command:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' '-DargLine=-Xmx4g' test
```

Java: Temurin 25.0.4.1. No -Dtest selector or additional exclusions in the complete invocation. Financial, pension, Phase 21 seeded/random, legacy codec/fingerprint, historical association, UI and PDF tests remain part of normal verification.

Original financial/seeded golden expectations, legacy frozen wire/digests, B0 pension correction, B2 financial oracles and Phase 21 expense/return/mortality/inflation protocols passed unchanged. No golden fixture was regenerated. Expected injected filesystem/storage-failure traces and PDFBox overwrite notices concern isolated test artifacts; all corresponding tests passed. Existing JavaFX/JDK warnings were not suppressed by build changes.

## 25. Existing skipped-test count

The final XML count is 46: 42 pre-existing opt-in conditions and four Mississippi research-worksheet assumptions pending authoritative guidance (IRA-to-Roth before/after eligibility, employer-plan conversion and a contingent reduction). No new skipped or disabled test was introduced. The exact suite/test/reason inventory is target/phase23b/skip-inventory.json. Standard visual/benchmark/private-audit opt-ins remain unset, including state.tax.preview, weighted.persistence.preview, montecarlo.preview, complete.wizard.preview and audit.plan. No actual user plan was supplied for private-audit tests. Native acceptance is not replaced by automated previews.

## 26. Native Windows acceptance checklist

1. Open the qualified native fixture from section 27. Confirm Florida, primary/spouse, $500,000 brokerage and no pre-existing policy asset/premium.
2. In Income Sources → Conditional Benefits, inspect the $400,000 one-time PRIMARY-death/SPOUSE-recipient definition, coverage and excluded treatment.
3. Edit to $450,000 and Cancel/window-close; confirm $400,000, tax character, revision and accepted results unchanged. Reopen and save an intentional edit; confirm freshness invalidation.
4. Verify qualification acknowledgment, disabled/ineligible choice behavior, monthly/date/age restrictions, same-person recipient error and new-copy acknowledgment.
5. Run the original ordinary projection: 2030 conditional cash $0, 2031 gross $400,000, taxable $0, excluded $400,000; no replay in later years. Under this zero-other-income fixture AGI/MAGI remain zero. Medicare/other uses reduce the actual retained surplus.
6. Confirm live brokerage remains $500,000; projected retained household cash is separate and credited once. No RMD/contribution or extra receipt-year return.
7. Open the separate fully taxable native control: its 2031 conditional AGI is $400,000. Its classification must not be promoted because its name/category resembles insurance.
8. Save a taxable projection as Baseline, explicitly edit today's tax treatment/qualification, run the excluded projection, Save/Save As, restart and swap in both directions. Confirm original taxable and excluded receipts/assumptions remain distinct and exportable.
9. Run bounded Quick/grid/weighted and small generated fixed/longevity MC. Compare Current-only/Baseline-only and excluded-versus-taxable assumptions, with optional positive expense volatility. Keep unsupported supplied worlds blocked.
10. Exercise progress, cancellation, duplicate prevention, failed storage/Retry Save and prior-result retention using existing supported workflow checks; retry must not simulate again.
11. View/Save/Cancel current and historical PDFs using the configured default directory. Check gross/excluded/taxable facts, original qualification, future-earnings limitation, and charts/tables.
12. Inspect Light/Dark at 900×650 and 1180×820, Windows DPI scaling, keyboard focus/required markers and screen-reader descriptions. Inspect a PA/IA copy for explicit unsupported feedback without changing the state.

Native Windows acceptance has not been claimed. Phase 23C remains unauthorized.

## 27. Safe synthetic acceptance fixture paths

Recommended fixtures, generated without overwriting any existing file:

- `C:\Users\david\IdeaProjects\retirement-planner\target\phase23b\qualified-personal-life-native.json`
- `C:\Users\david\IdeaProjects\retirement-planner\target\phase23b\fully-taxable-native-control.json`

Both contain only synthetic Primary Test / Spouse Test, DOBs 1960-02-29 / 1962-06-15, explicit male/female mortality categories, Florida, one $500,000 Primary brokerage, zero quoted SS benefits/return/inflation, no ordinary premium/spending, 2030-01-01 opening with five years, PRIMARY death January 1, 2031 and SPOUSE alive. Coverage is inclusive 2030-01-01 through 2040-12-31. Each new native fixture has its own plan UUID and no embedded Baseline. The principal UUID/definition is preserved; qualification is present only in the excluded plan.

Original test-generated qualified-personal-life.json / fully-taxable-control.json are also preserved under target/phase23b; the qualified test fixture includes the original taxable Baseline. Native copies remove that Baseline and supply explicit mortality categories for convenient integrated/longevity acceptance. NativeFixturePreparation.java uses the production repository, refuses to overwrite existing destinations and verifies actual ordinary cash/AGI facts. No real plan or previously existing fixture was replaced.

## 28. Git state and preservation

Initial status: main at 7db85b2e67066d73539fc68291e3df50d20730ee, empty index; only pre-existing untracked PHASE-23A-GENERALIZED-INSURANCE-ANNUITY-ARCHITECTURE.md. Its exact hash and every initially tracked file hash were recorded before production edits.

Final checks: git diff --check passed, and separate checks found no trailing whitespace in new source/report files. Index empty; main/HEAD unchanged. All 32 tested source files match their final pre-run hashes. All four synthetic fixture hashes are unchanged. The 23 tracked modifications are the authorized production paths in section 22; all other 1,476 initially tracked paths plus the pre-existing Phase 23A report are byte-for-byte preserved. Nine test files and this report are new, untracked files. There are no unrelated modifications or new build/dependency/configuration files.

No staging, commit, push, reset, clean or revert was performed. All edits are confined to Phase 23B. Ignored logs/scenes/PDFs/fixtures remain under target/phase23b; no real plan or old historical artifact was edited.

## 29. Remaining exclusions and Phase 23C dependencies

No premiums or insured-death premium cessation, cash-value/loan/surrender accounting, annuity basis/exclusion ratios, MYGA, purchases/transfers, external beneficiaries, trusts/estates, post-final-death cash, accelerated benefits, employer/transfer/sale exception calculations, new state jurisdictions or Medicare lookback redesign. An ordinary personal qualification assertion is not legal advice or an insurer guarantee.

Retained investment-income realization remains a disclosed pre-existing approximation. Settlement interest is outside the principal-only feature. Benefit-bearing weighted work retains the accepted slower reference path and existing resource bounds; bounded synthetic runs do not establish production-scale performance guarantees. No new per-trial serialization, event history/cache or random stream was introduced.

Stop after full regression and this report; request native Windows acceptance before any further Phase 23 work.
