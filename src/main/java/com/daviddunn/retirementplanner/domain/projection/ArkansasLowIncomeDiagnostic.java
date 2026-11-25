package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue;

/** Completed refusal evidence, with categories only. It is never part of a projection result. */
public record ArkansasLowIncomeDiagnostic(int year, FilingStatus filingStatus, DeathYear deathYear,
        FinancialCalculationMode mode, RothPath rothPath, Pass pass, boolean lifePrincipalRecognized,
        ArkansasTaxIssue.Bound section301Gross, ArkansasTaxIssue.Bound section501Net,
        ArkansasTaxIssue.ExactFundingProbe fundingProbe, ArkansasProjectionUnavailableException.Reason reason) {

    public enum DeathYear { NO_MODELED_DEATH, BEFORE_FIRST_DEATH, FIRST_DEATH_YEAR, LATER_SURVIVOR_YEAR }
    public enum RothPath { FIXED, TARGET, TARGET_FALLBACK_FIXED }
    public enum Pass { INITIAL, MEDICARE_AWARE }
    public enum Stage { INNER_EXACT }

    public Stage stage() { return Stage.INNER_EXACT; }

    public String safeText() {
        return """
                phase=P3D3C2
                status=COMPLETE_REFUSAL
                year=%d
                filing=%s
                deathYear=%s
                mode=%s
                rothPath=%s
                pass=%s
                stage=%s
                probe=EXACT
                candidate=EXPLORATORY
                fundingWithdrawal=%s
                pointFederalCash=%s
                lifePrincipalRecognized=%s
                section301Gross=%s
                section501Net=%s
                reason=%s
                issue=LOW_INCOME
                """.formatted(year, filingStatus.name(), deathYear, mode.name(), rothPath, pass, stage(),
                fundingProbe.withdrawal(), fundingProbe.cash(), lifePrincipalRecognized, section301Gross, section501Net,
                reason);
    }
}
