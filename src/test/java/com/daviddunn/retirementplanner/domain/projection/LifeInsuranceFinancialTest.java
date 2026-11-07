package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.daviddunn.retirementplanner.domain.benefit.LifeInsurancePrincipalTest.principal;
import static com.daviddunn.retirementplanner.domain.projection.ConditionalBenefitFinancialIntegrationTest.*;
import static org.junit.jupiter.api.Assertions.*;

public class LifeInsuranceFinancialTest {
    public static RetirementPlan fixture(StateTaxJurisdiction state) {
        var plan = base(state); plan.setConditionalBenefits(List.of(principal())); return plan;
    }

    @ParameterizedTest @ValueSource(strings={"MI","IL","FL","TX"})
    void literalCashTaxMedicareAndEstateOracles(String state) throws Exception {
        var plan = fixture(StateTaxJurisdiction.valueOf(state));
        var original = LifetimeProjectionTestSupport.json(plan);
        var without = base(StateTaxJurisdiction.valueOf(state));
        var baseline = new ProjectionEngine().project(without);
        var actual = new ProjectionEngine().project(plan);
        var explicit = new ProjectionEngine().project(plan,ProjectionEvaluationContext.withLifetimeScenario(
                new HouseholdLifetimeScenario(Optional.of(Year.of(2031)),Optional.empty())));
        for (int i=0;i<actual.size();i++) {
            var year = actual.getYearAt(i); var control = baseline.getYearAt(i);
            money(year.getCalendarYear()==2031 ? "400000" : "0",year.getGuaranteedIncome());
            money("0",year.getAdjustedGrossIncome()); money("0",year.getTaxableSocialSecurity());
            money("0",year.getFederalIncomeTax()); money("0",year.getStateIncomeTax());
            money("0",year.getRequiredMinimumDistribution()); money("0",year.getRothConversion());
            money(control.getAnnualPartBPremium().toPlainString(),year.getAnnualPartBPremium());
            money(control.getAnnualPartDPremium().toPlainString(),year.getAnnualPartDPremium());
            money("0",year.getModifiedAdjustedGrossIncome());
            money(year.getCalendarYear()>=2031?"400000":"0",year.getEndingInvestableAssets().subtract(control.getEndingInvestableAssets()));
            money(year.getEndingInvestableAssets().toPlainString(),year.getAfterTaxEstateValue());
            money(year.getEndingInvestableAssets().toPlainString(),explicit.getYearAt(i).getEndingInvestableAssets());
            // No growth, spending or other income in this independent literal cash oracle.
            money(year.getBeginningInvestableAssets().add(year.getGuaranteedIncome()).subtract(year.getAnnualMedicarePremium()).toPlainString(),
                    year.getEndingInvestableAssets());
            var cash=year.getHouseholdCashSettlement();
            money("0",cash.guaranteedIncome().add(cash.projectedPeriodRmdCash()).add(cash.spendingWithdrawal()).add(cash.taxFundingWithdrawal())
                    .subtract(cash.ordinaryExpenses()).subtract(cash.medicarePremiums()).subtract(cash.incomeTaxes()).subtract(cash.retainedHouseholdSurplus()));
            if (year.getCalendarYear()==2031) {
                var source=year.getIncomeSources().stream().filter(s->s.conditionalBenefit().isPresent()).findFirst().orElseThrow();
                assertTrue(source.account().isEmpty()); assertTrue(source.pension().isEmpty());
                assertFalse(source.isRetirementDistribution()); money("0",source.modeledTaxableAmount().orElseThrow());
                money("400000",source.conditionalBenefit().orElseThrow().excluded());
            }
        }
        assertEquals(original,LifetimeProjectionTestSupport.json(plan));
        money("500000",plan.getAccountPortfolio().getAccounts().getFirst().getCurrentBalance());
    }

