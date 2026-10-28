# Phase 12G — SS result provenance follow-up

## Scope and finding

The confirmed 70/62 → 70/65 → swap-to-70/62 sequence correctly leaves the ordinary analyzer displaying its last-success 70/65 result, assessed Outdated. The original 70/62 result remains independently associated with Current. This follow-up clarifies that distinction; it does not promote History into the ordinary session or change compatibility policy.

Baseline: branch `main`, HEAD `e620bec4a0011ab12d15fce670bff5011093d9da`; staging index empty. Accumulated Phase 11–12 work was preserved. The previous investigation remains in `PHASE-12G-SS-ONLY-SWAP-FRESHNESS-INVESTIGATION.md`.

## Changes

- Ordinary SS/integrated persistence notices identify the displayed completed result as the **last-success analysis**, including its original run UUID. Existing classification text remains authoritative and unchanged.
- Outdated notices explain that Current's effective inputs or required analysis compatibility do not match. They do not claim every mismatch necessarily comes from a claiming age.
- Couple SS Only can show exact original-versus-Current claiming-age differences from the frozen request and frozen report context. This requires matching participant birth dates, one unambiguous Current SS record, agreement between frozen/request claim dates, and Current configured inputs matching its accepted projection. Ages are shown only for exact birthdays with consistent Current age/date; otherwise recorded date differences are shown. Missing or ambiguous records do not produce invented ages. This is explanatory presentation, not a replacement fingerprint predicate or exhaustive mismatch diagnostic.
- The shared action is now **View Current Projection's Associated Result**. Its tooltip explains read-only, calculation-free navigation, artifact verification on opening, pending pin durability, and preservation of controls and last-success slots. This shared label applies consistently wherever the existing action is used, including Monte Carlo.
- A recorded association yields an explanation of Current's separate saved analysis. Absence does not invent legacy associations. Existing artifact verification still determines whether a recorded artifact can actually open.
- Ordinary SS PDF export captures the same immutable provenance strings on FX before background rendering. Integrated ordinary report context also reuses the notice provenance. Read-only History remains labeled as the original saved result, not last-success.

## Preserved contracts

No financial algorithms, request/financial fingerprints, claiming-age dependencies, snapshot schemas, historical payloads, financial values, association identities, swap behavior, execution guards, admission rules, generation checks, or cross-analysis join restrictions changed. Pending assessment, Inputs Need Attention, compatible-but-unassociated legacy status, Current and Outdated retain their existing predicates. A source revision alone is not used as a financial mismatch. No automatic historical promotion or parameter save was added.

## Regression coverage

`SocialSecuritySwapFreshnessInvestigationTest` extends the actual production-calculated bounded fixture from the investigation. It saves A and B with independent projection UUIDs and SS runs, swaps/reloads A with throwing engines, and checks:

- ordinary restored run and durable last-success slot remain B/70/65 and Outdated;
- original A/70/62 association assesses Current independently;
- notice and PDF context identify B and show spouse age 65 versus Current 62;
- the actual button opens Current A in the existing asynchronous read-only History dialog;
- opening History preserves B and the analyzer's exact effective controls;
- PDF source strings and extracted PDF text agree.

Existing focused coverage includes all 15 association identities, equal input digest/different UUID, wrong ownership/corruption, legacy absence, pending SS restoration, Save As, restart, swap/double-swap, PDF fidelity and parameter preservation. No assertions or golden values were weakened.

Focused final run: **54 tests, 54 passed, 0 skipped, 0 failures, 0 errors**, Maven **51.930 s**. Log: `target/phase12g-provenance-focused-final.log`. An earlier intermediate compile caught two incorrect accessor/import references; both were corrected before final verification. No final focused exclusions.

Full suite: **3,577 total; 3,533 passed; 44 skipped; 0 failures; 0 errors**, Maven **10:31 min**, finished `2026-10-27T17:23:52-04:00`, exit 0 / BUILD SUCCESS. Log: `target/phase12g-provenance-full.log`. No command-line test exclusions; existing opt-in/disabled environment, capacity and preview skips remain unchanged. Current-run Surefire XML totals were checked separately, excluding stale reports from previous runs by timestamp. Skip reasons are retained in `target/phase12g-provenance-skips.txt`.

Final `git diff --check`: passed, exit 0; Git emitted existing LF/CRLF conversion advisories only. Final accumulated inventory: **45 modified tracked files, 57 untracked files, 0 staged paths**. No staging, commit, push, reset, clean or discard occurred. Financial golden resources and dependencies were not changed by this follow-up.

Both commands use IntelliJ bundled Maven, the project-local `.codex-m2/repository`, and `-DargLine=-Xmx4g`.

## Visual inspection

Generated and inspected `target/phase11c-pdf/phase12g-provenance-status.png` and `phase12g-provenance-swap-outdated-page-1.png`. The status and action are readable, long explanation text wraps, and the PDF separates original run/completion/export times and provenance from Stage 2 strategy recommendations. No clipping was visible in these previews. These are automated JavaFX/PDF previews, not native Windows/DPI/accessibility certification. Generated PDF/PNG/log evidence remains under ignored `target/` and is excluded from any commit.

## Exact follow-up file inventory

Modified in this follow-up (all already part of accumulated uncommitted work):

1. `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/AssociatedAnalyzerAction.java`
2. `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/WeightedResultPersistence.java`
3. `src/main/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecurityStrategyAnalyzerDialog.java`
4. `src/test/java/com/daviddunn/retirementplanner/ui/socialsecurity/SocialSecuritySwapFreshnessInvestigationTest.java`

Added: this report. No other source, resource, test, dependency or persistence-format changes were made by this follow-up. Complete accumulated status inventory is captured in ignored `target/phase12g-provenance-final-status.txt` and `target/phase12g-provenance-untracked.txt`; that inventory must not be mistaken for newly changed follow-up files.

## Native Windows acceptance and limitations

Automated verification succeeded. **Ready for review and native Windows acceptance; native acceptance is pending for this correction.**

1. Reproduce 70/62 A → baseline → 70/65 B → swap to A.
2. Confirm ordinary completed result identifies last-success and remains Outdated, with spouse **last-success 65; Current 62** when the records support it.
3. Choose **View Current Projection's Associated Result**; confirm the original A result opens read-only without recalculation, control changes or last-success replacement.
4. Close History and export ordinary SS PDF; confirm it still identifies B, its original assumptions/timestamps and the same provenance/difference.
5. Verify Current, Inputs Need Attention, pending, legacy-unassociated and missing/corrupt artifact states remain honest; verify Save As ownership independently.

Concrete differences are intentionally limited to safely supported SS retirement dates/ages. This is not a new general financial-input diff engine. Artifact availability is verified on opening, not inferred solely from a recorded reference. No new lifecycle contract or persistence format was introduced. No subsequent phase begun; stop for review and native acceptance.
