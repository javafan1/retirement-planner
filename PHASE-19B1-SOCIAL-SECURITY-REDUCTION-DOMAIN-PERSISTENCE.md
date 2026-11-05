# Phase 19B1 — Social Security Benefit Reduction Domain and Persistence

## Summary

Added the Social Security benefit reduction assumption to immutable `EconomicAssumptions`, retained the existing constructor signatures, and carried the values through plan editing, persistence, snapshots, plan swaps, and analyzer input identity. The default remains 0% beginning in 2032. No payment calculation, projection, tax, analyzer, Monte Carlo, report, or UI control was changed.

## Domain model and compatibility

`EconomicAssumptions` now exposes:

- `BigDecimal socialSecurityBenefitReductionPercent`, default `0`.
- `int socialSecurityBenefitReductionEffectiveYear`, default `2032`.

Percentages accept decimal precision and are validated inclusively from 0 through 100. The effective year is validated inclusively from 1000 through 9999, independently of the plan horizon. Invalid non-null values throw `IllegalArgumentException`; missing and explicit-null JSON values use the compatibility defaults.

The Jackson creator accepts the new nullable properties. The prior five-argument JSON/value constructor and existing four- and two-argument convenience constructors remain available and use the defaults. A full constructor is available for the new values. Equality and hash code include both new properties; the percentage compares using `BigDecimal.equals`, preserving decimal scale as part of exact configured value identity.

The new properties are omitted from JSON when they have exactly the compatibility defaults. This keeps old default-valued plan serialization and established exact fingerprints stable. Any nonzero percentage or nondefault year is serialized. In particular, a zero percentage with an edited effective year is serialized and changes configured-input identity, even though its financial calculations remain equivalent at 0%.

## Persistence, snapshots, and identity

Save/Load, baseline snapshots, complete projection snapshots, and plan Save/Load/Swap preserve nondefault values. Loading a legacy plan does not rewrite it. No outer plan schema migration or historical snapshot rewrite was introduced.

`AnalysisFingerprintService` includes a nonzero reduction percentage and includes the effective year whenever the percentage is nonzero or the year differs from 2032. Thus the Social Security analyzer fingerprint reflects effective configured changes while default plans retain historical digests. Complete projection snapshot and general plan input digests receive the nondefault properties through existing plan serialization.

All production construction paths were searched. The two paths that rebuild existing economics during user edits—`AssumptionsView` and `ResultsSummaryView`—now pass through the current reduction fields. `PlanningAssumptions` legacy/default constructors still intentionally produce the compatibility defaults.

## Files changed

Production:

- `src/main/java/com/daviddunn/retirementplanner/domain/model/EconomicAssumptions.java`
- `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisFingerprintService.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java`
- `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java`

Tests:

- `src/test/java/com/daviddunn/retirementplanner/domain/model/EconomicAssumptionsTest.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/SocialSecurityReductionPersistenceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSwapTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalysisFingerprintTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsViewTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/views/ResultsSummarySocialSecurityPreviewTest.java`

The pre-existing untracked `PHASE-19A-SOCIAL-SECURITY-BENEFIT-REDUCTION-AUDIT.md` was preserved and not modified.

## Verification

Focused final regression command covered `ExpenseEscalationCompatibilityTest`, `MonteCarloRestoredFingerprintInvestigationTest`, `AnalysisFingerprintTest`, `EconomicAssumptionsTest`, `SocialSecurityReductionPersistenceTest`, and `ProjectionEngineTest`: **117 tests, 0 failures, 0 errors, 0 skipped**.

Final full Maven test run, using IntelliJ's bundled Maven and the repository-local `.codex-m2` cache: **4,001 tests, 0 failures, 0 errors, 46 skipped; BUILD SUCCESS**. The skipped tests are existing opt-in/preview/benchmark cases. Existing projection tests and the full financial golden suite passed; golden values were not changed.

`git diff --check` passed. Git emitted only its normal LF-to-CRLF working-copy notices for changed source files.

## Deferred scope and limitations

- Phase 19B2: apply the reduction once to projected benefit payments, including survivor benefits and annual timing.
- Phase 19B3: propagate the assumption through Social Security analyzers and Monte Carlo paths that require separate integration.
- Phase 19B4: add the Assumptions controls and disclose nonzero settings in relevant UI and reports.

This phase stores, validates, and fingerprints the configuration only. Until the later calculation phases are approved and implemented, the new assumption does not alter payable Social Security benefits.

## Working-tree status

Branch: `main` (tracking `origin/main`). No files were staged, committed, or pushed. Existing work was preserved.
