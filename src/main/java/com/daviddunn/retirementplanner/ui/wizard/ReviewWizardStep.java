package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.ui.dialogs.OpeningRmdDialog;
import com.daviddunn.retirementplanner.ui.rmd.OpeningRmdWorkflowService;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import com.daviddunn.retirementplanner.util.CurrencyFormatter;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/** Presentation of current draft values, never a second projection or financial model. */
public final class ReviewWizardStep implements NewPlanWizardStep {

    private final NewPlanDraft draft;
    private final VBox content = new VBox(10);
    private final Label error = new Label();
    private IntConsumer editStep = index -> { };

    public ReviewWizardStep(NewPlanDraft draft) {
        this.draft = java.util.Objects.requireNonNull(draft);
        error.setId("wizard-review-error");
        error.getStyleClass().add("wizard-field-error");
        error.setWrapText(true);
        error.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());
    }

    void setEditStep(IntConsumer editStep) { this.editStep = editStep; }

    @Override
    public String title() { return "Review"; }

    @Override
    public Node content() { return content; }

    @Override
    public void onEntering() {
        var plan = draft.getPlan();
        var assumptions = plan.getPlanningAssumptions();
        var economic = assumptions.getEconomicAssumptions();
        content.getChildren().clear();
        Label intro = wrapped("Review your initial plan. Create opens the existing editing tabs; it does not save a file.");
        content.getChildren().addAll(intro, error);
        validateAndApply();
        List<String> warnings = new ArrayList<>();
        if (plan.getAccountPortfolio().getAccounts().isEmpty()) warnings.add("No accounts entered. Confirm how spending will be funded.");
        if (plan.getHousehold().getExpenses().isEmpty()) warnings.add("No retirement spending entered. Add expenses when you are ready.");
        OpeningRmdWorkflowService rmd = new OpeningRmdWorkflowService();
        var missing = rmd.getApplicableAccounts(plan).stream().filter(row -> row.account().getOpeningRmdAccountData() == null
                || row.account().getOpeningRmdAccountData().getDistributionYear() != assumptions.getProjectionStartDate().getYear()).toList();
        if (!missing.isEmpty()) {
            warnings.add("Opening RMD history is needed before projecting: " + missing.stream()
                    .map(row -> row.account().getName()).collect(Collectors.joining(", ")) + ".");
        }
        if (!warnings.isEmpty()) {
            Label notice = wrapped("Review reminders (do not prevent creation)\n" + String.join("\n", warnings));
            notice.setId("wizard-review-warnings");
            notice.getStyleClass().add("wizard-review-warning");
            content.getChildren().add(notice);
        }
        if (rmd.isRequired(plan)) {
            Button historical = new Button("Opening RMD Information…");
            historical.setId("wizard-opening-rmd");
            com.daviddunn.retirementplanner.ui.controls.InputHelp.install(historical,
                    com.daviddunn.retirementplanner.ui.help.PlanningInputHelp.OPENING_RMD);
            historical.setOnAction(event -> {
                var dialog = new OpeningRmdDialog(plan);
                if (content.getScene() != null) dialog.initOwner(content.getScene().getWindow());
                dialog.showAndWait().ifPresent(saved -> onEntering());
            });
            content.getChildren().add(historical);
        }
        String household = (plan.getHousehold().hasSpouse() ? "Couple household" : "Single-person household") + "\n"
                + plan.getHousehold().peopleByOwner().entrySet().stream().map(entry -> {
                    var person = entry.getValue();
                    return AccountsWizardStep.displayOwner(entry.getKey()) + ": " + person.getFullName().trim()
                            + " — DOB " + person.getBirthDate() + ", "
                            + (person.getMortalityCategory() == null ? "category not entered" : person.getMortalityCategory().name().toLowerCase(java.util.Locale.ROOT));
                }).collect(Collectors.joining("\n"));
        section("Household", 0, household);
        section("Accounts", 1, plan.getAccountPortfolio().getAccounts().stream().map(account ->
                account.getName() + " — " + account.getType() + ", " + AccountsWizardStep.displayOwner(account.getOwnership())
                        + ", " + CurrencyFormatter.format(account.getCurrentBalance())).collect(Collectors.joining("\n")));
        section("Income", 2, plan.getHousehold().members().stream().flatMap(person -> person.getIncomeSources().stream()).map(income -> {
            String owner = AccountsWizardStep.displayOwner(income.getOwnership());
            if (income instanceof SocialSecurityIncome ss) return owner + " Social Security: FRA "
                    + CurrencyFormatter.format(ss.getFullRetirementMonthlyBenefit()) + "/month (" + ss.getBenefitValuationYear()
                    + " dollars), claim at " + ss.getClaimingAge() + ", starts " + ss.getStartDate();
            Pension pension = (Pension) income;
            return owner + ": " + pension.getName() + ", " + CurrencyFormatter.format(pension.getMonthlyBenefit())
                    + "/month from " + pension.getStartDate() + ", COLA " + UIFormatters.percent(pension.getAnnualColaRate())
                    + (pension.getEndDate() == null ? "" : ", ends " + pension.getEndDate())
                    + (pension.getSurvivorMonthlyBenefit() == null ? "" : ", survivor " + CurrencyFormatter.format(pension.getSurvivorMonthlyBenefit()) + "/month");
        }).collect(Collectors.joining("\n")));
        section("Expenses", 3, plan.getHousehold().getExpenses().stream().map(expense -> expense.getDescription() + ": "
                + CurrencyFormatter.format(expense.getAnnualAmount()) + (expense.isOneTimeExpense() ? " purchase" : "/year")
                + ", " + (expense.isHealthcareExpense() && !expense.isOneTimeExpense() ? "Healthcare" : "General") + " inflation"
                + (expense.getStartDate() == null ? "" : ", starts " + expense.getStartDate())
                + (expense.getEndDate() == null || expense.isOneTimeExpense() ? "" : ", ends " + expense.getEndDate()))
                .collect(Collectors.joining("\n")));
        section("Assumptions", 4, "Starts " + assumptions.getProjectionStartDate() + "; " + assumptions.getProjectionLengthYears()
                + " years through " + (assumptions.getProjectionStartDate().getYear() + assumptions.getProjectionLengthYears() - 1)
                + "\nInvestment return " + UIFormatters.percent(economic.getExpectedAnnualInvestmentReturn())
                + "; inflation " + UIFormatters.percent(economic.getGeneralInflationRate())
                + "; healthcare " + UIFormatters.percent(economic.getHealthcareInflationRate())
                + "; Social Security COLA " + UIFormatters.percent(economic.getSocialSecurityColaRate())
                + "\nFiling status: " + assumptions.getTaxAssumptions().getFilingStatus());
    }

    private void section(String title, int index, String text) {
        Label heading = new Label(title.toUpperCase(java.util.Locale.ROOT));
        heading.getStyleClass().add("wizard-person-heading");
        heading.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(heading, Priority.ALWAYS);
        Button edit = new Button("Edit");
        edit.setId("wizard-review-edit-" + title.toLowerCase(java.util.Locale.ROOT));
        edit.setOnAction(event -> editStep.accept(index));
        HBox header = new HBox(10, heading, edit);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox card = new VBox(6, header, wrapped(text.isBlank() ? "None entered" : text));
        card.getStyleClass().add("wizard-review-section");
        content.getChildren().add(card);
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return label;
    }

    @Override
    public boolean validateAndApply() {
        try {
            draft.complete();
            error.setText("");
            return true;
        }
        catch (IllegalArgumentException exception) {
            error.setText("Cannot create this plan: " + exception.getMessage());
            return false;
        }
    }
}
