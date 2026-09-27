# Monte Carlo Mortality Phase 2 — Commit E

## Starting state and scope

Starting HEAD: `5a64a955d1972572048f2618450fa7dc28898abf` — `Add mortality-aware Monte Carlo aggregation` (Commit D).
Both `git status --short` and `git status` confirmed a clean working tree before implementation.

Survivor-age correction precondition (2026-09-25): HEAD was unchanged, and `git status --short`
contained exactly the original 12 uncommitted Commit E paths listed below. No unrelated changes were present.
The correction amended those changes in place; nothing was reset, stashed, discarded, staged, or committed.

Commit E adds presentation and orchestration to the existing **Analysis → Monte Carlo Retirement Analysis** dialog.
There is no second dialog, strategy comparison, PDF change, persisted analysis setting, parallel simulation execution,
new mortality table, stochastic inflation, or survivor-benefit/mortality/random-stream/percentile formula change.
The targeted correction extends the mortality request and projection context to supply an explicit session survivor age.
CSS, dependencies, Maven configuration, and Java version are unchanged.

## Original uncommitted Commit E file boundary (before correction)

Production files created:

```text
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMode.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityPresentation.java
```

Production files modified:

```text
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloFanChart.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloFanModel.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPresentation.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRun.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRunService.java
```

Test files created:

```text
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityUiFixtures.java
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityViewTest.java
```

Test files modified:

```text
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPreviewTest.java
```


Documentation created: `MONTE-CARLO-MORTALITY-COMMIT-E.md` (this report).
Generated screenshots, geometry records, and Maven logs are under ignored `target/` and are not part of the proposed commit.

## User-facing behavior

