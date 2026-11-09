# Phase 23D-A — Nonqualified Annuity Architecture and Tax Checkpoint

Audit date: November 7, 2026. Branch: main. HEAD: 866b2f7d0fdf25f449b63e851e667866986bc1c3. The initial index and working tree were clean. This document records source inspection and a proposed design; it does not authorize or describe an implemented annuity feature.

**Continuation status:** the user subsequently approved Option A. Section 17 records the bounded NIIT design before financial source edits and supersedes the initial stop/approval request in sections 1–16. Implemented behavior and verification are recorded separately in [the Phase 23D implementation report](PHASE-23D-NONQUALIFIED-ANNUITY-INCOME.md).

## 1. Checkpoint decision

**Stop before production implementation for a federal tax-scope decision.** A documented fixed excluded dollar amount can support the requested restricted General Rule payment split. A pure finite-basis calculation is feasible. However, taxable nonqualified annuity income can also incur net investment income tax (NIIT). The current federal engine does not calculate NIIT. Treating the new taxable portion solely as ordinary bracket income would therefore omit a potentially material tax.

The user’s Phase 23D sections 5 and 30 require a stop when a fundamental tax-treatment issue remains unresolved. The unresolved issue is the supported calculation/admission scope, rather than uncertainty that NIIT exists. Do not silently omit it, introduce a broad tax-engine change, or promise unrestricted analyzer support.

**Recommendation:** approve a bounded NIIT foundation before enabling unrestricted Phase 23D financial execution. An alternative is an explicitly restricted release that rejects NIIT-exposed calculations. That alternative needs a carefully validated admission contract across Roth trials, tax funding, mortality scenarios and all generated worlds. Neither alternative is implemented here.

No incompatible historical migration has been established as necessary. Compatible optional inputs and new disjoint capabilities appear feasible, subject to implementation and legacy-artifact tests after the tax decision. Existing results remain unchanged.

## 2. Verified current implementation

All source paths below are relative to src/main/java/com/daviddunn/retirementplanner/.

| Component / actual method | Verified behavior | Consequence for Phase 23D |
|---|---|---|
| domain/income/IncomeSource.getAnnualIncome; Pension.calculateAnnualIncome / getAnnualSurvivorIncome | Legacy monthly income and independent survivor dollars; pension participant facts support state classification | Reusing a Pension would falsely imply existing pension tax/source semantics and supplies no finite annuity basis |
| domain/benefit/ConditionalBenefitDefinition canonical constructor / validateFor | Immutable closed categories, triggers, roles and tax characters; household-only recipients; strict bounds | Reusable definition ownership and role validation, but no annuity method, annuitant or recoverable-basis contract exists |
| ConditionalBenefitDefinitionDeserializer.deserialize | Explicit allowed fields, strict enum conversion and numeric/date decoding | New mandatory annuity facts need a closed discriminator; an optional field alone is insufficient protection |
| ConditionalBenefitEvaluator.evaluate | Pure annual selection; shared death view; living recipient; inclusive whole eligible calendar months; DECIMAL128 then annual HALF_UP cents | Reuse selection/cash boundary; introduce only a narrowly defined annuity calculation branch/helper, preserving all old timing |
| ConditionalBenefitReceipt canonical constructor | Permits fully taxable receipts or fully excluded qualifying death principal; checks pattern, source and period | Partial exclusion and closing basis cannot be represented truthfully under either existing tax character |
| AnnualBenefitEvaluation | Immutable receipts and gross/taxable/excluded sums | Suitable authoritative annual aggregation, after a separately validated new receipt variant |
| domain/projection/ProjectionEngine.project / calculateProjectionYear | Evaluates conditional receipts once per year before annual funding retries; gross enters guaranteed cash; sources enter taxation | Preserve one selected result for cash, tax, Roth and Medicare |
| domain/tax/TaxIncomeAttribution.attach | Sums conditional sources’ modeled taxable amounts independently of gross | The split can propagate without a negative deduction; new annuity identity must remain explicit |
| TaxIncome / FederalTaxCalculator.calculate | Ordinary income plus taxable Social Security, modeled deduction, progressive bracket tax | No NIIT income base, threshold evaluation or tax component |
| TaxFundingCalculator.calculate | Iterates withdrawals, attaches immutable sources, calculates federal/state tax, funds their sum | Missing NIIT would also understate funding needs and alter ending assets |
| domain/roth/RothConversionBracketFillCalculator.calculateConversion | Calls authoritative iterative tax funding for trial conversions | Testing only a final annuity payment split cannot establish correct Roth behavior |
| domain/medicare/MedicarePremiumCalculator.calculate | Uses modeled federal AGI for existing same-year MAGI convention | Only annuity taxable income should directly enter this input; NIIT is a separate tax, not an IRMAA addback |
| domain/projection/HouseholdCashSettlement.calculate | Reconciles guaranteed cash, RMD cash and withdrawals against expenses, Medicare, taxes and retained surplus, within $0.02 | Gross receipt once; no deposit for excluded basis and no annuity asset |
| domain/rmd/OwnerRmdCalculator.calculate | Owner IRA/employer-plan RMDs; projected prior-year snapshot overload | Nonqualified annuity basis is not an eligible account balance or RMD distribution |
| EffectiveHouseholdDeathView.resolve / state / isAlive | Configured or supplied lifetime deaths; January 1 death year; absent spouse distinct | Annuitant = recipient can reuse the living-person rule if enforced as a domain invariant |
| app/montecarlo/MonteCarloAnalyzer; MonteCarloStrategyComparisonAnalyzer | Common engine, generated-world capability binding, independent candidates; runtime errors abort execution rather than becoming ordinary portfolio failure | New tax limits must be execution errors, never ignored receipts or false failure-probability samples |
| app/socialsecurity/LongevityWeightedIntegratedStrategyEvaluator.evaluate | Isolated scenario projection through second-death year minus one; opening estate shortcut; failure stops expected-value completion | No basis refund, final-return deduction or terminal annuity settlement can be inferred |
| ConditionalBenefitAdmission | Existing MI/IL/no-general-income-tax benefit execution; full reference evaluation for benefits/premiums; supplied-world restrictions | Existing admission is not sufficient evidence for a new tax class |

