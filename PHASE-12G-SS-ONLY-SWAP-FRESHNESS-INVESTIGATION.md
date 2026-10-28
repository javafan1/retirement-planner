# Phase 12G — SS Only freshness after restoring 70/62 by swap

## Executive finding

**Audit and test-only investigation. No production correction implemented.**

The reported UI state is reproducible without a fingerprint bug or a failed swap. The ordinary SS Only dialog displays the **last-success analysis for the later 70/65 plan**, while Current has correctly returned to the original **70/62 accepted projection**. Its original 70/62 analysis remains available through the separate immutable association. The enabled **View Associated Result** button refers to that Current projection's associated result, not necessarily to the completed result displayed in the ordinary dialog.

The displayed 70/65 result is Outdated because its **effective SS input fingerprint differs** from the fingerprint evaluated against the restored 70/62 plan. This is a settled assessment. A projection-association difference alone does not cause this Outdated branch. The SS Only fingerprint explicitly includes persisted selected claiming ages **and** claiming dates, in addition to the mortality/valuation inputs.

The warning is correct under the existing fingerprint and last-success policies, but its generic wording fails to identify which completed analysis is on screen. The smallest policy-consistent correction is a presentation clarification identifying the displayed last-success result, its recorded projection/source elections, and the separate associated result available for Current. Do not remove fingerprint fields, mark B Current for A, or automatically replace the ordinary session with A.

## Evidence boundary: native instance versus reproducible fixture

The user's native reproduction is the motivating observation. **The native plan, manifest, last-success file and analyzer components were not supplied or inspected.** Consequently this report does not claim the fixture UUIDs below are the native UUIDs, or that the native instance has no additional mismatched settings. A screenshot showing saved parameters, a completed result, an enabled associated-result button and Outdated does not reveal the displayed run UUID or prove that the displayed run itself has a recorded association.

Current source and the deterministic reproducer establish the exact normal workflow that produces those observations. To confirm the same diagnosis for a particular native file, compare its displayed last-success run/fingerprints with its role manifest and the associated run for Current, using the checklist below. No personal financial files were searched for or opened.

## Reproduction and scope

Added `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecuritySwapFreshnessInvestigationTest.java` with two tests. This is a regression characterization of current behavior, not a failing expectation demanding a production change.

The main test:

1. Creates a couple plan with primary/spouse elections **70/62**, accepts its ordinary projection A and saves the plan.
2. Prepares an SS request through the production request factory, independently calculates a real result using the production SS optimizer, accepts/persists its last-success slot, exact parameters and explicit A association.
3. Captures A as baseline without recalculation and saves.
4. Changes the spouse election to **65**, accepts ordinary projection B and saves.
5. Independently prepares/calculates/accepts/persists SS result B with the same temporary mortality/valuation parameters and a recorded B association.
6. Installs a throwing projection engine; prepares/commits the real swap, saves, reopens the plan and waits for original projection restoration.
7. Opens the actual ordinary SS dialog with a throwing SS calculation factory. Checks the displayed run, actual control-derived inputs, settled assessment, enabled associated action, exact Current UUID/input digest and both immutable associations.
8. Retrieves A's associated artifact. Verifies that A assesses Current for the restored inputs, while the ordinary dialog still displays B. No automatic promotion occurs.

For bounded audit execution the SS calculations use three tested retirement ages per owner (62, 65, 70), four joint mortality scenarios from two valid death ages per owner, and the existing optimizer's complete survivor expansion. Each independently calculated run contains **9 Stage 1 cells and 320 Stage 2 complete strategies**. No outcome is duplicated or reused to manufacture the second run. This is a lifecycle/fingerprint reproducer, not a full-native-universe capacity claim. The main test hands successful prepared results to the existing accepted-configuration/store/association APIs; it does not claim to retest the real job's entire admission/cancellation implementation.

The second test independently changes only the stored claiming-age field, then only the stored claim-date field, and proves each changes the SS Only fingerprint. These isolated probes deliberately avoid running any financial calculation with an inconsistent age/date pair. Restoring both original fields restores the original hash exactly.

## Recorded identities and fingerprints

**These are fixture-only identifiers from the final passing run, not identifiers from the user's plan.** Projection/run UUIDs are legitimately generated by existing controller admission; assertions use their captured identities rather than relying on random UUID values. Financial inputs, request settings and expected relationships are deterministic.

Owner UUID: `7ee59d2f-f444-4dc7-a9da-4d8e46077aef`.

| Identity | Original A, 70/62 | Later B, 70/65 |
|---|---|---|
| Projection UUID | `e5aeb590-f613-46b1-b58c-3a9dda474e35` | `ab72c2aa-4b4c-4dc5-a3aa-171120e56f54` |
| SS run UUID | `7848f2b8-cd16-4958-80ce-bad869b14d27` | `39d286e6-f18d-4cc4-8800-a1b186a8e7cf` |
| Role after swap/reload | Current | Baseline |
| Ordinary SS dialog displays this run | No | **Yes** |
| Recorded association | A UUID | B UUID |
| Applicable to restored Current under existing SS fingerprint | **Current** | **Outdated** |
| Spouse claim date in original context | `2027-02-28` | `2030-02-28` |

