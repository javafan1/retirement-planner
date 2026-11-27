# R3-B · Versioned Roth scheduling configuration

## Scope and starting baseline

**IMPLEMENTED_AND_TESTED.** The existing immutable Roth request now accepts an optional version-1 scheduling object. Both Roth editors present the pending settings. The plan creation workflow prepares a disabled Continue preference, and a typed admission guard protects financial execution and completed history.

Local `main` and its `origin/main` tracking reference pointed to `43f4dda9dd544bba58df5e95d401063291ebe645`. The starting tree contained the accepted R2a4 through R2b-D work, R3-A and earlier reports, and the prior R2a3-B report edit. Those changes were preserved. The accepted bounded comparison baseline is 5,768 passes and 35 skips.

## Model, validation and wire behavior

**IMPLEMENTED_AND_TESTED.** The existing start year, amount, frequency, strategy, target and legacy stop rule retain their APIs. The optional `scheduling` object has three required fields, `schedulingVersion`, `conversionEndYear` and `rmdSchedulingPolicy`. Version 1 accepts `CONTINUE_THROUGH_END_YEAR`, `SKIP_POSITIVE_RMD_YEARS` and `PERMANENTLY_STOP_AT_FIRST_RMD` as distinct identities. Their labels and descriptions are shared by both editors. Year boundaries are inclusive configuration. Both must be at least 1900. The end cannot precede the start, and One Time requires equal years. Annual accepts equal years. Out-of-horizon dates retain their configured values.

The local Jackson reader preserves the original fields and legacy shape, including absent scheduling and nullable stop. It rejects malformed explicit versions, wrong types, missing or duplicate schedule properties, and unknown policy strings. Legacy JSON uses its original representation, including when opened and saved under the new application. The existing identity-based request object remains immutable. Semantic comparison and disabled copy now include the scheduling object. Existing plan/snapshot clone paths pass the same immutable request. A persisted schedule participates in the frozen input digest, while view-only drafts do not.

## New plan preference and both editor surfaces

**IMPLEMENTED_AND_TESTED.** The controller's existing `newUserPlan` boundary, shared by ordinary creation and completed wizard creation, captures the finalized start year and initial horizon end for the pending Continue preference. The preference is session-only while these controls are locked. The accepted plan remains disabled, and its serialized request is unchanged. Opening a saved plan does not manufacture a new-plan preference from a null request.

Both existing editors use the same pending panel with read-only End Year and unavailable When RMDs Begin choices. The current execution controls remain available for legacy schedules. An explicit saved versioned request is shown read-only. A loaded enabled versioned request can be explicitly disabled while retaining all its fields. An already disabled versioned request cannot be enabled in either editor. Draft changes use the existing transactional Apply path, destination confirmation, dirty-state comparison and Cancel/reload. Pending controls are outside the Apply transaction and introduce no persisted draft changes. The dedicated editor now labels the existing year as Start Year. Legacy unspecified stop continues to display and round-trip as null, with a null-safe label for PDF and editor presentation.

## Admission and financial history

**UNSUPPORTED_OR_REFUSED.** An enabled version-1 request reaches a typed refusal at common projection admission, before annual calculation, regardless of whether its range overlaps the horizon. The standalone scheduler has the same defense. Controller refusal keeps the previously accepted result and the original input object. Completed snapshot construction also rejects an enabled version-1 input. An explicit disabled request can carry pending metadata in saved inputs while a projection runs with conversion disabled.

**EXISTING_BEHAVIOR_PRESERVED.** Legacy FIRST_HOUSEHOLD_RMD still blocks positive current RMD years in normal deterministic mode and remembers prior RMDs in lifetime context. NEVER and null remain permissive under their original semantics. The new names are never mapped onto those values. Schema 1–11 and history dispatch stay in place. Accepted projection associations, UUIDs, balances, financial fingerprints and output replay keep their existing policies. The PDF now safely labels nullable legacy stop rules as “Legacy unspecified (no RMD stop).”

