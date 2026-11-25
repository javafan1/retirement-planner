# Phase 6E-P3D3C2 — Arkansas native low-income refusal diagnostics

November 21, 2026. Diagnostic instrumentation and root-cause isolation.

## Executive summary

An opt-in Arkansas Basic deterministic run now emits one redacted classification when an exact `LOW_INCOME` refusal escapes the annual calculation. The classification identifies the two independent Arkansas bounds, the effective filer and death year, whether an eligible life payout was recognized, the Medicare pass, and the exact funding and Roth context. It also records whether the candidate's available cash was already below its independently calculated federal tax.

The production guard, tax calculations, funding and Roth algorithms, policy versions, candidate budgets and projection acceptance are unchanged. The diagnostic can distinguish multiple synthetic mechanisms that produced the same warning in P3D3C1. A matching native saved-plan copy or trace was unavailable, so the reported private 2028 run remains unclassified. This report includes Windows capture steps. [P3D3A architecture](PHASE-6E-P3D3A-ARKANSAS-CONDITIONAL-BENEFITS-ARCHITECTURE.md) · [P3D3C implementation](PHASE-6E-P3D3C-ARKANSAS-LIFE-PRINCIPAL-IMPLEMENTATION.md) · [P3D3C1 audit](PHASE-6E-P3D3C1-ARKANSAS-DEATH-LOW-INCOME-AUDIT.md)

## Git baseline

The audit opened on `main`, HEAD `2bedb55e99fb48aa31225e9c5549c842d77ebb2d`. The local `origin/main` tracking reference was even, with zero commits on either side and an empty index. The working tree already contained 22 modified tracked files and five untracked files from P3D3A, P3D3C and P3D3C1, including the existing Arkansas life-principal tests. The original work was preserved. Diagnostic code was added on top of the existing working tree and no Git history was changed.

## Instrumentation and exact call path

1. The controller reads the development JVM property once when starting a controlled Arkansas run and passes the resulting optional sink to the worker along with the existing frozen input document. The deterministic engine carries that sink in its existing run control. Its `finally` removes the control on every exit. Analyzer run control always uses an empty sink. [ApplicationController.java](src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java) · [ProjectionEngine.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java)
2. The annual engine selects the modeled death activity, filing status, receipts, pension, Social Security, RMD and spending withdrawals. `ArkansasProjectionValidation` captures the same facts and verified receipt evidence as before. The diagnostic capture option is carried in the same run-only funding input. [ArkansasProjectionValidation.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ArkansasProjectionValidation.java) · [ArkansasAnnualFunding.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasAnnualFunding.java)
3. An exact evaluator allocates a candidate tax withdrawal, attaches the executed sources and recomputes federal tax. `ArkansasProductionTaxCalculator` first validates annual sources and computes the stable regular estimate. At the existing positive regular tax refusal, the optional typed issue records the results of both existing comparisons separately. It can evaluate the second comparison for diagnostics even when the first failed and the actual gate short-circuited. The exact evaluator adds zero versus positive additional **tax-funding** withdrawal and compares that candidate's exact federal tax to its available tax cash. `BELOW_FEDERAL_TAX` means insufficient at that point even if Arkansas tax were zero. `COVERS_FEDERAL_TAX` leaves Arkansas sufficiency unresolved. [ArkansasProductionTaxCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasProductionTaxCalculator.java) · [ArkansasTaxIssue.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasTaxIssue.java)
4. A fixed path propagates this issue as `SOURCE_OR_TAX`. A targeted path runs the same inner exact pricing. The outer proof may independently exclude an unsupported conversion amount. When it cannot exclude the trial and the issue leaves the target path, the output is `rothPath=TARGET`, `reason=TARGET_PROOF`, and `stage=INNER_EXACT`. A configured target that fell back to the existing fixed-zero conversion path is distinguished as `TARGET_FALLBACK_FIXED` if that fixed evaluation refuses. The disposition comes from the executed branches and typed reason. [ArkansasMinimumFundingSolver.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasMinimumFundingSolver.java) · [ArkansasRothFunding.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasRothFunding.java) · [P3D2D funding audit](PHASE-6E-P3D2D-ARKANSAS-FUNDING-BUDGET-AUDIT.md)
5. The annual boundary emits only the escaping exception's exact evidence. `LOW_INCOME` originates in exact pricing. A funding interval or Roth outer box may decline a positive state floor, but those proof methods do not emit this issue. Budget exhaustion keeps its existing typed error. Every emitted trial is exploratory, including a trial during the Medicare-aware pass. A refused price cannot become a final settled candidate because it has no calculated state tax. [ArkansasJointWorksheet.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/arkansas/ArkansasJointWorksheet.java) · [ArkansasTargetBounds.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasTargetBounds.java)

