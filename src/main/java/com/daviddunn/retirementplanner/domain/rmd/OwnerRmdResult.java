package com.daviddunn.retirementplanner.domain.rmd;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class OwnerRmdResult {

    private final BigDecimal iraRmd;
    private final List<AccountRmd> traditional401kRmds;
    private final List<AccountRmd> traditional403bRmds;

    public OwnerRmdResult(
            BigDecimal iraRmd,
            List<AccountRmd> traditional401kRmds,
            List<AccountRmd> traditional403bRmds) {

        this.iraRmd =
                Objects.requireNonNull(
                        iraRmd,
                        "IRA RMD is required.");

        this.traditional401kRmds =
                List.copyOf(
                        Objects.requireNonNull(
                                traditional401kRmds,
                                "401(k) RMDs are required."));

        this.traditional403bRmds =
                List.copyOf(
                        Objects.requireNonNull(
                                traditional403bRmds,
                                "403(b) RMDs are required."));

        if (iraRmd.signum() < 0) {
            throw new IllegalArgumentException(
                    "IRA RMD cannot be negative.");
        }
    }

    public BigDecimal getIraRmd() {
        return iraRmd;
    }

    public List<AccountRmd> getTraditional401kRmds() {
        return traditional401kRmds;
    }

    public List<AccountRmd> getTraditional403bRmds() {
        return traditional403bRmds;
    }

    public BigDecimal getTraditional401kRmdTotal() {

        return traditional401kRmds
                .stream()
                .map(AccountRmd::getAmount)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);
    }

    public BigDecimal getTraditional403bRmdTotal() {

        return traditional403bRmds
                .stream()
                .map(AccountRmd::getAmount)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);
    }

    public BigDecimal getTotalRmd() {

        return iraRmd
                .add(getTraditional401kRmdTotal())
                .add(getTraditional403bRmdTotal());
    }

    @Override
    public String toString() {

        return "OwnerRmdResult{" +
                "iraRmd=" + iraRmd +
                ", traditional401kRmds=" + traditional401kRmds +
                ", traditional403bRmds=" + traditional403bRmds +
                ", totalRmd=" + getTotalRmd() +
                '}';
    }
}