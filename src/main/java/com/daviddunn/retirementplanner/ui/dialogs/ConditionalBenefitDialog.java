package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.benefits.ConditionalBenefitPresentation;
import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import com.daviddunn.retirementplanner.ui.controls.RequiredFieldSupport;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static com.daviddunn.retirementplanner.ui.benefits.ConditionalBenefitPresentation.*;

/** A detached input transaction. Only the caller commits an accepted immutable definition. */
public final class ConditionalBenefitDialog extends Dialog<ConditionalBenefitDefinition> {
    public static final ButtonType SAVE = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    private final RetirementPlan plan;
    private final UUID identity;
    private final TextField name = new TextField();
    private final ComboBox<Category> category = new ComboBox<>();
    private final ComboBox<PaymentPattern> pattern = new ComboBox<>();
    private final TextField amount = new TextField();
    private final ComboBox<TriggerType> trigger = new ComboBox<>();
    private final DatePicker triggerDate = datePicker();
    private final ComboBox<PersonReference> triggerPerson = new ComboBox<>();
    private final TextField age = new TextField();
    private final ComboBox<PersonReference> recipient = new ComboBox<>();
    private final DatePicker notBefore = datePicker();
    private final DatePicker end = datePicker();
    private final TextField escalation = new TextField("0");
    private final DatePicker coverageStart = datePicker();
    private final DatePicker coverageEnd = datePicker();
    private final ComboBox<TaxCharacter> tax = new ComboBox<>();
    private final CheckBox qualification = new CheckBox();
    private final Label taxHelp = text(TAX_HELP);
    private TaxCharacter draftTax = TaxCharacter.FULLY_TAXABLE_ORDINARY_NON_PENSION;
    private boolean updatingTax;
    private final Label status = text("");
    private final Label insurance = text(INSURANCE_HELP);
    private final Label deathTriggerHelp = text("Death Benefit payments require the selected person's death, modeled on January 1 of the selected death year.");
    private final Label monthlyHelp = text("Partial eligible calendar months count as whole payment months. Payments require a living recipient. Annual escalation begins in the first eligible payment year after any Not Before date, even if that year precedes the projection opening.");
    private final VBox form = new VBox(12);
    private final ScrollPane scroll = new ScrollPane(form);
    private final Map<Control, Field> fields = new LinkedHashMap<>();
    private final List<RequiredFieldSupport.Attachment> required = new ArrayList<>();
    private ConditionalBenefitDefinition accepted;
    private TriggerType previousIncomeTrigger = TriggerType.FIXED_DATE;

    public ConditionalBenefitDialog(RetirementPlan plan, ConditionalBenefitDefinition existing) {
        this(plan, existing, false);
    }

