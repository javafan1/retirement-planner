package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicareDeathCoverageProjectionTest {

    @Test
    void bothEligibleAndAliveProduceTwoCoveredParticipants() {
        ProjectionYear year = project(
                LocalDate.of(1950, 1, 1),
                LocalDate.of(1952, 1, 1),
                new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null),
                2026,
                1).getFirstYear();

        assertCoverage(year, 2);
    }

    @Test
    void primaryIsExcludedBeginningWithFullYearDeathYear() {
        Projection projection = project(
                LocalDate.of(1950, 1, 1),
                LocalDate.of(1952, 1, 1),
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2027),
                2026,
                3);

        assertCoverage(projection.getYearAt(0), 2);
        assertCoverage(projection.getYearAt(1), 1);
        assertCoverage(projection.getYearAt(2), 1);
    }

    @Test
    void spouseIsExcludedBeginningWithFullYearDeathYear() {
        Projection projection = project(
                LocalDate.of(1950, 1, 1),
                LocalDate.of(1952, 1, 1),
                new DeathScenarioAssumptions(DeathScenario.SPOUSE_DIES, 2027),
                2026,
                3);

        assertCoverage(projection.getYearAt(0), 2);
        assertCoverage(projection.getYearAt(1), 1);
        assertCoverage(projection.getYearAt(2), 1);
    }

    @Test
    void personWhoDiesBeforeEligibilityNeverLaterBecomesCovered() {
        Projection projection = project(
                LocalDate.of(1963, 1, 1),
                LocalDate.of(1990, 1, 1),
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2027),
                2026,
                4);

        projection.getYears().forEach(year -> assertCoverage(year, 0));
    }

    @Test
    void youngerSurvivorBecomesCoveredAtExistingEligibilityAge() {
        Projection projection = project(
                LocalDate.of(1950, 1, 1),
                LocalDate.of(1963, 1, 1),
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2026),
                2026,
                3);

        assertCoverage(projection.getYearAt(0), 0);
        assertCoverage(projection.getYearAt(1), 0);
        assertCoverage(projection.getYearAt(2), 1);
    }

    private Projection project(
            LocalDate primaryBirth,
            LocalDate spouseBirth,
            DeathScenarioAssumptions death,
            int startYear,
            int years) {
        Household household = new Household(
                new Person("Primary", "Person", primaryBirth),
                new Person("Spouse", "Person", spouseBirth));
        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(new BrokerageAccount(
                "Brokerage",
                AccountOwnership.PRIMARY,
                new BigDecimal("1000000")));
        PlanningAssumptions assumptions = new PlanningAssumptions(
                new EconomicAssumptions(BigDecimal.ZERO, BigDecimal.ZERO),
                new TaxAssumptions(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO),
                new WithdrawalAssumptions(WithdrawalStrategyType.TAXABLE_FIRST),
                death,
                years,
                LocalDate.of(startYear, 1, 1));
        return new ProjectionEngine().project(
                new RetirementPlan(household, portfolio, assumptions));
    }

    private void assertCoverage(ProjectionYear year, int expectedParticipants) {
        var medicare = year.getMedicarePremiumCalculation();
        assertEquals(expectedParticipants, medicare.coveredMedicareParticipants());
        assertMoney(
                medicare.monthlyPartBPremium().multiply(BigDecimal.valueOf(12)),
                medicare.annualPartBPremium());
        assertMoney(
                medicare.monthlyPartDPremium().multiply(BigDecimal.valueOf(12)),
                medicare.annualPartDPremium());
        assertMoney(
                medicare.annualPartBPremium().add(medicare.annualPartDPremium()),
                medicare.totalAnnualMedicarePremium());
        if (expectedParticipants == 0) {
            assertMoney(BigDecimal.ZERO, medicare.totalAnnualMedicarePremium());
        } else {
            assertTrue(medicare.totalAnnualMedicarePremium().signum() > 0);
        }
        assertCashReconciles(year);
    }

    private void assertCashReconciles(ProjectionYear year) {
        HouseholdCashSettlement settlement = year.getHouseholdCashSettlement();
        BigDecimal sources = settlement.guaranteedIncome()
                .add(settlement.projectedPeriodRmdCash())
                .add(settlement.spendingWithdrawal())
                .add(settlement.taxFundingWithdrawal());
        BigDecimal uses = settlement.ordinaryExpenses()
                .add(settlement.medicarePremiums())
                .add(settlement.incomeTaxes())
                .add(settlement.retainedHouseholdSurplus());
        assertTrue(sources.subtract(uses).abs()
                .compareTo(new BigDecimal("0.02")) <= 0);
        assertMoney(
                year.getAnnualMedicarePremium(),
                settlement.medicarePremiums());
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
