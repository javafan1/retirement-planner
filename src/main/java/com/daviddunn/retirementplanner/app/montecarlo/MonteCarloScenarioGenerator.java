package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.ProjectionEconomicPath;

import java.math.BigDecimal;
import java.util.*;

/**
 * V1 independent lognormal gross returns, matched to arithmetic simple-return moments.
 * Each indexed scenario has its own java.util.Random stream; execution order, failures,
 * simulation count and plan contents cannot affect another scenario's draws.
 */
public final class MonteCarloScenarioGenerator {
    public static final String MODEL_VERSION = "LOGNORMAL_ARITHMETIC_MOMENTS_V1";

    @FunctionalInterface
    public interface GaussianSource {
        double nextGaussian();
    }

    public ProjectionEconomicPath generate(int firstYear, int lastYear, MonteCarloSettings settings, int scenarioIndex) {
        if (scenarioIndex < 0) {
            throw new IllegalArgumentException("Scenario index cannot be negative.");
        }
        // Intentional 64-bit wraparound is part of the reproducible V1 stream definition.
        var random = new Random(settings.seed() + 0x9E3779B97F4A7C15L * scenarioIndex);
        return generate(firstYear, lastYear, settings, random::nextGaussian);
    }

    public ProjectionEconomicPath generate(int firstYear, int lastYear, MonteCarloSettings settings, GaussianSource random) {
        java.time.Year.of(firstYear);
        java.time.Year.of(lastYear);
        if (lastYear < firstYear) {
            throw new IllegalArgumentException("Invalid scenario horizon.");
        }
        Objects.requireNonNull(settings);
        Objects.requireNonNull(random);
        Map<Integer, BigDecimal> path = new LinkedHashMap<>();
        double mean = settings.expectedReturn().doubleValue();
        double ratio = settings.returnVolatility().doubleValue() / (1 + mean);
        double varianceLog = StrictMath.log1p(ratio * ratio);
        double sigmaLog = StrictMath.sqrt(varianceLog);
        double meanLog = StrictMath.log1p(mean) - varianceLog / 2;
        for (int year = firstYear; year <= lastYear; year++) {
            BigDecimal value;
            if (settings.returnVolatility().signum() == 0) {
                value = settings.expectedReturn();
            } else {
                double draw = random.nextGaussian();
                double rate = StrictMath.expm1(meanLog + sigmaLog * draw);
                if (!Double.isFinite(draw) || !Double.isFinite(rate) || rate < -1) {
                    throw new IllegalArgumentException("Non-finite or invalid generated return.");
                }
                // Sole floating-point boundary. Financial execution remains BigDecimal.
                value = BigDecimal.valueOf(rate);
            }
            path.put(year, value);
        }
        return ProjectionEconomicPath.annual(path);
    }
}