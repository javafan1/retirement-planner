package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import com.daviddunn.retirementplanner.ui.views.ExpensesView;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import static com.daviddunn.retirementplanner.ui.dialogs.ConditionalBenefitUiTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

public class ContractualPremiumDialogTest {
    @BeforeAll static void start() throws Exception { startFx(); Files.createDirectories(Path.of("target/phase23c/previews")); }
    public static <T> T field(Object dialog,String name,Class<T> type) throws Exception {
        var field=dialog.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(dialog));
    }
    @SuppressWarnings("unchecked")
    public static Expense create(RetirementPlan plan) throws Exception {
        return fx(()-> {
            var d=new ExpenseDialog(null,false,plan.getHousehold()); d.show();
            try {
                field(d,"descriptionField",TextField.class).setText("UI-created contractual premium");
                field(d,"annualAmountField",TextField.class).setText("2000");
                field(d,"expenseTypeComboBox",ComboBox.class).setValue(ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM);
                field(d,"effectiveDatePicker",DatePicker.class).setValue(LocalDate.of(2030,1,1));
                field(d,"endDatePicker",DatePicker.class).setValue(LocalDate.of(2040,12,31));
                field(d,"insuredField",ComboBox.class).setValue(ContractualPremiumPolicy.Insured.PRIMARY);
                ((Button)d.getDialogPane().lookupButton(ButtonType.OK)).fire();
                assertNotNull(d.getResult()); return d.getResult();
            } finally { d.close(); }
        });
    }

    @Test void uiCreatedPremiumUsesAuthoritativeFinancialEngine() throws Exception {
        var p=LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.FL);
        p.getHousehold().addExpense(create(p));
        var result=new ProjectionEngine().project(p);
        assertEquals(new BigDecimal("2000.00"),result.getYearAt(0).getAnnualExpenses());
        assertEquals(new BigDecimal("0.00"),result.getYearAt(1).getAnnualExpenses());
        assertEquals(0,result.getYearAt(1).getAdjustedGrossIncome().signum());
    }

    @SuppressWarnings("unchecked")
    @Test void validationDraftTypeChangesAndCancelNeverCommitHiddenPolicy() throws Exception {
        fx(()-> {
            var p=LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.FL);
            var original=com.daviddunn.retirementplanner.domain.financial.ContractualPremiumDomainTest.premium();
            var d=new ExpenseDialog(original,false,p.getHousehold()); d.show();
            try {
                var amount=field(d,"annualAmountField",TextField.class);
                amount.setText("-1"); ((Button)d.getDialogPane().lookupButton(ButtonType.OK)).fire();
                assertTrue(d.isShowing()); assertFalse(field(d,"validationMessage",Label.class).getText().isBlank());
                assertEquals("-1",amount.getText());
                amount.setText("4500"); field(d,"insuredField",ComboBox.class).setValue(null);
                ((Button)d.getDialogPane().lookupButton(ButtonType.OK)).fire(); assertTrue(d.isShowing());
                ((Button)d.getDialogPane().lookupButton(ButtonType.CANCEL)).fire(); assertNull(d.getResult());
                assertEquals(new BigDecimal("2000"),original.getAnnualAmount());
            } finally { d.close(); }
            d=new ExpenseDialog(original,false,p.getHousehold()); d.show();
            try {
                field(d,"expenseTypeComboBox",ComboBox.class).setValue(ExpenseType.RECURRING);
                ((Button)d.getDialogPane().lookupButton(ButtonType.OK)).fire();
                assertNull(d.getResult().getContractualPremium()); assertFalse(d.getResult().isContractualInsurancePremium());
            } finally { d.close(); }
            d=new ExpenseDialog(original,false,p.getHousehold()); d.show(); d.close(); assertNull(d.getResult());
            var single=plan(false); var singleDialog=new ExpenseDialog(null,false,single.getHousehold());
            assertEquals(java.util.List.of(ContractualPremiumPolicy.Insured.PRIMARY),field(singleDialog,"insuredField",ComboBox.class).getItems());
            return null;
        });
    }

    @Test void lightDarkCompactAndStandardActualScenesKeepFixedFooterAndScrollableBody() throws Exception {
        for(var theme:ApplicationTheme.values()) for(int width:new int[]{900,1180}) {
            var manager=fx(()->ApplicationThemeManager.start(theme));
            var d=fx(()-> {
                var plan=LifeInsuranceFinancialTest.fixture(StateTaxJurisdiction.FL);
                var dialog=new ExpenseDialog(com.daviddunn.retirementplanner.domain.financial.ContractualPremiumDomainTest.premium(),false,plan.getHousehold());
                dialog.show(); var stage=(Stage)dialog.getDialogPane().getScene().getWindow();
                stage.setWidth(width); stage.setHeight(width==900?650:820); return dialog;
            });
            try {
                LifeInsuranceDialogTest.pulses(fx(()->d.getDialogPane().getScene()));
                fx(()-> {
                    var pane=d.getDialogPane(); var body=(ScrollPane)pane.getContent(); body.setVvalue(1);
                    assertTrue(body.isFitToWidth());
                    var button=pane.lookupButton(ButtonType.OK); var bounds=button.localToScene(button.getBoundsInLocal());
                    assertTrue(bounds.getMaxY()<=pane.getScene().getHeight()+1);
                    assertFalse(field(d,"volatilityField",TextField.class).isManaged());
                    assertTrue(field(d,"growthCategoryComboBox",ComboBox.class).isDisabled());
                    return null;
                });
                LifeInsuranceDialogTest.pulses(fx(()->d.getDialogPane().getScene()));
                fx(()-> {
                    var image=d.getDialogPane().snapshot(null,null);
                    var buffer=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<buffer.getHeight();y++) for(int x=0;x<buffer.getWidth();x++) buffer.setRGB(x,y,image.getPixelReader().getArgb(x,y));
                    javax.imageio.ImageIO.write(buffer,"png",Path.of("target/phase23c/previews/premium-"+theme+"-"+width+".png").toFile());
                    return null;
                });
            } finally { fx(()-> { d.close(); manager.close(); return null; }); }
        }
    }
}
