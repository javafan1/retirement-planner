# SSA Period Life Table 2022

- Issuing organization: U.S. Social Security Administration.
- Table: Annual Statistical Supplement, 2025, Table 4.C6, “Period life
  table (mortality and survival indicators, by sex and age), 2022.”
- Mortality experience year: 2022.
- Publication context: Annual Statistical Supplement, 2025, completed March
  2026.
- Table type: period life table.
- Official source: https://www.ssa.gov/policy/docs/statcomps/supplement/2025/4c.html
- Added to the application: 2026-08-30.
- CSV SHA-256: `CC1AEF00E697B4962A5754DD4C42DAE55C94034D44D36055538AD0D7221591F2`.

SSA defines the death-probability column as the probability of dying within
one year for a person alive at the listed exact age. The published table covers
exact ages 0 through 119 and reports male and female probabilities to six
decimal places. Age 119 has probability 1.000000 for both categories.

## Application resource transformation

The official table publishes exact age, death probability, number of lives,
and life expectancy for each sex. The application CSV retains only:

```text
age,male_qx,female_qx
```

No probability was interpolated, smoothed, rescaled, extrapolated, normalized,
or otherwise mathematically transformed. Leading zeroes were added solely to
make the published decimals explicit CSV decimal values.

The application treats attained age 119 as the last qx interval and maps it to
terminal deterministic death age 120. The existing provider assigns all
remaining survival probability to that terminal bucket, so no probability is
dropped and no death age above 120 is generated.

This is a population-based period table. It does not model future mortality
improvement, cohort improvement, individual health, or personalized longevity.

## Optional scenario adjustment

The analyzer may apply a person-specific, constant proportional-hazard scenario
factor after table lookup: `adjustedQ = 1 - (1 - q)^factor`. Factor 1 preserves
the published qx exactly; factors above 1 increase modeled mortality and factors
below 1 decrease it. The adjustment is recorded separately from SSA source
metadata and is a planning scenario assumption, not a medically calibrated or
individualized actuarial estimate.
