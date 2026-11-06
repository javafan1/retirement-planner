package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.*;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitFinancialIntegrationTest {
    static RetirementPlan base(StateTaxJurisdiction state) {
        var plan = PensionCashTaxConsistencyTest.survivorOnlyPlan(DeathScenario.PRIMARY_DIES);
        PensionCashTaxConsistencyTest.removePensions(plan);
        plan.getHousehold().getExpenses().stream().toList().forEach(plan.getHousehold()::removeExpense);
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),
                a.getDeathScenarioAssumptions(),a.getProjectionLengthYears(),LocalDate.of(2030,1,1),StateTaxAssumptions.simplified(state)));
        return plan;
    }
    static ConditionalBenefitDefinition payment(String amount, LocalDate event) {
        return change(change(change(monthly(),"grossAmount",new BigDecimal(amount)),"annualEscalationRate",BigDecimal.ZERO),
                "trigger",new Trigger(TriggerType.FIXED_DATE,event,null,null));
    }
    static void money(String expected, BigDecimal actual) { assertEquals(0,new BigDecimal(expected).compareTo(actual)); }
    @Test void selectedReceiptsAreBothCashAndTaxableNonPensionIncome() {
        var plan = base(StateTaxJurisdiction.FL);
        var benefit = change(payment("2500",LocalDate.of(2030,1,1)),"recipient",PersonReference.SPOUSE);
        plan.setConditionalBenefits(List.of(benefit));
        var projection = new ProjectionEngine().project(plan);
        for (var year : projection.getYears()) {
            money("30000",year.getGuaranteedIncome()); money("30000",year.getAdjustedGrossIncome());
            var sources = year.getIncomeSources().stream().filter(s->s.conditionalBenefit().isPresent()).toList();
            assertEquals(1,sources.size()); assertEquals(IncomeSourceAttribution.Kind.CONDITIONAL_RECURRING,sources.getFirst().kind());
            assertEquals(AccountOwnership.SPOUSE,sources.getFirst().owner().orElseThrow());
            assertTrue(sources.getFirst().pension().isEmpty());
            assertEquals(benefit.id(),sources.getFirst().conditionalBenefit().orElseThrow().benefitId());
            var cash = year.getHouseholdCashSettlement();
            money("30000",cash.guaranteedIncome());
            assertTrue(cash.guaranteedIncome().add(cash.projectedPeriodRmdCash()).add(cash.spendingWithdrawal()).add(cash.taxFundingWithdrawal())
                    .subtract(cash.ordinaryExpenses().add(cash.medicarePremiums()).add(cash.incomeTaxes()).add(cash.retainedHouseholdSurplus())).abs().compareTo(new BigDecimal(".02")) <= 0);
        }
    }
    @Test void survivorAndLumpReceiptsUseConfiguredDeathOnceAndDoNotGrowAtReceipt() throws Exception {
        var plan = base(StateTaxJurisdiction.FL);
        var benefit = change(change(payment("400000",LocalDate.of(2030,1,1)),"recipient",PersonReference.SPOUSE),
                "trigger",new Trigger(TriggerType.DEATH,null,PersonReference.PRIMARY,null));
        benefit = change(benefit,"paymentPattern",PaymentPattern.ONE_TIME_LUMP_SUM);
        plan.setConditionalBenefits(List.of(benefit));
        var before = LifetimeProjectionTestSupport.json(plan);
        var result = new ProjectionEngine().project(plan);
        for (var y : result.getYears()) {
            money(y.getCalendarYear()==2031 ? "400000" : "0",y.getGuaranteedIncome());
            money(y.getCalendarYear()==2031 ? "400000" : "0",y.getAdjustedGrossIncome());
        }
        assertEquals(before,LifetimeProjectionTestSupport.json(plan));
        var again = new ProjectionEngine().project(plan);
        for (int i=0;i<result.size();i++) money(result.getYearAt(i).getEndingInvestableAssets().toPlainString(),again.getYearAt(i).getEndingInvestableAssets());
    }

    @ParameterizedTest @CsvSource({"MI,true,2500", "MI,false,15000", "IL,true,15000", "IL,false,2500", "FL,true,2500"})
    void independentLivingPaymentOracleReconcilesRothRmdIrmaaWithdrawalsAndEstate(String state, boolean taxableAssets, String monthlyAmount) throws Exception {
        var plan = base(StateTaxJurisdiction.valueOf(state));
        plan.getHousehold().getSpouse().setBirthDate(LocalDate.of(1955,6,15));
        for (var entry : plan.getHousehold().peopleByOwner().entrySet()) {
            var person = entry.getValue();
            person.getIncomeSources().stream().filter(SocialSecurityIncome.class::isInstance).toList().forEach(person::removeIncomeSource);
            person.addIncomeSource(new SocialSecurityIncome("Zero",entry.getKey(),person.getBirthDate().plusYears(67),null,BigDecimal.ZERO,67,BigDecimal.ZERO,2030));
        }
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),
                a.getDeathScenarioAssumptions(),3,LocalDate.of(2031,1,1),a.getStateTaxAssumptions()));
        plan.getHousehold().addExpense(new com.daviddunn.retirementplanner.domain.financial.Expense("Spending",new BigDecimal("250000")));
        if (!taxableAssets) plan.getAccountPortfolio().getAccounts().forEach(account -> account.setCurrentBalance(BigDecimal.ZERO));
        var ira = new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA("Living IRA",AccountOwnership.SPOUSE,new BigDecimal("2000000"));
        ira.setOpeningRmdAccountData(new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(2031,new BigDecimal("2000000"),BigDecimal.ZERO));
        plan.getAccountPortfolio().addAccount(ira);
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.RothIRA("Living Roth",AccountOwnership.SPOUSE,BigDecimal.ZERO));
        plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(true,2031,BigDecimal.ZERO,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.NEVER,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ANNUAL));
        var benefit = change(change(payment(monthlyAmount,LocalDate.of(2030,1,1)),"recipient",PersonReference.SPOUSE),
                "trigger",new Trigger(TriggerType.DEATH,null,PersonReference.PRIMARY,null));
        plan.setConditionalBenefits(List.of(benefit));
        var oracle = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(plan);
        oracle.setConditionalBenefits(List.of());
        oracle.getHousehold().getSpouse().addIncomeSource(new Pension("Independent scheduled payment",AccountOwnership.SPOUSE,
                LocalDate.of(2031,1,1),null,new BigDecimal(monthlyAmount),BigDecimal.ZERO,null));
        String original = LifetimeProjectionTestSupport.json(plan);
        var actual = new ProjectionEngine().project(plan);
        var expected = new ProjectionEngine().project(oracle);
        var lifetime = new ProjectionEngine().project(plan,ProjectionEvaluationContext.withLifetimeScenario(
                new HouseholdLifetimeScenario(Optional.of(java.time.Year.of(2031)),Optional.empty())));
        for (int i=0;i<actual.size();i++) {
            var year = actual.getYearAt(i); var control = expected.getYearAt(i);
            for (var getter : ProjectionYear.class.getMethods()) {
                // Conditional income must not become Michigan retirement income; its tax base still reconciles with the conservatively taxed control.
                if (getter.getName().startsWith("get") && getter.getReturnType()==BigDecimal.class && getter.getParameterCount()==0
                        && !getter.getName().equals("getMichiganRetirementIncome")) {
                    var amount = (BigDecimal)getter.invoke(control);
                    money(amount.toPlainString(),(BigDecimal)getter.invoke(year));
                    money(((BigDecimal)getter.invoke(year)).toPlainString(),(BigDecimal)getter.invoke(lifetime.getYearAt(i)));
                }
            }
            var cash = year.getHouseholdCashSettlement();
            assertTrue(cash.guaranteedIncome().add(cash.projectedPeriodRmdCash()).add(cash.spendingWithdrawal()).add(cash.taxFundingWithdrawal())
                    .subtract(cash.ordinaryExpenses().add(cash.medicarePremiums()).add(cash.incomeTaxes()).add(cash.retainedHouseholdSurplus())).abs().compareTo(new BigDecimal(".02"))<=0);
            assertTrue(year.getRequiredMinimumDistribution().signum()>0);
            assertTrue(year.getTaxFundingWithdrawal().signum()>0);
            assertEquals(1,year.getIncomeSources().stream().filter(s -> s.conditionalBenefit().isPresent()).count());
            assertTrue(year.getIncomeSources().stream().noneMatch(s -> s.kind()==IncomeSourceAttribution.Kind.PENSION));
            assertEquals(control.getMedicarePremiumCalculation().modifiedAdjustedGrossIncome(),year.getMedicarePremiumCalculation().modifiedAdjustedGrossIncome());
            assertTrue(year.getMedicarePremiumCalculation().monthlyPartDPremium().signum()>0);
        }
        assertEquals(original,LifetimeProjectionTestSupport.json(plan));
    }

    @Test void bothDeathOrdersAndSinglePersonDateAgePlansUseTheirOwnFrozenRoles() {
        for (var insured : PersonReference.values()) {
            var plan = base(StateTaxJurisdiction.FL);
            var a = plan.getPlanningAssumptions();
            var scenario = insured==PersonReference.PRIMARY ? DeathScenario.PRIMARY_DIES : DeathScenario.SPOUSE_DIES;
            plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),
                    new DeathScenarioAssumptions(scenario,2031,67),a.getProjectionLengthYears(),a.getProjectionStartDate(),a.getStateTaxAssumptions()));
            var recipient = insured==PersonReference.PRIMARY ? PersonReference.SPOUSE : PersonReference.PRIMARY;
            plan.setConditionalBenefits(List.of(change(change(payment("2500",LocalDate.of(2030,1,1)),"recipient",recipient),"trigger",new Trigger(TriggerType.DEATH,null,insured,null))));
            for (var year : new ProjectionEngine().project(plan).getYears()) {
                money(year.getCalendarYear()==2030 ? "0" : "30000",year.getGuaranteedIncome());
                money(year.getCalendarYear()==2030 ? "0" : "30000",year.getAdjustedGrossIncome());
            }
        }
        for (var trigger : List.of(new Trigger(TriggerType.FIXED_DATE,LocalDate.of(2032,1,1),null,null),new Trigger(TriggerType.AGE,null,PersonReference.PRIMARY,72))) {
            var plan = plan(false); plan.getHousehold().getPrimaryPerson().getIncomeSources().stream().toList().forEach(plan.getHousehold().getPrimaryPerson()::removeIncomeSource);
            var a = plan.getPlanningAssumptions();
            plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),a.getDeathScenarioAssumptions(),2,LocalDate.of(2032,1,1),StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
            plan.setConditionalBenefits(List.of(change(payment("2500",LocalDate.of(2032,1,1)),"trigger",trigger)));
            money("30000",new ProjectionEngine().project(plan).getYearAt(0).getGuaranteedIncome());
        }
    }
    @Test void unvalidatedStateCategoriesAreExplicitlyBlockedInsteadOfSilentlyOmitted() {
        for (var state : List.of(StateTaxJurisdiction.PA,StateTaxJurisdiction.IA)) {
            var plan = base(state); plan.setConditionalBenefits(List.of(payment("2500",LocalDate.of(2030,1,1))));
            var failure = assertThrows(ConditionalBenefitAdmission.UnsupportedExecutionException.class,()->new ProjectionEngine().project(plan));
            assertTrue(failure.getMessage().contains("tax classification"));
        }
    }
    @Test void unspentLumpIsRetainedOnceWithoutReceiptYearGrowthAndFundsLaterYears() {
        var plan = base(StateTaxJurisdiction.FL);
        plan.getAccountPortfolio().getAccounts().forEach(account->account.setCurrentBalance(BigDecimal.ZERO));
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(new EconomicAssumptions(new BigDecimal(".10"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO),
                a.getTaxAssumptions(),a.getWithdrawalAssumptions(),a.getDeathScenarioAssumptions(),2,a.getProjectionStartDate(),a.getStateTaxAssumptions()));
        plan.setConditionalBenefits(List.of(change(payment("200000",LocalDate.of(2030,1,1)),"paymentPattern",PaymentPattern.ONE_TIME_LUMP_SUM)));
        var result = new ProjectionEngine().project(plan); var first = result.getYearAt(0); var next = result.getYearAt(1);
        money("0",first.getInvestmentGrowth()); money("0",first.getPortfolioWithdrawal());
        var expected = new BigDecimal("200000").subtract(first.getTotalIncomeTax()).subtract(first.getMedicarePremiumCalculation().totalAnnualMedicarePremium());
        money(expected.toPlainString(),first.getEndingInvestableAssets()); money(expected.toPlainString(),first.getEndingRetainedNonQualifiedAssets());
        money("0",next.getGuaranteedIncome()); money(expected.multiply(new BigDecimal(".10")).toPlainString(),next.getInvestmentGrowth());
        assertTrue(next.getPortfolioWithdrawal().signum()>0);
        money(next.getBeginningInvestableAssets().add(next.getInvestmentGrowth()).subtract(next.getAnnualExpenses())
                .subtract(next.getMedicarePremiumCalculation().totalAnnualMedicarePremium()).subtract(next.getTotalIncomeTax()).toPlainString(),next.getEndingInvestableAssets());
    }
}
