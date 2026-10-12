package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.OptionalInt;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class IllinoisProductionTaxCalculatorTest {
    // Independently worked: max(0, investment - exemptions) * .0495.
    // IDOR Pub 120 and 2026 exemption guidance; no calculator-generated expectations.
    @ParameterizedTest
    @CsvSource({
            "false,60,10000,0,350.21", "false,65,10000,0,300.71",
            "true,60,20000,0,700.43", "true,65,20000,0,601.43",
            "false,40,0,100000,0", "false,65,10000,100000,300.71",
            "true,65,20000,200000,601.43", "false,65,0,300000,0",
            "false,65,10000,300000,495.00", "false,65,250000,0,12180.71",
            "false,65,250001,0,12375.05", "true,65,500000,0,24361.43",
            "true,65,500001,0,24750.05", "false,60,2925,0,0",
            "false,60,2926,0,0.05", "false,65,3925,0,0",
            "false,65,3926,0,0.05"})
    void independentRetirementAndExemptionFixtures(boolean couple, int age, String investment,
            String retirement, String expected) throws Exception {
        var amount = new BigDecimal(retirement);
        var source = IncomeSourceAttribution.withdrawal(new TraditionalIRA("IRA", AccountOwnership.PRIMARY, amount),
                amount, IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                IncomeSourceAttribution.Purpose.CASH_FLOW);
        var result = calculate(couple, age, new TaxIncome(BigDecimal.ZERO, BigDecimal.ZERO, amount,
                BigDecimal.ZERO, new BigDecimal(investment), List.of(source)));
        assertEquals(new BigDecimal(expected).setScale(2), result.incomeTax().setScale(2));
        assertEquals(StateTaxJurisdiction.IL, result.jurisdiction());
        assertEquals(ProductionStateTaxResult.Status.CALCULATED, result.calculationStatus());
        assertTrue(result.michiganDetails().isEmpty());
    }

    static ProductionStateTaxResult calculate(boolean couple, int age, TaxIncome income) throws Exception {
        return new ProductionStateTaxService().calculate(request(couple, age, income));
    }

    static StateTaxCalculationRequest request(boolean couple, int age, TaxIncome income) throws Exception {
        var primary = new Person("Primary", "", LocalDate.of(2026 - age, 1, 1));
        var spouse = couple ? new Person("Spouse", "", LocalDate.of(2026 - age, 1, 1)) : null;
        var household = new Household(primary, spouse);
        var status = couple ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE;
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        return new StateTaxCalculationRequest(2026, status,
                TaxHouseholdContext.capture(household, LocalDate.of(2026, 1, 1), null), income,
                new FederalTaxCalculator().calculate(income, status, rules), rules,
                StateTaxAssumptions.simplified(StateTaxJurisdiction.IL));
    }

    @ParameterizedTest
    @EnumSource(value = IncomeSourceAttribution.Kind.class, names = {
            "ORDINARY_RETIREMENT_WITHDRAWAL", "RMD", "OPENING_RMD", "ROTH_CONVERSION"})
    void eachRetirementFlowSubtractsTaxableAmountRatherThanGross(IncomeSourceAttribution.Kind kind) throws Exception {
        var account = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("100000"));
        var source = new IncomeSourceAttribution(kind, IncomeSourceAttribution.Purpose.INCOME,
                Optional.of(AccountOwnership.PRIMARY), Optional.of(IncomeSourceAttribution.AccountReference.capture(account)),
                Optional.empty(), "Partly taxable", new BigDecimal("100000"), Optional.of(new BigDecimal("60000")),
                OptionalInt.of(2026), Optional.empty());
        boolean conversion = kind == IncomeSourceAttribution.Kind.ROTH_CONVERSION;
        var income = new TaxIncome(BigDecimal.ZERO, BigDecimal.ZERO,
                conversion ? BigDecimal.ZERO : new BigDecimal("60000"),
                conversion ? new BigDecimal("60000") : BigDecimal.ZERO, new BigDecimal("10000"), List.of(source));
        assertEquals(new BigDecimal("300.71"), calculate(false, 65, income).incomeTax());
    }

    @ParameterizedTest
    @EnumSource(value = IncomeSourceAttribution.Kind.class, names = {"INTEREST", "DIVIDEND", "CAPITAL_GAIN"})
    void investmentClassificationNeverReceivesRetirementSubtraction(IncomeSourceAttribution.Kind kind) throws Exception {
        var amount = new BigDecimal("10000");
        var source = IncomeSourceAttribution.aggregate(kind, IncomeSourceAttribution.Purpose.INCOME,
                kind.name(), amount, Optional.of(amount));
        assertEquals(new BigDecimal("300.71"), calculate(false, 65,
                new TaxIncome(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, amount,
                        List.of(source))).incomeTax());
    }

    @ParameterizedTest
    @CsvSource({"0,0.00", "10000,300.71"})
    void socialSecuritySubtractsOnlyTheFederallyIncludedAmount(String investment, String expected) throws Exception {
        var income = new TaxIncome(BigDecimal.ZERO, new BigDecimal("40000"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal(investment));
        assertEquals(new BigDecimal(expected), calculate(false, 65, income).incomeTax());
    }

    @Test
    void unknownPensionQualificationIsConservativeAndDiagnosed() throws Exception {
        var result = calculate(false, 65, new TaxIncome(new BigDecimal("20000"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10000")));
        assertEquals(new BigDecimal("1290.71"), result.incomeTax());
        assertTrue(result.limitations().stream().anyMatch(text -> text.contains("Pension plan qualification unavailable")));
    }

    @Test
    void missingRetirementAttributionCannotExcludeInvestmentOrUnknownIncome() throws Exception {
        var result = calculate(false, 65, new TaxIncome(BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("20000"), BigDecimal.ZERO, new BigDecimal("10000")));
        assertEquals(new BigDecimal("1290.71"), result.incomeTax());
        assertTrue(result.limitations().stream().anyMatch(text -> text.contains("Unassessed retirement")));
    }

    @Test
    void ownerSpecificConversionAndWithdrawalsShareNoArtificialExtraPool() throws Exception {
        var primary = IncomeSourceAttribution.withdrawal(new TraditionalIRA("Primary", AccountOwnership.PRIMARY,
                new BigDecimal("60000")), new BigDecimal("60000"), IncomeSourceAttribution.Kind.RMD,
                IncomeSourceAttribution.Purpose.CASH_FLOW);
        var spouse = IncomeSourceAttribution.withdrawal(new TraditionalIRA("Spouse", AccountOwnership.SPOUSE,
                new BigDecimal("40000")), new BigDecimal("40000"), IncomeSourceAttribution.Kind.ROTH_CONVERSION,
                IncomeSourceAttribution.Purpose.CONVERSION);
        var income = new TaxIncome(BigDecimal.ZERO, new BigDecimal("40000"), new BigDecimal("60000"),
                new BigDecimal("40000"), new BigDecimal("20000"), List.of(primary, spouse));
        assertEquals(new BigDecimal("601.43"), calculate(true, 65, income).incomeTax());
    }

    @ParameterizedTest
    @CsvSource({"1961-12-31,300.71", "1962-01-01,350.21"})
    void seniorAllowanceUsesAgeReachedDuringTaxYear(String birth, String expected) throws Exception {
        var original = request(false, 60, new TaxIncome(BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10000")));
        var context = new TaxHouseholdContext(original.household().distributionDate(),
                java.util.Map.of(AccountOwnership.PRIMARY, LocalDate.parse(birth)),
                original.household().householdOwners(), original.household().activeOwners(), Optional.empty());
        var changed = new StateTaxCalculationRequest(2026, original.filingStatus(), context, original.income(),
                original.federal(), original.projectedRules(), original.assumptions());
        assertEquals(new BigDecimal(expected), new ProductionStateTaxService().calculate(changed).incomeTax());
    }

    @ParameterizedTest
    @CsvSource({"TRADITIONAL_IRA", "ROLLOVER_IRA", "TRADITIONAL_401K", "TRADITIONAL_403B"})
    void authoritativeQualifiedAccountTypesSubtractOnlyTheirTaxableIncome(AccountType type) throws Exception {
        var amount = new BigDecimal("100000");
        var account = com.daviddunn.retirementplanner.domain.financial.AccountFactory.create(type, "Retirement",
                AccountOwnership.PRIMARY, amount);
        var source = IncomeSourceAttribution.withdrawal(account, amount, IncomeSourceAttribution.Kind.RMD,
                IncomeSourceAttribution.Purpose.CASH_FLOW);
        assertEquals(new BigDecimal("300.71"), calculate(false, 65, new TaxIncome(BigDecimal.ZERO,
                BigDecimal.ZERO, amount, BigDecimal.ZERO, new BigDecimal("10000"), List.of(source))).incomeTax());
    }

    @Test
    void conversionAndWithdrawalShareTheirActualFederallyIncludedAmounts() throws Exception {
        var account = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, new BigDecimal("100000"));
        var first = IncomeSourceAttribution.withdrawal(account, new BigDecimal("40000"),
                IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW);
        var second = IncomeSourceAttribution.withdrawal(account, new BigDecimal("60000"),
                IncomeSourceAttribution.Kind.ROTH_CONVERSION, IncomeSourceAttribution.Purpose.CONVERSION);
        assertEquals(new BigDecimal("300.71"), calculate(false, 65, new TaxIncome(BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("40000"), new BigDecimal("60000"), new BigDecimal("10000"),
                List.of(first, second))).incomeTax());
        assertThrows(IllegalArgumentException.class, () -> calculate(false, 65, new TaxIncome(BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("40000"), new BigDecimal("60000"), new BigDecimal("10000"),
                List.of(first, second, second))));
    }

    @Test
    void qualifiedRothDistributionNeverCreatesAnAdditionalTaxableAmount() throws Exception {
        var roth = new com.daviddunn.retirementplanner.domain.financial.RothIRA("Roth", AccountOwnership.PRIMARY,
                new BigDecimal("100000"));
        var source = IncomeSourceAttribution.withdrawal(roth, new BigDecimal("100000"),
                IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL, IncomeSourceAttribution.Purpose.CASH_FLOW);
        assertEquals(new BigDecimal("300.71"), calculate(false, 65, new TaxIncome(BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10000"), List.of(source))).incomeTax());
    }

    @Test
    void singleReturnDoesNotReceiveTwoSeniorAllowancesFromACoupleContext() throws Exception {
        var original = request(true, 65, new TaxIncome(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("20000")));
        var single = new StateTaxCalculationRequest(2026, FilingStatus.SINGLE, original.household(),
                original.income(), original.federal(), original.projectedRules(), original.assumptions());
        assertEquals(new BigDecimal("795.71"), new ProductionStateTaxService().calculate(single).incomeTax());
    }

    @Test
    void modeledDeathDoesNotGrantASeniorBirthdayAfterDeath() throws Exception {
        var original = request(true, 65, new TaxIncome(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("20000")));
        var context = new TaxHouseholdContext(original.household().distributionDate(),
                java.util.Map.of(AccountOwnership.PRIMARY, LocalDate.of(1961, 12, 31),
                        AccountOwnership.SPOUSE, LocalDate.of(1950, 1, 1)), original.household().householdOwners(),
                java.util.Set.of(AccountOwnership.SPOUSE), Optional.empty());
        var joint = new StateTaxCalculationRequest(2026, FilingStatus.MARRIED_FILING_JOINTLY, context,
                original.income(), original.federal(), original.projectedRules(), original.assumptions());
        // 20000 - 5850 basic - 1000 for the surviving older spouse.
        assertEquals(new BigDecimal("650.93"), new ProductionStateTaxService().calculate(joint).incomeTax());
    }
}
