# Phase 23E-A — Calculation Mode Architecture

Checkpoint recorded November 8, 2026, before production edits. Starting branch main, HEAD 866b2f7d0fdf25f449b63e851e667866986bc1c3, empty index. Phase 23D has 27 tracked modifications and 25 new/initially untracked files; preserve that work. Starting status and all source/report hashes are under ignored target/phase23e.

## 1. Existing application settings

ApplicationSettings is mutable preferences with detached copy; ApplicationSettingsService serializes asynchronous field-specific updates and atomic repository writes. ApplicationSettingsDialog previews themes but commits only durable Save, retaining Cancel/error behavior. Settings currently affect theme/directories/introductions, not math. Add a BASIC default for new plans only; no entitlement provider or feature checks.

## 2. Existing plan configuration

RetirementPlan owns PlanningAssumptions, accounts, conditional definitions and immutable projection associations. Financial inputs are frozen separately from mutable settings. RetirementPlanSnapshot and explicit reconstruction sites must preserve any new root field. JSON readers are strict at the plan root: a new recognized optional configuration can protect against old-reader silent reinterpretation while omitted legacy output preserves original digests.

## 3. Location and closed policy

Add the closed domain FinancialCalculationMode BASIC/ADVANCED as an optional explicit plan configuration, omitted when absent. Keep a separate immutable execution policy that distinguishes legacy absence from explicit selection; absence never consults settings. Do not place modes in economic assumptions or JavaFX services. Pass explicit policy through authoritative annual source attribution into every tax-funding/Roth trial, using a zero-flow control/evidence record like existing NIIT metadata, not a second tax engine.

## 4. Defaults and legacy semantics

New user-created plans receive the application default (initial BASIC). Loading/copying/restoring old files does not assign a mode. Missing mode internally means the exact pre-23E contract: annuity presence activates guarded 23D NIIT; otherwise NIIT remains absent. UI resolves that legacy contract to Basic or Advanced with a compatibility explanation, not a third selectable mode. An explicit edit establishes new provenance. Saving an untouched legacy file keeps omission. Global preference changes never promote or rewrite a plan.

## 5. Existing NIIT activation and rejection inventory

FederalTaxCalculator currently detects NONQUALIFIED_ANNUITY sources. NonqualifiedAnnuityAdmission rejects generic pensions, other taxable conditional income and positive brokerage balances; ProjectionEngine rejects nonzero returns with initial cash/retained assets; NetInvestmentIncomeTaxCalculator rejects incomplete interest/distribution/conversion attribution and unsupported dividend/gain/OTHER sources. These are NIIT-completeness checks. Annuity state/opening/basis/contract/age/recipient rules are independent and remain in both modes. Completed NIIT evidence cannot be reused as trial income under either mode.

## 6. BASIC policy

Keep gross/taxable/recovery arithmetic unchanged and omit NIIT from every federal/Roth/funding iteration. Bypass only NIIT source/growth restrictions. Retain annuity domain and IL/existing no-general-tax state admission; MI/PA/IA remain blocked. Preserve existing brokerage/cash growth conventions with an explicit NIIT Not Modeled disclosure. A missing NIIT calculation is not a computed zero; retain mode/status evidence in completed annual results.

## 7. ADVANCED policy

Explicit ADVANCED activates the bounded source-complete NIIT policy, including supported no-annuity SS/qualified-account/personal-interest combinations. This requires no new income recognition: unknown pensions, brokerage gain/basis, dividend/gain sources and nonqualified growth remain unsupported. Standard-deduction/domestic/no-special-adjustment assumptions remain explicit. No universal investment-income or foreign-tax claim. Preserve statutory thresholds, same-year IRMAA separation and typed unsupported execution.

## 8. Common comparison mode

New paired execution selects the active/Current plan's explicit mode, or its resolved legacy numerical policy when missing. Apply it to both isolated working candidates; retain both original saved configurations/documents unchanged. New mixed/annuity legacy comparisons must acquire a new common-mode capability, not reuse V4. Reject the whole comparison if either candidate fails admission. Baseline/current saved snapshots remain historical and are not recomputed merely for viewing. Plain old no-annuity legacy pairs already share NIIT omission and may retain byte-identical compatible contracts.

## 9. History

Old complete/envelope/retained versions, original documents, UUIDs, digests and associations remain readable without rerunning math. New known optional annual policy evidence is omitted on old results. New semantic authenticates selected mode, NIIT status and original inputs separately from settings. Legacy decoding must not require today's default. Old-reader tests use isolated predecessor classes; explicit new mode files must reject rather than silently reinterpret.

## 10. Proposed disjoint contracts

Preserve all Phase 22/23B/C/D tags and MC payloads 1–11/fingerprints 1–12. Add explicit BASIC/ADVANCED deterministic semantic, a new closed mode-aware analyzer capability V5, generated standalone/paired payloads V12/V13 and fingerprints V13/V14. Reuse the existing bounded original-input manifest infrastructure with an explicit mode model; do not attach speculative licensing metadata. New mode capability can exist without conditional benefits. Old capability/fingerprint combinations must reject explicit mode inputs. Original no-mode normalizations remain unchanged. Material mode edits invalidate financial fingerprints; SS-only identity remains independent.

