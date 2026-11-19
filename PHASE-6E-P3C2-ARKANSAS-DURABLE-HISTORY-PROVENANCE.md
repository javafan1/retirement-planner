# Phase 6E-P3C2 — Arkansas Durable History and Provenance

## 1. Executive summary

**COMPLETE within the controlled integration boundary.** Genuine Arkansas projections can be retained, reopened, copied, and associated with their historical Monte Carlo and integrated Social Security analyses. Arkansas is a recognized historical jurisdiction. The ordinary production registry still contains 20 states. Sections 2 through 6 record the opening architecture and the compatibility decisions made before persistence changed.

## 2. Opening Git checkpoint and working tree

`main` and `origin/main` point to `baa25930dd2a87c4bfa982acce728f65efcdb04b`. The index is empty. The opening worktree contained 21 modified tracked files and 13 untracked files from P3B/P3C1. No subsequent checkpoint exists.

## 3. Existing 20-state admission baseline

AK, AL, AZ, FL, GA, IA, IL, KY, MI, MS, NC, NH, NV, OK, PA, SD, TN, TX, VA and WY. The picker uses alphabetical display names from `StateTaxJurisdiction`. `ProductionStateTaxService` installs explicit calculators and the enum's no-income-tax entries. Colorado has preparatory code only.

## 4. Current accepted-history architecture

- The plan and baseline input document store `PlanningAssumptions.stateTaxAssumptions` as a stable state code, method, and nullable rate override. The simplified method is the only admitted method. Older inputs default to Michigan.
- The engine emits a completed-year list or the completed prefix and a structured funding failure. Arkansas proof or evidence unavailability is an exception separate from insolvency. Each normal annual result carries a `ProductionStateTaxResult` with jurisdiction and calculation status. Pre-state annual records use their own legacy reconstruction path.
- Live annual results copy lists but reference account identities. The retained graph freezes account data, uses indexed references, and reconstructs detached accounts. The complete snapshot freezes immutable annual rows, original input JSON, non-investable rows, summary and report assumptions. Current and baseline roles point to projection UUIDs in the manifest.
- Annual income-source attribution carries recipient, original pension facts, and indexed withdrawal/transfer account identities. P3C1 stores source origin in the frozen pension input document. The legacy annual pension codec omits origin. Run-specific Arkansas receipt evidence, timing and additional-tax status go through annual funding trials and are not in existing history.
- `ApplicationController` captures the accepted original inputs, annual projection and derivative presentation data. `JsonRetirementPlanRepository` delegates component I/O to `ProjectionSnapshotComponents`. There is no production class named `ProjectionSnapshotPersistence`; that name identifies the lifecycle tests. The component store hashes compressed bytes and proves owner, projection UUID, path binding and input SHA-256. The annual codec validates reconstruction field by field. The analysis codec checks its canonical payload/context digest and the manifest also binds the compressed analysis component.
- Bounded strict Jackson readers reject unknown fields, duplicates, trailing tokens, and numeric coercions. Field order, exact decimal scale and specific historical readers preserve old canonical data. The settlement reader accepts the original ten fields and omits a zero funding surplus.
- Save As rebinds owner and storage path, preserving projection/source UUIDs and input digests. Analyzer owner is rebound with a new file checksum. Swaps resolve and validate both original components and their associated analyses, rebuild editable roots from frozen inputs, and reverse the role references without rerunning the engine.
- Application scenario creation discards accepted history and creates a new plan identity. The internal JSON-copy service removes projection state for isolated evaluation. Its current identity and baseline handling need to be checked against callers that consume only financial inputs.
- Controller admission joins a projection UUID and frozen input digest with a run ticket and request fingerprint. Associations also pin run UUID, analysis kind, input and context fingerprints, payload digest, component digest, and timestamp. Accepted analysis references and algorithm versions are compared with the next effective request. Historical display and current eligibility are distinct. Current/last-success routing also checks the active role and exact association.

## 5. Root cause of Arkansas historical identity limitation

The package-internal gated engine emits `ArkansasValidationTax` beside annual cash facts. Its normal state result is absent, the annual jurisdiction getter throws, and retained capture refuses it. The detached CSV/PDF report has frozen selected values and diagnostic versions, but lacks the original projection UUID, manifest and full annual input/evidence graph.

