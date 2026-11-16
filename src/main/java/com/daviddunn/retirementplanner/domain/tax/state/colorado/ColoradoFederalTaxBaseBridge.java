package com.daviddunn.retirementplanner.domain.tax.state.colorado;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Nonproduction CO worksheet bridge; never changes the supplied federal result.
 * Ordinary domestic nondependent standard-deduction filers with valid SSNs only;
 * no blindness, foreign MAGI adjustments, itemization or additional deductions.
 * 2026 IRS RP 2025-32; IRC 151 senior phaseout; DOR high-income deduction addback.
 */
public final class ColoradoFederalTaxBaseBridge {
    public record Base(BigDecimal basicDeduction, BigDecimal ageDeduction, BigDecimal enhancedSeniorDeduction,
            BigDecimal signedFederalBase, BigDecimal standardDeductionAddback,
            BigDecimal beforeStateRetirementSubtractions) { }

    public Base calculate(int year, FilingStatus status, TaxHouseholdContext household, FederalTaxCalculation federal) {
        ColoradoTaxRules.requireVerifiedYear(year);
        Objects.requireNonNull(federal).getModeledBaseBreakdown();
        var filers = filers(year, status, household);
        BigDecimal basic = new BigDecimal(status == FilingStatus.SINGLE ? "16100" : "32200");
        if (federal.getStandardDeduction().compareTo(basic) != 0)
            throw new IllegalArgumentException("Colorado bridge requires the verified 2026 modeled basic deduction.");
        int seniors = 0;
        for (var filer : filers) {
            LocalDate close = household.activeOwners().contains(filer) ? LocalDate.of(year, 12, 31) : LocalDate.of(year, 1, 1);
            // IRS Pub 501: federal age 65 is attained the day before the birthday,
            // including a final return. Do not reuse a state pension-age test.
            if (!household.birthDates().get(filer).plusYears(65).minusDays(1).isAfter(close)) seniors++;
        }
        BigDecimal age = new BigDecimal(status == FilingStatus.SINGLE ? "2050" : "1650").multiply(BigDecimal.valueOf(seniors));
        BigDecimal threshold = new BigDecimal(status == FilingStatus.SINGLE ? "75000" : "150000");
        BigDecimal enhanced = new BigDecimal("6000").subtract(federal.getAdjustedGrossIncome().subtract(threshold)
                .max(BigDecimal.ZERO).multiply(new BigDecimal("0.06"))).max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(seniors));
        BigDecimal signed = federal.getAdjustedGrossIncome().subtract(basic).subtract(age).subtract(enhanced);
        BigDecimal addback = federal.getAdjustedGrossIncome().compareTo(new BigDecimal("300000")) > 0
                ? basic.add(age).subtract(new BigDecimal(status == FilingStatus.SINGLE ? "1000" : "2000")).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        return new Base(basic, age, enhanced, signed, addback, signed.add(addback).max(BigDecimal.ZERO));
    }

    static Set<AccountOwnership> filers(int year, FilingStatus status, TaxHouseholdContext household) {
        Objects.requireNonNull(household);
        if (household.distributionDate().getYear() != year
                || status != FilingStatus.SINGLE && status != FilingStatus.MARRIED_FILING_JOINTLY)
            throw new IllegalArgumentException("Colorado worksheet requires matching year and Single/MFJ.");
        var filers = status == FilingStatus.SINGLE ? household.activeOwners() : household.householdOwners();
        if (filers.size() != (status == FilingStatus.SINGLE ? 1 : 2) || !household.birthDates().keySet().containsAll(filers))
            throw new IllegalArgumentException("Colorado worksheet requires complete filer birth/recipient facts.");
        return filers;
    }

    static int ageAtTaxClose(int year, TaxHouseholdContext household, AccountOwnership filer) {
        LocalDate ageDate = household.activeOwners().contains(filer) ? LocalDate.of(year, 12, 31) : LocalDate.of(year, 1, 1);
        return java.time.Period.between(household.birthDates().get(filer), ageDate).getYears();
    }
}
