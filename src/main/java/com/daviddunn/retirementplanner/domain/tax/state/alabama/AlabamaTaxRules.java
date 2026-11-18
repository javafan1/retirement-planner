package com.daviddunn.retirementplanner.domain.tax.state.alabama;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/** 40-18-5/15/19, 2026 HB341/HB527 and Schedule RS; reviewed 2026-11-17. */
public final class AlabamaTaxRules {
    public static final String VERSION = "AL-2026-1";
    public static final String REFERENCE_KEY = "alabama-income-tax-rules";
    public static final BigDecimal RETIREMENT_LIMIT = new BigDecimal("6000");

    public BigDecimal standardDeduction(BigDecimal alabamaAgi, FilingStatus status) {
        requireNonnegative(alabamaAgi);
        var steps = alabamaAgi.subtract(new BigDecimal("25500")).max(BigDecimal.ZERO)
                .divide(new BigDecimal("500"), 0, RoundingMode.DOWN);
        return new BigDecimal(joint(status) ? "8500" : "3000")
                .subtract(steps.multiply(new BigDecimal(joint(status) ? "175" : "25")))
                .max(new BigDecimal(joint(status) ? "5000" : "2500"));
    }

    public BigDecimal personalExemption(FilingStatus status) {
        return new BigDecimal(joint(status) ? "3000" : "1500");
    }

    public BigDecimal tax(BigDecimal taxableIncome, FilingStatus status) {
        requireNonnegative(taxableIncome);
        var factor = BigDecimal.valueOf(joint(status) ? 2 : 1);
        var first = new BigDecimal("500").multiply(factor);
        var upper = new BigDecimal("3000").multiply(factor);
        return taxableIncome.min(first).multiply(new BigDecimal("0.02"))
                .add(taxableIncome.subtract(first).max(BigDecimal.ZERO).min(upper.subtract(first))
                        .multiply(new BigDecimal("0.04")))
                .add(taxableIncome.subtract(upper).max(BigDecimal.ZERO).multiply(new BigDecimal("0.05")))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public LocalDate ageDate(int year, TaxHouseholdContext household, AccountOwnership owner) {
        return LocalDate.of(year, household.activeOwners().contains(owner) ? 12 : 1,
                household.activeOwners().contains(owner) ? 31 : 1);
    }

    public boolean senior(int year, TaxHouseholdContext household, AccountOwnership owner) {
        return !household.birthDates().get(owner).plusYears(65).isAfter(ageDate(year, household, owner));
    }

    public boolean ambiguousSenior(int year, TaxHouseholdContext household, AccountOwnership owner) {
        return household.birthDates().get(owner).plusYears(65).equals(ageDate(year, household, owner).plusDays(1));
    }

    /** Known federal day-before-birthday convention; separate from unresolved AL boundary. */
    public boolean federalSenior(int year, TaxHouseholdContext household, AccountOwnership owner) {
        return !household.birthDates().get(owner).plusYears(65).minusDays(1).isAfter(ageDate(year, household, owner));
    }

    /** Published 2026/2027 additional-age amounts; latest reviewed nominal amount thereafter. */
    public BigDecimal additionalFederalAgeDeduction(int year, FilingStatus status) {
        requireYear(year);
        return new BigDecimal(year == 2026 ? joint(status) ? "1650" : "2050" : joint(status) ? "1700" : "2100");
    }

    public BigDecimal enhancedSeniorDeduction(int year, FilingStatus status, BigDecimal agi, int seniors) {
        requireYear(year);
        requireNonnegative(agi);
        if (seniors < 0 || seniors > (joint(status) ? 2 : 1)) throw new IllegalArgumentException("Invalid senior count.");
        if (year > 2028) return BigDecimal.ZERO;
        return new BigDecimal("6000").subtract(agi.subtract(new BigDecimal(joint(status) ? "150000" : "75000"))
                .max(BigDecimal.ZERO).multiply(new BigDecimal("0.06"))).max(BigDecimal.ZERO)
                .multiply(BigDecimal.valueOf(seniors));
    }

    public static void requireYear(int year) {
        if (year < 2026) throw new IllegalArgumentException("Alabama reviewed rules start in 2026.");
    }

    private static void requireNonnegative(BigDecimal amount) {
        if (Objects.requireNonNull(amount).signum() < 0) throw new IllegalArgumentException("Negative Alabama worksheet amount.");
    }

    private static boolean joint(FilingStatus status) {
        if (Objects.requireNonNull(status) != FilingStatus.SINGLE && status != FilingStatus.MARRIED_FILING_JOINTLY)
            throw new IllegalArgumentException("Alabama supports Single/MFJ.");
        return status == FilingStatus.MARRIED_FILING_JOINTLY;
    }
}
