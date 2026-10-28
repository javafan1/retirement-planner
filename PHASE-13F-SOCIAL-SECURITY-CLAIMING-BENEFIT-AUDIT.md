# Phase 13F — Social Security Claiming Benefit Audit

## 1. Executive finding and repository baseline

**Confirmed presentation defect: the Summary tab mixes two monetary reference bases.** A restored, matching accepted projection displays its saved `IncomeSummary` benefit without COLA. Selecting a different claiming age switches the same label to a preview with COLA through the selected claiming year. Returning to the saved age switches back to the uninflated summary. This can make an earlier age appear to pay more per month.

No evidence was found that this branch changes projected Social Security cash flow, tax inputs, analyzer rankings or immutable financial results. Restoration faithfully retains an existing base-dollar summary; it is the inconsistent use of that summary versus a future-dollar preview that is defective. This is **presentation-only with a restoration-dependent trigger**, not demonstrated JSON corruption or an engine-formula defect.

Audit baseline: `main`, HEAD `84ec5e03f73682d691007f68fff10f86105afd98`; working tree and index were clean at entry. Production code, tests, CSS, schemas, fingerprints, historical payloads and financial golden fixtures were not modified. Only this tracked-report candidate was added; temporary diagnostic code/data/logs are beneath ignored `target/phase13f/`.

## 2. Reproduction and measurement limits

The user's native observations—David 70: **$4,788**, then 69: **$5,192**—are the reported evidence, approximately +8.44%. They were not independently reproduced to the dollar because no actual user-plan file or its birth date/PIA/valuation year/COLA was used. This audit deliberately did not open or modify personal plan files or application preferences.

A deterministic synthetic reproduction uses the real JavaFX `ResultsSummaryView` row creation and ComboBox listener, a real controller, accepted projections, JSON Save/reload and component restoration. The temporary probe invokes the private `loadSocialSecurity` path on FX with the controller's active plan; it does not automate the entire native application's menus/startup. It uses fixture-only repositories/settings beneath `target/phase13f/fixture/`.

Fixture:

- Primary: born 1963-06-04; FRA 67; full-retirement monthly amount **$4,000.00**, expressed in 2026 benefit-valuation-year dollars; configured claim age 70.
- Spouse: born 1965-02-28; FRA 67; full-retirement monthly amount **$2,000.00**, also valuation year 2026; configured claim age 62.
- Plan economic Social Security COLA **2.5%**; projection starts 2026-01-01 for 12 years.
- Source-level legacy COLA intentionally **99%**, demonstrating that the preview and engine use the economic COLA, not that legacy field.

| Scenario | Primary initial 70 | Change to 69 | Return to 70 | Spouse initial 62 |
|---|---:|---:|---:|---:|
| Freshly calculated projection, not restored | $5,896 | $5,381 | $5,896 | $1,435 |
| Saved complete projection reopened/restored | $4,960 | $5,381 | $4,960 | $1,400 |
| Ordinary Save and reopen after draft selections | $4,960 | $5,381 | $4,960 | $1,400 |
| Switch A70 → B69 → A70, final A70 | $4,960 | $5,381 | $4,960 | $1,400 |
| Restored projection, zero economic COLA | $4,960 | $4,640 | $4,960 | $1,400 |

For independently accepted/reopened B69, the primary initially displays **$4,640** at its saved age 69; selecting 70 displays **$5,896**. Thus the location of the inconsistent base-dollar entry follows the saved configured age, not an intrinsically broken age-70 formula.

The 70→69 restored fixture increase is approximately 8.49% using the displayed whole dollars, closely reproducing the reported mechanism. Every sequential 62–70 preview was independently checked with `BigDecimal` arithmetic. The probe verifies previews do not mutate the plan's frozen financial-input document. Merely editing these ComboBoxes creates drafts; the existing Apply action is required to change plan elections. Ordinary Save does not persist unapplied drafts.

Diagnostic reproduction: `target/phase13f/ClaimingBenefitProbe.java`, compiled classes, `classpath.txt` and `probe.log`. The probe finished with exit 0 and `PROBE PASSED`. It can be rerun against the existing Maven test classpath; its JavaFX FX-thread calls and independent expected-value assertions are retained there for review. This is temporary audit instrumentation, not a new committed JUnit test.

