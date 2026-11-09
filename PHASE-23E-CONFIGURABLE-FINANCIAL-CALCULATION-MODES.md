# Phase 23E — Configurable Financial Calculation Modes

Implementation and verification report. Native Windows acceptance remains pending. This report describes Phase 23E changes relative to the preserved, accepted Phase 23D working tree, not merely relative to Git HEAD.

## 1. Executive summary

Two freely selectable financial calculation modes are implemented. BASIC preserves ordinary federal/state tax calculations and finite nonqualified-annuity basis recovery, omits NIIT, and does not impose NIIT completeness/growth restrictions. ADVANCED applies the bounded source-complete NIIT policy, including independently supported no-annuity configurations. Unsupported Advanced calculations fail explicitly; there is no Basic fallback.

Application Settings controls the default for new user-created plans. Each plan retains its own optional, explicit mode. Missing legacy mode preserves the original feature-dependent numerical contract. Settings changes never rewrite existing plans or their results. There is no licensing, feature gating, dependency change, tax-law expansion, or new stochastic dimension.

The mandatory architecture audit preceded production edits: [architecture checkpoint](PHASE-23E-A-CALCULATION-MODE-ARCHITECTURE.md). Its compatible optional-field/disjoint-capability design did not require an incompatible historical migration.

## 2. Architecture checkpoint decision

Introduce `FinancialCalculationMode` in the domain model and one central `FinancialCalculationPolicy`. The projection captures the plan mode and carries it as immutable, zero-cash annual source evidence through the existing federal, tax-funding, Medicare reevaluation and Roth trial calculations. Financial math never reads Application Settings or JavaFX controls.

Use a distinct transient common-mode comparison calculation rather than replacing a saved historical Baseline projection. Both working candidates receive Current's resolved mode, while both original saved documents remain independently bound. Historical viewing restores saved annual evidence without running the evaluator or engine.

## 3. Application Settings design

`ApplicationSettings.defaultFinancialCalculationMode` defaults to BASIC, persists explicit values, and is copied with the other settings. Existing settings missing the property also default to BASIC. Invalid stored values use the existing visible recovery/preservation path; they do not silently overwrite the damaged file.

Application Preferences has a Financial Calculation Mode section with both enabled choices and the requested explanations. Existing durable asynchronous Save/Cancel and theme preview behavior remain. The compatibility overload for old preference writers changes only the settings it owns; a concurrent theme-only write cannot reset a newly selected calculation default.

## 4. Plan-level calculation mode

`RetirementPlan.financialCalculationMode` is optional only for historical compatibility. Explicit BASIC/ADVANCED values survive Save, Load, Save As, snapshots, scenario copies, baseline construction, MC candidate isolation and projection swaps. Unknown values, numeric enum ordinals and explicit null values reject.

The Assumptions workspace has a required mode selector with its disclosure. Selection stays in the detached draft until Apply. Apply uses the existing revision/change notification and invalidates financial freshness without automatically executing a projection. Cancel discards the draft. Selecting the original mode again is not a material change. Legacy plans can explicitly establish the displayed compatible policy using Use Selected Mode Explicitly.

## 5. Defaults and legacy files

| Input | Execution/default behavior | Serialization |
|---|---|---|
| Newly created user plan | Application default captured at creation; initially BASIC | Explicit mode |
| Existing explicit plan | Its saved mode, independent of preferences | Same explicit mode |
| Missing legacy mode, no annuity | Original NIIT omission | Property remains omitted |
| Missing legacy mode, Phase 23D annuity | Original guarded NIIT behavior | Property remains omitted |
| Old frozen result | Its original documents, capability and annual evidence | Never rewritten on viewing |

No third user-selectable mode was introduced. Internally, null means legacy compatibility; it never consults a mutable global preference. Domain/demo factory calls and generic supplied-plan attachment retain their original behavior. A separate new-user-plan boundary and the wizard capture the application default. The predecessor-reader test compiles the archived pre-mode root model: old documents load, while explicit mode documents reject instead of silently executing different taxes.

## 6. Basic NIIT policy

BASIC produces no NIIT calculation or NIIT tax-funding charge. Annual evidence and reports say **NIIT Not Modeled**, not Computed Zero. Mode evidence has zero gross/taxable cash and cannot become a deduction, receipt or deposit.

Positive brokerage assets, existing cash growth, generic pensions and ordinary taxable conditional benefits retain their established calculations when otherwise valid. Their prior modeling limitations are not repaired or hidden by this phase. NIIT omission is disclosed in modeled federal totals. Domain, contract, basis, state, mortality and stochastic-input checks remain independent.

## 7. Advanced NIIT policy

ADVANCED uses the accepted bounded NIIT calculator and thresholds; it does not change Section 1411 law or broaden source recognition. Independently supported no-annuity plans now receive explicit NIIT evidence, including a calculated zero when the admitted facts establish zero. Qualified retirement withdrawals and conversions may increase MAGI without becoming net investment income. Recognized personal interest and taxable commercial annuity amounts retain explicit attribution.

