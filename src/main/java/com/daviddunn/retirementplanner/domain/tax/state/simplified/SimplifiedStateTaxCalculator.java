package com.daviddunn.retirementplanner.domain.tax.state.simplified;

import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRequest.*;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxResult.*;
import com.daviddunn.retirementplanner.domain.tax.state.simplified.StateTaxRules.*;
import com.daviddunn.retirementplanner.persistence.StateTaxDatasetRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Stateless prototype. No jurisdiction-specific rates, branches, UI or plan access. */
public final class SimplifiedStateTaxCalculator {

    private final Map<String, StateTaxRules> catalog;

    public SimplifiedStateTaxCalculator() throws IOException {
        catalog = new StateTaxDatasetRepository().load();
    }

    public StateTaxResult calculate(StateTaxRequest request) {
        Objects.requireNonNull(request);
        StateTaxRules rules = catalog.get(request.jurisdiction());
        if (rules == null) {
            return unsupported(request, "Unavailable", "Jurisdiction not present in supplied dataset");
        }
        if (request.taxYear() != rules.taxYear()) {
            return unsupported(request, rules.datasetVersion(), "Only tax year 2026 is available; no future-year policy");
        }
        if (!rules.standardDeduction().containsKey(request.filingStatus())) {
            return unsupported(request, rules.datasetVersion(), "Filing status not supported by dataset");
        }
        try {
            return calculate(request, rules);
        } catch (UnsupportedScenario exception) {
            List<String> warnings = new ArrayList<>(rules.limitations());
            warnings.add(exception.getMessage());
            return new StateTaxResult(request.jurisdiction(), request.taxYear(), request.filingStatus(),
                    rules.datasetVersion(), Status.UNSUPPORTED, Optional.empty(), warnings);
        }
    }