## 3. Values for every claiming age

These are exact cents before the UI rounds to whole dollars. “Raw” means own-retirement adjustment applied to the supplied PIA, without COLA. Preview amounts apply economic COLA through the claim calendar year, **clamped at zero years** when the claim year precedes valuation year. The restored UI is the actual label with a saved primary age of 70 and spouse age of 62.

| Age | FRA-67 factor | Primary raw | Primary preview | Primary restored UI | Spouse raw | Spouse preview | Spouse restored UI |
|---|---:|---:|---:|---:|---:|---:|---:|
| 62 | 0.70 | $2,800.00 | $2,800.00 | $2,800 | $1,400.00 | $1,435.00 | **$1,400** |
| 63 | 0.75 | $3,000.00 | $3,000.00 | $3,000 | $1,500.00 | $1,575.94 | $1,576 |
| 64 | 0.80 | $3,200.00 | $3,280.00 | $3,280 | $1,600.00 | $1,723.03 | $1,723 |
| 65 | 0.8666666667 | $3,466.67 | $3,642.17 | $3,642 | $1,733.33 | $1,913.28 | $1,913 |
| 66 | 0.9333333333 | $3,733.33 | $4,020.39 | $4,020 | $1,866.67 | $2,111.96 | $2,112 |
| 67 | 1.00 | $4,000.00 | $4,415.25 | $4,415 | $2,000.00 | $2,319.39 | $2,319 |
| 68 | 1.08 | $4,320.00 | $4,887.68 | $4,888 | $2,160.00 | $2,567.56 | $2,568 |
| 69 | 1.16 | $4,640.00 | $5,380.98 | $5,381 | $2,320.00 | $2,826.69 | $2,827 |
| 70 | 1.24 | $4,960.00 | $5,895.88 | **$4,960** | $2,480.00 | $3,097.18 | $3,097 |

Primary claim years are 2025–2033; spouse claim years are 2027–2035. Fresh, non-restored UI labels use the preview column rounded to dollars at **every** age. With zero COLA, both preview columns equal their raw columns and all restored labels use that consistent basis.

## 4. Exact code paths and initialization/update difference

| Class/file | Relevant methods | Verified role |
|---|---|---|
| `ui/views/ResultsSummaryView.java` | `load`, `loadSocialSecurity`, `addSocialSecuritySource`, `createSocialSecurityRow` | Builds each person's age ComboBox and Monthly Benefit label. Sets the saved age, performs an initial preview, then installs the age listener. Initialization and edits call the same method; they do not have separate age formulas. |
| Same | `updateSocialSecurityPreview` (around lines 3700–3756) | The three-part restoration conditional selects the inconsistent saved-summary path. Otherwise constructs a temporary SS income and previews at the selected claim date. |
| Same | `applySocialSecurity` | Replaces the configured source through the existing handler, preserving PIA, source legacy COLA and benefit valuation year; writes no displayed preview amount. |
| `ui/MainWindow.java` | `loadCurrentPlan`, `setOnSocialSecurityApply` handler | Loads Summary controls and applies elections through the established dirty/invalidation/update workflow. |
| `ui/controller/ApplicationController.java` | `restoreProjectionSnapshots`, `currentProjectionInputsMatch`, `isRestoredProjectionMode` | Restores immutable summaries and marks restored mode; matching requires accepted snapshot, current result and exact configured-input match. Swap publication also sets restored mode, so the same presentation issue can arise after swaps. |
| `domain/projection/summary/IncomeSummaryService.java` | `summarize`, `calculateSocialSecurity` | Sums claiming-adjusted own-retirement amounts, without COLA; rounds to cents. This is not the first projection year's actual payable income. |
| `domain/projection/summary/ProjectionSummaryService.java` | `summarize` | Combines result statistics and that income summary. |
| `app/projection/CompleteProjectionSnapshot.java` | `Summary.capture`, `Summary.restore` | Saves/restores the original summary amounts unchanged, alongside annual results and frozen inputs. |
| `domain/income/SocialSecurityBenefitCalculator.java` | integer-age and exact-date `calculateMonthlyBenefit`, benefit-factor helpers | Early-retirement reductions and delayed credits; no COLA. |
| `domain/income/SocialSecurityIncome.java` | constructor, `getProjectedMonthlyBenefit` | Persists PIA/election/valuation year; temporary preview applies one economic COLA growth operation. |
| `domain/income/SocialSecurityBenefitStartDateCalculator.java` | `calculate` | Existing integer-age UI maps to birth date plus selected years. |
| `domain/socialsecurity/analysis/SocialSecurityStrategyCalculator.java` | `prepare`, `ownBenefit` | Authoritative monthly financial result uses exact election dates and payment-year COLA. |

