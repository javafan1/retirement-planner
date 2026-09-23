package com.daviddunn.retirementplanner.util;

import java.math.BigDecimal;
import java.util.Optional;

/** Shared semantic colors for the JavaFX and vector PDF heat maps. */
public enum ClaimingHeatMapPalette {
    ESSENTIALLY_OPTIMAL("99.5", "Essentially optimal", "heat-map-tier-1", "#85c8bc", "At least 99.5%"),
    VERY_CLOSE("99.0", "Very close", "heat-map-tier-2", "#b7ddd1", "99.0% to below 99.5%"),
    MODERATE("97.0", "Moderate", "heat-map-tier-3", "#e5e4b1", "97.0% to below 99.0%"),
    MEANINGFUL("95.0", "Meaningful", "heat-map-tier-4", "#f0c98a", "95.0% to below 97.0%"),
    SUBSTANTIALLY_WORSE("0", "Substantially worse", "heat-map-tier-5", "#e9a58d", "Below 95.0%"),
    UNAVAILABLE("0", "Not Available", "heat-map-unavailable", "#e5e8ec", "No positive optimum or no successful result");

    public static final String OPTIMAL_BORDER = "#174d43";
    public static final String SELECTED_BORDER = "#173f78";
    private final BigDecimal minimum;
    public final String label;
    public final String css;
    public final String color;
    public final String help;

    ClaimingHeatMapPalette(String minimum, String label, String css, String color, String help) {
        this.minimum = new BigDecimal(minimum);
        this.label = label;
        this.css = css;
        this.color = color;
        this.help = help;
    }

    public static ClaimingHeatMapPalette forPercent(Optional<BigDecimal> percent) {
        if (percent.isEmpty()) return UNAVAILABLE;
        for (var tier : values()) {
            if (tier != UNAVAILABLE && percent.orElseThrow().compareTo(tier.minimum) >= 0) return tier;
        }
        return SUBSTANTIALLY_WORSE;
    }

    public static String cssVariables() {
        StringBuilder style = new StringBuilder();
        for (var tier : values()) style.append('-').append(tier.css).append(": ").append(tier.color).append(';');
        return style + "-heat-map-optimal-border: " + OPTIMAL_BORDER
                + ";-heat-map-selected-border: " + SELECTED_BORDER + ";";
    }
}
