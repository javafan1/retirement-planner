# Phase 6D.3A.1 — Generic retirement participant facts and bounded state-tax unavailability

## Architecture decision

This implements the user's reduced architecture-gate decision. Pennsylvania is still disabled. The production jurisdiction enum, selectors, registry entries, state assumptions and tax-law data remain Michigan / Florida / Illinois only. No effective-rate override, basis subsystem or employment history is introduced.

The prior six-fact proposal is superseded. The following plan-document assertions were rejected and were **not implemented**:

- `writtenTermsCommunicated`
- `hasRetirementConditions`
- `providesLifetimeRecurringBenefits`
- `restrictsBenefitsUntilSeparation`

They are not replaced by a generic `qualified`, favorable legacy default, or state exemption flag. Participant facts alone do not establish an eligible legal retirement plan.

### Approved facts and ownership

| Fact | Natural owner | Generic usefulness | User input / derivation | Legacy default |
| --- | --- | --- | --- | --- |
| Separation status and optional separation event date | Employer account or pension arrangement | Participant service status, independent of any state exemption | Optional user assertion; never inferred from age, claiming, RMD or payments | UNKNOWN / no date |
| Retirement-condition status and optional met event date | Same arrangement | Participant satisfaction of arrangement conditions, not legal plan qualification | Optional user assertion; never inferred | UNKNOWN / no date |

`domain/model/RetirementParticipantFacts` is one immutable record containing two three-state enums and two optional nullable JSON event dates. The optional dates do not represent employment history. A non-null date requires the corresponding affirmative status. UNKNOWN or negative status plus a date is rejected, including during JSON loading. Affirmative status with no date is allowed but does not establish timing in a multi-year calculation.

`separationStatusOn(boundary)` and `retirementConditionsStatusOn(boundary)` evaluate the caller's modeled tax boundary. Before an explicit event the corresponding fact is negative; on and after the event it is affirmative. Undated affirmative status evaluates UNKNOWN. Negative planning assertions remain negative until changed to an explicit dated event; UNKNOWN remains UNKNOWN. No event is applied retroactively. These methods do not decide tax eligibility.

Account metadata is allowed on the authoritative employer account types currently represented: Traditional 401(k), Traditional 403(b), Roth 401(k). There is no modeled 457 type to invent. Non-employer accounts return UNKNOWN and omit the JSON group; assigning non-unknown participant facts to them is rejected. Pensions have the same optional immutable value. Existing domain edit patterns are preserved: the arrangement can replace the value, while attribution snapshots retain the original immutable value.

## Attribution

`IncomeSourceAttribution.AccountReference.capture` snapshots `participantFacts`; `PensionFacts` carries the original participant's facts separately from the payment recipient. A survivor pension does not transfer the deceased participant's factual history to the survivor. Existing source owner, account type, opaque identity, inherited information, gross/taxable amounts and classifications remain unchanged.

```mermaid
flowchart LR
    A[Employer account / pension] --> B[Immutable participant facts]
    B --> C[Transaction source attribution]
    C --> D[TaxIncome incomeSources]
    D --> E[StateTaxCalculationRequest]
    E --> F[State calculator interprets facts at household tax boundary]
```

The existing withdrawal, RMD/opening-RMD, conversion, and tax-funding source factories carry these facts automatically. No mutable arrangement is added to the attribution payload. Existing opaque account identity semantics are unchanged. The common request already contains these attributed sources and `TaxHouseholdContext.distributionDate`; no duplicate request metadata or Pennsylvania conversion layer is needed. Current MI/FL/IL calculators deliberately do not consume the new participant facts, so populating them cannot change their financial rules.

## Roth conversion invariant

The unchanged `ProjectedPortfolioRothConverter.convertHousehold` debits each executed source amount and credits that exact amount to a same-owner Roth destination. Requested conversion shortfall is distinct from retention: a partially fulfilled request still transfers every executed dollar. Tests cover primary and spouse execution with a $100,000 request and $25,000 capacity, and assert full debit/credit, matching gross/modeled-taxable attribution, same ownership and unchanged persisted balance. Existing complete-conversion PA audit fixtures cover $25,000 / $100,000 / $250,000, and constrained execution.

No generated withholding or retained amount is added. External transactions whose full transfer cannot be established remain unsupported. IRA-to-Roth generated flows stay within the prior audit's bounded full-transfer policy; this does not qualify employer-plan conversions automatically.

## Unsupported result contract — approved Route A

`StateTaxCalculationStatus` is shared by production and isolated prototype results:

