package com.daviddunn.retirementplanner.domain.breakeven;

import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;
import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxCalculation;
import com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BreakEvenInsightServiceTest {
    @Test void snapshotUsesSustainedSsYearAndOnlySharedAnnualDriverValues() {
        var baseline = List.of(year(2042, 100, 1000, 10), year(2043, 100, 1000, 20), year(2048, 100, 1000, 30));
        var current = List.of(year(2042, 0, 900, 30), year(2043, 210, 950, 40),
                year(2044, 9999, 9999, 9999), year(2048, 100, 1010, 50));
        var result = analyze(baseline, current);
        var before = result.metrics();
        var insight = new BreakEvenInsightService().prepare(result, baseline, current);
        assertEquals(2043, insight.referenceYear());
        assertEquals(new BigDecimal("-50"), insight.snapshot().get(BreakEvenMetric.INVESTABLE_ASSETS).difference());
        assertEquals(2048, result.metrics().get(BreakEvenMetric.INVESTABLE_ASSETS).sustainedBreakEvenYear());
        assertEquals(5, result.metrics().get(BreakEvenMetric.INVESTABLE_ASSETS).sustainedBreakEvenYear() - insight.referenceYear());
        assertEquals(new BigDecimal("40"), observation(insight, BreakEvenInsight.Driver.GROSS_PORTFOLIO_WITHDRAWALS).difference());
        assertEquals(new BigDecimal("40"), observation(insight, BreakEvenInsight.Driver.INVESTMENT_GROWTH).difference());
        assertEquals(new BigDecimal("40.00"), observation(insight, BreakEvenInsight.Driver.INCOME_TAXES).difference());
        assertEquals(before, result.metrics());
        assertEquals(result, analyze(baseline, current));
        assertThrows(UnsupportedOperationException.class, () -> insight.snapshot().clear());
    }

    @Test void missingDriversAreOmittedAndNoSsRecoveryUsesComparisonEnd() {
        var result = analyze(List.of(year(2042, 100, 1000, 0)), List.of(year(2042, 0, 900, 0)));
        var insight = new BreakEvenInsightService().prepare(result);
        assertEquals(2042, insight.referenceYear());
        assertTrue(insight.observations().isEmpty());
        assertEquals(new BigDecimal("-100"), insight.snapshot().get(BreakEvenMetric.INVESTABLE_ASSETS).difference());
        var empty = new BreakEvenInsightService().prepare(analyze(List.of(), List.of()));
        assertNull(empty.referenceYear());
        assertTrue(empty.snapshot().isEmpty());
        var positive = new BreakEvenInsightService().prepare(analyze(
                List.of(year(2042, 100, 900, 0)), List.of(year(2042, 100, 1000, 0))));
        assertEquals(new BigDecimal("100"), positive.snapshot().get(BreakEvenMetric.INVESTABLE_ASSETS).difference());
    }

    @Test void grossWithdrawalsDoNotAddRmdTaxFundingOrRothTransfersAgain() {
        var original = year(2042, 100, 1000, 40);
        var converted = new ProjectionYear(0, 2042, BigDecimal.ZERO, original.getInvestmentGrowth(),
                BigDecimal.ZERO, original.getSocialSecurityResult(), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("40"), new BigDecimal("20"), BigDecimal.ZERO, new BigDecimal("20"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                com.daviddunn.retirementplanner.domain.projection.HouseholdCashSettlement.zero(),
                new BigDecimal("1000"), List.of(),
                new FederalTaxCalculation(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new MichiganTaxCalculation(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                original.getMedicarePremiumCalculation(), new BigDecimal("5"), new BigDecimal("50000"),
                new BigDecimal("50000"), new BigDecimal("50000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000"), 80);
        var result = analyze(List.of(original), List.of(converted));
        var insight = new BreakEvenInsightService().prepare(result, List.of(original), List.of(converted));
        assertEquals(0, observation(insight, BreakEvenInsight.Driver.GROSS_PORTFOLIO_WITHDRAWALS).difference().signum());
        assertEquals(new BigDecimal("40"), observation(insight, BreakEvenInsight.Driver.GROSS_PORTFOLIO_WITHDRAWALS).current());
        assertEquals(new BigDecimal("50000"), converted.getRothConversion());
    }

    private BreakEvenInsight.Observation observation(BreakEvenInsight insight, BreakEvenInsight.Driver driver) {
        return insight.observations().stream().filter(value -> value.driver() == driver).findFirst().orElseThrow();
    }

    private ProjectionYear year(int year, long ss, long assets, long flow) {
        BigDecimal amount = BigDecimal.valueOf(ss);
        return ProjectionYearBuilder.aProjectionYear().withCalendarYear(year).withEndingInvestableAssets(assets)
                .withAfterTaxEstateValue(assets).withPortfolioWithdrawal(flow).withInvestmentGrowth(flow)
                .withSocialSecurityResult(new HouseholdSocialSecurityResult(amount, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, SocialSecurityBenefitSelection.OWN, SocialSecurityBenefitSelection.NONE, amount))
                .withFederalTaxCalculation(new FederalTaxCalculation(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(flow).add(new BigDecimal("0.42"))))
                .withMichiganTaxCalculation(new MichiganTaxCalculation(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("0.58"))).build();
    }

    private BreakEvenAnalysisResult analyze(List<ProjectionYear> baseline, List<ProjectionYear> current) {
        var person = new BreakEvenPlanSummary.PersonSummary("Alex", LocalDate.of(1960, 1, 1), 70);
        var people = new BreakEvenPlanSummary(person, person);
        return new BreakEvenAnalyzer().analyze(new BreakEvenProjectionSnapshot(baseline, List.of(), people),
                new BreakEvenProjectionSnapshot(current, List.of(), people));
    }
}
