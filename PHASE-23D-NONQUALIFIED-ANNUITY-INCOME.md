# Phase 23D — Nonqualified Annuity Income

November 7, 2026. Implementation, focused tests and full regression completed. Native Windows acceptance pending.

## 1. Executive summary

Implemented the approved already-purchased nonqualified single-life income annuity and bounded NIIT Option A. The authoritative conditional-benefit evaluator supplies one immutable gross/taxable/recovery result to existing cash settlement and every federal tax-funding/Roth trial. Distinct authenticated capabilities preserve old result meanings. No annuity asset, purchase, pricing engine or permanent exclusion percentage was introduced.

Above-threshold calculations are supported, including the independent $218.88 NIIT control. This is not the NIIT-unexposed-only alternative. Unknown investment-income sources produce typed unsupported admission. Illinois and existing no-general-individual-income-tax models are admitted; Michigan/Iowa/Pennsylvania remain blocked for this subtype. No-annuity financial semantics are unchanged.

The final source audit found existing nonqualified cash/retained growth lacks a recognized-interest/realized-gain split. Annuity execution rejects nonzero returns with initial cash accounts or beginning retained assets. It never silently exempts that growth, changes inputs or discards worlds. Qualified-account market growth remains supported when unknown nonqualified balances are absent. This material limitation is disclosed in UI/reporting.

## 2. Checkpoint A decisions

Selected a closed ConditionalBenefitDefinition extension, not a second income/insurance engine. The user approved bounded NIIT Option A. Architecture section 17 contains the pre-production addendum; section 18 contains final contracts. Earlier stopped-checkpoint sections are historical. No incompatible snapshot migration was necessary.

Reuse includes existing source identity, death view, tax funding, snapshots, capabilities, financial normalization and Income Sources plan-change callbacks. Old documents omit new optional fields.

## 3. Verified IRS tax-method boundary