The Medicare pass flag is taken from the engine's actual call. `INITIAL` uses the pre-premium spending calculation and may be the only pass. After the initial tax result establishes a positive Medicare premium, `MEDICARE_AWARE` recalculates the year's spending, conversion and tax from the same opening portfolio with the premium included. A failure in either pass prevents publication. The deceased person is inactive from January 1. The configured MFJ filing status applies through the modeled first-death year, followed by Single when one modeled household member survives. The pass flag and death category are separate fields.

## Safe output and retention

The synthetic fixed-conversion witness emitted the following shape. It describes a failed exact point before settlement. It is entirely synthetic.

```text
phase=P3D3C2
status=COMPLETE_REFUSAL
year=2028
filing=MARRIED_FILING_JOINTLY
deathYear=FIRST_DEATH_YEAR
mode=BASIC
rothPath=FIXED
pass=INITIAL
stage=INNER_EXACT
probe=EXACT
candidate=EXPLORATORY
fundingWithdrawal=ZERO
pointFederalCash=BELOW_FEDERAL_TAX
lifePrincipalRecognized=true
section301Gross=PASS
section501Net=FAIL
reason=SOURCE_OR_TAX
issue=LOW_INCOME
```

Only the fields shown are available. In a year without the reviewed ceiling proof, the two bounds are `NOT_EVALUATED`. Death timing also distinguishes `NO_MODELED_DEATH`, `BEFORE_FIRST_DEATH` and `LATER_SURVIVOR_YEAR`. `status=COMPLETE_REFUSAL` means the refusal classification finished, while the projection failed. The code keeps no per-candidate buffer. When cancellation has been observed before emission, no artifact is written. A cancellation or edit after an already-completed refusal can still make the controller ticket obsolete without invalidating the diagnostic for that failed attempt. The worker publishes the summary atomically from a marked incomplete temporary file and uses a fresh final name. Logging failure cannot replace the financial exception. The existing centered warning, year and statute explanation, previous result, and JavaFX acceptance checks continue to apply. [ArkansasLowIncomeDiagnostic.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ArkansasLowIncomeDiagnostic.java) · [ArkansasLowIncomeDiagnosticFile.java](src/main/java/com/daviddunn/retirementplanner/ui/controller/ArkansasLowIncomeDiagnosticFile.java) · [ProjectionFailureMessages.java](src/main/java/com/daviddunn/retirementplanner/ui/ProjectionFailureMessages.java)

No amounts, names, account or policy identifiers, timestamps, plan paths, or raw exceptions enter the output payload. The only stored diagnostic is the redacted text file in ignored `target/phase6e-p3d3c2/`. The writer registers no persistent preference. Frozen inputs, annual evidence, archive readers, currentness and fingerprints do not use the diagnostic. The earlier accepted result remained installed in a controlled worker regression and its immutable snapshot was retained. Existing provenance and history coverage ran alongside the new tests. This audit used no private saved-plan data.

## Windows capture

Diagnostics are off without the JVM option. The following steps use an isolated profile and a read-only copy inside the ignored directory. Start PowerShell from the repository root with Java 25 on `PATH`.

