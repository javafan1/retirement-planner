# Phase 6E-N2 — Arkansas isolated preparation

Verification date: **2026-11-17**. Arkansas is **not production-admitted**. Phase6E-N authorized preparatory law/qualification worksheets, independent tests, funding counterexamples and an architecture recommendation. It did not approve policy P, a production Arkansas settlement correction, an AR analysis key or registration. Alabama's independent result is in `PHASE-6E-N1-ALABAMA-IMPLEMENTATION.md`.

## 1. Outcome

Two distinguishable 2026 rate worksheets, an actual-joint/credit worksheet, source eligibility decisions and isolated funding experiments are implemented. Tests reproduce the audit's policy-P two-cycle, verify its minimum sufficient executable-cent candidate, and demonstrate a previously unproven settlement limitation: **the existing HouseholdCashSettlement cannot correctly attribute tax-funding surplus**. It must not be labeled as guaranteed-income or excess-RMD surplus.

The official estimated-table alternative has a different result for the same fixture and multiple fixed points around an upward tax jump. Neither smoothing nor ordinary fixed-point iteration is an adequate general Arkansas admission policy. Final annual-table/cent policy and a narrowly approved funding/settlement contract remain prerequisites. No shared production funding or settlement implementation was changed.

## 2. Exact preparatory files

All production-source files are isolated under `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/arkansas/`:

- `ArkansasTaxRules.java`: immutable reviewed2026 constants and preparatory version; `productionAdmitted()` is false. No production reference-key activation.
- `ArkansasRateWorksheet.java`: explicit `P_STATUTORY_CENTS` and `OFFICIAL_2026_ESTIMATED_TABLE` alternatives; gross tax and discrete adjustment.
- `ArkansasRetirementEligibility.java`: standalone interval/source decisions, including explicitly labeled conversion interpretation.
- `ArkansasJointWorksheet.java`: actual joint/single ordinary standard-deduction worksheet with individual retirement caps, personal/age credits and at most four senior pool-versus-65-Special alternatives. No filing-method optimizer.

Tests under the corresponding `src/test/java/.../domain/tax/state/arkansas/` directory:

- `ArkansasWorksheetTest.java`: independent statutory/table values, all29 adjustment bands and fractional edges, recipient/credit/retirement matrices, no AR registration and unpublished-year refusal.
- `ArkansasFundingCounterexampleTest.java`: decimal cycle, piecewise fixed-point analysis, minimum candidate, account realization, settlement refusal, official-table alternatives, bounded candidate prototype and actual unmodified funding-solver reproduction through an explicit test-local adapter.

There is no ArkansasProductionTaxCalculator, enum entry, router registration, state selection, production rules freshness key or live plan projection promise. Preparatory version is **AR-2026-PREPARATORY-1**. The API refuses unreviewed years rather than pretending2026 indexed dollars are published future values.

## 3. Official law/table verification