A separate federal known-answer test uses no annuity: $294,240 qualified conversion plus $5,760 completely attributed personal interest gives $300,000 AGI, $49,468 ordinary tax and $218.88 Advanced NIIT. Basic omits that NIIT while preserving ordinary tax. This is source-aware federal API support, not a new UI interest-entry feature or automatic recognition of brokerage/cash growth.

Generic pension qualification, positive brokerage assets without realized-gain/basis facts, unsupported conditional ordinary sources, and unclassified nonqualified growth still reject. A generated world encountering unsupported growth aborts the calculation, rather than becoming a portfolio failure or disappearing from the trial set.

The new contract-only annuity qualification `PERSONAL_GENERAL_RULE_FIXED_SINGLE_LIFE_V2` is available under explicit BASIC. It does not assert the old domestic/standard-deduction NIIT assumptions. Moving such an input to ADVANCED requires reopening the annuity and confirming the bounded NIIT facts; the editor clears that acknowledgment until confirmed. Existing Phase 23D V1 qualifications remain unchanged. Selecting Advanced alone never manufactures missing qualification facts.

When the first annuity is added to a missing-mode legacy plan, its prospective execution retains the original guarded Phase D policy. The editor discloses that policy before commitment. To elect NIIT omission, explicitly select BASIC for the plan; merely opening the editor does not rewrite its mode.

## 8. Annuity and basis independence

No payment-evaluator arithmetic was changed for the modes. Fixed monthly payments, documented tax starting date, finite opening investment, annuitant survival, original identity, inclusive timing and pure reentrant basis recovery remain authoritative. The new Basic qualification changes asserted scope, not the payment split.

For $1,200 gross/$720 excluded monthly and $10,000 opening recoverable investment, BASIC gives:

| Year | Gross | Recovery | Taxable | Closing investment |
|---|---:|---:|---:|---:|
| 1 | $14,400 | $8,640 | $5,760 | $1,360 |
| 2 | $14,400 | $1,360 | $13,040 | $0 |
| 3 | $14,400 | $0 | $14,400 | $0 |

NIIT is not modeled in any Basic year. No separate investable annuity asset is created.

## 9. Federal tax, Roth and cash integration

`FederalTaxCalculator` selects NIIT from the captured annual policy. `TaxFundingCalculator` and the existing Roth solver use those same annual sources. There are not two tax engines or an independent UI tax calculation. Ordinary tax components are identical before NIIT funding feedback.

Literal MFJ control: first-year AGI/MAGI $300,000, taxable annuity $5,760, recovery $8,640, ordinary federal tax $49,468. BASIC total is $49,468 with no NIIT fact; ADVANCED adds $218.88, producing $49,686.88. Same-year Medicare remains separate, $10,639.20 in this fixture. NIIT is not a Part B/D premium.

The Basic Roth bracket-fill funding control converges to approximately $248,532.80 conversion and $45,707.20 tax-funding withdrawal at $300,000 AGI (existing two-cent solver tolerance). The accepted D Advanced controls remain $248,313.92 and $45,926.08. Gross annuity cash enters settlement once, while only its taxable component enters ordinary income. RMD formulas and account eligibility are unchanged.

## 10. State admission

Both modes retain nonqualified-annuity admission only for Illinois and existing modeled no-general-individual-income-tax jurisdictions. Michigan, Pennsylvania and Iowa remain explicitly blocked for this annuity classification. BASIC bypasses no state classification rule. Mode evidence is zero financial flow in source-aware state bridges; it is not pension, IRA or municipal income. No state rate, table, exemption or jurisdiction was added.

## 11. Current/Baseline consistency

New MC paired execution freezes both original documents before applying one common policy to working copies. Current's explicit mode controls; if missing, its resolved legacy policy controls. Neither Baseline's saved mode nor its benefit definitions are rewritten. Admission failure on either side rejects the comparison as a whole.

The ordinary Results Summary preserves the original saved Baseline for Details, charts, swaps and history. Mixed-mode saved results initially show an explanation instead of asymmetric financial deltas. Run Comparison Using Current Calculation Mode explicitly calculates a new isolated pair in the background. Acceptance checks owner, original Current/Baseline, source revision, selected mode and both input digests. Failure or intervening edits preserve previous accepted projections. Break-Even consumes the common pair and matching assumptions/non-investable projections; raw mixed-mode projection comparisons reject.

Independent controls with Current $1,500 monthly versus Baseline $1,200, otherwise identical: common BASIC annual tax delta $864 and ending/estate delta $9,936; common ADVANCED annual tax delta $1,000.80 and ending/estate delta $9,799.20. Identical financial candidates yield zero paired deltas despite different saved modes.

A deliberately mixed *legacy* reexecution has a documented authorized change: old standalone Current no-annuity tax $48,085.60 and old standalone annuity Baseline tax $49,686.88 remain exact. A newly generated common-BASIC pair uses $48,085.60 and $49,468. This removes $218.88 of candidate tax-policy asymmetry. It does not modify any saved old result.

## 12. Historical provenance and version matrix

