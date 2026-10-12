# Phase 6D.1A — Production state-tax performance audit

## Outcome and behavior freeze

The original Stage 1 observation of 4.5–10.8% slower Monte Carlo execution is **not
reproduced as a sustained multistate penalty** by this controlled comparison. The
final Michigan fixed/longevity medians are -0.03%/+0.70% relative to the committed
Phase 6C.3 implementation. The 30-year fixed/conversion and integrated-grid batches
are within roughly 3–4.3% of that baseline. Short batches remain more sensitive to
JIT, GC and scheduling variation; their changes are not attributed to an optimization.

**No production optimization was retained.** An isolated prepared-context candidate
saved a little allocation, but did not establish a useful, repeatable timing gain.
It was rejected under the phase's evidence/maintainability requirements. This is a
completed profiling and regression delivery, not a claim that unchanged code became
faster. No new state, tax rule, funding tolerance, financial expectation, UI,
persistence behavior or public production API changed.

**No financial values changed.** Michigan remains at Phase 6C.3/6D1 values; Florida
remains calculated zero. The production boundary and conservative eligibility policy
remain intact. The independent eight-state prototype is untouched.

## Prior work and inspected path

Read the complete 6D1, 6C3, 6C2, 6C and 6B reports and all 733 financial-impact ledger
rows before preparing any candidate. The workspace was clean at the start, at
`325945b` (Stage 1); the production pre-integration reference is `3702097` (Phase 6C.3).

Inspected production routing, both adapters, immutable state assumptions, attributed
income, the annual funding loop, Roth bracket fill, projection construction, integrated
and weighted analysis, Monte Carlo, result construction, and resource loading. All
production paths below are relative to
`src/main/java/com/daviddunn/retirementplanner/`.

| Path / method | Runtime responsibility and finding |
| --- | --- |
| `domain/tax/state/ProductionStateTaxService.calculate` | Immutable MI/FL map lookup and method check; cached calculators, no I/O |
| `domain/tax/state/MichiganProductionTaxAdapter.calculate` | Translates household facts into `MichiganTaxContext`, invokes the existing calculator |
| `domain/tax/state/FloridaProductionTaxCalculator.calculate` | Immutable structured calculated-zero result with jurisdiction, method and limitation |
| `domain/tax/state/StateTaxCalculationRequest` constructor | Non-null checks and year/date consistency; no classification or parsing |
| `domain/tax/state/TaxHouseholdContext.capture` | Annual immutable household facts, owner/death/pension context; not per-income tax-rule parsing |
| `domain/tax/TaxFundingCalculator.calculate` | Trial/final attributed income and federal/state recalculation at unchanged convergence settings |
| `domain/roth/RothConversionBracketFillCalculator.calculateConversion` | Existing conversion preview and funding trials; no changed search, targets or ordering |
| `domain/projection/ProjectionEngine` constructor / `calculateProjectionYear` | Loads government rules per engine; projects parameters per year and passes state configuration |
| `app/socialsecurity/IntegratedRetirementClaimingGridCalculator.calculate` | Ordered 81 strategies plus current baseline through the ordinary engine |
| `app/socialsecurity/LongevityWeightedIntegratedStrategyEvaluator.evaluate` | Isolated scenario projections; same tax service |
| `app/montecarlo/MonteCarloAnalyzer` | One engine and isolated plan per analysis; state routing stays inside normal projections |
| `persistence/GovernmentRulesRepository.load` | Jackson resource loading at engine construction, not at annual tax calculation |
| `persistence/StateTaxDatasetRepository.load` | Standalone research catalog initialization cache; not used by MI/FL production routing |

## Repeatable baseline methodology

- Java **25.0.4.1+1-LTS**, OpenJDK 64-Bit Server VM / HotSpot; Windows NT
  10.0.26200; JVM reports eight available processors.
- Timing JVMs: `-Xms512m -Xmx1536m`; maximum heap 1,610,612,736 bytes. Default
  collector/JIT and no profiler during timing comparisons. No new dependencies.
