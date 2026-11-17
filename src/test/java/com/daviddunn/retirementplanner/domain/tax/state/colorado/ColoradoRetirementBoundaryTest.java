package com.daviddunn.retirementplanner.domain.tax.state.colorado;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.domain.tax.state.colorado.ColoradoFederalBridgeCompletionTest.*;

class ColoradoRetirementBoundaryTest {
    @ParameterizedTest
    @CsvSource({"1972-01-01,0", "1971-12-31,20000", "1962-01-01,20000", "1961-12-31,24000"})
    void age55And65UseStateYearEndBoundary(String birthday, String expected) {
        var births = Map.of(AccountOwnership.PRIMARY, LocalDate.parse(birthday));
        var household = new TaxHouseholdContext(LocalDate.of(2026, 1, 1), births, births.keySet(), births.keySet(), Optional.empty());
        same(expected, calculate(household, false, "100000", "0", Map.of(),
                Map.of(AccountOwnership.PRIMARY, n("50000"))).totalSubtraction());
    }

    @ParameterizedTest
    @CsvSource({"75000,30000", "75000.01,20000", "74999.99,30000"})
    void singleFullSocialSecurityStrictAgiBoundary(String agi, String expected) {
        same(expected, calculate(person(false, 1964, 1960), false, agi, "30000",
                Map.of(AccountOwnership.PRIMARY, n("40000")), Map.of(AccountOwnership.PRIMARY, n("10000"))).totalSubtraction());
    }

    @ParameterizedTest
    @CsvSource({"95000,30000", "95000.01,20000", "94999.99,30000"})
    void jointFullSocialSecurityStrictAgiBoundary(String agi, String expected) {
        same(expected, calculate(person(true, 1964, 1974), true, agi, "30000",
                Map.of(AccountOwnership.PRIMARY, n("40000")), Map.of(AccountOwnership.PRIMARY, n("10000"))).totalSubtraction());
    }

    @Test
    void sixtyFiveFullSocialSecurityCanExceedCapButLeavesNoOtherAllowance() {
        var result = calculate(person(false, 1960, 1960), false, "300001", "34000",
                Map.of(AccountOwnership.PRIMARY, n("40000")), Map.of(AccountOwnership.PRIMARY, n("50000")));
        same("34000", result.totalSubtraction());
        same("0", result.otherRetirementSubtractions().get(AccountOwnership.PRIMARY));
    }

    @Test
    void proportionalRecurringDecimalsPreserveHouseholdTotalAndIndividualCaps() {
        var result = calculate(person(true, 1960, 1964), true, "120000", "20000",
                Map.of(AccountOwnership.PRIMARY, n("10000"), AccountOwnership.SPOUSE, n("20000")),
                Map.of(AccountOwnership.PRIMARY, n("50000"), AccountOwnership.SPOUSE, n("50000")));
        same("20000", result.taxableSocialSecurityByRecipient().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        same("44000", result.totalSubtraction());
        assertTrue(result.taxableSocialSecurityByRecipient().get(AccountOwnership.PRIMARY)
                .compareTo(result.taxableSocialSecurityByRecipient().get(AccountOwnership.SPOUSE)) < 0);
    }

    @ParameterizedTest
    @CsvSource({"19999.99,19999.99", "20000,20000", "20000.01,20000"})
    void personalCapBoundaryDoesNotRoundReceipts(String receipts, String expected) {
        same(expected, calculate(person(false, 1964, 1960), false, "100000", "0", Map.of(),
                Map.of(AccountOwnership.PRIMARY, n(receipts))).totalSubtraction());
    }

    @Test
    void invalidRecipientGrossAndTaxableSocialSecurityAreRejected() {
        var household = person(true, 1960, 1964).withActiveOwners(java.util.Set.of(AccountOwnership.PRIMARY));
        assertThrows(IllegalArgumentException.class, () -> calculate(household, true, "100000", "10000",
                Map.of(AccountOwnership.SPOUSE, n("20000")), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> calculate(household, true, "100000", "10000", Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> calculate(household, true, "100000", "18000",
                Map.of(AccountOwnership.PRIMARY, n("20000")), Map.of()));
    }

    @Test
    void exactHalfCentRoundsOnlyAtFinalTax() {
        assertEquals(n("0.06"), new ColoradoTaxRules().tax(2026, n("1.25")));
        assertEquals(n("4.41"), new ColoradoTaxRules().tax(2026, n("100.125")));
        assertThrows(IllegalArgumentException.class, () -> new ColoradoTaxRules().tax(2026, n("-1")));
    }

    private static ColoradoRetirementAdjustments.Adjustments calculate(TaxHouseholdContext household, boolean joint,
            String agi, String taxable, Map<AccountOwnership, BigDecimal> gross, Map<AccountOwnership, BigDecimal> other) {
        return new ColoradoRetirementAdjustments().calculate(2026,
                joint ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE, household, n(agi), n(taxable), gross, other);
    }

    private static TaxHouseholdContext person(boolean joint, int primaryYear, int spouseYear) {
        var births = joint ? Map.of(AccountOwnership.PRIMARY, LocalDate.of(primaryYear, 6, 1),
                AccountOwnership.SPOUSE, LocalDate.of(spouseYear, 8, 1))
                : Map.of(AccountOwnership.PRIMARY, LocalDate.of(primaryYear, 6, 1));
        return new TaxHouseholdContext(LocalDate.of(2026, 1, 1), births, births.keySet(), births.keySet(), Optional.empty());
    }
}
