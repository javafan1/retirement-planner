package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.projection.NonqualifiedAnnuityFinancialTest;
import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import com.daviddunn.retirementplanner.ui.views.IncomeSourcesView;
import javafx.scene.control.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.dialogs.ConditionalBenefitUiTestSupport.*;

class NonqualifiedAnnuityDialogTest {
    static final Path OUTPUT=Path.of("target/phase23d/previews");
    @BeforeAll static void initialize() throws Exception {startFx();Files.createDirectories(OUTPUT);}
    @Test void realDialogValidatesDetachedDraftAndPreservesIdentityAndCancel() throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(true); var original=plan.getConditionalBenefits().getFirst();
        fx(()->{
            var dialog=new NonqualifiedAnnuityDialog(plan,original,false);dialog.show();
            ((TextField)dialog.getDialogPane().lookup("#annuity-gross")).setText("invalid");
            ((Button)dialog.getDialogPane().lookupButton(ConditionalBenefitDialog.SAVE)).fire();
            assertTrue(dialog.isShowing()); assertNull(dialog.getResult());
            assertFalse(((Label)dialog.getDialogPane().lookup("#annuity-error")).getText().isBlank());
            assertEquals(original,plan.getConditionalBenefits().getFirst());
            ((TextField)dialog.getDialogPane().lookup("#annuity-gross")).setText("1500");
            assertFalse(((CheckBox)dialog.getDialogPane().lookup("#annuity-qualification")).isSelected());
            ((CheckBox)dialog.getDialogPane().lookup("#annuity-qualification")).setSelected(true);
            ((Button)dialog.getDialogPane().lookupButton(ConditionalBenefitDialog.SAVE)).fire();
            assertNotNull(dialog.getResult());assertEquals(original.id(),dialog.getResult().id());
            assertEquals(original,plan.getConditionalBenefits().getFirst());
            var cancel=new NonqualifiedAnnuityDialog(plan,original,false);cancel.show();
            ((TextField)cancel.getDialogPane().lookup("#annuity-remaining")).setText("1");
            ((Button)cancel.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();
            assertNull(cancel.getResult());assertEquals(original,plan.getConditionalBenefits().getFirst());
            var assumptions=plan.getPlanningAssumptions();
            plan.setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(assumptions.getEconomicAssumptions(),assumptions.getTaxAssumptions(),
                    assumptions.getWithdrawalAssumptions(),assumptions.getDeathScenarioAssumptions(),3,java.time.LocalDate.of(2031,1,1),assumptions.getStateTaxAssumptions()));
            var newOpening=new NonqualifiedAnnuityDialog(plan,original,false);newOpening.show();
            assertFalse(((CheckBox)newOpening.getDialogPane().lookup("#annuity-qualification")).isSelected());
            newOpening.close();assertEquals(java.time.LocalDate.of(2030,1,1),plan.getConditionalBenefits().getFirst().annuityTerms().basisAsOf());
            return null;
        });
    }
    @Test void lightDarkCompactAndLargeActualScenesHaveScrollingAndVisibleFooter() throws Exception {
        for(var theme:ApplicationTheme.values())for(int width:new int[]{900,1180}) {
            int height=width==900?650:820;
            var manager=fx(()->ApplicationThemeManager.start(theme));
            var dialog=fx(()->{
                var plan=NonqualifiedAnnuityFinancialTest.fixture(true);
                var value=new NonqualifiedAnnuityDialog(plan,plan.getConditionalBenefits().getFirst(),false);
                value.getDialogPane().setPrefSize(width,height);value.show();return value;
            });
            try {
                pulses(fx(()->dialog.getDialogPane().getScene()));
                fx(()->{
                    var pane=dialog.getDialogPane();var scroll=(ScrollPane)pane.lookup("#annuity-form-scroll");
                    assertTrue(scroll.getContent().getBoundsInLocal().getHeight()>scroll.getViewportBounds().getHeight());
                    assertInside(pane.lookupButton(ConditionalBenefitDialog.SAVE),pane.getScene());
                    assertInside(pane.lookupButton(ButtonType.CANCEL),pane.getScene());
                    image(pane,"annuity-"+theme+"-"+width+"-top.png");
                    scroll.setVvalue(1);return null;
                });
                pulses(fx(()->dialog.getDialogPane().getScene()));
                fx(()->{
                    var pane=dialog.getDialogPane();assertInside(pane.lookupButton(ConditionalBenefitDialog.SAVE),pane.getScene());
                    image(pane,"annuity-"+theme+"-"+width+"-bottom.png");
                    return null;
                });
            } finally {fx(()->{dialog.close();manager.close();return null;});}
        }
    }
    @Test void newSinglePersonUiDefinitionExecutesTheAuthoritativeFinancialSplit() throws Exception {
        var plan=NonqualifiedAnnuityFinancialTest.fixture(false);
        var originalIdentity=plan.getConditionalBenefits().getFirst().id();
        var result=fx(()->{
            var dialog=new NonqualifiedAnnuityDialog(plan,null,false);dialog.show();var pane=dialog.getDialogPane();
            ((TextField)pane.lookup("#annuity-name")).setText("UI-created quotation");
            ((TextField)pane.lookup("#annuity-gross")).setText("1200");
            ((TextField)pane.lookup("#annuity-excluded")).setText("720");
            ((TextField)pane.lookup("#annuity-original")).setText("100000");
            ((TextField)pane.lookup("#annuity-remaining")).setText("10000");
            ((DatePicker)pane.lookup("#annuity-purchase")).getEditor().setText("2020-01-01");
            ((DatePicker)pane.lookup("#annuity-tax-start")).getEditor().setText("2025-01-01");
            ((DatePicker)pane.lookup("#annuity-cash-start")).getEditor().setText("2025-01-01");
            assertEquals(1,((ComboBox<?>)pane.lookup("#annuity-annuitant")).getItems().size());
            ((CheckBox)pane.lookup("#annuity-qualification")).setSelected(true);
            ((Button)pane.lookupButton(ConditionalBenefitDialog.SAVE)).fire();
            assertNotNull(dialog.getResult());return dialog.getResult();
        });
        plan.setConditionalBenefits(java.util.List.of(result));
        var year=new com.daviddunn.retirementplanner.domain.projection.ProjectionEngine().project(plan).getYearAt(0);
        assertEquals(0,new java.math.BigDecimal("14400").compareTo(year.getGuaranteedIncome()));
        assertEquals(0,new java.math.BigDecimal("5760").compareTo(year.getAdjustedGrossIncome()));
        assertNotEquals(originalIdentity,result.id());
    }
    private static void assertInside(javafx.scene.Node node,javafx.scene.Scene scene) {
        var bounds=node.localToScene(node.getBoundsInLocal());
        assertTrue(bounds.getMinX()>=0&&bounds.getMinY()>=0);
        assertTrue(bounds.getMaxX()<=scene.getWidth()+1&&bounds.getMaxY()<=scene.getHeight()+1);
    }
    private static void image(javafx.scene.Node node,String file) throws Exception {
        var image=node.snapshot(null,null);
        var buffer=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<buffer.getHeight();y++)for(int x=0;x<buffer.getWidth();x++)buffer.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(buffer,"png",OUTPUT.resolve(file).toFile());
    }
    private static void pulses(javafx.scene.Scene scene) throws Exception {
        var complete=new java.util.concurrent.CompletableFuture<Void>();
        fx(()->{
            scene.addPostLayoutPulseListener(new Runnable(){
                int remaining=3;
                public void run(){if(--remaining==0){scene.removePostLayoutPulseListener(this);complete.complete(null);}else javafx.application.Platform.requestNextPulse();}
            });
            javafx.application.Platform.requestNextPulse();return null;
        });
        complete.get(15,java.util.concurrent.TimeUnit.SECONDS);
    }
}
