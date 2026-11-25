# Phase 6E-P3D3C4D-R2a1 — Unified spousal inheritance foundation

## 1. Executive summary

**IMPLEMENTED_AND_TESTED.** A plan can save **SPOUSE AS OWNER** or **SPOUSE AS BENEFICIARY** for traditional IRAs and traditional 401(k)s inherited from the first spouse to die. The editor, Results Summary and projection PDFs identify the selection as planning only. Ordinary projections remain available and execute under their existing semantics. Pending choices have a versioned frozen input identity and a separate binding to unchanged annual evidence.

**LEGACY_BEHAVIOR_PRESERVED.** Eleven five-year financial snapshots and eleven legacy characterization variants matched before and after. Both pending choices matched legacy financial ledgers in eight additional Florida and Michigan configurations. This phase does not change RMDs, account debits, Roth conversions, taxes, Medicare, estate value or the projection engine.

## 2. Git and worktree baseline

Opened on `main` at `2bedb55e99fb48aa31225e9c5549c842d77ebb2d`, zero ahead or behind the local upstream reference. The index was empty. Opening status contained 32 tracked modifications and 30 untracked files. The phase adds scoped changes on top of the retained Arkansas and R1 work. It uses synthetic data only. Probes and logs stay under ignored `target/phase6e-p3d3c4d-r2a1/`. No private plans were read.

The D0R review, R1 structural boundary and R2a gate still apply. Household economic access and original source lineage remain separate from legal rights, RMD ownership, beneficiary obligations and conversion eligibility. R2a1 implements a planning input and represents the proposed account pathways. It does not resolve R2a's separate need to verify account facts and actual transactions before financial activation. [D0R audit](PHASE-6E-P3D3C4D0R-RMD-ROTH-MINIMAL-CHANGE-AUDIT.md) · [R1 foundation](PHASE-6E-P3D3C4D-R1-SURVIVOR-OWNERSHIP-FOUNDATION.md) · [R2a gate](PHASE-6E-P3D3C4D-R2A-SURVIVOR-RMD-IMPLEMENTATION.md) · [Succession design](PHASE-6E-P3D3C4C-SPOUSAL-SUCCESSION-DESIGN.md) · [Pooling audit](PHASE-6E-P3D3C4B-HOUSEHOLD-COMMINGLING-AUDIT.md)

## 3. Changes actually implemented

1. Versioned plan-level choice and backward-compatible Java/JSON normalization.
2. Two radio choices in the Assumptions survivor section and guided creation, with transactional draft handling and pending-only disclosure.
3. Immutable source-by-source structural classification using R1's resolved annual context.
4. Additive deterministic acceptance evidence and frozen disclosure in Summary and PDF, including the existing specialized Arkansas renderer.
5. Synthetic domain, persistence, snapshot, editor, and PDF tests. No changes to calculation, funding, analyzer admission, annual receipt writers or monetary expectations.

## 4. Exact new and changed source files

