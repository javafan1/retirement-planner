
Survivor benefit claiming age audit and implementation
====================================================

The audit was performed before implementation. The existing `survivorClaimingAge` is the correct single persisted parameter for a deterministic death scenario. No second survivor field, JSON property, or person-specific plan election was added. Neither person's retirement age or retirement start date is rewritten.

Audit findings
--------------

| Area traced | Finding and action |
| --- | --- |
| `DeathScenarioAssumptions` / Jackson | The shared integer applies to the surviving person, but validation incorrectly restricted it to 62–70. It now accepts ages from 60, including actual late-death ages above 70. The constructor, getter and JSON property names are unchanged. Context-dependent limits belong to the household policy because this value object has no household DOBs. |
| `SocialSecurityIncome` / retirement elections | Retirement age and start date are independent inputs. No survivor-driven rewriting was found; these classes are unchanged. |
| Monthly `SocialSecurityStrategyCalculator` | Already computes own benefits independently, gates survivor benefits on death, resolves entitlement no earlier than the death month, and applies Survivor FRA reductions. This engine and its benefit formulas are unchanged. |
| `HouseholdSocialSecurityResult` / `ProjectionEngine` | The annual result carries own, spousal and survivor amounts separately and supplies authoritative cash-flow and tax inputs. Neither the result type nor the engine orchestration needed changes. |
| `SocialSecurityProjectionIncomeProvider` | Correctly maps PRIMARY_DIES to the spouse and SPOUSE_DIES to the primary, ignoring the dormant age for BOTH_SURVIVE. It now resolves immediate election when death occurs at/after Survivor FRA, including explicit integrated strategies and lifetime scenarios. This also fixes a late election previously omitted when its intended date fell after second death even though immediate survivor entitlement was available. |
| Legacy `HouseholdSocialSecurityIncomeCalculator` | Own retirement already continues while survivor election is delayed. However, the fallback incorrectly used the requested age for the reduction even when the other person died later. It now uses the actual eligible entitlement month through the existing survivor reduction calculator. The legacy annual payment convention and worker-benefit basis remain unchanged. |
| Planning Horizon editors | `AssumptionsView` and `ResultsSummaryView` used fixed 62–70 lists. Both now share a UI adapter backed by the same domain choices, refresh on scenario/death-year edits, retain a valid selection, replace invalid selections, and validate before Apply. Loading/editing controls does not write the plan. BOTH_SURVIVE disables the election and displays Not Applicable. |
| Deterministic integrated analysis | Retirement candidates and conditional survivor elections were already separate. Searches, ordering, ranking, strategy generation and isolated plan copies remain unchanged. Invalid pre-age-60 candidate dates remain errors. The persisted shared election still supplies the deterministic survivor policy. |
| Longevity-weighted analysis | The existing complete strategy has two conditional survivor dates because either person can die first across scenarios. These are existing analyzer strategy/session inputs, not duplicate persisted plan fields. Generated candidates already stop at exact Survivor FRA and are death-gated. The provider's immediate-at-death fix also applies here; mortality distributions, scenario generation, continuation/equivalence admission and optimization methodology are unchanged. The session baseline accepts valid late-death ages above 70. |
| Analyzer matrices / details / heat maps | Labels now say Survivor Benefit Claiming Age and explain that the election is separate from retirement and conditional on the other person's death. BOTH_SURVIVE continues to use neither election. |
| PDFs | Analyzer report headers/details use the expanded term; existing wrapping handles longer titles. The projection PDF uses the same terminology, identifies immediate-at-death ages and omits a dormant BOTH_SURVIVE election. |
| Existing tests | Tests encoding the obsolete 62–70 range were updated. Legacy expectations using age-62 reductions for a death at age 64 were corrected to death-month reductions. The lifetime test that expected omission after an already-post-FRA death now expects immediate entitlement. UI fixtures now supply actual DOBs and valid scenario elections. |

Rules and precision
-------------------

`SurvivorBenefitClaimingPolicy` resolves the survivor from the household and selected scenario. Death remains January 1 of the configured year; age at death uses `Person.getAge(date)`. Before Survivor FRA, whole-year choices start at `max(60, age at death)` and run through the last whole-year age at/below Survivor FRA. At/after Survivor FRA, the sole choice is the actual age at death, displayed as `Immediate at death (Age N)`.

As selected by the user, the persisted field remains whole-year precision for fractional-FRA cohorts. For example, FRA 66 years 8 months provides whole-year choices through 66, with the exact FRA date shown in the tooltip. Adding an exact-FRA plan election is deferred; the analyzers retain their existing exact-FRA candidates. No fractional persistence or new election representation was introduced.

Existing JSON remains readable with its unchanged `survivorClaimingAge` property. A loaded out-of-range election is presented as a corrected draft in the editor and requires Apply to save it; projection calculations do not mutate the persisted plan. Existing later-than-FRA birthdays for deaths *before* FRA retain their stored meaning until edited, preserving compatibility; the UI no longer offers those choices. Death at/after FRA uses immediate semantics even for older stored elections.