## 6. Identity versus admission distinction

`StateTaxAssumptions`, `ProductionStateTaxResult`, and the retained legacy state record use `StateTaxJurisdiction`. The same enum currently supplies picker values and no-tax registration. Calculator guards, source admission checks and analysis version helpers compare explicit state constants. Results, exports and console code use the projection's state getter. An Arkansas identity can be recognized independently, provided assumptions, production routing, the picker and legacy state records explicitly enforce production admission.

The new schema adds a discriminated Arkansas history component to the retained graph. Legacy schemas and annual wire fields keep their existing contracts. Pre-P3C2 applications cannot read the new Arkansas schema. Detached P3B reports lack sufficient provenance for conversion into accepted history. The configured jurisdiction remains in the restorable input document so role swaps can rebuild ordinary editable inputs. The Arkansas execution identity is explicit in the result and governs its output and freshness.

## 7. Genuine Arkansas annual result schema

`RetainedProjectionSnapshot` schema 3 adds a typed `Arkansas` component with component version 1, jurisdiction `AR`, projection algorithm version, the approved policy map, and one typed record per annual row. Existing annual rows retain their exact financial fields and ordered source and balance references. Each Arkansas extension carries filing status, first projected receipt date, government rules version, modeled deaths, a bounded selected election, execution proof metrics, and receipt provenance. The captured targeted federal taxable income is present when the targeted policy ran.

## 8. Source evidence and pension provenance

Pension payment occurrences refer to the annual income-source index. That source contains the amount, actual recipient, original owner, name, participant facts, and original pension terms including the DB/DC classification. Arkansas adds the original origin and the effective legal qualification. The complete reader requires an exact unique match in the frozen original member and pension inputs. Weighted continuation receipts use the original frozen pension ordinal and retain their executed strategy, mortality years, payment year, recipient, amount and effective qualification.

Receipt evidence explicitly identifies a supplied validation receipt or a window derived from a modeled transaction. A separate indicator identifies supplied additional-tax status. A persisted employer selection is preserved as an input fact. Effective qualification is the calculation's interpretation. UNKNOWN remains UNKNOWN when a separate internal receipt substantiates qualification. No source document is retained.

## 9. Immutable monetary accounting

The same annual record owns federal and state tax, gross funding, executed Roth by owner, RMDs, ending accounts, retained assets, estimated heir tax, and estate totals. Taxable funding is read from the ordered executed funding trace on restoration. The detailed pension and source evidence refers to that same graph. Arkansas validation compares exact settlement sources, cash uses, state plus federal tax, Roth and funding totals, retained carry-forward, component balances and estate totals. The aggregate growth column is the modeled input to proportional account crediting. Account growth shares use the established 12-decimal rounding, so replay uses the stored account totals for asset reconciliation.

The worksheet captures the already selected exclusion and each senior election at the existing strict-best selection point. Ties, candidate ordering, statutory cents, thresholds, solver budgets, conversion behavior and tax amounts follow the existing authorities.

## 10. Arkansas rule-version identity

| Dependency | Captured effective value |
| --- | --- |
| Arkansas income-tax rules | `AR-2026-P2-1` |
| Minimum-sufficient tax funding | `AR-FUNDING-P2-1` |
| Targeted Roth optimization | `AR-TARGET-P3A-1` |
| Projection algorithm | Existing `v1` |
| Federal, RMD and Medicare inputs | Actual projected GovernmentRules identifier in each annual row |

The version 1 reader has an explicit policy allowlist. The effective implementation versions are captured at execution. A future effective version affects freshness while a reader for this approved historical tuple can still display it. An unrecognized historical tuple is rejected.

## 11. Canonical serialization

The retained reader accepts schemas 1 and 2 with their original required fields and schema 3 with the required Arkansas component. Omitted optional fields keep legacy field order and bytes. The reader binds exact decimal trees to strict records and then checks reproduction through the existing detached projection reconstruction. Existing component SHA-256, bounded gzip reader, strict duplicate detection, and analysis content digest remain in force. Arkansas analysis provenance is covered by that content digest.

## 12. Backward compatibility

Existing state annual rows and the original settlement schema retain their canonical writers. Zero funding surplus remains omitted. The older ten-field settlement and the positive-surplus accounting fixture still use their own validation contracts. Earlier pension inputs default to UNKNOWN. Existing analyzer payloads have no Arkansas property and retain their existing fingerprints and payload digests. There is no legacy-to-Arkansas migration.