- Sequential processes, with no concurrent benchmark/test workload. Same machine,
  JVM, scenario construction, seed, workload order and batch size for the comparisons.
- Three warm-up batches per scenario, followed by five measured batches. Three
  fresh current-baseline JVMs and three fresh pre-6D JVMs: 15 samples per scenario.
  Two final unchanged-production JVMs: 10 samples. Candidate: two JVMs, 10 samples.
- The primary table uses the conventional pooled median (mean of the central two
  observations for an even count). Means, minima/maxima and individual rows remain
  in `target/phase6d1a-summary.csv` and the per-run CSVs. No scaled 5000-trial estimate
  is substituted for measured 500-trial time.
- Plan/engine construction is outside direct projection timing. Construction that
  is inherently inside analyzer evaluation remains inside that workload. Financial
  hashing, CSV writes, reflection used to access existing test factories and console
  output occur outside the measured interval. Reflection is not a production tax path.
- Warm-up matters: the first Michigan Single batch in the first current JVM took
  369.74ms; its warmed median was 55.70ms. Short workloads retain visible cross-JVM
  JIT/GC variability and are not evidence for speculative micro-optimizations.
- These are local sequential measurements, not a universal hardware SLA or a
  statistical proof of an exact fractional-percent regression.

### Actual workload batches

| Scenario, each run for MI and FL | Batch and financial workload |
| --- | --- |
| Standard Single / couple | 150 four-year projections using the existing `FloridaProductionProjectionTest.plan` fixture; pension, SS, opening/annual RMD and fixed conversions |
| Fixed conversion | 50 30-year projections using the existing Monte Carlo mortality execution plan; annual 10,000 conversion, pension/SS/expenses/accounts |
| Bracket fill | 10 30-year projections, same plan with annual 22% federal bracket filling and first-household-RMD stop |
| Deterministic integrated grid | One full ordered 81-cell grid plus its current-plan baseline, five-year Stage 4 plan |
| Longevity weighted | Two evaluations of one fixed strategy over nine positive paired death scenarios; existing exact-ending and PV semantics |
| Fixed Monte Carlo | 500 trials, seed 417, return volatility .12, same representative couple and 30-year plan as the Stage 1 benchmark; includes normal current reference |
| Longevity Monte Carlo | Same 500 indexed seed-417 worlds, existing mortality request, exact second-death projection endings |

The eight workloads per state provide 16 separate frozen output fingerprints.
Projection fingerprints include every existing no-argument BigDecimal year getter
in deterministic year/name order, preserving precision/scale and expected unavailable
spouse diagnostics. Grid fingerprints include all ordered cell financial metrics.
Weighted fingerprints include expected nominal/PV estate and evaluated probability.
Monte Carlo fingerprints include the complete ordered outcome records. Existing
goldens additionally protect ownership, account balances, rankings, annual flows,
attribution, exports and persistence.

### Practical pre-6D comparison

Read five original financial classes from `3702097` using `git show` into ignored
`target/phase6d1a-old-src`: `TaxFundingCalculator`, `TaxFundingResult`,
`RothConversionBracketFillCalculator`, `ProjectionEngine`, `ProjectionYear`.
Compiled into an isolated leading classpath, using the same unchanged Michigan rules
resource and same fixtures/JVM. No checkout, branch, reset, history rewrite or source
replacement occurred. Only Michigan is compared historically: Florida did not exist
as a production calculation in that revision. Current model/copy machinery accepts
the additional MI metadata, while the original financial classes use their original
Michigan path. Complete seeded outcome fingerprints agree across those versions.

This controlled reference supersedes attributing causality to the three coarse Stage 1
timing observations (17.115s before, 17.891–17.937s after fixed; 13.624s before,
15.056–15.102s after longevity). Those samples did not isolate warm-up/JIT effects or
establish an exclusive routing cost. The original difference cannot honestly be
assigned a percentage breakdown from those samples.

## Profile and exact call counts