| Requirement | Implemented behavior |
|---|---|
| Modes and default | **Fixed Lifespan** and **Longevity-Adjusted**. Fixed Lifespan remains the default. Mode tooltip explains configured plan lifetime/horizon versus sampled household longevity, both with stochastic returns. |
| Existing defaults | 5,000 simulations, seed 417, volatility 12.00%, expected return from the plan. Opening or changing mode does not run analysis. |
| Mortality assumptions | Two mortality adjustment fields appear in Longevity-Adjusted mode. They reuse `LongevitySessionSettings` and `SocialSecurityMortalityAdjustment`, initialized from the controller's shared longevity session. Standard defaults are 1.00. |
| Adjustment semantics | Same 0.50–3.00 UI range and hazard-multiplier explanation as the longevity analyzer. Verified against `1 - (1 - qx)^factor`: higher factors increase mortality risk; lower factors decrease it. This is not a proportional change in remaining lifespan. |
| Session ownership | Valid edits update the existing controller session, preserving the other analyzer's conditioning date. MC request conditioning is always the plan projection start, as established by Commit B. No plan writes. Different source plans reload their own session defaults. |
| Categories | Read-only categories and names come from `Person`, through existing household adapters. There are no duplicate Male/Female controls. Missing categories use the existing request validation directing users to Person information. |
| Survivor policy | **Survivor Social Security Claiming Age** is always available in Longevity-Adjusted mode. It initializes from an available saved age, otherwise remains blank. It is a session assumption, frozen in the request; missing/invalid analysis age blocks Run without asking users to change their deterministic death scenario. No default is invented. |
| Fixed Run | Existing capture/reference path → `MonteCarloRunService.run` → `MonteCarloAnalyzer.analyzeWithReferenceOutcome`. Existing reference cache, progress, cancellation, and funding constraints are preserved. |
| Longevity Run | FX captures an isolated plan and immutable `MonteCarloMortalityRequest`; existing single worker calls `MonteCarloRunService.runMortality` → `MonteCarloAnalyzer.analyzeMortality`. No deterministic reference is calculated for this mode. |
| Lifecycle | Existing `MonteCarloSession` handles progress, cancellation, failure, generation/revision checks, and publication. Inputs/competing Run are disabled while busy. Errors and cancellation publish no partial result. |
| Result identity | `MonteCarloRun` has explicit sealed Fixed/Mortality payloads. Result rendering follows the completed payload, never the currently selected input mode. Accessing a mortality result as fixed output throws rather than inventing shared semantics. |
| Staleness | Mode, either mortality factor, survivor analysis age, seed, expected return, volatility, simulation count, and controller plan revisions invalidate results without automatic analysis. Displayed result metadata stays frozen. The existing Commit E input invalidation is uniform, rather than mode-specific; the new field follows it. |
| Funding | Mortality headline is **LIFETIME FUNDING PROBABILITY**. Tooltip: “Share of simulated market and lifetime scenarios that completed all modeled obligations through the household's second death.” Fixed wording is unchanged. |
| Fan bands | Existing chart consumes Commit D `annualResults`: P10–P90, P25–P75, and P50 for households living and funded through that completed financial year. No reconstruction from raw worlds; no deceased/failed zeros or carried-forward estates. |
| Reporting range | Uses every authoritative annual result from projection start through the latest sampled final living financial year, independent of nominal plan horizon. Existing adaptive tick spacing is preserved. |
| Selected year | Calendar year, P90/P75/median/P25/P10, living households/requested simulations, funded-through-year/living households, both alive, primary-only and spouse-only counts. Actual frozen names label survivor counts. Counts are the empirical Commit D aggregates. |
| Conditional disclosure | Visible immediately below the chart: annual bands include only households still living and funded through that year. Details explain changing sample counts and excluded deceased/failed observations. |
| Deterministic overlay | Hidden in mortality mode because configured plan lifetimes do not match sampled lifetimes. Fixed mode remains unchanged. No new deterministic mortality projection. |
| SS/Roth/RMD annotations | Hidden with the deterministic reference in mortality mode. The decision is explained in Analysis Details. Fixed-mode claiming markers and actual reference Roth/RMD shading are unchanged. |
| Terminal table | **LIFETIME OUTCOMES — FUNDED SIMULATIONS**. Rows Min, P10, P25, Median, P75, P90, Max; columns Investable Assets, Total Net Worth, After-Tax Estate, Lifetime Taxes. Uses Commit D terminal percentiles directly, only for worlds funded through second death. |
| Variable dates | Displays authoritative earliest/latest successful terminal balance dates in ISO date form next to the section title. No common “Ending Year.” Opening deaths retain their actual opening balance date. |
| Zero success | Shows 0.0%, omits the terminal table, and states that no terminal distribution exists because no simulation funded all obligations through second death. Annual completed prefixes remain inspectable; this is not an application error. |
| All success | Shows 100.0% normally; living and survivor counts remain visible and decline with sampled deaths. |
| Empty annual range | Explains that all sampled lifetimes end at opening, supplies accessible empty-chart text, and retains available opening terminal outcomes. |
| Dollars | Main notice identifies nominal future dollars immediately before second death, varying dates, and nominal lifetime tax totals. Details explicitly exclude PV/constant-dollar/common-year interpretations. |
| Details | Collapsed by default; compact Simulation, Longevity, Funding, Annual chart, Terminal outcomes, and Model limitations sections. Includes settings, categories/factors, mortality table/version, conditioning, January 1 deaths, survivor age, counts, funding probability, distribution conditions, reference hiding, independent mortality/markets, independent spouses, deceased-owner household assets, no inherited-account retitling/beneficiary model, and no stochastic inflation. Fixed details text is preserved. |
| Accessibility | One focusable chart; Left/Right, Home/End; click selects/focuses; hover inspects; no per-point tab stops. Selected text, tooltip, and accessible description use the same formatter, including population counts. Focus border and chart-layout invalidation remain. |
| Interaction cost | Selection, hover, details expansion, and table inspection only read frozen results; none submits analysis work. |

## Original Commit E verification (before survivor-age correction)

All Maven commands use IntelliJ's bundled Maven and
`-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository`.
Dedicated benchmark classes are excluded; no opt-in benchmark is enabled.

