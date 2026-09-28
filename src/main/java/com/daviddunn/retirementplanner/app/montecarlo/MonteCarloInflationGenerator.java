package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.DiscreteProbabilitySampler.RandomBytes;
import com.daviddunn.retirementplanner.domain.projection.ProjectionInflationPath;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Year;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/**
 * GENERAL_INFLATION_V1: independent annual floored normal rates. Each normal draw
 * consumes two 8-byte big-endian words from dimension 4/version 1. Each word's
 * high 52 bits becomes U=(bits+0.5)*2^-52 (strictly inside (0,1)). Box-Muller uses
 * StrictMath sqrt/log/cos, retaining only the cosine draw (no cached sine).
 * No market correlation, persistence, moment normalization or shared RNG state.
 */
public final class MonteCarloInflationGenerator {
    public static final String MODEL_VERSION = "FLOORED_NORMAL_GENERAL_INFLATION_V1";

    public Optional<ProjectionInflationPath> generate(
            int firstYear, int lastYear, MonteCarloSettings settings, int scenarioIndex) {
        Objects.requireNonNull(settings);
        if (scenarioIndex < 0) {
            throw new IllegalArgumentException("Scenario index cannot be negative.");
        }
        return settings.inflation().map(inflation -> generate(firstYear, lastYear, inflation,
                MonteCarloRandomStreams.create(settings.seed(), scenarioIndex,
                        MonteCarloRandomStreams.GENERAL_INFLATION, MonteCarloRandomStreams.GENERAL_INFLATION_V1)));
    }

    public ProjectionInflationPath generate(
            int firstYear, int lastYear, MonteCarloInflationSettings settings, RandomBytes random) {
        Year.of(firstYear);
        Year.of(lastYear);
        if (lastYear < firstYear) {
            throw new IllegalArgumentException("Invalid inflation horizon.");
        }
        Objects.requireNonNull(settings);
        Objects.requireNonNull(random);
        var years = new ArrayList<ProjectionInflationPath.AnnualRate>();
        byte[] bytes = new byte[16];
        for (int year = firstYear; year <= lastYear; year++) {
            BigDecimal rate = settings.expectedInflationRate();
            if (settings.inflationVolatility().signum() != 0) {
                random.nextBytes(bytes);
                var words = ByteBuffer.wrap(bytes);
                double u = ((words.getLong() >>> 12) + 0.5) * 0x1.0p-52;
                double v = ((words.getLong() >>> 12) + 0.5) * 0x1.0p-52;
                double z = StrictMath.sqrt(-2 * StrictMath.log(u)) * StrictMath.cos(2 * StrictMath.PI * v);
                double sampled = settings.expectedInflationRate().doubleValue()
                        + settings.inflationVolatility().doubleValue() * z;
                if (!Double.isFinite(sampled)) {
                    throw new IllegalArgumentException("Non-finite generated inflation.");
                }
                rate = BigDecimal.valueOf(sampled).max(settings.minimumInflationRate());
            }
            years.add(new ProjectionInflationPath.AnnualRate(year, rate));
        }
        return new ProjectionInflationPath(years);
    }
}
