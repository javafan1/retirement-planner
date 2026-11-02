package com.daviddunn.retirementplanner.ui.components;

import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.ui.controls.HelpIcon;
import com.daviddunn.retirementplanner.ui.controls.RequiredFieldSupport;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import java.time.LocalDate;
import java.util.Objects;
import java.util.List;

public class PersonCard extends GridPane {

    private final TextField firstNameField = new TextField();
    private final TextField lastNameField = new TextField();
    private final Label firstNameLabel = new Label("First Name:");
    private final Label lastNameLabel = new Label("Last Name:");
    private final DatePicker birthDatePicker = new DatePicker();
    private final ComboBox<MortalityCategory> mortalityCategory = new ComboBox<>();
    private final boolean guidedCreation;
    private final Label birthDateError = new Label();
    private final Label mortalityCategoryError = new Label();
    private final List<RequiredFieldSupport.Attachment> requiredFieldAttachments = new java.util.ArrayList<>();
    private final SimpleBooleanProperty personRequired = new SimpleBooleanProperty(true);

    public PersonCard() {

        this(false);
    }

    /** Required indicators and inline feedback are opt-in for guided new-plan creation. */
    public PersonCard(boolean guidedCreation) {

        this.guidedCreation = guidedCreation;
        configureBirthDateConverter();

        mortalityCategory.getItems().setAll(MortalityCategory.values());
        mortalityCategory.setPromptText("Required");
        mortalityCategory.setMaxWidth(Double.MAX_VALUE);
        mortalityCategory.setConverter(new StringConverter<>() {
            @Override
            public String toString(MortalityCategory value) {
                return value == null ? "" : value == MortalityCategory.MALE ? "Male" : "Female";
            }

            @Override
            public MortalityCategory fromString(String value) {
                return MortalityCategory.valueOf(value.toUpperCase(java.util.Locale.ROOT));
            }
        });
        mortalityCategory.setTooltip(HelpIcon.createTooltip(
                "Select the mortality-table category used for longevity and Social Security analysis."));

        birthDatePicker.valueProperty().addListener((observable, oldValue, newValue) ->
                birthDatePicker.getEditor().setText(
                        birthDatePicker.getConverter().toString(newValue)));
        setPadding(guidedCreation ? new Insets(6, 15, 6, 15) : new Insets(15));
        setHgap(10);
        setVgap(guidedCreation ? 8 : 10);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setHalignment(HPos.RIGHT);
        labelColumn.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);

        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        firstNameField.setMaxWidth(Double.MAX_VALUE);
        lastNameField.setMaxWidth(Double.MAX_VALUE);
        birthDatePicker.setMaxWidth(Double.MAX_VALUE);
        getColumnConstraints().addAll(labelColumn, fieldColumn);

        firstNameField.setPrefColumnCount(20);
        lastNameField.setPrefColumnCount(20);

        add(firstNameLabel, 0, 0);
        add(firstNameField, 1, 0);

        add(lastNameLabel, 0, 1);
        add(lastNameField, 1, 1);

