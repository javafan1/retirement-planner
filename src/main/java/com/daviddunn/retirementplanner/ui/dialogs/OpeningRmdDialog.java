package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.rmd.AccountRmd;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdCalculation;
import com.daviddunn.retirementplanner.domain.rmd.OwnerRmdResult;
import com.daviddunn.retirementplanner.domain.rmd.RmdAccountCategory;
import com.daviddunn.retirementplanner.ui.rmd.OpeningRmdWorkflowService;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects historical first-year RMD facts and displays calculations
 * produced by OpeningRmdWorkflowService.
 */
public final class OpeningRmdDialog extends Dialog<Boolean> {

    private final RetirementPlan plan;
    private final OpeningRmdWorkflowService workflowService;
    private final List<OpeningRmdWorkflowService.OpeningRmdAccountRow> rows;
    private final Map<Account, TextField> priorBalanceFields =
            new LinkedHashMap<>();
    private final Map<Account, TextField> distributedFields =
            new LinkedHashMap<>();
    private final Map<Account, CalculatedLabels> calculatedLabels =
            new LinkedHashMap<>();
    private final Label statusLabel = new Label();

    public OpeningRmdDialog(
            RetirementPlan plan) {

        this(plan, new OpeningRmdWorkflowService());
    }

    OpeningRmdDialog(
            RetirementPlan plan,
            OpeningRmdWorkflowService workflowService) {

        this.plan = plan;
        this.workflowService = workflowService;
        this.rows = workflowService.getApplicableAccounts(plan);

        int distributionYear = workflowService.getDistributionYear(plan);

        setTitle("Opening RMD Information — " + distributionYear);
        setHeaderText("Enter the prior December 31 balance and any RMD "
                + "already distributed before the projection began.");

        VBox content = new VBox(12);
        content.setPadding(new Insets(15));

        if (workflowService.hasStaleData(plan)) {
            Label staleLabel = new Label(
                    "Existing opening RMD information is for a different "
                            + "projection year. Review and save replacement "
                            + "historical values before running the projection.");
            staleLabel.setWrapText(true);
            content.getChildren().add(staleLabel);
        }

        AccountOwnership currentOwner = null;

        for (OpeningRmdWorkflowService.OpeningRmdAccountRow row : rows) {
            if (row.ownership() != currentOwner) {
                currentOwner = row.ownership();
                Label ownerHeading = new Label(row.ownerName());
                ownerHeading.setStyle("-fx-font-weight: bold;");
                content.getChildren().addAll(ownerHeading, new Separator());
            }

            content.getChildren().add(createAccountPane(row, distributionYear));
        }

        statusLabel.setWrapText(true);
        content.getChildren().add(statusLabel);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportWidth(620);
        scrollPane.setPrefViewportHeight(560);

        getDialogPane().setContent(scrollPane);
        getDialogPane().getButtonTypes().addAll(
                ButtonType.CANCEL,
                ButtonType.OK);

        Button saveButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
        saveButton.setText("Save");
        saveButton.addEventFilter(ActionEvent.ACTION, event -> save(event));

        setResultConverter(button ->
                button == ButtonType.OK ? Boolean.TRUE : null);

        refreshCalculation();
    }

