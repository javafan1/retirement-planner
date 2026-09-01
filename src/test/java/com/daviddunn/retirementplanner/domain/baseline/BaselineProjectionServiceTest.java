package com.daviddunn.retirementplanner.domain.baseline;

import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BaselineProjectionServiceTest {

    @Test
    void projectsBaselineUsingSavedSnapshot() {

        RetirementPlan plan =
                RetirementPlanFactory.createEmptyPlan();

        /*
         * The empty-plan factory intentionally creates
         * people without birth dates.
         *
         * ProjectionEngine requires valid birth dates
         * when calculating projection years.
         */
        plan.getHousehold()
                .getPrimaryPerson()
                .setBirthDate(
                        LocalDate.of(
                                1963,
                                6,
                                4));

        plan.getHousehold()
                .getSpouse()
                .setBirthDate(
                        LocalDate.of(
                                1965,
                                2,
                                28));

        plan.getAccountPortfolio().addAccount(new BrokerageAccount(
                "Baseline projection funding account",
                AccountOwnership.PRIMARY,
                new BigDecimal("100000")));

        ProjectionBaseline baseline =
                ProjectionBaselineFactory.create(
                        plan,
                        "Test Baseline");

        BaselineProjectionService service =
                new BaselineProjectionService(
                        new ProjectionEngine());

        Projection projection =
                service.projectBaseline(
                        baseline);

        assertNotNull(
                projection);

        assertFalse(
                projection.isEmpty());
    }

    @Test
    void comparesTheSpecifiedYearWhenProjectionContainsMultipleYears() {

        Projection baselineProjection =
                new Projection();

        baselineProjection.addYear(
                new ProjectionYear(
                        1,
                        2035,
                        new BigDecimal("4000000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("4000000"),
                        72));

        baselineProjection.addYear(
                new ProjectionYear(
                        2,
                        2040,
                        new BigDecimal("4500000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("5000000"),
                        77));


        Projection currentProjection =
                new Projection();

        currentProjection.addYear(
                new ProjectionYear(
                        1,
                        2035,
                        new BigDecimal("4000000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("4100000"),
                        72));

        currentProjection.addYear(
                new ProjectionYear(
                        2,
                        2040,
                        new BigDecimal("4500000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("5300000"),
                        77));


        List<NonInvestableAssetProjection>
                baselineNonInvestableAssets =
                List.of(
                        new NonInvestableAssetProjection(
                                2035,
                                List.of(),
                                new BigDecimal("900000")),

                        new NonInvestableAssetProjection(
                                2040,
                                List.of(),
                                new BigDecimal("1000000")));


        List<NonInvestableAssetProjection>
                currentNonInvestableAssets =
                List.of(
                        new NonInvestableAssetProjection(
                                2035,
                                List.of(),
                                new BigDecimal("950000")),

                        new NonInvestableAssetProjection(
                                2040,
                                List.of(),
                                new BigDecimal("1100000")));


        ProjectionComparisonService service =
                new ProjectionComparisonService();


        ProjectionComparison comparison =
                service.compare(
                        baselineProjection,
                        baselineNonInvestableAssets,
                        currentProjection,
                        currentNonInvestableAssets);


        assertEquals(
                2040,
                comparison.getCalendarYear());

        assertEquals(
                new BigDecimal("300000"),
                comparison
                        .getEndingInvestableAssetsChange());

        assertEquals(
                new BigDecimal("400000"),
                comparison
                        .getNetWorthChange());
    }

}