Exact complete-projection configured-input document digests:

```text
A/current: ed8e4913ad2be9e5a9b04eb262cf97eb3a6d408e4c8fddcef04fe1546d6a8844
B/baseline: 39365718acbc5e011c1cf26c2037a39576e610efdfcf22062e4d13208cf8bd76
```

Exact SS effective-input fingerprints:

```text
A / restored Current effective inputs:
d384c60385d59276c3fe91a073f80a2e7e0d97c00ee4384a3a54e14d7050ba6a

B / displayed last-success snapshot:
bf46b78d98a07538b600ac473ff7a58dc8f0b42e6e68a044f2860b0716619262
```

The SS context fingerprint is identical for both:

```text
44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a
```

Couple SS Only's separate effective-context object is empty under the current contract; selected plan elections participate in the **input** fingerprint, not this context hash. Original snapshot payload/context digests also remain independently different:

```text
A: f2cd5d60670e1d6370a6ff826baaa7e169deac7700d58a795d16d59315c38ec0
B: 800c2111f02abff93d9d104b231f24923ee8a3ea5ca066f57881912d81a52569
```

The fixture diagnostic is `target/phase12g-ss-swap/evidence.json` (ignored generated output). It contains only generated fixture identities/hashes, claiming context, counts and result status; no native plan or account balances.

## Exact Outdated predicate and responsible paths

`AnalysisFingerprintService.fingerprint` always adds S using `socialSecurity(plan)`, including for `COUPLE_SOCIAL_SECURITY`. `socialSecurity` serializes participant records with:

```java
r.put("claimingAge", ss.getClaimingAge());
r.set("claimDate", tree(ss.getStartDate()));
```

In this reproduction the changed fields are:

```text
S.people[1].records[0].claimingAge: displayed B 65; Current A 62
S.people[1].records[0].claimDate:   displayed B 2030-02-28; Current A 2027-02-28
```

Primary election stays 70. Benefits/valuation years, COLA, births, mortality categories/factors, conditioning/valuation dates, discount rate and version/reference contracts are unchanged. The actual dialog's private `socialSecurityInputs()` result is captured in the test and equals the original effective temporary inputs exactly, including BigDecimal scales. Saved parameter restoration therefore succeeds; it does not make the complete SS request identity equal.

`WeightedResultPersistence.reassess()` obtains those temporary inputs, evaluates them against `source.getCurrentPlan()`, and calls `AnalysisCompatibilityPolicy.assess`. The policy returns OUTDATED if any of algorithms, references, input fingerprint or context fingerprint differ. Here its decisive condition is:

```text
!saved.inputFingerprint().equals(inputs.fingerprint())
                 B hash             A hash
```

No source revision or projection UUID is tested by that particular predicate. `SocialSecurityStrategyAnalyzerDialog.refreshSocialSecurityResultStatus()` publishes `socialSecurityPersistence.warning()`. `AnalysisResultFreshness.status()` formats OUTDATED as `Historical Analysis — Outdated: ...`; the persistence notice also retains the `Previous Results — Outdated` heading.

## Distinguishing the four mechanisms

| Mechanism | Verified behavior in reproducer | What it does not mean |
|---|---|---|
| Last-success restoration | `WeightedResultPersistence.load` binds `(plan UUID, COUPLE_SOCIAL_SECURITY, file)` and `AnalysisResultStore.load` gives the newest accepted in-memory completion precedence, otherwise reads the durable kind slot. Both durable slot and displayed metadata identify B. | It is not a lookup by newly Current projection UUID. |
| Association mismatch | B's verified recorded projection is B, now Baseline; `analysisAssociation(displayed)` returns HISTORICAL_PROJECTION. A's separate association is verified for Current A. | UUID difference alone is not what makes the policy return OUTDATED. Compatible inputs plus a different association would have a different, historical-compatible presentation. |
| Effective-input mismatch | B's S fingerprint contains spouse age/date 65/2030; restored plan A contains 62/2027. Valid actual restored controls yield A's hash. | Saved Mortality settings do not include or override the plan's financial SS elections. |
| Pending assessment | False: metadata is present, actual inputs are valid, policy state is OUTDATED, and `freshness().pending()` is false. | This is not the transient Assessing state corrected by Phase 12G. |

Swapping intentionally leaves last-success slots and accepted analyzer settings separate from immutable projection associations. `AssociatedAnalyzerAction.available()` looks for a compatible recorded reference for accepted **Current**. Its enabled state says A has a recorded artifact available for read-only verification/viewing; it does not certify the ordinary completed result B as A's result. Opening History does not overwrite B's slot, controls or current session.

If a native B run occurred before a fresh accepted B projection existed, it may be **unassociated** instead of historically associated with B. It can still be the newest last-success slot and still fail the same S hash against A. The enabled button for A alone cannot distinguish those two cases; only the native manifest/run IDs can. The test covers the normal eligible-projection sequence with both A and B explicitly associated.

## Why financial candidate values can match despite different fingerprints