    public ConditionalBenefitDialog(RetirementPlan plan, ConditionalBenefitDefinition existing, boolean duplicate) {
        this.plan = Objects.requireNonNull(plan);
        identity = existing == null || duplicate ? UUID.randomUUID() : existing.id();
        setTitle(existing == null || duplicate ? "Add Conditional Benefit" : "Edit Conditional Benefit");
        setHeaderText(duplicate ? "Review the new, independent copy before saving." : "Configure a future payment to a living household member.");
        setResizable(true);
        getDialogPane().setId("conditional-benefit-dialog");
        getDialogPane().getButtonTypes().addAll(SAVE, ButtonType.CANCEL);
        form.setPadding(new Insets(12));
        form.setFillWidth(true);
        form.getChildren().add(heading("General"));
        field("Benefit Name", name, "name", true, "A display name, up to 128 characters without control characters. Renaming does not change the financial definition.");
        choices(category, List.of(Category.CONDITIONAL_INCOME,Category.DEATH_BENEFIT), ConditionalBenefitPresentation::category);
        field("Benefit Category", category, "category", true, "Death Benefit requires a person's death. Only qualifying personal death-principal lump sums can use the restricted excluded tax treatment.");
        choices(pattern, List.of(PaymentPattern.values()), value -> value == PaymentPattern.MONTHLY_RECURRING
                ? "Recurring Monthly Payment" : "One-Time Lump Sum");
        field("Payment Type", pattern, "pattern", true, "Choose a monthly gross amount or the entire one-time gross receipt.");
        field("Payment Amount ($)", amount, "amount", true, "Gross dollars before taxes, from 0 through 1,000,000,000,000. Enter a decimal number without currency symbols or grouping commas; up to 8 fractional digits and 24 significant digits.");
        form.getChildren().add(heading("Trigger and recipient"));
        choices(trigger, List.of(TriggerType.values()), value -> switch (value) {
            case FIXED_DATE -> "On a Specific Date";
            case AGE -> "When a Person Reaches an Age";
            case DEATH -> "Upon a Person's Death";
        });
        field("Payment Trigger", trigger, "trigger", true, "Date, birthday-derived age, or January 1 of the selected person's modeled death year. No independent mortality event is generated.");
        form.getChildren().add(deathTriggerHelp);
        field("Trigger Date", triggerDate, "trigger-date", true, "Required for a specific-date trigger. ISO yyyy-MM-dd; years 1000 through 9999.");
        var people = plan.getHousehold().hasSpouse() ? List.of(PersonReference.PRIMARY, PersonReference.SPOUSE)
                : List.of(PersonReference.PRIMARY);
        choices(triggerPerson, people, value -> person(value, plan.getHousehold()));
        field("Trigger Person", triggerPerson, "trigger-person", true, "The modeled person whose birthday or death establishes eligibility. A missing spouse is not a deceased spouse.");
        field("Trigger Age", age, "age", true, "Whole-number age from 0 through 120. Derived from the frozen date of birth, independently of Social Security elections. February 29 birthdays resolve to February 28 in non-leap years.");
        choices(recipient, people, value -> person(value, plan.getHousehold()));
        field("Payment Recipient", recipient, "recipient", true, "A living modeled Primary or Spouse. A death-triggered payment cannot be payable to its deceased triggering person. No estate or external beneficiary is supported.");
        form.getChildren().add(heading("Payment timing and event coverage"));
        field("Not Before Date", notBefore, "not-before", false, "Optional monthly commencement threshold: the later of the trigger and this date. This does not change trigger-event coverage.");
        field("Payment End Date", end, "end", false, "Optional inclusive final monthly payment date. This is different from coverage qualifying the trigger event.");
        field("Annual Escalation (%)", escalation, "escalation", true, "Monthly payments only. Enter 2 for 2% or 0 for no escalation, from 0 through 100. The stored rate supports up to 8 fractional digits after dividing by 100; no rounding is applied here.");
        form.getChildren().add(monthlyHelp);
        field("Coverage Start Date", coverageStart, "coverage-start", false, "Optional inclusive first date on which the triggering event qualifies. It does not delay a qualifying recurring payment stream.");
        field("Coverage End Date", coverageEnd, "coverage-end", false, "Optional inclusive last date on which the triggering event qualifies; it does not terminate an already-active recurring stream. Modeled deaths occur January 1, which the coverage must allow.");
        form.getChildren().add(text("Leave optional dates blank for no additional limit. One-time payments occur only once in the eligible trigger year; events before the projection opening are not replayed."));
        form.getChildren().add(heading("Financial Treatment"));
        choices(tax, List.of(TaxCharacter.FULLY_TAXABLE_ORDINARY_NON_PENSION), ConditionalBenefitPresentation::tax);
        field("Tax Treatment", tax, "tax-character", true, "Fully taxable by default. Excluded principal requires a qualifying personal death-benefit lump sum to the other living modeled person.");
        form.getChildren().add(taxHelp);
        qualification.setText(com.daviddunn.retirementplanner.app.projection.ConditionalBenefitDisclosures.LIFE_QUALIFICATION);
        qualification.setWrapText(true);
        field("Confirm qualifying personal death principal", qualification, "life-qualification", true,
                "Required planning assertion for excluded principal. Unknown or special tax situations are unsupported; do not include settlement interest.");
        form.getChildren().addAll(text("Payment Destination: " + DESTINATION), text(DESTINATION_HELP), insurance);
        Label stateWarning = text("");
        stateWarning.setId("cb-state-warning");
        stateWarning.getStyleClass().add("analysis-stale");
        stateWarning.managedProperty().bind(stateWarning.textProperty().isNotEmpty());
        stateWarning.visibleProperty().bind(stateWarning.managedProperty());
        try {
            ConditionalBenefitAdmission.requireDeterministicState(plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
        } catch (ConditionalBenefitAdmission.UnsupportedExecutionException unsupported) {
            stateWarning.setText("This definition can be saved, but projections and integrated analyses using conditional benefits are not yet supported for "
                    + plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction().displayName()
                    + ". No state setting will be changed.");
        }
        form.getChildren().add(RequiredFieldSupport.createLegend());
        scroll.setId("cb-form-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMinHeight(100);
        scroll.setPrefViewportHeight(440);
        status.setId("cb-validation-status");
        status.getStyleClass().add("wizard-field-error");
        status.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
        status.setFocusTraversable(true);
        status.managedProperty().bind(status.textProperty().isNotEmpty());
        status.visibleProperty().bind(status.managedProperty());
        var body = new BorderPane(scroll);
        body.setTop(new VBox(4, stateWarning, status));
        getDialogPane().setContent(body);
        getDialogPane().setPrefWidth(680);
        category.setValue(existing == null ? Category.CONDITIONAL_INCOME : existing.category());
        pattern.setValue(existing == null ? PaymentPattern.MONTHLY_RECURRING : existing.paymentPattern());
        trigger.setValue(existing == null ? TriggerType.FIXED_DATE : existing.trigger().type());
        previousIncomeTrigger = trigger.getValue();
        if (existing != null) {
            draftTax = existing.taxCharacter();
            qualification.setSelected(existing.excludesLifePrincipal() && !duplicate);
            name.setText(existing.label());
            amount.setText(existing.grossAmount().toPlainString());
            triggerDate.setValue(existing.trigger().date());
            triggerPerson.setValue(existing.trigger().person());
            age.setText(existing.trigger().age() == null ? "" : existing.trigger().age().toString());
            recipient.setValue(existing.recipient());
            notBefore.setValue(existing.notBefore());
            end.setValue(existing.endDate());
            coverageStart.setValue(existing.coverageStart());
            coverageEnd.setValue(existing.coverageEnd());
            escalation.setText(existing.annualEscalationRate().movePointRight(2).toPlainString());
        }
        category.valueProperty().addListener((o, old, value) -> {
            if (value == Category.DEATH_BENEFIT) {
                if (trigger.getValue() != TriggerType.DEATH) previousIncomeTrigger = trigger.getValue();
                trigger.setValue(TriggerType.DEATH);
            } else if (old == Category.DEATH_BENEFIT) {
                trigger.setValue(previousIncomeTrigger);
            }
            updateFields();
        });
        pattern.valueProperty().addListener((o, old, value) -> updateFields());
        trigger.valueProperty().addListener((o, old, value) -> updateFields());
        recipient.valueProperty().addListener((o, old, value) -> updateFields());
        triggerPerson.valueProperty().addListener((o, old, value) -> updateFields());
        tax.valueProperty().addListener((o, old, value) -> {
            if (!updatingTax && value != null) { draftTax = value; updateFields(); }
        });
        updateFields();
        getDialogPane().lookupButton(SAVE).addEventFilter(ActionEvent.ACTION, event -> {
            if (!validateDraft()) event.consume();
        });
        setResultConverter(button -> button == SAVE ? accepted : null);
        setOnShown(event -> {
            var window = getDialogPane().getScene().getWindow();
            var bounds = Screen.getScreensForRectangle(window.getX(), window.getY(), window.getWidth(), window.getHeight())
                    .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
            getDialogPane().setMaxSize(bounds.getWidth() - 24, bounds.getHeight() - 24);
            if (window.getHeight() > bounds.getHeight() - 24) window.setHeight(bounds.getHeight() - 24);
            if (window.getWidth() > bounds.getWidth() - 24) window.setWidth(bounds.getWidth() - 24);
            Platform.runLater(name::requestFocus);
        });
        setOnHidden(event -> required.forEach(RequiredFieldSupport.Attachment::close));
    }

    private void updateFields() {
        boolean monthly = pattern.getValue() == PaymentPattern.MONTHLY_RECURRING;
        boolean dated = trigger.getValue() == TriggerType.FIXED_DATE;
        boolean aged = trigger.getValue() == TriggerType.AGE;
        boolean death = trigger.getValue() == TriggerType.DEATH;
        fields.get(amount).label().setText(monthly ? "Monthly Payment Amount ($)" : "Lump-Sum Payment Amount ($)");
        amount.setAccessibleText(fields.get(amount).label().getText());
        fields.get(triggerDate).show(dated);
        fields.get(triggerPerson).show(aged || death);
        fields.get(triggerPerson).label().setText(death ? "Insured/Triggering Person" : "Trigger Person");
        fields.get(age).show(aged);
        fields.get(notBefore).show(monthly);
        fields.get(end).show(monthly);
        fields.get(escalation).show(monthly);
        monthlyHelp.setVisible(monthly);
        monthlyHelp.setManaged(monthly);
        boolean eligiblePrincipal = death && !monthly && category.getValue() == Category.DEATH_BENEFIT
                && plan.getHousehold().hasSpouse() && recipient.getValue() != null && triggerPerson.getValue() != null
                && recipient.getValue() != triggerPerson.getValue();
        var treatments = eligiblePrincipal ? List.of(TaxCharacter.values())
                : List.of(TaxCharacter.FULLY_TAXABLE_ORDINARY_NON_PENSION);
        updatingTax = true;
        try {
            if (!tax.getItems().equals(treatments)) tax.getItems().setAll(treatments);
            tax.setValue(eligiblePrincipal ? draftTax : TaxCharacter.FULLY_TAXABLE_ORDINARY_NON_PENSION);
        } finally { updatingTax = false; }
        boolean excluded = tax.getValue() == TaxCharacter.QUALIFIED_LIFE_INSURANCE_DEATH_PRINCIPAL_EXCLUDED;
        fields.get(qualification).show(excluded);
        taxHelp.setText(excluded ? com.daviddunn.retirementplanner.app.projection.ConditionalBenefitDisclosures.LIFE_PRINCIPAL_SCOPE : TAX_HELP);
        insurance.setVisible(death && !monthly && !excluded);
        insurance.setManaged(insurance.isVisible());
        trigger.setDisable(category.getValue() == Category.DEATH_BENEFIT);
        deathTriggerHelp.setVisible(trigger.isDisabled());
        deathTriggerHelp.setManaged(trigger.isDisabled());
        clearFeedback();
    }

    private void field(String title, Control control, String id, boolean mandatory, String help) {
        control.setId("cb-" + id);
        control.setAccessibleText(title);
        control.setMinWidth(0);
        control.setMaxWidth(Double.MAX_VALUE);
        InputHelp.install(control, help);
        Label label = new Label(title);
        label.setWrapText(true);
        InputHelp.link(label, control);
        Label error = text("");
        error.setId("cb-" + id + "-error");
        error.getStyleClass().add("wizard-field-error");
        error.managedProperty().bind(error.textProperty().isNotEmpty());
        error.visibleProperty().bind(error.managedProperty());
        var row = new VBox(4, label, control, error);
        fields.put(control, new Field(label, control, error, row));
        if (mandatory) required.add(RequiredFieldSupport.attach(label, control, control.visibleProperty()));
        form.getChildren().add(row);
    }

    private record Field(Label label, Control control, Label error, VBox row) {
        void show(boolean value) {
            row.setVisible(value);
            row.setManaged(value);
            label.setVisible(value);
            label.setManaged(value);
            control.setVisible(value);
            control.setManaged(value);
        }
    }

    private boolean validateDraft() {
        clearFeedback();
        accepted = null;
        try {
            String label = name.getText() == null ? "" : name.getText().strip();
            if (label.isBlank()) throw invalid(name, "Benefit Name is required.");
            Category selectedCategory = requiredChoice(category);
            PaymentPattern selectedPattern = requiredChoice(pattern);
            TriggerType selectedTrigger = requiredChoice(trigger);
            boolean monthly = selectedPattern == PaymentPattern.MONTHLY_RECURRING;
            Trigger event = switch (selectedTrigger) {
                case FIXED_DATE -> new Trigger(selectedTrigger, date(triggerDate, true), null, null);
                case AGE -> new Trigger(selectedTrigger, null, requiredChoice(triggerPerson), integerAge());
                case DEATH -> new Trigger(selectedTrigger, null, requiredChoice(triggerPerson), null);
            };
            var selectedRecipient = requiredChoice(recipient);
            if (selectedTrigger == TriggerType.DEATH && selectedRecipient == event.person())
                throw invalid(recipient, "Select a different living recipient from the person whose death triggers payment.");
            var treatment = requiredChoice(tax);
            boolean excluded = treatment == TaxCharacter.QUALIFIED_LIFE_INSURANCE_DEATH_PRINCIPAL_EXCLUDED;
            if (excluded && !qualification.isSelected())
                throw invalid(qualification, "Confirm the restricted personal-policy principal qualification, or keep the payment fully taxable.");
            var definition = new ConditionalBenefitDefinition(identity, label, selectedCategory, event, selectedRecipient,
                    selectedPattern, decimal(amount), monthly ? date(notBefore, false) : null,
                    monthly ? date(end, false) : null, date(coverageStart, false), date(coverageEnd, false),
                    monthly ? decimal(escalation).movePointLeft(2) : BigDecimal.ZERO,
                    EscalationOrigin.FIRST_ELIGIBLE_PAYMENT_YEAR, treatment,
                    ReceiptDestination.HOUSEHOLD_RETAINED_NON_QUALIFIED_CASH,
                    excluded ? LifeInsuranceQualification.PERSONAL_LIFE_PRINCIPAL_ONLY_V1 : null);
            definition.validateFor(plan.getHousehold());
            accepted = definition;
            return true;
        } catch (DraftInvalid invalid) {
            feedback(invalid.field, invalid.getMessage());
        } catch (IllegalArgumentException | NullPointerException invalid) {
            // These are the closed domain's safe validation explanations, not persistence exceptions.
            String message = invalid.getMessage() == null ? "Review this benefit's required fields." : invalid.getMessage();
            Control field = message.contains("label") ? name : message.contains("Gross amount") ? amount
                    : message.contains("Escalation") ? escalation : message.contains("Age trigger") || message.contains("birth") ? age
                    : message.contains("Coverage") || message.contains("coverage") || message.contains("January 1") ? coverageEnd
                    : message.contains("ends") || message.contains("end precedes") ? end : trigger;
            feedback(field, message);
        }
        return false;
    }

    private int integerAge() {
        String value = age.getText().strip();
        if (!value.matches("[0-9]{1,3}")) throw invalid(age, "Enter a whole-number age from 0 through 120.");
        int parsed = Integer.parseInt(value);
        if (parsed > 120) throw invalid(age, "Enter a whole-number age from 0 through 120.");
        return parsed;
    }

    private BigDecimal decimal(TextField field) {
        try {
            return new BigDecimal(field.getText().trim());
        } catch (NumberFormatException invalid) {
            throw invalid(field, "Enter a valid decimal number without currency symbols or grouping commas.");
        }
    }

    private LocalDate date(DatePicker picker, boolean mandatory) {
        String input = picker.getEditor().getText().strip();
        if (input.isEmpty()) {
            if (mandatory) throw invalid(picker, "This date is required (yyyy-MM-dd).");
            return null;
        }
        try {
            if (!input.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
            LocalDate result = LocalDate.parse(input);
            if (result.getYear() < 1000 || result.getYear() > 9999) throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException invalid) {
            throw invalid(picker, "Enter a valid ISO date (yyyy-MM-dd), with a year from 1000 through 9999.");
        }
    }

    private <T> T requiredChoice(ComboBox<T> control) {
        if (control.getValue() == null) throw invalid(control, fields.get(control).label().getText() + " is required.");
        if (!control.getItems().contains(control.getValue())) throw invalid(control, "Select one of the supported choices.");
        return control.getValue();
    }

    private void clearFeedback() {
        status.setText("");
        for (Field field : fields.values()) {
            field.error().setText("");
            field.control().pseudoClassStateChanged(INVALID, false);
        }
    }

    private void feedback(Control control, String message) {
        fields.get(control).error().setText(message);
        control.pseudoClassStateChanged(INVALID, true);
        status.setText("Benefit not saved. " + message);
        control.requestFocus();
        Platform.runLater(() -> {
            form.applyCss();
            form.layout();
            scroll.layout();
            var bounds = fields.get(control).row().getBoundsInParent();
            double range = form.getHeight() - scroll.getViewportBounds().getHeight();
            if (range > 0) scroll.setVvalue(Math.max(0, Math.min(1, (bounds.getMinY() - 12) / range)));
        });
    }

    private static DraftInvalid invalid(Control field, String message) {
        return new DraftInvalid(field, message);
    }

    private static final class DraftInvalid extends RuntimeException {
        private final Control field;
        DraftInvalid(Control field, String message) {
            super(message);
            this.field = field;
        }
    }

    private static DatePicker datePicker() {
        var picker = new DatePicker();
        picker.setPromptText("yyyy-MM-dd");
        picker.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate value) { return value == null ? "" : value.toString(); }
            @Override public LocalDate fromString(String value) {
                return value == null || value.isBlank() ? null : LocalDate.parse(value.strip());
            }
        });
        return picker;
    }

    private static <T> void choices(ComboBox<T> combo, List<T> values, java.util.function.Function<T, String> display) {
        combo.getItems().setAll(values);
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(T value) { return value == null ? "" : display.apply(value); }
            @Override public T fromString(String value) { throw new UnsupportedOperationException(); }
        });
    }

    private static Label text(String value) {
        var label = new Label(value);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private static Label heading(String value) {
        var label = text(value);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }
}