Used JDK Flight Recorder `profile` settings, 2ms execution samples and allocation
sampling in separate profiling JVMs. Timed-batch events exclude hashing/output from
the focused Monte Carlo CPU summary. Summary deduplicates repeated frames within
each sample. Inclusive stacks are **not additive** and include existing Michigan
calculation work; they are not an estimate of incremental dispatch cost.

Temporary instrumented class copies under `target` count calls in a separate untimed
process. They do not modify production source, measured timing classes or the shipped
calculator. Financial fingerprints remain identical. Exact completed-year counts
include analyzer reference projections and Medicare-related re-evaluations normally
performed by the engine.

| Batch | Engine runs / completed years | Funding calls / loop iterations | Federal and state calls each | Source records created |
| --- | ---: | ---: | ---: | ---: |
| MI fixed MC500 | 501 / 15,030 | 29,559 / 39,579 | 69,138 | 694,386 |
| MI longevity MC500 | 500 / 13,183 | 25,834 / 37,713 | 63,547 | 679,398 |
| FL fixed MC500 | 501 / 15,030 | 29,559 / 33,567 | 63,126 | 633,264 |
| FL longevity MC500 | 500 / 13,183 | 25,834 / 32,165 | 57,999 | 612,137 |
| MI bracket batch (10 projections) | 10 / 300 | 1,220 / 2,050 | 3,270 | 24,520 |
| FL bracket batch | 10 / 300 | 1,220 / 1,850 | 3,070 | 23,020 |
| MI or FL integrated grid | 82 / 410 | 820 / 830 | 1,650 | 5,814 |
| MI or FL weighted batch | 18 / 240 | 480 / 480 | 960 | 6,978 |

Every state call constructs one request and result; every MI call translates one
household context. Trial/final attribution attachment count equals the tax-call
count. MI fixed/longevity MC average 4.60/4.82 tax calls per completed year; FL
averages 4.20/4.40. These counts are not unique per-year withdrawals: trials are
discarded and only the converged final ledger is retained. Florida's fewer iterations
are the established state-tax funding effect, not a new optimization or weaker criterion.

### Measured contributors

| Contributor | Evidence | Decision |
| --- | --- | --- |
| Generic dispatch/registry | MI fixed: service and adapter each present in 47 of 1,587 CPU samples, with existing MI calculator in 40; longevity 54 of 1,324, MI calculator 50. FL service only 3 of 1,492 fixed and 1 of 1,220 longevity samples | No evidence of a costly registry; preserve immutable map and common boundary |
| MI context translation/validation | Constructor present in four fixed and four longevity samples; 69,138/63,547 actual translations | Test prepared-context candidate, then reject negligible end-to-end benefit |
| Request/result allocation | One each per state call; constructor/service validation barely sampled; production assumptions already immutable/validated | Preserve narrow value contract and dynamic year/date validation |
| Attribution scanning/list/stream work | Existing attachment in funding stacks; 694,386 source constructors in MI fixed batch versus 69,138 tax calls. Same attribution machinery predates 6D1 | Do not remove facts or cache changing trial income; no new duplicate classification in generic boundary |
| BigDecimal, growth, annual rule projection, SS | Prominent existing CPU/allocation stacks; required financial calculations, not introduced by state dispatch | Preserve arithmetic, scaling, rounding, iteration order and benefit calculations |
| Funding/bracket trials | Counts above; existing trial and final recalculation and conversion preview amplify per-call work | No tolerance, search, allocation or convergence changes |
| Reflection/JSON inside state calculation | No state-rule loading or reflection call in annual tax/funding dispatch | No parsing/reflection optimization warranted |

No exclusive percentages are invented for these inclusive samples. JFR weighted
allocation samples showed large carry-over/outlier weights around recording boundaries;
they were used to identify allocation sites, **not** as exact total allocation or
retained-heap measurements. `ThreadMXBean` allocated bytes from unprofiled batches
are the quantitative allocation source. Profiling runs are excluded from timing tables.

## Rule/resource loading findings

