package com.daviddunn.retirementplanner.domain.tax.state.michigan;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.MichiganTaxRules;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Michigan planning estimate: taxable base and qualifying subtraction are distinct. */
public final class MichiganTaxCalculator {
    private final MichiganRetirementEligibility eligibility = new MichiganRetirementEligibility();
    private final MichiganRetirementDeductionCalculator deductionCalculator = new MichiganRetirementDeductionCalculator();
    private final MichiganTaxableIncomeCalculator taxableIncomeCalculator = new MichiganTaxableIncomeCalculator();
    private final MichiganIncomeTaxCalculator incomeTaxCalculator = new MichiganIncomeTaxCalculator();

    /**
     * Compatibility boundary: an aggregate establishes neither qualification nor
     * household exemptions. Production callers must supply the attributed context.
     */
    @Deprecated
    public MichiganTaxCalculation calculate(BigDecimal retirementIncome, FilingStatus status, MichiganTaxRules rules) {
        return calculate(new TaxIncome(BigDecimal.ZERO, BigDecimal.ZERO, retirementIncome,
                BigDecimal.ZERO, BigDecimal.ZERO), status, rules,
                new MichiganTaxContext(LocalDate.MIN, Map.of(), Set.of(), Set.of(), Optional.empty()));
    }

    public MichiganTaxCalculation calculate(
            TaxIncome income,
            FilingStatus status,
            MichiganTaxRules rules,
            MichiganTaxContext context) {
        Objects.requireNonNull(income);
        Objects.requireNonNull(status);
        Objects.requireNonNull(rules);
        Objects.requireNonNull(context);
        int returnPeople = switch (status) {
            case SINGLE -> Math.min(1, context.householdOwners().size());
            case MARRIED_FILING_JOINTLY -> Math.min(2, context.householdOwners().size());
            default -> throw new UnsupportedOperationException("Michigan filing status is not modeled: " + status);
        };
        BigDecimal ordinary = income.getOrdinaryIncomeBeforeSocialSecurity();
        BigDecimal retirement = income.getMichiganRetirementIncome();
        List<IncomeSourceAttribution> sources = income.getIncomeSources();
        if (context.pensionIncome().isPresent()) {
            var pensions = context.pensionIncome().get();
            ordinary = ordinary.subtract(income.getPensionIncome()).add(pensions.total());
            retirement = retirement.subtract(income.getPensionIncome()).add(pensions.total());
            sources = new ArrayList<>(sources.stream()
                    .filter(source -> source.kind() != IncomeSourceAttribution.Kind.PENSION).toList());
            sources.addAll(pensions.sources());
        }
        List<MichiganRetirementEligibility.Decision> decisions = new ArrayList<>();
        BigDecimal eligible = BigDecimal.ZERO;
        BigDecimal attributed = BigDecimal.ZERO;
        for (IncomeSourceAttribution source : sources) {
            if (!source.isRetirementDistribution() && source.kind() != IncomeSourceAttribution.Kind.PENSION) {
                continue;
            }
            var decision = eligibility.assess(source, context);
            decisions.add(decision);
            eligible = eligible.add(decision.eligibleAmount());
            attributed = attributed.add(source.modeledTaxableAmount().orElse(BigDecimal.ZERO));
        }
        if (attributed.compareTo(retirement) > 0) {
            throw new IllegalArgumentException("Michigan attributed retirement amounts exceed the taxable retirement total.");
        }
        if (attributed.compareTo(retirement) < 0) {
            var remainder = retirement.subtract(attributed);
            var unknown = IncomeSourceAttribution.aggregate(IncomeSourceAttribution.Kind.ORDINARY_RETIREMENT_WITHDRAWAL,
                    IncomeSourceAttribution.Purpose.INCOME, "Unattributed taxable retirement income", remainder,
                    Optional.of(remainder));
            decisions.add(eligibility.assess(unknown, context));
        }
        BigDecimal deduction = deductionCalculator.calculate(eligible, status, rules);
        BigDecimal exemption = rules.getPersonalExemption().multiply(BigDecimal.valueOf(returnPeople));
        BigDecimal taxable = taxableIncomeCalculator.calculateTaxableIncome(ordinary, deduction, exemption);
        List<String> limitations = new ArrayList<>();
        if (context.householdOwners().isEmpty()) {
            limitations.add("HOUSEHOLD_EXEMPTION_CONTEXT_UNAVAILABLE");
        }
        if (context.birthDates().values().stream().anyMatch(birth ->
                !birth.plusYears(67).isAfter(context.distributionDate()))) {
            limitations.add("AGE_67_AND_OTHER_SPECIAL_DEDUCTION_ELECTIONS_NOT_MODELED");
        }
        return new MichiganTaxCalculation(retirement, deduction, taxable, incomeTaxCalculator.calculate(taxable, rules),
                ordinary, exemption, eligible, decisions, limitations);
    }
}