The production request factory captures the original selected claim dates in the base strategy and in `SocialSecurityStrategyAnalysisContext`. `SocialSecurityClaimingGridCalculator.deriveStrategy` replaces both retirement election dates for each tested age pair. Stage 2 expands the retained pairs into complete survivor strategies. In the bounded independent runs, **every Stage 1 cell and every Stage 2 strategy/value record is exactly equal**, including BigDecimal representations, while original request/context fields differ.

`CoupleSocialSecuritySnapshotMapper` preserves the original current claim dates, and `SocialSecurityOnlyReportAdapter` explicitly reports them. Therefore equal candidate outcomes do not make the original accepted request/context or payload interchangeable. This fixture does not authorize removing selected elections from the version-1 fingerprint, generalizing equality to every supported plan, relabeling B as A, or moving associations based on matching numbers. Any future relaxation would require an explicitly approved, versioned compatibility design and separate provenance handling.

## Smallest policy-consistent correction recommended for approval

**Keep freshness assessment, fingerprints and slot selection unchanged. Clarify provenance in the ordinary results UI.**

For a verified last-success result associated with another projection, identify it explicitly as the displayed last-success result and show its recorded projection/run identity (short IDs with full tooltip where practical). Distinguish this from the accepted Current UUID and the independent action's target. Example wording, constructed from verified frozen context:

> Displaying last successful SS Only analysis for the former Current projection (70/65). It does not match Current's saved inputs (70/62). A separate saved result for Current is available through View Associated Result.

Where originals are unavailable, omit ages/identity rather than infer them. A mismatch explanation can identify the category `Plan Social Security elections changed`, with test-scoped diagnostics or UI details for exact fields; do not log raw financial plans. Preserve original PDF assumptions and show provenance rather than changing financial content.

This correction would not automatically promote A, reset controls, replace slots, weaken Quick prerequisites, recalculate anything, or alter execution guards. A subsequent explicit adoption workflow allowing a pinned result to become the executable ordinary session/Quick prerequisite would be a separate policy decision, not part of this recommendation. Existing read-only View Associated Result already permits viewing A without rerunning SS.

## Native verification needed to close the instance-specific question

1. Record the active owner UUID and role manifest Current/Baseline UUIDs after the swap.
2. Record the displayed SS last-success run UUID, input/context fingerprints and original saved claim dates.
3. Find the association with that run UUID, if any; compare its projection UUID with Current. Never infer an association if absent.
4. Compare Current's exact accepted input-document digest and live configured-input match separately from the SS effective-input hash.
5. Compare actual next-run mortality factors, conditioning/valuation dates and exact discount decimal with the frozen result; then classify differences in S, M, V, algorithms, references and context. Do not stop at assuming claiming ages are the only native difference.
6. Open View Associated Result and verify its run UUID is A's, rather than assuming the previously displayed result has changed. The ordinary dialog should still retain B independently.

Without the native metadata these exact native identifiers and an exhaustive native mismatch list remain unconfirmed. No additional native access or financial data transfer was attempted.

## Automated verification and Git

Final focused Maven command used the required IntelliJ bundled Maven, project-local `.codex-m2/repository`, 4 GiB test heap, and explicit native exit propagation:

```text
-Dtest=SocialSecuritySwapFreshnessInvestigationTest,AnalysisFingerprintTest,CoupleSocialSecurityPersistenceTest,Phase12GFreshnessTest test
```

**100 tests passed; zero failures, errors or skips. BUILD SUCCESS, 26.454 seconds, finished 2026-10-27 17:01:21 -04:00.** Log: `target/phase12g-ss-swap-focused.log`. The new reproducer alone also passed both tests. An initial new-test accessor-name compilation error was corrected in test code only before passing runs. No assertions or goldens were weakened.

Full suite was not rerun for this test/documentation-only investigation. The preceding Phase 12G implementation report's full-suite result remains background evidence, not a new run. No new native Windows test was executed by Codex. Restoration uses a throwing projection factory and a throwing SS factory; the actual dialog's job generation remains zero.

Baseline and final branch/HEAD: `main`, `e620bec4a0011ab12d15fce670bff5011093d9da`. Baseline: 45 modified tracked files, 54 expanded untracked files, empty index. This investigation adds only:

1. `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecuritySwapFreshnessInvestigationTest.java`.
2. `PHASE-12G-SS-ONLY-SWAP-FRESHNESS-INVESTIGATION.md`.

Generated logs and fixture evidence remain under ignored `target/`. Final expected inventory is the same 45 tracked modifications and 56 untracked files; all prior work is preserved. `git diff --check` and final preservation checks are recorded below. No production code, schemas, fingerprints, financial algorithms, immutable artifacts or accepted historical values were changed. Nothing staged, committed, pushed, reset, cleaned or discarded.

**Stop for approval before implementing the recommended provenance/status clarification or any broader compatibility change.**

Final checks: `git diff --check` passed; 45 tracked modifications and 56 untracked files confirmed; index empty. All 20 preserved Phase 12G implementation source/test hashes still match their pre-investigation release hashes. The new report is valid UTF-8. These checks changed no production or historical data.
