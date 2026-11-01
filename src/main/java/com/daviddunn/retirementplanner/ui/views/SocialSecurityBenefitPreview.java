package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityBenefitCalculator;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

import java.math.RoundingMode;
import java.util.Objects;

/** Shared presentation-only calculation for the own-retirement monthly benefit preview. */
final class SocialSecurityBenefitPreview {
    private SocialSecurityBenefitPreview() { }

    static String explanation(SocialSecurityIncome source) {
        return "Monthly own-retirement benefit in " + source.getBenefitValuationYear()
                + " dollars. The claiming-age adjustment is included. COLA after that valuation year is not applied to this preview. "
                + "This is not the household payment including spousal or survivor benefits, or the deposit after deductions.";
    }

    static void update(Person person, SocialSecurityIncome source, Integer claimingAge, Label target) {
        Objects.requireNonNull(person);
        Objects.requireNonNull(source);
        Objects.requireNonNull(target);
        String help = explanation(source);
        Tooltip tooltip = new Tooltip(help);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(360);
        target.setTooltip(tooltip);
        target.setAccessibleHelp(help);
        if (claimingAge == null || person.getBirthDate() == null) {
            target.setText("-");
            target.setAccessibleText("Monthly own-retirement benefit unavailable.");
            return;
        }
        var amount = SocialSecurityBenefitCalculator.calculateMonthlyBenefit(
                source.getFullRetirementMonthlyBenefit(), person.getBirthDate(), claimingAge)
                .setScale(2, RoundingMode.HALF_UP);
        target.setText(UIFormatters.money(amount));
        target.setAccessibleText("Monthly own-retirement benefit at age " + claimingAge + ": "
                + target.getText() + " in " + source.getBenefitValuationYear() + " dollars.");
    }
}
