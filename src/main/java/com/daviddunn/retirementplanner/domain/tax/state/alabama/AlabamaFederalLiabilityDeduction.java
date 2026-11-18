package com.daviddunn.retirementplanner.domain.tax.state.alabama;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.FederalIncomeTaxCalculator;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxCalculationRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Eligible uncontested annual liability for AL only. Never changes modeled federal cash tax. */
public final class AlabamaFederalLiabilityDeduction {
    public record Worksheet(BigDecimal modeledCashTax, BigDecimal eligibleLiability,
            BigDecimal additionalAgeDeduction, BigDecimal enhancedSeniorDeduction, BigDecimal niit) { }

    private final AlabamaTaxRules rules = new AlabamaTaxRules();
    private final FederalIncomeTaxCalculator federalArithmetic = new FederalIncomeTaxCalculator();

    public Worksheet calculate(StateTaxCalculationRequest request) {
        Objects.requireNonNull(request);
        AlabamaTaxRules.requireYear(request.taxYear());
        var federal = request.federal();
        var base = federal.getModeledBaseBreakdown();
        var niit = federal.niit().map(value -> value.tax()).orElse(BigDecimal.ZERO);
        var modeledRegular = federalArithmetic.calculateTax(base.modeledClampedTaxableIncome(),
                request.filingStatus(), request.projectedRules());
        if (modeledRegular.add(niit).compareTo(federal.getFederalIncomeTax()) != 0
                || federal.getStandardDeduction().compareTo(request.projectedRules()
                        .getFederalTaxRules(request.filingStatus()).getStandardDeduction()) != 0)
            throw new IllegalArgumentException("Federal liability contains unexplained credits, deductions or special tax outside Alabama ordinary coverage.");
        var filers = request.filingStatus() == FilingStatus.SINGLE
                ? request.household().activeOwners() : request.household().householdOwners();
        int seniors = (int) filers.stream().filter(owner -> rules.federalSenior(request.taxYear(), request.household(), owner)).count();
        var additional = rules.additionalFederalAgeDeduction(request.taxYear(), request.filingStatus())
                .multiply(BigDecimal.valueOf(seniors));
        var enhanced = rules.enhancedSeniorDeduction(request.taxYear(), request.filingStatus(), federal.getAdjustedGrossIncome(), seniors);
        var ordinaryTaxable = base.derivedRawTaxableIncome().subtract(additional).subtract(enhanced).max(BigDecimal.ZERO);
        var eligible = fromWorksheet(federalArithmetic.calculateTax(ordinaryTaxable,
                request.filingStatus(), request.projectedRules()), niit, BigDecimal.ZERO);
        return new Worksheet(federal.getFederalIncomeTax(), eligible, additional, enhanced, niit);
    }

    /** Form40 worksheet: regular tax after known nonrefundable credits + NIIT − enumerated refundable credits. */
    public BigDecimal fromWorksheet(BigDecimal regularAfterCredits, BigDecimal niit, BigDecimal refundableCredits) {
        if (Objects.requireNonNull(regularAfterCredits).signum() < 0 || Objects.requireNonNull(niit).signum() < 0
                || Objects.requireNonNull(refundableCredits).signum() < 0)
            throw new IllegalArgumentException("Federal worksheet components must be nonnegative.");
        var exact = regularAfterCredits.add(niit).subtract(refundableCredits).max(BigDecimal.ZERO).stripTrailingZeros();
        return exact.setScale(Math.max(2, exact.scale()));
    }
}
