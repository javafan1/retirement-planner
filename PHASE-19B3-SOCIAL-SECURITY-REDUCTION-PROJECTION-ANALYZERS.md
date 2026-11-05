# Phase 19B3 — Social Security Reduction Projection and Analyzer Integration

## Executive summary

Phase 19B3 audited the B1/B2 propagation through projection, analyzer, Monte Carlo, and result-identity paths, then added cross-path and full-plan integration coverage. The audited architecture already carried the policy end to end: `EconomicAssumptions` is captured by the plan, B2 creates the immutable policy and threads it through supported benefit requests, and `ProjectionEngine` consumes the resulting annual household Social Security result. No second reduction was needed or added in the projection, tax, Roth, or Monte Carlo layers.

The B3 increment changed tests only. No production source changes were made for this phase. The focused tests and final full Maven suite passed, including the additional Roth-conversion assertions made after the initial full-suite run. Existing 0% golden cases remained green.

## Projection dependency verification

The verified execution path is:

1. `EconomicAssumptions` stores reduction percent and effective year (B1).
2. `SocialSecurityProjectionIncomeProvider` creates `SocialSecurityBenefitReductionPolicy` from those configured values.
3. The provider passes that policy to the advanced couple strategy request, the single-person own-benefit path, and the legacy annual household path supported by the provider.
4. Phase 19B2 applies the reduction once to the final scheduled payable benefit at the owner/household payment boundary. It does not reduce PIA, COLA, claiming adjustments, or survivor eligibility.
5. `ProjectionYear` receives the provider's `HouseholdSocialSecurityResult`. The engine's generic income path excludes duplicate Social Security income when that authoritative result exists.
6. Guaranteed income, cash flow, taxes, Medicare/IRMAA inputs, withdrawals, portfolio evolution, net worth, and estate calculations use the annual result through existing projection paths.

`TaxFundingCalculator` and `TaxIncomeCalculator` receive the authoritative annual result from projection execution. A compatibility overload that calculates legacy Social Security without a passed result remains for older callers; the production projection path does not use it to recalculate a second benefit stream. No reduction was added inside `ProjectionEngine`, `TaxIncomeCalculator`, tax rules, Medicare calculations, Roth conversion math, or reporting adapters.

The direct/provider integration test checks payment-year boundaries, matches a policy-aware direct strategy request to provider output, and confirms the same annual household amount is present in `ProjectionYear` and guaranteed income. Existing downstream calculations are then allowed to recompute from that changed input.

## Analyzer compatibility matrix

| Path | Policy propagation/result | Verification in this phase |
|---|---|---|
| Couple deterministic claiming grid | B2-derived requests preserve policy; candidate payable schedules use it | Existing grid and strategy tests plus policy request propagation coverage |
| Couple mortality-weighted grid and expected value | Base request and mortality-derived requests retain policy; reduced cash flows feed PV | Existing mortality suite; explicit nonzero single-person mortality comparison |
| Individual deterministic own-retirement | Own benefit uses payment calendar year and policy | Single/direct strategy tests and provider integration |
| Single-person mortality-weighted | Social Security-only valuation uses the policy | Added test verifies expected PV declines for each of the nine strategies under 22% from 2032 |
| Spousal and survivor election alternatives | Final payable amount is reduced after selection; derived requests retain policy | Added integrated override test checks pre-effective parity, post-effective reduction, and owner-component reconciliation |
| Death-age matrix | Matrix requests and derived requests carry policy | Existing matrix/calculator regression coverage; no independent benefit engine found |
| Social Security strategy comparisons / break-even | Selected schedules and payment streams originate from policy-aware strategy calculator | Existing strategy comparison and break-even regressions; shared calculation path confirmed |
| Integrated deterministic quick comparison | Candidate plan retains configured economics; overrides alter elections only | Added 17%/2032 test compares direct selected strategy with the complete plan projection |
| Integrated exhaustive search and claiming heat maps | Full-plan candidates use configured plan; SS-only candidate inputs derive from policy-aware factory | Audited plan-copy and request-factory plumbing; existing exhaustive and heat-map tests |
| Integrated longevity-weighted | Frozen plan and candidate requests retain economics; weighted projections use the common engine | Existing Stage 5E weighted and freshness tests passed; no policy reset found |
| Break-Even Analyzer | Compares calculated projection outcomes and does not calculate a separate SS benefit | Existing Break-Even regressions passed |
| Monte Carlo standalone and comparisons | Frozen plan JSON retains EconomicAssumptions; each scenario runs through common projection | Added fixed-seed reproducibility, 0%/22% timing, and effective-input fingerprint checks |
| Historical results and restored analyzer associations | Stored results remain historical; current assumptions affect identities/freshness, not restored values | Existing Phase 12 association, restore, swap, and freshness tests passed; no historical recalculation added |

No analyzer path audited for this phase was found to rerun claiming analysis or independently calculate unreduced Social Security during export or projection execution. Strategy ranking is allowed to change where an effective year changes the relative value of candidate schedules.

## Monte Carlo verification

`MonteCarloPlanDocument.capture` freezes the configured plan, retaining the new economic assumptions while stripping unrelated transient identity/configuration. Fixed and longevity-adjusted simulations, as well as comparison candidates, execute with their captured plan through projection services. The audit found no simulation orchestration-level Social Security reduction and no separate random generation introduced by export or capture.

New tests establish that:

