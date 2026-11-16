package com.daviddunn.retirementplanner.domain.tax.state.colorado;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated worksheets, not Colorado production admission or future-rate fixtures. */
class ColoradoPreparatoryRulesTest {
    @ParameterizedTest
    @CsvSource({"false,60,60,50000,33900,0", "false,65,60,50000,25850,0",
            "true,60,66,100000,60150,0", "false,65,60,80000,56150,0",
            "false,60,60,300000,283900,0", "false,60,60,300001,299001,15100"})
    void independentFederalBaseAndAddback(boolean joint, int a, int b, String agi, String expected, String addback) throws Exception {
        var status = joint ? FilingStatus.MARRIED_FILING_JOINTLY : FilingStatus.SINGLE;
        var federal = new FederalTaxCalculator().calculate(new TaxIncome(n("0"), n("0"), n(agi), n("0"), n("0")),
                status, new GovernmentRulesRepository().load("/rules/government-rules-2026.json"));
        var original = federal.getModeledBaseBreakdown();
        var base = new ColoradoFederalTaxBaseBridge().calculate(2026, status, context(a, b, joint), federal);
        assertEquals(0, n(expected).compareTo(base.beforeStateRetirementSubtractions()));
        assertEquals(0, n(addback).compareTo(base.standardDeductionAddback()));
        assertEquals(original, federal.getModeledBaseBreakdown());
    }

    @Test
    void officialProportionalAllocationAndPersonalSsPriority() {
        var result = new ColoradoRetirementAdjustments().calculate(2026, FilingStatus.MARRIED_FILING_JOINTLY,
                context(66, 62, true), n("134000"), n("34000"),
                Map.of(AccountOwnership.PRIMARY, n("30000"), AccountOwnership.SPOUSE, n("10000")),
                Map.of(AccountOwnership.PRIMARY, n("10000"), AccountOwnership.SPOUSE, n("10000")));
        assertEquals(0, n("25500").compareTo(result.taxableSocialSecurityByRecipient().get(AccountOwnership.PRIMARY)));
        assertEquals(0, n("8500").compareTo(result.taxableSocialSecurityByRecipient().get(AccountOwnership.SPOUSE)));
        assertEquals(0, n("44000").compareTo(result.totalSubtraction()));
    }

    @Test
    void agiThresholdAndZeroGrossGuardAreExplicit() {
        var adjustments = new ColoradoRetirementAdjustments();
        assertEquals(0, n("30459").compareTo(adjustments.calculate(2026, FilingStatus.SINGLE, context(62, 60, false),
                n("74999"), n("30459"), Map.of(AccountOwnership.PRIMARY, n("40000")), Map.of()).totalSubtraction()));
        assertEquals(0, n("20000").compareTo(adjustments.calculate(2026, FilingStatus.SINGLE, context(62, 60, false),
                n("75000.85"), n("30459.85"), Map.of(AccountOwnership.PRIMARY, n("40000")), Map.of()).totalSubtraction()));
        assertThrows(IllegalArgumentException.class, () -> adjustments.calculate(2026, FilingStatus.SINGLE,
                context(62, 60, false), n("1"), n("1"), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ColoradoTaxRules().rate(2027));
        assertFalse(new ColoradoTaxRules().productionAdmitted());
    }

    @Test
    void federalSeniorDeductionsUseDayBeforeBirthdayAndDeathDate() throws Exception {
        var rules = new GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var income = new TaxIncome(n("0"), n("0"), n("50000"), n("0"), n("0"));
        var federal = new FederalTaxCalculator().calculate(income, FilingStatus.SINGLE, rules);
        var owners = Set.of(AccountOwnership.PRIMARY);
        var household = new TaxHouseholdContext(LocalDate.of(2026, 1, 1),
                Map.of(AccountOwnership.PRIMARY, LocalDate.of(1962, 1, 1)), owners, owners, Optional.empty());
        // IRS Pub 501: age 65 is reached on the day before the 65th birthday.
        assertEquals(0, n("25850").compareTo(new ColoradoFederalTaxBaseBridge()
                .calculate(2026, FilingStatus.SINGLE, household, federal).beforeStateRetirementSubtractions()));
        var jointFederal = new FederalTaxCalculator().calculate(income, FilingStatus.MARRIED_FILING_JOINTLY, rules);
        var couple = Set.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE);
        var deathYear = new TaxHouseholdContext(LocalDate.of(2026, 1, 1),
                Map.of(AccountOwnership.PRIMARY, LocalDate.of(1961, 1, 2), AccountOwnership.SPOUSE, LocalDate.of(1966, 1, 1)),
                couple, Set.of(AccountOwnership.SPOUSE), Optional.empty());
        assertEquals(0, n("1650").compareTo(new ColoradoFederalTaxBaseBridge()
                .calculate(2026, FilingStatus.MARRIED_FILING_JOINTLY, deathYear, jointFederal).ageDeduction()));
        var diedBeforeBirthday = new TaxHouseholdContext(LocalDate.of(2026, 1, 1),
                Map.of(AccountOwnership.PRIMARY, LocalDate.of(1961, 1, 3), AccountOwnership.SPOUSE, LocalDate.of(1966, 1, 1)),
                couple, Set.of(AccountOwnership.SPOUSE), Optional.empty());
        assertEquals(0, new ColoradoFederalTaxBaseBridge().calculate(2026, FilingStatus.MARRIED_FILING_JOINTLY,
                diedBeforeBirthday, jointFederal).ageDeduction().signum());
    }

    private static TaxHouseholdContext context(int a, int b, boolean joint) {
        var births = new EnumMap<AccountOwnership, LocalDate>(AccountOwnership.class);
        births.put(AccountOwnership.PRIMARY, LocalDate.of(2026 - a, 1, 1));
        if (joint) births.put(AccountOwnership.SPOUSE, LocalDate.of(2026 - b, 1, 1));
        return new TaxHouseholdContext(LocalDate.of(2026, 1, 1), births, births.keySet(), births.keySet(), Optional.empty());
    }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
}