The supported post-1986 fixed-payment commercial annuity uses insurer/tax-document-validated General Rule excluded dollars per full payment and finite verified remaining investment. Aledrix does not derive an insurer quote, expected return or exclusion ratio, or certify legal qualification. The ordinary commercial nonqualified contract is not assigned the Simplified Method. [IRC 72](https://www.govinfo.gov/content/pkg/USCODE-2024-title26/html/USCODE-2024-title26-subtitleA-chap1-subchapB-partII-sec72.htm), [IRS Publication 939, revision December 2025](https://www.irs.gov/publications/p939), [Publication 575, 2025](https://www.irs.gov/publications/p575).

Tax starting date, acquisition and first cash date are distinct. Initial restrictions include full month-aligned payments/opening, fixed nominal payments, annuitant age at least 59½ at first cash and no unmodeled early-distribution exception. Form 1099-R NIIT reporting does not itself establish contract eligibility. [Topic 411](https://www.irs.gov/taxtopics/tc411), [2026 Form 1099-R instructions](https://www.irs.gov/instructions/i1099r).

## 4. Supported contract subset

Already acquired personal after-tax single-life annuity; annuitant equals named household recipient; fixed monthly gross/documented exclusion; no separate liquid asset, escalation, end-date guarantee, survivor/refund, qualified funding or future purchase. Opening is the first of a month in 2026 or later. Acquisition precedes opening; post-1986 tax start is no later than first cash.

The closed qualification also asserts domestic individual standard-deduction NIIT treatment, no foreign/CFC/PFIC adjustments, nonresident/special elections, investment deductions/loss carryovers or unresolved sources. This records user-supported planning facts, not a tax opinion.

## 5. Exact financial input contract

ConditionalBenefitDefinition adds NONQUALIFIED_INCOME_ANNUITY, NONQUALIFIED_GENERAL_RULE_FIXED_EXCLUSION and optional NonqualifiedAnnuityTerms. Old constructors and omitted-field output remain. Recipient is annuitant; FIXED_DATE identifies first cash; pattern is MONTHLY_RECURRING; destination remains household retained nonqualified cash.

Terms freeze acquisitionDate, taxAnnuityStartingDate, excludedPerPayment, originalInvestment, openingInvestment, basisAsOf and qualification. Original investment means adjusted investment at tax start; opening remaining investment is separately verified. Future commencement requires opening investment equal original because no prior recovery is represented. Positive opening investment requires positive documented exclusion. Exact-cent bounded BigDecimal amounts enforce excluded ≤ gross and opening ≤ original. Labels/UUIDs retain existing audit versus financial identity rules.

Aledrix cannot identify an unrelated asset as this contract without a linkage. The acknowledgment requires no separately represented contract asset and warns against double counting; other accounts/assets are never silently deleted.

## 6. Payment timing and survival

Full eligible months start at later(first cash, opening) and stop before the annuitant's January 1 death year. Other spouse death does not stop payments. July gives six months. Pre-opening payments are not replayed or used to recover verified opening basis again. Zero-payment years retain immutable basis/NIIT evidence. No new mortality draw, legacy pension timing change or post-death refund/deduction occurs.

## 7. Basis recovery and exhaustion

For P prior eligible payments after opening, N current payments, B0 frozen opening investment and E excluded/payment: opening=max(B0−E×P,0); recovery=min(opening,E×N); closing=opening−recovery. Gross=monthly×N; taxable=gross−recovery. AnnuityBasisRecovery validates terms, counts and arithmetic. The last recovery payment can exclude less than E; later payments are fully taxable. No mutable source balance, paid flag or cross-trial basis exists.

## 8. Gross/taxable/excluded reconciliation

| Literal control | Gross | Recovery | Taxable | Closing basis |
|---|---:|---:|---:|---:|
| 12 × $1,200, E=$720, opening=$10,000 | $14,400 | $8,640 | $5,760 | $1,360 |
| Following 12 payments | $14,400 | $1,360 | $13,040 | $0 |
| Third year | $14,400 | $0 | $14,400 | $0 |
| July commencement | $7,200 | $4,320 | $2,880 | $5,680 |
| January 1 annuitant death | $0 | $0 | $0 | Unchanged, not an asset |

Monthly exhaustion independently verifies January $720, February $640, March $0. Gross=taxable+recovery; total recovery never exceeds opening investment. These arithmetic oracles do not establish a real contract's legal qualification.

## 9. Federal tax and bounded NIIT

The distinct NONQUALIFIED_ANNUITY source carries taxable ordinary annuity income and excluded recovery. Gross enters cash once; recovery is not AGI, pension, IRA income, interest, life principal or a negative deduction. New annuity-enabled federal tax includes ordinary bracket tax plus NIIT in all funding/Roth trials.

NIIT = 3.8% × min(supported NII, max(MAGI−threshold,0)). Thresholds: MFJ/qualifying surviving spouse $250,000; MFS $125,000; Single/HOH $200,000. They are not indexed and reside in versioned niit-2013-v1.json, not ProjectionEngine. Under the admitted no-special-adjustment assertion, AGI equals NIIT MAGI. Standard deduction is not subtracted from NII. [IRC 1411](https://www.govinfo.gov/content/pkg/USCODE-2024-title26/html/USCODE-2024-title26-subtitleA-chap2A-sec1411.htm), [Form 8960 instructions, 2025, lines 1–3, 13–17 and Part II](https://www.irs.gov/instructions/i8960).

| Audited modeled source | New execution treatment |
|---|---|
| Taxable commercial annuity | Included in NII and AGI |
| Explicit attributed personal taxable interest | Included; source/scalar totals must reconcile |
| Qualified IRA/employer-plan distributions, RMDs, conversions | Taxable amount affects MAGI; qualified account evidence excludes it from NII |
| Social Security | Existing taxable benefit rules; not NII |
| Qualifying Phase 23B principal | Not taxable ordinary income or NII |
| Known principal-only cash withdrawals | Allowed only under supported no-growth evidence |
| Generic Pension | Reject: federal qualified-plan/NIIT facts unavailable |
| Other taxable conditional income | Reject: investment/business/other identity unavailable |
| DIVIDEND, CAPITAL_GAIN, OTHER sources | Reject: complete recognition/basis/tax mapping unavailable |
| Positive brokerage balance | Reject before execution: no realized-gain/basis split |
| Initial cash/retained growth | Reject nonzero return with relevant positive balances |
| Investment deductions/carryovers/special MAGI facts | Outside qualification; never silently estimated |

A direct attributed-interest control verifies $5,760 annuity + $10,000 interest at MFJ MAGI $310,000 yields $598.88 NIIT. $50,000 IRA distribution and $244,240 conversion affect MAGI only. No NII is inferred from generic AGI; no new public interest/brokerage engine was added.

Initial positive cash conservatively requires zero returns throughout execution, even after cash is spent. Without it, qualified-account growth is allowed until retained surplus would require unknown taxable growth. Such a world raises NiitUnsupportedInputException and aborts; it is not dropped or converted to portfolio failure. Inputs are never automatically zeroed. This restricts, but does not eliminate, useful above-threshold and stochastic annuity planning.

## 10. State admission matrix

| State/model | New annuity policy | Verification |
|---|---|---|
| Illinois | Taxable federal component in state base; no pension subtraction | Literal state/federal control |
| AK, FL, NV, NH, SD, TN, TX, WY | Existing no-general-income-tax models/year limits | Existing implementation; FL/TX literal controls |
| Michigan | Reject | Senior lifetime-annuity qualification missing |
| Iowa | Reject | Commercial source reconciliation not implemented |
| Pennsylvania | Reject | State source/basis mapping not implemented |

IL does not assign a commercial annuity a qualified-retirement subtraction. MI's possible senior lifetime-annuity treatment needs separate facts. IA expressly excludes nonqualified annuities from its retirement exclusion. These legal distinctions do not authorize an unverified calculator fallback. [Illinois Pub-120](https://tax.illinois.gov/research/publications/pubs/retirement-income.html), [Michigan Treasury](https://www.michigan.gov/taxes/iit/tax-guidance/tax-situations/retirement-and-pension-benefits/qualified-pension-distribution-requirements/individual-plans), [Iowa Revenue, March 13, 2025](https://revenue.iowa.gov/taxes/tax-guidance/individual-income-tax/retirement-income-tax-guidance), [PA Revenue annuity FAQ](https://revenue-pa.custhelp.com/app/answers/detail/a_id/368/related/1).

Literal 2026 control: $60,000 gross − $8,640 recovery = $51,360 taxable annuity; $294,240 conversion gives $345,600 AGI. IL subtracts modeled $7,850 personal/senior exemptions from annuity income, not a pension exclusion: $43,510 × 4.95% = $2,153.75. FL/TX state tax is zero. Each has $1,951.68 NIIT and $62,363.68 federal total, proving state-independent federal treatment. [IL 2026 exemption guidance](https://tax.illinois.gov/questionsandanswers/answer.851.html).

## 11. Medicare IRMAA

Only taxable federal annuity income directly enters existing same-year MAGI; recovery is not municipal interest or an addback. NIIT is federal tax, not Part B/D premium. Existing eligibility, tables and timing are unchanged. MFJ $300,000 AGI control has separate $10,639.20 Medicare and $49,686.88 federal tax. A single-person SS control verifies taxable SS $7,696, AGI $33,456, federal $1,834.72 and Medicare $2,434.80. Indirect withdrawal/conversion effects remain legitimate.

## 12. Roth and RMD effects

Every TaxFundingCalculator/Roth trial receives the same immutable source and NIIT activation. No one-pass bracket shortcut. $294,240 conversion + $5,760 annuity taxable = $300,000 AGI: ordinary tax $49,468 + $218.88 NIIT = $49,686.88. An independent funded-bracket control verifies approximately $248,313.92 conversion/$45,926.08 tax-funding withdrawal with cent-scale iterative tolerance. Repeated annual conversions verify NIIT $218.88/$495.52/$547.20 as basis exhausts.

A separate age-80 $1,000,000 IRA control verifies $49,504.95 RMD. Nonqualified annuity income neither creates nor satisfies it. Account ownership, conversion eligibility, RMD formulas/prior-year bases and withdrawal ordering are unchanged.

## 13. Household cash settlement

Gross is counted once through guaranteed funds. Recovery/tax attribution are not additional deposits. Existing HouseholdCashSettlement alone retains actual surplus; no live Account.deposit or annuity asset is introduced. Zero-return low-income couple: $1,500,000 + $14,400 − $4,869.60 Medicare = $1,509,530.40 ending assets. High-MAGI control ends at $1,454,073.92 after $49,686.88 federal/$10,639.20 Medicare. Saved sources/uses and balances reconcile; retries neither duplicate cash nor recover basis again.

## 14. Deterministic/integrated support

Admitted single/couple ordinary/baseline projections and integrated individual, Quick, complete strategy search, grids/heat maps and comparisons use the common engine. Copies retain exact definitions and source facts. Tests exercise Quick/grid/complete execution and literal financial controls. SS-only rankings do not acquire annuity value. Unsupported inputs cannot publish a successful partial result merely because ProjectionEngine can evaluate another source.

## 15. Longevity-weighted support

Existing scenario deaths, probabilities, discounting, order and exact pre-second-death horizon remain. Basis is independently derived for each scenario. Accepted benefit-bearing reference execution remains where prefix/continuation reuse is unproven. Tests compare reference/continuation results and literal weighted estates $1,254,660.80/$1,283,460.80. Opening second death creates no annuity cash/refund/basis asset. Unknown growth aborts rather than dropping a scenario. No terminal horizon or mortality change.

## 16. Eight generated Monte Carlo modes

Standalone/paired × single/couple × fixed/longevity are covered, with zero and 0.25 ordinary-expense volatility. Paired tests exercise candidate asymmetries, identical economics, alternate annuitant, deferred payments, exhausted basis, insurance/premium coexistence and repeat seeds.

Fixed-market tests use qualified accounts, no initial cash and sufficient spending over the bounded horizon to avoid unknown retained growth. Longevity equality controls use zero market volatility and a fully funded IRA while mortality/expense worlds remain stochastic. These are explicit test conditions, never automatic user-input changes. All modes retain source/growth admission. Caller-supplied worlds/paths remain guarded. No reducer independently adds annuity cash.

## 17. Random-stream preservation

No RNG, world generator, mortality sampler, expense sampling/correspondence or expense dimension/version 6/1 source changed. Fixed annuity/basis adds no draw. Seed/index/year/shared-world protocols remain. Tests verify reproducibility and zero deltas for identical candidates. Original financial goldens were not regenerated.

## 18. Historical provenance/version matrix

| New contract | Value | Preservation |
|---|---|---|
| Input normalization | NONQUALIFIED_ANNUITY_NIIT_INPUTS_V3 | No-annuity normalization unchanged |
| Deterministic | deterministic-nonqualified-annuity-niit-guarded-v3 | B2/B3/life/premium original tags unchanged |
| Analyzer capability | V4 / ANALYZER_NONQUALIFIED_ANNUITY_NIIT_GUARDED_V4 | Original V1/V2/V3 |
| Analyzer algorithm | analyzer-nonqualified-annuity-niit-guarded-v4 | No global algorithm-map bump |
| MC standalone | Payload V10 / fingerprint V11 | Original payload V1–V9/fingerprint V1–V10 meanings |
| MC paired | Payload V11 / fingerprint V12 | Disjoint original combinations preserved |
| NIIT facts | DOMESTIC_RECOGNIZED_INVESTMENT_INCOME_1411_V1 | Absent from old results |
| Envelope/complete/retained | Existing V1/V1/V1–V2 | No incompatible schema replacement |

Optional annuity/NIIT evidence is omitted on old documents. A local receipt reader defaults only the new optional field; original required/duplicate/unknown-field strictness remains. Retained Income adds optional NIIT facts. One zero-gross/taxable NIIT_EVIDENCE source retains final tax facts without another cash source. Old constructors and exact restore/recapture remain supported.

Capability binds original family/protocol/candidate documents, definitions, qualification/method/basis anchor, fingerprints and evidence. Validators check receipt arithmetic/identity, recovery continuity, NIIT MAGI/status/components, unsupported growth and cash/tax reconciliation without reprojecting. Missing/mixed/tampered/wrong-family/candidate/version/fingerprint combinations reject. Existing pension-selection=death-selected-v2 remains. Interim unguarded prototypes cannot authenticate final guarded semantics.

An isolated old-reader test compiles archived accepted pre-feature definition/deserializer classes: old benefits load; annuity inputs reject instead of silently becoming all-taxable ordinary income. These archives are new resources, not altered historical fixtures.

## 19. Current/Baseline isolation and history

Tests include Current-only/Baseline-only, identical, different gross/opening basis/annuitant/commencement, fully taxable exhausted basis and coexisting life principal/premiums. Shared worlds do not share mutable basis or definitions. In mixed comparisons, only annuity-bearing candidates activate the new NIIT semantics; no-annuity candidates deliberately retain their accepted legacy tax calculation. This is not a whole-application NIIT retrofit. Label/UUID-only edits preserve economic identity; material edits change it. Original exact documents retain label/UUID/order.

Production repository/controller tests cover Save As, restart and swaps both ways. A replacement engine throws if history invokes it, establishing no historical recalculation. Old taxable results remain taxable; old/new annuity results retain original payment/method/basis and NIIT after live edits. Existing atomic save, Retry Save, cancellation/failure and previous-result tests are included in the full suite; not every failure path received a separate annuity-specific manual test.

## 20. UI implementation/validation

Income Sources / Conditional Benefits adds a specialized Nonqualified Income Annuity modal. Inputs: description, annuitant/recipient, monthly gross, first cash, acquisition, tax start, excluded/payment, adjusted original investment, verified opening remaining investment, qualification. Existing required TextFlow markers/help, themes, scroll body, fixed footer and mutation callback are reused.

Detached add/edit/cancel preserves committed state and stable edit UUID; duplicate creates new UUID/fresh acknowledgment. Financial changes clear acknowledgment; label edits preserve economic fingerprints. An opening-date change clears stale qualification and shows original verification date; basis is never silently rolled. Field-specific missing/invalid amount/date feedback preserves entered values. Domain validation remains authoritative.

State/source/growth/no-separate-asset limits are disclosed. Valid but unexecutable definitions remain saveable. No quote, purchase, insurer account, qualified-annuity or new pension control was added.

## 21. Current/historical reporting and inspected previews

Yearly Details/PDF show original gross, taxable amount, recovery, closing investment and separate NIIT included in federal tax. Frozen analyzer assumptions preserve method, original/opening investment, dates and qualification. Zero-payment monthly income is not called a lump sum; future cash is not described as received.

Actual PDFBox current/historical exports were rendered and text-checked. Inspected page 17 displays first-year $14,400/$5,760/$8,640/$1,360, subsequent exhaustion, and $218.88 NIIT/$300,000 MAGI/$250,000 threshold. Artifacts: target/phase23d/reporting/annuity-current.pdf, annuity-historical.pdf and annuity-page-0.png through annuity-page-16.png. The historical PDF was independently rendered with the existing PDFBox dependency and its last page inspected at target/phase23d/reporting/annuity-historical-last-page.png; it preserves the same original basis and NIIT facts. Original frozen history survives live edits without evaluator/engine calls.

Actual Light/Dark JavaFX top/bottom scenes at 900×650 and 1180×820 are in target/phase23d/previews. Inspected representative scenes show readable controls, scroll access and fixed visible Save/Cancel; geometry is asserted. Existing charts/export flows remain. This is automated Windows JavaFX verification, not user native acceptance at every DPI/accessibility setting.

## 22. Exact changed files

Generated inventory follows. No preexisting test, financial golden, build configuration, stochastic generator, prior-phase report or existing tax table changed. The two initially untracked Phase 23D reports were updated in place.

- Modified: src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java
- Modified: src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloFingerprintService.java
- Modified: src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshot.java
- Modified: src/main/java/com/daviddunn/retirementplanner/app/projection/ConditionalBenefitDisclosures.java
- Modified: src/main/java/com/daviddunn/retirementplanner/app/projection/ConditionalBenefitExecutionSemantics.java
- Modified: src/main/java/com/daviddunn/retirementplanner/app/projection/ConditionalBenefitFinancialInputs.java
- Modified: src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ConditionalBenefitAnalysisExecution.java
- Modified: src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java
- Modified: src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitAdmission.java
- Modified: src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitDefinition.java
- Modified: src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitDefinitionDeserializer.java
- Modified: src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitEvaluator.java
- Modified: src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitReceipt.java
- Modified: src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefits.java
- Modified: src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java
- Modified: src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java
- Modified: src/main/java/com/daviddunn/retirementplanner/domain/tax/FederalTaxCalculation.java
- Modified: src/main/java/com/daviddunn/retirementplanner/domain/tax/FederalTaxCalculator.java
- Modified: src/main/java/com/daviddunn/retirementplanner/domain/tax/TaxFundingCalculator.java
- Modified: src/main/java/com/daviddunn/retirementplanner/domain/tax/TaxIncomeAttribution.java
- Modified: src/main/java/com/daviddunn/retirementplanner/domain/tax/state/PennsylvaniaProductionTaxCalculator.java
- Modified: src/main/java/com/daviddunn/retirementplanner/domain/tax/state/iowa/IowaTaxBaseBridge.java
- Modified: src/main/java/com/daviddunn/retirementplanner/ui/benefits/ConditionalBenefitPresentation.java
- Modified: src/main/java/com/daviddunn/retirementplanner/ui/dialogs/ConditionalBenefitDialog.java
- Modified: src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/FrozenConditionalBenefitDetails.java
- Modified: src/main/java/com/daviddunn/retirementplanner/ui/summary/ProjectionYearDetailsPane.java
- Modified: src/main/java/com/daviddunn/retirementplanner/ui/views/ConditionalBenefitsPane.java
- New/initially untracked: PHASE-23D-A-NONQUALIFIED-ANNUITY-ARCHITECTURE.md
- New/initially untracked: PHASE-23D-NONQUALIFIED-ANNUITY-INCOME.md
- New/initially untracked: src/main/java/com/daviddunn/retirementplanner/domain/benefit/AnnuityBasisRecovery.java
- New/initially untracked: src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitReceiptReader.java
- New/initially untracked: src/main/java/com/daviddunn/retirementplanner/domain/benefit/NonqualifiedAnnuityAdmission.java
- New/initially untracked: src/main/java/com/daviddunn/retirementplanner/domain/benefit/NonqualifiedAnnuityTerms.java
- New/initially untracked: src/main/java/com/daviddunn/retirementplanner/domain/tax/NetInvestmentIncomeTaxCalculation.java
- New/initially untracked: src/main/java/com/daviddunn/retirementplanner/domain/tax/NetInvestmentIncomeTaxCalculator.java
- New/initially untracked: src/main/java/com/daviddunn/retirementplanner/domain/tax/NetInvestmentIncomeTaxRules.java
- New/initially untracked: src/main/java/com/daviddunn/retirementplanner/domain/tax/NiitUnsupportedInputException.java
- New/initially untracked: src/main/java/com/daviddunn/retirementplanner/ui/dialogs/NonqualifiedAnnuityDialog.java
- New/initially untracked: src/main/resources/rules/niit-2013-v1.json
- New/initially untracked: src/test/java/com/daviddunn/retirementplanner/app/montecarlo/NonqualifiedAnnuityMonteCarloTest.java
- New/initially untracked: src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/NonqualifiedAnnuityIntegratedTest.java
- New/initially untracked: src/test/java/com/daviddunn/retirementplanner/domain/benefit/NonqualifiedAnnuityEvaluatorTest.java
- New/initially untracked: src/test/java/com/daviddunn/retirementplanner/domain/benefit/NonqualifiedAnnuityOldReaderTest.java
- New/initially untracked: src/test/java/com/daviddunn/retirementplanner/domain/benefit/NonqualifiedAnnuityPersistenceTest.java
- New/initially untracked: src/test/java/com/daviddunn/retirementplanner/domain/projection/NonqualifiedAnnuityAcceptanceFixturesTest.java
- New/initially untracked: src/test/java/com/daviddunn/retirementplanner/domain/projection/NonqualifiedAnnuityFinancialTest.java
- New/initially untracked: src/test/java/com/daviddunn/retirementplanner/domain/projection/NonqualifiedAnnuityHistoryTest.java
- New/initially untracked: src/test/java/com/daviddunn/retirementplanner/domain/tax/NetInvestmentIncomeTaxCalculatorTest.java
- New/initially untracked: src/test/java/com/daviddunn/retirementplanner/ui/dialogs/NonqualifiedAnnuityDialogTest.java
- New/initially untracked: src/test/resources/phase23d/legacy/ConditionalBenefitDefinition.java.txt
- New/initially untracked: src/test/resources/phase23d/legacy/ConditionalBenefitDefinitionDeserializer.java.txt
- New/initially untracked: src/test/resources/phase23d/legacy/README.md

## 23. Focused tests

Final focused run: **94 tests / 94 passed / zero failures, errors or skips**, 32.128 seconds. Log: target/phase23d/final-release-focused.log. Selected NetInvestmentIncomeTaxCalculatorTest, NonqualifiedAnnuity*Test and accepted ConditionalBenefitPersistenceTest, ConditionalBenefitLegacyContractTest, LifeInsuranceFinancialTest, ContractualPremiumFinancialTest.

Sixty-four new tests/invocations cover filing thresholds/NIIT limits, interest/source completeness, finite basis/reentrancy, literal tax/Medicare/RMD/Roth/cash, both deaths, integrated/weighted/eight MC modes, identity, strict legacy/new capabilities, persistence/history, GUI/PDF and old-reader rejection. Earlier development fixture failures were corrected without weakening legacy assertions/goldens.

## 24. Complete Maven regression

**Final guarded-code run: 4,656 total / 4,610 passed / 46 existing skips / zero failures/errors. BUILD SUCCESS, exit 0. Elapsed 15:05 minutes; completed November 7, 2026 at 15:10:46 EST.** This includes all 4,592 original tests plus 64 new tests/invocations, without added skips or changed old financial expectations.

Final command uses Java 25, required IntelliJ bundled Maven, local .codex-m2/repository and -DargLine=-Xmx4g, plain test with no added exclusions. Starting accepted baseline: 4,592 total / 4,546 passed / 46 existing skips. An intermediate pre-final-guard full run passed 4,647 total, zero failures/errors, 46 skips; it is development evidence, not release verification. Final log: target/phase23d/final-full-regression.log. Existing skipped-test evidence is recorded in target/phase23d/final-existing-skips.json: 46 skips across 33 existing classes.

## 25. Native fixture paths

- C:\Users\david\IdeaProjects\retirement-planner\target\phase23d\acceptance-9335981206244707464\01-single.json
- C:\Users\david\IdeaProjects\retirement-planner\target\phase23d\acceptance-9335981206244707464\02-primary-annuitant.json
- C:\Users\david\IdeaProjects\retirement-planner\target\phase23d\acceptance-9335981206244707464\03-spouse-annuitant.json
- C:\Users\david\IdeaProjects\retirement-planner\target\phase23d\acceptance-9335981206244707464\04-july-commencement.json
- C:\Users\david\IdeaProjects\retirement-planner\target\phase23d\acceptance-9335981206244707464\05-basis-exhaustion.json
- C:\Users\david\IdeaProjects\retirement-planner\target\phase23d\acceptance-9335981206244707464\06-exhausted-fully-taxable.json
- C:\Users\david\IdeaProjects\retirement-planner\target\phase23d\acceptance-9335981206244707464\07-life-principal.json
- C:\Users\david\IdeaProjects\retirement-planner\target\phase23d\acceptance-9335981206244707464\08-contractual-premium.json
- C:\Users\david\IdeaProjects\retirement-planner\target\phase23d\acceptance-9335981206244707464\09-current-baseline.json
- C:\Users\david\IdeaProjects\retirement-planner\target\phase23d\acceptance-9335981206244707464\10-niit-218-88.json
- C:\Users\david\IdeaProjects\retirement-planner\target\phase23d\acceptance-9335981206244707464\11-qualified-market-risk.json

Production JSON save/load creates only synthetic files under a unique ignored target/phase23d/acceptance-* directory. Earlier fixtures are preserved. Pointer: target/phase23d/latest-acceptance-paths.txt. Fixture 09 stores actual completed baseline/current associations. Files 01–10 use zero expected return/cash balances and require zero market volatility for generated execution; fixture 11 uses qualified assets/spending for a bounded three-year fixed market-risk example. Accompanying README gives assumptions and arithmetic. These are not real insurer/legal documents.

## 26. Native Windows acceptance checklist — pending

1. Open 01/02/03; inspect all annuity inputs in Income Sources. Test edit/Cancel/window-close, save a material change, label-only edit and duplicate/fresh acknowledgment.
2. Change opening date; verify basis must be reverified. Inspect required-field/date/amount/state/source feedback.
3. Run 05: first year gross $14,400/recovery $8,640/taxable $5,760/remaining $1,360; second recovery $1,360/taxable $13,040; third fully taxable. Run 04 for six-month $7,200/$4,320/$2,880.
4. Verify annuitant death stops payments; other death does not. No refund, annuity asset or IRA RMD satisfaction appears.
5. Run 10: $218.88 NIIT in federal tax, separate from Medicare. Check cash reconciliation.
6. Verify independent Phase 23B/23C behavior in 07/08; swap Current/Baseline in 09 both ways.
7. Run bounded Quick/grid/weighted and small generated fixed/longevity MC with documented admissible settings, zero/positive ordinary expense volatility and candidate differences. Fixture 11 demonstrates qualified market risk.
8. Try positive brokerage, unknown retained growth, generic pension, other taxable conditional income or MI/PA/IA: expect unsupported explanation, never successful zero-NIIT/partial-world results. Inputs must remain unchanged.
9. Save/Save As/restart, inspect old/new history, edit live assumptions, swap both ways and export historical PDFs. Original numbers/method/basis must remain frozen without calculation.
10. Inspect Light/Dark at 900×650/1180×820, tab traversal/DPI/footer ownership, PDF View/Save/Cancel/default directory and previous-result retention on cancellation/failure/Retry Save.

Native acceptance is not claimed. Further phases require user acceptance/authorization.

## 27. Performance/resources

Bounded prefix arithmetic over existing maximum 100 definitions avoids event logs, mutable per-payment/trial ledgers and whole-plan serialization inside tax loops. No per-trial receipt paths retained after reduction. Existing snapshot/compression/numeric/trial/horizon/cancellation limits remain. Focused run took 32.128 seconds including GUI/PDF/compiler/MC work; this is not production-scale performance evidence. Weighted reference execution intentionally remains slower where reuse is unproven.

## 28. Git state/preservation

Starting branch main; HEAD 866b2f7d0fdf25f449b63e851e667866986bc1c3; index empty; only the two Phase 23D reports initially untracked. All 1,528 tracked SHA-256 values were captured in target/phase23d/starting-hashes.json before implementation. Accepted Phase 23C work was present.

Final branch/HEAD remain unchanged; index empty. Hash comparison identifies exactly the 27 in-scope tracked production modifications listed in section 22; the other 1,501 original tracked files are unchanged. No original test/golden, build/dependency file, prior-phase report, existing rule dataset or stochastic source changed. Twenty-five new/initially untracked paths are listed in the inventory, including both checkpoint reports and the archived old-reader sources. git diff --check and explicit new-file whitespace/conflict-marker checks pass. Ignored target/phase23d holds only synthetic fixtures, previews, logs and verification records.

No stage/commit/push/reset/clean/revert/history rewrite or historical-artifact regeneration occurred. Only in-scope local changes and ignored synthetic verification artifacts were produced. No real user plans accessed. Prior-phase reports, old tests/goldens, build dependencies and random-stream source remain preserved.

## 29. Remaining limitations

Not a complete NIIT return, brokerage engine or universal annuity model. Source/growth restrictions can reject ordinary retirement plans, especially long market-risk runs with retained surplus. Rejection is intentional/disclosed; inputs are not modified. Recognized yield/basis/gain accounting requires separate approval.

Month-aligned fixed exact-cent quoted payments, post-1986 tax start, age ≥59½, externally documented tax split and verified opening remaining investment are required. No penalties/fractional billing/post-death deduction/legal qualification are inferred. State support is IL/existing no-general models. Authenticity proves original supported facts/arithmetic, not truth of insurer documentation.

## 30. Deferred functionality

Joint/survivor/period-certain/refund/variable/indexed contracts, purchases, qualified annuities/QLAC, MYGA/deferred accumulation, earnings-first withdrawals, exchanges, pricing/riders, cash/surrender values, external beneficiaries, terminal settlements and post-death basis deductions remain unsupported. No later phase begun. Native Windows acceptance is requested before further implementation.
