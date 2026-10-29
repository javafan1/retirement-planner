# Phase 13G — Analyzer Introductions

## Architecture audit before implementation

Baseline: main at eac11e4e396c4cfbe27c174bd5552ce848efea39; working tree/index clean. AGENTS.md Maven/repository/heap rules apply.

Verified launch inventory:

1. Social Security Only — `SocialSecurityStrategyAnalyzerDialog.content`, SOCIAL_SECURITY workspace. Compares retirement and complete survivor claiming strategies by mortality-weighted Social Security value, not full-plan taxes/investments.
2. Deterministic Quick Comparison — QUICK workspace. Evaluates a small SS-ranked candidate set through the configured plan; requires compatible SS Only results. Does not perform a new integrated optimization ranking.
3. Deterministic Exhaustive Search — EXHAUSTIVE workspace. Tests the supported complete retirement/survivor universe under configured assumptions, with after-tax-estate ranking.
4. Longevity-Weighted Search — WEIGHTED workspace. Combines plan outcomes across modeled lifetimes and ranks expected discounted after-tax investable estate.
5. Monte Carlo Strategy Comparison — `MainWindow.createMenuBar` → `MonteCarloStrategyComparisonDialog`; paired Current/Saved Baseline simulations require matching household composition.
6. Monte Carlo Retirement Analysis — `MainWindow.createMenuBar` → `MonteCarloAnalysisDialog`; independent simulation of the configured current plan.

The four SS entries are choices inside one owner-modal analyzer, not four menu commands. For a single-person plan, `SinglePersonIntegratedView` instead exposes Deterministic Integrated (the initial workspace, nine ages), Social Security Only, and Longevity-Weighted Integrated. Quick Comparison is not offered. Preserve those exact titles and availability; use the same six preference identities with household-specific educational wording where applicable.

Opening configures controls/restores saved results; explicit Run buttons start calculations. Existing SocialSecurityAnalyzerJobController and Monte Carlo job/session controllers retain progress, admission, cancellation and freshness ownership. No educational dialog should invoke them. Programmatic navigation/restoration must bypass the new user-selection interception.

Preferences currently live in `ApplicationSettings`, committed through `ApplicationSettingsService` field-level synchronized updates and its bounded serial worker. `JsonApplicationSettingsRepository` validates a bounded settings document, preserves malformed/unknown documents, detects external changes and reuses AtomicFileWriter. Add only optional application preference metadata; no plan JSON changes. Reset-all is an extra detached Settings draft flag, merged only on durable Save so newer unrelated preferences/lastOpenedPlan are preserved.

`ApplicationSettingsDialog` already supports preview/rollback, asynchronous durable Save, owner modality, wrapping and semantic themes. `ApplicationThemeManager.prepare` covers new scenes/dialogs/popups. Reuse it. The unrelated StartupSplashView/ApplicationStartupCoordinator and Phase 13E progress feedback remain unchanged.

Bounded design: six stable application-level enum identifiers; shared immutable educational content, one reusable Dialog and one small FX-owned introduction/launch gate. Intercept MainWindow's three analysis menu commands and only user-initiated SS workspace requests. Track the captured owner/plan and reject late Continue after ownership changes. Preserve existing programmatic `select` semantics. Continue opens the existing configuration/results UI, never Run. Track active introductions and analyzer dialog lifetime to reject rapid duplicate launches. No new executor or general async framework.

Preference failure policy: show failure in the still-open introduction, retain the checkbox/draft, allow retry or unchecking suppression; do not launch or claim durability on failed save. Save-in-flight disables dismissal consistently with existing Application Settings. Cancel/window close before Continue never writes. No material architectural conflict requires a redesign.

Implementation, exact text, tests and visual evidence will be appended below after verification.

## Implemented architecture and lifecycle

One `AnalyzerIntroductionDialog` renders all six introductions from immutable `AnalyzerIntroductionContent`. A small FX-owned `AnalyzerIntroductionGate` holds at most one active request per host. There is no new financial runner, executor, analysis admission mechanism or startup preference system.