1. Build with IntelliJ's bundled Maven and the configured workspace-local repository. Run `-o -B test` with `-Dtest=ArkansasLowIncomeDiagnosticsTest` if its Surefire report is missing or the checkout has changed. This compiles the application and records the current JavaFX and application classpath. An IntelliJ run configuration can alternatively launch `com.daviddunn.retirementplanner.ui.MainApplication` with the usual JavaFX settings, the repository root as its working directory, and VM option `-Daledrix.diagnostics.arkansasLowIncome=true`.
2. Make and open a read-only copy. Run the following commands to select the source interactively, then launch the diagnostic application. Keep the prompt entry and the copy local.

```powershell
$auditDir = (New-Item -ItemType Directory -Force target/phase6e-p3d3c2).FullName
$copy = Join-Path $auditDir ('read-only-' + [guid]::NewGuid() + '.json')
Copy-Item -LiteralPath (Read-Host 'Saved plan path') -Destination $copy
(Get-Item -LiteralPath $copy).IsReadOnly = $true
$profile = (New-Item -ItemType Directory -Force (Join-Path $auditDir 'capture-profile')).FullName
$xml = Get-ChildItem target/surefire-reports -Filter 'TEST-*ArkansasLowIncomeDiagnosticsTest.xml' | Select-Object -First 1
[xml]$surefire = Get-Content -Raw -LiteralPath $xml.FullName
$entries = ($surefire.testsuite.properties.property | Where-Object name -eq 'surefire.test.class.path').value -split ';'
$modules = ($entries | Where-Object { $_ -match '[\\/]org[\\/]openjfx[\\/].+-win\.jar$' }) -join ';'
$appClasspath = ($entries | Where-Object { $_ -notmatch '[\\/]org[\\/]openjfx[\\/]|[\\/]target[\\/]test-classes$' }) -join ';'
$previousLocalData = $env:LOCALAPPDATA
try {
    $env:LOCALAPPDATA = $profile
    & java "-Duser.home=$profile" '-Daledrix.diagnostics.arkansasLowIncome=true' `
        --module-path $modules --add-modules javafx.controls --enable-native-access=javafx.graphics `
        -cp $appClasspath com.daviddunn.retirementplanner.ui.MainApplication
} finally {
    $env:LOCALAPPDATA = $previousLocalData
}
```

3. Use **File → Open** for the local copy at `$copy`. For a copy without the death scenario, first reproduce the known successful run and apply the original modeled death in memory. Use **Recalculate Projection** for the affected configuration. If the original refusal occurs, the prior successful projection can remain visible with the usual warning. Close the diagnostic launch and discard edits when prompted.
4. View the new `low-income-*.txt` in `target/phase6e-p3d3c2/` and share only its safe contents if needed. A successful run or a different typed failure creates no low-income file. After that one-time capture, launch without the JVM option. Neither the switch nor the artifact is stored in the plan. If an IntelliJ configuration is used, remove the VM option from its local settings after the session.

The matching native plan was unavailable. The opt-in file path was exercised on a synthetic Windows controlled-worker run. The Java 25 launcher and classpath passed `java --dry-run` on Windows. The reported private run still needs the interactive acceptance step.

## Arkansas eligibility and life-principal evidence

For every otherwise stable positive estimate, the active guard requires both strict inequalities. Let `A` be reconciled federal AGI less federally taxable Social Security. Let `E` be the sum of each recipient's possibly eligible retirement receipts, with a separate $6,000 cap for each. Let `D` be $2,470 times the number of applicable filers. The gross proof checks `A > Cg`, and the net proof checks `max(0, A − E − D) > Cn`. The 2028 gross ceilings are $24,559 Single and $39,292 MFJ. The net ceiling is $30,200. They are modeled outward proof ceilings, with equality refusing. They do not represent actual 2028 DFA tables. [ArkansasIncomeReconciliation.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasIncomeReconciliation.java) · [ArkansasTaxRules.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/arkansas/ArkansasTaxRules.java) · [P3D3C1 legal and proof findings](PHASE-6E-P3D3C1-ARKANSAS-DEATH-LOW-INCOME-AUDIT.md)