Repository search for NIIT, net investment income, 1411 and 8960 in production/test Java returned no matches. Inspection of FederalTaxCalculator, FederalTaxCalculation and TaxFundingCalculator independently confirms the missing calculation. This is source evidence, not a newly executed financial regression.

## 3. Federal authorities and supported method

The preferred method is appropriate only as **externally documented General Rule fixed-payment facts**, not an arbitrary exclusion election. Commercial nonqualified annuities cannot use the Simplified Method merely because payments are monthly. [IRS Topic 411](https://www.irs.gov/taxtopics/tc411), [Publication 575 (2025), Partly Taxable Payments](https://www.irs.gov/publications/p575).

IRC 72(a)–(c) distinguishes gross annuity income, exclusion based on investment and expected return, and unrecovered investment. The per-receipt exclusion is capped by investment remaining immediately before receipt. Investment at the annuity starting date is adjusted consideration, not automatically the original purchase price. [26 USC 72, 2024 edition](https://www.govinfo.gov/content/pkg/USCODE-2024-title26/html/USCODE-2024-title26-subtitleA-chap1-subchapB-partII-sec72.htm).

Publication 939’s General Rule instructions derive the fixed tax-free portion from the initial regular payment and exclusion percentage. Post-1986 exclusions stop at net cost. A documented fixed dollar amount plus verified remaining basis is therefore sufficient for the arithmetic of the restricted fixed-payment subset; the application need not calculate actuarial expected return or certify the insurer’s tax determination. [Publication 939, revised December 2025, Taxation of Periodic Payments and Exclusion Limits](https://www.irs.gov/publications/p939).

The tax annuity starting date and first cash payment date must remain distinct. Publication 939 gives an August payment for a July period, with a July annuity starting date. Store the documented tax date separately from cash commencement; do not derive it from the UI payment date. [Publication 939, Annuity Starting Date](https://www.irs.gov/publications/p939).

An annual Form 1099-R alone is not a universal proof of the future monthly split or remaining investment. The 2026 instructions explicitly identify code D nonqualified annuity distributions as potentially subject to section 1411. Use insurer/tax-professional documentation and a dated opening-basis assertion; do not infer eligibility from a form code alone. [2026 Forms 1099-R and 5498 instructions](https://www.irs.gov/instructions/i1099r).

Reviewed online November 7, 2026. Publication revisions and the code edition are identified rather than described as a newly issued 2026 tax ruling. This checkpoint supplies planning boundaries, not an individual legal determination.

## 4. NIIT: demonstrated blocking example

NIIT is 3.8% of the lesser of net investment income and MAGI exceeding the applicable threshold. Section 1411 includes annuity gross income, while excluding specified qualified-plan distributions. This is separate from ordinary federal brackets and Medicare Part B/D premiums. [26 USC 1411](https://www.govinfo.gov/content/pkg/USCODE-2024-title26/html/USCODE-2024-title26-subtitleA-chap2A-sec1411.htm).

For an independently specified MFJ example, assume $294,240 other included income, $5,760 taxable commercial annuity income, no other net investment income, no allocable deductions and no NIIT MAGI adjustments. MAGI is $300,000. The MFJ threshold is $250,000. NIIT is 0.038 × min($5,760, $50,000) = **$218.88**. The excluded $8,640 is absent from both taxable annuity income and this NIIT base. Qualified IRA conversion income can raise MAGI without itself becoming net investment income. [IRS NIIT questions and answers, questions 3, 4, 8, 9 and 11](https://www.irs.gov/newsroom/questions-and-answers-on-the-net-investment-income-tax).

That $218.88 is an independent arithmetic illustration, not a ProjectionEngine result or a complete return. Current FederalTaxCalculator would apply ordinary brackets but has no additional NIIT calculation; TaxFundingCalculator funds federal bracket tax plus state tax. Adding a taxable annuity source alone cannot close the gap.

### Decision alternatives

| Alternative | Required work | Limitation / recommendation |
|---|---|---|
| NIIT foundation, then complete Phase 23D | Explicit modeled net-investment-income attribution, MAGI, filing thresholds, supported deductions/adjustments or admission, additional federal tax funding and retained evidence; disjoint applicability | **Recommended** for Roth/high-income and weighted/MC use. Determine the supported subset of other investment sources explicitly; do not assume every existing Pension or conditional ordinary source is a qualified-plan distribution |
| Restricted NIIT-unexposed Phase 23D | Explicit typed capability and admission that refuses any unsupported tax evaluation, including tax-funding/Roth trials and every relevant mortality/world outcome | Smaller tax scope but significant usability and analyzer restrictions. A static opening-income test or an acknowledgment is insufficient. A safe conservative guard may reject a plan whose trial conversion crosses a threshold even if a different conversion could have worked |
| Omit NIIT with a generic warning | No authoritative additional tax | Rejected: materially incomplete new tax execution under unrestricted support claims |

The restricted alternative must not treat a refused calculation as a zero receipt, failed portfolio, dropped mortality weight or successful partial ranking. MonteCarloAnalyzer’s runtime-error propagation and the weighted evaluator’s scenario failure are useful boundaries, but all search/comparison catch paths still need focused validation. No claim of complete guard coverage is made here.

The recommended foundation should initially apply through an explicit new capability to **new annuity-enabled calculations**, account for all admitted modeled NIIT sources in those calculations, and retain the existing no-annuity algorithms unchanged. It must not apply NIIT only to the annuity while silently ignoring other included investment income. Inputs whose net-investment-income status cannot be established need explicit admission or source facts. Broader correction of preexisting no-annuity calculations would be a separate applicability decision, never a silent golden-value update. Historical outputs remain frozen under either choice.

## 5. Model-location alternatives

| Option | Reuse / benefits | Costs / correctness concerns | Recommendation |
|---|---|---|---|
| Expand ConditionalBenefitDefinition with a closed annuity category and method facts | Existing immutable plan collection, IDs, copies, death context, annual evaluator, retained receipt and reporting infrastructure | Needs strict new tax/receipt facts, finite basis, annuitant invariant and capability. Existing monthly eligibility is not a tax method | **Preferred design**, pending tax gate; a bounded nested facts record, not an event graph |
| New IncomeSource subtype | Existing Income workspace and source ownership | Existing authoritative selection and state retirement qualifications specialize Pension/SS. A subtype alone would need parallel selection, basis attribution and history work | Reject for initial phase: greater risk of cash/tax divergence and false pension classification |
| New immutable plan-level quoted-annuity collection | Strong explicit type and uncomplicated fields | Repeats collection persistence, reconstruction, fingerprint, UI and capability machinery already present for conditional definitions | Reserve if implementation proves the closed extension substantially more complex; no demonstrated need now |

The annuity category would require FIXED_DATE, MONTHLY_RECURRING, annuitant equal to named recipient, zero escalation and no death-benefit qualification. It must not masquerade as one of the existing taxable/life-principal categories. A distinct nonqualified-annuity source kind would preserve state and NIIT identity even if ordinary federal subtotal arithmetic is shared.

## 6. Proposed restricted input contract

This is a proposed contract, not a new persisted schema or enum allocation.

- Preserve opaque benefit UUID, label and household destination; UUID/label/order remain excluded from normalized financial identity. Distinct identical contracts remain multiplicative.
- Add a closed annuity category and explicit nonqualified General Rule fixed-exclusion tax character, plus an omitted-when-absent immutable annuity-facts object. Old readers must reject the unknown discriminator; test the accepted predecessor decoder directly.
- Record named annuitant and enforce annuitant = recipient. No joint ownership, separate payee, survivor continuation or refund.
- Record fixed gross monthly quotation, documented fixed excluded dollars per full monthly payment, documented tax annuity starting date, original adjusted investment at that date, verified remaining investment and its projection-opening as-of date.
- Record acquisition completed before projection opening. Cash commencement is independent; a future first payment is allowed only for an already funded contract with a verified applicable tax determination. No source-account funding transaction is inferred.
- Require an explicit supported-method/qualification assertion: personal after-tax commercial single-life fixed annuity, post-1986 tax starting date, verified tax split/opening basis, no residual liquid contract asset, no refunds/guarantees/variable payments/special ownership or unverified methods.
- Reject early-distribution cases requiring an unmodeled penalty determination; the conservative initial boundary is annuitant at least 59½ at modeled payment commencement. Do not infer a section 72(q) exception.
- Initially omit optional contractual termination: only annuitant death ends this single-life quotation. A shorter-of-life-and-term contract has different tax facts and should not be accepted merely by adding an end date.

Validate 0 ≤ excluded per payment ≤ gross, 0 ≤ opening investment ≤ original adjusted investment; zero opening investment means fully taxable future payments. Reject missing facts and inconsistent acquisition/tax/cash dates. Do not automatically equate premium, original adjusted investment and opening remaining investment.

For the smallest exact arithmetic subset, require the quoted gross, excluded dollars and investment amounts to be representable in cents without rounding, bounded by existing monetary/date/definition limits. This is a proposed new-type restriction, not a tightening of existing Pension/benefit validation. Non-cent documentation would need a separately verified precision contract.

An acknowledgment cannot detect an annuity premium also entered as a brokerage balance. Show a required no-separate-contract-asset assertion and warning; without durable account linkage, do not claim reliable automatic duplicate-asset detection or match accounts by display name. The basis ledger is never wealth.

## 7. Payment and survival timing

Existing conditional monthly receipts count whole partial calendar months. That legacy convention remains unchanged. Publication 939 also recognizes fractional first payments; such payments cannot automatically receive a full monthly exclusion.

**Recommended initial annuity timing subset:** full monthly payments with month-aligned commencement and projection opening (first day of a month). Reject unsupported fractional first payments or a midmonth opening rather than guessing whether that month’s payment was already received. This keeps the initial schedule exact without carrier billing-day administration. Broader midmonth support would require an explicit separately verified receipt schedule; it is not certified by this checkpoint.

For admitted inputs, count inclusive eligible calendar months from the later of first-payment month and opening month. July commencement gives six payments through December. A pre-opening commencement establishes an active stream, but consumes no pre-opening basis again: the opening basis already incorporates actual previous recoveries.

Use EffectiveHouseholdDeathView exclusively. If the annuitant dies January 1 of year Y, no Y or later payments/recovery occur. The other spouse’s death does not stop payments. No mortality random draw, midyear death, refund or final-return basis deduction is added. The user explicitly deferred post-death unrecovered-basis deductions; reports must identify that limitation rather than imply the final tax return is fully modeled.

If the plan opening date changes, require matching verified basis-as-of facts. Save As, scenario copies and swaps preserve the original anchor; they must never silently roll basis forward, reset it to purchase premium or regenerate IDs.

## 8. Pure basis-recovery design

Prefer a pure bounded helper invoked by ConditionalBenefitEvaluator. Its context contains frozen opening basis/date, documented per-payment split, eligible month counts and the existing death view. It returns detached annual evidence, not a mutable ledger on a live definition.

Let B0 be opening remaining investment, E the excluded dollars per payment, P the number of recognized payments before the current year and N current-year payments. Compute:

- Opening basis this year = max(B0 − E × P, 0).
- Current recovery = min(opening basis this year, E × N).
- Gross = monthly gross × N.
- Taxable = gross − recovery.
- Closing basis = opening basis this year − recovery.

For monthly evidence, payment j recovers min(E, remaining basis before j). January/February exhaustion follows directly. Prior counts stop at annuitant death; when the annuitant is alive in the current year, the same irreversible death context establishes previous survival. Retain closing basis even in a zero-payment year for audit, without treating it as an asset or refund.

All calculations use BigDecimal/DECIMAL128 and the established cents result policy. Count months arithmetically; do not serialize plans or allocate lifetime event logs inside tax retries. At most 100 definitions and 12 current-year payments per definition need bounded detailed evaluation.

Invariants: gross = taxable + recovery; recovery cannot exceed opening basis; closing basis is nonnegative; repeated annual evaluation is identical; trials/strategies/candidates share no mutable basis. The no-annuity path performs no new financial arithmetic.

## 9. Authoritative financial integration

One annual selection supplies gross cash, taxable ordinary annuity amount, excluded recovery, opening/closing basis, source UUID, annuitant/recipient, year and method evidence. The existing receipt constructor must be extended under a new discriminator and strict facts contract; neither old tax character may carry partial recovery.

Gross enters the existing guaranteed-cash source once. TaxIncomeAttribution receives only the taxable component while preserving a distinct nonqualified-annuity source identity for state/NIIT decisions. The excluded component is not a deduction, interest, life-insurance principal, IRA withdrawal or second cash source. Use the same frozen facts in all TaxFundingCalculator and RothConversionBracketFillCalculator calls. Medicare retains its existing AGI convention. No account, IRA contribution or RMD balance is created.

HouseholdCashSettlement remains the sole surplus-retention path. Expected zero-return conservation: gross annuity cash minus actual expenses/Medicare/taxes increases retained cash once. Investment growth sequencing stays unchanged. NIIT, if approved, must enter tax funding and total taxes, rather than appearing only as a report footnote.

## 10. State-law review and proposed admission

No new state is enabled in this checkpoint. Existing ordinary benefits, excluded life principal and premiums retain their existing admission.

| State/model | Official guidance and source implementation | Proposed annuity admission after federal gate |
|---|---|---|
| Michigan | Treasury permits a possible subtraction for a lifetime retirement annuity paid to a senior citizen, including the specified age-65 rule. MichiganRetirementEligibility.assess currently denies non-retirement/non-PENSION sources and has no commercial-annuity qualification. Blanket pension or blanket non-pension treatment would be unsafe. [Michigan Treasury: Individual Plans, Senior Citizen Annuity IRC 72](https://www.michigan.gov/taxes/iit/tax-guidance/tax-situations/retirement-and-pension-benefits/qualified-pension-distribution-requirements/individual-plans) | **Block initially.** A dedicated validated annuity qualification/subtraction mapping is needed; source recipient DOB and lifetime contract facts must be preserved. Do not borrow employer pension qualifications |
| Illinois | Pub-120 limits the retirement subtraction to listed qualified sources and disallows nonqualified employee-plan income. A personal after-tax commercial annuity is outside those qualified sources. IllinoisProductionTaxCalculator starts from federal AGI and only subtracts assessed eligible retirement sources. [Illinois Pub-120](https://tax.illinois.gov/research/publications/pubs/retirement-income.html) | **Candidate initial support:** taxable federal component remains in the ordinary state base; recovery excluded through AGI; no retirement subtraction. Requires explicit new-source tests before enablement |
| Iowa | Revenue explicitly states nonqualified annuities do not qualify for the retirement-income exclusion. IowaTaxBaseBridge.reconcile currently rejects conditional source kinds, despite the ordinary federal-base starting point. [Iowa Revenue, March 13, 2025 guidance](https://revenue.iowa.gov/taxes/tax-guidance/individual-income-tax/retirement-income-tax-guidance) | **Block initially.** Add separately verified nonqualified source reconciliation before any future admission; do not grant age-based retirement exclusion |
| Pennsylvania | Department guidance identifies nonqualified annuity income as taxable interest to the federally included extent; other guide passages discuss non-employee annuity cost recovery/gain. Current PennsylvaniaProductionTaxCalculator rejects unsupported conditional sources and does not reconstruct state basis. [PA Revenue annuity FAQ](https://revenue-pa.custhelp.com/app/answers/detail/a_id/368/related/1), [PA gross-compensation guide](https://www.pa.gov/agencies/revenue/forms-and-publications/pa-personal-income-tax-guide/gross-compensation) | **Block initially.** Resolve exact commercial periodic-annuity source/basis mapping using current PA guidance before admission. A PA-only classification must not relabel the federal annuity as taxable interest |
| Existing no-general-individual-income-tax models | StateTaxJurisdiction and NoGeneralIndividualIncomeTaxCalculator explicitly cover AK, FL, NV, NH, SD, TN, TX, WY under their existing full-year/year-baseline limitations | **Candidate initial support**, within existing model scope; no new insurance exemption or new jurisdiction is inferred. Federal NIIT still matters |

These are separate conclusions about law, current implementation and proposed admission. A zero state result is not proof that an unsupported source classification was executed. No claim is made that every Michigan annuity is exempt or that Iowa/PA can simply use an existing pension route.

## 11. Existing and proposed historical/version contracts

Observed current combinations:

| Family / feature | Capability / algorithm | MC payload / stored fingerprint |
|---|---|---|
| Legacy / expense-only | Original maps; temporary numeric fingerprint remains read-only | Payload V1 / fingerprint V1; expense standalone V2 / V3; expense paired V3 / V4; temporary fingerprint V2 not a new-write contract |
| Fully taxable conditional receipts | Analyzer capability V1; analyzer-annual-v1 | Standalone V4 / V5; paired V5 / V6 |
| Qualifying excluded life principal | Capability V2; analyzer-life-principal-v2 | Standalone V6 / V7; paired V7 / V8 |
| Contractual premiums, optionally with benefits | Capability V3; analyzer-contractual-premiums-v3 | Standalone V8 / V9; paired V9 / V10 |
| Deterministic conditional receipts | deterministic-annual-v1 or deterministic-life-principal-v2; separate premium suffix insured-alive-months-v1 when configured | CompleteProjectionSnapshot V1; retained schemas V1/V2; original input and annual evidence binding |

Analysis envelope schema V1 and pension-selection=death-selected-v2 remain distinct and unchanged. Current declarations and validators were inspected in ConditionalBenefitAnalysisExecution, MonteCarloSnapshot, MonteCarloFingerprintService, AnalysisSnapshot and the deterministic semantic classes.

Proposed annuity combinations must be disjoint: a new annuity input normalization contract; a new deterministic annuity semantic; a new analyzer capability that composes original benefits/premiums; and new standalone/paired MC payload and fingerprint contracts. **Provisional next slots**, subject to the NIIT architecture: analyzer capability V4, MC payload V10/V11 and fingerprint V11/V12. No identifier is allocated or promised by this report. NIIT-inclusive versus NIIT-unexposed semantics cannot share an indistinguishable stamp.

Old capabilities must reject annuity-bearing originals. A new annuity capability requires an annuity in at least one original candidate and exact family/protocol/candidate bindings. Validate tax method, opening basis anchor, source identity, original digest, gross/taxable/recovery/closing-basis evidence and matching capability; reject missing, mixed, tampered and unsupported combinations.

Use compatible omitted optional annuity facts/evidence, preserving old creator requirements and absent-field wire output. Do not weaken AnalysisSnapshotCodec’s unknown/missing/duplicate validation or its 32 MiB raw, 16 MiB compressed and structural/numeric budgets. RetainedProjectionSnapshot.restoreProjection requires exact recapture equality; any new optional evidence must pass that check. If it cannot, stop for a new schema decision rather than repurpose a retained version.

Financial normalization includes method, annuitant, payment/tax dates, original/opening investment, excluded dollars, basis-as-of and qualification. Labels/UUID/order remain presentation/audit facts outside economic identity. Frozen original documents retain them exactly. No-annuity maps, documents, fingerprints and goldens remain byte-identical. No historical rewrite, recalculation or current-label substitution is proposed.

## 12. Analyzer and Monte Carlo execution matrix

Every row below is **proposed only**, pending the federal decision, source/capability implementation and tests. No public admission is changed.

| Family | Shared engine/death context | Required annuity evidence and limits |
|---|---|---|
| Ordinary/baseline deterministic; single/couple | ProjectionEngine; configured EffectiveHouseholdDeathView | Frozen basis anchor, annual receipts, new deterministic capability, supported state/tax scope |
| Integrated individual, Quick, grid/heat map, complete search, associated comparisons | Isolated plan/strategy, same common engine | Complete annuity definitions across every strategy; typed unsupported tax outcomes cannot be presented as supported financial results |
| Couple and single mortality-weighted; weighted comparisons | Existing sampled/enumerated lifetime scenario; exact ending before final-death year | Independent basis per scenario; probabilities/discounting/order unchanged; reference path when prefix/equivalence proof is unavailable |
| Couple standalone fixed / longevity | Generated economic worlds; configured deaths / existing sampled deaths | New standalone capability, original plan and existing expense protocol |
| Single standalone fixed / longevity | Same, with primary only | PRIMARY annuitant valid; no artificial spouse |
| Couple paired fixed / longevity | Shared generated worlds and independent frozen Current/Baseline | Independent annuity amount, basis, dates, identity and annuitant; new paired capability |
| Single paired fixed / longevity | Existing primary mortality worlds and independent candidates | Same isolation; identical economics give zero deltas |
| Opening second-death estate shortcut | Existing opening asset estimate, no financial engine call | No annuity cash or unrecovered-basis asset/refund; no post-final-death settlement |
| Caller-supplied worlds/paths, unverified reuse | Existing guards | Remain blocked; no broad admission removal |
| Social Security-only | Existing SS-only engine/objective | Unchanged; no annuity value added to SS rankings |

There is no new random dimension. Fixed quoted payments and basis depend on frozen inputs and existing scenario survival only. Preserve market/inflation/mortality streams, expense dimension/version 6/1, candidate correspondence, seed/year identity, cancellation and resource limits. Do not persist per-trial annuity paths.

## 13. UI and reporting design, not implemented

Reuse Income Sources / ConditionalBenefitsPane with a specialized modal form or specialized configuration in the existing dialog: “Nonqualified Income Annuity — After-Tax Purchase.” Show annuitant, monthly gross, cash commencement, documented tax method/date, per-payment excluded dollars, original adjusted investment and verified opening remaining investment/as-of. The proposed month-alignment and age restrictions require clear validation. No generic tax-free percentage, pricing, insurer account or funding controls.

Use RequiredFieldSupport, detached drafts, fixed Save/Cancel, theme controls and existing plan-change callbacks. Show the user-provided-documentation assertion, no-separate-liquid-asset warning, remaining-versus-original basis distinction, state warning and final-death deduction limitation. Preserve UUID on edit; duplicate gets a new ID and requires fresh qualification confirmation.

Retain annual gross/taxable/recovery/opening/closing basis in completed evidence. Summary/Yearly Details and PDFs must display original method and source, not label basis recovery as life-insurance principal. Historical adapters use frozen inputs and saved evidence; no evaluator or projection call during rendering/export. Compact assumption sections supplement existing charts/rankings. No actual UI scene or PDF was changed/rendered at this checkpoint.

## 14. Independent oracles and future validation gates

| Case | Gross | Recovery | Taxable | Closing basis |
|---|---:|---:|---:|---:|
| Twelve $1,200 payments; $720 documented exclusion; opening $10,000 | $14,400 | $8,640 | $5,760 | $1,360 |
| Next twelve payments | $14,400 | $1,360 | $13,040 | $0 |
| Following twelve payments | $14,400 | $0 | $14,400 | $0 |
| July commencement, six payments; opening $10,000 | $7,200 | $4,320 | $2,880 | $5,680 |
| Annuitant January 1 death; opening $10,000 | $0 | $0 | $0 | $10,000, not an asset/refund |

Monthly exhaustion oracle: following year January recovers $720; February $640; March–December $0. The $218.88 NIIT counterexample is an additional independent oracle. These calculations were checked with decimal arithmetic in ignored target/phase23d/arithmetic-oracles.json, without running a projection. They do not establish any insurer contract’s legal eligibility.

Before execution enablement: domain/strict old-reader tests; exact finite-basis/monthly boundary and reentrancy tests; direct tax controls including NIIT decision; MI/IL/PA/IA admission; literal Roth/RMD/Medicare/cash conservation controls; ordinary/lifetime equivalence; all eight generated modes with stochastic ordinary expenses; Current/Baseline differences and equality; Phase 23B/C coexistence; capability tampering and strict legacy restore; Save As/restart/swaps/Retry Save; frozen historical PDFs and UI drafts.

After focused gates, run the full normal Java 25 / IntelliJ bundled Maven suite with local .codex-m2/repository and -DargLine=-Xmx4g, no exclusions/golden regeneration. Accepted baseline is user-confirmed 4,592 total / 4,546 passed / 46 existing skips. Inspect actual Light/Dark scenes at 900×650 and 1180×820 and rendered current/historical PDFs. None of those implementation acceptance checks has been performed in this documentation-only stop.

## 15. Source and report inventory

Inspected implementation/contracts, including targeted method bodies, constructors or call-site searches appropriate to the checkpoint:

- domain/income/IncomeSource.java, Pension.java, IncomeSourceAttribution.java, HouseholdPensionIncomeCalculator.java (selection references).
- domain/benefit/ConditionalBenefitDefinition.java, ConditionalBenefitDefinitionDeserializer.java, ConditionalBenefits.java, ConditionalBenefitEvaluator.java, ConditionalBenefitReceipt.java, AnnualBenefitEvaluation.java, ConditionalBenefitAdmission.java, ConditionalBenefitExecutionVerification.java.
- domain/tax/TaxIncome.java, TaxIncomeCalculator.java, TaxIncomeAttribution.java, FederalTaxCalculator.java, FederalTaxCalculation.java, TaxFundingCalculator.java.
- domain/tax/state/ProductionStateTaxService.java, StateTaxCalculationRequest.java, IllinoisProductionTaxCalculator.java, MichiganProductionTaxAdapter.java, michigan/MichiganRetirementEligibility.java, PennsylvaniaProductionTaxCalculator.java, IowaProductionTaxCalculator.java, iowa/IowaTaxBaseBridge.java, NoGeneralIndividualIncomeTaxCalculator.java.
- domain/model/RetirementPlan.java, Person.java, StateTaxJurisdiction.java; domain/projection/ProjectionEngine.java, ProjectionYear.java, EffectiveHouseholdDeathView.java, HouseholdLifetimeScenario.java, HouseholdCashSettlement.java; domain/medicare/MedicarePremiumCalculator.java; domain/roth/RothConversionBracketFillCalculator.java; domain/rmd/OwnerRmdCalculator.java; domain/estate/EstateAtSecondDeathCalculator.java.
- app/projection/CompleteProjectionSnapshot.java, ConditionalBenefitExecutionSemantics.java, ConditionalBenefitFinancialInputs.java, ContractualPremiumExecutionSemantics.java.
- app/socialsecurity/results/AnalysisSnapshot.java, AnalysisVersions.java, AnalysisFingerprintService.java, ConditionalBenefitAnalysisExecution.java, RetainedProjectionSnapshot.java; app/socialsecurity/LongevityWeightedIntegratedStrategyEvaluator.java.
- app/montecarlo/MonteCarloAnalyzer.java, MonteCarloStrategyComparisonAnalyzer.java, MonteCarloSnapshot.java, MonteCarloFingerprintService.java; persistence/AnalysisSnapshotCodec.java, StateTaxDatasetRepository.java (validation references).
- ui/controller/ApplicationController.java, ui/dialogs/ConditionalBenefitDialog.java, ui/socialsecurity/FrozenConditionalBenefitDetails.java, app/export/ProjectionPdfExporter.java (revision/reporting call sites).

Reports reviewed/reconciled through relevant sections: Phase 23A, 23B, 23C-A and 23C implementation reports; Phase 22A and 22B0–B4 reports; PHASE-12D-COMPLETE-PROJECTION-SNAPSHOT-PERSISTENCE.md, PHASE-12E-IMMUTABLE-ANALYZER-ASSOCIATIONS.md, PHASE-12F-CURRENT-BASELINE-SWAP.md, PHASE-19B3-SOCIAL-SECURITY-REDUCTION-PROJECTION-ANALYZERS.md and PHASE-21B4-MONTE-CARLO-FINAL-INTEGRATION-AND-CLOSURE.md. Source takes precedence over old report comments, including older shallow-baseline cautions and pre-execution conditional-benefit descriptions.

This is a checkpoint audit, not a claim that every reconstruction/reporting/analyzer entry point has passed new-feature tests. Those checks remain gated on implementation.

## 16. Verification, preservation and approval request

Only this architecture document and the companion Phase 23D status report are intended additions. Ignored target/phase23d contains initial Git/head/hash observations and decimal arithmetic evidence. No production/test source, financial rules, schema, build dependency, saved historical artifact or existing report is intentionally changed. No stage/commit/push/reset/clean/revert operation is authorized or performed.

No Maven suite, JavaFX acceptance run, Monte Carlo simulation, native fixture generation or PDF rendering was run for this stopped architecture checkpoint. The accepted Phase 23C total is a baseline, not a new test result. Whitespace and tracked-file hash preservation results are recorded in the companion status report after final verification.

**Approval requested:** choose (A) a bounded NIIT foundation before completing annuity execution, recommended, or (B) explicitly authorize an NIIT-unexposed-only annuity subset with conservative runtime/analyzer rejection. Finalize the corresponding capability/tax-source contract before production edits. The proposed month-aligned fixed-payment subset and IL/existing-no-general-tax initial state scope are explicit planning restrictions, not claims of broader contract support.

All joint/survivor/refund/period-certain/variable annuities, purchases, qualified annuities, QLACs, MYGAs, exchanges, pre-annuitization withdrawals, cash values, external beneficiaries and post-death basis deduction calculations remain deferred. No later phase has begun.

## 17. Approved bounded NIIT addendum — before production financial edits

The user approved Option A following this report: implement NIIT for new annuity-enabled calculations, preserve all no-annuity semantics, and reject unresolved source classifications. The preceding stop is superseded by that approval subject to the following bounded contract. This section describes the selected design; implementation/test evidence belongs in the companion implementation report.

**Law and arithmetic.** For admitted domestic individual taxpayers, NIIT is 3.8% × min(max(net investment income, 0), max(NIIT MAGI − threshold, 0)). Thresholds are MFJ/qualifying surviving spouse $250,000; MFS $125,000; Single/HOH $200,000, not inflation indexed. A separate versioned NIIT rule source will keep these statutory values out of ProjectionEngine. Foreign-earned-income and CFC/PFIC adjustments are not silently zero: the supported qualification explicitly excludes those facts, nonresident/dual-status elections, special adjustments and NIIT loss/deduction carryovers. [IRC 1411](https://www.govinfo.gov/content/pkg/USCODE-2024-title26/html/USCODE-2024-title26-subtitleA-chap2A-sec1411.htm).

**Deductions and MAGI.** The initial personal domestic subset requires the modeled standard-deduction method and no applicable special NIIT adjustments or deductible investment expenses. There is no itemized state-tax allocation or arbitrary deduction input. Standard deduction is not subtracted from NII. AGI equals NIIT MAGI only under that explicit qualification. Commercial annuity taxable amounts and ordinary personal taxable interest are included; qualified retirement distributions are excluded from NII while remaining in AGI when federally taxable. [IRS Form 8960 instructions (2025), lines 1–3, Part II, and lines 13–17](https://www.irs.gov/instructions/i8960).

**Source-completeness boundary.** The source inventory establishes: taxable interest has an explicit TaxIncome scalar/source; dividends and capital-gain attribution kinds exist but do not have a complete federal projection tax-base calculation; generic Pension has no federal qualified-plan/NIIT classification; generic taxable conditional income does not identify whether it is investment/business/other income. Annuity-enabled plans must reject generic pensions and other taxable conditional definitions, and refuse unknown taxable brokerage withdrawals or unexpected dividend/gain/OTHER source facts. They may include Social Security, explicitly qualified account distributions/conversions, the new annuity, qualifying Phase 23B principal, Phase 23C premiums and ordinary expenses. Direct tax boundaries may include correctly attributed personal taxable interest. This is a usable pensionless SS/IRA/Roth/quoted-annuity subset, not a comprehensive investment-income engine.

The initial production guard is deliberately stricter for persisted brokerage accounts: a positive brokerage balance is rejected before an annuity-enabled run, rather than waiting for an unclassified sale in a later world. Generic pensions are rejected even when dormant. Definitions remain saveable; these are execution limits. For not-yet-started payments, opening investment must equal the documented original adjusted investment because no prior payment recovery is represented. Positive remaining investment also requires a positive documented excluded amount.

Existing retained household assets have explicit modeled zero-taxable cash-withdrawal attribution; existing growth is not automatically recognized as interest/dividends/realized gains. Final source audit therefore adds a mandatory **nonqualified-growth guard**, rather than assuming that growth is exempt. In an annuity-enabled run, any nonzero return with positive opening cash accounts or beginning retained household assets is a typed unsupported-input error. Initial cash holdings conservatively require zero returns throughout that execution; the engine does not silently change any rate or volatility. Qualified-account growth remains supported when those unknown nonqualified balances are absent. Known principal withdrawals from a pool that has had no growth remain valid cash, with no invented investment income.

This preserves all no-annuity growth behavior. It permits useful IRA/Roth/SS/annuity spending scenarios with market risk, and zero-nonqualified-growth mortality/expense-risk scenarios, while refusing worlds whose cash surplus creates an unclassified taxable-yield/gain requirement. A generated analysis aborts rather than dropping the world. Final deterministic and analyzer model names explicitly include **guarded**; interim development prototypes without that model cannot authenticate final execution. Expanding recognized investment yield or basis/gain realization remains outside this approval.

**Execution and funding.** Presence of an annuity activates the new NIIT semantic even in zero-payment/death/basis-exhausted years. Frozen annual annuity evidence carries that activation through the same source path into every tax-funding and Roth trial. The federal total includes ordinary bracket tax plus NIIT; AGI/taxable income and same-year IRMAA remain separately defined. Final annual evidence records NIIT MAGI, threshold/status, included investment components, supported deductions/adjustments, rate and tax. NIIT is not a Medicare premium or second cash expense.

**Admission.** Apply domain/state/source admission at deterministic and analyzer boundaries; retain annual typed checks for dynamically supplied withdrawals/sources. An unsupported source throws a distinct unsupported-input exception, never FundingConstraint. Generated MC wraps/aborts unsupported execution, retains no completed simulation, and does not drop a trial; weighted expected value cannot drop a scenario. Preserve earlier successful results/cancellation/storage lifecycle. Supplied-world/path guards and reference-only weighted execution remain. IL and existing no-general-income-tax jurisdictions are the only initial state candidates. No new RNG.

**History.** Keep old deterministic and analyzer capabilities untouched. Use a new annuity/NIIT deterministic semantic, analyzer capability V4, standalone/paired payload V10/V11 and fingerprints V11/V12, with explicit composition of old features. Bind original method/qualification/basis-as-of, candidate documents and annual basis/NIIT facts. Extend only known optional retained source evidence, omitted on old results, preserving envelope V1 and retained annual shapes; reject new evidence without matching original inputs and capability. No historical recalculation. If exact compatible retained reconstruction fails, stop rather than globally change schemas.

**Required independent checks.** All filing thresholds; $218.88 control; annuity plus attributed interest; qualified IRA/Roth MAGI crossing; above/below thresholds; standard deduction not reducing NII; unknown-source rejection; finite basis exhaustion; tax-funding conservation; Medicare separation; all eight generated modes/candidate isolation; old exact snapshots/fingerprints/goldens; real UI/PDF and full Maven regression. These are gates to verify, not completed results.

## 18. Final implementation contracts and verification boundary

The approved Option A has now been implemented. Sections 1–16 preserve the original stopped-checkpoint record; section 17 records the selected pre-production design. Current implementation evidence is in PHASE-23D-NONQUALIFIED-ANNUITY-INCOME.md. The NIIT-unexposed-only alternative was not selected.

| Financial contract | Final new value |
|---|---|
| Input normalization | NONQUALIFIED_ANNUITY_NIIT_INPUTS_V3 |
| Deterministic semantic | deterministic-nonqualified-annuity-niit-guarded-v3 |
| Analyzer capability | V4 / ANALYZER_NONQUALIFIED_ANNUITY_NIIT_GUARDED_V4 |
| Analyzer algorithm | analyzer-nonqualified-annuity-niit-guarded-v4 |
| Generated standalone MC | Payload V10 / fingerprint V11 |
| Generated paired MC | Payload V11 / fingerprint V12 |
| NIIT annual evidence | DOMESTIC_RECOGNIZED_INVESTMENT_INCOME_1411_V1 |
| Rule source | niit-2013-v1.json / irc-1411-2013-v1 |

These are disjoint new-write contracts; no old semantics or global map is repurposed. Existing complete/envelope V1 and retained V1/V2 shapes remain, with only known omitted optional evidence. Both original annuity input and validated annual basis/NIIT facts are bound. Existing taxable benefits, excluded principal, premiums, pension selection and Monte Carlo payloads/fingerprints retain their meanings. Interim unguarded development stamps are not accepted as final guarded execution.

The admitted opening is month-aligned, 2026 or later, with acquisition before opening, post-1986 documented tax start, age at least 59½ at first cash, fixed full monthly payments and exact-cent monetary inputs. Illinois and existing no-general-income-tax jurisdictions are verified; MI/IA/PA stay blocked for this type. No separate liquid annuity asset or post-death refund is recognized.

The nonqualified-growth guard is mandatory: nonzero annual returns with initial positive cash or beginning retained household assets are unsupported because recognized yield/realized gains are unknown. Inputs are never silently changed. Qualified-account growth without those unknown balances remains supported. Generic pensions, positive brokerage assets, other taxable conditional definitions and unknown dividend/gain sources are also blocked for this capability. Explicit attributed personal interest is included completely. Retirement distributions/conversions affect MAGI without becoming NII. Thus NIIT is supported above threshold without pretending to implement a full investment tax engine.

Dynamic unsupported worlds abort generated/weighted analysis rather than dropping trials/scenarios or treating unsupported taxes as funding failure. Existing caller-supplied-world guards and benefit-bearing weighted reference paths remain. New evidence stores zero-payment basis years and final NIIT source details without mutable state or added random draws. UI and frozen reports disclose these limits. The companion report distinguishes completed focused/full verification, inspected JavaFX/PDF evidence and pending native user acceptance.