## 13. Save As and reopen

The controlled test captures directly from the gated engine, then saves through the existing JSON repository and retained sidecar transaction. Reopening uses a controller with an engine that throws on any calculation. Save As gives the file a new plan UUID and owner binding. Projection UUIDs, source plan identity, original input documents, annual provenance, run UUIDs and analysis payload digests are preserved. Owner-specific component checksums are regenerated as required.

## 14. Scenario copy behavior

The existing internal working copy removes the projection manifest for isolated calculations and preserves its existing identity semantics for callers. The explicit scenario-copy path assigns a new plan UUID and keeps editable inputs, including pension origin and DB/DC. A retained baseline becomes input-only in that scenario. Projection components and analyzer associations stay with the original plan.

## 15. Current/baseline swap

The lifecycle test applies alternating role swaps among two real Arkansas results with different original pension classifications. One uses an UNKNOWN plan input with explicit internal qualification. The other uses the persisted employer origin and DB classification. The swap exchanges role pointers and restores each exact input document. Annual and associated-analysis identity survive repeated swaps, then Save As and reopening.

## 16. Projection UUID and fingerprint binding

An Arkansas analysis dependency identifies the original projection UUID, its input SHA-256, and the canonical retained annual component SHA-256. Observed executions must use the captured financial inputs. The ordinary manifest continues to bind plan owner, analysis kind, run UUID, fingerprints, timestamp and component digest. Paired Monte Carlo additionally pins its separately verified alternative projection UUID. Role changes preserve both pins. An equal input fingerprint alone does not create an association.

## 17. Analysis current/last-success freshness

The established analysis fingerprints continue to track effective financial and analyzer inputs. Arkansas dependency checks include the state tax and funding policies and include targeted Roth only when the engine exercised it. The gated comparison API accepts the effective policy set explicitly. Ordinary production workflows report the Arkansas analysis as historical while role association still identifies the exact selected projection. An origin edit changes full financial inputs without changing prior results or the unrelated Social Security-only fingerprint.

## 18. Monte Carlo retained analysis

The bounded fixed and paired fixtures use the injected Arkansas engine. Snapshots use the existing compact format for request, seed, settings, outcomes and distributions. The paired fixture uses two shared economic worlds and verifies both original projection dependencies and the alternative pin. Observed insolvencies and completed executions are distinguished. Arkansas calculation or proof unavailability still aborts stochastic work before snapshot capture.

## 19. Integrated Social Security retained analysis

Controlled Quick Comparison preserves its baseline, ordered strategy entries and detached candidate annual graphs. The annual digest of the Quick baseline must match the original accepted projection. A financial Arkansas Quick payload requires the gated execution provenance. Social Security-only calculation, ranking, and dependency contracts are unchanged.

## 20. Longevity-weighted retained analysis

The controlled comparison uses the injected gated engine on the caller's thread, including continuation carriers. The existing weighted payload stores strategies, aggregates and bounded selected scenarios. The provenance adds the original projection reference and compact pension receipts from actual executed carrier or fallback runs. Selected second-death results retain their precise retirement balances and death dates. The bounded continuation fixture agrees with its independent projection comparison and retains evidence of direct survivor payments. A failed scenario cannot supply a successful expected-value result.

## 21. Frozen PDF/CSV exports

The Arkansas export reads the accepted annual values and selected election. The CSV identifies jurisdiction and Arkansas report schema 1. The PDF includes federal and Arkansas tax, gross and taxable funding, executed Roth, exclusion, senior elections, retained amounts, ending assets, estate, original rules and planning limitations. The original discontinuity fixture prints `Retained from tax-funding surplus: $1.34`. The visible report distinguishes live gated validation from retained Arkansas history. Stored accounting and identifiers survive live edits and file reconstruction.

## 22. Tamper and schema validation

The focused tests alter jurisdiction, tax/funding/Roth policy versions, schema, funding, retained balances, surplus, federal/state fields, election and source references. Contradictory bindings are rejected, including an analysis offered to the wrong projection and an invalid legacy state record carrying Arkansas. The complete projection verifies frozen pension origin against the actual source occurrence. File checksums, schema validation, and accounting validation address different integrity failures. Hashes detect changes against recorded content and are independent of cryptographic authentication.

