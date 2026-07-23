package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.income.IncomeSource;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.dialogs.PensionDialog;
import com.daviddunn.retirementplanner.ui.dialogs.SocialSecurityDialog;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;

import javafx.scene.control.ChoiceDialog;

import java.util.Optional;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import java.time.format.DateTimeFormatter;
import javafx.scene.control.Alert;

public class IncomeSourcesView extends BorderPane {

    private final TableView<IncomeSource> table;

    private final Button addButton;
    private final Button editButton;
    private final Button removeButton;

    private RetirementPlan currentPlan;

    private final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern("MM/dd/yyyy");

    public IncomeSourcesView() {

        table = new TableView<>();

        createColumns();

        addButton = new Button("Add");
        editButton = new Button("Edit");
        removeButton = new Button("Remove");

        editButton.disableProperty().bind(
                table.getSelectionModel()
                        .selectedItemProperty()
                        .isNull());

        removeButton.disableProperty().bind(
                table.getSelectionModel()
                        .selectedItemProperty()
                        .isNull());


        addButton.setOnAction(e -> onAdd());
        editButton.setOnAction(e -> onEdit());
        removeButton.setOnAction(e -> onRemove());

        HBox buttonBar = new HBox(10);
        buttonBar.setPadding(new Insets(10));

        buttonBar.getChildren().addAll(
                addButton,
                editButton,
                removeButton);

        setCenter(table);
        setBottom(buttonBar);
    }

    private IncomeSource getSelectedIncomeSource() {
        return table.getSelectionModel().getSelectedItem();
    }
    private void onAdd() {

        ChoiceDialog<String> dialog =
                new ChoiceDialog<>(
                        "Pension",
                        "Pension",
                        "Social Security");

        dialog.setTitle("Add Income");
        dialog.setHeaderText("Select the type of income to add.");
        dialog.setContentText("Income Type:");

        Optional<String> result =
                dialog.showAndWait();

        if (result.isEmpty()) {
            return;
        }

        switch (result.get()) {

            case "Pension" ->
                    addPension();

            case "Social Security" ->
                    addSocialSecurity();

            default ->
                    throw new IllegalStateException(
                            "Unexpected income type: "
                                    + result.get());
        }
    }

    private void onEdit() {

        IncomeSource selected =
                getSelectedIncomeSource();

        if (selected == null) {
            return;
        }

        if (selected instanceof Pension pension) {

            PensionDialog dialog =
                    new PensionDialog(pension);

            Optional<Pension> result =
                    dialog.showAndWait();

            result.ifPresent(updated ->
                    replaceIncomeSource(
                            selected,
                            updated));

        } else if (selected instanceof SocialSecurityIncome socialSecurity) {

            SocialSecurityDialog dialog =
                    new SocialSecurityDialog(socialSecurity);

            Optional<SocialSecurityIncome> result =
                    dialog.showAndWait();

            result.ifPresent(updated ->
                    replaceIncomeSource(
                            selected,
                            updated));
        }
    }

    private void replaceIncomeSource(
            IncomeSource oldIncome,
            IncomeSource newIncome) {

        Person oldOwner =
                getPerson(oldIncome.getOwnership());

        Person newOwner =
                getPerson(newIncome.getOwnership());

        if (oldOwner == newOwner) {

            oldOwner.replaceIncomeSource(
                    oldIncome,
                    newIncome);

        } else {

            oldOwner.removeIncomeSource(oldIncome);
            newOwner.addIncomeSource(newIncome);
        }

        refreshTable();
    }

    private void onRemove() {

        IncomeSource selected =
                getSelectedIncomeSource();

        if (selected == null) {
            return;
        }

        Person owner =
                getPerson(
                        selected.getOwnership());

        owner.removeIncomeSource(selected);

        refreshTable();
    }

    private void addSocialSecurity() {

        SocialSecurityDialog dialog =
                new SocialSecurityDialog(null);

        Optional<SocialSecurityIncome> result =
                dialog.showAndWait();

        result.ifPresent(income -> {

            Person person =
                    getPerson(income.getOwnership());

            person.addIncomeSource(income);

            refreshTable();
        });
    }

    private Person getPerson(
            AccountOwnership ownership) {

        return switch (ownership) {

            case PRIMARY ->
                    currentPlan
                            .getHousehold()
                            .getPrimaryPerson();

            case SPOUSE ->
                    currentPlan
                            .getHousehold()
                            .getSpouse();

            case JOINT ->
                    throw new IllegalArgumentException(
                            "Income sources cannot have joint ownership.");
        };
    }

    private void addPension() {

        PensionDialog dialog =
                new PensionDialog(null);

        Optional<Pension> result =
                dialog.showAndWait();

        result.ifPresent(pension -> {

            Person person =
                    getPerson(
                            pension.getOwnership());

            person.addIncomeSource(pension);

            refreshTable();
        });
    }
//    private void createColumns() {
//
//        TableColumn<IncomeSource, String> nameColumn =
//                new TableColumn<>("Name");
//
//        nameColumn.setCellValueFactory(cellData ->
//                new ReadOnlyStringWrapper(
//                        cellData.getValue().getName()));
//
//        TableColumn<IncomeSource, String> ownershipColumn =
//                new TableColumn<>("Owner");
//
//        ownershipColumn.setCellValueFactory(cellData ->
//                new ReadOnlyStringWrapper(
//                        cellData.getValue()
//                                .getOwnership()
//                                .toString()));
//
//        TableColumn<IncomeSource, String> typeColumn =
//                new TableColumn<>("Type");
//
//        typeColumn.setCellValueFactory(cellData ->
//                new ReadOnlyStringWrapper(
//                        getIncomeTypeName(
//                                cellData.getValue())));
//
//        TableColumn<IncomeSource, String> startDateColumn =
//                new TableColumn<>("Start Date");
//
//        startDateColumn.setCellValueFactory(cellData ->
//                new ReadOnlyStringWrapper(
//                        formatDate(
//                                cellData.getValue()
//                                        .getStartDate())));
//
//        table.getColumns().addAll(
//                nameColumn,
//                ownershipColumn,
//                typeColumn,
//                startDateColumn);
//
//        table.setColumnResizePolicy(
//                TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
//    }

