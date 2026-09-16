package com.daviddunn.retirementplanner.ui.socialsecurity;

import java.util.List;

/** Financial-objective dependencies; Quick's SS candidate-selection dependency is explained separately. */
final class SocialSecurityAnalyzerInputMatrix {
    enum Use {
        USED("Used"), NOT_USED("Not used"), REFERENCE_ONLY("Reference only"),
        PRIMARY_SURVIVES("Used when Spouse Dies"), SPOUSE_SURVIVES("Used when Primary Dies");
        private final String text;
        Use(String text) { this.text = text; }
        public String text() { return text; }
    }

    record Row(String input, Use socialSecurity, Use deterministic, Use weighted) { }

    static final String SS_OBJECTIVE = "Social Security Only: Expected present value of Social Security benefits.";
    static final String DETERMINISTIC_OBJECTIVE = "Deterministic Integrated: After-tax estate under the configured plan death scenario and configured projection horizon. Quick Comparison retains SS candidate order; only Exhaustive Search ranks by estate.";
    static final String WEIGHTED_OBJECTIVE = "Longevity-Weighted Integrated: Expected present-value after-tax investable estate across modeled household lifespans, measured at household second death. This excludes non-investable assets.";
    static final String QUICK_NOTE = "Quick Comparison uses SS-only mortality and valuation inputs for candidate selection and its SS expected-PV column. Its full-plan outcomes remain deterministic. Claim ages in all searches are candidate elections; persisted elections identify the current-plan reference/baseline.";
    static final String BASELINE_NOTE = "Survivor elections in SS-only analysis are candidate elections. Deterministic analysis uses only the surviving person's election under the configured death scenario; Both Survive uses neither. The analyzer's two survivor baseline inputs are used only for the longevity-weighted Current Strategy comparison, not SS-only requests or candidate generation.";

    static List<Row> rows() {
        return List.of(
                all("Primary DOB"), all("Spouse DOB"),
                all("Primary FRA benefit"), all("Spouse FRA benefit"),
                all("Primary retirement claim age"), all("Spouse retirement claim age"),
                new Row("Primary survivor claim age", Use.USED, Use.PRIMARY_SURVIVES, Use.USED),
                new Row("Spouse survivor claim age", Use.USED, Use.SPOUSE_SURVIVES, Use.USED),
                new Row("Current Strategy Baseline (analyzer survivor inputs)", Use.NOT_USED, Use.NOT_USED, Use.USED),
                all("Social Security COLA"),
                longevity("Primary mortality category"), longevity("Spouse mortality category"),
                longevity("Primary mortality adjustment"), longevity("Spouse mortality adjustment"),
                longevity("Mortality conditioning date"), longevity("Valuation date"),
                longevity("Real discount rate"), longevity("Mortality probability distribution"),
                plan("Projection start date"),
                new Row("Planning Horizon - end date", Use.NOT_USED, Use.USED, Use.REFERENCE_ONLY),
                new Row("Planning Horizon - calendar years", Use.NOT_USED, Use.USED, Use.REFERENCE_ONLY),
                plan("General inflation"), plan("Investment return assumptions"), plan("Expenses"),
                plan("Federal tax assumptions"), plan("State tax assumptions"),
                plan("Medicare / IRMAA assumptions"), plan("RMD assumptions"),
                plan("Roth conversion strategy"), plan("Pension assumptions"), plan("Account balances / ownership"),
                new Row("Configured plan death scenario", Use.NOT_USED, Use.USED, Use.NOT_USED),
                new Row("Configured-horizon estate", Use.NOT_USED, Use.USED, Use.NOT_USED),
                new Row("Estate at household second death", Use.NOT_USED, Use.NOT_USED, Use.USED),
                all("Ranking objective"));
    }

    private static Row all(String input) { return new Row(input, Use.USED, Use.USED, Use.USED); }
    private static Row longevity(String input) { return new Row(input, Use.USED, Use.NOT_USED, Use.USED); }
    private static Row plan(String input) { return new Row(input, Use.NOT_USED, Use.USED, Use.USED); }
}
