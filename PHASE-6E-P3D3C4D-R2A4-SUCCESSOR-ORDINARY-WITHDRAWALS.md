# Phase 6E-P3D3C4D-R2a4 — Florida successor ordinary withdrawals

## Scope and checkpoint

Florida Basic deterministic projections use the existing spending and tax-funding paths for qualifying spouse-inherited traditional IRAs and 401(k)s. Own-IRA treatment, qualifying direct rollover, beneficiary IRA treatment and beneficiary employer-plan treatment retain their separate admission and RMD rules. The plan-level inheritance election determines which policy applies.

Local `main`, `origin/main`, and GitHub's live `refs/heads/main` matched `43f4dda9dd544bba58df5e95d401063291ebe645`, **Complete Arkansas groundwork and Florida spousal inheritance RMD foundation**. The worktree had one pre-existing edit in the R2a3-B report, adding `exio` below its exact-files heading. That edit was preserved. The index was empty. All fixtures use synthetic data.

The implementation follows the prior [minimal-change audit](PHASE-6E-P3D3C4D0R-RMD-ROTH-MINIMAL-CHANGE-AUDIT.md), [ownership foundation](PHASE-6E-P3D3C4D-R1-SURVIVOR-OWNERSHIP-FOUNDATION.md), [unified assumption](PHASE-6E-P3D3C4D-R2A1-UNIFIED-SPOUSAL-INHERITANCE.md), [attribution reuse](PHASE-6E-P3D3C4D-R2A2A1-ATTRIBUTION-REUSE.md), [owner IRA](PHASE-6E-P3D3C4D-R2A2A2-OWNER-IRA-RMD-EXECUTION.md), [owner rollover](PHASE-6E-P3D3C4D-R2A2B-401K-SPOUSAL-ROLLOVER.md), [beneficiary IRA](PHASE-6E-P3D3C4D-R2A3A-SPOUSE-BENEFICIARY-IRA-RMD.md), and [beneficiary 401(k)](PHASE-6E-P3D3C4D-R2A3B-401K-BENEFICIARY-RMD.md) reports.

## Federal rules and admission

| Original position and treatment | Authorized annual draw |
| --- | --- |
| Traditional IRA under effective own-IRA treatment | Owner IRA payout after the inherited position enters the survivor's own-IRA treatment. |
| Traditional 401(k) under effective direct rollover | Owner IRA payout from the same position after the approved pretax rollover takes effect. The rollover creates zero household cash and zero current ordinary income. Required amounts remain excluded from rollover. |
| Traditional IRA retained by the spouse as beneficiary | Death-beneficiary payout in an eligible modeled beneficiary year, including a year when the applicable minimum is zero. |
| Traditional 401(k) retained by the spouse as beneficiary | Death-beneficiary payout from the same employer plan under the existing beneficiary method and the approved additional-payout assumption. Its own mandatory amount remains a separate obligation. |

