package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/** Pure report formatting only; differences and ranks always come from accepted outputs. */
final class AnalysisPdfData {
    static final String OUTDATED = "Historical Analysis — Outdated: These results were generated using previous retirement-plan or analysis assumptions.";
    static String money(BigDecimal value) { return value == null ? "Unavailable" : UIFormatters.money(value); }
    static String survivor(SocialSecuritySurvivorClaimingCandidate election) {
        return "Age " + election.ageYears() + "y " + election.ageMonths() + "m — " + election.claimDate();
    }
    static List<String> elections(SocialSecurityHouseholdClaimingStrategy s) {
        return List.of("Primary retirement: " + s.primaryRetirementAge() + " — " + s.primaryRetirementClaimDate(),
                "Spouse retirement: " + s.spouseRetirementAge() + " — " + s.spouseRetirementClaimDate(),
                "Primary survivor: " + survivor(s.primarySurvivorElection()), "Spouse survivor: " + survivor(s.spouseSurvivorElection()));
    }
    static List<Column> electionColumns() {
        return List.of(new Column("Original order", 55, true), new Column("SS Rank", 45, true),
                new Column("Primary retirement", 110, false), new Column("Spouse retirement", 110, false),
                new Column("Primary survivor election", 150, false), new Column("Spouse survivor election", 150, false));
    }
    static List<String> electionRow(int order, int rank, SocialSecurityHouseholdClaimingStrategy s) {
        return List.of(Integer.toString(order), Integer.toString(rank), s.primaryRetirementAge() + " — " + s.primaryRetirementClaimDate(),
                s.spouseRetirementAge() + " — " + s.spouseRetirementClaimDate(), survivor(s.primarySurvivorElection()), survivor(s.spouseSurvivorElection()));
    }
    record Metric(String label, Function<ProjectionMetrics, BigDecimal> value) { }
    static final List<Metric> METRICS = List.of(
            new Metric("Investment growth", ProjectionMetrics::totalInvestmentGrowth), new Metric("Guaranteed income", ProjectionMetrics::totalIncome),
            new Metric("Total taxes", ProjectionMetrics::totalTaxes), new Metric("Peak annual tax", ProjectionMetrics::peakAnnualTax),
            new Metric("Ending investable assets", ProjectionMetrics::endingInvestableAssets), new Metric("Ending net worth", ProjectionMetrics::endingNetWorth),
            new Metric("After-tax heir value", ProjectionMetrics::afterTaxEstate), new Metric("Portfolio withdrawals", ProjectionMetrics::lifetimePortfolioWithdrawals),
            new Metric("Roth conversions", ProjectionMetrics::lifetimeRothConversions), new Metric("RMDs", ProjectionMetrics::lifetimeRequiredMinimumDistributions),
            new Metric("Medicare premiums", ProjectionMetrics::lifetimeMedicarePremiums), new Metric("Lifetime household Social Security", ProjectionMetrics::lifetimeHouseholdSocialSecurity),
            new Metric("Lifetime primary Social Security", ProjectionMetrics::lifetimePrimarySocialSecurity), new Metric("Lifetime spouse Social Security", ProjectionMetrics::lifetimeSpouseSocialSecurity));
}
