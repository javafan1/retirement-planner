package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.estate.*;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Nine real individual elections using shared mortality, benefit, valuation and projection primitives. */
public final class SinglePersonMortalityAnalysis {
    public enum Mode { SOCIAL_SECURITY_ONLY, INTEGRATED }

    public record Outcome(int deathYear, BigDecimal probability, BigDecimal nominalEstate,
            BigDecimal investableAssets, BigDecimal presentValue) { }

    public record Entry(SocialSecurityHouseholdClaimingStrategy strategy, BigDecimal expectedPresentValue,
            BigDecimal expectedNominal, BigDecimal expectedInvestableAssets, List<Outcome> outcomes) {
        public Entry { outcomes = List.copyOf(outcomes); }
        public Optional<BigDecimal> minimumNominalEstate() {
            return outcomes.stream().map(Outcome::nominalEstate).min(BigDecimal::compareTo);
        }
        public Optional<BigDecimal> maximumNominalEstate() {
            return outcomes.stream().map(Outcome::nominalEstate).max(BigDecimal::compareTo);
        }
    }

    public record Result(Mode mode, String primaryName, int currentAge, IndividualLongevityScenarios mortality,
            LocalDate valuationDate, BigDecimal discountRate, BigDecimal colaRate, BigDecimal inflationRate,
            List<Entry> entries, int projectionCount) {
        public Result {
            entries = List.copyOf(entries);
            if (entries.size() != 9) throw new IllegalArgumentException("Nine claiming strategies are required.");
        }
        public List<Entry> ranked() {
            return entries.stream().sorted(Comparator.comparing(Entry::expectedPresentValue).reversed()).toList();
        }
        public Entry current() {
            return entries.stream().filter(e -> e.strategy().primaryRetirementAge() == currentAge).findFirst().orElseThrow();
        }
        public int rank(Entry entry) {
            return 1 + (int) entries.stream().filter(e -> e.expectedPresentValue().compareTo(entry.expectedPresentValue()) > 0).count();
        }
    }

