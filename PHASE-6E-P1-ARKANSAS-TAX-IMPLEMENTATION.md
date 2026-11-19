# Phase 6E-P1 — Arkansas Tax Calculation and Source Qualification

## 1. Executive summary

P1 implements an isolated Arkansas annual income-tax calculation. It applies the authorized statutory-cent schedule, reconciles modeled federal and source facts, limits ordinary retirement relief to one pool per recipient, and compares the ordinary senior-credit elections. The result carries either an estimate in cents or structured unavailability.

The 2026 annual-return table and indexed low-income worksheets could not be verified as of November 18, 2026. The calculator therefore accepts a tax-stable zero or a positive regular-schedule result proved outside both potentially relevant low-income ranges. It refuses material uncertainty. The existing 20 states remain the production baseline, and Arkansas and Colorado are disabled.

## 2. Exact implementation scope

`domain/tax/state/ArkansasProductionTaxCalculator.calculateUnregistered(StateTaxCalculationRequest, ArkansasTaxEvidence)` uses the shared annual request, original governmental and federal facts, `StateTaxCalculationStatus` and `StateTaxUnavailableReason`. The request's registered jurisdiction supplies no Arkansas legal input. The isolated result carries no surrogate production state. The normal result and exception require an enum jurisdiction, so the explicit test adapter alone wraps the isolated outcome in a `ProductionStateTaxResult` to exercise `StateTaxUnavailableException`. The default Florida route is checked separately.

The implementation is all Arkansas-local plus one direct test class. Scope is full-year domestic ordinary Single and actual MFJ with standard deduction and the no-dependents planning premise. No Arkansas route, selector, production rules reference, snapshot format, funding, Roth executor, settlement, Monte Carlo or Social Security workflow is part of P1. The four preparatory worksheets, Arkansas funding prototype, accepted Alabama precedent, and Phase 6E-M, N2 and O reports were reviewed. Existing prototype and golden files retain their original content.

## 3. Official source verification

Sources rechecked **November 18, 2026**.