## 23. Production activation isolation

`StateTaxJurisdiction.AR` is a historical identity. `StateTaxAssumptions` rejects it during programmatic construction or ordinary plan load. The picker and service registration use the explicit production set. Production service routing and the normal production result reject unadmitted states. The Arkansas engine capability is package-internal, with no preference or settings switch. The production controller still uses the registered-result capture. Controlled history uses the explicit capture method and the standard retained-component transaction.

## 24. Serialization/storage performance

These are local single-fixture measurements under Temurin 25.0.4.1.

| Fixture or operation | Observed |
| --- | --- |
| 30-year retained annual graph | 187,185 raw bytes, 10,884 gzip bytes, about 6,240 raw bytes per year including shared metadata |
| Annual source-evidence section across 30 years | 18,027 raw bytes, about 601 raw bytes per year |
| 30-year complete saved component | 190,772 raw bytes, 12,058 gzip bytes, plus 1,850 bytes of plan JSON |
| Retained graph encode and decode | 14.7 ms encode and 14.6 ms decode |
| Complete 30-year repository Save and reload | 188.9 ms Save and 20.5 ms reload including component verification |
| Two-year controller Save and reload | 64 ms Save and 17 ms reload |
| Two-world fixed Monte Carlo envelope | 8,233 raw bytes, 2,985 gzip bytes |
| Bounded Quick Comparison envelope | 71,241 raw bytes, about 8,500 gzip bytes |
| Bounded weighted comparison envelope | About 10,220 raw bytes, about 2,990 gzip bytes |

The codec stores one policy tuple and one account registry per projection, references the annual source list by position, and avoids copying frozen household inputs into annual metadata. Compact analyses retain a bounded number of dependency records and release the observed projection graphs after each execution. The weighted evidence list is bounded at 10,000 receipts. No isolated peak-heap measurement was taken.

## 25. Independent Arkansas financial fixtures

The actual engine retains the $1.34 discontinuity surplus, $8,949.09 deferred-funded target and $16,500 cash-funded control. Controls cover Single, MFJ, both direct-survivor directions, UNKNOWN with explicitly supplied qualification, employer origin, material UNKNOWN refusal, exact spending and the funded/missing-proof distinction. The 30-year fixture retains account balances, annual rules and actual funding. The scenario and swap fixtures preserve different run-time provenance for the same named pension.

## 26. Existing-state financial invariance

The 20 admitted calculations use their existing execution routes. Registry tests iterate the production list in the same declaration order where used by financial goldens, and the same display-name order in the picker. Results use the existing state/report contracts, with the Arkansas exception behind the explicit historical identity. No baseline money golden was changed.

## 27. Focused test results

IntelliJ IDEA bundled Maven, Temurin Java 25.0.4.1, and the workspace-local `.codex-m2/repository` ran the focused suite. **575 discovered, 574 passed, one visual preview skipped, zero failures or errors.** It covered Arkansas calculations and history, both codecs, scenario and role movement, analysis associations and freshness, Monte Carlo history, report exports, registry, pension-origin and Alabama classification, Michigan and Alabama goldens, and Light/Dark Arkansas history rendering. The supplemental repository round trips for paired Monte Carlo, Quick and weighted, and the complete 30-year measurement passed **8 tests, zero skips, failures or errors**. [Focused log](target/phase6e-p3c2-focused-final.log) · [Repository follow-up log](target/phase6e-p3c2-history-verification.log)

## 28. Bounded test results

The required filter `-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*Performance*,!*Exhaustive*,!*Search*` discovered **5,477 tests**. **5,442 passed**, 35 skipped, and zero failed or errored. Maven exited successfully after 10 minutes 9 seconds. This is four additional passing tests with the same skip count as P3C1. The supplemental run added assertions to two already counted test classes after the bounded run. Production code was identical across the bounded and supplemental runs. [Bounded log](target/phase6e-p3c2-bounded.log)

## 29. Changed-file manifest

Paths below are relative to `src/main/java/com/daviddunn/retirementplanner/` or `src/test/java/com/daviddunn/retirementplanner/` as indicated. This lists this phase's edits, including files already modified or untracked at the opening checkpoint.

