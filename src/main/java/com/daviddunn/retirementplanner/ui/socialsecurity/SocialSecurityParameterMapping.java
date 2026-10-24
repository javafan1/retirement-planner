package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterConfiguration.*;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityCompleteStrategySearchRequest;
import java.math.BigDecimal;

/** Effective prepared request values; no results, plan financial copies or calculations. */
final class SocialSecurityParameterMapping {
    static final String WEIGHTED = "couple-weighted-full-v1";
    static final String INDIVIDUAL_SS = "individual-ss-nine-v1";
    static final String INDIVIDUAL_WEIGHTED = "individual-weighted-nine-v1";
    private SocialSecurityParameterMapping() { }
    static Mortality socialSecurity(SocialSecurityStrategyAnalysisContext context) {
        var request = context.request().retirementGridRequest();
        return new Mortality(context.primaryMortalityAdjustment().factor(), context.spouseMortalityAdjustment().factor(),
                request.mortalityBaseDate(), request.presentValueBaseDate(), request.realDiscountRate(),
                "couple-ss-v1:top=" + context.request().settings().topRetirementCandidates(), null);
    }
    static Mortality weighted(LongevityWeightedAnalysisRequestFactory.Snapshot request, Baseline baseline) {
        return new Mortality(request.longevity.primaryAdjustment().factor(), request.longevity.spouseAdjustment().factor(),
                request.longevity.mortalityBaseDate(), request.valuationDate, request.discountRate, WEIGHTED, baseline);
    }
    static Mortality individual(SinglePersonMortalityView.CompletionInput input, boolean integrated) {
        var request = input.mortality().individual().request();
        return new Mortality(request.mortalityAdjustment().factor(), null, request.mortalityBaseDate(),
                input.valuation(), input.rate(), integrated ? INDIVIDUAL_WEIGHTED : INDIVIDUAL_SS, null);
    }
    static Search search(IntegratedSocialSecurityCompleteStrategySearchRequest request) {
        return new Search(request.plan().getHousehold().hasSpouse() ? "couple-full-v1" : "individual-nine-v1",
                request.rankingMeasure().name(), request.detailRetentionCount(), request.primaryDeathYear().map(java.time.Year::getValue).orElse(null));
    }
    static OptionalElection election(String original, CurrentStrategyBaseline.Election effective) {
        if (original == null) return new OptionalElection(OptionalElection.State.NULL, null, effective.source(), null);
        if (original.isBlank()) return new OptionalElection(OptionalElection.State.ABSENT, null, effective.source(), null);
        String bounded = original.length() <= 256 ? original : original.substring(0, 230) + " [text unavailable]";
        if (original.length() > 256) return new OptionalElection(OptionalElection.State.UNAVAILABLE, bounded, effective.source(), null);
        try {
            int age = Integer.parseInt(original.trim());
            if (age < 60) throw new IllegalArgumentException();
            return new OptionalElection(OptionalElection.State.VALUE, bounded, effective.source(), age);
        } catch (RuntimeException invalid) {
            return new OptionalElection(OptionalElection.State.UNAVAILABLE, bounded, effective.source(), null);
        }
    }
    static String text(OptionalElection election) {
        return switch (election.state()) {
            case ABSENT, NULL -> "";
            case VALUE -> election.originalText() == null ? election.age().toString() : election.originalText();
            case UNAVAILABLE -> election.originalText() == null ? "Unavailable" : election.originalText();
        };
    }
    static String percent(BigDecimal rate) { return rate.scaleByPowerOfTen(2).toString(); }
    static void date(javafx.scene.control.DatePicker picker, java.time.LocalDate date) {
        picker.setValue(date);
        var converter = picker.getConverter();
        if (converter == null) converter = new javafx.util.converter.LocalDateStringConverter();
        picker.getEditor().setText(converter.toString(date));
        picker.getProperties().remove("weighted-date-edited");
    }
}
