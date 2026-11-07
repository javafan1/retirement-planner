package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Explicit versioned storage graph. No JavaFX objects, optional serializers, random generators or engines. */
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = MonteCarloSnapshotDeserializer.class)
public record MonteCarloSnapshot(int version, Mode mode, boolean hasSpouse, Settings settings,
        Request request, Provenance provenance, Fixed fixed, Mortality mortality, Paired paired,
        Display display, Instant startedAt, Instant completedAt,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        ExpenseExecution expenseExecution,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        PairedExpenseExecution pairedExpenseExecution,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        ConditionalBenefitAnalysisExecution benefitExecution) implements AnalysisPayload {
    public static final int VERSION = 1;
    public static final int EXPENSE_VERSION = 2;
    public static final int PAIRED_EXPENSE_VERSION = 3;
    public static final int BENEFIT_VERSION = 4;
    public static final int PAIRED_BENEFIT_VERSION = 5;
    public static final int LIFE_PRINCIPAL_VERSION = 6;
    public static final int PAIRED_LIFE_PRINCIPAL_VERSION = 7;
    public static final int PREMIUM_VERSION = 8;
    public static final int PAIRED_PREMIUM_VERSION = 9;
    public static final int MAX_TRIALS = 100_000;
    public static final int MAX_YEARS = 1_000;
    public static final long MAX_ANNUAL_OBSERVATIONS = 1_500_000;
    public static final String AGGREGATION = "TYPE7-v1/complete-terminals/living-funded-prefixes/paired-both-completed/A-minus-B";
    public enum Mode { FIXED, LONGEVITY, PAIRED_FIXED, PAIRED_LONGEVITY }

    public MonteCarloSnapshot(int version, Mode mode, boolean hasSpouse, Settings settings, Request request,
            Provenance provenance, Fixed fixed, Mortality mortality, Paired paired, Display display,
            Instant startedAt, Instant completedAt, ExpenseExecution expenses, PairedExpenseExecution pairedExpenses) {
        this(version, mode, hasSpouse, settings, request, provenance, fixed, mortality, paired, display,
                startedAt, completedAt, expenses, pairedExpenses, null);
    }

    public MonteCarloSnapshot(int version, Mode mode, boolean hasSpouse, Settings settings, Request request,
            Provenance provenance, Fixed fixed, Mortality mortality, Paired paired, Display display,
            Instant startedAt, Instant completedAt) {
        this(version, mode, hasSpouse, settings, request, provenance, fixed, mortality, paired, display,
                startedAt, completedAt, null, null);
    }

    public MonteCarloSnapshot(int version, Mode mode, boolean hasSpouse, Settings settings, Request request,
            Provenance provenance, Fixed fixed, Mortality mortality, Paired paired, Display display,
            Instant startedAt, Instant completedAt, ExpenseExecution expenseExecution) {
        this(version, mode, hasSpouse, settings, request, provenance, fixed, mortality, paired, display,
                startedAt, completedAt, expenseExecution, null);
    }

    public MonteCarloSnapshot {
        Objects.requireNonNull(mode); Objects.requireNonNull(settings); Objects.requireNonNull(request);
        Objects.requireNonNull(provenance); Objects.requireNonNull(display);
        Objects.requireNonNull(startedAt); Objects.requireNonNull(completedAt);
        if ((version < VERSION || version > PAIRED_PREMIUM_VERSION) || completedAt.isBefore(startedAt))
            throw invalid("Unsupported version or timestamps.");
        boolean comparison = mode == Mode.PAIRED_FIXED || mode == Mode.PAIRED_LONGEVITY;
        if (version <= PAIRED_EXPENSE_VERSION && ((version == EXPENSE_VERSION) != (expenseExecution != null)
                    || (version == PAIRED_EXPENSE_VERSION) != (pairedExpenseExecution != null) || benefitExecution != null)
                || version >= BENEFIT_VERSION && (benefitExecution == null
                    || version != benefitPayloadVersion(benefitExecution, comparison))
                || expenseExecution != null && comparison || pairedExpenseExecution != null && !comparison)
            throw invalid("Unsupported expense capability/result combination.");
        if ((fixed == null ? 0 : 1) + (mortality == null ? 0 : 1) + (paired == null ? 0 : 1) != 1
                || (mode == Mode.FIXED) != (fixed != null) || (mode == Mode.LONGEVITY) != (mortality != null))
            throw invalid("Result family mismatch.");
    }

    public AnalysisKind kind() {
        return AnalysisKind.valueOf((hasSpouse ? "COUPLE" : "INDIVIDUAL") + "_MONTE_CARLO_" + mode.name());
    }

    @Override public boolean successful() { return true; } // Complete all-funding-failed runs are valid evidence.

    @Override public void validate(AnalysisKind kind) {
        if (kind != kind()) throw invalid("Analysis kind mismatch.");
        request.validate(hasSpouse, mode, settings);
        requireImplementedPlanInputs();
        if (pairedExpenseExecution != null) pairedExpenseExecution.validate(request.planA(), request.planB());
        if (expenseExecution != null) {
            expenseExecution.validate(request.planA());
        }
        if (hasExpenseExecution() || benefitExecution != null) {
            if (provenance.source() != MonteCarloStrategyComparisonResult.WorldSource.GENERATED
                    || !MonteCarloScenarioGenerator.MODEL_VERSION.equals(provenance.returns())
                    || !MonteCarloInflationGenerator.MODEL_VERSION.equals(provenance.inflation())
                    || !"RPMC-SHA256-v1/legacy-market-v1".equals(provenance.streams())
                    || (mode == Mode.LONGEVITY || mode == Mode.PAIRED_LONGEVITY)
                        && !(hasSpouse ? "HOUSEHOLD_MORTALITY_V1" : "PRIMARY_MORTALITY_V1").equals(provenance.mortality())
                    || provenance.suppliedWorldFingerprint() != null)
                throw invalid("Enabled financial capabilities require supported generated worlds.");
        }
        if ((mortality != null || mode == Mode.PAIRED_LONGEVITY) != (provenance.mortality() != null))
            throw invalid("Mortality provenance does not match the result family.");
        int n = settings.trials();
        long observations = 0;
        if (fixed != null) {
            if (fixed.annual().size() > MAX_YEARS || fixed.reference().firstYear() != request.start().getYear())
                throw invalid("Invalid fixed annual range.");
            if (fixed.outcomes().size() != n) throw invalid("Trial count mismatch.");
            for (int i = 0; i < n; i++) {
                var t = fixed.outcomes().get(i);
                if (t.index() != i || (t.metrics() == null) == (t.failure() == null)
                        || t.failure() != null && t.nonpositiveYear() != null) throw invalid("Invalid fixed outcome.");
                if (t.metrics() != null && (t.metrics().lifetimeSpouseSocialSecurity() != null) != hasSpouse)
                    throw invalid("Fixed metric household mismatch.");
            }
            fixed.restore(settings.restore(), provenance.returns());
        } else if (mortality != null) {
            if (mortality.outcomes().size() != n) throw invalid("Trial count mismatch.");
            int completed = 0;
            for (int i = 0; i < n; i++) {
                var t = mortality.outcomes().get(i);
                if (t.index() != i) throw invalid("Unordered trial identity.");
                t.life().validate(hasSpouse, true);
                observations += t.outcome().validate(request.start(), null, t.life().lastYear());
                if (t.outcome().terminal() != null) completed++;
                checkBudget(observations);
            }
            mortality.summary().validate(n, completed, hasSpouse);
        } else {
            if (paired.outcomes().size() != n) throw invalid("Paired trial count mismatch.");
            int both = 0, aOnly = 0, bOnly = 0, neither = 0;
            for (int i = 0; i < n; i++) {
                var t = paired.outcomes().get(i);
                if (t.index() != i) throw invalid("Unordered paired identity.");
                t.life().validate(hasSpouse, mode == Mode.PAIRED_LONGEVITY);
                if (mode == Mode.PAIRED_FIXED && !t.life().equals(request.fixedLife())) throw invalid("Fixed lifetime mismatch.");
                int last = mode == Mode.PAIRED_FIXED ? request.endingYear() : t.life().lastYear();
                observations += t.a().validate(request.start(), null, last);
                observations += t.b().validate(request.start(), t.a().history(), last);
                checkBudget(observations);
                boolean a = t.a().terminal() != null, b = t.b().terminal() != null;
                if (a && b) {
                    both++;
                    if (!t.a().terminal().balanceDate().equals(t.b().terminal().balanceDate())) throw invalid("Paired terminal date mismatch.");
                } else if (a) aOnly++; else if (b) bOnly++; else neither++;
            }
            if (!paired.summary().states().equals(new MonteCarloPairedStateSummary(n, both, aOnly, bOnly, neither)))
                throw invalid("Paired state counts mismatch.");
            paired.summary().restore();
        }
        if (display.years().size() > MAX_YEARS || display.chart().years().size() > MAX_YEARS)
            throw invalid("Display range exceeds limit.");
        if ((paired == null) != (display.people() != null)
                || display.people() != null && display.people().hasSpouse() != hasSpouse)
            throw invalid("Display household mismatch.");
        MonteCarloSnapshotNumbers.validate(this);
    }

    public static int benefitPayloadVersion(ConditionalBenefitAnalysisExecution capability, boolean paired) {
        if (capability.includesPremiums()) return paired ? PAIRED_PREMIUM_VERSION : PREMIUM_VERSION;
        return capability.excludesLifePrincipal() ? paired ? PAIRED_LIFE_PRINCIPAL_VERSION : LIFE_PRINCIPAL_VERSION
                : paired ? PAIRED_BENEFIT_VERSION : BENEFIT_VERSION;
    }

    public int fingerprintVersion() { return benefitExecution != null && benefitExecution.includesPremiums() ? paired != null ? 10 : 9
            : benefitExecution != null ? benefitExecution.excludesLifePrincipal()
            ? paired != null ? 8 : 7 : paired != null ? 6 : 5
            : pairedExpenseExecution != null ? 4 : expenseExecution != null ? 3 : 1; }

    public boolean hasExpenseExecution() { return expenseExecution != null || pairedExpenseExecution != null; }

    /** A legacy artifact is never reinterpreted as proof of enabled execution. */
    public void requireInterpretable() {
        requireImplementedPlanInputs();
        if (benefitExecution == null && (com.daviddunn.retirementplanner.domain.financial.ContractualPremiums.requiresCapability(MonteCarloPlanDocument.read(request.planA()))
                || request.planB() != null && com.daviddunn.retirementplanner.domain.financial.ContractualPremiums.requiresCapability(MonteCarloPlanDocument.read(request.planB()))))
            throw invalid("Historical conditional benefits have no authenticated analyzer execution capability; original artifact preserved.");
        if (!hasExpenseExecution() && (ExpenseExecution.configured(MonteCarloPlanDocument.read(request.planA()))
                || request.planB() != null && ExpenseExecution.configured(MonteCarloPlanDocument.read(request.planB()))))
            throw invalid("Historical expense configuration has no supported execution provenance; the artifact was preserved.");
    }

    private void requireImplementedPlanInputs() {
        boolean premiums = com.daviddunn.retirementplanner.domain.financial.ContractualPremiums.configured(MonteCarloPlanDocument.read(request.planA()))
                || request.planB() != null && com.daviddunn.retirementplanner.domain.financial.ContractualPremiums.configured(MonteCarloPlanDocument.read(request.planB()));
        if (premiums && (benefitExecution == null || !benefitExecution.includesPremiums()))
            throw invalid("Contractual premiums require their versioned execution capability.");
        if (benefitExecution != null) {
            benefitExecution.validate(kind(), request.planA(), request.planB());
            for (var candidate : benefitExecution.candidates())
                com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission.requireAnalyzerSupported(
                        MonteCarloPlanDocument.read(candidate.originalInputs()));
            boolean positiveA = ExpenseExecution.configured(MonteCarloPlanDocument.read(request.planA()));
            boolean positiveB = request.planB() != null && ExpenseExecution.configured(MonteCarloPlanDocument.read(request.planB()));
            if ((paired == null ? expenseExecution != null : pairedExpenseExecution != null) != (positiveA || positiveB))
                throw invalid("Conditional-benefit result has incomplete stochastic expense provenance.");
        }
    }

    public record Settings(int trials, long seed, BigDecimal expectedReturn, BigDecimal volatility,
            MonteCarloInflationSettings inflation) {
        public Settings {
            if (trials < 1 || trials > MAX_TRIALS) throw invalid("Invalid trial count.");
            new MonteCarloSettings(trials, seed, expectedReturn, volatility, Optional.ofNullable(inflation));
        }
        public static Settings capture(MonteCarloSettings s) {
            return new Settings(s.simulationCount(), s.seed(), s.expectedReturn(), s.returnVolatility(), s.inflation().orElse(null));
        }
        public MonteCarloSettings restore() { return new MonteCarloSettings(trials, seed, expectedReturn, volatility, Optional.ofNullable(inflation)); }
    }

    /** References describe generated worlds; supplied sources require a caller-frozen identity digest. */
    public record Provenance(String returns, String mortality, String inflation, String streams, String aggregation,
            MonteCarloStrategyComparisonResult.WorldSource source, String suppliedWorldFingerprint) {
        public Provenance {
            Objects.requireNonNull(returns); Objects.requireNonNull(inflation); Objects.requireNonNull(streams); Objects.requireNonNull(source);
            if (!AGGREGATION.equals(aggregation)) throw invalid("Unsupported statistical semantics.");
            if (source == MonteCarloStrategyComparisonResult.WorldSource.CALLER_SUPPLIED
                    && (suppliedWorldFingerprint == null || !suppliedWorldFingerprint.matches("[0-9a-f]{64}")))
                throw invalid("Supplied worlds require frozen provenance.");
        }
    }

    public record Request(String planA, String planB, String labelA, String labelB, LocalDate start,
            Integer endingYear, Life fixedLife, MortalityInputs mortality) {
        public Request { Objects.requireNonNull(planA); Objects.requireNonNull(labelA); Objects.requireNonNull(start); }
        void validate(boolean couple, Mode mode, Settings settings) {
            boolean comparison = mode == Mode.PAIRED_FIXED || mode == Mode.PAIRED_LONGEVITY;
            boolean sampled = mode == Mode.LONGEVITY || mode == Mode.PAIRED_LONGEVITY;
            if (comparison != (planB != null) || comparison != (labelB != null) || sampled != (mortality != null)
                    || sampled == (endingYear != null) || sampled == (fixedLife != null)) throw invalid("Request family mismatch.");
            if (endingYear != null && (endingYear < start.getYear() || (long) endingYear - start.getYear() + 1 > MAX_YEARS))
                throw invalid("Invalid fixed horizon.");
            if (fixedLife != null) fixedLife.validate(couple, false);
            if (fixedLife != null && couple && fixedLife.primaryDeath() != null && fixedLife.spouseDeath() != null)
                throw invalid("Fixed scenarios support at most one configured death.");
            if (mortality != null) mortality.validate(couple, start);
            var a = MonteCarloPlanDocument.read(planA);
            if (a.getHousehold().hasSpouse() != couple || !a.getPlanningAssumptions().getProjectionStartDate().equals(start))
                throw invalid("Frozen plan binding mismatch.");
            if (mortality != null) mortality.restore(a, settings);
            if (planB != null) {
                var b = MonteCarloPlanDocument.read(planB);
                if (b.getHousehold().hasSpouse() != couple || !b.getPlanningAssumptions().getProjectionStartDate().equals(start))
                    throw invalid("Frozen alternative binding mismatch.");
                var peopleA = a.getHousehold().hasSpouse() ? List.of(a.getHousehold().getPrimaryPerson(), a.getHousehold().getSpouse()) : List.of(a.getHousehold().getPrimaryPerson());
                var peopleB = b.getHousehold().hasSpouse() ? List.of(b.getHousehold().getPrimaryPerson(), b.getHousehold().getSpouse()) : List.of(b.getHousehold().getPrimaryPerson());
                for (int i = 0; i < peopleA.size(); i++) if (!Objects.equals(peopleA.get(i).getBirthDate(), peopleB.get(i).getBirthDate())
                        || peopleA.get(i).getMortalityCategory() != peopleB.get(i).getMortalityCategory()) throw invalid("Paired demographics differ.");
            }
        }
    }

    public record MortalityInputs(SocialSecurityMortalityDistributionRequest primary,
            AnalyzerLongevityAssumptions coupleInputs, SocialSecurityMortalityTableMetadata table,
            List<SocialSecurityMortalityTableEntry> entries, Integer survivorAge) {
        public MortalityInputs { Objects.requireNonNull(primary); Objects.requireNonNull(table); entries = List.copyOf(entries); }
        static MortalityInputs capture(MonteCarloMortalityRequest r) {
            return new MortalityInputs(r.primary(), r.hasSpouse() ? r.longevityAssumptions() : null,
                    r.mortalityTable().metadata(), r.mortalityTable().entries(), r.survivorClaimingAge().orElse(null));
        }
        void validate(boolean couple, LocalDate start) {
            if (couple != (coupleInputs != null) || !primary.mortalityBaseDate().equals(start)
                    || entries.size() > MAX_YEARS || !couple && survivorAge != null) throw invalid("Invalid mortality context.");
            new SocialSecurityMortalityTable(table, entries);
        }
        MonteCarloMortalityRequest restore(RetirementPlan plan, Settings settings) {
            var session = new LongevitySessionSettings(primary.mortalityBaseDate(), primary.mortalityAdjustment(),
                    coupleInputs == null ? SocialSecurityMortalityAdjustment.standard() : coupleInputs.spouseAdjustment());
            var r = coupleInputs == null ? MonteCarloMortalityRequest.individualFromSnapshot(plan, settings.restore(), primary.mortalityAdjustment(), new SocialSecurityMortalityTable(table, entries))
                    : MonteCarloMortalityRequest.coupleFromSnapshot(plan, settings.restore(), session, new SocialSecurityMortalityTable(table, entries), Optional.ofNullable(survivorAge));
            if (!r.primary().equals(primary) || !r.survivorClaimingAge().equals(Optional.ofNullable(survivorAge))
                    || (r.hasSpouse() && !r.longevityAssumptions().equals(coupleInputs)))
                throw invalid("Frozen mortality inputs disagree with plan.");
            return r;
        }
    }

    public record Life(Integer primaryDeath, boolean hasSpouse, Integer spouseDeath) {
        static Life capture(MonteCarloLifetime life) {
            return new Life(life.primary().deathYear().map(Year::getValue).orElse(null), life.hasSpouse(),
                    life instanceof MonteCarloLifetime.Couple c ? c.spouse().deathYear().map(Year::getValue).orElse(null) : null);
        }
        void validate(boolean couple, boolean sampled) {
            if (hasSpouse != couple || !couple && spouseDeath != null || sampled && (primaryDeath == null || couple && spouseDeath == null))
                throw invalid("Invalid lifetime membership.");
            restore();
        }
        int lastYear() { return Math.max(Objects.requireNonNull(primaryDeath), hasSpouse ? Objects.requireNonNull(spouseDeath) : primaryDeath) - 1; }
        MonteCarloLifetime restore() {
            var primary = new MonteCarloLifetime.Life(Optional.ofNullable(primaryDeath).map(Year::of));
            return hasSpouse ? new MonteCarloLifetime.Couple(primary, new MonteCarloLifetime.Life(Optional.ofNullable(spouseDeath).map(Year::of)))
                    : new MonteCarloLifetime.Individual(primary);
        }
    }

    /** Only B may refer to A in the same pair (source=0). No cross-trial references or cycles exist. */
    public record History(int firstYear, Integer source, int sharedLength, List<BigDecimal> suffix) {
        public History {
            suffix = List.copyOf(suffix);
            if (firstYear < 1 || firstYear > 9999 || sharedLength < 0 || suffix.size() > MAX_YEARS
                    || (long) sharedLength + suffix.size() > MAX_YEARS || source == null && sharedLength != 0
                    || source != null && (source != 0 || sharedLength == 0)) throw invalid("Invalid annual prefix.");
            Math.addExact(firstYear, Math.addExact(sharedLength, suffix.size()));
        }
        int length(History a) {
            if (source != null && (a == null || a.source != null || a.firstYear != firstYear || sharedLength > a.suffix.size()))
                throw invalid("Invalid prefix source or length.");
            if (source == null && a != null && a.firstYear != firstYear) throw invalid("Paired origins differ.");
            return sharedLength + suffix.size();
        }
        static History capture(Map<Integer, BigDecimal> years, int first, History a) {
            if (years.size() > MAX_YEARS) throw invalid("Annual history exceeds its source budget.");
            var values = new ArrayList<BigDecimal>();
            for (int i = 0; i < years.size(); i++) values.add(Objects.requireNonNull(years.get(first + i), "Noncontiguous annual history."));
            int shared = 0;
            if (a != null && a.firstYear == first) while (shared < values.size() && shared < a.suffix.size()
                    && values.get(shared).equals(a.suffix.get(shared))) shared++;
            return new History(first, shared == 0 ? null : 0, shared, values.subList(shared, values.size()));
        }
        Map<Integer, BigDecimal> restore(History a) {
            int n = length(a);
            var result = new TreeMap<Integer, BigDecimal>();
            for (int i = 0; i < n; i++) result.put(Math.addExact(firstYear, i), i < sharedLength ? a.suffix.get(i) : suffix.get(i - sharedLength));
            return result;
        }
    }

    public record Failure(int year, int yearIndex, Integer primaryAge, Integer spouseAge, FundingFailure.Stage stage,
            AccountOwnership owner, BigDecimal required, BigDecimal available, BigDecimal shortfall) {
        static Failure capture(FundingFailure f) { return f == null ? null : new Failure(f.calendarYear(), f.projectionYearIndex(), f.primaryAge().orElse(null),
                f.spouseAge().orElse(null), f.stage(), f.owner().orElse(null), f.requiredAmount(), f.availableAmount(), f.shortfallAmount()); }
        FundingFailure restore() { return new FundingFailure(year, yearIndex, Optional.ofNullable(primaryAge), Optional.ofNullable(spouseAge), stage,
                Optional.ofNullable(owner), required, available, shortfall); }
    }
    public record Distribution(MonteCarloPercentiles investable, MonteCarloPercentiles netWorth,
            MonteCarloPercentiles estate, MonteCarloPercentiles taxes) {
        void validate(int completed) {
            for (var p : Arrays.asList(investable, netWorth, estate, taxes))
                if ((p != null) != (completed > 0) || p != null && p.sampleCount() != completed) throw invalid("Distribution population mismatch.");
        }
    }
    public record FixedTrial(int index, ProjectionMetrics metrics, Integer nonpositiveYear, Failure failure) { }
    public record Fixed(List<FixedTrial> outcomes, Map<Integer, MonteCarloPercentiles> annual,
            Distribution terminal, History reference, ProjectionMetrics referenceMetrics, Failure referenceFailure) {
        public Fixed { outcomes = List.copyOf(outcomes); annual = nullableMap(annual); Objects.requireNonNull(terminal); Objects.requireNonNull(reference); }
        MonteCarloAnalysisResult restore(MonteCarloSettings settings, String returnModel) {
            var trials = outcomes.stream().map(t -> new MonteCarloAnalysisResult.RunOutcome(t.index(), Optional.ofNullable(t.metrics()),
                    Optional.ofNullable(t.nonpositiveYear()), optionalFailure(t.failure()))).toList();
            int completed = (int) trials.stream().filter(t -> t.metrics().isPresent()).count(); terminal.validate(completed);
            return new MonteCarloAnalysisResult(settings, returnModel, trials, optionalMap(annual), Optional.ofNullable(terminal.investable()),
                    Optional.ofNullable(terminal.netWorth()), Optional.ofNullable(terminal.estate()), Optional.ofNullable(terminal.taxes()),
                    new MonteCarloAnalysisResult.Reference(reference.restore(null), Optional.ofNullable(referenceMetrics), optionalFailure(referenceFailure)));
        }
    }
    public record Outcome(History history, MonteCarloMortalityAnalysisResult.TerminalOutcome terminal, Failure failure) {
        public Outcome { Objects.requireNonNull(history); if ((terminal == null) == (failure == null)) throw invalid("Outcome must be complete or funding-failed."); }
        int validate(LocalDate start, History a, int last) {
            if (last < start.getYear() - 1) throw invalid("Lifetime ends before opening.");
            int length = history.length(a);
            int completedLast = failure == null ? Math.max(start.getYear() - 1, last) : failure.year() - 1;
            if (history.firstYear != start.getYear() || length != (long) completedLast - start.getYear() + 1
                    || failure != null && (failure.year() > last || failure.yearIndex() != failure.year() - start.getYear()))
                throw invalid("Outcome prefix does not match its endpoint.");
            if (failure != null) failure.restore();
            if (terminal != null && !terminal.balanceDate().equals(last < start.getYear() ? start : LocalDate.of(last, 12, 31)))
                throw invalid("Terminal date mismatch.");
            return length;
        }
        MonteCarloStrategyOutcome restore(History a) { return new MonteCarloStrategyOutcome(history.restore(a), Optional.ofNullable(terminal), optionalFailure(failure)); }
    }
    public record MortalityTrial(int index, Life life, Outcome outcome) { }
    public record MortalityYear(int year, int requested, int living, int funded, int failed, int deceased,
            MonteCarloMortalityAnnualResult.CouplePopulation couple, MonteCarloPercentiles assets) {
        public MortalityYear { new MonteCarloMortalityAnnualResult(year, requested, living, funded, failed, deceased, Optional.ofNullable(couple), Optional.ofNullable(assets)); }
        static MortalityYear capture(MonteCarloMortalityAnnualResult a) { return new MortalityYear(a.year(), a.requestedWorldCount(), a.livingHouseholdCount(),
                a.completedLivingYearSampleCount(), a.livingFundingFailedByYearCount(), a.deceasedCount(), a.couplePopulation().orElse(null), a.investableAssets().orElse(null)); }
        MonteCarloMortalityAnnualResult restore() { return new MonteCarloMortalityAnnualResult(year, requested, living, funded, failed, deceased, Optional.ofNullable(couple), Optional.ofNullable(assets)); }
    }
    public record MortalitySummary(Map<Integer, MortalityYear> annual, int completed, Distribution terminal,
            LocalDate earliest, LocalDate latest, Map<Integer, Integer> terminalYears, FundingFailureStatistics failures) {
        public MortalitySummary { annual = Map.copyOf(annual); terminalYears = Map.copyOf(terminalYears); }
        void validate(int n, int count, boolean couple) {
            if (completed != count || (failures == null ? 0 : failures.count()) != n - completed || annual.size() > MAX_YEARS
                    || terminalYears.values().stream().mapToLong(Integer::longValue).sum() != completed) throw invalid("Mortality population mismatch.");
            terminal.validate(completed);
            for (var e : annual.entrySet()) if (e.getKey() != e.getValue().year() || e.getValue().requested() != n
                    || (e.getValue().couple() != null) != couple) throw invalid("Annual mortality context mismatch.");
        }
    }
    public record Mortality(List<MortalityTrial> outcomes, MortalitySummary summary) {
        public Mortality { outcomes = List.copyOf(outcomes); Objects.requireNonNull(summary); }
    }
    public record PairTrial(int index, Life life, Outcome a, Outcome b) { }
    public record Metric(int count, MonteCarloPercentiles percentiles, BigDecimal mean, int greater, int equal, int less) {
        public Metric { new MonteCarloPairedMetricSummary(count, Optional.ofNullable(percentiles), Optional.ofNullable(mean), greater, equal, less); }
        static Metric capture(MonteCarloPairedMetricSummary s) { return new Metric(s.sampleCount(), s.differencePercentiles().orElse(null), s.meanDifference().orElse(null), s.greaterCount(), s.equalCount(), s.lessCount()); }
        MonteCarloPairedMetricSummary restore() { return new MonteCarloPairedMetricSummary(count, Optional.ofNullable(percentiles), Optional.ofNullable(mean), greater, equal, less); }
    }
    public record PairYear(int year, int requested, int living, int deceased, int aOnly, int bOnly, int neither, Metric assets) {
        public PairYear { new MonteCarloPairedAnnualResult(year, requested, living, deceased, aOnly, bOnly, neither, assets.restore()); }
        static PairYear capture(MonteCarloPairedAnnualResult a) { return new PairYear(a.year(), a.requestedWorldCount(), a.livingHouseholdCount(), a.bothDeceasedCount(), a.aCompletedBFailedCount(), a.aFailedBCompletedCount(), a.bothFailedCount(), Metric.capture(a.investableAssetsDifference())); }
        MonteCarloPairedAnnualResult restore() { return new MonteCarloPairedAnnualResult(year, requested, living, deceased, aOnly, bOnly, neither, assets.restore()); }
    }
    public record PairSummary(MonteCarloPairedStateSummary states, FundingFailureStatistics failuresA, FundingFailureStatistics failuresB,
            Metric investable, Metric netWorth, Metric estate, Metric taxes, Map<Integer, PairYear> annual) {
        public PairSummary { Objects.requireNonNull(states); annual = Map.copyOf(annual); if (annual.size() > MAX_YEARS) throw invalid("Annual range exceeds limit."); }
        MonteCarloStrategyComparisonSummary restore() { return new MonteCarloStrategyComparisonSummary(states, Optional.ofNullable(failuresA), Optional.ofNullable(failuresB), investable.restore(), netWorth.restore(), estate.restore(), taxes.restore(), map(annual, PairYear::restore)); }
    }
    public record Paired(List<PairTrial> outcomes, PairSummary summary) {
        public Paired { outcomes = List.copyOf(outcomes); Objects.requireNonNull(summary); }
    }

    public record DisplayYear(int year, MonteCarloPercentiles assets, BigDecimal deterministic, MortalityYear population) { }
    public record ChartPoint(int year, int primaryAge, Map<String, BigDecimal> values, Map<ProjectionAssetType, BigDecimal> composition, boolean complete) {
        public ChartPoint {
            values = Map.copyOf(values); composition = Map.copyOf(composition);
            if (!Set.of("INVESTABLE_ASSETS", "TOTAL_NET_WORTH", "AFTER_TAX_ESTATE", "TOTAL_INCOME", "EXPENSES", "TOTAL_TAXES", "SOCIAL_SECURITY",
                    "INVESTMENT_GROWTH", "PORTFOLIO_WITHDRAWALS", "NON_INVESTABLE_ASSETS", "MEDICARE", "ROTH_CONVERSIONS", "RMD").equals(values.keySet())
                    || composition.size() != ProjectionAssetType.values().length) throw invalid("Unknown frozen chart field.");
        }
    }
    public record Claim(int year, String person, int age, LocalDate date) { }
    public record Period(int firstYear, int lastYear) { }
    public record Chart(List<ChartPoint> years, List<Claim> claims, List<Period> roth, List<Period> rmd) {
        public Chart {
            years = List.copyOf(years); claims = List.copyOf(claims); roth = List.copyOf(roth); rmd = List.copyOf(rmd);
            if (years.size() > MAX_YEARS || claims.size() > MAX_YEARS || roth.size() > MAX_YEARS || rmd.size() > MAX_YEARS)
                throw invalid("Frozen chart exceeds collection bounds.");
        }
    }
    public record Display(BreakEvenPlanSummary people, String primaryName, String spouseName, List<DisplayYear> years, Chart chart,
            int firstYear, int lastYear, boolean referenceIncomplete, long elapsedNanos, String details) {
        public Display { years = List.copyOf(years); Objects.requireNonNull(chart); Objects.requireNonNull(details); if (elapsedNanos < 0) throw invalid("Invalid runtime."); }
    }

    static Optional<FundingFailure> optionalFailure(Failure f) { return Optional.ofNullable(f).map(Failure::restore); }
    static <T> Map<Integer, T> nullableMap(Map<Integer, T> source) { return Collections.unmodifiableMap(new TreeMap<>(source)); }
    static <T> Map<Integer, Optional<T>> optionalMap(Map<Integer, T> source) { return map(source, Optional::ofNullable); }
    static <A, B> Map<Integer, B> map(Map<Integer, A> source, java.util.function.Function<A, B> mapper) {
        var result = new TreeMap<Integer, B>(); source.forEach((k, v) -> result.put(k, mapper.apply(v))); return result;
    }
    private static void checkBudget(long count) { if (count > MAX_ANNUAL_OBSERVATIONS) throw invalid("Expanded annual observation budget exceeded."); }
    static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
