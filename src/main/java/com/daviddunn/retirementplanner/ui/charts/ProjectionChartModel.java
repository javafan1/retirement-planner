package com.daviddunn.retirementplanner.ui.charts;

import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.domain.projection.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.*;

/** Immutable presentation snapshot of existing projected dollar values. No JavaFX or engine dependency.
 * No inflation adjustment is applied. Account classifications are the existing ProjectionAssetType.
 */
public record ProjectionChartModel(List<Point> years, List<Claim> claims,
        List<Period> rothPeriods, List<Period> rmdPeriods) {
    public ProjectionChartModel {
        years = List.copyOf(years); claims = List.copyOf(claims);
        rothPeriods = List.copyOf(rothPeriods); rmdPeriods = List.copyOf(rmdPeriods);
    }
    public record Claim(int year, String person, int age, LocalDate date) { }
    public record Period(int firstYear, int lastYear) { }
    public record Point(int year, int primaryAge, Map<ProjectionChartMetric, BigDecimal> values,
            Map<ProjectionAssetType, BigDecimal> composition, boolean compositionComplete) {
        public Point { values = Map.copyOf(values); composition = Map.copyOf(composition); }
        public BigDecimal compositionResidual() {
            return values.get(ProjectionChartMetric.INVESTABLE_ASSETS).subtract(
                    composition.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        public BigDecimal percentage(ProjectionAssetType category) {
            BigDecimal total = values.get(ProjectionChartMetric.INVESTABLE_ASSETS);
            return total.signum() == 0 ? BigDecimal.ZERO : composition.get(category)
                    .multiply(BigDecimal.valueOf(100)).divide(total, MathContext.DECIMAL128);
        }
    }

    public static ProjectionChartModel empty() { return new ProjectionChartModel(List.of(), List.of(), List.of(), List.of()); }

    public static ProjectionChartModel from(List<ProjectionYear> years,
            List<NonInvestableAssetProjection> nonInvestable, BreakEvenPlanSummary people,
            List<com.daviddunn.retirementplanner.domain.financial.Account> expectedAccounts) {
        List<Point> points = new ArrayList<>();
        var sorted = years.stream().sorted(Comparator.comparingInt(ProjectionYear::getCalendarYear)).toList();
        Set<Integer> unique = new HashSet<>();
        for (var year : sorted) {
            if (!unique.add(year.getCalendarYear())) throw new IllegalArgumentException("Duplicate projection year");
            BigDecimal non = nonInvestable.stream().filter(p -> p.getCalendarYear() == year.getCalendarYear())
                    .map(NonInvestableAssetProjection::getTotalValue).findFirst().orElse(BigDecimal.ZERO);
            Map<ProjectionChartMetric, BigDecimal> values = new EnumMap<>(ProjectionChartMetric.class);
            for (var metric : ProjectionChartMetric.values()) values.put(metric, switch (metric) {
                case INVESTABLE_ASSETS -> year.getEndingInvestableAssets();
                case TOTAL_NET_WORTH -> year.getEndingInvestableAssets().add(non);
                case AFTER_TAX_ESTATE -> year.getAfterTaxEstateValue();
                case TOTAL_INCOME -> year.getGuaranteedIncome();
                case EXPENSES -> year.getAnnualExpenses();
                case TOTAL_TAXES -> year.getTotalIncomeTax();
                case SOCIAL_SECURITY -> year.getSocialSecurityResult().householdBenefit();
                case INVESTMENT_GROWTH -> year.getInvestmentGrowth();
                case PORTFOLIO_WITHDRAWALS -> year.getPortfolioWithdrawal();
                case NON_INVESTABLE_ASSETS -> non;
                case MEDICARE -> year.getAnnualMedicarePremium();
                case ROTH_CONVERSIONS -> year.getRothConversion();
                case RMD -> year.getRmdDistributedInProjection();
            });
            Map<ProjectionAssetType, BigDecimal> composition = new EnumMap<>(ProjectionAssetType.class);
            for (var type : ProjectionAssetType.values()) composition.put(type, BigDecimal.ZERO);
            boolean valid = CompositionDisplayReconciliation.hasCompleteAccounts(year, expectedAccounts);
            if (valid) {
                for (var account : year.getEndingAccountSnapshots()) composition.merge(
                        account.getAccount().getProjectionAssetType(), account.getEndingBalance(), BigDecimal::add);
                composition.merge(ProjectionAssetType.TAXABLE, year.getEndingRetainedNonQualifiedAssets(), BigDecimal::add);
            }
            BigDecimal sum = composition.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            points.add(new Point(year.getCalendarYear(), year.getPrimaryPersonAge(), values, composition,
                    valid && CompositionDisplayReconciliation.withinCurrencyPrecision(year.getEndingInvestableAssets(), sum)));
        }
        List<Claim> claims = points.isEmpty() ? List.of()
                : claims(people, points.getFirst().year(), points.getLast().year());
        return new ProjectionChartModel(points, claims, periods(points, ProjectionChartMetric.ROTH_CONVERSIONS),
                periods(points, ProjectionChartMetric.RMD));
    }

    /** Shared configured-election markers, also usable when a reference projection ends early. */
    public static List<Claim> claims(BreakEvenPlanSummary people, int firstYear, int lastYear) {
        List<Claim> claims = new ArrayList<>();
        if (people != null) {
            for (var person : List.of(people.primary(), people.spouse())) {
                // Shared immutable election metadata captures source.getStartDate(), exactly as projection does.
                LocalDate date = person.retirementClaimDate();
                if (date != null && person.retirementClaimingAge() != null
                        && date.getYear() >= firstYear && date.getYear() <= lastYear) {
                    claims.add(new Claim(date.getYear(), person.name(), person.retirementClaimingAge(), date));
                }
            }
        }
        claims.sort(Comparator.comparingInt(Claim::year));
        return List.copyOf(claims);
    }

    public static List<Period> periods(List<Point> points, ProjectionChartMetric metric) {
        List<Period> periods = new ArrayList<>();
        for (int year : points.stream().filter(p -> p.values().get(metric).signum() > 0)
                .map(Point::year).distinct().sorted().toList()) {
            if (!periods.isEmpty() && periods.getLast().lastYear() + 1 == year) {
                var previous = periods.removeLast();
                periods.add(new Period(previous.firstYear(), year));
            } else periods.add(new Period(year, year));
        }
        return List.copyOf(periods);
    }
}
