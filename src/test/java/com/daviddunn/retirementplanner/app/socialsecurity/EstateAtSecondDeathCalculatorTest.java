package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.estate.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class EstateAtSecondDeathCalculatorTest {
    private final EstateAtSecondDeathCalculator calculator = new EstateAtSecondDeathCalculator();

    @Test
    void januaryDeathUsesPriorRowIncludingRetainedAssetsAndNeverDeathYearGrowth() {
        var plan = Stage4TestPlans.plan();
        var projection = new ProjectionEngine().project(plan, ProjectionEvaluationContext.withLifetimeScenario(
                new HouseholdLifetimeScenario(java.util.Optional.of(java.time.Year.of(2031)),
                        java.util.Optional.of(java.time.Year.of(2033)))));
        var snapshot = calculator.calculate(plan, projection, LocalDate.of(2033, 1, 1));
        var prior = projection.getYearAt(2);
        assertEquals(prior.getEndingInvestableAssets(), snapshot.nominalInvestableAssets());
        assertEquals(prior.getEstimatedHeirTax(), snapshot.estimatedHeirTax());
        assertEquals(prior.getAfterTaxEstateValue(), snapshot.nominalAfterTaxEstate());
        assertEquals(LocalDate.of(2032, 12, 31), snapshot.balanceDate());
        assertNotEquals(projection.getYearAt(3).getAfterTaxEstateValue(), snapshot.nominalAfterTaxEstate());
        // Heir tax is a metric, never a first-death withdrawal or extra second-death haircut.
        assertEquals(0, snapshot.nominalInvestableAssets().subtract(snapshot.estimatedHeirTax())
                .compareTo(snapshot.nominalAfterTaxEstate()));
    }

    @Test
    void openingDeathUsesOpeningCompositionRatherThanAnyAnnualRow() {
        var plan = Stage4TestPlans.plan();
        plan.addNonInvestableAsset(new com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAsset(
                "Home", new BigDecimal("900000"), BigDecimal.ZERO));
        var snapshot = calculator.calculate(plan, new Projection(), LocalDate.of(2030, 1, 1));
        assertEquals(0, new BigDecimal("800000").compareTo(snapshot.nominalInvestableAssets()));
        assertEquals(0, new BigDecimal("125000").compareTo(snapshot.estimatedHeirTax()));
        assertEquals(0, new BigDecimal("675000").compareTo(snapshot.nominalAfterTaxEstate()));
        assertEquals(snapshot.effectiveSecondDeathDate(), snapshot.balanceDate());
    }

    @Test
    void unavailableHistoricalAndMissingPriorRowsFailClearly() {
        var plan = Stage4TestPlans.plan();
        assertTrue(assertThrows(IllegalArgumentException.class, () -> calculator.calculate(plan,
                new Projection(), LocalDate.of(2029, 1, 1))).getMessage().contains("opening balances"));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(plan,
                new Projection(), LocalDate.of(2031, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(plan,
                new Projection(), LocalDate.of(2030, 6, 1)));
        var assumptions = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(
                assumptions.getEconomicAssumptions(), assumptions.getTaxAssumptions(),
                assumptions.getWithdrawalAssumptions(), assumptions.getDeathScenarioAssumptions(),
                5, LocalDate.of(2030, 7, 1)));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(plan,
                new Projection(), LocalDate.of(2030, 1, 1)));
    }

    @Test
    void overrideExtendsOnlyAndDoesNotAlterTheConfiguredHorizon() throws Exception {
        var plan = Stage4TestPlans.plan();
        var before = Stage4TestPlans.json(plan);
        var engine = new ProjectionEngine();
        var extended = engine.project(plan, ProjectionEvaluationContext.empty().withEndingYear(2038));
        assertEquals(2038, extended.getEndYear());
        var ordinary = engine.project(plan);
        assertEquals(2034, ordinary.getEndYear());
        var shortened = engine.project(plan, ProjectionEvaluationContext.empty().withEndingYear(2031));
        assertEquals(2034, shortened.getEndYear());
        assertEquals(Stage4TestPlans.json(ordinary), Stage4TestPlans.json(shortened));
        assertEquals(before, Stage4TestPlans.json(plan));
    }

    @Test
    void heirTaxIsNeverWithdrawnAtFirstDeathAndRetainedCashIsIncludedWithoutHaircut() {
        var plan = Stage4TestPlans.plan();
        java.util.List.copyOf(plan.getHousehold().getExpenses()).forEach(plan.getHousehold()::removeExpense);
        var lifetime = new HouseholdLifetimeScenario(java.util.Optional.of(java.time.Year.of(2031)),
                java.util.Optional.of(java.time.Year.of(2034)));
        var context = ProjectionEvaluationContext.withLifetimeScenario(lifetime);
        var taxed = new ProjectionEngine().project(plan, context);
        var assumptions = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(
                assumptions.getEconomicAssumptions(),
                new com.daviddunn.retirementplanner.domain.model.TaxAssumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("0.25"),
                        com.daviddunn.retirementplanner.domain.rules.FilingStatus.MARRIED_FILING_JOINTLY, BigDecimal.ZERO),
                assumptions.getWithdrawalAssumptions(), assumptions.getDeathScenarioAssumptions(),
                5, assumptions.getProjectionStartDate()));
        var untaxedEstate = new ProjectionEngine().project(plan, context);
        for (int i = 0; i < taxed.size(); i++) {
            assertEquals(taxed.getYearAt(i).getEndingInvestableAssets(), untaxedEstate.getYearAt(i).getEndingInvestableAssets());
            assertEquals(taxed.getYearAt(i).getTotalIncomeTax(), untaxedEstate.getYearAt(i).getTotalIncomeTax());
        }
        var row = taxed.getYearAt(3);
        var accountTotal = row.getEndingAccountSnapshots().stream().map(ProjectedAccountSnapshot::getEndingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertTrue(row.getEndingInvestableAssets().compareTo(accountTotal) > 0);
        var snapshot = calculator.calculate(plan, taxed, LocalDate.of(2034, 1, 1));
        assertEquals(row.getAfterTaxEstateValue(), snapshot.nominalAfterTaxEstate());
    }

    @Test
    void extendingALivingOwnerBeyondTheRmdTableFailsInsteadOfInventingAFactor() {
        var plan = Stage4TestPlans.plan();
        var error = assertThrows(IllegalArgumentException.class, () -> new ProjectionEngine().project(plan,
                ProjectionEvaluationContext.withLifetimeScenario(HouseholdLifetimeScenario.bothSurvive())
                        .withEndingYear(2081)));
        assertTrue(error.getMessage().contains("121"), error::getMessage);
    }
}
