# Phase 6E-P3D3C4D-R1 — Survivor effective-ownership foundation

## 1. Executive summary

R1 adds an immutable annual ownership view and a pure resolver. A qualifying account can identify the surviving spouse as its effective modeled RMD owner only under explicit, account-scoped planning evidence. The account reference and original owner stay intact. Every unsupported disposition carries a typed reason and an empty effective owner. The resolver has no production caller.

The before-and-after comparison matched all ten five-year financial snapshots, their frozen input and annual evidence digests, and all eleven smaller characterization variants. The test suite remained green with 32 new structural cases. Michigan's previously demonstrated premature RMD and Roth cutoff are **EXISTING_BEHAVIOR**. R1 leaves that correction to R2a and R3.

## 2. Git baseline and workspace preservation

Opened on `main` at `2bedb55e99fb48aa31225e9c5549c842d77ebb2d`, zero ahead and behind the local `origin/main` reference. The index was empty. The working tree had 32 modified tracked files and 24 untracked files. Byte hashes of all 56 paths matched after R1. Five new untracked files comprise the R1 changes. No files were staged, committed or pushed. All fixtures are synthetic and build/probe artifacts are under ignored `target/phase6e-p3d3c4d-r1/`. No private plan was opened.

The code confirmed the D0R audit's distinct generic and controlled/lifetime owner selection, original-owner account filtering and schedule behavior. R1's design follows D0R's common-engine correction boundary. The earlier Arkansas succession design was scoped around keeping Arkansas beyond-RMD receipts unavailable. R1 can represent later effective ownership for a structurally qualifying IRA, while current Arkansas admission and all execution gates remain unchanged. [D0R audit](PHASE-6E-P3D3C4D0R-RMD-ROTH-MINIMAL-CHANGE-AUDIT.md) · [Succession design](PHASE-6E-P3D3C4C-SPOUSAL-SUCCESSION-DESIGN.md) · [Pooling audit](PHASE-6E-P3D3C4B-HOUSEHOLD-COMMINGLING-AUDIT.md) · [Roth eligibility architecture](PHASE-13I-A-ROTH-CONVERSION-ELIGIBILITY.md) · [Survivor architecture](SURVIVOR-BENEFIT-AUDIT.md) · [Mortality architecture](MONTE-CARLO-MORTALITY-COMMIT-E.md)

## 3. Actual new and modified source files

| File | Role |
|---|---|
| [AnnualRetirementOwnership.java](src/main/java/com/daviddunn/retirementplanner/domain/retirementownership/AnnualRetirementOwnership.java) | Calendar-year view of frozen death context, birth dates, living owners and ordered account dispositions. |
| [RetirementOwnershipResolver.java](src/main/java/com/daviddunn/retirementplanner/domain/retirementownership/RetirementOwnershipResolver.java) | Pure resolution and validation with a nested, run-only spouse-as-owner planning assumption. |
| [RetirementOwnershipDisposition.java](src/main/java/com/daviddunn/retirementplanner/domain/retirementownership/RetirementOwnershipDisposition.java) | Original reference, effective RMD owner where qualified, status, treatment, date and reason. |
| [RetirementOwnershipResolverTest.java](src/test/java/com/daviddunn/retirementplanner/domain/retirementownership/RetirementOwnershipResolverTest.java) | Synthetic account, evidence, chronology and immutability tests. |
| This report | Design, verification and next approval. |

All source additions are within the new `domain.retirementownership` package and its matching test package. Zero pre-existing file modifications and zero changes outside the authorized scope.

## 4. Immutable domain model

`RetirementOwnershipResolver.resolve` takes a `Year`, projection opening, `EffectiveHouseholdDeathView`, birth dates by household slot, existing `RmdRules`, ordered original account references and optional per-account planning assumptions supplied as a list. The annual view holds immutable copies of its maps and owner set, preserves caller account order, and exposes the existing immutable death view. Each disposition has its own year and applies to that calendar year. The date attached to a qualifying successor describes when the treatment began. It does not extend the annual result to other years.

Asserted facts are frozen via defensive copy. No mutable account is exposed and no monetary value is captured. `MODELED_SPOUSE_AS_OWNER_NEXT_YEAR_V1` is an in-memory treatment enum on positive dispositions. It is neither a persisted calculation identity nor a workflow capability. Original-owner disposition describes membership only. It does not certify RMD start, payment, employer exceptions or a future Roth operation.

## 5. Eligibility and typed refusal rules

