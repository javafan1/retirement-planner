package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/** Text shared by the screen and the report adapter; no JavaFX controls. */
final class ClaimingHeatMapFormatting {
    static String format(ClaimingStrategyHeatMapMetric metric, Optional<BigDecimal> value) {
        return value.map(amount -> metric == ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL
                ? amount.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%"
                : ((metric == ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_OPTIMAL
                        || metric == ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT) && amount.signum() > 0 ? "+" : "")
                        + UIFormatters.money(amount)).orElse("Not Available");
    }
}
