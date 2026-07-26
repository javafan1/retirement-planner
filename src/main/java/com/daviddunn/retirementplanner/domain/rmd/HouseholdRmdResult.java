package com.daviddunn.retirementplanner.domain.rmd;

import java.math.BigDecimal;
import java.util.Objects;
/*
HouseholdRmdResult
│
├── Primary
│   ├── IRA
│   ├── 401(k)s
│   ├── 403(b)s
│   └── Total
│
├── Spouse
│   ├── IRA
│   ├── 401(k)s
│   ├── 403(b)s
│   └── Total
│
└── Household Total
 */
public final class HouseholdRmdResult {

    private final OwnerRmdResult primaryRmd;
    private final OwnerRmdResult spouseRmd;

    public HouseholdRmdResult(
            OwnerRmdResult primaryRmd,
            OwnerRmdResult spouseRmd) {

        this.primaryRmd =
                Objects.requireNonNull(
                        primaryRmd,
                        "Primary RMD result is required.");

        this.spouseRmd =
                Objects.requireNonNull(
                        spouseRmd,
                        "Spouse RMD result is required.");
    }

    public OwnerRmdResult getPrimaryRmd() {
        return primaryRmd;
    }

    public OwnerRmdResult getSpouseRmd() {
        return spouseRmd;
    }

    public BigDecimal getTotalRmd() {

        return primaryRmd
                .getTotalRmd()
                .add(
                        spouseRmd
                                .getTotalRmd());
    }

    public static HouseholdRmdResult zero() {

        return new HouseholdRmdResult(
                OwnerRmdResult.zero(),
                OwnerRmdResult.zero());
    }

    @Override
    public String toString() {

        return "HouseholdRmdResult{" +
                "primaryRmd=" + primaryRmd +
                ", spouseRmd=" + spouseRmd +
                ", totalRmd=" + getTotalRmd() +
                '}';
    }
}