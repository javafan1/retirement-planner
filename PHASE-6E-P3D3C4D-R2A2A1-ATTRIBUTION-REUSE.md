# Phase 6E-P3D3C4D-R2a2-A1 revised — Existing attribution reuse

## 1. Executive summary

**IMPLEMENTED_AND_TESTED.** The existing RMD attribution can represent a survivor receiving a distribution from the deceased spouse's original IRA. It reuses the original account reference, existing recipient, year, kind, gross and taxable amount. The guarded exception requires the existing RMD debit plus a matching resolved annual ownership disposition. The existing retained income row has one additive optional treatment marker. A synthetic row round-trips against the original account graph and verified debit. Complete pending or ordinary history refuses marked rows until an applied policy is implemented.

The common-case hypothetical convention now uses the plan-level Owner selection. It requires separately established mandatory-distribution clearance and continues to distinguish an assumption from a documentary fact. No financial path calls the new resolver or receipt factory. No formula, balance, cash flow, RMD, Roth, state decision or analysis admission changed.

## 2. Git and workspace baseline

Opened on `main` at `2bedb55e99fb48aa31225e9c5549c842d77ebb2d`. The index was empty. The opening tree had 39 tracked modifications and 37 untracked paths. Existing Arkansas and survivor work was preserved, with narrow edits to the existing retained reader and R1 resolver. Probes and logs stayed under ignored `target/phase6e-p3d3c4d-r2a2a1/`. All data used here is synthetic. No private plans were accessed and no files were staged, committed or pushed.

The audit reconciled the earlier RMD/Roth review, R1 ownership classes, R2a stop gate, R2a1 selected input and R2a2-A evidence gate. The revised authorization supersedes the proposed beneficiary attestation for every account. The legal scope, engine order and compatibility findings still apply. [D0R audit](PHASE-6E-P3D3C4D0R-RMD-ROTH-MINIMAL-CHANGE-AUDIT.md) · [R1 foundation](PHASE-6E-P3D3C4D-R1-SURVIVOR-OWNERSHIP-FOUNDATION.md) · [R2a gate](PHASE-6E-P3D3C4D-R2A-SURVIVOR-RMD-IMPLEMENTATION.md) · [R2a1 choice](PHASE-6E-P3D3C4D-R2A1-UNIFIED-SPOUSAL-INHERITANCE.md) · [R2a2-A findings](PHASE-6E-P3D3C4D-R2A2A-SPOUSE-OWNER-IRA-RMD.md)

## 3. Existing attribution capability inventory

| Question | Existing behavior and reusable evidence |
|---|---|
| A. Original identity | `AccountReference.capture` retains opaque original account object identity plus frozen owner, type and participant facts. Two matching names do not merge. `RetainedProjectionSnapshot.CaptureRegistry` assigns an account occurrence index to the original position. |
| B–C. Debits and RMD source | `ProjectedWithdrawalAllocator.allocateHouseholdRmds` debits existing projected positions and calls `calculateWithdrawalBreakdown`. Positive before/after balance deltas become `RMD/CASH_FLOW` sources with the original account reference and amount. `ProjectionYear` and retained annual records already keep those sources, ending balances, RMD cash and totals. |
| D–E. Original versus recipient in existing flows | `IncomeSourceAttribution.owner` holds the recipient for survivor pension and conditional receipts. Pension original owner lives in `PensionFacts`. Conditional benefit roles stay in the existing receipt. Neither links a withdrawal to a different original investment-account owner. Existing inherited IRA account information describes donor/beneficiary facts and does not preserve a separate deceased-household-position payee for the ordinary RMD path. |
| F–G. Owner equality | `IncomeSourceAttribution` checks account owner equality on creation and `RetainedProjectionSnapshot.Income.restore` uses the same constructor. It is a guard for supported account-linked events. An eligible own-IRA election can create a different modeled recipient while preserving original source lineage. |
| H–J. Reuse and real gap | Account reference and recipient, year and amount already exist on the same income row. A scoped marker is needed to distinguish an authorized successor RMD from a forged mismatch and to fail closed when history lacks applied context. The marker carries no second amount or account ID. |
| K–L. History verification | Keep original frozen input digest, canonical retained account indexing, component checksum and UUID/role checking, monetary evidence and restore/recapture comparison. Contextual restoration can validate one existing RMD row against the resolved ownership and independently established source debit. Full applied validation still needs a run-level identity and source/annual reconciliation. |

[AccountReference](src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java:335) · [Allocator](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectedWithdrawalAllocator.java:471) · [ProjectedPortfolio.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectedPortfolio.java) · [Retained projection and annual ledger](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java) · [ProjectionSnapshotComponents.java](src/main/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotComponents.java)

## 4. Reusable classes, fields and validation