        Label birthDateLabel = new Label("Birth Date:");
        add(birthDateLabel, 0, 2);
        add(birthDatePicker, 1, 2);
        int categoryRow = guidedCreation ? 4 : 3;
        Label mortalityLabel = new Label("Mortality category:");
        add(mortalityLabel, 0, categoryRow);
        InputHelp.install(birthDatePicker, "This person's birth date. Used to determine ages, Social Security eligibility and start dates, Medicare eligibility, RMD timing and longevity-model ages.");
        add(mortalityCategory, 1, categoryRow);
        if (guidedCreation) {
            labelColumn.setHalignment(HPos.LEFT);
            InputHelp.install(firstNameField, "First name shown in the plan's household and person details.");
            InputHelp.install(lastNameField, "Last name shown in the plan's household and person details.");
            configureError(birthDateError);
            configureError(mortalityCategoryError);
            add(birthDateError, 1, 3);
            add(mortalityCategoryError, 1, 5);
        }
        InputHelp.linkGridLabels(this);
        requiredFieldAttachments.add(RequiredFieldSupport.attach(birthDateLabel, birthDatePicker, personRequired));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(mortalityLabel, mortalityCategory, personRequired));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(firstNameLabel, firstNameField, personRequired));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(lastNameLabel, lastNameField, personRequired));
    }

    /** Birth-date entry uses 19xx for two-digit years, retaining localized date order and display. */
    private void configureBirthDateConverter() {
        var locale = java.util.Locale.getDefault(java.util.Locale.Category.FORMAT);
        String pattern = java.time.format.DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                java.time.format.FormatStyle.SHORT, null, java.time.chrono.IsoChronology.INSTANCE, locale);
        var year = java.util.regex.Pattern.compile("'(?:[^']|'')*'|y+").matcher(pattern);
        while (year.find()) {
            if (year.group().startsWith("'")) continue;
            var parser = new java.time.format.DateTimeFormatterBuilder().parseLenient()
                    .appendPattern(pattern.substring(0, year.start()))
                    .appendValueReduced(java.time.temporal.ChronoField.YEAR, 2, 4, 1900)
                    .appendPattern(pattern.substring(year.end())).toFormatter(locale);
            var display = birthDatePicker.getConverter();
            birthDatePicker.setConverter(new StringConverter<>() {
                @Override public String toString(LocalDate value) { return display.toString(value); }
                @Override public LocalDate fromString(String value) {
                    return value == null || value.isBlank() ? null : LocalDate.parse(value.trim(), parser);
                }
            });
            return;
        }
    }

    public void load(Person person) {

        if (person == null) {
            clear();
            return;
        }

        firstNameField.setText(person.getFirstName());
        lastNameField.setText(person.getLastName());
        birthDatePicker.setValue(person.getBirthDate());
        mortalityCategory.setValue(person.getMortalityCategory());
        birthDatePicker.getEditor().setText(
                birthDatePicker.getConverter().toString(person.getBirthDate()));
    }

    public void save(Person person) {

        if (person == null) {
            return;
        }

        readValidated().applyTo(person);
    }

    public void setOnEdited(Runnable listener) {

        mortalityCategory.valueProperty().addListener((observable, oldValue, newValue) -> listener.run());

        firstNameField.textProperty().addListener((observable, oldValue, newValue) -> listener.run());
        lastNameField.textProperty().addListener((observable, oldValue, newValue) -> listener.run());
        birthDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> listener.run());
        birthDatePicker.getEditor().textProperty().addListener((observable, oldValue, newValue) -> listener.run());
    }

    public boolean matches(Person person) {

        try {
            return Objects.equals(firstNameField.getText(), person.getFirstName())
                    && Objects.equals(lastNameField.getText(), person.getLastName())
                    && mortalityCategory.getValue() == person.getMortalityCategory()
                    && Objects.equals(readBirthDate(), person.getBirthDate());
        }
        catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public Edit readValidated() {

        firstNameField.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("invalid"), false);
        lastNameField.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("invalid"), false);
        if (guidedCreation) {
            clearError(birthDatePicker, birthDateError);
            clearError(mortalityCategory, mortalityCategoryError);
        }
        LocalDate birthDate = validateExistingPersonInformation();
        String firstName;
        String lastName;
        try {
            firstName = com.daviddunn.retirementplanner.domain.model.PersonInformationValidation
                    .validateName(firstNameField.getText(), "First name");
        } catch (IllegalArgumentException exception) {
            firstNameField.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("invalid"), true);
            firstNameField.requestFocus();
            throw exception;
        }
        try {
            lastName = com.daviddunn.retirementplanner.domain.model.PersonInformationValidation
                    .validateName(lastNameField.getText(), "Last name");
        } catch (IllegalArgumentException exception) {
            lastNameField.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("invalid"), true);
            lastNameField.requestFocus();
            throw exception;
        }
        return new Edit(firstName, lastName, birthDate,
                mortalityCategory.getValue());
    }

    /** Retains pre-existing date/category validation for untouched legacy profiles without requiring a name migration. */
    public LocalDate validateExistingPersonInformation() {
        LocalDate birthDate;
        try {
            birthDate = readBirthDate();
        } catch (IllegalArgumentException exception) {
            showError(birthDatePicker, birthDateError, exception.getMessage());
            birthDatePicker.requestFocus();
            throw exception;
        }
        try {
            com.daviddunn.retirementplanner.domain.model.PersonInformationValidation.validate(
                    birthDate, mortalityCategory.getValue());
        } catch (IllegalArgumentException exception) {
            if (birthDate == null) showError(birthDatePicker, birthDateError, exception.getMessage());
            else showError(mortalityCategory, mortalityCategoryError, exception.getMessage());
            if (birthDate == null) birthDatePicker.requestFocus();
            else mortalityCategory.requestFocus();
            throw exception;
        }
        return birthDate;
    }

    private LocalDate readBirthDate() {

        try {
            return birthDatePicker.getConverter().fromString(birthDatePicker.getEditor().getText());
        }
        catch (RuntimeException exception) {
            throw new IllegalArgumentException("Please enter a valid birth date.", exception);
        }
    }

    private void configureError(Label error) {

        error.getStyleClass().add("wizard-field-error");
        error.setWrapText(true);
        error.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());
    }

    private void showError(javafx.scene.control.Control input, Label error, String message) {

        if (guidedCreation) {
            input.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("invalid"), true);
            error.setText(message);
        }
    }

    private void clearError(javafx.scene.control.Control input, Label error) {

        input.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("invalid"), false);
        error.setText("");
    }

    public record Edit(String firstName, String lastName, LocalDate birthDate,
                       MortalityCategory mortalityCategory) {

        public Edit {
            firstName = com.daviddunn.retirementplanner.domain.model.PersonInformationValidation
                    .validateName(firstName, "First name");
            lastName = com.daviddunn.retirementplanner.domain.model.PersonInformationValidation
                    .validateName(lastName, "Last name");
            Objects.requireNonNull(mortalityCategory, "Mortality category is required.");
        }

        public void applyTo(Person person) {
            person.setFirstName(firstName);
            person.setLastName(lastName);
            person.setBirthDate(birthDate);
            person.setMortalityCategory(mortalityCategory);
        }
    }

    public void clear() {

        firstNameField.clear();
        mortalityCategory.setValue(null);
        lastNameField.clear();
        birthDatePicker.setValue(null);
        birthDatePicker.getEditor().clear();
    }

    /** Releases required-field listeners when the owning household form is disposed. */
    public void close() {
        requiredFieldAttachments.forEach(RequiredFieldSupport.Attachment::close);
        requiredFieldAttachments.clear();
    }

    /** Mirrors the owning household's existing inclusion validation condition. */
    public void setRequired(boolean required) {
        personRequired.set(required);
    }
}
