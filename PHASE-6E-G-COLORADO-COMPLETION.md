# Aledrix Retirement Planning — Phase 6E-G
# Colorado completion work and independent admission decision

Verification date: **2026-11-16**. Continues the accepted Phase 6E-F Colorado preparation. Read before changing source: [combined audit](PHASE-6E-E-KENTUCKY-COLORADO-TAX-AUDIT.md), [Phase 6E-F](PHASE-6E-F-KENTUCKY-COLORADO-IMPLEMENTATION.md), [North Carolina implementation](PHASE-6E-D-NORTH-CAROLINA-IMPLEMENTATION.md), and [Arizona B3](PHASE-6E-B3-ARIZONA-BROKERAGE-ADMISSION.md).

## 1. Executive summary

**Colorado remains disabled and is not admitted as jurisdiction 16.** Official certified election outcomes/proclamation and the resulting 2027+ effective law were not established. Current official compilation confirmation of the conversion interpretation also remains open. Neither unofficial election returns nor a conditional worksheet is a production rule.

Safe isolated preparation was extended: published 2026/2027 federal deduction coverage, explicit temporary senior-deduction expiration, signed base reconciliation, ordinary-source qualification/reconciliation, and a composed **2026-only nonproduction worksheet**. It supports testing the independently verified ordinary subset without registering Colorado, substituting another state's route, or changing existing financial calculations. Conversion transactions explicitly refuse rather than receiving guessed eligibility.

The initial working tree was clean. Kentucky's accepted source/tests and all other existing states remain byte-for-byte unchanged. Source confirms **15 production jurisdictions**: Michigan, Illinois, Pennsylvania, Iowa, Arizona, North Carolina, Kentucky, and the eight existing no-general-income-tax states Alaska, Florida, Nevada, New Hampshire, South Dakota, Tennessee, Texas and Wyoming. Colorado is absent from the jurisdiction enum, router and selector. No federal, funding, conversion-execution, ProjectionEngine, JavaFX, export, persistence-schema or analyzer changes were made.

Labels in this report: **Verified** means authoritative law/guidance successfully inspected; **Inference** means a reasoned application requiring the stated remaining confirmation; **Preparation** is isolated code with no production caller; **Approximation** is an expressly identified planning assumption; **Open** is an unmet release gate.

## 2. Election and effective-law verification

