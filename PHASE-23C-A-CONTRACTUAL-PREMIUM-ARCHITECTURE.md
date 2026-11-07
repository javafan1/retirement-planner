# Phase 23C — Checkpoint A: Contractual Premium Architecture

Date: November 7, 2026. Initial branch `main`, HEAD `42950386184a80a0ae4521257bff261f2ecd2d94`. Index and working tree initially clean. This checkpoint precedes production edits. Starting tracked-file hashes and Git observations are retained in ignored `target/phase23c/`.

## 1. Current expense execution

`domain/financial/Expense` is the immutable expense value, stored once on `Household`. `ExpenseDeserializer` requires the original six fields in strict snapshot decoding, handles optional volatility, and skips unknown properties. `ProjectionEngine.calculateProjectedExpenses` selects overlapping years, escalates, optionally applies a bound stochastic path, prorates ordinary recurring expenses in the opening year, then applies survivor spending. Later ordinary expense start/end dates gate years rather than exact eligible months. Preserve all these legacy conventions.

## 2. Survivor spending

The engine multiplies every ordinary `RECURRING` expense by `postDeathExpenseFactor` after any household death. A contractual premium needs a distinct branch before that calculation: eligibility depends on its named insured, never the surviving household's lifestyle factor. Ordinary expenses retain their existing branch unchanged.

## 3. Mortality

Reuse `EffectiveHouseholdDeathView`, resolved once from configured deaths or the explicit lifetime scenario. A selected death is January 1 of its year. No premium in that year or later; absent spouse is not a deceased spouse. No new death state or random draw.

## 4. Premium payment interval

Model an annualized contractual premium budget, not carrier billing or refunds. Require commencement, permit inclusive termination. Intersect the interval with the projection opening and current calendar year. Every overlapping calendar month counts once, including partial months. Annual expense is `annualAmount * eligibleMonths / 12`, rounded once to cents HALF_UP. Fixed nominal escalation only. A $2,000 annual rate owes $2,000 over twelve eligible months and $1,000 over six; this does not assume an annual January 1 bill. Death overrides all eligible months. Ordinary expense proration does not change.

## 5. Alternatives

| Alternative | Assessment |
|---|---|
| A: extend Expense with a closed premium form | Selected. Reuses household storage, immutable copies, expense editor, common cash settlement, original request documents and financial fingerprints. A new required discriminator protects old readers. |
| B: separate plan-level premiums | Safe if fully versioned, but adds a second expense collection, reconstruction inventory, validation and UI mutation surface without a distinct economic asset. Larger change than necessary. |
| C: negative conditional receipt | Rejected. Income attribution, tax selection and guaranteed cash are not expense semantics; would risk deductions, double counting and misleading reporting. |

## 6. Domain contract

Add `ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM` and an omitted-when-absent immutable `ContractualPremiumPolicy` with closed insured roles PRIMARY/SPOUSE and model `INSURED_ALIVE_FIXED_NOMINAL_MONTHS_V1`. Require nonblank bounded description, nonnegative bounded BigDecimal annual amount, commencement, ordered bounded dates, `GrowthCategory.NONE`, zero volatility and the policy. Prohibit the policy on legacy types. Validate spouse presence at household admission/mutation/load. No opaque ID is needed: ordinary Expense has value occurrences, not durable IDs; identical separate premiums remain separate list occurrences. No owner, payer, account or death-benefit inference.

## 7. Persistence

Retain the original Expense JSON properties and ordering/omission for legacy values. New type and policy must appear together. Reject duplicate premium properties, unknown nested policy fields/model/role, missing policy/commencement, invalid amounts/dates, inflation or volatility. Retain established legacy unknown-field behavior for ordinary records. No plan schema migration or new root collection.

## 8. Old-reader protection

Protection uses the **existing required `expenseType` field**, not an optional field old readers skip. The accepted pre-change deserializer calls Jackson enum decoding on that field; its old enum contains only RECURRING and ONE_TIME. Verify the actual pre-change source in an isolated reader test: valid premium JSON must fail, including with unknown-property ignoring enabled. New readers must also reject a policy attached to RECURRING/ONE_TIME, preventing a mixed record from silently becoming ordinary. Older releases cannot execute premium-bearing plans or frozen premium inputs; unchanged legacy files remain readable. This is additive closed-input support, not a migration or reinterpretation.

## 9. Financial identity

