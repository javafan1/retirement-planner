package com.daviddunn.retirementplanner.app.montecarlo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/** DECIMAL128 fractions with the rounding residual assigned to the largest category.
 * Exact counts remain authoritative. Ties for largest use category order; zero counts stay zero.
 * Marginals must be sums of these fractions to preserve exact partition identities. */
final class MonteCarloPairedProbabilities {
    private MonteCarloPairedProbabilities() { }

    static List<BigDecimal> of(int... counts) {
        long total = 0;
        int largest = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] < 0) {
                throw new IllegalArgumentException("Negative population.");
            }
            total += counts[i];
            if (counts[i] > counts[largest]) {
                largest = i;
            }
        }
        if (total == 0) {
            throw new IllegalArgumentException("Probabilities require observations.");
        }
        var probabilities = new ArrayList<BigDecimal>();
        var sum = BigDecimal.ZERO;
        for (int count : counts) {
            var probability = BigDecimal.valueOf(count).divide(BigDecimal.valueOf(total), MathContext.DECIMAL128);
            probabilities.add(probability);
            sum = sum.add(probability);
        }
        probabilities.set(largest, probabilities.get(largest).add(BigDecimal.ONE.subtract(sum)));
        return List.copyOf(probabilities);
    }
}