- MainWindow intercepts the existing three Analysis menu commands. For the SS command it introduces the workspace that actually opens: Social Security Only for couples, Deterministic Integrated for a single person.
- After the existing SS dialog is constructed, `configureIntroductions()` installs a request hook on its user-navigation routes. Sidebar buttons, the compact chooser, keyboard navigation and Quick's Go to Social Security Only action wait for Continue. Cancel leaves the selected workspace, scroll position and controls intact.
- Programmatic `AnalyzerNavigationView.select()` retains its existing behavior and does not display an introduction. Passive result restoration/history does not masquerade as a new analyzer launch.
- Continue changes to the captured workspace or opens the existing configuration/results dialog. It never presses Run. Existing Quick prerequisites, job generations, source revisions, parameter bindings and result freshness remain authoritative.
- Capture guards retain plan object identity, UUID and source revision. Monte Carlo Comparison also revalidates the captured baseline reference and existing household-composition/availability rules. A changed owner/plan cannot receive a delayed launch.
- A one-shot accepted flag rejects repeated Continue events from a dismissed dialog. The MainWindow gate remains occupied through the existing analyzer window lifetime, including nonblocking Monte Carlo dialogs. Their existing hidden/close handlers are preserved and followed by token release. SS retains its original showAndWait lifecycle; the educational dialog itself uses asynchronous show(), adding no nested event loop.
- Owner-hide and dialog-close listeners release the request and detach owner listeners. Closed introduction references are dropped before opening an analyzer. Stale preference completions check the original request token and cannot release or launch a newer request.

The full-window busy gates from prior phases remain unchanged. The educational interface is not PlanOperationFeedback or an analyzer progress screen. Previously running jobs may still complete through their existing callbacks while an introduction is visible; introductions initiate no calculation and do not cancel those jobs.

## Educational text — exact shipped copy
### SOCIAL_SECURITY_ONLY � Social Security Only

**Purpose:** Compare supported ages for starting Social Security and survivor benefits. The analysis allows for different possible lifetimes for both people.

**What you learn:** See which tested choices produce more estimated lifetime Social Security income, with future payments converted to a common dollar value so they can be compared.

**Limitations:** This compares Social Security benefits, not your whole retirement plan, taxes or investment results. Lifetime assumptions are estimates, not predictions. No choice is best for everyone. Only the supported retirement and survivor elections are tested; existing eligibility checks still apply.

### SOCIAL_SECURITY_QUICK � Deterministic Quick Comparison

**Purpose:** Using one configured financial path, compare a small set of highly ranked Social Security choices within your full retirement plan, including their complete survivor elections. A compatible Social Security Only result is required first.

**What you learn:** See how those choices compare with your current strategy for income, taxes, withdrawals and money left at the end of the plan. Candidates stay in their Social Security ranking order.

**Limitations:** This uses one configured set of financial and lifetime assumptions, not many random market paths. It does not search every claiming strategy or identify a universally best plan. Quick Comparison is not offered for single-person plans.

### SOCIAL_SECURITY_EXHAUSTIVE � Deterministic Exhaustive Search

**Purpose:** Test the supported retirement and survivor claiming combinations through your full retirement plan, using the same configured financial assumptions for each.

**What you learn:** See the tested strategies ranked by the after-tax estate measure, along with their differences from your current strategy. The ranking is a financial comparison, not advice to choose one strategy.

**Limitations:** Deterministic means using one configured path, rather than random investment scenarios. Your configured projection horizon and death assumptions matter. The search covers the supported elections, not every possible real-world strategy.

### SOCIAL_SECURITY_WEIGHTED � Longevity-Weighted Search

**Purpose:** Compare supported claiming choices through your retirement plan across different possible lifetimes. Each lifetime outcome contributes according to its modeled likelihood.

**What you learn:** See a ranking based on estimated after-tax investable money left at the end of life, converted to a common date's dollars. This differs from simply projecting to one fixed ending year.