Production MI/FL routing does not read the eight-state JSON at all. The standalone
catalog uses its existing immutable initialization cache and stays isolated. Registry
and adapter instances are created once per funding service, not per tax trial; static
plan assumptions are validated at construction/load.

The configuration counter recorded **one** `StateTaxAssumptions` construction and
**zero** registry constructions during each 500-world MC analysis: configuration is
copied once with the isolated run plan; the two engine services were created in
setup. A grid records 82 configuration copies and 164 registries (two per engine),
not 1,650 setups. The weighted batch records 20 configuration copies and four
registries, reflecting its established scenario isolation and two engines. None is
constructed per annual tax request. Assumption access was not a distinct hot CPU
stack; repeated service checks are simple validated-enum comparisons.

Government rules are loaded **once per ProjectionEngine construction**, not globally
cached. The measured MC batches have **zero resource loads inside the measured run**:
their engine was created during setup. The 81-cell integrated grid constructs 82
engines and therefore loads government JSON 82 times; the weighted batch constructs
two engines and loads twice. Annual rule *projection* remains a separate, expected
dynamic indexing calculation.

The integrated-grid construction/loading behavior is present in Phase 6C.3 and is
not a new multistate defect or the cause of the historical incremental MC claim.
It is a possible separate engine-lifecycle performance task. No repository/global
rule cache, engine reuse redesign or analyzer refactor was attempted in this phase.

## Candidate and rejected optimizations

Prepared-context experiment, confined to ignored overlay classes:

- Bind immutable household facts and validated jurisdiction once per funding call.
- Michigan adapter constructs its immutable legacy context once for that binding.
- Keep dynamic income, federal result, year checks and the same registry boundary.
- Guard bound household/configuration identity; no state branches in projection,
  mutable result cache, changed financial calculations or convergence changes.

| Candidate | Workload | Before median ms | Candidate median ms | Difference | Financial fingerprints |
| --- | --- | ---: | ---: | ---: | --- |
| Prepared MI context/registry binding | Fixed MC500 | 1,674.53 | 1,664.88 | -0.58% | Identical |
| Same | Longevity MC500 | 1,396.74 | 1,395.05 | -0.12% | Identical |

Mean allocation fell about 0.22% fixed / 0.24% longevity, with roughly 13.38/11.65 MiB
less allocation per 500-trial batch. The gain is within measured JVM/run variation and does
not justify adding a binding API, guards and closures. **Rejected; no production
files modified.** Those hypothetical classes never replace `target/classes` or
route the normal tests; they are used only on the isolated candidate classpath.

Other rejected ideas:

- Florida cached zero result: metadata allows an immutable constant, but the adapter
  is scarcely sampled; saving one tiny result per call has no demonstrated material
  benefit. No zero-result bypass or unknown-state fallback.
- Replace map dispatch with scattered jurisdiction branches: no measured lookup
  bottleneck; damages extension boundary.
- Memoize state tax across funding/conversion trials: taxable amounts and attribution
  change; a stale-key error would alter money. No result caching introduced.
- Collapse owner/account source records or prequalify them in withdrawal logic:
  violates the eligibility architecture and financial safeguards.
- Remove BigDecimal, diagnostics, defensive collection boundaries or validation:
  precision and immutability remain more important than an unproven micro-gain.
- Globally cache government resources or reuse analyzer engines: pre-existing
  lifecycle work, outside this narrow incremental-state-overhead task.

## Final performance

The following final runs use **unchanged production code**. Negative deltas are
observed timing differences, not achieved optimizations. Short grid/weighted/Single
batches especially show why one timing sample cannot establish a small regression.