The requested normal aged-survivor scope is consistent with [SSA's survivor benefit guidance](https://www.ssa.gov/survivor/amount). Disability, child-in-care, earnings-test and other existing unsupported cases were not expanded.

Regression coverage
-------------------

- Domain choice tests: deaths while the survivor is 58, 60, 62, 64, 66, 67 and 71; both death directions; BOTH_SURVIVE; death-date gating; delayed valid election; post-FRA immediate election.
- UI tests: dynamic range updates, retained valid selection, replaced invalid selection, independent retirement records, correct survivor names, immediate-at-death display, disabled BOTH_SURVIVE control and validation of injected invalid values.
- Projection tests: own retirement at 62 continues through death at 64 and the wait until survivor age 67; deceased retirement remains 70; deterministic and lifetime overrides preserve candidate retirement elections; late-death election is not lost merely because the intended later date follows second death.
- Persistence tests: unchanged JSON property and round trips at ages 60 and 71; existing persistence tests run in the full suite.
- Presentation tests: analyzer assumptions/matrix, frozen result details, baseline labels, heat-map details and text extracted from both real analyzer PDFs.

Changed files
-------------

Paths below are relative to `src/main/java/com/daviddunn/retirementplanner/` unless noted. Existing untracked analyzer files were edited in place; unrelated untracked files were preserved.

| Production files | Purpose |
| --- | --- |
| `domain/income/SurvivorBenefitClaimingPolicy.java` (new) | Household choice constraints and conditional date resolution |
| `domain/income/HouseholdSocialSecurityIncomeCalculator.java` | Legacy actual-entitlement reduction |
| `domain/income/SocialSecuritySurvivorBenefitCalculator.java` | Age-60 support and entitlement-date overload |
| `domain/model/DeathScenarioAssumptions.java` | Compatible age validation and documentation |
| `domain/projection/SocialSecurityProjectionIncomeProvider.java` | Conditional survivor date plumbing for ordinary and integrated projections |
| `ui/views/SurvivorBenefitClaimingControls.java` (new) | Shared control presentation |
| `ui/views/AssumptionsView.java`, `ui/views/ResultsSummaryView.java` | Dynamic labels, choices, validation and HelpText tooltips |
| `ui/help/HelpText.java` | Shared concise explanation |
| `ui/socialsecurity/CurrentStrategyBaseline.java`, `ui/socialsecurity/CurrentStrategyBaselineView.java` | Baseline terminology and late-age support |
| `ui/socialsecurity/ClaimingStrategyHeatMapView.java` | Strategy-detail terminology |
| `ui/socialsecurity/LongevityWeightedIntegratedView.java` | Weighted table/detail terminology |
| `ui/socialsecurity/SocialSecurityAnalyzerInputMatrix.java` | Conditional-use terminology and explanation |
| `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java` | Deterministic and SS-only terminology |
| `ui/socialsecurity/IntegratedAnalyzerReportAdapter.java` | Analyzer PDF terminology |
| `app/export/ProjectionPdfExporter.java` | Projection PDF terminology / immediate and inactive elections |
| `app/socialsecurity/IntegratedRetirementClaimingGridCalculator.java` | Survivor policy description only |

Test paths are relative to `src/test/java/com/daviddunn/retirementplanner/`:

- `domain/income/SurvivorBenefitClaimingPolicyTest.java` (new)
- `domain/income/HouseholdSocialSecurityIncomeCalculatorTest.java`
- `domain/income/SocialSecuritySurvivorBenefitCalculatorTest.java`
- `domain/model/DeathScenarioAssumptionsTest.java`
- `domain/projection/SocialSecurityProjectionIncomeProviderTest.java`
- `domain/projection/HouseholdLifetimeProjectionTest.java`
- `ui/views/SurvivorBenefitClaimingControlsTest.java` (new)
- `ui/views/AssumptionsViewTest.java`
- `ui/socialsecurity/CurrentStrategyBaselineTest.java`
- `ui/socialsecurity/ClaimingStrategyHeatMapViewTest.java`
- `ui/socialsecurity/IntegratedAnalyzerPdfReportTest.java`
- `ui/socialsecurity/SocialSecurityAnalyzerInputPresentationTest.java`
- `ui/socialsecurity/SocialSecurityStrategyAnalyzerDialogStateTest.java`

This report is the additional root file `SURVIVOR-BENEFIT-AUDIT.md`. The prior Planning Horizon wording change and its `PlanningHorizonLayoutTest` were preserved.

Verification
------------

IntelliJ bundled Maven was used with `-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`. Tests that write application settings use the forked JVM option `-DargLine=-Duser.home=C:/Users/david/IdeaProjects/retirement-planner/target/survivor-test-home`, so verification does not write the real user settings.

The final focused run passed 114 tests, zero failures/errors/skips, including Planning Horizon/UI, survivor domain/projection behavior and both analyzer PDF tests. Log: `target/survivor-final-focused-tests.log`.

Full non-benchmark verification used `-Dtest=*Test,!*BenchmarkTest`: **1,305 tests, zero failures, zero errors, 3 skipped; BUILD SUCCESS**. This includes the deterministic integrated, longevity-weighted, survivor/death-scenario, persistence, Planning Horizon/UI and analyzer PDF suites. Log: `target/survivor-full-tests.log`. `git diff --check` passed. No changes were committed.
