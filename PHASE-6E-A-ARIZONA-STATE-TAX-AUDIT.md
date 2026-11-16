# Phase 6E-A — Arizona State Income Tax Implementation Audit

Audit and source-verification date: **2026-11-13**. Documentation only; Phase 6E-B is not authorized.

## 1. Executive summary

Arizona is suitable for bounded implementation with **moderate complexity**. Reuse the existing state-tax interface, federal AGI, household context, iterative tax funding and generic tax results. No generalized state-tax engine or analyzer redesign is necessary.

The arithmetic is simple, but Arizona must not be implemented as an exclusion of all retirement income. Traditional withdrawals, RMDs, private pensions, taxable annuities and taxable Roth conversions remain in the AGI base. Federally taxable Social Security is subtracted.

Two material qualifications require decisions before admission:

* Enacted law adopts the temporary federal senior deduction as an Arizona subtraction. The current federal calculator does not calculate that deduction. Arizona needs a bounded state-only calculation, without changing federal behavior, or an explicitly approved approximation. Silent omission would materially affect common retirees and conversion analysis.
* The model lacks general brokerage gain/basis/holding-period information. Arizona's qualifying long-term capital-gain subtraction cannot be inferred from account withdrawals. Complete taxable-account coverage is outside a simple Arizona adapter.

Recommended first scope: ordinary domestic full-year Single/MFJ residents, fully taxable traditional retirement income, private pensions, modeled taxable annuity receipts, ordinary bank interest, qualified Roth distributions and taxable conversions, with explicit coverage limitations. Specialized pension and brokerage admission require approval below.

**Evidence labels:** Current code = inspected implementation, not runtime certification. Verified rule = supported by linked authoritative evidence. Proposed = not implemented. Unresolved = not a verified production value. No production, configuration or test files were changed.

## 2. Current codebase findings

Production paths below are relative to `src/main/java/com/daviddunn/retirementplanner/`.

| Actual class/method | Current behavior and reuse |
|---|---|
| `domain.model.StateTaxJurisdiction.alphabeticalChoices()`, `fromCode(String)` | Explicit production enum, sorted presentation choices, textual persistence codes. AZ is absent. |
| `domain.model.StateTaxAssumptions` constructor, `simplified(...)` | One full-year state for every projection year. Only SIMPLIFIED_STATE_ESTIMATE enabled; rate overrides rejected. No residency timeline. |
| `domain.tax.state.ProductionStateTaxService.defaultCalculators()`, `calculate(...)` | Explicit production registry, separate from research catalog; validates result routing metadata. |
| `StateIncomeTaxCalculator.calculate(StateTaxCalculationRequest)` | Existing narrow implementation boundary is sufficient. |
| `StateTaxCalculationRequest` | Immutable year, filing status, household, TaxIncome, federal calculation, projected rules and state assumptions. |
| `TaxHouseholdContext.capture(...)`, `withActiveOwners(...)` | Birth dates, household/active owners, tax date and selected pension facts. Supports different spouse ages and death-aware context. |
| `ProductionStateTaxResult.unsupported(...)`, `incomeTax()` | Unsupported has no numeric estimate; numeric access throws. Calculated zero is distinct. |
| `MichiganProductionTaxAdapter`, `domain.tax.state.michigan` | Preserve existing rules, details and goldens unchanged. |
| `IllinoisProductionTaxCalculator.calculate(...)` | Closest arithmetic/structure reference: AGI, flat rate, taxable-SS subtraction, age handling and baseline-year disclosures. **Do not copy its retirement/conversion exclusions.** |
| `IowaProductionTaxCalculator.calculate(...)`, `planningLimitations(...)` | Best reference for versioned dedicated rules, structured admission and frozen disclosure. Its federal-taxable-income bridge and low-income relief are not Arizona rules. |
| `PennsylvaniaProductionTaxCalculator.calculate(...)` | Reuse missing qualification/basis refusal patterns, not its state-defined base. |
| `simplified.SimplifiedStateTaxCalculator.calculate(...)` | Research engine already supports FLAT multiplication, AGI adjustments, per-person seniors and boundaries. Exact dataset year only, no future-year policy. Not the production dispatcher. |
| `persistence.StateTaxDatasetRepository.load()` | Research admission is independent of production. Current resource search found no AZ/Arizona entry. |
| `domain.tax.TaxIncome.getOrdinaryIncomeBeforeSocialSecurity()` | Pension + tax-deferred withdrawals + Roth conversion + taxable interest + conditional ordinary income. Conversion is separate from a Roth withdrawal. |
| `TaxIncomeCalculator.calculate(...)` | Includes opening tax-deferred distributions, authoritative selected SS and pension amounts, annual attribution. |
| `TaxIncomeAttribution.attach(...)` | Attaches metadata and taxable conditional receipts; a CAPITAL_GAIN enum does not itself establish an AGI gain total. |
| `FederalTaxCalculator.calculate(...)` | Modeled taxable SS, AGI, projected standard deduction, bracket tax and optional NIIT. No federal age additions, enhanced senior deduction, itemization or complete Form 1040 reconstruction. |
| `FederalTaxCalculation.getAdjustedGrossIncome()`, `getTaxableSocialSecurity()`, `getModeledBaseBreakdown()` | Reuse observed amounts; modeled coverage is not legal completeness. |
| `TaxFundingCalculator.calculate(...)` | ProductionStateTaxService called for trial and final taxes, around lines 471 and 534, with selected state and recomputed federal result. This is the operative combined-tax path. |
| `CombinedTaxCalculator.combine(...)` | Legacy federal/Michigan combiner; not the Arizona integration target. |
| `ProjectionEngine` annual flow | Explicit selected state passed to bracket fill and funding around lines 755 and 790–808; generic state result retained in ProjectionYear. |
| `domain.roth.RothConversionBracketFillCalculator.calculate(...)` overloads | Explicit state-aware path retains funding feedback; compatibility overloads default to Michigan. Test/use explicit selected-state route. |

