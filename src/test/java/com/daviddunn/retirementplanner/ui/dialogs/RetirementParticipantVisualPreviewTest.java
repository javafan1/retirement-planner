package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.financial.Traditional401K;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.ui.controls.RetirementParticipantFactsEditor;
import javafx.scene.control.Dialog;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.file.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "participant.preview", matches = "true")
class RetirementParticipantVisualPreviewTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @Test void inspectDetailedAndGuidedOptionalFacts() throws Exception {
        var folder = Path.of("target/phase6d3a1-preview");
        Files.createDirectories(folder);
        fx(() -> {
            var household = new Household(new Person("Primary", "", LocalDate.of(1960, 1, 1)));
            var account = new Traditional401K("Employer retirement", AccountOwnership.PRIMARY, new BigDecimal("250000"));
            var pension = new Pension("Retirement pension", AccountOwnership.PRIMARY, LocalDate.of(2030, 1, 1), null,
                    new BigDecimal("2500"), new BigDecimal("0.02"));
            snapshot(new AccountDialog(account, household), "account-unknown", folder, true);
            snapshot(new PensionDialog(pension, household), "pension-unknown", folder, true);
            var facts = new RetirementParticipantFacts(RetirementParticipantFacts.SeparationStatus.SEPARATED,
                    LocalDate.of(2030, 1, 1), RetirementParticipantFacts.RetirementConditionsStatus.MET,
                    LocalDate.of(2031, 1, 1));
            account.setRetirementParticipantFacts(facts);
            pension.setRetirementParticipantFacts(facts);
            snapshot(new AccountDialog(account, household), "account-dated", folder, true);
            snapshot(new PensionDialog(pension, household), "pension-dated", folder, true);
            snapshot(new AccountDialog(account, household, true), "wizard-account", folder, false);
            snapshot(new PensionDialog(pension, household, true), "wizard-pension", folder, false);
        });
    }

    private static void snapshot(Dialog<?> dialog, String name, Path folder, boolean expanded) throws Exception {
        try {
            RetirementParticipantFactsEditor editor = field(dialog, "participantFactsEditor");
            editor.setExpanded(expanded);
            dialog.show();
            var pane = dialog.getDialogPane();
            pane.applyCss();
            pane.layout();
            assertTrue(pane.lookupAll(".help-icon").isEmpty());
            assertTrue(pane.getWidth() < 1000);
            assertTrue(pane.getHeight() < 800);
            var image = pane.snapshot(null, null);
            var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) {
                output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
            javax.imageio.ImageIO.write(output, "png", folder.resolve(name + ".png").toFile());
        } finally {
            dialog.close();
        }
    }
}