    private StateTaxResult calculate(StateTaxRequest request, StateTaxRules rules) {
        List<Component> components = new ArrayList<>();
        boolean stateDefined = rules.taxBase().equals("STATE_DEFINED_INCOME");
        if (stateDefined) {
            BigDecimal classifiedFederal = request.people().stream()
                    .flatMap(person -> person.income().stream()).map(Income::federalIncluded)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (classifiedFederal.compareTo(request.federalAgi()) != 0) {
                throw new UnsupportedScenario("State-defined base requires complete income classification; unclassified AGI cannot be omitted");
            }
        }
        for (PersonIncome person : request.people()) {
            for (Income income : person.income()) {
                BigDecimal amount = stateDefined ? income.stateDefinedAmount() : income.federalIncluded();
                if (stateDefined && rules.incomeTreatment().get(income.type()).equals("EXEMPT")) {
                    amount = BigDecimal.ZERO;
                }
                components.add(new Component(person, income, amount));
            }
        }
        BigDecimal starting = switch (rules.taxBase()) {
            case "NONE" -> BigDecimal.ZERO;
            case "FEDERAL_AGI_WITH_ADJUSTMENTS" -> request.federalAgi();
            case "FEDERAL_TAXABLE_INCOME_WITH_ADJUSTMENTS" -> request.federalTaxableIncome();
            case "STATE_DEFINED_INCOME" -> sum(components);
            default -> throw new UnsupportedScenario("Unsupported tax base");
        };
        List<Line> incomeAdjustments = new ArrayList<>();
        List<Line> exclusions = new ArrayList<>();
        List<Line> seniors = new ArrayList<>();
        BigDecimal current = starting;
        checkBoundaries(request, rules, "BEFORE_RETIREMENT", starting, current);
        Map<String, BigDecimal> consumedLimits = new HashMap<>();
        if (!rules.system().equals("NONE")) {
            for (Component component : components) {
                if (rules.incomeTreatment().get(component.income.type()).equals("EXEMPT")) {
                    if (component.remaining.signum() > 0) {
                        incomeAdjustments.add(new Line(component.person.owner() + " " + component.income.type(),
                                component.remaining));
                        current = current.subtract(component.remaining);
                        component.remaining = BigDecimal.ZERO;
                    }
                }
                if (stateDefined && component.income.type() == IncomeType.ROTH_CONVERSION
                        && component.remaining.signum() > 0
                        && rules.boundaries().stream().noneMatch(boundary -> boundary.trigger().equals("UNASSESSED_QUALIFICATION")
                        && boundary.incomeTypes().contains(IncomeType.ROTH_CONVERSION))) {
                    throw new UnsupportedScenario("State-defined Roth conversion basis/treatment requires separate review");
                }
            }
            checkSharedGroups(rules, components);
            List<Adjustment> ordered = rules.adjustments().stream()
                    .sorted(Comparator.comparingInt(Adjustment::priority)).toList();
            for (Adjustment adjustment : ordered) {
                if (adjustment.scope().equals("PER_PERSON")) {
                    for (PersonIncome person : request.people()) {
                        BigDecimal excluded = exclude(adjustment, components.stream()
                                .filter(component -> component.person == person).toList(), request, starting, stateDefined,
                                consumedLimits, person.owner().name());
                        if (excluded.signum() > 0) {
                            exclusions.add(new Line(person.owner() + " " + adjustment.id(), excluded));
                            current = current.subtract(excluded);
                        }
                    }
                } else {
                    BigDecimal excluded = exclude(adjustment, components, request, starting, stateDefined,
                            consumedLimits, "HOUSEHOLD");
                    if (excluded.signum() > 0) {
                        exclusions.add(new Line(adjustment.id(), excluded));
                        current = current.subtract(excluded);
                    }
                }
            }
        }
        checkBoundaries(request, rules, "AFTER_RETIREMENT", starting, current);
        boolean belowCeiling = rules.exemptionCeiling().isEmpty()
                || request.federalAgi().compareTo(rules.exemptionCeiling().get(request.filingStatus())) <= 0;
        for (Senior senior : rules.seniors()) {
            if (!senior.subjectToCeiling() || belowCeiling) {
                for (PersonIncome person : request.people()) {
                    if (person.age().compareTo(senior.minimumAge()) >= 0) {
                        seniors.add(new Line(person.owner() + " " + senior.id(), senior.amount()));
                        current = current.subtract(senior.amount());
                    }
                }
            }
        }
        BigDecimal standard = rules.standardDeduction().get(request.filingStatus());
        BigDecimal personal = belowCeiling && rules.exemptionType().equals("DEDUCTION")
                ? rules.exemptions().get(request.filingStatus()) : BigDecimal.ZERO;
        BigDecimal taxable = current.subtract(standard).subtract(personal).max(BigDecimal.ZERO);
        BigDecimal beforeCredits = switch (rules.system()) {
            case "NONE" -> BigDecimal.ZERO;
            case "FLAT" -> taxable.multiply(rules.rate());
            case "PROGRESSIVE" -> bracketTax(taxable, rules.brackets().get(request.filingStatus()));
            default -> throw new UnsupportedScenario("Unsupported tax system");
        };
        BigDecimal additional = BigDecimal.ZERO;
        for (Surcharge surcharge : rules.surcharges()) {
            additional = additional.add(taxable.subtract(surcharge.threshold().get(request.filingStatus()))
                    .max(BigDecimal.ZERO).multiply(surcharge.rate()));
        }
        if (!rules.filingThreshold().isEmpty()
                && starting.compareTo(rules.filingThreshold().get(request.filingStatus())) <= 0) {
            // Declared state-defined total income, before exemptions/exclusions.
            beforeCredits = BigDecimal.ZERO;
            additional = BigDecimal.ZERO;
        }
        BigDecimal credits = belowCeiling && rules.exemptionType().equals("CREDIT")
                ? rules.exemptions().get(request.filingStatus()).min(beforeCredits) : BigDecimal.ZERO;
        BigDecimal finalTax = beforeCredits.subtract(credits).max(BigDecimal.ZERO).add(additional)
                .setScale(2, RoundingMode.HALF_UP);
        List<String> warnings = new ArrayList<>(rules.limitations());
        warnings.add("2026 full-year resident research estimate; not a tax return. Final tax rounded HALF_UP to cents.");
        if (rules.readiness().equals("REVIEW_REQUIRED")) {
            warnings.add("Dataset REVIEW_REQUIRED: provisional, not independently certified or production-ready.");
        }
        return new StateTaxResult(request.jurisdiction(), request.taxYear(), request.filingStatus(),
                rules.datasetVersion(), rules.readiness().equals("REVIEW_REQUIRED")
                ? Status.PROVISIONAL : Status.CALCULATED,
                Optional.of(new Estimate(starting, incomeAdjustments, exclusions, seniors,
                        standard, personal, taxable, beforeCredits, credits, additional, finalTax)), warnings);
    }

