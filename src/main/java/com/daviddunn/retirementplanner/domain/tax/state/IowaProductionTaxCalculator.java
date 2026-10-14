package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxBaseBreakdown;
import com.daviddunn.retirementplanner.domain.tax.state.iowa.IowaLowIncomeReliefCalculator;
import com.daviddunn.retirementplanner.domain.tax.state.iowa.IowaPersonalCreditCalculator;
import com.daviddunn.retirementplanner.domain.tax.state.iowa.IowaTaxBaseBridge;
import com.daviddunn.retirementplanner.domain.tax.state.iowa.IowaTaxRules;
import com.daviddunn.retirementplanner.persistence.IowaTaxRulesRepository;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bounded disclosed full-year resident planning estimate, never a complete Form 1040 reconstruction. */
public final class IowaProductionTaxCalculator implements StateIncomeTaxCalculator {
    private final IowaTaxRules rules;
    private final IowaTaxBaseBridge bridge = new IowaTaxBaseBridge();
    private final IowaPersonalCreditCalculator credits = new IowaPersonalCreditCalculator();
    private final IowaLowIncomeReliefCalculator relief = new IowaLowIncomeReliefCalculator();

    public IowaProductionTaxCalculator() {
        this(new IowaTaxRulesRepository().load());
    }

    public IowaProductionTaxCalculator(IowaTaxRules rules) {
        this.rules = Objects.requireNonNull(rules);
    }

    @Override
    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        Objects.requireNonNull(request);
        if (request.assumptions().jurisdiction() != StateTaxJurisdiction.IA) {
            throw new IllegalArgumentException("Iowa calculator requires Iowa residency.");
        }
        if (!rules.supportsYear(request.taxYear())
                || (request.filingStatus() != FilingStatus.SINGLE
                && request.filingStatus() != FilingStatus.MARRIED_FILING_JOINTLY)) {
            return unsupported(request, List.of(StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE),
                    "Iowa supports Single/MFJ for 2026 and later using nominal validated 2026 parameters.");
        }
        FederalTaxBaseBreakdown observed;
        try {
            observed = request.federal().getModeledBaseBreakdown();
        } catch (IllegalArgumentException exception) {
            return unsupported(request, List.of(StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE),
                    "The federal result is incompatible with MODELED_STANDARD_DEDUCTION_ONLY_V1; it has not been repaired.");
        }
        var baseAdmission = bridge.reconcile(request, observed, rules);
        var creditAdmission = credits.calculate(request.taxYear(), request.filingStatus(), request.household(), rules);
        var reasons = new LinkedHashSet<>(baseAdmission.reasons());
        reasons.addAll(creditAdmission.reasons());
        if (!reasons.isEmpty()) {
            var explanations = new ArrayList<>(baseAdmission.diagnostics());
            explanations.addAll(creditAdmission.diagnostics());
            return unsupported(request, List.copyOf(reasons), String.join(" ", explanations));
        }
        var base = baseAdmission.amounts().orElseThrow();
        var allowance = creditAdmission.allowance().orElseThrow();
        var result = relief.calculate(new IowaLowIncomeReliefCalculator.Inputs(request.filingStatus(),
                base.nonnegativeIowaTaxableIncome().multiply(rules.rate()), base.reliefIncome(),
                allowance.seniorReliefEligible(), allowance.personalCredit().add(allowance.ageCredit())), rules);
        var limitations = new ArrayList<>(planningLimitations(request.taxYear(), request.taxYear()));
        limitations.add("Coverage: " + observed.coverage() + "; modeled AGI " + base.modeledAgi().toPlainString()
                + "; modeled deduction " + base.modeledDeduction().toPlainString()
                + "; signed Iowa base " + base.signedIowaBase().toPlainString()
                + "; Iowa taxable income " + base.nonnegativeIowaTaxableIncome().toPlainString()
                + "; relief income " + base.reliefIncome().toPlainString()
                + "; threshold " + result.threshold().toPlainString()
                + "; nominal credit " + result.nominalCredit().toPlainString()
                + "; applied credit " + result.creditApplied().toPlainString() + ".");
        return new ProductionStateTaxResult(StateTaxJurisdiction.IA, request.assumptions().calculationMethod(),
                StateTaxCalculationStatus.CALCULATED, result.finalRawTax().setScale(2, RoundingMode.HALF_UP),
                limitations, Optional.empty());
    }

    /** Iowa-only presentation metadata for frozen report assumptions; performs no tax calculation. */
    public List<String> planningLimitations(int firstYear, int lastYear) {
        if (lastYear < firstYear) throw new IllegalArgumentException("Invalid Iowa disclosure year range.");
        var notes = new ArrayList<String>();
        notes.add("Uses the planner's modeled federal AGI and projected standard deduction, not a complete Form 1040 taxable base. "
                + "Federal age additions, enhanced senior deductions, itemization and other unmodeled deductions are omitted; "
                + "their legal amounts are not assumed to be zero. Iowa insurance and special adjustments and ancillary credits are outside this estimate.");
        notes.add("Full-year Iowa resident; state personal income tax only. Uses validated nominal 2026 Iowa parameters "
                + "for 2026 and later projection years; future legislation is not predicted. Rule version: " + rules.version() + ".");
        if (firstYear < 2026) notes.add("Iowa years before 2026 are unsupported.");
        if (firstYear <= 2028 && lastYear >= 2026) notes.add(
                "The federal enhanced senior deduction may legally apply in 2026–2028 but is omitted from this modeled base.");
        if (lastYear >= 2029) notes.add(
                "The reviewed temporary federal enhanced senior deduction expired after 2028; future legislative changes are unknown. No temporary deduction is modeled.");
        return List.copyOf(notes);
    }

    private static ProductionStateTaxResult unsupported(StateTaxCalculationRequest request,
            List<StateTaxUnavailableReason> reasons, String explanation) {
        return ProductionStateTaxResult.unsupported(StateTaxJurisdiction.IA,
                request.assumptions().calculationMethod(), reasons, explanation);
    }
}