The exact problematic branch is:

```java
controller.isRestoredProjectionMode()
    && controller.currentProjectionInputsMatch()
    && claimingAge == socialSecurity.getClaimingAge()
```

When true, the label reads `getCurrentProjectionSummary().getIncomeSummary()` for that owner. When the user selects 69 in a plan configured for 70, the plan itself has not changed; the first two predicates remain true but the third becomes false. The label switches to COLA-inflated preview dollars. On returning to 70 it switches back.

This is not a missing initialization listener: initialization deliberately calls the update before installing the listener. The incorrect comparison stems from differing branch meanings. A related tooltip defect is visible in source: the restored branch installs “Original accepted income summary; no benefit calculation was run during restoration.” The alternate path does not clear it, so a later calculated preview may retain that inaccurate explanation.

## 5. COLA/reference-year and benefit contract

For this fixture, independently expected raw benefits use FRA 67:

- Up to 36 early months: 5/9 of 1% reduction per month.
- Additional early months: 5/12 of 1% reduction per month.
- Delayed months: 2/3 of 1% per month, or 8% per year through the supported age-70 election limit.

These factor rules were independently cross-checked against [SSA Early or Late Retirement](https://www.ssa.gov/OACT/quickcalc/early_late.html); FRA 67 and the 30% age-62 own-retirement reduction for these birth cohorts agree with [SSA Retirement Age and Benefit Reduction](https://www.ssa.gov/benefits/retirement/planner/agereduction.html). These are model-level election comparisons; SSA also notes that some delayed credits before age 70 are applied the following January. This audit does not certify exact first-payment administration or propose changing that separate model contract.

Existing code keeps financial values in `BigDecimal`; factor divisions use the established scale/rounding. The UI does not determine cash flow using its whole-dollar formatted label.

Current preview formula:

```text
raw = fullRetirementMonthlyBenefit × claiming adjustment
colaYears = max(claimDate.year − benefitValuationYear, 0)
preview = roundCents(raw × (1 + plan.economicSocialSecurityCola)^colaYears)
```

At primary age 69: **4000 × 1.16 × 1.025^6 = $5,380.98**.
At age 70: raw is **4000 × 1.24 = $4,960.00**, while the consistently inflated preview would be **4000 × 1.24 × 1.025^7 = $5,895.88**. The defective restored UI compares the former age-70 raw value with the age-69 inflated value. There is no reversed delayed-credit calculation and no double-COLA operation in this path.

The raw amount is in the supplied benefit's **benefit-valuation-year dollars**, not necessarily today's dollars. The preview is generally in future claim-year dollars; for earlier claim years the zero clamp means it stays in valuation-year dollars. Neither is a lifetime cumulative benefit or expected present-value ranking.

Supported UI and persisted `SocialSecurityIncome` ages are restricted to 62–70. `SocialSecurityClaimingElection` separately validates exact claim dates within its earliest/latest age bounds. The low-level benefit-factor helper itself does not cap arbitrary out-of-contract ages/dates after 70; it relies on caller validation. This is not the reported 69/70 defect and should not be silently changed in a display correction. The integer-age and exact-date APIs also intentionally retain their different established January-1/FRA handling; this audit does not propose changing them.

## 6. Other consumers and financial impact

| Consumer | Finding |
|---|---|
| Primary and spouse Summary rows | Both affected. Their saved configured age can use base dollars while other ages use inflated preview dollars. Single-person plans use the same row method. |
| Dashboard | `DashboardView.load` uses `IncomeSummary` directly. It consistently displays the raw summary, but may disagree with Summary's fresh/edited preview. It does not exhibit the age-change branch by itself. |
| Income Sources / Social Security assumptions | `IncomeSourcesView` explicitly shows `FRA Benefit` from the source. Its generic Monthly Benefit column is for pensions. It does not consume this Summary preview label. |
| Deterministic projection | `SocialSecurityProjectionIncomeProvider` builds an authoritative monthly request (or explicit compatibility fallback) from source PIA/elections, valuation year and economic COLA. It never reads the preview label. |
| SS Only / weighted SS | Request factories preserve PIA, valuation year and economic COLA. `SocialSecurityStrategyCalculator.ownBenefit` uses payment-year growth once. Mortality/discounting ranks lifetime values independently of this label. |
| Deterministic/weighted integrated and Quick | Consume isolated plan/election requests and projection outcomes, not Summary label text. No financial-output defect demonstrated from this UI branch. |
| Break-Even | Uses accepted annual `ProjectionYear.getSocialSecurityResult().householdBenefit()` and established directional/shared-year methodology, not this label. |
| Monte Carlo | Uses captured plan/elections through the projection engine. The inflation regression passes; no preview-string dependency found. |
| PDF exports | Projection PDF assumptions write original FRA benefit, election date/age and valuation year; annual tables/charts use accepted financial data. Analyzer reports use frozen result contexts. They do not scrape the Summary preview label. Relevant SS/Quick PDF tests pass. |
| Saved/restored projections | Snapshot summary and annual amounts are preserved separately and restored exactly. Saving a raw summary does not turn it into claim-year dollars. Their faithful restoration exposes the mixed UI basis; no snapshot rewrite is appropriate. |

A direct synthetic annual-result check confirms the 2034 primary own benefit **$72,519.36** and spouse own benefit **$20,469.12**, exactly matching independent `(raw × 1.025^8, rounded monthly to cents) × 12`. This checks the engine's separate financial path and single COLA application for this fixture; it is not a proof for every legacy/cohort/survivor scenario.

Additional scope cautions: the saved income summary aggregates all SS sources for each person, whereas the Summary editor creates a row for the first SS source. Do not assume an aggregate is a per-source preview for an unusual multiple-source legacy plan. The restored branch also falls away if configured inputs no longer match the accepted projection, potentially changing the label's basis without changing its selected age. Both reinforce the need for an explicit row-level display contract, not changing stored summaries.

## 7. Recommended smallest correction — pending approval

**Recommend a presentation-only contract: “Monthly Own-Retirement Benefit — [benefit valuation year] dollars.”** Use the existing claiming-age adjustment on the supplied FRA amount consistently for all row ages, startup, recalculation, restoration and plan switching. Exclude COLA from this particular comparison so all ages use the same monetary reference basis. Preserve the source's exact valuation year; do not call it “today's dollars” unless that really is its year.

Implement narrowly in `ResultsSummaryView` with focused UI/presentation tests, optionally extracting a small pure preview presenter if useful. Keep `IncomeSummaryService`, annual engines, original snapshot summaries and historical PDF payloads unchanged. Read source-specific values rather than treating an aggregate income summary as an arbitrary row's value. Replace/clear the restored-only tooltip when the display context changes, and explain that this is the gross own-retirement election amount, not the household spousal/survivor payment or deposit after deductions.

Alternative policy requiring explicit selection: retain the existing future claim-year preview everywhere, including restored initial rows, clearly label each claim year and derive only a display preview from recorded inputs. That changes startup amounts and compares different-year dollars across ages; the current zero-clamp behavior also needs honest wording. It is less suitable for a same-reference-year age comparison. Do not fix by changing `IncomeSummaryService` to inflate stored summaries, altering COLA inputs, or patching values to enforce monotonicity.

No projection/analyzer calculation, automatic recalculation, snapshot rewrite, election change or dirty-state change should be added to merely render or edit a preview. Existing Apply behavior, ownership, admission, freshness and generation guards remain intact.

## 8. Regression strategy for an approved correction

Add deterministic JUnit coverage using independent expected values, extending existing fixtures where practical:

1. New Summary claiming-benefit presentation/UI test: primary and spouse, ages 62–70, exact raw amounts/factors and display year; whole-dollar formatting only after exact-value assertions.
2. Fresh projection versus saved/reopened/restored projection versus plan switch: identical input/election produces identical display semantics. Include A70 → B69 → A70 and single-person plans.
3. Initial 70 →69 →70 and full sequential ages; label and tooltip must track the chosen contract, not change basis at the configured age.
4. Zero, nonzero and different economic COLA values; a base-year preview remains unchanged by COLA, while annual financial results still receive the established one-time growth. Preserve source-level legacy COLA isolation.
5. Different benefit valuation years, claim year before/at/after valuation year, missing legacy valuation year default, and saved original context versus unapplied draft controls.
6. FRA-month cohorts, precise-date/January-1 boundary tests and supported age-70 upper bounds. Preserve existing calculator contracts; any independent formula/bounds change requires a separate approval.
7. Save/reload with unchanged inputs, Apply→successful projection→Save/reload, unapplied draft Save, stale configured inputs, restored missing summaries and unusual multiple SS sources.
8. Snapshot UUID/input document/annual-result identity, no plan mutation or dirty change during preview, no projection/analyzer invocation during restoration or display. Use throwing engine fixtures where appropriate.
9. Dashboard/base-summary agreement where intended; existing projection, SS analyzer, weighted, Break-Even, Monte Carlo and PDF financial expectations remain unchanged. Do not regenerate goldens.

Recommended production scope is one view/presenter plus its focused tests. Any broader financial or persisted-summary change should return to approval.

## 9. Verification and decisions

Existing focused tests, IntelliJ bundled Maven, project-local `.codex-m2/repository`, 4 GiB test heap:

| Run | Total | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Benefit factors/dates, source income/COLA, start dates, projection income provider, strategy calculator, projection-year detail and Summary metric tests | 65 | 65 | 0 | 0 | 0 |
| IncomeSummaryServiceTest, ProjectionSnapshotPersistenceTest, BreakEvenAnalyzerTest, MonteCarloInflationRegressionTest, SocialSecurityAndQuickPdfTest | 44 | 44 | 0 | 0 | 0 |
| Full Maven regression | 3649 | 3605 | 0 | 0 | 44 |

Full suite: BUILD SUCCESS, exit 0, 10:41 elapsed. The 44 skips are existing opt-in/resource-dependent diagnostic, capacity and visual-preview tests; no exclusions or skip annotations were added. Financial golden fixtures were neither changed nor regenerated.

Logs: `target/phase13f/focused.log`, `consumer-tests.log`, `full-suite.log`, `probe.log`. The temporary probe passed independently of JUnit. No production or tracked test changes were made. The first targeted selector included a nonexistent `CompleteProjectionSnapshotTest` name; actual snapshot round-trip coverage was subsequently run under the existing `ProjectionSnapshotPersistenceTest` (13 passing tests), and the tables report only tests actually executed.

Decisions requiring approval before implementation:

1. Approve the recommended same-valuation-year, own-retirement benefit display, with an explicit year label; or choose consistently labeled future claim-year previews instead.
2. Approve a narrow presentation correction and regression tests only; retain stored income-summary meaning, projection/analysis formulas, exact-date versus integer-age contracts and historical values.
3. Any change to low-level after-age-70 validation/capping or legacy multiple-source handling beyond honest display needs separate scope/evidence; neither should be bundled into a 69/70 label fix.

The user's exact $4,788/$5,192 pair remains unverified against their original inputs. That limitation does not prevent confirming the branch mismatch with deterministic independent values. No implementation is authorized or performed by this audit. Stop for policy review and approval.

## Final audit boundary

`git diff --check` passed. Final tracked production/test diff and staging index are empty. The only untracked repository deliverable is this audit report; temporary diagnostic fixtures and logs remain under ignored `target/phase13f/`. No user plans were modified, and nothing was staged, committed or pushed. Branch and HEAD remain unchanged.