A successor can qualify only on an inactive original owner's personal traditional IRA and a matching, explicit assumption. The sole living member is the other spouse. The assumption binds the original reference, other individual slot, survivor DOB, modeled death date and planned effective date. It must affirm all five facts independently.

| Required affirmation | Refusal if absent |
|---|---|
| Direct sole-spouse beneficiary | `SOLE_DIRECT_SPOUSE_BENEFICIARY_UNVERIFIED` |
| Unlimited withdrawal rights | `UNLIMITED_WITHDRAWAL_RIGHTS_UNVERIFIED` |
| Fully pretax account and zero relevant basis | `FULLY_PRETAX_AND_NO_RELEVANT_BASIS_UNVERIFIED` |
| Compatible personal IRA history | `ACCOUNT_HISTORY_UNVERIFIED_OR_CONFLICTING` |
| No unresolved mandatory distribution for the proposed election or earlier periods | `MANDATORY_DISTRIBUTION_STATUS_UNRESOLVED` |

Missing assumptions refuse inactive eligible account types with `MISSING_SPOUSE_AS_OWNER_ASSUMPTION`. Wrong account classes, multiple modeled deaths, no surviving spouse and date/cohort issues have separate reasons. A refused or inapplicable account supplies no effective owner or authorized effective date. Absent mandatory arguments, contradictory bindings, unrelated accounts, duplicate identities and competing assumptions are structural validation errors. The resolver checks reference and date consistency and the presence of affirmations. The caller must substantiate the underlying legal and historical facts when eventually approved to build an assumption. [Treasury Regulation §1.408-8(c)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-408-8.html)

## 6. Original versus effective ownership

An original owner is read from the existing `AccountReference`. A modeled effective owner is the individual RMD grouping holder for the specified year. The household's economic access to the same positions remains under the existing projected portfolio and spending contract. A later transaction needs its own actual payee independently verified for that year's source and debit. No receipt, tax recipient, account transfer, debit or Roth permission is produced by R1.

## 7. Chronology validation

Living owners and modeled death dates come from `EffectiveHouseholdDeathView`, with death effective January 1. The birth-date roster must match the modeled members. Contradictory death evidence cannot qualify. The planned election date must be exactly January 1 following death. A correct election input during the death year yields `BEFORE_EFFECTIVE_YEAR`. Opening on the modeled death date is structurally admissible for a later year with the required evidence. Opening after that date, even in the death year, is outside scope. A configured second death is outside this initial model, including the intervening survivor period.

Using existing start-age rules, the resolver checks that death precedes the decedent's first distribution calendar year. It does not compute a divisor or distribution amount. Both cohorts must have a supported rule. The reserved 1959 cohort and the legacy integer approximation of 70½ are outside successor scope. The structural disposition does not prove an election-year or prior mandatory payment. No successor membership is issued for the actual year of death. [EffectiveHouseholdDeathView.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/EffectiveHouseholdDeathView.java) · [Dated RMD rules](src/main/resources/rules/government-rules-2026.json) · [26 CFR §1.401(a)(9)-2](https://www.ecfr.gov/current/title-26/chapter-I/subchapter-A/part-1/subject-group-ECFR6f8c3724b50e44d/section-1.401(a)(9)-2)

## 8. Account identity invariants

The account key uses the existing opaque reference equality for an original account object. A newly captured reference to that same object resolves to the same position. Two different accounts with identical labels, owner or balance remain separate. Changes to the underlying projected or live balance cannot change grouping. The same assumption cannot address two positions, and duplicate account occurrences and repeated designations are rejected. An equal identity with inconsistent separately frozen participant or inherited metadata is rejected. The assumed survivor must match an opposite household slot and DOB in the frozen roster. Labels supply no proof of identity. There is no durable account ID and an independently reconstructed account object must be validated within its own run. [AccountReference in IncomeSourceAttribution.java](src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java:273)

## 9. Unsupported source categories

Initial successor authority is limited to personal `TRADITIONAL_IRA` without contradictory participant or inheritance metadata. `ROLLOVER_IRA` is deferred until separate qualifying lineage is supported. Traditional 401(k), traditional 403(b), Roth IRA, Roth 401(k), inherited traditional and Roth IRA, and taxable/cash accounts yield no successor RMD owner. A position without ordinary owner-RMD membership can be marked `NOT_APPLICABLE`, or refused when it presents an unsupported succession claim. The existing account taxonomy does not certify separate SEP/SIMPLE or other employer-linked IRA treatment. Keep those histories outside the supported evidence. State-specific exclusions and beneficiary distributions remain separate.