    private VBox createAccountPane(
            OpeningRmdWorkflowService.OpeningRmdAccountRow row,
            int distributionYear) {

        Account account = row.account();
        VBox pane = new VBox(6);
        pane.setPadding(new Insets(8, 0, 10, 10));

        Label accountHeading = new Label(
                account.getType() + " — " + account.getName());
        accountHeading.setStyle("-fx-font-weight: bold;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        TextField priorBalanceField = new TextField();
        priorBalanceField.setAlignment(Pos.CENTER_RIGHT);
        priorBalanceField.setPromptText("0.00");

        TextField distributedField = new TextField();
        distributedField.setAlignment(Pos.CENTER_RIGHT);
        distributedField.setPromptText("0.00");

        if (account.getOpeningRmdAccountData() != null
                && account.getOpeningRmdAccountData().getDistributionYear()
                == distributionYear) {
            priorBalanceField.setText(account.getOpeningRmdAccountData()
                    .getPriorDecember31Balance().toPlainString());
            distributedField.setText(account.getOpeningRmdAccountData()
                    .getRmdAlreadyDistributedBeforeProjection().toPlainString());
        }

        priorBalanceFields.put(account, priorBalanceField);
        distributedFields.put(account, distributedField);

        grid.add(new Label("Prior Dec. 31, " + (distributionYear - 1)
                + " balance:"), 0, 0);
        grid.add(priorBalanceField, 1, 0);

        grid.add(new Label("RMD already distributed in " + distributionYear
                + ":"), 0, 1);
        grid.add(distributedField, 1, 1);

        if (plan.getPlanningAssumptions().getProjectionStartDate()
                .getMonthValue() == 1
                && plan.getPlanningAssumptions().getProjectionStartDate()
                .getDayOfMonth() == 1) {
            Button useCurrentBalanceButton = new Button(
                    "Use current account balance as Dec. 31 balance");
            useCurrentBalanceButton.setOnAction(event -> {
                priorBalanceField.setText(
                        account.getCurrentBalance().toPlainString());
                refreshCalculation();
            });
            grid.add(useCurrentBalanceButton, 1, 2);
        }

        CalculatedLabels labels = new CalculatedLabels(
                new Label(), new Label(), new Label());
        calculatedLabels.put(account, labels);

        int calculatedRow = plan.getPlanningAssumptions().getProjectionStartDate()
                .getMonthValue() == 1
                && plan.getPlanningAssumptions().getProjectionStartDate()
                .getDayOfMonth() == 1 ? 3 : 2;

        grid.add(new Label(getAnnualLabel(account) + ":"), 0, calculatedRow);
        grid.add(labels.annual(), 1, calculatedRow++);
        grid.add(new Label("Already distributed:"), 0, calculatedRow);
        grid.add(labels.distributed(), 1, calculatedRow++);
        grid.add(new Label(getRemainingLabel(account) + ":"), 0, calculatedRow);
        grid.add(labels.remaining(), 1, calculatedRow);

        priorBalanceField.textProperty().addListener(
                (observable, oldValue, newValue) -> refreshCalculation());
        distributedField.textProperty().addListener(
                (observable, oldValue, newValue) -> refreshCalculation());

        pane.getChildren().addAll(accountHeading, grid);
        return pane;
    }

    private void refreshCalculation() {

        try {
            OpeningRmdCalculation calculation = workflowService.preview(
                    plan, readInputs());

            for (OpeningRmdWorkflowService.OpeningRmdAccountRow row : rows) {
                updateCalculatedLabels(row, calculation);
            }

            statusLabel.setText("");
        } catch (RuntimeException ex) {
            clearCalculatedLabels();
            statusLabel.setText("Enter non-negative monetary amounts for "
                    + "each applicable account to calculate its RMD.");
        }
    }

    private void updateCalculatedLabels(
            OpeningRmdWorkflowService.OpeningRmdAccountRow row,
            OpeningRmdCalculation calculation) {

        Account account = row.account();
        OwnerRmdResult annualOwnerResult = ownerResult(
                calculation.getAnnualRequirement(), row.ownership());
        OwnerRmdResult remainingOwnerResult = ownerResult(
                calculation.getRemainingRequirement(), row.ownership());

        BigDecimal annual;
        BigDecimal remaining;
        BigDecimal distributed;

        if (account.getType().getRmdAccountCategory()
                == RmdAccountCategory.IRA) {
            annual = annualOwnerResult.getIraRmd();
            remaining = remainingOwnerResult.getIraRmd();
            distributed = rows.stream()
                    .filter(candidate -> candidate.ownership()
                            == row.ownership())
                    .map(OpeningRmdWorkflowService.OpeningRmdAccountRow::account)
                    .filter(candidate -> candidate.getType()
                            .getRmdAccountCategory()
                            == RmdAccountCategory.IRA)
                    .map(candidate -> readInputs().get(candidate)
                            .rmdAlreadyDistributedBeforeProjection())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            annual = accountRmd(annualOwnerResult, account);
            remaining = accountRmd(remainingOwnerResult, account);
            distributed = readInputs().get(account)
                    .rmdAlreadyDistributedBeforeProjection();
        }

        CalculatedLabels labels = calculatedLabels.get(account);
        labels.annual().setText(UIFormatters.money(annual));
        labels.distributed().setText(UIFormatters.money(distributed));
        labels.remaining().setText(UIFormatters.money(remaining));
    }

    private OwnerRmdResult ownerResult(
            com.daviddunn.retirementplanner.domain.rmd.HouseholdRmdResult result,
            AccountOwnership ownership) {

        return ownership == AccountOwnership.PRIMARY
                ? result.getPrimaryRmd()
                : result.getSpouseRmd();
    }

    private BigDecimal accountRmd(
            OwnerRmdResult result,
            Account account) {

        return List.of(
                        result.getTraditional401kRmds(),
                        result.getTraditional403bRmds())
                .stream()
                .flatMap(List::stream)
                .filter(accountRmd -> accountRmd.getAccount() == account)
                .map(AccountRmd::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private Map<Account, OpeningRmdWorkflowService.OpeningRmdInput> readInputs() {

        Map<Account, OpeningRmdWorkflowService.OpeningRmdInput> inputs =
                new LinkedHashMap<>();

        for (OpeningRmdWorkflowService.OpeningRmdAccountRow row : rows) {
            Account account = row.account();
            inputs.put(account,
                    new OpeningRmdWorkflowService.OpeningRmdInput(
                            parseMoney(priorBalanceFields.get(account).getText(),
                                    "Prior December 31 balance"),
                            parseMoney(distributedFields.get(account).getText(),
                                    "RMD already distributed")));
        }

        return inputs;
    }

    private BigDecimal parseMoney(
            String text,
            String label) {

        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }

        BigDecimal value;

        try {
            value = new BigDecimal(text.trim().replace("$", "").replace(",", ""));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a valid amount.");
        }

        if (value.signum() < 0) {
            throw new IllegalArgumentException(label + " cannot be negative.");
        }

        return value;
    }

    private void save(
            ActionEvent event) {

        try {
            workflowService.save(plan, readInputs());
            refreshCalculation();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            event.consume();
            statusLabel.setText(ex.getMessage());
        }
    }

    private void clearCalculatedLabels() {

        calculatedLabels.values().forEach(labels -> {
            labels.annual().setText("");
            labels.distributed().setText("");
            labels.remaining().setText("");
        });
    }

    private String getAnnualLabel(
            Account account) {

        return account.getType().getRmdAccountCategory()
                == RmdAccountCategory.IRA
                ? "IRA annual RMD"
                : "Annual RMD";
    }

    private String getRemainingLabel(
            Account account) {

        return account.getType().getRmdAccountCategory()
                == RmdAccountCategory.IRA
                ? "Remaining IRA RMD"
                : "Remaining RMD";
    }

    private record CalculatedLabels(
            Label annual,
            Label distributed,
            Label remaining) {
    }
}
