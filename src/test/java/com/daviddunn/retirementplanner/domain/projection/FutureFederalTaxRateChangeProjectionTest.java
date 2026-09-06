package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.TaxAssumptions;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FutureFederalTaxRateChangeProjectionTest {

    @Test
    void clearingConfiguredChangeMatchesNeverConfiguredProjection() {

        RetirementPlan configured =
                plan(new BigDecimal("0.05"), 2030);

        RetirementPlan cleared =
                plan(new BigDecimal("0.05"), 2030);

        cleared.setPlanningAssumptions(
                assumptions(null, null));

        Projection configuredProjection =
                new ProjectionEngine().project(configured);
        Projection clearedProjection =
                new ProjectionEngine().project(cleared);
        Projection neverConfiguredProjection =
                new ProjectionEngine().project(
                        plan(null, null));

        for (int index = 0;
             index < clearedProjection.size();
             index++) {

            ProjectionYear clearedYear =
                    clearedProjection.getYearAt(index);
            ProjectionYear neverConfiguredYear =
                    neverConfiguredProjection.getYearAt(index);

            assertEquals(
                    neverConfiguredYear.getFederalIncomeTax(),
                    clearedYear.getFederalIncomeTax());
            assertEquals(
                    neverConfiguredYear.getEndingInvestableAssets(),
                    clearedYear.getEndingInvestableAssets());
            assertEquals(
                    neverConfiguredYear.getAfterTaxEstateValue(),
                    clearedYear.getAfterTaxEstateValue());
        }

        assertNotEquals(
                configuredProjection.getYearAt(4)
                        .getFederalIncomeTax(),
                clearedProjection.getYearAt(4)
                        .getFederalIncomeTax());
    }

    private RetirementPlan plan(
            BigDecimal adjustment,
            Integer effectiveYear) {

        Person primary =
                new Person(
                        "Primary",
                        "Planner",
                        LocalDate.of(1960, 1, 1));

        Person spouse =
                new Person(
                        "Spouse",
                        "Planner",
                        LocalDate.of(1962, 1, 1));

        primary.addIncomeSource(
                new Pension(
                        "Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 1, 1),
                        null,
                        new BigDecimal("10000"),
                        BigDecimal.ZERO));

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000")));

        return new RetirementPlan(
                new Household(primary, spouse),
                portfolio,
                assumptions(adjustment, effectiveYear));
    }

    private PlanningAssumptions assumptions(
            BigDecimal adjustment,
            Integer effectiveYear) {

        return new PlanningAssumptions(
                new EconomicAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO),
                new TaxAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        FilingStatus.MARRIED_FILING_JOINTLY,
                        new BigDecimal("0.25"),
                        adjustment,
                        effectiveYear),
                6,
                LocalDate.of(2026, 1, 1));
    }
}