## 10. Future RMD integration contract

Resolve once per modeled year from the same frozen opening and household facts. R2a would resolve each authorized reference to exactly one original position, preserve position order and derive owner groups and the effective owner's DOB from that view. The existing calculators would use the actual prior December 31 values, verified table and rules for that owner. The allocator would consume the **same membership view**, locate and debit the original available accounts, and reconcile cash to actual debits with no change to balance authority. Any future receipt records the original reference, applicable obligation identity, actual recipient and amount, after execution. The Medicare-aware pass would reuse the immutable view and independent opening ledger.

Changing only the owner-eligibility filter is insufficient. It can remove the premature RMD while excluding successor balances or letting the debit allocator use a different set. Keep death-year mandatory amounts, genuinely unpaid historical RMDs, opening snapshots, and insufficient current funds distinct. R2b requires separate admission for mandatory death-year payments. [ProjectionEngine.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java) · [OwnerRmdService.java](src/main/java/com/daviddunn/retirementplanner/domain/rmd/OwnerRmdService.java) · [ProjectedWithdrawalAllocator.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectedWithdrawalAllocator.java)

## 11. Future Roth integration contract

R3 would consume the certified annual obligation, including required amounts already satisfied where applicable, through an independently approved stop rule. R4 would derive a separate source authorization after settling mandatory amounts. It must prove executability from the original source, actual recipient, a valid Roth destination owned by the survivor, taxable executed dollars and exact funding. Fixed and targeted conversion must use the same real source/debit facts in pricing and final settlement. RMD membership confers no conversion right, and required amounts cannot be converted. The existing converter and allocation checks still require matching original ownership. [ScheduledRothConversionPolicy.java](src/main/java/com/daviddunn/retirementplanner/domain/roth/ScheduledRothConversionPolicy.java) · [RothConversionAllocation.java](src/main/java/com/daviddunn/retirementplanner/domain/roth/RothConversionAllocation.java) · [26 CFR §1.408-8(b)(3)](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-408-8.html)

## 12. Production activation boundaries

There are no production references from outside the new package, including from the annual engine, controller, user settings, serializer or analyzer admission. All workflows retain their current capabilities. The prospective entry route is a separately approved deterministic projection. Florida offers an isolated federal control. Michigan and Pennsylvania require their own source classification and admission. Arkansas controlled succession stays deferred. Fixed Monte Carlo, lifetime/mortality, integrated Social Security and paired analyses need independent capability approval before using the context. Current/Baseline and historical readers have no new policy or payload to activate. Source-reference inspection and the unchanged projection and archive tests verify this boundary.

## 13. Existing deterministic financial baselines

The comparison ran a synthetic Java probe before and after the additions, using existing production projectors and snapshot capture. Ten five-year fixtures covered Michigan both alive, primary death, spouse death, death after a living RMD, and targeted versus fixed conversion, Florida fixed and targeted, Arkansas before/after the first modeled RMD, and a Michigan lifetime control. The canonical annual digests cover account balance rows, withdrawals, actual income and sources, RMD, taxable income, federal/state tax, Medicare, Roth, retained cash, and estate. For each snapshot capture, restore and recapture matched. The probe also compared each frozen input digest, verified complete Arkansas capture, and replayed eleven D0R variants including the typed Pennsylvania refusal. The full before/after probe transcripts had identical SHA-256 values. Existing focused tests covered premium-aware annual repricing and historical save/load and projection identity.

| Case | R1 before and after, EXISTING_BEHAVIOR |
|---|---|
| Michigan, both living | Legitimate $10,000 primary RMD in 2035 and household automatic stop. |
| Michigan, primary dies before RMD | $1,000 conversion in 2034. Fictional $10,000 original-primary RMD, $10,000 AGI and zero Roth conversion in 2035. Current state tax $174.25 in this synthetic fixture. |
| Michigan, younger spouse dies | Same retained projection baselines and original account lineage across runs. |
| Florida generic versus lifetime 12% target | Single year generic $56,500 conversion plus $10,000 RMD. Lifetime $66,500 conversion with zero RMD. Both priced $50,400 federal taxable income in the synthetic control. |
| Arkansas and Michigan lifetime | No RMD on the primary's would-be 75th birthday in the control. The later survivor RMD pool does not include the original primary IRA under existing execution. |

