package com.daviddunn.retirementplanner.domain.tax.state;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.ArkansasLowIncomePolicy;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;

import java.time.Year;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Optional observation on the controlled worker. Only allowlisted classifications reach the sink. */
public final class ArkansasRecipientDiagnostic {
    private static final int MAX_RECORDS_PER_RUN = 4;
    private static final ThreadLocal<Capture> CURRENT = new ThreadLocal<>();

    public enum DeathYear { NO_MODELED_DEATH, BEFORE_FIRST_DEATH, FIRST_DEATH, SURVIVOR, UNKNOWN }
    public enum Pass { INITIAL, MEDICARE_AWARE, UNKNOWN }
    public enum RothPath { FIXED_OR_NONE, TARGETED, UNKNOWN }
    enum Stage { EXACT_PRICING, FUNDING_INTERVAL, TARGET_BOUND }
    private enum Candidate { ZERO, POSITIVE, UNKNOWN }

    public interface Scope extends AutoCloseable {
        @Override void close();
    }

    private record Annual(DeathYear deathYear, FinancialCalculationMode mode,
                          ArkansasLowIncomePolicy policy, Pass pass, RothPath rothPath) { }

    private static final class Capture {
        private final AnalysisCancellationToken cancellation;
        private final Consumer<String> sink;
        private final Set<String> recorded = new HashSet<>();
        private Annual annual;
        private Candidate candidate = Candidate.UNKNOWN;

        private Capture(AnalysisCancellationToken cancellation, Consumer<String> sink) {
            this.cancellation = cancellation;
            this.sink = sink;
        }
    }

    private ArkansasRecipientDiagnostic() { }

    public static <T> T capture(AnalysisCancellationToken cancellation, Consumer<String> sink, Supplier<T> work) {
        var previous = CURRENT.get();
        CURRENT.set(new Capture(cancellation, sink));
        try {
            return work.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static boolean isEnabled() {
        return CURRENT.get() != null;
    }

    public static Scope annual(Optional<Year> firstDeath, int year,
                               FinancialCalculationMode mode, ArkansasLowIncomePolicy policy,
                               Pass pass, RothPath rothPath) {
        var capture = CURRENT.get();
        if (capture == null) return null;
        var deathYear = firstDeath.map(death -> {
            int compare = Integer.compare(year, death.getValue());
            if (compare < 0) return DeathYear.BEFORE_FIRST_DEATH;
            return compare == 0 ? DeathYear.FIRST_DEATH : DeathYear.SURVIVOR;
        }).orElse(DeathYear.NO_MODELED_DEATH);
        var previous = capture.annual;
        capture.annual = new Annual(deathYear, mode, policy, pass, rothPath);
        return () -> capture.annual = previous;
    }

    public static Scope exactFunding(boolean positive) {
        var capture = CURRENT.get();
        if (capture == null) return null;
        var previous = capture.candidate;
        capture.candidate = positive ? Candidate.POSITIVE : Candidate.ZERO;
        return () -> capture.candidate = previous;
    }

    static void rejected(AnnualIncomeTaxFacts request, IncomeSourceAttribution source, Stage stage) {
        var capture = CURRENT.get();
        if (capture == null || capture.recorded.size() >= MAX_RECORDS_PER_RUN) return;
        try {
            if (capture.cancellation.isCancellationRequested()) return;
            var annual = capture.annual;
            var activeOwners = request.household().activeOwners().stream().sorted().map(Enum::name)
                    .collect(Collectors.joining(","));
            var failure = """
                    phase=P3D3C4A
                    year=%d
                    filing=%s
                    activeOwners=%s
                    receiptOwner=%s
                    receiptKind=%s
                    receiptPurpose=%s
                    accountType=%s
                    conditionalTrigger=%s
                    deathYear=%s
                    mode=%s
                    policy=%s
                    pass=%s
                    rothPath=%s
                    stage=%s
                    candidate=%s
                    reason=UNSUPPORTED_MODEL_RULE
                    category=ATTRIBUTION
                    issue=SOURCE_OR_TAX
                    """.formatted(request.taxYear(), request.filingStatus().name(),
                    activeOwners.isEmpty() ? "NONE" : activeOwners,
                    source.owner().orElseThrow().name(), source.kind().name(), source.purpose().name(),
                    source.account().map(account -> account.type().name()).orElse("NOT_APPLICABLE"),
                    source.conditionalBenefit().map(receipt -> receipt.trigger().type().name()).orElse("NOT_APPLICABLE"),
                    annual == null ? DeathYear.UNKNOWN.name() : annual.deathYear().name(),
                    annual == null || annual.mode() == null ? "UNKNOWN" : annual.mode().name(),
                    annual == null ? "UNKNOWN" : annual.policy().name(),
                    annual == null ? Pass.UNKNOWN.name() : annual.pass().name(),
                    annual == null ? RothPath.UNKNOWN.name() : annual.rothPath().name(),
                    stage.name(), (stage == Stage.EXACT_PRICING ? capture.candidate : Candidate.UNKNOWN).name());
            if (capture.recorded.add(failure)) capture.sink.accept(failure);
        } catch (RuntimeException ignored) {
            // Diagnostics cannot replace the existing tax rejection.
        }
    }
}
