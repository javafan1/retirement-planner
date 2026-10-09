# Eight-state 2026 retirement-tax dataset — research draft

Generated October 8, 2026. This is a populated, read-only JSON data *draft* for an approximate full-year-resident retirement planner, **not production-ready tax logic**. It covers CA, MI, FL, IL, PA, NY, CO, NJ and the SINGLE/MARRIED_FILING_JOINTLY statuses. Each record includes bracket/rate information, a tax base, exemptions, retirement adjustment rules, limitations and official sources.

## Interpretation and integration cautions

- `calculationStatus: REVIEW_REQUIRED` means the record contains substantive verified data but needs a testable interpretation and/or omitted-rule review. Never treat absent data or unsupported rule types as zero tax. Florida is the only `READY_FOR_SIMPLIFIED_MODEL` record.
- The JSON deliberately contains **rule descriptors**; Java must implement and test each named rule method and eligibility condition. There is no calculator in this package.
- Roth conversions should **not** be automatically exempted merely because a state exempts qualifying pension/IRA distributions. Confirm classification for each state, especially IL/PA/MI.
- Colorado starts from **federal taxable income**, not federal AGI; do not deduct federal standard deduction twice. Colorado Social Security/pension interaction requires allocation logic.
- New York's high-income tax recapture is omitted; the bracket schedule alone can underestimate liability in some cases.
- Michigan's 2026 alternative senior standard deduction/tier/public safety elections are omitted. Keep existing Michigan calculation authoritative until regression comparisons are completed.
- California's exemption credit phaseout is omitted; the 1% high-income mental health surcharge is represented.
- New Jersey's *other retirement income exclusion* is omitted, and pension exclusion percentages are conditional on the appropriate NJ total-income measure.
- Pennsylvania uses state-defined income, not federal AGI, and qualifying retirement distributions are conditional; early withdrawals and Roth conversions need special review.
- The override belongs in a user's saved plan, not in this read-only tax table. A flat effective rate is not an exact marginal rate for Roth optimization.
- This is a **tax year 2026 snapshot**; the planner needs an explicit future-year projection policy, and a reviewed yearly refresh process.

## Representative official sources

- California FTB: https://www.ftb.ca.gov/about-ftb/newsroom/tax-news/
- Michigan Treasury: https://www.michigan.gov/taxes/iit/tax-guidance/tax-situations/retirement-and-pension-benefits
- Florida DOR: https://floridarevenue.com/faq/Pages/FAQDetails.aspx?FAQID=1466
- Illinois DOR: https://tax.illinois.gov/individuals/pension.html
- Pennsylvania DOR: https://www.pa.gov/agencies/revenue/forms-and-publications/pa-personal-income-tax-guide/gross-compensation
- New York Tax Law: https://www.nysenate.gov/legislation/laws/TAX/601
- Colorado General Assembly: https://content.leg.colorado.gov/agencies/legislative-council-staff/individual-income-tax%C2%A0
- New Jersey Treasury: https://www.nj.gov/treasury/taxation/njit7.shtml

All source URLs are also embedded in the jurisdiction records.