| Status | Numeric estimate | Meaning |
| --- | --- | --- |
| CALCULATED | Present, including a legitimate zero | Supported numeric estimate, possibly with a documented conservative limitation |
| PROVISIONAL | Present in the isolated prototype only | Research estimate; production contract rejects this status |
| UNSUPPORTED | Absent | Insufficient facts or unsupported model rule; no reliable numeric estimate |

Production `ProductionStateTaxResult` stores `Optional<BigDecimal> estimatedIncomeTax`, immutable limitations and immutable reason codes. Unsupported results require at least one reason and a nonblank explanation, and cannot carry Michigan calculation details. Calculated results cannot carry unavailability reasons. Compatibility constructors preserve existing calculated adapters. The existing `incomeTax()` accessor is calculated-only and throws rather than substituting zero; funding explicitly checks status before calling it.

Generic reason vocabulary:

- MISSING_RETIREMENT_QUALIFICATION
- MISSING_DISTRIBUTION_BASIS
- NONQUALIFIED_ROTH_BASIS_REQUIRED
- INHERITED_DISTRIBUTION_HISTORY_REQUIRED
- UNSUPPORTED_TRANSACTION_TYPE
- UNSUPPORTED_MODEL_RULE

The final umbrella reason is needed for existing isolated dataset/year/rule-boundary refusals that are not transaction or basis failures. Existing prototype warnings remain unchanged; those legacy rule refusals acquire this honest umbrella code rather than guessed finer classifications. Future callers can supply finer reason lists. Both result models have the same status semantics, absent numeric estimate, reason vocabulary and calculation-method semantics. The isolated prototype remains simplified-estimate only.

### Funding and evaluation propagation

`TaxFundingCalculator` checks unsupported status immediately after both trial and final state calculations, **before tax arithmetic or constructing a successful funding result**. `StateTaxUnavailableException` is the dedicated bounded domain failure; it carries the structured result and tax year. `TaxFundingResult` accepts only CALCULATED state results.

No convergence criteria, allocation, RMD, conversion or federal calculations change. No arbitrary conservative amount or zero is substituted. Tests prove refusal on the first trial and on the final recalculation, no additional allocation, and unchanged live/projected balances.

`ProjectionEngine` already rethrows failures that are not actual funding constraints; both ordinary projection and `projectWithOutcome` therefore abort with this typed failure. There is no third `ProjectionExecutionResult` case and no misuse of `InsufficientFunds`.

The existing integrated analyzer candidate boundaries retain the meaningful exception message as an EVALUATION failure; baseline failures abort evaluation. Monte Carlo keeps its existing failure wrapper and no partial analysis; its new `stateTaxUnavailable()` accessor preserves the typed condition/structured result. No Monte Carlo loop or analyzer scoring code changes.

`ProjectionFailureMessages.stateTaxUnavailableMessage` recognizes the dedicated failure through causes. `MainWindow.handleProjectionFailure` presents a concise State Tax Unavailable explanation before generic error handling. Unsupported is not displayed as $0, insolvency, an unexplained unexpected error or successful output. No report containing fabricated annual values can be built from an aborted projection. Calculated reporting is unchanged.

Immutable dependency-injection constructors on the state service, funding calculator and projection engine support bounded refusal tests without registering a new jurisdiction. The default registry is unchanged. The state service also rejects inconsistent jurisdiction/method result metadata.

## Persistence and editing

Employer accounts and pensions persist one `retirementParticipantFacts` group:

```json
"retirementParticipantFacts": {
  "separationStatus": "SEPARATED",
  "separationDate": [2030, 1, 1],
  "retirementConditionsStatus": "MET",
  "retirementConditionsMetDate": [2031, 1, 1]
}
```

Dates retain the repository's existing JavaTime encoding; no global date-format change is introduced. Missing legacy groups and missing individual status properties initialize UNKNOWN. Invalid combinations reject through Jackson/domain validation. JSON repository save/load and `RetirementPlanScenarioCopyService.copy` preserve the value and dates, with independent arrangement copies. `stateTaxAssumptions` and legacy default-to-Michigan are unchanged. Derived annual attribution and unsupported results are not added to saved-plan JSON. Baseline reference-isolation semantics are not redesigned.

`RetirementParticipantFactsEditor` is a shared collapsed optional panel in the detailed employer-account and pension dialogs. It contains two choices plus optional event-date controls; dates are disabled/cleared for unknown or negative statuses. Labels use participant language, not Pennsylvania language. All help is directly on the relevant input controls; no duplicate help icons. Apply creates replacement objects, cancel preserves originals, and existing view callbacks retain dirty-state behavior. These controls are hidden/unmanaged in guided wizard dialogs; values already present are preserved, and new drafts remain UNKNOWN. The wizard is not lengthened and creation is not blocked by optional unknown facts.

