package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Run-only diagnostic propagated through tax, funding and Roth proof results. */
public record ArkansasTaxIssue(Kind kind, Set<AccountOwnership> reviewRecipients,
                              Optional<LowIncomeEvidence> lowIncomeEvidence) {
    public enum Kind {
        PENSION_ORIGIN, RETIREMENT_SOURCE, RECEIPT_TIMING, ADDITIONAL_TAX, LOW_INCOME,
        BASIS, NIIT, LIFE_PRINCIPAL_EVIDENCE, POLICY, FUNDING_BUDGET, FUNDING_PROOF, TARGET_BUDGET, TARGET_PROOF, SOURCE_OR_TAX
    }

    public enum Bound { PASS, FAIL, NOT_EVALUATED }
    public enum Withdrawal { ZERO, POSITIVE }
    public enum FederalCash { BELOW_FEDERAL_TAX, COVERS_FEDERAL_TAX }

    public record ExactFundingProbe(Withdrawal withdrawal, FederalCash cash) { }

    public record LowIncomeEvidence(Bound section301Gross, Bound section501Net,
                                    Optional<ExactFundingProbe> exactFundingProbe) { }

    public ArkansasTaxIssue(Kind kind, Set<AccountOwnership> reviewRecipients) {
        this(kind, reviewRecipients, Optional.empty());
    }

    public ArkansasTaxIssue {
        Objects.requireNonNull(kind);
        reviewRecipients = Set.copyOf(reviewRecipients);
        Objects.requireNonNull(lowIncomeEvidence);
        if ((kind == Kind.PENSION_ORIGIN) != !reviewRecipients.isEmpty()
                || reviewRecipients.contains(AccountOwnership.JOINT)) {
            throw new IllegalArgumentException("Invalid Arkansas pension review recipients.");
        }
        if (kind != Kind.LOW_INCOME && lowIncomeEvidence.isPresent()) {
            throw new IllegalArgumentException("Arkansas low-income evidence requires a low-income issue.");
        }
    }

    public static ArkansasTaxIssue of(Kind kind) {
        return new ArkansasTaxIssue(kind, Set.of());
    }

    static ArkansasTaxIssue lowIncome(Bound gross, Bound net) {
        return new ArkansasTaxIssue(Kind.LOW_INCOME, Set.of(),
                Optional.of(new LowIncomeEvidence(gross, net, Optional.empty())));
    }

    public ArkansasTaxIssue withExactFundingProbe(ExactFundingProbe probe) {
        var evidence = lowIncomeEvidence.orElseThrow();
        return new ArkansasTaxIssue(kind, reviewRecipients, Optional.of(new LowIncomeEvidence(
                evidence.section301Gross(), evidence.section501Net(), Optional.of(probe))));
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