| Scope | Files |
|---|---|
| New plan model | [SpousalInheritanceAssumption.java](src/main/java/com/daviddunn/retirementplanner/domain/model/SpousalInheritanceAssumption.java) |
| Changed plan model/default | [EconomicAssumptions.java](src/main/java/com/daviddunn/retirementplanner/domain/model/EconomicAssumptions.java), [PlanningAssumptions.java](src/main/java/com/daviddunn/retirementplanner/domain/model/PlanningAssumptions.java), [RetirementPlanFactory.java](src/main/java/com/daviddunn/retirementplanner/domain/factory/RetirementPlanFactory.java) |
| New structural classifier | [SpousalInheritanceDisposition.java](src/main/java/com/daviddunn/retirementplanner/domain/retirementownership/SpousalInheritanceDisposition.java) |
| Changed R1 view and construction | [AnnualRetirementOwnership.java](src/main/java/com/daviddunn/retirementplanner/domain/retirementownership/AnnualRetirementOwnership.java), [RetirementOwnershipResolver.java](src/main/java/com/daviddunn/retirementplanner/domain/retirementownership/RetirementOwnershipResolver.java) |
| New deterministic evidence | [SpousalInheritancePlanningEvidence.java](src/main/java/com/daviddunn/retirementplanner/app/projection/SpousalInheritancePlanningEvidence.java) |
| Changed snapshot/publication | [CompleteProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java), [ApplicationController.java](src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java) |
| Changed presentation | [AssumptionsView.java](src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java), [ResultsSummaryView.java](src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java), [ProjectionPdfReport.java](src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfReport.java), [ProjectionPdfExporter.java](src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java), [ArkansasValidationReport.java](src/main/java/com/daviddunn/retirementplanner/app/export/ArkansasValidationReport.java) |
| New tests | [SpousalInheritanceAssumptionTest.java](src/test/java/com/daviddunn/retirementplanner/domain/model/SpousalInheritanceAssumptionTest.java), [SpousalInheritanceDispositionTest.java](src/test/java/com/daviddunn/retirementplanner/domain/retirementownership/SpousalInheritanceDispositionTest.java) |
| Extended tests | [ProjectionSnapshotPersistenceTest.java](src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotPersistenceTest.java), [AssumptionsViewTest.java](src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsViewTest.java), [ProjectionPdfExportTest.java](src/test/java/com/daviddunn/retirementplanner/ui/charts/ProjectionPdfExportTest.java) |

The report is the only documentation addition. The R2a1 edits to previously modified files are limited to the new feature's acceptance evidence, UI and disclosure, and the two R1 chronology pass-through sites.

## 5. Plan-level assumption design

`EconomicAssumptions.spousalInheritance` carries the optional versioned value. Its JSON object contains exactly numeric integer `version: 1` and named `treatment`. The enum deliberately exposes only owner and beneficiary. Unknown schema versions, unknown choices, numeric ordinals, unknown shape and invalid members fail validation. There are no per-account elections in the editor. Eligibility remains an independent account assessment.

## 6. User-facing default and legacy handling

New-plan factories prefer owner. The creation boundary can preserve it as a dormant choice for a single-member plan. The Assumptions group is visible for couples. Missing or whole-field JSON null remains `LEGACY_UNSPECIFIED_CALCULATION` internally, represented by an absent nullable setting. It serializes as omitted. Legacy editors show neither choice selected until an explicit selection is applied. Unrelated edits, Cancel, Open and Swap do not select a default on older inputs. Complete historical input bytes and original UUIDs are kept.

## 7. IRA and 401(k) treatment distinctions

| Original account | Owner proposal | Beneficiary proposal |
|---|---|---|
| Personal traditional IRA | Explicit own-IRA election after qualified rights and timing. Separate survivor owner grouping after the approved effective date. | Beneficiary IRA obligations apply under supported rules. No automatic aggregation into the spouse's own IRA group. |
| Traditional 401(k) | Plan-qualified spousal rollover into own traditional IRA treatment from a separately supported actual modeled rollover date. The original employer-plan identity stays intact. | Plan-specific spouse-beneficiary payout and obligation. The account remains an employer-plan source in the initial proposal. |
| Surviving spouse's present account | Remains the original-owner account. | Remains the original-owner account. |

Treasury rules require sole beneficiary and unlimited withdrawal rights for the own-IRA election. The election differs from remaining a beneficiary. For employer plans the spouse rollover applies only to an eligible distribution. Required amounts are ineligible for rollover, and the acceptance of a direct rollover and the receiving IRA's prior-balance treatment must be established. IRA aggregation is restricted to eligible groups, with employer-plan requirements normally determined separately. The 401(k) proposal carries no implemented rollover date or eligibility. [26 CFR §1.408-8](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-408-8.html) · [26 CFR §1.402(c)-2](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-402c-2.html) · [IRS Pub. 590-A](https://www.irs.gov/publications/p590a) · [IRS RMD FAQs](https://www.irs.gov/retirement-plans/retirement-plan-and-ira-required-minimum-distributions-faqs)

## 8. R1 immutable domain integration

