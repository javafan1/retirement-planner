# Phase 6C - Eight-state simplified tax validation and hardening

Completed and verified October 9, 2026. Standalone prototype only. Michigan production discrepancies
remain a separately deferred production issue under the user's explicit approval.
Nothing in this phase routes the calculator into the application or changes plan JSON.

## Architecture

Reviewed the complete Phase 6A/6B reports, dataset notes, supplied and packaged JSON,
all five prototype implementation classes and both original prototype test classes.
Preserved immutable requests/rules/results, cached classpath loading, BigDecimal,
request-local scratch and explicit unsupported results. No new dependencies.

The resource now uses additive schema `1.1-validated-boundaries`; the loader also
accepts the original `1.0-draft`. Original research input is untouched and archived
byte-for-byte at `src/test/resources/tax/state/state-tax-2026-phase6b.json`.
Original/archive SHA-256:
`5A7918AB919B4D1E81FF4D1575B0F4C1C4D9A14E6A36E975F8B95ED8E9083734`.

Generic additions:

- `StateTaxRules.Adjustment.coordinatedLimit`: track prior exclusions within each
  household/person group. Full SS can exceed a pension allowance while consuming
  all remaining pension room. No shared cap is multiplied by the number of rules.
- `federalAgiMaximum`: declared full-SS eligibility ceiling by filing status.
- Explicit upper-inclusive income-band endpoints and no exclusion above the last band.
- Whole-age bands include fractional ages until the next completed birthday.
- `StateTaxRules.Boundary`: before/after-retirement scenario admission using declared
  federal AGI, starting state base, or adjusted state base; age, income/source,
  conversion qualification, benefit-eligibility and SS allocation checks.
- Strict loading rejects unknown methods/fields, malformed boundaries, incompatible
  shared scopes/caps, mixed coordination flags, unbounded groups and fractional
  whole-age definitions. Original validation remains in place.
- Result statuses are `CALCULATED`, `PROVISIONAL`, `UNSUPPORTED`. CALCULATED means
  evaluated within the declared approximation, not an authoritative completed return.
  REVIEW_REQUIRED still produces PROVISIONAL; unsupported means no estimate at all.

`SimplifiedStateTaxCalculator.calculate`, `exclude`, `checkBoundaries` implement these
generic rules. `StateTaxDatasetRepository.parse`, `state`, `adjustment`, `ageBands`
validate/normalize them once. No jurisdiction branches or state dollar/rate constants
were added to Java calculation code. `StateTaxRequest` is unchanged. Qualifications
are existing immutable per-income assertions, not edits to pensions, accounts or plans.

## Dataset change control

`PHASE-6C-STATE-TAX-DATASET-CHANGES.json` records all **44 semantic changes**, including
metadata/source/limitation additions, with JSON pointers and exact old/new values.
A test replays the complete ledger against the archived original and requires exact
structural equality with the packaged resource. Formatting changes are not rule changes.
No tax rates, brackets, original deduction amounts or original exclusion caps changed.