    private void createColumns() {

        TableColumn<IncomeSource, String> typeColumn =
                new TableColumn<>("Type");

        typeColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        getIncomeTypeName(
                                cellData.getValue())));

        TableColumn<IncomeSource, String> nameColumn =
                new TableColumn<>("Name");

        nameColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        cellData.getValue().getName()));

        TableColumn<IncomeSource, String> ownershipColumn =
                new TableColumn<>("Owner");

        ownershipColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        cellData.getValue()
                                .getOwnership()
                                .toString()));

        TableColumn<IncomeSource, String> startDateColumn =
                new TableColumn<>("Start Date");

        startDateColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        formatDate(
                                cellData.getValue()
                                        .getStartDate())));

        TableColumn<IncomeSource, String> endDateColumn =
                new TableColumn<>("End Date");

        endDateColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        formatDate(
                                cellData.getValue()
                                        .getEndDate())));

        TableColumn<IncomeSource, String> monthlyBenefitColumn =
                new TableColumn<>("Monthly Benefit");

        monthlyBenefitColumn.setCellValueFactory(cellData -> {

            IncomeSource income =
                    cellData.getValue();

            if (income instanceof Pension pension) {

                return new ReadOnlyStringWrapper(
                        pension.getMonthlyBenefit()
                                .toPlainString());
            }

            return new ReadOnlyStringWrapper("");
        });

        TableColumn<IncomeSource, String> fraBenefitColumn =
                new TableColumn<>("FRA Benefit");

        fraBenefitColumn.setCellValueFactory(cellData -> {

            IncomeSource income =
                    cellData.getValue();

            if (income instanceof SocialSecurityIncome socialSecurity) {

                return new ReadOnlyStringWrapper(
                        socialSecurity
                                .getFullRetirementMonthlyBenefit()
                                .toPlainString());
            }

            return new ReadOnlyStringWrapper("");
        });

        TableColumn<IncomeSource, String> claimingAgeColumn =
                new TableColumn<>("Claim Age");

        claimingAgeColumn.setCellValueFactory(cellData -> {

            IncomeSource income =
                    cellData.getValue();

            if (income instanceof SocialSecurityIncome socialSecurity) {

                return new ReadOnlyStringWrapper(
                        Integer.toString(
                                socialSecurity.getClaimingAge()));
            }

            return new ReadOnlyStringWrapper("");
        });

        TableColumn<IncomeSource, String> colaColumn =
                new TableColumn<>("COLA");

        colaColumn.setCellValueFactory(cellData -> {

            IncomeSource income =
                    cellData.getValue();

            if (income instanceof Pension pension) {

                return new ReadOnlyStringWrapper(
                        formatPercent(
                                pension.getAnnualColaRate()));
            }

            if (income instanceof SocialSecurityIncome socialSecurity) {

                return new ReadOnlyStringWrapper(
                        formatPercent(
                                socialSecurity.getAnnualColaRate()));
            }

            return new ReadOnlyStringWrapper("");
        });

        table.getColumns().addAll(
                typeColumn,
                nameColumn,
                ownershipColumn,
                startDateColumn,
                endDateColumn,
                monthlyBenefitColumn,
                fraBenefitColumn,
                claimingAgeColumn,
                colaColumn);

        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    private String getIncomeTypeName(
            IncomeSource incomeSource) {

        String className =
                incomeSource.getClass().getSimpleName();

        return switch (className) {

            case "Pension" ->
                    "Pension";

            case "SocialSecurityIncome" ->
                    "Social Security";

            default ->
                    className;
        };
    }


    private String formatDate(
            java.time.LocalDate date) {

        if (date == null) {
            return "";
        }

        return dateFormatter.format(date);
    }

    private String formatPercent(
            java.math.BigDecimal rate) {

        if (rate == null) {
            return "";
        }

        return rate
                .multiply(
                        new java.math.BigDecimal("100"))
                .stripTrailingZeros()
                .toPlainString()
                + "%";
    }

    public void load(RetirementPlan plan) {

        currentPlan = plan;

        refreshTable();
    }

    public void save(RetirementPlan plan) {

        // Nothing to do.
        // Changes will be applied directly
        // to the domain model.
    }

    private void refreshTable() {

        table.getItems().clear();

        if (currentPlan == null) {
            return;
        }

        table.getItems().addAll(
                currentPlan
                        .getHousehold()
                        .getPrimaryPerson()
                        .getIncomeSources());

        table.getItems().addAll(
                currentPlan
                        .getHousehold()
                        .getSpouse()
                        .getIncomeSources());
    }

    public TableView<IncomeSource> getTable() {
        return table;
    }
}