Exact implementation names/validation matrix will be recorded in the implementation report. Prior tests that assert newly written V4/MC V11 for mixed annuity comparisons describe superseded write contracts; retain legacy decode tests and numerical oracles, and strengthen new-write assertions to the new explicit common-mode contract. Do not replace any historical financial golden.

## 11. Save/Load/Save As and reconstruction

Cover RetirementPlan constructors, RetirementPlanSnapshot, ProjectionBaselineFactory/BaselineProjectionService, CompleteProjectionSnapshot.freeze, scenario-copy service, ApplicationController save preparation/restoration/swap/execution construction, MC candidate/baseline capture and new-plan wizard completion. Do not initialize IDs/defaults during history. A separate new-user-plan boundary applies settings; generic domain copies do not.

## 12. Analyzer matrix

Ordinary/single deterministic, integrated individual/Quick/complete/grid, weighted reference, break-even, baseline and all eight generated MC modes use the same annual policy. Advanced weighted reuse remains guarded where NIIT equivalence is unproven; Basic retains existing independently valid behavior. No changes to RNG, expense dimension 6/1, seeds/worlds, failure reduction, progress or cancellation. Unsupported Advanced sources abort, not portfolio failure or dropped worlds. SS-only objectives remain untouched. Existing benefit-bearing supplied-world restrictions remain, but adding Basic mode alone must not impose unrelated feature restrictions.

## 13. UI and reporting

Settings selects default for new plans. Existing assumptions draft selects actual plan mode with Apply/Cancel, revision/freshness callback and no automatic projection. Summary/Yearly Details/PDF/analyzer disclosures use frozen completed policy, not live preferences. Basic states NIIT Not Modeled and federal totals exclude it. Advanced states bounded validated NIIT and may reject unsupported sources. Historical missing-mode results use original execution facts with a legacy explanation. Both options and all features remain available.

## 14. Future licensing

A future external entitlement boundary could govern offered mode choices or feature commands. This phase adds no license classes, unused interfaces, keys, remote services, active feature identifiers or access enforcement. Modes are financial semantics, not product tiers.

## 15. Verification and stop conditions

Before financial edits add independent Basic/Advanced $300,000/$218.88 tests, finite-basis controls, brokerage/pension/growth admission, common paired policy and legacy equivalence. Then settings/default/cancel, reconstruction, strict old/new capabilities, all generated modes, original history, actual Light/Dark scenes/PDF and full Java 25 Maven test with local cache/Xmx4g; preserve 46 skips and financial goldens.

Stop for incompatible snapshot replacement, historical reinterpretation, unresolved source/state support, silent mode fallback or feature access restrictions. The selected optional/disjoint design appears compatible; implementation remains subject to exact round-trip and authenticity tests. No production edits preceded this document. Reviewed current settings/plan/tax/annuity/projection/attribution/Roth/snapshot/fingerprint/candidate/controller/UI paths, Phase 23B/C/D reports, Phase 13 settings/save workflows, immutable analysis/complete history and Phase 21 stochastic provenance. Source is authoritative over older report descriptions.

## 16. Fresh comparison versus immutable baseline detail

Source tracing established that getBaselineProjection restores the original accepted baseline and is also used for historical details/charts. It must not silently recalculate that historical cache. Add a separate transient, source-revision-fenced common-mode comparison calculation initiated explicitly from the existing comparison section. It freezes both candidates, applies Current's mode to working copies, computes both on the existing background executor and publishes only if original Current/Baseline/revision still match. Original projection UUIDs/maps/history are untouched. A mismatch initially shows an explanation and a Run Comparison action; it must not display asymmetric deltas or automatically project while history is being viewed.

Comparison/Break-Even consume the common pair after acceptance; saved Baseline details remain explicitly original/historical. PDF comparison data carries the chosen mode separately. Mixed-mode raw comparison/Break-Even inputs reject. This is financial validity admission, not mode-based feature licensing. Both BASIC and ADVANCED retain the same actions. No persisted historical schema or original numerical artifact is changed by this transient comparison context.

## 17. Final compatible contract details

Explicit mode annuity financial normalization uses MODE_EXPLICIT_ANNUITY_INPUTS_V4; the old no-mode V3 normalization stays byte-compatible. BASIC-created annuities use the contract-only PERSONAL_GENERAL_RULE_FIXED_SINGLE_LIFE_V2 qualification. It neither requires nor asserts NIIT assumptions. Advanced execution of that input requires an explicit editor revalidation to the existing domestic standard-deduction V1 qualification; the payment/basis definition is never automatically promoted. This is a NIIT admission boundary only.

Mode-aware analyzer capability V5 binds the common selected policy and independent original documents. Its MC scenario protocols are FROZEN_REQUEST_CONFIGURED_DEATHS_V1 and FROZEN_REQUEST_LIFETIME_JAN1_PRIOR_DEC31_V1. Generated executions still require the exact accepted generated-world provenance; the neutral capability does not bypass RNG/caller-source verification. BASIC alone preserves the existing no-benefit/no-premium/nonstochastic-expense supplied-world path, with an original world fingerprint required for new stored mode evidence. Benefit-bearing and stochastic-expense caller paths retain their earlier guards.

Break-Even UI/PDF mode disclosure is derived from saved annual policy facts. Its old persisted two-property person-summary shape is unchanged: the derived presentation mode is ignored by JSON, like the established reduction-policy presentation metadata. Old reports without explicit mode facts do not gain a current preference or newly computed NIIT claim.
