package com.daviddunn.retirementplanner.domain.tax.state.oklahoma;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/** HB2764 / HB4072 and 2026 Form511, reviewed 2026-11-17; untriggered cuts are not forecasts. */
public final class OklahomaTaxRules {
    public static final String VERSION = "OK-2026-1";
    public static final String REFERENCE_KEY = "oklahoma-income-tax-rules";
    public static final BigDecimal RETIREMENT_LIMIT = new BigDecimal("10000");

    public BigDecimal standardDeduction(int year, FilingStatus status) {
        requireYear(year);
        return new BigDecimal(joint(status) ? "12700" : "6350");
    }

    public BigDecimal exemptions(int year, FilingStatus status, TaxHouseholdContext household,
            Set<AccountOwnership> filers, BigDecimal agi, BigDecimal iraConversions) {
        requireYear(year);
        Objects.requireNonNull(household);
        Objects.requireNonNull(filers);
        var total = new BigDecimal("1000").multiply(BigDecimal.valueOf(filers.size()));
        var limit = new BigDecimal(joint(status) ? "25000" : "15000");
        // This test alone removes IRA conversion income; it is not an unlimited conversion exclusion.
        if (agi.subtract(iraConversions).compareTo(limit) > 0) return total;
        for (var owner : filers) {
            var ageDate = household.activeOwners().contains(owner)
                    ? LocalDate.of(year, 12, 31) : LocalDate.of(year, 1, 1);
            if (!household.birthDates().get(owner).plusYears(65).minusDays(1).isAfter(ageDate))
                total = total.add(new BigDecimal("1000"));
        }
        return total;
    }

    public BigDecimal tax(int year, FilingStatus status, BigDecimal taxableIncome) {
        requireYear(year);
        Objects.requireNonNull(taxableIncome);
        if (taxableIncome.signum() < 0) throw new IllegalArgumentException("Negative Oklahoma taxable income.");
        var factor = BigDecimal.valueOf(joint(status) ? 2 : 1);
        var zero = new BigDecimal("3750").multiply(factor);
        var middle = new BigDecimal("4900").multiply(factor);
        var upper = new BigDecimal("7200").multiply(factor);
        var tax = taxableIncome.subtract(zero).max(BigDecimal.ZERO).min(middle.subtract(zero))
                .multiply(new BigDecimal("0.025"))
                .add(taxableIncome.subtract(middle).max(BigDecimal.ZERO).min(upper.subtract(middle))
                        .multiply(new BigDecimal("0.035")))
                .add(taxableIncome.subtract(upper).max(BigDecimal.ZERO).multiply(new BigDecimal("0.045")));
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean joint(FilingStatus status) {
        Objects.requireNonNull(status);
        if (status != FilingStatus.SINGLE && status != FilingStatus.MARRIED_FILING_JOINTLY)
            throw new IllegalArgumentException("Oklahoma supports Single/MFJ.");
        return status == FilingStatus.MARRIED_FILING_JOINTLY;
    }

    private static void requireYear(int year) {
        if (year < 2026) throw new IllegalArgumentException("Oklahoma reviewed rules start in 2026.");
    }
}