- Initial fixed UI/session/fan regression: **16 tests passed** (`target/commit-e-initial-tests.log`).
- New mortality UI/state suite: **21 tests passed** (`target/commit-e-mortality-ui-tests.log`). Covers all requested input invalidations, missing survivor policy/categories, actual background mortality execution, frozen capture, cancellation/error/late publication, mode round trips, zero/100% funding, authoritative terminal/population display, empty/long annual ranges, disclosures, and first/middle/final guide coordinates.
- Broader focused selector: `MonteCarlo*Test,DiscreteProbabilitySamplerTest,*Longevity*Test,*Mortality*Test,*Survivor*Test,Stage5*Test,!*BenchmarkTest`. Initial run had **464 tests, 460 passed, 3 skipped, 1 new test-fixture error** (`target/commit-e-focused-tests.log`). The fixture incorrectly attempted to clear a required Person category through its setter; it now uses a legacy unset-category fixture. The corrected 21-test UI run passed. Existing selected tests passed, including A–D and Stage 5.
- Full non-benchmark selector: `*Test,!*BenchmarkTest`: **1,515 tests, zero failures, zero errors, five skipped; BUILD SUCCESS**. The initial successful run took 1 minute 24 seconds (`target/commit-e-full-tests.log`). Because the redirected PowerShell command reported a nonzero shell status alongside Maven's success log, verification was repeated with an explicit captured native exit status: **Maven exit code 0**, same 1,515-test results in 1 minute 23 seconds (`target/commit-e-full-final.log`). The five skips include the two opt-in previews, which passed separately. Stage 5 thread-liveness tests passed; no isolation rerun or fresh-JVM-per-test workaround was necessary.
- Explicit preview selector: `MonteCarloPreviewTest`, `-Dmontecarlo.preview=true`: **2 tests passed** (`target/commit-e-preview-tests.log`), generating three **1900×1040** screenshots from actual 5,000-simulation runs. Plan JSON remained unchanged.

Screenshots inspected:

1. [Fixed Lifespan](target/monte-carlo-phase2-preview.png): existing overlay/markers/shading, outcomes, selected year 2042, collapsed details, Run/Cancel/Close visible.
2. [Longevity-Adjusted, opening year](target/monte-carlo-longevity-opening-preview.png): populated annual bands and lifetime outcomes, no deterministic annotations.
3. [Longevity-Adjusted, year 2051](target/monte-carlo-longevity-late-preview.png): living **3,130 / 5,000**, funded through year **3,130 / 3,130**, both alive **729**, Alex only **698**, Sam only **1,703**. There are **1,870** deceased households.

The mortality preview reporting range is **2027–2071**, beyond the configured 2056 ending year.
Successful terminal dates are **2027-12-31–2071-12-31**. All 5,000 preview worlds funded successfully; mixed/zero-funding cases use bounded authoritative-result fixtures in UI tests.

Geometry tests compare the selected-year guide to axis and actual rendered median vertices after real FX layout pulses.
Mortality regression years: **2027, 2056, 2085**. Real preview years: **2027, 2051, 2071**.
Existing fixed geometry regression is unchanged and passes, including median and deterministic vertices.
Preview geometry checks assert Run, Cancel, the terminal table, and collapsed details remain within the desktop viewport.
Geometry records: `target/monte-carlo-phase2-preview.txt`, `target/monte-carlo-longevity-preview.txt`.

Visual corrections: the initial extra longevity row pushed Details below the target viewport. Adjustment inputs now share the market-input row, read-only context occupies one line, and terminal dates share the section-title row. Chart height is 330px in mortality mode versus the unchanged 370px fixed preference. Final screenshots show readable axes/legend/readouts, all seven terminal rows, and no clipped/overlapping controls. The outer scroller remains available for expanded details and smaller windows. Screen-reader speech was not manually tested.

## Original regression coverage

Original full-suite results included:

