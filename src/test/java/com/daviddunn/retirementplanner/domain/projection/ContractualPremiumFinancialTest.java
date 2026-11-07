package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.domain.financial.ContractualPremiumDomainTest.premium;

class ContractualPremiumFinancialTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"MI","IL","FL","PA","IA"})
    void premiumFundingMatchesIndependentOneTimeSpendingOracleWithRothRmdAndMedicare(String jurisdiction) throws Exception {
        var plan=LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.valueOf(jurisdiction)); plan.setConditionalBenefits(java.util.List.of());
        plan.getHousehold().getSpouse().setBirthDate(LocalDate.of(1955,6,15));
        for (var entry : plan.getHousehold().peopleByOwner().entrySet()) {
            var person=entry.getValue();
            person.getIncomeSources().stream().filter(com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome.class::isInstance)
                    .toList().forEach(person::removeIncomeSource);
            person.addIncomeSource(new com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome("Zero",entry.getKey(),person.getBirthDate().plusYears(67),null,
                    BigDecimal.ZERO,67,BigDecimal.ZERO,2030));
        }
        var a=plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES,2031,67,new BigDecimal(".75")),3,LocalDate.of(2031,1,1),a.getStateTaxAssumptions()));
        plan.getHousehold().addExpense(new Expense("Lifestyle",new BigDecimal("250000"),GrowthCategory.NONE));
        plan.getAccountPortfolio().getAccounts().forEach(account->account.setCurrentBalance(BigDecimal.ZERO));
        var ira=new TraditionalIRA("Living IRA",AccountOwnership.SPOUSE,new BigDecimal("2000000"));
        ira.setOpeningRmdAccountData(new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(2031,new BigDecimal("2000000"),BigDecimal.ZERO));
        plan.getAccountPortfolio().addAccount(ira);
        plan.getAccountPortfolio().addAccount(new RothIRA("Living Roth",AccountOwnership.SPOUSE,BigDecimal.ZERO));
        plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(true,2031,BigDecimal.ZERO,
                com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.NEVER,com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ANNUAL));
        var premium=com.daviddunn.retirementplanner.domain.financial.ContractualPremiumDomainTest.premium("2000",ContractualPremiumPolicy.Insured.SPOUSE,LocalDate.of(2031,1,1),LocalDate.of(2033,12,31));
        plan.getHousehold().addExpense(premium);
        var actual=new ProjectionEngine().project(plan);
        var oracle=new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(plan);
        oracle.getHousehold().getExpenses().stream().filter(Expense::isContractualInsurancePremium).toList().forEach(oracle.getHousehold()::removeExpense);
        for(int year=2031;year<=2033;year++) oracle.getHousehold().addExpense(new Expense("Independent scheduled spending",new BigDecimal("2000"),
                GrowthCategory.GENERAL,LocalDate.of(year,1,1),LocalDate.of(year,1,1),ExpenseType.ONE_TIME));
        var expected=new ProjectionEngine().project(oracle);
        for(int i=0;i<actual.size();i++) {
            var year=actual.getYearAt(i);
            assertTrue(year.getRequiredMinimumDistribution().signum()>0);
            assertTrue(year.getTaxFundingWithdrawal().signum()>0);
            for(var method:ProjectionYear.class.getMethods()) if(method.getParameterCount()==0 && method.getReturnType()==BigDecimal.class)
                assertEquals(method.invoke(expected.getYearAt(i)),method.invoke(year),method.getName()+" "+jurisdiction);
            assertEquals(expected.getYearAt(i).getMedicarePremiumCalculation().modifiedAdjustedGrossIncome(),year.getMedicarePremiumCalculation().modifiedAdjustedGrossIncome());
            assertEquals(expected.getYearAt(i).getMedicarePremiumCalculation().totalAnnualMedicarePremium(),year.getMedicarePremiumCalculation().totalAnnualMedicarePremium());
        }
        assertEquals(new BigDecimal("2000000"),ira.getCurrentBalance());
    }
    @Test void premiumStopsInInsuredDeathYear() {
        var plan = LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.FL);
        plan.setConditionalBenefits(java.util.List.of());
        plan.getHousehold().addExpense(premium());
        var projection = new ProjectionEngine().project(plan);
        assertEquals(0, projection.getYearAt(0).getAnnualExpenses().compareTo(new BigDecimal("2000")));
        assertEquals(0, projection.getYearAt(1).getAnnualExpenses().compareTo(BigDecimal.ZERO));
    }

    @Test void independentDeathPrincipalAndCashConservation() {
        var plan = LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.FL);
        plan.getHousehold().addExpense(premium());
        var projection = new ProjectionEngine().project(plan);
        for (var year : projection.getYears()) {
            var cash = year.getHouseholdCashSettlement();
            assertEquals(0, cash.guaranteedIncome().add(cash.projectedPeriodRmdCash()).add(cash.spendingWithdrawal())
                    .add(cash.taxFundingWithdrawal()).subtract(cash.ordinaryExpenses()).subtract(cash.medicarePremiums())
                    .subtract(cash.incomeTaxes()).subtract(cash.retainedHouseholdSurplus()).compareTo(BigDecimal.ZERO));
            assertEquals(0,year.getAdjustedGrossIncome().compareTo(BigDecimal.ZERO));
            assertEquals(0,year.getEndingInvestableAssets().compareTo(year.getBeginningInvestableAssets()
                    .add(year.getGuaranteedIncome()).subtract(year.getAnnualExpenses()).subtract(year.getAnnualMedicarePremium())));
        }
        var death = projection.getYearAt(1);
        assertEquals(0,death.getAnnualExpenses().compareTo(BigDecimal.ZERO));
        var receipt=death.getIncomeSources().stream().filter(s->s.conditionalBenefit().isPresent()).findFirst().orElseThrow().conditionalBenefit().orElseThrow();
        assertEquals(0,receipt.gross().compareTo(new BigDecimal("400000")));
        assertEquals(0,receipt.excluded().compareTo(new BigDecimal("400000")));
        assertEquals(0,receipt.taxable().compareTo(BigDecimal.ZERO));
        assertEquals(0,plan.getAccountPortfolio().getAccounts().getFirst().getCurrentBalance().compareTo(new BigDecimal("500000")));
    }

    @Test void explicitLifetimeAndOrdinaryConfiguredDeathsSelectSamePremium() {
        var plan=LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.FL);
        plan.getHousehold().addExpense(premium());
        var engine=new ProjectionEngine();
        var ordinary=engine.project(plan);
        var lifetime=engine.project(plan,ProjectionEvaluationContext.withLifetimeScenario(new HouseholdLifetimeScenario(
                java.util.Optional.of(java.time.Year.of(2031)),java.util.Optional.empty())));
        for (int i=0;i<ordinary.size();i++) {
            assertEquals(ordinary.getYearAt(i).getAnnualExpenses(),lifetime.getYearAt(i).getAnnualExpenses());
            assertEquals(ordinary.getYearAt(i).getEndingInvestableAssets(),lifetime.getYearAt(i).getEndingInvestableAssets());
        }
    }

    @Test void ordinaryExpenseStillFollowsSurvivorFactor() {
        for (String factor : new String[]{"0", ".50", ".75", "1"}) {
            var plan = LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.FL);
            plan.setConditionalBenefits(java.util.List.of());
            plan.getHousehold().addExpense(premium());
            plan.getHousehold().addExpense(new Expense("Lifestyle",new BigDecimal("10000"),GrowthCategory.NONE));
            var a=plan.getPlanningAssumptions();
            plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),
                    new DeathScenarioAssumptions(DeathScenario.SPOUSE_DIES,2031,67,new BigDecimal(factor)),a.getProjectionLengthYears(),a.getProjectionStartDate(),a.getStateTaxAssumptions()));
            var y=new ProjectionEngine().project(plan).getYearAt(1);
            assertEquals(0,y.getAnnualExpenses().compareTo(new BigDecimal("2000").add(new BigDecimal("10000").multiply(new BigDecimal(factor)))));
        }
    }

    @Test void noDeathFinancialFactsMatchOrdinaryNominalExpenseIncludingTaxesRothRmdAndMedicare() throws Exception {
        var plan = LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.MI);
        plan.setConditionalBenefits(java.util.List.of());
        var a=plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE,null),a.getProjectionLengthYears(),a.getProjectionStartDate(),a.getStateTaxAssumptions()));
        var p=premium(); plan.getHousehold().addExpense(p);
        var actual=new ProjectionEngine().project(plan);
        plan.getHousehold().replaceExpense(p,new Expense("Control",new BigDecimal("2000"),GrowthCategory.NONE));
        var expected=new ProjectionEngine().project(plan);
        for (int i=0;i<actual.size();i++) for (var method : ProjectionYear.class.getMethods()) {
            if (method.getParameterCount()==0 && method.getReturnType()==BigDecimal.class)
                assertEquals(method.invoke(expected.getYearAt(i)),method.invoke(actual.getYearAt(i)),method.getName());
        }
    }
    @Test void insuredAlivePremiumSurvivesOtherDeathWithoutLifestyleReduction() {
        for (String factor : new String[]{"0", ".50", ".75", "1"}) {
            var plan = LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.FL);
            plan.setConditionalBenefits(java.util.List.of());
            var household = plan.getHousehold();
            for (var expense : java.util.List.copyOf(household.getExpenses())) household.removeExpense(expense);
            household.addExpense(premium());
            var deaths = plan.getPlanningAssumptions().getDeathScenarioAssumptions();
            var a = plan.getPlanningAssumptions();
            plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                    a.getWithdrawalAssumptions(), new DeathScenarioAssumptions(DeathScenario.SPOUSE_DIES, 2031,
                    deaths.getSurvivorClaimingAge(), new BigDecimal(factor)), a.getProjectionLengthYears(),
                    a.getProjectionStartDate(), a.getStateTaxAssumptions()));
            var projection = new ProjectionEngine().project(plan);
            assertTrue(projection.getYears().stream().allMatch(y -> y.getAnnualExpenses().compareTo(new BigDecimal("2000")) == 0));
        }
    }

    static Expense premium() {
        return new Expense("Term premium", new BigDecimal("2000"), GrowthCategory.NONE,
                LocalDate.of(2030, 1, 1), LocalDate.of(2040, 12, 31),
                ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM, null,
                new ContractualPremiumPolicy(ContractualPremiumPolicy.Model.INSURED_ALIVE_FIXED_NOMINAL_MONTHS_V1,
                        ContractualPremiumPolicy.Insured.PRIMARY));
    }
}