**Limitations:** Lifetime tables and financial assumptions are estimates. Investment returns follow the configured assumptions; this is not a random-market simulation. The ranking is not a universally best claiming choice. Household outcomes use the supported survivor choices and modeled last-survivor lifetimes.

### MONTE_CARLO_COMPARISON � Monte Carlo Strategy Comparison

**Purpose:** Compare Current Plan and Saved Baseline using matched possible investment-market scenarios. A saved baseline with matching household composition is required.

**What you learn:** See how the two plans differ in modeled balances and ability to fund spending while experiencing the same scenarios. Fixed mode uses a chosen horizon; longevity-adjusted mode also models possible lifetimes.

**Limitations:** Monte Carlo means repeating the projection with different simulated conditions. These are estimates, not guaranteed outcomes or a search for the best claiming strategy. Results depend on your inputs, selected mode and supported lifetime assumptions.

### MONTE_CARLO_ANALYSIS � Monte Carlo Retirement Analysis

**Purpose:** Run your current retirement plan through many possible investment-market scenarios to explore uncertainty instead of relying on one return assumption.

**What you learn:** See a range of modeled balances and how often the plan could fund spending. Fixed mode uses a chosen horizon; longevity-adjusted mode also models possible lifetimes.

**Limitations:** Monte Carlo means repeating the projection with different simulated conditions. Results are estimates, not guarantees. They depend on your plan and selected assumptions, and do not identify a universally best investment or Social Security strategy.

### SOCIAL_SECURITY_ONLY � Social Security Only (single-person wording)

**Purpose:** Compare nine ages for starting your own Social Security retirement benefit, from 62 through 70. The analysis allows for different possible lifetimes.

**What you learn:** See which tested choices produce more estimated lifetime Social Security income, with future payments converted to a common dollar value so they can be compared.

**Limitations:** This compares Social Security benefits, not your whole retirement plan, taxes or investment results. Lifetime assumptions are estimates, not predictions. No choice is best for everyone. The single-person analysis does not model spouse or survivor benefits.

### SOCIAL_SECURITY_EXHAUSTIVE � Deterministic Integrated (single-person wording)

**Purpose:** Compare nine Social Security starting ages, from 62 through 70, by running each through your retirement plan with its configured financial assumptions.

**What you learn:** See the tested strategies ranked by the after-tax estate measure, along with their differences from your current strategy. The ranking is a financial comparison, not advice to choose one strategy.

**Limitations:** Deterministic means using one configured path, rather than random investment scenarios. Your configured projection horizon and death assumptions matter. The single-person analysis has no spouse or survivor strategies.

### SOCIAL_SECURITY_WEIGHTED � Longevity-Weighted Integrated (single-person wording)

**Purpose:** Compare supported claiming choices through your retirement plan across different possible lifetimes. Each lifetime outcome contributes according to its modeled likelihood.

**What you learn:** See a ranking based on estimated after-tax investable money left at the end of life, converted to a common date's dollars. This differs from simply projecting to one fixed ending year.

**Limitations:** Lifetime tables and financial assumptions are estimates. Investment returns follow the configured assumptions; this is not a random-market simulation. The ranking is not a universally best claiming choice. Only your own retirement benefit is modeled; there are no spouse or survivor elections.

### Shared duration text

Complex analyses may take up to an hour to complete, depending on your plan, selected assumptions, and computer performance.

Simpler analyses may finish much faster. This is a caution, not a completion-time promise or an execution limit.

## Preference identifiers and persistence

The six stable enum keys are:

| Key | Existing entry |
|---|---|
| SOCIAL_SECURITY_ONLY | Social Security Only, couple or single |
| SOCIAL_SECURITY_QUICK | Deterministic Quick Comparison, couple only |
| SOCIAL_SECURITY_EXHAUSTIVE | Deterministic Exhaustive Search / single-person Deterministic Integrated |
| SOCIAL_SECURITY_WEIGHTED | Longevity-Weighted Search / single-person Longevity-Weighted Integrated |
| MONTE_CARLO_COMPARISON | Monte Carlo Strategy Comparison, matching single/couple shapes |
| MONTE_CARLO_ANALYSIS | Monte Carlo Retirement Analysis, single or couple |