| Verification group | Tests passed |
|---|---:|
| Exact probability sampler and dimension-specific random streams | 14 |
| World generator | 10 |
| Mortality execution | 15 |
| Mortality accumulator, results, and distribution | 18 |
| Existing fixed UI/session/fan tests | 16 |
| New mortality UI/state tests | 21 |

All selected longevity UI/domain, survivor policy, and Stage 5 tests also passed in the complete suite.
The existing fixed-mode geometry test was not changed. Fixed-mode inputs, validation, reference orchestration,
progress/cancellation, probability wording, fan/selected year, overlay/SS/Roth/RMD annotations, outcomes,
details and accessibility passed existing regressions; the new mode-round-trip test verifies restoration of fixed presentation.

`git diff --check` passed with no whitespace errors. Git emitted only LF-to-CRLF conversion notices.
`git diff --cached --name-only` was empty. HEAD remains the starting Commit D hash.

Revised `git status --short` (the complete revised proposed Commit E boundary):

```text
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloAnalyzer.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityRequest.java
 M src/main/java/com/daviddunn/retirementplanner/domain/income/SurvivorBenefitClaimingPolicy.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEvaluationContext.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/SocialSecurityProjectionIncomeProvider.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloFanChart.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloFanModel.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPresentation.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRun.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRunService.java
 M src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityExecutionTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPreviewTest.java
?? MONTE-CARLO-MORTALITY-COMMIT-E.md
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMode.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityPresentation.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSurvivorAssumptionTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityUiFixtures.java
?? src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityViewTest.java
```

There is no scope deviation. Hiding the mortality-mode deterministic line and annotations is the explicitly permitted v1 choice;
it avoids comparing incompatible lifetime horizons. The mortality table uses the requested seven-row orientation while retaining the existing table styling.
Nothing staged or committed; work stops at revised Commit E.

## Survivor-age correction: exact additional changes

Seven paths extend the original 12-file boundary:

| Path | Correction |
|---|---|
| `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityRequest.java` | Modified: freezes optional shared whole-year survivor age; explicit analysis constructor validates it. |
| `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloAnalyzer.java` | Modified: requires the request election and supplies it in each non-opening lifetime execution context. |
| `src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEvaluationContext.java` | Modified: immutable lifetime-only survivor age override, retained by both horizon builders; old constructors preserved. |
| `src/main/java/com/daviddunn/retirementplanner/domain/projection/SocialSecurityProjectionIncomeProvider.java` | Modified: passes the context age to the existing survivor policy in both death directions, falling back to saved policy only for callers without an override. |
| `src/main/java/com/daviddunn/retirementplanner/domain/income/SurvivorBenefitClaimingPolicy.java` | Modified: exposes its existing minimum-age check for reuse; formula and claim-date behavior unchanged. |
| `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityExecutionTest.java` | Modified: expects the frozen request age in execution context and updates missing-request-policy wording. |
| `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSurvivorAssumptionTest.java` | Created: both-direction cash-flow oracles, request/context validation and freezing, exact world/path invariance, opening death and non-mutation. |

Already-uncommitted files amended in place (all retain their original Commit E work):

```text
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityPresentation.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRun.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRunService.java
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityUiFixtures.java
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityViewTest.java
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPreviewTest.java
MONTE-CARLO-MORTALITY-COMMIT-E.md
```

The other four original paths (`MonteCarloMode`, `MonteCarloFanChart`, `MonteCarloFanModel`, and
`MonteCarloPresentation`) were left as implemented by Commit E. Revised total: **19 files**:
2 new and 11 modified production files, 3 new and 2 modified test files, and this new report.

## Survivor-age ownership and execution

The UI uses a whole-year `TextField`, matching the longevity analyzer's existing `CurrentStrategyBaselineView`
survivor-entry convention. Its in-dialog value survives runs, chart selection, details expansion, and mode switches;
a different plan reinitializes it. It is hidden with the other longevity controls in Fixed Lifespan mode.
It is not stored in `RetirementPlan`, controller persisted settings, or mortality-only `LongevitySessionSettings`.