`IncomeSourceAttribution` distinguishes ordinary withdrawals, RMD, OPENING_RMD, ROTH_CONVERSION, PENSION, INTEREST, DIVIDEND, CAPITAL_GAIN and NONQUALIFIED_ANNUITY. `withdrawal(...)` marks tax-deferred income taxable, cash/Roth zero, taxable-account amounts unknown. These are existing modeled assumptions, not proof of actual Roth qualification or traditional-account basis.

`PensionFacts` contains owner, dates, survivor amount, COLA and participant facts, but no Arizona government/military pension classification. Names cannot establish eligibility. There are no dedicated TaxIncome dividend/gain totals, capital-loss or acquisition-date fields.

Existing birthdays suffice for mixed ages. Illinois provides a useful pattern: living filers age at year end, inactive deceased filers at modeled January 1 death, no later birthday attainment. Final Arizona instruction age-at-death/January 1 birthday conventions still need review. Preserve configured status through death year, Single afterward.

## 3. Current supported-state baseline

**12 production jurisdictions:** Michigan, Illinois, Pennsylvania, Iowa; plus Alaska, Florida, Nevada, New Hampshire, South Dakota, Tennessee, Texas and Wyoming using the no-general-individual-income-tax model. Arizona, North Carolina, Kentucky and Colorado are not registered. An older eight-state research catalog or stale comment is not the activation registry.

Future-year policy is state-specific. Illinois holds nominal dataset parameters after baseline. Iowa explicitly supports 2026 onward with nominal 2026 state parameters and the modeled projected federal base. Research calculator accepts its exact year only. Arizona needs an explicit policy; there is no universal state indexing contract.

Preserve `ProductionStateTaxServiceTest`, `StateTaxUnavailableContractTest`, `simplified/StateTaxGoldenFixturesTest`, all Michigan calculator/deduction/taxable-income tests and Illinois/Pennsylvania/Iowa production goldens. Do not change existing expected values for Arizona.

## 4. Arizona tax-rule matrix

All sources investigated **2026-11-13**. Some direct ADOR requests returned HTTP 403; official search-index extracts are identified below. Final 2026 Form 140 instructions were not established as accessible. Secondary calculators were not used as authority.

