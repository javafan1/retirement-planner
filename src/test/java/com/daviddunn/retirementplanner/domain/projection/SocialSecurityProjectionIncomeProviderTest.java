package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import com.daviddunn.retirementplanner.domain.tax.TaxIncomeCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SocialSecurityProjectionIncomeProviderTest {

    @Test
    void reconcilesEveryYearToDirectAdvancedCalculationIncludingSpousalExcess() {
        RetirementPlan plan = plan(new DeathScenarioAssumptions(
                DeathScenario.BOTH_SURVIVE, null), true);
        Map<Integer, HouseholdSocialSecurityResult> projected =
                new SocialSecurityProjectionIncomeProvider().calculate(plan, 2030, 2033);
        SocialSecurityLifetimeResult direct = new SocialSecurityStrategyCalculator()
                .calculate(directRequest(plan, 2030, 2033));

        for (SocialSecurityAnnualResult annual : direct.annualResults()) {
            HouseholdSocialSecurityResult actual = projected.get(annual.calendarYear());
            assertMoney(annual.primaryOwnBenefits(), actual.primaryOwnBenefit());
            assertMoney(annual.spouseOwnBenefits(), actual.spouseOwnBenefit());
            assertMoney(annual.primarySpousalExcessBenefits(),
                    actual.primarySpousalExcessBenefit());
            assertMoney(annual.spouseSpousalExcessBenefits(),
                    actual.spouseSpousalExcessBenefit());
            assertMoney(annual.householdBenefits(), actual.householdBenefit());
        }
        assertTrue(projected.get(2031).spouseSpousalExcessBenefit().signum() > 0);
    }

    @Test
    void projectionUsesAuthoritativeAmountOnceForCashAndTaxInput() {
        RetirementPlan plan = plan(new DeathScenarioAssumptions(
                DeathScenario.BOTH_SURVIVE, null), true);
        Projection projection = new ProjectionEngine().project(plan);
        Projection repeated = new ProjectionEngine().project(plan);

        for (int index = 0; index < projection.size(); index++) {
            ProjectionYear year = projection.getYearAt(index);
            HouseholdSocialSecurityResult result = year.getSocialSecurityResult();
            assertMoney(result.householdBenefit(), year.getGuaranteedIncome());
            TaxIncome taxIncome = new TaxIncomeCalculator().calculate(
                    plan.getHousehold(), LocalDate.of(year.getCalendarYear(), 12, 31),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    plan.getPlanningAssumptions().getSocialSecurityColaRate(),
                    plan.getPlanningAssumptions().getDeathScenarioAssumptions(), result);
            assertMoney(result.householdBenefit(), taxIncome.getSocialSecurityIncome());
            assertMoney(result.householdBenefit(), repeated.getYearAt(index)
                    .getSocialSecurityResult().householdBenefit());
        }
    }

    @Test
    void januaryFirstDeathBridgeProducesMonthlyDeathYearTransitionAndSurvivor() {
        RetirementPlan plan = plan(new DeathScenarioAssumptions(
                DeathScenario.PRIMARY_DIES, 2032, 62), true);
        Map<Integer, HouseholdSocialSecurityResult> results =
                new SocialSecurityProjectionIncomeProvider().calculate(plan, 2030, 2033);

        assertTrue(results.get(2031).primaryOwnBenefit().signum() > 0);
        assertMoney(BigDecimal.ZERO, results.get(2032).primaryOwnBenefit());
        assertTrue(results.get(2032).spouseSurvivorCandidate().signum() > 0);
        assertMoney(BigDecimal.ZERO, results.get(2033).primaryOwnBenefit());
    }

    @Test
    void strategyOverrideAndSurvivorBenefitsRetainPlanReductionPolicy() {
        RetirementPlan baseline = plan(new DeathScenarioAssumptions(
                DeathScenario.PRIMARY_DIES, 2032, 67), true);
        RetirementPlan reduced = plan(new DeathScenarioAssumptions(
                DeathScenario.PRIMARY_DIES, 2032, 67), true, "22", 2032);
        Person primary = reduced.getHousehold().getPrimaryPerson();
        Person spouse = reduced.getHousehold().getSpouse();
        var strategy = new SocialSecurityHouseholdClaimingStrategy(68, 62,
                LocalDate.of(2031, 6, 4), LocalDate.of(2027, 2, 28),
                new SocialSecuritySurvivorClaimingCandidate(
                        primary.getBirthDate().plusYears(67), 67, 0, "Age 67"),
                new SocialSecuritySurvivorClaimingCandidate(
                        spouse.getBirthDate().plusYears(67), 67, 0, "Age 67"));
        var context = ProjectionEvaluationContext.withSocialSecurityStrategy(strategy);
        var provider = new SocialSecurityProjectionIncomeProvider();
        var full = provider.calculate(baseline, 2031, 2033, context);
        var cut = provider.calculate(reduced, 2031, 2033, context);

        assertMoney(full.get(2031).householdBenefit(), cut.get(2031).householdBenefit());
        assertTrue(cut.get(2032).spouseSurvivorCandidate().signum() > 0);
        assertTrue(cut.get(2032).householdBenefit().compareTo(
                full.get(2032).householdBenefit()) < 0);
        assertTrue(cut.get(2033).householdBenefit().compareTo(
                full.get(2033).householdBenefit()) < 0);
        assertMoney(cut.get(2032).householdBenefit(), cut.get(2032).primarySelectedBenefit()
                .add(cut.get(2032).spouseSelectedBenefit()));
    }

    @Test
    void missingSpouseSourceRemainsSafeThroughIsolatedLegacyFallback() {
        RetirementPlan plan = plan(new DeathScenarioAssumptions(
                DeathScenario.BOTH_SURVIVE, null), false);
        Map<Integer, HouseholdSocialSecurityResult> results =
                new SocialSecurityProjectionIncomeProvider().calculate(plan, 2030, 2031);

        assertTrue(results.get(2031).primaryOwnBenefit().signum() > 0);
        assertMoney(BigDecimal.ZERO, results.get(2031).spouseOwnBenefit());
    }

    @Test
    void planReductionPolicyReachesLegacyAnnualAndSinglePersonOwnPaths() {
        RetirementPlan legacy = plan(new DeathScenarioAssumptions(
                DeathScenario.BOTH_SURVIVE, null), false, "10", 2031);
        var legacyResult = new SocialSecurityProjectionIncomeProvider().calculate(legacy, 2030, 2031);
        assertMoney(new BigDecimal("21000.00"), legacyResult.get(2030).householdBenefit());
        assertMoney(new BigDecimal("33048.00"), legacyResult.get(2031).householdBenefit());

        Person primary = new Person("Primary", "Planner", LocalDate.of(1963, 6, 4));
        primary.addIncomeSource(source(AccountOwnership.PRIMARY, LocalDate.of(2030, 6, 4), "3000", 67));
        var old = new EconomicAssumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
        var economic = new EconomicAssumptions(old.getExpectedAnnualInvestmentReturn(),
                old.getGeneralInflationRate(), old.getHealthcareInflationRate(),
                old.getSocialSecurityColaRate(), new BigDecimal("10"), 2031);
        var assumptions = new PlanningAssumptions(economic,
                new TaxAssumptions(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new WithdrawalAssumptions(WithdrawalStrategyType.TAXABLE_FIRST),
                new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null),
                2, LocalDate.of(2030, 1, 1));
        RetirementPlan single = new RetirementPlan(new Household(primary), new AccountPortfolio(), assumptions);
        var singleResult = new SocialSecurityProjectionIncomeProvider().calculate(single, 2030, 2031);
        assertMoney(new BigDecimal("21000.00"), singleResult.get(2030).householdBenefit());
        assertMoney(new BigDecimal("32400.00"), singleResult.get(2031).householdBenefit());
    }

    @Test
    void reducedBenefitsFlowOnceThroughProjectionTaxesCashFlowAndEstate() {
        RetirementPlan baseline = plan(new DeathScenarioAssumptions(
                DeathScenario.BOTH_SURVIVE, null), true, "0", 2032);
        RetirementPlan reduced = plan(new DeathScenarioAssumptions(
                DeathScenario.BOTH_SURVIVE, null), true, "22", 2032);
        for (RetirementPlan candidate : java.util.List.of(baseline, reduced)) {
            candidate.getHousehold().addExpense(new Expense("Retirement spending", new BigDecimal("80000")));
            candidate.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount(
                    "Joint brokerage", AccountOwnership.JOINT, new BigDecimal("500000")));
            candidate.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA(
                    "Primary IRA", AccountOwnership.PRIMARY, new BigDecimal("1000000")));
            candidate.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.RothIRA(
                    "Primary Roth", AccountOwnership.PRIMARY, BigDecimal.ZERO));
            candidate.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(
                    true, 2030, new BigDecimal("10000"),
                    com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.NEVER,
                    com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                    com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ANNUAL));
        }

        Projection baseProjection = new ProjectionEngine().project(baseline);
        Projection reducedProjection = new ProjectionEngine().project(reduced);
        var provider = new SocialSecurityProjectionIncomeProvider();
        var reducedProvider = provider.calculate(reduced, 2030, 2033);

        for (int i = 0; i < baseProjection.size(); i++) {
            ProjectionYear baseYear = baseProjection.getYearAt(i);
            ProjectionYear reducedYear = reducedProjection.getYearAt(i);
            int year = baseYear.getCalendarYear();
            HouseholdSocialSecurityResult payable = reducedProvider.get(year);
            assertMoney(payable.householdBenefit(), reducedYear.getSocialSecurityResult().householdBenefit());
            assertMoney(payable.householdBenefit(), reducedYear.getGuaranteedIncome());
            assertMoney(baseYear.getSocialSecurityResult().householdBenefit(),
                    baseYear.getGuaranteedIncome());
            if (year < 2032) {
                assertMoney(baseYear.getSocialSecurityResult().householdBenefit(), payable.householdBenefit());
            } else {
                assertEquals(-1, payable.householdBenefit().compareTo(
                        baseYear.getSocialSecurityResult().householdBenefit()));
            }

            TaxIncome taxIncome = new TaxIncomeCalculator().calculate(
                    reduced.getHousehold(), LocalDate.of(year, 12, 31),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    reduced.getPlanningAssumptions().getSocialSecurityColaRate(),
                    reduced.getPlanningAssumptions().getDeathScenarioAssumptions(), payable);
            assertMoney(payable.householdBenefit(), taxIncome.getSocialSecurityIncome());
        }

        ProjectionYear beforeEffective = baseProjection.getYearAt(1);
        ProjectionYear firstReducedYear = reducedProjection.getYearAt(2);
        ProjectionYear baseFirstReducedYear = baseProjection.getYearAt(2);
        assertTrue(firstReducedYear.getTaxableSocialSecurity().compareTo(
                baseFirstReducedYear.getTaxableSocialSecurity()) < 0);
        assertTrue(firstReducedYear.getPortfolioWithdrawal().compareTo(
                baseFirstReducedYear.getPortfolioWithdrawal()) > 0);
        assertTrue(firstReducedYear.getFederalIncomeTax().signum() >= 0);
        assertTrue(firstReducedYear.getMichiganIncomeTax().signum() >= 0);
        assertTrue(firstReducedYear.getRothConversion().signum() > 0);
        assertMoney(firstReducedYear.getRothConversion(),
                firstReducedYear.getPrimaryRothConversion()
                        .add(firstReducedYear.getSpouseRothConversion()));
        assertTrue(baseFirstReducedYear.getRothConversion().signum() > 0);
        assertNotEquals(0, reducedProjection.getFinalInvestableAssets().compareTo(
                baseProjection.getFinalInvestableAssets()));
        assertNotEquals(0, reducedProjection.getLastYear().getAfterTaxEstateValue().compareTo(
                baseProjection.getLastYear().getAfterTaxEstateValue()));
        assertMoney(beforeEffective.getSocialSecurityResult().householdBenefit(),
                reducedProjection.getYearAt(1).getSocialSecurityResult().householdBenefit());
    }

    @Test
    void retirementAt62ContinuesAfterDeathAt64WhileSurvivorElectionWaitsUntil67() {
        RetirementPlan plan = plan(new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2030, 67), true);
        Person primary = plan.getHousehold().getPrimaryPerson();
        SocialSecurityIncome primaryIncome = source(AccountOwnership.PRIMARY,
                LocalDate.of(2033, 6, 4), "3000", 70);
        primary.replaceIncomeSource(primary.getIncomeSources().getFirst(), primaryIncome);
        SocialSecurityIncome spouseIncome = (SocialSecurityIncome) plan.getHousehold().getSpouse().getIncomeSources().getFirst();
        var results = new SocialSecurityProjectionIncomeProvider().calculate(plan, 2027, 2033);
        for (int year = 2027; year <= 2031; year++) {
            assertTrue(results.get(year).spouseOwnBenefit().signum() > 0);
            assertMoney(BigDecimal.ZERO, results.get(year).spouseSurvivorCandidate());
        }
        assertTrue(results.get(2032).spouseSurvivorCandidate().signum() > 0);
        assertMoney(BigDecimal.ZERO, results.get(2033).primaryOwnBenefit());
        assertEquals(62, spouseIncome.getClaimingAge());
        assertEquals(LocalDate.of(2027, 2, 28), spouseIncome.getStartDate());
        assertEquals(70, primaryIncome.getClaimingAge());
        assertEquals(LocalDate.of(2033, 6, 4), primaryIncome.getStartDate());
    }

    @Test
    void deathAfterFraUsesImmediateElectionEvenWhenStoredAgeWouldDelayIt() {
        RetirementPlan immediate = plan(new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2033, 67), true);
        RetirementPlan late = plan(new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2033, 70), true);
        var provider = new SocialSecurityProjectionIncomeProvider();
        assertEquals(provider.calculate(immediate, 2033, 2034), provider.calculate(late, 2033, 2034));
        assertTrue(provider.calculate(late, 2033, 2033).get(2033).spouseSurvivorCandidate().signum() > 0);
    }

    @Test
    void bothSurviveIgnoresDormantSurvivorElection() {
        var provider = new SocialSecurityProjectionIncomeProvider();
        assertEquals(provider.calculate(plan(new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, null), true), 2030, 2033),
                provider.calculate(plan(new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE, 2030, 60), true), 2030, 2033));
    }

    @Test
    void deterministicAndLifetimeOverridesKeepCandidateRetirementIndependentOfSurvivorElection() {
        RetirementPlan plan = plan(new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2030, 67), true);
        Person primary = plan.getHousehold().getPrimaryPerson();
        Person spouse = plan.getHousehold().getSpouse();
        var strategy = new SocialSecurityHouseholdClaimingStrategy(70, 62,
                LocalDate.of(2033, 6, 4), LocalDate.of(2027, 2, 28),
                new SocialSecuritySurvivorClaimingCandidate(primary.getBirthDate().plusYears(67), 67, 0, "Age 67"),
                new SocialSecuritySurvivorClaimingCandidate(spouse.getBirthDate().plusYears(67), 67, 0, "Age 67"));
        var lifetime = new HouseholdLifetimeScenario(java.util.Optional.of(java.time.Year.of(2030)),
                java.util.Optional.of(java.time.Year.of(2034)));
        var provider = new SocialSecurityProjectionIncomeProvider();
        for (var context : java.util.List.of(ProjectionEvaluationContext.withSocialSecurityStrategy(strategy),
                ProjectionEvaluationContext.withSocialSecurityStrategy(strategy, lifetime))) {
            var result = provider.calculate(plan, 2027, 2033, context);
            assertTrue(result.get(2029).spouseOwnBenefit().signum() > 0);
            assertTrue(result.get(2030).spouseOwnBenefit().signum() > 0);
            assertMoney(BigDecimal.ZERO, result.get(2030).spouseSurvivorCandidate());
            assertMoney(BigDecimal.ZERO, result.get(2031).spouseSurvivorCandidate());
            assertTrue(result.get(2032).spouseSurvivorCandidate().signum() > 0);
            assertMoney(BigDecimal.ZERO, result.get(2033).primaryOwnBenefit());
        }
        assertEquals(70, strategy.primaryRetirementAge());
        assertEquals(62, strategy.spouseRetirementAge());
        assertEquals(67, ((SocialSecurityIncome) primary.getIncomeSources().getFirst()).getClaimingAge());
    }

    @Test
    void lateDeathInLifetimeScenarioIsImmediateEvenWhenIntendedElectionIsAfterSecondDeath() {
        RetirementPlan plan = plan(new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2033, 70), true);
        Person primary = plan.getHousehold().getPrimaryPerson();
        Person spouse = plan.getHousehold().getSpouse();
        var strategy = new SocialSecurityHouseholdClaimingStrategy(67, 62,
                LocalDate.of(2030, 6, 4), LocalDate.of(2027, 2, 28),
                new SocialSecuritySurvivorClaimingCandidate(primary.getBirthDate().plusYears(70), 70, 0, "Age 70"),
                new SocialSecuritySurvivorClaimingCandidate(spouse.getBirthDate().plusYears(70), 70, 0, "Age 70"));
        var lifetime = new HouseholdLifetimeScenario(java.util.Optional.of(java.time.Year.of(2033)),
                java.util.Optional.of(java.time.Year.of(2034)));
        var provider = new SocialSecurityProjectionIncomeProvider();
        for (var context : java.util.List.of(ProjectionEvaluationContext.withSocialSecurityStrategy(strategy),
                ProjectionEvaluationContext.withSocialSecurityStrategy(strategy, lifetime))) {
            assertTrue(provider.calculate(plan, 2033, 2034, context).get(2033).spouseSurvivorCandidate().signum() > 0);
        }
    }

    private RetirementPlan plan(
            DeathScenarioAssumptions death,
            boolean includeSpouseSource) {
        return plan(death, includeSpouseSource, "0", 2032);
    }

    private RetirementPlan plan(
            DeathScenarioAssumptions death,
            boolean includeSpouseSource,
            String reductionPercent,
            int reductionYear) {
        Person primary = new Person("Primary", "Planner", LocalDate.of(1963, 6, 4));
        Person spouse = new Person("Spouse", "Planner", LocalDate.of(1965, 2, 28));
        primary.addIncomeSource(source(AccountOwnership.PRIMARY,
                LocalDate.of(2030, 6, 4), "3000", 67));
        if (includeSpouseSource) {
            spouse.addIncomeSource(source(AccountOwnership.SPOUSE,
                    LocalDate.of(2027, 2, 28), "800", 62));
        }
        PlanningAssumptions assumptions = new PlanningAssumptions(
                new EconomicAssumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("0.02"),
                        new BigDecimal(reductionPercent), reductionYear),
                new TaxAssumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO),
                new WithdrawalAssumptions(WithdrawalStrategyType.TAXABLE_FIRST),
                death, 4, LocalDate.of(2030, 7, 1));
        return new RetirementPlan(new Household(primary, spouse),
                new AccountPortfolio(), assumptions);
    }

    private SocialSecurityIncome source(
            AccountOwnership owner,
            LocalDate start,
            String benefit,
            int age) {
        return new SocialSecurityIncome("Social Security", owner, start, null,
                new BigDecimal(benefit), age, BigDecimal.ZERO, 2030);
    }

    private SocialSecurityStrategyRequest directRequest(
            RetirementPlan plan,
            int firstYear,
            int lastYear) {
        Person primary = plan.getHousehold().getPrimaryPerson();
        Person spouse = plan.getHousehold().getSpouse();
        SocialSecurityIncome primarySource = (SocialSecurityIncome)
                primary.getIncomeSources().getFirst();
        SocialSecurityIncome spouseSource = (SocialSecurityIncome)
                spouse.getIncomeSources().getFirst();
        return new SocialSecurityStrategyRequest(
                LocalDate.of(firstYear, 1, 1), LocalDate.of(lastYear, 12, 31),
                election(primary, primarySource, AccountOwnership.PRIMARY),
                election(spouse, spouseSource, AccountOwnership.SPOUSE),
                null, null, null, null,
                plan.getPlanningAssumptions().getSocialSecurityColaRate(),
                new com.daviddunn.retirementplanner.domain.socialsecurity.SocialSecurityBenefitReductionPolicy(
                        plan.getPlanningAssumptions().getEconomicAssumptions()
                                .getSocialSecurityBenefitReductionPercent(),
                        plan.getPlanningAssumptions().getEconomicAssumptions()
                                .getSocialSecurityBenefitReductionEffectiveYear()));
    }

    private SocialSecurityClaimingElection election(
            Person person,
            SocialSecurityIncome source,
            AccountOwnership owner) {
        return new SocialSecurityClaimingElection(owner, person.getBirthDate(),
                source.getFullRetirementMonthlyBenefit(),
                source.getBenefitValuationYear(), source.getStartDate());
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