| Execution | New contract | Old contracts |
|---|---|---|
| Explicit BASIC deterministic | `conditional-benefits=deterministic-basic-niit-not-modeled-v1` plus existing original/annual binding | Preserved |
| Explicit ADVANCED deterministic | `conditional-benefits=deterministic-advanced-niit-guarded-v1` plus existing original/annual binding | Preserved |
| Mode-aware analyzer | Capability V5; `ANALYZER_BASIC_NIIT_NOT_MODELED_V5` or `ANALYZER_ADVANCED_NIIT_GUARDED_V5` | V1–V4 retain original meanings |
| Analyzer algorithm value | `analyzer-basic-niit-not-modeled-v5` / `analyzer-advanced-niit-guarded-v5` | Existing algorithm maps unchanged |
| MC standalone explicit mode | Payload V12 / fingerprint V13 | Payloads V1–V11 / fingerprints V1–V12 preserved |
| MC paired common mode | Payload V13 / fingerprint V14 | Original paired history remains readable |
| Explicit-mode annuity normalized financial inputs | `MODE_EXPLICIT_ANNUITY_INPUTS_V4` | Legacy annuity V3 unchanged |
| Complete snapshot/envelope/annual schemas | Existing versions; known optional policy source field | No global replacement or migration |

MC V5 capability protocols bind the frozen request's configured deaths or lifetime January 1/prior-December-31 context. Generated-world provenance remains independently strict about market, inflation, mortality, stream identifiers and absence of a supplied-world fingerprint. BASIC alone, without conditional/premium/stochastic expense features, preserves the existing caller-supplied-world path, with its original world fingerprint required for new authenticated storage. This does not enable benefit-bearing caller-supplied worlds.

Retained annual policy evidence must occur exactly once per explicit-mode year and match original mode/year. Basic rejects NIIT evidence; Advanced requires the existing validated NIIT evidence. Mixed/missing tags, changed original inputs, incorrect mode/family/model, new facts under old capabilities and missing original-world bindings reject. Untagged mode-only results cannot masquerade as legacy calculations. Frozen source labels, IDs, tax facts and original document digests are preserved separately from economic fingerprints.

Legacy missing-mode normalization omits the new property. Mode edits change full-plan financial fingerprints but not financially independent SS-only fingerprints. Old paired V11/V4 artifacts remain readable under their original stored fingerprint V12; new common-mode execution uses its new contract, so an old asymmetric contract cannot claim current common-mode applicability.

## 13. Analyzer and Monte Carlo execution

| Family | BASIC | ADVANCED |
|---|---|---|
| Ordinary/single-person deterministic | Existing financial behavior, NIIT omitted | Bounded NIIT admission |
| Integrated individual, Quick, complete search, grids/heat maps | Common authoritative projection | Same with strict NIIT |
| Couple weighted and single mortality-weighted | Existing valid paths/reference evaluation | Reference evaluation where reuse is unproven |
| Baseline and Break-Even | Same-mode original or explicit new common pair | Same rule |
| Standalone fixed/longevity, single/couple | Generated execution; ordinary market and expense risk retained | Generated execution with bounded source/growth admission |
| Paired fixed/longevity, single/couple | One common BASIC policy | One common ADVANCED policy |
| SS-only ranking | Unchanged objective | Unchanged objective |

The new matrix tests 64 combinations: both modes × eight configurations × zero/positive ordinary expense volatility × annuity/no-annuity. It verifies seeded repetition, candidate isolation, zero equal-candidate deltas, new snapshot/FP round trips and independently saved candidate modes. Unsupported Advanced source states are errors, not investment failures. RNG implementations, market/inflation/mortality draws, expense dimension/version 6/1, seed/year identity, resource limits and reducer arithmetic are unchanged.

## 14. Future licensing considerations

A future authorized entitlement provider could constrain UI choices or command availability at application boundaries. No such provider, placeholder interface, feature identifier, service or enforcement was added. The mode enum and financial policy express tax semantics only. Future licensing requires a separate decision and cannot silently change a saved result's calculation contract.

## 15. Current feature access

Both mode selectors offer both choices. There are no mode checks disabling or hiding MC, Break-Even, Social Security analysis, Roth tools, PDFs, history or baseline commands. Mixed-tax comparison admission protects financial validity; the explicit common-mode action restores a like-for-like calculation in either mode. Advanced unsupported-source feedback is financial admission, not a subscription restriction.

## 16. UI previews

Actual JavaFX settings and Assumptions scenes are generated in `target/phase23e/previews/` for LIGHT/DARK at 900×650 and 1180×820. The mode selector is inside the existing Income Tax Assumptions card, preserving the seven-card ordering and independent-column layout. Its explanation wraps, it reuses required markers/tooltips, and the fixed Apply/Cancel footer and body scroller remain. Settings retains Save/Cancel. Compact previews scroll the mode field into view; the test checks the actual theme stylesheet, visible field and footer bounds. A preview-test theme reset after cancel was corrected so the plan Dark images represent the real Dark stylesheet.

The rendered plan scenes in both themes and both sizes were visually inspected, along with the corresponding settings scenes. Labels and disclosures are readable, controls are within the scrollable body, and footers remain visible. These automated scene previews do not constitute the user's native acceptance.