Existing `WithdrawalBreakdown.getIncomeSources` is the per-account debit evidence and remains monetary authority. The same row uses `kind=RMD`, `purpose=CASH_FLOW`, `owner` as the actual payee, `account` as the original source, and its original description, gross, taxable amount and year. `RetainedProjectionSnapshot.Reference` already serializes the account occurrence and participant facts and resolves the same reference against the reconstructed graph. Save and Open preserve the snapshot checksum. Save As and Current/Baseline Swap continue to use existing component and role binding. The retained graph and existing PDF renderer remain presentation authority. All old routes use their original versions. [CompleteProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java) · [ProjectionSnapshotPersistenceTest.java](src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotPersistenceTest.java) · [ProjectionPdfExporter.java](src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java)

## 5. Exact owner-equality constraint

For ordinary account-linked receipts the legacy owner must still equal the original account owner. The guarded exception applies to a fully taxable positive ordinary personal traditional IRA RMD that already has a year and whose account, participant facts, effective recipient and date/year match a resolved eligible disposition. Its in-memory authorization is minted privately from the existing debit. Direct construction with a different owner and no authorization still throws the existing owner-match error. Reusing an authorization for other amounts, accounts, tax years or a Roth event fails. Opening RMD, ordinary withdrawal, funding and destination validation retain their original behavior. [IncomeSourceAttribution.java](src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java:161)

## 6. Existing beneficiary and survivor patterns

The survivor pension calculator supplies the actual scoped recipient using `IncomeSourceAttribution.pension`; `PensionFacts.originalOwner` preserves the configured owner independently. Conditional and life-insurance benefits attach existing validated definition and event facts and use the same recipient field without an investment-position debit. They establish precedent for a separate recipient and source identity within a single row. Their kind-specific receipt validation does not authorize an account-linked RMD. Roth uses its existing same-owner source and Roth destination rule and aligned source purpose. [HouseholdPensionIncomeCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/income/HouseholdPensionIncomeCalculator.java) · [ConditionalBenefitReceipt.java](src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitReceipt.java) · [ProjectedRothConversionResult.java](src/main/java/com/daviddunn/retirementplanner/domain/roth/ProjectedRothConversionResult.java)

## 7. Chosen minimal extension and alternatives

| Option | Conclusion |
|---|---|
| Existing fields alone | Economically sufficient for source and payee. Insufficient to distinguish approved treatment from a malformed mismatch, or to make a detached retained row fail closed. Making the existing equality unconditional loose would also affect other kinds. |
| Optional scoped treatment on the same attribution row | **IMPLEMENTED_AND_TESTED.** Reuses ordinary row shape, monetary fields and registry, with runtime authorization and one retained marker. Restore can use existing account graph and debit evidence. |
| Companion retirement event/payee ledger | Redundant. It would have to duplicate account references and amounts and keep two event streams consistent. |

The runtime authorization attaches to the one existing attribution row. It carries no extra cash flow. The retained row persists the existing R1 treatment identity `MODELED_SPOUSE_AS_OWNER_NEXT_YEAR_V1`. The year and effective date can come from the verified annual R1 disposition and frozen January 1 death chronology. There is no second new persisted policy family.

## 8. Actual modified files and method sites

| File | Change |
|---|---|
| [RetirementOwnershipResolver.java](src/main/java/com/daviddunn/retirementplanner/domain/retirementownership/RetirementOwnershipResolver.java:73) | `resolveForPlanning` adapts the selected Owner convention into existing R1 inputs with independent mandatory clearance. |
| [IncomeSourceAttribution.java](src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java:186) | Scoped `forSuccessorRmd`, private authorization creation, constructor checks and version-preserving `inYear`. Existing overloads still default to ordinary attribution. |
| [RetainedProjectionSnapshot.java](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java:465) | Existing `Income` capture, row field/reader and contextual `restoreSuccessorRmd`. Unapplied envelopes reject the marked row. |
| [RetirementOwnershipResolverTest.java](src/test/java/com/daviddunn/retirementplanner/domain/retirementownership/RetirementOwnershipResolverTest.java), [IncomeSourceAttributionTest.java](src/test/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttributionTest.java) | Extended existing tests. |
| [SuccessorRmdRetainedIncomeTest.java](src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/results/SuccessorRmdRetainedIncomeTest.java) | Row-level reader and contextual reconstruction tests in the existing retained-reader package. |

There were no engine, settlement, allocator, account model, persistence subsystem, controller, UI or exporter edits. This report is the only new documentation.

## 9. Planning convention representation