| Source checked2026-11-17 | Governing finding |
|---|---|
| [2026 special-session Act1](https://www.arkleg.state.ar.us/Home/FTPDocument?path=/ACTS/2026S1/Public/ACT1.pdf), [companion Act2](https://www.arkleg.state.ar.us/Home/FTPDocument?path=/ACTS/2026S1/Public/ACT2.pdf) | Approved May6,2026; individual schedule applies from January1,2026. Top individual rate3.7%; different corporate effective dates do not alter this. Retains annual statutory adjustment mechanism. |
| [DFA2026 AR1000ES revision5/6/2026](https://www.dfa.arkansas.gov/wp-content/uploads/2026_Final_AR1000ES_2.pdf), worksheet pp2–3 | Standard deduction2,470 per taxpayer; personal credit29 /joint58; ordinary age/65-Special29 each; dated estimated-tax schedule and29 fixed-dollar tax lookup rows. Distinct from final annual-return instructions. |
| [DFA individual forms](https://www.dfa.arkansas.gov/office/taxes/income-tax-administration/individual-income-tax/forms/) | Final annual2026 table/low-income decisions remain a recheck gate. Earlier `_1` estimated-table version and stale3.9% tables are not used. |
| [26CAR100-118](https://codeofarrules.arkansas.gov/Rules/Rule?chapterID=33&levelType=section&partID=941&sectionID=40225&subChapterID=261&subPartID=6195&titleID=26), [official rule compilation](https://webftp.blr.arkansas.gov/Home/FTPDocument?path=CAR%2FParts%2F26CARpt100.pdf) | Ordinary employer/source provisions. Its old own-IRA exclusion sentence cannot override later statutory inclusion. |
| [Act817 of1999](https://www.arkleg.state.ar.us/Home/FTPDocument?path=%2FACTS%2F1999%2FPublic%2FACT817.pdf), [Act773 of2001](https://arkleg.state.ar.us/Home/FTPDocument?path=%2FACTS%2F2001%2FPublic%2FACT773.pdf), current26-51-307 as reconciled in accepted audit | Later IRA retirement-benefit coverage, own-IRA age/timing limitations and individual6,000 pool control over older contrary regulation. Some legislative endpoints intermittently returned cache errors; retained official statute/enrolled evidence in Phase6E-M is identified, not replaced by an unofficial calculator. |
| [DFA2025 annual instructions](https://www.dfa.arkansas.gov/wp-content/uploads/2025_AR1000F_and_AR1000NR_Instructions.pdf), retirement and credit sections | Corroborates source/survivor and age/65-Special mechanics. Old indexed low-income endpoints are not promoted to2026 constants. |
| [DFA Subject300](https://www.dfa.arkansas.gov/wp-content/uploads/300-IRAs.pdf), revised2/27/2023 | Traditional-to-Roth conversion inclusion; no located conversion-specific DFA retirement-exemption determination. Contribution-limit history is not used for new rules. |
| [2021 Second Extraordinary Session Act1](https://www.arkleg.state.ar.us/Home/FTPDocument?path=/ACTS/2021S2/Public/ACT1.pdf), retained indexing text with2026 Acts | Future bracket/deduction indexing can change actual dollar parameters. No forecast CPI or unpublished future amount is enacted by this implementation. |

**Verified enacted law:** two schedules, top3.7%,29 discrete bracket adjustments and later own-IRA statutory coverage. **Official guidance:** dated ES table amounts, deductions/credits, ordinary retirement filing instructions. **Approved preparation interpretation:** post59½ own-IRA conversion shares the6,000 pool. **Unapproved production policy:** P's floor-dollar classification with exact cent arithmetic, or selecting the estimated-table convention as the definitive long-range return policy.

## 4. Two schedules and cent/official-table reconciliation

For the lower schedule, statutory rate slices are0% through5,600,2% on the next5,600,3% on the next4,800,3.4% on the next10,400, then3.7%. At N above26,400 the provisional exact expression is `.037*N - 367.20`.

For the separate higher schedule, first4,700 at2%, remainder3.7%, giving `.037*N - 79.90`, before the discrete adjustment. Act1 describes higher-income treatment above94,700 and adjustment bands94,701–97,600. **Policy P** classifies by `floor(N)` to extend these whole-dollar labels to cents. That extension is clearly named, testable and not approved as production law.

The official ES alternative uses each printed row's base amount, percentage and printed excess-over amount; in the adjustment range it uses the actual fixed-dollar lookup amounts. It preserves `.99` endpoints printed by the agency. For example:

| N | P gross tax | Dated ES gross tax |
|---:|---:|---:|
|5,600|0.00|0.00|
|11,200|112.00|111.00|
|16,000|256.00|255.00|
|26,400|609.60|608.00|
|30,000|742.80|741.00|
|50,000|1,482.80|1,481.00|
|80,001|2,592.84|2,591.00|
|94,700|3,136.70|3,134.86|
|94,700.99|3,136.74|3,134.90|
|94,701|3,134.04|3,136.00|
|94,800.99|3,137.74|3,136.00|
|94,801|3,147.74|3,150.00|
|97,600|3,521.30|3,519.00|
|97,601|3,531.34|3,533.00|
|100,000|3,620.10|3,618.00|

P has a downward transition at94,701 and upward jumps at subsequent adjustment steps. ES has its own fixed-dollar steps, rounded bases and even small row-boundary artifacts. They must not be averaged or smoothed. Final cents are HALF_UP in both preparatory alternatives; this does not assert final filed-return equivalence.

The ES prose says zero below5,599 while its next printed rate row starts5,600. The preparatory ES method explicitly refuses5,599.00–5,599.99 instead of silently filling that publication gap. Statutory P handles the interval. The error is source-specific, not a blanket rejection of ordinary retirement income.

The complete29-band reconciliation is appended below. Tests cover first/last/fractional edges and predecessor cents in every band, plus lower-schedule golden boundaries and the high-range exit. No credit or standard deduction is hidden inside gross-tax table values.

**Policy recommendation:** current official evidence justifies reproducing ES as a dated2026 estimated-payment worksheet. It does not establish that its rounded bases, lookup amounts and fractional gap are the definitive annual-return computation for every cent-valued projection. Do not select P by default or promote ES as filed-return accuracy. A future reviewed decision could explicitly accept ES as a planning approximation, or use the final annual table once available, but must accept/test its actual discontinuities and disclosed limitations. This phase intentionally leaves both alternatives identifiable and makes no production selection.

## 5. Retirement/source qualifications

| Receipt | Preparatory decision |
|---|---|
| Fully taxable own IRA/rollover cash after59½ throughout actual receipt interval | Qualifying income shares the current recipient's one6,000 pool. |
| Own IRA cash entirely before59½ without established separate exception | Taxable without the retirement pool. Federal penalty exceptions are not assumed to establish state qualification. |
| Annual receipt spans59½, unknown timing | Structured unsupported standalone decision; do not assign a payment date or call the whole year qualified. A known later partial-period start may establish eligibility. |
| Qualified ordinary employer-plan cash/retirement annuity | Ordinary Arkansas-specific plan qualification can permit the pool without importing the own-IRA59½ rule or Mississippi's retirement conditions. Adverse/unknown material plan facts require refusal. |
| Own RMD | Qualified own-account receipt sharing the same pool, not another allowance. |
| Direct ordinary survivor pension | Current recipient's pool, no unused deceased cap transfer. Do not invent special beneficiary facts. |
| Own traditional IRA-to-Roth conversion after59½ | Included first, then shares the same6,000 pool under the accepted audit's interpretation **for preparation**. Final admission must explicitly approve this interpretation or obtain DFA confirmation. |
| Early conversion | No automatic retirement relief because a federal conversion penalty may not apply. Not Mississippi's separate statutory exclusion. |
| Employer/in-plan conversions, inherited/disability/service exceptions | No guessed qualification. Separately evidenced legal interpretation/source support required before advertising. |
| Commercial nonqualified annuity | Taxable fraction included, recovered principal not retaxed; no6,000 pool without qualifying retirement-plan evidence. |
| Qualified Roth cash | Already outside modeled federal base; no second subtraction or pool use. |

This helper is not a production source-admission service. Its source categories and receipt intervals are explicit worksheet premises. No new broad timing, pension or investment input UI is added. An implementation would connect existing account/source/participant/DOB evidence and refuse material ambiguity; it must not infer names or arbitrarily allocate owners.

## 6. Actual joint filing, credits and future amounts

The worksheet accepts one or two explicit individual recipients. It applies each recipient's6,000 cap once, then one2,470 deduction per modeled taxpayer and actual-joint tax bands. It does not double the entire tax schedule or split one person's income in half. Investment ownership is not invented. Actual joint can overstate tax versus legal separate-on-same-return filing, which remains outside this preparatory optimizer.

Personal credit is29 per ordinary filer. A verified senior can receive the additional29 age credit; another29 65-Special is an alternative when that person does not claim retirement relief. The bounded comparison enumerates at most four combinations for two seniors, without changing filing status or consulting unpublished low-income tables. For income50,300 with only300 eligible retirement receipts, the independent senior special choice gives1,315.51, versus1,333.41 with the retirement subtraction and ordinary age credit. For fully eligible retirement50,000, the6,000 pool remains better:1,111.41.

Only the2026 published ordinary values are enabled in the worksheet. Optional low-income tables/additional indexed credits are deliberately not implemented from old2025 values. Small-income results are ordinary-subset worksheet values, not an optimized full return. A future production nominal-hold policy would hold latest published amounts with explicit CPI/credit uncertainty; it needs approval and must not be described as unindexed enacted law.

## 7. Independent regular-path fixtures

Phase6E-M Section17 independent fixtures are represented as statutory legal worksheets, source eligibility decisions or documented future-admission contrasts, rather than fabricated live projections.

| Premise under P | Expected tax after stated ordinary credits |
|---|---:|
|Single60 investments50,000|1,362.41|
|Above +eligibleIRA5,000 /6,000 /7,000|1,362.41 /1,362.41 /1,399.41|
|Single60 own qualifying IRA50,000|1,140.41|
|Single50 clearly early IRA50,000|1,362.41|
|Qualified employer-plan50,000 at50|1,140.41|
|Age60 IRA30,000+executed conversion20,000, interpreted eligibility|1,140.41, one cap|
|Age50 conversion50,000 without separate exception|1,362.41|
|MFJ50,000 each /one person100,000|2,648.02 /2,870.02|
|Separate-owner single-column legal contrast only|2×1,140.41=2,280.82; joint can overstate367.20|
|Senior qualifying RMD/retirement50,000, personal+age58|1,111.41|
|Senior investments50,000, personal+age+special87|1,304.41|
|Commercial annuity taxable20,000, no pool|Gross P308.02 before credits/optional low-income treatment|
|Ordinary50,000+modeled gain20,000 without holding-period relief|2,102.41|
|Same with separately proven qualifying netLTCG20,000, legal contrast|1,732.41; half-gain relief not inferred by production/prep sources|
|Brokerage principal100,000, no taxable bucket|0.00|
|Single survivor pension50,000+ownIRA50,000, one recipient|2,990.41|
|Five constant-premise qualifyingIRA50,000 rows|5,702.05 accumulated, not a future-index forecast|

No new-state expected value was generated from an existing/new production calculator. Test constants derive from cited law/ES rows and stated decimal worksheets. Tests preserve the distinction between income inclusion, eligibility and a fully optimized return.

## 8. Mandatory funding reproduction

Premises: Single age60; own qualified IRA cash86,197 before funding; one6,000 pool exhausted; state deduction2,470; credit29; no SS, NIIT, other income, available tax cash or special tax; traditional funding; existing2026 federal ordinary basic deduction16,100. State net income is `N=77,727+W`. The relevant federal22% expression before final cents is `F=10,133.34+.22*W`.

| P trial W | N | Federal (cent worksheet) | State after29 credit | Next requirement |
|---:|---:|---:|---:|---:|
|16,972.94|94,699.94|13,867.39|3,107.70|16,975.09|
|16,975.09|94,702.09|13,867.86|3,105.08|16,972.94|
|16,974.00|94,701.00|13,867.62|3,105.04|16,972.66|

The2.15 alternating difference exceeds one-cent tolerance. Before final rounding, lower/high segment fixed-point equations are `.743*W=12,613.039` and `.743*W=12,610.339`. Their roots lie on the wrong sides of16,974; rounding cannot bridge this multi-dollar gap. A bounded cent neighborhood is independently checked for no exact fixed point. For this fixed-source fixture, later100-dollar adjustment intervals increase the sufficiency residual by74.30 before each10-dollar tax jump, so none restores a zero. After the adjustment range there are no further state jumps and combined ordinary marginal rates remain below100%; there is no later exact funding root to rescue this fixture. The existing real TaxFundingCalculator, supplied an explicitly test-local Arkansas-P calculator under a test-only FL map entry, also throws its100-iteration nonconvergence error. **The default FL route is never changed, and no Arkansas enum is added.** Its unrounded federal arithmetic is not confused with the displayed cent worksheet; both establish the failure.

The minimum sufficient executable cent under P is16,974.00. The preceding16,973.99 is insufficient; the entire earlier affine branch has positive decreasing deficit and remains insufficient at its final cent. At16,974.00, actual taxes16,972.66 leave genuine1.34. This is not a conversion, RMD or phantom guarantee.

Under the dated ES alternative, the original fixture converges near16,973.36: federal13,867.48 +state3,105.88 =16,973.36. There is also an upper fixed point16,974.79: federal13,867.79 +state3,107 =16,974.79. Thus a sufficient-funding predicate need not be globally monotone. The earlier provisional16,973.35 expectation was corrected independently: federal rounds13,867.48 and state3,105.88, requiring16,973.36. No tax formula was altered to fit that correction.

The ES table eliminates this specific two-cycle but does not prove all Arkansas plans converge or select a unique/minimum root. Its upward transition is1.10 in gross tax at94,701, rather than P's downward2.70 change between adjacent cent-classified endpoints. Other table steps can create different multiple-root or sufficiency regions. No universal claim is made from one passing alternative.

## 9. Source realization and surplus feasibility

Tests use an actual1,000,000 own IRA projected balance. After86,197 spending and16,974 funding, the account allocator can realize the full cent withdrawal without overdraft or mutating the live Account. Hypothetically retaining1.34 once gives896,830.34 ending investable assets; economic uses are86,197 spending +16,972.66 tax. There are no RMD/conversion source entries to misclassify in this fixture.

**Current production settlement incompatibility:** HouseholdCashSettlement retained total must equal `retainedFromExcessRmd + retainedFromGuaranteedIncome`. Its factory derives retained amounts only from those two sources. With zero guaranteed/RMD cash, it cannot label1.34 excess traditional tax-funding cash. The factory fails sources/uses reconciliation; a direct constructor with unattributed retained1.34 fails retained-attribution validation. Both are asserted tests. Calling it excess RMD or guaranteed surplus would misrepresent actual financial sources. Merely calling `withAdditionalRetainedNonQualifiedAssets` is not a complete settlement fix.

Therefore the audit's candidate is mathematically and account-capacity feasible, **not compatible with unchanged production settlement semantics**. A future authorization must include an honest narrowly scoped tax-funding-surplus contract/attribution decision as well as the solver choice. Phase6E-N does not implement that change.

## 10. Recommended narrow algorithm and proof limits

The smallest defensible future design is an Arkansas-only minimum-sufficient candidate path with explicit settlement support, leaving every other state on its current algorithm. Required preconditions and behavior:

1. Lock the reviewed tax-table/cent policy first. Capture immutable annual household, source balances/owners, actual conversion, SS and retirement qualification. No guessed timing or unavailable source may enter a search.
2. Bound total executable funding by actual source balances and the existing withdrawal strategy. Source/owner changes are additional segment boundaries; never assume a household scalar implies identical eligible receipts.
3. Enumerate ordered tax/eligibility/table/source breakpoints and candidate roots within each supported segment, including both sides of each discontinuity and endpoint cents. Recompute exact annual taxes using the actual account allocation for every accepted candidate.
4. A candidate is sufficient only when actual spendable funding plus available cash covers recomputed taxes within the explicit tolerance and account/owner capacities are valid. Order by actual executable additional withdrawal, with stable source-order tie behavior; choose the first proven sufficient candidate. Do not globally binary-search a nonmonotone predicate.
5. Record all extra funded cash as an explicit tax-funding-surplus source. Deposit only real excess once using retained nonqualified assets; reflect it in settlement/source reporting. Never change executed conversions, RMD amounts, federal taxes or spending to absorb a residual.
6. If no feasible candidate exists, distinguish insufficient assets from unsupported segmentation/nonconvergence. Do not report a calculated zero state tax, accept underfunding or keep iterating indefinitely.

The test-local prototype covers only this fixture's0–20,000 withdrawal range, fully taxable own IRA, fixed22% federal region, known state policy, no SS/NIIT and no additional credits. It evaluates bounded roots plus neighbors and both sides of29 tax-adjustment boundaries, including the ES row change at80,001. Rounding two tax components contributes at most .01 error; three cent neighbors safely cover its root uncertainty within this restricted slope range. It finds P minimum16,974.00 and ES minimum16,973.36, and reports no feasible funding with1,000 capacity. It is not installed in production.

For K proved segments and A accounts, a deterministic candidate approach is approximately `O(K log K + K*A)` (plus each exact tax calculation). It avoids enumerating every cent or running strategy searches. A general root/bracket method is insufficient unless split at each discontinuity; unrestricted cent enumeration is wasteful and does not establish ownership/eligibility. Fixed-point iteration can alternate; smoothing statutory steps changes tax. Multiple valid roots require a stated minimum policy rather than dependence on initial guesses.

Proof does **not** yet cover arbitrary mixed sources, SS inclusion regions, NIIT, conversion-funded capacity feedback, future indexed tables, tax credits or partial-period eligibility. Those need separately bounded segment/validation tests before live support. A generally discontinuous tax function can defeat a continuous fixed-point solver; the theoretical limitation is not unique to Arkansas, but no other state's production behavior is changed or claimed defective here.

Minimum shared data-contract work, if separately approved, would distinguish tax-funding surplus from the current guaranteed/RMD retained categories in the immutable funding/settlement result and its frozen codec. Other states would default that new category to zero and retain their exact existing calculation path. This is not permission to add the field or migrate history now. Altering the global solver to accept a positive residual without source attribution risks double-retention, phantom cash and changed convergence outcomes for all states; silently reusing an existing retained category is unsafe. If backward-compatible narrow representation cannot be proved, continued nonadmission is preferable to a general redesign.

## 11. Verification and future admission decisions

Final focused selection: `target/phase6en-focused-complete-final.log`,127 tests,0 failures/errors/skips. Arkansas accounts for49 tests (44 worksheet cases and5 funding experiments). Initial AR worksheet failure concerned BigDecimal zero scale only; the result now consistently returns cents. The known policy-P solver exception and current settlement exceptions are successful counterexample assertions, not skipped acceptance.

Final bounded regression outcome is recorded in the Alabama report. No live Arkansas deterministic/MC/SS/reporting claim is made. Future admission must independently pass real source reconciliation/B3, both survivor directions, source capacities, RMD/conversion sequencing, funding/settlement, controlled economic paths, bounded SS/longevity, JSON/scenario/baseline, retained Single/MFJ reports and an independent rules key. None is activated here.

Final `target/phase6en-bounded-final.log`: **5,354 tests,0 failures,0 errors,35 existing skips;5,319 passed, BUILD SUCCESS and native exit0**. The final49-test Arkansas-only run also passes without skips. This validates preparation and unchanged accepted behavior, not Arkansas production qualification or settlement approval.

Explicit decisions required before future Arkansas production work:

- Approve a reviewed final annual-table/cent policy, resolving the dated ES versus exact-statute differences and material fractional gaps. P remains unapproved.
- Approve the identified post59½ own-IRA conversion interpretation or obtain a conversion-specific DFA determination. Employer/in-plan/special exceptions require separate evidence.
- Approve actual-joint versus separate-method scope, ordinary credit comparison and omission/dated implementation of optional low-income/additional indexed credit tables. No2025 indexed endpoint becomes2026 law by default.
- Approve latest-published nominal-hold projection assumptions while acknowledging statutory indexing.
- Authorize a precisely bounded minimum-sufficient Arkansas funding path **and explicit honest surplus attribution/settlement semantics**, with account realization, conservation, minimality and failure tests. A solver-only edit is insufficient.
- Independently complete reporting/history/freshness, source/MC/SS/persistence and existing-state regressions before registration. Do not infer permission to change the shared solver from acceptance of these preparatory tests.

**Recommendation: keep Arkansas disabled pending these decisions.** Alabama's admission is independent. Colorado remains disabled. No Phase6E-O, other state, Git publication or production Arkansas change is begun.

## 12. Complete adjustment boundary worksheet

The following table is generated from independent decimal arithmetic of Act1's printed adjustment bands and separately transcribed dated ES lookup values; no production calculator supplies these expected constants. P columns are gross before credits. Tests also verify each band through its `.99` extension and the predecessor cent. This is a policy comparison, not approval of cent extrapolation.

| Whole-dollar N interval | Statutory adjustment | P gross at first / last dollar | ES fixed gross |
|---|---:|---:|---:|
| 94701 - 94800 | 290 | 3134.04 / 3137.70 | 3136.00 |
| 94801 - 94900 | 280 | 3147.74 / 3151.40 | 3150.00 |
| 94901 - 95000 | 270 | 3161.44 / 3165.10 | 3163.00 |
| 95001 - 95100 | 260 | 3175.14 / 3178.80 | 3177.00 |
| 95101 - 95200 | 250 | 3188.84 / 3192.50 | 3191.00 |
| 95201 - 95300 | 240 | 3202.54 / 3206.20 | 3204.00 |
| 95301 - 95400 | 230 | 3216.24 / 3219.90 | 3218.00 |
| 95401 - 95500 | 220 | 3229.94 / 3233.60 | 3232.00 |
| 95501 - 95600 | 210 | 3243.64 / 3247.30 | 3245.00 |
| 95601 - 95700 | 200 | 3257.34 / 3261.00 | 3259.00 |
| 95701 - 95800 | 190 | 3271.04 / 3274.70 | 3273.00 |
| 95801 - 95900 | 180 | 3284.74 / 3288.40 | 3287.00 |
| 95901 - 96000 | 170 | 3298.44 / 3302.10 | 3300.00 |
| 96001 - 96100 | 160 | 3312.14 / 3315.80 | 3314.00 |
| 96101 - 96200 | 150 | 3325.84 / 3329.50 | 3328.00 |
| 96201 - 96300 | 140 | 3339.54 / 3343.20 | 3341.00 |
| 96301 - 96400 | 130 | 3353.24 / 3356.90 | 3355.00 |
| 96401 - 96500 | 120 | 3366.94 / 3370.60 | 3369.00 |
| 96501 - 96600 | 110 | 3380.64 / 3384.30 | 3382.00 |
| 96601 - 96700 | 100 | 3394.34 / 3398.00 | 3396.00 |
| 96701 - 96800 | 90 | 3408.04 / 3411.70 | 3410.00 |
| 96801 - 96900 | 80 | 3421.74 / 3425.40 | 3424.00 |
| 96901 - 97000 | 70 | 3435.44 / 3439.10 | 3437.00 |
| 97001 - 97100 | 60 | 3449.14 / 3452.80 | 3451.00 |
| 97101 - 97200 | 50 | 3462.84 / 3466.50 | 3465.00 |
| 97201 - 97300 | 40 | 3476.54 / 3480.20 | 3478.00 |
| 97301 - 97400 | 30 | 3490.24 / 3493.90 | 3492.00 |
| 97401 - 97500 | 20 | 3503.94 / 3507.60 | 3506.00 |
| 97501 - 97600 | 10 | 3517.64 / 3521.30 | 3519.00 |

## 13. Final repository and stop verification

Final status is35 modified tracked files and31 untracked files across the whole authorized phase, itemized in N1 and this report. `git diff --check` exits0; strict UTF-8/trailing-whitespace validation passes, the staged diff is empty and the opening Phase6E-M report hash is unchanged. All four Arkansas source files and two Arkansas test files are preparatory. No production tax-funding/settlement change, AR route/reference-key activation, CO activation or Git publication occurred. **Arkansas remains disabled; further production work requires explicit new approval.**
