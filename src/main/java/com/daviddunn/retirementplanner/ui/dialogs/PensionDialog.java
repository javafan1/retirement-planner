package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.ui.controls.RetirementParticipantFactsEditor;

import com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts;

import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import com.daviddunn.retirementplanner.ui.controls.RequiredFieldSupport;
import com.daviddunn.retirementplanner.ui.wizard.WizardDialogSupport;
import com.daviddunn.retirementplanner.ui.wizard.WizardDialogSupport.Field;
import com.daviddunn.retirementplanner.ui.wizard.WizardDialogSupport.Kind;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.stage.Screen;
import javafx.stage.Window;
import javafx.application.Platform;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PensionDialog extends Dialog<Pension> {

    private final TextField nameField;
    private final Label nameLabel = new Label("Name:");
    private final Label nameError = new Label();
    private final Label validationError = new Label();
    private final ComboBox<AccountOwnership> ownershipCombo;
    private final DatePicker startDatePicker;
    private final DatePicker endDatePicker;
    private final TextField monthlyBenefitField;
    private final TextField survivorMonthlyBenefitField;
    private final TextField colaRateField;
    private final RetirementParticipantFactsEditor participantFactsEditor;
    private final List<RequiredFieldSupport.Attachment> requiredFieldAttachments = new ArrayList<>();
    private ScrollPane formScrollPane;
    private GridPane formGrid;

    public PensionDialog(Pension pension) {
        this(pension, true);
    }

    public PensionDialog(Pension pension, com.daviddunn.retirementplanner.domain.model.Household household) {
        this(pension, household.hasSpouse());
    }

    public PensionDialog(Pension pension, com.daviddunn.retirementplanner.domain.model.Household household,
                         boolean guidedCreation) {
        this(pension, household);
        if (guidedCreation) {
            participantFactsEditor.setVisible(false);
            participantFactsEditor.setManaged(false);
            var converter = getResultConverter();
            WizardDialogSupport.install(this, java.util.List.of(
                    new Field("Name", nameField, Kind.TEXT, true),
                    new Field("Owner", ownershipCombo, Kind.CHOICE, true),
                    new Field("Start Date", startDatePicker, Kind.DATE, true),
                    new Field("End Date", endDatePicker, Kind.DATE, false),
                    new Field("Monthly Benefit", monthlyBenefitField, Kind.NUMBER, true),
                    new Field("Survivor Monthly Benefit", survivorMonthlyBenefitField, Kind.NUMBER, false),
                    new Field("Annual COLA Rate", colaRateField, Kind.NUMBER, true)),
                    () -> converter.call(ButtonType.OK));
            WizardDialogSupport.guidance(this, "Name, owner, start date and monthly benefit are required. End date and survivor amount are optional. "
                    + "Annual COLA is a decimal fraction: 0.02 = 2% annually. Survivor amount, when applicable, is optional dollars/month.");
        }
    }

    private PensionDialog(Pension pension, boolean hasSpouse) {
        if (!hasSpouse && pension != null && (pension.getOwnership() != AccountOwnership.PRIMARY
                || (pension.getSurvivorMonthlyBenefit() != null && pension.getSurvivorMonthlyBenefit().signum() > 0))) {
            throw new IllegalArgumentException("A single-person pension cannot require a spouse or survivor benefit.");
        }

        if (pension == null) {
            setTitle("Add Pension");
            setHeaderText("Enter pension information.");
        } else {
            setTitle("Edit Pension");
            setHeaderText("Update pension information.");
        }

        nameField = new TextField();
        nameField.setId("pension-name");
        nameField.textProperty().addListener((observable, oldValue, newValue) -> {
            nameError.setText("");
            nameField.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("invalid"), false);
        });

        ownershipCombo = new ComboBox<>();

        /*
         * Income sources belong to an individual,
         * so JOINT is intentionally excluded.
         */
        ownershipCombo.getItems().add(AccountOwnership.PRIMARY);
        if (hasSpouse) ownershipCombo.getItems().add(AccountOwnership.SPOUSE);

        ownershipCombo.getSelectionModel().selectFirst();

        startDatePicker = new DatePicker();

        endDatePicker = new DatePicker();

        monthlyBenefitField = new TextField();

        survivorMonthlyBenefitField =
                new TextField();

        /*
         * COLA is stored as a decimal:
         *
         * 0.0   = no COLA
         * 0.02  = 2%
         * 0.025 = 2.5%
         */
        colaRateField =
                new TextField("0.0");
        participantFactsEditor = new RetirementParticipantFactsEditor(
                pension == null ? RetirementParticipantFacts.UNKNOWN
                        : pension.getRetirementParticipantFacts());

        /*
         * Populate controls when editing
         * an existing pension.
         */
        if (pension != null) {

            nameField.setText(
                    pension.getName());

            ownershipCombo.setValue(
                    pension.getOwnership());

            startDatePicker.setValue(
                    pension.getStartDate());

            endDatePicker.setValue(
                    pension.getEndDate());

            monthlyBenefitField.setText(
                    pension.getMonthlyBenefit()
                            .toPlainString());

            if (pension.getSurvivorMonthlyBenefit()
                    != null) {

                survivorMonthlyBenefitField.setText(
                        pension
                                .getSurvivorMonthlyBenefit()
                                .toPlainString());
            }

            colaRateField.setText(
                    pension.getAnnualColaRate()
                            .toPlainString());
        }

        GridPane grid =
                new GridPane();
        formGrid = grid;

        grid.setPadding(
                new Insets(15));

        grid.setHgap(10);
        grid.setVgap(10);

        int row = 0;

        nameError.setId("pension-name-error");
        nameError.getStyleClass().add("wizard-field-error");
        nameError.setWrapText(true);
        nameError.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        nameError.managedProperty().bind(nameError.textProperty().isNotEmpty());
        nameError.visibleProperty().bind(nameError.managedProperty());
        grid.add(nameLabel, 0, row);

        grid.add(
                nameField,
                1,
                row++);
        grid.add(nameError, 0, row++, 2, 1);

        Label ownerLabel = new Label("Owner:");
        grid.add(
                ownerLabel,
                0,
                row);

        grid.add(
                ownershipCombo,
                1,
                row++);

        Label startDateLabel = new Label("Start Date:");
        grid.add(
                startDateLabel,
                0,
                row);

        grid.add(
                startDatePicker,
                1,
                row++);

        grid.add(
                new Label("End Date:"),
                0,
                row);

        grid.add(
                endDatePicker,
                1,
                row++);

        Label monthlyBenefitLabel = new Label("Monthly Benefit:");
        grid.add(
                monthlyBenefitLabel,
                0,
                row);

        grid.add(
                monthlyBenefitField,
                1,
                row++);

        grid.add(
                new Label(
                        "Survivor Monthly Benefit:"),
                0,
                row);

        grid.add(
                survivorMonthlyBenefitField,
                1,
                row++);

        Label colaRateLabel = new Label("Annual COLA Rate:");
        grid.add(
                colaRateLabel,
                0,
                row);

        grid.add(
                colaRateField,
                1,
                row);

        getDialogPane().setContent(grid);
        validationError.setId("pension-validation-error");
        validationError.getStyleClass().add("wizard-field-error");
        validationError.setWrapText(true);
        validationError.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        validationError.managedProperty().bind(validationError.textProperty().isNotEmpty());
        validationError.visibleProperty().bind(validationError.managedProperty());
        grid.add(validationError, 0, row + 1, 2, 1);
        grid.add(participantFactsEditor, 0, row + 2, 2, 1);
        setResizable(true);

        getDialogPane()
                .getButtonTypes()
                .addAll(
                        ButtonType.OK,
                        ButtonType.CANCEL);

        InputHelp.install(ownershipCombo, "Person whose pension this is. Their death determines when the separate survivor benefit, if provided, replaces the owner's benefit.");
        InputHelp.install(nameField, "Display name for this pension. Leading and trailing whitespace is removed when saved.");
        InputHelp.install(startDatePicker, "Date pension payments begin. Income is counted for active calendar months; this date also establishes the base year for this pension's COLA.");
        InputHelp.install(endDatePicker, "Optional last date of pension payments. Leave blank for no scheduled termination; death and any survivor benefit still affect modeled income.");
        InputHelp.install(monthlyBenefitField, "Gross monthly pension benefit in dollars at the pension start, before income taxes. The projection applies this pension's COLA and counts active months.");
        InputHelp.install(survivorMonthlyBenefitField, "Monthly dollar benefit payable to the surviving spouse after the pension owner's death, before COLA. Enter an amount, not a percentage; blank means no survivor benefit.");
        InputHelp.install(colaRateField, "Annual pension COLA as a decimal: enter 0.02 for 2%, or 0 for no increase. Compounded from the pension start year for both owner and survivor benefits; independent of General Inflation.");
        InputHelp.linkGridLabels(grid);
        requiredFieldAttachments.add(RequiredFieldSupport.attach(nameLabel, nameField));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(ownerLabel, ownershipCombo));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(startDateLabel, startDatePicker));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(monthlyBenefitLabel, monthlyBenefitField));
        // The UI always parses this value at submission; the displayed 0.0 is a valid default.
        requiredFieldAttachments.add(RequiredFieldSupport.attach(colaRateLabel, colaRateField));
        grid.add(RequiredFieldSupport.createLegend(), 0, row + 3, 2, 1);
        setOnShowing(event -> installScrollableForm());
        setOnShown(event -> resizeToScreen());
        participantFactsEditor.setAnimated(false);
        participantFactsEditor.expandedProperty().addListener((observable, oldValue, newValue) -> resizeToScreen());
        setOnHidden(event -> {
            requiredFieldAttachments.forEach(RequiredFieldSupport.Attachment::close);
            requiredFieldAttachments.clear();
            participantFactsEditor.close();
        });
        if (!hasSpouse) {
            grid.getChildren().stream()
                    .filter(node -> java.util.Objects.equals(GridPane.getRowIndex(node),
                            GridPane.getRowIndex(survivorMonthlyBenefitField)))
                    .forEach(node -> { node.setVisible(false); node.setManaged(false); });
            InputHelp.install(ownershipCombo, "The actual person receiving this pension. A single-person household has only Primary ownership.");
            InputHelp.install(endDatePicker, "Optional last date of pension payments. Leave blank for no scheduled termination; modeled death still ends the person's income.");
            InputHelp.install(colaRateField, "Annual pension COLA as a decimal: enter 0.02 for 2%, or 0 for no increase. Compounded from the pension start year, independently of General Inflation.");
            InputHelp.linkGridLabels(grid);
        }
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!validateBeforeSubmit()) event.consume();
        });
        setResultConverter(button -> {

            if (button != ButtonType.OK) {
                return null;
            }

            String name = com.daviddunn.retirementplanner.domain.model.RequiredNameValidation
                    .normalize(nameField.getText(), "Pension name");

            AccountOwnership ownership =
                    ownershipCombo.getValue();

            LocalDate startDate =
                    startDatePicker.getValue();

            LocalDate endDate =
                    endDatePicker.getValue();

            BigDecimal monthlyBenefit =
                    new BigDecimal(
                            monthlyBenefitField
                                    .getText()
                                    .trim());

            /*
             * Survivor benefit is optional.
             *
             * Blank = this pension has no
             * survivor benefit.
             */
            String survivorText =
                    survivorMonthlyBenefitField
                            .getText()
                            .trim();

            BigDecimal survivorMonthlyBenefit =
                    survivorText.isEmpty()
                            ? null
                            : new BigDecimal(
                            survivorText);

            BigDecimal annualColaRate =
                    new BigDecimal(
                            colaRateField
                                    .getText()
                                    .trim());

            Pension result = new Pension(
                    name,
                    ownership,
                    startDate,
                    endDate,
                    monthlyBenefit,
                    annualColaRate,
                    survivorMonthlyBenefit);
            result.setRetirementParticipantFacts(participantFactsEditor.value());
            return result;
        });
    }

    private boolean validateBeforeSubmit() {
        nameError.setText("");
        validationError.setText("");
        nameField.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("invalid"), false);
        try {
            com.daviddunn.retirementplanner.domain.model.RequiredNameValidation
                    .normalize(nameField.getText(), "Pension name");
        } catch (IllegalArgumentException invalid) {
            showValidationError(invalid.getMessage(), nameField);
            return false;
        }
        if (ownershipCombo.getValue() == null) {
            showValidationError("Pension owner is required.", ownershipCombo);
            return false;
        }
        LocalDate start;
        try {
            start = parseDate(startDatePicker);
        } catch (RuntimeException invalid) {
            showValidationError("Enter a valid pension start date.", startDatePicker);
            return false;
        }
        if (start == null) {
            showValidationError("Pension start date is required.", startDatePicker);
            return false;
        }
        if (!validDecimal(monthlyBenefitField)) {
            showValidationError("Enter a valid monthly benefit.", monthlyBenefitField);
            return false;
        }
        if (!validDecimal(colaRateField)) {
            showValidationError("Enter a valid annual COLA rate.", colaRateField);
            return false;
        }
        if (!survivorMonthlyBenefitField.getText().isBlank()
                && !validDecimal(survivorMonthlyBenefitField)) {
            showValidationError("Enter a valid survivor monthly benefit or leave it blank.",
                    survivorMonthlyBenefitField);
            return false;
        }
        try {
            parseDate(endDatePicker);
        } catch (RuntimeException invalid) {
            showValidationError("Enter a valid pension end date or leave it blank.", endDatePicker);
            return false;
        }
        nameError.setText("");
        return true;
    }

    private static boolean validDecimal(TextField field) {
        try {
            new BigDecimal(field.getText().trim());
            return true;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static LocalDate parseDate(DatePicker picker) {
        String text = picker.getEditor().getText();
        if (text == null || text.isBlank()) return null;
        return picker.getConverter().fromString(text);
    }

    private void showValidationError(String message, Control field) {
        Label messageLabel = field == nameField ? nameError : validationError;
        messageLabel.setText(message);
        field.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("invalid"), true);
        field.requestFocus();
        if (getDialogPane().getContent() instanceof javafx.scene.Parent content) {
            getDialogPane().applyCss();
            content.applyCss();
            content.requestLayout();
            content.layout();
            formScrollPane.applyCss();
            formScrollPane.layout();
            getDialogPane().layout();
        }
        scrollTo(field);
        Platform.runLater(() -> scrollTo(field));
    }

    private void installScrollableForm() {
        if (formScrollPane != null) return;
        Node form = getDialogPane().getContent();
        formGrid.getChildren().removeAll(nameError, validationError);
        formScrollPane = new ScrollPane(form);
        formScrollPane.setId("pension-dialog-form-scroll");
        formScrollPane.setFitToWidth(true);
        formScrollPane.setFitToHeight(false);
        formScrollPane.setPannable(true);
        formScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        formScrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox boundedContent = new VBox(8, nameError, validationError, formScrollPane);
        boundedContent.setFillWidth(true);
        getDialogPane().setContent(boundedContent);
        getDialogPane().setMinWidth(280);
        getDialogPane().setPrefWidth(560);
        resizeToScreen();
    }

    private void resizeToScreen() {
        Platform.runLater(() -> {
            if (formScrollPane == null || getDialogPane().getScene() == null
                    || getDialogPane().getScene().getWindow() == null) return;
            Window window = getDialogPane().getScene().getWindow();
            Rectangle2D visual = visualBounds(window);
            double maxHeight = Math.max(300, visual.getHeight() - 24);
            double maxWidth = Math.max(240, visual.getWidth() - 24);
            double maxViewportHeight = Math.max(160, maxHeight - 185);
            getDialogPane().setMaxHeight(maxHeight);
            getDialogPane().setMaxWidth(maxWidth);
            getDialogPane().setMinWidth(Math.min(280, maxWidth));
            getDialogPane().setPrefWidth(Math.min(560, maxWidth));
            formScrollPane.setMaxWidth(Math.max(240, maxWidth - 24));
            formScrollPane.setMaxHeight(maxViewportHeight);
            getDialogPane().applyCss();
            getDialogPane().layout();
            Node form = formScrollPane.getContent();
            double naturalHeight = form.prefHeight(Math.max(220, formScrollPane.getWidth()));
            if (!Double.isFinite(naturalHeight) || naturalHeight <= 0) naturalHeight = maxViewportHeight;
            formScrollPane.setPrefViewportHeight(Math.min(naturalHeight, maxViewportHeight));
            if (window instanceof javafx.stage.Stage stage) {
                stage.setMaxHeight(maxHeight);
                stage.setMaxWidth(maxWidth);
                stage.sizeToScene();
                if (stage.getHeight() > maxHeight) stage.setHeight(maxHeight);
                if (stage.getWidth() > maxWidth) stage.setWidth(maxWidth);
            }
        });
    }

    private Rectangle2D visualBounds(Window window) {
        Window owner = getOwner();
        double x = owner == null ? window.getX() : owner.getX();
        double y = owner == null ? window.getY() : owner.getY();
        double width = owner == null ? window.getWidth() : owner.getWidth();
        double height = owner == null ? window.getHeight() : owner.getHeight();
        return Screen.getScreensForRectangle(x, y, width, height).stream().findFirst()
                .orElse(Screen.getPrimary()).getVisualBounds();
    }

    private void scrollTo(Node node) {
        if (formScrollPane == null || formScrollPane.getContent() == null) return;
        var viewport = formScrollPane.getViewportBounds();
        var content = formScrollPane.getContent();
        var bounds = node.localToScene(node.getBoundsInLocal());
        var viewportBounds = formScrollPane.localToScene(viewport);
        double range = Math.max(1, content.getLayoutBounds().getHeight() - viewport.getHeight());
        double delta = bounds.getMinY() < viewportBounds.getMinY()
                ? bounds.getMinY() - viewportBounds.getMinY()
                : bounds.getMaxY() > viewportBounds.getMaxY()
                ? bounds.getMaxY() - viewportBounds.getMaxY() : 0;
        if (delta != 0) formScrollPane.setVvalue(Math.max(0, Math.min(1,
                formScrollPane.getVvalue() + delta / range)));
    }
}