The actual [Secretary of State elections page](https://www.sos.state.co.us/pubs/elections/main.html) links to [2026 statewide ENR](https://results.enr.clarityelections.com/CO/127407/). That application returned a JavaScript/robot-verification page. The [official archive](https://www.sos.state.co.us/pubs/elections/Results/Archives.html), inspected on the verification date, lists 2026 **primary** results/abstract, without a 2026 general-election abstract. This establishes what was retrievable, not proof that no official determination exists anywhere.

The [SOS ENR explanation](https://www.coloradosos.gov/pubs/elections/FAQs/ENR.html) distinguishes reporting completion from certified county canvass results. The [2026 official calendar, pages 13–14](https://www.sos.state.co.us/pubs/elections/calendars/2026ElectionCalendar.pdf#page=13) puts the county canvass/abstract deadline on **November 25**, after this verification date. A deadline alone does not prove earlier certification impossible. No final statewide certificate, official final outcome or gubernatorial proclamation was successfully verified for either measure.

The legislature's [Amendment 87 fiscal statement, PDF page 22](https://content.leg.colorado.gov/sites/default/files/2026-fiscal-impact-statements-english-accessible.pdf#page=22) explains that, if approved, effectiveness follows the Governor's proclamation, no later than 30 days after official canvass completion. The proposed tax-year application begins in 2027. No actual proclamation date is asserted here.

The [ballot page](https://leg.colorado.gov/ballots/graduated-income-tax-2) being marked Completed is not election certification. Title-board ballot approval is likewise distinct. Unofficial reports of Amendment 87 passing and Proposition 136 failing were not promoted into implementation constants.

**Verified submitted text, not verified enactment:** Amendment 87 §2 removes the constitutional single-rate requirement; §3 changes individual tax rates and preserves a separate residence-gain treatment. The [final text](https://leg.colorado.gov/ballots/graduated-income-tax-2/files/text-of-measure) was readable through indexed official content after direct retrieval returned 403. An earlier eight-page initiative draft was also located; it is not substituted for the final measure or certification. The [official Blue Book analysis](https://leg.colorado.gov/ballots/graduated-income-tax-2/files/blue-book) corroborates marginal slices and lack of inflation adjustment.

The [Proposition 136 final text](https://leg.colorado.gov/ballots/income-tax-rate-limit-2/files/text-of-measure) proposes a 4.4% individual/corporate ceiling from January 1, 2027 and effectiveness on proclamation. Its certified defeat was not established. A final legal review must resolve the interaction using actual outcomes/effective law; this phase supplies no conflict-resolution assumption. No subsequently enacted provision establishing a different final schedule was verified.

## 3. Rate schedule and boundaries

| Year/category | Verification and code behavior |
|---|---|
| 2026 ordinary income | **Verified 4.4%** from [DOR DR 0104EP, worksheet page 3](https://tax.colorado.gov/sites/default/files/documents/DR_0104EP_2026.pdf#page=3). Isolated calculation only. |
| 2027 onward | **Open effective-law gate.** `rate(year)`, `tax(year, base)` and the composed worksheet refuse these years; no indefinite 4.4% fallback. |
| Conditional TABOR reductions/refunds | Not forecast or incorporated without reviewed legal establishment. |
| Principal-residence gains | No modeled amount is invented; generic brokerage proceeds do not establish this category. Separate source/rate handling remains outside the ordinary subset. |

The final submitted Amendment 87 §3 schedule is a verification target only:

| Taxable-income slice | Proposed marginal rate |
|---|---:|
| First $25,000 | 3.7% |
| Above $25,000 through $100,000 | 4.2% |
| Above $100,000 through $500,000 | 4.4% |
| Above $500,000 through $750,000 | 7.4% |
| Above $750,000 through $1,000,000 | 7.9% |
| Above $1,000,000 | 8.4% |

Its bands are shared by individual/joint filers, apply to successive slices rather than the entire base, and have no automatic inflation indexing. §3(1.8)(b) separately rates taxable principal-residence gains exceeding the IRC 121 exclusion at 4.4%. State modifications precede rates under amended subsection (2). These statements describe the submitted legal mechanism, not an admitted enacted schedule. No graduated constants or boundary expectations were placed in executable production/preparatory tax code ahead of certification.

## 4. Roth conversion conclusion and remaining confirmation

**Inference supported by official source definitions:** [DOR pension guidance](https://tax.colorado.gov/income-tax-topics-social-security-pensions-and-annuities) includes taxable IRA distributions and disqualifies premature IRA payments subject to federal additional tax. [IRS Publication 590-A, conversion section](https://www.irs.gov/publications/p590a) explains that a properly completed traditional-IRA conversion is taxable as applicable but avoids the early-distribution additional tax; retained cash can differ. Thus a completed taxable traditional-source conversion is not equivalent to a qualified Roth withdrawal.

The user-specified [Rule 39-22-104(4)(f) reproduction](https://www.law.cornell.edu/regulations/colorado/39-22-104%284-parens-f%29) was inspected in full. Paragraphs (1)(c)(i) and (2)(c) describe IRA receipts and premature-distribution limits. Paragraph (4)(b) excludes Roth distributions and contributions; it should not automatically be read as disqualifying every taxable traditional-source conversion. Conversely, source type alone does not establish every conversion's eligibility. The reproduction agrees with the important distinctions; it is not the governing official current PDF.

The [DOR current-rules page](https://tax.colorado.gov/current-rules) leads to the [official CCR index](https://www.sos.state.co.us/CCR/DisplayRule.do?action=ruleinfo&ruleId=1936&seriesNum=1%20CCR%20201-2), which identifies the current compilation as effective **March 2, 2026** and states the PDF governs. The current compiled text/download was not successfully retrieved through available access. An [archival official compilation, version 7212](https://www.sos.state.co.us/CCR/GenerateRulePdf.do?fileName=1+CCR+201-2&ruleVersionId=7212), was retrieved and corroborates the distinction, but its history ends in 2017; it is not substituted for the governing current compilation. No direct DOR interpretation resolving the conversion/contribution application was obtained. **The production interpretation gate remains open.** This does not establish that conversions are legally ineligible.

Preparation therefore rejects positive attributed **and scalar-only conversion income**, with a specific current-rule-confirmation reason. No additional owner cap, double income, blanket Roth-contribution inference or guessed taxable treatment is used. Ordinary IRA cash, completed IRA conversions, new Roth contributions, qualified Roth distributions, retained early cash, employer-to-Roth and in-plan conversions remain distinct classifications. Employer variants are not inferred from IRA guidance. No conversion golden is represented as a passed production acceptance case.

## 5. Federal taxable-income bridge

**Verified:** [DOR's individual guide](https://tax.colorado.gov/individual-income-tax-guide) starts from federal taxable income. For 2026+, standard-deduction addback applies strictly above $300,000 AGI, allowing $1,000 Single/$2,000 joint of that deduction. The bridge includes the basic plus age addition in the standard-deduction addback; the separate enhanced senior deduction is not a second standard deduction.

The [official Legislative Council OBBBA analysis, page 12](https://content.leg.colorado.gov/sites/default/files/images/lcs_june_2025_forecast_updated_with_obbba_fiscal_impacts.pdf#page=12) identifies the senior deduction flowing into state revenue. No later senior-specific addback was established in the current DOR guide. Ordinary deduction amounts are sourced independently:

| Year | Basic Single / MFJ | Additional age deduction Single / per married senior |
|---|---:|---:|
| 2026 | $16,100 / $32,200 | $2,050 / $1,650 |
| 2027 | $16,600 / $33,200 | $2,100 / $1,700 |

Sources: [IRS RP 2025-32 §3.14](https://www.irs.gov/pub/irs-drop/rp-25-32.pdf) and [RP 2026-35 §3.15, page 13](https://www.irs.gov/pub/irs-drop/rp-26-35.pdf#page=13). The bridge requires the supplied federal basic deduction to match the reviewed annual value and validates the existing modeled-base coverage. A projected value that differs is refused, not silently replaced or double-subtracted.

The temporary deduction is $6,000 per eligible senior in 2025–2028, reduced per person by 6% of domestic MAGI above $75,000 Single/$150,000 joint, floored at zero. [IRS senior guidance](https://www.irs.gov/newsroom/working-families-tax-cuts-individuals-and-workers) confirms its temporary period and eligibility. [Schedule 1-A (2025), lines 31–37](https://www.irs.gov/pub/irs-pdf/f1040s1a.pdf#page=2) independently confirms the continuing per-person phaseout method: compute the reduced $6,000 amount, then sum the eligible filers' amounts. It is not represented as a final 2026 form. The isolated rule method explicitly returns zero after 2028; tests cover 2028/2029 independently of unavailable future state rates. [IRS Pub 501](https://www.irs.gov/publications/p501) federal day-before-birthday and age-at-death conventions remain preserved from Phase 6E-F.

**Preparation coverage:** full bridge calculation supports published 2026/2027 amounts. It retains signed AGI-minus-basic-minus-age-minus-enhanced amounts, adds the verified state standard-deduction addback, then lets the worksheet subtract retirement adjustments and floor once. The original federal result remains unchanged, including federal tax and taxable SS. No federal deduction is subtracted twice.

**Open future-base requirement:** 2028+ complete bridge calls are refused until reviewed published amounts or an explicitly approved projection convention is supplied. The independently tested enhanced-senior expiration method is not certification of a complete 2029 federal base. No unapproved nominal deduction carry-forward or new general federal projection framework was introduced.

**Disclosed assumptions:** ordinary domestic nondependent standard-deduction filers, valid SSNs, MAGI equal to AGI, modeled ordinary sources. Itemization, blindness, foreign adjustments, business/QBI and other deductions/credits remain outside this bridge. Existing combined federal taxes retain their own modeled coverage. Further federal/state conformity changes must be checked before admission.

## 6. Per-person retirement subtractions and source policy

The existing `ColoradoRetirementAdjustments` arithmetic is reused unchanged. Per the [official DOR SS/pension worksheet](https://tax.colorado.gov/sites/default/files/documents/ITT_Social_Security_Pensions_and_Annuities_Jan_2025.pdf), each spouse has a separate $20,000 pool at 55–64 or $24,000 at 65+. Unused amounts do not transfer. Allocate household taxable SS proportionally to selected gross recipient benefits, with high-precision division and a residual preserving the household total. SS uses the personal pool first. Full-SS eligibility applies at 65+ and, from 2025, at 55–64 with AGI at most $75,000 Single/$95,000 joint; excess full SS leaves no other pension allowance.

| Preparation source | Admission/qualification boundary |
|---|---|
| Traditional/rollover IRA cash, RMD/opening RMD | Full taxable amount, account, actual active owner and matching year required. Sources reconcile to the income scalar. At age 55+, ordinary IRA relief requires age 59½ already reached at January 1; early/midyear crossing needs unavailable timing/exception facts and is refused. No claim that all withdrawals in that birthday year qualify. |
| Ordinary IRA before age 55 | No ordinary age-based subtraction; no special death/exception classification is inferred. Existing federal penalty omissions remain a separate limitation. |
| Conversion | Explicitly refused pending current-rule confirmation, including aggregate-only residuals. |
| Employer account withdrawals | Periodicity/self-employment/qualification not established by an ordinary account withdrawal DTO; this worksheet restricts the subset instead of granting guessed relief. |
| Qualified Roth cash | Known zero federal taxable amount; no added AGI or subtraction. Taxable/partial Roth is not automatically admitted. |
| Ordinary private pension | Fully taxable periodic private-plan assumption, actual current recipient and pension facts required. Finite terms must exceed one year. Explicit contrary separation/retirement facts override the ordinary assumption. Names are not classified. |
| Survivor pension | Supported ordinary periodic receipt at the survivor's age/pool, retaining original owner separately. Under-55 special death eligibility is refused; no deceased unused-cap transfer. |
| Commercial annuity | Current basis/starting-date terms do not prove full legal maturity. Explicit maturity refusal; no new input or unapproved maturity assumption. Basis recovery is not retaxed. |
| Generic conditional taxable receipt | Ordinary non-pension income, where independently admitted; retirement/death trigger creates no subtraction. Existing federal/NIIT/conditional gates remain independent. |
| SS | Complete recipient gross totals required and reconciled; no equal split, independent per-spouse federal SS tax calculation or double subtraction. |
| Wrong year, unknown owner, partial traditional basis, inherited history, unclassified/contradictory facts | Explicit refusal; no fabricated numeric result. |

These are preparatory boundaries, not production advertised support. Specialized military/railroad, government/PERA history, inherited exceptions, principal-residence gains and special investment-source adjustments remain outside the ordinary subset. No pension-classification or annuity-maturity screen was added.

The preparatory pension participant guard evaluates affirmative facts at the request distribution date. First-retirement-year payments commencing later than that date can therefore require further dated receipt reconciliation instead of admission under the ordinary steady-pension assumption. That boundary must be tested and resolved before advertising full first-retirement-year production coverage; no source owner or eligibility date is guessed here.

## 7. Brokerage, funding and bracket-fill integration

Arizona B3 policy is preserved in the isolated reconciliation: real ordinary unknown-basis brokerage source evidence is admitted, gross proceeds are not income, known taxable gain/proceeds evidence is checked against the already-modeled investment bucket, and duplicate/excess evidence fails. Tax-deferred receipts cannot masquerade as investment proceeds. Missing gains are a limitation, not a claim of a legal zero gain. No tax lots, invented residence gains or state basis assumptions.

The Kentucky capacity correction was inspected in `RothConversionBracketFillCalculator`: it bounds only KY trials to same-owner executable capacity. Existing NC capacity evidence and KY funding tests remain unchanged. **Colorado will need the same complete trial-attribution contract** because exclusions belong to recipients; an unowned trial residual cannot be split or assigned arbitrarily. No shared branch was extended while Colorado has no enum/route or verified conversion admission.

No existing funding solver, RMD-before-conversion sequence, execution ownership, balance mutation behavior or federal calculation was changed. A future CO adapter must call the annual worksheet-equivalent on every funding trial with current owner totals/SS/AGI/deductions and annual schedule, translate source refusals to the existing structured unsupported result, and use executed conversion amounts once.

**Open acceptance, not a passing claim:** common CO tax-funding convergence, deduction floors, SS cliffs, the $300,000 addback cliff, graduated boundaries, capacity exhaustion, actual/requested conversions and cash conservation have not been certified through a production CO route. The audit's independent no-cliff target remains approximately $5,300.96 traditional funding, $4,456.12 federal and $844.84 CO on the age-60 $50,000 IRA fixture. It is a future oracle, not a result obtained here. A valid common nonconvergence remains a hard blocker; fixed marginal-rate multiplication is not an acceptable substitute.

## 8. Files created or modified

Production-directory preparation, all under `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/colorado/`:

- **Modified `ColoradoTaxRules.java`:** preparatory version 2, 2026 exact-cent tax, published federal deduction parameters, temporary senior-deduction/phaseout method. Production admission stays false; future tax years refuse.
- **Modified `ColoradoFederalTaxBaseBridge.java`:** verified 2027 deduction support independent of tax-year rate gating; rule methods replace repeated deduction constants; preserve signed reconciliation before final floor.
- **Added `ColoradoIncomeReconciliation.java`:** narrow source/recipient aggregation and qualification; explicit conversion/maturity/early-receipt restrictions; B3 investment evidence reconciliation.
- **Added `ColoradoTaxWorksheet.java`:** composed 2026-only isolated result, immutable maps/limitations/version. Deliberately does not implement the production calculator interface and has no application caller.

Tests added under the matching `src/test/java/.../domain/tax/state/colorado/` directory:

- `ColoradoFederalBridgeCompletionTest.java` — 19 published-deduction, phaseout/expiration, signed-base, addback and immutable-federal cases.
- `ColoradoRetirementBoundaryTest.java` — 17 age/cap/SS threshold/allocation/rounding/refusal cases.
- `ColoradoWorksheetCompletionTest.java` — 24 independent income/ownership/B3/RMD/survivor/source/annuity/disabled-admission cases.

Added documentation: this report. Preserved unchanged: `ColoradoRetirementAdjustments`, its nine original preparatory tests, all accepted KY source/tests/reports and every other preexisting source/test. No registration, allowlist, selector, UI, export, report-context, freshness, persistence-schema, dependency or build-configuration edits.

## 9. Independent goldens and focused verification

Expected values are from Phase 6E-E §11's official-form/IRS arithmetic, not derived from production helpers. Examples passed by the isolated **2026** worksheet:

| Fixture, no incremental funding | Expected tax |
|---|---:|
| Single age 60, IRA $50,000 | $611.60 |
| Single age 65, IRA or ordinary pension $50,000 | $81.40 |
| Single age 60, IRA $50,000 + modeled investments $5,000 | $831.60 |
| MFJ age 60/66, IRA $50,000 each | $710.60 |
| Same household, all IRA $100,000 to age-60 recipient | $1,766.60 |
| Same household, all IRA to age-66 recipient | $1,590.60 |
| MFJ SS $30,000/$10,000, investments $80,000, IRA $10,000 each | $2,206.60 |
| Same, IRA $20,000 entirely to age-66 recipient | $2,646.60 |
| Single age 62, SS $40,000 + investments $44,540 / $44,541 | $1,251.36 / $1,711.64 |
| Single age 60, investments $300,000 / $300,001 | $12,491.60 / $13,156.04 |
| Brokerage proceeds $100,000, no modeled taxable income | Calculated $0.00 with limitations |
| CO reconciled base $1.25 / $100.125 | $0.06 / $4.41 |

Additional cases prove separate caps, no principal double taxation, known-gain contradictions, exact SS allocation sum, age 55/65 boundaries, strict Single/joint AGI thresholds, early/midyear 59½ refusal, opening/current RMD pooling, ordinary survivor receipt ownership, no live-account mutation, immutable limitations and continued rejection of `StateTaxJurisdiction.fromCode("CO")`. Bridge cases cover both qualifying seniors, partial/full phaseout, published 2027 values, expiration after 2028, negative intermediates, wrong federal basic deduction and inconsistent modeled base.

Initial new-test verification exposed a missing planned method before implementation (`phase6eg-bridge-before.log`, expected compile failure), then **28 bridge/original cases passed** (`phase6eg-bridge.log`). A test helper initially used nonexistent `getBalance`; corrected to the actual account accessor without changing production behavior (`phase6eg-focused-initial.log`). The next **50 Colorado cases passed** (`phase6eg-focused.log`). A dedicated new pension test reproduced acceptance of explicitly contradictory participant facts before the state-local guard was added (`phase6eg-pension-before.log`, one expected diagnostic failure).

An expanded run had **433 tests, one failure**, because its new annuity test expected the state gate before the independently existing federal NIIT refusal (`phase6eg-focused-final.log`). The test now asserts that federal refusal and exercises state maturity refusal separately; no federal/NIIT logic was weakened. **Final focused run: 433 tests, 0 failures, 0 errors, 0 skips, BUILD SUCCESS, 21.521 seconds** (`target/phase6eg-focused-accepted.log`). Includes **69 Colorado cases** (60 new + nine preserved), KY/NC/AZ acceptance including brokerage, state goldens and the unchanged capacity evidence.

## 10. Bounded existing-state regression

The established bounded suite uses IntelliJ bundled Maven and the project-local repository, excluding benchmark, capacity-probe, performance-audit, exhaustive and search classes. Result is recorded below when the run finishes; no unrestricted full-suite or large Monte Carlo claim is made.

Initial bounded run: **4,926 tests, 1 failure, 0 errors, 35 skips, BUILD FAILURE, 8 minutes 46 seconds** (`target/phase6eg-bounded.log`). The sole failure was the unchanged Michigan-based `Stage5DComparisonTest.callbackFailureCleansUpOutstandingWorkers`, line 266, checking whether a worker thread has already terminated immediately after callback failure. The same transient was recorded in Phases 6E-D/F. No worker code, timing assertion, state golden or expected financial result was modified. All 69 Colorado cases passed in this run. A direct class rerun and final bounded run follow; initial failure is not represented as a successful gate.

Direct unchanged class rerun: **23 tests, 0 failures/errors/skips, BUILD SUCCESS** (`target/phase6eg-stage5d-rerun.log`).

Final finished-source bounded run: **4,926 tests, 0 failures, 0 errors, 35 skips, BUILD SUCCESS, 9 minutes 42 seconds** (`target/phase6eg-bounded-final.log`). One Surefire retry was permitted for the observed timing flake, but **no retry/flaky result occurred**: XML contains no flaky/rerun entries, and all 23 Stage 5D tests passed on the first attempt in this run. Optional/visual skips are not manual desktop acceptance. Applicable existing-state financial goldens, brokerage, tax funding, annuity safeguards, persistence/export and analyzer-freshness regressions passed. This does not close the separate unimplemented CO production gates.

Existing financial expectations were not edited. The initial clean baseline was hashed for tracked `src/main`/`src/test` files; only the two isolated Colorado preparatory files differ among preexisting source/tests. New files are confined to the Colorado package and this report.

## 11. Persistence, reports, disclosures and freshness

No CO production metadata is fabricated while the legal gate is open. The isolated result captures `CO-2026-PREPARATORY-2` and all five immutable limitation paragraphs: ordinary/per-person scope; missing gains/special sourcing; rate/election policy; qualification/exclusions; federal-base/rounding limitations. Tests check result/map immutability. This is **not** a claim of completed user-visible CO integration.

`colorado-income-tax-rules` remains an isolated key, not inserted into existing-state analyzer completions. Before admission, extend the NC/KY mechanism for actual frozen CO full-plan inputs and either MC comparison side, then verify changed CO versions produce OUTDATED without rewriting stored taxes, notes, payloads or references. SS-only/other-state behavior must stay unchanged.

CO selector help, normal/year/range summaries, analyzer inputs, individual/integrated/MC contexts, Single/MFJ CSV/PDF, JSON CO save/reload/scenario/baseline, frozen CO history and rule-version freshness remain **deferred admission requirements**, as required by the authorization's legal-first registration gate. No schema migration or historical rewrite was introduced. Existing-state reporting/history/freshness tests are included in focused/bounded regression; they do not certify future CO paths.

## 12. Colorado admission decision and remaining work

**NOT ADMITTED. Supported count remains 15.** No `ColoradoProductionTaxCalculator`, CO enum/route, selection option or allowlist entry was added. The composed worksheet cannot be selected or reached from ProjectionEngine/analyzers/MC.

Independent remaining gates, in order:

1. Obtain certified official Amendment 87/Proposition 136 outcomes, proclamation/effective-law evidence and any subsequent amendments. Resolve competing-measure interaction and actual 2027+ schedule before implementing its bands; retain special residence-gain exclusion from the ordinary subset.
2. Reconcile the governing current Rule 39-22-104(4)(f) with the traditional-source conversion application, or obtain adequate authoritative interpretation. Remove the explicit refusal only after that evidence and actual execution/ownership tests; do not generalize employer variants.
3. Finish a reviewed future projected basic/age deduction policy for 2028+ without perpetuating the temporary enhanced deduction. No exact unpublished federal amount was invented here.
4. Integrate the small production adapter, source refusals, same-owner capacity-complete trials and annual recalculation; demonstrate common/cliff tax-funding convergence, actual conversions, annual/lifetime totals, both survivor directions and cash conservation.
5. Pass real brokerage/IRA/conversion projections, different-age spouses, two controlled MC worlds, a bounded integrated SS/longevity comparison, JSON/scenario/baseline/frozen history, full PDF/CSV disclosure and CO-specific freshness tests before production routing.

The ordinary IRA/pension subset can be developed with current ownership evidence; no new general attribution framework is required. Early receipt timing, unproved maturity and specialized inherited/death exceptions stay explicitly restricted. Registration is blocked by unmet gates, not by a request for routine implementation approval. No further approval was requested or inferred to waive these gates.

## 13. Native Windows acceptance and verification commands

```powershell
$maven = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd'
$repository = '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository'
& $maven $repository '-Dtest=Colorado*Test' test
& $maven $repository '-Dtest=Colorado*Test,Kentucky*Test,BracketFillCapacityEvidenceTest,StateTaxGoldenFixturesTest,MichiganTaxCalculatorTest,IllinoisProductionTaxCalculatorTest,PennsylvaniaProductionTaxCalculatorTest,IowaProductionTaxCalculatorTest,Arizona*Test,NorthCarolina*Test' test
& $maven $repository '-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*PerformanceAudit*,!*Exhaustive*,!*Search*' test
& $maven $repository '-Dtest=Stage5DComparisonTest' test
& $maven $repository '-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*PerformanceAudit*,!*Exhaustive*,!*Search*' '-Dsurefire.rerunFailingTestsCount=1' test
git status --short
git diff --check
```

1. Use the existing Java 25/IntelliJ application configuration. State selection must still have 15 jurisdictions, including Kentucky, with Colorado absent. Do not manually add a CO code to a private production plan to bypass admission.
2. Repeat the focused worksheet tests and inspect logs for BUILD SUCCESS. They are nonproduction calculator checks, not evidence that CO can run through the UI.
3. Confirm Kentucky's accepted native behavior remains unchanged. Existing Single/MFJ save/reload, normal/history exports and analyzer metadata retain their established behavior.
4. After the legal and financial gates are resolved in a continuation, perform the CO production scenarios in §12 and the full bounded acceptance list from the authorization; check every material limitation paragraph and frozen version. Do not use exhaustive searches/large random runs as a smoke test.

No manual interactive CO desktop acceptance is claimed. Initial/final `git status` and `git diff --check` are required repository evidence. No staging, commit, push or tag; no other state or unrelated feature was started. Stop at Phase 6E-G with Colorado disabled until its material gates can be completed.

## 14. Final repository record and stop point

Initial `git status --short` was clean. Final status contains only the two modified Colorado preparatory source files, two added Colorado source files, three added Colorado tests and this report. A SHA-256 baseline captured 1,369 preexisting tracked source/test files; only the two listed Colorado files changed, preserving the other 1,367, including accepted Kentucky work. `git diff --check` passed; new untracked Java/Markdown files were also checked directly for trailing whitespace. Git's LF-to-CRLF notices are line-ending notices, not whitespace-check failures.

No staging, commit, push, tag, project-file deletion, other-state calculation change, federal change or schema migration. Safe preparatory work and its tests are retained locally. **Stop after this report; Colorado is not production admitted.**
