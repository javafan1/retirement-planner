package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static com.daviddunn.retirementplanner.ui.dialogs.ConditionalBenefitUiTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class LifeInsuranceDialogTest {
    static final Path OUTPUT=Path.of("target/phase23b/previews");
    @BeforeAll static void initialize() throws Exception { startFx(); Files.createDirectories(OUTPUT); }
    static CheckBox acknowledgment(ConditionalBenefitDialog d) { return (CheckBox)d.getDialogPane().lookup("#cb-life-qualification"); }
    static void excluded(ConditionalBenefitDialog d) {
        configure(d,TriggerType.DEATH,PaymentPattern.ONE_TIME_LUMP_SUM);
        choice(d,"category").setValue(Category.DEATH_BENEFIT);
        choice(d,"tax-character").setValue(TaxCharacter.QUALIFIED_LIFE_INSURANCE_DEATH_PRINCIPAL_EXCLUDED);
    }
    @Test void qualificationIsRequiredAndUiCreatedReceiptExecutesThroughTheFinancialEngine() throws Exception {
        var plan=LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.FL); plan.setConditionalBenefits(List.of());
        var benefit=fx(()-> {
            var d=new ConditionalBenefitDialog(plan,null); d.show();
            try {
                excluded(d); ((Button)d.getDialogPane().lookupButton(ConditionalBenefitDialog.SAVE)).fire();
                assertTrue(d.isShowing()); assertFalse(((Label)d.getDialogPane().lookup("#cb-life-qualification-error")).getText().isBlank());
                assertEquals("400000",input(d,"amount").getText());
                acknowledgment(d).setSelected(true); ((Button)d.getDialogPane().lookupButton(ConditionalBenefitDialog.SAVE)).fire();
                assertNotNull(d.getResult()); return d.getResult();
            } finally { d.close(); }
        });
        plan.setConditionalBenefits(List.of(benefit)); var year=new ProjectionEngine().project(plan).getYearAt(1);
        assertEquals(0,year.getAdjustedGrossIncome().signum());
        assertEquals(new java.math.BigDecimal("400000.00"),year.getGuaranteedIncome());
    }
    @Test void draftChangesNeverSilentlyCommitAnIncompatibleClassificationAndCancelPreservesOriginal() throws Exception {
        fx(()-> {
            var p=LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.FL); var original=p.getConditionalBenefits().getFirst();
            var d=new ConditionalBenefitDialog(p,original); d.show();
            try {
                assertEquals(original.taxCharacter(),choice(d,"tax-character").getValue());
                choice(d,"pattern").setValue(PaymentPattern.MONTHLY_RECURRING);
                assertEquals(TaxCharacter.FULLY_TAXABLE_ORDINARY_NON_PENSION,choice(d,"tax-character").getValue());
                assertFalse(acknowledgment(d).isVisible());
                choice(d,"pattern").setValue(PaymentPattern.ONE_TIME_LUMP_SUM);
                assertEquals(original.taxCharacter(),choice(d,"tax-character").getValue(),"Exploratory changes keep the detached draft");
                input(d,"amount").setText("450000"); ((Button)d.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();
                assertNull(d.getResult()); assertEquals(List.of(original),p.getConditionalBenefits());
            } finally { d.close(); }
            d=new ConditionalBenefitDialog(p,original,true); d.show();
            try { assertFalse(acknowledgment(d).isSelected(),"A new independent copy requires its own acknowledgment"); }
            finally { d.close(); }
            return null;
        });
    }
    @Test void legacyTaxableDefinitionIsNeverAutomaticallyPromotedByAnInsuranceName() throws Exception {
        fx(()-> {
            var p=plan(true); var d=new ConditionalBenefitDialog(p,null); d.show();
            try {
                configure(d,TriggerType.DEATH,PaymentPattern.ONE_TIME_LUMP_SUM);
                choice(d,"category").setValue(Category.DEATH_BENEFIT); input(d,"name").setText("Life Insurance");
                assertEquals(TaxCharacter.FULLY_TAXABLE_ORDINARY_NON_PENSION,choice(d,"tax-character").getValue());
                ((Button)d.getDialogPane().lookupButton(ConditionalBenefitDialog.SAVE)).fire();
                assertFalse(d.getResult().excludesLifePrincipal()); assertNull(d.getResult().lifeInsuranceQualification());
            } finally { d.close(); } return null;
        });
    }
    @Test void actualLightDarkCompactAndStandardScenesExposeQualificationWithFixedFooter() throws Exception {
        for(var theme:ApplicationTheme.values()) for(int width:new int[]{900,1180}) {
            var manager=fx(()->ApplicationThemeManager.start(theme));
            var dialog=fx(()-> {
                var d=new ConditionalBenefitDialog(LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.FL),LifeInsurancePrincipalTest.principal());
                d.getDialogPane().setPrefSize(width,width==900?650:820); d.show();
                ((Stage)d.getDialogPane().getScene().getWindow()).sizeToScene(); return d;
            });
            try {
                pulses(fx(()->dialog.getDialogPane().getScene()));
                fx(()-> { ((ScrollPane)dialog.getDialogPane().lookup("#cb-form-scroll")).setVvalue(1); return null; });
                pulses(fx(()->dialog.getDialogPane().getScene()));
                fx(()-> {
                    var pane=dialog.getDialogPane(); var save=pane.lookupButton(ConditionalBenefitDialog.SAVE);
                    var bounds=save.localToScene(save.getBoundsInLocal());
                    assertTrue(bounds.getMaxY()<=pane.getScene().getHeight()+1);
                    assertTrue(acknowledgment(dialog).isVisible()); assertTrue(acknowledgment(dialog).isWrapText());
                    var image=pane.snapshot(null,null);
                    var buffer=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<buffer.getHeight();y++) for(int x=0;x<buffer.getWidth();x++) buffer.setRGB(x,y,image.getPixelReader().getArgb(x,y));
                    javax.imageio.ImageIO.write(buffer,"png",OUTPUT.resolve("qualified-principal-"+theme+"-"+width+".png").toFile());
                    return null;
                });
            } finally { fx(()-> { dialog.close(); manager.close(); return null; }); }
        }
    }
    static void pulses(Scene scene) throws Exception {
        var complete=new CompletableFuture<Void>();
        fx(()-> { scene.addPostLayoutPulseListener(new Runnable() {
            int remaining=3;
            public void run() { if(--remaining==0) { scene.removePostLayoutPulseListener(this); complete.complete(null); }
                else Platform.requestNextPulse(); }
        }); Platform.requestNextPulse(); return null; });
        complete.get(15,TimeUnit.SECONDS);
    }
}