On Run the UI parses the text to `int`; `MonteCarloMortalityRequest` freezes it as `Optional<Integer>`.
There is no authoritative standalone shared claiming-age value type in this project: the existing shared policy
uses `int`/`Integer`, while `SocialSecuritySurvivorClaimingCandidate` is a person-specific exact-date election,
and `SocialSecurityClaimingElection` is an own-retirement election. Neither represents one shared age across two birth dates.
`AnalyzerLongevityAssumptions` and `HouseholdLongevityScenarioFactory` remain mortality-only and unchanged.

The optional request field preserves Commit B's generation-only callers: legacy constructors capture a saved age
if present, and generation still works without one. Mortality **execution** requires a frozen request age.
The explicit UI constructor always supplies and validates it. Validation reuses `SurvivorBenefitClaimingPolicy`:
whole years of at least **60**, without an invented FRA/70 upper cap. As in the existing exact-date entry path,
the birthday dates must be representable by `LocalDate`. No default is applied. The compatibility age 67 in a
legacy deterministic-death constructor is not an authoritative default for this analysis.

For BOTH_SURVIVE with no saved age, the field starts blank. Run is blocked until a valid analysis age is entered,
with “Select a survivor Social Security claiming age for longevity-adjusted Monte Carlo.” The saved scenario stays
BOTH_SURVIVE and its saved survivor age stays absent. PRIMARY_DIES or SPOUSE_DIES initializes from its saved age;
the user can override that value for the analysis without changing the plan or its retirement elections/start dates.

Execution path:

```text
UI session field → immutable MonteCarloMortalityRequest.survivorClaimingAge
 → MonteCarloAnalyzer → ProjectionEvaluationContext.withSurvivorClaimingAge
 → existing ProjectionEngine → SocialSecurityProjectionIncomeProvider
 → existing SurvivorBenefitClaimingPolicy and authoritative Social Security benefit engine
```

The context applies the same age whichever spouse survives. It overrides no own-retirement date or age.
It requires a lifetime scenario and rejects combination with a complete strategy override to avoid ambiguous ownership.
Both context horizon builders retain it. The record's equality/hashCode include it. The request retains its existing
identity-equality semantics; its frozen field is available as reproducibility metadata through the result's retained request.
The redundant age formerly stored in `MonteCarloRun.Mortality` has been removed.

No active or isolated plan mutation transports the election. Tests compare every field of the isolated plan at engine entry
with the original source, as well as the source before/after UI execution and cancellation. Whole-year death convention,
second-death-minus-one horizon, opening estate, funding constraints, compact outcomes, and conditional aggregation stay intact.
The world generator and random streams do not read the age. The 225-world regression compares scenario indices,
both death years, and every annual market return, not only aggregates.

Analysis Details reads the completed result's request: **Survivor Social Security claiming age: 60 (shared analysis
assumption; saved plan elections unchanged)**, for example. Editing to 65 marks the result stale without rerunning;
the actual rendered details still show 60. Fixed-mode execution ignores this field. Uniform input invalidation follows
existing Commit E conventions rather than introducing a new mode-aware stale-state design.

## Correction verification and previews

- Initial execution/oracle checks: **19 passed** (15 existing execution tests plus 4 initial new cases), `target/commit-e-survivor-domain.log`.
- Final survivor request/execution suite: **5 passed**, `target/commit-e-survivor-domain-final.log`, including opening death and representable-date validation.
- Updated mortality UI/state and existing fixed UI/session/chart tests: **44 passed** (28 mortality, 16 existing fixed), `target/commit-e-survivor-ui.log`.
- Broad Monte Carlo A–D, exact sampler, survivor, longevity, Stage 5 and UI regression selector: **476 tests, zero failures/errors, 3 skipped**, `target/commit-e-survivor-focused.log`.
- Explicit previews: **3 passed**, `target/commit-e-survivor-preview.log`. One fixed-mode run and both requested mortality plan shapes, each with 5,000 actual simulations. Maven exit code 0.
- Final full non-benchmark result: **1,527 tests, zero failures, zero errors, five skipped; BUILD SUCCESS**, 1 minute 26 seconds, **Maven exit code 0** (`target/commit-e-survivor-full.log`). The latest source and rendered-detail freezing assertions are included. No Stage 5 thread-liveness failure occurred; no isolation rerun or fresh-JVM-per-test workaround was needed.

