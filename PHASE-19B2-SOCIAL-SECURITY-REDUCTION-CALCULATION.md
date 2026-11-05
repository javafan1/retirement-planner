# Phase 19B2 — Social Security Benefit Reduction Calculation

## Summary

Added a shared immutable calendar-year reduction policy and connected it to the advanced monthly calculation, legacy annual household calculation, single-person own-retirement valuation, and analysis request construction. The reduction is applied after scheduled benefits are selected and COLA is calculated. A 0% policy returns the original scheduled amount and preserves existing results.

No calculation was added to `ProjectionEngine`, tax calculators, reporting, or Monte Carlo code. The projection income provider now passes the already-persisted `EconomicAssumptions` values into the existing benefit calculation paths; broader projection and analyzer integration verification remains Phase 19B3.

## Shared policy

`SocialSecurityBenefitReductionPolicy` is an immutable value record containing the reduction percentage and effective year. It validates percentages from 0 through 100 and years from 1000 through 9999. A payment is unchanged when reduction is zero or the payment calendar year precedes the effective year. Otherwise the scheduled payable amount is multiplied by `1 - percent / 100` and rounded to cents using `HALF_UP`.

The percentage is normalized with `stripTrailingZeros()` so mathematically equivalent values such as 17.0 and 17.00 compare equal and share cache keys. Arithmetic remains `BigDecimal`; no floating-point conversion, cumulative reduction, COLA modification, or PIA/claiming adjustment modification was introduced. A 100% reduction yields zero.

## Calculation paths

### Advanced monthly strategy

`SocialSecurityStrategyCalculator` uses the payment month’s calendar year. It first calculates scheduled own, ordinary spousal excess, and survivor excess benefits under existing claiming, eligibility, and COLA rules. It then reduces each owner’s selected payable total once. The own and applicable excess component values are reconciled to that selected total, so the detail fields sum exactly to the selected owner amount despite cent rounding. Household benefit remains the sum of selected primary and spouse amounts.

This handles own retirement, spousal excess, and survivor excess without reducing both a benefit component and its aggregate. The reduction does not change eligibility or survivor selection.

### Legacy annual household calculation

`HouseholdSocialSecurityIncomeCalculator` provides compatibility overloads that use no reduction. Its policy-aware overloads reduce each annual scheduled own benefit and survivor candidate at the projection payment year, then perform the existing max/selection and owner/household aggregation. The projection year, rather than claim year or death year, controls activation.

### Single-person and provider paths

Policy-aware overloads were added to the advanced own-retirement monthly and annual methods. The single-person mortality valuation passes the plan’s existing economic-assumption values into the monthly stream. `SocialSecurityProjectionIncomeProvider` passes the same values to its advanced couple, legacy annual, and single-person own-retirement paths. No second reduction is performed downstream.

## Requests and cache keys

`SocialSecurityStrategyRequest` carries the policy. Existing full and shorter constructor signatures remain available and default to no reduction. A missing policy on an older request also defaults to no reduction.

Claiming-grid, death-age-matrix, and mortality-weighted derived requests retain the base policy. Strategy comparison and death-age-matrix comparison requests reject strategies with differing reduction policies, preserving the existing same-input comparison contract.

`SocialSecurityStrategyCalculator.scheduleKey()` now includes the policy. Different percentages and effective years therefore cannot share a schedule key; scale-equivalent percentages remain equivalent after normalization.

## Rounding and compatibility

Advanced schedules reduce monthly selected payable amounts and aggregate those monthly amounts using existing annual/lifetime aggregation. Legacy annual calculations reduce the already-scheduled annual payable amount at the existing annual boundary. Both use cents `HALF_UP`. Existing zero-reduction outputs are unchanged; no golden values were modified.

The request/provider plumbing carries the policy without introducing mutable shared state. No plan schema, persistence, UI controls, tax behavior, projection-engine rule, or report content was changed in this phase.

## Files changed for Phase 19B2

Production:

- `src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/SocialSecurityBenefitReductionPolicy.java` — new policy.
- `src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityStrategyCalculator.java` — monthly and own-retirement integration, component reconciliation, cache key.
- `src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityStrategyRequest.java` — policy input and compatibility constructors.
- `src/main/java/com/daviddunn/retirementplanner/domain/income/HouseholdSocialSecurityIncomeCalculator.java` — policy-aware legacy annual calculation.
- `src/main/java/com/daviddunn/retirementplanner/domain/projection/SocialSecurityProjectionIncomeProvider.java` — passes persisted assumptions into existing benefit paths.
- `src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityClaimingGridCalculator.java` — derived request propagation.
- `src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityDeathAgeMatrixCalculator.java` — derived request propagation.
- `src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityMortalityWeightedStrategyCalculator.java` — derived request propagation.
- `src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityStrategyComparisonRequest.java` — comparison policy consistency.
- `src/main/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityDeathAgeMatrixRequest.java` — comparison policy consistency.
- `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalysisRequestFactory.java` — obtains policy from the existing plan assumption.
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/SinglePersonMortalityAnalysis.java` — policy-aware single-person monthly valuation.

Tests:

- `src/test/java/com/daviddunn/retirementplanner/domain/socialsecurity/SocialSecurityBenefitReductionPolicyTest.java` — new policy boundary, precision, normalization, and validation tests.
- `src/test/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityStrategyCalculatorTest.java` — payment-year, COLA, spousal/survivor reconciliation, and cache separation tests.
- `src/test/java/com/daviddunn/retirementplanner/domain/socialsecurity/analysis/SocialSecurityClaimingGridCalculatorTest.java` — derived request propagation.
- `src/test/java/com/daviddunn/retirementplanner/domain/income/HouseholdSocialSecurityIncomeCalculatorTest.java` — annual own/survivor policy tests.
- `src/test/java/com/daviddunn/retirementplanner/domain/projection/SocialSecurityProjectionIncomeProviderTest.java` — policy propagation into legacy and single-person provider paths.

## Verification

Focused command covered the policy, advanced strategy, claiming grid, mortality-weighted grid, annual household calculator, projection income provider, single-person mortality analysis, strategy comparison, and death-age matrix:

- 100 tests run; 0 failures; 0 errors; 0 skipped.
- Existing zero-reduction Social Security and single-person golden/regression tests passed.

Full Maven regression suite:

- 4,014 tests run; 0 failures; 0 errors; 46 skipped.
- Build succeeded. The skipped tests are existing opt-in/preview/benchmark cases; no tests were disabled or weakened.
- `git diff --check` passed. Git emitted only existing LF-to-CRLF working-copy notices.

## Deferred work and limitations

- Phase 19B3: verify main projection, deterministic and longevity-weighted analyzer results, freshness, Monte Carlo, and downstream tax/financial propagation with nonzero reductions. This phase did not change tax calculation or financial orchestration.
- Phase 19B4: add user controls and disclosures to Assumptions, Summary, projection details, analyzer summaries, and applicable PDF reports.
- The legacy annual household calculator retains its existing annual scheduling and rounding conventions; this work applies the reduction at that path’s annual payable boundary.

## Working-tree preservation

The working tree already contained Phase 19B1 domain, persistence, fingerprint, UI, test, and report changes before Phase 19B2. Those files were preserved and were not reverted, staged, committed, or pushed. Phase 19B2 changes are limited to the calculation/request/provider files and tests listed above, plus this report. No Git history or unrelated working-tree content was changed.

**Phase 19B2 is complete. Stop here for review and approval before Phase 19B3.**
