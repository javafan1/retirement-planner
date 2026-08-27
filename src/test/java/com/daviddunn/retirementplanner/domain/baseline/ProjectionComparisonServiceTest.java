package com.daviddunn.retirementplanner.domain.baseline;

import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.daviddunn.retirementplanner.testutil.FederalTaxCalculationBuilder.aFederalTaxCalculation;
import static com.daviddunn.retirementplanner.testutil.MichiganTaxCalculationBuilder.aMichiganTaxCalculation;
import static com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder.aProjectionYear;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectionComparisonServiceTest {

    private final ProjectionComparisonService service =
            new ProjectionComparisonService();

    @Test
    void calculatesLifetimeMetricsAndFinalYearValues() {

        Projection baseline = projection(
                year(2040, 100, 500, 20, 5, 1_000, 900),
                year(2041, 200, 600, 30, 10, 1_200, 1_050),
                year(2042, 300, 700, 25, 5, 1_400, 1_250));

        Projection current = projection(
                year(2040, 150, 550, 15, 5, 1_100, 1_000),
                year(2041, 250, 650, 50, 20, 1_300, 1_150),
                year(2042, 350, 750, 20, 5, 1_600, 1_450));

        ProjectionComparison comparison = service.compare(
                baseline,
                List.of(nonInvestable(2042, 400)),
                current,
                List.of(nonInvestable(2042, 500)));

        assertEquals(new BigDecimal("600"), comparison.getBaselineInvestmentGrowth());
        assertEquals(new BigDecimal("750"), comparison.getCurrentInvestmentGrowth());
        assertEquals(new BigDecimal("1800"), comparison.getBaselineTotalIncome());
        assertEquals(new BigDecimal("1950"), comparison.getCurrentTotalIncome());
        assertEquals(new BigDecimal("95"), comparison.getBaselineTotalTaxes());
        assertEquals(new BigDecimal("115"), comparison.getCurrentTotalTaxes());
        assertEquals(new BigDecimal("40"), comparison.getBaselinePeakAnnualTax());
        assertEquals(new BigDecimal("70"), comparison.getCurrentPeakAnnualTax());
        assertEquals(new BigDecimal("1400"), comparison.getBaselineEndingInvestableAssets());
        assertEquals(new BigDecimal("1600"), comparison.getCurrentEndingInvestableAssets());
        assertEquals(new BigDecimal("1800"), comparison.getBaselineNetWorth());
        assertEquals(new BigDecimal("2100"), comparison.getCurrentNetWorth());
        assertEquals(new BigDecimal("1250"), comparison.getBaselineAfterTaxEstate());
        assertEquals(new BigDecimal("1450"), comparison.getCurrentAfterTaxEstate());
        assertEquals(new BigDecimal("150"), comparison.getInvestmentGrowthChange());
        assertEquals(new BigDecimal("20"), comparison.getTotalTaxesChange());
    }

    @Test
    void peakAnnualTaxCanOccurBeforeFinalYear() {

        Projection projection = projection(
                year(2040, 0, 0, 100, 20, 0, 0),
                year(2041, 0, 0, 10, 5, 0, 0));

        ProjectionComparison comparison = service.compare(
                projection, List.of(), projection, List.of());

        assertEquals(new BigDecimal("120"), comparison.getCurrentPeakAnnualTax());
    }

    @Test
    void emptyProjectionsDoNotThrow() {

        ProjectionComparison comparison = assertDoesNotThrow(
                () -> service.compare(
                        new Projection(), List.of(),
                        new Projection(), List.of()));

        assertEquals(BigDecimal.ZERO, comparison.getCurrentTotalTaxes());
        assertEquals(BigDecimal.ZERO, comparison.getCurrentPeakAnnualTax());
        assertEquals(BigDecimal.ZERO, comparison.getCurrentEndingInvestableAssets());
    }

    private Projection projection(ProjectionYear... years) {
        Projection projection = new Projection();
        for (ProjectionYear year : years) {
            projection.addYear(year);
        }
        return projection;
    }

    private ProjectionYear year(
            int calendarYear,
            long growth,
            long income,
            long federalTax,
            long michiganTax,
            long endingAssets,
            long estate) {

        return aProjectionYear()
                .withCalendarYear(calendarYear)
                .withInvestmentGrowth(growth)
                .withGuaranteedIncome(income)
                .withFederalTaxCalculation(
                        aFederalTaxCalculation()
                                .withFederalIncomeTax(federalTax)
                                .build())
                .withMichiganTaxCalculation(
                        aMichiganTaxCalculation()
                                .withIncomeTax(michiganTax)
                                .build())
                .withEndingInvestableAssets(endingAssets)
                .withAfterTaxEstateValue(estate)
                .build();
    }

    private NonInvestableAssetProjection nonInvestable(
            int calendarYear,
            long value) {

        return new NonInvestableAssetProjection(
                calendarYear,
                List.of(),
                BigDecimal.valueOf(value));
    }
}
