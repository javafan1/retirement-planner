package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions.CalculationMethod;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import java.util.Map;
import java.util.Objects;

/** The single production routing boundary; the eight-state research catalog is not an activation registry. */
public final class ProductionStateTaxService {
    private final Map<StateTaxJurisdiction, StateIncomeTaxCalculator> calculators;

    public ProductionStateTaxService() {
        this(defaultCalculators());
    }

    private static Map<StateTaxJurisdiction, StateIncomeTaxCalculator> defaultCalculators() {
        var registry = new java.util.EnumMap<StateTaxJurisdiction, StateIncomeTaxCalculator>(StateTaxJurisdiction.class);
        registry.put(StateTaxJurisdiction.MI, new MichiganProductionTaxAdapter());
        registry.put(StateTaxJurisdiction.IL, new IllinoisProductionTaxCalculator());
        registry.put(StateTaxJurisdiction.PA, new PennsylvaniaProductionTaxCalculator());
        registry.put(StateTaxJurisdiction.IA, new IowaProductionTaxCalculator());
        var zeroTax = new NoGeneralIndividualIncomeTaxCalculator();
        for (var jurisdiction : StateTaxJurisdiction.values()) {
            if (jurisdiction.usesNoGeneralIndividualIncomeTaxModel()) {
                registry.put(jurisdiction, zeroTax);
            }
        }
        return registry;
    }

    /** Immutable calculator configuration, useful for bounded evaluation and contract tests. */
    public ProductionStateTaxService(Map<StateTaxJurisdiction, StateIncomeTaxCalculator> calculators) {
        this.calculators = Map.copyOf(calculators);
    }

    public ProductionStateTaxResult calculate(StateTaxCalculationRequest request) {
        Objects.requireNonNull(request);
        if (request.assumptions().calculationMethod() != CalculationMethod.SIMPLIFIED_STATE_ESTIMATE) {
            throw new IllegalArgumentException("Unsupported production state tax calculation method.");
        }
        var calculator = calculators.get(request.assumptions().jurisdiction());
        if (calculator == null) {
            throw new IllegalArgumentException("Unsupported production state tax jurisdiction.");
        }
        var result = Objects.requireNonNull(calculator.calculate(request));
        if (result.jurisdiction() != request.assumptions().jurisdiction()
                || result.calculationMethod() != request.assumptions().calculationMethod()) {
            throw new IllegalStateException("State calculator returned inconsistent routing metadata.");
        }
        return result;
    }
}
