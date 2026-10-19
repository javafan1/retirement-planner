package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Analyzer-only presentation helpers; no input values, listeners, validation or execution ownership. */
final class AnalyzerConfigurationLayout {
    private AnalyzerConfigurationLayout() { }

    static Label heading(String text) {
        Label label = note(text);
        label.setStyle("-fx-font-weight: bold;");
        label.setAccessibleText(text);
        return label;
    }

    static Label note(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    static VBox field(Label label, Control control) {
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        InputHelp.link(label, control);
        control.setMaxWidth(Double.MAX_VALUE);
        VBox field = new VBox(3, label, control);
        field.setMinWidth(0);
        field.setPrefWidth(270);
        field.setMaxWidth(270);
        return field;
    }

    static FlowPane flow(Node... nodes) {
        FlowPane flow = new FlowPane(12, 8, nodes);
        flow.setMinWidth(0);
        flow.setPrefWrapLength(760);
        return flow;
    }

    static String household(RetirementPlan plan, boolean benefits) {
        return plan.getHousehold().members().stream().map(person -> {
            String text = (person == plan.getHousehold().getPrimaryPerson() ? "Primary: " : "Spouse: ")
                    + person.getFullName() + " — DOB " + (person.getBirthDate() == null ? "Not configured" : person.getBirthDate());
            if (benefits) {
                text += "; mortality category " + (person.getMortalityCategory() == null ? "Not configured" : person.getMortalityCategory());
                var records = person.getIncomeSources().stream().filter(SocialSecurityIncome.class::isInstance)
                        .map(SocialSecurityIncome.class::cast).map(source ->
                                UIFormatters.money(source.getFullRetirementMonthlyBenefit()) + "/month gross FRA benefit"
                                        + "; valuation year " + source.getBenefitValuationYear()
                                        + "; current claiming age " + source.getClaimingAge() + "; claim date " + source.getStartDate()).toList();
                text += "\n" + (records.isEmpty() ? "Social Security record not configured" : String.join("; ", records));
            }
            return text;
        }).collect(java.util.stream.Collectors.joining("\n"));
    }

    static String individualPlan(RetirementPlan plan) {
        var assumptions = plan.getPlanningAssumptions();
        return household(plan, true) + "\nProjection start " + assumptions.getProjectionStartDate()
                + "; configured horizon " + assumptions.getProjectionLengthYears() + " calendar years"
                + "; plan Social Security COLA " + assumptions.getSocialSecurityColaRate().movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }
}