The R1 view now retains the existing projection opening date for classification. The proposal classifier walks the ordered R1 references, preserving original identity, owner and class, records the year and selected policy, and reports the proposed lane, modeled survivor, supported date if separately available, basis and reason. It never creates a new account or adds anything to an RMD group. Two accounts with the same label remain distinct under existing opaque reference identity. The existing R1 resolver checks duplicate identities and inconsistent household/death bindings.

The classifier distinguishes `ACCOUNT_FACT_VERIFIED_FROM_INPUT` for living original ownership, `SUPPORTED_MODELING_ASSUMPTION` when independent R1 own-IRA evidence has resolved, `NOT_VERIFIABLE_FROM_EXISTING_INPUT` for proposed accounts missing required facts, and `UNSUPPORTED_SCENARIO` for refused categories and timing. A plan setting alone yields no tax-recipient authorization or RMD ownership. Only the independently affirmed own-IRA case carries its already-validated modeled date. Pending rollover and beneficiary cases explicitly carry unresolved reasons and no date. No balances are present.

## 9. Supported and unsupported cases

**IMPLEMENTED_AND_TESTED structural proposals.** Personal traditional IRA and traditional 401(k), either spouse surviving, both policies. Before death the existing original-owner facts are preserved. No successor execution is admitted.

**UNSUPPORTED_OR_REFUSED.** Rollover history, traditional 403(b), Roth accounts, preconfigured inherited accounts, other employer categories and non-retirement sources are outside the successor classifier. The classifier also refuses a pre-opening death, unresolved death-year boundary, multiple modeled deaths and missing sole survivor. Unknown account ownership, contradictory chronology and duplicate identities fail at the R1 input boundary. A reached but unverified future obligation needs a typed refusal rather than a zero amount.

## 10. RMD and Roth boundaries

**DESIGNED_NOT_IMPLEMENTED.** Beneficiary obligations and effective owner membership are separate lanes. A later beneficiary schedule needs the applicable starting date, distribution period, divisor and balance. The ordinary first IRA distribution calendar year differs from the legal required beginning date, usually April 1 of the following year. Death after that date can leave a mandatory unpaid balance. Neither absent data nor deferred starting eligibility proves that no RMD is due. Preserve the existing January 1 modeled death and filing transition; the death-year obligation remains R2b.

Future successor-owned assets need independent authorization for conversion and the actual mandatory distributions must be removed from convertible amounts. Beneficiary-retained assets remain outside ordinary conversion eligibility as the approved initial product restriction. This is a scope boundary. Federal law can permit certain eligible spouse rollovers under separately established facts. Preserve the surviving spouse's existing own sources and all current fixed, targeted and cutoff algorithms. The reviewed RMD regulations apply from distribution calendar years beginning January 1, 2025. [T.D. 10001](https://www.govinfo.gov/content/pkg/FR-2024-07-19/html/2024-14542.htm) · [26 CFR §1.401(a)(9)-2](https://www.ecfr.gov/current/title-26/chapter-I/subchapter-A/part-1/subject-group-ECFR6f8c3724b50e44d/section-1.401(a)(9)-2) · [IRS Pub. 590-B](https://www.irs.gov/publications/p590b)

## 11. Employer-plan caveat

