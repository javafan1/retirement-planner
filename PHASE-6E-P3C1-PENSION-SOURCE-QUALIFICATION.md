# Phase 6E-P3C1 — Pension Source Qualification

## 1. Executive summary

The standard and guided pension editors now record how the original benefit was established. The gated Arkansas annual engine can use that documented fact for ordinary employer retirement benefits, including direct spouse-survivor payments. For unresolved origin within the ordinary pension model, a proven tax-equivalent result can proceed. A material unresolved result carries the pension rows for future review. Arkansas and Colorado remain disabled.

## 2. Opening Git state

HEAD was `baa25930dd2a87c4bfa982acce728f65efcdb04b` (Complete Arkansas tax funding and Roth conversion certification). The index was empty. P3B was still an unstaged working-tree checkpoint with 14 modified tracked paths and nine untracked paths. Its report and existing integration files were present. Four source paths and the Arkansas integration test overlap the opening P3B changes.

## 3. Existing pension and source model audit

`Pension` stores original owner, amount, dates, COLA, survivor amount, participant status and an optional legal DB/DC classification. Participant facts cover separation and retirement conditions. Neither those facts nor the payment schedule identify a sponsor. The household pension selector decides whether the original pension pays its owner or the surviving spouse, then freezes the source facts. Typed IRA and employer accounts and the separate nonqualified-annuity model hold different legal and basis evidence. They do not establish the origin of a separate Pension record. The normal Income workspace and guided Pensions step already share `PensionDialog` and replace an accepted edit with its returned value.

## 4. Legal meaning of the origin

| Stored value | User-facing selection | Meaning |
| --- | --- | --- |
| `UNKNOWN` | Unknown | The original arrangement has not been established. |
| `EMPLOYER_RETIREMENT_PLAN` | Employer Plan | An ordinary retirement benefit from a plan established by the original owner's employer and directly tied to that employment, as supported by plan documents. |
| `INDIVIDUALLY_PURCHASED_CONTRACT` | Private Contract | A privately purchased insurance or income contract independent of employment. An insurer paying an employer plan does not by itself make the benefit a private contract. |
| `OTHER_OR_SPECIAL_ARRANGEMENT` | Other / Special | An identified arrangement outside ordinary pension modeling, such as military, railroad, disability, or divorce-order benefits. Additional evidence and modeling are necessary for tax treatment. |

## 5. Arkansas §26-51-307 qualification

Verified November 19, 2026. The official administrative rule requires direct public or private employment connection and receipt by the original employee or former employee, or that person's spouse. The Arkansas DFA 2025 return instructions distinguish employer distributions from the traditional IRA age rule, describe a possible early-distribution additional tax even when employer relief applies, and limit a surviving spouse to one combined $6,000 exclusion. The current phase continues the accepted ordinary planning convention and 2026 rate and low-income policy. [26 CAR § 100-118](https://codeofarrules.arkansas.gov/Rules/Rule?chapterID=33&levelType=section&partID=941&sectionID=40225&subChapterID=261&subPartID=6195&titleID=26) · [2025 DFA annual return instructions, pages 7 and 13](https://www.dfa.arkansas.gov/wp-content/uploads/2025_AR1000F_and_AR1000NR_Instructions.pdf)