The existing source gates, RMD and rollover sequencing, federal and state tax, Medicare, NIIT, conversion amounts and solver logic remain in force. This phase changes admission, input and presentation only.

## Files changed in R3-B

Production paths below are relative to `src/main/java/com/daviddunn/retirementplanner`.

- `app/export/ProjectionPdfExporter.java`
- `app/projection/CompleteProjectionSnapshot.java`
- `domain/model/RetirementPlan.java`
- `domain/projection/ProjectionEngine.java`
- `domain/roth/RothConversionConfiguration.java`
- `domain/roth/RothConversionRequest.java`
- `domain/roth/RothRmdSchedulingPolicy.java`
- `domain/roth/ScheduledRothConversionPolicy.java`
- `ui/controller/ApplicationController.java`
- `ui/controls/PendingRothSchedulingView.java`
- `ui/views/ResultsSummaryView.java`
- `ui/views/RothConversionView.java`

Test paths below are relative to `src/test/java/com/daviddunn/retirementplanner`.

- `domain/roth/RothSchedulingConfigurationTest.java`
- `persistence/RothConversionPersistenceTest.java`
- `ui/charts/ProjectionPdfExportTest.java`

This report is the only added documentation. The inventory distinguishes the phase from earlier unstaged work.

## Verification

Java 25 and IntelliJ's bundled Maven used the workspace-local repository offline. The established bounded selector is `target/r2a4-suite-selector.txt`.

| Run | Passed | Failures | Errors | Skipped | Maven exit |
| --- | ---: | ---: | ---: | ---: | ---: |
| Accepted R2b-D bounded baseline | 5,768 | 0 | 0 | 35 | 0 |
| Final focused | 466 | 0 | 0 | 0 | 0 |
| First bounded attempt | 5,777 | 1 | 2 | 35 | 1 |
| Bounded regression | 5,780 | 0 | 0 | 35 | 0 |

The focused selector covers Roth, spousal inheritance, retained projection serialization, snapshot persistence, PDF and wizard equivalence tests. An initial UI assertion looked up the summary control before mounting and applying CSS to its ScrollPane. The corrected test initializes the Scene and passes. The first bounded attempt caught exact legacy fingerprint ordering in `ExpenseEscalationCompatibilityTest.preChangeLegacyInputAndAnalyzerDigestsRemainExact` and two End Year help errors in `InputTooltipTest.assumptionsAndRothKeepPlanAndDirtyStateUnchanged` and `InputTooltipTest.resultsQuickEditorsReuseTheSamePlanningAndRothSemantics`. Explicit legacy property order and the standard End Year tooltip restored those tests without changing their assertions. Logs are `target/r3b-focused-certified.log`, `target/r3b-broad.log` and `target/r3b-broad-final.log`. The 35 skips match R2b-D by class and count. Both Stage 5D cancellation cases passed. Both Git whitespace checks exited 0. The index remains empty and HEAD remains at the starting checkpoint.

## Native acceptance

1. Create a synthetic plan through the wizard with a known projection start and horizon. Confirm conversions start disabled. Open Roth Conversions and Results Summary and check the same pending Continue window and disabled new controls.
2. Open a synthetic legacy plan. Check its existing request fields, make and cancel a legacy edit, then explicitly apply a supported legacy edit. Save, reopen, and verify that the legacy request has no versioned scheduling object.
3. Render the frozen Results Summary and PDF for a saved legacy request with null stop and confirm the explanatory label. Repeat in Light and Dark, and verify keyboard traversal leaves the pending controls out of the editing path.
4. Use a synthetic serialized version-1 request to check Save/Open. With an enabled version-1 request, verify the refusal and retain the last accepted result. Disable it and check that the pending metadata is preserved.

**DESIGNED_NOT_IMPLEMENTED.** Version-1 execution, applied calculation policy certification, and any later conversion source authorization belong to separate approved phases.

**BLOCKED_PENDING_APPROVAL.** R3-C awaits native acceptance and explicit authorization.
