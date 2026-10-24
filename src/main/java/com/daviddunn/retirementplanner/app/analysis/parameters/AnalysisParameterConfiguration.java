package com.daviddunn.retirementplanner.app.analysis.parameters;

import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Accepted effective inputs only. No financial result graphs or editable drafts. */
public record AnalysisParameterConfiguration(int configurationVersion, AnalysisKind kind,
        AnalysisKind.Shape shape, UUID acceptedRunId, Instant acceptedAt,
        Parameters parameters, Map<String, String> provenance) {
    public sealed interface Parameters permits MonteCarlo, Mortality, Quick, Search { }

    public record Inflation(String mode, BigDecimal meanRate, BigDecimal volatilityRate, BigDecimal floorRate) {
        public Inflation {
            if ("DETERMINISTIC".equals(mode)) {
                if (meanRate != null || volatilityRate != null || floorRate != null) throw invalid();
            } else if ("STOCHASTIC".equals(mode)) {
                range(meanRate, "-0.99", "1"); range(volatilityRate, "0", "1"); range(floorRate, "-0.99", "1");
                if (floorRate.compareTo(meanRate) > 0) throw invalid();
            } else throw invalid();
        }
    }

    public record MonteCarlo(int simulations, long seed, BigDecimal expectedReturnRate,
            BigDecimal returnVolatilityRate, Inflation inflation, BigDecimal primaryMortalityFactor,
            BigDecimal spouseMortalityFactor, Integer currentPlanSurvivorClaimingAge,
            Integer baselineSurvivorClaimingAge) implements Parameters {
        public MonteCarlo {
            if (simulations < 1 || simulations > 10_000) throw invalid();
            range(expectedReturnRate, "-0.99", "1"); range(returnVolatilityRate, "0", "1");
            Objects.requireNonNull(inflation);
            if (primaryMortalityFactor != null) range(primaryMortalityFactor, ".50", "3");
            if (spouseMortalityFactor != null) range(spouseMortalityFactor, ".50", "3");
            age(currentPlanSurvivorClaimingAge); age(baselineSurvivorClaimingAge);
        }
    }

    /** Invalid optional baseline text is descriptive provenance, never a parsed election. */
    public record OptionalElection(State state, String originalText, String source, Integer age) {
        public enum State { ABSENT, NULL, VALUE, UNAVAILABLE }
        public OptionalElection {
            Objects.requireNonNull(state);
            bounded(originalText, 256); bounded(source, 256);
            if ((state == State.VALUE) != (age != null)) throw invalid();
            AnalysisParameterConfiguration.age(age);
            if (state == State.VALUE && originalText != null && Integer.parseInt(originalText.trim()) != age) throw invalid();
            if ((state == State.ABSENT || state == State.NULL) && (originalText != null || age != null)) throw invalid();
        }
    }

    public record Baseline(OptionalElection primary, OptionalElection spouse) {
        public Baseline { Objects.requireNonNull(primary); Objects.requireNonNull(spouse); }
    }

    public record Mortality(BigDecimal primaryFactor, BigDecimal spouseFactor,
            LocalDate conditioningDate, LocalDate valuationDate, BigDecimal realDiscountRate,
            String optimizerProfile, Baseline baseline) implements Parameters {
        public Mortality {
            number(primaryFactor); if (primaryFactor.signum() <= 0) throw invalid();
            if (spouseFactor != null) { number(spouseFactor); if (spouseFactor.signum() <= 0) throw invalid(); }
            Objects.requireNonNull(conditioningDate); Objects.requireNonNull(valuationDate);
            number(realDiscountRate); if (realDiscountRate.compareTo(BigDecimal.ONE.negate()) <= 0) throw invalid();
            bounded(Objects.requireNonNull(optimizerProfile), 128);
        }
    }

    public record Quick(int candidateCount, String prerequisiteFingerprint) implements Parameters {
        public Quick {
            if (candidateCount < 1 || candidateCount > 20 || prerequisiteFingerprint == null
                    || !prerequisiteFingerprint.matches("[0-9a-f]{64}")) throw invalid();
        }
    }

    public record Search(String profile, String rankingMeasure, int retainedDetails,
            Integer primaryDeathYear) implements Parameters {
        public Search {
            bounded(Objects.requireNonNull(profile), 128); bounded(Objects.requireNonNull(rankingMeasure), 128);
            if (retainedDetails < 0 || retainedDetails > 100) throw invalid();
        }
    }

    public AnalysisParameterConfiguration {
        if (configurationVersion != 1) throw invalid();
        Objects.requireNonNull(kind); Objects.requireNonNull(shape); Objects.requireNonNull(acceptedRunId);
        Objects.requireNonNull(acceptedAt); Objects.requireNonNull(parameters);
        if (kind.shape() != shape) throw invalid();
        provenance = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(Objects.requireNonNull(provenance)));
        if (provenance.size() > 16) throw invalid();
        provenance.forEach((key, value) -> { bounded(Objects.requireNonNull(key), 64); bounded(Objects.requireNonNull(value), 512); });
        if (kind.isMonteCarlo()) {
            if (!(parameters instanceof MonteCarlo mc)) throw invalid();
            boolean longevity = kind.name().endsWith("LONGEVITY");
            boolean paired = kind.name().contains("PAIRED");
            if (longevity != (mc.primaryMortalityFactor() != null)) throw invalid();
            if ((shape == AnalysisKind.Shape.SINGLE || !longevity) && (mc.spouseMortalityFactor() != null
                    || mc.currentPlanSurvivorClaimingAge() != null || mc.baselineSurvivorClaimingAge() != null)) throw invalid();
            if (shape == AnalysisKind.Shape.COUPLE && longevity && mc.spouseMortalityFactor() == null) throw invalid();
            if (!paired && mc.baselineSurvivorClaimingAge() != null) throw invalid();
        } else if (kind == AnalysisKind.COUPLE_QUICK) {
            if (!(parameters instanceof Quick)) throw invalid();
        } else if (kind == AnalysisKind.COUPLE_EXHAUSTIVE || kind == AnalysisKind.INDIVIDUAL_DETERMINISTIC) {
            if (!(parameters instanceof Search search) || kind == AnalysisKind.COUPLE_EXHAUSTIVE && search.primaryDeathYear() != null) throw invalid();
        } else {
            if (!(parameters instanceof Mortality mortality)) throw invalid();
            if ((shape == AnalysisKind.Shape.COUPLE) != (mortality.spouseFactor() != null)) throw invalid();
            if (shape == AnalysisKind.Shape.COUPLE) {
                range(mortality.primaryFactor(), ".50", "3"); range(mortality.spouseFactor(), ".50", "3");
            }
            if (kind != AnalysisKind.COUPLE_WEIGHTED && mortality.baseline() != null) throw invalid();
        }
    }

    static void number(BigDecimal value) {
        Objects.requireNonNull(value);
        if (value.precision() > 128 || Math.abs((long) value.scale()) > 128) throw invalid();
    }
    private static void range(BigDecimal value, String low, String high) {
        number(value);
        if (value.compareTo(new BigDecimal(low)) < 0 || value.compareTo(new BigDecimal(high)) > 0) throw invalid();
    }
    private static void age(Integer value) { if (value != null && value < 60) throw invalid(); }
    static void bounded(String value, int limit) { if (value != null && value.length() > limit) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid accepted analysis parameter configuration."); }
}