Six visual states were generated and inspected: employer unknown/dates, pension unknown/dates, guided account and guided pension. They fit normal desktop sizes; actions remain visible, Unknown is clear, optional dates and direct-control tooltips are verified. Evidence: `target/phase6d3a1-preview/*.png`.

## Pennsylvania re-admission

This phase does not re-verify or alter tax law; it retains the authoritative source trail and supported boundaries documented in Phase 6D.3A and Phase 6C. The isolated PA calculator is still research-only.

| Scenario | 6D.3A | 6D.3A.1 | Remaining limitation |
| --- | --- | --- | --- |
| Social Security retirement/survivor benefits | Supported | Supported | Full-year resident scope; federal treatment unchanged |
| Normal age-eligible individual traditional IRA receipt | Supported with bounded assumptions | Same | Age/owner/account required; not early/history-dependent receipts |
| Individual IRA RMD | Supported | Same | Underlying IRA facts matter, not merely RMD label |
| Complete generated individual IRA-to-Roth IRA transfer | Supported with limitations | Same, full-transfer invariant tested | No external withholding/retention or unknown transaction history |
| Ordinary supported interest/dividends/positive investment gains | Supported with limitations | Same | Caller-established category/basis; no special obligations or loss-netting reconstruction |
| Employer receipt with separated/met participant facts | Missing qualification | UNSUPPORTED | Legal plan characteristics deliberately not captured |
| Employer RMD with participant facts | Missing qualification | UNSUPPORTED | RMD does not prove eligible legal plan |
| Pension with affirmative participant facts | Missing qualification | UNSUPPORTED | Pension name/payment/age do not establish legal qualification |
| Unknown employer/pension facts | UNSUPPORTED | UNSUPPORTED | UNKNOWN never upgrades to exempt |
| Known early/nonqualifying retirement receipt | UNSUPPORTED | UNSUPPORTED | Required basis/history absent; taxation of gross is not a reliable substitute |
| Survivor pension | Underlying qualification missing | UNSUPPORTED | Original participant/recipient retained, legal qualification still missing |
| Nonqualified Roth receipt | UNSUPPORTED | UNSUPPORTED | Contribution basis/order history deliberately absent |
| Complex inherited receipt | UNSUPPORTED | UNSUPPORTED | Unavailable beneficiary/history/basis facts |
| External conversion with withholding/retention/unknown transfer completeness | UNSUPPORTED | UNSUPPORTED | No external transaction reconstruction |

Audit-only tests exercise employer and pension requests with unknown legal-plan qualification even when both participant facts are affirmative and dated; neither is promoted to exempt. They also cover unknown and negative participant assertions. Existing PA worksheets remain intact. Any separately verified narrow inherited exception remains subject to the original audit boundary; complex inherited receipts are not admitted generically.

Recommendation: **PENNSYLVANIA_READY_FOR_PHASE_6D3B_WITH_EXPLICIT_UNSUPPORTED_CASES**.

A future bounded activation can be useful for IRA/SS/ordinary supported investment scenarios while refusing material employer/pension/basis/history scenarios. It must not claim that filling the two participant controls resolves legal-plan qualification. All affected annual evaluations must abort clearly if a material unsupported source exists. Do not add plan-document assertions or a basis engine to force coverage. Pennsylvania is not activated here.

## Financial regression

No existing financial value or numeric expectation was changed. Michigan independent targets remain $174.25 / $348.50 / $1,125.83 / $2,251.65. All 20 Florida category cases remain calculated zero. All 39 independent Illinois fixtures remain unchanged. All 16 frozen financial fingerprints match byte-for-byte, including projection, fixed/bracket-fill conversion, integrated grid/weighted outcomes and seeded Monte Carlo results.

Known participant metadata is also compared directly against UNKNOWN for existing employer-source calculators and complete existing-state projection rows. Federal tax, state tax, total tax, funding, RMDs, conversions, ending assets, net worth/estate metrics, rankings, seeded results, JSON behavior and existing PDF/CSV financial outputs are protected by the regression suites. No tax-law resource, Michigan rule implementation, RMD algorithm or Roth algorithm changed.

## Verification results

IntelliJ bundled Maven, Java 25, project-local `.codex-m2/repository` on every invocation. Logs are retained under `target/phase6d3a1-*.log`.

