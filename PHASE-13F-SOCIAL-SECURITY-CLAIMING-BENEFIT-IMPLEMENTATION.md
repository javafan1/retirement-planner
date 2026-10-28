# Phase 13F — Social Security Claiming Benefit Implementation

## Summary

Implemented the approved presentation-only correction. Summary's primary and spouse monthly benefit previews now use one calculation path in the configured benefit-valuation-year dollars. Startup, restored projections, interactive claiming-age changes and refreshes no longer switch between base-year and future claim-year amounts.

Baseline: branch `main`, HEAD `84ec5e03f73682d691007f68fff10f86105afd98`. The preexisting Phase 13F audit report was preserved. No financial engine, claiming formula, analyzer, fingerprint, plan schema, snapshot payload, PDF exporter, dependency or financial golden resource changed.

## Root cause

`ResultsSummaryView.updateSocialSecurityPreview` previously selected a saved, uninflated `IncomeSummary` amount when restored inputs and the selected age matched the accepted plan. For other ages it constructed a temporary `SocialSecurityIncome` and inflated its benefit through the claim year. This compared different dollar bases inside the same row. It also left the restored-only tooltip attached after a calculated preview replaced that value.

The audit explains the evidence and limits in `PHASE-13F-SOCIAL-SECURITY-CLAIMING-BENEFIT-AUDIT.md`. The user's exact $4,788/$5,192 pair was not recalculated from a personal plan; all implementation verification uses synthetic fixtures.

## Exact correction and valuation contract

`updateSocialSecurityPreview` now always calls the existing authoritative integer-age `SocialSecurityBenefitCalculator.calculateMonthlyBenefit` with the row's original FRA amount, birth date and selected age. It rounds to cents with the existing `HALF_UP` presentation convention before using the existing whole-dollar UI formatter. No COLA is added to this preview; the supplied FRA amount already has its configured benefit valuation year.

Each row visibly states:

```text
Monthly Benefit
(2026 dollars)
```

The year comes from that row's `SocialSecurityIncome.getBenefitValuationYear()` and stays fixed during age selection. Primary and spouse may have different configured years; no common year is invented. The tooltip explains that this is a monthly own-retirement election amount, with claiming-age adjustment, without COLA after that valuation year. It distinguishes this from the household payment with spousal/survivor benefits or a deposit after deductions. Accessible help states the same basis, and accessible text tracks the selected age, amount and year. Missing age or birth date retains the unavailable dash presentation.

Initialization and the existing ComboBox listener call the same method. There is no restoration-only benefit branch and no new settings or COLA input. The preview uses the specific source represented by the row rather than an aggregated income summary. Existing Apply behavior is unchanged: selecting ages remains a UI draft; Apply updates the configured election using the established callback. Rendering or selecting a preview does not change the plan, dirty flag, financial revision, snapshot identity or original results.

Annual projections, analyzers and historical PDFs continue applying their established payment-year/valuation rules. No stored income summary is rewritten to make it resemble this preview.

## Before and after

For the audit reference fixture (both sources valued in 2026 dollars, primary FRA amount $4,000, spouse $2,000, economic COLA 2.5%):

| State/selection | Before | After |
|---|---:|---:|
| Fresh primary, age 70 | $5,896 | $4,960 |
| Fresh primary, age 69 | $5,381 | $4,640 |
| Restored primary, saved age 70 | $4,960 | $4,960 |
| Restored primary, select 69 | $5,381 | $4,640 |
| Restored primary, return to 70 | $4,960 | $4,960 |
| Fresh spouse, age 62 | $1,435 | $1,400 |
| Restored spouse, saved age 62 | $1,400 | $1,400 |
| Spouse, age 70 | $3,097 | $2,480 |

New consistent expected values before whole-dollar formatting:

| Age | Primary monthly | Spouse monthly |
|---|---:|---:|
| 62 | $2,800.00 | $1,400.00 |
| 63 | $3,000.00 | $1,500.00 |
| 64 | $3,200.00 | $1,600.00 |
| 65 | $3,466.67 | $1,733.33 |
| 66 | $3,733.33 | $1,866.67 |
| 67 | $4,000.00 | $2,000.00 |
| 68 | $4,320.00 | $2,160.00 |
| 69 | $4,640.00 | $2,320.00 |
| 70 | $4,960.00 | $2,480.00 |

These are independently tabulated FRA-67 expectations. UI assertions compare explicit expected text as well as cent-level expected calculator values, rather than merely checking monotonicity. The JUnit fixture also deliberately uses different primary/spouse valuation years (2026/2027), and alternate plans use 2020/2021, to prove each source retains its own basis.

The original temporary audit probe was rerun against the corrected production classes, with a separate fixture directory, preserving the original audit evidence. All recorded startup/restoration/reload/switch sequences now agree. Its independently checked 2034 annual results remain **$72,519.36 primary** and **$20,469.12 spouse**. See `target/phase13f-implementation/audit-probe-after.log`; the before evidence remains in `target/phase13f/probe.log`.

## Exact incremental file inventory

Modified production:

- `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java`: explicit year caption, one source-specific valuation-year preview, refreshed tooltip/accessibility text; remove restoration-summary and claim-year-COLA display branches.

New tests:

- `src/test/java/com/daviddunn/retirementplanner/ui/views/ResultsSummarySocialSecurityPreviewTest.java`: seven JUnit invocations covering all ages for both owners; zero/2.5%/10% economic COLA; deliberately conflicting legacy source COLA; unchanged accepted results/context; single/couple save/reload and A70/B69/A70 switching; source-specific versus aggregated legacy summary; Light/Dark UI previews.