The D0R corrected independent oracle, $14,878.05 on $366,000 divided by 24.6 in 2037 when three $1,000 conversions occur, stays a future acceptance fixture. R1 tests do not assert it as current output or promote defective current behavior into a permanent correctness golden. [Annual evidence](src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java:543) · [Persistence tests](src/test/java/com/daviddunn/retirementplanner/persistence/ProjectionSnapshotPersistenceTest.java)

## 14. Test results

Temurin Java 25.0.4.1 and IntelliJ bundled Maven, using the prescribed workspace-local Maven cache with `-o -B -Dtest=... test`. Focused selectors and logs remain under the ignored phase directory. No full Monte Carlo, exhaustive longevity, benchmark or capacity suite was run.

| Run | Passed | Failures | Errors | Skips | Exit |
|---|---:|---:|---:|---:|---:|
| Pre-change focused suites, 34 classes | 299 | 0 | 0 | 0 | 0 |
| Intermediate structural suite after correcting the fixture enum | 31 | 0 | 0 | 0 | 0 |
| Final focused run, same existing 299 plus 32 new structural cases | 331 | 0 | 0 | 0 | 0 |
| Standalone comparison before and after, per run | 10 full snapshots + 11 variants | 0 | 0 | 0 | 0 |

The first structural compile attempt referenced a nonexistent beneficiary enum in the new test fixture. It exited 1 before test execution. The fixture now uses the existing `CHILD` enum. No existing tests or financial expectations changed. The new suite covers both succession directions, dates and roster, each evidence omission, unsupported classes, no survivor, multiple scheduled deaths, identity/metadata conflicts, duplicate references and immutability.

Both `git diff --check` and `git diff --cached --check` passed. SHA-256 verified all opening changed and untracked files. The new files have no trailing whitespace. Existing JavaFX/native-access warnings appeared on the successful scoped test run.

## 15. Performance and immutability considerations

Resolution is linear in the number of original references and assumptions for a fixed rule set, with small account-order maps and reference lookups. Existing production routes incur zero work. No financial search loops, budgets or cancellation behavior were changed. The account key's opaque object identity supports matching without exposing or copying projected balances. Tests mutate caller collections and a synthetic account balance to exercise the isolation. A future implementation must preserve cooperative cancellation and last-success retention across both Medicare passes.

## 16. Remaining legal and modeling uncertainties

R1 accepts an explicit hypothetical affirmation, without checking legal documentation or performing elections. Actual beneficiary designation, basis, an unverified distribution or catch-up obligation, ambiguous cohort, special beneficiary election, later or pre-opening succession, and multiple-death scenarios require additional validated facts or a refusal. Uniform Lifetime versus another table and dates around the required beginning date need the R2/R2b evidence described in D0R. State exclusion eligibility, beneficiary withdrawal pricing, actual Roth eligibility, and any further law changes require separate review. [T.D. 10001](https://www.govinfo.gov/content/pkg/FR-2024-07-19/html/2024-14542.htm) · [IRS Pub. 590-B](https://www.irs.gov/publications/p590b)

## 17. R2a implementation prerequisites

Approve the allowed state and deterministic route, the source of reliably frozen assumption facts, the strict pre-first-distribution-year scope, and the approved birth cohort and table premises. Certify prior balances and membership for each original reference before sizing and debiting a requirement. Define typed refusal for reached sources whose timing, beneficiary classification, state treatment or mandatory history is unresolved. Before R4, explicitly fence legacy deceased-source conversions when they could execute. Require separate proof of unchanged both-alive calculations, exact annual cash and tax settlement, state gate behavior, empty obligations, the two survivor directions and historical compatibility. Adopt new provenance only in its separately reviewed activation stage. No Arkansas succession admission is part of R2a.

## 18. Explicit approval decisions

- **R2a entry.** Approve evidence capture, qualifying personal traditional IRA scope and chronology, deterministic route and state gates, the shared calculation/debit membership interface, provenance, and unsupported-source handling.
- **R2b.** Approve a separate death-year obligation lane, actual payee and pre-opening/previously satisfied evidence.
- **R3.** Approve the annual obligation-based resumption rule or specify a different cutoff. Keep that decision independent of RMD ownership.
- **R4.** Approve successor conversion rights, independently verified source and actual Roth destination, with fixed and targeted settlement reviewed separately.
- **R5.** Approve the subsequent Michigan and cross-state evidence, historical readers, reporting and native acceptance. Arkansas succession remains deferred.

R1 is ready for review and ends at the structural foundation.
