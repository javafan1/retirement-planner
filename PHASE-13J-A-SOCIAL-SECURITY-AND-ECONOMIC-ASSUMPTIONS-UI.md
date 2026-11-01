# Phase 13J-A — Social Security Benefit and Economic Assumptions UI

## Audit findings

The Summary tab already exposed Primary and Spouse claiming-age selectors (62–70) beside a read-only monthly own-retirement benefit. It calculates the amount with `SocialSecurityBenefitCalculator.calculateMonthlyBenefit(PIA, birthDate, claimingAge)`, rounds to cents, and formats with the application's currency formatter. The amount is explicitly expressed in the income source's benefit valuation-year dollars; the preview does not apply later COLA, spousal/survivor benefits, or deductions. Summary Apply derives the benefit start date using `SocialSecurityBenefitStartDateCalculator` and replaces the income source through its existing callback.

The Assumptions screen previously had no own-retirement claiming-age controls. Its survivor claiming-age field is a separate death-scenario assumption. The two future federal tax rate change controls were also duplicated on Summary and Assumptions. Summary's economic Apply rebuilt `TaxAssumptions` from its duplicated controls, so those controls could overwrite values maintained by Assumptions.

## Implementation

Added Primary and, when a spouse and corresponding Social Security income source exist, Spouse claiming-age selectors to a dedicated boxed `Social Security Claiming Ages & Benefits` card in Assumptions. The card uses the same `assumptions-card` styling as the other sections. Claiming-age and benefit rows share the existing responsive label/value alignment (55/45 columns at normal widths and stacked fields at narrow widths). Each selector presents the same live valuation-year monthly benefit, tooltip, accessible help, and accessible text as Summary. Summary and Assumptions now call the same presentation-only `SocialSecurityBenefitPreview` helper; the underlying benefit formula and valuation-year convention are unchanged.

Assumptions edits remain drafts. Changing an age updates only its read-only preview. Apply validates both the existing Assumptions form and Social Security age/date inputs before mutation, updates the sources and assumptions together, and notifies the established plan-change callback once. Cancel reloads the plan and discards the draft ages. The spouse row is naturally absent for single-person plans or when that person has no Social Security source. Existing MainWindow refresh behavior refreshes both views after either screen applies a change.

Removed Future Federal Tax Rate Change and Effective Year controls and their Summary-only read/apply wiring. Summary's economic apply now retains the current `TaxAssumptions` object unchanged, so these settings remain owned by Assumptions and cannot be reset when Summary is opened or saved. Both controls remain editable, validated, and persisted through Assumptions.

No Social Security, tax, projection, analyzer, persistence, fingerprint, or PDF calculation behavior was changed.

## Files changed

- `src/main/java/com/daviddunn/retirementplanner/ui/views/SocialSecurityBenefitPreview.java` — shared presentation calculation and labeling.
- `src/main/java/com/daviddunn/retirementplanner/ui/views/AssumptionsView.java` — detached claiming-age controls, preview, Apply/Cancel, and dirty-state integration.
- `src/main/java/com/daviddunn/retirementplanner/ui/views/ResultsSummaryView.java` — shared benefit helper and removal of duplicated federal-rate controls.
- `src/main/resources/css/assumptions.css` — semantic subsection heading style.
- `src/test/java/com/daviddunn/retirementplanner/ui/views/AssumptionsViewTest.java` — Primary/Spouse, single-person, independently expected benefit values, draft lifecycle, and visual preview coverage.
- `src/test/java/com/daviddunn/retirementplanner/ui/views/FutureFederalTaxRateChangePresentationTest.java` — controls remain on Assumptions and are absent from Summary.
- `src/test/java/com/daviddunn/retirementplanner/ui/views/ResultsSummarySocialSecurityPreviewTest.java` — Summary Apply preserves existing future federal tax assumptions.

## Verification

Focused command:

```text
mvn -Dtest=AssumptionsViewTest,AssumptionsLayoutTest,AssumptionsMainWindowTest,ResultsSummarySocialSecurityPreviewTest,FutureFederalTaxRateChangePresentationTest test
```

Final result: 77 tests, 0 failures, 0 errors, 0 skipped (includes the check that invalid claiming-age draft data blocks global validation before plan mutation).

The Assumptions preview test creates actual JavaFX snapshots in Light and Dark themes at 1180×820 and 900×650. Files are written under `target/phase13ja/previews/`. The separate Social Security card follows the Economic & Investment card in the wide left column and in narrow single-column order. At 900×650 it remains in the existing scrollable content flow with the stable footer visible. The 1180×820 Light and 900×650 Dark previews show the selector and valuation-year amount using aligned label/value columns and readable contrast. Existing Summary preview coverage continues to exercise Light/Dark and both sizes. These are Codex-hosted JavaFX previews; native Windows scaling and focus checks remain for acceptance.

Full suite command:

```text
mvn test
```

Result: **3,924 tests, 0 failures, 0 errors, 46 skipped; BUILD SUCCESS** (11 minutes 13 seconds). Skipped cases are the suite's existing opt-in visual/performance tests. Informational benchmark output and expected fault-injection logs appeared; no test failed. `git diff --check` completed with no whitespace errors.

### Assumptions card layout refinement

The Social Security claiming-age controls and calculated monthly benefits were moved from a subsection inside Economic & Investment Assumptions into their own bordered card. The card's rows use the same label/value column proportions and narrow-width stacking behavior as other assumption fields. Tests verify the separate card, its position in the wide column, exact row alignment, and responsive ordering. Generated card previews are `assumptions-social-security-LIGHT-1180.png`, `assumptions-social-security-DARK-1180.png`, `assumptions-social-security-LIGHT-900.png`, and `assumptions-social-security-DARK-900.png` under `target/phase13ja/previews/`.

Refinement focused test command:

```text
mvn -Dtest=AssumptionsViewTest,AssumptionsLayoutTest,AssumptionsMainWindowTest test
```

Result: **68 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Light and Dark card previews were generated and inspected. `git diff --check` completed with no whitespace errors. The complete suite was not rerun for this layout-only refinement; the full-suite result above covers the preceding Phase 13J-A implementation.

## Native Windows acceptance checklist

- Verify Primary and Spouse estimates match Summary for identical inputs, including 70→69→70.
- Confirm the displayed basis is the source's fixed benefit valuation year and the tooltip explains the amount.
- Verify changing an age updates only the draft preview; Cancel restores the saved age, while Apply updates the plan once and refreshes Summary.
- Verify single-person plans show no Spouse row.
- Change the future federal tax adjustment and effective year in Assumptions, then open/apply Summary economic settings and confirm both values remain intact; save and reopen the plan.
- Inspect both screens in Light and Dark themes at normal and reduced window sizes; verify keyboard access, tooltips, validation, and scrolling.
- Confirm the ordinary projection and existing analysis results for unchanged saved inputs remain unchanged.
