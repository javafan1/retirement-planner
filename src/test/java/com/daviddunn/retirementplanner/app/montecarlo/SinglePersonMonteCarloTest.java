package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysisTest;
import com.daviddunn.retirementplanner.domain.analysis.DiscreteProbabilitySampler;
import com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Year;
import java.util.Optional;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator;
import com.daviddunn.retirementplanner.domain.roth.*;
import static org.junit.jupiter.api.Assertions.*;

class SinglePersonMonteCarloTest {
    @Test void openingDeathDoesNotBypassExistingSinglePersonOwnershipValidation() {
        var plan = SinglePersonMortalityAnalysisTest.plan();
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount(
                "Invalid joint ownership", com.daviddunn.retirementplanner.domain.model.AccountOwnership.JOINT, BigDecimal.ONE));
        var failure = assertThrows(IllegalArgumentException.class, () -> new MonteCarloAnalyzer()
                .analyzeMortality(plan, request(1), i -> {
                    fail("Invalid ownership must be rejected before a world is generated");
                    return null;
                }, AnalysisProgressListener.none(), AnalysisCancellationToken.none()));
        assertTrue(failure.getMessage().contains("Joint accounts require a spouse"));
    }

    @Test void pensionOnlyPlanNeedsNeitherSocialSecurityElectionNorSpouse() {
        var plan = SinglePersonMortalityAnalysisTest.plan();
        var primary = plan.getHousehold().getPrimaryPerson();
        java.util.List.copyOf(primary.getIncomeSources()).stream()
                .filter(com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome.class::isInstance)
                .forEach(primary::removeIncomeSource);
        var result = new MonteCarloAnalyzer().analyzeMortality(plan, request(2));
        assertEquals(2, result.outcomes().size());
        assertFalse(plan.getHousehold().hasSpouse());
    }

    @Test void callerSuppliedWorldCompositionCannotBeMisinterpreted() {
        var plan = SinglePersonMortalityAnalysisTest.plan();
        var couple = new MonteCarloWorld(0, ProjectionEconomicPath.constant(BigDecimal.ZERO),
                new HouseholdLifetimeScenario(Optional.of(Year.of(2040)), Optional.of(Year.of(2045))));
        var failure = assertThrows(MonteCarloExecutionException.class, () -> new MonteCarloAnalyzer()
                .analyzeMortality(plan, request(1), i -> couple, AnalysisProgressListener.none(), AnalysisCancellationToken.none()));
        assertTrue(failure.getCause().getMessage().contains("composition"));
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloWorld(0, couple.economicPath(),
                new HouseholdLifetimeScenario(Optional.of(Year.of(2040)), Optional.empty())));
    }

    @Test void sampledFinancialRowsMatchEngineWithoutChangingAccountsOrCreatingSurvivors() {
        var plan = SinglePersonMortalityAnalysisTest.plan();
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, new BigDecimal("5000"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL));
        var balances = plan.getAccountPortfolio().getAccounts().stream().map(a -> a.getCurrentBalance()).toList();
        var world = new MonteCarloWorld(0, ProjectionEconomicPath.constant(new BigDecimal("0.04")),
                MonteCarloLifetime.individual(Year.of(2045)), Optional.empty());
        var context = ProjectionEvaluationContext.withLifetimeScenario(world.lifetimeScenario()).withExactEndingYear(2044);
        var direct = new ProjectionEngine().projectWithOutcome(plan, context, world.economicPath());
        assertInstanceOf(ProjectionExecutionResult.Completed.class, direct);
        var actual = new MonteCarloAnalyzer().analyzeMortality(plan, request(1), i -> world,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var outcome = actual.outcomes().getFirst();
        for (var row : direct.completedYears()) {
            assertEquals(row.getEndingInvestableAssets(), outcome.annualInvestableAssets().get(row.getCalendarYear()));
            assertFalse(row.getSocialSecurityResult().hasSpouse());
            assertTrue(row.getSocialSecurityResult().spouseOwnBenefitIfPresent().isEmpty());
            assertNull(row.getSocialSecurityResult().primarySurvivorCandidate());
            assertEquals(row.getSocialSecurityResult().primaryOwnBenefit(), row.getSocialSecurityResult().householdBenefit());
            assertTrue(row.spouseRothConversion().isEmpty());
            assertEquals(row.getPrimaryRothConversion(), row.getRothConversion());
            assertTrue(row.getGuaranteedIncome().compareTo(row.getSocialSecurityResult().householdBenefit()) > 0, "Actual pension");
            assertTrue(row.getAnnualExpenses().signum() > 0);
            assertTrue(row.getMedicarePremiumCalculation().coveredMedicareParticipants() <= 1);
        }
        assertTrue(direct.completedYears().stream().anyMatch(y -> y.getRequiredMinimumDistribution().signum() > 0));
        assertTrue(direct.completedYears().stream().anyMatch(y -> y.getRothConversion().signum() > 0));
        assertTrue(direct.completedYears().stream().anyMatch(y -> y.getFederalIncomeTax().signum() > 0));
        var metrics = new ProjectionMetricsCalculator().calculate(plan, ((ProjectionExecutionResult.Completed) direct).projection());
        var terminal = outcome.terminal().orElseThrow();
        assertEquals(metrics.endingInvestableAssets(), terminal.endingInvestableAssets());
        assertEquals(metrics.endingNetWorth(), terminal.endingNetWorth());
        assertEquals(metrics.afterTaxEstate(), terminal.afterTaxEstate());
        assertEquals(metrics.totalTaxes(), terminal.lifetimeTaxes());
        assertEquals(balances, plan.getAccountPortfolio().getAccounts().stream().map(a -> a.getCurrentBalance()).toList());
        // Projection beyond death separately verifies cessation; MC itself correctly stops before death.
        var after = new ProjectionEngine().projectWithOutcome(plan, context.withExactEndingYear(2046), world.economicPath());
        for (var row : after.completedYears()) if (row.getCalendarYear() >= 2045) {
            assertEquals(0, row.getSocialSecurityResult().householdBenefit().signum());
            assertEquals(0, row.getGuaranteedIncome().signum());
            assertEquals(0, row.getRequiredMinimumDistribution().signum());
            assertEquals(0, row.getRothConversion().signum());
            assertEquals(0, row.getMedicarePremiumCalculation().coveredMedicareParticipants());
        }
    }

    @Test void fixedAndPairedModesUseActualSinglePlanAndCachedPairedStatistics() {
        var plan = SinglePersonMortalityAnalysisTest.plan();
        var fixedSettings = new MonteCarloSettings(3, 417, plan.getPlanningAssumptions().getExpectedAnnualInvestmentReturn(), BigDecimal.ZERO);
        var fixed = new MonteCarloAnalyzer().analyze(plan, fixedSettings);
        var oracle = new ProjectionMetricsCalculator().calculate(plan, new ProjectionEngine().project(plan));
        assertEquals(0, fixed.endingInvestableAssets().orElseThrow().p50().compareTo(oracle.endingInvestableAssets()));
        var a = new MonteCarloStrategyCandidate("Current Plan", plan);
        var b = new MonteCarloStrategyCandidate("Saved Baseline", plan);
        var start = plan.getPlanningAssumptions().getProjectionStartDate();
        for (var assumptions : java.util.List.<MonteCarloStrategyComparisonRequest.Assumptions>of(
                new MonteCarloStrategyComparisonRequest.Fixed(fixedSettings, start, start.getYear() + 19,
                        new MonteCarloLifetime.Individual(new MonteCarloLifetime.Life(Optional.empty()))),
                new MonteCarloStrategyComparisonRequest.Longevity(request(3)))) {
            var result = new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(
                    new MonteCarloStrategyComparisonRequest(a, b, assumptions), AnalysisProgressListener.none(), AnalysisCancellationToken.none());
            for (var pair : result.outcomes()) {
                assertInstanceOf(MonteCarloLifetime.Individual.class, pair.lifetime());
                assertEquals(pair.outcomeA(), pair.outcomeB());
                pair.deltas().ifPresent(delta -> assertEquals(0, delta.investableAssets().signum()));
            }
            assertSame(result.summary(), result.summary());
        }
    }

    @Test void openingDeathAndFundingFailureRemainDifferentPopulations() {
        var plan = SinglePersonMortalityAnalysisTest.plan();
        var opening = new MonteCarloWorld(0, ProjectionEconomicPath.constant(BigDecimal.ZERO),
                MonteCarloLifetime.individual(Year.of(2027)), Optional.empty());
        var result = new MonteCarloAnalyzer().analyzeMortality(plan, request(1), i -> opening,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(1, result.completedCount());
        assertTrue(result.annualResults().isEmpty());
        assertEquals(plan.getPlanningAssumptions().getProjectionStartDate(), result.outcomes().getFirst().terminal().orElseThrow().balanceDate());
        plan.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense("Unfundable", new BigDecimal("1000000000")));
        var later = new MonteCarloWorld(0, ProjectionEconomicPath.constant(BigDecimal.ZERO),
                MonteCarloLifetime.individual(Year.of(2040)), Optional.empty());
        var failed = new MonteCarloAnalyzer().analyzeMortality(plan, request(1), i -> later,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(1, failed.fundingFailureCount());
        assertEquals(0, failed.fundingProbability().signum());
        assertTrue(failed.endingInvestableAssets().isEmpty());
        assertTrue(failed.outcomes().getFirst().fundingFailure().isPresent());
        assertEquals(1, failed.annualResults().get(2027).livingFundingFailedByYearCount());
        assertTrue(failed.annualResults().get(2027).investableAssets().isEmpty());
    }

    @Test void individualOutcomesAndPopulationsAreNotSurvivingCouples() {
        var plan = SinglePersonMortalityAnalysisTest.plan();
        var result = new MonteCarloAnalyzer().analyzeMortality(plan, request(12));
        var repeated = new MonteCarloAnalyzer().analyzeMortality(plan, request(12));
        assertEquals(result.outcomes(), repeated.outcomes());
        assertEquals(result.annualResults(), repeated.annualResults());
        assertEquals(12, result.completedCount() + result.fundingFailureCount());
        for (var outcome : result.outcomes()) {
            assertInstanceOf(MonteCarloLifetime.Individual.class, outcome.lifetime());
            assertThrows(IllegalStateException.class, outcome::secondDeathYear);
            outcome.terminal().ifPresent(t -> assertEquals(outcome.lifetime().terminalDeathYear() - 1, t.balanceDate().getYear()));
        }
        for (var annual : result.annualResults().values()) {
            assertTrue(annual.couplePopulation().isEmpty());
            assertEquals(12, annual.livingHouseholdCount() + annual.deceasedCount());
            assertEquals(annual.livingHouseholdCount(), annual.completedLivingYearSampleCount() + annual.livingFundingFailedByYearCount());
            assertEquals(annual.completedLivingYearSampleCount(), annual.investableAssets().map(MonteCarloPercentiles::sampleCount).orElse(0));
        }
    }

    static MonteCarloSettings settings(int count) {
        return new MonteCarloSettings(count, 417, new BigDecimal("0.045"), new BigDecimal("0.15"));
    }
    static MonteCarloMortalityRequest request(int count) {
        return MonteCarloMortalityRequest.individual(SinglePersonMortalityAnalysisTest.plan(), settings(count),
                SocialSecurityMortalityAdjustment.standard());
    }

    @Test void compositionIsStructuralAndIncompleteCoupleCannotBecomeIndividual() {
        var single = MonteCarloLifetime.individual(Year.of(2050));
        assertFalse(single.hasSpouse());
        assertEquals(1, single.lives().size());
        assertEquals(2050, single.terminalDeathYear());
        var couple = MonteCarloLifetime.couple(new HouseholdLifetimeScenario(Optional.of(Year.of(2050)), Optional.empty()));
        assertTrue(couple.hasSpouse());
        assertEquals(2, couple.lives().size());
        assertTrue(couple.living(2060));
        assertThrows(IllegalArgumentException.class, couple::terminalDeathYear);
        assertFalse(single.living(2050));
    }

    @Test void individualSamplingUsesOnlyPrimaryDimensionAndRepeatsAcrossCountAndOrder() {
        var input = request(8);
        assertFalse(input.hasSpouse());
        assertTrue(input.survivorClaimingAge().isEmpty());
        assertThrows(IllegalStateException.class, input::longevityAssumptions);
        assertEquals(SocialSecurityMortalityCategory.FEMALE, input.primary().mortalityCategory());
        var generator = new MonteCarloWorldGenerator(input);
        var independent = new MonteCarloWorldGenerator(request(100));
        var deaths = new IndividualLongevityScenarios(new SocialSecurityMortalityDistributionProvider(input.mortalityTable())
                .createDistribution(input.primary())).scenarios();
        var sampler = new DiscreteProbabilitySampler(deaths.stream().map(IndividualLongevityScenarios.DeathScenario::probability).toList());
        for (int i = 7; i >= 0; i--) {
            var world = generator.generate(i);
            assertInstanceOf(MonteCarloLifetime.Individual.class, world.lifetime());
            assertEquals(1, world.lifetime().lives().size());
            assertEquals(world.lifetime(), independent.generate(i).lifetime());
            assertEquals(world.economicPath().annualReturns(), independent.generate(i).economicPath().annualReturns());
            var random = MonteCarloRandomStreams.create(417, i, MonteCarloRandomStreams.PRIMARY_MORTALITY, 1);
            assertEquals(deaths.get(sampler.sample(random)).deathDate().getYear(), world.lifetime().terminalDeathYear());
        }
    }
}