- The same fixed-seed nonzero-reduction Monte Carlo request is reproducible.
- Its deterministic reference benefit stream is unchanged before 2032 and reduced from 2032 onward.
- Effective input identity changes when the reduction percent or effective year changes.
- Existing random streams, sampling, success calculations, and unsupported-case behavior were not modified.

## Snapshots, fingerprints, and freshness

The policy settings already participate in serialized plan inputs and complete projection snapshots from B1. `AnalysisFingerprintService` intentionally omits default 0%/2032 values where needed to preserve historical default fingerprints; non-default percent and effective year participate in configured input identity. An explicit effective-year change at 0% can therefore change identity while preserving financial outputs, as intended.

Existing analyzer freshness, source revision, projection association, baseline, Save/Load/Swap, and historical restoration contracts remain unchanged. B3 did not rewrite snapshots, historical fingerprints, or analyzer associations. The added Monte Carlo fingerprint test verifies sensitivity to both reduction fields; existing `AnalysisFingerprintTest`, persistence, projection swap, and Phase 12 lifecycle suites passed.

## Cross-path and downstream integration tests

Added and extended tests cover:

- Provider versus direct strategy calculation using identical policy and payment timing.
- Provider output versus `ProjectionYear` Social Security and guaranteed income.
- Integrated strategy override versus equivalent direct and full-plan projection assumptions.
- Survivor/election override behavior and component-to-household reconciliation.
- Single-person mortality-weighted expected values with a nonzero reduction.
- Monte Carlo deterministic reference and reproducibility.
- 0% versus 22% full-plan projection, with an effective year of 2032.

The full-plan scenario includes primary and spouse benefits, tax-deferred and taxable assets, spending, income tax, state tax, and Roth conversion assumptions. It checks that pre-effective benefits remain the same, applicable benefits are reduced, taxable Social Security is recalculated, projection cash-flow/withdrawal effects flow through the engine, actual Roth conversion amounts reconcile by owner, and ending investable assets/estate reflect the changed stream. It does not encode a universal directional expectation for taxes or every downstream metric.

Tested reductions across B1/B2/B3 policy and calculator coverage include 0%, decimal values (including 0.1%), 10%, 17%, 22%, and 100%, with effective-year boundary cases in the focused and existing calculator tests. Existing monthly-versus-annual rounding contracts were preserved; exact agreement is asserted where timing and conventions match, and no rounding convention was changed to force universal equality.

## Functionality already present from Phase 19B2

The following were already implemented in B1/B2 and were verified rather than reimplemented in B3:

- Immutable reduction policy arithmetic and once-only application at payable-benefit boundaries.
- Policy propagation through provider, direct strategy requests, derived claiming/mortality requests, survivor alternatives, and single-person paths.
- Cache-key separation by reduction configuration with mathematical percentage normalization.
- Configured-input fingerprint and snapshot propagation.
- Default 0% calculation compatibility.

The B3 additions provide wider full-plan and analyzer cross-path evidence. The UI assumption controls, Summary disclosures, analyzer assumption displays, and PDF disclosures remain Phase 19B4 work.

## Files changed in Phase 19B3

Only the following test files received Phase 19B3 additions or extensions:

- `src/test/java/com/daviddunn/retirementplanner/domain/projection/SocialSecurityProjectionIncomeProviderTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalysisRequestFactoryTest.java`
- `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloFoundationTest.java`
- `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/IntegratedSocialSecurityStrategyEvaluatorTest.java`
- `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/SinglePersonMortalityAnalysisTest.java`

The production files visible as modified in Git belong to the preserved Phase 19B1/B2 work; they were not changed as part of B3. The report itself is a new untracked file.

## Verification results

### Focused tests

- Focused integration/regression subset: **201 tests, 0 failures, 0 errors, 0 skipped**.
- After adding further assertions for actual Roth conversion execution and owner reconciliation, `SocialSecurityProjectionIncomeProviderTest`: **12 tests, 0 failures, 0 errors, 0 skipped**.

### Full Maven suite

- Command: IntelliJ bundled Maven 3 with Java 25, `mvn test`, and the repository-local `.codex-m2/repository` cache.
- Final run after the last test assertion: **4,020 tests, 0 failures, 0 errors, 46 skipped**; **BUILD SUCCESS** (12m23s).
- The 46 skipped tests are existing opt-in/visual/performance preview tests. Fault-injection tests emitted expected warning traces while passing.
- Existing zero-reduction projection, strategy, Monte Carlo, snapshot, and historical-result regressions passed. No golden values were changed.

### Repository checks

- `git diff --check`: clean. Git printed only its standard LF-to-CRLF working-copy notices.
- No screenshots were required or generated because B3 makes no UI changes.

## Known limitations and remaining Phase 19B4 work

- Phase 19B3 did not add editable UI controls or visible disclosure of the percentage/effective year. Those belong to Phase 19B4.
- The compatibility-only tax overload without an authoritative `HouseholdSocialSecurityResult` remains available for legacy callers; projection engine execution supplies the authoritative result.
- Analyzer coverage is supplied largely through shared request factories/calculators and common projection execution. B3 adds representative nonzero path tests rather than rerunning every exhaustive strategy universe at every percentage.
- Platform-native Windows acceptance is not part of this calculation integration report; no UI or OS behavior changed.

## Git status and preservation

The worktree contains the existing uncommitted Phase 19A/B1/B2 changes and the five test-file extensions listed above, plus this report. No preexisting files were reset, cleaned, staged, committed, or pushed. The branch remains `main`.

## Approval gate

Phase 19B3 implementation and verification are complete. Request explicit approval before starting Phase 19B4 UI and reporting work.
