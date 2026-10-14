package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class IowaSourceContractTest {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal AMOUNT = new BigDecimal("40000");

    @ParameterizedTest
    @ValueSource(strings = {"missingOwner", "missingYear", "missingIncluded", "basis", "roth", "employer", "external", "unattributedTrial"})
    void materialUnknownsNeverBecomeLowIncomeCalculatedZero(String shape) throws Exception {
        var sourceAccount = shape.equals("roth") ? new RothIRA("Roth", AccountOwnership.PRIMARY, AMOUNT)
                : shape.equals("employer") ? new Traditional401K("Plan", AccountOwnership.PRIMARY, AMOUNT)
                : new TraditionalIRA("IRA", AccountOwnership.PRIMARY, AMOUNT);
        var kind = shape.equals("external") || shape.equals("unattributedTrial")
                ? IncomeSourceAttribution.Kind.ROTH_CONVERSION : IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL;
        var source = new IncomeSourceAttribution(kind,
                kind == IncomeSourceAttribution.Kind.ROTH_CONVERSION ? IncomeSourceAttribution.Purpose.CONVERSION : IncomeSourceAttribution.Purpose.CASH_FLOW,
                shape.equals("missingOwner") || shape.equals("unattributedTrial") ? Optional.empty() : Optional.of(AccountOwnership.PRIMARY),
                shape.equals("missingOwner") || shape.equals("unattributedTrial") ? Optional.empty() : Optional.of(IncomeSourceAttribution.AccountReference.capture(sourceAccount)),
                Optional.empty(), shape, AMOUNT,
                shape.equals("missingIncluded") ? Optional.empty() : Optional.of(shape.equals("basis") ? BigDecimal.TEN : shape.equals("roth") ? ZERO : AMOUNT),
                shape.equals("missingYear") ? OptionalInt.empty() : OptionalInt.of(2026), Optional.empty());
        var income = new TaxIncome(ZERO, ZERO, kind == IncomeSourceAttribution.Kind.ROTH_CONVERSION || shape.equals("roth") ? ZERO
                : shape.equals("basis") ? BigDecimal.TEN : AMOUNT, kind == IncomeSourceAttribution.Kind.ROTH_CONVERSION ? AMOUNT : ZERO,
                ZERO, List.of(source));
        var result = new IowaProductionTaxCalculator().calculate(request(income));
        assertTrue(result.estimatedIncomeTax().isEmpty());
        var reason = switch (shape) {
            case "missingOwner", "employer" -> StateTaxUnavailableReason.MISSING_RETIREMENT_QUALIFICATION;
            case "missingIncluded", "basis" -> StateTaxUnavailableReason.MISSING_DISTRIBUTION_BASIS;
            case "roth" -> StateTaxUnavailableReason.NONQUALIFIED_ROTH_BASIS_REQUIRED;
            default -> StateTaxUnavailableReason.UNSUPPORTED_TRANSACTION_TYPE;
        };
        assertTrue(result.unavailableReasons().contains(reason), shape);
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "scaledDuplicate", "year", "excess", "socialSecurityExcess"})
    void malformedSourcesPreserveInputContractErrors(String shape) throws Exception {
        var account = new TraditionalIRA("IRA", AccountOwnership.PRIMARY, AMOUNT);
        var source = IncomeSourceAttribution.withdrawal(account, AMOUNT, IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                IncomeSourceAttribution.Purpose.CASH_FLOW).inYear(shape.equals("year") ? 2027 : 2026);
        List<IncomeSourceAttribution> sources = List.of(source);
        BigDecimal aggregate = AMOUNT;
        if (shape.equals("duplicate") || shape.equals("scaledDuplicate")) {
            var second = shape.equals("duplicate") ? source : IncomeSourceAttribution.withdrawal(account, new BigDecimal("40000.00"),
                    source.kind(), source.purpose()).inYear(2026);
            sources = List.of(source, second); aggregate = AMOUNT.multiply(BigDecimal.TWO);
        }
        if (shape.equals("excess")) aggregate = BigDecimal.ONE;
        if (shape.equals("socialSecurityExcess")) {
            sources = List.of(IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.SOCIAL_SECURITY,
                    IncomeSourceAttribution.Purpose.INCOME, "SS", AMOUNT, Optional.empty()).inYear(2026));
            aggregate = ZERO;
        }
        var input = request(new TaxIncome(ZERO, ZERO, aggregate, ZERO, ZERO, sources));
        assertThrows(IllegalArgumentException.class, () -> new IowaProductionTaxCalculator().calculate(input));
    }

    @Test
    void fullyAttributedPartialExecutionExcludesOnlyActualTransfersAcrossBothOwners() throws Exception {
        var sources = new ArrayList<IncomeSourceAttribution>();
        for (var owner : List.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE)) {
            sources.add(new IncomeSourceAttribution(IncomeSourceAttribution.Kind.ROTH_CONVERSION, IncomeSourceAttribution.Purpose.CONVERSION,
                    Optional.of(owner), Optional.of(IncomeSourceAttribution.AccountReference.capture(new RolloverIRA("Source", owner, AMOUNT))),
                    Optional.of(IncomeSourceAttribution.AccountReference.capture(new RothIRA("Roth", owner, ZERO))), "Actual transfer",
                    AMOUNT, Optional.of(AMOUNT), OptionalInt.of(2026), Optional.empty()));
        }
        var actual = AMOUNT.multiply(BigDecimal.TWO); // Requested 100000, actual 80000: shortfall is not income.
        var input = IowaProductionTaxCalculatorTest.request(LocalDate.of(1966, 1, 1), LocalDate.of(1966, 1, 1),
                new TaxIncome(ZERO, ZERO, ZERO, actual, ZERO, sources), new BigDecimal("32200"));
        assertEquals(0, new IowaProductionTaxCalculator().calculate(input).incomeTax().signum());
    }

    @ParameterizedTest
    @ValueSource(ints = {2025, 2026, 2027, 2028, 2029, 2040})
    void nominalYearPolicyIsExplicitAndNeverModelsAnEnhancedDeduction(int year) throws Exception {
        var current = request(new TaxIncome(ZERO, ZERO, ZERO, ZERO, new BigDecimal("60000")));
        var h = current.household();
        var input = new StateTaxCalculationRequest(year, current.filingStatus(), new TaxHouseholdContext(LocalDate.of(year, 1, 1),
                h.birthDates(), h.householdOwners(), h.activeOwners(), h.pensionIncome()), current.income(), current.federal(), current.projectedRules(), current.assumptions());
        var result = new IowaProductionTaxCalculator().calculate(input);
        if (year < 2026) assertEquals(List.of(StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE), result.unavailableReasons());
        else {
            assertEquals(new BigDecimal(year >= 2030 ? "1608.20" : "1628.20"), result.incomeTax()); // Same base/rate; actual owner ages into credit.
            assertTrue(result.limitations().get(1).contains("nominal 2026"));
            assertTrue(result.limitations().get(2).contains(year <= 2028 ? "omitted" : "expired after 2028"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MARRIED_FILING_SEPARATELY", "HEAD_OF_HOUSEHOLD", "QUALIFYING_SURVIVING_SPOUSE"})
    void filingStatusesAreNotRemapped(String status) throws Exception {
        var current = request(new TaxIncome(ZERO, ZERO, ZERO, ZERO, ZERO));
        var result = new IowaProductionTaxCalculator().calculate(new StateTaxCalculationRequest(2026, FilingStatus.valueOf(status),
                current.household(), current.income(), current.federal(), current.projectedRules(), current.assumptions()));
        assertEquals(List.of(StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE), result.unavailableReasons());
    }

    private static StateTaxCalculationRequest request(TaxIncome income) throws Exception {
        return IowaProductionTaxCalculatorTest.request(LocalDate.of(1966, 1, 1), null, income, new BigDecimal("16100"));
    }
}
