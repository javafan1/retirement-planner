package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class NewRetirementPlanWizardTest {

    private static final LocalDate PRIMARY_DATE = LocalDate.of(1964, 5, 6);
    private static final LocalDate SPOUSE_DATE = LocalDate.of(1966, 7, 8);
    @TempDir Path directory;

    @BeforeAll
    static void init() throws Exception { startFx(); }

    @Test
    void beginsWithOnlyPrimaryRequiredIndicatorsAndNoEarlyErrors() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            try {
                var pane = wizard.getDialogPane();
                assertNotNull(person(pane, "primary"));
                assertNull(person(pane, "spouse"));
                assertNotNull(pane.lookup("#wizard-add-spouse"));
                assertNull(pane.lookup("#wizard-remove-spouse"));
                var labels = descendants(pane).filter(Label.class::isInstance)
                        .map(Label.class::cast).map(Label::getText).toList();
                assertTrue(labels.contains("Birth Date *"));
                assertTrue(labels.contains("Mortality category *"));
                assertTrue(labels.contains("First Name:"));
                assertTrue(labels.contains("Last Name:"));
                assertTrue(labels.stream().anyMatch(text -> text.startsWith("* Required")));
                assertFalse(descendants(pane).anyMatch(node -> node.getPseudoClassStates().contains(PseudoClass.getPseudoClass("invalid"))));
                assertFalse(button(pane, "Back").isVisible());
                assertFalse(button(pane, "Next").isVisible());
                assertTrue(button(pane, "Create Plan").isVisible());
                assertTrue(labels.contains("Step 1 of 1 — Household"));
            }
            finally { wizard.close(); }
        });
    }

    @Test
    void invalidCreateStaysOpenAndIdentifiesBirthDateThenCategory() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            try {
                var pane = wizard.getDialogPane();
                button(pane, "Create Plan").fire();
                assertTrue(wizard.isShowing());
                assertNull(wizard.getResult());
                var card = person(pane, "primary");
                DatePicker date = field(card, "birthDatePicker");
                assertTrue(date.getPseudoClassStates().contains(PseudoClass.getPseudoClass("invalid")));
                assertEquals("Birth date is required.", ((Label) field(card, "birthDateError")).getText());
                date.setValue(PRIMARY_DATE);
                button(pane, "Create Plan").fire();
                ComboBox<?> category = field(card, "mortalityCategory");
                assertTrue(category.getPseudoClassStates().contains(PseudoClass.getPseudoClass("invalid")));
                assertEquals("Mortality category is required.", ((Label) field(card, "mortalityCategoryError")).getText());
                assertTrue(((Label) pane.lookup("#wizard-validation")).getText().startsWith("Primary Person:"));
            }
            finally { wizard.close(); }
        });
    }

    @Test
    void malformedTypedBirthDateIsBlockedUsingExistingParser() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            try {
                fill(person(wizard.getDialogPane(), "primary"), "Alex", "Example", PRIMARY_DATE, MortalityCategory.FEMALE);
                DatePicker date = field(person(wizard.getDialogPane(), "primary"), "birthDatePicker");
                date.getEditor().setText("invalid date");
                button(wizard.getDialogPane(), "Create Plan").fire();
                assertTrue(wizard.isShowing());
                assertNull(wizard.getResult());
                assertEquals("Please enter a valid birth date.",
                        ((Label) field(person(wizard.getDialogPane(), "primary"), "birthDateError")).getText());
            }
            finally { wizard.close(); }
        });
    }

    @Test
    void namesRemainOptionalAndTypedDateAndFemaleCategoryCreateOnePerson() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            var pane = wizard.getDialogPane();
            var card = person(pane, "primary");
            ComboBox<MortalityCategory> category = field(card, "mortalityCategory");
            category.setValue(MortalityCategory.FEMALE);
            DatePicker date = field(card, "birthDatePicker");
            date.getEditor().setText(date.getConverter().toString(PRIMARY_DATE));
            button(pane, "Create Plan").fire();
            assertFalse(wizard.isShowing());
            var plan = wizard.getResult();
            assertNotNull(plan);
            assertEquals(1, plan.getHousehold().members().size());
            assertFalse(plan.getHousehold().hasSpouse());
            assertEquals("", plan.getHousehold().getPrimaryPerson().getFirstName());
            assertEquals("", plan.getHousehold().getPrimaryPerson().getLastName());
            assertEquals(PRIMARY_DATE, plan.getHousehold().getPrimaryPerson().getBirthDate());
            assertEquals(MortalityCategory.FEMALE, plan.getHousehold().getPrimaryPerson().getMortalityCategory());
            assertTrue(plan.getAccountPortfolio().getAccounts().isEmpty());
            assertTrue(plan.getHousehold().getPrimaryPerson().getIncomeSources().isEmpty());
            assertNull(plan.getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
        });
    }

    @Test
    void addSpouseShowsRequiredFormAndBlocksIncompleteSpouseAtomically() throws Exception {
        fx(() -> {
            var draft = new NewPlanDraft();
            var step = new HouseholdWizardStep(draft);
            var wizard = new NewRetirementPlanWizard(null, draft, List.of(step));
            wizard.show();
            try {
                var pane = wizard.getDialogPane();
                fill(person(pane, "primary"), "Alex", "Example", PRIMARY_DATE, MortalityCategory.MALE);
                ((Button) pane.lookup("#wizard-add-spouse")).fire();
                assertNotNull(person(pane, "spouse"));
                assertFalse(pane.lookup("#wizard-add-spouse").isVisible());
                assertEquals(2, draft.getPlan().getHousehold().members().size());
                button(pane, "Create Plan").fire();
                assertTrue(wizard.isShowing());
                assertTrue(((Label) pane.lookup("#wizard-validation")).getText().startsWith("Spouse Person: Birth date"));
                assertNull(draft.getPlan().getHousehold().getPrimaryPerson().getBirthDate());
                DatePicker spouseDate = field(person(pane, "spouse"), "birthDatePicker");
                spouseDate.setValue(SPOUSE_DATE);
                button(pane, "Create Plan").fire();
                assertTrue(((Label) pane.lookup("#wizard-validation")).getText().contains("Mortality category is required"));
                assertNull(wizard.getResult());
            }
            finally { wizard.close(); }
        });
    }

    @Test
    void removeSpouseDiscardsEntriesAndCreatesOnlyPrimary() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            var pane = wizard.getDialogPane();
            fill(person(pane, "primary"), "Alex", "Example", PRIMARY_DATE, MortalityCategory.MALE);
            ((Button) pane.lookup("#wizard-add-spouse")).fire();
            fill(person(pane, "spouse"), "Sam", "Example", SPOUSE_DATE, MortalityCategory.FEMALE);
            ((Button) pane.lookup("#wizard-remove-spouse")).fire();
            assertNull(person(pane, "spouse"));
            assertTrue(pane.lookup("#wizard-add-spouse").isVisible());
            ((Button) pane.lookup("#wizard-add-spouse")).fire();
            assertEquals("", ((TextField) field(person(pane, "spouse"), "firstNameField")).getText());
            ((Button) pane.lookup("#wizard-remove-spouse")).fire();
            button(pane, "Create Plan").fire();
            assertEquals(1, wizard.getResult().getHousehold().members().size());
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void createdPlansPreserveFactoryDefaultsAndRoundTripPersonValues(boolean couple) throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            var pane = wizard.getDialogPane();
            fill(person(pane, "primary"), "Alex", "Example", PRIMARY_DATE, MortalityCategory.MALE);
            if (couple) {
                ((Button) pane.lookup("#wizard-add-spouse")).fire();
                fill(person(pane, "spouse"), "Sam", "Example", SPOUSE_DATE, MortalityCategory.FEMALE);
            }
            button(pane, "Create Plan").fire();
            var plan = wizard.getResult();
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            assertEquals(mapper.valueToTree(RetirementPlanFactory.createSinglePersonPlan().getPlanningAssumptions()),
                    mapper.valueToTree(plan.getPlanningAssumptions()));
            var repository = new JsonRetirementPlanRepository();
            Path file = directory.resolve(couple ? "couple.json" : "single.json");
            repository.save(plan, file);
            var reloaded = repository.load(file);
            assertEquals(couple ? 2 : 1, reloaded.getHousehold().members().size());
            assertEquals(couple, reloaded.getHousehold().hasSpouse());
            assertEquals("Alex", reloaded.getHousehold().getPrimaryPerson().getFirstName());
            assertEquals("Example", reloaded.getHousehold().getPrimaryPerson().getLastName());
            assertEquals(PRIMARY_DATE, reloaded.getHousehold().getPrimaryPerson().getBirthDate());
            assertEquals(MortalityCategory.MALE, reloaded.getHousehold().getPrimaryPerson().getMortalityCategory());
            if (couple) {
                assertEquals("Sam", reloaded.getHousehold().getSpouse().getFirstName());
                assertEquals("Example", reloaded.getHousehold().getSpouse().getLastName());
                assertEquals(SPOUSE_DATE, reloaded.getHousehold().getSpouse().getBirthDate());
                assertEquals(MortalityCategory.FEMALE, reloaded.getHousehold().getSpouse().getMortalityCategory());
            }
            assertEquals(mapper.valueToTree(plan), mapper.valueToTree(reloaded));
        });
    }

    @Test
    void allPersonInputsHaveHelpWithoutDuplicateHelpIcons() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            try {
                var pane = wizard.getDialogPane();
                ((Button) pane.lookup("#wizard-add-spouse")).fire();
                for (String id : List.of("primary", "spouse")) {
                    var card = person(pane, id);
                    for (String field : List.of("firstNameField", "lastNameField", "birthDatePicker", "mortalityCategory")) {
                        Control input = field(card, field);
                        assertNotNull(input.getTooltip());
                        assertFalse(input.getTooltip().getText().isBlank());
                    }
                }
                assertFalse(descendants(pane).anyMatch(node -> node.getStyleClass().contains("help-icon")));
            }
            finally { wizard.close(); }
        });
    }

    @Test
    void multipleImplementedStepsSupportValidationBackProgressAndFinalRevalidation() throws Exception {
        fx(() -> {
            var draft = new NewPlanDraft();
            var primary = draft.getPlan().getHousehold().getPrimaryPerson();
            primary.setBirthDate(PRIMARY_DATE);
            primary.setMortalityCategory(MortalityCategory.MALE);
            StubStep first = new StubStep("First");
            StubStep second = new StubStep("Second");
            var wizard = new NewRetirementPlanWizard(null, draft, List.of(first, second));
            wizard.show();
            try {
                var pane = wizard.getDialogPane();
                button(pane, "Next").fire();
                assertSame(first.content(), ((ScrollPane) pane.lookup("#wizard-page")).getContent());
                first.valid.set(true);
                first.input.setText("Retained draft text");
                button(pane, "Next").fire();
                assertSame(second.content(), ((ScrollPane) pane.lookup("#wizard-page")).getContent());
                assertEquals(1, ((ProgressBar) pane.lookup("#wizard-progress")).getProgress());
                button(pane, "Back").fire();
                assertEquals("Retained draft text", first.input.getText());
                button(pane, "Next").fire();
                first.valid.set(false);
                second.valid.set(true);
                button(pane, "Create Plan").fire();
                assertTrue(wizard.isShowing());
                assertSame(first.content(), ((ScrollPane) pane.lookup("#wizard-page")).getContent());
                first.valid.set(true);
                button(pane, "Next").fire();
                button(pane, "Create Plan").fire();
                assertSame(draft.getPlan(), wizard.getResult());
            }
            finally { wizard.close(); }
        });
    }

    @Test
    void draftCompletionCannotBypassPersonValidationAndEmptyWizardIsRejected() throws Exception {
        fx(() -> {
            var draft = new NewPlanDraft();
            assertThrows(IllegalArgumentException.class, draft::complete);
            assertThrows(IllegalArgumentException.class,
                    () -> new NewRetirementPlanWizard(null, draft, List.of()));
        });
    }

    static Stream<Node> descendants(Node node) {
        return Stream.concat(Stream.of(node), node instanceof Parent parent
                ? parent.getChildrenUnmodifiable().stream().flatMap(NewRetirementPlanWizardTest::descendants)
                : Stream.empty());
    }

    private static final class StubStep implements NewPlanWizardStep {
        private final String title;
        private final TextField input = new TextField();
        private final VBox content = new VBox(input);
        private final AtomicBoolean valid = new AtomicBoolean();

        private StubStep(String title) { this.title = title; }
        public String title() { return title; }
        public Node content() { return content; }
        public boolean validateAndApply() { return valid.get(); }
    }
}
