package com.daviddunn.retirementplanner.domain.tax.state.iowa;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Single retention cap is after credits; MFJ alternate comparison is before credits. */
public final class IowaLowIncomeReliefCalculator {

    public record Inputs(
            FilingStatus filingStatus,
            BigDecimal regularTax,
            BigDecimal signedReliefIncome,
            boolean seniorReliefEligible,
            BigDecimal nominalNonrefundableCredit) {
        public Inputs {
            Objects.requireNonNull(filingStatus);
            Objects.requireNonNull(signedReliefIncome);
            nonnegative(regularTax);
            nonnegative(nominalNonrefundableCredit);
            if (filingStatus != FilingStatus.SINGLE && filingStatus != FilingStatus.MARRIED_FILING_JOINTLY) {
                throw new IllegalArgumentException("Unsupported Iowa relief filing status.");
            }
        }
    }

    public record Result(
            BigDecimal threshold,
            BigDecimal reliefIncome,
            BigDecimal excessIncome,
            boolean lowIncomeExempt,
            BigDecimal regularTax,
            Optional<BigDecimal> alternativeTax,
            BigDecimal selectedPreCreditTax,
            BigDecimal nominalCredit,
            BigDecimal creditApplied,
            Optional<BigDecimal> singlePostCreditReduction,
            BigDecimal finalRawTax) {
        public Result {
            nonnegative(threshold);
            Objects.requireNonNull(reliefIncome);
            nonnegative(excessIncome);
            nonnegative(regularTax);
            Objects.requireNonNull(alternativeTax).ifPresent(IowaLowIncomeReliefCalculator::nonnegative);
            nonnegative(selectedPreCreditTax);
            nonnegative(nominalCredit);
            nonnegative(creditApplied);
            Objects.requireNonNull(singlePostCreditReduction).ifPresent(IowaLowIncomeReliefCalculator::nonnegative);
            nonnegative(finalRawTax);
        }
    }

    public Result calculate(Inputs inputs, IowaTaxRules rules) {
        Objects.requireNonNull(inputs);
        Objects.requireNonNull(rules);
        var threshold = rules.threshold(inputs.filingStatus(), inputs.seniorReliefEligible());
        var excess = inputs.signedReliefIncome().subtract(threshold).max(BigDecimal.ZERO);
        boolean exempt = inputs.signedReliefIncome().compareTo(threshold) <= 0;
        boolean joint = inputs.filingStatus() == FilingStatus.MARRIED_FILING_JOINTLY;
        Optional<BigDecimal> alternative = joint
                ? Optional.of(excess.multiply(rules.alternativeRate())) : Optional.empty();
        var selected = exempt ? BigDecimal.ZERO
                : joint ? inputs.regularTax().min(alternative.orElseThrow()) : inputs.regularTax();
        var applied = selected.min(inputs.nominalNonrefundableCredit());
        var afterCredits = selected.subtract(applied);
        var finalRaw = joint ? afterCredits : afterCredits.min(excess);
        return new Result(threshold, inputs.signedReliefIncome(), excess, exempt,
                inputs.regularTax(), alternative, selected, inputs.nominalNonrefundableCredit(), applied,
                joint ? Optional.empty() : Optional.of(afterCredits.subtract(finalRaw)), finalRaw);
    }

    private static void nonnegative(BigDecimal amount) {
        if (Objects.requireNonNull(amount).signum() < 0) {
            throw new IllegalArgumentException("Iowa tax/credit cannot be negative.");
        }
    }
}
