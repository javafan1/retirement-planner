package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.NiitUnsupportedInputException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.domain.benefit.NonqualifiedAnnuityEvaluatorTest.annuity;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;

public class NonqualifiedAnnuityFinancialTest {
    public static RetirementPlan fixture(boolean couple) {
        var plan=BenefitFixtures.plan(couple);
        for (var entry:plan.getHousehold().peopleByOwner().entrySet()) {
            var p=entry.getValue();
            List.copyOf(p.getIncomeSources()).forEach(p::removeIncomeSource);
            p.addIncomeSource(new SocialSecurityIncome("Zero SS",entry.getKey(),SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(p.getBirthDate(),67),null,
                    BigDecimal.ZERO,67,BigDecimal.ZERO,2026));
        }
        List.copyOf(plan.getHousehold().getExpenses()).forEach(plan.getHousehold()::removeExpense);
        List.copyOf(plan.getAccountPortfolio().getAccounts()).forEach(plan.getAccountPortfolio()::removeAccount);
        plan.getAccountPortfolio().addAccount(new CheckingAccount("Synthetic cash",AccountOwnership.PRIMARY,new BigDecimal("500000")));
        plan.getAccountPortfolio().addAccount(new TraditionalIRA("Synthetic IRA",AccountOwnership.PRIMARY,new BigDecimal("1000000")));
        plan.getAccountPortfolio().addAccount(new RothIRA("Synthetic Roth",AccountOwnership.PRIMARY,BigDecimal.ZERO));
        var a=plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(new EconomicAssumptions(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO),
                new TaxAssumptions(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,
                    couple?FilingStatus.MARRIED_FILING_JOINTLY:FilingStatus.SINGLE,new BigDecimal(".25")),
                a.getWithdrawalAssumptions(),new DeathScenarioAssumptions(DeathScenario.BOTH_SURVIVE,null,couple?67:null),3,
                LocalDate.of(2030,1,1),StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        plan.setConditionalBenefits(List.of(annuity(PersonReference.PRIMARY,LocalDate.of(2030,1,1),LocalDate.of(2025,1,1),"1200","720","10000")));
        return plan;
    }
    static void money(String expected,BigDecimal actual) { assertEquals(0,new BigDecimal(expected).compareTo(actual),()->"expected "+expected+", actual "+actual); }
    @Test void cashAndTaxBasisExhaustionAreOneAuthoritativeFlow() {
        var plan=fixture(true); var projection=new ProjectionEngine().project(plan);
        String[] taxable={"5760","13040","14400"};
        for(int i=0;i<3;i++) {
            var y=projection.getYearAt(i); money("14400",y.getGuaranteedIncome()); money(taxable[i],y.getAdjustedGrossIncome());
            money("0",y.getRequiredMinimumDistribution());
            var receipt=y.getIncomeSources().stream().flatMap(s->s.conditionalBenefit().stream()).findFirst().orElseThrow();
            money(taxable[i],receipt.taxable());
            assertEquals(1,y.getIncomeSources().stream().filter(s->s.niit().isPresent()).count());
            var cash=y.getHouseholdCashSettlement();
            money("0",cash.guaranteedIncome().add(cash.projectedPeriodRmdCash()).add(cash.spendingWithdrawal())
                    .add(cash.taxFundingWithdrawal()).subtract(cash.ordinaryExpenses()).subtract(cash.medicarePremiums())
                    .subtract(cash.incomeTaxes()).subtract(cash.retainedHouseholdSurplus()));
        }
        var y=projection.getYearAt(0);
        money("1500000",y.getBeginningInvestableAssets());
        money("0",y.getInvestmentGrowth()); money("0",y.getFederalIncomeTax());
        money("1514400",y.getBeginningInvestableAssets().add(y.getGuaranteedIncome()));
        money("1509530.40",y.getEndingInvestableAssets());
        money("1500000",plan.getAccountPortfolio().getAccounts().stream().map(Account::getCurrentBalance).reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    @Test void fixedRothControlIncludes21888NiitButOnlyTaxablePaymentEntersMagi() {
        var plan=fixture(true);
        plan.setRothConversionRequest(new RothConversionRequest(true,2030,new BigDecimal("294240"),RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT,RothConversionFrequency.ONE_TIME));
        var y=new ProjectionEngine().project(plan).getYearAt(0);
        money("300000",y.getAdjustedGrossIncome()); money("267800",y.getFederalTaxableIncome());
        money("49686.88",y.getFederalIncomeTax());
        money("218.88",y.getIncomeSources().stream().flatMap(s->s.niit().stream()).findFirst().orElseThrow().tax());
        money("300000",y.getModifiedAdjustedGrossIncome()); money("10639.20",y.getAnnualMedicarePremium());
        money("1454073.92",y.getEndingInvestableAssets());
        money("0",y.getRequiredMinimumDistribution()); money("0",y.getTaxableSocialSecurity());
    }
    @Test void basisExhaustionChangesNiitInEachHighIncomeConversionYear() {
        var plan=fixture(true);
        plan.setRothConversionRequest(new RothConversionRequest(true,2030,new BigDecimal("294240"),RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT,RothConversionFrequency.ANNUAL));
        var result=new ProjectionEngine().project(plan);
        String[] magi={"300000","307280","308640"};String[] tax={"218.88","495.52","547.20"};
        for(int i=0;i<3;i++) {
            money(magi[i],result.getYearAt(i).getAdjustedGrossIncome());
            money(tax[i],result.getYearAt(i).getIncomeSources().stream().flatMap(s->s.niit().stream()).findFirst().orElseThrow().tax());
        }
    }
    @Test void taxFundingAndBracketFillJointlySolveTheLiteralFixedPoint() {
        var plan=fixture(true); plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(BigDecimal.ZERO);
        plan.setRothConversionRequest(new RothConversionRequest(true,2030,BigDecimal.ZERO,RothConversionStopRule.NEVER,
                RothConversionStrategy.CUSTOM_TAXABLE_INCOME_TARGET,RothConversionFrequency.ONE_TIME,new BigDecimal("267800")));
        var y=new ProjectionEngine().project(plan).getYearAt(0);
        assertTrue(y.getAdjustedGrossIncome().subtract(new BigDecimal("300000")).abs().compareTo(new BigDecimal(".02"))<=0);
        assertTrue(y.getRothConversion().subtract(new BigDecimal("248313.92")).abs().compareTo(new BigDecimal(".02"))<=0);
        assertTrue(y.getTaxFundingWithdrawal().subtract(new BigDecimal("45926.08")).abs().compareTo(new BigDecimal(".02"))<=0);
    }
    @ParameterizedTest @ValueSource(strings={"MI","PA","IA"})
    void unsupportedStateCannotBorrowPensionTaxTreatment(String state) {
        var plan=fixture(true); var a=plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),
                a.getDeathScenarioAssumptions(),3,a.getProjectionStartDate(),StateTaxAssumptions.simplified(StateTaxJurisdiction.valueOf(state))));
        assertThrows(NiitUnsupportedInputException.class,()->new ProjectionEngine().project(plan));
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"IL,2153.75","FL,0","TX,0"})
    void commercialAnnuityIsNotPensionExemptAndFederalNiitIsStateIndependent(String state,String expectedStateTax) {
        var plan=fixture(true);var a=plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),
                a.getDeathScenarioAssumptions(),3,a.getProjectionStartDate(),StateTaxAssumptions.simplified(StateTaxJurisdiction.valueOf(state))));
        plan.setConditionalBenefits(List.of(BenefitFixtures.change(plan.getConditionalBenefits().getFirst(),"grossAmount",new BigDecimal("5000"))));
        plan.setRothConversionRequest(new RothConversionRequest(true,2030,new BigDecimal("294240"),RothConversionStopRule.NEVER,
                RothConversionStrategy.FIXED_AMOUNT,RothConversionFrequency.ONE_TIME));
        var year=new ProjectionEngine().project(plan).getYearAt(0);
        money("345600",year.getAdjustedGrossIncome());money("62363.68",year.getFederalIncomeTax());
        money("1951.68",year.getIncomeSources().stream().flatMap(s->s.niit().stream()).findFirst().orElseThrow().tax());
        money(expectedStateTax,year.getStateIncomeTax());
        assertEquals(StateTaxJurisdiction.valueOf(state),year.stateTaxCalculation().orElseThrow().jurisdiction());
    }
    @Test void unknownPensionAndBrokerageFactsAreNotSilentlyExempted() {
        var plan=fixture(true);
        plan.getAccountPortfolio().addAccount(new BrokerageAccount("Unknown gains",AccountOwnership.PRIMARY,new BigDecimal("100")));
        assertThrows(NiitUnsupportedInputException.class,()->new ProjectionEngine().project(plan));
    }
    @Test void unclassifiedCashOrRetainedGrowthIsUnsupportedRatherThanZeroInvestmentIncome() {
        var cash=fixture(true);var a=cash.getPlanningAssumptions();
        cash.setPlanningAssumptions(new PlanningAssumptions(new EconomicAssumptions(new BigDecimal(".10"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO),
                a.getTaxAssumptions(),a.getWithdrawalAssumptions(),a.getDeathScenarioAssumptions(),3,a.getProjectionStartDate(),a.getStateTaxAssumptions()));
        assertThrows(NiitUnsupportedInputException.class,()->new ProjectionEngine().projectWithOutcome(cash));
        cash.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(BigDecimal.ZERO);
        // Year one has qualified account growth only; its excess annuity cash creates retained assets.
        // The following nonzero-growth year must refuse their unknown income character.
        assertThrows(NiitUnsupportedInputException.class,()->new ProjectionEngine().projectWithOutcome(cash));
        cash.setConditionalBenefits(List.of());
        assertDoesNotThrow(()->new ProjectionEngine().projectWithOutcome(cash));
    }
    @Test void statutoryRmdIsIndependentOfNonqualifiedPaymentAndBasis() {
        var plan=fixture(true);plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1950,1,1));
        var ira=plan.getAccountPortfolio().getAccounts().get(1);
        ira.setOpeningRmdAccountData(new com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData(2030,new BigDecimal("1000000"),BigDecimal.ZERO));
        var year=new ProjectionEngine().project(plan).getYearAt(0);
        assertTrue(year.getRequiredMinimumDistribution().subtract(new BigDecimal("49504.95")).abs().compareTo(new BigDecimal(".01"))<0);
        money(year.getRequiredMinimumDistribution().toPlainString(),year.getRmdDistributedInProjection());
        assertTrue(year.getAdjustedGrossIncome().subtract(year.getRequiredMinimumDistribution()).subtract(new BigDecimal("5760")).abs().compareTo(new BigDecimal(".01"))<0);
    }
    @Test void independentTaxableSocialSecurityAndTaxFundingControl() {
        var plan=fixture(false);plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(BigDecimal.ZERO);
        var primary=plan.getHousehold().getPrimaryPerson();primary.setBirthDate(LocalDate.of(1960,2,1));List.copyOf(primary.getIncomeSources()).forEach(primary::removeIncomeSource);
        primary.addIncomeSource(new SocialSecurityIncome("Quoted primary SS",AccountOwnership.PRIMARY,SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(primary.getBirthDate(),67),null,
                new BigDecimal("2000"),67,BigDecimal.ZERO,2026));
        plan.getHousehold().addExpense(new Expense("Controlled spending",new BigDecimal("54130.48")));
        var y=new ProjectionEngine().project(plan).getYearAt(0);
        assertTrue(y.getAdjustedGrossIncome().subtract(new BigDecimal("33456")).abs().compareTo(new BigDecimal(".03"))<0,()->"Actual AGI "+y.getAdjustedGrossIncome()+"; SS "+y.getSocialSecurityResult().householdBenefit()+"; tax "+y.getFederalIncomeTax());
        assertTrue(y.getTaxableSocialSecurity().subtract(new BigDecimal("7696")).abs().compareTo(new BigDecimal(".02"))<0);
        assertTrue(y.getFederalIncomeTax().subtract(new BigDecimal("1834.72")).abs().compareTo(new BigDecimal(".02"))<0);
        money("2434.80",y.getAnnualMedicarePremium());
        money("0",y.getIncomeSources().stream().flatMap(s->s.niit().stream()).findFirst().orElseThrow().tax());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"PRIMARY,PRIMARY_DIES","PRIMARY,SPOUSE_DIES","SPOUSE,PRIMARY_DIES","SPOUSE,SPOUSE_DIES"})
    void bothAnnuitantsAndDeathOrdersUseIdenticalConfiguredAndLifetimeIncome(PersonReference owner,DeathScenario death) {
        var plan=fixture(true);plan.setConditionalBenefits(List.of(BenefitFixtures.change(plan.getConditionalBenefits().getFirst(),"recipient",owner)));
        var a=plan.getPlanningAssumptions();plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(death,2031,67),3,a.getProjectionStartDate(),a.getStateTaxAssumptions()));
        var timing=new HouseholdLifetimeScenario(death==DeathScenario.PRIMARY_DIES?Optional.of(Year.of(2031)):Optional.empty(),
                death==DeathScenario.SPOUSE_DIES?Optional.of(Year.of(2031)):Optional.empty());
        var ordinary=new ProjectionEngine().project(plan);var explicit=new ProjectionEngine().project(plan,ProjectionEvaluationContext.withLifetimeScenario(timing));
        boolean dies=owner==PersonReference.PRIMARY?death==DeathScenario.PRIMARY_DIES:death==DeathScenario.SPOUSE_DIES;
        for(int i=0;i<3;i++) {
            var year=ordinary.getYearAt(i);money(i==0||!dies?"14400":"0",year.getGuaranteedIncome());
            money(year.getGuaranteedIncome().toPlainString(),explicit.getYearAt(i).getGuaranteedIncome());
            money(year.getAdjustedGrossIncome().toPlainString(),explicit.getYearAt(i).getAdjustedGrossIncome());
        }
    }
}