A qualifying sole spouse with unrestricted withdrawal rights may elect own-IRA treatment. The spouse then becomes the owner for §72(t). A fully pretax beneficiary receipt after death enters the recipient's gross income in the distribution year. Section 72(t)(2)(A)(ii) exempts payments made to a beneficiary on or after death from the 10% additional tax. An own-IRA draw after an election or rollover uses the owner's own age under §72(t)(2)(A)(i). The approved beneficiary minimum-distribution method establishes the minimum and its commencement, independently of the actual payout selected. [IRC §72](https://www.govinfo.gov/content/pkg/USCODE-2024-title26/pdf/USCODE-2024-title26-subtitleA-chap1-subchapB-partII-sec72.pdf) · [IRS Publication 590-B](https://www.irs.gov/publications/p590b) · [26 CFR §1.408-8](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-408-8.html) · [IRS beneficiary RMD guidance](https://www.irs.gov/retirement-plans/required-minimum-distributions-for-ira-beneficiaries)

The model admits an own-IRA debit only when the survivor has reached age 59½ by January 1 of the applicable successor year. Annual funding has no transaction date that can place a debit on a particular side of a later half-birthday. A selected debit in that boundary year or an earlier year produces a typed refusal. The reviewed model has no general exception or §72(t) additional-tax calculation for these owner IRA distributions. Beneficiary withdrawals retain the death exception regardless of the survivor's age. [IRS Form 5329 instructions](https://www.irs.gov/instructions/i5329) · [IRS IRA distribution FAQ](https://www.irs.gov/retirement-plans/retirement-plans-faqs-regarding-iras-distributions-withdrawals)

The discretionary 401(k) assumption is that plan terms allow additional beneficiary payouts on the accepted schedule. Actual employer terms can restrict payout options or require faster distribution. The federal minimum still applies to each plan. The separate owner rollover scope still requires an eligible rollover under §402(c), with positive unresolved mandatory amounts triggering the existing refusal. Federal income tax is settled on an annual household basis. Actual eligible rollover distributions paid in cash may be subject to withholding, and payment-date withholding and refund timing are outside the current annual cash convention. [IRC §401](https://www.govinfo.gov/content/pkg/USCODE-2024-title26/html/USCODE-2024-title26-subtitleA-chap1-subchapD-partI-subpartA-sec401.htm) · [IRC §402](https://www.govinfo.gov/content/pkg/USCODE-2024-title26/html/USCODE-2024-title26-subtitleA-chap1-subchapD-partI-subpartA-sec402.htm) · [IRS Publication 575](https://www.irs.gov/publications/p575) · [IRS plan distribution rules](https://www.irs.gov/retirement-plans/plan-sponsor/401k-resource-guide-plan-sponsors-general-distribution-rules) · [26 CFR §1.401(a)(9)-3](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-3.html) · [26 CFR §1.401(a)(9)-5](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-401a9-5.html) · [26 CFR §1.402(c)-2](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-402c-2.html)

## Execution and retained evidence

- The earlier `validateDiscretionarySources` checks ran after the spending allocation and the settled tax estimate and refused all selected deceased-original retirement debits. The Florida route now uses the annual membership to authorize only effective successor positions. Pre-effective draws, incompatible basis and history, unresolved mandatory distributions, and contradicting plan facts still fail admission. A higher-priority selected source that requires unsupported early-distribution treatment produces a refusal.
- `ProjectedWithdrawalAllocator` still follows the configured order, spends retained and portfolio cash according to the existing rules, and continues across eligible positive balances. It pays RMDs first. An extra withdrawal uses `ORDINARY_RETIREMENT_WITHDRAWAL` with `CASH_FLOW` or `TAX_FUNDING` and enters household receipts once. Beneficiary IRA membership remains separate from owner IRA membership and each inherited employer plan pays its own RMD.
- The ordinary authorization captures the original account reference and pretax debit, along with the survivor recipient, effective treatment, calendar year and purpose. The ordinary owner-match guard still applies to all account receipts without successor proof. Gross equals the modeled taxable amount. This represents economic access to one original position and charges tax to the effective recipient.
- The tax solver estimates funding against the same opening portfolio for every trial. Only the settled estimate is authorized. The actual funding allocation runs once for a completed year and reconciles its ordered original references and gross amounts to the tax receipts. Taxes use the existing aggregated tax-deferred distribution totals, including any gross-up, and the existing taxable Social Security formula. The annual funding convergence tolerance and bound are kept.
- Medicare uses the existing filing status, coverage and IRMAA calculation. For a year with an ordinary successor receipt, the funded tax result supplies final modeled MAGI. A premium change triggers the existing annual reevaluation from the same opening assets with the same annual ownership, up to a limit derived from the available brackets. A run with unstable premium or tax funding fails before publication. This uses the application's modeled current-year IRMAA convention. Actual two-year statutory administration and appeals are outside this phase.
- Schema 8 adds one optional `successorOrdinaryTreatment` marker to the existing retained income row and uses `FL-BASIC-SPOUSAL-SUCCESSOR-ORDINARY-WITHDRAWALS-V1`. The existing separate RMD marker, rollover lineage and per-plan obligations remain in the same retained graph. The complete snapshot replays the appropriate policy from the frozen inputs and matches every annual field, including balances, taxes, Medicare MAGI, settlement and estate. Rebuilding checksums provides no authority to change a receipt. Schemas 4–7 replay under their recorded RMD-only policies and retain their recorded provenance.
- Results Summary and PDF use the frozen disclosure. Save, Save As, Open, History, Current/Baseline and two-way Swap reuse the existing snapshot and component paths. The controller replaces the accepted result only on successful completion. Existing cancellation checks also run before each annual Medicare retry.

## Independent oracles

| Fixture | Expected execution |
| --- | --- |
| A — own IRA, spouse dies first | The survivor turns 75 in 2035. The Uniform Lifetime divisor is 24.6, so a \$492,000 prior balance gives a \$20,000 RMD. With \$40,000 additional spending, the original account has \$432,000 before tax funding and \$60,000 initial ordinary taxable distributions. The starting balance differs from the illustrative \$500,000 to give the exact statutory RMD with a valid divisor. |
| B — beneficiary IRA in deferral, spouse dies first | \$500,000 opening, required amount zero, \$30,000 ordinary draw and \$470,000 before tax funding. The original account funds additional taxes separately. |
| C — beneficiary 401(k), primary dies first | The surviving spouse turns 73 in 2035. Single Life divisor 16.4 gives \$246,000 ÷ 16.4 = \$15,000 from the original plan. \$20,000 ordinary spending gives \$35,000 total distributions and \$211,000 remaining before tax funding. |
| D — effective direct rollover, primary dies first | A younger decedent's \$500,000 eligible rollover starts the following January 1 before commencement of any beneficiary minimum. The \$10,000 ordinary IRA draw creates \$10,000 taxable income and leaves \$490,000 in the original employer position with zero federal liability and one original asset. |

The C fixture uses zero growth, \$24,000 survivor Social Security, \$24,000 pension, \$80,565.20 spending, and \$2,434.80 Medicare. The \$20,400 taxable Social Security cap yields \$79,400 MAGI before gross-up. After the \$16,100 deduction the initial federal tax is \$8,638. With a 22% marginal rate, solving `x = 8638 + 0.22x` gives \$11,074.36 federal funding, about \$90,474.36 final MAGI and \$199,925.64 ending 401(k). The survivor pays both expenses and premiums with zero ending retained cash under the existing tolerance. A companion \$95,565.20 expense case crosses the \$109,000 IRMAA boundary during funding and settles at \$3,583.20 Medicare, \$36,148.40 discretionary spending withdrawals and \$15,629.04 federal funding.

Both death directions are exercised. The mixed-position case exhausts taxable and traditional IRA positions in strategy order before drawing from a deferred inherited 401(k). It proves zero beneficiary RMD for both account families and separate ordinary receipts. A separate case checks exact exhaustion of an inherited own IRA during a year with zero RMD. RMD ordering, positive mandatory employer obligations and the debit limits retain the previous tests.

## Files changed

Paths below are relative to the indicated Java source root and the `com/daviddunn/retirementplanner` package.

| Root | Files |
| --- | --- |
| `src/main/java` | `app/projection/CompleteProjectionSnapshot.java`, `app/projection/SpousalInheritancePlanningEvidence.java`, `app/socialsecurity/results/RetainedProjectionSnapshot.java`, `domain/income/IncomeSourceAttribution.java`, `domain/projection/ProjectionEngine.java`, `domain/projection/SpousalIraRmdPolicy.java`, `domain/rmd/SpousalIraRmdMembership.java`, `domain/rmd/SpousalIraRmdUnavailable.java`, `domain/tax/TaxFundingCalculator.java`, `domain/withdrawal/WithdrawalBreakdown.java`, `ui/controller/ApplicationController.java` |
| `src/test/java` | `domain/projection/SpousalSuccessorOrdinaryWithdrawalTest.java`, `persistence/ProjectionSnapshotPersistenceTest.java`, `ui/charts/ProjectionPdfExportTest.java` |
| Repository root | `PHASE-6E-P3D3C4D-R2A4-SUCCESSOR-ORDINARY-WITHDRAWALS.md` |

## Classification and verification

- **IMPLEMENTED_AND_TESTED** — Florida Basic deterministic ordinary successor receipts for spending and tax funding, original debit and beneficiary/owner recipient attribution, exact-age admission, Medicare settlement, retained policy binding and replay. See Maven totals below.
- **EXISTING_BEHAVIOR_PRESERVED** — Original account order and constraints, RMD sizing and grouping, Roth eligibility and configured stop rules, tax and Social Security formulas, prior policy identities, normal state and analyzer routes, and the last accepted result on a failed or canceled rerun. There is no new conversion authority for a successor position.
- **UNSUPPORTED_OR_REFUSED** — Selected sources before effective successor treatment, unsupported early own-IRA debits, unsupported basis or employer terms, inconsistent sources and annual evidence, unresolved RMDs, insufficient funds and nonconvergent funding. The earlier Florida RMD policy limits, including pre-RMD death chronology, still apply.
- **DESIGNED_NOT_IMPLEMENTED** — Broader states and analysis routes, new RMD methods, alternative elections and beneficiary arrangements, and the generalized additional-tax engine.
- **BLOCKED_PENDING_APPROVAL** — R2b, R3 and R4 remain separate approval gates. Financial scope stays at Florida Basic deterministic.

IntelliJ bundled Maven ran offline with Temurin Java 25.0.4.1 and the workspace-local `.codex-m2/repository`. The focused run covered ownership, the four approved RMD treatments, the new successor fixtures, allocator and income attribution, federal tax, taxable Social Security, Medicare and IRMAA, cash settlement, Roth, Michigan, Arkansas funding and provenance, persistence, and JavaFX summary and PDF workflows. The opt-in Medicare audit was skipped.

| Run | Passed | Failures | Errors | Skipped | Maven exit |
| --- | ---: | ---: | ---: | ---: | ---: |
| Targeted financial, retained and UI verification | 576 | 0 | 0 | 1 | 0 |
| Broad regression with long-running exclusions | 5,696 | 4 | 7 | 35 | 1 |
| Unmodified checkpoint replay of all three failing classes | 34 | 4 | 7 | 0 | 1 |

The first broad run was interrupted after reaching a state-comparison fixture that launched repeated full mortality analyses. The revised selector excludes that fixture as well as benchmark, performance, capacity, and other long-running longevity and Monte Carlo classes. Selectors and raw transcripts are in `target/r2a4-focused-selector.txt`, `target/r2a4-suite-selector.txt`, `target/r2a4-verification.log`, `target/r2a4-suite-final.log`, and `target/r2a4-baseline-all.log`.

The broad run found the following failing groups.

- `ProductionExhaustiveInterningTest` reported one Alabama error. The pension's DB/DC classification is `UNKNOWN` and materially affects tax in that synthetic fixture.
- `AssumptionsLayoutTest` reported six errors because the expected controls omit the existing Spousal Retirement Account Inheritance section.
- `WizardFinancialEquivalenceTest` reported four failures because the independently constructed input fixtures omit the existing wizard's default spousal inheritance election.

All four failures and all seven errors reproduced in a detached source archive of the verified checkpoint. The broad run includes eight passing successor oracles and the focused run includes passing Save/Open, two-way Swap, cancellation, forgery rejection, Results Summary and PDF checks. The broad suite remains red. Resolving the existing checkpoint groups requires separate scope approval.

`git diff --check` and `git diff --cached --check` both exited 0. The index is empty and all commits and remote refs were preserved.