    public Result calculate(RetirementPlan source, IndividualLongevityScenarios mortality,
            LocalDate valuationDate, BigDecimal realDiscountRate, Mode mode,
            AnalysisProgressListener progress, AnalysisCancellationToken cancellation) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(progress);
        Objects.requireNonNull(cancellation);
        Objects.requireNonNull(valuationDate);
        EstatePresentValueCalculator.validateRate(realDiscountRate);
        var plan = new RetirementPlanScenarioCopyService().copy(source);
        if (plan.getHousehold().hasSpouse()) throw new IllegalArgumentException("This request requires a single-person household.");
        IntegratedSocialSecurityStrategyEvaluator.requireAdvancedPlan(plan);
        var person = plan.getHousehold().getPrimaryPerson();
        if (!person.getBirthDate().equals(mortality.individual().request().dateOfBirth())) {
            throw new IllegalArgumentException("Mortality birth date must match Primary.");
        }
        if (person.getMortalityCategory() == null || !person.getMortalityCategory().name()
                .equals(mortality.individual().request().mortalityCategory().name())) {
            throw new IllegalArgumentException("Mortality category must match Primary's Person record.");
        }
        var current = new IntegratedSocialSecurityStrategyEvaluator().extractCurrentStrategy(plan);
        if (mode == Mode.SOCIAL_SECURITY_ONLY && mortality.scenarios().stream()
                .filter(s -> s.probability().signum() > 0)
                .anyMatch(s -> YearMonth.from(valuationDate).isAfter(YearMonth.from(s.deathDate())))) {
            throw new IllegalArgumentException("Present-value base date cannot be after a positive-probability strategy horizon.");
        }
        var income = person.getIncomeSources().stream().filter(SocialSecurityIncome.class::isInstance)
                .map(SocialSecurityIncome.class::cast).findFirst().orElseThrow();
        var cola = plan.getPlanningAssumptions().getSocialSecurityColaRate();
        var inflation = plan.getPlanningAssumptions().getEconomicAssumptions().getGeneralInflationRate();
        var strategies = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan).strategies();
        var entries = new ArrayList<Entry>();
        var ssPv = new SocialSecurityPresentValueCalculator();
        var estatePv = new EstatePresentValueCalculator();
        var snapshots = new EstateAtSecondDeathCalculator(); // cardinality-independent balance snapshot selection
        var engine = mode == Mode.INTEGRATED ? new ProjectionEngine() : null;
        int projections = 0;
        var phase = AnalysisPhase.LONGEVITY_INTEGRATED_SCENARIOS;
        int positiveScenarios = (int) mortality.scenarios().stream().filter(s -> s.probability().signum() > 0).count();
        int total = mode == Mode.INTEGRATED ? 9 * positiveScenarios : 9;
        int done = 0;
        progress.onProgress(new AnalysisProgress(phase, 0, total));
        for (var strategy : strategies) {
            cancellation.throwIfCancellationRequested();
            BigDecimal nominal = BigDecimal.ZERO;
            BigDecimal pv = BigDecimal.ZERO;
            BigDecimal assets = mode == Mode.INTEGRATED ? BigDecimal.ZERO : null;
            var outcomes = new ArrayList<Outcome>();
            if (mode == Mode.SOCIAL_SECURITY_ONLY) {
                var election = new SocialSecurityClaimingElection(AccountOwnership.PRIMARY, person.getBirthDate(),
                        income.getFullRetirementMonthlyBenefit(), income.getBenefitValuationYear(), strategy.primaryRetirementClaimDate());
                var first = YearMonth.from(mortality.individual().request().mortalityBaseDate());
                var last = YearMonth.from(mortality.scenarios().getLast().deathDate());
                if (YearMonth.from(valuationDate).isAfter(last)) {
                    throw new IllegalArgumentException("Valuation date cannot follow the mortality horizon.");
                }
                var monthly = new SocialSecurityStrategyCalculator().calculateOwnRetirementMonthly(election, first, last, cola);
                for (var month : monthly.entrySet()) {
                    cancellation.throwIfCancellationRequested();
                    var weight = mortality.probabilityOfReceipt(month.getKey());
                    var real = ssPv.toBaseDateRealAmount(month.getValue(), cola, YearMonth.from(valuationDate), month.getKey());
                    nominal = nominal.add(month.getValue().multiply(weight));
                    pv = pv.add(ssPv.presentValue(real, realDiscountRate, YearMonth.from(valuationDate), month.getKey()).multiply(weight));
                }
                progress.onProgress(new AnalysisProgress(phase, ++done, total));
            } else {
                for (var scenario : mortality.scenarios()) {
                    cancellation.throwIfCancellationRequested();
                    if (scenario.probability().signum() == 0) continue;
                    var death = LocalDate.of(scenario.deathDate().getYear(), 1, 1);
                    if (death.isBefore(plan.getPlanningAssumptions().getProjectionStartDate())) {
                        throw new IllegalArgumentException("Primary death " + death + " precedes available opening balances.");
                    }
                    snapshots.validateCoverageStart(plan, death);
                    EstateAtSecondDeathSnapshot snapshot;
                    if (death.equals(plan.getPlanningAssumptions().getProjectionStartDate())) {
                        snapshot = snapshots.calculateOpening(plan, death);
                    } else {
                        var isolated = new RetirementPlanScenarioCopyService().copy(plan);
                        var context = ProjectionEvaluationContext.withSocialSecurityStrategy(strategy,
                                HouseholdLifetimeScenario.primaryOnly(Optional.of(Year.from(death))))
                                .withExactEndingYear(death.getYear() - 1);
                        var projection = engine.project(isolated, context);
                        projections++;
                        snapshot = snapshots.calculate(isolated, projection, death);
                    }
                    var value = snapshot.nominalAfterTaxEstate().multiply(
                            estatePv.discountFactor(valuationDate, death, inflation, realDiscountRate));
                    outcomes.add(new Outcome(death.getYear(), scenario.probability(), snapshot.nominalAfterTaxEstate(),
                            snapshot.nominalInvestableAssets(), value));
                    nominal = nominal.add(snapshot.nominalAfterTaxEstate().multiply(scenario.probability()));
                    assets = assets.add(snapshot.nominalInvestableAssets().multiply(scenario.probability()));
                    pv = pv.add(value.multiply(scenario.probability()));
                    progress.onProgress(new AnalysisProgress(phase, ++done, total));
                }
            }
            entries.add(new Entry(strategy, pv, nominal, assets, outcomes));
        }
        cancellation.throwIfCancellationRequested();
        return new Result(mode, person.getFullName(), current.primaryRetirementAge(), mortality, valuationDate,
                realDiscountRate, cola, inflation, entries, projections);
    }
}
