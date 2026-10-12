package com.daviddunn.retirementplanner.domain.tax.state.simplified;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxCalculator;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRequest.*;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRules.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Keeps the original discrepancy amounts traceable while checking the corrected production boundary. */
class MichiganPrototypeComparisonTest {

    @ParameterizedTest
    @CsvSource({
            "SINGLE,0,10000,10000,0,0.00,0,4100,174.25",
            "MARRIED_FILING_JOINTLY,0,20000,20000,0,0.00,0,8200,348.50",
            "SINGLE,100000,0,40762,59238,2517.62,67610,26490,1125.83",
            "MARRIED_FILING_JOINTLY,200000,0,81524,118476,5035.23,135220,52980,2251.65"})
    void preservesObservedProductionAndIndependentPrototypeValues(
            FilingStatus status, String iraText, String interestText,
            String productionDeduction, String productionTaxable, String productionTax,
            String prototypeDeduction, String prototypeTaxable, String prototypeTax) throws Exception {
        BigDecimal ira = new BigDecimal(iraText);
        BigDecimal interest = new BigDecimal(interestText);
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var primary = new com.daviddunn.retirementplanner.domain.model.Person("Primary", "Fixture", java.time.LocalDate.of(1966, 1, 1));
        var spouse = status == FilingStatus.SINGLE ? null
                : new com.daviddunn.retirementplanner.domain.model.Person("Spouse", "Fixture", java.time.LocalDate.of(1966, 1, 1));
        var household = new com.daviddunn.retirementplanner.domain.model.Household(primary, spouse);
        var account = new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA("IRA",
                com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY, ira);
        var source = com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.withdrawal(account, ira,
                com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.CASH_FLOW);
        var taxIncome = new TaxIncome(BigDecimal.ZERO, BigDecimal.ZERO, ira, BigDecimal.ZERO, interest, List.of(source));
        var production = new MichiganTaxCalculator().calculate(
                taxIncome, status, rules.getMichiganTaxRules(),
                com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxContext.capture(
                        household, java.time.LocalDate.of(2026, 1, 1), null));
        amount(prototypeDeduction, production.retirementDeduction());
        amount(prototypeTaxable, production.taxableIncome());
        amount(prototypeTax, production.incomeTax().setScale(2, RoundingMode.HALF_UP));
        List<PersonIncome> people = status == FilingStatus.SINGLE
                ? List.of(person(Owner.PRIMARY, ira, interest))
                : List.of(person(Owner.PRIMARY, ira.divide(BigDecimal.TWO), interest.divide(BigDecimal.TWO)),
                        person(Owner.SPOUSE, ira.divide(BigDecimal.TWO), interest.divide(BigDecimal.TWO)));
        var prototype = new SimplifiedStateTaxCalculator().calculate(new StateTaxRequest(
                "MI", 2026, status, ira.add(interest), BigDecimal.ZERO, people)).estimate().orElseThrow();
        amount(prototypeDeduction, prototype.retirementExclusions().stream().map(StateTaxResult.Line::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        amount(prototypeTaxable, prototype.taxableIncome());
        amount(prototypeTax, prototype.finalEstimatedTax());
    }

    private static PersonIncome person(Owner owner, BigDecimal ira, BigDecimal interest) {
        return new PersonIncome(owner, new BigDecimal("60"), BigDecimal.ZERO, BigDecimal.ZERO, Set.of(), List.of(
                new Income(IncomeType.TRADITIONAL_IRA, ira, ira, PensionSource.PRIVATE,
                        Map.of("QUALIFYING_RETIREMENT_BENEFIT_ONLY", Qualification.ELIGIBLE)),
                new Income(IncomeType.INTEREST, interest, interest, PensionSource.PRIVATE, Map.of())));
    }

    private static void amount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