**IMPLEMENTED_AND_TESTED.** The plan-level Owner selection supplies the approved hypothetical sole-direct-spouse, unlimited-rights, supported pretax/basis and personal IRA planning convention. The caller supplies a set of original references whose earlier and election-year mandatory status has been affirmatively resolved for that frozen run. The convention never fills that set. Missing clearance stays `MANDATORY_DISTRIBUTION_STATUS_UNRESOLVED`. An unrelated reference rejects. Existing R1 checks still validate the account classification and contradictory metadata, calendar boundary, survivor roster, count of deaths and decedent's first distribution year. The positive result remains `SUPPORTED_MODELING_ASSUMPTION` in the R2a1 classifier. Original ownership is `ACCOUNT_FACT_VERIFIED_FROM_INPUT` and unsupported shapes retain explicit reasons.

No per-account confirmation or extra user input was added. Future admission must substantiate the source of mandatory clearance and applicable table, and explicitly refuse known conflicting facts. Assumed plan rights do not establish a real legal election. Treasury's own-IRA rule uses the sole-beneficiary and unlimited-rights conditions, and distinguishes death-year and catch-up obligations. [26 CFR §1.408-8](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-408-8.html) · [IRS Pub. 590-B](https://www.irs.gov/publications/p590b)

## 10. Original, effective owner and recipient model

For an original PRIMARY IRA, the resolved R1 disposition identifies SPOUSE as the effective RMD holder in the supported post-election year. An actual RMD debit can re-use the same original reference while the one attribution row identifies SPOUSE as taxable recipient. The same contract works in reverse. Effective ownership alone produces no RMD, payment, cash or income. The proof is scoped to that original debit and year. The current engine still creates and consumes its existing ordinary rows.

## 11. Historical persistence reuse

The retained income serializer uses the existing row, existing account index, and exactly one additional optional field. Its strict reader rejects unrecognized treatment versions, as well as null or non-name marker values when present. Ordinary rows omit the field. Contextual reconstruction validates the decoded reference, kind and purpose, original identity, participant facts, recipient, year, monetary amount and the matching ownership disposition. The test proves exact row reserialization and rebinds the same original account identity. The normal projection history reader and ordinary restore paths refuse marked rows absent a separately certified applied scope. No whole applied projection history is publishable yet.

## 12. New evidence only where necessary

The new marker labels the existing debit as authority for a specific recipient treatment. There is no separate occurrence, amount, cash event or duplicate source. The allocator's positive balance delta stays the authoritative debit at execution. The next phase must bind marked rows to existing funded opening/closing and source evidence, and must avoid accepting the retained row as independent proof of its own debit. Existing checksums bind bytes and detect alterations. They do not establish eligibility. The caller for applied restore must provide validated frozen ownership and a verified source debit, and reject unresolved amount/source membership. Existing R2a1 planning markers and versions continue identifying only selected treatment.

## 13. Legacy result preservation

**EXISTING_BEHAVIOR_PRESERVED.** Ordinary runtime constructors and retained row structure keep original fields and omissions. No migration, new legacy calculation version, per-account field or history mode was introduced. Previously accepted amount, hashes, UUIDs, roles and readback semantics retain their identity. Existing controlled Arkansas source rules remain in place. Snapshots with a prospective Owner or Beneficiary choice remain explicitly pending. They cannot carry a newly marked receipt and be accepted by the current read/write boundary.

## 14. Unsupported and negative cases

The focused tests covered both death directions, living originals, absent mandatory-history clearance, before-election years, unknown account identities, unsupported employer category, pending beneficiary mode, and original owner versus effective recipient. They verified actual allocator debit amount, same original account reference and no extra withdrawal, a changed year, forged owner without proof, unrelated equal-name source, wrong amount, inappropriate Roth kind and reuse of a successor row as a debit. Retained row tests reject changed recipient, date, index graph and amount, unknown marker version, ordinary restore of a marked row, and an unapplied snapshot containing the row. Existing R1 tests continue covering duplicate identity, contradictory participant metadata and unsupported retirement categories.

## 15. RMD and Roth financial activation boundary

There are no financial callers of `resolveForPlanning`, `forSuccessorRmd` or the contextual retained restore. No financial amount is produced by the planning convention. No Roth source permission or stop-policy change is granted. No existing tax or state classifications are relaxed. The next applied route must independently protect the separate living-original-owner set in both fixed and target capacity/execution. Analyzers, pending results and state proof paths were left unchanged.

## 16. Traditional 401(k) future compatibility

**DESIGNED_NOT_IMPLEMENTED.** The plan setting still covers traditional 401(k) proposals as previously disclosed. Keep inherited employer obligations and the IRA membership group separate. A reviewed own-IRA rollover would require payout eligibility, a valid effective date, separately settled mandatory amounts, pretax source and supported receiving classification, and transition balance evidence. Only then can the position use the verified IRA treatment in the common adapter. The current personal IRA marker explicitly cannot stand in for an employer rollover. The held-as-beneficiary employer path needs its own approved distribution schedule. This remains an active follow-on. [26 CFR §1.402(c)-2](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-402c-2.html)

