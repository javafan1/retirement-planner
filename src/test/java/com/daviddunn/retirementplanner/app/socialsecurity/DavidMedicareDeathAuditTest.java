package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.nio.file.Path;

@EnabledIfSystemProperty(named = "audit.plan", matches = ".+")
class DavidMedicareDeathAuditTest {

    private final RetirementPlanScenarioCopyService copier =
            new RetirementPlanScenarioCopyService();
    private final IntegratedSocialSecurityStrategyEvaluator evaluator =
            new IntegratedSocialSecurityStrategyEvaluator();

    @Test
    void auditDeathBoundaryMedicare() throws Exception {
        RetirementPlan source = new JsonRetirementPlanRepository().load(
                Path.of(System.getProperty("audit.plan")));
        SocialSecurityHouseholdClaimingStrategy strategy = strategy(source);
        run("PRIMARY_DIES", source, strategy, DeathScenario.PRIMARY_DIES, 2048);
        run("SPOUSE_DIES", source, strategy, DeathScenario.SPOUSE_DIES, 2048);
    }

    private void run(
            String label,
            RetirementPlan source,
            SocialSecurityHouseholdClaimingStrategy strategy,
            DeathScenario scenario,
            int deathYear) {
        RetirementPlan plan = copier.copy(source);
        PlanningAssumptions old = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(
                old.getEconomicAssumptions(),
                old.getTaxAssumptions(),
                old.getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(
                        scenario, deathYear, 67, new BigDecimal("0.6")),
                old.getProjectionLengthYears(),
                old.getProjectionStartDate()));

        IntegratedSocialSecurityStrategyResult result =
                evaluator.evaluate(plan, strategy);
        var metrics = result.metrics();
        BigDecimal retained = result.projection().getYears().stream()
                .map(ProjectionYear::getRetainedHouseholdSurplus)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println("DEATH_AUDIT," + label
                + ",medicare=" + metrics.lifetimeMedicarePremiums()
                + ",taxes=" + metrics.totalTaxes()
                + ",withdrawals=" + metrics.lifetimePortfolioWithdrawals()
                + ",retained=" + retained
                + ",growth=" + metrics.totalInvestmentGrowth()
                + ",rmd=" + metrics.lifetimeRequiredMinimumDistributions()
                + ",roth=" + metrics.lifetimeRothConversions()
                + ",ending=" + metrics.endingInvestableAssets()
                + ",estate=" + metrics.afterTaxEstate());
        for (int year = deathYear - 1; year <= deathYear + 1; year++) {
            int targetYear = year;
            ProjectionYear value = result.projection().getYears().stream()
                    .filter(item -> item.getCalendarYear() == targetYear)
                    .findFirst().orElseThrow();
            var medicare = value.getMedicarePremiumCalculation();
            System.out.println("DEATH_LEDGER," + label + "," + year
                    + ",covered=" + medicare.coveredMedicareParticipants()
                    + ",partB=" + medicare.annualPartBPremium()
                    + ",partD=" + medicare.annualPartDPremium()
                    + ",total=" + medicare.totalAnnualMedicarePremium());
        }
    }

    private SocialSecurityHouseholdClaimingStrategy strategy(RetirementPlan plan) {
        var primary = plan.getHousehold().getPrimaryPerson();
        var spouse = plan.getHousehold().getSpouse();
        var generator = new SocialSecuritySurvivorClaimingCandidateGenerator();
        return new SocialSecurityHouseholdClaimingStrategy(
                70,
                62,
                SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(
                        primary.getBirthDate(), 70),
                SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(
                        spouse.getBirthDate(), 62),
                generator.generate(primary.getBirthDate()).getLast(),
                generator.generate(spouse.getBirthDate()).getLast());
    }
}
