package com.daviddunn.retirementplanner.domain.tax.state.virginia;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.Objects;

/** Form760 age worksheet: reduce the combined tested pool once, not once per spouse. */
public final class VirginiaAgeDeduction {
    public record Result(BigDecimal deduction,BigDecimal exemptions) { }
    public Result calculate(int year,FilingStatus status,TaxHouseholdContext household,Set<AccountOwnership> filers,BigDecimal afagi) {
        Objects.requireNonNull(status);
        Objects.requireNonNull(household);
        Objects.requireNonNull(filers);
        Objects.requireNonNull(afagi);
        int tested=0,grandfathered=0,aged=0;
        for (var owner:filers) {
            var birth=household.birthDates().get(owner);
            var date=household.activeOwners().contains(owner) ? LocalDate.of(year,12,31) : LocalDate.of(year,1,1);
            // Federal/official VA convention: age is attained on the day before the birthday.
            if (birth.plusYears(65).minusDays(1).isAfter(date)) continue;
            aged++;
            if (!birth.isAfter(LocalDate.of(1939,1,1))) grandfathered++; else tested++;
        }
        var threshold=new BigDecimal(status==FilingStatus.SINGLE ? "50000" : "75000");
        var deduction=new BigDecimal("12000").multiply(BigDecimal.valueOf(tested))
                .subtract(afagi.subtract(threshold).max(BigDecimal.ZERO)).max(BigDecimal.ZERO)
                .add(new BigDecimal("12000").multiply(BigDecimal.valueOf(grandfathered)));
        var exemptions=new BigDecimal("930").multiply(BigDecimal.valueOf(filers.size()))
                .add(new BigDecimal("800").multiply(BigDecimal.valueOf(aged)));
        return new Result(deduction,exemptions);
    }
}