Acceptance coverage: BOTH_SURVIVE/no saved age with entered analysis age executes the real analyzer off FX; unset analysis age
submits no worker; both persisted death directions initialize at 66 and execute with session age 60 without plan mutation;
both-direction direct engine oracles confirm actual survivor cash-flow effects for 60 versus 67; editing completed results
preserves frozen details; cancellation/late publication and JSON non-mutation checks pass. The existing seed-417 fingerprints,
fixed output values, overlay/markers, chart navigation, progress/cancellation and defaults are covered by the unchanged fixed regressions.

Inspected 1900×1040 correction previews:

- [BOTH_SURVIVE, no saved age, user-entered analysis age 67](target/monte-carlo-longevity-both-survive-late-preview.png)
- [PRIMARY_DIES, initialized saved age 66](target/monte-carlo-longevity-persisted-age-late-preview.png)
- [Fixed Lifespan](target/monte-carlo-phase2-preview.png)

Both mortality variants also capture opening-year screenshots with `-opening-preview.png` in place of `-late-preview.png`.
The new field fits in the existing row without additional vertical growth. Geometry assertions confirm the full label is
not truncated, tooltip exists, Run/Cancel and table/details remain in the viewport, details start collapsed, and the selected-year
guide still matches the actual median vertex. Chart and population readouts remain readable. No unrelated layout redesign was needed.

The only compatibility accommodation is keeping generation-only requests age-optional; execution is always request-owned.
There is no separate per-person policy, optimization, automatic default, strategy comparison, persistence, PDF, inflation, or parallelism change.