Identifiers are independent of display titles, plan UUIDs, historical AnalysisKind identities and Fixed/Longevity settings. These are six application-wide entry preferences, not a new set of the fifteen last-success analysis configurations. A shared entry's suppression applies across plans/household shapes, with contextual educational wording when shown.

The existing application settings JSON gains one optional field, for example:

```json
"suppressedAnalyzerIntroductions": ["SOCIAL_SECURITY_ONLY"]
```

An absent/null collection defaults to empty: logical showIntroduction=true for every entry. Only members have showIntroduction=false. The model stores an immutable enum set with at most six distinct known identities. Copies cannot expose mutable committed state. Existing 64 KiB document bounds and strict unknown/duplicate/trailing/unsupported-document handling remain in JsonApplicationSettingsRepository. Unknown future enum identifiers or malformed collections trigger the established protected-default/recovery behavior; the original file is not silently overwritten.

Suppression is submitted only when the user checks the checkbox and presses Continue. It uses ApplicationSettingsService's existing bounded serial worker and fresh-copy field update under the shared write lock, followed by JsonApplicationSettingsRepository/AtomicFileWriter. No disk I/O is introduced on FX. Theme, directory, auto-open and lastOpenedPlan are retained; stale settings snapshots are never written back wholesale.

No checkbox means no preference write. Cancel/Esc/window-close before Continue never changes settings. After Continue initiates a durable preference transaction, buttons and close requests are temporarily disabled, matching existing Settings Save behavior. A failed write leaves the introduction open, explains that suppression was not saved, and allows retry, Cancel, or unchecking suppression to continue without saving. No analyzer opens and no durability is claimed on failure. If the owner is forcibly hidden during the acknowledged Continue transaction, UI cleanup proceeds; a previously authorized preference write may finish, but its completion cannot launch into the closed/replaced context.

Application preferences remain in the existing user-home .retirement-planner/settings.json, separate from all plan documents, projection snapshots, financial fingerprints and analyzer result artifacts. No financial persistence schema or storage limit changed.

## Application Settings

File → Application Settings now contains Analyzer Introductions, the required explanatory sentence, and Show All Analyzer Introductions Again. The action is available even when none are suppressed.

Clicking it sets only a detached reset draft and a notice saying it will apply after Save. Save validates the existing directory/theme draft and merges the reset with the latest committed settings in one atomic update. Cancel/Esc/close discards the reset and preserves the existing theme-preview rollback. Failure retains the draft for correction/retry. A concurrent last-plan update is not lost. Later explicit suppressions remain independent serial updates.

Startup splash, branding, last-plan reopening and existing theme persistence/preview ownership were not changed.

## UI, accessibility and visual verification

The intro uses owner-scoped WINDOW_MODAL, standard Continue/Cancel button roles, a default Continue button, cancel/Escape support before saving, keyboard-focusable checkbox, and accessible suppression instructions. It centers over its owner on opening. Main launches use the main stage; workspace introductions use the existing SS analyzer stage.

The normal preferred dialog pane is 620×450 logical pixels, resizable with a 420-pixel minimum width. Text labels wrap with natural preferred heights. Only the explanatory paragraphs scroll if needed; the exact duration warning, shorter-run qualification, checkbox and actions remain outside that scroll region. There is no artificial delay, progress percentage, guaranteed one-hour limit, timer or financial computation.

ApplicationThemeManager prepares the scene and registers the owned window through existing lifecycle handling. Two narrowly scoped semantic CSS classes style the heading and amber warning. No hardcoded light background/dark text, global chart palette or PDF styling was added.

Actual JavaFX screenshots were generated for all six introductions and Application Settings in both themes with owner windows at 1180×820 and 900×650: 28 images beneath target/phase13g/previews/. Tests verify that the warning's computed bounds and natural text height fit the visible scene without scrolling.