## 17. Before and after financial comparisons

The existing synthetic probe reproduced eleven five-year retained snapshots and eleven characterization variants with identical transcripts, including both alive, both death directions, living owner RMD, initial Florida/MI/AR, fixed and targeted Roth, Federal/Medicare, spending and cash settlement, ending accounts, estate and restore. SHA-256 of both probe transcripts is `05BC3D18C9975B6BBFE8D3157BC066F2C0803362108F105922FB4B8F1FC1F656`. Eight prospective Owner/Beneficiary configurations matched their unchanged legacy ledgers. No production projection used the new marker.

Independent future acceptance figures remain $369,000 ÷ 24.6 = **$15,000.00** and $366,000 ÷ 24.6 = **$14,878.05** for the forward case. The reverse already-RMD-eligible survivor case uses $349,042.19 ÷ 22.9 = **$15,242.02** at age 77. These figures are future oracles, and no tested RMD financial result was altered to match them.

## 18. Focused test results

Java 25, IntelliJ bundled Maven, project-local Maven repository, `-o -B -Dtest=… test`. Test selectors and logs are in the ignored work directory. Focused selectors cover attribution, account identity, R1/R2a1, RMD and withdrawal, tax/funding/Medicare, Roth, existing state and proof cases, retained codecs, history, UI and PDF. No exhaustive, benchmark or longevity search was run.

| Verification | Passed | Failed | Errors | Skipped | Exit |
|---|---:|---:|---:|---:|---:|
| Before-change focused baseline | 523 | 0 | 0 | 2 | 0 |
| New scoped attribution, resolver and retained-row suites | 57 | 0 | 0 | 0 | 0 |
| Final focused run including additional codec suites | 571 | 0 | 0 | 2 | 0 |
| Synthetic legacy comparison | 11 snapshots and 11 variants | 0 | 0 | 0 | 0 |
| Pending configuration comparison | 8 | 0 | 0 | 0 | 0 |

The two Maven skips are the existing optional interactive wizard preview tests. Both Git whitespace checks passed.

## 19. Remaining risks or blockers

**BLOCKED_PENDING_APPROVAL for actual execution.** The minimum row contract is viable. Applied execution still requires a verified source for mandatory clearance, reviewed survivor age/table and earlier balance rules, exact live debit agreement, state/route admission and applied publication. Validating one row with its expected debit is not sufficient to publish an entire claimed applied projection. Annual closing/cash checks and the independent RMD obligation must reconcile, including split debits, duplicate identities and use of funds. Unknown or inconsistent evidence must result in an explicit refusal. No broad history rewrite is required by what was proven here.

## 20. R2a2-A2 exact integration prerequisites

1. At the admitted deterministic boundary, freeze the plan choice and prove earlier/election-year mandatory clearance for the bounded personal IRA domain. Prepare the annual `resolveForPlanning` arguments once, including original ordered references. Gate the admitted Florida Basic scenario distinctly from analyzer and pending routes.
2. Bind the resulting `AnnualRetirementOwnership` to one ordered member set and verify actual prior December 31 positions once. Reuse the existing owner divisor math and allocation order. The calculator and `allocateHouseholdRmds` use the identical set for sizing and real debit. Distinguish absent entry, unsupported obligation, and actual funding shortfall.
3. Preserve the existing account and RMD debit row. Use `forSuccessorRmd` only on the allocator's dated real debit evidence and replace its ordinary attribution at that boundary. Never append a second receipt. Apply Federal cash and income once and retain the existing funding and Medicare-aware passes, with the same immutable context.
4. Carry the independently verified applied identity and outstanding-obligation evidence into existing accepted snapshot assembly. Admit the marked row only there and use `Income.restoreSuccessorRmd` with the reconstructed verified ownership and debit context. Reconcile source index, year, gross and taxable amounts, annual balances and cash totals before accepting or displaying applied history. Verify Save/Open/Save As and Current/Baseline/Swap with the existing UUIDs and component checks.
5. Preserve living-original-owner-only conversion safeguards, existing state/analyzer gates, last-success/cancellation and unchanged legacy paths. Defer other event kinds, death-year residuals, beneficiary and employer rules.

## 21. Explicit user approval decisions

- Approve activation of the bounded IRA-only Florida Basic deterministic path using the already approved common planning convention. Set exact prerequisites for establishing mandatory distribution clearance and the supported owner table. No account-election UI is needed.
- Approve the applied snapshot identity and whole-year source/debit proof before publication. Reuse the newly verified row contract and the existing component reader. Keep pending and legacy history distinguishable.
- Review the live-owner Roth and reached-source refusal integration within the existing solver. Proceed with separate review for 401(k) rollover, beneficiary schedules, state expansion and any solver-policy change.

This phase stops at the reuse and convention foundation. R2a2-A2 is a separate financial implementation gate.
