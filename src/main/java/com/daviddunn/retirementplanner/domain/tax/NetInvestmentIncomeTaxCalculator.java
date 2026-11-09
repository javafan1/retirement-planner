package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** All admitted recognized investment sources; never an AGI-percentage approximation. */
public final class NetInvestmentIncomeTaxCalculator {
    public NetInvestmentIncomeTaxCalculation calculate(TaxIncome income, FilingStatus status, BigDecimal magi) {
        BigDecimal annuities = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;
        BigDecimal distributions = BigDecimal.ZERO;
        BigDecimal conversions = BigDecimal.ZERO;
        if (income.getPensionIncome().signum() != 0)
            throw new NiitUnsupportedInputException("Pension qualified-plan facts are unavailable.");
        for (var source : income.getIncomeSources()) {
            if (source.grossAmount().signum() == 0) continue;
            switch (source.kind()) {
                case NONQUALIFIED_ANNUITY -> annuities = annuities.add(source.modeledTaxableAmount().orElseThrow());
                case INTEREST -> interest = interest.add(source.modeledTaxableAmount().orElseThrow(
                        () -> new NiitUnsupportedInputException("Taxable personal interest amount is unknown.")));
                case SOCIAL_SECURITY, NIIT_EVIDENCE, FINANCIAL_CALCULATION_POLICY -> { }
                case CONDITIONAL_RECURRING, CONDITIONAL_LUMP_SUM -> {
                    if (source.modeledTaxableAmount().orElseThrow().signum() != 0)
                        throw new NiitUnsupportedInputException("Other conditional income lacks an investment-income classification.");
                }
                case ORDINARY_RETIREMENT_WITHDRAWAL, RMD, OPENING_RMD, ROTH_CONVERSION -> {
                    if (source.account().isEmpty() || !source.account().get().type().requiresIndividualOwnership())
                        throw new NiitUnsupportedInputException("Retirement distribution lacks qualified account evidence.");
                    var taxable = source.modeledTaxableAmount().orElseThrow(() -> new NiitUnsupportedInputException("Retirement taxable amount is unknown."));
                    if (source.kind() == com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Kind.ROTH_CONVERSION)
                        conversions = conversions.add(taxable);
                    else distributions = distributions.add(taxable);
                }
                case ORDINARY_WITHDRAWAL -> {
                    if (source.modeledTaxableAmount().isEmpty() || source.modeledTaxableAmount().get().signum() != 0)
                        throw new NiitUnsupportedInputException("Nonqualified withdrawal has no supported recognized-income/basis split.");
                }
                case PENSION, DIVIDEND, CAPITAL_GAIN, OTHER -> throw new NiitUnsupportedInputException(
                        "Source " + source.kind() + " lacks supported federal NIIT qualification/recognition facts.");
            }
        }
        if (interest.compareTo(income.getTaxableInterestIncome()) != 0)
            throw new NiitUnsupportedInputException("Taxable interest is not completely attributed.");
        if (distributions.compareTo(income.getTaxDeferredWithdrawals()) != 0
                || conversions.compareTo(income.getRothConversion()) != 0
                || annuities.compareTo(income.getConditionalOrdinaryIncome()) != 0)
            throw new NiitUnsupportedInputException("Modeled taxable income is not completely classified for NIIT.");
        return calculate(status, magi, annuities, interest);
    }
    public NetInvestmentIncomeTaxCalculation calculate(FilingStatus status, BigDecimal magi,
            BigDecimal taxableAnnuities, BigDecimal taxableInterest) {
        for (var amount : new BigDecimal[]{magi,taxableAnnuities,taxableInterest}) {
            if (amount == null || amount.signum() < 0 || amount.precision() > 4096 || amount.scale() < -1024 || amount.scale() > 4096)
                throw new IllegalArgumentException("NIIT amount exceeds supported numeric bounds.");
        }
        var threshold = NetInvestmentIncomeTaxRules.threshold(status);
        var rate = NetInvestmentIncomeTaxRules.rate();
        var tax = taxableAnnuities.add(taxableInterest).min(magi.subtract(threshold).max(BigDecimal.ZERO))
                .multiply(rate).setScale(2, RoundingMode.HALF_UP);
        return new NetInvestmentIncomeTaxCalculation(NetInvestmentIncomeTaxCalculation.MODEL, status, magi,
                taxableAnnuities, taxableInterest, BigDecimal.ZERO, BigDecimal.ZERO, threshold, rate, tax);
    }
}