The editor explains that employer plans may restrict payout and rollover options. Both choices keep investment assets available to the surviving household under current modeling. The future 401(k) treatments require verified or explicitly approved narrowly bounded plan terms, eligible source and destination, known mandatory payments and supported basis facts. Existing participant metadata does not establish rights or a completed rollover. No new plan-document or beneficiary configuration was added. [IRS beneficiary guidance](https://www.irs.gov/retirement-plans/plan-participant-employee/retirement-topics-beneficiary) · [26 CFR §1.401(a)(9)-7](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-7.html)

## 12. Frozen input identity

The new nested versioned field is part of the existing `CompleteProjectionSnapshot.freeze` input bytes for explicit choices. Different choices change the input fingerprint and make a previous result outdated under normal comparison rules. Omitted legacy fields do not change serialization or old identities. Financial analyzer fingerprints that consume the full plan inherit this input change. Social Security-only input grouping remains independent. No analyzer version or execution route was changed.

## 13. Historical receipt and provenance design

**IMPLEMENTED_AND_TESTED.** New accepted results with an explicit selection carry `spousal-inheritance-planning=v1/<treatment>` plus SHA-256 binding to the frozen input digest and retained annual evidence. The marker is explicitly separate from existing calculation provenance and is checked on reconstruction. Missing, mismatched, duplicate or unsupported planning provenance refuses. Existing Arkansas, benefit, premium and pension validation still sees its original calculation identity. This covers no-transaction years through the complete snapshot and writes zero new monetary entries. Legacy readers in this build preserve old serialized evidence, UUIDs, component roles and exact inputs.

**DESIGNED_NOT_IMPLEMENTED.** Actual financial activation must use distinct applied treatment IDs and a validated event-specific receipt contract. A future annual row should include selected and applied policy, frozen original account occurrence, original owner/class, effective survivor and qualified account category, legal effective date, obligation ID, required amount, actual debit, actual taxable recipient and conversion permission evidence if independently granted. A no-transaction annual payload can carry policy and obligation status, with no invented receipt. Restoration must verify the event against frozen bindings and reject mismatched account, recipient, date, source amount or policy. The general owner-match invariant of `IncomeSourceAttribution` remains unchanged. [RetainedProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java) · [ProjectionSnapshotComponents.java](src/main/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotComponents.java) · [IncomeSourceAttribution.java](src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java)

## 14. UI and Apply/Cancel behavior

The existing Assumptions survivor section has two keyboard-focusable radio choices, tooltips for each proposed route, and a wrapped explanation of household assets, employer restrictions and current calculation limits. No new dialog, third choice, per-account election or automatic optimizer. Existing applied choices survive unrelated edits, including the Results Summary quick edit. Apply updates through the existing draft lifecycle. Cancel reloads the applied value and preserves old-plan omission. JavaFX tests verified both themes and text layout at a compact window size. The existing Summary and ordinary and Arkansas PDF paths use frozen accepted disclosure when available. A stale legacy result does not inherit a newer live selection in its PDF.

## 15. Admission and feature gates

**IMPLEMENTED_AND_TESTED.** The development boundary admits the setting only as a planning preference. There is no projection, RMD, tax or Roth caller for the new classifier. Results with pending input stay visibly legacy, and accepted evidence cannot claim either treatment was executed. All pre-existing routes remain usable with their own existing limitations. Save, Save As and Open preserve both the pending choice and historical result identity. Swap retains its existing valid-current-result and saved-plan checks. No additional state, analyzer, weighted or Monte Carlo route accepts applied inheritance. Manual comparisons under actual treatment cannot start until later certified phases.

## 16. Zero financial-change proof

The pre/post synthetic probe compared eleven complete five-year snapshots, including both alive, either first death, original IRA and 401(k) cases, living RMD, fixed and targeted Roth, Michigan, Florida and Arkansas, and a lifetime control. It compared exact input and annual digests, account balances, RMDs, income, taxes, Medicare, funded withdrawals and estate, and verified restored evidence. Eleven auxiliary variants matched too. The transcripts matched SHA-256 `05BC3D18C9975B6BBFE8D3157BC066F2C0803362108F105922FB4B8F1FC1F656`.

An additional explicit-selection probe independently compared both choices against legacy annual rows in both death directions for Florida and Michigan, across eight configurations containing IRA and employer positions. Every complete retained ledger and annual digest matched. Frozen input identities differed for each prospective selection as specified. Focused snapshot tests exercised the controlled Arkansas acceptance branch with an explicit selection and no annual change.

## 17. Focused test results

Java 25.0.4.1 and IntelliJ bundled Maven, workspace-local Maven repository, `-o -B` with recorded focused selectors. No full Monte Carlo, weighted longevity, capacity or benchmark suite ran.

| Verification | Passed | Failed | Errors | Skipped | Exit |
|---|---:|---:|---:|---:|---:|
| Pre-change Maven baseline | 381 | 0 | 0 | 0 | 0 |
| Scoped production compilation | — | 0 | 0 | — | 0 |
| Initial focused check | 100 | 1 | 2 | 0 | 1 |
| Follow-up focused check | 102 | 0 | 1 | 0 | 1 |
| Final focused check | 444 | 0 | 0 | 2 | 0 |
| Legacy financial comparison | 11 snapshots + 11 variants | 0 | 0 | 0 | 0 |
| Pending-choice financial comparison | 8 configurations | 0 | 0 | 0 | 0 |

The focused retries corrected synthetic expectations for the new-plan default, the existing save-before-reswap requirement, PDF line wrapping, and the theme notice's computed height. No existing fixture amounts or golden results changed. The final two skips are existing interactive preview tests requiring `funding.preview` and `new.plan.wizard.preview`. Both Git whitespace checks passed. Nothing was staged, committed or pushed.

## 18. Future R2a2 and R2a3 contracts

**R2a2.** Capture independently qualified pretax personal IRA facts, use a valid own-IRA date, derive one member set for previous December 31 sizing and actual ordered debits, and prove same-reference effective payee and year. For 401(k) extend only after the separate R2c legal and plan-qualified rollover work described in the R2a report. A valid virtual treatment must preserve original source and support the actual taxable/non-taxable event, amount and effective date. No future Roth right follows automatically.

**R2a3.** Establish explicit supported spousal beneficiary treatment separately for IRA and employer plan, supported before/after required beginning date branch, beneficiary schedule and table, potentially deferred commencement, and actual annual withdrawals. Keep beneficiary and own buckets separate. Prove unverified mandatory amounts trigger refusal. No ordinary beneficiary-source Roth conversion.

## 19. Outstanding legal and modeling uncertainties

**BLOCKED_PENDING_APPROVAL for applied treatment.** The future authorized facts source for beneficiary status, unrestricted own-IRA rights, compatible zero basis, prior obligations and employer payout or rollover remains to be approved and substantiated. Separate before/after death-year rules, order of post-death withdrawals, deferred and previously satisfied mandatory payments, and state-specific eligibility also require evidence. No deemed election based on noncompliance, penalty simulation, third-party or non-spousal inheritance is part of this policy.

## 20. Explicit approval decisions

- Approve a narrow independent evidence source and typed refusal rules for actual qualification before R2a2. The plan-level preference alone supplies no legal attestation.
- Approve distinct applied-policy and effective-payee history contracts, with exact source/debit agreement, scoped state and route gates, and successful restore before publication.
- Review the initial 401(k) rollover assumptions with R2c-specific legal and acceptance cases before admitting employer plan assets. Traditional 403(b) remains deferred.
- Review beneficiary schedules, death-year mandatory amounts, Roth cutoff and successor conversion separately. Keep existing Arkansas rules and analyzers on their established scope.

## 21. Updated implementation roadmap

| Phase | Gate |
|---|---|
| R2a1 | Completed prospective input, provenance, and disclosure foundation. |
| R2a2 | Qualifying owner execution after independent facts, shared sizing/debit membership, applied evidence, and initial deterministic state approval. |
| R2c employer rollover | Separately reviewed employer-plan law and virtual rollover proof prerequisite for the 401(k) owner slice of R2a2. |
| R2a3 | Independently approved beneficiary IRA and 401(k) obligations, schedules and execution. |
| R2b | Death-year mandatory distribution and previously satisfied evidence. |
| R3 | Approved annual obligation-based cutoff, including beneficiary obligations where those are certified. Preserve historical lifetime latch results. |
| R4 | Separate verified successor source and destination, mandatory amount restriction and exact fixed/targeted settlement. |
| R5 | Michigan and cross-state verification, explicit historical and analyzer capability decisions, then native acceptance. Arkansas succession remains deferred. |

R2a1 stops before financial activation. R2a2 requires explicit approval.