## 17. Current/historical PDFs

Production PDF export generates `target/phase23e/reporting/basic-historical.pdf` and `advanced-current.pdf` from their respective frozen inputs and saved annual projections. Text assertions require the original mode, Basic omission disclosure and Advanced $218.88 fact. Rendered page PNGs are retained beside the PDFs. Advanced annual NIIT evidence is shown even for supported no-annuity plans; Basic does not display a calculated zero.

Analyzer details and PDF assumption sections use the frozen V5 manifest, including common mode and independent original Current/Baseline saved modes. Yearly Details reads completed annual policy evidence. No export recalculates the annuity or projection.

Break-Even UI/PDF disclosures derive their mode from completed annual sources. `advanced-common-break-even.pdf` and its rendered first page retain the common Advanced policy. The old serialized person-summary shape is unchanged; this is detached, JSON-ignored presentation metadata. Yearly Details allows original Basic/Advanced values to be inspected together but suppresses misleading mixed-mode deltas. A Basic NIIT cell reads NIIT Not Modeled even alongside an Advanced computed-zero result.

## 18. Known-answer and lifecycle evidence

New tests cover the literal tax/basis/funding controls above; state rejection in both modes; positive brokerage/growth/pension Basic compatibility versus Advanced refusal; computed-zero versus not-modeled; qualification revalidation; mode-only provenance rejection; economic fingerprint materiality and SS-only independence; strict null/unknown/ordinal decoding; old-reader refusal; settings default/cancel/concurrent writes; Save/Save As/restart; both-direction swaps; source-fenced comparison completion; original analyzer capability restoration; and all generated configurations.

The history test installs an engine that throws if invoked after reopening. It successfully restores and swaps the original Basic/Advanced results and exports their saved projections, proving these paths do not depend on historical recalculation. Existing atomic storage, Retry Save, cancellation/failure retention and financial goldens remain covered by the complete normal suite; no storage mechanism was replaced.

## 19. Exact changed-file inventory

The exact Phase E source/test/resource inventory is generated by comparison with the starting byte hashes in `target/phase23e/changed-file-inventory.txt` and listed in Appendix A. Final source/test/resource SHA-256 values are recorded in `target/phase23e/verified-source-hashes.json`, with post-regression verification retained separately. Reports added by this phase are this report and the architecture checkpoint; existing Phase D reports are preserved.

There are **66 Phase E source/test/resource paths: 53 modified existing paths and 13 new paths**. Appendix A records each changed file's byte SHA-256. The final all-source/test/resource manifest contains 1,326 paths; its SHA-256 is `0AEF701315A4CECE5D7ECF572DC8A8A8345231478B1D627F741E60D0253A1F30`.

Only one preexisting test is intentionally adjusted: `NonqualifiedAnnuityMonteCarloTest` asserts the new paired write payload/fingerprint/capability versions (13/14/5). Its financial expectations are unchanged. A separate historical V11/V4 round-trip test preserves old decoding, rather than redefining the old contract.

## 20. Focused regression

Java 25, required IntelliJ bundled Maven, repository-local `.codex-m2/repository`, `-DargLine=-Xmx4g`. Final focused selector: `CalculationMode*Test,NonqualifiedAnnuity*Test,NetInvestmentIncomeTaxCalculatorTest,ApplicationSettingsServiceTest,ApplicationSettingsDialogTest,Assumptions*Test,PlanningHorizonLayoutTest,InputTooltipTest,ProjectionComparisonServiceTest,BreakEven*Test`. Final exact totals are recorded after completion in section 22. Development failures included test API/import mistakes, a newly exposed Basic supplied-world guard, and a test mortality conditioning date before available opening balances. The initial full run also exposed the legacy empty-comparison API, required tooltip and established card-order contracts. Production was corrected to preserve those contracts, without changing their tests. The later full run was stopped early to include the prospective legacy-annuity editor disclosure fix. Final source is retested below. No financial golden was regenerated.

Final focused run: **316 tests / 316 passed / 0 skipped / 0 failures or errors**, 57.762 seconds, completed November 8, 2026 at 14:26:21 EST. Log: `target/phase23e/final-focused.log`.

## 21. Native synthetic fixtures

The production JSON repository created ten isolated plans in a unique ignored `target/phase23e/acceptance-*` directory. It refused to overwrite existing plan paths. `target/phase23e/latest-acceptance-paths.txt` and Appendix B list the exact paths produced by the successful normal regression.

1. Basic annuity, positive $500,000 brokerage and 5% expected return.
2. Same Advanced case, expected unsupported NIIT-source rejection.
3. Basic annuity plus $500/month generic pension.
4. Basic three-year basis exhaustion.
5. Supported Advanced MFJ $218.88 NIIT control.
6. Basic Current / opposite-mode saved Baseline, different quoted income.
7. Advanced Current / opposite-mode saved Baseline, different quoted income.
8. Basic annuity with separate $400,000 excluded life principal and $2,000 insured premium.
9. Legacy no-annuity comparison with no explicit mode property.
10. Original saved Basic and Advanced histories for mode-switch/restart/swap inspection.

