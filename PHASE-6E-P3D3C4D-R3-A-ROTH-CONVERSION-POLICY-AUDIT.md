# R3-A · Roth conversion scheduling and execution audit

## Executive finding and baseline

**CURRENT_IMPLEMENTATION.** The start year already exists and is inclusive. There is no persisted end year. The existing “First Household RMD” setting is route-dependent. Normal deterministic execution blocks the current positive-RMD year and can resume. A lifetime-context evaluation additionally carries a prior-year trigger across years. The existing annual flag does not use participant age. An explicit new permanent policy cannot be represented by silently relabeling the old enum.

**PROPOSED_NOT_IMPLEMENTED.** Reuse the Roth request, editor and scheduler. Add an optional versioned scheduling policy and inclusive end year, preserve the unversioned behavior, and initially admit the new execution only in certified Florida Basic normal deterministic projections. Ready for approval of the staged design below.

Local `main` and `origin/main` showed `43f4dda9dd544bba58df5e95d401063291ebe645`. The accepted R2a4 through R2b-D changes and the earlier R2a3-B report edit were already unstaged. The R2b-D comparison baseline is 5,768 passes, zero failures or errors, and 35 skips. Schema 11 is the newest retained policy. This audit adds only this Markdown report.

## Federal law and application policy

**VERIFIED_CURRENT_LAW.** An IRA RMD cannot be a rollover or conversion contribution. Treasury treats the required amount as distributed first in an applicable calendar year. Employer-plan RMDs are likewise excluded from eligible rollover amounts. A lawful eligible additional distribution may be converted after the applicable requirement is satisfied. No statutory scheduling rule permanently prohibits future conversions merely because RMDs began. Pretax amounts converted to Roth generally enter income. The applicable source, beneficiary treatment, plan terms, and transaction determine eligibility. [IRC §408A](https://uscode.house.gov/us/usc/119-124/t26/s408A/) · [IRC §408](https://uscode.house.gov/us/usc/119-124/t26/s408/) · [IRC §402](https://uscode.house.gov/us/usc/119-124/t26/s402/) · [26 CFR §1.408A-4, Q&A-6](https://www.govinfo.gov/content/pkg/CFR-2025-title26-vol6/xhtml/CFR-2025-title26-vol6-sec1-408A-4.html) · [IRS Publication 590-A](https://www.irs.gov/publications/p590a)

**VERIFIED_CURRENT_LAW.** Sole-spouse beneficiary life-expectancy treatment and treating a qualifying inherited IRA as one's own have different conditions. A spouse may have other legal rollover choices for eligible amounts, but beneficiary status alone supplies no authority for the application to model an own-account Roth conversion. A qualifying direct pretax 401(k)-to-traditional-IRA rollover is distinct from a taxable Roth conversion. R2b-D pays the required distribution first and changes the treatment of the eligible remainder. The 2024 final RMD regulations generally apply from 2025. The proposed spouse-as-employee rules are outside this audit's proposed implementation. [IRC §401(a)(9)](https://uscode.house.gov/us/usc/119-124/t26/s401/) · [Treasury Decision 10001](https://www.irs.gov/irb/2024-33_IRB) · [IRS Publication 590-B](https://www.irs.gov/publications/p590b) · [IRS Announcement 2026-7](https://www.irs.gov/pub/irs-drop/a-26-07.pdf)

## Current inputs, defaults and draft behavior

**CURRENT_IMPLEMENTATION.** A nullable, immutable `RothConversionRequest` is held on `RetirementPlan` and frozen in `RetirementPlanSnapshot`. Regular wizard plans start without a request. Both editors display a disabled, blank year and amount, fixed strategy, one-time frequency and First Household RMD for an absent request. A demo may explicitly configure a request. Explicit disable retains the saved fields. A null strategy in the request constructor resolves to fixed, null frequency is invalid, and null stop rule is accepted by the domain and behaves like Never. The enabled editors require a selection. Audit regression candidates include the legacy null-stop request because the PDF exporter currently calls `getStopRule().toString()` when a request is present.

| Input or behavior | Current | Proposed |
| --- | --- | --- |
| Enable | Checkbox on two existing surfaces, nullable request means disabled | Reuse both controls. Preserve disabled financial default and dormant configuration |
| Start | Integer, at least 1900, inclusive. ONE_TIME runs only at that year | Reuse as explicit calendar start. New-plan draft default first projection year |
| End | Implicit projection horizon for ANNUAL. ONE_TIME ends at its start | Persist inclusive end in explicit new schedules. ONE_TIME uses a single-year window |
| RMD scheduling | `NEVER`, `FIRST_HOUSEHOLD_RMD`, historical null | Preserve legacy values. Add separate versioned Continue, Skip and Permanent choices |
| Frequency | `ONE_TIME`, `ANNUAL` | Preserve. A skipped one-time conversion never shifts to a later year |
| Fixed target | Nonnegative annual dollar amount, limited by executable capacity and actual allocation | Reuse. Keep requested, actual and shortfall distinct |
| Taxable income target | 12%, 22%, 24%, or nonnegative custom taxable income | Reuse existing resolver and gross-up calculation. Separate model review for any discovered financial gap |
| Source, order, caps | No elective conversion-source picker or independent universal cap. Original same-owner eligibility and available balance determine capacity. Primary before spouse, stored account order within each owner | Preserve these gates and order. Keep employer availability separate from schedule |
| Taxes | Existing withdrawal strategy funds tax after conversion, using available household cash and eligible portfolio sources | Preserve solver, ordinary funding access, and tax reporting |

`RothConversionView` and `ResultsSummaryView` maintain draft values, reload accepted fields on cancel and construct a validated immutable request before apply. `MainWindow.applyRothConversionRequest` is the shared transactional boundary. It confirms creation of missing same-owner Roth IRA destinations when appropriate. Save has validation and draft handling, and the dedicated view refuses implicit destination creation on Save. `RothConversionConfiguration.assess/validateTransition/same/disabled` controls availability, equality and disabling. Source assessment distinguishes available, dated, unavailable and legacy-unknown employer accounts. It can allow future scheduling for a zero-balance supported source. Financial execution produces zero from unavailable/depleted sources and continues to other eligible owners. The UI refuses a newly enabled or changed schedule with no supported available present-owner source, preserving a previously saved schedule.

## Actual execution and legacy event

**CURRENT_IMPLEMENTATION.** In `ProjectionEngine`, R2b-D's following-January original-plan payment precedes growth and rollover. The other existing RMD sources are sized from opening evidence or prior December 31, and the regular allocator pays them from the growth-adjusted portfolio. The ordinary spending allocator then draws. The scheduler is evaluated on that remaining portfolio. Fixed conversion uses its request. Targeted conversion invokes existing bracket logic with withdrawal breakdown, actual RMD cash, opening RMD evidence, Social Security, pensions, eligible owners and applicable state context. Conversion executes from remaining legal positions, then taxes and tax-funding withdrawals settle, Medicare can trigger a retry, and household cash and ending balances are retained. Conversion is an internal transfer with taxable attribution. RMD, ordinary spending, and tax-funding debits are separate.

The current-year predicate is exactly `annualHouseholdRmdResult.getTotalRmd().add(openingMandatoryAmount).signum() > 0`. It is based on annual mandatory obligation, including a fully presatisfied opening obligation even when current projected-period payment is zero. It includes the R2b-D January beneficiary payment. The scheduler receives `currentPositive || (lifetimeRun && householdRmdHasOccurred)`. The loop updates the remembered flag after an accepted annual result when the reported annual RMD is positive. That reported aggregate is rounded to cents, unlike the same-year raw sign check. A failed mandatory funding year cannot reach conversion. There is no event inferred merely from attaining RMD age, or from years outside the projection.

| Scenario | Current FIRST_HOUSEHOLD_RMD |
| --- | --- |
| Both below age with no beneficiary requirement | Allows otherwise scheduled eligible conversions |
| Age reached and prior qualifying balance zero | No new stop. If prior balance was positive, intra-year exhaustion does not erase the required amount |
| Current positive RMD, including opening fully presatisfied | Blocks the same year on both route types |
| Positive year followed by zero after exhaustion | Normal deterministic can resume. Lifetime context retains its previous trigger |
| Effective Owner inheritance | Uses actual separately calculated owner and any transition obligations. Source eligibility is separate |
| Retained Beneficiary inheritance | Uses actual beneficiary minimum, including any delayed commencement or post-RBD requirement. Ordinary beneficiary status gives no source authority |
| Death-year or rollover-year mandatory amount | Blocks the year if positive. Existing R2b-D direct transfer principal is excluded from conversion |
| NEVER or null stop rule | The stop test does not block. Other time and source gates apply |

`ScheduledRothConversionPolicy` applies enabled, start and one-time frequency tests around the stop test. There is no general retirement, Social Security claiming or owner age predicate in that scheduler. Owner presence, employer availability and source rights remain independent. A live survivor's original eligible account can continue under the applied spousal routes if the schedule passes.

## Strategy, tax and source constraints

**CURRENT_IMPLEMENTATION.** `RothConversionTargetBracketResolver` resolves the top of the configured federal bracket for the year and filing status. `RothConversionBracketFillCalculator` computes pre-conversion taxable income with the existing tax-funding machinery and iterates trial conversions against income, Social Security taxability and additional taxable funding. RMDs enter that base through the withdrawal breakdown or previously paid opening evidence, so they already reduce capacity. The engine limits execution to available same-owner positions. The certified spousal path explicitly prices executable trial funding from the trial portfolio. The generic path retains legacy trial inputs and the final execution cap. Existing Arkansas controlled target/capacity paths remain distinct. Do not alter their formulas for this feature. Zero remaining bracket room is a valid zero result. Recheck targeted results after the premium retry because Medicare cash requirements can affect taxable funding.

`RothConversionEligibilityPolicy` supports traditional IRA, rollover IRA, traditional 401(k) and traditional 403(b) sources with a same-owner Roth IRA destination and positive balance. Employer availability can be explicit or effective from a calendar year. Legacy unknown availability can execute under the historical assumption. The same-owner constraint prevents using only one spouse's destination for both. `ProjectedPortfolioRothConverter` uses annual original-owner sets and account order. In applied spousal execution those sets exclude the deceased from the death year. Consequently original deceased positions cannot be converted through either retained Beneficiary or effective Owner succession, including after the R2b-D rollover. Already-distributed RMD dollars are removed before allocation. A spouse's original IRA has no need for R4. R4 would separately authorize new effective-owner successor sources with provenance. An explicitly inherited beneficiary type is outside the supported type list.

**UNSUPPORTED_OR_REFUSED.** The legacy ordinary deterministic owner set can retain both original owners after a modeled death outside the applied spousal route. Do not extend policy execution into that source boundary. R3-C should require living-owner conversion admission explicitly or refuse a new schedule on unsupported death/analysis paths, while legacy requests keep their exact historical routing. Preserve other states, Advanced/NIIT and analyzers. Identifying a broader legacy deceased-owner issue does not authorize a correction in R3-A or an expansion of R4 eligibility.

## Household reference control and other death scenarios

**PROPOSED_NOT_IMPLEMENTED.** Set both original traditional IRAs synthetically, primary born 1963 with $1,500,000, spouse born 1976, spouse dies in 2030 under Beneficiary treatment. Use an independent zero-growth ledger control with $100,000 ordinary plus $100,000 conversion debits per year in 2030–36, then $50,000 of each in 2037. That consumes exactly $1,500,000. For engine integration, choose synthetic expenses, funding order and tax cash to reproduce those debits or assert separately derived debits that reach the same zero closing value. At age 75 in 2038 its own minimum is zero. The spouse would reach 75 in 2051, so the supported commencement is max(2031, 2051) = 2051. If the inherited prior December 31 value is an independently specified $660,000, the survivor at age 88 has a Single Life factor of 6.6 and a $100,000 first beneficiary minimum in 2051. This is a positive household scheduling event. The inherited balance provides no Roth capacity in either year. The fixture should assert balances and transfers independently, with zero growth and separately controlled tax and spending cash. [IRS Publication 590-B, Table I](https://www.irs.gov/publications/p590b) · [§1.401(a)(9)-3(d)](https://www.irs.gov/irb/2024-33_IRB)

Repeat first-death order in both directions, with younger/older survivors, early death, the first distribution calendar year and post-RBD death. Include Owner and Beneficiary, multiple positions, two IRAs, supported IRA/401(k) combinations and applicable mixed-source refusals. R2b-B pre-April first-year cases may produce zero decedent requirement. R2b-B/C/D post-RBD death and R2b-D January beneficiary distributions can produce positive house requirements even with zero survivor own RMD. Continue uses only existing living sources after mandatory and spending debits. Skip and Permanent operate on the same household total regardless of payee. No second household or per-spouse setting is needed.

## Decisions and approval matrix

**REQUIRES_USER_APPROVAL.** Each row is a decision for R3-B or later. No proposed behavior is active in this audit.

| Decision | Current | Proposed | Alternative and financial or compatibility impact |
| --- | --- | --- | --- |
| A Start | Inclusive year exists | Reuse the field. New-plan draft begins at projection start | Clamping edits silently shifts a transaction and changes taxes |
| B End | Frequency and horizon only | Inclusive explicit end. Legacy retains implicit horizon | A mandatory migrated end would alter old extended-horizon analyses |
| C New default | No active request. Editors offer First RMD | Still disabled. First year and end at creation horizon as draft defaults, Continue as new-plan preference | Enable by default would change plan outputs. Do not do so |
| D Legacy | Route-dependent first RMD and permissive null | Preserve exact old route logic and stored null/Never | Mapping all first-RMD requests to Permanent changes historical results |
| E Continue | Never ignores RMD stop | Execute within window after mandatory payouts, subject to existing capacity | A forced positive conversion can violate source and tax limits |
| F Skip | Current-year behavior in normal legacy | Block precisely positive-obligation years and allow later resumption | Age trigger would skip depleted positions without a requirement |
| G Permanent | Remembered in lifetime context only | Lock at first modeled positive annual obligation during the run, even before schedule start | Window-only lock would permit a strategy despite an earlier house event |
| H Trigger | Raw annual same-year, rounded remembered legacy | Use one raw positive verified annual requirement for both new predicates. Include fully presatisfied, death-year and rollover-year obligations | A cash-paid test misclassifies prior-paid opening evidence. An age test misclassifies zero balances |
| I Household | Household total | Retain house scope | Per-person settings could allow an otherwise blocked house year |
| J Survivor | Existing owner eligibility varies by route | Continue schedule under audited living source checks on admitted route | Expanding deceased or effective successor source access exceeds R3 |
| K Strategy | Fixed and current bracket/custom | Reuse all currently admitted strategies and solver | New optimization changes financial semantics and review scope |
| L No source | Zero executable capacity or UI transition refusal | Preserve both distinctions and provide clear status | Refusing every zero result would reject valid depleted and future schedules |
| M Year checks | Minimum 1900, no horizon clipping stored | Validate bounds, enabled range order and one-time equality. Preserve out-of-horizon selections as inert where disjoint | Rejecting all out-of-horizon years makes ordinary horizon edits destructive |
| N Persistence | Nullable unversioned request | Optional versioned scheduling envelope on existing immutable request. Write only on explicit apply or genuine new-plan creation policy initialization | Null as new default breaks legacy round trips |
| O History | Frozen digest and scope-specific provenance | Bind applied policy identity and exact frozen years. Reuse existing annual fields where sufficient, add schema only for incompatible new evidence | Reusing existing provenance without identity can relabel older results |
| P UI | Two surfaces share apply transaction | Reuse start, add end, use labeled policy control and common validation/help | Duplicated fields or independent apply paths cause draft divergence |
| Q Phasing | One scheduler used by multiple routes | Gate R3-B dormant settings, R3-C certified execution, R3-D lifecycle and native acceptance | Turning on new choices in B alone silently activates incomplete financial behavior |

A new plan is identified by the actual new-plan factory path, never by a missing request or deserialization null. If B must persist a preference before activation, use explicit new-plan version metadata or a disabled versioned request and distinguish it in UI from an old loaded plan. Old files saved by the new release remain old when no explicit schedule change was accepted. Existing Never and null continue to use their historical behavior. An explicit Permanent selection is a new policy, not the stored legacy FIRST_HOUSEHOLD_RMD value.

## Validation and interface

| Condition | Proposed result |
| --- | --- |
| Equal start/end | Inclusive single year, including Annual |
| Start after end or malformed year/policy | Validation failure for new config. Typed load rejection for malformed explicit version |
| Start before projection | Keep stored year. Annual evaluates intersection, one-time does not move |
| End before opening or start beyond horizon | Valid saved preference, no conversion in current run, visible status |
| End beyond horizon | Keep saved year. Execute only modeled years |
| Missing optional new fields on old plan | Use historical scheduling. Missing required fields within new version are invalid |
| Disabled or cancelled edits | Preserve accepted financial inputs and calculation freshness unless actually applied |
| No source, exhausted balance, partial owner availability | Zero or reduced executable amount, with original priority. Maintain existing UI transition validation |
| Zero RMD at age, delayed Beneficiary, positive then zero | Use actual obligation and the selected policy's memory semantics |
| Positive RMD with zero/positive bracket room | Continue produces bounded zero/positive respectively. Skip blocks current year. Permanent remembers event |
| Positive first-death year, out-of-scope route | Use applicable actual supported obligation. Refuse unsupported new-policy execution and preserve last success |

In both existing editors, label the existing conversion year as Start year, add End year and reuse the stop control with readable choices and help. A compact choice control or accessible radio group is acceptable. Bind the end to start for One-Time with visible meaning. Add it to dirty-state comparison and cancel reload on both surfaces. Preserve Light/Dark styling, keyboard traversal, enabled and required-field state, screen-reader labels and error focus. Keep the existing destination dialog and transaction. Update analyzer input summaries and PDF disclosures for frozen schedules where that policy is supported. Unsupported analysis should disclose the unavailability, with no hidden policy substitution.

## Retention and minimal implementation stages

**CURRENT_IMPLEMENTATION.** Complete history stores the plan snapshot including the Roth request in `inputsDocument` with a digest. Capture/comparison and plan modifications rely on these inputs. `CompleteProjectionSnapshot` independently replays applied spousal schemas and compares annual fields. Earlier generic histories have their existing validation and should not be assumed to use that same financial replay. Retained schemas 1–11 represent generic, account availability, Arkansas, and the distinct R2 spousal policies. `SpousalInheritancePlanningEvidence` binds their provenance. Annual rows already carry actual conversion and supporting income attribution. Current/baseline association, UUID, Save/Open/Save As, Swap, PDF and accepted projection freshness use the frozen result. PDF also reads the Roth request for display.

**PROPOSED_NOT_IMPLEMENTED.** Use an optional new schedule-version property plus end and policy in the existing request. Preserve old creator behavior and omit absent fields from old wire representations. Update all construction, copy/disable, equality and draft comparisons, plus input summaries. Preserve configured-year fingerprints, including an out-of-horizon value. For new execution, add an orthogonal applied scheduling identity alongside any R2 identity, bound to frozen inputs and annual evidence. Independent replay must dispatch to the exact frozen combination, then compare conversion, mandatory payouts, taxable sources, taxes, Medicare and balances. Keep earlier schemas' predicates and restore methods. Add a retained schema only if representing the new execution combination or proof requires it, rather than duplicating end year or event booleans already derivable from inputs and annual evidence. Test tampering with rebuilt digest/provenance. A recalculated digest alone does not authenticate caller intent if a different predicate is financially indistinguishable for every modeled year. Bind the explicit policy and verify internal consistency as well as numerical replay.

- **R3-B** can be input-only when the new semantics remain unavailable for active use. Approve serialization, read-only/dormant draft controls, validation, factory defaults and cancellation behavior. If users can apply a new enabled policy in B, ship the financial gate with C or refuse the unsupported execution explicitly.
- **R3-C** should pass distinct current raw-positive and previously accepted raw-positive inputs to a versioned scheduler. Preserve the separate legacy predicate and existing lifetime flag. Track the new event only on accepted years, include the current event when deciding, and reuse the resolved flag across Medicare retries. Start outside the horizon contributes no fabricated event. Keep the existing RMD, spending, conversion and tax order. Initially gate the explicit policy to the normal Florida Basic certified route. Add provenance and independent replay with the financial change so results remain certifiable.
- **R3-D** finishes cross-surface validation, persistence and history workflow coverage, frozen PDF disclosure and native acceptance. Run the accepted R2b-D bounded selector with new cases. R4 source authorization remains a separate approval.

## Independent test oracles

**PROPOSED_NOT_IMPLEMENTED.** Use zero growth, supported original owner and destination, sufficient outside cash for tax unless specifically testing gross-up, and rates explicitly labeled as synthetic or frozen 2026 federal rules. The amounts below are hand calculations.

| Control | Expected |
| --- | --- |
| Window A/B/C | With an eligible $100,000 IRA and $10,000 annual fixed request, start 2032/end 2033 yields $0, $10,000, $10,000, $0 for 2031–34. Equal 2032 boundaries yield only $10,000 in 2032 |
| Continue and mandatory exclusion D/I | At age 75 with $246,000 December 31 IRA, 24.6 gives $10,000 RMD. A $20,000 fixed conversion leaves $216,000 before other debits, with cash $10,000 and conversion $20,000 attributed separately. For an oversized fixed request the source can contribute at most $236,000 after that RMD |
| Skip/resume and Permanent E/F/G | In a two-owner control give the age-75 original IRA $24,600, and a younger eligible spouse a separate funded source. Year one RMD is $1,000. Spend the $23,600 remainder that year with tax separately funded. Year two RMD is zero. Skip permits a $10,000 otherwise eligible spouse conversion in year two, Permanent gives zero both years. Legacy normal permits year two, legacy lifetime stops |
| Opening trigger | In a plan where annual required $10,000 was entirely paid before the projection, required remains $10,000 and projected RMD cash is $0. Skip and Permanent trigger, while Continue can use eligible residual source |
| Owner/Beneficiary H/J/K | Reference control above gives zero owner minimum in 2038 and $100,000 inherited minimum in 2051. Zero successor conversion capacity. In a separate death control an eligible living survivor with $60,000 and destination can still execute a $10,000 fixed conversion on Continue |
| Bracket room L | Freeze 2026 Single deduction $16,100 and 12% upper threshold $50,400. $40,000 pretax income including a $10,000 RMD, with taxes funded by nontaxable cash, gives baseline taxable $23,900 and conversion room $26,500. Omitting the RMD would incorrectly give $36,500. A baseline already over $50,400 yields zero |
| Historical M | Replay the same legacy request across a positive-then-zero boundary in each route, compare actual dollars and frozen original identity. Recomputed checksum tamper with changed year or policy must fail where annual evidence disagrees |
| Gross-up and Medicare N | For a Single covered survivor and frozen 2026 rules, $114,000 AGI exceeds the $109,000 first Medicare surcharge threshold. Use the accepted model's annual tariff of $3,583.20. A taxable shortfall funded entirely in the 22% marginal band requires $1,000 ÷ 0.78 = $1,282.05 additional pretax draw for a $1,000 after-tax gap. Separate this from the bracket target test and assert one receipt, premium settlement and balance conservation |

[IRS Publication 590-B RMD tables](https://www.irs.gov/publications/p590b) · [IRS 2026 inflation adjustments](https://www.irs.gov/newsroom/irs-releases-tax-inflation-adjustments-for-tax-year-2026-including-amendments-from-the-one-big-beautiful-bill)

## Verification and final boundary

The existing Java 25 Maven tests selected by `*Roth*Test,Spousal*Test,OpeningRmdProjectionTest,ProjectionSnapshotPersistenceTest,ProjectionPdfExportTest` passed 405 with zero failures, zero errors and zero skips. Maven exited 0 using IntelliJ bundled Maven offline and the workspace-local repository. The audit run is recorded in `target/r3a-audit-focused.log`. The full bounded suite was not rerun for this report-only change. Both Git whitespace checks exited 0 and the index is empty.

**REQUIRES_USER_APPROVAL.** Approve R3-B's preference-only boundary, explicit new-plan Continue default, inclusive years, and preservation of route-specific legacy semantics before any implementation. The financial scheduler, history identity and successor conversions remain for their respective approved implementation phases.
