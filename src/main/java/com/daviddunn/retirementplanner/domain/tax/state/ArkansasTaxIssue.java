package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import java.util.Objects;
import java.util.Set;

/** Run-only diagnostic propagated through tax, funding and Roth proof results. */
public record ArkansasTaxIssue(Kind kind, Set<AccountOwnership> reviewRecipients) {
    public enum Kind {
        PENSION_ORIGIN, RETIREMENT_SOURCE, RECEIPT_TIMING, ADDITIONAL_TAX, LOW_INCOME,
        BASIS, NIIT, POLICY, FUNDING_BUDGET, FUNDING_PROOF, TARGET_BUDGET, TARGET_PROOF, SOURCE_OR_TAX
    }

    public ArkansasTaxIssue {
        Objects.requireNonNull(kind);
        reviewRecipients = Set.copyOf(reviewRecipients);
        if ((kind == Kind.PENSION_ORIGIN) != !reviewRecipients.isEmpty()
                || reviewRecipients.contains(AccountOwnership.JOINT)) {
            throw new IllegalArgumentException("Invalid Arkansas pension review recipients.");
        }
    }

    public static ArkansasTaxIssue of(Kind kind) {
        return new ArkansasTaxIssue(kind, Set.of());
    }

    static ArkansasTaxIssue fromSourceReason(StateTaxUnavailableReason reason) {
        return of(switch (reason) {
            case MISSING_DISTRIBUTION_BASIS, NONQUALIFIED_ROTH_BASIS_REQUIRED -> Kind.BASIS;
            case MISSING_RETIREMENT_QUALIFICATION, INHERITED_DISTRIBUTION_HISTORY_REQUIRED,
                    UNSUPPORTED_TRANSACTION_TYPE -> Kind.RETIREMENT_SOURCE;
            case UNSUPPORTED_MODEL_RULE -> Kind.SOURCE_OR_TAX;
        });
    }
}
