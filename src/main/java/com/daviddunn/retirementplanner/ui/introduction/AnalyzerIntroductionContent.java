package com.daviddunn.retirementplanner.ui.introduction;

import com.daviddunn.retirementplanner.application.settings.AnalyzerIntroduction;

/** Educational copy only; no requests, calculations or result state. */
public record AnalyzerIntroductionContent(String title, String purpose, String results, String limitations) {
    public AnalyzerIntroductionContent {
        java.util.Objects.requireNonNull(title);
        java.util.Objects.requireNonNull(purpose);
        java.util.Objects.requireNonNull(results);
        java.util.Objects.requireNonNull(limitations);
    }

    public static final String DURATION = "Complex analyses may take up to an hour to complete, depending on your plan, selected assumptions, and computer performance.";
    public static final String TIMING = "Simpler analyses may finish much faster. This is a caution, not a completion-time promise or an execution limit.";

    public static AnalyzerIntroductionContent forEntry(AnalyzerIntroduction entry, boolean single) {
        return switch (entry) {
            case SOCIAL_SECURITY_ONLY -> new AnalyzerIntroductionContent("Social Security Only",
                    single ? "Compare nine ages for starting your own Social Security retirement benefit, from 62 through 70. The analysis allows for different possible lifetimes."
                            : "Compare supported ages for starting Social Security and survivor benefits. The analysis allows for different possible lifetimes for both people.",
                    "See which tested choices produce more estimated lifetime Social Security income, with future payments converted to a common dollar value so they can be compared.",
                    "This compares Social Security benefits, not your whole retirement plan, taxes or investment results. Lifetime assumptions are estimates, not predictions. No choice is best for everyone. "
                            + (single ? "The single-person analysis does not model spouse or survivor benefits." : "Only the supported retirement and survivor elections are tested; existing eligibility checks still apply."));
            case SOCIAL_SECURITY_QUICK -> new AnalyzerIntroductionContent("Deterministic Quick Comparison",
                    "Using one configured financial path, compare a small set of highly ranked Social Security choices within your full retirement plan, including their complete survivor elections. A compatible Social Security Only result is required first.",
                    "See how those choices compare with your current strategy for income, taxes, withdrawals and money left at the end of the plan. Candidates stay in their Social Security ranking order.",
                    "This uses one configured set of financial and lifetime assumptions, not many random market paths. It does not search every claiming strategy or identify a universally best plan. Quick Comparison is not offered for single-person plans.");
            case SOCIAL_SECURITY_EXHAUSTIVE -> new AnalyzerIntroductionContent(single ? "Deterministic Integrated" : "Deterministic Exhaustive Search",
                    single ? "Compare nine Social Security starting ages, from 62 through 70, by running each through your retirement plan with its configured financial assumptions."
                            : "Test the supported retirement and survivor claiming combinations through your full retirement plan, using the same configured financial assumptions for each.",
                    "See the tested strategies ranked by the after-tax estate measure, along with their differences from your current strategy. The ranking is a financial comparison, not advice to choose one strategy.",
                    "Deterministic means using one configured path, rather than random investment scenarios. Your configured projection horizon and death assumptions matter. "
                            + (single ? "The single-person analysis has no spouse or survivor strategies." : "The search covers the supported elections, not every possible real-world strategy."));
            case SOCIAL_SECURITY_WEIGHTED -> new AnalyzerIntroductionContent(single ? "Longevity-Weighted Integrated" : "Longevity-Weighted Search",
                    "Compare supported claiming choices through your retirement plan across different possible lifetimes. Each lifetime outcome contributes according to its modeled likelihood.",
                    "See a ranking based on estimated after-tax investable money left at the end of life, converted to a common date's dollars. This differs from simply projecting to one fixed ending year.",
                    "Lifetime tables and financial assumptions are estimates. Investment returns follow the configured assumptions; this is not a random-market simulation. The ranking is not a universally best claiming choice. "
                            + (single ? "Only your own retirement benefit is modeled; there are no spouse or survivor elections." : "Household outcomes use the supported survivor choices and modeled last-survivor lifetimes."));
            case MONTE_CARLO_COMPARISON -> new AnalyzerIntroductionContent("Monte Carlo Strategy Comparison",
                    "Compare Current Plan and Saved Baseline using matched possible investment-market scenarios. A saved baseline with matching household composition is required.",
                    "See how the two plans differ in modeled balances and ability to fund spending while experiencing the same scenarios. Fixed mode uses a chosen horizon; longevity-adjusted mode also models possible lifetimes.",
                    "Monte Carlo means repeating the projection with different simulated conditions. These are estimates, not guaranteed outcomes or a search for the best claiming strategy. Results depend on your inputs, selected mode and supported lifetime assumptions.");
            case MONTE_CARLO_ANALYSIS -> new AnalyzerIntroductionContent("Monte Carlo Retirement Analysis",
                    "Run your current retirement plan through many possible investment-market scenarios to explore uncertainty instead of relying on one return assumption.",
                    "See a range of modeled balances and how often the plan could fund spending. Fixed mode uses a chosen horizon; longevity-adjusted mode also models possible lifetimes.",
                    "Monte Carlo means repeating the projection with different simulated conditions. Results are estimates, not guarantees. They depend on your plan and selected assumptions, and do not identify a universally best investment or Social Security strategy.");
        };
    }
}