| State | Old rule | New rule / reason | Authoritative source and applicable year | Demonstrating fixtures |
| --- | --- | --- | --- | --- |
| Global | 1.0-draft, research version; FL ready, others review | 1.1 additive generic boundaries, dated version; bounded readiness classifications | Validation described here; this is metadata, not new tax law | Archive/patch replay and 20 new malformed cases |
| CA | Research status; no early-distribution admission guard | Indexed 2026 values independently checked; bounded readiness and early-distribution guard. Senior/blind credits and high-income credit phaseout explicitly remain omitted | [FTB 2026 indexing](https://www.ftb.ca.gov/about-ftb/newsroom/tax-news/), [FTB additional retirement tax instructions](https://www.ftb.ca.gov/forms/2025/2025-3805p-instructions.html), enduring provisions checked in 2025 guidance | CA-SINGLE, CA-MFJ, CA-SURCHARGE, CA-EARLY-DISTRIBUTION |
| MI | Conversions taxable without exclusion; group label alone | Assessed qualifying conversions at 59.5+ share the existing household limit; age67/public-pension election scenarios blocked | [Michigan Roth-rollover FAQ](https://www.michigan.gov/taxes/questions/iit/accordion/subtract/is-a-rollover-from-a-regular-ira-to-a-roth-ira-an-allowable-subtraction-1), [2026 RAB](https://www.michigan.gov/taxes/rep-legal/rab/2026-revenue-administrative-bulletins/revenue-administrative-bulletin-2026-1) | MI-CONVERSION-SHARED, exact/below age, MI-AGE67-ELECTION |
| IL | Conversion absent from unlimited eligible retirement subtraction | Add assessed federally taxable qualifying conversions; verified bounded readiness | [Illinois Pub120](https://tax.illinois.gov/research/publications/pubs/retirement-income.html), [2026 exemption bulletin](https://tax.illinois.gov/content/dam/soi/en/web/tax/research/publications/bulletins/documents/2026/fy-2026-15.pdf) | IL-LARGE-CONVERSION, IL-CEILING-EXACT/ABOVE |
| PA | Every positive conversion unsupported | Complete qualifying IRA-to-Roth transfers excluded; unknown/withheld/retained conversions unsupported | [PA Revenue FAQ274](https://revenue-pa.custhelp.com/app/answers/detail/a_id/274/kw/retirement), current enduring resident rule | PA-COMPLETE-CONVERSION, PA-CONVERSION-WITHHOLDING |
| NY | No conversion exclusion; ordinary brackets even at high income | Add assessed conversions to per-person shared20000 pool; block adjusted NY AGI above107650 | [NY conversion memo](https://www.tax.ny.gov/pdf/memos/income/m98_7i.pdf), [2026 IT2105 instructions](https://www.tax.ny.gov/pdf/current_forms/it/it2105i.pdf) | NY-CONVERSION-SHARED, NY-RECAPTURE-BELOW/EXACT/ABOVE |
| CO | SS/pension interaction unsupported; fractional age gap; under65 full-SS and high-income addback absent | Coordinate per-person allowance; completed-age intervals; age55+ full-SS AGI ceiling75000/95000; guard federal AGI above300000, conversions and inconsistent joint SS allocation | [CO retirement guide](https://tax.colorado.gov/sites/tax/files/documents/ITT_Social_Security_Pensions_and_Annuities_Jan_2025.pdf), rules effective2025 onward; [January2026 tax guide](https://tax.colorado.gov/sites/tax/files/documents/Individual_Income_Tax_Guide_January_2026.pdf) | CO-SHARED-LIMIT, CO-AGE-54/55/64/64.5/65, CO-SS-LOW/ABOVE-AGI, CO-ADDBACK-ABOVE |
| NJ | Band endpoints/above150000 and all conversions unsupported; additional retirement exclusion silently omitted | Inclusive upper endpoints, zero pension exclusion above ceiling; explicitly assessed NJ taxable conversion amount admitted to pension pool. Potential Other/Special Retirement Income Exclusion cases blocked | [NJ January2026 GIT1&2](https://www.nj.gov/treasury/taxation/pdf/pubs/tgi-ee/git1&2.pdf), enduring current rules | NJ-INCOME boundary family, NJ-EXPLICIT-CONVERSION, NJ-OTHER-RETIREMENT, NJ-SPECIAL-ELIGIBILITY |
| FL | Verified no personal income tax | No financial rule changes; empty boundary list is explicit schema metadata | [Florida DOR FAQ1466](https://floridarevenue.com/faq/Pages/FAQDetails.aspx?FAQID=1466), current constitutional rule | FL-SINGLE/MFJ and original six income/status fixtures |

## State readiness

These classifications apply to the stated full-year-resident approximation for 2026,
not to all taxpayers or future years. All eight parse, and all eight have bounded
CALCULATED scenarios. No state is approved for projection integration in this phase.

| State | Dataset valid | Calculates | New independent golden fixtures | Known unsupported cases | Status |
| --- | --- | --- | --- | --- | --- |
| California | Yes | Yes, with disclosed credit/basis limitations | 8 | Early retirement distributions below59.5 where additional tax/exception is unavailable | READY_WITH_LIMITATIONS |
| Michigan | Yes | Yes, restricted qualified retirement treatment | 8 plus four production comparisons | Age67+ alternative elections, public-source pension elections, unassessed retirement qualification | READY_WITH_LIMITATIONS |
| Florida | Yes | Yes, zero personal income tax | 2 plus six existing independent cases | General unsupported year/status only | READY_FOR_SIMPLIFIED_MODEL |
| Illinois | Yes | Yes, assessed qualifying retirement and investment income | 5 | Unassessed qualifying retirement income/conversions | READY_FOR_SIMPLIFIED_MODEL |
| Pennsylvania | Yes | Yes, established state amounts and eligibility | 6 | Early distributions requiring basis recovery; unconfirmed complete conversions/withheld amounts; nonqualifying employer/pension basis | READY_WITH_LIMITATIONS |
| New York | Yes | Yes, below recapture boundary | 6 | Adjusted NY AGI above107650, unknown government-pension source/qualification | READY_WITH_LIMITATIONS |
| Colorado | Yes | Yes, coordinated retirement subtraction | 11 | Federal AGI above300000, Roth conversions, arbitrary joint taxable-SS allocation, unassessed retirement qualification | READY_WITH_LIMITATIONS |
| New Jersey | Yes | Yes, limited pension-only or otherwise unaffected scenarios | 12 | Unestablished conversion basis, age-eligible investment income at total NJ income<=150000, unproven special-exclusion eligibility | READY_WITH_LIMITATIONS |

No packaged state remains REVIEW_REQUIRED after the bounded validation; that status
remains supported and never automatically maps to CALCULATED. Neither readiness nor
successful arithmetic implies tax-preparation accuracy or readiness for every scenario.

## Authoritative validation findings

California's published October indexing confirms the supplied 2026 schedules,
5900/11800 standard deductions and158/316 personal credits. Exact progressive
slices are used rather than rounded worksheet base constants. The 1% Behavioral
Health Services Tax applies only above1000000 taxable income. FTB's final complete
2026 forms are not yet available; enduring provisions use identified earlier guidance.
[FTB 2026 indexing](https://www.ftb.ca.gov/about-ftb/newsroom/tax-news/),
[Behavioral Health tax, line61](https://www.ftb.ca.gov/forms/2025/2025-540-booklet.html).

California excludes the federal-taxable SS portion, taxes retirement/conversion
income generally as federally included, and taxes capital gains without preferential
rates. Conversion input is the taxable amount, not gross transferred assets. State/federal
IRA-basis reconciliation, municipal-bond sourcing and specialized credits/AMT are not
modeled. Senior/blind credits and personal-credit phaseout omissions are disclosed;
the high-income final-tax fixture is MEDIUM confidence, while its bracket/surcharge
arithmetic is independently verified. This record therefore retains limitations.
[FTB SS guidance](https://www.ftb.ca.gov/file/personal/income-types/social-security.html),
[FTB capital gains](https://www.ftb.ca.gov/file/personal/income-types/capital-gains-and-losses.html),
[FTB Pub1005](https://www.ftb.ca.gov/forms/2024/2024-1005-publication.pdf).

Michigan's 2026 rate/exemption and published retirement maxima are independently
verified. Employer income is never assumed qualifying: voluntary-deferral-only
arrangements can differ from employer-contribution/mandatory-contribution plans.
Qualified rollover subtraction at59.5+ consumes the same retirement allowance.
Alternative age67 deductions, public pension elections, military/public-safety and
survivor-specific exceptions require separate assessment and remain outside this
bounded prototype. The production path does not implement those alternative elections.
[2026 MI rate/exemption](https://www.michigan.gov/taxes/business-taxes/withholding/calendar-year-tax-information),
[ORS limits](https://www.michigan.gov/ors/public-act-4-of-2023-faq),
[Employer-distribution qualification](https://www.michigan.gov/taxes/questions/iit/accordion/subtract/are-distributions-from-a-deferred-compensation-plan-an-allowable-subtraction-1).

Illinois subtracts only qualifying federally taxable amounts actually included in
AGI, including eligible IRA-to-Roth conversions. Investments remain taxable at4.95%.
The2925 exemption and1000 age65 deduction are denied above AGI250000 Single/500000 MFJ;
the ceiling is tested before subtraction. Qualified Roth distributions are never
added to the federal starting base.
[Pub120](https://tax.illinois.gov/research/publications/pubs/retirement-income.html),
[Illinois exemption guidance](https://tax.illinois.gov/questionsandanswers/answer.851.html),
[2026 Comptroller bulletin](https://illinoiscomptroller.gov/__media/sites/comptroller/assets/File/PayrollBulletins/Payroll%20Bulletin%201-26%20-%20Illinois%20State%20Income%20Tax%20Exemptions%20-%202026.pdf).

Pennsylvania remains STATE_DEFINED_INCOME,3.07%, not adjusted federal AGI.
Normal eligible IRA distributions and explicitly qualified employer pensions are
excluded. Early IRA cost recovery is not invented. Complete qualifying IRA-to-Roth
transfers need an explicit assessment; amounts withheld/not transferred cannot use
that exclusion. Basis differences are caller-established state amounts.
[PA gross-compensation guide](https://www.pa.gov/agencies/revenue/forms-and-publications/pa-personal-income-tax-guide/gross-compensation),
[PA conversion FAQ](https://revenue-pa.custhelp.com/app/answers/detail/a_id/274/kw/retirement),
[2026 PA estimated-tax instructions](https://www.pa.gov/content/dam/copapwp-pagov/en/revenue/documents/formsandpublications/formsforindividuals/pit/documents/2026/2026_rev-413i.pdf).

New York applies qualifying private retirement/IRA/conversion exclusions per owner,
20000 shared, age59.5+, without spouse transfer. Qualifying federal/NY government
pensions are fully excluded first. The older conversion memo is used only for its
immediate-recognition rule, not its obsolete1998 spreading election. Current2026
instructions require supplemental tax when NY AGI exceeds107650. Nine worksheets
would be disproportionate here, so those scenarios are UNSUPPORTED after adjustments.
Mid-year distributions require caller qualification for post-eligibility receipts.
[NY senior guidance](https://www.tax.ny.gov/pit/file/information_for_seniors.htm),
[Conversion memo](https://www.tax.ny.gov/pdf/memos/income/m98_7i.pdf),
[2026 instructions](https://www.tax.ny.gov/pdf/current_forms/it/it2105i.pdf).

Colorado uses federal taxable income and4.4%; no federal standard deduction repeats.
At55-64 the allowance is20000 per person; at65+ it is24000. Full taxable SS at65+
and qualifying low-AGI55+ SS consume pension room, rather than stacking allowances.
Joint taxable SS must use the official gross-benefit proportional allocation; the
prototype checks cents and rejects inconsistent input. Premature IRA exceptions
must be assessed, not inferred from age55 alone. High-income2026 deduction addbacks
need unavailable deduction details; Roth conversion subtraction is not independently
established here and is deliberately unsupported.
[CO retirement guide](https://tax.colorado.gov/sites/tax/files/documents/ITT_Social_Security_Pensions_and_Annuities_Jan_2025.pdf),
[2026 tax guide](https://tax.colorado.gov/sites/tax/files/documents/Individual_Income_Tax_Guide_January_2026.pdf).

New Jersey remains STATE_DEFINED_INCOME. Pension exclusion is age62+, limited to
eligible owners, with inclusive upper income bands100000/125000/150000 and zero
above150000. An explicitly established state-taxable conversion amount may differ
from the federal amount; missing assessment cannot become zero-basis taxation.
Other Retirement Income Exclusion can use unused allowance but requires earned-income
eligibility; Special Exclusion requires benefit eligibility. Rather than add earnings,
business and eligibility models, potentially affected cases return UNSUPPORTED.
Gross SS receipt proves the special benefit exclusion is inapplicable; absence of SS
does not prove eligibility. This is conservative admission, not an ORIE calculation.
[January2026 NJ GIT1&2](https://www.nj.gov/treasury/taxation/pdf/pubs/tgi-ee/git1&2.pdf),
[NJ rate schedules](https://www.nj.gov/treasury/taxation/taxtables.shtml).

## Independent fixtures and results

`StateTaxGoldenFixturesTest.fixtures` is the authoritative new fixture definition:
each has2026, filing status, ages, disjoint owner income, gross/federal-taxable SS,
explicit qualification/basis, expected adjustment/exclusion/taxable income/tax/status,
confidence, source and worksheet rationale. Expectations are literal independent
worksheet arithmetic, never generated by the implementation or packaged JSON.
NJ eligible fixtures include actual gross40000/federal-taxable34000 SS per person,
excluded from NJ total, to establish the special-exclusion boundary explicitly.

**58 state fixtures:47 calculated (46 HIGH,1 MEDIUM),11 unsupported/ambiguous.**
Four additional independent Michigan production-comparison fixtures follow below.
Original tests separately cover all eight parsing/arithmetic paths, qualified Roth,
money classification, request contracts and cached concurrency. Passing parsing and
arithmetic is distinguished from the authoritative-rule findings above.

The initial new fixture run caught three manually mistyped NY expectations:2200
times.0515 is113.30, not112.20. Only these new worksheet literals were corrected
after independent multiplication; no calculator or pre-existing golden value changed.


| Fixture | Adjustments | Retirement exclusions | Taxable income | Tax | Status | Confidence |
| --- | --- | --- | --- | --- | --- | --- |
| FL-SINGLE | 0 | 0 | 0 | 0 | CALCULATED | HIGH |
| FL-MFJ | 0 | 0 | 0 | 0 | CALCULATED | HIGH |
| CA-SINGLE | 0 | 0 | 54100 | 1573.08 | CALCULATED | HIGH |
| CA-MFJ | 0 | 0 | 48200 | 418.88 | CALCULATED | HIGH |
| CA-SS-IRA | 17000 | 0 | 37100 | 668.30 | CALCULATED | HIGH |
| CA-CONVERSION | 0 | 0 | 54100 | 1573.08 | CALCULATED | HIGH |
| CA-INVESTMENTS | 0 | 0 | 27157 | 270.58 | CALCULATED | HIGH |
| CA-SURCHARGE | 0 | 0 | 1100000 | 116327.07 | CALCULATED | MEDIUM |
| CA-EARLY-DISTRIBUTION | — | — | — | — | UNSUPPORTED | UNSUPPORTED_AMBIGUOUS |
| CA-DISTRIBUTION-EXACT-AGE | 0 | 0 | 54100 | 1573.08 | CALCULATED | HIGH |
| MI-INTEREST | 0 | 0 | 4100 | 174.25 | CALCULATED | HIGH |
| MI-SINGLE-CAP | 0 | 67610 | 26490 | 1125.83 | CALCULATED | HIGH |
| MI-MFJ-CAP | 0 | 135220 | 52980 | 2251.65 | CALCULATED | HIGH |
| MI-CONVERSION-SHARED | 0 | 67610 | 16490 | 700.83 | CALCULATED | HIGH |
| MI-CONVERSION-BELOW-AGE | 0 | 0 | 4100 | 174.25 | CALCULATED | HIGH |
| MI-CONVERSION-EXACT-AGE | 0 | 10000 | 0 | 0 | CALCULATED | HIGH |
| MI-AGE67-ELECTION | — | — | — | — | UNSUPPORTED | UNSUPPORTED_AMBIGUOUS |
| MI-EMPLOYER-UNKNOWN | — | — | — | — | UNSUPPORTED | UNSUPPORTED_AMBIGUOUS |
| IL-RETIREMENT | 0 | 80000 | 0 | 0 | CALCULATED | HIGH |
| IL-INVESTMENT | 0 | 80000 | 16075 | 795.71 | CALCULATED | HIGH |
| IL-LARGE-CONVERSION | 0 | 300000 | 10000 | 495 | CALCULATED | HIGH |
| IL-CEILING-EXACT | 0 | 0 | 246075 | 12180.71 | CALCULATED | HIGH |
| IL-CEILING-ABOVE | 0 | 0 | 250001 | 12375.05 | CALCULATED | HIGH |
| PA-AGE65-IRA | 0 | 80000 | 10000 | 307 | CALCULATED | HIGH |
| PA-PENSION | 0 | 40000 | 10000 | 307 | CALCULATED | HIGH |
| PA-COMPLETE-CONVERSION | 0 | 60000 | 0 | 0 | CALCULATED | HIGH |
| PA-INVESTMENTS | 0 | 0 | 20000 | 614 | CALCULATED | HIGH |
| PA-EARLY-IRA | — | — | — | — | UNSUPPORTED | UNSUPPORTED_AMBIGUOUS |
| PA-CONVERSION-WITHHOLDING | — | — | — | — | UNSUPPORTED | UNSUPPORTED_AMBIGUOUS |
| NY-PER-PERSON | 0 | 30000 | 23950 | 970.68 | CALCULATED | HIGH |
| NY-GOVERNMENT | 0 | 50000 | 22000 | 1023 | CALCULATED | HIGH |
| NY-CONVERSION-SHARED | 0 | 20000 | 32000 | 1563.00 | CALCULATED | HIGH |
| NY-RECAPTURE-BELOW | 0 | 0 | 99649 | 5311.04 | CALCULATED | HIGH |
| NY-RECAPTURE-EXACT | 0 | 0 | 99650 | 5311.10 | CALCULATED | HIGH |
| NY-RECAPTURE-ABOVE | — | — | — | — | UNSUPPORTED | UNSUPPORTED_AMBIGUOUS |
| CO-AGE-54 | 0 | 0 | 60000 | 2640 | CALCULATED | HIGH |
| CO-AGE-55 | 0 | 20000 | 40000 | 1760 | CALCULATED | HIGH |
| CO-AGE-64 | 0 | 20000 | 40000 | 1760 | CALCULATED | HIGH |
| CO-AGE-64.5 | 0 | 20000 | 40000 | 1760 | CALCULATED | HIGH |
| CO-AGE-65 | 0 | 24000 | 36000 | 1584 | CALCULATED | HIGH |
| CO-SHARED-LIMIT | 0 | 24000 | 26000 | 1144 | CALCULATED | HIGH |
| CO-SS-LOW-AGI | 0 | 25000 | 25000 | 1100 | CALCULATED | HIGH |
| CO-SS-ABOVE-AGI | 0 | 20000 | 30000 | 1320 | CALCULATED | HIGH |
| CO-ADDBACK-EXACT | 0 | 0 | 280000 | 12320 | CALCULATED | HIGH |
| CO-ADDBACK-ABOVE | — | — | — | — | UNSUPPORTED | UNSUPPORTED_AMBIGUOUS |
| CO-CONVERSION | — | — | — | — | UNSUPPORTED | UNSUPPORTED_AMBIGUOUS |
| NJ-INCOME-99999 | 0 | 75000 | 22999 | 332.48 | CALCULATED | HIGH |
| NJ-INCOME-100000 | 0 | 75000 | 23000 | 332.50 | CALCULATED | HIGH |
| NJ-INCOME-100001 | 0 | 37500.375 | 60500.625 | 1850.16 | CALCULATED | HIGH |
| NJ-INCOME-125000 | 0 | 46875 | 76125 | 2722.91 | CALCULATED | HIGH |
| NJ-INCOME-125001 | 0 | 23437.6875 | 99563.3125 | 4215.93 | CALCULATED | HIGH |
| NJ-INCOME-150000 | 0 | 28125 | 119875 | 5509.79 | CALCULATED | HIGH |
| NJ-INCOME-150001 | 0 | 0 | 148001 | 7301.41 | CALCULATED | HIGH |
| NJ-MFJ | 0 | 35000 | 101000 | 2805.25 | CALCULATED | HIGH |
| NJ-EXPLICIT-CONVERSION | 0 | 75000 | 13000 | 182 | CALCULATED | HIGH |
| NJ-CONVERSION-BASIS | — | — | — | — | UNSUPPORTED | UNSUPPORTED_AMBIGUOUS |
| NJ-OTHER-RETIREMENT | — | — | — | — | UNSUPPORTED | UNSUPPORTED_AMBIGUOUS |
| NJ-SPECIAL-ELIGIBILITY | — | — | — | — | UNSUPPORTED | UNSUPPORTED_AMBIGUOUS |


## Unsupported scenarios and approximation boundary

Runtime UNSUPPORTED always has an empty estimate, never a successful zero:

- Unknown jurisdiction, any tax year other than2026, or filing status other than
  SINGLE/MARRIED_FILING_JOINTLY. Missing/negative/inconsistent required amounts are
  rejected request contracts rather than tax estimates.
- Incomplete classified federal AGI for PA/NJ state-defined bases; missing required
  retirement qualification or pension source. Assessed INELIGIBLE income receives
  no exclusion; PA nonqualifying state-defined retirement requires basis review.
- CA IRA/employer/pension distributions below59.5 where additional early tax/exception
  is unavailable. Complete taxable conversions themselves are not early withdrawals.
- MI age67+ alternative deductions, public-source/unknown-source pension elections,
  unassessed qualifying distributions/conversions. Under59.5 ordinary conversions
  remain taxable without the modeled retirement subtraction.
- PA positive early IRA income without the modeled death/disability eligibility;
  conversions not explicitly confirmed as complete qualifying transfers, including
  partial/withheld/retained amounts or unestablished conversion eligibility.
- NY adjusted NY AGI>107650; unknown government pension source or qualifying private
  retirement assessment. Supplemental high-income recapture is not approximated by
  continuing ordinary brackets.
- CO federal AGI>300000; all Roth conversions pending authoritative eligibility
  resolution; inconsistent joint taxable-SS allocation; unassessed pension/IRA
  eligibility. A declared premature-IRA exception must actually be assessed.
- NJ conversions without an established state taxable amount (even if an unassessed
  state amount was entered as zero); age-eligible investment income at total NJ
  income<=150000 that could qualify for ORIE; positive remaining income where special
  retirement-exclusion eligibility cannot be proven by gross SS receipt. Admission
  is deliberately conservative and may reject some ultimately unaffected cases.

The API is explicitly a full-year-resident, nonnegative-income, standard-deduction
planning contract. Part-year/nonresident/local taxes, dependent/disability/survivor
special exemptions, tax-exempt municipal-bond sourcing, business/earned-income
classification, itemized deductions, detailed IRA/security basis and specialized
credits/AMT are not admitted as modeled tax-return situations. Caller-established
taxable income and qualification are prerequisites; this API cannot verify facts
that are not represented. Federal-based estimates assume no state/federal basis
adjustments beyond declared rules. State-defined amounts must already reflect any
known basis difference; the calculator does not recover or infer basis.

CA senior/blind credits and personal exemption phaseout are explicitly omitted;
numeric estimates retain that warning. This modest credit approximation is separate
from the materially unsupported CO addback/NY recapture/NJ ORIE cases. No omitted
deduction, credit, tax rate or basis is silently invented as zero.

## Michigan production discrepancies - deferred, not corrected

The initial stop gate identified these issues; the user explicitly authorized
resuming isolated6C while deferring production correction. Evidence is retained:

1. `src/main/java/com/daviddunn/retirementplanner/domain/tax/TaxIncome.java`,
   `getOrdinaryIncomeBeforeSocialSecurity` (line67), includes taxable interest.
   `getMichiganRetirementIncome` (line75) omits it.
2. `src/main/java/com/daviddunn/retirementplanner/domain/tax/TaxFundingCalculator.java`,
   trial calculation (lines358-361) and final calculation (lines417-422), pass
   `getOrdinaryIncomeBeforeSocialSecurity` to `MichiganTaxCalculator.calculate`.
3. `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/michigan/MichiganTaxCalculator.java`,
   `calculate`, treats that entire argument as retirement income.
   `MichiganRetirementDeductionCalculator.calculate`/`determineDeductionLimit`
   apply only a filing-status cap, without age/qualification/alternative election.
4. `src/main/resources/rules/government-rules-2026.json`, lines79-85, contains
   retirement caps40762 SINGLE/81524 MFJ; independently published2026 maxima are
   67610/135220. Production also lacks the prototype5900-per-person exemption.

Normal `ProjectionEngine` currently supplies zero taxable interest to funding.
The interest fixture establishes a discrepancy at the supported tax-caller boundary,
not evidence that the normal projection currently generates investment interest.
Changing the caller to `getMichiganRetirementIncome` alone would not be sufficient:
taxable interest must remain in the tax base while the subtraction is limited to
eligible retirement income. Production alternative age67 treatment is absent, so
it cannot explain the interest subtraction in these age60 fixtures.

All four comparisons are2026 full-year residents, ages60/60, no SS/conversion,
qualified IRA income with no state basis difference, no dependents/special credits;
joint income is equally allocated. They reproduce the exact funding argument
preparation but do not run an entire iterative funding projection. Legacy tax is
rounded to cents only for comparison. Expectations are independent literal arithmetic.

| Fixture | Production subtraction | Production TI | Production tax | Prototype retirement subtraction | Prototype personal exemption | Prototype TI | Prototype tax |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Single interest10000 | 10000 | 0 | 0.00 | 0 | 5900 | 4100 | 174.25 |
| MFJ interest20000 | 20000 | 0 | 0.00 | 0 | 11800 | 8200 | 348.50 |
| Single IRA100000 | 40762 | 59238 | 2517.62 | 67610 | 5900 | 26490 | 1125.83 |
| MFJ IRA200000 | 81524 | 118476 | 5035.23 | 135220 | 11800 | 52980 | 2251.65 |

`MichiganPrototypeComparisonTest` preserves all four observed differences without
modifying the production implementation or any existing expectation. All are HIGH
confidence for this restricted comparison, not a certification of either full return.
The initial gate reproduction/log remain under ignored `target/` as historical
diagnostics. Initial prototype status was PROVISIONAL; the bounded6C cases now use
CALCULATED. These production issues remain explicitly unresolved for a separate task.

## Future effective-rate override

Keep two user-selected modes, `SIMPLIFIED_STATE_ESTIMATE` and
`EFFECTIVE_RATE_OVERRIDE`. Recommend an explicitly labeled rate multiplied by
nonnegative **federal AGI**, rounded HALF_UP to cents, replacing (not adding to)
the calculated state tax for the plan/year. This is not a marginal tax rate.

| Base | Advantage | Limitation |
| --- | --- | --- |
| Federal AGI - recommended | Stable, already available even when state rules are unsupported; easy to explain | Includes federally taxable income excluded by some states; excludes federally exempt amounts potentially taxed by a state; user must choose a rate for this base |
| Simplified state AGI | Better reflects exclusions before deductions | Can itself be unsupported; definitions differ across states |
| Simplified state taxable income | Most directly matches a state income-tax base | Depends on supported deductions/exclusions; cannot provide a fallback when they are unavailable |

The original research JSON suggests SIMPLIFIED_STATE_TAXABLE_INCOME; this phase
preserves that metadata and documents the different future recommendation instead
of silently changing override semantics. No override API/UI/persistence was added.
User override belongs in future plan configuration, not the read-only state-law JSON.

## Files

Created in Phase6C:

- `PHASE-6C-STATE-TAX-DATASET-CHANGES.json`
- `src/test/resources/tax/state/state-tax-2026-phase6b.json`
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/StateTaxGoldenFixturesTest.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/StateTaxHardeningTest.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/MichiganPrototypeComparisonTest.java`

Modified existing Phase6B/gate artifacts:

- `PHASE-6C-EIGHT-STATE-TAX-VALIDATION.md` (replaces the stopped-gate report with this report)
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/StateTaxRules.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/StateTaxResult.java`
- `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/SimplifiedStateTaxCalculator.java`
- `src/main/java/com/daviddunn/retirementplanner/persistence/StateTaxDatasetRepository.java`
- `src/main/resources/tax/state/state-tax-2026.json`
- `src/test/java/com/daviddunn/retirementplanner/domain/tax/state/simplified/SimplifiedStateTaxCalculatorTest.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/StateTaxDatasetRepositoryTest.java`

Phase6B artifacts were already untracked when6C resumed; Git's untracked label is
not evidence they were all created in this phase. Supplied root JSON, dataset notes,
6A/6B reports, StateTaxRequest and all existing application financial/UI/persistence
code and existing financial tests remain unchanged. Temporary tools/logs are ignored
under `target/`. Nothing was staged, committed or pushed.

## Regression verification

IntelliJ bundled Maven, always with the repository-local cache:
`-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.

- Focused prototype suite: **204 tests,0 failures/errors/skips**. Includes original
  105 tests (only verified prototype interpretation/status assertions updated),
  58 state golden fixtures,4 matched Michigan fixtures,16 hardening scenarios,
  20 new malformed datasets and1 complete patch replay.
  Log: `target/phase6c-focused-final.log`.
- Targeted existing tax/funding/federal/Roth-fill/single-person/golden/persistence:
  **79 existing tests passed**, plus4 comparison fixtures =83 total,0 failures/errors/skips.
  Log: `target/phase6c-existing-tax-regressions.log`.
- Non-benchmark: **2001 tests,11 skipped,0 failures/errors**, BUILD SUCCESS.
  Log: `target/phase6c-nonbenchmark.log`.
- Benchmark-inclusive full suite: **2022 tests,15 skipped,0 failures/errors**, BUILD SUCCESS (5m19s).
  Log: `target/phase6c-full.log`.
- `git diff --check`: **passed**. All **17 untracked artifacts** also passed
  `git diff --no-index --check` with CRLF recognized as line endings (`cr-at-eol`),
  while preserving trailing-whitespace/blank-EOF/space-before-tab checks. Git's tracked
  diff alone does not cover these Phase6B/6C artifacts. No staged changes exist.

**No existing production expected financial value changed.** Federal/legacy Michigan,
RMDs, Roth conversions, tax funding, Medicare/IRMAA, SS, single/couple behavior,
deterministic/longevity-weighted/seeded Monte Carlo results, reports/PDF, plan JSON
and wizard expectations remain unchanged. Existing tests were not rewritten to
match the prototype. Passing regressions preserves behavior, not legal accuracy
of the deferred Michigan production calculations.

Standalone prototype estimates intentionally change where verified6C rules require
it: qualifying conversions, coordinated CO limits, completed-age bands and NJ
endpoints. Previously misleading numeric scenarios now become UNSUPPORTED. Those
prototype-only changes are completely traceable in the ledger and fixtures.

## Performance and next phase

Rules remain loaded once, immutable and shared safely; all calculation scratch and
shared-limit accounting are request-local. No network/parsing/table-loading occurs
during calculation. The existing200-call/four-thread determinism/cache test passes.
No speculative microbenchmark or mutable global calculation cache was added.

Recommend Phase6D as a bounded integration-design/admission phase first: approve
state/year selection, classified owner income adapters, taxable conversion/basis
contracts, unsupported-result behavior and an explicit user-owned override policy.
Do not silently route unsupported states/years to zero or the legacy Michigan path.
Resolve the separate Michigan production task independently. Before general
eight-state retirement projection integration, address age67 MI alternatives,
CO conversion eligibility and NJ additional-exclusion eligibility, and approve
future-year tax-rule assumptions. Typical multi-year plans cross these boundaries.

The isolated prototype is hardened and useful within its declared bounds; it is
not cleared for unrestricted eight-state projections. Integration, UI, plan fields,
withdrawal/Roth/analyzer/Monte Carlo routing and reporting remain deferred until
explicit user approval. No integration was performed here.