Fixture 08: Primary death January 1, 2031 stops the primary's annuity and premium; the independent spouse death-principal receipt is $400,000 gross/excluded, zero taxable principal. The premium is $2,000 in 2030. Intervals/recipient/tax qualification remain separately configured; no policy link is inferred.

## 22. Complete normal regression and verification results

**BUILD SUCCESS: 4,759 total / 4,713 passed / 46 existing skips / zero failures or errors.** Completed November 8, 2026 at 14:41:32 EST; elapsed 15 minutes 8 seconds. This adds 103 tests to the accepted 4,656-test Phase D baseline. Log: `target/phase23e/full-regression.log`.

Exact normal regression invocation (JAVA_HOME points to the verified Temurin Java 25 installation):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot'
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' `
  '-Dmaven.repo.local=C:\Users\david\IdeaProjects\retirement-planner\.codex-m2\repository' `
  '-DargLine=-Xmx4g' test
```

No test exclusions, additional skips, dependency changes or regenerated financial goldens. Existing opt-in skips remain 46; their suite/count inventory is retained in `target/phase23e/existing-skips.txt`. The complete suite includes the original pension correction, insurance principal, contractual premium, annuity/NIIT, stochastic expense, snapshot/FP compatibility, storage failure/Retry Save, cancellation, history association/swap and wizard financial-equivalence tests.

The preliminary full run reported one empty-comparison failure and twelve UI contract errors (tooltip/card ordering); those production regressions were fixed without changing the existing tests. That development log is retained as `full-regression-development.log`. Final normal regression above is a fresh run against the final source, including the legacy annuity disclosure refinement.

Visual evidence inspected: Light/Dark plan mode controls and Preferences at both sizes; original Basic/Advanced projection PDF mode disclosures and receipt/basis pages; Advanced common-mode Break-Even PDF first page. `InspectModePdfs.java` under ignored `target/phase23e` rendered the mode disclosure pages without financial recalculation. Both projection PDF mode sections are on page 16 of their 17-page examples; the Advanced final page shows $218.88 NIIT and the original basis exhaustion evidence.

Final hash, index and whitespace preservation results are recorded in `target/phase23e/final-verification.json` and the final preservation paragraph below. No production/test/resource edits occurred during the final normal regression. Per-suite totals from this run match Maven's 4,759 total; the verification excludes one preexisting October 19 Phase 9C compatibility XML artifact, which was preserved rather than deleted.

## 23. Native Windows acceptance checklist

Not yet performed by the user for Phase E.

1. Open Preferences. Verify both modes enabled; Cancel a change, then Save Advanced as the new-plan default. An existing Basic plan must remain Basic and retain its completed result.
2. Create a new plan through the wizard; verify it inherits the selected default. Restore preferred default afterward.
3. Open fixture 01. Verify Basic in Assumptions, Summary, Yearly Details and PDF; NIIT Not Modeled; brokerage growth and quoted annuity execute.
4. Open fixture 02. Run and verify explicit NIIT-source rejection; no fallback, fake failed trial, changed inputs or replacement of prior successful history.
5. Test Assumptions Apply/Cancel and change to Advanced. Apply should mark affected results outdated and should not run a projection automatically.
6. Open fixture 04: verify the three-year gross/recovery/taxable/closing-basis table in section 8. Open fixture 05: AGI $300,000, ordinary federal tax $49,468 plus NIIT $218.88; IRMAA remains separate.
7. Create a Basic annuity through the editor. Switch to Advanced; verify revalidation guidance, reopen the annuity, read/confirm NIIT assumptions and save a supported configuration. Cancel must not commit qualification changes.
8. Open fixtures 06/07. Before new comparison, inspect original saved Baseline details. Explicitly run the common-mode comparison; verify both working candidates' mode and the literal deltas in section 11. Saved Baseline mode/history must remain original.
9. Run small generated standalone and paired MC, fixed and longevity, under each admitted mode; test single/couple where applicable. Basic market risk and positive ordinary expense volatility remain usable. Advanced unclassified nonqualified growth must reject explicitly.
10. Open fixture 08 and verify independent premium/death-principal behavior. Open fixture 09 and compare original legacy outputs.
11. Open fixture 10, Save As, restart and swap both directions. Historical mode labels, federal taxes, inputs and UUID associations must remain original. View/Save both PDFs and cancel an export.
12. Inspect Light/Dark at 900×650 and 1180×820, keyboard selection and fixed footers. Verify MC, Break-Even, SS analyzers, Roth, history and PDF commands remain available in both modes.

## 24. Git preservation

Starting branch `main`, HEAD `866b2f7d0fdf25f449b63e851e667866986bc1c3`, empty index. The working tree already contained accepted Phase D changes and untracked D reports/source/tests/resources. Starting status, HEAD, branch and 1,553 file byte hashes were recorded under ignored `target/phase23e/starting-*` before E edits.

No staging, commit, push, reset, clean or revert was performed. No real retirement plan, historical golden, RNG source, dependency or build configuration was modified. Final preservation checks compare Phase E changes against those initial bytes, not against an assumed clean tree. Final whitespace/index/hash verification is recorded in section 22.

