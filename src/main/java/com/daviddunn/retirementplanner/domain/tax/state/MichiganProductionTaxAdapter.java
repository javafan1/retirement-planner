package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxCalculator;
import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxContext;

/** Preserves the independently tested Phase 6C3 arithmetic and conservative eligibility policy. */
final class MichiganProductionTaxAdapter implements StateIncomeTaxCalculator {
    private final MichiganTaxCalculator calculator = new MichiganTaxCalculator();

    @Override
    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        var facts = request.household();
        var context = new MichiganTaxContext(facts.distributionDate(), facts.birthDates(),
                facts.householdOwners(), facts.activeOwners(), facts.pensionIncome());
        return ProductionStateTaxResult.michigan(calculator.calculate(request.income(), request.filingStatus(),
                request.projectedRules().getMichiganTaxRules(), context));
    }
}
