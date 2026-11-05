# Phase 19B4-S3 — Analyzer Snapshot Save Failure

## Executive summary

The Save and Save As diagnostics both failed before filesystem writing while `ProjectionSnapshotComponents` decoded a stored analyzer sidecar. The two call paths are different (`verifyUnchangedAnalyzer` for Save, `resolveAnalyzer` for Save As), but they converge on the same `AnalysisSnapshotCodec` deserialization contract.

A deterministic synthetic reproduction identified a backward-compatibility failure in the v1 `COUPLE_SOCIAL_SECURITY` snapshot payload. Phase 19B2 added `benefitReductionPolicy` to the nested `SocialSecurityStrategyRequest` record while retaining the analyzer snapshot schema version. Older v1 couple Social Security payloads omit that JSON creator property. The codec's strict Jackson configuration rejects the missing property before the record constructor can apply its default reduction policy.

The codec now narrowly recognizes that legacy payload shape, supplies JSON `null` for the new property (which the record maps to the established no-reduction policy), and permits the old payload digest only when it matches the exact legacy representation. Save As re-encodes the cloned snapshot in its legacy shape when that condition holds. No analysis is discarded, recomputed, or migrated. A nonzero or otherwise changed reduction policy cannot use the legacy digest fallback.

The native diagnostics did not contain analyzer-family or property-path information, so they cannot prove whether the user's Save and Save As failures involved the same association or whether their exact underlying sidecar was a couple Social Security snapshot. The reproduction establishes a concrete incompatibility matching the reported exception class and preparation stage, and the production snapshot inventory identifies this as the only analyzer payload using the newly extended request record. Native retest remains necessary to confirm this was the user's artifact.

## Native evidence and preparation paths

The reported native Save failure followed:

`prepare → verifyUnchangedAnalyzer → AnalysisSnapshotCodec.decode → decodeSnapshot`

The reported native Save As failure followed:

`prepare → resolveAnalyzer → readAnalyzer → AnalysisSnapshotCodec.decode → decodeSnapshot`

Both reported `MismatchedInputException`, stage `PROJECTION_COMPONENT_PREPARATION`, `fileWritingStarted=false`, and `atomicReplacementAttempted=false`. Thus neither path had started destination serialization or atomic replacement when it failed.

Save verifies the previously associated analyzer component against the unchanged association. Save As resolves the component so it can preserve/copy its association to the new plan identity. The paths can decode the same sidecar when they refer to the same analyzer association/run ID; when a plan retains multiple associations they can decode different snapshots. The native record does not include the association/run identity, so sameness of the actual two native artifacts is undetermined. Both paths decode with the same codec and are affected by the same legacy-shape incompatibility.

## Snapshot contract and root cause

- Analyzer family: `COUPLE_SOCIAL_SECURITY`.
- Snapshot schema: version 1; no schema-version increment accompanied the new optional request setting.
- Payload path: `payload.request.base.benefitReductionPolicy`.
- Containing Java type: `SocialSecurityStrategyRequest`.
- Missing property's Jackson target type: `SocialSecurityBenefitReductionPolicy`.
- Legacy JSON structure: `payload.request.base` is an object with the v1 request fields (`analysisDate`, `primaryElection`, `spouseElection`, and `socialSecurityColaRate`) but no `benefitReductionPolicy` member. The expected token for the missing member is absent (synthetic classification: `MISSING`; there is no JSON token to report).
- Strict Jackson behavior: the production snapshot mapper enables `FAIL_ON_MISSING_CREATOR_PROPERTIES`; it rejects the absent record component before `SocialSecurityStrategyRequest` can normalize an explicit null to `SocialSecurityBenefitReductionPolicy.none()`.

The regression test constructs a synthetic current payload, removes only the new property, computes the legacy payload digest, and first confirms that strict Jackson throws `MismatchedInputException`. It asserts the missing property and Jackson target type. The production codec then decodes that same legacy structure to the no-reduction policy. A changed 22% policy paired with the old digest is rejected.

Phase 19 contributed the compatibility defect: adding a required serialized record component to an already-versioned snapshot request without a decoder default for old v1 payloads. The benefit-reduction calculation itself and the configured plan's 0%/nonzero values are not the issue. Missing reduction data in an old analyzer result means the historical result was calculated under the prior no-reduction behavior, so the safe compatibility value is 0% with the established default year.

## Compatibility correction

`AnalysisSnapshotCodec` now:

1. Detects only the known v1 `COUPLE_SOCIAL_SECURITY` legacy nested request shape, using its fixed application schema fields and absence of `benefitReductionPolicy`.
2. Adds an explicit JSON null for that new field in the in-memory decode tree. The existing request constructor maps null to the immutable no-reduction policy. Other missing creator fields remain subject to strict validation.
3. Validates the current payload digest normally. If it differs, a legacy serializer that omits only `benefitReductionPolicy` may validate the stored digest only for this analyzer family and only when the decoded policy is exactly `none()`.
4. Uses that legacy serializer when a Save As clone must retain the wire shape and payload digest of an original legacy snapshot.

The compatibility serializer is not used for new nonzero results. Current snapshots with 22%/2032 round-trip with that policy intact. No financial model, fingerprint rule, snapshot schema version, or persisted plan structure was changed by this correction. Historical analyzer data remains the original payload and is not recalculated.

## Persistence and historical safety

Save's unchanged-association verification can now decode the legacy sidecar and preserve its association. Save As can resolve, clone the plan association, and preserve the legacy analyzer payload and payload digest under the destination plan identity. Synthetic end-to-end controller tests exercise both `preparePlanSave` branches, reload the written plans, and read the associated analyzer snapshot back.

The same codec is used when restoring analyzer results and when reading associated historical results for reports. The compatibility path therefore also supports those legacy decode consumers. It does not replace a historical result with current-plan assumptions. Malformed documents and digest mismatches remain errors; they are not converted into empty snapshots.

No actual user plan, plan backup, or native data file was read or modified. Tests use synthetic results and JUnit temporary directories.

## Files changed for S3

- `src/main/java/com/daviddunn/retirementplanner/persistence/AnalysisSnapshotCodec.java` — strict, family-specific legacy defaulting, digest validation, and legacy-shape encoding for cloned legacy snapshots.
- `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/CoupleSocialSecurityPersistenceTest.java` — strict Jackson failure reproduction, legacy decode and integrity checks, current nonzero round-trip, both projection-component branches, and isolated controller Save/Save As preservation.
- `PHASE-19B4-S3-ANALYZER-SNAPSHOT-SAVE-FAILURE.md` — this report.

The existing Phase 19A–19B4 implementation, S1/S2 diagnostics, reports, and unrelated modified/untracked work were preserved.

## Verification

Focused persistence and association regression command selected `CoupleSocialSecurityPersistenceTest`, `AnalysisSnapshotCodecTest`, `AnalyzerAssociationStorageTest`, `ProjectionSnapshotPersistenceTest`, `ProjectionSwapTest`, `Phase12EAssociationTest`, `Phase12ECodecTest`, and `SocialSecurityReductionPersistenceTest`.

- Final focused run: **90 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.
- Final full Maven suite on the corrected source: **4,035 tests, 0 failures, 0 errors, 46 skipped; BUILD SUCCESS** (12m 26s).
- Skips are pre-existing opt-in, benchmark, and visual-preview tests; no tests were disabled or weakened.
- `git diff --check` passed after the implementation and report updates. Git emitted line-ending conversion notices for existing modified files only.

The earlier focused run briefly failed because its assertion expected Jackson's target type to be the containing `SocialSecurityStrategyRequest`. Jackson correctly identifies the missing property's type (`SocialSecurityBenefitReductionPolicy`). The assertion was corrected and the final focused suite passed.

## Remaining limitations and native Windows retest

The S2 diagnostic intentionally omitted analyzer family, schema version, expected type, property path, and token category. The synthetic reproduction and code audit establish a backward-incompatibility in the only persisted analyzer payload containing the changed request type, but the family of the exact native sidecar remains unconfirmed. No extra diagnostic fields were added because they are not needed for the narrow decoder fix and would only be necessary if native retesting reproduces a different failure.

After rebuilding the application, retest Save and Save As on a preserved copy of the plan that previously failed. Confirm both complete and then reopen the saved files. If a failure remains, share only the sanitized diagnostic record plus the approved analyzer family/schema/property/token fields; do not share plan contents or paths. Verify the historical analyzer result can still be restored and that its provenance is unchanged.

## Git and acceptance gate

No files were staged, committed, pushed, reset, cleaned, or reverted. Existing Phase 19 work was preserved. No real retirement plan files were accessed.

**Stop for native Windows acceptance. Do not begin new feature development.**