Final verification: branch and HEAD unchanged; index empty; all 1,553 original captured paths still present; zero unexpected changes outside the 53 declared existing-file modifications; all 1,326 source/test/resource hashes unchanged during final regression. Existing Phase D report hashes are unchanged. The 13 new source/test/resource paths and two Phase E reports are identified above. `git diff --check` and untracked text-file whitespace checks pass. No financial golden, original historical fixture, random-stream implementation or build dependency was changed.

## 25. Remaining limitations

Advanced is a bounded NIIT model, not comprehensive brokerage/dividend/gain recognition or transaction-level investment tax accounting. Basic's preexisting taxable-growth limitations remain, with explicit NIIT omission. Both modes retain the accepted restricted annuity contract/state scope. Missing-mode legacy files preserve legacy behavior until explicitly changed; merely changing global preferences does not modernize them.

The new ordinary common-mode comparison is transient. It preserves original durable Current/Baseline artifacts and does not claim to create a new historical projection association automatically. After restart it must be explicitly recalculated when needed. Historical viewing remains independent of that action.

## 26. Deferred functionality

No licensing, activation, entitlements, subscriptions, trials, feature restrictions, new state annuity tax treatment, comprehensive investment-income recognition, two-year IRMAA redesign, insurance contract engine, annuity pricing/purchases or later phase was implemented. Native Windows acceptance is required before proceeding beyond Phase E.

## Appendix A — Phase E source/test/resource paths