Representative SS Only Light, Weighted Dark, Quick Light, Monte Carlo Analysis Dark, Monte Carlo Comparison Dark, and Settings Light/Dark images were inspected during development. Final compact previews were inspected again after the focused run. Text, buttons and warning are readable; the Settings reset section is visible. These are real rendered DialogPanes, not mockups or native Windows screen-reader/DPI/focus certification. More extreme resizing may require the existing scroll regions.

## Test evidence and findings

All Maven runs use IntelliJ bundled Maven, project-local .codex-m2/repository and the established 4 GiB test heap.

| Run | Total | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Existing baseline: ApplicationPreferencesTest, ApplicationSettingsUiTest, AnalyzerDirectNavigationTest | 29 | 28 | 0 | 0 | 1 |
| Final focused: baseline plus four new test classes | 51 | 50 | 0 | 0 | 1 |
| Complete Maven regression before final focus correction | 3678 | 3634 | 0 | 0 | 44 |
| Complete Maven regression, final source | 3678 | 3634 | 0 | 0 | 44 |

The focused skip is the existing opt-in navigation preview case; no test was disabled or weakened. Logs are target/phase13g/baseline.log, focused.log (initial failing duplicate-launch regression), focused-verified.log (final focused source), full-suite.log (before focus correction), and full-suite-final.log (final source).

New coverage:

- All six first-use identifiers, exact contextual titles and educational distinctions; correct intended Continue callback; checkbox focus/default/cancel button roles and actual owner modality.
- Cancel/window close with suppression checked: no launch and no write. Six independent flags, restart persistence, old settings defaults and immutable copies.
- Atomic replacement failures, protected malformed/future/oversized settings, retry, reset failure, concurrent field updates and preservation of latest lastOpenedPlan.
- Settings reset draft Cancel versus durable Save and reset availability at defaults.
- Repeated Continue/menu requests, context replacement, owner shutdown and stale asynchronous preference completion while a newer introduction is active.
- Actual MainWindow menu launches for both households; correct Monte Carlo dialog types; correct initial single/couple SS workspace; no duplicate analyzer windows.
- All four couple and three single SS workspaces, cancel/accept behavior and passive select bypass. Existing Quick readiness remains disabled without its prerequisite.
- Throwing projection engines, empty manual analysis-executor queues, unchanged job generations, frozen financial inputs, accepted snapshot identities, dirty flags and source revisions.
- Both themes, normal/reduced owner sizes, warning visibility and Settings rendering.

The initial six-variant UI regression exposed a genuine new-feature defect: a queued second Continue on the dismissed intro could reopen an analyzer while its first launch was still active. The gate now checks its one-shot accepted flag before any repeated action. All six regression variants pass.

Static review then found that requesting the already-selected workspace returned before its keyboard-focus callback ran. A new assertion reproduced this for both household shapes (target/phase13g/focus-before.log). The request hook now runs the focus callback without changing the workspace or displaying another introduction, preserving the original Home/selection behavior. Final focused tests and the complete suite pass against that final source. Test-harness compilation/selector fixes did not change financial behavior or existing assertions.

## Exact changed-file inventory

Modified production/resources:

1. src/main/java/com/daviddunn/retirementplanner/application/settings/ApplicationSettings.java
2. src/main/java/com/daviddunn/retirementplanner/application/settings/ApplicationSettingsService.java
3. src/main/java/com/daviddunn/retirementplanner/ui/MainWindow.java
4. src/main/java/com/daviddunn/retirementplanner/ui/settings/ApplicationSettingsDialog.java
5. src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerNavigationView.java
6. src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java
7. src/main/resources/css/application.css

New production:

8. src/main/java/com/daviddunn/retirementplanner/application/settings/AnalyzerIntroduction.java
9. src/main/java/com/daviddunn/retirementplanner/ui/introduction/AnalyzerIntroductionContent.java
10. src/main/java/com/daviddunn/retirementplanner/ui/introduction/AnalyzerIntroductionDialog.java
11. src/main/java/com/daviddunn/retirementplanner/ui/introduction/AnalyzerIntroductionGate.java

New tests:

12. src/test/java/com/daviddunn/retirementplanner/persistence/AnalyzerIntroductionPreferencesTest.java
13. src/test/java/com/daviddunn/retirementplanner/ui/introduction/AnalyzerIntroductionUiTest.java
14. src/test/java/com/daviddunn/retirementplanner/ui/introduction/AnalyzerIntroductionMenuTest.java
15. src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/AnalyzerIntroductionNavigationTest.java

Documentation:

16. PHASE-13G-ANALYZER-INTRODUCTIONS.md

Existing tests, financial fixtures, runtime logo assets, plan persistence, analyzer codecs, result values and PDF exporters are unchanged. Ignored target diagnostics/screenshots are excluded from any future source commit. No new dependencies.

## Native Windows acceptance checklist

1. Open a couple plan. Open the SS Analyzer: verify Social Security Only introduction, readable duration warning, Continue opens the configuration/results workspace without starting a run.
2. Select Quick, Exhaustive and Weighted through sidebar, compact chooser and keyboard. Verify the correct title/copy and only the intended workspace opens after Continue. Quick still requires a compatible SS Only result.
3. Cancel, press Escape and close an introduction with its checkbox checked. Verify the prior workspace/controls remain selected and no suppression or calculation occurs.
4. Suppress one introduction with Continue; close/reopen and restart. Verify only that entry skips its intro; all other entries still show theirs.
5. Test both Monte Carlo menu commands with a valid baseline. Verify the configuration dialog opens once, no simulation runs automatically, and repeated clicks cannot duplicate windows. Existing run/cancel/progress remains unchanged.
6. Repeat on a single-person plan. Verify initial Deterministic Integrated, primary-only SS and Weighted wording, no fabricated Quick/spouse choice, and both supported Monte Carlo entries.
7. In Application Settings, reset all then Cancel: suppressions remain. Reset and Save: all six return, including after restart. Verify theme, directory, auto-open and last-opened plan are retained.
8. Exercise damaged/unwritable settings in a safe test profile. Verify no false suppression acknowledgement, readable retry/uncheck notice, preserved original settings and usable Cancel after failure.
9. Inspect Light/Dark, 1180×820, 900×650 and 100/125/150/200% DPI. Check warning visibility, paragraph scrolling, checkbox/buttons, focus restoration, Escape, screen-reader descriptions, centering and multi-monitor behavior.
10. Verify plan dirty state/UUIDs, Current/Baseline history, existing stale/fresh classifications, analyzer parameters, startup splash and financial/PDF results are unchanged.

## Limitations and deferred scope

No new analysis category, automatic calculation, result promotion or historical migration is introduced. Suppression is per six entry families, not per plan or every internal Fixed/Longevity or household variant. Read-only historical navigation does not display introductions. All currently unsupported plan shapes/elections remain subject to their existing validation.

During a user-confirmed suppression write, dismissal is briefly disabled under the existing Settings transaction convention; a forced owner shutdown cleans up UI without canceling the already authorized metadata write. OS termination can interrupt preferences just as before; existing atomic recovery protections apply. No new shutdown/persistence framework was added.

Native Windows accessibility, focus, DPI and interaction acceptance remain pending. No subsequent phase has begun.
## Final verification and review status

Final full Maven run completed successfully on 2026-10-28 at 19:35:48 America/New_York in 11:03: 3,678 total, 3,634 passed, 44 existing opt-in/environment skips, zero failures and errors. No new exclusions, disabled tests, weakened assertions or changed golden fixtures. Final focused run completed in 30.934 seconds: 51 total, 50 passed, one existing opt-in skip. Actual JavaFX previews were generated and inspected in both themes; native Windows acceptance remains pending.

HEAD remains eac11e4e396c4cfbe27c174bd5552ce848efea39 on main. The staging index is empty. Final git diff --check passed. The exact 16-file inventory above contains all Phase 13G changes; there were no preexisting working-tree changes at the recorded baseline. No files were staged, committed or pushed. Ready for review and native Windows acceptance; no subsequent phase started.