| Selection | Run | Passed | Failed | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| Before-change warmed 16 workloads plus financial fingerprints | 2 | 2 | 0 | 0 | 0 |
| Final focused domain/persistence/attribution/unsupported/PA/MI/FL/IL/fingerprints, visual opt-in enabled | 221 | 220 | 0 | 0 | 1 |
| Affected regression checkpoint (tax/funding/RMD/Roth/MC/wizard/assumptions/accounts/pensions) | 1075 | 1065 | 0 | 0 | 10 |
| Complete non-benchmark (`-Dtest=*,!*BenchmarkTest`) | 2291 | 2277 | 0 | 0 | 14 |
| Complete benchmark-inclusive suite | 2312 | 2294 | 0 | 0 | 18 |
| After-change warmed 16 workloads plus financial fingerprints | 2 | 2 | 0 | 0 | 0 |

The affected checkpoint preceded the final additional existing-state metadata-invariance test; the final focused and complete suites include that test. Existing benchmark/preview opt-ins account for skips. The six visual states were inspected directly, not merely generated. No expectations were regenerated.

## Performance

Same JDK 25.0.4.1+1-LTS, same machine (8 reported CPUs), same JVM defaults, three warm-up batches and five measured batches, existing 16 real workloads. Before: `target/phase6d3a1-before/measurements.csv`; after: `target/phase6d3a1-after/measurements.csv`. Each run also executes the financial fingerprint test. No parallel test/benchmark workload was started during timing measurement.

The new runtime facts are immutable references, with one shared UNKNOWN value. There is no parsing/reflection, rule loading, plan qualification reconstruction, source regrouping or added income scan. Result status/reason guards are constant-time; calculated result optional amount and slightly larger attribution snapshots add small allocation overhead. Timing changes are mixed and these single-process before/after measurements are not a statistically isolated causality experiment; no zero-runtime-impact claim is made.

| Scenario (batch units) | Before median ms | After median ms | Runtime change | Allocation change |
| --- | ---: | ---: | ---: | ---: |
| Michigan-projection-single (150) | 34.84 | 30.17 | -13.4% | +0.52% |
| Michigan-projection-couple (150) | 39.76 | 40.54 | +2.0% | +0.45% |
| Michigan-fixed-30years (50) | 165.24 | 168.45 | +1.9% | +0.28% |
| Michigan-bracket-30years (10) | 41.66 | 40.89 | -1.8% | +0.37% |
| Michigan-grid81 (1) | 122.01 | 122.16 | +0.1% | +0.88% |
| Michigan-weighted9 (2) | 22.97 | 24.39 | +6.2% | +0.76% |
| Michigan-MC-fixed500 (1) | 1697.21 | 1734.68 | +2.2% | +0.27% |
| Michigan-MC-longevity500 (1) | 1393.10 | 1415.20 | +1.6% | +0.40% |
| Florida-projection-single (150) | 25.15 | 23.96 | -4.7% | +0.54% |
| Florida-projection-couple (150) | 35.91 | 34.55 | -3.8% | +0.48% |
| Florida-fixed-30years (50) | 156.54 | 151.49 | -3.2% | +0.28% |
| Florida-bracket-30years (10) | 35.66 | 34.41 | -3.5% | +0.37% |
| Florida-grid81 (1) | 80.71 | 83.34 | +3.3% | +0.68% |
| Florida-weighted9 (2) | 18.28 | 18.97 | +3.8% | +0.72% |
| Florida-MC-fixed500 (1) | 1552.43 | 1631.67 | +5.1% | +0.28% |
| Florida-MC-longevity500 (1) | 1275.47 | 1296.72 | +1.7% | +0.38% |

## Exact change inventory

Created Java files (
15
):

- `src/main/java/com/daviddunn/retirementplanner/domain/model/RetirementParticipantFacts.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/StateTaxCalculationStatus.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/StateTaxUnavailableException.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/StateTaxUnavailableReason.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/controls/RetirementParticipantFactsEditor.java`
- `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/StateTaxUnavailableMonteCarloBoundaryTest.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/income/RetirementParticipantAttributionTest.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/model/RetirementParticipantFactsTest.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/projection/StateTaxUnavailableProjectionTest.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/StateTaxUnavailableContractTest.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/PennsylvaniaParticipantFactsAuditTest.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/RetirementParticipantPersistenceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/StateTaxUnavailableMessageTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/dialogs/RetirementParticipantEditorTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/dialogs/RetirementParticipantVisualPreviewTest.java`

Modified tracked Java files (
23
):