Final correction audit: `git diff --check` passed (only Git's LF-to-CRLF notices); `git diff --cached --name-only`
was empty. `git status --short` exactly matches the 19-file revised boundary above, and HEAD remains
`5a64a955d1972572048f2618450fa7dc28898abf`. Revised Commit E is independently buildable and ready for review.
No changes were staged or committed.
# Selected-year follow-up — investigation and decision (2026-09-27)

Starting HEAD: `5a64a955d1972572048f2618450fa7dc28898abf`. The existing 19-path
Commit E boundary was verified before work; no unrelated changes were present.
Read-only investigation completed before production/test edits. An ignored
`target/TailAudit.java` diagnostic ran the existing compiled preview fixture;
`target/commit-e-tail-investigation.log` retains exact observations and geometry checks.

Decision: proceed. Cached annual aggregates, chart year mapping, Type-7 percentiles,
and guide/median geometry agree for all five years. No calculation defect found.
The shrinking conditional population, together with its annual market outcomes,
explains the tail. No calculations will change.

Proposed presentation rule, recorded before implementation: warn only for a positive
completed living-year sample of at most 100 AND at most 5% of requested simulations.
At 100 observations, each decile has roughly ten observations; the percentage gate
identifies a substantially reduced original population. This is a transparent UI
heuristic, not a statistical confidence guarantee or a failed-run indication.
Zero samples receive unavailable values rather than a percentile-stability warning.
No existing project sample-size warning convention was found.

## Tail characterization

Existing preview: seed 417, 5,000 simulations, plan expected return, 12% volatility,
standard mortality adjustments, analysis survivor age 67; names Alex and Sam.
Values below are exact cached nominal-dollar aggregates, not rounded chart labels.

| Year | Requested | Living | Completed/funded | Living failures | Both deceased | Both alive | Alex only | Sam only | Living % |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 2067 | 5000 | 63 | 63 | 0 | 4937 | 0 | 1 | 62 | 1.26% |
| 2068 | 5000 | 39 | 39 | 0 | 4961 | 0 | 1 | 38 | 0.78% |
| 2069 | 5000 | 18 | 18 | 0 | 4982 | 0 | 0 | 18 | 0.36% |
| 2070 | 5000 | 9 | 9 | 0 | 4991 | 0 | 0 | 9 | 0.18% |
| 2071 | 5000 | 2 | 2 | 0 | 4998 | 0 | 0 | 2 | 0.04% |

| Year | Min | P10 | P25 | Median/P50 | P75 | P90 | Max |
|---|---:|---:|---:|---:|---:|---:|---:|
| 2067 | 1849713.31 | 5970288.6080 | 10126335.5750 | 16773943.4100 | 31438393.1000 | 49450520.6260 | 84445727.77 |
| 2068 | 1804119.49 | 4831945.6820 | 9532868.3250 | 15461142.8300 | 28856771.9050 | 41434881.9340 | 62774980.68 |
| 2069 | 3836052.87 | 8292147.3560 | 10493902.3450 | 14213504.6650 | 28797230.2675 | 42224399.4930 | 56713903.62 |
| 2070 | 3223814.83 | 5783135.1340 | 9340769.5900 | 10191769.7800 | 32889730.4000 | 46790565.1460 | 49384054.41 |
| 2071 | 6275736.70 | 6479975.9150 | 6786334.7375 | 7296932.7750 | 7807530.8125 | 8113889.6350 | 8318128.85 |

All five years satisfy requested = living + deceased; living = completed + living failures;
living = both alive + primary only + spouse only. The fan model holds the same cached
annual object and percentile optional, not reconstructed data. Its chart series use the
actual year and exact BigDecimal percentile. Bands use the same values for pixel coordinates.
Selection and focus invalidate chart layout; guide X matches median vertices in every tail year.
The final two observations are 6275736.70 and 8318128.85; existing Type-7 interpolation gives
the reported 2071 quantiles exactly. No stale-selection, indexing, geometry or percentile defect found.
This describes conditional cross-sections, not one household's asset path or one fixed cohort.

## Presentation refinement

Calculations changed: **No**. No sampling, annual or terminal percentile, financial,
persistence, execution, cancellation or session-state code changed in this follow-up.
The two-line summary uses existing `UIFormatters.money` (whole-dollar currency, not a new
abbreviated-money convention): year/Median/P10/P90, then living/requested, completed through
year, both alive and actual person-name survivor counts. Missing percentiles say unavailable.
Living failures remain visible through the difference between living and funded counts.
The implemented small-sample rule is exactly the proposed inclusive rule above, with the neutral
notice appended to line two. Analysis Details states the rule and its heuristic limitation.

The shared mortality formatter supplies visible text and the beginning of tooltip/accessibility
text; P25/P75 follow in the latter. No per-year focus targets were added. Left/Right/Home/End,
click/hover selection, focus border and guide layout remain unchanged. Selection consumes
cached results and does not enqueue analysis. Fixed-mode text still uses the original formatter
and newline-to-middle-dot rendering. Details distinguish same-calendar-year living/funded
cross-sections from each successful world's own pre-second-death terminal date and nominal values.

Exactly eight existing Commit E paths changed in this follow-up; no new boundary paths:

```text
MONTE-CARLO-MORTALITY-COMMIT-E.md
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloFanChart.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPresentation.java
src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityPresentation.java
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityUiFixtures.java
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityViewTest.java
src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPreviewTest.java
```

## Follow-up visual verification

All three enabled preview tests pass (`target/commit-e-tail-preview-final.log`).
The first preview attempt caught the extra summary line moving Analysis Details to
Y=1034, beyond the existing Y=1015 bound. Reduced mortality-only result-section gaps
from 5 to 3 pixels; retained chart and terminal-table dimensions and fixed-mode spacing.
The rerun passes the unchanged viewport bound, first/middle/final and all five tail-year
median-guide alignment assertions, exact P10/P90/median series values, and untruncated
selected-year labels. All screenshots are 1900x1040.

Manually inspected the representative 2051 result and 2069/2070 tails: no clipping or
overlap, exactly two summary lines, readable names/counts and subtle warning, unchanged
chart/legend/table, visible Run/Cancel, and collapsed compact Analysis Details.

Preview artifacts (regenerated for this follow-up):

- `target/monte-carlo-longevity-both-survive-opening-preview.png`
- `target/monte-carlo-longevity-both-survive-late-preview.png` (2051)
- `target/monte-carlo-longevity-both-survive-2069-preview.png` (18 living/funded)
- `target/monte-carlo-longevity-both-survive-2070-preview.png` (9 living/funded)
- `target/monte-carlo-longevity-persisted-age-opening-preview.png`
- `target/monte-carlo-longevity-persisted-age-late-preview.png`
- `target/monte-carlo-longevity-persisted-age-2069-preview.png`
- `target/monte-carlo-longevity-persisted-age-2070-preview.png`
- `target/monte-carlo-phase2-preview.png` (Fixed Lifespan)

The original fixed-mode geometry and seed-417 fingerprint tests remain unchanged and pass.
Focused suite: 136 tests, zero failures/errors, two preview opt-in skips
(`target/commit-e-tail-focused.log`); includes 37 mortality UI tests, 8 aggregation tests,
8 mortality-result tests, all existing Monte Carlo execution/world/random/fixed/UI tests.
Pre-edit existing UI/geometry suite: 34 tests, zero failures/errors
(`target/commit-e-tail-before.log`). Preview rerun: 3 tests, zero failures/errors/skips.
The initial preview failure is retained in `target/commit-e-tail-preview.log` for transparency.

## Final follow-up boundary and Git status

Exact final proposed Commit E boundary: **19 paths**, unchanged from the start of
this follow-up. No additional production/test/report paths were created. Diagnostic
sources, logs and screenshots are ignored artifacts under `target/`.

`git status --short` (this is also the complete proposed Commit E path list):

```text
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloAnalyzer.java
 M src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityRequest.java
 M src/main/java/com/daviddunn/retirementplanner/domain/income/SurvivorBenefitClaimingPolicy.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEvaluationContext.java
 M src/main/java/com/daviddunn/retirementplanner/domain/projection/SocialSecurityProjectionIncomeProvider.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloAnalysisView.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloFanChart.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloFanModel.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPresentation.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRun.java
 M src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloRunService.java
 M src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityExecutionTest.java
 M src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloPreviewTest.java
?? MONTE-CARLO-MORTALITY-COMMIT-E.md
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMode.java
?? src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityPresentation.java
?? src/test/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSurvivorAssumptionTest.java
?? src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityUiFixtures.java
?? src/test/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloMortalityViewTest.java
```

Final full non-benchmark suite: **1,536 tests, zero failures, zero errors, five skipped**;
BUILD SUCCESS, native Maven exit 0 (`target/commit-e-tail-full.log`, 2:36).
Command selectors: focused `-Dtest=MonteCarlo*Test,!*BenchmarkTest`; preview
`-Dtest=MonteCarloPreviewTest -Dmontecarlo.preview=true`; full
`-Dtest=*Test,!*BenchmarkTest`. All used IntelliJ bundled Maven and the required
repository-local `.codex-m2/repository`. No benchmarks were enabled. Stage5D passed
without a thread-liveness retry or unrelated change.

`git diff --check`: passed, with only Git LF-to-CRLF conversion notices.
`git diff --cached --name-only`: empty. HEAD remains the starting HEAD.
Fixed-lifespan financial results, seed-417 fingerprints, defaults and UI regressions pass.
The final full suite includes the mortality-only spacing correction.

The focused follow-up is independently buildable and ready for review. No calculation,
sampling, percentile, terminal-distribution, persistence or fixed-mode behavior changes.
Nothing was staged or committed. Work stops here for review.
