package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.income.SurvivorBenefitClaimingPolicy;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.controls.HelpIcon;
import com.daviddunn.retirementplanner.ui.help.HelpText;
import javafx.beans.binding.Bindings;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.util.StringConverter;

/** Shared presentation of the domain's whole-year survivor election choices. */
final class SurvivorBenefitClaimingControls {
    private SurvivorBenefitClaimingControls() {
    }

    static void update(RetirementPlan plan, DeathScenario scenario, String yearText,
                       ComboBox<Integer> ages, Label label) {
        Integer year;
        try {
            year = Integer.valueOf(yearText.trim());
        } catch (NumberFormatException invalid) {
            year = null;
        }
        var choices = plan == null ? null : SurvivorBenefitClaimingPolicy.choices(
                plan.getHousehold(), scenario, year);
        String name = choices == null || choices.survivor() == null ? null : choices.survivor().getFirstName();
        label.setText((name == null || name.isBlank() ? "" : name + " — ") + "Survivor Benefit Claiming Age");
        var available = choices == null ? java.util.List.<Integer>of() : choices.ages();
        Integer selected = ages.getValue();
        if (!ages.getItems().equals(available)) {
            ages.getItems().setAll(available);
        }
        boolean immediate = choices != null && choices.immediateAtDeath();
        ages.setConverter(new StringConverter<>() {
            @Override public String toString(Integer age) {
                return age == null ? "" : immediate ? "Immediate at death (Age " + age + ")" : age.toString();
            }
            @Override public Integer fromString(String value) {
                return Integer.valueOf(value);
            }
        });
        Integer appliedAge = plan == null ? null : plan.getPlanningAssumptions()
                .getDeathScenarioAssumptions().getSurvivorClaimingAge();
        ages.setValue(selected != null && available.contains(selected) ? selected
                : appliedAge != null && available.contains(appliedAge) ? appliedAge
                : available.isEmpty() ? null : available.getFirst());
        ages.setDisable(available.isEmpty() || immediate);
        ages.setPromptText(scenario == DeathScenario.BOTH_SURVIVE ? "Not Applicable" : "Enter a valid death year");
        String context = choices == null || choices.survivorFullRetirementDate() == null ? ""
                : " Survivor FRA date: " + choices.survivorFullRetirementDate() + ". Plan choices use whole years.";
        var tooltip = HelpIcon.createTooltip(HelpText.SURVIVOR_BENEFIT_CLAIMING_AGE + context);
        tooltip.textProperty().bind(Bindings.createStringBinding(() -> {
            String selectedValue = ages.getConverter().toString(ages.getValue());
            return (selectedValue.isEmpty() ? "" : selectedValue + "\n\n")
                    + HelpText.SURVIVOR_BENEFIT_CLAIMING_AGE + context;
        }, ages.valueProperty(), ages.converterProperty()));
        ages.setTooltip(tooltip);
        // The label also supplies help when the immediate-at-death control is disabled.
        label.setTooltip(tooltip);
    }
}
