package com.daviddunn.retirementplanner.domain.tax.state.mississippi;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/** HB1 / 27-7-5, 27-7-15(4)(ff), state deductions/exemptions; reviewed 2026-11-17. */
public final class MississippiTaxRules {
    public static final String VERSION = "MS-2026-1";
    public static final String REFERENCE_KEY = "mississippi-income-tax-rules";
    public static final BigDecimal ZERO_BAND = new BigDecimal("10000");
    public static final BigDecimal SENIOR_EXEMPTION = new BigDecimal("1500");

    public BigDecimal rate(int year) {
        if (year < 2026) throw new IllegalArgumentException("Mississippi reviewed rules start in 2026.");
        return new BigDecimal(switch (year) {
            case 2026 -> "0.04";
            case 2027 -> "0.0375";
            case 2028 -> "0.035";
            case 2029 -> "0.0325";
            default -> "0.03";
        });
    }

    public BigDecimal standardDeduction(FilingStatus status) {
        return new BigDecimal(joint(status) ? "4600" : "2300");
    }

    public BigDecimal exemptions(int year, FilingStatus status, TaxHouseholdContext household,
            Set<AccountOwnership> filers) {
        var total = new BigDecimal(joint(status) ? "12000" : "6000");
        for (var owner : filers) {
            var date = household.activeOwners().contains(owner)
                    ? LocalDate.of(year, 12, 31) : LocalDate.of(year, 1, 1);
            if (!household.birthDates().get(owner).plusYears(65).isAfter(date))
                total = total.add(SENIOR_EXEMPTION);
        }
        return total;
    }

    /** Refuse only if this unresolved convention would change the final calculated tax. */
    public int ambiguousSeniorBirthdays(int year, TaxHouseholdContext household, Set<AccountOwnership> filers) {
        int count = 0;
        for (var owner : filers) {
            var ageDate = household.activeOwners().contains(owner)
                    ? LocalDate.of(year, 12, 31) : LocalDate.of(year, 1, 1);
            if (household.birthDates().get(owner).plusYears(65).equals(ageDate.plusDays(1)))
                count++;
        }
        return count;
    }

    private static boolean joint(FilingStatus status) {
        Objects.requireNonNull(status);
        if (status != FilingStatus.SINGLE && status != FilingStatus.MARRIED_FILING_JOINTLY)
            throw new IllegalArgumentException("Mississippi supports Single/MFJ.");
        return status == FilingStatus.MARRIED_FILING_JOINTLY;
    }
}