| Scope | Files |
| --- | --- |
| Main, exports and projection application | `app/export/ArkansasValidationReport.java`, `app/projection/AnalyzerAssociation.java`, `app/projection/CompleteProjectionSnapshot.java`, `app/projection/ProjectionRoleManifest.java` |
| Main, Social Security applications | `app/socialsecurity/LongevityWeightedIntegratedStrategyComparisonService.java`, `app/socialsecurity/RetirementPlanScenarioCopyService.java` |
| Main, retained analysis | `app/socialsecurity/results/AcceptedAnalysisCompletion.java`, `AnalysisCompatibilityPolicy.java`, `AnalysisSnapshot.java`, `AnalysisSnapshotReader.java`, `AnalysisVersions.java`, `ArkansasAnalysisProvenance.java`, `QuickComparisonSnapshot.java`, `RetainedProjectionSnapshot.java` |
| Main, domain model and projections | `domain/model/StateTaxAssumptions.java`, `domain/model/StateTaxJurisdiction.java`, `domain/projection/ArkansasAnnualProvenance.java`, `ArkansasProjectionValidation.java`, `ArkansasValidationTax.java`, `ProjectionEngine.java`, `ProjectionYear.java` |
| Main, tax | `domain/tax/ArkansasAnnualFunding.java`, `domain/tax/state/ArkansasProductionTaxCalculator.java`, `domain/tax/state/ProductionStateTaxResult.java`, `domain/tax/state/ProductionStateTaxService.java`, `domain/tax/state/arkansas/ArkansasJointWorksheet.java` |
| Main, persistence and UI | `persistence/AnalysisSnapshotCodec.java`, `persistence/ProjectionSnapshotComponents.java`, `ui/controller/ApplicationController.java`, `ui/summary/ProjectionYearDetailsPane.java`, `ui/views/ResultsSummaryView.java` |
| Test, Arkansas | `app/socialsecurity/ArkansasLongevityValidationTest.java`, `domain/projection/ArkansasProjectionIntegrationTest.java`, `domain/projection/ArkansasValidationAnalysisTest.java`, `domain/tax/state/arkansas/ArkansasWorksheetTest.java`, `persistence/ProjectionSnapshotPersistenceTest.java`, `ui/wizard/ArkansasHistoryUiTest.java` |
| Test, legacy admission and codec | `domain/projection/PennsylvaniaProductionProjectionTest.java`, `domain/projection/ProductionExhaustiveInterningTest.java`, `domain/projection/RetainedProjectionCodecTest.java`, `domain/projection/StateTaxFundingFeedbackTest.java`, `domain/tax/state/NoIncomeTaxAdmissionTest.java`, `domain/tax/state/colorado/ColoradoWorksheetCompletionTest.java`, `domain/tax/state/simplified/IowaMississippiAdmissionWorksheetTest.java`, `persistence/RetirementParticipantPersistenceTest.java`, `ui/wizard/StateTaxVisualPreviewTest.java` |
| Report | `PHASE-6E-P3C2-ARKANSAS-DURABLE-HISTORY-PROVENANCE.md` |

## 30. Remaining Arkansas admission blockers

Production admission remains a separate decision. Prior Arkansas legal and modeling limits still apply, including nominal hold, limited low-income proof, unsupported special pension and basis situations, and Form 5329 evidence constraints. The retained contract covers the reviewed identities and the bounded Arkansas analysis families exercised here.

## 31. Phase P3C3 dependency and acceptance plan

Future work requires separate approval of any production workflow, legal review, source collection, and release admission. P3C2 provides the history foundation for that work.

## 32. Native Windows acceptance requirements

The JavaFX focused history test renders Arkansas details in Light and Dark and verifies the ordinary assumptions picker. A native end-to-end release acceptance remains a later gate. Its checklist includes 100% and 150% scaling, history reopen, both role swaps, historical analyzer navigation, and export preview from each role after editing current inputs.

## 33. Git verification and approval request

`git status --short --untracked-files=all` reported 50 modified tracked files and 17 untracked files. The opening 21 modified tracked files and 13 untracked files are still present. `git diff --check` passed. The index is empty and HEAD remains `baa25930dd2a87c4bfa982acce728f65efcdb04b`. Arkansas and Colorado remain outside production admission. Please approve any separate P3C3 work explicitly.