New documentation:

- `PHASE-13F-SOCIAL-SECURITY-CLAIMING-BENEFIT-IMPLEMENTATION.md` (this report).

Preserved, excluded from the implementation delta:

- `PHASE-13F-SOCIAL-SECURITY-CLAIMING-BENEFIT-AUDIT.md`, which was already untracked at implementation entry.
- All other existing repository files.

Ignored diagnostics, screenshots, fixture JSON and logs are beneath `target/phase13f-implementation/`; no personal plan/settings files were used or modified.

## Regression and financial isolation

The new tests verify:

- Initial controls and edits use the same fixed-year amounts, including 70→69→70 and every age 62–70 for both people.
- Tooltip and accessible text identify the row's valuation year and do not retain the old restored-only explanation.
- Economic COLA changes do not alter a fixed-year preview; underlying accepted annual benefits still follow their independently expected COLA values.
- Complete input documents, accepted snapshot references/UUIDs, annual results, cached summaries, dirty flags and source revisions remain unchanged by preview edits.
- A throwing `ProjectionEngine` prevents unnoticed projection execution during rendering, edits and restored reload/switch workflows.
- Original annual payloads/identities survive Save/reload and plan switching for couple and single-person plans.
- A supported couple legacy multiple-source shape does not substitute its $6,200 aggregated primary summary for the $4,960 first-source preview. Single-person multiple-source projections remain unsupported as before; an initial test-fixture attempt correctly encountered that existing validation, so the regression uses the supported couple compatibility path without changing any engine rule.

No existing tests were weakened or removed. Before the correction, the new test reproduced the incorrect $5,896 (and $9,666 at 10% COLA) fresh age-70 labels instead of $4,960; the zero-COLA case exposed the absent basis tooltip. These intentional failures are recorded in `target/phase13f-implementation/before.log`.

## Automated verification

Used IntelliJ bundled Maven with project-local `.codex-m2/repository` and `-DargLine=-Xmx4g`.

| Run | Total | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Focused new UI plus benefit calculator/date, source income/COLA, projection provider, income summary, snapshot persistence and SS/Quick PDF tests | 77 | 77 | 0 | 0 | 0 |
| New UI tests repeated after strengthening initial-selection/accessibility assertions | 7 | 7 | 0 | 0 | 0 |
| Complete Maven regression | 3656 | 3612 | 0 | 0 | 44 |

Complete regression: BUILD SUCCESS, exit 0. The 44 skips are preexisting opt-in/resource-dependent diagnostic, capacity and visual-preview cases. No new skip annotations or test exclusions were added.

The repeated seven tests overlap the focused run. Logs: `focused.log`, `focused-new-final.log`, `full-suite.log` under `target/phase13f-implementation/`. The unchanged audit probe independently completed successfully. Financial golden files were not regenerated. No PDF financial values or print styling changed.

## Visual inspection

Generated actual JavaFX Summary and Social Security card snapshots at 1180×820 and 900×650, using the existing application theme manager:

- `social-security-{LIGHT,DARK}-{1180,900}.png`
- `summary-{LIGHT,DARK}-{1180,900}.png`

Location: `target/phase13f-implementation/previews/`.

Inspected all four Social Security card previews, the Light/1180 full Summary and the Dark/900 full Summary. The year caption, age controls and dollar values are readable in both themes; the source-specific years remain distinct. No CSS changes were needed. At 900×650 the existing assumptions pane requires vertical scrolling to reach the lower card/actions. The full Summary also retains its existing chart scrolling/layout behavior; this correction does not redesign it. These are rendered JavaFX previews, not native Windows DPI or screen-reader certification.

## Limitations and native Windows acceptance

1. Reopen the user's saved plan: verify the primary's configured age-70 benefit remains consistent with its labeled valuation year. The exact personal dollar amounts require native confirmation.
2. Change primary 70→69→70, then 62–70; verify the amount follows the fixed-year adjustment without a reference-basis jump.
3. Repeat for spouse and single-person plans, including a different spouse valuation year.
4. Verify fresh recalculation, Save/restart and switching plans retain the same displayed basis. Unapplied age drafts must not be persisted by ordinary Save.
5. Change economic COLA: this preview should retain its fixed-year values, while a deliberately requested new projection continues using the configured COLA. Verify no projection is initiated merely by selecting an age.
6. Hover benefit labels and check keyboard/screen-reader descriptions; verify no stale “Original accepted income summary” explanation appears after edits.
7. In Light/Dark themes at 1180×820, 900×650 and normal Windows scaling, verify captions/values are readable and the existing assumptions scrollbar reaches all controls.
8. Check current/baseline history and analyzer/PDF results remain their original values. Verify preview edits do not alter dirty state until Apply is used.

The preview remains an own-retirement estimate using the existing integer-age convention, not a household spousal/survivor total, net deposit or exact administrative first-payment schedule. This phase does not change those financial contracts. Native Windows acceptance is pending; stop for review after verification.

## Final repository verification

`git diff --check` passed (exit 0; the existing LF-to-CRLF conversion warning is informational). Branch/HEAD remain unchanged and the staging index is empty. The tracked production delta is only `ResultsSummaryView.java`; the new test and implementation report are listed above. The preexisting untracked audit report was preserved. No files were staged, committed, pushed, reset, cleaned or discarded. Ready for review and native Windows acceptance; no subsequent phase begun.