| Change | Path | SHA-256 |
|---|---|---|
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/baseline/ProjectionInputs.java` | `AEF6F498F50BE9A5FB3E6709575EF7FEDC75C6BE9C5A80A4B15230E7C10C6B6B` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/export/BreakEvenPdfExporter.java` | `D7EAA693F1CB583478AE431A3A1B80CC6D5DE8075220E95271FC3CF5D2CF1ED9` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/export/ProjectionPdfExporter.java` | `6E1598EE4B55F4D10E60BBFF411A13E0591D04BF6A9FC3C5AE33803B4EA47F55` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloAnalysisResult.java` | `A5C1FF4424AAA701D0E9DCA12C58C1B3505FA9C8B431FAD9923F0EE41EE27FB0` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloAnalyzer.java` | `C92C4FF950ECE40AE7FDB0AE5B4384B1A536D0AF4E25BDED5852070E76C8708F` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloFingerprintService.java` | `380F2C04E64BCB8C318A1DA9911DB9DEBC0BBC757E83162B5F6A569F61471FCB` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloMortalityAnalysisResult.java` | `AD3EAD167412A9450D5AA8B4B6080CD41EB94EE7AF7FAECE408245925CC87B83` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloSnapshot.java` | `2DE516916AA386CC1C0625DBAA05B847018D9EEEAEA11F6B326F78966E7DE0F6` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonAnalyzer.java` | `AEC09CE8A5707C31D9A67399996DBAD1287CBBE45A580EE28C373DDFF763B0A0` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/montecarlo/MonteCarloStrategyComparisonResult.java` | `B31A2D9757CD31BC587F1B8A4BAF25CA375CC775DED1FB68DEDE6CB101A25B85` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/projection/CompleteProjectionSnapshot.java` | `C1919895014F012674293F38966F3330FB8ECE123918CCFC984B39724044749C` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/projection/ConditionalBenefitDisclosures.java` | `A8A2179D32114631EE86A240167963CCEDC0B61CEE7CB38C0782D940DF63B397` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/projection/ConditionalBenefitExecutionSemantics.java` | `54DF7430661D633F3EAEC5BD242D501386EA5D3FEC6CC232BD287B4C97BA27AA` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/projection/ConditionalBenefitFinancialInputs.java` | `D6C40AD551C19FBD6B97D83C0B1489B6D8A79FC5F686D820214FCF7A287D367B` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedContinuationEvaluator.java` | `3E78563D66D1C3879B6C93B023861D2B929E170D18F27D50A868AA7F756FBFE1` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/LongevityWeightedIntegratedStrategyComparisonService.java` | `99F8D4061D363FD6D0DBC8030789C71C356A9C86B37916C32944F5B03A1CAE8D` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AcceptedAnalysisCompletion.java` | `D167EA4A869D48162EFD8A59EF9F23BE957453F3CC883174635204333B1CB118` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/AnalysisFingerprintService.java` | `47051B56959B36B068B9F34D98B710A40C3A15EF6B638A7FE3D7BC4C57C77540` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ConditionalBenefitAnalysisExecution.java` | `6916D0B9FE6F96EDE25E6532877940DDC3E2DB296DA0A4BF66582C2173A58BA3` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/ConditionalBenefitAnalyzerValidation.java` | `11621DF6554924B515337CEE6B80D803DB44E4C652724CF124678A56068A1B83` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/app/socialsecurity/results/RetainedProjectionSnapshot.java` | `381A4F9F1C9EE8885B631ED2EF6392FD7D4A3991E7CCFCD7D0CE225157CD4C19` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/application/settings/ApplicationSettings.java` | `4521D874B7FB9CC55DBFF9F4AD96BE1C17E940CF99D4D6B0D3D4D09D0281748C` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/application/settings/ApplicationSettingsService.java` | `FFB09C33EBD3ECBBD8BF823B64C290F27340D4A02A636068BFD2521372CCBFCB` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/baseline/BaselineProjectionService.java` | `4BFBBE58EAAF442E598DF4AD0DB4A723EFF31E402FE0E09BADCD354A97B3FD04` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/baseline/ProjectionComparison.java` | `BF9C94C19B1DAADEF7818F8D65C79EDA4C31447477F53FECA86230E4EF6D6252` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/baseline/ProjectionComparisonService.java` | `975B7C81198AEA0D905FB10CEF86B8B9024D3AA8CA1574C1669BAE38A7CB470F` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/baseline/RetirementPlanSnapshot.java` | `04630395C9EE04806CEA3C8E179F95DF0B4A9DF91DC644E9380091413BFDC2B9` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/benefit/ConditionalBenefitAdmission.java` | `E71AD068D1207EB0534E101814AF779631FAA581F6DA613F20CF2B03EB000B89` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/benefit/NonqualifiedAnnuityAdmission.java` | `D86CA3D3DA60C83AD49B4EC0D694ABA5E97EFBF9BB85023092FA3759CBC84A2C` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/benefit/NonqualifiedAnnuityTerms.java` | `65F6C84BE956EF5755A8B873F9AC1FE1B04043B8B9568DA4D7BF9968B4136D6B` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/breakeven/BreakEvenAnalyzer.java` | `A872BEDE8AE4652A6623057BCD26AC14CF60068F0D5BA9F81CBBAD820829465E` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/breakeven/BreakEvenPlanSummary.java` | `965C6FCDBB07B2D464EA93843335084530E4B3F100726E56A401877739BC71FB` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/income/IncomeSourceAttribution.java` | `92A56EC6847289141A47255887A8F4E88A17BB3EB5D4D307BCAB1719BA44777B` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/model/RetirementPlan.java` | `C5A2E0F80B5AF832F596D629F367793D41251FBF2B4424B686A8DB961044D303` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java` | `16A9C43EB076190650D9C40BC0A358ABD61B348A0EF7CD69B3750A39E5A0A6B7` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/tax/FederalTaxCalculation.java` | `967390E056FF04BBC6CB8F886A20B9F8F27C029EBA947849C03E3D8AAB322541` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/tax/FederalTaxCalculator.java` | `322FDD1C1F7E7FC59512F9D3FA46EEC990B405BFCAB9DF9DA3E758C3E262B9DB` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/tax/NetInvestmentIncomeTaxCalculator.java` | `644B0D2308E3016738BED71962A31F28A426D187E453ABE5EFD97FF7B8E6CDC3` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/iowa/IowaTaxBaseBridge.java` | `4F368DB82B8DC08DB89F12B6C7CBBB118F90D7AD73B16C23A16D1854E20F1568` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/domain/tax/state/PennsylvaniaProductionTaxCalculator.java` | `36C1DF968EB55D106775EC384CA6AF649F1600555F493E1FE67DACD7792457FE` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/ui/breakeven/BreakEvenAnalysisView.java` | `A10F06288C8328D1A63761BBC81302D1B6AEE15D87E3A27FDC9EBAD8151A626F` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java` | `CDB2DBE7B26BBF7545F47886F78E5AFCAD72AE1DFA8F77424E7508D7421CE753` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/ui/dialogs/NonqualifiedAnnuityDialog.java` | `F1D5B90D9252F6716944A72C9F26151AE10DBAD3E7A016672ED75F166187C027` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java` | `EE0D6842813E6AAAEEA54DAAB8725367B98D24A2D94388160C6BD2B2F4CA9FF3` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/ui/montecarlo/MonteCarloStrategyComparisonRunService.java` | `ABAEE74EF3613ECD37CDB80432F5DA227A0ABABF59439EFB61CBB6EDA251335E` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/ui/settings/ApplicationSettingsDialog.java` | `900D2429EC3C3BA127B7CE4EEFD6B45AD77F1F5213988CADFD2614C0FB42FC46` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/IndividualReportContext.java` | `18DE76A15EC439F33A7EC56B6F333CE943743298A62677141339C06E239D2424` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityAnalyzerInputSummary.java` | `E94B56849530208D17A0F7E4BDD65D2FB0C46DD3997DFDC852458CDDD2B594EC` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/ui/summary/ProjectionYearDetailsPane.java` | `0E586C1EC0C381CEE4E15B1F0BDBEE438C3A32A846796CA90BE8296A2C57B4E2` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java` | `A28EB2EF44390596AA229AACDFC2ED0626C7F815C3E8213A192CF96B214E71E2` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java` | `9615B31A2DE797C7017FF59F43E23ACF9B199D6A48B158274D2637EF98C9BF1F` |
| MODIFIED | `src/main/java/com/daviddunn/retirementplanner/ui/wizard/NewRetirementPlanWizard.java` | `26EF133F221AC88B98F15FE12A75512703D51837384DE13D7E7E7198DB14C5CD` |
| MODIFIED | `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/NonqualifiedAnnuityMonteCarloTest.java` | `91D9F950B3CEC5BA2608DEB84BCA90DBAA7C080529ACB6B4AB2D12D255BDDA33` |
| NEW | `src/main/java/com/daviddunn/retirementplanner/domain/model/FinancialCalculationMode.java` | `3D7C4F5713EFB2616133C9FC27DB5F4B79B12D722B4DD2A79110771EE2C4FB3F` |
| NEW | `src/main/java/com/daviddunn/retirementplanner/domain/tax/FinancialCalculationPolicy.java` | `74EC034F036D3CA701A7C8FF4A2E5F875D6A20FF9ED5F98C9003A85809D80F7D` |
| NEW | `src/test/java/com/daviddunn/retirementplanner/app/montecarlo/CalculationModeMonteCarloTest.java` | `4CBACB34A110755B6C6891AE9A306DAECC6ADC22960F9F51E6B13D23AF696579` |
| NEW | `src/test/java/com/daviddunn/retirementplanner/app/socialsecurity/CalculationModeIntegratedTest.java` | `1F6BA2E52D11CB1E3EB96F2D741D205A5A81303627964C85338AB44AD69A90FC` |
| NEW | `src/test/java/com/daviddunn/retirementplanner/domain/projection/CalculationModeAcceptanceFixturesTest.java` | `6B844C26E9C1FCD148CEECB9ECB6E25B684F491E5E6C720AB0B6B97566F7A708` |
| NEW | `src/test/java/com/daviddunn/retirementplanner/domain/projection/CalculationModeFinancialTest.java` | `A3A403FB1B807C8EDB42946DC99B28F973221A4AEDB08F0958E4C3D6DB8F1C87` |
| NEW | `src/test/java/com/daviddunn/retirementplanner/domain/projection/CalculationModeHistoryTest.java` | `0A8AE2D10AFDB0FF93FE17E11B0A5959FD3CFBE45108B61A13D695C6BDB4A3D8` |
| NEW | `src/test/java/com/daviddunn/retirementplanner/domain/projection/CalculationModeOldReaderTest.java` | `1CE97CB580503F5B4B3981486761BAEFDCDB562C6AF747BA0C627D66B988EC7E` |
| NEW | `src/test/java/com/daviddunn/retirementplanner/domain/projection/CalculationModePersistenceTest.java` | `28BFD1A34ED24B2089751917E6C541BBA2E5C9C191A2C0752309485EABA2E997` |
| NEW | `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/CalculationModeAnalyzerContractTest.java` | `BE593DB474D18EBAE410239216C7D239592A8C11538173DBF9F097B1DE4D0E52` |
| NEW | `src/test/java/com/daviddunn/retirementplanner/ui/views/CalculationModeUiTest.java` | `2E243DA23CAA487ABCF13B01E637AB2A64D19D3EE4D3B2478E9409E987A4C176` |
| NEW | `src/test/resources/phase23e/legacy/README.md` | `8E13219138DE93D4AEEA09BA07A3790468E1B44A58DE90E1E2AD3C9A41B64048` |
| NEW | `src/test/resources/phase23e/legacy/RetirementPlan.java.txt` | `DE7A0AD12122B6D69E017EF220561B8EB193168E6CAA793B5990C5BBEE60D401` |

## Appendix B — Exact native Windows fixture paths

- `C:\Users\david\IdeaProjects\retirement-planner\target\phase23e\acceptance-18097130724905291514\01-basic-brokerage.json`
- `C:\Users\david\IdeaProjects\retirement-planner\target\phase23e\acceptance-18097130724905291514\02-advanced-rejection.json`
- `C:\Users\david\IdeaProjects\retirement-planner\target\phase23e\acceptance-18097130724905291514\03-basic-pension.json`
- `C:\Users\david\IdeaProjects\retirement-planner\target\phase23e\acceptance-18097130724905291514\04-basic-basis-exhaustion.json`
- `C:\Users\david\IdeaProjects\retirement-planner\target\phase23e\acceptance-18097130724905291514\05-advanced-218-88.json`
- `C:\Users\david\IdeaProjects\retirement-planner\target\phase23e\acceptance-18097130724905291514\06-basic-comparison.json`
- `C:\Users\david\IdeaProjects\retirement-planner\target\phase23e\acceptance-18097130724905291514\07-advanced-comparison.json`
- `C:\Users\david\IdeaProjects\retirement-planner\target\phase23e\acceptance-18097130724905291514\08-insurance-and-premium.json`
- `C:\Users\david\IdeaProjects\retirement-planner\target\phase23e\acceptance-18097130724905291514\09-legacy-comparison.json`
- `C:\Users\david\IdeaProjects\retirement-planner\target\phase23e\acceptance-18097130724905291514\10-basic-advanced-history.json`

Fixture instructions and literal expectations:

`C:\Users\david\IdeaProjects\retirement-planner\target\phase23e\acceptance-18097130724905291514\README.txt`
