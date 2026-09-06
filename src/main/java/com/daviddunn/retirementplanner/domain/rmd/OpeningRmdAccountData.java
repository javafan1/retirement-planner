package com.daviddunn.retirementplanner.domain.rmd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Historical data needed only to calculate an RMD in the
 * first calendar year of a projection.
 */
public final class OpeningRmdAccountData {

    private final int distributionYear;
    private final BigDecimal priorDecember31Balance;
    private final BigDecimal rmdAlreadyDistributedBeforeProjection;

    @JsonCreator
    public OpeningRmdAccountData(
            @JsonProperty("distributionYear") int distributionYear,
            @JsonProperty("priorDecember31Balance")
            BigDecimal priorDecember31Balance,
            @JsonProperty("rmdAlreadyDistributedBeforeProjection")
            BigDecimal rmdAlreadyDistributedBeforeProjection) {

        if (distributionYear <= 0) {
            throw new IllegalArgumentException(
                    "RMD distribution year must be positive.");
        }

        this.distributionYear = distributionYear;
        this.priorDecember31Balance = requireNonNegative(
                priorDecember31Balance,
                "Prior December 31 balance");
        this.rmdAlreadyDistributedBeforeProjection = requireNonNegative(
                rmdAlreadyDistributedBeforeProjection,
                "RMD already distributed before projection");
    }

    public int getDistributionYear() {
        return distributionYear;
    }

    public BigDecimal getPriorDecember31Balance() {
        return priorDecember31Balance;
    }

    public BigDecimal getRmdAlreadyDistributedBeforeProjection() {
        return rmdAlreadyDistributedBeforeProjection;
    }

    private static BigDecimal requireNonNegative(
            BigDecimal amount,
            String description) {

        Objects.requireNonNull(amount, description + " is required.");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    description + " cannot be negative.");
        }

        return amount;
    }
}
