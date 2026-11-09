package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.roth.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalculationModeFinancialTest {
    @Test void explicitAdvancedRecognizesAttributedInterestWithoutAnAnnuity() throws Exception {
        var ira=NonqualifiedAnnuityFinancialTest.fixture(true).getAccountPortfolio().getAccounts().get(1);
        var rules=new com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        for(var selected:com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode.values()) {
            var sources=java.util.List.of(com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.withdrawal(ira,new BigDecimal("294240"),
                    com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.ROTH_CONVERSION,
                    com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.CONVERSION),
                    com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.aggregate(
                            com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.INTEREST,
                            com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.INCOME,"Known personal interest",
                            new BigDecimal("5760"),java.util.Optional.of(new BigDecimal("5760"))),
                    com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.calculationPolicy(selected,2030));
            var income=new com.daviddunn.retirementplanner.domain.tax.TaxIncome(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,
                    new BigDecimal("294240"),new BigDecimal("5760"),sources);
            var tax=new com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculator().calculate(income,
                    com.daviddunn.retirementplanner.domain.rules.FilingStatus.MARRIED_FILING_JOINTLY,rules);
            assertEquals(0,new BigDecimal("300000").compareTo(tax.getAdjustedGrossIncome()));
            if(selected==com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode.ADVANCED) {
                assertEquals(0,new BigDecimal("218.88").compareTo(tax.niit().orElseThrow().tax()));
                assertEquals(0,new BigDecimal("49686.88").compareTo(tax.getFederalIncomeTax()));
            }else{assertTrue(tax.niit().isEmpty());assertEquals(0,new BigDecimal("49468").compareTo(tax.getFederalIncomeTax()));}
        }
    }
    @Test void basicContractQualificationDoesNotAssertAdvancedNiitFacts() throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true);mode(plan,"BASIC");
        var original=plan.getConditionalBenefits().getFirst();var terms=original.annuityTerms();
        var basicTerms=new com.daviddunn.retirementplanner.domain.benefit.NonqualifiedAnnuityTerms(terms.acquisitionDate(),terms.taxAnnuityStartingDate(),
                terms.excludedPerPayment(),terms.originalInvestment(),terms.openingInvestment(),terms.basisAsOf(),
                com.daviddunn.retirementplanner.domain.benefit.NonqualifiedAnnuityTerms.Qualification.PERSONAL_GENERAL_RULE_FIXED_SINGLE_LIFE_V2);
        plan.setConditionalBenefits(java.util.List.of(com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures.change(original,"annuityTerms",basicTerms)));
        assertEquals(0,new BigDecimal("5760").compareTo(new ProjectionEngine().project(plan).getYearAt(0).getAdjustedGrossIncome()));
        mode(plan,"ADVANCED");
        var unsupported=assertThrows(com.daviddunn.retirementplanner.domain.tax.NiitUnsupportedInputException.class,()->new ProjectionEngine().project(plan));
        assertTrue(unsupported.getMessage().contains("Reopen the annuity"));
        assertEquals(basicTerms,plan.getConditionalBenefits().getFirst().annuityTerms());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"BASIC,MI","ADVANCED,MI","BASIC,PA","ADVANCED,PA","BASIC,IA","ADVANCED,IA"})
    void stateAdmissionRemainsIndependentOfNiitMode(String selected,String state) throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true);mode(plan,selected);
        var a=plan.getPlanningAssumptions();plan.setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(
                a.getEconomicAssumptions(),a.getTaxAssumptions(),a.getWithdrawalAssumptions(),a.getDeathScenarioAssumptions(),3,a.getProjectionStartDate(),
                com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions.simplified(com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.valueOf(state))));
        assertThrows(UnsupportedOperationException.class,()->new ProjectionEngine().project(plan));
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void mode(RetirementPlan plan, String name) throws Exception {
        var type = Class.forName("com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode");
        plan.getClass().getMethod("setFinancialCalculationMode", type).invoke(plan, Enum.valueOf((Class) type, name));
    }

    @Test void explicitBasicOmitsNiitWithoutChangingOrdinaryTaxOrAnnuitySplit() throws Exception {
        var plan = NonqualifiedAnnuityFinancialTest.fixture(true);
        mode(plan, "BASIC");
        plan.setRothConversionRequest(new RothConversionRequest(true, 2030, new BigDecimal("294240"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME));
        var year = new ProjectionEngine().project(plan).getYearAt(0);
        assertEquals(0, new BigDecimal("300000").compareTo(year.getAdjustedGrossIncome()));
        assertEquals(0, new BigDecimal("49468").compareTo(year.getFederalIncomeTax()));
        assertTrue(year.getIncomeSources().stream().noneMatch(s -> s.niit().isPresent()));
        var receipt = year.getIncomeSources().stream().flatMap(s -> s.conditionalBenefit().stream()).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("8640").compareTo(receipt.excluded()));
        assertEquals(0, new BigDecimal("1360").compareTo(receipt.annuityBasis().closing()));
    }

    @Test void explicitAdvancedRetainsKnown21888Niit() throws Exception {
        var plan = NonqualifiedAnnuityFinancialTest.fixture(true);
        mode(plan, "ADVANCED");
        plan.setRothConversionRequest(new RothConversionRequest(true, 2030, new BigDecimal("294240"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ONE_TIME));
        var year = new ProjectionEngine().project(plan).getYearAt(0);
        assertEquals(0, new BigDecimal("49686.88").compareTo(year.getFederalIncomeTax()));
        assertEquals(0, new BigDecimal("218.88").compareTo(year.getIncomeSources().stream()
                .flatMap(s -> s.niit().stream()).findFirst().orElseThrow().tax()));
    }

    @Test void basicBasisExhaustionDoesNotDependOnNiitSupport() throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true); mode(plan,"BASIC");
        var years=new ProjectionEngine().project(plan).getYears();
        String[] taxable={"5760","13040","14400"}; String[] recovery={"8640","1360","0"};
        for(int i=0;i<3;i++) {
            var receipt=years.get(i).getIncomeSources().stream().flatMap(s->s.conditionalBenefit().stream()).findFirst().orElseThrow();
            assertEquals(0,new BigDecimal(taxable[i]).compareTo(receipt.taxable()));
            assertEquals(0,new BigDecimal(recovery[i]).compareTo(receipt.excluded()));
            assertTrue(years.get(i).getIncomeSources().stream().noneMatch(s->s.niit().isPresent()));
            assertTrue(com.daviddunn.retirementplanner.domain.tax.FinancialCalculationPolicy.disclosure(years.get(i).getIncomeSources()).contains("NIIT Not Modeled"));
        }
    }

    @Test void basicPermitsPensionBrokerageAndGrowthButAdvancedRefusesUnknownNiit() throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true); mode(plan,"BASIC");
        plan.getAccountPortfolio().addAccount(new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount("Taxable assets",
                com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY,new BigDecimal("500000")));
        plan.getHousehold().getPrimaryPerson().addIncomeSource(new com.daviddunn.retirementplanner.domain.income.Pension("Generic pension",
                com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY,java.time.LocalDate.of(2025,1,1),null,
                new BigDecimal("500"),BigDecimal.ZERO,BigDecimal.ZERO));
        var old=plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(
                new com.daviddunn.retirementplanner.domain.model.EconomicAssumptions(new BigDecimal(".05"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO),
                old.getTaxAssumptions(),old.getWithdrawalAssumptions(),old.getDeathScenarioAssumptions(),3,old.getProjectionStartDate(),old.getStateTaxAssumptions()));
        var year=new ProjectionEngine().project(plan).getYearAt(0);
        assertEquals(0,new BigDecimal("20400").compareTo(year.getGuaranteedIncome()));
        assertEquals(0,new BigDecimal("11760").compareTo(year.getAdjustedGrossIncome()));
        assertTrue(year.getInvestmentGrowth().signum()>0);
        mode(plan,"ADVANCED");
        assertThrows(com.daviddunn.retirementplanner.domain.tax.NiitUnsupportedInputException.class,()->new ProjectionEngine().project(plan));
    }

    @Test void advancedNoAnnuityComputesKnownZeroInsteadOfOmittingNiit() throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true); plan.setConditionalBenefits(java.util.List.of()); mode(plan,"ADVANCED");
        var year=new ProjectionEngine().project(plan).getYearAt(0);
        assertEquals(0,year.getIncomeSources().stream().flatMap(s->s.niit().stream()).findFirst().orElseThrow().tax().signum());
        mode(plan,"BASIC");
        assertTrue(new ProjectionEngine().project(plan).getYearAt(0).getIncomeSources().stream().noneMatch(s->s.niit().isPresent()));
    }

    @Test void basicBracketFillIncludesOrdinaryTaxFundingWithoutNiit() throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true);mode(plan,"BASIC");
        plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(BigDecimal.ZERO);
        plan.setRothConversionRequest(new RothConversionRequest(true,2030,BigDecimal.ZERO,RothConversionStopRule.NEVER,
                RothConversionStrategy.CUSTOM_TAXABLE_INCOME_TARGET,RothConversionFrequency.ONE_TIME,new BigDecimal("267800")));
        var year=new ProjectionEngine().project(plan).getYearAt(0);
        assertTrue(new BigDecimal("248532.80").subtract(year.getRothConversion()).abs().compareTo(new BigDecimal(".02"))<=0);
        assertTrue(new BigDecimal("45707.20").subtract(year.getTaxFundingWithdrawal()).abs().compareTo(new BigDecimal(".02"))<=0);
        assertTrue(new BigDecimal("300000").subtract(year.getAdjustedGrossIncome()).abs().compareTo(new BigDecimal(".02"))<=0);
    }
}
