package com.daviddunn.retirementplanner.ui.components;

import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.ui.controls.HelpIcon;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import java.time.LocalDate;
import java.util.Objects;

public class PersonCard extends GridPane {

    private final TextField firstNameField = new TextField();
    private final TextField lastNameField = new TextField();
    private final DatePicker birthDatePicker = new DatePicker();
    private final ComboBox<MortalityCategory> mortalityCategory = new ComboBox<>();

    public PersonCard() {

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
        setPadding(new Insets(15));
        setHgap(10);
        setVgap(10);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setHalignment(HPos.RIGHT);

        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        firstNameField.setMaxWidth(Double.MAX_VALUE);
        lastNameField.setMaxWidth(Double.MAX_VALUE);
        birthDatePicker.setMaxWidth(Double.MAX_VALUE);
        getColumnConstraints().addAll(labelColumn, fieldColumn);

        firstNameField.setPrefColumnCount(20);
        lastNameField.setPrefColumnCount(20);

        add(new Label("First Name:"), 0, 0);
        add(firstNameField, 1, 0);

        add(new Label("Last Name:"), 0, 1);
        add(lastNameField, 1, 1);

        add(new Label("Birth Date:"), 0, 2);
        add(birthDatePicker, 1, 2);
        add(new Label("Mortality category:"), 0, 3);
        add(mortalityCategory, 1, 3);
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

        LocalDate birthDate;
        try {
            birthDate = readBirthDate();
        }
        catch (IllegalArgumentException exception) {
            birthDatePicker.requestFocus();
            throw exception;
        }
        if (birthDate == null) {
            birthDatePicker.requestFocus();
            throw new IllegalArgumentException("Birth date is required.");
        }
        // Person permits empty names and preserves whitespace. Keep those semantics.
        if (mortalityCategory.getValue() == null) {
            mortalityCategory.requestFocus();
            throw new IllegalArgumentException("Mortality category is required.");
        }
        return new Edit(firstNameField.getText(), lastNameField.getText(), birthDate,
                mortalityCategory.getValue());
    }

    private LocalDate readBirthDate() {

        try {
            return birthDatePicker.getConverter().fromString(birthDatePicker.getEditor().getText());
        }
        catch (RuntimeException exception) {
            throw new IllegalArgumentException("Please enter a valid birth date.", exception);
        }
    }

    public record Edit(String firstName, String lastName, LocalDate birthDate,
                       MortalityCategory mortalityCategory) {

        public Edit {
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
}
