package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static com.daviddunn.retirementplanner.ui.views.AssumptionsViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

class SurvivorBenefitClaimingControlsTest {
    @TempDir Path directory;

    @BeforeAll static void startFx() throws Exception { AssumptionsViewTest.startFx(); }

    @Test void assumptionsRefreshesChoicesPreservesValidSelectionAndKeepsRetirementIndependent() throws Exception {
        fx(() -> {
            Fixture f = fixture();
            var primaryIncome = List.copyOf(f.plan().getHousehold().getPrimaryPerson().getIncomeSources());
            var spouseIncome = List.copyOf(f.plan().getHousehold().getSpouse().getIncomeSources());
            ComboBox<Integer> ages = control(f.view, "survivorClaimingAgeComboBox");
            Label label = control(f.view, "survivorAgeLabel");
            assertEquals("Lisa — Survivor Benefit Claiming Age", label.getText());
            assertEquals(List.of(64, 65, 66, 67), ages.getItems());
            assertEquals(67, ages.getValue());
            field(f.view, "deathYearField").setText("2031");
            assertEquals(List.of(65, 66, 67), ages.getItems());
            assertEquals(67, ages.getValue());
            ages.setValue(65);
            field(f.view, "deathYearField").setText("2032");
            assertEquals(List.of(66, 67), ages.getItems());
            assertTrue(ages.getItems().contains(ages.getValue()));
            assertTrue(f.view.applyChanges());
            assertEquals(ages.getValue(), f.assumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
            field(f.view, "deathYearField").setText("2037");
            assertEquals(List.of(71), ages.getItems());
            assertEquals("Immediate at death (Age 71)", ages.getConverter().toString(ages.getValue()));
            assertTrue(ages.isDisabled());
            assertTrue(f.view.applyChanges());
            assertEquals(71, f.assumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
            ComboBox<DeathScenario> scenario = control(f.view, "deathScenarioComboBox");
            scenario.setValue(DeathScenario.SPOUSE_DIES);
            assertEquals("David — Survivor Benefit Claiming Age", label.getText());
            assertEquals(List.of(73), ages.getItems());
            assertTrue(f.view.applyChanges());
            assertEquals(primaryIncome, f.plan().getHousehold().getPrimaryPerson().getIncomeSources());
            assertEquals(spouseIncome, f.plan().getHousehold().getSpouse().getIncomeSources());
            scenario.setValue(DeathScenario.BOTH_SURVIVE);
            assertTrue(ages.isDisabled());
            assertTrue(ages.getItems().isEmpty());
            assertTrue(f.view.applyChanges());
            assertNull(f.assumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
        });
    }

    @Test void resultsSummaryRefreshesBothDeathDirections() throws Exception {
        fx(() -> {
            Fixture f = fixture();
            ResultsSummaryView summary = new ResultsSummaryView(f.controller);
            summary.setOnDeathScenarioApply(f.plan()::setPlanningAssumptions);
            summary.load(f.plan(), null, List.of());
            ComboBox<Integer> ages = control(summary, "survivorAgeComboBox");
            assertEquals(List.of(64, 65, 66, 67), ages.getItems());
            assertEquals(67, ages.getValue());
            field(summary, "deathYearField").setText("2024");
            assertEquals(60, ages.getItems().getFirst());
            assertEquals(67, ages.getValue());
            field(summary, "deathYearField").setText("2037");
            assertEquals(71, ages.getValue());
            Button apply = control(summary, "applyDeathButton");
            apply.fire();
            assertEquals(71, f.assumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
            ComboBox<String> scenario = control(summary, "deathScenarioComboBox");
            scenario.setValue("Spouse Dies");
            field(summary, "deathYearField").setText("2028");
            assertEquals(List.of(64, 65, 66, 67), ages.getItems());
            assertEquals("David — Survivor Benefit Claiming Age", ((Label) control(summary, "survivorAgeLabel")).getText());
            field(summary, "deathYearField").setText("2035");
            assertEquals(71, ages.getValue());
            assertTrue(ages.isDisabled());
            scenario.setValue("Both Survive");
            assertEquals("Not Applicable", ages.getPromptText());
            apply.fire();
            assertNull(f.assumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
        });
    }

    @Test void invalidAgeRequiresCorrectionAndExistingJsonPropertyRoundTripsNewBoundaryAges() throws Exception {
        fx(() -> {
            Fixture f = fixture();
            ComboBox<Integer> ages = control(f.view, "survivorClaimingAgeComboBox");
            ages.setValue(62);
            assertFalse(f.view.applyChanges());
            assertEquals(67, f.assumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
            var repository = new JsonRetirementPlanRepository();
            for (int year : List.of(2024, 2037)) {
                field(f.view, "deathYearField").setText(Integer.toString(year));
                ages.setValue(ages.getItems().getFirst());
                assertTrue(f.view.applyChanges());
                Path file = directory.resolve(year + ".json");
                repository.save(f.plan(), file);
                assertEquals(year == 2024 ? 60 : 71,
                        repository.load(file).getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge());
                String json = java.nio.file.Files.readString(file);
                assertTrue(json.contains("\"survivorClaimingAge\""));
                assertFalse(json.contains("primarySurvivorClaimingAge"));
                assertFalse(json.contains("spouseSurvivorClaimingAge"));
            }
        });
    }

    private static Fixture fixture() {
        Fixture f = new Fixture();
        f.plan().getHousehold().getPrimaryPerson().setFirstName("David");
        f.plan().getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1963, 6, 4));
        f.plan().getHousehold().getSpouse().setFirstName("Lisa");
        f.plan().getHousehold().getSpouse().setBirthDate(LocalDate.of(1965, 2, 28));
        var a = f.assumptions();
        f.plan().setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                a.getWithdrawalAssumptions(), new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2030, 67),
                a.getProjectionLengthYears(), a.getProjectionStartDate()));
        f.view.load(f.plan());
        return f;
    }
}
