# Phase 6E-P3D3C4D-R2a4-M1 — Regression test maintenance

The three failing regression groups were repaired in test source. The revised fixtures exercise the accepted R2a4 production code and retain the original financial and serialized graph comparisons. The user reported native Windows acceptance for R2a4 before this maintenance phase.

## Checkpoint and starting worktree

`main` and `origin/main` pointed to `43f4dda9dd544bba58df5e95d401063291ebe645`, **Complete Arkansas groundwork and Florida spousal inheritance RMD foundation**. The worktree already contained the accepted R2a4 production, test and report changes and the earlier `exio` edit to the R2a3-B report. The index was empty. The starting changes were preserved. Tests used synthetic fixture inputs.

The [R2a4 report](PHASE-6E-P3D3C4D-R2A4-SUCCESSOR-ORDINARY-WITHDRAWALS.md), archived selectors, and focused, broad and checkpoint logs were used to locate the cases. The archived unmodified checkpoint run and a fresh run of the three classes on the accepted R2a4 tree each reported 45 cases, 34 passed, four failures and seven errors.

## Repairs and fixture changes

| Group | Reproduced diagnostic and root cause | Resolution |
| --- | --- | --- |
| `AssumptionsLayoutTest.originalControlsRemainUniqueAccessibleAndResponsive`, invocations 1–6 | The expected heading set had seven entries. The actual set also contained `Spousal Retirement Account Inheritance`. The section is a nested group in Household & Survivor Planning, as accepted in [R2a1](PHASE-6E-P3D3C4D-R2A1-UNIFIED-SPOUSAL-INHERITANCE.md). | Add the heading to the exact expected set and keep the seven-card assertion. Verify parent card, couple-only visibility and layout management, the two distinct plan-level radio buttons, shared selection group, uniqueness and keyboard focus in Light and Dark across the existing sizes. |
| `WizardFinancialEquivalenceTest.independentInputsAnnualResultsPersistenceAndSeededRunsAreEquivalent`, SINGLE, COUPLE, OPENING_RMD and EMPTY | The exact serialized input assertion failed at `<scenario> inputs complete financial persisted input contract`. Only the wizard output included `"spousalInheritance":{"version":1,"treatment":"SPOUSE_AS_OWNER"}`. The hand-built fixture replaced `EconomicAssumptions` and dropped the election. | Set version 1 Owner in the independent fixture alongside its original economic inputs. `NewPlanDraft` obtains its plan from `RetirementPlanFactory`, which establishes this default for new plans. All four cases still compare the full financial input JSON, annual results, save/reload and backup. Populated cases still compare the seeded trial results. The existing Assumptions UI test independently commits both Owner and Beneficiary selections. |
| `ProductionExhaustiveInterningTest.optimizedPayloadPreservesActualStateAccountAndAttributionGraphs`, Alabama invocation 20 | `State income tax unavailable for Alabama in 2027 ([UNSUPPORTED_MODEL_RULE]): Alabama pension DB-versus-DC classification is UNKNOWN and materially changes tax. Verify the original legal plan classification.` The reused synthetic pension defaulted to `UNKNOWN`, so evaluation stopped before interning. | Declare the Alabama synthetic pension `DEFINED_CONTRIBUTION`. The primary pension still starts January 1, 2026 at $10,000 monthly with 2% COLA. All other synthetic plan facts are unchanged. Both the baseline and detail comparisons use the same supported graph. The exhaustive codec comparison and legacy digest checks stay exact. `AlabamaPensionPreflightTest` continues to verify typed refusal when material pension facts are unknown. |

**FIXED_AND_VERIFIED.** The independent class runs passed for all three repairs. The broader checks below also cover historical and financial nonregression.

**PRE_EXISTING_AND_UNCHANGED.** The wizard factory default, the couple-only inheritance controls, the Alabama classification gate and the positive and refusal contracts remain production behavior. Legacy plans can still carry their historical absence of an inheritance preference. R2a4 financial execution, Roth rules, snapshot schemas 1–8, account references, fingerprints, UUIDs, replay, Save/Open/Swap and PDF behavior use the same production source as at the start of M1.

**UNSUPPORTED_OR_REFUSED.** Alabama still refuses a materially unknown pension classification. Tests exercise that refusal separately from interning.

**BLOCKED_PENDING_APPROVAL.** R2b remains a separate phase pending native acceptance and explicit approval. This investigation found fixture issues and did not identify a production defect requiring approval.

## Exact file inventory

- `src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsLayoutTest.java`
- `src/test/java/com/daviddunn/retirementplanner/ui/wizard/WizardFinancialEquivalenceTest.java`
- `src/test/java/com/daviddunn/retirementplanner/domain/projection/ProductionExhaustiveInterningTest.java`
- `PHASE-6E-P3D3C4D-R2A4-M1-REGRESSION-TEST-MAINTENANCE.md`

Production-code inventory for M1 is empty. The modified production paths in Git status belong to the accepted R2a4 worktree.

## Verification

IntelliJ's bundled Maven ran offline with Java 25 and the workspace-local `.codex-m2/repository`. The three classes ran independently before focused verification. The broad run reads the unchanged R2a4 bounded selector, which excludes the established long-running tests and includes all three repaired classes. Maven logs are under `target/r2a4-m1-*.log`.

The layout class ran `originalControlsRemainUniqueAccessibleAndResponsive` (six), `renderScrolledScreenAndVerifyTabNavigation` (six) and `independentStacksAndResponsiveOrdering` (four). The wizard class ran all four scenarios of `independentInputsAnnualResultsPersistenceAndSeededRunsAreEquivalent`. The interning class ran `internedDecimalsRetainExactScaleAndRejectOverLimit` (four), `optimizedPayloadPreservesActualStateAccountAndAttributionGraphs` (20) and `legacyExhaustiveBytesRemainReadableWithUnchangedLogicalDigest` (one).

| Run | Passed | Failures | Errors | Skipped | Maven exit |
| --- | ---: | ---: | ---: | ---: | ---: |
| Before edits, all three groups | 34 | 4 | 7 | 0 | 1 |
| AssumptionsLayoutTest | 16 | 0 | 0 | 0 | 0 |
| WizardFinancialEquivalenceTest | 4 | 0 | 0 | 0 | 0 |
| ProductionExhaustiveInterningTest | 25 | 0 | 0 | 0 | 0 |
| Focused financial, history, admission and UI verification | 737 | 0 | 0 | 1 | 0 |
| Same bounded broad regression selector | 5,707 | 0 | 0 | 35 | 0 |

The focused run covered all eight R2a4 successor cases, earlier RMD execution, Alabama classification and refusal, retained projection codecs, provenance, persistence, swap and PDF workflows. The one focused skip was the opt-in `DavidMedicareDeathAuditTest`.

The bounded broad run completed in 8 minutes 38 seconds. The 35 skips matched the prior run by class and count across all 23 classes reporting skips. Exact class names and counts are in `target/r2a4-m1-broad.log`. The pass count increased by eleven with zero remaining failures or errors.

Both `git diff --check` and `git diff --cached --check` exited 0. The index is empty, and the production path inventory matches the starting R2a4 worktree. M1 is ready for native acceptance. R2b remains at the explicit approval gate.