The existing immutable life evidence rebuilds expected receipts from the frozen definition, death state, year and household. Reconciliation checks actual sources against that evidence before reaching the low-income guard. The guard has a separate gross eligibility input, so a future reviewed proof could add a verified life receipt once there without changing the ordinary base, state net, or federal income. Arkansas DFA treats qualifying lump-sum policy proceeds as nontaxable income, and the §301 worksheet expressly counts income from all sources for eligibility. Including paid principal in that specific measure is a supported reading of those authorities. A focused DFA confirmation of the qualified category and return association would be prudent before broadening admission. The §501 credit uses net taxable income and the excluded payment has no direct effect on that measure. [ArkansasTaxEvidence.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasTaxEvidence.java) · [DFA 2025 return instructions](https://www.dfa.arkansas.gov/wp-content/uploads/2025_AR1000F_and_AR1000NR_Instructions.pdf) · [DFA Subject 209](https://www.dfa.arkansas.gov/wp-content/uploads/209-NontaxableIncome.pdf)

In one synthetic MFJ death-year test, $38,000 of taxable conversion with no age-qualified retirement pool produces `A=$38,000` and `Nlo=$33,060`. A $100,000 verified receipt would make a gross-only lower bound of $138,000, clearing both proof ceilings without changing eligible retirement amounts or the tax base. Current diagnostics correctly emit `FAIL/PASS`. In the synthetic $30,000 survivor pension plus $10,000 fixed conversion case, the existing gross bound already passes but `Nlo=$29,060`, so even a verified payout cannot close the net proof. Current diagnostics emit `PASS/FAIL`.

## Matched synthetic death and benefits

The 2028 MFJ comparison holds constant the opening IRA capacity, Basic mode, $60,000 employer pension while the insured is living, $30,000 survivor pension, zero Social Security, zero RMD, and no scheduled Roth conversion. An eligible policy pays $100,000 to the surviving spouse. Separate sets hold ordinary expenses at $0 or $70,000. Both sets run the actual controlled Arkansas deterministic engine.

| Case | $0 spending | $70,000 spending |
|---|---|---|
| A. Both alive, qualifying definition present | No receipt. $60,000 pension, both bounds clear, completes. | No receipt. $10,000 taxable IRA spending withdrawal, plus IRA tax funding, completes. |
| B. First modeled death, no policy | $30,000 pension, no receipt. $30,000 assured cash. `FAIL/FAIL`, fixed/initial, exact zero tax-funding probe, refuses. | $30,000 pension and $40,000 taxable IRA spending withdrawal, plus IRA tax funding. Both bounds clear, completes. |
| C. Same death, policy pays | $130,000 assured cash, including excluded payout. `FAIL/FAIL`, fixed/initial, exact zero tax-funding probe, refuses. | $130,000 assured cash covers spending with no IRA withdrawal. `FAIL/FAIL`, fixed/initial, exact zero tax-funding probe, refuses. |

For an opening 2028 projection after a 2027 death, the controlled test flags `LATER_SURVIVOR_YEAR`, `SINGLE`, and no payment. A $30,000 survivor pension has `A=$30,000` and `Nlo=$21,530`, yielding `PASS/FAIL`. A later untriggered death produces `BEFORE_FIRST_DEATH`. Neither the filing label nor a benefit definition alone is used to infer a payment.

For the 2028 targeted case, the survivor has a $30,000 pension and the excluded receipt, with only $10,000 of conversion capacity. The top 12% target probe propagates `TARGET_PROOF/LOW_INCOME` after an inner exact zero tax-funding withdrawal, `PASS/FAIL`. A separate capacity-sufficient control completes the same target with $103,000 in actual conversion. The reference and opt-in executions agree on annual evidence, input digest, provenance and target metrics. The limited-capacity trace proves a target proof stopped. It does not establish a feasible lower optimum.

In the Medicare case, the $19,440 survivor pension and $100,000 payout cover $119,440 in ordinary obligations. The initial pass succeeds with a zero regular state estimate. The older survivor's Medicare premium adds a same-year IRA spending withdrawal, making the regular estimate positive on the premium-aware pass. The trace classifies `MEDICARE_AWARE`, `FAIL/FAIL`, and **zero tax-funding withdrawal**. The Medicare expense is funded in the earlier spending allocation. The younger control incurs no Medicare premium and completes with zero state tax and zero discretionary spending withdrawal. This demonstrates a second-pass refusal without changing the policy receipt.

## Exploratory funding proof and next phase

The exact-point witness combines the $100,000 payout and $30,000 survivor pension against $130,000 of spending, leaving zero non-withdrawal tax cash. Its fixed $11,000 conversion raises AGI to $41,000. At the initial zero tax-funding probe both the production path and the lower-level evaluator identify §301 `PASS`, §501 `FAIL` and `BELOW_FEDERAL_TAX`. The production solver stops after one price and no interval proof. Independent point pricing records the same classification at $140.00 with a **positive** requested tax withdrawal. That point is driven by the test oracle, since the production traversal has already stopped. At $140.01 both bounds clear. The first sufficient cent is $1,815.78, with $1,061.578 federal tax, $754.20 state tax and $0.002 surplus. The preceding cent leaves a $0.007 deficit.

The mathematical certificate uses one reachable ordinary IRA prefix and one already saturated recipient pool. Every withdrawal below $880 is insufficient because the initial federal tax alone is $880, federal tax rises with the same taxable funding source, and Arkansas liability cannot be negative. The entire ambiguous net eligibility prefix ends at $140. On the relevant remaining interval, total deficit follows `880 + 0.10w + roundCents(687.02 + 0.037w) − w`, which falls with successive cents. The two exact neighboring prices prove the synthetic minimum. The diagnostic's federal comparison at **one** exact point does not prove this interval condition or authorize skipping. For a native target, eliminating a cash-infeasible exact funding point does not by itself eliminate the conversion amount or settle target optimality. [P3D3C1 independent numerical audit](PHASE-6E-P3D3C1-ARKANSAS-DEATH-LOW-INCOME-AUDIT.md) · [P3D2E capped exclusion proof](PHASE-6E-P3D2E-ARKANSAS-CAPPED-EXCLUSION-PROOF.md) · [P3D2G source reachability proof](PHASE-6E-P3D2G-ARKANSAS-SOURCE-REACHABILITY-PROOF.md)

The first decision in review is to obtain the redacted native artifact. If it reports a gross-only failure with an eligible receipt, investigate a scoped §301 receipt lower bound and document the legal confirmation. If it reports an unaffordable exact candidate, establish a native or representative prefix proof and request a separate bounded solver phase. If a potentially fundable candidate's §501 net measure remains unresolved, determine whether an independent exclusion from the credit is provable. A tax implementation phase would be needed for genuinely material unresolved relief under the accepted future-year assumptions. A Medicare-aware classification directs the audit to the actual premium-aware spending and conversion, without assuming a different tax rule. The native cause remains open until the file identifies the affected route and bounds. The three existing policy identities `AR-LIFE-PRINCIPAL-P3D3C-1`, `AR-FUNDING-P3D2E-1` and `AR-TARGET-P3D2G-1` are unchanged.

## Verification and files

IntelliJ bundled Maven and Java 25 used `-Dmaven.repo.local` for the configured repository-local `.codex-m2/repository` plus `-o -B test`. A focused initial domain compilation and test check passed 36 tests, exit 0. During development, two runs exited 1. The first found an unsupported test-only pension setter. The second found that the younger control lacked a senior credit and already crossed the positive regular tax threshold. The test fixtures were corrected without changing engine behavior. The final focused run covered 181 tests with zero failures, errors or skips, exit 0. It included actual Windows JavaFX presentation, cancellation and archive coverage. A full or exhaustive suite was outside this diagnostic phase's verification needs.

| Focused suite | Passed |
|---|---:|
| ArkansasLowIncomeDiagnosticsTest, ArkansasLowIncomeExactFundingDiagnosticsTest | 12 + 1 |
| ArkansasLifePrincipalProjectionTest, ArkansasLifePrincipalReconciliationTest | 18 + 2 |
| ArkansasDomainCalculationTest, ArkansasProjectionIntegrationTest | 36 + 15 |
| ArkansasAccountFundingTest, ArkansasCappedExclusionProofTest, ArkansasFundingCounterexampleTest | 16 + 14 + 5 |
| ArkansasSourceReachabilityTest, ArkansasTargetCertificationTest | 16 + 18 |
| ProjectionFailureMessagesTest, ArkansasProjectionFailureUiTest, ArkansasControlledWorkflowTest | 5 + 6 + 10 |
| ArkansasFundingProvenanceTest, LifeInsuranceHistoryTest | 3 + 4 |

Tests verify one bounded refusal with opt-in, legacy behavior with the option off, absence of a trace on a successful both-bounds-pass case, matching successful financial evidence and identities, isolation on simultaneous runs on one engine, cancellation inside funding, and preservation of the prior accepted projection. The default focused run wrote zero diagnostic files. The native setting was separately exercised with `-Daledrix.diagnostics.arkansasLowIncome=true` and `-Dtest=ArkansasControlledWorkflowTest#lowIncomeDiagnosticOnTheControlledWorkerPreservesTheAcceptedProjection`. This repeated one test and wrote one new safe file, exit 0. No incomplete diagnostic file remained. JavaFX emitted its existing test runtime warnings. `git diff --check` and `git diff --cached --check` passed. No files were staged, committed or pushed.

| File | Phase changes |
|---|---|
| [ArkansasLowIncomeDiagnostic.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ArkansasLowIncomeDiagnostic.java) | Typed, bounded, safe summary and formatting. |
| [ArkansasLowIncomeDiagnosticFile.java](src/main/java/com/daviddunn/retirementplanner/ui/controller/ArkansasLowIncomeDiagnosticFile.java), [ApplicationController.java](src/main/java/com/daviddunn/retirementplanner/ui/controller/ApplicationController.java) | Explicit native opt-in, scoped worker wiring and local artifact. |
| [ProjectionEngine.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ProjectionEngine.java), [ArkansasProjectionValidation.java](src/main/java/com/daviddunn/retirementplanner/domain/projection/ArkansasProjectionValidation.java) | Annual context, run-control lifetime and reporting at the escaping refusal. |
| [ArkansasProductionTaxCalculator.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasProductionTaxCalculator.java), [ArkansasTaxIssue.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/state/ArkansasTaxIssue.java), [ArkansasAnnualFunding.java](src/main/java/com/daviddunn/retirementplanner/domain/tax/ArkansasAnnualFunding.java) | Optional evidence through the existing issue, using the unchanged comparisons and exact funding values. |
| [ArkansasLowIncomeDiagnosticsTest.java](src/test/java/com/daviddunn/retirementplanner/domain/projection/ArkansasLowIncomeDiagnosticsTest.java), [ArkansasLowIncomeExactFundingDiagnosticsTest.java](src/test/java/com/daviddunn/retirementplanner/domain/tax/ArkansasLowIncomeExactFundingDiagnosticsTest.java), [ArkansasControlledWorkflowTest.java](src/test/java/com/daviddunn/retirementplanner/ui/controller/ArkansasControlledWorkflowTest.java) | Synthetic reproduction, arithmetic, worker, cancellation and evidence-invariance tests. |
| This report | Classification, capture instructions and approval branches. |