| Scenario / batch | Pre-audit median ms | Final median ms | Final vs pre-audit | Pre-6D median ms | Final vs pre-6D |
| --- | ---: | ---: | ---: | ---: | ---: |
| Michigan-projection-single | 59.41 | 51.89 | -12.66% | 54.80 | -5.31% |
| Michigan-projection-couple | 64.26 | 58.59 | -8.82% | 58.87 | -0.49% |
| Michigan-fixed-30years | 190.24 | 189.15 | -0.58% | 183.77 | 2.92% |
| Michigan-bracket-30years | 41.95 | 42.38 | 1.02% | 40.90 | 3.61% |
| Michigan-grid81 | 174.81 | 180.63 | 3.33% | 173.24 | 4.27% |
| Michigan-weighted9 | 31.74 | 29.85 | -5.97% | 34.02 | -12.27% |
| Michigan-MC-fixed500 | 1674.53 | 1659.37 | -0.91% | 1659.93 | -0.03% |
| Michigan-MC-longevity500 | 1396.74 | 1406.08 | 0.67% | 1396.34 | 0.70% |
| Florida-projection-single | 24.13 | 24.04 | -0.41% | Not applicable | Not applicable |
| Florida-projection-couple | 36.16 | 37.85 | 4.69% | Not applicable | Not applicable |
| Florida-fixed-30years | 150.85 | 151.86 | 0.67% | Not applicable | Not applicable |
| Florida-bracket-30years | 36.34 | 36.40 | 0.16% | Not applicable | Not applicable |
| Florida-grid81 | 88.67 | 96.13 | 8.42% | Not applicable | Not applicable |
| Florida-weighted9 | 19.27 | 18.21 | -5.54% | Not applicable | Not applicable |
| Florida-MC-fixed500 | 1572.76 | 1574.42 | 0.11% | Not applicable | Not applicable |
| Florida-MC-longevity500 | 1269.42 | 1300.39 | 2.44% | Not applicable | Not applicable |


The representative heavy MI workloads meet the approximate <=5% investigation
target relative to the controlled pre-6D implementation. This is a bounded local
finding, not an assurance that every future state's calculator will have the same cost.

## Memory and financial regression

Measured allocated bytes per 500-trial batch (mean of unprofiled observations):

| Michigan workload | Pre-6D mean allocated bytes | Pre-audit mean | Final mean | Final vs pre-6D |
| --- | ---: | ---: | ---: | ---: |
| Fixed MC500 | 6,343,041,686 | 6,367,714,703 | 6,361,037,610 | +0.28% |
| Longevity MC500 | 4,991,388,865 | 5,015,104,822 | 5,010,681,866 | +0.39% |

Allocation is cumulative churn, not peak/retained heap. New boundary records add a
small measured amount alongside the pre-existing financial/income work. The original
5.42/15.13 MiB fixed/longevity retained-heap measurements were unchanged by Stage 1.
Final full-suite retained-heap observations are recorded in verification below.

The new 16-fingerprint fixture is a **characterization freeze**, not an independent
tax-law worksheet. It was captured before the candidate and checked across fresh JVMs.
No existing fixture or expectation was edited. Existing independent tests preserve:

| Michigan independent input | Unchanged tax |
| --- | ---: |
| Single interest 10,000 | 174.25 |
| MFJ interest 20,000 | 348.50 |
| Single qualifying IRA 100,000 | 1,125.83 |
| MFJ qualifying IRA 200,000 | 2,251.65 |

All 20 Florida category fixtures retain zero. The Stage 1 A/B worksheets are unchanged:
couple MI/FL state tax 4,690.47/0; ending assets 1,558,976.66/1,563,667.13; bracket
conversion 203,563.35/207,668.00; funding worksheet 12,265.52/11,243.59; subsequent
RMD 39,639.06/39,683.69. Federal/state/total tax, withdrawals, conversions, RMDs,
assets/net worth/estate, analyzer outputs, seeded worlds, export values, persistence
and legacy Michigan defaulting have zero changed expectations.

## Reproduction and useful artifacts

Use IntelliJ bundled Maven with the mandatory project-local repository, as in
AGENTS.md. Opt-in reusable benchmark (normal suites run the financial freeze and
skip the timing test):

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' `
  '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' `
  '-Dtest=StateTaxPerformanceAuditTest#warmedRealApplicationWorkloads' `
  '-Dstate.tax.performance=true' '-DargLine=-Xms512m -Xmx1536m' `
  '-Dperf.warm=3' '-Dperf.samples=5' '-Dperf.output=target/phase6d1a-benchmark' test
```