DFA Subject 206, revised February 24, 2023, includes a broad under-59½ bullet. The more specific ordinary employer guidance in the official rule and later annual booklet supports the established distinction between the exclusion and an early-distribution additional tax. P3C1 requires existing run evidence that a potentially early own payment has no applicable Form 5329-related additional tax. Timing, state-specific basis, military or railroad treatment, disability, QDRO, inheritance and specialized IRA/rollover categories still require evidence beyond the selected origin. [DFA Subject 206](https://www.dfa.arkansas.gov/wp-content/uploads/206-PensionsAnnuities.pdf)

## 6. Alabama DB/DC

The existing `PensionPlanClassification` enum, serialized values, computation and Alabama notices/navigation are unchanged. Arkansas's ordinary cap can apply to either documented ordinary employer DB or employer DC income. Neither DB/DC value determines Arkansas source origin. Employer origin does not create an Alabama defined-benefit exemption.

## 7. Data-model changes

`RetirementIncomeSourceOrigin` is a general factual enum. The Pension bean accepts optional `sourceOrigin`, and the immutable annual `IncomeSourceAttribution.PensionFacts` freezes it with the other original terms. The old attribution constructors still produce UNKNOWN. Arkansas evidence can also substantiate a legacy UNKNOWN payment inside the isolated validation harness. Conflicting positive attestations are rejected, and explicit out-of-scope evidence takes priority over the ordinary choice.

## 8. Legacy UNKNOWN

Missing and null input mean UNKNOWN. Explicit UNKNOWN normalizes to the omitted wire form. Unrecognized strings and invalid serialized values fail instead of becoming employer evidence. Existing plans receive no inferred classification.

## 9. Persistence and canonical identity

The ordinary plan repository, scenario copy and frozen projection input paths use the additive bean property. An explicit source alters the full input digest, allowing the application to tell new and old inputs apart even when a registered state's dollar results are identical. UNKNOWN is omitted, so untouched legacy canonical inputs keep the same bytes and digest. Full-plan analyzer financial fingerprints consume the serialized financial input tree. SS-only fingerprints use separate benefit inputs. No global compatibility version is introduced. The existing retained annual wire record is unchanged. For registered-state retained results, the origin is available in the frozen input document and is intentionally absent from the retained annual source encoding. Live legacy `RetirementPlanSnapshot` reference-sharing was outside scope. The accepted frozen current and baseline role swap is tested in both directions without recalculation.

## 10. Annual and survivor propagation

`HouseholdPensionIncomeCalculator` keeps its payment selection and calendar. Its existing call to the attribution factory now freezes original owner, origin and plan class. The source's recipient remains separate. Arkansas checks the original owner in the household, and requires an active spouse recipient, an inactive original owner and a positive original survivor provision for direct survivor relief. For death-year MFJ and subsequent Single the annual recipient controls the pool. No deceased pool transfers.

## 11. Materiality

For positive ordinary fully taxable receipts, Arkansas adds confirmed qualifying amounts to the recipient's certain and possible pools. It adds unresolved amounts only to the possible pool. The existing worksheet caps both at $6,000 and bounds legal credit choices throughout the taxable range, including Arkansas rate discontinuities. Equal proven tax bounds permit the annual result, including tax-stable zero and a pool exhausted by other qualifying receipts. Nonzero results still require the accepted low-income admission proof. A Private Contract entered as a Pension lacks the separate taxable-portion and basis evidence of the supported commercial annuity model and produces structured unavailability. An independently admitted commercial annuity continues to enter once at its taxable amount without employer-plan relief. The employer choice does not resolve partial basis, contradictory evidence or special treatments.

## 12. Preflight and navigation preparation

`ArkansasPensionPreflight` matches authoritative positive annual receipts back to the original editable pension row. It returns an immutable list with the original reference, captured name and owner, and the current recipient. Pension references are session-local. Indistinguishable duplicate receipt facts still fail the existing reconciliation. Zero, inactive, out-of-horizon and already internally confirmed pension receipts are excluded. When the existing annual tax authority refuses a materially unresolved origin, the gated execution attaches the relevant year's potential review rows to its unavailable exception. This list is conservative when several unresolved receipts coexist in that year. It is guidance about source review, with no speculative tax amount. The pure lookup can support a future early notice. The phase adds no public Arkansas banner, state picker entry or UI routing.

## 13. Regular pension editor

The optional Original Retirement Income Source control sits after the existing legal-plan control and before the separate required-field legend. It uses a linked tooltip and the existing scrollable form. Apply creates an edited Pension for the owning view. Cancel discards the draft.

## 14. Guided pension editor

The guided Pensions step reuses the same control, tooltip and result converter. The optional source field keeps the wizard's existing required-field markers and draft navigation. An Unknown choice does not prevent plan creation.

## 15. Light/Dark and scaling verification

Two Windows Glass runs asserted 100% and 150% window output scales. Each passed 26 UI tests with zero failures, errors or skips. Tests opened regular and guided controls in Light and Dark, narrowed the pane to 350 JavaFX pixels and applied an 18 px font. They checked separate rows, selected text, actual popup captions, help text and the Cancel footer. Inspected all eight captured Windows renders in `target/phase6e-p3c1/previews/`. Both source controls were readable. The regular legend was on its own row below the selector and the guided legend remained above the form. The separate full-app native checklist remains open. Logs: `target/phase6e-p3c1-scale1.0.log` and `target/phase6e-p3c1-scale1.5.log`.

## 16. Independent tests

The ordinary DB and DC direct controls produce $1,140.41 at $50,000 and apply no special relief from either legal class alone. Other controls cover tax-stable zero, $1,158.91 with an already used pool, $2,648.02 for actual MFJ with two separate pools, a positive unresolved amount, wrong-year evidence, contradictory receipt identity, the younger-owner Form 5329 gate, and refusal of unsupported private/special pension entries. The actual engine covers both survivor directions, MFJ and later Single, and a partial initial year with two current pensions, an inactive future pension and a zero pension. Its $48,000 receipt case gives $1,066.41 after one known pension fills the pool. The established partly taxable commercial-annuity/NIIT fixture also runs. JSON, dialog independence, Save As, scenario copy, frozen input and analysis fingerprints, and both accepted current/baseline role swaps have direct assertions.

## 17. Focused verification

Temurin 25.0.4.1, IntelliJ's bundled Maven and the workspace `.codex-m2/repository` were used. The final focused run passed **619 tests, zero failures, errors or skips**. It covered pension forms and domain behavior, guided creation, Arkansas and Alabama suites, projection and analysis codecs, admission, legacy state fixtures and the Michigan attributed goldens. Log: `target/phase6e-p3c1-focused.log`.

## 18. Bounded regression

The established selector `-Dtest=*,!*Benchmark*,!*CapacityProbe*,!*Performance*,!*Exhaustive*,!*Search*` discovered **5,473 tests**. **5,438 passed**, 35 were skipped, and there were zero failures or errors. This is 12 additional passing tests compared with P3B. The skip count is unchanged. Maven exited 0 after 9 minutes 35 seconds. Log: `target/phase6e-p3c1-bounded.log`.

## 19. Existing-state financial invariance

Registry remains AK, AL, AZ, FL, GA, IA, IL, KY, MI, MS, NC, NH, NV, OK, PA, SD, TN, TX, VA and WY. Alabama retains its DB/DC and preflight behavior. This phase changes only source data collection and Arkansas interpretation of the new fact. No existing monetary golden was edited.

## 20. Exact changed-file manifest

The Java paths below are beneath `src/main/java/com/daviddunn/retirementplanner/` or the corresponding test package beneath `src/test/java/com/daviddunn/retirementplanner/`.

| Scope | Path |
| --- | --- |
| Main, new | `domain/income/RetirementIncomeSourceOrigin.java` |
| Main | `domain/income/Pension.java` |
| Main | `domain/income/IncomeSourceAttribution.java` |
| Main | `ui/dialogs/PensionDialog.java` |
| Main | `domain/tax/state/ArkansasTaxEvidence.java` |
| Main | `domain/tax/state/ArkansasIncomeReconciliation.java` |
| Main | `domain/tax/state/ArkansasProductionTaxCalculator.java` |
| Main, new | `domain/tax/state/ArkansasPensionPreflight.java` |
| Main, P3B extension | `domain/projection/ArkansasProjectionValidation.java` |
| Main, P3B extension | `domain/projection/ArkansasProjectionUnavailableException.java` |
| Test, new | `domain/income/PensionSourceOriginTest.java` |
| Test, P3B extension | `domain/projection/ArkansasProjectionIntegrationTest.java` |
| Test | `domain/tax/ArkansasDomainCalculationTest.java` |
| Test | `persistence/ProjectionSnapshotPersistenceTest.java` |
| Test | `ui/dialogs/PensionLayoutAndRequiredNameTest.java` |
| Report, new | `PHASE-6E-P3C1-PENSION-SOURCE-QUALIFICATION.md` |

## 21. Remaining historical provenance

Arkansas's internal gate still cannot produce accepted durable history. The projection and analysis codecs, manifest and freshness registries retain their accepted formats. The next phase needs a genuine jurisdiction and result identity for source evidence, rule versions, detached reports, accepted analyses and independent current/baseline comparisons. The general pension source already persists in frozen inputs.

## 22. Phase P3C2 dependencies

Separate approval is required. Before eventual admission, specify how durable annual source provenance and user-facing navigation use these facts. Resolve evidence collection for material early-distribution and basis cases if those are within the intended admission scope, recheck official annual-return guidance, and maintain structured refusal for unsupported facts. Include compatibility coverage for pre-origin snapshots and frozen analyses.

## 23. Native Windows acceptance checklist

- [ ] Check standard and guided dialogs at normal Windows scaling and 150%, in Light and Dark. Confirm all captions and drop-down options, help readability, required legend separation, lack of horizontal scrolling, and footer reachability.
- [ ] Apply one origin and alter DB/DC independently. Cancel each edit and verify the original plan is unchanged. Create a guided Michigan or Alabama plan with Unknown and verify normal navigation and completion.
- [ ] Save As, reload, make a scenario copy and swap both accepted baseline roles. Verify explicit source persistence and legacy Unknown. Confirm the accepted historical taxes remain attached to their result roles.
- [ ] Verify Alabama's separate preflight and review navigation. Arkansas validation requires the isolated internal engine and remains absent from the production state picker.

## 24. Git verification

Final `git status --short --untracked-files=all` showed 21 modified tracked files and 13 untracked files, including the opening P3B files. `git diff --check` and the staged whitespace check passed. HEAD is unchanged and the staged diff remains empty. All work remains local.
