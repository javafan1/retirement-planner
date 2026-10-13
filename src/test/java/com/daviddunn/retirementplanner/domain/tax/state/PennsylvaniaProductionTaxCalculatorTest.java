package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Independent PA 2026 worksheets: ordinary taxable income x .0307; no deduction/exemption. */
class PennsylvaniaProductionTaxCalculatorTest {
    static final BigDecimal ZERO = BigDecimal.ZERO;
    static ProductionStateTaxResult calculate(boolean couple, int age, TaxIncome income) throws Exception {
        var il = IllinoisProductionTaxCalculatorTest.request(couple, age, income);
        return new ProductionStateTaxService().calculate(new StateTaxCalculationRequest(il.taxYear(), il.filingStatus(),
                il.household(), income, il.federal(), il.projectedRules(), StateTaxAssumptions.simplified(StateTaxJurisdiction.PA)));
    }

    @ParameterizedTest
    @CsvSource({"false,0,0,0.00", "false,10000,0,307.00", "false,20000,80000,614.00",
            "true,20000,200000,614.00", "false,1,0,0.03", "false,1000000,0,30700.00",
            "true,0,200000,0.00", "false,0,100000,0.00"})
    void independentSingleAndCoupleWorksheets(boolean couple, String investment, String ira, String expected) throws Exception {
        var amount = new BigDecimal(ira);
        var source = IncomeSourceAttribution.withdrawal(new TraditionalIRA("IRA", AccountOwnership.PRIMARY, amount),
                amount, IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW);
        var result = calculate(couple, 65, new TaxIncome(ZERO, ZERO, amount, ZERO, new BigDecimal(investment), List.of(source)));
        assertEquals(new BigDecimal(expected), result.incomeTax());
        assertEquals(StateTaxCalculationStatus.CALCULATED, result.calculationStatus());
        assertTrue(result.unavailableReasons().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = IncomeSourceAttribution.Kind.class, names = {"INTEREST", "DIVIDEND", "CAPITAL_GAIN"})
    void ordinaryInvestmentsAreTaxableEvenWithRetirementIncome(IncomeSourceAttribution.Kind kind) throws Exception {
        var amount = new BigDecimal("20000");
        var source = IncomeSourceAttribution.aggregate(kind, IncomeSourceAttribution.Purpose.INCOME, "Ordinary investment",
                amount, Optional.of(amount));
        var retirement = new BigDecimal("80000");
        var ira = IncomeSourceAttribution.withdrawal(new TraditionalIRA("IRA", AccountOwnership.PRIMARY, retirement), retirement,
                IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW);
        assertEquals(new BigDecimal("614.00"), calculate(false, 65,
                new TaxIncome(ZERO, ZERO, retirement, ZERO, amount, List.of(source, ira))).incomeTax());
    }

    @ParameterizedTest
    @CsvSource({"0,0.00", "10000,307.00"})
    void socialSecurityIsExcludedIndependentlyOfFederalInclusion(String interest, String expected) throws Exception {
        assertEquals(new BigDecimal(expected), calculate(false, 65,
                new TaxIncome(ZERO, new BigDecimal("40000"), ZERO, ZERO, new BigDecimal(interest))).incomeTax());
    }

    @ParameterizedTest
    @EnumSource(value = IncomeSourceAttribution.Kind.class, names = {"ORDINARY_RETIREMENT_WITHDRAWAL", "RMD", "OPENING_RMD"})
    void iraReceiptAndRmdAreExcludedAtNormalAge(IncomeSourceAttribution.Kind kind) throws Exception {
        var amount = new BigDecimal("100000");
        var source = IncomeSourceAttribution.withdrawal(new TraditionalIRA("IRA", AccountOwnership.PRIMARY, amount),
                amount, kind, IncomeSourceAttribution.Purpose.CASH_FLOW);
        assertEquals(new BigDecimal("0.00"), calculate(false, 65, new TaxIncome(ZERO, ZERO, amount, ZERO, ZERO, List.of(source))).incomeTax());
    }

    @ParameterizedTest
    @CsvSource({"25000", "100000", "250000"})
    void completeGeneratedConversionBoundaryIsNotAnAgeOrDollarCap(String value) throws Exception {
        var amount = new BigDecimal(value);
        var source = new IncomeSourceAttribution(IncomeSourceAttribution.Kind.ROTH_CONVERSION,
                IncomeSourceAttribution.Purpose.CONVERSION, Optional.of(AccountOwnership.PRIMARY),
                Optional.of(IncomeSourceAttribution.AccountReference.capture(new TraditionalIRA("IRA", AccountOwnership.PRIMARY, amount))),
                Optional.of(IncomeSourceAttribution.AccountReference.capture(new RothIRA("Roth", AccountOwnership.PRIMARY, ZERO))),
                "Executed direct full transfer", amount, Optional.of(amount), OptionalInt.of(2026), Optional.empty());
        assertEquals(new BigDecimal("0.00"), calculate(false, 40,
                new TaxIncome(ZERO, ZERO, ZERO, amount, ZERO, List.of(source))).incomeTax());
    }

    @ParameterizedTest
    @EnumSource(value = IncomeSourceAttribution.Kind.class, names = {"ORDINARY_RETIREMENT_WITHDRAWAL", "RMD", "ROTH_CONVERSION"})
    void employerPlanCannotBeQualifiedByAccountTypeOrParticipantAssertions(IncomeSourceAttribution.Kind kind) throws Exception {
        var amount = new BigDecimal("40000");
        var account = new Traditional401K("Employer", AccountOwnership.PRIMARY, amount);
        account.setRetirementParticipantFacts(new RetirementParticipantFacts(RetirementParticipantFacts.SeparationStatus.SEPARATED,
                LocalDate.of(2020, 1, 1), RetirementParticipantFacts.RetirementConditionsStatus.MET, LocalDate.of(2020, 1, 1)));
        var source = IncomeSourceAttribution.withdrawal(account, amount, kind, IncomeSourceAttribution.Purpose.CASH_FLOW);
        boolean conversion = kind == IncomeSourceAttribution.Kind.ROTH_CONVERSION;
        unsupported(calculate(false, 75, new TaxIncome(ZERO, ZERO, conversion ? ZERO : amount,
                conversion ? amount : ZERO, ZERO, List.of(source))), StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION);
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void pensionAndSurvivorPensionRequireUnavailableOriginalPlanQualification(boolean survivor) throws Exception {
        var amount = new BigDecimal("40000");
        var pension = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.PENSION,
                IncomeSourceAttribution.Purpose.INCOME, survivor ? "Survivor pension" : "Pension", amount, Optional.of(amount));
        unsupported(calculate(true, 75, new TaxIncome(amount, ZERO, ZERO, ZERO, ZERO, List.of(pension))),
                StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION);
    }

    @Test
    void earlyIraAndEarlyRothDoNotAssumeZeroBasisOrFederalZeroMeansExempt() throws Exception {
        var amount = new BigDecimal("10000");
        var ira = IncomeSourceAttribution.withdrawal(new TraditionalIRA("IRA", AccountOwnership.PRIMARY, amount), amount,
                IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW);
        unsupported(calculate(false, 59, new TaxIncome(ZERO, ZERO, amount, ZERO, ZERO, List.of(ira))),
                StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS);
        var roth = IncomeSourceAttribution.withdrawal(new RothIRA("Roth", AccountOwnership.PRIMARY, amount), amount,
                IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW);
        unsupported(calculate(false, 59, new TaxIncome(ZERO, ZERO, ZERO, ZERO, ZERO, List.of(roth))),
                StateTaxUnavailableReason.NONQUALIFIED_ROTH_BASIS_REQUIRED);
        assertEquals(new BigDecimal("0.00"), calculate(false, 65, new TaxIncome(ZERO, ZERO, ZERO, ZERO, ZERO, List.of(roth))).incomeTax());
    }

    @Test
    void externalConversionAndUnknownBrokerageBasisNeverReturnCalculatedZero() throws Exception {
        var amount = new BigDecimal("10000");
        var source = IncomeSourceAttribution.withdrawal(new TraditionalIRA("IRA", AccountOwnership.PRIMARY, amount), amount,
                IncomeSourceAttribution.Kind.ROTH_CONVERSION, IncomeSourceAttribution.Purpose.CONVERSION);
        unsupported(calculate(false, 65, new TaxIncome(ZERO, ZERO, ZERO, amount, ZERO, List.of(source))),
                StateTaxUnavailableReason.UNSUPPORTED_TRANSACTION_TYPE);
        var broker = IncomeSourceAttribution.withdrawal(new BrokerageAccount("Broker", AccountOwnership.PRIMARY, amount), amount,
                IncomeSourceAttribution.Kind.ORDINARY_WITHDRAWAL, IncomeSourceAttribution.Purpose.TAX_FUNDING);
        unsupported(calculate(false, 65, new TaxIncome(ZERO, ZERO, ZERO, ZERO, ZERO, List.of(broker))),
                StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS);
    }

    static void unsupported(ProductionStateTaxResult result, StateTaxUnavailableReason reason) {
        assertEquals(StateTaxCalculationStatus.UNSUPPORTED, result.calculationStatus());
        assertEquals(StateTaxJurisdiction.PA, result.jurisdiction());
        assertTrue(result.estimatedIncomeTax().isEmpty());
        assertTrue(result.unavailableReasons().contains(reason), result.toString());
        assertThrows(IllegalStateException.class, result::incomeTax);
    }

    @ParameterizedTest
    @CsvSource({"1966-07-02,false", "1966-07-01,true", "1966-06-30,true"})
    void exactReceiptAgeBoundaryDoesNotUseYearEndAge(String dob, boolean eligible) throws Exception {
        var amount = new BigDecimal("10000");
        var source = IncomeSourceAttribution.withdrawal(new TraditionalIRA("IRA", AccountOwnership.PRIMARY, amount), amount,
                IncomeSourceAttribution.Kind.RMD, IncomeSourceAttribution.Purpose.CASH_FLOW);
        var income = new TaxIncome(ZERO, ZERO, amount, ZERO, ZERO, List.of(source));
        var template = IllinoisProductionTaxCalculatorTest.request(false, 60, income);
        var household = new TaxHouseholdContext(template.household().distributionDate(),
                java.util.Map.of(AccountOwnership.PRIMARY, LocalDate.parse(dob)), template.household().householdOwners(),
                template.household().activeOwners(), Optional.empty());
        var result = new ProductionStateTaxService().calculate(new StateTaxCalculationRequest(2026, template.filingStatus(),
                household, income, template.federal(), template.projectedRules(), StateTaxAssumptions.simplified(StateTaxJurisdiction.PA)));
        if (eligible) assertEquals(new BigDecimal("0.00"), result.incomeTax());
        else unsupported(result, StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS);
    }

    @ParameterizedTest
    @CsvSource({"65,65,true", "65,59,false", "59,65,false", "59,59,false"})
    void eachSpousesReceiptRequiresThatOwnersOwnAge(int primaryAge, int spouseAge, boolean eligible) throws Exception {
        var amount = new BigDecimal("10000");
        var sources = List.of(IncomeSourceAttribution.withdrawal(new TraditionalIRA("Primary", AccountOwnership.PRIMARY, amount), amount,
                        IncomeSourceAttribution.Kind.RMD, IncomeSourceAttribution.Purpose.CASH_FLOW),
                IncomeSourceAttribution.withdrawal(new TraditionalIRA("Spouse", AccountOwnership.SPOUSE, amount), amount,
                        IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW));
        var income = new TaxIncome(ZERO, ZERO, new BigDecimal("20000"), ZERO, ZERO, sources);
        var template = IllinoisProductionTaxCalculatorTest.request(true, primaryAge, income);
        var household = new TaxHouseholdContext(template.household().distributionDate(), java.util.Map.of(
                AccountOwnership.PRIMARY, LocalDate.of(2026 - primaryAge, 1, 1),
                AccountOwnership.SPOUSE, LocalDate.of(2026 - spouseAge, 1, 1)), template.household().householdOwners(),
                template.household().activeOwners(), Optional.empty());
        var result = new ProductionStateTaxService().calculate(new StateTaxCalculationRequest(2026, template.filingStatus(), household,
                income, template.federal(), template.projectedRules(), StateTaxAssumptions.simplified(StateTaxJurisdiction.PA)));
        if (eligible) assertEquals(new BigDecimal("0.00"), result.incomeTax());
        else unsupported(result, StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS);
    }

    @Test
    void inheritedHistoryAndUnattributedRetirementAmountsRemainUnavailable() throws Exception {
        var amount = new BigDecimal("10000");
        var inherited = new InheritedTraditionalIRA("Inherited", AccountOwnership.PRIMARY, amount,
                new InheritedAccountInformation(LocalDate.of(1940, 1, 1), LocalDate.of(2020, 1, 1), BeneficiaryRelationship.SPOUSE));
        var source = IncomeSourceAttribution.withdrawal(inherited, amount, IncomeSourceAttribution.Kind.RMD,
                IncomeSourceAttribution.Purpose.CASH_FLOW);
        unsupported(calculate(false, 65, new TaxIncome(ZERO, ZERO, amount, ZERO, ZERO, List.of(source))),
                StateTaxUnavailableReason.INHERITED_DISTRIBUTION_HISTORY_REQUIRED);
        unsupported(calculate(false, 65, new TaxIncome(ZERO, ZERO, amount, ZERO, ZERO)),
                StateTaxUnavailableReason.UNSUPPORTED_TRANSACTION_TYPE);
    }

    @Test
    void normalIraExemptionDoesNotRequireInferringStateBasisFromFederalTaxableAmount() throws Exception {
        var account = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("100000"));
        var source = new IncomeSourceAttribution(IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                IncomeSourceAttribution.Purpose.CASH_FLOW, Optional.of(AccountOwnership.PRIMARY),
                Optional.of(IncomeSourceAttribution.AccountReference.capture(account)), Optional.empty(), "Normal IRA",
                new BigDecimal("100000"), Optional.of(new BigDecimal("60000")), OptionalInt.of(2026), Optional.empty());
        var income = new TaxIncome(ZERO, ZERO, new BigDecimal("60000"), ZERO, new BigDecimal("10000"), List.of(source));
        assertEquals(new BigDecimal("307.00"), calculate(false, 65, income).incomeTax());
        unsupported(calculate(false, 40, income), StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS);
    }
}