    @ParameterizedTest @ValueSource(strings={"PA","IA"})
    void unresolvedStateMappingsRemainExplicitlyBlocked(String state) {
        assertThrows(ConditionalBenefitAdmission.UnsupportedExecutionException.class,
                ()->new ProjectionEngine().project(fixture(StateTaxJurisdiction.valueOf(state))));
    }

    @ParameterizedTest @ValueSource(strings={"MI","IL","FL"})
    void excludedCashMatchesIndependentlyFundedTaxableCashForRothRmdAndMedicare(String state) {
        var actual = fixture(StateTaxJurisdiction.valueOf(state));
        var a = actual.getPlanningAssumptions();
        actual.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),
                a.getDeathScenarioAssumptions(),3,LocalDate.of(2031,1,1),a.getStateTaxAssumptions()));
        actual.getHousehold().getSpouse().setBirthDate(LocalDate.of(1955,6,15));
        for (var entry : actual.getHousehold().peopleByOwner().entrySet()) {
            var person = entry.getValue();
            person.getIncomeSources().stream().filter(com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome.class::isInstance)
                    .toList().forEach(person::removeIncomeSource);
            person.addIncomeSource(new com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome("Zero",entry.getKey(),
                    person.getBirthDate().plusYears(67),null,BigDecimal.ZERO,67,BigDecimal.ZERO,2030));
        }
        actual.getHousehold().addExpense(new Expense("Spending",new BigDecimal("250000")));
        var ira = new TraditionalIRA("Living IRA",AccountOwnership.SPOUSE,new BigDecimal("2000000"));
        ira.setOpeningRmdAccountData(new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(2031,new BigDecimal("2000000"),BigDecimal.ZERO));
        actual.getAccountPortfolio().addAccount(ira);
        actual.getAccountPortfolio().addAccount(new RothIRA("Living Roth",AccountOwnership.SPOUSE,BigDecimal.ZERO));
        actual.setRothConversionRequest(new RothConversionRequest(true,2031,BigDecimal.ZERO,RothConversionStopRule.NEVER,
                RothConversionStrategy.FILL_22_PERCENT_BRACKET,RothConversionFrequency.ANNUAL));
        var control=new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(actual);
        control.setConditionalBenefits(List.of());
        control.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("900000"));
        var actualYears=new ProjectionEngine().project(actual); var controlYears=new ProjectionEngine().project(control);
        for (int i=0;i<actualYears.size();i++) {
            var year=actualYears.getYearAt(i); var expected=controlYears.getYearAt(i);
            money(expected.getAdjustedGrossIncome().toPlainString(),year.getAdjustedGrossIncome());
            money(expected.getTotalIncomeTax().toPlainString(),year.getTotalIncomeTax());
            money(expected.getRequiredMinimumDistribution().toPlainString(),year.getRequiredMinimumDistribution());
            money(expected.getRmdDistributedInProjection().toPlainString(),year.getRmdDistributedInProjection());
            money(expected.getRothConversion().toPlainString(),year.getRothConversion());
            money(expected.getAnnualMedicarePremium().toPlainString(),year.getAnnualMedicarePremium());
            money(expected.getModifiedAdjustedGrossIncome().toPlainString(),year.getModifiedAdjustedGrossIncome());
            money(expected.getEndingInvestableAssets().toPlainString(),year.getEndingInvestableAssets());
            money(expected.getAfterTaxEstateValue().toPlainString(),year.getAfterTaxEstateValue());
            assertTrue(year.getRmdDistributedInProjection().signum()>0,"Receipt cannot substitute for the statutory distribution");
        }
    }

    @Test void taxableOptInIsNeverInferredFromLabelAndFutureEarningsAreNotClassifiedAsExcluded() throws Exception {
        var node=LifeInsurancePrincipalTest.document(); node.remove("lifeInsuranceQualification");
        node.put("taxCharacter","FULLY_TAXABLE_ORDINARY_NON_PENSION"); node.put("label","Life Insurance");
        var plan=fixture(StateTaxJurisdiction.FL);
        plan.setConditionalBenefits(List.of(BenefitFixtures.JSON.treeToValue(node,ConditionalBenefitDefinition.class)));
        var year=new ProjectionEngine().project(plan).getYearAt(1); money("400000",year.getAdjustedGrossIncome());
        assertTrue(year.getAnnualMedicarePremium().compareTo(new ProjectionEngine().project(fixture(StateTaxJurisdiction.FL)).getYearAt(1).getAnnualMedicarePremium())>0);
        assertTrue(com.daviddunn.retirementplanner.app.projection.ConditionalBenefitDisclosures.lines(fixture(StateTaxJurisdiction.FL),"Original").stream()
                .anyMatch(s->s.contains("Future investment earnings may be taxable")));
    }

    @Test void receiptGetsNoImmediateExtraReturnAndDoesNotIncreaseTaxableSocialSecurity() {
        var actual=fixture(StateTaxJurisdiction.FL); var a=actual.getPlanningAssumptions();
        actual.setPlanningAssumptions(new PlanningAssumptions(new EconomicAssumptions(new BigDecimal(".10"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO),
                a.getTaxAssumptions(),a.getWithdrawalAssumptions(),a.getDeathScenarioAssumptions(),a.getProjectionLengthYears(),a.getProjectionStartDate(),a.getStateTaxAssumptions()));
        for(var entry:actual.getHousehold().peopleByOwner().entrySet()) {
            var person=entry.getValue(); person.getIncomeSources().stream().toList().forEach(person::removeIncomeSource);
            person.addIncomeSource(new com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome("Known SS",entry.getKey(),
                    person.getBirthDate().plusYears(67),null,new BigDecimal("2500"),67,BigDecimal.ZERO,2030));
        }
        var control=new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(actual);
        control.setConditionalBenefits(List.of());
        var years=new ProjectionEngine().project(actual); var expected=new ProjectionEngine().project(control);
        money("400000",years.getYearAt(1).getEndingInvestableAssets().subtract(expected.getYearAt(1).getEndingInvestableAssets()));
        money("440000",years.getYearAt(2).getEndingInvestableAssets().subtract(expected.getYearAt(2).getEndingInvestableAssets()));
        for(int i=0;i<years.size();i++) {
            money(expected.getYearAt(i).getTaxableSocialSecurity().toPlainString(),years.getYearAt(i).getTaxableSocialSecurity());
            money(expected.getYearAt(i).getAdjustedGrossIncome().toPlainString(),years.getYearAt(i).getAdjustedGrossIncome());
            money(expected.getYearAt(i).getModifiedAdjustedGrossIncome().toPlainString(),years.getYearAt(i).getModifiedAdjustedGrossIncome());
        }
    }

    @ParameterizedTest @ValueSource(strings={"MI","IL","FL"})
    void mixedReceiptsKeepTheOriginalTaxableIncomeComponent(String state) {
        var plan=fixture(StateTaxJurisdiction.valueOf(state));
        var monthly=BenefitFixtures.change(payment("2500",LocalDate.of(2031,1,1)),"recipient",
                ConditionalBenefitDefinition.PersonReference.SPOUSE);
        monthly=BenefitFixtures.change(monthly,"id",new UUID(0,2));
        plan.setConditionalBenefits(List.of(principal(),monthly));
        var year=new ProjectionEngine().project(plan).getYearAt(1);
        money("430000",year.getGuaranteedIncome()); money("30000",year.getAdjustedGrossIncome());
        var receipts=year.getIncomeSources().stream().flatMap(s->s.conditionalBenefit().stream()).toList();
        money("400000",receipts.stream().map(ConditionalBenefitReceipt::excluded).reduce(BigDecimal.ZERO,BigDecimal::add));
        money("30000",receipts.stream().map(ConditionalBenefitReceipt::taxable).reduce(BigDecimal.ZERO,BigDecimal::add));
    }
}
