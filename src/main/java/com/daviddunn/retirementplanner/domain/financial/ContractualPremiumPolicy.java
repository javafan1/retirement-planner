package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import java.util.Objects;

/** Closed, immutable planning policy. No policy assets, billing ledger or benefit linkage. */
public record ContractualPremiumPolicy(Model model, Insured insured) {
    public enum Model { INSURED_ALIVE_FIXED_NOMINAL_MONTHS_V1 }
    public enum Insured { PRIMARY, SPOUSE }

    public ContractualPremiumPolicy {
        Objects.requireNonNull(model, "Premium policy model is required.");
        Objects.requireNonNull(insured, "Insured person is required.");
    }

    public AccountOwnership person() { return AccountOwnership.valueOf(insured.name()); }
}
