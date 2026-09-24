package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.estate.EstateAtSecondDeathCalculator;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjectionService;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloMortalityExecutionTest {

    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    static RetirementPlan plan() {
        var plan = MonteCarloWorldGeneratorTest.plan();
        configure(plan, LocalDate.of(2027, 1, 1), 30, 67);
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, new BigDecimal("10000"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL));
        return plan;
    }

    static void configure(RetirementPlan plan, LocalDate start, int length, Integer survivorAge) {
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                a.getWithdrawalAssumptions(), new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE,
                null, survivorAge, new BigDecimal("0.75")), length, start));
    }

    static MonteCarloMortalityRequest request(RetirementPlan plan, int count, String volatility) {
        return MonteCarloWorldGeneratorTest.request(plan,
                MonteCarloSettings.forPlan(plan, count, 417, new BigDecimal(volatility)));
    }

    static MonteCarloWorld world(int index, int primary, int spouse, ProjectionEconomicPath path) {
        return new MonteCarloWorld(index, path,
                new HouseholdLifetimeScenario(Optional.of(Year.of(primary)), Optional.of(Year.of(spouse))));
    }

    static ProjectionEvaluationContext context(MonteCarloWorld world) {
        return ProjectionEvaluationContext.withLifetimeScenario(world.lifetimeScenario()).withExactEndingYear(
                Math.max(world.lifetimeScenario().primaryDeathYear().orElseThrow().getValue(),
                        world.lifetimeScenario().spouseDeathYear().orElseThrow().getValue()) - 1);
    }

    static MonteCarloMortalityAnalysisResult run(RetirementPlan plan, MonteCarloWorld world) {
        return new MonteCarloAnalyzer().analyzeMortality(plan, request(plan, 1, "0"), index -> world,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
    }

    static void assertOracle(RetirementPlan plan, MonteCarloWorld world,
            MonteCarloMortalityAnalysisResult.WorldOutcome actual) {
        var direct = new ProjectionEngine().projectWithOutcome(plan, context(world), world.economicPath());
        var annual = new TreeMap<Integer, BigDecimal>();
        direct.completedYears().forEach(year -> annual.put(year.getCalendarYear(), year.getEndingInvestableAssets()));
        assertEquals(annual, actual.annualInvestableAssets());
        if (direct instanceof ProjectionExecutionResult.InsufficientFunds failed) {
            assertEquals(failed.fundingFailure(), actual.fundingFailure().orElseThrow());
            assertTrue(actual.terminal().isEmpty());
        } else {
            var projection = ((ProjectionExecutionResult.Completed) direct).projection();
            var metrics = new ProjectionMetricsCalculator().calculate(plan, projection);
            var terminal = actual.terminal().orElseThrow();
            assertEquals(metrics.endingInvestableAssets(), terminal.endingInvestableAssets());
            assertEquals(metrics.endingNetWorth(), terminal.endingNetWorth());
            assertEquals(metrics.afterTaxEstate(), terminal.afterTaxEstate());
            assertEquals(metrics.totalTaxes(), terminal.lifetimeTaxes());
            var estate = new EstateAtSecondDeathCalculator().calculate(plan, projection,
                    LocalDate.of(actual.secondDeathYear(), 1, 1));
            assertEquals(estate.nominalAfterTaxEstate(), terminal.afterTaxEstate());
            assertEquals(estate.balanceDate(), terminal.balanceDate());
            assertTrue(actual.fundingFailure().isEmpty());
        }
    }

    @ParameterizedTest
    @CsvSource({"2041,2044", "2044,2041"})
    void bothFirstDeathDirectionsRouteAllSurvivorMechanicsThroughEngine(int primaryDeath, int spouseDeath) {
        var plan = plan();
        boolean primaryDies = primaryDeath < spouseDeath;
        var deceased = primaryDies ? plan.getHousehold().getPrimaryPerson() : plan.getHousehold().getSpouse();
        var survivor = primaryDies ? plan.getHousehold().getSpouse() : plan.getHousehold().getPrimaryPerson();
        var deceasedOwner = primaryDies ? AccountOwnership.PRIMARY : AccountOwnership.SPOUSE;
        var survivorOwner = primaryDies ? AccountOwnership.SPOUSE : AccountOwnership.PRIMARY;
        for (var person : List.of(deceased, survivor)) {
            for (var income : List.copyOf(person.getIncomeSources())) {
                person.removeIncomeSource(income);
            }
        }
        deceased.addIncomeSource(new SocialSecurityIncome("Deceased SS", deceasedOwner,
                deceased.getBirthDate().plusYears(67), null, new BigDecimal("5000"), 67, BigDecimal.ZERO, 2027));
        survivor.addIncomeSource(new SocialSecurityIncome("Survivor SS", survivorOwner,
                survivor.getBirthDate().plusYears(67), null, new BigDecimal("1000"), 67, BigDecimal.ZERO, 2027));
        deceased.addIncomeSource(new Pension("Survivor pension", deceasedOwner, LocalDate.of(2027, 1, 1),
                null, new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("500")));
        var world = world(0, primaryDeath, spouseDeath, ProjectionEconomicPath.constant(BigDecimal.ZERO));
        var direct = new ProjectionEngine().projectWithOutcome(plan, context(world), world.economicPath());
        assertInstanceOf(ProjectionExecutionResult.Completed.class, direct);
        var calls = new AtomicInteger();
        var before = JSON.valueToTree(plan);
        var observer = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(RetirementPlan isolated,
                    ProjectionEvaluationContext supplied, ProjectionEconomicPath path) {
                calls.incrementAndGet();
                assertNotSame(plan, isolated);
                assertEquals(context(world), supplied);
                assertTrue(supplied.socialSecurityStrategy().isEmpty());
                var actual = super.projectWithOutcome(isolated, supplied, path);
                // Full-row oracle covers SS, pension, expense factor, taxes, Medicare, RMD, Roth and account values.
                assertEquals(JSON.valueToTree(direct.completedYears()), JSON.valueToTree(actual.completedYears()));
                return actual;
            }
        };
        var result = new MonteCarloAnalyzer(observer).analyzeMortality(plan, request(plan, 1, "0"), i -> world,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(1, calls.get());
        assertEquals(before, JSON.valueToTree(plan));
        assertOracle(plan, world, result.outcomes().getFirst());
        var rows = direct.completedYears();
        var beforeDeath = rows.get(2040 - 2027);
        var death = rows.get(2041 - 2027);
        var afterDeath = rows.get(2042 - 2027);
        var ss = death.getSocialSecurityResult();
        assertTrue((primaryDies ? ss.spouseSurvivorCandidate() : ss.primarySurvivorCandidate()).signum() > 0);
        assertEquals(0, (primaryDies ? ss.primarySelectedBenefit() : ss.spouseSelectedBenefit()).signum());
        assertEquals(0, new BigDecimal("6000").compareTo(death.getGuaranteedIncome().subtract(ss.householdBenefit())));
        assertTrue(death.getAnnualExpenses().compareTo(beforeDeath.getAnnualExpenses()) < 0);
        assertTrue(afterDeath.getFederalStandardDeduction().compareTo(death.getFederalStandardDeduction()) < 0);
        assertEquals(2, beforeDeath.getMedicarePremiumCalculation().coveredMedicareParticipants());
        assertEquals(1, death.getMedicarePremiumCalculation().coveredMedicareParticipants());
        assertEquals(1, afterDeath.getMedicarePremiumCalculation().coveredMedicareParticipants());
        assertTrue(death.getRequiredMinimumDistribution().signum() > 0);
        assertEquals(0, (primaryDies ? death.getPrimaryRothConversion() : death.getSpouseRothConversion()).signum());
        assertTrue((primaryDies ? death.getSpouseRothConversion() : death.getPrimaryRothConversion()).signum() > 0);
        var ira = plan.getAccountPortfolio().getAccounts().stream()
                .filter(account -> account instanceof TraditionalIRA && account.getOwnership() == deceasedOwner)
                .findFirst().orElseThrow();
        // With zero returns and brokerage funding, the deceased IRA stays intact: no own RMD or conversion.
        assertTrue(beforeDeath.getEndingBalance(ira).signum() > 0);
        assertEquals(beforeDeath.getEndingBalance(ira), death.getEndingBalance(ira));
    }

    @ParameterizedTest
    @CsvSource({"2030,2030,2029", "2055,2056,2055", "2056,2057,2056", "2060,2065,2064"})
    void sampledLifetimeDeterminesExactHorizonIncludingSameYearDeaths(int primary, int spouse, int last) {
        var plan = plan();
        var world = world(0, primary, spouse, ProjectionEconomicPath.constant(new BigDecimal("0.045")));
        var result = run(plan, world);
        assertEquals(1, result.completedCount());
        var outcome = result.outcomes().getFirst();
        assertEquals(last, outcome.finalLivingFinancialYear());
        assertEquals(last - 2027 + 1, outcome.annualInvestableAssets().size());
        assertEquals(last, Collections.max(outcome.annualInvestableAssets().keySet()));
        assertOracle(plan, world, outcome);
    }

    @Test
    void generatedTerminalAge120ExtendsExecutionToAuthoritativeMappedYear() {
        var plan = plan();
        var request = new MonteCarloMortalityRequest(plan, MonteCarloSettings.forPlan(plan, 1, 417, BigDecimal.ZERO),
                LongevitySessionSettings.defaults(LocalDate.of(2027, 1, 1)),
                MonteCarloWorldGeneratorTest.deathTable(120, 120));
        var world = new MonteCarloWorldGenerator(request).generate(0);
        var result = new MonteCarloAnalyzer().analyzeMortality(plan, request);
        assertEquals(1, result.completedCount());
        assertEquals(2085, world.lifetimeScenario().spouseDeathYear().orElseThrow().getValue());
        assertEquals(2084, result.outcomes().getFirst().finalLivingFinancialYear());
        assertOracle(plan, world, result.outcomes().getFirst());
    }

    @Test
    void openingDeathUsesOpeningAssetsAndEstateWithoutEngineCall() {
        var plan = plan();
        var calls = new AtomicInteger();
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(RetirementPlan p,
                    ProjectionEvaluationContext context, ProjectionEconomicPath path) {
                calls.incrementAndGet();
                throw new AssertionError("Opening second death must not execute financial years");
            }
        };
        var world = world(0, 2027, 2027, ProjectionEconomicPath.constant(BigDecimal.ZERO));
        var result = new MonteCarloAnalyzer(engine).analyzeMortality(plan, request(plan, 1, "0"), i -> world,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var actual = result.outcomes().getFirst().terminal().orElseThrow();
        var expected = new EstateAtSecondDeathCalculator().calculateOpening(plan, LocalDate.of(2027, 1, 1));
        var nonInvestable = new NonInvestableAssetProjectionService()
                .project(plan.getNonInvestableAssets(), 2027, 2027).getFirst().getTotalValue();
        assertEquals(expected.nominalInvestableAssets(), actual.endingInvestableAssets());
        assertEquals(expected.nominalInvestableAssets().add(nonInvestable), actual.endingNetWorth());
        assertEquals(expected.nominalAfterTaxEstate(), actual.afterTaxEstate());
        assertTrue(actual.afterTaxEstate().signum() > 0);
        assertEquals(BigDecimal.ZERO, actual.lifetimeTaxes());
        assertEquals(expected.balanceDate(), actual.balanceDate());
        assertTrue(result.outcomes().getFirst().annualInvestableAssets().isEmpty());
        assertEquals(1, result.completedCount());
        assertEquals(0, calls.get());
    }

    @Test
    void secondDeathBeforeAvailableOpeningBalancesAbortsRatherThanCreatingZeroEstate() {
        var plan = plan();
        configure(plan, LocalDate.of(2027, 7, 1), 30, 67);
        var error = assertThrows(MonteCarloExecutionException.class,
                () -> run(plan, world(0, 2027, 2027, ProjectionEconomicPath.constant(BigDecimal.ZERO))));
        assertTrue(error.getCause().getMessage().contains("precedes available opening balances"));
        var world = world(0, 2030, 2031, ProjectionEconomicPath.constant(new BigDecimal("0.045")));
        assertOracle(plan, world, run(plan, world).outcomes().getFirst());
    }

    @Test
    void openingDeathRetainsAuthoritativeOpeningDistributionConsistencyCheck() {
        var plan = plan();
        var ira = plan.getAccountPortfolio().getAccounts().stream()
                .filter(TraditionalIRA.class::isInstance).findFirst().orElseThrow();
        ira.setOpeningRmdAccountData(new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(
                2027, ira.getCurrentBalance(), BigDecimal.ONE));
        var direct = assertThrows(IllegalStateException.class,
                () -> new EstateAtSecondDeathCalculator().calculateOpening(plan, LocalDate.of(2027, 1, 1)));
        var error = assertThrows(MonteCarloExecutionException.class,
                () -> run(plan, world(0, 2027, 2027, ProjectionEconomicPath.constant(BigDecimal.ZERO))));
        assertEquals(direct.getClass(), error.getCause().getClass());
        assertEquals(direct.getMessage(), error.getCause().getMessage());
    }

    @Test
    void zeroVolatilityAndRepeatedSeedUseSameWorldsAndDirectEngineValues() {
        var plan = plan();
        for (String volatility : List.of("0", "0.12")) {
            var request = request(plan, 5, volatility);
            var generator = new MonteCarloWorldGenerator(request);
            var analyzer = new MonteCarloAnalyzer();
            var first = analyzer.analyzeMortality(plan, request);
            assertEquals(first, analyzer.analyzeMortality(plan, request));
            assertEquals(MonteCarloScenarioGenerator.MODEL_VERSION, first.returnModel());
            assertEquals("HOUSEHOLD_MORTALITY_V1", first.mortalityModel());
            for (int index = 0; index < 5; index++) {
                var world = generator.generate(index);
                assertOracle(plan, world, first.outcomes().get(index));
                if (volatility.equals("0")) {
                    assertTrue(world.economicPath().annualReturns().values().stream()
                            .allMatch(request.settings().expectedReturn()::equals));
                    var constant = world(index, world.lifetimeScenario().primaryDeathYear().orElseThrow().getValue(),
                            world.lifetimeScenario().spouseDeathYear().orElseThrow().getValue(),
                            ProjectionEconomicPath.constant(request.settings().expectedReturn()));
                    assertOracle(plan, constant, first.outcomes().get(index));
                }
            }
        }
    }

    static RetirementPlan fundingPlan(String balance) {
        var plan = MonteCarloFundingTest.plan(balance, "100", 2);
        plan.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.MALE);
        plan.getHousehold().getSpouse().setMortalityCategory(MortalityCategory.FEMALE);
        for (var owner : List.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE)) {
            var person = owner == AccountOwnership.PRIMARY ? plan.getHousehold().getPrimaryPerson() : plan.getHousehold().getSpouse();
            person.addIncomeSource(new SocialSecurityIncome("Future SS", owner, person.getBirthDate().plusYears(67),
                    null, BigDecimal.ONE, 67, BigDecimal.ZERO, 2027));
        }
        configure(plan, LocalDate.of(2027, 1, 1), 2, 67);
        return plan;
    }

    @Test
    void failurePrefixMatchesEngineAndEarlyDeathAvoidsHypotheticalLaterFailure() {
        var plan = fundingPlan("250");
        var path = ProjectionEconomicPath.constant(BigDecimal.ZERO);
        var result = new MonteCarloAnalyzer().analyzeMortality(plan, request(plan, 2, "0"),
                i -> world(i, i == 0 ? 2029 : 2031, i == 0 ? 2029 : 2031, path),
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(1, result.completedCount());
        assertEquals(1, result.fundingFailureCount());
        assertEquals(new BigDecimal("0.5"), result.fundingProbability());
        assertOracle(plan, world(0, 2029, 2029, path), result.outcomes().get(0));
        assertOracle(plan, world(1, 2031, 2031, path), result.outcomes().get(1));
        var failed = result.outcomes().get(1);
        assertEquals(Set.of(2027, 2028), failed.annualInvestableAssets().keySet());
        assertTrue(failed.terminal().isEmpty());
        assertEquals(2029, failed.fundingFailure().orElseThrow().calendarYear());
        assertEquals(Map.of(2029, 1L), result.fundingFailureStatistics().orElseThrow().countByYear());
    }

    @Test
    void zeroEndingAssetsCanBeSuccessfullyFunded() {
        var plan = fundingPlan("100");
        var result = run(plan, world(0, 2028, 2028, ProjectionEconomicPath.constant(BigDecimal.ZERO)));
        assertEquals(1, result.completedCount());
        assertEquals(0, result.fundingFailureCount());
        assertEquals(BigDecimal.ONE, result.fundingProbability());
        assertEquals(0, result.outcomes().getFirst().terminal().orElseThrow().endingInvestableAssets().signum());
    }

    @Test
    void missingSurvivorPolicyAndUnsupportedPlanFailBeforeGeneratingWorlds() {
        var plan = plan();
        configure(plan, LocalDate.of(2027, 1, 1), 30, null);
        var count = new AtomicInteger();
        var error = assertThrows(IllegalArgumentException.class, () -> new MonteCarloAnalyzer().analyzeMortality(
                plan, request(plan, 5000, "0"), i -> {
                    count.incrementAndGet();
                    throw new AssertionError();
                }, AnalysisProgressListener.none(), AnalysisCancellationToken.none()));
        assertTrue(error.getMessage().contains("persisted survivor claiming age"));
        assertEquals(0, count.get());
        configure(plan, LocalDate.of(2027, 1, 1), 30, 67);
        var spouse = plan.getHousehold().getSpouse();
        spouse.removeIncomeSource(spouse.getIncomeSources().getFirst());
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloAnalyzer().analyzeMortality(plan, request(plan, 1, "0")));
    }

    @Test
    void staleGenerationRequestIsRejected() {
        var plan = plan();
        var request = request(plan, 1, "0");
        configure(plan, LocalDate.of(2028, 1, 1), 30, 67);
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloAnalyzer().analyzeMortality(plan, request));
    }
}