| Rule/effective period | Verified rule, proposed use and uncertainty | Authoritative source |
|---|---|---|
| Flat rate, 2026 | Official 2026 estimate instructions/worksheet specify 2.5%. Generic ARS 43-1011 page returned historical text; do not copy its old brackets. No enacted later rate change verified. | [ADOR 2026 140ES instructions](https://azdor.gov/sites/default/files/document/FORMS_INDIVIDUAL_2026_140iES.pdf), [worksheet](https://azdor.gov/sites/default/files/document/FORMS_INDIVIDUAL_2026_140ES_f.pdf), official indexed text |
| AGI/conformity | Individual starting point is federal AGI. Enacted 2026 omnibus updates IRC conformity to January 1, 2026 with retroactive provisions. Use modeled AGI, not federal taxable income. | [Enacted HB4168/SB1861 fact sheet](https://www.azleg.gov/legtext/57leg/2R/summary/S.1861-4168ATT_ASENACTED.DOCX.htm) |
| Basic standard deduction | Statute specifies Single 15,750/MFJ 31,500 base, subject to annual inflation adjustment in federal basic-deduction manner. **Final published 2026 Arizona amounts unresolved.** Federal 2026 16,100/32,200 is a plausible derivation, not an independently verified AZ constant. | [ARS 43-1041 A,H](https://www.azleg.gov/ars/43/01041.htm), [IRS 2026 1040-ES](https://www.irs.gov/pub/irs-pdf/f1040es.pdf) |
| Age exemption | Current law: 2,100 for eligible age-65 taxpayer/spouse, subject to joint return/dependency rules. Distinct from standard deduction and enhanced senior subtraction. Do not invent a general personal exemption. | [ARS 43-1023 E](https://www.azleg.gov/ars/43/01023.htm) |
| Temporary senior subtraction | Arizona adopts amount under IRC 151(d)(5)(C), effective from 2025. Federal provision applies 2025–2028: 6,000 per eligible person with MAGI phaseout above 75,000 Single/150,000 MFJ. Must stop in 2029 absent new law. | [ARS 43-1022(35)](https://www.azleg.gov/ars/43/01022.htm), [IRS guidance](https://www.irs.gov/newsroom/working-families-tax-cuts-individuals-and-workers), [Public Law 119-21](https://www.govinfo.gov/content/pkg/PLAW-119publ21/pdf/PLAW-119publ21.pdf) |
| Social Security, current law | Subtract federally included IRC 86 amount, not gross SS again. No sunset identified. | [ARS 43-1022(10)](https://www.azleg.gov/ars/43/01022.htm) |
| Traditional IRA/401(k), RMD, private pension, taxable annuity | No general subtraction in reviewed statute: ordinary federally included amounts stay in AGI. Use fully taxable modeled distributions and taxable annuity receipt portion. | [ARS 43-1022](https://www.azleg.gov/ars/43/01022.htm), [ADOR resident form page](https://azdor.gov/forms/individual/form-140-resident-personal-income-tax-form-fillable), indexed corroboration |
| Qualified Roth vs conversion | Qualified distributions are outside federal AGI; federally taxable conversion stays in AGI. No general Arizona conversion subtraction identified. Destination Roth does not make conversion exempt. Federal qualification/basis assumptions remain. | Arizona AGI starting point and [ARS 43-1022](https://www.azleg.gov/ars/43/01022.htm) |
| Interest/dividends | Ordinary federally included amounts remain; US-obligation interest subtraction and special exempt-income additions require source facts. Federal qualified-dividend rate coverage is not repaired by Arizona. | [ARS 43-1022(4)](https://www.azleg.gov/ars/43/01022.htm), [ARS 43-1021](https://www.azleg.gov/ars/43/01021.htm) |
| Capital gains | Since 2015, 25% subtraction for qualifying net LTCG from assets acquired after 2011; unverifiable date does not qualify. Gifts/inheritance have transferor acquisition-date rule. | [ARS 43-1022(22)](https://www.azleg.gov/ars/43/01022.htm) |
| Government/military pension | Certain federal/AZ government pensions: capped 2,500 subtraction. Qualifying uniformed-service retirement: full subtraction since 2021. Missing classification is material. | [ARS 43-1022(2),(26)](https://www.azleg.gov/ars/43/01022.htm) |
| Charity enhancement, 2026 onward | Qualifying contributions increase standard deduction, capped 1,000 Single/2,000 MFJ. Missing dedicated input. | [ARS 43-1041 I](https://www.azleg.gov/ars/43/01041.htm) |
| Credits | Family credit has income/per-person/nonrefundable limits. Senior property/rent credit may be refundable with special income definition excluding SS. Not a universal age credit. | [ARS 43-1073](https://www.azleg.gov/ars/43/01073.htm), [ARS 43-1072](https://www.azleg.gov/ars/43/01072.htm) |

Release blockers: verify final Arizona 2026 indexed deduction, age-at-death and birthday conventions, final senior worksheet, and return rounding/table differences. The 140ES form's use of prior-year exemption information is not final 2026 annual-return authority. Do not treat vetoed or introduced conformity/rate bills as enacted law. [ADOR deduction guidance](https://azdor.gov/individuals/income-tax-filing-assistance/deductions-and-exemptions) directs users to year-specific instructions.

## 5. Common scenarios and sufficiency

Within existing modeled federal coverage: SS-only; SS plus ordinary fully taxable IRA/401(k)/RMD income; private pension plus SS; annual conversions; bank interest; qualified Roth distributions. Different spouse ages, pension start dates, independent SS claiming, one spouse receiving retirement income earlier and survivor status use existing annual facts. This does not certify arbitrary employment/wage/tips coverage.

NONQUALIFIED_ANNUITY conditional receipts already carry gross/taxable portions and annuity basis facts. Use admitted taxable receipt amount once; do not re-tax gross payment. Inherited accounts, nonqualified Roth and IRA after-tax basis need separate admission.

General taxable-account withdrawals/gains/dividends are not accurately representable from current TaxIncome alone. Withdrawal principal is not income. Attribution kinds do not supply federal gain inclusion, holding period, netting, acquisition dates or state adjustments. Do not advertise complete common brokerage coverage.

## 6. Proposed exclusions and disclosure requirements

| Exclusion | Materiality and proposed handling |
|---|---|
| Government/military/unknown pension source | Government omission up to 62.50 per eligible recipient annually at flat rate; military much larger. Guard observable specialized facts. Current source type is unavailable: require narrow private-only confirmation/classification or explicitly approved assumption. Never infer from name or promise automatic detection. Later enhancement: actual source subtractions. |
| Brokerage gains/basis/losses | Common, potentially large, can distort conversions. Guard explicit CAPITAL_GAIN or unreconciled facts; unknown taxable withdrawals require either guard or approved visible legacy approximation. Accurate general gain support is a separately approved financial expansion. |
| Nonqualified Roth, inherited distributions, after-tax IRA basis | Potentially large. Reuse existing unavailable reasons where facts expose missing qualification/basis; document fully taxable traditional and qualified Roth assumptions for ordinary scope. Unknown explicitly flagged facts cannot be treated as eligible. |
| Treasury/municipal bonds | Material for bond-heavy portfolios. Guard represented special sources; disclose ordinary taxable-interest-only assumption where totals are unattributed. Later source classification. |
| Nonresident, part-year, state moves/multistate credits | Large. Full-year single-state assumption in selection/report; refuse represented contrary cases. No timeline exists to detect unreported moves. |
| Itemization, medical/LTC, charity, blindness | Can matter substantially for care costs; charity omission alone up to 25/50 before interactions. Disclose standard-deduction-only approximation; no detailed new tax UI. Explicit unsupported requests cannot masquerade as included deductions. |
| Refundable senior property/rent credit, dependents | Important to low-income households including SS-only. Disclose omitted refunds/dependent benefits; later enhancement. Production tax result cannot represent negative tax. Verify family-credit/basic-deduction zero-liability overlap for ordinary no-dependent cases. |
| Foreign income/MAGI, trusts/estates, business/pass-through, QCD/state basis, unusual retirement, Trump Accounts | Potentially large for affected taxpayers. Structured unsupported result on observable facts; ordinary domestic individual-income scope assumption otherwise. No comprehensive questionnaire proposed. |
| Working-retiree special employment deductions | No complete input coverage; unsupported if explicitly requested/represented; disclose employment scope limitations. |

Guards can inspect only represented facts. Backend limitation strings alone do not satisfy visible disclosure. To prevent silent incorrect results, enforce known conflicts and display the assumptions under which an estimate is available. Pension confirmation is the only potentially necessary new narrow input for pension-inclusive admission; brokerage fields require separate scope approval.

## 7. Minimal implementation design (proposed)

1. Add one ArizonaProductionTaxCalculator implementing StateIncomeTaxCalculator and a small Arizona-only versioned rules resource/repository following Iowa's pattern. No dependency/build changes or generalized hierarchy.
2. Use Illinois AGI/flat and death-aware-age structure, Iowa structured admission/disclosure, existing unavailable contract. Do not copy Illinois retirement exclusions or Iowa's taxable-income bridge. Existing FLAT arithmetic is sufficient; extracting a helper for multiplication alone adds no value.
3. Admit approved full-year domestic Single/MFJ scope, 2026 onward. Refuse incompatible years/statuses, explicit unsupported facts and inconsistent attribution. Use unsupported result with absent estimate, never zero on failure.
4. Start at observed federal AGI. Subtract taxable SS and verified applicable Arizona adjustments/exemptions, then Arizona basic deduction; preserve signed intermediates until statutory floors. Apply .025 to nonnegative taxable base and supported credits. BigDecimal final HALF_UP cents follows current estimate practice; disclose final return-table/whole-dollar differences after instruction review.
5. Calculate Arizona temporary senior subtraction without altering federal behavior. Under domestic MAGI=modeled AGI assumption, per eligible filer during 2026–2028: `max(6000 - .06 * max(MAGI - threshold, 0), 0)`. Multiply by eligible filer count; do not apply phaseout once to a combined 12,000. Use federal MAGI before Arizona SS subtraction. Verify exact worksheet eligibility/caps before release; foreign adjustments unsupported.
6. Future policy approval: smallest approach is nominal verified 2026 Arizona basic deduction, .025 rate and 2,100 age exemption, with explicit statutory-indexing approximation and mandatory 2029 senior sunset. Alternative: model basic deduction indexing using economic assumptions, disclosed as a projection of statutory indexing. Never silently reuse projected federal deduction or freeze temporary relief forever.
7. Only after verification/tests, add AZ enum/registry entry and reuse alphabetical choice/Jackson fields. Add actual visible Arizona disclosures in existing UI/PDF/CSV paths. No new tax calculation in UI or ProjectionEngine.

Existing household and ordinary-income fields suffice for bounded ordinary scope; complete brokerage and specialized pension facts do not. No hypothetical future-state abstraction is justified.

## 8. Roth correctness assessment

Current federal ordinary income includes actual conversion plus ordinary tax-deferred distributions independently. Proposed Arizona retains both, subtracts changed federally taxable SS only and never determines conversion exemption from destination Roth or `isRetirementDistribution()` (which includes conversions).

Conversion can increase taxable SS federally. Arizona removes that changed taxable SS, but federal MAGI used for temporary senior phaseout still rises. State marginal conversion cost therefore is not universally 2.5%: in an active senior phaseout with positive taxable base, an extra non-SS dollar can cost 2.65 cents for one eligible senior or 2.8 cents for two, before SS/MAGI and funding feedback. Taxable-SS changes can further affect MAGI phaseout. Zero-base and fully phased-out cases differ.

Use existing iterative state/federal tax funding and finalization; traditional assets funding taxes generate additional taxable withdrawals. Do not compute conversion cost as conversion times flat rate. Do not add actual conversion again from metadata or conflate RMD with additional income after withdrawal totals.

Bracket fill targets a federal bracket, not an Arizona optimum or a global objective. Existing omission of federal enhanced senior deductions remains a federal precision limit; an accurate state-only subtraction does not repair it. Compatibility overloads defaulting to Michigan must not be used for selected AZ analysis.

Verify actual execution shortfall, ordinary withdrawal plus conversion, opening/projection RMD, authoritative SS, filing status, changing pensions, senior phaseout/sunset, tax funding, annual/lifetime state sums, and no persisted account mutation. Qualified Roth withdrawals remain outside AGI under existing modeled qualification assumptions.

## 9. Integration and regression risks

| Consumer | Existing path and required focused check |
|---|---|
| Deterministic | ProjectionEngine -> TaxFundingCalculator -> production state service. Preserve cash settlement and generic ProjectionYear state result. No separate end-of-year Arizona deduction. |
| Monte Carlo | `app.montecarlo.MonteCarloAnalyzer` uses ProjectionEngine per world. Compare identical deterministic/zero-volatility world, not different stochastic paths. Verify selected state survives copying and unsupported propagation. |
| Longevity weighted | `LongevityWeightedIntegratedStrategyEvaluator`, `LongevityContinuationSession` use ProjectionEngine. Preserve exact second-death ending, opening-estate zero-call path, continuation and probabilities. Age/status adjustments use scenario context. |
| SS analyzers | `IntegratedSocialSecurityStrategyEvaluator` runs full plan; grids/exhaustive/weighted inherit this. SS-only calculator does not use state taxes and stays unchanged. Short integrations suffice; no exhaustive benchmark required. |
| Roth | Explicit state-aware bracket-fill and normal projection use funding feedback; senior phaseout can affect outcomes. No new optimizer objective. |
| Break-even/baselines | Compare projection outcomes; `BreakEvenContextFactory` adds survival context, no tax engine. Verify baseline/current state input capture; do not change baseline isolation semantics. |
| Persistence | StateTaxAssumptions and textual enum code; test AZ save/reload and existing plan defaults. Old binaries cannot be assumed to recognize new enum values. |
| History/freshness | `AnalysisFingerprintService`, `MonteCarloFingerprintService` include planning-assumption input groups. `RetainedProjectionSnapshot.State.capture/restore` stores generic amount/status/limitations. Test state-change invalidation and frozen historical exports. Tax-rule resource-version invalidation was not certified; verify before claiming freshness after rule updates. |
| PDF/UI/CSV | ProjectionYear.getStateIncomeTax and generic jurisdiction table support AZ. ProjectionPdfExporter around 719–724 and ProjectionCsvExporter have Iowa-specific disclosure branches: AZ requires explicit equivalent visibility. MI detail columns remain MI-only. Verify frozen Monte Carlo/history disclosure too. |
| Unsupported | StateTaxUnavailableException and structured unavailable reasons must survive every boundary; no fabricated zeros, ranks or break-even results. |

Reusable tests (under `src/test/java/com/daviddunn/retirementplanner/`): `domain/projection/StateTaxFundingFeedbackTest`, `StateTaxUnavailableProjectionTest`; `domain/tax/state/ProductionStateTaxServiceTest`, `StateTaxUnavailableContractTest`, `IowaProductionTaxCalculatorTest`, `PennsylvaniaProductionTaxCalculatorTest`; `persistence/IowaTaxRulesRepositoryTest`, `StateTaxDatasetRepositoryTest`; `app/socialsecurity/IowaIntegratedAnalysisTest`, `IllinoisIntegratedAnalysisTest`; `app/montecarlo/IowaMonteCarloIntegrationTest`, `StateTaxUnavailableMonteCarloBoundaryTest`; `ui/wizard/StateTaxWizardIntegrationTest`; `ui/StateTaxUnavailableMessageTest`; `ui/montecarlo/MonteCarloProjectionAssociationTest`; `app/montecarlo/MonteCarloHistoricalPdfFidelityTest`. Reuse focused patterns, not expensive suites during this audit.

## 10. Proposed golden-value tests and acceptance

Use independently hand-calculated statute/instruction worksheets, not calculator-generated expectations. Let D(S)/D(J) be final verified 2026 Arizona basic deduction. Numerical examples below use **provisional** 16,100/32,200 solely as review arithmetic, not approved Arizona golden constants. No special credits/deductions; age exemption 2,100 each, enhanced senior subtraction where eligible. Federal SS inputs must have separate instruction-backed expected values.

| # | Scenario | Independent expected assertion |
|---|---|---|
| 1 | Single 70, SS 30,000 only | Arizona liability zero; excludes any refundable property/rent benefit from this assertion. |
| 2 | MFJ 70/68, SS 40,000, IRA 60,000 | `.025*max(60000-4200-12000-D(J),0)`; provisional 290.00. MAGI below phaseout. |
| 3 | Single 60, IRA 50,000 + conversion 20,000 | `.025*(70000-D(S))`; provisional 1,347.50; without conversion 847.50; incremental 500.00. Add execution shortfall/two-owner conversion cases. |
| 4 | Single 70, private pension 45,000 + SS 25,000 | `.025*max(45000-2100-6000-D(S),0)`; provisional 520.00. Specialized pension variant tests admission. |
| 5 | Single 60, IRA 50,000 + bank interest 5,000 | `.025*(55000-D(S))`; provisional 972.50. Separate gain/dividend/bond variants must guard or disclose approved approximation. |
| 6 | Single 70, qualified Roth withdrawal 40,000 only | Zero. Paired taxable conversion 40,000 produces provisional 395.00, proving conversion is not exempt. Nonqualified/basis-required variant unavailable. |
| 7 | RMD commencement, MFJ seniors, SS 40,000, total distribution 60,000 | Same tax as #2; RMD/ordinary/opening labels do not duplicate totals. Add projected prior-Dec-31 balance and pre-satisfied RMD plus conversion checks. |
| 8 | Death-year MFJ -> Single, non-SS 60,000, both already 70 | With fixed D: death-year provisional 290.00; survivor Single next year 895.00. Add deceased-before-65 and owner reversal. |
| 9 | Single 70, ordinary 60,000, 2028 -> 2029, controlled fixed D | Sunset: provisional 895.00 -> 1,045.00; 150.00 difference. Separate indexed-future-policy and pre-2026 rejection tests. |
| 10 | Specialized/unknown pension, explicit gains, missing basis, unsupported transaction | Absent estimate plus structured reason for observable missing facts. Do not fabricate source classification; confirmation policy must be approved first. |
| 11 | AZ selection and persistence | Stable code/method survive JSON reload, snapshots and baselines; existing state defaults/goldens unchanged. |
| 12 | Deterministic vs Monte Carlo | Identical isolated plan/world/deaths/inflation/horizon -> identical annual federal/state taxes and funding withdrawals. Few controlled trials only. |
| 13 | Mixed ages 64/65 -> 65/66 | Correct per-filer exemptions/senior subtraction, exact birthday convention, no deceased later birthday. |
| 14 | MAGI phaseout boundaries | 75,000/75,001 Single; 150,000/150,001 MFJ; full exhaustion; one/two eligible seniors. Hand-calculate per-filer formula and independently verify rounding. |
| 15 | Conversion + SS + funding | Independently recompute taxable SS, MAGI senior phaseout and funded-tax fixed point; annual/lifetime state totals and household cash conservation. |
| 16 | Taxable annuity/reporting | Taxable receipt portion once, basis excluded; displayed/PDF/history state amount and assumptions match frozen projection. |

For implementation: domain/rule/admission tests before behavior changes; targeted funding/Roth/persistence/short MC and integrated tests; existing state goldens; then full Maven suite when practical. All Maven commands must use IntelliJ bundled Maven and `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`. Do not change financial behavior to satisfy tests. No Maven or expensive analyses were run for this documentation-only audit.

## 11. Concise Phase 6E-B proposal

After explicit approval and final source resolution: versioned Arizona rules; focused calculator with senior adjustments/admission; independently sourced tests; AZ enum/registry/round trip; visible disclosures in existing UI/report paths; short tax-funding/conversion/integrated regressions and existing state goldens. Moderate implementation effort; accurate general brokerage taxation would materially expand scope. No federal changes, analyzer redesign, new objectives, dependency changes or broad refactor.

## 12. Explicit decisions requiring approval

1. Authorize Phase 6E-B; this audit does not begin implementation.
2. Approve bounded full-year domestic Single/MFJ ordinary-income scope and existing federal-model limitations. Recommended: calculate correct state-only temporary senior subtraction rather than omit it.
3. Resolve final 2026 Arizona basic deduction and instruction/birthday/rounding details before registration. Provisional values cannot become verified constants by assumption.
4. Choose future deduction policy: nominal 2026 planning constants (smallest recommendation) versus modeled indexing, with clear statutory-indexing approximation and required senior sunset.
5. Choose pension admission: minimal source classification/private-only confirmation or explicitly disclosed private-only assumption. Unknown records cannot be reliably auto-detected.
6. Choose brokerage scope: guard unsupported accounts/gain cases, approve visible legacy approximation, or separately authorize accurate basis/gain/source work. No complete brokerage promise from existing fields.
7. Approve disclosed exclusions for refunds/dependents, charity/itemization, special bonds and uncommon arrangements, with guards on observable conflicts. No detailed tax-preparation UI recommended.

## Verification record

Initial `git status --short`: clean. Read-only source/Git inspection and official web research performed. Only this Markdown deliverable created. No production/configuration/test edits, registration changes, staging, commits or pushes. Final status and diff checks are reported at completion. Work stops pending Phase 6E-B approval.