- `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloExecutionException.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/financial/Account.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/income/Pension.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/TaxFundingCalculator.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/TaxFundingResult.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/FloridaProductionTaxCalculator.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/IllinoisProductionTaxCalculator.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ProductionStateTaxResult.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ProductionStateTaxService.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/SimplifiedStateTaxCalculator.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/StateTaxResult.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/ProjectionFailureMessages.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/AccountDialog.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/PensionDialog.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/IllinoisProductionTaxCalculatorTest.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/ProductionStateTaxServiceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/PennsylvaniaProductionAdmissionAuditTest.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/SimplifiedStateTaxCalculatorTest.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/StateTaxGoldenFixturesTest.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/StateTaxHardeningTest.java`

Updated the pre-existing, untracked architecture-gate report: `PHASE-6D3A1-GENERIC-RETIREMENT-TAX-QUALIFICATION.md`.
No dependency/build/rule-resource/state-assumption changes. Existing-test edits only replace the old nested status identifier with the shared status identifier; no numeric or financial expectations change.

## Measured memory and test coverage

Batch allocation medians increased approximately 0.27–0.88% across the 16 workloads. Retained heap was not independently measured in this phase. The heaviest Monte Carlo point estimates increased 1.6–5.1%; shorter workload changes were mixed, including decreases. No expensive new reconstruction or repeated metadata parsing was introduced. These observations do not justify speculative optimization or weakening validation.

There are 42 new JUnit executions across 10 new test classes, including parameterized cases and one opt-in visual fixture. Coverage includes exact event dates and invalid combinations, legacy UNKNOWN, JSON/scenario copying, immutable source snapshots, actual employer RMD allocation, conversion completeness for both owners, first/final funding refusal, ordinary/structured projection abort, typed Monte Carlo failure, application explanation, optional/cancel/guided editor behavior, and Pennsylvania plan-qualification refusals despite affirmative participant facts. The visual case was run explicitly; it is normally skipped in suite runs.

The existing 38 Pennsylvania admission checks remain unchanged in substance; two new parameterized audit cases each exercise unknown, affirmative dated, and negative participant configurations without asserting legal-plan qualification. Research requests that explicitly assert `ELIGIBLE_PLAN_AND_RETIRED` remain legal-rule worksheets, not evidence that production domain facts establish that assertion.

No financial golden or expected numeric value was updated. RMD, Roth conversion, funding convergence, federal tax, Michigan tax-law implementation, Florida zero-tax behavior and Illinois tax-law implementation are unchanged. Changes to the Florida/Illinois adapters are only the shared enum reference. No state-tax rule resource was modified. Pennsylvania remains excluded from production enum, registry, persistence validation, wizard, assumptions, routing and output.

## Final performance confirmation and readiness

The largest initial heavy-workload point increase was checked in a fresh process, with five warm-up batches and seven measured batches for Florida-MC-fixed500. Median: 1552.33 ms versus the original 1552.43 ms; change 0%. Sample range: 1543.49–1584.46 ms. All captured financial fingerprints matched. Evidence: target/phase6d3a1-confirmation/measurements.csv and target/phase6d3a1-confirmation.log (1 run, 1 passed, 0 failures/errors/skips).

No major or sustained architecture-related performance regression is established by these measurements. The small allocation increase is documented rather than hidden. No speculative optimization, financial precision change, caching of changing tax inputs, or convergence weakening was performed.

All required verification completed successfully. Complete non-benchmark: 2291 run / 2277 passed / 0 failed / 0 errors / 14 skipped. Complete full suite: 2312 run / 2294 passed / 0 failed / 0 errors / 18 skipped. Full-suite log: target/phase6d3a1-full.log (BUILD SUCCESS, 6m25s). Non-benchmark log: target/phase6d3a1-nonbenchmark.log (BUILD SUCCESS, 2m37s).

No financial values changed. Michigan / Florida / Illinois remain unchanged; all 16 financial fingerprints remain unchanged. Pennsylvania remains disabled. No RMD, Roth, funding, mortality, tax-law, analyzer scoring, market model or golden expectations were changed. Existing calculated PDF/CSV outputs and persistence regressions pass.

Tracked git diff --check passes. New Java/Markdown files were checked explicitly for trailing whitespace and blank EOF; zero issues. The staged diff is empty. Nothing staged, committed or pushed.

Recommended next phase: bounded Pennsylvania integration only after approval, using the established IRA/SS/ordinary investment subset and typed refusal for material unavailable legal-plan, basis/history or external-transfer facts. Do not infer legal qualification from the two participant statuses. Do not add the rejected plan-document assertions.

PENNSYLVANIA_READY_FOR_PHASE_6D3B_WITH_EXPLICIT_UNSUPPORTED_CASES

PHASE-6D3A1-GENERIC-RETIREMENT-TAX-QUALIFICATION-IMPLEMENTED
