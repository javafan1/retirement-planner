package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PensionCashTaxConsistencyTest {
    static void money(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "Expected " + expected + ", actual " + actual);
    }

    static void removePensions(RetirementPlan plan) {
        for (var person : plan.getHousehold().members())
            person.getIncomeSources().stream().filter(Pension.class::isInstance).toList().forEach(person::removeIncomeSource);
    }
    static RetirementPlan survivorOnlyPlan(DeathScenario scenario) {
        var plan = LifetimeProjectionTestSupport.plan(new DeathScenarioAssumptions(scenario, 2031, 67));
        for (var person : plan.getHousehold().members()) {
            for (var income : List.copyOf(person.getIncomeSources())) person.removeIncomeSource(income);
        }
        for (var entry : plan.getHousehold().peopleByOwner().entrySet()) {
            entry.getValue().addIncomeSource(new SocialSecurityIncome("Synthetic zero SS", entry.getKey(),
                    entry.getValue().getBirthDate().plusYears(67), null, BigDecimal.ZERO, 67, BigDecimal.ZERO, 2030));
        }
        var owner = scenario == DeathScenario.PRIMARY_DIES ? AccountOwnership.PRIMARY : AccountOwnership.SPOUSE;
        plan.getHousehold().peopleByOwner().get(owner).addIncomeSource(new Pension("Independent survivor pension", owner,
                LocalDate.of(2030, 1, 1), null, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("2500")));
        var p = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(new EconomicAssumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO), p.getTaxAssumptions(), p.getWithdrawalAssumptions(),
                p.getDeathScenarioAssumptions(), p.getProjectionLengthYears(), p.getProjectionStartDate(), p.getStateTaxAssumptions()));
        return plan;
    }

    @ParameterizedTest
    @EnumSource(value = DeathScenario.class, names = {"PRIMARY_DIES", "SPOUSE_DIES"})
    void existingLifetimeSelectionProvidesTheIndependentSurvivorTaxOracle(DeathScenario scenario) {
        var plan = survivorOnlyPlan(scenario);
        var timing = new HouseholdLifetimeScenario(
                scenario == DeathScenario.PRIMARY_DIES ? Optional.of(Year.of(2031)) : Optional.empty(),
                scenario == DeathScenario.SPOUSE_DIES ? Optional.of(Year.of(2031)) : Optional.empty());
        var projection = new ProjectionEngine().project(plan, ProjectionEvaluationContext.withLifetimeScenario(timing));
        for (var year : projection.getYears()) {
            var expected = year.getCalendarYear() == 2030 ? BigDecimal.ZERO : new BigDecimal("30000");
            assertEquals(0, expected.compareTo(year.getGuaranteedIncome()), "12 monthly survivor payments");
            assertEquals(0, expected.compareTo(year.getAdjustedGrossIncome()), "Fully taxable selected pension");
            var sources = year.getIncomeSources().stream()
                    .filter(source -> source.kind() == IncomeSourceAttribution.Kind.PENSION).toList();
            assertEquals(0, expected.compareTo(sources.stream().map(IncomeSourceAttribution::grossAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)), "Attributed pension reconciles without double cash");
        }
    }

    @ParameterizedTest
    @EnumSource(value = DeathScenario.class, names = {"PRIMARY_DIES", "SPOUSE_DIES"})
    void zeroOwnerPositiveSurvivorUsesTheSameSelectedPensionForCashAndTaxes(DeathScenario scenario) throws Exception {
        var plan = survivorOnlyPlan(scenario);
        String before = LifetimeProjectionTestSupport.json(plan);
        var engine = new ProjectionEngine();
        var ordinary = engine.project(plan);
        var timing = new HouseholdLifetimeScenario(scenario == DeathScenario.PRIMARY_DIES ? Optional.of(Year.of(2031)) : Optional.empty(),
                scenario == DeathScenario.SPOUSE_DIES ? Optional.of(Year.of(2031)) : Optional.empty());
        var lifetime = engine.project(plan, ProjectionEvaluationContext.withLifetimeScenario(timing));
        var owner = scenario == DeathScenario.PRIMARY_DIES ? AccountOwnership.PRIMARY : AccountOwnership.SPOUSE;
        var recipient = owner == AccountOwnership.PRIMARY ? AccountOwnership.SPOUSE : AccountOwnership.PRIMARY;
        for (int index = 0; index < ordinary.size(); index++) {
            var a = ordinary.getYearAt(index); var b = lifetime.getYearAt(index);
            var expected = index == 0 ? BigDecimal.ZERO : new BigDecimal("30000");
            assertEquals(0, expected.compareTo(a.getGuaranteedIncome()), "Selected cash in " + a.getCalendarYear());
            assertEquals(0, expected.compareTo(b.getGuaranteedIncome()), "Equivalent lifetime cash");
            assertEquals(0, expected.compareTo(a.getAdjustedGrossIncome()), "Selected pension must supply ordinary AGI in " + a.getCalendarYear());
            assertEquals(0, expected.compareTo(b.getAdjustedGrossIncome()), "Equivalent lifetime AGI");
            var sources = a.getIncomeSources().stream().filter(s -> s.kind() == IncomeSourceAttribution.Kind.PENSION).toList();
            if (expected.signum() == 0) assertTrue(sources.isEmpty());
            else {
                assertEquals(1, sources.size(), "One pension source, not owner plus survivor");
                var source = sources.getFirst();
                assertEquals(recipient, source.owner().orElseThrow());
                assertEquals(owner, source.pension().orElseThrow().originalOwner());
                assertEquals(0, expected.compareTo(source.grossAmount()));
                assertEquals(0, expected.compareTo(source.modeledTaxableAmount().orElseThrow()));
            }
        }
        assertEquals(before, LifetimeProjectionTestSupport.json(plan), "Financial execution must not change input accounts or pensions");
    }

    @ParameterizedTest
    @EnumSource(value = DeathScenario.class, names = {"PRIMARY_DIES", "SPOUSE_DIES"})
    void independentDollarAmountsDatesAndColaAgreeInBothDeathDirections(DeathScenario scenario) {
        var owner = scenario == DeathScenario.PRIMARY_DIES ? AccountOwnership.PRIMARY : AccountOwnership.SPOUSE;
        var cases = List.of(
                new Object[]{LocalDate.of(2030, 1, 1), LocalDate.of(2033, 6, 30), "1000", "500", ".10",
                        List.of("12000", "6600", "7260", "3993", "0")},
                new Object[]{LocalDate.of(2032, 7, 1), LocalDate.of(2033, 6, 30), "1000", "500", "0",
                        List.of("0", "0", "3000", "3000", "0")},
                new Object[]{LocalDate.of(2030, 1, 1), null, "1000", "0", "0",
                        List.of("12000", "0", "0", "0", "0")});
        for (var example : cases) {
            var plan = survivorOnlyPlan(scenario);
            removePensions(plan);
            plan.getHousehold().peopleByOwner().get(owner).addIncomeSource(new Pension("Scheduled pension", owner,
                    (LocalDate) example[0], (LocalDate) example[1], new BigDecimal((String) example[2]),
                    new BigDecimal((String) example[4]), new BigDecimal((String) example[3])));
            var ordinary = new ProjectionEngine().project(plan);
            var timing = new HouseholdLifetimeScenario(scenario == DeathScenario.PRIMARY_DIES ? Optional.of(Year.of(2031)) : Optional.empty(),
                    scenario == DeathScenario.SPOUSE_DIES ? Optional.of(Year.of(2031)) : Optional.empty());
            var lifetime = new ProjectionEngine().project(plan, ProjectionEvaluationContext.withLifetimeScenario(timing));
            var amounts = (List<?>) example[5];
            for (int index = 0; index < ordinary.size(); index++) {
                var expected = new BigDecimal((String) amounts.get(index));
                money(expected, ordinary.getYearAt(index).getGuaranteedIncome());
                money(expected, ordinary.getYearAt(index).getAdjustedGrossIncome());
                money(expected, lifetime.getYearAt(index).getAdjustedGrossIncome());
            }
        }
    }

    @Test void livingOwnerAndMultiplePensionsRetainTheirOwnSourceAndParticipantFacts() {
        var plan = survivorOnlyPlan(DeathScenario.PRIMARY_DIES);
        var facts = new RetirementParticipantFacts(RetirementParticipantFacts.SeparationStatus.SEPARATED,
                LocalDate.of(2028, 1, 1), RetirementParticipantFacts.RetirementConditionsStatus.MET, LocalDate.of(2028, 1, 1));
        var original = (Pension) plan.getHousehold().getPrimaryPerson().getIncomeSources().stream()
                .filter(Pension.class::isInstance).findFirst().orElseThrow();
        original.setRetirementParticipantFacts(facts);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(new Pension("Second deceased-owner pension", AccountOwnership.PRIMARY,
                LocalDate.of(2030, 1, 1), null, new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("500")));
        plan.getHousehold().getSpouse().addIncomeSource(new Pension("Living spouse pension", AccountOwnership.SPOUSE,
                LocalDate.of(2030, 1, 1), null, new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("10")));
        var projection = new ProjectionEngine().project(plan);
        money(new BigDecimal("13200"), projection.getYearAt(0).getGuaranteedIncome());
        for (var year : projection.getYears().subList(1, projection.size())) {
            money(new BigDecimal("37200"), year.getGuaranteedIncome());
            money(new BigDecimal("37200"), year.getAdjustedGrossIncome());
            var sources = year.getIncomeSources().stream().filter(s -> s.kind() == IncomeSourceAttribution.Kind.PENSION).toList();
            assertEquals(3, sources.size());
            assertTrue(sources.stream().allMatch(s -> s.owner().orElseThrow() == AccountOwnership.SPOUSE));
            var survivor = sources.stream().filter(s -> s.grossAmount().compareTo(new BigDecimal("30000")) == 0).findFirst().orElseThrow();
            assertEquals(AccountOwnership.PRIMARY, survivor.pension().orElseThrow().originalOwner());
            assertEquals(facts, survivor.pension().orElseThrow().participantFacts());
        }
    }

    @Test void noDeathAndSinglePersonPensionsKeepTheirExistingScheduledPayments() {
        var couple = survivorOnlyPlan(DeathScenario.BOTH_SURVIVE);
        removePensions(couple);
        couple.getHousehold().getPrimaryPerson().addIncomeSource(new Pension("Own", AccountOwnership.PRIMARY,
                LocalDate.of(2030, 1, 1), null, new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("2500")));
        for (var year : new ProjectionEngine().project(couple).getYears()) {
            money(new BigDecimal("12000"), year.getGuaranteedIncome());
            money(new BigDecimal("12000"), year.getAdjustedGrossIncome());
        }
        var single = com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.plan(false);
        single.getHousehold().getPrimaryPerson().addIncomeSource(new Pension("Own", AccountOwnership.PRIMARY,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 30), new BigDecimal("1000"), BigDecimal.ZERO));
        var projection = new ProjectionEngine().project(single);
        for (int index = 0; index < 3; index++) {
            var expected = new BigDecimal(List.of("12000", "6000", "0").get(index));
            money(expected, projection.getYearAt(index).getAdjustedGrossIncome().subtract(projection.getYearAt(index).getTaxableSocialSecurity()));
        }
        var singleDeath = new ProjectionEngine().project(single, ProjectionEvaluationContext.withLifetimeScenario(
                new HouseholdLifetimeScenario(Optional.of(Year.of(2027)), Optional.empty())));
        for (var year : singleDeath.getYears().subList(1, singleDeath.size())) {
            money(BigDecimal.ZERO, year.getGuaranteedIncome());
            money(BigDecimal.ZERO, year.getAdjustedGrossIncome());
            assertTrue(year.getIncomeSources().stream().noneMatch(source -> source.kind() == IncomeSourceAttribution.Kind.PENSION));
        }
    }

    @ParameterizedTest
    @CsvSource({"MI,true,2500", "MI,false,15000", "IL,true,15000", "IL,false,2500"})
    void selectedPensionReachesRothRmdIrmaaTaxFundingAndSettlement(String state, boolean taxableAssets, String monthly) throws Exception {
        var plan = survivorOnlyPlan(DeathScenario.PRIMARY_DIES);
        removePensions(plan);
        var pension = new Pension("Survivor", AccountOwnership.PRIMARY, LocalDate.of(2030, 1, 1), null,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(monthly));
        pension.setRetirementParticipantFacts(new RetirementParticipantFacts(RetirementParticipantFacts.SeparationStatus.SEPARATED,
                LocalDate.of(2028, 1, 1), RetirementParticipantFacts.RetirementConditionsStatus.MET, LocalDate.of(2028, 1, 1)));
        plan.getHousehold().getPrimaryPerson().addIncomeSource(pension);
        plan.getHousehold().getSpouse().setBirthDate(LocalDate.of(1955, 6, 15));
        for (var entry : plan.getHousehold().peopleByOwner().entrySet()) {
            var person = entry.getValue();
            person.getIncomeSources().stream().filter(SocialSecurityIncome.class::isInstance).toList().forEach(person::removeIncomeSource);
            person.addIncomeSource(new SocialSecurityIncome("Zero", entry.getKey(), person.getBirthDate().plusYears(67),
                    null, BigDecimal.ZERO, 67, BigDecimal.ZERO, 2030));
        }
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                a.getWithdrawalAssumptions(), a.getDeathScenarioAssumptions(), 3, LocalDate.of(2031, 1, 1),
                StateTaxAssumptions.simplified(StateTaxJurisdiction.valueOf(state))));
        List.copyOf(plan.getHousehold().getExpenses()).forEach(plan.getHousehold()::removeExpense);
        plan.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense("Spending", new BigDecimal("250000")));
        if (!taxableAssets) plan.getAccountPortfolio().getAccounts().forEach(account -> account.setCurrentBalance(BigDecimal.ZERO));
        var ira = new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA("Living IRA", AccountOwnership.SPOUSE, new BigDecimal("2000000"));
        ira.setOpeningRmdAccountData(new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(2031, new BigDecimal("2000000"), BigDecimal.ZERO));
        plan.getAccountPortfolio().addAccount(ira);
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.RothIRA("Living Roth", AccountOwnership.SPOUSE, BigDecimal.ZERO));
        plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(true, 2031, BigDecimal.ZERO,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.NEVER,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ANNUAL));
        var oracle = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(plan);
        removePensions(oracle);
        oracle.getHousehold().getSpouse().addIncomeSource(new Pension("Independent living-recipient oracle", AccountOwnership.SPOUSE,
                LocalDate.of(2030, 1, 1), null, new BigDecimal(monthly), BigDecimal.ZERO));
        String before = LifetimeProjectionTestSupport.json(plan);
        var actual = new ProjectionEngine().project(plan);
        var expected = new ProjectionEngine().project(oracle);
        var lifetime = new ProjectionEngine().project(plan, ProjectionEvaluationContext.withLifetimeScenario(
                new HouseholdLifetimeScenario(Optional.of(Year.of(2031)), Optional.empty())));
        for (int index = 0; index < actual.size(); index++) {
            var year = actual.getYearAt(index);
            // Every monetary annual result must agree with a normal pension paid to the living recipient.
            for (var method : ProjectionYear.class.getMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == BigDecimal.class && method.getName().startsWith("get")) {
                    money((BigDecimal) method.invoke(expected.getYearAt(index)), (BigDecimal) method.invoke(year));
                    money((BigDecimal) method.invoke(lifetime.getYearAt(index)), (BigDecimal) method.invoke(year));
                }
            }
            money(new BigDecimal(monthly).multiply(new BigDecimal("12")), year.getGuaranteedIncome());
            assertTrue(year.getRequiredMinimumDistribution().signum() > 0);
            assertTrue(year.getTaxFundingWithdrawal().signum() > 0);
            if (taxableAssets && monthly.equals("2500") && index == 0) assertTrue(year.getRothConversion().signum() > 0);
            var expectedMedicare = expected.getYearAt(index).getMedicarePremiumCalculation();
            var actualMedicare = year.getMedicarePremiumCalculation();
            money(expectedMedicare.totalAnnualMedicarePremium(), actualMedicare.totalAnnualMedicarePremium());
            money(expectedMedicare.monthlyPartBPremium(), actualMedicare.monthlyPartBPremium());
            money(expectedMedicare.monthlyPartDPremium(), actualMedicare.monthlyPartDPremium());
            assertEquals(expectedMedicare.coveredMedicareParticipants(), actualMedicare.coveredMedicareParticipants());
            money(expectedMedicare.modifiedAdjustedGrossIncome(), actualMedicare.modifiedAdjustedGrossIncome());
            assertTrue(actualMedicare.monthlyPartDPremium().signum() > 0, "The oracle crosses an IRMAA threshold");
            var cash = year.getHouseholdCashSettlement();
            var sources = cash.guaranteedIncome().add(cash.projectedPeriodRmdCash()).add(cash.spendingWithdrawal()).add(cash.taxFundingWithdrawal());
            var uses = cash.ordinaryExpenses().add(cash.medicarePremiums()).add(cash.incomeTaxes()).add(cash.retainedHouseholdSurplus());
            assertTrue(sources.subtract(uses).abs().compareTo(new BigDecimal(".02")) <= 0);
            var attributed = year.getIncomeSources().stream().filter(s -> s.kind() == IncomeSourceAttribution.Kind.PENSION).toList();
            assertEquals(1, attributed.size());
            assertEquals(AccountOwnership.SPOUSE, attributed.getFirst().owner().orElseThrow());
            assertEquals(AccountOwnership.PRIMARY, attributed.getFirst().pension().orElseThrow().originalOwner());
            assertEquals(pension.getRetirementParticipantFacts(), attributed.getFirst().pension().orElseThrow().participantFacts());
            if (state.equals("MI")) assertTrue(year.stateTaxCalculation().orElseThrow().michiganDetails().orElseThrow()
                    .eligibilityDecisions().stream().anyMatch(d -> d.reason().equals("PENSION_SOURCE_QUALIFICATION_UNAVAILABLE")));
        }
        assertEquals(before, LifetimeProjectionTestSupport.json(plan));
    }
}
