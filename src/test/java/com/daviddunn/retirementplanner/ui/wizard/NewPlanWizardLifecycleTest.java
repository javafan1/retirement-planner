package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.ui.MainWindow;
import com.daviddunn.retirementplanner.ui.components.PersonCard;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.views.AssumptionsView;
import com.daviddunn.retirementplanner.ui.views.HouseholdView;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class NewPlanWizardLifecycleTest {

    @TempDir Path directory;

    @BeforeAll
    static void init() throws Exception { startFx(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void newMenuOpensWizardAndCancelPreservesPlanFileDirtyRevisionCacheAndTabDrafts(boolean dirty) throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            var current = f.controller.getCurrentPlan();
            current.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1964, 1, 1));
            current.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.MALE);
            current.getAccountPortfolio().addAccount(new BrokerageAccount("Funding", AccountOwnership.PRIMARY,
                    new BigDecimal("10000000")));
            f.controller.saveAs(directory.resolve("current.json"));
            String saved = Files.readString(f.controller.getCurrentFile());
            var projection = f.controller.getCurrentProjection();
            if (dirty) f.controller.markModified();
            var revision = f.controller.getSourcePlanRevision();
            var file = f.controller.getCurrentFile();
            TextField inflation = field(f.assumptions, "inflationRateField");
            inflation.setText("unapplied invalid draft");
            var tabs = (TabPane) f.root.getCenter();
            tabs.getSelectionModel().select(2);
            var selected = tabs.getSelectionModel().getSelectedItem();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Platform.runLater(() -> {
                DialogPane pane = activeWizardPane();
                try {
                    assertSame(current, f.controller.getCurrentPlan());
                    assertEquals(dirty, f.controller.isModified());
                    fill(person(pane, "primary"), "Draft", "Only", LocalDate.of(1965, 2, 3), MortalityCategory.FEMALE);
                    ((Button) pane.lookup("#wizard-add-spouse")).fire();
                    assertSame(current, f.controller.getCurrentPlan());
                    assertFalse(current.getHousehold().hasSpouse());
                    assertSame(projection, f.controller.peekCurrentProjection());
                    assertEquals(revision, f.controller.getSourcePlanRevision());
                    assertEquals("unapplied invalid draft", inflation.getText());
                }
                catch (Throwable exception) { failure.set(exception); }
                finally { button(pane, "Cancel").fire(); }
            });
            f.newItem().fire();
            if (failure.get() != null) throw new AssertionError(failure.get());
            assertSame(current, f.controller.getCurrentPlan());
            assertEquals(dirty, f.controller.isModified());
            assertSame(projection, f.controller.peekCurrentProjection());
            assertEquals(revision, f.controller.getSourcePlanRevision());
            assertEquals(file, f.controller.getCurrentFile());
            assertEquals(saved, Files.readString(file));
            assertEquals("unapplied invalid draft", inflation.getText());
            assertTrue(f.assumptions.isDirty());
            assertSame(selected, tabs.getSelectionModel().getSelectedItem());
        });
    }

    @Test
    void invalidCreateDoesNotActivateAndWindowCloseCancels() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            var current = f.controller.getCurrentPlan();
            long revision = f.controller.getSourcePlanRevision();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Platform.runLater(() -> {
                DialogPane pane = activeWizardPane();
                try {
                    button(pane, "Next").fire();
                    assertTrue(pane.getScene().getWindow().isShowing());
                    assertSame(current, f.controller.getCurrentPlan());
                    assertEquals(revision, f.controller.getSourcePlanRevision());
                }
                catch (Throwable exception) { failure.set(exception); }
                finally { ((Stage) pane.getScene().getWindow()).close(); }
            });
            f.newItem().fire();
            if (failure.get() != null) throw new AssertionError(failure.get());
            assertSame(current, f.controller.getCurrentPlan());
            assertFalse(f.controller.isModified());
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void validCreateActivatesUnsavedPlanLoadsHouseholdAndProjectsWhenFunded(boolean couple) throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            var before = f.controller.getCurrentPlan();
            var revision = f.controller.getSourcePlanRevision();
            AtomicInteger revisions = new AtomicInteger();
            f.controller.addSourcePlanRevisionListener(revisions::incrementAndGet);
            answerCreate(couple, null);
            f.newItem().fire();
            var plan = f.controller.getCurrentPlan();
            assertNotSame(before, plan);
            assertEquals(1, revisions.get());
            assertEquals(revision + 1, f.controller.getSourcePlanRevision());
            assertFalse(f.controller.hasCurrentFile());
            assertFalse(f.controller.isModified());
            assertEquals(couple ? 2 : 1, plan.getHousehold().members().size());
            assertEquals(couple, plan.getHousehold().hasSpouse());
            assertEquals("Alex", plan.getHousehold().getPrimaryPerson().getFirstName());
            assertEquals("Example", plan.getHousehold().getPrimaryPerson().getLastName());
            assertEquals(MortalityCategory.FEMALE, plan.getHousehold().getPrimaryPerson().getMortalityCategory());
            PersonCard primary = field(f.household, "primaryPersonCard");
            assertEquals("Alex", ((TextField) field(primary, "firstNameField")).getText());
            assertEquals("Example", ((TextField) field(primary, "lastNameField")).getText());
            assertEquals(LocalDate.of(1964, 5, 6), ((DatePicker) field(primary, "birthDatePicker")).getValue());
            assertEquals(MortalityCategory.FEMALE, ((ComboBox<?>) field(primary, "mortalityCategory")).getValue());
            CheckBox includeSpouse = field(f.household, "includeSpouse");
            assertEquals(couple, includeSpouse.isSelected());
            if (couple) {
                PersonCard spouse = field(f.household, "spousePersonCard");
                assertEquals("Sam", ((TextField) field(spouse, "firstNameField")).getText());
                assertEquals(LocalDate.of(1966, 7, 8), ((DatePicker) field(spouse, "birthDatePicker")).getValue());
                assertEquals(MortalityCategory.MALE, ((ComboBox<?>) field(spouse, "mortalityCategory")).getValue());
            }
            var tabs = (TabPane) f.root.getCenter();
            assertSame(f.household, tabs.getSelectionModel().getSelectedItem().getContent());
            assertFalse(f.household.isDirty());
            assertFalse(f.assumptions.isDirty());
            plan.getAccountPortfolio().addAccount(new BrokerageAccount("Funding", AccountOwnership.PRIMARY,
                    new BigDecimal("10000000")));
            f.controller.invalidateProjection();
            assertTrue(f.controller.isCurrentPlanReadyForProjection());
            assertEquals(40, f.controller.getCurrentProjection().getYears().size());
            f.controller.saveAs(directory.resolve(couple ? "wizard-couple.json" : "wizard-single.json"));
            var reloaded = new JsonRetirementPlanRepository().load(f.controller.getCurrentFile());
            assertEquals(couple ? 2 : 1, reloaded.getHousehold().members().size());
        });
    }

    @Test
    void createStillHonorsUnsavedDepartureCancelWithoutDiscardingPendingEdits() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            var before = f.controller.getCurrentPlan();
            f.controller.markModified();
            TextField inflation = field(f.assumptions, "inflationRateField");
            inflation.setText("unapplied invalid draft");
            long revision = f.controller.getSourcePlanRevision();
            answerCreate(false, "Cancel");
            f.newItem().fire();
            assertSame(before, f.controller.getCurrentPlan());
            assertTrue(f.controller.isModified());
            assertEquals(revision, f.controller.getSourcePlanRevision());
            assertEquals("unapplied invalid draft", inflation.getText());
            assertTrue(f.assumptions.isDirty());
        });
    }

    @Test
    void createDiscardResetsOldDraftsAndFileOnlyAfterValidHousehold() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            var before = f.controller.getCurrentPlan();
            f.controller.saveAs(directory.resolve("old.json"));
            TextField inflation = field(f.assumptions, "inflationRateField");
            inflation.setText("unapplied invalid draft");
            answerCreate(false, "Discard");
            f.newItem().fire();
            assertNotSame(before, f.controller.getCurrentPlan());
            assertFalse(f.controller.hasCurrentFile());
            assertFalse(f.controller.isModified());
            assertFalse(f.assumptions.isDirty());
            assertNotEquals("unapplied invalid draft", inflation.getText());
            assertTrue(Files.exists(directory.resolve("old.json")));
        });
    }

    @Test
    void openExistingPlanStillBypassesWizardAndKeepsLegacyNullMortality() throws Exception {
        fx(() -> {
            Fixture f = new Fixture();
            var repository = new JsonRetirementPlanRepository();
            Path file = directory.resolve("legacy.json");
            repository.save(f.controller.getCurrentPlan(), file);
            f.controller.open(file);
            var method = MainWindow.class.getDeclaredMethod("loadCurrentPlan");
            method.setAccessible(true);
            method.invoke(f.window);
            assertFalse(f.controller.getCurrentPlan().getHousehold().hasSpouse());
            assertNull(f.controller.getCurrentPlan().getHousehold().getPrimaryPerson().getMortalityCategory());
            assertEquals(file, f.controller.getCurrentFile());
            assertFalse(f.controller.isModified());
            assertTrue(Window.getWindows().stream().noneMatch(window -> window.isShowing()
                    && "new-plan-wizard".equals(window.getScene().getRoot().getId())));
        });
    }

    private static final class Fixture {
        final MainWindow window = new MainWindow();
        final ApplicationController controller;
        final HouseholdView household;
        final AssumptionsView assumptions;
        final BorderPane root;

        Fixture() throws Exception {
            controller = field(window, "controller");
            household = field(window, "householdView");
            assumptions = field(window, "assumptionsView");
            root = (BorderPane) window.createScene().getRoot();
        }

        MenuItem newItem() {
            return ((MenuBar) root.getTop()).getMenus().getFirst().getItems().stream()
                    .filter(item -> "New".equals(item.getText())).findFirst().orElseThrow();
        }
    }
}