Existing financial trees already include household expenses and remove descriptions. Include the closed type, insured/model, amount, dates and nominal escalation. Normalize only premium occurrences for economic decimal equality and irrelevant premium ordering; leave ordinary expense order/scale identity untouched. No labels or invented IDs enter the financial identity. Exact frozen documents still retain original descriptions, order and scale. Empty-premium fingerprints remain byte-for-byte unchanged.

## 10. Monte Carlo

Premiums have no enabled sampling binding. Preserve ordinary definition correspondence, duplicate ordinals, dimension 6/version 1, master seed and all existing market/inflation/mortality/expense draws. The financial engine's premium branch never reads an expense multiplier. Generated standalone and paired fixed/longevity modes use their existing death context. Supplied benefit/premium worlds remain unsupported. Expense path financial binding must include the insured policy, independently of sampling identity.

## 11. Current/Baseline

Capture each candidate's own original request. No substitution or regenerated identity. Equal economic premiums under equal scenarios must have equal effects; different insureds/dates/amounts legitimately differ. Premiums and Phase 23B death receipts remain independent.

## 12. Result/history contracts

No existing identifier is repurposed. Complete projection schema V1 and retained annual schemas remain unchanged. Add a digest-bound deterministic `contractual-premiums=insured-alive-months-v1` stamp composed with existing B0/B2/23B stamps. Historical validation checks saved evidence, never runs an evaluator.

Extend the existing bounded original-input analyzer capability container with a **disjoint V3** model `ANALYZER_CONTRACTUAL_PREMIUMS_V3`, algorithm `analyzer-contractual-premiums-v3`. V3 is required iff at least one original candidate contains premiums. It binds both original benefits and premium definitions and supports their coexistence; existing V1/V2 reject premium-bearing originals. The existing container/property name is retained for wire compatibility, but V3 explicitly attests the composed financial features rather than claiming a premium is income. Preserve V1 taxable-benefit and V2 excluded-principal meanings.

| Inputs | MC payload | Stored MC fingerprint | Capability |
|---|---:|---:|---|
| Legacy/expense-only/taxable-benefit/life-principal without premium | Existing V1–V7 | Existing V1–V8, numeric V2 read-only | Existing exact combinations unchanged |
| Premium-bearing standalone, with any supported benefits/ordinary expense volatility | V8 | V9 | Analyzer capability V3, generated original family/protocol |
| Premium-bearing paired, either/both candidates, with any supported benefits/ordinary expense volatility | V9 | V10 | Analyzer capability V3, ordered Current/Baseline bindings |

Envelope schema remains V1. No global algorithm-map change. Missing/mixed capability/version/fingerprint/input digest must reject. Existing immutable UUID/digest associations and historical exports remain untouched. Premium-bearing weighted paths use the full reference evaluator rather than unproved prefix/equivalence reuse; no-premium optimization remains unchanged. Premium expenses introduce no new state-tax classification or deduction; coexisting benefits retain their established state admission.

## 13. UI/reporting

Extend the existing Expense dialog/list, not navigation. Show Contractual Insurance Premium, insured selector, annual amount, required start/optional end, fixed nominal policy and concise month/death/survivor explanation. Hide volatility/inflation inputs for premiums; construct only applicable draft fields on Save. Household-aware selectors omit absent spouse; Cancel/close do not commit. Reuse required markers, theme controls, scrolling and fixed dialog buttons. Frozen current/history/PDF assumptions disclose premium amount, insured, dates, fixed nominal escalation, death termination and survivor exemption independently of death benefits.

## 14. Oracles and verification

Permanent tests precede financial execution changes: $2,000 alive; zero from insured January 1 death; $2,000 after other death for factors 0/50/75/100%; ordinary control still reduced; expired interval zero; eligible month/proration boundaries; zero-return cash conservation; $400,000 excluded receipt plus zero death-year premium. Verify domain bounds/strict decode, isolated old reader, copies, fingerprints, version tampering, all eight generated modes, candidate independence, seeded ordinary streams, history without recalculation, UI transactions and actual JavaFX/PDF previews. Then one complete normal Java 25 Maven suite, existing skips, no changed goldens.

## 15. Stop conditions

Stop if old readers can silently accept the premium form, legacy serialization/fingerprints/goldens or random vectors change, required history decoding cannot stay strict/readable, or correct execution requires new insurance/tax/terminal-estate semantics. No such incompatibility has yet been established. Proceed only with the bounded additive contracts above; record actual verification and any correction in the final report. Native acceptance remains a separate user gate.
