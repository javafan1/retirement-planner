package com.daviddunn.retirementplanner.domain.tax.state.georgia;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** HB463 §§2-1/2-3 and OCGA48-7-27. Reviewed 2026-11-17; conditional steps not activated. */
public final class GeorgiaTaxRules {
    public static final String VERSION = "GA-2026-1";
    public static final String REFERENCE_KEY = "georgia-income-tax-rules";
    public BigDecimal rate(int year) {
        requireYear(year);
        return new BigDecimal("0.0499");
    }
    public BigDecimal standardDeduction(int year, FilingStatus status) {
        requireYear(year);
        return switch (Objects.requireNonNull(status)) {
            case SINGLE -> new BigDecimal("15000");
            case MARRIED_FILING_JOINTLY -> new BigDecimal("30000");
            default -> throw new IllegalArgumentException("Georgia supports Single/MFJ only.");
        };
    }
    public BigDecimal retirementLimit(int year, LocalDate birth, boolean active) {
        requireYear(year);
        Objects.requireNonNull(birth);
        LocalDate ageDate = LocalDate.of(year, active ? 12 : 1, active ? 31 : 1);
        // State guidance reviewed does not establish the day-before rule at these two thresholds.
        for (int threshold : new int[]{62,65}) {
            LocalDate birthday = birth.plusYears(threshold);
            if (birthday.getMonthValue()==1 && birthday.getDayOfMonth()==1
                    && birthday.equals(ageDate.plusDays(1)))
                throw new IllegalArgumentException("Georgia January 1 threshold birthday requires separately verified age treatment; no exclusion is guessed.");
        }
        if (!birth.plusYears(65).isAfter(ageDate)) return new BigDecimal(year==2026 ? "65000" : "70000");
        return !birth.plusYears(62).isAfter(ageDate) ? new BigDecimal("35000") : BigDecimal.ZERO;
    }
    private static void requireYear(int year) {
        if (year<2026) throw new IllegalArgumentException("Georgia rules begin in 2026.");
    }
}