| Source | Governing evidence |
| --- | --- |
| [Enrolled 2026 Act 1](https://www.arkleg.state.ar.us/Home/FTPDocument?path=/ACTS/2026S1/Public/ACT1.pdf), [Act 2](https://arkleg.state.ar.us/Home/FTPDocument?path=%2FACTS%2F2026S1%2FPublic%2FACT2.pdf) and [legislative session summary](https://arkleg.state.ar.us/Home/FTPDocument?path=%2Fassembly%2F2025%2F2026F%2FDocuments%2F2026+Fiscal+Session+Summary+Book.pdf) | Approved May 6, 2026, individual rate schedules effective for tax years beginning January 1, 2026 onward. They amend §26-51-201(a)(4) and include the 29 tax subtractions. The 2027 corporate rate amendment is separate. |
| [DFA 2026 revised AR1000ES](https://www.dfa.arkansas.gov/wp-content/uploads/2026_Final_AR1000ES_2.pdf) and [DFA forms index](https://www.dfa.arkansas.gov/office/taxes/income-tax-administration/individual-income-tax/forms/) | May 6 worksheet revision, indexed June 2 listing. Supplies the $2,470 deduction and $29 ordinary credits for the model. ES bases and fixed lookup amounts are estimated-payment guidance. Its general instructions specify whole-dollar installment rounding. |
| [2025 annual booklet](https://www.dfa.arkansas.gov/wp-content/uploads/2025_AR1000F_and_AR1000NR_Instructions.pdf) and [2025 tax tables](https://www.dfa.arkansas.gov/wp-content/uploads/2025_TaxTables.pdf) | Prior-year whole-dollar income entries and annual table selection, including retirement, credits, low-income elections, and Form 5329-related line 32. The DFA index search listed only AR1000ES for tax year 2026. The direct 2026 annual form/table paths were unavailable and targeted official searches produced no verified final 2026 annual rules or 2027 individual table. |
| [Act 817 of 1999](https://arkleg.state.ar.us/Home/FTPDocument?path=%2FACTS%2F1999%2FPublic%2FACT817.pdf), [Act 773 of 2001](https://arkleg.state.ar.us/Home/FTPDocument?path=%2FACTS%2F2001%2FPublic%2FACT773.pdf), [26 CAR §100-118](https://codeofarrules.arkansas.gov/Rules/Rule?chapterID=33&levelType=section&partID=941&sectionID=40225&subChapterID=261&subPartID=6195&titleID=26) and [DFA Subject 300](https://www.dfa.arkansas.gov/wp-content/uploads/300-IRAs.pdf) | §26-51-307 ordinary retirement and later IRA coverage, eligibility at 59½, employer-plan facts, and taxable conversion guidance (February 27, 2023). Older contrary non-SEP regulation language does not supersede the later statute. |
| [2021 Special Session Act 1, §§9–10](https://www.arkleg.state.ar.us/Home/FTPDocument?path=/ACTS/2021S2/Public/ACT1.pdf), [2021 Act 483](https://www.arkleg.state.ar.us/Home/FTPDocument?path=/ACTS/2021R/Public/ACT483.pdf), [codified §26-51-301](https://law.justia.com/codes/arkansas/title-26/subtitle-5/chapter-51/subchapter-3/section-26-51-301/), [codified §26-51-201(d)](https://law.justia.com/codes/arkansas/title-26/subtitle-5/chapter-51/subchapter-2/section-26-51-201/) | Indexed deduction/credit provisions, statutory low-income range and indexing cap, consulted with the dated DFA prior-year forms. The codified pre-special-session source is used for unamended text, while the enrolled 2026 Acts control the rates. |

## 4. Statutory-cent policy

`P_STATUTORY_CENTS` is an approved planning convention, with annual-return equivalence pending review. Floor the state's net taxable base at zero. Use `floor(N)` solely to choose the high schedule and its applicable $100 adjustment band. Use the exact fractional net in the marginal slices. Apply the adjustment to gross tax, subtract applicable verified nonrefundable credits, floor at zero, and round the final tax once HALF_UP to cents. The worksheet's ES option retains its own bases, $5,599.00–$5,599.99 publication gap and fixed-dollar rows. The normal P1 calculation always selects P.

Historical annual whole-dollar rounding and the ES start at $94,701.00 do not settle how a future annual-return method treats unrounded $94,700.50. If authoritative 2026 material changes these assumptions, stop and obtain a reviewed policy decision before any Arkansas activation.

## 5. Brackets and all 29 adjustment boundaries

The lower schedule uses continuous marginal slices at $5,600, $11,200, $16,000 and $26,400 with rates 0%, 2%, 3%, 3.4% and 3.7%. With floor-classification from $94,701, the high schedule taxes the first $4,700 at 2% and the excess at 3.7%, then subtracts the amount below. The high schedule continues after the adjustment ends at $97,601.

| Whole-dollar classification interval | Tax subtraction | P gross at first cent | ES comparison within band |
| --- | ---: | ---: | ---: |
| 94701–94800 | 290 | 3134.04 | 3136.00 |
| 94801–94900 | 280 | 3147.74 | 3150.00 |
| 94901–95000 | 270 | 3161.44 | 3163.00 |
| 95001–95100 | 260 | 3175.14 | 3177.00 |
| 95101–95200 | 250 | 3188.84 | 3191.00 |
| 95201–95300 | 240 | 3202.54 | 3204.00 |
| 95301–95400 | 230 | 3216.24 | 3218.00 |
| 95401–95500 | 220 | 3229.94 | 3232.00 |
| 95501–95600 | 210 | 3243.64 | 3245.00 |
| 95601–95700 | 200 | 3257.34 | 3259.00 |
| 95701–95800 | 190 | 3271.04 | 3273.00 |
| 95801–95900 | 180 | 3284.74 | 3287.00 |
| 95901–96000 | 170 | 3298.44 | 3300.00 |
| 96001–96100 | 160 | 3312.14 | 3314.00 |
| 96101–96200 | 150 | 3325.84 | 3328.00 |
| 96201–96300 | 140 | 3339.54 | 3341.00 |
| 96301–96400 | 130 | 3353.24 | 3355.00 |
| 96401–96500 | 120 | 3366.94 | 3369.00 |
| 96501–96600 | 110 | 3380.64 | 3382.00 |
| 96601–96700 | 100 | 3394.34 | 3396.00 |
| 96701–96800 | 90 | 3408.04 | 3410.00 |
| 96801–96900 | 80 | 3421.74 | 3424.00 |
| 96901–97000 | 70 | 3435.44 | 3437.00 |
| 97001–97100 | 60 | 3449.14 | 3451.00 |
| 97101–97200 | 50 | 3462.84 | 3465.00 |
| 97201–97300 | 40 | 3476.54 | 3478.00 |
| 97301–97400 | 30 | 3490.24 | 3492.00 |
| 97401–97500 | 20 | 3503.94 | 3506.00 |
| 97501–97600 | 10 | 3517.64 | 3519.00 |

The exact transition $94,700.99 → $94,701.00 changes P gross from $3,136.74 to $3,134.04 and ES from $3,134.90 to $3,136.00. The final exit $97,600.99 → $97,601.00 changes P from $3,521.34 to $3,531.34 and ES from $3,519.00 to $3,533.00. The retained N2/O tests cover adjacent cents throughout the entire range. The direct P1 calculation also checks each transition at −$0.0001, the threshold and +$0.0001. [Enacted rate and adjustment schedule](https://www.arkleg.state.ar.us/Home/FTPDocument?path=/ACTS/2026S1/Public/ACT1.pdf) · [DFA ES worksheet](https://www.dfa.arkansas.gov/wp-content/uploads/2026_Final_AR1000ES_2.pdf)

## 6. Source reconciliation and federal bridge

`ArkansasIncomeReconciliation` checks annual year and source identity, actual active ownership for retirement/pension/conditional receipts, and exact contributions to scalar tax-deferred cash (including opening/current RMD), executed Roth conversion, pension and conditional taxable totals. SS attribution cannot exceed its modeled gross amount. When supplied, frozen household pension selection must match the attributed pension inputs. A source may explain an already-modeled investment bucket and never adds to its amount. Taxable joint amounts require the ordinary modeled joint context without creating individual shares.

The calculator verifies supplied federal AGI, taxable Social Security and NIIT against the existing federal calculator. The state bridge removes federally included Social Security once. Arkansas ordinary income, state retirement relief and Arkansas deduction determine the state base. Federal standard deduction and modeled federal taxable income are never the Arkansas base. The existing federal calculator continues to compute federal tax, including the governed NIIT evidence, for ordinary projection workflows.

## 7. Per-recipient retirement eligibility

For each actual recipient, one fixed $6,000 pool covers fully taxable, evidenced qualifying own traditional or rollover IRA distributions, RMDs, ordinary traditional 401(k)/403(b) withdrawals, qualifying pensions and completed eligible own-IRA conversions. No per-account pool or transfer applies. The account's typed ordinary source supports the employer classification within scope. The 59½ IRA condition is separate from employer-plan qualification. Early cash may still require the independent Form 5329 evidence gate. Represented mixed basis, unexplained partial taxable amounts, inherited and special categories refuse. Qualified modeled Roth cash is outside AGI and consumes none of the pool. The P1 direct request relies on validated upstream statutory RMD facts and does not rerun the RMD executor.

## 8. Own IRA age and receipt timing

Use `birth.plusYears(59).plusMonths(6)` with Java calendar month-end clamping at both steps, as reviewed for the planning convention. Direct fixtures cover ordinary dates, August 31 in leap and non-leap February, and February 29 birth. No Arkansas administrative month-end rule was located. The threshold date itself is eligible under the proposed reaching-age interpretation.

An explicit immutable receipt window is accepted when confined to the tax year. In its absence the whole calendar year is possible. The generic distribution date alone cannot prove a payment occurred on that date, after a birthday, or after a partial projection start. An opening RMD's supplied window must be before the opening date. Current and opening RMD sources keep their distinct purposes.

For each qualified/uncertain receipt the calculator accumulates per-recipient certain and possible amounts. The worksheet caps each at $6,000 and proves the tax envelope for every eligible credit election, including the left limit and actual value at each tax jump. A saturated pool or tax-stable zero can admit uncertainty. Endpoint equality alone cannot establish stability. The direct fixture with net endpoints $94,700.00 and $94,772.97 returns equal $3,107.70 endpoint taxes yet refuses the intervening possible tax.

## 9. Roth-conversion interpretation

P1 uses the exact funded conversion attribution already supplied by the caller. An eligible source is a completed fully taxable ordinary traditional/rollover IRA-to-same-owner Roth IRA transfer. Income inclusion reconciles once to the executed conversion scalar. At/after the modeled 59½ threshold, the conversion may share the owner's $6,000 §307 pool. This is the approved bounded statutory interpretation. The reviewed official guidance confirms inclusion and gives no conversion-specific exemption ruling. An early conversion remains taxable without the exclusion and does not trigger a penalty inference merely from age. Employer-to-Roth, in-plan, inherited, unexecuted, missing-destination or special basis cases require separate support. [DFA Subject 300](https://www.dfa.arkansas.gov/wp-content/uploads/300-IRAs.pdf) · [Act 817](https://arkleg.state.ar.us/Home/FTPDocument?path=%2FACTS%2F1999%2FPublic%2FACT817.pdf)

## 10. Pension and direct-survivor treatment

`PensionFacts` identifies the original owner, original classification and dates, while the source's owner is the actual recipient. Since a pension name and DB/DC status alone cannot prove ordinary Arkansas employment coverage, `ArkansasTaxEvidence.ReceiptFacts` must explicitly attest ordinary employment pension. DB and DC use the same Arkansas capped treatment. Direct spouse-survivor qualification also needs the original owner in the household and inactive, an actual active spouse recipient and a positive original survivor provision. The cap belongs to the recipient. The requested federal status controls one actual joint schedule in the modeled death year and one Single filer afterward. P1 takes these facts from the request and does not change the annual filing-status transition.

## 11. Deduction and senior credits

Use $2,470 deduction and $29 ordinary credit per taxpayer. Actual MFJ uses $4,940 and two personal credits with one unscaled joint schedule. Apply a $29 age credit under the January 1 following-tax-year convention (2025 annual age rule, 2026 ES amounts). For each qualifying senior compare $29 additional 65 Special against using that same individual's retirement exclusion. There are at most four choices per two-filer eligibility scenario. Candidates are traversed in primary/spouse order, first considering 65 Special for seniors. The first equal-cent choice wins. A non-senior takes the regular eligible retirement exclusion in the bounded ordinary worksheet. No claim is made about optimizing other legal elections.

At the modeled death use the known DOB and January 1 death if supplied. A deceased filer whose 65th birthday was strictly before death qualifies by the reviewed convention. When credit would depend on a birthday on or after modeled death and no later than the following January 1, compare with and without the credit. Unverified material differences refuse. A missing DOB without taxable retirement claims is also evaluated only if an invariant can be proved. [2025 annual line 7, death-year and age instructions](https://www.dfa.arkansas.gov/wp-content/uploads/2025_AR1000F_and_AR1000NR_Instructions.pdf) · [2026 ES credit amounts](https://www.dfa.arkansas.gov/wp-content/uploads/2026_Final_AR1000ES_2.pdf)

## 12. Low-income admission boundary

Two independent tests protect the regular-schedule result. Under §26-51-301 the exemption/low-income schedule follows filing status and gross-income rules, and a person may forego retirement relief to try the alternate schedule. §26-51-501(a)(6) separately caps a timely-filer additional credit by liability and bases it on net taxable income. The 2025 DFA worksheet used $27,600 as its upper endpoint and doubled the credit for actual joint filers. The new calculator does not reuse either 2025 worksheet as a 2026 rule.

- **§301 gross proof.** For ordinary Single and actual joint with no dependents start from statutory 2010 category ceilings $14,000 and $22,400. Apply a conservative maximum 3% each year from 2010 through 2026 inclusive, rounded outward to the next dollar. The resulting **proof ceilings** are $23,148 Single and $37,035 actual joint. Use reconciled ordinary income before retirement relief as a conservative lower bound on statutory all-source gross, leaving Social Security and excluded gross receipts out of that proof. This can refuse more cases than a full statutory gross worksheet.
- **§501 net proof.** The verified 2025 worksheet's $27,600 outer endpoint times the §201(d) maximum one-year 3% adjustment, rounded outward to a $100 multiple, yields a proof ceiling of **$28,500**. Compare the *smallest possible* state net across permitted claim/timing amounts. The separate additional credit is not omitted merely because gross passes the §301 test.
- If the ordinary result is **exactly zero** across the eligibility bounds, neither a nonrefundable credit nor selection of a lower-tax alternate can reduce it. Otherwise require both gross and minimum net to be **strictly above** the proof ceilings. A positive result at or below either bound is unsupported. These are refusal bounds, not eligibility or tax thresholds. No unresolved material amount is manufactured.

Example boundaries without retirement or senior adjustments: Single $18,000 and $30,970 ordinary gross refuse, while $30,970.01 calculates $658.30. Joint $35,000 refuses and $40,080 calculates $874.98. In production, dated annual schedules and exact election facts are needed for wider support. [§26-51-301](https://law.justia.com/codes/arkansas/title-26/subtitle-5/chapter-51/subchapter-3/section-26-51-301/) · [2021 enacted additional credit](https://www.arkleg.state.ar.us/Home/FTPDocument?path=/ACTS/2021S2/Public/ACT1.pdf) · [2025 DFA filing and credit examples](https://www.dfa.arkansas.gov/wp-content/uploads/2025_AR1000F_and_AR1000NR_Instructions.pdf)

## 13. Early-distribution additional tax

The prior annual instructions direct filers to calculate an Arkansas amount using specified federal Form 5329 additional tax. Aledrix has no complete federal penalty or exception ledger. P1 refuses affirmative evidence of applicable additional tax. It also refuses potentially early taxable cash where the source facts cannot establish inapplicability. For directly supplied evidence, `NO_APPLICABLE_ADDITIONAL_TAX` means the caller has substantiated inapplicability. An absent federal tax field or a Part I exception inferred merely from age does not establish it. An early completed ordinary own-IRA conversion has no age-only additional-tax inference. Its taxable principal remains in ordinary income. [2025 annual booklet, Form 5329 and line 32](https://www.dfa.arkansas.gov/wp-content/uploads/2025_AR1000F_and_AR1000NR_Instructions.pdf)

## 14. Brokerage and capital gains

The cash withdrawal's principal is neither a taxable retirement receipt nor an automatic realized gain. Unknown brokerage basis alone permits B3 principal cash. A validated modeled interest/dividend/gain bucket contributes once. Investment evidence is reconciled to amounts and ownership already carried in the annual investment history, checking purpose, year, duplicated or overlapping evidence and amounts beyond the supplied history. This policy may understate taxable realized gains absent from the model and overstate tax if eligible Arkansas long-term-gain relief went unmodeled. P1 neither guesses a gain or holding period nor claims a specialized exclusion on generic investment income.

## 15. Commercial annuities and conditional income

Taxable conditional amounts must reconcile to the existing attributed receipts and federal income. An otherwise admitted commercial nonqualified annuity contributes its verified taxable portion without re-including recovered basis or receiving an employment-retirement pool. An annuity claiming Advanced-mode NIIT treatment must carry the existing verified domestic qualification. Supported domestic conditional income contributes only its attributed taxable amount. Unexplained or specialized exclusions, ambiguous allocation and contradictory federal evidence refuse.

## 16. Rule version and future-year nominal hold

`ArkansasTaxRules.VERSION` is `AR-2026-P1-1`, its reviewed parameter year is 2026, and the isolated method is `P_STATUTORY_CENTS`. Tax years after 2026 use the latest reviewed amounts in nominal dollars. The versioned assumption includes the low-income proof bounds, so a future-year calculation under that assumption does not certify actual future qualification. Statutory CPI indexing may change deductions, thresholds, credits and bracket amounts. No 2027 individual parameters, projected CPI, new rate reduction, or indexing of the $6,000 cap were assumed. Years before the reviewed scope return a structured policy failure. The 2026 ES comparison remains dated to 2026.

`arkansas-income-tax-rules` remains a candidate key for a later admission phase. A new authoritative annual or future-year revision requires reviewed fixtures and a new rule version. Funding semantics will need their own identity. No production freshness rules, analysis versions or accepted history formats were changed here. [Enrolled Act 1](https://www.arkleg.state.ar.us/Home/FTPDocument?path=/ACTS/2026S1/Public/ACT1.pdf) · [Indexing provision](https://law.justia.com/codes/arkansas/title-26/subtitle-5/chapter-51/subchapter-2/section-26-51-201/)

## 17. Independent numeric goldens

The retained rate and Phase O comparisons provide worksheet expectations derived from the enacted marginal sums and the published ES table. The added direct request tests apply independent expected net liabilities, including the 29 adjustment starts and the exit, with fractional values on both sides. The $5,600, $11,200, $16,000 and $26,400 bracket tests also compare the senior election at and around the edge. The direct results admit the tax-stable zero and refuse positive worksheet results while low-income eligibility is unresolved. Gross worksheet values below exclude the ordinary credits.

| Net Arkansas taxable income | P gross tax | Published ES comparison when retained |
| ---: | ---: | ---: |
| $5,600 | $0.00 | |
| $11,200 | $112.00 | $111.00 |
| $16,000 | $256.00 | |
| $26,400 | $609.60 | |
| $50,000 | $1,482.80 | $1,481.00 |
| $94,700 | $3,136.70 | $3,134.86 |
| $94,701 | $3,134.04 | $3,136.00 |
| $94,801 | $3,147.74 | $3,150.00 |
| $97,601 | $3,531.34 | $3,533.00 |
| $100,000 | $3,620.10 | |

Direct examples include $2,619.02 for actual MFJ with separate eligible retirement receipts and different ages, and $2,812.02 where retirement income is concentrated with one filer and the senior spouse claims 65 Special. An ordinary eligible $6,000 pool yields $1,140.41 versus $1,362.41 with no exemption on the comparable included ordinary income. The account-backed senior case combining withdrawals, current/opening RMDs and actual qualified conversion yields $1,111.41 with one pooled $6,000 deduction. The pension/IRA survivor fixtures yield $2,870.02 in the death year and $2,990.41 in the later Single year. The direct tests verify taxable Social Security exclusion, federal attribution mismatch refusal, the cash/taxable distinction and repeatability under future nominal parameters.

## 18. Structured unsupported results

`Result.estimatedIncomeTax()` contains cents only for `CALCULATED`. A true zero is present as `0.00`. `UNSUPPORTED` carries a shared reason and an Arkansas failure category, `LEGAL_OR_SOURCE`, `ATTRIBUTION` or `OUTSIDE_REVIEWED_POLICY`, plus an explanation. Income unsupported in P1 includes potentially applicable low-income provisions, materially ambiguous IRA receipt or senior eligibility, unsubstantiated pension qualification, special retirement/inherited/rollover variants, partial basis not represented for ordinary retirement, applicable or potentially applicable additional tax lacking resolution, and unadmitted conditional or annuity treatment. Attribution failures include missing/excess source totals, wrong year or owner, overlapping investments, unmatched pension evidence, and inconsistent federal data. No unsupported case is converted to tax of zero.

## 19. Changed-file manifest

Paths below are relative to the repository root. The earlier Phase O report and funding prototype were already untracked at entry and retain their contents.

| Change | File |
| --- | --- |
| Added | `PHASE-6E-P1-ARKANSAS-TAX-IMPLEMENTATION.md` |
| Added | `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasProductionTaxCalculator.java` |
| Added | `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasIncomeReconciliation.java` |
| Added | `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasTaxEvidence.java` |
| Modified | `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/arkansas/ArkansasTaxRules.java` |
| Modified | `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/arkansas/ArkansasRateWorksheet.java` |
| Modified | `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/arkansas/ArkansasJointWorksheet.java` |
| Modified | `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/arkansas/ArkansasRetirementEligibility.java` |
| Added | `src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasDomainCalculationTest.java` |

## 20. Test execution

The IntelliJ-bundled Maven ran on Java 25 with the workspace `.codex-m2/repository`. The focused selector was `Arkansas*,AlabamaAnnualGoldenTest,AlabamaProductionTaxCalculatorTest,AlabamaPensionPreflightTest,AlabamaPensionNoticeTest,StateTaxGoldenFixturesTest,MichiganAttributedGoldenTest,ProductionStateTaxServiceTest,NoIncomeTaxAdmissionTest,ColoradoWorksheetCompletionTest`.

| Run | Passed | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| Focused | 372 | 0 | 0 | 0 |
| New direct Arkansas cases, included in focused | 26 | 0 | 0 | 0 |
| Existing Arkansas preparation and O funding prototype, included in focused | 61 | 0 | 0 | 0 |
| Initial bounded run | 5,365 | 0 | 0 | 35 |
| Final bounded regression (5,400 discovered) | 5,365 | 0 | 0 | 35 |

Bounded selector `*,!*Benchmark*,!*CapacityProbe*,!*Performance*,!*Exhaustive*,!*Search*`. The reports are recorded in `target/phase6e-p1-focused.log` and `target/phase6e-p1-bounded-final.log`. The initial bounded run before the extra lower-bracket credit assertions is in `target/phase6e-p1-bounded.log`. The final run completed November 18, 2026 at 15:50:49 EST. The initial and final runs had identical skip counts. All 87 Arkansas cases ran and passed in the final bounded run.

## 21. Production baseline and nonregression

The production enum/service registry still admits exactly AK, AL, AZ, FL, GA, IA, IL, KY, MI, MS, NC, NH, NV, OK, PA, SD, TN, TX, VA and WY. The router has no Arkansas or Colorado entry. Alabama's accepted source classification, preflight guidance and final unavailable handling remain in the original paths. The focused and bounded runs cover the registry, Alabama annual and pension checks, general state financial fixtures and Michigan attributed goldens. All existing-state bounded tests that ran passed with the original expected values. `git status --short --untracked-files=all` matched the manifest and the two pre-existing Phase O files. `git diff --check` passed, and the Git index stayed unchanged.

## 22. P2 funding and settlement dependencies

- Build the annual evidence adapter from actual owner/account allocations after RMD and executed same-owner conversions. Demonstrate that receipt windows stay valid for current/opening RMD, partial years and modeled survivor transitions. Keep unresolved low-income, pension and Form 5329 cases explicit instead of treating a pricing failure as a feasible funding point.
- Recompute taxes against each candidate's executable withdrawals and their taxable consequences, including funding Roth conversion taxes. The existing bracket-fill trial and final execution must agree on post-conversion capacity and source order. Mixed principal/deferred/retained accounts and shifting taxable Social Security need account-backed tests.
- Implement the segment-aware minimum-sufficient policy under separate authorization. The unchanged Phase O prototype reproduces the fixed-point oscillation and demonstrates that executable $16,974.00 can meet recalculated taxes of $16,972.66. Its $1.34 residual requires separately attributed available and retained cash. The present settlement rejects modeling this residual under its existing source categories, and P1 makes no change there.
- Verify no feasible withdrawal and unsupported source/tax remain separate outcomes. Require actual use/source and retained balance reconciliation with later-period growth or spending, a versioned algorithm, and compatibility checks across frozen history and exports before considering registry activation.

## 23. Remaining implementation and native-acceptance gates

1. Recheck the final Arkansas forms, table and credit worksheet. Review any deviation from P before release. Require reliable 2026 (or later reviewed) parameters to price currently refused low-income cases, or retain that explicit coverage boundary. Reassess a written DFA interpretation of own-IRA conversion and timing conventions.
2. Complete the approved Arkansas-only funding and surplus accounting design. Verify direct fixtures, live account capacity, ordinary B3 principal, exact cash conservation, RMD priority, actual Roth execution, Single/MFJ and survivor behavior.
3. Pass controlled deterministic, Monte Carlo and bounded integrated Social Security/longevity cases. Validate JSON reload, Save As, scenario and baseline swaps, history fingerprint compatibility, results and frozen CSV/PDF source/use totals, and rules-version freshness under the intended production path.
4. Run the complete authorized bounded 20-state comparison and native Windows acceptance of the eventual end-to-end Arkansas workflow, including supported results and the State Tax Unavailable path. Direct Java tests and the available UI regression suite are not an Arkansas native production acceptance.

## 24. Recommendation for Phase 6E-P2

Recommend separately authorizing isolated Arkansas account-backed funding and settlement implementation using the P1 domain and Phase O architecture. Keep Arkansas and Colorado disabled. The low-income gap is bounded for P1 calculations and still limits production admission. Final annual guidance, the real evidence adapter, surplus accounting and the release gates above remain prerequisites. A separate explicit P2 authorization is required.
