package com.daviddunn.retirementplanner.domain.projection;

import java.math.BigDecimal;
import java.time.Year;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import java.time.LocalDate;

/**
 * Immutable scenario-local expense multipliers, with no generator or engine dependency.
 * Contains enabled occurrences only. Missing occurrence/year lookups fail, never default to one.
 * Financial execution requires an explicit frozen-list binding; an unbound sampler
 * path cannot be silently applied to a plan.
 */
public final class ProjectionExpensePath {

    public static final int MAX_OCCURRENCES = 1000;
    public static final int MAX_YEARS = 1000;
    public static final int MAX_ENTRIES = 100_000;

    /** Full SHA-256 financial-definition key plus zero-based first-seen duplicate ordinal. */
    public record Occurrence(String correspondenceKey, int duplicateIndex) {
        public Occurrence {
            Objects.requireNonNull(correspondenceKey, "Correspondence key is required.");
            if (!correspondenceKey.matches("[0-9a-f]{64}") || duplicateIndex < 0) {
                throw new IllegalArgumentException("A full lowercase SHA-256 key and nonnegative occurrence are required.");
            }
        }
    }

    private final int firstYear;
    private final int lastYear;
    private final Map<Occurrence, List<BigDecimal>> multipliers;
    private final List<FinancialDefinition> definitions;
    private final List<Optional<Occurrence>> bindings;

    /** Exact binding values, not a new fingerprint or sampling identity. */
    public record FinancialDefinition(ExpenseType type, BigDecimal amount, LocalDate start,
            LocalDate end, GrowthCategory escalation) {
        public static FinancialDefinition capture(Expense expense) {
            Objects.requireNonNull(expense);
            return new FinancialDefinition(expense.getExpenseType(), expense.getAnnualAmount(),
                    expense.getStartDate(), expense.getEndDate(), expense.getGrowthCategory());
        }
    }

    public ProjectionExpensePath(int firstYear, int lastYear,
            Map<Occurrence, List<BigDecimal>> multipliers) {
        validateBounds(firstYear, lastYear, Objects.requireNonNull(multipliers).size());
        if (multipliers.isEmpty()) {
            throw new IllegalArgumentException("Disabled expense sampling must omit the path.");
        }
        this.firstYear = firstYear;
        this.lastYear = lastYear;
        var copy = new LinkedHashMap<Occurrence, List<BigDecimal>>();
        for (var entry : multipliers.entrySet()) {
            var occurrence = Objects.requireNonNull(entry.getKey(), "Occurrence is required.");
            var values = List.copyOf(entry.getValue());
            if (values.size() != lastYear - firstYear + 1) {
                throw new IllegalArgumentException("Every occurrence must cover the complete expense horizon.");
            }
            for (var value : values) {
                if (value.signum() <= 0) {
                    throw new IllegalArgumentException("Expense multipliers must be strictly positive.");
                }
            }
            copy.put(occurrence, values);
        }
        this.multipliers = Collections.unmodifiableMap(copy);
        definitions = List.of();
        bindings = List.of();
    }

    private ProjectionExpensePath(ProjectionExpensePath source, List<FinancialDefinition> definitions,
            List<Optional<Occurrence>> bindings) {
        firstYear = source.firstYear;
        lastYear = source.lastYear;
        multipliers = source.multipliers;
        this.definitions = List.copyOf(definitions);
        this.bindings = List.copyOf(bindings);
    }

    public ProjectionExpensePath bind(List<Expense> expenses, List<Optional<Occurrence>> bindings) {
        Objects.requireNonNull(expenses);
        Objects.requireNonNull(bindings);
        if (expenses.size() != bindings.size() || expenses.size() > MAX_OCCURRENCES) {
            throw new IllegalArgumentException("Expense binding must cover the exact frozen input list.");
        }
        var definitions = expenses.stream().map(FinancialDefinition::capture).toList();
        var required = new java.util.HashSet<Occurrence>();
        for (int index = 0; index < bindings.size(); index++) {
            var binding = Objects.requireNonNull(bindings.get(index));
            if (binding.isPresent()) {
                var definition = definitions.get(index);
                if (definition.type() != ExpenseType.RECURRING || definition.amount().signum() < 0
                        || !required.add(binding.orElseThrow())) {
                    throw new IllegalArgumentException("Enabled bindings require distinct nonnegative recurring occurrences.");
                }
            }
        }
        if (!required.equals(multipliers.keySet())) {
            throw new IllegalArgumentException("Expense path and enabled bindings differ.");
        }
        return new ProjectionExpensePath(this, definitions, bindings);
    }

    public void validateBinding(List<Expense> expenses, int first, int last) {
        if (definitions.isEmpty() || !definitions.equals(expenses.stream().map(FinancialDefinition::capture).toList())) {
            throw new IllegalArgumentException("Expense path is unbound or differs from the frozen financial expense list.");
        }
        for (var occurrence : multipliers.keySet()) requireCoverage(occurrence, first, last);
    }

    public Optional<Occurrence> bindingForExpense(int index) {
        if (bindings.isEmpty()) throw new IllegalArgumentException("Expense path requires a financial binding.");
        return bindings.get(index);
    }

    public BigDecimal multiplier(Occurrence occurrence, int year) {
        if (year < firstYear || year > lastYear) {
            throw new IllegalArgumentException("Missing expense year: " + year);
        }
        var values = multipliers.get(Objects.requireNonNull(occurrence, "Occurrence is required."));
        if (values == null) {
            throw new IllegalArgumentException("Missing expense occurrence: " + occurrence);
        }
        return values.get(year - firstYear);
    }

    public void requireCoverage(Occurrence occurrence, int first, int last) {
        validateBounds(first, last, 1);
        multiplier(occurrence, first);
        multiplier(occurrence, last);
    }

    public int firstYear() { return firstYear; }
    public int lastYear() { return lastYear; }
    public Map<Occurrence, List<BigDecimal>> annualMultipliers() { return multipliers; }

    public static void validateBounds(int firstYear, int lastYear, int occurrences) {
        Year.of(firstYear);
        Year.of(lastYear);
        long years = (long) lastYear - firstYear + 1;
        if (years <= 0 || years > MAX_YEARS || occurrences < 0 || occurrences > MAX_OCCURRENCES
                || years * occurrences > MAX_ENTRIES) {
            throw new IllegalArgumentException("Expense path exceeds supported horizon or occurrence bounds.");
        }
    }
}