    private BigDecimal exclude(Adjustment rule, List<Component> pool, StateTaxRequest request,
                               BigDecimal starting, boolean stateDefined,
                               Map<String, BigDecimal> consumedLimits, String owner) {
        if (!rule.federalAgiMaximum().isEmpty()
                && request.federalAgi().compareTo(rule.federalAgiMaximum().get(request.filingStatus())) > 0) {
            return BigDecimal.ZERO;
        }
        List<Component> eligible = new ArrayList<>();
        for (Component component : pool) {
            if (!rule.appliesTo().contains(component.income.type()) || component.remaining.signum() == 0) {
                continue;
            }
            boolean ageEligible = rule.minimumAge() == null
                    || component.person.age().compareTo(rule.minimumAge()) >= 0
                    || rule.alternativeEvents().stream().anyMatch(component.person.qualifyingEvents()::contains);
            if (!ageEligible) {
                if (stateDefined && rule.method().equals("UNLIMITED_ELIGIBLE_INCOME")) {
                    throw new UnsupportedScenario("Early state-defined retirement distribution requires basis/eligibility review");
                }
                continue;
            }
            if (!rule.pensionSources().isEmpty()) {
                if (component.income.pensionSource() == PensionSource.UNKNOWN) {
                    throw new UnsupportedScenario("Pension source required for " + rule.id());
                }
                if (!rule.pensionSources().contains(component.income.pensionSource())) {
                    continue;
                }
            }
            if (rule.qualification() != null) {
                Qualification qualification = component.income.qualifications()
                        .getOrDefault(rule.qualification(), Qualification.UNKNOWN);
                if (qualification == Qualification.UNKNOWN) {
                    throw new UnsupportedScenario("Unassessed qualification " + rule.qualification());
                }
                if (qualification == Qualification.INELIGIBLE) {
                    if (stateDefined && rule.method().equals("UNLIMITED_ELIGIBLE_INCOME")) {
                        throw new UnsupportedScenario("Nonqualifying state-defined retirement income requires separate review");
                    }
                    continue;
                }
            }
            eligible.add(component);
        }
        BigDecimal total = sum(eligible);
        if (total.signum() == 0) {
            return BigDecimal.ZERO;
        }
        String groupKey = rule.group() + ":" + owner;
        BigDecimal consumed = rule.coordinatedLimit()
                ? consumedLimits.getOrDefault(groupKey, BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal excluded = switch (rule.method()) {
            case "UNLIMITED_ELIGIBLE_INCOME", "UNLIMITED_FEDERALLY_TAXABLE_SOCIAL_SECURITY" -> total;
            case "CAPPED_ELIGIBLE_INCOME" -> total.min(rule.maximum().get(request.filingStatus())
                    .subtract(consumed).max(BigDecimal.ZERO));
            case "AGE_BAND_CAPPED_ELIGIBLE_INCOME" -> {
                BigDecimal age = eligible.getFirst().person.age();
                Band matched = rule.ageBands().stream()
                        .filter(band -> age.compareTo(band.from()) >= 0
                                && (band.to() == null || age.compareTo(band.to().add(BigDecimal.ONE)) < 0))
                        .findFirst().orElse(null);
                if (matched == null) {
                    if (age.compareTo(rule.ageBands().getFirst().from()) < 0) {
                        yield BigDecimal.ZERO;
                    }
                    throw new UnsupportedScenario("Fractional age falls in unspecified age-band boundary");
                }
                yield total.min(matched.amount().get(request.filingStatus()).subtract(consumed).max(BigDecimal.ZERO));
            }
            case "STEPPED_INCOME_EXCLUSION" -> {
                if (!rule.inclusiveIncomeBands()
                        && rule.incomeBands().stream().anyMatch(band -> starting.compareTo(band.to()) == 0)) {
                    throw new UnsupportedScenario("Dataset does not specify inclusive income-band endpoints");
                }
                if (rule.zeroAboveIncomeBands() && starting.compareTo(rule.incomeBands().getLast().to()) > 0) {
                    yield BigDecimal.ZERO;
                }
                Band matched = rule.incomeBands().stream()
                        .filter(band -> rule.inclusiveIncomeBands()
                                ? (starting.compareTo(band.from()) > 0 || band.from().signum() == 0)
                                    && starting.compareTo(band.to()) <= 0
                                : starting.compareTo(band.from()) >= 0 && starting.compareTo(band.to()) < 0)
                        .findFirst().orElseThrow(() -> new UnsupportedScenario("No declared exclusion band for total income"));
                BigDecimal amount = matched.amount().get(request.filingStatus());
                yield matched.method().equals("CAPPED_ELIGIBLE_INCOME") ? total.min(amount) : total.multiply(amount);
            }
            default -> throw new UnsupportedScenario("Unimplemented adjustment " + rule.method());
        };
        if (rule.coordinatedLimit()) {
            consumedLimits.merge(groupKey, excluded, BigDecimal::add);
        }
        BigDecimal remaining = excluded;
        for (Component component : eligible) {
            BigDecimal used = component.remaining.min(remaining);
            component.remaining = component.remaining.subtract(used);
            remaining = remaining.subtract(used);
        }
        return excluded;
    }

    private void checkSharedGroups(StateTaxRules rules, List<Component> components) {
        Map<String, List<Adjustment>> groups = new HashMap<>();
        for (Adjustment rule : rules.adjustments()) {
            if (rule.group() != null) {
                groups.computeIfAbsent(rule.group(), ignored -> new ArrayList<>()).add(rule);
            }
        }
        for (List<Adjustment> group : groups.values()) {
            if (group.size() < 2 || group.stream().allMatch(Adjustment::coordinatedLimit)) {
                continue;
            }
            for (Component component : components) {
                if (component.income.type() != IncomeType.SOCIAL_SECURITY || component.remaining.signum() == 0) {
                    continue;
                }
                boolean fullSs = group.stream().anyMatch(rule -> rule.method()
                        .equals("UNLIMITED_FEDERALLY_TAXABLE_SOCIAL_SECURITY")
                        && (rule.minimumAge() == null || component.person.age().compareTo(rule.minimumAge()) >= 0));
                boolean other = components.stream().anyMatch(otherComponent -> otherComponent.person == component.person
                        && otherComponent.income.type() != IncomeType.SOCIAL_SECURITY && otherComponent.remaining.signum() > 0
                        && group.stream().anyMatch(rule -> rule.appliesTo().contains(otherComponent.income.type())));
                if (fullSs && other) {
                    throw new UnsupportedScenario("Shared Social Security/pension cap consumption is unspecified by dataset");
                }
            }
        }
    }

    private void checkBoundaries(StateTaxRequest request, StateTaxRules rules, String stage,
                                 BigDecimal starting, BigDecimal adjusted) {
        for (Boundary boundary : rules.boundaries()) {
            if (!boundary.stage().equals(stage)) {
                continue;
            }
            if (boundary.trigger().equals("SOCIAL_SECURITY_ALLOCATION_MISMATCH")) {
                BigDecimal gross = request.people().stream().map(PersonIncome::totalSocialSecurity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal taxable = request.people().stream().map(PersonIncome::federallyTaxableSocialSecurity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (gross.signum() > 0) {
                    for (PersonIncome person : request.people()) {
                        BigDecimal allocation = taxable.multiply(person.totalSocialSecurity())
                                .divide(gross, 2, RoundingMode.HALF_UP);
                        if (allocation.compareTo(person.federallyTaxableSocialSecurity().setScale(2, RoundingMode.HALF_UP)) != 0) {
                            throw new UnsupportedScenario(boundary.id() + ": " + boundary.reason());
                        }
                    }
                }
                continue;
            }
            BigDecimal basis = switch (boundary.basis()) {
                case "FEDERAL_AGI" -> request.federalAgi();
                case "STARTING_STATE_BASE" -> starting;
                case "ADJUSTED_STATE_BASE" -> adjusted;
                default -> throw new UnsupportedScenario("Unknown scenario-boundary basis");
            };
            boolean above = !boundary.maximum().isEmpty()
                    && basis.compareTo(boundary.maximum().get(request.filingStatus())) > 0;
            if (boundary.trigger().equals("ABOVE_MAXIMUM")) {
                if (above) {
                    throw new UnsupportedScenario(boundary.id() + ": " + boundary.reason());
                }
                continue;
            }
            if (above) {
                continue;
            }
            for (PersonIncome person : request.people()) {
                if (boundary.minimumAge() != null && (boundary.trigger().equals("AGE_BELOW_MINIMUM")
                        ? person.age().compareTo(boundary.minimumAge()) >= 0
                        : person.age().compareTo(boundary.minimumAge()) < 0)) {
                    continue;
                }
                if (boundary.trigger().equals("BENEFIT_ELIGIBILITY_UNKNOWN")) {
                    if (person.totalSocialSecurity().signum() == 0 && adjusted.signum() > 0) {
                        throw new UnsupportedScenario(boundary.id() + ": " + boundary.reason());
                    }
                    continue;
                }
                boolean present = boundary.incomeTypes().isEmpty() || person.income().stream().anyMatch(income ->
                        boundary.incomeTypes().contains(income.type())
                        && (income.federalIncluded().signum() > 0 || income.stateDefinedAmount().signum() > 0)
                        && (boundary.pensionSources().isEmpty() || boundary.pensionSources().contains(income.pensionSource()))
                        && (boundary.qualification() == null || income.qualifications()
                        .getOrDefault(boundary.qualification(), Qualification.UNKNOWN) != Qualification.ELIGIBLE));
                if (present && (starting.signum() > 0 || boundary.qualification() != null)) {
                    throw new UnsupportedScenario(boundary.id() + ": " + boundary.reason());
                }
            }
        }
    }

    private static BigDecimal bracketTax(BigDecimal taxable, List<Bracket> brackets) {
        BigDecimal tax = BigDecimal.ZERO;
        for (Bracket bracket : brackets) {
            BigDecimal upper = bracket.to() == null ? taxable : taxable.min(bracket.to());
            tax = tax.add(upper.subtract(bracket.from()).max(BigDecimal.ZERO).multiply(bracket.rate()));
        }
        return tax;
    }

    private static BigDecimal sum(List<Component> components) {
        return components.stream().map(component -> component.remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static StateTaxResult unsupported(StateTaxRequest request, String version, String message) {
        return new StateTaxResult(request.jurisdiction(), request.taxYear(), request.filingStatus(),
                version, Status.UNSUPPORTED, Optional.empty(), List.of(message));
    }

    private static final class Component {
        private final PersonIncome person;
        private final Income income;
        private BigDecimal remaining;

        private Component(PersonIncome person, Income income, BigDecimal remaining) {
            this.person = person;
            this.income = income;
            this.remaining = remaining;
        }
    }

    private static final class UnsupportedScenario extends RuntimeException {
        private UnsupportedScenario(String message) {
            super(message);
        }
    }
}