Optional `-Dperf.profile=true` records JFRs; `-Dperf.filter=MC-` limits workloads.
Do not combine profiled timing with unprofiled samples. Every workload records units,
individual milliseconds, current-thread allocated bytes and its financial hash.

Ignored `target` artifacts include primary before/final/old/candidate measurement
CSVs/logs, `phase6d1a-summary.csv`, initial/all-workload and batch-filtered JFRs and
text summaries, exact call-count overlays, read-only original class copies, and
candidate sources/classes. Giant recordings are not shipped. The evidence above is
usable without profiler software.

## Exact files and verification

Created:

- `PHASE-6D1A-MULTISTATE-PERFORMANCE-AUDIT.md`
- `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/StateTaxPerformanceAuditTest.java`
- `src/test/resources/tax/state/phase6d1a-financial-fingerprints.txt`

Modified existing files: **none**. In particular, no production, tax rules, UI,
persistence, build configuration or existing test expectations changed.

All commands used IntelliJ bundled Maven with the project-local `.codex-m2/repository`.

| Verification | Run | Passed | Failed | Errors | Skipped | Log |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| All Phase 6D1 focused tests + new freeze, screenshots enabled | 54 | 53 | 0 | 0 | 1 | `target/phase6d1a-focused.log` |
| Tax/attribution/funding/RMD/Roth/Single/couple/analyzer/MC/persistence/wizard/report selection | 1,065 | 1,056 | 0 | 0 | 9 | `target/phase6d1a-affected.log` |
| Complete non-benchmark | 2,150 | 2,137 | 0 | 0 | 13 | `target/phase6d1a-nonbenchmark.log` |
| Complete benchmark-inclusive | 2,171 | 2,154 | 0 | 0 | 17 | `target/phase6d1a-full.log` |

The only new ordinary-suite skip is the deliberate opt-in timing test; the new
financial fingerprint test runs in both suites. The focused run enabled existing
visual previews; no UI optimization or screen change was performed. All 204
standalone eight-state prototype/dataset tests passed unchanged, along with the
Phase 6C2 attribution and Phase 6C3 independent Michigan fixtures.

The full suite's original 5000-trial retained-heap benchmark observed **5.40 MiB fixed /
15.12 MiB longevity**, versus Stage 1's 5.42/15.13 MiB; small GC/measurement variation,
with no evidence of increased retained memory. Its 16.374s/13.862s wall times are
uncontrolled full-suite observations and are not mixed into the comparison table.

The reusable opt-in Maven benchmark also passed: **1 run / 1 passed / 0 failed / 0 errors / 0 skipped** (`target/phase6d1a-reusable-benchmark.log`). It executed all 16 workloads with three warm-ups and five measurements at the documented heap settings. All 80 measured financial hashes matched the pre-audit freeze; its smoke-verification timings are separate from the controlled table.

`git diff --check` passed. All three new files also passed new-file whitespace
checks. The tracked diff and staged diff are empty. No production or existing
financial test/fixture file changed; zero expected financial values were updated.

## Readiness and next phase

The common production boundary is sufficiently inexpensive for a bounded Illinois
integration when separately approved. Performance evidence does not warrant replacing
the registry, dropping attribution, weakening funding convergence or preemptively
redesigning taxation. Require the same independent rules/eligibility checks, MI/FL
freeze, funding/bracket-fill tests and representative measurements when Illinois is
actually added. Profile that state on its real production workload before generalizing
these MI/FL findings. Consider engine-construction/resource reuse only as a separately
scoped follow-up if integrated-grid latency becomes a product concern.

Only Michigan and Florida remain production-enabled. No additional state or Stage 2
work was implemented. Nothing was staged, committed or pushed. Await approval before
enabling Illinois or any additional state.
